# Topic 18: Partitioning, Replication, and Sharding

## Partitioning

### Theory

Partitioning splits a large table into smaller physical pieces while appearing as a single logical table.

```
┌─────────────────────────────────────────────────────────────┐
│                    PARTITIONING TYPES                         │
├──────────────────┬────────────────────┬─────────────────────┤
│ Range Partition   │ List Partition      │ Hash Partition      │
├──────────────────┼────────────────────┼─────────────────────┤
│ By value range   │ By explicit values  │ By hash of column   │
│ dates, IDs       │ regions, statuses   │ even distribution   │
├──────────────────┼────────────────────┼─────────────────────┤
│ orders_2024_q1   │ orders_us          │ orders_part_0       │
│ orders_2024_q2   │ orders_eu          │ orders_part_1       │
│ orders_2024_q3   │ orders_asia        │ orders_part_2       │
└──────────────────┴────────────────────┴─────────────────────┘
```

### Internal Working

```
Without Partitioning:
Query: WHERE created_at = '2024-03-15'
→ Scan entire 100GB table (all pages)

With Range Partitioning (by month):
Query: WHERE created_at = '2024-03-15'
→ PARTITION PRUNING: Only scan orders_2024_03 partition (~3GB)
→ 97% less I/O!
```

### Code Examples

```sql
-- Range Partitioning (most common for time-series)
CREATE TABLE orders (
    id BIGSERIAL,
    customer_id BIGINT NOT NULL,
    total_amount NUMERIC(10, 2),
    status VARCHAR(20),
    created_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (created_at);

-- Create partitions
CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');
CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');
CREATE TABLE orders_2024_q3 PARTITION OF orders
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');
CREATE TABLE orders_2024_q4 PARTITION OF orders
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');

-- Indexes are per-partition
CREATE INDEX idx_orders_customer ON orders (customer_id);
-- Actually creates: idx_orders_2024_q1_customer, idx_orders_2024_q2_customer, etc.

-- List Partitioning
CREATE TABLE orders_by_region (
    id BIGSERIAL,
    region VARCHAR(10),
    total NUMERIC
) PARTITION BY LIST (region);

CREATE TABLE orders_us PARTITION OF orders_by_region FOR VALUES IN ('US', 'CA');
CREATE TABLE orders_eu PARTITION OF orders_by_region FOR VALUES IN ('UK', 'DE', 'FR');
CREATE TABLE orders_asia PARTITION OF orders_by_region FOR VALUES IN ('JP', 'KR', 'IN');

-- Hash Partitioning (even distribution)
CREATE TABLE sessions (
    id UUID,
    user_id BIGINT,
    data JSONB
) PARTITION BY HASH (user_id);

CREATE TABLE sessions_0 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE sessions_1 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE sessions_2 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE sessions_3 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 3);

-- Partition maintenance (drop old data instantly)
DROP TABLE orders_2023_q1;  -- Much faster than DELETE!
-- Or detach:
ALTER TABLE orders DETACH PARTITION orders_2023_q1;
```

### Partition Pruning

```sql
-- Query automatically targets only relevant partition
EXPLAIN SELECT * FROM orders WHERE created_at = '2024-03-15';
-- Shows: Scan on orders_2024_q1 only (other partitions pruned)

-- Ensure partition key is in WHERE clause for pruning to work!
```

---

## Replication

### Theory

```
┌─────────────────────────────────────────────────────────────────┐
│                       REPLICATION                                 │
├────────────────────────────┬────────────────────────────────────┤
│ Synchronous                │ Asynchronous                        │
├────────────────────────────┼────────────────────────────────────┤
│ Primary waits for replica  │ Primary doesn't wait               │
│ Zero data loss             │ Slight lag (ms to seconds)         │
│ Higher latency             │ Lower latency                      │
│ Used for: HA with no loss  │ Used for: Read scaling             │
└────────────────────────────┴────────────────────────────────────┘

Architecture:
┌──────────┐      WAL Stream      ┌──────────────┐
│  PRIMARY │ ──────────────────── │  REPLICA 1   │ (read-only)
│  (R/W)   │ ──────────┐         └──────────────┘
└──────────┘           │
                       │         ┌──────────────┐
                       └──────── │  REPLICA 2   │ (read-only)
                                 └──────────────┘
```

### Read/Write Splitting

```java
// Spring Boot with read/write routing
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    public DataSource routingDataSource() {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("primary", primaryDataSource());
        targetDataSources.put("replica", replicaDataSource());
        
        RoutingDataSource routing = new RoutingDataSource();
        routing.setTargetDataSources(targetDataSources);
        routing.setDefaultTargetDataSource(primaryDataSource());
        return routing;
    }
}

// Route based on @Transactional(readOnly)
@Transactional(readOnly = true)  // → Routes to replica
public List<Product> findAll() { ... }

@Transactional  // → Routes to primary
public Product save(Product p) { ... }
```

### Replication Lag Handling

```sql
-- Check replication lag
SELECT 
    client_addr,
    state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    NOW() - pg_last_xact_replay_timestamp() AS replay_lag
FROM pg_stat_replication;

-- In application: Use primary for reads after writes
-- "Read your own writes" pattern:
-- After INSERT/UPDATE → query primary for next N seconds
-- After timeout → safe to read from replica
```

---

## Sharding

### Theory

Sharding distributes data across multiple **independent** database instances.

```
┌─────────────────────────────────────────────────────────────────┐
│                      APPLICATION                                  │
│                    (Shard Router)                                 │
├──────────────────┬──────────────────┬───────────────────────────┤
│    Shard 1       │    Shard 2       │    Shard 3                │
│ customers A-H    │ customers I-P    │ customers Q-Z             │
│ (independent DB) │ (independent DB) │ (independent DB)          │
└──────────────────┴──────────────────┴───────────────────────────┘
```

### Shard Key Selection

| Strategy | How | Pros | Cons |
|----------|-----|------|------|
| Hash | hash(key) % N | Even distribution | Range queries need all shards |
| Range | value ranges | Range queries efficient | Hot spots possible |
| Geo | by region | Data locality | Uneven distribution |
| Tenant | by customer/org | Isolation, easy migration | Variable shard sizes |

### Challenges

```
Cross-Shard Query:
SELECT * FROM orders WHERE customer_id = 42 AND product_id = 99;
- customer_id is shard key → goes to specific shard
- But what if product_id data is on different shard?
- Need to query multiple shards and merge results!

Cross-Shard Transaction:
Transfer money between customers on different shards
- Requires distributed transaction (2PC) or Saga pattern
- Much more complex than single-shard transaction

Hot Partition:
If one customer generates 80% of traffic → their shard is overloaded
- Solution: Re-shard, split hot shard, or use consistent hashing
```

---

## Distributed Database Concepts

### CAP Theorem

```
┌───────────────────────────────────────────────────────────────┐
│              CAP THEOREM: Pick 2 of 3                          │
├───────────────────────────────────────────────────────────────┤
│                                                                 │
│        Consistency ──── Availability                           │
│              \              /                                    │
│               \            /                                    │
│                \          /                                     │
│         Partition Tolerance                                     │
│                                                                 │
│  CP (Consistent + Partition Tolerant):                         │
│    → Sacrifices availability during network partition           │
│    → Examples: HBase, MongoDB (default), PostgreSQL            │
│                                                                 │
│  AP (Available + Partition Tolerant):                           │
│    → Sacrifices consistency (eventual consistency)              │
│    → Examples: Cassandra, DynamoDB, CouchDB                    │
│                                                                 │
│  CA (Consistent + Available):                                  │
│    → Only works with no network partitions (single node)       │
│    → Examples: Traditional single-node RDBMS                   │
│                                                                 │
│  In distributed systems, P is mandatory.                       │
│  Real choice: Consistency OR Availability during partitions    │
│                                                                 │
└───────────────────────────────────────────────────────────────┘
```

### Consistency Models

| Model | Guarantee | Example |
|-------|-----------|---------|
| Strong | Read always sees latest write | PostgreSQL, Spanner |
| Eventual | Reads eventually see latest write | DynamoDB, Cassandra |
| Causal | Causally related ops seen in order | MongoDB (sessions) |
| Read-your-writes | Writer sees own writes immediately | Most systems (with routing) |

---

## Interview Questions & Answers

**Q1: When would you partition vs shard?**

| Partitioning | Sharding |
|-------------|----------|
| Single database instance | Multiple database instances |
| Managed by DB engine | Managed by application/middleware |
| Transparent to queries | Requires routing logic |
| Scale reads (parallel scan) | Scale reads AND writes |
| Use when: single DB, need faster queries on large tables | Use when: single DB can't handle load |

**Q2: What are the drawbacks of sharding?**
1. Cross-shard queries are expensive
2. Cross-shard transactions are complex (2PC/Saga)
3. Re-sharding is painful (data migration)
4. Application complexity (routing, failover)
5. No cross-shard foreign keys or joins
6. Operational overhead (manage N databases)

**Q3: How do you handle replication lag in an application?**
1. "Read your own writes": After write, read from primary for short window
2. Monotonic reads: Sticky sessions to same replica
3. Lag-aware routing: Check lag, route to primary if lag > threshold
4. Causal consistency tokens: Include version/timestamp in read requests

**Q4: Explain eventual consistency with an example.**
User updates profile picture:
- Write goes to primary → returns success
- Replica hasn't received the change yet (lag = 200ms)
- User refreshes page → hits replica → sees OLD picture
- After 200ms, replica catches up → sees NEW picture
- Eventually consistent, but temporary stale read

---

## Common Mistakes

1. **Partitioning without partition key in queries** — no pruning, scans all partitions
2. **Too many partitions** — planning overhead for each partition
3. **Wrong shard key** — causes hot spots or too many cross-shard queries
4. **Not planning for rebalancing** — adding shards requires data migration
5. **Ignoring replication lag** — stale reads causing business logic errors

---

## Best Practices

1. **Partition by time** for time-series data (easy maintenance, natural pruning)
2. **Choose shard key based on query pattern** — minimize cross-shard operations
3. **Use async replication** for read scaling (sync for zero-loss requirements)
4. **Monitor replication lag** — alert when lag exceeds threshold
5. **Plan partition management** — automate creation of future partitions
6. **Start with partitioning** before sharding (simpler, often sufficient)

---

## Production Considerations

1. **Automated partition creation** (use cron/pg_partman extension)
2. **Partition detach/drop** for efficient data lifecycle management
3. **Replica promotion** for failover (pg_promote or repmgr)
4. **Connection pooling per shard** (PgBouncer per instance)
5. **Monitoring**: replication lag, partition sizes, cross-shard query count

```sql
-- pg_partman for automatic partition management
CREATE EXTENSION pg_partman;
SELECT partman.create_parent('public.orders', 'created_at', 'native', 'monthly');
-- Automatically creates partitions ahead of time and drops old ones
```

---

## Related Topics
- [Topic 14: Indexes](#)
- [Topic 15: Transactions, ACID, Isolation](#)
- [Topic 19: Database Architecture / System Design](#)
