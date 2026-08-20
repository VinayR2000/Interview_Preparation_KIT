# 26. Annotations

## Theory

Annotations are metadata that provide information about code but do not directly affect program execution. They are a form of syntactic metadata added to Java source code — classes, methods, fields, parameters, and other program elements.

Annotations serve three main purposes:
1. **Information for the compiler** — Detect errors, suppress warnings (`@Override`, `@SuppressWarnings`)
2. **Compile-time processing** — Generate code, XML, etc. (annotation processors like Lombok)
3. **Runtime processing** — Frameworks examine annotations via reflection (Spring, JPA, JUnit)

### Annotation Categories

- **Marker annotations**: No elements, just a flag (`@Override`, `@Deprecated`)
- **Single-value annotations**: One element (`@SuppressWarnings("unchecked")`)
- **Multi-value annotations**: Multiple elements (`@RequestMapping(path="/api", method=GET)`)

---

## Internal Working

### How Annotations Are Stored

```
Java Source Code with @MyAnnotation
        ↓ (javac compiles)
.class file
├── Class bytecode
├── RuntimeVisibleAnnotations attribute    ← @Retention(RUNTIME)
├── RuntimeInvisibleAnnotations attribute  ← @Retention(CLASS)
└── (SOURCE annotations are discarded)    ← @Retention(SOURCE)
        ↓ (class loading)
Class object in JVM
├── Annotation metadata accessible via reflection
└── Annotation proxy objects created on demand
```

### Retention Policies

| Policy | Stored In | Accessible At | Use Case |
|--------|-----------|---------------|----------|
| `SOURCE` | Source code only | Compile time only | `@Override`, `@SuppressWarnings` |
| `CLASS` | .class file | Compile time + bytecode tools | Default if not specified |
| `RUNTIME` | .class file + loaded by JVM | Runtime via reflection | Spring, JPA, JUnit |

### How Runtime Annotations Work

```
1. Framework calls: clazz.getAnnotation(MyAnnotation.class)
      ↓
2. JVM checks RuntimeVisibleAnnotations table
      ↓
3. If found, creates a dynamic proxy implementing the annotation interface
      ↓
4. Proxy stores annotation element values
      ↓
5. Method calls on annotation proxy return stored values

Example:
  @RequestMapping(path = "/users", method = "GET")
  → Proxy implements RequestMapping interface
  → proxy.path() returns "/users"
  → proxy.method() returns "GET"
```

---

## Diagram

### Meta-Annotation Relationships

```
@Target          → WHERE can the annotation be used?
├── TYPE         (class, interface, enum, record)
├── FIELD        (instance/static fields)
├── METHOD       (methods)
├── PARAMETER    (method parameters)
├── CONSTRUCTOR  (constructors)
├── LOCAL_VARIABLE (local variables)
├── ANNOTATION_TYPE (other annotations)
├── PACKAGE      (package-info.java)
├── TYPE_PARAMETER (generic type parameters) [Java 8]
├── TYPE_USE     (any type usage) [Java 8]
├── MODULE       (modules) [Java 9]
└── RECORD_COMPONENT (record components) [Java 16]

@Retention       → HOW LONG is the annotation kept?
├── SOURCE       (discarded by compiler)
├── CLASS        (in .class file, not loaded at runtime)
└── RUNTIME      (available via reflection)

@Documented      → Include in Javadoc?
@Inherited       → Should subclasses inherit this annotation?
@Repeatable      → Can this annotation be applied multiple times?
```

### Annotation Processing Pipeline

```
                    Compile Time                     Runtime
                    ──────────────                   ─────────
Source Code    →   Annotation Processor  →   .class file   →   Reflection API
@Entity           (generates code,              (stored)        (frameworks read
@Override          validates, etc.)                               annotations)
@Getter           
                   Example: Lombok               Example:
                   generates getters             Spring reads @Component
                   at compile time               at runtime via reflection
```

---

## Code

### Built-in Annotations

```java
public class BuiltInAnnotations {
    
    @Override // Compiler checks this actually overrides a parent method
    public String toString() {
        return "Example";
    }
    
    @Deprecated(since = "2.0", forRemoval = true)
    public void oldMethod() {
        // Compiler warns callers
    }
    
    @SuppressWarnings("unchecked")
    public void rawTypeUsage() {
        List list = new ArrayList(); // Warning suppressed
    }
    
    @SafeVarargs // Suppresses heap pollution warning for varargs
    public final <T> void safeMethod(T... args) {
        for (T arg : args) {
            System.out.println(arg);
        }
    }
    
    @FunctionalInterface // Compiler ensures exactly one abstract method
    interface Processor {
        void process(String input);
    }
}
```

### Creating Custom Annotations

```java
import java.lang.annotation.*;

// Marker annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {
}

// Single-value annotation
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {
    String value(); // "value" is special - can omit name: @Table("users")
}

// Multi-value annotation with defaults
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int maxRequests() default 100;
    int windowSeconds() default 60;
    String key() default "";
}

// Annotation with array element
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Roles {
    String[] value();
}

// Usage
@Table("employees")
@Roles({"ADMIN", "MANAGER"})
public class Employee {
    
    @RateLimit(maxRequests = 10, windowSeconds = 30)
    public void sensitiveOperation() { }
    
    @RateLimit // Uses defaults: 100 requests per 60 seconds
    @Cacheable
    public List<Employee> getAll() { return List.of(); }
}
```

### Annotation with Enum Element

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduled {
    TimeUnit unit() default TimeUnit.SECONDS;
    long interval();
    boolean fixedRate() default false;
}

// Usage
@Scheduled(interval = 5, unit = TimeUnit.MINUTES)
public void cleanupJob() { }
```

### Repeatable Annotations (Java 8+)

```java
// Container annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Schedules {
    Schedule[] value();
}

// Repeatable annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Schedules.class)
public @interface Schedule {
    String cron();
    String timezone() default "UTC";
}

// Usage - can apply multiple times
public class TaskRunner {
    
    @Schedule(cron = "0 0 * * *")           // Daily at midnight UTC
    @Schedule(cron = "0 12 * * *", timezone = "US/Eastern") // Noon ET
    public void runReport() { }
}
```

### Inherited Annotations

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited // Subclasses will inherit this annotation
public @interface Auditable {
    String level() default "INFO";
}

@Auditable(level = "DEBUG")
public class BaseService { }

// AuditedService inherits @Auditable from BaseService
public class AuditedService extends BaseService { }

// Check at runtime:
// AuditedService.class.getAnnotation(Auditable.class) → not null!
```

### Processing Annotations at Runtime

```java
import java.lang.reflect.*;

public class AnnotationProcessor {
    
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Validate {
        int minLength() default 0;
        int maxLength() default Integer.MAX_VALUE;
        boolean notNull() default true;
    }
    
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Column {
        String name();
        boolean nullable() default true;
    }
    
    static class UserDTO {
        @Column(name = "user_name", nullable = false)
        private String username;
        
        @Column(name = "email_address")
        private String email;
        
        @Validate(minLength = 8, maxLength = 50)
        public void setPassword(String password) { }
    }
    
    // Runtime processing
    public static void processEntity(Class<?> clazz) {
        System.out.println("Processing: " + clazz.getSimpleName());
        
        // Process field annotations
        for (Field field : clazz.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null) {
                System.out.printf("  Field '%s' → Column '%s' (nullable=%s)%n",
                    field.getName(), column.name(), column.nullable());
            }
        }
        
        // Process method annotations
        for (Method method : clazz.getDeclaredMethods()) {
            Validate validate = method.getAnnotation(Validate.class);
            if (validate != null) {
                System.out.printf("  Method '%s' → Validate(min=%d, max=%d)%n",
                    method.getName(), validate.minLength(), validate.maxLength());
            }
        }
    }
    
    public static void main(String[] args) {
        processEntity(UserDTO.class);
        // Output:
        // Processing: UserDTO
        //   Field 'username' → Column 'user_name' (nullable=false)
        //   Field 'email' → Column 'email_address' (nullable=true)
        //   Method 'setPassword' → Validate(min=8, max=50)
    }
}
```

### Building a Simple Validation Framework

```java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

// Annotations
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface NotNull {
    String message() default "must not be null";
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface Size {
    int min() default 0;
    int max() default Integer.MAX_VALUE;
    String message() default "size out of range";
}

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface Min {
    long value();
    String message() default "must be greater than or equal to {value}";
}

// Validator
public class SimpleValidator {
    
    public static List<String> validate(Object obj) throws IllegalAccessException {
        List<String> errors = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(obj);
            
            // Check @NotNull
            if (field.isAnnotationPresent(NotNull.class)) {
                if (value == null) {
                    NotNull ann = field.getAnnotation(NotNull.class);
                    errors.add(field.getName() + ": " + ann.message());
                }
            }
            
            // Check @Size
            if (field.isAnnotationPresent(Size.class)) {
                Size size = field.getAnnotation(Size.class);
                if (value instanceof String s) {
                    if (s.length() < size.min() || s.length() > size.max()) {
                        errors.add(field.getName() + ": " + size.message() 
                            + " [" + size.min() + "-" + size.max() + "]");
                    }
                }
            }
            
            // Check @Min
            if (field.isAnnotationPresent(Min.class)) {
                Min min = field.getAnnotation(Min.class);
                if (value instanceof Number n) {
                    if (n.longValue() < min.value()) {
                        errors.add(field.getName() + ": must be >= " + min.value());
                    }
                }
            }
        }
        
        return errors;
    }
    
    // Usage
    static class CreateUserRequest {
        @NotNull @Size(min = 3, max = 20)
        private String username;
        
        @NotNull @Size(min = 8)
        private String password;
        
        @Min(18)
        private int age;
        
        CreateUserRequest(String username, String password, int age) {
            this.username = username;
            this.password = password;
            this.age = age;
        }
    }
    
    public static void main(String[] args) throws Exception {
        CreateUserRequest req = new CreateUserRequest("ab", null, 15);
        List<String> errors = validate(req);
        errors.forEach(System.out::println);
        // username: size out of range [3-20]
        // password: must not be null
        // age: must be >= 18
    }
}
```

---

## Dry Run

### Annotation Resolution at Runtime

```
Code: method.getAnnotation(RateLimit.class)

Step 1: JVM checks Method's RuntimeVisibleAnnotations attribute
  → Found: RateLimit annotation data
    - maxRequests = 10
    - windowSeconds = 30
    - key = "" (default)

Step 2: Create dynamic proxy implementing RateLimit interface
  → proxy = Proxy.newProxyInstance(...)
  → InvocationHandler stores annotation values in a Map

Step 3: Return proxy as RateLimit reference

Step 4: When caller invokes proxy.maxRequests()
  → InvocationHandler.invoke() is called
  → Looks up "maxRequests" in stored values Map
  → Returns 10
```

---

## Complexity

| Operation | Cost | Notes |
|-----------|------|-------|
| getAnnotation() | O(n) | n = number of annotations on element |
| isAnnotationPresent() | O(n) | Linear search through annotation list |
| Annotation proxy creation | O(1) | Created once, cached |
| Annotation method call | O(1) | Map lookup in proxy |
| Full class annotation scan | O(f + m + c) | f=fields, m=methods, c=constructors |

Annotations themselves add zero runtime cost unless accessed via reflection.

---

## Real Project Usage

### 1. Spring Boot Controller

```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/{id}")
    @Cacheable("users")
    public ResponseEntity<UserDTO> getUser(
            @PathVariable @Min(1) Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDTO createUser(
            @RequestBody @Valid CreateUserRequest request) {
        return userService.create(request);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

### 2. JPA Entity

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_status", columnList = "status")
})
@NamedQuery(name = "Order.findByStatus",
    query = "SELECT o FROM Order o WHERE o.status = :status")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "customer_id", nullable = false)
    private Long customerId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
```

### 3. Custom Annotation for Cross-Cutting Concerns

```java
// Custom annotation for audit logging
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String resource() default "";
}

// AOP aspect that processes @Audited
@Aspect
@Component
public class AuditAspect {
    
    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String user = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            auditLog.record(user, audited.action(), audited.resource(), 
                "SUCCESS", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            auditLog.record(user, audited.action(), audited.resource(), 
                "FAILED", System.currentTimeMillis() - start);
            throw e;
        }
    }
}

// Usage
@Audited(action = "DELETE", resource = "user")
public void deleteUser(Long id) { ... }
```

---

## Interview Questions and Answers

### Q1: What are annotations in Java?
**A**: Annotations are metadata that can be attached to classes, methods, fields, parameters, and other program elements. They don't directly affect execution but provide information used by the compiler (compile-time checks), build tools (code generation), or frameworks (runtime processing via reflection).

### Q2: What is @Retention and its policies?
**A**: `@Retention` specifies how long an annotation is kept:
- `SOURCE` — discarded during compilation (e.g., `@Override`)
- `CLASS` — stored in .class file but not loaded at runtime (default)
- `RUNTIME` — available at runtime via reflection (e.g., `@Component`, `@Transactional`)

Most framework annotations use `RUNTIME` because they need to be read at application startup.

### Q3: What is @Target?
**A**: `@Target` specifies where an annotation can be applied: TYPE (classes/interfaces), METHOD, FIELD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE (meta-annotations), PACKAGE, TYPE_PARAMETER, TYPE_USE, MODULE, RECORD_COMPONENT. Without `@Target`, the annotation can be used anywhere.

### Q4: How do you create a custom annotation?
**A**: Use `@interface` syntax. Elements look like methods but are actually attributes. They can have default values, and the allowed types are: primitives, String, Class, enums, annotations, and arrays of these types.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {
    int maxAttempts() default 3;
    long delayMs() default 1000;
    Class<? extends Exception>[] retryOn() default {Exception.class};
}
```

### Q5: What is the difference between @Override and @Deprecated?
**A**: Both are marker annotations but serve different purposes:
- `@Override` — SOURCE retention, tells compiler to verify the method actually overrides a parent method. Compile error if not.
- `@Deprecated` — RUNTIME retention, marks code as obsolete. Compiler issues warnings when deprecated code is used. Has `since` and `forRemoval` attributes since Java 9.

### Q6: What is @Inherited?
**A**: When `@Inherited` is placed on an annotation definition, and that annotation is applied to a class, then subclasses automatically inherit the annotation without explicitly declaring it. Only works for class-level annotations, not methods/fields.

---

## Follow-up Questions and Answers

### Q: What are annotation processors?
**A**: Annotation processors run at compile time (via `javac`) and can read annotations from source code, generate new source files, report errors/warnings, but cannot modify existing source files. They implement `javax.annotation.processing.Processor`. Examples: Lombok (generates boilerplate), MapStruct (generates mappers), Dagger (generates DI code).

### Q: What are TYPE_USE annotations (Java 8+)?
**A**: TYPE_USE annotations can be placed anywhere a type is used, not just declarations. This enables type annotations for null checking and other type-system extensions:
```java
@NonNull String name;
List<@NonNull String> items;
String @NonNull [] array;
```

### Q: How do composed annotations work in Spring?
**A**: Spring supports meta-annotations — annotations on annotations. `@RestController` is composed of `@Controller` + `@ResponseBody`. Spring's annotation search uses `AnnotationUtils.findAnnotation()` which traverses annotation hierarchies, allowing custom composed annotations.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Forgetting `@Retention(RUNTIME)` | Annotation not available via reflection | Always specify RUNTIME for framework annotations |
| Wrong `@Target` | Compilation error when applying annotation | Specify all intended targets |
| Using mutable types in annotation elements | Compilation error | Use only: primitives, String, Class, enums, annotations, arrays |
| Expecting `null` as default value | Not allowed in annotations | Use empty string/array or sentinel value |
| Forgetting `@Repeatable` container | Cannot apply same annotation twice | Define container annotation with value() array |
| Confusing getAnnotation vs getDeclaredAnnotation | Missing inherited annotations (or including unwanted ones) | Choose based on whether inheritance is needed |

---

## Best Practices

1. **Always specify @Target and @Retention** — Makes intent explicit
2. **Use RUNTIME retention for framework annotations** — Required for reflection-based processing
3. **Provide meaningful defaults** — Reduce boilerplate at usage sites
4. **Name single-element annotations "value"** — Enables `@Ann("x")` shorthand instead of `@Ann(value="x")`
5. **Document annotation semantics** — JavaDoc what the annotation does and its requirements
6. **Prefer composed annotations** — Combine multiple annotations into domain-specific ones
7. **Validate annotation usage** — Use annotation processors to catch misuse at compile time

---

## Production Considerations

- **Startup performance**: Classpath scanning for annotated classes (Spring `@ComponentScan`) can slow startup. Use explicit configuration or Spring Native for faster startup.
- **Reflection cost**: First access to annotations involves reflection. Spring caches annotation metadata after first access.
- **GraalVM/Native compilation**: All reflection-based annotation access must be declared in `reflect-config.json`. Spring AOT processing handles this.
- **Library evolution**: Annotation changes are binary-compatible (adding elements with defaults doesn't break existing code). But removing elements breaks at runtime.
- **Testing**: Annotations themselves can't be unit tested. Test the processors/aspects that consume them.

---

## Related Topics

- [25. Reflection](./25-reflection.md)
- [23. Class Loading](./23-class-loading.md)
- [08. Generics](./08-generics.md)
