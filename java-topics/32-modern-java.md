# 32. Modern Java (Java 9 to 21+)

## Theory

Modern Java refers to features introduced after Java 8. The Java platform shifted to a 6-month release cycle starting with Java 9 (2017), delivering features incrementally. Key milestones:

- **Java 9 (2017)**: Module system, JShell, collection factories
- **Java 10 (2018)**: Local variable type inference (var)
- **Java 11 (2018 LTS)**: String enhancements, HttpClient, single-file execution
- **Java 14 (2020)**: Switch expressions, helpful NullPointerExceptions
- **Java 15 (2020)**: Text blocks
- **Java 16 (2021)**: Records, pattern matching for instanceof
- **Java 17 (2021 LTS)**: Sealed classes
- **Java 21 (2023 LTS)**: Virtual threads, pattern matching for switch, sequenced collections, record patterns

### Why Modern Java Matters
- Reduces boilerplate significantly (records, var, text blocks)
- Improves null safety (pattern matching, Optional enhancements)
- Enables high-throughput concurrency (virtual threads)
- Better data modeling (sealed classes, records)
- Frequently asked in interviews for Java 11+ positions

---

## Internal Working

### Java Release Model

```
Before Java 9:            After Java 9:
Java 6 (2006)            Java 9  (Sep 2017)
    ↓ 5 years            Java 10 (Mar 2018)
Java 7 (2011)            Java 11 (Sep 2018) ← LTS
    ↓ 3 years            Java 12 (Mar 2019)
Java 8 (2014)            ...every 6 months...
    ↓ 3 years            Java 17 (Sep 2021) ← LTS
Java 9 (2017)            ...
                         Java 21 (Sep 2023) ← LTS

LTS = Long-Term Support (Oracle provides updates for years)
Non-LTS = Supported only until next release (6 months)
Production recommendation: Use LTS versions (11, 17, or 21)
```

### How Records Work Internally

```
Source: record Point(int x, int y) {}

Compiler generates:
├── final class Point extends java.lang.Record
├── private final int x;    (component fields)
├── private final int y;
├── public Point(int x, int y) { ... }  (canonical constructor)
├── public int x() { return x; }        (accessor - NOT getX())
├── public int y() { return y; }
├── public boolean equals(Object o) { ... } (component-based)
├── public int hashCode() { ... }           (component-based)
└── public String toString() { ... }        (Point[x=1, y=2])
```

### How Virtual Threads Work

```
Platform Threads (before Java 21):
┌─────────────────────────────────────────┐
│ Java Thread 1:1 maps to OS Thread       │
│ OS Thread → expensive (1-2 MB stack)    │
│ Limited to ~thousands per JVM           │
└─────────────────────────────────────────┘

Virtual Threads (Java 21):
┌─────────────────────────────────────────┐
│ Virtual Thread → mounted on Carrier     │
│ Carrier Thread = Platform Thread        │
│ When VT blocks (I/O) → unmounted       │
│ Carrier freed for other Virtual Threads │
│ Millions of VTs possible                │
└─────────────────────────────────────────┘

Scheduling:
Virtual Thread 1 ──[running]──[blocked I/O]──────────[resume]──
Virtual Thread 2 ────────────[running]──[blocked]────────────────
                   ↕            ↕            ↕         ↕
Carrier Thread:  [VT1]        [VT2]       [VT1]     [VT2]
(Only 1 platform thread serving multiple virtual threads)
```

### How Sealed Classes Work

```
sealed interface Shape permits Circle, Rectangle, Triangle {}

Compiler enforces:
1. Only Circle, Rectangle, Triangle can implement Shape
2. Each permitted class must be: final, sealed, or non-sealed
3. All permitted classes must be in same module (or package)

Pattern matching exhaustiveness:
switch (shape) {
    case Circle c    → ...  // compiler knows all cases
    case Rectangle r → ...  // no default needed
    case Triangle t  → ...  // exhaustive!
}
```

---

## Diagram

### Feature Timeline

```
Java 9:  modules | JShell | List.of() | Set.of() | Map.of()
Java 10: var
Java 11: var in lambdas | String.isBlank() | Files.readString()
Java 14: switch expressions | NullPointerException messages
Java 15: text blocks (""")
Java 16: record | Pattern matching instanceof
Java 17: sealed classes/interfaces
Java 21: virtual threads | record patterns | switch pattern matching
         | sequenced collections
```

### Sealed Class Hierarchy

```
sealed interface Shape
    permits Circle, Rectangle, Triangle
         ↓            ↓            ↓
  final class    sealed class    non-sealed class
    Circle        Rectangle        Triangle
                     ↓                 ↓
              permits Square      Anyone can extend
                     ↓
              final class Square
```

---

## Code

### Java 9: Collection Factory Methods

```java
import java.util.*;

public class Java9Features {
    public static void main(String[] args) {
        // Immutable collections (Java 9)
        List<String> list = List.of("a", "b", "c");
        Set<Integer> set = Set.of(1, 2, 3);
        Map<String, Integer> map = Map.of("one", 1, "two", 2, "three", 3);
        
        // Map with more than 10 entries
        Map<String, Integer> bigMap = Map.ofEntries(
            Map.entry("one", 1),
            Map.entry("two", 2),
            Map.entry("three", 3)
        );
        
        // These are IMMUTABLE - UnsupportedOperationException on modification
        // list.add("d"); // throws!
        
        // Optional enhancements (Java 9)
        Optional<String> opt = Optional.of("hello");
        opt.ifPresentOrElse(
            val -> System.out.println("Value: " + val),
            () -> System.out.println("Empty")
        );
        
        // Optional.or() - lazy alternative
        Optional<String> result = Optional.<String>empty()
            .or(() -> Optional.of("default"));
        
        // Optional.stream()
        List<String> values = Optional.of("hello")
            .stream()
            .toList();
        
        // Private interface methods (Java 9)
    }
}

interface Loggable {
    default void logInfo(String msg) { log("INFO", msg); }
    default void logError(String msg) { log("ERROR", msg); }
    
    // Private method - shared logic between defaults
    private void log(String level, String msg) {
        System.out.printf("[%s] %s: %s%n", 
            java.time.LocalDateTime.now(), level, msg);
    }
}
```

### Java 10: var (Local Variable Type Inference)

```java
public class Java10Var {
    public static void main(String[] args) {
        // var infers the type from the right side
        var name = "John";              // String
        var age = 30;                   // int
        var list = new ArrayList<String>(); // ArrayList<String>
        var map = Map.of("a", 1);       // Map<String, Integer>
        
        // Useful for complex generic types
        var entries = map.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .collect(Collectors.toList());
        // Instead of: List<Map.Entry<String, Integer>> entries = ...
        
        // var in for loops
        for (var entry : map.entrySet()) {
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
        
        // var in try-with-resources
        try (var reader = new java.io.BufferedReader(
                new java.io.FileReader("file.txt"))) {
            var line = reader.readLine();
        } catch (Exception e) { }
        
        // Java 11: var in lambda parameters
        // Useful when you need annotations on lambda params
        // (@NotNull var x, @Nullable var y) -> x + y
    }
}
```

#### var Rules and Restrictions

```java
public class VarRules {
    // var CANNOT be used for:
    // var field = "hello";           // ❌ class fields
    // var param                      // ❌ method parameters
    // var returnType() { }           // ❌ return types
    // var x;                         // ❌ without initializer
    // var arr = {1, 2, 3};           // ❌ array initializer
    // var nothing = null;            // ❌ null literal
    
    void validUsages() {
        var x = 10;                   // ✅ local variable
        var list = List.of(1, 2);     // ✅ with initializer
        for (var i = 0; i < 10; i++) {} // ✅ for loop
    }
}
```

### Java 14: Switch Expressions

```java
public class SwitchExpressions {
    
    // Old switch (statement)
    String oldSwitch(int day) {
        String result;
        switch (day) {
            case 1: result = "Monday"; break;
            case 2: result = "Tuesday"; break;
            default: result = "Other"; break;
        }
        return result;
    }
    
    // New switch (expression) - returns a value
    String newSwitch(int day) {
        return switch (day) {
            case 1 -> "Monday";       // Arrow syntax, no break needed
            case 2 -> "Tuesday";
            case 3, 4, 5 -> "Midweek"; // Multiple labels
            default -> "Other";
        };
    }
    
    // Switch expression with blocks
    String withBlock(int day) {
        return switch (day) {
            case 1 -> {
                System.out.println("Start of week");
                yield "Monday"; // yield returns value from block
            }
            case 7 -> {
                System.out.println("Weekend!");
                yield "Sunday";
            }
            default -> "Other";
        };
    }
    
    // Pattern matching in switch (Java 21)
    String describe(Object obj) {
        return switch (obj) {
            case Integer i when i > 0 -> "Positive int: " + i;
            case Integer i            -> "Non-positive int: " + i;
            case String s when s.length() > 5 -> "Long string: " + s;
            case String s             -> "Short string: " + s;
            case null                 -> "null value";
            default                   -> "Unknown: " + obj.getClass();
        };
    }
}
```

### Java 15: Text Blocks

```java
public class TextBlocks {
    
    // Before: painful string concatenation
    String oldJson = "{\n" +
        "  \"name\": \"John\",\n" +
        "  \"age\": 30,\n" +
        "  \"address\": {\n" +
        "    \"city\": \"NYC\"\n" +
        "  }\n" +
        "}";
    
    // After: text blocks (triple quotes)
    String newJson = """
            {
              "name": "John",
              "age": 30,
              "address": {
                "city": "NYC"
              }
            }
            """;
    
    // SQL queries
    String sql = """
            SELECT e.name, e.salary, d.department_name
            FROM employees e
            JOIN departments d ON e.dept_id = d.id
            WHERE e.salary > ?
            ORDER BY e.salary DESC
            """;
    
    // HTML
    String html = """
            <html>
                <body>
                    <h1>%s</h1>
                    <p>%s</p>
                </body>
            </html>
            """.formatted("Title", "Content");
    
    // Indentation is determined by closing """ position
    // Trailing whitespace can be preserved with \s
    // Line continuation with \ (no newline)
    String singleLine = """
            This is a very long line that I want to \
            keep on one line in the output""";
}
```

### Java 16: Records

```java
// Record declaration - that's it! Full class generated.
public record Point(int x, int y) {}

// Record with validation
public record Range(int start, int end) {
    // Compact constructor (no parameter list)
    public Range {
        if (start > end) {
            throw new IllegalArgumentException("start must be <= end");
        }
    }
}

// Record with custom methods
public record Employee(String name, String department, double salary) {
    
    // Custom accessor
    public String displayName() {
        return name.toUpperCase() + " (" + department + ")";
    }
    
    // Static factory method
    public static Employee of(String name, double salary) {
        return new Employee(name, "General", salary);
    }
}

// Record implementing interface
public record Circle(double radius) implements Shape {
    public double area() {
        return Math.PI * radius * radius;
    }
}

// Usage
public class RecordDemo {
    public static void main(String[] args) {
        var p1 = new Point(1, 2);
        var p2 = new Point(1, 2);
        
        System.out.println(p1.x());        // 1 (accessor, not getX())
        System.out.println(p1.y());        // 2
        System.out.println(p1);            // Point[x=1, y=2]
        System.out.println(p1.equals(p2)); // true (component-based)
        System.out.println(p1.hashCode() == p2.hashCode()); // true
        
        // Records work great with streams
        var employees = List.of(
            new Employee("Alice", "Eng", 90000),
            new Employee("Bob", "Sales", 70000)
        );
        
        var byDept = employees.stream()
            .collect(Collectors.groupingBy(Employee::department));
    }
}
```

### Java 16: Pattern Matching for instanceof

```java
public class PatternMatchingInstanceof {
    
    // Old way
    void oldWay(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;  // Manual cast
            System.out.println(s.length());
        }
    }
    
    // New way (Java 16)
    void newWay(Object obj) {
        if (obj instanceof String s) {  // Pattern variable 's' created
            System.out.println(s.length()); // s is already a String
        }
        
        // Works with && (but NOT ||)
        if (obj instanceof String s && s.length() > 5) {
            System.out.println("Long string: " + s);
        }
        
        // Negation pattern
        if (!(obj instanceof String s)) {
            return; // early return
        }
        // s is in scope here (flow scoping)
        System.out.println(s.toUpperCase());
    }
    
    // Real use case: equals() method
    record Point(int x, int y) {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Point p 
                && this.x == p.x 
                && this.y == p.y;
        }
    }
}
```

### Java 17: Sealed Classes

```java
// Sealed interface - only permitted classes can implement
public sealed interface Shape 
    permits Circle, Rectangle, Triangle {
    double area();
}

// Must be final, sealed, or non-sealed
public final class Circle implements Shape {
    private final double radius;
    public Circle(double radius) { this.radius = radius; }
    public double area() { return Math.PI * radius * radius; }
}

public sealed class Rectangle implements Shape 
    permits Square {
    protected final double width, height;
    public Rectangle(double w, double h) { this.width = w; this.height = h; }
    public double area() { return width * height; }
}

public final class Square extends Rectangle {
    public Square(double side) { super(side, side); }
}

// non-sealed: open for extension by anyone
public non-sealed class Triangle implements Shape {
    private final double base, height;
    public Triangle(double b, double h) { this.base = b; this.height = h; }
    public double area() { return 0.5 * base * height; }
}

// Exhaustive switch (no default needed!)
public class ShapeProcessor {
    String describe(Shape shape) {
        return switch (shape) {
            case Circle c    -> "Circle with radius " + c.radius;
            case Rectangle r -> "Rectangle " + r.width + "x" + r.height;
            case Triangle t  -> "Triangle";
        }; // Compiler knows all cases are covered
    }
}
```

### Java 21: Virtual Threads

```java
import java.util.concurrent.*;
import java.time.Duration;

public class VirtualThreadsDemo {
    
    public static void main(String[] args) throws Exception {
        // Create a single virtual thread
        Thread vt = Thread.ofVirtual().name("my-vt").start(() -> {
            System.out.println("Running on: " + Thread.currentThread());
        });
        vt.join();
        
        // Virtual thread per task executor
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Submit 100,000 tasks - impossible with platform threads!
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 100_000; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    Thread.sleep(Duration.ofSeconds(1)); // Simulates I/O
                    return "Result-" + id;
                }));
            }
            
            // Collect results
            for (var future : futures) {
                future.get(); // All 100K complete in ~1-2 seconds!
            }
        }
        
        // Structured Concurrency (Preview in Java 21)
        // try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        //     var user = scope.fork(() -> fetchUser(id));
        //     var orders = scope.fork(() -> fetchOrders(id));
        //     scope.join();
        //     scope.throwIfFailed();
        //     return new UserProfile(user.get(), orders.get());
        // }
    }
    
    // Platform thread vs Virtual thread comparison
    static void comparison() throws Exception {
        // Platform threads: limited to ~few thousand
        long start = System.currentTimeMillis();
        try (var executor = Executors.newFixedThreadPool(200)) {
            for (int i = 0; i < 10_000; i++) {
                executor.submit(() -> {
                    try { Thread.sleep(100); } catch (Exception e) {}
                });
            }
        }
        System.out.println("Platform: " + (System.currentTimeMillis() - start) + "ms");
        
        // Virtual threads: can handle millions
        start = System.currentTimeMillis();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                executor.submit(() -> {
                    try { Thread.sleep(100); } catch (Exception e) {}
                });
            }
        }
        System.out.println("Virtual: " + (System.currentTimeMillis() - start) + "ms");
    }
}
```

### Java 21: Record Patterns and Pattern Matching for Switch

```java
public class RecordPatterns {
    
    record Point(int x, int y) {}
    record Circle(Point center, double radius) {}
    record Line(Point start, Point end) {}
    
    // Record patterns - destructure in patterns
    void processShape(Object obj) {
        if (obj instanceof Circle(Point(var x, var y), var r)) {
            // Destructured: x, y from center Point, r from Circle
            System.out.printf("Circle at (%d,%d) radius %.1f%n", x, y, r);
        }
    }
    
    // Pattern matching in switch with guards
    String classify(Shape shape) {
        return switch (shape) {
            case Circle(Point(var x, var y), var r) when r > 10 
                -> "Large circle at " + x + "," + y;
            case Circle c 
                -> "Small circle";
            case Line(Point(var x1, var y1), Point(var x2, var y2))
                -> "Line from (%d,%d) to (%d,%d)".formatted(x1, y1, x2, y2);
            default 
                -> "Unknown shape";
        };
    }
    
    // Null handling in switch
    String process(String input) {
        return switch (input) {
            case null -> "null input";
            case String s when s.isBlank() -> "blank";
            case String s -> "value: " + s;
        };
    }
}
```

### Java 21: Sequenced Collections

```java
import java.util.*;

public class SequencedCollectionsDemo {
    
    public static void main(String[] args) {
        // SequencedCollection: defined encounter order with first/last access
        SequencedCollection<String> list = new ArrayList<>(List.of("a", "b", "c"));
        
        String first = list.getFirst();  // "a"
        String last = list.getLast();     // "c"
        
        list.addFirst("z");  // ["z", "a", "b", "c"]
        list.addLast("d");   // ["z", "a", "b", "c", "d"]
        
        list.removeFirst();  // removes "z"
        list.removeLast();   // removes "d"
        
        // Reversed view (not a copy!)
        SequencedCollection<String> reversed = list.reversed();
        // ["c", "b", "a"]
        
        // SequencedSet
        SequencedSet<String> set = new LinkedHashSet<>(List.of("x", "y", "z"));
        set.getFirst(); // "x"
        set.getLast();  // "z"
        
        // SequencedMap
        SequencedMap<String, Integer> map = new LinkedHashMap<>();
        map.put("one", 1);
        map.put("two", 2);
        map.put("three", 3);
        
        var firstEntry = map.firstEntry();  // one=1
        var lastEntry = map.lastEntry();    // three=3
        map.putFirst("zero", 0);
        
        SequencedMap<String, Integer> reversedMap = map.reversed();
    }
}
```

---

## Dry Run

### Record Pattern Matching

```
Input: Object obj = new Circle(new Point(3, 4), 5.0)

Pattern: obj instanceof Circle(Point(var x, var y), var r)

Step 1: Is obj a Circle? → YES
Step 2: Destructure Circle → center=Point(3,4), radius=5.0
Step 3: Is center a Point? → YES (nested pattern)
Step 4: Destructure Point → x=3, y=4
Step 5: Bind r=5.0
Result: x=3, y=4, r=5.0 all available as local variables
```

### Virtual Thread Scheduling

```
3 Virtual Threads (VT1, VT2, VT3), 1 Carrier (platform) Thread

Time 0ms:  VT1 starts → mounted on Carrier → executing code
Time 5ms:  VT1 hits Thread.sleep(100) → BLOCKS
           VT1 unmounted from Carrier (saved to heap)
           VT2 mounted on Carrier → executing code
Time 8ms:  VT2 hits HTTP call → BLOCKS
           VT2 unmounted, VT3 mounted → executing
Time 15ms: VT3 completes → unmounted
           VT1's sleep finished → VT1 remounted → continues
Time 105ms: VT1 completes
Time 108ms: VT2's HTTP response arrives → mounted → continues
Time 110ms: VT2 completes

All 3 tasks done using just 1 OS thread!
```

---

## Complexity

| Feature | Runtime Cost | Benefit |
|---------|-------------|---------|
| var | Zero (compile-time only) | Readability |
| Records | Same as hand-written class | Less boilerplate |
| Sealed classes | Zero runtime overhead | Compile-time exhaustiveness |
| Text blocks | Zero (compile-time processing) | Readability |
| Switch expressions | Same as switch statement | Type safety |
| Virtual threads | ~200 bytes per VT (vs 1-2 MB platform) | Massive scalability |
| Pattern matching | Equivalent to instanceof + cast | Safer, more concise |

---

## Real Project Usage

### 1. Modern Spring Boot Service (Java 21)

```java
// Using records as DTOs
public record CreateUserRequest(
    @NotBlank String name,
    @Email String email,
    @Min(18) int age
) {}

public record UserResponse(
    Long id,
    String name,
    String email,
    LocalDateTime createdAt
) {
    public static UserResponse from(User entity) {
        return new UserResponse(
            entity.getId(),
            entity.getName(),
            entity.getEmail(),
            entity.getCreatedAt()
        );
    }
}

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        var user = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
```

### 2. Sealed Interface for Domain Modeling

```java
public sealed interface PaymentResult 
    permits PaymentResult.Success, PaymentResult.Failed, PaymentResult.Pending {
    
    record Success(String transactionId, BigDecimal amount) implements PaymentResult {}
    record Failed(String errorCode, String message) implements PaymentResult {}
    record Pending(String referenceId, Duration estimatedWait) implements PaymentResult {}
}

// Exhaustive handling
public class PaymentHandler {
    String handleResult(PaymentResult result) {
        return switch (result) {
            case PaymentResult.Success s -> 
                "Payment successful: " + s.transactionId();
            case PaymentResult.Failed f -> 
                "Payment failed: " + f.message();
            case PaymentResult.Pending p -> 
                "Payment pending, wait " + p.estimatedWait();
        };
    }
}
```

### 3. Virtual Threads in Web Server

```java
// Spring Boot 3.2+ with virtual threads
// application.properties: spring.threads.virtual.enabled=true

// Or manual configuration
@Configuration
public class VirtualThreadConfig {
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandler() {
        return handler -> handler.setExecutor(
            Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

---

## Interview Questions and Answers

### Q1: What are Records? When to use them vs regular classes?
**A**: Records are immutable data carriers that auto-generate constructor, accessors, equals(), hashCode(), and toString(). Use records for DTOs, value objects, API responses, and data transfer. Use regular classes when you need: mutability, inheritance (records are final), custom field encapsulation, or complex behavior beyond data holding.

### Q2: What are sealed classes?
**A**: Sealed classes restrict which classes can extend/implement them using `permits`. All subtypes must be `final`, `sealed`, or `non-sealed`. Benefits: exhaustive pattern matching in switch (compiler knows all cases), better domain modeling, controlled extension. Used for algebraic data types (Sum types) like Result<Success, Error>.

### Q3: What are virtual threads? How are they different from platform threads?
**A**: Virtual threads are lightweight threads managed by the JVM (not OS). A platform thread maps 1:1 to an OS thread (1-2MB stack each, limited to thousands). Virtual threads use ~200 bytes, are multiplexed onto carrier (platform) threads, and can scale to millions. When a virtual thread blocks (I/O), it's unmounted and the carrier handles another VT. Use for I/O-bound workloads (web servers, database calls), NOT CPU-bound work.

### Q4: When should you use var?
**A**: Use var when: the type is obvious from the right side (`var list = new ArrayList<String>()`), the type is complex/verbose (streams, generics), or in for-each loops. Avoid when: the type isn't clear from context, for numeric types where precision matters (`var x = 1` — is it int? long?), or when it reduces readability for the team.

### Q5: What are text blocks used for?
**A**: Multi-line string literals (JSON, SQL, HTML, XML) without escape characters. Delimited by `"""`. Indentation is relative to the closing `"""`. Support `\s` (space), `\` (line continuation), and `.formatted()` for interpolation. Makes embedded DSLs and test fixtures much more readable.

### Q6: Explain pattern matching for switch (Java 21).
**A**: Switch can now match on types, destructure records, use guards (`when`), and handle null. Combined with sealed classes, enables exhaustive type-safe switching without default. Supports: type patterns (`case Integer i`), record patterns (`case Point(var x, var y)`), guarded patterns (`case String s when s.length() > 5`), and null (`case null`).

---

## Follow-up Questions and Answers

### Q: Can records implement interfaces?
**A**: Yes. Records can implement interfaces but cannot extend classes (they implicitly extend `java.lang.Record`). They can also have static fields, static methods, and instance methods. They cannot have non-static instance fields beyond the components declared in the header.

### Q: What is the difference between virtual threads and reactive programming?
**A**: Both solve the thread-per-request scalability problem but differently. Reactive (Project Reactor, RxJava) uses non-blocking callbacks and operators — different programming model, harder to debug, steep learning curve. Virtual threads keep the familiar blocking-style code but make blocking cheap. Virtual threads give you reactive-level scalability with imperative code style.

### Q: What are sequenced collections solving?
**A**: Before Java 21, getting the first/last element from a List, LinkedHashSet, or LinkedHashMap required different approaches (`list.get(0)`, `set.iterator().next()`, no easy "last" access). SequencedCollection/Set/Map interfaces unify this with `getFirst()`, `getLast()`, `addFirst()`, `reversed()` across all ordered collections.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using var everywhere | Readability suffers | Use when type is obvious or very verbose |
| Mutable fields in records | Records are meant to be immutable | Records enforce final fields; don't pass mutable collections without wrapping |
| Using virtual threads for CPU-bound work | No benefit, may be slower (scheduling overhead) | Use platform threads/parallel streams for CPU work |
| Forgetting `yield` in switch expression blocks | Compilation error | Use `yield` to return value from block |
| Not making sealed class subtypes final/sealed/non-sealed | Compilation error | Every permitted subtype must declare its extensibility |
| Blocking with synchronized in virtual threads | Pins carrier thread (defeats purpose) | Use ReentrantLock instead of synchronized |

---

## Best Practices

1. **Use records for all DTOs and value objects** — Less boilerplate, correct equals/hashCode for free
2. **Model domain states with sealed interfaces** — Exhaustive switch, no invalid states representable
3. **Adopt virtual threads for I/O-bound services** — Web servers, DB-heavy services benefit enormously
4. **Use text blocks for embedded SQL/JSON/HTML** — Much more readable than string concatenation
5. **Use var judiciously** — Great for complex types, bad when type isn't obvious
6. **Prefer switch expressions over if-else chains** — Exhaustiveness checking, cleaner code
7. **Use pattern matching instead of instanceof + cast** — Safer and more concise

---

## Production Considerations

- **LTS versions for production**: Use Java 17 or 21 in production. Non-LTS versions get only 6 months of patches.
- **Virtual thread pitfalls**: Avoid `synchronized` blocks (pins carrier thread). Use `ReentrantLock`. Avoid `ThreadLocal` (high memory if millions of VTs).
- **Records and frameworks**: Jackson, Spring, JPA all support records. Some limitations: JPA entities cannot be records (need mutable state). Records work perfectly as DTOs/projections.
- **Migration path**: Java 8 → 11 → 17 → 21 is typical. Each LTS adds major features. Modules (Java 9) are optional for applications.
- **Sealed classes and libraries**: Don't seal classes meant for user extension. Seal types representing fixed domain concepts (payment states, AST nodes, errors).

---

## Related Topics

- [10. Java 8 Features](./10-java8-features.md)
- [11. Stream API](./11-stream-api.md)
- [14. Multithreading Fundamentals](./14-multithreading-fundamentals.md)
- [17. Executor Framework](./17-executor-framework.md)
- [02. OOP Concepts](./02-oop-concepts.md)
