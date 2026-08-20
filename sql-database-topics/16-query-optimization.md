# Topic 16: Query Optimization

## Theory

Query optimization is the process of selecting the most efficient execution plan for a SQL query. The optimizer evaluates multiple strategies and picks the one with the lowest estimated cost.

### Query Execution Pipeline

```
SQL Query → Parser → Analyzer → Rewriter → Optimizer → Executor → Results
                                               │
                                    Generates multiple plans
                                    Estimates cost of each
                                    Picks lowest cost plan
```

### Scan Types

| Scan Type | Description | When Used |
|-----------|-------------|-----------|
| Sequential Scan | Reads every row in table | No index, or >10-20% of rows |
| Index Scan | Uses index, then fetches row from table | Selective queries |
| Index Only Scan | Reads only from index (covering) | All columns in index |
| Bitmap Index Scan | Creates bitmap from index, then scans table | Multiple conditions, medium selectivity |
| Bitmap Heap Scan | Reads table pages using bitmap | After bitmap index scan |

### Join Algorithms

| Algorithm | Best When | Time | Space |
|-----------|-----------|------|-------|
| Nested Loop | Small outer, indexed inner | O(n × log m) | O(1) |
| Hash Join | Large tables, no index, equality | O(n + m) | O(min(n,m)) |
| Merge Join | Both sorted (indexed), equality | O(n + m) | O(1) |

---

## Internal Working

### Cost-Based Optimizer

```
┌────────────────────────────────────────────────────────────┐
│                   COST ESTIMATION                            │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  Total Cost = I/O Cost + CPU Cost                           │
│                                                              │
│  Key factors:                                               │
│  - seq_page_cost = 1.0 (baseline)                          │
│  - random_page_cost = 4.0 (random I/O 4x slower)          │
│  - cpu_tuple_cost = 0.01 (processing per row)              │
│  - cpu_operator_cost = 0.0025 (per operation)              │
│                                                              │
│  Statistics used:                                           │
│  - Table size (pages, rows)                                │
│  - Column statistics (distinct values, histograms)         │
│  - Index availability and selectivity                      │
│  - Correlation (physical vs logical ordering)              │
│                                                              │
│  Optimizer decision:                                        │
│  IF (estimated_rows / total_rows) > threshold              │
│    → Sequential Scan (read all pages sequentially)         │
│  ELSE                                                       │
│    → Index Scan (random I/O but fewer pages)               │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### Sargability (Search ARGument ABLE)

A WHERE condition is **sargable** if it can use an index.

```
SARGABLE (index usable):
✓ WHERE column = value
✓ WHERE column > value
✓ WHERE column BETWEEN a AND b
✓ WHERE column LIKE 'prefix%'
✓ WHERE column IN (1, 2, 3)
✓ WHERE column IS NULL

NOT SARGABLE (index unusable):
✗ WHERE YEAR(column) = 2024          → Fix: WHERE column >= '2024-01-01' AND column < '2025-01-01'
✗ WHERE column + 1 = 5               → Fix: WHERE column = 4
✗ WHERE UPPER(column) = 'ALICE'      → Fix: Expression index or WHERE column ILIKE 'alice'
✗ WHERE column LIKE '%suffix'         → Fix: GIN trigram index
✗ WHERE column != value              → Usually requires full scan
✗ WHERE NOT column                    → Usually requires full scan
```

---

## Code Examples

### EXPLAIN and EXPLAIN ANALYZE

```sql
-- EXPLAIN: Shows plan WITHOUT executing
EXPLAIN
SELECT * FROM orders WHERE customer_id = 42;

-- EXPLAIN ANALYZE: Executes and shows actual times
EXPLAIN ANALYZE
SELECT * FROM orders WHERE customer_id = 42;

-- Full diagnostic
EXPLAIN (ANALYZE, BUFFERS, COSTS, TIMING, FORMAT TEXT)
SELECT o.*, c.name
FROM orders o
JOIN customers c ON o.customer_id = c.id
WHERE o.created_at > '2024-01-01'
ORDER BY o.created_at DESC
LIMIT 20;
```

### Reading EXPLAIN Output

```sql
EXPLAIN ANALYZE SELECT * FROM orders WHERE customer_id = 42 AND status = 'completed';

-- Output:
-- Bitmap Heap Scan on orders (cost=4.59..50.32 rows=12 width=120) (actual time=0.045..0.052 rows=8 loops=1)
--   Recheck Cond: (customer_id = 42)
--   Filter: (status = 'completed')
--   Rows Removed by Filter: 4
--   Heap Blocks: exact=5
--   ->  Bitmap Index Scan on idx_orders_customer (cost=0.00..4.59 rows=16 width=0) (actual time=0.031..0.031 rows=12 loops=1)
--         Index Cond: (customer_id = 42)
-- Planning Time: 0.085 ms
-- Execution Time: 0.073 ms

-- Reading this:
-- 1. Bitmap Index Scan: Found 12 rows matching customer_id=42 in index
-- 2. Bitmap Heap Scan: Fetched those rows from table, then filtered by status
-- 3. Filter removed 4 rows (12 from index - 4 filtered = 8 actual)
-- 4. cost=start..total: estimated startup and total cost
-- 5. actual time: real milliseconds
-- 6. rows: estimated vs actual row count
```

### Query Optimization Examples

```sql
-- BEFORE: Non-sargable (can't use index)
SELECT * FROM orders WHERE EXTRACT(YEAR FROM created_at) = 2024;

-- AFTER: Sargable (uses index on created_at)
SELECT * FROM orders 
WHERE created_at >= '2024-01-01' AND created_at < '2025-01-01';

-- BEFORE: Function on column prevents index
SELECT * FROM users WHERE LOWER(email) = 'john@example.com';

-- AFTER: Expression index
CREATE INDEX idx_lower_email ON users (LOWER(email));
SELECT * FROM users WHERE LOWER(email) = 'john@example.com';

-- BEFORE: SELECT * fetches unnecessary columns
SELECT * FROM products JOIN categories ON ... JOIN inventory ON ...;

-- AFTER: Only needed columns (enables index-only scan)
SELECT p.name, p.price, c.name 
FROM products p JOIN categories c ON p.category_id = c.id;

-- BEFORE: Correlated subquery (N+1 pattern)
SELECT name, (SELECT COUNT(*) FROM orders WHERE customer_id = c.id) AS order_count
FROM customers c;

-- AFTER: JOIN with aggregation
SELECT c.name, COUNT(o.id) AS order_count
FROM customers c
LEFT JOIN orders o ON c.id = o.customer_id
GROUP BY c.id, c.name;

-- BEFORE: OR on different columns (usually can't use single index)
SELECT * FROM products WHERE name = 'Widget' OR category = 'Electronics';

-- AFTER: UNION for separate index usage
SELECT * FROM products WHERE name = 'Widget'
UNION
SELECT * FROM products WHERE category = 'Electronics';
```

### Predicate Pushdown and Filtering Early

```sql
-- BEFORE: Filter late (joins everything first)
SELECT o.*, p.name
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
WHERE o.created_at > '2024-01-01' AND o.status = 'completed';

-- AFTER: CTE to filter early (reduces rows before join)
-- In modern PostgreSQL, optimizer often does this automatically
-- But explicit helps with complex queries:
WITH recent_orders AS (
    SELECT * FROM orders 
    WHERE created_at > '2024-01-01' AND status = 'completed'
)
SELECT ro.*, p.name
FROM recent_orders ro
JOIN order_items oi ON ro.id = oi.order_id
JOIN products p ON oi.product_id = p.id;
```

### Statistics and Maintenance

```sql
-- Update statistics (helps optimizer make better decisions)
ANALYZE orders;
ANALYZE products;

-- Update all tables
ANALYZE;

-- Check table statistics
SELECT 
    attname,
    n_distinct,
    most_common_vals,
    correlation
FROM pg_stats
WHERE tablename = 'orders' AND attname = 'status';

-- Check if vacuum is needed
SELECT 
    relname,
    n_live_tup,
    n_dead_tup,
    last_vacuum,
    last_autovacuum,
    last_analyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
```

---

## Dry Run

### Optimizer Decision Making

```sql
-- Table: orders (1,000,000 rows)
-- Index: idx_orders_customer_id (customer_id)
-- Statistics: 10,000 distinct customers (avg 100 orders each)

-- Query: SELECT * FROM orders WHERE customer_id = 42;
-- Optimizer estimate: 100 rows out of 1,000,000 (0.01% selectivity)
-- → Index Scan chosen (100 random page reads << 10,000 sequential pages)

-- Query: SELECT * FROM orders WHERE status = 'active';
-- Statistics: 80% of rows are 'active' = 800,000 rows
-- → Sequential Scan chosen (reading 80% via index means 800,000 random reads
--    which is MUCH slower than one sequential pass of all 10,000 pages)

-- Query: SELECT * FROM orders WHERE customer_id = 42 AND status = 'completed';
-- Plan A: Index on customer_id → 100 rows → filter status → ~30 rows
-- Plan B: Seq scan → filter both conditions → 30 rows
-- → Index Scan on customer_id wins (100 vs 1,000,000 rows examined)
```

---

## Real Project Usage

### Slow Query Investigation Workflow

```sql
-- Step 1: Enable slow query logging
SET log_min_duration_statement = '100ms';  -- Log queries > 100ms

-- Step 2: Find slowest queries (pg_stat_statements extension)
SELECT 
    query,
    calls,
    ROUND(mean_exec_time::NUMERIC, 2) AS avg_ms,
    ROUND(total_exec_time::NUMERIC, 2) AS total_ms,
    rows
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Step 3: Analyze the slow query
EXPLAIN (ANALYZE, BUFFERS)
SELECT ... -- the slow query

-- Step 4: Look for:
-- - Sequential scans on large tables (needs index)
-- - "Rows Removed by Filter" being high (filter after scan)
-- - Sort operations (missing index for ORDER BY)
-- - Hash Join with large build input (increase work_mem)
-- - Nested Loop with many iterations (consider Hash Join)

-- Step 5: Create appropriate index
CREATE INDEX CONCURRENTLY idx_fix ON table (column) WHERE condition;

-- Step 6: Verify improvement
EXPLAIN (ANALYZE, BUFFERS) SELECT ... -- same query
```

---

## Interview Questions & Answers

**Q1: What is a query execution plan and how do you read it?**

An execution plan shows HOW the database will execute a query. Read bottom-up (innermost operations first). Key metrics:
- **cost**: Estimated I/O + CPU cost (arbitrary units)
- **rows**: Estimated number of rows
- **actual time**: Real milliseconds (only with ANALYZE)
- **loops**: How many times this node was executed
- **Buffers**: Pages read from cache (shared hit) vs disk (read)

**Q2: Why might an index not be used even when it exists?**

1. Low selectivity (>10-20% of rows match)
2. Statistics are stale (ANALYZE not run)
3. Non-sargable condition (function on column)
4. Type mismatch (implicit casting)
5. NULL check on non-partial index
6. Optimizer estimates seq scan is cheaper
7. Table is very small (fits in few pages)

**Q3: How do you optimize a query that joins 5 tables?**

1. Check EXPLAIN plan for the bottleneck
2. Ensure indexes exist on all join columns (both sides)
3. Filter early (WHERE conditions that reduce most rows first)
4. Consider materialized views for pre-joined data
5. Increase work_mem if hash joins are spilling
6. Consider partial denormalization for read-heavy paths

**Q4: What is correlation in pg_stats and why does it matter?**

Correlation measures how well the physical order of rows matches the logical order of the index. Range 0 to ±1.
- Correlation = 1: Rows are physically ordered by this column (index scan is sequential I/O)
- Correlation = 0: Random physical order (index scan is random I/O, expensive)

This affects the optimizer's choice between index scan and bitmap scan.

---

## Common Mistakes

1. **Not using EXPLAIN ANALYZE** before optimizing (guessing instead of measuring)
2. **Creating indexes without checking if they're used**
3. **Stale statistics** — forgetting to ANALYZE after bulk loads
4. **Low work_mem** — causes hash joins to spill to disk
5. **Non-sargable queries** — putting functions on indexed columns
6. **SELECT *** — fetches unnecessary data, prevents index-only scans

---

## Best Practices

1. **Always EXPLAIN ANALYZE first** — measure, don't guess
2. **Keep statistics fresh** — `ANALYZE` after large data changes
3. **Use appropriate indexes** for your query patterns
4. **Write sargable conditions** — keep indexed columns bare
5. **Use covering indexes** for frequently-run queries
6. **Set work_mem appropriately** — too low causes disk spill
7. **Use connection pooling** — reduce per-query overhead
8. **Paginate large results** — never return unbounded result sets

---

## Production Considerations

1. **pg_stat_statements**: Essential for finding slow queries
2. **auto_explain**: Automatically log plans of slow queries
3. **pg_stat_user_tables**: Monitor seq_scan vs idx_scan ratio
4. **Autovacuum tuning**: Ensure statistics stay current
5. **work_mem**: Default 4MB often too low for complex queries (set 64MB-256MB for analytics)
6. **shared_buffers**: Set to 25% of RAM for caching

```sql
-- Essential production settings
ALTER SYSTEM SET shared_buffers = '4GB';    -- 25% of 16GB RAM
ALTER SYSTEM SET work_mem = '64MB';          -- For complex sorts/hashes
ALTER SYSTEM SET effective_cache_size = '12GB'; -- 75% of RAM (helps planner)
ALTER SYSTEM SET random_page_cost = 1.1;     -- Lower for SSD (default 4.0 for HDD)
```

---

## Related Topics
- [Topic 14: Indexes](#)
- [Topic 15: Transactions](#)
- [Topic 37: PostgreSQL Specifics](#)
