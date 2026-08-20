# 34. Kafka + Microservices ⭐⭐⭐

---

## Theory

Kafka serves as the backbone for event-driven communication between microservices, enabling loose coupling, independent scaling, and resilience.

### Service-to-Service Communication

```
Synchronous (REST/gRPC):
  OrderService → HTTP → PaymentService → HTTP → InventoryService
  Pros: Simple, immediate response, familiar
  Cons: Tight coupling, cascading failures, latency accumulation

Asynchronous (Kafka):
  OrderService → Kafka → PaymentService
                       → InventoryService
                       → NotificationService
  Pros: Decoupled, resilient, scalable, event replay
  Cons: Eventual consistency, debugging complexity
```

### When to Use Each

| Use Case | Kafka (Async) | REST/gRPC (Sync) |
|----------|---------------|-------------------|
| Commands needing response | ✗ | ✓ (query endpoints) |
| Broadcasting events | ✓ | ✗ |
| Decoupled notifications | ✓ | ✗ |
| Data pipeline/streaming | ✓ | ✗ |
| User-facing request/response | ✗ | ✓ |
| Cross-service data sync | ✓ | ✗ |
| High throughput firehose | ✓ | ✗ |

### Event Contracts

```
Shared event schema between services (the "contract"):

// event-contracts module (shared library or Schema Registry)
public class OrderCreatedEvent {
    private String eventId;         // for idempotency
    private String orderId;         // aggregate ID
    private String userId;
    private BigDecimal totalAmount;
    private List<OrderItem> items;
    private Instant createdAt;
    private int version;            // schema version
}

Contract ownership:
  - Producer OWNS the event schema
  - Consumer must handle current and older versions
  - Use Schema Registry for compatibility enforcement
  - Breaking changes: versioned topics (orders-v1, orders-v2)
```

### Loose Coupling

```
Tightly coupled (synchronous):
  OrderService KNOWS about PaymentService, InventoryService, NotificationService
  If any downstream service is down → OrderService fails

Loosely coupled (event-driven):
  OrderService publishes OrderCreated event — doesn't know who consumes
  PaymentService subscribes independently
  InventoryService subscribes independently
  NotificationService subscribes independently
  
  If NotificationService is down:
  - OrderService unaffected (fire and forget)
  - Messages queue in Kafka
  - NotificationService processes backlog when it recovers
```

### Eventual Consistency

```
With sync communication:
  Order created + payment charged + stock reserved = consistent immediately
  BUT: if payment service down, order can't be created at all

With async (Kafka):
  Order created immediately (local state = PENDING)
  Payment charged asynchronously (event: PaymentProcessed)
  Stock reserved asynchronously (event: StockReserved)
  Order updated to CONFIRMED after all events received
  
  Window of inconsistency:
  - Order is PENDING for seconds/minutes
  - UI shows "Processing..." state
  - Eventually consistent (all services align)
  
  This is acceptable for most business scenarios!
```

### Microservice Communication Patterns

```
1. Event Notification:
   OrderService publishes minimal event
   Consumer fetches full data via API if needed
   Keeps events small, reduces coupling to event schema

2. Event-Carried State Transfer:
   Event contains ALL needed data
   Consumer doesn't need to call back to producer
   More data in events, but fully decoupled (no sync calls)

3. Domain Events:
   Business-meaningful events (OrderPlaced, PaymentReceived)
   Named in ubiquitous language (DDD)
   Consumers react to business facts

4. Integration Events:
   Technical events for system integration
   May combine data from multiple aggregates
   Designed for specific consumer needs
```

### Topic Design for Microservices

```
Option 1: One topic per event type
  orders-created, orders-paid, orders-shipped, orders-cancelled
  Pros: Consumers subscribe to exactly what they need
  Cons: Many topics to manage

Option 2: One topic per aggregate
  orders (contains: Created, Paid, Shipped, Cancelled events)
  Pros: Fewer topics, event ordering per entity (same key)
  Cons: Consumers receive events they don't care about

Option 3: One topic per bounded context/domain
  order-domain-events (all events from order service)
  Pros: Simplest topic management
  Cons: High volume, irrelevant events consumed

Recommendation: One topic per aggregate for most cases
  Key = aggregateId (orderId) → ordering guaranteed per entity
```

---

## Code

### Producer Service

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. Business logic
        Order order = Order.create(request);
        orderRepository.save(order);
        
        // 2. Publish event (via outbox for consistency)
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .orderId(order.getId())
            .userId(request.getUserId())
            .items(order.getItems())
            .totalAmount(order.getTotalAmount())
            .createdAt(Instant.now())
            .build();
        
        outboxRepository.save(OutboxEvent.from(event));
        
        return OrderResponse.from(order);
    }
}
```

### Consumer Service

```java
@Component
@Slf4j
public class PaymentEventHandler {

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedRepo;

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    @Transactional
    public void handleOrderEvent(@Payload OrderCreatedEvent event, Acknowledgment ack) {
        // Idempotency check
        if (processedRepo.existsByEventId(event.getEventId())) {
            ack.acknowledge();
            return;
        }
        
        // Process
        paymentService.processPayment(event.getOrderId(), event.getTotalAmount());
        
        // Mark processed + acknowledge
        processedRepo.save(new ProcessedEvent(event.getEventId()));
        ack.acknowledge();
        
        // Publish own event
        kafkaTemplate.send("payment-events", event.getOrderId(), 
            new PaymentProcessedEvent(event.getOrderId(), Instant.now()));
    }
}
```

### Saga Orchestrator

```java
@Component
@Slf4j
public class OrderSagaOrchestrator {

    @KafkaListener(topics = "order-saga-replies", groupId = "saga-orchestrator")
    public void handleSagaReply(SagaReply reply) {
        OrderSaga saga = sagaRepository.findById(reply.getSagaId()).orElseThrow();
        
        switch (saga.getCurrentStep()) {
            case PAYMENT_PENDING:
                if (reply.isSuccess()) {
                    saga.setCurrentStep(SagaStep.INVENTORY_PENDING);
                    kafkaTemplate.send("inventory-commands", 
                        new ReserveStockCommand(saga.getOrderId(), saga.getItems()));
                } else {
                    // Compensate: cancel order
                    saga.setStatus(SagaStatus.COMPENSATING);
                    kafkaTemplate.send("order-commands",
                        new CancelOrderCommand(saga.getOrderId()));
                }
                break;
            
            case INVENTORY_PENDING:
                if (reply.isSuccess()) {
                    saga.setStatus(SagaStatus.COMPLETED);
                    kafkaTemplate.send("order-commands",
                        new ConfirmOrderCommand(saga.getOrderId()));
                } else {
                    // Compensate: refund payment
                    saga.setStatus(SagaStatus.COMPENSATING);
                    kafkaTemplate.send("payment-commands",
                        new RefundPaymentCommand(saga.getOrderId()));
                }
                break;
        }
        sagaRepository.save(saga);
    }
}
```

---

## Interview Questions

### Q1: How do you handle the case where a consumer service is down for hours?

**A:** Kafka's retention handles this naturally:
1. Messages remain in topic for configured retention (default 7 days)
2. Consumer's committed offset is stored — it remembers where it left off
3. When service comes back up: resumes from last committed offset
4. Processes backlog at its own pace (may take time to catch up)
5. Monitor: consumer lag metrics alert you to growing backlog
6. Scale: temporarily increase consumer instances to process faster
7. Key advantage over HTTP: no lost messages, no timeout failures

### Q2: How do you maintain data consistency across microservices without distributed transactions?

**A:** Use the **Saga pattern** with **eventual consistency**:
1. Each service owns its data and publishes domain events
2. Other services react to events and update their own state
3. Outbox pattern ensures DB + event are atomic within each service
4. Saga coordinates multi-service workflows with compensation on failure
5. Idempotent consumers handle message replay safely
6. Accept temporary inconsistency (order in PENDING state briefly)
7. Business invariants enforced within each service's transaction boundary

### Q3: How would you migrate from synchronous REST to event-driven with Kafka?

**A:** Incremental strangler pattern:
1. Identify a non-critical flow to start (e.g., notifications)
2. Keep existing REST call, ADD Kafka event publishing alongside
3. Build new consumer service that reads from Kafka
4. Validate both produce same result (dual-write validation period)
5. Remove REST call, rely solely on Kafka
6. Repeat for next flow
7. Critical: implement idempotency from day one, use outbox pattern
8. Don't migrate request-response (queries) — only fire-and-forget flows

---

## Best Practices

1. **Use outbox pattern** for publishing events from services
2. **Design coarse-grained events** — include enough data for consumers to act
3. **One topic per aggregate** with entity ID as key
4. **Every consumer must be idempotent** — expect duplicates
5. **Keep sync for queries, async for commands/events**
6. **Version your events** — enable schema evolution
7. **Monitor consumer lag per service** — detect falling-behind services
8. **Document event contracts** — Schema Registry or shared modules

---

## Related Topics

- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
- [35. Kafka + Database](./35-kafka-database.md)
- [25. Kafka + Spring Boot](./25-kafka-spring-boot.md)
- [26. Kafka Error Handling](./26-kafka-error-handling.md)
