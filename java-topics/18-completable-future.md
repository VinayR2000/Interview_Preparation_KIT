# 18. CompletableFuture

---

## Theory

`CompletableFuture<T>` (Java 8+) represents an asynchronous computation that can be composed, combined, and chained. It's Java's answer to modern async/reactive programming — combining `Future` with callback-based composition.

### Future vs CompletableFuture

| Future | CompletableFuture |
|--------|------------------|
| `get()` blocks | Chain callbacks (non-blocking) |
| Can't combine futures | `thenCombine`, `allOf`, `anyOf` |
| No exception chaining | `exceptionally`, `handle`, `whenComplete` |
| Can't manually complete | `complete()`, `completeExceptionally()` |
| No transformation | `thenApply`, `thenCompose` |

---

## Internal Working

```
CompletableFuture State Machine:

┌─────────────┐     complete(value)    ┌──────────────┐
│ INCOMPLETE  │──────────────────────►│  COMPLETED   │
│ (running)   │                        │  (has value) │
└─────────────┘                        └──────────────┘
       │          completeExceptionally()       
       └─────────────────────────────────►┌──────────────┐
                                          │   FAILED     │
                                          │ (has error)  │
                                          └──────────────┘

Internally:
- result: volatile Object (value or AltResult wrapper for exception)
- stack: Treiber stack of dependent CompletableFutures (callbacks)
- When completed → pops and runs all dependent stages
```

### Thread Execution

```
Method Suffix     | Execution Thread
------------------|-----------------------------------------
thenApply()       | Same thread that completed previous stage OR caller
thenApplyAsync()  | ForkJoinPool.commonPool() (default)
thenApplyAsync(executor) | Specified executor
```

---

## Diagram

```
CompletableFuture API Categories:

┌────────────────────────────────────────────────────────────────┐
│  CREATION                                                       │
│  supplyAsync(Supplier<T>)     → async with return value        │
│  runAsync(Runnable)           → async without return            │
│  completedFuture(T)          → already completed               │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  TRANSFORMATION (maps value)                                    │
│  thenApply(Function)          → T → U (like map)              │
│  thenCompose(Function)        → T → CF<U> (like flatMap)      │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  CONSUMPTION (side effects)                                     │
│  thenAccept(Consumer)         → T → void                       │
│  thenRun(Runnable)           → ignore result, run action       │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  COMBINATION (merge results)                                    │
│  thenCombine(CF, BiFunction)  → combine two CFs               │
│  allOf(CF...)                → complete when ALL done           │
│  anyOf(CF...)                → complete when ANY done           │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  ERROR HANDLING                                                  │
│  exceptionally(Function)      → recover from exception          │
│  handle(BiFunction)          → access both result and error    │
│  whenComplete(BiConsumer)    → side effect on completion       │
└────────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Creating CompletableFutures

```java
// Async with return value
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // runs in ForkJoinPool.commonPool()
    return fetchDataFromApi();
});

// Async without return value
CompletableFuture<Void> voidFuture = CompletableFuture.runAsync(() -> {
    sendNotification();
});

// With custom executor
ExecutorService executor = Executors.newFixedThreadPool(10);
CompletableFuture<String> custom = CompletableFuture.supplyAsync(
    () -> fetchData(), executor
);

// Already completed (for testing or default values)
CompletableFuture<String> completed = CompletableFuture.completedFuture("default");

// Manually completable
CompletableFuture<String> manual = new CompletableFuture<>();
// ... later in another thread:
manual.complete("result");
// or: manual.completeExceptionally(new RuntimeException("failed"));
```

### Chaining — thenApply, thenAccept, thenRun

```java
CompletableFuture<String> future = CompletableFuture
    .supplyAsync(() -> fetchUser(userId))       // async: get user
    .thenApply(user -> user.getEmail())          // transform: user → email
    .thenApply(email -> email.toLowerCase())     // transform: uppercase → lower
    .thenAccept(email -> sendEmail(email));      // consume: send email (void)

// thenRun — ignore previous result
CompletableFuture<Void> cleanup = future.thenRun(() -> {
    System.out.println("All done!");
});
```

### thenCompose vs thenApply

```java
// thenApply — Function<T, U> — returns the value directly
CompletableFuture<String> name = getUserId()
    .thenApply(id -> "User-" + id);  // Integer → String

// thenCompose — Function<T, CompletableFuture<U>> — flatMap for futures
CompletableFuture<String> name2 = getUserId()
    .thenCompose(id -> fetchUserName(id));  // Integer → CF<String>

// Why thenCompose? Without it:
CompletableFuture<CompletableFuture<String>> nested = getUserId()
    .thenApply(id -> fetchUserName(id));  // CF<CF<String>> — wrong!
```

### Combining Multiple Futures

```java
// thenCombine — combine results of two futures
CompletableFuture<String> userFuture = fetchUser(id);
CompletableFuture<List<Order>> ordersFuture = fetchOrders(id);

CompletableFuture<String> combined = userFuture.thenCombine(ordersFuture,
    (user, orders) -> user.getName() + " has " + orders.size() + " orders"
);

// allOf — wait for ALL to complete (returns Void)
CompletableFuture<Void> all = CompletableFuture.allOf(
    fetchUser(1), fetchUser(2), fetchUser(3)
);
all.join();  // blocks until all complete

// allOf with result collection
List<CompletableFuture<String>> futures = userIds.stream()
    .map(id -> CompletableFuture.supplyAsync(() -> fetchUserName(id)))
    .collect(Collectors.toList());

CompletableFuture<List<String>> allNames = CompletableFuture
    .allOf(futures.toArray(new CompletableFuture[0]))
    .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)
        .collect(Collectors.toList()));

// anyOf — first to complete wins
CompletableFuture<Object> fastest = CompletableFuture.anyOf(
    fetchFromCache(key),
    fetchFromDatabase(key),
    fetchFromRemote(key)
);
```

### Error Handling

```java
// exceptionally — recover from exception
CompletableFuture<String> safe = fetchData()
    .exceptionally(ex -> {
        logger.error("Fetch failed: {}", ex.getMessage());
        return "default";  // fallback value
    });

// handle — access both result AND exception
CompletableFuture<String> handled = fetchData()
    .handle((result, ex) -> {
        if (ex != null) {
            return "error: " + ex.getMessage();
        }
        return "success: " + result;
    });

// whenComplete — side effect (doesn't transform result)
CompletableFuture<String> logged = fetchData()
    .whenComplete((result, ex) -> {
        if (ex != null) {
            logger.error("Failed", ex);
        } else {
            logger.info("Got: {}", result);
        }
    });
// result is STILL the original (or still throws the original exception)
```

### Timeout (Java 9+)

```java
CompletableFuture<String> withTimeout = fetchData()
    .orTimeout(5, TimeUnit.SECONDS)          // throws TimeoutException
    .exceptionally(ex -> "timeout fallback");

CompletableFuture<String> withDefault = fetchData()
    .completeOnTimeout("default", 5, TimeUnit.SECONDS);  // completes with default
```

---

## Dry Run

### Async Pipeline Execution

```java
CompletableFuture<String> pipeline = CompletableFuture
    .supplyAsync(() -> {
        System.out.println("Step 1: " + Thread.currentThread().getName());
        return 42;
    })
    .thenApplyAsync(n -> {
        System.out.println("Step 2: " + Thread.currentThread().getName());
        return "Value is " + n;
    })
    .thenApply(s -> {
        System.out.println("Step 3: " + Thread.currentThread().getName());
        return s.toUpperCase();
    });

String result = pipeline.join();

// Output:
// Step 1: ForkJoinPool.commonPool-worker-1 (async)
// Step 2: ForkJoinPool.commonPool-worker-2 (async — different thread)
// Step 3: ForkJoinPool.commonPool-worker-2 (sync — same thread as step 2)
// Result: "VALUE IS 42"
```

### Exception Propagation

```java
CompletableFuture<String> chain = CompletableFuture
    .supplyAsync(() -> {
        throw new RuntimeException("Oops");
    })
    .thenApply(x -> x + " transformed")     // SKIPPED (exception propagates)
    .thenApply(x -> x + " again")           // SKIPPED
    .exceptionally(ex -> "Recovered: " + ex.getMessage());

// Result: "Recovered: java.lang.RuntimeException: Oops"
// thenApply stages are skipped when previous stage failed
// exceptionally catches the exception
```

---

## Complexity

| Operation | Behavior |
|-----------|----------|
| `supplyAsync` | Submits to thread pool — O(1) submission |
| `thenApply` (sync) | Runs in completing thread — near-zero overhead |
| `thenApplyAsync` | Submits to pool — task queue overhead |
| `allOf(n futures)` | Completes in max(execution times) |
| `anyOf(n futures)` | Completes in min(execution times) |
| `join/get` | Blocks calling thread |

---

## Real Project Usage

### Parallel API Aggregation

```java
public class UserProfileService {
    
    public UserProfile getFullProfile(Long userId) {
        CompletableFuture<User> userFuture = CompletableFuture
            .supplyAsync(() -> userService.findById(userId));
        
        CompletableFuture<List<Order>> ordersFuture = CompletableFuture
            .supplyAsync(() -> orderService.findByUserId(userId));
        
        CompletableFuture<CreditScore> creditFuture = CompletableFuture
            .supplyAsync(() -> creditService.getScore(userId))
            .completeOnTimeout(CreditScore.UNKNOWN, 3, TimeUnit.SECONDS);
        
        return CompletableFuture.allOf(userFuture, ordersFuture, creditFuture)
            .thenApply(v -> new UserProfile(
                userFuture.join(),
                ordersFuture.join(),
                creditFuture.join()
            ))
            .join();
    }
}
```

### Retry with Exponential Backoff

```java
public <T> CompletableFuture<T> retryAsync(Supplier<T> action, int maxRetries) {
    CompletableFuture<T> future = CompletableFuture.supplyAsync(action);
    
    for (int i = 0; i < maxRetries; i++) {
        int attempt = i;
        future = future.exceptionallyCompose(ex -> {
            long delay = (long) Math.pow(2, attempt) * 100;
            return delay(delay).thenCompose(v -> 
                CompletableFuture.supplyAsync(action));
        });
    }
    return future;
}

private CompletableFuture<Void> delay(long millis) {
    return CompletableFuture.runAsync(() -> {
        try { Thread.sleep(millis); } 
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    });
}
```

### Fan-Out / Fan-In Pattern

```java
public List<SearchResult> searchAll(String query) {
    List<CompletableFuture<SearchResult>> futures = searchEngines.stream()
        .map(engine -> CompletableFuture
            .supplyAsync(() -> engine.search(query))
            .orTimeout(2, TimeUnit.SECONDS)
            .exceptionally(ex -> SearchResult.empty()))
        .collect(Collectors.toList());
    
    return futures.stream()
        .map(CompletableFuture::join)
        .filter(r -> !r.isEmpty())
        .collect(Collectors.toList());
}
```

---

## Interview Questions and Answers

### Q1: What is the difference between `thenApply` and `thenCompose`?

**A:** Same as `map` vs `flatMap`:
- `thenApply(Function<T, U>)` — transforms the result. Returns `CompletableFuture<U>`.
- `thenCompose(Function<T, CompletableFuture<U>>)` — chains another async operation. Returns `CompletableFuture<U>` (flattened).

```java
// thenApply: synchronous transformation
cf.thenApply(s -> s.length());           // CF<String> → CF<Integer>

// thenCompose: another async step
cf.thenCompose(s -> fetchAsync(s));      // CF<String> → CF<Response>
// Without thenCompose: CF<String> → CF<CF<Response>> (nested!)
```

### Q2: What thread runs the callback in `thenApply` vs `thenApplyAsync`?

**A:**
- `thenApply()` — either the completing thread OR the calling thread (whichever attaches the callback after completion)
- `thenApplyAsync()` — always in `ForkJoinPool.commonPool()` (or specified executor)

**Use `Async` variants** when the callback is expensive or blocking, to avoid blocking the completing thread.

### Q3: How does `allOf` work? It returns `CompletableFuture<Void>` — how to get results?

**A:**
```java
CompletableFuture<String> f1 = supplyAsync(() -> "A");
CompletableFuture<String> f2 = supplyAsync(() -> "B");

CompletableFuture<List<String>> results = CompletableFuture
    .allOf(f1, f2)
    .thenApply(v -> List.of(f1.join(), f2.join()));
// allOf guarantees both are done, so join() won't block
```

### Q4: What is the difference between `exceptionally`, `handle`, and `whenComplete`?

**A:**
- `exceptionally(Function<Throwable, T>)` — **recovers** from exception, provides fallback value. Only runs on failure.
- `handle(BiFunction<T, Throwable, U>)` — **transforms** result regardless of success/failure. Always runs. Can change type.
- `whenComplete(BiConsumer<T, Throwable>)` — **observes** without modifying. Returns same result/exception. For logging/cleanup.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| `get()` instead of `join()` | `get()` throws checked exceptions | Use `join()` (unchecked) or handle properly |
| Blocking in async callback | Defeats purpose, starves pool | Use `thenCompose` for async ops |
| Not handling exceptions | Silent failures | Always add `exceptionally` or `handle` |
| Using common pool for blocking IO | Starves other async operations | Use dedicated executor |
| Forgetting `Async` for expensive callbacks | Blocks completing thread | Use `thenApplyAsync` |
| Not specifying executor | Common pool exhaustion | Provide custom executor for IO work |

---

## Best Practices

1. **Use custom executor for IO** — don't exhaust `ForkJoinPool.commonPool()`
2. **Always handle exceptions** — use `exceptionally`, `handle`, or `whenComplete`
3. **Add timeouts** — `orTimeout()` or `completeOnTimeout()` (Java 9+)
4. **Prefer `join()` over `get()`** — cleaner exception handling
5. **Use `thenCompose` for async chains** — avoid nested futures
6. **Keep callbacks short** — offload heavy work with `Async` variants
7. **Use `allOf` + `join`** pattern for collecting multiple results

---

## Production Considerations

- **Common pool exhaustion:** CPU-bound tasks in common pool can starve your async callbacks. Use separate pools.
- **Exception swallowing:** Unhandled exceptions in CompletableFuture are silent! Always add error handling.
- **Memory:** Each CompletableFuture holds references to its dependent stages until completion.
- **Debugging:** Stack traces don't show the submitting thread. Add context in exception messages.
- **Spring `@Async`:** Returns `CompletableFuture` and integrates with Spring's thread pool management.

---

## Related Topics

- [17. Executor Framework](./17-executor-framework.md) — thread pools that back CompletableFuture
- [14. Multithreading Fundamentals](./14-multithreading-fundamentals.md) — thread basics
- [10. Java 8 Features](./10-java8-features.md) — functional interfaces used throughout
