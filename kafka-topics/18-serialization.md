# 18. Serialization ⭐⭐

---

## Theory

Kafka stores and transmits data as byte arrays. Serialization converts objects to bytes (producer side), and deserialization converts bytes back to objects (consumer side).

### Built-in Serializers

```
StringSerializer / StringDeserializer     — plain text
IntegerSerializer / IntegerDeserializer   — 4-byte integer
LongSerializer / LongDeserializer         — 8-byte long
ByteArraySerializer / ByteArrayDeserializer — raw bytes
```

### JSON Serialization

```
Pros: Human-readable, flexible, no schema registry needed
Cons: Larger payload, no schema enforcement, no evolution guarantees

Producer: Object → JSON string → bytes
Consumer: bytes → JSON string → Object

// Spring Kafka
key-serializer: StringSerializer
value-serializer: JsonSerializer
value-deserializer: JsonDeserializer
```

### Avro Serialization

```
Pros: Compact binary format, schema evolution, Schema Registry integration
Cons: Requires Schema Registry, not human-readable

Structure:
  Schema (defined separately) + Data (compact binary encoding)
  Schema stored in Schema Registry (referenced by ID)
  Message contains: [magic byte][schema ID (4 bytes)][Avro data]

Evolution: Add/remove fields with defaults → backward/forward compatible
```

### Protobuf (Protocol Buffers)

```
Pros: Very compact, strongly typed, excellent language support, evolution
Cons: Requires .proto files, Schema Registry for Kafka integration

Similar to Avro but:
- Uses .proto file definitions
- More language-native feel (generated classes)
- Slightly less compact than Avro for some schemas
- Better tooling in many languages
```

### Schema Evolution

```
The problem: Producer changes event structure over time
  v1: {orderId, amount}
  v2: {orderId, amount, currency}     ← new field
  v3: {orderId, amount, currency}     ← removed field from v1

Without schema evolution:
  Consumer built for v1 crashes on v2 message

With schema evolution:
  Consumer built for v1 reads v2 message → ignores unknown fields
  Consumer built for v2 reads v1 message → uses default for missing fields
```

### Compatibility Modes

```
BACKWARD (default):
  New schema can read data written by OLD schema
  Consumer upgrade first, then producer
  Rule: can add fields with defaults, can remove fields

FORWARD:
  Old schema can read data written by NEW schema
  Producer upgrade first, then consumer
  Rule: can remove fields with defaults, can add fields

FULL:
  Both backward and forward compatible
  Any upgrade order is safe
  Rule: only add/remove fields with defaults

NONE:
  No compatibility checking
  Any change allowed (dangerous for production)
```

---

## Code

### JSON Serialization (Spring Kafka)

```java
// Configuration
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);  // adds __TypeId__ header
    return new DefaultKafkaProducerFactory<>(props);
}

@Bean
public ConsumerFactory<String, Object> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.events");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
    return new DefaultKafkaConsumerFactory<>(props);
}
```

### Avro with Schema Registry

```java
// Producer config
props.put("key.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
props.put("schema.registry.url", "http://schema-registry:8081");

// Consumer config
props.put("key.deserializer", "io.confluent.kafka.serializers.KafkaAvroDeserializer");
props.put("value.deserializer", "io.confluent.kafka.serializers.KafkaAvroDeserializer");
props.put("schema.registry.url", "http://schema-registry:8081");
props.put("specific.avro.reader", true);

// Avro schema (order.avsc):
{
  "type": "record",
  "name": "OrderEvent",
  "namespace": "com.example.events",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "amount", "type": "double"},
    {"name": "currency", "type": "string", "default": "USD"},
    {"name": "timestamp", "type": "long"}
  ]
}
```

### Custom Serializer

```java
public class OrderEventSerializer implements Serializer<OrderEvent> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, OrderEvent data) {
        if (data == null) return null;
        try {
            return mapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Error serializing OrderEvent", e);
        }
    }
}
```

---

## Interview Questions

### Q1: When would you choose Avro over JSON for Kafka?

**A:**
- **Choose Avro when:** Schema enforcement needed, bandwidth matters (2-3x smaller than JSON), multiple teams share topics, schema evolution is expected, strong type safety required.
- **Choose JSON when:** Simplicity preferred, debugging (human-readable), no Schema Registry available, prototype/small teams, schema changes are rare.
- In production microservices with multiple teams: Avro + Schema Registry is preferred.

### Q2: How does Schema Registry work with Kafka?

**A:** Schema Registry is a separate service (not part of Kafka) that stores and manages schemas:
1. Producer registers schema → gets schema ID
2. Producer serializes: [magic_byte(1) + schema_id(4) + data(N)]
3. Consumer reads schema_id from message → fetches schema from registry
4. Consumer deserializes using that schema
5. Registry enforces compatibility rules on new schema versions
6. Result: Decoupled evolution — producer and consumer can be on different schema versions.

### Q3: What is backward compatibility and why is it the default?

**A:** Backward compatibility means a NEW consumer schema can read data written with an OLD producer schema. It's the default because in most deployments, consumers are upgraded before producers (read code deployed before write code). Rules: new schema can add fields (must have defaults) and remove fields. Example: Adding `currency` field with default `"USD"` — old messages without `currency` read fine (default applied).

---

## Best Practices

1. **Use Schema Registry in production** — prevents incompatible changes
2. **Choose Avro or Protobuf** for cross-team topics (enforce contracts)
3. **Use JSON for internal services** where simplicity matters
4. **Always provide defaults** for new fields (enables backward compatibility)
5. **Never rename fields** — add new field, deprecate old one
6. **Version your schemas** — track evolution history

---

## Related Topics

- [19. Schema Registry](./19-schema-registry.md)
- [06. Producer](./06-producer.md)
- [08. Consumer](./08-consumer.md)
