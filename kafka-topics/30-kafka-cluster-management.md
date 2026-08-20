# 30. Kafka Cluster Management

---

## Theory

Managing a Kafka cluster involves scaling brokers, balancing partitions, handling failures, and planning for disaster recovery.

### Broker Addition

```
Adding a new broker to the cluster:
1. Configure new broker with unique broker.id
2. Point to same ZooKeeper/KRaft quorum
3. Start broker — it joins the cluster automatically
4. BUT: existing partitions are NOT automatically moved to new broker
5. Must manually reassign partitions to balance load

After adding Broker 4 to a 3-broker cluster:
  Before: Broker 1,2,3 each have ~33% of partitions
  After (without rebalance): Broker 1,2,3 still have ~33%, Broker 4 has 0%
  After (with reassignment): All four brokers have ~25%
```

### Broker Removal

```
Removing a broker:
1. Reassign all partitions from target broker to remaining brokers
2. Wait for reassignment to complete (verify with --verify)
3. Stop the broker gracefully (it will deregister)
4. If broker stops without reassignment → partitions lose one replica
   → ISR shrinks → reduced fault tolerance until re-replicated elsewhere
```

### Partition Reassignment

```json
// reassignment.json
{
  "version": 1,
  "partitions": [
    {"topic": "orders", "partition": 0, "replicas": [2, 3, 4]},
    {"topic": "orders", "partition": 1, "replicas": [3, 4, 1]},
    {"topic": "orders", "partition": 2, "replicas": [4, 1, 2]}
  ]
}
```

```bash
# Execute with throttle (limit replication bandwidth)
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --execute --throttle 50000000  # 50 MB/s limit
```

### Leader Balancing

```
Preferred leader election:
- First replica in the replica list is the "preferred leader"
- After broker restarts, leaders may not return to preferred broker
- Auto leader rebalance: auto.leader.rebalance.enable=true (default)
- Or manual: kafka-leader-election.sh --election-type preferred

Importance:
- Unbalanced leaders = uneven load (some brokers handle more traffic)
- Regular preferred leader election maintains balance
```

### Failover

```
Broker failure handling:
1. Controller detects broker down (heartbeat timeout)
2. For each partition where failed broker was LEADER:
   → Controller elects new leader from ISR
   → Metadata updated, clients redirect
3. For each partition where failed broker was FOLLOWER:
   → ISR shrinks (fewer replicas available)
   → Writes continue if ISR ≥ min.insync.replicas
4. When broker recovers:
   → Truncates log to HW, fetches from current leaders
   → Rejoins ISR when caught up
```

### Cluster Expansion

```
Scaling strategy:
1. Add brokers to cluster
2. Create reassignment plan (spread hot topics to new brokers)
3. Execute with throttle (avoid I/O spikes)
4. Verify completion
5. Run preferred leader election (balance leaders)

Considerations:
- Reassignment replicates data (network + disk intensive)
- Throttle to avoid impacting production traffic
- Plan during low-traffic windows
- Monitor: network usage, disk I/O, consumer lag during migration
```

### Disaster Recovery

```
Strategies:
1. Multi-rack deployment (rack.id): survive rack failure
2. Multi-AZ deployment: survive availability zone failure
3. Multi-region (MirrorMaker 2): survive region failure
4. Backup to object storage (S3/GCS): point-in-time recovery

RPO (Recovery Point Objective):
  - Within-cluster: ~0 (synchronous replication with acks=all)
  - Cross-region: seconds to minutes (async replication lag)

RTO (Recovery Time Objective):
  - Within-cluster: seconds (automatic leader election)
  - Cross-region: minutes (DNS/routing switch + consumer restart)
```

---

## Interview Questions

### Q1: How do you scale a Kafka cluster without downtime?

**A:** 
1. Add new brokers (they join cluster automatically, no restart needed)
2. Generate partition reassignment plan targeting new brokers
3. Execute reassignment with throttle (limits I/O impact on production)
4. Monitor reassignment progress and consumer lag
5. Once complete, run preferred leader election to balance leaders
6. Zero downtime throughout — producers/consumers continue working during reassignment

### Q2: What is the impact of broker failure with RF=3 and min.insync.replicas=2?

**A:**
- 1 broker fails: ISR shrinks to 2 for affected partitions. Writes still succeed (2 ≥ min.insync.replicas=2). Reads continue from leader. Fault tolerance reduced to surviving 0 more failures for those partitions.
- 2 brokers fail: ISR shrinks to 1. Writes REJECTED (1 < min.insync.replicas=2). Reads may still work from surviving leader. Need at least one broker to recover for writes to resume.
- All 3 fail: Complete unavailability until recovery.

### Q3: How would you handle a multi-region Kafka deployment?

**A:** Use MirrorMaker 2 (or Confluent Cluster Linking):
- Active-passive: Primary region handles all writes, MirrorMaker replicates to DR region
- Active-active: Both regions accept writes for their topics, cross-replicate via MirrorMaker
- Considerations: async replication (some lag), offset translation between clusters, topic naming conventions, consumer failover strategy
- Use Kafka's `rack.id` for rack-awareness within a region for intra-region fault tolerance

---

## Best Practices

1. **Always throttle reassignment** — unthrottled saturates network/disk
2. **Plan capacity ahead** — scaling under pressure is risky
3. **Use rack-awareness** (rack.id) — distribute replicas across failure domains
4. **Automate preferred leader election** — keep leaders balanced
5. **Test DR procedures** — practice failover before you need it
6. **Monitor during operations** — watch lag, ISR, throughput during changes

---

## Related Topics

- [29. Kafka Operations](./29-kafka-operations.md)
- [31. ZooKeeper & KRaft](./31-zookeeper-kraft.md)
- [32. Kafka Disaster Recovery](./32-kafka-disaster-recovery.md)
