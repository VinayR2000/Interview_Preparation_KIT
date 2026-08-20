# 24. Java Memory Model (JMM)

## Theory

The Java Memory Model (JMM) defines how threads interact through memory and what behaviors are allowed in concurrent programs. It specifies:
- How and when changes made by one thread become visible to other threads
- What reorderings of instructions are permitted by the compiler, JVM, and hardware
- The rules that guarantee memory consistency in multithreaded programs

The JMM exists because modern CPUs use caches, write buffers, and out-of-order execution that can cause threads to see stale or inconsistent data without proper synchronization.

### Key Concepts

**Visibility** — When one thread modifies a shared variable, other threads may not immediately see the change because each thread may work with its own cached copy.

**Atomicity** — An operation is atomic if it completes entirely without any other thread observing an intermediate state. Reading/writing a reference or primitive (except long/double without volatile) is atomic, but compound operations like `i++` are NOT atomic.

**Ordering** — The compiler and CPU can reorder instructions for optimization. The JMM defines rules about which reorderings are permitted.

**Happens-Before Relationship** — The fundamental concept of the JMM. If action A "happens-before" action B, then:
- A's results are guaranteed to be visible to B
- A is ordered before B from memory visibility perspective

---

## Internal Working

### Memory Architecture in JMM

```
Thread 1              Thread 2              Thread 3
┌──────────┐         ┌──────────┐         ┌──────────┐
│ Working  │         │ Working  │         │ Working  │
│ Memory   │         │ Memory   │         │ Memory   │
│ (Cache)  │         │ (Cache)  │         │ (Cache)  │
└────┬─────┘         └────┬─────┘         └────┬─────┘
     │                     │                     │
     └─────────────────────┼─────────────────────┘
                           │
                    ┌──────┴──────┐
                    │ Main Memory │
                    │ (Heap)      │
                    └─────────────┘
```

Each thread has its own **working memory** (conceptual — maps to CPU caches). Variables are read from and written to main memory, but threads operate on local copies.

### Happens-Before Rules

1. **Program Order Rule**: Each action in a thread happens-before every subsequent action in that thread.

2. **Monitor Lock Rule**: An unlock on a monitor happens-before every subsequent lock on that same monitor.

3. **Volatile Variable Rule**: A write to a volatile field happens-before every subsequent read of that same field.

4. **Thread Start Rule**: A call to `Thread.start()` happens-before any action in the started thread.

5. **Thread Termination Rule**: Any action in a thread happens-before any other thread detects that thread has terminated (via `Thread.join()` or `Thread.isAlive()`).

6. **Interruption Rule**: A thread calling `interrupt()` on another thread happens-before the interrupted thread detects the interrupt.

7. **Finalizer Rule**: The end of a constructor happens-before the start of the finalizer for that object.

8. **Transitivity**: If A happens-before B, and B happens-before C, then A happens-before C.

### How volatile Works

```
Thread 1 writes volatile variable x
    ↓
All variables written BEFORE the volatile write
are flushed to main memory
    ↓
Thread 2 reads volatile variable x
    ↓
Thread 2's working memory is invalidated
    ↓
Thread 2 re-reads ALL variables from main memory
```

Volatile provides:
- **Visibility guarantee**: Changes are immediately visible to all threads
- **Ordering guarantee**: Prevents reordering of instructions around the volatile access
- Does NOT provide atomicity for compound operations

### How synchronized Works (Memory Effects)

```
Thread acquires lock (monitor enter)
    ↓
Working memory is invalidated
(forces re-read from main memory)
    ↓
Execute critical section
    ↓
Thread releases lock (monitor exit)
    ↓
All changes flushed to main memory
```

---

## Diagram

### Visibility Problem Without Synchronization

```
Thread 1                          Thread 2
─────────                         ─────────
flag = true  (writes to cache)    while (!flag) { }  ← may NEVER see true!
                                  // reads from its own cache
                                  // which still has flag = false

Main Memory: flag = false → eventually flag = true
             (but Thread 2 may never refresh its cache)
```

### Volatile Fixes Visibility

```
Thread 1                          Thread 2
─────────                         ─────────
volatile flag = true              while (!flag) { }  ← guaranteed to see true
  ↓ flushes to main memory          ↑ reads from main memory every time
```

### Instruction Reordering Problem

```java
// Original code
int a = 1;        // (1)
int b = 2;        // (2)
flag = true;      // (3)

// CPU/Compiler might reorder to:
flag = true;      // (3) moved up!
int a = 1;        // (1)
int b = 2;        // (2)

// Another thread checking flag might see flag=true
// but a and b are not yet initialized!
```

### Double-Checked Locking Problem

```
Without volatile on instance:

Thread 1                              Thread 2
─────────                             ─────────
1. Allocate memory for Singleton
2. Assign reference to instance       
   (instance != null now)             3. Check instance != null → true
3. Call constructor                    4. Return instance (NOT fully constructed!)

With volatile: step 2 cannot be reordered before step 3
```

---

## Code

### Demonstrating Visibility Problem

```java
public class VisibilityProblem {
    private static boolean running = true; // NOT volatile
    private static int counter = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            while (running) { // May never see running = false
                counter++;
            }
            System.out.println("Worker stopped. Counter: " + counter);
        });

        worker.start();
        Thread.sleep(1000);
        running = false; // Change may not be visible to worker thread
        System.out.println("Main: set running to false");
        worker.join(2000); // May timeout - worker might never stop!
        
        if (worker.isAlive()) {
            System.out.println("WARNING: Worker thread is still running!");
        }
    }
}
```

### Fixing with volatile

```java
public class VisibilityFixed {
    private static volatile boolean running = true;
    private static volatile int counter = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            while (running) {
                counter++;
            }
            System.out.println("Worker stopped. Counter: " + counter);
        });

        worker.start();
        Thread.sleep(1000);
        running = false; // Guaranteed visible to worker
        worker.join();
        System.out.println("Main: Worker stopped successfully");
    }
}
```

### Volatile Does NOT Provide Atomicity

```java
public class VolatileNotAtomic {
    private static volatile int count = 0;

    public static void main(String[] args) throws InterruptedException {
        Runnable incrementTask = () -> {
            for (int i = 0; i < 10000; i++) {
                count++; // NOT atomic even with volatile!
                // count++ is actually: read count, add 1, write count
                // Another thread can interleave between read and write
            }
        };

        Thread t1 = new Thread(incrementTask);
        Thread t2 = new Thread(incrementTask);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Expected: 20000, Actual: " + count);
        // Will likely be less than 20000 due to lost updates
    }
}
```

### Using AtomicInteger for Atomicity

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicSolution {
    private static AtomicInteger count = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        Runnable incrementTask = () -> {
            for (int i = 0; i < 10000; i++) {
                count.incrementAndGet(); // Atomic operation using CAS
            }
        };

        Thread t1 = new Thread(incrementTask);
        Thread t2 = new Thread(incrementTask);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Expected: 20000, Actual: " + count.get());
        // Always 20000
    }
}
```

### Happens-Before with synchronized

```java
public class HappensBeforeDemo {
    private int x = 0;
    private int y = 0;
    private final Object lock = new Object();

    public void writer() {
        synchronized (lock) {
            x = 1; // These writes happen-before
            y = 2; // the lock release
        } // Lock release: all changes flushed to main memory
    }

    public void reader() {
        synchronized (lock) { // Lock acquire: refresh from main memory
            // Guaranteed to see x=1 and y=2
            // if writer() completed before this lock acquisition
            System.out.println("x=" + x + ", y=" + y);
        }
    }
}
```

### Double-Checked Locking (Correct Implementation)

```java
public class Singleton {
    // volatile prevents instruction reordering
    private static volatile Singleton instance;

    private Singleton() {
        // initialization
    }

    public static Singleton getInstance() {
        if (instance == null) {           // First check (no lock)
            synchronized (Singleton.class) {
                if (instance == null) {    // Second check (with lock)
                    instance = new Singleton();
                    // Without volatile, another thread might see
                    // a partially constructed object
                }
            }
        }
        return instance;
    }
}
```

### Data Race Example

```java
public class DataRace {
    private int sharedValue = 0;
    private boolean ready = false; // NOT volatile - data race!

    // Writer thread
    public void write() {
        sharedValue = 42;   // (1) might be reordered after (2)
        ready = true;       // (2)
    }

    // Reader thread
    public void read() {
        if (ready) {                    // might see ready=true
            System.out.println(sharedValue); // but sharedValue might still be 0!
            // Due to reordering: ready=true might execute before sharedValue=42
        }
    }
}

// Fix: make ready volatile
// volatile boolean ready ensures:
// 1. Write to sharedValue happens-before write to ready (program order)
// 2. Write to ready happens-before read of ready (volatile rule)
// 3. Therefore write to sharedValue happens-before read (transitivity)
```

### Atomic Classes and CAS

```java
import java.util.concurrent.atomic.*;

public class AtomicDemo {
    private AtomicInteger counter = new AtomicInteger(0);
    private AtomicLong longCounter = new AtomicLong(0L);
    private AtomicBoolean flag = new AtomicBoolean(false);
    private AtomicReference<String> ref = new AtomicReference<>("initial");

    public void demonstrate() {
        // Atomic increment
        int newVal = counter.incrementAndGet(); // atomically: ++counter

        // Compare-And-Swap (CAS)
        boolean success = counter.compareAndSet(1, 2);
        // If current value is 1, set to 2 atomically

        // Get and update
        int old = counter.getAndUpdate(x -> x * 2);

        // Accumulate
        counter.accumulateAndGet(5, Integer::sum);

        // AtomicReference CAS
        ref.compareAndSet("initial", "updated");
    }

    // Lock-free stack using CAS
    public static class LockFreeStack<T> {
        private AtomicReference<Node<T>> top = new AtomicReference<>(null);

        public void push(T value) {
            Node<T> newNode = new Node<>(value);
            Node<T> currentTop;
            do {
                currentTop = top.get();
                newNode.next = currentTop;
            } while (!top.compareAndSet(currentTop, newNode));
            // Retry if another thread modified top between get and set
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

        private static class Node<T> {
            final T value;
            Node<T> next;
            Node(T value) { this.value = value; }
        }
    }
}
```

---

## Dry Run

### Volatile Visibility Guarantee

```
Initial state: volatile boolean flag = false; int data = 0;

Thread 1:                    Thread 2:
─────────                    ─────────
data = 42;                   while (!flag) { }
  (store to working memory)    (reads flag from main memory each time
   → flushed because of         because flag is volatile)
   volatile write below)
                             
flag = true;                 // When flag becomes true:
  (volatile write)           // Happens-before guarantees data=42 is visible
  (flushes ALL prior         
   writes to main memory)    int result = data; // Guaranteed to be 42
```

### CAS Operation (Compare-And-Swap)

```
AtomicInteger counter = new AtomicInteger(5);

Thread 1: counter.compareAndSet(5, 6)
──────────
Step 1: Read current value → 5
Step 2: Compare 5 == 5? → YES
Step 3: Set to 6 atomically
Result: returns true, counter = 6

Thread 2: counter.compareAndSet(5, 7) (concurrent)
──────────
Step 1: Read current value → 6 (Thread 1 already changed it)
Step 2: Compare 6 == 5? → NO
Step 3: Do NOT update
Result: returns false, counter still = 6
Thread 2 retries with new expected value...
```

---

## Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| volatile read | O(1) | Reads from main memory (slightly slower than cached) |
| volatile write | O(1) | Writes to main memory + memory barrier |
| CAS operation | O(1) amortized | May spin-retry under contention |
| synchronized | O(1) uncontended | Biased lock → thin lock → heavy lock escalation |
| synchronized (contended) | Depends on wait time | Thread parking/unparking overhead |

### Performance Characteristics

| Mechanism | Overhead | Contention Behavior |
|-----------|----------|---------------------|
| volatile | Low (memory barriers) | No blocking, always succeeds |
| AtomicInteger (CAS) | Low-Medium | Spin-retry, good for low contention |
| synchronized | Medium (lock mgmt) | Thread blocking, fair for high contention |
| ReentrantLock | Medium | Configurable fairness, interruptible |

---

## Real Project Usage

### 1. Shutdown Flag Pattern

```java
public class GracefulShutdown {
    private volatile boolean shutdownRequested = false;

    public void requestShutdown() {
        shutdownRequested = true;
    }

    public void workerLoop() {
        while (!shutdownRequested) {
            processNextTask();
        }
        cleanup();
    }
}
```

### 2. Publishing Immutable Objects

```java
public class ConfigPublisher {
    // volatile reference to immutable config
    private volatile AppConfig currentConfig;

    public void updateConfig(AppConfig newConfig) {
        // newConfig is fully constructed before assignment
        currentConfig = newConfig; // Safe publication via volatile
    }

    public AppConfig getConfig() {
        return currentConfig; // Always sees a fully constructed config
    }
}
```

### 3. Metrics Counter (High-Throughput)

```java
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    // LongAdder is better than AtomicLong under high contention
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    public void recordRequest() {
        requestCount.increment();
    }

    public void recordError() {
        errorCount.increment();
    }

    public long getRequestCount() {
        return requestCount.sum();
    }
}
```

### 4. State Machine with Atomic Transitions

```java
import java.util.concurrent.atomic.AtomicReference;

public class OrderStateMachine {
    private final AtomicReference<OrderState> state = 
        new AtomicReference<>(OrderState.CREATED);

    public boolean transition(OrderState from, OrderState to) {
        if (!isValidTransition(from, to)) {
            throw new IllegalStateException(
                "Invalid transition: " + from + " → " + to);
        }
        return state.compareAndSet(from, to);
    }

    public OrderState getState() {
        return state.get();
    }

    private boolean isValidTransition(OrderState from, OrderState to) {
        return switch (from) {
            case CREATED -> to == OrderState.CONFIRMED;
            case CONFIRMED -> to == OrderState.SHIPPED || to == OrderState.CANCELLED;
            case SHIPPED -> to == OrderState.DELIVERED;
            default -> false;
        };
    }
}
```

---

## Interview Questions and Answers

### Q1: What is the Java Memory Model?
**A**: The JMM defines rules for how threads interact through memory. It specifies visibility (when changes by one thread become visible to others), atomicity (what operations are indivisible), and ordering (what reorderings are allowed). The central concept is the "happens-before" relationship — if action A happens-before action B, then A's effects are guaranteed to be visible to B.

### Q2: What is the happens-before relationship?
**A**: Happens-before is a guarantee that memory writes by one specific statement are visible to another specific statement. Key happens-before rules include: program order within a single thread, monitor unlock → subsequent monitor lock, volatile write → subsequent volatile read, thread start → actions in started thread, and actions in thread → join() returning in another thread.

### Q3: What does volatile do in Java?
**A**: Volatile provides two guarantees:
1. **Visibility**: A write to a volatile variable is immediately visible to all threads (bypasses CPU cache)
2. **Ordering**: Prevents instruction reordering around the volatile access (acts as a memory barrier)

It does NOT provide atomicity for compound operations like `i++`.

### Q4: Why is `i++` not thread-safe even with volatile?
**A**: `i++` is actually three operations: read i, increment, write i. Even with volatile, another thread can read the same value between our read and write, causing a lost update. Solutions: use `AtomicInteger.incrementAndGet()` or `synchronized`.

### Q5: What is a data race?
**A**: A data race occurs when two threads access the same variable, at least one access is a write, and there is no happens-before ordering between the accesses. Data races lead to undefined behavior — the reading thread might see stale values, partially constructed objects, or values that never existed.

### Q6: Explain Compare-And-Swap (CAS).
**A**: CAS is a hardware-level atomic operation: "if the current value equals the expected value, set it to the new value." It's used by `AtomicInteger`, `AtomicReference`, etc. CAS is lock-free — if it fails (value was changed by another thread), the operation retries. This avoids thread blocking but can spin under high contention.

### Q7: Why does double-checked locking require volatile?
**A**: Without volatile, object construction can be reordered. `instance = new Singleton()` involves: allocate memory, call constructor, assign reference. The JVM can reorder to: allocate, assign reference, call constructor. Another thread checking `instance != null` might get a reference to a partially constructed object. Volatile prevents this reordering.

### Q8: What is the difference between Heap and Stack in JMM context?
**A**: Stack is thread-private (local variables, method frames) — no visibility issues. Heap is shared (objects, instance variables) — subject to visibility and ordering issues. The JMM primarily governs how threads interact through heap memory.

---

## Follow-up Questions and Answers

### Q: What is a memory barrier/fence?
**A**: A memory barrier is a CPU instruction that enforces ordering constraints on memory operations. A "store barrier" ensures all writes before the barrier are visible to other processors. A "load barrier" ensures subsequent reads see the latest values. Volatile reads/writes insert appropriate memory barriers.

### Q: What is false sharing?
**A**: False sharing occurs when two threads modify different variables that happen to be on the same CPU cache line (typically 64 bytes). Each modification invalidates the entire cache line for the other CPU core, causing unnecessary cache misses. Solution: `@Contended` annotation or manual padding.

### Q: LongAdder vs AtomicLong?
**A**: AtomicLong uses a single CAS variable — high contention means many failed CAS retries. LongAdder distributes updates across multiple cells (striped approach), reducing contention. LongAdder is faster for writes but `sum()` is not atomic (acceptable for metrics). Use AtomicLong when you need exact real-time value; use LongAdder for counters.

### Q: What is safe publication?
**A**: Safe publication means making an object visible to other threads such that they see it fully constructed. Techniques: store via volatile field, store in a final field set in constructor, store into a concurrent collection, or store via synchronized block.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Not using volatile for flags | Infinite loop — thread never sees update | Add volatile |
| Thinking volatile makes `i++` atomic | Lost updates | Use AtomicInteger |
| Double-checked locking without volatile | Partially constructed object visible | Add volatile to instance field |
| Assuming reference assignment is enough for safe publication | Reader thread may see partially constructed object | Use volatile, final, or synchronized |
| Using synchronized on different lock objects | No mutual exclusion | Use the same lock object |
| Over-synchronizing | Deadlocks, poor performance | Minimize critical sections |
| Relying on Thread.sleep() for ordering | Race condition still exists | Use proper synchronization |

---

## Best Practices

1. **Prefer higher-level concurrency utilities** — Use `java.util.concurrent` classes before resorting to volatile/synchronized
2. **Minimize shared mutable state** — Immutable objects don't need synchronization
3. **Use volatile for simple flags** — Perfect for shutdown signals, status flags
4. **Use AtomicXxx for counters** — Lock-free, better performance than synchronized
5. **Use LongAdder for high-contention counters** — Striped cells reduce CAS failures
6. **Document threading contracts** — Which fields are shared? What synchronization is required?
7. **Don't rely on testing to find concurrency bugs** — They are timing-dependent; reason about happens-before instead
8. **Make fields final when possible** — Final fields have safe publication semantics from constructor

---

## Production Considerations

- **Volatile vs synchronized tradeoff**: Volatile is lighter but only provides visibility. For compound operations, you need atomics or locks.
- **Cache line effects**: Be aware of false sharing in performance-critical code. Use `@jdk.internal.vm.annotation.Contended` for JDK internals or manual padding.
- **Lock contention monitoring**: Use JFR or async-profiler to identify contended locks in production.
- **Avoid spin-waiting**: `while (!flag) {}` burns CPU. Use `LockSupport.park()`, `wait()/notify()`, or higher-level constructs.
- **Memory ordering on ARM/POWER**: These architectures have weaker memory models than x86. Code that "works on Intel" may break on ARM. Always rely on JMM guarantees, not hardware behavior.

---

## Related Topics

- [15. Synchronization](./15-synchronization.md)
- [16. Locks](./16-locks.md)
- [19. Concurrent Collections](./19-concurrent-collections.md)
- [14. Multithreading Fundamentals](./14-multithreading-fundamentals.md)
- [20. JVM Internals](./20-jvm-internals.md)
- [21. JVM Memory](./21-jvm-memory.md)
