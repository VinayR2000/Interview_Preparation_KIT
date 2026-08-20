# 25. Logging

## Theory

Logging is a fundamental pillar of application observability. Spring Boot uses SLF4J as the logging facade with Logback as the default implementation.

### Logging Stack:
- **SLF4J**: Logging facade (API) — code depends on this
- **Logback**: Default implementation in Spring Boot
- **Log4j2**: Alternative implementation (higher throughput)

### Log Levels (increasing severity):
- **TRACE**: Finest detail (method entry/exit, variable values)
- **DEBUG**: Diagnostic information for development
- **INFO**: General operational messages (startup, shutdown, key events)
- **WARN**: Potential issues that aren't failures yet
- **ERROR**: Errors that need attention but app continues

### Key Concepts:
- **Structured Logging**: JSON-formatted logs for machine parsing
- **MDC (Mapped Diagnostic Context)**: Thread-local context for correlation
- **Correlation ID**: Unique ID tracking a request across services
- **Log Masking**: Hiding sensitive data (passwords, PII)

---

## Internal Working

```
Application code: log.info("Order {} created", orderId)
       ↓
SLF4J API (Logger interface)
       ↓
Logback implementation
       ↓
┌────────────────────────────────────────────┐
│ Logger hierarchy:                           │
│   ROOT → com → example → service           │
│                                             │
│ Level check:                                │
│   Is INFO >= configured level for logger?   │
│   ├── YES → Continue to appender           │
│   └── NO → Discard (very cheap)            │
│                                             │
│ Layout/Encoder:                              │
│   Format: timestamp level thread logger msg │
│   Or JSON structured format                 │
│                                             │
│ Appender (output):                           │
│   ConsoleAppender → stdout                  │
│   FileAppender → rolling file               │
│   AsyncAppender → non-blocking queue        │
└────────────────────────────────────────────┘
       ↓
Log output → Collected by log aggregator (ELK, CloudWatch, etc.)
```

### MDC Flow:
```
Incoming HTTP Request
       ↓
Filter extracts/generates correlationId
       ↓
MDC.put("correlationId", "abc-123")
       ↓
All log statements in this thread include correlationId
       ↓
log.info("Processing order") 
  → "2024-01-15 10:30:00 [abc-123] INFO Processing order"
       ↓
Filter removes MDC on response
  MDC.clear()
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION                                │
│                                                              │
│  log.info("Order {} created for user {}", orderId, userId)  │
│       │                                                      │
│       ↓                                                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              SLF4J (Facade)                           │    │
│  └──────────────────────┬──────────────────────────────┘    │
│                          ↓                                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              LOGBACK (Implementation)                  │    │
│  │                                                        │    │
│  │  Logger: com.example.service.OrderService             │    │
│  │  Level: INFO ✓ (passes level check)                   │    │
│  │                                                        │    │
│  │  MDC Context: {correlationId=abc-123, userId=42}      │    │
│  │                                                        │    │
│  │  ┌──────────────────────────────────────────────┐    │    │
│  │  │ Appenders:                                    │    │    │
│  │  │  ├── ConsoleAppender → stdout (JSON)         │    │    │
│  │  │  ├── RollingFileAppender → logs/app.log      │    │    │
│  │  │  └── AsyncAppender → non-blocking wrapper    │    │    │
│  │  └──────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
         │                              │
         ↓                              ↓
┌──────────────────┐         ┌──────────────────┐
│     CONSOLE       │         │  LOG AGGREGATOR  │
│  (Docker stdout)   │         │  (ELK/Loki)     │
└──────────────────┘         └──────────────────┘
```

---

## Code

### Basic Configuration (application.yml):

```yaml
logging:
  level:
    root: INFO
    com.example.service: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{correlationId}] %-5level [%thread] %logger{36} - %msg%n"
  file:
    name: logs/application.log
```

### Structured Logging (logback-spring.xml):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console: JSON for production, pattern for dev -->
    <springProfile name="prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>correlationId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
            </encoder>
        </appender>
    </springProfile>

    <springProfile name="!prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%X{correlationId:-}] %-5level [%thread] %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
    </springProfile>

    <!-- Rolling File Appender -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>3GB</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <!-- Async wrapper for performance -->
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="FILE"/>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC_FILE"/>
    </root>
</configuration>
```

### Correlation ID Filter:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### Request/Response Logging:

```java
@Component
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        log.info("Incoming request: {} {} from {}",
            request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

        filterChain.doFilter(request, response);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Outgoing response: {} {} → {} ({}ms)",
            request.getMethod(), request.getRequestURI(), 
            response.getStatus(), duration);
    }
}
```

### Sensitive Data Masking:

```java
@Slf4j
@Service
public class UserService {

    // BAD - logs sensitive data
    // log.info("User registered: {}", user);  // May contain password, SSN

    // GOOD - log only safe fields
    public void registerUser(UserRegistrationDTO dto) {
        log.info("User registration: email={}, role={}", 
            maskEmail(dto.getEmail()), dto.getRole());
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
```

### Service Layer Logging:

```java
@Service
@Slf4j
public class OrderService {

    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());
        log.debug("Order details: items={}, total={}", 
            request.getItems().size(), request.getTotalAmount());

        try {
            Order order = processOrder(request);
            log.info("Order created successfully: orderId={}, status={}", 
                order.getId(), order.getStatus());
            return order;
        } catch (InsufficientStockException e) {
            log.warn("Insufficient stock for order: customerId={}, items={}", 
                request.getCustomerId(), e.getFailedItems());
            throw e;
        } catch (Exception e) {
            log.error("Failed to create order for customer {}: {}", 
                request.getCustomerId(), e.getMessage(), e);
            throw e;
        }
    }
}
```

---

## Dry Run

### Request with Correlation ID:

```
1. Client sends: GET /api/orders/42
   Header: X-Correlation-ID: req-abc-123

2. CorrelationIdFilter:
   → MDC.put("correlationId", "req-abc-123")

3. RequestLoggingFilter:
   → log.info("Incoming request: GET /api/orders/42 from 192.168.1.1")
   Output: 2024-01-15 10:30:00.123 [req-abc-123] INFO Incoming request: GET /api/orders/42

4. Controller → Service:
   → log.info("Fetching order: orderId=42")
   Output: 2024-01-15 10:30:00.125 [req-abc-123] INFO Fetching order: orderId=42

5. Repository query:
   → log.debug("SQL: SELECT * FROM orders WHERE id=42")
   Output: 2024-01-15 10:30:00.130 [req-abc-123] DEBUG SQL query executed

6. Response:
   → log.info("Outgoing response: GET /api/orders/42 → 200 (15ms)")
   Output: 2024-01-15 10:30:00.138 [req-abc-123] INFO Outgoing response: 200 (15ms)

7. MDC.clear() — correlationId removed from thread
```

---

## Complexity

| Operation | Cost |
|-----------|------|
| Level check (disabled level) | ~1 nanosecond (very cheap) |
| Log statement (sync) | ~1-5 microseconds |
| Log statement (async) | ~0.5 microsecond (queue + background write) |
| MDC put/get | O(1) - ThreadLocal HashMap |
| String formatting (parameterized) | Only if level enabled |

---

## Real Project Usage

### Microservice Logging with Tracing:

```java
@Component
public class TracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response,
                                     FilterChain chain) throws Exception {
        MDC.put("correlationId", getOrCreateCorrelationId(request));
        MDC.put("service", "order-service");
        MDC.put("userId", extractUserId(request));
        MDC.put("traceId", getTraceId());
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

JSON output in production:
```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.example.service.OrderService",
  "message": "Order created successfully",
  "correlationId": "req-abc-123",
  "traceId": "trace-xyz-789",
  "service": "order-service",
  "userId": "42",
  "orderId": "123",
  "duration": 15
}
```

---

## Interview Questions

1. **What is SLF4J and why use it?**
   - Logging facade (interface). Decouples code from implementation (Logback/Log4j2). Easy to switch implementations without code changes.

2. **What is MDC and when would you use it?**
   - Mapped Diagnostic Context: thread-local key-value store. Used for correlation IDs, user context. Automatically included in log output.

3. **Difference between parameterized and concatenated logging?**
   - Parameterized: `log.debug("User {}", id)` — formatting only happens if level enabled. Concatenated: `log.debug("User " + id)` — string always built (wasteful if DEBUG disabled).

4. **How to implement correlation ID tracking across microservices?**
   - Generate/extract from header in filter → MDC → include in all logs → propagate to downstream HTTP calls via header.

5. **How to change log level at runtime without restart?**
   - Spring Boot Actuator: POST /actuator/loggers/{name} with {"configuredLevel": "DEBUG"}

---

## Follow-up Questions

1. How to implement log aggregation with ELK stack?
   - Application logs JSON to stdout → Filebeat/Fluentd collects → Logstash processes/enriches → Elasticsearch stores/indexes → Kibana visualizes. In K8s: stdout collected by DaemonSet automatically.

2. How to handle logging in async/multi-threaded scenarios (MDC propagation)?
   - MDC is ThreadLocal, lost in child threads. Use TaskDecorator to copy MDC map before async execution. For @Async: configure executor with decorating wrapper. Clear MDC after task completes.

3. How to implement audit logging vs application logging?
   - Audit logging: Separate logger/appender, structured (who, what, when, where), stored permanently, compliance requirement. Application logging: Operational (debug, errors), rotated, shorter retention. Different log levels and destinations.

4. What's the performance impact of logging and how to minimize it?
   - Synchronous file logging can block threads under load. Minimize: Use AsyncAppender (queue-based), parameterized logging (avoid string concatenation), appropriate log levels, avoid logging in tight loops.

5. How to implement sensitive data masking in logs?
   - Custom pattern layout that regex-replaces sensitive patterns (credit cards, SSNs). Or use Logback's converter. Mark sensitive fields in DTOs and mask in toString(). Never log passwords, tokens, full PII.

---

## Common Mistakes

1. **String concatenation in log statements** - `log.debug("user " + id)` always evaluates even if DEBUG disabled
2. **Logging sensitive data** - Passwords, tokens, PII in logs
3. **Too verbose in production** - DEBUG level in prod = disk/performance issues
4. **Not using MDC** - Impossible to trace requests across log lines
5. **Synchronous file logging under load** - Use AsyncAppender for file writes
6. **Not logging exceptions properly** - `log.error("Error: " + e.getMessage())` loses stack trace. Use `log.error("Error", e)`
7. **Missing contextual information** - Logging "failed" without what, who, why

---

## Best Practices

1. **Use parameterized logging**: `log.info("Order {} created", orderId)`
2. **Always include correlation ID** for request tracing
3. **Structured logging (JSON)** in production for machine parsing
4. **Log at appropriate levels** - INFO for business events, DEBUG for technical detail
5. **Include context** - WHO (userId), WHAT (action), WHERE (orderId), OUTCOME
6. **Mask sensitive data** - Never log passwords, tokens, PII
7. **Use AsyncAppender** for file/network outputs
8. **Limit stack trace logging** - Don't log full trace for expected exceptions

---

## Production Considerations

- **Log volume**: High-traffic services generate massive logs. Use sampling for DEBUG/TRACE.
- **Storage costs**: JSON logs are larger. Set retention policies (7-30 days typical).
- **Performance**: Async appenders prevent logging from blocking request threads.
- **Aggregation**: Use ELK (Elasticsearch, Logstash, Kibana) or Loki + Grafana.
- **MDC in async code**: MDC doesn't propagate to child threads automatically. Use TaskDecorator.
- **Container logging**: In Docker/K8s, log to stdout (collected by container runtime).
- **Log levels in production**: INFO default, DEBUG via actuator for temporary troubleshooting.

---

## Related Topics

- Spring Boot Actuator (runtime log level changes)
- Distributed Tracing (OpenTelemetry)
- Async Processing (MDC propagation)
- Exception Handling (what to log)
- Spring Security (audit logging)
- Microservices (distributed logging)
