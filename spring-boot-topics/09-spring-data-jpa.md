# 9. Spring Data JPA

## Theory

**JPA (Java Persistence API):**
A specification for Object-Relational Mapping (ORM) in Java. Hibernate is the most common implementation. Spring Data JPA builds on top of both.

**Core Concepts:**

- **Entity**: A Java class mapped to a database table
- **Persistence Context**: First-level cache managing entity instances
- **EntityManager**: API to interact with persistence context
- **Entity Lifecycle States**: Transient → Managed → Detached → Removed

**Entity Lifecycle:**

| State | Description |
|-------|-------------|
| Transient | New object, not associated with persistence context |
| Managed | Attached to persistence context, changes auto-synced |
| Detached | Was managed, now disconnected (after transaction ends) |
| Removed | Marked for deletion |

**Key Mechanisms:**
- **Dirty Checking**: Hibernate detects changes to managed entities and auto-generates UPDATE SQL at flush time
- **First-level Cache**: Persistence context caches entities by ID (no duplicate instances in same session)
- **Flush**: Syncs persistence context → database (before commit, before queries)
- **Clear**: Detaches all entities from persistence context

**Entity Annotations:**

| Annotation | Purpose |
|-----------|---------|
| @Entity | Marks class as JPA entity |
| @Table(name) | Maps to specific table |
| @Id | Primary key |
| @GeneratedValue | Auto-generated ID strategy |
| @Column | Column mapping details |
| @Transient | Not persisted to DB |
| @Enumerated | Enum → DB mapping (STRING/ORDINAL) |
| @Temporal | Date/Time mapping (legacy Date API) |
| @CreationTimestamp | Auto-set on insert |
| @UpdateTimestamp | Auto-set on update |

---

## Internal Working

```
repository.save(entity)
       ↓
EntityManager.persist() or merge()
       ↓
Entity becomes "Managed" in Persistence Context
       ↓
Transaction commits
       ↓
Flush triggered:
  - Dirty checking compares current state vs snapshot
  - Generates INSERT/UPDATE SQL
  - Executes via JDBC
       ↓
Database updated
       ↓
Transaction ends → entity becomes "Detached"

Dirty Checking:
┌─────────────────┐     ┌──────────────────┐
│ Entity Snapshot  │     │  Current Entity  │
│ (at load time)   │     │  (modified)      │
│ name = "Alice"   │     │  name = "Bob"    │
│ email = "a@b.c"  │     │  email = "a@b.c" │
└─────────────────┘     └──────────────────┘
         ↕ Compare at flush
   name changed → generate UPDATE SET name='Bob' WHERE id=1
```

---

## Diagram

```
┌─────────────────────────────────────────────────┐
│             Persistence Context                   │
│          (First-Level Cache)                      │
│                                                  │
│  ┌────────────────────────────────────────┐     │
│  │ ID=1 → User("Alice", "a@b.com")       │     │
│  │ ID=2 → User("Bob", "b@c.com")         │     │
│  │ ID=3 → User("Charlie", "c@d.com")     │     │
│  └────────────────────────────────────────┘     │
│                                                  │
│  Operations:                                     │
│  find(1) → Returns from cache (no SQL)           │
│  find(4) → SQL SELECT, puts in cache            │
│  user.setName("X") → dirty checked at flush     │
└─────────────────────────────────────────────────┘
         │ flush()
         ▼
┌─────────────────────────────────────────────────┐
│              Database                             │
│  users table                                     │
│  | id | name    | email     |                   │
│  | 1  | Alice   | a@b.com   |                   │
│  | 2  | Bob     | b@c.com   |                   │
└─────────────────────────────────────────────────┘

Entity Lifecycle:
  new User()          persist()         detach()/close()
TRANSIENT ──────────▶ MANAGED ──────────▶ DETACHED
                        │                    │
                        │ remove()           │ merge()
                        ▼                    │
                      REMOVED ◀──────────────┘
```

---

## Code

```java
// === Entity with all common annotations ===
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_status", columnList = "status")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(precision = 10, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Transient  // Not stored in DB
    private String fullDisplayName;

    @Version  // Optimistic locking
    private Long version;

    // Constructors
    protected User() {} // JPA requires no-arg constructor

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Getters and setters
}

public enum UserStatus {
    ACTIVE, INACTIVE, SUSPENDED, DELETED
}

// === GenerationType strategies ===
// IDENTITY — DB auto-increment (MySQL, PostgreSQL SERIAL)
@GeneratedValue(strategy = GenerationType.IDENTITY)

// SEQUENCE — DB sequence (PostgreSQL preferred for batch inserts)
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
@SequenceGenerator(name = "user_seq", sequenceName = "user_sequence", allocationSize = 50)

// UUID
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;

// === Embedded types ===
@Embeddable
public class Address {
    private String street;
    private String city;
    private String zipCode;
    private String country;
}

@Entity
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
            @AttributeOverride(name = "city", column = @Column(name = "billing_city"))
    })
    private Address billingAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "shipping_street")),
            @AttributeOverride(name = "city", column = @Column(name = "shipping_city"))
    })
    private Address shippingAddress;
}

// === Auditing ===
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}

// Enable auditing
@Configuration
@EnableJpaAuditing
public class JpaConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName);
    }
}
```

---

## Dry Run

**Scenario**: Save, modify, and flush an entity

```java
// Code:
@Transactional
public void updateUserEmail(Long userId, String newEmail) {
    User user = userRepository.findById(userId).orElseThrow();  // Step 1
    user.setEmail(newEmail);  // Step 2
    // No explicit save() needed! — Step 3 happens automatically
}
```

```
Step 1: findById(1)
  → SQL: SELECT * FROM users WHERE id = 1
  → User loaded, snapshot stored in persistence context
  → Snapshot: {name="Alice", email="old@email.com"}

Step 2: user.setEmail("new@email.com")
  → Java object modified in memory
  → No SQL yet

Step 3: Transaction commits (method ends)
  → Flush triggered
  → Dirty checking: compare current vs snapshot
    - name: "Alice" == "Alice" → no change
    - email: "new@email.com" != "old@email.com" → CHANGED
  → SQL: UPDATE users SET email='new@email.com', updated_at=NOW() WHERE id=1
  → Commit
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| findById (cache hit) | O(1) — persistence context lookup |
| findById (cache miss) | O(log n) — DB index scan |
| Dirty checking | O(m) — m = managed entities × fields |
| Flush (batch) | O(k) — k = dirty entities |
| persist() | O(1) — adds to context |
| merge() | O(1) — copies state |

**Performance implications:**
- Loading 10,000 entities in one transaction = 10,000 dirty checks at flush
- Use `@Modifying` queries for bulk updates instead

---

## Real Project Usage

```java
// E-commerce Product entity
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.DRAFT;

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();

    @Version
    private Long version; // Optimistic locking for concurrent updates

    public void decrementStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new InsufficientStockException(this.sku, quantity, this.stockQuantity);
        }
        this.stockQuantity -= quantity;
    }
}
```

---

## Interview Questions

1. **What is the difference between JPA, Hibernate, and Spring Data JPA?**
   - JPA: Specification/standard (jakarta.persistence API). Hibernate: JPA implementation (provides the actual ORM engine). Spring Data JPA: Abstraction on top of JPA/Hibernate — adds repositories, query derivation, pagination.

2. **Explain the entity lifecycle states.**
   - Transient: New object, not managed. Managed/Persistent: Attached to persistence context, changes auto-tracked. Detached: Was managed, now disconnected (after tx closes). Removed: Marked for deletion.

3. **What is dirty checking? How does it work?**
   - Hibernate compares entity's current state with its snapshot (taken when loaded). At flush time, if fields changed → generates UPDATE SQL automatically. No explicit save() needed for managed entities.

4. **What is the persistence context? What is the first-level cache?**
   - Persistence context = container of managed entities within a session/transaction. First-level cache = the persistence context itself. Same entity loaded twice → returns same object instance (identity guarantee).

5. **What is the difference between persist() and merge()?**
   - persist(): Makes transient entity managed (INSERT). Entity must be new. merge(): Copies state of detached entity to a managed copy (UPDATE or INSERT). Returns the managed copy (not the original).

6. **What is the difference between GenerationType.IDENTITY and SEQUENCE?**
   - IDENTITY: DB auto-increment, requires INSERT to get ID (no batching). SEQUENCE: DB sequence, can pre-allocate IDs (allocationSize), enables batch inserts. SEQUENCE preferred for performance.

7. **What is optimistic locking? How does @Version work?**
   - @Version field (int/Long/Timestamp) auto-incremented on each UPDATE. If version in DB != version in entity → OptimisticLockException (concurrent modification detected). No DB locks held.

8. **What is the difference between @Column(nullable=false) and @NotNull?**
   - @Column(nullable=false): DDL constraint (DB level), only for schema generation. @NotNull: Bean Validation (Java level), validated before DB call. Use both: @NotNull catches early, @Column ensures DB integrity.

9. **When is flush called automatically?**
   - Before JPQL/native queries (to ensure consistent results), at transaction commit, when explicitly called. Auto-flush mode ensures queries see uncommitted changes in the same persistence context.

10. **What happens when you access a lazy-loaded field outside a transaction?**
    - LazyInitializationException — the session is closed, proxy can't load data. Solutions: JOIN FETCH in query, @EntityGraph, @Transactional on calling method, or DTO projection (avoid loading entity at all).

---

## Follow-up Questions

1. **After Q3**: "How do you avoid performance issues with dirty checking on large result sets?"
   → Use read-only transactions (`@Transactional(readOnly = true)`), projections, or clear the persistence context.

2. **After Q6**: "Why is SEQUENCE preferred for batch inserts?"
   → IDENTITY requires a round-trip per insert (to get generated ID). SEQUENCE can pre-allocate IDs in batches.

3. **After Q10**: "What is LazyInitializationException?"
   → Accessing an unloaded lazy association after the session is closed. Solutions: JOIN FETCH, Entity Graphs, @Transactional scope.

4. **After Q7**: "What happens on OptimisticLockException?"
   → Spring throws OptimisticLockingFailureException. The operation should be retried with fresh data.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| No @Transactional on update operations | Changes not flushed | Ensure transaction boundaries |
| Using IDENTITY with batch inserts | Forces one-by-one inserts | Use SEQUENCE with allocationSize |
| Not using @Version | Lost updates in concurrent access | Add optimistic locking |
| Entity as API response | Exposes DB structure, lazy-load issues | Use DTOs |
| @Enumerated(EnumType.ORDINAL) | Breaks if enum order changes | Use EnumType.STRING |
| Missing no-arg constructor | JPA can't instantiate | Add protected no-arg constructor |
| Mutable entity in cache | Shared state corruption | Use DTOs or defensive copies |

---

## Best Practices

1. **Use DTOs for API layer** — entities stay in service/repository layer
2. **@Enumerated(EnumType.STRING)** — survives enum refactoring
3. **@Version for optimistic locking** — prevent lost updates
4. **Base entity class** with auditing fields (createdAt, updatedAt, createdBy)
5. **GenerationType.SEQUENCE** for PostgreSQL — enables batch inserts
6. **Indexes on frequently queried columns** — @Table(indexes = ...)
7. **Read-only transactions** for queries — skips dirty checking
8. **Limit persistence context size** — don't load thousands of entities
9. **Named entity graphs** for controlling fetch strategy per use case
10. **Flyway/Liquibase** for schema management — never use ddl-auto in prod

---

## Production Considerations

- **ddl-auto=validate** in production — never create/update
- **Schema migrations**: Flyway or Liquibase for versioned migrations
- **Connection pool**: HikariCP tuning (max-pool-size based on load)
- **Query performance**: Monitor slow queries, add indexes
- **N+1 detection**: Use `spring.jpa.show-sql=true` in dev, Hibernate statistics
- **Batch processing**: Configure `spring.jpa.properties.hibernate.jdbc.batch_size=50`
- **Second-level cache**: Consider for read-heavy, rarely-changing data
- **Database-specific features**: Use native queries when JPA abstractions are inefficient

---

## Related Topics

- → [10. Entity Relationships](#) (OneToMany, ManyToMany)
- → [11. Spring Data JPA Repository](#) (query methods)
- → [12. Transactions](#) (@Transactional, propagation)
- → [13. JdbcTemplate](#) (alternative for complex queries)
- → [14. Database Connection Pool](#) (HikariCP)
