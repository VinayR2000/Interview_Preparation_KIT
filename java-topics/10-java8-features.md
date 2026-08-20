# 10. Java 8 Features — Lambda & Functional Interfaces

---

## Theory

Java 8 introduced **functional programming** capabilities to Java. The two foundational features are:

1. **Lambda Expressions** — anonymous functions that can be passed around as values
2. **Functional Interfaces** — interfaces with exactly one abstract method (SAM — Single Abstract Method)

### Why Java 8 Changed Everything

Before Java 8, behavior parameterization required verbose anonymous inner classes:

```java
// Before Java 8
Collections.sort(list, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.length() - b.length();
    }
});

// Java 8+
list.sort((a, b) -> a.length() - b.length());
// Or even better:
list.sort(Comparator.comparingInt(String::length));
```

---

## Internal Working

### Lambda Expression Compilation

Lambdas are NOT anonymous inner classes. They're compiled using `invokedynamic` bytecode instruction:

```
Lambda Expression
    ↓
Compiler generates:
    1. A private static method in the enclosing class (desugared lambda body)
    2. An invokedynamic call site (bootstrap method: LambdaMetafactory)
    ↓
At runtime (first call):
    LambdaMetafactory creates an implementation class
    ↓
Subsequent calls:
    Reuses the generated class (cached)
```

**Key differences from anonymous inner classes:**
- No separate `.class` file generated at compile time
- No object allocation for non-capturing lambdas (can be cached as singleton)
- Uses `invokedynamic` — JVM optimizes the call site
- Doesn't capture `this` unless needed (anonymous class always does)

### Functional Interface Contract

```java
@FunctionalInterface  // optional but recommended
public interface MyFunction {
    int apply(int x);           // THE abstract method (SAM)
    
    // These DON'T count:
    default int doubled(int x) { return apply(x) * 2; }  // default method
    static void helper() { }                               // static method
    String toString();                                     // from Object
    boolean equals(Object o);                              // from Object
}
```

---

## Diagram

```
Core Functional Interfaces (java.util.function):

┌───────────────────────────────────────────────────────────┐
│ Interface         │ Method          │ Signature            │
├───────────────────┼─────────────────┼──────────────────────┤
│ Predicate<T>      │ test(T)         │ T → boolean          │
│ Function<T,R>     │ apply(T)        │ T → R                │
│ Consumer<T>       │ accept(T)       │ T → void             │
│ Supplier<T>       │ get()           │ () → T               │
│ UnaryOperator<T>  │ apply(T)        │ T → T                │
│ BinaryOperator<T> │ apply(T,T)      │ (T, T) → T           │
│ BiFunction<T,U,R> │ apply(T,U)      │ (T, U) → R           │
│ BiPredicate<T,U>  │ test(T,U)       │ (T, U) → boolean     │
│ BiConsumer<T,U>   │ accept(T,U)     │ (T, U) → void        │
└───────────────────────────────────────────────────────────┘

Lambda Syntax Variants:
┌──────────────────────────────────────────────────────────┐
│ () -> expression           │ no params, single expression │
│ x -> expression            │ one param (parens optional)  │
│ (x) -> expression          │ one param with parens        │
│ (x, y) -> expression       │ multiple params              │
│ (x, y) -> { statements; }  │ block body (needs return)    │
│ (int x, int y) -> x + y   │ explicit types               │
└──────────────────────────────────────────────────────────┘

Method Reference Types:
┌────────────────────────────────────────────────────────────┐
│ Type              │ Syntax              │ Lambda Equivalent  │
├───────────────────┼─────────────────────┼────────────────────┤
│ Static method     │ Class::staticMethod │ x -> Class.method(x)│
│ Instance method   │ obj::instanceMethod │ x -> obj.method(x)  │
│ Arbitrary object  │ Class::instMethod   │ (obj,x)->obj.m(x)   │
│ Constructor       │ Class::new          │ x -> new Class(x)   │
└────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Lambda Expressions

```java
// No parameters
Runnable r = () -> System.out.println("Hello");

// Single parameter (parentheses optional)
Consumer<String> printer = s -> System.out.println(s);

// Multiple parameters
BinaryOperator<Integer> add = (a, b) -> a + b;

// Block body (must use return)
Function<String, Integer> parser = s -> {
    if (s == null) return 0;
    return Integer.parseInt(s.trim());
};

// With explicit types
Comparator<String> comp = (String a, String b) -> a.compareTo(b);
```

### Core Functional Interfaces

```java
// Predicate<T> — T → boolean (filtering/testing)
Predicate<String> isEmpty = String::isEmpty;
Predicate<Integer> isEven = n -> n % 2 == 0;
Predicate<String> startsWithA = s -> s.startsWith("A");

// Composing predicates
Predicate<Integer> isEvenAndPositive = isEven.and(n -> n > 0);
Predicate<String> notEmpty = isEmpty.negate();

// Function<T, R> — T → R (transformation)
Function<String, Integer> toLength = String::length;
Function<String, String> toUpper = String::toUpperCase;

// Composing functions
Function<String, Integer> upperThenLength = toUpper.andThen(toLength);
// "hello" → "HELLO" → 5

// Consumer<T> — T → void (side effects)
Consumer<String> print = System.out::println;
Consumer<List<String>> clear = List::clear;

// Chaining consumers
Consumer<String> printAndLog = print.andThen(s -> logger.info(s));

// Supplier<T> — () → T (factory/lazy evaluation)
Supplier<LocalDateTime> now = LocalDateTime::now;
Supplier<List<String>> listFactory = ArrayList::new;
Supplier<UUID> idGenerator = UUID::randomUUID;

// UnaryOperator<T> — T → T (same type transformation)
UnaryOperator<String> trim = String::trim;
UnaryOperator<Integer> doubleIt = n -> n * 2;

// BinaryOperator<T> — (T, T) → T (combining)
BinaryOperator<Integer> sum = Integer::sum;
BinaryOperator<String> concat = String::concat;
```

### Method References

```java
// Static method reference
Function<String, Integer> parseInt = Integer::parseInt;
// equivalent to: s -> Integer.parseInt(s)

// Instance method of particular object
String prefix = "Hello, ";
Function<String, String> greeter = prefix::concat;
// equivalent to: s -> prefix.concat(s)

// Instance method of arbitrary object (first param becomes target)
Function<String, String> toUpper = String::toUpperCase;
// equivalent to: s -> s.toUpperCase()

BiFunction<String, String, Boolean> startsWith = String::startsWith;
// equivalent to: (s, prefix) -> s.startsWith(prefix)

// Constructor reference
Supplier<ArrayList<String>> newList = ArrayList::new;
Function<Integer, int[]> newArray = int[]::new;
Function<String, Employee> fromName = Employee::new;  // Employee(String name)
```

### Custom Functional Interface

```java
@FunctionalInterface
public interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);
}

// Usage
TriFunction<String, Integer, Boolean, String> formatter = 
    (name, age, active) -> String.format("%s (age:%d, active:%b)", name, age, active);

String result = formatter.apply("Alice", 30, true);
// "Alice (age:30, active:true)"
```

### Effectively Final and Variable Capture

```java
// Lambda can capture "effectively final" local variables
String greeting = "Hello";  // effectively final (never reassigned)

Function<String, String> greeter = name -> greeting + " " + name;

// This would FAIL:
// greeting = "Hi";  // makes it non-effectively-final
// Function<String, String> greeter = name -> greeting + " " + name;  // COMPILE ERROR

// Workaround for mutable state — use array or AtomicReference
int[] counter = {0};  // array reference is effectively final
Runnable increment = () -> counter[0]++;  // modifying content, not reference
```

---

## Dry Run

### Lambda Desugaring

```java
public class LambdaDemo {
    public void process(List<String> items) {
        items.forEach(s -> System.out.println(s.toUpperCase()));
    }
}

// What the compiler generates:
public class LambdaDemo {
    // Desugared lambda body (private static method)
    private static void lambda$process$0(String s) {
        System.out.println(s.toUpperCase());
    }
    
    public void process(List<String> items) {
        // invokedynamic bootstrap → LambdaMetafactory
        // Creates Consumer<String> that calls lambda$process$0
        items.forEach(/* invokedynamic → Consumer */);
    }
}
```

### Function Composition

```java
Function<Integer, Integer> multiply2 = x -> x * 2;
Function<Integer, Integer> add3 = x -> x + 3;

Function<Integer, Integer> multiply2ThenAdd3 = multiply2.andThen(add3);
Function<Integer, Integer> add3ThenMultiply2 = multiply2.compose(add3);

// multiply2ThenAdd3.apply(5):
// Step 1: multiply2.apply(5) → 10
// Step 2: add3.apply(10) → 13
// Result: 13

// add3ThenMultiply2.apply(5):
// Step 1: add3.apply(5) → 8    (compose applies inner first)
// Step 2: multiply2.apply(8) → 16
// Result: 16
```

---

## Complexity

| Aspect | Anonymous Inner Class | Lambda |
|--------|----------------------|--------|
| .class files | One per usage | None (invokedynamic) |
| Object creation | Every invocation | Cached for non-capturing |
| Memory footprint | Higher (captures `this`) | Lower (minimal capture) |
| Performance | Slower startup | Faster (JIT optimized) |
| Debugging | Harder (anonymous names) | Slightly better (method names) |

---

## Real Project Usage

### Strategy Pattern with Lambdas

```java
public class PricingService {
    private Function<Order, BigDecimal> pricingStrategy;
    
    public PricingService(Function<Order, BigDecimal> strategy) {
        this.pricingStrategy = strategy;
    }
    
    public BigDecimal calculatePrice(Order order) {
        return pricingStrategy.apply(order);
    }
}

// Different strategies as lambdas
Function<Order, BigDecimal> standard = order -> order.getSubtotal();
Function<Order, BigDecimal> premium = order -> order.getSubtotal().multiply(new BigDecimal("0.9"));
Function<Order, BigDecimal> vip = order -> order.getSubtotal().multiply(new BigDecimal("0.8"));

PricingService service = new PricingService(premium);
```

### Validation Chain

```java
public class Validator<T> {
    private final List<Predicate<T>> rules = new ArrayList<>();
    
    public Validator<T> addRule(Predicate<T> rule) {
        rules.add(rule);
        return this;
    }
    
    public boolean validate(T obj) {
        return rules.stream().allMatch(rule -> rule.test(obj));
    }
}

// Usage
Validator<String> passwordValidator = new Validator<String>()
    .addRule(s -> s.length() >= 8)
    .addRule(s -> s.matches(".*[A-Z].*"))
    .addRule(s -> s.matches(".*[0-9].*"))
    .addRule(s -> s.matches(".*[!@#$%].*"));

boolean valid = passwordValidator.validate("MyPass1!");  // true
```

### Event Handling / Callbacks

```java
public class EventEmitter<T> {
    private final Map<String, List<Consumer<T>>> listeners = new HashMap<>();
    
    public void on(String event, Consumer<T> handler) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(handler);
    }
    
    public void emit(String event, T data) {
        listeners.getOrDefault(event, Collections.emptyList())
                 .forEach(handler -> handler.accept(data));
    }
}

// Usage
EventEmitter<Order> emitter = new EventEmitter<>();
emitter.on("created", order -> emailService.sendConfirmation(order));
emitter.on("created", order -> inventoryService.reserve(order));
emitter.on("cancelled", order -> paymentService.refund(order));
```

---

## Interview Questions and Answers

### Q1: What is a functional interface? Can it have multiple methods?

**A:** A functional interface has exactly **one abstract method** (SAM). It CAN have:
- Multiple `default` methods
- Multiple `static` methods
- Methods from `Object` class (`toString`, `equals`, `hashCode`)

The `@FunctionalInterface` annotation is optional but recommended — it causes a compile error if the interface has more than one abstract method.

```java
@FunctionalInterface
public interface MyInterface {
    void doSomething();                    // THE abstract method
    default void helper() { }             // doesn't count
    static void utility() { }             // doesn't count
    String toString();                     // from Object, doesn't count
}
```

### Q2: What is the difference between a lambda and an anonymous inner class?

**A:**

| Feature | Lambda | Anonymous Inner Class |
|---------|--------|----------------------|
| Compiled to | invokedynamic + private method | Separate .class file |
| `this` reference | Enclosing class | Anonymous class itself |
| Can implement | Only functional interfaces | Any interface/abstract class |
| State | Effectively final captures | Can have instance variables |
| Performance | Better (cached, no allocation) | Worse (always allocates) |

**Critical difference — `this`:**
```java
public class Outer {
    Runnable lambda = () -> System.out.println(this.getClass());
    // prints: Outer
    
    Runnable anon = new Runnable() {
        public void run() { System.out.println(this.getClass()); }
    };
    // prints: Outer$1
}
```

### Q3: What is "effectively final"?

**A:** A variable is effectively final if it's never reassigned after initialization. Lambdas can only capture effectively final local variables:

```java
int x = 10;      // effectively final — never reassigned
int y = 20;
y = 30;          // y is NOT effectively final

Runnable r = () -> System.out.println(x);  // OK
// Runnable r2 = () -> System.out.println(y);  // COMPILE ERROR
```

**Why:** Lambdas capture the **value**, not the variable. If the variable could change, the lambda would have a stale copy, leading to confusing bugs.

### Q4: Explain Predicate composition methods.

**A:**

```java
Predicate<Integer> isPositive = n -> n > 0;
Predicate<Integer> isEven = n -> n % 2 == 0;

Predicate<Integer> isPositiveAndEven = isPositive.and(isEven);   // AND
Predicate<Integer> isPositiveOrEven = isPositive.or(isEven);     // OR
Predicate<Integer> isNotPositive = isPositive.negate();          // NOT

// Static methods
Predicate<String> notNull = Predicate.not(Objects::isNull);  // Java 11+
Predicate<String> isEqual = Predicate.isEqual("target");
```

### Q5: What is the difference between `Function.andThen()` and `Function.compose()`?

**A:**
- `f.andThen(g)` → applies `f` first, then `g`: `g(f(x))`
- `f.compose(g)` → applies `g` first, then `f`: `f(g(x))`

```java
Function<Integer, Integer> doubleIt = x -> x * 2;
Function<Integer, Integer> addOne = x -> x + 1;

doubleIt.andThen(addOne).apply(3);  // double(3)=6, then add(6)=7 → 7
doubleIt.compose(addOne).apply(3);  // add(3)=4, then double(4)=8 → 8
```

---

## Follow-up Questions and Answers

### Q: Can a lambda throw checked exceptions?

**A:** Standard functional interfaces don't declare checked exceptions. You need either:

```java
// 1. Wrap in unchecked
Function<String, Integer> parse = s -> {
    try {
        return Integer.parseInt(s);
    } catch (NumberFormatException e) {
        throw new RuntimeException(e);
    }
};

// 2. Create custom functional interface
@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}

// 3. Utility wrapper
public static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R> f) {
    return t -> {
        try { return f.apply(t); }
        catch (Exception e) { throw new RuntimeException(e); }
    };
}

// Usage:
list.stream().map(unchecked(s -> new URL(s))).collect(toList());
```

### Q: What are primitive specializations?

**A:** To avoid autoboxing overhead, Java provides primitive versions:

```java
IntPredicate isEven = n -> n % 2 == 0;        // int → boolean
IntFunction<String> intToStr = Integer::toString;  // int → R
ToIntFunction<String> strLen = String::length;     // T → int
IntUnaryOperator doubleIt = n -> n * 2;            // int → int
IntBinaryOperator sum = Integer::sum;              // (int, int) → int
IntConsumer printer = System.out::println;         // int → void
IntSupplier random = () -> ThreadLocalRandom.current().nextInt(); // () → int

// Also: Long*, Double* variants
LongSupplier timestamp = System::currentTimeMillis;
DoubleUnaryOperator sqrt = Math::sqrt;
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Modifying captured variables | Compile error | Use `AtomicReference` or array |
| Lambda with checked exception | Won't compile with standard interfaces | Use wrapper or custom interface |
| Overusing lambdas for complex logic | Unreadable code | Extract to named method |
| `this` confusion in lambda | References enclosing class, not lambda | Understand scoping rules |
| Ignoring return type inference issues | Ambiguous lambda target | Add explicit type or cast |
| Capturing mutable objects | Shared state issues in parallel streams | Use immutable or thread-local |

---

## Best Practices

1. **Keep lambdas short** — one-liners preferred; extract complex logic to methods
2. **Use method references** when possible — `String::length` over `s -> s.length()`
3. **Use `@FunctionalInterface`** annotation — documents intent, catches mistakes
4. **Prefer standard functional interfaces** — don't create custom ones unnecessarily
5. **Use primitive specializations** — `IntPredicate` over `Predicate<Integer>` to avoid boxing
6. **Don't capture mutable state** — leads to thread-safety issues
7. **Use composition methods** — `and()`, `or()`, `andThen()`, `compose()`

---

## Production Considerations

- **Debugging:** Lambda stack traces show `lambda$method$0` — use meaningful variable names for reference
- **Serialization:** Lambdas are not serializable by default. Use `(Serializable & Predicate<T>)` cast if needed.
- **Memory:** Non-capturing lambdas are singletons (no allocation). Capturing lambdas allocate per call.
- **GC:** Lambdas that capture `this` prevent the enclosing object from being GC'd
- **Testing:** Extract complex lambdas into named methods for unit testing

---

## Related Topics

- [9. Comparable vs Comparator](./09-comparable-vs-comparator.md) — lambda comparators
- [11. Stream API](./11-stream-api.md) — primary consumer of functional interfaces
- [12. Optional](./12-optional.md) — uses functional interfaces extensively
