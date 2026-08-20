# 13. JdbcTemplate

## Theory

**JdbcTemplate:**
Spring's core class for JDBC operations. It handles connection management, exception translation, and resource cleanup while you write the SQL.

**Key Classes:**
- `JdbcTemplate` — Positional parameters (`?`)
- `NamedParameterJdbcTemplate` — Named parameters (`:name`)
- `RowMapper<T>` — Maps a single ResultSet row to an object
- `ResultSetExtractor<T>` — Maps the entire ResultSet to a single object
- `BeanPropertyRowMapper<T>` — Auto-maps columns to bean properties

**When to use JdbcTemplate over JPA:**
- Complex reporting queries with aggregations
- Bulk inserts/updates (better performance)
- Stored procedures
- Legacy databases with non-standard schemas
- When you need raw SQL control
- Read-heavy queries where JPA overhead is unnecessary

---

## Internal Working

```
jdbcTemplate.query(sql, rowMapper, params)
       ↓
Get Connection from DataSource (HikariCP pool)
       ↓
PreparedStatement created with SQL
       ↓
Parameters bound (prevents SQL injection)
       ↓
Execute query → ResultSet
       ↓
RowMapper maps each row → Java object
       ↓
ResultSet closed
       ↓
PreparedStatement closed
       ↓
Connection returned to pool
       ↓
If exception: translated to Spring DataAccessException hierarchy
  - SQLException → DataAccessException
  - Duplicate key → DuplicateKeyException
  - Bad SQL → BadSqlGrammarException
```

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│              JdbcTemplate Architecture                │
│                                                      │
│  Your Code                                           │
│    ↓ SQL + params + RowMapper                        │
│  ┌────────────────────────────────┐                 │
│  │        JdbcTemplate            │                 │
│  │  - Connection management       │                 │
│  │  - Exception translation       │                 │
│  │  - Resource cleanup            │                 │
│  └──────────────┬─────────────────┘                 │
│                 ↓                                     │
│  ┌────────────────────────────────┐                 │
│  │        DataSource              │                 │
│  │     (HikariCP Pool)           │                 │
│  └──────────────┬─────────────────┘                 │
│                 ↓                                     │
│  ┌────────────────────────────────┐                 │
│  │        Database                │                 │
│  └────────────────────────────────┘                 │
└─────────────────────────────────────────────────────┘

Exception Translation:
SQLException
  ├── Duplicate key → DuplicateKeyException
  ├── FK violation → DataIntegrityViolationException
  ├── Connection failed → DataAccessResourceFailureException
  ├── Bad SQL syntax → BadSqlGrammarException
  └── Others → UncategorizedDataAccessException
```

---

## Code

```java
// === Basic JdbcTemplate operations ===
@Repository
public class UserJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper
    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setEmail(rs.getString("email"));
        user.setStatus(UserStatus.valueOf(rs.getString("status")));
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return user;
    };

    // SELECT single
    public Optional<User> findById(Long id) {
        try {
            User user = jdbcTemplate.queryForObject(
                    "SELECT * FROM users WHERE id = ?",
                    USER_ROW_MAPPER, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // SELECT list
    public List<User> findByStatus(UserStatus status) {
        return jdbcTemplate.query(
                "SELECT * FROM users WHERE status = ? ORDER BY created_at DESC",
                USER_ROW_MAPPER, status.name());
    }

    // INSERT
    public Long create(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (name, email, status, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getStatus().name());
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    // UPDATE
    public int update(User user) {
        return jdbcTemplate.update(
                "UPDATE users SET name = ?, email = ?, status = ? WHERE id = ?",
                user.getName(), user.getEmail(), user.getStatus().name(), user.getId());
    }

    // DELETE
    public int delete(Long id) {
        return jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }

    // COUNT
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    }

    // Scalar value
    public String findEmailById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE id = ?", String.class, id);
    }
}

// === NamedParameterJdbcTemplate ===
@Repository
public class OrderJdbcRepository {

    private final NamedParameterJdbcTemplate namedTemplate;

    public OrderJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
    }

    public List<Order> findByCustomerAndStatus(Long customerId, OrderStatus status) {
        String sql = "SELECT * FROM orders WHERE customer_id = :customerId AND status = :status";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("status", status.name());

        return namedTemplate.query(sql, params, ORDER_ROW_MAPPER);
    }

    // IN clause
    public List<Order> findByIds(List<Long> ids) {
        String sql = "SELECT * FROM orders WHERE id IN (:ids)";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", ids);
        return namedTemplate.query(sql, params, ORDER_ROW_MAPPER);
    }

    // BeanPropertyRowMapper (auto-maps column names to field names)
    public List<OrderSummary> getOrderSummaries() {
        return namedTemplate.query(
                "SELECT id, customer_id, total_amount, status, created_at FROM orders",
                new MapSqlParameterSource(),
                new BeanPropertyRowMapper<>(OrderSummary.class));
    }
}

// === Batch Operations ===
@Repository
public class BatchRepository {

    private final JdbcTemplate jdbcTemplate;

    // Batch insert (much faster than individual inserts)
    public int[] batchInsertUsers(List<User> users) {
        return jdbcTemplate.batchUpdate(
                "INSERT INTO users (name, email, status) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        User user = users.get(i);
                        ps.setString(1, user.getName());
                        ps.setString(2, user.getEmail());
                        ps.setString(3, user.getStatus().name());
                    }

                    @Override
                    public int getBatchSize() {
                        return users.size();
                    }
                });
    }

    // Simpler batch with NamedParameterJdbcTemplate
    public int[] batchInsert(List<User> users) {
        SqlParameterSource[] batch = users.stream()
                .map(u -> new MapSqlParameterSource()
                        .addValue("name", u.getName())
                        .addValue("email", u.getEmail()))
                .toArray(SqlParameterSource[]::new);

        return namedParameterJdbcTemplate.batchUpdate(
                "INSERT INTO users (name, email) VALUES (:name, :email)", batch);
    }
}

// === ResultSetExtractor (one-to-many mapping) ===
public class OrderWithItemsExtractor implements ResultSetExtractor<List<Order>> {

    @Override
    public List<Order> extractData(ResultSet rs) throws SQLException {
        Map<Long, Order> orderMap = new LinkedHashMap<>();

        while (rs.next()) {
            Long orderId = rs.getLong("order_id");
            Order order = orderMap.computeIfAbsent(orderId, id -> {
                try {
                    Order o = new Order();
                    o.setId(id);
                    o.setCustomerName(rs.getString("customer_name"));
                    o.setTotal(rs.getBigDecimal("total"));
                    return o;
                } catch (SQLException e) { throw new RuntimeException(e); }
            });

            // Map order items
            Long itemId = rs.getLong("item_id");
            if (itemId > 0) {
                OrderItem item = new OrderItem();
                item.setId(itemId);
                item.setProductName(rs.getString("product_name"));
                item.setQuantity(rs.getInt("quantity"));
                order.getItems().add(item);
            }
        }

        return new ArrayList<>(orderMap.values());
    }
}
```

---

## Dry Run

**Scenario**: Batch insert 1000 users

```
1. jdbcTemplate.batchUpdate() called with batch size 1000
2. Connection obtained from HikariCP
3. PreparedStatement created: INSERT INTO users (name, email, status) VALUES (?, ?, ?)
4. For each user (i=0 to 999):
   - setValues(ps, i) called
   - ps.addBatch()
5. After all 1000: ps.executeBatch()
6. One round-trip to DB with 1000 inserts
7. Connection returned to pool
8. Returns int[1000] with rows affected per statement

Performance comparison:
- Individual inserts: 1000 round-trips → ~5 seconds
- Batch insert: 1 round-trip → ~100ms
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Single query | O(1) network round-trip + O(n) result processing |
| Batch insert (k items) | O(1) round-trip + O(k) DB processing |
| ResultSetExtractor | O(n) — single pass through ResultSet |
| RowMapper per row | O(1) per row × n rows |

---

## Real Project Usage

```java
// Reporting queries — better with JdbcTemplate than JPA
@Repository
public class ReportRepository {

    private final NamedParameterJdbcTemplate template;

    public List<SalesReport> getMonthlySales(int year) {
        String sql = """
            SELECT 
                EXTRACT(MONTH FROM o.created_at) as month,
                COUNT(*) as order_count,
                SUM(o.total_amount) as revenue,
                AVG(o.total_amount) as avg_order_value
            FROM orders o
            WHERE EXTRACT(YEAR FROM o.created_at) = :year
              AND o.status = 'COMPLETED'
            GROUP BY EXTRACT(MONTH FROM o.created_at)
            ORDER BY month
            """;

        return template.query(sql,
                new MapSqlParameterSource("year", year),
                new BeanPropertyRowMapper<>(SalesReport.class));
    }
}
```

---

## Interview Questions

1. **What is JdbcTemplate? When would you use it over JPA?**
   - Spring's abstraction over JDBC that handles connection management, exception translation, and resource cleanup. Use over JPA for: complex SQL, bulk operations, reporting queries, when ORM overhead is unwanted, legacy schemas that don't map to entities.

2. **What is the difference between RowMapper and ResultSetExtractor?**
   - RowMapper: Maps ONE row to one object (called per row, returns List). ResultSetExtractor: Processes ENTIRE ResultSet (called once, you iterate). Use ResultSetExtractor for grouping one-to-many JOINs into parent-child objects.

3. **How does JdbcTemplate handle connection management?**
   - Gets connection from DataSource (pool), executes query, closes/returns connection in finally block. Within @Transactional: reuses same connection. No manual open/close needed — prevents leaks.

4. **How does Spring translate SQL exceptions?**
   - SQLExceptionTranslator converts vendor-specific SQLExceptions to Spring's DataAccessException hierarchy (DataIntegrityViolationException, DuplicateKeyException, etc.). Consistent regardless of DB vendor.

5. **What is NamedParameterJdbcTemplate? When is it preferred?**
   - Allows named parameters (`:userId`) instead of positional (`?`). Preferred when: 3+ parameters, same param used multiple times, better readability. Uses MapSqlParameterSource or BeanPropertySqlParameterSource.

6. **How do you perform batch operations with JdbcTemplate?**
   - `jdbcTemplate.batchUpdate(sql, BatchPreparedStatementSetter)` or `NamedParameterJdbcTemplate.batchUpdate(sql, SqlParameterSource[])`. Much faster than individual inserts (single round-trip for batch). Chunk large batches (500-1000).

7. **How do you get auto-generated keys after an insert?**
   - Use `KeyHolder keyHolder = new GeneratedKeyHolder()` with `jdbcTemplate.update(psc, keyHolder)`. After insert: `keyHolder.getKey().longValue()` returns generated ID.

8. **How does JdbcTemplate prevent SQL injection?**
   - Uses PreparedStatement with parameterized queries. `?` placeholders separate SQL structure from data. DB compiles SQL first, then binds parameters — values never interpreted as SQL.

9. **Can you use JdbcTemplate and JPA together?**
   - Yes. Both can share the same DataSource and transaction. Within @Transactional, both use the same connection. Useful: JPA for CRUD, JdbcTemplate for complex reports/bulk operations.

10. **What is BeanPropertyRowMapper?**
    - Automatically maps ResultSet columns to Java bean properties by name (column_name → propertyName via relaxed naming). Convenient for simple mappings. Less performant than custom RowMapper (uses reflection).

---

## Follow-up Questions

1. **After Q1**: "Can JdbcTemplate and JPA share the same transaction?"
   → Yes. Both use the same DataSource/Connection. Within @Transactional, they share the transaction.

2. **After Q2**: "When would you prefer ResultSetExtractor?"
   → For one-to-many relationships in a single query (JOIN), where one parent has multiple children — need to group rows.

3. **After Q8**: "How does PreparedStatement prevent injection?"
   → Parameters are sent separately from SQL. DB compiles SQL first, then binds parameters — parameters can never be interpreted as SQL code.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| String concatenation in SQL | SQL injection vulnerability | Use ? or :param placeholders |
| Not closing resources | Connection leak | JdbcTemplate handles this automatically |
| queryForObject with no results | EmptyResultDataAccessException | Catch exception or use query() |
| Large batch without chunking | OutOfMemoryError | Chunk into batches of 500-1000 |
| Not using NamedParameterJdbcTemplate | Positional params hard to maintain | Use named params for 3+ parameters |

---

## Best Practices

1. **Always use parameterized queries** — never concatenate user input
2. **NamedParameterJdbcTemplate** for readability with multiple params
3. **Batch operations** for bulk inserts/updates (10-100x faster)
4. **Custom RowMapper** over BeanPropertyRowMapper (type-safe, faster)
5. **Use with JPA** — JdbcTemplate for reports, JPA for CRUD
6. **Transaction management** — same @Transactional works
7. **Chunk large batches** — 500-1000 per batch for memory management
8. **Handle EmptyResultDataAccessException** for queryForObject
9. **Use ResultSetExtractor** for complex object graphs from JOINs
10. **Keep SQL in constants or external files** for maintainability

---

## Production Considerations

- **Connection pool**: JdbcTemplate uses same HikariCP pool as JPA
- **Query timeout**: Set `jdbcTemplate.setQueryTimeout(30)` for long queries
- **Fetch size**: `jdbcTemplate.setFetchSize(100)` for large result sets
- **Monitoring**: Log slow queries (> 100ms)
- **Batch size tuning**: Optimal batch size depends on DB (typically 500-5000)
- **Memory**: Large ResultSets — use streaming with `queryForStream()` (Spring 5.3+)

---

## Related Topics

- → [9. Spring Data JPA](#) (higher-level alternative)
- → [11. Spring Data JPA Repository](#) (query methods vs raw SQL)
- → [12. Transactions](#) (same transaction manager)
- → [14. Database Connection Pool](#) (HikariCP shared)
