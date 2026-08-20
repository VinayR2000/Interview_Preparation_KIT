# 6. Producer ⭐⭐⭐

---

## Theory

The **KafkaProducer** is the client that publishes records to Kafka topics. It handles serialization, partitioning, batching, compression, and delivery guarantees.

### KafkaProducer

Thread-safe client that can be shared across threads. Internally manages connection pooling, batching, and retries.

```java
// Creating a producer
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
```

### ProducerRecord

The message to be sent to Kafka.

```java
// Full constructor
ProducerRecord<K, V>(
    String topic,           // required
    Integer partition,      // optional (explicit partition)
    Long timestamp,         // optional (message timestamp)
    K key,                  // optional (determines partition if no explicit partition)
    V value,               // required (the actual data)
    Iterable<Header> headers // optional (metadata key-value pairs)
)

// Common usage patterns
new ProducerRecord<>("topic", key, value);        // key-based partitioning
new ProducerRecord<>("topic", value);             // no key (sticky/round-robin)
new ProducerRecord<>("topic", 2, key, value);     // explicit partition 2
```

### Key

- Determines partition assignment (`hash(key) % numPartitions`)
- Ensures ordering: same key → same partition → ordered consumption
- Can be null (uses sticky partitioner)
- Should represent the entity/aggregate (orderId, userId, etc.)

### Value

- The actual message content (payload)
- Serialized to bytes by configured serializer
- Cannot be null in most use cases (null value = tombstone in compacted topics)

### Headers

```java
// Key-value metadata pairs attached to a record
ProducerRecord<String, String> record = new ProducerRecord<>("topic", key, value);
record.headers().add("correlationId", correlationId.getBytes());
record.headers().add("source", "order-service".getBytes());
record.headers().add("eventType", "OrderCreated".getBytes());

// Use cases: tracing, routing, versioning, content-type indication
// Headers do NOT affect partitioning
```

### Serialization

Converts key and value objects to byte arrays.

```
Built-in serializers:
- StringSerializer
- IntegerSerializer, LongSerializer, DoubleSerializer
- ByteArraySerializer, ByteBufferSerializer
- UUIDSerializer

Custom/Third-party:
- JsonSerializer (Spring Kafka)
- KafkaAvroSerializer (Confluent)
- KafkaProtobufSerializer (Confluent)
```

---

## Producer Configuration ⭐⭐⭐

### acks (Acknowledgment)

```
acks=0: "Fire and forget"
  Producer doesn't wait for any acknowledgment
  Fastest but highest risk of data loss
  Use: Metrics, logs where loss is acceptable

acks=1: "Leader acknowledged" (default)
  Producer waits for leader to write to its local log
  Leader crash before replication → data loss
  Use: Standard workloads with acceptable rare loss

acks=all (or acks=-1): "All ISR acknowledged"
  Producer waits for ALL in-sync replicas to acknowledge
  Slowest but strongest durability guarantee
  Combined with min.insync.replicas=2 → no data loss
  Use: Financial transactions, critical events
```

### retries

```
retries = 2147483647 (default in Kafka 2.1+, effectively infinite)
  - Number of times to retry a failed send
  - Only retries transient errors (network, leader election)
  - Combined with delivery.timeout.ms to bound total retry time

delivery.timeout.ms = 120000 (2 minutes, default)
  - Total time from send() to acknowledgment (including retries)
  - After this timeout, send() fails regardless of retry count
```

### batch.size

```
batch.size = 16384 (16KB, default)
  - Maximum bytes per batch (per partition)
  - Larger batch = higher throughput, higher latency
  - Batching reduces network requests
  
  Messages accumulate in partition-specific batches:
  P0 batch: [msg1, msg2, msg3] → send when batch.size reached or linger.ms expires
  P1 batch: [msg4] → waiting...
```

### linger.ms

```
linger.ms = 0 (default — send immediately)
  - Time to wait for more messages before sending a batch
  - linger.ms=0: send as soon as any message is ready
  - linger.ms=5: wait up to 5ms to fill the batch
  - Higher linger = bigger batches = better throughput but more latency

Trade-off:
  linger=0:  low latency, many small batches (more network overhead)
  linger=50: higher latency, fewer large batches (better throughput)
```

### buffer.memory

```
buffer.memory = 33554432 (32MB, default)
  - Total memory available for buffering records
  - If buffer is full, send() blocks for max.block.ms (60s default)
  - Then throws BufferExhaustedException
  
  Buffer = sum of all partition batches waiting to be sent
  If producers produce faster than network can send → buffer fills up
```

### compression.type

```
compression.type = none (default)
  Options: none, gzip, snappy, lz4, zstd

  Comparison:
  | Algorithm | Compression Ratio | CPU Cost | Speed    |
  |-----------|-------------------|----------|----------|
  | none      | 1:1              | None     | Fastest  |
  | snappy    | ~2:1             | Low      | Fast     |
  | lz4       | ~2:1             | Low      | Fastest  |
  | gzip      | ~3:1             | High     | Slow     |
  | zstd      | ~3:1             | Medium   | Fast     |

  Recommendation:
  - Low latency: lz4 or snappy
  - Max compression: zstd (best ratio/speed balance)
  - Bandwidth constrained: gzip or zstd
```

### max.in.flight.requests.per.connection

```
max.in.flight.requests = 5 (default)
  - Maximum unacknowledged requests per broker connection
  - Higher = better throughput (pipeline effect)
  - Risk: If request 1 fails and request 2 succeeds → out-of-order!

For strict ordering:
  max.in.flight.requests = 1 → guaranteed order (low throughput)
  OR: enable.idempotence=true → allows up to 5 in-flight with ordering guarantee
```

### enable.idempotence

```
enable.idempotence = true (default since Kafka 3.0)
  - Ensures exactly-once delivery to a partition
  - Prevents duplicates from retries
  - Uses Producer ID (PID) + sequence number per partition
  
  Requires:
  - acks=all
  - retries > 0
  - max.in.flight.requests ≤ 5
  
  How it works:
  - Broker tracks (PID, partition, sequenceNumber)
  - Duplicate detected → returns success without writing
  - Out-of-order detected → rejects, producer retries in order
```

---

## Diagram

### Producer Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         KafkaProducer                                 │
│                                                                       │
│  Application Thread(s)                                               │
│  ┌──────────────────────────────────────────────────────┐           │
│  │ producer.send(record)                                  │           │
│  │     ↓                                                  │           │
│  │ Interceptors (onSend)                                  │           │
│  │     ↓                                                  │           │
│  │ Serializer (key → bytes, value → bytes)               │           │
│  │     ↓                                                  │           │
│  │ Partitioner (determine target partition)               │           │
│  │     ↓                                                  │           │
│  │ RecordAccumulator                                      │           │
│  │   ┌─────────┐ ┌─────────┐ ┌─────────┐              │           │
│  │   │P0 Batch │ │P1 Batch │ │P2 Batch │              │           │
│  │   │[r1,r2]  │ │[r3]     │ │[r4,r5,r6]│             │           │
│  │   └─────────┘ └─────────┘ └─────────┘              │           │
│  └──────────────────────────────────────────────────────┘           │
│                            ↓                                         │
│  Sender Thread (background, single thread)                          │
│  ┌──────────────────────────────────────────────────────┐           │
│  │ 1. Check ready batches (size or linger.ms expired)    │           │
│  │ 2. Group by broker (leader of partition)              │           │
│  │ 3. Compress batch (if configured)                     │           │
│  │ 4. Send ProduceRequest to broker                      │           │
│  │ 5. Receive acknowledgment                             │           │
│  │ 6. Complete futures / invoke callbacks                │           │
│  │ 7. Handle retries on failure                          │           │
│  └──────────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Code

### Complete Producer Configuration

```java
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Connection
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "order-producer");
        
        // Serialization
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Durability
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Batching & Performance
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);        // 32KB
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);            // wait 20ms
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);  // 64MB
        
        // Retries & Timeouts
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

### Sending Messages

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Fire and forget
    public void sendFireAndForget(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
    }

    // Synchronous (blocking, waits for ack)
    public SendResult<String, Object> sendSync(String topic, String key, Object event) 
            throws Exception {
        return kafkaTemplate.send(topic, key, event).get(10, TimeUnit.SECONDS);
    }

    // Asynchronous with callback
    public CompletableFuture<SendResult<String, Object>> sendAsync(
            String topic, String key, Object event) {
        
        return kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Send failed for key={}: {}", key, ex.getMessage());
                    handleFailure(topic, key, event, ex);
                } else {
                    RecordMetadata metadata = result.getRecordMetadata();
                    log.debug("Sent to {}:P{}:O{}", 
                        metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
    }

    // With explicit partition and headers
    public void sendWithDetails(String topic, int partition, String key, 
                                Object event, Map<String, String> headers) {
        ProducerRecord<String, Object> record = 
            new ProducerRecord<>(topic, partition, key, event);
        
        headers.forEach((k, v) -> record.headers().add(k, v.getBytes()));
        kafkaTemplate.send(record);
    }
}
```

---

## Dry Run

### Message Send Flow

```
1. producer.send("orders", "order-123", OrderCreatedEvent{...})

2. Serialization:
   key: "order-123" → bytes (StringSerializer)
   value: OrderCreatedEvent → bytes (JsonSerializer)

3. Partitioning:
   murmur2("order-123") = 1847362981
   1847362981 % 6 = 3  → target: partition 3

4. RecordAccumulator:
   Appends to P3's batch buffer
   Batch currently has 12KB of data (batch.size = 32KB)
   linger.ms = 20ms → timer started

5. After 20ms (linger expired, batch not full):
   Sender thread picks up P3's batch
   Compresses with lz4
   Sends ProduceRequest to Broker 2 (leader of P3)

6. Broker 2 (Leader):
   Appends to P3 log segment
   Waits for ISR replicas (Broker 1, Broker 3) to replicate
   All ISR acknowledged → sends ACK back

7. Producer receives ACK:
   Completes CompletableFuture with SendResult
   Callback invoked: partition=3, offset=4521

8. If network failure at step 5:
   Sender retries (up to delivery.timeout.ms = 120s)
   Idempotence prevents duplicates on retry
   After all retries exhausted → future completes exceptionally
```

---

## Interview Questions

### Q1: Explain the difference between acks=0, acks=1, and acks=all.

**A:**
- **acks=0:** Producer doesn't wait for any confirmation. Maximum throughput, risk of silent data loss. Use for non-critical data (metrics, logs).
- **acks=1:** Producer waits for leader to write to its log. If leader crashes before followers replicate, data is lost. Good balance for most use cases.
- **acks=all:** Producer waits for ALL in-sync replicas to confirm. Strongest guarantee — with `min.insync.replicas=2`, data survives any single broker failure. Required for financial/critical data.

### Q2: How does the idempotent producer prevent duplicates?

**A:** Each producer gets a unique Producer ID (PID) and assigns a sequence number per partition. The broker tracks (PID, partition, lastSequence). On retry:
- If sequence already seen → broker returns success without re-writing (duplicate detected)
- If sequence is ahead (gap) → broker rejects (out-of-order detected)
- This ensures each message is written exactly once to its partition
- Works with up to 5 in-flight requests per connection

### Q3: What is the relationship between batch.size and linger.ms?

**A:** They work together to control batching:
- A batch is sent when EITHER condition is met: `batch.size` reached OR `linger.ms` expires
- `batch.size=32KB, linger.ms=0`: sends immediately (batch may be tiny)
- `batch.size=32KB, linger.ms=50`: waits up to 50ms to fill batch
- For high throughput: increase both (larger batches, more wait time)
- For low latency: small linger.ms (0-5ms)
- The trade-off is always throughput vs. latency

### Q4: What happens when buffer.memory is exhausted?

**A:** When the producer's buffer is full:
1. `send()` blocks for up to `max.block.ms` (default 60s)
2. If buffer doesn't free up within that time → throws `BufferExhaustedException`
3. Buffer exhaustion means the sender thread can't send batches fast enough (network bottleneck, broker slow, too many partitions)
4. Solutions: increase buffer.memory, reduce producer rate, check broker health, increase batch.size/linger.ms for fewer network calls

### Q5: How to achieve exactly-once delivery with a producer?

**A:** Enable idempotent producer:
```properties
enable.idempotence=true  (implies acks=all, retries>0, max.in.flight≤5)
```
This gives exactly-once per partition. For exactly-once across partitions/topics (atomic writes), use transactions:
```java
producer.initTransactions();
producer.beginTransaction();
producer.send(record1);  // topic A
producer.send(record2);  // topic B
producer.commitTransaction();  // atomic: both succeed or both fail
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| acks=0 for critical data | Silent data loss | Use acks=all + min.insync.replicas=2 |
| Not closing producer | Resource leak, unflushed batches | Always close() or use try-with-resources |
| Blocking on send().get() in hot path | Kills throughput | Use async with callbacks |
| Large messages (>1MB) | Exceeds max.message.bytes | Use claim-check pattern (store in S3, send reference) |
| max.in.flight=1 without idempotence | Halves throughput | Enable idempotence instead (allows 5 in-flight safely) |
| Not handling send failures | Lost events | Implement error handling and fallback logic |

---

## Best Practices

1. **Always enable idempotence** — no downside, prevents duplicates
2. **Use acks=all** for important data with min.insync.replicas=2
3. **Tune batch.size + linger.ms** together for your throughput/latency needs
4. **Use async sends with callbacks** — don't block application threads
5. **Set meaningful keys** — ensures partition affinity and ordering
6. **Close the producer gracefully** — flushes pending batches
7. **Monitor producer metrics** — record-send-rate, batch-size-avg, bufferpool-wait-time
8. **Use compression** — lz4 for speed, zstd for ratio

---

## Related Topics

- [07. Producer Internals](./07-producer-internals.md)
- [05. Partitions](./05-partitions.md)
- [12. Message Delivery Semantics](./12-message-delivery-semantics.md)
- [14. Durability & Reliability](./14-durability-reliability.md)
