# 17. Kafka Performance ⭐⭐

---

## Theory

Kafka achieves millions of messages per second through batching, compression, sequential I/O, zero-copy, and parallelism. Understanding performance tuning is critical for production deployments.

### Throughput vs Latency

```
Throughput = messages/sec or MB/sec (how much data flows)
Latency = time from produce to consume (how fast one message travels)

These are often inversely related:
- Higher batching → better throughput, higher latency
- Lower batching → lower latency, lower throughput
```

### Key Performance Levers

| Lever | Affects | Trade-off |
|-------|---------|-----------|
| Batching (batch.size + linger.ms) | Throughput ↑, Latency ↑ | Bigger batches = more throughput, more delay |
| Compression | Throughput ↑, CPU ↑ | Less network, more CPU |
| Partitioning | Throughput ↑ (parallelism) | More resources, complexity |
| Producer parallelism | Throughput ↑ | More connections, memory |
| Consumer parallelism | Throughput ↑ | Limited by partition count |
| Fetch size | Consumer throughput ↑ | Memory usage ↑ |
| Network threads | Broker throughput ↑ | CPU ↑ |
| I/O threads | Broker throughput ↑ | CPU ↑ |

### Batching

```
Producer batching:
  batch.size = 32768 (32KB)     — max bytes per batch
  linger.ms = 20                — wait time to fill batch

  Small batch + low linger = low latency, many requests
  Large batch + high linger = high throughput, fewer requests

Consumer batching:
  fetch.min.bytes = 1           — min data to return (wait for more)
  fetch.max.wait.ms = 500       — max wait time if min not reached
  max.poll.records = 500        — records per poll
```

### Compression

```
| Algorithm | Ratio | Speed    | CPU    | Best For |
|-----------|-------|----------|--------|----------|
| none      | 1x    | Fastest  | None   | Low CPU budget |
| snappy    | ~2x   | Fast     | Low    | General purpose |
| lz4       | ~2x   | Fastest  | Low    | Low latency |
| gzip      | ~3x   | Slow     | High   | Bandwidth constrained |
| zstd      | ~3x   | Fast     | Medium | Best ratio/speed |

Compression applied per batch → bigger batches = better ratio
Network savings often far exceed CPU cost
Broker stores compressed (no decompression on broker)
```

### Partitioning for Performance

```
More partitions = more parallelism:
  - Multiple producers can write to different partitions concurrently
  - Multiple consumers can read from different partitions concurrently
  - Each partition has its own segment files (parallel disk I/O)

Diminishing returns:
  - Each partition = file handles, memory, replication overhead
  - Leader election takes longer with many partitions
  - Producer buffer memory per partition
  - Recommended max: ~4000 partitions per broker
```

### Producer Performance Tuning

```
High throughput config:
  batch.size = 65536 (64KB)
  linger.ms = 50
  compression.type = lz4
  buffer.memory = 67108864 (64MB)
  max.in.flight.requests = 5
  acks = 1 (trade durability for speed)

Low latency config:
  batch.size = 16384 (16KB)
  linger.ms = 0
  compression.type = none (or lz4)
  acks = 1
  max.in.flight.requests = 5
```

### Consumer Performance Tuning

```
High throughput config:
  fetch.min.bytes = 100000 (100KB)
  fetch.max.wait.ms = 500
  max.poll.records = 1000
  max.partition.fetch.bytes = 1048576 (1MB)

Processing optimization:
  - Batch DB operations (collect records, bulk insert)
  - Use batch listener in Spring Kafka
  - Increase concurrency (ConcurrentKafkaListenerContainerFactory)
  - Async processing with manual offset commit
```

### Broker Performance Tuning

```
Network threads: num.network.threads = 3 (default)
  - Handle network I/O (receive requests, send responses)
  - Increase for high connection count / high request rate
  - Typically: 4-8 for busy brokers

I/O threads: num.io.threads = 8 (default)
  - Handle disk I/O (read/write log segments)
  - Increase for many partitions or heavy traffic
  - Typically: 8-16 depending on disk count

Socket buffers:
  socket.send.buffer.bytes = 102400
  socket.receive.buffer.bytes = 102400
  - Increase for high-latency network (WAN replication)
```

---

## Diagram

### Performance Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRODUCER PERFORMANCE                           │
│                                                                   │
│  [Serialize] → [Partition] → [Accumulate in Batch] → [Compress] │
│                                    ↑ batch.size                   │
│                                    ↑ linger.ms                    │
│                                                                   │
│  → [Send to Broker] ← max.in.flight.requests (pipeline)         │
└───────────────────────────────────────┬─────────────────────────┘
                                        │
┌───────────────────────────────────────┼─────────────────────────┐
│                    BROKER PERFORMANCE  │                          │
│                                       ▼                          │
│  [Network Threads] → [Request Queue] → [I/O Threads]           │
│   num.network.threads     ↓              num.io.threads          │
│                     [Page Cache] ←→ [Disk]                       │
│                     (OS RAM)        (Sequential I/O)             │
│                           ↓                                      │
│                     [Zero-Copy] → [Response Queue] → [Network]  │
└───────────────────────────────────────┬─────────────────────────┘
                                        │
┌───────────────────────────────────────┼─────────────────────────┐
│                    CONSUMER PERFORMANCE│                          │
│                                       ▼                          │
│  [Fetch Request] ← fetch.min.bytes + fetch.max.wait.ms          │
│       ↓                                                          │
│  [Receive Batch] → [Decompress] → [Deserialize] → [Process]    │
│   max.poll.records                                               │
│       ↓                                                          │
│  [Commit Offset] (manual for reliability)                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: How would you tune Kafka for maximum throughput?

**A:**
- **Producer:** Large batch.size (64-128KB), linger.ms (20-100ms), compression=lz4/zstd, acks=1
- **Broker:** Increase num.network.threads and num.io.threads, dedicate disks
- **Consumer:** Large fetch.min.bytes, batch listener, multiple consumers per group
- **Topic:** More partitions (match expected consumer count)
- **Infrastructure:** Fast disks (NVMe), high bandwidth network, enough RAM for page cache

### Q2: How would you tune Kafka for lowest latency?

**A:**
- **Producer:** linger.ms=0, small batch.size, acks=1, no compression
- **Consumer:** fetch.min.bytes=1, fetch.max.wait.ms=0-100
- **Broker:** Ensure data is in page cache (enough RAM)
- **Topic:** Fewer partitions (less overhead per message)
- **Expect:** Sub-5ms end-to-end latency achievable with proper tuning
- **Trade-off:** Lower throughput, more network overhead

### Q3: What are the common bottlenecks in a Kafka cluster?

**A:**
1. **Disk I/O:** Under-provisioned disks, too many partitions per disk
2. **Network:** Replication traffic + client traffic saturating NIC
3. **CPU:** Compression/decompression, SSL/TLS overhead
4. **Memory:** Insufficient page cache → disk reads for consumers
5. **Consumer processing:** Slow consumers → growing lag → needs more parallelism
6. **Producer buffer:** Buffer full → blocking send calls

### Q4: How does compression improve performance?

**A:** Compression reduces data size at the batch level:
- **Less network I/O:** 3x compression = 1/3 the bytes transferred
- **Less disk I/O:** Compressed batches stored as-is on broker
- **Better batching:** More records fit in batch.size
- **CPU cost:** Typically negligible vs. network savings (lz4/snappy)
- **Key insight:** Broker never decompresses — CPU cost only on producer and consumer
- JSON data often compresses 5-10x (very repetitive structure)

---

## Best Practices

1. **Benchmark your workload** — don't guess, measure with `kafka-producer-perf-test.sh`
2. **Use compression** — lz4 for speed, zstd for ratio, almost always a net win
3. **Monitor end-to-end** — track produce-to-consume latency percentiles
4. **Right-size partitions** — more than you need today, fewer than wasteful
5. **Tune incrementally** — change one knob at a time, measure impact
6. **Page cache is king** — leave 60%+ of RAM for OS cache
7. **Use SSD for latency-sensitive workloads** — HDD fine for throughput with page cache

---

## Related Topics

- [06. Producer](./06-producer.md)
- [08. Consumer](./08-consumer.md)
- [15. Kafka Storage Internals](./15-kafka-storage-internals.md)
- [36. Production-Level Kafka](./36-production-level-kafka.md)
