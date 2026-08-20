# 12. Event Sourcing

## Theory

Event Sourcing stores state as a sequence of events rather than the current state. Instead of updating a record, you append a new event describing what happened. Current state is derived by replaying all events.

### Traditional vs Event Sourcing:

| Aspect | Traditional (CRUD) | Event Sourcing |
|--------|-------------------|----------------|
| Storage | Current state only | All events (history) |
| Update | Overwrite record | Append new event |
| History | Lost (unless audit log) | Complete audit trail |
| Recovery | Restore from backup | Replay events |
| Debugging | "What happened?" is hard | Full history available |

### Key Concepts:
- **Event Store**: Append-only log of events
- **Aggregate**: Entity whose state is built from events
- **Projection**: Read-optimized view built from events
- **Snapshot**: Periodic state checkpoint (optimization)

---

## Internal Working

### How State is Built from Events:

```
TRADITIONAL (Current State):
┌──────────────────────────────────┐
│ orders table                      │
│                                   │
│ id=101, status=SHIPPED, total=$50│
│                                   │
│ (We don't know HOW it got here)  │
└──────────────────────────────────┘

EVENT SOURCING (Event History):
┌──────────────────────────────────────────────────┐
│ Event Store (order-101)                           │
│                                                   │
│ Event 1: OrderCreated {total: $50, items: [...]} │
│ Event 2: PaymentReceived {amount: $50}           │
│ Event 3: OrderConfirmed {}                       │
│ Event 4: ItemShipped {trackingId: "TR123"}       │
│                                                   │
│ Current state = replay(Event1 + Event2 + Event3 + Event4)│
│ Result: {status: SHIPPED, total: $50, tracking: "TR123"} │
└──────────────────────────────────────────────────┘
```

### Event Replay to Build State:

```
Replaying events for Order #101:

Initial state: {} (empty)

Apply Event 1: OrderCreated
  state = {id: 101, status: PENDING, items: ["Book"], total: $50}

Apply Event 2: PaymentReceived  
  state = {id: 101, status: PAID, items: ["Book"], total: $50, paid: $50}

Apply Event 3: OrderConfirmed
  state = {id: 101, status: CONFIRMED, items: ["Book"], total: $50, paid: $50}

Apply Event 4: ItemShipped
  state = {id: 101, status: SHIPPED, items: ["Book"], total: $50, 
           paid: $50, tracking: "TR123"}

Final state (current): Order is SHIPPED with tracking TR123
```

### Snapshots for Performance:

```
Problem: Order with 10,000 events → replay takes too long

Solution: Periodic snapshots

Event 1 ──→ Event 100 ──→ [SNAPSHOT at event 100]
                            state = {...current state...}

Event 101 ──→ Event 200 ──→ [SNAPSHOT at event 200]

To get current state:
  1. Load latest snapshot (event 200 state)
  2. Replay events 201 → current
  
  Instead of replaying 10,000 events
  Replay only events since last snapshot
```

---

## Diagram

```
Event Sourcing Architecture:

┌─────────────────────────────────────────────────────────┐
│                                                          │
│  Commands (Write Side)        Queries (Read Side)       │
│                                                          │
│  ┌──────────┐                ┌──────────────────┐      │
│  │ Command  │                │   Projections     │      │
│  │ Handler  │                │                   │      │
│  └────┬─────┘                │ ┌──────────────┐ │      │
│       │                      │ │Order Summary │ │      │
│       │ validate             │ │  (read model)│ │      │
│       │ + append             │ └──────────────┘ │      │
│       ↓                      │ ┌──────────────┐ │      │
│  ┌──────────────┐           │ │User Orders   │ │      │
│  │  Event Store │           │ │  (read model)│ │      │
│  │ (append-only)│           │ └──────────────┘ │      │
│  │              │           │ ┌──────────────┐ │      │
│  │ OrderCreated │──events──→│ │Analytics     │ │      │
│  │ PaymentRecvd │           │ │  (read model)│ │      │
│  │ OrderShipped │           │ └──────────────┘ │      │
│  └──────────────┘           └──────────────────┘      │
│                                                          │
│  Event Store is the source of truth                     │
│  Projections are derived/disposable views               │
│  Can rebuild any projection by replaying events         │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Code

### Event Store Entity:

```java
@Entity
@Table(name = "event_store")
public class StoredEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sequenceNumber;
    
    private UUID aggregateId;
    private String aggregateType;  // "Order", "Payment"
    private int version;           // Ordering within aggregate
    private String eventType;      // "OrderCreated", "PaymentReceived"
    
    @Column(columnDefinition = "jsonb")
    private String eventData;      // JSON payload
    
    private Instant occurredAt;
    private String metadata;       // Correlation ID, user, etc.
}
```

### Aggregate with Event Sourcing:

```java
public class Order {
    private UUID id;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItem> items;
    private String trackingId;
    private int version;

    // State changes tracked as events
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    // Command: Create order
    public static Order create(UUID id, String customerId, List<OrderItem> items) {
        Order order = new Order();
        BigDecimal total = items.stream()
            .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Don't set state directly — raise event
        order.apply(new OrderCreatedEvent(id, customerId, items, total));
        return order;
    }

    // Command: Confirm payment
    public void confirmPayment(BigDecimal amount) {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm payment for " + status + " order");
        }
        apply(new PaymentReceivedEvent(id, amount));
    }

    // Command: Ship order
    public void ship(String trackingId) {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot ship " + status + " order");
        }
        apply(new OrderShippedEvent(id, trackingId));
    }

    // Apply event — mutates state
    private void apply(DomainEvent event) {
        mutate(event);  // Change state
        uncommittedEvents.add(event);  // Track for persistence
        version++;
    }

    // Rebuild state from event history
    public static Order fromEvents(List<DomainEvent> events) {
        Order order = new Order();
        for (DomainEvent event : events) {
            order.mutate(event);
            order.version++;
        }
        return order;
    }

    // State mutation based on event type
    private void mutate(DomainEvent event) {
        switch (event) {
            case OrderCreatedEvent e -> {
                this.id = e.getOrderId();
                this.items = e.getItems();
                this.totalAmount = e.getTotalAmount();
                this.status = OrderStatus.PENDING;
            }
            case PaymentReceivedEvent e -> {
                this.status = OrderStatus.CONFIRMED;
            }
            case OrderShippedEvent e -> {
                this.status = OrderStatus.SHIPPED;
                this.trackingId = e.getTrackingId();
            }
            default -> throw new IllegalArgumentException("Unknown event: " + event.getClass());
        }
    }

    public List<DomainEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }

    public void markCommitted() {
        uncommittedEvents.clear();
    }
}
```

### Event Store Repository:

```java
@Repository
public class EventStoreRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // Load aggregate from events
    public Order loadOrder(UUID orderId) {
        List<DomainEvent> events = jdbcTemplate.query(
            "SELECT * FROM event_store WHERE aggregate_id = ? ORDER BY version",
            (rs, rowNum) -> deserializeEvent(rs),
            orderId
        );
        
        if (events.isEmpty()) {
            throw new AggregateNotFoundException("Order " + orderId + " not found");
        }
        
        return Order.fromEvents(events);
    }

    // Save new events (optimistic locking via version)
    @Transactional
    public void save(Order order) {
        for (DomainEvent event : order.getUncommittedEvents()) {
            jdbcTemplate.update(
                """
                INSERT INTO event_store (aggregate_id, aggregate_type, version, 
                                        event_type, event_data, occurred_at)
                VALUES (?, 'Order', ?, ?, ?::jsonb, ?)
                """,
                order.getId(),
                order.getVersion(),
                event.getClass().getSimpleName(),
                objectMapper.writeValueAsString(event),
                Instant.now()
            );
        }
        order.markCommitted();
    }
}
```

### Projection (Read Model):

```java
@Service
public class OrderSummaryProjection {

    private final OrderSummaryRepository summaryRepo;

    // Listen to events and update read model
    @KafkaListener(topics = "order-events", groupId = "order-summary-projection")
    public void handle(DomainEvent event) {
        switch (event) {
            case OrderCreatedEvent e -> {
                OrderSummary summary = new OrderSummary();
                summary.setOrderId(e.getOrderId());
                summary.setCustomerId(e.getCustomerId());
                summary.setTotal(e.getTotalAmount());
                summary.setStatus("PENDING");
                summary.setCreatedAt(e.getOccurredAt());
                summaryRepo.save(summary);
            }
            case PaymentReceivedEvent e -> {
                summaryRepo.updateStatus(e.getOrderId(), "CONFIRMED");
            }
            case OrderShippedEvent e -> {
                summaryRepo.updateStatusAndTracking(
                    e.getOrderId(), "SHIPPED", e.getTrackingId());
            }
        }
    }
}
```

---

## Interview Questions

1. **What is Event Sourcing?**
   - Instead of storing current state, store all state-changing events. Current state derived by replaying events. Provides complete audit trail, enables event replay, supports temporal queries ("what was the state at time X?").

2. **Event Sourcing vs Event-Driven Architecture?**
   - EDA: Services communicate via events (integration pattern). Event Sourcing: Store state AS events (persistence pattern). You can use EDA without event sourcing, and vice versa. Often used together.

3. **When to use Event Sourcing?**
   - Audit requirements (finance, compliance). Complex domains with frequent state changes. Need to rebuild state at any point in time. Want to build multiple read models from same events.

4. **What are the downsides?**
   - Complexity. Eventual consistency. Event schema evolution is hard. Replay can be slow without snapshots. Harder to query (need projections). Steeper learning curve.

5. **What is a Projection?**
   - Read-optimized view built from events. Denormalized for specific query patterns. Disposable — can be rebuilt by replaying events. Multiple projections from same event stream.

6. **How to handle event schema evolution?**
   - Upcasters: transform old event format to new. Versioned events. Backward-compatible changes (add optional fields). Never delete or rename fields in existing events.

---

## Common Mistakes

1. **Using event sourcing for simple CRUD** — Massive overkill
2. **No snapshots** — Replay becomes slow over time
3. **Mutable events** — Events must be immutable once stored
4. **Breaking event schema** — Existing events can't be replayed
5. **Coupling projections to event store** — Projections should be independent
6. **Not handling concurrency** — Optimistic locking on aggregate version is essential

---

## Best Practices

1. **Events are immutable** — Never modify or delete stored events
2. **Snapshots** — Take periodic snapshots for long-lived aggregates
3. **Small aggregates** — Fewer events per aggregate = faster replay
4. **Schema versioning** — Use upcasters for backward compatibility
5. **Separate event store from projections** — Different scaling needs
6. **Idempotent projections** — Handle replayed events gracefully
7. **Monitor projection lag** — Alert if read model falls behind
