# 22. Redis

## Theory

Redis (Remote Dictionary Server) is an in-memory data structure store used as a database, cache, message broker, and queue. In Spring Boot, Redis is primarily used for distributed caching, session storage, distributed locking, and pub/sub messaging.

### Key Concepts:
- **Key/Value Store**: All data stored as key-value pairs
- **TTL (Time To Live)**: Automatic expiration of keys
- **Data Structures**: Strings, Lists, Sets, Sorted Sets, Hashes, Streams
- **Distributed Cache**: Shared cache across multiple application instances
- **Pub/Sub**: Publish-subscribe messaging pattern
- **Distributed Lock**: Coordination mechanism for distributed systems
- **Serialization**: Converting objects to storable format (JSON, byte array)

### Why Redis?
- **Speed**: All data in-memory (~100K ops/sec)
- **Shared State**: Multiple app instances access same cache
- **Rich Data Structures**: Not just simple key-value
- **Atomic Operations**: Thread-safe without external locks
- **TTL Support**: Automatic expiration
- **Persistence Options**: RDB snapshots, AOF logging

---

## Internal Working

```
Spring Boot Application
       ↓
RedisTemplate / StringRedisTemplate
       ↓
LettuceConnectionFactory (default) or JedisConnectionFactory
       ↓
Connection Pool (Lettuce uses Netty, non-blocking)
       ↓
Redis Protocol (RESP)
       ↓
┌─────────────────────────────────────┐
│            REDIS SERVER              │
│                                      │
│  Memory: Key → Value                │
│                                      │
│  "user:42" → {"name":"John",...}    │
│  "session:abc" → {session data}      │
│  "cache:product:1" → {product}       │
│  "lock:order:99" → "owner-id"       │
│                                      │
│  TTL tracked per key                 │
│  Eviction policy when memory full    │
└─────────────────────────────────────┘
```

---

## Diagram

```
┌────────────────────────────────────────────────────────┐
│              SPRING BOOT APPLICATION                     │
│                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐  │
│  │ Cache Layer  │  │ Session Mgmt│  │ Dist. Lock   │  │
│  │ @Cacheable   │  │ HttpSession │  │ Redisson     │  │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘  │
│         │                 │                 │          │
│  ┌──────┴─────────────────┴─────────────────┴───────┐ │
│  │              RedisTemplate                         │ │
│  │         (Serialization + Operations)               │ │
│  └──────────────────────┬────────────────────────────┘ │
│                          │                              │
│  ┌──────────────────────┴────────────────────────────┐ │
│  │         LettuceConnectionFactory                    │ │
│  │            (Connection Pooling)                      │ │
│  └──────────────────────┬────────────────────────────┘ │
└─────────────────────────┼──────────────────────────────┘
                          │ TCP
                          ↓
┌────────────────────────────────────────────────────────┐
│                    REDIS SERVER                          │
│                                                         │
│  ┌─────────────────────────────────────────────────┐  │
│  │  Strings: "user:1" → "John"                      │  │
│  │  Hashes:  "user:1:profile" → {name, email}      │  │
│  │  Lists:   "queue:orders" → [order1, order2]      │  │
│  │  Sets:    "online:users" → {user1, user2}        │  │
│  │  Sorted:  "leaderboard" → {(score, user)}        │  │
│  └─────────────────────────────────────────────────┘  │
│                                                         │
│  Eviction: allkeys-lru | volatile-lru | noeviction     │
│  Persistence: RDB + AOF                                 │
└────────────────────────────────────────────────────────┘
```

---

## Code

### Redis Configuration:

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: secret
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
          max-wait: 2000ms
```

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // JSON serialization for values
        Jackson2JsonRedisSerializer<Object> jsonSerializer = 
            new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
            mapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL);
        jsonSerializer.setObjectMapper(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

### Basic Operations with RedisTemplate:

```java
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // String operations
    public void setWithTTL(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // Hash operations (store object fields)
    public void saveUserProfile(Long userId, Map<String, String> profile) {
        String key = "user:" + userId + ":profile";
        redisTemplate.opsForHash().putAll(key, profile);
        redisTemplate.expire(key, Duration.ofHours(1));
    }

    // List operations (queue)
    public void pushToQueue(String queue, Object message) {
        redisTemplate.opsForList().leftPush(queue, message);
    }

    public Object popFromQueue(String queue) {
        return redisTemplate.opsForList().rightPop(queue);
    }

    // Set operations (unique collections)
    public void addOnlineUser(Long userId) {
        redisTemplate.opsForSet().add("online:users", userId.toString());
    }

    public Set<Object> getOnlineUsers() {
        return redisTemplate.opsForSet().members("online:users");
    }

    // Sorted Set (leaderboard)
    public void updateScore(String leaderboard, String player, double score) {
        redisTemplate.opsForZSet().add(leaderboard, player, score);
    }

    public Set<Object> getTopPlayers(String leaderboard, int count) {
        return redisTemplate.opsForZSet().reverseRange(leaderboard, 0, count - 1);
    }

    // Delete & TTL
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
```

### Distributed Locking:

```java
@Service
@Slf4j
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    public boolean acquireLock(String lockKey, String ownerId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, ownerId, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean releaseLock(String lockKey, String ownerId) {
        // Lua script for atomic check-and-delete
        String script = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            List.of(lockKey), ownerId);
        return result != null && result == 1;
    }

    // Usage in service
    public void processOrderExclusive(Long orderId) {
        String lockKey = "lock:order:" + orderId;
        String ownerId = UUID.randomUUID().toString();

        if (acquireLock(lockKey, ownerId, Duration.ofSeconds(30))) {
            try {
                // Critical section - only one instance processes this order
                processOrder(orderId);
            } finally {
                releaseLock(lockKey, ownerId);
            }
        } else {
            log.warn("Could not acquire lock for order {}", orderId);
            throw new ConcurrentModificationException("Order being processed");
        }
    }
}
```

### Redis Pub/Sub:

```java
// Configuration
@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(orderEventListener(), 
            new ChannelTopic("orders"));
        return container;
    }

    @Bean
    public MessageListenerAdapter orderEventListener() {
        return new MessageListenerAdapter(new OrderEventSubscriber());
    }
}

// Publisher
@Service
public class OrderEventPublisher {
    private final RedisTemplate<String, Object> redisTemplate;

    public void publishOrderEvent(OrderEvent event) {
        redisTemplate.convertAndSend("orders", event);
    }
}

// Subscriber
public class OrderEventSubscriber implements MessageListener {
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        // Process order event
    }
}
```

---

## Dry Run

### Cache Miss → Cache Hit scenario:

```
Request 1: GET /api/products/42

  1. @Cacheable checks Redis: GET "cache:products:42" → null (MISS)
  2. Execute method → DB query → Product{id=42, name="Laptop", price=999}
  3. Store in Redis: SET "cache:products:42" → serialized Product, EX 1800
  4. Return Product to client
  5. Time: ~55ms (50ms DB + 5ms Redis write)

Request 2: GET /api/products/42 (10 seconds later)

  1. @Cacheable checks Redis: GET "cache:products:42" → HIT
  2. Deserialize → Product{id=42, name="Laptop", price=999}
  3. Return from cache (method NOT executed)
  4. Time: ~3ms (Redis read + deserialize)

After 30 minutes (TTL expires):

Request 3: GET /api/products/42
  1. @Cacheable checks Redis: GET "cache:products:42" → null (expired)
  2. Same as Request 1 - fetch from DB, cache again
```

---

## Complexity

| Operation | Time Complexity |
|-----------|----------------|
| GET/SET | O(1) |
| HGET/HSET | O(1) |
| LPUSH/RPOP | O(1) |
| SADD/SISMEMBER | O(1) |
| ZADD | O(log n) |
| ZRANGE | O(log n + m) where m = result size |
| Keys pattern match | O(n) - AVOID in production |
| Network round trip | ~0.5-2ms (same datacenter) |

---

## Real Project Usage

### Rate Limiting:

```java
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String clientId, int maxRequests, Duration window) {
        String key = "ratelimit:" + clientId;
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == 1) {
            redisTemplate.expire(key, window);
        }
        
        return count <= maxRequests;
    }
}
```

### Session Storage:

```java
// application.yml
spring:
  session:
    store-type: redis
    timeout: 30m
    redis:
      namespace: myapp:session
```

---

## Interview Questions

1. **Why use Redis over local cache?**
   - Shared across instances (consistency), survives app restart, supports distributed locking, larger capacity (not limited to JVM heap).

2. **How to handle cache invalidation?**
   - TTL-based expiration, explicit eviction on writes (@CacheEvict), event-driven invalidation (Pub/Sub), versioned keys.

3. **What eviction policies does Redis support?**
   - noeviction, allkeys-lru, volatile-lru, allkeys-random, volatile-random, volatile-ttl. LRU removes least recently used keys.

4. **How to implement distributed locking with Redis?**
   - SET key value NX EX ttl (atomic set-if-not-exists with expiry). Release with Lua script to check ownership. For production, use Redisson/RedLock.

5. **Redis vs Memcached?**
   - Redis: Rich data structures, persistence, pub/sub, Lua scripting, clustering. Memcached: Simpler, multi-threaded, slightly faster for simple get/set.

---

## Follow-up Questions

1. How does Redis clustering work and how to configure with Spring?
   - Redis Cluster: Data sharded across multiple nodes (16384 hash slots). Configure: `spring.data.redis.cluster.nodes=host1:6379,host2:6379,host3:6379`. Lettuce handles slot routing automatically.

2. What is Redis Sentinel and when would you use it?
   - Sentinel provides high availability: monitors master, promotes replica on failure, notifies clients of new master. Use for: HA without sharding. Configure: `spring.data.redis.sentinel.master=mymaster`.

3. How to handle Redis connection failures gracefully?
   - Circuit breaker pattern around Redis calls. Fallback to DB on cache miss. Don't let Redis unavailability crash the app. Use short connection timeouts. Log and alert on failures.

4. How does Redis persistence (RDB vs AOF) work?
   - RDB: Point-in-time snapshots at intervals (fast restart, potential data loss). AOF: Logs every write (slower restart, minimal data loss). Both can be combined. For cache-only: persistence may be unnecessary.

5. How to implement Redis-based distributed session in microservices?
   - spring-session-data-redis: Stores HttpSession in Redis (shared across instances). All instances read same session. Configure: `spring.session.store-type=redis`. User routed to any instance.

---

## Common Mistakes

1. **Using KEYS command in production** - Blocks Redis (O(n) scan of all keys). Use SCAN instead.
2. **No TTL on cache entries** - Memory grows unbounded until OOM
3. **Large objects in Redis** - Serialization/deserialization overhead. Keep values small.
4. **Not handling connection failures** - App should degrade gracefully (circuit breaker pattern)
5. **Single Redis instance for everything** - Separate concerns (cache vs sessions vs locks)
6. **Using default serializer** - JdkSerializationRedisSerializer is large and not human-readable. Use JSON.

---

## Best Practices

1. **Use meaningful key naming** - `entity:id:field` pattern (e.g., `user:42:profile`)
2. **Always set TTL** - Prevent memory leak
3. **Use JSON serialization** - Readable, debuggable, smaller than JDK serializer
4. **Configure connection pool** - Prevent connection exhaustion under load
5. **Use Lua scripts** for atomic multi-step operations
6. **Monitor memory usage** - Set maxmemory and eviction policy
7. **Use Redis Sentinel/Cluster** for high availability in production
8. **Implement circuit breaker** around Redis calls

---

## Production Considerations

- **High Availability**: Use Redis Sentinel (auto-failover) or Redis Cluster (sharding)
- **Memory Management**: Set maxmemory, choose appropriate eviction policy
- **Persistence**: RDB for periodic snapshots, AOF for durability (or both)
- **Connection Limits**: Monitor connections, size pool appropriately
- **Network Latency**: Place Redis in same AZ/region as application
- **Monitoring**: Track memory usage, hit rate, evictions, slow commands (SLOWLOG)
- **Security**: Use AUTH, disable dangerous commands (FLUSHALL, KEYS), network isolation
- **Serialization Compatibility**: Plan for class changes that break deserialization

---

## Related Topics

- Caching (Spring Cache abstraction)
- Distributed Locking
- Session Management
- Pub/Sub (event-driven communication)
- Microservices (shared state)
- Rate Limiting
- Spring Security (token storage)
