# 15. Synchronization

---

## Theory

Synchronization is the mechanism that ensures only one thread accesses a critical section at a time, preventing **race conditions** and ensuring **data consistency**.

### Key Concepts

- **Race Condition** — when multiple threads access shared data concurrently and at least one modifies it, producing incorrect results
- **Critical Section** — code that accesses shared resources and must be executed atomically
- **Mutual Exclusion (Mutex)** — only one thread can enter the critical section at a time
- **Monitor** — Java's built-in synchronization mechanism (every object has one)
- **Intrinsic Lock (Monitor Lock)** — the lock associated with every Java object

### The Three Problems of Concurrency

1. **Atomicity** — operations execute as indivisible units (`synchronized`, `Atomic*`)
2. **Visibility** — changes by one thread are seen by other threads (`volatile`, `synchronized`)
3. **Ordering** — instructions execute in expected order (happens-before, `volatile`)

---

## Internal Working

### Java Object Monitor

Every Java object has an associated monitor (intrinsic lock):

```
Object Header (in memory):
┌─────────────────────────────────────────────────────┐
│ Mark Word (64 bits on 64-bit JVM)                   │
├─────────────────────────────────────────────────────┤
│ [hashCode | age | biased_lock | lock_state]         │
│                                                      │
│ Lock states:                                         │
│   01 → unlocked (biasable)                          │
│   00 → lightweight locked (stack pointer)            │
│   10 → heavyweight locked (monitor pointer)          │
│   11 → marked for GC                                │
└─────────────────────────────────────────────────────┘

Monitor Structure:
┌─────────────────────────────────────┐
│ Owner Thread: Thread-1              │
│ Entry Count: 1 (reentrant)          │
│ Wait Set: [Thread-3, Thread-4]      │
│ Entry Set: [Thread-2, Thread-5]     │
└─────────────────────────────────────┘
```

### Lock Escalation (JVM Optimization)

```
No Contention → Biased Lock (no atomic ops)
     ↓ (another thread tries to acquire)
Light Contention → Lightweight Lock (CAS spin)
     ↓ (spin fails repeatedly)
Heavy Contention → Heavyweight Lock (OS mutex, thread parking)
```

---

## Diagram

```
synchronized Method Execution:

Thread-1                          Thread-2
   │                                 │
   ├── acquire lock ─────────────┐   │
   │   ┌─────────────────────┐  │   ├── try acquire lock
   │   │ CRITICAL SECTION    │  │   │   BLOCKED! (waiting)
   │   │ read/write shared   │  │   │   ...
   │   │ data safely         │  │   │   ...
   │   └─────────────────────┘  │   │   ...
   ├── release lock ─────────────┘   │
   │                                 ├── acquire lock (got it!)
   │                                 │   ┌─────────────────┐
   │                                 │   │ CRITICAL SECTION│
   │                                 │   └─────────────────┘
   │                                 ├── release lock

volatile vs synchronized:
┌────────────────────────────────────────────────────────────┐
│ volatile:                                                   │
│   ✓ Visibility (reads see latest write)                    │
│   ✓ Ordering (prevents reordering)                         │
│   ✗ Atomicity (only for single read/write)                 │
│                                                             │
│ synchronized:                                               │
│   ✓ Visibility                                             │
│   ✓ Ordering                                               │
│   ✓ Atomicity (entire block is atomic)                     │
└────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### synchronized Keyword

```java
// Method-level synchronization (locks 'this')
public class Counter {
    private int count = 0;
    
    public synchronized void increment() {
        count++;  // now atomic — only one thread at a time
    }
    
    public synchronized int getCount() {
        return count;
    }
}

// Static synchronized (locks Class object)
public class Singleton {
    private static Singleton instance;
    
    public static synchronized Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}

// Block-level synchronization (fine-grained — better performance)
public class BankAccount {
    private double balance;
    private final Object lock = new Object();  // dedicated lock object
    
    public void deposit(double amount) {
        synchronized (lock) {
            balance += amount;
        }
    }
    
    public void withdraw(double amount) {
        synchronized (lock) {
            if (balance >= amount) {
                balance -= amount;
            }
        }
    }
    
    public double getBalance() {
        synchronized (lock) {
            return balance;
        }
    }
}
```

### volatile Keyword

```java
// Visibility guarantee — all threads see latest value
public class VolatileFlag {
    private volatile boolean running = true;  // volatile ensures visibility
    
    public void run() {
        while (running) {  // reads always see latest write
            doWork();
        }
    }
    
    public void stop() {
        running = false;  // immediately visible to other threads
    }
}

// WITHOUT volatile:
// The running thread might NEVER see running=false
// because it reads from CPU cache (stale value)

// volatile is NOT enough for compound operations:
private volatile int count = 0;
count++;  // NOT ATOMIC! (read + increment + write) — still a race condition!
```

### Atomic Classes

```java
import java.util.concurrent.atomic.*;

// AtomicInteger — lock-free thread-safe integer
AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();      // atomic i++, returns new value
counter.getAndIncrement();      // atomic i++, returns old value
counter.compareAndSet(5, 10);   // CAS: if current==5, set to 10
counter.addAndGet(5);           // atomic +=5
counter.updateAndGet(x -> x * 2);  // atomic transform

// AtomicReference — lock-free reference swap
AtomicReference<String> ref = new AtomicReference<>("initial");
ref.compareAndSet("initial", "updated");

// AtomicBoolean
AtomicBoolean flag = new AtomicBoolean(false);
flag.compareAndSet(false, true);  // only one thread succeeds

// LongAdder — better than AtomicLong for high contention
LongAdder adder = new LongAdder();
adder.increment();     // internally uses striped cells
adder.sum();           // get total (approximate during contention)
```

### Double-Checked Locking

```java
public class Singleton {
    private static volatile Singleton instance;  // volatile required!
    
    public static Singleton getInstance() {
        if (instance == null) {                    // first check (no lock)
            synchronized (Singleton.class) {
                if (instance == null) {            // second check (with lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}

// Why volatile is needed:
// Without volatile, instance = new Singleton() can be reordered:
// 1. Allocate memory
// 2. Assign reference to instance  ← another thread sees non-null but uninitialized!
// 3. Call constructor
// volatile prevents this reordering
```

### Deadlock Example and Prevention

```java
// DEADLOCK — two threads acquiring locks in different order
public class DeadlockDemo {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    public void method1() {
        synchronized (lock1) {         // Thread-1 holds lock1
            synchronized (lock2) {     // Thread-1 wants lock2 → BLOCKED
                // work
            }
        }
    }
    
    public void method2() {
        synchronized (lock2) {         // Thread-2 holds lock2
            synchronized (lock1) {     // Thread-2 wants lock1 → BLOCKED
                // work                // DEADLOCK!
            }
        }
    }
}

// FIX: Always acquire locks in consistent order
public void method1() {
    synchronized (lock1) {
        synchronized (lock2) { /* work */ }
    }
}

public void method2() {
    synchronized (lock1) {   // same order as method1!
        synchronized (lock2) { /* work */ }
    }
}
```

---

## Dry Run

### Race Condition

```java
// Shared state
int balance = 1000;

// Thread 1: withdraw(800)         Thread 2: withdraw(800)
// T1: read balance → 1000        
//                                 T2: read balance → 1000
// T1: 1000 >= 800? yes           
//                                 T2: 1000 >= 800? yes
// T1: balance = 1000 - 800 = 200
//                                 T2: balance = 1000 - 800 = 200
// Final: balance = 200 (should have rejected one withdrawal!)
// Both withdrawals succeeded — account overdrafted!

// With synchronized:
// T1: acquire lock → read 1000 → 1000>=800 → balance=200 → release lock
// T2: acquire lock → read 200 → 200>=800? NO → release lock
// Correct! Second withdrawal rejected.
```

### Happens-Before Relationship

```java
// Happens-before guarantees ordering:

// 1. synchronized block:
synchronized(lock) {
    x = 1;          // happens-before any subsequent lock acquire
}

// 2. volatile write → volatile read:
volatile boolean ready = false;
// Thread 1:
data = 42;         // regular write
ready = true;      // volatile write (publishes data too!)

// Thread 2:
if (ready) {       // volatile read
    print(data);   // guaranteed to see 42!
}

// 3. Thread start:
x = 10;
thread.start();    // x=10 happens-before anything in thread

// 4. Thread join:
// everything in thread happens-before code after join()
thread.join();
print(x);  // sees all writes from thread
```

---

## Complexity

| Mechanism | Cost | Contention Impact |
|-----------|------|-------------------|
| `volatile` read/write | ~few ns | No blocking, just memory barrier |
| `synchronized` (uncontended) | ~20-50ns | Biased lock — near zero |
| `synchronized` (contended) | ~μs to ms | Thread parking, context switch |
| `AtomicInteger` CAS | ~5-10ns uncontended | Spin loop under contention |
| `LongAdder` | ~distributed | Best for high-contention counters |

---

## Real Project Usage

### Thread-Safe Cache

```java
public class SimpleCache<K, V> {
    private final Map<K, V> cache = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public V get(K key) {
        rwLock.readLock().lock();
        try {
            return cache.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        rwLock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

### Thread-Safe Singleton (Enum)

```java
// Best singleton pattern — enum (inherently thread-safe)
public enum DatabaseConnection {
    INSTANCE;
    
    private final Connection connection;
    
    DatabaseConnection() {
        connection = createConnection();
    }
    
    public Connection getConnection() {
        return connection;
    }
}
```

### Publishing Immutable Objects

```java
// Immutable objects are inherently thread-safe
public final class ImmutableConfig {
    private final String host;
    private final int port;
    private final List<String> endpoints;
    
    public ImmutableConfig(String host, int port, List<String> endpoints) {
        this.host = host;
        this.port = port;
        this.endpoints = List.copyOf(endpoints);  // defensive copy
    }
    
    // Only getters — no synchronization needed!
    public String getHost() { return host; }
    public int getPort() { return port; }
    public List<String> getEndpoints() { return endpoints; }
}
```

---

## Interview Questions and Answers

### Q1: What is the difference between `synchronized` and `volatile`?

**A:**

| `synchronized` | `volatile` |
|---------------|-----------|
| Provides atomicity + visibility + ordering | Provides visibility + ordering only |
| Blocks other threads | Never blocks |
| Can protect compound operations | Only for single read/write |
| Has performance cost (locking) | Lighter (memory barrier only) |
| Works on blocks/methods | Works on variables only |

**Use `volatile`** for simple flags/state visible across threads.
**Use `synchronized`** for compound operations (check-then-act, read-modify-write).

### Q2: What is a deadlock? How to prevent it?

**A:** Deadlock occurs when two or more threads are blocked forever, each waiting for a lock held by another.

**Four necessary conditions (Coffman):**
1. Mutual exclusion
2. Hold and wait
3. No preemption
4. Circular wait

**Prevention:**
1. **Lock ordering** — always acquire locks in the same global order
2. **Lock timeout** — use `tryLock(timeout)` with `ReentrantLock`
3. **Single lock** — use one lock for all shared resources (simpler but less concurrent)
4. **Avoid nested locks** — minimize lock scope

### Q3: What is the difference between `notify()` and `notifyAll()`?

**A:**
- `notify()` — wakes up ONE waiting thread (arbitrary). Other threads stay waiting.
- `notifyAll()` — wakes up ALL waiting threads. They compete for the lock.

**Always prefer `notifyAll()`** — `notify()` can cause missed signals if the wrong thread is woken.

### Q4: Can two threads enter two different synchronized methods of the same object simultaneously?

**A:** **No.** Both synchronized instance methods use the same intrinsic lock (`this`). Only one thread can hold `this` lock at a time, so only one synchronized method executes at a time on the same instance.

```java
// Thread-1 in methodA locks 'this'
// Thread-2 cannot enter methodB on same instance — also needs 'this' lock

// FIX: Use different lock objects for independent operations
private final Object lockA = new Object();
private final Object lockB = new Object();
```

### Q5: What is a race condition vs data race?

**A:**
- **Race condition** — program correctness depends on thread timing (logical bug)
- **Data race** — concurrent unsynchronized access to shared variable where at least one is a write (memory model violation)

A race condition can exist WITHOUT a data race (using synchronized but with wrong logic), and a data race can exist without visible race condition (but is still undefined behavior).

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Synchronizing on `this` in all methods | Blocks ALL methods, poor performance | Use fine-grained locks |
| Synchronizing on mutable field | Lock object changes, threads use different locks | Use `final` lock objects |
| `volatile` for compound operations | Still has race conditions | Use `synchronized` or `Atomic*` |
| Not releasing lock in finally | Lock held forever on exception | Always use try/finally |
| Using `notify()` | May wake wrong thread | Use `notifyAll()` |
| Synchronizing on String literal | All code sharing that literal shares the lock! | Use `new Object()` |
| Synchronizing on boxed Integer | Integer cache returns shared instances | Use `new Object()` |

---

## Best Practices

1. **Minimize lock scope** — lock only the critical section, not entire methods
2. **Use `final` lock objects** — `private final Object lock = new Object()`
3. **Prefer concurrent utilities** — `ConcurrentHashMap`, `AtomicInteger` over manual sync
4. **Immutability** — immutable objects need no synchronization
5. **Lock ordering** — establish and document global lock order
6. **Avoid nested locks** — if unavoidable, always same order
7. **Use `volatile` for flags** — simple boolean signals between threads
8. **Document thread safety** — use `@ThreadSafe`, `@NotThreadSafe`, `@GuardedBy`

---

## Production Considerations

- **Lock contention:** High contention = poor scalability. Use `LongAdder` over `AtomicLong`, `ConcurrentHashMap` over `synchronized HashMap`
- **Deadlock detection:** Use `jstack` or `ThreadMXBean.findDeadlockedThreads()`
- **Starvation:** Fair locks (`new ReentrantLock(true)`) prevent starvation but reduce throughput
- **Lock-free algorithms:** CAS-based (Atomic classes) — better for high contention but harder to reason about
- **Monitoring:** Track lock wait times, contention rates in production

---

## Related Topics

- [14. Multithreading Fundamentals](./14-multithreading-fundamentals.md) — thread basics
- [16. Locks](./16-locks.md) — ReentrantLock, ReadWriteLock
- [19. Concurrent Collections](./19-concurrent-collections.md) — thread-safe collections
- [24. Java Memory Model](./24-java-memory-model.md) — happens-before, visibility
