# 28. System Design Patterns — Putting It All Together

## Theory

This chapter connects all microservices patterns into a cohesive system. In interviews, you need to demonstrate how patterns work together to solve real business problems.

### E-Commerce System — All Patterns Applied:

The goal is to design a production-ready e-commerce platform using:
- API Gateway (routing, auth, rate limiting)
- Service Discovery (Kubernetes DNS)
- Circuit Breaker + Retry + Timeout + Bulkhead
- Saga (distributed transactions)
- Transactional Outbox (reliable events)
- Idempotency (safe retries)
- CQRS (separate read/write)
- Event-Driven (Kafka)
- Caching (Redis)
- Observability (logs, metrics, traces)

---

## Internal Working

### Complete E-Commerce Architecture:

```
┌──────────────────────────────────────────────────────────────────┐
│                    E-COMMERCE SYSTEM DESIGN                        │
│                                                                    │
│  ┌──────────┐                                                    │
│  │  Client  │ (Browser/Mobile)                                   │
│  └────┬─────┘                                                    │
│       │ HTTPS                                                    │
│       ↓                                                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    API GATEWAY                              │  │
│  │  - JWT validation          - SSL termination              │  │
│  │  - Rate limiting (100/min) - Request routing              │  │
│  │  - CORS                    - Request/response logging     │  │
│  └──────┬────────────┬────────────┬──────────────┬──────────┘  │
│         │            │            │              │               │
│    ┌────┘      ┌─────┘      ┌────┘         ┌────┘              │
│    ↓           ↓            ↓              ↓                    │
│ ┌────────┐ ┌────────┐ ┌─────────┐ ┌──────────┐               │
│ │ Order  │ │Payment │ │Inventory│ │   User   │               │
│ │Service │ │Service │ │ Service │ │  Service │               │
│ │        │ │        │ │         │ │          │               │
│ │CB+Retry│ │CB+Retry│ │CB+Retry │ │ CB+Retry │               │
│ │Timeout │ │Timeout │ │Timeout  │ │ Timeout  │               │
│ │Bulkhead│ │Bulkhead│ │Bulkhead │ │ Bulkhead │               │
│ └───┬────┘ └───┬────┘ └────┬────┘ └────┬─────┘               │
│     │          │           │           │                       │
│     ↓          ↓           ↓           ↓                       │
│ ┌───────┐ ┌───────┐ ┌──────────┐ ┌──────────┐              │
│ │OrderDB│ │PayDB  │ │InvDB     │ │ UserDB   │              │
│ │+Outbox│ │+Outbox│ │+Outbox   │ │          │              │
│ └───────┘ └───────┘ └──────────┘ └──────────┘              │
│     │          │           │                                  │
│     └──────────┼───────────┘                                  │
│                ↓                                               │
│ ┌─────────────────────────────────────────────────────────┐  │
│ │                      KAFKA                               │  │
│ │  Topics:                                                 │  │
│ │  - order-events (OrderCreated, OrderConfirmed, ...)     │  │
│ │  - payment-events (PaymentCompleted, PaymentFailed)     │  │
│ │  - inventory-events (StockReserved, StockFailed)        │  │
│ │  - notification-events (EmailSent, SmsSent)             │  │
│ └─────────────────────────────────────────────────────────┘  │
│                ↓                                               │
│ ┌──────────────────────────┐  ┌───────────────────────────┐ │
│ │   Notification Service   │  │   Analytics Service       │ │
│ │   (Email, SMS, Push)     │  │   (CQRS read model)      │ │
│ └──────────────────────────┘  └───────────────────────────┘ │
│                                                                │
│ Cross-cutting:                                                │
│ ┌──────┐ ┌────────────┐ ┌──────────────┐ ┌──────────────┐  │
│ │Redis │ │Prometheus  │ │   Jaeger     │ │    ELK       │  │
│ │Cache │ │+ Grafana   │ │  (Tracing)   │ │  (Logging)   │  │
│ └──────┘ └────────────┘ └──────────────┘ └──────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### Order Creation Flow — All Patterns in Action:

```
┌──────────────────────────────────────────────────────────────────┐
│ "PLACE ORDER" — Complete Flow                                     │
│                                                                    │
│ 1. CLIENT → API Gateway                                          │
│    POST /api/orders                                              │
│    Authorization: Bearer <jwt>                                   │
│    Idempotency-Key: "order-abc-123"                             │
│                                                                    │
│ 2. API GATEWAY:                                                  │
│    ✓ Validate JWT (extract userId, roles)                       │
│    ✓ Rate limit check (user under 100 req/min)                  │
│    ✓ Route to order-service                                     │
│    ✓ Add headers: X-User-Id, X-Trace-Id                        │
│                                                                    │
│ 3. ORDER SERVICE:                                                │
│    a) Idempotency check (was "order-abc-123" processed?)        │
│    b) Validate request                                           │
│    c) Check user exists (sync call to User Service + CB)        │
│    d) BEGIN TRANSACTION:                                         │
│       - INSERT order (status=PENDING)                           │
│       - INSERT outbox_event (OrderCreatedEvent)                 │
│       COMMIT                                                     │
│    e) Return 201 {orderId, status: "PENDING"}                   │
│                                                                    │
│ 4. OUTBOX PUBLISHER (background):                                │
│    - Reads outbox → publishes OrderCreatedEvent to Kafka        │
│                                                                    │
│ 5. PAYMENT SERVICE (Kafka consumer):                             │
│    a) Receive OrderCreatedEvent                                  │
│    b) Idempotency check (was this event processed?)             │
│    c) Process payment (charge customer)                          │
│    d) Transaction: save payment + outbox(PaymentCompletedEvent) │
│    e) Publish PaymentCompletedEvent                             │
│                                                                    │
│ 6. INVENTORY SERVICE (Kafka consumer):                           │
│    a) Receive OrderCreatedEvent                                  │
│    b) Reserve stock                                              │
│    c) Publish StockReservedEvent                                │
│                                                                    │
│ 7. ORDER SERVICE (Kafka consumer):                               │
│    a) Receive PaymentCompletedEvent + StockReservedEvent        │
│    b) Update order → CONFIRMED                                  │
│                                                                    │
│ 8. NOTIFICATION SERVICE (Kafka consumer):                        │
│    a) Receive OrderConfirmedEvent                                │
│    b) Send confirmation email                                    │
│                                                                    │
│ FAILURE SCENARIO (Payment fails):                                │
│    Payment Service → publishes PaymentFailedEvent               │
│    Order Service → cancels order (CANCELLED)                    │
│    Inventory Service → releases reserved stock                  │
│    = SAGA COMPENSATION                                           │
│                                                                    │
│ PATTERNS USED:                                                   │
│ ✓ API Gateway (routing, auth, rate limiting)                    │
│ ✓ Circuit Breaker (User Service call)                           │
│ ✓ Transactional Outbox (reliable event publishing)              │
│ ✓ Idempotency (duplicate detection)                             │
│ ✓ Saga Choreography (distributed transaction)                   │
│ ✓ Event-Driven (Kafka pub/sub)                                  │
│ ✓ Observability (traceId propagated everywhere)                 │
│ ✓ Database per Service (each has own DB)                        │
└──────────────────────────────────────────────────────────────────┘
```

---

## Diagram

### How Read Path Works (CQRS + Cache):

```
┌────────────────────────────────────────────────────────────┐
│ READ PATH: "Get Order Details"                              │
│                                                             │
│ GET /api/orders/101                                        │
│       │                                                    │
│       ↓                                                    │
│ ┌──────────────┐                                          │
│ │ API Gateway  │                                          │
│ └──────┬───────┘                                          │
│        ↓                                                   │
│ ┌──────────────────────────────────────┐                  │
│ │ Order Service — Query Handler         │                  │
│ │                                       │                  │
│ │  1. Check Redis cache                 │                  │
│ │     └── HIT? Return cached response  │                  │
│ │         MISS? ↓                       │                  │
│ │                                       │                  │
│ │  2. Query Read DB (denormalized view)│                  │
│ │     ┌─────────────────────────────┐  │                  │
│ │     │ order_detail_view            │  │                  │
│ │     │ - orderId, status           │  │                  │
│ │     │ - customerName (from events)│  │                  │
│ │     │ - items + prices            │  │                  │
│ │     │ - paymentStatus             │  │                  │
│ │     │ - trackingId                │  │                  │
│ │     └─────────────────────────────┘  │                  │
│ │                                       │                  │
│ │  3. Store in Redis (TTL=5min)        │                  │
│ │                                       │                  │
│ │  4. Return response                  │                  │
│ └──────────────────────────────────────┘                  │
│                                                             │
│ Read model updated by projections:                        │
│ Kafka events → Projection handler → Update read DB       │
└────────────────────────────────────────────────────────────┘
```

---

## Code

### Complete Order Service Implementation:

```java
@RestController
@RequestMapping("/api/orders")
@Slf4j
public class OrderController {

    private final OrderCommandHandler commandHandler;
    private final OrderQueryHandler queryHandler;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        
        MDC.put("traceId", traceId);
        MDC.put("userId", userId);
        log.info("Creating order. IdempotencyKey={}", idempotencyKey);

        OrderResponse response = commandHandler.createOrder(
            userId, idempotencyKey, request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") String userId) {
        
        OrderDetailResponse response = queryHandler.getOrderDetail(orderId, userId);
        return ResponseEntity.ok(response);
    }
}
```

```java
@Service
@Transactional
public class OrderCommandHandler {

    private final IdempotencyStore idempotencyStore;
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final UserServiceClient userClient;

    public OrderResponse createOrder(
            String userId, String idempotencyKey, CreateOrderRequest request) {
        
        // 1. Idempotency
        Optional<OrderResponse> existing = idempotencyStore.find(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent hit: {}", idempotencyKey);
            return existing.get();
        }

        // 2. Validate user exists (sync call with circuit breaker)
        userClient.validateUser(userId);  // Throws if user invalid

        // 3. Create order + outbox event (SAME transaction)
        Order order = Order.builder()
            .id(UUID.randomUUID())
            .customerId(userId)
            .items(mapItems(request.getItems()))
            .totalAmount(calculateTotal(request.getItems()))
            .status(OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        orderRepository.save(order);

        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .eventType("OrderCreated")
            .payload(toJson(OrderCreatedEvent.from(order)))
            .build();
        
        outboxRepository.save(outboxEvent);

        // 4. Cache response for idempotency
        OrderResponse response = OrderResponse.from(order);
        idempotencyStore.save(idempotencyKey, response);

        log.info("Order created: {}", order.getId());
        return response;
    }
}
```

```java
// User Service Client with resilience
@Service
public class UserServiceClient {

    private final WebClient userWebClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "userFallback")
    @Retry(name = "userService")
    @TimeLimiter(name = "userService")
    public CompletableFuture<UserDto> validateUser(String userId) {
        return CompletableFuture.supplyAsync(() ->
            userWebClient.get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserDto.class)
                .block()
        );
    }

    private CompletableFuture<UserDto> userFallback(String userId, Throwable t) {
        log.warn("User service unavailable. Proceeding with cached user data.");
        // Allow order creation even if user service is down
        // We'll validate asynchronously later
        return CompletableFuture.completedFuture(
            UserDto.builder().userId(userId).verified(false).build());
    }
}
```

```java
// Saga — handle payment/inventory events
@Service
public class OrderSagaHandler {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        switch (event) {
            case PaymentCompletedEvent e -> {
                markStep(e.getOrderId(), "PAYMENT_COMPLETED");
                checkSagaCompletion(e.getOrderId());
            }
            case PaymentFailedEvent e -> {
                log.warn("Payment failed for order {}", e.getOrderId());
                cancelOrder(e.getOrderId(), "Payment failed: " + e.getReason());
            }
        }
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    @Transactional
    public void handleInventoryEvent(InventoryEvent event) {
        switch (event) {
            case StockReservedEvent e -> {
                markStep(e.getOrderId(), "STOCK_RESERVED");
                checkSagaCompletion(e.getOrderId());
            }
            case StockReservationFailedEvent e -> {
                log.warn("Stock failed for order {}", e.getOrderId());
                cancelOrder(e.getOrderId(), "Stock unavailable");
                // Publish compensation event for payment refund
                publishCompensation(e.getOrderId());
            }
        }
    }

    private void checkSagaCompletion(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.isPaymentCompleted() && order.isStockReserved()) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            
            outboxRepository.save(OutboxEvent.builder()
                .aggregateId(orderId.toString())
                .eventType("OrderConfirmed")
                .payload(toJson(new OrderConfirmedEvent(orderId)))
                .build());
            
            log.info("Order {} CONFIRMED", orderId);
        }
    }
}
```

---

## Interview Questions

1. **Design an order placement system with microservices.**
   - API Gateway (auth, rate limit) → Order Service (validates, saves order + outbox event) → Kafka (OrderCreated event) → Payment + Inventory (consume, process, publish results) → Order Service (confirm/cancel based on outcomes). Use transactional outbox for reliability, idempotency keys for safe retries, circuit breaker for sync calls.

2. **How do you handle failures in this system?**
   - Sync call failure (User Service): Circuit breaker → fallback. Kafka publish failure: Outbox pattern guarantees delivery. Payment failure: Saga compensation (refund + cancel). Service down: Messages wait in Kafka, processed when service recovers. Full outage: Dead letter queue for investigation.

3. **How do you ensure consistency across services?**
   - No distributed transactions. Use Saga pattern (choreography for simple flows, orchestration for complex). Transactional outbox for atomicity within a service. Idempotent consumers handle duplicates. Accept eventual consistency. Monitor saga completion.

4. **How to scale this system for Black Friday?**
   - Stateless services: HPA auto-scales based on load. Kafka: Increase partitions for higher throughput. Caching: Redis absorbs read load. Queue-based load leveling: Accept orders fast, process async. Database: Read replicas for queries. Pre-warm caches.

5. **How to add a new feature (e.g., loyalty points) without changing existing services?**
   - Create new Loyalty Service. Subscribe to existing events (OrderConfirmed → award points). No changes to Order/Payment services. This is the power of event-driven architecture — new features by subscribing to existing events.

---

## Production Checklist

```
Before going live, ensure you have:

RESILIENCE:
□ Circuit breaker on all external calls
□ Timeout on all HTTP calls (< 3s)
□ Retry with exponential backoff + jitter
□ Bulkhead for thread isolation
□ Fallback for critical paths
□ Rate limiting at gateway

DATA:
□ Database per service
□ Transactional outbox for event publishing
□ Idempotency keys on all state-changing operations
□ Saga pattern for distributed transactions
□ Dead letter queue for failed events

OBSERVABILITY:
□ Structured JSON logging
□ Correlation ID in all logs
□ Distributed tracing (OpenTelemetry)
□ RED metrics (Rate, Errors, Duration)
□ Alerting on error rate and latency
□ Dashboard per service

DEPLOYMENT:
□ Health checks (liveness + readiness)
□ Graceful shutdown
□ Rolling/canary deployments
□ Automated rollback
□ Feature flags for new features

SECURITY:
□ JWT at gateway
□ mTLS between services (or service mesh)
□ Secrets in vault (not in code)
□ Rate limiting per user
□ NetworkPolicy restricting communication

SCALABILITY:
□ Stateless services
□ HPA based on business metrics
□ Redis caching for hot data
□ Kafka for async processing
□ Read replicas for read-heavy queries
```

---

## Best Practices

1. **Event-driven by default** — Sync only when response needed immediately
2. **Outbox + Idempotency everywhere** — Reliable messaging foundation
3. **Observability from day 1** — Can't debug distributed systems without it
4. **Design for failure** — Every call can fail; have fallbacks
5. **Independent deployability** — If you can't deploy independently, boundaries are wrong
6. **Start simple, add patterns as needed** — Don't over-engineer for day one traffic
7. **Test failure scenarios** — Chaos engineering validates your resilience patterns
