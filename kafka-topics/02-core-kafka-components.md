# 2. Core Kafka Components

---

## Theory

Kafka's architecture consists of several interconnected components that work together to provide distributed, fault-tolerant event streaming.

### Producer

The component that **publishes messages** to Kafka topics.

```
Producer Responsibilities:
- Serialize key and value to bytes
- Determine target partition (by key hash, round-robin, or custom logic)
- Batch messages for efficiency
- Handle retries on failure
- Compress messages (optional)
```

### Consumer

The component that **reads messages** from Kafka topics.

```
Consumer Responsibilities:
- Subscribe to one or more topics
- Poll for new messages
- Deserialize bytes back to objects
- Track position (offset) in each partition
- Commit offsets (auto or manual)
```

### Topic

A **named category/feed** to which records are published. Think of it as a logical channel.

```
Topic Properties:
- Name (unique within cluster)
- Number of partitions
- Replication factor
- Retention policy
- Cleanup policy (delete or compact)
```

### Partition

An **ordered, immutable sequence** of records within a topic. The unit of parallelism.

```
Topic "orders" with 3 partitions:

Partition 0: [msg0] [msg1] [msg2] [msg3] [msg4] → offset grows →
Partition 1: [msg0] [msg1] [msg2] →
Partition 2: [msg0] [msg1] [msg2] [msg3] →

- Messages within a partition are strictly ordered
- Messages across partitions have NO ordering guarantee
- Each partition is an independent log
```

### Offset

A **sequential ID** (64-bit integer) assigned to each message within a partition. Uniquely identifies a message within its partition.

```
Partition 0:
  Offset: 0    1    2    3    4    5    6    7
  Value:  A    B    C    D    E    F    G    H
                         ↑              ↑
                   committed        log-end
                    offset           offset

- Current Offset: Next message consumer will read
- Committed Offset: Last successfully processed position
- Log End Offset: Latest message written by producer
- Consumer Lag: Log End Offset - Committed Offset
```

### Broker

A **Kafka server** that stores data and serves client requests.

```
Broker Responsibilities:
- Receive messages from producers
- Store messages on disk (append to log)
- Serve messages to consumers
- Replicate data to followers
- Participate in leader election
- Report health to controller
```

### Cluster

A **group of brokers** working together. Provides fault tolerance and scalability.

```
Cluster (3 brokers):
┌──────────────────────────────────────────────────────┐
│  Broker 1 (id=1)   Broker 2 (id=2)   Broker 3 (id=3) │
│  - Topic-A P0 (L)  - Topic-A P1 (L)  - Topic-A P2 (L) │
│  - Topic-A P1 (F)  - Topic-A P2 (F)  - Topic-A P0 (F) │
│  - Topic-B P0 (L)  - Topic-A P0 (F)  - Topic-B P1 (L) │
│                                                          │
│  L = Leader, F = Follower                               │
└──────────────────────────────────────────────────────┘
```

### Consumer Group

A **set of consumers** that cooperatively consume from topics. Each partition is assigned to exactly ONE consumer within a group.

```
Consumer Group "order-processors" consuming Topic with 4 partitions:

Scenario 1: 2 consumers
  Consumer-1 → P0, P1
  Consumer-2 → P2, P3

Scenario 2: 4 consumers (ideal)
  Consumer-1 → P0
  Consumer-2 → P1
  Consumer-3 → P2
  Consumer-4 → P3

Scenario 3: 5 consumers (one idle!)
  Consumer-1 → P0
  Consumer-2 → P1
  Consumer-3 → P2
  Consumer-4 → P3
  Consumer-5 → (idle, no partition assigned)

Rule: max useful consumers = number of partitions
```

### Leader and Follower

Each partition has one **Leader** (handles all reads/writes) and zero or more **Followers** (replicate from leader).

```
Topic "orders", Partition 0, Replication Factor = 3:

  Broker 1: Partition 0 (LEADER)    ← Producers write here
  Broker 2: Partition 0 (FOLLOWER)  ← Replicates from leader
  Broker 3: Partition 0 (FOLLOWER)  ← Replicates from leader

- All produce/consume goes through Leader
- Followers are for fault tolerance
- If Leader dies → one Follower promoted to Leader
```

### Replication

Copies of partitions across multiple brokers for **fault tolerance**.

```
Replication Factor (RF) = 3 means:
  - 1 Leader + 2 Followers = 3 copies
  - Can survive 2 broker failures
  - Trade-off: more replication = more disk/network but better durability

min.insync.replicas = 2 means:
  - At least 2 replicas (including leader) must acknowledge writes
  - Combined with acks=all → guaranteed data durability
```

### Controller

A **special broker** responsible for cluster-wide administrative operations.

```
Controller Responsibilities:
- Partition leader election
- Detecting broker failures
- Reassigning partitions on broker failure
- Maintaining cluster metadata
- Only ONE controller per cluster at a time
- If controller dies → another broker elected as controller
```

---

## Diagram

### Component Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                        KAFKA CLUSTER                              │
│                                                                   │
│  ┌─────────────────────────────────────────────────────┐        │
│  │            Controller (Broker 1)                      │        │
│  │  - Leader election                                    │        │
│  │  - Metadata management                                │        │
│  │  - Failure detection                                  │        │
│  └─────────────────────────────────────────────────────┘        │
│                                                                   │
│  Topic: "orders" (RF=3)                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │  Broker 1   │  │  Broker 2   │  │  Broker 3   │             │
│  │             │  │             │  │             │             │
│  │ P0 (Leader) │  │ P0 (Follow) │  │ P0 (Follow) │             │
│  │ P1 (Follow) │  │ P1 (Leader) │  │ P1 (Follow) │             │
│  │ P2 (Follow) │  │ P2 (Follow) │  │ P2 (Leader) │             │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │
│         │                 │                 │                     │
│         └─── Replicate ───┴─── Replicate ───┘                    │
└──────────────────────────────┬───────────────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
   ┌─────┴─────┐        ┌─────┴─────┐        ┌─────┴─────┐
   │ Producer   │        │Consumer   │        │Consumer   │
   │            │        │Group A    │        │Group B    │
   │ send(key,  │        │           │        │           │
   │  value)    │        │ C1 → P0   │        │ C1 → P0,P1│
   └───────────┘        │ C2 → P1   │        │ C2 → P2   │
                         │ C3 → P2   │        │           │
                         └───────────┘        └───────────┘
```

### Message Flow Through Components

```
Producer                    Cluster                         Consumer
────────                    ───────                         ────────
1. Create record     →  2. Route to partition leader  →  5. Poll from partition
   (key, value,         3. Append to log                  6. Deserialize
    topic, headers)     4. Replicate to followers         7. Process
                           Return ACK                      8. Commit offset
```

---

## Interview Questions

### Q1: What is the relationship between topics, partitions, and brokers?

**A:** A **topic** is a logical category split into multiple **partitions** for parallelism. Each partition is a physical log hosted on a **broker**. Partitions of the same topic are distributed across different brokers for load balancing. Each partition has a leader (on one broker) and followers (on other brokers) for fault tolerance.

### Q2: What happens when you have more consumers than partitions in a consumer group?

**A:** Extra consumers sit idle. Kafka assigns each partition to exactly one consumer within a group. With 4 partitions and 6 consumers, only 4 consumers are active — 2 are idle standby (they'll take over if an active consumer fails). Maximum useful parallelism = number of partitions.

### Q3: How does the Controller broker differ from regular brokers?

**A:** The Controller is an elected broker with additional responsibilities: leader election for partitions, detecting broker failures via heartbeats, managing metadata (topic/partition assignments), and coordinating cluster-wide changes. Only one broker is Controller at any time. If it dies, another broker is elected. All brokers still handle normal produce/consume traffic.

### Q4: What is the difference between the committed offset and current offset?

**A:**
- **Current offset:** The position of the next message the consumer will read on the next `poll()`.
- **Committed offset:** The last offset the consumer has confirmed as successfully processed (stored in `__consumer_offsets` topic).
- On consumer restart, it resumes from the **committed offset**, not current offset.
- If a consumer reads offset 10 but only committed 7, and crashes — it will re-read 8, 9, 10 on restart.

### Q5: How does Kafka achieve fault tolerance?

**A:** Through **replication**. Each partition is copied across multiple brokers (replication factor). One copy is the Leader (serves reads/writes), others are Followers (replicate from leader). If the Leader broker fails, the Controller promotes a Follower to Leader. Producers and consumers automatically connect to the new leader. Combined with `acks=all` and `min.insync.replicas`, ensures no data loss even during failures.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Confusing topic with partition | Topic is logical, partition is physical unit | Remember: topic = category, partition = ordered log within it |
| Assuming global ordering | Kafka only orders within a partition | Use same key for messages that need ordering |
| Creating too few partitions | Limits consumer parallelism | Plan partitions based on peak throughput needs |
| Not understanding consumer groups | Multiple groups each get all messages | Use same group.id for load balancing, different for broadcast |
| Ignoring replication | Single copy → data loss on broker failure | Always use RF ≥ 3 in production |

---

## Best Practices

1. **Partition count ≥ expected max consumers** — can't scale beyond partition count
2. **Replication factor = 3** for production (survive 2 broker failures)
3. **Use consumer groups** for scaling consumption, not multiple independent consumers
4. **Understand leader distribution** — balance leaders across brokers for even load
5. **Monitor consumer lag** — growing lag means consumers can't keep up
6. **Use meaningful keys** — determines partition assignment and ordering

---

## Related Topics

- [03. Kafka Architecture](./03-kafka-architecture.md)
- [05. Partitions](./05-partitions.md)
- [09. Consumer Groups](./09-consumer-groups.md)
- [13. Replication](./13-replication.md)
