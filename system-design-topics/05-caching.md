# Caching

## Why Caching?

### Theory
- Store frequently accessed data in a faster storage layer
- Reduce latency: Memory access (~100ns) vs Disk (~10ms) vs Network (~100ms)
- Reduce load on backend systems (database, external APIs)
- Improve throughput and user experience

### Diagram
```
┌────────┐      ┌───────┐  Cache Hit   ┌──────────┐
│ Client │─────→│ Cache │─────────────→│ Response │
└────────┘      └───┬───┘              └──────────┘
                    │ Cache Miss
                    ▼
               ┌──────────┐
               │ Database │
               └──────────┘
```

### Cache Hit vs Miss
- **Cache Hit**: Data found in cache → return immediately (fast)
- **Cache Miss**: Data not in cache → fetch from source → store in cache → return (slow)
- **Hit Ratio**: hits / (hits + misses) — target > 80-90%

---

## Caching Strategies

### Cache-Aside (Lazy Loading)

```
Read:
1. Application checks cache
2. If HIT → return cached data
3. If MISS → read from DB → store in cache → return

Write:
1. Write to database
2. Invalidate/delete cache entry
```

**Pros**: Only caches what's needed, cache failure doesn't break system
**Cons**: Cache miss penalty (extra round trip), potential stale data

### Read-Through

```
1. Application reads from cache
2. Cache itself fetches from DB on miss
3. Cache stores and returns data

Application ──→ Cache ──→ Database
                  ↑           │
                  └───────────┘ (cache manages fetching)
```

**Pros**: Application code simpler, cache manages fetching
**Cons**: Initial request always slow, cache library must support

### Write-Through

```
Write:
1. Application writes to cache
2. Cache synchronously writes to database
3. Acknowledge to application

Application ──→ Cache ──→ Database (synchronous)
```

**Pros**: Cache always consistent with DB, no stale reads
**Cons**: Higher write latency (two writes), cache may store rarely-read data

### Write-Back (Write-Behind)

```
Write:
1. Application writes to cache
2. Cache acknowledges immediately
3. Cache asynchronously writes to database (batched)

Application ──→ Cache ──→ (async batch) ──→ Database
```

**Pros**: Very fast writes, batching reduces DB load
**Cons**: Risk of data loss if cache fails before persisting, complexity

### Write-Around

```
Write:
1. Application writes directly to database (bypass cache)
2. Cache populated only on reads (cache-aside)
```

**Pros**: Cache not polluted with write-once data
**Cons**: Read after write causes cache miss

### Strategy Comparison

| Strategy | Read Performance | Write Performance | Consistency | Data Loss Risk |
|----------|-----------------|-------------------|-------------|----------------|
| Cache-Aside | Good (after warm-up) | N/A (writes go to DB) | Eventual | Low |
| Read-Through | Good | N/A | Eventual | Low |
| Write-Through | Excellent | Slower (sync) | Strong | Very Low |
| Write-Back | Excellent | Fastest | Eventual | Higher |
| Write-Around | Slow first read | Fast | Eventual | Low |

---

## TTL (Time to Live)

### Theory
- Duration a cache entry remains valid
- After TTL expires, entry is evicted or marked stale
- Trade-off: Short TTL → more consistent, more misses; Long TTL → faster, more stale

### TTL Guidelines
| Data Type | TTL | Reason |
|-----------|-----|--------|
| Static content (images, CSS) | Hours to days | Rarely changes |
| User profiles | 5-15 minutes | Moderate change frequency |
| Product catalog | 1-5 minutes | Updated periodically |
| Real-time data (stock prices) | Seconds | Changes constantly |
| Session data | 30 minutes | Security/freshness |

---

## Cache Eviction Policies

| Policy | Description | Use Case |
|--------|-------------|----------|
| LRU (Least Recently Used) | Evict entry not accessed for longest time | General purpose (most common) |
| LFU (Least Frequently Used) | Evict entry accessed fewest times | Hot/cold data patterns |
| FIFO (First In First Out) | Evict oldest entry | Simple, predictable |
| TTL-based | Evict expired entries | Time-sensitive data |
| Random | Evict random entry | Simple, low overhead |

### LRU Implementation
```
Doubly Linked List + HashMap

HashMap: key → node reference (O(1) lookup)
LinkedList: head = most recently used, tail = least recently used

GET(key):
  If found → move to head → return value
  If not found → cache miss

PUT(key, value):
  If exists → update value, move to head
  If not exists:
    If cache full → remove tail (LRU entry)
    Add new node at head
```

---

## Cache Invalidation

### Strategies
| Strategy | Description | When to Use |
|----------|-------------|-------------|
| TTL expiry | Auto-expire after time | When eventual consistency is OK |
| Explicit delete | Delete cache on write | Strong consistency needed |
| Publish/Subscribe | Event triggers invalidation | Distributed systems |
| Version-based | Increment version, old versions invalid | Concurrent access |

### Why Cache Invalidation is Hard
> "There are only two hard things in CS: cache invalidation and naming things."

Problems:
- Race conditions between write and invalidate
- Distributed invalidation (multiple cache nodes)
- Thundering herd on invalidation (many requests hit DB simultaneously)

---

## Redis

### Theory
- In-memory data structure store
- Used as cache, message broker, queue
- Supports: Strings, Lists, Sets, Sorted Sets, Hashes, Streams

### Redis as Cache
```
SET user:123 '{"name":"John","email":"john@example.com"}' EX 300
GET user:123

# With conditional set
SET user:123 value NX  # Only set if not exists
SET user:123 value XX  # Only set if exists
```

### Redis Data Structures for Caching
| Structure | Use Case | Example |
|-----------|----------|---------|
| String | Simple key-value | User profile cache |
| Hash | Object with fields | `HSET user:123 name "John" email "j@e.com"` |
| Sorted Set | Leaderboards, ranking | `ZADD leaderboard 100 "player1"` |
| List | Recent items, queue | `LPUSH recent:user:123 "page1"` |
| Set | Unique items, tags | `SADD user:123:roles "ADMIN" "USER"` |

### Redis Persistence
| Mode | Description | Trade-off |
|------|-------------|-----------|
| RDB | Point-in-time snapshots | Fast recovery, some data loss |
| AOF | Append every write operation | Durable, larger files |
| RDB + AOF | Both | Best durability, more I/O |
| None | No persistence | Pure cache, fastest |

---

## Distributed Cache

### Architecture
```
┌───────────┐     ┌──────────────────────────────────┐
│ App Server│────→│       Cache Cluster              │
│     1     │     │  ┌─────┐  ┌─────┐  ┌─────┐     │
└───────────┘     │  │Node1│  │Node2│  │Node3│     │
                  │  │A-G  │  │H-N  │  │O-Z  │     │
┌───────────┐     │  └─────┘  └─────┘  └─────┘     │
│ App Server│────→│     (Partitioned by key hash)    │
│     2     │     └──────────────────────────────────┘
└───────────┘
```

### Redis Cluster
- Data partitioned across multiple nodes (16384 hash slots)
- Each key hashed to a slot: `CRC16(key) % 16384`
- Automatic failover with replicas
- Client-side routing (MOVED redirects)

### Consistent Hashing
- Problem: Simple `hash % N` breaks when nodes added/removed (rehashes everything)
- Solution: Hash ring — only K/N keys need redistribution
- Virtual nodes ensure even distribution

---

## Cache Stampede (Thundering Herd)

### Problem
```
Cache entry expires
→ 1000 concurrent requests find cache miss
→ All 1000 hit database simultaneously
→ Database overwhelmed
```

### Solutions
| Solution | How |
|----------|-----|
| Lock/Mutex | First request locks, fetches, caches. Others wait. |
| Early expiry | Proactively refresh before TTL expires |
| Stale-while-revalidate | Return stale data, refresh in background |
| Jitter | Add random offset to TTL (prevent simultaneous expiry) |
| Probabilistic early expiry | Randomly refresh before TTL (XFetch algorithm) |

### Mutex Pattern
```
GET key
If MISS:
  If SETNX lock:key (acquire lock):
    Fetch from DB
    SET key value EX ttl
    DEL lock:key
  Else:
    Wait/retry → GET key
```

---

## Hot Keys

### Problem
- One key gets disproportionate traffic (celebrity post, flash sale)
- Single cache node becomes bottleneck

### Solutions
| Solution | Description |
|----------|-------------|
| Local cache | App-level cache for hot keys |
| Key replication | Duplicate across multiple nodes (key:1, key:2, ...) |
| Read replicas | Multiple Redis replicas for read scaling |
| Key splitting | Break hot value into sub-keys |

---

## Caching Levels

```
┌─────────────────────────────────────────────────────┐
│ L1: Browser Cache (Client-side)                      │
│   - HTTP cache headers (Cache-Control, ETag)         │
│   - Service Worker cache                             │
├─────────────────────────────────────────────────────┤
│ L2: CDN Cache (Edge)                                 │
│   - Static assets, API responses                     │
│   - Geographically distributed                       │
├─────────────────────────────────────────────────────┤
│ L3: Application Cache (In-process)                   │
│   - Caffeine, Guava, ConcurrentHashMap               │
│   - No network hop, fastest                          │
├─────────────────────────────────────────────────────┤
│ L4: Distributed Cache (Redis/Memcached)              │
│   - Shared across instances                          │
│   - Network hop, but still fast                      │
├─────────────────────────────────────────────────────┤
│ L5: Database Query Cache                             │
│   - Built into DB (MySQL query cache)                │
│   - Buffer pool / shared buffers                     │
└─────────────────────────────────────────────────────┘
```

---

## Interview Questions

**Q: How do you decide what to cache?**
> Cache data that is: read-heavy (high read:write ratio), expensive to compute/fetch, tolerant of eventual consistency, accessed by many users (shared). Don't cache: frequently changing data, user-specific data that's rarely re-read, large objects with low hit rate.

**Q: How do you ensure cache consistency with the database?**
> Options ranked by consistency:
> 1. Write-through (strongest, higher latency)
> 2. Cache-aside with invalidation on write (good balance)
> 3. TTL-based expiry (eventual, simplest)
> 4. Event-driven invalidation via CDC/messaging (scalable)
> Choose based on your consistency requirements.

**Q: What happens when your Redis cache goes down?**
> 1. Traffic floods database (thundering herd)
> 2. Mitigation: Circuit breaker to reject overflow, graceful degradation
> 3. Prevention: Redis Cluster with replicas, Redis Sentinel for failover
> 4. Design services to survive cache failure (slower, not broken)
> 5. Rate limit requests to database during recovery

**Q: Cache-aside vs Read-through — when to use which?**
> Cache-aside: When you want full control over caching logic, when cache failure shouldn't break the system, when you need to cache from multiple data sources. Read-through: When you want simpler application code, when caching logic is uniform, when using a cache library/framework that supports it.

**Q: How would you cache for a system with 1M requests/sec?**
> Multi-level caching: CDN for static, local in-process cache (Caffeine) for hot data, distributed Redis cluster for shared data. Use consistent hashing for key distribution. Monitor hit ratios and tune TTLs. Handle hot keys with local caching + key replication.

---

## Common Mistakes
- Caching without TTL (stale forever)
- Not handling cache failures gracefully (system breaks on cache down)
- Caching too much (memory exhaustion, low hit ratio)
- Not monitoring hit/miss ratios
- Using cache as primary data store (it's volatile!)
- Ignoring cache stampede problem
- Not considering cache warm-up after deployment/restart

---

## Best Practices
- Set appropriate TTLs for all cache entries
- Monitor hit ratio (target > 80%)
- Implement graceful degradation on cache failure
- Use multi-level caching (local + distributed)
- Add jitter to TTLs to prevent simultaneous expiry
- Size cache based on working set, not total data
- Implement cache warm-up for critical paths
- Use compression for large cached values

---

## Production Considerations
- Memory sizing: Monitor eviction rates, adjust maxmemory
- Redis Cluster vs Sentinel: Cluster for sharding, Sentinel for HA
- Monitoring: Memory usage, hit ratio, eviction rate, connection count
- Backup strategy for Redis (RDB snapshots for disaster recovery)
- Network latency between app and cache (same AZ)
- Security: Redis AUTH, TLS, VPC isolation
- Cache versioning for schema changes (key prefix with version)

---

## Related Topics
- Redis (deep dive)
- CDN
- Database Indexing
- Consistent Hashing
- Cache Stampede patterns
