# 7. Resilience Patterns ⭐⭐⭐⭐⭐

## Theory

Resilience patterns protect your system from cascading failures. In distributed systems, any service can fail at any time. These patterns ensure one failure doesn't bring down the entire system.

### Key Patterns:

| Pattern | Purpose | Analogy |
|---------|---------|---------|
| Circuit Breaker | Stop calling failing service | Electrical circuit breaker |
| Retry | Handle transient failures | Try again |
| Timeout | Don't wait forever | Patience limit |
| Bulkhead | Isolate resource pools | Ship compartments |
| Rate Limiter | Prevent overload | Traffic control |
| Fallback | Alternative when primary fails | Plan B |

---

## Internal Working

### Circuit Breaker — State Machine:

```
┌─────────────────────────────────────────────────────┐
│                 CIRCUIT BREAKER                       │
│                                                      │
│         ┌──────────────────┐                        │
│   ┌────→│     CLOSED       │                        │
│   │     │ (Normal flow)    │                        │
│   │     │                  │                        │
│   │     │ All calls pass   │                        │
│   │     │ Track failures   │                        │
│   │     └────────┬─────────┘                        │
│   │              │                                   │
│   │              │ Failure rate > threshold (e.g. 50%)│
│   │              ↓                                   │
│   │     ┌──────────────────┐                        │
│   │     │      OPEN        │                        │
│   │     │ (Fail fast)      │                        │
│   │     │                  │                        │
│   │     │ All calls rejected│                       │
│   │     │ Return fallback  │                        │
│   │     └────────┬─────────┘                        │
│   │              │                                   │
│   │              │ Wait duration expires (e.g. 30s)  │
│   │              ↓                                   │
│   │     ┌──────────────────┐                        │
│   │     │   HALF_OPEN      │                        │
│   │     │ (Testing)        │                        │
│   │     │                  │                        │
│   │     │ Allow N test calls│                       │
│   │     └───┬──────────┬───┘                        │
│   │         │          │                             │
│   │  Success│    Failure│                            │
│   │  rate OK│    still  │                            │
│   │         │          │                             │
│   └─────────┘          └──→ Back to OPEN            │
│                                                      │
│  Sliding Window (COUNT_BASED):                      │
│  Last 10 calls: [✓ ✓ ✗ ✓ ✗ ✗ ✗ ✓ ✗ ✗]            │
│  Failure rate: 6/10 = 60% > 50% threshold          │
│  → Circuit OPENS                                    │
└─────────────────────────────────────────────────────┘
```

### Retry with Exponential Backoff + Jitter:

```
WITHOUT JITTER (Thundering Herd Problem):
Service recovers at T=0
  All 1000 clients retry at T=1s  → OVERLOAD again!
  All 1000 clients retry at T=2s  → OVERLOAD again!

WITH EXPONENTIAL BACKOFF + JITTER:
Client A: retry at 1.2s, then 2.7s, then 5.1s
Client B: retry at 0.8s, then 2.3s, then 4.6s
Client C: retry at 1.5s, then 3.1s, then 6.2s
→ Spread out, gradual recovery

Formula:
  delay = min(base × 2^attempt + random(0, jitter), maxDelay)

Example (base=1s, jitter=0.5s):
  Attempt 1: 1×2^0 + random(0, 0.5) = ~1.3s
  Attempt 2: 1×2^1 + random(0, 0.5) = ~2.4s
  Attempt 3: 1×2^2 + random(0, 0.5) = ~4.2s
```

### Bulkhead — Resource Isolation:

```
WITHOUT BULKHEAD:
┌─────────────────────────────────────────┐
│         Application (20 threads)         │
│                                          │
│  Payment Service SLOW (hangs 30s)       │
│  → 20/20 threads waiting for Payment   │
│  → NO threads for Order or Inventory   │
│  → ENTIRE APPLICATION BLOCKED          │
└─────────────────────────────────────────┘

WITH BULKHEAD:
┌─────────────────────────────────────────┐
│         Application (20 threads)         │
│                                          │
│  ┌────────────┐ ┌────────────┐ ┌──────┐│
│  │Payment Pool│ │Order Pool  │ │Inv.  ││
│  │  5 threads │ │  8 threads │ │Pool  ││
│  │  (all busy)│ │  (working) │ │7 thds││
│  └────────────┘ └────────────┘ └──────┘│
│                                          │
│  Payment slow → only 5 threads blocked  │
│  Order + Inventory still work fine!     │
│  Failure is ISOLATED                    │
└─────────────────────────────────────────┘
```

### Rate Limiting Algorithms:

```
FIXED WINDOW:
  Window: 1 minute, Limit: 100 requests
  ┌───────────────┬───────────────┐
  │  Minute 1     │  Minute 2     │
  │  100 allowed  │  100 allowed  │
  │  101st → 429  │  reset count  │
  └───────────────┴───────────────┘
  Problem: 100 at end of min1 + 100 at start of min2 = 200 in 1s window

SLIDING WINDOW:
  Tracks requests in rolling time frame
  Always enforces limit within ANY 1-minute window
  No burst at boundary issue

TOKEN BUCKET:
  Bucket holds N tokens (capacity)
  Tokens added at fixed rate (refill rate)
  Each request takes 1 token
  No tokens → rejected
  Allows bursts up to bucket capacity

  ┌─────────────────┐
  │ Bucket (100)    │ ← 10 tokens/sec added
  │ ████████░░░░░░  │   (75 tokens available)
  │                 │
  │ Request → take 1│
  │ No tokens → 429 │
  └─────────────────┘

LEAKY BUCKET:
  Requests enter a queue (bucket)
  Processed at fixed rate (leak rate)
  Bucket full → rejected
  Ensures SMOOTH output rate
```

---

## Diagram

```
Combined Resilience — Request Flow:

Client Request
     │
     ↓
┌──────────────┐
│ Rate Limiter │ → Over limit? → 429 Too Many Requests
└──────┬───────┘
       ↓
┌──────────────┐
│  Bulkhead    │ → Thread pool full? → 503 Service Unavailable
└──────┬───────┘
       ↓
┌──────────────┐
│Circuit Breaker│ → OPEN? → Return fallback immediately
└──────┬───────┘
       ↓
┌──────────────┐
│   Timeout    │ → Exceeded? → TimeoutException → trigger retry
└──────┬───────┘
       ↓
┌──────────────┐
│    Retry     │ → Failed? → Wait (exponential) → try again
│              │ → Max retries? → throw exception
└──────┬───────┘
       ↓
┌──────────────┐
│   Service    │ → Actual HTTP call to downstream
└──────┬───────┘
       │
       ↓
   Response (Success or Failure → feeds circuit breaker metrics)
```

---

## Code

### Resilience4j Configuration:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
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
        ignoreExceptions:
          - com.example.BusinessException

  retry:
    instances:
      paymentService:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        exponentialBackoffMaxWaitDuration: 10s
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:
          - com.example.BusinessException  # Don't retry business errors

  timelimiter:
    instances:
      paymentService:
        timeoutDuration: 3s

  bulkhead:
    instances:
      paymentService:
        maxConcurrentCalls: 10
        maxWaitDuration: 500ms

  ratelimiter:
    instances:
      paymentService:
        limitForPeriod: 100
        limitRefreshPeriod: 1s
        timeoutDuration: 0s
```

### Service with All Patterns:

```java
@Service
@Slf4j
public class PaymentServiceClient {

    private final WebClient webClient;

    // Order of annotations: outer → inner
    // RateLimiter → Bulkhead → CircuitBreaker → TimeLimiter → Retry
    @RateLimiter(name = "paymentService")
    @Bulkhead(name = "paymentService")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Calling payment service for order: {}", request.getOrderId());
        
        return webClient.post()
            .uri("/api/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentResponse.class)
            .timeout(Duration.ofSeconds(3))  // Timeout
            .block();
    }

    // Fallback — triggered when circuit is open or all retries exhausted
    private PaymentResponse paymentFallback(PaymentRequest request, Throwable t) {
        log.warn("Payment fallback for order {}: {}", 
            request.getOrderId(), t.getMessage());
        
        // Queue payment for async processing
        paymentQueue.add(request);
        
        return PaymentResponse.builder()
            .status("PENDING")
            .message("Payment queued for processing")
            .orderId(request.getOrderId())
            .build();
    }
}
```

### Retryable vs Non-Retryable Exceptions:

```java
@Service
public class SmartRetryService {

    @Retry(name = "paymentService", fallbackMethod = "fallback")
    public PaymentResponse pay(PaymentRequest request) {
        try {
            return callPaymentApi(request);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                // 400 = client error → DON'T retry (will always fail)
                throw new NonRetryableException("Invalid request", e);
            }
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // 409 = duplicate → DON'T retry
                throw new NonRetryableException("Duplicate payment", e);
            }
            // 500, timeout → DO retry
            throw e;
        }
    }
}
```

### Rate Limiter Implementation Concepts:

```java
// Token Bucket conceptual implementation
public class TokenBucket {
    private final int capacity;
    private final int refillRate;  // tokens per second
    private double tokens;
    private Instant lastRefill;

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1) {
            tokens--;
            return true;  // Request allowed
        }
        return false;  // Rate limited → 429
    }

    private void refill() {
        Instant now = Instant.now();
        double elapsed = Duration.between(lastRefill, now).toMillis() / 1000.0;
        tokens = Math.min(capacity, tokens + elapsed * refillRate);
        lastRefill = now;
    }
}
```

---

## Dry Run

### Circuit Breaker Scenario:

```
Configuration: threshold=50%, window=10, minCalls=5, wait=30s

Call 1: SUCCESS → window [✓] → failure=0/1=0%
Call 2: SUCCESS → window [✓✓] → failure=0/2=0%
Call 3: FAIL    → window [✓✓✗] → failure=1/3=33%
Call 4: FAIL    → window [✓✓✗✗] → failure=2/4=50%
Call 5: FAIL    → window [✓✓✗✗✗] → failure=3/5=60%
                  minCalls reached (5) + failure > 50%
                  STATE: CLOSED → OPEN ⚡

Call 6-15: All REJECTED immediately (fail fast)
           Return fallback response
           No HTTP call made
           Duration: ~0ms each

After 30 seconds: STATE: OPEN → HALF_OPEN

Call 16: SUCCESS (test call 1/3)
Call 17: SUCCESS (test call 2/3)
Call 18: SUCCESS (test call 3/3)
         All 3 permitted calls succeeded
         STATE: HALF_OPEN → CLOSED ✓

Normal operation resumes.
```

---

## Interview Questions

1. **Explain Circuit Breaker pattern and its states?**
   - CLOSED: Normal, all calls pass, track failures. OPEN: All calls rejected immediately, return fallback. HALF_OPEN: Allow limited test calls; if they succeed → CLOSED, if they fail → OPEN again.

2. **When to use Retry vs Circuit Breaker?**
   - Retry: Transient failures (network blip, momentary timeout). Circuit Breaker: Sustained failures (service is down). Combine: retry inside circuit breaker. CB prevents retry storms when service is actually down.

3. **What is exponential backoff with jitter?**
   - Increase delay between retries exponentially (1s, 2s, 4s, 8s). Add random jitter to prevent thundering herd (all clients retrying simultaneously). Formula: `delay = base × 2^attempt + random(0, jitter)`.

4. **What is Bulkhead pattern?**
   - Isolate resources per dependency. If Payment Service is slow, it uses its own thread pool (5 threads). Other services (Order, Inventory) use separate pools and aren't affected. Like ship compartments — flooding one doesn't sink the ship.

5. **When should you NOT retry?**
   - Non-idempotent operations without idempotency key (duplicate payment risk). Client errors (400, 409) — will always fail. Authentication failures (401, 403). Business logic failures.

6. **Rate limiting algorithms — compare them?**
   - Fixed Window: Simple but allows burst at window boundary. Sliding Window: No boundary burst but more memory. Token Bucket: Allows controlled bursts. Leaky Bucket: Smooth output rate. Most systems use Token Bucket.

---

## Common Mistakes

1. **Retrying non-idempotent operations** — Payment processed twice
2. **No jitter on retry** — Thundering herd overloads recovering service
3. **Timeout longer than circuit breaker wait** — Threads blocked even when CB is open
4. **Same fallback for all errors** — Fallback should be error-type specific
5. **Not monitoring circuit breaker state** — Blind to degradation
6. **Combining all patterns without understanding** — Over-engineering simple calls

---

## Best Practices

1. **Order matters**: RateLimiter → Bulkhead → CircuitBreaker → Timeout → Retry
2. **Meaningful fallbacks**: Cached data, degraded response, or queue for later
3. **Only retry idempotent operations**: Or use idempotency keys
4. **Monitor CB transitions**: Alert when circuit opens
5. **Tune based on SLAs**: If SLA is 500ms, timeout should be < 500ms
6. **Separate CB per dependency**: Don't share one CB across all services
7. **Test with chaos**: Inject failures to validate patterns work
