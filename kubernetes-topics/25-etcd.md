# 25. etcd ⭐⭐

---

## Theory

**etcd** is a distributed, strongly consistent key-value store that serves as Kubernetes' single source of truth for all cluster state.

### What is etcd?

```
etcd = Kubernetes' database

Stores everything:
  - All object definitions (Pods, Services, Deployments, etc.)
  - Cluster configuration
  - Secrets
  - RBAC policies
  - Current and desired state

Properties:
  - Distributed (runs on multiple nodes)
  - Strongly consistent (Raft consensus)
  - Key-value store with prefix queries
  - Watch support (real-time notifications)
  - MVCC (Multi-Version Concurrency Control)
```

### Key-Value Store

```
etcd stores data as key-value pairs with hierarchy:

/registry/pods/default/my-pod → {pod JSON}
/registry/services/production/order-service → {service JSON}
/registry/deployments/production/my-app → {deployment JSON}
/registry/secrets/production/db-secret → {encrypted secret}

Prefix queries:
  /registry/pods/default/ → all pods in default namespace
  /registry/ → all resources (don't do this in production!)
```

### Kubernetes State

```
What etcd stores for K8s:
  - Object spec (desired state, user-defined)
  - Object status (current state, controller-updated)
  - Object metadata (name, labels, annotations, resourceVersion)
  - Cluster state (node registrations, leases)
  - Secrets and ConfigMaps

What etcd does NOT store:
  - Container logs
  - Metrics data
  - Container images
  - Node filesystem state
```

### Consistency

```
etcd guarantees:
  - Linearizability: Reads always return most recent write
  - Sequential consistency: Operations observed in same order by all
  - Atomicity: Transactions succeed or fail completely

Why this matters:
  - No stale reads (won't schedule pod on deleted node)
  - No lost updates (concurrent writes are serialized)
  - Reliable watch notifications (no missed events)
```

### Raft

```
Raft consensus protocol:

Roles:
  Leader:    Handles all writes, replicates to followers
  Follower:  Receives replications, responds to reads
  Candidate: Temporary state during election

Write flow:
  1. Client sends write to Leader
  2. Leader appends to its log
  3. Leader replicates to all Followers
  4. Majority acknowledge → committed
  5. Leader responds to client

Quorum: Majority must agree
  3 nodes: 2 must agree (survives 1 failure)
  5 nodes: 3 must agree (survives 2 failures)
  7 nodes: 4 must agree (survives 3 failures)
```

### Leader Election

```
Leader election occurs when:
  - Cluster starts fresh
  - Current leader fails (heartbeat timeout)
  - Network partition isolates leader

Process:
  1. Follower doesn't receive heartbeat (election timeout: 150-300ms)
  2. Becomes Candidate, increments term, votes for itself
  3. Requests votes from other nodes
  4. If majority votes received → becomes Leader
  5. New Leader starts sending heartbeats

Split-brain prevention:
  - Only one Leader per term
  - Requires majority (can't have two majorities)
  - Stale Leader (old term) rejected
```

### Backup

```bash
# Snapshot backup
ETCDCTL_API=3 etcdctl snapshot save /backup/etcd-snapshot.db \
  --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/etcd/ca.crt \
  --cert=/etc/etcd/server.crt \
  --key=/etc/etcd/server.key

# Verify snapshot
etcdctl snapshot status /backup/etcd-snapshot.db --write-table

# Backup strategy:
#   - Every 30 minutes (automated via CronJob or cron)
#   - Before cluster upgrades
#   - Store off-cluster (S3, GCS, etc.)
#   - Test restore procedure regularly
```

### Restore

```bash
# Stop kube-apiserver and etcd

# Restore from snapshot
ETCDCTL_API=3 etcdctl snapshot restore /backup/etcd-snapshot.db \
  --data-dir=/var/lib/etcd-restored \
  --initial-cluster="master1=https://master1:2380" \
  --initial-advertise-peer-urls=https://master1:2380 \
  --name=master1

# Update etcd config to use new data-dir
# Restart etcd and kube-apiserver

# Disaster Recovery:
#   - Restores cluster state to backup point
#   - Pods running at backup time will be recreated
#   - Pods created after backup are lost (recreate manually)
```

---

## Interview Questions

### Q1: Why is etcd so critical for Kubernetes?

**A:** etcd is the ONLY state store for the entire cluster. Every object (pods, services, secrets, configs) is stored there. If etcd is lost without backup, the entire cluster state is gone — you'd need to recreate everything from scratch. That's why etcd HA (3-5 nodes) and regular backups are mandatory for production.

### Q2: How does etcd handle node failures?

**A:** Using Raft consensus with quorum:
- 3-node cluster: survives 1 node failure (2/3 = majority)
- 5-node cluster: survives 2 node failures (3/5 = majority)
- If majority is lost: cluster becomes read-only (can't write)

Leader failure: remaining nodes elect new leader (usually <1 second).

### Q3: What is the recommended etcd cluster size?

**A:** 3 or 5 nodes:
- **3 nodes:** Minimum for HA. Survives 1 failure. Good for most clusters.
- **5 nodes:** Survives 2 failures. Higher availability but slower writes (more replication).
- **Never even numbers:** 4 nodes has same fault tolerance as 3 (both need majority of 3).
- **7+ rarely needed:** Diminishing returns, slower consensus.

### Q4: How do you back up and restore etcd?

**A:** 
- **Backup:** `etcdctl snapshot save` — creates point-in-time snapshot. Automate every 30 min. Store off-cluster (S3). Verify with `snapshot status`.
- **Restore:** Stop API Server → `etcdctl snapshot restore` → update etcd data-dir → restart. All state reverts to backup point.

---

## Best Practices

1. **3 or 5 etcd nodes** for production HA
2. **SSD storage** — etcd is sensitive to disk latency
3. **Regular backups** — every 30 minutes, stored off-cluster
4. **Monitor etcd** — disk I/O, leader changes, request latency
5. **Dedicated nodes** — don't share with workloads
6. **Encrypt at rest** — especially for secrets
7. **Test restores** — backup is useless if restore doesn't work
8. **Compaction** — prevent unbounded growth (auto-compact recommended)

---

## Related Topics

- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
- [24. Kubernetes Internals](./24-kubernetes-internals.md)
- [36. Production Architecture](./36-production-architecture.md)
