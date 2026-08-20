# 4. String Handling

---

## Theory

Strings in Java are **immutable objects** — once created, their content cannot be changed. This is one of the most important concepts in Java.

### String Immutability

```java
String s = "hello";
s.concat(" world"); // creates NEW string, original unchanged
System.out.println(s); // "hello" — not modified!

s = s.concat(" world"); // reassigns reference to new object
System.out.println(s); // "hello world"
```

**Why String is Immutable:**
1. **Security**: Strings used in class loading, network connections, file paths — mutation would be dangerous
2. **Thread Safety**: Immutable objects are inherently thread-safe
3. **Caching (String Pool)**: Same literal can be shared safely because it can't change
4. **hashCode caching**: String caches its hashCode; possible only because content never changes
5. **Class loading**: Class names are strings — mutable strings could corrupt the class loading mechanism

**Why String is Final:**
- Prevents subclasses from overriding methods and breaking immutability
- If `String` could be extended, a subclass could make it mutable

### String Pool (Intern Pool)

```java
String a = "hello";          // goes to String Pool
String b = "hello";          // reuses same pool reference
String c = new String("hello"); // creates new object on HEAP (bypasses pool)

System.out.println(a == b);     // true (same reference in pool)
System.out.println(a == c);     // false (different objects)
System.out.println(a.equals(c)); // true (same content)

String d = c.intern();       // explicitly adds to pool / returns pool reference
System.out.println(a == d);  // true
```

### String Pool Memory Layout
```
Stack:
  a → ─────────┐
  b → ─────────┤──→ String Pool: "hello" (one object)
  d → ─────────┘
  c → ──────────────→ Heap: new String("hello") (separate object)
```

### == vs .equals()

| `==` | `.equals()` |
|------|-------------|
| Compares references (memory addresses) | Compares content |
| Works for primitives (value comparison) | Works for objects |
| Cannot be overridden | Can be overridden |
| `null == null` → true | `null.equals(x)` → NPE |

### StringBuilder vs StringBuffer

| Feature | StringBuilder | StringBuffer |
|---------|--------------|--------------|
| Thread Safety | Not thread-safe | Thread-safe (synchronized) |
| Performance | Faster | Slower (synchronization overhead) |
| Use case | Single-threaded | Multi-threaded (rare) |
| Introduced | Java 5 | Java 1.0 |

Both are **mutable** — modify content in place without creating new objects.

### String Concatenation Performance

```java
// BAD — creates many intermediate objects
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i; // new StringBuilder + toString each iteration!
}

// GOOD — single StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);
}
String result = sb.toString();
```

### intern() Method
- Returns the canonical (pool) representation of a string
- If pool already contains equal string → returns pool reference
- Otherwise, adds this string to pool and returns it
- Use case: Memory optimization when many duplicate strings exist

---

## Internal Working

### String Class Internals (Java 9+)

```java
public final class String implements Serializable, Comparable<String>, CharSequence {
    
    @Stable
    private final byte[] value;  // Java 9+: compact strings (byte[] not char[])
    
    private final byte coder;    // LATIN1 (0) or UTF16 (1)
    
    private int hash;            // cached hashCode (default 0 = not computed)
    
    // hashCode implementation:
    public int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            // s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
            for (byte v : value) {
                h = 31 * h + (v & 0xff);
            }
            hash = h; // cache it
        }
        return h;
    }
}
```

**Java 9 Compact Strings:**
- Before Java 9: `char[]` (2 bytes per character always)
- Java 9+: `byte[]` with encoding flag
  - LATIN1 strings (ASCII/Latin): 1 byte per char → 50% less memory
  - UTF-16 strings (Chinese, emoji, etc.): 2 bytes per char

### String Pool Location
- Java 7+: String Pool is in the **Heap** (was in PermGen before Java 7)
- Garbage collected when no references exist
- Initial size configurable: `-XX:StringTableSize=60013`

### How + Concatenation Works (Compilation)

```java
// Source code:
String s = "Hello" + " " + "World";

// Compiler optimization (constant folding):
String s = "Hello World"; // computed at compile time!

// Non-constant concatenation (Java 9+):
String name = getName();
String greeting = "Hello " + name + "!";

// Compiled to (Java 9+ uses invokedynamic):
// Uses StringConcatFactory — more efficient than StringBuilder
// makeConcatWithConstants strategy
```

### StringBuilder Internal Working
```
Initial state: capacity = 16
┌─────────────────────────────────────────────┐
│ char[16]:  [  |  |  |  |  |  |...         ] │
│ count: 0                                     │
└─────────────────────────────────────────────┘

After append("Hello"):
┌─────────────────────────────────────────────┐
│ char[16]:  [H|e|l|l|o|  |  |...           ] │
│ count: 5                                     │
└─────────────────────────────────────────────┘

When capacity exceeded: new capacity = (old capacity * 2) + 2
```

---

## Diagram

```
String Memory Model:
                    Stack              Heap
                  ┌───────┐       ┌─────────────────┐
                  │ s1 ───┼──────→│ String "hello"  │ ← String Pool
                  │ s2 ───┼──────→│                 │
                  ├───────┤       └─────────────────┘
                  │ s3 ───┼──────→┌─────────────────┐
                  │       │       │ String "hello"  │ ← Heap (new)
                  └───────┘       └─────────────────┘

String Operations (Immutability):
"hello" ──concat(" world")──→ "hello world" (NEW object)
   │                                 │
   └── original unchanged            └── returned, may or may not be assigned
```

```
StringBuilder vs String:
String concatenation (n times):
  "a" → "ab" → "abc" → "abcd"   = n objects created, O(n²)
  
StringBuilder:
  [a][b][c][d]────────────────── = 1 mutable buffer, O(n)
```

---

## Code

```java
public class StringHandlingDemo {

    public static void main(String[] args) {
        // --- String Pool ---
        String s1 = "Java";
        String s2 = "Java";
        String s3 = new String("Java");
        
        System.out.println(s1 == s2);       // true (pool)
        System.out.println(s1 == s3);       // false (heap vs pool)
        System.out.println(s1.equals(s3));  // true (content)
        System.out.println(s1 == s3.intern()); // true (intern returns pool ref)
        
        // --- How many objects created? ---
        String x = new String("Hello"); 
        // Answer: 2 objects
        // 1. "Hello" literal → String Pool (if not already there)
        // 2. new String("Hello") → Heap object
        
        // --- Immutability proof ---
        String original = "immutable";
        String modified = original.replace("im", "");
        System.out.println(original); // "immutable" — unchanged
        System.out.println(modified); // "mutable" — new object
        
        // --- StringBuilder ---
        StringBuilder sb = new StringBuilder(64); // initial capacity
        sb.append("Hello");
        sb.append(" ");
        sb.append("World");
        sb.insert(5, ",");   // "Hello, World"
        sb.reverse();        // "dlroW ,olleH"
        sb.delete(0, 5);     // " ,olleH"
        String result = sb.toString();
        
        // --- String methods ---
        String str = "  Hello, World!  ";
        str.trim();           // "Hello, World!" (removes leading/trailing whitespace)
        str.strip();          // "Hello, World!" (Java 11+ Unicode-aware trim)
        str.toLowerCase();    // "  hello, world!  "
        str.substring(2, 7); // "Hello"
        str.contains("World"); // true
        str.startsWith("  H"); // true
        str.split(",");       // ["  Hello", " World!  "]
        str.replace(",", ";"); // "  Hello; World!  "
        str.charAt(2);        // 'H'
        str.indexOf("World"); // 9
        str.isEmpty();        // false
        str.isBlank();        // false (Java 11+)
        
        // --- Java 11+ String methods ---
        "  ".isBlank();       // true
        "ha".repeat(3);       // "hahaha"
        "line1\nline2".lines(); // Stream<String>
        " hi ".strip();      // "hi"
        " hi ".stripLeading(); // "hi "
        " hi ".stripTrailing(); // " hi"
        
        // --- Performance comparison ---
        long start = System.nanoTime();
        performanceTest();
        long end = System.nanoTime();
        System.out.println("Time: " + (end - start) / 1_000_000 + "ms");
    }
    
    private static void performanceTest() {
        // StringBuilder is ~100x faster for large concatenations
        int iterations = 100_000;
        
        // StringBuilder: ~5ms
        StringBuilder sb = new StringBuilder(iterations * 5);
        for (int i = 0; i < iterations; i++) {
            sb.append("hello");
        }
        String result1 = sb.toString();
        
        // String + in loop: ~500ms+
        // DON'T do this in production
        // String result2 = "";
        // for (int i = 0; i < iterations; i++) {
        //     result2 += "hello"; // creates new object each time!
        // }
    }
}
```

---

## Dry Run

### String Pool Example
```
Code:
  String a = "hello";
  String b = "hello";
  String c = new String("hello");
  String d = c.intern();

Step 1: "hello" literal → Check String Pool
  Pool doesn't have "hello" → Create in Pool
  a → points to Pool's "hello"

Step 2: "hello" literal → Check String Pool
  Pool already has "hello" → Reuse
  b → points to SAME Pool's "hello" as a

Step 3: new String("hello") → Always creates new Heap object
  c → points to NEW object on Heap (content copied from pool's "hello")

Step 4: c.intern() → Check if Pool has "hello"
  Yes! → Return Pool reference
  d → points to Pool's "hello" (same as a and b)

Results:
  a == b → true  (same pool reference)
  a == c → false (pool vs heap)
  a == d → true  (both point to pool)
  a.equals(c) → true (same content)
```

### StringBuilder Growth
```
new StringBuilder() → internal char[16], count=0

append("Hello World Java") → 15 chars, fits in 16
  char[16]: [H,e,l,l,o, ,W,o,r,l,d, ,J,a,v,a]
  count = 15 (not yet 16, it's 15)

Wait: "Hello World Java" = 15 chars. Let's say we append one more "!"
append("!") → need 16 chars total, capacity is 16 → fits!
  count = 16

append(" Programming") → need 16+12=28, capacity=16 → EXPAND!
  new capacity = 16 * 2 + 2 = 34
  System.arraycopy(old, 0, new, 0, 16)
  Then append remaining chars
  count = 28
```

---

## Complexity

| Operation | String | StringBuilder |
|-----------|--------|---------------|
| charAt(i) | O(1) | O(1) |
| concat / append | O(n+m) | O(m) amortized |
| substring | O(1)* Java 7u6+ creates new | O(n) |
| indexOf | O(n*m) | O(n*m) |
| replace | O(n) | O(n) |
| equals | O(n) | N/A |
| hashCode | O(n) first call, O(1) cached | N/A |
| + in loop (n iterations) | O(n²) | O(n) |

*Note: Since Java 7u6, substring() creates a new String with its own char[]. Before that, it shared the original array.

---

## Real Project Usage

```java
// 1. Building SQL queries safely
public class QueryBuilder {
    private final StringBuilder query = new StringBuilder(256);
    private final List<Object> params = new ArrayList<>();
    
    public QueryBuilder select(String... columns) {
        query.append("SELECT ").append(String.join(", ", columns));
        return this;
    }
    
    public QueryBuilder from(String table) {
        query.append(" FROM ").append(table);
        return this;
    }
    
    public QueryBuilder where(String condition, Object value) {
        query.append(" WHERE ").append(condition).append(" = ?");
        params.add(value);
        return this;
    }
    
    public String build() { return query.toString(); }
}

// 2. Efficient log message building
public class Logger {
    public void debug(String format, Object... args) {
        if (isDebugEnabled()) {
            // Only format if actually logging — avoid unnecessary String creation
            log(String.format(format, args));
        }
    }
}

// 3. String interning for memory optimization
public class EventProcessor {
    // Thousands of events with same type strings
    public Event processEvent(String rawType) {
        // intern() reuses pool reference — saves memory for repeated values
        String type = rawType.intern(); 
        return new Event(type);
    }
}

// 4. Text block (Java 15+) for readability
public class EmailTemplate {
    private static final String TEMPLATE = """
            Dear %s,
            
            Your order #%s has been shipped.
            Estimated delivery: %s
            
            Thank you for shopping with us!
            """;
    
    public String generateEmail(String name, String orderId, String date) {
        return TEMPLATE.formatted(name, orderId, date);
    }
}
```

---

## Interview Questions and Answers

**Q1: How many objects are created by `String s = new String("hello")`?**
> Up to 2 objects: (1) The "hello" literal goes to the String Pool if not already present, (2) `new String("hello")` creates a new object on the heap that copies the content. If "hello" already exists in the pool, only 1 new object is created (the heap one).

**Q2: Why is String immutable in Java?**
> Security (used in class loading, connections), thread safety (no synchronization needed), String Pool efficiency (safe sharing), hashCode caching (computed once), and preventing bugs from unintended modification of shared references.

**Q3: What is the difference between `==` and `.equals()` for Strings?**
> `==` compares references — true only if both variables point to the exact same object in memory. `.equals()` compares content — true if the character sequences are identical. Always use `.equals()` for String comparison. Use `==` only when you intentionally want reference comparison.

**Q4: When would you use StringBuilder vs StringBuffer?**
> Use StringBuilder in 99% of cases — it's faster because it's not synchronized. Use StringBuffer only if the string is being built by multiple threads concurrently (which is extremely rare). In practice, StringBuffer is almost never needed.

**Q5: What does intern() do?**
> It checks if the String Pool contains an equal string. If yes, returns the pool reference. If no, adds the string to the pool and returns that reference. Useful for memory savings when many strings have the same content (e.g., parsing large CSV files with repeated values).

**Q6: What is Compact Strings (Java 9)?**
> Java 9 changed String's internal storage from `char[]` (always 2 bytes/char) to `byte[]` with an encoding flag. ASCII-only strings use LATIN1 encoding (1 byte/char), saving ~50% memory. Strings with non-Latin characters use UTF-16 (2 bytes/char). This is transparent to the developer.

---

## Follow-up Questions and Answers

**Q: Is String Pool garbage collected?**
> Yes, since Java 7 (when it moved to the heap). If no references point to a pooled string, it becomes eligible for GC. Before Java 7, it was in PermGen and was not garbage collected, causing potential memory leaks with excessive `intern()` calls.

**Q: What's wrong with `"" + someValue` for conversion?**
> It works but creates unnecessary intermediate objects. Prefer `String.valueOf(someValue)` for primitives or `Objects.toString(obj, "default")` for objects. The `+` approach is fine for readability in non-performance-critical code though.

**Q: How does the JVM optimize String concatenation?**
> Since Java 9, the compiler uses `invokedynamic` with `StringConcatFactory` instead of creating explicit StringBuilder objects. This gives the JVM freedom to choose the optimal strategy at runtime (e.g., pre-sizing the buffer based on argument types).

**Q: Can String cause memory leaks?**
> Before Java 7u6, `substring()` shared the underlying char[] of the original string. A small substring could prevent a large string from being GC'd. Since Java 7u6, substring creates an independent copy. With `intern()`, excessive unique strings can bloat the String Pool.

---

## Common Mistakes

1. **Comparing strings with ==**
   ```java
   String input = scanner.nextLine();
   if (input == "yes") { } // WRONG — always false for runtime strings
   if (input.equals("yes")) { } // CORRECT
   if ("yes".equals(input)) { } // BEST — null-safe
   ```

2. **String concatenation in loops**
   ```java
   String sql = "";
   for (String col : columns) {
       sql += col + ", "; // O(n²) — creates n objects
   }
   // Fix: Use StringBuilder or String.join(", ", columns)
   ```

3. **Not accounting for Unicode**
   ```java
   String emoji = "👋";
   emoji.length(); // 2! (surrogate pair)
   emoji.codePointCount(0, emoji.length()); // 1 (correct character count)
   ```

4. **NullPointerException with equals**
   ```java
   String s = null;
   s.equals("hello"); // NPE!
   "hello".equals(s); // false — safe!
   Objects.equals(s, "hello"); // false — null-safe
   ```

5. **Ignoring locale in case conversion**
   ```java
   "TITLE".toLowerCase(); // uses default locale — inconsistent across systems
   "TITLE".toLowerCase(Locale.ENGLISH); // explicit — predictable
   ```

---

## Best Practices

1. **Use `.equals()` for comparison**, never `==` for string content.
2. **Put literal on left**: `"value".equals(variable)` — avoids NPE.
3. **Use StringBuilder** for building strings in loops.
4. **Use `String.join()`** for joining collections: `String.join(", ", list)`.
5. **Use text blocks** (Java 15+) for multi-line strings.
6. **Pre-size StringBuilder** if you know approximate length: `new StringBuilder(1024)`.
7. **Use `String.format()` or `.formatted()`** for complex formatting.
8. **Avoid excessive `intern()`** — pool size is limited; only use for known-repetitive strings.
9. **Use `isEmpty()` or `isBlank()`** instead of comparing with `""` or checking `length() == 0`.

---

## Production Considerations

- **Memory**: In applications with millions of strings (parsers, caches), consider `intern()` for repeated values. Monitor pool size with `-XX:+PrintStringTableStatistics`.

- **String Pool sizing**: `-XX:StringTableSize=1000003` (prime number, default 60013). Larger table = fewer collisions = faster intern().

- **Compact Strings**: Enabled by default since Java 9. Disable with `-XX:-CompactStrings` if your app is all Unicode (saves the encoding check overhead).

- **Logging**: Use parameterized logging (`log.debug("User {} logged in", userId)`) instead of concatenation — avoids String creation when log level is disabled.

- **Serialization**: Strings in serialized objects consume significant space. Consider compression or more efficient serialization formats (Protocol Buffers, MessagePack).

- **String deduplication**: `-XX:+UseStringDeduplication` (G1 GC only) — GC automatically deduplicates String values in the heap. Free memory savings for apps with many duplicate strings.

---

## Related Topics

- → [1. Java Fundamentals](./01-java-fundamentals.md)
- → [3. Object Class](./03-object-class.md) (equals/hashCode for Strings)
- → [7. Collection Internals](./07-collection-internals.md) (String as HashMap key)
- → [20. JVM Internals](./20-jvm-internals.md) (String Pool location)
- → [33. Java Performance](./33-java-performance.md)
