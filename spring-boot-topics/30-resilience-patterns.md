# 30. Resilience Patterns

## Theory

Resilience patterns protect applications from cascading failures in distributed systems. When one service is slow or down, these patterns prevent the failure from spreading to dependent services.

### Key Patterns:
- **Circuit Breaker**: Stop calling a failing service, fail fast
- **Retry**: Automatically retry failed operations
- **Timeout**: Set maximum wait time for operations
- **Bulkhead**: Isolate resources to prevent one failure from exhausting all resources
- **Rate Limiter**: Control the rate of requests to prevent overload
- **Fallback**: Provide alternative response when primary fails

### Resilience4j (Spring Boot Standard):
- Lightweight, modular library
- Works with Spring Boot auto-configuration
- Supports annotations and programmatic API
- Integrates with Actuator for metrics

---

## Internal Working

### Circuit Breaker States:
```
┌──────────────────────────────────────────────────────┐
│                                                       │
│  CLOSED ─────────────────→ OPEN                      │
│  (normal operation)    (failure threshold exceeded)   │
│  All requests pass     All requests fail fast        │
│                                                       │
│         ↑                     │                       │
│         │                     │ (wait duration)       │
│         │                     ↓                       │
│         │               HALF_OPEN                     │
│         │         (allow limited requests)            │
│         │                     │                       │
│         │    ┌────────────────┼────────────┐         │
│         │    │ Success rate   │ Still      │         │
│         │    │ above threshold│ failing    │         │
│         │    ↓                ↓            │         │
│         └── CLOSED           OPEN ─────────┘         │
│                                                       │
└──────────────────────────────────────────────────────┘

Metrics tracked:
- Failure rate (% of failed calls in sliding window)
- Slow call rate (% of calls exceeding slow threshold)
- Number of calls in sliding window
```

### Retry with Exponential Backoff:
```
Attempt 1: Call service → FAIL
  Wait: 1 second
Attempt 2: Call service → FAIL
  Wait: 2 seconds (exponential)
Attempt 3: Call service → FAIL
  Wait: 4 seconds (exponential)
Attempt 4: Call service → SUCCESS ✓
  Return result
```

### Bulkhead (Thread Pool Isolation):
```
Service A has 2 dependencies:

Without bulkhead:
  [shared thread pool: 10 threads]
  → Payment Service slow → all 10 threads waiting
  → Inventory Service calls also blocked!

With bulkhead:
  [Payment pool: 5 threads] → Payment slow → only 5 blocked
  [Inventory pool: 5 threads] → Still serving normally!
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    REQUEST FLOW                                │
│                                                               │
│  Client Request                                               │
│       │                                                       │
│       ↓                                                       │
│  ┌─────────────┐                                             │
│  │ Rate Limiter│ → Too many requests? → 429 Too Many Requests│
│  └──────┬──────┘                                             │
│         ↓                                                     │
│  ┌─────────────┐                                             │
│  │  Bulkhead   │ → Thread pool full? → BulkheadFullException │
│  └──────┬──────┘                                             │
│         ↓                                                     │
│  ┌─────────────────┐                                         │
│  │ Circuit Breaker  │ → OPEN state? → Fallback response      │
│  └──────┬───────────┘                                         │
│         ↓                                                     │
│  ┌─────────────┐                                             │
│  │   Timeout   │ → Exceeded? → TimeoutException              │
│  └──────┬──────┘                                             │
│         ↓                                                     │
│  ┌─────────────┐                                             │
│  │    Retry    │ → Failed? → Retry with backoff              │
│  └──────┬──────┘                                             │
│         ↓                                                     │
│  ┌─────────────┐                                             │
│  │   Service   │ → Actual HTTP call                          │
│  └─────────────┘                                             │
│                                                               │
│  Order of decoration (outermost → innermost):                │
│  RateLimiter → Bulkhead → CircuitBreaker → Timeout → Retry  │
└──────────────────────────────────────────────────────────────┘
```

---

## Code

### Configuration:

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 2s
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.HttpServerErrorException
        ignoreExceptions:
          - com.example.exception.BusinessException

  retry:
    instances:
      paymentService:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:
          - com.example.exception.BusinessException

  timelimiter:
    instances:
      paymentService:
        timeoutDuration: 3s
        cancelRunningFuture: true

  bulkhead:
    instances:
      paymentService:
        maxConcurrentCalls: 10
        maxWaitDuration: 500ms

  ratelimiter:
    instances:
      paymentService:
        limitForPeriod: 50
        limitRefreshPeriod: 1s
        timeoutDuration: 0s
```

### Service with Resilience Annotations:

```java
@Service
@Slf4j
public class PaymentService {

    private final RestClient paymentClient;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    @TimeLimiter(name = "paymentService")
    @Bulkhead(name = "paymentService")
    public CompletableFuture<PaymentResponse> processPayment(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Processing payment for order: {}", request.getOrderId());
            return paymentClient.post()
                .uri("/api/payments")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
        });
    }

    // Fallback method (same parameters + Throwable)
    public CompletableFuture<PaymentResponse> paymentFallback(
            PaymentRequest request, Throwable throwable) {
        log.warn("Payment service unavailable, using fallback. Error: {}", 
            throwable.getMessage());
        return CompletableFuture.completedFuture(
            new PaymentResponse("PENDING", "Payment queued for retry"));
    }
}
```

### Synchronous Circuit Breaker:

```java
@Service
public class InventoryService {

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "stockFallback")
    @Retry(name = "inventoryService", fallbackMethod = "stockFallback")
    public StockResponse checkStock(String productId) {
        return restClient.get()
            .uri("/api/inventory/{id}/stock", productId)
            .retrieve()
            .body(StockResponse.class);
    }

    private StockResponse stockFallback(String productId, Throwable t) {
        log.warn("Inventory check failed for {}, assuming in stock", productId);
        return new StockResponse(productId, true, "UNKNOWN");  // Optimistic fallback
    }
}
```

### Programmatic Usage:

```java
@Service
public class ResilientService {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;

    public ResilientService(CircuitBreakerRegistry cbRegistry,
                            RetryRegistry retryRegistry,
                            RateLimiterRegistry rlRegistry) {
        this.circuitBreaker = cbRegistry.circuitBreaker("paymentService");
        this.retry = retryRegistry.retry("paymentService");
        this.rateLimiter = rlRegistry.rateLimiter("paymentService");
    }

    public PaymentResponse makePayment(PaymentRequest request) {
        Supplier<PaymentResponse> supplier = () -> callPaymentApi(request);

        // Decorate with resilience patterns
        Supplier<PaymentResponse> decorated = Decorators.ofSupplier(supplier)
            .withRateLimiter(rateLimiter)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .withFallback(List.of(
                CallNotPermittedException.class,
                BulkheadFullException.class
            ), e -> fallbackResponse(request))
            .decorate();

        return decorated.get();
    }
}
```

### Event Listeners for Monitoring:

```java
@Component
public class CircuitBreakerEventListener {

    public CircuitBreakerEventListener(CircuitBreakerRegistry registry) {
        registry.circuitBreaker("paymentService").getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker '{}' state: {} → {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()))
            .onFailureRateExceeded(event ->
                log.error("Circuit breaker '{}' failure rate: {}%",
                    event.getCircuitBreakerName(),
                    event.getFailureRate()));
    }
}
```

---

## Dry Run

### Circuit Breaker Scenario (threshold=50%, window=10):

```
Calls 1-4: SUCCESS (4/4 = 100% success)
  State: CLOSED

Call 5: FAIL → (1 failure / 5 calls = 20% failure rate)
  State: CLOSED (below 50% threshold)

Call 6: FAIL → (2/6 = 33%)
  State: CLOSED

Call 7: FAIL → (3/7 = 43%)
  State: CLOSED

Call 8: FAIL → (4/8 = 50%)
  State: CLOSED (minimum calls reached, but exactly at threshold)

Call 9: FAIL → (5/9 = 55.5%)
  State: CLOSED → OPEN ⚡ (exceeds 50% failure threshold)

Call 10-15: REJECTED immediately (fail fast)
  → Returns fallback response
  → No actual HTTP call made

After 30s (waitDurationInOpenState):
  State: OPEN → HALF_OPEN

Call 16: SUCCESS (testing)
Call 17: SUCCESS
Call 18: SUCCESS (3/3 permitted calls succeeded)
  State: HALF_OPEN → CLOSED ✓ (service recovered)

Normal operation resumes.
```

---

## Complexity

| Pattern | Overhead | Purpose |
|---------|----------|---------|
| Circuit Breaker | O(1) state check | Prevent calling failing service |
| Retry | O(attempts × delay) | Handle transient failures |
| Timeout | O(1) timer setup | Prevent indefinite waiting |
| Bulkhead | O(1) semaphore/thread check | Resource isolation |
| Rate Limiter | O(1) token bucket check | Prevent overload |

---

## Real Project Usage

### E-commerce Resilience Strategy:

```java
@Service
public class OrderProcessingService {

    // Critical path: payment MUST work (retry aggressively)
    @CircuitBreaker(name = "payment", fallbackMethod = "paymentDown")
    @Retry(name = "payment")  // 3 attempts with exponential backoff
    public PaymentResult processPayment(Order order) { ... }

    // Non-critical: notifications can fail gracefully
    @CircuitBreaker(name = "notification", fallbackMethod = "notifyLater")
    @Bulkhead(name = "notification")
    public void sendNotification(Order order) { ... }

    private PaymentResult paymentDown(Order order, Throwable t) {
        // Queue for manual processing
        failedPaymentQueue.add(order);
        return PaymentResult.QUEUED;
    }

    private void notifyLater(Order order, Throwable t) {
        // Silently log, retry via scheduled job
        log.warn("Notification deferred for order {}", order.getId());
    }
}
```

---

## Interview Questions

1. **What is Circuit Breaker pattern and its states?**
   - CLOSED (normal), OPEN (failing fast), HALF_OPEN (testing recovery). Prevents cascading failures by stopping calls to failing service.

2. **When to use Retry vs Circuit Breaker?**
   - Retry: Transient failures (network blip, timeout). Circuit Breaker: Sustained failures (service down). Use together: retry within circuit breaker.

3. **What is Bulkhead pattern?**
   - Isolates failures by limiting concurrent calls per dependency. Like ship compartments — water in one doesn't sink the ship. Thread pool or semaphore based.

4. **How does Rate Limiter work?**
   - Token bucket algorithm: N tokens per period. Each request takes a token. No tokens = request rejected. Prevents overwhelming a service.

5. **What order should resilience patterns be applied?**
   - Outer to inner: RateLimiter → Bulkhead → CircuitBreaker → Timeout → Retry. Rate limit first, retry last (closest to actual call).

---

## Common Mistakes

1. **Retrying non-idempotent operations** - POST without idempotency key can create duplicates
2. **Too many retries** - 10 retries × 1000 clients = DDoS on recovering service
3. **Circuit breaker on wrong granularity** - One CB for all endpoints vs. per-endpoint
4. **No fallback** - Circuit opens → unhandled exception → 500 error
5. **Timeout > circuit breaker wait** - Threads stuck waiting even when CB is open
6. **Ignoring metrics** - Not monitoring CB state changes → blind to issues

---

## Best Practices

1. **Combine patterns**: CB + Retry + Timeout for comprehensive resilience
2. **Idempotent retries**: Only retry safe operations or use idempotency keys
3. **Meaningful fallbacks**: Return cached data, default response, or queued status
4. **Monitor CB state**: Alert on OPEN transitions, track failure rates
5. **Tune thresholds**: Based on SLA requirements and observed failure patterns
6. **Exponential backoff**: Prevent retry storms on recovering services
7. **Separate CBs per dependency**: paymentCB, inventoryCB, userCB

---

## Production Considerations

- **Metrics integration**: Resilience4j + Micrometer → Prometheus → Grafana dashboards
- **Alerting**: Alert when circuit opens (service degradation)
- **Tuning**: Start conservative, adjust based on production behavior
- **Testing resilience**: Chaos engineering (inject failures) to validate patterns work
- **Health indicators**: Expose CB status via Actuator health endpoint
- **Distributed coordination**: Each instance has its own CB state (not shared)

---

## Related Topics

- Microservices (why resilience matters)
- Spring Cloud (gateway resilience)
- Kafka (async = inherently resilient)
- Monitoring (Actuator + Prometheus)
- Testing (chaos engineering)
