# 20. Kafka Transactions ⭐⭐⭐

---

## Theory

Kafka transactions provide **atomic writes** across multiple topics/partitions and enable **exactly-once semantics** for consume-transform-produce patterns.

### Transactional Producer

```java
Properties props = new Properties();
props.put("transactional.id", "order-processor-1");  // REQUIRED for transactions
props.put("enable.idempotence", "true");             // automatically enabled

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
producer.initTransactions();  // MUST call before any transactional operation

try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("orders", key, value));
    producer.send(new ProducerRecord<>("inventory", key, value2));
    producer.commitTransaction();  // atomic: both writes committed
} catch (Exception e) {
    producer.abortTransaction();   // both writes rolled back
}
```

### Transaction ID

```
transactional.id = "order-processor-1"
  - Unique identifier for the transactional producer
  - Survives producer restarts (same ID = same producer logically)
  - Broker uses this to fence zombie producers (old instances)
  - Must be unique per producer instance across the cluster

Fencing: If two producers have same transactional.id:
  - Newer producer "fences" (invalidates) the older one
  - Old producer's in-progress transactions are aborted
  - Prevents duplicate processing from zombie instances
```

### Transaction API

```java
producer.initTransactions();    // Initialize producer (one time)
producer.beginTransaction();    // Start transaction
producer.send(record1);         // Write to topic A
producer.send(record2);         // Write to topic B
producer.sendOffsetsToTransaction(offsets, groupMetadata);  // Commit consumer offsets
producer.commitTransaction();   // All-or-nothing commit
// OR
producer.abortTransaction();    // Discard all writes in this transaction
```

### Read Committed / Read Uncommitted

```
Consumer isolation level:
  isolation.level = read_committed (default for transactional consumers)
    → Consumer only sees committed transaction records
    → In-progress or aborted transaction records are invisible
    → Introduces slight latency (waits for commit)

  isolation.level = read_uncommitted (default for non-transactional)
    → Consumer sees ALL records immediately (including uncommitted)
    → Faster but may see records that are later aborted

LSO (Last Stable Offset):
  The offset below which all transactions are resolved (committed or aborted)
  read_committed consumers can only read up to LSO
```

### Consume-Transform-Produce (Exactly-Once)

```
The canonical exactly-once pattern:

1. Consumer reads from input topic
2. Application transforms the data
3. Producer writes to output topic + commits consumer offset
4. All within ONE Kafka transaction

If any step fails → entire transaction aborted:
  - Output records discarded
  - Consumer offset NOT committed
  - On restart: re-reads same input messages (safe, no duplicates in output)
```

### Exactly-Once Semantics (EOS)

```
EOS = Idempotent Producer + Transactions + read_committed

Three guarantees combined:
1. Idempotent producer: no duplicates from retries (per partition)
2. Transactions: atomic writes across partitions/topics
3. read_committed: consumers skip aborted transaction records

Scope:
- Within Kafka: full exactly-once (consume-transform-produce)
- External systems: need additional application-level idempotency
```

---

## Diagram

### Transaction Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                    KAFKA TRANSACTION                               │
│                                                                    │
│  Producer (transactional.id="txn-1")                             │
│  ┌────────────────────────────────────────────────────┐          │
│  │ 1. initTransactions()                                │          │
│  │ 2. beginTransaction()                                │          │
│  │ 3. send("orders", P0, record1)  ──► Transaction Log │          │
│  │ 4. send("inventory", P2, record2) ──► Transaction Log│         │
│  │ 5. sendOffsetsToTransaction(offsets) ──► __consumer_offsets │  │
│  │ 6. commitTransaction()           ──► COMMIT marker   │          │
│  └────────────────────────────────────────────────────┘          │
│                                                                    │
│  Broker perspective:                                              │
│  ┌────────────────────────────────────────────────────┐          │
│  │ Transaction Coordinator (manages txn state)          │          │
│  │                                                      │          │
│  │ Transaction Log (__transaction_state):               │          │
│  │   txn-1: BEGIN → [orders:P0, inventory:P2] → COMMIT │          │
│  │                                                      │          │
│  │ On COMMIT: control messages written to each partition │          │
│  │   orders:P0 → COMMIT marker                          │          │
│  │   inventory:P2 → COMMIT marker                       │          │
│  │   __consumer_offsets → offset entry                  │          │
│  │                                                      │          │
│  │ read_committed consumers: skip until COMMIT marker    │          │
│  └────────────────────────────────────────────────────┘          │
└──────────────────────────────────────────────────────────────────┘
```

---

## Code

### Transactional Consume-Transform-Produce

```java
@Configuration
public class TransactionalConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-processor-");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> pf) {
        return new KafkaTransactionManager<>(pf);
    }
}

@Service
public class OrderProcessor {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional("kafkaTransactionManager")
    public void processOrder(ConsumerRecord<String, OrderEvent> input) {
        OrderEvent order = input.value();
        
        // Transform
        InvoiceEvent invoice = buildInvoice(order);
        InventoryEvent inventory = buildInventoryReduction(order);
        
        // Atomic writes to multiple topics
        kafkaTemplate.send("invoices", order.getOrderId(), invoice);
        kafkaTemplate.send("inventory-updates", order.getOrderId(), inventory);
        
        // Consumer offset committed as part of this transaction
        // If anything fails → all rolled back, input re-consumed
    }
}
```

---

## Interview Questions

### Q1: What is the difference between idempotent producer and transactional producer?

**A:**
- **Idempotent:** Prevents duplicates within a single partition from retries. Scope: per-partition. No cross-partition atomicity.
- **Transactional:** Provides atomic writes across multiple topics/partitions. All messages in a transaction either all visible or all invisible. Can also atomically commit consumer offsets.
- Transactions require (and enable) idempotence as a foundation. Use transactions for consume-transform-produce patterns; idempotent producer alone is sufficient for simple produce-only scenarios.

### Q2: How does Kafka prevent zombie producers in transactions?

**A:** Through **fencing** using the transactional.id:
- Each transactional.id has a monotonically increasing epoch
- When a new producer initializes with the same transactional.id, it gets a higher epoch
- The broker rejects requests from old epochs → zombie producer's writes are invalid
- Any in-progress transaction from the zombie is aborted
- This prevents split-brain scenarios where two instances of the same processor are active

### Q3: What is the performance cost of transactions?

**A:** Moderate overhead:
- Extra round trips: initTransactions, beginTransaction, commitTransaction
- Transaction coordinator load (manages state)
- read_committed consumers have higher latency (wait for commit markers)
- Long-running transactions block LSO advancement → may increase consumer lag for other consumers
- Best practice: keep transactions short, batch commits, use for critical paths only

---

## Best Practices

1. **Keep transactions short** — long transactions block other consumers (LSO)
2. **Use unique transactional.id per instance** — prevent fencing issues
3. **Enable read_committed** on consumers that read from transactional topics
4. **Don't mix transactional and non-transactional writes** to the same topic
5. **Use for consume-transform-produce** — the primary use case
6. **Set transaction.timeout.ms** appropriately (default 1 minute)

---

## Related Topics

- [12. Message Delivery Semantics](./12-message-delivery-semantics.md)
- [21. Idempotency](./21-idempotency.md)
- [25. Kafka + Spring Boot](./25-kafka-spring-boot.md)
