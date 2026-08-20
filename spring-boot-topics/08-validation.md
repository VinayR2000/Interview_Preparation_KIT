# 8. Validation

## Theory

**Bean Validation (Jakarta Validation):**
A standard specification (JSR 380) for validating Java objects using annotations. Spring Boot integrates seamlessly with `spring-boot-starter-validation`.

**Key Annotations:**
- `@Valid` — Triggers validation on @RequestBody parameter
- `@Validated` — Spring-specific, enables class-level validation (path vars, query params)

**Built-in Constraints:**

| Annotation | Purpose | Applies To |
|-----------|---------|-----------|
| @NotNull | Not null | Any type |
| @NotBlank | Not null, not empty, not whitespace | String |
| @NotEmpty | Not null, not empty | String, Collection, Map |
| @Size(min, max) | Size bounds | String, Collection |
| @Min(value) | Minimum value | Number |
| @Max(value) | Maximum value | Number |
| @Positive | > 0 | Number |
| @PositiveOrZero | >= 0 | Number |
| @Email | Valid email format | String |
| @Pattern(regexp) | Regex match | String |
| @Past | Date in past | Date/Time |
| @Future | Date in future | Date/Time |
| @Digits(integer, fraction) | Decimal precision | Number |

---

## Internal Working

```
@RequestBody + @Valid
       ↓
Jackson deserializes JSON → Java object
       ↓
Hibernate Validator triggered by MethodValidationPostProcessor
       ↓
Each constraint annotation checked
       ↓
Violations collected into BindingResult
       ↓
If violations exist → MethodArgumentNotValidException thrown
       ↓
@RestControllerAdvice catches and returns 400

For @PathVariable/@RequestParam + @Validated:
Controller class must be annotated with @Validated
       ↓
ConstraintViolationException thrown instead
```

---

## Diagram

```
Request Body Validation:
┌────────────────┐     ┌──────────────┐     ┌──────────────┐
│ JSON Request   │────▶│ Deserialize  │────▶│  Validate    │
│ {"name": ""}   │     │ → Java obj   │     │  @NotBlank   │
└────────────────┘     └──────────────┘     └──────────────┘
                                                    │
                                              Violations?
                                              /         \
                                           No             Yes
                                           ↓               ↓
                                    Controller        MethodArgument
                                    executes         NotValidException
                                                          ↓
                                                    400 Bad Request

Nested Validation:
CreateOrderRequest
├── @NotBlank customerName  ✓
├── @Valid address          ← Triggers validation on nested object
│     ├── @NotBlank street  ✓
│     └── @NotBlank city    ✗ FAIL
└── @NotEmpty items
      └── @Valid item[0]    ← Each item validated
            ├── @NotBlank sku  ✓
            └── @Positive qty  ✗ FAIL
```

---

## Code

```java
// === Request DTOs with Validation ===
public record CreateUserRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 50, message = "Password must be 8-50 characters")
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).*$",
                 message = "Password must contain uppercase, lowercase, and digit")
        String password,

        @Size(min = 10, max = 15, message = "Phone must be 10-15 digits")
        @Pattern(regexp = "^\\d+$", message = "Phone must contain only digits")
        String phone,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Valid  // Triggers nested validation
        @NotNull(message = "Address is required")
        AddressRequest address
) {}

public record AddressRequest(
        @NotBlank(message = "Street is required")
        String street,

        @NotBlank(message = "City is required")
        String city,

        @NotBlank(message = "Zip code is required")
        @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Invalid zip code")
        String zipCode
) {}

// === Controller with validation ===
@RestController
@RequestMapping("/api/v1/users")
@Validated  // Enables @PathVariable/@RequestParam validation
public class UserController {

    // Request body validation
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.create(request));
    }

    // Path variable validation
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable @Positive(message = "ID must be positive") Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    // Request param validation
    @GetMapping
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam @Size(min = 2, message = "Query must be at least 2 chars") String query,
            @RequestParam @Min(0) int page,
            @RequestParam @Max(100) int size) {
        return ResponseEntity.ok(userService.search(query, page, size));
    }
}

// === Custom Validator ===
// Step 1: Create annotation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email already registered";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Step 2: Create validator
@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    private final UserRepository userRepository;

    public UniqueEmailValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null) return true;  // @NotNull handles null check
        return !userRepository.existsByEmail(email);
    }
}

// Usage:
public record CreateUserRequest(
        @NotBlank String name,
        @Email @UniqueEmail String email,  // Custom validator
        @NotBlank String password
) {}

// === Validation Groups ===
public interface OnCreate {}
public interface OnUpdate {}

public record ProductRequest(
        @Null(groups = OnCreate.class, message = "ID must be null on create")
        @NotNull(groups = OnUpdate.class, message = "ID required on update")
        Long id,

        @NotBlank(groups = {OnCreate.class, OnUpdate.class})
        String name,

        @Positive(groups = {OnCreate.class, OnUpdate.class})
        BigDecimal price
) {}

@PostMapping
public ResponseEntity<ProductResponse> create(
        @RequestBody @Validated(OnCreate.class) ProductRequest request) { ... }

@PutMapping("/{id}")
public ResponseEntity<ProductResponse> update(
        @PathVariable Long id,
        @RequestBody @Validated(OnUpdate.class) ProductRequest request) { ... }

// === Cross-field validation ===
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {
    String message() default "End date must be after start date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@ValidDateRange
public record DateRangeRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRangeRequest> {
    @Override
    public boolean isValid(DateRangeRequest request, ConstraintValidatorContext context) {
        if (request.startDate() == null || request.endDate() == null) return true;
        return request.endDate().isAfter(request.startDate());
    }
}
```

---

## Dry Run

**Scenario**: POST /api/v1/users with invalid data

```
Request:
{"name": "", "email": "not-an-email", "password": "weak", "phone": "abc", "dateOfBirth": "2030-01-01"}

1. Jackson deserializes → CreateUserRequest
2. @Valid triggers Hibernate Validator
3. Validation checks:
   - @NotBlank name: "" → FAIL ("Name is required")
   - @Email email: "not-an-email" → FAIL ("Invalid email format")
   - @Size(8,50) password: "weak" (4 chars) → FAIL
   - @Pattern phone: "abc" → FAIL ("Phone must contain only digits")
   - @Past dateOfBirth: 2030-01-01 → FAIL ("must be in the past")
4. 5 violations collected
5. MethodArgumentNotValidException thrown
6. GlobalExceptionHandler catches it
7. Returns 400 with all field errors:

Response:
HTTP/1.1 400 Bad Request
{
  "status": 400,
  "message": "Validation failed",
  "errors": [
    {"field": "name", "message": "Name is required", "rejectedValue": ""},
    {"field": "email", "message": "Invalid email format", "rejectedValue": "not-an-email"},
    {"field": "password", "message": "Password must be 8-50 characters", "rejectedValue": "weak"},
    {"field": "phone", "message": "Phone must contain only digits", "rejectedValue": "abc"},
    {"field": "dateOfBirth", "message": "Date of birth must be in the past", "rejectedValue": "2030-01-01"}
  ]
}
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Single constraint validation | O(1) — simple check |
| @Pattern (regex) | O(n) — n = string length |
| @UniqueEmail (DB check) | O(log n) — indexed lookup |
| All constraints on object | O(m) — m = total constraints |
| Nested validation | O(m * d) — d = nesting depth |

---

## Real Project Usage

```java
// E-commerce order validation
public record CreateOrderRequest(
        @NotNull Long customerId,
        
        @NotEmpty(message = "Order must have at least one item")
        @Size(max = 50, message = "Maximum 50 items per order")
        List<@Valid OrderItemRequest> items,
        
        @Valid @NotNull AddressRequest shippingAddress,
        
        @NotNull PaymentMethod paymentMethod,
        
        @Size(max = 500) String notes
) {}

public record OrderItemRequest(
        @NotNull Long productId,
        @Positive @Max(999) Integer quantity,
        @PositiveOrZero BigDecimal discount
) {}
```

---

## Interview Questions

1. **What is the difference between @Valid and @Validated?**
   - @Valid: Jakarta standard, triggers validation on @RequestBody, supports nested validation. @Validated: Spring-specific, supports validation groups, required at class level for @PathVariable/@RequestParam constraints.

2. **How do you validate path variables and query parameters?**
   - Add @Validated at the controller class level, then use constraint annotations directly: `@PathVariable @Min(1) Long id`, `@RequestParam @Size(min=2) String name`. Throws ConstraintViolationException.

3. **How do you create a custom validator?**
   - Create annotation with @Constraint(validatedBy = ...), and implement ConstraintValidator<MyAnnotation, TargetType> with isValid() method. Register as @Component to allow dependency injection.

4. **What exception is thrown for @RequestBody validation failures?**
   - MethodArgumentNotValidException. Contains BindingResult with all field errors. Handle in @RestControllerAdvice to return structured error response.

5. **How do you perform cross-field validation?**
   - Class-level constraint annotation (e.g., @PasswordMatch). Validator receives the entire object and can compare multiple fields. Place annotation on the DTO class, not on individual fields.

6. **What are validation groups? When are they useful?**
   - Interfaces used to apply different validation rules in different contexts. E.g., OnCreate group requires @NotNull on ID, OnUpdate doesn't. Activate with @Validated(OnCreate.class).

7. **What is the difference between @NotNull, @NotEmpty, and @NotBlank?**
   - @NotNull: Value != null (but can be empty ""). @NotEmpty: Not null AND not empty (size > 0). @NotBlank: Not null, not empty, AND not just whitespace. For Strings: always use @NotBlank.

8. **How do you validate nested objects?**
   - Add @Valid on the nested field: `@Valid @NotNull private AddressDTO address;`. Without @Valid, the nested object's constraints are NOT evaluated.

9. **How do you perform programmatic validation (not annotation-based)?**
   - Inject Validator bean, call `validator.validate(object)`. Returns Set<ConstraintViolation>. Useful for validating objects not from request body (e.g., from Kafka messages).

10. **How do you handle validation in service layer vs controller?**
    - Controller: @Valid on @RequestBody for input validation. Service: @Validated on class + constraints on method params for business rule validation. Both are valid; controller catches early, service protects business logic.

---

## Follow-up Questions

1. **After Q1**: "@Validated supports groups, @Valid doesn't. Why use @Valid then?"
   → @Valid is the Jakarta standard, works with @RequestBody. @Validated is Spring-specific, needed for method-level validation.

2. **After Q7**: "What does @NotBlank('  ') return?"
   → Invalid. @NotBlank trims and checks if empty. @NotEmpty would pass (contains spaces).

3. **After Q4**: "What about @PathVariable validation?"
   → Throws ConstraintViolationException (different exception, needs separate handler).

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Forgetting @Valid on @RequestBody | Validation never triggers | Always add @Valid |
| No @Validated on controller class | @PathVariable constraints ignored | Add @Validated at class level |
| Custom validator not a @Component | Can't inject dependencies | Add @Component |
| Null check in custom validator | @NotNull handles that | Return true for null, let @NotNull handle |
| Not validating nested objects | Inner objects bypass validation | Add @Valid on nested field |
| String validation with @NotNull only | Allows empty string "" | Use @NotBlank for strings |

---

## Best Practices

1. **Use @NotBlank for strings** (not just @NotNull)
2. **Always provide message** — default messages are cryptic
3. **Use @Valid for nested objects** — propagate validation
4. **Custom validators for business rules** — @UniqueEmail, @ValidDateRange
5. **Validate early** — controller layer, fail fast
6. **Validation groups** for create vs update differences
7. **Handle all validation exceptions** in @RestControllerAdvice
8. **Test validation rules** — unit test constraints
9. **Use records for DTOs** — immutable, clean
10. **Consistent error format** — field, message, rejectedValue

---

## Production Considerations

- **Performance**: Custom validators with DB calls can be slow — cache if possible
- **Security**: Validate input to prevent injection attacks
- **i18n**: Externalize validation messages for multi-language support
- **Client experience**: Return ALL validation errors at once (not one at a time)
- **API docs**: Document constraints in OpenAPI spec
- **Business validation**: Keep in service layer, separate from format validation

---

## Related Topics

- → [5. Spring Boot REST API](#) (controller validation)
- → [7. Exception Handling](#) (handling validation exceptions)
- → [6. Response Handling](#) (400 error responses)
- → [9. Spring Data JPA](#) (entity-level constraints)
