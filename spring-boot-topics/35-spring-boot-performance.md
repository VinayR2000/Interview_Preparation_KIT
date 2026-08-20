# 35. Spring Boot Performance

## Theory

Performance tuning in Spring Boot involves identifying bottlenecks across the full stack: application code, JVM, database, network, and infrastructure. The goal is to maximize throughput and minimize latency under production load.

### Performance Dimensions:
- **Latency**: Time to respond to a single request (p50, p95, p99)
- **Throughput**: Requests handled per second (RPS)
- **Resource Utilization**: CPU, memory, threads, connections
- **Scalability**: Ability to handle increasing load

### Key Areas to Diagnose:

| Area | Metrics | Tools |
|------|---------|-------|
| Application | Response time, error rate | Micrometer, Actuator |
| CPU | Usage %, thread count | JFR, VisualVM |
| Memory | Heap usage, GC pauses | JFR, GC logs |
| Threads | Active, blocked, waiting | Thread dump, JMC |
| Connection Pool | Active, idle, waiting | HikariCP metrics |
| Database | Query time, slow queries | Hibernate stats, pgBadger |
| API | Latency percentiles, timeouts | Prometheus, Grafana |

### Common Performance Issues:
- N+1 queries (database)
- Connection pool exhaustion
- Memory leaks (heap growth)
- Thread pool saturation
- Slow garbage collection
- Retry amplification (cascading retries)
- Unindexed queries
- Large response payloads

---

## Internal Working

```
Request arrives
       ↓
┌─── Performance Bottleneck Points ────────────────────┐
│                                                       │
│ 1. Thread Pool (Tomcat)                              │
│    → Are threads available? Or all busy?             │
│    → Default: 200 threads max                        │
│                                                       │
│ 2. Controller/Service Processing                     │
│    → CPU-bound computation?                          │
│    → Inefficient algorithms?                         │
│    → Blocking I/O calls?                             │
│                                                       │
│ 3. Connection Pool (HikariCP)                        │
│    → Are connections available?                      │
│    → Long-running transactions holding connections?  │
│                                                       │
│ 4. Database                                          │
│    → Slow queries? Missing indexes?                  │
│    → Lock contention?                                │
│    → N+1 problem?                                    │
│                                                       │
│ 5. External Services                                 │
│    → Timeout? Slow response?                         │
│    → No circuit breaker?                             │
│                                                       │
│ 6. JVM                                               │
│    → GC pauses? Memory pressure?                     │
│    → ClassLoader issues?                             │
└───────────────────────────────────────────────────────┘
```

### GC Impact:
```
Request Processing Timeline (with GC pause):

Normal:    |---process---|---response---|  (50ms)
With GC:   |---process---|--GC PAUSE--|---response---|  (250ms)

Stop-the-world GC pauses block ALL application threads.
G1GC: Typical pause 10-50ms
ZGC: Pause < 1ms (Java 17+)
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                    PERFORMANCE MONITORING STACK                    │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                SPRING BOOT APPLICATION                        │ │
│  │                                                               │ │
│  │  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐   │ │
│  │  │ Actuator │  │ Micrometer   │  │ Hibernate Stats    │   │ │
│  │  │ /health  │  │ Timers       │  │ Slow query log     │   │ │
│  │  │ /metrics │  │ Counters     │  │ N+1 detection      │   │ │
│  │  └──────────┘  └──────────────┘  └────────────────────┘   │ │
│  └────────────────────────┬────────────────────────────────────┘ │
│                            │ /actuator/prometheus                  │
│                            ↓                                       │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    PROMETHEUS (scrape every 15s)              │ │
│  └────────────────────────┬────────────────────────────────────┘ │
│                            ↓                                       │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    GRAFANA (dashboards + alerts)              │ │
│  │                                                               │ │
│  │  [Request Rate] [p99 Latency] [Error Rate] [CPU] [Memory]   │ │
│  │  [DB Pool]      [GC Pauses]   [Thread Pool] [Kafka Lag]     │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ADDITIONAL TOOLS:                                                │
│  JFR (Java Flight Recorder) → Low-overhead production profiling  │
│  VisualVM → Development profiling (heap, threads, CPU)           │
│  JMC (Java Mission Control) → Analyze JFR recordings             │
│  async-profiler → Flame graphs for CPU/allocation hot spots      │
└──────────────────────────────────────────────────────────────────┘
```

---

## Code

### Performance Monitoring Configuration:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
      sla:
        http.server.requests: 100ms, 500ms, 1s
    tags:
      application: ${spring.application.name}

spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true  # Dev only — shows query counts
        session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 100
```

### Custom Performance Metrics:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MeterRegistry meterRegistry;
    private final OrderRepository orderRepository;

    public Order createOrder(CreateOrderRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Order order = processOrder(request);
            sample.stop(Timer.builder("order.creation")
                .tag("status", "success")
                .register(meterRegistry));
            return order;
        } catch (Exception e) {
            sample.stop(Timer.builder("order.creation")
                .tag("status", "error")
                .tag("exception", e.getClass().getSimpleName())
                .register(meterRegistry));
            throw e;
        }
    }
}
```

### Connection Pool Monitoring:

```java
@Component
@Slf4j
public class ConnectionPoolHealthCheck {

    private final HikariDataSource dataSource;

    @Scheduled(fixedRate = 30000)
    public void monitor() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        int waiting = pool.getThreadsAwaitingConnection();

        if (waiting > 0) {
            log.warn("POOL PRESSURE: active={}, idle={}, waiting={}, total={}",
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                waiting,
                pool.getTotalConnections());
        }
    }
}
```

### N+1 Detection:

```java
// Hibernate statistics to detect N+1
@Component
public class QueryCountInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, Object handler) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        request.setAttribute("queryCount", stats.getQueryExecutionCount());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response, Object handler, Exception ex) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = (long) request.getAttribute("queryCount");
        long queriesExecuted = stats.getQueryExecutionCount() - before;

        if (queriesExecuted > 10) {
            log.warn("POTENTIAL N+1: {} queries for {} {}", 
                queriesExecuted, request.getMethod(), request.getRequestURI());
        }
    }
}
```

### JVM Tuning for Containers:

```dockerfile
# Dockerfile ENTRYPOINT with performance tuning
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+OptimizeStringConcat \
  -Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=10M"
```

### Slow Query Logging:

```yaml
# PostgreSQL slow query detection
spring:
  jpa:
    properties:
      hibernate:
        session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 50
  datasource:
    hikari:
      leak-detection-threshold: 30000  # 30 seconds
```

### Thread Pool Tuning:

```yaml
server:
  tomcat:
    threads:
      max: 200        # Max worker threads
      min-spare: 20   # Min idle threads
    max-connections: 10000
    accept-count: 100  # Queue when all threads busy
    connection-timeout: 5000  # 5s connection timeout
```

---

## Dry Run

### Diagnosing Slow Response (p99 = 2s):

```
1. Check Grafana dashboard:
   - p99 latency spiked from 100ms to 2000ms at 14:00
   - Correlates with traffic increase (500 → 2000 RPS)

2. Check HikariCP metrics:
   - Active connections: 20/20 (MAXED OUT!)
   - Threads waiting: 150+ (POOL EXHAUSTION)
   - → Bottleneck: Connection pool too small

3. Check database:
   - Slow query log shows: SELECT with no index → 500ms per query
   - Long transactions holding connections (2-3 seconds)
   - → Root cause: Missing index + long transactions

4. Fix:
   a. Add database index: CREATE INDEX idx_orders_status ON orders(status, created_at)
   b. Split read/write: @Transactional(readOnly=true) for reads
   c. Increase pool: maximum-pool-size: 30 (temporary)
   d. Optimize query: Use projection instead of full entity load

5. After fix:
   - Query time: 500ms → 2ms
   - p99 latency: 2000ms → 80ms
   - Connection pool waiting: 150 → 0
```

### Memory Leak Detection:

```
1. Observe: Heap usage grows continuously, never returns to baseline after GC

2. JFR recording:
   jcmd <pid> JFR.start duration=60s filename=leak.jfr

3. Analyze in JMC:
   - Object allocation: List<Order> growing unbounded
   - Source: OrderCache holding references without TTL

4. Fix: Add TTL to cache, use WeakReferences, or configure Caffeine eviction
```

---

## Complexity

| Optimization | Impact | Effort |
|-------------|--------|--------|
| Add database index | 10-1000x query speedup | Low |
| Fix N+1 (JOIN FETCH) | 5-50x fewer queries | Low |
| Connection pool tuning | Eliminates waiting | Low |
| Enable response compression | 50-80% smaller payload | Low |
| Switch to ZGC | Sub-ms GC pauses | Low |
| Implement caching | Eliminate DB calls entirely | Medium |
| Read replica for reads | 2x read capacity | Medium |
| Async processing | Free up request threads | Medium |
| Horizontal scaling | Linear capacity increase | High |
| Redesign architecture | Fundamental improvement | Very High |

---

## Real Project Usage

### Production Performance Dashboard Metrics:

```java
@Configuration
public class MetricsConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config()
            .commonTags("service", "order-service")
            .commonTags("env", System.getenv("ENVIRONMENT"));
    }
}

// Key metrics to track:
// 1. http_server_requests_seconds (p50, p95, p99)
// 2. hikaricp_connections_active
// 3. hikaricp_connections_pending
// 4. jvm_gc_pause_seconds
// 5. jvm_memory_used_bytes
// 6. process_cpu_usage
// 7. order_creation_seconds (custom business metric)
```

---

## Interview Questions

1. **How do you identify and fix N+1 query problems?**
   - Enable Hibernate statistics (query count per request). If queries >> expected, check for lazy collections accessed in loops. Fix: JOIN FETCH, @EntityGraph, or DTO projections. Verify with `spring.jpa.show-sql=true`.

2. **How do you diagnose connection pool exhaustion?**
   - Monitor HikariCP metrics: threads_awaiting > 0, active_connections = max. Causes: Long transactions, slow queries, connection leaks. Fix: Optimize queries, reduce transaction scope, increase pool (temporarily), enable leak detection.

3. **What GC settings do you use for Spring Boot in production?**
   - G1GC (default) for general use. ZGC for low-latency requirements (sub-ms pauses). Set MaxRAMPercentage=75.0 in containers. Monitor GC pause time and frequency. Tune with `-XX:MaxGCPauseMillis=200`.

4. **How do you handle retry amplification in microservices?**
   - Retry amplification: Service A retries 3x → Service B retries 3x = 9 downstream calls. Fix: Only retry at the edge (client-facing), use exponential backoff, jitter, budget-based retry limits. Circuit breaker to stop retries when service is down.

5. **What's your approach to performance testing a Spring Boot app?**
   - Load test with realistic data (JMeter, Gatling, k6). Measure p50/p95/p99 latency under expected and peak load. Monitor resources during test. Identify bottleneck (CPU/memory/DB/network). Fix and re-test. Baseline before production.

---

## Follow-up Questions

1. How to profile a Spring Boot app in production without impacting performance?
   - Use Java Flight Recorder (JFR) — designed for always-on production use with <2% overhead. `jcmd <pid> JFR.start duration=60s`. Analyze with JMC. No need to restart application.

2. What's the difference between throughput-oriented and latency-oriented GC tuning?
   - Throughput: Parallel GC, larger heaps, longer but fewer pauses (batch processing). Latency: G1GC/ZGC, shorter pauses, more frequent GC cycles (web applications). ZGC best for latency-sensitive APIs.

3. How to handle thread pool saturation in Tomcat?
   - Monitor active threads. If consistently near max (200): optimize slow requests, add caching, use @Async for heavy processing, or scale horizontally. Increase max-threads as temporary fix only.

4. How do you detect memory leaks in Spring Boot?
   - Growing heap that doesn't reclaim after Full GC. Use JFR/heap dump analysis. Common causes: unbounded caches, event listeners not unregistered, static collections, ThreadLocal not cleared.

5. What metrics indicate your Spring Boot app needs horizontal scaling?
   - Consistent CPU > 70%, response time degrading under load, thread pool saturation, connection pool exhaustion despite tuning. Vertical scaling (bigger instance) hits diminishing returns.

---

## Common Mistakes

1. **Premature optimization** - Profile first, optimize based on data, not assumptions
2. **Missing database indexes** - Single biggest cause of slow APIs in practice
3. **Not monitoring in production** - "Works on my machine" doesn't scale
4. **Ignoring GC logs** - Long GC pauses cause latency spikes invisible without monitoring
5. **Over-fetching data** - SELECT * with lazy collections instead of projections
6. **Not using @Transactional(readOnly=true)** - Misses Hibernate optimization for read queries
7. **Synchronous external calls without timeout** - One slow service blocks everything
8. **Connection pool too small for load** - Causes queuing and timeout cascades

---

## Best Practices

1. **Measure before optimizing** - Profile, don't guess
2. **Monitor continuously** - Prometheus + Grafana from day one
3. **Index all WHERE/JOIN columns** in database
4. **Use read-only transactions** for queries
5. **Enable response GZIP compression** for payloads > 1KB
6. **Cache hot data** (Redis/Caffeine) — most reads don't need real-time
7. **Set timeouts on everything** — HTTP clients, DB queries, thread pools
8. **Use async for non-critical paths** — email, notifications, analytics
9. **Right-size connection pool** — formula: (core_count * 2) + spindle_count
10. **Profile with JFR in production** — zero-cost observability

---

## Production Considerations

- **Baseline metrics**: Establish normal before comparing abnormal
- **Alerting**: CPU > 80%, p99 > 500ms, error rate > 1%, GC pause > 500ms
- **Capacity planning**: Load test at 2x expected peak before production launch
- **Gradual rollout**: Canary deployment + metric comparison to catch performance regressions
- **Auto-scaling**: HPA based on CPU + custom metrics (request rate, latency)
- **Database performance**: Connection pooler (PgBouncer), read replicas, query plan analysis
- **CDN**: Serve static assets and cacheable API responses from CDN edge

---

## Related Topics

- Spring Boot Actuator (metrics exposure)
- Database Connection Pool (HikariCP tuning)
- Caching (reducing DB load)
- Async Processing (thread optimization)
- Docker + Kubernetes (resource limits, scaling)
- Resilience Patterns (preventing cascade failures)
