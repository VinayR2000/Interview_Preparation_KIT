# Topic 20: Normalization & Database Design

## Theory

### What is Normalization?

Normalization is the process of organizing a relational database to reduce data redundancy and improve data integrity. It involves decomposing tables into smaller, well-structured tables while preserving relationships.

### Why Normalize?

```
┌─────────────────────────────────────────────────────────────┐
│            PROBLEMS WITHOUT NORMALIZATION                     │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  1. INSERT ANOMALY:                                          │
│     Can't add a department unless an employee exists         │
│                                                               │
│  2. UPDATE ANOMALY:                                          │
│     Department name changes require updating ALL rows        │
│     Risk of inconsistency if some rows missed                │
│                                                               │
│  3. DELETE ANOMALY:                                          │
│     Deleting last employee in dept loses department info     │
│                                                               │
│  4. DATA REDUNDANCY:                                         │
│     Same department info repeated for every employee         │
│     Wastes storage, increases inconsistency risk             │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Functional Dependency

A functional dependency X → Y means: for every valid instance of X, there is exactly one value of Y associated with it.

```
Example: employee_id → employee_name
         (employee_id determines employee_name)

student_id → {student_name, date_of_birth, address}
         (student_id determines all these attributes)

{student_id, course_id} → grade
         (composite key determines grade)
```

---

## Normal Forms

### 1NF (First Normal Form)

**Rule**: Every column must contain atomic (indivisible) values. No repeating groups or arrays.

```
VIOLATES 1NF:
┌─────────┬──────────────────────────┐
│ emp_id  │ phone_numbers            │
├─────────┼──────────────────────────┤
│ 1       │ 9876543210, 9123456789   │  ← Multi-valued!
│ 2       │ 8765432109               │
└─────────┴──────────────────────────┘

SATISFIES 1NF:
┌─────────┬──────────────┐
│ emp_id  │ phone_number │
├─────────┼──────────────┤
│ 1       │ 9876543210   │
│ 1       │ 9123456789   │
│ 2       │ 8765432109   │
└─────────┴──────────────┘
```

### 2NF (Second Normal Form)

**Rule**: Must be in 1NF + no partial dependency (no non-key attribute depends on only PART of a composite primary key).

```
VIOLATES 2NF:
Table: student_course (PK = {student_id, course_id})
┌────────────┬───────────┬──────────────┬───────┐
│ student_id │ course_id │ course_name  │ grade │
├────────────┼───────────┼──────────────┼───────┤
│ 1          │ CS101     │ Data Struct  │ A     │
│ 2          │ CS101     │ Data Struct  │ B     │  ← course_name repeated!
└────────────┴───────────┴──────────────┴───────┘

Problem: course_name depends ONLY on course_id (partial dependency)
         course_id → course_name (partial key determines non-key attribute)

SATISFIES 2NF (decompose):
Table: enrollments (PK = {student_id, course_id})
┌────────────┬───────────┬───────┐
│ student_id │ course_id │ grade │
└────────────┴───────────┴───────┘

Table: courses (PK = course_id)
┌───────────┬──────────────┐
│ course_id │ course_name  │
└───────────┴──────────────┘
```

### 3NF (Third Normal Form)

**Rule**: Must be in 2NF + no transitive dependency (non-key attribute must not depend on another non-key attribute).

```
VIOLATES 3NF:
Table: employees (PK = emp_id)
┌────────┬──────────┬─────────┬──────────────────┐
│ emp_id │ emp_name │ dept_id │ dept_name        │
├────────┼──────────┼─────────┼──────────────────┤
│ 1      │ Alice    │ D1      │ Engineering      │
│ 2      │ Bob      │ D1      │ Engineering      │  ← dept_name repeated!
└────────┴──────────┴─────────┴──────────────────┘

Problem: emp_id → dept_id → dept_name (transitive dependency)
         dept_name depends on dept_id, not directly on emp_id

SATISFIES 3NF (decompose):
Table: employees (PK = emp_id)
┌────────┬──────────┬─────────┐
│ emp_id │ emp_name │ dept_id │
└────────┴──────────┴─────────┘

Table: departments (PK = dept_id)
┌─────────┬──────────────────┐
│ dept_id │ dept_name        │
└─────────┴──────────────────┘
```

### BCNF (Boyce-Codd Normal Form)

**Rule**: Must be in 3NF + every determinant must be a candidate key.

```
VIOLATES BCNF but satisfies 3NF:
Table: student_advisor (PK = {student_id, subject})
┌────────────┬─────────┬─────────┐
│ student_id │ subject │ advisor │
├────────────┼─────────┼─────────┤
│ 1          │ DB      │ Prof A  │
│ 2          │ DB      │ Prof A  │
│ 1          │ OS      │ Prof B  │
└────────────┴─────────┴─────────┘

FDs: {student_id, subject} → advisor  (PK determines advisor)
     advisor → subject                 (each advisor teaches one subject)

Problem: advisor → subject, but advisor is NOT a candidate key

SATISFIES BCNF (decompose):
Table: advisor_subject (PK = advisor)
┌─────────┬─────────┐
│ advisor │ subject │
└─────────┴─────────┘

Table: student_advisor (PK = {student_id, advisor})
┌────────────┬─────────┐
│ student_id │ advisor │
└────────────┴─────────┘
```

### 4NF (Fourth Normal Form)

**Rule**: Must be in BCNF + no multi-valued dependencies.

```
VIOLATES 4NF:
Table: emp_skills_languages
┌────────┬────────┬──────────┐
│ emp_id │ skill  │ language │
├────────┼────────┼──────────┤
│ 1      │ Java   │ English  │
│ 1      │ Java   │ Hindi    │
│ 1      │ Python │ English  │
│ 1      │ Python │ Hindi    │  ← Cartesian product of independent sets!
└────────┴────────┴──────────┘

Problem: skills and languages are independent of each other
         emp_id →→ skill, emp_id →→ language (multi-valued deps)

SATISFIES 4NF:
Table: emp_skills            Table: emp_languages
┌────────┬────────┐         ┌────────┬──────────┐
│ emp_id │ skill  │         │ emp_id │ language │
└────────┴────────┘         └────────┴──────────┘
```

### 5NF (Fifth Normal Form)

**Rule**: Must be in 4NF + no join dependencies that can't be derived from candidate keys.

```
Rare in practice. Handles cases where a table can be decomposed into
three or more smaller tables and then reconstructed by joining.
Only matters when there's a cyclic constraint.
```

---

## Denormalization

### When to Denormalize

```
┌─────────────────────────────────────────────────────────────┐
│                 DENORMALIZATION DECISIONS                     │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  NORMALIZE WHEN:                                             │
│  • Write-heavy workload (OLTP)                               │
│  • Data integrity is critical                                │
│  • Storage is a concern                                       │
│  • Application is still evolving                             │
│                                                               │
│  DENORMALIZE WHEN:                                           │
│  • Read-heavy workload (OLAP/reporting)                      │
│  • Joins are too expensive (many tables, large datasets)     │
│  • Caching frequently-accessed computed values               │
│  • Performance > consistency trade-off is acceptable         │
│  • Data is relatively static                                 │
│                                                               │
│  COMMON DENORMALIZATION TECHNIQUES:                          │
│  • Redundant columns (store dept_name in employee table)     │
│  • Derived/computed columns (store total_amount)             │
│  • Pre-joined tables (materialized views)                    │
│  • Summary tables (daily_sales_summary)                      │
│  • Nested data (JSON columns for rare lookups)               │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## ER Diagrams & Database Design

### Entity-Relationship Concepts

```
┌─────────────────────────────────────────────────────────────────┐
│                    ER DIAGRAM COMPONENTS                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ENTITY: A thing that exists (table)                             │
│     Example: Customer, Order, Product                            │
│                                                                   │
│  ATTRIBUTE: A property of an entity (column)                     │
│     Example: customer_name, email, created_at                    │
│                                                                   │
│  RELATIONSHIP: How entities relate to each other                 │
│     Example: Customer PLACES Order                               │
│                                                                   │
│  CARDINALITY: How many instances participate                     │
│     1:1   → One-to-One (User ↔ Profile)                         │
│     1:N   → One-to-Many (Department ↔ Employees)                │
│     M:N   → Many-to-Many (Students ↔ Courses)                   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Cardinality Relationships

```
ONE-TO-ONE (1:1):
┌──────────┐       ┌─────────────┐
│  User    │ 1───1 │  Profile    │
│  ------  │       │  ---------  │
│  id (PK) │       │  id (PK)    │
│  name    │       │  user_id(FK)│ ← UNIQUE constraint on FK
│          │       │  bio        │
└──────────┘       └─────────────┘

CREATE TABLE profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    bio TEXT
);


ONE-TO-MANY (1:N):
┌────────────┐         ┌──────────────┐
│ Department │ 1─────N │  Employee    │
│ ---------- │         │ ------------ │
│ id (PK)    │         │ id (PK)      │
│ name       │         │ dept_id (FK) │ ← FK on "many" side
│            │         │ name         │
└────────────┘         └──────────────┘

CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100),
    dept_id BIGINT REFERENCES departments(id)
);


MANY-TO-MANY (M:N):
┌──────────┐       ┌───────────────────┐       ┌──────────┐
│ Student  │ N───M │ student_course    │ M───N │  Course  │
│ -------- │       │ (Junction Table)  │       │ -------- │
│ id (PK)  │       │ student_id (FK)   │       │ id (PK)  │
│ name     │       │ course_id (FK)    │       │ name     │
│          │       │ enrolled_at       │       │ credits  │
└──────────┘       └───────────────────┘       └──────────┘

CREATE TABLE student_course (
    student_id BIGINT REFERENCES students(id),
    course_id BIGINT REFERENCES courses(id),
    enrolled_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (student_id, course_id)
);
```

### Design Best Practices

```sql
-- 1. Audit Columns (every table should have these)
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    total_amount DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    
    -- Audit columns
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- 2. Soft Delete
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(10,2),
    is_deleted BOOLEAN DEFAULT FALSE,   -- Soft delete flag
    deleted_at TIMESTAMP                -- When it was deleted
);

-- Query active products only
SELECT * FROM products WHERE is_deleted = FALSE;

-- 3. Temporal Data (valid time ranges)
CREATE TABLE price_history (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id),
    price DECIMAL(10,2),
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP,  -- NULL = currently active
    EXCLUDE USING gist (
        product_id WITH =,
        tsrange(valid_from, valid_to) WITH &&
    )  -- PostgreSQL: prevent overlapping periods
);

-- 4. Status Enumeration
CREATE TYPE order_status AS ENUM (
    'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'
);

-- 5. Referential Integrity with Cascading
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL
);
```

---

## Dry Run — Normalization Example

```
UNNORMALIZED TABLE (0NF):
┌────────┬──────┬─────────────────────┬──────────────────────────┐
│ ord_id │ cust │ items               │ addresses                │
├────────┼──────┼─────────────────────┼──────────────────────────┤
│ 1      │ John │ Laptop,Mouse        │ 123 St, 456 Ave          │
│ 2      │ Jane │ Keyboard            │ 789 Blvd                 │
└────────┴──────┴─────────────────────┴──────────────────────────┘

→ 1NF (atomic values):
orders: {ord_id, cust_name}
order_items: {ord_id, item_name}
cust_addresses: {cust_name, address}

→ 2NF (remove partial dependencies):
Add proper PKs, ensure non-key attributes depend on whole PK

→ 3NF (remove transitive dependencies):
customers: {cust_id, cust_name}
orders: {ord_id, cust_id}
order_items: {item_id, ord_id, product_id, qty}
products: {product_id, product_name, price}
addresses: {addr_id, cust_id, address_line, city, zip}
```

---

## Real Project Usage

### E-Commerce Database Design

```sql
-- Normalized design for an e-commerce system

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    address_type VARCHAR(20) NOT NULL,  -- BILLING, SHIPPING
    street VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(50),
    zip VARCHAR(20),
    country VARCHAR(50) DEFAULT 'US',
    is_default BOOLEAN DEFAULT FALSE
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES categories(id)  -- Self-referencing for hierarchy
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    category_id BIGINT REFERENCES categories(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    shipping_address_id BIGINT REFERENCES addresses(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(12,2) NOT NULL,
    placed_at TIMESTAMP DEFAULT NOW(),
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL,  -- Price at time of order (denormalized)
    UNIQUE (order_id, product_id)
);

-- Junction table for product tags (M:N)
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE product_tags (
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    tag_id INT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, tag_id)
);
```

---

## Interview Questions and Answers

**Q1: What is normalization? Why do we do it?**
A: Normalization organizes database tables to minimize redundancy and dependency. We do it to prevent anomalies (insert, update, delete), ensure data integrity, and reduce storage waste. It decomposes tables so each table represents one concept.

**Q2: Explain 1NF, 2NF, 3NF with examples.**
A: 
- 1NF: Atomic values only (no comma-separated lists, no arrays in columns)
- 2NF: 1NF + no partial dependencies (non-key attributes depend on entire composite key, not just part)
- 3NF: 2NF + no transitive dependencies (non-key attributes don't depend on other non-key attributes)

**Q3: What is denormalization? When would you use it?**
A: Denormalization deliberately introduces redundancy to improve read performance. Use it for read-heavy workloads, reporting tables, cached computations, or when JOIN costs exceed update costs. Common in OLAP systems and data warehouses.

**Q4: What is a junction/bridge table?**
A: A table that resolves a many-to-many relationship by holding foreign keys to both related tables. Example: `student_course` table with `student_id` and `course_id` as a composite primary key.

**Q5: Explain the difference between BCNF and 3NF.**
A: 3NF allows non-prime attributes to have functional dependencies on candidate keys. BCNF is stricter: every determinant MUST be a candidate key. BCNF eliminates all redundancy due to functional dependencies. 3NF may still have some redundancy if there are overlapping candidate keys.

**Q6: How do you design for soft delete?**
A: Add `is_deleted BOOLEAN DEFAULT FALSE` and `deleted_at TIMESTAMP` columns. Filter queries with `WHERE is_deleted = FALSE`. Consider creating a view for "active" records. Add index on `is_deleted` if table is large.

---

## Follow-up Questions and Answers

**Q: If normalization reduces redundancy, why do most production systems have some denormalization?**
A: Pure normalization optimizes for writes and data integrity but can make reads expensive (many JOINs). In practice, we accept controlled redundancy for critical read paths while keeping the core schema normalized. The key is being intentional about what we denormalize and having mechanisms (triggers, application logic) to keep redundant data consistent.

**Q: How do you decide what normal form to target?**
A: Most OLTP systems target 3NF as a good balance. BCNF is preferred when possible but sometimes causes loss of functional dependency preservation. Data warehouses often use 2NF or star/snowflake schemas. The answer depends on read/write ratio and consistency requirements.

**Q: What's the difference between a natural key and a surrogate key?**
A: Natural key has business meaning (email, SSN, ISBN). Surrogate key is system-generated with no business meaning (auto-increment ID, UUID). Surrogate keys are preferred in most systems because natural keys can change, may be composite, and can have encoding issues.

---

## Common Mistakes

1. **Over-normalizing**: Breaking everything into tiny tables causing excessive JOINs
2. **Under-normalizing**: Keeping everything in one giant table, data becomes inconsistent
3. **Missing junction tables**: Using comma-separated IDs instead of proper M:N relationships
4. **Storing computed values without update logic**: Denormalized total_amount gets stale
5. **Not considering query patterns**: Normalizing a field that's always read together with the parent
6. **Using natural keys as PKs when they might change**: Email as PK → nightmare when email changes
7. **Forgetting audit columns**: No created_at, updated_at on important tables

---

## Best Practices

1. **Start with 3NF**, denormalize only when performance data justifies it
2. **Use surrogate keys** (BIGSERIAL/UUID) as primary keys
3. **Add audit columns** (created_at, updated_at, created_by, updated_by) to every table
4. **Design for soft delete** where business requires audit trail
5. **Name constraints explicitly** for easier debugging
6. **Document your ERD** and keep it updated
7. **Consider future growth** — design for the data volume you'll have in 2 years
8. **Use appropriate data types** — don't use VARCHAR for dates or TEXT for short codes

---

## Production Considerations

- **Schema migrations** should be backward-compatible (add columns as nullable first)
- **Large table denormalization** should be done incrementally with backfill scripts
- **Materialized views** are a good middle ground between full normalization and denormalization
- **Partition large denormalized tables** for manageability
- **Monitor data drift** in denormalized columns — add reconciliation jobs

---

## Related Topics

- Topic 13: Constraints and Keys (key types in detail)
- Topic 14: Indexes (performance implications of schema design)
- Topic 17: Views & Materialized Views (denormalization alternatives)
- Topic 18: Partitioning (managing large tables)
- Topic 19: JPA/Hibernate (object-relational mapping considerations)
