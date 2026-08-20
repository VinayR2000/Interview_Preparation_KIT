# 33. Java Performance

## Theory

Java performance optimization involves identifying and resolving bottlenecks in CPU usage, memory allocation, I/O operations, and concurrency. Understanding how the JVM executes code, manages memory, and optimizes at runtime is essential for writing high-performance applications.

### Key Performance Areas

| Area | Bottleneck | Indicators |
|------|-----------|------------|
| CPU | Computation-heavy code, tight loops | High CPU%, slow response |
| Memory | Excessive allocation, GC pressure | Frequent GC pauses, OOM |
| I/O | Disk/network latency | Thread waiting, low CPU% |
| Concurrency | Lock contention, thread starvation | High thread count, low throughput |

### Performance Optimization Principles

1. **Measure first, optimize later** — Never guess where the bottleneck is
2. **Premature optimization is the root of all evil** — Focus on correct code first
3. **Big O matters more than micro-optimizations** — Algorithm choice dominates
4. **JIT compiler is smart** — Don't outsmart it; write clean, idiomatic code
5. **GC-friendly code** — Reduce allocation rate and object lifetime

---

## Internal Working

### JIT Compilation Pipeline

```
Java Bytecode
     ↓ (interpreted first few executions)
Method call counter reaches threshold (C1: ~1500, C2: ~10000)
     ↓
C1 Compiler (Client)
  → Quick compilation, basic optimizations
     ↓ (if hot enough)
C2 Compiler (Server)
  → Aggressive optimization:
    - Method inlining
    - Loop unrolling
    - Escape analysis
    - Dead code elimination
    - Null check elimination
    - Branch prediction
     ↓
Native machine code (stored in Code Cache)
     ↓
Direct execution (no interpretation)
```

### Escape Analysis

```
Object does NOT escape method → Stack allocation (no GC needed!)

void process() {
    Point p = new Point(1, 2);  // May be stack-allocated
    int sum = p.x + p.y;       // JIT may eliminate object entirely
    return sum;                 // "Scalar replacement"
}

Object ESCAPES method → Heap allocation (subject to GC)

Point createPoint() {
    Point p = new Point(1, 2);
    return p;  // Escapes! Must be on heap
}
```

### Memory Allocation Path

```
new Object()
     ↓
Thread Local Allocation Buffer (TLAB)
  → Fast: bump pointer in thread-private buffer
  → No synchronization needed!
     ↓ (TLAB full)
Allocate new TLAB from Eden
     ↓ (Eden full)
Minor GC (Young Generation collection)
     ↓ (survivors promoted)
Old Generation
     ↓ (Old Gen full)
Major/Full GC (stop-the-world pause)
```

---

## Diagram

### Performance Analysis Workflow

```
1. Define performance goals (latency, throughput, resource usage)
        ↓
2. Establish baseline (benchmark current state)
        ↓
3. Identify bottleneck (profiling, monitoring)
        ↓
4. Hypothesize root cause
        ↓
5. Apply targeted fix
        ↓
6. Measure impact (re-benchmark)
        ↓
7. Repeat if needed
```

### Common Bottleneck Locations

```
Request Flow:
Client → Network → Web Server → Business Logic → Database → Response
          ↑           ↑              ↑               ↑
      Latency     Thread Pool    CPU/Memory      I/O Wait
      Bandwidth   Connection     GC Pauses       Queries
                  Limits         Lock Contention  Connection Pool
```

---

## Code

### String Performance

```java
public class StringPerformance {
    
    // ❌ BAD: String concatenation in loop (creates N intermediate objects)
    String badConcat(List<String> items) {
        String result = "";
        for (String item : items) {
            result += item + ","; // Each += creates new String object
        }
        return result;
    }
    // For 10,000 items: ~100ms, ~50MB garbage generated
    
    // ✅ GOOD: StringBuilder
    String goodConcat(List<String> items) {
        StringBuilder sb = new StringBuilder(items.size() * 20); // Pre-size
        for (String item : items) {
            sb.append(item).append(",");
        }
        return sb.toString();
    }
    // For 10,000 items: ~1ms, minimal garbage
    
    // ✅ BEST: String.join or Collectors.joining
    String bestConcat(List<String> items) {
        return String.join(",", items);
    }
    
    // Note: Single-line concatenation is fine (compiler optimizes it)
    String singleLine(String a, String b, String c) {
        return a + " " + b + " " + c; // Compiler uses StringBuilder or invokedynamic
    }
}
```

### Collection Selection for Performance

```java
import java.util.*;

public class CollectionPerformance {
    
    // Choose collection based on access patterns:
    
    // Random access by index → ArrayList
    // Time: get(i) = O(1), add(end) = O(1) amortized, add(middle) = O(n)
    List<String> randomAccess = new ArrayList<>();
    
    // Frequent insert/remove at ends → ArrayDeque
    // Time: addFirst/Last = O(1), removeFirst/Last = O(1)
    Deque<String> queue = new ArrayDeque<>();
    
    // Frequent insert/remove at middle → LinkedList (rarely best choice)
    // Time: add/remove at iterator position = O(1), but get(i) = O(n)
    
    // Unique elements, fast lookup → HashSet
    // Time: contains/add/remove = O(1) average
    Set<String> uniqueItems = new HashSet<>();
    
    // Sorted unique elements → TreeSet
    // Time: contains/add/remove = O(log n)
    Set<String> sorted = new TreeSet<>();
    
    // Key-value, fast lookup → HashMap
    // Time: get/put = O(1) average
    Map<String, Integer> lookup = new HashMap<>(expectedSize * 4 / 3 + 1);
    // Pre-size to avoid rehashing!
    
    // Thread-safe map → ConcurrentHashMap (NOT Collections.synchronizedMap)
    Map<String, Integer> concurrent = new ConcurrentHashMap<>();
    
    // ❌ BAD: Using LinkedList as general-purpose List
    // ✅ GOOD: ArrayList for 99% of use cases (cache-friendly, less memory)
    
    // Pre-sizing collections
    void preSizing() {
        // ❌ BAD: Grows and rehashes multiple times
        Map<String, Object> map = new HashMap<>(); // default cap=16
        for (int i = 0; i < 10000; i++) {
            map.put("key" + i, new Object());
        }
        
        // ✅ GOOD: Pre-sized, no rehashing
        Map<String, Object> sized = new HashMap<>(14000); // capacity > size/0.75
    }
}
```

### Object Creation and Pooling

```java
public class ObjectCreationPerformance {
    
    // ❌ BAD: Creating expensive objects repeatedly
    String formatDate(LocalDateTime dt) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dt.format(formatter); // Creates formatter EVERY call
    }
    
    // ✅ GOOD: Reuse immutable/thread-safe objects
    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    String formatDateGood(LocalDateTime dt) {
        return dt.format(FORMATTER); // Reuse
    }
    
    // ❌ BAD: Autoboxing in tight loops
    long badSum() {
        Long sum = 0L; // Boxed Long
        for (long i = 0; i < 1_000_000; i++) {
            sum += i; // Unbox, add, rebox → creates ~1M Long objects!
        }
        return sum;
    }
    
    // ✅ GOOD: Use primitives
    long goodSum() {
        long sum = 0L; // primitive
        for (long i = 0; i < 1_000_000; i++) {
            sum += i; // No object creation
        }
        return sum;
    }
    
    // ❌ BAD: Boolean.valueOf in hot path (though cached for true/false)
    // ✅ GOOD: Use Integer.valueOf for -128 to 127 (cached by JVM)
    // ✅ GOOD: Use primitive arrays instead of wrapper arrays
}
```

### Thread Pool Sizing

```java
import java.util.concurrent.*;

public class ThreadPoolSizing {
    
    // Formula for optimal pool size:
    // CPU-bound tasks: threads = number of CPU cores
    // I/O-bound tasks: threads = cores * (1 + wait_time/compute_time)
    
    // CPU-bound work
    ExecutorService cpuPool() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(cores);
        // More threads = more context switching = slower!
    }
    
    // I/O-bound work (DB calls, HTTP calls)
    ExecutorService ioPool() {
        int cores = Runtime.getRuntime().availableProcessors();
        // If tasks wait 90% of time (I/O), compute 10%:
        // threads = cores * (1 + 0.9/0.1) = cores * 10
        int poolSize = cores * 10; // Typical for I/O-heavy work
        return new ThreadPoolExecutor(
            cores,          // core pool size
            poolSize,       // max pool size
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // bounded queue
            new ThreadPoolExecutor.CallerRunsPolicy() // backpressure
        );
    }
    
    // Java 21: Virtual threads for I/O-bound (best approach)
    ExecutorService virtualPool() {
        return Executors.newVirtualThreadPerTaskExecutor();
        // No sizing needed! Scales automatically.
    }
}
```

### Memory Leak Patterns

```java
public class MemoryLeakExamples {
    
    // Leak 1: Static collections that grow forever
    private static final Map<String, Object> CACHE = new HashMap<>();
    // ❌ Never evicts! Grows until OOM
    
    // Fix: Use bounded cache or WeakHashMap
    private static final Map<String, Object> BOUNDED_CACHE = 
        Collections.synchronizedMap(new LinkedHashMap<>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > 1000; // Evict when > 1000 entries
            }
        });
    
    // Leak 2: Unclosed resources
    void leakyMethod() throws Exception {
        Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT ...");
        ResultSet rs = ps.executeQuery();
        // If exception thrown here, resources leak!
        rs.close();
        ps.close();
        conn.close();
    }
    
    // Fix: try-with-resources
    void fixedMethod() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT ...");
             ResultSet rs = ps.executeQuery()) {
            // Process results
        } // All closed automatically
    }
    
    // Leak 3: Listeners/callbacks not deregistered
    class EventSystem {
        private List<EventListener> listeners = new ArrayList<>();
        
        void register(EventListener l) { listeners.add(l); }
        // If listeners are never removed, objects they reference can't be GC'd
        
        void unregister(EventListener l) { listeners.remove(l); } // Always provide this!
    }
    
    // Leak 4: Inner class holds reference to outer
    class Outer {
        byte[] largeData = new byte[10_000_000]; // 10MB
        
        Runnable createTask() {
            return new Runnable() { // Anonymous inner class holds ref to Outer!
                public void run() { /* doesn't use largeData but holds Outer ref */ }
            };
        }
        
        // Fix: use static inner class or lambda (if no outer ref needed)
        Runnable createTaskFixed() {
            return () -> { /* lambda doesn't capture 'this' unless needed */ };
        }
    }
}
```

### Benchmarking with JMH

```java
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
public class StringBenchmark {
    
    @Param({"10", "100", "1000"})
    private int size;
    
    private List<String> items;
    
    @Setup
    public void setup() {
        items = IntStream.range(0, size)
            .mapToObj(i -> "item" + i)
            .collect(Collectors.toList());
    }
    
    @Benchmark
    public String concatPlus() {
        String result = "";
        for (String item : items) {
            result += item;
        }
        return result;
    }
    
    @Benchmark
    public String concatBuilder() {
        StringBuilder sb = new StringBuilder(size * 8);
        for (String item : items) {
            sb.append(item);
        }
        return sb.toString();
    }
    
    @Benchmark
    public String concatJoin() {
        return String.join("", items);
    }
}

// Run: java -jar benchmarks.jar StringBenchmark
// Results show actual ns/op for each approach
```

### GC Tuning

```java
public class GCTuning {
    /*
    Common GC Flags:
    
    // Choose GC algorithm
    -XX:+UseG1GC              (default Java 9+, good general purpose)
    -XX:+UseZGC               (Java 15+, ultra-low latency, <1ms pauses)
    -XX:+UseShenandoahGC      (Java 12+, low latency alternative)
    -XX:+UseParallelGC        (throughput-optimized, larger pauses)
    
    // Heap sizing
    -Xms4g -Xmx4g            (min=max avoids resize, predictable)
    -XX:NewRatio=2            (Old:Young = 2:1)
    -XX:SurvivorRatio=8       (Eden:Survivor = 8:1)
    
    // G1 specific
    -XX:MaxGCPauseMillis=200  (target pause time)
    -XX:G1HeapRegionSize=16m  (region size, power of 2)
    
    // Logging
    -Xlog:gc*:file=gc.log:time,uptime,level,tags
    
    // Typical production config (Java 21, 8GB heap):
    -Xms8g -Xmx8g
    -XX:+UseZGC
    -XX:+ZGenerational
    -Xlog:gc*:file=/var/log/app/gc.log:time
    */
    
    // GC-friendly coding patterns:
    
    // 1. Reduce allocation rate
    // ❌ BAD: allocate in hot loop
    void bad() {
        for (int i = 0; i < 1000000; i++) {
            String s = new String("data" + i); // Creates garbage
            process(s);
        }
    }
    
    // 2. Short-lived objects are cheap (die in Young Gen, Minor GC)
    // Long-lived objects are expensive (promoted to Old Gen, Major GC)
    
    // 3. Avoid finalizers and cleaners (delay GC, add overhead)
    // Use try-with-resources instead
}
```

---

## Dry Run

### Profiling a Slow Endpoint

```
Symptoms: /api/users endpoint takes 2000ms (target: 200ms)

Step 1: Enable JFR recording
  $ java -XX:StartFlightRecording=duration=60s,filename=profile.jfr ...
  
Step 2: Analyze in JMC (JDK Mission Control)
  CPU profiling shows:
    40% time in UserRepository.findAll()
    30% time in ObjectMapper.writeValueAsString()
    20% time in GC pauses
    10% other

Step 3: Identify root causes
  - findAll() loads 10,000 rows (no pagination!)
  - Jackson serializes huge list
  - GC triggered by large object allocation

Step 4: Fixes
  - Add pagination: LIMIT 20 OFFSET 0
  - Reduce response payload (select specific columns)
  - Pre-size collections to avoid resizing

Step 5: Re-measure
  Response time: 2000ms → 45ms ✓
```

---

## Complexity

### Algorithm Complexity Impact

| Operation | O(1) | O(log n) | O(n) | O(n log n) | O(n²) |
|-----------|------|----------|------|-----------|--------|
| n=100 | 1 | 7 | 100 | 700 | 10,000 |
| n=10,000 | 1 | 14 | 10,000 | 140,000 | 100,000,000 |
| n=1,000,000 | 1 | 20 | 1,000,000 | 20,000,000 | 1,000,000,000,000 |

### Collection Operation Complexity

| Collection | get(i) | contains | add | remove | Iterator |
|-----------|--------|----------|-----|--------|----------|
| ArrayList | O(1) | O(n) | O(1)* | O(n) | O(n) |
| LinkedList | O(n) | O(n) | O(1)** | O(1)** | O(n) |
| HashSet | - | O(1) | O(1) | O(1) | O(n) |
| TreeSet | - | O(log n) | O(log n) | O(log n) | O(n) |
| HashMap | O(1) | O(1) | O(1) | O(1) | O(n) |
| TreeMap | O(log n) | O(log n) | O(log n) | O(log n) | O(n) |

*amortized, **at known position

---

## Real Project Usage

### 1. Production Performance Monitoring

```java
// Custom metrics for monitoring
@Component
public class PerformanceMetrics {
    private final MeterRegistry registry;
    
    public PerformanceMetrics(MeterRegistry registry) {
        this.registry = registry;
        
        // Track JVM memory
        new JvmMemoryMetrics().bindTo(registry);
        new JvmGcMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
    }
    
    // Custom timer for business operations
    public <T> T timed(String name, Supplier<T> operation) {
        return registry.timer(name).record(operation);
    }
}

// Usage in service
@Service
public class OrderService {
    private final PerformanceMetrics metrics;
    
    public Order processOrder(OrderRequest request) {
        return metrics.timed("order.process", () -> {
            // Business logic
            return createOrder(request);
        });
    }
}
```

### 2. Efficient Batch Processing

```java
public class BatchProcessor {
    
    // ❌ BAD: N+1 queries
    List<OrderDTO> bad_getOrders(List<Long> ids) {
        return ids.stream()
            .map(id -> orderRepository.findById(id)) // N queries!
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    // ✅ GOOD: Batch fetch
    List<OrderDTO> good_getOrders(List<Long> ids) {
        Map<Long, Order> orders = orderRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        
        return ids.stream()
            .map(orders::get)
            .filter(Objects::nonNull)
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    // ✅ GOOD: Chunked processing for large datasets
    void processLargeDataset(List<Record> records) {
        int chunkSize = 1000;
        for (int i = 0; i < records.size(); i += chunkSize) {
            List<Record> chunk = records.subList(i, 
                Math.min(i + chunkSize, records.size()));
            processChunk(chunk); // Process in manageable batches
        }
    }
}
```

### 3. Caching Strategy

```java
@Service
public class ProductService {
    
    // L1: In-memory cache (Caffeine)
    private final Cache<Long, Product> localCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .recordStats() // Enable monitoring
        .build();
    
    // L2: Distributed cache (Redis) via Spring
    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public Product findById(Long id) {
        // Check local cache first
        Product cached = localCache.getIfPresent(id);
        if (cached != null) return cached;
        
        // Database query
        Product product = repository.findById(id).orElse(null);
        if (product != null) {
            localCache.put(id, product);
        }
        return product;
    }
}
```

---

## Interview Questions and Answers

### Q1: How do you identify performance bottlenecks in a Java application?
**A**: 1) Monitor metrics (CPU, memory, GC, thread count, response times). 2) Use profilers: JFR + JMC for production-safe profiling, async-profiler for CPU/allocation profiling. 3) For specific issues: jstack for thread dumps (deadlocks, contention), jmap for heap dumps (memory leaks), GC logs for GC analysis. 4) APM tools (Datadog, New Relic) for distributed tracing. Always measure before optimizing.

### Q2: What causes memory leaks in Java?
**A**: Despite GC, leaks occur when objects are referenced but no longer needed: (1) Static collections growing unbounded, (2) Unclosed resources (connections, streams), (3) Listeners/callbacks not deregistered, (4) Inner class references to outer class, (5) ThreadLocal not cleaned up, (6) Custom ClassLoader holding class references. Detect with: heap dumps (jmap), MAT (Memory Analyzer Tool), or JFR allocation tracking.

### Q3: How do you tune GC for a Java application?
**A**: 1) Choose the right collector: G1 (balanced), ZGC (low latency <1ms), Parallel (max throughput). 2) Set heap size: -Xms = -Xmx (avoid resizing). 3) Analyze GC logs for pause times and frequency. 4) For G1: adjust MaxGCPauseMillis. 5) Reduce allocation rate in code (reuse objects, pre-size collections). The best GC tuning is reducing garbage creation in code.

### Q4: What is the difference between CPU profiling and allocation profiling?
**A**: CPU profiling shows where time is spent executing code (hot methods, call trees). Allocation profiling shows where objects are created and how much memory is allocated per call site. A method might not be CPU-intensive but could create millions of short-lived objects causing GC pressure. Both are needed for complete performance analysis.

### Q5: How do you size a thread pool?
**A**: For CPU-bound tasks: threads = CPU cores (more causes context-switching overhead). For I/O-bound tasks: threads = cores * (1 + wait_time/compute_time). For mixed: separate pools for CPU and I/O work. With Java 21, use virtual threads for I/O-bound work (no manual sizing needed). Always benchmark with realistic load.

### Q6: What is JIT compilation and how does it affect performance?
**A**: JIT (Just-In-Time) compiles hot bytecode to native machine code at runtime. The JVM interprets code initially, then compiles frequently-executed methods (C1 at ~1500 calls, C2 at ~10000). JIT applies optimizations: inlining, escape analysis (stack allocation), loop unrolling, dead code elimination. This is why Java warmup matters — cold starts are slower until JIT kicks in.

---

## Follow-up Questions and Answers

### Q: What is escape analysis?
**A**: The JIT compiler analyzes whether an object's reference escapes the method scope. If it doesn't escape: the object can be stack-allocated (no GC), or eliminated entirely (scalar replacement — fields become local variables). This is why short-lived local objects in tight loops may have zero allocation cost after JIT optimization.

### Q: How does false sharing affect performance?
**A**: False sharing occurs when two threads modify variables on the same CPU cache line (64 bytes). Each write invalidates the entire cache line for other cores, even though they're accessing different variables. Fix: add padding between frequently-written fields, or use `@Contended` annotation (JDK internal). Can cause 10-100x slowdown in microbenchmarks.

### Q: What are the profiling tools available?
**A**:
- **JFR (Flight Recorder)**: Production-safe, low-overhead (<2%) continuous profiling built into JVM
- **JMC (Mission Control)**: GUI for analyzing JFR recordings
- **async-profiler**: Low-overhead sampling profiler (CPU, allocation, lock)
- **VisualVM**: GUI for heap/thread monitoring and profiling
- **jstack**: Thread dump (deadlock detection)
- **jmap**: Heap dump, histogram
- **jcmd**: Swiss army knife (GC, JFR, VM info)
- **jstat**: GC statistics in real-time

---

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| String concatenation in loops | O(n²) time, massive garbage | StringBuilder or String.join |
| Not pre-sizing collections | Multiple rehash/resize operations | Constructor capacity parameter |
| Autoboxing in tight loops | Millions of wrapper objects | Use primitives |
| Synchronizing on wrong scope | Either too broad (contention) or too narrow (bugs) | Minimize critical section |
| Using LinkedList as default List | Cache-unfriendly, more memory | ArrayList for 99% of cases |
| Logging with string concatenation at DEBUG level | Concatenation happens even if not logged | Use parameterized logging: log.debug("x={}", x) |
| Not closing resources | Connection/file handle leaks | try-with-resources |
| Premature optimization | Wasted effort, complex code | Profile first, optimize measured bottlenecks |

---

## Best Practices

1. **Measure with JMH for micro-benchmarks** — Don't use System.currentTimeMillis() for benchmarking
2. **Use JFR in production** — Low overhead, always-on profiling
3. **Pre-size collections** — `new HashMap<>(expectedSize * 4/3 + 1)`
4. **Prefer primitives over wrappers** — Avoid autoboxing in hot paths
5. **Use StringBuilder for loop concatenation** — Or better: Stream + Collectors.joining()
6. **Choose the right data structure** — O(1) lookup vs O(n) scan makes orders of magnitude difference
7. **Pool expensive objects** — Database connections, thread pools, formatters
8. **Set -Xms equal to -Xmx** — Avoid heap resize pauses
9. **Enable GC logging in production** — `-Xlog:gc*:file=gc.log`
10. **Profile under realistic load** — Synthetic benchmarks miss real-world contention patterns

---

## Production Considerations

- **Warmup**: JIT needs time to optimize hot paths. Use warmup requests after deployment. Consider CDS (Class Data Sharing) and AOT (GraalVM Native Image) for fast startup.
- **GC selection**: ZGC for latency-sensitive services (<1ms pauses). G1 for general services. Parallel GC only for batch processing where throughput matters more than latency.
- **Heap sizing**: Too small = frequent GC. Too large = long GC pauses (for non-ZGC). Monitor and right-size based on live data set.
- **Container awareness**: JVM respects container memory limits since Java 10. Use `-XX:MaxRAMPercentage=75` instead of fixed -Xmx in containers.
- **Thread dumps for debugging**: `jcmd <pid> Thread.print` or `kill -3 <pid>` (Unix). Analyze with fastthread.io or IntelliJ.
- **Heap dumps for memory issues**: `jcmd <pid> GC.heap_dump /tmp/heap.hprof`. Analyze with Eclipse MAT or VisualVM.
- **Continuous profiling**: Tools like Pyroscope, Datadog Continuous Profiler, or AWS CodeGuru run JFR continuously and aggregate flame graphs over time.

---

## Related Topics

- [20. JVM Internals](./20-jvm-internals.md)
- [21. JVM Memory](./21-jvm-memory.md)
- [22. Garbage Collection](./22-garbage-collection.md)
- [17. Executor Framework](./17-executor-framework.md)
- [06. Collections Framework](./06-collections-framework.md)
- [04. String Handling](./04-string-handling.md)
