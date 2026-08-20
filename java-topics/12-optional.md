# 12. Optional

---

## Theory

`Optional<T>` (Java 8+) is a container that may or may not hold a non-null value. It's designed to represent the **absence of a value** as a first-class concept, replacing `null` returns and reducing `NullPointerException`.

**Key purpose:** Force callers to explicitly handle the "no value" case instead of forgetting null checks.

### When to Use Optional

| Use | Don't Use |
|-----|-----------|
| Method return types | Method parameters |
| Stream terminal operations | Fields/instance variables |
| Expressing "might not exist" | Collections (use empty collection) |
| Chaining transformations | Performance-critical code |

---

## Internal Working

```java
public final class Optional<T> {
    private static final Optional<?> EMPTY = new Optional<>(null);
    private final T value;  // the actual value (or null)
    
    private Optional(T value) { this.value = value; }
    
    public static <T> Optional<T> of(T value) {
        return new Optional<>(Objects.requireNonNull(value));  // throws NPE if null
    }
    
    public static <T> Optional<T> ofNullable(T value) {
        return value == null ? (Optional<T>) EMPTY : new Optional<>(value);
    }
    
    public static <T> Optional<T> empty() {
        return (Optional<T>) EMPTY;
    }
    
    public boolean isPresent() { return value != null; }
    public boolean isEmpty() { return value == null; }  // Java 11
    public T get() {
        if (value == null) throw new NoSuchElementException();
        return value;
    }
}
```

---

## Diagram

```
Optional API Flow:

Creating:
┌─────────────────────────────────────────────────────────┐
│ Optional.of(value)         → must be non-null (NPE)     │
│ Optional.ofNullable(value) → null becomes empty          │
│ Optional.empty()           → always empty                │
└─────────────────────────────────────────────────────────┘

Transforming:
┌─────────────────────────────────────────────────────────┐
│ map(f)      → applies f if present, wraps in Optional   │
│ flatMap(f)  → applies f if present (f returns Optional) │
│ filter(p)   → keeps value if predicate true, else empty │
└─────────────────────────────────────────────────────────┘

Extracting:
┌─────────────────────────────────────────────────────────┐
│ get()             → value or NoSuchElementException      │
│ orElse(default)   → value or default (always evaluated)  │
│ orElseGet(supplier) → value or supplier.get() (lazy)    │
│ orElseThrow()     → value or NoSuchElementException     │
│ orElseThrow(supplier) → value or custom exception       │
│ ifPresent(consumer) → runs consumer if present          │
│ ifPresentOrElse(c, r) → consumer or runnable (Java 9)  │
└─────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Creating Optional

```java
// of — use when you KNOW value is non-null
Optional<String> opt1 = Optional.of("hello");
// Optional.of(null);  // throws NullPointerException!

// ofNullable — use when value might be null
String name = getName();  // might return null
Optional<String> opt2 = Optional.ofNullable(name);

// empty — explicit absence
Optional<String> opt3 = Optional.empty();
```

### Transforming with map, flatMap, filter

```java
// map — transforms the value inside Optional
Optional<String> name = Optional.of("  Alice  ");
Optional<String> trimmed = name.map(String::trim);          // Optional["Alice"]
Optional<Integer> length = name.map(String::trim).map(String::length); // Optional[5]

// flatMap — when transformation returns Optional (avoids Optional<Optional<T>>)
public Optional<String> findCity(String userId) {
    return findUser(userId)              // Optional<User>
        .flatMap(User::getAddress)       // Optional<Address> (not Optional<Optional<Address>>)
        .flatMap(Address::getCity);      // Optional<String>
}

// filter — keeps value only if predicate passes
Optional<Integer> age = Optional.of(25);
Optional<Integer> adult = age.filter(a -> a >= 18);  // Optional[25]
Optional<Integer> minor = age.filter(a -> a < 18);   // Optional.empty
```

### Extracting Values

```java
Optional<String> name = findName(id);

// orElse — always evaluates the default (even when value present!)
String result1 = name.orElse("Unknown");

// orElseGet — lazy evaluation (supplier called ONLY if empty)
String result2 = name.orElseGet(() -> fetchDefaultName());

// orElseThrow — throw custom exception
String result3 = name.orElseThrow(() -> new UserNotFoundException(id));

// orElseThrow() — no-arg version (Java 10+) throws NoSuchElementException
String result4 = name.orElseThrow();

// ifPresent — execute action only if present
name.ifPresent(System.out::println);
name.ifPresent(n -> emailService.send(n));

// ifPresentOrElse (Java 9)
name.ifPresentOrElse(
    n -> System.out.println("Found: " + n),
    () -> System.out.println("Not found")
);
```

### orElse vs orElseGet — Critical Difference

```java
// orElse — ALWAYS evaluates the argument
public String getDefault() {
    System.out.println("Computing default...");  // expensive operation
    return "default";
}

Optional<String> opt = Optional.of("present");
String val1 = opt.orElse(getDefault());           // "Computing default..." PRINTED!
String val2 = opt.orElseGet(() -> getDefault());  // NOT printed (lazy)

// When to use which:
// orElse      → cheap default values: orElse("N/A"), orElse(0)
// orElseGet   → expensive computation: orElseGet(() -> dbQuery())
```

### Optional with Streams (Java 9+)

```java
// Optional.stream() — converts to 0-or-1 element stream
Optional<String> opt = Optional.of("hello");
Stream<String> stream = opt.stream();  // Stream with one element

// Useful for flattening Stream<Optional<T>> → Stream<T>
List<Optional<String>> optionals = List.of(
    Optional.of("a"), Optional.empty(), Optional.of("c")
);

List<String> values = optionals.stream()
    .flatMap(Optional::stream)  // removes empties
    .collect(Collectors.toList());  // [a, c]

// or() — provides alternative Optional (Java 9)
Optional<String> result = findInCache(key)
    .or(() -> findInDatabase(key))
    .or(() -> findInRemote(key));
```

---

## Dry Run

### Chaining Scenario

```java
public Optional<String> getManagerEmail(Long employeeId) {
    return findEmployee(employeeId)         // Optional<Employee>
        .filter(Employee::isActive)         // Optional<Employee> or empty
        .flatMap(Employee::getManager)      // Optional<Employee> (manager)
        .map(Employee::getEmail);           // Optional<String>
}

// Scenario 1: Employee exists, is active, has manager with email
// findEmployee(1) → Optional[Employee{active=true, manager=Manager{email="mgr@x.com"}}]
// .filter(isActive) → Optional[Employee] (passes)
// .flatMap(getManager) → Optional[Manager]
// .map(getEmail) → Optional["mgr@x.com"]

// Scenario 2: Employee exists but inactive
// findEmployee(2) → Optional[Employee{active=false}]
// .filter(isActive) → Optional.empty (filtered out)
// .flatMap(...) → Optional.empty (skipped)
// .map(...) → Optional.empty (skipped)
// Result: Optional.empty

// Scenario 3: Employee doesn't exist
// findEmployee(999) → Optional.empty
// Everything after → Optional.empty
```

---

## Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| Creation (of, ofNullable, empty) | O(1) | Simple wrapper |
| map, flatMap, filter | O(1) | Just applies function |
| orElse | O(1) | But argument always evaluated |
| orElseGet | O(1) if present | Supplier called only if empty |
| isPresent / isEmpty | O(1) | Null check |
| stream() | O(1) | Creates 0-or-1 element stream |

**Memory:** Optional is an object on the heap (not free). For hot paths, consider primitive return + sentinel value.

---

## Real Project Usage

### Repository Layer

```java
public interface UserRepository {
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    // NOT Optional<List<User>> — use empty list instead
    List<User> findByDepartment(String dept);
}

// Service layer
public class UserService {
    public UserDTO getUser(Long id) {
        return userRepository.findById(id)
            .map(this::toDTO)
            .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
    
    public UserDTO getUserOrDefault(Long id) {
        return userRepository.findById(id)
            .map(this::toDTO)
            .orElseGet(this::createGuestDTO);
    }
}
```

### Configuration Lookup

```java
public class ConfigService {
    public String getProperty(String key) {
        return Optional.ofNullable(System.getProperty(key))
            .or(() -> Optional.ofNullable(System.getenv(key)))
            .or(() -> Optional.ofNullable(fileConfig.get(key)))
            .orElseThrow(() -> new ConfigMissingException(key));
    }
}
```

### Safe Chaining (replacing nested null checks)

```java
// Before Optional (null-check hell):
String city = null;
if (user != null) {
    Address address = user.getAddress();
    if (address != null) {
        city = address.getCity();
    }
}
if (city == null) city = "Unknown";

// With Optional:
String city = Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

---

## Interview Questions and Answers

### Q1: What is the difference between `orElse()` and `orElseGet()`?

**A:**
- `orElse(T value)` — **always** evaluates the argument, even if Optional has a value. Use for cheap constants.
- `orElseGet(Supplier<T>)` — **lazy**. Supplier is called ONLY when Optional is empty. Use for expensive operations.

```java
Optional<String> opt = Optional.of("present");
opt.orElse(expensiveCall());       // expensiveCall() IS executed (wasted!)
opt.orElseGet(() -> expensiveCall()); // expensiveCall() NOT executed (saved!)
```

### Q2: Why shouldn't Optional be used for method parameters?

**A:**
1. Makes API awkward: `findUser(Optional.of(name), Optional.empty())`
2. Caller can still pass `null` instead of `Optional.empty()` → NPE
3. Method overloading is cleaner: `findUser(name)` and `findUser()`
4. Forces unnecessary wrapping at call site

### Q3: Why shouldn't Optional be used for fields?

**A:**
1. `Optional` is not `Serializable` — breaks serialization frameworks
2. Extra object per instance → memory overhead
3. Not intended for that use — use `@Nullable` annotation or null with documented contract

### Q4: How to convert Optional to Stream?

**A:**
```java
// Java 9+
Optional<String> opt = Optional.of("hello");
Stream<String> stream = opt.stream();  // Stream of 1 element

// Flattening List<Optional<T>> → List<T>
List<Optional<String>> opts = getOptionals();
List<String> values = opts.stream()
    .flatMap(Optional::stream)
    .collect(Collectors.toList());
```

---

## Follow-up Questions and Answers

### Q: What happens if you call `get()` on an empty Optional?

**A:** Throws `NoSuchElementException`. **Never call `get()` without checking `isPresent()` first.** Better: use `orElse`, `orElseGet`, or `orElseThrow`.

### Q: Can Optional contain null?

**A:** `Optional.of(null)` throws `NullPointerException`. `Optional.ofNullable(null)` returns `Optional.empty()`. An Optional is either empty or contains a non-null value — never contains null.

### Q: Is Optional a monad?

**A:** Almost. It satisfies the monad laws (left identity, right identity, associativity) with `flatMap`, but it's not a full monad in the functional programming sense because it doesn't handle all edge cases (e.g., `Optional.of(null)` throws). It's more accurately a "monad-like container."

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| `opt.get()` without check | `NoSuchElementException` | Use `orElse`/`orElseThrow` |
| `if (opt.isPresent()) opt.get()` | Verbose, defeats purpose | Use `map`/`ifPresent`/`orElse` |
| `Optional.of(nullableValue)` | NPE at creation | Use `Optional.ofNullable()` |
| `Optional<List<T>>` | Use empty list instead | Return `Collections.emptyList()` |
| `Optional` as method parameter | Awkward API | Use overloading or `@Nullable` |
| `Optional` as field | Not serializable, overhead | Use null + `@Nullable` |
| `orElse(expensiveOperation())` | Always evaluated | Use `orElseGet(() -> ...)` |
| `opt.map(x -> null)` | Returns `Optional.empty` silently | Be aware map treats null return as empty |

---

## Best Practices

1. **Use for return types** — signal "might not exist" to callers
2. **Never return null Optional** — return `Optional.empty()` instead
3. **Prefer functional style** — `map`, `flatMap`, `filter` over `isPresent` + `get`
4. **Use `orElseGet` for expensive defaults** — lazy evaluation
5. **Use `orElseThrow` for mandatory values** — clear error message
6. **Don't wrap collections** — use empty collection instead of `Optional<List>`
7. **Keep chains readable** — break long chains into meaningful variables

---

## Production Considerations

- **Performance:** Each Optional is a heap allocation. In hot loops, consider null + careful checks.
- **Serialization:** Optional is not Serializable. Jackson handles it with `jackson-datatype-jdk8` module.
- **Spring Data:** Repositories return `Optional<T>` for single-entity queries.
- **Debugging:** Empty Optional doesn't tell you WHERE it became empty — add logging in flatMap chains.

---

## Related Topics

- [11. Stream API](./11-stream-api.md) — terminal operations return Optional
- [10. Java 8 Features](./10-java8-features.md) — functional interfaces used with Optional
- [5. Exception Handling](./05-exception-handling.md) — orElseThrow pattern
