# Topic 15: Transactions, ACID, and Isolation Levels

## Theory

### Transaction

A transaction is a logical unit of work that either completes entirely or not at all. It groups one or more SQL operations that must succeed or fail together.

```sql
BEGIN;
    UPDATE accounts SET balance = balance - 500 WHERE id = 1;
    UPDATE accounts SET balance = balance + 500 WHERE id = 2;
COMMIT;
-- Either BOTH updates happen, or NEITHER does
```

### ACID Properties

```
┌─────────────────────────────────────────────────────────────────────┐
│ A - ATOMICITY                                                        │
│     All or nothing. If any part fails, entire transaction rolls back.│
│     Example: Transfer $500 — both debit AND credit happen, or neither│
├─────────────────────────────────────────────────────────────────────┤
│ C - CONSISTENCY                                                      │
│     Database moves from one valid state to another.                  │
│     All constraints, rules, cascades satisfied after commit.         │
│     Example: Balance can't go negative (CHECK constraint honored)    │
├─────────────────────────────────────────────────────────────────────┤
│ I - ISOLATION                                                        │
│     Concurrent transactions don't interfere with each other.         │
│     Each transaction sees a consistent view of the database.         │
│     Example: Two simultaneous transfers don't lose money            │
├─────────────────────────────────────────────────────────────────────┤
│ D - DURABILITY                                                       │
│     Once committed, data survives crashes/power failures.            │
│     Achieved via WAL (Write-Ahead Log).                             │
│     Example: Committed transfer survives server reboot              │
└─────────────────────────────────────────────────────────────────────┘
```

### Isolation Levels

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Performance |
|-------|-----------|-------------------|--------------|-------------|
| Read Uncommitted | ✓ Possible | ✓ Possible | ✓ Possible | Fastest |
| Read Committed | ✗ Prevented | ✓ Possible | ✓ Possible | Fast |
| Repeatable Read | ✗ Prevented | ✗ Prevented | ✓ Possible* | Medium |
| Serializable | ✗ Prevented | ✗ Prevented | ✗ Prevented | Slowest |

*PostgreSQL's Repeatable Read actually prevents phantoms too (uses MVCC snapshot).

### Isolation Problems Explained

```
DIRTY READ:
┌─────────────────────────────────────────────────────┐
│ Txn A: UPDATE balance SET amount = 0 (not committed)│
│ Txn B: SELECT amount → reads 0 (uncommitted data!)  │
│ Txn A: ROLLBACK → balance back to original          │
│ Txn B used wrong data!                              │
└─────────────────────────────────────────────────────┘

NON-REPEATABLE READ:
┌─────────────────────────────────────────────────────┐
│ Txn A: SELECT salary WHERE id=1 → 50000            │
│ Txn B: UPDATE salary=60000 WHERE id=1; COMMIT;     │
│ Txn A: SELECT salary WHERE id=1 → 60000 (changed!) │
│ Same query, different results within same txn!       │
└─────────────────────────────────────────────────────┘

PHANTOM READ:
┌─────────────────────────────────────────────────────┐
│ Txn A: SELECT COUNT(*) WHERE dept='Eng' → 5        │
│ Txn B: INSERT INTO employees (dept='Eng'); COMMIT;  │
│ Txn A: SELECT COUNT(*) WHERE dept='Eng' → 6        │
│ New row appeared (phantom)!                          │
└─────────────────────────────────────────────────────┘

LOST UPDATE:
┌─────────────────────────────────────────────────────┐
│ Txn A: Read balance = 1000                          │
│ Txn B: Read balance = 1000                          │
│ Txn A: Write balance = 1000 + 100 = 1100; COMMIT   │
│ Txn B: Write balance = 1000 + 200 = 1200; COMMIT   │
│ Balance = 1200 (A's +100 is LOST!)                  │
│ Should be: 1000 + 100 + 200 = 1300                 │
└─────────────────────────────────────────────────────┘

WRITE SKEW:
┌─────────────────────────────────────────────────────┐
│ Constraint: At least 1 doctor on call               │
│ Doctors on call: [Alice, Bob]                       │
│ Txn A: Count on_call=2; Alice goes off call; COMMIT │
│ Txn B: Count on_call=2; Bob goes off call; COMMIT  │
│ Result: 0 doctors on call! (violated constraint)    │
│ Each saw 2, thought it was safe to remove 1         │
└─────────────────────────────────────────────────────┘
```

---

## Internal Working

### MVCC (Multi-Version Concurrency Control)

PostgreSQL uses MVCC — readers never block writers, writers never block readers.

```
┌─────────────────────────────────────────────────────────────┐
│                    MVCC in PostgreSQL                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│ Each row has hidden system columns:                          │
│   xmin: Transaction ID that created this row version         │
│   xmax: Transaction ID that deleted/updated this version     │
│                                                               │
│ Transaction 100 inserts row:                                 │
│   Row Version 1: xmin=100, xmax=NULL → VISIBLE              │
│                                                               │
│ Transaction 200 updates row:                                 │
│   Row Version 1: xmin=100, xmax=200 → OLD (dead)           │
│   Row Version 2: xmin=200, xmax=NULL → NEW (current)       │
│                                                               │
│ Transaction 150 (started before 200) sees:                   │
│   Row Version 1: xmin=100 < 150 ✓, xmax=200 > 150 ✓       │
│   → Row Version 1 is VISIBLE (doesn't see the update!)      │
│                                                               │
│ Transaction 300 (started after 200) sees:                    │
│   Row Version 2: xmin=200 < 300 ✓, xmax=NULL ✓             │
│   → Row Version 2 is VISIBLE                                │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Snapshot Isolation (PostgreSQL Repeatable Read)

```
┌─────────────────────────────────────────────────────────────┐
│ Transaction starts → Takes SNAPSHOT of all active txn IDs    │
│                                                               │
│ Visibility rule for each row version:                        │
│   1. xmin must be committed AND before snapshot              │
│   2. xmax must be NULL or not yet committed at snapshot time │
│                                                               │
│ Effect: Transaction sees CONSISTENT view from start time     │
│ No dirty reads, no non-repeatable reads, no phantom reads   │
│                                                               │
│ Trade-off: May get serialization error on write conflicts    │
│ "could not serialize access due to concurrent update"        │
└─────────────────────────────────────────────────────────────┘
```

### WAL (Write-Ahead Logging) for Durability

```
┌──────────────────────────────────────────────────────────────┐
│                    WRITE FLOW                                  │
│                                                                │
│  1. Write change to WAL buffer (in memory)                    │
│  2. fsync WAL to disk (guarantees durability)                │
│  3. Modify data page in shared buffers (memory)              │
│  4. Eventually flush dirty pages to disk (checkpoint)        │
│                                                                │
│  On crash recovery:                                           │
│  1. Read WAL from last checkpoint                            │
│  2. Replay all committed changes not yet in data files       │
│  3. Database is consistent again                             │
│                                                                │
│  Key insight: Data in WAL = guaranteed durable               │
│  Data only in shared buffers = lost on crash (replayed from WAL)│
└──────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Transaction Basics

```sql
-- Basic transaction
BEGIN;
    INSERT INTO orders (customer_id, total) VALUES (1, 99.99);
    INSERT INTO order_items (order_id, product_id, qty) VALUES (currval('orders_id_seq'), 5, 2);
    UPDATE products SET stock = stock - 2 WHERE id = 5;
COMMIT;

-- With error handling (in application)
BEGIN;
    UPDATE accounts SET balance = balance - 500 WHERE id = 1;
    -- Check if balance went negative
    IF (SELECT balance FROM accounts WHERE id = 1) < 0 THEN
        ROLLBACK;
    ELSE
        UPDATE accounts SET balance = balance + 500 WHERE id = 2;
        COMMIT;
    END IF;

-- Savepoints
BEGIN;
    INSERT INTO orders (...) VALUES (...);
    SAVEPOINT sp1;
    
    INSERT INTO order_items (...) VALUES (...);  -- might fail
    -- If it fails:
    ROLLBACK TO SAVEPOINT sp1;
    -- Order still exists, only item rolled back
    
    INSERT INTO order_items (...) VALUES (...);  -- try different values
COMMIT;
```

### Setting Isolation Levels

```sql
-- Per transaction
BEGIN ISOLATION LEVEL SERIALIZABLE;
    SELECT * FROM inventory WHERE product_id = 5;
    UPDATE inventory SET quantity = quantity - 1 WHERE product_id = 5;
COMMIT;

-- Per session
SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- Check current level
SHOW transaction_isolation;
```

### Locking Patterns

```sql
-- Pessimistic locking (SELECT FOR UPDATE)
BEGIN;
    SELECT * FROM products WHERE id = 5 FOR UPDATE;
    -- Row is locked — other transactions wait
    UPDATE products SET stock = stock - 1 WHERE id = 5;
COMMIT;

-- SELECT FOR UPDATE NOWAIT (fail immediately if locked)
BEGIN;
    SELECT * FROM products WHERE id = 5 FOR UPDATE NOWAIT;
    -- Throws error immediately if row is locked by another txn
COMMIT;

-- SELECT FOR UPDATE SKIP LOCKED (skip locked rows)
BEGIN;
    SELECT * FROM task_queue
    WHERE status = 'pending'
    ORDER BY created_at
    LIMIT 1
    FOR UPDATE SKIP LOCKED;
    -- Gets next available unlocked task
COMMIT;

-- Advisory locks (application-level)
SELECT pg_advisory_lock(12345);  -- Acquire lock
-- ... do work ...
SELECT pg_advisory_unlock(12345);  -- Release lock
```

### Optimistic Locking (Application Level)

```sql
-- Using version column
-- Read:
SELECT id, name, price, version FROM products WHERE id = 5;
-- Returns: (5, 'Widget', 29.99, 3)

-- Update with version check:
UPDATE products 
SET price = 34.99, version = version + 1
WHERE id = 5 AND version = 3;  -- Only succeeds if no one else changed it

-- If 0 rows affected → conflict! Someone else updated first.
```

### Deadlock Scenario and Prevention

```sql
-- DEADLOCK SCENARIO:
-- Transaction A:
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;  -- Locks row 1
UPDATE accounts SET balance = balance + 100 WHERE id = 2;  -- Waits for row 2...

-- Transaction B (concurrent):
BEGIN;
UPDATE accounts SET balance = balance - 50 WHERE id = 2;   -- Locks row 2
UPDATE accounts SET balance = balance + 50 WHERE id = 1;   -- Waits for row 1...
-- DEADLOCK! A waits for B's lock on 2, B waits for A's lock on 1

-- PREVENTION: Always lock in consistent order
-- Transaction A:
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;  -- Lock 1 first
UPDATE accounts SET balance = balance + 100 WHERE id = 2;  -- Then lock 2

-- Transaction B:
BEGIN;
UPDATE accounts SET balance = balance + 50 WHERE id = 1;   -- Lock 1 first (same order!)
UPDATE accounts SET balance = balance - 50 WHERE id = 2;   -- Then lock 2
-- No deadlock possible! B waits for A to finish with row 1.
```

---

## Dry Run

### Lost Update Problem and Solution

```sql
-- Initial: product.stock = 10

-- WITHOUT proper isolation (Lost Update):
-- Time 1: Txn A reads stock = 10
-- Time 2: Txn B reads stock = 10
-- Time 3: Txn A writes stock = 10 - 3 = 7, COMMIT
-- Time 4: Txn B writes stock = 10 - 2 = 8, COMMIT  ← A's change LOST!
-- Result: stock = 8 (should be 5)

-- SOLUTION 1: SELECT FOR UPDATE
-- Time 1: Txn A: SELECT stock FROM products WHERE id=1 FOR UPDATE → 10 (row locked)
-- Time 2: Txn B: SELECT stock FROM products WHERE id=1 FOR UPDATE → WAITS...
-- Time 3: Txn A: UPDATE stock = 10 - 3 = 7; COMMIT (lock released)
-- Time 4: Txn B: SELECT finally returns → 7 (sees committed value)
-- Time 5: Txn B: UPDATE stock = 7 - 2 = 5; COMMIT
-- Result: stock = 5 ✓

-- SOLUTION 2: Atomic UPDATE (no read needed)
UPDATE products SET stock = stock - 3 WHERE id = 1;
-- This is atomic — reads current value and updates in one step
-- No lost update possible!
```

---

## Complexity

| Operation | Lock Type | Duration |
|-----------|-----------|----------|
| SELECT (Read Committed) | No lock (MVCC) | None |
| SELECT (Serializable) | Predicate lock | Until commit |
| SELECT FOR UPDATE | Row exclusive | Until commit |
| UPDATE/DELETE | Row exclusive | Until commit |
| INSERT | Row exclusive | Until commit |
| CREATE INDEX | Share lock | Until done |
| ALTER TABLE | Access Exclusive | Until done |
| VACUUM | Share Update Exclusive | Until done |

---

## Real Project Usage

### Spring Boot Transaction Management

```java
@Service
public class OrderService {
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order createOrder(OrderRequest request) {
        // All operations in one transaction
        Order order = orderRepository.save(new Order(request));
        
        for (ItemRequest item : request.getItems()) {
            // Pessimistic lock on product
            Product product = productRepository.findByIdWithLock(item.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            
            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException(product.getId());
            }
            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
            
            orderItemRepository.save(new OrderItem(order, product, item.getQuantity()));
        }
        return order;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderEvent(Long orderId, String event) {
        // Independent transaction — committed even if parent rolls back
        auditRepository.save(new AuditLog(orderId, event));
    }
}

// Repository with pessimistic lock
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
}
```

### Handling Serialization Failures (Retry Pattern)

```java
@Service
public class TransferService {
    
    private static final int MAX_RETRIES = 3;
    
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        int attempts = 0;
        while (true) {
            try {
                doTransfer(fromId, toId, amount);
                return;
            } catch (CannotSerializeTransactionException e) {
                attempts++;
                if (attempts >= MAX_RETRIES) throw e;
                // Wait with exponential backoff
                Thread.sleep((long) Math.pow(2, attempts) * 100);
            }
        }
    }
    
    @Transactional(isolation = Isolation.SERIALIZABLE)
    private void doTransfer(Long fromId, Long toId, BigDecimal amount) {
        accountRepository.debit(fromId, amount);
        accountRepository.credit(toId, amount);
    }
}
```

---

## Interview Questions & Answers

**Q1: Explain ACID with a real example (bank transfer).**

Transfer $500 from Account A to Account B:
- **Atomicity**: If debit succeeds but credit fails (network error), the entire transfer is rolled back. A's money isn't lost.
- **Consistency**: After transfer, total money in system is unchanged. No constraint violated (balance doesn't go negative).
- **Isolation**: If concurrent transfer happens, they don't interfere. No double-spending.
- **Durability**: Once you see "Transfer complete", even if server crashes, the transfer is permanent.

**Q2: What isolation level would you use for an inventory system?**

For an e-commerce inventory system:
- **Read Committed** (default) + **SELECT FOR UPDATE** on the stock check/decrement
- This prevents lost updates while allowing high concurrency
- Alternative: Use atomic `UPDATE stock = stock - 1 WHERE stock > 0` which is simpler and avoids explicit locking

**Q3: How does PostgreSQL prevent phantom reads in Repeatable Read?**

PostgreSQL uses **snapshot isolation** (MVCC-based). At transaction start, it takes a snapshot of all committed transactions. It only sees rows committed before the snapshot. New inserts by other transactions are invisible regardless.

**Q4: Explain deadlock detection and prevention.**

**Detection**: PostgreSQL checks for lock wait cycles periodically. When found, it kills one transaction (victim) with deadlock error.

**Prevention**:
1. Always lock resources in consistent order (e.g., by ID ascending)
2. Keep transactions short
3. Use lock timeouts
4. Use NOWAIT or SKIP LOCKED when appropriate

**Q5: What is the "self-invocation problem" in Spring @Transactional?**

Spring uses AOP proxies for @Transactional. If method A calls method B on the same class, the proxy is bypassed — B's @Transactional annotation is ignored.

Solution: Inject the service into itself, use `TransactionTemplate`, or extract to a separate service.

---

## Common Mistakes

1. **Long-running transactions** — hold locks, cause contention
2. **Not handling serialization failures** — retry logic needed for Serializable
3. **Using Serializable everywhere** — massive performance penalty
4. **Forgetting self-invocation problem** in Spring (proxy bypass)
5. **Not using `FOR UPDATE`** when reading then writing (lost update)
6. **Ignoring deadlock possibility** — always handle potential deadlock errors

---

## Best Practices

1. **Keep transactions short** — minimal work between BEGIN and COMMIT
2. **Use Read Committed** (default) for most workloads
3. **Use Serializable** only when write skew must be prevented
4. **Use `UPDATE ... WHERE` atomically** instead of SELECT then UPDATE
5. **Lock resources in consistent order** to prevent deadlocks
6. **Set `lock_timeout`** and `statement_timeout` to prevent indefinite waits
7. **Implement retry logic** for serialization/deadlock failures
8. **Use optimistic locking** (version column) for low-contention scenarios

---

## Production Considerations

1. **idle_in_transaction_session_timeout**: Kill idle transactions holding locks
2. **log_lock_waits**: Log when a lock wait exceeds `deadlock_timeout`
3. **Monitor**: `pg_stat_activity` for long-running transactions
4. **Connection pooling**: PgBouncer transaction mode for efficient connection use
5. **Vacuum**: MVCC creates dead tuples; vacuum reclaims space

```sql
-- Production safety settings
SET idle_in_transaction_session_timeout = '60s';
SET lock_timeout = '10s';
SET statement_timeout = '30s';

-- Monitor locks
SELECT pid, mode, relation::regclass, granted
FROM pg_locks
WHERE NOT granted;  -- Who's waiting?
```

---

## Related Topics
- [Topic 22: Locking & Concurrency](#)
- [Topic 23: MVCC](#)
- [Topic 24: Deadlocks](#)
- [Topic 42: Spring Transactions](#)
