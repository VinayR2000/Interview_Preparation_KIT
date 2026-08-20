# 25. Kafka + Spring Boot ⭐⭐⭐

---

## Theory

Spring Kafka provides a convenient abstraction over the Kafka client libraries, integrating deeply with Spring's dependency injection, transaction management, and configuration system.

### Key Components

| Component | Purpose |
|-----------|---------|
| KafkaTemplate | Send messages (producer wrapper) |
| @KafkaListener | Receive messages (consumer annotation) |
| ProducerFactory | Creates producer instances |
| ConsumerFactory | Creates consumer instances |
| ConcurrentKafkaListenerContainerFactory | Manages listener containers |
| KafkaTransactionManager | Spring transaction integration |

---

## Code

### Application Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        linger.ms: 20
        batch.size: 32768
    consumer:
      group-id: order-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "com.example.events"
        max.poll.records: 100
        session.timeout.ms: 45000
```

### Producer (KafkaTemplate)

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Async send with callback
    public CompletableFuture<SendResult<String, Object>> publishOrderCreated(OrderCreatedEvent event) {
        String key = event.getOrderId().toString();
        return kafkaTemplate.send("orders", key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send: {}", ex.getMessage());
                } else {
                    log.info("Sent to partition={}, offset={}", 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }

    // With headers
    public void publishWithHeaders(String topic, Object event, String correlationId) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, event);
        record.headers().add("correlationId", correlationId.getBytes());
        record.headers().add("eventType", event.getClass().getSimpleName().getBytes());
        kafkaTemplate.send(record);
    }
}
```

### Consumer (@KafkaListener)

```java
@Component
@Slf4j
public class OrderEventConsumer {

    @KafkaListener(topics = "orders", groupId = "order-processing",
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleOrder(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        log.info("Received: orderId={}, partition={}, offset={}", 
            event.getOrderId(), partition, offset);
        
        try {
            orderService.processOrder(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Processing failed: {}", e.getMessage());
            throw e;  // let error handler manage retry
        }
    }

    // Batch listener
    @KafkaListener(topics = "events", groupId = "batch-group")
    public void handleBatch(List<ConsumerRecord<String, Object>> records,
                            Acknowledgment ack) {
        records.forEach(this::process);
        ack.acknowledge();
    }
}
```

### Consumer Configuration with Error Handling

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Error handling with retry + DLT
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())),
            new FixedBackOff(1000L, 3)  // 3 retries, 1s between
        ));
        
        return factory;
    }
}
```

### Dead Letter Topic Consumer

```java
@Component
@Slf4j
public class DeadLetterConsumer {

    @KafkaListener(topics = "orders.DLT", groupId = "dlt-processing")
    public void handleDeadLetter(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String errorMessage,
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic) {
        
        log.error("DLT received: topic={}, key={}, error={}", 
            originalTopic, record.key(), errorMessage);
        // Alert, store for manual review, or attempt different processing
    }
}
```

### Retry with @RetryableTopic (Spring Kafka 2.7+)

```java
@Component
public class RetryableOrderConsumer {

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "orders", groupId = "retry-group")
    public void handleOrder(OrderCreatedEvent event) {
        orderService.process(event);  // throws on failure → auto-retry
    }

    @DltHandler
    public void handleDlt(OrderCreatedEvent event, 
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("DLT handler: event={}, topic={}", event, topic);
    }
}
// Creates: orders-retry-0, orders-retry-1, orders-retry-2, orders-dlt
```

### Transactional Producer + Consumer

```java
@Configuration
public class TransactionalKafkaConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
            ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-tx-",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true
        );
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> pf) {
        return new KafkaTransactionManager<>(pf);
    }
}

@Service
public class TransactionalOrderService {

    @Transactional("kafkaTransactionManager")
    public void processAndPublish(Order order) {
        kafkaTemplate.send("orders-processed", order.getId(), new OrderProcessedEvent(order));
        kafkaTemplate.send("notifications", order.getUserId(), new NotifyEvent(order));
        // Both succeed or both fail (atomic)
    }
}
```

### Manual Acknowledgment Modes

```java
// AckMode options:
ContainerProperties.AckMode.MANUAL        // ack.acknowledge() per record
ContainerProperties.AckMode.MANUAL_IMMEDIATE  // commit immediately on ack
ContainerProperties.AckMode.BATCH         // commit after all records in poll batch
ContainerProperties.AckMode.RECORD        // commit after each record (auto)
ContainerProperties.AckMode.COUNT         // commit after N records
ContainerProperties.AckMode.TIME          // commit after elapsed time
```

---

## Interview Questions

### Q1: How does Spring Kafka's @KafkaListener work internally?

**A:** Spring creates a `MessageListenerContainer` that:
1. Creates a KafkaConsumer with configured properties
2. Subscribes to specified topics
3. Runs a poll loop in a dedicated thread (per concurrency level)
4. Deserializes records and invokes the annotated method
5. Handles acknowledgment based on AckMode
6. Routes errors to configured error handler
- With `concurrency=3`: 3 consumer threads, each assigned partitions via consumer group protocol.

### Q2: How would you handle poison messages (messages that always fail)?

**A:** Use the Dead Letter Topic pattern:
1. Configure `DefaultErrorHandler` with retry backoff
2. After max retries exhausted → `DeadLetterPublishingRecoverer` sends to `.DLT` topic
3. Original message offset committed (consumer moves forward)
4. DLT consumer handles failed messages (alert, manual review, different processing)
5. Alternative: `@RetryableTopic` annotation creates dedicated retry topics with progressive backoff.

### Q3: How to achieve exactly-once with Spring Kafka and a database?

**A:** Two approaches:
1. **Kafka Transactions + ChainedTransactionManager:** Atomic Kafka write + DB write (complex, limited)
2. **Practical approach:** At-least-once + idempotent consumer:
   - `@KafkaListener` with manual ack
   - `@Transactional` on processing method
   - Store event ID in DB (same transaction as business logic)
   - Skip duplicates by checking event ID before processing

---

## Best Practices

1. **Use manual acknowledgment** for reliable processing
2. **Configure error handling with DLT** — don't lose messages
3. **Set concurrency = partition count** for max parallelism
4. **Use `@RetryableTopic`** for automatic retry with backoff
5. **Enable idempotent producer** (acks=all, enable.idempotence=true)
6. **Use JsonSerializer with type headers** for multi-type topics
7. **Monitor consumer lag** via Spring Boot Actuator + Micrometer

---

## Related Topics

- [06. Producer](./06-producer.md)
- [08. Consumer](./08-consumer.md)
- [26. Kafka Error Handling](./26-kafka-error-handling.md)
