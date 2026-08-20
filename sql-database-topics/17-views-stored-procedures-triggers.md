# Topic 17: Views, Stored Procedures, Triggers, and Sequences

## Views

### Theory

A view is a named query stored in the database. It acts as a virtual table.

```sql
-- Create view
CREATE VIEW active_products AS
SELECT id, name, price, category_id
FROM products
WHERE is_active = TRUE AND stock > 0;

-- Query view like a table
SELECT * FROM active_products WHERE price < 50;

-- The database internally expands:
SELECT * FROM (
    SELECT id, name, price, category_id
    FROM products
    WHERE is_active = TRUE AND stock > 0
) WHERE price < 50;
```

### Materialized View

```sql
-- Materialized view: Results stored physically (like a cache)
CREATE MATERIALIZED VIEW monthly_revenue AS
SELECT 
    DATE_TRUNC('month', created_at) AS month,
    SUM(total_amount) AS revenue,
    COUNT(*) AS order_count
FROM orders
WHERE status = 'completed'
GROUP BY DATE_TRUNC('month', created_at);

-- Must refresh manually
REFRESH MATERIALIZED VIEW monthly_revenue;

-- Concurrent refresh (doesn't lock reads, requires UNIQUE index)
CREATE UNIQUE INDEX idx_monthly_revenue ON monthly_revenue (month);
REFRESH MATERIALIZED VIEW CONCURRENTLY monthly_revenue;
```

### View vs Materialized View

| Feature | View | Materialized View |
|---------|------|-------------------|
| Data stored | No (computed on query) | Yes (physically stored) |
| Always current | Yes | No (must refresh) |
| Speed | Same as underlying query | Very fast (pre-computed) |
| Storage | None | Uses disk space |
| Indexes | No | Yes |
| Updatable | Sometimes | No (read-only) |

---

## Stored Procedures and Functions

```sql
-- PostgreSQL Function
CREATE OR REPLACE FUNCTION get_department_stats(dept_name TEXT)
RETURNS TABLE(employee_count BIGINT, avg_salary NUMERIC, total_payroll NUMERIC)
LANGUAGE plpgsql AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*),
        ROUND(AVG(salary), 2),
        SUM(salary)
    FROM employees
    WHERE department = dept_name;
END;
$$;

-- Usage
SELECT * FROM get_department_stats('Engineering');

-- Procedure (PostgreSQL 11+, supports transactions)
CREATE OR REPLACE PROCEDURE transfer_funds(
    from_account BIGINT,
    to_account BIGINT,
    amount NUMERIC
)
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE accounts SET balance = balance - amount WHERE id = from_account;
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Source account % not found', from_account;
    END IF;
    
    UPDATE accounts SET balance = balance + amount WHERE id = to_account;
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target account % not found', to_account;
    END IF;
    
    COMMIT;
END;
$$;

-- Call procedure
CALL transfer_funds(1, 2, 500.00);
```

---

## Triggers

```sql
-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_timestamp
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_timestamp();

-- Audit trail trigger
CREATE OR REPLACE FUNCTION audit_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO audit_log (table_name, action, new_data, performed_at)
        VALUES (TG_TABLE_NAME, 'INSERT', row_to_json(NEW), NOW());
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit_log (table_name, action, old_data, new_data, performed_at)
        VALUES (TG_TABLE_NAME, 'UPDATE', row_to_json(OLD), row_to_json(NEW), NOW());
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO audit_log (table_name, action, old_data, performed_at)
        VALUES (TG_TABLE_NAME, 'DELETE', row_to_json(OLD), NOW());
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_orders
    AFTER INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION audit_changes();
```

### Trigger Advantages & Disadvantages

| Advantages | Disadvantages |
|-----------|--------------|
| Automatic enforcement | Hidden logic (debugging hard) |
| Consistent across all access paths | Performance overhead per row |
| Audit trails | Can cause cascading triggers |
| Data validation | Testing complexity |

---

## Sequences and Identity

```sql
-- Sequence
CREATE SEQUENCE order_number_seq START 1000 INCREMENT 1;
SELECT nextval('order_number_seq');  -- 1000
SELECT nextval('order_number_seq');  -- 1001
SELECT currval('order_number_seq');  -- 1001 (last value in session)

-- Identity column (modern PostgreSQL, preferred over SERIAL)
CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number TEXT NOT NULL
);

-- SERIAL (older style, creates sequence automatically)
CREATE TABLE legacy_orders (
    id SERIAL PRIMARY KEY  -- Shorthand for: integer + sequence + default
);

-- UUID vs Sequence
-- UUID: Globally unique, no coordination needed, worse index locality
-- Sequence: Ordered, better index locality, single point of contention
```

---

## Interview Questions & Answers

**Q1: When would you use a materialized view vs a regular view?**
- Regular view: When data must always be current, simple query simplification
- Materialized view: Expensive aggregations, reports that tolerate staleness, dashboards refreshed periodically

**Q2: What's the difference between a function and a stored procedure?**
- Function: Returns a value, used in SELECT/WHERE, no transaction control
- Procedure: Can control transactions (COMMIT/ROLLBACK), called with CALL

**Q3: Why are triggers sometimes considered an anti-pattern?**
- Hidden side effects (hard to trace and debug)
- Performance impact (runs on every affected row)
- Can cause infinite loops (trigger A fires trigger B fires trigger A)
- Makes testing harder
- Better alternatives: application-level events, database middleware

---

## Related Topics
- [Topic 14: Indexes](#)
- [Topic 15: Transactions](#)
- [Topic 37: PostgreSQL Specifics](#)
