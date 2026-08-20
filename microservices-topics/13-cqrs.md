# 13. CQRS ⭐⭐⭐⭐⭐

## Theory

CQRS (Command Query Responsibility Segregation) separates the write model (commands) from the read model (queries). Instead of one model handling both reads and writes, you have two optimized for their specific purpose.

### Why CQRS?
- Read and write patterns are fundamentally different
- Reads need denormalized, fast data (joins are expensive)
- Writes need normalized, consistent data (enforce business rules)
- Scale reads and writes independently
- Optimize each side separately

### CQRS Variants:

| Variant | Complexity | Use Case |
|---------|-----------|----------|
| Separate models, same DB | Low | Different DTOs for read/write |
| Separate databases | Medium | Read replica, denormalized views |
| CQRS + Event Sourcing | High | Full separation, event-driven sync |

### When CQRS is Useful:
- High read-to-write ratio (read-heavy)
- Complex queries that don't map to write model
- Different scaling needs for reads vs writes
- Multiple read representations needed

### When CQRS is Overengineering:
- Simple CRUD applications
- Small-scale applications
- When reads and writes have same shape
- When team doesn't have distributed systems experience

---

## Internal Working

### Basic CQRS Architecture:

```
┌──────────────────────────────────────────────────────────────┐
│                        CQRS                                   │
│                                                               │
│  COMMAND SIDE (Write)           QUERY SIDE (Read)            │
│                                                               │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │ Command Handler  │         │  Query Handler   │          │
│  │                  │         │                  │          │
│  │ - Validate       │         │ - Simple fetch   │          │
│  │ - Business rules │         │ - No logic       │          │
│  │ - State change   │         │ - Denormalized   │          │
│  └────────┬─────────┘         └────────┬─────────┘          │
│           │                            │                      │
│           ↓                            ↓                      │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │   Write Model    │         │   Read Model     │          │
│  │                  │         │                  │          │
│  │ - Normalized     │         │ - Denormalized   │          │
│  │ - Enforces rules │         │ - Pre-joined     │          │
│  │ - Complex domain │         │ - Fast queries   │          │
│  └────────┬─────────┘         └────────┬─────────┘          │
│           │                            │                      │
│           ↓                            ↓                      │
│  ┌──────────────────┐         ┌──────────────────┐          │
│  │   Write DB       │  sync   │   Read DB        │          │
│  │  (PostgreSQL)    │ ──────→ │  (Elasticsearch) │          │
│  │                  │         │  (Redis)         │          │
│  │  - orders        │         │  (MongoDB)       │          │
│  │  - order_items   │         │                  │          │
│  │  - payments      │         │  - order_summary │          │
│  └──────────────────┘         │  - user_orders   │          │
│                               │  - analytics     │          │
│                               └──────────────────┘          │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### CQRS + Event Sourcing:

```
┌──────────────────────────────────────────────────────────────┐
│              CQRS + EVENT SOURCING                             │
│                                                               │
│  Command                                                      │
│    │                                                          │
│    ↓                                                          │
│  ┌──────────────┐                                            │
│  │Command Handler│                                            │
│  │  - Validate   │                                            │
│  │  - Apply event│                                            │
│  └──────┬───────┘                                            │
│         │                                                     │
│         ↓                                                     │
│  ┌──────────────┐     ┌────────────────────────────┐        │
│  │ Event Store  │────→│ Event Bus (Kafka)          │        │
│  │ (write DB)   │     │                            │        │
│  │              │     │ OrderCreated               │        │
│  │ All events   │     │ PaymentReceived            │        │
│  │ append-only  │     │ OrderShipped               │        │
│  └──────────────┘     └────────┬───────┬───────────┘        │
│                                │       │                      │
│                    ┌───────────┘       └───────────┐         │
│                    ↓                               ↓          │
│          ┌──────────────┐              ┌──────────────┐      │
│          │  Projection  │              │  Projection  │      │
│          │  "Order List"│              │  "Analytics" │      │
│          └──────┬───────┘              └──────┬───────┘      │
│                 ↓                              ↓              │
│          ┌──────────────┐              ┌──────────────┐      │
│          │ Read DB      │              │ Read DB      │      │
│          │(Elasticsearch)│             │ (ClickHouse) │      │
│          └──────────────┘              └──────────────┘      │
│                 ↑                              ↑              │
│                 │                              │              │
│          Query: "search orders"    Query: "monthly revenue"  │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### Eventual Consistency in CQRS:

```
Timeline:

T=0: Client sends CreateOrder command
T=1: Command handler processes, writes to event store ✓
T=2: Event published to Kafka
T=3: Read model projection receives event
T=4: Read model updated ✓

Gap: T=1 to T=4 (usually milliseconds, but NOT instant)

Client queries at T=2: Order NOT YET visible in read model!
Client queries at T=5: Order IS visible ✓

This is EVENTUAL CONSISTENCY.
The read model eventually catches up to the write model.

Mitigation strategies:
1. Return write result directly to the client who made the command
2. Poll/subscribe for confirmation
3. Accept eventual consistency in UI (show "processing" state)
```

---

## Diagram

```
Simple CQRS vs Full CQRS:

SIMPLE CQRS (Same DB, different models):
┌───────────────────────────────────────┐
│                                        │
│  Command DTO         Query DTO        │
│  ┌──────────┐       ┌──────────┐     │
│  │CreateOrder│       │OrderView │     │
│  │ - items   │       │ - orderId│     │
│  │ - userId  │       │ - status │     │
│  └─────┬─────┘       │ - total  │     │
│        │             │ - items  │     │
│        │             │ - userName│     │
│        │             └─────┬─────┘     │
│        │                   │           │
│        ↓                   ↓           │
│  ┌──────────────────────────────┐     │
│  │      Same Database           │     │
│  │  (Write uses JPA entities)   │     │
│  │  (Read uses custom queries)  │     │
│  └──────────────────────────────┘     │
│                                        │
│  Low complexity, immediate consistency │
└───────────────────────────────────────┘

FULL CQRS (Separate DBs):
┌───────────────────────────────────────┐
│                                        │
│  Write                     Read       │
│  ┌──────────┐       ┌──────────┐     │
│  │PostgreSQL│──events→│ MongoDB  │    │
│  │(normalized│       │(denormalized)│ │
│  │ 3NF)     │       │ pre-joined)│   │
│  └──────────┘       └──────────┘     │
│                                        │
│  Higher complexity, eventual consistency│
│  But: independent scaling, optimized   │
└───────────────────────────────────────┘
```

---

## Code

### Command Side:

```java
// Command
public record CreateOrderCommand(
    String customerId,
    List<OrderItemDto> items,
    String shippingAddress
) {}

// Command Handler
@Service
@Transactional
public class OrderCommandHandler {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final CustomerService customerService;

    public UUID handle(CreateOrderCommand command) {
        // Validate business rules
        Customer customer = customerService.validate(command.customerId());
        if (!customer.isActive()) {
            throw new BusinessException("Customer account is inactive");
        }

        // Create aggregate
        Order order = Order.create(
            UUID.randomUUID(),
            command.customerId(),
            command.items(),
            command.shippingAddress()
        );

        // Persist
        orderRepository.save(order);

        // Publish event for read model update
        eventPublisher.publish(new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal(),
            order.getItems(),
            Instant.now()
        ));

        return order.getId();
    }
}
```

### Query Side:

```java
// Query
public record GetOrdersByCustomerQuery(String customerId, int page, int size) {}

// Query Handler — reads from denormalized read model
@Service
@Transactional(readOnly = true)
public class OrderQueryHandler {

    private final OrderReadRepository readRepository;  // MongoDB or Elasticsearch

    public Page<OrderSummaryView> handle(GetOrdersByCustomerQuery query) {
        // Simple fetch — no joins, no business logic
        return readRepository.findByCustomerId(
            query.customerId(),
            PageRequest.of(query.page(), query.size(), Sort.by("createdAt").descending())
        );
    }

    public OrderDetailView getOrderDetail(UUID orderId) {
        // Pre-joined view — customer name, items, payment status all in one document
        return readRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

### Read Model Projection:

```java
// Read model — denormalized for fast queries
@Document(collection = "order_views")
public class OrderSummaryView {
    @Id
    private UUID orderId;
    private String customerId;
    private String customerName;  // Denormalized from User service
    private BigDecimal total;
    private String status;
    private List<ItemView> items;
    private Instant createdAt;
    private String trackingId;
}

// Projection updater — listens to events
@Service
public class OrderViewProjection {

    private final MongoTemplate mongoTemplate;

    @KafkaListener(topics = "order-events", groupId = "order-view-projection")
    public void handle(DomainEvent event) {
        switch (event) {
            case OrderCreatedEvent e -> {
                OrderSummaryView view = new OrderSummaryView();
                view.setOrderId(e.getOrderId());
                view.setCustomerId(e.getCustomerId());
                view.setCustomerName(e.getCustomerName());
                view.setTotal(e.getTotal());
                view.setStatus("PENDING");
                view.setItems(mapItems(e.getItems()));
                view.setCreatedAt(e.getOccurredAt());
                mongoTemplate.save(view);
            }
            case OrderShippedEvent e -> {
                mongoTemplate.updateFirst(
                    Query.query(Criteria.where("orderId").is(e.getOrderId())),
                    Update.update("status", "SHIPPED")
                          .set("trackingId", e.getTrackingId()),
                    OrderSummaryView.class
                );
            }
            case OrderCancelledEvent e -> {
                mongoTemplate.updateFirst(
                    Query.query(Criteria.where("orderId").is(e.getOrderId())),
                    Update.update("status", "CANCELLED"),
                    OrderSummaryView.class
                );
            }
        }
    }
}
```

### CQRS without Event Sourcing (Simpler):

```java
// Command side — normal JPA entities
@Service
@Transactional
public class OrderWriteService {

    private final OrderRepository orderRepo;
    private final ApplicationEventPublisher publisher;

    public UUID createOrder(CreateOrderCommand cmd) {
        Order order = new Order(cmd);
        orderRepo.save(order);
        
        // Publish domain event for read model sync
        publisher.publishEvent(new OrderCreatedEvent(order));
        return order.getId();
    }
}

// Query side — custom read-optimized queries
@Service
@Transactional(readOnly = true)
public class OrderReadService {

    private final JdbcTemplate jdbc;

    public List<OrderListDto> getCustomerOrders(String customerId) {
        return jdbc.query("""
            SELECT o.id, o.status, o.total, o.created_at,
                   u.name as customer_name,
                   COUNT(oi.id) as item_count
            FROM orders o
            JOIN users u ON o.customer_id = u.id
            JOIN order_items oi ON oi.order_id = o.id
            WHERE o.customer_id = ?
            GROUP BY o.id, u.name
            ORDER BY o.created_at DESC
            """,
            orderListRowMapper, customerId);
    }
}
```

---

## Interview Questions

1. **What is CQRS?**
   - Separate write model (commands that change state) from read model (queries that return data). Different models optimized for different purposes. Can use same or different databases.

2. **CQRS + Event Sourcing — how do they work together?**
   - Event Sourcing provides the events that sync the read model. Write side appends events to event store. Events published to bus. Projections consume events and build read models. Natural fit but not required.

3. **When is CQRS overkill?**
   - Simple CRUD apps. Low traffic. Reads and writes have same shape. Small team without distributed systems experience. Adding CQRS to a simple app adds unnecessary complexity.

4. **How to handle eventual consistency?**
   - Accept it in UI (show "processing"). Return write response directly. Subscribe for confirmation. Design UX around async (optimistic UI). Monitor projection lag.

5. **Can you use CQRS without Event Sourcing?**
   - Yes! Simple CQRS: Same DB, different models for reading and writing. Medium: Read replicas with denormalized views. Don't need event sourcing for CQRS benefits.

6. **How to rebuild a read model?**
   - If using Event Sourcing: replay all events from event store. Without ES: re-sync from write DB. This is why projections are disposable — they can always be rebuilt.

---

## Common Mistakes

1. **CQRS everywhere** — Not every service needs CQRS
2. **Ignoring eventual consistency** — UI shows stale data confusing users
3. **Complex read model updates** — Projections should be simple event handlers
4. **Coupling command and query models** — They should evolve independently
5. **No monitoring of sync lag** — Read model silently falls behind
6. **Using CQRS for simple CRUD** — Adds complexity with no benefit

---

## Best Practices

1. **Start simple** — Same DB, different DTOs before going full CQRS
2. **Monitor projection lag** — Alert if read model > N seconds behind
3. **Idempotent projections** — Handle duplicate events safely
4. **Rebuild capability** — Be able to rebuild any read model from scratch
5. **Match read model to query needs** — One projection per query pattern
6. **Eventual consistency in UI** — Design UX to handle delay gracefully
7. **Keep projections simple** — Just transform events to read model, no business logic
