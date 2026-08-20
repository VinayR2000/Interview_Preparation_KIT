# SQS, SNS, and EventBridge ⭐⭐⭐

## Theory

AWS messaging services for decoupling microservices and building event-driven architectures.

| Service | Pattern | Use Case |
|---------|---------|----------|
| SQS | Queue (point-to-point) | Decouple producer/consumer, buffer |
| SNS | Pub/Sub (fan-out) | Notify multiple subscribers |
| EventBridge | Event bus (event routing) | Event-driven architecture, rules |

---

## Diagram

### SQS — Simple Queue Service

```
Producer → [Queue] → Consumer
                      (polls)

Order Service                      Payment Service
    │                                     │
    └── SendMessage ──→ [OrderQueue] ←── ReceiveMessage
                              │
                              └── Dead-Letter Queue (DLQ)
                                  (failed messages go here)
```

### SNS — Simple Notification Service (Fan-Out)

```
Order Service
    │
    └── Publish("OrderPlaced")
              │
              ↓
    ┌──── SNS Topic: OrderEvents ────┐
    │                                  │
    ├── SQS: payment-queue            │
    ├── SQS: inventory-queue          │
    ├── SQS: notification-queue       │
    ├── Lambda: analytics-function    │
    └── Email: ops@company.com        │
```

### EventBridge — Event Bus

```
Source Events                    Rules                    Targets
─────────────                   ─────                    ───────
Order Created ──→ ┌─────────┐  IF source=orders    → SQS Queue
User Signed Up ──→│  Event  │  IF detail.total>100 → Lambda
Payment Failed ──→│   Bus   │  IF status=FAILED    → SNS Topic
Custom Events  ──→└─────────┘  IF source=payments  → Step Functions
```

---

## Internal Working

### SQS Key Concepts

| Concept | Description |
|---------|-------------|
| Standard Queue | At-least-once delivery, best-effort ordering |
| FIFO Queue | Exactly-once, guaranteed order (300 msg/s without batching) |
| Visibility Timeout | Message hidden from other consumers while being processed |
| Dead-Letter Queue | Messages that fail processing N times go here |
| Long Polling | Consumer waits up to 20s for messages (reduces empty polls) |
| Retention | 1 min to 14 days (default 4 days) |
| Max Size | 256 KB per message (use S3 for larger) |

### SQS vs Kafka ⭐⭐⭐

| Feature | SQS | Kafka |
|---------|-----|-------|
| Model | Queue (message deleted after consume) | Log (messages retained) |
| Ordering | FIFO queue only | Per partition |
| Replay | No (once consumed, gone) | Yes (consumer offset reset) |
| Scale | Unlimited (serverless) | Partition-based |
| Consumer groups | One consumer per message | Multiple consumer groups |
| Throughput | High (burst capable) | Very high (sustained) |
| Management | Serverless (zero ops) | Managed (MSK) or self-managed |
| Use case | Simple decoupling, async tasks | Event streaming, event sourcing |

---

## Code

### Spring Boot + SQS

```java
// Producer
@Service
public class OrderProducer {
    private final SqsTemplate sqsTemplate;

    public void publishOrder(OrderEvent event) {
        sqsTemplate.send(to -> to
            .queue("order-processing-queue")
            .payload(event));
    }
}

// Consumer
@SqsListener("order-processing-queue")
public void processOrder(OrderEvent event) {
    log.info("Processing order: {}", event.getOrderId());
    paymentService.charge(event);
    // If exception thrown → message returns to queue after visibility timeout
    // After maxReceiveCount failures → goes to DLQ
}
```

### Spring Boot + SNS
```java
@Service
public class NotificationPublisher {
    private final SnsTemplate snsTemplate;

    public void publishOrderEvent(OrderEvent event) {
        snsTemplate.sendNotification("order-events-topic", 
            SnsNotification.builder()
                .payload(event)
                .header("eventType", event.getType())
                .build());
    }
}
```

### SNS + SQS Fan-Out Pattern
```
OrderService.publish("OrderPlaced") → SNS Topic
                                        ├→ SQS: payment-queue → PaymentService
                                        ├→ SQS: inventory-queue → InventoryService
                                        └→ SQS: email-queue → EmailService
```

Each SQS queue subscribes to the SNS topic. Each service processes independently — failure in one doesn't affect others.

---

## Interview Questions and Answers

**Q: What's the difference between SQS, SNS, and EventBridge?**
> SQS: Point-to-point queue — one producer, one consumer per message. For decoupling and buffering. SNS: Fan-out pub/sub — one message goes to many subscribers. For broadcasting. EventBridge: Event bus with rules — routes events based on content/source. For complex event-driven architectures with filtering. Often combined: SNS for fan-out → SQS for reliable consumption.

**Q: When would you use SQS vs Kafka (MSK)?**
> SQS: Simple async processing, decoupling services, no need for replay, serverless (zero ops), temporary data. Kafka: Event streaming, event sourcing (need to replay), multiple consumer groups reading same data, high-throughput ordered streams, permanent event log. Most microservices: start with SQS. Move to Kafka when you need replay, multiple consumers, or event sourcing.

**Q: What is a Dead-Letter Queue and why is it important?**
> DLQ receives messages that fail processing after N attempts (maxReceiveCount). Without DLQ: failed messages block the queue forever or get lost. With DLQ: failed messages are captured for investigation, main queue keeps flowing. Set up CloudWatch alarm on DLQ depth > 0 to alert on failures.

**Q: How do you handle duplicate messages in SQS?**
> Standard queues provide at-least-once delivery (duplicates possible). Solutions: (1) Use FIFO queue (exactly-once, 300 msg/s limit), (2) Idempotent consumers — use messageId or deduplicationId to check "already processed" in database, (3) Idempotent operations (e.g., SET vs INCREMENT).

---

## Best Practices

1. **Always configure DLQ** — capture failed messages for debugging
2. **Long polling** (WaitTimeSeconds=20) — reduces costs and empty responses
3. **Visibility timeout** > processing time — prevent duplicate processing
4. **Idempotent consumers** — handle at-least-once delivery gracefully
5. **SNS + SQS fan-out** — for multi-service event distribution
6. **FIFO for ordering** — when message order matters (max 300/s per group)
7. **Monitor**: ApproximateNumberOfMessagesVisible, ApproximateAgeOfOldestMessage, DLQ depth

---

## Related Topics
- → [10. ElastiCache](./10-elasticache.md)
- → [12. ECS and EKS](./12-ecs-eks.md)
- → [13. Lambda and Serverless](./13-lambda-serverless.md)
