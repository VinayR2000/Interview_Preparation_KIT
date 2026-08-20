# Topic 25: Advanced Database Performance

## Theory

### Performance Pillars

```
┌─────────────────────────────────────────────────────────────────┐
│              DATABASE PERFORMANCE PILLARS                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. QUERY PERFORMANCE                                            │
│     → Efficient SQL, proper indexing, query optimization         │
│                                                                  │
│  2. CONNECTION MANAGEMENT                                        │
│     → Pool sizing, connection reuse, leak prevention             │
│                                                                  │
│  3. SCHEMA DESIGN                                                │
│     → Normalization vs denormalization, data types               │
│                                                                  │
│  4. CACHING                                                      │
│     → Application cache, query cache, materialized views         │
│                                                                  │
│  5. HARDWARE/INFRASTRUCTURE                                      │
│     → CPU, memory, disk I/O, network                             │
│                                                                  │
│  6. CONCURRENCY                                                  │
│     → Lock contention, isolation levels, MVCC efficiency         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### The N+1 Query Problem — Most Common Performance Issue

```
PROBLEM:
─────────
// Loading 100 orders with their items
List<Order> orders = orderRepository.findAll(); // 1 query
for (Order order : orders) {
    order.getItems(); // 100 queries (one per order)!
}
// Total: 1 + 100 = 101 queries!

SQL Generated:
  SELECT * FROM orders;                          -- 1 query
  SELECT * FROM order_items WHERE order_id = 1;  -- +100 queries
  SELECT * FROM order_items WHERE order_id = 2;
  ...
  SELECT * FROM order_items WHERE order_id = 100;

SOLUTION — Fetch Join:
─────────
@Query("SELECT o FROM Order o JOIN FETCH o.items")
List<Order> findAllWithItems(); // 1 query!

SQL Generated:
  SELECT o.*, i.* FROM orders o
  JOIN order_items i ON o.id = i.order_id;      -- 1 query
```

---

## Internal Working — Query Execution Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│            QUERY EXECUTION PIPELINE (PostgreSQL)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  SQL Text                                                        │
│     │                                                            │
│     ▼                                                            │
│  ┌─────────┐                                                     │
│  │ PARSER  │ → Syntax check → Parse tree                         │
│  └────┬────┘                                                     │
│       ▼                                                          │
│  ┌──────────┐                                                    │
│  │ ANALYZER │ → Resolve tables/columns → Query tree              │
│  └────┬─────┘                                                    │
│       ▼                                                          │
│  ┌──────────┐                                                    │
│  │ REWRITER │ → Apply rules, view expansion                      │
│  └────┬─────┘                                                    │
│       ▼                                                          │
│  ┌───────────┐                                                   │
│  │ PLANNER/  │ → Generate possible plans                         │
│  │ OPTIMIZER │ → Estimate costs using statistics                  │
│  │           │ → Choose cheapest plan                             │
│  └────┬──────┘                                                   │
│       ▼                                                          │
│  ┌──────────┐                                                    │
│  │ EXECUTOR │ → Execute chosen plan                              │
│  │          │ → Return results                                   │
│  └──────────┘                                                    │
│                                                                  │
│  COST FACTORS:                                                   │
│  • seq_page_cost = 1.0 (baseline)                                │
│  • random_page_cost = 4.0 (random I/O is 4x sequential)         │
│  • cpu_tuple_cost = 0.01                                         │
│  • cpu_index_tuple_cost = 0.005                                  │
│  • cpu_operator_cost = 0.0025                                    │
└─────────────────────────────────────────────────────────────────┘
```

### Scan Types Performance

```
SCAN TYPE COMPARISON:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sequential Scan:
  → Reads ENTIRE table page by page
  → Best when: selecting large portion of table (>5-10%)
  → Cost: proportional to table size

Index Scan:
  → Uses B-tree index to find rows
  → Reads index → follows pointers to heap (table)
  → Best when: selecting small portion of table (<5%)
  → Cost: log(n) for index + random I/O per row

Index-Only Scan:
  → Uses covering index (all needed columns in index)
  → Never accesses heap table
  → FASTEST for covered queries
  → Requires visibility map to be up-to-date (VACUUM)

Bitmap Index Scan:
  → Scans index → builds bitmap of pages
  → Reads pages in physical order (sequential I/O)
  → Best when: selecting moderate portion (5-20%)
  → Combines multiple indexes (BitmapAnd, BitmapOr)
```

---

## Diagram — Performance Tuning Decision Tree

```
┌─────────────────────────────────────────────────────────────────┐
│           SLOW QUERY TROUBLESHOOTING DECISION TREE               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Query is slow?                                                  │
│     │                                                            │
│     ├── Run EXPLAIN ANALYZE                                      │
│     │                                                            │
│     ├── Sequential Scan on large table?                          │
│     │     ├── Add appropriate index                              │
│     │     └── Check WHERE clause sargability                     │
│     │                                                            │
│     ├── Index exists but not used?                               │
│     │     ├── Statistics outdated → ANALYZE                      │
│     │     ├── Function on column → expression index              │
│     │     ├── Type mismatch → fix types                          │
│     │     ├── OR conditions → restructure query                  │
│     │     └── Too many rows match → optimizer chose seq scan     │
│     │                                                            │
│     ├── Nested Loop with many iterations?                        │
│     │     ├── Missing index on inner relation                    │
│     │     ├── Consider increasing work_mem                       │
│     │     └── May need query restructuring                       │
│     │                                                            │
│     ├── Sort taking long?                                        │
│     │     ├── Add index matching ORDER BY                        │
│     │     ├── Increase work_mem (in-memory sort)                 │
│     │     └── Limit result set                                   │
│     │                                                            │
│     ├── Hash Join using too much memory?                         │
│     │     ├── Increase work_mem                                  │
│     │     ├── Add index for nested loop alternative              │
│     │     └── Partition large tables                             │
│     │                                                            │
│     └── Lock wait?                                               │
│           ├── Identify blocking query                            │
│           ├── Optimize blocking transaction                      │
│           └── Consider isolation level change                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code — Performance Optimization Patterns

### Solving N+1 with JPA

```java
// PROBLEM: N+1
@Entity
public class Order {
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY) // Default lazy
    private List<OrderItem> items;
}

// Solution 1: Fetch Join (JPQL)
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.customer.id = :customerId")
List<Order> findOrdersWithItems(@Param("customerId") Long customerId);

// Solution 2: Entity Graph
@EntityGraph(attributePaths = {"items", "items.product"})
@Query("SELECT o FROM Order o WHERE o.customer.id = :customerId")
List<Order> findOrdersWithItemsGraph(@Param("customerId") Long customerId);

// Solution 3: Batch fetching (Hibernate)
@Entity
public class Order {
    @OneToMany(mappedBy = "order")
    @BatchSize(size = 25) // Fetches items for 25 orders at once
    private List<OrderItem> items;
}
// Instead of 100 queries, now: 1 + ceil(100/25) = 5 queries

// Solution 4: DTO Projection (most efficient)
@Query("SELECT new com.app.dto.OrderSummary(o.id, o.status, o.totalAmount) " +
       "FROM Order o WHERE o.customer.id = :customerId")
List<OrderSummary> findOrderSummaries(@Param("customerId") Long customerId);
```

### Pagination Performance

```java
// PROBLEM: Offset pagination with large offsets
// SELECT * FROM orders ORDER BY id LIMIT 20 OFFSET 100000;
// → DB scans 100,020 rows, returns only 20. SLOW!

// SOLUTION: Keyset/Cursor pagination
@Query(value = "SELECT * FROM orders WHERE id > :lastId ORDER BY id LIMIT :pageSize",
       nativeQuery = true)
List<Order> findNextPage(@Param("lastId") Long lastId, @Param("pageSize") int pageSize);

// Usage:
// First page: lastId = 0, pageSize = 20
// Next page: lastId = last_order_id_from_previous_page, pageSize = 20
// ALWAYS O(1) — uses index to start from exact position
```

### Bulk Operations

```java
// PROBLEM: Updating 10,000 rows one by one through Hibernate
@Transactional
public void deactivateOldUsers() {
    List<User> users = userRepo.findByLastLoginBefore(cutoffDate);
    for (User user : users) {
        user.setStatus("INACTIVE"); // Dirty checking on each
    }
    // Flush: 10,000 individual UPDATE statements!
}

// SOLUTION: Bulk update (single SQL)
@Modifying
@Query("UPDATE User u SET u.status = 'INACTIVE' WHERE u.lastLogin < :cutoff")
int deactivateOldUsers(@Param("cutoff") LocalDateTime cutoff);
// Single SQL statement, no entity loading, no dirty checking
```

### Connection Pool Monitoring

```java
@Component
@Slf4j
public class ConnectionPoolMonitor {

    @Autowired
    private HikariDataSource dataSource;

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void logPoolStats() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        
        log.info("Pool Stats — Active: {}, Idle: {}, Waiting: {}, Total: {}",
            pool.getActiveConnections(),
            pool.getIdleConnections(),
            pool.getThreadsAwaitingConnection(),
            pool.getTotalConnections()
        );
        
        if (pool.getThreadsAwaitingConnection() > 0) {
            log.warn("CONNECTION POOL PRESSURE: {} threads waiting!",
                pool.getThreadsAwaitingConnection());
        }
    }
}
```

---

## Sargability — Critical Concept

```
SARGABLE (Search ARGument ABLE) = Index CAN be used
NON-SARGABLE = Index CANNOT be used

┌────────────────────────────────┬────────────────────────────────┐
│ NON-SARGABLE (index not used) │ SARGABLE (index used)          │
├────────────────────────────────┼────────────────────────────────┤
│ WHERE YEAR(created_at) = 2024  │ WHERE created_at >= '2024-01-01│
│                                │   AND created_at < '2025-01-01'│
├────────────────────────────────┼────────────────────────────────┤
│ WHERE UPPER(name) = 'JOHN'     │ WHERE name = 'john' (with CI   │
│                                │   collation or expression idx) │
├────────────────────────────────┼────────────────────────────────┤
│ WHERE salary + 1000 > 50000    │ WHERE salary > 49000           │
├────────────────────────────────┼────────────────────────────────┤
│ WHERE name LIKE '%john%'       │ WHERE name LIKE 'john%'        │
├────────────────────────────────┼────────────────────────────────┤
│ WHERE id::text = '123'         │ WHERE id = 123                 │
├────────────────────────────────┼────────────────────────────────┤
│ WHERE NOT status = 'ACTIVE'    │ WHERE status IN ('INACTIVE',   │
│                                │   'SUSPENDED', 'DELETED')      │
└────────────────────────────────┴────────────────────────────────┘

RULE: Never apply a function to an indexed column in WHERE clause.
      Apply transformations to the constant/comparison side instead.
```

---

## Dry Run — EXPLAIN ANALYZE

```sql
EXPLAIN ANALYZE
SELECT o.id, o.status, c.name
FROM orders o
JOIN customers c ON o.customer_id = c.id
WHERE o.status = 'PENDING'
  AND o.created_at >= '2024-01-01'
ORDER BY o.created_at DESC
LIMIT 20;
```

```
Output:
─────────
Limit (cost=0.86..45.23 rows=20 width=48) (actual time=0.052..0.089 rows=20 loops=1)
  → Nested Loop (cost=0.86..1234.56 rows=555 width=48) (actual time=0.051..0.086 rows=20 loops=1)
       → Index Scan Backward using idx_orders_status_created on orders o
            (cost=0.43..890.12 rows=555 width=28) (actual time=0.035..0.048 rows=20 loops=1)
            Index Cond: ((status = 'PENDING') AND (created_at >= '2024-01-01'))
       → Index Scan using customers_pkey on customers c
            (cost=0.43..0.62 rows=1 width=24) (actual time=0.001..0.001 rows=1 loops=20)
            Index Cond: (id = o.customer_id)
Planning Time: 0.256 ms
Execution Time: 0.112 ms

READING THE PLAN:
─────────────────
1. Uses index idx_orders_status_created (composite index on status + created_at)
2. Scans backward (for DESC order) — no separate sort needed!
3. LIMIT stops execution after 20 rows found
4. Nested Loop: For each order, does PK lookup on customers
5. Total time: 0.112ms — excellent!

KEY INDEX:
  CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
  — Covers both WHERE conditions AND ORDER BY!
```

---

## Complexity — Performance Impact

| Optimization | Before | After | Improvement |
|---|---|---|---|
| Add index on WHERE column | O(n) seq scan | O(log n) index scan | 100x+ for large tables |
| Fix N+1 (fetch join) | N+1 queries | 1 query | N × latency reduction |
| Batch insert (1000 rows) | 1000 round-trips | 1 round-trip | 100x network reduction |
| Keyset pagination | O(offset + limit) | O(limit) | Linear → constant |
| Covering index | Index scan + heap fetch | Index-only scan | 2x-10x for wide tables |
| Bulk UPDATE | N individual UPDATEs | 1 UPDATE statement | N × overhead reduction |
| DTO projection | Load full entity graph | Load only needed cols | Memory + transfer reduction |
| Connection pool | 20-100ms per connection | ~0.001ms (reuse) | 20,000x improvement |

---

## Real Project Usage — Comprehensive Performance Setup

```java
@Configuration
public class PerformanceConfig {

    // Hibernate batching for bulk operations
    @Bean
    public HibernatePropertiesCustomizer hibernateCustomizer() {
        return properties -> {
            properties.put("hibernate.jdbc.batch_size", "50");
            properties.put("hibernate.jdbc.batch_versioned_data", "true");
            properties.put("hibernate.order_inserts", "true");
            properties.put("hibernate.order_updates", "true");
            properties.put("hibernate.jdbc.fetch_size", "100");
            // Second-level cache
            properties.put("hibernate.cache.use_second_level_cache", "true");
            properties.put("hibernate.cache.region.factory_class", 
                "org.hibernate.cache.jcache.JCacheRegionFactory");
        };
    }
}

// Slow query logging
// application.yml:
// spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 50
```

### Implementing Efficient Search

```java
@Repository
public class ProductSearchRepository {

    @PersistenceContext
    private EntityManager em;

    public Page<ProductDTO> searchProducts(ProductFilter filter, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<ProductDTO> query = cb.createQuery(ProductDTO.class);
        Root<Product> root = query.from(Product.class);

        // DTO Projection — don't load entire entity
        query.select(cb.construct(ProductDTO.class,
            root.get("id"),
            root.get("name"),
            root.get("price"),
            root.get("category")
        ));

        // Dynamic WHERE clause
        List<Predicate> predicates = new ArrayList<>();
        if (filter.getCategory() != null) {
            predicates.add(cb.equal(root.get("category"), filter.getCategory()));
        }
        if (filter.getMinPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.getMinPrice()));
        }
        if (filter.getMaxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.getMaxPrice()));
        }
        if (filter.getSearchTerm() != null) {
            predicates.add(cb.like(cb.lower(root.get("name")), 
                "%" + filter.getSearchTerm().toLowerCase() + "%"));
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("createdAt")));

        // Execute with pagination
        TypedQuery<ProductDTO> typedQuery = em.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<ProductDTO> results = typedQuery.getResultList();

        // Count query (separate, simpler)
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        Long total = em.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }
}
```

---

## Interview Questions and Answers

### Q1: How do you identify and fix slow queries in production?

**Answer:**

1. **Identify**: Enable slow query logging
   ```sql
   -- PostgreSQL
   SET log_min_duration_statement = 100; -- Log queries taking > 100ms
   ```

2. **Analyze**: Run `EXPLAIN ANALYZE` on the slow query

3. **Common fixes**:
   - Missing index → add targeted index
   - Sequential scan → check sargability
   - Bad join order → update statistics with ANALYZE
   - Too many rows → add more selective WHERE conditions
   - Lock wait → optimize concurrent transactions

4. **Verify**: Compare before/after EXPLAIN ANALYZE

### Q2: Explain the N+1 problem and all ways to solve it.

**Answer:**

**Problem**: Loading parent entities, then lazily loading children one-by-one.

**Solutions**:
1. **Fetch Join** (`JOIN FETCH`): Single query with JOIN. Best for single collection.
2. **Entity Graph** (`@EntityGraph`): Declarative eager loading.
3. **Batch Size** (`@BatchSize(size=25)`): Fetches children in batches.
4. **Subselect Fetch** (`@Fetch(FetchMode.SUBSELECT)`): Single subselect for all children.
5. **DTO Projection**: Skip entities entirely, load exactly what's needed.
6. **Native query with JOIN**: Full control over SQL.

Best choice depends on:
- Number of collections to load (fetch join = 1 max for pagination)
- Whether you need entities or just data (DTO is fastest)
- Whether pagination is required (entity graph + pagination is problematic)

### Q3: When would a sequential scan be FASTER than an index scan?

**Answer:**
- When selecting >5-10% of the table (random I/O vs sequential I/O)
- Small tables (entire table fits in 1-2 pages)
- No index exists on the needed column
- Query returns most/all rows
- After bulk load before statistics are updated

The optimizer chooses seq scan when `random_page_cost × pages_to_read > seq_page_cost × total_pages`.

### Q4: How do you handle database performance in a microservices architecture?

**Answer:**
1. **Database per service** — isolate load
2. **Read replicas** — offload read traffic
3. **Caching layer** (Redis) — reduce DB hits
4. **Connection pooling** — per service with proper sizing
5. **CQRS** — separate read/write models for different optimization
6. **Async processing** — move heavy operations to background
7. **Pagination** — never return unbounded results
8. **Circuit breaker** — prevent cascading failures from slow DB

---

## Follow-up Questions and Answers

### Q: How does Hibernate's second-level cache work and when should you use it?

**Answer:**
- **First-level cache**: Per-session (EntityManager), always on, short-lived
- **Second-level cache**: Shared across sessions, configurable, long-lived

Use when:
- Data is read frequently but rarely updated
- Lookup tables (countries, categories)
- Reference data

Don't use when:
- Data changes frequently
- Data is unique per request
- Distributed system without cache invalidation strategy

### Q: What is the difference between `work_mem` and `shared_buffers` in PostgreSQL?

**Answer:**
- **shared_buffers**: Shared memory for caching data pages. Set to 25% of system RAM.
- **work_mem**: Per-operation memory for sorts, hashes, joins. Applied per operation per connection.
  - If work_mem = 256MB and 100 connections each do a sort → 25.6GB RAM used!
  - Keep it modest (4MB-64MB), increase selectively for specific queries.

---

## Common Mistakes

| Mistake | Impact | Fix |
|---|---|---|
| SELECT * everywhere | Extra data transfer, no index-only scan | Select only needed columns |
| No EXPLAIN before production | Discover issues under load | Always EXPLAIN complex queries |
| Indexing every column | Slow writes, wasted storage | Index based on query patterns |
| Ignoring VACUUM | Bloated tables, slow queries | Tune autovacuum |
| Large transactions | Long lock holds | Keep transactions short |
| Not using DTO projections | Loading entire entity graph | Project only needed fields |
| Offset pagination on millions | Scans entire offset | Use keyset pagination |
| No query timeout | Runaway queries consume resources | Set statement_timeout |

---

## Best Practices

1. **Always run EXPLAIN ANALYZE** before deploying new queries
2. **Use DTO projections** when you don't need entity lifecycle
3. **Implement keyset pagination** for large datasets
4. **Batch all bulk operations** (inserts, updates)
5. **Monitor slow queries** continuously in production
6. **Keep transactions short** — no external calls inside
7. **Use covering indexes** for frequently-executed queries
8. **Set statement timeout** to prevent runaway queries
9. **VACUUM and ANALYZE** regularly (or tune autovacuum)
10. **Profile before optimizing** — measure, don't guess

---

## Production Considerations

### Monitoring Dashboard Metrics

| Metric | Warning Threshold | Critical Threshold |
|---|---|---|
| Query response time (p95) | > 100ms | > 500ms |
| Active connections | > 70% pool | > 90% pool |
| Threads waiting for connection | > 0 | > 5 |
| Cache hit ratio | < 99% | < 95% |
| Dead tuples / live tuples | > 10% | > 20% |
| Replication lag | > 1s | > 10s |
| CPU utilization | > 70% | > 90% |
| Disk I/O wait | > 20% | > 50% |
| Lock waits | > 10/s | > 50/s |

### Emergency Checklist — DB Under Load

```
1. Check active queries:
   SELECT * FROM pg_stat_activity WHERE state = 'active';

2. Find blocked queries:
   SELECT * FROM pg_stat_activity WHERE wait_event_type = 'Lock';

3. Kill problematic queries:
   SELECT pg_cancel_backend(pid); -- graceful
   SELECT pg_terminate_backend(pid); -- force

4. Check connection count:
   SELECT count(*) FROM pg_stat_activity;

5. Identify slow queries:
   SELECT * FROM pg_stat_statements ORDER BY total_time DESC LIMIT 10;

6. Check table bloat:
   SELECT relname, n_dead_tup, n_live_tup FROM pg_stat_user_tables
   WHERE n_dead_tup > 10000 ORDER BY n_dead_tup DESC;
```

---

## Related Topics

- Topic 14: Indexes
- Topic 16: Query Optimization (EXPLAIN, scan types)
- Topic 19: JPA, Hibernate & Spring Data JPA
- Topic 23: Spring Transactions
- Topic 24: JDBC & Connection Pooling
