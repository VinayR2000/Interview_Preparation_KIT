# 20. JVM Internals

---

## Theory

The **Java Virtual Machine (JVM)** is the runtime engine that executes Java bytecode. It provides platform independence — "Write Once, Run Anywhere."

### Compilation Flow

```
Java Source (.java)
    ↓ javac (compiler)
Bytecode (.class)
    ↓ ClassLoader
JVM Memory (loaded classes)
    ↓ Interpreter + JIT Compiler
Native Machine Code (executed by CPU)
```

### JVM Components

1. **Class Loader Subsystem** — loads, links, and initializes classes
2. **Runtime Data Areas** — memory regions used during execution
3. **Execution Engine** — interprets/compiles and executes bytecode
4. **Native Method Interface (JNI)** — bridge to native code
5. **Garbage Collector** — automatic memory management

---

## Internal Working

### JVM Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            JVM                                        │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────┐         │
│  │           Class Loader Subsystem                         │         │
│  │  Loading → Linking → Initialization                      │         │
│  └─────────────────────────────────────────────────────────┘         │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────┐         │
│  │             Runtime Data Areas                           │         │
│  │                                                          │         │
│  │  ┌───────────────────────────────────────────────────┐  │         │
│  │  │   Method Area (Metaspace) — shared                 │  │         │
│  │  │   Class metadata, static vars, constant pool       │  │         │
│  │  └───────────────────────────────────────────────────┘  │         │
│  │                                                          │         │
│  │  ┌───────────────────────────────────────────────────┐  │         │
│  │  │   Heap — shared                                    │  │         │
│  │  │   All objects and arrays                           │  │         │
│  │  │   ┌──────────────┐  ┌──────────────────────┐     │  │         │
│  │  │   │Young Gen      │  │   Old Gen             │     │  │         │
│  │  │   │Eden|S0|S1     │  │   (tenured)           │     │  │         │
│  │  │   └──────────────┘  └──────────────────────┘     │  │         │
│  │  └───────────────────────────────────────────────────┘  │         │
│  │                                                          │         │
│  │  ┌─────────────────┐  ┌─────────────────┐              │         │
│  │  │ Stack (per-thread)│  │ PC Register     │              │         │
│  │  │ Frames: locals,  │  │ (per-thread)    │              │         │
│  │  │ operand stack,   │  │ current instr   │              │         │
│  │  │ return address   │  └─────────────────┘              │         │
│  │  └─────────────────┘                                    │         │
│  │                                                          │         │
│  │  ┌─────────────────────────────────────────────────────┐│         │
│  │  │ Native Method Stack (per-thread)                     ││         │
│  │  └─────────────────────────────────────────────────────┘│         │
│  └─────────────────────────────────────────────────────────┘         │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────┐         │
│  │             Execution Engine                             │         │
│  │  Interpreter → JIT Compiler (C1, C2) → Native Code     │         │
│  └─────────────────────────────────────────────────────────┘         │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────┐         │
│  │  Garbage Collector (GC)                                  │         │
│  └─────────────────────────────────────────────────────────┘         │
└──────────────────────────────────────────────────────────────────────┘
```

### JIT Compilation Tiers

```
Method execution:
1. Interpreted (first few invocations)
2. C1 compiled (after ~1,500 invocations) — fast compile, basic optimizations
3. C2 compiled (after ~10,000 invocations) — slow compile, aggressive optimizations

Optimizations by JIT:
- Inlining (small methods merged into caller)
- Dead code elimination
- Loop unrolling
- Escape analysis (object may be stack-allocated)
- Lock elimination (lock on non-shared object removed)
- Null check elimination
```

---

## Diagram

```
Memory Layout:

┌──────────────────────────────────────────────────────────────┐
│                         HEAP                                   │
│  ┌────────────────────────────────┐  ┌───────────────────┐   │
│  │      Young Generation           │  │  Old Generation    │   │
│  │  ┌──────┐ ┌─────┐ ┌─────┐     │  │                    │   │
│  │  │ Eden │ │ S0  │ │ S1  │     │  │   Long-lived       │   │
│  │  │      │ │     │ │     │     │  │   objects           │   │
│  │  │ new  │ │surv │ │surv │     │  │                    │   │
│  │  │ obj  │ │     │ │     │     │  │   (promoted from   │   │
│  │  └──────┘ └─────┘ └─────┘     │  │    Young Gen)      │   │
│  └────────────────────────────────┘  └───────────────────┘   │
│                                                                │
│  Default ratio: Young:Old = 1:2                               │
│  Eden:S0:S1 = 8:1:1                                          │
└──────────────────────────────────────────────────────────────┘

┌──────────────────┐  ┌──────────────────┐
│     Stack         │  │    Metaspace      │
│  (per thread)     │  │  (native memory)  │
│                   │  │                    │
│  ┌─────────────┐ │  │  Class metadata    │
│  │ Frame 3     │ │  │  Method bytecode   │
│  ├─────────────┤ │  │  Constant pool     │
│  │ Frame 2     │ │  │  Static variables  │
│  ├─────────────┤ │  │                    │
│  │ Frame 1     │ │  │  No fixed size     │
│  │ (main)      │ │  │  (limited by OS)   │
│  └─────────────┘ │  └──────────────────┘
└──────────────────┘
```

---

## Code Examples

### Observing JVM Behavior

```java
// Memory info
Runtime runtime = Runtime.getRuntime();
long maxMemory = runtime.maxMemory();        // -Xmx (max heap)
long totalMemory = runtime.totalMemory();    // current heap size
long freeMemory = runtime.freeMemory();      // free within current heap
long usedMemory = totalMemory - freeMemory;

System.out.println("Max: " + maxMemory / 1024 / 1024 + " MB");
System.out.println("Used: " + usedMemory / 1024 / 1024 + " MB");

// Available processors
int cores = runtime.availableProcessors();

// Suggest GC (just a hint!)
System.gc();  // suggests GC — JVM may ignore
```

### Stack Frame Demonstration

```java
public class StackDemo {
    public static void main(String[] args) {   // Frame 1
        int x = method1(5);                    // main → method1
    }
    
    static int method1(int a) {                // Frame 2
        int b = a + 10;
        return method2(b);                     // method1 → method2
    }
    
    static int method2(int c) {                // Frame 3
        return c * 2;                          // returns, Frame 3 popped
    }                                          // Frame 2 pops, Frame 1 pops
}

// Stack at deepest point:
// | method2 | ← top (current)
// | method1 |
// | main    | ← bottom
```

### Bytecode Inspection

```java
// Compile: javac Demo.java
// View bytecode: javap -c Demo.class

public int add(int a, int b) {
    return a + b;
}

// Bytecode:
// 0: iload_1     (load int arg 1 → operand stack)
// 1: iload_2     (load int arg 2 → operand stack)
// 2: iadd        (pop two, add, push result)
// 3: ireturn     (return int from top of stack)
```

### JVM Arguments

```bash
# Memory settings
java -Xms512m -Xmx2g -XX:MetaspaceSize=128m MyApp

# GC selection
java -XX:+UseG1GC MyApp
java -XX:+UseZGC MyApp           # Java 15+
java -XX:+UseShenandoahGC MyApp  # Java 12+

# GC logging
java -Xlog:gc*:file=gc.log:time MyApp

# JIT settings
java -XX:+PrintCompilation MyApp           # show JIT compilations
java -XX:CompileThreshold=5000 MyApp       # invocations before JIT

# Debugging
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/dump.hprof MyApp
java -XX:NativeMemoryTracking=summary MyApp
```

---

## Dry Run

### Object Lifecycle in JVM Memory

```java
public class App {
    private static Config config;  // stored in Metaspace (static)
    
    public static void main(String[] args) {
        // "args" reference → Stack
        // String[] object → Heap (Young Gen / Eden)
        
        config = new Config();
        // Config object → Heap (Eden)
        // static reference "config" → Metaspace
        
        process();
    }
    
    static void process() {
        // New stack frame pushed
        List<String> items = new ArrayList<>();
        // "items" reference → Stack (local variable)
        // ArrayList object → Heap (Eden)
        
        items.add("hello");
        // "hello" → String pool (Metaspace/Heap depending on JVM version)
        // Node inside ArrayList → Heap (Eden)
        
    }   // Stack frame popped
        // "items" no longer referenced → eligible for GC
}
```

---

## Complexity

| JVM Operation | Time |
|--------------|------|
| Object allocation (Eden, TLAB) | ~10ns |
| Minor GC (Young Gen) | ~10-50ms |
| Major GC (Old Gen, G1) | ~100-500ms |
| Full GC (stop-the-world) | ~1-10s (avoid!) |
| Method interpretation | ~100ns per bytecode |
| JIT-compiled method | ~1-5ns per instruction |
| Class loading | ~1-10ms per class |

---

## Real Project Usage

### JVM Tuning for Production

```bash
# Typical production JVM settings
java \
  -Xms4g -Xmx4g \                    # fixed heap (avoid resize)
  -XX:+UseG1GC \                      # G1 for balanced latency/throughput
  -XX:MaxGCPauseMillis=200 \          # GC pause target
  -XX:+HeapDumpOnOutOfMemoryError \   # dump on OOM
  -XX:HeapDumpPath=/var/dumps/ \
  -XX:+UseStringDeduplication \       # reduce string memory (G1 only)
  -XX:MetaspaceSize=256m \
  -Xlog:gc*:file=/var/log/gc.log:time,level \
  -jar myapp.jar
```

### Detecting Memory Leaks

```java
// Common leak patterns:
// 1. Static collections growing forever
private static final List<Object> cache = new ArrayList<>();  // never cleared!

// 2. Listeners not removed
eventBus.register(listener);  // but never unregister!

// 3. ThreadLocal not removed
threadLocal.set(value);       // but never threadLocal.remove()!

// Tools: VisualVM, Eclipse MAT, jmap + jhat
// jmap -histo:live <pid>  → shows live objects by class
// jmap -dump:live,format=b,file=heap.bin <pid>  → heap dump
```

---

## Interview Questions and Answers

### Q1: Explain the JVM memory structure.

**A:** JVM memory is divided into:
1. **Heap** (shared) — all objects. Split into Young Gen (Eden + S0 + S1) and Old Gen.
2. **Stack** (per thread) — method frames with local variables, operand stack, return address.
3. **Metaspace** (native memory) — class metadata, static variables, constant pool. Replaced PermGen in Java 8.
4. **PC Register** (per thread) — address of current bytecode instruction.
5. **Native Method Stack** (per thread) — for native (JNI) method calls.

### Q2: What is the difference between stack and heap?

**A:**

| Stack | Heap |
|-------|------|
| Per-thread | Shared across threads |
| Stores local variables, references | Stores objects |
| LIFO allocation/deallocation | GC managed |
| Fixed size (-Xss) | Dynamic (-Xms/-Xmx) |
| Fast (pointer bump) | Slower (GC overhead) |
| StackOverflowError if full | OutOfMemoryError if full |

### Q3: What is JIT compilation?

**A:** Just-In-Time compilation converts frequently executed bytecode ("hot spots") into native machine code at runtime. The JVM profiles execution, identifies hot methods, and compiles them for maximum performance.

- **C1 (Client):** Fast compilation, basic optimizations. For methods called ~1,500 times.
- **C2 (Server):** Aggressive optimization, slower compilation. For methods called ~10,000+ times.
- **Tiered compilation** (default): Uses both — start with C1, promote to C2 for hottest methods.

### Q4: What is escape analysis?

**A:** JIT optimization that determines if an object's reference "escapes" the method or thread:
- **No escape:** Object can be allocated on stack (no GC needed!) or eliminated entirely
- **Thread-local:** Synchronization can be removed (lock elision)
- **Escapes:** Must be heap-allocated (normal behavior)

```java
// JIT can stack-allocate this Point — it never escapes the method
public int sumCoords() {
    Point p = new Point(1, 2);  // escape analysis: doesn't escape
    return p.x + p.y;           // may be optimized to: return 3;
}
```

### Q5: What is Metaspace? How is it different from PermGen?

**A:** Metaspace (Java 8+) replaced PermGen for storing class metadata:

| PermGen (Java 7-) | Metaspace (Java 8+) |
|-------------------|---------------------|
| Fixed size, part of heap | Native memory, auto-grows |
| `OutOfMemoryError: PermGen` | `OutOfMemoryError: Metaspace` |
| Required manual sizing | Self-tuning (but can limit with -XX:MaxMetaspaceSize) |
| String pool here | String pool moved to heap |

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| -Xms ≠ -Xmx in production | Heap resizing causes pauses | Set equal for predictability |
| Ignoring GC logs | Can't diagnose latency issues | Always enable GC logging |
| `System.gc()` in production | Triggers Full GC, massive pause | Never call manually |
| Not setting -Xss for deep recursion | StackOverflowError | Increase stack or use iteration |
| PermGen/Metaspace leak | ClassLoader leak from hot-deploy | Monitor and restart, fix leaks |

---

## Best Practices

1. **Set -Xms = -Xmx** — avoid heap resize pauses
2. **Enable GC logging** — always, even in production
3. **Enable heap dump on OOM** — `-XX:+HeapDumpOnOutOfMemoryError`
4. **Choose GC based on needs** — G1 (balanced), ZGC (low latency), Parallel (throughput)
5. **Monitor with JMX/Prometheus** — heap usage, GC frequency, GC pause times
6. **Use JFR (Java Flight Recorder)** — low-overhead production profiling
7. **Avoid premature optimization** — let JIT do its job first

---

## Production Considerations

- **Container awareness:** Use `-XX:+UseContainerSupport` (default since Java 10) for Docker
- **GC selection:** ZGC for < 1ms pauses, G1 for general purpose, Parallel for batch throughput
- **Memory monitoring:** Track RSS, heap used, GC overhead ratio
- **Thread dumps:** `jstack <pid>` for deadlock/hang diagnosis
- **Profiling:** JFR (free in OpenJDK 11+) for production profiling with < 2% overhead

---

## Related Topics

- [21. JVM Memory Deep Dive](./21-jvm-memory.md) — heap, GC generations
- [22. Garbage Collection](./22-garbage-collection.md) — GC algorithms
- [23. Class Loading](./23-class-loading.md) — how classes are loaded
- [24. Java Memory Model](./24-java-memory-model.md) — visibility, ordering
