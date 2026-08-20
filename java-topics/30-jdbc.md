# 30. JDBC (Java Database Connectivity)

## Theory

JDBC is Java's standard API for connecting to and interacting with relational databases. It provides a uniform interface regardless of the underlying database (MySQL, PostgreSQL, Oracle, etc.).

### JDBC Architecture

JDBC uses a **driver-based architecture**:
- Application code uses JDBC API (java.sql package)
- JDBC Driver Manager loads the appropriate driver
- Driver translates JDBC calls to database-specific protocol
- Database processes the request and returns results

### JDBC Driver Types

| Type | Name | Description |
|------|------|-------------|
| Type 1 | JDBC-ODBC Bridge | Uses ODBC driver (obsolete, removed in Java 8) |
| Type 2 | Native-API | Uses native database client libraries |
| Type 3 | Network Protocol | Middleware translates JDBC to DB protocol |
| Type 4 | Thin Driver | Pure Java, communicates directly with DB (most common) |

### Key Interfaces

- **Connection** — Represents a database session
- **Statement** — Executes static SQL
- **PreparedStatement** — Executes parameterized SQL (prevents SQL injection)
- **CallableStatement** — Executes stored procedures
- **ResultSet** — Holds query results (cursor-based)

---

## Internal Working

### JDBC Execution Flow

```
Application Code
      ↓
DriverManager.getConnection(url, user, password)
      ↓
JDBC Driver (Type 4: Pure Java)
      ↓
TCP/IP connection to database server
      ↓
Database Server
      ↓
Parse SQL → Optimize → Execute → Return Results
      ↓
ResultSet (cursor to results)
      ↓
Application processes rows
      ↓
Close: ResultSet → Statement → Connection
```

### PreparedStatement Lifecycle

```
1. connection.prepareStatement("SELECT * FROM users WHERE id = ?")
      ↓
2. Database parses and compiles SQL plan (once)
      ↓
3. pstmt.setInt(1, userId)  ← bind parameter
      ↓
4. pstmt.executeQuery()
      ↓
5. Database executes pre-compiled plan with bound value
      ↓
6. ResultSet returned
      ↓
7. Re-execute with different parameters (step 3-6)
   (No re-parsing needed!)
```

### Connection Pooling

```
Without pooling:
Request 1 → Create Connection → Execute → Close Connection → [destroyed]
Request 2 → Create Connection → Execute → Close Connection → [destroyed]
(Each connection: ~200-500ms to establish)

With pooling (HikariCP, DBCP):
Startup → Create N connections → Pool [C1, C2, C3, C4, C5]

Request 1 → Borrow C1 → Execute → Return C1 to pool
Request 2 → Borrow C2 → Execute → Return C2 to pool
Request 3 → Borrow C1 → Execute → Return C1 to pool (reused!)
(Borrow from pool: ~1ms)
```

---

## Diagram

### JDBC API Hierarchy

```
java.sql
├── DriverManager          — manages JDBC drivers
├── Connection             — database session
│   ├── createStatement()
│   ├── prepareStatement()
│   ├── prepareCall()
│   ├── setAutoCommit()
│   ├── commit()
│   ├── rollback()
│   └── close()
├── Statement              — execute SQL
│   ├── executeQuery()     → ResultSet (SELECT)
│   ├── executeUpdate()    → int (INSERT/UPDATE/DELETE)
│   └── execute()          → boolean
├── PreparedStatement      — parameterized SQL (extends Statement)
│   ├── setInt/String/...()
│   ├── executeQuery()
│   └── executeUpdate()
├── CallableStatement      — stored procedures (extends PreparedStatement)
├── ResultSet              — query results
│   ├── next()             → move cursor
│   ├── getInt/getString/...()
│   ├── wasNull()
│   └── close()
└── SQLException           — database errors
    ├── getSQLState()
    ├── getErrorCode()
    └── getNextException()
```

### Transaction Flow

```
connection.setAutoCommit(false)
      ↓
try {
    statement1.executeUpdate(...)  → DB executes, holds locks
    statement2.executeUpdate(...)  → DB executes, holds locks
    statement3.executeUpdate(...)  → DB executes, holds locks
    connection.commit()            → All changes made permanent, locks released
} catch (SQLException e) {
    connection.rollback()          → All changes undone, locks released
}
```

---

## Code

### Basic JDBC Connection and Query

```java
import java.sql.*;

public class JdbcBasics {
    
    private static final String URL = "jdbc:mysql://localhost:3306/mydb";
    private static final String USER = "root";
    private static final String PASSWORD = "password";
    
    public static void main(String[] args) {
        // Try-with-resources ensures connection is closed
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            System.out.println("Connected to database!");
            System.out.println("Database: " + conn.getMetaData().getDatabaseProductName());
            System.out.println("Driver: " + conn.getMetaData().getDriverName());
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
        }
    }
}
```

### CRUD Operations with PreparedStatement

```java
import java.sql.*;
import java.util.*;

public class EmployeeCrud {
    
    private final String url;
    private final String user;
    private final String password;
    
    public EmployeeCrud(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
    
    // CREATE
    public long createEmployee(String name, String email, double salary) throws SQLException {
        String sql = "INSERT INTO employees (name, email, salary) VALUES (?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, 
                 Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setDouble(3, salary);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1); // Return generated ID
                    }
                }
            }
            return -1;
        }
    }
    
    // READ (single)
    public Optional<Map<String, Object>> findById(long id) throws SQLException {
        String sql = "SELECT id, name, email, salary, created_at FROM employees WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> employee = new HashMap<>();
                    employee.put("id", rs.getLong("id"));
                    employee.put("name", rs.getString("name"));
                    employee.put("email", rs.getString("email"));
                    employee.put("salary", rs.getDouble("salary"));
                    employee.put("createdAt", rs.getTimestamp("created_at"));
                    return Optional.of(employee);
                }
            }
        }
        return Optional.empty();
    }
    
    // READ (all with pagination)
    public List<Map<String, Object>> findAll(int page, int size) throws SQLException {
        String sql = "SELECT id, name, email, salary FROM employees ORDER BY id LIMIT ? OFFSET ?";
        List<Map<String, Object>> employees = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, size);
            pstmt.setInt(2, page * size);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> emp = new HashMap<>();
                    emp.put("id", rs.getLong("id"));
                    emp.put("name", rs.getString("name"));
                    emp.put("email", rs.getString("email"));
                    emp.put("salary", rs.getDouble("salary"));
                    employees.add(emp);
                }
            }
        }
        return employees;
    }
    
    // UPDATE
    public boolean updateSalary(long id, double newSalary) throws SQLException {
        String sql = "UPDATE employees SET salary = ? WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDouble(1, newSalary);
            pstmt.setLong(2, id);
            
            return pstmt.executeUpdate() > 0;
        }
    }
    
    // DELETE
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM employees WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }
}
```

### SQL Injection Prevention

```java
public class SqlInjectionDemo {
    
    // ❌ VULNERABLE — Never do this!
    public ResultSet findUserUnsafe(Connection conn, String username) throws SQLException {
        // Attacker input: username = "' OR '1'='1"
        // Resulting SQL: SELECT * FROM users WHERE username = '' OR '1'='1'
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(sql); // Returns ALL users!
    }
    
    // ✅ SAFE — Always use PreparedStatement
    public ResultSet findUserSafe(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, username); // Properly escaped, treated as data not SQL
        return pstmt.executeQuery();
        // Input "' OR '1'='1" is treated as a literal string value
    }
}
```

### Transaction Management

```java
import java.sql.*;

public class TransactionDemo {
    
    // Transfer money between accounts (must be atomic)
    public boolean transfer(Connection conn, long fromAccount, long toAccount, 
                           double amount) throws SQLException {
        
        String debitSql = "UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?";
        String creditSql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
        String auditSql = "INSERT INTO transactions (from_acc, to_acc, amount, timestamp) VALUES (?, ?, ?, ?)";
        
        conn.setAutoCommit(false); // Start transaction
        
        try {
            // Debit from source account
            try (PreparedStatement debit = conn.prepareStatement(debitSql)) {
                debit.setDouble(1, amount);
                debit.setLong(2, fromAccount);
                debit.setDouble(3, amount);
                
                int rows = debit.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false; // Insufficient funds
                }
            }
            
            // Credit to destination account
            try (PreparedStatement credit = conn.prepareStatement(creditSql)) {
                credit.setDouble(1, amount);
                credit.setLong(2, toAccount);
                credit.executeUpdate();
            }
            
            // Audit log
            try (PreparedStatement audit = conn.prepareStatement(auditSql)) {
                audit.setLong(1, fromAccount);
                audit.setLong(2, toAccount);
                audit.setDouble(3, amount);
                audit.setTimestamp(4, Timestamp.valueOf(java.time.LocalDateTime.now()));
                audit.executeUpdate();
            }
            
            conn.commit(); // All or nothing
            return true;
            
        } catch (SQLException e) {
            conn.rollback(); // Undo everything on failure
            throw e;
        } finally {
            conn.setAutoCommit(true); // Restore default
        }
    }
}
```

### Batch Operations

```java
import java.sql.*;
import java.util.List;

public class BatchOperationsDemo {
    
    // Insert many records efficiently
    public int[] batchInsert(Connection conn, List<String[]> employees) throws SQLException {
        String sql = "INSERT INTO employees (name, email, salary) VALUES (?, ?, ?)";
        
        conn.setAutoCommit(false);
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            for (String[] emp : employees) {
                pstmt.setString(1, emp[0]); // name
                pstmt.setString(2, emp[1]); // email
                pstmt.setDouble(3, Double.parseDouble(emp[2])); // salary
                pstmt.addBatch(); // Add to batch
                
                // Execute batch every 1000 records (memory management)
                if (employees.indexOf(emp) % 1000 == 0) {
                    pstmt.executeBatch();
                }
            }
            
            int[] results = pstmt.executeBatch(); // Execute remaining
            conn.commit();
            return results;
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }
}
```

### ResultSet Metadata

```java
import java.sql.*;

public class MetadataDemo {
    
    public void printResultSetInfo(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT * FROM " + tableName + " LIMIT 1";
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            
            System.out.println("Table: " + tableName);
            System.out.println("Columns: " + columnCount);
            System.out.println("─".repeat(60));
            
            for (int i = 1; i <= columnCount; i++) {
                System.out.printf("%-20s %-15s nullable=%s%n",
                    meta.getColumnName(i),
                    meta.getColumnTypeName(i),
                    meta.isNullable(i) == ResultSetMetaData.columnNullable ? "YES" : "NO");
            }
        }
    }
    
    // Database metadata
    public void printDatabaseInfo(Connection conn) throws SQLException {
        DatabaseMetaData dbMeta = conn.getMetaData();
        System.out.println("Database: " + dbMeta.getDatabaseProductName());
        System.out.println("Version: " + dbMeta.getDatabaseProductVersion());
        System.out.println("Driver: " + dbMeta.getDriverName());
        System.out.println("URL: " + dbMeta.getURL());
        System.out.println("Max connections: " + dbMeta.getMaxConnections());
        System.out.println("Supports transactions: " + dbMeta.supportsTransactions());
    }
}
```

### Connection Pooling with HikariCP

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.sql.*;

public class ConnectionPoolDemo {
    
    private static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        config.setUsername("root");
        config.setPassword("password");
        
        // Pool configuration
        config.setMaximumPoolSize(10);         // Max connections in pool
        config.setMinimumIdle(5);              // Min idle connections
        config.setConnectionTimeout(30000);    // 30s wait for connection
        config.setIdleTimeout(600000);         // 10min idle before eviction
        config.setMaxLifetime(1800000);        // 30min max connection age
        
        // Performance settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
    
    private static final DataSource dataSource = createDataSource();
    
    // Usage: borrow connection from pool
    public List<String> getEmployeeNames() throws SQLException {
        List<String> names = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection(); // Borrows from pool
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT name FROM employees")) {
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
            }
        } // Connection returned to pool (not actually closed)
        
        return names;
    }
}
```

---

## Dry Run

### PreparedStatement Execution

```
SQL: "SELECT * FROM employees WHERE department = ? AND salary > ?"
Parameters: ("Engineering", 80000.0)

Step 1: conn.prepareStatement(sql)
  → Driver sends SQL to database
  → Database parses: validates syntax, resolves table/columns
  → Database creates execution plan (query plan)
  → Returns prepared statement handle

Step 2: pstmt.setString(1, "Engineering")
  → Binds "Engineering" to first ? parameter
  → Value stored in PreparedStatement object (client-side)

Step 3: pstmt.setDouble(2, 80000.0)
  → Binds 80000.0 to second ? parameter

Step 4: pstmt.executeQuery()
  → Driver sends bound parameters to database
  → Database executes pre-compiled plan with parameters
  → Results streamed back

Step 5: ResultSet processing
  → rs.next() → moves cursor to first row
  → rs.getString("name") → returns column value
  → rs.next() → moves to next row
  → rs.next() → returns false (no more rows)
```

---

## Complexity

| Operation | Network Round Trips | Notes |
|-----------|---------------------|-------|
| getConnection() | 1 (handshake) | With pool: 0 (local borrow) |
| prepareStatement() | 1 | Parsed once, reused many times |
| executeQuery() | 1 | Results streamed |
| Batch of N inserts | 1 | All sent in single round trip |
| N individual inserts | N | One round trip per insert |
| Transaction (3 ops) | 3 + commit | 1 per statement + 1 for commit |

### Performance Comparison

| Approach | 10,000 Inserts Time |
|----------|---------------------|
| Individual statements | ~30-60 seconds |
| PreparedStatement (reused) | ~10-20 seconds |
| Batch insert (1000/batch) | ~1-3 seconds |
| LOAD DATA INFILE (MySQL) | ~0.1-0.5 seconds |

---

## Real Project Usage

### 1. DAO Pattern (Data Access Object)

```java
public class EmployeeDao {
    private final DataSource dataSource;
    
    public EmployeeDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    public Employee findById(long id) {
        String sql = "SELECT * FROM employees WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find employee: " + id, e);
        }
        return null;
    }
    
    private Employee mapRow(ResultSet rs) throws SQLException {
        return new Employee(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getDouble("salary"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
```

### 2. Repository with Pagination and Filtering

```java
public class EmployeeRepository {
    private final DataSource dataSource;
    
    public Page<Employee> search(String nameFilter, Double minSalary, 
                                  int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM employees WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        if (nameFilter != null) {
            sql.append(" AND name LIKE ?");
            params.add("%" + nameFilter + "%");
        }
        if (minSalary != null) {
            sql.append(" AND salary >= ?");
            params.add(minSalary);
        }
        
        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            
            List<Employee> employees = new ArrayList<>();
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    employees.add(mapRow(rs));
                }
            }
            
            long total = countTotal(conn, nameFilter, minSalary);
            return new Page<>(employees, page, size, total);
        } catch (SQLException e) {
            throw new DataAccessException("Search failed", e);
        }
    }
}
```

---

## Interview Questions and Answers

### Q1: What is JDBC?
**A**: JDBC (Java Database Connectivity) is a standard Java API for interacting with relational databases. It provides interfaces (Connection, Statement, PreparedStatement, ResultSet) that database vendors implement via JDBC drivers. This abstraction allows the same code to work with different databases by changing only the driver and connection URL.

### Q2: Statement vs PreparedStatement vs CallableStatement?
**A**:
- **Statement**: For static SQL without parameters. SQL is parsed every time. Vulnerable to SQL injection.
- **PreparedStatement**: For parameterized SQL. Pre-compiled (parsed once), faster for repeated execution. Prevents SQL injection by treating parameters as data.
- **CallableStatement**: For executing stored procedures. Supports IN, OUT, and INOUT parameters.

Always use PreparedStatement for any SQL with user-provided values.

### Q3: How does PreparedStatement prevent SQL injection?
**A**: PreparedStatement separates SQL structure from data. The SQL template with `?` placeholders is sent to the database and parsed once. Parameter values are sent separately and treated strictly as data, never as SQL code. Even if a parameter contains SQL keywords or special characters (`' OR 1=1 --`), it's treated as a literal string value.

### Q4: What is connection pooling and why is it needed?
**A**: Creating a database connection is expensive (~200-500ms: TCP handshake, authentication, resource allocation). Connection pooling maintains a pool of pre-created connections that are reused. Applications "borrow" and "return" connections instead of creating/destroying them. This reduces latency to ~1ms per request. HikariCP is the standard pool in Spring Boot.

### Q5: Explain JDBC transaction management.
**A**: By default, JDBC auto-commits each statement. For transactions:
1. `conn.setAutoCommit(false)` — start transaction
2. Execute multiple statements
3. `conn.commit()` — make all changes permanent
4. On failure: `conn.rollback()` — undo all changes

This ensures atomicity (all-or-nothing). Always use try-catch-finally or try-with-resources.

### Q6: What is ResultSet? How do you iterate it?
**A**: ResultSet is a cursor pointing to query results. Initially positioned before the first row. `rs.next()` moves to the next row and returns false when no more rows exist. Values are retrieved by column name (`rs.getString("name")`) or index (`rs.getString(1)`). By default, ResultSet is forward-only and read-only.

---

## Follow-up Questions and Answers

### Q: What are the different ResultSet types?
**A**:
- `TYPE_FORWARD_ONLY` — Default. Can only move forward.
- `TYPE_SCROLL_INSENSITIVE` — Can scroll (first, last, absolute, relative). Doesn't reflect DB changes.
- `TYPE_SCROLL_SENSITIVE` — Can scroll. Reflects changes made by others.

And concurrency modes: `CONCUR_READ_ONLY` (default) vs `CONCUR_UPDATABLE` (can update rows through ResultSet).

### Q: What is a DataSource vs DriverManager?
**A**: `DriverManager` is the basic way to get connections (directly creates them). `DataSource` is the preferred approach — it can be backed by a connection pool, supports JNDI lookup in application servers, and is more configurable. In production, always use DataSource (via HikariCP, Tomcat JDBC, etc.).

### Q: How does JDBC relate to Spring JDBC and JPA?
**A**: Raw JDBC → lots of boilerplate (open/close resources, exception handling, row mapping). Spring JdbcTemplate wraps JDBC, eliminating boilerplate. Spring Data JPA wraps Hibernate which wraps JDBC — adds ORM (object-relational mapping), entity management, and automatic query generation. Under the hood, it's all JDBC.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| String concatenation for SQL | SQL injection vulnerability | Use PreparedStatement with ? |
| Not closing connections | Connection leak, pool exhaustion | Use try-with-resources |
| Creating connection per query | Extremely slow | Use connection pool |
| Not setting autoCommit(false) for transactions | Partial updates on failure | Explicit transaction management |
| Using Statement instead of PreparedStatement | SQL injection + slower | Always PreparedStatement |
| Ignoring SQLState codes | Poor error handling | Check SQLState for specific errors |
| Fetching all rows for large results | OutOfMemoryError | Use pagination (LIMIT/OFFSET) or setFetchSize() |

---

## Best Practices

1. **Always use PreparedStatement** — Security and performance
2. **Always use connection pooling** — HikariCP in production
3. **Always use try-with-resources** — Close ResultSet, Statement, Connection in order
4. **Use transactions for multi-statement operations** — Ensure data consistency
5. **Use batch operations for bulk inserts** — 10-100x faster
6. **Set appropriate fetch size** — `stmt.setFetchSize(100)` for large result sets
7. **Log SQL with parameters** — For debugging (but never log in production at DEBUG level)
8. **Handle SQLExceptions properly** — Check SQLState, wrap in domain exceptions
9. **Use connection timeouts** — Prevent hanging connections

---

## Production Considerations

- **Connection pool sizing**: Formula: pool size = (core_count * 2) + spindle_count. For SSDs, typically 10-20 connections is optimal. More connections doesn't mean more throughput — it can actually decrease due to context switching.
- **Connection validation**: Configure pool to test connections before use (`SELECT 1`) to handle stale connections after network issues.
- **Statement timeout**: Set `stmt.setQueryTimeout(seconds)` to prevent long-running queries from blocking resources.
- **Connection leak detection**: HikariCP has leak detection — enable it in development to find unreturned connections.
- **Read replicas**: Route read queries to replicas, writes to primary. Some pools support this natively.
- **Monitoring**: Track pool metrics (active/idle/waiting connections), query execution times, and slow queries.

---

## Related Topics

- [27. I/O](./27-io.md)
- [05. Exception Handling](./05-exception-handling.md)
- [31. Design Patterns](./31-design-patterns.md) (DAO Pattern)
