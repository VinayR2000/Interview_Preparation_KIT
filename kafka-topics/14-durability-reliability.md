# 14. Durability & Reliability ⭐⭐⭐

---

## Theory

Durability and reliability in Kafka are achieved through a combination of producer acknowledgments, replication, and proper configuration.

### The Durability Equation

```
Maximum Durability = acks=all + min.insync.replicas=2 + RF=3 + idempotent producer

This combination guarantees:
- Message written to at least 2 brokers before ack
- Retries don't create duplicates
- Survives any single broker failure without data loss
- Survives two broker failures for existing data
```

### acks=0 (No Durability)

```
Producer → send → done (no wait)
         (message may never reach broker)

Scenarios:
- Network drop: message LOST silently
- Broker down: message LOST silently
- Broker receives: message stored (but producer doesn't know)

Use: Ultra-high throughput, loss acceptable (metrics, logs)
Throughput: Highest
Risk: Silent data loss
```

### acks=1 (Leader Durability)

```
Producer → send → Leader writes → ACK to producer

Scenarios:
- Leader writes, acks, then crashes before replication: DATA LOST
- Leader writes, acks, followers replicate: SAFE
- Leader rejects (full, error): Producer gets error

Window of data loss:
  Time ─────────────────────────────────────────►
  Leader writes │ ACK sent │ Follower replicates
                │           │
                └── CRASH HERE = data lost
                    (message on leader only, not replicated)
```

### acks=all (Full Durability)

```
Producer → send → Leader writes → ALL ISR replicate → ACK to producer

Scenarios:
- All ISR acknowledge: SAFE (multiple copies exist)
- ISR member slow: Producer waits (higher latency)
- ISR < min.insync.replicas: Write REJECTED (availability sacrifice)

No data loss window (with min.insync.replicas ≥ 2):
  Time ─────────────────────────────────────────►
  Leader writes │ ISR replicates │ ACK sent
                                  │
                                  └── Even if leader crashes NOW,
                                      data exists on followers
```

### Replication + ISR + min.insync.replicas

```
Configuration Matrix:

| RF | min.insync.replicas | acks | Survives | Data Loss Risk |
|----|---------------------|------|----------|----------------|
| 1  | 1                   | 1    | Nothing  | HIGH           |
| 3  | 1                   | all  | 2 brokers| MEDIUM (ISR=1 possible) |
| 3  | 2                   | all  | 1 broker | LOW            |
| 3  | 3                   | all  | 0 brokers| NONE (but low availability) |

RECOMMENDED: RF=3, min.insync.replicas=2, acks=all
  → Survives 1 broker failure for writes
  → Survives 2 broker failures for reads
  → Good balance of durability and availability
```

### Producer Retries

```
retries = MAX_INT (default)
delivery.timeout.ms = 120000 (2 minutes)

Retry behavior:
1. Send fails (network error, leader unavailable)
2. Wait retry.backoff.ms (default 100ms)
3. Retry send
4. If delivery.timeout.ms exceeded → fail permanently
5. With idempotence: retries are safe (no duplicates)

Retriable errors:
- LEADER_NOT_AVAILABLE (leader election in progress)
- NOT_ENOUGH_REPLICAS (ISR too small, transient)
- REQUEST_TIMED_OUT (broker slow)
- NETWORK_EXCEPTION (transient network issue)

Non-retriable errors:
- MESSAGE_TOO_LARGE (record exceeds max.message.bytes)
- INVALID_TOPIC_EXCEPTION (bad topic name)
- TOPIC_AUTHORIZATION_FAILED (permission denied)
```

### Idempotent Producer

```
enable.idempotence = true

Mechanism:
  Producer assigned PID (Producer ID) on init
  Each message gets sequence number per (PID, partition)
  Broker tracks: {PID, partition} → last sequence number

  Duplicate detection:
    Message arrives with seq=5
    Broker's last recorded seq=5 → DUPLICATE → discard, return success
    
  Out-of-order detection:
    Broker's last seq=3, message arrives with seq=5 (gap!)
    → Reject with OutOfOrderSequenceException
    → Producer retries seq=4 first

Guarantees:
- No duplicates from retries
- Ordering maintained with up to 5 in-flight requests
- Zero performance overhead (negligible)
```

### Data Loss Scenarios

```
Scenario 1: acks=1, leader crashes after ack
  Producer sends → Leader writes (offset 100) → ACK sent → Leader CRASHES
  Follower only has up to offset 99
  New leader (follower) has offset 99 as LEO
  Offset 100 LOST forever

Scenario 2: min.insync.replicas=1, ISR shrinks to leader only
  ISR = {Leader} (followers fell behind)
  Producer sends with acks=all → only leader acks (ISR=1 counts as "all")
  Leader crashes → data LOST
  Fix: min.insync.replicas=2 prevents writes when ISR too small

Scenario 3: Unclean leader election
  All ISR replicas down
  unclean.leader.election.enable=true
  Out-of-sync replica becomes leader
  Messages it never received → LOST

Scenario 4: No data loss (proper config)
  RF=3, min.insync.replicas=2, acks=all
  Producer sends → Leader + 1 follower acknowledge → ACK
  Leader crashes → follower (with data) becomes leader
  No data lost ✓
```

### Duplicate Messages

```
When duplicates occur:
1. Producer timeout → retry → broker had actually written first attempt
   Fix: idempotent producer

2. Consumer processes → crash before commit → restart → reprocesses
   Fix: idempotent consumer (deduplication)

3. Network partition → producer thinks failed → retries to different broker
   Fix: idempotent producer handles this

Preventing duplicates:
  Producer side: enable.idempotence=true (automatic)
  Consumer side: application-level deduplication (event ID check)
```

---

## Diagram

### Durability Levels

```
LEVEL 1: acks=0 (No guarantee)
══════════════════════════════
Producer ──send──► (void)
  "I sent it, no idea if it arrived"
  Data loss: LIKELY on any failure

LEVEL 2: acks=1 (Leader only)
══════════════════════════════
Producer ──send──► Leader ──ACK──► Producer
                     │
                     └── Followers may not have it yet
  Data loss: POSSIBLE if leader crashes before replication

LEVEL 3: acks=all, min.insync.replicas=1 (ISR acknowledges)
═══════════════════════════════════════════════════════════
Producer ──send──► Leader ──replicate──► Follower(s) in ISR ──ACK──► Producer
  BUT: If ISR shrinks to just leader, effectively same as acks=1
  Data loss: POSSIBLE when ISR=1 and leader crashes

LEVEL 4: acks=all, min.insync.replicas=2, RF=3 (Production standard)
═══════════════════════════════════════════════════════════════════════
Producer ──send──► Leader ──replicate──► min 1 Follower ──ACK──► Producer
  At least 2 copies exist before producer gets ACK
  Leader crash → follower has data → becomes new leader
  Data loss: PREVENTED (single broker failure safe)
```

---

## Interview Questions

### Q1: How do you configure Kafka for zero data loss?

**A:** The configuration:
```properties
# Producer
acks=all
enable.idempotence=true
retries=MAX_VALUE
delivery.timeout.ms=120000

# Topic
replication.factor=3
min.insync.replicas=2

# Broker
unclean.leader.election.enable=false
```
This ensures: writes require 2+ replicas to acknowledge, retries are safe (idempotent), and no out-of-sync replica can become leader. Trade-off: higher latency and write unavailability when ISR < 2.

### Q2: What is the trade-off between durability and availability?

**A:**
- **More durable** (higher min.insync.replicas): Writes fail when ISR is too small → less available
- **More available** (lower min.insync.replicas): Writes succeed even with fewer replicas → risk of data loss
- With RF=3, min.insync.replicas=2: tolerates 1 broker failure (for writes). If 2 brokers fail, writes are unavailable but no data lost.
- With RF=3, min.insync.replicas=1: tolerates 2 broker failures (for writes) but if ISR shrinks to 1 and that broker crashes, data lost.
- CAP theorem: Kafka chooses CP (consistency/partition tolerance) with proper config.

### Q3: Explain how the idempotent producer prevents duplicates from retries.

**A:**
1. Producer gets unique PID (Producer ID) from broker on initialization
2. Producer assigns monotonically increasing sequence number per partition
3. Each message carries (PID, partition, sequence)
4. Broker stores mapping: (PID, partition) → last_committed_sequence
5. On retry: same message arrives with same (PID, partition, sequence)
6. Broker checks: "I already have sequence N for this PID/partition" → returns success without writing again
7. Effectively deduplicates at the broker level with negligible performance cost

### Q4: What happens to in-flight messages during a leader election?

**A:**
1. Leader fails → controller starts election (milliseconds to seconds)
2. In-flight produce requests from producer get timeout/error
3. Producer retries when new leader is available (metadata refresh)
4. With idempotent producer: no duplicates even if original message was partially written
5. Messages between old leader's HW and LEO are truncated on recovery
6. Consumer continues from new leader at HW (no data beyond HW exposed)
7. Brief unavailability (seconds) but no data loss with proper configuration

### Q5: What is the relationship between acks, ISR, and min.insync.replicas?

**A:**
- `acks` = how many acknowledgments the producer waits for
- `ISR` = replicas currently in sync with leader (dynamic)
- `min.insync.replicas` = minimum ISR size required for `acks=all` to succeed
- With `acks=all`: producer waits for ALL ISR members. If ISR < min.insync.replicas → write rejected.
- They work together: ISR determines who must ack, min.insync.replicas sets the minimum safety threshold.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| acks=all without min.insync.replicas=2 | ISR can shrink to 1, effectively acks=1 | Always set min.insync.replicas=2 |
| Not enabling idempotence | Duplicates on retries | enable.idempotence=true (default since 3.0) |
| Setting delivery.timeout too low | Legitimate retries fail | 120s default is usually good |
| Enabling unclean election for critical topics | Data loss on partition failure | Keep disabled (default) |
| Assuming RF=3 prevents all data loss | Without acks=all, leader can lose data | Full chain: RF + acks + min.insync |

---

## Best Practices

1. **Production config:** RF=3, min.insync.replicas=2, acks=all, idempotent producer
2. **Monitor under-replicated partitions** — early warning of durability degradation
3. **Test failure scenarios** — kill brokers and verify data survives
4. **Set meaningful delivery.timeout.ms** — enough for transient failures to resolve
5. **Never enable unclean leader election** for business-critical topics
6. **Design consumers for at-least-once** — expect and handle redelivery

---

## Related Topics

- [06. Producer](./06-producer.md)
- [13. Replication](./13-replication.md)
- [12. Message Delivery Semantics](./12-message-delivery-semantics.md)
- [21. Idempotency](./21-idempotency.md)
