# 32. Kafka Disaster Recovery

---

## Theory

Disaster recovery (DR) for Kafka ensures data and service availability across failures ranging from single broker crashes to entire region outages.

### Backup Strategies

```
Kafka data backup options:
1. Replication (within cluster): RF=3 — survives broker failures
2. MirrorMaker 2: Cross-cluster replication (DR site)
3. Object storage: Tiered storage to S3/GCS (long-term archival)
4. Consumer-based backup: Consume and write to external storage

Note: Kafka doesn't have traditional "backup/restore" — replication IS the backup
```

### Multi-Region Kafka

```
Region A (Primary)                    Region B (DR)
┌──────────────────┐                 ┌──────────────────┐
│ Kafka Cluster A  │   MirrorMaker2  │ Kafka Cluster B  │
│ (Brokers 1,2,3)  │ ───────────────► │ (Brokers 4,5,6)  │
│                  │   async repl    │                  │
│ Topic: orders    │                 │ Topic: orders    │
│ (source of truth)│                 │ (replica)        │
└──────────────────┘                 └──────────────────┘
```

### MirrorMaker 2 (MM2)

```
What it does:
- Replicates topics from source to target cluster
- Preserves topic names (configurable prefix: source.topic-name)
- Translates consumer offsets between clusters
- Monitors replication lag
- Handles schema replication

Features:
- Based on Kafka Connect framework
- Supports topic filtering (include/exclude patterns)
- Offset sync (consumers can failover to DR without re-reading)
- Heartbeat topics (monitoring replication health)
- Checkpoints (periodic offset translation snapshots)
```

### Active-Active vs Active-Passive

```
Active-Passive:
  Region A: All producers write here (active)
  Region B: Read-only replica (passive, for DR)
  Failover: Redirect producers to Region B, consumers switch cluster
  Pros: Simple, no conflict resolution needed
  Cons: Region B resources idle until failover

Active-Active:
  Region A: Handles region-A traffic (writes local topics)
  Region B: Handles region-B traffic (writes local topics)
  Both replicate to each other (bidirectional MM2)
  Pros: Both regions utilized, lower latency for local users
  Cons: Complex — must avoid circular replication, handle conflicts
  
  Conflict avoidance:
  - Topic naming: region-prefix (us.orders, eu.orders)
  - Or: each region owns specific partitions
  - Or: aggregate topics replicated one-way only
```

### Failover Process

```
Active-Passive Failover:
1. Detect primary region failure (monitoring/alerting)
2. Verify DR cluster is caught up (check replication lag)
3. Stop MirrorMaker (prevent partial replication)
4. Update DNS/routing to point to DR cluster
5. Restart producers → they write to DR cluster
6. Translate consumer offsets (MM2 offset sync)
7. Restart consumers → resume from translated offsets
8. Verify processing is working

Failback (return to primary):
1. Repair primary region
2. Start MM2 from DR → Primary (reverse direction)
3. Wait for primary to catch up
4. Switch traffic back to primary
5. Resume normal MM2 direction (Primary → DR)
```

### RPO and RTO

```
RPO (Recovery Point Objective): How much data can you afford to lose?
  Within-cluster (acks=all, RF=3): RPO ≈ 0 (synchronous replication)
  Cross-region (MM2): RPO = replication lag (typically seconds to minutes)

RTO (Recovery Time Objective): How fast must you recover?
  Broker failure: seconds (automatic leader election)
  Region failure: minutes (manual failover to DR)
  Automated failover: faster but risk of false positives
```

---

## Interview Questions

### Q1: How would you design a Kafka disaster recovery setup for a financial system?

**A:**
- **Within region:** RF=3, min.insync.replicas=2, acks=all, 3+ brokers across AZs
- **Cross-region:** MirrorMaker 2 active-passive to DR region
- **RPO target:** <30 seconds (monitor MM2 lag, alert if exceeds)
- **RTO target:** <5 minutes (automated detection, semi-automated failover)
- **Offset translation:** Enable MM2 checkpoint syncing for consumer failover
- **Regular DR drills:** Test failover quarterly to verify procedures
- **After failover:** Ensure idempotent consumers handle potential message replay

### Q2: What are the challenges of active-active Kafka across regions?

**A:**
1. **Circular replication:** Must prevent topic A→B→A loops (use topic prefixes or provenance headers)
2. **Ordering:** No global ordering across regions (each region has local ordering)
3. **Conflicts:** Two regions writing same key → last-writer-wins or custom resolution
4. **Latency:** Cross-region replication adds 50-200ms (async — can't be synchronous)
5. **Offset divergence:** Same topic has different offsets in each cluster
6. **Complexity:** Significantly harder to operate and reason about than active-passive

### Q3: How does MirrorMaker 2 handle consumer offset translation?

**A:** MM2 maintains a mapping between source and target cluster offsets:
- Periodically writes **checkpoint** records to a special topic
- Checkpoint: source offset → target offset for each consumer group/partition
- On failover: consumers look up their source committed offset, translate to target offset via checkpoint, and seek to that position
- Not perfectly precise (checkpoints are periodic) — consumers may re-read a few messages
- Idempotent consumers handle this gracefully

---

## Best Practices

1. **Within-cluster:** RF=3, min.insync.replicas=2, spread across AZs
2. **Cross-region DR:** MirrorMaker 2 with offset sync enabled
3. **Monitor replication lag** — RPO depends on how caught up DR is
4. **Test failover regularly** — untested DR is not DR
5. **Document runbook** — step-by-step failover/failback procedures
6. **Prefer active-passive** unless you have a strong reason for active-active
7. **Automate detection, semi-automate failover** — avoid false positives

---

## Related Topics

- [30. Kafka Cluster Management](./30-kafka-cluster-management.md)
- [13. Replication](./13-replication.md)
- [36. Production-Level Kafka](./36-production-level-kafka.md)
