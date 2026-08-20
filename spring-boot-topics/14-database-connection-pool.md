# 14. Database Connection Pool (HikariCP)

## Theory

A connection pool is a cache of database connections maintained so that connections can be reused when future requests to the database are required. Creating a new database connection is expensive (TCP handshake, authentication, resource allocation), so pooling dramatically improves performance.

**HikariCP** is the default connection pool in Spring Boot. It's known for being the fastest and most lightweight connection pool available for Java.

### Key Concepts:
- **Connection Pool**: A cache of pre-established database connections
- **Maximum Pool Size**: Maximum number of connections the pool can hold (active + idle)
- **Minimum Idle**: Minimum number of idle connections maintained in the pool
- **Connection Timeout**: Maximum time a client waits for a connection from the pool
- **Idle Timeout**: Maximum time a connection can sit idle before being removed
- **Max Lifetime**: Maximum lifetime of a connection in the pool (prevents stale connections)
- **Leak Detection**: Threshold time to detect connection leaks (connections not returned)

### Why Connection Pooling?

Without pooling:
```
Request → Create Connection → Execute SQL → Close Connection → Response
(Expensive: ~200-500ms per connection creation)
```

With pooling:
```
Request → Borrow Connection from Pool → Execute SQL → Return Connection → Response
(Fast: ~1-2ms to borrow)
```

---

## Internal Working

```
Application Start
       ↓
HikariCP Initializes Pool
       ↓
Creates minimumIdle connections
       ↓
Ready to serve requests

Request arrives:
       ↓
Thread requests connection from HikariPool
       ↓
┌─────────────────────────────────────┐
│  Is idle connection available?      │
│  ├── YES → Return connection        │
│  └── NO                             │
│       ├── Pool < maxPoolSize?       │
│       │   ├── YES → Create new conn │
│       │   └── NO → Wait (timeout)   │
│       └── Timeout exceeded?         │
│           └── YES → Throw Exception │
└─────────────────────────────────────┘
       ↓
Connection used by application
       ↓
Connection returned to pool (not closed)
       ↓
Connection marked as idle
```

### HikariCP Internal Architecture:
1. **ConcurrentBag**: Custom lock-free collection for connection storage
2. **HouseKeeper**: Scheduled task that maintains pool size and removes idle connections
3. **Connection Proxy**: Wraps real connections to intercept close() calls
4. **PoolEntry**: Wraps connection with metadata (creation time, last access, state)

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│                    APPLICATION                        │
│                                                      │
│  Thread-1  Thread-2  Thread-3  Thread-4  Thread-5   │
│     │         │         │         │         │        │
└─────┼─────────┼─────────┼─────────┼─────────┼───────┘
      │         │         │         │         │
      ▼         ▼         ▼         ▼         ▼
┌─────────────────────────────────────────────────────┐
│              HikariCP Connection Pool                 │
│                                                      │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │Conn-1│ │Conn-2│ │Conn-3│ │Conn-4│ │Conn-5│     │
│  │ACTIVE│ │ACTIVE│ │ IDLE │ │ IDLE │ │ACTIVE│     │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │
│                                                      │
│  maximumPoolSize: 10                                 │
│  minimumIdle: 5                                      │
│  connectionTimeout: 30000ms                          │
│  idleTimeout: 600000ms                               │
│  maxLifetime: 1800000ms                              │
└─────────────────────────────────────────────────────┘
      │         │         │         │         │
      ▼         ▼         ▼         ▼         ▼
┌─────────────────────────────────────────────────────┐
│                   DATABASE SERVER                     │
│         (PostgreSQL / MySQL / Oracle)                 │
└─────────────────────────────────────────────────────┘
```

---

## Code

### Basic HikariCP Configuration (application.yml):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: admin
    password: secret
    driver-class-name: org.postgresql.Driver
    
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000      # 30 seconds
      idle-timeout: 600000           # 10 minutes
      max-lifetime: 1800000          # 30 minutes
      leak-detection-threshold: 60000 # 1 minute
      pool-name: MyAppPool
      auto-commit: true
      connection-test-query: SELECT 1
```

### Programmatic Configuration:

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setPassword("secret");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("MyAppPool");
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
}
```

### Multiple DataSource Configuration:

```java
@Configuration
public class MultiDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary.hikari")
    public HikariDataSource primaryDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.secondary.hikari")
    public HikariDataSource secondaryDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
```

### Monitoring Pool Metrics:

```java
@Component
public class ConnectionPoolMonitor {

    private final HikariDataSource dataSource;

    public ConnectionPoolMonitor(DataSource dataSource) {
        this.dataSource = (HikariDataSource) dataSource;
    }

    @Scheduled(fixedRate = 30000)
    public void logPoolStats() {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        log.info("Pool Stats - Active: {}, Idle: {}, Waiting: {}, Total: {}",
                poolMXBean.getActiveConnections(),
                poolMXBean.getIdleConnections(),
                poolMXBean.getThreadsAwaitingConnection(),
                poolMXBean.getTotalConnections());
    }
}
```

---

## Dry Run

### Scenario: 3 concurrent requests with pool size = 2

```
Initial State: Pool [Conn-1: IDLE, Conn-2: IDLE]

T1: Request-A arrives
    → Pool: [Conn-1: ACTIVE(A), Conn-2: IDLE]

T2: Request-B arrives
    → Pool: [Conn-1: ACTIVE(A), Conn-2: ACTIVE(B)]

T3: Request-C arrives
    → Pool full, no idle connections
    → Request-C WAITS (connectionTimeout starts counting)

T4: Request-A completes, returns Conn-1
    → Pool: [Conn-1: IDLE, Conn-2: ACTIVE(B)]
    → Request-C gets Conn-1
    → Pool: [Conn-1: ACTIVE(C), Conn-2: ACTIVE(B)]

T5: If connectionTimeout exceeded before connection available:
    → SQLTransientConnectionException thrown
```

### Leak Detection Scenario:

```
T1: Thread borrows connection
T2: Thread processes (long running or forgot to close)
T3: leakDetectionThreshold exceeded (e.g., 60s)
T4: HikariCP logs WARNING with stack trace of where connection was borrowed
    → "Connection leak detection triggered for connection-1,
       stack trace follows..."
```

---

## Complexity

| Operation | Time Complexity |
|-----------|----------------|
| Borrow connection (idle available) | O(1) - ConcurrentBag thread-local lookup |
| Borrow connection (none available, create new) | O(1) + network latency |
| Return connection | O(1) |
| Housekeeping (eviction check) | O(n) where n = pool size |

**Space Complexity**: O(maxPoolSize) for connection objects + metadata

---

## Real Project Usage

### E-commerce Application Pool Sizing:

```yaml
# For a service handling ~500 concurrent users
# Formula: connections = ((core_count * 2) + effective_spindle_count)
# For 4-core server with SSD: (4 * 2) + 1 = 9 (round to 10)
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 10          # Keep pool fixed size for predictability
      connection-timeout: 3000  # Fail fast (3 seconds)
      max-lifetime: 1800000
      leak-detection-threshold: 30000
```

### Microservice with Read Replicas:

```java
@Configuration
public class ReadWriteDataSourceConfig {

    @Bean("writeDataSource")
    @Primary
    public HikariDataSource writeDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://primary:5432/mydb");
        config.setMaximumPoolSize(10);
        config.setReadOnly(false);
        return new HikariDataSource(config);
    }

    @Bean("readDataSource")
    public HikariDataSource readDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://replica:5432/mydb");
        config.setMaximumPoolSize(20);  // More reads expected
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }
}
```

---

## Interview Questions

1. **What is HikariCP and why is it the default in Spring Boot?**
   - Fastest connection pool, minimal overhead, ~130KB jar, zero-overhead proxy

2. **How do you determine the optimal pool size?**
   - Formula: connections = ((core_count * 2) + effective_spindle_count)
   - For most applications: 10-20 connections is sufficient
   - More connections ≠ better performance (context switching overhead)

3. **What happens when pool is exhausted?**
   - Thread waits up to connectionTimeout milliseconds
   - If no connection available within timeout → SQLTransientConnectionException

4. **What is connection leak detection?**
   - HikariCP tracks borrowed connections
   - If not returned within leakDetectionThreshold, logs a warning with stack trace
   - Doesn't close the connection, just alerts

5. **Difference between maxLifetime and idleTimeout?**
   - maxLifetime: Maximum time ANY connection (active or idle) lives before being recycled
   - idleTimeout: Maximum time an IDLE connection stays in pool before being removed

---

## Follow-up Questions

1. How does HikariCP handle database failover?
   - When a connection fails validation, it's evicted. maxLifetime ensures connections are recycled. For active failover, configure multiple hosts in JDBC URL or use a connection-aware DNS (like RDS proxy).

2. What's the relationship between pool size and transaction isolation level?
   - Higher isolation (SERIALIZABLE) holds locks longer → connections are held longer → pool exhausts faster. May need larger pool or shorter transactions with high isolation levels.

3. How would you configure connection pooling for a multi-tenant application?
   - Options: Separate pool per tenant (isolation but more resources), shared pool with tenant routing (efficient but less isolation), or use a routing DataSource (AbstractRoutingDataSource) with a single pool.

4. How does connection validation work (connection-test-query vs isValid)?
   - connection-test-query: Executes SQL (SELECT 1) to verify connection. isValid() (JDBC4+): Uses driver-native ping, no SQL overhead, faster. Modern drivers should use isValid() (HikariCP default).

5. What metrics should you monitor in production for connection pool health?
   - Active connections, idle connections, pending threads (waiting for connection), connection acquire time, timeout exceptions, total connections vs max. Alert on: threads waiting > 0 sustained, acquire time > 100ms.

---

## Common Mistakes

1. **Setting pool size too large** - More than 20-30 causes context switching overhead; database also has max_connections limit
2. **Not setting maxLifetime** - Stale connections can cause intermittent failures (set lower than database's wait_timeout)
3. **Using connectionTestQuery with modern drivers** - Use `Connection.isValid()` instead (faster, no SQL overhead)
4. **Ignoring leak detection in dev** - Always enable in development to catch leaks early
5. **Same pool size for all environments** - Production needs different tuning than development
6. **Not matching pool size with thread pool** - If you have 200 threads but only 10 connections, most threads will wait

---

## Best Practices

1. **Fixed pool size in production**: Set minimumIdle = maximumPoolSize (avoids connection creation spikes)
2. **Pool size formula**: Start with `(core_count * 2) + effective_spindle_count`
3. **Set maxLifetime < database timeout**: e.g., if MySQL wait_timeout=28800, set maxLifetime=1740000 (29 min)
4. **Enable leak detection in non-prod**: Set leakDetectionThreshold to 2x your longest expected query time
5. **Monitor pool metrics**: Expose via Actuator/Micrometer (active, idle, waiting threads)
6. **Fail fast**: Set connectionTimeout to 3-5 seconds (don't let users wait forever)
7. **Use connection validation**: Prefer isValid() over test queries

---

## Production Considerations

- **Database max_connections**: Sum of all application instance pools must be < database max_connections
- **Kubernetes scaling**: If HPA scales to 10 pods × 20 pool size = 200 connections needed at database
- **Connection storms**: After database restart, all pools reconnect simultaneously - consider staggering maxLifetime
- **DNS caching**: Java caches DNS indefinitely by default - problematic for cloud databases (RDS failover)
- **Prepared statement caching**: Enable cachePrepStmts for repeated queries (significant performance boost)
- **Health checks**: Use `/actuator/health` with DataSourceHealthIndicator to detect pool exhaustion

---

## Related Topics

- Spring Data JPA
- JdbcTemplate
- Transactions
- Spring Boot Actuator (pool metrics)
- Microservices (pool sizing per service)
- Kubernetes (scaling considerations)
