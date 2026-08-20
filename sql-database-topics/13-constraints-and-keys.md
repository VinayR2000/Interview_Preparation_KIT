# Topic 13: Constraints, Keys, and Normalization

## Constraints — Deep Dive

### NOT NULL

```sql
-- Column cannot contain NULL
CREATE TABLE users (
    name VARCHAR(100) NOT NULL  -- Insertion without name will fail
);

-- Adding NOT NULL to existing column (requires backfill first)
UPDATE users SET name = 'Unknown' WHERE name IS NULL;
ALTER TABLE users ALTER COLUMN name SET NOT NULL;
```

### UNIQUE

```sql
-- Single column unique
CREATE TABLE users (
    email VARCHAR(255) UNIQUE  -- Allows one NULL (PostgreSQL)
);

-- Multi-column unique (combination must be unique)
CREATE TABLE enrollment (
    student_id INT,
    course_id INT,
    semester VARCHAR(10),
    UNIQUE (student_id, course_id, semester)
);
```

### CHECK

```sql
-- Column-level check
CREATE TABLE products (
    price NUMERIC CHECK (price > 0),
    discount NUMERIC CHECK (discount BETWEEN 0 AND 1),
    status VARCHAR(20) CHECK (status IN ('active', 'inactive', 'deleted'))
);

-- Table-level check (can reference multiple columns)
CREATE TABLE events (
    start_date DATE,
    end_date DATE,
    CONSTRAINT valid_dates CHECK (end_date > start_date)
);
```

### Foreign Key Actions

```sql
CREATE TABLE orders (
    customer_id INT REFERENCES customers(id)
        ON DELETE CASCADE      -- Delete orders when customer deleted
        ON UPDATE CASCADE      -- Update FK when customer id changes
);

-- All referential actions:
-- CASCADE:     Propagate change to child rows
-- SET NULL:    Set FK to NULL in child rows
-- SET DEFAULT: Set FK to DEFAULT value in child rows
-- RESTRICT:    Prevent action immediately
-- NO ACTION:   Prevent action (but deferred check possible)
```

### Composite Constraints

```sql
-- Composite primary key
CREATE TABLE order_items (
    order_id BIGINT,
    product_id BIGINT,
    quantity INT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Exclusion constraint (PostgreSQL)
CREATE TABLE room_bookings (
    room_id INT,
    during TSRANGE,
    EXCLUDE USING GIST (room_id WITH =, during WITH &&)
    -- No two bookings for same room can overlap
);
```

---

## Normalization

### Functional Dependency

A functional dependency X → Y means: for any two rows, if X values are the same, Y values must be the same.

Example: `emp_id → emp_name` (employee ID determines name)

### Normal Forms

```
┌─────────────────────────────────────────────────────────────────┐
│ 1NF: No repeating groups, atomic values                          │
│      Each cell contains a single value                           │
├─────────────────────────────────────────────────────────────────┤
│ 2NF: 1NF + No partial dependencies                              │
│      Non-key columns depend on ENTIRE primary key                │
├─────────────────────────────────────────────────────────────────┤
│ 3NF: 2NF + No transitive dependencies                           │
│      Non-key columns don't depend on other non-key columns       │
├─────────────────────────────────────────────────────────────────┤
│ BCNF: 3NF + Every determinant is a candidate key                │
│       Stricter version of 3NF                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 1NF Example

```
VIOLATION (repeating groups):
| student | courses              |
|---------|----------------------|
| Alice   | Math, Physics, Chem  |  ← Multiple values in one cell

1NF FIX:
| student | course  |
|---------|---------|
| Alice   | Math    |
| Alice   | Physics |
| Alice   | Chem    |
```

### 2NF Example

```
VIOLATION (partial dependency):
Table: order_items (order_id, product_id, quantity, product_name)
PK: (order_id, product_id)
product_name depends ONLY on product_id (partial dependency!)

2NF FIX:
Table: order_items (order_id, product_id, quantity)
Table: products (product_id, product_name)
```

### 3NF Example

```
VIOLATION (transitive dependency):
Table: employees (emp_id, dept_id, dept_name)
emp_id → dept_id → dept_name (transitive!)
dept_name depends on dept_id, not directly on emp_id

3NF FIX:
Table: employees (emp_id, dept_id)
Table: departments (dept_id, dept_name)
```

### When to Denormalize

| Denormalize When | Keep Normalized When |
|-----------------|---------------------|
| Read-heavy workload | Write-heavy workload |
| Complex joins hurting performance | Data consistency is critical |
| Reporting/analytics | Transactional systems |
| Caching frequently joined data | Storage efficiency matters |
| Calculated fields (totals, counts) | Small to medium datasets |

---

## Database Design

### ER Diagram Relationships

```
One-to-One:
┌──────────┐     1     1    ┌───────────┐
│  users   │────────────────│  profiles │
└──────────┘                 └───────────┘

One-to-Many:
┌──────────────┐  1     *   ┌──────────┐
│ departments  │────────────│ employees│
└──────────────┘            └──────────┘

Many-to-Many:
┌──────────┐  *     *  ┌──────────┐
│ students │────────────│ courses  │
└──────────┘     │      └──────────┘
                 │
        ┌────────────────┐
        │  enrollments   │  (junction table)
        │ student_id (FK)│
        │ course_id (FK) │
        └────────────────┘
```

### Production Table Design Patterns

```sql
-- Audit columns (add to every table)
CREATE TABLE base_entity (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id),
    version INTEGER DEFAULT 0  -- Optimistic locking
);

-- Soft delete pattern
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by BIGINT REFERENCES users(id)
);
-- Add partial index for active records
CREATE INDEX idx_active_products ON products(id) WHERE is_deleted = FALSE;

-- Temporal/Versioned data
CREATE TABLE product_prices (
    product_id BIGINT REFERENCES products(id),
    price NUMERIC(10, 2) NOT NULL,
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP,  -- NULL = currently active
    PRIMARY KEY (product_id, effective_from)
);
```

---

## Interview Questions & Answers

**Q1: What's the difference between 2NF and 3NF?**
- 2NF eliminates partial dependencies (non-key depends on part of composite PK)
- 3NF eliminates transitive dependencies (non-key depends on another non-key)

**Q2: When would you intentionally denormalize?**
- Reporting dashboards needing fast reads
- Storing calculated totals (order_total instead of computing from items)
- Caching joined data for API performance
- Example: Store `customer_name` in orders table for fast display

**Q3: Explain CASCADE vs RESTRICT.**
- CASCADE: Automatically propagate the operation (delete parent → delete children)
- RESTRICT: Block the operation if children exist

**Q4: What's the difference between UNIQUE and PRIMARY KEY?**
- PK: NOT NULL + UNIQUE, one per table, creates clustered index
- UNIQUE: Allows NULL (one in most DB), multiple per table, non-clustered index

---

## Related Topics
- [Topic 1: Database Fundamentals](#)
- [Topic 17: Database Design](#)
- [Topic 18: Indexes](#)
