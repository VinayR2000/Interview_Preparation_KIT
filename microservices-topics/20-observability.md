# 20. Observability ⭐⭐⭐⭐⭐

## Theory

Observability is the ability to understand a system's internal state from its external outputs. In microservices, a single request traverses multiple services — without observability, debugging is nearly impossible.

### Three Pillars:

| Pillar | What It Captures | Tools |
|--------|-----------------|-------|
| Logs | Discrete events, errors, state changes | ELK Stack, Loki, CloudWatch |
| Metrics | Numerical measurements over time | Prometheus, Grafana, Micrometer |
| Traces | Request journey across services | Jaeger, Zipkin, OpenTelemetry |

### Key Concepts:
- **Correlation ID**: Unique ID that follows a request across all services
- **Trace ID**: Identifies the entire distributed request
- **Span ID**: Identifies a single operation within a trace
- **Structured Logging**: JSON logs with consistent fields
- **Centralized Logging**: All service logs in one searchable place

---

## Internal Working

### Distributed Tracing Flow:

```
┌──────────────────────────────────────────────────────────────┐
│ DISTRIBUTED TRACE: "Create Order" request                     │
│                                                               │
│ TraceId: abc-123-def (same across all services)              │
│                                                               │
│ API Gateway          SpanId: span-1                          │
│ ├── Validate JWT     duration: 5ms                           │
│ ├── Route request    duration: 2ms                           │
│ │                                                            │
│ Order Service        SpanId: span-2 (parent: span-1)        │
│ ├── Create order     duration: 15ms                          │
│ ├── Save to DB       duration: 8ms                           │
│ ├── Call Payment     SpanId: span-3 (parent: span-2)        │
│ │   │                                                        │
│ │   Payment Service  SpanId: span-4 (parent: span-3)        │
│ │   ├── Validate     duration: 3ms                           │
│ │   ├── Charge       duration: 200ms                         │
│ │   └── Return       duration: 1ms                           │
│ │                    Total: 204ms                             │
│ │                                                            │
│ ├── Publish event    duration: 5ms                           │
│ └── Return response  duration: 2ms                           │
│                                                               │
│ Total trace duration: 240ms                                  │
│ Bottleneck identified: Payment.Charge (200ms = 83% of time) │
└──────────────────────────────────────────────────────────────┘
```

### Metrics Architecture:

```
┌────────────────────────────────────────────────────────────┐
│ METRICS PIPELINE                                            │
│                                                             │
│ Services expose metrics:                                   │
│                                                             │
│ ┌──────────────┐  /actuator/prometheus                    │
│ │ Order Service│ ────────────────────┐                    │
│ └──────────────┘                     │                    │
│ ┌──────────────┐  /actuator/prometheus│                   │
│ │Payment Service│ ───────────────────┤                    │
│ └──────────────┘                     │                    │
│ ┌──────────────┐  /actuator/prometheus│                   │
│ │ User Service │ ───────────────────┤                    │
│ └──────────────┘                     │                    │
│                                      ↓                    │
│                            ┌──────────────────┐          │
│                            │   Prometheus     │          │
│                            │   (scrapes every │          │
│                            │    15 seconds)   │          │
│                            └────────┬─────────┘          │
│                                     │                     │
│                                     ↓                     │
│                            ┌──────────────────┐          │
│                            │    Grafana       │          │
│                            │   (dashboards)   │          │
│                            │                  │          │
│                            │ ┌──────────────┐│          │
│                            │ │Request Rate  ││          │
│                            │ │Error Rate    ││          │
│                            │ │Latency P99   ││          │
│                            │ │CPU/Memory    ││          │
│                            │ └──────────────┘│          │
│                            └──────────────────┘          │
│                                     │                     │
│                                     ↓                     │
│                            ┌──────────────────┐          │
│                            │   AlertManager   │          │
│                            │  (alert on       │          │
│                            │   thresholds)    │          │
│                            └──────────────────┘          │
└────────────────────────────────────────────────────────────┘
```

### Centralized Logging:

```
┌────────────────────────────────────────────────────────────┐
│ CENTRALIZED LOGGING (ELK Stack)                             │
│                                                             │
│ Services produce structured JSON logs:                     │
│                                                             │
│ {"timestamp":"2024-01-15T10:30:00Z",                      │
│  "level":"INFO",                                           │
│  "service":"order-service",                                │
│  "traceId":"abc-123",                                      │
│  "spanId":"span-2",                                        │
│  "userId":"user-456",                                      │
│  "message":"Order created",                                │
│  "orderId":"order-789"}                                    │
│                                                             │
│ Log Pipeline:                                              │
│ Services → Filebeat → Logstash → Elasticsearch → Kibana   │
│                                                             │
│ Or with Kubernetes:                                        │
│ Pod stdout → Fluentd/Fluent Bit → Elasticsearch → Kibana  │
│                                                             │
│ Search: traceId="abc-123"                                  │
│ → Shows ALL logs across ALL services for this request     │
│ → In chronological order                                  │
│ → With full context (userId, orderId, etc.)              │
└────────────────────────────────────────────────────────────┘
```

---

## Diagram

```
RED Metrics (Rate, Errors, Duration):

For every service, track:

┌────────────────────────────────────────────┐
│ RATE (Request throughput)                   │
│ http_requests_total                         │
│ "How many requests per second?"            │
│ Normal: 500 req/s                          │
│ Alert: > 2000 req/s (possible DDoS)        │
├────────────────────────────────────────────┤
│ ERRORS (Error rate)                         │
│ http_requests_total{status="5xx"}          │
│ "What percentage of requests fail?"        │
│ Normal: < 0.1%                             │
│ Alert: > 1% error rate                     │
├────────────────────────────────────────────┤
│ DURATION (Latency)                          │
│ http_request_duration_seconds              │
│ "How long do requests take?"               │
│ P50: 50ms, P95: 200ms, P99: 500ms         │
│ Alert: P99 > 1s                            │
└────────────────────────────────────────────┘
```

---

## Code

### Structured Logging with Correlation ID:

```java
// MDC Filter — adds traceId to all logs
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) 
            throws ServletException, IOException {
        
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put("traceId", traceId);
        MDC.put("service", "order-service");
        
        response.setHeader("X-Trace-Id", traceId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

```yaml
# logback-spring.xml — structured JSON output
logging:
  pattern:
    console: >
      {"timestamp":"%d{ISO8601}","level":"%level","service":"${spring.application.name}",
       "traceId":"%X{traceId}","spanId":"%X{spanId}","class":"%logger{36}",
       "message":"%msg"}%n
```

### Custom Metrics with Micrometer:

```java
@Service
@Slf4j
public class OrderService {

    private final MeterRegistry meterRegistry;
    private final Counter orderCreatedCounter;
    private final Timer orderProcessingTimer;
    private final AtomicInteger activeOrders;

    public OrderService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.orderCreatedCounter = Counter.builder("orders.created.total")
            .description("Total orders created")
            .tag("service", "order-service")
            .register(meterRegistry);
            
        this.orderProcessingTimer = Timer.builder("orders.processing.duration")
            .description("Order processing time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
            
        this.activeOrders = meterRegistry.gauge("orders.active.count", 
            new AtomicInteger(0));
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        return orderProcessingTimer.record(() -> {
            activeOrders.incrementAndGet();
            try {
                OrderResponse response = processOrder(request);
                orderCreatedCounter.increment();
                
                // Tag-based metrics for different order types
                meterRegistry.counter("orders.created", 
                    "type", request.getType(),
                    "region", request.getRegion()
                ).increment();
                
                return response;
            } finally {
                activeOrders.decrementAndGet();
            }
        });
    }
}
```

### Distributed Tracing with OpenTelemetry:

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% in dev, lower in prod
  otlp:
    tracing:
      endpoint: http://jaeger:4318/v1/traces
```

```java
// Traces are automatic with Spring Boot 3 + Micrometer Tracing
// But you can add custom spans:
@Service
public class PaymentService {

    private final Tracer tracer;

    public PaymentResponse processPayment(PaymentRequest request) {
        // Custom span for detailed tracing
        Span span = tracer.nextSpan().name("process-payment").start();
        
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("orderId", request.getOrderId().toString());
            span.tag("amount", request.getAmount().toString());
            
            // This generates child spans automatically
            PaymentResponse response = callPaymentGateway(request);
            
            span.tag("paymentId", response.getPaymentId());
            span.event("payment-completed");
            
            return response;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### Health Checks:

```java
@Component
public class PaymentServiceHealthIndicator implements HealthIndicator {

    private final WebClient paymentClient;

    @Override
    public Health health() {
        try {
            paymentClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .block();
            
            return Health.up()
                .withDetail("paymentService", "reachable")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("paymentService", "unreachable")
                .withException(e)
                .build();
        }
    }
}
```

---

## Interview Questions

1. **What are the three pillars of observability?**
   - Logs (discrete events), Metrics (numerical measurements over time), Traces (request journey across services). All three needed together — logs tell you what happened, metrics tell you the trend, traces tell you the path.

2. **What is distributed tracing?**
   - Tracking a request as it flows across multiple services. Each service adds a span. Trace ID links all spans of one request. Helps identify bottlenecks, failures, and latency sources across the distributed system.

3. **What is a Correlation ID?**
   - Unique identifier that travels with a request across all services. Added at API Gateway, propagated via headers. All logs for one user request can be found by searching this ID.

4. **What metrics should every service expose?**
   - RED: Rate (request count), Errors (error rate/count), Duration (latency percentiles). Also: CPU, memory, thread count, DB connection pool, circuit breaker state, Kafka consumer lag.

5. **How to debug a slow request in microservices?**
   - Use distributed trace: find trace by ID, see waterfall of spans, identify which service/operation took longest. Combine with logs (filter by traceId) and metrics (check if latency spike is systemic).

6. **Centralized logging — why not just kubectl logs?**
   - Pods are ephemeral (logs lost on restart). Can't search across services. No correlation. Centralized logging (ELK, Loki) provides: persistent storage, cross-service search, correlation ID filtering, dashboards.

---

## Common Mistakes

1. **No correlation ID** — Can't trace a request across services
2. **Unstructured logs** — Can't parse or search effectively
3. **Sampling too aggressively** — Miss important traces in production
4. **Metrics without alerts** — Dashboards no one watches
5. **Not logging enough context** — userId, orderId, requestId in every log
6. **Separate tools, no correlation** — Can't jump from metric spike to related traces

---

## Best Practices

1. **Structured JSON logging** — Machine-parseable, consistent fields
2. **Correlation ID in every log** — traceId, spanId, userId
3. **RED metrics for every service** — Rate, Errors, Duration
4. **Distributed tracing** — OpenTelemetry for automatic instrumentation
5. **Alert on symptoms, not causes** — Alert on error rate, not "disk full"
6. **Dashboards per service + aggregate** — Both micro and macro view
7. **Trace propagation via headers** — Automatic with Spring Cloud Sleuth/Micrometer
