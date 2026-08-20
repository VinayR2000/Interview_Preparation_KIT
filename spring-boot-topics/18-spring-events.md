# 18. Spring Events

## Theory

Spring Events implement the **Observer/Publish-Subscribe pattern** within a Spring application. They allow loose coupling between components — a publisher emits an event without knowing who will handle it, and multiple listeners can react independently.

### Core Components:
- **ApplicationEvent**: Base class for custom events (optional in modern Spring)
- **ApplicationEventPublisher**: Interface to publish events
- **@EventListener**: Annotation to mark event handler methods
- **@TransactionalEventListener**: Listener that executes in transaction phase

### Event Types:
- **Synchronous** (default): Listeners execute in the same thread as publisher
- **Asynchronous** (@Async): Listeners execute in separate threads
- **Transactional**: Listeners execute after transaction commit/rollback

### Built-in Spring Events:
- ContextRefreshedEvent, ContextStartedEvent, ContextStoppedEvent, ContextClosedEvent
- ApplicationReadyEvent, ApplicationStartedEvent

---

## Internal Working

```
Publisher calls applicationEventPublisher.publishEvent(event)
       ↓
ApplicationEventMulticaster (SimpleApplicationEventMulticaster)
       ↓
Resolves all listeners matching the event type
       ↓
┌─────────────────────────────────────────────┐
│ For each matching listener:                  │
│                                              │
│ Synchronous (default):                       │
│   → Invoke listener in SAME thread           │
│   → Publisher WAITS for all listeners        │
│                                              │
│ Asynchronous (@Async):                       │
│   → Submit to TaskExecutor                   │
│   → Publisher does NOT wait                  │
│                                              │
│ Transactional (@TransactionalEventListener): │
│   → Register to execute AFTER tx phase       │
│   → AFTER_COMMIT (default)                   │
│   → BEFORE_COMMIT, AFTER_ROLLBACK,           │
│     AFTER_COMPLETION                         │
└─────────────────────────────────────────────┘
```

### Event Resolution:
```
publishEvent(OrderCreatedEvent)
       ↓
ApplicationEventMulticaster scans all beans
       ↓
Finds methods annotated with @EventListener
  where parameter type matches OrderCreatedEvent
  (or supertype — supports inheritance)
       ↓
Invokes all matching listeners
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    ORDER SERVICE                          │
│                                                          │
│  orderRepository.save(order);                            │
│  eventPublisher.publishEvent(new OrderCreatedEvent(...)) │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ↓
              ┌──────────────────────────────┐
              │  ApplicationEventMulticaster   │
              └──────────────┬───────────────┘
                             │
              ┌──────────────┼──────────────────┐
              ↓              ↓                   ↓
┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐
│ EmailService     │ │ AuditService │ │ NotificationSvc  │
│                  │ │              │ │                   │
│ @EventListener   │ │ @EventListener│ │ @EventListener   │
│ sendConfirmation │ │ logAudit()   │ │ pushNotify()     │
└──────────────────┘ └──────────────┘ └──────────────────┘

Benefits:
- OrderService doesn't know about listeners
- Add/remove listeners without changing OrderService
- Each listener handles its own concern
```

---

## Code

### Event Definition:

```java
// Modern approach - any POJO works as event
public record OrderCreatedEvent(
    Long orderId,
    Long customerId,
    BigDecimal totalAmount,
    List<OrderItem> items,
    Instant createdAt
) {}

// Traditional approach - extends ApplicationEvent
public class OrderCreatedEvent extends ApplicationEvent {
    private final Long orderId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(Object source, Long orderId, BigDecimal totalAmount) {
        super(source);
        this.orderId = orderId;
        this.totalAmount = totalAmount;
    }
}
```

### Publisher:

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
            .customerId(request.getCustomerId())
            .items(request.getItems())
            .totalAmount(calculateTotal(request.getItems()))
            .status(OrderStatus.CREATED)
            .build();

        Order savedOrder = orderRepository.save(order);

        // Publish event - listeners handle side effects
        eventPublisher.publishEvent(new OrderCreatedEvent(
            savedOrder.getId(),
            savedOrder.getCustomerId(),
            savedOrder.getTotalAmount(),
            savedOrder.getItems(),
            Instant.now()
        ));

        return savedOrder;
    }
}
```

### Synchronous Listeners:

```java
@Component
@Slf4j
public class OrderEventListeners {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Order {} created for customer {}",
            event.orderId(), event.customerId());
    }

    // Conditional listener
    @EventListener(condition = "#event.totalAmount > 1000")
    public void handleHighValueOrder(OrderCreatedEvent event) {
        log.info("High-value order detected: {}", event.orderId());
        // Notify sales team
    }

    // Returns another event (event chaining)
    @EventListener
    public InventoryReservedEvent handleInventoryReservation(OrderCreatedEvent event) {
        // Reserve inventory
        return new InventoryReservedEvent(event.orderId(), event.items());
    }
}
```

### Transactional Event Listener:

```java
@Component
@Slf4j
public class OrderTransactionalListeners {

    private final EmailService emailService;
    private final NotificationService notificationService;

    // Executes ONLY after transaction commits successfully
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendOrderConfirmation(OrderCreatedEvent event) {
        emailService.sendOrderConfirmation(event.customerId(), event.orderId());
    }

    // Executes after rollback (compensation logic)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleOrderFailure(OrderCreatedEvent event) {
        notificationService.notifyOrderFailed(event.orderId());
    }

    // Default: AFTER_COMMIT
    @TransactionalEventListener
    public void updateAnalytics(OrderCreatedEvent event) {
        // Only runs if order was actually saved
        analyticsService.recordOrder(event);
    }
}
```

### Async Event Listeners:

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("eventExecutor")
    public TaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("event-");
        return executor;
    }
}

@Component
public class AsyncOrderListeners {

    @Async("eventExecutor")
    @EventListener
    public void sendEmail(OrderCreatedEvent event) {
        // Runs in separate thread - doesn't block publisher
        emailService.sendOrderConfirmation(event);
    }

    @Async("eventExecutor")
    @EventListener
    public void updateSearchIndex(OrderCreatedEvent event) {
        searchService.indexOrder(event);
    }
}
```

### Ordered Listeners:

```java
@Component
public class OrderedListeners {

    @EventListener
    @Order(1)  // Executes first
    public void validateOrder(OrderCreatedEvent event) {
        // Validation logic
    }

    @EventListener
    @Order(2)  // Executes second
    public void processPayment(OrderCreatedEvent event) {
        // Payment logic
    }

    @EventListener
    @Order(3)  // Executes third
    public void sendNotification(OrderCreatedEvent event) {
        // Notification logic
    }
}
```

---

## Dry Run

### Scenario: Order creation with multiple listeners

```
1. OrderService.createOrder() called
2. Order saved to DB (within @Transactional)
3. publishEvent(OrderCreatedEvent{orderId=42, amount=1500})

4. SimpleApplicationEventMulticaster resolves listeners:
   - AuditListener.logAudit() [sync]
   - HighValueListener.notify() [sync, condition: amount > 1000 → TRUE]
   - EmailListener.sendEmail() [async]
   - AnalyticsListener.update() [@TransactionalEventListener AFTER_COMMIT]

5. Synchronous execution (same thread):
   a. AuditListener.logAudit() → logs "Order 42 created"
   b. HighValueListener.notify() → condition matches, notifies sales

6. Async execution (separate thread):
   c. EmailListener.sendEmail() → submitted to thread pool (non-blocking)

7. Transaction commits successfully

8. Transactional listeners fire:
   d. AnalyticsListener.update() → records order in analytics

9. createOrder() returns to caller
```

### If transaction ROLLS BACK:
```
- Steps 5a, 5b, 5c still execute (sync/async listeners already ran)
- Step 8: AnalyticsListener does NOT execute (AFTER_COMMIT)
- AFTER_ROLLBACK listeners would execute instead
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| Publishing event | O(n) where n = number of matching listeners |
| Listener resolution (first time) | O(m) where m = total @EventListener methods |
| Listener resolution (cached) | O(1) lookup |
| Async listener dispatch | O(1) - just queue submission |

---

## Real Project Usage

### Domain Event Pattern (DDD):

```java
// Base domain event
public abstract class DomainEvent {
    private final Instant occurredAt = Instant.now();
    private final String eventId = UUID.randomUUID().toString();
}

// Aggregate publishes events
@Entity
public class Order {
    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public void place() {
        this.status = OrderStatus.PLACED;
        domainEvents.add(new OrderPlacedEvent(this.id, this.customerId));
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearEvents() {
        domainEvents.clear();
    }
}

// Service publishes after save
@Service
public class OrderService {
    @Transactional
    public void placeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.place();
        orderRepository.save(order);
        
        order.getDomainEvents().forEach(eventPublisher::publishEvent);
        order.clearEvents();
    }
}
```

---

## Interview Questions

1. **What are Spring Events and when would you use them?**
   - In-app pub/sub mechanism for decoupling components. Use when action triggers multiple side effects (email, audit, notifications) without coupling.

2. **Difference between @EventListener and @TransactionalEventListener?**
   - @EventListener executes immediately when event is published. @TransactionalEventListener waits for transaction phase (AFTER_COMMIT by default).

3. **Are Spring Events synchronous or asynchronous?**
   - Synchronous by default (same thread). Can be made async with @Async + @EnableAsync. @TransactionalEventListener is still sync but deferred.

4. **What happens if a listener throws an exception?**
   - Synchronous: Exception propagates to publisher, can roll back transaction. Async: Exception is handled by AsyncUncaughtExceptionHandler.

5. **How are Spring Events different from Kafka/RabbitMQ?**
   - Spring Events are in-process (same JVM). Message brokers are inter-process (between services). Spring Events don't survive application restart.

---

## Follow-up Questions

1. How do you ensure event ordering when multiple listeners exist?
   - Use @Order annotation on listener methods. Lower value = executes first. Without @Order, order is non-deterministic. Consider using a single listener that orchestrates if strict ordering is critical.

2. How to handle failures in @TransactionalEventListener?
   - Exception in AFTER_COMMIT listener doesn't roll back (tx already committed). Log the error, publish to DLQ, or retry via scheduled job. Consider making listeners idempotent for retry safety.

3. Can Spring Events replace a message broker in microservices?
   - No. Spring Events are JVM-local (same process only). For inter-service communication, use Kafka/RabbitMQ. Spring Events work within a single service for decoupling internal components.

4. How to test event publishing and listening?
   - Use @SpyBean on listener to verify invocation. Or use ApplicationEvents (Spring test utility) to capture published events. For integration: @SpringBootTest and verify side effects.

5. What's the Outbox pattern and how does it relate to domain events?
   - Store events in an outbox DB table within the same transaction as the business operation. A separate process reads outbox and publishes to Kafka. Guarantees event delivery even if Kafka is temporarily down.

---

## Common Mistakes

1. **Heavy logic in synchronous listeners** - Blocks the publisher thread
2. **Expecting @TransactionalEventListener without @Transactional on publisher** - Listener won't fire (no transaction to observe)
3. **Not handling async listener exceptions** - Exceptions silently lost
4. **Circular event publishing** - Event A triggers listener that publishes Event B which triggers Event A
5. **Relying on Spring Events for inter-service communication** - Won't work across JVMs
6. **Mutating event objects** - Events should be immutable (use records)

---

## Best Practices

1. **Use records/immutable objects** for events
2. **Use @TransactionalEventListener for side effects** (email, notifications) to avoid sending on rollback
3. **Use @Async for non-critical listeners** (analytics, search indexing)
4. **Keep events focused** - One event per significant domain action
5. **Add correlation IDs** to events for tracing
6. **Use @Order** when listener sequence matters
7. **Consider Outbox pattern** for guaranteed delivery to external systems

---

## Production Considerations

- **Failure handling**: Synchronous listener failure can roll back publisher's transaction
- **Performance**: Too many synchronous listeners slow down the publisher
- **Async thread exhaustion**: Size thread pool appropriately for async listeners
- **Event loss**: Application crash between publish and async listener execution = lost event
- **Monitoring**: Track event publishing rate, listener execution time, failures
- **Scaling**: Spring Events are JVM-local. For multi-instance, use Kafka/RabbitMQ

---

## Related Topics

- Async Processing (@Async)
- Transactions (@TransactionalEventListener)
- Kafka (external event streaming)
- Domain-Driven Design (domain events)
- Microservices (event-driven architecture)
- Outbox Pattern
