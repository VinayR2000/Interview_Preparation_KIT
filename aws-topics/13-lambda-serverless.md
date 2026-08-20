# Lambda and Serverless

## Theory

AWS Lambda runs code without provisioning servers. You upload code, define triggers, and pay only for compute time used (per millisecond). No servers to manage, auto-scales to thousands of concurrent executions.

---

## Diagram

### Common Lambda Architectures

```
Pattern 1: API Gateway + Lambda + DynamoDB (Fully Serverless)
  Client → API Gateway → Lambda → DynamoDB

Pattern 2: Event Processing
  S3 Upload → Lambda → Process Image → Save to S3

Pattern 3: Async Processing  
  SQS Queue → Lambda → Process Message → RDS

Pattern 4: Scheduled Tasks
  EventBridge (cron) → Lambda → Cleanup/Reports

Pattern 5: Stream Processing
  DynamoDB Stream → Lambda → Elasticsearch/SNS
```

### Lambda Execution Model
```
Cold Start (first invocation):
  Download code → Initialize runtime → Initialize handler → Execute
  [~100ms-3s for Java]

Warm Start (subsequent invocations):
  Execute (reuse existing container)
  [~1-5ms overhead]
```

---

## Internal Working

### Key Configuration

| Setting | Description | Limits |
|---------|-------------|--------|
| Runtime | Java 17, Node.js 20, Python 3.12 | |
| Memory | 128 MB – 10,240 MB | CPU scales with memory |
| Timeout | 1s – 15 min | |
| Concurrency | Default 1000 per account | Increase via quota |
| Package size | 50 MB (zip), 250 MB (unzipped) | Use layers or container images |
| Ephemeral storage | /tmp: 512 MB – 10 GB | |

### Cold Start Problem (Java)
Java Lambdas have slower cold starts (2-5s) due to JVM initialization.

**Solutions**:
1. **Provisioned Concurrency** — keep N instances warm (costs more)
2. **SnapStart** (Java 11+) — snapshots initialized JVM (sub-second cold start)
3. **GraalVM Native Image** — compiles to native binary (fast start, complex build)
4. **Keep warm** — scheduled ping every 5 minutes (hack, not recommended)

---

## Code

### Spring Cloud Function on Lambda

```java
@SpringBootApplication
public class OrderFunction {
    @Bean
    public Function<OrderEvent, OrderResult> processOrder() {
        return event -> {
            // Process the order
            Payment payment = paymentService.charge(event.getTotal());
            return new OrderResult(event.getOrderId(), payment.getStatus());
        };
    }
}
```

### Simple Lambda Handler (No Spring)
```java
public class OrderHandler implements RequestHandler<SQSEvent, Void> {
    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSMessage message : event.getRecords()) {
            OrderEvent order = objectMapper.readValue(message.getBody(), OrderEvent.class);
            processOrder(order);
        }
        return null;
    }
}
```

---

## API Gateway

### Types

| Type | Use Case | Features |
|------|----------|----------|
| REST API | Full-featured REST | Caching, WAF, request validation |
| HTTP API | Simple, fast, cheap | Basic routing, JWT auth, 70% cheaper |

### Architecture
```
Client → API Gateway (HTTPS) → Lambda → DynamoDB
                │
                ├── Authentication (Cognito, JWT)
                ├── Rate Limiting (throttling)
                ├── Request Validation
                ├── Caching (optional)
                └── Usage Plans (API keys)
```

---

## Interview Questions and Answers

**Q: When would you use Lambda vs ECS/EKS for a Spring Boot app?**
> Lambda: Short-lived, event-driven tasks (process an upload, handle webhook, scheduled jobs, simple APIs). ECS/EKS: Long-running services, complex applications, WebSockets, high sustained throughput, sub-millisecond latency requirements. Most Spring Boot microservices: ECS/EKS (persistent, stateful connections to DB, long startup). Specific functions: Lambda (file processing, notifications, scheduled cleanup).

**Q: How do you solve Lambda cold start for Java?**
> (1) SnapStart: Checkpoints initialized JVM, restores in ~200ms (best for Java 11+). (2) Provisioned Concurrency: Keeps N instances warm (guaranteed no cold start, costs more). (3) Optimize: Reduce dependencies, use lighter frameworks (Quarkus, Micronaut instead of Spring). (4) Increase memory: More memory = more CPU = faster initialization.

**Q: What are Lambda concurrency limits and how do you handle them?**
> Default: 1000 concurrent executions per account (all functions combined). Each invocation uses one concurrent execution. Solutions: (1) Request quota increase, (2) Reserved concurrency per function (guarantees capacity), (3) SQS as buffer (absorbs bursts, Lambda processes at controlled rate), (4) Provisioned concurrency for predictable workloads.

---

## Best Practices

1. **Keep functions small and focused** — single responsibility
2. **Use SQS** as event source for reliable processing (DLQ for failures)
3. **Set appropriate timeout** — don't use 15 min default for quick functions
4. **Minimize package size** — faster cold starts
5. **Connection reuse** — initialize DB/HTTP clients outside handler
6. **SnapStart for Java** — dramatic cold start improvement
7. **Environment variables** for configuration (use Secrets Manager for secrets)
8. **Monitor** with CloudWatch: duration, errors, throttles, concurrent executions

---

## Related Topics
- → [11. SQS SNS EventBridge](./11-sqs-sns-eventbridge.md)
- → [09. DynamoDB](./09-dynamodb.md)
- → [14. CloudWatch](./14-cloudwatch.md)
