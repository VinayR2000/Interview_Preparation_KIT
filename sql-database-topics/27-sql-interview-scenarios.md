# Topic 27: SQL Interview Scenarios

## Theory

### Why Scenario-Based Questions?

Senior-level database interviews focus on scenario-based questions because they test:
1. Understanding of concurrent access and race conditions
2. Ability to reason about system behavior under load
3. Knowledge of failure modes and recovery strategies
4. Practical experience with production issues

These are not coding problems — they require verbal explanation of what happens and how to fix it.

---

## Scenario 1: 500 Users Buying 5 Available Items

### The Problem

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: Flash sale — 500 users try to buy 5 items             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Product: Widget X, stock = 5                                    │
│  500 concurrent users hit "Buy Now" at the same time             │
│                                                                  │
│  NAIVE CODE:                                                     │
│  1. SELECT stock FROM products WHERE id = 1;  -- reads stock=5  │
│  2. IF stock > 0 THEN                                            │
│  3.   UPDATE products SET stock = stock - 1 WHERE id = 1;       │
│  4.   INSERT INTO orders (...);                                  │
│  5. END IF;                                                      │
│                                                                  │
│  RACE CONDITION:                                                 │
│  User A reads stock=5, User B reads stock=5 (before A updates)  │
│  Both proceed → stock goes to 3 (should be 3, 2 items sold)     │
│  With 500 users → oversold! More than 5 orders created.         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Solutions

```sql
-- SOLUTION 1: Pessimistic Lock (SELECT FOR UPDATE)
BEGIN;
SELECT stock FROM products WHERE id = 1 FOR UPDATE; -- Locks the row
-- Other transactions WAIT here until this transaction completes
IF stock > 0 THEN
    UPDATE products SET stock = stock - 1 WHERE id = 1;
    INSERT INTO orders (...);
END IF;
COMMIT;

-- SOLUTION 2: Atomic UPDATE with condition (best for simple cases)
UPDATE products 
SET stock = stock - 1 
WHERE id = 1 AND stock > 0
RETURNING stock;
-- If returns 0 rows affected → stock was already 0 → reject order
-- ATOMIC — no race condition possible

-- SOLUTION 3: Optimistic Lock with version
UPDATE products 
SET stock = stock - 1, version = version + 1
WHERE id = 1 AND stock > 0 AND version = :expected_version;
-- If 0 rows affected → retry (someone else modified)
```

```java
// Spring Boot implementation — Pessimistic Lock
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}

@Service
public class PurchaseService {
    
    @Transactional
    public OrderResult purchase(Long productId, Long userId) {
        Product product = productRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        
        if (product.getStock() <= 0) {
            throw new OutOfStockException(productId);
        }
        
        product.setStock(product.getStock() - 1);
        Order order = orderRepository.save(new Order(userId, productId));
        
        return OrderResult.success(order);
    }
}
```

---

## Scenario 2: Two Transactions Updating the Same Row

### The Problem

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: Lost Update                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Account balance = $1000                                         │
│                                                                  │
│  Transaction A: Deposit $200                                     │
│  Transaction B: Withdraw $300                                    │
│                                                                  │
│  WITHOUT PROPER LOCKING:                                         │
│  ─────────────────────────────────────────────────────           │
│  Time    TX-A                       TX-B                         │
│  T1      READ balance = 1000                                     │
│  T2                                 READ balance = 1000          │
│  T3      balance = 1000 + 200                                    │
│  T4      UPDATE balance = 1200                                   │
│  T5                                 balance = 1000 - 300         │
│  T6                                 UPDATE balance = 700         │
│  ─────────────────────────────────────────────────────           │
│                                                                  │
│  RESULT: balance = 700                                           │
│  EXPECTED: 1000 + 200 - 300 = 900                                │
│  LOST UPDATE: The $200 deposit is lost!                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Solutions

```sql
-- SOLUTION 1: Atomic arithmetic (simplest, preferred)
-- Don't read-then-write. Do it in one statement.
UPDATE accounts SET balance = balance + 200 WHERE id = 1; -- TX-A
UPDATE accounts SET balance = balance - 300 WHERE id = 1; -- TX-B
-- The database serializes these. No lost update possible.

-- SOLUTION 2: SELECT FOR UPDATE (when logic is complex)
BEGIN;
SELECT balance FROM accounts WHERE id = 1 FOR UPDATE;
-- Compute new balance in application
UPDATE accounts SET balance = :new_balance WHERE id = 1;
COMMIT;

-- SOLUTION 3: Optimistic locking (@Version in JPA)
UPDATE accounts 
SET balance = :new_balance, version = version + 1
WHERE id = 1 AND version = :current_version;
-- If 0 rows: OptimisticLockException → retry
```

---

## Scenario 3: Deadlock Between Two Transactions

### The Problem

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: Transfer money between two accounts                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  TX-1: Transfer $100 from Account A → Account B                  │
│  TX-2: Transfer $50 from Account B → Account A                   │
│                                                                  │
│  Time    TX-1                         TX-2                       │
│  T1      LOCK Account A (row lock)                               │
│  T2                                   LOCK Account B (row lock)  │
│  T3      Try to LOCK Account B        Try to LOCK Account A     │
│          → BLOCKED (TX-2 holds it)    → BLOCKED (TX-1 holds it) │
│                                                                  │
│          ████████ DEADLOCK ████████                               │
│                                                                  │
│  PostgreSQL detects deadlock → kills one transaction             │
│  → ERROR: deadlock detected                                      │
│  → DETAIL: Process 1234 waits for lock on row...                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Solutions

```java
// SOLUTION 1: Consistent lock ordering (BEST)
// Always lock accounts in the same order (e.g., by ID ascending)
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // Lock in consistent order (lower ID first)
    Long firstId = Math.min(fromId, toId);
    Long secondId = Math.max(fromId, toId);
    
    Account first = accountRepo.findByIdForUpdate(firstId);
    Account second = accountRepo.findByIdForUpdate(secondId);
    
    Account from = fromId.equals(firstId) ? first : second;
    Account to = toId.equals(firstId) ? first : second;
    
    from.debit(amount);
    to.credit(amount);
}
// Both TX-1 and TX-2 will lock Account A first, then B
// No circular wait → no deadlock

// SOLUTION 2: Retry with exponential backoff
@Retryable(
    value = DeadlockLoserDataAccessException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepo.findByIdForUpdate(fromId);
    Account to = accountRepo.findByIdForUpdate(toId);
    from.debit(amount);
    to.credit(amount);
}

// SOLUTION 3: Lock timeout
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // Set short lock timeout — fail fast instead of waiting
    entityManager.createNativeQuery("SET LOCAL lock_timeout = '5s'").executeUpdate();
    // If can't acquire lock in 5s, throws exception → retry
}
```

---

## Scenario 4: Preventing Double Payment

### The Problem

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: User clicks "Pay" twice (or network retry)            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User clicks Pay → Request 1 sent                                │
│  Network slow → User clicks Pay again → Request 2 sent          │
│  OR: API gateway retries on timeout → Request 2 sent             │
│                                                                  │
│  Without protection:                                             │
│  Request 1: Charge $100 ✓                                        │
│  Request 2: Charge $100 ✓  (double charge!)                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Solution: Idempotency Key

```java
@Service
public class PaymentService {

    @Transactional
    public PaymentResult processPayment(String idempotencyKey, PaymentRequest request) {
        // Check if this request was already processed
        Optional<Payment> existing = paymentRepository
            .findByIdempotencyKey(idempotencyKey);
        
        if (existing.isPresent()) {
            // Already processed — return same result (idempotent)
            return PaymentResult.from(existing.get());
        }
        
        // Process payment
        Payment payment = Payment.builder()
            .idempotencyKey(idempotencyKey)
            .amount(request.getAmount())
            .customerId(request.getCustomerId())
            .status(PaymentStatus.COMPLETED)
            .build();
        
        paymentRepository.save(payment);
        return PaymentResult.success(payment);
    }
}

// Database schema
// CREATE TABLE payments (
//     id BIGSERIAL PRIMARY KEY,
//     idempotency_key VARCHAR(255) UNIQUE NOT NULL, ← UNIQUE constraint
//     amount DECIMAL(10,2),
//     status VARCHAR(20),
//     ...
// );
// 
// If two requests arrive simultaneously with same idempotency_key:
// One succeeds (INSERT), other gets unique constraint violation → return existing
```

---

## Scenario 5: Duplicate Order Creation

### The Problem and Solution

```java
// PROBLEM: Network timeout → client retries → duplicate order

// SOLUTION: Idempotency + Unique constraint on business key
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "cart_id"}))
public class Order {
    @Id @GeneratedValue
    private Long id;
    
    private Long customerId;
    private String cartId; // Client sends this — same cart = same order
}

@Service
public class OrderService {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // Check if order already exists for this cart
        Optional<Order> existing = orderRepository
            .findByCustomerIdAndCartId(request.getCustomerId(), request.getCartId());
        
        if (existing.isPresent()) {
            return existing.get(); // Idempotent — return existing order
        }
        
        try {
            Order order = new Order(request);
            return orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread created it between check and insert
            return orderRepository
                .findByCustomerIdAndCartId(request.getCustomerId(), request.getCartId())
                .orElseThrow();
        }
    }
}
```

---

## Scenario 6: Inventory Decrement (500 Users, 5 Items)

### Optimistic vs Pessimistic Approach

```java
// APPROACH 1: Pessimistic (guaranteed, but high contention)
@Transactional
public boolean purchaseItem(Long productId) {
    Product product = productRepo.findByIdForUpdate(productId); // Row lock
    if (product.getStock() > 0) {
        product.setStock(product.getStock() - 1);
        return true;
    }
    return false; // Out of stock
}
// With 500 concurrent requests: they serialize (queue up)
// Only 5 succeed, 495 get "out of stock"
// Trade-off: high contention, but correct

// APPROACH 2: Optimistic (lower contention, retry needed)
@Transactional
public boolean purchaseItem(Long productId) {
    Product product = productRepo.findById(productId).orElseThrow();
    if (product.getStock() <= 0) return false;
    
    product.setStock(product.getStock() - 1);
    try {
        productRepo.saveAndFlush(product); // @Version check
        return true;
    } catch (OptimisticLockException e) {
        // Someone else modified — retry
        throw new RetryableException("Concurrent modification, retry");
    }
}
// With 500 concurrent: many retries, some may fail after max retries
// Trade-off: lower contention per attempt, but more complex

// APPROACH 3: Atomic SQL (BEST for simple decrement)
@Modifying
@Query("UPDATE Product p SET p.stock = p.stock - 1 " +
       "WHERE p.id = :id AND p.stock > 0")
int decrementStock(@Param("id") Long id);

// Service:
@Transactional
public boolean purchaseItem(Long productId) {
    int updated = productRepo.decrementStock(productId);
    if (updated == 0) {
        throw new OutOfStockException(productId);
    }
    orderRepo.save(new Order(...));
    return true;
}
// ATOMIC — database handles concurrency
// No race condition, no explicit locking needed
// Fastest approach for simple counter operations
```

---

## Scenario 7: Database Connection Pool Exhaustion

### Symptoms and Diagnosis

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: Connection pool exhaustion                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  SYMPTOMS:                                                       │
│  • HikariPool-1 - Connection is not available, request           │
│    timed out after 30000ms                                       │
│  • Requests timing out                                           │
│  • Application appears frozen                                    │
│                                                                  │
│  COMMON CAUSES:                                                  │
│  1. Long-running transactions (HTTP call inside @Transactional)  │
│  2. Connection leak (not returned to pool)                       │
│  3. Pool too small for concurrent load                           │
│  4. REQUIRES_NEW in loop (each needs additional connection)      │
│  5. Deadlock holding connections indefinitely                    │
│  6. Slow queries holding connections                             │
│                                                                  │
│  DIAGNOSIS:                                                      │
│  • Check hikaricp_connections_active metric                      │
│  • Enable leak-detection-threshold                               │
│  • Check pg_stat_activity for idle-in-transaction connections    │
│  • Review @Transactional boundaries for external calls           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Solution

```java
// BAD: HTTP call inside transaction
@Transactional
public void processOrder(Order order) {
    orderRepo.save(order);
    paymentGateway.charge(order.getTotal()); // 2-5 second HTTP call!
    // Connection held for entire duration
}

// GOOD: Move external call outside transaction
public void processOrder(Order order) {
    saveOrder(order);
    PaymentResult result = paymentGateway.charge(order.getTotal()); // No tx held
    updateOrderStatus(order.getId(), result);
}

@Transactional
private void saveOrder(Order order) {
    orderRepo.save(order); // Quick — connection held briefly
}

@Transactional
private void updateOrderStatus(Long orderId, PaymentResult result) {
    orderRepo.updateStatus(orderId, result.getStatus()); // Quick
}
```

---

## Scenario 8: Slow Query Suddenly Appears in Production

### Diagnosis Steps

```
Step 1: Identify the query
─────────────────────────
SELECT * FROM pg_stat_activity 
WHERE state = 'active' AND duration > interval '5 seconds';

Step 2: Check execution plan
────────────────────────────
EXPLAIN ANALYZE <the slow query>;

Step 3: Common root causes
──────────────────────────
┌────────────────────────────────┬────────────────────────────────┐
│ Root Cause                     │ Fix                            │
├────────────────────────────────┼────────────────────────────────┤
│ Table grown, stats outdated    │ ANALYZE table_name;            │
│ New query pattern, no index    │ CREATE INDEX ...               │
│ Lock wait (blocked)            │ Find and fix blocking tx       │
│ Autovacuum behind              │ Manual VACUUM, tune autovacuum │
│ Disk I/O saturation            │ Add memory, optimize queries   │
│ Plan regression (bad plan)     │ ANALYZE, or pin good plan      │
│ Table bloat (dead tuples)      │ VACUUM FULL or pg_repack       │
│ Missing index after data growth│ Add targeted index             │
└────────────────────────────────┴────────────────────────────────┘

Step 4: Verify fix
──────────────────
-- Before: Seq Scan, 500ms
-- After:  Index Scan, 2ms
EXPLAIN ANALYZE <same query>;
```

---

## Scenario 9: Index Not Being Used

```sql
-- You created an index but the query still does a sequential scan. Why?

-- REASON 1: Function applied to indexed column
CREATE INDEX idx_users_email ON users(email);
SELECT * FROM users WHERE LOWER(email) = 'john@test.com';
-- Index on email doesn't help for LOWER(email)
-- FIX: CREATE INDEX idx_users_email_lower ON users(LOWER(email));

-- REASON 2: Type mismatch
CREATE INDEX idx_orders_customer_id ON orders(customer_id); -- bigint
SELECT * FROM orders WHERE customer_id = '123'; -- string comparison!
-- FIX: Use correct type: WHERE customer_id = 123

-- REASON 3: Statistics outdated
-- Planner thinks table has 10 rows (stale stats), does seq scan
-- FIX: ANALYZE orders;

-- REASON 4: High selectivity (returns most of table)
SELECT * FROM orders WHERE status = 'COMPLETED'; -- 95% of rows
-- Planner correctly chooses seq scan (faster for large % of rows)
-- NOT a problem — seq scan is the right choice here

-- REASON 5: OR condition
SELECT * FROM users WHERE email = 'a@b.com' OR name = 'John';
-- Single index can't cover both columns in OR
-- FIX: Two separate indexes + UNION, or expression index

-- REASON 6: Leading wildcard in LIKE
SELECT * FROM users WHERE email LIKE '%@gmail.com';
-- B-tree can only use prefix: 'abc%' works, '%abc' doesn't
-- FIX: Use pg_trgm GIN index for pattern matching
```

---

## Scenario 10: N+1 Queries Causing Slow API

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: API endpoint taking 3 seconds                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  GET /api/orders?page=1&size=20                                  │
│                                                                  │
│  Logs show 41 SQL queries:                                       │
│  1x SELECT * FROM orders LIMIT 20                                │
│  20x SELECT * FROM customers WHERE id = ?                        │
│  20x SELECT * FROM order_items WHERE order_id = ?                │
│                                                                  │
│  Each query = 1ms + 2ms network = 3ms                            │
│  41 × 3ms = 123ms just for queries                               │
│  With DB connection overhead = 300ms+ per request                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

FIX:
────
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.customer " +
       "JOIN FETCH o.items")
Page<Order> findAllWithDetails(Pageable pageable);

// Now: 1 query instead of 41
// Response time: 300ms → 15ms
```

---

## Scenario 11: Database CPU Suddenly at 100%

### Diagnosis and Response

```
IMMEDIATE ACTIONS:
──────────────────
1. Check what's running:
   SELECT pid, now() - query_start as duration, query, state
   FROM pg_stat_activity
   WHERE state = 'active'
   ORDER BY duration DESC;

2. Identify the culprit:
   - One massive query (missing index, full table scan)
   - Many small queries (N+1, connection storm)
   - Lock contention (blocking chain)
   - Autovacuum running on huge table

3. Short-term fix:
   -- Kill long-running query
   SELECT pg_cancel_backend(<pid>);  -- graceful
   SELECT pg_terminate_backend(<pid>); -- force

4. Long-term fixes based on root cause:
   ┌──────────────────────────────┬───────────────────────────────┐
   │ Cause                        │ Fix                           │
   ├──────────────────────────────┼───────────────────────────────┤
   │ Missing index                │ CREATE INDEX                  │
   │ N+1 queries                  │ Fetch join / batch loading    │
   │ Table scan on huge table     │ Add index, partition table    │
   │ Statistics stale             │ ANALYZE                       │
   │ Runaway query                │ Add statement_timeout         │
   │ Sudden traffic spike         │ Add caching, read replicas   │
   │ Autovacuum on huge table     │ Tune autovacuum parameters   │
   └──────────────────────────────┴───────────────────────────────┘
```

---

## Scenario 12: Read Replica Returning Stale Data

```
PROBLEM:
────────
User updates their profile (writes to primary)
User refreshes page (reads from replica)
→ Sees OLD profile data! (replication lag)

SOLUTIONS:
──────────

1. READ-YOUR-WRITES consistency:
   After a write, force subsequent reads to go to PRIMARY
   for that specific user (for a short window, e.g., 5 seconds)

   @Service
   public class UserService {
       @Transactional // Writes to primary
       public void updateProfile(Long userId, ProfileDTO dto) {
           userRepo.save(mapToEntity(dto));
           // Set flag: next read from primary
           readAfterWriteCache.set(userId, Duration.ofSeconds(5));
       }
       
       @Transactional(readOnly = true) // Usually reads from replica
       public ProfileDTO getProfile(Long userId) {
           if (readAfterWriteCache.exists(userId)) {
               // Force read from primary (override routing)
               return readFromPrimary(userId);
           }
           return userRepo.findById(userId); // Normal: read from replica
       }
   }

2. CAUSAL CONSISTENCY:
   Track LSN (Log Sequence Number) of last write.
   When reading, wait until replica has caught up to that LSN.
   
   -- After write, get current LSN:
   SELECT pg_current_wal_lsn(); -- Returns: 0/16B3748
   
   -- Before read on replica, wait for LSN:
   SELECT pg_last_wal_replay_lsn(); -- Check if caught up

3. SYNCHRONOUS REPLICATION (prevents lag entirely):
   -- On primary:
   SET synchronous_standby_names = 'replica1';
   -- Write WAITS for replica to confirm before COMMIT
   -- Trade-off: higher write latency
```

---

## Scenario 13: Huge Table Pagination (100M Rows)

```sql
-- PROBLEM: Deep offset pagination
SELECT * FROM events ORDER BY created_at DESC LIMIT 20 OFFSET 5000000;
-- PostgreSQL must skip 5,000,000 rows → takes 10+ seconds

-- SOLUTION: Keyset pagination
-- Page 1:
SELECT * FROM events ORDER BY created_at DESC, id DESC LIMIT 20;

-- Page 2 (use last row's values from page 1):
SELECT * FROM events 
WHERE (created_at, id) < ('2024-01-15 10:30:00', 9999980)
ORDER BY created_at DESC, id DESC 
LIMIT 20;

-- ALWAYS fast regardless of "page number"
-- Uses index: CREATE INDEX idx_events_created_id ON events(created_at DESC, id DESC);

-- For search with filters + pagination:
SELECT * FROM events 
WHERE event_type = 'purchase'
  AND (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC 
LIMIT 20;
```

---

## Scenario 14: Primary Database Failure

```
┌─────────────────────────────────────────────────────────────────┐
│  SCENARIO: Primary DB crashes                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  AUTOMATED FAILOVER SEQUENCE (with Patroni):                     │
│                                                                  │
│  T=0s   Primary goes down                                        │
│  T=1s   Health check fails                                       │
│  T=5s   Confirmed failure (multiple missed heartbeats)           │
│  T=6s   Patroni selects most up-to-date replica                  │
│  T=7s   Replica promoted to primary (pg_promote)                 │
│  T=8s   DNS/endpoint updated to new primary                      │
│  T=10s  Applications reconnect to new primary                    │
│  T=15s  Service restored                                         │
│                                                                  │
│  APPLICATION HANDLING:                                            │
│  • Connection pool detects dead connections                      │
│  • Spring retry: re-establish connections                        │
│  • Failed in-flight transactions: rolled back → client retry     │
│  • PgBouncer mode: transparent reconnection                      │
│                                                                  │
│  DATA LOSS ASSESSMENT:                                           │
│  • Synchronous replication: 0 data loss (RPO=0)                  │
│  • Async replication: possible seconds of data loss              │
│    (transactions committed on primary but not yet replicated)    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions and Answers

### Q1: Two users simultaneously update the same product price. What happens?

**Answer:**

It depends on isolation level and locking:

**READ COMMITTED (PostgreSQL default):**
- TX-A reads price=100, TX-B reads price=100
- TX-A updates to 120, commits
- TX-B updates to 150, commits
- Final: 150 (TX-A's change is lost — "lost update")

**With @Version (Optimistic Lock):**
- TX-A reads price=100, version=1
- TX-B reads price=100, version=1
- TX-A: UPDATE ... SET price=120, version=2 WHERE version=1 → succeeds
- TX-B: UPDATE ... SET price=150, version=2 WHERE version=1 → 0 rows (stale!)
- TX-B gets OptimisticLockException → retry, reads new data

**With SELECT FOR UPDATE (Pessimistic):**
- TX-A: SELECT FOR UPDATE → gets lock
- TX-B: SELECT FOR UPDATE → WAITS
- TX-A updates, commits → lock released
- TX-B proceeds with updated data

### Q2: How do you prevent double-charging a customer?

**Answer:**

Use idempotency keys:
1. Client generates unique key (UUID) per payment intent
2. Server stores key with payment result
3. On retry (same key), return stored result without re-processing

Database enforcement:
```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    amount DECIMAL(10,2),
    status VARCHAR(20)
);
```

Even concurrent identical requests: one INSERT succeeds, other gets unique violation → return existing payment.

### Q3: Your API response time went from 50ms to 5 seconds. What do you check?

**Answer:**

1. **Is it the database?** Check application metrics (response time breakdown)
2. **Active queries**: `pg_stat_activity` — any long-running queries?
3. **Locks**: `pg_locks` — is something blocked?
4. **Connection pool**: HikariCP metrics — pool exhausted?
5. **Query plan change**: `EXPLAIN ANALYZE` — did the optimizer change plans?
6. **Statistics**: Were they invalidated? Run `ANALYZE`
7. **Disk I/O**: Is the database doing full table scans? (shared_buffers insufficient)
8. **Replication lag**: If reading from replica, is it caught up?
9. **Recent changes**: New code deployed? Schema change? Data growth?

---

## Common Mistakes in Interview Answers

| Mistake | Correct Answer |
|---|---|
| "Use SERIALIZABLE isolation for everything" | Use minimum isolation needed; SERIALIZABLE tanks performance |
| "Just add more connections" | More connections = more contention. Optimize queries first. |
| "SELECT then UPDATE is fine" | Race condition! Use atomic UPDATE or SELECT FOR UPDATE |
| "Optimistic locking always better than pessimistic" | Depends on contention level. High contention → pessimistic wins |
| "Read replicas guarantee consistency" | Replicas have lag. Use read-your-writes pattern. |
| "Just retry on deadlock" | Fix the root cause (consistent lock ordering). Retry is a band-aid. |

---

## Best Practices for Scenario Questions

1. **State the problem clearly** — show you understand the race condition
2. **Explain what goes wrong** — describe the exact failure mode
3. **Propose 2-3 solutions** — show breadth of knowledge
4. **Discuss trade-offs** — no solution is perfect
5. **Mention monitoring** — how would you detect this in production?
6. **Consider scale** — does the solution work at high concurrency?

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation Levels
- Topic 21: Locking, Concurrency & MVCC
- Topic 22: PostgreSQL Specifics
- Topic 23: Spring Transactions
- Topic 25: Advanced Database Performance
