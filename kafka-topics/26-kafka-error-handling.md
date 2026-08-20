# 26. Kafka Error Handling ⭐⭐⭐

---

## Theory

Error handling in Kafka involves managing failures in both producers (send failures) and consumers (processing failures) with retry strategies and dead letter patterns.

### Consumer Exceptions

```
Types of consumer failures:
1. Deserialization error — malformed message (poison pill)
2. Processing error — business logic failure
3. Transient error — temporary downstream failure (DB timeout, service unavailable)
4. Non-retryable error — permanent failure (invalid data, business rule violation)
```

### Producer Exceptions

```
Retriable: LEADER_NOT_AVAILABLE, NOT_ENOUGH_REPLICAS, REQUEST_TIMED_OUT
  → Kafka client retries automatically (up to delivery.timeout.ms)

Non-retriable: MESSAGE_TOO_LARGE, AUTHORIZATION_FAILED, INVALID_TOPIC
  → Send fails immediately, no retry
```

### Retry Strategy

```
Retry attempts with backoff:
  Attempt 1: process → fail → wait 1s
  Attempt 2: process → fail → wait 2s
  Attempt 3: process → fail → wait 4s
  Attempt 4: process → fail → SEND TO DLT (all retries exhausted)
```

### Retry Topic Pattern

```
Main Topic: "orders"
  ↓ fails
Retry Topic 1: "orders-retry-0" (delay: 1s)
  ↓ fails
Retry Topic 2: "orders-retry-1" (delay: 5s)
  ↓ fails
Retry Topic 3: "orders-retry-2" (delay: 30s)
  ↓ fails
Dead Letter Topic: "orders-dlt"

Benefits:
- Main consumer not blocked by retrying messages
- Progressive backoff without blocking thread
- Easy monitoring (check retry topic lag)
- Preserves ordering for successful messages in main topic
```

### Dead Letter Topic (DLT)

```
Messages that fail after all retries → sent to DLT:
- Original message preserved (key, value, headers)
- Error info added as headers (exception message, stack trace, original topic)
- Separate consumer can:
  - Alert operations team
  - Store for manual review
  - Attempt different processing logic
  - Replay after fix is deployed
```

### Backoff Strategies

```
Fixed Backoff: same delay every retry
  1s → 1s → 1s → 1s (simple, predictable)

Exponential Backoff: increasing delay
  1s → 2s → 4s → 8s (reduces load on failing service)

Exponential with Jitter: random variation
  1s±200ms → 2s±400ms → 4s±800ms (prevents thundering herd)
```

### Poison Messages

Messages that can NEVER be processed successfully (malformed, schema mismatch).

```
Without handling:
  Consumer reads poison message → fails → retries forever → STUCK!
  All subsequent messages in that partition blocked.

With handling:
  Option 1: Deserialization error handler → skip + log
  Option 2: Send to DLT after N retries → consumer moves forward
  Option 3: ErrorHandlingDeserializer wraps exceptions as readable records
```

### Non-Retryable Exceptions

```java
// Mark certain exceptions as non-retryable (skip retry, go directly to DLT)
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    deadLetterRecoverer,
    new FixedBackOff(1000L, 3)
);

// These go directly to DLT without retry:
errorHandler.addNotRetryableExceptions(
    DeserializationException.class,
    ValidationException.class,
    InvalidOrderException.class
);

// These get retried:
// Everything else (NullPointerException, DataAccessException, etc.)
```

---

## Code

### Complete Error Handling Configuration

```java
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
        
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Dead Letter Publishing Recoverer
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                // Custom DLT topic routing
                if (ex.getCause() instanceof ValidationException) {
                    return new TopicPartition("validation-errors", record.partition());
                }
                return new TopicPartition(record.topic() + ".DLT", record.partition());
            });
        
        // Exponential backoff: 1s, 2s, 4s, 8s, then DLT
        var backoff = new ExponentialBackOff(1000L, 2.0);
        backoff.setMaxElapsedTime(15000L);  // max total retry time
        
        var errorHandler = new DefaultErrorHandler(recoverer, backoff);
        
        // Non-retryable exceptions (go directly to DLT)
        errorHandler.addNotRetryableExceptions(
            SerializationException.class,
            DeserializationException.class,
            ClassCastException.class
        );
        
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
```

### @RetryableTopic (Declarative Retry)

```java
@Component
@Slf4j
public class OrderConsumerWithRetry {

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000),
        exclude = {DeserializationException.class, ValidationException.class},
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "orders", groupId = "order-retry-group")
    public void processOrder(OrderEvent event) {
        log.info("Processing order: {}", event.getOrderId());
        orderService.process(event);  // if throws → retry
    }

    @DltHandler
    public void handleDlt(OrderEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String error) {
        log.error("DLT: order={}, topic={}, error={}", event.getOrderId(), topic, error);
        alertService.notifyFailedOrder(event, error);
    }
}
```

### Handling Deserialization Errors

```java
// Use ErrorHandlingDeserializer to catch deserialization failures
@Bean
public ConsumerFactory<String, Object> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
}
// Bad messages won't crash consumer — error wrapped in ConsumerRecord header
```

---

## Diagram

### Error Flow

```
Message arrives
       │
       ▼
┌──────────────┐
│ Deserialize  │───fail───► ErrorHandlingDeserializer catches
└──────┬───────┘              → Routes to DLT (non-retryable)
       │ success
       ▼
┌──────────────┐
│   Process    │───fail───► DefaultErrorHandler
└──────┬───────┘              │
       │ success              ▼
       ▼              ┌──────────────────┐
┌──────────────┐      │ Non-retryable?   │
│ Acknowledge  │      └────┬─────────┬───┘
└──────────────┘           │ YES     │ NO
                           ▼         ▼
                    ┌──────────┐ ┌──────────────┐
                    │ Send DLT │ │ Retry with   │
                    │ (skip    │ │ backoff      │
                    │  retry)  │ │ (1s,2s,4s..) │
                    └──────────┘ └──────┬───────┘
                                        │ all retries fail
                                        ▼
                                 ┌──────────────┐
                                 │ Send to DLT  │
                                 │ Commit offset│
                                 └──────────────┘
```

---

## Interview Questions

### Q1: How do you handle a poison message that blocks a partition?

**A:** Configure error handler with max retries + DLT recovery. After N failed attempts, the message is sent to a Dead Letter Topic and the original offset is committed — consumer moves forward. For deserialization errors specifically, use `ErrorHandlingDeserializer` which wraps the error in a header instead of throwing, so the consumer can handle it gracefully (log + skip or route to DLT).

### Q2: What is the difference between blocking retry and retry topics?

**A:**
- **Blocking retry:** Same consumer retries in a loop (thread blocked). Other messages in that partition wait. Simple but can cause consumer lag and max.poll.interval.ms violations.
- **Retry topics:** Failed message sent to a separate topic. Main consumer moves forward immediately. Dedicated retry consumer picks up later with delay. Main topic throughput unaffected. Better for production — preserves ordering for successful messages.

### Q3: How would you implement progressive retry with increasing delays?

**A:** Two approaches:
1. **In-process (DefaultErrorHandler):** Use `ExponentialBackOff` — consumer thread sleeps between retries. Simple but blocks partition processing.
2. **Retry topics (@RetryableTopic):** Message sent to `topic-retry-0` (1s delay), then `topic-retry-1` (5s delay), then `topic-retry-2` (30s delay). Each retry topic has its own consumer with appropriate pause. Non-blocking, production-friendly.

---

## Best Practices

1. **Always configure DLT** — never let messages block indefinitely
2. **Classify exceptions** — non-retryable go directly to DLT (save time)
3. **Use exponential backoff** — reduces pressure on failing downstream
4. **Monitor DLT size** — alert when messages accumulate
5. **Add context headers** — include error info, retry count, original topic
6. **Build DLT replay tooling** — ability to reprocess after fixes
7. **Use @RetryableTopic** for non-blocking retry in production

---

## Related Topics

- [25. Kafka + Spring Boot](./25-kafka-spring-boot.md)
- [21. Idempotency](./21-idempotency.md)
- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
