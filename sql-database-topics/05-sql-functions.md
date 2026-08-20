# Topic 5: SQL Functions

## Theory

SQL functions transform or aggregate data. Two main categories:

```
┌─────────────────────────────────────────────────────────┐
│                    SQL FUNCTIONS                          │
├───────────────────────────┬─────────────────────────────┤
│   Scalar Functions        │   Aggregate Functions        │
│   (one row → one value)   │   (many rows → one value)   │
├───────────────────────────┼─────────────────────────────┤
│ String: CONCAT, UPPER...  │ COUNT                       │
│ Numeric: ROUND, ABS...    │ SUM                         │
│ Date: NOW, EXTRACT...     │ AVG                         │
│ Conversion: CAST...       │ MIN                         │
│ Conditional: CASE, COALESCE│ MAX                        │
└───────────────────────────┴─────────────────────────────┘
```

---

## Aggregate Functions

### COUNT, SUM, AVG, MIN, MAX

```sql
-- COUNT variants
SELECT 
    COUNT(*)           AS total_rows,        -- Counts all rows including NULLs
    COUNT(bonus)       AS non_null_bonus,    -- Counts non-NULL values only
    COUNT(DISTINCT dept) AS unique_depts     -- Counts unique non-NULL values
FROM employees;

-- SUM, AVG — ignore NULLs
SELECT 
    SUM(salary) AS total_payroll,            -- Sum of non-NULL salaries
    AVG(salary) AS avg_salary,               -- Average of non-NULL salaries
    SUM(salary) / COUNT(*) AS true_avg       -- Different if NULLs exist!
FROM employees;

-- MIN, MAX — work on any comparable type
SELECT 
    MIN(salary) AS lowest_salary,
    MAX(salary) AS highest_salary,
    MIN(hire_date) AS earliest_hire,
    MAX(name) AS last_alphabetically
FROM employees;

-- Conditional aggregation
SELECT 
    COUNT(*) FILTER (WHERE dept = 'Engineering') AS eng_count,    -- PostgreSQL
    COUNT(*) FILTER (WHERE dept = 'Marketing') AS mkt_count,
    SUM(salary) FILTER (WHERE is_active = TRUE) AS active_payroll,
    AVG(salary) FILTER (WHERE hire_date > '2023-01-01') AS new_hire_avg
FROM employees;

-- Equivalent using CASE (works in all databases)
SELECT 
    COUNT(CASE WHEN dept = 'Engineering' THEN 1 END) AS eng_count,
    SUM(CASE WHEN is_active THEN salary ELSE 0 END) AS active_payroll,
    AVG(CASE WHEN hire_date > '2023-01-01' THEN salary END) AS new_hire_avg
FROM employees;
```

---

## String Functions

```sql
-- CONCAT / ||
SELECT CONCAT(first_name, ' ', last_name) AS full_name FROM employees;
SELECT first_name || ' ' || last_name AS full_name FROM employees;  -- PostgreSQL

-- UPPER / LOWER
SELECT UPPER(email) AS upper_email FROM employees;
SELECT LOWER(department) FROM employees;

-- LENGTH
SELECT name, LENGTH(name) AS name_length FROM employees;

-- SUBSTRING / SUBSTR
SELECT SUBSTRING(phone FROM 1 FOR 3) AS area_code FROM employees;
SELECT SUBSTRING(email FROM '@(.+)$') AS domain FROM employees;  -- Regex!

-- TRIM / LTRIM / RTRIM
SELECT TRIM('  hello  ') AS trimmed;           -- 'hello'
SELECT TRIM(BOTH '-' FROM '--hello--');         -- 'hello'
SELECT LTRIM('  hello');                        -- 'hello'

-- REPLACE
SELECT REPLACE(phone, '-', '') AS clean_phone FROM employees;

-- POSITION / STRPOS
SELECT POSITION('@' IN email) AS at_position FROM employees;

-- LEFT / RIGHT
SELECT LEFT(name, 1) AS initial FROM employees;
SELECT RIGHT(phone, 4) AS last_four FROM employees;

-- REPEAT / LPAD / RPAD
SELECT LPAD(employee_id::TEXT, 6, '0') AS padded_id FROM employees;  -- '000042'
SELECT RPAD(name, 20, '.') FROM employees;  -- 'Alice...............'

-- SPLIT_PART (PostgreSQL)
SELECT SPLIT_PART(email, '@', 1) AS username FROM employees;
SELECT SPLIT_PART(email, '@', 2) AS domain FROM employees;

-- STRING_AGG (PostgreSQL) — aggregate strings
SELECT 
    department,
    STRING_AGG(name, ', ' ORDER BY name) AS employee_names
FROM employees
GROUP BY department;

-- REGEXP functions (PostgreSQL)
SELECT * FROM products WHERE name ~ '^[A-Z]';           -- Starts with uppercase
SELECT REGEXP_REPLACE(phone, '[^0-9]', '', 'g') FROM contacts;  -- Strip non-digits
SELECT REGEXP_MATCHES(text, '\b\w+@\w+\.\w+\b', 'g') FROM emails; -- Find all emails
```

---

## Numeric Functions

```sql
-- ROUND
SELECT ROUND(123.456, 2);     -- 123.46
SELECT ROUND(123.456, 0);     -- 123
SELECT ROUND(123.456, -1);    -- 120

-- CEIL / FLOOR
SELECT CEIL(4.1);   -- 5
SELECT FLOOR(4.9);  -- 4

-- ABS
SELECT ABS(-42);    -- 42

-- MOD / %
SELECT MOD(10, 3);  -- 1
SELECT 10 % 3;      -- 1

-- POWER / SQRT
SELECT POWER(2, 10);  -- 1024
SELECT SQRT(144);     -- 12

-- TRUNC (truncate without rounding)
SELECT TRUNC(123.456, 2);  -- 123.45 (not 123.46)

-- SIGN
SELECT SIGN(-5), SIGN(0), SIGN(5);  -- -1, 0, 1

-- RANDOM (PostgreSQL)
SELECT RANDOM();  -- Random float between 0 and 1
SELECT FLOOR(RANDOM() * 100 + 1);  -- Random integer 1-100

-- Practical: Percentage calculation
SELECT 
    department,
    COUNT(*) AS dept_count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) AS percentage
FROM employees
GROUP BY department;
```

---

## Date/Time Functions

```sql
-- Current date/time
SELECT CURRENT_DATE;           -- 2024-03-15
SELECT CURRENT_TIMESTAMP;      -- 2024-03-15 10:30:45.123+00
SELECT NOW();                  -- Same as CURRENT_TIMESTAMP (PostgreSQL)
SELECT CURRENT_TIME;           -- 10:30:45.123+00

-- EXTRACT
SELECT EXTRACT(YEAR FROM hire_date) AS hire_year FROM employees;
SELECT EXTRACT(MONTH FROM created_at) AS month FROM orders;
SELECT EXTRACT(DOW FROM created_at) AS day_of_week FROM orders;  -- 0=Sun, 6=Sat
SELECT EXTRACT(EPOCH FROM created_at) AS unix_timestamp FROM orders;

-- DATE_TRUNC (PostgreSQL) — truncate to precision
SELECT DATE_TRUNC('month', NOW());    -- 2024-03-01 00:00:00
SELECT DATE_TRUNC('year', NOW());     -- 2024-01-01 00:00:00
SELECT DATE_TRUNC('hour', NOW());     -- 2024-03-15 10:00:00
SELECT DATE_TRUNC('week', NOW());     -- Start of current week

-- Date arithmetic
SELECT NOW() + INTERVAL '30 days';          -- 30 days from now
SELECT NOW() - INTERVAL '2 hours';          -- 2 hours ago
SELECT hire_date + INTERVAL '1 year' AS anniversary FROM employees;

-- AGE function (PostgreSQL)
SELECT AGE(NOW(), hire_date) AS tenure FROM employees;
-- Returns: '3 years 2 mons 5 days'

-- Date difference
SELECT 
    created_at,
    NOW() - created_at AS time_since_creation,
    EXTRACT(DAY FROM NOW() - created_at) AS days_old
FROM orders;

-- Date formatting (PostgreSQL)
SELECT TO_CHAR(NOW(), 'YYYY-MM-DD HH24:MI:SS') AS formatted;
SELECT TO_CHAR(NOW(), 'Day, DD Month YYYY') AS readable;

-- Parse string to date
SELECT TO_DATE('15-03-2024', 'DD-MM-YYYY');
SELECT TO_TIMESTAMP('2024-03-15 10:30:00', 'YYYY-MM-DD HH24:MI:SS');

-- Time zones
SELECT NOW() AT TIME ZONE 'America/New_York';
SELECT created_at AT TIME ZONE 'UTC' FROM orders;

-- Practical: Group by date period
SELECT 
    DATE_TRUNC('month', created_at) AS month,
    COUNT(*) AS order_count,
    SUM(total_amount) AS revenue
FROM orders
WHERE created_at >= NOW() - INTERVAL '12 months'
GROUP BY DATE_TRUNC('month', created_at)
ORDER BY month;

-- Generate date series (PostgreSQL)
SELECT generate_series(
    '2024-01-01'::DATE,
    '2024-12-31'::DATE,
    '1 month'::INTERVAL
) AS month;
```

---

## Dry Run

### Aggregate Functions with NULLs

```sql
-- employees table:
-- | id | name    | salary | bonus |
-- |----|---------|--------|-------|
-- | 1  | Alice   | 90000  | 5000  |
-- | 2  | Bob     | 75000  | NULL  |
-- | 3  | Charlie | 85000  | 3000  |
-- | 4  | Diana   | 95000  | NULL  |
-- | 5  | Eve     | 70000  | 2000  |

SELECT 
    COUNT(*) AS total,
    COUNT(bonus) AS with_bonus,
    SUM(bonus) AS total_bonus,
    AVG(bonus) AS avg_bonus
FROM employees;

-- COUNT(*) = 5 (all rows)
-- COUNT(bonus) = 3 (only non-NULL: Alice=5000, Charlie=3000, Eve=2000)
-- SUM(bonus) = 5000 + 3000 + 2000 = 10000 (ignores NULLs)
-- AVG(bonus) = 10000 / 3 = 3333.33 (divides by non-NULL count!)
-- ⚠️ AVG(bonus) ≠ SUM(bonus)/COUNT(*) which would be 10000/5 = 2000

-- If you want average including NULLs as 0:
SELECT AVG(COALESCE(bonus, 0)) FROM employees;
-- = (5000+0+3000+0+2000)/5 = 2000
```

---

## Complexity

| Function | Complexity |
|----------|-----------|
| Scalar functions (per row) | O(1) per row, O(n) total |
| COUNT(*) without index | O(n) |
| COUNT(*) with covering index | O(n) but faster (smaller pages) |
| SUM/AVG/MIN/MAX | O(n) |
| MIN/MAX with B-tree index | O(log n) — index extremes |
| STRING_AGG / GROUP_CONCAT | O(n) per group |
| DISTINCT aggregates | O(n log n) — needs sorting/hashing |

---

## Real Project Usage

### Analytics Dashboard Queries

```sql
-- Monthly revenue with YoY comparison
SELECT 
    DATE_TRUNC('month', o.created_at) AS month,
    SUM(o.total_amount) AS revenue,
    COUNT(DISTINCT o.customer_id) AS unique_customers,
    ROUND(AVG(o.total_amount), 2) AS avg_order_value,
    SUM(o.total_amount) - LAG(SUM(o.total_amount), 12) OVER (ORDER BY DATE_TRUNC('month', o.created_at)) AS yoy_change
FROM orders o
WHERE o.status = 'completed'
  AND o.created_at >= NOW() - INTERVAL '24 months'
GROUP BY DATE_TRUNC('month', o.created_at)
ORDER BY month DESC;

-- Data quality report
SELECT
    'products' AS table_name,
    COUNT(*) AS total_records,
    COUNT(*) - COUNT(description) AS null_descriptions,
    COUNT(*) - COUNT(category_id) AS null_categories,
    COUNT(*) FILTER (WHERE price <= 0) AS invalid_prices,
    ROUND(AVG(LENGTH(name)), 1) AS avg_name_length
FROM products;
```

### Search with Relevance Scoring

```sql
SELECT 
    id,
    name,
    CASE 
        WHEN LOWER(name) = LOWER(:query) THEN 100
        WHEN LOWER(name) LIKE LOWER(:query) || '%' THEN 80
        WHEN LOWER(name) LIKE '%' || LOWER(:query) || '%' THEN 60
        WHEN LOWER(description) LIKE '%' || LOWER(:query) || '%' THEN 40
        ELSE 0
    END AS relevance_score
FROM products
WHERE name ILIKE '%' || :query || '%'
   OR description ILIKE '%' || :query || '%'
ORDER BY relevance_score DESC, name ASC
LIMIT 20;
```

---

## Interview Questions & Answers

**Q1: What's the difference between COUNT(*), COUNT(column), and COUNT(DISTINCT column)?**

| Function | Behavior |
|----------|----------|
| COUNT(*) | Counts ALL rows (including NULLs) |
| COUNT(column) | Counts rows where column IS NOT NULL |
| COUNT(DISTINCT column) | Counts unique non-NULL values |

```sql
-- Table: [1, 2, 2, NULL, 3]
COUNT(*) = 5
COUNT(col) = 4  -- NULL excluded
COUNT(DISTINCT col) = 3  -- {1, 2, 3}
```

**Q2: What's the difference between COALESCE and NULLIF?**

- **COALESCE(a, b, c)**: Returns first non-NULL argument
- **NULLIF(a, b)**: Returns NULL if a = b, otherwise returns a

```sql
-- COALESCE: default values
SELECT COALESCE(phone, email, 'No contact') FROM users;

-- NULLIF: prevent division by zero
SELECT revenue / NULLIF(orders, 0) FROM stats;
-- If orders = 0: NULLIF returns NULL → revenue/NULL = NULL (not error)
```

**Q3: How does EXTRACT differ from DATE_TRUNC?**

- **EXTRACT**: Returns a numeric component (year, month, day as integer)
- **DATE_TRUNC**: Returns a truncated timestamp

```sql
SELECT EXTRACT(MONTH FROM '2024-03-15'::DATE);  -- Returns: 3 (integer)
SELECT DATE_TRUNC('month', '2024-03-15'::DATE);  -- Returns: 2024-03-01 (date)
```

**Q4: Write a query to find employees hired in the last 90 days with salary above department average.**

```sql
SELECT e.name, e.salary, e.department, d.avg_salary
FROM employees e
JOIN (
    SELECT department, AVG(salary) AS avg_salary
    FROM employees
    GROUP BY department
) d ON e.department = d.department
WHERE e.hire_date >= CURRENT_DATE - INTERVAL '90 days'
  AND e.salary > d.avg_salary;
```

**Q5: What's the FILTER clause in PostgreSQL?**

FILTER restricts which rows an aggregate function processes:
```sql
-- PostgreSQL FILTER (cleaner than CASE):
SELECT 
    COUNT(*) FILTER (WHERE status = 'active') AS active,
    COUNT(*) FILTER (WHERE status = 'inactive') AS inactive,
    SUM(amount) FILTER (WHERE type = 'credit') AS total_credits
FROM accounts;

-- Equivalent (standard SQL):
SELECT
    COUNT(CASE WHEN status = 'active' THEN 1 END) AS active,
    SUM(CASE WHEN type = 'credit' THEN amount ELSE 0 END) AS total_credits
FROM accounts;
```

---

## Common Mistakes

1. **AVG with NULLs**: AVG ignores NULLs, doesn't treat them as 0
2. **String comparison case sensitivity**: PostgreSQL is case-sensitive by default; use ILIKE or LOWER()
3. **Date arithmetic without considering time zones**: `CURRENT_DATE` is timezone-dependent
4. **Integer division**: `SELECT 5/2` = 2 (not 2.5). Use `5.0/2` or `5::NUMERIC/2`
5. **ROUND with negative precision**: `ROUND(1234, -2)` = 1200 (rounds to hundreds)
6. **DATE_TRUNC timezone issues**: Truncating in wrong timezone can give wrong dates

---

## Best Practices

1. **Use COALESCE for NULL defaults** in calculations
2. **Use DATE_TRUNC** for grouping by time periods (not EXTRACT)
3. **Use FILTER** (PostgreSQL) over CASE for conditional aggregation — more readable
4. **Use numeric precision** in financial calculations: `NUMERIC(10,2)` not FLOAT
5. **Index expressions** if you filter on function results:
   ```sql
   CREATE INDEX idx_lower_email ON users (LOWER(email));
   ```
6. **Use generate_series** for gap-filling in time series data

---

## Production Considerations

1. **Function-based indexes**: If you filter on `LOWER(email)`, create index on `LOWER(email)`
2. **Immutable functions only in indexes**: PostgreSQL requires immutable functions for expression indexes
3. **Aggregate performance**: COUNT(*) on large tables can be slow in PostgreSQL (MVCC)
   - Use `SELECT reltuples FROM pg_class` for estimates
4. **String operations on large text**: LIKE '%pattern%' cannot use standard B-tree indexes; consider GIN trigram index
5. **Timezone handling**: Store timestamps in UTC, convert on display

```sql
-- Create trigram index for LIKE '%pattern%' searches
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_product_name_trgm ON products USING GIN (name gin_trgm_ops);
```

---

## Related Topics
- [Topic 3: SELECT Fundamentals](#)
- [Topic 6: Grouping](#)
- [Topic 10: Window Functions](#)
