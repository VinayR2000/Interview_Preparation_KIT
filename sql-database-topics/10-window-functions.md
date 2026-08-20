# Topic 10: Window Functions — Must Master

## Theory

Window functions perform calculations across a set of rows **related to the current row** without collapsing them into groups (unlike GROUP BY). They add computed values to each row while preserving all original rows.

### Key Difference: GROUP BY vs Window

```
GROUP BY: 5 rows → 1 row (aggregates and collapses)
Window:   5 rows → 5 rows (computes and adds to each)
```

### Syntax

```sql
function_name() OVER (
    [PARTITION BY column(s)]     -- Define groups (like GROUP BY but keeps rows)
    [ORDER BY column(s)]         -- Define ordering within partition
    [frame_clause]               -- Define window frame
)
```

### Window Function Categories

| Category | Functions |
|----------|-----------|
| **Ranking** | ROW_NUMBER, RANK, DENSE_RANK, NTILE |
| **Value** | LAG, LEAD, FIRST_VALUE, LAST_VALUE, NTH_VALUE |
| **Aggregate** | SUM, AVG, COUNT, MIN, MAX (over window) |

### Ranking Functions Comparison

```
Data: [100, 90, 90, 80, 70]

ROW_NUMBER:  1, 2, 3, 4, 5  ← Always unique (arbitrary for ties)
RANK:        1, 2, 2, 4, 5  ← Same for ties, GAPS after ties
DENSE_RANK:  1, 2, 2, 3, 4  ← Same for ties, NO gaps
NTILE(2):    1, 1, 1, 2, 2  ← Divides into N equal groups
```

---

## Internal Working

```
┌────────────────────────────────────────────────────────────┐
│              Window Function Execution                       │
├────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Execute FROM, WHERE, GROUP BY, HAVING                   │
│  2. Compute result set (all rows available)                 │
│  3. PARTITION BY: Divide rows into windows                  │
│  4. ORDER BY within window: Sort each partition             │
│  5. Frame: Define which rows in the partition to consider   │
│  6. Compute function for each row's frame                   │
│  7. Then apply SELECT (column selection)                    │
│  8. Then ORDER BY (final result ordering)                   │
│                                                              │
│  Execution order:                                           │
│  FROM → WHERE → GROUP BY → HAVING → [WINDOW] → SELECT     │
│  → DISTINCT → ORDER BY → LIMIT                             │
│                                                              │
└────────────────────────────────────────────────────────────┘
```

### Window Frame Explained

```
ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW (default for ORDER BY)
ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING
RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW

Partition: [A, B, C, D, E]  ← Current row = C

UNBOUNDED PRECEDING = A
2 PRECEDING = A
1 PRECEDING = B
CURRENT ROW = C
1 FOLLOWING = D
2 FOLLOWING = E
UNBOUNDED FOLLOWING = E

Frame: ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
→ For row C: considers [B, C, D]
```

---

## Code Examples

### ROW_NUMBER, RANK, DENSE_RANK

```sql
-- All three ranking functions compared
SELECT 
    name,
    department,
    salary,
    ROW_NUMBER() OVER (ORDER BY salary DESC) AS row_num,
    RANK() OVER (ORDER BY salary DESC) AS rank,
    DENSE_RANK() OVER (ORDER BY salary DESC) AS dense_rank
FROM employees;

-- Result:
-- | name    | salary | row_num | rank | dense_rank |
-- |---------|--------|---------|------|------------|
-- | Diana   | 105000 | 1       | 1    | 1          |
-- | Alice   | 95000  | 2       | 2    | 2          |
-- | Charlie | 95000  | 3       | 2    | 2          |  ← Tie!
-- | Bob     | 85000  | 4       | 4    | 3          |  ← RANK skips, DENSE doesn't
-- | Eve     | 70000  | 5       | 5    | 4          |

-- Top 3 earners per department
SELECT * FROM (
    SELECT 
        name, department, salary,
        DENSE_RANK() OVER (
            PARTITION BY department 
            ORDER BY salary DESC
        ) AS dept_rank
    FROM employees
) ranked
WHERE dept_rank <= 3;
```

### NTILE

```sql
-- Divide into salary quartiles
SELECT 
    name, salary,
    NTILE(4) OVER (ORDER BY salary) AS quartile
FROM employees;
-- Divides rows into 4 roughly equal groups

-- Percentile buckets for performance scoring
SELECT 
    name, sales_amount,
    NTILE(10) OVER (ORDER BY sales_amount DESC) AS decile
FROM salespeople;
-- Top 10%, top 20%, etc.
```

### LAG and LEAD

```sql
-- Compare with previous/next row
SELECT 
    date,
    revenue,
    LAG(revenue, 1) OVER (ORDER BY date) AS prev_day_revenue,
    LEAD(revenue, 1) OVER (ORDER BY date) AS next_day_revenue,
    revenue - LAG(revenue, 1) OVER (ORDER BY date) AS day_over_day_change,
    ROUND(
        (revenue - LAG(revenue, 1) OVER (ORDER BY date)) * 100.0 
        / NULLIF(LAG(revenue, 1) OVER (ORDER BY date), 0), 
        2
    ) AS pct_change
FROM daily_revenue;

-- LAG with default value (avoid NULL for first row)
SELECT 
    name, salary,
    LAG(salary, 1, 0) OVER (ORDER BY salary) AS prev_salary
FROM employees;
-- Third argument is the default when there's no previous row

-- Find consecutive values
SELECT *
FROM (
    SELECT 
        date, temperature,
        LAG(temperature) OVER (ORDER BY date) AS prev_temp,
        LEAD(temperature) OVER (ORDER BY date) AS next_temp
    FROM weather
) t
WHERE temperature > prev_temp AND temperature > next_temp;
-- Local maximums (peaks)
```

### FIRST_VALUE, LAST_VALUE, NTH_VALUE

```sql
-- Department: show best and worst salary alongside each employee
SELECT 
    name, department, salary,
    FIRST_VALUE(salary) OVER (
        PARTITION BY department ORDER BY salary DESC
    ) AS highest_in_dept,
    LAST_VALUE(salary) OVER (
        PARTITION BY department ORDER BY salary DESC
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS lowest_in_dept,  -- Note: MUST specify frame for LAST_VALUE!
    NTH_VALUE(salary, 2) OVER (
        PARTITION BY department ORDER BY salary DESC
    ) AS second_highest
FROM employees;
```

### Running Totals and Moving Averages

```sql
-- Running total
SELECT 
    date,
    amount,
    SUM(amount) OVER (ORDER BY date) AS running_total,
    SUM(amount) OVER (
        ORDER BY date 
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_total_explicit
FROM transactions;

-- Running total per category
SELECT 
    date, category, amount,
    SUM(amount) OVER (
        PARTITION BY category 
        ORDER BY date
    ) AS category_running_total
FROM transactions;

-- 7-day moving average
SELECT 
    date,
    revenue,
    ROUND(AVG(revenue) OVER (
        ORDER BY date
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ), 2) AS moving_avg_7day
FROM daily_revenue;

-- Moving average with RANGE (date-based, handles gaps)
SELECT 
    date,
    revenue,
    AVG(revenue) OVER (
        ORDER BY date
        RANGE BETWEEN INTERVAL '6 days' PRECEDING AND CURRENT ROW
    ) AS moving_avg_7day
FROM daily_revenue;
```

### Window Frame Specifications

```sql
-- ROWS: Physical row count
SUM(amount) OVER (ORDER BY date ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)
-- Always exactly 3 rows (2 previous + current)

-- RANGE: Logical value range  
SUM(amount) OVER (ORDER BY date RANGE BETWEEN INTERVAL '7 days' PRECEDING AND CURRENT ROW)
-- All rows within 7 days of current row's date (could be 0 or many)

-- GROUPS (PostgreSQL 11+): Peer groups
SUM(amount) OVER (ORDER BY date GROUPS BETWEEN 1 PRECEDING AND 1 FOLLOWING)
-- 1 peer group before + current group + 1 peer group after

-- Common frame definitions:
ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW       -- Default with ORDER BY
ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING -- Entire partition
ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING       -- Current to end
ROWS BETWEEN 3 PRECEDING AND 3 FOLLOWING               -- 7-row window
```

### Percentage and Cumulative Distribution

```sql
-- Percentage of total
SELECT 
    department,
    salary,
    ROUND(salary * 100.0 / SUM(salary) OVER (), 2) AS pct_of_total,
    ROUND(salary * 100.0 / SUM(salary) OVER (PARTITION BY department), 2) AS pct_of_dept
FROM employees;

-- Cumulative distribution
SELECT 
    name, salary,
    ROUND(CUME_DIST() OVER (ORDER BY salary), 4) AS cume_dist,
    ROUND(PERCENT_RANK() OVER (ORDER BY salary), 4) AS percent_rank
FROM employees;
-- CUME_DIST: % of rows with value <= current
-- PERCENT_RANK: relative rank (0 to 1)
```

---

## Diagram

### PARTITION BY Visual

```
Input:
┌──────┬────────────┬────────┐
│ name │ department │ salary │
├──────┼────────────┼────────┤
│ A    │ Eng        │ 100    │
│ B    │ Eng        │ 90     │
│ C    │ Eng        │ 80     │
│ D    │ Mkt        │ 85     │
│ E    │ Mkt        │ 75     │
└──────┴────────────┴────────┘

DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC):

Partition 1: Eng           Partition 2: Mkt
┌──────┬────────┬──────┐   ┌──────┬────────┬──────┐
│ name │ salary │ rank │   │ name │ salary │ rank │
├──────┼────────┼──────┤   ├──────┼────────┼──────┤
│ A    │ 100    │ 1    │   │ D    │ 85     │ 1    │
│ B    │ 90     │ 2    │   │ E    │ 75     │ 2    │
│ C    │ 80     │ 3    │   └──────┴────────┴──────┘
└──────┴────────┴──────┘

Final Result:
┌──────┬────────────┬────────┬──────┐
│ name │ department │ salary │ rank │
├──────┼────────────┼────────┼──────┤
│ A    │ Eng        │ 100    │ 1    │
│ B    │ Eng        │ 90     │ 2    │
│ C    │ Eng        │ 80     │ 3    │
│ D    │ Mkt        │ 85     │ 1    │
│ E    │ Mkt        │ 75     │ 2    │
└──────┴────────────┴────────┴──────┘
```

---

## Dry Run

### Running Total with Window Frame

```sql
-- transactions:
-- | id | date       | amount |
-- |----|------------|--------|
-- | 1  | 2024-01-01 | 100    |
-- | 2  | 2024-01-02 | 200    |
-- | 3  | 2024-01-03 | 150    |
-- | 4  | 2024-01-04 | 300    |
-- | 5  | 2024-01-05 | 250    |

SELECT 
    date, amount,
    SUM(amount) OVER (ORDER BY date) AS running_total,
    AVG(amount) OVER (ORDER BY date ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS moving_avg_3
FROM transactions;

-- Row 1 (Jan 01, 100):
--   running_total = 100
--   moving_avg_3: frame = [100] → 100/1 = 100

-- Row 2 (Jan 02, 200):
--   running_total = 100 + 200 = 300
--   moving_avg_3: frame = [100, 200] → 300/2 = 150

-- Row 3 (Jan 03, 150):
--   running_total = 300 + 150 = 450
--   moving_avg_3: frame = [100, 200, 150] → 450/3 = 150

-- Row 4 (Jan 04, 300):
--   running_total = 450 + 300 = 750
--   moving_avg_3: frame = [200, 150, 300] → 650/3 = 216.67

-- Row 5 (Jan 05, 250):
--   running_total = 750 + 250 = 1000
--   moving_avg_3: frame = [150, 300, 250] → 700/3 = 233.33

-- Result:
-- | date       | amount | running_total | moving_avg_3 |
-- |------------|--------|---------------|--------------|
-- | 2024-01-01 | 100    | 100           | 100.00       |
-- | 2024-01-02 | 200    | 300           | 150.00       |
-- | 2024-01-03 | 150    | 450           | 150.00       |
-- | 2024-01-04 | 300    | 750           | 216.67       |
-- | 2024-01-05 | 250    | 1000          | 233.33       |
```

---

## Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| ROW_NUMBER (with sort) | O(n log n) | Sort required |
| ROW_NUMBER (indexed) | O(n) | If ORDER BY column is indexed |
| Running SUM | O(n) per partition | Single pass after sort |
| Moving average (k rows) | O(n × k) worst case | Optimized to O(n) with running sum |
| LAG/LEAD | O(n) | Single pass |
| PARTITION BY | O(n log n) | Needs sort per partition |

---

## Real Project Usage

### Year-over-Year Growth Report

```sql
WITH monthly_revenue AS (
    SELECT 
        DATE_TRUNC('month', created_at) AS month,
        SUM(total_amount) AS revenue
    FROM orders
    WHERE status = 'completed'
    GROUP BY DATE_TRUNC('month', created_at)
)
SELECT 
    month,
    revenue,
    LAG(revenue, 12) OVER (ORDER BY month) AS same_month_last_year,
    ROUND(
        (revenue - LAG(revenue, 12) OVER (ORDER BY month)) * 100.0 
        / NULLIF(LAG(revenue, 12) OVER (ORDER BY month), 0), 
        2
    ) AS yoy_growth_pct,
    SUM(revenue) OVER (
        ORDER BY month 
        ROWS BETWEEN 11 PRECEDING AND CURRENT ROW
    ) AS trailing_12_month
FROM monthly_revenue
ORDER BY month DESC;
```

### Gaps and Islands Problem

```sql
-- Find consecutive login days for each user
WITH numbered AS (
    SELECT 
        user_id,
        login_date,
        login_date - (ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY login_date))::INTEGER AS grp
    FROM (SELECT DISTINCT user_id, login_date::DATE FROM user_logins) t
)
SELECT 
    user_id,
    MIN(login_date) AS streak_start,
    MAX(login_date) AS streak_end,
    COUNT(*) AS consecutive_days
FROM numbered
GROUP BY user_id, grp
HAVING COUNT(*) >= 7  -- Streaks of 7+ days
ORDER BY consecutive_days DESC;
```

### De-duplication (Keep Latest)

```sql
-- Keep only the latest record per user (common in event processing)
DELETE FROM user_events
WHERE id NOT IN (
    SELECT DISTINCT ON (user_id) id
    FROM user_events
    ORDER BY user_id, created_at DESC
);

-- Or using window function for soft-delete approach:
WITH ranked AS (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC) AS rn
    FROM user_events
)
SELECT * FROM ranked WHERE rn = 1;
```

---

## Interview Questions & Answers

**Q1: What's the difference between ROW_NUMBER, RANK, and DENSE_RANK?**

```
Values: [100, 90, 90, 80]
ROW_NUMBER: 1, 2, 3, 4  → Always unique, arbitrary for ties
RANK:       1, 2, 2, 4  → Ties get same rank, gaps after
DENSE_RANK: 1, 2, 2, 3  → Ties get same rank, no gaps
```

Use ROW_NUMBER for: deduplication, top-1 per group
Use DENSE_RANK for: "Nth highest salary" problems
Use RANK for: competition-style ranking

**Q2: Write a query for the second highest salary per department.**

```sql
SELECT department, name, salary
FROM (
    SELECT 
        department, name, salary,
        DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employees
) ranked
WHERE rnk = 2;
```

**Q3: Why does LAST_VALUE often give unexpected results?**

Default window frame with ORDER BY is `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`. So LAST_VALUE sees only up to the current row!

```sql
-- WRONG: Returns current row's value
LAST_VALUE(salary) OVER (ORDER BY salary)

-- CORRECT: Sees entire partition
LAST_VALUE(salary) OVER (
    ORDER BY salary 
    ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
)
```

**Q4: Explain the difference between ROWS and RANGE in window frames.**

- **ROWS**: Counts physical rows (exactly N rows before/after)
- **RANGE**: Uses logical value range (all rows within a value distance)

```sql
-- ROWS: exactly 3 rows
AVG(amount) OVER (ORDER BY id ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)

-- RANGE: all rows with same date (handles duplicates differently)
SUM(amount) OVER (ORDER BY date RANGE BETWEEN CURRENT ROW AND CURRENT ROW)
-- Includes ALL rows with the same date as current row
```

**Q5: How do you find top N per group efficiently?**

```sql
-- Method 1: Window function (most common)
SELECT * FROM (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn
    FROM employees
) t WHERE rn <= 3;

-- Method 2: LATERAL JOIN (PostgreSQL, often faster with index)
SELECT d.name, e.*
FROM departments d
CROSS JOIN LATERAL (
    SELECT * FROM employees
    WHERE dept_id = d.id
    ORDER BY salary DESC
    LIMIT 3
) e;
```

---

## Common Mistakes

1. **LAST_VALUE without proper frame** — default frame ends at current row
2. **Window function in WHERE** — not allowed! Wrap in subquery/CTE
   ```sql
   -- WRONG
   SELECT * FROM emp WHERE ROW_NUMBER() OVER (...) = 1
   -- CORRECT
   SELECT * FROM (SELECT *, ROW_NUMBER() OVER (...) AS rn FROM emp) t WHERE rn = 1
   ```
3. **Missing PARTITION BY** — function applies to entire result set
4. **Confusing RANK and DENSE_RANK** for "Nth highest" problems
5. **Performance: window over huge partitions** without indexes

---

## Best Practices

1. **Use named windows** for multiple functions with same definition:
   ```sql
   SELECT 
       ROW_NUMBER() OVER w AS rn,
       SUM(salary) OVER w AS running_sum
   FROM employees
   WINDOW w AS (PARTITION BY department ORDER BY salary DESC);
   ```
2. **Index PARTITION BY + ORDER BY columns** for performance
3. **Use LATERAL JOIN** for top-N per group with index support
4. **Specify explicit frame** when using LAST_VALUE or NTH_VALUE
5. **Filter using subquery/CTE** — can't use window function in WHERE

---

## Production Considerations

1. **Memory**: Window functions buffer entire partitions in memory
2. **Large partitions**: If partition has millions of rows, may spill to disk
3. **Index support**: `CREATE INDEX ON table(partition_col, order_col)` helps sort
4. **Parallel execution**: PostgreSQL can parallelize window function computation
5. **Alternative for top-N**: LATERAL join with LIMIT often outperforms window + filter

---

## Related Topics
- [Topic 6: Grouping](#)
- [Topic 11: CTE](#)
- [Topic 13: SQL Coding Problems](#)
- [Topic 19: Query Optimization](#)
