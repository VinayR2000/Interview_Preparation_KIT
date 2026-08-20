# 8. Fault Tolerance

## Theory

Fault tolerance is the ability of a system to continue operating correctly when components fail. It combines multiple resilience patterns into a comprehensive strategy.

### The Challenge:
In microservices, failures are the norm — not the exception. Network partitions, service crashes, slow responses, and resource exhaustion happen constantly.

### Combining Patterns:
```
Timeout + Retry + Circuit Breaker + Bulkhead + Fallback = Fault Tolerance
```

### When NOT to Combine Blindly:
- **Retry + no idempotency** = duplicate operations
- **Long timeout + retry** = very long total wait time
- **Retry + Circuit Breaker both counting same failure** = premature circuit opening
- **Bulkhead too small + rate limiter** = rejecting valid traffic

---

## Internal Working

### Combined Fault Tolerance Flow:

```
Request from Order Service → Payment Service

Step 1: RATE LIMITER
  Question: Are we sending too many requests?
  If over limit → REJECT immediately (429)
  
Step 2: BULKHEAD
  Question: Is the thread pool for Payment full?
  If full → REJECT (no resources available)

Step 3: CIRCUIT BREAKER
  Question: Is Payment Service known to be down?
  If OPEN → return FALLBACK immediately (don't even try)
  If CLOSED or HALF_OPEN → proceed

Step 4: TIMEOUT
  Question: Did the call take too long?
  Start timer → if exceeds 3s → TimeoutException

Step 5: RETRY
  Question: Was it a transient failure?
  If retryable exception → wait (exponential) → try again
  If max retries reached → propagate failure to Circuit Breaker

Step 6: OUTCOME
  Success → update CB metrics (success count)
  Failure → update CB metrics (failure count)
          → if failure rate > threshold → CB opens

TOTAL TIME BUDGET:
  Timeout: 3s per attempt
  Retries: 3 attempts
  Backoff: 1s, 2s, 4s
  Worst case: 3s + 1s + 3s + 2s + 3s = 12s
  
  BUT if CB is open: ~0ms (fail fast!)
```

### Smart Timeout Calculation:

```
┌────────────────────────────────────────────────┐
│ TIMEOUT MATH                                    │
│                                                 │
│ P99 latency of Payment Service: 500ms          │
│ Normal response time: 100ms                    │
│                                                 │
│ Timeout should be:                             │
│   > P99 (don't timeout normal traffic)         │
│   < SLA budget (don't exceed overall SLA)      │
│                                                 │
│ If Order Service SLA = 2s total:               │
│   Timeout = 1s (leaves room for retries)       │
│   Max retries = 2                              │
│   Total: 1s + 0.5s(backoff) + 1s = 2.5s       │
│   → Too long! Reduce retries to 1             │
│   Total: 1s + 0.5s + 1s = 2.5s → still too long│
│   → Reduce timeout to 800ms                   │
│   Total: 800ms + 500ms + 800ms = 2.1s → close │
│   → Or: 1 retry only: 1s + 1s = 2s ✓         │
│                                                 │
│ Key insight: timeout × (retries+1) < SLA       │
└────────────────────────────────────────────────┘
```

### When Patterns Conflict:

```
PROBLEM: Retry amplifies load on failing service

Service B is slow (responding in 5s instead of 100ms)
1000 clients × 3 retries = 3000 requests to already-struggling service

SOLUTION: Circuit Breaker stops retries

After 10 failures → CB opens → no more requests to B
Clients get fallback in <1ms instead of waiting 15s

PROBLEM: Timeout + Retry = long total time

Timeout: 5s, Retries: 3
Worst case: 5 + 5 + 5 = 15s user wait!

SOLUTION: Time budget approach

Total budget: 3s
  Attempt 1: timeout 1.5s
  Attempt 2: timeout 1s (less time remains)
  → total ≤ 3s guaranteed

PROBLEM: Bulkhead too restrictive

Bulkhead: 5 concurrent calls to Payment
Normal load: 100 concurrent users
→ 95% rejected even when Payment is healthy!

SOLUTION: Size bulkhead based on load testing

Measure: Payment handles 50 concurrent req at P99 < 500ms
Bulkhead: 40 concurrent (80% of capacity, leave headroom)
```

---

## Diagram

```
Fault Tolerance Decision Tree:

Request arrives
     │
     ├── Is it rate limited? ──── YES → 429 (protect backend)
     │                   NO
     │
     ├── Is bulkhead full? ──── YES → 503 (resource exhausted)
     │                   NO
     │
     ├── Is circuit OPEN? ──── YES → Fallback (fail fast)
     │                   NO
     │
     ├── Call downstream
     │        │
     │        ├── Timeout? ──── YES → Is it retryable?
     │        │                          │
     │        │                    YES   │   NO
     │        │                    ↓     │    ↓
     │        │              Retry       │  Fallback
     │        │                          │
     │        ├── 4xx error? ──── YES → Don't retry → return error
     │        │
     │        ├── 5xx error? ──── YES → Retry (transient)
     │        │
     │        └── Success? ──── YES → Return response ✓
     │
     └── Max retries? ──── YES → Fallback
```

---

## Code

### Comprehensive Fault Tolerance Service:

```java
@Service
@Slf4j
public class FaultTolerantPaymentService {

    private final WebClient paymentClient;
    private final PaymentCacheService cache;
    private final PaymentQueueService queue;

    @CircuitBreaker(name = "payment", fallbackMethod = "handlePaymentFailure")
    @Bulkhead(name = "payment", type = Bulkhead.Type.THREADPOOL)
    @Retry(name = "payment")
    @TimeLimiter(name = "payment")
    public CompletableFuture<PaymentResponse> processPayment(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            // Idempotency key prevents duplicate payments on retry
            log.info("Processing payment. IdempotencyKey={}", 
                request.getIdempotencyKey());
            
            return paymentClient.post()
                .uri("/api/payments")
                .header("Idempotency-Key", request.getIdempotencyKey())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    // Don't retry client errors
                    return response.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(
                            new NonRetryableException("Client error: " + body)));
                })
                .bodyToMono(PaymentResponse.class)
                .block();
        });
    }

    // Fallback strategy — tiered approach
    private CompletableFuture<PaymentResponse> handlePaymentFailure(
            PaymentRequest request, Throwable throwable) {
        
        log.warn("Payment failed for order {}. Cause: {}", 
            request.getOrderId(), throwable.getClass().getSimpleName());

        // Tier 1: Check if payment already processed (idempotency)
        Optional<PaymentResponse> cached = cache.find(request.getIdempotencyKey());
        if (cached.isPresent()) {
            log.info("Found cached payment result");
            return CompletableFuture.completedFuture(cached.get());
        }

        // Tier 2: Queue for async processing
        queue.enqueue(request);
        
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .status("PENDING")
                .message("Payment queued. Will be processed shortly.")
                .orderId(request.getOrderId())
                .build());
    }
}
```

### Time-Budget Based Retry:

```java
@Service
public class TimeBudgetRetryService {

    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(3);

    public PaymentResponse callWithTimeBudget(PaymentRequest request) {
        Instant deadline = Instant.now().plus(TOTAL_BUDGET);
        int attempt = 0;
        Exception lastException = null;

        while (Instant.now().isBefore(deadline) && attempt < 3) {
            attempt++;
            Duration remaining = Duration.between(Instant.now(), deadline);
            
            if (remaining.isNegative()) break;

            try {
                // Timeout = remaining budget (shrinks each attempt)
                Duration timeout = remaining.dividedBy(2);  // Leave room for next attempt
                return callPayment(request, timeout);
            } catch (TimeoutException | IOException e) {
                lastException = e;
                log.warn("Attempt {} failed. Remaining budget: {}ms", 
                    attempt, remaining.toMillis());
                
                // Exponential backoff (but respect budget)
                Duration backoff = Duration.ofMillis(
                    Math.min(100L * (1L << attempt), remaining.toMillis() / 2));
                Thread.sleep(backoff.toMillis());
            }
        }
        
        // All attempts failed within budget
        return fallback(request, lastException);
    }
}
```

### Health-Aware Routing:

```java
@Service
public class HealthAwareService {

    private final CircuitBreakerRegistry registry;

    // Check circuit state before complex operations
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Check if payment service is available before starting
        CircuitBreaker paymentCB = registry.circuitBreaker("payment");
        
        if (paymentCB.getState() == CircuitBreaker.State.OPEN) {
            // Don't start order if we know payment will fail
            throw new ServiceUnavailableException(
                "Payment service is currently unavailable. Please try later.");
        }

        Order order = orderRepository.save(new Order(request));
        PaymentResponse payment = paymentService.processPayment(order);
        
        return new OrderResponse(order, payment);
    }
}
```

---

## Interview Questions

1. **How do you combine resilience patterns correctly?**
   - Order: RateLimiter (outermost) → Bulkhead → CircuitBreaker → Timeout → Retry (innermost). Rate limit first to protect backend. Retry last because it's closest to the actual call.

2. **What is a time budget approach?**
   - Allocate total time for an operation (e.g., 3s). Each retry attempt gets a portion of remaining time. Prevents total wait from exceeding SLA even with retries.

3. **How does Circuit Breaker prevent retry storms?**
   - Without CB: 1000 clients × 3 retries = 3000 requests to failing service. With CB: After threshold breached, all 1000 clients get fallback immediately. Service gets 0 requests, time to recover.

4. **When should you NOT use fallback?**
   - When partial/cached data could cause incorrect business decisions. E.g., don't fallback to "in stock" if you can't verify inventory — might oversell.

5. **How to size a Bulkhead?**
   - Based on load testing: measure downstream service's capacity, set bulkhead at 80% of that. Monitor queue depth. Too small = reject valid traffic. Too large = no isolation benefit.

---

## Common Mistakes

1. **Retry without idempotency** — Creates duplicate transactions
2. **Timeout × retries > SLA** — User waits too long
3. **Fallback that calls another failing service** — Cascading fallback failure
4. **Not distinguishing transient vs permanent errors** — Retrying 400 Bad Request
5. **All services share same CB instance** — Should be one per dependency
6. **Bulkhead with wrong size** — Too small rejects traffic, too large doesn't isolate

---

## Best Practices

1. **Design for failure** — Assume every call can fail
2. **Fail fast** — Better to reject quickly than wait and fail
3. **Graceful degradation** — Partial functionality > complete failure
4. **Time budget** — total timeout across all retries ≤ SLA
5. **Monitor everything** — CB state, retry count, timeout rate, bulkhead utilization
6. **Chaos testing** — Regularly inject failures to validate resilience
7. **Idempotency everywhere** — Enable safe retries
