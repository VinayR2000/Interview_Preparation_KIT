# Topic 8: Subqueries

## Theory

A subquery is a query nested inside another SQL statement. It can appear in SELECT, FROM, WHERE, or HAVING clauses.

### Types of Subqueries

| Type | Returns | Usage |
|------|---------|-------|
| **Scalar** | Single value (1 row, 1 column) | SELECT, WHERE comparisons |
| **Single-row** | One row, multiple columns | WHERE with =, <, > |
| **Multi-row** | Multiple rows, one column | WHERE with IN, ANY, ALL |
| **Multi-column** | Multiple rows and columns | FROM clause (derived table) |
| **Correlated** | Depends on outer query | EXISTS, row-by-row evaluation |
| **Non-correlated** | Independent of outer query | Executed once, result reused |

### Correlated vs Non-Correlated

```
Non-Correlated:
┌─────────────────────────────────────────┐
│ Subquery runs ONCE                       │
│ Result cached and reused for all rows    │
│ Independent of outer query               │
│ Generally faster                         │
└─────────────────────────────────────────┘

Correlated:
┌─────────────────────────────────────────┐
│ Subquery runs ONCE PER ROW of outer     │
│ References outer query columns           │
│ Cannot run independently                 │
│ Can be slow on large datasets           │
└─────────────────────────────────────────┘
```

---

## Internal Working

```
Non-Correlated Subquery Execution:
┌──────────────────────────────────────┐
│ 1. Execute inner query ONCE          │
│ 2. Store result (hash/list)          │
│ 3. Execute outer query               │
│ 4. For each outer row, check against │
│    stored result                     │
└──────────────────────────────────────┘

Correlated Subquery Execution:
┌──────────────────────────────────────┐
│ 1. For each row in outer query:      │
│    a. Substitute outer values        │
│    b. Execute inner query            │
│    c. Evaluate condition             │
│ 2. Return matching rows              │
│                                      │
│ Optimization: May be rewritten as    │
│ semi-join or anti-join by optimizer   │
└──────────────────────────────────────┘
```

---

## Code Examples

### Scalar Subquery (returns single value)

```sql
-- In SELECT
SELECT 
    name,
    salary,
    salary - (SELECT AVG(salary) FROM employees) AS diff_from_avg
FROM employees;

-- In WHERE
SELECT * FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);

-- In HAVING
SELECT department, AVG(salary) AS dept_avg
FROM employees
GROUP BY department
HAVING AVG(salary) > (SELECT AVG(salary) FROM employees);
```

### Multi-row Subquery (IN, ANY, ALL)

```sql
-- IN: Match any value in the list
SELECT * FROM employees
WHERE dept_id IN (
    SELECT id FROM departments WHERE budget > 1000000
);

-- NOT IN (careful with NULLs!)
SELECT * FROM customers
WHERE id NOT IN (
    SELECT customer_id FROM orders WHERE customer_id IS NOT NULL
);

-- ANY/SOME: Compare to any value (at least one must match)
SELECT * FROM employees
WHERE salary > ANY (
    SELECT salary FROM employees WHERE department = 'Marketing'
);
-- Returns employees earning more than the LOWEST marketing salary

-- ALL: Compare to all values (all must satisfy)
SELECT * FROM employees
WHERE salary > ALL (
    SELECT salary FROM employees WHERE department = 'Marketing'
);
-- Returns employees earning more than the HIGHEST marketing salary
```

### Correlated Subquery

```sql
-- Employees earning above their department average
SELECT e.name, e.salary, e.department
FROM employees e
WHERE e.salary > (
    SELECT AVG(e2.salary)
    FROM employees e2
    WHERE e2.department = e.department  -- References outer query!
);

-- Latest order per customer
SELECT *
FROM orders o
WHERE o.created_at = (
    SELECT MAX(o2.created_at)
    FROM orders o2
    WHERE o2.customer_id = o.customer_id  -- Correlated!
);
```

### EXISTS and NOT EXISTS

```sql
-- EXISTS: Returns TRUE if subquery returns ANY rows
-- Customers who have placed at least one order
SELECT c.name
FROM customers c
WHERE EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id
);

-- NOT EXISTS: Customers who have NEVER ordered
SELECT c.name
FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id
);

-- EXISTS vs IN comparison:
-- EXISTS is generally better when:
-- 1. Subquery returns many rows (EXISTS stops at first match)
-- 2. NULL values exist (EXISTS handles NULLs correctly)
-- 3. Checking existence, not values
```

### Subquery in FROM (Derived Table)

```sql
-- Derived table
SELECT dept_stats.department, dept_stats.avg_salary
FROM (
    SELECT department, AVG(salary) AS avg_salary, COUNT(*) AS cnt
    FROM employees
    GROUP BY department
) AS dept_stats
WHERE dept_stats.cnt > 5;

-- Equivalent to CTE (often preferred):
WITH dept_stats AS (
    SELECT department, AVG(salary) AS avg_salary, COUNT(*) AS cnt
    FROM employees
    GROUP BY department
)
SELECT department, avg_salary FROM dept_stats WHERE cnt > 5;
```

### EXISTS vs IN — The Key Difference

```sql
-- Scenario: orders.customer_id can be NULL

-- IN with NULLs — DANGER!
SELECT * FROM customers
WHERE id NOT IN (SELECT customer_id FROM orders);
-- If ANY customer_id in orders is NULL:
-- NOT IN returns: id <> 1 AND id <> 2 AND id <> NULL
-- → Always evaluates to NULL → Returns ZERO rows!

-- NOT EXISTS with NULLs — SAFE!
SELECT * FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id
);
-- EXISTS only checks if a row exists — NULL handling is correct

-- RULE: Always prefer NOT EXISTS over NOT IN
```

---

## Dry Run

### Correlated Subquery Step-by-Step

```sql
-- employees:
-- | id | name    | dept | salary |
-- |----|---------|------|--------|
-- | 1  | Alice   | Eng  | 95000  |
-- | 2  | Bob     | Eng  | 80000  |
-- | 3  | Charlie | Mkt  | 75000  |
-- | 4  | Diana   | Eng  | 90000  |
-- | 5  | Eve     | Mkt  | 85000  |

-- Query: Find employees earning above their department average
SELECT name, dept, salary
FROM employees e
WHERE salary > (
    SELECT AVG(salary) FROM employees WHERE dept = e.dept
);

-- Execution (correlated — runs subquery per outer row):
-- 
-- Row 1 (Alice, Eng, 95000):
--   Subquery: AVG WHERE dept='Eng' = (95000+80000+90000)/3 = 88333
--   95000 > 88333? YES → Include
--
-- Row 2 (Bob, Eng, 80000):
--   Subquery: AVG WHERE dept='Eng' = 88333
--   80000 > 88333? NO → Exclude
--
-- Row 3 (Charlie, Mkt, 75000):
--   Subquery: AVG WHERE dept='Mkt' = (75000+85000)/2 = 80000
--   75000 > 80000? NO → Exclude
--
-- Row 4 (Diana, Eng, 90000):
--   Subquery: AVG WHERE dept='Eng' = 88333
--   90000 > 88333? YES → Include
--
-- Row 5 (Eve, Mkt, 85000):
--   Subquery: AVG WHERE dept='Mkt' = 80000
--   85000 > 80000? YES → Include

-- Result:
-- | name  | dept | salary |
-- |-------|------|--------|
-- | Alice | Eng  | 95000  |
-- | Diana | Eng  | 90000  |
-- | Eve   | Mkt  | 85000  |
```

---

## Complexity

| Subquery Type | Time Complexity |
|---------------|----------------|
| Non-correlated scalar | O(inner) + O(outer) |
| Non-correlated IN | O(inner) + O(outer × lookup) |
| Correlated | O(outer × inner) worst case |
| EXISTS (correlated) | O(outer × partial_inner) — stops early |
| Derived table | O(inner) + O(result_set) |

**Optimizer optimizations:**
- Correlated subqueries often rewritten as joins (semi-join)
- IN converted to hash semi-join
- EXISTS with early termination

---

## Real Project Usage

### Find Records Meeting Complex Conditions

```sql
-- Orders with items exceeding average item price for their category
SELECT o.order_number, oi.product_id, oi.unit_price
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
WHERE oi.unit_price > (
    SELECT AVG(oi2.unit_price)
    FROM order_items oi2
    JOIN products p2 ON oi2.product_id = p2.id
    WHERE p2.category_id = p.category_id
);

-- Users who have been active every month for the last 6 months
SELECT u.id, u.name
FROM users u
WHERE NOT EXISTS (
    SELECT generate_series(
        DATE_TRUNC('month', NOW() - INTERVAL '5 months'),
        DATE_TRUNC('month', NOW()),
        '1 month'
    ) AS month
    EXCEPT
    SELECT DATE_TRUNC('month', a.activity_date)
    FROM user_activity a
    WHERE a.user_id = u.id
);
```

---

## Interview Questions & Answers

**Q1: What's the difference between correlated and non-correlated subqueries?**

Non-correlated: Runs once independently, result reused for all rows.
Correlated: References outer query, re-executes for each outer row.

**Q2: When is EXISTS better than IN?**
- When subquery might return NULLs (NOT IN fails with NULLs)
- When subquery returns many rows (EXISTS stops at first match)
- When you don't need the actual values, just existence

**Q3: Can you rewrite this correlated subquery as a JOIN?**
```sql
-- Correlated subquery:
SELECT * FROM employees e
WHERE salary > (SELECT AVG(salary) FROM employees WHERE dept = e.dept);

-- As JOIN:
SELECT e.*
FROM employees e
JOIN (SELECT dept, AVG(salary) as avg_sal FROM employees GROUP BY dept) d
    ON e.dept = d.dept
WHERE e.salary > d.avg_sal;
```

**Q4: Write a query to find the Nth highest salary.**
```sql
-- Method 1: Subquery with DISTINCT
SELECT DISTINCT salary
FROM employees
ORDER BY salary DESC
LIMIT 1 OFFSET N-1;

-- Method 2: Correlated subquery (no LIMIT)
SELECT * FROM employees e1
WHERE N-1 = (
    SELECT COUNT(DISTINCT salary) FROM employees e2
    WHERE e2.salary > e1.salary
);
```

---

## Common Mistakes

1. **NOT IN with NULLs** — always returns zero rows
2. **Using correlated subquery when JOIN would suffice** — performance penalty
3. **Scalar subquery returning multiple rows** — runtime error
4. **Not using aliases** for derived tables (required in most databases)
5. **Over-nesting subqueries** — use CTEs for readability

---

## Best Practices

1. **Use NOT EXISTS instead of NOT IN** — NULL-safe and often faster
2. **Use CTEs over deeply nested subqueries** — more readable
3. **Let the optimizer decide** — it may rewrite your subquery as a join anyway
4. **Use EXISTS for existence checks** — semantically clear and efficient
5. **Avoid correlated subqueries on large tables** without supporting indexes

---

## Production Considerations

1. **EXPLAIN ANALYZE** to verify optimizer strategy (semi-join vs. nested loop)
2. **Index on correlated subquery filter** — crucial for performance
3. **Materialized CTEs** — use `MATERIALIZED` hint if optimizer makes bad choice
4. **Subquery in SELECT** executed per row — avoid for large result sets

---

## Related Topics
- [Topic 7: Joins](#)
- [Topic 9: Set Operations](#)
- [Topic 11: CTE](#)
