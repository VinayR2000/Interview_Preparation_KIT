# 1. Microservices Fundamentals

## Theory

Microservices architecture is an approach where an application is built as a collection of small, independent services. Each service runs in its own process, owns its data, and communicates via well-defined APIs.

### Monolith vs Microservices:

| Aspect | Monolith | Microservices |
|--------|----------|---------------|
| Deployment | Single unit | Independent per service |
| Scaling | Scale entire app | Scale individual services |
| Technology | Single tech stack | Polyglot (any language/DB) |
| Data | Shared database | Database per service |
| Team | Large team on one codebase | Small teams per service |
| Failure | One bug crashes all | Isolated failures |
| Complexity | Simple to start | Distributed system complexity |

### SOA vs Microservices:

| Aspect | SOA | Microservices |
|--------|-----|---------------|
| Scope | Enterprise-wide | Application-specific |
| Communication | ESB (Enterprise Service Bus) | Lightweight (REST, messaging) |
| Data | Often shared | Database per service |
| Size | Larger services | Smaller, focused services |
| Governance | Centralized | Decentralized |

### Core Principles:
- **Service boundaries**: Each service handles one business capability
- **Bounded Context** (DDD): Clear boundary around a domain model
- **Database per service**: No shared database between services
- **Independent deployment**: Deploy one service without affecting others
- **Stateless services**: No session state stored on the server
- **Horizontal scaling**: Add more instances, not bigger servers
- **Service ownership**: One team owns, builds, and operates a service

---

## Internal Working

### How Microservices Communicate:

```
┌──────────────────────────────────────────────────────────────┐
│                    MICROSERVICES ECOSYSTEM                     │
│                                                               │
│  Client (Browser/Mobile)                                     │
│       │                                                       │
│       ↓                                                       │
│  ┌──────────────┐                                            │
│  │  API Gateway │  (Single entry point)                      │
│  └──────┬───────┘                                            │
│         │                                                     │
│    ┌────┼────────────┐                                       │
│    ↓    ↓            ↓                                       │
│  ┌────┐ ┌───────┐ ┌──────┐                                  │
│  │User│ │ Order │ │Payment│                                  │
│  │Svc │ │  Svc  │ │  Svc │                                  │
│  └──┬─┘ └───┬───┘ └───┬──┘                                  │
│     │        │         │                                      │
│     ↓        ↓         ↓                                      │
│  ┌────┐ ┌───────┐ ┌──────┐                                  │
│  │User│ │ Order │ │Payment│                                  │
│  │ DB │ │  DB   │ │  DB  │                                   │
│  └────┘ └───────┘ └──────┘                                   │
│                                                               │
│  Supporting Infrastructure:                                   │
│  ┌─────────────┬───────────────┬──────────────┐             │
│  │Service      │Config Server  │Message Broker│              │
│  │Registry     │               │(Kafka/RabbitMQ)│            │
│  └─────────────┴───────────────┴──────────────┘             │
└──────────────────────────────────────────────────────────────┘
```

### Bounded Context Example:

```
E-Commerce Domain
├── Order Context
│   ├── Order (aggregate root)
│   ├── OrderLine
│   └── OrderStatus
├── Payment Context
│   ├── Payment (aggregate root)
│   ├── Transaction
│   └── Refund
├── User Context
│   ├── User (aggregate root)
│   ├── Address
│   └── Profile
└── Inventory Context
    ├── Product (aggregate root)
    ├── Stock
    └── Warehouse

Each context = one microservice
Each context has its own database
Each context has clear boundaries
```

---

## Diagram

```
Monolith → Microservices Evolution:

MONOLITH:
┌─────────────────────────────┐
│         Single WAR/JAR       │
│  ┌────────┬────────┬──────┐ │
│  │ Order  │Payment │ User │ │
│  │ Module │Module  │Module│ │
│  └────────┴────────┴──────┘ │
│         Shared Database      │
│  ┌─────────────────────────┐│
│  │     Single Database      ││
│  └─────────────────────────┘│
└─────────────────────────────┘

MICROSERVICES:
┌───────┐   ┌─────────┐   ┌──────┐
│ Order │   │ Payment │   │ User │
│Service│   │ Service │   │Service│
└───┬───┘   └────┬────┘   └───┬──┘
    │            │             │
┌───┴───┐   ┌───┴────┐   ┌───┴──┐
│Order  │   │Payment │   │User  │
│  DB   │   │  DB    │   │  DB  │
└───────┘   └────────┘   └──────┘

Each service:
- Has its own codebase
- Has its own database
- Can be deployed independently
- Can scale independently
- Can use different technologies
```

---

## Code

### Spring Boot Microservice Structure:

```java
// Order Service - Application
@SpringBootApplication
@EnableDiscoveryClient
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

```yaml
# application.yml
spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://localhost:5432/order_db

server:
  port: 8081

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

```java
// Domain model - bounded to Order context
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerId;  // Reference by ID, not entity
    private BigDecimal totalAmount;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}

// Note: Order service does NOT have User entity
// It only stores customerId as a reference
```

### Stateless Service Example:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // No HttpSession, no server-side state
    // Authentication via JWT token (stateless)
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Authorization") String token,  // Stateless auth
            @Valid @RequestBody CreateOrderRequest request) {
        
        String userId = jwtService.extractUserId(token);
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

---

## Interview Questions

1. **Why microservices over monolith?**
   - Independent deployment, scaling, technology diversity, fault isolation, team autonomy. But adds distributed system complexity (networking, data consistency, observability).

2. **What is Bounded Context?**
   - A DDD concept where each microservice has a clear boundary around its domain model. User in Order context (just userId + name) differs from User in User context (full profile). Prevents tight coupling.

3. **Why database per service?**
   - Loose coupling: services don't share schema. Independent scaling. Technology freedom (SQL vs NoSQL). But creates data consistency challenges (solved by Saga pattern).

4. **When NOT to use microservices?**
   - Small team, simple domain, early-stage startup, no need for independent scaling. Start monolith, decompose when needed (Strangler Fig pattern).

5. **What makes a service stateless?**
   - No server-side session. All state in database or passed with request (JWT). Any instance can handle any request. Enables horizontal scaling and load balancing.

6. **SOA vs Microservices?**
   - SOA: enterprise-wide, ESB, shared databases, SOAP. Microservices: application-level, lightweight protocols (REST/gRPC), database per service, smaller services. Microservices evolved from SOA lessons.

---

## Common Mistakes

1. **Too many services too early** — Start with a well-structured monolith, decompose when needed
2. **Shared database** — Defeats the purpose; creates coupling
3. **Not defining clear boundaries** — Leads to distributed monolith
4. **Synchronous everywhere** — Creates tight coupling and cascading failures
5. **No observability from day one** — Can't debug distributed systems without tracing
6. **Ignoring data consistency** — Distributed transactions don't work; need Saga pattern

---

## Best Practices

1. **Design around business capabilities** — not technical layers
2. **One team per service** — ownership and accountability
3. **API-first design** — define contracts before implementation
4. **Automate everything** — CI/CD per service is essential
5. **Design for failure** — circuit breakers, retries, fallbacks
6. **Event-driven communication** — async where possible to reduce coupling
7. **Start monolith, extract services** — don't over-decompose prematurely
