# 8. Generics

---

## Theory

Generics enable **type-safe** code by allowing classes, interfaces, and methods to operate on parameterized types. They were introduced in Java 5 to eliminate `ClassCastException` at runtime by catching type errors at compile time.

**Key concept:** Generics are a **compile-time** feature. Due to **type erasure**, generic type information is removed during compilation and replaced with `Object` (or the bound type).

### Why Generics Exist

```java
// Before generics (Java 1.4) — unsafe
List list = new ArrayList();
list.add("hello");
list.add(123);  // no compile error!
String s = (String) list.get(1);  // ClassCastException at runtime!

// With generics — type-safe
List<String> list = new ArrayList<>();
list.add("hello");
list.add(123);  // COMPILE ERROR — caught early!
String s = list.get(0);  // no cast needed
```

---

## Internal Working

### Type Erasure

The compiler removes all generic type information and inserts necessary casts:

```java
// What you write:
public class Box<T> {
    private T item;
    public T getItem() { return item; }
    public void setItem(T item) { this.item = item; }
}

// What the compiler generates (after type erasure):
public class Box {
    private Object item;
    public Object getItem() { return item; }
    public void setItem(Object item) { this.item = item; }
}

// When you use it:
Box<String> box = new Box<>();
box.setItem("hello");
String s = box.getItem();

// Compiler generates:
Box box = new Box();
box.setItem("hello");
String s = (String) box.getItem();  // cast inserted by compiler
```

### Bridge Methods

When a generic class is extended with a concrete type, the compiler generates **bridge methods** to maintain polymorphism:

```java
public class StringBox extends Box<String> {
    @Override
    public String getItem() { return super.getItem(); }
}

// Compiler generates bridge method:
// public Object getItem() { return getItem(); }  // bridge
```

---

## Diagram

```
Type Parameter Hierarchy:

┌─────────────────────────────────────────────────────┐
│  Generic Declaration                                 │
├─────────────────────────────────────────────────────┤
│  class Box<T>           → T is type parameter       │
│  Box<String> box        → String is type argument   │
│                                                     │
│  Compile Time:                                      │
│  Box<String> ──────→ type check enforced            │
│                                                     │
│  Runtime (after erasure):                           │
│  Box (raw) ──────→ no generic info available        │
└─────────────────────────────────────────────────────┘

Wildcard Hierarchy:
┌──────────────────────────────────────────┐
│      <?>                                  │
│     (unbounded — any type)               │
│          │                                │
│    ┌─────┴─────┐                         │
│    │           │                          │
│ <? extends T>  <? super T>               │
│ (upper bound)  (lower bound)             │
│ READ only      WRITE allowed             │
│ (Producer)     (Consumer)                │
└──────────────────────────────────────────┘

PECS Principle:
┌─────────────────────────────────────────────────────┐
│  Producer Extends, Consumer Super                    │
│                                                      │
│  List<? extends Number> producer;                    │
│  → Can READ Number from it                          │
│  → Cannot WRITE to it (except null)                 │
│                                                      │
│  List<? super Integer> consumer;                     │
│  → Can WRITE Integer to it                          │
│  → READ returns Object only                         │
└─────────────────────────────────────────────────────┘
```

---

## Code Examples

### Generic Classes

```java
// Simple generic class
public class Pair<K, V> {
    private K key;
    private V value;
    
    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }
    
    public K getKey() { return key; }
    public V getValue() { return value; }
    
    @Override
    public String toString() {
        return key + "=" + value;
    }
}

// Usage
Pair<String, Integer> pair = new Pair<>("age", 25);
String key = pair.getKey();      // no cast needed
Integer value = pair.getValue(); // type-safe
```

### Generic Methods

```java
public class Utils {
    
    // Generic method — type parameter before return type
    public static <T> T getFirst(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }
    
    // Multiple type parameters
    public static <K, V> Map<K, V> mapOf(K key, V value) {
        Map<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
    
    // Generic method with bounded type
    public static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}

// Usage — type inference
String first = Utils.getFirst(List.of("a", "b", "c"));
Integer maxVal = Utils.max(10, 20);
```

### Bounded Type Parameters

```java
// Upper bound — T must be Number or subclass
public class Calculator<T extends Number> {
    private T value;
    
    public Calculator(T value) { this.value = value; }
    
    public double doubleValue() {
        return value.doubleValue() * 2;  // can call Number methods
    }
}

// Multiple bounds — T must implement both
public <T extends Comparable<T> & Serializable> T findMax(List<T> list) {
    return list.stream().max(Comparable::compareTo).orElse(null);
}

// Note: class bound must come first
// <T extends MyClass & Interface1 & Interface2>  ✓
// <T extends Interface1 & MyClass>               ✗ compile error
```

### Wildcards

```java
// Unbounded wildcard — accepts any type
public void printList(List<?> list) {
    for (Object obj : list) {
        System.out.println(obj);
    }
    // list.add("test");  // COMPILE ERROR — can't add (except null)
}

// Upper bounded wildcard (Producer Extends)
public double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number n : numbers) {
        total += n.doubleValue();  // can READ as Number
    }
    // numbers.add(1);  // COMPILE ERROR — can't write
    return total;
}

// Lower bounded wildcard (Consumer Super)
public void addIntegers(List<? super Integer> list) {
    list.add(1);    // can WRITE Integer
    list.add(2);
    list.add(3);
    // Integer x = list.get(0);  // COMPILE ERROR — read returns Object
    Object obj = list.get(0);    // only Object guaranteed
}
```

### PECS in Practice

```java
// Collections.copy() — perfect PECS example
public static <T> void copy(List<? super T> dest,    // Consumer — Super
                             List<? extends T> src) {  // Producer — Extends
    for (int i = 0; i < src.size(); i++) {
        dest.set(i, src.get(i));  // read from src, write to dest
    }
}

// Real-world example
List<Integer> integers = List.of(1, 2, 3);
List<Number> numbers = new ArrayList<>(Arrays.asList(0.0, 0.0, 0.0));
Collections.copy(numbers, integers);  // works! Integer extends Number
```

### Generic Interface

```java
@FunctionalInterface
public interface Transformer<T, R> {
    R transform(T input);
}

// Implementation with concrete types
public class StringToInteger implements Transformer<String, Integer> {
    @Override
    public Integer transform(String input) {
        return Integer.parseInt(input);
    }
}

// Implementation keeping generic
public class IdentityTransformer<T> implements Transformer<T, T> {
    @Override
    public T transform(T input) {
        return input;
    }
}
```

---

## Dry Run

### Type Erasure and Bridge Methods

```java
public interface Comparable<T> {
    int compareTo(T o);
}

public class Age implements Comparable<Age> {
    private int value;
    
    @Override
    public int compareTo(Age other) {  // specific type
        return Integer.compare(this.value, other.value);
    }
}

// After type erasure, Comparable becomes:
// interface Comparable { int compareTo(Object o); }

// Bridge method generated in Age:
// public int compareTo(Object o) {       // bridge (synthetic)
//     return compareTo((Age) o);          // delegates to typed method
// }

// This is why you can do:
Comparable c = new Age();
c.compareTo(new Age());  // calls bridge → delegates to typed method
```

### Wildcard Capture

```java
public static void swap(List<?> list, int i, int j) {
    // list.set(i, list.get(j));  // COMPILE ERROR!
    // Why? list.get(j) returns Object, but list expects "capture of ?"
    
    // Solution — helper with captured type:
    swapHelper(list, i, j);
}

private static <T> void swapHelper(List<T> list, int i, int j) {
    T temp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, temp);
}
```

---

## Complexity

| Operation | Impact |
|-----------|--------|
| Type checking | Compile-time only — zero runtime cost |
| Type erasure | No generic info at runtime — can't do `new T()` |
| Autoboxing with generics | Possible performance hit (primitives boxed) |
| Wildcard resolution | Compile-time — no runtime overhead |

**Key limitation:** Cannot use primitives with generics
```java
List<int> list;      // COMPILE ERROR
List<Integer> list;  // must use wrapper — autoboxing overhead
```

---

## Real Project Usage

### Repository Pattern

```java
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    T save(T entity);
    void deleteById(ID id);
}

public class UserRepository implements Repository<User, Long> {
    @Override
    public User findById(Long id) { /* ... */ }
    
    @Override
    public List<User> findAll() { /* ... */ }
    
    @Override
    public User save(User entity) { /* ... */ }
    
    @Override
    public void deleteById(Long id) { /* ... */ }
}
```

### Generic Response Wrapper

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.timestamp = LocalDateTime.now();
        return response;
    }
    
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = message;
        response.timestamp = LocalDateTime.now();
        return response;
    }
}

// Usage
ApiResponse<User> response = ApiResponse.ok(user);
ApiResponse<List<Order>> orders = ApiResponse.ok(orderList);
```

### Event System

```java
public interface Event {}

public interface EventHandler<T extends Event> {
    void handle(T event);
}

public class EventBus {
    private Map<Class<?>, List<EventHandler<?>>> handlers = new HashMap<>();
    
    public <T extends Event> void register(Class<T> eventType, EventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends Event> void publish(T event) {
        List<EventHandler<?>> list = handlers.get(event.getClass());
        if (list != null) {
            for (EventHandler<?> handler : list) {
                ((EventHandler<T>) handler).handle(event);
            }
        }
    }
}
```

---

## Interview Questions and Answers

### Q1: What is type erasure? Why does Java use it?

**A:** Type erasure is the process where the compiler removes all generic type information during compilation, replacing type parameters with their bounds (or `Object` if unbounded). Java uses it for **backward compatibility** — generic code must work with pre-generics bytecode. The JVM has no knowledge of generics at runtime.

**Consequence:** You cannot do `new T()`, `instanceof T`, or `T.class` at runtime.

### Q2: What is the difference between `<? extends T>` and `<? super T>`?

**A:**
- `<? extends T>` — **upper bound**. Accepts T or any subtype. Use for **reading** (Producer).
- `<? super T>` — **lower bound**. Accepts T or any supertype. Use for **writing** (Consumer).

This follows the **PECS principle**: Producer Extends, Consumer Super.

```java
// Can read Number from producer
List<? extends Number> producer = List.of(1, 2.0, 3L);
Number n = producer.get(0);  // safe

// Can write Integer to consumer
List<? super Integer> consumer = new ArrayList<Number>();
consumer.add(42);  // safe
```

### Q3: Why can't you do `new T()` in Java generics?

**A:** Due to type erasure, `T` becomes `Object` at runtime. The JVM doesn't know what constructor to call. Workarounds:

```java
// 1. Pass Class<T> token
public <T> T create(Class<T> clazz) throws Exception {
    return clazz.getDeclaredConstructor().newInstance();
}

// 2. Pass Supplier<T>
public <T> T create(Supplier<T> factory) {
    return factory.get();
}
```

### Q4: Can you have a generic array? Why or why not?

**A:** You cannot create a generic array directly:

```java
T[] array = new T[10];           // COMPILE ERROR
List<String>[] arr = new List<String>[10];  // COMPILE ERROR
```

**Why:** Arrays are **covariant** and **reified** (know their type at runtime). Generics are **invariant** and **erased**. Mixing them would break type safety:

```java
// If this were allowed:
Object[] arr = new List<String>[10];
arr[0] = new List<Integer>();  // ArrayStoreException should fire, but can't!
// Because at runtime both are just List due to erasure
```

**Workaround:** Use `@SuppressWarnings("unchecked")` or `Array.newInstance()`:
```java
@SuppressWarnings("unchecked")
T[] array = (T[]) new Object[10];
```

### Q5: What is the difference between `List<Object>` and `List<?>`?

**A:**
- `List<Object>` — you can add any Object to it, but it only accepts `List<Object>`, not `List<String>`.
- `List<?>` — accepts any `List<T>`, but you cannot add to it (except null).

```java
List<Object> objects = new ArrayList<>();
objects.add("hello");  // OK
objects.add(123);      // OK

List<String> strings = new ArrayList<>();
// List<Object> obj = strings;  // COMPILE ERROR — not covariant!

List<?> unknown = strings;  // OK — wildcard accepts any
// unknown.add("test");     // COMPILE ERROR — can't write
Object o = unknown.get(0); // OK — can read as Object
```

---

## Follow-up Questions and Answers

### Q: What are recursive type bounds?

**A:** A type parameter that references itself in its bound:

```java
// T must be comparable to itself
public class Sort<T extends Comparable<T>> {
    public T findMax(List<T> list) {
        return list.stream().max(Comparable::compareTo).orElse(null);
    }
}

// Enum uses this pattern:
// public abstract class Enum<E extends Enum<E>>
```

### Q: What is a raw type and why is it dangerous?

**A:** A raw type is a generic class used without type arguments:

```java
List rawList = new ArrayList();  // raw type
rawList.add("hello");
rawList.add(123);  // no compile error!
Integer x = (Integer) rawList.get(0);  // ClassCastException!
```

Raw types exist only for backward compatibility. They bypass all generic safety checks.

### Q: Can you overload methods that differ only in generic type?

**A:** No — due to type erasure, they have the same signature after erasure:

```java
// COMPILE ERROR — both erase to process(List)
public void process(List<String> strings) { }
public void process(List<Integer> integers) { }
```

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| `new T()` | Type erased at runtime | Pass `Class<T>` or `Supplier<T>` |
| `instanceof T` | No type info at runtime | Pass `Class<T>` and use `clazz.isInstance()` |
| `new T[10]` | Arrays are reified, generics aren't | Use `List<T>` or `Array.newInstance()` |
| `List<Object>` thinking it accepts `List<String>` | Generics are invariant | Use `List<? extends Object>` |
| Adding to `List<? extends T>` | Upper bound = read-only | Use `List<? super T>` for writing |
| Ignoring raw type warnings | Defeats purpose of generics | Always specify type parameters |
| `(T) object` without check | Unchecked cast — may fail later | Use `Class<T>.cast()` or `instanceof` |

---

## Best Practices

1. **Always specify type parameters** — never use raw types
2. **Follow PECS** — Producer Extends, Consumer Super
3. **Prefer `List<T>` over `T[]`** — lists work naturally with generics
4. **Use bounded types** to restrict and enable operations on type parameters
5. **Minimize `@SuppressWarnings("unchecked")`** — isolate to smallest scope
6. **Use diamond operator `<>`** — `new ArrayList<>()` instead of `new ArrayList<String>()`
7. **Prefer generic methods over raw casts** — let the compiler help you
8. **Document type parameter meanings** — `<K, V>` for key-value, `<T>` for type, `<E>` for element

---

## Production Considerations

- **Performance:** Generics have zero runtime cost (erased). But autoboxing with wrapper types has overhead.
- **Serialization:** Generic type info lost at runtime — use type tokens or `TypeReference` for frameworks like Jackson.
- **Reflection:** To get generic type info at runtime, use `getGenericSuperclass()` or `ParameterizedType`.
- **Frameworks:** Spring, Jackson, Hibernate all work around erasure using various techniques (subclassing, type tokens, annotations).

---

## Related Topics

- [6. Collections Framework](./06-collections-framework.md) — heavily uses generics
- [10. Java 8 Functional Interfaces](./10-java8-features.md) — `Function<T,R>`, `Predicate<T>`, etc.
- [25. Reflection](./25-reflection.md) — accessing generic type info at runtime
