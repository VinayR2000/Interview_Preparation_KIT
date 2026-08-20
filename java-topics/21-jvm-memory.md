# 21. JVM Memory — Deep Dive

---

## Theory

JVM memory is organized into distinct regions, each serving a specific purpose. Understanding these regions is critical for performance tuning, debugging OOM errors, and writing memory-efficient code.

### Memory Regions

| Region | Scope | Stores | Error |
|--------|-------|--------|-------|
| Heap | Shared | Objects, arrays | OutOfMemoryError: Java heap space |
| Stack | Per-thread | Frames, locals, references | StackOverflowError |
| Metaspace | Shared (native) | Class metadata, static vars | OutOfMemoryError: Metaspace |
| PC Register | Per-thread | Current bytecode address | — |
| Native Stack | Per-thread | Native method frames | StackOverflowError |
| Direct Memory | Off-heap | NIO buffers | OutOfMemoryError: Direct buffer memory |

---

## Internal Working

### Heap Generations

```
Heap (-Xms / -Xmx):
┌─────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  Young Generation (-Xmn or -XX:NewRatio)                            │
│  ┌────────────────────┐  ┌─────────┐  ┌─────────┐                 │
│  │       Eden          │  │   S0    │  │   S1    │                 │
│  │                     │  │(from)   │  │ (to)    │                 │
│  │  New objects here   │  │survivor │  │survivor │                 │
│  │  (TLAB per thread)  │  │         │  │         │                 │
│  └────────────────────┘  └─────────┘  └─────────┘                 │
│  Ratio: Eden:S0:S1 = 8:1:1 (default)                               │
│                                                                      │
│  Old Generation (Tenured)                                           │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Long-lived objects (survived multiple Minor GCs)            │   │
│  │  Large objects directly allocated here                       │   │
│  │  Objects promoted from Young Gen                             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Object Allocation — TLAB (Thread Local Allocation Buffer)

```
Each thread gets a TLAB in Eden:
┌─────────────────── Eden ───────────────────────────┐
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐  │
│ │ Thread-1 │ │ Thread-2 │ │ Thread-3 │ │  Free   │  │
│ │  TLAB   │ │  TLAB   │ │  TLAB   │ │         │  │
│ │[obj][obj]│ │[obj]    │ │[obj]    │ │         │  │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘  │
└────────────────────────────────────────────────────┘

Allocation in TLAB = pointer bump (extremely fast, no lock)
When TLAB full → request new TLAB or CAS allocate in Eden
```

### Object Promotion Flow

```
New Object
    ↓
Eden (TLAB allocation)
    ↓ Minor GC (Eden full)
Survivor Space (S0 or S1)
    ↓ Age > threshold (default 15) OR survivor full
Old Generation
    ↓ Old Gen full
Major GC / Full GC
```

---

## Diagram

```
Stack Frame Detail:

Thread Stack:
┌─────────────────────────────────────────┐
│  Frame: methodC()                        │  ← top (current)
│  ├─ Local Variables: [this, x, y]       │
│  ├─ Operand Stack: [temp values]        │
│  └─ Return Address: → line in methodB   │
├─────────────────────────────────────────┤
│  Frame: methodB()                        │
│  ├─ Local Variables: [this, param1]     │
│  ├─ Operand Stack: []                   │
│  └─ Return Address: → line in methodA   │
├─────────────────────────────────────────┤
│  Frame: methodA() / main()               │  ← bottom
│  ├─ Local Variables: [args]             │
│  ├─ Operand Stack: []                   │
│  └─ Return Address: → JVM              │
└─────────────────────────────────────────┘

What goes WHERE:

Stack:                          Heap:
- primitive local variables     - all objects (new ...)
- object references             - all arrays
- method parameters             - instance variables (inside objects)
- return addresses              - String objects (pool in heap since Java 7)
                                
Metaspace:                      
- Class objects (metadata)      
- Method bytecode               
- Static variables              
- Constant pool (runtime)       
- Annotations                   
```

---

## Code Examples

### Where Things Are Stored

```java
public class MemoryExample {
    // Static variable → Metaspace
    private static int staticCounter = 0;
    
    // Instance variable → Heap (inside the MemoryExample object)
    private String name;
    private int[] data;
    
    public void process() {
        // Local primitive → Stack
        int localVar = 42;
        
        // Local reference → Stack (the reference)
        // Object → Heap
        String message = new String("hello");
        
        // Array → Heap
        int[] arr = new int[100];
        
        // String literal → String Pool (in Heap since Java 7)
        String literal = "world";
        
        // After method returns:
        // localVar, message (ref), arr (ref) → popped from stack
        // String object, int[] object → eligible for GC (no more references)
        // "world" literal → stays in String Pool
    }
}
```

### Demonstrating Memory Areas

```java
// Stack Overflow
public void infinite() {
    infinite();  // no base case → StackOverflowError
    // Each call adds a frame; default stack size ~512KB-1MB
}

// Heap Overflow
List<byte[]> leak = new ArrayList<>();
while (true) {
    leak.add(new byte[1024 * 1024]);  // 1MB each → OutOfMemoryError: Java heap
}

// Metaspace Overflow (classloader leak)
while (true) {
    ClassLoader cl = new URLClassLoader(new URL[]{url});
    Class<?> loaded = cl.loadClass("SomeClass");
    // If cl is never GC'd → classes stay in Metaspace
}

// Direct Memory Overflow
List<ByteBuffer> buffers = new ArrayList<>();
while (true) {
    buffers.add(ByteBuffer.allocateDirect(1024 * 1024));
    // OutOfMemoryError: Direct buffer memory
}
```

### Memory Monitoring

```java
// MemoryMXBean
MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
System.out.println("Heap used: " + heapUsage.getUsed() / 1024 / 1024 + " MB");
System.out.println("Heap max: " + heapUsage.getMax() / 1024 / 1024 + " MB");

MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
System.out.println("Non-heap (metaspace): " + nonHeap.getUsed() / 1024 / 1024 + " MB");

// Memory Pool details
for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
    System.out.println(pool.getName() + ": " + pool.getUsage());
}
// Output: Eden Space, Survivor Space, Tenured Gen, Metaspace, etc.
```

### Object Size Estimation

```java
// Object header: 12 bytes (64-bit JVM with compressed oops)
// + fields aligned to 8 bytes

// Empty object: 16 bytes (12 header + 4 padding)
Object obj = new Object();

// int field: 16 bytes (12 header + 4 int)
class IntHolder { int x; }  // 16 bytes

// With reference: 24 bytes (12 header + 4 ref + 4 int + 4 padding)
class Mixed { int x; String s; }  // 24 bytes

// Array: 16 + n*element_size (arrays have extra length field)
int[] arr = new int[10];  // 16 + 40 = 56 bytes (aligned to 8)
```

---

## Dry Run

### Object Promotion Through Generations

```java
// Scenario: -Xmx100m -XX:NewRatio=2 (Young=33m, Old=67m)
// Eden=26m, S0=3.3m, S1=3.3m

public void simulate() {
    for (int i = 0; i < 1000; i++) {
        byte[] data = new byte[100_000];  // ~100KB each in Eden
        process(data);
        // 'data' eligible for GC after each iteration
    }
}

// Iteration 1-260: Objects allocated in Eden (26MB / 100KB ≈ 260 objects fit)
// Iteration ~261: Eden full → Minor GC triggered
//   - Live objects (currently referenced) → copied to S0
//   - Dead objects (from previous iterations) → freed
//   - Objects age = 1

// Next 260 iterations: Same cycle
//   - Eden full → Minor GC
//   - S0 objects → S1 (age incremented)
//   - New survivors → S1
//   - S0 cleared (S0 and S1 swap roles)

// After 15 Minor GCs (default): objects with age 15 → promoted to Old Gen
// If Old Gen fills → Major GC (much more expensive)
```

---

## Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| TLAB allocation | ~10 ns | Pointer bump, no lock |
| Eden allocation (no TLAB) | ~50 ns | CAS required |
| Minor GC | 10-50 ms | Proportional to LIVE objects in young gen |
| Major GC (G1) | 100-500 ms | Depends on heap size and live data |
| Full GC (stop-the-world) | 1-10+ seconds | Entire heap scanned |
| Object header access | ~1 ns | Always in cache |

---

## Real Project Usage

### Memory-Efficient Data Processing

```java
// BAD: holds all in memory
List<Record> allRecords = repository.findAll();  // 10M records → OOM!
process(allRecords);

// GOOD: streaming with bounded memory
try (Stream<Record> stream = repository.streamAll()) {
    stream.filter(Record::isActive)
          .map(this::transform)
          .forEach(this::save);
    // Only a few objects in memory at a time
}
```

### Weak References for Caches

```java
// WeakHashMap — entries removed when key has no strong references
Map<Key, Value> cache = new WeakHashMap<>();

// SoftReference — cleared only when memory is low
Map<String, SoftReference<ExpensiveObject>> softCache = new ConcurrentHashMap<>();

public ExpensiveObject get(String key) {
    SoftReference<ExpensiveObject> ref = softCache.get(key);
    ExpensiveObject obj = (ref != null) ? ref.get() : null;
    if (obj == null) {
        obj = loadExpensive(key);
        softCache.put(key, new SoftReference<>(obj));
    }
    return obj;
}
```

---

## Interview Questions and Answers

### Q1: What causes OutOfMemoryError and how to fix each?

**A:**
| Error | Cause | Fix |
|-------|-------|-----|
| `Java heap space` | Too many objects or memory leak | Increase -Xmx, fix leak |
| `GC overhead limit exceeded` | >98% time in GC, <2% freed | Fix leak, increase heap |
| `Metaspace` | Too many classes loaded | Increase -XX:MaxMetaspaceSize, fix classloader leak |
| `Direct buffer memory` | Too many NIO direct buffers | Increase -XX:MaxDirectMemorySize |
| `Unable to create native thread` | Too many threads | Reduce thread count, reduce -Xss |

### Q2: What is the difference between Young Generation and Old Generation?

**A:**
- **Young Gen:** Short-lived objects. Frequent, fast GC (Minor GC). Uses copying collector.
- **Old Gen:** Long-lived objects (survived many Minor GCs). Infrequent, expensive GC (Major GC). Uses mark-sweep-compact.

Objects start in Eden → survive → move through Survivor spaces → eventually promoted to Old Gen.

### Q3: What is a TLAB?

**A:** Thread-Local Allocation Buffer. Each thread gets a private chunk of Eden for object allocation. Since it's private, allocation is just a pointer bump (no synchronization). When TLAB is full, thread requests a new one. This makes `new` extremely fast (~10ns).

### Q4: Why is -Xms == -Xmx recommended in production?

**A:** Prevents heap resizing during runtime. Resizing requires a GC and can cause latency spikes. With fixed heap:
- No resize pauses
- Predictable memory footprint
- OS doesn't need to allocate/deallocate pages

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Large objects in Young Gen | Premature promotion, GC pressure | Set -XX:PretenureSizeThreshold |
| Static collections growing | Old Gen leak, eventual OOM | Bound caches, use WeakReference |
| Deep recursion | StackOverflow | Increase -Xss or use iteration |
| Unused ThreadLocals | Memory pinned per thread | Always call `remove()` |
| Direct ByteBuffers not freed | Off-heap memory leak | Explicitly clean or use try-with-resources |

---

## Best Practices

1. **Set -Xms = -Xmx** for production stability
2. **Monitor heap usage** — alert at 80% utilization
3. **Use streaming** for large datasets — don't load all into memory
4. **Clear ThreadLocal** — always call `remove()` when done
5. **Profile with JFR/VisualVM** — identify allocation hot spots
6. **Avoid finalizers** — use `try-with-resources` and `Cleaner` (Java 9+)
7. **Use object pooling sparingly** — JVM allocation is already fast

---

## Production Considerations

- **Container memory:** JVM sees container limits with `-XX:+UseContainerSupport` (default Java 10+)
- **RSS vs Heap:** JVM process uses more memory than heap (metaspace + thread stacks + native + direct buffers)
- **Memory limits:** Set `-XX:MaxRAMPercentage=75.0` to use 75% of container memory for heap
- **Monitoring:** Track heap after GC (baseline), allocation rate, promotion rate
- **Alerting:** Alert on heap > 80%, GC pause > threshold, allocation rate spike

---

## Related Topics

- [20. JVM Internals](./20-jvm-internals.md) — JVM architecture overview
- [22. Garbage Collection](./22-garbage-collection.md) — GC algorithms
- [24. Java Memory Model](./24-java-memory-model.md) — visibility and ordering
