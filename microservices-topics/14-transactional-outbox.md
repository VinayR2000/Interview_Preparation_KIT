# 14. Transactional Outbox ⭐⭐⭐⭐⭐

## Theory

The Transactional Outbox pattern solves a critical problem: How to reliably update the database AND publish an event atomically?

### The Problem:
```
Service updates DB → then publishes to Kafka

Scenario 1: DB succeeds, Kafka fails
  DB: order created ✓
  Kafka: event NOT sent ✗
  Result: Inconsistent! Other services don't know about the order.

Scenario 2: Kafka succeeds, DB fails
  Kafka: event sent ✓
  DB: order NOT saved ✗ (rolled back)
  Result: Inconsistent! Event published for non-existent order.
```

### The Solution — Outbox Pattern:
Write both the business data AND the event to the SAME database in a SINGLE transaction. A separate process reads the outbox and publishes to Kafka.

### Key Components:
- **Outbox table**: Stores events in the same database as business data
- **Polling publisher**: Periodically reads outbox and sends to Kafka
- **CDC (Change Data Capture)**: Captures DB changes and streams them (Debezium)
- **Idempotent consumers**: Handle potential duplicate events

---

## Internal Working

### Without Outbox (The Problem):

```
┌───────────────────────────────────────────────────┐
│ DUAL WRITE PROBLEM                                 │
│                                                    │
│ Order Service:                                    │
│                                                    │
│   1. BEGIN TRANSACTION                            │
│   2. INSERT INTO orders (...)  ← DB write ✓      │
│   3. COMMIT                                       │
│   4. kafkaTemplate.send(...)   ← Kafka write     │
│                                                    │
│ What if step 4 fails?                            │
│   - Order exists in DB                           │
│   - Event never published                        │
│   - Payment service never processes payment      │
│   - INCONSISTENT STATE!                          │
│                                                    │
│ What if service crashes between 3 and 4?         │
│   - Same problem — event lost forever            │
└───────────────────────────────────────────────────┘
```

### With Outbox (The Solution):

```
┌───────────────────────────────────────────────────┐
│ TRANSACTIONAL OUTBOX                               │
│                                                    │
│ Order Service:                                    │
│                                                    │
│   1. BEGIN TRANSACTION                            │
│   2. INSERT INTO orders (...)     ← business data│
│   3. INSERT INTO outbox_events (...)  ← event    │
│   4. COMMIT                                       │
│                                                    │
│ Both writes in SAME transaction!                  │
│ If transaction fails → neither is committed      │
│ If transaction succeeds → both exist in DB       │
│                                                    │
│ Outbox Publisher (separate process):             │
│   5. SELECT * FROM outbox_events WHERE status='PENDING'│
│   6. Publish each event to Kafka                 │
│   7. UPDATE outbox_events SET status='PUBLISHED' │
│                                                    │
│ If publisher crashes after Kafka but before UPDATE:│
│   - Event published TWICE to Kafka               │
│   - Consumer must be IDEMPOTENT                  │
│   - Better than losing events!                   │
└───────────────────────────────────────────────────┘
```

### CDC with Debezium:

```
┌────────────────────────────────────────────────────────┐
│ CDC APPROACH (No Polling)                               │
│                                                         │
│ ┌──────────────┐                                       │
│ │ Order Service│                                       │
│ │              │                                       │
│ │ Transaction: │                                       │
│ │  - orders    │                                       │
│ │  - outbox    │                                       │
│ └──────┬───────┘                                       │
│        │                                                │
│        ↓                                                │
│ ┌──────────────┐                                       │
│ │  PostgreSQL  │                                       │
│ │              │                                       │
│ │ orders table │                                       │
│ │ outbox table │──── WAL (Write-Ahead Log)            │
│ └──────────────┘           │                           │
│                            │ Debezium reads WAL        │
│                            ↓                           │
│                  ┌──────────────────┐                  │
│                  │    Debezium      │                  │
│                  │ (CDC Connector)  │                  │
│                  │                  │                  │
│                  │ Monitors outbox  │                  │
│                  │ table changes    │                  │
│                  └────────┬─────────┘                  │
│                           │                            │
│                           ↓                            │
│                  ┌──────────────────┐                  │
│                  │      Kafka       │                  │
│                  │  order-events    │                  │
│                  └──────────────────┘                  │
│                                                         │
│ Advantages over polling:                               │
│ - Near real-time (reads DB WAL, not polling)          │
│ - No "SELECT ... WHERE" overhead on DB               │
│ - Guaranteed ordering                                 │
│ - Lower latency                                       │
└────────────────────────────────────────────────────────┘
```

---

## Diagram

```
Outbox Table Structure:

┌────────────────────────────────────────────────────┐
│ outbox_events table                                 │
├──────┬───────────┬──────────┬───────┬──────┬──────┤
│ id   │aggregate_id│event_type│payload│status│created│
├──────┼───────────┼──────────┼───────┼──────┼──────┤
│ 1    │ order-101 │OrderCreated│{...} │PUBLISHED│T1│
│ 2    │ order-102 │OrderCreated│{...} │PUBLISHED│T2│
│ 3    │ order-103 │OrderCreated│{...} │PENDING  │T3│
│ 4    │ order-101 │OrderPaid   │{...} │PENDING  │T4│
└──────┴───────────┴──────────┴───────┴──────┴──────┘

                    │
                    │ Publisher reads PENDING events
                    ↓

Topic: order-events
┌──────────────────────────────────────┐
│ [OrderCreated-103] [OrderPaid-101]   │
└──────────────────────────────────────┘
```

---

## Code

### Outbox Table Schema:

```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(255) NOT NULL,
    aggregate_type  VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    created_at      TIMESTAMP DEFAULT NOW(),
    published_at    TIMESTAMP,
    retry_count     INT DEFAULT 0,
    
    -- Index for polling publisher
    INDEX idx_outbox_pending (status, created_at) WHERE status = 'PENDING'
);
```

### Writing Business Data + Outbox Event in One Transaction:

```java
@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    @Transactional  // SINGLE transaction for both writes
    public Order createOrder(CreateOrderRequest request) {
        // Business data
        Order order = Order.builder()
            .id(UUID.randomUUID())
            .customerId(request.getCustomerId())
            .items(request.getItems())
            .totalAmount(calculateTotal(request.getItems()))
            .status(OrderStatus.PENDING)
            .build();
        
        orderRepository.save(order);

        // Outbox event (same transaction!)
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .eventType("OrderCreated")
            .payload(toJson(new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getItems()
            )))
            .status("PENDING")
            .createdAt(Instant.now())
            .build();
        
        outboxRepository.save(outboxEvent);
        
        log.info("Order {} created with outbox event {}", order.getId(), outboxEvent.getId());
        return order;
    }
}
```

### Polling Outbox Publisher:

```java
@Service
@Slf4j
public class OutboxPollingPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)  // Poll every second
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository
            .findByStatusOrderByCreatedAt("PENDING", PageRequest.of(0, 100));

        for (OutboxEvent event : pending) {
            try {
                String topic = resolveTopicName(event.getAggregateType());
                
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);  // Wait for ack
                
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxRepository.save(event);
                
                log.debug("Published outbox event {}", event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", event.getId(), e);
                event.setRetryCount(event.getRetryCount() + 1);
                
                if (event.getRetryCount() >= 5) {
                    event.setStatus("FAILED");
                    log.error("Outbox event {} permanently failed after {} retries", 
                        event.getId(), event.getRetryCount());
                }
                outboxRepository.save(event);
            }
        }
    }

    // Cleanup old published events
    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
    @Transactional
    public void cleanupPublishedEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        int deleted = outboxRepository.deleteByStatusAndPublishedAtBefore("PUBLISHED", cutoff);
        log.info("Cleaned up {} published outbox events", deleted);
    }
}
```

### Debezium CDC Configuration:

```json
{
  "name": "order-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "order-db",
    "database.port": "5432",
    "database.user": "debezium",
    "database.password": "***",
    "database.dbname": "order_service",
    "database.server.name": "order",
    "table.include.list": "public.outbox_events",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.topic.replacement": "${routedByValue}-events",
    "transforms.outbox.table.field.event.timestamp": "created_at"
  }
}
```

### Idempotent Consumer (Handles Duplicate Events):

```java
@Service
public class IdempotentPaymentConsumer {

    private final ProcessedEventRepository processedEventRepo;
    private final PaymentService paymentService;

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        // Idempotency check — was this event already processed?
        String eventId = event.getEventId().toString();
        
        if (processedEventRepo.existsById(eventId)) {
            log.warn("Event {} already processed. Skipping.", eventId);
            return;  // Duplicate — skip
        }

        // Process the event
        paymentService.processPayment(event);

        // Mark as processed
        processedEventRepo.save(new ProcessedEvent(eventId, Instant.now()));
    }
}
```

---

## Dry Run

### Outbox Pattern — Normal Flow:

```
T=0: Client calls POST /api/orders
T=1: BEGIN TRANSACTION
     INSERT INTO orders (id=101, status=PENDING, total=$50) ✓
     INSERT INTO outbox_events (id=E1, aggregate_id=101, 
                                type=OrderCreated, status=PENDING) ✓
     COMMIT ✓

T=2: Polling publisher runs (every 1s)
     SELECT * FROM outbox_events WHERE status='PENDING'
     Found: [E1]
     
T=3: kafkaTemplate.send("order-events", "101", payload)
     Kafka acknowledged ✓
     
T=4: UPDATE outbox_events SET status='PUBLISHED', published_at=NOW() 
     WHERE id=E1 ✓

Result: Business data + event guaranteed consistent ✓
```

### Outbox Pattern — Failure Scenarios:

```
SCENARIO: Service crashes after DB commit, before Kafka publish
T=0: BEGIN TRANSACTION
T=1: INSERT order ✓, INSERT outbox ✓
T=2: COMMIT ✓
T=3: --- SERVICE CRASHES ---

Recovery: Service restarts
T=10: Polling publisher runs
      SELECT FROM outbox WHERE status='PENDING'
      Found: [E1] (still pending because publish never happened)
T=11: Publish to Kafka ✓
T=12: Mark as PUBLISHED ✓

Result: Event eventually delivered! No data loss ✓

SCENARIO: Kafka publish succeeds, but marking PUBLISHED fails
T=3: Kafka publish ✓
T=4: UPDATE SET status='PUBLISHED' → FAILS (DB connection lost)

Next polling cycle:
T=5: SELECT FROM outbox WHERE status='PENDING'
     Found: [E1] (still pending!)
T=6: Kafka publish AGAIN (duplicate!)
T=7: Mark as PUBLISHED ✓

Result: Consumer receives event TWICE
        → Consumer MUST be idempotent to handle this
```

---

## Interview Questions

1. **What problem does Transactional Outbox solve?**
   - The dual-write problem: can't atomically update DB AND publish to Kafka. If DB succeeds but Kafka fails (or vice versa), system becomes inconsistent. Outbox writes event to same DB in same transaction, guaranteeing atomicity.

2. **Polling vs CDC (Debezium) — which is better?**
   - Polling: Simple, no extra infrastructure, but adds DB load and has latency (poll interval). CDC: Near real-time, no DB polling overhead, guaranteed ordering, but requires Debezium infrastructure. CDC preferred for high-throughput systems.

3. **Why do consumers need to be idempotent?**
   - Outbox pattern can produce duplicate events (publish succeeds, marking fails, republish on next poll). At-least-once delivery guarantee means consumers MUST handle receiving the same event multiple times.

4. **How to implement idempotency in consumers?**
   - Store processed event IDs. Before processing, check if event ID exists. Use database unique constraint on event ID. Idempotency key in the event payload.

5. **What is CDC (Change Data Capture)?**
   - Technology that captures database changes (from WAL/binlog) and streams them to external systems. Debezium is the most popular. Reads the transaction log — no application code changes needed. Near real-time event publishing.

6. **How to clean up the outbox table?**
   - Scheduled job deletes PUBLISHED events older than N days. Or CDC with log-based approach can use table truncation. Keep FAILED events for investigation.

---

## Common Mistakes

1. **Publishing event outside transaction** — The whole point is transactional atomicity
2. **No idempotent consumers** — Duplicates will happen, consumers must handle them
3. **Never cleaning outbox** — Table grows forever, slows queries
4. **Polling too frequently** — DB overhead. Polling too rarely — high latency
5. **No retry limit** — Failed events retried forever, wasting resources
6. **Large payloads in outbox** — Store minimal data, or reference to full payload

---

## Best Practices

1. **Same transaction** — Business data + outbox event in one atomic commit
2. **Idempotent consumers** — Always, without exception
3. **CDC over polling** — Lower latency, less DB load for high throughput
4. **Event ID in payload** — Enables consumer deduplication
5. **Cleanup strategy** — Delete published events periodically
6. **Monitor failed events** — Alert on events stuck in FAILED status
7. **Small outbox events** — Keep payload lean, include just what consumers need
