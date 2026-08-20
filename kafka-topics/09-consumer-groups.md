# 9. Consumer Groups ⭐⭐⭐

---

## Theory

A **Consumer Group** is a set of consumers that cooperatively read from a topic. Each partition is assigned to exactly one consumer within a group — enabling parallel consumption while maintaining per-partition ordering.

### Consumer Group Fundamentals

```
Key Rules:
1. Each partition → exactly ONE consumer within a group
2. One consumer → can handle MULTIPLE partitions
3. Max useful consumers in a group = number of partitions
4. Different groups are INDEPENDENT (each gets all messages)
```

### Group ID

The identifier that binds consumers into a group. All consumers with the same `group.id` form one group.

```java
props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processing-group");

// Same group → load balancing (partitions split among consumers)
// Different group → broadcast (each group gets all messages)
```

### Partition Assignment Scenarios

```
Topic: "orders" (6 partitions)

Scenario 1: 1 consumer in group
  Consumer-1: P0, P1, P2, P3, P4, P5  (handles all)

Scenario 2: 3 consumers in group
  Consumer-1: P0, P1
  Consumer-2: P2, P3
  Consumer-3: P4, P5

Scenario 3: 6 consumers in group (ideal)
  Consumer-1: P0
  Consumer-2: P1
  Consumer-3: P2
  Consumer-4: P3
  Consumer-5: P4
  Consumer-6: P5

Scenario 4: 8 consumers in group (wasteful)
  Consumer-1: P0
  Consumer-2: P1
  Consumer-3: P2
  Consumer-4: P3
  Consumer-5: P4
  Consumer-6: P5
  Consumer-7: (IDLE — no partition available)
  Consumer-8: (IDLE — no partition available)
```

### Consumer Group Parallelism

```
Throughput scaling:
- 1 consumer, 6 partitions: ~10 MB/s (single threaded)
- 3 consumers, 6 partitions: ~30 MB/s (3× throughput)
- 6 consumers, 6 partitions: ~60 MB/s (max throughput)
- 12 consumers, 6 partitions: still ~60 MB/s (6 idle!)

To increase parallelism beyond partition count:
- Increase partition count (can't decrease later!)
- Use internal thread pool per consumer (risky with offsets)
- Use separate consumer groups (but changes semantics)
```

### Multiple Consumer Groups

```
Topic: "orders" (3 partitions)

Group A: "order-fulfillment"         Group B: "order-analytics"
  Consumer-A1: P0, P1                  Consumer-B1: P0
  Consumer-A2: P2                      Consumer-B2: P1, P2

Group C: "order-audit"
  Consumer-C1: P0, P1, P2

- Each group independently tracks offsets
- Each group receives ALL messages
- Groups are completely independent
- Use case: different services processing same events
```

### Consumer Rebalancing (Overview)

When consumers join or leave a group, partitions are redistributed.

```
Trigger events:
- Consumer joins group (new instance starts)
- Consumer leaves group (shutdown, crash)
- Consumer exceeds max.poll.interval.ms
- Topic partitions added
- Consumer subscribes to new topic (pattern subscription)

During rebalance:
- (Eager) ALL consumers stop processing, release partitions
- Coordinator reassigns partitions
- Consumers resume with new assignment
- Brief processing pause (seconds to minutes)
```

### Consumer Failure

```
Failure detection:
1. session.timeout.ms: No heartbeat → consumer dead
2. max.poll.interval.ms: No poll() → consumer stuck

Failure handling:
1. Coordinator detects failure
2. Triggers rebalance
3. Failed consumer's partitions reassigned
4. Other consumers pick up extra partitions
5. Processing resumes from last committed offset

Example:
  Before: C1→P0,P1  C2→P2,P3  C3→P4,P5
  C2 crashes
  After:  C1→P0,P1,P2  C3→P3,P4,P5
  P2,P3 resume from last committed offsets
```

### Group Coordinator

A broker elected to manage a specific consumer group.

```
Group Coordinator responsibilities:
- Track group membership (which consumers are alive)
- Handle JoinGroup/SyncGroup/LeaveGroup/Heartbeat requests
- Detect consumer failures
- Trigger rebalances
- Store committed offsets (in __consumer_offsets)

Coordinator selection:
- partition = hash(group.id) % __consumer_offsets partitions (50 by default)
- Coordinator = leader of that __consumer_offsets partition
```

---

## Diagram

### Consumer Group Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     Kafka Cluster                                  │
│                                                                    │
│  Topic: "orders" (6 partitions)                                   │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐                    │
│  │ P0 │ │ P1 │ │ P2 │ │ P3 │ │ P4 │ │ P5 │                    │
│  └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘ └──┬─┘                    │
│     │      │      │      │      │      │                         │
│     └──┬───┘      └──┬───┘      └──┬───┘                         │
│        │              │              │                             │
│  Group Coordinator (Broker 2)                                     │
│  ┌─────────────────────────────────────┐                         │
│  │ Manages: group "order-processors"    │                         │
│  │ Members: [C1, C2, C3]               │                         │
│  │ Assignment: C1→P0,P1 C2→P2,P3      │                         │
│  │             C3→P4,P5                 │                         │
│  │ Generation: 5                        │                         │
│  └─────────────────────────────────────┘                         │
└──────────────────────────────────────────────────────────────────┘
         │              │              │
         ▼              ▼              ▼
   ┌───────────┐ ┌───────────┐ ┌───────────┐
   │Consumer 1 │ │Consumer 2 │ │Consumer 3 │
   │           │ │           │ │           │
   │ P0 → □□□ │ │ P2 → □□□ │ │ P4 → □□□ │
   │ P1 → □□□ │ │ P3 → □□□ │ │ P5 → □□□ │
   └───────────┘ └───────────┘ └───────────┘
```

### Multiple Groups Reading Same Topic

```
                    Topic: "user-events" (4 partitions)
                    ┌────┐ ┌────┐ ┌────┐ ┌────┐
                    │ P0 │ │ P1 │ │ P2 │ │ P3 │
                    └─┬──┘ └─┬──┘ └─┬──┘ └─┬──┘
                      │      │      │      │
           ┌──────────┼──────┼──────┼──────┼──────────┐
           │          │      │      │      │          │
           ▼          ▼      ▼      ▼      ▼          ▼
    ┌─────────────────────────┐  ┌─────────────────────────┐
    │ Group: "notifications"   │  │ Group: "analytics"       │
    │                          │  │                          │
    │ C1: P0, P1              │  │ C1: P0                  │
    │ C2: P2, P3              │  │ C2: P1                  │
    │                          │  │ C3: P2                  │
    │ (2 consumers)           │  │ C4: P3                  │
    └─────────────────────────┘  │ (4 consumers)           │
                                  └─────────────────────────┘
    
    Both groups independently read ALL messages from ALL partitions
    Each group tracks its own offsets separately
```

---

## Dry Run

### Consumer Group Lifecycle

```
Time 0: Consumer C1 starts (first in group "orders-group")
  → JoinGroup request to coordinator
  → Coordinator: C1 is group leader (first member)
  → C1 receives assignment: P0, P1, P2, P3, P4, P5
  → C1 starts consuming all partitions

Time 10s: Consumer C2 starts (joins group)
  → JoinGroup request to coordinator
  → Coordinator triggers rebalance
  → C1 stops processing (revokes partitions in eager mode)
  → Leader (C1) computes new assignment:
    C1: P0, P1, P2
    C2: P3, P4, P5
  → SyncGroup: both receive new assignments
  → Both resume consuming

Time 20s: Consumer C3 starts (joins group)
  → Rebalance triggered again
  → New assignment:
    C1: P0, P1
    C2: P2, P3
    C3: P4, P5
  → All three consuming

Time 45s: C2 crashes (no heartbeat for session.timeout.ms)
  → Coordinator detects failure
  → Rebalance triggered
  → New assignment:
    C1: P0, P1, P2
    C3: P3, P4, P5
  → C1 picks up P2 from last committed offset
  → C3 picks up P3 from last committed offset
  → Messages in-flight at C2 (uncommitted) will be reprocessed

Time 60s: C2 restarts (rejoins group)
  → Rebalance → back to:
    C1: P0, P1
    C2: P2, P3
    C3: P4, P5
```

---

## Interview Questions

### Q1: How does Kafka ensure that each message is processed by only one consumer in a group?

**A:** By assigning each partition to exactly one consumer within a group. Since messages in a partition are read sequentially by a single consumer, no two consumers in the same group read the same message. The group coordinator manages this mapping and enforces it. If a consumer dies, its partitions are reassigned to other members (and messages may be reprocessed from last committed offset — hence "at-least-once").

### Q2: What happens when you add more consumers than partitions?

**A:** Extra consumers are idle (no partitions assigned). They serve as standby — if an active consumer fails, an idle consumer gets partitions immediately during rebalance. This is useful for high-availability but wastes resources. Solution: increase partition count if you need more parallelism.

### Q3: How would you design a system where multiple services need to process the same events?

**A:** Use different consumer groups. Each service subscribes with a unique group.id:
- "notification-service" group → sends notifications
- "analytics-service" group → builds reports
- "audit-service" group → writes audit log

Each group independently receives ALL messages and tracks its own offsets. This is the pub/sub pattern — one topic, multiple subscribers.

### Q4: How does the Group Coordinator work?

**A:** The coordinator is a broker selected using `hash(group.id) % 50` (number of `__consumer_offsets` partitions). It manages:
1. **Membership:** tracks who's in the group via heartbeats
2. **Rebalancing:** triggers when membership changes, delegates assignment computation to the group leader (a consumer)
3. **Offsets:** stores committed offsets in `__consumer_offsets`
4. If the coordinator broker fails, another broker becomes coordinator for that group.

### Q5: What is the group generation and why does it matter?

**A:** The generation is a monotonically increasing number that increments with each rebalance. It prevents stale consumers from committing offsets:
- Consumer gets assignment in generation 5
- Rebalance happens → generation 6
- Old consumer (still on generation 5) tries to commit → rejected (wrong generation)
- Prevents a slow/zombie consumer from corrupting newer consumer's progress

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| All instances using different group.id | Each gets all messages (no load balancing) | Use same group.id for scaling |
| More consumers than partitions | Wasted resources (idle consumers) | Match consumer count to partition count |
| Not handling rebalance listener | Uncommitted offsets lost during rebalance | Implement ConsumerRebalanceListener |
| Hardcoding partition assignment | No dynamic scaling | Use subscribe() instead of assign() |
| Long processing blocking poll loop | Consumer removed from group | Offload processing to thread pool |

---

## Best Practices

1. **Match consumer count to partition count** for optimal parallelism
2. **Use different group IDs** for different use cases reading same topic
3. **Implement ConsumerRebalanceListener** to handle partition revocation gracefully
4. **Monitor consumer group lag** per partition and consumer
5. **Plan partition count** to accommodate future scaling needs
6. **Use cooperative rebalancing** (Kafka 2.4+) to minimize processing downtime

---

## Related Topics

- [10. Consumer Rebalancing](./10-consumer-rebalancing.md)
- [11. Offset Management](./11-offset-management.md)
- [08. Consumer](./08-consumer.md)
