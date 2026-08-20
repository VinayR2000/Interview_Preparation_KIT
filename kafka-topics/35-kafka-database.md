# 35. Kafka + Database

---

## Theory

Integrating Kafka with databases introduces challenges around consistency, ordering, and the dual-write problem.

### DB → Kafka (Publishing Database Changes)

```
Approaches:
1. Application-level events (Outbox pattern)
   Service writes to DB + outbox table → publisher sends to Kafka
   
2. CDC (Change Data Capture)
   Debezium reads DB transaction log → publishes changes to Kafka
   
3. Polling-based (Kafka Connect JDBC Source)
   Polls table periodically for new/changed rows → publishes to Kafka

Comparison:
| Approach | Latency | Invasiveness | Captures All |
|----------|---------|--------------|--------------|
| Outbox   | ~100ms  | Code change  | Only explicit|
| CDC      | ~ms     | No code change| All changes  |
| Polling  | seconds | No code change| Only queried |
```

### Kafka → DB (Consuming into Database)

```
Consumer reads from Kafka → writes to database:

Challenges:
1. Duplicate processing (at-least-once) → use idempotent writes
2. Ordering → same key goes to same partition → process sequentially
3. Batch efficiency → collect records, bulk insert
4. Transaction boundary → commit DB + Kafka offset atomically?

Pattern:
  @KafkaListener + @Transactional:
    1. Read records from Kafka
    2. Write to DB in transaction
    3. Acknowledge Kafka offset
    If step 3 fails → message redelivered → idempotent write handles it
```

### Transaction Consistency (The Dual-Write Problem)

```
The problem:
  orderRepo.save(order);                    // DB write
  kafkaTemplate.send("orders", event);      // Kafka write
  
  What if:
  - DB succeeds, Kafka fails? → DB has order, no event published
  - Kafka succeeds, DB fails? → Event published, no order in DB

Both scenarios leave the system inconsistent!

Solutions (ranked by reliability):
1. Outbox pattern (recommended)
2. CDC (Debezium)
3. Kafka transactions + ChainedTransactionManager (complex)
4. Listen-to-yourself (consume own events to trigger DB writes)
```

### Outbox Pattern (Recommended Solution)

```java
// Everything in ONE database transaction:
@Transactional
public Order createOrder(CreateOrderRequest req) {
    Order order = orderRepo.save(buildOrder(req));
    
    outboxRepo.save(OutboxEvent.builder()
        .id(UUID.randomUUID())
        .aggregateId(order.getId().toString())
        .aggregateType("Order")
        .eventType("OrderCreated")
        .payload(toJson(event))
        .published(false)
        .createdAt(Instant.now())
        .build());
    
    return order;
}

// Separate publisher (polling or CDC on outbox table):
@Scheduled(fixedDelay = 100)
@Transactional
public void publishOutbox() {
    List<OutboxEvent> events = outboxRepo.findByPublishedFalseOrderByCreatedAt(100);
    for (OutboxEvent event : events) {
        kafkaTemplate.send(event.topic(), event.getAggregateId(), event.getPayload());
        event.setPublished(true);
    }
}
```

### CDC with Debezium

```
Database binlog → Debezium → Kafka:

Configuration (Debezium MySQL connector):
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "database.hostname": "mysql",
  "database.port": "3306",
  "database.user": "debezium",
  "database.server.id": "1",
  "database.server.name": "myapp",
  "table.include.list": "mydb.orders,mydb.outbox",
  "include.schema.changes": "false"
}

Output topic: myapp.mydb.orders
Each row change → one Kafka message:
{
  "before": {"id": 1, "status": "PENDING"},
  "after": {"id": 1, "status": "PAID"},
  "op": "u",  // c=create, u=update, d=delete
  "ts_ms": 1705312800000
}
```

### Exactly-Once Challenges

```
True exactly-once (DB + Kafka) is hard because they're separate systems.

Practical approaches:

1. Outbox + Idempotent Consumer:
   Producer: DB transaction includes outbox → at-least-once to Kafka
   Consumer: Check event ID before processing → effectively exactly-once

2. Kafka Transactions + DB:
   Not directly possible (Kafka txn can't span external DB)
   Workaround: Store consumer offset in DB (same transaction as processing)
   
   @Transactional  // DB transaction
   void process(event) {
       businessRepo.save(result);                    // business write
       offsetRepo.save(event.partition, event.offset); // offset write
   }
   // On restart: seek to offset from DB (not __consumer_offsets)

3. CDC + Kafka Transactions:
   DB changes captured by CDC → processed in Kafka Streams with EOS
   Full exactly-once for the Kafka pipeline portion
```

---

## Diagram

### Dual-Write Problem and Solutions

```
PROBLEM (Dual Write):
═══════════════════════════════════
Service ──► DB (success) ──► Kafka (FAILS!)
  Result: DB has data, Kafka doesn't → INCONSISTENT

Service ──► Kafka (success) ──► DB (FAILS!)
  Result: Kafka has event, DB doesn't → INCONSISTENT


SOLUTION 1 (Outbox Pattern):
═══════════════════════════════════
Service ──► DB (order + outbox event) ──► COMMIT (atomic!)
                     │
                     ▼ (async, separate process)
              Outbox Publisher ──► Kafka
              (at-least-once, idempotent consumer handles duplicates)


SOLUTION 2 (CDC/Debezium):
═══════════════════════════════════
Service ──► DB (business write) ──► COMMIT
                     │
                     ▼ (captured from binlog)
              Debezium ──► Kafka
              (captures actual committed changes, no dual-write)
```

---

## Interview Questions

### Q1: Explain the dual-write problem and your recommended solution.

**A:** The dual-write problem occurs when a service needs to update both a database and Kafka atomically. Since they're separate systems, you can't do a distributed transaction easily. If either fails after the other succeeds, you have inconsistency.

**Recommended solution:** Outbox pattern.
1. Write business data AND an event record in the same DB transaction (atomic)
2. A separate process reads the outbox and publishes to Kafka
3. If Kafka publish fails, it retries (event safely in DB)
4. Consumers handle at-least-once delivery with idempotent processing
5. Result: guaranteed consistency with minimal complexity

### Q2: When would you choose CDC (Debezium) over the Outbox pattern?

**A:**
- **Choose CDC when:** Multiple applications write to the same DB (can't modify all to use outbox), need to capture ALL changes (not just explicit events), want zero application code changes, need very low latency (ms), dealing with legacy systems.
- **Choose Outbox when:** You control the application code, want domain-specific events (not raw DB changes), don't want infrastructure dependency (Debezium/Kafka Connect), simpler operational model, events need to be business-meaningful rather than technical DB diffs.

### Q3: How would you ensure exactly-once processing when consuming from Kafka into a database?

**A:** Store the Kafka offset in the same database transaction as the business write:
```java
@Transactional
void processRecord(ConsumerRecord record) {
    // Check if already processed (idempotency)
    if (offsetStore.getOffset(record.partition()) >= record.offset()) return;
    
    // Business logic
    orderRepo.save(transformToOrder(record.value()));
    
    // Store offset (same DB transaction)
    offsetStore.save(record.partition(), record.offset());
}
```
On restart: read last processed offset from DB, seek consumer to that position. This avoids relying on Kafka's offset commits (which are separate from DB transaction). Combined with idempotent writes as safety net.

---

## Best Practices

1. **Never dual-write** directly to DB and Kafka — use Outbox or CDC
2. **Outbox table**: id, aggregate_id, event_type, payload, published, created_at
3. **Clean up outbox** — delete published events after retention period
4. **Use CDC for legacy** — when you can't modify application code
5. **Idempotent consumers** — always assume messages may arrive more than once
6. **Batch DB writes** — collect Kafka records, bulk insert for throughput
7. **Monitor outbox lag** — alert if unpublished events accumulate

---

## Related Topics

- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
- [34. Kafka + Microservices](./34-kafka-microservices.md)
- [24. Kafka Connect](./24-kafka-connect.md)
- [21. Idempotency](./21-idempotency.md)
