# 12. Message Delivery Semantics ⭐⭐⭐

---

## Theory

Delivery semantics define the guarantees about how many times a message is delivered and processed.

### At Most Once

```
Message may be LOST, but never DUPLICATED.

Flow:
1. Consumer fetches message
2. Commits offset IMMEDIATELY (before processing)
3. Processes message
4. If processing fails → message already committed → LOST

When to use:
- Metrics where occasional loss is acceptable
- Logging where completeness is not critical
- High-throughput scenarios where loss is tolerable

Implementation:
  enable.auto.commit=true + auto.commit.interval.ms=very low
  OR: commit offset → then process
```

### At Least Once (Default)

```
Message NEVER LOST, but may be DUPLICATED.

Flow:
1. Consumer fetches message
2. Processes message
3. Commits offset AFTER processing
4. If crash between process and commit → message REPROCESSED

When to use:
- Most production systems
- When combined with idempotent processing
- Business events where loss is unacceptable

Implementation:
  enable.auto.commit=false
  Process → then commit
  Handle duplicates at consumer side (idempotent design)
```

### Exactly Once

```
Message delivered and processed EXACTLY ONE TIME. Neither lost nor duplicated.

The hardest guarantee to achieve — requires coordination between:
- Producer (idempotent + transactional)
- Broker (transaction log, LSO)
- Consumer (read-committed isolation)

When to use:
- Financial transactions
- Billing events
- Inventory updates
- Any scenario where duplicates cause real harm
```

### Producer Guarantees

```
acks=0: At most once (fire and forget, may lose)
acks=1: At least once (leader ack, but follower may not have it)
acks=all + idempotent: Exactly once delivery TO Kafka

Idempotent Producer:
- PID (Producer ID) + Sequence Number per partition
- Broker detects duplicates from retries
- Ensures each record written exactly once to its partition

Transactional Producer:
- Atomic writes across multiple partitions/topics
- beginTransaction → send → send → commitTransaction
- All or nothing semantics
```

### Consumer Guarantees

```
At-most-once consumer:
  commit → process (if process fails, message skipped)

At-least-once consumer:
  process → commit (if crash between, message reprocessed)

Exactly-once consumer options:
1. Kafka Transactions (consume-transform-produce):
   - Read from input topic
   - Process and write to output topic
   - Commit consumer offset
   - ALL within one transaction

2. Idempotent consumer (external system):
   - Process message
   - Write result + offset to DB in same transaction
   - On restart, check DB for last processed offset
   - Skip already-processed messages
```

### Duplicate Messages — When and Why

```
Producer duplicates:
  Producer sends → network timeout → producer retries → broker writes TWICE
  Fix: enable.idempotence=true

Consumer duplicates:
  Consumer processes → crash before commit → restart → reprocesses
  Fix: Idempotent processing (deduplication)

Common deduplication strategies:
  1. Unique event ID in message → check before processing
  2. Database unique constraint on event ID
  3. Idempotent operations (SET balance=X, not ADD to balance)
  4. Version/timestamp check (process only if newer)
```

### End-to-End Exactly Once

```
Option 1: Kafka Transactions (stream processing):
┌─────────────────────────────────────────────────────────┐
│ Transaction boundary:                                    │
│                                                          │
│ 1. Read from input topic                                │
│ 2. Process/transform                                    │
│ 3. Write to output topic                                │
│ 4. Commit consumer offsets                              │
│                                                          │
│ All succeed or all rollback atomically                  │
└─────────────────────────────────────────────────────────┘

Option 2: Outbox + Idempotent Consumer (microservices):
┌─────────────────────────────────────────────────────────┐
│ Producer side:                                           │
│   DB transaction: write entity + outbox event           │
│   Outbox publisher: send to Kafka (at-least-once)       │
│                                                          │
│ Consumer side:                                           │
│   DB transaction: process event + store event ID        │
│   Skip if event ID already exists (idempotent)          │
└─────────────────────────────────────────────────────────┘
```

---

## Diagram

### Delivery Semantics Comparison

```
AT MOST ONCE:
  Producer ─── send ───► Broker        Consumer: commit → process
  (no retry)             (may lose)              (may skip on failure)
  
  Loss window: ████████
  Duplicate window: (none)

AT LEAST ONCE:
  Producer ─── send+retry ──► Broker   Consumer: process → commit
  (retries on failure)                            (may reprocess on crash)
  
  Loss window: (none)
  Duplicate window: ████████

EXACTLY ONCE:
  Producer ─── idempotent+txn ──► Broker   Consumer: txn(process + commit)
  (PID + seq + transaction)                          (atomic processing)
  
  Loss window: (none)
  Duplicate window: (none)
```

### Transaction Flow

```
Transactional Exactly-Once (Consume-Transform-Produce):

Consumer                  Broker                    Output Topic
────────                  ──────                    ────────────
1. beginTransaction()
2. poll() input topic  ←─── read records ───
3. Transform records
4. send(output topic)  ───► write to txn log ────► (uncommitted)
5. sendOffsetsToTxn()  ───► write offsets to txn
6. commitTransaction() ───► mark txn committed ──► (now visible)
                                                    
If crash at any point before commit:
  → Transaction aborted
  → Output messages discarded
  → Consumer offsets not committed
  → On restart: re-reads input, reprocesses (safe — no duplicates in output)
```

---

## Code

### At-Least-Once Consumer (Standard)

```java
@KafkaListener(topics = "orders", groupId = "order-group")
public void processOrder(@Payload OrderEvent event, Acknowledgment ack) {
    try {
        // Process first
        orderService.process(event);
        // Then commit (at-least-once)
        ack.acknowledge();
    } catch (Exception e) {
        // Don't commit → will be redelivered
        throw e;
    }
}
```

### Exactly-Once with Kafka Transactions

```java
@Service
public class ExactlyOnceProcessor {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional("kafkaTransactionManager")
    public void consumeTransformProduce(ConsumerRecord<String, Object> input) {
        // Read input
        OrderEvent order = (OrderEvent) input.value();
        
        // Transform
        InvoiceEvent invoice = createInvoice(order);
        
        // Write to output topic (within transaction)
        kafkaTemplate.send("invoices", order.getOrderId(), invoice);
        
        // Offset committed as part of transaction
        // If any step fails → entire transaction aborted
    }
}
```

### Idempotent Consumer (External System)

```java
@Service
@Transactional  // DB transaction
public class IdempotentOrderConsumer {

    private final ProcessedEventRepository processedRepo;
    private final OrderRepository orderRepo;

    @KafkaListener(topics = "orders", groupId = "order-group")
    public void handleOrder(@Payload OrderEvent event, Acknowledgment ack) {
        String eventId = event.getEventId();
        
        // Check if already processed (idempotency check)
        if (processedRepo.existsByEventId(eventId)) {
            log.info("Duplicate event {}, skipping", eventId);
            ack.acknowledge();
            return;
        }
        
        // Process event
        orderRepo.save(toOrder(event));
        
        // Mark as processed (in same DB transaction)
        processedRepo.save(new ProcessedEvent(eventId, Instant.now()));
        
        // Commit Kafka offset
        ack.acknowledge();
    }
}
```

---

## Interview Questions

### Q1: Why is exactly-once so hard to achieve?

**A:** It requires atomicity across distributed systems:
- Producer must write exactly once (solved by idempotent producer)
- Consumer must process exactly once — but processing + offset commit are two separate operations
- If crash between processing and commit → reprocessing (duplicate)
- If crash between commit and processing → data loss
- True exactly-once requires coordinating external systems (DB, cache) with Kafka offsets atomically — either through Kafka transactions (within Kafka) or application-level idempotency (with external systems).

### Q2: When would you accept at-most-once over at-least-once?

**A:** When occasional data loss is acceptable and low latency matters:
- **Metrics collection:** Missing one data point is fine; duplicates would skew analytics
- **Live location updates:** Old location data is useless; latest matters
- **Health checks / heartbeats:** Missing one is fine; the next one confirms health
- **High-frequency sensor data:** Losing 0.01% is acceptable; duplicates waste storage

### Q3: How does Kafka's transactional consumer work?

**A:** Using `isolation.level=read_committed`:
- Consumer only reads records from committed transactions
- Uncommitted or aborted transaction records are invisible
- This prevents reading "dirty" data from in-progress transactions
- Combined with transactional producer: consume-transform-produce pattern gives end-to-end exactly-once within Kafka

### Q4: What is the practical approach to exactly-once in microservices?

**A:** True distributed exactly-once is impractical. The practical approach:
1. **Producer:** Enable idempotent producer (handles retries)
2. **Consumer:** Design idempotent processing:
   - Store event ID with result in same DB transaction
   - On duplicate: check event ID, skip if exists
   - Use idempotent operations (SET vs ADD)
3. **Result:** "Effectively exactly-once" — duplicates are detected and ignored

### Q5: What is the difference between idempotent producer and transactional producer?

**A:**
- **Idempotent producer:** Prevents duplicates within a single partition from retries. Uses PID + sequence number. Scope: per-partition deduplication.
- **Transactional producer:** Provides atomic writes across multiple partitions/topics. If one write fails, all are rolled back. Scope: cross-partition/topic atomicity.
- Transactions are a superset — they require idempotence as a foundation.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Assuming Kafka provides exactly-once by default | It provides at-least-once | Enable idempotent producer + design idempotent consumers |
| Not handling duplicates in at-least-once | Data corruption (double-counting) | Implement deduplication logic |
| Using at-most-once for critical business events | Silent data loss | Use at-least-once with idempotent processing |
| Mixing read_committed consumers with non-transactional producers | No benefit — all messages visible | Align isolation level with producer setup |
| Over-engineering exactly-once | Complexity for scenarios that don't need it | Most services work well with at-least-once + idempotency |

---

## Best Practices

1. **Default to at-least-once** — simplest reliable approach
2. **Always design idempotent consumers** — deduplication by event ID
3. **Use idempotent producer** (default since Kafka 3.0)
4. **Reserve transactions** for Kafka Streams or consume-transform-produce patterns
5. **For external systems:** outbox pattern + idempotent consumer = effectively exactly-once
6. **Include event IDs** in every message for deduplication

---

## Related Topics

- [11. Offset Management](./11-offset-management.md)
- [20. Kafka Transactions](./20-kafka-transactions.md)
- [21. Idempotency](./21-idempotency.md)
- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
