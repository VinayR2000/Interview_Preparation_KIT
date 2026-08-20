# Topic 7: Joins — Extremely Important

## Theory

Joins combine rows from two or more tables based on a related column. The most critical SQL concept for interviews.

### Join Types Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                           JOIN TYPES                                  │
├──────────────┬─────────────────────────────────────────────────────┤
│ INNER JOIN   │ Only matching rows from BOTH tables                  │
│ LEFT JOIN    │ ALL rows from left + matching from right             │
│ RIGHT JOIN   │ ALL rows from right + matching from left             │
│ FULL JOIN    │ ALL rows from BOTH tables                            │
│ CROSS JOIN   │ Every row × every row (Cartesian product)           │
│ SELF JOIN    │ Table joined with itself                             │
│ NATURAL JOIN │ Auto-join on same-named columns (avoid in production)│
└──────────────┴─────────────────────────────────────────────────────┘
```

### Visual Representation

```
Table A        Table B          INNER JOIN (A ∩ B)
┌───┐          ┌───┐           Only where A and B match
│ 1 │──────────│ 1 │ ✓
│ 2 │──────────│ 2 │ ✓
│ 3 │          │ 4 │
│ 5 │          │ 6 │
└───┘          └───┘

LEFT JOIN                       RIGHT JOIN
All of A + matching B           All of B + matching A
│ 1 │──────────│ 1 │ ✓         │ 1 │──────────│ 1 │ ✓
│ 2 │──────────│ 2 │ ✓         │ 2 │──────────│ 2 │ ✓
│ 3 │          │   │ (NULL)    │   │          │ 4 │ (NULL from A)
│ 5 │          │   │ (NULL)    │   │          │ 6 │ (NULL from A)

FULL OUTER JOIN                 CROSS JOIN
Everything from both            Every combination
│ 1 │──────────│ 1 │ ✓         A has 4 rows, B has 4 rows
│ 2 │──────────│ 2 │ ✓         Result: 4 × 4 = 16 rows
│ 3 │          │   │ (NULL)
│ 5 │          │   │ (NULL)
│   │          │ 4 │ (NULL)
│   │          │ 6 │ (NULL)
```

---

## Internal Working

### Join Algorithms

The database optimizer chooses among three algorithms:

```
┌─────────────────────────────────────────────────────────────────┐
│                     JOIN ALGORITHMS                               │
├──────────────────────┬──────────────────┬───────────────────────┤
│   Nested Loop Join   │   Hash Join      │   Merge Join          │
├──────────────────────┼──────────────────┼───────────────────────┤
│ For each row in A:   │ 1. Build hash    │ 1. Sort both tables   │
│   For each row in B: │    table from    │    on join key        │
│     If match → emit  │    smaller table │ 2. Walk through both  │
│                      │ 2. Probe hash    │    simultaneously     │
│                      │    with larger   │ 3. Emit matches       │
│                      │    table rows    │                       │
├──────────────────────┼──────────────────┼───────────────────────┤
│ Best for:            │ Best for:        │ Best for:             │
│ - Small tables       │ - Large tables   │ - Already sorted      │
│ - Index on inner     │ - No index       │    (index exists)     │
│ - Few outer rows     │ - Equality join  │ - Large equality joins│
├──────────────────────┼──────────────────┼───────────────────────┤
│ Time: O(n × m)       │ Time: O(n + m)   │ Time: O(n log n +    │
│ With index: O(n×logm)│ Space: O(min(n,m))│      m log m)        │
└──────────────────────┴──────────────────┴───────────────────────┘
```

### Nested Loop Join (Detail)

```
For row in outer_table:            -- O(n)
    For row in inner_table:         -- O(m) without index, O(log m) with index
        If join_condition matches:
            Emit combined row

Total without index: O(n × m)
Total with index on inner: O(n × log m)
Best when: outer table is small, inner table has index on join column
```

### Hash Join (Detail)

```
BUILD PHASE:
    Create hash table from smaller table (build input)
    Key = join column value, Value = row data
    
PROBE PHASE:
    For each row in larger table (probe input):
        Hash the join column
        Look up in hash table
        If found → emit match

Total: O(n + m)
Space: O(min(n, m)) for hash table
Best when: no useful indexes, large tables, equality joins
```

### Merge Join (Detail)

```
Prerequisite: Both inputs sorted on join key

pointer_a = first row of A
pointer_b = first row of B

While both have rows:
    If A.key == B.key → emit match, advance both
    If A.key < B.key → advance A
    If A.key > B.key → advance B

Total: O(n + m) if pre-sorted, O(n log n + m log m) if sorting needed
Best when: both tables have indexes on join column
```

---

## Code Examples

### INNER JOIN

```sql
-- Basic INNER JOIN
SELECT e.name, e.salary, d.department_name
FROM employees e
INNER JOIN departments d ON e.dept_id = d.id;
-- Returns ONLY employees who have a department assigned

-- Multiple conditions
SELECT o.id, o.total, c.name
FROM orders o
INNER JOIN customers c ON o.customer_id = c.id 
    AND c.is_active = TRUE;

-- Multiple joins
SELECT 
    o.order_number,
    c.name AS customer,
    p.name AS product,
    oi.quantity,
    oi.unit_price
FROM orders o
INNER JOIN customers c ON o.customer_id = c.id
INNER JOIN order_items oi ON o.id = oi.order_id
INNER JOIN products p ON oi.product_id = p.id
WHERE o.status = 'completed';
```

### LEFT JOIN

```sql
-- All employees, with department name (NULL if no dept)
SELECT e.name, e.salary, d.department_name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id;

-- Find employees WITHOUT a department
SELECT e.name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id
WHERE d.id IS NULL;  -- No match in departments

-- Find customers who never ordered
SELECT c.id, c.name, c.email
FROM customers c
LEFT JOIN orders o ON c.id = o.customer_id
WHERE o.id IS NULL;

-- Left join with aggregation
SELECT 
    d.department_name,
    COUNT(e.id) AS employee_count,  -- COUNT(e.id) not COUNT(*) !
    COALESCE(AVG(e.salary), 0) AS avg_salary
FROM departments d
LEFT JOIN employees e ON d.id = e.dept_id
GROUP BY d.department_name;
```

### RIGHT JOIN

```sql
-- All departments with their employees (equivalent to swapped LEFT JOIN)
SELECT e.name, d.department_name
FROM employees e
RIGHT JOIN departments d ON e.dept_id = d.id;

-- This is equivalent to:
SELECT e.name, d.department_name
FROM departments d
LEFT JOIN employees e ON d.id = e.dept_id;
```

### FULL OUTER JOIN

```sql
-- All employees and all departments (matched where possible)
SELECT 
    COALESCE(e.name, 'No Employee') AS employee,
    COALESCE(d.department_name, 'No Department') AS department
FROM employees e
FULL OUTER JOIN departments d ON e.dept_id = d.id;

-- Find unmatched records on BOTH sides
SELECT e.name, d.department_name
FROM employees e
FULL OUTER JOIN departments d ON e.dept_id = d.id
WHERE e.id IS NULL OR d.id IS NULL;
-- Returns: employees without dept + departments without employees
```

### CROSS JOIN

```sql
-- Cartesian product (every combination)
SELECT e.name, p.project_name
FROM employees e
CROSS JOIN projects p;
-- If 10 employees and 5 projects → 50 rows

-- Practical use: generate all date-category combinations for gap filling
SELECT d.date, c.category_name, COALESCE(s.total, 0) AS sales
FROM generate_series('2024-01-01', '2024-01-31', '1 day') AS d(date)
CROSS JOIN categories c
LEFT JOIN daily_sales s ON s.sale_date = d.date AND s.category_id = c.id;
```

### SELF JOIN

```sql
-- Employee-Manager relationship
SELECT 
    e.name AS employee,
    m.name AS manager
FROM employees e
LEFT JOIN employees m ON e.manager_id = m.id;

-- Find employees earning more than their manager
SELECT 
    e.name AS employee,
    e.salary AS emp_salary,
    m.name AS manager,
    m.salary AS mgr_salary
FROM employees e
INNER JOIN employees m ON e.manager_id = m.id
WHERE e.salary > m.salary;

-- Find duplicate records
SELECT a.id, a.email
FROM customers a
INNER JOIN customers b ON a.email = b.email AND a.id < b.id;
```

### Complex Multi-Table Joins

```sql
-- E-commerce order details with all related data
SELECT 
    o.order_number,
    o.created_at,
    c.name AS customer_name,
    c.email,
    a.city || ', ' || a.state AS shipping_address,
    p.name AS product_name,
    p.sku,
    cat.name AS category,
    oi.quantity,
    oi.unit_price,
    (oi.quantity * oi.unit_price) AS line_total,
    d.code AS discount_code,
    COALESCE(d.percentage, 0) AS discount_pct
FROM orders o
INNER JOIN customers c ON o.customer_id = c.id
INNER JOIN addresses a ON o.shipping_address_id = a.id
INNER JOIN order_items oi ON o.id = oi.order_id
INNER JOIN products p ON oi.product_id = p.id
LEFT JOIN categories cat ON p.category_id = cat.id
LEFT JOIN order_discounts od ON o.id = od.order_id
LEFT JOIN discounts d ON od.discount_id = d.id
WHERE o.created_at >= '2024-01-01'
ORDER BY o.created_at DESC, o.id, oi.id;
```

---

## Dry Run

### LEFT JOIN Step-by-Step

```sql
-- employees:                    departments:
-- | id | name  | dept_id |     | id | name        |
-- |----|-------|---------|     |----|-------------|
-- | 1  | Alice | 10      |     | 10 | Engineering |
-- | 2  | Bob   | 20      |     | 20 | Marketing   |
-- | 3  | Eve   | NULL    |     | 30 | Finance     |
-- | 4  | Dave  | 10      |     

SELECT e.name, d.name AS dept
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id;

-- Process each row in LEFT table (employees):
-- Row 1: Alice, dept_id=10 → Look for d.id=10 → Found "Engineering"
--         Output: (Alice, Engineering)
-- Row 2: Bob, dept_id=20 → Look for d.id=20 → Found "Marketing"  
--         Output: (Bob, Marketing)
-- Row 3: Eve, dept_id=NULL → NULL can't match any d.id → No match
--         Output: (Eve, NULL)
-- Row 4: Dave, dept_id=10 → Look for d.id=10 → Found "Engineering"
--         Output: (Dave, Engineering)

-- Final Result:
-- | name  | dept        |
-- |-------|-------------|
-- | Alice | Engineering |
-- | Bob   | Marketing   |
-- | Eve   | NULL        |  ← LEFT JOIN keeps unmatched left rows
-- | Dave  | Engineering |

-- Note: Finance (id=30) doesn't appear — no employee references it
-- If we wanted Finance too → use FULL OUTER JOIN or RIGHT JOIN
```

### JOIN vs EXISTS vs IN Performance

```sql
-- Scenario: Find customers who have at least one order

-- Method 1: JOIN (may produce duplicates if multiple orders)
SELECT DISTINCT c.name
FROM customers c
INNER JOIN orders o ON c.id = o.customer_id;
-- Reads: all matching pairs, then deduplicates
-- Problem: if customer has 100 orders, creates 100 rows then deduplicates

-- Method 2: EXISTS (semi-join, stops at first match)
SELECT c.name
FROM customers c
WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);
-- For each customer, checks if ANY order exists → stops immediately on first find
-- More efficient when there are many orders per customer

-- Method 3: IN
SELECT c.name
FROM customers c
WHERE c.id IN (SELECT customer_id FROM orders);
-- Similar to EXISTS, optimizer often converts to same plan

-- RECOMMENDATION: Use EXISTS for "exists at least one" checks
```

---

## Complexity

| Join Type | Without Index | With Index on Join Column |
|-----------|--------------|---------------------------|
| Nested Loop | O(n × m) | O(n × log m) |
| Hash Join | O(n + m) | O(n + m) — index not needed |
| Merge Join | O(n log n + m log m) | O(n + m) — already sorted |
| CROSS JOIN | O(n × m) | Always O(n × m) |

**Optimizer typically chooses:**
- Small × Large (with index) → Nested Loop
- Large × Large (no index) → Hash Join
- Large × Large (both indexed) → Merge Join

---

## Real Project Usage

### Spring Boot JPA Fetch Join

```java
// N+1 problem and solution
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;
    
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items;
}

// JPQL Fetch Join — solves N+1
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.customer " +
       "JOIN FETCH o.items " +
       "WHERE o.status = :status")
List<Order> findByStatusWithDetails(@Param("status") String status);
```

### Reporting Query with Multiple Joins

```sql
-- Product performance report
SELECT 
    p.id,
    p.name,
    p.sku,
    c.name AS category,
    COUNT(DISTINCT o.id) AS order_count,
    SUM(oi.quantity) AS units_sold,
    SUM(oi.quantity * oi.unit_price) AS gross_revenue,
    ROUND(AVG(r.rating), 2) AS avg_rating,
    COUNT(DISTINCT r.id) AS review_count,
    p.stock AS current_stock,
    CASE 
        WHEN p.stock = 0 THEN 'OUT_OF_STOCK'
        WHEN p.stock < SUM(oi.quantity) / 12 THEN 'LOW_STOCK'
        ELSE 'ADEQUATE'
    END AS stock_status
FROM products p
LEFT JOIN categories c ON p.category_id = c.id
LEFT JOIN order_items oi ON p.id = oi.product_id
LEFT JOIN orders o ON oi.order_id = o.id AND o.status = 'completed'
LEFT JOIN reviews r ON p.id = r.product_id
WHERE p.is_active = TRUE
GROUP BY p.id, p.name, p.sku, c.name, p.stock
ORDER BY gross_revenue DESC NULLS LAST
LIMIT 50;
```

---

## Interview Questions & Answers

**Q1: What's the difference between JOIN and subquery? When to use which?**

| Aspect | JOIN | Subquery |
|--------|------|----------|
| Returns | Combined columns from both tables | Single result set |
| Duplicates | Can create duplicates | Typically no duplicates |
| Performance | Often faster (optimizer) | Correlated subqueries can be slow |
| Readability | Better for combining data | Better for filtering conditions |

Use JOIN when: You need columns from multiple tables
Use subquery when: You need to check existence or compare against aggregates

**Q2: Explain LEFT JOIN with NULL filtering. What does `WHERE right.id IS NULL` accomplish?**

It finds rows from the LEFT table that have NO match in the RIGHT table. It's an anti-join pattern.

```sql
-- Find products that have never been ordered
SELECT p.name
FROM products p
LEFT JOIN order_items oi ON p.id = oi.product_id
WHERE oi.id IS NULL;
```

**Q3: Write a query to find the second highest salary per department.**

```sql
-- Using window function with JOIN
WITH ranked AS (
    SELECT 
        name, department, salary,
        DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employees
)
SELECT name, department, salary
FROM ranked
WHERE rnk = 2;
```

**Q4: What's the difference between `ON` and `WHERE` in a LEFT JOIN?**

```sql
-- Condition in ON: doesn't eliminate left rows
SELECT e.name, d.name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id AND d.is_active = TRUE;
-- Returns ALL employees. Department is NULL if dept is inactive.

-- Condition in WHERE: eliminates left rows
SELECT e.name, d.name
FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id
WHERE d.is_active = TRUE;
-- Only returns employees whose department is active.
-- CONVERTS LEFT JOIN to INNER JOIN behavior!
```

**Q5: How do you optimize a slow JOIN query?**

1. Add indexes on join columns (both sides)
2. Filter early with WHERE (reduce rows before joining)
3. Use EXISTS instead of JOIN + DISTINCT for existence checks
4. Ensure statistics are up to date (ANALYZE)
5. Consider denormalization for read-heavy queries
6. Check EXPLAIN for chosen algorithm; adjust work_mem for hash joins

---

## Follow-up Questions & Answers

**Q: Can you JOIN on non-equality conditions?**
Yes. Non-equi joins use `<`, `>`, `BETWEEN`, etc.
```sql
-- Find employees hired before their manager
SELECT e.name, m.name
FROM employees e
JOIN employees m ON e.manager_id = m.id AND e.hire_date < m.hire_date;
```

**Q: What happens with NULL in JOIN conditions?**
NULL never equals anything (including NULL). Rows with NULL in join columns will NOT match.

**Q: What's a LATERAL JOIN?**
PostgreSQL feature allowing the right side of a join to reference columns from the left side:
```sql
SELECT d.name, top_emp.name, top_emp.salary
FROM departments d
CROSS JOIN LATERAL (
    SELECT name, salary FROM employees
    WHERE dept_id = d.id
    ORDER BY salary DESC LIMIT 3
) top_emp;
```

---

## Common Mistakes

1. **LEFT JOIN converted to INNER by WHERE clause**:
   ```sql
   -- WRONG: WHERE on right table column nullifies LEFT JOIN
   FROM a LEFT JOIN b ON a.id = b.a_id WHERE b.status = 'active'
   -- CORRECT: Put condition in ON clause
   FROM a LEFT JOIN b ON a.id = b.a_id AND b.status = 'active'
   ```

2. **Accidental Cartesian product** (missing JOIN condition):
   ```sql
   -- WRONG: No join condition → millions of rows
   FROM orders, customers
   -- CORRECT:
   FROM orders JOIN customers ON orders.customer_id = customers.id
   ```

3. **COUNT(*) with LEFT JOIN** counting NULLs:
   ```sql
   -- WRONG: Counts departments without employees as 1
   SELECT d.name, COUNT(*) FROM departments d LEFT JOIN employees e ON ...
   -- CORRECT: Count non-null values from right table
   SELECT d.name, COUNT(e.id) FROM departments d LEFT JOIN employees e ON ...
   ```

4. **Joining without indexes** on large tables → full table scans

5. **Over-joining** — fetching more data than needed:
   ```sql
   -- If you only need product names, don't join order_items
   ```

---

## Best Practices

1. **Always index foreign key columns** — they're used in joins
2. **Use explicit JOIN syntax** instead of comma-separated FROM with WHERE conditions
3. **Use table aliases** for readability
4. **Put filter conditions in the right place**: ON for join logic, WHERE for row filtering
5. **Use EXISTS for existence checks** instead of JOIN + DISTINCT
6. **Limit columns** in SELECT — don't `SELECT *` with JOINs
7. **ANALYZE tables** regularly for optimizer to make good join strategy choices

---

## Production Considerations

1. **Index both sides of join**: Create indexes on FK columns AND PK columns
2. **Monitor join spill**: If hash join spills to disk, increase `work_mem`
3. **Parallel query**: PostgreSQL can parallelize hash joins on large tables
4. **Materialized views**: Pre-join frequently accessed combinations
5. **Denormalization**: For read-heavy workloads, consider adding redundant columns

```sql
-- Check join performance
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders o
JOIN customers c ON o.customer_id = c.id
WHERE o.created_at > '2024-01-01';

-- Look for:
-- - Nested Loop with large outer → consider Hash Join (increase work_mem)
-- - Seq Scan on join column → add index
-- - High "actual time" → bottleneck identified
```

---

## Related Topics
- [Topic 8: Subqueries](#)
- [Topic 9: Set Operations](#)
- [Topic 10: Window Functions](#)
- [Topic 18: Indexes](#)
- [Topic 19: Query Optimization](#)
