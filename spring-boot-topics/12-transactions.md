# 12. Transactions

## Theory

**@Transactional:**
Declarative transaction management in Spring. Wraps method execution in a database transaction (begin → commit/rollback).

**Transaction Properties (ACID):**
- **Atomicity**: All or nothing
- **Consistency**: Valid state to valid state
- **Isolation**: Concurrent transactions don't interfere
- **Durability**: Committed data persists

**Propagation Types:**

| Type | Behavior |
|------|----------|
| REQUIRED (default) | Join existing or create new |
| REQUIRES_NEW | Always create new (suspend current) |
| SUPPORTS | Join existing or run without |
| NOT_SUPPORTED | Always run without (suspend current) |
| MANDATORY | Must have existing (throw if none) |
| NEVER | Must NOT have existing (throw if exists) |
| NESTED | Nested within existing (savepoint) |

**Isolation Levels:**

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|-------|-----------|-------------------|-------------|
| READ_UNCOMMITTED | ✅ | ✅ | ✅ |
| READ_COMMITTED | ❌ | ✅ | ✅ |
| REPEATABLE_READ | ❌ | ❌ | ✅ |
| SERIALIZABLE | ❌ | ❌ | ❌ |

**Rollback Rules:**
- RuntimeException (unchecked) → Auto rollback ✅
- Checked Exception → No rollback ❌ (by default)
- Error → Auto rollback ✅

---

## Internal Working

```
@Transactional method called
       ↓
Spring Proxy intercepts (AOP)
       ↓
TransactionInterceptor invoked
       ↓
PlatformTransactionManager.getTransaction()
  - Checks propagation rules
  - Gets connection from pool
  - Sets auto-commit = false
  - Sets isolation level
       ↓
Target method executes
       ↓
Method completes normally?
  ├── YES → TransactionManager.commit()
  │          → Connection.commit()
  │          → Connection returned to pool
  └── NO (exception) → 
       Is it RuntimeException or configured rollbackFor?
       ├── YES → TransactionManager.rollback()
       │          → Connection.rollback()
       └── NO → TransactionManager.commit() (!) ← Surprise!

CRITICAL: Why self-invocation breaks @Transactional
┌──────────────────────┐
│    Client Code       │
│                      │
│  orderService.save() │ ← Goes through proxy ✅
└──────────┬───────────┘
           ↓
┌──────────────────────┐
│     PROXY            │ ← Transaction started here
│  (TransactionProxy)  │
└──────────┬───────────┘
           ↓
┌──────────────────────┐
│  OrderService        │
│                      │
│  save() {            │
│    this.validate();  │ ← Direct call, BYPASSES proxy ❌
│  }                   │     @Transactional on validate() IGNORED
└──────────────────────┘
```

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│              @Transactional Flow                      │
│                                                      │
│  Caller                                              │
│    ↓                                                 │
│  Proxy (TransactionInterceptor)                      │
│    ↓                                                 │
│  BEGIN TRANSACTION                                   │
│    ↓                                                 │
│  Target Method                                       │
│    ↓                                                 │
│  ┌─────────────┐                                    │
│  │ Success?    │                                    │
│  │  YES → COMMIT                                    │
│  │  NO  → ROLLBACK (if unchecked exception)         │
│  └─────────────┘                                    │
└─────────────────────────────────────────────────────┘

Propagation REQUIRES_NEW:
┌─────────────────────────────────────────────┐
│ Service A (@Transactional)                   │
│   Transaction T1 active                      │
│   ↓                                          │
│   Call Service B (REQUIRES_NEW)              │
│   ↓                                          │
│   T1 SUSPENDED                               │
│   ┌────────────────────────────────┐        │
│   │ Service B                       │        │
│   │   Transaction T2 (NEW)          │        │
│   │   ↓                             │        │
│   │   COMMIT or ROLLBACK T2         │        │
│   └────────────────────────────────┘        │
│   T1 RESUMED                                 │
│   ↓                                          │
│   Continue in T1                             │
└─────────────────────────────────────────────┘
```

---

## Code

```java
// === Basic @Transactional ===
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // All or nothing: if payment fails, order is not saved
        Order order = new Order(request.getCustomerId());

        for (OrderItemRequest item : request.getItems()) {
            inventoryService.decrementStock(item.getProductId(), item.getQuantity());
            order.addItem(item.getProductId(), item.getQuantity(), item.getPrice());
        }

        Order saved = orderRepository.save(order);
        paymentService.charge(request.getPaymentMethod(), order.getTotal());

        return saved;
    }

    // Read-only transaction (optimizations: no dirty checking, can use read replica)
    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}

// === Propagation examples ===
@Service
public class AuditService {

    // REQUIRES_NEW: audit log must persist even if parent transaction fails
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String action, String details) {
        auditRepository.save(new AuditLog(action, details));
        // This commits independently of caller's transaction
    }
}

@Service
public class PaymentService {

    private final AuditService auditService;

    @Transactional
    public void processPayment(Payment payment) {
        try {
            // Business logic that might fail
            gateway.charge(payment);
            paymentRepository.save(payment);
        } catch (PaymentDeclinedException e) {
            // Audit log persists even though this transaction rolls back
            auditService.logAction("PAYMENT_DECLINED", payment.getId().toString());
            throw e;  // Re-throw to trigger rollback of THIS transaction
        }
    }
}

// === Rollback rules ===
@Service
public class TransferService {

    // Rollback on checked exception too
    @Transactional(rollbackFor = InsufficientFundsException.class)
    public void transfer(Long fromId, Long toId, BigDecimal amount)
            throws InsufficientFundsException {

        Account from = accountRepository.findById(fromId).orElseThrow();
        Account to = accountRepository.findById(toId).orElseThrow();

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Not enough balance");
            // Without rollbackFor, this checked exception would COMMIT!
        }

        from.debit(amount);
        to.credit(amount);
    }

    // Don't rollback on specific runtime exception
    @Transactional(noRollbackFor = EmailSendFailedException.class)
    public void createOrderAndNotify(Order order) {
        orderRepository.save(order);
        try {
            emailService.sendConfirmation(order); // May throw
        } catch (EmailSendFailedException e) {
            // Order still committed, email failure is non-critical
            log.warn("Email failed for order {}", order.getId());
        }
    }
}

// === Timeout ===
@Transactional(timeout = 5)  // 5 seconds max
public void importLargeDataset(List<Data> dataset) {
    // If this takes > 5 seconds, transaction times out and rolls back
}

// === Isolation level ===
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferBetweenAccounts(Long from, Long to, BigDecimal amount) {
    // Prevents phantom reads — strongest isolation
}

// === Self-invocation fix ===
@Service
public class OrderService {

    private final OrderService self; // Inject self for proxy access

    // Or better: extract to separate service
    // Or use ApplicationContext.getBean()

    @Transactional
    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        // self.validate(order);  // Goes through proxy ✅
        validationService.validate(order);  // Better: separate service
    }
}

// === Transaction with async (WRONG!) ===
@Service
public class OrderService {

    @Transactional
    public void createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));

        // WRONG: @Async runs in different thread = different transaction!
        notificationService.sendAsync(order); // May see uncommitted data
    }
}

// CORRECT: Use TransactionalEventListener
@Service
public class OrderService {

    private final ApplicationEventPublisher publisher;

    @Transactional
    public void createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        publisher.publishEvent(new OrderCreatedEvent(order.getId()));
    }
}

@Component
public class OrderEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        // Runs only after transaction successfully commits
        notificationService.send(event.getOrderId());
    }
}
```

---

## Dry Run

**Scenario**: Transfer $100 between accounts, debit fails

```java
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepository.findById(fromId).orElseThrow(); // Step 1
    Account to = accountRepository.findById(toId).orElseThrow();     // Step 2
    from.debit(amount);  // Step 3 — throws InsufficientFundsException
    to.credit(amount);   // Step 4 — never reached
}
```

```
1. Proxy intercepts → TransactionManager begins transaction
2. Connection obtained from HikariCP, auto-commit=false
3. Step 1: SELECT * FROM accounts WHERE id = 1 (from)
4. Step 2: SELECT * FROM accounts WHERE id = 2 (to)
5. Step 3: from.debit(100) → balance is 50 → throws InsufficientFundsException
6. Exception propagates to proxy
7. InsufficientFundsException extends RuntimeException → ROLLBACK triggered
8. Connection.rollback() executed
9. Connection returned to pool
10. Exception propagated to caller

Result: NEITHER account modified. Atomicity preserved.

If InsufficientFundsException was a CHECKED exception (without rollbackFor):
7. Checked exception → COMMIT triggered! (Bug!)
8. from.debit() was never called (exception), but nothing else changed
   In this case no harm, but could be dangerous in other scenarios.
```

---

## Complexity

| Operation | Overhead |
|-----------|----------|
| Transaction begin | ~0.1ms (connection acquisition) |
| Transaction commit | ~1-5ms (disk flush) |
| Transaction rollback | ~0.5ms |
| SERIALIZABLE isolation | High contention, potential deadlocks |
| READ_COMMITTED | Standard overhead |
| Read-only optimization | ~10% faster (no dirty check snapshots) |

---

## Real Project Usage

```java
// Saga-like pattern with compensation
@Service
public class OrderFulfillmentService {

    @Transactional
    public void fulfillOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();

        try {
            inventoryService.reserve(order);      // May throw
            paymentService.capture(order);        // May throw
            shippingService.createLabel(order);   // May throw

            order.setStatus(OrderStatus.FULFILLED);
        } catch (PaymentException e) {
            inventoryService.release(order);  // Compensate
            throw e;  // Rollback the rest
        }
    }
}
```

---

## Interview Questions

1. **How does @Transactional work internally? (Proxy mechanism)**
   - Spring creates a CGLIB/JDK proxy around the bean. When method called, proxy: gets connection from pool → disables auto-commit → calls target method → commits on success / rolls back on exception → returns connection. TransactionInterceptor handles this via AOP.

2. **What is self-invocation and why does it break @Transactional?**
   - Calling a @Transactional method from within the same class (`this.method()`) bypasses the proxy — goes directly to the target object. No proxy = no transaction management. Fix: extract to separate class or inject self.

3. **Explain transaction propagation types with examples.**
   - REQUIRED (default): Join existing tx or create new. REQUIRES_NEW: Always create new tx, suspend existing. SUPPORTS: Use tx if exists, else run without. MANDATORY: Must have existing tx or throw exception. NESTED: Savepoint within existing tx.

4. **What is the default rollback behavior? How do you change it?**
   - Default: Rollback on unchecked exceptions (RuntimeException), commit on checked exceptions. Change with: `rollbackFor = Exception.class` (rollback on all), `noRollbackFor = BusinessException.class` (don't rollback specific).

5. **What happens if you put @Transactional on a private method?**
   - Nothing — it's silently ignored. The proxy cannot intercept private methods (not visible to subclass/interface proxy). The method runs without transaction management.

6. **What is the difference between REQUIRED and REQUIRES_NEW?**
   - REQUIRED: Joins caller's transaction (one big tx, one commit/rollback). REQUIRES_NEW: Suspends caller's tx, creates independent tx (inner can commit even if outer rolls back). Use REQUIRES_NEW for audit logs.

7. **How does @Transactional work with checked exceptions?**
   - By default, checked exceptions DO NOT trigger rollback (tx commits). Must explicitly specify: `@Transactional(rollbackFor = Exception.class)`. This is a common interview trap.

8. **What is the difference between READ_COMMITTED and REPEATABLE_READ?**
   - READ_COMMITTED: Sees only committed data; re-reading same row may return different value (non-repeatable read). REPEATABLE_READ: Same query returns same results within transaction (snapshot isolation). Higher isolation = less concurrency.

9. **How do you handle transactions with @Async methods?**
   - @Async methods run in a new thread with NO transaction context from the caller. They need their own @Transactional annotation. Transaction propagation doesn't cross thread boundaries.

10. **What is @Transactional(readOnly = true) and what does it optimize?**
    - Hints that no writes will occur. Optimizations: Hibernate skips dirty checking (no snapshot comparison), DB may route to read replica, JDBC driver may optimize. Always use for read-only service methods.

---

## Follow-up Questions

1. **After Q2**: "How do you fix self-invocation?"
   → Extract to separate service (best), inject self, use `AopContext.currentProxy()`, or use `@Transactional` on the outer method.

2. **After Q5**: "What about protected or package-private methods?"
   → With JDK proxy: only public works. With CGLIB proxy: public and protected work. Private never works.

3. **After Q9**: "What's the correct pattern for async after commit?"
   → `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

4. **After Q7**: "Why doesn't Spring rollback on checked exceptions by default?"
   → Historical convention: checked exceptions often represent recoverable business conditions (not failures).

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| @Transactional on private method | Proxy can't intercept | Make public |
| Self-invocation | Bypasses proxy | Extract to separate service |
| Catching exception inside @Transactional | Hides rollback trigger | Let exception propagate or manually rollback |
| Checked exception without rollbackFor | Transaction commits on failure | Add `rollbackFor = Exception.class` |
| Long-running transactions | Holds connections, blocks others | Keep transactions short |
| @Transactional + @Async on same method | Async runs in new thread without transaction | Separate concerns |
| Not testing rollback behavior | Bugs in error paths | Test failure scenarios |
| REQUIRES_NEW everywhere | Creates too many connections | Only when truly independent |

---

## Best Practices

1. **Keep transactions short** — minimize lock duration
2. **@Transactional(readOnly = true)** for read operations
3. **Rollback on all exceptions**: `@Transactional(rollbackFor = Exception.class)`
4. **Separate service classes** to avoid self-invocation
5. **Use REQUIRES_NEW** only for truly independent operations (audit, logging)
6. **Test rollback scenarios** — verify atomicity
7. **Use @TransactionalEventListener** for post-commit actions
8. **Set timeouts** on long-running transactions
9. **Understand proxy model** — annotate at service layer, not repository
10. **Monitor connection pool** — long transactions exhaust connections

---

## Production Considerations

- **Connection pool exhaustion**: Long transactions hold connections; tune HikariCP accordingly
- **Deadlocks**: SERIALIZABLE and REPEATABLE_READ increase deadlock risk; implement retry
- **Distributed transactions**: Across microservices, use Saga pattern (not 2PC)
- **Transaction + Kafka**: Use transactional outbox pattern for exactly-once
- **Monitoring**: Track transaction duration, rollback rate via Micrometer
- **Read replicas**: Route `readOnly = true` transactions to replica DB
- **Retry on OptimisticLockException**: Implement retry logic for concurrent conflicts

---

## Related Topics

- → [4. Spring Bean Management](#) (proxy creation in BeanPostProcessor)
- → [9. Spring Data JPA](#) (persistence context lifecycle)
- → [14. Database Connection Pool](#) (connection per transaction)
- → [17. Spring AOP](#) (proxy mechanism)
- → [18. Spring Events](#) (@TransactionalEventListener)
- → [23. Kafka](#) (transactional outbox pattern)
