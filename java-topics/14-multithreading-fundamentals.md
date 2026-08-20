# 14. Multithreading — Fundamentals

---

## Theory

A **thread** is the smallest unit of execution within a process. Java supports multithreading natively — every Java application has at least one thread (the main thread).

### Process vs Thread

| Aspect | Process | Thread |
|--------|---------|--------|
| Memory | Own address space | Shares process memory |
| Creation | Heavyweight (expensive) | Lightweight |
| Communication | IPC (pipes, sockets) | Shared memory (direct) |
| Isolation | Crash doesn't affect others | Crash can kill process |
| Context switch | Expensive | Cheaper |

### Thread Lifecycle (States)

```
NEW → RUNNABLE → RUNNING → TERMINATED
         ↕           ↕
      BLOCKED    WAITING/TIMED_WAITING
```

```java
public enum Thread.State {
    NEW,              // created, not yet started
    RUNNABLE,         // ready to run or running
    BLOCKED,          // waiting for monitor lock
    WAITING,          // waiting indefinitely (wait(), join())
    TIMED_WAITING,    // waiting with timeout (sleep(), wait(ms))
    TERMINATED        // completed execution
}
```

---

## Internal Working

### JVM Thread Model

```
JVM Thread Architecture:

Main Thread ─────────────────────────────────────────────►
    │
    ├── Thread-1 ───────────────────────────────────────►
    │
    ├── Thread-2 ─────────────────►  (completed)
    │
    └── Thread-3 ────────────────────────────────────────►

Each thread has:
├── Program Counter (PC) — current instruction
├── Stack — local variables, method calls
├── Thread-local storage
└── Reference to shared heap memory
```

### How Java Maps to OS Threads

- **Java thread = OS native thread** (1:1 mapping since Java 1.2)
- `Thread.start()` calls native `pthread_create()` (Linux) or `CreateThread()` (Windows)
- JVM does NOT implement its own scheduler — relies on OS scheduler
- **Exception:** Virtual threads (Java 21+) use M:N mapping (many virtual → few platform threads)

---

## Diagram

```
Thread State Transitions:

        ┌──────────────────────────────────────────────────┐
        │                                                  │
        ▼                                                  │
┌─────────────┐    start()    ┌────────────┐    run()     │
│    NEW      │──────────────►│  RUNNABLE  │───ends───────┘
└─────────────┘               └────────────┘   TERMINATED
                                    │  ▲
                                    │  │
                    ┌───────────────┘  └───────────────┐
                    │                                   │
                    ▼                                   │
            ┌──────────────┐                    ┌──────────────┐
            │   BLOCKED    │                    │   WAITING    │
            │(needs lock)  │                    │  (wait/join) │
            └──────────────┘                    └──────────────┘
                    │                                   │
                    │  lock acquired                    │ notify/interrupt
                    └──────────┐      ┌────────────────┘
                               ▼      ▼
                           ┌────────────┐
                           │  RUNNABLE  │
                           └────────────┘
```

---

## Code Examples

### Creating Threads

```java
// Method 1: Extend Thread class
public class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + " running");
    }
}
MyThread t = new MyThread();
t.start();  // starts new thread — calls run() in new thread

// Method 2: Implement Runnable (preferred)
public class MyTask implements Runnable {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName() + " running");
    }
}
Thread t2 = new Thread(new MyTask());
t2.start();

// Method 3: Lambda (simplest)
Thread t3 = new Thread(() -> System.out.println("Lambda thread"));
t3.start();

// Method 4: Callable (returns result + can throw)
Callable<Integer> task = () -> {
    Thread.sleep(1000);
    return 42;
};
// Used with ExecutorService (covered in topic 17)
```

### Thread vs Runnable — Why Runnable is Better

```java
// Thread: single inheritance limitation
class MyThread extends Thread { }  // can't extend anything else!

// Runnable: composition over inheritance
class MyTask implements Runnable {  // can still extend other classes
    @Override
    public void run() { /* task logic */ }
}

// Runnable separates task from execution mechanism
// Same task can be:
Runnable task = () -> processData();
new Thread(task).start();              // direct thread
executor.submit(task);                  // thread pool
scheduledExecutor.schedule(task, 5, TimeUnit.SECONDS);  // scheduled
```

### Important Thread Methods

```java
// start() vs run()
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()));
t.run();    // WRONG — runs in CURRENT thread (just a method call)
t.start();  // CORRECT — creates NEW thread, then calls run()

// sleep — pauses current thread (does NOT release lock)
Thread.sleep(1000);  // sleep 1 second, throws InterruptedException

// join — wait for another thread to finish
Thread worker = new Thread(() -> heavyComputation());
worker.start();
worker.join();         // current thread blocks until worker finishes
worker.join(5000);     // wait at most 5 seconds

// interrupt — signals thread to stop
Thread t2 = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        // do work
    }
    System.out.println("Interrupted, cleaning up...");
});
t2.start();
t2.interrupt();  // sets interrupt flag → sleep/wait throw InterruptedException

// yield — hint to scheduler (rarely used)
Thread.yield();  // suggests giving up CPU — scheduler may ignore

// setDaemon — daemon threads die when all non-daemon threads finish
Thread daemon = new Thread(() -> { while(true) { /* background work */ } });
daemon.setDaemon(true);  // must call BEFORE start()
daemon.start();  // will be killed when main thread exits
```

### Thread Priority

```java
Thread t = new Thread(task);
t.setPriority(Thread.MAX_PRIORITY);  // 10
t.setPriority(Thread.NORM_PRIORITY); // 5 (default)
t.setPriority(Thread.MIN_PRIORITY);  // 1

// NOTE: Priority is a HINT to the scheduler — not guaranteed
// OS may ignore it entirely. Don't rely on priority for correctness.
```

### Thread Communication — wait/notify

```java
public class ProducerConsumer {
    private final Queue<Integer> buffer = new LinkedList<>();
    private final int capacity = 10;
    private final Object lock = new Object();
    
    public void produce(int item) throws InterruptedException {
        synchronized (lock) {
            while (buffer.size() == capacity) {
                lock.wait();  // releases lock, waits for notify
            }
            buffer.add(item);
            lock.notifyAll();  // wakes up consumers
        }
    }
    
    public int consume() throws InterruptedException {
        synchronized (lock) {
            while (buffer.isEmpty()) {
                lock.wait();  // releases lock, waits for notify
            }
            int item = buffer.poll();
            lock.notifyAll();  // wakes up producers
            return item;
        }
    }
}
```

---

## Dry Run

### Thread Interleaving

```java
public class Counter {
    private int count = 0;
    
    public void increment() { count++; }  // NOT thread-safe!
    public int getCount() { return count; }
}

Counter counter = new Counter();
Thread t1 = new Thread(() -> { for(int i=0; i<1000; i++) counter.increment(); });
Thread t2 = new Thread(() -> { for(int i=0; i<1000; i++) counter.increment(); });
t1.start(); t2.start();
t1.join(); t2.join();
System.out.println(counter.getCount());  // Expected: 2000, Actual: < 2000!

// Why? count++ is NOT atomic. It's actually:
// 1. READ count → register
// 2. INCREMENT register
// 3. WRITE register → count

// Race condition:
// T1: READ count (0) → register1 = 0
// T2: READ count (0) → register2 = 0
// T1: INCREMENT → register1 = 1
// T2: INCREMENT → register2 = 1
// T1: WRITE → count = 1
// T2: WRITE → count = 1  (LOST UPDATE! Should be 2)
```

### join() Behavior

```java
Thread t1 = new Thread(() -> {
    System.out.println("T1 start");
    Thread.sleep(2000);
    System.out.println("T1 end");
});

Thread t2 = new Thread(() -> {
    System.out.println("T2 start");
    Thread.sleep(1000);
    System.out.println("T2 end");
});

t1.start();      // T1 begins
t1.join();       // Main blocks here until T1 finishes (2 sec)
t2.start();      // T2 begins AFTER T1 completes
t2.join();       // Main blocks until T2 finishes (1 sec)

// Output (sequential due to join):
// T1 start
// T1 end       (after 2 sec)
// T2 start
// T2 end       (after 1 more sec)
// Total: ~3 seconds
```

---

## Complexity

| Operation | Cost | Notes |
|-----------|------|-------|
| Thread creation | ~1ms + ~512KB-1MB stack | Heavy — reuse with pools |
| Context switch | ~1-10μs | OS scheduler overhead |
| `start()` | O(1) but expensive | Allocates native thread |
| `join()` | Blocks calling thread | No CPU cost while waiting |
| `sleep()` | Blocks current thread | No CPU cost while sleeping |
| `yield()` | O(1) | Just a hint, may do nothing |

---

## Real Project Usage

### Background Task

```java
public class FileProcessor {
    public void processAsync(Path file, Consumer<Result> callback) {
        Thread worker = new Thread(() -> {
            try {
                Result result = processFile(file);
                callback.accept(result);
            } catch (Exception e) {
                logger.error("Processing failed", e);
            }
        }, "file-processor-" + file.getFileName());
        worker.setDaemon(true);
        worker.start();
    }
}
```

### Graceful Shutdown

```java
public class GracefulWorker implements Runnable {
    private volatile boolean running = true;
    
    @Override
    public void run() {
        while (running) {
            try {
                doWork();
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();  // restore flag
                break;
            }
        }
        cleanup();
    }
    
    public void shutdown() {
        running = false;
    }
}
```

---

## Interview Questions and Answers

### Q1: What is the difference between `start()` and `run()`?

**A:**
- `start()` — creates a new OS thread and invokes `run()` on that new thread. The calling thread continues immediately.
- `run()` — just a regular method call on the current thread. No new thread is created.

```java
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()));
t.run();    // prints "main" — runs in current thread!
t.start();  // prints "Thread-0" — runs in new thread
```

### Q2: Can we start a thread twice?

**A:** No. Calling `start()` on a thread that has already been started throws `IllegalThreadStateException`. A thread goes from NEW → RUNNABLE → TERMINATED. Once terminated, it cannot be restarted. Create a new Thread object instead.

### Q3: What is the difference between `sleep()` and `wait()`?

**A:**

| `sleep()` | `wait()` |
|-----------|----------|
| `Thread` class method | `Object` class method |
| Does NOT release lock | Releases the monitor lock |
| Wakes after timeout | Wakes on `notify()`/`notifyAll()` or timeout |
| Can be called anywhere | Must be inside `synchronized` block |
| Pauses current thread | Used for thread communication |

### Q4: What is a daemon thread?

**A:** A daemon thread is a background thread that doesn't prevent JVM shutdown. When all non-daemon (user) threads finish, JVM terminates and kills all daemon threads.

**Use cases:** GC thread, monitoring, background cleanup.

```java
Thread t = new Thread(task);
t.setDaemon(true);  // must set BEFORE start()
t.start();
```

### Q5: Why use `while` instead of `if` with `wait()`?

**A:** **Spurious wakeups** — a thread can wake up without being notified. Also, another thread might consume the condition between notify and actual execution:

```java
// WRONG — if
synchronized(lock) {
    if (buffer.isEmpty()) lock.wait();
    // might still be empty! (spurious wakeup or race)
    consume(buffer.poll());
}

// CORRECT — while
synchronized(lock) {
    while (buffer.isEmpty()) lock.wait();
    // guaranteed non-empty when we exit the loop
    consume(buffer.poll());
}
```

---

## Follow-up Questions and Answers

### Q: What is thread starvation?

**A:** When a thread never gets CPU time because higher-priority threads always run first, or because it repeatedly fails to acquire a contested lock. Solution: use fair locks (`new ReentrantLock(true)`) or avoid priority abuse.

### Q: What is a thread-local variable?

**A:** `ThreadLocal<T>` gives each thread its own copy of a variable:

```java
ThreadLocal<SimpleDateFormat> formatter = ThreadLocal.withInitial(
    () -> new SimpleDateFormat("yyyy-MM-dd")
);

// Each thread gets its own SimpleDateFormat instance
String date = formatter.get().format(new Date());
```

### Q: What happens if `run()` throws an unchecked exception?

**A:** The thread terminates. You can catch it with `Thread.setUncaughtExceptionHandler`:

```java
thread.setUncaughtExceptionHandler((t, e) -> {
    logger.error("Thread {} died: {}", t.getName(), e.getMessage());
});
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Calling `run()` instead of `start()` | No new thread created | Use `start()` |
| Calling `start()` twice | `IllegalThreadStateException` | Create new Thread |
| Not handling `InterruptedException` | Swallowing interrupt signal | Restore flag or propagate |
| Using `stop()`/`suspend()`/`resume()` | Deprecated — unsafe | Use interrupt + volatile flag |
| Sharing mutable state without sync | Race conditions | Use synchronized/volatile/atomic |
| Creating unlimited threads | OOM/resource exhaustion | Use thread pools |
| Setting priority expecting guarantees | OS may ignore | Don't rely on priority for correctness |

---

## Best Practices

1. **Use thread pools** — never create unbounded threads (see topic 17)
2. **Handle InterruptedException properly** — restore interrupt flag or propagate
3. **Name your threads** — `new Thread(task, "order-processor")` for debugging
4. **Use Runnable over Thread** — separation of concerns, more flexible
5. **Prefer higher-level abstractions** — `ExecutorService`, `CompletableFuture`
6. **Use volatile or synchronized** for shared state — don't rely on timing
7. **Design for immutability** — immutable objects are automatically thread-safe

---

## Production Considerations

- **Thread count:** Rule of thumb — CPU-bound: threads = cores. IO-bound: threads = cores * (1 + wait/compute ratio)
- **Stack size:** Each thread uses 512KB-1MB stack. 10,000 threads = 5-10GB just for stacks!
- **Virtual threads (Java 21+):** Solve the thread-per-request problem with millions of lightweight threads
- **Monitoring:** Use `jstack` to dump thread states, `jconsole`/`VisualVM` to visualize
- **Deadlock detection:** JVM can detect deadlocks — visible in thread dumps

---

## Related Topics

- [15. Synchronization](./15-synchronization.md) — making multithreaded code safe
- [16. Locks](./16-locks.md) — advanced locking mechanisms
- [17. Executor Framework](./17-executor-framework.md) — thread pool management
- [18. CompletableFuture](./18-completable-future.md) — async programming
- [19. Concurrent Collections](./19-concurrent-collections.md) — thread-safe data structures
