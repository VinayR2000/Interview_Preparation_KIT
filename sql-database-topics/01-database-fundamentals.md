# Topic 1: Database Fundamentals

## Theory

### Database vs DBMS vs RDBMS

| Concept | Definition | Example |
|---------|-----------|---------|
| **Database** | Organized collection of structured data stored electronically | A file containing employee records |
| **DBMS** | Software that manages databases - provides tools to create, read, update, delete data | MongoDB, Redis, Neo4j |
| **RDBMS** | DBMS that stores data in tables with relationships between them using relational model | PostgreSQL, MySQL, Oracle, SQL Server |

**Key distinction**: All RDBMS are DBMS, but not all DBMS are RDBMS. RDBMS enforces ACID properties and uses SQL.

### Relational Database Concepts

A relational database organizes data into **relations** (tables) where:
- Each table represents an entity (Employee, Department, Order)
- Each row (tuple) represents a single record
- Each column (attribute) represents a property
- Relationships between tables are established via foreign keys

### Tables, Rows, Columns

```
┌─────────────────────────────────────────────────┐
│                  EMPLOYEE TABLE                   │
├─────────┬──────────┬────────────┬───────────────┤
│ emp_id  │ name     │ department │ salary        │  ← Column Headers (Attributes)
├─────────┼──────────┼────────────┼───────────────┤
│ 1       │ Alice    │ Engineering│ 95000         │  ← Row (Tuple/Record)
│ 2       │ Bob      │ Marketing  │ 75000         │
│ 3       │ Charlie  │ Engineering│ 88000         │
└─────────┴──────────┴────────────┴───────────────┘
     ↑          ↑           ↑            ↑
   Column    Column      Column       Column
```

### Schema

A **schema** is the logical structure/blueprint of a database:
- Defines tables, columns, data types, constraints, relationships
- In PostgreSQL, schema is also a namespace within a database (e.g., `public`, `hr`, `sales`)

```sql
-- PostgreSQL: Creating a schema
CREATE SCHEMA hr;

CREATE TABLE hr.employees (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    hire_date DATE DEFAULT CURRENT_DATE
);
```

### Catalog

A **catalog** is metadata about the database itself (information_schema):
- Contains information about all schemas, tables, columns, constraints
- System tables that describe the database structure

```sql
-- Query the catalog
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public';
```

### Data Types

| Category | PostgreSQL Types | Description |
|----------|-----------------|-------------|
| **Integer** | SMALLINT, INTEGER, BIGINT | Whole numbers |
| **Decimal** | NUMERIC(p,s), DECIMAL | Exact decimal |
| **Floating** | REAL, DOUBLE PRECISION | Approximate decimal |
| **Serial** | SERIAL, BIGSERIAL | Auto-increment |
| **Character** | CHAR(n), VARCHAR(n), TEXT | Strings |
| **Boolean** | BOOLEAN | true/false |
| **Date/Time** | DATE, TIME, TIMESTAMP, INTERVAL | Temporal |
| **Binary** | BYTEA | Binary data |
| **JSON** | JSON, JSONB | JSON documents |
| **Array** | INTEGER[], TEXT[] | Arrays |
| **UUID** | UUID | Universally unique ID |
| **Network** | INET, CIDR, MACADDR | Network addresses |

### NULL

**NULL** represents the absence of a value — it is NOT zero, NOT empty string, NOT false.

Key behaviors:
- `NULL = NULL` → NULL (not TRUE)
- `NULL <> NULL` → NULL (not TRUE)
- `NULL + 5` → NULL
- `NULL AND TRUE` → NULL
- `NULL OR TRUE` → TRUE
- Use `IS NULL` / `IS NOT NULL` to check

```sql
-- Wrong
SELECT * FROM employees WHERE manager_id = NULL;  -- Always returns 0 rows

-- Correct
SELECT * FROM employees WHERE manager_id IS NULL;
```

### Constraints

Constraints enforce rules on data:

| Constraint | Purpose |
|-----------|---------|
| NOT NULL | Column cannot have NULL |
| UNIQUE | All values must be distinct |
| PRIMARY KEY | NOT NULL + UNIQUE, identifies row |
| FOREIGN KEY | References primary key of another table |
| CHECK | Custom validation condition |
| DEFAULT | Default value if none provided |
| EXCLUSION | Ensures no two rows satisfy a condition (PostgreSQL) |

```sql
CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES customers(id),
    amount NUMERIC(10, 2) CHECK (amount > 0),
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_status CHECK (status IN ('pending', 'processing', 'shipped', 'delivered'))
);
```

### Referential Integrity

Ensures that foreign key values always reference a valid primary key:
- Cannot insert a row with a FK value that doesn't exist in parent
- Cannot delete a parent row if child rows reference it (unless cascade is set)

```
┌──────────────┐          ┌──────────────────┐
│  departments │          │    employees     │
├──────────────┤          ├──────────────────┤
│ id (PK)      │◄─────────│ dept_id (FK)     │
│ name         │          │ id (PK)          │
│ budget       │          │ name             │
└──────────────┘          └──────────────────┘

If dept_id = 5 in employees, there MUST be id = 5 in departments
```

---

## Keys - Deep Dive

### Primary Key
- Uniquely identifies each row in a table
- Cannot be NULL
- Only ONE per table
- Creates a clustered index (in most RDBMS)

```sql
CREATE TABLE students (
    student_id INTEGER PRIMARY KEY,  -- Single column PK
    name VARCHAR(100)
);
```

### Foreign Key
- References the primary key of another table
- Establishes relationship between tables
- CAN be NULL (optional relationship)
- CAN have duplicates

```sql
CREATE TABLE enrollments (
    id SERIAL PRIMARY KEY,
    student_id INTEGER REFERENCES students(student_id),
    course_id INTEGER REFERENCES courses(course_id)
);
```

### Candidate Key
- Any column or set of columns that COULD be a primary key
- Must be unique and not null
- A table can have multiple candidate keys; one is chosen as PK

Example: In a `users` table:
- `user_id` → candidate key
- `email` → candidate key
- `ssn` → candidate key
- Choose `user_id` as PK; others become alternate keys

### Super Key
- Any set of columns that uniquely identifies a row
- Includes candidate keys plus any additional columns
- {emp_id} is a super key
- {emp_id, name} is also a super key (but not minimal)

### Alternate Key
- Candidate keys that were NOT chosen as primary key
- Usually enforced with UNIQUE constraint

```sql
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,        -- Chosen as PK
    email VARCHAR(255) UNIQUE NOT NULL, -- Alternate key
    ssn CHAR(11) UNIQUE               -- Alternate key
);
```

### Composite Key
- Primary key consisting of TWO or more columns
- Used when no single column can uniquely identify a row

```sql
CREATE TABLE enrollment (
    student_id INTEGER REFERENCES students(id),
    course_id INTEGER REFERENCES courses(id),
    semester VARCHAR(10),
    grade CHAR(2),
    PRIMARY KEY (student_id, course_id, semester)  -- Composite key
);
```

### Surrogate Key
- System-generated artificial key (no business meaning)
- SERIAL, BIGSERIAL, UUID
- Stable — doesn't change when business data changes

```sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,  -- Surrogate key
    sku VARCHAR(50) UNIQUE,    -- Natural key (business identifier)
    name VARCHAR(200)
);
```

### Natural Key
- Key derived from business data (has real-world meaning)
- Examples: SSN, email, ISBN, VIN

**Surrogate vs Natural Key Trade-offs:**

| Aspect | Surrogate | Natural |
|--------|-----------|---------|
| Stability | Never changes | May change |
| Size | Fixed, small | Variable |
| Meaning | None | Business meaning |
| Performance | Usually better (integer) | May be slower (string) |
| Joins | Simpler | Carries meaning |
| Data integrity | Need additional UNIQUE on natural | Built-in |

---

## Internal Working

### How RDBMS Stores Data

```
┌──────────────────────────────────────────────────┐
│                    DATABASE                        │
├──────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐               │
│  │  Tablespace │  │  Tablespace │               │
│  ├─────────────┤  ├─────────────┤               │
│  │ ┌─────────┐ │  │ ┌─────────┐ │               │
│  │ │  File   │ │  │ │  File   │ │               │
│  │ ├─────────┤ │  │ ├─────────┤ │               │
│  │ │ Block 1 │ │  │ │ Block 1 │ │               │
│  │ │ Block 2 │ │  │ │ Block 2 │ │               │
│  │ │ Block 3 │ │  │ │ Block 3 │ │               │
│  │ └─────────┘ │  │ └─────────┘ │               │
│  └─────────────┘  └─────────────┘               │
└──────────────────────────────────────────────────┘

Each Block (Page) = 8KB (PostgreSQL default)
Contains: Page Header + Row Pointers + Rows + Free Space
```

### PostgreSQL Page Layout

```
┌──────────────────────────────────┐
│         Page Header (24 bytes)    │
├──────────────────────────────────┤
│  Item Pointer 1 → Row offset     │
│  Item Pointer 2 → Row offset     │
│  Item Pointer 3 → Row offset     │
├──────────────────────────────────┤
│                                  │
│          Free Space              │
│                                  │
├──────────────────────────────────┤
│  Row 3 (Tuple)                   │
│  Row 2 (Tuple)                   │
│  Row 1 (Tuple)                   │
├──────────────────────────────────┤
│      Special Space (for indexes) │
└──────────────────────────────────┘
```

---

## Code Examples

### Complete Table Creation with All Key Types

```sql
-- Database and Schema setup
CREATE DATABASE company_db;

-- Departments table with surrogate key
CREATE TABLE departments (
    dept_id SERIAL PRIMARY KEY,           -- Surrogate key
    dept_code CHAR(4) UNIQUE NOT NULL,    -- Natural/Alternate key
    dept_name VARCHAR(100) NOT NULL,
    budget NUMERIC(12, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Employees table demonstrating foreign key
CREATE TABLE employees (
    emp_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,   -- Alternate key
    ssn CHAR(11) UNIQUE,                  -- Alternate key
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    dept_id INTEGER REFERENCES departments(dept_id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    salary NUMERIC(10, 2) CHECK (salary >= 0),
    hire_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN DEFAULT TRUE
);

-- Composite key example
CREATE TABLE project_assignments (
    emp_id BIGINT REFERENCES employees(emp_id),
    project_id INTEGER REFERENCES projects(project_id),
    role VARCHAR(50) NOT NULL,
    assigned_date DATE DEFAULT CURRENT_DATE,
    PRIMARY KEY (emp_id, project_id)       -- Composite key
);

-- Using UUID as surrogate key
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE audit_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_name VARCHAR(100) NOT NULL,
    action VARCHAR(10) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    performed_by BIGINT REFERENCES employees(emp_id),
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Working with NULLs

```sql
-- COALESCE - returns first non-null value
SELECT 
    emp_id,
    first_name,
    COALESCE(phone, email, 'No Contact') AS contact_info,
    COALESCE(bonus, 0) + salary AS total_compensation
FROM employees;

-- NULLIF - returns NULL if two values are equal
SELECT 
    product_name,
    NULLIF(discount_price, 0) AS effective_discount  -- Avoid division by zero
FROM products;

-- NULL-safe comparison
SELECT * FROM employees 
WHERE dept_id IS DISTINCT FROM 5;  -- PostgreSQL: treats NULL as a comparable value
```

---

## Dry Run

### Scenario: Insert with Referential Integrity Check

```sql
-- Given:
-- departments: {dept_id: 1, dept_code: 'ENG'}, {dept_id: 2, dept_code: 'MKT'}
-- employees: empty

-- Step 1: Valid insert
INSERT INTO employees (first_name, last_name, email, dept_id, salary)
VALUES ('Alice', 'Smith', 'alice@co.com', 1, 95000);
-- Result: ✓ Success (dept_id=1 exists in departments)

-- Step 2: Invalid insert
INSERT INTO employees (first_name, last_name, email, dept_id, salary)
VALUES ('Bob', 'Jones', 'bob@co.com', 99, 75000);
-- Result: ✗ ERROR: insert or update on table "employees" violates 
--         foreign key constraint (dept_id=99 doesn't exist)

-- Step 3: NULL foreign key (allowed if column is nullable)
INSERT INTO employees (first_name, last_name, email, dept_id, salary)
VALUES ('Charlie', 'Brown', 'charlie@co.com', NULL, 80000);
-- Result: ✓ Success (NULL FK is valid — employee with no department)

-- Step 4: Try to delete referenced department
DELETE FROM departments WHERE dept_id = 1;
-- Result: Depends on ON DELETE action:
--   RESTRICT/NO ACTION: ✗ ERROR (Alice references it)
--   CASCADE: ✓ Deletes department AND Alice
--   SET NULL: ✓ Deletes department, Alice.dept_id becomes NULL
--   SET DEFAULT: ✓ Deletes department, Alice.dept_id becomes DEFAULT
```

---

## Complexity

| Operation | Without Index | With Index |
|-----------|--------------|------------|
| Find by PK | O(n) | O(log n) |
| Insert with FK check | O(n) per FK | O(log n) per FK |
| Constraint check (UNIQUE) | O(n) | O(log n) via unique index |
| Full table scan | O(n) | O(n) |

---

## Real Project Usage

### E-Commerce Database Foundation

```sql
-- Product catalog with proper keys and constraints
CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,     -- Natural key for URLs
    name VARCHAR(200) NOT NULL,
    parent_id INTEGER REFERENCES categories(id),
    display_order INTEGER DEFAULT 0
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,       -- Business identifier
    name VARCHAR(300) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    category_id INTEGER REFERENCES categories(id),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(20) UNIQUE NOT NULL,  -- Business-facing ID
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending','confirmed','shipped','delivered','cancelled')),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    order_id BIGINT REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id)   -- Composite key
);
```

---

## Interview Questions & Answers

**Q1: What is the difference between DELETE, TRUNCATE, and DROP?**

| Feature | DELETE | TRUNCATE | DROP |
|---------|--------|----------|------|
| What it removes | Specific rows | All rows | Entire table |
| WHERE clause | Yes | No | No |
| Rollback | Yes (logged) | Depends on DB | No |
| Triggers fired | Yes | No | No |
| Space reclaimed | No (until VACUUM) | Yes | Yes |
| Identity reset | No | Yes (RESTART) | N/A |
| Speed | Slow (row-by-row) | Fast | Fast |
| DML/DDL | DML | DDL | DDL |

**Q2: What's the difference between UNIQUE constraint and PRIMARY KEY?**

| Aspect | PRIMARY KEY | UNIQUE |
|--------|-------------|--------|
| NULL allowed | No | Yes (one NULL per column) |
| Per table | Only one | Multiple |
| Clustered index | Yes (default in most) | No |
| Purpose | Row identifier | Enforce uniqueness |

**Q3: When would you use a composite key vs a surrogate key?**

Use **composite key** when:
- Junction/bridge tables (many-to-many)
- The combination has natural business meaning
- You need to enforce uniqueness of the combination

Use **surrogate key** when:
- Natural key might change
- Natural key is too large for efficient joins
- You need a simple, stable reference
- ORM frameworks work better with single-column keys

**Q4: Explain NULL handling in SQL with examples.**

```sql
-- NULL in comparisons
SELECT 1 = NULL;          -- NULL (not FALSE)
SELECT NULL = NULL;       -- NULL (not TRUE)
SELECT NULL IS NULL;      -- TRUE

-- NULL in aggregates
-- COUNT(*) counts all rows, COUNT(column) skips NULLs
SELECT COUNT(*), COUNT(bonus) FROM employees;
-- If 10 rows, 3 have NULL bonus: returns 10, 7

-- NULL in sorting
-- PostgreSQL: NULLs sort LAST by default in ASC
SELECT * FROM employees ORDER BY bonus ASC NULLS FIRST;
```

**Q5: What is referential integrity and how is it enforced?**

Referential integrity ensures every FK value corresponds to an existing PK in the parent table. Enforced by:
1. FK constraint declaration
2. On INSERT/UPDATE: checks parent exists
3. On DELETE of parent: action defined by ON DELETE clause
4. Deferred constraints: checked at COMMIT time

---

## Follow-up Questions & Answers

**Q: Can a foreign key reference a UNIQUE column instead of a PRIMARY KEY?**
Yes. A FK can reference any column with a UNIQUE constraint. The referenced column doesn't have to be the PK.

**Q: What happens to indexes when you drop a table?**
All indexes, constraints, triggers, and rules associated with the table are also dropped.

**Q: Can you have a PRIMARY KEY on a nullable column?**
No. PRIMARY KEY implicitly adds NOT NULL constraint.

**Q: What's the difference between RESTRICT and NO ACTION for foreign keys?**
- RESTRICT: checks immediately, throws error immediately
- NO ACTION: checks at end of statement (allows deferred checking in PostgreSQL)
- In practice, behavior is often the same unless using deferred constraints.

---

## Common Mistakes

1. **Using NULL comparisons with `=`**
   ```sql
   -- WRONG
   WHERE column = NULL
   -- CORRECT
   WHERE column IS NULL
   ```

2. **Not specifying ON DELETE action for foreign keys**
   - Default is NO ACTION/RESTRICT, which may cause unexpected errors

3. **Using VARCHAR without considering storage**
   - In PostgreSQL, VARCHAR(n) and TEXT have same performance
   - Use TEXT for unlimited, VARCHAR(n) only when length limit is business rule

4. **Overusing composite keys with ORMs**
   - JPA/Hibernate work better with single-column surrogate keys
   - Use `@EmbeddedId` or `@IdClass` for composite keys but adds complexity

5. **Not considering NULL in UNIQUE constraints**
   - PostgreSQL allows multiple NULLs in UNIQUE columns
   - Some databases (SQL Server) allow only one NULL

---

## Best Practices

1. **Always define explicit constraints** — don't rely on application layer alone
2. **Use BIGSERIAL for PKs** in high-volume tables (SERIAL maxes at ~2.1 billion)
3. **Name your constraints** for readable error messages:
   ```sql
   CONSTRAINT fk_employee_department 
       FOREIGN KEY (dept_id) REFERENCES departments(id)
   ```
4. **Use CHECK constraints** for business rules that never change
5. **Prefer surrogate keys** for primary keys, add UNIQUE on natural keys
6. **Add NOT NULL** wherever business logic requires a value
7. **Use appropriate data types** — don't store dates as strings, don't use NUMERIC for non-decimal integers

---

## Production Considerations

1. **Schema migrations**: Use Flyway/Liquibase for version-controlled schema changes
2. **Adding NOT NULL to existing columns**: Requires backfilling data first
3. **Adding FK to large tables**: Can lock tables; consider adding without validation first, then validate
4. **UUID vs SERIAL**: UUID avoids sequential guessing but has worse index locality
5. **Constraint validation on large tables**: Use `NOT VALID` then `VALIDATE CONSTRAINT` separately

```sql
-- Non-blocking FK addition on large table
ALTER TABLE orders 
    ADD CONSTRAINT fk_orders_customer 
    FOREIGN KEY (customer_id) REFERENCES customers(id) 
    NOT VALID;  -- Doesn't check existing rows

-- Later, validate (takes shared lock, not exclusive)
ALTER TABLE orders VALIDATE CONSTRAINT fk_orders_customer;
```

---

## Related Topics
- [Topic 14: Constraints (Deep Dive)](#)
- [Topic 15: Keys (Deep Dive)](#)
- [Topic 16: Normalization](#)
- [Topic 17: Database Design](#)
- [Topic 18: Indexes](#)
