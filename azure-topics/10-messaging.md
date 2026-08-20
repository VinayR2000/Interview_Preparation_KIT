# Azure Messaging Services

## Theory

### Messaging Options

| Service | Pattern | AWS Equivalent | Use Case |
|---------|---------|----------------|----------|
| Azure Service Bus | Enterprise messaging (queue/pub-sub) | SQS + SNS | Reliable messaging, ordering, transactions |
| Azure Event Grid | Event routing | EventBridge | React to Azure/custom events |
| Azure Event Hubs | Event streaming | Kinesis / MSK (Kafka) | High-throughput streaming, telemetry |
| Azure Queue Storage | Simple queue | SQS (basic) | Lightweight decoupling |

### Decision Framework ⭐⭐⭐

```
What do you need?
├── Enterprise messaging (ordered, transactional, exactly-once)?
│   └── Azure Service Bus
│
├── React to events (Azure resource changes, webhooks)?
│   └── Azure Event Grid
│
├── High-throughput event streaming (millions/sec)?
│   └── Azure Event Hubs (Kafka-compatible)
│
├── Simple lightweight queue?
│   └── Azure Queue Storage
│
└── Full Kafka ecosystem?
    └── Azure Event Hubs with Kafka protocol
    └── OR Confluent Cloud on Azure
```

---

## Azure Service Bus ⭐⭐⭐

### Core Concepts

```
Service Bus Namespace: sb-ecommerce-prod
│
├── Queue: order-processing
│   ├── Message 1: {orderId: "123", action: "process"}
│   ├── Message 2: {orderId: "456", action: "process"}
│   └── Dead-Letter Queue (DLQ): failed messages
│
├── Topic: order-events
│   ├── Subscription: payment-service (filter: action = 'payment')
│   │   └── Messages matching filter
│   ├── Subscription: inventory-service (filter: action = 'ship')
│   │   └── Messages matching filter
│   └── Subscription: notification-service (filter: all)
│       └── All messages
│
└── Topic: user-events
    ├── Subscription: audit-service
    └── Subscription: analytics-service
```

### Queue vs Topic

| Feature | Queue | Topic |
|---------|-------|-------|
| Pattern | Point-to-point | Publish/Subscribe |
| Receivers | One consumer per message | Multiple subscribers |
| Use case | Task processing | Event broadcasting |
| AWS equivalent | SQS | SNS + SQS |

### Key Features ⭐⭐⭐

| Feature | Description |
|---------|-------------|
| FIFO ordering | Sessions guarantee ordering within a session |
| Dead-Letter Queue | Failed messages moved to DLQ for investigation |
| Message lock | Peek-Lock prevents other consumers from processing |
| Duplicate detection | Prevents duplicate processing (message ID-based) |
| Scheduled delivery | Send message now, deliver at future time |
| TTL | Messages expire after time-to-live |
| Sessions | Group related messages, ordered processing |
| Transactions | Send/complete multiple messages atomically |
| Max message size | 256 KB (Standard) / 100 MB (Premium) |

### Message Processing Patterns

#### Peek-Lock (Recommended) ⭐⭐⭐
```
1. Consumer receives message (message locked)
2. Consumer processes message
3. If success → Complete (message deleted)
4. If failure → Abandon (message unlocked, redelivered)
5. If lock expires → Message redelivered to another consumer
6. After max delivery count → Message sent to Dead-Letter Queue

Timeline:
Receive → [Lock Duration: 30s] → Complete/Abandon
                                   │
                                   └── If not completed → DLQ (after N retries)
```

#### Receive-and-Delete
```
1. Consumer receives message (immediately deleted)
2. If processing fails → message is LOST
3. Use only when message loss is acceptable
```

### Dead-Letter Queue (DLQ) ⭐⭐⭐
```
Main Queue: order-processing
    │
    ├── Message processed successfully → deleted ✓
    │
    ├── Message fails processing (max delivery count: 10)
    │   └── Moved to DLQ
    │
    └── Message expired (TTL exceeded)
        └── Moved to DLQ

DLQ: order-processing/$deadletterqueue
├── Contains failed/expired messages
├── Reason and error description in properties
└── Must be manually processed or purged
```

### Spring Boot + Service Bus

```java
@Service
public class OrderEventPublisher {

    private final ServiceBusSenderClient sender;

    public void publishOrderCreated(OrderEvent event) {
        ServiceBusMessage message = new ServiceBusMessage(
            objectMapper.writeValueAsString(event)
        );
        message.setContentType("application/json");
        message.setSubject("order-created");
        message.setMessageId(event.getOrderId()); // deduplication
        sender.sendMessage(message);
    }
}

@Service
public class OrderEventConsumer {

    @ServiceBusListener(destination = "order-events", 
                        subscription = "payment-service")
    public void handleOrderEvent(ServiceBusReceivedMessage message) {
        OrderEvent event = objectMapper.readValue(
            message.getBody().toString(), OrderEvent.class
        );
        paymentService.processPayment(event);
        // Auto-complete on success, auto-abandon on exception
    }
}
```

---

## Azure Event Grid ⭐⭐

### What is Event Grid?
A fully managed event routing service. Reacts to events from Azure services or custom sources and routes them to handlers.

### Architecture

```
Event Sources                    Event Grid                 Event Handlers
├── Azure Storage        ──────►                   ──────► Azure Functions
│   (blob created)               Event Grid Topic          Azure Logic Apps
├── Resource Group       ──────►                   ──────► Service Bus Queue
│   (resource changed)           Subscriptions +           Webhooks (your API)
├── Entra ID            ──────►  Filters                   Event Hubs
│   (user created)                                         Storage Queue
├── Custom App          ──────►
│   (order placed)
└── Azure IoT Hub       ──────►
```

### Event Grid vs Service Bus

| Feature | Event Grid | Service Bus |
|---------|-----------|-------------|
| Purpose | Event notification ("something happened") | Message processing ("do this work") |
| Delivery | Push (webhook) | Pull (consumer fetches) |
| Retry | Built-in with DLQ | Peek-Lock with retry |
| Ordering | No guarantee | Sessions guarantee ordering |
| Use case | React to Azure events, webhooks | Reliable task processing |
| Latency | Near real-time | Slightly higher |

---

## Azure Event Hubs ⭐⭐⭐

### What is Event Hubs?
A big data streaming platform and event ingestion service. Handles millions of events per second. **Kafka-compatible** — your existing Kafka code works with Event Hubs.

### Architecture

```
Event Hubs Namespace: eh-analytics-prod
│
├── Event Hub: clickstream (≈ Kafka topic)
│   ├── Partition 0: [event1, event4, event7, ...]
│   ├── Partition 1: [event2, event5, event8, ...]
│   ├── Partition 2: [event3, event6, event9, ...]
│   └── Partition 3: [event10, event11, ...]
│
│   Consumer Groups:
│   ├── $Default
│   ├── analytics-processors
│   └── real-time-dashboard
│
├── Event Hub: orders
│   ├── Partition 0-7
│   ├── Retention: 7 days
│   └── Throughput: 10 TUs
│
└── Capture: → Blob Storage / ADLS (auto-archive)
```

### Event Hubs vs Kafka Concepts

| Kafka | Event Hubs |
|-------|-----------|
| Cluster | Namespace |
| Topic | Event Hub |
| Partition | Partition |
| Consumer Group | Consumer Group |
| Offset | Offset/Sequence Number |
| Broker | Throughput Unit (TU) |

### Kafka Compatibility ⭐⭐⭐
```
Your existing Kafka producers/consumers work with Event Hubs!
Just change the connection string:

# Kafka bootstrap servers → Event Hubs endpoint
bootstrap.servers=eh-analytics-prod.servicebus.windows.net:9093
security.protocol=SASL_SSL
sasl.mechanism=PLAIN
sasl.jaas.config=...connection-string...

Your Spring Boot Kafka code works unchanged!
```

### Tiers

| Tier | Throughput | Partitions | Use Case |
|------|-----------|-----------|----------|
| Basic | 1 TU = 1 MB/s in, 2 MB/s out | 32 max | Dev/test |
| Standard | 20 TUs max | 32 max | Most production |
| Premium | No TU limits | 100 max | High-end workloads |
| Dedicated | Exclusive capacity | 2048 max | Mission-critical |

### Event Hubs Capture
Automatically archive events to Blob Storage or Data Lake:
```
Event Hubs → Capture → Blob Storage
                        ├── /clickstream/2024/01/15/08/00.avro
                        ├── /clickstream/2024/01/15/08/05.avro
                        └── /clickstream/2024/01/15/08/10.avro
                        (5-minute windows, Avro format)
```

---

## Service Bus vs Event Grid vs Event Hubs ⭐⭐⭐

| Feature | Service Bus | Event Grid | Event Hubs |
|---------|------------|-----------|-----------|
| Pattern | Reliable messaging | Event routing | Event streaming |
| Throughput | Moderate | High | Very high (millions/sec) |
| Ordering | FIFO (sessions) | No | Per partition |
| Retention | Until consumed | 24 hours | 1-90 days |
| Consumer model | Competing consumers | Push (webhook) | Consumer groups |
| Replay | No (once consumed, gone) | No | Yes (offset-based) |
| Use case | Task queues, workflows | React to events | Analytics, streaming |
| Kafka compatible | No | No | Yes |

### When to Use What ⭐⭐⭐

```
"Process this order reliably, exactly once, in order"
→ Service Bus (Queue with Sessions)

"Notify multiple services that a blob was uploaded"
→ Event Grid

"Ingest 1 million click events per second for analytics"
→ Event Hubs

"Replace our Kafka cluster with a managed service"
→ Event Hubs (Kafka protocol)

"Decouple microservices with pub/sub messaging"
→ Service Bus (Topics + Subscriptions)
```

---

## Interview Questions

### Q: Service Bus vs Event Hubs — when to use which?
**A:**
- **Service Bus**: Enterprise messaging. Use for reliable, ordered, transactional message processing. Supports competing consumers, dead-letter queues, sessions (FIFO). Messages consumed once and deleted. Best for: order processing, payment workflows, command patterns.
- **Event Hubs**: Event streaming. Use for high-throughput event ingestion (millions/sec). Supports replay (offset-based), long retention, Kafka compatibility. Multiple consumers read the same stream. Best for: clickstream analytics, telemetry, log aggregation, real-time dashboards.

**Key difference**: Service Bus = reliable task processing. Event Hubs = high-volume event streaming with replay.

### Q: How does Azure Event Hubs relate to Apache Kafka?
**A:** Event Hubs supports the Kafka wire protocol. This means:
- Kafka producers can publish to Event Hubs without code changes (just connection config)
- Kafka consumers can read from Event Hubs
- Spring Boot Kafka applications work with Event Hubs
- You get a managed, serverless "Kafka" without cluster management
- Limitations: No Kafka Streams, no exactly-once semantics, no compacted topics

### Q: Explain the Dead-Letter Queue pattern in Service Bus.
**A:** DLQ is a sub-queue for messages that can't be processed:
- Messages exceeding max delivery count (repeated failures)
- Messages exceeding TTL
- Messages explicitly dead-lettered by application logic

DLQ messages include reason/description. They must be manually investigated (monitoring alert → developer reviews → fix issue → resubmit or delete). Critical for reliability: no message is ever silently lost.

### Q: How would you design an event-driven order system on Azure?
**A:**
```
Order API (Spring Boot)
    │
    ▼ (publish)
Service Bus Topic: order-events
    │
    ├── Subscription: payment-service
    │   └── Processes payment, publishes payment-completed
    ├── Subscription: inventory-service
    │   └── Reserves stock
    ├── Subscription: notification-service
    │   └── Sends email/SMS
    └── Subscription: analytics (→ Event Hubs for streaming)

Dead-Letter handling:
├── Azure Function monitors DLQ
├── Alerts on DLQ messages
└── Dashboard for retry/investigation
```

Service Bus Topics for reliable pub/sub between microservices. Event Hubs for analytics streaming. Event Grid for reacting to infrastructure events.
