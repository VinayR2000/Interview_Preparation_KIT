# 10. Consumer Rebalancing ⭐⭐

---

## Theory

**Rebalancing** is the process of redistributing partitions among consumers in a group when membership changes. It's critical for scaling and fault tolerance but introduces temporary processing pauses.

### What is Rebalancing?

```
Before rebalance:
  C1: P0, P1, P2    C2: P3, P4, P5

Event: C3 joins group

After rebalance:
  C1: P0, P1    C2: P2, P3    C3: P4, P5
```

### Why Rebalancing Happens

| Trigger | Scenario |
|---------|----------|
| Consumer joins | New instance deployed (scale out) |
| Consumer leaves | Graceful shutdown (scale in) |
| Consumer crashes | JVM crash, network partition, OOM |
| Poll timeout | max.poll.interval.ms exceeded |
| Topic changes | Partitions added to subscribed topic |
| Subscription change | Pattern subscription matches new topic |

### Rebalance Protocol

```
JoinGroup/SyncGroup protocol:

1. Consumer sends JoinGroup request to coordinator
2. Coordinator waits for all consumers (or timeout)
3. Coordinator selects one consumer as "group leader"
4. Group leader receives all member subscriptions
5. Leader computes partition assignment
6. Leader sends assignment via SyncGroup
7. Coordinator distributes assignments to all members
8. Consumers start fetching from assigned partitions
```

### Eager Rebalancing (Stop-the-World)

The traditional approach — ALL consumers stop and release all partitions.

```
Timeline (Eager):

C1: ████████████ STOP ─── wait ─── RESUME ████████████
C2: ████████████ STOP ─── wait ─── RESUME ████████████
C3:             (joining)           ─────── START █████

                    ↑
            All processing paused!
            (no messages consumed during rebalance)

Problems:
- ALL consumers stop (even those keeping same partitions)
- Processing gap = rebalance duration (seconds to minutes)
- State stores must be rebuilt (Kafka Streams)
- Duplicate processing of uncommitted messages
```

### Cooperative Rebalancing (Incremental)

Only affected partitions are revoked and reassigned. Other partitions continue processing.

```
Timeline (Cooperative):

C1: █████████████████████████████████████████████████████
    P0, P1, P2   →   P0, P1 (P2 revoked)
                              ↑ only P2 paused briefly

C2: █████████████████████████████████████████████████████
    P3, P4, P5   →   P3, P4, P5 (no change, no pause!)

C3: ──────────────────────── START ██████████████████████
    (joining)                 P2, assigned

Process:
1. First rebalance: coordinator identifies which partitions need to move
2. Consumers that must give up partitions: revoke only those
3. Second rebalance: revoked partitions assigned to new consumers
4. Two-phase: slightly longer total, but much less disruptive
```

### Assignment Strategies

#### Range Assignment

```java
partition.assignment.strategy = RangeAssignor (default before 3.0)

Assigns ranges of partitions per topic:
  Topic A (6 partitions), Topic B (6 partitions), 3 consumers

  Topic A: C1→P0,P1  C2→P2,P3  C3→P4,P5
  Topic B: C1→P0,P1  C2→P2,P3  C3→P4,P5

Problem with uneven division:
  Topic C (7 partitions), 3 consumers
  C1→P0,P1,P2  C2→P3,P4  C3→P5,P6
  C1 gets extra partition for EVERY topic → overloaded
```

#### Round Robin

```java
partition.assignment.strategy = RoundRobinAssignor

Distributes partitions one by one across consumers:
  Topic A (6 partitions), Topic B (6 partitions), 3 consumers

  All partitions sorted: A-P0, A-P1, A-P2, A-P3, A-P4, A-P5, B-P0...
  C1: A-P0, A-P3, B-P0, B-P3
  C2: A-P1, A-P4, B-P1, B-P4
  C3: A-P2, A-P5, B-P2, B-P5

Better balance across topics than Range
```

#### Sticky Assignment

```java
partition.assignment.strategy = StickyAssignor

Goals:
1. Balance partitions evenly
2. Minimize partition movement during rebalance

Before rebalance:
  C1: P0, P1    C2: P2, P3    C3: P4, P5

C3 leaves:
  Range would: reassign everything
  Sticky: C1: P0, P1, P4    C2: P2, P3, P5
         (only P4, P5 moved — C1, C2 keep existing partitions)

Benefit: Less state rebuilding, less duplicate processing
```

#### Cooperative Sticky (Recommended)

```java
partition.assignment.strategy = CooperativeStickyAssignor (default since Kafka 3.0)

Combines:
- Sticky assignment (minimize movement)
- Cooperative protocol (no stop-the-world)

Best of both worlds:
- Only affected partitions pause
- Existing assignments preserved as much as possible
- Two-phase rebalance (revoke then assign)
```

---

## Diagram

### Eager vs Cooperative Rebalance

```
EAGER REBALANCE (Stop-the-World):
═══════════════════════════════════════════════════════════════════

Time ────────────────────────────────────────────────────────────►

C1:  [P0,P1,P2] ──STOP──┐         ┌──RESUME──[P0,P1]────►
                          │   GAP   │
C2:  [P3,P4,P5] ──STOP──┘         └──RESUME──[P3,P4]────►
                          │         │
C3:  (not yet)  ─────────┘         └──START───[P2,P5]────►

                    ◄── ALL PAUSED ──►
                    (10s - 60s+ gap)


COOPERATIVE REBALANCE (Incremental):
═══════════════════════════════════════════════════════════════════

Time ────────────────────────────────────────────────────────────►

C1:  [P0,P1,P2]─────[P0,P1]──── revoke P2 only ─[P0,P1]────►
                        │ P2 paused briefly │
C2:  [P3,P4,P5]─────[P3,P4]──── revoke P5 only ─[P3,P4]────►
                        │ P5 paused briefly │
C3:  (not yet) ─────────────── assign P2,P5 ─────[P2,P5]────►

     C1 keeps P0,P1 processing throughout! ✓
     C2 keeps P3,P4 processing throughout! ✓
     Only P2,P5 have brief pause ✓
```

### Rebalance Protocol Flow

```
Consumer 1          Coordinator           Consumer 2         Consumer 3 (new)
    │                    │                     │                    │
    │                    │                     │       JoinGroup    │
    │                    │◄────────────────────┼────────────────────┤
    │                    │                     │                    │
    │   JoinGroup(gen+1) │  JoinGroup(gen+1)   │                    │
    │◄───────────────────│────────────────────►│                    │
    │                    │                     │                    │
    │  (C1 elected leader)                     │                    │
    │  Compute assignment │                     │                    │
    │                    │                     │                    │
    │   SyncGroup        │                     │                    │
    │  (with assignment) │                     │                    │
    ├───────────────────►│                     │                    │
    │                    │   SyncGroup(result)  │   SyncGroup(result)│
    │  Assignment result │────────────────────►│───────────────────►│
    │◄───────────────────│                     │                    │
    │                    │                     │                    │
    │  Resume consuming  │  Resume consuming   │  Start consuming   │
    ▼                    ▼                     ▼                    ▼
```

---

## Code

### ConsumerRebalanceListener

```java
@Component
@Slf4j
public class OrderConsumer {

    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    @Bean
    public ConsumerRebalanceListener rebalanceListener(KafkaConsumer<?, ?> consumer) {
        return new ConsumerRebalanceListener() {
            
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // Called BEFORE rebalance — commit current progress
                log.info("Partitions revoked: {}", partitions);
                consumer.commitSync(currentOffsets);
                currentOffsets.clear();
                
                // Close any partition-specific resources
                partitions.forEach(tp -> closePartitionState(tp));
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                // Called AFTER rebalance — initialize for new partitions
                log.info("Partitions assigned: {}", partitions);
                partitions.forEach(tp -> initializePartitionState(tp));
            }
            
            // Cooperative rebalancing adds this method:
            @Override
            public void onPartitionsLost(Collection<TopicPartition> partitions) {
                // Called when partitions are lost without graceful revocation
                // (e.g., consumer fell out of group)
                log.warn("Partitions LOST (not gracefully revoked): {}", partitions);
                // State may be inconsistent — handle carefully
            }
        };
    }
}
```

### Configuring Cooperative Rebalancing

```java
// Spring Boot application.yml
spring:
  kafka:
    consumer:
      properties:
        partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor

// Or programmatically
props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    CooperativeStickyAssignor.class.getName());
```

---

## Interview Questions

### Q1: What is the difference between eager and cooperative rebalancing?

**A:**
- **Eager:** ALL consumers revoke ALL partitions, full stop. Then reassignment happens. Simple but causes a processing gap where no messages are consumed.
- **Cooperative:** Only partitions that need to move are revoked. Other partitions continue processing. Uses two-phase protocol (revoke, then assign). More complex but significantly reduces downtime.
- **Impact:** For a group with 10 consumers where 1 joins — eager stops all 10, cooperative only pauses 1-2 partition moves.

### Q2: How does sticky assignment help compared to range/round-robin?

**A:** Sticky assignment minimizes partition movement during rebalance. If C3 dies and you have C1→P0,P1 and C2→P2,P3:
- **Range/RoundRobin:** May reassign all partitions from scratch
- **Sticky:** Keeps C1→P0,P1 and C2→P2,P3, only adds P4,P5 (which C3 had) to C1 and C2

This matters because:
- Less duplicate processing (uncommitted messages on moved partitions)
- Less state store rebuilding (Kafka Streams)
- Faster recovery

### Q3: What happens to uncommitted offsets during a rebalance?

**A:** Uncommitted offsets are lost. When a partition is revoked from Consumer A and assigned to Consumer B:
- Consumer B starts from the last **committed** offset
- Any messages Consumer A had processed but not committed will be **reprocessed**
- Solution: Commit offsets in `onPartitionsRevoked()` callback before rebalance completes
- This is why `ConsumerRebalanceListener` exists

### Q4: How to minimize the impact of rebalancing?

**A:**
1. **Use CooperativeStickyAssignor** — only moves necessary partitions
2. **Commit offsets before revocation** — implement `onPartitionsRevoked`
3. **Use static group membership** — `group.instance.id` prevents rebalance on transient disconnects
4. **Tune timeouts** — `session.timeout.ms` high enough to survive GC pauses
5. **Avoid frequent scaling** — deploy in stable groups rather than constant up/down
6. **Monitor rebalance frequency** — alerts on excessive rebalancing

### Q5: What is static group membership?

**A:** Normally, when a consumer restarts, it's treated as a new member (triggers rebalance). With static membership:
```properties
group.instance.id=consumer-1  // unique per consumer instance
```
- Consumer identified by instance ID, not member ID
- If consumer disconnects and reconnects within `session.timeout.ms`, it gets the same partitions back without rebalance
- Useful for rolling deployments (restart consumers one by one without rebalancing)

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using eager rebalancing in production | Long processing pauses | Switch to CooperativeStickyAssignor |
| Not implementing RebalanceListener | Offset loss during rebalance | Commit offsets in onPartitionsRevoked |
| Setting session.timeout too low | False positives trigger unnecessary rebalances | Use 45s+ (default since Kafka 3.0) |
| Frequent deployments without static membership | Constant rebalancing | Use group.instance.id for stable assignments |
| Not monitoring rebalance events | Hidden performance issues | Log and alert on rebalance frequency |

---

## Best Practices

1. **Use CooperativeStickyAssignor** (default since Kafka 3.0)
2. **Implement ConsumerRebalanceListener** — commit on revocation
3. **Use static group membership** for containerized/Kubernetes deployments
4. **Monitor rebalance frequency and duration** in production
5. **Set session.timeout.ms = 45s** and heartbeat.interval.ms = 3s
6. **Test rebalance scenarios** — simulate consumer failures during load testing

---

## Related Topics

- [09. Consumer Groups](./09-consumer-groups.md)
- [11. Offset Management](./11-offset-management.md)
- [30. Kafka Cluster Management](./30-kafka-cluster-management.md)
