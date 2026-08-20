# 5. Partitions ⭐⭐⭐

---

## Theory

Partitions are the **unit of parallelism** in Kafka. A topic is split into one or more partitions, each being an ordered, immutable sequence of records stored on a single broker.

### Why Partitions?

```
Without partitions (single log):
  Producer → [one sequence] → One consumer
  Bottleneck: single read/write path

With partitions (parallel logs):
  Producer → P0 → Consumer 1
           → P1 → Consumer 2
           → P2 → Consumer 3
  Throughput multiplied by partition count
```

1. **Parallelism:** Multiple consumers can read concurrently
2. **Scalability:** Spread data across multiple brokers/disks
3. **Throughput:** Each partition can be on a different broker (network/disk)
4. **Ordering domains:** Group related messages in same partition

### Parallelism

```
Topic with 6 partitions:
- Max parallel consumers in one group = 6
- Each partition served by exactly one consumer in a group
- Multiple groups each get all partitions independently

Throughput calculation:
  Single partition throughput: ~10-30 MB/s (depends on message size, disk)
  6 partitions → 60-180 MB/s aggregate throughput
  12 partitions → 120-360 MB/s aggregate throughput
```

### Partition Ordering

**Kafka guarantees ordering ONLY within a single partition, NOT across partitions.**

```
Partition 0: [A, B, C, D]    → Consumer reads A→B→C→D (guaranteed order)
Partition 1: [E, F, G]       → Consumer reads E→F→G (guaranteed order)

But NO guarantee about interleaving:
  Consumer might see: A, E, B, F, C, G, D
  Or: E, A, F, B, C, D, G
  Or any other interleaving of partition-internal orders
```

### Partition Assignment (Producer Side)

When a producer sends a message, the partition is determined by:

```
1. Explicit partition: producer specifies partition number
   record = new ProducerRecord<>("topic", 2, key, value);  // partition 2

2. Key-based (hash): key present → hash(key) % numPartitions
   record = new ProducerRecord<>("topic", "user-123", value);
   // partition = murmur2("user-123") % numPartitions
   // Same key ALWAYS goes to same partition (if partition count unchanged)

3. No key (round-robin / sticky):
   record = new ProducerRecord<>("topic", value);
   // Kafka 2.4+: Sticky partitioner (fills one batch, then switches)
   // Before 2.4: Round-robin across partitions
```

### Partition Key

The message key determines partition placement and ordering domain.

```
Key design examples:

Order processing: key = orderId
  → All events for order-123 go to same partition
  → Ordered: OrderCreated → OrderPaid → OrderShipped

User activity: key = userId
  → All events for user-456 go to same partition
  → Can process user's full history in order

IoT sensors: key = sensorId
  → All readings from sensor-789 go to same partition
  → Temperature readings arrive in order per sensor
```

### Hash Partitioning

```java
// Kafka's default partitioner uses murmur2 hash:
int partition = Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions;

// Properties of murmur2:
// - Deterministic: same key → always same partition
// - Uniform distribution: keys spread evenly
// - Fast: non-cryptographic hash

// IMPORTANT: If partition count changes, key→partition mapping changes!
// key "user-123" might go to P2 with 6 partitions, P5 with 12 partitions
```

### Custom Partitioner

```java
public class OrderPriorityPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                         Object value, byte[] valueBytes, Cluster cluster) {
        
        int numPartitions = cluster.partitionCountForTopic(topic);
        
        if (key == null) {
            // No key → random partition
            return ThreadLocalRandom.current().nextInt(numPartitions);
        }
        
        String orderKey = (String) key;
        
        // Priority orders go to partition 0 (dedicated fast consumer)
        if (orderKey.startsWith("PRIORITY-")) {
            return 0;
        }
        
        // Regular orders distributed across remaining partitions
        return Utils.toPositive(Utils.murmur2(keyBytes)) % (numPartitions - 1) + 1;
    }

    @Override
    public void close() { }

    @Override
    public void configure(Map<String, ?> configs) { }
}
```

### Partition Count — Choosing and Changing

```
Choosing initial count:
- Formula: max(throughputRequired / throughputPerPartition, maxConsumers)
- Example: 100 MB/s needed, 10 MB/s per partition → at least 10
- Start with more than minimum (hard to reduce later)
- Common: 6 (small), 12 (medium), 30-50 (high throughput)

Increasing partitions:
- Can be done live (no downtime)
- BUT: messages with same key may go to different partition!
- New messages hash to new partition count
- Old messages stay in original partition
- Breaks key ordering if consumers rely on it

Decreasing partitions:
- NOT POSSIBLE without recreating topic
- Must create new topic with fewer partitions and migrate data
```

### Ordering Guarantees

```
Within a single partition:
  ✓ Total order guaranteed
  ✓ Offset is sequential (0, 1, 2, 3...)
  ✓ Consumer reads in exact write order

Across partitions:
  ✗ No ordering guarantee
  ✗ Messages in P0 and P1 can arrive in any interleaved order

Achieving order for related events:
  Strategy: Use same key for related messages
  - orderId as key → all order events in same partition → ordered

Global ordering (very rare need):
  - Use single partition (kills parallelism)
  - Or use sequence numbers and reorder at consumer
```

---

## Diagram

### Partition Distribution Across Brokers

```
Topic: "transactions" (6 partitions, RF=3)

Broker 1          Broker 2          Broker 3
┌───────────┐    ┌───────────┐    ┌───────────┐
│ P0 (L)    │    │ P0 (F)    │    │ P0 (F)    │
│ P1 (F)    │    │ P1 (L)    │    │ P1 (F)    │
│ P2 (F)    │    │ P2 (F)    │    │ P2 (L)    │
│ P3 (L)    │    │ P3 (F)    │    │ P3 (F)    │
│ P4 (F)    │    │ P4 (L)    │    │ P4 (F)    │
│ P5 (F)    │    │ P5 (F)    │    │ P5 (L)    │
└───────────┘    └───────────┘    └───────────┘

Leaders distributed: Broker 1 = P0,P3  Broker 2 = P1,P4  Broker 3 = P2,P5
(Even leader distribution = balanced load)
```

### Key-Based Partition Assignment

```
Producer sends orders with key = orderId:

hash("order-1") % 6 = 3  →  ┌─── P3: [order-1-created, order-1-paid, order-1-shipped]
hash("order-2") % 6 = 1  →  ├─── P1: [order-2-created, order-2-cancelled]
hash("order-3") % 6 = 5  →  ├─── P5: [order-3-created, order-3-paid]
hash("order-4") % 6 = 3  →  └─── P3: [order-1-created, order-1-paid, order-4-created, ...]
                                        ↑ order-1 and order-4 share partition (same hash bucket)
                                        ↑ All order-1 events are ORDERED within P3
```

---

## Dry Run

### Partition Selection with Key Changes

```java
// Topic "users" has 4 partitions
// Key: userId

producer.send(new ProducerRecord<>("users", "user-A", "login"));
// hash("user-A") % 4 = 2 → Partition 2, offset 0

producer.send(new ProducerRecord<>("users", "user-B", "login"));
// hash("user-B") % 4 = 0 → Partition 0, offset 0

producer.send(new ProducerRecord<>("users", "user-A", "purchase"));
// hash("user-A") % 4 = 2 → Partition 2, offset 1 (same partition!)

producer.send(new ProducerRecord<>("users", null, "anonymous-event"));
// No key → sticky partitioner picks current sticky partition (say P1)
// Partition 1, offset 0

// Result:
// P0: ["user-B:login"]
// P1: ["anonymous-event"]
// P2: ["user-A:login", "user-A:purchase"]  ← user-A ordered!
// P3: (empty)

// NOW: Admin increases partitions from 4 to 8
// hash("user-A") % 8 = 6  ← DIFFERENT partition!
producer.send(new ProducerRecord<>("users", "user-A", "logout"));
// Goes to Partition 6, offset 0

// user-A events are now SPLIT across P2 and P6 — ordering broken for new messages!
```

---

## Interview Questions

### Q1: How does Kafka decide which partition a message goes to?

**A:** Three mechanisms:
1. **Explicit partition:** Producer specifies partition number directly.
2. **Key hash:** `murmur2(key) % numPartitions` — deterministic, same key → same partition.
3. **No key:** Sticky partitioner (Kafka 2.4+) fills one batch then rotates, or round-robin in older versions.
Priority: explicit > key > default strategy.

### Q2: What happens to message ordering when you increase partitions?

**A:** Existing messages stay in their original partitions. New messages with the same key may hash to a different partition (since `hash % newPartitionCount` gives different results). This breaks ordering for that key — events before and after the change are in different partitions. To avoid this:
- Choose partition count carefully upfront
- If you must increase, wait for consumers to fully process existing messages first
- Consider using a new topic instead

### Q3: How many partitions should you use?

**A:** Guidelines:
- **Minimum:** max(peak_throughput / per_partition_throughput, max_consumers)
- **Per-partition throughput:** typically 10-30 MB/s (limited by disk/network)
- **Overhead of many partitions:** more file descriptors, longer leader election, more memory (each partition uses buffers)
- **Over-partitioning cost:** empty partitions waste resources
- **Under-partitioning cost:** can't scale consumers, bottleneck
- **Practical:** 6 partitions for low traffic, 12-30 for moderate, 50-100 for high throughput

### Q4: Why would you implement a custom partitioner?

**A:** Use cases:
- **Priority routing:** High-priority messages to specific partitions with dedicated consumers
- **Geographic routing:** Messages to partitions based on region
- **Hot key mitigation:** Spread hot keys across multiple partitions to avoid overloading
- **Weighted distribution:** More messages to certain partitions based on capacity
- **Tenant isolation:** Multi-tenant systems where each tenant gets dedicated partitions

### Q5: What is the sticky partitioner and why was it introduced?

**A:** Before Kafka 2.4, messages without keys used round-robin (one message per partition per batch cycle). This created many small batches — poor throughput. Sticky partitioner sticks to one partition until a batch is full, then switches. This creates fewer, larger batches → better compression, less network overhead, higher throughput. Ordering doesn't matter for keyless messages, so stickiness is safe.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Changing partition count for key-based topics | Breaks ordering for existing keys | Plan count upfront, or use new topic |
| Using timestamps as keys | Unique keys → no partition affinity | Use entity ID (orderId, userId) |
| Hot partition from popular key | One partition gets all traffic | Use sub-keys or custom partitioner |
| Too few partitions | Can't scale consumers | Start with at least 6 in production |
| Assuming cross-partition ordering | Events from different partitions interleave | Design for per-partition ordering only |

---

## Best Practices

1. **Choose keys that represent ordering domains** — orderId for order events, userId for user events
2. **Monitor partition size distribution** — ensure even spread
3. **Plan partition count for growth** — easier to over-provision than to increase later
4. **Use custom partitioner** for special routing needs
5. **Document key design** — future developers need to understand ordering guarantees
6. **Never decrease partitions** — design to avoid this from the start
7. **Watch for hot partitions** — monitor per-partition throughput metrics

---

## Related Topics

- [06. Producer](./06-producer.md)
- [09. Consumer Groups](./09-consumer-groups.md)
- [10. Consumer Rebalancing](./10-consumer-rebalancing.md)
- [11. Offset Management](./11-offset-management.md)
