# ElastiCache ⭐⭐⭐

## Theory

ElastiCache is managed in-memory caching (Redis or Memcached). Sub-millisecond latency for frequently accessed data. Reduces database load and improves application response times.

---

## Diagram

### Cache-Aside Pattern (Most Common for Spring Boot)

```
Spring Boot Application
    │
    ├── 1. Check Redis cache
    │   ├── Cache HIT → Return data immediately (sub-ms)
    │   └── Cache MISS → Continue to step 2
    │
    ├── 2. Query RDS PostgreSQL
    │
    ├── 3. Store result in Redis (with TTL)
    │
    └── 4. Return data to client

Request flow:
GET /api/users/123
    ↓
┌─────────────────────┐
│    Redis Cache       │
│    Key: user:123     │──→ HIT? Return immediately
└─────────┬───────────┘
          │ MISS
          ↓
┌─────────────────────┐
│    RDS PostgreSQL    │
│    SELECT * FROM ... │──→ Get data
└─────────┬───────────┘
          │
          ↓
    Store in Redis (TTL: 5 min)
    Return to client
```

### Architecture

```
┌──────────────── VPC ─────────────────┐
│                                       │
│  ┌─── Private Subnet ───┐           │
│  │  Spring Boot (ECS)    │           │
│  │       ↓       ↓       │           │
│  │    Redis    RDS       │           │
│  │  (ElastiCache)  (PostgreSQL)      │
│  └────────────────────────┘          │
│                                       │
│  Redis Cluster (Multi-AZ):           │
│  ├── Primary (AZ-1a): Reads + Writes│
│  └── Replica (AZ-1b): Reads only    │
│      (automatic failover)            │
└───────────────────────────────────────┘
```

---

## Code

### Spring Boot + Redis (Spring Cache)

```java
// Enable caching
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .withCacheConfiguration("users", 
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("products",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1)))
            .build();
    }
}

// Service with caching
@Service
public class UserService {
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow();  // Only called on cache miss
    }

    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);  // Updates cache with new value
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);  // Removes from cache
    }
}
```

```yaml
# application-production.yml
spring:
  data:
    redis:
      host: my-redis.xxx.cache.amazonaws.com
      port: 6379
      ssl:
        enabled: true
      timeout: 2000ms
```

---

## Interview Questions and Answers

**Q: When would you use ElastiCache Redis with Spring Boot?**
> (1) Caching frequently read, rarely changing data (user profiles, product catalogs), (2) Session storage in distributed systems (multiple instances share sessions), (3) Rate limiting (API throttling), (4) Leaderboards/counters (Redis sorted sets), (5) Distributed locks (Redisson), (6) Pub/sub for real-time features.

**Q: How do you handle cache invalidation?**
> Strategies: (1) TTL-based: Set expiration, accept slightly stale data. (2) Write-through: Update cache on every write. (3) Cache-aside with eviction: Evict on update, next read populates cache. (4) Event-driven: Database change → publish event → invalidate cache. For Spring Boot: Use `@CacheEvict` on update/delete methods + reasonable TTLs.

**Q: Redis vs Memcached — when to use which?**
> Redis: Rich data structures (lists, sets, sorted sets, hashes), persistence, replication, Pub/Sub, Lua scripting, Multi-AZ failover. Memcached: Simple key-value only, multi-threaded (better for simple caching with many cores), no persistence. **Almost always choose Redis** — it can do everything Memcached does plus much more.

---

## Best Practices

1. **Always set TTL** — prevent unbounded cache growth
2. **Multi-AZ with replicas** for production (automatic failover)
3. **Encryption** in transit (TLS) and at rest (KMS)
4. **Private subnet** — never expose Redis to internet
5. **Connection pooling** — use Lettuce (default in Spring Boot) with pool settings
6. **Monitor** evictions, cache hit ratio, memory usage via CloudWatch
7. **Graceful degradation** — if Redis is down, fall through to database (not error)

---

## Related Topics
- → [08. RDS](./08-rds.md)
- → [09. DynamoDB](./09-dynamodb.md)
- → [12. ECS and EKS](./12-ecs-eks.md)
