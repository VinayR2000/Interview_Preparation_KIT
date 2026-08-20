# Topic 28: SQL & Microservices

## Theory

### Database per Service Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│            DATABASE PER SERVICE (Fundamental Rule)                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  MONOLITH (shared DB):                                           │
│  ┌──────────────────────────────────┐                           │
│  │         Application              │                           │
│  │  ┌───────┐ ┌───────┐ ┌───────┐  │                           │
│  │  │Order  │ │Payment│ │Inventory│ │                           │
│  │  │Module │ │Module │ │Module  │  │                           │
│  │  └───┬───┘ └───┬───┘ └───┬───┘  │                           │
│  └──────┼──────────┼────────┼───────┘                           │
│         └──────────┼────────┘                                    │
│                ┌───▼───┐                                         │
│                │  DB   │  ← Single shared database               │
│                └───────┘  ← Tight coupling!                      │
│                                                                  │
│  MICROSERVICES (separate DBs):                                   │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐                   │
│  │  Order    │  │  Payment  │  │ Inventory │                   │
│  │  Service  │  │  Service  │  │ Service   │                   │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘                   │
│        │               │               │                         │
│  ┌─────▼─────┐  ┌─────▼─────┐  ┌─────▼─────┐                   │
│  │ Order DB  │  │Payment DB │  │Inventory  │                   │
│  │(PostgreSQL)│  │(PostgreSQL)│  │   DB      │                   │
│  └───────────┘  └───────────┘  └───────────┘                   │
│                                                                  │
│  WHY SEPARATE DATABASES?                                         │
│  ✓ Independent deployment                                       │
│  ✓ Independent scaling                                          │
│  ✓ Technology choice per service                                │
│  ✓ Fault isolation                                              │
│  ✓ Schema changes don't affect other services                   │
│                                                                  │
│  CHALLENGES:                                                     │
│  ✗ No cross-service JOINs                                       │
│  ✗ No cross-service transactions (no ACID across services)      │
│  ✗ Data consistency is eventually consistent                    │
│  ✗ Data duplication between services                            │
│  ✗ Complex debugging                                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Why Shared Database is an Anti-Pattern

```
SHARED DATABASE PROBLEMS:
─────────────────────────
1. TIGHT COUPLING: Service A changes a column → Service B breaks
2. NO INDEPENDENT DEPLOY: Schema migration requires coordinated release
3. SCALING: Can't scale Order DB independently of Payment DB
4. SINGLE POINT OF FAILURE: DB goes down → ALL services down
5. TECHNOLOGY LOCK-IN: All services forced to use same DB technology
6. CONTENTION: Different access patterns compete for same resources
```

---

## Distributed Transactions

### The Problem

```
┌─────────────────────────────────────────────────────────────────┐
│         WHY DISTRIBUTED TRANSACTIONS ARE HARD                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Place Order (needs to be atomic across 3 services):             │
│  1. Order Service: Create order record                           │
│  2. Payment Service: Charge customer                             │
│  3. Inventory Service: Reserve items                             │
│                                                                  │
│  What if Step 2 succeeds but Step 3 fails?                       │
│  → Order exists, payment charged, but items not reserved!        │
│  → INCONSISTENT STATE                                            │
│                                                                  │
│  Can't use regular ACID transaction:                             │
│  • Each service has its own database                             │
│  • Different transaction managers                                 │
│  • Network can fail between services                             │
│                                                                  │
│  SOLUTIONS:                                                      │
│  1. Saga Pattern (most common)                                   │
│  2. Two-Phase Commit (rarely used in microservices)              │
│  3. Eventual Consistency with Events                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Saga Pattern

### Internal Working

```
┌─────────────────────────────────────────────────────────────────┐
│                    SAGA PATTERN                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  A saga = sequence of LOCAL transactions                         │
│  Each step has a COMPENSATING action (undo)                      │
│                                                                  │
│  ORCHESTRATION SAGA (central coordinator):                       │
│                                                                  │
│  ┌──────────────┐                                               │
│  │  Saga        │                                               │
│  │  Orchestrator│                                               │
│  └──────┬───────┘                                               │
│         │                                                        │
│    1. Create Order                                               │
│         │───────────────▶ Order Service: createOrder()           │
│         │◀─── success ───                                        │
│    2. Process Payment                                            │
│         │───────────────▶ Payment Service: charge()              │
│         │◀─── success ───                                        │
│    3. Reserve Inventory                                          │
│         │───────────────▶ Inventory Service: reserve()           │
│         │◀─── FAILURE ───                                        │
│                                                                  │
│  COMPENSATING ACTIONS (rollback):                                │
│    4. Refund Payment                                             │
│         │───────────────▶ Payment Service: refund()              │
│    5. Cancel Order                                               │
│         │───────────────▶ Order Service: cancel()                │
│                                                                  │
│                                                                  │
│  CHOREOGRAPHY SAGA (event-driven, no coordinator):               │
│                                                                  │
│  Order Service ──(OrderCreated)──▶ Payment Service               │
│                                         │                        │
│                                    (PaymentProcessed)            │
│                                         │                        │
│                                         ▼                        │
│                                   Inventory Service              │
│                                         │                        │
│                                    (InventoryReserved)           │
│                                         │                        │
│                                         ▼                        │
│                                   Order Service                  │
│                                    → confirmOrder()              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Code — Orchestration Saga

```java
// Saga Orchestrator
@Service
@Slf4j
public class CreateOrderSaga {

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private InventoryService inventoryService;

    public OrderResult execute(CreateOrderCommand command) {
        OrderSagaState state = new OrderSagaState();
        
        try {
            // Step 1: Create Order (PENDING)
            Order order = orderService.createOrder(command);
            state.setOrderId(order.getId());
            state.setOrderCreated(true);
            
            // Step 2: Process Payment
            PaymentResult payment = paymentService.charge(
                command.getCustomerId(), order.getTotalAmount());
            state.setPaymentId(payment.getId());
            state.setPaymentProcessed(true);
            
            // Step 3: Reserve Inventory
            inventoryService.reserve(order.getItems());
            state.setInventoryReserved(true);
            
            // Step 4: Confirm Order
            orderService.confirmOrder(order.getId());
            
            return OrderResult.success(order);
            
        } catch (Exception e) {
            log.error("Saga failed at step: {}", state.getCurrentStep(), e);
            compensate(state);
            return OrderResult.failure(e.getMessage());
        }
    }
    
    private void compensate(OrderSagaState state) {
        // Undo in REVERSE order
        if (state.isInventoryReserved()) {
            try {
                inventoryService.cancelReservation(state.getOrderId());
            } catch (Exception e) {
                log.error("Compensation failed: cancel reservation", e);
                // Alert operations team — manual intervention needed
            }
        }
        if (state.isPaymentProcessed()) {
            try {
                paymentService.refund(state.getPaymentId());
            } catch (Exception e) {
                log.error("Compensation failed: refund", e);
            }
        }
        if (state.isOrderCreated()) {
            try {
                orderService.cancelOrder(state.getOrderId());
            } catch (Exception e) {
                log.error("Compensation failed: cancel order", e);
            }
        }
    }
}
```

---

## Outbox Pattern

### Theory

```
┌─────────────────────────────────────────────────────────────────┐
│                    OUTBOX PATTERN                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PROBLEM: Dual Write                                             │
│  ───────────────────                                             │
│  @Transactional                                                  │
│  void createOrder(request) {                                     │
│    orderRepo.save(order);        // Write to DB    ← succeeds   │
│    kafka.send(orderCreatedEvent); // Write to Kafka ← may fail! │
│  }                                                               │
│  // If Kafka send fails: DB has order, but no event published   │
│  // Other services never learn about the order!                  │
│                                                                  │
│  SOLUTION: Outbox Table                                          │
│  ──────────────────────                                          │
│  @Transactional                                                  │
│  void createOrder(request) {                                     │
│    orderRepo.save(order);         // Write to DB                 │
│    outboxRepo.save(outboxEvent);  // Write to SAME DB            │
│  }                                                               │
│  // ATOMIC! Both in same transaction.                            │
│  // Separate process reads outbox → publishes to Kafka           │
│                                                                  │
│  ┌────────────────────────────────────────────────────┐         │
│  │ outbox_events table:                                │         │
│  │ id | aggregate_type | aggregate_id | event_type    │         │
│  │    | payload (JSON) | created_at   | processed     │         │
│  └────────────────────────────────────────────────────┘         │
│                                                                  │
│  DELIVERY METHODS:                                               │
│  1. Polling: Background job reads unprocessed events             │
│  2. CDC (Debezium): Tails transaction log, forwards to Kafka    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Code — Full Outbox Implementation

```java
// Outbox Entity
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String aggregateType; // "Order", "Payment"
    
    @Column(nullable = false)
    private String aggregateId;   // "12345"
    
    @Column(nullable = false)
    private String eventType;     // "ORDER_CREATED"
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;       // JSON
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private boolean processed = false;
    
    private Instant processedAt;
}

// Service — writes business data + outbox event in SAME transaction
@Service
public class OrderService {

    @Autowired private OrderRepository orderRepo;
    @Autowired private OutboxRepository outboxRepo;
    @Autowired private ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.from(request);
        order = orderRepo.save(order);
        
        // Write event to outbox (SAME transaction — atomic)
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(order.getId().toString())
            .eventType("ORDER_CREATED")
            .payload(toJson(new OrderCreatedPayload(order)))
            .createdAt(Instant.now())
            .build();
        outboxRepo.save(event);
        
        return order;
    }
}

// Outbox Relay — polls and publishes
@Component
@Slf4j
public class OutboxRelay {

    @Autowired private OutboxRepository outboxRepo;
    @Autowired private KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 1000) // Every 1 second
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepo
            .findTop100ByProcessedFalseOrderByCreatedAt();
        
        for (OutboxEvent event : events) {
            try {
                kafka.send(
                    event.getAggregateType(), // topic
                    event.getAggregateId(),   // key (for ordering)
                    event.getPayload()        // value
                ).get(); // Wait for ack
                
                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getId(), e);
                break; // Stop — maintain ordering, retry next cycle
            }
        }
    }
    
    // Cleanup old processed events
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void cleanupProcessedEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        outboxRepo.deleteByProcessedTrueAndProcessedAtBefore(cutoff);
    }
}
```

---

## Change Data Capture (CDC)

```
┌─────────────────────────────────────────────────────────────────┐
│                 CDC with DEBEZIUM                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐    ┌──────────┐    ┌──────────┐               │
│  │ PostgreSQL  │    │ Debezium │    │  Kafka   │               │
│  │             │───▶│ Connector│───▶│          │               │
│  │ WAL (tx log)│    │          │    │  Topics  │               │
│  └─────────────┘    └──────────┘    └────┬─────┘               │
│                                          │                       │
│                              ┌───────────┼───────────┐           │
│                              │           │           │           │
│                         ┌────▼───┐  ┌────▼───┐  ┌───▼────┐     │
│                         │Search  │  │Analytics│  │Other   │     │
│                         │Service │  │Service │  │Service │     │
│                         └────────┘  └────────┘  └────────┘     │
│                                                                  │
│  HOW IT WORKS:                                                   │
│  1. Debezium reads PostgreSQL WAL (logical replication slot)     │
│  2. Converts row changes to events (JSON/Avro)                   │
│  3. Publishes to Kafka topics (one per table)                    │
│  4. Consumers process changes in real-time                       │
│                                                                  │
│  ADVANTAGES over Polling:                                        │
│  • No polling delay (real-time)                                  │
│  • No polling overhead on source DB                              │
│  • Captures ALL changes (including deletes)                      │
│  • No "processed" column needed                                  │
│  • Guaranteed ordering                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Eventual Consistency

### Theory

```
┌─────────────────────────────────────────────────────────────────┐
│              EVENTUAL CONSISTENCY                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  In microservices, you ACCEPT that data is temporarily           │
│  inconsistent between services.                                  │
│                                                                  │
│  EXAMPLE:                                                        │
│  1. Order Service creates order → publishes OrderCreated event   │
│  2. Inventory Service receives event → decrements stock          │
│  3. Between steps 1 and 2: order exists but stock isn't updated │
│     → TEMPORARILY INCONSISTENT (eventually consistent)           │
│                                                                  │
│  CONSISTENCY WINDOW:                                             │
│  • Typically milliseconds to seconds                             │
│  • Depends on: network, consumer lag, processing time            │
│                                                                  │
│  HANDLING INCONSISTENCY:                                         │
│  • Design UX to handle it (show "processing..." status)         │
│  • Retry mechanisms for failed processing                        │
│  • Dead letter queues for permanently failed events              │
│  • Reconciliation jobs (periodic consistency checks)             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Idempotency

### Code — Idempotent Consumer

```java
// PROBLEM: Kafka delivers message more than once (at-least-once delivery)
// Consumer must handle duplicates safely

@Service
public class InventoryEventConsumer {

    @Autowired private ProcessedEventRepository processedRepo;
    @Autowired private InventoryRepository inventoryRepo;

    @KafkaListener(topics = "order-events")
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        String eventId = event.getEventId(); // Unique per event
        
        // Idempotency check
        if (processedRepo.existsByEventId(eventId)) {
            log.info("Event already processed: {}", eventId);
            return; // Skip — already handled
        }
        
        // Process event
        for (OrderItem item : event.getItems()) {
            int updated = inventoryRepo.decrementStock(
                item.getProductId(), item.getQuantity());
            if (updated == 0) {
                throw new InsufficientStockException(item.getProductId());
            }
        }
        
        // Mark as processed (in SAME transaction as business logic)
        processedRepo.save(new ProcessedEvent(eventId, Instant.now()));
    }
}

// Schema:
// CREATE TABLE processed_events (
//     event_id VARCHAR(255) PRIMARY KEY,
//     processed_at TIMESTAMP NOT NULL
// );
```

---

## Data Synchronization Patterns

### API Composition (Query Side)

```java
// When you need data from multiple services for a single response
@RestController
public class OrderDashboardController {

    @Autowired private OrderServiceClient orderClient;
    @Autowired private CustomerServiceClient customerClient;
    @Autowired private ProductServiceClient productClient;

    @GetMapping("/api/dashboard/orders/{orderId}")
    public OrderDashboardDTO getOrderDashboard(@PathVariable Long orderId) {
        // Parallel calls to different services
        CompletableFuture<OrderDTO> orderFuture = 
            CompletableFuture.supplyAsync(() -> orderClient.getOrder(orderId));
        CompletableFuture<CustomerDTO> customerFuture = 
            orderFuture.thenCompose(order -> 
                CompletableFuture.supplyAsync(() -> 
                    customerClient.getCustomer(order.getCustomerId())));
        CompletableFuture<List<ProductDTO>> productsFuture = 
            orderFuture.thenCompose(order -> 
                CompletableFuture.supplyAsync(() -> 
                    productClient.getProducts(order.getProductIds())));
        
        // Compose response
        return CompletableFuture.allOf(orderFuture, customerFuture, productsFuture)
            .thenApply(v -> new OrderDashboardDTO(
                orderFuture.join(),
                customerFuture.join(),
                productsFuture.join()
            )).join();
    }
}
```

### Data Replication via Events

```java
// Service maintains LOCAL copy of data it needs from other services
// Updated via events (eventual consistency)

// Inventory Service keeps local copy of product names (from Product Service)
@Entity
@Table(name = "product_cache")
public class ProductCache {
    @Id
    private Long productId;
    private String productName;
    private BigDecimal price;
    private Instant lastUpdated;
}

@KafkaListener(topics = "product-events")
@Transactional
public void handleProductEvent(ProductEvent event) {
    switch (event.getType()) {
        case "PRODUCT_CREATED":
        case "PRODUCT_UPDATED":
            productCacheRepo.save(ProductCache.from(event));
            break;
        case "PRODUCT_DELETED":
            productCacheRepo.deleteById(event.getProductId());
            break;
    }
}
```

---

## Dry Run — Saga Compensation

```
Scenario: Order creation fails at inventory step

T1: User requests order creation
    → Saga orchestrator starts

T2: Step 1 — Create Order (PENDING)
    → Order Service: INSERT INTO orders (status='PENDING') ✓
    → Returns order_id = 42

T3: Step 2 — Process Payment
    → Payment Service: INSERT INTO payments (order_id=42, amount=100) ✓
    → External payment gateway: charge $100 ✓
    → Returns payment_id = 789

T4: Step 3 — Reserve Inventory
    → Inventory Service: UPDATE stock SET quantity = quantity - 3 WHERE product_id = 5 AND quantity >= 3
    → Returns 0 rows affected (stock = 2, need 3)
    → THROWS InsufficientStockException ✗

T5: SAGA FAILURE — Start compensation

T6: Compensate Step 2 — Refund Payment
    → Payment Service: UPDATE payments SET status='REFUNDED' WHERE id = 789
    → External payment gateway: refund $100
    → ✓ Refund processed

T7: Compensate Step 1 — Cancel Order
    → Order Service: UPDATE orders SET status='CANCELLED' WHERE id = 42
    → ✓ Order cancelled

T8: Return failure to user
    → "Order failed: Insufficient stock for product X"

FINAL STATE:
  Order: CANCELLED
  Payment: REFUNDED
  Inventory: UNCHANGED
  → CONSISTENT (no partial state)
```

---

## Interview Questions and Answers

### Q1: Why can't you use regular database transactions across microservices?

**Answer:**

Each microservice has its own database, typically on different servers:
1. **No shared transaction manager** — can't span multiple databases atomically
2. **Network boundaries** — a commit on one DB can't guarantee commit on another
3. **Coupling** — distributed transactions (2PC) create tight coupling between services
4. **Availability** — if one service is down, the entire transaction blocks
5. **Performance** — 2PC requires locks held across network calls (high latency)

Instead, use:
- Saga pattern (compensating transactions)
- Eventual consistency with events
- Outbox pattern for reliable event publishing

### Q2: Explain the difference between orchestration and choreography sagas.

**Answer:**

**Orchestration** (central coordinator):
- Saga orchestrator tells each service what to do
- Easier to understand flow, centralized error handling
- Risk: orchestrator is a single point of failure
- Good for: complex flows with many steps

**Choreography** (event-driven):
- Each service reacts to events and publishes its own events
- No central point of failure, more decoupled
- Harder to understand and debug (flow is implicit)
- Risk: cyclic dependencies, harder to track progress
- Good for: simple flows with few steps

### Q3: How do you handle duplicate messages in event-driven microservices?

**Answer:**

**Idempotent consumers:**
1. Store event ID in a `processed_events` table
2. Before processing, check if event was already processed
3. If yes, skip (return success without reprocessing)
4. If no, process AND mark as processed in SAME transaction

**Additional strategies:**
- Use idempotent operations (e.g., `SET stock = 5` instead of `SET stock = stock - 1`)
- Natural idempotency (creating with unique key — second attempt hits unique constraint)
- Deduplication at broker level (Kafka exactly-once semantics)

### Q4: What is the Outbox Pattern and when do you use it?

**Answer:**

The outbox pattern solves the "dual write" problem:

**Problem:** Need to save to database AND publish event, but they're separate systems (can't be atomic).

**Solution:** Write the event to an "outbox" table in the same database transaction. A separate process reads from outbox and publishes to the message broker.

**When to use:**
- Any time you need to reliably publish an event after a database write
- Cross-service communication that must be guaranteed
- When Kafka/RabbitMQ might be temporarily unavailable

**Delivery guarantee:** At-least-once (consumers must be idempotent)

### Q5: How do you query data that spans multiple services?

**Answer:**

**Option 1 — API Composition:**
- Gateway/BFF calls multiple services and combines results
- Simple but creates runtime dependencies
- Higher latency (multiple network calls)

**Option 2 — CQRS Read Model:**
- Build denormalized read model from events
- Fast queries (pre-joined data)
- Eventually consistent
- Good for complex dashboards/reports

**Option 3 — Data Replication:**
- Each service maintains local copy of data it needs
- Updated via events
- Fast queries (local data)
- Eventual consistency

---

## Follow-up Questions and Answers

### Q: How do you handle saga failures when compensation also fails?

**Answer:**

This is a critical edge case:
1. **Retry with backoff** — most compensation failures are transient
2. **Dead letter queue** — store failed compensations for manual processing
3. **Alerting** — notify operations team immediately
4. **Reconciliation job** — periodic process that detects and fixes inconsistencies
5. **Manual intervention** — human operator resolves via admin tools

Design compensations to be idempotent so retries are safe.

### Q: What's the difference between Outbox + Polling vs Outbox + CDC?

**Answer:**

| Aspect | Polling | CDC (Debezium) |
|---|---|---|
| Latency | 1-5 seconds (poll interval) | Near real-time (~100ms) |
| DB Load | Periodic queries on outbox | Reads WAL (minimal load) |
| Infrastructure | Simple (just scheduler) | Complex (Debezium + Kafka Connect) |
| Ordering | Guaranteed (ORDER BY created_at) | Guaranteed (WAL order) |
| Cleanup | Must delete processed events | Events auto-removed (WAL retention) |
| Complexity | Low | Medium-High |

**Recommendation:** Start with polling (simpler). Move to CDC when you need lower latency or higher throughput.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---|---|---|
| Shared database between services | Tight coupling, no independent deploy | Database per service |
| Synchronous calls for data consistency | Availability problems | Async events + eventual consistency |
| No idempotency in consumers | Duplicate processing | Idempotency keys + processed_events table |
| Missing compensation in saga | Partial state on failure | Design compensating action for every step |
| Dual write (DB + broker) | Data loss or inconsistency | Outbox pattern |
| Not handling out-of-order events | Incorrect state | Use event versioning or timestamps |
| No dead letter queue | Lost events silently | DLQ + alerting + manual replay |
| Tight coupling via sync API calls | Cascading failures | Events for data propagation |

---

## Best Practices

1. **One database per service** — no exceptions for production systems
2. **Use the outbox pattern** for reliable event publishing
3. **Make all consumers idempotent** — messages will be delivered more than once
4. **Prefer choreography for simple flows**, orchestration for complex ones
5. **Design compensating actions** for every saga step
6. **Implement dead letter queues** for failed message processing
7. **Use correlation IDs** to trace requests across services
8. **Run reconciliation jobs** to detect and fix inconsistencies
9. **Start with eventual consistency** — strong consistency across services is rarely needed
10. **Document data ownership** — one service owns each piece of data

---

## Production Considerations

### Event Schema Evolution

```json
// Version 1 of OrderCreated event
{
  "eventType": "ORDER_CREATED",
  "version": 1,
  "orderId": "123",
  "customerId": "456",
  "amount": 100.00
}

// Version 2 (added currency field — backward compatible)
{
  "eventType": "ORDER_CREATED",
  "version": 2,
  "orderId": "123",
  "customerId": "456",
  "amount": 100.00,
  "currency": "USD"  // New field with default
}

// Rules:
// • Add new fields (backward compatible)
// • Don't remove fields
// • Don't rename fields
// • Don't change field types
// • Use schema registry (Avro/Protobuf) for enforcement
```

### Monitoring Cross-Service Data Consistency

```java
// Reconciliation job — runs periodically
@Scheduled(cron = "0 0 * * * *") // Every hour
public void reconcileOrdersAndPayments() {
    // Find orders that should have payments but don't
    List<Order> unpairedOrders = orderRepo
        .findConfirmedOrdersWithoutPayment(oneHourAgo);
    
    for (Order order : unpairedOrders) {
        log.warn("Inconsistency detected: Order {} has no payment", order.getId());
        // Alert + attempt repair
        sagaRepairService.repairOrder(order.getId());
    }
}
```

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation
- Topic 23: Spring Transactions
- Topic 26: Database Architecture & System Design
- Topic 27: SQL Interview Scenarios
- Topic 29: Production-Level Topics (migrations, backup)
