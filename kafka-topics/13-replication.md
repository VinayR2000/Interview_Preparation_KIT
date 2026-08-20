# 13. Replication ⭐⭐⭐

---

## Theory

**Replication** is Kafka's mechanism for fault tolerance. Each partition is replicated across multiple brokers so that if one broker fails, data is still available.

### Replication Factor

```
Replication Factor (RF) = total number of copies (leader + followers)

RF=1: Single copy, no fault tolerance (lose broker = lose data)
RF=2: 2 copies, survive 1 broker failure
RF=3: 3 copies, survive 2 broker failures (PRODUCTION STANDARD)

Rule: RF ≤ number of brokers in cluster
```

### Leader Replica

The replica that handles ALL read and write requests for a partition.

```
Responsibilities:
- Accept produce requests from producers
- Serve fetch requests from consumers
- Track ISR (which followers are caught up)
- Manage high watermark (HW)
```

### Follower Replica

Passive replicas that fetch data from the leader to stay in sync.

```
Responsibilities:
- Send FetchRequests to leader (same mechanism as consumer!)
- Replicate all records from leader
- If leader dies → eligible for promotion to leader (if in ISR)
- Does NOT serve client requests (by default, since Kafka 2.4 can serve reads)
```

### ISR (In-Sync Replicas)

The set of replicas that are fully caught up with the leader.

```
ISR membership criteria:
- Replica must have fetched ALL messages up to leader's LEO
- Must have fetched within replica.lag.time.max.ms (default 30s)

Example:
  Leader (Broker 1): LEO = 1000
  Follower (Broker 2): caught up to 1000 → IN ISR ✓
  Follower (Broker 3): caught up to 985, last fetch 5s ago → IN ISR ✓
  Follower (Broker 3): caught up to 900, last fetch 35s ago → OUT OF ISR ✗

ISR is dynamic — shrinks and expands as followers catch up or fall behind
```

### Out-of-Sync Replica (OSR)

```
A follower that has fallen behind the leader beyond replica.lag.time.max.ms.

Causes:
- Broker under heavy load (slow disk I/O)
- Network congestion between brokers
- Broker recovering from crash (catching up)
- GC pauses

Impact:
- OSR replicas NOT eligible for leader election (default)
- If all replicas are OSR + leader fails = NO LEADER (unless unclean election)
- Monitoring ISR shrinkage is critical
```

### Leader Election

When a leader replica fails, the controller must elect a new leader.

```
Clean Leader Election (default):
1. Controller detects leader failure (no heartbeat)
2. Selects new leader from ISR (first available)
3. Updates metadata (new leader assignment)
4. Notifies all brokers
5. Producers/consumers discover new leader via metadata refresh

Selection criteria:
- MUST be in ISR (unless unclean.leader.election.enable=true)
- Preferred leader: first replica in replica list (for balanced distribution)
```

### Failover

```
Normal operation:
  Broker 1: Partition 0 (Leader) ← Producer writes here
  Broker 2: Partition 0 (Follower, ISR)
  Broker 3: Partition 0 (Follower, ISR)

Broker 1 fails:
  Controller detects (heartbeat timeout)
  ISR = {Broker 2, Broker 3}
  New leader = Broker 2 (first in ISR)
  
  Broker 2: Partition 0 (NEW Leader) ← Producer now writes here
  Broker 3: Partition 0 (Follower, ISR) ← fetches from Broker 2

Broker 1 recovers:
  Rejoins cluster
  Truncates any uncommitted messages (after HW)
  Starts fetching from new leader (Broker 2)
  Once caught up → rejoins ISR
```

### Replica Recovery

```
When a failed broker recovers:
1. Starts up, connects to controller
2. For each partition it hosts:
   a. Check local log's last offset
   b. Fetch leader's current HW (high watermark)
   c. Truncate local log to HW (remove any records beyond HW)
      → Why? Records beyond HW were not replicated, may not match new leader
   d. Begin fetching from leader starting at HW
   e. Once caught up → rejoin ISR

Truncation is necessary because:
  Old leader wrote record at offset 100
  Follower (now recovering) has offset 100
  But NEW leader may not have offset 100 (wasn't in ISR when written)
  → Must align with current leader's log
```

### min.insync.replicas

```
min.insync.replicas = 2 (recommended for production)

What it means:
- With acks=all: minimum ISR members that must acknowledge for write to succeed
- If ISR size < min.insync.replicas: writes REJECTED (NotEnoughReplicasException)

Scenarios with RF=3, min.insync.replicas=2:
  ISR = {B1, B2, B3}: writes succeed (3 ≥ 2) ✓
  ISR = {B1, B2}:     writes succeed (2 ≥ 2) ✓
  ISR = {B1}:         writes REJECTED (1 < 2) ✗

This prevents writing to a single replica that could lose data!
```

### Unclean Leader Election

```
unclean.leader.election.enable = false (default)

What happens when ALL ISR replicas are down?
  - false: Partition is UNAVAILABLE until an ISR member recovers
    → No data loss, but availability sacrificed
    → System waits for ISR replica to come back
    
  - true: Elect ANY available replica (even out-of-sync) as leader
    → Partition available again immediately
    → But DATA LOSS for messages the new leader didn't replicate!
    → Only enable for topics where availability > durability

Trade-off: Availability vs Data Consistency (CAP theorem in action)
```

---

## Diagram

### Replication Architecture

```
Topic: "orders", Partition 0, RF=3

┌──────────────────────────────────────────────────────────────────┐
│                                                                    │
│  Broker 1                 Broker 2                Broker 3        │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ P0 (LEADER)     │    │ P0 (FOLLOWER)   │    │ P0 (FOLLOWER)│  │
│  │                  │    │                  │    │              │  │
│  │ Log:            │    │ Log:            │    │ Log:         │  │
│  │ [0][1][2][3][4] │    │ [0][1][2][3][4] │    │ [0][1][2][3]│  │
│  │ [5][6][7]       │    │ [5][6][7]       │    │ [4][5][6]   │  │
│  │        ↑ LEO=8  │    │        ↑ LEO=8  │    │       ↑LEO=7│  │
│  │                  │    │                  │    │              │  │
│  │ HW=7 (all ISR   │    │                  │    │  (lagging)  │  │
│  │ caught up to 7) │    │  ISR member ✓    │    │  ISR? check │  │
│  └────────┬─────────┘    └────────┬─────────┘    └──────┬──────┘  │
│           │                       ↑                      ↑         │
│           │         FetchRequest  │                      │         │
│           └───────────────────────┴──────────────────────┘         │
│                   (Followers pull from Leader)                      │
└──────────────────────────────────────────────────────────────────┘

Producer → writes to Leader (Broker 1)
Consumer → reads from Leader (up to HW=7)
Records 7: written to leader, replicated to B2, NOT yet to B3
           → NOT exposed to consumers (above HW)
```

### Failover Sequence

```
1. NORMAL:
   B1(Leader) ←writes── Producer
   B2(ISR) ←fetch── from B1
   B3(ISR) ←fetch── from B1

2. B1 FAILS:
   B1 ✗ (unreachable)
   Controller detects (no heartbeat for 30s)
   
3. LEADER ELECTION:
   ISR = {B2, B3}
   Controller selects B2 as new leader
   
4. NEW TOPOLOGY:
   B2(NEW Leader) ←writes── Producer (metadata refresh)
   B3(ISR) ←fetch── from B2
   
5. B1 RECOVERS:
   B1 starts up
   B1 truncates to HW (removes anything beyond committed)
   B1 fetches from B2 (new leader)
   B1 catches up → rejoins ISR
   ISR = {B2, B1, B3}
```

---

## Interview Questions

### Q1: What is the high watermark and why is it important?

**A:** The high watermark (HW) is the offset of the last record replicated to ALL ISR members. Consumers can only read up to HW. This ensures consumers never read a message that could be lost if the leader fails before replication. Records between HW and LEO are "uncommitted" — visible only to the leader internally.

### Q2: Explain the difference between ISR and all replicas.

**A:**
- **All replicas:** Every copy of a partition (RF count). Static list defined at topic creation.
- **ISR:** Dynamic subset of replicas currently caught up with the leader. Changes as followers lag or catch up.
- **Importance:** Only ISR members are eligible for leader election (default). `acks=all` only waits for ISR acknowledgment, not all replicas. This prevents a slow/dead follower from blocking writes.

### Q3: What happens when min.insync.replicas is not met?

**A:** With `acks=all` and ISR size < `min.insync.replicas`:
- Produce requests rejected with `NotEnoughReplicasException`
- Topic becomes **write-unavailable** (reads still work from existing data)
- Producer receives error, can retry or fail
- This is intentional — prevents writing to insufficient replicas which risks data loss
- Recovery: when enough replicas catch up and rejoin ISR, writes resume

### Q4: When would you enable unclean leader election?

**A:** Only when **availability is more important than data consistency**:
- Log/metrics topics where some data loss is acceptable
- Topics that can be rebuilt from source
- Non-critical notification systems
- **Never** for: financial data, order processing, audit logs
- In practice: most production systems keep it disabled (default) and accept partition unavailability until ISR recovers.

### Q5: How does a broker recover after a crash?

**A:**
1. Broker starts up, registers with controller
2. For each partition: checks local log's last offset
3. Contacts new leader, gets current HW
4. **Truncates** local log to match HW (records beyond HW may be from old leader and inconsistent with new leader)
5. Begins fetching from current leader's log (from HW forward)
6. Once fully caught up (within replica.lag.time.max.ms): rejoins ISR
7. Controller updates metadata to include broker in ISR

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| RF=1 in production | Any broker failure = data loss | Always RF=3 minimum |
| acks=all without min.insync.replicas | Single replica can satisfy acks=all if ISR shrinks to 1 | Set min.insync.replicas=2 |
| Enabling unclean leader election globally | Data loss on any partition failure | Only enable per-topic for non-critical data |
| Not monitoring ISR shrinkage | Silent degradation of durability | Alert on under-replicated partitions |
| Ignoring replica lag | Followers fall out of ISR → reduced fault tolerance | Monitor and fix slow brokers |

---

## Best Practices

1. **RF=3, min.insync.replicas=2, acks=all** — the gold standard for durability
2. **Monitor under-replicated partitions** — indicates broker health issues
3. **Keep unclean.leader.election.enable=false** for important topics
4. **Ensure even leader distribution** — run preferred leader election
5. **Monitor ISR expansion/shrinkage rate** — frequent changes indicate instability
6. **Size clusters so RF < broker count** — survive broker failures

---

## Related Topics

- [03. Kafka Architecture](./03-kafka-architecture.md)
- [14. Durability & Reliability](./14-durability-reliability.md)
- [30. Kafka Cluster Management](./30-kafka-cluster-management.md)
- [31. ZooKeeper & KRaft](./31-zookeeper-kraft.md)
