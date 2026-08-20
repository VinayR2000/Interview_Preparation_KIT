# 17. Executor Framework

---

## Theory

The Executor Framework (Java 5+) decouples **task submission** from **task execution**. Instead of creating threads manually, you submit tasks to a thread pool that manages thread lifecycle, reuse, and scheduling.

### Why Thread Pools?

| Manual Threads | Thread Pool |
|---------------|-------------|
| 1 thread per task (unbounded) | Fixed number of threads (bounded) |
| Thread creation overhead per task | Threads reused across tasks |
| Risk of OOM with too many threads | Controlled resource usage |
| No task queuing | Built-in task queue |
| No rejection policy | Configurable rejection handling |

### Core Interfaces

```
Executor                    → execute(Runnable)
    └── ExecutorService     → submit(), shutdown(), Future support
        └── ScheduledExecutorService → schedule(), scheduleAtFixedRate()
```

---

## Internal Working

### ThreadPoolExecutor Internals

```java
public ThreadPoolExecutor(
    int corePoolSize,       // threads always kept alive
    int maximumPoolSize,    // max threads allowed
    long keepAliveTime,     // idle time before non-core threads die
    TimeUnit unit,
    BlockingQueue<Runnable> workQueue,    // queue for pending tasks
    ThreadFactory threadFactory,          // how to create threads
    RejectedExecutionHandler handler      // what to do when queue is full
)
```

### Task Submission Flow

```
Task submitted
    │
    ├── Core threads not full? → Create new core thread → execute task
    │
    ├── Core threads full? → Queue not full? → Add to queue
    │
    ├── Queue full? → Below max threads? → Create new non-core thread
    │
    └── Queue full AND at max threads? → Rejection Policy
         ├── AbortPolicy (default) → throws RejectedExecutionException
         ├── CallerRunsPolicy → caller thread executes the task
         ├── DiscardPolicy → silently drops the task
         └── DiscardOldestPolicy → drops oldest queued task, retries
```

---

## Diagram

```
ThreadPoolExecutor Architecture:

┌─────────────────────────────────────────────────────────────────┐
│                    ThreadPoolExecutor                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────┐     ┌──────────────────────────────────┐   │
│  │   Thread Pool   │     │        Work Queue                │   │
│  │                 │     │  ┌────┬────┬────┬────┬────┐     │   │
│  │  [Worker-1] ◄───────────┤Task│Task│Task│Task│Task│     │   │
│  │  [Worker-2] ◄──┐    │  └────┴────┴────┴────┴────┘     │   │
│  │  [Worker-3] ◄──┤    │                                   │   │
│  │  [Worker-4]    ││    │  Types:                          │   │
│  │  (idle)     │  ││    │  - LinkedBlockingQueue (unbounded)│   │
│  │             │  ││    │  - ArrayBlockingQueue (bounded)   │   │
│  └─────────────┘  ││    │  - SynchronousQueue (no capacity)│   │
│                    ││    └──────────────────────────────────┘   │
│  corePoolSize: 3   ││                                           │
│  maxPoolSize: 5    │└── take task from queue when idle          │
│                    │                                             │
└────────────────────┴─────────────────────────────────────────────┘

Factory Methods:
┌────────────────────────────────────────────────────────────────┐
│ Executors.newFixedThreadPool(n)                                 │
│   core=n, max=n, queue=LinkedBlockingQueue (unbounded)          │
│                                                                  │
│ Executors.newCachedThreadPool()                                  │
│   core=0, max=Integer.MAX, queue=SynchronousQueue, keepAlive=60s│
│                                                                  │
│ Executors.newSingleThreadExecutor()                              │
│   core=1, max=1, queue=LinkedBlockingQueue (unbounded)           │
│                                                                  │
│ Executors.newScheduledThreadPool(n)                              │
│   core=n, max=Integer.MAX, queue=DelayedWorkQueue               │
└────────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Basic Usage

```java
// Fixed thread pool — n threads, unbounded queue
ExecutorService executor = Executors.newFixedThreadPool(4);

// Submit Runnable (no return value)
executor.execute(() -> System.out.println("Task 1"));

// Submit Callable (returns Future)
Future<String> future = executor.submit(() -> {
    Thread.sleep(1000);
    return "Result";
});

String result = future.get();           // blocks until done
String result2 = future.get(5, TimeUnit.SECONDS);  // with timeout

// Shutdown
executor.shutdown();              // no new tasks, finish existing
executor.shutdownNow();          // interrupt running, return queued
executor.awaitTermination(10, TimeUnit.SECONDS);  // wait for completion
```

### Custom ThreadPoolExecutor

```java
// Production-grade thread pool (don't use Executors factory methods!)
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    4,                                    // corePoolSize
    8,                                    // maximumPoolSize
    60L, TimeUnit.SECONDS,               // keepAliveTime for non-core
    new ArrayBlockingQueue<>(100),        // bounded queue (capacity 100)
    new ThreadFactory() {                  // named threads
        private final AtomicInteger counter = new AtomicInteger(1);
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "worker-" + counter.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // back-pressure
);

// Monitor pool state
int activeCount = executor.getActiveCount();
int queueSize = executor.getQueue().size();
long completedTasks = executor.getCompletedTaskCount();
```

### Future — Result and Exception Handling

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

Future<Integer> future = executor.submit(() -> {
    if (someCondition) throw new RuntimeException("Failed!");
    return 42;
});

try {
    Integer result = future.get(10, TimeUnit.SECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} catch (ExecutionException e) {
    Throwable cause = e.getCause();  // the actual exception
    logger.error("Task failed: {}", cause.getMessage());
} catch (TimeoutException e) {
    future.cancel(true);  // cancel the task
    logger.warn("Task timed out");
}

// Check state
future.isDone();       // completed (normally, exception, or cancelled)
future.isCancelled();  // was cancelled
future.cancel(true);   // attempt to cancel (true = may interrupt)
```

### ScheduledExecutorService

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

// Run once after delay
scheduler.schedule(() -> System.out.println("Delayed"), 5, TimeUnit.SECONDS);

// Run periodically (fixed rate — ignores execution time)
scheduler.scheduleAtFixedRate(() -> {
    System.out.println("Heartbeat");
}, 0, 10, TimeUnit.SECONDS);  // start immediately, every 10 sec

// Run periodically (fixed delay — waits after completion)
scheduler.scheduleWithFixedDelay(() -> {
    System.out.println("Poll");
    // takes 3 seconds
}, 0, 10, TimeUnit.SECONDS);  // next run: 3 + 10 = 13 seconds after start

// scheduleAtFixedRate: start at 0, 10, 20, 30... (regardless of execution time)
// scheduleWithFixedDelay: start at 0, 13, 26, 39... (waits 10 after each completion)
```

### invokeAll and invokeAny

```java
List<Callable<String>> tasks = List.of(
    () -> { Thread.sleep(1000); return "Task 1"; },
    () -> { Thread.sleep(2000); return "Task 2"; },
    () -> { Thread.sleep(500);  return "Task 3"; }
);

// invokeAll — wait for ALL to complete
List<Future<String>> allResults = executor.invokeAll(tasks);
// Returns when ALL done (after ~2 seconds)

// invokeAny — return FIRST successful result, cancel others
String firstResult = executor.invokeAny(tasks);
// Returns "Task 3" (fastest) after ~500ms, cancels others
```

---

## Dry Run

### Thread Pool Task Flow

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 60, SECONDS,
    new ArrayBlockingQueue<>(2));

// State: core=2, max=4, queue capacity=2
// Pool: [], Queue: []

pool.submit(task1);  // Pool: [task1], Queue: [] — new core thread
pool.submit(task2);  // Pool: [task1, task2], Queue: [] — new core thread
pool.submit(task3);  // Pool: [task1, task2], Queue: [task3] — core full, queue
pool.submit(task4);  // Pool: [task1, task2], Queue: [task3, task4] — queue
pool.submit(task5);  // Pool: [task1,task2,task5], Queue: [task3,task4] — queue full! new thread
pool.submit(task6);  // Pool: [task1,task2,task5,task6], Queue: [task3,task4] — new thread
pool.submit(task7);  // Queue full + max threads reached → REJECTION!
```

---

## Complexity

| Pool Type | Queue | Core Behavior |
|-----------|-------|---------------|
| FixedThreadPool | Unbounded (LinkedBlockingQueue) | Never creates above core, tasks queue up |
| CachedThreadPool | SynchronousQueue (0 capacity) | Creates threads up to MAX_INT, reuses idle |
| SingleThread | Unbounded (LinkedBlockingQueue) | One thread, tasks serialize |
| Custom (bounded queue) | ArrayBlockingQueue(n) | Most predictable behavior |

**Warning:** Unbounded queues (FixedThread, SingleThread) can cause OOM if tasks are submitted faster than processed!

---

## Real Project Usage

### HTTP Request Handler Pool

```java
@Configuration
public class AsyncConfig {
    
    @Bean("httpWorkerPool")
    public ExecutorService httpWorkerPool() {
        return new ThreadPoolExecutor(
            10, 50,
            30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(200),
            new CustomThreadFactory("http-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

### Graceful Shutdown

```java
public class AppShutdown {
    
    public void shutdown(ExecutorService executor) {
        executor.shutdown();  // stop accepting new tasks
        
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();  // force interrupt
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

### Batch Processor with Timeout

```java
public <T> List<T> processWithTimeout(List<Callable<T>> tasks, long timeoutSec) {
    ExecutorService executor = Executors.newFixedThreadPool(
        Math.min(tasks.size(), Runtime.getRuntime().availableProcessors())
    );
    
    try {
        List<Future<T>> futures = executor.invokeAll(tasks, timeoutSec, TimeUnit.SECONDS);
        return futures.stream()
            .filter(f -> !f.isCancelled())
            .map(f -> {
                try { return f.get(); }
                catch (Exception e) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    } finally {
        executor.shutdown();
    }
}
```

---

## Interview Questions and Answers

### Q1: Why shouldn't you use `Executors.newFixedThreadPool()` in production?

**A:** It uses an **unbounded** `LinkedBlockingQueue`. If tasks are submitted faster than processed, the queue grows indefinitely → **OutOfMemoryError**. 

Use `ThreadPoolExecutor` directly with a **bounded** `ArrayBlockingQueue` and appropriate rejection policy.

```java
// BAD — unbounded queue
ExecutorService bad = Executors.newFixedThreadPool(10);

// GOOD — bounded queue + rejection policy
ExecutorService good = new ThreadPoolExecutor(10, 20, 60, SECONDS,
    new ArrayBlockingQueue<>(500),
    new ThreadPoolExecutor.CallerRunsPolicy());
```

### Q2: What is the difference between `execute()` and `submit()`?

**A:**

| `execute(Runnable)` | `submit(Callable/Runnable)` |
|---------------------|----------------------------|
| Returns void | Returns `Future<T>` |
| Exceptions propagate to UncaughtExceptionHandler | Exceptions stored in Future |
| Cannot check completion | `future.isDone()`, `future.get()` |
| Defined in `Executor` | Defined in `ExecutorService` |

### Q3: Explain the rejection policies.

**A:**
1. **AbortPolicy** (default) — throws `RejectedExecutionException`
2. **CallerRunsPolicy** — the submitting thread runs the task itself (back-pressure)
3. **DiscardPolicy** — silently drops the task (data loss!)
4. **DiscardOldestPolicy** — drops the oldest queued task, then retries submission

**CallerRunsPolicy** is usually best for production — it provides natural back-pressure without data loss.

### Q4: What is the difference between `scheduleAtFixedRate` and `scheduleWithFixedDelay`?

**A:**
- **scheduleAtFixedRate** — next execution starts at `initialDelay + n*period` regardless of how long execution takes. If execution takes longer than period, next execution starts immediately after.
- **scheduleWithFixedDelay** — next execution starts `delay` after the previous execution **completes**.

```
scheduleAtFixedRate(task, 0, 10, SECONDS):     |--3s--|  |--3s--|  |--3s--|
Starts at:                                      0      10      20
                                               
scheduleWithFixedDelay(task, 0, 10, SECONDS):  |--3s--|     |--3s--|     |--3s--|
Starts at:                                      0         13         26
                                               (0+3+10=13)
```

### Q5: How do you size a thread pool?

**A:**
- **CPU-bound tasks:** threads = number of CPU cores (or cores + 1)
- **IO-bound tasks:** threads = cores × (1 + waitTime/computeTime)
- **Mixed:** separate pools for CPU and IO work

```java
int cores = Runtime.getRuntime().availableProcessors();

// CPU-bound
ExecutorService cpuPool = Executors.newFixedThreadPool(cores + 1);

// IO-bound (e.g., 80% wait time)
int ioThreads = cores * (1 + 80/20);  // cores * 5
ExecutorService ioPool = Executors.newFixedThreadPool(ioThreads);
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Unbounded queue in production | OOM under load | Use bounded `ArrayBlockingQueue` |
| Not shutting down executor | Thread leak, JVM won't exit | Always shutdown in finally/shutdown hook |
| `newCachedThreadPool` with burst load | Creates thousands of threads | Use bounded pool |
| Ignoring Future exceptions | Silent failures | Always `get()` or check |
| One pool for everything | IO-bound tasks starve CPU-bound | Separate pools by work type |
| Not naming threads | Can't identify threads in dumps | Use custom `ThreadFactory` |

---

## Best Practices

1. **Always use bounded queues** in production
2. **Name your threads** — use custom `ThreadFactory`
3. **Choose CallerRunsPolicy** for back-pressure without data loss
4. **Separate pools** for IO-bound and CPU-bound work
5. **Always shutdown** executors (shutdown hook or try-finally)
6. **Monitor pool metrics** — queue size, active threads, rejected count
7. **Use `invokeAll` with timeout** for batch operations
8. **Prefer `submit` over `execute`** — get Future for error handling

---

## Production Considerations

- **Monitoring:** Export metrics — pool size, queue depth, rejection count, task latency
- **Health checks:** Alert when queue depth exceeds threshold
- **Graceful shutdown:** In Spring, use `@PreDestroy` or implement `DisposableBean`
- **Thread naming:** Include pool purpose: `"order-processor-1"`, `"email-sender-3"`
- **Virtual threads (Java 21+):** For IO-bound work, consider `Executors.newVirtualThreadPerTaskExecutor()`

---

## Related Topics

- [14. Multithreading Fundamentals](./14-multithreading-fundamentals.md) — thread basics
- [18. CompletableFuture](./18-completable-future.md) — modern async with chaining
- [19. Concurrent Collections](./19-concurrent-collections.md) — thread-safe data structures
