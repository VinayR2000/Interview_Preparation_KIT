# Topic 6: Grouping (GROUP BY & HAVING)

## Theory

GROUP BY collapses rows with identical values in specified columns into a single summary row. Combined with aggregate functions, it produces statistics per group.

### Execution Order

```
FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT
                  ↑           ↑
              Groups rows   Filters groups
```

### Rules
1. Every non-aggregated column in SELECT must appear in GROUP BY
2. WHERE filters individual rows BEFORE grouping
3. HAVING filters groups AFTER aggregation
4. Column aliases from SELECT cannot be used in WHERE or HAVING (execution order)
5. ORDER BY can use aliases and aggregates

---

## Internal Working

### How GROUP BY Executes

```
┌─────────────────────────────────────────────────────┐
│              GROUP BY Execution                       │
├─────────────────────────────────────────────────────┤
│                                                       │
│  Strategy 1: HASH AGGREGATE                          │
│  ┌─────────────────────────────────────────────┐     │
│  │ 1. Scan all rows                             │    │
│  │ 2. Hash each group key                       │    │
│  │ 3. Accumulate aggregates in hash buckets     │    │
│  │ 4. Output all buckets                        │    │
│  │ Memory: O(groups) | Time: O(n)              │    │
│  │ Best for: Few distinct groups                │    │
│  └─────────────────────────────────────────────┘     │
│                                                       │
│  Strategy 2: SORT + GROUP AGGREGATE                  │
│  ┌─────────────────────────────────────────────┐     │
│  │ 1. Sort rows by group key                    │    │
│  │ 2. Sequential scan through sorted data       │    │
│  │ 3. Accumulate until group key changes        │    │
│  │ 4. Output group, start next                  │    │
│  │ Memory: O(1) per group | Time: O(n log n)   │    │
│  │ Best for: Already sorted (index) or many groups│  │
│  └─────────────────────────────────────────────┘     │
│                                                       │
└─────────────────────────────────────────────────────┘
```

### EXPLAIN Comparison

```sql
-- Hash Aggregate (optimizer chooses for few groups)
EXPLAIN SELECT department, COUNT(*) FROM employees GROUP BY department;
-- HashAggregate (cost=15.00..15.05 rows=5)
--   Group Key: department
--   -> Seq Scan on employees (cost=0.00..12.50 rows=500)

-- Sort + Group Aggregate (many distinct groups)
EXPLAIN SELECT customer_id, SUM(amount) FROM orders GROUP BY customer_id;
-- GroupAggregate (cost=120.00..130.00 rows=10000)
--   Group Key: customer_id
--   -> Sort (cost=100.00..110.00 rows=100000)
--        Sort Key: customer_id
--        -> Seq Scan on orders
```

---

## Code Examples

### Basic GROUP BY

```sql
-- Count employees per department
SELECT department, COUNT(*) AS employee_count
FROM employees
GROUP BY department;

-- Multiple aggregates per group
SELECT 
    department,
    COUNT(*) AS headcount,
    SUM(salary) AS total_payroll,
    AVG(salary) AS avg_salary,
    MIN(salary) AS min_salary,
    MAX(salary) AS max_salary
FROM employees
GROUP BY department
ORDER BY avg_salary DESC;

-- Group by multiple columns
SELECT 
    department,
    job_title,
    COUNT(*) AS count,
    AVG(salary) AS avg_salary
FROM employees
GROUP BY department, job_title
ORDER BY department, avg_salary DESC;
```

### HAVING — Filter Groups

```sql
-- Departments with more than 10 employees
SELECT department, COUNT(*) AS count
FROM employees
GROUP BY department
HAVING COUNT(*) > 10;

-- Departments where average salary exceeds 80000
SELECT department, AVG(salary) AS avg_sal
FROM employees
GROUP BY department
HAVING AVG(salary) > 80000;

-- Combined WHERE + HAVING
SELECT 
    department,
    COUNT(*) AS active_count,
    AVG(salary) AS avg_salary
FROM employees
WHERE is_active = TRUE           -- Filter rows first
GROUP BY department
HAVING COUNT(*) >= 5             -- Then filter groups
   AND AVG(salary) > 70000
ORDER BY avg_salary DESC;
```

### WHERE vs HAVING

```sql
-- WHERE: Filters rows BEFORE grouping (more efficient)
SELECT department, AVG(salary)
FROM employees
WHERE hire_date > '2020-01-01'   -- Only recent hires
GROUP BY department;

-- HAVING: Filters groups AFTER aggregation
SELECT department, AVG(salary)
FROM employees
GROUP BY department
HAVING AVG(salary) > 80000;     -- Only high-paying departments

-- WRONG: Using aggregate in WHERE
SELECT department, AVG(salary)
FROM employees
WHERE AVG(salary) > 80000    -- ERROR! Can't use aggregate in WHERE
GROUP BY department;
```

### Conditional Aggregation

```sql
-- Pivot-style report using CASE
SELECT 
    department,
    COUNT(*) AS total,
    COUNT(*) FILTER (WHERE salary >= 100000) AS senior_count,
    COUNT(*) FILTER (WHERE salary < 100000 AND salary >= 70000) AS mid_count,
    COUNT(*) FILTER (WHERE salary < 70000) AS junior_count,
    ROUND(AVG(salary), 2) AS avg_salary
FROM employees
GROUP BY department;

-- Monthly breakdown in columns
SELECT 
    EXTRACT(YEAR FROM created_at) AS year,
    SUM(amount) FILTER (WHERE EXTRACT(MONTH FROM created_at) = 1) AS jan,
    SUM(amount) FILTER (WHERE EXTRACT(MONTH FROM created_at) = 2) AS feb,
    SUM(amount) FILTER (WHERE EXTRACT(MONTH FROM created_at) = 3) AS mar,
    SUM(amount) AS total
FROM orders
GROUP BY EXTRACT(YEAR FROM created_at)
ORDER BY year;

-- Boolean aggregation
SELECT 
    order_id,
    BOOL_AND(is_shipped) AS all_shipped,  -- TRUE if all items shipped
    BOOL_OR(is_returned) AS any_returned  -- TRUE if any item returned
FROM order_items
GROUP BY order_id;
```

### Advanced GROUP BY

```sql
-- GROUPING SETS — multiple groupings in one query
SELECT 
    COALESCE(department, 'ALL') AS department,
    COALESCE(job_title, 'ALL') AS job_title,
    COUNT(*) AS count,
    AVG(salary) AS avg_salary
FROM employees
GROUP BY GROUPING SETS (
    (department, job_title),  -- Group by both
    (department),             -- Subtotal by department
    ()                        -- Grand total
)
ORDER BY department NULLS LAST, job_title NULLS LAST;

-- ROLLUP — hierarchical subtotals (parent → child)
SELECT 
    COALESCE(region, 'TOTAL') AS region,
    COALESCE(country, 'SUBTOTAL') AS country,
    SUM(revenue) AS total_revenue
FROM sales
GROUP BY ROLLUP (region, country);
-- Produces: (region, country), (region), ()

-- CUBE — all possible combinations
SELECT 
    department,
    quarter,
    SUM(revenue) AS total_revenue
FROM department_revenue
GROUP BY CUBE (department, quarter);
-- Produces: (dept, quarter), (dept), (quarter), ()
```

---

## Diagram

### GROUP BY Visual Flow

```
Input Table (employees):
┌─────┬────────────┬────────┐
│ id  │ department │ salary │
├─────┼────────────┼────────┤
│ 1   │ Eng        │ 90000  │
│ 2   │ Mkt        │ 75000  │
│ 3   │ Eng        │ 85000  │
│ 4   │ Eng        │ 95000  │
│ 5   │ Mkt        │ 72000  │
└─────┴────────────┴────────┘
         │
         ▼ GROUP BY department
         
┌──────────────────────────────────┐
│ Group: Eng                        │
│ Rows: [90000, 85000, 95000]      │
│ COUNT(*) = 3                      │
│ AVG(salary) = 90000               │
│ SUM(salary) = 270000              │
├──────────────────────────────────┤
│ Group: Mkt                        │
│ Rows: [75000, 72000]             │
│ COUNT(*) = 2                      │
│ AVG(salary) = 73500               │
│ SUM(salary) = 147000              │
└──────────────────────────────────┘
         │
         ▼ HAVING COUNT(*) > 2
         
┌──────────────────────────────────┐
│ Group: Eng                        │  ← Only Eng passes
│ COUNT(*) = 3, AVG = 90000        │
└──────────────────────────────────┘

Result:
┌────────────┬───────┬───────────┐
│ department │ count │ avg_salary│
├────────────┼───────┼───────────┤
│ Eng        │ 3     │ 90000     │
└────────────┴───────┴───────────┘
```

---

## Dry Run

### Complex Grouping Query

```sql
-- orders table:
-- | id | customer_id | status    | amount | created_at          |
-- |----|-------------|-----------|--------|---------------------|
-- | 1  | 101         | completed | 150.00 | 2024-01-15 10:00:00 |
-- | 2  | 102         | completed | 200.00 | 2024-01-20 12:00:00 |
-- | 3  | 101         | cancelled | 75.00  | 2024-02-01 09:00:00 |
-- | 4  | 103         | completed | 300.00 | 2024-02-15 14:00:00 |
-- | 5  | 101         | completed | 180.00 | 2024-02-20 11:00:00 |
-- | 6  | 102         | pending   | 90.00  | 2024-03-01 08:00:00 |

SELECT 
    customer_id,
    COUNT(*) AS total_orders,
    COUNT(*) FILTER (WHERE status = 'completed') AS completed,
    SUM(amount) FILTER (WHERE status = 'completed') AS revenue
FROM orders
WHERE created_at >= '2024-01-01'
GROUP BY customer_id
HAVING COUNT(*) FILTER (WHERE status = 'completed') >= 1
ORDER BY revenue DESC NULLS LAST;

-- Step 1: FROM orders → all 6 rows
-- Step 2: WHERE created_at >= '2024-01-01' → all 6 pass
-- Step 3: GROUP BY customer_id:
--   customer 101: rows [1, 3, 5]
--   customer 102: rows [2, 6]
--   customer 103: rows [4]
-- Step 4: Compute aggregates:
--   101: total=3, completed=2 (rows 1,5), revenue=150+180=330
--   102: total=2, completed=1 (row 2), revenue=200
--   103: total=1, completed=1 (row 4), revenue=300
-- Step 5: HAVING completed >= 1 → all pass
-- Step 6: ORDER BY revenue DESC:
--   101: 330, 103: 300, 102: 200

-- Result:
-- | customer_id | total_orders | completed | revenue |
-- |-------------|--------------|-----------|---------|
-- | 101         | 3            | 2         | 330.00  |
-- | 103         | 1            | 1         | 300.00  |
-- | 102         | 2            | 1         | 200.00  |
```

---

## Complexity

| Scenario | Time Complexity | Space Complexity |
|----------|----------------|------------------|
| Hash Aggregate (few groups) | O(n) | O(g) where g = groups |
| Sort Aggregate (no index) | O(n log n) | O(n) for sort |
| Sort Aggregate (with index) | O(n) | O(1) |
| HAVING filter | O(g) | O(1) |
| GROUPING SETS with k sets | O(n × k) | O(g × k) |

---

## Real Project Usage

### Sales Report with Rollup

```sql
-- Monthly sales report with subtotals
SELECT 
    COALESCE(TO_CHAR(DATE_TRUNC('month', o.created_at), 'YYYY-MM'), 'TOTAL') AS month,
    COALESCE(c.name, 'ALL CATEGORIES') AS category,
    COUNT(DISTINCT o.id) AS order_count,
    COUNT(DISTINCT o.customer_id) AS unique_customers,
    SUM(oi.quantity * oi.unit_price) AS gross_revenue,
    SUM(oi.quantity * oi.unit_price * (1 - COALESCE(oi.discount, 0))) AS net_revenue
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
JOIN categories c ON p.category_id = c.id
WHERE o.status = 'completed'
  AND o.created_at >= DATE_TRUNC('year', CURRENT_DATE)
GROUP BY ROLLUP (
    DATE_TRUNC('month', o.created_at),
    c.name
)
ORDER BY month NULLS LAST, net_revenue DESC;
```

### Customer Segmentation

```sql
-- RFM (Recency, Frequency, Monetary) Analysis
WITH customer_metrics AS (
    SELECT 
        customer_id,
        MAX(created_at) AS last_order_date,
        COUNT(*) AS order_count,
        SUM(total_amount) AS total_spent,
        AVG(total_amount) AS avg_order_value,
        EXTRACT(DAY FROM NOW() - MAX(created_at)) AS days_since_last_order
    FROM orders
    WHERE status = 'completed'
    GROUP BY customer_id
)
SELECT 
    CASE 
        WHEN days_since_last_order <= 30 AND order_count >= 5 AND total_spent >= 1000 THEN 'VIP'
        WHEN days_since_last_order <= 60 AND order_count >= 3 THEN 'Loyal'
        WHEN days_since_last_order <= 90 THEN 'Active'
        WHEN days_since_last_order <= 180 THEN 'At Risk'
        ELSE 'Churned'
    END AS segment,
    COUNT(*) AS customer_count,
    ROUND(AVG(total_spent), 2) AS avg_lifetime_value,
    ROUND(AVG(order_count), 1) AS avg_orders
FROM customer_metrics
GROUP BY 
    CASE 
        WHEN days_since_last_order <= 30 AND order_count >= 5 AND total_spent >= 1000 THEN 'VIP'
        WHEN days_since_last_order <= 60 AND order_count >= 3 THEN 'Loyal'
        WHEN days_since_last_order <= 90 THEN 'Active'
        WHEN days_since_last_order <= 180 THEN 'At Risk'
        ELSE 'Churned'
    END
ORDER BY avg_lifetime_value DESC;
```

---

## Interview Questions & Answers

**Q1: Can you use WHERE and HAVING in the same query? When would you?**

Yes. Use WHERE to filter individual rows before grouping (more efficient), and HAVING to filter groups after aggregation.

```sql
-- Find departments with more than 5 active employees earning above 70k
SELECT department, COUNT(*) AS count, AVG(salary) AS avg
FROM employees
WHERE is_active = TRUE AND salary > 70000  -- Row filter (before grouping)
GROUP BY department
HAVING COUNT(*) > 5;  -- Group filter (after grouping)
```

**Q2: Why is "SELECT name, department, COUNT(*) FROM employees GROUP BY department" invalid?**

`name` is not in GROUP BY and not aggregated. There are multiple names per department — the database doesn't know which one to display. Every non-aggregated column in SELECT must be in GROUP BY.

**Q3: What's the difference between GROUP BY and DISTINCT?**

| GROUP BY | DISTINCT |
|----------|----------|
| Groups for aggregation | Just removes duplicates |
| Allows aggregates | No aggregates |
| Returns one row per group | Returns one row per unique combination |
| More powerful | Simpler |

```sql
-- These produce the same result:
SELECT DISTINCT department FROM employees;
SELECT department FROM employees GROUP BY department;
-- But only GROUP BY allows: SELECT department, COUNT(*) ...
```

**Q4: Explain GROUPING SETS vs ROLLUP vs CUBE.**

```sql
-- GROUPING SETS: explicitly specify which groupings you want
GROUP BY GROUPING SETS ((a, b), (a), ())
-- Produces: group by (a,b) + group by (a) + grand total

-- ROLLUP: hierarchical (parent→child) subtotals
GROUP BY ROLLUP (a, b)
-- Equivalent to: GROUPING SETS ((a, b), (a), ())
-- Does NOT include group by (b) alone

-- CUBE: ALL combinations
GROUP BY CUBE (a, b)
-- Equivalent to: GROUPING SETS ((a, b), (a), (b), ())
-- 2^n combinations for n columns
```

---

## Common Mistakes

1. **Non-aggregated column in SELECT without GROUP BY**:
   ```sql
   -- WRONG: Which employee name for this department?
   SELECT name, department, AVG(salary) FROM employees GROUP BY department;
   ```

2. **Using WHERE instead of HAVING for aggregate conditions**:
   ```sql
   -- WRONG
   WHERE COUNT(*) > 5
   -- CORRECT
   HAVING COUNT(*) > 5
   ```

3. **GROUP BY column alias** (not supported in standard SQL):
   ```sql
   -- WRONG in most databases (works in MySQL/PostgreSQL):
   SELECT EXTRACT(YEAR FROM date) AS yr FROM orders GROUP BY yr;
   -- SAFE:
   GROUP BY EXTRACT(YEAR FROM date);
   ```

4. **Forgetting NULL forms its own group**:
   ```sql
   -- NULLs in department will form their own group
   SELECT department, COUNT(*) FROM employees GROUP BY department;
   -- Result includes: NULL | 3 (if 3 employees have NULL department)
   ```

---

## Best Practices

1. **Filter with WHERE before GROUP BY** — reduces rows to group (performance)
2. **Use HAVING only for aggregate conditions** — don't filter non-aggregated values there
3. **Consider indexes on GROUP BY columns** — enables sort-based grouping
4. **Use ROLLUP/CUBE** for reports needing subtotals — one query instead of multiple
5. **Use FILTER clause** (PostgreSQL) for conditional aggregation — cleaner than CASE

---

## Production Considerations

1. **work_mem setting**: Hash aggregates need memory; increase for heavy grouping
   ```sql
   SET work_mem = '256MB';  -- For session with heavy aggregation
   ```
2. **Partial aggregation in parallel queries**: PostgreSQL can parallelize GROUP BY
3. **Materialized views for expensive aggregations**: Pre-compute and refresh
4. **Index on GROUP BY columns**: Allows sorted (not hash) aggregation without sort step

```sql
-- Materialized view for expensive report
CREATE MATERIALIZED VIEW monthly_sales AS
SELECT 
    DATE_TRUNC('month', created_at) AS month,
    category_id,
    SUM(amount) AS revenue,
    COUNT(*) AS order_count
FROM orders
GROUP BY DATE_TRUNC('month', created_at), category_id;

CREATE UNIQUE INDEX idx_monthly_sales ON monthly_sales (month, category_id);

-- Refresh periodically
REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_sales;
```

---

## Related Topics
- [Topic 5: SQL Functions](#)
- [Topic 7: Joins](#)
- [Topic 10: Window Functions](#)
- [Topic 11: CTE](#)
