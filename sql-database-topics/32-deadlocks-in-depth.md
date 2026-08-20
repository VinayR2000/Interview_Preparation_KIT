# Topic 32: Deadlocks — In-Depth

## Theory

### What is a Deadlock?

A deadlock occurs when two or more transactions are waiting for each other to release locks, creating a circular dependency where none can proceed.

```
┌─────────────────────────────────────────────────────────────────┐
│                    DEADLOCK                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  FOUR CONDITIONS (all must be true for deadlock):                │
│                                                                  │
│  1. MUTUAL EXCLUSION                                             │
│     → Resource can be held by only one transaction               │
│                                                                  │
│  2. HOLD AND WAIT                                                │
│     → Transaction holds resources while waiting for others       │
│                                                                  │
│  3. NO PREEMPTION                                                │
│     → Resources cannot be forcibly taken from a transaction      │
│                                                                  │
│  4. CIRCULAR WAIT                                                │
│     → Transaction A waits for B, B waits for A (cycle)           │
│                                                                  │
│  BREAK ANY ONE CONDITION → DEADLOCK IMPOSSIBLE                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Classic Deadlock Scenario

```
┌─────────────────────────────────────────────────────────────────┐
│              DEADLOCK EXAMPLE                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Transaction A: Transfer $100 from Account 1 → Account 2         │
│  Transaction B: Transfer $50 from Account 2 → Account 1          │
│                                                                  │
│  Time    TX-A                          TX-B                      │
│  ────    ─────────────────────────     ─────────────────────     │
│  T1      LOCK Account 1 (row lock)                               │
│  T2                                   LOCK Account 2 (row lock)  │
│  T3      Try LOCK Account 2           Try LOCK Account 1        │
│          → BLOCKED (TX-B holds it)    → BLOCKED (TX-A holds it) │
│                                                                  │
│          ┌───────────────────────────────────┐                   │
│          │   TX-A waits for TX-B             │                   │
│          │   TX-B waits for TX-A             │                   │
│          │   → CIRCULAR WAIT → DEADLOCK!     │                   │
│          └───────────────────────────────────┘                   │
│                                                                  │
│  WAIT-FOR GRAPH:                                                 │
│         TX-A ──────waits──────▶ TX-B                             │
│          ▲                        │                              │
│          └────────waits───────────┘                              │
│          (Cycle detected = deadlock!)                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Internal Working — Deadlock Detection in PostgreSQL

```
┌─────────────────────────────────────────────────────────────────┐
│         POSTGRESQL DEADLOCK DETECTION                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PostgreSQL uses a DEADLOCK DETECTOR background process:         │
│                                                                  │
│  1. Transaction requests a lock                                  │
│  2. If lock not available → transaction waits                    │
│  3. After deadlock_timeout (default 1 second):                   │
│     → Deadlock detector builds wait-for graph                    │
│     → Checks for cycles in the graph                            │
│  4. If cycle found:                                              │
│     → One transaction is chosen as "victim"                      │
│     → Victim is rolled back                                      │
│     → Victim receives: ERROR: deadlock detected                  │
│     → Other transaction(s) proceed                               │
│                                                                  │
│  VICTIM SELECTION:                                               │
│  PostgreSQL picks the transaction that is "easiest to rollback"  │
│  (least work done / most recently blocked)                       │
│                                                                  │
│  KEY PARAMETER:                                                   │
│  deadlock_timeout = 1s (default)                                 │
│  • Before this: PostgreSQL just waits (no detection)             │
│  • After this: detector runs (CPU cost)                          │
│  • Too low: detector runs too often (overhead)                   │
│  • Too high: transactions wait too long before detection         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Lock Types That Can Deadlock

```
┌─────────────────────────────────────────────────────────────────┐
│              LOCK TYPES IN POSTGRESQL                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ROW-LEVEL LOCKS (most common source of deadlocks):              │
│  • FOR UPDATE: Exclusive row lock (blocks other FOR UPDATE)      │
│  • FOR NO KEY UPDATE: Less restrictive (doesn't block FK checks) │
│  • FOR SHARE: Shared lock (blocks FOR UPDATE)                    │
│  • FOR KEY SHARE: Least restrictive shared lock                  │
│                                                                  │
│  TABLE-LEVEL LOCKS:                                              │
│  • ACCESS SHARE: SELECT (compatible with everything except       │
│    ACCESS EXCLUSIVE)                                              │
│  • ROW SHARE: SELECT FOR UPDATE                                  │
│  • ROW EXCLUSIVE: INSERT/UPDATE/DELETE                            │
│  • ACCESS EXCLUSIVE: ALTER TABLE, DROP TABLE, VACUUM FULL        │
│                                                                  │
│  INDEX LOCKS:                                                    │
│  • During index scans (typically short-lived)                    │
│  • Can deadlock during concurrent index builds                   │
│                                                                  │
│  ADVISORY LOCKS:                                                 │
│  • Application-controlled (pg_advisory_lock)                     │
│  • Can deadlock if used without consistent ordering              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Diagram — Deadlock Scenarios

### Scenario 1: Row-Level Deadlock (Most Common)

```
Table: accounts (id, balance)
Account 1: balance = $1000
Account 2: balance = $500

TX-A: UPDATE accounts SET balance = balance - 100 WHERE id = 1;
      UPDATE accounts SET balance = balance + 100 WHERE id = 2;

TX-B: UPDATE accounts SET balance = balance - 50 WHERE id = 2;
      UPDATE accounts SET balance = balance + 50 WHERE id = 1;

Timeline:
T1: TX-A: UPDATE ... WHERE id = 1  → Locks row 1 ✓
T2: TX-B: UPDATE ... WHERE id = 2  → Locks row 2 ✓
T3: TX-A: UPDATE ... WHERE id = 2  → WAITS (TX-B has row 2)
T4: TX-B: UPDATE ... WHERE id = 1  → WAITS (TX-A has row 1)
    → DEADLOCK!
```

### Scenario 2: Foreign Key Deadlock

```
Parent: orders (id)
Child: order_items (id, order_id REFERENCES orders)

TX-A: INSERT INTO order_items (order_id=1, ...) 
      → Takes SHARE lock on orders row 1

TX-B: UPDATE orders SET status='SHIPPED' WHERE id = 1
      → Needs EXCLUSIVE lock on orders row 1 → WAITS for TX-A's SHARE

TX-A: UPDATE orders SET total = 150 WHERE id = 1
      → Needs EXCLUSIVE lock → WAITS for TX-B (which is waiting for TX-A)
      → DEADLOCK!
```

### Scenario 3: Index Gap Lock Deadlock (Less Common in PostgreSQL)

```
-- More common in MySQL with gap locks
-- PostgreSQL MVCC mostly avoids this, but can happen with
-- concurrent INSERTs on same index page

TX-A: INSERT INTO users (email) VALUES ('aaa@test.com');
TX-B: INSERT INTO users (email) VALUES ('aab@test.com');
-- Both try to insert adjacent index entries
-- Can deadlock on index page split
```

---

## Code — Prevention Strategies

### Strategy 1: Consistent Lock Ordering (BEST)

```java
// ALWAYS acquire locks in a predictable, consistent order
// Common approach: sort by primary key (ascending)

@Service
public class TransferService {

    @Transactional
    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // ALWAYS lock lower ID first
        Long firstId = Math.min(fromAccountId, toAccountId);
        Long secondId = Math.max(fromAccountId, toAccountId);
        
        Account first = accountRepo.findByIdForUpdate(firstId);   // Lock first
        Account second = accountRepo.findByIdForUpdate(secondId); // Lock second
        
        // Now determine which is "from" and which is "to"
        Account from = fromAccountId.equals(firstId) ? first : second;
        Account to = toAccountId.equals(firstId) ? first : second;
        
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccountId);
        }
        
        from.debit(amount);
        to.credit(amount);
    }
}

// Why this works:
// TX-A: transfer(1, 2, $100) → locks 1, then 2
// TX-B: transfer(2, 1, $50)  → locks 1, then 2 (same order!)
// TX-B waits for TX-A to release lock on 1
// No circular wait → No deadlock!
```

### Strategy 2: Lock Timeout

```java
@Service
public class OrderService {

    @Transactional
    public void processOrder(Long orderId) {
        // Set a short lock timeout — fail fast instead of waiting forever
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '5s'")
                     .executeUpdate();
        
        try {
            Order order = orderRepo.findByIdForUpdate(orderId);
            // ... process order
        } catch (PessimisticLockException e) {
            // Lock timeout — another transaction holds the lock
            throw new ConflictException("Order is being processed by another transaction");
        }
    }
}
```

### Strategy 3: Retry with Backoff

```java
@Service
public class ResilientTransferService {

    @Retryable(
        value = {DeadlockLoserDataAccessException.class, CannotAcquireLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        Account from = accountRepo.findByIdForUpdate(fromId);
        Account to = accountRepo.findByIdForUpdate(toId);
        from.debit(amount);
        to.credit(amount);
    }
    
    @Recover
    public void transferFallback(DeadlockLoserDataAccessException e, 
                                  Long fromId, Long toId, BigDecimal amount) {
        log.error("Transfer failed after retries: from={}, to={}, amount={}", 
                  fromId, toId, amount);
        throw new TransferFailedException("Unable to process transfer", e);
    }
}
```

### Strategy 4: Reduce Lock Duration

```java
// BAD: Long transaction (holds locks during HTTP call)
@Transactional
public void processOrderBad(Long orderId) {
    Order order = orderRepo.findByIdForUpdate(orderId); // Lock acquired
    PaymentResult result = paymentGateway.charge(order); // HTTP call = 2-5 seconds!
    order.setStatus(result.isSuccess() ? "PAID" : "FAILED");
    // Lock held for entire HTTP call duration → high deadlock risk
}

// GOOD: Minimize lock duration
public void processOrderGood(Long orderId) {
    // Step 1: Read without lock
    Order order = orderRepo.findById(orderId).orElseThrow();
    
    // Step 2: External call (no lock held)
    PaymentResult result = paymentGateway.charge(order);
    
    // Step 3: Short transaction with lock
    updateOrderStatus(orderId, result);
}

@Transactional
private void updateOrderStatus(Long orderId, PaymentResult result) {
    Order order = orderRepo.findByIdForUpdate(orderId); // Lock briefly
    order.setStatus(result.isSuccess() ? "PAID" : "FAILED");
    // Lock released immediately after commit
}
```

### Strategy 5: Use Optimistic Locking (Avoid Locks Entirely)

```java
@Entity
public class Product {
    @Id
    private Long id;
    private Integer stock;
    
    @Version  // Optimistic lock — no actual DB lock!
    private Integer version;
}

@Service
public class InventoryService {
    
    @Retryable(value = OptimisticLockException.class, maxAttempts = 3)
    @Transactional
    public void decrementStock(Long productId, int quantity) {
        Product product = productRepo.findById(productId).orElseThrow();
        
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(productId);
        }
        
        product.setStock(product.getStock() - quantity);
        // On flush: UPDATE products SET stock=?, version=version+1
        //           WHERE id=? AND version=?
        // If 0 rows affected → OptimisticLockException → retry
    }
}
// No actual locks → No deadlocks possible!
// Trade-off: May need retries under high contention
```

---

## Dry Run — Deadlock Detection

```
Setup: deadlock_timeout = 1s

T=0.000s: TX-A starts
           BEGIN;
           UPDATE accounts SET balance = balance - 100 WHERE id = 1;
           → Acquires RowExclusiveLock on row 1 ✓

T=0.010s: TX-B starts
           BEGIN;
           UPDATE accounts SET balance = balance - 50 WHERE id = 2;
           → Acquires RowExclusiveLock on row 2 ✓

T=0.020s: TX-A executes:
           UPDATE accounts SET balance = balance + 100 WHERE id = 2;
           → Requests lock on row 2
           → Row 2 locked by TX-B
           → TX-A enters WAIT state
           → Added to wait queue for row 2

T=0.030s: TX-B executes:
           UPDATE accounts SET balance = balance + 50 WHERE id = 1;
           → Requests lock on row 1
           → Row 1 locked by TX-A
           → TX-B enters WAIT state
           → Added to wait queue for row 1

T=0.030s to T=1.030s: Both transactions waiting...
           (deadlock_timeout = 1 second)

T=1.030s: Deadlock detector activated for TX-B (waited > 1s)
           → Builds wait-for graph:
             TX-A → waits_for → TX-B
             TX-B → waits_for → TX-A
           → CYCLE DETECTED!

T=1.031s: PostgreSQL selects TX-B as victim
           → TX-B is rolled back
           → TX-B receives:
             ERROR: deadlock detected
             DETAIL: Process 5678 waits for ShareLock on transaction 1234;
                     blocked by process 1234.
             Process 1234 waits for ShareLock on transaction 5678;
                     blocked by process 5678.
             HINT: See server log for query details.

T=1.031s: TX-A's wait resolves
           → Row 2 no longer locked (TX-B rolled back)
           → TX-A acquires lock on row 2
           → TX-A continues: UPDATE succeeds

T=1.032s: TX-A completes
           COMMIT;
           → Both updates applied for TX-A
           → Locks released

Result:
  TX-A: Success (transfer completed)
  TX-B: Failed (must retry)
```

---

## Monitoring & Detecting Deadlocks

```sql
-- Check for deadlocks in PostgreSQL logs
-- Log message format:
-- LOG: process 1234 detected deadlock while waiting for ShareLock

-- Monitor deadlock count
SELECT datname, deadlocks FROM pg_stat_database;

-- Find blocked queries RIGHT NOW
SELECT 
    blocked.pid AS blocked_pid,
    blocked.query AS blocked_query,
    blocking.pid AS blocking_pid,
    blocking.query AS blocking_query,
    blocked.wait_event_type,
    now() - blocked.query_start AS blocked_duration
FROM pg_stat_activity blocked
JOIN pg_locks blocked_locks ON blocked.pid = blocked_locks.pid
JOIN pg_locks blocking_locks ON blocked_locks.locktype = blocking_locks.locktype
    AND blocked_locks.relation = blocking_locks.relation
    AND blocked_locks.pid != blocking_locks.pid
JOIN pg_stat_activity blocking ON blocking_locks.pid = blocking.pid
WHERE blocked_locks.granted = false
    AND blocking_locks.granted = true;

-- View all current locks
SELECT 
    l.pid,
    l.locktype,
    l.mode,
    l.granted,
    a.query,
    a.state,
    now() - a.query_start AS duration
FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE a.state != 'idle'
ORDER BY a.query_start;
```

### Alerting

```yaml
# Prometheus alert rule
- alert: PostgreSQLDeadlocks
  expr: rate(pg_stat_database_deadlocks[5m]) > 0
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Deadlocks detected in {{ $labels.datname }}"
    description: "{{ $value }} deadlocks/sec in last 5 minutes"
```

---

## Complexity

| Operation | Notes |
|---|---|
| Deadlock detection | O(V + E) where V = transactions, E = wait edges (graph cycle detection) |
| Wait-for graph construction | O(V + E) traversal of lock table |
| deadlock_timeout overhead | Only triggered after timeout (not continuous) |
| Consistent lock ordering | O(n log n) for sorting n resources before locking |
| Retry with backoff | O(retries × transaction_cost) |

---

## Real Project Usage

### E-Commerce Inventory with Deadlock Prevention

```java
@Service
@Slf4j
public class InventoryService {

    @Autowired
    private ProductRepository productRepo;

    /**
     * Purchase multiple products atomically.
     * Uses consistent ordering to prevent deadlocks.
     */
    @Transactional
    public void purchaseProducts(List<PurchaseItem> items) {
        // PREVENT DEADLOCK: Sort items by product ID before locking
        List<PurchaseItem> sorted = items.stream()
            .sorted(Comparator.comparing(PurchaseItem::getProductId))
            .collect(Collectors.toList());
        
        for (PurchaseItem item : sorted) {
            // Lock in consistent order (ascending product ID)
            Product product = productRepo.findByIdForUpdate(item.getProductId())
                .orElseThrow(() -> new NotFoundException(item.getProductId()));
            
            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException(
                    product.getId(), product.getStock(), item.getQuantity());
            }
            
            product.setStock(product.getStock() - item.getQuantity());
        }
        // All products decremented successfully → commit
    }
}

// Why sorted order matters:
// Cart A: [Product 5, Product 3, Product 8]
// Cart B: [Product 8, Product 3]
//
// Without sorting:
//   TX-A locks: 5, then 3, then tries 8
//   TX-B locks: 8, then tries 3
//   → TX-A waits for 8 (held by TX-B)
//   → TX-B waits for 3 (held by TX-A)
//   → DEADLOCK!
//
// With sorting:
//   TX-A locks: 3, then 5, then 8
//   TX-B locks: 3 (WAITS for TX-A), then 8
//   → TX-B just waits for TX-A to finish
//   → NO DEADLOCK!
```

### Deadlock-Resistant Batch Processing

```java
@Service
public class BatchProcessingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Process records in batches with deadlock prevention.
     * Each batch is a separate transaction to minimize lock duration.
     */
    public void processInBatches(List<Long> recordIds) {
        // Sort IDs for consistent locking
        List<Long> sorted = new ArrayList<>(recordIds);
        Collections.sort(sorted);
        
        // Process in small batches
        List<List<Long>> batches = Lists.partition(sorted, 100);
        
        for (List<Long> batch : batches) {
            processWithRetry(batch);
        }
    }
    
    @Retryable(value = DeadlockLoserDataAccessException.class, maxAttempts = 3)
    @Transactional
    public void processWithRetry(List<Long> batchIds) {
        // Short transaction, consistent order, retry on deadlock
        for (Long id : batchIds) {
            jdbcTemplate.update(
                "UPDATE records SET status = 'PROCESSED', updated_at = NOW() " +
                "WHERE id = ? AND status = 'PENDING'", id);
        }
    }
}
```

---

## Interview Questions and Answers

### Q1: What is a deadlock? How does PostgreSQL handle it?

**Answer:**

A deadlock is a circular dependency where two or more transactions are each waiting for locks held by the other.

PostgreSQL handles it with a **deadlock detector**:
1. When a transaction waits longer than `deadlock_timeout` (default 1s), the detector activates
2. It builds a wait-for graph and checks for cycles
3. If a cycle is found, one transaction is chosen as "victim" and rolled back
4. The victim receives `ERROR: deadlock detected`
5. The other transaction(s) proceed

The application should catch this error and retry the transaction.

### Q2: How do you prevent deadlocks?

**Answer:**

1. **Consistent lock ordering** (most effective): Always acquire locks in the same order (e.g., by ascending ID). Breaks the circular-wait condition.

2. **Reduce lock duration**: Keep transactions short. No external HTTP calls while holding locks.

3. **Use optimistic locking** (@Version): No actual locks → no deadlocks. Handle conflicts with retry.

4. **Lock timeout**: Set `lock_timeout` to fail fast rather than wait indefinitely.

5. **Retry mechanism**: If deadlock occurs, catch the exception and retry with exponential backoff.

6. **Reduce lock granularity**: Lock only what you need. Use `SELECT FOR NO KEY UPDATE` instead of `FOR UPDATE` when you don't modify the key.

### Q3: What's the difference between a deadlock and lock contention?

**Answer:**

**Lock contention**: Transaction A holds a lock, Transaction B waits. Eventually A releases, B proceeds. This is NORMAL and expected.

**Deadlock**: Transaction A waits for B AND Transaction B waits for A. NEITHER can proceed. Requires intervention (detection + victim rollback).

Contention is a performance issue (slow). Deadlock is a correctness issue (stuck).

### Q4: Can optimistic locking cause deadlocks?

**Answer:**

No. Optimistic locking (`@Version`) doesn't acquire actual database locks. It works by:
1. Reading the entity (no lock)
2. On update: `WHERE id=? AND version=?`
3. If version mismatch → throw exception, retry

Since no locks are held, there's nothing to create a circular wait. However, optimistic locking can cause **excessive retries** under high contention (livelock), which is a different problem.

### Q5: You see deadlock errors in production logs. How do you investigate and fix?

**Answer:**

**Investigation:**
1. Check PostgreSQL logs for deadlock details (shows exact queries and lock types)
2. Identify the tables/rows involved
3. Identify the transactions (source code) that caused it
4. Look for pattern: Are they always the same tables? Same ordering issue?

**Fix:**
1. Implement consistent lock ordering for the identified operations
2. If ordering is complex, add retry with backoff
3. Reduce transaction scope (shorter lock hold time)
4. Consider if optimistic locking is sufficient for the use case

**Monitoring:**
- Track `pg_stat_database.deadlocks` over time
- Alert on any non-zero deadlock rate
- Log slow queries and lock waits

---

## Follow-up Questions and Answers

### Q: Can SELECT statements cause deadlocks?

**Answer:**

Plain `SELECT` in PostgreSQL: NO (due to MVCC, readers don't block writers).

But `SELECT FOR UPDATE`: YES. It acquires row-level exclusive locks, which can create circular dependencies just like UPDATE statements.

Also, DDL statements (`ALTER TABLE`) acquire `AccessExclusiveLock` which can deadlock with other DDL or long-running queries that hold `AccessShareLock`.

### Q: What is `deadlock_timeout` and should you change it?

**Answer:**

`deadlock_timeout` (default 1s) is how long a transaction waits BEFORE the deadlock detector checks for cycles.

- Setting lower (e.g., 100ms): Faster detection but more overhead (detector runs more often even for normal lock waits)
- Setting higher (e.g., 5s): Less overhead but transactions wait longer when deadlocked
- Recommendation: Keep at 1s for most workloads. Lower only if you have frequent deadlocks and need faster recovery.

### Q: How do deadlocks differ between PostgreSQL and MySQL?

**Answer:**

| Aspect | PostgreSQL | MySQL (InnoDB) |
|---|---|---|
| Detection | After deadlock_timeout | Immediate (every lock wait) |
| Gap locks | No gap locks (MVCC) | Has gap locks (more deadlock-prone) |
| Phantom reads | Prevented by MVCC | Prevented by gap locks (deadlock risk) |
| Index lock | Minimal | More aggressive (next-key locking) |
| Result | PostgreSQL has fewer deadlocks in general due to MVCC |

---

## Common Mistakes

| Mistake | Impact | Fix |
|---|---|---|
| No consistent lock ordering | Frequent deadlocks | Sort by PK before locking |
| HTTP call inside transaction | Lock held during I/O | Move external calls outside tx |
| Ignoring deadlock exceptions | Silent failures | Catch + retry with backoff |
| Locking entire table | Maximum contention | Lock only needed rows |
| Not monitoring deadlocks | Issues go unnoticed | Alert on pg_stat_database.deadlocks |
| Using SELECT FOR UPDATE everywhere | Unnecessary contention | Use optimistic locking where possible |
| Large batch in single transaction | Long lock holds | Split into smaller batches |
| No lock_timeout | Infinite waits | Set reasonable timeout |

---

## Best Practices

1. **Always use consistent lock ordering** — sort by primary key before locking multiple rows
2. **Keep transactions short** — minimize lock hold duration
3. **Prefer optimistic locking** for low-contention scenarios
4. **Implement retry logic** for deadlock exceptions (Spring @Retryable)
5. **Set lock_timeout** to fail fast (5-10 seconds for OLTP)
6. **Monitor deadlocks** — any non-zero rate deserves investigation
7. **Reduce lock scope** — use `FOR NO KEY UPDATE` when possible
8. **Avoid locking during external calls** — no HTTP/message sends inside locks
9. **Batch process with small transactions** — each batch gets its own short transaction
10. **Log deadlock context** — include correlation IDs for debugging

---

## Production Considerations

### PostgreSQL Configuration

```sql
-- postgresql.conf settings for deadlock management
deadlock_timeout = 1s          -- Default, works for most cases
lock_timeout = 10s             -- Max wait for any lock (application-wide)
statement_timeout = 30s        -- Kill queries running too long
log_lock_waits = on            -- Log when a query waits longer than deadlock_timeout
```

### Application-Level Configuration

```yaml
# Spring Boot retry configuration
spring:
  retry:
    enabled: true

# Custom retry for database operations
app:
  database:
    retry:
      max-attempts: 3
      initial-delay: 100ms
      multiplier: 2
      max-delay: 2000ms
```

### Deadlock Investigation Checklist

```
□ Check PostgreSQL logs for deadlock detail messages
□ Identify the SQL statements involved
□ Identify which transactions (application code) generate those SQL
□ Check if lock ordering is consistent
□ Check if transactions are holding locks during slow operations
□ Verify connection pool isn't contributing (REQUIRES_NEW exhaustion)
□ Consider if optimistic locking is viable for the use case
□ Implement fix + add retry as safety net
□ Add monitoring/alerting for deadlocks
□ Test fix under concurrent load
```

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation Levels
- Topic 21: Locking, Concurrency & MVCC
- Topic 22: PostgreSQL Specifics
- Topic 23: Spring Transactions (@Transactional)
- Topic 27: SQL Interview Scenarios
