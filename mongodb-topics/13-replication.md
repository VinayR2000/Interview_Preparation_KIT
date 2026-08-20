# Replication ⭐⭐⭐

## What is Replication?

Replication provides redundancy and high availability by maintaining multiple copies of data across different servers.

```
┌──────────────────────────────────────────────┐
│              Replica Set                       │
│                                              │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐│
│  │ PRIMARY  │   │SECONDARY │   │SECONDARY ││
│  │          │──▶│          │──▶│          ││
│  │ (Reads & │   │ (Reads   │   │ (Reads   ││
│  │  Writes) │   │  only)   │   │  only)   ││
│  └──────────┘   └──────────┘   └──────────┘│
│       │              │              │        │
│       ▼              ▼              ▼        │
│    Data Copy      Data Copy      Data Copy   │
└──────────────────────────────────────────────┘
```

---

## Replica Set

A replica set is a group of mongod instances that maintain the same data set.

### Components
- **Primary**: Receives ALL write operations. Only one primary per replica set.
- **Secondary**: Replicates data from primary. Can serve read operations (if configured).
- **Arbiter**: Participates in elections but holds NO data. Used for tie-breaking votes.

### Minimum Configuration
```
Production: 3 members (1 Primary + 2 Secondaries)
  - Provides fault tolerance (survives loss of 1 member)
  - Allows majority write concern
  
Common configurations:
  - P-S-S (Primary + 2 Secondaries) → Most common
  - P-S-A (Primary + Secondary + Arbiter) → Budget option (less redundancy)
```

---

## How Replication Works

### Oplog (Operations Log)
The primary records all write operations in a capped collection called the **oplog** (`local.oplog.rs`).

```
Primary receives write
       ↓
Write applied to data
       ↓
Operation recorded in oplog
       ↓
Secondaries tail the oplog
       ↓
Secondaries apply operations
```

### Oplog Entry Example
```javascript
{
  "ts": Timestamp(1718450000, 1),
  "op": "u",                    // Operation: i(insert), u(update), d(delete)
  "ns": "mydb.orders",          // Namespace
  "o": { "$set": { "status": "shipped" } },  // Operation details
  "o2": { "_id": ObjectId("...") }            // Document identifier
}
```

### Replication Lag
- Time difference between primary and secondary
- Secondaries may be slightly behind primary
- Monitor with `rs.printReplicationInfo()` and `rs.printSecondaryReplicationInfo()`

---

## Primary Election

When the primary becomes unavailable, an **automatic election** occurs.

### Election Triggers
1. Primary goes down (crash, network failure)
2. Primary steps down (manual or due to priority)
3. Primary becomes unreachable by majority of members
4. Configuration change (new member added, priority changed)

### Election Process
```
Primary goes down
       ↓
Secondaries detect (heartbeat timeout: 10 seconds)
       ↓
Eligible secondary calls election
       ↓
Members vote (majority needed)
       ↓
Winner becomes new Primary
       ↓
Clients redirect to new Primary (typically < 12 seconds)
```

### Election Rules
- **Majority vote required**: In a 3-member set, need 2 votes to win
- **Priority**: Higher priority members are preferred (default: 1)
- **Most up-to-date**: Member with latest oplog entry is preferred
- **Cannot elect**: Members with priority 0, hidden members, or delayed members

### Priority Configuration
```javascript
// Set member priorities
rs.reconfig({
  members: [
    { _id: 0, host: "mongo1:27017", priority: 2 },  // Preferred primary
    { _id: 1, host: "mongo2:27017", priority: 1 },  // Can become primary
    { _id: 2, host: "mongo3:27017", priority: 0 }   // Never becomes primary
  ]
})
```

---

## Failover

### What Happens When Primary Goes Down?

```
Timeline:
0s     - Primary crashes
~2s    - Secondaries notice missing heartbeat
~10s   - Heartbeat timeout reached
~10-12s - Election begins
~12-15s - New primary elected
~15s   - Clients reconnect to new primary

During this window (~10-15 seconds):
  - Writes FAIL (no primary)
  - Reads from secondaries continue (if readPreference allows)
  - Clients should implement retry logic
```

### Application Impact
```java
// Spring Boot — MongoClient handles failover automatically
// Connection string with all replica set members:
spring.data.mongodb.uri=mongodb://host1:27017,host2:27017,host3:27017/mydb?replicaSet=myRS

// The driver:
// 1. Discovers all members via replica set topology
// 2. Monitors member status
// 3. Automatically routes to new primary after election
// 4. Retries retryable writes (since MongoDB 3.6)
```

### Retryable Writes
```
Client sends write → Primary crashes → Client retries → New primary handles write

Retryable operations:
  - insertOne, updateOne, deleteOne
  - findOneAndUpdate, findOneAndDelete
  
NOT retryable:
  - updateMany, deleteMany (could partially complete)
  - insertMany with ordered: true
```

---

## Read Preference

Controls which members receive read operations.

```
┌─────────────────────────────────────────────────┐
│                                                  │
│   primary        → All reads go to primary       │
│                    (strongest consistency)        │
│                                                  │
│   primaryPreferred → Primary; secondary if       │
│                      primary unavailable         │
│                                                  │
│   secondary      → Only secondaries              │
│                    (offload reads from primary)   │
│                                                  │
│   secondaryPreferred → Secondaries; primary if   │
│                        no secondary available    │
│                                                  │
│   nearest        → Lowest network latency        │
│                    (geo-distributed reads)        │
│                                                  │
└─────────────────────────────────────────────────┘
```

### Configuration in Spring Boot
```yaml
# Connection URI
spring:
  data:
    mongodb:
      uri: mongodb://host1,host2,host3/mydb?replicaSet=rs0&readPreference=secondaryPreferred
```

```java
// Per-query read preference with MongoTemplate
mongoTemplate.setReadPreference(ReadPreference.secondaryPreferred());

// Or per operation
Query query = new Query();
query.withReadPreference(ReadPreference.secondary());
```

### ⚠️ Stale Reads Warning
Reading from secondaries may return stale data due to replication lag. Only use secondary reads when:
- Slight staleness is acceptable (analytics, reporting)
- You need to offload read traffic from primary
- Geo-distributed reads (nearest) for latency benefits

---

## Write Concern in Replica Sets

```javascript
// Write acknowledged by primary only (default)
{ w: 1 }

// Write replicated to majority of members
{ w: "majority" }
// In 3-member set: primary + 1 secondary must acknowledge

// Write replicated to all members
{ w: 3 }  // For 3-member set

// With journal acknowledgment
{ w: "majority", j: true }
// Data written to journal on majority of members → survives any crash
```

### Trade-offs
```
w: 1             → Fast, risk of data loss if primary crashes before replication
w: "majority"    → Slower, data survives primary failure
w: "majority", j: true → Slowest, data survives any single member crash
```

---

## Special Replica Set Members

### Hidden Members
- Not visible to client applications
- Never become primary (priority: 0)
- Do NOT receive read operations from applications
- Use cases: dedicated backup, reporting, analytics

```javascript
{ _id: 2, host: "mongo3:27017", priority: 0, hidden: true }
```

### Delayed Members
- Replicate data with a configured delay
- Acts as a "time machine" for recovery from human errors
- Priority: 0, hidden: true

```javascript
{ _id: 2, host: "mongo3:27017", priority: 0, hidden: true, secondaryDelaySecs: 3600 }
// 1 hour delayed — can recover from accidental drops within 1 hour
```

### Arbiters
- Vote in elections but hold NO data
- Lightweight (no storage needed)
- Use when you need an odd number of votes but can't afford a full member
- ⚠️ Generally discouraged in production — prefer full data members

---

## Replica Set Commands

```javascript
// Initialize replica set
rs.initiate({
  _id: "myRS",
  members: [
    { _id: 0, host: "mongo1:27017" },
    { _id: 1, host: "mongo2:27017" },
    { _id: 2, host: "mongo3:27017" }
  ]
})

// Check status
rs.status()

// Check replication info
rs.printReplicationInfo()       // Oplog info on primary
rs.printSecondaryReplicationInfo()  // Lag info on secondaries

// Add member
rs.add("mongo4:27017")

// Remove member
rs.remove("mongo4:27017")

// Step down primary (force election)
rs.stepDown(60)  // Step down for 60 seconds

// Force reconfiguration
rs.reconfig(newConfig, { force: true })
```

---

## Replica Set Best Practices

1. **Odd number of members** (3, 5, 7) — avoids election ties
2. **Spread across availability zones** — survives AZ failure
3. **Use w: "majority"** for critical writes — survives primary failure
4. **Monitor replication lag** — alerts if secondary falls behind
5. **Size oplog appropriately** — must cover maintenance windows
6. **Test failover regularly** — verify application handles it
7. **Use retryable writes** — handles transient failures

---

## Interview Questions

**Q: What happens when the MongoDB primary goes down?**
A: The remaining members detect the failure (within ~10 seconds via heartbeat timeout), hold an election (majority vote needed), and elect a new primary. The whole process typically takes 10-15 seconds. During this window, writes fail but reads from secondaries can continue (with appropriate read preference). Applications should use retry logic.

**Q: Can a replica set function with only 2 members (1 primary + 1 secondary)?**
A: Technically yes, but if either goes down, the remaining member cannot form a majority (needs 2 out of 2 votes) and cannot elect itself as primary. The replica set becomes read-only. Always use 3+ members.

**Q: What's the difference between w:1 and w:majority?**
A: `w:1` means the primary acknowledged the write. If the primary crashes before replicating to secondaries, that write is LOST. `w:majority` means the write was replicated to a majority of members — it survives primary failure because at least one other member has the data.

**Q: How do you handle replication lag?**
A: Monitor lag with `rs.printSecondaryReplicationInfo()`. Causes include: high write volume, slow secondaries (undersized), network issues, or long-running operations on secondaries. Solutions: scale up secondary hardware, reduce write volume, use appropriate read concern.

**Q: When would you read from secondaries?**
A: For read-heavy analytics/reporting where slight staleness is acceptable, for geo-distributed applications (read from nearest), or to offload the primary during high-traffic periods. Never read from secondaries when you need the latest data for business logic.

**Q: What's an oplog and why does its size matter?**
A: The oplog is a capped collection that records all write operations. Its size determines how far back operations are retained. If a secondary goes offline longer than the oplog window, it cannot catch up via normal replication and needs a full resync. Size the oplog to cover your maintenance window + buffer.
