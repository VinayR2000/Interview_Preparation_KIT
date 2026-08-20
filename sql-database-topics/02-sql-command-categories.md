# Topic 2: SQL Command Categories

## Theory

SQL commands are divided into five categories based on their purpose:

```
┌─────────────────────────────────────────────────────────────────┐
│                        SQL COMMANDS                               │
├──────────┬──────────┬──────────┬──────────┬─────────────────────┤
│   DDL    │   DML    │   DQL    │   DCL    │        TCL          │
├──────────┼──────────┼──────────┼──────────┼─────────────────────┤
│ CREATE   │ INSERT   │ SELECT   │ GRANT    │ COMMIT              │
│ ALTER    │ UPDATE   │          │ REVOKE   │ ROLLBACK            │
│ DROP     │ DELETE   │          │          │ SAVEPOINT           │
│ TRUNCATE │ MERGE    │          │          │                     │
│ RENAME   │          │          │          │                     │
└──────────┴──────────┴──────────┴──────────┴─────────────────────┘
```

### DDL — Data Definition Language

Defines and modifies database structure. Changes are **auto-committed** (in most RDBMS).

| Command | Purpose |
|---------|---------|
| CREATE | Create new database objects |
| ALTER | Modify existing objects |
| DROP | Delete objects permanently |
| TRUNCATE | Remove all rows from table |
| RENAME | Rename objects |

### DML — Data Manipulation Language

Manipulates data within tables. Changes are **transactional** (can be rolled back).

| Command | Purpose |
|---------|---------|
| INSERT | Add new rows |
| UPDATE | Modify existing rows |
| DELETE | Remove specific rows |
| MERGE | Insert or update (UPSERT) |

### DQL — Data Query Language

Retrieves data. Does not modify anything.

| Command | Purpose |
|---------|---------|
| SELECT | Query and retrieve data |

### DCL — Data Control Language

Controls access permissions.

| Command | Purpose |
|---------|---------|
| GRANT | Give privileges to users |
| REVOKE | Remove privileges from users |

### TCL — Transaction Control Language

Manages transactions.

| Command | Purpose |
|---------|---------|
| COMMIT | Save changes permanently |
| ROLLBACK | Undo changes |
| SAVEPOINT | Create rollback point within transaction |

---

## Internal Working

### How DDL Works Internally

```
┌─────────────────────────────────────────────────────────┐
│                  CREATE TABLE Statement                    │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  1. Parser → Validates syntax                            │
│  2. Catalog Update → Adds metadata to system tables     │
│      • pg_class (table info)                             │
│      • pg_attribute (column info)                        │
│      • pg_constraint (constraints)                       │
│      • pg_index (indexes)                                │
│  3. Storage Allocation → Creates physical files          │
│  4. WAL Entry → Logs the change for crash recovery      │
│  5. Auto-Commit → DDL is immediately committed          │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

### How DML Works Internally (PostgreSQL MVCC)

```
INSERT:
┌─────────────────────────────────────────────┐
│ 1. Acquire row lock                          │
│ 2. Find free space in a page (8KB block)     │
│ 3. Write tuple with xmin = current txn ID    │
│ 4. Update indexes                            │
│ 5. Write to WAL (Write-Ahead Log)            │
│ 6. Return success                            │
└─────────────────────────────────────────────┘

UPDATE (in PostgreSQL = DELETE old + INSERT new):
┌─────────────────────────────────────────────┐
│ 1. Find existing tuple                       │
│ 2. Mark old tuple as dead (set xmax)         │
│ 3. Insert new tuple with updated values      │
│ 4. Update indexes to point to new tuple      │
│ 5. Write to WAL                              │
└─────────────────────────────────────────────┘

DELETE:
┌─────────────────────────────────────────────┐
│ 1. Find tuple                                │
│ 2. Set xmax = current txn ID                 │
│ 3. Tuple is now "invisible" to new txns      │
│ 4. Write to WAL                              │
│ 5. Space reclaimed by VACUUM later           │
└─────────────────────────────────────────────┘
```

---

## Code Examples

### DDL — CREATE

```sql
-- Create database
CREATE DATABASE ecommerce
    WITH OWNER = app_user
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8';

-- Create schema
CREATE SCHEMA inventory;

-- Create table with all constraint types
CREATE TABLE inventory.products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    cost NUMERIC(10, 2) CHECK (cost >= 0),
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
    weight_kg NUMERIC(6, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Table-level constraint
    CONSTRAINT price_above_cost CHECK (price > COALESCE(cost, 0))
);

-- Create index
CREATE INDEX idx_products_category ON inventory.products(category_id);
CREATE INDEX idx_products_active ON inventory.products(is_active) WHERE is_active = TRUE;

-- Create sequence
CREATE SEQUENCE order_number_seq START 1000 INCREMENT 1;
```

### DDL — ALTER

```sql
-- Add column
ALTER TABLE products ADD COLUMN brand VARCHAR(100);

-- Drop column
ALTER TABLE products DROP COLUMN IF EXISTS brand;

-- Rename column
ALTER TABLE products RENAME COLUMN name TO product_name;

-- Change data type
ALTER TABLE products ALTER COLUMN description TYPE TEXT;

-- Add constraint
ALTER TABLE products ADD CONSTRAINT chk_weight CHECK (weight_kg > 0);

-- Drop constraint
ALTER TABLE products DROP CONSTRAINT chk_weight;

-- Set/drop default
ALTER TABLE products ALTER COLUMN is_active SET DEFAULT TRUE;
ALTER TABLE products ALTER COLUMN is_active DROP DEFAULT;

-- Set/drop NOT NULL
ALTER TABLE products ALTER COLUMN brand SET NOT NULL;
ALTER TABLE products ALTER COLUMN brand DROP NOT NULL;

-- Rename table
ALTER TABLE products RENAME TO product_catalog;
```

### DDL — DROP & TRUNCATE

```sql
-- Drop table (with dependent objects)
DROP TABLE IF EXISTS products CASCADE;

-- Drop schema with all objects
DROP SCHEMA inventory CASCADE;

-- Truncate — removes all rows, resets identity
TRUNCATE TABLE products;
TRUNCATE TABLE products RESTART IDENTITY;  -- Reset serial
TRUNCATE TABLE orders, order_items CASCADE; -- Multiple tables
```

### DML — INSERT

```sql
-- Single row
INSERT INTO products (sku, name, price, stock)
VALUES ('SKU-001', 'Widget', 29.99, 100);

-- Multiple rows
INSERT INTO products (sku, name, price, stock) VALUES
    ('SKU-002', 'Gadget', 49.99, 50),
    ('SKU-003', 'Doohickey', 9.99, 200),
    ('SKU-004', 'Thingamajig', 19.99, 75);

-- Insert from SELECT
INSERT INTO product_archive (sku, name, price, archived_at)
SELECT sku, name, price, NOW()
FROM products
WHERE is_active = FALSE;

-- INSERT with RETURNING (PostgreSQL)
INSERT INTO products (sku, name, price, stock)
VALUES ('SKU-005', 'Gizmo', 39.99, 30)
RETURNING id, sku, created_at;

-- UPSERT — ON CONFLICT (PostgreSQL)
INSERT INTO products (sku, name, price, stock)
VALUES ('SKU-001', 'Widget Pro', 34.99, 150)
ON CONFLICT (sku)
DO UPDATE SET
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    stock = EXCLUDED.stock,
    updated_at = NOW();

-- ON CONFLICT DO NOTHING
INSERT INTO products (sku, name, price, stock)
VALUES ('SKU-001', 'Widget', 29.99, 100)
ON CONFLICT (sku) DO NOTHING;
```

### DML — UPDATE

```sql
-- Simple update
UPDATE products SET price = 34.99 WHERE sku = 'SKU-001';

-- Update multiple columns
UPDATE products
SET price = price * 1.10,    -- 10% price increase
    updated_at = NOW()
WHERE category_id = 5;

-- Update with subquery
UPDATE products p
SET category_id = (
    SELECT id FROM categories WHERE name = 'Electronics'
)
WHERE p.sku LIKE 'ELEC-%';

-- Update with JOIN (PostgreSQL)
UPDATE order_items oi
SET unit_price = p.price
FROM products p
WHERE oi.product_id = p.id
AND oi.unit_price <> p.price;

-- Update with RETURNING
UPDATE products
SET stock = stock - 1
WHERE sku = 'SKU-001' AND stock > 0
RETURNING id, sku, stock;
```

### DML — DELETE

```sql
-- Delete specific rows
DELETE FROM products WHERE is_active = FALSE;

-- Delete with subquery
DELETE FROM products
WHERE id NOT IN (
    SELECT DISTINCT product_id FROM order_items
);

-- Delete with JOIN (PostgreSQL)
DELETE FROM order_items oi
USING orders o
WHERE oi.order_id = o.id
AND o.status = 'cancelled';

-- Delete with RETURNING
DELETE FROM products
WHERE stock = 0 AND is_active = FALSE
RETURNING id, sku, name;

-- Delete all rows (prefer TRUNCATE for this)
DELETE FROM temp_data;
```

### DML — MERGE (SQL Standard / Oracle / SQL Server)

```sql
-- MERGE (not native PostgreSQL, use ON CONFLICT instead)
-- SQL Server syntax:
MERGE INTO products AS target
USING staging_products AS source
ON target.sku = source.sku
WHEN MATCHED THEN
    UPDATE SET
        target.price = source.price,
        target.stock = source.stock
WHEN NOT MATCHED THEN
    INSERT (sku, name, price, stock)
    VALUES (source.sku, source.name, source.price, source.stock)
WHEN NOT MATCHED BY SOURCE THEN
    DELETE;
```

### DCL — GRANT & REVOKE

```sql
-- Create role
CREATE ROLE app_readonly;
CREATE ROLE app_readwrite;

-- Grant privileges
GRANT CONNECT ON DATABASE ecommerce TO app_readonly;
GRANT USAGE ON SCHEMA public TO app_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_readwrite;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO app_readwrite;

-- Grant to specific user
GRANT app_readonly TO reporting_user;
GRANT app_readwrite TO application_user;

-- Revoke
REVOKE DELETE ON products FROM app_readwrite;
REVOKE ALL PRIVILEGES ON orders FROM intern_user;

-- Default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT ON TABLES TO app_readonly;
```

### TCL — Transaction Control

```sql
-- Basic transaction
BEGIN;
    UPDATE accounts SET balance = balance - 500 WHERE id = 1;
    UPDATE accounts SET balance = balance + 500 WHERE id = 2;
COMMIT;

-- Transaction with rollback
BEGIN;
    INSERT INTO orders (customer_id, total) VALUES (1, 99.99);
    -- Something goes wrong...
ROLLBACK;  -- All changes undone

-- Savepoints
BEGIN;
    INSERT INTO orders (customer_id, total) VALUES (1, 199.99);
    SAVEPOINT order_created;
    
    INSERT INTO order_items (order_id, product_id, qty) VALUES (1, 5, 2);
    -- Oops, wrong product
    ROLLBACK TO SAVEPOINT order_created;
    
    INSERT INTO order_items (order_id, product_id, qty) VALUES (1, 7, 2);
COMMIT;  -- Order saved with correct item
```

---

## Dry Run

### Transaction with Savepoint

```sql
-- Initial state: accounts table
-- | id | name  | balance |
-- |----|-------|---------|
-- | 1  | Alice | 1000    |
-- | 2  | Bob   | 500     |

BEGIN;
-- Transaction starts, txn_id = 100

UPDATE accounts SET balance = balance - 200 WHERE id = 1;
-- Alice: 1000 → 800  (visible only within txn 100)

SAVEPOINT sp1;

UPDATE accounts SET balance = balance + 200 WHERE id = 2;
-- Bob: 500 → 700  (visible only within txn 100)

-- Oh no, we need to send to a different account!
ROLLBACK TO SAVEPOINT sp1;
-- Bob: 700 → 500 (reverted to state at sp1)
-- Alice: still 800 (not reverted, before savepoint)

UPDATE accounts SET balance = balance + 200 WHERE id = 3;
-- Assuming id=3 (Charlie): gets +200

COMMIT;
-- Final state:
-- | id | name    | balance |
-- |----|---------|---------|
-- | 1  | Alice   | 800     |  ← decreased
-- | 2  | Bob     | 500     |  ← unchanged (rolled back)
-- | 3  | Charlie | 200     |  ← increased (assuming started at 0)
```

---

## Complexity

| Operation | Lock Type | Log Generated | Rollback Impact |
|-----------|-----------|---------------|-----------------|
| CREATE TABLE | Schema lock | WAL entry | Auto-committed (no rollback in most RDBMS) |
| INSERT 1 row | Row lock | WAL entry | Fast |
| UPDATE 1 row | Row lock | WAL entry (old + new) | Fast |
| DELETE 1 row | Row lock | WAL entry | Fast |
| INSERT N rows | N row locks | N WAL entries | Proportional to N |
| TRUNCATE | Table lock | Minimal WAL | Fast (deallocates pages) |
| DROP TABLE | Schema lock | WAL entry | Not reversible |

---

## Real Project Usage

### Database Setup Script for a Spring Boot Application

```sql
-- V1__init_schema.sql (Flyway migration)

-- Create application schemas
CREATE SCHEMA IF NOT EXISTS app;
CREATE SCHEMA IF NOT EXISTS audit;

-- Core tables
CREATE TABLE app.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE app.roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE app.user_roles (
    user_id BIGINT REFERENCES app.users(id) ON DELETE CASCADE,
    role_id INTEGER REFERENCES app.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Audit table using JSONB
CREATE TABLE audit.activity_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create application roles with least privilege
CREATE ROLE app_service LOGIN PASSWORD 'secure_password';
GRANT USAGE ON SCHEMA app TO app_service;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA app TO app_service;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA app TO app_service;

CREATE ROLE audit_service LOGIN PASSWORD 'audit_password';
GRANT USAGE ON SCHEMA audit TO audit_service;
GRANT INSERT ON audit.activity_log TO audit_service;
GRANT USAGE ON SEQUENCE audit.activity_log_id_seq TO audit_service;
```

---

## Interview Questions & Answers

**Q1: What's the difference between TRUNCATE and DELETE?**

| Aspect | DELETE | TRUNCATE |
|--------|--------|----------|
| Type | DML | DDL |
| WHERE clause | Supported | Not supported |
| Rollback | Yes (transactional) | Generally no (auto-commits); PostgreSQL allows rollback |
| Triggers | Fires row triggers | Does not fire row triggers |
| Space | Doesn't release immediately | Releases immediately |
| Identity/Serial | Not reset | Can reset (RESTART IDENTITY) |
| Speed | Slow (row-by-row) | Very fast (drops pages) |
| Vacuum needed | Yes (PostgreSQL) | No |
| Foreign keys | Works with FKs | Fails if referenced by FK (unless CASCADE) |

**Important PostgreSQL exception**: TRUNCATE IS transactional in PostgreSQL (can be rolled back).

**Q2: Can DDL be rolled back?**

- **PostgreSQL**: YES. DDL is transactional. You can `BEGIN; CREATE TABLE...; ROLLBACK;`
- **Oracle**: NO. DDL causes implicit commit before and after.
- **SQL Server**: YES, within explicit transaction.
- **MySQL**: NO (for most DDL operations).

**Q3: What is MERGE and why don't we have it in PostgreSQL?**

MERGE combines INSERT, UPDATE, DELETE in one statement based on matching criteria. PostgreSQL uses `INSERT ... ON CONFLICT` (UPSERT) instead. PostgreSQL 15+ added MERGE support.

**Q4: Explain the difference between GRANT and REVOKE with a scenario.**

```sql
-- Scenario: New developer joins team
-- 1. Create role
CREATE ROLE developer LOGIN PASSWORD 'dev123';

-- 2. Grant read access to all tables
GRANT SELECT ON ALL TABLES IN SCHEMA public TO developer;

-- 3. Grant write access to specific tables
GRANT INSERT, UPDATE ON public.feature_flags TO developer;

-- 4. Developer moves to different team — revoke sensitive access
REVOKE ALL PRIVILEGES ON public.users TO developer;
REVOKE ALL PRIVILEGES ON public.payment_info TO developer;
```

**Q5: What happens if you UPDATE without a WHERE clause?**

ALL rows in the table are updated. This is a common production disaster.

Prevention strategies:
- Enable `safe_updates` mode (MySQL)
- Use transactions: `BEGIN; UPDATE...; SELECT...; COMMIT or ROLLBACK;`
- Use `RETURNING` to verify affected rows before committing
- Code review and parameterized queries

---

## Follow-up Questions & Answers

**Q: Does TRUNCATE reset sequences?**
Not by default. Use `TRUNCATE table RESTART IDENTITY` to reset associated sequences.

**Q: Can you GRANT privileges on a column level?**
Yes: `GRANT SELECT (name, email) ON users TO analyst;`

**Q: What happens to views when you DROP a table?**
Views that depend on the dropped table become invalid. Use `DROP TABLE ... CASCADE` to also drop dependent views.

**Q: Is SELECT ... FOR UPDATE considered DML or DQL?**
It's technically DQL (SELECT) but it acquires exclusive row locks like DML.

---

## Common Mistakes

1. **Forgetting WHERE in UPDATE/DELETE** — updates/deletes all rows
2. **Using DELETE instead of TRUNCATE** for clearing large tables — much slower, generates huge WAL
3. **Not using transactions** for multi-statement operations
4. **Granting excessive privileges** — use least-privilege principle
5. **Not using IF EXISTS / IF NOT EXISTS** in DDL:
   ```sql
   DROP TABLE IF EXISTS temp_data;            -- Safe
   CREATE TABLE IF NOT EXISTS temp_data (...); -- Safe
   ```
6. **Truncating tables with foreign key references** without CASCADE

---

## Best Practices

1. **Always use transactions** for DML involving multiple related changes
2. **Use RETURNING** in PostgreSQL for INSERT/UPDATE/DELETE to get affected data
3. **Use ON CONFLICT** instead of check-then-insert patterns (avoids race conditions)
4. **Use IF EXISTS/IF NOT EXISTS** for idempotent DDL scripts
5. **Name all constraints** explicitly for better error messages
6. **Use schema-qualified table names** in production code
7. **Test DDL changes in a transaction** before committing:
   ```sql
   BEGIN;
   ALTER TABLE products ADD COLUMN ... ;
   -- Verify
   SELECT * FROM products LIMIT 5;
   COMMIT;  -- or ROLLBACK if issues
   ```

---

## Production Considerations

1. **DDL and Locks**: ALTER TABLE can acquire ACCESS EXCLUSIVE lock, blocking all reads/writes
   - Solution: Use `CONCURRENTLY` for index creation
   - Solution: Use lock_timeout to avoid long waits

2. **Large table operations**:
   ```sql
   SET lock_timeout = '5s';  -- Don't wait forever
   ALTER TABLE large_table ADD COLUMN new_col INTEGER;  -- Instant in PostgreSQL 11+
   ```

3. **Online schema migration tools**: For MySQL, use pt-online-schema-change or gh-ost

4. **Audit all DDL changes**: Use event triggers in PostgreSQL
   ```sql
   CREATE EVENT TRIGGER audit_ddl ON ddl_command_end
   EXECUTE FUNCTION log_ddl_changes();
   ```

5. **TRUNCATE in production**: Be extremely careful — it's irreversible in most databases (except PostgreSQL in a transaction)

---

## Related Topics
- [Topic 3: SELECT Fundamentals](#)
- [Topic 20: Transactions](#)
- [Topic 21: ACID](#)
- [Topic 36: Security](#)
