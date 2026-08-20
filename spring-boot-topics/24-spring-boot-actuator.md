# 24. Spring Boot Actuator

## Theory

Spring Boot Actuator provides production-ready features for monitoring and managing your application. It exposes operational information about the running application via HTTP endpoints or JMX.

### Key Endpoints:
- `/actuator/health` - Application health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics (JVM, HTTP, custom)
- `/actuator/prometheus` - Metrics in Prometheus format
- `/actuator/env` - Environment properties
- `/actuator/beans` - All registered beans
- `/actuator/mappings` - All @RequestMapping paths
- `/actuator/loggers` - View/change logging levels at runtime
- `/actuator/threaddump` - Thread dump

### Health Indicators:
- **Liveness**: Is the app running? (K8s uses to restart pod)
- **Readiness**: Can the app serve traffic? (K8s uses to route/unroute)
- **Custom**: Database, Redis, Kafka, external service health

### Metrics with Micrometer:
- Timers, Counters, Gauges, Distribution Summaries
- Export to Prometheus, Datadog, CloudWatch, etc.

---

## Internal Working

```
Application starts
       ↓
Actuator auto-configuration activates
       ↓
Registers endpoints as Spring MVC/WebFlux handlers
       ↓
HealthIndicator beans auto-detected:
  - DataSourceHealthIndicator (DB)
  - RedisHealthIndicator
  - KafkaHealthIndicator
  - DiskSpaceHealthIndicator
       ↓
MeterRegistry collects metrics:
  - JVM metrics (memory, GC, threads)
  - HTTP server metrics (requests, latency)
  - Custom application metrics
       ↓
Endpoints exposed via HTTP (configurable)
       ↓
GET /actuator/health
  → Aggregates all HealthIndicator results
  → Returns UP/DOWN with details
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                     │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Controllers  │  │   Services   │  │  Repositories    │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                 ACTUATOR LAYER                           │  │
│  │                                                          │  │
│  │  /health      → HealthIndicators (DB, Redis, Kafka)     │  │
│  │  /metrics     → MeterRegistry (counters, timers)        │  │
│  │  /prometheus  → PrometheusMeterRegistry (scrape)        │  │
│  │  /info        → InfoContributor (build, git, custom)    │  │
│  │  /loggers     → LoggingSystem (view/change levels)      │  │
│  └────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬─────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ↓                  ↓                  ↓
     ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
     │  Prometheus   │  │  Kubernetes  │  │  Grafana     │
     │  (scrape)     │  │  (probes)    │  │  (dashboard) │
     └──────────────┘  └──────────────┘  └──────────────┘
```

---

## Code

### Configuration:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, loggers, env
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized  # never | when-authorized | always
      probes:
        enabled: true  # Enable /health/liveness and /health/readiness
  health:
    db:
      enabled: true
    redis:
      enabled: true
    kafka:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active:default}

# Application info
info:
  app:
    name: Order Service
    version: 2.1.0
    description: Handles order processing
```

### Custom Health Indicator:

```java
@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private final PaymentGatewayClient paymentClient;

    @Override
    public Health health() {
        try {
            boolean isAvailable = paymentClient.ping();
            if (isAvailable) {
                return Health.up()
                    .withDetail("gateway", "Payment Gateway")
                    .withDetail("responseTime", "45ms")
                    .build();
            } else {
                return Health.down()
                    .withDetail("gateway", "Payment Gateway")
                    .withDetail("error", "Not responding")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("gateway", "Payment Gateway")
                .withException(e)
                .build();
        }
    }
}
```

### Custom Metrics:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final MeterRegistry meterRegistry;
    private final Counter orderCounter;
    private final Timer orderProcessingTimer;

    public OrderService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.orderCounter = Counter.builder("orders.created")
            .description("Total orders created")
            .tag("service", "order-service")
            .register(meterRegistry);
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Order processing duration")
            .register(meterRegistry);
    }

    public Order createOrder(CreateOrderRequest request) {
        return orderProcessingTimer.record(() -> {
            Order order = processOrder(request);
            orderCounter.increment();
            
            // Gauge: current pending orders
            meterRegistry.gauge("orders.pending",
                orderRepository.countByStatus(OrderStatus.PENDING));
            
            // Distribution summary: order amounts
            DistributionSummary.builder("orders.amount")
                .baseUnit("dollars")
                .register(meterRegistry)
                .record(order.getTotalAmount().doubleValue());
            
            return order;
        });
    }
}
```

### Readiness/Liveness for Kubernetes:

```java
@Component
public class CustomReadinessIndicator implements HealthIndicator {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Override
    public Health health() {
        if (ready.get()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "Warming up cache").build();
    }

    // Called after cache is warmed
    public void markReady() {
        ready.set(true);
    }
}
```

### Securing Actuator Endpoints:

```java
@Configuration
public class ActuatorSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
```

---

## Dry Run

### Health Check Response:

```json
GET /actuator/health

Response (200 OK):
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "brokerId": "1"
      }
    },
    "paymentGateway": {
      "status": "UP",
      "details": {
        "gateway": "Payment Gateway",
        "responseTime": "45ms"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500107862016,
        "free": 250054931008
      }
    }
  }
}
```

### Metrics Response:

```
GET /actuator/prometheus

# HELP orders_created_total Total orders created
# TYPE orders_created_total counter
orders_created_total{service="order-service"} 1247.0

# HELP orders_processing_time_seconds Order processing duration
# TYPE orders_processing_time_seconds summary
orders_processing_time_seconds_count 1247
orders_processing_time_seconds_sum 89.5

# HELP jvm_memory_used_bytes
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap"} 234881024
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| Health check | O(n) where n = health indicators |
| Metrics counter increment | O(1) |
| Prometheus scrape | O(m) where m = registered meters |
| Logger level change | O(1) |

---

## Real Project Usage

### Kubernetes Deployment with Probes:

```yaml
# deployment.yaml
spec:
  containers:
    - name: order-service
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 30
        periodSeconds: 10
        failureThreshold: 3
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 5
        failureThreshold: 3
      startupProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 5
        failureThreshold: 30
```

---

## Interview Questions

1. **What is Spring Boot Actuator and why is it important?**
   - Provides production-ready monitoring endpoints (health, metrics, info). Essential for observability in microservices and Kubernetes deployments.

2. **Difference between liveness and readiness probes?**
   - Liveness: "Is the app alive?" If fails, K8s restarts pod. Readiness: "Can it serve traffic?" If fails, K8s stops sending requests.

3. **How to add custom metrics?**
   - Inject MeterRegistry, create Counter/Timer/Gauge. Use Counter for events, Timer for durations, Gauge for current values.

4. **How to secure actuator endpoints?**
   - Separate SecurityFilterChain for /actuator/**. Permit health/prometheus publicly, require auth for sensitive endpoints (env, beans, loggers).

5. **How to change log level at runtime?**
   - POST /actuator/loggers/{loggerName} with body {"configuredLevel": "DEBUG"}. No restart needed.

---

## Follow-up Questions

1. How to integrate Actuator with Prometheus + Grafana monitoring stack?
   - Add micrometer-registry-prometheus dependency. Exposes /actuator/prometheus endpoint. Prometheus scrapes this endpoint. Grafana dashboards visualize metrics. Standard stack for production monitoring.

2. How to create custom Actuator endpoints?
   - `@Endpoint(id = "custom")` on a class with `@ReadOperation`, `@WriteOperation`, `@DeleteOperation` methods. Or extend AbstractHealthIndicator for custom health checks.

3. How to expose actuator on a different port for security?
   - `management.server.port=8081` — separates management from application traffic. Allows different firewall rules. Only internal network accesses management port.

4. How to implement graceful shutdown with actuator?
   - `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=30s`. On SIGTERM: stop accepting new requests, complete in-flight requests (up to timeout), then shutdown. K8s preStop hook helps.

5. How to track SLA metrics (p95, p99 latency) with Micrometer?
   - Use Timer.builder().publishPercentiles(0.95, 0.99) or publishPercentileHistogram(true). Prometheus calculates percentiles server-side from histograms. Alert on p99 > threshold.

---

## Common Mistakes

1. **Exposing all endpoints in production** - Security risk (env shows secrets, heapdump exposes memory)
2. **Health check calling external services** - Slow health checks can trigger false-positive restarts
3. **No metrics on business operations** - Only JVM metrics; missing order rates, error rates, etc.
4. **Liveness depending on external services** - DB down → liveness fails → pod restart loop (use readiness instead)
5. **Not using management port** - Actuator on same port as API; harder to secure
6. **show-details: always in production** - Exposes internal details publicly

---

## Best Practices

1. **Separate management port**: `management.server.port=8081`
2. **Liveness = internal only** (deadlock, OOM). Readiness = external dependencies
3. **Custom metrics for business KPIs** (orders/sec, revenue, error rate)
4. **Tag metrics** with service name, environment, version
5. **Export to Prometheus** with proper scrape interval (15-30s)
6. **Alert on health status changes** and metric thresholds
7. **Use @Timed annotation** for automatic method timing
8. **Secure sensitive endpoints** (env, beans, heapdump, threaddump)

---

## Production Considerations

- **Monitoring stack**: Actuator → Prometheus → Grafana (standard stack)
- **Alert rules**: CPU > 80%, memory > 85%, error rate > 1%, latency p99 > 500ms
- **Dashboard**: Service health, request rate, error rate, latency percentiles
- **Log aggregation**: Combine with structured logging for full observability
- **Distributed tracing**: Pair with Micrometer Tracing / OpenTelemetry
- **Cost**: High-cardinality metrics (per-user, per-request-id) are expensive — avoid

---

## Related Topics

- Kubernetes (health probes)
- Prometheus + Grafana (monitoring)
- Logging (observability pillar)
- Distributed Tracing (OpenTelemetry)
- Spring Security (endpoint protection)
- Microservices (per-service monitoring)
