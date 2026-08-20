# Topic 31: Distributed Database Concepts

## Theory

### CAP Theorem

```
┌─────────────────────────────────────────────────────────────────┐
│                    CAP THEOREM                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  In a distributed system, you can only guarantee 2 of 3:         │
│                                                                  │
│              Consistency                                          │
│                 /\                                                │
│                /  \                                               │
│               /    \                                              │
│              / CP   \                                             │
│             /        \                                            │
│            /    CA    \                                           │
│           /  (not     \                                          │
│          /  possible   \                                         │
│         /  in distrib)  \                                        │
│        /________________\                                        │
│  Availability ──── AP ──── Partition Tolerance                   │
│                                                                  │
│  C (Consistency): Every read returns the most recent write       │
│  A (Availability): Every request gets a response (non-error)     │
│  P (Partition Tolerance): System works despite network splits    │
│                                                                  │
│  IN REALITY: Partition tolerance is NOT optional in distributed  │
│  systems (networks WILL fail). So the real choice is:            │
│                                                                  │
│  CP: Consistency + Partition Tolerance                            │
│      → Sacrifice availability during partition                   │
│      → Examples: PostgreSQL (single), ZooKeeper, etcd, HBase    │
│      → Use when: Financial transactions, inventory counts        │
│                                                                  │
│  AP: Availability + Partition Tolerance                           │
│      → Sacrifice consistency during partition                    │
│      → Examples: Cassandra, DynamoDB, CouchDB                    │
│      → Use when: Social feeds, analytics, shopping carts         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Consistency Models

```
┌─────────────────────────────────────────────────────────────────┐
│             CONSISTENCY SPECTRUM                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  STRONG ←─────────────────────────────────────────→ WEAK         │
│                                                                  │
│  Linearizable                                                    │
│  │  • Strongest guarantee                                        │
│  │  • Every read sees the latest write (real-time)               │
│  │  • Single leader, synchronous replication                     │
│  │  • Example: PostgreSQL primary                                │
│  │                                                               │
│  Sequential Consistency                                          │
│  │  • Operations appear in some sequential order                 │
│  │  • All nodes agree on the order                               │
│  │                                                               │
│  Causal Consistency                                              │
│  │  • Causally related operations are ordered                    │
│  │  • Concurrent operations may be seen in any order             │
│  │                                                               │
│  Read-Your-Writes                                                │
│  │  • After a write, the same client always sees it              │
│  │  • Other clients may see stale data temporarily               │
│  │                                                               │
│  Eventual Consistency                                            │
│     • If no new writes, all replicas will converge               │
│     • Reads may return stale data                                │
│     • Lowest latency, highest availability                       │
│     • Example: DNS, Cassandra (tunable)                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Distributed Transactions

### Two-Phase Commit (2PC)

```
┌─────────────────────────────────────────────────────────────────┐
│              TWO-PHASE COMMIT (2PC)                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│              ┌──────────────┐                                    │
│              │ Coordinator  │                                    │
│              └──────┬───────┘                                    │
│                     │                                            │
│    ┌────────────────┼────────────────┐                           │
│    │                │                │                           │
│    ▼                ▼                ▼                           │
│ ┌──────┐       ┌──────┐       ┌──────┐                         │
│ │Node A│       │Node B│       │Node C│                         │
│ └──────┘       └──────┘       └──────┘                         │
│                                                                  │
│ PHASE 1: PREPARE (vote)                                          │
│ ─────────────────────────                                        │
│ Coordinator → All Nodes: "Can you commit?"                       │
│ Node A → Coordinator: "YES, prepared"                            │
│ Node B → Coordinator: "YES, prepared"                            │
│ Node C → Coordinator: "YES, prepared"                            │
│                                                                  │
│ PHASE 2: COMMIT (execute)                                        │
│ ─────────────────────────                                        │
│ If ALL said YES:                                                 │
│   Coordinator → All Nodes: "COMMIT"                              │
│   All nodes commit their local transaction                       │
│                                                                  │
│ If ANY said NO (or timeout):                                     │
│   Coordinator → All Nodes: "ROLLBACK"                            │
│   All nodes rollback their local transaction                     │
│                                                                  │
│ PROBLEMS WITH 2PC:                                               │
│ • Blocking: If coordinator crashes after PREPARE, nodes WAIT     │
│ • Performance: Locks held across network round-trips             │
│ • Availability: Any node failure blocks the whole transaction    │
│ • Not partition-tolerant: Network split = stuck                  │
│                                                                  │
│ WHY NOT USED IN MICROSERVICES:                                   │
│ • Too slow (distributed lock duration)                           │
│ • Too brittle (single point of failure)                          │
│ • Reduces availability (violates CAP)                            │
│ • Alternative: Saga pattern (eventual consistency)               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Saga Pattern (Preferred for Microservices)

```
2PC vs Saga:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
│ Aspect        │ 2PC                    │ Saga                  │
├───────────────┼────────────────────────┼───────────────────────┤
│ Consistency   │ Strong (ACID)          │ Eventual              │
│ Availability  │ Low (blocking)         │ High                  │
│ Performance   │ Slow (distributed lock)│ Fast (local txns)     │
│ Complexity    │ Medium                 │ High (compensations)  │
│ Isolation     │ Full                   │ Requires design       │
│ Recovery      │ Coordinator-dependent  │ Event-driven          │
│ Use case      │ Within single system   │ Across microservices  │
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Consensus Algorithms

### Why Consensus?

```
┌─────────────────────────────────────────────────────────────────┐
│              CONSENSUS — WHY AND WHAT                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PROBLEM: Multiple nodes must agree on a value/leader            │
│  despite failures and network partitions                         │
│                                                                  │
│  USE CASES:                                                      │
│  • Leader election (who is the primary DB?)                      │
│  • Distributed locking (only one service holds the lock)         │
│  • Configuration management (all nodes agree on config)          │
│  • State machine replication (all replicas apply same ops)       │
│                                                                  │
│  ALGORITHMS:                                                     │
│  • Paxos (theoretical foundation, complex)                       │
│  • Raft (understandable consensus, used in etcd)                │
│  • ZAB (ZooKeeper Atomic Broadcast)                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Raft Consensus (Simplified)

```
┌─────────────────────────────────────────────────────────────────┐
│                  RAFT CONSENSUS                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ROLES:                                                          │
│  • Leader: Handles all client requests, replicates to followers  │
│  • Follower: Accepts replicated log entries from leader          │
│  • Candidate: Requests votes when leader is absent               │
│                                                                  │
│  LEADER ELECTION:                                                │
│  1. Followers don't hear from leader (timeout)                   │
│  2. Follower becomes Candidate, requests votes                   │
│  3. If majority votes YES → becomes new Leader                   │
│  4. Leader sends heartbeats to prevent new elections             │
│                                                                  │
│  LOG REPLICATION:                                                │
│  1. Client sends write to Leader                                 │
│  2. Leader appends to its log                                    │
│  3. Leader sends AppendEntries to all Followers                  │
│  4. When MAJORITY acknowledges → entry is committed             │
│  5. Leader responds to client: "committed"                       │
│                                                                  │
│  SAFETY:                                                         │
│  • Only leader with most up-to-date log can win election         │
│  • Committed entries are never lost (majority guarantee)         │
│                                                                  │
│  QUORUM: Majority = (N/2) + 1                                    │
│  • 3 nodes: quorum = 2 (tolerates 1 failure)                    │
│  • 5 nodes: quorum = 3 (tolerates 2 failures)                   │
│  • 7 nodes: quorum = 4 (tolerates 3 failures)                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Quorum

```
┌─────────────────────────────────────────────────────────────────┐
│                    QUORUM SYSTEMS                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  N = total nodes                                                 │
│  W = write quorum (nodes that must acknowledge a write)          │
│  R = read quorum (nodes that must respond to a read)             │
│                                                                  │
│  RULE: W + R > N guarantees consistency                          │
│  (at least one node in read set has the latest write)            │
│                                                                  │
│  EXAMPLES (N=3):                                                 │
│                                                                  │
│  W=2, R=2: Strong consistency                                    │
│    Write to 2 of 3 nodes                                         │
│    Read from 2 of 3 nodes                                        │
│    → At least 1 node has latest data (overlap guaranteed)        │
│                                                                  │
│  W=3, R=1: Favor read speed                                      │
│    Write to ALL 3 nodes (slow writes)                            │
│    Read from ANY 1 node (fast reads)                             │
│    → Every node has latest data                                  │
│                                                                  │
│  W=1, R=3: Favor write speed                                     │
│    Write to ANY 1 node (fast writes)                             │
│    Read from ALL 3 nodes (slow reads)                            │
│    → Must read all to find latest                                │
│                                                                  │
│  W=1, R=1: Eventual consistency (W+R=2, not > N=3)              │
│    Fast reads AND writes                                         │
│    → May read stale data                                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Leader Election

```
┌─────────────────────────────────────────────────────────────────┐
│              LEADER ELECTION                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  WHY: Only ONE node should be primary/leader to avoid conflicts  │
│                                                                  │
│  MECHANISMS:                                                     │
│                                                                  │
│  1. External consensus (most common):                            │
│     • etcd / ZooKeeper / Consul                                  │
│     • Leader acquires a distributed lock                         │
│     • If leader dies, lock expires → new election                │
│     • Used by: Patroni (PostgreSQL HA)                           │
│                                                                  │
│  2. Built-in consensus:                                          │
│     • Database clusters with built-in election                   │
│     • CockroachDB, TiDB, YugabyteDB                             │
│     • Use Raft internally                                        │
│                                                                  │
│  PATRONI (PostgreSQL HA) with etcd:                              │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐                     │
│  │ Primary │    │ Standby │    │ Standby │                     │
│  │ (leader)│    │         │    │         │                     │
│  └────┬────┘    └────┬────┘    └────┬────┘                     │
│       │              │              │                            │
│       └──────────────┼──────────────┘                            │
│                      │                                           │
│              ┌───────▼───────┐                                   │
│              │  etcd cluster │ (consensus)                       │
│              └───────────────┘                                   │
│                                                                  │
│  • Primary holds leader lock in etcd                             │
│  • Heartbeat every 1-5 seconds                                   │
│  • If heartbeat missed → standbys compete for lock               │
│  • Winner promoted to primary                                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Split Brain

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPLIT BRAIN                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PROBLEM:                                                        │
│  Network partition → both sides think THEY are the primary       │
│  → Both accept writes → DATA DIVERGENCE (inconsistency!)        │
│                                                                  │
│  ┌─────────┐    NETWORK    ┌─────────┐                         │
│  │ Node A  │    PARTITION   │ Node B  │                         │
│  │(primary)│ ──── ✗ ────── │(primary)│  ← SPLIT BRAIN!        │
│  │ Clients │               │ Clients │                         │
│  │ write   │               │ write   │                         │
│  └─────────┘               └─────────┘                         │
│                                                                  │
│  PREVENTION:                                                     │
│                                                                  │
│  1. FENCING (most common):                                       │
│     • Old primary is forcibly shut down before promoting new     │
│     • STONITH (Shoot The Other Node In The Head)                 │
│     • Power-off the old primary via IPMI/cloud API               │
│                                                                  │
│  2. QUORUM-BASED:                                                │
│     • Require majority agreement to be primary                   │
│     • Node without quorum steps down (stops accepting writes)    │
│     • 3 nodes: need 2 to agree. Minority partition → read-only  │
│                                                                  │
│  3. LEASE-BASED:                                                 │
│     • Leader holds a time-limited lease                           │
│     • Must renew lease periodically                              │
│     • If can't renew (network issue) → steps down               │
│     • Other nodes wait for lease expiry before promoting         │
│                                                                  │
│  PATRONI APPROACH:                                               │
│     • Uses etcd for consensus (quorum-based)                     │
│     • Primary must maintain etcd leader key                      │
│     • If can't reach etcd → demotes itself (safe)                │
│     • Standbys check etcd before accepting promotion             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code — Distributed Lock with Retry

```java
// Using Redis for distributed locking (Redisson)
@Service
public class DistributedLockService {

    @Autowired
    private RedissonClient redisson;

    public <T> T executeWithLock(String lockKey, Duration timeout, Supplier<T> action) {
        RLock lock = redisson.getLock(lockKey);
        
        try {
            // Try to acquire lock with timeout
            boolean acquired = lock.tryLock(timeout.toMillis(), 
                                            30000, // Hold for max 30s
                                            TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new LockAcquisitionException("Could not acquire lock: " + lockKey);
            }
            
            return action.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted waiting for lock");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

// Usage — ensure only one instance processes a scheduled job
@Scheduled(cron = "0 0 * * * *")
public void hourlyReconciliation() {
    lockService.executeWithLock("reconciliation-job", Duration.ofMinutes(5), () -> {
        // Only ONE instance across the cluster executes this
        reconciliationService.run();
        return null;
    });
}
```

---

## Dry Run — Quorum Write with N=3, W=2

```
Setup: 3 nodes (Node-A, Node-B, Node-C), W=2

Client writes: SET user.email = 'new@test.com'

1. Client sends write to coordinator (any node)
   → Coordinator: Node-A

2. Node-A sends write to Node-B and Node-C
   → Node-B: Write received, ACK ✓
   → Node-C: Network timeout... no response ✗

3. Count acknowledgments: 2 (Node-A + Node-B)
   → W=2 achieved → WRITE COMMITTED ✓
   → Client receives: "Success"

4. Node-C comes back online later:
   → Receives write via anti-entropy/read-repair
   → Eventually consistent

5. Client reads (R=2):
   → Asks Node-A: email = 'new@test.com' (latest) ✓
   → Asks Node-C: email = 'old@test.com' (stale) 
   → Take the most recent value (by timestamp/version)
   → Return: 'new@test.com' ✓

NOTE: W+R = 2+2 = 4 > N=3
→ Read set ALWAYS overlaps with write set
→ At least one node in read response has latest data
→ Strong consistency guaranteed
```

---

## Interview Questions and Answers

### Q1: Explain the CAP theorem. Give examples.

**Answer:**

CAP states that a distributed system can guarantee only 2 of 3 properties: Consistency, Availability, Partition tolerance.

Since network partitions are inevitable, the practical choice is:
- **CP** (consistent but may be unavailable during partition): PostgreSQL with synchronous replication, ZooKeeper, etcd. Use for: financial data, inventory counts.
- **AP** (available but may return stale data during partition): Cassandra, DynamoDB, Redis cluster. Use for: social feeds, caching, analytics.

**Important nuance:** CAP applies during network partitions. During normal operation, you can have all three. Most systems are "CP most of the time with AP for specific operations."

### Q2: What is the difference between strong consistency and eventual consistency?

**Answer:**

**Strong consistency**: After a write completes, all subsequent reads (from any node) return the latest value. Like a single-node database.
- Higher latency (wait for replication)
- Lower availability during failures
- Example: Bank balance

**Eventual consistency**: After a write, reads may temporarily return stale data. Given enough time without new writes, all nodes converge to the same value.
- Lower latency
- Higher availability
- Example: Social media like count

**In between**: Read-your-writes, causal consistency, session consistency — practical compromises.

### Q3: What is split brain and how do you prevent it?

**Answer:**

Split brain occurs when a network partition causes two nodes to both believe they are the primary, accepting conflicting writes.

Prevention:
1. **Quorum**: Require majority to be primary. Minority side steps down.
2. **Fencing/STONITH**: Physically shut down old primary before promoting new one.
3. **Lease-based**: Primary must periodically renew a time-limited lease. If it can't (network issue), it demotes itself. New primary waits for lease expiry.

In practice (Patroni + etcd): Primary maintains a key in etcd. If it loses connection to etcd, it steps down. New primary only promotes after etcd confirms old leader's key expired.

### Q4: When would you use 2PC vs Saga?

**Answer:**

**2PC** (Two-Phase Commit):
- Within a single system or tightly coupled databases
- When strong consistency is absolutely required
- When operations are fast (lock duration is short)
- Example: Banking system transferring between two accounts in same DB cluster

**Saga**:
- Across microservices with separate databases
- When availability is more important than immediate consistency
- When operations involve external systems (payment gateway)
- When lock duration would be long (network calls)
- Example: E-commerce order flow (order → payment → inventory → shipping)

### Q5: Explain quorum-based replication. Why W + R > N?

**Answer:**

In quorum replication:
- N = total replicas
- W = nodes that must ACK a write
- R = nodes that must respond to a read

**W + R > N** ensures that read set and write set always overlap by at least one node. That overlapping node has the latest data, so reads always find the most recent write.

Example (N=5, W=3, R=3):
- Write goes to 3 of 5 nodes
- Read queries 3 of 5 nodes
- At least 1 node appears in both sets (pigeonhole principle)
- That node has the latest value

Tuning: Increase W for consistency, decrease for write speed. Increase R for consistency, decrease for read speed.

---

## Follow-up Questions and Answers

### Q: How does PostgreSQL achieve high availability without distributed consensus?

**Answer:**

PostgreSQL itself doesn't have built-in consensus. It relies on external tools:

1. **Patroni** (most popular): Uses etcd/ZooKeeper for consensus. Manages failover, promotion, and replication setup.
2. **pg_auto_failover**: Built by Citus. Uses a monitor node.
3. **Manual failover**: DBA promotes replica manually.

Architecture:
- Primary + 1-2 synchronous standbys
- Patroni agents on each node communicate via etcd
- If primary fails, Patroni promotes most up-to-date standby
- Applications connect via proxy (PgBouncer/HAProxy) that tracks the current primary

### Q: What is the difference between synchronous and asynchronous replication in the context of CAP?

**Answer:**

- **Synchronous**: Write waits for replica ACK. CP system — consistent but reduced availability (if replica is down, writes block). RPO = 0.
- **Asynchronous**: Write returns immediately, replica catches up later. AP system — available but may lose data on primary crash. RPO > 0.

Most production systems use a hybrid:
- 1 synchronous replica (ensures no data loss)
- 1-2 async replicas (for read scaling)

---

## Common Mistakes

| Mistake | Impact | Fix |
|---|---|---|
| Treating CAP as binary choice | Oversimplification | Understand it's a spectrum |
| Using 2PC across microservices | Availability disaster | Use Saga pattern |
| No quorum for leader election | Split brain | Use odd number of nodes + consensus |
| Ignoring network partitions | Data loss/corruption | Design for partition tolerance |
| Expecting strong consistency from AP system | Stale data bugs | Design for eventual consistency |
| Single-node consensus store | SPOF for entire cluster | 3+ node consensus cluster |

---

## Best Practices

1. **Accept network partitions WILL happen** — design for them
2. **Use quorum-based systems** (3 or 5 nodes) for coordination
3. **Prefer Saga over 2PC** for microservices
4. **Design for eventual consistency** where possible (simpler, more available)
5. **Use strong consistency** only where required (financial, inventory)
6. **Implement fencing** to prevent split brain
7. **Monitor replication lag** — it's your consistency gap indicator
8. **Test failure scenarios** — simulate network partitions regularly
9. **Choose the right consistency per operation** (not per system)
10. **Document consistency guarantees** for each API endpoint

---

## Production Considerations

### Choosing the Right Architecture

```
┌──────────────────────────┬───────────────────────────────────┐
│ Requirement              │ Architecture                       │
├──────────────────────────┼───────────────────────────────────┤
│ Strong consistency +     │ Single PostgreSQL with sync        │
│ moderate scale           │ replication (Patroni)              │
├──────────────────────────┼───────────────────────────────────┤
│ High read scale +        │ PostgreSQL + async read replicas   │
│ eventual consistency ok  │ + Redis caching                    │
├──────────────────────────┼───────────────────────────────────┤
│ High write scale +       │ Sharded PostgreSQL (Citus)         │
│ strong consistency       │ or CockroachDB                     │
├──────────────────────────┼───────────────────────────────────┤
│ Massive write scale +    │ Cassandra or ScyllaDB              │
│ eventual consistency ok  │                                    │
├──────────────────────────┼───────────────────────────────────┤
│ Global distribution +    │ CockroachDB, Spanner,              │
│ strong consistency       │ YugabyteDB                         │
└──────────────────────────┴───────────────────────────────────┘
```

---

## Related Topics

- Topic 18: Partitioning, Replication, Sharding
- Topic 21: Locking, Concurrency & MVCC
- Topic 26: Database Architecture & System Design
- Topic 28: SQL & Microservices (Saga, Outbox)
