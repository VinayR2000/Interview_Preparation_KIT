# 1. Kafka Fundamentals

---

## Theory

**Apache Kafka** is a distributed event streaming platform originally developed at LinkedIn (2011) and later open-sourced under the Apache Software Foundation. It's designed for high-throughput, fault-tolerant, real-time data streaming.

### What is Kafka?

Kafka is a **distributed commit log** — an append-only, ordered, immutable sequence of records. It combines messaging, storage, and stream processing into a single platform.

```
Traditional DB: Store current state (snapshot)
Kafka:          Store sequence of events (log of changes)
```

### Why Kafka?

| Problem | Kafka Solution |
|---------|---------------|
| Point-to-point coupling between services | Centralized event bus (decoupling) |
| Data loss during failures | Replication + durability guarantees |
| Scalability bottleneck | Horizontal scaling via partitions |
| No event replay | Retained log allows re-reading past events |
| Slow consumers blocking producers | Asynchronous pub/sub model |

### Event Streaming

Event streaming is the practice of capturing data in real-time from event sources (databases, sensors, applications) as streams of events, storing them durably, and processing/reacting to them.

```
Event: An immutable fact that something happened
  - "Order #123 was placed at 2024-01-15T10:30:00Z"
  - "User logged in"
  - "Payment of $500 was processed"

Stream: Unbounded, continuously generated sequence of events
```

### Event-Driven Architecture (EDA)

```
Traditional (Request-Response):
  OrderService → calls → InventoryService → calls → NotificationService
  (synchronous, tightly coupled)

Event-Driven:
  OrderService publishes → "OrderCreated" event → Kafka
    ↓                        ↓                       ↓
  InventoryService        NotificationService     AnalyticsService
  (each consumes independently)
```

**Benefits:**
- Loose coupling (producers don't know consumers)
- Independent scalability
- Event replay and auditing
- Eventual consistency

### Kafka vs Traditional Message Queue

| Feature | Kafka | Traditional MQ (RabbitMQ/ActiveMQ) |
|---------|-------|-----------------------------------|
| Model | Distributed log | Message queue/broker |
| Retention | Configurable (days/forever) | Deleted after consumption |
| Replay | Yes (re-read from any offset) | No (once consumed, gone) |
| Consumer model | Pull-based | Push-based (RabbitMQ) |
| Ordering | Per partition guaranteed | Per queue (single consumer) |
| Throughput | ~1M+ msg/sec per broker | ~10K-50K msg/sec |
| Scaling | Horizontal (add partitions) | Vertical (limited) |
| Use case | Event streaming, log aggregation | Task queues, RPC |

### Kafka vs RabbitMQ

| Aspect | Kafka | RabbitMQ |
|--------|-------|----------|
| Architecture | Distributed log | Message broker |
| Message lifecycle | Retained after consumption | Deleted after ack |
| Routing | Topic + partition (simple) | Exchange + routing key (flexible) |
| Consumer groups | Built-in (partition assignment) | Competing consumers |
| Replay | Native | Not supported |
| Latency | Low (ms), optimized for throughput | Very low (sub-ms), optimized for latency |
| Protocol | Custom binary protocol | AMQP |
| Best for | Event streaming, data pipelines | Task distribution, RPC, routing |

### Pub/Sub Model

```
Publisher (Producer):
  - Publishes messages to a named topic
  - Does not know who will consume

Subscriber (Consumer):
  - Subscribes to topics
  - Receives messages asynchronously
  - Multiple subscribers can consume same message

Kafka Pub/Sub:
  Producer → Topic → Consumer Group 1 (each group gets ALL messages)
                   → Consumer Group 2
                   → Consumer Group 3
```

### Kafka Use Cases

1. **Event Sourcing** — Store all state changes as events
2. **Log Aggregation** — Collect logs from multiple services
3. **Stream Processing** — Real-time data transformation
4. **Metrics/Monitoring** — Collect and process metrics
5. **CDC (Change Data Capture)** — Database change streaming
6. **Messaging** — Asynchronous service communication
7. **Activity Tracking** — User behavior tracking (LinkedIn's original use)
8. **Data Pipeline** — Move data between systems (ETL replacement)

---

## Diagram

### Kafka Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         KAFKA CLUSTER                                 │
│                                                                       │
│  ┌────────────┐    ┌────────────┐    ┌────────────┐                │
│  │  Broker 1  │    │  Broker 2  │    │  Broker 3  │                │
│  │            │    │            │    │            │                │
│  │ Topic-A P0 │    │ Topic-A P1 │    │ Topic-A P2 │                │
│  │ Topic-B P1 │    │ Topic-B P2 │    │ Topic-B P0 │                │
│  │ (Leader)   │    │ (Leader)   │    │ (Leader)   │                │
│  └────────────┘    └────────────┘    └────────────┘                │
│         │                 │                 │                        │
│         └─────── Replicas synced ───────────┘                        │
│                                                                       │
│  Controller: Broker 1 (manages metadata, leader election)            │
│  ZooKeeper/KRaft: Cluster coordination                               │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
          ┌─────────────────┼─────────────────┐
          │                 │                 │
    ┌─────┴─────┐    ┌─────┴─────┐    ┌─────┴─────┐
    │ Producer 1│    │ Producer 2│    │ Producer 3│
    │ (Order    │    │ (Payment  │    │ (User     │
    │  Service) │    │  Service) │    │  Service) │
    └───────────┘    └───────────┘    └───────────┘

    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
    │ Consumer    │    │ Consumer    │    │ Consumer    │
    │ Group A     │    │ Group B     │    │ Group C     │
    │ (Analytics) │    │ (Notification)│  │ (Audit)     │
    └─────────────┘    └─────────────┘    └─────────────┘
```

### Event Flow

```
1. Producer creates event
2. Serializer converts to bytes
3. Partitioner selects partition (by key hash or round-robin)
4. Message sent to Leader broker for that partition
5. Leader appends to commit log
6. Followers replicate from Leader
7. Acknowledgment sent to Producer (based on acks config)
8. Consumer polls from Leader (or follower in some configs)
9. Consumer processes message
10. Consumer commits offset
```

---

## Interview Questions

### Q1: What is Apache Kafka and why was it created?

**A:** Kafka is a distributed event streaming platform designed for high-throughput, fault-tolerant pub/sub messaging. Created at LinkedIn to solve the problem of connecting multiple data systems — instead of N×M point-to-point connections, all systems publish/consume through Kafka as a central nervous system.

### Q2: When would you choose Kafka over RabbitMQ?

**A:**
- **Choose Kafka when:** You need event replay, high throughput (millions msg/sec), log retention, ordered processing within partitions, stream processing, event sourcing, or multiple consumer groups reading the same data.
- **Choose RabbitMQ when:** You need complex routing (headers, topics, fanout exchanges), very low latency (sub-ms), message priority, traditional request-reply patterns, or task queue with acknowledgment-based deletion.

### Q3: Explain event-driven architecture and Kafka's role.

**A:** EDA is a software design pattern where services communicate by producing and consuming events rather than direct API calls. Kafka acts as the event backbone — producers publish domain events (OrderCreated, PaymentProcessed) to topics. Consumers subscribe independently, enabling loose coupling, independent scaling, event replay, and eventual consistency.

### Q4: What are the key guarantees Kafka provides?

**A:**
1. **Ordering** — Messages within a partition maintain strict order
2. **Durability** — Messages are persisted to disk and replicated
3. **At-least-once delivery** — Default guarantee (exactly-once possible with config)
4. **Availability** — Continues operating during broker failures (if replicated)
5. **Scalability** — Horizontal scaling by adding partitions and brokers

### Q5: What is the difference between a message queue and an event log?

**A:**
- **Message queue:** Messages consumed once then deleted. Consumer acknowledges, message removed. No replay possible.
- **Event log (Kafka):** Messages are appended to immutable log, retained for configured duration. Multiple consumers can read independently. Replay possible by resetting offset. Message not deleted upon consumption.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using Kafka for simple request-reply | Overkill, adds latency | Use REST/gRPC for sync communication |
| Treating Kafka as a database | Not designed for random access queries | Use for streaming, not primary storage |
| Ignoring ordering requirements | Messages processed out of order | Use partition keys for ordered entities |
| Not planning retention | Disk fills up or events lost | Configure retention based on replay needs |
| Using Kafka for tiny payloads with extreme low-latency needs | Kafka optimized for throughput, not sub-ms latency | Use Redis Pub/Sub or direct messaging |

---

## Best Practices

1. **Design events as facts** — immutable, past-tense (OrderCreated, not CreateOrder)
2. **Use meaningful partition keys** — ensures related events stay ordered
3. **Plan topic granularity** — one topic per event type or per bounded context
4. **Set retention policies** — balance between replay needs and storage costs
5. **Document event schemas** — use Schema Registry for evolution
6. **Think about consumer independence** — each consumer group reads at its own pace
7. **Start with at-least-once** — simpler than exactly-once, pair with idempotent consumers

---

## Related Topics

- [02. Core Kafka Components](./02-core-kafka-components.md)
- [03. Kafka Architecture](./03-kafka-architecture.md)
- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
- [34. Kafka + Microservices](./34-kafka-microservices.md)
