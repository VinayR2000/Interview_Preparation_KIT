# 16. Locks (java.util.concurrent.locks)

---

## Theory

The `java.util.concurrent.locks` package provides more flexible locking mechanisms than `synchronized`. These locks offer features like:

- **Try-lock with timeout** — don't block forever
- **Interruptible lock acquisition** — respond to interrupts while waiting
- **Fairness** — threads acquire lock in order
- **Read/Write separation** — multiple readers, single writer
- **Condition variables** — multiple wait sets per lock

### synchronized vs Lock

| Feature | synchronized | Lock (ReentrantLock) |
|---------|-------------|---------------------|
| Release | Automatic (block exit) | Manual (must call unlock) |
| Try-lock | No | `tryLock()` / `tryLock(timeout)` |
| Fairness | No (always unfair) | Optional (`new ReentrantLock(true)`) |
| Interruptible | No | `lockInterruptibly()` |
| Multiple conditions | One wait set per object | Multiple `Condition` objects |
| Read/Write split | No | `ReadWriteLock` |
| Across methods | No (must be same block) | Yes (lock in one, unlock in another) |

---

## Internal Working

### ReentrantLock Internals

```java
// ReentrantLock uses AbstractQueuedSynchronizer (AQS)
// AQS maintains:
// - state (int): 0 = unlocked, >0 = lock hold count
// - ownerThread: thread that holds the lock
// - CLH queue: FIFO queue of waiting threads

ReentrantLock
└── Sync (extends AQS)
    ├── NonfairSync (default) — barging allowed
    └── FairSync — strict FIFO ordering

// Nonfair: new thread can "steal" lock from waiting threads
// Fair: threads acquire lock in order they requested it
```

### CAS (Compare-And-Swap)

```
Lock acquisition (nonfair):
1. CAS(state, 0 → 1)  // try to acquire
   ├── Success → acquired! set owner = currentThread
   └── Failure → enqueue in CLH queue, park thread (OS sleep)

Reentrant behavior:
- If owner == currentThread → state++ (increment hold count)
- unlock() → state-- (decrement)
- When state reaches 0 → truly unlocked, wake next in queue
```

---

## Diagram

```
Lock Types:

┌──────────────────────────────────────────────────────────────┐
│                       Lock Interface                          │
├──────────────────────────────────────────────────────────────┤
│ void lock()                                                  │
│ void lockInterruptibly() throws InterruptedException         │
│ boolean tryLock()                                            │
│ boolean tryLock(long time, TimeUnit unit)                    │
│ void unlock()                                                │
│ Condition newCondition()                                     │
└──────────────────────────────────────────────────────────────┘
        △                              △
        │                              │
┌───────────────────┐     ┌────────────────────────────┐
│  ReentrantLock    │     │  ReadWriteLock (interface)  │
│  (exclusive)      │     │  readLock() → Lock          │
│                   │     │  writeLock() → Lock          │
└───────────────────┘     └────────────────────────────┘
                                       △
                                       │
                          ┌────────────────────────────┐
                          │  ReentrantReadWriteLock     │
                          │  Multiple readers OR        │
                          │  single writer              │
                          └────────────────────────────┘

StampedLock (Java 8):
┌─────────────────────────────────────────────────────────┐
│ Optimistic Read → try without lock, validate after      │
│ Read Lock → shared (like ReadWriteLock)                  │
│ Write Lock → exclusive                                   │
└─────────────────────────────────────────────────────────┘
```

---

## Code Examples

### ReentrantLock — Basic Usage

```java
public class BankAccount {
    private double balance;
    private final ReentrantLock lock = new ReentrantLock();
    
    public void deposit(double amount) {
        lock.lock();  // acquire lock
        try {
            balance += amount;
        } finally {
            lock.unlock();  // ALWAYS in finally — even on exception
        }
    }
    
    public boolean withdraw(double amount) {
        lock.lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}
```

### tryLock — Non-blocking & Timeout

```java
public class ResourceManager {
    private final ReentrantLock lock = new ReentrantLock();
    
    // Non-blocking: try once, give up if can't acquire
    public boolean tryProcess() {
        if (lock.tryLock()) {
            try {
                process();
                return true;
            } finally {
                lock.unlock();
            }
        } else {
            System.out.println("Resource busy, skipping...");
            return false;
        }
    }
    
    // With timeout: wait up to N seconds
    public boolean tryProcessWithTimeout() throws InterruptedException {
        if (lock.tryLock(5, TimeUnit.SECONDS)) {
            try {
                process();
                return true;
            } finally {
                lock.unlock();
            }
        } else {
            throw new TimeoutException("Could not acquire lock within 5 seconds");
        }
    }
}
```

### Fair Lock

```java
// Fair lock — threads acquire in FIFO order
ReentrantLock fairLock = new ReentrantLock(true);

// Unfair lock (default) — better throughput, possible starvation
ReentrantLock unfairLock = new ReentrantLock(false);

// Fair lock prevents starvation but reduces throughput by ~10-30%
// because it prevents "barging" (cutting in line)
```

### Condition Variables

```java
public class BoundedBuffer<T> {
    private final Queue<T> queue = new LinkedList<>();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();   // producers wait here
    private final Condition notEmpty = lock.newCondition();  // consumers wait here
    
    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }
    
    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                notFull.await();  // wait until not full (releases lock)
            }
            queue.add(item);
            notEmpty.signal();  // wake one consumer
        } finally {
            lock.unlock();
        }
    }
    
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();  // wait until not empty
            }
            T item = queue.poll();
            notFull.signal();  // wake one producer
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

### ReentrantReadWriteLock

```java
public class ThreadSafeCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    // Multiple threads can read simultaneously
    public V get(K key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    // Only one thread can write (and no readers during write)
    public void put(K key, V value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    
    // Read lock count
    public int size() {
        readLock.lock();
        try {
            return cache.size();
        } finally {
            readLock.unlock();
        }
    }
}
```

### StampedLock (Java 8) — Optimistic Reading

```java
public class Point {
    private double x, y;
    private final StampedLock sl = new StampedLock();
    
    public void move(double deltaX, double deltaY) {
        long stamp = sl.writeLock();  // exclusive write lock
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            sl.unlockWrite(stamp);
        }
    }
    
    public double distanceFromOrigin() {
        // Optimistic read — no lock! Just a "stamp" to validate later
        long stamp = sl.tryOptimisticRead();
        double currentX = x;
        double currentY = y;
        
        // Validate: did a write happen while we read?
        if (!sl.validate(stamp)) {
            // Write happened — fall back to pessimistic read
            stamp = sl.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                sl.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }
}
```

---

## Dry Run

### Deadlock Prevention with tryLock

```java
// Without tryLock — deadlock possible:
// Thread 1: lock(A) → lock(B)
// Thread 2: lock(B) → lock(A) → DEADLOCK

// With tryLock — deadlock impossible:
public boolean transferMoney(Account from, Account to, double amount) {
    while (true) {
        if (from.lock.tryLock()) {
            try {
                if (to.lock.tryLock()) {
                    try {
                        from.debit(amount);
                        to.credit(amount);
                        return true;
                    } finally {
                        to.lock.unlock();
                    }
                }
            } finally {
                from.lock.unlock();
            }
        }
        // Both locks not acquired — back off and retry
        Thread.sleep(random.nextInt(10));  // prevent livelock
    }
}
```

### ReadWriteLock Concurrency

```java
// Scenario: 10 readers, 1 writer

// With synchronized: all serialize (1 at a time)
// Throughput: 1x

// With ReadWriteLock:
// Readers:  [R1][R2][R3][R4][R5] all concurrent → 10x read throughput
// Writer:   ────────────────────[W1]──── exclusive (blocks all readers)
// After W1: [R6][R7][R8][R9][R10] concurrent again

// Rule: Many readers OR one writer (never both)
```

---

## Complexity

| Lock Type | Uncontended | Contended | Fairness |
|-----------|-------------|-----------|----------|
| `synchronized` | ~20ns (biased) | μs-ms (heavyweight) | Unfair |
| `ReentrantLock` (unfair) | ~30ns | μs (spin then park) | Unfair |
| `ReentrantLock` (fair) | ~30ns | μs-ms (strict FIFO) | Fair |
| `ReadWriteLock` (read) | ~40ns | Near-zero if no writer | Configurable |
| `StampedLock` (optimistic) | ~5ns | Retry cost if invalid | N/A |

---

## Real Project Usage

### Rate Limiter

```java
public class RateLimiter {
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxRequests;
    private final long windowMs;
    private final Queue<Long> timestamps = new LinkedList<>();
    
    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }
    
    public boolean tryAcquire() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            while (!timestamps.isEmpty() && now - timestamps.peek() > windowMs) {
                timestamps.poll();
            }
            if (timestamps.size() < maxRequests) {
                timestamps.add(now);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}
```

### Lock-Protected Lazy Initialization

```java
public class ExpensiveResource {
    private volatile Object resource;  // volatile for double-check
    private final ReentrantLock initLock = new ReentrantLock();
    
    public Object getResource() {
        if (resource == null) {
            initLock.lock();
            try {
                if (resource == null) {
                    resource = createExpensiveResource();
                }
            } finally {
                initLock.unlock();
            }
        }
        return resource;
    }
}
```

---

## Interview Questions and Answers

### Q1: Why use ReentrantLock over synchronized?

**A:** Use `ReentrantLock` when you need:
1. `tryLock()` — non-blocking lock acquisition
2. `tryLock(timeout)` — timed waiting
3. `lockInterruptibly()` — respond to interrupts while waiting
4. Fairness guarantee
5. Multiple condition variables
6. Lock in one method, unlock in another

**Use `synchronized`** when none of these are needed — it's simpler and the JVM can optimize it better.

### Q2: What does "reentrant" mean?

**A:** A reentrant lock can be acquired multiple times by the same thread without deadlocking:

```java
ReentrantLock lock = new ReentrantLock();
lock.lock();      // hold count = 1
lock.lock();      // hold count = 2 (same thread — allowed!)
lock.unlock();    // hold count = 1
lock.unlock();    // hold count = 0 (truly released)
```

`synchronized` is also reentrant — a thread can enter multiple synchronized methods on the same object.

### Q3: What is the difference between ReentrantReadWriteLock and StampedLock?

**A:**

| ReentrantReadWriteLock | StampedLock |
|----------------------|-------------|
| Reentrant | NOT reentrant |
| Fair option available | No fairness |
| No optimistic read | Has optimistic read (no lock!) |
| Supports Conditions | No Conditions |
| Read → Write upgrade: NO | Write → Read downgrade: YES |
| Safer (simpler API) | Higher performance (optimistic) |

### Q4: What happens if you forget to call `unlock()`?

**A:** The lock is held forever. Other threads will block permanently waiting for it. This is effectively a **deadlock** with a single lock. Always use try-finally:

```java
lock.lock();
try {
    // work
} finally {
    lock.unlock();  // ALWAYS in finally
}
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Not using `finally` for `unlock()` | Lock never released on exception | Always `try/finally` |
| Calling `unlock()` without `lock()` | `IllegalMonitorStateException` | Ensure paired calls |
| Using `StampedLock` reentrantly | Deadlock with itself | Use ReentrantLock if reentrance needed |
| Fair lock for everything | 10-30% throughput loss | Use fair only when starvation is a problem |
| Read lock → Write lock upgrade | Deadlock! (RW lock) | Release read, acquire write |
| Locking across `await()` returns | Must re-check condition | Always use `while` loop |

---

## Best Practices

1. **Always unlock in `finally`** — never risk leaking locks
2. **Prefer `synchronized`** unless you need Lock-specific features
3. **Use ReadWriteLock for read-heavy** workloads (>90% reads)
4. **Use StampedLock** for read-heavy with maximum performance needs
5. **Avoid fair locks** unless starvation is proven — they reduce throughput
6. **Minimize lock scope** — hold locks for the shortest time possible
7. **Document locking strategy** — which locks protect which state
8. **Use `tryLock`** to avoid deadlocks in multi-lock scenarios

---

## Production Considerations

- **Contention monitoring:** Track `lock.getQueueLength()` and `lock.hasQueuedThreads()` in metrics
- **Deadlock detection:** Not automatic with explicit locks (unlike synchronized). Use `ThreadMXBean`
- **Lock striping:** For collections, use multiple locks for different segments (like ConcurrentHashMap)
- **Lock-free alternatives:** Consider `AtomicReference`, `LongAdder` for simple operations
- **StampedLock caveat:** Not reentrant, no conditions — use only for simple read/write patterns

---

## Related Topics

- [15. Synchronization](./15-synchronization.md) — synchronized and volatile
- [17. Executor Framework](./17-executor-framework.md) — thread pools
- [19. Concurrent Collections](./19-concurrent-collections.md) — collections with built-in locking
- [24. Java Memory Model](./24-java-memory-model.md) — visibility and ordering guarantees
