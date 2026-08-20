# 2. Decomposition Patterns ⭐⭐⭐⭐⭐

## Theory

Decomposition is the process of breaking a monolithic application into microservices. The key challenge is finding the right boundaries — too fine-grained leads to chatty services, too coarse-grained leads to mini-monoliths.

### Decomposition Strategies:

| Strategy | Approach | When to Use |
|----------|----------|-------------|
| By Business Capability | Align with business functions | Greenfield projects |
| By Subdomain | Align with DDD subdomains | Complex domains |
| Strangler Fig | Gradually replace monolith | Legacy migration |
| Service per Team | Align with team structure | Organization restructuring |

### Decompose by Business Capability:
- Identify what the business does (not how)
- Each capability becomes a service
- Examples: Order Management, Payment Processing, User Management, Inventory Management

### Decompose by Subdomain (DDD):
- **Core domain**: Main business differentiator (e.g., recommendation engine)
- **Supporting subdomain**: Supports core but not differentiating (e.g., order management)
- **Generic subdomain**: Common across businesses (e.g., authentication)

### Strangler Fig Pattern:
- Gradually replace monolith functionality
- Route traffic to new service as features migrate
- Eventually strangle (replace) the old system completely

---

## Internal Working

### Business Capability Decomposition:

```
E-Commerce Business Capabilities:
┌─────────────────────────────────────────────────────────┐
│                    E-Commerce Platform                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │    Product    │  │    Order     │  │   Payment    │ │
│  │  Management   │  │  Management  │  │  Processing  │ │
│  │              │  │              │  │              │ │
│  │ - Catalog    │  │ - Create     │  │ - Charge     │ │
│  │ - Pricing   │  │ - Track      │  │ - Refund     │ │
│  │ - Search    │  │ - Cancel     │  │ - History    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │   Customer   │  │  Inventory   │  │  Shipping    │ │
│  │  Management  │  │  Management  │  │  & Delivery  │ │
│  │              │  │              │  │              │ │
│  │ - Register  │  │ - Stock      │  │ - Dispatch   │ │
│  │ - Profile   │  │ - Reserve    │  │ - Track      │ │
│  │ - Auth      │  │ - Restock    │  │ - Returns    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐                    │
│  │ Notification │  │  Analytics   │                    │
│  │   Service    │  │   Service    │                    │
│  │              │  │              │                    │
│  │ - Email     │  │ - Reports    │                    │
│  │ - SMS       │  │ - Metrics    │                    │
│  │ - Push      │  │ - Dashboard  │                    │
│  └──────────────┘  └──────────────┘                    │
└─────────────────────────────────────────────────────────┘
```

### Strangler Fig Pattern — Step by Step:

```
Phase 1: Identify boundary
┌─────────────────────────────┐
│         MONOLITH            │
│  ┌──────┐ ┌──────┐ ┌─────┐│
│  │Order │ │Payment│ │User ││
│  └──────┘ └──────┘ └─────┘│
└─────────────────────────────┘
        ↑ All traffic

Phase 2: Extract + proxy
┌────────────────┐
│   Proxy/Router │ ← All traffic enters here
└───┬────────┬───┘
    │        │
    ↓        ↓
┌──────┐  ┌─────────────────┐
│ NEW  │  │    MONOLITH     │
│Order │  │ ┌──────┐ ┌─────┐│
│ Svc  │  │ │Payment│ │User ││
└──────┘  │ └──────┘ └─────┘│
          └─────────────────┘

Phase 3: Continue extracting
┌────────────────┐
│   Proxy/Router │
└─┬────┬─────┬──┘
  │    │     │
  ↓    ↓     ↓
┌────┐┌───────┐┌─────────┐
│Order││Payment││MONOLITH │
│Svc ││ Svc   ││ ┌─────┐ │
└────┘└───────┘││ │User │ │
               │ └─────┘ │
               └─────────┘

Phase 4: Fully decomposed (monolith strangled)
┌────────────────┐
│   API Gateway  │
└─┬────┬─────┬──┘
  │    │     │
  ↓    ↓     ↓
┌────┐┌───────┐┌────┐
│Order││Payment││User│
│Svc ││ Svc   ││Svc │
└────┘└───────┘└────┘
```

---

## Diagram

```
Shared Database → Database Per Service Migration:

BEFORE (Anti-pattern):
┌───────┐  ┌─────────┐  ┌──────┐
│ Order │  │ Payment │  │ User │
│Service│  │ Service │  │Service│
└───┬───┘  └────┬────┘  └───┬──┘
    │            │           │
    └────────────┼───────────┘
                 ↓
    ┌────────────────────────┐
    │    SHARED DATABASE     │
    │  ┌──────┬──────┬─────┐│
    │  │orders│payments│users││
    │  └──────┴──────┴─────┘│
    └────────────────────────┘
    Problems:
    - Schema changes affect all services
    - Can't scale DB per service
    - Technology lock-in
    - Tight coupling

AFTER (Correct):
┌───────┐  ┌─────────┐  ┌──────┐
│ Order │  │ Payment │  │ User │
│Service│  │ Service │  │Service│
└───┬───┘  └────┬────┘  └───┬──┘
    │            │           │
    ↓            ↓           ↓
┌───────┐  ┌─────────┐  ┌──────┐
│Order  │  │Payment  │  │User  │
│  DB   │  │  DB     │  │  DB  │
│(Postgres)│(Postgres)│  │(MongoDB)│
└───────┘  └─────────┘  └──────┘
    Benefits:
    - Independent schema evolution
    - Technology freedom
    - Independent scaling
    - Loose coupling
```

---

## Code

### Strangler Fig with Spring Cloud Gateway:

```java
// Gateway routing — gradually redirect traffic to new service
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // New microservice (extracted from monolith)
            .route("order-service", r -> r
                .path("/api/orders/**")
                .uri("lb://order-service"))  // Route to new service
            
            // Still in monolith (not yet extracted)
            .route("monolith-remaining", r -> r
                .path("/api/**")
                .uri("http://monolith-host:8080"))  // Route to monolith
            .build();
    }
}
```

### Feature Flag for Gradual Migration:

```java
@Service
public class OrderRoutingService {

    @Value("${feature.new-order-service.enabled:false}")
    private boolean useNewOrderService;

    @Value("${feature.new-order-service.percentage:0}")
    private int rolloutPercentage;

    public OrderResponse createOrder(CreateOrderRequest request) {
        if (shouldUseNewService(request)) {
            return newOrderServiceClient.createOrder(request);
        }
        return monolithClient.createOrder(request);
    }

    private boolean shouldUseNewService(CreateOrderRequest request) {
        if (!useNewOrderService) return false;
        // Canary: route percentage of traffic to new service
        int hash = Math.abs(request.getUserId().hashCode() % 100);
        return hash < rolloutPercentage;
    }
}
```

### Database Per Service — Correct Boundaries:

```java
// Order Service — only knows about orders
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;
    private String customerId;  // Just an ID reference, NOT a User entity
    private BigDecimal total;
    private OrderStatus status;
    
    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderItem> items;
}

// If Order Service needs customer name for display:
// Option 1: Event-carried state (store name locally, update via events)
// Option 2: API call to User Service (synchronous coupling)
// Option 3: API Gateway aggregation
```

### Subdomain Decomposition Example:

```java
// Core Domain — highest investment, custom logic
// Recommendation engine (competitive advantage)
@Service
public class RecommendationService {
    // Complex ML-based recommendation logic
    // Most senior engineers work here
    // Custom-built, not off-the-shelf
}

// Supporting Subdomain — supports core but simpler
// Order management
@Service
public class OrderService {
    // Standard CRUD + business rules
    // Important but not differentiating
}

// Generic Subdomain — buy or use existing solution
// Authentication
@Service  
public class AuthService {
    // Use Keycloak, Auth0, or Spring Security
    // Don't reinvent the wheel
}
```

---

## Interview Questions

1. **How do you decompose a monolith into microservices?**
   - Identify business capabilities or DDD bounded contexts. Start with loosely coupled modules. Use Strangler Fig to gradually extract services. Don't decompose everything at once.

2. **What is Strangler Fig pattern?**
   - Gradually replace monolith by routing traffic to new microservices one feature at a time. Proxy/gateway controls routing. Eventually all traffic goes to new services and monolith is decommissioned.

3. **What is "Database per Service" and why?**
   - Each service owns its database schema. Ensures loose coupling, independent deployments, technology freedom. Trade-off: distributed data consistency (solved by Saga pattern).

4. **How to decide service boundaries?**
   - Business capability alignment, DDD bounded contexts, team structure (Conway's Law), data ownership. If two modules change together frequently, they probably belong in one service.

5. **What is a distributed monolith?**
   - Anti-pattern where services share databases, require synchronized deployments, or are tightly coupled. Has all the complexity of microservices with none of the benefits.

6. **Service per Team — how does Conway's Law apply?**
   - Organizations design systems that mirror their communication structure. Align service boundaries with team boundaries. One team = one or few services they fully own and operate.

---

## Common Mistakes

1. **Decomposing too early** — Start monolith, discover boundaries, then decompose
2. **Decomposing too fine** — Nano-services create excessive network overhead
3. **Ignoring data boundaries** — Services that share a database are coupled
4. **Breaking domain concepts** — Splitting an aggregate across services
5. **Big bang migration** — Trying to rewrite everything at once instead of incremental Strangler Fig
6. **Technology-driven decomposition** — Splitting by layer (UI service, business service, data service) instead of by capability

---

## Best Practices

1. **Start with a well-structured monolith** — modular monolith with clear boundaries
2. **Use DDD to find boundaries** — bounded contexts map naturally to services
3. **Extract one service at a time** — Strangler Fig approach
4. **Data ownership is the key** — if two services need the same data, boundaries are wrong
5. **Two-pizza team** — each service should be owned by a team small enough to feed with two pizzas
6. **Verify with use cases** — walk through user journeys to ensure boundaries make sense
7. **Accept eventual consistency** — cross-service data will be eventually consistent, not immediately
