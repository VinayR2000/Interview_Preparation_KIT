# 7. Exception Handling

## Theory

**Exception Types:**
- **Checked exceptions**: Must be caught or declared (IOException, SQLException)
- **Unchecked exceptions**: RuntimeException subclasses (NPE, IllegalArgumentException)
- **Custom exceptions**: Application-specific (ResourceNotFoundException, InsufficientBalanceException)

**Global Exception Handling:**
- `@ExceptionHandler` — Method-level, handles specific exception types
- `@ControllerAdvice` — Global handler for @Controller classes
- `@RestControllerAdvice` — @ControllerAdvice + @ResponseBody (returns JSON)

**Flow:**
```
Controller throws exception
       ↓
@RestControllerAdvice intercepts
       ↓
Matches @ExceptionHandler method by exception type
       ↓
Returns structured error response with proper HTTP status
```

---

## Internal Working

```
Exception thrown in Controller/Service
       ↓
Spring checks for @ExceptionHandler in same controller
       ↓ (not found)
Spring checks @ControllerAdvice classes
       ↓
ExceptionHandlerMethodResolver finds matching handler
  (matches by exception class hierarchy — most specific wins)
       ↓
Handler method invoked with exception as argument
       ↓
Return value processed (JSON serialization)
       ↓
HTTP response with proper status code sent

Exception Matching Order:
1. Exact exception type match
2. Nearest superclass in hierarchy
3. If multiple @ControllerAdvice, @Order determines priority
```

---

## Diagram

```
┌─────────────────────────────────────────────────┐
│              Request Flow with Exceptions         │
│                                                  │
│  Client Request                                  │
│       ↓                                          │
│  Controller                                      │
│       ↓                                          │
│  Service Layer                                   │
│       ↓                                          │
│  ⚡ Exception Thrown!                             │
│       ↓                                          │
│  ┌──────────────────────────────┐               │
│  │    @RestControllerAdvice     │               │
│  │                              │               │
│  │  @ExceptionHandler(          │               │
│  │    ResourceNotFound.class)   │ → 404         │
│  │                              │               │
│  │  @ExceptionHandler(          │               │
│  │    ValidationException.class)│ → 400         │
│  │                              │               │
│  │  @ExceptionHandler(          │               │
│  │    Exception.class)          │ → 500         │
│  └──────────────────────────────┘               │
│       ↓                                          │
│  Structured Error Response (JSON)                │
│       ↓                                          │
│  Client                                          │
└─────────────────────────────────────────────────┘
```

---

## Code

```java
// === Custom Exceptions ===
public class ResourceNotFoundException extends RuntimeException {
    private final String resource;
    private final String field;
    private final Object value;

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
        this.resource = resource;
        this.field = field;
        this.value = value;
    }
    // Getters
}

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}

public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(BigDecimal required, BigDecimal available) {
        super("INSUFFICIENT_BALANCE",
              String.format("Required: %s, Available: %s", required, available));
    }
}

// === Error Response DTO ===
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        String correlationId,
        List<ValidationError> errors
) {
    public record ValidationError(String field, String message, Object rejectedValue) {}

    // Builder-style static factories
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                status.value(), status.getReasonPhrase(), message,
                path, LocalDateTime.now(), MDC.get("correlationId"), null);
    }

    public static ErrorResponse withValidation(String path, List<ValidationError> errors) {
        return new ErrorResponse(
                400, "Bad Request", "Validation failed",
                path, LocalDateTime.now(), MDC.get("correlationId"), errors);
    }
}

// === Global Exception Handler ===
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 404 - Resource Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // 409 - Duplicate/Conflict
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {

        log.warn("Duplicate resource: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // 400 - Validation errors (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.ValidationError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.ValidationError(
                        fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();

        log.warn("Validation failed: {} errors", errors.size());

        ErrorResponse error = ErrorResponse.withValidation(
                request.getRequestURI(), errors);

        return ResponseEntity.badRequest().body(error);
    }

    // 400 - Constraint violation (@Validated on path/query params)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ErrorResponse.ValidationError> errors = ex.getConstraintViolations()
                .stream()
                .map(v -> new ErrorResponse.ValidationError(
                        v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                .toList();

        ErrorResponse error = ErrorResponse.withValidation(
                request.getRequestURI(), errors);

        return ResponseEntity.badRequest().body(error);
    }

    // 422 - Business logic errors
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        log.warn("Business rule violation: {} - {}", ex.getErrorCode(), ex.getMessage());

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    // 500 - Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        // Don't expose internal details to client
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

// === Service layer throwing exceptions ===
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return mapToResponse(user);
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "User already exists with email: " + request.email());
        }
        // create logic
    }
}
```

---

## Dry Run

**Scenario**: GET /api/v1/users/999 — user doesn't exist

```
1. UserController.getUserById(999) called
2. userService.findById(999) called
3. userRepository.findById(999) returns Optional.empty()
4. orElseThrow → throws ResourceNotFoundException("User", "id", 999)
5. Exception propagates up through controller
6. GlobalExceptionHandler catches it:
   - Matches @ExceptionHandler(ResourceNotFoundException.class)
   - handleNotFound() invoked
7. Logs: "Resource not found: User not found with id: 999"
8. Builds ErrorResponse:
   - status: 404
   - error: "Not Found"
   - message: "User not found with id: 999"
   - path: "/api/v1/users/999"
   - timestamp: current time

Response:
HTTP/1.1 404 Not Found
Content-Type: application/json
{
  "status": 404,
  "error": "Not Found",
  "message": "User not found with id: 999",
  "path": "/api/v1/users/999",
  "timestamp": "2024-01-15T10:30:00",
  "correlationId": "abc-123-def",
  "errors": null
}
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Exception handler lookup | O(n) — n = registered handlers (small, cached) |
| Exception class matching | O(d) — d = depth of exception hierarchy |
| Error response construction | O(1) |
| Validation error extraction | O(n) — n = field errors |

---

## Real Project Usage

```java
// Exception with error codes for API consumers
public enum ErrorCode {
    USER_NOT_FOUND("USR-001"),
    DUPLICATE_EMAIL("USR-002"),
    INSUFFICIENT_FUNDS("PAY-001"),
    ORDER_ALREADY_CANCELLED("ORD-001");

    private final String code;
    ErrorCode(String code) { this.code = code; }
}

// Client receives: {"errorCode": "USR-001", "message": "..."}
// They can map error codes to their own UI messages
```

---

## Interview Questions

1. **What is the difference between @ControllerAdvice and @RestControllerAdvice?**
   - @RestControllerAdvice = @ControllerAdvice + @ResponseBody. It automatically serializes return values to JSON. Use @RestControllerAdvice for REST APIs, @ControllerAdvice for MVC with views.

2. **How does Spring determine which @ExceptionHandler to invoke?**
   - Most specific exception type match wins. Controller-level handlers take priority over global @ControllerAdvice. Among multiple @ControllerAdvice, @Order annotation determines priority.

3. **How do you handle validation exceptions globally?**
   - Create @RestControllerAdvice with @ExceptionHandler(MethodArgumentNotValidException.class). Extract field errors from BindingResult, format into consistent error response with field name, message, rejected value.

4. **What is the difference between MethodArgumentNotValidException and ConstraintViolationException?**
   - MethodArgumentNotValidException: Thrown when @Valid fails on @RequestBody objects. ConstraintViolationException: Thrown when @Validated constraints fail on @PathVariable/@RequestParam at class level.

5. **How do you add a correlation ID to error responses?**
   - Extract from MDC (set by filter on request entry) and include in error response DTO. `MDC.get("correlationId")` in the exception handler.

6. **Should you expose stack traces in production API responses?**
   - Never. Security risk (reveals internals, class names, SQL). Log full stack trace server-side, return only: status code, error code, user-friendly message, correlation ID.

7. **How do you handle exceptions from @Async methods?**
   - Implement AsyncUncaughtExceptionHandler for void methods. For CompletableFuture, use .exceptionally() or .handle(). @ExceptionHandler doesn't catch async exceptions (different thread).

8. **What is ResponseStatusException?**
   - Runtime exception with embedded HTTP status. Throw from anywhere: `throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")`. Quick alternative to custom exceptions. Less suitable for complex error responses.

9. **How do you test exception handling?**
   - MockMvc: Mock service to throw exception, assert response status and body structure. Test both the exception throwing and the response format.

10. **How do you order multiple @ControllerAdvice classes?**
    - Use @Order(1), @Order(2) annotations. Lower number = higher priority. Useful for: specific exception handlers in one advice, catch-all in another with lower priority.

---

## Follow-up Questions

1. **After Q2**: "What if two handlers match? Which wins?"
   → Most specific exception type wins. If same level, @Order annotation on @ControllerAdvice decides.

2. **After Q4**: "When does each get thrown?"
   → MethodArgumentNotValidException: @RequestBody + @Valid. ConstraintViolationException: @Validated on class + @PathVariable/@RequestParam constraints.

3. **After Q6**: "What information should be in production error responses?"
   → Status, error code, user-friendly message, correlation ID, timestamp. Never: stack trace, internal class names, SQL queries.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| No global handler | Each controller duplicates error handling | Use @RestControllerAdvice |
| Catching Exception everywhere in service | Swallows bugs | Let exceptions propagate to handler |
| Exposing stack traces | Security risk | Log internally, return generic message |
| No catch-all handler | Unhandled exceptions return ugly Spring default | Always have Exception handler |
| Same HTTP status for all errors | Clients can't differentiate | Map exception → appropriate status |
| Not logging in handler | Errors invisible in production | Always log with appropriate level |

---

## Best Practices

1. **Single @RestControllerAdvice** per application (or few, well-ordered)
2. **Custom exception hierarchy** — base class for your app exceptions
3. **Consistent error response structure** — same format everywhere
4. **Error codes** — machine-readable codes for API consumers
5. **Correlation IDs** — trace errors across services
6. **Log at appropriate levels** — WARN for client errors, ERROR for server errors
7. **Never expose internals** — class names, SQL, stack traces
8. **Test exception scenarios** — test your error paths
9. **Document error codes** — API docs should list possible errors
10. **Use ResponseStatusException** for simple cases in controllers

---

## Production Considerations

- **Monitoring**: Alert on 5xx errors, track 4xx rates
- **Correlation IDs**: MDC-based, passed in X-Request-Id header
- **Sensitive data**: Never log passwords, tokens, PII in error messages
- **Rate of errors**: High 4xx might indicate clients need better docs
- **Error aggregation**: Group similar errors to identify systemic issues
- **Circuit breaker integration**: Downstream failures → proper error responses
- **Internationalization**: Error messages may need i18n for global apps

---

## Related Topics

- → [5. Spring Boot REST API](#) (controllers that throw)
- → [6. Response Handling](#) (HTTP status codes)
- → [8. Validation](#) (validation exceptions)
- → [25. Logging](#) (error logging strategy)
- → [30. Resilience Patterns](#) (fallback error handling)
