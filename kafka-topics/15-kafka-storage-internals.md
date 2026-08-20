# 15. Kafka Storage Internals

---

## Theory

Kafka achieves its extraordinary throughput through careful storage design: append-only logs, sequential I/O, OS page cache leveraging, and zero-copy transfers.

### Append-Only Log

```
Every partition is an append-only log:
- New records always appended to the END (never inserted in middle)
- Records are IMMUTABLE once written
- Only deletion is through retention/compaction (whole segments)

Benefits:
- Sequential writes (fastest disk access pattern)
- No random I/O (no seek time)
- Simple concurrency (append doesn't conflict with reads)
- Easy replication (just copy the tail)
```

### Log Segment

A partition is split into segment files of configurable size.

```
Partition directory: /kafka-logs/orders-0/
├── 00000000000000000000.log         ← First segment (base offset = 0)
├── 00000000000000000000.index       ← Offset index for segment
├── 00000000000000000000.timeindex   ← Time index for segment
├── 00000000000000524288.log         ← Second segment (base offset = 524288)
├── 00000000000000524288.index
├── 00000000000000524288.timeindex
├── 00000000000001048576.log         ← Active segment (being written to)
├── 00000000000001048576.index
└── 00000000000001048576.timeindex

Segment file name = base offset (first offset in that segment)
Only the ACTIVE (last) segment is open for writes
Closed segments are immutable → eligible for retention/deletion
```

### Log Index (Offset Index)

Sparse index mapping offsets to file positions within the segment.

```
Offset Index (.index file):
  Offset → Physical Position in .log file

  offset 0     → position 0
  offset 100   → position 16384    (not every offset indexed)
  offset 200   → position 32768    (indexed every ~4KB of data)
  offset 300   → position 49152
  ...

To find offset 150:
  1. Binary search index: closest ≤ 150 is offset 100 at position 16384
  2. Sequential scan .log from position 16384 until offset 150 found

Sparse index trade-off:
  - Small index file (not one entry per record)
  - Slightly slower lookup (scan after binary search)
  - Configurable: log.index.interval.bytes (default 4096)
```

### Time Index

Maps timestamps to offsets for time-based lookups.

```
Time Index (.timeindex file):
  Timestamp → Offset

  1705312800000 → offset 0
  1705312900000 → offset 150
  1705313000000 → offset 300
  ...

Used for:
  - consumer.offsetsForTimes() — "give me offset at this timestamp"
  - Time-based retention (find segments older than retention.ms)
  - Time-based consumption (replay from specific time)
```

### Sequential Writes

```
Random Write (traditional DB):
  Disk head seeks to position → write → seek → write → seek → write
  Throughput: ~100-200 MB/s (SSD), ~1-10 MB/s (HDD)

Sequential Write (Kafka):
  Write → write → write → write (continuous, no seeking)
  Throughput: ~600+ MB/s (SSD), ~100+ MB/s (HDD)

Kafka's append-only design ensures ALL writes are sequential!
Even HDDs achieve excellent throughput for sequential writes.
```

### Page Cache

Kafka relies heavily on the OS page cache (file system cache in RAM).

```
Write path:
  Producer → Kafka (JVM) → OS page cache → disk (async)
  
  Kafka writes to filesystem → OS buffers in page cache
  Background: OS flushes dirty pages to disk
  
Read path (recent data):
  Consumer → Kafka → OS page cache → consumer (no disk read!)
  
  Most consumers read recent data → already in page cache
  Effectively: consumer reads from RAM

Read path (old data):
  Consumer → Kafka → OS (cache miss) → disk read → consumer
  
  Only if consuming very old data (outside cache)

Why this works:
  - Kafka doesn't manage its own cache (avoids GC pressure)
  - OS page cache is efficient and well-tested
  - JVM heap stays small → predictable GC
  - Warm cache = consumer reads at memory speed
```

### Zero-Copy (sendfile)

```
Traditional data transfer (4 copies):
  Disk → OS buffer → Application buffer → Socket buffer → NIC
  (read)  (copy1)      (copy2)            (copy3)      (copy4)

Zero-Copy transfer (2 copies):
  Disk → OS buffer ──────────────────────→ NIC
  (read)  (sendfile syscall, bypasses application)

Kafka uses sendfile() for consumer fetches:
  - Data goes directly from page cache to network socket
  - No copy into JVM heap
  - Massive throughput improvement for consumer reads
  - Only works for unmodified data (no transformation)
```

### Disk Storage Layout

```
Broker disk layout:
/kafka-data/
├── meta.properties              ← Broker metadata
├── cleaner-offset-checkpoint    ← Compaction progress
├── log-start-offset-checkpoint  ← Earliest available offset per partition
├── recovery-point-offset-checkpoint ← Recovery information
├── replication-offset-checkpoint
│
├── orders-0/                    ← Partition directory
│   ├── 00000000000000000000.log
│   ├── 00000000000000000000.index
│   ├── 00000000000000000000.timeindex
│   ├── leader-epoch-checkpoint
│   └── partition.metadata
│
├── orders-1/
│   └── ...
│
└── __consumer_offsets-23/       ← Internal topic partition
    └── ...
```

### Segment Rolling

```
A new segment is created when ANY condition is met:

1. Size: log.segment.bytes = 1GB (default)
   Current segment reaches 1GB → close, start new

2. Time: log.roll.ms (or log.roll.hours = 168h/7 days)
   Segment has been active for 7 days → roll

3. Index full: offset.index or time.index reaches max size
   Rare but possible with very large records

Rolling process:
  1. Close current active segment (make immutable)
  2. Flush index files
  3. Create new segment with base offset = next offset
  4. New segment becomes active
  5. Closed segment now eligible for retention/compaction
```

---

## Diagram

### Storage Architecture

```
Producer writes:
═══════════════
Producer ──► Kafka Broker (JVM)
                   │
                   ▼
             ┌──────────────┐
             │ OS Page Cache │ ← Write lands here first
             │   (RAM)       │
             └──────┬───────┘
                    │ (async flush)
                    ▼
             ┌──────────────┐
             │    Disk       │
             │  (segments)   │
             └──────────────┘

Consumer reads (recent data = hot path):
═══════════════════════════════════════
Consumer ◄── Kafka Broker ◄── OS Page Cache (RAM)
                              ↑ data already here!
                              │ ZERO-COPY: sendfile()
                              │ (no copy to JVM heap)

Consumer reads (old data = cold path):
═══════════════════════════════════════
Consumer ◄── Kafka Broker ◄── OS ◄── Disk (segment file)
                                     (page fault, disk read)
```

### Segment File Structure

```
Segment .log file (binary):
┌─────────────────────────────────────────────────────────┐
│ Record Batch 0 (offset 0-14)                             │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Base Offset: 0 | Batch Length: 1024 | CRC | ...     │ │
│ │ Record 0: offset=0, key="k1", value="v1"           │ │
│ │ Record 1: offset=1, key="k2", value="v2"           │ │
│ │ ...                                                  │ │
│ │ Record 14: offset=14, key="k15", value="v15"        │ │
│ └─────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│ Record Batch 1 (offset 15-30)                            │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Base Offset: 15 | Batch Length: 2048 | CRC | ...    │ │
│ │ ...                                                  │ │
│ └─────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│ ... more batches ...                                     │
└─────────────────────────────────────────────────────────┘

Offset Index (.index):
┌──────────┬───────────────┐
│ Offset   │ File Position │
├──────────┼───────────────┤
│ 0        │ 0             │
│ 15       │ 1024          │
│ 31       │ 3072          │
│ ...      │ ...           │
└──────────┴───────────────┘
```

---

## Interview Questions

### Q1: How does Kafka achieve high throughput despite writing to disk?

**A:** Several design choices:
1. **Sequential I/O:** Append-only writes avoid disk seeks (even HDDs perform well)
2. **Page cache:** Writes go to OS page cache first (effectively RAM-speed), flushed async
3. **Zero-copy:** Consumer reads use `sendfile()` — data flows disk/cache → network without entering JVM
4. **Batching:** Multiple records written in one I/O operation
5. **Compression:** Less data to write/read
6. **No random access:** Never reads/writes in the middle of a file
- Result: Kafka can saturate network bandwidth before hitting disk limits

### Q2: Why does Kafka use the OS page cache instead of managing its own cache?

**A:**
- **No GC pressure:** Data in page cache doesn't contribute to JVM GC (critical for latency)
- **Survives restart:** Page cache persists even if Kafka JVM restarts
- **Efficient:** OS kernel has decades of cache optimization (LRU, readahead, etc.)
- **No double-buffering:** JVM cache + OS cache = wasteful; Kafka uses only OS cache
- **Automatic:** OS manages eviction, dirty page flushing, memory pressure handling
- Trade-off: Less control (can't pin specific data), but benefits far outweigh costs.

### Q3: Explain how zero-copy works in Kafka consumer reads.

**A:** Traditional data transfer: disk → kernel buffer → user space (JVM) → socket buffer → NIC (4 context switches, 2 CPU copies). Zero-copy (`sendfile` syscall): disk → kernel buffer → NIC (2 context switches, 0 CPU copies). Kafka calls `transferTo()` (Java's `FileChannel.transferTo`), which uses `sendfile` on Linux. Data goes directly from page cache to network socket without entering JVM heap. This is why consumer throughput is often limited by network, not disk.

### Q4: How does Kafka find a specific offset in a partition?

**A:** Two-step process:
1. **Find segment:** Binary search segment files by name (base offset in filename). Find segment where base_offset ≤ target_offset.
2. **Find position in segment:** Binary search the sparse `.index` file for closest entry ≤ target offset. Get file position.
3. **Sequential scan:** Read `.log` file from that position forward until target offset found.

The sparse index (entry every ~4KB) keeps index files small while allowing fast lookups. This is O(log n) for segment selection + O(log n) for index lookup + O(small constant) for scan.

### Q5: What happens to data when a broker crashes and restarts?

**A:**
1. **Recovery point:** Kafka tracks the last flushed offset per partition (`recovery-point-offset-checkpoint`)
2. **Log recovery:** On startup, Kafka reads the log from the recovery point to the end, rebuilding the index
3. **CRC validation:** Each record batch has a CRC — corrupted records detected and truncated
4. **Truncation:** Records beyond the high watermark (not fully replicated) are truncated to align with the current leader
5. **Resume:** Once recovered, broker resumes fetching from leader for its follower partitions

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Small JVM heap + large data | OS page cache squeezed | Give Kafka small heap (~6GB), leave rest for page cache |
| Using swap | Terrible performance when page cache is evicted to swap | Disable or minimize swap |
| SAN/NAS storage | Added latency, defeats sequential I/O | Use local disks (SSD or HDD) |
| Too many partitions | Each has segment files + index → too many file handles | Increase ulimit, consolidate small topics |
| Not aligning segment.bytes with retention | Full segments needed for deletion | Smaller segments = finer retention granularity |

---

## Best Practices

1. **Dedicate disks to Kafka** — don't share with other I/O-heavy applications
2. **Leave 60-70% of RAM for page cache** — small JVM heap (4-8GB)
3. **Use XFS or ext4** — best performing filesystems for Kafka
4. **Monitor disk I/O** — watch for high await times (indicates saturation)
5. **Segment size:** 512MB-1GB for most workloads
6. **Disable swap** or set `vm.swappiness=1`
7. **Use RAID 10 or JBOD** — Kafka handles replication itself

---

## Related Topics

- [03. Kafka Architecture](./03-kafka-architecture.md)
- [16. Retention & Log Compaction](./16-retention-log-compaction.md)
- [17. Kafka Performance](./17-kafka-performance.md)
