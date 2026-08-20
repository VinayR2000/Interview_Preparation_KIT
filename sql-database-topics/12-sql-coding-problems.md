# Topic 12: SQL Coding Problems — Interview Essentials

## Must-Master Problems with Solutions

### 1. Second Highest Salary

```sql
-- Method 1: LIMIT OFFSET
SELECT DISTINCT salary FROM employees
ORDER BY salary DESC
LIMIT 1 OFFSET 1;

-- Method 2: Subquery (without LIMIT)
SELECT MAX(salary) AS second_highest
FROM employees
WHERE salary < (SELECT MAX(salary) FROM employees);

-- Method 3: DENSE_RANK
SELECT salary FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
) t WHERE rnk = 2;
```

### 2. Nth Highest Salary

```sql
-- Generic Nth highest
CREATE FUNCTION nth_highest_salary(n INTEGER) 
RETURNS NUMERIC AS $$
    SELECT DISTINCT salary
    FROM employees
    ORDER BY salary DESC
    LIMIT 1 OFFSET n - 1;
$$ LANGUAGE SQL;

-- Using DENSE_RANK
SELECT salary FROM (
    SELECT DISTINCT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
) t WHERE rnk = :n;
```

### 3. Highest Salary Per Department

```sql
-- Method 1: Window function
SELECT department, name, salary FROM (
    SELECT *, RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employees
) t WHERE rnk = 1;

-- Method 2: Correlated subquery
SELECT * FROM employees e
WHERE salary = (
    SELECT MAX(salary) FROM employees WHERE department = e.department
);

-- Method 3: JOIN with aggregate
SELECT e.*
FROM employees e
JOIN (SELECT department, MAX(salary) AS max_sal FROM employees GROUP BY department) d
    ON e.department = d.department AND e.salary = d.max_sal;
```

### 4. Second Highest Salary Per Department

```sql
SELECT department, name, salary FROM (
    SELECT *, DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employees
) t WHERE rnk = 2;
```

### 5. Top 3 Salaries Per Department

```sql
SELECT department, name, salary FROM (
    SELECT *, DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employees
) t WHERE rnk <= 3;
```

### 6. Employees Earning Above Average

```sql
-- Company average
SELECT * FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);

-- Department average
SELECT e.* FROM employees e
WHERE e.salary > (
    SELECT AVG(salary) FROM employees WHERE department = e.department
);

-- Using window function
SELECT * FROM (
    SELECT *, AVG(salary) OVER (PARTITION BY department) AS dept_avg
    FROM employees
) t WHERE salary > dept_avg;
```

### 7. Find and Delete Duplicate Records

```sql
-- Find duplicates
SELECT email, COUNT(*) FROM employees
GROUP BY email HAVING COUNT(*) > 1;

-- Delete duplicates (keep lowest id)
DELETE FROM employees
WHERE id NOT IN (
    SELECT MIN(id) FROM employees GROUP BY email
);

-- PostgreSQL: Using ctid
DELETE FROM employees a
USING employees b
WHERE a.ctid > b.ctid AND a.email = b.email;

-- Using window function to identify duplicates
WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY email ORDER BY id) AS rn
    FROM employees
)
DELETE FROM employees WHERE id IN (
    SELECT id FROM duplicates WHERE rn > 1
);
```

### 8. Find Missing Records / Anti-Join Patterns

```sql
-- Employees without a department
SELECT e.* FROM employees e
LEFT JOIN departments d ON e.dept_id = d.id
WHERE d.id IS NULL;

-- Departments without employees
SELECT d.* FROM departments d
LEFT JOIN employees e ON d.id = e.dept_id
WHERE e.id IS NULL;

-- Customers without orders
SELECT c.* FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.customer_id = c.id
);

-- Products never ordered
SELECT p.* FROM products p
WHERE p.id NOT IN (
    SELECT DISTINCT product_id FROM order_items WHERE product_id IS NOT NULL
);
-- Better (NULL-safe):
SELECT p.* FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM order_items oi WHERE oi.product_id = p.id
);
```

### 9. Latest/First Record Per Group

```sql
-- Latest order per customer
SELECT DISTINCT ON (customer_id) *
FROM orders
ORDER BY customer_id, created_at DESC;

-- Using window function
SELECT * FROM (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at DESC) AS rn
    FROM orders
) t WHERE rn = 1;

-- First order per customer
SELECT DISTINCT ON (customer_id) *
FROM orders
ORDER BY customer_id, created_at ASC;
```

### 10. Running Total

```sql
SELECT 
    date,
    amount,
    SUM(amount) OVER (ORDER BY date) AS running_total
FROM transactions;

-- Running total per category
SELECT 
    date, category, amount,
    SUM(amount) OVER (PARTITION BY category ORDER BY date) AS running_total
FROM transactions;

-- Cumulative percentage
SELECT 
    date, amount,
    SUM(amount) OVER (ORDER BY date) AS running_total,
    ROUND(SUM(amount) OVER (ORDER BY date) * 100.0 / SUM(amount) OVER (), 2) AS cumulative_pct
FROM transactions;
```

### 11. Moving Average

```sql
-- 7-day moving average
SELECT 
    date,
    amount,
    ROUND(AVG(amount) OVER (
        ORDER BY date ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ), 2) AS moving_avg_7
FROM daily_sales;
```

### 12. Consecutive Days / Gaps and Islands

```sql
-- Find users active on consecutive days
WITH user_dates AS (
    SELECT DISTINCT user_id, login_date::DATE AS dt
    FROM logins
),
grouped AS (
    SELECT 
        user_id, dt,
        dt - ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY dt)::INTEGER AS grp
    FROM user_dates
)
SELECT 
    user_id,
    MIN(dt) AS streak_start,
    MAX(dt) AS streak_end,
    COUNT(*) AS streak_length
FROM grouped
GROUP BY user_id, grp
HAVING COUNT(*) >= 3  -- At least 3 consecutive days
ORDER BY streak_length DESC;
```

### 13. Year-over-Year Growth

```sql
WITH monthly AS (
    SELECT 
        DATE_TRUNC('month', created_at) AS month,
        SUM(amount) AS revenue
    FROM orders
    GROUP BY DATE_TRUNC('month', created_at)
)
SELECT 
    month,
    revenue,
    LAG(revenue, 12) OVER (ORDER BY month) AS prev_year,
    ROUND((revenue - LAG(revenue, 12) OVER (ORDER BY month)) * 100.0 
          / NULLIF(LAG(revenue, 12) OVER (ORDER BY month), 0), 2) AS yoy_growth
FROM monthly;
```

### 14. Percentage Contribution

```sql
-- Each product's contribution to category revenue
SELECT 
    category,
    product_name,
    revenue,
    ROUND(revenue * 100.0 / SUM(revenue) OVER (PARTITION BY category), 2) AS pct_of_category,
    ROUND(revenue * 100.0 / SUM(revenue) OVER (), 2) AS pct_of_total
FROM product_revenue
ORDER BY category, revenue DESC;
```

### 15. Pivot-Style Reports

```sql
-- Monthly sales by category (pivot)
SELECT 
    category,
    SUM(CASE WHEN EXTRACT(MONTH FROM sale_date) = 1 THEN amount ELSE 0 END) AS jan,
    SUM(CASE WHEN EXTRACT(MONTH FROM sale_date) = 2 THEN amount ELSE 0 END) AS feb,
    SUM(CASE WHEN EXTRACT(MONTH FROM sale_date) = 3 THEN amount ELSE 0 END) AS mar,
    SUM(amount) AS total
FROM sales
WHERE EXTRACT(YEAR FROM sale_date) = 2024
GROUP BY category
ORDER BY total DESC;

-- Using PostgreSQL crosstab (tablefunc extension)
SELECT * FROM crosstab(
    'SELECT category, EXTRACT(MONTH FROM sale_date)::INT, SUM(amount)
     FROM sales WHERE EXTRACT(YEAR FROM sale_date) = 2024
     GROUP BY category, EXTRACT(MONTH FROM sale_date)
     ORDER BY 1, 2',
    'SELECT generate_series(1, 12)'
) AS ct(category TEXT, jan NUMERIC, feb NUMERIC, mar NUMERIC, 
        apr NUMERIC, may NUMERIC, jun NUMERIC, jul NUMERIC, 
        aug NUMERIC, sep NUMERIC, oct NUMERIC, nov NUMERIC, dec NUMERIC);
```

### 16. Compare Current vs Previous Row

```sql
-- Price changes
SELECT 
    product_id,
    effective_date,
    price,
    LAG(price) OVER (PARTITION BY product_id ORDER BY effective_date) AS prev_price,
    price - LAG(price) OVER (PARTITION BY product_id ORDER BY effective_date) AS price_change,
    CASE 
        WHEN price > LAG(price) OVER (PARTITION BY product_id ORDER BY effective_date) THEN 'INCREASE'
        WHEN price < LAG(price) OVER (PARTITION BY product_id ORDER BY effective_date) THEN 'DECREASE'
        ELSE 'NO CHANGE'
    END AS direction
FROM price_history
ORDER BY product_id, effective_date;
```

### 17. Employee-Manager Hierarchy (Recursive)

```sql
WITH RECURSIVE hierarchy AS (
    SELECT id, name, manager_id, 0 AS level, ARRAY[id] AS path
    FROM employees WHERE manager_id IS NULL
    UNION ALL
    SELECT e.id, e.name, e.manager_id, h.level + 1, h.path || e.id
    FROM employees e JOIN hierarchy h ON e.manager_id = h.id
    WHERE NOT (e.id = ANY(h.path))  -- Cycle prevention
)
SELECT 
    REPEAT('  ', level) || name AS org_chart,
    level
FROM hierarchy
ORDER BY path;
```

### 18. Find Customers Active on Consecutive Days

```sql
WITH daily_active AS (
    SELECT DISTINCT user_id, activity_date::DATE AS dt
    FROM user_activity
),
with_groups AS (
    SELECT 
        user_id, dt,
        dt - (ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY dt))::INT AS island
    FROM daily_active
),
streaks AS (
    SELECT user_id, island, COUNT(*) AS consecutive_days,
           MIN(dt) AS start_date, MAX(dt) AS end_date
    FROM with_groups
    GROUP BY user_id, island
)
SELECT * FROM streaks WHERE consecutive_days >= 7
ORDER BY consecutive_days DESC;
```

---

## Interview Tips

1. **Always clarify**: Handle ties? NULLs? Exact duplicates?
2. **Start simple**: Write the basic query first, then optimize
3. **Know multiple approaches**: Subquery, JOIN, Window function
4. **Think about edge cases**: Empty tables, single row, all same values
5. **Mention performance**: Which approach works better for large data?

### Common Patterns to Memorize

| Pattern | Technique |
|---------|-----------|
| Nth highest | DENSE_RANK or OFFSET |
| Top-N per group | DENSE_RANK + PARTITION BY |
| Duplicates | GROUP BY + HAVING COUNT > 1 |
| Anti-join | LEFT JOIN + IS NULL or NOT EXISTS |
| Running total | SUM() OVER (ORDER BY) |
| Previous row | LAG() |
| Consecutive | ROW_NUMBER trick (date - row_number) |
| Hierarchy | Recursive CTE |

---

## Related Topics
- [Topic 7: Joins](#)
- [Topic 10: Window Functions](#)
- [Topic 11: CTE and Recursive Queries](#)
