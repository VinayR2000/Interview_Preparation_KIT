# 9. Data Management Patterns ⭐⭐⭐⭐⭐

## Theory

Data management in microservices is fundamentally different from monoliths. Each service owns its data, creating challenges around consistency, queries across services, and distributed transactions.

### Database Per Service:
- Each service has its own database instance
- No direct database access between services
- Data shared only via APIs or events
- Enables technology diversity (SQL vs NoSQL)

### Why Shared Database is Discouraged:
- Schema changes affect all services
- Can't deploy independently
- Single point of failure
- Technology lock-in
- Performance coupling

### Saga Pattern ⭐⭐⭐⭐⭐:
Manages distributed transactions without ACID guarantees. Instead of one transaction spanning multiple databases, uses a sequence of local transactions with compensating actions.

### Two Saga Approaches:
| Approach | Coordination | Best For |
|----------|-------------|----------|
| Choreography | Event-driven, no central coordinator | Simple flows (3-4 steps) |
| Orchestration | Central coordinator manages flow | Complex flows (5+ steps) |

---

## Internal Working

### Database Per Service:

```
┌────────────────────────────────────────────────────────┐
│ DATABASE PER SERVICE                                    │
│                                                         │
│ ┌───────────┐    ┌───────────┐    ┌───────────┐      │
│ │  Order    │    │  Payment  │    │  User     │      │
│ │  Service  │    │  Service  │    │  Service  │      │
│ └─────┬─────┘    └─────┬─────┘    └─────┬─────┘      │
│       │                │                │              │
│       ↓                ↓                ↓              │
│ ┌───────────┐    ┌───────────┐    ┌───────────┐      │
│ │ PostgreSQL│    │ PostgreSQL│    │  MongoDB  │      │
│ │           │    │           │    │           │      │
│ │ orders    │    │ payments  │    │ users     │      │
│ │ order_items│   │ refunds   │    │ profiles  │      │
│ └───────────┘    └───────────┘    └───────────┘      │
│                                                         │
│ Rules:                                                  │
│ ✗ Order Service CANNOT query payments table            │
│ ✗ Payment Service CANNOT query users table             │
│ ✓ Services communicate only via APIs/events            │
│ ✓ Each service can choose its own DB technology        │
└────────────────────────────────────────────────────────┘
```

### Saga Pattern — Choreography:

```
CREATE ORDER SAGA (Choreography):

┌──────────────────────────────────────────────────────┐
│ Happy Path (all steps succeed):                       │
│                                                       │
│ Order Service                                        │
│   │ Create order (PENDING)                           │
│   │ Publish: OrderCreatedEvent                       │
│   ↓                                                  │
│ ─────── Kafka ────────                               │
│   ↓                                                  │
│ Payment Service                                      │
│   │ Process payment                                  │
│   │ Publish: PaymentCompletedEvent                   │
│   ↓                                                  │
│ ─────── Kafka ────────                               │
│   ↓                                                  │
│ Inventory Service                                    │
│   │ Reserve stock                                    │
│   │ Publish: StockReservedEvent                      │
│   ↓                                                  │
│ ─────── Kafka ────────                               │
│   ↓                                                  │
│ Order Service                                        │
│   │ Update order → CONFIRMED                         │
│                                                       │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ Failure Path (stock unavailable):                    │
│                                                       │
│ Order Service → OrderCreatedEvent                    │
│   ↓                                                  │
│ Payment Service → PaymentCompletedEvent (SUCCESS)    │
│   ↓                                                  │
│ Inventory Service → StockReservationFailedEvent ✗    │
│   ↓                                                  │
│ ─────── Kafka (compensation events) ─────            │
│   ↓                                                  │
│ Payment Service                                      │
│   │ COMPENSATE: Refund payment                       │
│   │ Publish: PaymentRefundedEvent                    │
│   ↓                                                  │
│ Order Service                                        │
│   │ COMPENSATE: Cancel order → CANCELLED             │
│                                                       │
└──────────────────────────────────────────────────────┘
```

### Saga Pattern — Orchestration:

```
CREATE ORDER SAGA (Orchestration):

┌──────────────────────────────────────────────────────────┐
│                                                           │
│            ┌────────────────────────┐                    │
│            │   SAGA ORCHESTRATOR    │                    │
│            │   (Order Saga Service) │                    │
│            └───────────┬────────────┘                    │
│                        │                                  │
│   Step 1: ─────────── │ ──→ Order Service                │
│   "Create Order"      │        │ Create order (PENDING)  │
│                        │        │ Return: orderId         │
│                        │ ←──────┘                        │
│                        │                                  │
│   Step 2: ─────────── │ ──→ Payment Service              │
│   "Process Payment"   │        │ Charge customer         │
│                        │        │ Return: paymentId       │
│                        │ ←──────┘                        │
│                        │                                  │
│   Step 3: ─────────── │ ──→ Inventory Service            │
│   "Reserve Stock"     │        │ Reserve items           │
│                        │        │ Return: reservationId   │
│                        │ ←──────┘                        │
│                        │                                  │
│   Step 4: ─────────── │ ──→ Order Service                │
│   "Confirm Order"     │        │ Update → CONFIRMED      │
│                        │                                  │
│   If Step 3 FAILS: ── │ ──→ Compensate:                 │
│                        │      ├─ Refund Payment          │
│                        │      └─ Cancel Order            │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

## Diagram

```
Choreography vs Orchestration:

CHOREOGRAPHY:
- Each service knows what to do next
- Decentralized decision-making
- Events trigger next step
- Good for simple flows

  A ──event──→ B ──event──→ C ──event──→ D
  
  Pro: Simple, loosely coupled
  Con: Hard to track overall flow, circular dependencies possible

ORCHESTRATION:
- Central coordinator controls flow
- Centralized decision-making
- Commands trigger steps
- Good for complex flows

       ┌──────────┐
       │Orchestrator│
       └─┬──┬──┬──┘
         │  │  │
    cmd  │  │  │ cmd
         ↓  ↓  ↓
         A  B  C
  
  Pro: Easy to understand, modify, monitor
  Con: Single point of failure (mitigate with HA)
```

---

## Code

### Saga Choreography Implementation:

```java
// Order Service — starts the saga
@Service
@Transactional
public class OrderService {

    public Order createOrder(CreateOrderRequest request) {
        // Step 1: Create order in PENDING state
        Order order = Order.builder()
            .customerId(request.getCustomerId())
            .items(request.getItems())
            .status(OrderStatus.PENDING)
            .build();
        
        order = orderRepository.save(order);
        
        // Publish event to start saga
        eventPublisher.publish(new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotalAmount(),
            order.getItems()
        ));
        
        return order;
    }

    // Listen for saga completion/failure
    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void handlePaymentEvent(PaymentEvent event) {
        if (event instanceof PaymentCompletedEvent e) {
            // Payment succeeded — wait for inventory
            orderRepository.updateStatus(e.getOrderId(), OrderStatus.PAYMENT_CONFIRMED);
        } else if (event instanceof PaymentFailedEvent e) {
            // Payment failed — cancel order
            orderRepository.updateStatus(e.getOrderId(), OrderStatus.CANCELLED);
        }
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    public void handleInventoryEvent(InventoryEvent event) {
        if (event instanceof StockReservedEvent e) {
            orderRepository.updateStatus(e.getOrderId(), OrderStatus.CONFIRMED);
        } else if (event instanceof StockReservationFailedEvent e) {
            // Trigger compensation
            orderRepository.updateStatus(e.getOrderId(), OrderStatus.CANCELLED);
            // Payment service will listen and refund
        }
    }
}
```

```java
// Payment Service — participates in saga
@Service
public class PaymentService {

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            Payment payment = processPayment(event);
            eventPublisher.publish(new PaymentCompletedEvent(
                event.getOrderId(), payment.getId()));
        } catch (PaymentException e) {
            eventPublisher.publish(new PaymentFailedEvent(
                event.getOrderId(), e.getMessage()));
        }
    }

    // Compensation — listen for stock failure
    @KafkaListener(topics = "inventory-events", groupId = "payment-service")
    public void handleStockFailed(StockReservationFailedEvent event) {
        // Refund payment as compensation
        paymentRepository.findByOrderId(event.getOrderId())
            .ifPresent(payment -> {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                eventPublisher.publish(new PaymentRefundedEvent(
                    event.getOrderId(), payment.getId()));
            });
    }
}
```

### Saga Orchestration Implementation:

```java
// Saga Orchestrator
@Service
@Slf4j
public class CreateOrderSagaOrchestrator {

    private final OrderServiceClient orderClient;
    private final PaymentServiceClient paymentClient;
    private final InventoryServiceClient inventoryClient;
    private final SagaStateRepository sagaRepository;

    @Transactional
    public SagaResult executeSaga(CreateOrderRequest request) {
        SagaState saga = SagaState.builder()
            .sagaId(UUID.randomUUID())
            .status(SagaStatus.STARTED)
            .build();
        sagaRepository.save(saga);

        try {
            // Step 1: Create Order
            log.info("Saga {}: Creating order", saga.getSagaId());
            OrderResponse order = orderClient.createOrder(request);
            saga.setOrderId(order.getId());
            saga.setCurrentStep("ORDER_CREATED");
            sagaRepository.save(saga);

            // Step 2: Process Payment
            log.info("Saga {}: Processing payment", saga.getSagaId());
            PaymentResponse payment = paymentClient.processPayment(
                new PaymentRequest(order.getId(), order.getTotal()));
            saga.setPaymentId(payment.getId());
            saga.setCurrentStep("PAYMENT_PROCESSED");
            sagaRepository.save(saga);

            // Step 3: Reserve Inventory
            log.info("Saga {}: Reserving inventory", saga.getSagaId());
            inventoryClient.reserveStock(
                new StockReservationRequest(order.getId(), request.getItems()));
            saga.setCurrentStep("STOCK_RESERVED");
            sagaRepository.save(saga);

            // Step 4: Confirm Order
            orderClient.confirmOrder(order.getId());
            saga.setStatus(SagaStatus.COMPLETED);
            saga.setCurrentStep("COMPLETED");
            sagaRepository.save(saga);

            return SagaResult.success(order);

        } catch (Exception e) {
            log.error("Saga {} failed at step {}: {}", 
                saga.getSagaId(), saga.getCurrentStep(), e.getMessage());
            compensate(saga);
            saga.setStatus(SagaStatus.COMPENSATED);
            sagaRepository.save(saga);
            return SagaResult.failed(e.getMessage());
        }
    }

    private void compensate(SagaState saga) {
        log.info("Saga {}: Starting compensation", saga.getSagaId());

        // Compensate in reverse order
        switch (saga.getCurrentStep()) {
            case "STOCK_RESERVED":
                inventoryClient.releaseStock(saga.getOrderId());
                // Fall through to compensate previous steps
            case "PAYMENT_PROCESSED":
                paymentClient.refundPayment(saga.getPaymentId());
            case "ORDER_CREATED":
                orderClient.cancelOrder(saga.getOrderId());
                break;
        }
    }
}
```

### Saga State Entity:

```java
@Entity
@Table(name = "saga_state")
public class SagaState {
    @Id
    private UUID sagaId;
    
    @Enumerated(EnumType.STRING)
    private SagaStatus status;  // STARTED, COMPLETED, COMPENSATING, COMPENSATED, FAILED
    
    private String currentStep;
    private UUID orderId;
    private UUID paymentId;
    private String failureReason;
    
    @CreatedDate
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant updatedAt;
}
```

---

## Dry Run

### Order Saga — Failure Scenario:

```
Customer places order: {items: ["Book", "Pen"], total: $50}

1. Order Service:
   → Create order #101 (status: PENDING)
   → Publish: OrderCreatedEvent{orderId=101, total=$50}
   ✓ SUCCESS

2. Payment Service:
   → Receive OrderCreatedEvent
   → Charge customer $50
   → Publish: PaymentCompletedEvent{orderId=101, paymentId=P1}
   ✓ SUCCESS

3. Inventory Service:
   → Receive OrderCreatedEvent
   → Check stock for "Book" → Available ✓
   → Check stock for "Pen" → OUT OF STOCK ✗
   → Publish: StockReservationFailedEvent{orderId=101, reason="Pen out of stock"}
   ✗ FAILURE

4. COMPENSATION BEGINS:

5. Payment Service (listening to inventory-events):
   → Receive StockReservationFailedEvent
   → Refund $50 to customer
   → Publish: PaymentRefundedEvent{orderId=101}
   ✓ COMPENSATED

6. Order Service (listening to inventory-events):
   → Receive StockReservationFailedEvent
   → Update order #101 → CANCELLED
   ✓ COMPENSATED

Final state:
  Order: CANCELLED
  Payment: REFUNDED
  Inventory: No stock reserved
  Customer: Notified of cancellation + refund
```

---

## Interview Questions

1. **What is Saga pattern and why is it needed?**
   - Manages distributed transactions across microservices. Can't use ACID transactions across services (different databases). Saga = sequence of local transactions + compensating actions for rollback.

2. **Choreography vs Orchestration?**
   - Choreography: Event-driven, no coordinator, each service reacts and publishes. Simple but hard to track. Orchestration: Central coordinator tells each service what to do. Complex flows become easier to understand and debug.

3. **What are compensating transactions?**
   - Undo operations that reverse a previously completed step. Not a database rollback — it's a new business operation (e.g., refund payment, release stock, cancel order). Must be idempotent.

4. **How to handle saga failure in the middle?**
   - Execute compensating transactions in reverse order for all completed steps. Track saga state to know which steps to compensate. Ensure compensations are idempotent (might run multiple times).

5. **Database per service — how to query across services?**
   - API Composition: Call multiple services, aggregate in memory. CQRS: Maintain read-optimized view from events. Event-carried state: Store needed data locally via events.

---

## Common Mistakes

1. **Not tracking saga state** — Can't recover from coordinator crash
2. **Non-idempotent compensations** — Refund processed twice
3. **Circular event dependencies** — A triggers B triggers A
4. **Forgetting partial failures** — Some compensations might fail too
5. **Using sagas for simple CRUD** — Overkill for single-service operations
6. **Shared database "shortcut"** — Defeats microservices benefits

---

## Best Practices

1. **Track saga state** — Persist saga progress for recovery
2. **Idempotent steps** — Both forward and compensation operations
3. **Timeout sagas** — Don't let sagas hang forever
4. **Dead letter queue** — Handle permanently failed saga events
5. **Compensate in reverse** — Undo from last completed step backward
6. **Monitor saga duration** — Alert on long-running sagas
7. **Choose wisely** — Choreography for ≤4 steps, Orchestration for >4 steps
