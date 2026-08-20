# Topic 19: JPA, Hibernate & Spring Data JPA

## Theory — Extremely Important for Java/Spring Boot

### JPA Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   Application Code                            │
├─────────────────────────────────────────────────────────────┤
│                Spring Data JPA                               │
│          (Repository abstraction)                            │
├─────────────────────────────────────────────────────────────┤
│                    JPA API                                    │
│          (EntityManager, Criteria)                           │
├─────────────────────────────────────────────────────────────┤
│                  Hibernate                                    │
│    (JPA Implementation / ORM engine)                         │
├─────────────────────────────────────────────────────────────┤
│                   JDBC                                        │
│         (Database connectivity)                              │
├─────────────────────────────────────────────────────────────┤
│                 PostgreSQL                                    │
└─────────────────────────────────────────────────────────────┘
```

### Entity Lifecycle

```
┌──────────────────────────────────────────────────────────────┐
│                  ENTITY LIFECYCLE                              │
├──────────────────────────────────────────────────────────────┤
│                                                                │
│   new()         persist()        detach()/close()            │
│     │              │                    │                     │
│     ▼              ▼                    ▼                     │
│  TRANSIENT ──→ MANAGED ──────────→ DETACHED                  │
│                  │  ▲                   │                     │
│                  │  │   merge()         │                     │
│                  │  └───────────────────┘                     │
│                  │                                            │
│                  │ remove()                                   │
│                  ▼                                            │
│               REMOVED                                         │
│                                                                │
│  TRANSIENT: New object, not tracked by persistence context   │
│  MANAGED: Tracked, changes auto-detected (dirty checking)    │
│  DETACHED: Was managed, session closed, changes not tracked  │
│  REMOVED: Scheduled for deletion                             │
│                                                                │
└──────────────────────────────────────────────────────────────┘
```

### Persistence Context

The persistence context is a first-level cache that:
1. Tracks all managed entities
2. Ensures identity (same ID = same Java object reference)
3. Dirty checking (detects changes at flush time)
4. Write-behind (batches SQL operations)
5. Guarantees repeatable reads within a transaction

---

## Internal Working

### How Hibernate Executes a Query

```
repository.findById(42):
┌──────────────────────────────────────────────────────────────┐
│ 1. Check first-level cache (persistence context)             │
│    → If found, return cached entity (NO SQL!)               │
│                                                               │
│ 2. If not in cache, generate SQL:                            │
│    SELECT * FROM products WHERE id = ?                       │
│                                                               │
│ 3. Execute via JDBC PreparedStatement                        │
│                                                               │
│ 4. Map ResultSet to Entity object                            │
│                                                               │
│ 5. Put entity in persistence context (cache it)              │
│                                                               │
│ 6. Return entity to caller                                   │
└──────────────────────────────────────────────────────────────┘

Dirty Checking (on flush/commit):
┌──────────────────────────────────────────────────────────────┐
│ 1. For each managed entity:                                   │
│    - Compare current state with snapshot taken at load time   │
│    - If different → generate UPDATE SQL                      │
│                                                               │
│ 2. Batch all generated SQL                                   │
│                                                               │
│ 3. Execute in order: INSERT → UPDATE → DELETE                │
│                                                               │
│ 4. Clear dirty flags                                         │
└──────────────────────────────────────────────────────────────┘
```

### N+1 Problem

```
// Code:
List<Order> orders = orderRepository.findAll();  // 1 query: SELECT * FROM orders
for (Order order : orders) {
    System.out.println(order.getCustomer().getName());  // N queries!
    // Each .getCustomer() triggers: SELECT * FROM customers WHERE id = ?
}

// If 100 orders → 101 queries total (1 + 100)!
// This is the N+1 problem

Solution 1: Fetch Join
@Query("SELECT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomer();  // 1 query with JOIN

Solution 2: Entity Graph
@EntityGraph(attributePaths = {"customer"})
List<Order> findAll();

Solution 3: Batch fetching
@BatchSize(size = 25)  // Fetches 25 customers at a time
private Customer customer;
// Reduces to: 1 + ceil(100/25) = 5 queries
```

---

## Code Examples

### Entity Mapping

```java
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_category", columnList = "category_id")
})
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String sku;
    
    @Column(nullable = false, length = 300)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;
    
    @Column(nullable = false)
    private Integer stock = 0;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();
    
    @Column(name = "is_active")
    private Boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version  // Optimistic locking
    private Integer version;
}
```

### Relationships

```java
// One-to-Many / Many-to-One (bidirectional)
@Entity
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    
    // Helper method for bidirectional consistency
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}

@Entity
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    private Integer quantity;
    private BigDecimal unitPrice;
}

// Many-to-Many
@Entity
public class Student {
    @ManyToMany
    @JoinTable(
        name = "enrollment",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> courses = new HashSet<>();
}
```

### Cascade Types

```java
// CascadeType options:
// PERSIST - Save parent → saves children
// MERGE - Update parent → updates children
// REMOVE - Delete parent → deletes children
// REFRESH - Refresh parent → refreshes children
// DETACH - Detach parent → detaches children
// ALL - All of the above

@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items;

// orphanRemoval = true: removing item from list deletes it from DB
order.getItems().remove(item);  // Generates DELETE SQL on flush
```

### Fetching Strategies

```java
// LAZY (default for collections): Loaded on first access
@OneToMany(fetch = FetchType.LAZY)  // Default
private List<OrderItem> items;

// EAGER: Loaded immediately with parent
@ManyToOne(fetch = FetchType.EAGER)  // Load with every query
private Category category;

// BEST PRACTICE: Always use LAZY, fetch explicitly when needed

// Fetch Join in JPQL
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.customer " +
       "JOIN FETCH o.items i " +
       "JOIN FETCH i.product " +
       "WHERE o.id = :id")
Optional<Order> findByIdWithDetails(@Param("id") Long id);

// Entity Graph
@EntityGraph(attributePaths = {"customer", "items", "items.product"})
Optional<Order> findById(Long id);

// Named Entity Graph
@Entity
@NamedEntityGraph(
    name = "Order.withDetails",
    attributeNodes = {
        @NamedAttributeNode("customer"),
        @NamedAttributeNode(value = "items", subgraph = "items.product")
    },
    subgraphs = @NamedSubgraph(name = "items.product", attributeNodes = @NamedAttributeNode("product"))
)
public class Order { ... }
```

---

## Spring Data JPA Repository

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Derived query methods (auto-generated SQL)
    List<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status);
    List<Order> findByCreatedAtBetween(Instant start, Instant end);
    Optional<Order> findByOrderNumber(String orderNumber);
    long countByStatus(OrderStatus status);
    boolean existsByCustomerIdAndStatus(Long customerId, OrderStatus status);
    
    // JPQL
    @Query("SELECT o FROM Order o WHERE o.customer.id = :customerId " +
           "AND o.totalAmount > :minAmount ORDER BY o.createdAt DESC")
    List<Order> findLargeOrders(@Param("customerId") Long customerId, 
                                @Param("minAmount") BigDecimal minAmount);
    
    // Native SQL
    @Query(value = "SELECT * FROM orders WHERE customer_id = :customerId " +
                   "ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Order> findLatestOrder(@Param("customerId") Long customerId);
    
    // Modifying queries
    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);
    
    // Projections (DTO)
    @Query("SELECT new com.app.dto.OrderSummary(o.id, o.orderNumber, o.totalAmount, o.status) " +
           "FROM Order o WHERE o.customer.id = :customerId")
    Page<OrderSummary> findOrderSummaries(@Param("customerId") Long customerId, Pageable pageable);
    
    // Specifications (dynamic queries)
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);
    
    // Pessimistic lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
```

### Specifications (Dynamic Queries)

```java
public class OrderSpecification {
    
    public static Specification<Order> hasCustomer(Long customerId) {
        return (root, query, cb) -> 
            customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }
    
    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> 
            status == null ? null : cb.equal(root.get("status"), status);
    }
    
    public static Specification<Order> createdAfter(Instant date) {
        return (root, query, cb) -> 
            date == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }
    
    public static Specification<Order> totalAmountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            if (min != null && max != null) return cb.between(root.get("totalAmount"), min, max);
            if (min != null) return cb.greaterThanOrEqualTo(root.get("totalAmount"), min);
            return cb.lessThanOrEqualTo(root.get("totalAmount"), max);
        };
    }
}

// Usage
Specification<Order> spec = Specification
    .where(OrderSpecification.hasCustomer(customerId))
    .and(OrderSpecification.hasStatus(OrderStatus.COMPLETED))
    .and(OrderSpecification.createdAfter(startDate));

Page<Order> results = orderRepository.findAll(spec, pageable);
```

---

## JPA Performance

### Solving N+1

```java
// Problem: N+1 queries
List<Order> orders = orderRepository.findAll(); // 1 query
orders.forEach(o -> o.getCustomer().getName()); // N queries!

// Solution 1: JOIN FETCH (best for single association)
@Query("SELECT o FROM Order o JOIN FETCH o.customer")
List<Order> findAllWithCustomer();

// Solution 2: @EntityGraph (declarative)
@EntityGraph(attributePaths = {"customer", "items"})
List<Order> findByStatus(OrderStatus status);

// Solution 3: @BatchSize (on entity, reduces to N/batch queries)
@Entity
public class Customer {
    @BatchSize(size = 50)  // Fetch 50 at a time
    @OneToMany(mappedBy = "customer")
    private List<Order> orders;
}

// Solution 4: DTO Projection (best performance, no entity overhead)
@Query("SELECT new com.app.dto.OrderDTO(o.id, o.orderNumber, c.name, o.totalAmount) " +
       "FROM Order o JOIN o.customer c WHERE o.status = :status")
List<OrderDTO> findDTOsByStatus(@Param("status") OrderStatus status);
```

### Bulk Operations

```java
// BAD: Loading all entities into memory
List<Product> products = productRepository.findByCategoryId(5);
products.forEach(p -> p.setPrice(p.getPrice().multiply(BigDecimal.valueOf(1.1))));
productRepository.saveAll(products);
// Generates N SELECT + N UPDATE statements!

// GOOD: Bulk update (single SQL statement)
@Modifying
@Query("UPDATE Product p SET p.price = p.price * 1.1 WHERE p.category.id = :catId")
int updatePricesByCategory(@Param("catId") Long categoryId);
// Single UPDATE statement, no entity loading!

// GOOD: JDBC batch insert for large datasets
@Autowired
private JdbcTemplate jdbcTemplate;

public void bulkInsert(List<Product> products) {
    String sql = "INSERT INTO products (sku, name, price) VALUES (?, ?, ?)";
    jdbcTemplate.batchUpdate(sql, products, 1000, (ps, product) -> {
        ps.setString(1, product.getSku());
        ps.setString(2, product.getName());
        ps.setBigDecimal(3, product.getPrice());
    });
}
```

---

## Spring Transactions

```java
@Service
public class OrderService {
    
    // Default: REQUIRED propagation, READ_COMMITTED isolation
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // All operations in single transaction
        Order order = new Order();
        order.setCustomer(customerRepository.findById(request.getCustomerId())
            .orElseThrow(() -> new NotFoundException("Customer not found")));
        
        for (var item : request.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found"));
            
            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException(product.getId());
            }
            product.setStock(product.getStock() - item.getQuantity());
            order.addItem(new OrderItem(product, item.getQuantity(), product.getPrice()));
        }
        
        return orderRepository.save(order);
    }
    
    // Read-only: No dirty checking, can route to replica
    @Transactional(readOnly = true)
    public Page<OrderDTO> getOrders(Long customerId, Pageable pageable) {
        return orderRepository.findOrderSummaries(customerId, pageable);
    }
    
    // Independent transaction (committed even if caller rolls back)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(Long orderId, String event) {
        auditRepository.save(new AuditLog(orderId, event, Instant.now()));
    }
    
    // Custom rollback rules
    @Transactional(
        rollbackFor = BusinessException.class,    // Rollback on this checked exception
        noRollbackFor = WarningException.class    // Don't rollback on this
    )
    public void processPayment(Long orderId) { ... }
}
```

### Transaction Propagation

```
REQUIRED (default): Use existing txn or create new one
REQUIRES_NEW:       Always create new txn (suspend existing)
SUPPORTS:           Use existing txn if present, else non-transactional
MANDATORY:          Must have existing txn (throw if none)
NOT_SUPPORTED:      Execute non-transactionally (suspend existing)
NEVER:              Throw if transaction exists
NESTED:             Nested transaction (savepoint within outer)
```

### Self-Invocation Problem

```java
@Service
public class OrderService {
    
    @Transactional
    public void processOrder(Long orderId) {
        // This is the OUTER method with @Transactional
        updateInventory(orderId);  // ← DIRECT CALL, NOT THROUGH PROXY!
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInventory(Long orderId) {
        // This annotation is IGNORED because it's a self-call
        // It uses the OUTER transaction, not a new one!
    }
    
    // SOLUTION 1: Inject self
    @Autowired @Lazy
    private OrderService self;
    
    public void processOrder(Long orderId) {
        self.updateInventory(orderId);  // Goes through proxy!
    }
    
    // SOLUTION 2: Separate service
    @Autowired
    private InventoryService inventoryService;
    
    public void processOrder(Long orderId) {
        inventoryService.updateInventory(orderId);  // Different bean = works
    }
}
```

---

## Interview Questions & Answers

**Q1: Explain the N+1 problem and how to solve it.**

N+1 happens when loading N entities triggers N additional queries for their associations. If you load 100 orders, each order.getCustomer() fires a separate SELECT.

Solutions:
1. **JOIN FETCH** in JPQL (single query with JOIN)
2. **@EntityGraph** (declarative eager fetch for specific query)
3. **@BatchSize** (fetches associations in batches)
4. **DTO projection** (skip entities entirely)

**Q2: What's the difference between LAZY and EAGER fetching? Which should you use?**

- LAZY: Association loaded only when accessed (default for collections)
- EAGER: Association loaded immediately with parent entity

**Always use LAZY.** Eager loading causes:
- Unnecessary data fetching
- Cannot be overridden to lazy per-query
- Potential Cartesian product with multiple eager collections

**Q3: What happens when you access a LAZY field outside a transaction?**

`LazyInitializationException` — the persistence context (session) is closed, Hibernate can't load the data.

Solutions:
1. Ensure transaction spans the code that accesses lazy fields
2. Use `@Transactional` on the service method
3. Fetch eagerly for that specific query (JOIN FETCH)
4. Use DTO projections
5. Open Session in View (anti-pattern, avoid in production)

**Q4: Explain optimistic vs pessimistic locking in JPA.**

```java
// Optimistic: @Version field, no locks held, checks at commit time
@Version
private Integer version;
// UPDATE ... WHERE id=? AND version=?
// If 0 rows affected → OptimisticLockException (someone else changed it)

// Pessimistic: Acquires DB-level lock, blocks other transactions
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Product findForUpdate(Long id);
// SELECT ... FOR UPDATE
// Other transactions wait until lock released
```

**Q5: What is the persistence context and first-level cache?**

The persistence context:
1. Lives for the duration of a transaction (or session)
2. Caches loaded entities (first-level cache)
3. Guarantees identity: `findById(1) == findById(1)` (same object)
4. Tracks changes (dirty checking) — no explicit save needed for managed entities
5. Flushes changes to DB on commit/flush

---

## Common Mistakes

1. **Not using LAZY fetching** — causes unnecessary data loading
2. **Ignoring N+1** — check Hibernate SQL log in development
3. **Self-invocation with @Transactional** — proxy is bypassed
4. **Open Session in View** — masks lazy loading issues, causes N+1 in views
5. **Loading entities for bulk operations** — use @Modifying queries instead
6. **Not using @Version** — lost updates in concurrent scenarios
7. **Bidirectional relationship without helper methods** — inconsistent state

---

## Best Practices

1. **Always use FetchType.LAZY** — override with JOIN FETCH when needed
2. **Use DTO projections** for read-only queries (skip entity overhead)
3. **Enable SQL logging in dev** to catch N+1 early
4. **Use @Version for optimistic locking** in entities that may be concurrently updated
5. **Keep transactions short** — don't hold database connections in controller layer
6. **Use @Transactional(readOnly = true)** for read operations (optimization hints)
7. **Prefer JPQL @Query over derived methods** for complex queries
8. **Use Specifications** for dynamic/filter queries instead of string concatenation

---

## Production Considerations

1. **Hibernate statistics**: Enable to monitor cache hits, query counts
2. **HikariCP pool sizing**: connections = (core_count * 2) + disk_spindles (typically 10-20)
3. **Second-level cache** (Ehcache/Redis): For read-heavy, rarely-changing data
4. **Batch size configuration**:
   ```yaml
   spring.jpa.properties.hibernate.default_batch_fetch_size: 25
   spring.jpa.properties.hibernate.jdbc.batch_size: 50
   spring.jpa.properties.hibernate.order_inserts: true
   spring.jpa.properties.hibernate.order_updates: true
   ```
5. **Slow query detection**:
   ```yaml
   spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 100
   ```

---

## Related Topics
- [Topic 15: Transactions, ACID, Isolation](#)
- [Topic 14: Indexes (for query optimization)](#)
- [Topic 20: Database Performance](#)
