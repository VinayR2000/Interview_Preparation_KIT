# 42. Production-Level Architecture

## Theory

Production-level architecture for Spring Boot applications combines all the individual components into a cohesive, scalable, resilient system. This is the culmination of all previous topics.

### Architecture Layers:
```
Client → Load Balancer → API Gateway → Microservices → Databases/Cache/Messaging
```

### Key Architectural Concerns:
- **Authentication/Authorization**: Who can access what
- **Rate Limiting**: Protect services from abuse
- **Caching**: Reduce database load
- **Database Scaling**: Read replicas, sharding
- **Messaging**: Async communication (Kafka)
- **Resilience**: Circuit breakers, retry, fallback
- **Observability**: Metrics, logging, tracing
- **Deployment**: Containers, Kubernetes, CI/CD
- **Failure Scenarios**: What happens when X goes down

---

## Internal Working

### Request Flow Through Production Stack:
```
Client (Browser/Mobile)
       ↓ HTTPS
Load Balancer (AWS ALB / Nginx)
  → SSL termination
  → Health check routing
  → Sticky sessions (if needed)
       ↓ HTTP
API Gateway (Spring Cloud Gateway / Kong)
  → Authentication (JWT validation)
  → Rate limiting (per client/IP)
  → Request routing (path → service)
  → Response caching
  → CORS handling
       ↓ HTTP (internal)
Service Discovery (Eureka / K8s DNS)
  → Resolve service name → IP:port
  → Client-side load balancing
       ↓
Microservice (Spring Boot)
  → Security filter chain
  → Request validation
  → Business logic
  → Circuit breaker for external calls
  → Event publishing
       ↓
Data Layer:
  → Redis (cache, sessions, distributed locks)
  → PostgreSQL (primary data store)
  → Kafka (async events, CQRS)
  → External APIs (payment, email, SMS)
```

### Failure Cascade Prevention:
```
Normal: A → B → C → DB

C's DB is slow:
  Without resilience:
    A waits → B waits → C waits → Threads exhausted → ALL services DOWN

  With resilience:
    C: Circuit breaker opens after 5 failures
    B: Gets fast failure from C, returns fallback
    A: Gets partial response, serves to client
    Result: Graceful degradation, not total failure
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRODUCTION ARCHITECTURE                        │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    CLIENTS                                │    │
│  │    Browser    Mobile App    Third-party    Internal       │    │
│  └────────────────────────┬────────────────────────────────┘    │
│                            │ HTTPS                                │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │              LOAD BALANCER (ALB/NLB)                      │    │
│  │    SSL Termination | Health Checks | Auto-scaling        │    │
│  └────────────────────────┬────────────────────────────────┘    │
│                            │                                      │
│  ┌────────────────────────┴────────────────────────────────┐    │
│  │              API GATEWAY                                  │    │
│  │    Auth | Rate Limiting | Routing | CORS | Caching       │    │
│  └───────┬──────────────┬──────────────┬──────────────────┘    │
│           │              │              │                         │
│  ┌────────┴───┐  ┌──────┴─────┐  ┌────┴──────────┐            │
│  │Order Service│  │User Service│  │Payment Service│            │
│  │ (3 pods)   │  │ (2 pods)   │  │ (2 pods)      │            │
│  │            │  │            │  │               │            │
│  │ CB + Retry │  │ CB + Retry │  │ CB + Retry    │            │
│  └──┬────┬───┘  └──┬────┬───┘  └──┬────┬──────┘            │
│     │    │          │    │          │    │                     │
│  ┌──┴────┴──────────┴────┴──────────┴────┴───────────────┐   │
│  │                INFRASTRUCTURE LAYER                      │   │
│  │                                                          │   │
│  │  ┌─────────┐  ┌───────────┐  ┌──────────────────────┐ │   │
│  │  │  Redis  │  │PostgreSQL │  │       Kafka          │ │   │
│  │  │ (Cache  │  │ (Primary  │  │  (Event Streaming)   │ │   │
│  │  │  + Lock)│  │ + Replica)│  │                      │ │   │
│  │  └─────────┘  └───────────┘  └──────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              OBSERVABILITY                                 │   │
│  │  Prometheus | Grafana | ELK/Loki | Jaeger/Zipkin         │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              KUBERNETES CLUSTER                            │   │
│  │  Deployments | HPA | ConfigMaps | Secrets | Ingress      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code

### API Gateway Configuration:

```yaml
# Spring Cloud Gateway
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderCB
                fallbackUri: forward:/fallback
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
            - name: Retry
              args:
                retries: 2
                statuses: SERVICE_UNAVAILABLE
```

### Service with Full Production Features:

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final InventoryClient inventoryClient;
    private final MeterRegistry meterRegistry;

    @Transactional
    @CircuitBreaker(name = "inventory", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(String idempotencyKey, CreateOrderRequest request) {
        // Idempotency check
        String cacheKey = "idempotent:order:" + idempotencyKey;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return (OrderResponse) cached;

        // Check inventory (circuit breaker protected)
        boolean inStock = inventoryClient.checkStock(request.getItems());
        if (!inStock) throw new InsufficientStockException(request.getItems());

        // Create order
        Order order = orderRepository.save(buildOrder(request));

        // Publish event (async processing)
        kafkaTemplate.send("order-events", 
            order.getId().toString(),
            new OrderCreatedEvent(order));

        // Cache response for idempotency
        OrderResponse response = toResponse(order);
        redisTemplate.opsForValue().set(cacheKey, response, Duration.ofHours(24));

        // Metrics
        meterRegistry.counter("orders.created", "status", "success").increment();

        return response;
    }

    private OrderResponse createOrderFallback(String key, CreateOrderRequest request, Throwable t) {
        log.warn("Inventory unavailable, queuing order for later processing");
        meterRegistry.counter("orders.created", "status", "queued").increment();
        // Queue for retry when inventory is back
        return new OrderResponse(null, OrderStatus.QUEUED, "Order queued for processing");
    }
}
```

### Kubernetes Deployment (Production):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      containers:
        - name: order-service
          image: registry.example.com/order-service:v2.1.0
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: 1000m
              memory: 1Gi
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: JAVA_OPTS
              value: "-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
          ports:
            - containerPort: 8080
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            failureThreshold: 30
            periodSeconds: 5
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 10
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### Failure Scenario Handling:

```java
// Database failure → Graceful degradation
@Service
public class ProductService {

    @Cacheable("products")
    @CircuitBreaker(name = "database", fallbackMethod = "getProductCacheFallback")
    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    // If DB is down but product in cache → serve stale cache
    // If not in cache either → return unavailable message
    private Product getProductCacheFallback(Long id, Throwable t) {
        log.error("DB unavailable for product {}, checking stale cache", id);
        Product stale = staleCacheService.get("product:" + id);
        if (stale != null) return stale;
        throw new ServiceUnavailableException("Product data temporarily unavailable");
    }
}
```

---

## Dry Run

### Complete Request Flow:

```
Client: POST /api/orders {customerId: 1, items: [{sku: "P1", qty: 2}]}
Header: Authorization: Bearer eyJ..., Idempotency-Key: abc-123

1. Load Balancer: Routes to API Gateway pod (health check passed)

2. API Gateway:
   - JWT validation → valid, user authenticated ✓
   - Rate limit check → 50/100 requests this minute ✓
   - Route: /api/orders → lb://ORDER-SERVICE
   - Load balance: Pick Order-Service Pod-2

3. Order Service (Pod-2):
   - Security context set from JWT
   - Idempotency check: Redis GET "idempotent:order:abc-123" → null (first time)
   - Inventory check: RestClient → Inventory Service
     - Circuit breaker: CLOSED (healthy) ✓
     - Response: {available: true}
   - Save to PostgreSQL: INSERT INTO orders... → Order #42
   - Publish to Kafka: "order-events" topic, key="42"
   - Cache response: Redis SET "idempotent:order:abc-123" = response, TTL 24h
   - Return: 201 Created {orderId: 42, status: "CREATED"}

4. Async Processing (Kafka consumers):
   - Payment Service: Listens "order-events" → processes payment
   - Inventory Service: Listens "order-events" → reserves stock
   - Notification Service: Listens "order-events" → sends email

5. Metrics emitted:
   - http_server_requests: method=POST, uri=/api/orders, status=201, duration=150ms
   - orders.created: status=success
   - hikaricp.connections.active: 5
```

### Failure Scenario: Database Down:

```
1. PostgreSQL becomes unreachable

2. Order Service:
   - Connection pool: Threads start waiting (connectionTimeout = 3s)
   - After 3s: SQLTransientConnectionException
   - Circuit breaker: Records failure (1/10)
   - After 5 failures in 10 calls: Circuit OPENS

3. Subsequent requests:
   - Circuit breaker OPEN → immediate fallback (no DB call)
   - Fallback: "Service temporarily unavailable, order queued"
   - HTTP 503 returned to client

4. Monitoring:
   - Alert: "order-service circuit breaker OPEN"
   - Grafana shows: error rate spike, DB connection failures
   - PagerDuty notification to on-call

5. Recovery:
   - DBA fixes PostgreSQL
   - Circuit breaker: HALF_OPEN after 30s
   - Allows 3 test requests through
   - All succeed → CLOSED (normal operation resumed)
   - Queued orders processed from retry queue
```

---

## Complexity

| Component | Scaling Strategy | Limits |
|-----------|-----------------|--------|
| API Gateway | Horizontal (stateless) | ~50K RPS per instance |
| Microservice | Horizontal (stateless) | CPU/memory bound |
| Redis | Cluster (sharding) | ~100K ops/sec per node |
| PostgreSQL | Read replicas + connection pooler | ~10K connections |
| Kafka | Partitions = parallelism | ~1M msg/sec per broker |

---

## Real Project Usage

### Complete Docker Compose for Local Development:

```yaml
version: '3.8'
services:
  gateway:
    image: order-gateway:latest
    ports: ["8080:8080"]
    environment:
      EUREKA_URI: http://eureka:8761/eureka

  order-service:
    image: order-service:latest
    deploy:
      replicas: 2
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/orders
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092

  postgres:
    image: postgres:15-alpine
    volumes: [postgres_data:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine

  kafka:
    image: confluentinc/cp-kafka:7.5.0

  prometheus:
    image: prom/prometheus
    volumes: [./prometheus.yml:/etc/prometheus/prometheus.yml]

  grafana:
    image: grafana/grafana
    ports: ["3000:3000"]
```

---

## Interview Questions

1. **Design a production-ready order service architecture. What components would you include?**
   - API Gateway (auth, rate limiting), Order Service (business logic, circuit breaker), PostgreSQL (data), Redis (cache, idempotency, locks), Kafka (async events to inventory/payment/notification), K8s (deployment, scaling, health probes), Prometheus+Grafana (monitoring).

2. **What happens when your database goes down? How do you handle it?**
   - Circuit breaker opens after N failures → fast failure. Serve cached data for reads (stale but available). Queue writes for retry. Alert ops team. On recovery: circuit half-opens, tests connectivity, resumes. Process queued operations.

3. **How do you ensure zero-downtime deployments?**
   - Rolling update (maxUnavailable=0). Readiness probe gates traffic (new pod must be ready). Graceful shutdown (complete in-flight requests). PreStop hook (drain LB connections). Database migrations backward-compatible.

4. **How would you handle a service that's being overwhelmed by traffic?**
   - Immediate: Rate limiting at gateway, auto-scaling (HPA). Short-term: Caching, async processing for non-critical paths. Long-term: Optimize hot paths, database scaling, CDN for static content, architectural review.

5. **Explain how you'd implement distributed tracing across all services.**
   - Micrometer Tracing / OpenTelemetry. TraceId generated at gateway, propagated via headers. Each service logs with traceId (MDC). Export spans to Jaeger/Zipkin. Grafana Tempo for visualization. Correlate logs ↔ traces ↔ metrics.

---

## Follow-up Questions

1. How do you handle data consistency between Order Service and Inventory Service?
   - Saga pattern or event-driven eventual consistency. Order created in PENDING state. Kafka event triggers inventory reservation. If stock insufficient, compensation event cancels order. Eventually consistent within seconds.

2. How do you decide between synchronous and asynchronous communication?
   - Synchronous (REST): When caller needs immediate response (stock check before order confirm). Asynchronous (Kafka): When caller doesn't need response (send email, update analytics). Critical path = sync. Side effects = async.

3. What's your database scaling strategy?
   - Read replicas for read-heavy workloads (route @Transactional(readOnly=true)). Connection pooler (PgBouncer) for connection management. Vertical scaling (bigger instance) before horizontal. Sharding as last resort.

4. How do you handle secret management in production?
   - Never in code or ConfigMaps. Use: AWS Secrets Manager / HashiCorp Vault / K8s External Secrets Operator. Rotate secrets automatically. Audit access. Mount as environment variables or volume.

5. What monitoring/alerting would you set up for this architecture?
   - RED metrics per service: Rate (RPS), Errors (error %), Duration (latency p95/p99). Infrastructure: CPU, memory, disk, network. Business: Orders/sec, payment success rate. Alert on: error rate > 1%, latency p99 > 500ms, circuit breaker open, pod restarts.

---

## Common Mistakes

1. **No circuit breakers** - One slow service takes down everything
2. **Synchronous everything** - Tight coupling, cascading failures
3. **No idempotency** - Retries create duplicate orders/payments
4. **Shared database** - Defeats microservice independence
5. **No rate limiting** - One bad client exhausts service for all
6. **Ignoring graceful shutdown** - Losing in-flight requests on deploy
7. **No observability** - Can't diagnose issues in production
8. **Over-engineering** - Starting with 20 microservices instead of modular monolith

---

## Best Practices

1. **Start simple, evolve** - Modular monolith → extract when needed
2. **Circuit breakers on ALL external calls** - DB, Redis, HTTP, Kafka
3. **Async for non-critical operations** - Email, analytics, audit
4. **Idempotency on all write endpoints** - Safe retries
5. **Observability from day one** - Metrics, logging, tracing
6. **Database per service** - Independent scaling and deployment
7. **Graceful degradation** - Serve partial/cached data rather than fail completely
8. **Automate everything** - CI/CD, scaling, alerting, recovery

---

## Production Considerations

- **Cost optimization**: Right-size pods, use spot instances, scale down at night
- **Security**: mTLS between services, network policies, secret rotation
- **Disaster recovery**: Multi-AZ deployment, backup strategy, RTO/RPO targets
- **Compliance**: Audit logging, data encryption at rest/transit, GDPR
- **Team organization**: Conway's Law — align service boundaries with team boundaries
- **Technical debt**: Regular dependency updates, deprecated API sunset plans
- **Capacity planning**: Load test at 2x expected peak before major launches

---

## Related Topics

- All previous topics (1-41) — this is the synthesis
- Microservices Architecture
- Resilience Patterns
- Kafka (event-driven communication)
- Kubernetes (deployment orchestration)
- Distributed Systems Concepts
- Spring Cloud (infrastructure components)
- Performance Tuning
