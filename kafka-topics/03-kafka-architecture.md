# 3. Kafka Architecture ⭐⭐⭐

---

## Theory

Kafka's architecture is built on the concept of a **distributed commit log** — an append-only, ordered, immutable sequence of records distributed across multiple machines.

### Kafka Cluster

A cluster consists of multiple brokers coordinated by a controller (ZooKeeper or KRaft). The cluster provides fault tolerance, horizontal scalability, and high availability.

```
Cluster Properties:
- Multiple brokers (typically 3+ for production)
- One active Controller
- Automatic failover
- Even partition distribution
- Leader load balancing
```

### Brokers

Each broker is an independent Kafka server identified by a unique integer ID.

```
Broker Responsibilities:
1. Store partition data on disk
2. Handle produce requests (append to log)
3. Handle fetch requests (serve to consumers)
4. Replicate data from leaders (when acting as follower)
5. Respond to metadata requests
6. Participate in cluster coordination
```

### Topics and Partitions

```
Topic "user-events" (3 partitions, RF=3):

Broker 1           Broker 2           Broker 3
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ P0 (Leader)  │  │ P0 (Follower)│  │ P0 (Follower)│
│ P1 (Follower)│  │ P1 (Leader)  │  │ P1 (Follower)│
│ P2 (Follower)│  │ P2 (Follower)│  │ P2 (Leader)  │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Replicas, Leaders, and Followers

Every partition has multiple **replicas** spread across brokers:
- **Leader Replica:** Handles ALL produce and consume requests
- **Follower Replica:** Passively replicates from leader, serves as backup

### ISR (In-Sync Replicas)

The set of replicas that are caught up with the leader within a configurable lag threshold.

```
ISR = {replicas that have replicated ALL messages up to the leader's high watermark}

Example:
  Leader (Broker 1):    offset 0..100 (latest)
  Follower (Broker 2):  offset 0..100  → IN ISR ✓
  Follower (Broker 3):  offset 0..95   → OUT OF ISR ✗ (lagging)

ISR = {Broker 1, Broker 2}

Conditions for removal from ISR:
  - replica.lag.time.max.ms exceeded (default 30s)
  - Follower hasn't fetched in time
```

### Controller

```
Controller (one per cluster):
├── Monitors broker liveness (via heartbeats)
├── Detects broker failures
├── Triggers leader election for orphaned partitions
├── Updates cluster metadata
├── Communicates metadata changes to all brokers
└── If controller dies → new controller elected from brokers
```

### Metadata

Metadata is information about the cluster that all brokers maintain:

```
Cluster Metadata:
├── List of brokers (id, host, port)
├── List of topics
├── For each topic:
│   ├── Number of partitions
│   ├── Replication factor
│   └── For each partition:
│       ├── Leader broker ID
│       ├── Replica list (all brokers holding a copy)
│       └── ISR list (in-sync replicas)
└── Controller ID
```

Producers and consumers fetch metadata to know which broker to connect to for each partition.

### Log Structure

```
Partition = Ordered log of Records

Directory structure on disk:
/kafka-logs/
  └── user-events-0/          ← Partition 0
      ├── 00000000000000000000.log      ← Segment file (messages)
      ├── 00000000000000000000.index    ← Offset index
      ├── 00000000000000000000.timeindex ← Time index
      ├── 00000000000000523776.log      ← Next segment
      ├── 00000000000000523776.index
      └── 00000000000000523776.timeindex
```

### Log Segment

A partition's log is divided into **segments** (files of configurable size).

```
Segment Properties:
- Default size: 1GB (log.segment.bytes)
- Default roll time: 7 days (log.roll.ms)
- Active segment: currently being written to
- Inactive segments: closed, eligible for cleanup

Partition 0:
[Segment 0: offset 0-523775] [Segment 1: offset 523776-1047551] [Active Segment]
   (closed, may be deleted)     (closed)                           (being written)
```

### Record and Record Batch

```
Record (single message):
├── Offset (int64)
├── Timestamp (int64)
├── Key (bytes, nullable)
├── Value (bytes)
├── Headers (array of key-value pairs)
└── Size

Record Batch (group of records):
├── Base Offset
├── Batch Length
├── Partition Leader Epoch
├── Magic (version)
├── CRC (checksum)
├── Attributes (compression, timestamp type, transaction)
├── Last Offset Delta
├── First Timestamp
├── Max Timestamp
├── Producer ID (for idempotency)
├── Producer Epoch
├── Base Sequence
└── Records[]
```

---

## Diagram

### Complete Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         KAFKA CLUSTER                                  │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │              Controller (elected broker)                       │    │
│  │  • Leader election  • Metadata management  • Failure detection│    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                        │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐           │
│  │  Broker 0   │     │  Broker 1   │     │  Broker 2   │           │
│  │             │     │             │     │             │           │
│  │ ┌─────────┐│     │ ┌─────────┐│     │ ┌─────────┐│           │
│  │ │T-A P0(L)││     │ │T-A P0(F)││     │ │T-A P0(F)││           │
│  │ │T-A P1(F)││     │ │T-A P1(L)││     │ │T-A P1(F)││           │
│  │ │T-A P2(F)││     │ │T-A P2(F)││     │ │T-A P2(L)││           │
│  │ └─────────┘│     │ └─────────┘│     │ └─────────┘│           │
│  │             │     │             │     │             │           │
│  │ Disk:       │     │ Disk:       │     │ Disk:       │           │
│  │ /logs/      │     │ /logs/      │     │ /logs/      │           │
│  │  segments   │     │  segments   │     │  segments   │           │
│  └─────────────┘     └─────────────┘     └─────────────┘           │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │              ZooKeeper / KRaft Quorum                          │    │
│  │  • Cluster membership  • Controller election  • Config store  │    │
│  └──────────────────────────────────────────────────────────────┘    │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
        ┌──────────┐      ┌──────────┐      ┌──────────┐
        │ Producer │      │ Consumer │      │ Admin    │
        │ API      │      │ API      │      │ Client   │
        └──────────┘      └──────────┘      └──────────┘
```

### Write Path (Producer → Broker)

```
Producer                         Leader Broker                    Followers
────────                         ─────────────                    ─────────
1. Serialize(key, value)
2. Partition selection
   (hash(key) % numPartitions)
3. Batch accumulation
4. Send batch ──────────────────► 5. Validate request
                                  6. Append to active segment
                                  7. Update offset index
                                  8. Update time index ─────────► 9. Fetch & replicate
                                                                  10. ACK to leader
                                  11. Update ISR/HW
12. Receive ACK ◄──────────────── (based on acks setting)
```

### Read Path (Broker → Consumer)

```
Consumer                         Leader Broker
────────                         ─────────────
1. Poll(timeout) ───────────────► 2. Check offset validity
                                  3. Read from page cache (or disk)
                                  4. Apply max.poll.records limit
5. Receive records ◄────────────── 5. Send FetchResponse
6. Deserialize
7. Process records
8. Commit offset ───────────────► 9. Store in __consumer_offsets topic
```

---

## Internal Working

### How a Record is Stored

```
1. Producer sends Record(key="user-1", value="login event")
2. Leader receives → assigns offset (say 42)
3. Appends to active log segment file:
   00000000000000000000.log ← binary record appended here

4. Updates offset index (sparse, every 4KB):
   offset 40 → position 16384
   offset 44 → position 20480  (not every offset indexed)

5. Updates time index:
   timestamp 1705312800000 → offset 40

6. Page cache holds recent writes in memory
   - Consumers reading recent data = reads from RAM
   - Consumers reading old data = reads from disk
```

### How Consumer Finds a Message by Offset

```
Consumer wants offset 42 from Partition 0:

1. Find correct segment file:
   - Segment files named by base offset
   - 00000000000000000000.log (base=0)
   - 00000000000000523776.log (base=523776)
   → offset 42 is in first segment

2. Binary search offset index for closest entry ≤ 42:
   → offset 40 at file position 16384

3. Sequential scan from position 16384:
   → Read record at offset 40, skip
   → Read record at offset 41, skip
   → Read record at offset 42, return!
```

---

## Interview Questions

### Q1: Explain Kafka's storage architecture.

**A:** Kafka stores data as an append-only commit log on disk. Each partition is a directory containing segment files (default 1GB each). Each segment has a `.log` file (records), `.index` file (offset→position mapping), and `.timeindex` file (timestamp→offset mapping). The active segment is being written to; closed segments are eligible for retention cleanup. Kafka relies on the OS page cache for fast reads of recent data — this is why it achieves high throughput despite writing to disk.

### Q2: What is the ISR and why is it important?

**A:** ISR (In-Sync Replicas) is the set of replicas fully caught up with the leader. It's critical because:
- Only ISR members are eligible for leader election (unless unclean election enabled)
- `acks=all` waits for ALL ISR members to acknowledge (not all replicas)
- `min.insync.replicas` requires minimum ISR size for writes to succeed
- Falling out of ISR (due to lag) means that replica won't be chosen as leader if current leader fails

### Q3: How does Kafka handle a broker failure?

**A:**
1. Controller detects broker failure (missed heartbeats)
2. For each partition where failed broker was Leader:
   - Controller selects new leader from ISR
   - Updates metadata
   - Notifies all brokers of new leadership
3. Producers/consumers get metadata update → redirect to new leader
4. If failed broker was Follower: ISR shrinks, no immediate action needed
5. When broker recovers: catches up from leader, rejoins ISR

### Q4: What is the high watermark?

**A:** The high watermark (HW) is the offset of the last message fully replicated to ALL ISR members. Consumers can only read up to the HW — ensures they never read a message that might be lost if leader fails before replication completes.

```
Leader:    [0][1][2][3][4][5][6][7]  ← Log End Offset = 8
ISR Follower 1: [0][1][2][3][4][5]  ← caught up to 5
ISR Follower 2: [0][1][2][3][4][5]  ← caught up to 5
                                 ↑
                            High Watermark = 6

Consumer can read: 0..5 (up to HW - 1)
Messages 6, 7: "uncommitted" — not yet safe to expose
```

### Q5: How does Kafka achieve high throughput?

**A:**
1. **Sequential I/O:** Append-only writes, sequential reads (disk-friendly)
2. **Page cache:** OS caches recent data in RAM — most consumer reads hit cache
3. **Zero-copy:** `sendfile()` system call — data goes directly from disk → network socket (bypasses user space)
4. **Batching:** Messages batched at producer and broker level
5. **Compression:** Entire batches compressed (better ratio than per-message)
6. **Partitioning:** Parallel writes/reads across multiple disk/network paths

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Confusing ISR with all replicas | acks=all only waits for ISR, not all replicas | Monitor ISR shrinkage closely |
| Not monitoring HW lag | Consumers reading stale data without knowing | Monitor and alert on HW vs LEO gap |
| Setting too large segments | Long recovery time after crash | Use reasonable segment size (512MB-1GB) |
| Not understanding controller role | Confusion during failure scenarios | One controller manages all leader elections |
| Assuming reads go to followers | By default, consumers read from leader only | Follower fetching added in Kafka 2.4+ (KIP-392) |

---

## Best Practices

1. **Spread leaders evenly** across brokers for balanced load
2. **Monitor ISR shrinkage** — indicates broker health issues
3. **Understand page cache behavior** — recent data fast, old data slow
4. **Size segments appropriately** — smaller = faster recovery, larger = fewer files
5. **Plan disk I/O** — use dedicated disks for Kafka logs
6. **Monitor controller** — single point of coordination

---

## Related Topics

- [05. Partitions](./05-partitions.md)
- [13. Replication](./13-replication.md)
- [15. Kafka Storage Internals](./15-kafka-storage-internals.md)
- [31. ZooKeeper & KRaft](./31-zookeeper-kraft.md)
