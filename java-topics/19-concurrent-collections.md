# 19. Concurrent Collections

---

## Theory

`java.util.concurrent` provides thread-safe collections designed for high concurrency — without the bottleneck of `synchronized` wrappers (`Collections.synchronizedList()`).

### Why Not Just `synchronized`?

```java
// Collections.synchronizedMap — one global lock, poor throughput
Map<String, String> syncMap = Collections.synchronizedMap(new HashMap<>());
// Every read AND write acquires the same lock → serialize all access

// ConcurrentHashMap — segmented/fine-grained locking
ConcurrentHashMap<String, String> concMap = new ConcurrentHashMap<>();
// Reads are lock-free, writes lock only affected bucket → high concurrency
```

### Concurrent Collection Types

| Collection | Thread-Safe Alternative | Strategy |
|-----------|----------------------|----------|
| HashMap | **ConcurrentHashMap** | CAS + synchronized per bucket |
| ArrayList | **CopyOnWriteArrayList** | Copy array on write |
| HashSet | ConcurrentHashMap.newKeySet() | Backed by CHM |
| LinkedList/Queue | **ConcurrentLinkedQueue** | Lock-free (CAS) |
| PriorityQueue | **PriorityBlockingQueue** | Single lock |
| Deque | **ConcurrentLinkedDeque** | Lock-free (CAS) |
| — | **BlockingQueue** | Producer-consumer pattern |

---

## Internal Working

### ConcurrentHashMap (Java 8+)

```
Structure:
┌─────────────────────────────────────────────────────────┐
│ Node<K,V>[] table (volatile)                            │
│                                                          │
│ Bucket[0]  → Node → Node → ...                         │
│ Bucket[1]  → null                                       │
│ Bucket[2]  → TreeNode (if > 8 nodes, treeified)        │
│ ...                                                      │
│ Bucket[n-1]                                              │
└─────────────────────────────────────────────────────────┘

Read: NO lock! Uses volatile reads
Write: synchronized on the FIRST node of the bucket only
Resize: Concurrent — threads help with transfer

Key differences from HashMap:
- No null keys or values (would be ambiguous with absence)
- Reads never block
- Writes lock only the specific bucket being modified
- Size counting uses LongAdder-style distributed counters
```

### CopyOnWriteArrayList

```
Write Operation:
1. Acquire lock
2. Copy entire internal array to new array
3. Modify the new array
4. Replace old array reference with new array (volatile write)
5. Release lock

Read Operation:
- No lock needed!
- Always reads from current snapshot (old array is still valid)
- Iterators use snapshot at creation time (never throw ConcurrentModificationException)
```

### BlockingQueue Implementations

```
┌─────────────────────────────────────────────────────────────────┐
│ ArrayBlockingQueue  → bounded, single lock, fair option         │
│ LinkedBlockingQueue → optionally bounded, two locks (put/take)  │
│ PriorityBlockingQueue → unbounded, single lock, heap-ordered   │
│ SynchronousQueue → zero capacity, direct handoff               │
│ DelayQueue → elements available only after delay               │
│ LinkedTransferQueue → combines features of all above           │
└─────────────────────────────────────────────────────────────────┘

BlockingQueue Operations:
┌─────────────┬──────────┬───────────┬─────────────┬──────────────┐
│ Operation   │ Throws   │ Returns   │ Blocks      │ Times Out    │
├─────────────┼──────────┼───────────┼─────────────┼──────────────┤
│ Insert      │ add(e)   │ offer(e)  │ put(e)      │ offer(e,t,u) │
│ Remove      │ remove() │ poll()    │ take()      │ poll(t, u)   │
│ Examine     │ element()│ peek()    │ —           │ —            │
└─────────────┴──────────┴───────────┴─────────────┴──────────────┘
```

---

## Diagram

```
ConcurrentHashMap Read vs Write:

Thread A (read):           Thread B (write to bucket 5):
│                          │
├─ volatile read table     ├─ hash key → bucket 5
├─ hash key → bucket 3    ├─ synchronized(bucket[5].head)
├─ traverse bucket 3      │   ├─ insert/update node
│  (no lock needed)        │   └─ unlock
├─ return value            │
│                          │
│  NO CONTENTION! Different buckets, read needs no lock.

Producer-Consumer with BlockingQueue:

Producer ──put()──→ ┌──────────────────┐ ──take()──→ Consumer
Producer ──put()──→ │  BlockingQueue    │ ──take()──→ Consumer
Producer ──put()──→ │  [|||||||||||]    │ ──take()──→ Consumer
                    └──────────────────┘
                     If full: put() blocks
                     If empty: take() blocks
```

---

## Code Examples

### ConcurrentHashMap

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// Basic operations (thread-safe)
map.put("a", 1);
map.get("a");
map.remove("a");

// Atomic compound operations
map.putIfAbsent("key", 0);                    // only if not present
map.computeIfAbsent("key", k -> expensive()); // lazy computation
map.computeIfPresent("key", (k, v) -> v + 1); // update if present
map.merge("key", 1, Integer::sum);             // merge with existing
map.compute("key", (k, v) -> v == null ? 1 : v + 1);  // always compute

// Atomic operations — replaces check-then-act patterns
// BAD (race condition even with CHM):
if (!map.containsKey("key")) {
    map.put("key", computeValue());  // another thread might put between check and put!
}
// GOOD:
map.computeIfAbsent("key", k -> computeValue());  // atomic!

// Bulk operations (parallel, non-blocking)
map.forEach(2, (key, value) -> System.out.println(key + "=" + value));
// parallelism threshold = 2: if size > 2, execute in parallel

long count = map.mappingCount();  // use instead of size() for concurrent access

// Create concurrent Set from CHM
Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
concurrentSet.add("item");
```

### CopyOnWriteArrayList

```java
// Best for: many reads, very few writes (e.g., listener lists, config)
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();

list.add("item");       // copies entire array!
list.get(0);           // fast — no lock
list.set(0, "new");   // copies entire array!

// Iterator is snapshot — never throws ConcurrentModificationException
for (String item : list) {
    list.add("new");  // SAFE! Iterator reads old snapshot
    System.out.println(item);  // sees original items only
}

// Event listener pattern (classic use case)
public class EventBus {
    private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();
    
    public void addListener(EventListener l) { listeners.add(l); }
    public void removeListener(EventListener l) { listeners.remove(l); }
    
    public void fireEvent(Event e) {
        for (EventListener l : listeners) {  // safe iteration, no lock
            l.onEvent(e);
        }
    }
}
```

### BlockingQueue — Producer/Consumer

```java
public class ProducerConsumerExample {
    private final BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);
    
    // Producer
    public void produce(Task task) throws InterruptedException {
        queue.put(task);  // blocks if queue full
    }
    
    // Consumer
    public void consume() throws InterruptedException {
        while (true) {
            Task task = queue.take();  // blocks if queue empty
            process(task);
        }
    }
    
    // With timeout
    public void produceWithTimeout(Task task) throws InterruptedException {
        boolean added = queue.offer(task, 5, TimeUnit.SECONDS);
        if (!added) {
            handleQueueFull(task);
        }
    }
}
```

### SynchronousQueue — Direct Handoff

```java
// Zero capacity — producer blocks until consumer takes
SynchronousQueue<Task> handoff = new SynchronousQueue<>();

// Producer blocks until consumer calls take()
new Thread(() -> {
    handoff.put(new Task());  // blocks until someone takes
}).start();

// Consumer — unblocks producer
Task task = handoff.take();  // unblocks the producer

// Used in CachedThreadPool — task goes directly to thread
```

### ConcurrentLinkedQueue — Lock-Free

```java
ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

// Non-blocking operations
queue.offer("item");   // always succeeds (unbounded)
String item = queue.poll();  // returns null if empty (never blocks)
String peek = queue.peek();  // look without removing

// Good for: high-throughput producer-consumer without blocking
// Bad for: bounded queues, backpressure
```

---

## Dry Run

### ConcurrentHashMap putIfAbsent

```java
ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

// Thread 1 and Thread 2 simultaneously: counters.computeIfAbsent("hits", k -> new AtomicInteger(0))

// Thread 1: hash("hits") → bucket 7 → synchronized(bucket[7])
//   bucket is empty → create new node("hits", AtomicInteger(0))
//   release lock on bucket 7

// Thread 2: hash("hits") → bucket 7 → synchronized(bucket[7])
//   bucket has node("hits") → key exists! return existing AtomicInteger(0)
//   release lock

// Both threads get the SAME AtomicInteger instance
// Then both safely: counters.get("hits").incrementAndGet();
```

### CopyOnWriteArrayList Snapshot

```java
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>(List.of("A", "B", "C"));

// Internal: array = ["A", "B", "C"]

Iterator<String> iter = list.iterator();  // snapshot: ["A", "B", "C"]

list.add("D");  
// New array created: ["A", "B", "C", "D"]
// Old array still exists (iter points to it)

// iter.next() → "A" (from OLD array — snapshot)
// iter.next() → "B"
// iter.next() → "C"
// iter.hasNext() → false (snapshot only has 3 elements!)
// "D" is NOT visible to this iterator
```

---

## Complexity

| Collection | Read | Write | Iterator |
|-----------|------|-------|----------|
| ConcurrentHashMap | O(1) no lock | O(1) bucket lock | Weakly consistent |
| CopyOnWriteArrayList | O(1) no lock | O(n) copies array | Snapshot (safe) |
| ConcurrentLinkedQueue | O(1) CAS | O(1) CAS | Weakly consistent |
| ArrayBlockingQueue | O(1) lock | O(1) lock | Weakly consistent |
| LinkedBlockingQueue | O(1) take lock | O(1) put lock | Weakly consistent |

**Weakly consistent:** Iterator may or may not reflect modifications after creation. Never throws `ConcurrentModificationException`.

---

## Real Project Usage

### Thread-Safe Cache with TTL

```java
public class SimpleCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.getValue();
    }
    
    public void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
    }
    
    // Atomic: get or compute
    public V getOrCompute(K key, Function<K, V> loader) {
        CacheEntry<V> entry = cache.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired()) return existing;
            return new CacheEntry<>(loader.apply(k), System.currentTimeMillis() + ttlMillis);
        });
        return entry.getValue();
    }
}
```

### Work Queue with Multiple Consumers

```java
public class WorkerPool<T> {
    private final BlockingQueue<T> workQueue;
    private final List<Thread> workers;
    private volatile boolean running = true;
    
    public WorkerPool(int numWorkers, int queueCapacity, Consumer<T> processor) {
        this.workQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.workers = new ArrayList<>();
        
        for (int i = 0; i < numWorkers; i++) {
            Thread worker = new Thread(() -> {
                while (running) {
                    try {
                        T item = workQueue.poll(1, TimeUnit.SECONDS);
                        if (item != null) processor.accept(item);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "worker-" + i);
            worker.start();
            workers.add(worker);
        }
    }
    
    public boolean submit(T work) {
        return workQueue.offer(work);
    }
    
    public void shutdown() {
        running = false;
        workers.forEach(Thread::interrupt);
    }
}
```

---

## Interview Questions and Answers

### Q1: How is ConcurrentHashMap different from Hashtable?

**A:**

| ConcurrentHashMap | Hashtable |
|------------------|-----------|
| Locks per bucket | Single lock for entire map |
| Reads are lock-free | Reads also synchronized |
| Allows concurrent reads + writes (different buckets) | Only one thread at a time |
| `null` keys/values NOT allowed | `null` keys/values NOT allowed |
| Modern (Java 5+) | Legacy (Java 1.0) |

ConcurrentHashMap has **much higher throughput** under concurrent access.

### Q2: Why does ConcurrentHashMap not allow null keys/values?

**A:** Ambiguity. In a concurrent map, `map.get(key)` returning `null` could mean:
1. Key doesn't exist, OR
2. Key exists with null value

In a single-threaded HashMap, you can check `containsKey()` after `get()`. In ConcurrentHashMap, the state can change between the two calls — so you can't distinguish the cases.

### Q3: When would you use CopyOnWriteArrayList?

**A:** When:
- Reads vastly outnumber writes (>95% reads)
- Iteration must never fail or see partial state
- Collection is small (since writes copy the entire array)

**Classic use case:** Event listeners, observers, configuration lists that rarely change.

### Q4: What is the difference between `offer()` and `put()` in BlockingQueue?

**A:**
- `put(e)` — **blocks** if queue is full. Waits until space is available.
- `offer(e)` — returns `false` immediately if queue is full. Non-blocking.
- `offer(e, timeout, unit)` — waits up to timeout, then returns false.

Similarly for removal:
- `take()` — blocks if empty
- `poll()` — returns null if empty
- `poll(timeout, unit)` — waits up to timeout

### Q5: What does "weakly consistent" iterator mean?

**A:** The iterator:
- Will never throw `ConcurrentModificationException`
- Reflects some (but not necessarily all) modifications made after creation
- Guaranteed to traverse elements as they existed at some point
- May or may not show updates made during iteration

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Check-then-act with CHM | Race between `containsKey` and `put` | Use `computeIfAbsent` |
| `map.size()` in concurrent code | Approximate, expensive | Use `mappingCount()` or avoid |
| CopyOnWriteArrayList with frequent writes | O(n) per write, GC pressure | Use `ConcurrentLinkedQueue` or synchronized list |
| BlockingQueue without timeout | Thread blocks forever | Use `poll(timeout)` / `offer(timeout)` |
| Iterating + modifying CHM map entries | Values may be stale | Use `compute`/`merge` for atomic updates |
| Assuming `putIfAbsent` + `get` are atomic together | They're not | Use `computeIfAbsent` |

---

## Best Practices

1. **Use `computeIfAbsent`** for atomic get-or-create patterns
2. **Prefer `ConcurrentHashMap`** over `Collections.synchronizedMap`
3. **Use `CopyOnWriteArrayList`** only for small, read-heavy lists
4. **Choose BlockingQueue** for producer-consumer patterns
5. **Set queue bounds** — unbounded queues risk OOM under load
6. **Use `offer` with timeout** instead of `put` for graceful degradation
7. **Never use `null`** in concurrent collections — use `Optional` or sentinel values

---

## Production Considerations

- **Memory:** CopyOnWriteArrayList creates array copies on every write — monitor GC impact
- **Sizing CHM:** Initial capacity and concurrency level matter for performance
- **Queue monitoring:** Track queue depth as a key health metric
- **Backpressure:** Use bounded queues + rejection to prevent OOM under load
- **Iteration:** CHM iterators are safe but may not reflect latest state — acceptable for monitoring, not for transactional reads

---

## Related Topics

- [7. Collection Internals](./07-collection-internals.md) — HashMap internals
- [15. Synchronization](./15-synchronization.md) — why we need concurrent collections
- [17. Executor Framework](./17-executor-framework.md) — uses BlockingQueue internally
