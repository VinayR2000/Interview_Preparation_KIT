# 28. Microservices with Spring Boot

## Theory

Microservices architecture decomposes an application into small, independently deployable services, each owning its data and communicating over the network.

### Architectural Patterns:
- **Monolith**: Single deployable unit, shared database
- **Modular Monolith**: Monolith with clear module boundaries (stepping stone)
- **Microservices**: Independent services, separate databases, network communication

### Core Principles:
- **Single Responsibility**: Each service does one thing well
- **Database per Service**: No shared databases
- **Decentralized Governance**: Teams own their services end-to-end
- **Design for Failure**: Expect network failures, timeouts
- **Eventual Consistency**: Accept that data won't always be immediately consistent

### Communication Types:
- **Synchronous**: REST, gRPC (request-response, immediate)
- **Asynchronous**: Kafka, RabbitMQ (event-driven, decoupled)

---

## Internal Working

```
┌── Synchronous Communication ────────────────────┐
│                                                   │
│  Order Service → REST → Inventory Service         │
│  - Request/Response                               │
│  - Caller waits for response                     │
│  - Tight coupling (temporal)                     │
│  - Cascading failures possible                   │
└───────────────────────────────────────────────────┘

┌── Asynchronous Communication ───────────────────┐
│                                                   │
│  Order Service → Kafka Topic → Inventory Service │
│  - Fire and forget (or eventual response)        │
│  - Caller doesn't wait                          │
│  - Loose coupling                               │
│  - More resilient to failures                   │
└───────────────────────────────────────────────────┘
```

### Service Boundary Design:
```
Bounded Context (DDD):
  Order Service owns: Orders, OrderItems, OrderStatus
  Inventory Service owns: Products, Stock, Warehouses
  Payment Service owns: Transactions, PaymentMethods
  User Service owns: Users, Profiles, Addresses

Each service has its OWN database:
  Order Service → PostgreSQL (orders_db)
  Inventory Service → PostgreSQL (inventory_db)
  Payment Service → PostgreSQL (payments_db)
  User Service → PostgreSQL (users_db)
```

---

## Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                         CLIENT                                   │
└───────────────────────────┬────────────────────────────────────┘
                            │
                            ↓
┌────────────────────────────────────────────────────────────────┐
│                     API GATEWAY                                  │
│              (Spring Cloud Gateway)                              │
│   Routing, Auth, Rate Limiting, Load Balancing                  │
└───────┬────────────┬────────────┬──────────────────────────────┘
        │            │            │
        ↓            ↓            ↓
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Order Service│ │ User Service │ │ Payment Svc  │
│              │ │              │ │              │
│ REST API     │ │ REST API     │ │ REST API     │
│ Business     │ │ Business     │ │ Business     │
│ Logic        │ │ Logic        │ │ Logic        │
│              │ │              │ │              │
│ ┌──────────┐ │ │ ┌──────────┐ │ │ ┌──────────┐ │
│ │orders_db │ │ │ │users_db  │ │ │ │payments_db│ │
│ └──────────┘ │ │ └──────────┘ │ │ └──────────┘ │
└──────┬───────┘ └──────────────┘ └──────────────┘
       │
       │ Events
       ↓
┌────────────────────────────────────────────────────────────────┐
│                    KAFKA (Event Bus)                             │
│                                                                  │
│  Topics: order-events, payment-events, inventory-events          │
└────────────────────────────────────────────────────────────────┘
       ↑                         ↑
       │                         │
┌──────────────┐          ┌──────────────┐
│Inventory Svc │          │Notification  │
│              │          │Service       │
│ ┌──────────┐ │          │ (email, SMS) │
│ │invent_db │ │          └──────────────┘
│ └──────────┘ │
└──────────────┘
```

---

## Code

### Service Structure:

```
order-service/
├── src/main/java/com/example/order/
│   ├── OrderServiceApplication.java
│   ├── controller/OrderController.java
│   ├── service/OrderService.java
│   ├── repository/OrderRepository.java
│   ├── model/Order.java
│   ├── dto/OrderDTO.java
│   ├── event/OrderCreatedEvent.java
│   ├── client/InventoryClient.java    ← calls other service
│   └── config/
├── src/main/resources/
│   └── application.yml
└── pom.xml
```

### Inter-Service Communication (REST):

```java
// Using RestClient (Spring Boot 3.2+)
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Value("${services.inventory.url}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    public StockResponse checkStock(String productId, int quantity) {
        return restClient.get()
            .uri("/api/inventory/{productId}/stock?quantity={qty}", productId, quantity)
            .retrieve()
            .body(StockResponse.class);
    }

    public void reserveStock(ReserveStockRequest request) {
        restClient.post()
            .uri("/api/inventory/reserve")
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }
}
```

### Event-Driven Communication:

```java
// Order Service publishes event
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = orderRepository.save(buildOrder(request));
        
        // Publish event instead of calling inventory service directly
        kafkaTemplate.send("order-events", 
            order.getId().toString(),
            new OrderCreatedEvent(order.getId(), order.getItems()));
        
        return order;
    }
}

// Inventory Service consumes event
@Component
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        inventoryService.reserveStock(event.getOrderId(), event.getItems());
    }
}
```

### Saga Pattern (Choreography):

```java
// Each service listens for events and publishes next event

// Order Service
@KafkaListener(topics = "payment-events")
public void handlePaymentResult(PaymentResultEvent event) {
    if (event.getStatus() == PaymentStatus.SUCCESS) {
        orderRepository.updateStatus(event.getOrderId(), OrderStatus.CONFIRMED);
        kafkaTemplate.send("order-events", new OrderConfirmedEvent(event.getOrderId()));
    } else {
        orderRepository.updateStatus(event.getOrderId(), OrderStatus.CANCELLED);
        kafkaTemplate.send("order-events", new OrderCancelledEvent(event.getOrderId()));
    }
}

// Inventory Service
@KafkaListener(topics = "order-events")
public void handleOrderConfirmed(OrderConfirmedEvent event) {
    inventoryService.commitReservation(event.getOrderId());
}

@KafkaListener(topics = "order-events")
public void handleOrderCancelled(OrderCancelledEvent event) {
    inventoryService.releaseReservation(event.getOrderId());  // Compensating action
}
```

### API Gateway Configuration:

```yaml
# API Gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/api/orders/**
          filters:
            - RewritePath=/api/orders/(?<remaining>.*), /api/orders/${remaining}
        - id: user-service
          uri: lb://USER-SERVICE
          predicates:
            - Path=/api/users/**
```

---

## Dry Run

### Order Creation Flow (Synchronous + Async):

```
1. Client: POST /api/orders {customerId: 1, items: [{productId: "P1", qty: 2}]}
   → API Gateway → routes to Order Service

2. Order Service:
   a. Validate request
   b. Call Inventory Service (REST): GET /api/inventory/P1/stock?quantity=2
      → Response: {available: true}
   c. Save order (status: PENDING)
   d. Publish event: OrderCreatedEvent{orderId: 42, items: [...]}
   e. Return 201 Created {orderId: 42, status: "PENDING"}

3. Payment Service (consumes OrderCreatedEvent):
   → Process payment for order 42
   → Publish: PaymentSuccessEvent{orderId: 42, transactionId: "txn-123"}

4. Order Service (consumes PaymentSuccessEvent):
   → Update order 42 status: PENDING → CONFIRMED
   → Publish: OrderConfirmedEvent{orderId: 42}

5. Inventory Service (consumes OrderConfirmedEvent):
   → Commit stock reservation for order 42
   → Reduce available stock

6. Notification Service (consumes OrderConfirmedEvent):
   → Send confirmation email to customer
```

---

## Complexity

| Aspect | Monolith | Microservices |
|--------|----------|---------------|
| Deployment | Simple (1 artifact) | Complex (N services) |
| Development | Coupled | Independent teams |
| Data consistency | ACID transactions | Eventual consistency |
| Communication | Method calls | Network calls |
| Debugging | Easy (single process) | Distributed tracing needed |
| Scaling | Scale everything | Scale individual services |
| Infrastructure | Minimal | K8s, service mesh, etc. |

---

## Real Project Usage

### E-commerce Microservices:
```
Services:
- User Service (auth, profiles)
- Product Service (catalog, search)
- Order Service (order lifecycle)
- Payment Service (payment processing)
- Inventory Service (stock management)
- Notification Service (email, SMS, push)
- Shipping Service (delivery tracking)

Infrastructure:
- API Gateway (Spring Cloud Gateway)
- Service Discovery (Eureka/Consul)
- Config Server (Spring Cloud Config)
- Message Broker (Kafka)
- Distributed Cache (Redis)
- Monitoring (Prometheus + Grafana)
- Tracing (Jaeger/Zipkin)
```

---

## Interview Questions

1. **When to use microservices vs monolith?**
   - Microservices: Large team, independent scaling needs, different tech stacks, frequent deployments. Monolith: Small team, simple domain, early stage, faster initial development.

2. **How to handle distributed transactions?**
   - Saga pattern (choreography or orchestration). Avoid 2PC. Accept eventual consistency. Implement compensating transactions.

3. **How do services discover each other?**
   - Service registry (Eureka/Consul), DNS-based (Kubernetes Services), API Gateway routing.

4. **Database per service — how to join data?**
   - API composition (query multiple services), CQRS (read-optimized view), event-driven data synchronization.

5. **How to handle service-to-service authentication?**
   - JWT propagation, mTLS, API keys, service mesh (Istio). Internal services trust each other within mesh.

---

## Follow-up Questions

1. How to decompose a monolith into microservices?
   - Identify bounded contexts (DDD). Start with the least-coupled module. Strangler Fig pattern: Route traffic to new service gradually. Keep shared DB temporarily, extract later. Verify with integration tests.

2. How to implement distributed tracing across services?
   - Use Micrometer Tracing (Spring Boot 3) or Spring Cloud Sleuth (older). Trace ID propagated via HTTP headers. Each service logs trace ID. Visualize in Zipkin/Jaeger to see full request flow across services.

3. What's the Strangler Fig pattern?
   - Gradually replace monolith functionality. New features go to microservice. Route requests based on path/feature. Old code "strangled" as traffic moves to new services. Monolith shrinks over time until eliminated.

4. How to test microservices in isolation?
   - Contract tests (Pact/Spring Cloud Contract) verify API agreements. WireMock stubs external services. Testcontainers for infrastructure. Each service has its own test suite independent of others.

5. How to handle versioning of APIs between services?
   - Use semantic versioning for breaking changes. Consumer-driven contracts detect incompatibilities early. Support old and new versions simultaneously during migration. Deprecation headers for sunset period.

---

## Common Mistakes

1. **Too many services too early** - Start monolith, extract when needed
2. **Shared databases** - Defeats the purpose, creates coupling
3. **Synchronous everything** - Cascading failures, tight coupling
4. **Ignoring data consistency** - Distributed transactions are hard
5. **No API Gateway** - Direct client-to-service communication is fragile
6. **Not investing in observability** - Can't debug distributed systems without tracing/logging
7. **Nano-services** - Too granular = too much network overhead

---

## Best Practices

1. **Start with a modular monolith** - Extract services when boundaries are clear
2. **Database per service** - Each service owns its data
3. **Prefer async communication** - Events over synchronous REST where possible
4. **Implement circuit breakers** - Prevent cascading failures
5. **Use API Gateway** - Single entry point, cross-cutting concerns
6. **Invest in observability** - Distributed tracing, centralized logging, metrics
7. **Design for failure** - Retry, timeout, fallback for every network call
8. **Automate everything** - CI/CD per service, infrastructure as code

---

## Production Considerations

- **Service mesh**: Istio/Linkerd for mTLS, traffic management, observability
- **Container orchestration**: Kubernetes for deployment, scaling, self-healing
- **Data consistency**: Accept eventual consistency, implement Saga pattern
- **Testing strategy**: Contract tests between services, E2E tests for critical flows
- **Deployment**: Blue-green or canary deployments per service
- **Team structure**: Conway's Law — align team boundaries with service boundaries
- **Cost**: More infrastructure, more operational complexity

---

## Related Topics

- Spring Cloud (service discovery, config, gateway)
- Kafka (async communication)
- Resilience Patterns (circuit breaker, retry)
- Docker + Kubernetes (deployment)
- Distributed Tracing (observability)
- API Gateway (routing, auth)
