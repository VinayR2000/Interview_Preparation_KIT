# 16. Distributed Locking

## Theory

In a multi-instance microservices environment, you sometimes need to ensure only one instance performs a particular operation at a time. Distributed locks provide mutual exclusion across multiple processes/instances.

### Why Distributed Locks?
- Multiple instances of same service running
- Scheduled jobs should run on only one instance
- Resource access must be serialized (e.g., inventory update)
- Leader election (one instance coordinates work)

### Approaches:

| Approach | Tool | Pros | Cons |
|----------|------|------|------|
| Redis locks | Redis SETNX + TTL | Fast, simple | Single point of failure |
| Database locks | SELECT FOR UPDATE | No extra infra | Slow, DB load |
| Zookeeper | Ephemeral nodes | Strong guarantees | Complex infra |
| Redlock | Multiple Redis nodes | Higher availability | Controversial correctness |

### Key Concepts:
- **Lock expiration (TTL)**: Auto-release if holder crashes
- **Lock token**: Unique value to prevent releasing someone else's lock
- **Fencing token**: Monotonically increasing ID to detect stale locks
- **Leader election**: One instance elected to perform work

---

## Internal Working

### Redis Distributed Lock:

```
┌────────────────────────────────────────────────────────┐
│ REDIS DISTRIBUTED LOCK                                  │
│                                                         │
│ Instance A wants to process order-101:                 │
│                                                         │
│ ACQUIRE:                                               │
│   SET lock:order:101 "instance-A-uuid" NX PX 30000    │
│   │                                                    │
│   ├── NX: Only set if NOT EXISTS                      │
│   ├── PX 30000: Expire in 30 seconds (TTL)           │
│   └── Value: Unique token for this lock holder        │
│                                                         │
│ IF response = OK → Lock acquired ✓                    │
│ IF response = nil → Lock held by someone else ✗       │
│                                                         │
│ RELEASE:                                               │
│   Check value matches our token (Lua script):         │
│   if redis.call("get", key) == our_token then         │
│     redis.call("del", key)                            │
│     return 1                                          │
│   end                                                  │
│   return 0                                             │
│                                                         │
│ WHY CHECK TOKEN?                                       │
│   Instance A acquires lock (TTL=30s)                  │
│   Instance A takes 35s to process (lock expired!)     │
│   Instance B acquires lock (it's free now)            │
│   Instance A finishes → tries to release             │
│   Without token check: A deletes B's lock! 💥        │
│   With token check: A sees different value → no delete│
└────────────────────────────────────────────────────────┘
```

### Lock Expiration Problem:

```
TIME →

Instance A:    |--- acquire lock (TTL=30s) ---|---- expired! ----|
                |--- processing (takes 35s) --------- done ----|
                                                        ↑
Instance B:                              |--- acquire lock ---|
                                              (A's lock expired,
                                               B gets it)
                                                        ↑
                                        Instance A releases →
                                        BUT it's B's lock now!

SOLUTION: Fencing Token
                                        
Lock returns monotonically increasing token:
Instance A: token=42 → uses 42 in all writes
Instance B: token=43 → uses 43 in all writes

Storage accepts write only if token ≥ last seen token
Instance A tries to write with token=42 → REJECTED (43 > 42)

This prevents "zombie" lock holders from corrupting data.
```

### Leader Election:

```
┌────────────────────────────────────────────────────────┐
│ LEADER ELECTION                                         │
│                                                         │
│ 3 instances of Scheduler Service                       │
│ Only ONE should execute scheduled jobs                 │
│                                                         │
│ ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│ │Instance 1│  │Instance 2│  │Instance 3│            │
│ │ LEADER ★ │  │ FOLLOWER │  │ FOLLOWER │            │
│ └────┬─────┘  └──────────┘  └──────────┘            │
│      │                                                │
│      │ Holds lock: "scheduler-leader"                │
│      │ Renews every 10s (TTL=30s)                   │
│      │                                                │
│ If Instance 1 crashes:                               │
│   Lock expires after 30s                            │
│   Instance 2 or 3 acquires lock → becomes LEADER    │
│                                                         │
│ Implementation:                                        │
│   @Scheduled(fixedRate = 10000)                       │
│   renewLeadership() → extend TTL                     │
│                                                         │
│   @Scheduled(fixedRate = 10000)                       │
│   tryBecomeLeader() → attempt lock acquisition       │
└────────────────────────────────────────────────────────┘
```

---

## Code

### Redis Distributed Lock with Spring:

```java
@Service
@Slf4j
public class RedisDistributedLock {

    private final StringRedisTemplate redisTemplate;

    /**
     * Acquire a distributed lock.
     * @return lock token if acquired, empty if lock is held
     */
    public Optional<String> acquire(String lockKey, Duration ttl) {
        String token = UUID.randomUUID().toString();
        
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, token, ttl);
        
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock acquired: key={}, token={}", lockKey, token);
            return Optional.of(token);
        }
        
        log.debug("Lock not acquired: key={} (held by another)", lockKey);
        return Optional.empty();
    }

    /**
     * Release lock only if we still hold it (token matches).
     * Uses Lua script for atomicity.
     */
    public boolean release(String lockKey, String token) {
        String script = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """;
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            List.of(lockKey),
            token
        );
        
        boolean released = result != null && result == 1;
        log.debug("Lock release: key={}, released={}", lockKey, released);
        return released;
    }

    /**
     * Extend lock TTL (for long operations).
     */
    public boolean extend(String lockKey, String token, Duration newTtl) {
        String script = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("pexpire", KEYS[1], ARGV[2])
            else
                return 0
            end
            """;
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            List.of(lockKey),
            token, String.valueOf(newTtl.toMillis())
        );
        
        return result != null && result == 1;
    }
}
```

### Using the Lock in Business Logic:

```java
@Service
public class InventoryService {

    private final RedisDistributedLock distributedLock;

    public boolean reserveStock(UUID productId, int quantity) {
        String lockKey = "lock:inventory:" + productId;
        Duration lockTtl = Duration.ofSeconds(10);

        Optional<String> lockToken = distributedLock.acquire(lockKey, lockTtl);
        
        if (lockToken.isEmpty()) {
            throw new ConcurrentModificationException(
                "Could not acquire lock for product " + productId);
        }

        try {
            // Critical section — only one instance executes this
            Product product = productRepository.findById(productId).orElseThrow();
            
            if (product.getStock() < quantity) {
                return false;  // Insufficient stock
            }
            
            product.setStock(product.getStock() - quantity);
            productRepository.save(product);
            return true;
            
        } finally {
            // Always release lock
            distributedLock.release(lockKey, lockToken.get());
        }
    }
}
```

### Leader Election for Scheduled Jobs:

```java
@Service
@Slf4j
public class SchedulerLeaderElection {

    private final RedisDistributedLock lock;
    private volatile String leaderToken;
    private volatile boolean isLeader = false;

    private static final String LEADER_KEY = "leader:scheduler";
    private static final Duration LEADER_TTL = Duration.ofSeconds(30);
    private static final Duration RENEW_INTERVAL = Duration.ofSeconds(10);

    @Scheduled(fixedRate = 10000)  // Every 10s
    public void maintainLeadership() {
        if (isLeader) {
            // Try to renew
            boolean renewed = lock.extend(LEADER_KEY, leaderToken, LEADER_TTL);
            if (!renewed) {
                log.warn("Lost leadership! Another instance took over.");
                isLeader = false;
                leaderToken = null;
            }
        } else {
            // Try to become leader
            Optional<String> token = lock.acquire(LEADER_KEY, LEADER_TTL);
            if (token.isPresent()) {
                log.info("Became leader!");
                isLeader = true;
                leaderToken = token.get();
            }
        }
    }

    @Scheduled(cron = "0 * * * * *")  // Every minute
    public void executeScheduledJob() {
        if (!isLeader) {
            return;  // Only leader executes
        }
        
        log.info("Executing scheduled job (I am the leader)");
        // ... scheduled work here
    }
}
```

### Database-Based Lock (Alternative):

```java
@Repository
public class DatabaseLockRepository {

    private final JdbcTemplate jdbc;

    @Transactional
    public boolean tryAcquire(String lockName, String owner, Duration ttl) {
        // Try to insert lock (unique constraint prevents duplicates)
        try {
            jdbc.update("""
                INSERT INTO distributed_locks (lock_name, owner, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (lock_name) DO UPDATE
                SET owner = EXCLUDED.owner, expires_at = EXCLUDED.expires_at
                WHERE distributed_locks.expires_at < NOW()
                """,
                lockName, owner, Instant.now().plus(ttl)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void release(String lockName, String owner) {
        jdbc.update(
            "DELETE FROM distributed_locks WHERE lock_name = ? AND owner = ?",
            lockName, owner
        );
    }
}
```

---

## Interview Questions

1. **Why do we need distributed locks?**
   - Multiple instances of same service can't use JVM-level locks (different processes). Need coordination across instances for exclusive resource access, leader election, preventing duplicate processing of scheduled jobs.

2. **Redis SET NX PX — what does it do?**
   - SET key value NX PX milliseconds. NX: only set if Not eXists (atomic check-and-set). PX: expiration in milliseconds (auto-release if holder crashes). Together: atomic lock acquisition with TTL.

3. **Why do we need a lock token?**
   - To safely release only YOUR lock. Without token: Instance A's lock expires, Instance B acquires, Instance A deletes B's lock. With token: Release only if token matches (Lua script for atomicity).

4. **What is the Redlock algorithm?**
   - Acquire lock on majority (N/2+1) of Redis nodes. If majority acquired within timeout → lock is valid. Tolerates minority node failures. Controversial — Martin Kleppmann argued it's not safe for correctness.

5. **How to handle lock holder crash?**
   - TTL ensures lock auto-expires. Trade-off: short TTL → false lock releases during GC pause. Long TTL → long wait if holder crashes. Use lock renewal (heartbeat) for long operations.

6. **Database lock vs Redis lock?**
   - Redis: Fast, purpose-built, low latency. Database: No extra infrastructure, stronger durability. Use Redis for high-frequency locking. Use database for rare operations where you already have a DB.

---

## Common Mistakes

1. **No TTL** — Lock held forever if holder crashes
2. **Releasing without token check** — Releasing someone else's lock
3. **Non-atomic check-and-delete** — GET + DEL is not atomic, use Lua
4. **Too short TTL** — Lock expires during processing (GC pause, slow operation)
5. **Busy-waiting for lock** — Spinning wastes CPU; use backoff or pub/sub notification
6. **Over-using distributed locks** — Often idempotency or optimistic locking is simpler

---

## Best Practices

1. **Lock token + Lua script** — Atomic, safe release
2. **TTL with renewal** — Short TTL + periodic renewal for long operations
3. **Lock scope** — Lock only what's necessary (per-entity, not global)
4. **Fallback strategy** — What to do if lock can't be acquired (retry, reject, queue)
5. **Fencing tokens** — For correctness-critical operations
6. **Monitor lock contention** — High contention = design problem
7. **Prefer alternatives** — Optimistic locking, idempotency, queue-based serialization
