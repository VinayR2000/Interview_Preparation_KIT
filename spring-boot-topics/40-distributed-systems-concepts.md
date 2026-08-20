# 40. Distributed Systems Concepts

## Theory

Distributed systems concepts are essential for senior-level Spring Boot interviews. Understanding these principles guides architectural decisions in microservices.

### Core Concepts:

| Concept | Definition |
|---------|-----------|
| CAP Theorem | Can only guarantee 2 of 3: Consistency, Availability, Partition tolerance |
| Eventual Consistency | All nodes converge to same state eventually (not immediately) |
| Strong Consistency | Read always returns most recent write |
| Idempotency | Same operation applied multiple times produces same result |
| Distributed Locking | Coordination mechanism across multiple instances |
| Leader Election | Selecting one node as coordinator among peers |
| Backpressure | Consumer signaling producer to slow down |
| Retry Storm | Cascading retries amplifying failure across services |

### CAP Theorem in Practice:
```
Network Partition happens (inevitable in distributed systems):
  → Choose Consistency: Reject requests until partition heals (CP - banking)
  → Choose Availability: Serve possibly stale data (AP - social media feed)

Spring Boot services typically choose AP:
  - Redis cache may serve stale data
  - Kafka consumers may read old offsets
  - Service returns cached response when DB unreachable
```

### Consistency Models:
```
Strong Consistency:
  Write → Acknowledged → All readers see new value immediately
  Example: Bank balance after transfer

Eventual Consistency:
  Write → Acknowledged → Readers MAY see old value temporarily
  Eventually (seconds/minutes) all readers see new value
  Example: Product review count, follower count

Causal Consistency:
  If A causes B, everyone sees A before B
  Unrelated events may be seen in different orders
  Example: Comment appears after post it replies to
```

---

## Internal Working

### Idempotency Implementation:
```
Client sends: POST /api/orders (Idempotency-Key: "abc-123")
       ↓
Server checks: Has "abc-123" been processed before?
  ├── YES → Return stored response (don't re-process)
  └── NO  → Process order
             → Store result with key "abc-123"
             → Return response
       ↓
If client retries (network timeout): Same key → Same response
       ↓
No duplicate orders created!
```

### Distributed Lock:
```
Instance-1 wants to process Order-42:
  → SET lock:order:42 "instance-1" NX EX 30
  → Returns OK (lock acquired)
  → Process order
  → DEL lock:order:42 (release)

Instance-2 concurrently wants Order-42:
  → SET lock:order:42 "instance-2" NX EX 30
  → Returns nil (lock NOT acquired — already held)
  → Wait or skip

Safety: EX 30 ensures lock released even if Instance-1 crashes
```

### Retry Storm Prevention:
```
Without protection:
  Service A retries 3x → Service B retries 3x → Service C
  Total calls to C: 3 × 3 = 9 (exponential amplification!)

With budget-based retry:
  Each service has retry budget: max 10% of calls can be retries
  If retry budget exhausted → fail immediately (no retry)
  Prevents cascading amplification
```

---

## Diagram

```
┌──────────── CAP THEOREM ─────────────────────────────────────┐
│                                                               │
│              Consistency                                       │
│                 /\                                             │
│                /  \                                            │
│               / CP \    ← Banking, payments                   │
│              /______\     (reject requests during partition)  │
│             /        \                                         │
│            / CA       \  ← Single node (no distribution)     │
│           /   (myth)   \                                      │
│          /______________\                                      │
│   Availability ──────────── Partition Tolerance               │
│         AP ← Social media, caching                           │
│           (serve stale data during partition)                 │
│                                                               │
└───────────────────────────────────────────────────────────────┘

┌──────────── SAGA PATTERN ────────────────────────────────────┐
│                                                               │
│  Order Service → Payment Service → Inventory Service          │
│                                                               │
│  Success Flow:                                                │
│  CreateOrder → ChargePayment → ReserveStock → DONE           │
│                                                               │
│  Failure + Compensation:                                      │
│  CreateOrder → ChargePayment → ReserveStock FAILS!           │
│                  ↓                                            │
│  CancelOrder ← RefundPayment ← (compensating transactions)  │
│                                                               │
│  Each step is a local transaction                            │
│  Compensation = undo of previous steps                       │
└───────────────────────────────────────────────────────────────┘
```

---

## Code

### Idempotency with Redis:

```java
@Service
@RequiredArgsConstructor
public class IdempotentOrderService {

    private final RedisTemplate<String, String> redisTemplate;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {
        String cacheKey = "idempotent:" + idempotencyKey;

        // Check if already processed
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.readValue(cached, OrderResponse.class);
        }

        // Process order
        Order order = orderRepository.save(buildOrder(request));
        OrderResponse response = toResponse(order);

        // Cache result with TTL (24 hours)
        redisTemplate.opsForValue().set(cacheKey, 
            objectMapper.writeValueAsString(response),
            Duration.ofHours(24));

        return response;
    }
}

// Controller passes idempotency key
@PostMapping("/orders")
public ResponseEntity<OrderResponse> createOrder(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateOrderRequest request) {
    OrderResponse response = orderService.createOrder(idempotencyKey, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

### Distributed Lock with Redis:

```java
@Service
@Slf4j
public class DistributedLockService {

    private final StringRedisTemplate redis;

    public <T> T executeWithLock(String lockKey, Duration ttl, Supplier<T> action) {
        String lockValue = UUID.randomUUID().toString();
        boolean acquired = acquireLock(lockKey, lockValue, ttl);

        if (!acquired) {
            throw new LockNotAcquiredException("Cannot acquire lock: " + lockKey);
        }

        try {
            return action.get();
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    private boolean acquireLock(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(
            redis.opsForValue().setIfAbsent(key, value, ttl));
    }

    private void releaseLock(String key, String expectedValue) {
        // Atomic check-and-delete (Lua script)
        String script = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;
        redis.execute(new DefaultRedisScript<>(script, Long.class),
            List.of(key), expectedValue);
    }
}

// Usage
public void processPayment(Long orderId) {
    lockService.executeWithLock(
        "lock:payment:" + orderId,
        Duration.ofSeconds(30),
        () -> paymentGateway.charge(orderId)
    );
}
```

### Eventual Consistency with Events:

```java
// Order Service — writes order, publishes event
@Service
public class OrderService {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(buildOrder(request));
        // Don't call inventory directly — publish event
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), order.getItems()));
        return order;
        // Inventory will be eventually consistent
    }
}

// Inventory Service — consumes event, updates stock eventually
@Component
public class InventoryEventHandler {

    @KafkaListener(topics = "order-events")
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        // This runs asynchronously — eventual consistency
        event.getItems().forEach(item ->
            inventoryRepository.decrementStock(item.getProductId(), item.getQuantity()));
    }
}
```

### Backpressure with Reactive:

```java
// Flux with backpressure control
public Flux<Order> streamOrders() {
    return orderRepository.findAllAsFlux()
        .onBackpressureBuffer(1000)  // Buffer up to 1000 if consumer slow
        .onBackpressureDrop(order -> 
            log.warn("Dropped order {} due to backpressure", order.getId()));
}

// Rate-limited consumer
public void consumeWithBackpressure() {
    streamOrders()
        .limitRate(100)  // Request 100 at a time
        .subscribe(this::processOrder);
}
```

---

## Dry Run

### Idempotency Scenario:

```
1. Client sends: POST /orders (Idempotency-Key: "key-abc")
   → Server processes order, saves Order#42
   → Caches: Redis["idempotent:key-abc"] = {id:42, status:"CREATED"}
   → Returns 201 {id: 42, status: "CREATED"}

2. Network timeout — client didn't receive response

3. Client retries: POST /orders (Idempotency-Key: "key-abc")
   → Server checks Redis: "idempotent:key-abc" EXISTS
   → Returns cached response: 201 {id: 42, status: "CREATED"}
   → NO duplicate order created!

4. Different client: POST /orders (Idempotency-Key: "key-xyz")
   → Redis: "idempotent:key-xyz" NOT found
   → Normal processing → new Order#43 created
```

### Retry Storm:

```
Service A → Service B → Service C (Service C is down)

Without protection:
  A calls B (timeout 3s, retry 3x)
  B calls C (timeout 3s, retry 3x)

  Attempt 1: A→B→C (timeout) → B retries C 2 more times → B fails → A retries
  Total C calls: 3 (B retries) × 3 (A retries) = 9 calls to dead service!
  
  With more layers: exponential explosion

With circuit breaker on B→C:
  B→C fails 5 times → Circuit OPENS
  B immediately returns failure (no more C calls)
  A gets error, retries hit B, B returns fast failure
  Total C calls: 5 (before circuit opens), then 0
```

---

## Complexity

| Pattern | Implementation Complexity | When Needed |
|---------|--------------------------|-------------|
| Idempotency | Medium (Redis + key tracking) | Every write API endpoint |
| Distributed Lock | Medium (Redis/Zookeeper) | Exclusive resource access |
| Eventual Consistency | Low (just accept it) | Most read operations |
| Saga Pattern | High (orchestrator + compensation) | Multi-service transactions |
| Leader Election | High (consensus protocol) | Singleton tasks in cluster |
| CRDT | Very High | Conflict-free replicated data |

---

## Real Project Usage

### E-commerce: Order Placement Saga:

```java
// Saga Orchestrator
@Service
public class OrderSagaOrchestrator {

    public OrderResult placeOrder(OrderRequest request) {
        String sagaId = UUID.randomUUID().toString();

        try {
            // Step 1: Create order (PENDING)
            Order order = orderService.createPendingOrder(request);

            // Step 2: Reserve inventory
            inventoryService.reserve(order.getItems());

            // Step 3: Process payment
            paymentService.charge(order.getTotal(), request.getPaymentMethod());

            // Step 4: Confirm order
            orderService.confirmOrder(order.getId());
            return OrderResult.success(order);

        } catch (InsufficientStockException e) {
            // Compensate: Cancel order
            orderService.cancelOrder(sagaId, "Insufficient stock");
            return OrderResult.failed("Item out of stock");

        } catch (PaymentDeclinedException e) {
            // Compensate: Release inventory + Cancel order
            inventoryService.release(request.getItems());
            orderService.cancelOrder(sagaId, "Payment declined");
            return OrderResult.failed("Payment failed");
        }
    }
}
```

---

## Interview Questions

1. **What is CAP theorem and how does it apply to microservices?**
   - In a network partition, choose Consistency (reject requests) or Availability (serve stale data). Most microservices choose AP with eventual consistency. Critical operations (payments) may choose CP.

2. **How do you implement idempotency in REST APIs?**
   - Client sends Idempotency-Key header. Server checks if key was processed (Redis lookup). If yes, return cached response. If no, process and cache result. Prevents duplicate operations on retry.

3. **What is the Saga pattern? When do you use it?**
   - Sequence of local transactions with compensating actions for rollback. Use when a business operation spans multiple services and needs atomicity. Types: Choreography (events) vs Orchestration (coordinator).

4. **How do you handle distributed transactions without 2PC?**
   - Use Saga pattern, outbox pattern (event + DB in same tx), or eventual consistency with idempotent consumers. 2PC is impractical in microservices (holding locks across services doesn't scale).

5. **What is a retry storm and how to prevent it?**
   - Cascading retries across service layers amplify load on failing service. Prevent with: circuit breakers, retry budgets (max 10% retries), exponential backoff with jitter, only retry at the edge (gateway).

---

## Follow-up Questions

1. How do you implement leader election in Spring Boot?
   - Use Spring Integration's LockRegistry (Redis or JDBC backed). Or use Kubernetes lease API. Leader runs scheduled tasks; followers are standby. On leader failure, another instance acquires the lock.

2. What's the difference between optimistic and pessimistic distributed locking?
   - Optimistic: Proceed without lock, check for conflicts at commit (version/timestamp). Pessimistic: Acquire lock before processing, hold until done (Redis SETNX). Optimistic is better for low-contention, pessimistic for guaranteed exclusivity.

3. How does eventual consistency work with user-facing reads?
   - User creates order → immediate redirect to order page. If read goes to replica (not yet replicated): show "Processing" state. Or: read-your-own-writes by routing to primary for recent writes.

4. How do you implement backpressure between microservices?
   - Kafka: Consumer controls poll rate (max.poll.records). REST: Rate limiter on client side. Reactive: Flux backpressure signals. Queue-based: Bounded queue rejects when full.

5. When would you use eventual consistency vs strong consistency?
   - Strong: Financial transactions, inventory count (overselling prevention), authentication state. Eventual: Social feed, analytics, search index, notification counts. Strong is expensive (consensus required); eventual is scalable.

---

## Common Mistakes

1. **Assuming network is reliable** - Timeouts, partitions, packet loss are normal
2. **Not implementing idempotency on POST endpoints** - Retries create duplicates
3. **Using distributed locks for everything** - Bottleneck, use event-driven instead
4. **Ignoring retry storms** - One slow service cascades to entire system
5. **Treating all data as strongly consistent** - Unnecessary complexity and latency
6. **2PC across microservices** - Doesn't scale, use Saga instead

---

## Best Practices

1. **Design for failure** - Assume every network call can fail
2. **Idempotency everywhere** - All write operations should be idempotent
3. **Accept eventual consistency** where possible (most reads)
4. **Use Saga pattern** for distributed transactions
5. **Implement circuit breakers** on all service-to-service calls
6. **Retry with exponential backoff + jitter** - Prevents thundering herd
7. **Distributed locks with TTL** - Prevent deadlocks on crash
8. **Monitor consistency lag** - Alert if eventual consistency takes too long

---

## Production Considerations

- **Consistency SLAs**: Define acceptable lag for each data type (1s? 5s? 1min?)
- **Split-brain protection**: Redis Sentinel/Cluster for lock reliability
- **Idempotency key TTL**: Keep long enough for client retry window (24h typical)
- **Saga compensation**: Must handle partial failures in compensation itself
- **Clock skew**: Don't rely on wall-clock time across services (use logical clocks)
- **Observability**: Trace saga execution across services, alert on stuck sagas

---

## Related Topics

- Microservices (architecture patterns)
- Kafka (event-driven eventual consistency)
- Redis (distributed locking, idempotency storage)
- Resilience Patterns (circuit breaker, retry)
- Transactions (local vs distributed)
- Spring Events (in-process event-driven)
