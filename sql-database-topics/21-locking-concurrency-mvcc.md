# Topic 21: Locking, Concurrency & MVCC

## Theory

### Why Locking?

When multiple transactions access the same data concurrently, we need mechanisms to prevent data corruption. Locks coordinate access so transactions don't interfere with each other.

```
WITHOUT LOCKING:
┌─────────────────────────────────────────────────────────────┐
│ Account balance = 1000                                       │
│                                                               │
│ Txn A: READ balance → 1000                                   │
│ Txn B: READ balance → 1000                                   │
│ Txn A: balance = 1000 - 200 = 800 → WRITE 800               │
│ Txn B: balance = 1000 - 300 = 700 → WRITE 700               │
│                                                               │
│ Final: 700 (should be 500!) — LOST UPDATE!                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Internal Working — Lock Types

### Lock Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                   LOCK GRANULARITY                            │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  DATABASE LOCK ─── Coarsest (rare, used in maintenance)      │
│       │                                                       │
│  TABLE LOCK ────── Entire table (DDL operations)             │
│       │                                                       │
│  PAGE LOCK ─────── 8KB page (some DBs use this)              │
│       │                                                       │
│  ROW LOCK ──────── Single row (most common for DML)          │
│       │                                                       │
│  COLUMN LOCK ───── Finest (rarely used)                      │
│                                                               │
│  Finer granularity = more concurrency but more overhead      │
│  Coarser granularity = less overhead but more contention     │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Lock Modes

```
┌─────────────────────────────────────────────────────────────┐
│                    LOCK COMPATIBILITY                         │
├──────────────┬────────────────┬──────────────────────────────┤
│              │ Shared (S)     │ Exclusive (X)                │
├──────────────┼────────────────┼──────────────────────────────┤
│ Shared (S)   │ ✓ Compatible   │ ✗ Conflict                  │
│ Exclusive (X) │ ✗ Conflict    │ ✗ Conflict                  │
└──────────────┴────────────────┴──────────────────────────────┘

SHARED LOCK (S):
  - Acquired for READ operations
  - Multiple transactions can hold shared locks on same row
  - Prevents others from WRITING (but not reading)

EXCLUSIVE LOCK (X):
  - Acquired for WRITE operations (INSERT/UPDATE/DELETE)
  - Only ONE transaction can hold an exclusive lock
  - Prevents others from READING and WRITING
```

### Intent Locks

```
┌─────────────────────────────────────────────────────────────┐
│                    INTENT LOCKS                               │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Purpose: Signal intent to lock at a finer granularity       │
│  Prevents table-level locks from conflicting with row locks  │
│                                                               │
│  IS (Intent Shared):                                         │
│     "I intend to read some rows in this table"               │
│                                                               │
│  IX (Intent Exclusive):                                      │
│     "I intend to write some rows in this table"              │
│                                                               │
│  SIX (Shared + Intent Exclusive):                            │
│     "I'm reading the whole table but may update some rows"   │
│                                                               │
│  Example:                                                    │
│  Txn A wants to UPDATE row 5:                                │
│     1. Acquires IX lock on TABLE                             │
│     2. Acquires X lock on ROW 5                              │
│                                                               │
│  Txn B wants to LOCK TABLE EXCLUSIVE:                        │
│     1. Sees IX lock on table → knows someone has row locks   │
│     2. Must WAIT (no need to check every row)                │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Pessimistic vs Optimistic Locking

### Pessimistic Locking

```sql
-- Explicitly lock the row before modifying
-- Other transactions WAIT until lock is released

BEGIN;

-- Acquire exclusive lock on the row
SELECT * FROM products 
WHERE id = 1 
FOR UPDATE;  -- Locks the row!

-- Now safe to modify
UPDATE products 
SET stock = stock - 1 
WHERE id = 1;

COMMIT;  -- Lock released
```

```
┌─────────────────────────────────────────────────────────────┐
│              PESSIMISTIC LOCKING FLOW                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Txn A: SELECT ... FOR UPDATE (acquires lock)                │
│  Txn B: SELECT ... FOR UPDATE (BLOCKED — waits)             │
│  Txn A: UPDATE ... COMMIT (releases lock)                    │
│  Txn B: (unblocked) reads updated row, proceeds             │
│                                                               │
│  USE WHEN:                                                   │
│  • High contention (many txns updating same rows)            │
│  • Short transactions                                        │
│  • Cannot afford retry logic                                 │
│  • Financial/inventory operations                            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

**FOR UPDATE variants (PostgreSQL):**

```sql
-- Basic: lock row, others wait
SELECT * FROM orders WHERE id = 1 FOR UPDATE;

-- NOWAIT: fail immediately if row is locked
SELECT * FROM orders WHERE id = 1 FOR UPDATE NOWAIT;

-- SKIP LOCKED: skip locked rows (useful for job queues)
SELECT * FROM jobs WHERE status = 'pending' 
ORDER BY created_at 
LIMIT 1 
FOR UPDATE SKIP LOCKED;
```

### Optimistic Locking

```sql
-- No database lock acquired
-- Instead, check version at commit time

-- Read with version
SELECT id, name, stock, version FROM products WHERE id = 1;
-- Returns: id=1, stock=10, version=5

-- Update with version check
UPDATE products 
SET stock = 9, version = version + 1 
WHERE id = 1 AND version = 5;

-- If affected_rows = 0 → another transaction modified it!
-- Application must RETRY
```

```
┌─────────────────────────────────────────────────────────────┐
│              OPTIMISTIC LOCKING FLOW                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Txn A: READ row (version = 5)                               │
│  Txn B: READ row (version = 5)                               │
│  Txn A: UPDATE WHERE version = 5 → Success (version now 6)  │
│  Txn B: UPDATE WHERE version = 5 → 0 rows affected!         │
│  Txn B: Detect conflict → RETRY from beginning              │
│                                                               │
│  USE WHEN:                                                   │
│  • Low contention (conflicts are rare)                       │
│  • Long-running transactions                                 │
│  • Distributed systems (no central lock manager)             │
│  • Web applications (read-modify-write over HTTP)            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

**JPA/Hibernate Optimistic Locking:**

```java
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private int stock;
    
    @Version  // Hibernate manages this automatically
    private int version;
}

// When saving, Hibernate generates:
// UPDATE product SET stock=?, version=6 WHERE id=? AND version=5
// If version changed → throws OptimisticLockException
```

---

## Deadlocks

### How Deadlocks Occur

```
┌─────────────────────────────────────────────────────────────┐
│                    DEADLOCK SCENARIO                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Time 1: Txn A locks Row 1 (exclusive)                       │
│  Time 2: Txn B locks Row 2 (exclusive)                       │
│  Time 3: Txn A wants Row 2 → BLOCKED (B holds it)           │
│  Time 4: Txn B wants Row 1 → BLOCKED (A holds it)           │
│                                                               │
│          ┌───────┐         ┌───────┐                         │
│          │ Txn A │ ──waits──▶ Row 2 │ ◀──holds── Txn B      │
│          │       │         └───────┘            │            │
│          │       │ ◀──holds── Row 1 ──waits─────┘            │
│          └───────┘         └───────┘                         │
│                                                               │
│          CIRCULAR WAIT = DEADLOCK!                            │
│                                                               │
│  Resolution: DB detects cycle, aborts one transaction        │
│  (usually the one that has done less work)                   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Deadlock Prevention Strategies

```sql
-- 1. CONSISTENT LOCK ORDERING
-- Always lock resources in the same order (e.g., by ID ascending)
BEGIN;
SELECT * FROM accounts WHERE id IN (1, 5) ORDER BY id FOR UPDATE;
-- Both transactions lock account 1 first, then account 5
-- Prevents circular wait

-- 2. LOCK TIMEOUT
SET lock_timeout = '5s';  -- PostgreSQL
-- Transaction fails if it can't acquire lock within 5 seconds

-- 3. NOWAIT
SELECT * FROM products WHERE id = 1 FOR UPDATE NOWAIT;
-- Fails immediately instead of waiting → application retries

-- 4. KEEP TRANSACTIONS SHORT
-- Shorter transactions = less time holding locks = fewer deadlocks

-- 5. REDUCE LOCK SCOPE
-- Lock only what you need, release as soon as possible
```

---

## MVCC (Multi-Version Concurrency Control)

### Core Concept

```
┌─────────────────────────────────────────────────────────────┐
│                      MVCC PRINCIPLE                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  "Readers don't block writers, writers don't block readers"  │
│                                                               │
│  Instead of locking rows for reads, MVCC keeps MULTIPLE     │
│  VERSIONS of each row. Each transaction sees a SNAPSHOT      │
│  of the database at its start time.                          │
│                                                               │
│  Key insight: When you UPDATE a row, the database doesn't   │
│  overwrite it. It creates a NEW VERSION and marks the old    │
│  one as expired.                                             │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### PostgreSQL MVCC Implementation

```
┌─────────────────────────────────────────────────────────────┐
│            POSTGRESQL ROW VERSIONING                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Every row has hidden system columns:                         │
│  • xmin: Transaction ID that CREATED this row version       │
│  • xmax: Transaction ID that DELETED/UPDATED this version   │
│          (0 if still active)                                  │
│                                                               │
│  Example — UPDATE balance = 800 WHERE id = 1:               │
│                                                               │
│  BEFORE:                                                     │
│  ┌─────┬──────┬─────────┬──────┐                            │
│  │ xmin│ xmax │ id      │ bal  │                            │
│  ├─────┼──────┼─────────┼──────┤                            │
│  │ 100 │ 0    │ 1       │ 1000 │  ← Active version         │
│  └─────┴──────┴─────────┴──────┘                            │
│                                                               │
│  AFTER (Txn 200 does the UPDATE):                            │
│  ┌─────┬──────┬─────────┬──────┐                            │
│  │ 100 │ 200  │ 1       │ 1000 │  ← Old (expired by 200)   │
│  │ 200 │ 0    │ 1       │ 800  │  ← New (created by 200)   │
│  └─────┴──────┴─────────┴──────┘                            │
│                                                               │
│  Txn 150 (started before 200):                               │
│    → Sees row with xmin=100 (1000) because 200 not committed│
│                                                               │
│  Txn 250 (started after 200 committed):                      │
│    → Sees row with xmin=200 (800) because 200 is committed  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Visibility Rules

```
┌─────────────────────────────────────────────────────────────┐
│              SNAPSHOT VISIBILITY RULES                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  A row version is VISIBLE to transaction T if:               │
│                                                               │
│  1. xmin is committed AND xmin < T's snapshot               │
│     (row was created before T started)                       │
│                                                               │
│  2. xmax is either:                                          │
│     - 0 (not deleted/updated yet), OR                        │
│     - Not committed, OR                                      │
│     - Committed but after T's snapshot                       │
│     (row wasn't deleted before T started)                    │
│                                                               │
│  TIMELINE EXAMPLE:                                           │
│  ─────────────────────────────────────────────────▶ time     │
│  Txn 100: INSERT row                                         │
│  Txn 100: COMMIT                                             │
│  Txn 150: BEGIN (snapshot taken here)                        │
│  Txn 200: UPDATE row (creates new version)                   │
│  Txn 200: COMMIT                                             │
│  Txn 150: SELECT → still sees Txn 100's version!            │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### VACUUM — Cleaning Up Old Versions

```
┌─────────────────────────────────────────────────────────────┐
│                    VACUUM PROCESS                             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Problem: MVCC creates dead tuples (old row versions)        │
│  Dead tuples waste space and slow down sequential scans      │
│                                                               │
│  VACUUM:                                                     │
│  • Reclaims space from dead tuples                           │
│  • Marks space as reusable (doesn't return to OS)            │
│  • Updates visibility map                                    │
│  • Updates free space map                                    │
│                                                               │
│  VACUUM FULL:                                                │
│  • Rewrites entire table (returns space to OS)               │
│  • Requires EXCLUSIVE lock (table offline!)                  │
│  • Use only when table is severely bloated                   │
│                                                               │
│  AUTOVACUUM:                                                 │
│  • Background process that runs VACUUM automatically         │
│  • Triggers based on dead tuple thresholds                   │
│  • Critical — never disable in production!                   │
│                                                               │
│  autovacuum_vacuum_threshold = 50 (min dead tuples)          │
│  autovacuum_vacuum_scale_factor = 0.2 (20% of table)        │
│  Triggers when: dead_tuples > threshold + scale_factor*rows  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Lock Monitoring in PostgreSQL

```sql
-- View current locks
SELECT 
    l.locktype,
    l.relation::regclass AS table_name,
    l.mode,
    l.granted,
    l.pid,
    a.query
FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE l.relation IS NOT NULL
ORDER BY l.relation;

-- Find blocked queries
SELECT 
    blocked.pid AS blocked_pid,
    blocked.query AS blocked_query,
    blocking.pid AS blocking_pid,
    blocking.query AS blocking_query,
    NOW() - blocked.query_start AS wait_duration
FROM pg_stat_activity blocked
JOIN pg_locks blocked_locks ON blocked.pid = blocked_locks.pid
JOIN pg_locks blocking_locks ON blocked_locks.locktype = blocking_locks.locktype
    AND blocked_locks.relation = blocking_locks.relation
    AND blocked_locks.pid != blocking_locks.pid
JOIN pg_stat_activity blocking ON blocking_locks.pid = blocking.pid
WHERE NOT blocked_locks.granted;

-- Detect deadlocks (check PostgreSQL log or pg_stat_activity)
-- PostgreSQL logs: "deadlock detected"
-- Tune: deadlock_timeout = '1s' (default)
```

### Job Queue with SKIP LOCKED

```sql
-- Worker picks up next available job without blocking others
BEGIN;

SELECT id, payload 
FROM jobs 
WHERE status = 'pending' 
ORDER BY created_at 
LIMIT 1 
FOR UPDATE SKIP LOCKED;

-- Process the job...
UPDATE jobs SET status = 'processing' WHERE id = ?;

COMMIT;
-- Multiple workers can run concurrently without blocking!
```

### Spring Boot Pessimistic Locking

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "5000")})
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLockTimeout(@Param("id") Long id);
}

@Service
@Transactional
public class InventoryService {
    
    public void decrementStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(productId, quantity);
        }
        
        product.setStock(product.getStock() - quantity);
        // Hibernate auto-flushes at commit
    }
}
```

### Spring Boot Optimistic Locking with Retry

```java
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private int stock;
    
    @Version
    private int version;
}

@Service
public class InventoryService {
    
    @Retryable(
        value = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public void decrementStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow();
        
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(productId, quantity);
        }
        
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
        // If version mismatch → OptimisticLockException → retry
    }
    
    @Recover
    public void decrementStockFallback(OptimisticLockException ex, 
                                        Long productId, int quantity) {
        log.error("Failed to decrement stock after retries: {}", productId);
        throw new StockUpdateFailedException(productId);
    }
}
```

---

## Dry Run — MVCC Visibility

```
Scenario: Two transactions reading and writing the same row

Timeline:
─────────────────────────────────────────────────────▶ time
T=1: Row(id=1, balance=1000) exists, created by committed Txn 50

T=2: Txn 100 BEGINs (snapshot: sees everything committed before T=2)
T=3: Txn 200 BEGINs (snapshot: sees everything committed before T=3)

T=4: Txn 200 executes: UPDATE accounts SET balance = 800 WHERE id = 1
     → Creates new version (xmin=200, balance=800)
     → Marks old version (xmax=200)
     → Txn 200 NOT yet committed

T=5: Txn 100 executes: SELECT balance FROM accounts WHERE id = 1
     → Checks old version: xmin=50 (committed, visible), xmax=200 (not committed)
     → Row is VISIBLE → Returns balance = 1000 ✓

T=6: Txn 200 COMMITs

T=7: Txn 100 executes: SELECT balance FROM accounts WHERE id = 1
     → Under READ COMMITTED: takes new snapshot → sees 800
     → Under REPEATABLE READ: uses original snapshot → still sees 1000!

T=8: Txn 100 COMMITs
T=9: VACUUM can now remove old version (xmin=50, xmax=200) if no other 
     active transactions need it
```

---

## Complexity

| Operation | Lock Overhead |
|-----------|--------------|
| Row-level lock acquisition | O(1) |
| Lock conflict check | O(n) where n = concurrent lock holders |
| Deadlock detection (wait-for graph) | O(V + E) |
| MVCC visibility check | O(1) per tuple |
| VACUUM scan | O(table_size) |

---

## Real Project Usage

### Inventory Management (High Contention)

```java
// Using pessimistic lock for high-contention scenarios
@Service
public class OrderService {
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order placeOrder(Long customerId, List<OrderItemDTO> items) {
        Order order = new Order(customerId);
        
        for (OrderItemDTO item : items) {
            // Pessimistic lock — serialize access to inventory
            Product product = productRepository
                .findByIdForUpdate(item.getProductId())
                .orElseThrow();
            
            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException(product.getId());
            }
            
            product.setStock(product.getStock() - item.getQuantity());
            order.addItem(product, item.getQuantity(), product.getPrice());
        }
        
        return orderRepository.save(order);
    }
}
```

### Distributed Lock with Redis (when DB locking isn't enough)

```java
// For cross-service locking in microservices
@Service
public class DistributedInventoryService {
    
    private final RedissonClient redisson;
    
    public void decrementStock(Long productId, int quantity) {
        RLock lock = redisson.getLock("inventory:lock:" + productId);
        
        try {
            // Try to acquire lock with timeout
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                try {
                    // Critical section
                    Product product = productRepository.findById(productId).orElseThrow();
                    product.setStock(product.getStock() - quantity);
                    productRepository.save(product);
                } finally {
                    lock.unlock();
                }
            } else {
                throw new LockAcquisitionException("Could not acquire lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between pessimistic and optimistic locking?**
A: Pessimistic locking acquires a database lock (SELECT FOR UPDATE) before reading, preventing concurrent access. Optimistic locking reads without locking, then checks at commit time (via version column) whether data changed. Use pessimistic for high contention, optimistic for low contention.

**Q2: Explain MVCC. Why is it important?**
A: MVCC (Multi-Version Concurrency Control) maintains multiple versions of each row. Readers see a consistent snapshot without blocking writers, and writers don't block readers. Only writer-writer conflicts need locking. This dramatically improves concurrency compared to strict lock-based approaches.

**Q3: What is a deadlock? How do you prevent it?**
A: A deadlock occurs when two or more transactions hold locks and each waits for a lock held by another, forming a circular dependency. Prevention: acquire locks in consistent order, keep transactions short, use lock timeouts, use NOWAIT or SKIP LOCKED.

**Q4: What does SELECT FOR UPDATE do?**
A: It acquires an exclusive row-level lock on the selected rows. Other transactions attempting to lock, update, or delete these rows will block until the lock is released (at COMMIT/ROLLBACK). Variants: NOWAIT (fail immediately), SKIP LOCKED (skip locked rows).

**Q5: When would you use SKIP LOCKED?**
A: For job queue patterns where multiple workers process items from the same table. Each worker picks the next unlocked row, skipping rows being processed by other workers. This avoids contention and enables parallel processing.

**Q6: What is VACUUM in PostgreSQL? Why is it important?**
A: VACUUM reclaims space occupied by dead tuples (old row versions from MVCC). Without it, tables bloat and performance degrades. Autovacuum handles this automatically but must be monitored — never disable it in production.

---

## Follow-up Questions and Answers

**Q: How does PostgreSQL detect deadlocks?**
A: PostgreSQL builds a wait-for graph. It periodically (every `deadlock_timeout`, default 1s) checks for cycles. When detected, it aborts the transaction that has done the least work (measured by locks held). The aborted transaction gets ERROR: deadlock detected.

**Q: Can MVCC cause table bloat? How do you address it?**
A: Yes. Every UPDATE creates a new row version, and DELETE only marks rows as dead. If autovacuum can't keep up (long-running transactions, high write rate), table bloat occurs. Address by: tuning autovacuum parameters, avoiding long-running transactions, monitoring dead tuple counts, and using VACUUM FULL as a last resort (requires downtime).

**Q: What's the difference between lock timeout and statement timeout?**
A: `lock_timeout` applies specifically to waiting for locks — if a lock can't be acquired within the timeout, the statement fails. `statement_timeout` applies to total statement execution time regardless of locks. Both should be set in production to prevent runaway transactions.

---

## Common Mistakes

1. **Holding locks during external calls** (HTTP, message queue) — dramatically increases lock duration
2. **Not using consistent lock ordering** — primary cause of deadlocks
3. **Ignoring OptimisticLockException** — must implement retry logic
4. **Using SERIALIZABLE everywhere** — massive performance hit, rarely needed
5. **Long transactions holding locks** — blocks other work, causes lock escalation
6. **Not monitoring autovacuum** — leads to table bloat and degraded performance
7. **SELECT FOR UPDATE without NOWAIT/timeout** — transactions can wait forever
8. **Mixing pessimistic and optimistic in same entity** — confusing, pick one strategy

---

## Best Practices

1. **Keep transactions short** — acquire locks late, release early
2. **Use optimistic locking for web applications** (low contention, long user think-time)
3. **Use pessimistic locking for high-contention hot spots** (inventory, counters)
4. **Always set lock_timeout** in production (prevent indefinite waits)
5. **Order lock acquisition consistently** (prevent deadlocks)
6. **Monitor lock waits and deadlocks** via pg_stat_activity and logs
7. **Tune autovacuum** for write-heavy tables
8. **Use SKIP LOCKED for queue patterns** instead of external message queues

---

## Production Considerations

- **Monitor**: pg_stat_activity (blocked queries), pg_locks, deadlock rate in logs
- **Alert on**: lock wait time > threshold, deadlock count, table bloat ratio
- **Tune**: autovacuum_naptime, autovacuum_vacuum_cost_limit for busy tables
- **Consider**: connection pool size relative to lock contention — more connections can mean more contention
- **Test**: Run load tests to identify deadlock-prone code paths before production

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation Levels
- Topic 19: JPA/Hibernate (@Version, LockModeType)
- Topic 22: PostgreSQL Specifics (VACUUM, WAL details)
- Topic 25: Advanced Database Performance (connection pooling, monitoring)
