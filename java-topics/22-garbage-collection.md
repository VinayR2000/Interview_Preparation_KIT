# 22. Garbage Collection

---

## Theory

Garbage Collection (GC) is JVM's automatic memory management. It identifies and reclaims objects no longer reachable from any **GC root**, freeing developers from manual memory management.

### GC Roots

Objects directly or indirectly referenced from GC roots are **alive**; everything else is garbage.

- Local variables in active threads (stack frames)
- Static variables
- JNI references
- Active threads themselves
- System class loader classes

### GC Events

| Event | Scope | When | Duration |
|-------|-------|------|----------|
| Minor GC | Young Gen only | Eden full | 10-50ms |
| Major GC | Old Gen | Old Gen filling up | 100ms-1s |
| Full GC | Entire heap + Metaspace | Last resort | 1-10s+ |

---

## Internal Working

### Minor GC (Young Generation)

```
Before Minor GC:
Eden: [A][B][C][D][E]  (full)
S0:   [X](age=2)
S1:   (empty)

Step 1: Mark reachable objects from GC roots
  - A, C, E are reachable; B, D are garbage
  - X in S0 is reachable

Step 2: Copy live objects to S1 (increment age)
S1: [A](age=1) [C](age=1) [E](age=1) [X](age=3)

Step 3: Clear Eden and S0
Eden: (empty)
S0:   (empty)

Step 4: Promote if age >= threshold (15 default)
  - X has age=3 → stays in survivor (not yet 15)
  - If age >= 15 → move to Old Gen

After Minor GC:
Eden: (empty, ready for new allocations)
S0:   (empty, becomes "to" space next time)
S1:   [A][C][E][X]
```

### GC Algorithms

```
Mark-Sweep:
1. Mark: traverse from GC roots, mark all reachable
2. Sweep: scan heap, free unmarked objects
Problem: Memory fragmentation

Mark-Sweep-Compact:
1. Mark: same as above
2. Sweep: free unmarked
3. Compact: move live objects together (defragment)
Problem: Expensive compaction

Copying:
1. Divide space in two halves
2. Copy live objects to other half
3. Clear original half
Used in: Young Generation (Eden → Survivor)
Pro: No fragmentation. Con: Half memory wasted.
```

### GC Collectors

```
┌────────────────────────────────────────────────────────────────────┐
│ Collector    │ Algorithm       │ Pause     │ Throughput │ Use Case  │
├──────────────┼─────────────────┼───────────┼────────────┼───────────┤
│ Serial       │ Mark-Copy/MSC   │ High      │ Low        │ Small heap│
│ Parallel     │ Mark-Copy/MSC   │ Medium    │ High       │ Batch/ETL │
│ CMS (removed)│ Mark-Sweep      │ Low       │ Medium     │ Deprecated│
│ G1 (default) │ Region-based    │ Predictable│ Good      │ General   │
│ ZGC          │ Colored pointers│ <1ms      │ Good       │ Low-lat   │
│ Shenandoah   │ Brooks pointers │ <10ms     │ Good       │ Low-lat   │
└────────────────────────────────────────────────────────────────────┘
```

---

## Diagram

```
G1 GC Region Layout:

┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ E │ E │ S │ O │ O │ E │ H │ H │ O │ E │ S │ O │
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
E = Eden, S = Survivor, O = Old, H = Humongous (large object)

G1 divides heap into equal-sized regions (1-32MB each)
Regions can change role dynamically
Collects regions with most garbage first ("Garbage First")

G1 GC Phases:
1. Young GC: Evacuate Eden + Survivor → new Survivor/Old
2. Concurrent Mark: Find garbage in Old regions (concurrent with app)
3. Mixed GC: Collect Young + selected Old regions
4. Full GC: Last resort (stop-the-world, single-threaded)

ZGC Approach:
┌─────────────────────────────────────────────────────┐
│ Uses colored pointers (metadata in pointer bits)    │
│ Almost all work done concurrently                    │
│ Pause time < 1ms regardless of heap size            │
│ Supports heap up to 16TB                            │
│ Slight throughput cost (~5-15%)                      │
└─────────────────────────────────────────────────────┘
```

---

## Code Examples

### GC Configuration

```bash
# G1 (default since Java 9)
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar app.jar

# ZGC (Java 15+ production ready)
java -XX:+UseZGC -jar app.jar

# Shenandoah (Java 12+, not in Oracle JDK)
java -XX:+UseShenandoahGC -jar app.jar

# Parallel (batch/throughput)
java -XX:+UseParallelGC -jar app.jar

# GC Tuning parameters
java -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \         # target max pause
  -XX:G1HeapRegionSize=16m \         # region size
  -XX:InitiatingHeapOccupancyPercent=45 \  # start concurrent mark at 45% heap
  -XX:G1ReservePercent=10 \          # reserve for promotion
  -XX:MaxTenuringThreshold=15 \      # age before promotion
  -jar app.jar
```

### GC Logging (Java 11+)

```bash
# Unified logging
java -Xlog:gc*:file=gc.log:time,uptime,level,tags -jar app.jar

# Detailed GC logging
java -Xlog:gc*=debug:file=gc-debug.log:time,uptime -jar app.jar

# Specific categories
java -Xlog:gc,gc+heap,gc+phases:file=gc.log -jar app.jar
```

### Triggering GC (for understanding, not production)

```java
public class GCDemo {
    public static void main(String[] args) throws InterruptedException {
        // Create garbage
        for (int i = 0; i < 100000; i++) {
            new byte[1024];  // 1KB, immediately eligible for GC
        }
        
        // Register for GC notification
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println(gc.getName() + 
                " - Collections: " + gc.getCollectionCount() +
                " - Time: " + gc.getCollectionTime() + "ms");
        }
    }
}
```

### Finalization (deprecated but know for interviews)

```java
// DEPRECATED — avoid in new code
public class Resource {
    @Override
    protected void finalize() throws Throwable {
        try {
            cleanup();  // unreliable — may never be called!
        } finally {
            super.finalize();
        }
    }
}

// MODERN replacement: Cleaner (Java 9+)
public class Resource implements AutoCloseable {
    private static final Cleaner cleaner = Cleaner.create();
    private final Cleaner.Cleanable cleanable;
    
    public Resource() {
        cleanable = cleaner.register(this, () -> {
            // cleanup logic (must not reference 'this'!)
            System.out.println("Cleaning up native resource");
        });
    }
    
    @Override
    public void close() {
        cleanable.clean();  // explicit cleanup
    }
}
```

---

## Dry Run

### G1 GC Cycle

```
Initial State: Heap = 4GB, 256 regions of 16MB each
Eden: 100 regions, Survivor: 10, Old: 100, Free: 46

1. Application allocates → Eden regions fill up

2. Young GC triggered (Eden full):
   - STW pause starts (~20ms)
   - Copy live Eden + Survivor objects → new Survivor regions
   - Dead objects freed (entire Eden regions reclaimed)
   - STW pause ends
   - Result: Eden=0, Survivor=15, Old=100, Free=131

3. Concurrent Marking (Old > 45% threshold):
   - Initial Mark (STW, piggybacks on Young GC)
   - Root Region Scan (concurrent)
   - Concurrent Mark (concurrent — marks live objects in Old)
   - Remark (STW, ~5ms — finalize marking)
   - Cleanup (STW, ~5ms — identify garbage regions)

4. Mixed GC (collect Young + garbage-heavy Old regions):
   - STW pause (~30ms)
   - Evacuate Eden + Survivor + selected Old regions
   - Compacts in the process (no fragmentation)
   - Result: reclaimed Old regions added to free

5. If pause target exceeded → collect fewer Old regions per Mixed GC
```

---

## Complexity

| Collector | Pause Time | Throughput | Heap Range | Best For |
|-----------|-----------|-----------|-----------|----------|
| Serial | O(heap) | Low | <100MB | Embedded, single-core |
| Parallel | O(live) | Highest | 1-8GB | Batch processing |
| G1 | O(region) predictable | Good | 4-64GB | General purpose |
| ZGC | O(1) ~<1ms | Good (-10%) | 8GB-16TB | Ultra-low latency |
| Shenandoah | O(1) ~<10ms | Good (-15%) | 4-256GB | Low latency |

---

## Real Project Usage

### GC-Friendly Coding

```java
// 1. Reuse objects where sensible
StringBuilder sb = new StringBuilder();
for (String item : items) {
    sb.setLength(0);  // reuse instead of new StringBuilder each time
    sb.append(prefix).append(item);
    process(sb.toString());
}

// 2. Use primitives over wrappers in hot paths
int[] ids = new int[1000];       // one array object
Integer[] boxed = new Integer[1000];  // 1001 objects! (array + 1000 Integer)

// 3. Avoid short-lived large objects
// BAD: large temporary array
byte[] temp = new byte[10_000_000];  // 10MB, may go straight to Old Gen!
// BETTER: stream processing or smaller buffers

// 4. Clear collections when done
List<BigObject> list = new ArrayList<>();
// ... use list ...
list.clear();  // helps GC sooner (removes references)
list = null;   // if the list reference itself should be GC'd

// 5. Use try-with-resources for resources
try (Connection conn = dataSource.getConnection()) {
    // ...
}  // closed immediately, reference eligible for GC
```

---

## Interview Questions and Answers

### Q1: How does garbage collection work?

**A:** GC identifies unreachable objects (not referenced from any GC root) and reclaims their memory:
1. **Marking phase** — traverse from GC roots, mark all reachable objects as alive
2. **Sweep/Copy/Compact** — free unmarked objects (algorithm depends on collector)
3. **Generational hypothesis** — most objects die young, so separate Young/Old generations with different strategies

### Q2: What is the difference between Minor GC, Major GC, and Full GC?

**A:**
- **Minor GC** — collects Young Generation only. Fast (10-50ms). Triggered when Eden is full.
- **Major GC** — collects Old Generation. Slower. Triggered when Old Gen is filling.
- **Full GC** — collects entire heap + metaspace. Very expensive (seconds). Last resort.

G1/ZGC blur these lines with concurrent collection.

### Q3: What is stop-the-world?

**A:** A GC event that **pauses all application threads** to safely collect garbage. During STW:
- No application code runs
- Latency spike (application freezes)
- Duration depends on collector and heap size

Modern collectors (ZGC, Shenandoah) minimize STW to < 1-10ms regardless of heap size.

### Q4: What is a memory leak in Java?

**A:** Objects that are **reachable** (referenced) but no longer **needed**. GC can't collect them because they're still referenced. Common causes:
- Static collections that grow forever
- Listeners/callbacks not unregistered
- ThreadLocal not removed
- Unclosed resources holding references
- Inner class holding reference to outer class

### Q5: How do you choose a GC for production?

**A:**
- **General purpose / balanced:** G1 (default since Java 9)
- **Ultra-low latency (<1ms pauses):** ZGC
- **Maximum throughput (batch/ETL):** Parallel GC
- **Low latency with OpenJDK:** Shenandoah

**Decision factors:** Latency requirements, heap size, throughput needs, JDK version.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| `System.gc()` in production | Triggers Full GC, massive pause | Never call explicitly |
| Relying on `finalize()` | Not guaranteed to run, delays GC | Use `try-with-resources` |
| Huge Young Gen with long-lived objects | Expensive minor GCs (lots of survivors) | Tune generation sizes |
| Ignoring GC logs | Can't diagnose performance issues | Always enable GC logging |
| Using Parallel GC for low-latency | Long stop-the-world pauses | Use G1 or ZGC |

---

## Best Practices

1. **Use G1 as default** — good for most workloads
2. **Enable GC logging always** — even in production (near-zero cost)
3. **Set pause time goals** — `-XX:MaxGCPauseMillis` (G1 adapts to meet it)
4. **Avoid Full GC** — if happening, you likely have a memory leak or undersized heap
5. **Profile allocation rate** — high allocation = frequent GC = more pauses
6. **Use ZGC** for latency-sensitive services (Java 15+)
7. **Don't prematurely tune** — let GC do its job, tune only with evidence

---

## Production Considerations

- **Monitoring metrics:** GC pause duration, GC frequency, allocation rate, promotion rate, heap after GC
- **Alerting:** Alert on GC pause > SLA, heap after GC > 80% (growing = likely leak)
- **Heap dumps:** Enable `-XX:+HeapDumpOnOutOfMemoryError` to diagnose OOM
- **Tools:** GCViewer, GCEasy (log analysis), Eclipse MAT (heap dump analysis), JFR + JMC (profiling)
- **Container limits:** Ensure GC respects container memory (`-XX:MaxRAMPercentage`)

---

## Related Topics

- [20. JVM Internals](./20-jvm-internals.md) — JVM architecture
- [21. JVM Memory](./21-jvm-memory.md) — memory regions
- [23. Class Loading](./23-class-loading.md) — class lifecycle
