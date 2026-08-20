# Topic 22: PostgreSQL Specifics

## Theory

### PostgreSQL Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    CLIENT CONNECTIONS                             │
│              (psql, JDBC, application)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    POSTMASTER PROCESS                             │
│         (Listens for connections, forks backends)                │
└───────────────────────────┬─────────────────────────────────────┘
                            │ fork
┌───────────────────────────▼─────────────────────────────────────┐
│                   BACKEND PROCESS (per connection)                │
│     ┌─────────────┬──────────────┬───────────────────┐          │
│     │   Parser    │   Planner    │    Executor       │          │
│     └─────────────┴──────────────┴───────────────────┘          │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    SHARED MEMORY                                  │
│  ┌──────────────┬────────────────┬──────────────────────┐       │
│  │ Shared Buffer│ WAL Buffers    │ Lock Tables          │       │
│  │ Pool (8KB    │ (Write-Ahead   │ (Row/table locks)    │       │
│  │  pages)      │  Log buffer)   │                      │       │
│  └──────────────┴────────────────┴──────────────────────┘       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    BACKGROUND PROCESSES                           │
│  ┌───────────┬────────────┬──────────┬────────────────┐         │
│  │ BGWriter  │ WAL Writer │ Autovac  │ Checkpointer   │         │
│  │ (dirty    │ (flush WAL │ (clean   │ (write dirty   │         │
│  │  pages)   │  to disk)  │  dead    │  buffers,      │         │
│  │           │            │  tuples) │  ensure WAL)   │         │
│  └───────────┴────────────┴──────────┴────────────────┘         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                       STORAGE                                    │
│  ┌─────────────────┬──────────────────┬─────────────────┐       │
│  │ Data Files      │ WAL Files        │ CLOG (commit    │       │
│  │ (base/dboid/)   │ (pg_wal/)        │  log)           │       │
│  └─────────────────┴──────────────────┴─────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

### Schemas

```sql
-- Schemas are namespaces within a database
-- Default schema: "public"

CREATE SCHEMA inventory;
CREATE SCHEMA reporting;

-- Create table in specific schema
CREATE TABLE inventory.products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200),
    stock INT
);

-- Access: schema_name.table_name
SELECT * FROM inventory.products;

-- Set search path (controls default schema resolution)
SET search_path TO inventory, public;
-- Now "products" resolves to inventory.products first

-- Per-user default schema
ALTER ROLE app_user SET search_path TO app, public;
```

---

## WAL (Write-Ahead Log)

```
┌─────────────────────────────────────────────────────────────────┐
│                   WAL — WRITE-AHEAD LOG                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  PRINCIPLE: Log the change BEFORE writing to data files          │
│                                                                   │
│  WRITE FLOW:                                                     │
│  1. Client: UPDATE balance = 800 WHERE id = 1                    │
│  2. PostgreSQL modifies page in shared_buffers (memory)          │
│  3. WAL record written to WAL buffer                             │
│  4. On COMMIT: WAL buffer flushed to WAL file on disk (fsync)   │
│  5. Response sent to client: "COMMIT OK"                         │
│  6. Later: Background writer flushes dirty page to data file     │
│                                                                   │
│  WHY WAL?                                                        │
│  • Durability: WAL on disk = change survives crash              │
│  • Performance: Sequential writes (WAL) faster than random      │
│  • Recovery: Replay WAL to rebuild state after crash            │
│  • Replication: Stream WAL to replicas                          │
│                                                                   │
│  CHECKPOINT:                                                     │
│  • Flushes all dirty buffers to data files                      │
│  • Updates checkpoint record in WAL                              │
│  • Allows recycling old WAL files                               │
│  • Recovery only needs to replay from last checkpoint           │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## PostgreSQL Index Types

### B-Tree (Default)

```sql
-- Best for: equality and range queries
-- Supports: <, <=, =, >=, >, BETWEEN, IN, IS NULL
CREATE INDEX idx_emp_salary ON employees (salary);

-- Used for ORDER BY
CREATE INDEX idx_emp_name ON employees (name);
SELECT * FROM employees ORDER BY name;  -- Uses index
```

### Hash Index

```sql
-- Best for: equality-only comparisons
-- Smaller than B-tree for equality checks
-- NOT useful for range queries
CREATE INDEX idx_emp_email_hash ON employees USING hash (email);

-- Good for: WHERE email = 'user@example.com'
-- Bad for:  WHERE email LIKE 'user%' or WHERE email > 'a'
```

### GIN (Generalized Inverted Index)

```sql
-- Best for: full-text search, JSONB, arrays, composite values
-- Stores mapping: value → list of rows containing that value

-- Full-text search
CREATE INDEX idx_articles_search ON articles 
USING gin (to_tsvector('english', title || ' ' || body));

SELECT * FROM articles 
WHERE to_tsvector('english', title || ' ' || body) 
      @@ to_tsquery('english', 'database & optimization');

-- JSONB containment
CREATE INDEX idx_data_gin ON events USING gin (metadata);
SELECT * FROM events WHERE metadata @> '{"type": "click"}';

-- Array containment
CREATE INDEX idx_tags_gin ON posts USING gin (tags);
SELECT * FROM posts WHERE tags @> ARRAY['postgresql', 'performance'];
```

### GiST (Generalized Search Tree)

```sql
-- Best for: geometric data, ranges, full-text search (ranking)
-- Supports "nearest neighbor" searches

-- Range types (overlapping, containment)
CREATE INDEX idx_booking_period ON bookings 
USING gist (tsrange(check_in, check_out));

-- Exclusion constraint (no overlapping bookings for same room)
ALTER TABLE bookings ADD CONSTRAINT no_overlap 
EXCLUDE USING gist (
    room_id WITH =,
    tsrange(check_in, check_out) WITH &&
);

-- Geographic (PostGIS)
CREATE INDEX idx_location ON stores USING gist (location);
SELECT * FROM stores 
ORDER BY location <-> point(40.7128, -74.0060) 
LIMIT 5;  -- 5 nearest stores
```

### BRIN (Block Range Index)

```sql
-- Best for: naturally ordered data (timestamps, sequential IDs)
-- Extremely small index size — stores min/max per block range
-- Perfect for time-series data

CREATE INDEX idx_events_created ON events USING brin (created_at);

-- Very small (few MB) even for tables with billions of rows
-- Works because timestamps are naturally ordered (append-only)
-- Inefficient if data is randomly ordered
```

### Comparison

```
┌──────────┬──────────────────────────┬──────────────────────────┐
│ Index    │ Best For                 │ Size        │ Speed      │
├──────────┼──────────────────────────┼─────────────┼────────────┤
│ B-Tree   │ Equality + Range         │ Medium      │ Fast       │
│ Hash     │ Equality only            │ Small       │ Fastest =  │
│ GIN      │ Multi-value (JSON,array) │ Large       │ Fast search│
│ GiST     │ Geometric, ranges        │ Medium      │ Flexible   │
│ BRIN     │ Ordered data (time)      │ Tiny        │ Block-level│
└──────────┴──────────────────────────┴─────────────┴────────────┘
```

---

## PostgreSQL-Specific SQL Features

### RETURNING Clause

```sql
-- Get the inserted/updated/deleted row(s) back immediately
INSERT INTO orders (customer_id, total)
VALUES (1, 150.00)
RETURNING id, created_at;
-- Returns: id=42, created_at='2024-01-15 10:30:00'

UPDATE products SET stock = stock - 1 WHERE id = 5
RETURNING id, stock;
-- Returns: id=5, stock=99

DELETE FROM expired_sessions WHERE expires_at < NOW()
RETURNING id, user_id;
-- Returns all deleted rows
```

### ON CONFLICT (UPSERT)

```sql
-- Insert or update if conflict on unique constraint
INSERT INTO users (email, name, last_login)
VALUES ('alice@example.com', 'Alice', NOW())
ON CONFLICT (email) 
DO UPDATE SET 
    last_login = EXCLUDED.last_login,
    name = EXCLUDED.name;

-- Insert or do nothing (ignore duplicates)
INSERT INTO tags (name)
VALUES ('postgresql')
ON CONFLICT (name) DO NOTHING;

-- With partial index conflict target
INSERT INTO products (sku, name, is_active)
VALUES ('ABC123', 'Widget', true)
ON CONFLICT (sku) WHERE is_active = true
DO UPDATE SET name = EXCLUDED.name;
```

### LATERAL Join

```sql
-- LATERAL allows subquery to reference preceding tables
-- Like a "for each row" correlated subquery in FROM clause

-- Top 3 orders per customer
SELECT c.name, recent_orders.*
FROM customers c
CROSS JOIN LATERAL (
    SELECT o.id, o.total, o.placed_at
    FROM orders o
    WHERE o.customer_id = c.id
    ORDER BY o.placed_at DESC
    LIMIT 3
) recent_orders;

-- Equivalent to a correlated subquery but can return multiple rows
-- Much more efficient than window function approach for "top N per group"
```

### JSON/JSONB

```sql
-- JSONB = binary JSON (faster queries, supports indexing)
-- JSON = text JSON (preserves formatting, no indexing)

CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50),
    payload JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Insert JSON data
INSERT INTO events (event_type, payload)
VALUES ('user_signup', '{"user_id": 1, "email": "alice@test.com", "plan": "pro"}');

-- Query JSON fields
SELECT payload->>'email' AS email FROM events;              -- text
SELECT payload->'user_id' FROM events;                      -- JSON value
SELECT payload#>>'{address,city}' FROM events;              -- nested path (text)

-- Filter by JSON content
SELECT * FROM events WHERE payload @> '{"plan": "pro"}';   -- containment
SELECT * FROM events WHERE payload->>'email' LIKE '%@test%';
SELECT * FROM events WHERE payload ? 'email';              -- key exists

-- Update JSON field
UPDATE events 
SET payload = jsonb_set(payload, '{plan}', '"enterprise"')
WHERE id = 1;

-- Index JSONB
CREATE INDEX idx_events_payload ON events USING gin (payload);

-- Index specific JSON path
CREATE INDEX idx_events_email ON events ((payload->>'email'));
```

### Arrays

```sql
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200),
    tags TEXT[] NOT NULL DEFAULT '{}'
);

INSERT INTO posts (title, tags) 
VALUES ('PostgreSQL Tips', ARRAY['postgresql', 'database', 'performance']);

-- Array operators
SELECT * FROM posts WHERE tags @> ARRAY['postgresql'];       -- contains
SELECT * FROM posts WHERE tags && ARRAY['postgresql','mysql']; -- overlap (any)
SELECT * FROM posts WHERE 'postgresql' = ANY(tags);          -- element in array

-- Unnest (expand array to rows)
SELECT id, unnest(tags) AS tag FROM posts;

-- Aggregate back to array
SELECT array_agg(DISTINCT tag) FROM (
    SELECT unnest(tags) AS tag FROM posts
) t;
```

### ILIKE (Case-Insensitive LIKE)

```sql
-- PostgreSQL-specific case-insensitive pattern matching
SELECT * FROM users WHERE name ILIKE '%john%';
-- Matches: John, JOHN, john, JoHn

-- Standard SQL equivalent (less efficient)
SELECT * FROM users WHERE LOWER(name) LIKE '%john%';

-- For indexed ILIKE, use expression index:
CREATE INDEX idx_users_name_lower ON users (LOWER(name));
-- Or use pg_trgm extension for LIKE/ILIKE with GIN index
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_users_name_trgm ON users USING gin (name gin_trgm_ops);
```

---

## EXPLAIN & EXPLAIN ANALYZE

```sql
-- EXPLAIN: shows query plan WITHOUT executing
EXPLAIN 
SELECT * FROM orders WHERE customer_id = 5;

-- Output:
-- Index Scan using idx_orders_customer on orders (cost=0.29..8.30 rows=1 width=48)
--   Index Cond: (customer_id = 5)

-- EXPLAIN ANALYZE: executes query AND shows actual times
EXPLAIN ANALYZE 
SELECT * FROM orders WHERE customer_id = 5;

-- Output:
-- Index Scan using idx_orders_customer on orders
--   (cost=0.29..8.30 rows=1 width=48) 
--   (actual time=0.025..0.027 rows=3 loops=1)
-- Planning Time: 0.152 ms
-- Execution Time: 0.058 ms

-- EXPLAIN with all options
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT o.id, c.name, o.total
FROM orders o
JOIN customers c ON o.customer_id = c.id
WHERE o.placed_at > '2024-01-01';

-- Key metrics to look for:
-- cost=startup..total (estimated cost units)
-- rows=N (estimated rows)
-- actual time=start..end (milliseconds)
-- loops=N (how many times node executed)
-- Buffers: shared hit=N read=N (cache vs disk)
```

### Reading Execution Plans

```
┌─────────────────────────────────────────────────────────────┐
│              SCAN TYPES (best to worst)                       │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Index Only Scan  → reads from index alone (covering index) │
│  Index Scan       → reads index + heap (table)              │
│  Bitmap Index Scan → builds bitmap, then scans heap         │
│  Sequential Scan   → full table scan (no index used)        │
│                                                               │
│  JOIN ALGORITHMS:                                            │
│  Nested Loop  → for each row in A, scan B (small tables)    │
│  Hash Join    → build hash table of smaller set (equality)  │
│  Merge Join   → merge two sorted inputs (pre-sorted data)   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## PostgreSQL Functions & Triggers

```sql
-- PL/pgSQL Function
CREATE OR REPLACE FUNCTION calculate_order_total(p_order_id BIGINT)
RETURNS DECIMAL AS $$
DECLARE
    v_total DECIMAL(12,2);
BEGIN
    SELECT COALESCE(SUM(quantity * unit_price), 0)
    INTO v_total
    FROM order_items
    WHERE order_id = p_order_id;
    
    RETURN v_total;
END;
$$ LANGUAGE plpgsql STABLE;

-- Usage
SELECT calculate_order_total(42);
UPDATE orders SET total = calculate_order_total(42) WHERE id = 42;

-- Trigger function
CREATE OR REPLACE FUNCTION update_modified_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger
CREATE TRIGGER trg_orders_updated
BEFORE UPDATE ON orders
FOR EACH ROW
EXECUTE FUNCTION update_modified_timestamp();

-- Audit trigger
CREATE OR REPLACE FUNCTION audit_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO audit_log (table_name, operation, old_data, changed_at)
        VALUES (TG_TABLE_NAME, 'DELETE', row_to_json(OLD), NOW());
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit_log (table_name, operation, old_data, new_data, changed_at)
        VALUES (TG_TABLE_NAME, 'UPDATE', row_to_json(OLD), row_to_json(NEW), NOW());
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO audit_log (table_name, operation, new_data, changed_at)
        VALUES (TG_TABLE_NAME, 'INSERT', row_to_json(NEW), NOW());
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_audit
AFTER INSERT OR UPDATE OR DELETE ON orders
FOR EACH ROW EXECUTE FUNCTION audit_changes();
```

---

## PostgreSQL Configuration & Tuning

```
┌─────────────────────────────────────────────────────────────┐
│           KEY POSTGRESQL PARAMETERS                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  MEMORY:                                                     │
│  shared_buffers = 25% of RAM (e.g., 4GB for 16GB system)    │
│  work_mem = 256MB (per-operation sort/hash memory)           │
│  maintenance_work_mem = 512MB (VACUUM, CREATE INDEX)         │
│  effective_cache_size = 75% of RAM (planner hint)            │
│                                                               │
│  CONNECTIONS:                                                │
│  max_connections = 200 (use connection pool!)                │
│  Note: each connection uses ~10MB of memory                  │
│                                                               │
│  WAL:                                                        │
│  wal_level = replica (needed for replication)                │
│  max_wal_size = 2GB (triggers checkpoint when reached)       │
│  min_wal_size = 1GB                                          │
│                                                               │
│  AUTOVACUUM:                                                 │
│  autovacuum = on (NEVER disable)                             │
│  autovacuum_vacuum_scale_factor = 0.2                        │
│  autovacuum_analyze_scale_factor = 0.1                       │
│                                                               │
│  QUERY PLANNER:                                              │
│  random_page_cost = 1.1 (for SSD, default 4.0 for HDD)     │
│  effective_io_concurrency = 200 (for SSD)                    │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Connection Pooling

```
┌─────────────────────────────────────────────────────────────┐
│             WHY CONNECTION POOLING?                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Problem: PostgreSQL forks a new process per connection      │
│  Each process: ~10MB RAM + fork overhead + authentication    │
│                                                               │
│  Without pooling (100 app instances × 20 connections):       │
│    = 2000 PostgreSQL backend processes                        │
│    = ~20GB RAM just for connections!                          │
│    = Context switching nightmare                              │
│                                                               │
│  With pooling (PgBouncer with pool_size=50):                │
│    = 50 actual PostgreSQL connections shared among 2000      │
│    = ~500MB for connections                                  │
│    = Much better performance                                 │
│                                                               │
│  POOLING MODES:                                              │
│  • Session: Connection assigned for entire client session    │
│  • Transaction: Connection assigned per transaction          │
│    (most common, best utilization)                           │
│  • Statement: Connection per statement (limited, no txns)    │
│                                                               │
│  TOOLS:                                                      │
│  • PgBouncer (lightweight, most popular)                     │
│  • Pgpool-II (pooling + load balancing + replication)        │
│  • HikariCP (application-side, for Java/Spring Boot)         │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Partitioning in PostgreSQL

```sql
-- Range partitioning (most common for time-series)
CREATE TABLE events (
    id BIGSERIAL,
    event_type VARCHAR(50),
    payload JSONB,
    created_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (created_at);

-- Create partitions
CREATE TABLE events_2024_q1 PARTITION OF events
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');
CREATE TABLE events_2024_q2 PARTITION OF events
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');
CREATE TABLE events_2024_q3 PARTITION OF events
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');

-- List partitioning
CREATE TABLE orders (
    id BIGSERIAL,
    region VARCHAR(20),
    total DECIMAL(10,2)
) PARTITION BY LIST (region);

CREATE TABLE orders_us PARTITION OF orders FOR VALUES IN ('US');
CREATE TABLE orders_eu PARTITION OF orders FOR VALUES IN ('EU', 'UK');
CREATE TABLE orders_asia PARTITION OF orders FOR VALUES IN ('ASIA');

-- Hash partitioning (even distribution)
CREATE TABLE sessions (
    id UUID,
    user_id BIGINT,
    data JSONB
) PARTITION BY HASH (user_id);

CREATE TABLE sessions_0 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE sessions_1 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE sessions_2 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE sessions_3 PARTITION OF sessions FOR VALUES WITH (MODULUS 4, REMAINDER 3);

-- Partition pruning (automatic)
EXPLAIN SELECT * FROM events WHERE created_at = '2024-02-15';
-- Only scans events_2024_q1 partition!
```

---

## Replication in PostgreSQL

```
┌─────────────────────────────────────────────────────────────┐
│            STREAMING REPLICATION                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────┐  WAL Stream   ┌──────────┐                   │
│  │ Primary  │ ──────────────▶│ Replica  │                   │
│  │ (R/W)    │               │ (Read)   │                   │
│  └──────────┘               └──────────┘                   │
│       │                          │                           │
│       │  WAL Stream              │                           │
│       │         ┌──────────┐    │                           │
│       └─────────▶│ Replica 2│    │                           │
│                  │ (Read)   │    │                           │
│                  └──────────┘    │                           │
│                                                               │
│  SYNCHRONOUS:                                                │
│  • Primary waits for replica to write WAL before COMMIT      │
│  • Zero data loss (RPO = 0)                                 │
│  • Higher latency on writes                                  │
│                                                               │
│  ASYNCHRONOUS (default):                                     │
│  • Primary doesn't wait for replica                          │
│  • Possible data loss if primary crashes                    │
│  • Lower write latency                                       │
│  • Replication lag possible                                  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

```sql
-- Check replication status on primary
SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn,
       sent_lsn - replay_lsn AS replication_lag
FROM pg_stat_replication;

-- Check replication lag on replica
SELECT NOW() - pg_last_xact_replay_timestamp() AS replication_delay;
```

---

## Interview Questions and Answers

**Q1: What are the main PostgreSQL index types and when would you use each?**
A: B-Tree (default, equality + range), Hash (equality only), GIN (full-text, JSONB, arrays), GiST (geometric, ranges, nearest-neighbor), BRIN (naturally ordered data like timestamps — tiny index, block-level filtering).

**Q2: Explain MVCC in PostgreSQL. What are xmin and xmax?**
A: PostgreSQL MVCC keeps multiple row versions. xmin is the transaction ID that created the row version; xmax is the transaction that deleted/updated it (0 if still active). Each transaction sees a snapshot and only reads versions visible to its snapshot, allowing readers and writers to work concurrently without blocking.

**Q3: What is WAL and why is it important?**
A: WAL (Write-Ahead Log) records changes before they reach data files. Benefits: durability (survive crashes), performance (sequential writes), recovery (replay from last checkpoint), replication (stream to replicas). It's the foundation of PostgreSQL's crash safety and replication.

**Q4: How does VACUUM work? What happens if autovacuum stops working?**
A: VACUUM reclaims space from dead tuples (old MVCC versions). If autovacuum fails: table bloat grows, sequential scans slow down, indexes bloat, and eventually transaction ID wraparound can force the database into emergency shutdown. Always monitor autovacuum.

**Q5: What is ON CONFLICT and how does it differ from an application-level check?**
A: ON CONFLICT (UPSERT) atomically handles conflicts on unique constraints — either updating or doing nothing. Application-level check-then-insert has a race condition (two threads check simultaneously, both find no conflict, both insert). ON CONFLICT is atomic and safe for concurrent access.

**Q6: When would you use LATERAL JOIN?**
A: When you need a correlated subquery in FROM that returns multiple rows or uses LIMIT per outer row. Common use: "top N per group" queries. It's more efficient than window functions for this pattern because it can use indexes with LIMIT.

---

## Follow-up Questions and Answers

**Q: What's the difference between GIN and GiST for full-text search?**
A: GIN is faster for lookups (exact matching) but slower to build and larger. GiST is smaller and faster to build but slower to search and may return false positives that require recheck. Use GIN for read-heavy workloads, GiST for write-heavy or when combined with nearest-neighbor.

**Q: How do you handle connection limits in production PostgreSQL?**
A: Use PgBouncer in transaction mode in front of PostgreSQL. Set PostgreSQL max_connections conservatively (100-300). PgBouncer can handle thousands of client connections mapped to a small pool of actual database connections. Monitor with pg_stat_activity.

**Q: What is a partial index and when would you use it?**
A: A partial index includes only rows matching a WHERE clause. Use for: indexing only active records (`WHERE is_deleted = false`), indexing rare values (`WHERE status = 'failed'`), reducing index size significantly when most rows don't need indexing.

---

## Common Mistakes

1. **Not using connection pooling** — PostgreSQL handles few hundred connections well, not thousands
2. **Disabling autovacuum** — leads to bloat and eventual transaction ID wraparound
3. **Using JSON instead of JSONB** — JSONB is almost always better (indexed, efficient)
4. **Not running ANALYZE after bulk operations** — planner uses stale statistics
5. **Setting work_mem too high globally** — multiplied by concurrent queries = OOM
6. **Using VACUUM FULL in production** — takes exclusive lock, use pg_repack instead
7. **Not indexing foreign keys** — PostgreSQL doesn't auto-index FKs (unlike MySQL)
8. **Ignoring BRIN for time-series data** — orders of magnitude smaller than B-tree

---

## Best Practices

1. **Always use JSONB** over JSON unless you need to preserve formatting
2. **Index foreign keys** explicitly
3. **Use connection pooling** (PgBouncer or HikariCP)
4. **Set random_page_cost = 1.1** for SSD storage
5. **Use partial indexes** for queries with constant WHERE conditions
6. **Use BRIN indexes** for time-series and naturally ordered data
7. **Monitor pg_stat_statements** for slow query identification
8. **Use EXPLAIN (ANALYZE, BUFFERS)** to understand actual query performance
9. **Partition large tables** (100M+ rows) by time or logical key
10. **Never disable autovacuum** — tune it instead

---

## Production Considerations

- **pg_stat_statements**: Enable for query performance monitoring
- **pg_stat_user_tables**: Monitor dead tuple count and last vacuum/analyze times
- **log_min_duration_statement**: Log slow queries (set to 100ms-1000ms)
- **Statement timeout**: Set to prevent runaway queries (30s for OLTP)
- **Lock timeout**: Set to prevent indefinite lock waits (5s-10s)
- **Backup**: Use pg_basebackup + WAL archiving for point-in-time recovery
- **Monitor**: Replication lag, connection count, dead tuples, cache hit ratio

---

## Related Topics

- Topic 14: Indexes (general index concepts)
- Topic 16: Query Optimization (EXPLAIN, plan analysis)
- Topic 18: Partitioning, Replication, Sharding (expanded)
- Topic 21: Locking, Concurrency & MVCC (detailed)
- Topic 25: Advanced Database Performance
