# 19. Schema Registry

---

## Theory

**Schema Registry** is a centralized service for managing and enforcing schemas (Avro, Protobuf, JSON Schema) used in Kafka. It provides schema versioning, compatibility enforcement, and runtime schema resolution.

### What is Schema Registry?

```
Without Schema Registry:
  Producer changes event format → Consumer breaks (runtime error)
  No validation → anyone can publish anything to any topic

With Schema Registry:
  Producer registers schema → Registry validates compatibility
  Consumer fetches schema by ID → guaranteed compatible deserialization
  Breaking changes REJECTED at registration time
```

### Schema Versioning

```
Subject: "orders-value" (topic name + "-value" or "-key")

Version 1: {orderId: string, amount: double}
Version 2: {orderId: string, amount: double, currency: string (default "USD")}
Version 3: {orderId: string, amount: double, currency: string, region: string (default "US")}

Each version has a unique schema ID (global across all subjects)
Latest version used by default for serialization
```

### Compatibility Types

| Type | Rule | Upgrade Order |
|------|------|---------------|
| BACKWARD | New schema reads old data | Consumer first |
| FORWARD | Old schema reads new data | Producer first |
| FULL | Both directions | Any order |
| BACKWARD_TRANSITIVE | Backward with ALL previous versions | Consumer first |
| FORWARD_TRANSITIVE | Forward with ALL previous versions | Producer first |
| FULL_TRANSITIVE | Full with ALL previous versions | Any order |
| NONE | No check | Dangerous |

### Breaking Changes

```
Allowed (backward compatible):
  ✓ Add field WITH default value
  ✓ Remove field that had default value
  ✓ Change field from required to optional (with default)

NOT allowed (breaks backward compatibility):
  ✗ Remove field without default
  ✗ Change field type (string → int)
  ✗ Rename field (it's remove + add)
  ✗ Add required field without default
```

---

## Code

### Schema Registry API

```bash
# Register schema
curl -X POST http://schema-registry:8081/subjects/orders-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"schema": "{\"type\":\"record\",\"name\":\"Order\",\"fields\":[{\"name\":\"orderId\",\"type\":\"string\"}]}"}'

# Get latest schema
curl http://schema-registry:8081/subjects/orders-value/versions/latest

# Check compatibility
curl -X POST http://schema-registry:8081/compatibility/subjects/orders-value/versions/latest \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"schema": "..."}'

# Set compatibility level
curl -X PUT http://schema-registry:8081/config/orders-value \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"compatibility": "BACKWARD"}'
```

### Spring Boot with Schema Registry

```yaml
spring:
  kafka:
    properties:
      schema.registry.url: http://schema-registry:8081
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        specific.avro.reader: true
```

---

## Interview Questions

### Q1: Why is Schema Registry important in a microservices architecture?

**A:** In microservices, multiple teams produce and consume from shared topics. Without Schema Registry: any team can push breaking changes, causing runtime failures. With Schema Registry: schemas are contracts enforced at registration time. Breaking changes are rejected before reaching production. Teams can evolve schemas independently while maintaining compatibility. It's essentially API versioning for events.

### Q2: What is the difference between BACKWARD and FULL compatibility?

**A:** BACKWARD ensures new consumers can read old data (upgrade consumers first). FULL ensures BOTH — new consumers read old data AND old consumers read new data. FULL is more restrictive (only allows adding/removing optional fields with defaults) but allows upgrading producers and consumers in any order. Use FULL for critical shared topics, BACKWARD for single-team topics.

---

## Related Topics

- [18. Serialization](./18-serialization.md)
- [36. Production-Level Kafka](./36-production-level-kafka.md)
