# 1. Java Fundamentals — Must Master

---

## Theory

Java is a **statically-typed, object-oriented, platform-independent** programming language. Its core philosophy is **"Write Once, Run Anywhere" (WORA)**.

### JDK vs JRE vs JVM

| Component | Full Name | Purpose |
|-----------|-----------|---------|
| **JDK** | Java Development Kit | Complete development environment (compiler + JRE + tools) |
| **JRE** | Java Runtime Environment | Runtime libraries + JVM (for running Java apps) |
| **JVM** | Java Virtual Machine | Executes bytecode on any platform |

**Relationship:** JDK ⊃ JRE ⊃ JVM

### Java Compilation and Execution Flow

```
Source Code (.java)
       ↓ javac (compiler)
Bytecode (.class)
       ↓ JVM
Machine Code (platform-specific)
```

- **javac** compiles `.java` to `.class` (bytecode)
- **JVM** interprets/JIT-compiles bytecode to native machine code
- **Platform Independence**: Bytecode is the same on all platforms; only the JVM differs per OS

### Bytecode
- Intermediate representation between source code and machine code
- Human-readable via `javap -c ClassName`
- Stack-based instruction set

### Java Features
- Object-Oriented
- Platform Independent
- Robust (strong memory management, exception handling)
- Secure (no pointers, bytecode verification)
- Multithreaded
- Distributed (RMI, networking APIs)
- Garbage Collected

### Primitive vs Reference Types

| Primitive Types | Size | Default | Reference Types |
|----------------|------|---------|-----------------|
| `byte` | 1 byte | 0 | Classes |
| `short` | 2 bytes | 0 | Interfaces |
| `int` | 4 bytes | 0 | Arrays |
| `long` | 8 bytes | 0L | Enums |
| `float` | 4 bytes | 0.0f | Strings |
| `double` | 8 bytes | 0.0d | |
| `char` | 2 bytes | '\u0000' | |
| `boolean` | ~1 bit | false | |

**Key Difference:**
- Primitives store **values** directly on the stack
- References store **memory addresses** (pointers to objects on the heap)

### Variables
- **Local variables**: Declared in methods, must be initialized before use
- **Instance variables**: Declared in class, get default values
- **Static variables**: Shared across all instances, belong to class

### Operators
- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Relational: `==`, `!=`, `>`, `<`, `>=`, `<=`
- Logical: `&&`, `||`, `!`
- Bitwise: `&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`
- Assignment: `=`, `+=`, `-=`, etc.
- Ternary: `condition ? value1 : value2`
- `instanceof`: Type checking

### Type Casting
```java
// Widening (implicit) - smaller to larger
int i = 100;
long l = i;        // int → long automatically

// Narrowing (explicit) - larger to smaller
double d = 9.78;
int i = (int) d;   // double → int, loses decimal (i = 9)
```

### Control Statements

```java
// if/else
if (condition) { } else if (condition) { } else { }

// switch (traditional)
switch (value) {
    case 1: break;
    case 2: break;
    default: break;
}

// switch (Java 14+ expression)
String result = switch (day) {
    case MONDAY, FRIDAY -> "Working";
    case SATURDAY, SUNDAY -> "Weekend";
    default -> "Midweek";
};

// for loop
for (int i = 0; i < n; i++) { }

// enhanced for
for (String s : list) { }

// while
while (condition) { }

// do-while (executes at least once)
do { } while (condition);
```

### Arrays
```java
// Declaration and initialization
int[] arr = new int[5];           // default values (0)
int[] arr = {1, 2, 3, 4, 5};     // literal
int[][] matrix = new int[3][4];   // 2D array

// Key points
// - Fixed size after creation
// - Zero-indexed
// - Stored on heap
// - Length is a field, not method: arr.length
```

### Varargs
```java
public void printAll(String... values) {
    // values is treated as String[] internally
    for (String v : values) {
        System.out.println(v);
    }
}

// Rules:
// - Only ONE varargs per method
// - Must be the LAST parameter
// - Internally converted to an array
```

---

## Internal Working

### How Java Compilation Works Internally

```
1. Lexical Analysis → Tokens
2. Syntax Analysis → AST (Abstract Syntax Tree)
3. Semantic Analysis → Type checking, symbol resolution
4. Bytecode Generation → .class file
```

### .class File Structure
```
ClassFile {
    magic: 0xCAFEBABE
    minor_version
    major_version
    constant_pool
    access_flags
    this_class
    super_class
    interfaces
    fields
    methods
    attributes
}
```

### JVM Execution of Bytecode
```
1. Class Loading (Bootstrap → Extension → Application ClassLoader)
2. Bytecode Verification (type safety, stack overflow checks)
3. Interpretation (line by line)
4. JIT Compilation (hot methods compiled to native code)
5. Execution
```

### Memory Layout for Primitives vs References
```
Stack Frame:
┌──────────────────────┐
│ int x = 10           │ → value 10 directly on stack
│ String s = "hello"   │ → reference (address) on stack
└──────────────────────┘
                              ↓ (s points to)
Heap:
┌──────────────────────┐
│ String object "hello"│
└──────────────────────┘
```

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│                        JDK                          │
│  ┌───────────────────────────────────────────────┐  │
│  │                    JRE                        │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │                 JVM                     │  │  │
│  │  │  ┌──────────┐ ┌──────────┐ ┌────────┐  │  │  │
│  │  │  │Class     │ │Runtime   │ │Execution│  │  │  │
│  │  │  │Loader    │ │Data Areas│ │Engine   │  │  │  │
│  │  │  └──────────┘ └──────────┘ └────────┘  │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────┐  │  │
│  │  │        Runtime Libraries (rt.jar)       │  │  │
│  │  └─────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────┐  │
│  │  Development Tools (javac, javadoc, jar...)   │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

```
Java Execution Flow:
┌──────────┐    javac    ┌──────────┐    JVM     ┌──────────┐
│ Hello.java│ ────────→  │Hello.class│ ────────→  │ Machine  │
│ (source)  │            │(bytecode) │            │  Code    │
└──────────┘             └──────────┘            └──────────┘
```

---

## Code

```java
public class JavaFundamentalsDemo {

    // Instance variable (default value = 0)
    private int instanceVar;
    
    // Static variable (shared across instances)
    private static int count = 0;

    public static void main(String[] args) {
        // --- Primitives ---
        byte b = 127;               // -128 to 127
        short s = 32000;            // -32768 to 32767
        int i = 2_147_483_647;     // underscores for readability
        long l = 9_223_372_036_854_775_807L;
        float f = 3.14f;
        double d = 3.14159265358979;
        char c = 'A';              // Unicode character
        boolean flag = true;

        // --- Type Casting ---
        // Widening (implicit)
        double widened = i;  // int to double
        
        // Narrowing (explicit)
        int narrowed = (int) d;  // loses .14159...
        System.out.println("Narrowed: " + narrowed); // 3

        // --- Overflow behavior ---
        int maxInt = Integer.MAX_VALUE;
        System.out.println("Max + 1 = " + (maxInt + 1)); // wraps to MIN_VALUE

        // --- Arrays ---
        int[] numbers = {10, 20, 30, 40, 50};
        
        // Array copy
        int[] copy = new int[numbers.length];
        System.arraycopy(numbers, 0, copy, 0, numbers.length);

        // --- Varargs ---
        printAll("Hello", "World", "Java");
        printAll(); // empty is valid

        // --- Control Flow ---
        // Enhanced switch (Java 14+)
        int dayNum = 3;
        String dayType = switch (dayNum) {
            case 1, 7 -> "Weekend";
            case 2, 3, 4, 5, 6 -> "Weekday";
            default -> throw new IllegalArgumentException("Invalid day: " + dayNum);
        };
        System.out.println(dayType); // Weekday
    }

    // Varargs method
    public static void printAll(String... values) {
        System.out.println("Count: " + values.length);
        for (String v : values) {
            System.out.print(v + " ");
        }
        System.out.println();
    }
}
```

---

## Dry Run

### Example: Type Casting and Overflow
```
Code: int x = (int) 3.99;
Step 1: 3.99 is a double literal
Step 2: Cast to int → truncates decimal → x = 3

Code: int maxInt = Integer.MAX_VALUE; // 2147483647
      int result = maxInt + 1;
Step 1: 2147483647 in binary = 01111111 11111111 11111111 11111111
Step 2: Add 1             =     10000000 00000000 00000000 00000000
Step 3: Sign bit is 1 → negative number
Step 4: result = -2147483648 (Integer.MIN_VALUE)
```

### Example: Varargs
```
Call: printAll("A", "B", "C")
Step 1: Compiler converts to printAll(new String[]{"A", "B", "C"})
Step 2: values.length = 3
Step 3: Loop prints: A B C
```

---

## Complexity

| Operation | Array | ArrayList |
|-----------|-------|-----------|
| Access by index | O(1) | O(1) |
| Search (unsorted) | O(n) | O(n) |
| Insert at end | O(1)* | O(1) amortized |
| Insert at position | O(n) | O(n) |
| Delete | O(n) | O(n) |

*Array insert at end is O(1) only if space exists; arrays are fixed-size.

---

## Real Project Usage

```java
// Configuration class using primitives and constants
public class AppConfig {
    
    // Static constants for application-wide settings
    public static final int MAX_RETRY_COUNT = 3;
    public static final long TIMEOUT_MS = 5000L;
    public static final double RATE_LIMIT_FACTOR = 0.75;
    
    // Instance configuration
    private final int port;
    private final String host;
    private final boolean sslEnabled;
    
    public AppConfig(int port, String host, boolean sslEnabled) {
        // Input validation with primitives
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.port = port;
        this.host = host;
        this.sslEnabled = sslEnabled;
    }
}

// Varargs in logging utility
public class Logger {
    public static void info(String message, Object... params) {
        String formatted = String.format(message, params);
        System.out.println("[INFO] " + formatted);
    }
}
// Usage: Logger.info("User %s logged in from %s", username, ipAddress);
```

---

## Interview Questions and Answers

**Q1: What is the difference between JDK, JRE, and JVM?**
> JVM is the virtual machine that runs bytecode. JRE includes JVM + standard libraries needed to run Java programs. JDK includes JRE + development tools (compiler, debugger, etc.). For development you need JDK; for running apps, JRE suffices.

**Q2: Why is Java platform independent?**
> Java source code compiles to bytecode (.class files), not to platform-specific machine code. The JVM, which is platform-specific, interprets this bytecode on any OS. So the bytecode is portable; only the JVM implementation differs per platform.

**Q3: What is the difference between `==` and `.equals()` for primitives vs references?**
> For primitives, `==` compares values directly. For reference types, `==` compares memory addresses (whether two references point to the same object). `.equals()` compares logical equality (content), but must be properly overridden.

**Q4: What happens when an int overflows in Java?**
> Java doesn't throw an exception. It wraps around using two's complement. `Integer.MAX_VALUE + 1` becomes `Integer.MIN_VALUE`. This is silent and can cause bugs — use `Math.addExact()` for checked arithmetic.

**Q5: Can a varargs method be called with no arguments?**
> Yes. When called with no arguments, the varargs parameter becomes an empty array (length 0). This is valid and often used in practice.

**Q6: What is type promotion in expressions?**
> In arithmetic expressions, smaller types are automatically promoted: `byte/short/char → int → long → float → double`. For example, `byte + byte` results in `int`, which is why you need explicit casting: `byte result = (byte)(b1 + b2);`

---

## Follow-up Questions and Answers

**Q: If Java is platform independent, why do we need different JDKs for different OS?**
> The platform independence is for the bytecode, not the JVM itself. The JVM must be compiled natively for each OS to translate bytecode into that platform's machine instructions. The developer's compiled `.class` files remain identical across platforms.

**Q: What's the difference between narrowing cast and truncation?**
> Narrowing cast (`(int) 3.99`) truncates toward zero (result: 3). This is not rounding. For floating-point to integer casts, the fractional part is simply discarded. For integer narrowing (`(byte) 300`), excess bits are discarded.

**Q: Why does `char` occupy 2 bytes in Java?**
> Java uses Unicode (UTF-16) for characters, not ASCII. This allows Java to represent characters from virtually all world scripts. A `char` can hold values from 0 to 65,535 (`\u0000` to `\uFFFF`).

**Q: What happens with varargs and autoboxing?**
> Varargs and autoboxing can create ambiguity. If you have `method(int... args)` and `method(Integer... args)`, calling `method(1, 2)` causes a compilation error due to ambiguity. The compiler cannot decide which overload to use.

---

## Common Mistakes

1. **Uninitialized local variables**
   ```java
   int x;
   System.out.println(x); // COMPILE ERROR - local vars must be initialized
   ```

2. **Integer overflow without detection**
   ```java
   int total = Integer.MAX_VALUE + 1; // Silent wrap to MIN_VALUE
   // Fix: Use Math.addExact() or long
   ```

3. **Comparing references with ==**
   ```java
   Integer a = 128;
   Integer b = 128;
   System.out.println(a == b); // false! (outside Integer cache -128 to 127)
   ```

4. **Loss of precision in narrowing**
   ```java
   long bigNum = 9_999_999_999L;
   int small = (int) bigNum; // 1410065407 — silent data loss!
   ```

5. **Varargs with null**
   ```java
   printAll(null); // NullPointerException when iterating
   ```

6. **Array index out of bounds**
   ```java
   int[] arr = new int[5];
   arr[5] = 10; // ArrayIndexOutOfBoundsException (valid: 0-4)
   ```

---

## Best Practices

1. **Use appropriate primitive types** — don't default to `int` for everything. Use `long` for IDs, `double` for money calculations with caution.

2. **Prefer `Math.addExact()`, `Math.multiplyExact()`** for arithmetic that could overflow.

3. **Use underscores in numeric literals** for readability: `1_000_000` instead of `1000000`.

4. **Avoid magic numbers** — use named constants (`static final`).

5. **Initialize variables at declaration** when possible.

6. **Use enhanced for-loop** when you don't need the index.

7. **Prefer `Arrays.copyOf()`** over manual array copying.

8. **Use wrapper types (`Integer`, `Long`) only when needed** (collections, nullability). Prefer primitives for performance.

---

## Production Considerations

- **Memory footprint**: A `boolean` field in an object consumes at least 1 byte (often padded to 4+ bytes due to alignment). Array of boolean uses 1 byte per element. Use `BitSet` for large boolean arrays.

- **Autoboxing overhead**: In tight loops, autoboxing/unboxing creates garbage. Use primitive arrays and streams (`IntStream`, `LongStream`) instead.

- **Array vs Collection**: Use arrays for fixed-size, performance-critical data (image processing, scientific computing). Use collections for dynamic data with rich APIs.

- **JVM flags for performance**:
  - `-XX:+UseCompressedOops` — compress object pointers in 64-bit JVM
  - `-Xms` / `-Xmx` — set heap size to avoid resizing

- **Profiling**: Use JFR (Java Flight Recorder) to identify primitive boxing hotspots in production.

---

## Related Topics

- → [2. OOP Concepts](./02-oop-concepts.md)
- → [4. String Handling](./04-string-handling.md)
- → [6. Collections Framework](./06-collections-framework.md)
- → [20. JVM Internals](./20-jvm-internals.md)
- → [22. Garbage Collection](./22-garbage-collection.md)
- → [32. Modern Java](./32-modern-java.md)
