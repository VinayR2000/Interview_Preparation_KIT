# Database Scaling

## Vertical Scaling

### Theory
- Add more resources (CPU, RAM, SSD) to existing database server
- Simplest scaling approach — no code changes needed

| Pros | Cons |
|------|------|
| Simple (no code changes) | Hardware limits (can't scale infinitely) |
| No distributed complexity | Expensive at high end |
| ACID guarantees maintained | Single point of failure |
| Strong consistency | Downtime for upgrade |

### When to Use
- Database is not yet at hardware limits
- Simplicity is a priority
- Data model doesn't partition well
- Workload requires strong consistency

---

## Read Replicas

### Theory
- Create copies of the primary database for handling read queries
- Writes go to primary, reads distributed across replicas
- Replication can be synchronous or asynchronous

### Diagram
```
                    Writes
┌────────┐      ┌──────────────┐
│ Client │─────→│   Primary    │
└────────┘      │   (Master)   │
                └──────┬───────┘
                       │ Replication
            ┌──────────┼──────────┐
            ▼          ▼          ▼
      ┌──────────┐ ┌──────────┐ ┌──────────┐
      │ Replica 1│ │ Replica 2│ │ Replica 3│
      └──────────┘ └──────────┘ └──────────┘
            ▲          ▲          ▲
            └──────────┼──────────┘
                       │ Reads
                ┌──────┴───────┐
                │   Clients    │
                └──────────────┘
```

### Synchronous vs Asynchronous Replication

| Aspect | Synchronous | Asynchronous |
|--------|-------------|--------------|
| Write confirmed when | All replicas acknowledge | Primary writes to WAL |
| Consistency | Strong (replicas always up-to-date) | Eventual (replication lag) |
| Write latency | Higher (wait for replicas) | Lower (don't wait) |
| Data loss risk | Zero (on replica failure) | Some (lag window) |
| Use case | Financial systems | Social media, analytics |

### Replication Lag
- Time delay between write on primary and visibility on replica
- Can be milliseconds to seconds (or more under load)
- Problem: Read-after-write inconsistency

### Solving Read-After-Write Consistency
1. **Read from primary** for recently-written data
2. **Monotonic reads**: Route user to same replica
3. **Version tracking**: Client tracks last write timestamp, replica serves only if caught up
4. **Causal consistency**: Track dependencies between operations

---

## Database Partitioning

### Horizontal Partitioning (Sharding)
- Split rows across multiple databases
- Each shard holds a subset of data
- Application routes queries to correct shard

### Vertical Partitioning
- Split columns across multiple databases
- Group frequently-accessed columns together
- Reduce row size for faster scans

### Functional Partitioning
- Split by feature/domain
- Users in one DB, Orders in another, Products in third
- Natural fit for microservices

```
Horizontal (Sharding):
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Shard 1    │  │  Shard 2    │  │  Shard 3    │
│ Users A-H   │  │ Users I-P   │  │ Users Q-Z   │
│ (same schema)│  │ (same schema)│  │ (same schema)│
└─────────────┘  └─────────────┘  └─────────────┘

Vertical:
┌──────────────┐  ┌───────────────────┐
│ User Core    │  │ User Extended     │
│ id, name,    │  │ id, bio, avatar,  │
│ email, pwd   │  │ preferences       │
└──────────────┘  └───────────────────┘
```

---

## Sharding

### Sharding Strategies

| Strategy | Description | Pros | Cons |
|----------|-------------|------|------|
| Range-based | Key ranges (A-H, I-P, Q-Z) | Simple, range queries work | Uneven distribution (hot spots) |
| Hash-based | hash(key) % N | Even distribution | Range queries across shards |
| Directory-based | Lookup table maps key → shard | Flexible | Lookup table is bottleneck/SPOF |
| Geography-based | Region determines shard | Data locality | Cross-region queries hard |

### Hash-Based Sharding
```
shard_id = hash(user_id) % number_of_shards

user_id = 12345
hash(12345) = 7823
7823 % 3 = 1  → Route to Shard 1
```

### Problems with Sharding
| Problem | Description | Solution |
|---------|-------------|----------|
| Cross-shard queries | JOINs across shards are expensive | Denormalize, application-level joins |
| Rebalancing | Adding/removing shards moves lots of data | Consistent hashing |
| Hotspots | Some shards get more traffic | Better key design, consistent hashing |
| Transactions | ACID across shards is complex | Saga pattern, 2PC (expensive) |
| ID generation | Auto-increment doesn't work across shards | Snowflake IDs, UUIDs |

---

## Consistent Hashing

### Problem with Simple Hashing
```
hash(key) % N  where N = number of servers

If server added (N: 3→4):
  Most keys map to different servers → massive cache invalidation
```

### How Consistent Hashing Works
```
Hash Ring (0 to 2^32):

        0
    ╭───────╮
   /    N1    \
  │  ●         │
  │      N2    │
  │    ●       │
  │        N3  │
   \  ●      /
    ╰───────╯
     2^32

Keys: hash(key) → find next node clockwise on ring

Add node: Only keys between new node and predecessor move
Remove node: Only keys on removed node move to next node
```

### Virtual Nodes
- Physical nodes mapped to multiple points on ring
- Ensures even distribution
- More virtual nodes = more even distribution
- Typical: 100-200 virtual nodes per physical node

```
Physical Node A → Virtual: A1, A2, A3, A4, A5 (5 points on ring)
Physical Node B → Virtual: B1, B2, B3, B4, B5
```

### Benefits
| Aspect | Simple Hash | Consistent Hash |
|--------|-------------|-----------------|
| Add/remove node | ~100% keys move | ~K/N keys move |
| Distribution | Depends on hash quality | Even with virtual nodes |
| Complexity | Simple | More complex |
| Used by | Simple caches | Cassandra, DynamoDB, memcached |

---

## Database Indexing

### Theory
- Data structure that speeds up data retrieval
- Trade-off: Faster reads, slower writes (index must be updated)
- B-Tree index: Most common, good for range queries
- Hash index: Good for exact lookups

### B-Tree Index
```
                    [50]
                   /    \
            [20, 35]    [65, 80]
           /   |   \   /   |   \
        [10] [25] [40] [55] [70] [90]
         ↓    ↓    ↓    ↓    ↓    ↓
       Data  Data  Data Data  Data Data
```

### When to Index
| Index | Don't Index |
|-------|-------------|
| Frequently queried columns (WHERE) | Columns rarely in WHERE |
| JOIN columns (foreign keys) | Tables with very few rows |
| ORDER BY / GROUP BY columns | Columns with very low cardinality |
| High cardinality columns | Frequently updated columns |

### Index Types
| Type | Description | Use Case |
|------|-------------|----------|
| B-Tree | Balanced tree, sorted | Range queries, ordering |
| Hash | Hash table | Exact equality only |
| Composite | Multiple columns | Multi-column queries |
| Covering | Includes all needed columns | Avoid table lookup |
| Partial | Subset of rows | Specific conditions |

---

## Connection Pooling

### Theory
- Maintain pool of reusable database connections
- Avoid overhead of creating/destroying connections per request
- Connections are borrowed and returned to pool

### Diagram
```
┌───────────────────────────┐
│      Application          │
│  Thread1  Thread2  Thread3│
│    ↓        ↓       ↓    │
│  ┌──────────────────────┐│
│  │   Connection Pool    ││
│  │  [C1] [C2] [C3] [C4]││
│  └──────────┬───────────┘│
└─────────────┼─────────────┘
              │
    ┌─────────▼─────────┐
    │     Database       │
    └────────────────────┘
```

### Configuration Parameters
| Parameter | Description | Typical Value |
|-----------|-------------|---------------|
| minPoolSize | Minimum idle connections | 5-10 |
| maxPoolSize | Maximum connections | 20-50 |
| connectionTimeout | Wait time for connection | 30s |
| idleTimeout | Time before idle connection closed | 10 min |
| maxLifetime | Maximum connection age | 30 min |

### Pool Sizing Formula (from HikariCP)
```
connections = (core_count * 2) + effective_spindle_count

For SSD: connections ≈ core_count * 2 + 1
Example: 4 cores → 9 connections
```

---

## CQRS (Command Query Responsibility Segregation)

### Theory
- Separate the read model (Query) from write model (Command)
- Optimize each independently
- Write model: Normalized, transactional
- Read model: Denormalized, optimized for queries

### Diagram
```
┌─────────────┐         ┌──────────────────┐
│   Client    │         │   Write Model    │
│  (Commands) │────────→│   (Normalized)   │
└─────────────┘         │   PostgreSQL     │
                        └────────┬─────────┘
                                 │ Events/CDC
                                 ▼
┌─────────────┐         ┌──────────────────┐
│   Client    │         │   Read Model     │
│  (Queries)  │←────────│  (Denormalized)  │
└─────────────┘         │  Elasticsearch   │
                        └──────────────────┘
```

### When to Use CQRS
- Read and write workloads are very different
- Need different optimization for reads vs writes
- Read model benefits from denormalization
- Want to scale reads and writes independently

---

## NoSQL for Scaling

### MongoDB
| Aspect | Details |
|--------|---------|
| Type | Document store (JSON/BSON) |
| Scaling | Built-in sharding |
| Schema | Flexible (schema-less) |
| Use cases | Content management, catalogs, user profiles |
| Consistency | Configurable (read/write concern) |

### DynamoDB
| Aspect | Details |
|--------|---------|
| Type | Key-value + document |
| Scaling | Automatic, virtually unlimited |
| Consistency | Eventually consistent (default), strongly consistent (option) |
| Pricing | Pay per request or provisioned capacity |
| Use cases | Gaming, IoT, session management, high-scale apps |

### When SQL vs NoSQL

| Choose SQL When | Choose NoSQL When |
|-----------------|-------------------|
| Complex queries/JOINs needed | Simple key-value access |
| ACID transactions critical | Scale > consistency |
| Data is highly relational | Schema evolves frequently |
| Data integrity is paramount | Write-heavy workloads |
| Reporting and analytics | Geographic distribution |

---

## Interview Questions

**Q: How would you scale a database handling 100K writes/sec?**
> 1. Vertical scaling first (bigger machine, SSDs)
> 2. Write-ahead log + async replication for durability
> 3. Shard the database (hash on write key)
> 4. Use connection pooling (HikariCP)
> 5. Batch writes where possible
> 6. Consider NoSQL if schema allows (DynamoDB, Cassandra)
> 7. Use write-back caching for burst absorption

**Q: How do you handle cross-shard queries?**
> 1. Denormalize: Duplicate data to avoid cross-shard reads
> 2. Application-level joins: Query multiple shards, merge in app
> 3. Use scatter-gather pattern: Broadcast query to all shards, aggregate results
> 4. CQRS: Materialized views that span shards for read queries
> 5. Design shard key to keep related data together

**Q: When would you choose consistent hashing over range-based sharding?**
> Consistent hashing when: nodes frequently added/removed, want even distribution, key-value access pattern.
> Range-based when: need range queries on shard key, data has natural ordering, can tolerate potential hotspots (with monitoring).

**Q: How do you handle the "read-after-write" problem with replicas?**
> 1. Read your own writes from primary (within time window)
> 2. Track write timestamp, only read from replica if caught up
> 3. Route user to same replica (sticky routing)
> 4. Use synchronous replication (sacrifices write performance)
> 5. Implement causal consistency (track causality tokens)

**Q: How do you decide between adding an index and denormalizing?**
> Add index: Query pattern is well-defined, table structure supports it, write overhead acceptable.
> Denormalize: Queries are very complex multi-join, read performance critical, can tolerate data redundancy and update complexity. Often use both — index for simple queries, denormalize for complex read-heavy queries.

---

## Common Mistakes
- Sharding too early (adds massive complexity)
- Poor shard key choice leading to hotspots
- Not considering replication lag in application logic
- Too many indexes (slow writes, wasted storage)
- Connection pool too large (overwhelms DB) or too small (bottleneck)
- Not monitoring slow queries and index usage
- Ignoring VACUUM/maintenance on PostgreSQL

---

## Best Practices
- Start simple: vertical scaling → read replicas → sharding
- Choose shard key carefully (high cardinality, even distribution, query-aligned)
- Use connection pooling always (HikariCP, PgBouncer)
- Monitor: slow queries, index usage, replication lag, connection count
- Index based on actual query patterns (use EXPLAIN)
- Plan for schema migration strategy with sharding
- Keep frequently-joined data on same shard

---

## Production Considerations
- Automated failover (RDS Multi-AZ, Patroni for PostgreSQL)
- Backup strategy: Full + incremental, point-in-time recovery
- Connection pooling: Pool per service, not per instance
- Monitor replication lag with alerting
- Capacity planning: Growth rate × 6-12 months
- Schema migrations with zero downtime (expand-contract pattern)
- Regular maintenance: Index rebuild, statistics update, vacuum

---

## Related Topics
- Consistent Hashing
- CQRS
- Event Sourcing
- CAP Theorem
- Distributed Transactions (Saga pattern)
