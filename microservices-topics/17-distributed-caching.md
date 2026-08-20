# 17. Distributed Caching

## Theory

Distributed caching stores frequently accessed data in a shared, in-memory cache (Redis, Memcached) to reduce database load and improve response times across multiple service instances.

### Caching Strategies:

| Strategy | Read | Write | Use Case |
|----------|------|-------|----------|
| Cache-Aside | App checks cache first, loads from DB if miss | App updates DB, invalidates cache | Most common, flexible |
| Read-Through | Cache loads from DB on miss (transparent) | - | Simpler app code |
| Write-Through | - | Write to cache + DB simultaneously | Strong consistency |
| Write-Behind | - | Write to cache, async flush to DB | High write throughput |

### Cache Invalidation:
- **TTL (Time-To-Live)**: Automatic expiration after N seconds
- **Event-based**: Invalidate when data changes (via Kafka events)
- **Manual**: Application explicitly invalidates on update

---

## Internal Working

### Cache-Aside Pattern (Most Common):

```
┌────────────────────────────────────────────────────────┐
│ CACHE-ASIDE (Lazy Loading)                              │
│                                                         │
│ READ:                                                  │
│ ┌─────────┐    1. Check cache     ┌──────────┐       │
│ │  Client │ ──────────────────────→│  Redis   │       │
│ └────┬────┘                        └────┬─────┘       │
│      │                                  │              │
│      │                         Cache HIT? │            │
│      │              ┌───── YES ──────────┘            │
│      │              │              │                   │
│      │              ↓         Cache MISS               │
│      │         Return data         │                   │
│      │                             ↓                   │
│      │                    2. Query Database            │
│      │                    ┌───────────┐               │
│      │                    │ PostgreSQL│               │
│      │                    └─────┬─────┘               │
│      │                          │                      │
│      │               3. Store in cache                 │
│      │                    ┌──────────┐                │
│      │                    │  Redis   │                │
│      │                    └──────────┘                │
│      │                          │                      │
│      ←──────── 4. Return data ──┘                     │
│                                                         │
│ WRITE:                                                 │
│ 1. Update database                                    │
│ 2. Delete from cache (invalidate)                     │
│    → Next read will reload fresh data                 │
└────────────────────────────────────────────────────────┘
```

### Write-Through vs Write-Behind:

```
WRITE-THROUGH:
  Write arrives
    │
    ├──→ Write to cache  ──→ Cache updated immediately
    │
    └──→ Write to DB     ──→ DB updated immediately
    
  Both happen synchronously. Strong consistency but slower writes.

WRITE-BEHIND (Write-Back):
  Write arrives
    │
    └──→ Write to cache only (return immediately)
              │
              └──→ Async batch write to DB (later)
    
  Fast writes but risk of data loss if cache crashes before flush.
```

### Cache Invalidation Strategies:

```
STRATEGY 1: TTL-based
  Redis SET user:123 "{...}" EX 300  (expires in 5 min)
  Pro: Simple, automatic cleanup
  Con: Stale data for up to TTL duration

STRATEGY 2: Event-based invalidation
  User Service updates user → publishes UserUpdatedEvent
  → All services listening invalidate their cached copy
  Pro: Near real-time freshness
  Con: More complex, requires event infrastructure

STRATEGY 3: Cache-Aside with DELETE on write
  Update DB → DELETE cache key
  Next read → cache miss → reload from DB
  Pro: Simple, always fresh on next read
  Con: Brief window where cache is empty (thundering herd)
```

---

## Code

### Spring Boot with Redis Cache:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        // Different TTLs for different caches
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "users", defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "products", defaultConfig.entryTtl(Duration.ofHours(1)),
            "orders", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
```

### Service with Caching Annotations:

```java
@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#userId")
    public UserDto getUser(String userId) {
        log.info("Cache MISS: Loading user {} from database", userId);
        return userRepository.findById(userId)
            .map(this::toDto)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @CachePut(value = "users", key = "#userId")
    public UserDto updateUser(String userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        userRepository.save(user);
        return toDto(user);
    }

    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUserCache() {
        log.info("Cleared all user cache entries");
    }
}
```

### Manual Cache-Aside Implementation:

```java
@Service
public class ProductService {

    private final RedisTemplate<String, ProductDto> redisTemplate;
    private final ProductRepository productRepository;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public ProductDto getProduct(UUID productId) {
        String cacheKey = "product:" + productId;

        // 1. Check cache
        ProductDto cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;  // Cache HIT
        }

        // 2. Cache MISS — load from DB
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        ProductDto dto = toDto(product);

        // 3. Store in cache
        redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL);

        return dto;
    }

    public ProductDto updateProduct(UUID productId, UpdateProductRequest request) {
        // Update DB
        Product product = productRepository.findById(productId).orElseThrow();
        product.setPrice(request.getPrice());
        product.setName(request.getName());
        productRepository.save(product);

        // Invalidate cache (next read will reload)
        String cacheKey = "product:" + productId;
        redisTemplate.delete(cacheKey);

        return toDto(product);
    }
}
```

### Event-Based Cache Invalidation:

```java
@Service
public class CacheInvalidationListener {

    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = "user-events", groupId = "cache-invalidation")
    public void handleUserEvent(UserEvent event) {
        if (event instanceof UserUpdatedEvent e) {
            redisTemplate.delete("user:" + e.getUserId());
            log.info("Cache invalidated for user {}", e.getUserId());
        }
    }

    @KafkaListener(topics = "product-events", groupId = "cache-invalidation")
    public void handleProductEvent(ProductEvent event) {
        if (event instanceof ProductPriceChangedEvent e) {
            redisTemplate.delete("product:" + e.getProductId());
            // Also invalidate any cached lists containing this product
            redisTemplate.delete("product-list:category:" + e.getCategoryId());
        }
    }
}
```

---

## Interview Questions

1. **What is Cache-Aside pattern?**
   - Application checks cache first. On miss, loads from DB and stores in cache. On write, updates DB and invalidates cache. Most common pattern. Simple but requires app to manage cache.

2. **How to handle cache invalidation?**
   - TTL: Auto-expire after N seconds (simple but stale window). Event-based: Invalidate when source changes. Delete-on-write: Delete cache entry when updating DB. No perfect solution — pick based on consistency requirements.

3. **What is the thundering herd problem?**
   - Popular cache key expires. Many concurrent requests see cache miss. All hit database simultaneously. Solution: Mutex/lock on cache miss (only one loads), stale-while-revalidate, or staggered TTLs.

4. **Redis vs local cache (Caffeine)?**
   - Redis: Shared across instances, consistent, network latency. Local: Per-instance, no sharing, zero network latency. L1 (local) + L2 (Redis) is optimal for read-heavy services.

5. **Write-through vs Write-behind?**
   - Write-through: Sync write to both cache + DB (consistent, slow). Write-behind: Write to cache only, async flush to DB (fast, risk of data loss). Use write-through for consistency, write-behind for performance.

6. **How to cache in a microservices environment?**
   - Each service caches its own data. Use event-based invalidation for cross-service consistency. Don't cache data you don't own. Consider TTL for acceptable staleness window.

---

## Common Mistakes

1. **Caching without TTL** — Data grows forever, gets stale
2. **Cache stampede** — All instances miss simultaneously, overload DB
3. **Updating cache instead of invalidating** — Race conditions with concurrent updates
4. **Caching null values without short TTL** — Negative cache pollution
5. **No eviction policy** — Redis runs out of memory
6. **Caching mutable shared data** — Inconsistencies between services

---

## Best Practices

1. **Cache-aside for most cases** — Simple, explicit, predictable
2. **TTL always** — Even event-invalidated caches need TTL as safety net
3. **Delete, don't update** — Invalidate on write, reload on next read
4. **Monitor hit rate** — Low hit rate = cache not helping
5. **Warm cache on startup** — Pre-load frequently accessed data
6. **L1 + L2 caching** — Local (Caffeine) + distributed (Redis)
7. **Serialize efficiently** — Use JSON or Protobuf, not Java serialization
