# Sharding ⭐⭐⭐

## What is Sharding?

Sharding is MongoDB's approach to horizontal scaling — distributing data across multiple machines to handle larger datasets and higher throughput than a single server can provide.

```
         Application
              ↓
         ┌─────────┐
         │  mongos  │  ← Query router
         │ (router) │
         └────┬────┘
              ↓
    ┌─────────┼─────────┐
    ↓         ↓         ↓
┌────────┐ ┌────────┐ ┌────────┐
│Config  │ │Config  │ │Config  │  ← Cluster metadata
│Server 1│ │Server 2│ │Server 3│     (replica set)
└────────┘ └────────┘ └────────┘

    ┌─────────┼─────────┐
    ↓         ↓         ↓
┌────────┐ ┌────────┐ ┌────────┐
│Shard 1 │ │Shard 2 │ │Shard 3 │  ← Each shard is a
│(Replica│ │(Replica│ │(Replica│     replica set
│  Set)  │ │  Set)  │ │  Set)  │
└────────┘ └────────┘ └────────┘
```

---

## Sharded Cluster Components

### mongos (Query Router)
- Routes client requests to appropriate shards
- Multiple mongos instances for high availability
- Stateless — can add/remove without data loss
- Merges results from multiple shards for client
- Clients connect to mongos (not directly to shards)

### Config Servers
- Store cluster metadata and configuration
- Track which chunks live on which shard
- Deployed as a 3-member replica set
- Must be available for cluster to function
- Store: shard list, chunk ranges, balancer state

### Shards
- Each shard holds a subset of the total data
- Each shard is a replica set (for high availability)
- Can be added dynamically to scale out
- Data distributed based on shard key

---

## Shard Key

The shard key determines how documents are distributed across shards. **This is the most critical decision in sharding.**

```javascript
// Shard a collection
sh.shardCollection("mydb.orders", { customerId: 1 })
//                                  ^^^^^^^^^^^^^^^^^^
//                                  This is the shard key
```

### How Data is Distributed

```
Shard Key: customerId

Shard 1: customerId A-F
  ├── Alice's orders
  ├── Bob's orders
  └── Charlie's orders

Shard 2: customerId G-M
  ├── Grace's orders
  ├── Henry's orders
  └── Julia's orders

Shard 3: customerId N-Z
  ├── Nancy's orders
  ├── Robert's orders
  └── Vinay's orders
```

---

## Chunks

MongoDB divides data into **chunks** based on shard key ranges.

```
Collection: orders (sharded on customerId)

Chunk 1: { customerId: MinKey } → { customerId: "G" }  → Shard 1
Chunk 2: { customerId: "G" }    → { customerId: "N" }  → Shard 2
Chunk 3: { customerId: "N" }    → { customerId: MaxKey } → Shard 3
```

- Default chunk size: **128 MB** (configurable: 1-1024 MB)
- When a chunk exceeds max size → it splits into two chunks
- **Balancer** moves chunks between shards to maintain even distribution

---

## Sharding Strategies

### Range-Based Sharding

Documents distributed based on shard key value ranges.

```javascript
sh.shardCollection("mydb.orders", { orderDate: 1 })
```

```
Shard 1: Jan 2024 — Mar 2024
Shard 2: Apr 2024 — Jun 2024
Shard 3: Jul 2024 — Sep 2024
```

**Pros**: Efficient range queries on shard key
**Cons**: Can create hot spots (recent data goes to one shard)

### Hashed Sharding

Shard key values are hashed for distribution.

```javascript
sh.shardCollection("mydb.users", { userId: "hashed" })
```

```
Hash(userId) determines shard:
  hash("user1") % 3 = 0 → Shard 1
  hash("user2") % 3 = 2 → Shard 3
  hash("user3") % 3 = 1 → Shard 2
```

**Pros**: Even distribution, no hot spots
**Cons**: Range queries on shard key require scatter-gather (all shards)

### Comparison

| Aspect | Range | Hashed |
|--------|-------|--------|
| Distribution | Based on value ranges | Even (hashed) |
| Range queries on shard key | Targeted (efficient) | Scatter-gather (all shards) |
| Hot spots | Possible (monotonic keys) | Unlikely |
| Equality queries | Targeted | Targeted |
| Sort on shard key | Efficient | Not efficient |

---

## Choosing a Good Shard Key ⭐⭐⭐

**The most important question in sharding.**

### Properties of a Good Shard Key

| Property | Why | Example |
|----------|-----|---------|
| High Cardinality | Many distinct values = many possible chunks | ✅ userId, orderId ❌ status, country |
| Even Distribution | Avoids hot shards | ✅ hashed userId ❌ timestamp (monotonic) |
| Query Isolation | Queries target specific shards | ✅ customerId (most queries filter by customer) |
| Non-Monotonic | Avoids all writes going to one shard | ✅ userId ❌ auto-increment ID, timestamp |

### Shard Key Examples

```javascript
// ❌ BAD: Monotonically increasing
sh.shardCollection("mydb.orders", { createdAt: 1 })
// All new orders go to the LAST shard (hot shard)
// Other shards sit idle

// ❌ BAD: Low cardinality
sh.shardCollection("mydb.orders", { status: 1 })
// Only 4-5 possible values → cannot split chunks finely enough

// ✅ GOOD: High cardinality, queries filter by this field
sh.shardCollection("mydb.orders", { customerId: 1 })
// Even distribution, queries by customer are targeted

// ✅ GOOD: Compound shard key for better distribution
sh.shardCollection("mydb.orders", { customerId: 1, orderDate: -1 })
// Good cardinality + supports range queries within a customer

// ✅ GOOD: Hashed for write-heavy uniform distribution
sh.shardCollection("mydb.events", { eventId: "hashed" })
// Perfect distribution, but scatter-gather for range queries
```

---

## Hot Shards

A hot shard receives disproportionate traffic (reads or writes).

### Causes
1. **Monotonic shard key** (timestamp, auto-increment) → all writes to last chunk
2. **Low cardinality** → uneven chunk distribution
3. **Popular key values** → one customer generates 90% of traffic

### Detection
```javascript
// Check chunk distribution
sh.status()

// Monitor per-shard operations
db.adminCommand({ serverStatus: 1 }).opcounters
```

### Solutions
1. Choose better shard key (hashed for writes, compound for targeted reads)
2. Pre-split chunks before loading data
3. Use zones to control data placement

---

## Query Routing

### Targeted Query (Best Performance)
Query includes the shard key → mongos routes to specific shard(s).

```javascript
// Shard key: { customerId: 1 }
db.orders.find({ customerId: "C1" })
// → Routed to ONE shard (where C1's data lives)
```

### Scatter-Gather Query (Worst Performance)
Query does NOT include shard key → mongos must query ALL shards.

```javascript
// Shard key: { customerId: 1 }
db.orders.find({ status: "active" })
// → Sent to ALL shards, results merged by mongos

db.orders.find({ orderDate: { $gt: new Date("2024-01-01") } })
// → ALL shards queried (orderDate is not the shard key)
```

### Broadcast Operation
Certain operations always go to all shards:
- Queries without shard key
- `count()` without shard key
- Multi-update without shard key
- Aggregations without initial `$match` on shard key

---

## Balancer

The balancer automatically moves chunks between shards to maintain even distribution.

```
Before balancing:
  Shard 1: 100 chunks
  Shard 2: 50 chunks
  Shard 3: 30 chunks

After balancing:
  Shard 1: 60 chunks
  Shard 2: 60 chunks
  Shard 3: 60 chunks
```

### Balancer Behavior
- Runs in the background on config servers
- Moves one chunk at a time (by default)
- Trigger: imbalance > 2 chunks between any two shards
- Can be scheduled (run during off-peak hours)

```javascript
// Check balancer status
sh.getBalancerState()
sh.isBalancerRunning()

// Schedule balancer window
db.settings.updateOne(
  { _id: "balancer" },
  { $set: { activeWindow: { start: "02:00", stop: "06:00" } } }
)
```

---

## Zones (Tag-Aware Sharding)

Control which shards store which data ranges.

```javascript
// Assign zones to shards
sh.addShardTag("shard1", "US")
sh.addShardTag("shard2", "EU")
sh.addShardTag("shard3", "APAC")

// Define zone ranges
sh.addTagRange("mydb.users", 
  { region: "US", userId: MinKey }, 
  { region: "US", userId: MaxKey }, 
  "US"
)
sh.addTagRange("mydb.users",
  { region: "EU", userId: MinKey },
  { region: "EU", userId: MaxKey },
  "EU"
)
```

**Use Cases**: Data sovereignty, geo-proximity, tiered storage

---

## Sharding Limitations

1. **Shard key is immutable** (pre-5.0) — choose carefully!
   - MongoDB 5.0+: Can reshard (but expensive operation)
2. **Unique indexes** must include the shard key as a prefix
3. **Transactions** across shards have higher latency
4. **$lookup** has limitations with sharded collections
5. **Collection must have shard key index** before sharding
6. **Cannot unshard** a collection (must dump and restore)

---

## Sharding Commands

```javascript
// Enable sharding on database
sh.enableSharding("mydb")

// Shard a collection
sh.shardCollection("mydb.orders", { customerId: 1 })
sh.shardCollection("mydb.events", { eventId: "hashed" })

// Check sharding status
sh.status()

// Check chunk distribution for a collection
db.orders.getShardDistribution()

// Add a shard
sh.addShard("shard4RS/host4a:27017,host4b:27017,host4c:27017")

// Remove a shard (drains chunks first)
db.adminCommand({ removeShard: "shard4RS" })
```

---

## When to Shard

### You Likely Need Sharding When:
- Single server can't handle the write load
- Dataset exceeds single server's storage capacity
- Working set exceeds available RAM
- Read throughput needs exceed what a replica set provides

### You Probably DON'T Need Sharding When:
- Dataset fits on a single server (< 1-2 TB)
- Write load is manageable with a single primary
- You haven't optimized queries/indexes yet
- Application can use read replicas for read scaling

**Rule**: Shard only when vertical scaling is no longer sufficient. Sharding adds operational complexity.

---

## Interview Questions

**Q: How do you choose a good shard key?**
A: A good shard key has: (1) high cardinality (many distinct values), (2) even distribution (no hot spots), (3) matches your query patterns (targeted queries), (4) is not monotonically increasing (avoids hot shard). The ideal key is one that your most frequent queries include in their filter, has many possible values, and distributes writes evenly.

**Q: What happens when a query doesn't include the shard key?**
A: The mongos router must send the query to ALL shards (scatter-gather), wait for responses from each, and merge results. This is significantly slower than a targeted query. Design your schema and queries so that frequent queries include the shard key.

**Q: Can you change the shard key after sharding?**
A: Before MongoDB 5.0: No, the shard key was immutable. Since 5.0: Yes, you can reshard a collection, but it's a heavy operation that rewrites all data. Choose your shard key carefully to avoid needing this.

**Q: What's the difference between range and hashed sharding?**
A: Range sharding preserves key ordering (efficient range queries on shard key) but can create hot spots with monotonic keys. Hashed sharding ensures even distribution (no hot spots) but all range queries become scatter-gather operations.

**Q: A customer generates 80% of your traffic. How does this affect sharding?**
A: If the shard key is `customerId`, all that customer's data lives on ONE shard, creating a hot shard. Solutions: (1) use a compound shard key `{ customerId: 1, orderId: 1 }` to spread that customer's data across chunks, (2) use hashed sharding, or (3) handle this at the application level with dedicated resources.

**Q: What's the role of config servers in a sharded cluster?**
A: Config servers store all cluster metadata: which shards exist, how collections are sharded, chunk locations, and balancer state. They're deployed as a replica set for high availability. Without config servers, the cluster cannot route queries correctly.
