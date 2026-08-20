# 20. Async Processing

## Theory

Spring's `@Async` enables executing methods in a separate thread, allowing the caller to continue without waiting. This is essential for non-blocking operations like sending emails, processing files, or calling external services.

### Key Concepts:
- **@Async**: Marks a method for asynchronous execution
- **@EnableAsync**: Activates async processing support
- **TaskExecutor**: Thread pool that executes async tasks
- **CompletableFuture**: Return type for async methods that need results
- **Thread Pool**: Managed pool of worker threads

### When to Use Async:
- Fire-and-forget operations (email, notifications, audit logging)
- Parallel execution of independent operations
- Non-blocking external service calls
- Background processing (file generation, report creation)

---

## Internal Working

```
Caller invokes @Async method
       ↓
Spring AOP Proxy intercepts the call
       ↓
AsyncExecutionInterceptor wraps method as Runnable/Callable
       ↓
Submits to TaskExecutor (thread pool)
       ↓
Returns immediately to caller:
  - void: returns nothing
  - Future/CompletableFuture: returns placeholder
       ↓
Worker thread picks up task from queue
       ↓
Executes the actual method
       ↓
If CompletableFuture: completes the future with result
```

### Thread Pool Lifecycle:
```
Task submitted to executor
       ↓
┌─────────────────────────────────────────┐
│ Is corePoolSize reached?                 │
│ ├── NO → Create new thread, execute     │
│ └── YES                                 │
│      ↓                                  │
│ Is queue full?                           │
│ ├── NO → Add to queue, wait             │
│ └── YES                                 │
│      ↓                                  │
│ Is maxPoolSize reached?                  │
│ ├── NO → Create new thread, execute     │
│ └── YES → RejectionPolicy activates     │
│           (Abort/CallerRuns/Discard)     │
└─────────────────────────────────────────┘
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────┐
│                    REQUEST THREAD                          │
│                                                           │
│  orderService.createOrder(dto)                           │
│       │                                                   │
│       ├── Save order to DB (sync)                        │
│       ├── emailService.sendConfirmation(order) [ASYNC]   │
│       │       └──→ Submitted to thread pool              │
│       ├── analyticsService.track(order) [ASYNC]          │
│       │       └──→ Submitted to thread pool              │
│       └── Return response to client immediately          │
│                                                           │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│                    THREAD POOL                             │
│                                                           │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐      │
│  │Worker-1 │ │Worker-2 │ │Worker-3 │ │Worker-4 │      │
│  │sendEmail│ │track()  │ │  idle   │ │  idle   │      │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘      │
│                                                           │
│  Queue: [task5, task6, ...]                              │
│                                                           │
│  corePoolSize: 4                                         │
│  maxPoolSize: 8                                          │
│  queueCapacity: 100                                      │
└──────────────────────────────────────────────────────────┘
```

---

## Code

### Async Configuration:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean("taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // Separate pool for different concerns
    @Bean("emailExecutor")
    public TaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Async exception in {}.{}(): {}",
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                throwable.getMessage(), throwable);
        };
    }
}
```

### Fire-and-Forget:

```java
@Service
@Slf4j
public class NotificationService {

    @Async("emailExecutor")
    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to {} on thread {}", to, Thread.currentThread().getName());
        // Simulate email sending (2 seconds)
        emailClient.send(to, subject, body);
        log.info("Email sent to {}", to);
    }

    @Async
    public void sendPushNotification(Long userId, String message) {
        pushService.notify(userId, message);
    }
}
```

### With CompletableFuture (get result later):

```java
@Service
public class PricingService {

    @Async
    public CompletableFuture<BigDecimal> calculateDiscount(Long customerId) {
        // Expensive calculation
        BigDecimal discount = complexDiscountCalculation(customerId);
        return CompletableFuture.completedFuture(discount);
    }

    @Async
    public CompletableFuture<BigDecimal> fetchExternalPrice(String productId) {
        BigDecimal price = externalPriceApi.getPrice(productId);
        return CompletableFuture.completedFuture(price);
    }
}

// Caller: Parallel execution
@Service
public class OrderPricingService {

    private final PricingService pricingService;

    public OrderPrice calculateFinalPrice(Long customerId, String productId) 
            throws Exception {
        
        CompletableFuture<BigDecimal> discountFuture = 
            pricingService.calculateDiscount(customerId);
        CompletableFuture<BigDecimal> priceFuture = 
            pricingService.fetchExternalPrice(productId);

        // Wait for both to complete (parallel execution)
        CompletableFuture.allOf(discountFuture, priceFuture).join();

        BigDecimal discount = discountFuture.get();
        BigDecimal price = priceFuture.get();

        return new OrderPrice(price.subtract(discount));
    }
}
```

### Exception Handling:

```java
@Service
public class AsyncServiceWithErrorHandling {

    @Async
    public CompletableFuture<String> riskyOperation() {
        try {
            String result = externalCall();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}

// Caller handles exception
public void handleAsync() {
    asyncService.riskyOperation()
        .thenAccept(result -> log.info("Success: {}", result))
        .exceptionally(ex -> {
            log.error("Failed: {}", ex.getMessage());
            return null;
        });
}
```

---

## Dry Run

### Parallel API calls:

```
Thread: http-thread-1
  ↓
orderService.processOrder(order)
  ↓
1. Save order (sync) → 50ms
2. pricingService.calculateDiscount() → returns CompletableFuture immediately
   (submitted to async-thread-1, will take 200ms)
3. pricingService.fetchExternalPrice() → returns CompletableFuture immediately
   (submitted to async-thread-2, will take 300ms)
4. CompletableFuture.allOf().join() → waits for both
   
Timeline:
  T=0ms:   Start
  T=50ms:  Order saved, async tasks submitted
  T=50ms:  Both futures start executing in parallel
  T=250ms: calculateDiscount completes (200ms)
  T=350ms: fetchExternalPrice completes (300ms)
  T=350ms: allOf resolves, continue processing
  T=360ms: Return response

Total: 360ms (vs 550ms if sequential: 50 + 200 + 300)
```

---

## Complexity

| Aspect | Detail |
|--------|--------|
| Task submission | O(1) |
| Queue insertion | O(1) - LinkedBlockingQueue |
| Thread creation overhead | ~1ms per new thread |
| Context switching | OS-dependent, ~10-50μs |
| Memory per thread | ~512KB-1MB stack size |

---

## Real Project Usage

### Parallel Data Aggregation:

```java
@Service
public class DashboardService {

    @Async
    public CompletableFuture<SalesData> getSalesData(DateRange range) {
        return CompletableFuture.completedFuture(salesRepo.aggregate(range));
    }

    @Async
    public CompletableFuture<UserMetrics> getUserMetrics(DateRange range) {
        return CompletableFuture.completedFuture(userRepo.metrics(range));
    }

    @Async
    public CompletableFuture<InventoryStatus> getInventoryStatus() {
        return CompletableFuture.completedFuture(inventoryService.getStatus());
    }

    // Non-async orchestrator
    public DashboardData getDashboard(DateRange range) {
        var salesFuture = getSalesData(range);
        var usersFuture = getUserMetrics(range);
        var inventoryFuture = getInventoryStatus();

        CompletableFuture.allOf(salesFuture, usersFuture, inventoryFuture).join();

        return new DashboardData(
            salesFuture.join(),
            usersFuture.join(),
            inventoryFuture.join()
        );
    }
}
```

---

## Interview Questions

1. **How does @Async work internally?**
   - Spring creates a proxy. When async method is called, proxy intercepts and submits to TaskExecutor thread pool. Returns immediately (void or Future).

2. **Why doesn't @Async work with self-invocation?**
   - Same class call bypasses the proxy. `this.asyncMethod()` calls actual method, not proxy. Solution: inject self or extract to separate class.

3. **What's the default thread pool for @Async?**
   - SimpleAsyncTaskExecutor (creates new thread per task, NO pooling). Always configure a custom ThreadPoolTaskExecutor.

4. **How to handle exceptions in @Async void methods?**
   - Implement AsyncUncaughtExceptionHandler. Without it, exceptions are silently swallowed.

5. **@Async + @Transactional — do they work together?**
   - The async method gets its OWN transaction (new thread = new transaction context). Parent transaction is NOT propagated to async thread.

---

## Follow-up Questions

1. How to propagate SecurityContext to async threads?
   - Use TaskDecorator that copies SecurityContext: `SecurityContextHolder.getContext()` in parent, set in child. Or configure `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)` (not recommended for pools).

2. How to implement timeout for async operations?
   - CompletableFuture: `.orTimeout(5, TimeUnit.SECONDS)` or `.completeOnTimeout(defaultValue, 5, SECONDS)`. @TimeLimiter from Resilience4j. Or use @Async with CompletableFuture and caller-side timeout.

3. What's the difference between @Async and CompletableFuture.supplyAsync()?
   - @Async: Spring-managed, uses configured TaskExecutor, proxy-based. supplyAsync(): Java native, uses ForkJoinPool by default (or specified executor). @Async is better integrated with Spring context.

4. How to handle backpressure when queue is full?
   - RejectedExecutionHandler policies: CallerRunsPolicy (caller thread executes — slows producer), AbortPolicy (throw exception), DiscardPolicy (silently drop). CallerRunsPolicy is best for graceful degradation.

5. How does MDC (logging context) work with async threads?
   - MDC is ThreadLocal — not inherited by child threads. Use TaskDecorator to copy MDC map from parent to child thread before execution. Clear MDC in child after execution to prevent leaks.

---

## Common Mistakes

1. **Self-invocation** - Calling @Async method from same class bypasses proxy
2. **No custom executor** - Default creates unlimited threads (no pooling!)
3. **Ignoring void return exceptions** - Exceptions silently lost without handler
4. **@Async + @Transactional on same method** - Transaction context not shared with caller
5. **Unbounded queue** - Memory exhaustion under load
6. **Not propagating context** - SecurityContext, MDC lost in new thread
7. **Forgetting @EnableAsync** - Methods execute synchronously silently

---

## Best Practices

1. **Always configure ThreadPoolTaskExecutor** with bounded pool and queue
2. **Use CallerRunsPolicy** as rejection handler (graceful degradation)
3. **Separate executors** for different concerns (email, processing, etc.)
4. **Implement AsyncUncaughtExceptionHandler** for void methods
5. **Use CompletableFuture** when caller needs the result
6. **Propagate MDC/SecurityContext** using TaskDecorator
7. **Set waitForTasksToCompleteOnShutdown** for graceful shutdown
8. **Monitor queue depth** and active thread count

---

## Production Considerations

- **Thread pool sizing**: CPU-bound = core count. IO-bound = core count × (1 + wait/compute ratio)
- **Queue capacity**: Balance between memory usage and task rejection
- **Graceful shutdown**: Ensure in-flight tasks complete before app stops
- **Monitoring**: Track queue size, active threads, rejected tasks via Micrometer
- **Context propagation**: Use TaskDecorator to copy MDC, tracing context, security context
- **Memory**: Each queued task + thread stack consumes memory
- **Kubernetes**: Consider pod CPU limits when sizing thread pools

---

## Related Topics

- Spring AOP (proxy-based mechanism)
- Transactions (separate tx context in async)
- Spring Events (async event listeners)
- CompletableFuture (Java concurrency)
- Spring Scheduling (scheduled async work)
- Reactive Spring (non-blocking alternative)
