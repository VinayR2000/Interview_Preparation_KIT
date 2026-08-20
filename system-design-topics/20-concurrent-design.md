# Concurrent Design

## Thread Safety Fundamentals

### Theory
- Thread safety: Code behaves correctly when accessed from multiple threads simultaneously
- Race condition: Output depends on timing of thread execution
- Critical section: Code that accesses shared mutable state
- Happens-before: Memory visibility guarantee in Java Memory Model

### When is Code NOT Thread-Safe?
```java
// Race condition: check-then-act
public class UnsafeCounter {
    private int count = 0;  // Shared mutable state
    
    public void increment() {
        count++;  // NOT atomic: read → increment → write (3 operations)
    }
    // Two threads: both read 5, both write 6. Expected: 7
}

// Race condition: compound action
public class UnsafeCache {
    private Map<String, Object> cache = new HashMap<>();  // Not thread-safe!
    
    public Object get(String key) {
        if (!cache.containsKey(key)) {    // Check
            cache.put(key, loadFromDB(key)); // Act — another thread may have added between check and act
        }
        return cache.get(key);
    }
}
```

---

## synchronized

### Theory
- Java's built-in mutual exclusion mechanism
- Acquires intrinsic lock (monitor) of an object
- Only one thread can hold the lock at a time
- Provides both mutual exclusion AND memory visibility

### Code
```java
public class ThreadSafeCounter {
    private int count = 0;
    
    // Method-level synchronization
    public synchronized void increment() {
        count++; // Only one thread at a time
    }
    
    public synchronized int getCount() {
        return count;
    }
}

// Block-level (finer granularity)
public class BankAccount {
    private final Object lock = new Object(); // Dedicated lock object
    private double balance;
    
    public void transfer(BankAccount to, double amount) {
        // Lock ordering to prevent deadlock
        Object firstLock = System.identityHashCode(this) < System.identityHashCode(to) 
            ? this.lock : to.lock;
        Object secondLock = firstLock == this.lock ? to.lock : this.lock;
        
        synchronized (firstLock) {
            synchronized (secondLock) {
                if (this.balance >= amount) {
                    this.balance -= amount;
                    to.balance += amount;
                }
            }
        }
    }
}
```

### Problems with synchronized
| Problem | Description |
|---------|-------------|
| Coarse locking | Entire method locked, reduces concurrency |
| No timeout | Thread waits indefinitely |
| No interruptibility | Can't cancel waiting thread |
| No fairness | No guarantee of FIFO waiting |
| Deadlock prone | Lock ordering mistakes |

---

## Lock (java.util.concurrent.locks)

### ReentrantLock
```java
public class TicketBookingService {
    private final ReentrantLock lock = new ReentrantLock(true); // fair=true
    private final Map<String, SeatStatus> seats;
    
    public boolean bookSeat(String seatId, String userId) {
        // Try with timeout — don't wait forever
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                return false; // Couldn't get lock, fail gracefully
            }
            
            if (seats.get(seatId) != SeatStatus.AVAILABLE) {
                return false;
            }
            seats.put(seatId, SeatStatus.BOOKED);
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (acquired) lock.unlock(); // Always unlock in finally!
        }
    }
}
```

### ReadWriteLock
```java
public class ConcurrentCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    public V get(K key) {
        readLock.lock(); // Multiple readers can hold simultaneously
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    public void put(K key, V value) {
        writeLock.lock(); // Exclusive — no readers or writers
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
}
```

### Lock vs synchronized

| Aspect | synchronized | Lock (ReentrantLock) |
|--------|-------------|---------------------|
| Syntax | Keyword (implicit) | API (explicit lock/unlock) |
| Timeout | No | Yes (tryLock with timeout) |
| Interruptible | No | Yes (lockInterruptibly) |
| Fairness | No control | Configurable |
| Condition variables | One per object (wait/notify) | Multiple (newCondition) |
| Scope | Block/method | Flexible (can span methods) |
| Performance | Similar (modern JVM) | Similar |
| Safety | Auto-released | Must unlock in finally |

---

## Atomic Variables

### Theory
- Lock-free thread safety using CAS (Compare-And-Swap)
- Hardware-level atomic operations
- No blocking — better throughput under contention
- Types: AtomicInteger, AtomicLong, AtomicReference, AtomicBoolean

### Code
```java
public class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet(); // Atomic — CAS loop internally
    }
    
    public int getCount() {
        return count.get();
    }
}

// Compare-and-swap pattern
public class LockFreeStack<T> {
    private final AtomicReference<Node<T>> top = new AtomicReference<>(null);
    
    public void push(T value) {
        Node<T> newNode = new Node<>(value);
        Node<T> currentTop;
        do {
            currentTop = top.get();
            newNode.next = currentTop;
        } while (!top.compareAndSet(currentTop, newNode)); // CAS: retry if changed
    }
    
    public T pop() {
        Node<T> currentTop;
        Node<T> newTop;
        do {
            currentTop = top.get();
            if (currentTop == null) return null;
            newTop = currentTop.next;
        } while (!top.compareAndSet(currentTop, newTop));
        return currentTop.value;
    }
}
```

---

## ConcurrentHashMap

### Theory
- Thread-safe HashMap without locking entire map
- Segment-based locking (Java 7) → Node-based CAS (Java 8+)
- Allows concurrent reads and writes to different keys
- Does NOT allow null keys or values

### Code
```java
// Common thread-safe patterns
ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

// Atomic compute
counters.computeIfAbsent("page-views", k -> new AtomicLong()).incrementAndGet();

// Atomic update
counters.compute("api-calls", (key, current) -> {
    if (current == null) return new AtomicLong(1);
    current.incrementAndGet();
    return current;
});

// Bulk operations (Java 8+)
counters.forEachEntry(2, entry -> System.out.println(entry)); // Parallelism threshold: 2
long total = counters.reduceValuesToLong(1, AtomicLong::get, 0L, Long::sum);
```

---

## Producer/Consumer Pattern

### Code
```java
public class BoundedBuffer<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    
    public void produce(T item) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                notFull.await(); // Wait until space available
            }
            queue.add(item);
            notEmpty.signal(); // Wake up consumer
        } finally {
            lock.unlock();
        }
    }
    
    public T consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await(); // Wait until item available
            }
            T item = queue.poll();
            notFull.signal(); // Wake up producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}

// Simpler: Use BlockingQueue
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);

// Producer thread
queue.put(task); // Blocks if full

// Consumer thread
Task task = queue.take(); // Blocks if empty
```

---

## ExecutorService and Thread Pools

### Code
```java
// Fixed thread pool
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);

// Submit tasks
Future<PaymentResult> future = executor.submit(() -> {
    return paymentGateway.charge(request);
});

// Custom thread pool (production)
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10,                          // core pool size
    50,                          // max pool size
    60L, TimeUnit.SECONDS,       // idle thread keep-alive
    new LinkedBlockingQueue<>(1000), // work queue
    new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
);
```

### Thread Pool Sizing
```
CPU-bound tasks: threads = CPU cores + 1
I/O-bound tasks: threads = CPU cores × (1 + wait_time/compute_time)

Example: 8 cores, task waits 80% of time (I/O):
  threads = 8 × (1 + 0.8/0.2) = 8 × 5 = 40 threads
```

---

## CompletableFuture

### Code
```java
// Async pipeline
public CompletableFuture<OrderResult> processOrder(OrderRequest request) {
    return CompletableFuture
        .supplyAsync(() -> validateOrder(request))
        .thenCompose(order -> reserveInventory(order))       // Sequential
        .thenCompose(order -> processPayment(order))          // Sequential
        .thenApplyAsync(order -> sendConfirmation(order))     // Parallel
        .exceptionally(ex -> handleFailure(ex, request));
}

// Parallel operations
CompletableFuture<Price> priceFuture = CompletableFuture.supplyAsync(() -> getPrice(item));
CompletableFuture<Stock> stockFuture = CompletableFuture.supplyAsync(() -> getStock(item));
CompletableFuture<Reviews> reviewsFuture = CompletableFuture.supplyAsync(() -> getReviews(item));

CompletableFuture.allOf(priceFuture, stockFuture, reviewsFuture)
    .thenAccept(v -> {
        ProductDetails details = new ProductDetails(
            priceFuture.join(), stockFuture.join(), reviewsFuture.join()
        );
        return details;
    });
```

---

## Thread-Safe Design Patterns

### Immutable Objects
```java
// Immutable = inherently thread-safe (no shared mutable state)
public final class Money {
    private final BigDecimal amount;
    private final Currency currency;
    
    public Money(BigDecimal amount, Currency currency) {
        this.amount = amount;
        this.currency = currency;
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new CurrencyMismatchException();
        return new Money(this.amount.add(other.amount), this.currency); // New object
    }
    
    // No setters — immutable
}
```

### Thread-Local Storage
```java
// Each thread has its own copy — no sharing, no locking
public class RequestContext {
    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();
    
    private String userId;
    private String traceId;
    
    public static void set(RequestContext ctx) { CONTEXT.set(ctx); }
    public static RequestContext get() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); } // Important! Prevent memory leaks
}
```

---

## Interview Questions

**Q: How do you make a class thread-safe?**
> Options (ranked by preference):
> 1. Make it immutable (no shared mutable state)
> 2. Use atomic variables for simple operations
> 3. Use ConcurrentHashMap/CopyOnWriteArrayList for collections
> 4. Use synchronized/Lock for complex compound operations
> 5. Use ThreadLocal for per-thread state
> Choose based on: access pattern, contention level, complexity.

**Q: How would you implement a thread-safe Singleton?**
> Best approaches:
> 1. Enum singleton (simplest, handles serialization)
> 2. Static inner class holder (lazy, thread-safe via class loading)
> 3. Avoid manual singletons — use DI framework

**Q: What causes deadlock? How do you prevent it?**
> Deadlock requires ALL four conditions: 1) Mutual exclusion, 2) Hold and wait, 3) No preemption, 4) Circular wait.
> Prevention: Break any one condition. Most practical: consistent lock ordering (always acquire locks in same order), use tryLock with timeout, minimize lock scope, prefer immutable objects.

**Q: AtomicInteger vs synchronized — when to use which?**
> AtomicInteger: Simple atomic operations (increment, CAS), high contention (lock-free = better throughput), single variable.
> synchronized: Complex compound operations (check-then-act on multiple variables), need to protect a block of code, multiple variables that change together atomically.

**Q: How would you design a thread-safe bounded queue (producer-consumer)?**
> Use ReentrantLock with two Conditions (notFull, notEmpty). Producer: acquire lock, wait on notFull if at capacity, add item, signal notEmpty. Consumer: acquire lock, wait on notEmpty if empty, remove item, signal notFull. Or simply use `LinkedBlockingQueue` which does all this internally.

---

## Common Mistakes
- Not releasing locks in finally block (resource leak on exception)
- Using synchronized on non-final fields (lock object can change!)
- Double-checked locking without volatile (broken before Java 5)
- Not handling InterruptedException properly (restore interrupt flag)
- Using ThreadLocal without clearing (memory leaks in thread pools)
- Synchronizing on `this` in public classes (external code can lock same monitor)

---

## Best Practices
- Prefer immutability over synchronization
- Use higher-level concurrency utilities (Executor, BlockingQueue, ConcurrentMap)
- Keep synchronized blocks as small as possible
- Use lock ordering consistently to prevent deadlock
- Use tryLock with timeout to detect potential deadlocks
- Don't call alien methods while holding a lock
- Document thread-safety guarantees (@ThreadSafe annotation)

---

## Production Considerations
- Thread pool sizing based on workload type (CPU vs I/O bound)
- Monitor thread pool: queue size, rejection rate, active threads
- Set thread names for debugging (new ThreadFactory with meaningful names)
- Use structured concurrency (Java 21+ virtual threads)
- Implement graceful shutdown (ExecutorService.shutdown + awaitTermination)
- Detect deadlocks: JMX ThreadMXBean, thread dumps, monitoring tools

---

## Related Topics
- Java Memory Model
- Virtual Threads (Project Loom)
- Reactive Programming
- Distributed Locks (Redis, ZooKeeper)
