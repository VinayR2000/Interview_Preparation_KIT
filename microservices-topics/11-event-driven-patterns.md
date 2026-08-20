# 11. Event-Driven Patterns ⭐⭐⭐⭐⭐

## Theory

Event-driven architecture (EDA) is the backbone of loosely coupled microservices. Services communicate by producing and consuming events rather than making direct calls.

### Event Types:

| Pattern | Data in Event | Consumer Action |
|---------|--------------|-----------------|
| Event Notification | Minimal (just ID + type) | Consumer calls back for details |
| Event-Carried State Transfer | Full data | Consumer has everything it needs |
| Event Sourcing | State change events | Reconstruct state from event history |

### Key Concepts:
- **Publish/Subscribe**: One event, multiple independent consumers
- **Consumer Groups**: Multiple instances of same service share load
- **Dead Letter Queue (DLQ)**: Failed messages go here for investigation
- **Retry Topic**: Messages retried with backoff before going to DLQ
- **Event Replay**: Reprocess historical events (fix bugs, build new views)

---

## Internal Working

### Publish/Subscribe with Consumer Groups:

```
┌────────────────────────────────────────────────────────┐
│ PUBLISH/SUBSCRIBE + CONSUMER GROUPS                     │
│                                                         │
│ Order Service publishes: OrderCreatedEvent              │
│                                                         │
│        ┌──────────────────────────┐                    │
│        │    Kafka Topic:          │                    │
│        │    order-events          │                    │
│        │                          │                    │
│        │ P0: [E1, E3, E5]        │                    │
│        │ P1: [E2, E4, E6]        │                    │
│        └───────┬──────┬──────────┘                    │
│                │      │                                │
│    ┌───────────┘      └───────────┐                   │
│    ↓                              ↓                    │
│ Consumer Group:               Consumer Group:          │
│ "payment-service"            "notification-service"    │
│ ┌──────┐ ┌──────┐          ┌──────┐ ┌──────┐       │
│ │Inst 1│ │Inst 2│          │Inst 1│ │Inst 2│       │
│ │(P0)  │ │(P1)  │          │(P0)  │ │(P1)  │       │
│ └──────┘ └──────┘          └──────┘ └──────┘       │
│                                                         │
│ Each consumer GROUP gets ALL events                    │
│ Within a group, partitions are distributed             │
│ → Payment processes ALL orders                        │
│ → Notification also processes ALL orders              │
│ → But each group's instances share the load           │
└────────────────────────────────────────────────────────┘
```

### Dead Letter Queue + Retry Flow:

```
┌────────────────────────────────────────────────────────┐
│ RETRY + DEAD LETTER QUEUE                               │
│                                                         │
│ Main Topic: order-events                               │
│      │                                                  │
│      ↓                                                  │
│ Consumer tries to process                              │
│      │                                                  │
│      ├── SUCCESS → commit offset, done ✓               │
│      │                                                  │
│      └── FAILURE (1st time)                            │
│           │                                             │
│           ↓                                             │
│      Retry Topic: order-events-retry-1                 │
│      (wait 1 minute)                                   │
│           │                                             │
│           ├── SUCCESS → done ✓                         │
│           │                                             │
│           └── FAILURE (2nd time)                       │
│                │                                        │
│                ↓                                        │
│           Retry Topic: order-events-retry-2            │
│           (wait 5 minutes)                             │
│                │                                        │
│                ├── SUCCESS → done ✓                    │
│                │                                        │
│                └── FAILURE (3rd time)                  │
│                     │                                   │
│                     ↓                                   │
│                Dead Letter Queue: order-events-dlt     │
│                (manual investigation required)          │
│                                                         │
└────────────────────────────────────────────────────────┘
```

### Event Replay Scenario:

```
Scenario: New "Analytics Service" needs to process all past orders

Option 1: Event Replay
  Kafka retains events for N days (or forever with compaction)
  New service starts consuming from offset 0
  
  Topic: order-events
  ┌─────────────────────────────────────────────┐
  │ offset 0    offset 1000    offset 5000       │
  │  │              │              │ ← current  │
  │  ↑                                           │
  │  New Analytics Service starts here           │
  │  Replays all 5000 events to build its state  │
  └─────────────────────────────────────────────┘

Option 2: Snapshot + Events
  Load initial state from snapshot
  Then consume events from snapshot point forward
  Faster startup for services with large event history
```

---

## Diagram

```
Event Notification vs Event-Carried State Transfer:

EVENT NOTIFICATION (lightweight):
┌───────────┐  {orderId: 123, type: "CREATED"}  ┌───────────┐
│   Order   │ ──────────────────────────────────→│  Payment  │
│  Service  │                                    │  Service  │
└───────────┘                                    └─────┬─────┘
                                                       │
      ┌────────────────────────────────────────────────┘
      │ Need order details → must call back
      ↓
┌───────────┐  GET /api/orders/123   ┌───────────┐
│  Payment  │ ──────────────────────→│   Order   │
│  Service  │ ←──────────────────────│  Service  │
└───────────┘  {items, total, ...}   └───────────┘

Pro: Small events, always fresh data
Con: Coupling (callback), Order Service must be up

EVENT-CARRIED STATE TRANSFER (rich):
┌───────────┐  {orderId: 123, customerId: "C1",   ┌───────────┐
│   Order   │   items: [...], total: 99.99,       │  Payment  │
│  Service  │   customerEmail: "a@b.com"}         │  Service  │
└───────────┘ ────────────────────────────────────→└───────────┘

Pro: No callback needed, fully decoupled, works if Order is down
Con: Larger events, data might be stale, duplication
```

---

## Code

### Event Publishing with Spring Kafka:

```java
@Service
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .orderId(order.getId())
            .customerId(order.getCustomerId())
            .items(mapItems(order.getItems()))
            .totalAmount(order.getTotalAmount())
            .occurredAt(Instant.now())
            .build();

        // Key = orderId ensures ordering per order
        kafkaTemplate.send("order-events", order.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event for order {}", order.getId(), ex);
                } else {
                    log.info("Published OrderCreatedEvent. Topic={}, Partition={}, Offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
```

### Consumer with Retry and DLQ:

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        // Retry: 3 attempts with exponential backoff
        BackOff backOff = new ExponentialBackOff(1000L, 2.0);  // 1s, 2s, 4s
        ((ExponentialBackOff) backOff).setMaxElapsedTime(30000L);  // max 30s total

        // DLQ: send to dead letter topic after retries exhausted
        DeadLetterPublishingRecoverer recoverer = 
            new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(
                    record.topic() + "-dlt", record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        
        // Don't retry these exceptions (permanent failures)
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            ValidationException.class,
            IllegalArgumentException.class
        );
        
        return handler;
    }
}
```

```java
@Service
@Slf4j
public class PaymentEventConsumer {

    @KafkaListener(
        topics = "order-events",
        groupId = "payment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        log.info("Received OrderCreatedEvent. orderId={}, partition={}, offset={}",
            event.getOrderId(), partition, offset);

        // Idempotency check
        if (paymentRepository.existsByOrderId(event.getOrderId())) {
            log.warn("Payment already processed for order {}. Skipping.", event.getOrderId());
            return;
        }

        paymentService.processPayment(event);
    }

    // DLQ consumer for manual investigation
    @KafkaListener(topics = "order-events-dlt", groupId = "payment-service-dlt")
    public void handleDlt(ConsumerRecord<String, Object> record) {
        log.error("DLT received. Topic={}, Key={}, Value={}, Exception={}",
            record.topic(), record.key(), record.value(),
            new String(record.headers().lastHeader("kafka_dlt-exception-message").value()));
        
        // Store for manual review
        dlqRepository.save(DlqRecord.from(record));
    }
}
```

### Event Schema with Versioning:

```java
// Base event with common fields
public abstract class DomainEvent {
    private UUID eventId;
    private String eventType;
    private int version;  // Schema version
    private Instant occurredAt;
    private String source;  // Which service produced it
}

// Version 1
@JsonTypeName("OrderCreatedEvent")
public class OrderCreatedEventV1 extends DomainEvent {
    private UUID orderId;
    private String customerId;
    private BigDecimal totalAmount;
}

// Version 2 — backward compatible (new optional field)
@JsonTypeName("OrderCreatedEvent")
public class OrderCreatedEventV2 extends DomainEvent {
    private UUID orderId;
    private String customerId;
    private BigDecimal totalAmount;
    private String currency;  // NEW — optional, defaults to "USD"
    private List<OrderItemDto> items;  // NEW — enriched data
}
```

### Consumer Group Rebalancing:

```java
@Component
public class RebalanceListener implements ConsumerRebalanceListener {

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        // Called before rebalance — commit offsets, cleanup
        log.info("Partitions revoked: {}", partitions);
        // Flush any in-progress batch processing
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        // Called after rebalance — new partition assignment
        log.info("Partitions assigned: {}", partitions);
        // Initialize local state for new partitions
    }
}
```

---

## Interview Questions

1. **What is Event-Driven Architecture?**
   - Services communicate by publishing and subscribing to events. Producers don't know about consumers. Loose coupling in time and space. Enables independent scaling, deployment, and technology choices.

2. **Event Notification vs Event-Carried State Transfer?**
   - Notification: Minimal data, consumer callbacks for details (coupled to source). ECST: Full data in event, consumer self-sufficient (decoupled but larger events, possible staleness).

3. **What is a Dead Letter Queue?**
   - Topic where messages go after all retry attempts are exhausted. Preserves failed messages for investigation. Prevents poison messages from blocking the consumer. Requires monitoring and alerting.

4. **How to handle event ordering?**
   - Kafka guarantees order within a partition. Use entity ID as key (all events for same entity go to same partition). Cross-entity ordering requires additional coordination.

5. **What are Consumer Groups?**
   - Logical grouping of consumer instances that share partition load. Each partition assigned to exactly one consumer in the group. Enables horizontal scaling of consumers. Different groups independently consume all events.

6. **How does Event Replay work?**
   - New consumer starts from offset 0, processes all historical events to build its state. Kafka retains events based on retention policy. Enables rebuilding read models, fixing bugs, adding new services.

---

## Common Mistakes

1. **No idempotency** — Duplicate processing on redelivery
2. **Ordering assumptions across partitions** — Only guaranteed within partition
3. **No DLQ** — Poison messages block consumer forever
4. **Huge events** — Sending entire database rows in every event
5. **No schema versioning** — Breaking consumers when event format changes
6. **Ignoring consumer lag** — Not monitoring how far behind consumers are

---

## Best Practices

1. **Idempotent consumers** — Handle duplicates gracefully
2. **Schema registry** — Version and validate event schemas
3. **Dead letter queue** — Never lose messages, investigate failures
4. **Key selection** — Entity ID as key for ordering guarantees
5. **Monitor consumer lag** — Alert if consumer falls behind
6. **Event enrichment** — Include enough data to avoid callbacks
7. **Exactly-once semantics** — Use Kafka transactions + idempotent producers
