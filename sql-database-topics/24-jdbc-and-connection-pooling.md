# Topic 24: JDBC Architecture & Connection Pooling

## Theory

### What is JDBC?

JDBC (Java Database Connectivity) is Java's standard API for database-independent connectivity between Java applications and relational databases. It provides a common abstraction over database-specific protocols.

### JDBC Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    JDBC ARCHITECTURE                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────┐        │
│  │              Java Application                        │        │
│  │  (Spring Boot / Hibernate / Raw JDBC)               │        │
│  └────────────────────────┬────────────────────────────┘        │
│                           │                                      │
│  ┌────────────────────────▼────────────────────────────┐        │
│  │              JDBC API (java.sql.*)                   │        │
│  │  Connection, Statement, PreparedStatement,           │        │
│  │  CallableStatement, ResultSet                        │        │
│  └────────────────────────┬────────────────────────────┘        │
│                           │                                      │
│  ┌────────────────────────▼────────────────────────────┐        │
│  │           JDBC Driver Manager                        │        │
│  │     (Routes to appropriate driver)                   │        │
│  └────┬───────────────┬───────────────┬────────────────┘        │
│       │               │               │                          │
│  ┌────▼────┐    ┌─────▼─────┐   ┌────▼──────┐                  │
│  │PostgreSQL│    │   MySQL   │   │  Oracle   │                  │
│  │ Driver   │    │  Driver   │   │  Driver   │                  │
│  └────┬────┘    └─────┬─────┘   └────┬──────┘                  │
│       │               │               │                          │
│  ┌────▼────┐    ┌─────▼─────┐   ┌────▼──────┐                  │
│  │PostgreSQL│    │   MySQL   │   │  Oracle   │                  │
│  │   DB     │    │    DB     │   │   DB      │                  │
│  └─────────┘    └───────────┘   └───────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

### JDBC Driver Types

| Type | Name | Description |
|---|---|---|
| Type 1 | JDBC-ODBC Bridge | Translates JDBC to ODBC (deprecated) |
| Type 2 | Native API | Partially Java, uses native DB library |
| Type 3 | Network Protocol | Pure Java, middleware server |
| Type 4 | Thin Driver | Pure Java, direct DB protocol (STANDARD) |

Modern applications use Type 4 drivers exclusively (e.g., `org.postgresql.Driver`).

---

## Internal Working — Connection Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│              CONNECTION LIFECYCLE (Without Pool)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. OPEN CONNECTION                                              │
│     → TCP handshake (3-way)                                      │
│     → TLS negotiation (if SSL)                                   │
│     → Authentication (username/password)                         │
│     → Session initialization                                     │
│     → Time: 20-100ms per connection                              │
│                                                                  │
│  2. EXECUTE STATEMENTS                                           │
│     → Parse SQL                                                  │
│     → Execute                                                    │
│     → Fetch results                                              │
│                                                                  │
│  3. CLOSE CONNECTION                                             │
│     → TCP close (4-way handshake)                                │
│     → Server releases resources                                  │
│                                                                  │
│  PROBLEM: Opening/closing connection for every request            │
│           is extremely expensive in production!                   │
└─────────────────────────────────────────────────────────────────┘
```

### Connection Pooling — How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│              CONNECTION POOL (HikariCP)                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Application Threads          Connection Pool          Database  │
│                                                                  │
│  Thread-1 ─── getConnection() ──→ ┌─────────┐                   │
│                                    │ Conn-1  │ ←──── Active      │
│  Thread-2 ─── getConnection() ──→ │ Conn-2  │ ←──── Active      │
│                                    │ Conn-3  │ ←──── Idle        │
│  Thread-3 ─── waiting...           │ Conn-4  │ ←──── Idle        │
│                                    │ Conn-5  │ ←──── Idle        │
│  Thread-1 ─── close() ──────────→ │ Conn-1  │ ←──── Returned!   │
│                                    └─────────┘                   │
│  Thread-3 ─── getConnection() ──→  Gets Conn-1                   │
│                                                                  │
│  KEY CONCEPTS:                                                   │
│  • close() does NOT close the connection — returns it to pool    │
│  • Pool maintains min/max idle connections                       │
│  • Connections are reused across requests                        │
│  • Health checks validate connections before lending              │
│  • Idle timeout evicts unused connections                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## JDBC Core Classes

### Statement Types

```java
// 1. Statement — simple SQL (DON'T USE — SQL injection risk)
Statement stmt = connection.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = " + id); // DANGEROUS!

// 2. PreparedStatement — parameterized (ALWAYS USE)
PreparedStatement pstmt = connection.prepareStatement(
    "SELECT * FROM users WHERE id = ? AND status = ?"
);
pstmt.setLong(1, id);
pstmt.setString(2, "ACTIVE");
ResultSet rs = pstmt.executeQuery();

// 3. CallableStatement — stored procedures
CallableStatement cstmt = connection.prepareCall("{call get_user_orders(?, ?)}");
cstmt.setLong(1, userId);
cstmt.registerOutParameter(2, Types.INTEGER);
cstmt.execute();
int orderCount = cstmt.getInt(2);
```

### ResultSet Processing

```java
PreparedStatement pstmt = connection.prepareStatement(
    "SELECT id, name, email, created_at FROM users WHERE status = ?"
);
pstmt.setString(1, "ACTIVE");

try (ResultSet rs = pstmt.executeQuery()) {
    List<User> users = new ArrayList<>();
    while (rs.next()) {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        users.add(user);
    }
    return users;
}
```

### Batch Operations

```java
@Transactional
public void batchInsertUsers(List<User> users) {
    String sql = "INSERT INTO users (name, email, status) VALUES (?, ?, ?)";
    
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        int batchSize = 1000;
        int count = 0;
        
        for (User user : users) {
            pstmt.setString(1, user.getName());
            pstmt.setString(2, user.getEmail());
            pstmt.setString(3, user.getStatus());
            pstmt.addBatch();
            
            if (++count % batchSize == 0) {
                pstmt.executeBatch(); // Execute every 1000 rows
                pstmt.clearBatch();
            }
        }
        pstmt.executeBatch(); // Execute remaining
    }
}
```

---

## Spring's JdbcTemplate

### How It Simplifies JDBC

```java
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Query for single object
    public User findById(Long id) {
        return jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE id = ?",
            new UserRowMapper(),
            id
        );
    }

    // Query for list
    public List<User> findByStatus(String status) {
        return jdbcTemplate.query(
            "SELECT * FROM users WHERE status = ?",
            new UserRowMapper(),
            status
        );
    }

    // Insert/Update
    public int save(User user) {
        return jdbcTemplate.update(
            "INSERT INTO users (name, email, status) VALUES (?, ?, ?)",
            user.getName(), user.getEmail(), user.getStatus()
        );
    }

    // Batch update
    public int[] batchInsert(List<User> users) {
        return jdbcTemplate.batchUpdate(
            "INSERT INTO users (name, email, status) VALUES (?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    User user = users.get(i);
                    ps.setString(1, user.getName());
                    ps.setString(2, user.getEmail());
                    ps.setString(3, user.getStatus());
                }
                @Override
                public int getBatchSize() { return users.size(); }
            }
        );
    }
}

// RowMapper
public class UserRowMapper implements RowMapper<User> {
    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return User.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .email(rs.getString("email"))
            .status(rs.getString("status"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build();
    }
}
```

### NamedParameterJdbcTemplate

```java
@Repository
public class OrderRepository {

    private final NamedParameterJdbcTemplate namedTemplate;

    public List<Order> findByFilters(OrderFilter filter) {
        String sql = "SELECT * FROM orders WHERE status = :status " +
                     "AND created_at >= :startDate AND customer_id = :customerId";

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("status", filter.getStatus())
            .addValue("startDate", filter.getStartDate())
            .addValue("customerId", filter.getCustomerId());

        return namedTemplate.query(sql, params, new OrderRowMapper());
    }

    // IN clause
    public List<Order> findByIds(List<Long> ids) {
        String sql = "SELECT * FROM orders WHERE id IN (:ids)";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        return namedTemplate.query(sql, params, new OrderRowMapper());
    }
}
```

---

## HikariCP — The Standard Connection Pool

### Why HikariCP?

- Default connection pool in Spring Boot since 2.0
- Fastest connection pool for Java (benchmarked)
- Zero-overhead, minimal bytecode
- Reliable connection validation
- Excellent default configuration

### Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: app_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    
    hikari:
      # Pool sizing
      maximum-pool-size: 10          # Max connections in pool
      minimum-idle: 5                 # Min idle connections maintained
      
      # Timeouts
      connection-timeout: 30000       # Max wait for connection (ms)
      idle-timeout: 600000            # Max idle time before eviction (ms)
      max-lifetime: 1800000           # Max connection lifetime (ms)
      
      # Validation
      validation-timeout: 5000        # Max time for connection validation
      
      # Performance
      pool-name: MyAppPool
      auto-commit: false              # Let Spring manage commits
      
      # Leak detection
      leak-detection-threshold: 60000 # Log warning if connection not returned in 60s
```

### Pool Sizing Formula

```
Optimal pool size = (core_count * 2) + effective_spindle_count

For SSD-based systems:
  pool_size ≈ core_count * 2

Example:
  4-core server with SSD → pool_size = 8-10

IMPORTANT:
  A pool of 10 connections can handle thousands of requests/sec
  because each request holds a connection for only a few milliseconds.

  More connections ≠ better performance
  Too many connections → context switching overhead + DB resource waste
```

### HikariCP Internal Working

```
┌─────────────────────────────────────────────────────────────────┐
│                 HikariCP INTERNALS                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ConcurrentBag (custom lock-free collection)                     │
│  ┌──────────────────────────────────────────────────────┐       │
│  │                                                       │       │
│  │  ThreadLocal Cache ─── try thread-local first (fast)  │       │
│  │         │                                             │       │
│  │         ▼ (miss)                                      │       │
│  │  Shared List ─────── CAS-based acquisition            │       │
│  │         │                                             │       │
│  │         ▼ (empty)                                     │       │
│  │  Handoff Queue ──── wait with timeout                 │       │
│  │                                                       │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                  │
│  Connection States: NOT_IN_USE → IN_USE → NOT_IN_USE            │
│                                                                  │
│  ProxyConnection:                                                │
│  • Wraps real JDBC Connection                                    │
│  • close() returns to pool (not real close)                      │
│  • Tracks last access time (for idle eviction)                   │
│  • Validates on borrow (configurable)                            │
│                                                                  │
│  Housekeeper Thread (every 30s):                                 │
│  • Evicts idle connections beyond minimumIdle                    │
│  • Retires connections beyond maxLifetime                        │
│  • Creates new connections if below minimumIdle                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Dry Run — Connection Pool Operation

```
Application startup:
  → HikariCP creates minimumIdle (5) connections
  → Each: TCP connect → auth → session init
  → Pool ready: [Conn1, Conn2, Conn3, Conn4, Conn5] all IDLE

Request 1 arrives (Thread-A):
  → DataSource.getConnection()
  → HikariCP: Check ThreadLocal → empty
  → Check ConcurrentBag → find Conn1 (NOT_IN_USE)
  → CAS: Conn1 state → IN_USE
  → Return ProxyConnection wrapping Conn1
  → Thread-A executes queries...
  → Thread-A calls connection.close()
  → ProxyConnection.close() → Conn1 state back to NOT_IN_USE
  → Conn1 returned to bag (available for next request)

Pool exhaustion scenario:
  → 10 threads each hold a connection (pool maxSize = 10)
  → Thread-11 calls getConnection()
  → No connections available
  → Thread-11 waits on handoff queue
  → connectionTimeout (30s) expires
  → Throws: SQLTransientConnectionException
  → "Connection is not available, request timed out after 30000ms"

Leak detection:
  → Thread-X borrows Conn3 at T=0
  → leakDetectionThreshold = 60000ms
  → At T=60s, Conn3 still not returned
  → HikariCP logs WARNING with stack trace of borrower
  → Helps identify code that forgets to close connections
```

---

## Complexity

| Operation | Time | Notes |
|---|---|---|
| Get connection (from pool, cache hit) | O(1) ~microseconds | ThreadLocal fast path |
| Get connection (from pool, no cache) | O(1) ~microseconds | CAS on ConcurrentBag |
| Get connection (pool exhausted) | O(1) but BLOCKS | Waits up to connectionTimeout |
| Create new connection (TCP) | 20-100ms | Expensive! Done at startup/expansion |
| Close connection (return to pool) | O(1) ~microseconds | Just state change |
| Execute prepared statement | Depends on query | Network + DB processing |
| Batch insert (1000 rows) | ~50-200ms | Single network round-trip |

---

## Real Project Usage

### Multi-DataSource Configuration

```java
@Configuration
public class DataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.readonly")
    public DataSource readOnlyDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }
}
```

### Read/Write Splitting with AbstractRoutingDataSource

```java
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? DataSourceType.READ_REPLICA
            : DataSourceType.PRIMARY;
    }
}

@Configuration
public class RoutingDataSourceConfig {

    @Bean
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("readOnlyDataSource") DataSource readOnly) {
        
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceType.PRIMARY, primary);
        targets.put(DataSourceType.READ_REPLICA, readOnly);
        
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(primary);
        return routing;
    }
}
```

### SQL Injection Prevention

```java
// VULNERABLE — String concatenation
public User findUser(String username) {
    String sql = "SELECT * FROM users WHERE username = '" + username + "'";
    // If username = "'; DROP TABLE users; --"
    // SQL becomes: SELECT * FROM users WHERE username = ''; DROP TABLE users; --'
    return jdbcTemplate.queryForObject(sql, new UserRowMapper());
}

// SAFE — Parameterized query
public User findUser(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT * FROM users WHERE username = ?",
        new UserRowMapper(),
        username  // Properly escaped by driver
    );
}

// SAFE — NamedParameter
public User findUser(String username) {
    String sql = "SELECT * FROM users WHERE username = :username";
    MapSqlParameterSource params = new MapSqlParameterSource("username", username);
    return namedTemplate.queryForObject(sql, params, new UserRowMapper());
}
```

---

## Interview Questions and Answers

### Q1: What is the difference between Statement and PreparedStatement?

**Answer:**

| Aspect | Statement | PreparedStatement |
|---|---|---|
| SQL Injection | Vulnerable | Safe (parameterized) |
| Performance | Parsed every execution | Parsed once, cached |
| Use case | Dynamic DDL only | All DML/queries |
| Parameters | String concatenation | Placeholder binding (?) |
| Plan caching | No | Yes (DB can cache execution plan) |

Always use PreparedStatement for any query with parameters.

### Q2: How does HikariCP differ from other connection pools (C3P0, DBCP2)?

**Answer:**
- **Speed**: Uses lock-free ConcurrentBag, no synchronized blocks
- **Bytecode**: Uses Javassist for lightweight proxies (~130KB footprint)
- **Reliability**: Better leak detection, connection validation
- **Defaults**: Sensible out-of-box configuration
- **Maintenance**: Actively maintained, Spring Boot's default since 2.0
- **Benchmarks**: Consistently faster in all benchmarks

### Q3: How do you determine the optimal connection pool size?

**Answer:**

Formula: `connections = (core_count * 2) + effective_spindle_count`

For SSD systems: `core_count * 2` (typically 8-12 for most apps)

Key insight: More connections hurt performance due to:
- CPU context switching
- Lock contention on DB
- Memory overhead per connection

A pool of 10 connections can serve 10,000 concurrent users because requests hold connections for milliseconds.

### Q4: What is connection leak and how do you detect it?

**Answer:** A connection leak occurs when application code borrows a connection but never returns it (forgets to close). Over time, the pool runs out.

Detection with HikariCP:
```yaml
hikari:
  leak-detection-threshold: 60000 # 60 seconds
```

This logs the stack trace of the code that borrowed the connection if it's held longer than the threshold.

Prevention:
- Always use try-with-resources
- Use Spring's JdbcTemplate (handles connection lifecycle)
- Use `@Transactional` (Spring manages connection)

### Q5: Explain batch processing in JDBC.

**Answer:**
```java
// Instead of 1000 individual INSERT statements (1000 network round-trips):
for (User u : users) {
    pstmt.execute("INSERT INTO users VALUES (...)"); // BAD: 1000 round-trips
}

// Use batch (1 network round-trip):
for (User u : users) {
    pstmt.setString(1, u.getName());
    pstmt.addBatch(); // Buffers locally
}
pstmt.executeBatch(); // Single round-trip to DB
```

Benefits:
- Dramatic reduction in network round-trips
- Database can optimize batch execution
- PostgreSQL: Use `reWriteBatchedInserts=true` for multi-value INSERT

---

## Follow-up Questions and Answers

### Q: What happens when maxLifetime is reached?

**Answer:** HikariCP's housekeeper thread retires the connection gracefully:
1. Marks connection for eviction
2. Waits until it's returned to pool (if currently in use)
3. Closes the real JDBC connection
4. Creates a new connection to maintain minimumIdle

This prevents stale connections from accumulating (DB-side timeouts, network issues, etc.).

### Q: How does `autoCommit = false` in HikariCP work with Spring?

**Answer:**
- HikariCP sets `autoCommit = false` on the raw connection
- Spring's `DataSourceTransactionManager` then manages commit/rollback
- For non-transactional code, Spring resets autoCommit to true temporarily
- This avoids unnecessary autoCommit toggles on every connection borrow

### Q: How do you handle connection pool exhaustion in production?

**Answer:**
1. **Monitor**: Track active/idle/waiting connections via metrics
2. **Alert**: Set alerting on `hikaricp_connections_pending` > 0
3. **Timeout**: Set `connection-timeout` to fail fast vs. hang forever
4. **Find root cause**: Leak detection, long-running queries, or undersized pool
5. **Short-term**: Increase pool size (if not at DB limit)
6. **Long-term**: Optimize queries, add read replicas, implement caching

---

## Common Mistakes

| Mistake | Problem | Fix |
|---|---|---|
| Not closing connections | Pool exhaustion | Use try-with-resources or Spring management |
| Using Statement instead of PreparedStatement | SQL injection + poor performance | Always use PreparedStatement |
| Pool too large | DB resource waste, contention | Use formula: cores * 2 |
| Pool too small | Request queuing, timeouts | Monitor and adjust |
| No connection timeout | Threads hang forever | Set connectionTimeout |
| No leak detection | Silent pool exhaustion | Enable leakDetectionThreshold |
| Missing `reWriteBatchedInserts` (PostgreSQL) | Slow batch inserts | Add to connection URL |
| Individual inserts instead of batch | Network round-trip per row | Use batch operations |

---

## Best Practices

1. **Always use PreparedStatement** — for security and performance
2. **Let Spring manage connections** — via JdbcTemplate or @Transactional
3. **Use try-with-resources** when using raw JDBC
4. **Set pool size = cores × 2** — don't over-provision
5. **Enable leak detection** in development and staging
6. **Set connection-timeout** to fail fast (30s default is fine)
7. **Use batch operations** for bulk inserts/updates
8. **Add `reWriteBatchedInserts=true`** to PostgreSQL JDBC URL
9. **Monitor pool metrics** via Spring Boot Actuator + Micrometer
10. **Set max-lifetime < DB server timeout** to avoid stale connections

---

## Production Considerations

### PostgreSQL JDBC URL Best Practices

```
jdbc:postgresql://host:5432/dbname?
  reWriteBatchedInserts=true&
  prepareThreshold=5&
  preparedStatementCacheQueries=256&
  preparedStatementCacheSizeMiB=5&
  socketTimeout=30&
  connectTimeout=10
```

### Monitoring with Spring Boot Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  metrics:
    tags:
      application: my-app
```

Key metrics to watch:
- `hikaricp.connections.active` — currently in use
- `hikaricp.connections.idle` — available in pool
- `hikaricp.connections.pending` — threads waiting (should be 0!)
- `hikaricp.connections.timeout` — connection acquisition failures
- `hikaricp.connections.usage` — how long connections are held

### Connection Pool + Kubernetes

```yaml
# When running in K8s with multiple pods:
# Total DB connections = pool_size_per_pod × number_of_pods
# 
# If pool = 10 and pods = 20 → 200 connections to DB
# PostgreSQL default max_connections = 100 → PROBLEM
#
# Solutions:
# 1. Use PgBouncer (connection pooler between app and DB)
# 2. Reduce per-pod pool size
# 3. Increase DB max_connections
```

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation Levels
- Topic 19: JPA, Hibernate & Spring Data JPA
- Topic 23: Spring Transactions (@Transactional)
- Topic 25: Advanced Database Performance
- Topic 30: Database Security (SQL injection prevention)
