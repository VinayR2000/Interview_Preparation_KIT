# 8. Consumer ⭐⭐⭐

---

## Theory

The **KafkaConsumer** is the client that reads records from Kafka topics. It manages partition assignment, offset tracking, deserialization, and group coordination.

### KafkaConsumer

**NOT thread-safe** — must be used from a single thread (unlike the producer). Each consumer instance runs a poll loop.

```java
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("orders", "payments"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        process(record);
    }
}
```

### Consumer Poll Loop

The `poll()` method is the heart of the consumer. It does much more than just fetch messages.

```
poll(Duration timeout):
1. Send heartbeat to group coordinator (keeps consumer alive)
2. Check if rebalance needed (join/leave events)
3. Fetch data from assigned partitions (if buffer empty)
4. Auto-commit offsets (if enabled and interval elapsed)
5. Return fetched records (up to max.poll.records)
6. If no data available, wait up to timeout

CRITICAL: poll() must be called regularly!
- Heartbeats sent during poll
- If poll() not called within max.poll.interval.ms → consumer removed from group
```

### Deserialization

Converts bytes back to objects (reverse of producer serialization).

```java
// Configuration
props.put("key.deserializer", StringDeserializer.class);
props.put("value.deserializer", JsonDeserializer.class);

// Custom deserializer
public class OrderEventDeserializer implements Deserializer<OrderEvent> {
    private ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public OrderEvent deserialize(String topic, byte[] data) {
        try {
            return mapper.readValue(data, OrderEvent.class);
        } catch (IOException e) {
            throw new SerializationException("Error deserializing", e);
        }
    }
}
```

---

## Consumer Configuration ⭐⭐⭐

### group.id

```
group.id = "order-processing-group"
  - Identifies the consumer group this consumer belongs to
  - Consumers with same group.id share partitions (load balance)
  - Consumers with different group.id each get ALL messages (broadcast)
  - Required for subscribe(); not needed for assign()
```

### auto.offset.reset

```
auto.offset.reset = "latest" (default)
  What to do when no committed offset exists (new consumer group):
  
  earliest: Start reading from beginning of partition
    → Use when: You need to process ALL historical data
    → Risk: Processing huge backlog on first start
  
  latest: Start reading from end (only new messages)
    → Use when: Only future events matter
    → Risk: Missing messages produced before consumer started
  
  none: Throw exception if no offset found
    → Use when: You want explicit control, fail rather than guess
```

### enable.auto.commit

```
enable.auto.commit = true (default)
  - Automatically commits offsets periodically
  - Committed in background during poll()
  
  Risk: Message processed but offset not yet committed → crash → reprocessing
  Risk: Offset committed but processing failed → message skipped (data loss!)

  auto.commit.interval.ms = 5000 (default)
    - How often auto-commit happens
    - Lower = less reprocessing on crash, more commits to broker
```

### max.poll.records

```
max.poll.records = 500 (default)
  - Maximum records returned per poll() call
  - Controls batch size for processing
  - Higher = more records per iteration, better throughput
  - Lower = faster processing per poll, less risk of exceeding max.poll.interval.ms

  Choose based on:
  - Processing time per record × max.poll.records < max.poll.interval.ms
  - If processing is slow, reduce this value
```

### max.poll.interval.ms

```
max.poll.interval.ms = 300000 (5 minutes, default)
  - Maximum time between poll() calls
  - If exceeded: consumer removed from group, rebalance triggered
  
  Why this matters:
  - You fetch 500 records, processing takes 6 minutes
  - Consumer considered dead → partitions reassigned
  - Another consumer re-reads same messages (duplicate processing!)
  
  Solutions:
  - Reduce max.poll.records
  - Increase max.poll.interval.ms
  - Process faster (async processing, batch DB operations)
```

### session.timeout.ms

```
session.timeout.ms = 45000 (45s, default since Kafka 3.0)
  - Time without heartbeat before consumer is considered dead
  - Lower = faster failure detection, but risk of false positives
  - Higher = slower failure detection, more tolerant of GC pauses

  Note: Heartbeats sent by background thread (not tied to poll())
  This is different from max.poll.interval.ms which tracks poll() calls
```

### heartbeat.interval.ms

```
heartbeat.interval.ms = 3000 (3s, default)
  - How often consumer sends heartbeat to group coordinator
  - Must be < session.timeout.ms (typically 1/3 of it)
  - Lower = faster detection of consumer failure
  - Sent by background heartbeat thread (separate from poll())
```

---

## Diagram

### Consumer Internal Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                       KafkaConsumer                              │
│                                                                  │
│  Application Thread (single thread only!)                       │
│  ┌────────────────────────────────────────────────────┐        │
│  │                                                      │        │
│  │  while(true) {                                       │        │
│  │    records = consumer.poll(timeout)                   │        │
│  │    for (record : records) {                          │        │
│  │      process(record)                                 │        │
│  │    }                                                 │        │
│  │    consumer.commitSync()  // if manual commit        │        │
│  │  }                                                   │        │
│  │                                                      │        │
│  └────────────────────────────────────────────────────┘        │
│                            │                                     │
│                            ▼                                     │
│  ┌────────────────────────────────────────────────────┐        │
│  │ Fetcher                                              │        │
│  │ - Sends FetchRequests to brokers                     │        │
│  │ - Buffers fetched records                            │        │
│  │ - Returns up to max.poll.records per poll()          │        │
│  └────────────────────────────────────────────────────┘        │
│                                                                  │
│  Background Heartbeat Thread                                    │
│  ┌────────────────────────────────────────────────────┐        │
│  │ - Sends heartbeats every heartbeat.interval.ms       │        │
│  │ - Receives rebalance notifications                   │        │
│  │ - Triggers consumer to rejoin group                  │        │
│  └────────────────────────────────────────────────────┘        │
│                                                                  │
│  Coordinator                                                    │
│  ┌────────────────────────────────────────────────────┐        │
│  │ - Manages group membership                           │        │
│  │ - Handles join/sync group protocol                   │        │
│  │ - Commits offsets                                    │        │
│  └────────────────────────────────────────────────────┘        │
└────────────────────────────────────────────────────────────────┘
```

### Poll Loop Timeline

```
Time ─────────────────────────────────────────────────────────────►

poll()          process records       poll()         process
  │                   │                 │               │
  ▼                   ▼                 ▼               ▼
┌─────┐ ┌─────────────────────┐ ┌─────┐ ┌─────────────────┐
│fetch│ │ process 500 records  │ │fetch│ │ process 500     │
│     │ │ (must complete       │ │     │ │ records         │
│     │ │  within              │ │     │ │                 │
│     │ │  max.poll.interval)  │ │     │ │                 │
└─────┘ └─────────────────────┘ └─────┘ └─────────────────┘

Heartbeats (background thread):
  ♥──────♥──────♥──────♥──────♥──────♥──────♥──────♥──────

Auto-commit (if enabled, during poll):
  ────────────────────C────────────────────C──────────────
                  (5s interval)          (5s interval)
```

---

## Code

### Complete Consumer Setup (Spring Boot)

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Connection
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
        
        // Group
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processing-group");
        
        // Deserialization
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.events");
        
        // Offset management
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // manual commit
        
        // Polling
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        
        // Session
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);  // 3 consumer threads
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
```

### Consumer Listener Patterns

```java
@Component
@Slf4j
public class OrderEventConsumer {

    // Simple record listener
    @KafkaListener(topics = "orders", groupId = "order-group")
    public void handleOrder(
            @Payload OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment ack) {
        
        log.info("Received: partition={}, offset={}, orderId={}", 
            partition, offset, event.getOrderId());
        
        try {
            orderService.process(event);
            ack.acknowledge();  // commit only after successful processing
        } catch (RetryableException e) {
            // Don't ack — will be redelivered
            throw e;
        }
    }

    // Batch listener (higher throughput)
    @KafkaListener(topics = "events", groupId = "batch-group",
                   containerFactory = "batchFactory")
    public void handleBatch(List<ConsumerRecord<String, Object>> records,
                            Acknowledgment ack) {
        log.info("Batch of {} records", records.size());
        
        List<Object> events = records.stream()
            .map(ConsumerRecord::value)
            .collect(Collectors.toList());
        
        // Process all at once (e.g., batch insert to DB)
        eventRepository.saveAll(events);
        ack.acknowledge();  // commit entire batch
    }

    // Specific partition listener
    @KafkaListener(topicPartitions = @TopicPartition(
        topic = "priority-orders",
        partitions = {"0", "1"}
    ))
    public void handlePriorityOrders(ConsumerRecord<String, Object> record) {
        // Process only from partitions 0 and 1
    }
}
```

---

## Dry Run

### Consumer Startup and Message Processing

```
1. Application starts, Spring creates KafkaListener container
   - Creates KafkaConsumer with group.id="order-group"
   - Subscribes to topic "orders"

2. First poll() call:
   - No committed offsets found for group "order-group"
   - auto.offset.reset=earliest → start from offset 0
   - Consumer joins group (JoinGroup request to coordinator)
   - Coordinator assigns partitions: P0, P1 (this consumer gets 2 of 6)
   - SyncGroup response received with assignment

3. Fetcher sends FetchRequest to:
   - Broker 1 (leader of P0): fetch from offset 0
   - Broker 2 (leader of P1): fetch from offset 0

4. poll() returns 100 records (max.poll.records=100):
   - 60 from P0 (offsets 0-59)
   - 40 from P1 (offsets 0-39)

5. Application processes each record:
   - record[0]: P0, offset=0, key="order-1", value=OrderCreatedEvent
   - record[1]: P0, offset=1, key="order-1", value=OrderPaidEvent
   - ...
   - record[99]: P1, offset=39, key="order-50", value=...

6. acknowledgment.acknowledge() called:
   - Commits: P0=60, P1=40 (next offset to read)
   - Stored in __consumer_offsets topic

7. Next poll():
   - Fetches from P0 offset 60, P1 offset 40
   - Background: heartbeat sent to coordinator
   - Returns next batch of records

8. If processing takes > 5 minutes (max.poll.interval.ms):
   - Coordinator assumes consumer dead
   - Triggers rebalance
   - P0, P1 reassigned to another consumer
   - Current consumer gets "generation fence" error on next poll()
```

---

## Interview Questions

### Q1: Why is KafkaConsumer not thread-safe?

**A:** By design. The consumer maintains state (current offset position, fetch buffers, group coordination) that would be complex to synchronize across threads. Instead, Kafka recommends one consumer per thread with the poll loop pattern. For parallelism, use multiple consumers in a group (each in its own thread), or use a thread pool to process records after polling.

### Q2: What is the difference between session.timeout.ms and max.poll.interval.ms?

**A:**
- **session.timeout.ms:** If no heartbeat received within this time, consumer is dead. Heartbeats are sent by a background thread — separate from processing. Detects JVM crash or network failure.
- **max.poll.interval.ms:** If `poll()` not called within this time, consumer is removed. Detects stuck processing (long operations between polls). This is the "processing timeout."

A consumer can be alive (sending heartbeats) but stuck (not calling poll) — max.poll.interval catches this.

### Q3: When should you use auto.offset.reset=earliest vs latest?

**A:**
- **earliest:** When the consumer must process ALL data (new consumer group, data migration, replay). Risk: huge backlog on first start.
- **latest:** When only future events matter (real-time monitoring, live notifications). Risk: misses events produced between consumer restart.
- **In practice:** Most services use `earliest` with idempotent processing — better to reprocess than miss data.

### Q4: How does enable.auto.commit=false improve reliability?

**A:** With auto-commit, offsets are committed on a timer (every 5s by default), regardless of whether processing succeeded:
- Processing fails after auto-commit → message lost (offset moved past it)
- Processing succeeds before auto-commit + crash → message reprocessed (duplicate)

With manual commit, you commit AFTER successful processing:
```java
process(record);        // process first
ack.acknowledge();      // commit only after success
```
This guarantees at-least-once delivery — never skip a message.

### Q5: What happens if poll() isn't called within max.poll.interval.ms?

**A:**
1. Group coordinator detects poll timeout
2. Consumer removed from group
3. Rebalance triggered — partitions reassigned to remaining consumers
4. The slow consumer's next `poll()` throws `CommitFailedException` (can't commit, no longer owns partitions)
5. Consumer must rejoin group (will be assigned partitions again during next rebalance)
6. Messages it was processing may be reprocessed by the new assignee

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using auto-commit for critical data | Messages lost or duplicated | Use manual commit after processing |
| Long processing between polls | Consumer kicked from group | Reduce max.poll.records or use async processing |
| Sharing consumer across threads | ConcurrentModificationException | One consumer per thread |
| Using poll(0) | Returns immediately with nothing | Use reasonable timeout (100-1000ms) |
| Not handling deserialization errors | Consumer crashes on bad data | Use ErrorHandlingDeserializer or try-catch |
| Committing before processing | Data loss on failure | Always process first, then commit |

---

## Best Practices

1. **Use manual offset commits** — commit after successful processing
2. **Size max.poll.records appropriately** — processing time × records < max.poll.interval.ms
3. **Monitor consumer lag** — growing lag = consumer falling behind
4. **Handle deserialization errors gracefully** — don't let bad messages crash consumers
5. **Use batch processing** for high-throughput scenarios (batch DB inserts)
6. **Set session.timeout = 3× heartbeat.interval** — standard ratio
7. **Implement graceful shutdown** — commit offsets, close consumer

---

## Related Topics

- [09. Consumer Groups](./09-consumer-groups.md)
- [10. Consumer Rebalancing](./10-consumer-rebalancing.md)
- [11. Offset Management](./11-offset-management.md)
- [26. Kafka Error Handling](./26-kafka-error-handling.md)
