# 11. Spring Data JPA Repository

## Theory

**Repository Hierarchy:**
```
Repository (marker interface)
     ↓
CrudRepository (CRUD operations)
     ↓
PagingAndSortingRepository (+ pagination, sorting)
     ↓
JpaRepository (+ batch operations, flush, JPA-specific)
```

**Query Methods:**
Spring Data JPA derives SQL from method names:
- `findByName(String name)` → `WHERE name = ?`
- `findByStatus(Status s)` → `WHERE status = ?`
- `findByNameAndStatus(String n, Status s)` → `WHERE name = ? AND status = ?`
- `findByAgeGreaterThan(int age)` → `WHERE age > ?`
- `findByNameContaining(String n)` → `WHERE name LIKE %?%`
- `findByStatusIn(List<Status> s)` → `WHERE status IN (?...)`
- `existsByEmail(String email)` → `SELECT count(*) > 0`
- `countByStatus(Status s)` → `SELECT COUNT(*)`
- `deleteByStatus(Status s)` → `DELETE WHERE status = ?`

**Query Options:**
- **Derived queries**: Method name → SQL (simple cases)
- **JPQL**: `@Query("SELECT u FROM User u WHERE ...")` (entity-based)
- **Native SQL**: `@Query(value = "SELECT * FROM users...", nativeQuery = true)`
- **Specifications**: Dynamic queries (Criteria API wrapper)

---

## Internal Working

```
Interface declared:
  public interface UserRepository extends JpaRepository<User, Long>

At startup:
  Spring Data scans for interfaces extending Repository
       ↓
  Creates proxy implementation (SimpleJpaRepository)
       ↓
  Registers as Spring bean
       ↓
  Method calls intercepted by proxy:
    - Standard methods → SimpleJpaRepository implementation
    - Derived queries → method name parsed → JPQL generated
    - @Query → provided JPQL/SQL executed via EntityManager

Query Derivation:
  findByNameAndStatusOrderByCreatedAtDesc
       ↓
  Parser splits: findBy | Name | And | Status | OrderBy | CreatedAt | Desc
       ↓
  Generates: SELECT u FROM User u WHERE u.name = ?1 AND u.status = ?2 ORDER BY u.createdAt DESC
```

---

## Diagram

```
┌─────────────────────────────────────────────────┐
│           Repository Proxy Architecture          │
│                                                  │
│  UserRepository (interface)                      │
│       ↓                                          │
│  JDK Dynamic Proxy                               │
│       ↓                                          │
│  ┌─────────────────────────────────────┐        │
│  │ Method Interceptor                   │        │
│  │                                     │        │
│  │ findAll() → SimpleJpaRepository     │        │
│  │ findById() → SimpleJpaRepository    │        │
│  │ findByEmail() → Derived Query       │        │
│  │ @Query → JPQL Executor              │        │
│  └─────────────────────────────────────┘        │
│       ↓                                          │
│  EntityManager → JDBC → Database                 │
└─────────────────────────────────────────────────┘
```

---

## Code

```java
// === Repository with all query types ===
public interface UserRepository extends JpaRepository<User, Long> {

    // --- Derived Queries ---
    Optional<User> findByEmail(String email);
    List<User> findByStatus(UserStatus status);
    List<User> findByNameContainingIgnoreCase(String name);
    List<User> findByAgeGreaterThanEqual(int age);
    List<User> findByStatusAndCreatedAtAfter(UserStatus status, LocalDateTime after);
    List<User> findByNameInAndStatus(List<String> names, UserStatus status);
    boolean existsByEmail(String email);
    long countByStatus(UserStatus status);
    List<User> findTop10ByOrderByCreatedAtDesc();

    // --- JPQL Queries ---
    @Query("SELECT u FROM User u WHERE u.email LIKE %:domain")
    List<User> findByEmailDomain(@Param("domain") String domain);

    @Query("SELECT u FROM User u WHERE u.status = :status ORDER BY u.createdAt DESC")
    Page<User> findActiveUsers(@Param("status") UserStatus status, Pageable pageable);

    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @Query("SELECT new com.example.dto.UserSummary(u.id, u.name, u.email) FROM User u")
    List<UserSummary> findAllSummaries();

    // --- Native Queries ---
    @Query(value = "SELECT * FROM users WHERE MATCH(name, bio) AGAINST(:term IN BOOLEAN MODE)",
           nativeQuery = true)
    List<User> fullTextSearch(@Param("term") String term);

    @Query(value = "SELECT * FROM users u WHERE u.created_at > NOW() - INTERVAL '7 days'",
           nativeQuery = true)
    List<User> findRecentUsers();

    // --- Modifying Queries ---
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.status = :status WHERE u.id IN :ids")
    int bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("status") UserStatus status);

    @Modifying
    @Transactional
    @Query("DELETE FROM User u WHERE u.status = 'DELETED' AND u.updatedAt < :cutoff")
    int purgeDeletedUsers(@Param("cutoff") LocalDateTime cutoff);

    // --- Pagination and Sorting ---
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    Slice<User> findByNameContaining(String name, Pageable pageable);
}

// === Specifications (Dynamic Queries) ===
public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {}

public class UserSpecifications {

    public static Specification<User> hasName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<User> createdAfter(LocalDateTime date) {
        return (root, query, cb) ->
                date == null ? null : cb.greaterThan(root.get("createdAt"), date);
    }

    public static Specification<User> ageGreaterThan(Integer age) {
        return (root, query, cb) ->
                age == null ? null : cb.greaterThan(root.get("age"), age);
    }
}

// Usage in service:
@Service
public class UserService {

    private final UserRepository userRepository;

    public Page<User> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecifications.hasName(criteria.name()))
                .and(UserSpecifications.hasStatus(criteria.status()))
                .and(UserSpecifications.createdAfter(criteria.createdAfter()))
                .and(UserSpecifications.ageGreaterThan(criteria.minAge()));

        return userRepository.findAll(spec, pageable);
    }
}

// === Projections ===
// Interface projection (closed)
public interface UserSummaryProjection {
    Long getId();
    String getName();
    String getEmail();
    @Value("#{target.name + ' (' + target.email + ')'}")
    String getDisplayName();  // SpEL expression
}

// Record projection (DTO)
public record UserSummary(Long id, String name, String email) {}

// In repository:
List<UserSummaryProjection> findByStatus(UserStatus status);

// === Auditing ===
@Configuration
@EnableJpaAuditing
public class AuditConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(SecurityContextHolder.getContext()
                .getAuthentication().getName());
    }
}

// === Custom Repository Implementation ===
public interface UserRepositoryCustom {
    List<User> complexSearch(UserSearchCriteria criteria);
}

public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<User> complexSearch(UserSearchCriteria criteria) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);

        List<Predicate> predicates = new ArrayList<>();
        if (criteria.name() != null) {
            predicates.add(cb.like(root.get("name"), "%" + criteria.name() + "%"));
        }
        // ... more predicates

        query.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(query).getResultList();
    }
}

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {}
```

---

## Dry Run

**Scenario**: `userRepository.findByStatus(ACTIVE, PageRequest.of(0, 10, Sort.by("name")))`

```
1. Proxy intercepts call
2. Method name parsed: findBy + Status
3. Pageable applied: LIMIT 10 OFFSET 0, ORDER BY name ASC
4. Generated JPQL: SELECT u FROM User u WHERE u.status = ?1 ORDER BY u.name ASC
5. Translated to SQL:
   SELECT * FROM users WHERE status = 'ACTIVE' ORDER BY name ASC LIMIT 10 OFFSET 0
6. Count query (for Page):
   SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'
7. Returns Page<User> with:
   - content: List<User> (up to 10)
   - totalElements: total matching rows
   - totalPages: ceil(total / 10)
   - number: 0 (current page)
   - hasNext: true/false
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| findById | O(log n) — PK index |
| findByEmail (indexed) | O(log n) — B-tree |
| findByNameContaining | O(n) — unless full-text indexed |
| findAll with Pageable | O(log n + k) — k = page size |
| Count query | O(n) or O(1) with stats |
| Specification (dynamic) | Depends on generated SQL |
| Bulk @Modifying | O(m) — m = affected rows |

---

## Real Project Usage

```java
// Order repository with business queries
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.customer.id = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomerWithItems(@Param("customerId") Long customerId);

    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.createdAt < :timeout")
    List<Order> findTimedOutOrders(@Param("timeout") LocalDateTime timeout);

    @Query("SELECT new com.example.dto.OrderStats(o.status, COUNT(o), SUM(o.total)) " +
           "FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.status")
    List<OrderStats> getOrderStatistics(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);
}
```

---

## Interview Questions

1. **How does Spring Data JPA generate query implementations from method names?**
   - Parses method name into parts: `findBy` + property + condition. E.g., `findByStatusAndCreatedAtAfter` → `WHERE status = ? AND created_at > ?`. Uses reflection to map property names to entity fields.

2. **What is the difference between JPQL and native queries?**
   - JPQL: Object-oriented query on entities (portable, uses class/field names). Native: Raw SQL on tables (DB-specific, uses column names). Use JPQL for most cases; native for complex queries, functions, or performance.

3. **When should you use @Modifying? What are the caveats?**
   - Required for UPDATE/DELETE @Query methods. Caveats: Bypasses persistence context (stale entities in memory). Fix with `clearAutomatically=true` or `flushAutomatically=true`. Must be in @Transactional.

4. **What is the difference between Page and Slice?**
   - Page: Contains results + total count (executes extra COUNT query). Slice: Contains results + hasNext flag (no count, fetches size+1). Use Slice for infinite scroll, Page for numbered pagination.

5. **How do Specifications work? When would you use them?**
   - Programmatic query building using Criteria API. Composable predicates: `spec1.and(spec2).or(spec3)`. Use for dynamic filtering (search with optional criteria), avoiding method name explosion.

6. **What are projections? What types exist?**
   - Fetch subset of entity fields. Types: Interface-based (proxy with getters), Class-based (DTO constructor), Dynamic (generic type parameter). Faster than full entities — less data, no dirty checking.

7. **How do you implement a custom repository method?**
   - Create interface (CustomOrderRepository), implementation class (CustomOrderRepositoryImpl), extend both in main repository. Spring auto-discovers Impl suffix. Inject EntityManager for complex queries.

8. **What is the difference between findById and getById (getReferenceById)?**
   - findById: Returns Optional, immediately queries DB. getReferenceById: Returns proxy (no DB hit), throws EntityNotFoundException on access if not exists. Use getReference for setting FK without loading.

9. **How do you handle the N+1 problem in repositories?**
   - @Query with JOIN FETCH, @EntityGraph(attributePaths={"items"}), @BatchSize on entity, or DTO projections that don't trigger lazy loading.

10. **How does auditing work in Spring Data JPA?**
    - @EnableJpaAuditing + @EntityListeners(AuditingEntityListener.class) on entity. @CreatedDate, @LastModifiedDate auto-set timestamps. @CreatedBy, @LastModifiedBy need AuditorAware implementation.

---

## Follow-up Questions

1. **After Q1**: "What happens if the method name has a typo like findByNme?"
   → Application fails to start with `PropertyReferenceException` — Spring can't resolve the property.

2. **After Q4**: "When would you prefer Slice over Page?"
   → Infinite scrolling UIs. Slice doesn't execute a COUNT query (expensive on large tables). Just checks if hasNext by fetching size+1.

3. **After Q6**: "Are projections faster than loading full entities?"
   → Yes. Database returns fewer columns, no entity tracking overhead, no dirty checking.

4. **After Q8**: "What does getReferenceById return?"
   → A proxy. No DB hit until you access a non-ID field. Useful for setting FK references without loading the full entity.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| @Modifying without @Transactional | TransactionRequiredException | Add @Transactional |
| @Modifying bypasses persistence context | Stale data in cache | Add `clearAutomatically = true` |
| findAll() without pagination | Loads entire table | Always paginate |
| Using native queries everywhere | Not portable, no type safety | Use JPQL or Specifications |
| count() for existence check | Scans all matching rows | Use exists() or findFirst |
| Not indexing queried columns | Slow queries | Add DB indexes |

---

## Best Practices

1. **Use derived queries** for simple cases (1-2 conditions)
2. **Use @Query JPQL** for complex joins and aggregations
3. **Use Specifications** for dynamic/filter-based queries
4. **Always paginate** collection queries
5. **Use projections** for read-only views (performance)
6. **getReferenceById** for setting FK without loading entity
7. **@Modifying(clearAutomatically = true)** to avoid stale cache
8. **Index all WHERE clause columns** in the database
9. **Use Slice** for infinite scroll patterns (no count query)
10. **Custom repository** for truly complex query logic (Criteria API)

---

## Production Considerations

- **Query performance**: Monitor slow queries with Hibernate statistics
- **N+1 prevention**: Use JOIN FETCH or @EntityGraph in repository methods
- **Bulk operations**: @Modifying bypasses entity lifecycle — no events, no cascade
- **Native query portability**: Tied to specific DB dialect
- **Count query optimization**: For Page, consider separate optimized count query
- **Connection usage**: Each query holds a connection — paginate to minimize time
- **Read replicas**: Route read queries to replica with custom DataSource routing

---

## Related Topics

- → [9. Spring Data JPA](#) (entities, persistence)
- → [10. Entity Relationships](#) (JOIN FETCH, N+1)
- → [12. Transactions](#) (@Modifying needs @Transactional)
- → [13. JdbcTemplate](#) (alternative for raw SQL)
