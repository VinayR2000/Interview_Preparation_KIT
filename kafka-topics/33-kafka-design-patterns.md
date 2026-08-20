# 33. Kafka Design Patterns ⭐⭐⭐

---

## Theory

Design patterns for building reliable, scalable event-driven systems with Kafka.

### Event-Driven Architecture (EDA)

```
Services communicate through events instead of direct API calls:

Synchronous (traditional):
  OrderService → REST → InventoryService → REST → NotificationService
  (coupled, cascading failures)

Event-Driven:
  OrderService → publishes "OrderCreated" → Kafka
  InventoryService → consumes "OrderCreated" → reserves stock
  NotificationService → consumes "OrderCreated" → sends email
  AnalyticsService → consumes "OrderCreated" → updates dashboard
  (decoupled, independent, resilient)
```

### Event Sourcing

```
Store ALL state changes as a sequence of events (instead of current state).

Traditional: UPDATE orders SET status='PAID' WHERE id=123
Event Sourcing: Append events → [OrderCreated, OrderPaid, OrderShipped]

Benefits:
  - Complete audit trail (every change recorded)
  - Time travel (rebuild state at any point)
  - Event replay (reprocess events to fix bugs)
  - Multiple projections from same events

Kafka as event store:
  - Topic with compaction=delete, long retention
  - Each entity's events share same key (partition ordering)
  - Read all events for an entity → rebuild current state
```

### CQRS (Command Query Responsibility Segregation)

```
Separate write model (commands) from read model (queries):

┌──────────────┐        ┌───────────────────┐
│ Command Side │        │   Query Side       │
│              │        │                    │
│ Write to     │ event  │ Optimized read     │
│ event store  │───────►│ models (different  │
│ (Kafka)      │        │ DBs, denormalized) │
└──────────────┘        └───────────────────┘

Write: Domain logic → events → Kafka topic
Read:  Consumer → transforms events → read-optimized DB (Elasticsearch, Redis)

Benefits: Independent scaling of reads/writes, optimized query models
```

### Outbox Pattern ⭐⭐⭐

```
Problem: Need to update DB AND publish to Kafka atomically.
  If DB commit succeeds but Kafka send fails → inconsistency
  If Kafka send succeeds but DB commit fails → inconsistency

Solution: Write event to an OUTBOX TABLE within the DB transaction.
  A separate process reads outbox and publishes to Kafka.

┌─────────────────────────────────────────────────────────┐
│ Service                                                   │
│                                                           │
│ @Transactional                                           │
│ {                                                         │
│   orderRepo.save(order);              // 1. Save entity  │
│   outboxRepo.save(outboxEvent);       // 2. Save event   │
│ }  // Both in SAME DB transaction (atomic!)              │
│                                                           │
│ OutboxPublisher (scheduled/CDC):                         │
│   Read unpublished events → Send to Kafka → Mark sent   │
└─────────────────────────────────────────────────────────┘

Guarantees: At-least-once delivery to Kafka (idempotent consumer handles duplicates)
```

### Transactional Outbox (Implementation)

```java
// 1. Entity + Outbox in same transaction
@Service
public class OrderService {
    
    @Transactional
    public Order createOrder(CreateOrderRequest req) {
        Order order = orderRepository.save(buildOrder(req));
        
        outboxRepository.save(OutboxEvent.builder()
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .eventType("OrderCreated")
            .payload(toJson(new OrderCreatedEvent(order)))
            .createdAt(Instant.now())
            .published(false)
            .build());
        
        return order;
    }
}

// 2. Publisher reads outbox and sends to Kafka
@Component
public class OutboxPublisher {
    
    @Scheduled(fixedDelay = 100)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepo.findByPublishedFalse(PageRequest.of(0, 100));
        for (OutboxEvent event : events) {
            kafkaTemplate.send(
                event.getAggregateType().toLowerCase() + "-events",
                event.getAggregateId(),
                event.getPayload()
            );
            event.setPublished(true);
        }
    }
}
```

### CDC (Change Data Capture)

```
Alternative to outbox: Capture database changes directly from transaction log.

Database (binlog/WAL) → Debezium → Kafka

Benefits over outbox:
  - No outbox table needed
  - Captures ALL changes (not just those with explicit events)
  - Lower latency (reads directly from DB log)
  - No polling (event-driven)

Trade-offs:
  - Depends on DB-specific log format
  - Schema changes need careful handling
  - Operational complexity (Debezium/Connect cluster)
```

### Saga Pattern

Manage distributed transactions across multiple services.

```
Choreography Saga (event-driven):
  OrderService: OrderCreated →
  PaymentService: PaymentProcessed → (listens to OrderCreated)
  InventoryService: StockReserved → (listens to PaymentProcessed)
  ShippingService: ShipmentCreated → (listens to StockReserved)
  
  Compensation (on failure):
  PaymentService: PaymentFailed →
  OrderService: OrderCancelled (listens to PaymentFailed)

Orchestration Saga (centralized):
  SagaOrchestrator: publishes commands, listens for responses
  Step 1: Send "ProcessPayment" command → PaymentService
  Step 2: Receive "PaymentProcessed" → Send "ReserveStock" → InventoryService
  Step 3: Receive "StockReserved" → Send "CreateShipment" → ShippingService
  Failure: Send compensation commands in reverse order
```

### Idempotent Consumer Pattern

```
Ensure processing a message multiple times has same effect as once:

1. Deduplication by event ID (check processed_events table)
2. Conditional writes (UPDATE ... WHERE version < newVersion)
3. Natural idempotency (SET x=5 instead of ADD x+5)

Critical for at-least-once delivery (Kafka's default guarantee)
```

### Retry Pattern

```
On transient failure, retry with backoff before giving up:

Main Topic → [process] → fail → Retry Topic 1 (1s delay)
                                → fail → Retry Topic 2 (10s delay)
                                → fail → Retry Topic 3 (60s delay)
                                → fail → Dead Letter Topic (manual review)
```

### Dead Letter Pattern

```
Messages that fail after all retries → quarantined in DLT:
- Preserves failed message with error context
- Main consumer not blocked
- Operations team reviews and reprocesses
- Enables alerting on failure rates
```

### Competing Consumers

```
Multiple consumers in same group compete for messages:
- Each partition → exactly one consumer
- Load balanced across consumer instances
- Add consumers to scale (up to partition count)
- If consumer dies → partitions redistributed

This is Kafka's native consumer group model:
  Topic (6 partitions) → Consumer Group (3 consumers)
  Each consumer handles 2 partitions
```

---

## Diagram

### Outbox Pattern Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Order Service                              │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ @Transactional {                                      │    │
│  │   orderRepo.save(order);  ────────► Orders Table     │    │
│  │   outboxRepo.save(event); ────────► Outbox Table     │    │
│  │ }                                                     │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Outbox Publisher (every 100ms):                       │    │
│  │   SELECT * FROM outbox WHERE published=false          │    │
│  │   → kafkaTemplate.send(event)                        │    │
│  │   → UPDATE outbox SET published=true                  │    │
│  └─────────────────────────────────┬───────────────────┘    │
└────────────────────────────────────┼────────────────────────┘
                                     │
                                     ▼
                              ┌──────────────┐
                              │ Kafka Topic   │
                              │ "order-events"│
                              └──────┬───────┘
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
            ┌────────────┐  ┌────────────┐  ┌────────────┐
            │ Inventory  │  │Notification│  │ Analytics  │
            │ Service    │  │ Service    │  │ Service    │
            └────────────┘  └────────────┘  └────────────┘
```

### Saga Pattern (Choreography)

```
OrderService          PaymentService       InventoryService     NotificationService
     │                      │                     │                     │
     │ OrderCreated         │                     │                     │
     ├─────────────────────►├─────────────────────►─────────────────────►
     │                      │                     │                     │
     │                      │ ProcessPayment      │                     │
     │                      ├───────┐             │                     │
     │                      │       │             │                     │
     │                      │◄──────┘             │                     │
     │                      │                     │                     │
     │                      │ PaymentCompleted    │                     │
     │                      ├─────────────────────►                     │
     │                      │                     │                     │
     │                      │                     │ ReserveStock        │
     │                      │                     ├───────┐             │
     │                      │                     │◄──────┘             │
     │                      │                     │                     │
     │                      │                     │ StockReserved       │
     │◄─────────────────────┼─────────────────────┤                     │
     │                      │                     │                     │
     │ OrderCompleted       │                     │                     │
     ├──────────────────────┼─────────────────────┼─────────────────────►
```

---

## Interview Questions

### Q1: What is the dual-write problem and how does the Outbox pattern solve it?

**A:** The dual-write problem: you need to update a database AND publish an event to Kafka. These are two separate systems — if one succeeds and the other fails, you have inconsistency. The Outbox pattern solves this by writing BOTH the business data and the event to the same database in one transaction (atomic). A separate publisher then reads the outbox and sends to Kafka. Even if Kafka publish fails temporarily, the event is safely stored in the DB and will be retried.

### Q2: When would you choose choreography vs orchestration for a Saga?

**A:**
- **Choreography:** Each service reacts to events independently. Good for simple flows (3-4 steps), loose coupling, when services are owned by different teams. Risk: hard to understand overall flow, debugging distributed events is complex.
- **Orchestration:** Central coordinator manages the flow. Good for complex flows (5+ steps), clear visibility into process state, easier error handling and compensation. Risk: orchestrator is a single point of failure, tighter coupling to coordinator.
- Rule of thumb: Start with choreography, move to orchestration when flows get complex or need SLA tracking.

### Q3: How would you implement event sourcing with Kafka?

**A:**
1. Each aggregate (entity) has a topic partition (key = aggregateId)
2. All state changes stored as events: `OrderCreated`, `ItemAdded`, `OrderPaid`
3. To get current state: replay all events for that key from beginning (or use snapshot)
4. Kafka retention = forever (or very long) for event-sourced topics
5. Use compacted topics for snapshots (periodically store full state)
6. CQRS read side: separate consumer builds queryable projections (Elasticsearch, SQL)
7. Challenge: Kafka doesn't support conditional writes (optimistic locking needs external check)

### Q4: What is CDC and when would you use it over the Outbox pattern?

**A:**
- **CDC (Debezium):** Reads database transaction log directly. No code changes needed — captures ALL changes including those from legacy/non-Java systems. Better for: brownfield systems, multi-language environments, capturing all tables, minimal application changes.
- **Outbox pattern:** Application explicitly writes events. More control over event format, selective publishing, no dependency on DB log format. Better for: greenfield, custom event schemas, when you want explicit event design.
- Trade-off: CDC is non-invasive but produces raw DB changes; Outbox produces domain-specific events.

### Q5: How do you handle the case where a Saga step fails?

**A:** Compensation (undo previous steps):
1. Each Saga step has a compensating action (reverse of the forward action)
2. On failure: publish compensation events in reverse order
   - `PaymentFailed` → trigger `CancelOrder` + `ReleaseStock`
3. Compensation must be idempotent (may be triggered multiple times)
4. Some actions can't be undone (email sent) → use alternative compensation (send correction email)
5. Track Saga state (in-progress, completed, compensating, failed) for visibility
6. Set timeouts — if no response from a step within SLA, trigger compensation

---

## Best Practices

1. **Start with Outbox pattern** for DB+Kafka consistency — simpler than CDC
2. **Design events as immutable facts** — past tense (OrderCreated, not CreateOrder)
3. **Include event metadata** — eventId, timestamp, source, version
4. **Make all consumers idempotent** — at-least-once delivery is the norm
5. **Use Saga for distributed transactions** — don't use 2PC across services
6. **Keep Sagas short** — fewer steps = fewer failure modes
7. **Monitor DLT and Saga state** — alert on stuck/failed flows

---

## Related Topics

- [21. Idempotency](./21-idempotency.md)
- [26. Kafka Error Handling](./26-kafka-error-handling.md)
- [34. Kafka + Microservices](./34-kafka-microservices.md)
- [35. Kafka + Database](./35-kafka-database.md)
