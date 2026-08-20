# 22. Kafka APIs

---

## Theory

Kafka provides five core APIs for different interaction patterns.

### Producer API

Publish records to topics. Handles serialization, partitioning, batching, and delivery.

```java
KafkaProducer<String, String> producer = new KafkaProducer<>(props);
producer.send(new ProducerRecord<>("topic", "key", "value"));
```

### Consumer API

Subscribe to topics and process records. Manages group membership, offset tracking, and deserialization.

```java
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(List.of("topic"));
ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
```

### Admin API

Manage topics, configurations, ACLs, and cluster metadata programmatically.

```java
AdminClient admin = AdminClient.create(props);
admin.createTopics(List.of(new NewTopic("orders", 6, (short) 3)));
admin.describeTopics(List.of("orders"));
admin.listConsumerGroups();
admin.deleteConsumerGroups(List.of("old-group"));
```

### Streams API

Client library for building stream processing applications. Processes data in real-time with exactly-once semantics.

```java
StreamsBuilder builder = new StreamsBuilder();
KStream<String, String> stream = builder.stream("input-topic");
stream.filter((key, value) -> value.contains("important"))
      .mapValues(value -> value.toUpperCase())
      .to("output-topic");

KafkaStreams streams = new KafkaStreams(builder.build(), props);
streams.start();
```

### Connect API

Framework for streaming data between Kafka and external systems (databases, file systems, search engines).

```json
{
  "name": "jdbc-source-connector",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
    "connection.url": "jdbc:mysql://localhost:3306/mydb",
    "table.whitelist": "orders",
    "mode": "incrementing",
    "incrementing.column.name": "id",
    "topic.prefix": "db-"
  }
}
```

---

## Diagram

### API Ecosystem

```
┌─────────────────────────────────────────────────────────────┐
│                        Kafka Cluster                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Topics & Partitions                      │    │
│  └─────────────────────────────────────────────────────┘    │
└──────┬──────────┬──────────┬──────────┬──────────┬──────────┘
       │          │          │          │          │
┌──────┴───┐ ┌───┴────┐ ┌───┴────┐ ┌───┴────┐ ┌───┴────┐
│ Producer │ │Consumer│ │ Streams│ │Connect │ │ Admin  │
│ API      │ │ API    │ │ API    │ │ API    │ │ API    │
│          │ │        │ │        │ │        │ │        │
│ Publish  │ │ Read   │ │Process │ │External│ │Manage  │
│ events   │ │ events │ │streams │ │systems │ │cluster │
└──────────┘ └────────┘ └────────┘ └────────┘ └────────┘
                                        │
                              ┌─────────┴─────────┐
                              │                   │
                         ┌────┴────┐         ┌────┴────┐
                         │ Source  │         │  Sink   │
                         │Connector│         │Connector│
                         │(DB→Kafka)│        │(Kafka→DB)│
                         └─────────┘         └─────────┘
```

---

## Interview Questions

### Q1: When would you use Kafka Streams vs a separate consumer application?

**A:** Use **Kafka Streams** for: stateful stream processing (aggregations, joins, windowing), exactly-once processing within Kafka, when input AND output are Kafka topics. Use **Consumer API** for: simple consumption with external side effects (DB writes, API calls), when you need full control over processing, when output isn't a Kafka topic.

### Q2: What is the advantage of Kafka Connect over writing custom consumers for data integration?

**A:** Kafka Connect provides: fault-tolerant offset tracking, distributed mode (scale workers), hundreds of pre-built connectors (JDBC, Elasticsearch, S3), schema evolution support, and standardized configuration. Custom consumers require implementing all of this manually. Connect is the right choice for ETL-style data movement; custom consumers for complex business logic.

---

## Related Topics

- [23. Kafka Streams](./23-kafka-streams.md)
- [24. Kafka Connect](./24-kafka-connect.md)
- [25. Kafka + Spring Boot](./25-kafka-spring-boot.md)
