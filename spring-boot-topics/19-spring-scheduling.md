# 19. Spring Scheduling

## Theory

Spring Scheduling enables running tasks at fixed intervals or specific times without external schedulers. It's built on Java's `ScheduledExecutorService` and provides annotation-based scheduling.

### Key Concepts:
- **@Scheduled**: Marks a method to run on a schedule
- **Fixed Rate**: Execute every N milliseconds (regardless of previous execution time)
- **Fixed Delay**: Wait N milliseconds after previous execution completes
- **Cron**: Unix-style cron expression for complex schedules
- **@EnableScheduling**: Activates scheduling support
- **TaskScheduler**: Programmatic scheduling interface

### Cron Expression Format:
```
┌───── second (0-59)
│ ┌───── minute (0-59)
│ │ ┌───── hour (0-23)
│ │ │ ┌───── day of month (1-31)
│ │ │ │ ┌───── month (1-12)
│ │ │ │ │ ┌───── day of week (0-7, MON-SUN)
│ │ │ │ │ │
* * * * * *
```

---

## Internal Working

```
Application Startup
       ↓
@EnableScheduling activates ScheduledAnnotationBeanPostProcessor
       ↓
Scans all beans for @Scheduled methods
       ↓
For each @Scheduled method:
  - Creates a ScheduledTask
  - Registers with TaskScheduler
       ↓
TaskScheduler (backed by ScheduledThreadPoolExecutor)
       ↓
┌─────────────────────────────────────┐
│ Thread Pool (default size = 1)       │
│                                      │
│ fixedRate:                           │
│   Schedule task every N ms           │
│   (may overlap if task > interval)   │
│                                      │
│ fixedDelay:                          │
│   Schedule next AFTER previous ends  │
│   (never overlaps)                   │
│                                      │
│ cron:                                │
│   Calculate next execution time      │
│   Schedule accordingly               │
└─────────────────────────────────────┘
```

---

## Diagram

```
┌────────────── fixedRate = 5000ms ──────────────────┐
│                                                     │
│  Task takes 3s:                                     │
│  |--3s--|     |--3s--|     |--3s--|                 │
│  0s     3s    5s     8s   10s    13s               │
│  ↑           ↑            ↑                        │
│  start      start        start                     │
│  (every 5s regardless)                             │
│                                                     │
└─────────────────────────────────────────────────────┘

┌────────────── fixedDelay = 5000ms ─────────────────┐
│                                                     │
│  Task takes 3s:                                     │
│  |--3s--|     ~~~5s~~~     |--3s--|     ~~~5s~~~    │
│  0s     3s              8s      11s                 │
│  ↑                       ↑                         │
│  start                  start                      │
│  (5s gap AFTER completion)                         │
│                                                     │
└─────────────────────────────────────────────────────┘

┌────────────── cron = "0 0 2 * * *" ───────────────┐
│                                                     │
│  Runs at 2:00 AM every day                         │
│                                                     │
│  Day 1      Day 2      Day 3                       │
│  02:00 AM   02:00 AM   02:00 AM                   │
│  ↑          ↑          ↑                           │
└─────────────────────────────────────────────────────┘
```

---

## Code

### Basic Configuration:

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);  // Default is 1!
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setErrorHandler(t -> 
            log.error("Scheduled task error: {}", t.getMessage(), t));
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
```

### Scheduled Tasks:

```java
@Component
@Slf4j
public class ScheduledTasks {

    // Every 30 seconds (fixed rate)
    @Scheduled(fixedRate = 30000)
    public void checkHealthStatus() {
        log.info("Health check running...");
    }

    // 10 seconds after previous completion (fixed delay)
    @Scheduled(fixedDelay = 10000)
    public void processQueue() {
        log.info("Processing queue...");
        // Safe: won't overlap even if processing takes > 10s
    }

    // Initial delay + fixed rate
    @Scheduled(initialDelay = 5000, fixedRate = 60000)
    public void warmUpCache() {
        log.info("Cache warm-up...");
    }

    // Cron: Every day at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyCleanup() {
        log.info("Running daily cleanup...");
    }

    // Cron: Every Monday at 9 AM
    @Scheduled(cron = "0 0 9 * * MON")
    public void weeklyReport() {
        log.info("Generating weekly report...");
    }

    // Using properties for externalized config
    @Scheduled(fixedRateString = "${scheduler.health-check.rate:30000}")
    public void configurableTask() {
        log.info("Configurable task running...");
    }

    // Cron from properties
    @Scheduled(cron = "${scheduler.cleanup.cron:0 0 3 * * *}")
    public void configurableCronTask() {
        log.info("Configurable cron task running...");
    }
}
```

### Conditional Scheduling:

```java
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class ConditionalScheduledTasks {

    @Scheduled(fixedRate = 60000)
    public void taskOnlyWhenEnabled() {
        // Only runs if scheduler.enabled=true in properties
    }
}
```

### Programmatic Scheduling:

```java
@Service
public class DynamicSchedulerService {

    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledFuture;

    public void scheduleTask(Runnable task, Duration interval) {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = taskScheduler.scheduleAtFixedRate(task, interval);
    }

    public void scheduleCron(Runnable task, String cronExpression) {
        taskScheduler.schedule(task, new CronTrigger(cronExpression));
    }

    public void cancelTask() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }
}
```

### Distributed Scheduling with ShedLock:

```java
// Prevents same task running on multiple instances
@Component
public class DistributedScheduledTasks {

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "dailyCleanup", 
                   lockAtLeastFor = "5m", 
                   lockAtMostFor = "30m")
    public void dailyCleanup() {
        // Only ONE instance executes this across cluster
    }
}
```

---

## Dry Run

### fixedRate = 5000ms, task takes 3000ms:

```
T=0ms:    Task starts (Thread-1)
T=3000ms: Task completes
T=5000ms: Task starts again (on schedule)
T=8000ms: Task completes
T=10000ms: Task starts again
...
Pattern: Task runs every 5s regardless of duration
```

### fixedRate = 5000ms, task takes 7000ms (LONGER than interval):

```
T=0ms:    Task starts (Thread-1)
T=5000ms: Next execution SHOULD start but Thread-1 still busy
T=7000ms: Task completes → immediately starts next execution
T=12000ms: Next would be at T=10000, but started at T=7000+7000=14000
...
Pattern: Tasks queue up, no parallelism (single thread default)
```

### fixedDelay = 5000ms, task takes 3000ms:

```
T=0ms:    Task starts
T=3000ms: Task completes → delay countdown starts
T=8000ms: Task starts again (3000 + 5000)
T=11000ms: Task completes → delay countdown starts
T=16000ms: Task starts again
...
Pattern: Always 5s gap between end and next start
```

---

## Complexity

| Aspect | Detail |
|--------|--------|
| Thread pool (default) | 1 thread for ALL scheduled tasks |
| Task scheduling | O(log n) - priority queue based |
| Memory per task | Minimal - just Runnable reference + metadata |
| Cron parsing | O(1) per trigger calculation |

---

## Real Project Usage

### Order Timeout Processing:

```java
@Component
@Slf4j
public class OrderTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000)  // Every minute after previous completes
    @Transactional
    public void cancelExpiredOrders() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<Order> expiredOrders = orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
        
        expiredOrders.forEach(order -> {
            orderService.cancelOrder(order.getId(), "Payment timeout");
            log.info("Cancelled expired order: {}", order.getId());
        });
        
        log.info("Processed {} expired orders", expiredOrders.size());
    }
}
```

---

## Interview Questions

1. **Difference between fixedRate and fixedDelay?**
   - fixedRate: Schedules every N ms from START of previous (can overlap). fixedDelay: Schedules N ms after END of previous (never overlaps).

2. **What's the default thread pool size for @Scheduled?**
   - 1 thread. All scheduled tasks share this single thread. Must configure ThreadPoolTaskScheduler for parallelism.

3. **How to prevent task overlap in multi-instance deployment?**
   - Use distributed locks: ShedLock, Quartz cluster mode, or database-based locking.

4. **Can @Scheduled methods accept parameters or return values?**
   - No. Must be void, zero-argument methods. Use injected services for data.

5. **How to handle exceptions in scheduled tasks?**
   - Uncaught exceptions kill that task's future executions (with single thread). Use try-catch or configure ErrorHandler.

---

## Follow-up Questions

1. How does Spring Scheduling compare to Quartz Scheduler?
   - Spring: Simple, annotation-based, in-memory only. Quartz: Persistent job store (DB), clustering, misfire handling, job chaining. Use Spring for simple tasks, Quartz for enterprise scheduling with persistence.

2. How to unit test scheduled tasks?
   - Don't test the scheduling itself. Test the method logic independently (it's just a regular method). For integration: use Awaitility to wait for execution, or call the method directly.

3. How to dynamically change schedule at runtime?
   - Use TaskScheduler bean programmatically. Cancel existing ScheduledFuture, create new one with updated interval/cron. Or use Quartz for DB-backed schedule changes.

4. What happens if scheduled task exceeds its interval?
   - fixedRate: Next execution queues up (may overlap with enough threads, or delays with 1 thread). fixedDelay: No issue (next starts after previous completes + delay). With 1 thread: tasks serialize.

5. How to implement scheduling in Kubernetes with multiple pods?
   - Problem: Same task runs on all pods. Solutions: ShedLock (distributed DB/Redis lock), Kubernetes CronJob (dedicated pod), leader election (only leader runs tasks), or use a dedicated scheduler service.

---

## Common Mistakes

1. **Default single thread** - All tasks compete for one thread; long task blocks others
2. **No error handling** - Uncaught exception can terminate future executions
3. **fixedRate with slow tasks** - Tasks pile up if execution > interval
4. **Multi-instance without lock** - Same task runs on every instance simultaneously
5. **Forgetting @EnableScheduling** - Tasks silently never execute
6. **Heavy tasks on tight schedules** - Memory/CPU pressure

---

## Best Practices

1. **Configure thread pool size** based on number of scheduled tasks
2. **Always wrap in try-catch** to prevent task termination on error
3. **Use fixedDelay** when tasks must not overlap
4. **Externalize schedules** via properties (not hardcoded)
5. **Use ShedLock** for distributed deployments
6. **Monitor execution** - log start/end, track duration
7. **Set initialDelay** to avoid startup contention
8. **Use @ConditionalOnProperty** to disable in test environments

---

## Production Considerations

- **Multiple instances**: Use ShedLock or leader election to prevent duplicate execution
- **Graceful shutdown**: Configure waitForTasksToCompleteOnShutdown
- **Monitoring**: Expose scheduled task metrics via Actuator/Micrometer
- **Alerting**: Alert if task hasn't run within expected window
- **Time zones**: Cron uses server timezone by default; specify zone explicitly
- **DST**: Be aware of daylight saving time effects on cron schedules
- **Kubernetes CronJobs**: Consider K8s CronJob instead of in-app scheduling for batch jobs

---

## Related Topics

- Async Processing (@Async)
- Spring Events (event-driven alternative)
- Kafka (event-driven scheduling)
- Kubernetes CronJobs
- Quartz Scheduler
- ShedLock (distributed locks)
