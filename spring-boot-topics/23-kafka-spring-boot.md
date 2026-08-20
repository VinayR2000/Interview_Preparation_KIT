# 23. Kafka + Spring Boot

## Theory

Apache Kafka is a distributed event streaming platform used for building real-time data pipelines and event-driven architectures. Spring Kafka provides a convenient abstraction over the Kafka client libraries.

### Core Concepts:
- **Producer**: Publishes messages to topics
- **Consumer**: Reads messages from topics
- **Topic**: Named category/feed for messages
- **Partition**: Ordered, immutable sequence within a topic (parallelism unit)
- **Offset**: Sequential ID for each message within a partition
- **Consumer Group**: Set of consumers sharing workload (each partition → one consumer)
- **Broker**: Kafka server that stores data and serves clients
- **Replication**: Copies of partitions across brokers (fault tolerance)

### Delivery Semantics:
- **At-most-once**: Message may be lost, never duplicated
- **At-least-once**: Message never lost, may be duplicated (most common)
- **Exactly-once**: Message neither lost nor duplicated (hardest to achieve)

### Key Patterns:
- Event sourcing, CQRS, Saga pattern, Outbox pattern, Dead Letter Topic

---

## Internal Working

### Producer Flow:
```
Application calls kafkaTemplate.send(topic, key, value)
       ↓
Serializer (key → bytes, value → bytes)
       ↓
Partitioner determines target partition:
  - If key provided: hash(key) % numPartitions
  - If no key: round-robin or sticky partition
       ↓
RecordAccumulator batches messages per partition
       ↓
Sender thread sends batch to broker
       ↓
Broker writes to partition log
       ↓
Acknowledgment returned based on acks config:
  - acks=0: No ack (fire and forget)
  - acks=1: Leader ack only
  - acks=all: All in-sync replicas ack
```

### Consumer Flow:
```
Consumer starts → joins Consumer Group
       ↓
Coordinator assigns partitions (rebalance)
       ↓
Consumer polls messages from assigned partitions
       ↓
┌─────────────────────────────────────────────┐
│ For each message:                            │
│   1. Deserialize key and value              │
│   2. Invoke @KafkaListener method           │
│   3. Process message                        │
│   4. Commit offset (auto or manual)         │
│                                              │
│ Offset tracking:                             │
│   committed offset = "last processed"       │
│   On restart: resume from committed offset  │
└─────────────────────────────────────────────┘
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    KAFKA CLUSTER                              │
│                                                              │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐              │
│  │ Broker 1│     │ Broker 2│     │ Broker 3│              │
│  └─────────┘     └─────────┘     └─────────┘              │
│                                                              │
│  Topic: "orders" (3 partitions, RF=3)                       │
│  ┌──────────────────────────────────────────┐               │
│  │ P0: [msg0, msg1, msg2, msg3, ...]        │ → Broker 1   │
│  │ P1: [msg0, msg1, msg2, ...]              │ → Broker 2   │
│  │ P2: [msg0, msg1, msg2, msg3, msg4, ...] │ → Broker 3   │
│  └──────────────────────────────────────────┘               │
└──────────────┬───────────────────────────┬──────────────────┘
               │                           │
     ┌─────────┘                           └─────────┐
     ↓                                               ↓
┌─────────────────┐                     ┌─────────────────────┐
│   PRODUCER       │                     │  CONSUMER GROUP      │
│                  │                     │  "order-service"     │
│  KafkaTemplate   │                     │                      │
│  .send("orders", │                     │  Consumer-1 → P0    │
│   key, value)    │                     │  Consumer-2 → P1    │
│                  │                     │  Consumer-3 → P2    │
└─────────────────┘                     └─────────────────────┘
```

---

## Code

### Configuration:

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
    consumer:
      group-id: order-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "com.example.events"
```

### Producer:

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CompletableFuture<SendResult<String, Object>> publishOrderCreated(
            OrderCreatedEvent event) {
        
        String key = event.getOrderId().toString();  // Same order → same partition
        
        return kafkaTemplate.send("orders", key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send event for order {}: {}", 
                        event.getOrderId(), ex.getMessage());
                } else {
                    log.info("Order event sent: topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
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

### Consumer:

```java
@Component
@Slf4j
public class OrderEventConsumer {

    private final OrderService orderService;

    @KafkaListener(
        topics = "orders",
        groupId = "order-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderEvent(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Received order event: orderId={}, partition={}, offset={}",
            event.getOrderId(), partition, offset);

        try {
            orderService.processOrder(event);
            acknowledgment.acknowledge();  // Manual commit
        } catch (Exception e) {
            log.error("Failed to process order {}: {}", event.getOrderId(), e.getMessage());
            // Don't acknowledge → message will be redelivered
            throw e;
        }
    }

    // Batch consumer
    @KafkaListener(topics = "events", groupId = "batch-group")
    public void handleBatch(List<ConsumerRecord<String, Object>> records,
                            Acknowledgment acknowledgment) {
        log.info("Received batch of {} records", records.size());
        records.forEach(record -> processRecord(record));
        acknowledgment.acknowledge();  // Commit all at once
    }
}
```

### Error Handling & Retry:

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> 
            kafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);  // 3 consumer threads
        factory.getContainerProperties()
            .setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Retry configuration
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate(),
                (record, ex) -> new TopicPartition(
                    record.topic() + ".DLT", record.partition())),
            new FixedBackOff(1000L, 3)  // 3 retries, 1s between
        ));

        return factory;
    }
}
```

### Dead Letter Topic Consumer:

```java
@Component
@Slf4j
public class DeadLetterConsumer {

    @KafkaListener(topics = "orders.DLT", groupId = "dlt-group")
    public void handleDeadLetter(
            ConsumerRecord<String, Object> record,
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exMessage,
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic) {
        
        log.error("Dead letter received: topic={}, key={}, error={}",
            originalTopic, record.key(), exMessage);
        // Alert, store for manual review, etc.
    }
}
```

### Transactional Producer:

```java
@Configuration
public class KafkaTransactionalConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-tx-");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        return new KafkaTransactionManager<>(producerFactory);
    }
}

@Service
public class TransactionalProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional("kafkaTransactionManager")
    public void publishAtomically(Order order) {
        kafkaTemplate.send("orders", order.getId().toString(), new OrderCreatedEvent(order));
        kafkaTemplate.send("inventory", order.getId().toString(), new ReserveStockEvent(order));
        // Both succeed or both fail
    }
}
```

---

## Dry Run

### Producer sends, Consumer processes:

```
1. OrderService creates order, publishes event:
   kafkaTemplate.send("orders", "order-123", OrderCreatedEvent{orderId=123, amount=500})

2. Partitioner: hash("order-123") % 3 = partition 1

3. Broker receives, appends to orders-partition-1 at offset 47

4. Consumer (assigned to partition 1) polls:
   → Receives record: topic=orders, partition=1, offset=47, value=OrderCreatedEvent

5. @KafkaListener processes:
   → orderService.processOrder(event)
   → Success → acknowledgment.acknowledge()
   → Committed offset: 48 (next to read)

6. If processing FAILS:
   → Exception thrown → Error handler retries (1s, 1s, 1s)
   → Still failing after 3 retries
   → DeadLetterPublishingRecoverer sends to "orders.DLT"
   → Original offset committed (won't reprocess)
   → DLT consumer handles failed message separately
```

---

## Complexity

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Produce message | O(1) amortized | Batching helps throughput |
| Consume message | O(1) per message | Sequential read from partition |
| Partition lookup | O(1) | Hash-based |
| Consumer rebalance | O(n) | n = partitions being reassigned |
| Broker disk write | O(1) | Append-only log |
| Throughput | ~1M msg/sec | Per broker, depends on message size |

---

## Real Project Usage

### Outbox Pattern (reliable event publishing):

```java
// Instead of publishing directly to Kafka from business transaction:
@Service
public class OrderServiceWithOutbox {

    @Transactional  // DB transaction
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(buildOrder(request));
        
        // Store event in outbox table (same DB transaction)
        outboxRepository.save(OutboxEvent.builder()
            .aggregateId(order.getId().toString())
            .aggregateType("Order")
            .eventType("OrderCreated")
            .payload(objectMapper.writeValueAsString(new OrderCreatedEvent(order)))
            .build());
        
        return order;
    }
}

// Separate scheduler publishes outbox events to Kafka
@Component
public class OutboxPublisher {
    
    @Scheduled(fixedDelay = 100)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> events = outboxRepository.findUnpublished(100);
        events.forEach(event -> {
            kafkaTemplate.send(event.getAggregateType().toLowerCase() + "-events",
                event.getAggregateId(), event.getPayload());
            event.markPublished();
        });
    }
}
```

---

## Interview Questions

1. **How does Kafka ensure message ordering?**
   - Ordering guaranteed within a partition. Use same key for related messages to go to same partition. No global ordering across partitions.

2. **What happens when a consumer crashes mid-processing?**
   - If offset not committed, message re-delivered to another consumer in group (at-least-once). Must handle idempotently.

3. **How does Consumer Group rebalancing work?**
   - When consumer joins/leaves group, coordinator reassigns partitions. During rebalance, no messages consumed (brief pause).

4. **What is the Dead Letter Topic pattern?**
   - Messages that fail processing after retries are sent to a separate DLT topic for manual review/reprocessing. Prevents blocking the main consumer.

5. **How to achieve exactly-once semantics?**
   - Idempotent producer (enable.idempotence=true) + transactions + consumer reads-committed. Or use outbox pattern + idempotent consumers.

---

## Follow-up Questions

1. How to handle message schema evolution (Avro/Schema Registry)?
   - Use Confluent Schema Registry with Avro serialization. Schema stored centrally, versioned. Consumers can read old messages with new schema (backward compatible). Prevents breaking changes.

2. How to implement Saga pattern with Kafka?
   - Choreography: Each service listens for events, publishes next. Orchestration: Central orchestrator publishes commands, listens for responses. Compensation events for rollback. Each step must be idempotent.

3. How does Kafka compare to RabbitMQ for different use cases?
   - Kafka: High throughput, log-based retention, replay capability, ordered within partition. RabbitMQ: Low latency, complex routing, message acknowledgment, traditional queuing. Kafka for event streaming, RabbitMQ for task queues.

4. How to monitor consumer lag and what does it indicate?
   - Consumer lag = latest offset - committed offset. Growing lag means consumer can't keep up. Monitor via Kafka metrics, Burrow, or Prometheus (kafka_consumer_group_lag). Fix: Scale consumers, optimize processing.

5. How to handle back-pressure when consumer is slower than producer?
   - Scale consumers (up to partition count). Increase max.poll.records for batch processing. Pause/resume consumption. Use separate topic for overflow. Alert on growing lag before it becomes critical.

---

## Common Mistakes

1. **Not using keys** - Messages spread randomly, no ordering guarantee
2. **Auto-commit enabled** - Messages lost if processing fails after commit
3. **Not handling idempotency** - Duplicate processing on retry
4. **Too few partitions** - Limits parallelism (can't have more consumers than partitions)
5. **Very large messages** - Kafka optimized for small messages (< 1MB). Use claim check pattern for large payloads.
6. **Ignoring consumer lag** - Growing lag = consumer can't keep up = stale data

---

## Best Practices

1. **Use manual offset commits** for at-least-once semantics
2. **Implement idempotent consumers** (deduplication by event ID)
3. **Use Dead Letter Topics** for failed messages
4. **Set appropriate partition count** based on throughput needs
5. **Use message keys** for ordering-dependent events
6. **Configure retries with backoff** before DLT
7. **Monitor consumer lag** with metrics/alerting
8. **Use Outbox pattern** for reliable DB + Kafka consistency
9. **Enable idempotent producer** (acks=all + enable.idempotence=true)

---

## Production Considerations

- **Partition strategy**: More partitions = more parallelism but more resources. Plan for peak load.
- **Replication factor**: Minimum 3 for production (survive broker failure)
- **Consumer lag monitoring**: Alert if lag exceeds threshold (Kafka metrics → Prometheus)
- **Schema evolution**: Use Schema Registry (Avro) to handle payload changes safely
- **Retention policy**: Configure based on replay needs (default 7 days)
- **Cluster sizing**: Plan for throughput, storage, replication
- **Network partitions**: Understand ISR (in-sync replicas) behavior
- **Graceful shutdown**: Ensure consumers commit offsets before stopping

---

## Related Topics

- Spring Events (in-process events)
- Microservices (event-driven communication)
- Transactions (Kafka + DB transactions)
- Outbox Pattern
- Resilience Patterns (retry, DLT)
- Redis Pub/Sub (lighter weight alternative for simpler cases)
