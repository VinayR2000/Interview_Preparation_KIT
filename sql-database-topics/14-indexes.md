# Topic 14: Indexes — Extremely Important

## Theory

An index is a data structure that improves the speed of data retrieval at the cost of additional storage and slower writes.

**Analogy**: Like a book's index — instead of reading every page to find "ACID", you look up the index which says "ACID: page 145".

### Index Types

| Type | Structure | Best For |
|------|-----------|----------|
| **B-tree** | Balanced tree | Equality and range queries (default) |
| **Hash** | Hash table | Equality only (=) |
| **GIN** | Inverted index | Full-text search, arrays, JSONB |
| **GiST** | Generalized search tree | Geometric, range, full-text |
| **BRIN** | Block range | Very large tables with natural ordering |
| **Bitmap** | Bit array | Low cardinality columns (Oracle/internal) |

### Index Categories

| Category | Description |
|----------|-------------|
| **Clustered** | Data physically ordered by index (1 per table) |
| **Non-clustered** | Separate structure pointing to data |
| **Unique** | Enforces uniqueness |
| **Composite** | Multiple columns |
| **Covering** | Contains all columns needed (index-only scan) |
| **Partial** | Only indexes rows matching a condition |
| **Expression** | Indexes a function/expression result |

---

## Internal Working

### B-tree Structure

```
                         [50]
                        /    \
                [25, 35]      [75, 90]
               /   |   \    /   |    \
          [10,20][25,30][35,45][50,60][75,85][90,95]
              ↓      ↓     ↓     ↓      ↓     ↓
           (data) (data) (data) (data) (data) (data)

Properties:
- Balanced: All leaf nodes at same depth
- Ordered: Left < Parent < Right
- Lookup: O(log n) — at most ~4 levels for millions of rows
- Range scan: Follow leaf node pointers (linked list)
- Each node = one disk page (8KB in PostgreSQL)
```

### How Index Lookup Works

```
Query: SELECT * FROM employees WHERE id = 42;

Without Index (Sequential Scan):
┌──────────────────────────────────────────┐
│ Read page 1: rows 1-100    → not found   │
│ Read page 2: rows 101-200  → not found   │
│ Read page 3: rows 201-300  → not found   │
│ ...                                       │
│ Read page N: rows ...      → FOUND!      │
│ Time: O(n) — must scan all pages         │
└──────────────────────────────────────────┘

With B-tree Index:
┌──────────────────────────────────────────┐
│ Root page: 42 < 50 → go left            │
│ Branch page: 42 > 35 → go right         │
│ Leaf page: found entry for id=42        │
│   → Points to table page 5, offset 3    │
│ Read table page 5, get row              │
│ Time: O(log n) — 3-4 page reads         │
└──────────────────────────────────────────┘
```

### Composite Index and Leftmost Prefix Rule

```
Index: (department, salary, hire_date)

Can use index:
✓ WHERE department = 'Eng'
✓ WHERE department = 'Eng' AND salary > 90000
✓ WHERE department = 'Eng' AND salary > 90000 AND hire_date > '2023-01-01'
✓ WHERE department = 'Eng' ORDER BY salary

Cannot use index:
✗ WHERE salary > 90000  (skips first column)
✗ WHERE hire_date > '2023-01-01'  (skips first two columns)
✗ ORDER BY salary  (without department filter)

Partial use:
△ WHERE department = 'Eng' AND hire_date > '2023-01-01'
  (uses department part, but can't jump to hire_date — scans salary range)
```

### Index-Only Scan (Covering Index)

```
Normal index scan:
1. Search index → find row pointer
2. Go to table page → read full row
3. Return requested columns

Index-only scan:
1. Search index → ALL requested columns are IN the index
2. Return directly from index (no table access!)

CREATE INDEX idx_covering ON orders (customer_id) INCLUDE (total_amount, status);
-- Query: SELECT total_amount, status FROM orders WHERE customer_id = 42
-- → Index-only scan! Never touches the table.
```

---

## Code Examples

### Basic Index Creation

```sql
-- B-tree (default)
CREATE INDEX idx_emp_email ON employees (email);

-- Unique index
CREATE UNIQUE INDEX idx_emp_email_unique ON employees (email);

-- Composite index
CREATE INDEX idx_orders_customer_date ON orders (customer_id, created_at DESC);

-- Partial index (only active records)
CREATE INDEX idx_active_users ON users (email) WHERE is_active = TRUE;

-- Expression/Function index
CREATE INDEX idx_lower_email ON users (LOWER(email));

-- Covering index (INCLUDE — PostgreSQL 11+)
CREATE INDEX idx_orders_covering ON orders (customer_id, created_at DESC)
INCLUDE (total_amount, status);

-- Concurrent index creation (non-blocking)
CREATE INDEX CONCURRENTLY idx_orders_status ON orders (status);
```

### Specialized Index Types

```sql
-- Hash index (equality only, smaller than B-tree for =)
CREATE INDEX idx_sessions_token ON sessions USING HASH (session_token);

-- GIN for JSONB
CREATE INDEX idx_products_metadata ON products USING GIN (metadata);
-- Query: WHERE metadata @> '{"color": "red"}'

-- GIN for full-text search
CREATE INDEX idx_products_search ON products USING GIN (to_tsvector('english', name || ' ' || description));
-- Query: WHERE to_tsvector('english', name || ' ' || description) @@ to_tsquery('wireless & headphones')

-- GIN for array containment
CREATE INDEX idx_tags ON articles USING GIN (tags);
-- Query: WHERE tags @> ARRAY['postgresql', 'performance']

-- GiST for ranges
CREATE INDEX idx_booking_range ON bookings USING GIST (during);
-- Query: WHERE during && '[2024-01-01, 2024-01-31]'

-- BRIN for naturally ordered data (time-series)
CREATE INDEX idx_logs_timestamp ON logs USING BRIN (created_at);
-- Very small index, works because rows are inserted in timestamp order
```

### Index Usage Analysis

```sql
-- Check if index is being used
EXPLAIN ANALYZE
SELECT * FROM orders WHERE customer_id = 42 AND created_at > '2024-01-01';

-- Check index usage statistics
SELECT 
    indexrelname AS index_name,
    idx_scan AS times_used,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

-- Find unused indexes
SELECT 
    indexrelname AS index_name,
    relname AS table_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND indexrelname NOT LIKE '%pkey%'
ORDER BY pg_relation_size(indexrelid) DESC;
```

---

## Diagram

### When Index Is/Isn't Used

```
┌───────────────────────────────────────────────────────────┐
│         INDEX WILL BE USED                                 │
├───────────────────────────────────────────────────────────┤
│ ✓ WHERE column = value (equality)                         │
│ ✓ WHERE column > value (range with B-tree)                │
│ ✓ WHERE column LIKE 'abc%' (prefix pattern)               │
│ ✓ ORDER BY indexed_column                                 │
│ ✓ JOIN on indexed columns                                 │
│ ✓ WHERE LOWER(col) = 'x' (with expression index)         │
│ ✓ Small result set (high selectivity)                     │
└───────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────┐
│         INDEX WON'T BE USED                                │
├───────────────────────────────────────────────────────────┤
│ ✗ WHERE column LIKE '%abc' (leading wildcard)             │
│ ✗ WHERE function(column) = value (without expression idx) │
│ ✗ Large result set (>~10-20% of table → seq scan faster)  │
│ ✗ Very small table (seq scan faster)                      │
│ ✗ Column with low cardinality (status = 'active' on 90%)  │
│ ✗ OR conditions on different columns                      │
│ ✗ NOT IN / <> (usually needs full scan)                   │
│ ✗ IS NULL (depends on index type and configuration)       │
│ ✗ Implicit type conversion (varchar vs integer)           │
└───────────────────────────────────────────────────────────┘
```

---

## Dry Run

### EXPLAIN ANALYZE Reading

```sql
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE customer_id = 42
  AND status = 'completed'
ORDER BY created_at DESC
LIMIT 10;

-- Possible outputs:

-- GOOD (index used):
-- Limit (cost=0.43..8.50 rows=10) (actual time=0.035..0.052 rows=10)
--   -> Index Scan using idx_orders_customer_date on orders
--      (cost=0.43..125.50 rows=150) (actual time=0.034..0.048 rows=10)
--      Index Cond: (customer_id = 42)
--      Filter: (status = 'completed')
--      Rows Removed by Filter: 2

-- BAD (sequential scan):
-- Limit (cost=15000.00..15000.05 rows=10) (actual time=245.000..245.050 rows=10)
--   -> Sort (cost=15000.00..15200.00 rows=80000)
--      Sort Key: created_at DESC
--      -> Seq Scan on orders (cost=0.00..12000.00 rows=80000)
--         Filter: (customer_id = 42 AND status = 'completed')
--         Rows Removed by Filter: 920000
```

---

## Complexity

| Index Operation | B-tree | Hash | GIN |
|----------------|--------|------|-----|
| Lookup (=) | O(log n) | O(1) avg | O(log n) |
| Range scan | O(log n + k) | Not supported | O(log n + k) |
| Insert | O(log n) | O(1) avg | O(log n + items) |
| Delete | O(log n) | O(1) avg | O(log n) |
| Space | O(n) | O(n) | O(n × values) |

Where k = number of matching rows.

---

## Real Project Usage

### E-commerce Database Indexing Strategy

```sql
-- Products table
CREATE INDEX idx_products_category ON products (category_id) WHERE is_active = TRUE;
CREATE INDEX idx_products_price ON products (price) WHERE is_active = TRUE;
CREATE INDEX idx_products_search ON products USING GIN (
    to_tsvector('english', name || ' ' || COALESCE(description, ''))
);
CREATE INDEX idx_products_created ON products (created_at DESC);

-- Orders table
CREATE INDEX idx_orders_customer ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders (status) WHERE status IN ('pending', 'processing');
CREATE INDEX idx_orders_date ON orders (created_at DESC);

-- For pagination
CREATE INDEX idx_orders_pagination ON orders (created_at DESC, id DESC)
WHERE status = 'completed';

-- BRIN for large time-series
CREATE INDEX idx_events_time ON events USING BRIN (occurred_at)
WITH (pages_per_range = 32);
```

---

## Interview Questions & Answers

**Q1: Why doesn't the database always use an index?**

The optimizer chooses between index scan and sequential scan based on:
- **Selectivity**: If query returns >10-20% of rows, seq scan is faster (reading entire table sequentially is faster than random I/O from index)
- **Table size**: Very small tables fit in one page; seq scan is trivially fast
- **Statistics**: Stale statistics can mislead the optimizer

**Q2: Explain the leftmost prefix rule for composite indexes.**

For index `(a, b, c)`:
- WHERE a = 1 → uses index ✓
- WHERE a = 1 AND b = 2 → uses index ✓
- WHERE a = 1 AND b = 2 AND c = 3 → uses full index ✓
- WHERE b = 2 → cannot use index ✗ (skips 'a')
- WHERE b = 2 AND c = 3 → cannot use index ✗

Think of it like a phone book sorted by (last_name, first_name). You can look up "Smith" but you can't efficiently look up just "John" without knowing the last name.

**Q3: What is a covering index and why is it faster?**

A covering index includes ALL columns the query needs. The database reads only the index without accessing the main table (index-only scan).

```sql
-- Query: SELECT status, total FROM orders WHERE customer_id = 42
CREATE INDEX idx_covering ON orders (customer_id) INCLUDE (status, total);
-- Index-only scan: 2-3 page reads vs. potentially hundreds with table access
```

**Q4: When would you use a partial index?**

When most queries only need a subset of rows:
```sql
-- Only 5% of orders are 'pending', but you query them constantly
CREATE INDEX idx_pending_orders ON orders (created_at)
WHERE status = 'pending';
-- Much smaller index, faster maintenance, same query speed
```

**Q5: How do you identify missing indexes in production?**

1. `pg_stat_user_tables`: High `seq_scan` count suggests missing index
2. `EXPLAIN ANALYZE`: Look for Seq Scan with filter removing many rows
3. `pg_stat_statements`: Find slowest queries
4. `auto_explain`: Log slow query plans automatically

```sql
-- Tables needing indexes (high seq scan, low index scan)
SELECT relname, seq_scan, idx_scan, 
       seq_scan - idx_scan AS diff
FROM pg_stat_user_tables
WHERE seq_scan > idx_scan
ORDER BY diff DESC LIMIT 10;
```

---

## Common Mistakes

1. **Too many indexes** — each index slows INSERT/UPDATE/DELETE
2. **Indexing low-cardinality columns** — status with 3 values: index rarely helps
3. **Wrong column order in composite index** — must match query pattern
4. **Not using partial indexes** — full index when only subset is queried
5. **Implicit type conversion** — WHERE varchar_col = 123 (won't use index)
6. **Not running ANALYZE** — optimizer uses stale statistics
7. **Using function without expression index** — WHERE YEAR(date) = 2024

---

## Best Practices

1. **Index foreign keys** — used in JOINs and referential checks
2. **Index WHERE clause columns** — most direct performance impact
3. **Index ORDER BY columns** — avoid sort operations
4. **Use composite indexes** matching query patterns (most selective first)
5. **Use partial indexes** for queries targeting a subset
6. **Use INCLUDE** for covering indexes (avoid table lookups)
7. **Monitor and remove unused indexes** — they cost write performance
8. **Use CONCURRENTLY** for production index creation
9. **REINDEX periodically** for bloated indexes

---

## Production Considerations

1. **CREATE INDEX CONCURRENTLY**: Doesn't lock the table (but takes longer)
2. **Index bloat**: Dead tuples bloat indexes. Monitor with `pgstattuple`
3. **pg_stat_statements**: Identify slow queries needing indexes
4. **Index maintenance**: PostgreSQL auto-maintains B-tree; GIN needs `FASTUPDATE`
5. **Disk space**: Indexes can be 30-50% of total DB size. Plan accordingly
6. **Write amplification**: Each index adds work to every INSERT/UPDATE/DELETE

```sql
-- Safe production index creation
SET maintenance_work_mem = '1GB';  -- More memory = faster build
CREATE INDEX CONCURRENTLY idx_new ON large_table (column);
-- Note: CONCURRENTLY can't be in a transaction block
```

---

## Related Topics
- [Topic 1: Database Fundamentals](#)
- [Topic 19: Query Optimization](#)
- [Topic 37: PostgreSQL Specifics](#)
