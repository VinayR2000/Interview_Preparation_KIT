# 3. Communication Patterns

## Theory

Microservices need to communicate to fulfill business operations. The communication style you choose fundamentally impacts coupling, reliability, and performance.

### Synchronous Communication:
- **REST**: HTTP-based, JSON, widely used, simple
- **gRPC**: Binary protocol (Protobuf), faster, strongly typed, bidirectional streaming
- **HTTP**: Direct service-to-service calls

### Asynchronous Communication ⭐⭐⭐⭐⭐:
- **Kafka**: Distributed event streaming, high throughput, persistent
- **RabbitMQ**: Message broker, routing, traditional queuing
- **Event-driven**: Services communicate through events, not direct calls

### Patterns:
- **Publish/Subscribe**: Publisher sends events, multiple subscribers receive
- **Event Notification**: Minimal event data, consumer fetches details if needed
- **Event Streaming**: Full event history, consumers replay from any point

---

## Internal Working

### Synchronous vs Asynchronous:

```
SYNCHRONOUS (REST/gRPC):
┌──────┐  HTTP Request   ┌──────────┐
│Order │ ───────────────→ │ Payment  │
│ Svc  │ ←─────────────── │  Svc     │
└──────┘  HTTP Response   └──────────┘

Problems:
- Order Service waits (blocked)
- If Payment is down → Order fails
- Tight coupling in time
- Cascading failures

ASYNCHRONOUS (Kafka/RabbitMQ):
┌──────┐  Publish Event   ┌───────┐  Consume   ┌──────────┐
│Order │ ───────────────→ │ Kafka │ ─────────→ │ Payment  │
│ Svc  │                  │       │            │  Svc     │
└──────┘                  └───────┘            └──────────┘

Benefits:
- Order Service doesn't wait
- If Payment is down → message waits in Kafka
- Loose coupling in time
- Better fault tolerance
```

### Event-Driven Communication Flow:

```
Order Created Event Flow:

Order Service
    │
    │ Publish: OrderCreatedEvent
    │ {orderId, customerId, items, total}
    ↓
┌─────────────────────────────────┐
│            KAFKA                 │
│  Topic: order-events            │
│  Partition 0: [event1, event2]  │
│  Partition 1: [event3, event4]  │
└──────────┬──────────┬───────────┘
           │          │
    ┌──────┘          └──────┐
    ↓                        ↓
┌─────────┐          ┌──────────┐
│ Payment │          │ Inventory│
│ Service │          │ Service  │
│         │          │          │
│ Process │          │ Reserve  │
│ payment │          │ stock    │
└─────────┘          └──────────┘
    │                      │
    ↓                      ↓
PaymentProcessedEvent  StockReservedEvent
```

### Communication Patterns Comparison:

```
┌────────────────────────────────────────────────────────────┐
│ Pattern              │ Coupling │ Latency  │ Reliability  │
├────────────────────────────────────────────────────────────┤
│ REST (sync)          │ High     │ Low*     │ Low          │
│ gRPC (sync)          │ High     │ Very Low │ Low          │
│ Kafka (async)        │ Low      │ Higher   │ High         │
│ RabbitMQ (async)     │ Low      │ Medium   │ Medium-High  │
│ Event Notification   │ Very Low │ Variable │ High         │
│ Event Streaming      │ Very Low │ Variable │ Very High    │
└────────────────────────────────────────────────────────────┘
* Low latency per call, but cascading failures increase overall latency
```

---

## Diagram

```
When to Use Synchronous vs Asynchronous:

USE SYNCHRONOUS (REST/gRPC):
- Need immediate response (query data)
- Simple request-reply
- Read operations
- User-facing APIs that need instant feedback

Client → "GET /api/users/123" → User Service → JSON response

USE ASYNCHRONOUS (Kafka/Events):
- Fire-and-forget operations
- Multiple consumers need the same event
- Operations that take time
- Decoupling services
- Handling spikes/bursts

Order Service → "OrderCreated" → Kafka → Payment, Inventory, Notification

HYBRID (Most Common in Production):
Client
  │
  ↓ (sync - needs response)
API Gateway
  │
  ↓ (sync - needs response)
Order Service ──→ User Service (sync: validate user exists)
  │
  ↓ (async - fire and forget)
Kafka
  │
  ├──→ Payment Service (process payment)
  ├──→ Inventory Service (reserve stock)
  └──→ Notification Service (send email)
```

---

## Code

### REST Communication (WebClient):

```java
@Service
public class OrderService {

    private final WebClient userServiceClient;

    public OrderService(WebClient.Builder builder) {
        this.userServiceClient = builder
            .baseUrl("http://user-service")
            .build();
    }

    // Synchronous call to validate user exists
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Sync call — needed before creating order
        UserDto user = userServiceClient.get()
            .uri("/api/users/{id}", request.getUserId())
            .retrieve()
            .bodyToMono(UserDto.class)
            .block();  // Blocking for sync

        if (user == null) {
            throw new UserNotFoundException(request.getUserId());
        }

        Order order = orderRepository.save(buildOrder(request, user));
        
        // Async — publish event for downstream services
        kafkaTemplate.send("order-events", new OrderCreatedEvent(order));
        
        return OrderResponse.from(order);
    }
}
```

### gRPC Communication:

```protobuf
// user-service.proto
syntax = "proto3";

service UserService {
    rpc GetUser (GetUserRequest) returns (UserResponse);
    rpc ValidateUser (ValidateUserRequest) returns (ValidateUserResponse);
}

message GetUserRequest {
    string user_id = 1;
}

message UserResponse {
    string user_id = 1;
    string name = 2;
    string email = 3;
}
```

```java
// gRPC Client in Order Service
@Service
public class UserGrpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserResponse getUser(String userId) {
        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(userId)
            .build();
        return userStub.getUser(request);
    }
}
```

### Kafka Event-Driven Communication:

```java
// Publisher — Order Service
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(order.getId())
            .customerId(order.getCustomerId())
            .items(order.getItems())
            .totalAmount(order.getTotalAmount())
            .timestamp(Instant.now())
            .build();

        kafkaTemplate.send("order-events", order.getId().toString(), event);
    }
}
```

```java
// Consumer — Payment Service
@Service
public class PaymentEventConsumer {

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received order event: {}", event.getOrderId());
        
        PaymentRequest paymentRequest = PaymentRequest.builder()
            .orderId(event.getOrderId())
            .amount(event.getTotalAmount())
            .customerId(event.getCustomerId())
            .build();
        
        paymentService.processPayment(paymentRequest);
    }
}
```

```java
// Consumer — Inventory Service (same event, different consumer group)
@Service
public class InventoryEventConsumer {

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Reserving stock for order: {}", event.getOrderId());
        
        for (OrderItemDto item : event.getItems()) {
            inventoryService.reserveStock(item.getProductId(), item.getQuantity());
        }
    }
}
```

### Event Notification vs Event-Carried State Transfer:

```java
// Event Notification — minimal data, consumer fetches if needed
public class OrderCreatedNotification {
    private UUID orderId;
    private String eventType;  // "ORDER_CREATED"
    private Instant timestamp;
    // Consumer must call Order Service API to get full details
}

// Event-Carried State Transfer — full data in event
public class OrderCreatedEvent {
    private UUID orderId;
    private String customerId;
    private String customerName;
    private String customerEmail;
    private List<OrderItemDto> items;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Instant createdAt;
    // Consumer has everything it needs — no callback required
}
```

---

## Interview Questions

1. **When to use synchronous vs asynchronous communication?**
   - Sync: Need immediate response, read operations, user-facing queries. Async: Fire-and-forget, long-running operations, multiple consumers, decoupling. Most systems use both (hybrid).

2. **Why Kafka over RabbitMQ for microservices?**
   - Kafka: Event streaming (replay), high throughput, persistent, consumer groups, partition-based parallelism. RabbitMQ: Traditional queuing, routing flexibility, lower latency per message. Choose based on use case.

3. **What is Event-Carried State Transfer?**
   - Include all necessary data in the event so consumers don't need to callback the publisher. Reduces coupling and eliminates synchronous dependency. Trade-off: larger events, potential stale data.

4. **How to handle synchronous communication failures?**
   - Circuit breaker, retry with backoff, timeout, fallback. Better approach: minimize sync calls, use async + eventual consistency where possible.

5. **REST vs gRPC — when to use each?**
   - REST: External APIs, browser clients, simple CRUD. gRPC: Internal service-to-service, high performance needs, streaming, strongly typed contracts. gRPC is 2-10x faster than REST.

6. **What is Publish/Subscribe pattern?**
   - Publisher sends event to a topic. Multiple subscribers (consumer groups) each get a copy. One event triggers multiple independent actions. Decouples publisher from all consumers.

---

## Common Mistakes

1. **Synchronous chains** — A calls B calls C calls D → one failure breaks all
2. **Large event payloads** — Sending entire database records in events
3. **Not handling message ordering** — Kafka guarantees order within partition only
4. **Ignoring idempotency** — Consumer may receive same event twice
5. **Tight coupling via shared DTOs** — Each service should own its event schema
6. **No dead letter queue** — Failed messages lost forever

---

## Best Practices

1. **Default to async** — Use sync only when immediate response is required
2. **Event-first design** — Design events as first-class citizens
3. **Idempotent consumers** — Handle duplicate messages gracefully
4. **Schema evolution** — Use Avro/Protobuf with schema registry for event versioning
5. **Dead letter queue** — Capture failed messages for investigation and replay
6. **Correlation IDs** — Track requests across services for debugging
7. **Consumer groups** — Scale consumers independently per service
