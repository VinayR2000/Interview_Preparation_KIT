# Microservices Design Patterns - Complete Interview Guide

---

## Table of Contents

1. [Decomposition Patterns](#1-decomposition-patterns)
2. [Communication Patterns](#2-communication-patterns)
3. [Data Management Patterns](#3-data-management-patterns)
4. [Service Discovery Patterns](#4-service-discovery-patterns)
5. [Reliability & Resilience Patterns](#5-reliability--resilience-patterns)
6. [Security Patterns](#6-security-patterns)
7. [Deployment Patterns](#7-deployment-patterns)
8. [Observability Patterns](#8-observability-patterns)
9. [Structural Patterns](#9-structural-patterns)

---

## 1. Decomposition Patterns

### 1.1 Decompose by Business Capability

**What it is:** Split services based on business functions (e.g., Orders, Payments, Inventory).

**When to use:** When you want services aligned with business domains.

**Example:**

```
E-Commerce Application
├── Order Service         (handles order lifecycle)
├── Payment Service       (handles payments, refunds)
├── Inventory Service     (stock management)
├── Shipping Service      (delivery tracking)
└── Notification Service  (emails, SMS, push)
```

**Interview Tip:** "We decompose by business capability so each team owns a full vertical slice of the business, enabling independent development and deployment."

---

### 1.2 Decompose by Subdomain (Domain-Driven Design)

**What it is:** Use DDD bounded contexts to define service boundaries.

**When to use:** Complex domains where business logic is the primary driver.

**Key Concepts:**
- **Core Domain** – Main business differentiator (highest priority)
- **Supporting Subdomain** – Supports core but not a differentiator
- **Generic Subdomain** – Commodity functions (auth, notifications)

**Example:**
```
Online Banking System
├── Core Domain:       Loan Processing Service
├── Supporting:        Credit Scoring Service
└── Generic:           Authentication Service, Email Service
```

**Diagram:**
```
┌─────────────────────────────────────────────────┐
│                 Bounded Contexts                  │
│                                                   │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │
│  │  Order   │  │ Customer │  │   Inventory   │   │
│  │ Context  │  │ Context  │  │   Context     │   │
│  │          │  │          │  │               │   │
│  │ - Order  │  │ - Buyer  │  │ - StockItem   │   │
│  │ - Item   │  │ - Address│  │ - Warehouse   │   │
│  └──────────┘  └──────────┘  └──────────────┘   │
│       │              │               │            │
│       └──────────────┼───────────────┘            │
│            Anti-Corruption Layers                 │
└─────────────────────────────────────────────────┘
```

**Interview Tip:** "Each bounded context becomes a microservice with its own ubiquitous language. The same concept (e.g., 'Product') can mean different things in different contexts."

---

### 1.3 Strangler Fig Pattern

**What it is:** Incrementally migrate a monolith to microservices by gradually replacing specific functionality.

**When to use:** Migrating legacy monolith systems without big-bang rewrites.

**How it works:**
1. Identify a module in the monolith
2. Build a new microservice replicating that functionality
3. Route traffic from old module to new service
4. Remove old code once new service is stable

**Diagram:**
```
Phase 1:                    Phase 2:                    Phase 3:
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│   Monolith   │           │   Monolith   │           │   Monolith   │
│              │           │   (smaller)  │           │   (minimal)  │
│ ┌──┐┌──┐┌──┐│           │ ┌──┐┌──┐    │           │ ┌──┐         │
│ │A ││B ││C ││           │ │A ││B │    │           │ │A │         │
│ └──┘└──┘└──┘│           │ └──┘└──┘    │           │ └──┘         │
└──────────────┘           └──────────────┘           └──────────────┘
                                    │                       │    │
                            ┌───────┴───┐             ┌────┴┐ ┌─┴──┐
                            │ Service C │             │Svc B│ │Svc C│
                            └───────────┘             └─────┘ └────┘
```

**Interview Tip:** "The Strangler Fig pattern minimizes risk by allowing us to migrate piece by piece, validating each service before proceeding."

---

## 2. Communication Patterns

### 2.1 API Gateway Pattern

**What it is:** A single entry point for all client requests that routes to appropriate microservices.

**Responsibilities:**
- Request routing
- Authentication/Authorization
- Rate limiting
- Load balancing
- Response aggregation
- Protocol translation

**Diagram:**
```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Mobile  │  │   Web    │  │  3rd     │
│  Client  │  │  Client  │  │  Party   │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
            ┌───────▼────────┐
            │   API Gateway  │
            │                │
            │ • Auth         │
            │ • Rate Limit   │
            │ • Routing      │
            │ • Aggregation  │
            └───────┬────────┘
                    │
     ┌──────────────┼──────────────┐
     │              │              │
┌────▼─────┐ ┌─────▼────┐ ┌──────▼─────┐
│  User    │ │  Order   │ │  Product   │
│ Service  │ │ Service  │ │  Service   │
└──────────┘ └──────────┘ └────────────┘
```

**Tools:** Kong, AWS API Gateway, Netflix Zuul, Spring Cloud Gateway, Nginx

**Example (Spring Cloud Gateway):**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/api/orders/**
        - id: user-service
          uri: lb://USER-SERVICE
          predicates:
            - Path=/api/users/**
```

**Interview Tip:** "API Gateway acts as a reverse proxy, providing a unified interface to clients while hiding the complexity of the microservices topology."

---

### 2.2 Backend for Frontend (BFF) Pattern

**What it is:** Create separate backend services tailored for each frontend type.

**Why:** Different clients (mobile, web, IoT) have different data needs and bandwidth constraints.

**Diagram:**
```
┌──────────┐     ┌──────────┐     ┌──────────┐
│  Mobile  │     │   Web    │     │   IoT    │
│   App    │     │   App    │     │  Device  │
└────┬─────┘     └────┬─────┘     └────┬─────┘
     │                 │                 │
┌────▼─────┐     ┌────▼─────┐     ┌────▼─────┐
│ Mobile   │     │  Web     │     │  IoT     │
│   BFF    │     │   BFF    │     │   BFF    │
└────┬─────┘     └────┬─────┘     └────┬─────┘
     │                 │                 │
     └─────────────────┼─────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   ┌────▼───┐   ┌─────▼────┐  ┌─────▼────┐
   │ User   │   │  Order   │  │ Product  │
   │Service │   │ Service  │  │ Service  │
   └────────┘   └──────────┘  └──────────┘
```

**Interview Tip:** "BFF prevents a one-size-fits-all API. Mobile gets lightweight payloads, web gets richer data, and IoT gets minimal responses."

---

### 2.3 Synchronous Communication (Request/Response)

**What it is:** Direct service-to-service calls using REST or gRPC.

**REST Example:**
```java
// Order Service calls Inventory Service
@Service
public class OrderService {
    @Autowired
    private RestTemplate restTemplate;

    public Order placeOrder(OrderRequest request) {
        // Synchronous call to check stock
        InventoryResponse inventory = restTemplate.getForObject(
            "http://inventory-service/api/inventory/{productId}",
            InventoryResponse.class,
            request.getProductId()
        );

        if (inventory.getQuantity() >= request.getQuantity()) {
            return createOrder(request);
        }
        throw new InsufficientStockException();
    }
}
```

**gRPC Example:**
```protobuf
// inventory.proto
service InventoryService {
    rpc CheckStock (StockRequest) returns (StockResponse);
    rpc ReserveStock (ReserveRequest) returns (ReserveResponse);
}

message StockRequest {
    string product_id = 1;
    int32 quantity = 2;
}
```

**REST vs gRPC comparison:**

| Feature    | REST          | gRPC              |
|------------|---------------|-------------------|
| Protocol   | HTTP/1.1      | HTTP/2            |
| Format     | JSON (text)   | Protobuf (binary) |
| Speed      | Slower        | ~10x faster       |
| Streaming  | Limited       | Bidirectional     |
| Browser    | Native        | Needs proxy       |

---

### 2.4 Asynchronous Communication (Event-Driven)

**What it is:** Services communicate via messages/events without waiting for responses.

**Diagram:**
```
┌────────────┐         ┌─────────────────┐         ┌────────────┐
│   Order    │ publish  │  Message Broker │ consume  │ Inventory  │
│  Service   │────────▶│  (Kafka/RabbitMQ)│────────▶│  Service   │
└────────────┘         └─────────────────┘         └────────────┘
                              │
                              │ consume
                              ▼
                       ┌────────────┐
                       │Notification│
                       │  Service   │
                       └────────────┘
```

**Example (Spring + Kafka):**
```java
// Producer - Order Service
@Service
public class OrderEventPublisher {
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderEvent event = new OrderEvent("ORDER_CREATED", order);
        kafkaTemplate.send("order-events", order.getId(), event);
    }
}

// Consumer - Inventory Service
@Service
public class InventoryEventConsumer {
    @KafkaListener(topics = "order-events", groupId = "inventory-group")
    public void handleOrderEvent(OrderEvent event) {
        if ("ORDER_CREATED".equals(event.getType())) {
            reserveStock(event.getOrder());
        }
    }
}
```

**Interview Tip:** "Async communication decouples services temporally. The producer doesn't need to know who consumes the event, enabling better scalability and resilience."

---

### 2.5 Saga Pattern

**What it is:** Manages distributed transactions across multiple services using a sequence of local transactions with compensating actions.

**Two Types:**

#### Choreography-based Saga
Each service listens for events and decides what to do next.

```
┌──────────┐    event    ┌──────────┐    event    ┌──────────┐
│  Order   │───────────▶│ Payment  │───────────▶│ Shipping │
│ Service  │            │ Service  │            │ Service  │
│          │◀───────────│          │◀───────────│          │
└──────────┘  compensate └──────────┘  compensate └──────────┘
```

#### Orchestration-based Saga
A central orchestrator coordinates the saga steps.

```
                    ┌──────────────┐
                    │    Saga      │
                    │ Orchestrator │
                    └──────┬───────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
            ▼              ▼              ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │  Order   │  │ Payment  │  │ Shipping │
      │ Service  │  │ Service  │  │ Service  │
      └──────────┘  └──────────┘  └──────────┘
```

**Example (Order Saga - Orchestration):**
```java
@Service
public class OrderSagaOrchestrator {

    public void createOrderSaga(OrderRequest request) {
        try {
            // Step 1: Create Order
            Order order = orderService.createOrder(request);

            // Step 2: Reserve Inventory
            inventoryService.reserve(order.getItems());

            // Step 3: Process Payment
            paymentService.charge(order.getTotalAmount());

            // Step 4: Arrange Shipping
            shippingService.schedule(order);

            orderService.confirmOrder(order.getId());
        } catch (PaymentFailedException e) {
            // Compensating transactions
            inventoryService.releaseReservation(order.getItems());
            orderService.cancelOrder(order.getId());
        } catch (ShippingException e) {
            paymentService.refund(order.getTotalAmount());
            inventoryService.releaseReservation(order.getItems());
            orderService.cancelOrder(order.getId());
        }
    }
}
```

**Choreography vs Orchestration:**

| Aspect        | Choreography         | Orchestration         |
|---------------|----------------------|-----------------------|
| Coupling      | Loose                | Tighter (to orchestr.)|
| Complexity    | Grows with services  | Centralized           |
| Debugging     | Harder               | Easier                |
| Single point  | No                   | Yes (orchestrator)    |
| Best for      | Simple sagas         | Complex workflows     |

**Interview Tip:** "Saga replaces ACID transactions in distributed systems. Each step has a compensating action to undo its effect if a later step fails."

---

## 3. Data Management Patterns

### 3.1 Database per Service

**What it is:** Each microservice owns its private database, not shared with others.

**Diagram:**
```
┌──────────┐     ┌──────────┐     ┌──────────┐
│  Order   │     │  User    │     │ Product  │
│ Service  │     │ Service  │     │ Service  │
└────┬─────┘     └────┬─────┘     └────┬─────┘
     │                 │                 │
┌────▼─────┐     ┌────▼─────┐     ┌────▼─────┐
│  Order   │     │  User    │     │ Product  │
│   DB     │     │   DB     │     │   DB     │
│(Postgres)│     │ (MySQL)  │     │ (MongoDB)│
└──────────┘     └──────────┘     └──────────┘
```

**Benefits:**
- Services are loosely coupled
- Each service can use the best DB for its needs (polyglot persistence)
- Independent scaling
- No shared schema conflicts

**Challenges:**
- Cross-service queries are complex
- Data consistency is eventual (not immediate)
- Distributed transactions needed

**Interview Tip:** "Database per service enforces loose coupling. If two services share a DB, changes to the schema can break both, defeating the purpose of microservices."

---

### 3.2 CQRS (Command Query Responsibility Segregation)

**What it is:** Separate read (Query) and write (Command) models for a service.

**Diagram:**
```
                    ┌─────────────┐
                    │   Client    │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │                         │
        ┌─────▼─────┐            ┌─────▼─────┐
        │  Command   │            │   Query   │
        │   Side     │            │   Side    │
        │            │            │           │
        │ • Create   │   sync     │ • GetAll  │
        │ • Update   │──────────▶│ • GetById │
        │ • Delete   │  (event)   │ • Search  │
        └─────┬──────┘            └─────┬─────┘
              │                         │
        ┌─────▼──────┐           ┌─────▼──────┐
        │  Write DB  │           │  Read DB   │
        │(Normalized)│           │(Denormalized│
        │            │           │  Optimized) │
        └────────────┘           └────────────┘
```

**Example:**
```java
// Command Model
@Service
public class OrderCommandService {
    public void createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        orderWriteRepo.save(order);
        eventPublisher.publish(new OrderCreatedEvent(order));
    }
}

// Query Model
@Service
public class OrderQueryService {
    public OrderView getOrderSummary(String orderId) {
        // Reads from denormalized read-optimized store
        return orderReadRepo.findById(orderId);
    }

    public List<OrderView> searchOrders(SearchCriteria criteria) {
        return orderReadRepo.search(criteria); // Fast reads
    }
}
```

**Interview Tip:** "CQRS lets us optimize reads and writes independently. The write model ensures data integrity, while the read model is denormalized for fast queries."

---

### 3.3 Event Sourcing

**What it is:** Store state as a sequence of events rather than current state only.

**Diagram:**
```
Traditional:  ┌──────────────────────┐
              │ Account: $500        │  (only current state)
              └──────────────────────┘

Event Sourcing:
┌────────────────────────────────────────────────────────┐
│ Event Store                                             │
│                                                         │
│ 1. AccountCreated  { balance: 0 }          t=0         │
│ 2. MoneyDeposited  { amount: 1000 }        t=1         │
│ 3. MoneyWithdrawn  { amount: 300 }         t=2         │
│ 4. MoneyWithdrawn  { amount: 200 }         t=3         │
│                                                         │
│ Current State = replay all events → balance: $500      │
└────────────────────────────────────────────────────────┘
```

**Example:**
```java
// Event Store
public class AccountEventStore {
    private List<DomainEvent> events = new ArrayList<>();

    public void append(DomainEvent event) {
        events.add(event);
    }

    public Account reconstruct(String accountId) {
        Account account = new Account();
        events.stream()
            .filter(e -> e.getAccountId().equals(accountId))
            .forEach(account::apply);
        return account;
    }
}

// Domain Events
public record MoneyDeposited(String accountId, BigDecimal amount, Instant timestamp) 
    implements DomainEvent {}

public record MoneyWithdrawn(String accountId, BigDecimal amount, Instant timestamp) 
    implements DomainEvent {}
```

**Benefits:**
- Complete audit trail
- Can replay events to debug or rebuild state
- Supports temporal queries ("What was the balance on March 1?")
- Natural fit with CQRS

**Interview Tip:** "Event Sourcing gives us a full history. We can rebuild any past state, which is invaluable for audit, debugging, and analytics."

---

### 3.4 Transactional Outbox Pattern

**What it is:** Ensures reliable event publishing by writing events to a DB table (outbox) in the same transaction as the business data.

**Problem it solves:** Dual-write problem — updating DB and publishing event can leave them inconsistent if one fails.

**Diagram:**
```
┌─────────────────────────────────────────────────┐
│              Order Service                        │
│                                                   │
│  ┌─────────────────────────────────────────┐     │
│  │         Single DB Transaction            │     │
│  │                                          │     │
│  │  1. INSERT INTO orders (...)             │     │
│  │  2. INSERT INTO outbox_events (...)      │     │
│  │                                          │     │
│  └─────────────────────────────────────────┘     │
│                                                   │
│  ┌──────────────────┐                            │
│  │  Outbox Poller   │─── reads outbox table ──┐  │
│  │  (CDC/Polling)   │                         │  │
│  └──────────────────┘                         │  │
└───────────────────────────────────────────────┼──┘
                                                │
                                    ┌───────────▼────┐
                                    │ Message Broker  │
                                    │ (Kafka/RabbitMQ)│
                                    └────────────────┘
```

**Example:**
```sql
-- Same transaction
BEGIN;
  INSERT INTO orders (id, customer_id, total) VALUES ('ord-1', 'cust-1', 99.99);
  INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload)
    VALUES (uuid(), 'Order', 'ord-1', 'OrderCreated', '{"orderId":"ord-1","total":99.99}');
COMMIT;
```

**Tools:** Debezium (CDC), custom pollers

**Interview Tip:** "The Outbox pattern guarantees at-least-once delivery. We write the event and business data in one transaction, then a separate process publishes it."

---

## 4. Service Discovery Patterns

### 4.1 Client-Side Discovery

**What it is:** The client queries a service registry and picks an instance to call.

**Diagram:**
```
┌──────────┐  1. Query    ┌──────────────────┐
│  Client  │─────────────▶│ Service Registry │
│ (Order)  │◀─────────────│ (Eureka/Consul)  │
└────┬─────┘  2. Returns  └──────────────────┘
     │           list of         ▲
     │           instances       │ 3. Register
     │                           │
     │  4. Direct call    ┌──────┴─────┐
     └───────────────────▶│  Payment   │
                          │  Service   │
                          │ (instance) │
                          └────────────┘
```

**Example (Netflix Eureka + Ribbon):**
```java
@EnableDiscoveryClient
@SpringBootApplication
public class OrderServiceApp { }

@Service
public class PaymentClient {
    @Autowired
    @LoadBalanced
    private RestTemplate restTemplate;

    public PaymentResponse charge(PaymentRequest req) {
        // "payment-service" resolved via Eureka
        return restTemplate.postForObject(
            "http://payment-service/api/payments", req, PaymentResponse.class);
    }
}
```

---

### 4.2 Server-Side Discovery

**What it is:** A load balancer/router handles discovery. Client calls the router, which finds and routes to the right instance.

**Diagram:**
```
┌──────────┐           ┌──────────────┐          ┌────────────┐
│  Client  │──────────▶│ Load Balancer│─────────▶│  Service   │
│          │           │  (Nginx/ALB) │          │ Instance 1 │
└──────────┘           └──────┬───────┘          └────────────┘
                              │
                              │                  ┌────────────┐
                              └─────────────────▶│  Service   │
                                                 │ Instance 2 │
                                                 └────────────┘
```

**Tools:** AWS ALB/NLB, Kubernetes Service, Nginx, HAProxy

**Client-Side vs Server-Side:**

| Aspect        | Client-Side          | Server-Side          |
|---------------|----------------------|----------------------|
| Logic         | In the client        | In load balancer     |
| Complexity    | Client more complex  | Client simpler       |
| Language      | Language-dependent   | Language-agnostic    |
| Example       | Eureka + Ribbon      | Kubernetes Services  |

---

## 5. Reliability & Resilience Patterns

### 5.1 Circuit Breaker Pattern

**What it is:** Prevents cascading failures by stopping calls to a failing service after a threshold is reached.

**States:**
```
                    ┌─────────────┐
         success   │             │  failure count
     ┌────────────▶│   CLOSED    │────────────┐
     │             │ (normal)    │            │
     │             └─────────────┘            │
     │                                        ▼
┌────┴────────┐                      ┌───────────────┐
│  HALF-OPEN  │◀─────────────────────│     OPEN      │
│ (testing)   │     timeout expires  │ (blocking)    │
└─────┬───────┘                      └───────────────┘
      │                                       ▲
      │  failure                              │
      └───────────────────────────────────────┘
```

**How it works:**
1. **CLOSED** → Normal operation. Counts failures.
2. **OPEN** → When failures exceed threshold, stops all calls. Returns fallback immediately.
3. **HALF-OPEN** → After timeout, allows one test request. If successful → CLOSED. If fails → OPEN.

**Example (Resilience4j):**
```java
@Service
public class PaymentService {

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        return paymentClient.charge(request);
    }

    public PaymentResponse paymentFallback(PaymentRequest request, Exception ex) {
        // Queue for retry or return cached response
        return PaymentResponse.pending("Payment queued for processing");
    }
}

// Configuration
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
```

**Interview Tip:** "Circuit Breaker fails fast instead of waiting for timeouts, protecting both the caller and the failing service from being overwhelmed."

---

### 5.2 Retry Pattern

**What it is:** Automatically retry failed requests with backoff strategies.

**Strategies:**
```
Fixed Delay:        ─── 2s ─── 2s ─── 2s ───
Exponential:        ─ 1s ── 2s ──── 4s ──────── 8s ─
Exponential+Jitter: ─ 0.8s ── 2.3s ──── 3.7s ──────── 9.1s ─
```

**Example (Resilience4j):**
```java
@Retry(name = "inventoryService", fallbackMethod = "inventoryFallback")
public InventoryResponse checkStock(String productId) {
    return inventoryClient.getStock(productId);
}

// Config
resilience4j:
  retry:
    instances:
      inventoryService:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
```

**Interview Tip:** "Always use exponential backoff with jitter to prevent thundering herd problems when many clients retry simultaneously."

---

### 5.3 Bulkhead Pattern

**What it is:** Isolates resources so a failure in one area doesn't take down the entire system.

**Analogy:** Ship bulkheads — if one compartment floods, others stay dry.

**Diagram:**
```
┌─────────────────────────────────────────────┐
│              Order Service                    │
│                                              │
│  ┌───────────────┐   ┌───────────────┐      │
│  │  Thread Pool  │   │  Thread Pool  │      │
│  │   Payment     │   │   Inventory   │      │
│  │   (10 threads)│   │  (15 threads) │      │
│  │               │   │               │      │
│  │ ███░░░░░░░    │   │ ██████████░░░░│      │
│  └───────┬───────┘   └───────┬───────┘      │
│          │                    │               │
└──────────┼────────────────────┼──────────────┘
           │                    │
     ┌─────▼──────┐     ┌──────▼─────┐
     │  Payment   │     │ Inventory  │
     │  Service   │     │  Service   │
     │  (DOWN!)   │     │  (Healthy) │
     └────────────┘     └────────────┘

Payment pool exhausted → Inventory still works!
```

**Example:**
```java
@Bulkhead(name = "paymentService", type = Bulkhead.Type.THREADPOOL)
public CompletableFuture<PaymentResponse> processPayment(PaymentRequest req) {
    return CompletableFuture.supplyAsync(() -> paymentClient.charge(req));
}

// Config
resilience4j:
  bulkhead:
    instances:
      paymentService:
        maxConcurrentCalls: 10
      inventoryService:
        maxConcurrentCalls: 25
```

---

### 5.4 Rate Limiting / Throttling

**What it is:** Controls how many requests a service accepts in a time window.

**Algorithms:**
```
Token Bucket:
┌───────────────────────────────┐
│ Bucket (capacity: 10)         │
│ ████████░░                    │  ← tokens consumed per request
│ Refill: 5 tokens/second       │
└───────────────────────────────┘

Sliding Window:
|─── Window (1 min) ───|
  req req req req req    → 5 requests → allowed (limit: 10)
```

**Example (Spring + Bucket4j):**
```java
@RestController
public class ApiController {

    private final Bucket bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
        .build();

    @GetMapping("/api/resource")
    public ResponseEntity<?> getResource() {
        if (bucket.tryConsume(1)) {
            return ResponseEntity.ok(service.getData());
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body("Rate limit exceeded");
    }
}
```

---

### 5.5 Timeout Pattern

**What it is:** Set time limits on service calls to prevent indefinite waiting.

**Example:**
```java
// RestTemplate with timeout
@Bean
public RestTemplate restTemplate() {
    HttpComponentsClientHttpRequestFactory factory = 
        new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(3000);   // 3s to connect
    factory.setReadTimeout(5000);      // 5s to read
    return new RestTemplate(factory);
}

// Resilience4j TimeLimiter
@TimeLimiter(name = "paymentService")
public CompletableFuture<PaymentResponse> charge(PaymentRequest req) {
    return CompletableFuture.supplyAsync(() -> paymentClient.charge(req));
}

// Config
resilience4j:
  timelimiter:
    instances:
      paymentService:
        timeoutDuration: 3s
        cancelRunningFuture: true
```

**Interview Tip:** "Always set timeouts. A missing timeout is a latency bomb waiting to happen — one slow downstream service can consume all your threads."

---

### 5.6 Fallback Pattern

**What it is:** Provide alternative responses when primary service fails.

**Types:**
- **Cache fallback** — Return last known good data
- **Default fallback** — Return sensible defaults
- **Graceful degradation** — Return partial results

**Example:**
```java
@Service
public class ProductService {

    @CircuitBreaker(name = "recommendations", fallbackMethod = "fallbackRecommendations")
    public List<Product> getRecommendations(String userId) {
        return recommendationService.getPersonalized(userId);
    }

    // Fallback: return popular products instead
    private List<Product> fallbackRecommendations(String userId, Exception ex) {
        return cacheService.getPopularProducts();  // cached popular items
    }
}
```

---

## 6. Security Patterns

### 6.1 Token-Based Authentication (JWT)

**What it is:** Stateless authentication using JSON Web Tokens.

**Flow:**
```
┌──────────┐                    ┌──────────┐                 ┌──────────┐
│  Client  │                    │   Auth   │                 │  Service │
│          │                    │ Service  │                 │          │
└────┬─────┘                    └────┬─────┘                 └────┬─────┘
     │  1. Login (credentials)       │                            │
     │──────────────────────────────▶│                            │
     │                               │                            │
     │  2. Return JWT token          │                            │
     │◀──────────────────────────────│                            │
     │                               │                            │
     │  3. Request + JWT in header   │                            │
     │───────────────────────────────────────────────────────────▶│
     │                               │                            │
     │                               │    4. Validate JWT         │
     │                               │    (no auth service call!) │
     │                               │                            │
     │  5. Response                  │                            │
     │◀──────────────────────────────────────────────────────────│
```

**JWT Structure:**
```
Header.Payload.Signature

{                           {                        HMACSHA256(
  "alg": "HS256",            "sub": "user123",        base64(header) + "." +
  "typ": "JWT"               "role": "ADMIN",         base64(payload),
}                             "exp": 1700000000        secret
                            }                        )
```

---

### 6.2 OAuth 2.0 / OpenID Connect

**What it is:** Industry-standard protocol for authorization delegation.

**Flow (Authorization Code):**
```
┌──────┐    ┌──────┐    ┌─────────────┐    ┌───────────┐
│ User │    │Client│    │Auth Server  │    │ Resource  │
│      │    │ App  │    │(Keycloak)   │    │  Server   │
└──┬───┘    └──┬───┘    └──────┬──────┘    └─────┬─────┘
   │           │               │                  │
   │  1. Click│Login           │                  │
   │──────────▶│               │                  │
   │           │ 2. Redirect   │                  │
   │           │──────────────▶│                  │
   │  3. Login│prompt         │                  │
   │◀──────────────────────────│                  │
   │  4. Consent + credentials │                  │
   │──────────────────────────▶│                  │
   │           │ 5. Auth code  │                  │
   │           │◀──────────────│                  │
   │           │ 6. Exchange   │                  │
   │           │   code→token  │                  │
   │           │──────────────▶│                  │
   │           │ 7. Access+    │                  │
   │           │   Refresh token                  │
   │           │◀──────────────│                  │
   │           │ 8. API call + token              │
   │           │─────────────────────────────────▶│
   │           │ 9. Response   │                  │
   │           │◀─────────────────────────────────│
```

---

### 6.3 Service Mesh Security (mTLS)

**What it is:** Mutual TLS between services for zero-trust networking.

**Diagram:**
```
┌─────────────────────────────────────────────────┐
│                Service Mesh (Istio)               │
│                                                   │
│  ┌─────────────┐    mTLS    ┌─────────────┐     │
│  │ Service A   │◄──────────▶│ Service B   │     │
│  │ ┌─────────┐ │            │ ┌─────────┐ │     │
│  │ │  Envoy  │ │            │ │  Envoy  │ │     │
│  │ │ (Proxy) │ │            │ │ (Proxy) │ │     │
│  │ └─────────┘ │            │ └─────────┘ │     │
│  └─────────────┘            └─────────────┘     │
│                                                   │
│  • Automatic cert rotation                       │
│  • Traffic encryption                            │
│  • Identity verification                         │
│  • Access policies                               │
└─────────────────────────────────────────────────┘
```

**Interview Tip:** "mTLS in a service mesh ensures every service-to-service call is authenticated and encrypted, implementing zero-trust within the cluster."

---

## 7. Deployment Patterns

### 7.1 Blue-Green Deployment

**What it is:** Run two identical production environments. Switch traffic from old (blue) to new (green) instantly.

**Diagram:**
```
Before:                          After:
┌──────────┐                    ┌──────────┐
│  Router  │                    │  Router  │
└────┬─────┘                    └────┬─────┘
     │                               │
     ▼                               ▼
┌──────────┐  ┌──────────┐    ┌──────────┐  ┌──────────┐
│  BLUE    │  │  GREEN   │    │  BLUE    │  │  GREEN   │
│  (v1.0)  │  │  (v1.1)  │    │  (v1.0)  │  │  (v1.1)  │
│  ACTIVE  │  │  IDLE    │    │  IDLE    │  │  ACTIVE  │
└──────────┘  └──────────┘    └──────────┘  └──────────┘
     100%          0%               0%          100%
```

**Benefits:** Instant rollback (switch back to blue), zero downtime.

**Drawback:** Requires double infrastructure.

---

### 7.2 Canary Deployment

**What it is:** Gradually roll out changes to a small subset of users before full deployment.

**Diagram:**
```
Phase 1:          Phase 2:          Phase 3:
┌──────────┐     ┌──────────┐     ┌──────────┐
│  Router  │     │  Router  │     │  Router  │
└────┬─────┘     └────┬─────┘     └────┬─────┘
     │                 │                 │
  ┌──┴───┐         ┌──┴───┐         ┌──┴───┐
  │      │         │      │         │      │
  ▼      ▼         ▼      ▼         ▼      ▼
┌────┐ ┌────┐   ┌────┐ ┌────┐   ┌────┐ ┌────┐
│v1.0│ │v1.1│   │v1.0│ │v1.1│   │v1.0│ │v1.1│
│95% │ │ 5% │   │75% │ │25% │   │ 0% │ │100%│
└────┘ └────┘   └────┘ └────┘   └────┘ └────┘
```

**Interview Tip:** "Canary lets us validate in production with real traffic. If error rate spikes, we automatically route back to the stable version."

---

### 7.3 Sidecar Pattern

**What it is:** Deploy helper functionality alongside each service as a sidecar container.

**Diagram:**
```
┌────────────────────────────────┐
│            Pod                  │
│                                │
│  ┌──────────────┐  ┌────────┐ │
│  │   Main       │  │Sidecar │ │
│  │  Service     │  │        │ │
│  │              │←→│• Logging│ │
│  │  (Business   │  │• mTLS  │ │
│  │   Logic)     │  │• Metrics│ │
│  │              │  │• Proxy │ │
│  └──────────────┘  └────────┘ │
│                                │
└────────────────────────────────┘
```

**Use cases:** Logging agents, service mesh proxies (Envoy), config management, security

**Example (Kubernetes):**
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: app
      image: order-service:1.0
      ports:
        - containerPort: 8080
    - name: envoy-sidecar
      image: envoyproxy/envoy:latest
      ports:
        - containerPort: 9901
```

---

### 7.4 Service Mesh

**What it is:** Infrastructure layer that handles service-to-service communication (networking, security, observability) without changing application code.

**Diagram:**
```
┌──────────────────────────────────────────────────┐
│                 Control Plane                      │
│          (Istio Pilot / Linkerd)                  │
│                                                   │
│  • Configuration  • Service Discovery            │
│  • Certificate Management  • Policy              │
└────────────────────────┬─────────────────────────┘
                         │ config push
         ┌───────────────┼───────────────┐
         │               │               │
         ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ ┌─────────┐ │  │ ┌─────────┐ │  │ ┌─────────┐ │
│ │ Service │ │  │ │ Service │ │  │ │ Service │ │
│ │    A    │ │  │ │    B    │ │  │ │    C    │ │
│ └────┬────┘ │  │ └────┬────┘ │  │ └────┬────┘ │
│      │      │  │      │      │  │      │      │
│ ┌────▼────┐ │  │ ┌────▼────┐ │  │ ┌────▼────┐ │
│ │  Proxy  │◄├──├─▶│  Proxy  │◄├──├─▶│  Proxy  │ │
│ │ (Envoy) │ │  │ │ (Envoy) │ │  │ │ (Envoy) │ │
│ └─────────┘ │  │ └─────────┘ │  │ └─────────┘ │
└─────────────┘  └─────────────┘  └─────────────┘
     Data Plane (proxy-to-proxy communication)
```

**Tools:** Istio, Linkerd, Consul Connect

---

## 8. Observability Patterns

### 8.1 Distributed Tracing

**What it is:** Track a request as it flows through multiple services.

**Diagram:**
```
Request: GET /api/orders/123

┌─────────────────────────────────────────────────────────────┐
│ Trace ID: abc-123                                            │
│                                                              │
│ API Gateway    ████████████████████████████████  (200ms)     │
│   │                                                          │
│   └─▶ Order   ░░░░████████████████████  (150ms)            │
│        Service      │                                        │
│                     ├─▶ User    ░░░████  (40ms)             │
│                     │   Service                              │
│                     │                                        │
│                     └─▶ Payment ░░░░░░████████  (80ms)      │
│                         Service                              │
└─────────────────────────────────────────────────────────────┘
```

**Tools:** Jaeger, Zipkin, AWS X-Ray, OpenTelemetry

**Example (Spring + OpenTelemetry):**
```java
@RestController
public class OrderController {

    private final Tracer tracer;

    @GetMapping("/orders/{id}")
    public Order getOrder(@PathVariable String id) {
        Span span = tracer.spanBuilder("getOrder")
            .setAttribute("order.id", id)
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Order order = orderService.findById(id);
            span.setAttribute("order.status", order.getStatus());
            return order;
        } finally {
            span.end();
        }
    }
}
```

---

### 8.2 Log Aggregation

**What it is:** Centralize logs from all services into a single searchable system.

**Diagram:**
```
┌──────────┐  ┌──────────┐  ┌──────────┐
│Service A │  │Service B │  │Service C │
│  logs    │  │  logs    │  │  logs    │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
            ┌───────▼────────┐
            │  Log Shipper   │
            │ (Fluentd/      │
            │  Filebeat)     │
            └───────┬────────┘
                    │
            ┌───────▼────────┐
            │ Elasticsearch  │
            │   / Loki       │
            └───────┬────────┘
                    │
            ┌───────▼────────┐
            │   Kibana /     │
            │   Grafana      │
            │  (Dashboard)   │
            └────────────────┘
```

**Structured Logging Example:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "ERROR",
  "service": "payment-service",
  "traceId": "abc-123",
  "spanId": "def-456",
  "message": "Payment failed",
  "userId": "user-789",
  "errorCode": "INSUFFICIENT_FUNDS",
  "amount": 99.99
}
```

**Stack:** ELK (Elasticsearch + Logstash + Kibana), Grafana + Loki, Datadog

---

### 8.3 Health Check / Heartbeat

**What it is:** Services expose health endpoints for monitoring and orchestration.

**Example (Spring Boot Actuator):**
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        if (isDatabaseReachable()) {
            return Health.up()
                .withDetail("database", "PostgreSQL")
                .withDetail("latency", "5ms")
                .build();
        }
        return Health.down()
            .withDetail("error", "Cannot reach database")
            .build();
    }
}
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP", "details": { "free": "50GB" } }
  }
}
```

---

## 9. Structural Patterns

### 9.1 Aggregator Pattern

**What it is:** A service that calls multiple downstream services and combines their responses.

**Diagram:**
```
┌──────────┐
│  Client  │
└────┬─────┘
     │
┌────▼──────────┐
│  Aggregator   │
│   Service     │
└───┬───┬───┬───┘
    │   │   │
    ▼   ▼   ▼
┌────┐┌────┐┌────┐
│Svc ││Svc ││Svc │
│ A  ││ B  ││ C  │
└────┘└────┘└────┘

Response = merge(A.data, B.data, C.data)
```

**Example:**
```java
@Service
public class OrderAggregator {

    public OrderDetailsResponse getOrderDetails(String orderId) {
        // Parallel calls to multiple services
        CompletableFuture<Order> orderFuture = 
            CompletableFuture.supplyAsync(() -> orderService.getOrder(orderId));
        CompletableFuture<Customer> customerFuture = 
            CompletableFuture.supplyAsync(() -> customerService.getCustomer(order.getCustomerId()));
        CompletableFuture<ShippingInfo> shippingFuture = 
            CompletableFuture.supplyAsync(() -> shippingService.getTracking(orderId));

        CompletableFuture.allOf(orderFuture, customerFuture, shippingFuture).join();

        return OrderDetailsResponse.builder()
            .order(orderFuture.get())
            .customer(customerFuture.get())
            .shipping(shippingFuture.get())
            .build();
    }
}
```

---

### 9.2 Anti-Corruption Layer (ACL)

**What it is:** A translation layer between your service and an external/legacy system to prevent their models from leaking into yours.

**Diagram:**
```
┌──────────────────────────────────────────────────┐
│              Your Microservice                     │
│                                                   │
│  ┌──────────────┐    ┌────────────────────────┐  │
│  │  Domain      │    │  Anti-Corruption Layer │  │
│  │  Model       │◀──▶│                        │  │
│  │              │    │  • Translate models     │  │
│  │ Order {      │    │  • Adapt protocols      │  │
│  │   items[]    │    │  • Isolate changes      │  │
│  │   total      │    │                        │  │
│  │ }            │    └───────────┬────────────┘  │
│  └──────────────┘                │               │
└──────────────────────────────────┼───────────────┘
                                   │
                          ┌────────▼────────┐
                          │  Legacy System  │
                          │  (SOAP/XML)     │
                          │                 │
                          │ PurchaseOrder { │
                          │   line_items[]  │
                          │   grand_total   │
                          │ }               │
                          └─────────────────┘
```

**Example:**
```java
// Anti-Corruption Layer - Translator
@Component
public class LegacyOrderTranslator {

    public Order fromLegacy(LegacyPurchaseOrder legacyOrder) {
        return Order.builder()
            .id(legacyOrder.getPO_NUMBER())
            .items(legacyOrder.getLINE_ITEMS().stream()
                .map(this::translateItem)
                .collect(Collectors.toList()))
            .total(new BigDecimal(legacyOrder.getGRAND_TOTAL()))
            .build();
    }

    public LegacyPurchaseOrder toLegacy(Order order) {
        LegacyPurchaseOrder po = new LegacyPurchaseOrder();
        po.setPO_NUMBER(order.getId());
        po.setGRAND_TOTAL(order.getTotal().toString());
        return po;
    }
}
```

**Interview Tip:** "ACL protects our clean domain model from being polluted by external system quirks or legacy naming conventions."

---

### 9.3 Backends for Frontends (BFF) — Expanded

**Detailed Example (Node.js BFF for Mobile):**
```javascript
// Mobile BFF - lightweight responses for mobile
app.get('/api/orders/:id', async (req, res) => {
    const [order, tracking] = await Promise.all([
        orderService.getOrder(req.params.id),
        shippingService.getTracking(req.params.id)
    ]);

    // Mobile gets minimal data
    res.json({
        orderId: order.id,
        status: order.status,
        estimatedDelivery: tracking.eta,
        itemCount: order.items.length
        // No full item details — saves bandwidth
    });
});
```

```javascript
// Web BFF - rich responses for desktop
app.get('/api/orders/:id', async (req, res) => {
    const [order, tracking, reviews, recommendations] = await Promise.all([
        orderService.getOrder(req.params.id),
        shippingService.getTracking(req.params.id),
        reviewService.getReviews(req.params.id),
        recommendationService.getRelated(req.params.id)
    ]);

    // Web gets full data
    res.json({
        order: order,
        tracking: tracking,
        reviews: reviews,
        recommendations: recommendations
    });
});
```

---

## 10. Additional Important Patterns

### 10.1 Externalized Configuration

**What it is:** Store configuration outside the service binary (env vars, config servers).

**Tools:** Spring Cloud Config, HashiCorp Consul, AWS Parameter Store

**Diagram:**
```
┌─────────────────┐
│  Config Server  │  (Git-backed or Vault)
│  ┌───────────┐  │
│  │ dev.yml   │  │
│  │ prod.yml  │  │
│  │ test.yml  │  │
│  └───────────┘  │
└────────┬────────┘
         │ pull config on startup
    ┌────┼────────────────┐
    │    │                │
    ▼    ▼                ▼
┌──────┐ ┌──────┐  ┌──────┐
│Svc A │ │Svc B │  │Svc C │
└──────┘ └──────┘  └──────┘
```

---

### 10.2 Leader Election

**What it is:** One instance becomes the "leader" to perform coordination tasks (scheduled jobs, partition assignment).

**Tools:** Apache ZooKeeper, etcd, Redis (Redlock)

**Example:**
```java
@Component
public class LeaderElectionService {
    @Autowired
    private CuratorFramework curator;

    public void participate() {
        LeaderSelector selector = new LeaderSelector(curator, "/leader/job-scheduler",
            new LeaderSelectorListenerAdapter() {
                @Override
                public void takeLeadership(CuratorFramework client) {
                    // This instance is the leader
                    runScheduledJobs();
                }
            });
        selector.start();
    }
}
```

---

### 10.3 Idempotency Pattern

**What it is:** Ensures that performing the same operation multiple times produces the same result.

**Why it matters:** In distributed systems, retries are common. Without idempotency, a retry could charge a customer twice.

**Implementation:**
```java
@Service
public class PaymentService {
    
    @Transactional
    public PaymentResponse processPayment(String idempotencyKey, PaymentRequest req) {
        // Check if already processed
        Optional<Payment> existing = paymentRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());  // Return same result
        }
        
        // Process new payment
        Payment payment = executePayment(req);
        payment.setIdempotencyKey(idempotencyKey);
        paymentRepo.save(payment);
        return toResponse(payment);
    }
}
```

**Interview Tip:** "Every API that mutates state should be idempotent. We use idempotency keys to detect duplicate requests from retries."

---

### 10.4 Consumer-Driven Contract Testing

**What it is:** Consumers define the contract they expect from a provider. Provider tests verify compliance.

**Diagram:**
```
┌──────────────┐         ┌────────────────┐
│   Consumer   │ defines │    Contract    │
│ (Order Svc)  │────────▶│   (Pact file)  │
└──────────────┘         └───────┬────────┘
                                 │
                                 │ verifies against
                                 ▼
                         ┌──────────────┐
                         │   Provider   │
                         │(Payment Svc) │
                         └──────────────┘
```

**Tools:** Pact, Spring Cloud Contract

---

## 11. Quick Reference: Pattern Selection Guide

| Problem                                  | Pattern                        |
|------------------------------------------|--------------------------------|
| How to split a monolith?                 | Strangler Fig, DDD Decompose   |
| How to handle cross-service transactions?| Saga (Choreography/Orchestration)|
| How to handle service failures?          | Circuit Breaker, Retry, Fallback|
| How to route client requests?            | API Gateway, BFF               |
| How to manage per-service data?          | Database per Service, CQRS     |
| How to ensure reliable messaging?        | Transactional Outbox, Event Sourcing|
| How to deploy safely?                    | Blue-Green, Canary             |
| How to debug across services?            | Distributed Tracing, Log Aggregation|
| How to secure service communication?     | mTLS, Service Mesh, JWT        |
| How to find service instances?           | Service Discovery (Client/Server)|
| How to prevent overload?                 | Rate Limiting, Bulkhead        |
| How to prevent duplicate processing?     | Idempotency Pattern            |

---

## 12. Interview Power Answers

### "What is the difference between Microservices and Monolith?"

| Aspect         | Monolith                | Microservices             |
|----------------|-------------------------|---------------------------|
| Deployment     | Single unit             | Independent per service   |
| Scaling        | Scale entire app        | Scale individual services |
| Technology     | Single stack            | Polyglot                  |
| Team structure | One large team          | Small, autonomous teams   |
| Data           | Shared database         | Database per service      |
| Failure        | Entire app fails        | Isolated failures         |
| Complexity     | Simple to start         | Operationally complex     |

### "How do you handle distributed transactions?"

> "We use the Saga pattern. For simple flows, choreography-based sagas where services react to events. For complex business workflows, we use an orchestrator that coordinates the steps and handles compensating transactions if any step fails."

### "How do you ensure data consistency?"

> "We embrace eventual consistency with event-driven architecture. For critical consistency needs, we use the Transactional Outbox pattern to reliably publish events. CQRS separates our read and write models, and Event Sourcing gives us a complete audit trail."

### "How do you handle service failures?"

> "We layer multiple resilience patterns: Circuit Breakers prevent cascading failures, Retries with exponential backoff handle transient issues, Bulkheads isolate failures, Timeouts prevent resource exhaustion, and Fallbacks provide degraded but functional responses."

### "How do you secure microservices?"

> "At the perimeter, API Gateway handles authentication with JWT tokens. Service-to-service communication uses mTLS via a service mesh like Istio. We follow zero-trust principles — every call is authenticated and authorized regardless of network location."

---

## 13. Architecture Diagram: Complete Microservices System

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                          │
│     ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐                   │
│     │ Mobile │    │  Web   │    │  IoT   │    │3rd Party│                   │
│     └───┬────┘    └───┬────┘    └───┬────┘    └───┬────┘                   │
└─────────┼─────────────┼────────────┼─────────────┼──────────────────────────┘
          │             │            │             │
┌─────────┼─────────────┼────────────┼─────────────┼──────────────────────────┐
│         ▼             ▼            ▼             ▼                           │
│  ┌─────────────────────────────────────────────────────────┐                │
│  │                    API GATEWAY                            │                │
│  │  Auth │ Rate Limit │ Routing │ Load Balance │ SSL       │                │
│  └────────────────────────┬────────────────────────────────┘                │
│                           │                                                  │
│  ┌────────────────────────┼────────────────────────────────┐                │
│  │              SERVICE MESH (Istio/Linkerd)                │                │
│  │                        │                                 │                │
│  │    ┌───────┐    ┌──────┴──┐    ┌─────────┐    ┌──────┐ │                │
│  │    │ User  │    │  Order  │    │ Payment │    │Notif.│ │                │
│  │    │Service│◄──▶│ Service │◄──▶│ Service │    │Svc   │ │                │
│  │    └───┬───┘    └────┬────┘    └────┬────┘    └──┬───┘ │                │
│  │        │             │              │            │      │                │
│  └────────┼─────────────┼──────────────┼────────────┼──────┘                │
│           │             │              │            │                         │
│  ┌────────┼─────────────┼──────────────┼────────────┼──────┐                │
│  │        ▼             ▼              ▼            ▼      │  EVENT BUS     │
│  │  ┌─────────────────────────────────────────────────┐    │                │
│  │  │              Apache Kafka / RabbitMQ             │    │                │
│  │  └─────────────────────────────────────────────────┘    │                │
│  └─────────────────────────────────────────────────────────┘                │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────┐              │
│  │                    DATA LAYER                              │              │
│  │   ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐            │              │
│  │   │Postgr│   │MongoDB│   │Redis │   │Elastic│            │              │
│  │   │(Users)│   │(Orders│   │(Cache)│   │(Search)│           │              │
│  │   └──────┘   └──────┘   └──────┘   └──────┘            │              │
│  └───────────────────────────────────────────────────────────┘              │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────┐              │
│  │                  OBSERVABILITY                             │              │
│  │   ┌────────┐   ┌────────┐   ┌─────────┐   ┌──────────┐ │              │
│  │   │Prometheus│  │ Jaeger │   │ Grafana │   │ELK Stack │ │              │
│  │   │(Metrics)│   │(Traces)│   │(Dashbrd)│   │ (Logs)   │ │              │
│  │   └────────┘   └────────┘   └─────────┘   └──────────┘ │              │
│  └───────────────────────────────────────────────────────────┘              │
│                                                                              │
│                         INFRASTRUCTURE (Kubernetes)                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Summary

This document covers **25+ microservices design patterns** organized into:

1. **Decomposition** — How to break down services
2. **Communication** — How services talk to each other
3. **Data Management** — How to handle distributed data
4. **Service Discovery** — How services find each other
5. **Resilience** — How to handle failures gracefully
6. **Security** — How to protect communication
7. **Deployment** — How to release safely
8. **Observability** — How to monitor and debug
9. **Structural** — How to organize service interactions

Each pattern includes explanations, diagrams, code examples, and interview-ready answers. Use the Quick Reference table (Section 11) to quickly match problems to patterns during interviews.

---

*Good luck with your interview!* 🚀
