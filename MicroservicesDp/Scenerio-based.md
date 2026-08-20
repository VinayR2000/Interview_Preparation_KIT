# Java & Spring Boot Interview Handbook - Scenario-Based Questions

## Table of Contents

- [SECTION 1: Java & Spring Boot (Questions 1-10)](#section-1-java--spring-boot-questions-1-10)
  - [1. Humongous Allocation in G1GC](#1-explain-a-humongous-allocation-in-g1gc-its-impact-on-memory-management-and-mitigation-techniques)
  - [2. Thread Pinning in Java 21 Virtual Threads](#2-what-is-thread-pinning-in-java-21-virtual-threads-explain-its-causes-and-solutions)
  - [3. AOP Proxy Self-Invocation Problem](#3-explain-the-aop-proxy-self-invocation-problem-with-transactional-and-how-to-overcome-it)
  - [4. Transactional Outbox Pattern](#4-how-does-the-transactional-outbox-pattern-solve-the-dual-write-problem-in-microservices)
  - [5. @EnableAutoConfiguration Internals](#5-explain-the-complete-working-of-spring-boot-enableautoconfiguration-internally)
  - [6. Split-Brain Scenario](#6-what-is-a-split-brain-scenario-in-a-distributed-system-and-how-can-it-be-prevented)
  - [7. Count-Based vs Time-Based Sliding Windows](#7-explain-the-difference-between-count-based-and-time-based-sliding-windows-in-resilience4j)
  - [8. JWT Validation vs OAuth2 Token Introspection](#8-compare-jwt-validation-and-oauth2-token-introspection)
  - [9. Strict Idempotency for Concurrent POST](#9-how-do-you-guarantee-strict-idempotency-for-concurrent-post-requests-in-a-distributed-application)
  - [10. Kafka Rebalancing Storm](#10-what-causes-a-kafka-rebalancing-storm-and-how-would-you-troubleshoot-and-prevent-it)
- [SECTION 2: DSA & Logic (Questions 11-15)](#section-2-dsa--logic-questions-11-15)
  - [11. Thread-Safe LRU Cache](#11-design-a-thread-safe-lru-cache-with-o1-get-and-put-operations)
  - [12. Longest Palindromic Substring](#12-find-the-longest-palindromic-substring-in-a-given-string)
  - [13. Detect and Remove a Cycle in Directed Graph](#13-detect-and-remove-a-cycle-in-a-directed-graph)
  - [14. Serialize and Deserialize a Binary Tree](#14-serialize-and-deserialize-a-binary-tree-efficiently)
  - [15. Concurrent Rate Limiter (Token Bucket)](#15-design-a-concurrent-rate-limiter-using-the-token-bucket-algorithm)
- [SECTION 3: Java & Spring Boot Interview Sheet](#section-3-java--spring-boot-interview-sheet)
  - [1. Thread Safe LRU Cache (Detailed)](#1-thread-safe-lru-cache-detailed)
  - [2. Spring WebFlux](#2-spring-webflux)
  - [3. JDBC Driver](#3-jdbc-driver)
  - [4. @Repository in Spring Boot](#4-repository-in-spring-boot)
  - [5. Can Final Methods Be Inherited?](#5-can-final-methods-be-inherited)
  - [6. Poison Pill Problem in Kafka](#6-poison-pill-problem-in-kafka)

---

## SECTION 1: Java & Spring Boot (Questions 1-10)

---

### 1. Explain a Humongous Allocation in G1GC, its impact on memory management, and mitigation techniques.

**Answer:**

In G1GC, a **Humongous Allocation** is an object whose size is greater than **50% of the G1 region size**. Such objects are allocated in contiguous regions directly in the old generation.

**Visual Representation of G1 Heap:**

```
┌─────┬─────┬─────┬─────┬─────┐
│  E  │  S  │  O  │  H  │  F  │
├─────┼─────┼─────┼─────┼─────┤
│  O  │  F  │  O  │  H  │  E  │
├─────┼─────┼─────┼─────┼─────┤
│  F  │  O  │  F  │  F  │  O  │
├─────┼─────┼─────┼─────┼─────┤
│  O  │  E  │  F  │  O  │  F  │
└─────┴─────┴─────┴─────┴─────┘

E = Eden    S = Survivor    O = Old
H = Humongous    F = Free
```

**Impact:**
- Causes more GC cycles and pauses
- Increases memory fragmentation
- Reduces performance

**Mitigation:**
- Increase G1 region size (`-XX:G1HeapRegionSize`)
- Avoid creating very large objects
- Tune heap size (`-Xms`, `-Xmx`)
- Use object pooling or streaming where possible

```java
// Bad - creates humongous allocation
byte[] largeArray = new byte[32 * 1024 * 1024]; // 32MB array

// Better - stream processing instead of loading all at once
try (InputStream is = new FileInputStream("largefile.dat");
     BufferedInputStream bis = new BufferedInputStream(is, 8192)) {
    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = bis.read(buffer)) != -1) {
        process(buffer, bytesRead);
    }
}

// JVM flags to tune G1GC
// -XX:+UseG1GC
// -XX:G1HeapRegionSize=16m  (increase from default)
// -XX:InitiatingHeapOccupancyPercent=45
```

---

### 2. What is Thread Pinning in Java 21 Virtual Threads? Explain its causes and solutions.

**Answer:**

**Thread Pinning** occurs when a virtual thread is pinned to a carrier (platform) thread, preventing it from being unmounted. This reduces scalability because the carrier thread is blocked and cannot serve other virtual threads.

**Causes:**
- Synchronized blocks/methods
- Native calls (JNI)
- Blocking I/O (non-async)
- Certain JVM operations

**Solutions:**
- Avoid synchronized blocks (use `ReentrantLock` if needed)
- Use non-blocking APIs
- Prefer structured concurrency and async I/O
- Keep critical sections small

```java
// BAD - causes thread pinning
public class PinnedExample {
    private final Object lock = new Object();
    
    public void process() {
        synchronized (lock) {  // PINS the virtual thread!
            // Long-running I/O operation
            httpClient.send(request, bodyHandler);  // Carrier thread blocked
        }
    }
}

// GOOD - use ReentrantLock instead
public class UnpinnedExample {
    private final ReentrantLock lock = new ReentrantLock();
    
    public void process() {
        lock.lock();  // Virtual thread can unmount while waiting
        try {
            httpClient.send(request, bodyHandler);
        } finally {
            lock.unlock();
        }
    }
}

// GOOD - use structured concurrency
public void processWithVirtualThreads() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        executor.submit(() -> {
            // Use non-blocking APIs
            CompletableFuture<HttpResponse<String>> response =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            return response.join();
        });
    }
}
```

**Detection:** Use `-Djdk.tracePinnedThreads=full` JVM flag to detect pinning at runtime.

---

### 3. Explain the AOP Proxy Self-Invocation Problem with @Transactional and how to overcome it.

**Answer:**

In Spring AOP (proxy-based), a method call from within the same class does not go through the proxy. Hence, annotations like `@Transactional` are not applied on self-invocation.

**The Problem:**

```java
@Service
public class OrderService {
    
    @Transactional
    public void method1() {
        method2();  // DIRECT CALL - bypasses proxy!
        // @Transactional on method2 is IGNORED
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void method2() {
        // This runs WITHOUT its own transaction context
        // because it was called internally, not through the proxy
    }
}
```

**Why it happens:**
```
External Call → Proxy → method1() → method2() (direct, no proxy)
                 ↑
         AOP intercepts here only
```

**Solutions:**

**Solution 1: Use Self-Injection**
```java
@Service
public class OrderService {
    @Autowired
    private OrderService self;  // Inject proxy of itself
    
    @Transactional
    public void method1() {
        self.method2();  // Goes through proxy - @Transactional applied!
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void method2() { /* ... */ }
}
```

**Solution 2: Move method to another bean**
```java
@Service
public class OrderService {
    @Autowired
    private OrderHelper orderHelper;
    
    @Transactional
    public void method1() {
        orderHelper.method2();  // Different bean → goes through proxy
    }
}

@Service
public class OrderHelper {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void method2() { /* ... */ }
}
```

**Solution 3: Use AspectJ (compile/load-time weaving)**
```java
// application.properties
// spring.aop.proxy-target-class=true

// Or use @EnableAspectJAutoProxy with AspectJ mode
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
```

**Solution 4: Use TransactionTemplate (programmatic)**
```java
@Service
public class OrderService {
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Transactional
    public void method1() {
        transactionTemplate.execute(status -> {
            // This always runs in a transaction
            doMethod2Logic();
            return null;
        });
    }
}
```

---

### 4. How does the Transactional Outbox Pattern solve the Dual-Write Problem in Microservices?

**Answer:**

**Dual-Write Problem** occurs when a service updates its DB and publishes a message separately — if one succeeds and the other fails, data inconsistency happens.

**Example of the Problem:**
```java
// DANGEROUS - dual write
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);           // DB write succeeds
    kafkaTemplate.send("orders", order);   // Message publish FAILS!
    // Result: DB has the order, but no event was published
}
```

**Outbox Pattern Solution:**

```
┌─────────────┐         ┌──────────┐         ┌─────────────────┐
│   Service   │         │    DB    │         │  Message Broker  │
└──────┬──────┘         └────┬─────┘         └────────┬────────┘
       │                     │                         │
       │  1. Write business  │                         │
       │     data + outbox   │                         │
       │────────────────────>│                         │
       │   (single TX)       │                         │
       │                     │                         │
       │         2. Poller reads outbox                │
       │                     │────────────────────────>│
       │                     │   3. Publish event      │
       │                     │                         │
       │                     │   4. Mark as published  │
       │                     │<────────────────────────│
       │                     │                         │
```

**How it works:**
1. Write business data + event into an OUTBOX table in the same DB transaction
2. A separate process reads the outbox table and publishes the event to the message broker
3. Ensures atomicity and eventual consistency

**Implementation:**

```java
// Entity
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private LocalDateTime createdAt;
    private boolean published;
}

// Service - single transaction for both writes
@Service
public class OrderService {
    @Autowired private OrderRepository orderRepo;
    @Autowired private OutboxRepository outboxRepo;
    
    @Transactional  // Both writes in ONE transaction
    public Order createOrder(OrderRequest request) {
        Order order = orderRepo.save(new Order(request));
        
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Order");
        event.setAggregateId(order.getId());
        event.setEventType("ORDER_CREATED");
        event.setPayload(objectMapper.writeValueAsString(order));
        event.setCreatedAt(LocalDateTime.now());
        event.setPublished(false);
        outboxRepo.save(event);
        
        return order;
    }
}

// Poller - separate process publishes events
@Component
public class OutboxPoller {
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> events = outboxRepo.findByPublishedFalse();
        for (OutboxEvent event : events) {
            kafkaTemplate.send(event.getEventType(), event.getPayload());
            event.setPublished(true);
            outboxRepo.save(event);
        }
    }
}
```

**Alternative:** Use Debezium CDC (Change Data Capture) to tail the outbox table's WAL instead of polling.

---

### 5. Explain the complete working of Spring Boot @EnableAutoConfiguration internally.

**Answer:**

**Step-by-step process:**

1. `@SpringBootApplication` triggers auto-config (includes `@EnableAutoConfiguration`)
2. Spring loads all auto-configuration classes from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
3. Conditions (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, etc.) are evaluated
4. Matching auto-config classes are applied
5. Beans are created and added to the context
6. Application starts with required default configuration

**Flow:**
```
@SpringBootApplication
    └── @EnableAutoConfiguration
            └── @Import(AutoConfigurationImportSelector.class)
                    └── Loads META-INF/spring/...AutoConfiguration.imports
                            └── Evaluates @Conditional annotations
                                    └── Creates beans for matching configs
```

**Example - How DataSource auto-configures:**

```java
// Spring Boot's internal auto-config class (simplified)
@AutoConfiguration
@ConditionalOnClass(DataSource.class)                    // Only if DataSource is on classpath
@ConditionalOnMissingBean(DataSource.class)              // Only if user hasn't defined one
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {
    
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource(DataSourceProperties properties) {
        return DataSourceBuilder.create()
            .url(properties.getUrl())
            .username(properties.getUsername())
            .password(properties.getPassword())
            .build();
    }
}
```

**Key Conditions:**
| Annotation | Checks |
|-----------|--------|
| `@ConditionalOnClass` | Class exists on classpath |
| `@ConditionalOnMissingBean` | Bean not already defined by user |
| `@ConditionalOnProperty` | Property has specific value |
| `@ConditionalOnWebApplication` | Running as web app |

**Interview Tip:** You can exclude auto-configs with `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})`

---

### 6. What is a Split-Brain Scenario in a distributed system, and how can it be prevented?

**Answer:**

**Split-Brain** occurs when a network partition causes nodes in a distributed system to form multiple groups, each believing it is the primary, leading to data inconsistency.

**Example:**
```
Normal State:              Split-Brain State:
┌─────────────────┐       ┌────────┐    ┌────────┐
│  Node1  Node2   │       │ Node1  │    │ Node2  │
│  Node3  Node4   │  →→→  │ Node3  │    │ Node4  │
│  (One Cluster)  │       │(Leader)│    │(Leader)│
└─────────────────┘       └────────┘    └────────┘
                           Partition 1   Partition 2
                           Both think they are the leader!
```

**Prevention Strategies:**

1. **Use quorum-based systems (e.g., majority voting)**
```
Cluster of 5 nodes → need at least 3 to form quorum
If network splits into 2+3 → only the group of 3 can operate
```

2. **Implement leader election (e.g., ZooKeeper, etcd, Consul)**
```java
// Using Apache Curator (ZooKeeper) for leader election
LeaderSelector leaderSelector = new LeaderSelector(client, "/leader", 
    new LeaderSelectorListenerAdapter() {
        @Override
        public void takeLeadership(CuratorFramework client) throws Exception {
            System.out.println("I am the leader now!");
            // Perform leader duties
            Thread.sleep(Long.MAX_VALUE); // Hold leadership
        }
    });
leaderSelector.autoRequeue();
leaderSelector.start();
```

3. **Use proper heartbeat & health checks**
```yaml
# application.yml - Eureka configuration
eureka:
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
  server:
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
```

4. **Configure timeouts and fencing mechanisms**
```java
// Fencing token approach
public class FencedLeader {
    private final AtomicLong fencingToken = new AtomicLong(0);
    
    public void onLeadershipAcquired() {
        long token = fencingToken.incrementAndGet();
        // All writes must include this token
        // Storage rejects writes with older tokens
    }
}
```

---

### 7. Explain the difference between Count-Based and Time-Based Sliding Windows in Resilience4j.

**Answer:**

| Aspect | Count-Based Sliding Window | Time-Based Sliding Window |
|--------|---------------------------|--------------------------|
| **What it is** | Tracks last N calls | Tracks calls in last T seconds |
| **Precision** | Lower (depends on call rate) | Higher (time bucket based) |
| **Use Case** | When request rate is stable | When request rate varies |
| **Memory** | Lower | Slightly higher |
| **Configuration** | `slidingWindowSize=10` (last 10 calls) | `slidingWindowSize=10` (last 10 seconds) |

**Count-Based Example:**
```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .slidingWindowType(SlidingWindowType.COUNT_BASED)
    .slidingWindowSize(10)           // Evaluate last 10 calls
    .failureRateThreshold(50)        // Open if 50% fail
    .minimumNumberOfCalls(5)         // Need at least 5 calls to evaluate
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .build();

CircuitBreaker circuitBreaker = CircuitBreaker.of("myService", config);

// Decorate a supplier
Supplier<String> decoratedSupplier = CircuitBreaker
    .decorateSupplier(circuitBreaker, () -> remoteService.call());

String result = Try.ofSupplier(decoratedSupplier)
    .recover(throwable -> "Fallback response")
    .get();
```

**Time-Based Example:**
```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .slidingWindowType(SlidingWindowType.TIME_BASED)
    .slidingWindowSize(10)           // Evaluate last 10 SECONDS
    .failureRateThreshold(50)        // Open if 50% fail
    .minimumNumberOfCalls(5)
    .slowCallDurationThreshold(Duration.ofSeconds(2))
    .slowCallRateThreshold(80)       // Open if 80% are slow
    .waitDurationInOpenState(Duration.ofSeconds(60))
    .build();
```

**When to choose which:**
- **Count-Based:** Stable traffic patterns, simpler memory footprint
- **Time-Based:** Variable traffic, need time-aware failure detection (e.g., burst failures)

---

### 8. Compare JWT Validation and OAuth2 Token Introspection.

**Answer:**

| Aspect | JWT Validation | OAuth2 Token Introspection |
|--------|---------------|---------------------------|
| **Definition** | Validates token locally using public key | Calls auth server to validate token |
| **Performance** | Faster (no network call) | Slower (network call required) |
| **Real-time Revocation** | Not immediate | Immediate |
| **Use Case** | Stateless, high-performance APIs | High-security, revocation-critical systems |
| **Offline capable** | Yes | No (needs auth server) |
| **Token size** | Larger (contains claims) | Smaller (opaque reference) |

**JWT Validation (Local):**
```java
@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(jwtDecoder())  // Local validation
            )
        );
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        // Validates JWT signature using public key - NO network call
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
    }
}
```

**OAuth2 Token Introspection (Remote):**
```java
@Configuration
@EnableWebSecurity
public class IntrospectionSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2
            .opaqueToken(opaque -> opaque
                .introspectionUri("https://auth-server/oauth2/introspect")
                .introspectionClientCredentials("client-id", "client-secret")
                // Calls auth server for EVERY request
            )
        );
        return http.build();
    }
}
```

**Hybrid Approach (Best Practice):**
```java
// Use JWT for most requests (fast), but introspect for sensitive operations
@Service
public class TokenService {
    public boolean validateToken(String token, boolean highSecurity) {
        if (highSecurity) {
            return introspect(token);  // Network call to auth server
        }
        return validateJwtLocally(token);  // Local signature check
    }
}
```

---

### 9. How do you guarantee Strict Idempotency for concurrent POST requests in a distributed application?

**Answer:**

**Strategy:**
1. Use `Idempotency-Key` (unique per request) from client
2. Store the key with response in a DB/Cache with unique constraint
3. On duplicate request, return the stored response
4. Ensure atomic check-and-store using DB transaction or distributed lock

**Architecture:**
```
Client ──POST + Idempotency-Key──→ API Server ──→ DB/Cache
                                                  (Idempotency Store)
         ←──Return Stored Response (if duplicate)──┘
```

**Implementation:**

```java
@RestController
public class PaymentController {
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private PaymentService paymentService;
    
    @PostMapping("/payments")
    public ResponseEntity<?> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request) {
        
        // Check if already processed
        Optional<String> cached = idempotencyService.getResponse(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());  // Return stored response
        }
        
        // Process and store atomically
        String response = idempotencyService.executeIdempotent(idempotencyKey, () -> {
            return paymentService.processPayment(request);
        });
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

@Service
public class IdempotencyService {
    @Autowired private RedisTemplate<String, String> redis;
    
    public <T> T executeIdempotent(String key, Supplier<T> operation) {
        // Atomic check-and-set using Redis SETNX
        Boolean acquired = redis.opsForValue()
            .setIfAbsent("idempotency:" + key, "PROCESSING", Duration.ofHours(24));
        
        if (Boolean.FALSE.equals(acquired)) {
            // Another request is processing or already processed
            String result = waitForResult(key);
            return (T) result;
        }
        
        try {
            T result = operation.get();
            redis.opsForValue().set("idempotency:" + key, 
                objectMapper.writeValueAsString(result), Duration.ofHours(24));
            return result;
        } catch (Exception e) {
            redis.delete("idempotency:" + key);  // Allow retry on failure
            throw e;
        }
    }
}
```

**Database approach with unique constraint:**
```sql
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    response_body   TEXT,
    status_code     INT,
    created_at      TIMESTAMP DEFAULT NOW(),
    expires_at      TIMESTAMP
);
```

---

### 10. What causes a Kafka Rebalancing Storm and how would you troubleshoot and prevent it?

**Answer:**

**Causes:**
- Frequent consumer crashes/restarts
- Short `session.timeout.ms` / `heartbeat.interval.ms`
- Slow message processing
- Too many consumers for few partitions
- Network issues

**Troubleshoot:**
| Step | Action |
|------|--------|
| 1 | Check consumer logs (instances, timeouts) |
| 2 | Monitor consumer lag, CPU, GC |
| 3 | Check heartbeat failures |
| 4 | Verify partition count vs consumer count |

**Prevent:**
| Strategy | Configuration |
|----------|--------------|
| Increase timeouts | `session.timeout.ms=30000`, `heartbeat.interval.ms=10000` |
| Stable consumer performance | Ensure consistent processing times |
| Use cooperative-sticky assignor | `partition.assignment.strategy` |
| Avoid frequent deployments | Use rolling deployments |

**Configuration:**
```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "my-group");
        
        // Prevent rebalancing storms
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);       // 30s
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);    // 10s
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);    // 5min
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);           // Limit batch
        
        // Use CooperativeSticky to minimize rebalance impact
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
            CooperativeStickyAssignor.class.getName());
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
```

**Static Membership (prevents unnecessary rebalances on restart):**
```properties
# Each consumer gets a static ID - rejoins without triggering rebalance
group.instance.id=consumer-instance-1
session.timeout.ms=60000
```

---

## SECTION 2: DSA & Logic (Questions 11-15)

---

### 11. Design a Thread-Safe LRU Cache with O(1) get and put operations.

**Answer:**

**Data Structure:** Use `HashMap` + `Doubly Linked List` + `Lock`

```
HashMap: Key → Node
DLL: maintains order (MRU to LRU)
Lock: ensures thread safety

┌─────────────────────────────────────────────┐
│  HashMap                                    │
│  key1 → Node1                               │
│  key2 → Node2                               │
│  key3 → Node3                               │
└─────────────────────────────────────────────┘

Head (MRU)                          Tail (LRU)
  ↓                                    ↓
┌───┐    ┌───┐    ┌───┐    ┌───┐
│ 5 │ ↔  │ 3 │ ↔  │ 2 │ ↔  │ 1 │
└───┘    └───┘    └───┘    └───┘
Most Recent                  Least Recent
```

**Complexity:**
| Operation | Time Complexity |
|-----------|----------------|
| get(key) | O(1) |
| put(key, value) | O(1) |
| Space | O(capacity) |

**Key Points:**
- HashMap for O(1) lookup
- DLL for O(1) add/remove and tracking LRU
- ReentrantLock for thread safety

**Core Logic (Pseudo):**
1. If key exists → remove node and add it to head (Most Recent)
2. If key doesn't exist → add new node to head (Most Recent)
3. If size > capacity → remove node from tail (Least Recent)

**Full Implementation:**

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ThreadSafeLRUCache<K, V> {
    
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;  // Dummy head (MRU side)
    private final Node<K, V> tail;  // Dummy tail (LRU side)
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Doubly Linked List Node
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public ThreadSafeLRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null); // Dummy
        this.tail = new Node<>(null, null); // Dummy
        head.next = tail;
        tail.prev = head;
    }
    
    public V get(K key) {
        lock.writeLock().lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) return null;
            
            moveToHead(node);  // Mark as most recently used
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            Node<K, V> node = map.get(key);
            
            if (node != null) {
                node.value = value;
                moveToHead(node);
            } else {
                Node<K, V> newNode = new Node<>(key, value);
                map.put(key, newNode);
                addToHead(newNode);
                
                if (map.size() > capacity) {
                    Node<K, V> lru = removeTail();
                    map.remove(lru.key);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void addToHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
    
    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }
    
    private Node<K, V> removeTail() {
        Node<K, V> lru = tail.prev;
        removeNode(lru);
        return lru;
    }
}
```

---

### 12. Find the Longest Palindromic Substring in a given string.

**Answer:**

**Approach:** Expand Around Center

**Steps:**
1. For each index, expand for odd length
2. Expand for even length
3. Keep track of max length substring

**Time Complexity:** O(n²)  
**Space Complexity:** O(1)

**Example:**
- Input: `s = "babad"`
- Output: `"bab"` or `"aba"`

```java
public class LongestPalindrome {
    
    public String longestPalindrome(String s) {
        if (s == null || s.length() < 1) return "";
        
        int start = 0, maxLen = 0;
        
        for (int i = 0; i < s.length(); i++) {
            // Odd length palindrome (center is single char)
            int len1 = expandAroundCenter(s, i, i);
            // Even length palindrome (center is between two chars)
            int len2 = expandAroundCenter(s, i, i + 1);
            
            int len = Math.max(len1, len2);
            if (len > maxLen) {
                maxLen = len;
                start = i - (len - 1) / 2;
            }
        }
        
        return s.substring(start, start + maxLen);
    }
    
    private int expandAroundCenter(String s, int left, int right) {
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        return right - left - 1;  // Length of palindrome
    }
    
    // Example usage
    public static void main(String[] args) {
        LongestPalindrome lp = new LongestPalindrome();
        System.out.println(lp.longestPalindrome("babad"));  // "bab" or "aba"
        System.out.println(lp.longestPalindrome("cbbd"));   // "bb"
        System.out.println(lp.longestPalindrome("racecar")); // "racecar"
    }
}
```

**Dry Run for "babad":**
```
i=0: expand(0,0) → "b" (len=1), expand(0,1) → "" (len=0) → max=1, start=0
i=1: expand(1,1) → "aba" (len=3), expand(1,2) → "" (len=0) → max=3, start=0
i=2: expand(2,2) → "bab" (len=3), expand(2,3) → "" (len=0) → max=3
i=3: expand(3,3) → "a" (len=1), expand(3,4) → "" (len=0) → max=3
i=4: expand(4,4) → "d" (len=1) → max=3
Result: s.substring(0, 3) = "bab"
```

---

### 13. Detect and remove a Cycle in a Directed Graph.

**Answer:**

**Approach:** Use DFS + Recursion Stack

**Steps:**
1. Do DFS traversal
2. Keep a recursion stack set
3. If a visited node is in recursion stack → cycle found
4. To remove: remove the back edge causing the cycle

**Time Complexity:** O(V + E)  
**Space Complexity:** O(V)

```java
import java.util.*;

public class DirectedGraphCycle {
    
    private int vertices;
    private List<List<Integer>> adjList;
    
    public DirectedGraphCycle(int vertices) {
        this.vertices = vertices;
        adjList = new ArrayList<>();
        for (int i = 0; i < vertices; i++) {
            adjList.add(new ArrayList<>());
        }
    }
    
    public void addEdge(int from, int to) {
        adjList.get(from).add(to);
    }
    
    // Detect cycle using DFS
    public boolean hasCycle() {
        boolean[] visited = new boolean[vertices];
        boolean[] recStack = new boolean[vertices];  // Recursion stack
        
        for (int i = 0; i < vertices; i++) {
            if (!visited[i]) {
                if (dfs(i, visited, recStack)) return true;
            }
        }
        return false;
    }
    
    private boolean dfs(int node, boolean[] visited, boolean[] recStack) {
        visited[node] = true;
        recStack[node] = true;
        
        for (int neighbor : adjList.get(node)) {
            if (!visited[neighbor]) {
                if (dfs(neighbor, visited, recStack)) return true;
            } else if (recStack[neighbor]) {
                // Back edge found → Cycle detected!
                return true;
            }
        }
        
        recStack[node] = false;  // Backtrack
        return false;
    }
    
    // Detect AND remove cycle
    public boolean detectAndRemoveCycle() {
        boolean[] visited = new boolean[vertices];
        boolean[] recStack = new boolean[vertices];
        int[] parent = new int[vertices];
        Arrays.fill(parent, -1);
        
        for (int i = 0; i < vertices; i++) {
            if (!visited[i]) {
                if (dfsAndRemove(i, visited, recStack, parent)) return true;
            }
        }
        return false;
    }
    
    private boolean dfsAndRemove(int node, boolean[] visited, boolean[] recStack, int[] parent) {
        visited[node] = true;
        recStack[node] = true;
        
        Iterator<Integer> it = adjList.get(node).iterator();
        while (it.hasNext()) {
            int neighbor = it.next();
            if (!visited[neighbor]) {
                parent[neighbor] = node;
                if (dfsAndRemove(neighbor, visited, recStack, parent)) return true;
            } else if (recStack[neighbor]) {
                // Remove the back edge causing the cycle
                it.remove();  // Remove edge: node → neighbor
                System.out.println("Removed edge: " + node + " → " + neighbor);
                return true;
            }
        }
        
        recStack[node] = false;
        return false;
    }
    
    public static void main(String[] args) {
        DirectedGraphCycle graph = new DirectedGraphCycle(4);
        graph.addEdge(0, 1);
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        graph.addEdge(3, 1);  // Creates cycle: 1→2→3→1
        
        System.out.println("Has cycle: " + graph.hasCycle());  // true
        graph.detectAndRemoveCycle();  // Removes edge 3→1
        System.out.println("Has cycle after removal: " + graph.hasCycle());  // false
    }
}
```

---

### 14. Serialize and Deserialize a Binary Tree efficiently.

**Answer:**

**Approach:** Use Level Order Traversal (BFS)
- Perform BFS and store nodes in list
- Use "null" for missing children
- Rebuild tree level by level

**Deserialization:** Read list and rebuild the tree level by level.

```java
import java.util.*;

public class BinaryTreeCodec {
    
    public static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }
    
    // Serialize: BFS level-order
    public String serialize(TreeNode root) {
        if (root == null) return "[]";
        
        StringBuilder sb = new StringBuilder("[");
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            if (node == null) {
                sb.append("null,");
            } else {
                sb.append(node.val).append(",");
                queue.offer(node.left);
                queue.offer(node.right);
            }
        }
        
        sb.setLength(sb.length() - 1); // Remove trailing comma
        sb.append("]");
        return sb.toString();
    }
    
    // Deserialize: rebuild from level-order
    public TreeNode deserialize(String data) {
        if (data.equals("[]")) return null;
        
        String[] vals = data.substring(1, data.length() - 1).split(",");
        TreeNode root = new TreeNode(Integer.parseInt(vals[0].trim()));
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        int i = 1;
        while (!queue.isEmpty() && i < vals.length) {
            TreeNode parent = queue.poll();
            
            // Left child
            String leftVal = vals[i++].trim();
            if (!leftVal.equals("null")) {
                parent.left = new TreeNode(Integer.parseInt(leftVal));
                queue.offer(parent.left);
            }
            
            // Right child
            if (i < vals.length) {
                String rightVal = vals[i++].trim();
                if (!rightVal.equals("null")) {
                    parent.right = new TreeNode(Integer.parseInt(rightVal));
                    queue.offer(parent.right);
                }
            }
        }
        
        return root;
    }
    
    public static void main(String[] args) {
        BinaryTreeCodec codec = new BinaryTreeCodec();
        
        //       1
        //      / \
        //     2   3
        //    / \   \
        //   4   5   6
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.left.right = new TreeNode(5);
        root.right.right = new TreeNode(6);
        
        String serialized = codec.serialize(root);
        System.out.println(serialized);
        // [1,2,3,4,5,null,6,null,null,null,null,null,null]
        
        TreeNode deserialized = codec.deserialize(serialized);
        System.out.println(codec.serialize(deserialized)); // Same output
    }
}
```

**Time Complexity:** O(V + E) ≈ O(N)  
**Space Complexity:** O(N)

---

### 15. Design a Concurrent Rate Limiter using the Token Bucket Algorithm.

**Answer:**

**Token Bucket Concept:**
- Bucket has capacity = max tokens
- Tokens are refilled at a fixed rate
- On request:
  - If token available → allow & take token
  - Else → reject(throttle)

**Thread-Safe Implementation:**
- Store tokens count
- On each request, calculate new tokens based on elapsed time
- Use `ReentrantLock` or `Atomic` operations

```java
import java.util.concurrent.locks.ReentrantLock;

public class TokenBucketRateLimiter {
    
    private final int maxTokens;          // Bucket capacity
    private final double refillRate;      // Tokens per second
    private double currentTokens;
    private long lastRefillTimestamp;
    private final ReentrantLock lock = new ReentrantLock();
    
    public TokenBucketRateLimiter(int maxTokens, double refillRate) {
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;
        this.currentTokens = maxTokens;  // Start full
        this.lastRefillTimestamp = System.nanoTime();
    }
    
    public boolean tryAcquire() {
        lock.lock();
        try {
            refill();
            if (currentTokens >= 1.0) {
                currentTokens -= 1.0;
                return true;   // Request allowed
            }
            return false;      // Request rejected (throttled)
        } finally {
            lock.unlock();
        }
    }
    
    private void refill() {
        long now = System.nanoTime();
        double elapsed = (now - lastRefillTimestamp) / 1_000_000_000.0; // seconds
        double tokensToAdd = elapsed * refillRate;
        currentTokens = Math.min(maxTokens, currentTokens + tokensToAdd);
        lastRefillTimestamp = now;
    }
    
    // Usage example
    public static void main(String[] args) throws InterruptedException {
        // 10 requests per second, burst capacity of 10
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10.0);
        
        // Simulate concurrent requests
        for (int i = 0; i < 15; i++) {
            boolean allowed = limiter.tryAcquire();
            System.out.println("Request " + (i + 1) + ": " + (allowed ? "ALLOWED" : "REJECTED"));
        }
        
        // Wait for refill
        Thread.sleep(1000);
        System.out.println("\nAfter 1 second refill:");
        System.out.println("Request: " + (limiter.tryAcquire() ? "ALLOWED" : "REJECTED"));
    }
}
```

**Spring Boot Integration:**
```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    // Per-user rate limiters
    private final ConcurrentHashMap<String, TokenBucketRateLimiter> limiters = 
        new ConcurrentHashMap<>();
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                            Object handler) throws Exception {
        String userId = request.getHeader("X-User-Id");
        TokenBucketRateLimiter limiter = limiters.computeIfAbsent(userId, 
            k -> new TokenBucketRateLimiter(100, 10.0)); // 100 burst, 10/sec refill
        
        if (!limiter.tryAcquire()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Rate limit exceeded. Try again later.");
            return false;
        }
        return true;
    }
}
```

**Time Complexity:** O(1) per request

---
