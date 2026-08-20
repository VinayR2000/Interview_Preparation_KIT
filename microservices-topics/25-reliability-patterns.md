# 25. Reliability Patterns

## Theory

Reliability ensures services remain operational and recover gracefully from failures. In microservices, every component can fail — reliability patterns ensure the system continues functioning.

### Key Patterns:

| Pattern | Purpose |
|---------|---------|
| Health Checks | Detect unhealthy instances |
| Graceful Shutdown | Drain connections before stopping |
| Self-Healing | Automatically restart failed instances |
| Backpressure | Slow down when overwhelmed |
| Queue-Based Load Leveling | Buffer spikes with a queue |
| Disaster Recovery | Recover from catastrophic failures |

---

## Internal Working

### Health Checks (Liveness vs Readiness):

```
┌────────────────────────────────────────────────────────────┐
│ HEALTH CHECKS                                               │
│                                                             │
│ LIVENESS PROBE:                                           │
│ "Is the process alive and not deadlocked?"                │
│ Failure → Kubernetes RESTARTS the pod                     │
│                                                             │
│ Examples of liveness failure:                              │
│ - Application deadlocked                                  │
│ - Out of memory (but process still running)               │
│ - Infinite loop consuming CPU                             │
│                                                             │
│ READINESS PROBE:                                          │
│ "Is the service ready to handle traffic?"                 │
│ Failure → Kubernetes STOPS sending traffic (but no restart)│
│                                                             │
│ Examples of readiness failure:                             │
│ - Still warming up (loading cache)                        │
│ - Database connection lost                                │
│ - Downstream dependency is down                           │
│ - Temporarily overloaded                                  │
│                                                             │
│ ┌──────────────────────────────────────────┐             │
│ │ Pod Lifecycle:                            │             │
│ │                                           │             │
│ │ Start → [Not Ready] → Readiness ✓ → [Ready]           │
│ │                                    ↓                    │
│ │                            Receives traffic             │
│ │                                    ↓                    │
│ │                            DB goes down                 │
│ │                                    ↓                    │
│ │                            Readiness ✗ → [Not Ready]   │
│ │                            (traffic stopped)            │
│ │                                    ↓                    │
│ │                            DB recovers                  │
│ │                                    ↓                    │
│ │                            Readiness ✓ → [Ready]       │
│ │                            (traffic resumes)            │
│ └──────────────────────────────────────────┘             │
└────────────────────────────────────────────────────────────┘
```

### Graceful Shutdown:

```
┌────────────────────────────────────────────────────────────┐
│ GRACEFUL SHUTDOWN                                           │
│                                                             │
│ WITHOUT graceful shutdown:                                 │
│   SIGTERM received → process killed immediately           │
│   → Active requests get 502/connection reset              │
│   → Database transactions left incomplete                 │
│   → Kafka messages not committed                          │
│                                                             │
│ WITH graceful shutdown:                                    │
│   1. SIGTERM received                                     │
│   2. Stop accepting NEW requests (readiness → false)      │
│   3. Wait for in-flight requests to complete (30s max)    │
│   4. Close database connections                           │
│   5. Commit Kafka offsets                                 │
│   6. Deregister from service registry                     │
│   7. Shutdown                                             │
│                                                             │
│ Timeline:                                                  │
│ ─────────────────────────────────────────────────────     │
│ SIGTERM    Stop new    Drain active    Close DB    Exit    │
│   ↓       requests     requests       connections  ↓      │
│   T=0     T=0          T=0..30s       T=30s       T=30s  │
└────────────────────────────────────────────────────────────┘
```

### Backpressure:

```
┌────────────────────────────────────────────────────────────┐
│ BACKPRESSURE                                                │
│                                                             │
│ WITHOUT backpressure:                                     │
│   Producer: 10,000 msg/s                                  │
│   Consumer: can handle 1,000 msg/s                        │
│   → Consumer overwhelmed → OOM → crash → messages lost   │
│                                                             │
│ WITH backpressure:                                        │
│   Consumer signals "slow down" to producer               │
│   Or: bounded queue rejects when full                    │
│                                                             │
│ Strategies:                                               │
│                                                             │
│ 1. Bounded Queue:                                        │
│    Producer → [Queue: max 1000] → Consumer               │
│    Queue full → reject/block producer                    │
│                                                             │
│ 2. Rate Limiting at Consumer:                            │
│    Consumer polls at its own pace                        │
│    Kafka: consumer.poll(maxRecords=100)                  │
│                                                             │
│ 3. Reactive Streams:                                     │
│    Subscriber requests N items at a time                  │
│    Publisher sends only what's requested                  │
│                                                             │
│ 4. Load Shedding:                                        │
│    When overloaded, reject lowest-priority requests       │
│    Return 503 (Service Unavailable)                      │
└────────────────────────────────────────────────────────────┘
```

### Queue-Based Load Leveling:

```
┌────────────────────────────────────────────────────────────┐
│ QUEUE-BASED LOAD LEVELING                                   │
│                                                             │
│ Traffic spike:                                            │
│ ████████████████████ (10x normal load)                   │
│                                                             │
│ WITHOUT queue:                                            │
│   All requests hit service directly → overwhelmed → crash │
│                                                             │
│ WITH queue (Kafka/SQS):                                   │
│                                                             │
│ Spike hits    Messages     Consumers process              │
│    │          buffered     at steady pace                  │
│    ↓          in queue                                     │
│ ████████ → [Queue:█████████] → Consumer [████]           │
│                                  (constant rate)          │
│                                                             │
│ Queue absorbs the spike                                   │
│ Consumers process at their own pace                      │
│ No service crash, just increased latency                 │
│                                                             │
│ Real example:                                             │
│ Flash sale → 100K orders/sec                             │
│ Order API → Kafka → Order Processor (1K/sec)            │
│ Users get "Order received" immediately                    │
│ Processing happens asynchronously at sustainable rate    │
└────────────────────────────────────────────────────────────┘
```

---

## Code

### Health Check Implementation:

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final RedisTemplate<String, String> redis;
    private final KafkaTemplate<String, String> kafka;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();
        boolean healthy = true;

        // Check database
        try {
            dataSource.getConnection().isValid(2);
            details.put("database", "UP");
        } catch (Exception e) {
            details.put("database", "DOWN: " + e.getMessage());
            healthy = false;
        }

        // Check Redis
        try {
            redis.getConnectionFactory().getConnection().ping();
            details.put("redis", "UP");
        } catch (Exception e) {
            details.put("redis", "DOWN: " + e.getMessage());
            healthy = false;
        }

        return healthy 
            ? Health.up().withDetails(details).build()
            : Health.down().withDetails(details).build();
    }
}
```

### Graceful Shutdown Configuration:

```yaml
# application.yml
server:
  shutdown: graceful  # Enable graceful shutdown

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # Max 30s to drain
```

```java
@Component
public class GracefulShutdownHandler {

    private final KafkaListenerEndpointRegistry kafkaRegistry;
    private final ExecutorService executorService;

    @PreDestroy
    public void shutdown() {
        log.info("Graceful shutdown initiated...");
        
        // 1. Stop Kafka consumers (stop accepting new messages)
        kafkaRegistry.stop();
        log.info("Kafka consumers stopped");
        
        // 2. Wait for in-progress tasks
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(25, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                log.warn("Forced shutdown of remaining tasks");
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Graceful shutdown complete");
    }
}
```

### Kubernetes Probes:

```yaml
spec:
  containers:
    - name: order-service
      image: order-service:latest
      ports:
        - containerPort: 8081
      
      # Readiness: ready to accept traffic?
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8081
        initialDelaySeconds: 10
        periodSeconds: 5
        failureThreshold: 3
      
      # Liveness: is the process healthy?
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8081
        initialDelaySeconds: 30
        periodSeconds: 10
        failureThreshold: 3
      
      # Startup: give time to initialize
      startupProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8081
        initialDelaySeconds: 5
        periodSeconds: 5
        failureThreshold: 30  # 30 × 5s = 150s max startup time

      lifecycle:
        preStop:
          exec:
            command: ["sh", "-c", "sleep 5"]  # Allow LB to drain
```

### Load Shedding:

```java
@Component
public class LoadSheddingFilter extends OncePerRequestFilter {

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private static final int MAX_CONCURRENT = 500;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        int current = activeRequests.incrementAndGet();
        
        try {
            if (current > MAX_CONCURRENT) {
                // Shed load — reject request
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.getWriter().write(
                    "{\"error\": \"Service overloaded, please retry later\"}");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            activeRequests.decrementAndGet();
        }
    }
}
```

---

## Interview Questions

1. **Liveness vs Readiness probe — what's the difference?**
   - Liveness: "Is process alive?" Failure → restart pod. Readiness: "Can it handle traffic?" Failure → stop sending traffic (but don't restart). Wrong liveness probe = cascading restarts. Wrong readiness = traffic to unready pod.

2. **Why is graceful shutdown important?**
   - Without it: active requests get connection reset, DB transactions incomplete, Kafka offsets not committed. With it: drain in-flight work, commit state, deregister from discovery. Zero dropped requests during deployment.

3. **What is backpressure?**
   - Mechanism for consumers to signal producers to slow down when overwhelmed. Prevents OOM crashes and cascading failures. Implemented via bounded queues, rate limiting, or reactive streams (request N items at a time).

4. **Queue-based load leveling — when to use?**
   - When incoming rate > processing rate during spikes. Queue buffers excess work. Consumers process at sustainable rate. Trade-off: increased latency during spikes but no failures. E-commerce flash sales, batch uploads.

5. **How does Kubernetes self-healing work?**
   - Liveness probes detect unhealthy pods → restart. ReplicaSet ensures desired pod count → replaces terminated pods. Node failures → pods rescheduled to healthy nodes. Continuous reconciliation.

---

## Best Practices

1. **Separate liveness from readiness** — Don't restart healthy but unready pods
2. **Graceful shutdown everywhere** — Spring Boot `server.shutdown=graceful`
3. **preStop hook** — Sleep 5-10s to let load balancer update before draining
4. **Startup probe for slow apps** — Prevent premature liveness failures during boot
5. **Load shedding** — Better to reject some requests than crash entirely
6. **Queue for spikes** — Async processing smooths traffic peaks
7. **Chaos testing** — Kill pods randomly to verify self-healing works
