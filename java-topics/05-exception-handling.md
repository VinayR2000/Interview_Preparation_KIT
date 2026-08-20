# 5. Exception Handling

---

## Theory

Exceptions are events that disrupt the normal flow of program execution. Java provides a robust mechanism to handle these runtime errors gracefully.

### Exception Hierarchy

```
java.lang.Throwable
├── java.lang.Error (unrecoverable — don't catch)
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   ├── VirtualMachineError
│   └── AssertionError
│
└── java.lang.Exception (recoverable — can catch)
    ├── RuntimeException (unchecked)
    │   ├── NullPointerException
    │   ├── ArrayIndexOutOfBoundsException
    │   ├── IllegalArgumentException
    │   ├── IllegalStateException
    │   ├── ClassCastException
    │   ├── ArithmeticException
    │   ├── UnsupportedOperationException
    │   └── ConcurrentModificationException
    │
    └── Checked Exceptions (must handle)
        ├── IOException
        ├── SQLException
        ├── FileNotFoundException
        ├── ClassNotFoundException
        └── InterruptedException
```

### Checked vs Unchecked Exceptions

| Checked | Unchecked (Runtime) |
|---------|---------------------|
| Must be caught or declared (`throws`) | No obligation to handle |
| Compiler enforces handling | Compiler doesn't check |
| External failures (IO, network) | Programming errors (null, cast) |
| `extends Exception` | `extends RuntimeException` |
| Recover or fail gracefully | Fix the code / validate input |

### Keywords

```java
try {
    // Code that might throw exception
    int result = 10 / 0;
} catch (ArithmeticException e) {
    // Handle specific exception
    System.err.println("Cannot divide by zero: " + e.getMessage());
} catch (Exception e) {
    // Handle broader exception (catch order: specific → general)
    System.err.println("Error: " + e.getMessage());
} finally {
    // ALWAYS executes (cleanup) — even if exception thrown or return in try
    // Exception: System.exit(), JVM crash, infinite loop in try/catch
    closeResources();
}
```

```java
// throw — explicitly throw an exception
throw new IllegalArgumentException("Age cannot be negative");

// throws — declare method can throw checked exception
public void readFile(String path) throws IOException {
    // caller must handle IOException
}
```

### Try-With-Resources (Java 7+)

```java
// Automatically closes resources that implement AutoCloseable
try (BufferedReader reader = new BufferedReader(new FileReader("file.txt"));
     BufferedWriter writer = new BufferedWriter(new FileWriter("out.txt"))) {
    
    String line;
    while ((line = reader.readLine()) != null) {
        writer.write(line);
    }
} // reader and writer automatically closed here (in reverse order)
// No need for explicit finally block!
```

### Custom Exceptions

```java
// Checked custom exception
public class InsufficientFundsException extends Exception {
    private final double deficit;
    
    public InsufficientFundsException(String message, double deficit) {
        super(message);
        this.deficit = deficit;
    }
    
    public double getDeficit() { return deficit; }
}

// Unchecked custom exception
public class EntityNotFoundException extends RuntimeException {
    private final String entityType;
    private final Object id;
    
    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found with id: " + id);
        this.entityType = entityType;
        this.id = id;
    }
}
```

### Exception Propagation

```java
// Exceptions propagate UP the call stack until caught
method1() calls method2() calls method3()

method3() throws IOException
  → not caught in method3? propagates to method2()
  → not caught in method2? propagates to method1()
  → not caught in method1? propagates to JVM → thread terminates
```

### Exception Chaining

```java
try {
    connectToDatabase();
} catch (SQLException e) {
    // Wrap low-level exception in domain-specific exception
    throw new ServiceException("Failed to initialize service", e); // 'e' is the cause
}

// Later, access the chain:
catch (ServiceException e) {
    e.getCause(); // returns the original SQLException
}
```

### Suppressed Exceptions

```java
// In try-with-resources, if both try block AND close() throw exceptions:
// - Primary exception from try block is thrown
// - Exception from close() is "suppressed" and attached

try (MyResource r = new MyResource()) {
    throw new RuntimeException("Main error");
} // if r.close() also throws, it's suppressed

// Access:
catch (RuntimeException e) {
    Throwable[] suppressed = e.getSuppressed(); // close() exception here
}
```

---

## Internal Working

### Exception Object Creation
```
throw new NullPointerException("msg"):
1. Object allocated on heap (like any object)
2. Stack trace captured (fillInStackTrace()) — this is EXPENSIVE
3. Message stored
4. Exception object thrown (control flow changes)
```

### Stack Trace Capture
```
When exception is created (NOT when thrown):
- JVM walks the entire call stack
- Records each frame: class, method, file, line number
- Stored in Throwable.backtrace field (native)
- This is why exception creation is expensive (~100x a method call)
```

### JVM Exception Handling Mechanism
```
Each method has an Exception Table in bytecode:

Method: processOrder()
Exception Table:
  from   to    target  type
    0     4      7    IOException
    0     4     12    Exception
    0    15     18    any (finally)

When exception occurs at PC=2:
1. JVM searches exception table for matching entry
2. type matches → jump to target (handler)
3. No match → unwind frame, repeat in caller
```

### Performance Impact
```
Normal flow (no exception):
  try-catch has ZERO overhead in modern JVMs
  (exception table lookup only happens when exception actually thrown)

Exception thrown:
  1. Stack trace capture: ~1-5 μs (walks call stack)
  2. Stack unwinding: proportional to call depth
  3. Handler matching: exception table lookup per frame

Creating exception WITHOUT stack trace:
  throw new MyException("msg") { 
      @Override public Throwable fillInStackTrace() { return this; } 
  };
  // ~100x faster — but no stack trace for debugging
```

---

## Diagram

```
Exception Hierarchy:
┌─────────────────────────────────────────────────────────────┐
│                      Throwable                              │
├────────────────────────────┬────────────────────────────────┤
│         Error              │           Exception            │
│  (Don't catch)             │     (Recoverable)             │
│                            ├───────────────┬───────────────┤
│  OutOfMemoryError          │RuntimeException│ Checked      │
│  StackOverflowError        │ (Unchecked)   │              │
│  VirtualMachineError       │               │ IOException  │
│                            │ NPE           │ SQLException │
│                            │ ClassCastEx   │ FileNotFound │
│                            │ IllegalArgEx  │              │
└────────────────────────────┴───────────────┴───────────────┘
```

```
Exception Propagation:
┌──────────┐    ┌──────────┐    ┌──────────┐
│ method3()│───→│ method2()│───→│ method1()│───→ JVM
│ throws   │    │ not caught│   │ caught!  │
│ IOException   │ propagates│   │ handled  │
└──────────┘    └──────────┘    └──────────┘
```

```
Try-With-Resources Close Order:
try (A a = ...; B b = ...; C c = ...) { }
Close order: C → B → A (reverse of declaration)
```

---

## Code

```java
public class ExceptionHandlingDemo {

    // --- Custom checked exception ---
    public static class OrderProcessingException extends Exception {
        private final String orderId;
        
        public OrderProcessingException(String message, String orderId, Throwable cause) {
            super(message, cause);
            this.orderId = orderId;
        }
        
        public String getOrderId() { return orderId; }
    }

    // --- Custom unchecked exception ---
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String orderId) {
            super("Order not found: " + orderId);
        }
    }

    // --- Method declaring checked exception ---
    public Order findOrder(String orderId) throws OrderProcessingException {
        try {
            return repository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));
        } catch (DataAccessException e) {
            // Exception chaining — wrap low-level in domain exception
            throw new OrderProcessingException(
                "Database error while finding order", orderId, e);
        }
    }

    // --- Multi-catch (Java 7+) ---
    public void processInput(String input) {
        try {
            int num = Integer.parseInt(input);
            String[] arr = new String[num];
            arr[num] = "test"; // ArrayIndexOutOfBoundsException
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Multi-catch — handle multiple exceptions the same way
            // 'e' is effectively final in multi-catch
            System.err.println("Invalid input: " + e.getMessage());
        }
    }

    // --- Try-with-resources with custom AutoCloseable ---
    public static class DatabaseConnection implements AutoCloseable {
        private boolean open = true;
        
        public void query(String sql) {
            if (!open) throw new IllegalStateException("Connection closed");
            System.out.println("Executing: " + sql);
        }
        
        @Override
        public void close() {
            open = false;
            System.out.println("Connection closed");
        }
    }

    public void executeQuery() {
        try (DatabaseConnection conn = new DatabaseConnection()) {
            conn.query("SELECT * FROM orders");
            // conn.close() called automatically
        } // even if query() throws, close() is guaranteed
    }

    // --- Finally guaranteed execution ---
    public int demonstrateFinally() {
        try {
            return 1;
        } finally {
            // This STILL executes even with return in try!
            System.out.println("Finally executed");
            // WARNING: return here would override try's return
            // return 2; // BAD PRACTICE — would return 2 instead of 1
        }
    }

    // --- Proper exception handling pattern ---
    public void robustMethod() {
        try {
            riskyOperation();
        } catch (SpecificException e) {
            // 1. Log with context
            logger.error("Operation failed for user {}: {}", userId, e.getMessage(), e);
            
            // 2. Translate to appropriate abstraction level
            throw new ServiceException("Could not complete operation", e);
            
        } catch (Exception e) {
            // 3. Catch-all for unexpected errors
            logger.error("Unexpected error", e);
            throw new InternalServerException("Internal error", e);
        }
    }
}
```

---

## Dry Run

### Exception Propagation
```
Code:
  main() → processOrder() → validateOrder() → throws IllegalArgumentException

Step 1: validateOrder() throws IllegalArgumentException("Invalid quantity")
Step 2: Does validateOrder() have a catch for this? No → propagate up
Step 3: Does processOrder() have a catch? 
        Yes: catch (IllegalArgumentException e)
Step 4: Handler executes: log error, throw new OrderException(e)
Step 5: Does main() catch OrderException?
        Yes: catch (OrderException e) → display error to user
```

### Try-With-Resources with Suppressed Exception
```
Code:
  try (MyResource r = new MyResource()) {
      r.doWork(); // throws RuntimeException("work failed")
  } // r.close() throws RuntimeException("close failed")

Step 1: r.doWork() throws RuntimeException("work failed") — this is PRIMARY
Step 2: try-with-resources calls r.close()
Step 3: close() throws RuntimeException("close failed") — this is SUPPRESSED
Step 4: "close failed" attached to "work failed" via addSuppressed()
Step 5: "work failed" is thrown to caller
Step 6: Caller can access: e.getSuppressed()[0] → "close failed"
```

---

## Complexity

| Operation | Cost |
|-----------|------|
| try-catch (no exception) | O(0) — zero runtime cost |
| throw new Exception | O(stack depth) — stack trace capture |
| Exception without stack trace | O(1) |
| Stack unwinding | O(frames traversed) |
| Exception table lookup | O(entries in table) |
| getCause() / getSuppressed() | O(1) |

**Rule of thumb**: Exceptions should be exceptional. Don't use them for flow control — they're 100-1000x slower than conditionals.

---

## Real Project Usage

```java
// Spring Boot Global Exception Handler
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex) {
        return new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        
        return new ErrorResponse(400, "Validation failed", errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return new ErrorResponse(500, "Internal server error", null);
        // Never expose stack traces to clients!
    }
}

// Service layer with proper exception handling
@Service
@Transactional
public class PaymentService {

    public PaymentResult processPayment(PaymentRequest request) {
        try {
            validateRequest(request);
            PaymentGatewayResponse response = gateway.charge(request);
            return mapToResult(response);
            
        } catch (PaymentDeclinedException e) {
            // Expected business case — not an error
            log.info("Payment declined for order {}: {}", request.getOrderId(), e.getReason());
            return PaymentResult.declined(e.getReason());
            
        } catch (PaymentGatewayException e) {
            // Infrastructure issue — retry may help
            log.warn("Gateway error, will retry: {}", e.getMessage());
            throw new RetryableException("Payment gateway unavailable", e);
            
        } catch (Exception e) {
            // Unexpected — critical
            log.error("Unexpected payment error for order {}", request.getOrderId(), e);
            throw new PaymentProcessingException("Internal payment error", e);
        }
    }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between checked and unchecked exceptions?**
> Checked exceptions extend `Exception` (not RuntimeException) and MUST be either caught or declared with `throws`. The compiler enforces this. Examples: IOException, SQLException. Unchecked exceptions extend `RuntimeException` and don't require explicit handling. Examples: NPE, IllegalArgumentException. Use checked for recoverable conditions (file not found), unchecked for programming errors (null access).

**Q2: What is the difference between `throw` and `throws`?**
> `throw` is used to explicitly throw an exception object: `throw new Exception("msg")`. `throws` is used in method signature to declare that the method might throw checked exceptions: `void read() throws IOException`. `throw` creates/throws, `throws` declares.

**Q3: Can finally block prevent an exception from propagating?**
> No, unless you catch the exception in the finally block (which is very bad practice). If the finally block itself throws an exception, it replaces the original exception (which is lost unless using try-with-resources suppressed exceptions). Finally always runs, but it doesn't swallow exceptions.

**Q4: What happens if both try and finally have return statements?**
> The finally block's return overrides the try block's return. This is extremely confusing and should NEVER be done. The JVM always gives priority to finally.

**Q5: What is exception chaining and why is it important?**
> Wrapping a low-level exception as the "cause" of a higher-level exception: `throw new ServiceException("msg", originalException)`. Important because: (1) preserves the root cause for debugging, (2) provides appropriate abstraction level to callers, (3) doesn't leak implementation details (caller doesn't need to know about SQL errors).

**Q6: Explain try-with-resources. What interface must the resource implement?**
> Try-with-resources (Java 7+) automatically closes resources after the try block. Resources must implement `AutoCloseable` (or `Closeable`). Resources are closed in reverse declaration order. If both try body and close() throw, the close exception is suppressed and attached to the primary exception.

---

## Follow-up Questions and Answers

**Q: Should you catch Error?**
> Generally no. Errors represent JVM-level problems (OutOfMemoryError, StackOverflowError) that are unrecoverable. Exception: In servers/frameworks, you might catch `Throwable` at the top level to log the error before the thread dies, but you shouldn't try to continue normally after an Error.

**Q: Is there performance overhead for try-catch when no exception is thrown?**
> No. Modern JVMs use exception tables (not older "setjmp/longjmp" approach). When no exception occurs, try-catch has literally zero runtime overhead. The cost occurs only when an exception is actually thrown (stack trace capture + unwinding).

**Q: Why shouldn't exceptions be used for flow control?**
> Performance: throwing an exception is ~100-1000x slower than an if-check (due to stack trace capture). Readability: exception flow is non-linear and harder to follow. Design: exceptions should represent exceptional conditions, not expected business logic. Use Optional, result types, or status codes for expected cases.

**Q: What are suppressed exceptions?**
> When multiple exceptions occur (e.g., in try-with-resources: body throws + close throws), only one can be propagated. The secondary exceptions are "suppressed" — attached to the primary via `addSuppressed()`. Accessible via `getSuppressed()`. This prevents exception loss that occurred before Java 7.

---

## Common Mistakes

1. **Catching Exception or Throwable too broadly**
   ```java
   catch (Exception e) { } // swallows ALL exceptions — hides bugs!
   // Fix: Catch specific exceptions
   ```

2. **Empty catch block**
   ```java
   catch (IOException e) { } // silently swallows error — worst practice
   // Fix: at minimum, log it: logger.error("IO error", e);
   ```

3. **Losing the original exception**
   ```java
   catch (SQLException e) {
       throw new ServiceException("error"); // LOST original cause!
   }
   // Fix: throw new ServiceException("error", e);
   ```

4. **Using exceptions for flow control**
   ```java
   try {
       int value = Integer.parseInt(input);
   } catch (NumberFormatException e) {
       value = 0; // Using exception as if-else — SLOW
   }
   // Better: validate first, or use tryParse utility
   ```

5. **Catching and rethrowing without purpose**
   ```java
   catch (IOException e) {
       throw e; // useless — just don't catch it
   }
   ```

6. **Return in finally**
   ```java
   finally { return result; } // overrides try's return — extremely confusing
   ```

7. **Not closing resources**
   ```java
   InputStream is = new FileInputStream("file");
   is.read(); // if this throws, 'is' is never closed!
   // Fix: try-with-resources
   ```

---

## Best Practices

1. **Use specific exceptions** — catch the most specific type possible.
2. **Never swallow exceptions** — at minimum log them.
3. **Use try-with-resources** for ALL closeable resources.
4. **Throw early, catch late** — validate at entry points, handle at appropriate level.
5. **Include context in messages**: `"User " + userId + " not found"` not just `"Not found"`.
6. **Use exception chaining** — always pass the cause.
7. **Create custom exceptions** for your domain — better than reusing generic ones.
8. **Don't use exceptions for flow control** — use Optional, conditionals, or result types.
9. **Log at the handling point**, not at every rethrow point (avoids duplicate logs).
10. **Document thrown exceptions** with @throws JavaDoc.

---

## Production Considerations

- **Stack trace cost**: In high-throughput systems, exception creation can be a bottleneck. For expected business exceptions (validation), consider overriding `fillInStackTrace()` to return `this` (no trace capture).

- **Exception translation layers**: In layered architecture:
  - Repository → DataAccessException
  - Service → ServiceException / BusinessException
  - Controller → @ExceptionHandler → ErrorResponse

- **Monitoring**: Track exception rates per type. Sudden spikes indicate problems. Use structured logging: `log.error("msg", Map.of("orderId", id, "userId", userId), exception)`.

- **Circuit breakers**: When downstream services throw exceptions consistently, use circuit breakers (Resilience4j) to fail fast instead of accumulating slow failures.

- **Never expose stack traces to clients**: Return sanitized error responses. Log full details server-side.

- **Retry-safe exceptions**: Distinguish between retryable (network timeout) and non-retryable (validation error) exceptions for proper retry policies.

---

## Related Topics

- → [1. Java Fundamentals](./01-java-fundamentals.md)
- → [14. Multithreading](./14-multithreading.md) (InterruptedException)
- → [27. I/O](./27-io.md) (IOException, try-with-resources)
- → [30. JDBC](./30-jdbc.md) (SQLException)
- → [32. Modern Java](./32-modern-java.md) (enhanced exception handling)
