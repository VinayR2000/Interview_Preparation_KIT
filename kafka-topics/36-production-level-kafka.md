# 36. Production-Level Kafka ⭐⭐⭐

---

## Theory

Running Kafka in production requires careful planning across capacity, reliability, ordering, deduplication, monitoring, security, and disaster recovery.

### Capacity Planning

```
Key dimensions to plan:
1. Throughput: messages/sec × avg message size = MB/sec
2. Storage: throughput × retention period × replication factor
3. Network: ingress + egress + replication traffic
4. Partitions: throughput / per-partition throughput

Example calculation:
  10,000 msg/sec × 1KB avg = 10 MB/sec ingress
  Replication (RF=3): 10 × 3 = 30 MB/sec total write I/O
  Consumer groups (3): 10 × 3 = 30 MB/sec egress
  Retention (7 days): 10 MB/s × 86400 × 7 = ~6 TB raw
  With RF=3: 6 × 3 = 18 TB total storage
  
  Broker count: typically 3-9 brokers for medium workloads
  Rule: plan for 60-70% utilization (headroom for spikes)
```

### Partition Planning

```
Factors:
  - Target throughput / per-partition throughput (10-30 MB/s)
  - Maximum consumer parallelism desired
  - Message key cardinality (avoid hot partitions)
  - Future growth (can increase, can't decrease)

Guidelines:
  Small topic (<10 MB/s): 6 partitions
  Medium topic (10-100 MB/s): 12-30 partitions
  High-traffic topic (>100 MB/s): 50-100+ partitions
  
  Max partitions per broker: ~4000 (after that, leadership overhead grows)
  Max partitions per cluster: hundreds of thousands (KRaft) or ~200K (ZooKeeper)
```

### Replication Planning

```
Production standard:
  replication.factor = 3
  min.insync.replicas = 2
  acks = all
  unclean.leader.election.enable = false

This means:
  - 3 copies of every message
  - At least 2 must acknowledge writes
  - Survives 1 broker failure (for writes)
  - Survives 2 broker failures (for reads of committed data)
  - No data loss even during failures
```

### Consumer Scaling

```
Scaling strategies:
1. Add consumers (up to partition count): most common
2. Increase max.poll.records + batch processing
3. Async processing with manual offset management
4. Internal thread pool (process in parallel, careful with ordering)

Scaling limits:
  - Max consumers in group = partition count
  - Beyond that: need more partitions
  - More partitions = more brokers needed

Capacity formula:
  Required consumers = peak_throughput / per_consumer_throughput
  Required partitions ≥ required consumers
```

### Producer Scaling

```
Strategies:
1. Increase batch.size + linger.ms (better batching)
2. Use compression (lz4/zstd)
3. Multiple producer instances (across services)
4. More partitions (parallel write paths)

Single producer can typically handle:
  - 100K-1M+ messages/sec (depending on message size)
  - Kafka producers are rarely the bottleneck
  - Usually limited by serialization or business logic upstream
```

### Consumer Lag Management

```
What is healthy lag?
  - Lag = 0: caught up (ideal for real-time)
  - Lag < 1000: acceptable for most services
  - Lag growing: ACTION NEEDED

When lag grows:
  1. Check consumer processing time (is downstream slow?)
  2. Check consumer count vs partitions (scale consumers?)
  3. Check for rebalancing storms (too frequent rebalances?)
  4. Check for poison messages (one bad message blocking?)
  5. Temporarily increase max.poll.records for catch-up
```

### Backpressure

```
When consumers can't keep up with producers:

Options:
1. Scale consumers (add instances)
2. Increase consumer throughput (batch processing, optimize code)
3. Increase partitions (enables more consumers)
4. Throttle producers (rate limiting upstream)
5. Use separate topic for overflow (priority queue pattern)
6. Accept lag (if eventual consistency SLA allows)

Detection:
  - Monitor consumer lag (growing = backpressure)
  - Monitor consumer poll idle ratio (low = overloaded)
  - Monitor max.poll.interval violations (consumer kicked out)
```

### Ordering Guarantees

```
Within partition: GUARANTEED (total order)
Across partitions: NO guarantee

Strategies for ordering:
1. Use entity ID as key: all events for same entity → same partition → ordered
2. Single partition (extreme): global order but no parallelism
3. Sequence numbers in events: consumer reorders if needed
4. max.in.flight.requests=1 OR enable.idempotence=true: producer ordering

Common ordering requirements:
  - All events for an order: key=orderId ✓
  - All events for a user: key=userId ✓
  - Global ordering across all events: single partition (usually wrong approach)
```

### Duplicate Events

```
Sources of duplicates:
1. Producer retries (solved by idempotent producer)
2. Consumer reprocessing after crash (solved by idempotent consumer)
3. MirrorMaker replay (rare, handled by consumer dedup)

Prevention layers:
  Layer 1: enable.idempotence=true (producer-side, automatic)
  Layer 2: Event ID + deduplication at consumer (application-level)
  Layer 3: Idempotent operations (SET not ADD)
  Layer 4: Database unique constraints (safety net)
```

### Data Loss Prevention

```
Complete data loss prevention stack:

Producer:
  acks=all                          — all ISR must acknowledge
  enable.idempotence=true           — no duplicates from retries
  retries=MAX_INT                   — keep trying
  delivery.timeout.ms=120000        — 2 min total timeout

Topic:
  replication.factor=3              — 3 copies
  min.insync.replicas=2             — at least 2 must be in-sync
  unclean.leader.election.enable=false — no out-of-sync leader

Consumer:
  enable.auto.commit=false          — manual commit after processing
  Idempotent processing             — handle redelivery safely

Broker:
  log.flush.interval.messages=Long.MAX  — rely on replication, not fsync
  unclean.leader.election.enable=false   — prefer unavailability over data loss
```

### Message Reprocessing

```
When you need to reprocess messages:

1. Reset consumer offset:
   kafka-consumer-groups.sh --reset-offsets --to-earliest --execute
   (Requires stopping consumers first)

2. Seek programmatically:
   consumer.seek(partition, targetOffset);
   consumer.seekToBeginning(partitions);

3. New consumer group:
   Deploy with new group.id → reads from auto.offset.reset position

4. Time-based reset:
   --reset-offsets --to-datetime 2024-01-15T00:00:00.000

Prerequisites:
  - Consumer must be IDEMPOTENT (will see duplicate messages!)
  - Data must still be within retention period
  - Consider downstream impact (duplicate notifications, etc.)
```

### Schema Evolution

```
As events change over time:
1. Use Schema Registry (Avro/Protobuf) with compatibility checks
2. Add fields with defaults (backward compatible)
3. Never rename or remove required fields
4. Version your events if breaking changes needed
5. Support reading old and new formats simultaneously

Deployment order (BACKWARD compatibility):
  1. Deploy new consumers (can read old AND new format)
  2. Deploy new producers (start writing new format)
  3. Old messages still readable by new consumers ✓
```

### Security Checklist

```
□ SSL/TLS for all communication (SASL_SSL)
□ Authentication (SASL/SCRAM or Kerberos)
□ ACLs (least privilege per service)
□ Encrypt data at rest (disk encryption)
□ Network isolation (private subnet, security groups)
□ Credential rotation policy
□ Audit logging enabled
□ Schema Registry authentication
```

### Monitoring Checklist

```
□ Consumer lag (per group, per partition) — alert on growth
□ Under-replicated partitions — alert on any > 0
□ Offline partitions — CRITICAL alert
□ Broker disk usage — alert at 70%
□ Request latency (p95, p99) — alert on degradation
□ ISR shrink/expand rate — alert on frequent changes
□ Producer error rate — alert on non-zero
□ Active controller count — must be 1
□ Network utilization — alert at 80%
□ GC pause duration — alert on long pauses
```

### Alerting Strategy

```
Severity levels:

P1 (CRITICAL - immediate response):
  - Offline partitions > 0
  - Active controller = 0
  - Broker down
  - Consumer lag > threshold AND growing for > 5 min

P2 (WARNING - investigate within hours):
  - Under-replicated partitions > 0
  - Consumer lag above threshold (stable)
  - Disk usage > 70%
  - ISR shrinking frequently
  - Producer error rate elevated

P3 (INFO - review daily):
  - Consumer rebalance frequency
  - Request latency percentile degradation
  - Topic approaching partition limit
  - Certificate expiration approaching
```

### Performance Tuning Summary

```
Producer tuning:
  batch.size=32768-65536, linger.ms=20-50, compression=lz4/zstd
  acks=all, enable.idempotence=true, buffer.memory=64MB

Consumer tuning:
  max.poll.records=100-500, fetch.min.bytes=1-100000
  enable.auto.commit=false, session.timeout.ms=45000

Broker tuning:
  num.network.threads=4-8, num.io.threads=8-16
  socket.send/receive.buffer.bytes=102400-1048576
  log.segment.bytes=512MB-1GB

OS tuning:
  vm.swappiness=1, vm.dirty_ratio=60-80
  net.core.wmem/rmem_max=2097152
  File descriptors: ulimit -n 100000+
```

### Multi-Region Deployment

```
Architecture options:

1. Single cluster, multi-AZ (within region):
   - Brokers spread across 3 AZs
   - rack.id=az-1, rack.id=az-2, rack.id=az-3
   - Replicas distributed across AZs
   - Survives AZ failure

2. Multi-cluster, multi-region:
   - Primary cluster in Region A
   - DR cluster in Region B
   - MirrorMaker 2 replicates A → B
   - Failover to B on region failure
   - RPO: seconds (async replication lag)
   - RTO: minutes (manual/automated failover)
```

---

## Diagram

### Production Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    PRODUCTION KAFKA ARCHITECTURE                          │
│                                                                           │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ KRaft Controller Quorum (3 dedicated nodes)                        │  │
│  │  [Controller-1] [Controller-2] [Controller-3]                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                           │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Kafka Brokers (6 nodes across 3 AZs)                               │  │
│  │                                                                     │  │
│  │  AZ-1              AZ-2              AZ-3                          │  │
│  │  [Broker-1]        [Broker-3]        [Broker-5]                    │  │
│  │  [Broker-2]        [Broker-4]        [Broker-6]                    │  │
│  │                                                                     │  │
│  │  rack.id=az-1      rack.id=az-2      rack.id=az-3                 │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐      │
│  │ Schema Registry   │  │ Kafka Connect    │  │ MirrorMaker 2   │      │
│  │ (HA, 2 nodes)    │  │ (distributed)    │  │ (DR replication) │      │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
    ┌─────────┴───┐  ┌───────┴─────┐  ┌─────┴─────────┐
    │ Producers    │  │ Consumers   │  │ Monitoring     │
    │ (services)   │  │ (services)  │  │ (Prometheus +  │
    │              │  │             │  │  Grafana)      │
    └──────────────┘  └─────────────┘  └───────────────┘
```

### Production Checklist Visualization

```
BEFORE GO-LIVE:
═══════════════
[✓] Capacity planning done (throughput, storage, broker count)
[✓] Partition count decided (can't decrease later!)
[✓] RF=3, min.insync.replicas=2, acks=all configured
[✓] Security: TLS + SASL + ACLs
[✓] Monitoring: lag, under-replicated, disk, CPU, network
[✓] Alerting: P1/P2/P3 alerts configured
[✓] DR plan: MirrorMaker 2 or multi-AZ
[✓] Schema Registry: compatibility mode set
[✓] Error handling: retry + DLT pattern
[✓] Idempotent consumers: all consumers handle duplicates
[✓] Load testing: verify under expected peak load
[✓] Runbook: failover procedures documented
[✓] Backup: offset management, topic configs backed up
```

---

## Interview Questions

### Q1: How would you design Kafka for a system processing 1 million events per second?

**A:**
- **Brokers:** 9-12 brokers (high-spec: NVMe SSDs, 10Gbps NIC, 128GB RAM)
- **Partitions:** 100-200 per high-traffic topic (for parallelism)
- **Producers:** linger.ms=50, batch.size=128KB, compression=lz4, acks=1 (if loss tolerance) or acks=all (if critical)
- **Consumers:** 100+ consumers per group, batch processing, async I/O
- **Network:** 25Gbps NICs, dedicated replication network
- **Storage:** NVMe with RAID 10, 70%+ RAM for page cache
- **Monitoring:** Real-time lag monitoring, auto-scaling consumers
- **Key insight:** Kafka can handle 1M+ msg/sec per broker for small messages — main challenge is consumer processing speed

### Q2: What are the most common production incidents with Kafka and how do you prevent them?

**A:**
1. **Disk full:** Set retention, monitor disk usage, alert at 70%
2. **Consumer lag explosion:** Auto-scale consumers, alert on lag growth rate
3. **Broker OOM:** Limit JVM heap, ensure page cache has RAM, tune `num.replica.fetchers`
4. **Network partition:** Multi-AZ deployment, `min.insync.replicas=2`
5. **Rebalance storm:** Use cooperative sticky assignor, static group membership, tune timeouts
6. **Poison message:** Configure DLT, error-handling deserializer
7. **Hot partition:** Monitor per-partition throughput, redesign key if needed
8. **Certificate expiration:** Automate cert rotation, alert 30 days before expiry

### Q3: How do you handle a scenario where you need to change the key of a topic with existing data?

**A:** You can't change keys of existing messages. Options:
1. **New topic:** Create new topic, produce all messages with new keys from source, switch consumers. Consumers need to handle migration period (read from both topics temporarily).
2. **Kafka Streams:** Read from old topic, re-key using `selectKey()`, write to new topic. Maintains ordering within new key groups.
3. **Dual-write period:** Produce to both old and new topic temporarily, migrate consumers one by one.
4. Key consideration: partition count may change too (new key → new hash distribution).

### Q4: What is the impact of increasing partitions on a running production topic?

**A:**
- **Positive:** More consumers can be added (scale beyond current limit)
- **Negative for keyed messages:** Same key may go to different partition (hash % newPartitionCount changes). Events for same entity split across old and new partitions. Ordering broken for affected keys.
- **Mitigation:** Only increase for topics where key ordering doesn't matter across the boundary, or accept that ordering is "best effort" during transition.
- **Best practice:** Choose partition count correctly upfront. If you must increase, coordinate with consumers to handle potential reordering.

### Q5: How would you migrate from a monolith to event-driven microservices with Kafka?

**A:** Strangler fig pattern:
1. **Phase 1:** Add Kafka infrastructure alongside monolith
2. **Phase 2:** Monolith publishes domain events to Kafka (outbox pattern)
3. **Phase 3:** Extract first microservice — consumes from Kafka instead of direct DB queries
4. **Phase 4:** New microservice publishes its own events
5. **Phase 5:** Repeat for next service, gradually removing monolith responsibilities
6. **Throughout:** Keep monolith's database as source of truth until service is fully migrated
7. **Key principle:** Never big-bang. Each step is independently deployable and reversible.

---

## Common Mistakes in Production

| Mistake | Impact | Prevention |
|---------|--------|-----------|
| No monitoring | Blind to issues until user reports | Set up Prometheus + Grafana from day 1 |
| Auto-commit enabled | Data loss on consumer crash | Always use manual commit in production |
| No DLT configured | Poison messages block consumers | Always configure error handling + DLT |
| RF=1 for "non-critical" topics | Data loss on broker failure | RF=3 for everything in production |
| Not testing failover | DR doesn't work when needed | Quarterly DR drills |
| Unlimited retention | Disk fills silently | Set retention, monitor, alert |
| No schema management | Breaking changes crash consumers | Schema Registry with compatibility |
| Single AZ deployment | Entire cluster down on AZ failure | Multi-AZ with rack awareness |

---

## Best Practices Summary

1. **Durability:** RF=3, min.insync.replicas=2, acks=all, idempotent producer
2. **Reliability:** Manual commits, DLT pattern, idempotent consumers
3. **Performance:** Compression, batching, right-sized partitions, page cache
4. **Operations:** Monitoring, alerting, runbooks, DR testing
5. **Security:** SASL_SSL, ACLs, network isolation, credential rotation
6. **Evolution:** Schema Registry, backward compatibility, versioned events
7. **Scaling:** Horizontal (add brokers/consumers), vertical (tune configs)
8. **Consistency:** Outbox pattern, CDC, idempotent consumers

---

## Related Topics

- [14. Durability & Reliability](./14-durability-reliability.md)
- [17. Kafka Performance](./17-kafka-performance.md)
- [27. Kafka Security](./27-kafka-security.md)
- [28. Kafka Monitoring](./28-kafka-monitoring.md)
- [32. Kafka Disaster Recovery](./32-kafka-disaster-recovery.md)
- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
