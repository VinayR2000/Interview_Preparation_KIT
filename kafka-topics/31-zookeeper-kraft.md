# 31. ZooKeeper & KRaft ⭐⭐

---

## Theory

Kafka historically depended on ZooKeeper for cluster coordination. KRaft (Kafka Raft) is the modern replacement that eliminates this dependency.

### ZooKeeper Architecture

```
ZooKeeper Ensemble (typically 3 or 5 nodes):
┌──────────┐  ┌──────────┐  ┌──────────┐
│ ZK Node 1│  │ ZK Node 2│  │ ZK Node 3│
│ (Leader) │  │(Follower) │  │(Follower) │
└─────┬────┘  └─────┬────┘  └─────┬────┘
      └──────────────┼──────────────┘
                     │
          ┌──────────┼──────────┐
          │          │          │
    ┌─────┴───┐┌────┴────┐┌───┴─────┐
    │Broker 1 ││Broker 2 ││Broker 3 │
    └─────────┘└─────────┘└─────────┘
```

### ZooKeeper Role in Kafka

```
What ZooKeeper managed:
1. Controller election (which broker is controller)
2. Broker registration (which brokers are alive)
3. Topic configuration (partition count, RF, configs)
4. Partition leadership (which broker leads each partition)
5. ISR tracking (which replicas are in-sync)
6. ACLs (access control lists)
7. Consumer group offsets (legacy, moved to Kafka in 0.9+)
```

### Problems with ZooKeeper

```
1. Operational complexity: Separate distributed system to manage
2. Scalability limit: Metadata writes bottlenecked at ZK
3. Recovery time: Controller failover requires full metadata reload from ZK
4. Consistency issues: Split-brain possible during network partitions
5. Double infrastructure: ZK + Kafka = two distributed systems
6. Configuration: Two systems to configure, monitor, and upgrade
```

### KRaft (Kafka Raft) — The Replacement

```
KRaft removes ZooKeeper entirely. Kafka manages its own metadata using:
- Raft consensus protocol (for leader election and log replication)
- Internal metadata topic (__cluster_metadata)
- Controller quorum (dedicated controller nodes or combined mode)

Benefits:
- Single system to operate (no ZooKeeper)
- Faster controller failover (seconds → milliseconds)
- Better scalability (millions of partitions)
- Simpler deployment and configuration
- Event-driven metadata (instead of state-based ZK)
```

### KRaft Controller

```
KRaft Mode:
┌──────────────────────────────────────────────────────┐
│ Controller Quorum (3 controllers)                     │
│                                                        │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐        │
│ │Controller 1│ │Controller 2│ │Controller 3│        │
│ │  (Active)  │ │ (Standby)  │ │ (Standby)  │        │
│ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘        │
│       └───────────────┼───────────────┘               │
│                Raft Consensus                          │
│           __cluster_metadata log                       │
└──────────────────────┬───────────────────────────────┘
                       │ metadata updates
          ┌────────────┼────────────┐
          │            │            │
    ┌─────┴───┐ ┌─────┴───┐ ┌─────┴───┐
    │Broker 1 │ │Broker 2 │ │Broker 3 │
    │(data)   │ │(data)   │ │(data)   │
    └─────────┘ └─────────┘ └─────────┘
```

### Metadata Quorum

```
The controller quorum uses Raft protocol:
- One ACTIVE controller (leader of the quorum)
- Others are STANDBY (followers)
- Active controller handles:
  - Partition assignments
  - Leader elections
  - Configuration changes
  - Broker registrations
- All state stored in __cluster_metadata topic (event log)
- Standby controllers replicate this log via Raft
- Failover: Raft elects new active controller (sub-second)
```

### ZooKeeper vs KRaft Comparison

| Aspect | ZooKeeper Mode | KRaft Mode |
|--------|---------------|------------|
| Dependencies | Kafka + ZooKeeper | Kafka only |
| Controller failover | Seconds (reload metadata) | Milliseconds (Raft election) |
| Max partitions | ~200K (ZK bottleneck) | Millions (no ZK limit) |
| Operational complexity | High (2 systems) | Lower (1 system) |
| Configuration | Complex (2 configs) | Simpler (1 config) |
| Maturity | Proven (10+ years) | Production-ready (Kafka 3.3+) |
| Status | Deprecated (removed in Kafka 4.0) | Default/future |

### Modern Kafka Architecture (KRaft)

```
Deployment modes:

1. Combined mode (small clusters):
   - Same nodes act as both controllers AND brokers
   - process.roles=broker,controller
   - Good for: development, small deployments

2. Dedicated mode (production):
   - Separate controller nodes (3 or 5) — no data storage
   - Separate broker nodes — handle produce/consume
   - process.roles=controller (controllers)
   - process.roles=broker (brokers)
   - Good for: production, large clusters
```

### KRaft Configuration

```properties
# Controller node
process.roles=controller
node.id=1
controller.quorum.voters=1@controller1:9093,2@controller2:9093,3@controller3:9093
listeners=CONTROLLER://controller1:9093
controller.listener.names=CONTROLLER

# Broker node
process.roles=broker
node.id=101
controller.quorum.voters=1@controller1:9093,2@controller2:9093,3@controller3:9093
listeners=PLAINTEXT://broker1:9092
inter.broker.listener.name=PLAINTEXT

# Combined mode (dev)
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
listeners=PLAINTEXT://localhost:9092,CONTROLLER://localhost:9093
```

---

## Interview Questions

### Q1: Why is Kafka moving away from ZooKeeper?

**A:** Several fundamental issues:
1. **Operational burden:** Two distributed systems to deploy, configure, monitor, and upgrade
2. **Scalability ceiling:** ZooKeeper becomes a bottleneck beyond ~200K partitions (metadata writes)
3. **Slow failover:** Controller election requires reloading all metadata from ZK (seconds to minutes for large clusters)
4. **Consistency complexity:** ZK's eventual consistency model can cause metadata divergence
5. **Single responsibility:** Kafka is better served by managing its own consensus rather than depending on an external system
- KRaft solves all of these with an integrated Raft-based metadata quorum.

### Q2: How does KRaft improve controller failover time?

**A:** In ZooKeeper mode: when the controller dies, a new controller is elected, then it must read ALL cluster metadata from ZooKeeper into memory (potentially millions of partition records). For large clusters, this takes 30-60+ seconds.

In KRaft mode: metadata is replicated via Raft to standby controllers continuously. When the active controller fails, a standby already has all metadata in memory — Raft election completes in milliseconds. No reload needed. This reduces failover from minutes to sub-second.

### Q3: What is the migration path from ZooKeeper to KRaft?

**A:** Kafka provides a migration tool:
1. Deploy KRaft controllers alongside existing ZK-based cluster
2. Run `kafka-metadata.sh` to migrate metadata from ZK to KRaft
3. Reconfigure brokers to point to KRaft quorum (rolling restart)
4. Verify everything works
5. Decommission ZooKeeper nodes
- Kafka 3.3+ supports KRaft in production. Kafka 4.0 removes ZooKeeper entirely.

---

## Best Practices

1. **New deployments: Use KRaft** — no reason to start with ZooKeeper in 2024+
2. **Plan ZK → KRaft migration** — ZooKeeper is deprecated
3. **Use dedicated controller nodes** in production (separate from brokers)
4. **3 or 5 controller nodes** for quorum (odd number for majority)
5. **Monitor controller quorum** — same importance as monitoring brokers

---

## Related Topics

- [03. Kafka Architecture](./03-kafka-architecture.md)
- [30. Kafka Cluster Management](./30-kafka-cluster-management.md)
- [13. Replication](./13-replication.md)
