# Topic 11: CTE and Recursive Queries

## Theory

### CTE (Common Table Expression)

A CTE is a named temporary result set defined with the `WITH` keyword. It exists only for the duration of the single query.

```sql
WITH cte_name AS (
    SELECT ...
)
SELECT * FROM cte_name;
```

### CTE vs Subquery vs Temporary Table

| Feature | CTE | Subquery | Temp Table |
|---------|-----|----------|------------|
| Scope | Single query | Single query | Session |
| Reusable in query | Yes (multiple refs) | No | Yes |
| Self-referencing | Yes (recursive) | No | No |
| Materialized | DB decides (or hint) | Inline typically | Always |
| Index support | No | No | Yes |
| Modification | Can use INSERT/UPDATE | Read only | Full CRUD |

### Recursive CTE

A recursive CTE references itself, enabling hierarchical/tree/graph traversal:

```
WITH RECURSIVE cte AS (
    -- Anchor: Starting rows (base case)
    SELECT ... WHERE condition
    
    UNION ALL
    
    -- Recursive: References the CTE itself
    SELECT ... FROM cte JOIN table ON ...
)
SELECT * FROM cte;
```

**Execution:**
1. Execute anchor query → initial result set
2. Execute recursive part using previous iteration's result
3. Repeat until recursive part returns no rows
4. UNION ALL all iterations

---

## Internal Working

### Non-Recursive CTE Execution

```
┌─────────────────────────────────────────────────────┐
│ PostgreSQL CTE Behavior:                             │
│                                                       │
│ Before v12: CTEs always MATERIALIZED                 │
│   → Computed once, result stored in memory/temp      │
│   → Optimizer can't push predicates into CTE         │
│                                                       │
│ From v12+: CTEs can be INLINED (default if safe)     │
│   → Treated like a subquery                          │
│   → Optimizer can push predicates through            │
│   → Use MATERIALIZED/NOT MATERIALIZED to control     │
│                                                       │
│ CTE is MATERIALIZED if:                              │
│   - Referenced more than once                        │
│   - Is recursive                                     │
│   - Has side effects (data-modifying CTE)           │
│   - Explicitly marked MATERIALIZED                   │
└─────────────────────────────────────────────────────┘
```

### Recursive CTE Execution

```
┌──────────────────────────────────────────────────────┐
│ Iteration 0: Execute Anchor Query                     │
│   → Working Table = {initial rows}                   │
│                                                        │
│ Iteration 1: Execute Recursive with Working Table    │
│   → New rows found → Add to Working Table            │
│                                                        │
│ Iteration 2: Execute Recursive with NEW rows only    │
│   → More new rows → Add to Working Table             │
│                                                        │
│ ...                                                    │
│                                                        │
│ Iteration N: Execute Recursive → No new rows         │
│   → STOP                                              │
│                                                        │
│ Final Result = UNION ALL of all iterations            │
└──────────────────────────────────────────────────────┘
```

---

## Code Examples

### Basic CTE

```sql
-- Simple CTE
WITH active_employees AS (
    SELECT id, name, department, salary
    FROM employees
    WHERE is_active = TRUE
)
SELECT department, AVG(salary) AS avg_salary
FROM active_employees
GROUP BY department;

-- Multiple CTEs
WITH 
dept_stats AS (
    SELECT 
        department,
        AVG(salary) AS avg_salary,
        COUNT(*) AS headcount
    FROM employees
    GROUP BY department
),
high_paying_depts AS (
    SELECT department
    FROM dept_stats
    WHERE avg_salary > 90000
)
SELECT e.name, e.salary, e.department
FROM employees e
JOIN high_paying_depts h ON e.department = h.department
WHERE e.salary > (SELECT avg_salary FROM dept_stats WHERE department = e.department);

-- CTE chaining (one CTE references another)
WITH 
raw_orders AS (
    SELECT * FROM orders WHERE created_at >= '2024-01-01'
),
order_totals AS (
    SELECT customer_id, SUM(total_amount) AS total_spent
    FROM raw_orders
    GROUP BY customer_id
),
customer_segments AS (
    SELECT 
        customer_id,
        total_spent,
        CASE 
            WHEN total_spent >= 10000 THEN 'VIP'
            WHEN total_spent >= 5000 THEN 'Premium'
            ELSE 'Standard'
        END AS segment
    FROM order_totals
)
SELECT segment, COUNT(*) AS customer_count, AVG(total_spent) AS avg_spent
FROM customer_segments
GROUP BY segment;
```

### Materialization Control (PostgreSQL 12+)

```sql
-- Force materialization (CTE computed once, stored)
WITH active_users AS MATERIALIZED (
    SELECT * FROM users WHERE last_login > NOW() - INTERVAL '30 days'
)
SELECT * FROM active_users WHERE name LIKE 'A%'
UNION ALL
SELECT * FROM active_users WHERE department = 'Engineering';

-- Force inline (treated as subquery, optimizer can push predicates)
WITH filtered AS NOT MATERIALIZED (
    SELECT * FROM large_table
)
SELECT * FROM filtered WHERE id = 12345;
-- Optimizer can push WHERE id = 12345 into the CTE scan
```

### Recursive CTE — Employee Hierarchy

```sql
-- Find all reports under a manager (full org tree)
WITH RECURSIVE org_tree AS (
    -- Anchor: Start with the top manager
    SELECT id, name, manager_id, 1 AS level, name::TEXT AS path
    FROM employees
    WHERE manager_id IS NULL  -- Top of hierarchy
    
    UNION ALL
    
    -- Recursive: Find direct reports of previous level
    SELECT e.id, e.name, e.manager_id, t.level + 1, t.path || ' → ' || e.name
    FROM employees e
    INNER JOIN org_tree t ON e.manager_id = t.id
)
SELECT id, name, level, path
FROM org_tree
ORDER BY path;

-- Result:
-- | id | name    | level | path                          |
-- |----|---------|-------|-------------------------------|
-- | 1  | CEO     | 1     | CEO                           |
-- | 2  | VP Eng  | 2     | CEO → VP Eng                  |
-- | 4  | Dev Lead| 3     | CEO → VP Eng → Dev Lead       |
-- | 5  | Dev Sr  | 4     | CEO → VP Eng → Dev Lead → Dev Sr |
-- | 3  | VP Mkt  | 2     | CEO → VP Mkt                  |
```

### Recursive CTE — Bill of Materials

```sql
-- Product component tree (BOM explosion)
WITH RECURSIVE bom AS (
    -- Anchor: top-level product
    SELECT 
        component_id, 
        parent_id,
        quantity,
        1 AS level,
        ARRAY[component_id] AS path  -- Cycle detection
    FROM components
    WHERE parent_id = :product_id
    
    UNION ALL
    
    -- Recursive: sub-components
    SELECT 
        c.component_id,
        c.parent_id,
        c.quantity * b.quantity,  -- Multiply quantities down
        b.level + 1,
        b.path || c.component_id
    FROM components c
    INNER JOIN bom b ON c.parent_id = b.component_id
    WHERE c.component_id <> ALL(b.path)  -- Prevent cycles!
)
SELECT * FROM bom ORDER BY path;
```

### Recursive CTE — Generate Series Alternative

```sql
-- Generate numbers 1 to 100
WITH RECURSIVE numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 100
)
SELECT n FROM numbers;

-- Date series
WITH RECURSIVE dates AS (
    SELECT '2024-01-01'::DATE AS date
    UNION ALL
    SELECT date + 1 FROM dates WHERE date < '2024-12-31'
)
SELECT date FROM dates;
```

### Data-Modifying CTE

```sql
-- Archive and delete in one statement
WITH archived AS (
    DELETE FROM orders
    WHERE status = 'completed' AND created_at < NOW() - INTERVAL '2 years'
    RETURNING *
)
INSERT INTO orders_archive
SELECT * FROM archived;

-- Insert and update related tables
WITH new_order AS (
    INSERT INTO orders (customer_id, total_amount, status)
    VALUES (42, 199.99, 'pending')
    RETURNING id, customer_id
)
UPDATE customers
SET last_order_date = NOW(), order_count = order_count + 1
WHERE id = (SELECT customer_id FROM new_order);
```

---

## Dry Run

### Recursive CTE — Org Hierarchy

```sql
-- employees table:
-- | id | name     | manager_id |
-- |----|----------|------------|
-- | 1  | Alice    | NULL       |  (CEO)
-- | 2  | Bob      | 1          |  (reports to Alice)
-- | 3  | Charlie  | 1          |  (reports to Alice)
-- | 4  | Diana    | 2          |  (reports to Bob)
-- | 5  | Eve      | 2          |  (reports to Bob)

WITH RECURSIVE hierarchy AS (
    SELECT id, name, manager_id, 1 AS level
    FROM employees WHERE id = 2  -- Start from Bob
    UNION ALL
    SELECT e.id, e.name, e.manager_id, h.level + 1
    FROM employees e JOIN hierarchy h ON e.manager_id = h.id
)
SELECT * FROM hierarchy;

-- Iteration 0 (Anchor): WHERE id = 2
--   Working Table: [(2, Bob, 1, 1)]

-- Iteration 1 (Recursive): JOIN employees WHERE manager_id = 2
--   Found: Diana (4, Diana, 2, 2), Eve (5, Eve, 2, 2)
--   Working Table: [(4, Diana, 2, 2), (5, Eve, 2, 2)]

-- Iteration 2 (Recursive): JOIN employees WHERE manager_id IN (4, 5)
--   Found: nothing (no one reports to Diana or Eve)
--   STOP

-- Final Result (UNION ALL of all iterations):
-- | id | name   | manager_id | level |
-- |----|--------|------------|-------|
-- | 2  | Bob    | 1          | 1     |
-- | 4  | Diana  | 2          | 2     |
-- | 5  | Eve    | 2          | 2     |
```

---

## Complexity

| Type | Time Complexity |
|------|----------------|
| Non-recursive CTE | Same as equivalent subquery |
| Materialized CTE | O(CTE size) + O(main query) |
| Recursive CTE (depth d, branching b) | O(b^d) worst case |
| Recursive with cycle detection | O(n) where n = total reachable nodes |

**Depth limit**: PostgreSQL default is no limit. Use `LIMIT` or depth check to prevent infinite recursion.

---

## Real Project Usage

### Breadcrumb Navigation

```sql
-- Get category path for breadcrumbs
WITH RECURSIVE category_path AS (
    SELECT id, name, parent_id, name::TEXT AS path
    FROM categories
    WHERE id = :current_category_id
    
    UNION ALL
    
    SELECT c.id, c.name, c.parent_id, c.name || ' > ' || cp.path
    FROM categories c
    JOIN category_path cp ON c.id = cp.parent_id
)
SELECT path FROM category_path WHERE parent_id IS NULL;
-- Returns: "Electronics > Computers > Laptops"
```

### Pagination with Total Count (Single Query)

```sql
WITH filtered AS (
    SELECT * FROM products
    WHERE is_active = TRUE AND category_id = :cat_id
),
counted AS (
    SELECT COUNT(*) AS total FROM filtered
)
SELECT f.*, c.total
FROM filtered f, counted c
ORDER BY f.created_at DESC
LIMIT 20 OFFSET 0;
```

---

## Interview Questions & Answers

**Q1: What are the advantages of CTE over subqueries?**
1. **Readability**: Named, modular, top-down reading
2. **Reusability**: Can reference multiple times in one query
3. **Recursion**: Only CTEs support self-referencing
4. **Data-modifying**: Can include INSERT/UPDATE/DELETE with RETURNING

**Q2: When would a CTE hurt performance?**
Pre-PostgreSQL 12: CTEs were always materialized (optimization fence). This prevented the optimizer from pushing predicates into the CTE, potentially reading more data than needed.

**Q3: How do you prevent infinite loops in recursive CTEs?**
1. Add a depth/level counter and filter: `WHERE level < 20`
2. Use path array and check for cycles: `WHERE id <> ALL(path)`
3. PostgreSQL: `CYCLE` clause (PostgreSQL 14+)
```sql
WITH RECURSIVE ... CYCLE id SET is_cycle USING path;
```

**Q4: Can you use multiple CTEs in one query? Can they reference each other?**
Yes. Later CTEs can reference earlier ones (chaining). But a CTE cannot reference a later CTE.

---

## Common Mistakes

1. **Forgetting UNION ALL in recursive CTE** — using UNION causes dedup overhead
2. **No termination condition** — infinite recursion (add depth limit)
3. **Assuming CTE is always materialized** — post v12 it may be inlined
4. **Not using cycle detection** in graph traversal
5. **Over-using CTEs for simple subqueries** — adds complexity without benefit

---

## Best Practices

1. **Use CTEs for readability** in complex queries with multiple logical steps
2. **Use recursive CTEs** for hierarchical data (org charts, categories, BOM)
3. **Add depth limits** to recursive CTEs to prevent runaway queries
4. **Use MATERIALIZED/NOT MATERIALIZED** hints when optimizer makes wrong choice
5. **Prefer CTE over deep nesting** — easier to debug and maintain
6. **Use data-modifying CTEs** for atomic multi-table operations

---

## Production Considerations

1. **Recursive depth**: Set `max_recursive_cte_depth` or add WHERE clause limit
2. **Performance monitoring**: Recursive CTEs can explode on large hierarchies
3. **Alternative**: For deep hierarchies, consider ltree extension (PostgreSQL)
4. **Materialized views**: Pre-compute hierarchy paths for read-heavy workloads

```sql
-- PostgreSQL ltree alternative for hierarchies
CREATE EXTENSION ltree;
ALTER TABLE categories ADD COLUMN path ltree;
-- path = 'root.electronics.computers.laptops'
SELECT * FROM categories WHERE path <@ 'root.electronics';
```

---

## Related Topics
- [Topic 8: Subqueries](#)
- [Topic 10: Window Functions](#)
- [Topic 12: Recursive Queries (same topic expanded)](#)
- [Topic 13: SQL Coding Problems](#)
