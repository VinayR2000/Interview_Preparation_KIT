# Topic 3: SELECT Fundamentals

## Theory

SELECT is the most used SQL statement. It retrieves data from one or more tables without modifying anything.

### Execution Order (Logical)

```
FROM       → Which tables to query
JOIN       → Combine tables
WHERE      → Filter rows BEFORE grouping
GROUP BY   → Group rows
HAVING     → Filter groups AFTER grouping
SELECT     → Choose columns / compute expressions
DISTINCT   → Remove duplicates
ORDER BY   → Sort results
LIMIT/OFFSET → Paginate results
```

**Critical**: SQL does NOT execute in the order you write it.

```
Written Order:    SELECT → FROM → WHERE → GROUP BY → HAVING → ORDER BY → LIMIT
Execution Order:  FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT
```

This is why you can't use a column alias in WHERE (it hasn't been computed yet) but CAN use it in ORDER BY.

### Core Clauses

| Clause | Purpose | Required |
|--------|---------|----------|
| SELECT | Columns to return | Yes |
| FROM | Source table(s) | Yes (except for computed values) |
| WHERE | Row-level filter | No |
| DISTINCT | Remove duplicate rows | No |
| ORDER BY | Sort results | No |
| LIMIT/OFFSET | Pagination | No |

---

## Internal Working

### Query Processing Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│    Parser    │ →  │   Analyzer   │ →  │   Rewriter   │
│  (Syntax)    │    │  (Semantics) │    │  (Rules/Views)│
└──────────────┘    └──────────────┘    └──────────────┘
                                              │
                                              ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Executor   │ ←  │   Planner    │ ←  │  Optimizer   │
│  (Run Plan)  │    │ (Best Plan)  │    │ (Cost-Based) │
└──────────────┘    └──────────────┘    └──────────────┘
```

1. **Parser**: Checks SQL syntax, creates parse tree
2. **Analyzer**: Resolves table/column names, checks types
3. **Rewriter**: Expands views, applies rules
4. **Optimizer**: Generates possible execution plans, picks lowest-cost
5. **Planner**: Creates detailed execution plan
6. **Executor**: Runs the plan, returns results

---

## Code Examples

### Basic SELECT

```sql
-- Select all columns
SELECT * FROM employees;

-- Select specific columns
SELECT first_name, last_name, salary FROM employees;

-- Computed expressions
SELECT 
    first_name || ' ' || last_name AS full_name,
    salary,
    salary * 12 AS annual_salary,
    salary * 12 * 0.7 AS after_tax_annual
FROM employees;

-- Without FROM (PostgreSQL)
SELECT NOW(), CURRENT_DATE, 2 + 3 AS sum;
```

### WHERE Clause — Filtering

```sql
-- Comparison operators
SELECT * FROM employees WHERE salary > 80000;
SELECT * FROM employees WHERE department = 'Engineering';
SELECT * FROM employees WHERE hire_date >= '2023-01-01';

-- AND, OR, NOT
SELECT * FROM employees
WHERE department = 'Engineering'
  AND salary > 90000
  AND is_active = TRUE;

SELECT * FROM employees
WHERE department = 'Engineering'
   OR department = 'Product';

SELECT * FROM employees
WHERE NOT (department = 'HR');

-- Operator precedence: NOT > AND > OR
-- These are DIFFERENT:
SELECT * FROM employees
WHERE department = 'Engineering' OR department = 'Product'
  AND salary > 100000;
-- Evaluates as: Engineering OR (Product AND salary > 100000)

SELECT * FROM employees
WHERE (department = 'Engineering' OR department = 'Product')
  AND salary > 100000;
-- Evaluates as: (Engineering OR Product) AND salary > 100000
```

### IN and NOT IN

```sql
-- IN — equivalent to multiple OR conditions
SELECT * FROM employees
WHERE department IN ('Engineering', 'Product', 'Design');

-- NOT IN — warning about NULLs!
SELECT * FROM employees
WHERE department NOT IN ('HR', 'Finance');
-- If department has NULL values, those rows are EXCLUDED (NULL NOT IN = NULL)

-- Safe alternative when NULLs exist:
SELECT * FROM employees
WHERE department NOT IN ('HR', 'Finance')
   OR department IS NULL;

-- IN with subquery
SELECT * FROM employees
WHERE dept_id IN (
    SELECT id FROM departments WHERE budget > 1000000
);
```

### BETWEEN

```sql
-- BETWEEN is inclusive on both ends
SELECT * FROM employees
WHERE salary BETWEEN 70000 AND 100000;
-- Equivalent to: salary >= 70000 AND salary <= 100000

-- Date ranges
SELECT * FROM orders
WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31';
-- WARNING: For timestamps, '2024-12-31' means '2024-12-31 00:00:00'
-- Use: created_at BETWEEN '2024-01-01' AND '2024-12-31 23:59:59'
-- Or better: created_at >= '2024-01-01' AND created_at < '2025-01-01'
```

### LIKE and Pattern Matching

```sql
-- LIKE patterns: % = any characters, _ = single character
SELECT * FROM employees WHERE email LIKE '%@company.com';
SELECT * FROM products WHERE sku LIKE 'ELEC-___';  -- exactly 3 chars after ELEC-
SELECT * FROM employees WHERE name LIKE 'J%';       -- starts with J

-- ILIKE — case-insensitive (PostgreSQL)
SELECT * FROM products WHERE name ILIKE '%widget%';

-- Escaping wildcards
SELECT * FROM products WHERE name LIKE '20\%% off' ESCAPE '\';

-- Regular expressions (PostgreSQL)
SELECT * FROM employees WHERE email ~ '^[a-z]+\.[a-z]+@company\.com$';
SELECT * FROM employees WHERE name ~* 'john';  -- case-insensitive regex
```

### NULL Handling

```sql
-- IS NULL / IS NOT NULL
SELECT * FROM employees WHERE manager_id IS NULL;  -- top-level managers
SELECT * FROM employees WHERE phone IS NOT NULL;

-- COALESCE — returns first non-null value
SELECT 
    name,
    COALESCE(phone, email, 'N/A') AS contact
FROM employees;

-- NULLIF — returns NULL if two values are equal (useful for avoiding division by zero)
SELECT 
    department,
    total_revenue / NULLIF(employee_count, 0) AS revenue_per_employee
FROM department_stats;

-- CASE with NULL
SELECT 
    name,
    CASE 
        WHEN bonus IS NULL THEN 'No Bonus'
        WHEN bonus > 10000 THEN 'High'
        WHEN bonus > 5000 THEN 'Medium'
        ELSE 'Low'
    END AS bonus_category
FROM employees;
```

### CASE Expressions

```sql
-- Simple CASE
SELECT 
    name,
    status,
    CASE status
        WHEN 'A' THEN 'Active'
        WHEN 'I' THEN 'Inactive'
        WHEN 'T' THEN 'Terminated'
        ELSE 'Unknown'
    END AS status_label
FROM employees;

-- Searched CASE (more flexible)
SELECT 
    name,
    salary,
    CASE
        WHEN salary >= 150000 THEN 'Executive'
        WHEN salary >= 100000 THEN 'Senior'
        WHEN salary >= 70000 THEN 'Mid-Level'
        WHEN salary >= 40000 THEN 'Junior'
        ELSE 'Entry Level'
    END AS salary_band
FROM employees;

-- CASE in WHERE clause
SELECT * FROM orders
WHERE CASE 
    WHEN status = 'pending' THEN created_at > NOW() - INTERVAL '7 days'
    WHEN status = 'processing' THEN created_at > NOW() - INTERVAL '30 days'
    ELSE TRUE
END;

-- CASE in ORDER BY
SELECT * FROM tasks
ORDER BY 
    CASE priority
        WHEN 'critical' THEN 1
        WHEN 'high' THEN 2
        WHEN 'medium' THEN 3
        WHEN 'low' THEN 4
        ELSE 5
    END;
```

### DISTINCT

```sql
-- Remove duplicate rows
SELECT DISTINCT department FROM employees;

-- DISTINCT on multiple columns (unique combinations)
SELECT DISTINCT department, job_title FROM employees;

-- DISTINCT ON (PostgreSQL) — first row per group
SELECT DISTINCT ON (department)
    department, name, salary
FROM employees
ORDER BY department, salary DESC;
-- Returns the highest-paid employee from each department

-- COUNT with DISTINCT
SELECT COUNT(DISTINCT department) AS dept_count FROM employees;
```

### Type Casting

```sql
-- CAST syntax (SQL standard)
SELECT CAST('2024-01-15' AS DATE);
SELECT CAST(123.456 AS INTEGER);  -- 123 (truncates)
SELECT CAST(price AS INTEGER) FROM products;

-- :: syntax (PostgreSQL shorthand)
SELECT '2024-01-15'::DATE;
SELECT '123.456'::NUMERIC(5,2);
SELECT id::TEXT FROM employees;

-- Common conversions
SELECT 
    '100'::INTEGER + 50,                    -- String to int
    NOW()::DATE,                            -- Timestamp to date
    amount::NUMERIC(10,2),                  -- Force precision
    EXTRACT(YEAR FROM hire_date)::INTEGER   -- Extract and cast
FROM employees;
```

---

## Dry Run

### Execution Order Example

```sql
-- Given table employees:
-- | id | name    | dept        | salary | bonus |
-- |----|---------|-------------|--------|-------|
-- | 1  | Alice   | Engineering | 95000  | 5000  |
-- | 2  | Bob     | Marketing   | 75000  | NULL  |
-- | 3  | Charlie | Engineering | 88000  | 3000  |
-- | 4  | Diana   | Engineering | 105000 | 8000  |
-- | 5  | Eve     | Marketing   | 72000  | 2000  |

SELECT 
    dept AS department,
    COUNT(*) AS emp_count,
    AVG(salary) AS avg_salary
FROM employees
WHERE salary > 74000
GROUP BY dept
HAVING COUNT(*) > 1
ORDER BY avg_salary DESC;

-- Execution steps:
-- 1. FROM employees → load all 5 rows
-- 2. WHERE salary > 74000 → filter
--    Keep: Alice(95k), Bob(75k), Charlie(88k), Diana(105k)
--    Remove: Eve(72k)
--    Result: 4 rows
-- 3. GROUP BY dept →
--    Engineering: [Alice, Charlie, Diana]
--    Marketing: [Bob]
-- 4. HAVING COUNT(*) > 1 →
--    Engineering: COUNT=3 ✓
--    Marketing: COUNT=1 ✗ (removed)
-- 5. SELECT dept AS department, COUNT(*), AVG(salary) →
--    | department  | emp_count | avg_salary |
--    |-------------|-----------|------------|
--    | Engineering | 3         | 96000      |
-- 6. ORDER BY avg_salary DESC → (only 1 row, no change)

-- Final result:
-- | department  | emp_count | avg_salary |
-- |-------------|-----------|------------|
-- | Engineering | 3         | 96000      |
```

---

## Complexity

| Operation | Without Index | With Index |
|-----------|--------------|------------|
| SELECT * (full scan) | O(n) | O(n) |
| WHERE on indexed col | O(n) | O(log n) |
| WHERE with LIKE 'abc%' | O(n) | O(log n) with B-tree |
| WHERE with LIKE '%abc' | O(n) | O(n) — can't use index |
| DISTINCT | O(n log n) | O(n) if index covers |
| ORDER BY indexed col | O(n log n) | O(n) — pre-sorted |

---

## Real Project Usage

### Building a Product Search API

```sql
-- Spring Boot repository query for product search
-- Handles: search term, category filter, price range, sorting, pagination

SELECT 
    p.id,
    p.name,
    p.sku,
    p.price,
    p.stock,
    c.name AS category_name,
    p.created_at,
    CASE 
        WHEN p.stock = 0 THEN 'OUT_OF_STOCK'
        WHEN p.stock < 10 THEN 'LOW_STOCK'
        ELSE 'IN_STOCK'
    END AS availability
FROM products p
LEFT JOIN categories c ON p.category_id = c.id
WHERE p.is_active = TRUE
  AND (p.name ILIKE '%' || :searchTerm || '%' OR p.sku ILIKE '%' || :searchTerm || '%')
  AND (:categoryId IS NULL OR p.category_id = :categoryId)
  AND p.price BETWEEN COALESCE(:minPrice, 0) AND COALESCE(:maxPrice, 999999.99)
ORDER BY 
    CASE WHEN :sortBy = 'price_asc' THEN p.price END ASC,
    CASE WHEN :sortBy = 'price_desc' THEN p.price END DESC,
    CASE WHEN :sortBy = 'newest' THEN p.created_at END DESC,
    p.name ASC
LIMIT :pageSize OFFSET :offset;
```

### Dashboard Statistics Query

```sql
-- Admin dashboard: today's metrics
SELECT 
    COUNT(*) AS total_orders,
    COUNT(*) FILTER (WHERE status = 'completed') AS completed_orders,
    COUNT(*) FILTER (WHERE status = 'pending') AS pending_orders,
    COALESCE(SUM(total_amount), 0) AS total_revenue,
    COALESCE(AVG(total_amount), 0) AS avg_order_value,
    COUNT(DISTINCT customer_id) AS unique_customers
FROM orders
WHERE created_at >= CURRENT_DATE
  AND created_at < CURRENT_DATE + INTERVAL '1 day';
```

---

## Interview Questions & Answers

**Q1: Explain the logical order of SQL query execution.**

FROM → JOIN → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT

Key implications:
- Can't use SELECT alias in WHERE (SELECT runs after WHERE)
- CAN use SELECT alias in ORDER BY (ORDER BY runs after SELECT)
- HAVING can only filter on aggregated/grouped values
- WHERE filters before grouping (more efficient)

**Q2: What's the difference between WHERE and HAVING?**

| Aspect | WHERE | HAVING |
|--------|-------|--------|
| Filters | Individual rows | Groups |
| Timing | Before GROUP BY | After GROUP BY |
| Aggregates | Cannot use | Can use |
| Performance | Better (fewer rows to group) | Worse (groups first, then filters) |

```sql
-- WHERE: filter rows before grouping
SELECT dept, AVG(salary) FROM employees
WHERE hire_date > '2020-01-01'  -- filter rows first
GROUP BY dept;

-- HAVING: filter groups after grouping
SELECT dept, AVG(salary) FROM employees
GROUP BY dept
HAVING AVG(salary) > 80000;  -- filter groups
```

**Q3: Why is `NOT IN` dangerous with NULLs?**

```sql
-- If subquery returns {1, 2, NULL}:
WHERE id NOT IN (1, 2, NULL)
-- Becomes: id <> 1 AND id <> 2 AND id <> NULL
-- Since id <> NULL is always NULL (unknown), 
-- the entire condition is never TRUE
-- Result: 0 rows returned!

-- Solution: Use NOT EXISTS instead
WHERE NOT EXISTS (SELECT 1 FROM other WHERE other.id = main.id)
```

**Q4: What's the difference between `=` and `IS NOT DISTINCT FROM`?**

```sql
-- Standard comparison: NULL = NULL → NULL (falsy)
SELECT * FROM t WHERE a = b;  -- Rows where both are NULL won't match

-- IS NOT DISTINCT FROM: NULL-safe comparison
SELECT * FROM t WHERE a IS NOT DISTINCT FROM b;  -- NULL = NULL → TRUE
```

**Q5: How does DISTINCT ON work in PostgreSQL?**

```sql
-- Returns first row for each unique value of the specified columns
SELECT DISTINCT ON (customer_id)
    customer_id, order_id, amount, created_at
FROM orders
ORDER BY customer_id, created_at DESC;
-- Returns the MOST RECENT order for each customer
-- The ORDER BY determines which row is "first" per group
```

---

## Follow-up Questions & Answers

**Q: Can you use CASE in a JOIN condition?**
Yes, but it's unusual and can hurt readability and performance.

**Q: Is there a performance difference between `column = value` and `value = column`?**
No. The optimizer treats them identically.

**Q: What's the difference between `<>` and `!=`?**
Functionally identical. `<>` is SQL standard, `!=` is widely supported but not standard.

---

## Common Mistakes

1. **Using `SELECT *` in production** — fetches unnecessary data, breaks if schema changes
2. **Incorrect NULL handling in NOT IN**
3. **Confusing WHERE vs HAVING**
4. **Date range off-by-one errors** with BETWEEN on timestamps
5. **Assuming ORDER BY without explicit ORDER BY** — SQL makes no ordering guarantee
6. **Using column aliases in WHERE** — not allowed (execution order)
7. **Operator precedence** — AND binds tighter than OR; use parentheses

---

## Best Practices

1. **Always specify columns** instead of `SELECT *`
2. **Use parameterized queries** for user input (prevent SQL injection)
3. **Use `IS NOT DISTINCT FROM`** for NULL-safe comparisons
4. **Use date ranges with >= and <** instead of BETWEEN for timestamps
5. **Always include ORDER BY** if order matters (never rely on default order)
6. **Use NOT EXISTS** instead of NOT IN when subquery might return NULLs
7. **Index columns** used in WHERE, JOIN, and ORDER BY

---

## Production Considerations

1. **Query timeout**: Set `statement_timeout` to prevent runaway queries
2. **Result set size**: Always LIMIT results for user-facing queries
3. **Prepared statements**: Use for repeated queries (query plan caching)
4. **Connection pool**: Don't hold connections while processing results
5. **Explain plan**: Check execution plan for any query hitting production

```sql
-- Always check your query plan
EXPLAIN ANALYZE
SELECT * FROM products
WHERE category_id = 5 AND price > 100
ORDER BY created_at DESC
LIMIT 20;
```

---

## Related Topics
- [Topic 4: Sorting & Pagination](#)
- [Topic 5: SQL Functions](#)
- [Topic 6: Grouping](#)
- [Topic 7: Joins](#)
