# 21. Caching

## Theory

Spring Cache abstraction provides a consistent way to add caching to any method, independent of the underlying cache provider. It reduces database/API calls by storing results and returning cached data on subsequent calls with the same parameters.

### Key Annotations:
- **@Cacheable**: Cache method result; return cached value on subsequent calls
- **@CachePut**: Always execute method AND update cache (for writes)
- **@CacheEvict**: Remove entries from cache
- **@Caching**: Combine multiple cache operations
- **@EnableCaching**: Activate caching support

### Cache Providers:
- **ConcurrentMapCache** (default, in-memory, no TTL)
- **Caffeine** (high-performance in-memory with TTL)
- **Redis** (distributed cache, shared across instances)
- **EhCache** (feature-rich, supports disk overflow)
- **Hazelcast** (distributed, supports clustering)

### Cache-Aside Pattern (most common):
1. Check cache → if HIT, return cached value
2. If MISS → call method → store result in cache → return

---

## Internal Working

```
Client calls @Cacheable method
       ↓
Spring AOP Proxy intercepts
       ↓
CacheInterceptor executes
       ↓
Generate cache key (from method params)
       ↓
┌───────────────────────────────────┐
│ Look up key in cache store         │
│                                    │
│ Cache HIT?                         │
│ ├── YES → Return cached value     │
│ │         (method NOT executed)    │
│ └── NO → Execute actual method    │
│           → Store result in cache  │
│           → Return result          │
└───────────────────────────────────┘
```

### Key Generation:
```
@Cacheable("users")
public User findById(Long id)
  → Key: id value (e.g., 42)

@Cacheable("users")
public User findByNameAndCity(String name, String city)
  → Key: SimpleKey[name, city] (e.g., SimpleKey["John", "NYC"])
```

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│                   APPLICATION                         │
│                                                      │
│  @Cacheable("products")                              │
│  getProduct(id=42)                                   │
│       │                                              │
│       ↓                                              │
│  ┌─────────────────────────────────────────────┐    │
│  │          CACHE LAYER                          │    │
│  │                                               │    │
│  │  Key: "products::42"                          │    │
│  │  ┌────────┐                                   │    │
│  │  │ EXISTS?│                                   │    │
│  │  └───┬────┘                                   │    │
│  │      │                                        │    │
│  │  ┌───┴───────────────────────────────┐       │    │
│  │  │ HIT          │ MISS               │       │    │
│  │  │ Return value  │ Execute method     │       │    │
│  │  │ (skip DB)     │ Store in cache     │       │    │
│  │  │               │ Return value       │       │    │
│  │  └───────────────┴───────────────────┘       │    │
│  └─────────────────────────────────────────────┘    │
│       │ (on MISS)                                    │
│       ↓                                              │
│  ┌──────────┐                                        │
│  │ DATABASE │                                        │
│  └──────────┘                                        │
└─────────────────────────────────────────────────────┘
```

---

## Code

### Basic Caching with Caffeine:

```java
// Dependencies: spring-boot-starter-cache, caffeine

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats());
        return cacheManager;
    }

    // Multiple caches with different configs
    @Bean
    public CacheManager multiCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
            buildCache("products", 300, Duration.ofMinutes(30)),
            buildCache("users", 100, Duration.ofMinutes(5)),
            buildCache("config", 50, Duration.ofHours(1))
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, int maxSize, Duration ttl) {
        return new CaffeineCache(name, Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl)
            .build());
    }
}
```

### Service with Caching:

```java
@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    // Cache result - same ID returns cached product
    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) {
        log.info("Fetching product {} from DB", id);
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    // Cache with condition (only cache if price > 0)
    @Cacheable(value = "products", key = "#id", 
               condition = "#id > 0",
               unless = "#result.price == 0")
    public Product findByIdConditional(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    // Update cache entry after save
    @CachePut(value = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        log.info("Updating product {}", product.getId());
        return productRepository.save(product);
    }

    // Remove from cache on delete
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    // Clear entire cache
    @CacheEvict(value = "products", allEntries = true)
    public void clearProductCache() {
        log.info("Product cache cleared");
    }

    // Multiple cache operations
    @Caching(
        put = @CachePut(value = "products", key = "#product.id"),
        evict = @CacheEvict(value = "productsList", allEntries = true)
    )
    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    // Custom key generator
    @Cacheable(value = "products", key = "#category + '_' + #page + '_' + #size")
    public Page<Product> findByCategory(String category, int page, int size) {
        return productRepository.findByCategory(category, PageRequest.of(page, size));
    }
}
```

### Redis Cache Configuration:

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "products", defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "users", defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "sessions", defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .transactionAware()
            .build();
    }
}
```

---

## Dry Run

### @Cacheable behavior:

```
Call 1: productService.findById(42)
  → Cache lookup: key="42" in cache "products" → MISS
  → Execute method: DB query for product 42
  → Store in cache: products::42 = Product{id=42, name="Laptop"}
  → Return Product{id=42}
  → Time: ~50ms (DB call)

Call 2: productService.findById(42)
  → Cache lookup: key="42" in cache "products" → HIT
  → Return cached Product{id=42}
  → Method NOT executed (no DB call)
  → Time: ~1ms (cache read)

Call 3: productService.updateProduct(product42Updated)
  → @CachePut: Execute method + update cache
  → DB update executed
  → Cache updated: products::42 = Product{id=42, name="Gaming Laptop"}
  → Return updated product

Call 4: productService.findById(42)
  → Cache HIT → Returns updated "Gaming Laptop"

Call 5: productService.deleteProduct(42)
  → @CacheEvict: Remove key "42" from cache
  → DB delete executed

Call 6: productService.findById(42)
  → Cache MISS → DB call (will throw NotFoundException)
```

---

## Complexity

| Operation | ConcurrentMap | Caffeine | Redis |
|-----------|---------------|----------|-------|
| Get (HIT) | O(1) | O(1) | O(1) + network |
| Put | O(1) | O(1) | O(1) + network |
| Evict | O(1) | O(1) | O(1) + network |
| Evict all | O(n) | O(n) | O(n) |
| Memory | In-heap | In-heap | External |

---

## Real Project Usage

### Multi-Level Caching (L1 local + L2 Redis):

```java
@Service
public class ProductServiceWithMultiLevelCache {

    private final Cache<Long, Product> localCache; // Caffeine L1
    private final RedisTemplate<String, Product> redisTemplate; // Redis L2
    private final ProductRepository repository;

    public Product findById(Long id) {
        // L1: Check local cache
        Product product = localCache.getIfPresent(id);
        if (product != null) return product;

        // L2: Check Redis
        product = redisTemplate.opsForValue().get("product:" + id);
        if (product != null) {
            localCache.put(id, product); // Warm L1
            return product;
        }

        // DB: Fetch and populate both caches
        product = repository.findById(id).orElseThrow();
        redisTemplate.opsForValue().set("product:" + id, product, Duration.ofMinutes(30));
        localCache.put(id, product);
        return product;
    }
}
```

---

## Interview Questions

1. **What is the difference between @Cacheable and @CachePut?**
   - @Cacheable skips method execution on cache hit. @CachePut always executes method and updates cache (use for writes).

2. **How is the cache key generated?**
   - Default: SimpleKeyGenerator uses method parameters. Single param = param itself. Multiple params = SimpleKey[param1, param2].

3. **What happens if cached object is modified?**
   - In-memory cache: Object is shared reference — modifying it modifies cache (dangerous!). Redis: Serialized copy — safe from mutation.

4. **How to handle cache invalidation in microservices?**
   - Use distributed cache (Redis). Publish invalidation events via Kafka/Redis Pub-Sub. TTL-based expiration.

5. **@Cacheable with condition vs unless?**
   - condition: Evaluated BEFORE execution. If false, method runs without caching. unless: Evaluated AFTER execution. If true, result NOT cached.

---

## Follow-up Questions

1. How to implement cache warming/preloading on startup?
   - Use @PostConstruct or ApplicationRunner to load frequently accessed data into cache. Or use @Cacheable with a scheduled job that pre-calls methods. Prevents cold-start cache misses.

2. How to handle cache stampede (thundering herd)?
   - Caffeine: Use refreshAfterWrite (async refresh, serve stale during refresh). Redis: Distributed lock on cache miss (only one thread fetches from DB). Or use probabilistic early expiration.

3. Cache-aside vs read-through vs write-through patterns?
   - Cache-aside: Application manages cache (check, miss → load, store). Read-through: Cache itself loads from source on miss. Write-through: Writes go to cache AND source simultaneously. Cache-aside is most common in Spring.

4. How to implement cache versioning for schema changes?
   - Include version in cache key prefix: `v2:product:42`. On schema change, increment version → old keys naturally expire via TTL, new data uses new keys.

5. How to monitor cache hit rates in production?
   - Caffeine: `recordStats()` + expose via Actuator/Micrometer (`cache.gets`, `cache.puts`, `cache.evictions`). Redis: INFO stats command or RedisInsight. Alert if hit rate drops below threshold (e.g., < 80%).

---

## Common Mistakes

1. **Caching mutable objects** - Modifying returned object corrupts cache
2. **No TTL set** - Stale data served indefinitely
3. **Caching null values** - Wastes cache space (use `unless = "#result == null"`)
4. **Self-invocation** - @Cacheable on same-class method call bypasses proxy
5. **Too large cache** - Memory exhaustion (always set maximumSize)
6. **Forgetting @EnableCaching** - Annotations ignored silently
7. **Cache key collisions** - Generic keys like just using `#name` across different methods

---

## Best Practices

1. **Always set TTL** - Prevent stale data
2. **Use specific cache names** - Separate caches for different entities
3. **Return immutable objects** or use distributed cache to prevent mutation issues
4. **Monitor cache metrics** - Hit rate, evictions, load time
5. **Use @CacheEvict on writes** - Keep cache consistent
6. **Set maximumSize** - Prevent OOM errors
7. **Consider cache-aside pattern** for complex invalidation scenarios
8. **Use unless for conditional caching** - Don't cache errors/nulls

---

## Production Considerations

- **Cache stampede**: Multiple threads hit DB simultaneously on cache miss. Use Caffeine's `refreshAfterWrite` or distributed locks.
- **Serialization**: Redis requires serializable objects. Use JSON serialization for flexibility.
- **Memory monitoring**: Track cache size, especially in-memory caches. Set eviction policies.
- **Cluster consistency**: Redis cache shared across instances. Local cache may be stale — use short TTL.
- **Cold start**: After deployment, cache is empty. Consider cache warming strategies.
- **Cache penetration**: Requests for non-existent keys always hit DB. Cache null results with short TTL.
- **Monitoring**: Expose cache stats via Actuator/Micrometer (hit ratio, miss count, eviction count).

---

## Related Topics

- Redis (distributed caching)
- Spring AOP (caching uses proxy)
- Database Connection Pool (reducing DB load)
- Microservices (distributed cache challenges)
- Spring Boot Actuator (cache metrics)
- Performance Optimization
