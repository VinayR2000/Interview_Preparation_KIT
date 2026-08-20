# Topic 23: Spring Transactions (@Transactional)

## Theory

### What is a Transaction?

A transaction is a unit of work that either completes entirely or has no effect at all. In Spring Boot, the `@Transactional` annotation provides declarative transaction management, abstracting away the complexity of programmatic transaction handling.

### Why Spring Transaction Management?

Without Spring:
```java
Connection conn = dataSource.getConnection();
try {
    conn.setAutoCommit(false);
    // execute SQL statements
    conn.commit();
} catch (Exception e) {
    conn.rollback();
} finally {
    conn.close();
}
```

With Spring:
```java
@Transactional
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    debit(fromId, amount);
    credit(toId, amount);
}
```

Spring handles begin, commit, rollback, and resource cleanup automatically.

---

## Internal Working

### Proxy-Based Transaction Management

```
┌─────────────────────────────────────────────────────────────────┐
│                HOW @Transactional WORKS INTERNALLY               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Caller ──→ [Proxy] ──→ Actual Bean Method                      │
│                │                                                 │
│                │  1. TransactionInterceptor invoked               │
│                │  2. PlatformTransactionManager.getTransaction()  │
│                │  3. Begin transaction                            │
│                │  4. Invoke actual method                         │
│                │  5a. On success → commit()                       │
│                │  5b. On RuntimeException → rollback()            │
│                │  6. Release connection                           │
│                                                                  │
│  PROXY CREATION:                                                 │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ Spring creates proxy (JDK dynamic / CGLIB)           │       │
│  │ → Proxy wraps the target bean                        │       │
│  │ → Intercepts method calls                            │       │
│  │ → Applies transaction advice before/after            │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                  │
│  TRANSACTION SYNCHRONIZATION:                                    │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ ThreadLocal → TransactionSynchronizationManager      │       │
│  │ → Holds current transaction status                   │       │
│  │ → Binds Connection to current thread                 │       │
│  │ → All DAOs in same thread share same Connection      │       │
│  └──────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

### Transaction Manager Hierarchy

```
PlatformTransactionManager (interface)
├── DataSourceTransactionManager (JDBC)
├── JpaTransactionManager (JPA/Hibernate)
├── HibernateTransactionManager (Hibernate directly)
├── JtaTransactionManager (distributed/XA)
└── ChainedTransactionManager (multiple datasources)
```

### Spring Boot Auto-Configuration

- If `spring-boot-starter-data-jpa` is on classpath → `JpaTransactionManager` auto-configured
- If only `spring-boot-starter-jdbc` → `DataSourceTransactionManager` auto-configured
- No manual `@EnableTransactionManagement` needed (Boot enables it automatically)

---

## @Transactional Attributes

### 1. Propagation

```java
@Transactional(propagation = Propagation.REQUIRED)
```

| Propagation | Behavior |
|---|---|
| **REQUIRED** (default) | Join existing transaction, or create new one if none exists |
| **REQUIRES_NEW** | Always create a new transaction; suspend existing one |
| **SUPPORTS** | Join existing transaction if present; otherwise execute non-transactionally |
| **MANDATORY** | Must run within existing transaction; throw exception if none exists |
| **NOT_SUPPORTED** | Execute non-transactionally; suspend existing transaction if present |
| **NEVER** | Execute non-transactionally; throw exception if transaction exists |
| **NESTED** | Execute within nested transaction (savepoint) if transaction exists; otherwise create new |

#### Propagation Diagram

```
REQUIRED (default):
─────────────────────────────────────────────
Caller has TX?  YES → Join existing TX
                NO  → Create new TX

REQUIRES_NEW:
─────────────────────────────────────────────
Always → Suspend outer TX → Create NEW TX → Resume outer TX after

NESTED:
─────────────────────────────────────────────
Caller has TX?  YES → Create SAVEPOINT → Execute → Rollback to savepoint on failure
                NO  → Create new TX (same as REQUIRED)
```

### 2. Isolation Levels

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
```

| Isolation | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| READ_UNCOMMITTED | Possible | Possible | Possible |
| READ_COMMITTED | Prevented | Possible | Possible |
| REPEATABLE_READ | Prevented | Prevented | Possible |
| SERIALIZABLE | Prevented | Prevented | Prevented |
| DEFAULT | Uses DB default (PostgreSQL = READ_COMMITTED) |

### 3. Read-Only

```java
@Transactional(readOnly = true)
```

- Hints to the persistence provider that no writes will occur
- Hibernate skips dirty checking → performance gain
- Some databases route to read replicas
- Does NOT prevent writes (just a hint)

### 4. Timeout

```java
@Transactional(timeout = 30) // seconds
```

- Transaction will be rolled back if not completed within timeout
- Useful for preventing long-running queries from holding locks

### 5. Rollback Rules

```java
// Default: rollback on RuntimeException and Error, NOT on checked exceptions
@Transactional

// Rollback on specific checked exception
@Transactional(rollbackFor = BusinessException.class)

// Rollback on all exceptions
@Transactional(rollbackFor = Exception.class)

// Don't rollback on specific exception
@Transactional(noRollbackFor = EmailSendFailedException.class)
```

**Critical Rule:**
- `RuntimeException` (unchecked) → triggers rollback by default
- `Exception` (checked) → does NOT trigger rollback by default
- This catches many developers off-guard

---

## Code Examples

### Basic Usage

```java
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private PaymentService paymentService;

    @Transactional
    public Order placeOrder(OrderRequest request) {
        // All operations run in one transaction
        Order order = new Order(request);
        orderRepository.save(order);
        
        inventoryService.decrementStock(request.getProductId(), request.getQuantity());
        paymentService.processPayment(request.getPaymentDetails());
        
        return order;
    }
    
    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }
}
```

### Propagation Examples

```java
@Service
public class TransferService {

    @Autowired
    private AccountRepository accountRepo;
    
    @Autowired
    private AuditService auditService;

    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        Account from = accountRepo.findById(fromId).orElseThrow();
        Account to = accountRepo.findById(toId).orElseThrow();
        
        from.debit(amount);
        to.credit(amount);
        
        accountRepo.save(from);
        accountRepo.save(to);
        
        // This runs in a SEPARATE transaction
        // Even if this fails, the transfer still commits
        auditService.logTransfer(fromId, toId, amount);
    }
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransfer(Long fromId, Long toId, BigDecimal amount) {
        // Runs in its own transaction
        // If main transaction rolls back, this still commits
        // If this fails, main transaction is NOT affected
        auditRepository.save(new AuditLog("TRANSFER", fromId, toId, amount));
    }
}
```

### Rollback Control

```java
@Service
public class UserService {

    @Transactional(rollbackFor = Exception.class)
    public void registerUser(UserDTO dto) throws EmailException {
        User user = userRepository.save(new User(dto));
        
        // Without rollbackFor = Exception.class,
        // this checked exception would NOT rollback the transaction
        emailService.sendWelcomeEmail(user.getEmail());
    }

    @Transactional(noRollbackFor = NotificationException.class)
    public void updateProfile(Long userId, ProfileDTO dto) {
        User user = userRepository.findById(userId).orElseThrow();
        user.updateProfile(dto);
        userRepository.save(user);
        
        // Even if notification fails, profile update commits
        notificationService.notifyProfileUpdate(userId);
    }
}
```

---

## The Self-Invocation Problem — CRITICAL

### The Problem

```java
@Service
public class OrderService {

    @Transactional
    public void processOrders(List<OrderRequest> requests) {
        for (OrderRequest req : requests) {
            processOrder(req); // ⚠️ SELF-INVOCATION — @Transactional IGNORED!
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOrder(OrderRequest request) {
        // This does NOT get its own transaction!
        // Because the call bypasses the proxy
        orderRepository.save(new Order(request));
    }
}
```

### Why It Happens

```
External Call:
  Caller → [Proxy] → processOrders()  ✅ Transaction applied

Self-Invocation:
  processOrders() → this.processOrder()  ❌ Bypasses proxy!
  (direct method call on the same object, not through proxy)
```

### Solutions

```java
// Solution 1: Inject self (proxy)
@Service
public class OrderService {

    @Autowired
    private OrderService self; // Injects the proxy

    @Transactional
    public void processOrders(List<OrderRequest> requests) {
        for (OrderRequest req : requests) {
            self.processOrder(req); // ✅ Goes through proxy
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOrder(OrderRequest request) {
        orderRepository.save(new Order(request));
    }
}

// Solution 2: Extract to separate service
@Service
public class OrderService {

    @Autowired
    private OrderProcessor processor;

    @Transactional
    public void processOrders(List<OrderRequest> requests) {
        for (OrderRequest req : requests) {
            processor.processOrder(req); // ✅ Different bean = proxy involved
        }
    }
}

@Service
public class OrderProcessor {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOrder(OrderRequest request) {
        orderRepository.save(new Order(request));
    }
}

// Solution 3: Use ApplicationContext
@Service
public class OrderService implements ApplicationContextAware {

    private ApplicationContext context;

    @Transactional
    public void processOrders(List<OrderRequest> requests) {
        OrderService proxy = context.getBean(OrderService.class);
        for (OrderRequest req : requests) {
            proxy.processOrder(req); // ✅ Through proxy
        }
    }
}
```

---

## Dry Run — Transfer Money

```
Method: transfer(fromId=1, toId=2, amount=500)

Step 1: Proxy intercepts call
        → TransactionInterceptor triggered
        → PlatformTransactionManager.getTransaction()
        → Propagation = REQUIRED → No existing TX → Create new TX
        → Connection obtained from pool, autoCommit = false
        → Bound to ThreadLocal

Step 2: accountRepo.findById(1)
        → Uses SAME connection (ThreadLocal)
        → SELECT * FROM accounts WHERE id = 1
        → Returns Account{id=1, balance=1000}

Step 3: accountRepo.findById(2)
        → SELECT * FROM accounts WHERE id = 2
        → Returns Account{id=2, balance=200}

Step 4: from.debit(500) → balance = 500
Step 5: to.credit(500) → balance = 700

Step 6: accountRepo.save(from)
        → UPDATE accounts SET balance = 500 WHERE id = 1

Step 7: accountRepo.save(to)
        → UPDATE accounts SET balance = 700 WHERE id = 2

Step 8: auditService.logTransfer(1, 2, 500)
        → Propagation = REQUIRES_NEW
        → SUSPEND outer transaction
        → Create NEW transaction (new connection)
        → INSERT INTO audit_log ...
        → COMMIT new transaction
        → RESUME outer transaction

Step 9: Method returns successfully
        → Proxy → TransactionManager.commit()
        → Connection.commit()
        → Connection returned to pool

FAILURE SCENARIO (exception at Step 7):
        → Proxy catches RuntimeException
        → TransactionManager.rollback()
        → Connection.rollback() → Step 6 UPDATE is undone
        → Connection returned to pool
        → Exception propagated to caller
        → NOTE: Audit log (Step 8) was in REQUIRES_NEW, so it's STILL committed
```

---

## Complexity

| Operation | Time Complexity | Notes |
|---|---|---|
| Transaction begin | O(1) | Connection acquisition from pool |
| Commit | O(n) | n = number of dirty entities to flush |
| Rollback | O(1) | Undo log replay |
| Dirty checking | O(n) | n = number of managed entities |
| Proxy method interception | O(1) | Negligible overhead |
| REQUIRES_NEW | O(1) extra | Requires additional connection from pool |

---

## Real Project Usage

### E-Commerce Order Processing

```java
@Service
@Slf4j
public class OrderService {

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Validate stock
        inventoryService.validateStock(request.getItems());
        
        // Create order
        Order order = Order.builder()
            .customerId(request.getCustomerId())
            .status(OrderStatus.PENDING)
            .build();
        order = orderRepository.save(order);
        
        // Create line items
        for (ItemRequest item : request.getItems()) {
            OrderItem orderItem = OrderItem.builder()
                .order(order)
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build();
            orderItemRepository.save(orderItem);
        }
        
        // Decrement inventory (same transaction)
        inventoryService.decrementStock(request.getItems());
        
        // Publish event (REQUIRES_NEW to ensure event is saved even if later steps fail)
        eventPublisher.publish(new OrderCreatedEvent(order.getId()));
        
        return OrderResponse.from(order);
    }
    
    @Transactional(readOnly = true)
    public Page<OrderSummary> getOrders(Long customerId, Pageable pageable) {
        return orderRepository.findOrderSummariesByCustomerId(customerId, pageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(status);
        order.setUpdatedAt(Instant.now());
    }
}
```

### Service Layer with Exception Handling

```java
@Service
@Transactional
public class PaymentService {

    @Transactional(
        isolation = Isolation.REPEATABLE_READ,
        timeout = 10,
        rollbackFor = PaymentException.class
    )
    public PaymentResult processPayment(PaymentRequest request) {
        // Lock the account row
        Account account = accountRepository
            .findByIdForUpdate(request.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException(request.getAccountId()));
        
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(account.getId());
        }
        
        account.debit(request.getAmount());
        
        Payment payment = Payment.builder()
            .accountId(account.getId())
            .amount(request.getAmount())
            .status(PaymentStatus.COMPLETED)
            .build();
        
        return PaymentResult.success(paymentRepository.save(payment));
    }
}
```

---

## Interview Questions and Answers

### Q1: What happens if `@Transactional` is on a private method?

**Answer:** It has no effect. Spring's proxy-based AOP cannot intercept private methods because:
- JDK dynamic proxy works through interfaces (no private methods)
- CGLIB proxy creates a subclass (cannot override private methods)

The annotation is silently ignored. The method runs without transaction management.

### Q2: Explain the difference between REQUIRED and REQUIRES_NEW.

**Answer:**
- **REQUIRED** (default): If a transaction exists, join it. If not, create one. One failure rolls back the entire transaction.
- **REQUIRES_NEW**: Always creates a NEW transaction. Suspends any existing transaction. Inner and outer transactions are independent — inner commit/rollback doesn't affect outer.

Use case: Audit logging should use REQUIRES_NEW so the audit log persists even if the main business operation rolls back.

### Q3: Why does `@Transactional` not work when calling a method from within the same class?

**Answer:** Spring uses proxy-based AOP. When you call a method within the same class (self-invocation), the call goes directly to `this` object, bypassing the proxy. The proxy is only involved for external calls.

Solutions:
1. Inject the bean into itself (`@Autowired private MyService self`)
2. Extract the method to a separate service class
3. Use `ApplicationContext.getBean()`

### Q4: What is the default rollback behavior of `@Transactional`?

**Answer:**
- **Rolls back on:** `RuntimeException` (unchecked) and `Error`
- **Does NOT rollback on:** `Exception` (checked)

This is intentional — checked exceptions often represent expected business conditions (e.g., `InsufficientFundsException`). Override with `rollbackFor = Exception.class` if needed.

### Q5: How does `readOnly = true` improve performance?

**Answer:**
1. **Hibernate**: Skips dirty checking at flush time (no snapshot comparison needed)
2. **Connection pool**: May route to a read replica
3. **Database**: May optimize query execution knowing no writes will occur
4. **Persistence Context**: Entities can be loaded in read-only mode (no snapshot stored)

### Q6: Can you nest `@Transactional` methods? What happens?

**Answer:** Yes. The behavior depends on propagation:
- **REQUIRED** (default): Inner method joins outer transaction. If inner fails, entire transaction rolls back.
- **REQUIRES_NEW**: Inner gets its own transaction. Failures are independent.
- **NESTED**: Uses SAVEPOINT. Inner failure rolls back to savepoint, outer can still commit.

---

## Follow-up Questions and Answers

### Q: What happens if a `@Transactional(readOnly = true)` method writes to the database?

**Answer:** It depends on the provider:
- Hibernate: The write may succeed because `readOnly` is a hint, not enforcement
- Some databases: May throw an exception
- Best practice: Don't rely on the flag for enforcement; use it as a performance optimization

### Q: How do you handle transactions across multiple datasources?

**Answer:**
- Use `JtaTransactionManager` with XA-capable datasources (distributed transactions)
- Or use `ChainedTransactionManager` (best-effort with ordered commit/rollback)
- Or implement the Saga pattern for eventual consistency
- Spring Boot does NOT auto-configure multi-datasource transactions

### Q: What happens to the Hibernate session/persistence context when REQUIRES_NEW is used?

**Answer:**
- The outer persistence context is suspended
- A new persistence context is created for the inner transaction
- Entities loaded in the inner transaction are NOT visible to the outer context
- After inner completes, outer context is resumed
- If you need the same entity in both, you must reload it

### Q: How does `@Transactional` interact with `@Async`?

**Answer:**
- `@Async` methods run in a different thread
- Transaction context is ThreadLocal-bound → NOT propagated to async thread
- The async method needs its own `@Transactional` annotation
- The async method always starts a new transaction regardless of outer propagation

---

## Common Mistakes

| Mistake | Problem | Fix |
|---|---|---|
| `@Transactional` on private method | Annotation ignored | Make method public |
| Self-invocation | Proxy bypassed | Inject self or extract to separate bean |
| Checked exception not rolling back | Transaction commits despite error | Use `rollbackFor = Exception.class` |
| Too broad transaction scope | Long lock times, poor concurrency | Keep transactions short |
| Missing `@Transactional` on service layer | Each repository call is separate transaction | Add at service method level |
| `readOnly = true` on write method | Unpredictable behavior | Match annotation to actual operations |
| Long transaction with external HTTP calls | Connection held too long | Move HTTP calls outside transaction |
| REQUIRES_NEW in a loop | Connection pool exhaustion | Batch or reduce new transactions |

---

## Best Practices

1. **Place `@Transactional` at the service layer**, not repository or controller
2. **Keep transactions short** — don't hold connections during HTTP calls or file I/O
3. **Use `readOnly = true`** for all read operations
4. **Be explicit about rollback rules** — add `rollbackFor` for checked exceptions
5. **Avoid REQUIRES_NEW in loops** — can exhaust connection pool
6. **Don't mix transaction management** — either declarative OR programmatic, not both
7. **Test self-invocation scenarios** — common source of bugs
8. **Set timeouts** on transactions that interact with external systems
9. **Use `@Transactional` at class level** for consistent behavior across all methods, override per-method as needed
10. **Log transaction boundaries** in development for debugging

---

## Production Considerations

### Connection Pool Sizing with REQUIRES_NEW

```
If your main flow holds 1 connection and calls REQUIRES_NEW:
→ That method needs ANOTHER connection from the pool

If pool size = 10 and 10 threads each call REQUIRES_NEW:
→ Each thread needs 2 connections = 20 needed, but only 10 available
→ DEADLOCK (threads waiting for connections that won't be released)

Solution: pool size >= max_threads × max_nested_transactions
```

### Monitoring

```yaml
# application.yml — enable transaction logging
logging:
  level:
    org.springframework.transaction: DEBUG
    org.springframework.orm.jpa: DEBUG
    org.hibernate.SQL: DEBUG
```

### Transaction Boundaries in Microservices

- HTTP call inside a transaction = ANTI-PATTERN
- Use Saga pattern for cross-service transactions
- Or use Outbox pattern with CDC

```java
// BAD — holds DB connection during HTTP call
@Transactional
public void processOrder(Order order) {
    orderRepo.save(order);
    paymentClient.charge(order); // HTTP call holding transaction open!
}

// GOOD — separate concerns
@Transactional
public void processOrder(Order order) {
    orderRepo.save(order);
    outboxRepo.save(new OutboxEvent("ORDER_CREATED", order.getId()));
}
// Separate async process picks up outbox events and calls payment service
```

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation Levels
- Topic 19: JPA, Hibernate & Spring Data JPA (entity lifecycle)
- Topic 21: Locking & Concurrency (pessimistic/optimistic locking)
- Topic 24: JDBC & Connection Pooling
- Topic 25: Advanced Database Performance
