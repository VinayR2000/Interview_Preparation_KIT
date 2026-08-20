# 27. Anti-Patterns ⭐⭐⭐⭐

## Theory

Anti-patterns are common mistakes that undermine the benefits of microservices. Knowing what NOT to do is as important as knowing what to do.

### Critical Anti-Patterns:

| Anti-Pattern | What Goes Wrong | Correct Approach |
|-------------|-----------------|-----------------|
| Distributed Monolith | Services coupled, deploy together | Proper service boundaries |
| Shared Database | Schema coupling, no independence | Database per service |
| Too Many Services | Operational overhead > benefits | Right-size services |
| Synchronous Chains | Cascading failures | Async/event-driven |
| Chatty Services | High latency from many calls | API composition, ECST |
| No Observability | Can't debug distributed system | Three pillars from day 1 |
| No Timeout | Threads blocked forever | Timeout + fallback |
| Retry Without Backoff | DDoS your own services | Exponential backoff + jitter |
| Retry Non-Idempotent | Duplicate side effects | Idempotency keys |

---

## Internal Working

### Anti-Pattern 1: Distributed Monolith

```
┌────────────────────────────────────────────────────────────┐
│ DISTRIBUTED MONOLITH (Anti-Pattern)                         │
│                                                             │
│ Looks like microservices but acts like a monolith:         │
│                                                             │
│ ┌───────┐ ┌─────────┐ ┌──────┐                          │
│ │ Order │ │ Payment │ │ User │                           │
│ │Service│ │ Service │ │Service│                           │
│ └───┬───┘ └────┬────┘ └───┬──┘                           │
│     │          │           │                               │
│     └──── Shared library (common-models.jar) ────┘        │
│     │          │           │                               │
│     └──────────┼───────────┘                               │
│                ↓                                            │
│     ┌──────────────────────────┐                          │
│     │    SHARED DATABASE       │ ← All services           │
│     └──────────────────────────┘   access same tables     │
│                                                             │
│ Symptoms:                                                  │
│ ✗ Must deploy all services together                       │
│ ✗ Change in one service breaks others                     │
│ ✗ Shared library version conflicts                        │
│ ✗ One team blocks another                                 │
│ ✗ Can't scale independently                              │
│                                                             │
│ Root causes:                                               │
│ - Shared database                                         │
│ - Shared code libraries with domain logic                 │
│ - Synchronous call chains                                 │
│ - Tight API coupling (breaking changes)                   │
│ - Coordinated deployments required                        │
└────────────────────────────────────────────────────────────┘
```

### Anti-Pattern 2: Synchronous Chain (Death Star)

```
┌────────────────────────────────────────────────────────────┐
│ SYNCHRONOUS CALL CHAIN (Anti-Pattern)                       │
│                                                             │
│ User request → A → B → C → D → E                         │
│                                                             │
│ Problems:                                                  │
│ 1. Latency: sum of all calls (50ms × 5 = 250ms minimum) │
│ 2. Availability: if ANY service is down, entire chain fails│
│    Availability = 0.99^5 = 0.95 (95% uptime!)            │
│ 3. Cascading failure: E slow → D waits → C waits → ...  │
│                                                             │
│ ┌───┐ 50ms ┌───┐ 50ms ┌───┐ 50ms ┌───┐ 50ms ┌───┐     │
│ │ A │─────→│ B │─────→│ C │─────→│ D │─────→│ E │     │
│ └───┘      └───┘      └───┘      └───┘      └───┘     │
│                                                             │
│ If E takes 30s: ENTIRE chain blocked for 30s!             │
│                                                             │
│ CORRECT: Use async where possible                         │
│                                                             │
│ ┌───┐ sync  ┌───┐                                       │
│ │ A │──────→│ B │ (only when response needed immediately) │
│ └───┘       └───┘                                         │
│   │                                                        │
│   │ async (fire-and-forget)                               │
│   ↓                                                        │
│ ┌───────┐                                                 │
│ │ Kafka │──→ C, D, E consume independently               │
│ └───────┘                                                 │
└────────────────────────────────────────────────────────────┘
```

### Anti-Pattern 3: Too Many Microservices (Nano-services)

```
┌────────────────────────────────────────────────────────────┐
│ TOO MANY SERVICES (Anti-Pattern)                            │
│                                                             │
│ "Let's make everything a service!"                        │
│                                                             │
│ ┌───────────┐ ┌──────────────┐ ┌────────────────┐       │
│ │ Address   │ │ Phone Number │ │ Email          │       │
│ │ Service   │ │ Service      │ │ Validation Svc │       │
│ └───────────┘ └──────────────┘ └────────────────┘       │
│ ┌───────────┐ ┌──────────────┐ ┌────────────────┐       │
│ │ Full Name │ │ Date Format  │ │ Currency       │       │
│ │ Service   │ │ Service      │ │ Convert Svc    │       │
│ └───────────┘ └──────────────┘ └────────────────┘       │
│                                                             │
│ Problems:                                                  │
│ ✗ Operational overhead (deploy, monitor, debug × 100)    │
│ ✗ Network latency (many hops for simple operations)      │
│ ✗ Distributed transactions for trivial operations        │
│ ✗ Team cognitive overload                                 │
│                                                             │
│ CORRECT: Group by business capability                     │
│ ┌──────────────────┐                                     │
│ │  User Service    │ (handles address, phone, email,     │
│ │                  │  name — all part of user domain)     │
│ └──────────────────┘                                     │
│                                                             │
│ Rule of thumb:                                            │
│ - Can one team (5-8 people) own and operate it?          │
│ - Does it have its own data?                             │
│ - Can it be deployed independently?                      │
│ - Does it map to a business capability?                  │
│ If not → probably too small                              │
└────────────────────────────────────────────────────────────┘
```

### Anti-Pattern 4: No Timeout + No Backoff

```
┌────────────────────────────────────────────────────────────┐
│ RETRY WITHOUT BACKOFF (Anti-Pattern)                        │
│                                                             │
│ Payment Service is slow (recovering from failure)         │
│                                                             │
│ WITHOUT BACKOFF:                                          │
│ 1000 clients all retry immediately:                       │
│   T=0s: 1000 requests → Payment (overloaded)            │
│   T=0s: 1000 retries  → Payment (even more overloaded!) │
│   T=0s: 1000 retries  → Payment (CRASH!)                │
│                                                             │
│ This IS a DDoS attack on your own service!               │
│                                                             │
│ WITH EXPONENTIAL BACKOFF + JITTER:                        │
│   Client A: retry at 1.3s                                │
│   Client B: retry at 0.9s                                │
│   Client C: retry at 1.7s                                │
│   ... spread out over time                               │
│   Payment gets 100 req/s instead of 1000/s               │
│   Time to recover!                                        │
│                                                             │
│ NO TIMEOUT (Anti-Pattern):                                │
│   Order Service → Payment Service (hangs for 5 minutes)  │
│   Order Service thread blocked for 5 minutes!            │
│   × 100 concurrent requests = all threads exhausted      │
│   Order Service ALSO becomes unresponsive!               │
│   → Cascading failure!                                   │
│                                                             │
│ CORRECT: Always set timeout (e.g., 3s)                   │
│   If no response in 3s → fail fast → circuit breaker     │
└────────────────────────────────────────────────────────────┘
```

---

## Diagram

```
Anti-Patterns Summary:

┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  ❌ WRONG                          ✓ CORRECT               │
│                                                              │
│  Shared database                   Database per service     │
│  Shared business library           API contracts            │
│  Sync chain (A→B→C→D)            Async events              │
│  No timeout                        Timeout + fallback       │
│  Retry immediately                 Exponential backoff      │
│  Retry non-idempotent              Idempotency keys         │
│  Deploy all together               Independent deployment   │
│  One team owns all                 Team per service         │
│  No observability                  Logs + metrics + traces  │
│  100+ nano-services                Right-sized services     │
│  Distributed tx everywhere         Saga pattern             │
│  Same model for read/write         CQRS when needed        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Code

### Detecting Distributed Monolith:

```java
// ANTI-PATTERN: Shared library with domain entities
// shared-models/src/main/java/com/company/models/Order.java
public class Order {
    private Long id;
    private User user;        // ← Full User entity shared across services
    private Payment payment;  // ← Full Payment entity shared
    // Any change here breaks ALL services using this library
}

// CORRECT: Each service defines its own models
// order-service
public class Order {
    private Long id;
    private String userId;    // Just a reference ID
    private BigDecimal total;
    // Own model, no shared dependencies
}

// payment-service
public class Payment {
    private Long id;
    private String orderId;   // Just a reference ID
    private BigDecimal amount;
    // Completely independent
}
```

### Detecting Chatty Services:

```java
// ANTI-PATTERN: Multiple calls to display one page
public OrderDetailResponse getOrderDetails(UUID orderId) {
    Order order = orderRepository.findById(orderId);           // Local
    UserDto user = userClient.getUser(order.getUserId());      // Remote call 1
    AddressDto address = userClient.getAddress(user.getId());  // Remote call 2
    PaymentDto payment = paymentClient.getPayment(orderId);    // Remote call 3
    ShippingDto shipping = shippingClient.getStatus(orderId);  // Remote call 4
    ReviewDto review = reviewClient.getByOrder(orderId);       // Remote call 5
    // 5 network round-trips! Slow and fragile.
    
    return new OrderDetailResponse(order, user, address, payment, shipping, review);
}

// CORRECT Option 1: API composition at Gateway
// Gateway makes parallel calls, aggregates response

// CORRECT Option 2: Event-carried state transfer
// Order service stores denormalized data locally (updated via events)
public OrderDetailResponse getOrderDetails(UUID orderId) {
    // Everything in one local database query (denormalized)
    return orderDetailRepository.findById(orderId);  // Single query!
}

// CORRECT Option 3: GraphQL (client specifies what it needs)
```

### Correct Patterns:

```java
// ✓ Timeout + Circuit Breaker + Retry (correct combination)
@CircuitBreaker(name = "payment", fallbackMethod = "paymentFallback")
@Retry(name = "payment")  // Exponential backoff configured
@TimeLimiter(name = "payment")  // 3s timeout
public CompletableFuture<PaymentResponse> processPayment(PaymentRequest req) {
    return CompletableFuture.supplyAsync(() -> paymentClient.charge(req));
}

// ✓ Async where possible (don't chain synchronous calls)
public OrderResponse createOrder(CreateOrderRequest request) {
    // Sync: only what's needed for response
    Order order = orderRepository.save(new Order(request));
    
    // Async: everything else
    kafkaTemplate.send("order-events", new OrderCreatedEvent(order));
    // Payment, Inventory, Notification all consume async
    
    return new OrderResponse(order.getId(), "PENDING");
}

// ✓ Idempotent operations with idempotency key
@PostMapping("/payments")
public PaymentResponse pay(
        @RequestHeader("Idempotency-Key") String key,
        @RequestBody PaymentRequest request) {
    return paymentService.processIdempotent(key, request);
}
```

---

## Interview Questions

1. **What is a distributed monolith?**
   - Services that are technically separate but share database, deploy together, use shared domain libraries, or require coordinated changes. Has complexity of microservices with none of the benefits. Often worse than a well-structured monolith.

2. **How to identify if you have a distributed monolith?**
   - Can't deploy one service without deploying others. Shared database. Shared business logic library. One team's change breaks another team. Services fail together. Lock-step releases.

3. **Why is synchronous chain bad?**
   - Latency = sum of all calls. Availability = product of all availabilities (99%^5 = 95%). Any service failure blocks entire chain. Thread exhaustion under load. Use async events for non-immediate operations.

4. **When do microservices become an anti-pattern?**
   - Small team (< 5 people), simple domain, early-stage product, when you don't need independent scaling. Microservices add distributed system complexity (networking, data consistency, observability). Start monolith, extract when needed.

5. **How to avoid shared database anti-pattern?**
   - Define clear data ownership. Use APIs for cross-service data access. Event-driven sync (ECST) for data needed by multiple services. Accept eventual consistency. Saga for distributed transactions.

---

## Common Mistakes Checklist

```
Before going to production, verify you DON'T have:

□ Shared database between services
□ Shared domain model library
□ Synchronous chain of 4+ services
□ Any call without a timeout
□ Retry without exponential backoff
□ Retry on non-idempotent operations
□ No circuit breaker on external calls
□ No health checks
□ No centralized logging
□ No distributed tracing
□ No metrics/monitoring
□ Services that must deploy together
□ One team owning 10+ services
□ Distributed transactions (2PC) between services
```

---

## Best Practices

1. **Start monolith, extract services** — Don't prematurely decompose
2. **Right-size services** — Business capability, not technical layer
3. **Async by default** — Sync only when immediate response needed
4. **Timeout everything** — Never wait indefinitely for a response
5. **Idempotency everywhere** — Safe retries require idempotent operations
6. **Observe from day one** — Logs, metrics, traces before production
7. **Team ownership** — One team owns, builds, deploys, and operates their services
