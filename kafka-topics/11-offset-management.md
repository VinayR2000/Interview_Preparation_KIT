# 11. Offset Management ⭐⭐⭐

---

## Theory

**Offsets** are the mechanism by which Kafka tracks a consumer's position in a partition. Proper offset management is critical for delivery guarantees.

### What is Offset?

A sequential, monotonically increasing 64-bit integer assigned to each record within a partition.

```
Partition 0:
  Offset: 0    1    2    3    4    5    6    7    8    9
  Record: R0   R1   R2   R3   R4   R5   R6   R7   R8   R9
                              ↑              ↑         ↑
                        Committed      Current    Log-End
                         Offset        Offset      Offset
```

### Current Offset

The offset of the next record that will be returned to the consumer on `poll()`. Maintained in-memory by the consumer client.

### Committed Offset

The last offset the consumer has explicitly confirmed as processed. Stored durably in `__consumer_offsets` topic.

```
Committed Offset = "I have successfully processed all messages up to here"

On consumer restart:
  → Resumes from committed offset (not current offset)
  → Messages between committed and current offset may be reprocessed
```

### Log End Offset (LEO)

The offset of the next record to be written by the producer. Represents the "end" of the partition.

### Consumer Lag

```
Consumer Lag = Log End Offset - Committed Offset

Example:
  LEO = 1000 (producer has written 1000 records)
  Committed Offset = 950 (consumer has processed up to 950)
  Lag = 50 records behind

Growing lag indicates:
  - Consumer is slower than producer
  - Consumer was down and is catching up
  - Processing bottleneck (slow DB, external service)
```

### Offset Storage

Offsets are stored in a special internal topic: `__consumer_offsets`

```
__consumer_offsets (50 partitions by default):
  Key: (group.id, topic, partition)
  Value: (committed offset, metadata, timestamp)

  Partition selection: hash(group.id) % 50

  Example entries:
  Key: ("order-group", "orders", 0) → Value: offset=950
  Key: ("order-group", "orders", 1) → Value: offset=823
  Key: ("analytics-group", "orders", 0) → Value: offset=500

  This topic is compacted → only latest offset per key retained
```

### Automatic Commit

```java
enable.auto.commit = true (default)
auto.commit.interval.ms = 5000 (default)

How it works:
1. Consumer calls poll()
2. During poll(), checks if 5s elapsed since last commit
3. If yes: commits current offset for all assigned partitions
4. Not tied to processing success!

Risk scenarios:
  T=0: poll() returns records [offset 100-199]
  T=1: auto-commit fires → commits offset 200
  T=2: crash during processing record at offset 150
  T=3: restart → resumes from offset 200 → records 150-199 LOST!

  T=0: poll() returns records [offset 100-199]
  T=1: processing succeeds for all 100 records
  T=2: crash BEFORE auto-commit fires
  T=3: restart → resumes from offset 100 → records 100-199 REPROCESSED!
```

### Manual Commit

Gives full control over when offsets are committed.

```java
enable.auto.commit = false

Two approaches:
1. commitSync() — blocks until broker confirms commit
2. commitAsync() — non-blocking, fire-and-forget commit
```

### commitSync()

```java
consumer.commitSync();  // commit all partitions' current offsets

// With specific offsets:
Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(
    new TopicPartition("orders", 0), new OffsetAndMetadata(150),
    new TopicPartition("orders", 1), new OffsetAndMetadata(83)
);
consumer.commitSync(offsets);

// Behavior:
// - Blocks until broker acknowledges
// - Retries on failure (retriable errors)
// - Throws on non-retriable errors
// - Slower but guaranteed
```

### commitAsync()

```java
consumer.commitAsync();  // non-blocking

consumer.commitAsync((offsets, exception) -> {
    if (exception != null) {
        log.error("Commit failed: {}", offsets, exception);
        // Usually just log — don't retry (newer commit may have already succeeded)
    }
});

// Behavior:
// - Non-blocking (returns immediately)
// - No retries (to avoid out-of-order commits)
// - Use callback to detect failures
// - Higher throughput than commitSync
```

### Offset Reset

When a consumer has no committed offset (new group) or offset is out of range:

```
auto.offset.reset = earliest
  → Start from offset 0 (beginning of retained data)

auto.offset.reset = latest
  → Start from current end (only new messages)

auto.offset.reset = none
  → Throw exception (fail explicitly)

When offset is out of range (e.g., committed offset deleted due to retention):
  → Same reset policy applies
```

### Offset Replay

Ability to re-read messages by resetting offset to an earlier position.

```java
// Seek to beginning
consumer.seekToBeginning(consumer.assignment());

// Seek to specific offset
consumer.seek(new TopicPartition("orders", 0), 100);

// Seek to timestamp
Map<TopicPartition, Long> timestamps = Map.of(
    new TopicPartition("orders", 0), Instant.parse("2024-01-15T00:00:00Z").toEpochMilli()
);
Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestamps);
offsets.forEach((tp, ot) -> consumer.seek(tp, ot.offset()));
```

---

## Diagram

### Offset Positions Explained

```
Partition 0:
┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
│ R0 │ R1 │ R2 │ R3 │ R4 │ R5 │ R6 │ R7 │ R8 │ R9 │    │
└────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
  0    1    2    3    4    5    6    7    8    9    10
                     ↑                   ↑         ↑
               Committed=4          Current=8    LEO=10
               (stored in           (in-memory,  (next
               __consumer_offsets)   next to     write
                                    return)      position)

  Records 0-3: Processed AND committed ✓
  Records 4-7: Processed but NOT committed (will be reprocessed on crash)
  Records 8-9: Not yet fetched
  Offset 10: Next producer write position

  Consumer Lag = LEO - Committed = 10 - 4 = 6
```

### Auto-Commit vs Manual Commit

```
AUTO-COMMIT (enable.auto.commit=true):
─────────────────────────────────────────────────────────────────

  poll()    process    process    poll()    process    CRASH
    │         │          │         │         │          │
    ▼         ▼          ▼         ▼         ▼          ▼
────┼─────────┼──────────┼─────────┼─────────┼──────────┼────►
    │              5s elapses       │                    │
    │                ↓              │                    │
    │           AUTO-COMMIT         │                    │
    │           (offset=200)        │                    │
    │                               │                    │
    │         Records 100-199       │   Records 200-250  │
    │         processed ✓           │   processing...    │
    │         committed ✓           │   NOT committed!   │
                                                         │
    On restart: resume from 200 → records 200-250 REPROCESSED


MANUAL-COMMIT (enable.auto.commit=false):
─────────────────────────────────────────────────────────────────

  poll()    process    COMMIT    poll()    process    CRASH
    │         │          │        │         │          │
    ▼         ▼          ▼        ▼         ▼          ▼
────┼─────────┼──────────┼────────┼─────────┼──────────┼────►
    │         │          │        │         │          │
    │  Records 100-199   │ commit │ Records │          │
    │  ALL processed ✓   │ 200 ✓  │ 200-249 │ 200-249  │
                                           NOT committed!
    On restart: resume from 200 → records 200-249 REPROCESSED
    (at-least-once: guaranteed no data loss, possible duplicates)
```

---

## Code

### Manual Offset Commit Patterns

```java
// Pattern 1: Commit after each batch
@KafkaListener(topics = "orders", groupId = "order-group")
public void consume(List<ConsumerRecord<String, Object>> records, 
                    Acknowledgment ack) {
    for (ConsumerRecord<String, Object> record : records) {
        processOrder(record.value());
    }
    ack.acknowledge();  // commits offset for entire batch
}

// Pattern 2: Commit after each record (safest, lowest throughput)
@KafkaListener(topics = "orders", groupId = "order-group")
public void consume(ConsumerRecord<String, Object> record,
                    Acknowledgment ack) {
    processOrder(record.value());
    ack.acknowledge();  // commits after EACH record
}

// Pattern 3: Commit periodically (balance of safety and throughput)
public void consumeManually() {
    int count = 0;
    while (true) {
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<String, Object> record : records) {
            processRecord(record);
            count++;
            if (count % 100 == 0) {
                consumer.commitAsync();  // commit every 100 records
            }
        }
    }
}

// Pattern 4: Commit specific offsets (finest control)
public void consumeWithPerPartitionCommit() {
    while (true) {
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(100));
        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<String, Object>> partRecords = records.records(partition);
            for (ConsumerRecord<String, Object> record : partRecords) {
                processRecord(record);
            }
            long lastOffset = partRecords.get(partRecords.size() - 1).offset();
            consumer.commitSync(Map.of(
                partition, new OffsetAndMetadata(lastOffset + 1)
            ));
        }
    }
}
```

### Offset Reset via CLI

```bash
# Reset consumer group offsets to earliest
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-group --topic orders --reset-offsets --to-earliest --execute

# Reset to specific timestamp
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-group --topic orders \
  --reset-offsets --to-datetime 2024-01-15T00:00:00.000 --execute

# Reset to specific offset
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-group --topic orders:0 \
  --reset-offsets --to-offset 500 --execute

# Shift by N records
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-group --topic orders \
  --reset-offsets --shift-by -100 --execute
```

---

## Dry Run

### Offset Commit with Failure

```
Configuration: enable.auto.commit=false, manual commit after batch

Time 0: poll() returns 5 records from P0 (offsets 100-104)
  Committed offset for P0: 100

Time 1: Process record at offset 100 → success
Time 2: Process record at offset 101 → success
Time 3: Process record at offset 102 → success
Time 4: Process record at offset 103 → EXCEPTION!

  Option A: Don't commit, throw exception
    → Offset stays at 100
    → Next poll (or restart): re-reads from 100
    → Records 100-102 reprocessed (MUST handle idempotently)
    → Record 103 retried

  Option B: Commit up to 103 (last successful + 1)
    → consumer.commitSync(Map.of(P0, new OffsetAndMetadata(103)))
    → Next poll: starts from 103
    → Record 103 retried, 100-102 not reprocessed
    → BUT: requires per-record offset tracking

  Option C: Skip bad record, commit all
    → Log error for record 103, continue
    → Process record 104 → success
    → Commit offset 105
    → Record 103 LOST (acceptable for non-critical data)
```

---

## Interview Questions

### Q1: What is the difference between committed offset and current offset?

**A:** **Current offset** is the in-memory position of the next record to be returned by `poll()`. **Committed offset** is durably stored in `__consumer_offsets` — the position from which a consumer will resume after restart. The gap between them represents "processed but not yet safely committed" records. On crash, records between committed and current are reprocessed.

### Q2: Why would you choose commitSync() over commitAsync()?

**A:**
- **commitSync():** Blocks and retries until success. Use when you need guaranteed commit before proceeding (e.g., before shutdown, in `onPartitionsRevoked()`). Slower due to blocking.
- **commitAsync():** Non-blocking, no retry. Use during normal processing for throughput. Failure is usually acceptable because the next commit will cover the same offsets.
- **Common pattern:** Use commitAsync() during processing, commitSync() on shutdown/rebalance.

### Q3: How would you replay events from a specific timestamp?

**A:** Use `consumer.offsetsForTimes()` to find the offset corresponding to the timestamp, then `consumer.seek()` to position at that offset:
```java
Map<TopicPartition, Long> timestamps = Map.of(tp, targetTimestamp);
Map<TopicPartition, OffsetAndTimestamp> result = consumer.offsetsForTimes(timestamps);
consumer.seek(tp, result.get(tp).offset());
```
Or via CLI: `kafka-consumer-groups.sh --reset-offsets --to-datetime <timestamp>`

### Q4: What happens if a committed offset points to a deleted record (retention)?

**A:** The consumer gets an `OffsetOutOfRangeException` because the committed offset is older than the earliest available message. The behavior depends on `auto.offset.reset`:
- `earliest`: Consumer resets to the earliest available offset
- `latest`: Consumer jumps to the end (skips all retained messages)
- `none`: Consumer throws exception and stops

This typically happens when a consumer is down longer than the retention period.

### Q5: How does consumer lag affect the system?

**A:** Consumer lag (LEO - committed offset) indicates how far behind a consumer is:
- **Growing lag:** Consumer slower than producer → stale data, delayed processing
- **Constant lag:** Consumer keeping up at steady state
- **Lag = 0:** Consumer fully caught up (ideal for real-time)

Impact: Growing lag means events are delayed (late notifications, stale dashboards). Solutions: scale consumers, increase max.poll.records, optimize processing, increase partitions.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Auto-commit with slow processing | Data loss if crash after commit but before processing completes | Use manual commit |
| Committing before processing | Data loss on failure | Always process first, commit second |
| Not committing in onPartitionsRevoked | Duplicate processing after rebalance | Commit in rebalance listener |
| Retrying commitAsync failures | Out-of-order commits (older offset overwrites newer) | Don't retry async commits |
| Never monitoring consumer lag | Undetected processing delays | Alert on lag thresholds |

---

## Best Practices

1. **Use manual commit** for critical data (at-least-once guarantee)
2. **Commit per batch** for balance of safety and throughput
3. **Implement idempotent processing** — offsets may replay on failure
4. **Monitor consumer lag** — alert when lag grows beyond threshold
5. **Use commitSync in shutdown/rebalance** — guaranteed commit
6. **Use commitAsync during processing** — better throughput
7. **Consider committing to external store** — for exactly-once (offset + result atomically)

---

## Related Topics

- [08. Consumer](./08-consumer.md)
- [09. Consumer Groups](./09-consumer-groups.md)
- [12. Message Delivery Semantics](./12-message-delivery-semantics.md)
- [21. Idempotency](./21-idempotency.md)
