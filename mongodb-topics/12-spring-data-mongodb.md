# Spring Data MongoDB ⭐⭐⭐

## Overview

Spring Data MongoDB provides a familiar Spring-based programming model for MongoDB, including repository abstractions, template-based access, and annotation-driven mapping.

```
Controller
    ↓
Service
    ↓
┌─────────────────────────────────────────────┐
│         Data Access Layer                    │
│                                             │
│  ┌──────────────────┐  ┌────────────────┐  │
│  │ MongoRepository   │  │ MongoTemplate  │  │
│  │ (Simple CRUD)     │  │ (Complex ops)  │  │
│  └────────┬─────────┘  └───────┬────────┘  │
│           └──────────┬──────────┘           │
│                      ↓                      │
│              MongoDB Java Driver             │
└──────────────────────┬──────────────────────┘
                       ↓
                    MongoDB
```

---

## Configuration

### Dependencies (pom.xml)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

### application.yml
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb
      # OR individual properties:
      host: localhost
      port: 27017
      database: mydb
      username: admin
      password: secret
      authentication-database: admin

# For replica set:
spring:
  data:
    mongodb:
      uri: mongodb://host1:27017,host2:27017,host3:27017/mydb?replicaSet=myRS&readPreference=secondaryPreferred
```

---

## Document Mapping Annotations

### @Document
```java
@Document(collection = "orders")  // Maps to 'orders' collection
public class Order {
    
    @Id
    private String id;  // Maps to _id in MongoDB
    
    @Field("order_number")  // Custom field name in MongoDB
    private String orderNumber;
    
    private String customerId;
    
    @Field("order_date")
    private LocalDateTime orderDate;
    
    private OrderStatus status;
    
    private List<OrderItem> items;  // Embedded list
    
    private Address shippingAddress;  // Embedded document
    
    @DBRef
    private Customer customer;  // Reference to another collection
    
    private BigDecimal total;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;  // Optimistic locking
    
    @Transient  // Not persisted to MongoDB
    private String tempCalculation;
    
    @Indexed(unique = true)
    private String email;
    
    @CompoundIndex(name = "customer_date_idx", 
                   def = "{'customerId': 1, 'orderDate': -1}")
    // Compound index defined at class level
}
```

### Key Annotations

| Annotation | Purpose |
|-----------|---------|
| `@Document` | Marks class as MongoDB document, specifies collection |
| `@Id` | Maps to `_id` field |
| `@Field` | Custom field name in MongoDB |
| `@DBRef` | Reference to document in another collection |
| `@Indexed` | Creates an index on the field |
| `@CompoundIndex` | Creates a compound index |
| `@TextIndexed` | Includes field in text index |
| `@Transient` | Field not persisted |
| `@CreatedDate` | Auto-set on creation |
| `@LastModifiedDate` | Auto-set on update |
| `@Version` | Optimistic locking version field |

### @DBRef — When to Use (and When NOT to)
```java
// @DBRef stores a reference like:
// { "$ref": "customers", "$id": ObjectId("...") }

@DBRef
private Customer customer;  // Loaded lazily/eagerly from customers collection

// ⚠️ AVOID @DBRef in most cases!
// Better: Store just the ID and look up manually or use $lookup
private String customerId;

// Why avoid @DBRef?
// 1. Cannot be used in aggregation pipelines
// 2. Eager loading causes N+1 query problems
// 3. Not supported in all MongoDB operations
// 4. Manual ID reference is more flexible
```

---

## MongoRepository

### Basic Repository
```java
public interface OrderRepository extends MongoRepository<Order, String> {
    // Spring Data derives queries from method names
}

// Inherited methods from MongoRepository:
// save(entity) / saveAll(entities)
// findById(id) / findAll()
// existsById(id) / count()
// deleteById(id) / delete(entity) / deleteAll()
```

### Derived Query Methods
```java
public interface OrderRepository extends MongoRepository<Order, String> {
    
    // Simple field match
    List<Order> findByCustomerId(String customerId);
    
    // Multiple conditions (AND)
    List<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status);
    
    // OR condition
    List<Order> findByStatusOrTotal(OrderStatus status, BigDecimal total);
    
    // Comparison operators
    List<Order> findByTotalGreaterThan(BigDecimal amount);
    List<Order> findByTotalBetween(BigDecimal min, BigDecimal max);
    List<Order> findByOrderDateAfter(LocalDateTime date);
    List<Order> findByOrderDateBefore(LocalDateTime date);
    
    // String queries
    List<Order> findByCustomerNameContaining(String name);
    List<Order> findByCustomerNameStartingWith(String prefix);
    List<Order> findByEmailRegex(String pattern);
    
    // Null checks
    List<Order> findByDeletedAtIsNull();
    List<Order> findByDeletedAtIsNotNull();
    
    // Collection/Array queries
    List<Order> findByItemsProductId(String productId);
    List<Order> findByTagsIn(List<String> tags);
    
    // Sorting
    List<Order> findByCustomerIdOrderByOrderDateDesc(String customerId);
    
    // Pagination
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    
    // Exists
    boolean existsByEmail(String email);
    
    // Count
    long countByStatus(OrderStatus status);
    
    // Delete
    void deleteByStatusAndOrderDateBefore(OrderStatus status, LocalDateTime date);
    
    // Top/First
    Optional<Order> findFirstByCustomerIdOrderByOrderDateDesc(String customerId);
    List<Order> findTop10ByStatusOrderByTotalDesc(OrderStatus status);
}
```

### @Query Annotation
```java
public interface OrderRepository extends MongoRepository<Order, String> {
    
    // Custom JSON query
    @Query("{ 'customerId': ?0, 'status': ?1 }")
    List<Order> findCustomerOrders(String customerId, OrderStatus status);
    
    // With projection
    @Query(value = "{ 'customerId': ?0 }", fields = "{ 'orderNumber': 1, 'total': 1 }")
    List<Order> findOrderSummaries(String customerId);
    
    // Regex
    @Query("{ 'customerName': { $regex: ?0, $options: 'i' } }")
    List<Order> searchByName(String namePattern);
    
    // Date range
    @Query("{ 'orderDate': { $gte: ?0, $lte: ?1 } }")
    List<Order> findByDateRange(LocalDateTime start, LocalDateTime end);
    
    // Array query
    @Query("{ 'items.productId': ?0 }")
    List<Order> findOrdersContainingProduct(String productId);
    
    // Nested field
    @Query("{ 'shippingAddress.city': ?0 }")
    List<Order> findByShippingCity(String city);
    
    // Complex query with $or
    @Query("{ $or: [ { 'status': 'ACTIVE' }, { 'total': { $gt: ?0 } } ] }")
    List<Order> findActiveOrHighValue(BigDecimal minTotal);
    
    // Update query
    @Query("{ '_id': ?0 }")
    @Update("{ $set: { 'status': ?1, 'updatedAt': ?2 } }")
    void updateStatus(String id, OrderStatus status, LocalDateTime updatedAt);
    
    // Delete query
    @Query(value = "{ 'status': 'CANCELLED', 'orderDate': { $lt: ?0 } }", delete = true)
    void deleteOldCancelledOrders(LocalDateTime before);
}
```

### Pagination and Sorting
```java
// In service layer:
Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "orderDate"));
Page<Order> orders = orderRepository.findByStatus(OrderStatus.ACTIVE, pageable);

// Page info:
orders.getContent();          // List of orders
orders.getTotalElements();    // Total matching documents
orders.getTotalPages();       // Total pages
orders.getNumber();           // Current page number
orders.getSize();             // Page size
orders.hasNext();             // Has next page
```

---

## MongoTemplate

For complex operations that derived queries can't handle.

### Query with Criteria
```java
@Service
public class OrderService {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    public List<Order> findOrders(String customerId, OrderStatus status, 
                                   LocalDateTime fromDate, BigDecimal minTotal) {
        Query query = new Query();
        
        // Build criteria dynamically
        query.addCriteria(Criteria.where("customerId").is(customerId));
        
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (fromDate != null) {
            query.addCriteria(Criteria.where("orderDate").gte(fromDate));
        }
        if (minTotal != null) {
            query.addCriteria(Criteria.where("total").gt(minTotal));
        }
        
        // Sort and pagination
        query.with(Sort.by(Sort.Direction.DESC, "orderDate"));
        query.limit(50);
        query.skip(0);
        
        // Projection
        query.fields().include("orderNumber", "total", "status", "orderDate");
        
        return mongoTemplate.find(query, Order.class);
    }
}
```

### Criteria API
```java
// Equals
Criteria.where("status").is("active")

// Comparison
Criteria.where("age").gt(18).lt(65)
Criteria.where("total").gte(100)

// In
Criteria.where("status").in("active", "pending")

// Regex
Criteria.where("name").regex("^Vinay", "i")

// Exists
Criteria.where("phone").exists(true)

// Nested field
Criteria.where("address.city").is("Bangalore")

// Array
Criteria.where("skills").all("Java", "Spring")
Criteria.where("items").elemMatch(
    Criteria.where("productId").is("P1").and("qty").gt(2)
)

// Logical operators
new Criteria().orOperator(
    Criteria.where("status").is("active"),
    Criteria.where("total").gt(1000)
)

new Criteria().andOperator(
    Criteria.where("age").gte(18),
    Criteria.where("age").lte(65)
)
```

### Update Operations
```java
// Update single document
Query query = Query.query(Criteria.where("_id").is(orderId));
Update update = new Update()
    .set("status", OrderStatus.SHIPPED)
    .set("updatedAt", LocalDateTime.now())
    .inc("version", 1);

UpdateResult result = mongoTemplate.updateFirst(query, update, Order.class);
// result.getModifiedCount()

// Update multiple documents
mongoTemplate.updateMulti(
    Query.query(Criteria.where("status").is("PENDING")
        .and("orderDate").lt(cutoffDate)),
    new Update().set("status", "CANCELLED"),
    Order.class
);

// Upsert
mongoTemplate.upsert(query, update, Order.class);

// Push to array
Update pushUpdate = new Update().push("items", newItem);
mongoTemplate.updateFirst(query, pushUpdate, Order.class);

// Pull from array
Update pullUpdate = new Update().pull("items", 
    Query.query(Criteria.where("productId").is("P1")));
mongoTemplate.updateFirst(query, pullUpdate, Order.class);

// AddToSet
Update addToSet = new Update().addToSet("tags", "priority");
mongoTemplate.updateFirst(query, addToSet, Order.class);
```

### Find and Modify (Atomic)
```java
// Atomically find and update, returning the new version
Order updated = mongoTemplate.findAndModify(
    Query.query(Criteria.where("_id").is(orderId)),
    new Update().set("status", "PROCESSING"),
    FindAndModifyOptions.options().returnNew(true),
    Order.class
);
```

### Aggregation with MongoTemplate
```java
// Monthly revenue report
Aggregation aggregation = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("status").is("COMPLETED")),
    Aggregation.group("customerId")
        .sum("total").as("totalSpent")
        .count().as("orderCount")
        .avg("total").as("avgOrder"),
    Aggregation.sort(Sort.Direction.DESC, "totalSpent"),
    Aggregation.limit(10)
);

AggregationResults<CustomerSpending> results = 
    mongoTemplate.aggregate(aggregation, "orders", CustomerSpending.class);

List<CustomerSpending> topCustomers = results.getMappedResults();

// More complex aggregation
Aggregation pipeline = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("orderDate")
        .gte(startDate).lte(endDate)),
    Aggregation.unwind("items"),
    Aggregation.group("items.category")
        .sum(ArithmeticOperators.Multiply.valueOf("items.price")
            .multiplyBy("items.quantity")).as("revenue")
        .count().as("itemCount"),
    Aggregation.project()
        .and("_id").as("category")
        .andInclude("revenue", "itemCount"),
    Aggregation.sort(Sort.Direction.DESC, "revenue")
);

AggregationResults<CategoryRevenue> categoryResults =
    mongoTemplate.aggregate(pipeline, "orders", CategoryRevenue.class);
```

### $lookup with MongoTemplate
```java
Aggregation aggregation = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("customerId").is(customerId)),
    Aggregation.lookup("customers", "customerId", "_id", "customerInfo"),
    Aggregation.unwind("customerInfo"),
    Aggregation.project()
        .andInclude("orderNumber", "total", "status")
        .and("customerInfo.name").as("customerName")
        .and("customerInfo.email").as("customerEmail")
);
```

---

## Custom Repository Implementation

```java
// Interface for custom methods
public interface OrderRepositoryCustom {
    List<Order> searchOrders(OrderSearchCriteria criteria);
    Page<Order> findOrdersWithPagination(OrderFilter filter, Pageable pageable);
}

// Implementation
public class OrderRepositoryImpl implements OrderRepositoryCustom {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Override
    public List<Order> searchOrders(OrderSearchCriteria criteria) {
        Query query = new Query();
        
        if (criteria.getCustomerId() != null) {
            query.addCriteria(Criteria.where("customerId").is(criteria.getCustomerId()));
        }
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) {
            query.addCriteria(Criteria.where("status").in(criteria.getStatuses()));
        }
        if (criteria.getMinTotal() != null) {
            query.addCriteria(Criteria.where("total").gte(criteria.getMinTotal()));
        }
        if (criteria.getFromDate() != null) {
            query.addCriteria(Criteria.where("orderDate").gte(criteria.getFromDate()));
        }
        
        query.with(Sort.by(Sort.Direction.DESC, "orderDate"));
        return mongoTemplate.find(query, Order.class);
    }
    
    @Override
    public Page<Order> findOrdersWithPagination(OrderFilter filter, Pageable pageable) {
        Query query = buildQuery(filter);
        
        long total = mongoTemplate.count(query, Order.class);
        
        query.with(pageable);
        List<Order> orders = mongoTemplate.find(query, Order.class);
        
        return new PageImpl<>(orders, pageable, total);
    }
}

// Main repository extends both
public interface OrderRepository extends MongoRepository<Order, String>, OrderRepositoryCustom {
    // Derived query methods here
}
```

---

## Transactions in Spring Data MongoDB

```java
@Configuration
public class MongoConfig {
    
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}

@Service
public class TransferService {
    
    @Transactional  // Uses MongoTransactionManager
    public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        Account from = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        
        accountRepository.debit(fromAccountId, amount);
        accountRepository.credit(toAccountId, amount);
        
        transferRepository.save(new Transfer(fromAccountId, toAccountId, amount));
    }
}
```

---

## MongoRepository vs MongoTemplate

| Aspect | MongoRepository | MongoTemplate |
|--------|----------------|---------------|
| Simple CRUD | ✅ Perfect | Overkill |
| Derived queries | ✅ Method names | Manual criteria |
| Dynamic queries | ❌ Not flexible | ✅ Build dynamically |
| Aggregation | Limited @Aggregation | ✅ Full pipeline |
| Bulk operations | Limited | ✅ Full control |
| Complex updates | Limited | ✅ All update operators |
| Learning curve | Low | Higher |
| Code verbosity | Minimal | More verbose |

### When to Use What
- **MongoRepository**: Standard CRUD, simple queries, pagination
- **MongoTemplate**: Complex dynamic queries, aggregations, bulk operations, atomic find-and-modify

---

## Interview Questions

**Q: How does Spring Data MongoDB handle the _id field?**
A: If you use `@Id` on a String field, Spring converts between String and ObjectId automatically. If the field is null on insert, MongoDB generates an ObjectId. You can also use `ObjectId` type directly.

**Q: What's the problem with @DBRef?**
A: @DBRef creates tight coupling between collections, doesn't work with aggregation pipelines, can cause N+1 query problems (each reference triggers a separate query), and is generally unnecessary. Better to store the raw ID and do lookups manually or via $lookup in aggregation.

**Q: How do you implement optimistic locking?**
A: Use `@Version` on a Long/Integer field. Spring increments it on each save and throws `OptimisticLockingFailureException` if the version in the database doesn't match (concurrent modification detected).

**Q: How do you build dynamic queries where some filters are optional?**
A: Use MongoTemplate with Criteria builder. Conditionally add criteria based on which filter parameters are non-null. This creates the exact query needed at runtime without unnecessary conditions.

**Q: How do you handle pagination efficiently?**
A: For offset-based: use `PageRequest.of(page, size, sort)` with repository methods returning `Page<T>`. For cursor-based (better at scale): use MongoTemplate with `Criteria.where("_id").gt(lastId)` and `.limit(size)`.

**Q: What's the difference between save() and insert()?**
A: `save()` performs upsert — inserts if no _id match, updates if _id exists. `insert()` always inserts — throws DuplicateKeyException if _id already exists. Use `insert()` when you know it's a new document (slightly more efficient, clearer intent).
