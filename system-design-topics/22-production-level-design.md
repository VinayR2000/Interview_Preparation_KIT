# Production-Level Design

## Observability

### Theory
- The ability to understand a system's internal state from its external outputs
- Three pillars: Logs, Metrics, Traces
- Without observability, production issues are invisible

### Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                     Application                              │
│  ┌─────────┐    ┌─────────────┐    ┌─────────────────┐     │
│  │  Logs   │    │   Metrics   │    │ Distributed     │     │
│  │(events) │    │ (counters,  │    │   Traces        │     │
│  │         │    │  gauges,    │    │ (request flow)  │     │
│  │         │    │  histograms)│    │                 │     │
│  └────┬────┘    └─────┬───────┘    └────────┬────────┘     │
└───────┼───────────────┼──────────────────────┼──────────────┘
        │               │                      │
        ▼               ▼                      ▼
   ┌─────────┐    ┌──────────┐    ┌─────────────────────┐
   │ELK Stack│    │Prometheus│    │   Jaeger/Zipkin     │
   │Splunk   │    │Grafana   │    │   (Trace viewer)    │
   └─────────┘    └──────────┘    └─────────────────────┘
```

---

## Logging

### Theory
- Record of events happening in the system
- Structured logging (JSON) > unstructured (plain text) for searchability
- Log levels: TRACE < DEBUG < INFO < WARN < ERROR < FATAL

### Best Practices
```java
// Structured logging with context
@Slf4j
public class OrderService {
    
    public Order processOrder(OrderRequest request) {
        // Add context for all logs in this request
        MDC.put("orderId", request.getOrderId());
        MDC.put("userId", request.getUserId());
        MDC.put("traceId", request.getTraceId());
        
        try {
            log.info("Processing order", 
                kv("amount", request.getAmount()),
                kv("items", request.getItems().size()));
            
            Order order = createOrder(request);
            
            log.info("Order created successfully",
                kv("orderStatus", order.getStatus()),
                kv("totalAmount", order.getTotal()));
            
            return order;
        } catch (InsufficientInventoryException e) {
            log.warn("Order failed: insufficient inventory",
                kv("failedItems", e.getFailedItems()));
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing order", e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}
```

### What to Log
| Level | When | Example |
|-------|------|---------|
| ERROR | System failed, needs attention | Payment gateway down, data corruption |
| WARN | Potential problem, degraded | Cache miss rate high, retry succeeded |
| INFO | Business events, state changes | Order created, user logged in |
| DEBUG | Diagnostic detail | SQL query, request/response bodies |
| TRACE | Very detailed execution flow | Method entry/exit, loop iterations |

### What NOT to Log
- Passwords, tokens, credit card numbers (security!)
- PII without masking (GDPR compliance)
- Every iteration of a loop (log volume explosion)
- Binary data

---

## Metrics

### Key Metric Types
| Type | Description | Example |
|------|-------------|---------|
| Counter | Monotonically increasing value | Total requests, errors |
| Gauge | Current value (goes up and down) | CPU usage, queue size |
| Histogram | Distribution of values | Request latency percentiles |
| Summary | Similar to histogram, calculates quantiles | Response time p50, p95, p99 |

### Essential Metrics (RED + USE)

**RED Method (for services):**
- **R**ate: Requests per second
- **E**rrors: Failed requests per second
- **D**uration: Latency distribution (p50, p95, p99)

**USE Method (for resources):**
- **U**tilization: % of resource being used
- **S**aturation: Queue depth / waiting work
- **E**rrors: Error count for the resource

### Code
```java
// Using Micrometer (Spring Boot)
@Component
public class OrderMetrics {
    private final Counter orderCounter;
    private final Counter orderFailureCounter;
    private final Timer orderProcessingTimer;
    private final Gauge activeOrdersGauge;
    
    public OrderMetrics(MeterRegistry registry) {
        this.orderCounter = Counter.builder("orders.created")
            .description("Total orders created")
            .tag("service", "order-service")
            .register(registry);
        
        this.orderProcessingTimer = Timer.builder("orders.processing.duration")
            .description("Order processing time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
    
    public void recordOrderCreated(String type) {
        orderCounter.increment();
    }
    
    public <T> T timeOrderProcessing(Supplier<T> operation) {
        return orderProcessingTimer.record(operation);
    }
}
```

---

## Distributed Tracing

### Theory
- Track a request as it flows through multiple services
- Each service adds a "span" to the trace
- Helps identify: slow services, failure points, dependencies

### Trace Structure
```
Trace ID: abc-123
├── Span: API Gateway (5ms)
│   ├── Span: Auth Service (2ms)
│   └── Span: Order Service (45ms)
│       ├── Span: Inventory Service (10ms)
│       ├── Span: Payment Service (25ms)
│       │   └── Span: Stripe API call (20ms)
│       └── Span: Notification Service (3ms) [async]
```

### Implementation
```java
// Propagate trace context between services
// HTTP Header: traceparent: 00-{traceId}-{spanId}-{flags}

// Spring Boot + Micrometer Tracing (auto-instrumented)
// Just add dependencies — most instrumentation is automatic:
// - HTTP clients (RestTemplate, WebClient)
// - Database queries
// - Kafka producers/consumers
// - Custom spans for business operations

@Observed(name = "order.processing")
public Order processOrder(OrderRequest request) {
    // Span automatically created by @Observed
    // Trace context automatically propagated to downstream calls
}
```

---

## Health Checks

### Types
```java
// Liveness: Is the service alive? (restart if not)
@Component
public class LivenessHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Basic: can the service respond?
        return Health.up().build();
    }
}

// Readiness: Can the service handle requests? (stop sending traffic if not)
@Component
public class ReadinessHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        if (!databasePool.isHealthy()) return Health.down().withDetail("db", "pool exhausted").build();
        if (!cacheConnection.isConnected()) return Health.down().withDetail("cache", "disconnected").build();
        if (circuitBreaker.isOpen()) return Health.down().withDetail("dependency", "circuit open").build();
        return Health.up().build();
    }
}
```

### Health Check Endpoints
```
GET /actuator/health/liveness   → Kubernetes liveness probe
GET /actuator/health/readiness  → Kubernetes readiness probe

Response:
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "pool.active": 5, "pool.max": 20 } },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP", "details": { "free": "50GB" } }
  }
}
```

---

## Graceful Shutdown

### Theory
- Stop accepting new requests
- Complete in-flight requests
- Close resources (DB connections, message consumers)
- Exit cleanly

### Code
```java
@Configuration
public class GracefulShutdownConfig {
    
    @Bean
    public GracefulShutdownFilter gracefulShutdownFilter() {
        return new GracefulShutdownFilter();
    }
}

// Spring Boot 2.3+: Set in application.yml
// server.shutdown: graceful
// spring.lifecycle.timeout-per-shutdown-phase: 30s

// Custom shutdown hook
@Component
public class ShutdownHook {
    private final KafkaConsumer consumer;
    private final ExecutorService executor;
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutdown initiated");
        
        // 1. Stop consuming new messages
        consumer.pause();
        
        // 2. Wait for in-progress work to complete
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        // 3. Close connections
        consumer.close();
        log.info("Shutdown complete");
    }
}
```

---

## Configuration Management

### Externalized Configuration
```yaml
# application.yml — environment-specific
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

---
spring.config.activate.on-profile: production
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  redis:
    host: ${REDIS_HOST}
    port: 6379
```

### Configuration Hierarchy
```
1. Command-line arguments (highest priority)
2. Environment variables
3. application-{profile}.yml
4. application.yml
5. Default values in code (lowest priority)
```

---

## Feature Flags

### Theory
- Toggle functionality on/off without deployment
- Enable gradual rollouts, A/B testing, kill switches
- Decouple deployment from release

### Code
```java
public interface FeatureFlagService {
    boolean isEnabled(String flagName);
    boolean isEnabled(String flagName, String userId); // User-specific
}

// Usage
public class PaymentService {
    @Autowired
    private FeatureFlagService featureFlags;
    
    public PaymentResult processPayment(PaymentRequest request) {
        if (featureFlags.isEnabled("new-payment-flow", request.getUserId())) {
            return newPaymentProcessor.process(request);
        }
        return legacyPaymentProcessor.process(request);
    }
}
```

### Rollout Strategies
| Strategy | Description |
|----------|-------------|
| Boolean | On/off for everyone |
| Percentage | Enable for X% of users |
| User list | Enable for specific users |
| Gradual | 1% → 5% → 25% → 50% → 100% |
| Ring-based | Internal → Beta → GA |

---

## Secrets Management

### Principles
- Never store secrets in code or config files
- Use environment variables or secret stores
- Rotate secrets regularly
- Audit access to secrets

### Tools and Approaches
| Tool | Description |
|------|-------------|
| AWS Secrets Manager | Managed secret store, auto-rotation |
| HashiCorp Vault | Self-hosted, dynamic secrets |
| Kubernetes Secrets | k8s native (base64, not encrypted by default) |
| Environment variables | Simple but limited |

---

## Deployment Strategies

### Blue-Green Deployment
```
┌────────────────┐    ┌────────────────┐
│   BLUE (v1)    │    │  GREEN (v2)    │
│  (current)     │    │  (new)         │
│  ●●●●●●●●●●   │    │  ○○○○○○○○○○   │
└────────┬───────┘    └────────┬───────┘
         │                     │
         └──────┬──────────────┘
                │
         ┌──────▼──────┐
         │ Load Balancer│ ← Switch traffic: Blue → Green
         └─────────────┘

Steps:
1. Deploy v2 to Green (Blue still serving)
2. Test Green independently
3. Switch LB to point to Green
4. Keep Blue as rollback (instant rollback if issues)
5. Next release: deploy to Blue
```

### Canary Deployment
```
┌────────────────────────────────────────────────┐
│           Production (v1) — 95% traffic        │
│  ●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●   │
└────────────────────────────────────────────────┘
┌──────────────┐
│ Canary (v2)  │ ← 5% traffic, monitor closely
│  ○○           │
└──────────────┘

Progression: 5% → 10% → 25% → 50% → 100%
Monitor at each stage: error rate, latency, business metrics
Auto-rollback if metrics degrade
```

### Comparison
| Strategy | Rollback Speed | Resource Cost | Risk | Complexity |
|----------|---------------|---------------|------|------------|
| Blue-Green | Instant (switch LB) | 2x resources | Low | Medium |
| Canary | Fast (route back) | +small% resources | Very low | High |
| Rolling | Slow (redeploy) | No extra | Medium | Low |
| Feature Flag | Instant (toggle) | No extra | Low | Medium |

---

## Interview Questions

**Q: How would you add observability to a new microservice?**
> 1. Structured logging (JSON, correlation IDs, MDC)
> 2. Metrics: RED method (rate, errors, duration) via Micrometer
> 3. Distributed tracing: Auto-instrument with OpenTelemetry
> 4. Health checks: Liveness + Readiness endpoints
> 5. Dashboards: Grafana with alerting on error rate and latency p99
> 6. Alerting: PagerDuty integration for critical issues

**Q: How do you handle configuration changes without restart?**
> 1. Spring Cloud Config with @RefreshScope (trigger refresh endpoint)
> 2. Feature flag service (LaunchDarkly, Unleash) for runtime toggles
> 3. Kubernetes ConfigMaps with volume mounts (auto-reload on change)
> 4. Event-driven config: Listen to config change events
> 5. For secrets: Vault agent with lease renewal

**Q: How would you implement a zero-downtime deployment?**
> 1. Blue-Green or Canary deployment strategy
> 2. Graceful shutdown: Drain connections before termination
> 3. Readiness probe: New pods ready before receiving traffic
> 4. Rolling update with surge: Extra pods during transition
> 5. Database migrations: Backward-compatible (expand then contract)
> 6. Feature flags: Decouple deployment from release

**Q: What metrics would you alert on for a payment service?**
> Critical alerts:
> - Error rate > 1% (immediate)
> - Latency p99 > 5s (immediate)
> - Success rate drops below 99.9%
> - Payment provider errors (circuit breaker open)
> Warning alerts:
> - Latency p95 increase > 50%
> - Queue depth growing
> - Connection pool utilization > 80%

---

## Common Mistakes
- Logging sensitive data (PII, passwords, tokens)
- Not using structured logging (hard to search/aggregate)
- Missing correlation IDs (can't trace requests across services)
- Alerting on everything (alert fatigue)
- Not testing deployment rollback procedures
- Feature flags that never get cleaned up (technical debt)
- Health checks that don't check real dependencies

---

## Best Practices
- Log at the right level (don't spam INFO with DEBUG-level details)
- Use correlation IDs across all services
- Alert on symptoms (error rate) not causes (CPU usage)
- Test your rollback procedures regularly
- Clean up feature flags after full rollout
- Graceful shutdown in every service
- Immutable deployments (never modify running containers)

---

## Production Considerations
- Log retention policy (cost vs debugging needs)
- Metrics cardinality limits (don't create unbounded label values)
- Tracing sampling rate (100% in dev, 1-10% in prod for cost)
- Runbook for every alert (what to do when it fires)
- Post-incident reviews (document and learn)
- Capacity planning based on metrics trends
- Chaos engineering to validate resilience

---

## Related Topics
- Kubernetes Deployments
- CI/CD Pipelines
- Monitoring (Prometheus + Grafana)
- Incident Management
- SRE Practices
