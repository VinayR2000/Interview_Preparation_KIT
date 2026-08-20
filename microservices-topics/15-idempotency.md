# 15. Idempotency ⭐⭐⭐⭐⭐

## Theory

An operation is idempotent if performing it multiple times produces the same result as performing it once. In distributed systems, messages can be delivered more than once (retries, network issues, consumer restarts). Idempotency ensures duplicate processing doesn't cause incorrect behavior.

### Why Idempotency is Critical:
- Network retries send same request twice
- Kafka consumers may reprocess messages (rebalance, crash)
- Outbox pattern can publish duplicate events
- Client retries on timeout (server may have already processed)
- Message brokers provide at-least-once delivery (not exactly-once)

### Exactly-Once Misconception:
- True exactly-once delivery is extremely hard (practically impossible across systems)
- What we actually achieve: at-least-once delivery + idempotent processing = effectively exactly-once

### Naturally Idempotent Operations:
- SET status = 'PAID' (same result no matter how many times)
- DELETE WHERE id = 123 (already deleted = no effect)
- PUT /resource/123 with full replacement

### NOT Naturally Idempotent:
- INSERT INTO payments (creates duplicate records)
- INCREMENT balance by $50 (doubles the amount)
- POST /orders (creates duplicate orders)

---

## Internal Working

### The Problem Without Idempotency:

```
┌────────────────────────────────────────────────────────┐
│ WITHOUT IDEMPOTENCY                                     │
│                                                         │
│ Payment Request: {orderId: 101, amount: $50}           │
│                                                         │
│ Attempt 1:                                             │
│   Client → Payment Service → Charge $50 ✓             │
│   Payment Service → Client (response lost in network) │
│   Client thinks it failed!                            │
│                                                         │
│ Attempt 2 (retry):                                    │
│   Client → Payment Service → Charge $50 AGAIN ✓      │
│   Response received                                    │
│                                                         │
│ Result: Customer charged $100 instead of $50! 💀       │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ WITH IDEMPOTENCY KEY                                    │
│                                                         │
│ Payment Request: {orderId: 101, amount: $50,           │
│                   idempotencyKey: "pay-101-abc123"}    │
│                                                         │
│ Attempt 1:                                             │
│   Check: "pay-101-abc123" processed before? NO        │
│   → Process payment → Charge $50 ✓                    │
│   → Store: "pay-101-abc123" = COMPLETED               │
│   Response lost...                                     │
│                                                         │
│ Attempt 2 (retry):                                    │
│   Check: "pay-101-abc123" processed before? YES!      │
│   → Return cached response (don't charge again)       │
│                                                         │
│ Result: Customer charged exactly $50 ✓                 │
└────────────────────────────────────────────────────────┘
```

### Idempotency Strategies:

```
STRATEGY 1: Idempotency Key (most common)
┌─────────────────────────────────────────────┐
│ Request includes unique key                  │
│ Server checks if key was processed before   │
│                                              │
│ processed_requests table:                   │
│ ┌────────────────────┬────────┬──────────┐ │
│ │ idempotency_key    │ status │ response │ │
│ ├────────────────────┼────────┼──────────┤ │
│ │ pay-101-abc123     │ DONE   │ {payId:1}│ │
│ │ pay-102-def456     │ DONE   │ {payId:2}│ │
│ └────────────────────┴────────┴──────────┘ │
└─────────────────────────────────────────────┘

STRATEGY 2: Database Unique Constraint
┌─────────────────────────────────────────────┐
│ Natural business key as unique constraint   │
│                                              │
│ payments table:                             │
│ UNIQUE(order_id, payment_type)              │
│                                              │
│ Second INSERT → unique violation            │
│ → Catch exception → return existing record  │
└─────────────────────────────────────────────┘

STRATEGY 3: Conditional Update (Optimistic)
┌─────────────────────────────────────────────┐
│ UPDATE orders                               │
│ SET status = 'PAID'                         │
│ WHERE id = 101 AND status = 'PENDING'       │
│                                              │
│ If already PAID → WHERE doesn't match      │
│ → 0 rows updated → no duplicate effect     │
└─────────────────────────────────────────────┘

STRATEGY 4: Event Deduplication (Consumers)
┌─────────────────────────────────────────────┐
│ Consumer tracks processed event IDs         │
│                                              │
│ processed_events table:                     │
│ ┌────────────────────────────────────────┐ │
│ │ event_id (PK)    │ processed_at        │ │
│ ├────────────────────────────────────────┤ │
│ │ evt-001           │ 2024-01-15 10:30   │ │
│ │ evt-002           │ 2024-01-15 10:31   │ │
│ └────────────────────────────────────────┘ │
│                                              │
│ Before processing: check if event_id exists │
│ If exists → skip (already processed)       │
└─────────────────────────────────────────────┘
```

---

## Diagram

```
Idempotent Payment Flow:

Client
  │
  │ POST /api/payments
  │ Headers: Idempotency-Key: "key-123"
  │ Body: {orderId: 101, amount: $50}
  │
  ↓
┌─────────────────────────────────────┐
│          Payment Service            │
│                                     │
│  1. Extract Idempotency-Key         │
│                                     │
│  2. Check processed_requests table  │
│     ┌──────────────────────────┐   │
│     │ SELECT * FROM processed  │   │
│     │ WHERE key = 'key-123'    │   │
│     └──────────┬───────────────┘   │
│                │                    │
│     ┌──── Found? ────┐            │
│     │                 │            │
│    YES               NO            │
│     │                 │            │
│     ↓                 ↓            │
│  Return cached    Process payment  │
│  response         Save to table    │
│  (no re-charge)   Return response  │
│                                     │
└─────────────────────────────────────┘
```

---

## Code

### Idempotency Key Implementation:

```java
@Service
@Slf4j
public class IdempotentPaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyStore idempotencyStore;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PaymentResponse processPayment(
            String idempotencyKey, PaymentRequest request) {
        
        // Step 1: Check if already processed
        Optional<IdempotencyRecord> existing = idempotencyStore.find(idempotencyKey);
        
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Idempotent hit: key={} already processed", idempotencyKey);
                return record.getResponse();  // Return cached response
            }
            
            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                // Another thread is processing — wait or reject
                throw new ConflictException("Request is being processed");
            }
        }

        // Step 2: Mark as in-progress (prevents concurrent duplicates)
        idempotencyStore.save(IdempotencyRecord.builder()
            .key(idempotencyKey)
            .status(IdempotencyStatus.IN_PROGRESS)
            .createdAt(Instant.now())
            .build());

        try {
            // Step 3: Process payment
            Payment payment = paymentGateway.charge(
                request.getAmount(), request.getCustomerId());
            
            paymentRepository.save(payment);
            
            PaymentResponse response = PaymentResponse.builder()
                .paymentId(payment.getId())
                .status("COMPLETED")
                .amount(request.getAmount())
                .build();

            // Step 4: Store result for future duplicate requests
            idempotencyStore.save(IdempotencyRecord.builder()
                .key(idempotencyKey)
                .status(IdempotencyStatus.COMPLETED)
                .response(response)
                .completedAt(Instant.now())
                .build());

            return response;
            
        } catch (Exception e) {
            // Mark as failed — allows retry with same key
            idempotencyStore.save(IdempotencyRecord.builder()
                .key(idempotencyKey)
                .status(IdempotencyStatus.FAILED)
                .errorMessage(e.getMessage())
                .build());
            throw e;
        }
    }
}
```

### Controller with Idempotency Header:

```java
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        PaymentResponse response = paymentService.processPayment(idempotencyKey, request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .header("Idempotency-Key", idempotencyKey)
            .body(response);
    }
}
```

### Database Unique Constraint Approach:

```java
@Service
public class OrderPaymentService {

    @Transactional
    public Payment processOrderPayment(UUID orderId, BigDecimal amount) {
        try {
            // Unique constraint: (order_id, payment_type) ensures one payment per order
            Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .paymentType(PaymentType.ORDER_PAYMENT)
                .status(PaymentStatus.COMPLETED)
                .build();
            
            return paymentRepository.save(payment);
            
        } catch (DataIntegrityViolationException e) {
            // Duplicate! Return existing payment
            log.info("Payment already exists for order {}", orderId);
            return paymentRepository.findByOrderIdAndPaymentType(
                orderId, PaymentType.ORDER_PAYMENT)
                .orElseThrow();
        }
    }
}
```

### Idempotent Kafka Consumer:

```java
@Service
public class IdempotentOrderConsumer {

    private final ProcessedEventRepository processedEventRepo;
    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        String eventId = event.getEventId().toString();

        // Idempotency check
        if (processedEventRepo.existsById(eventId)) {
            log.debug("Event {} already processed, skipping", eventId);
            return;
        }

        // Process
        inventoryService.reserveStock(event.getOrderId(), event.getItems());

        // Mark processed (same transaction as business logic)
        processedEventRepo.save(new ProcessedEvent(
            eventId, 
            "OrderCreatedEvent", 
            Instant.now()
        ));
    }
}
```

### Conditional Update (State-Based Idempotency):

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Only updates if current status matches expected
    // Returns 0 if already transitioned → no duplicate effect
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :now " +
           "WHERE o.id = :orderId AND o.status = :expectedStatus")
    int updateStatusIfCurrent(
        @Param("orderId") UUID orderId,
        @Param("expectedStatus") OrderStatus expectedStatus,
        @Param("newStatus") OrderStatus newStatus,
        @Param("now") Instant now
    );
}

@Service
public class OrderStateService {

    public boolean confirmOrder(UUID orderId) {
        int updated = orderRepository.updateStatusIfCurrent(
            orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED, Instant.now());
        
        if (updated == 0) {
            log.info("Order {} not in PENDING state, skipping confirm", orderId);
            return false;  // Already confirmed or in different state
        }
        return true;
    }
}
```

---

## Interview Questions

1. **What is idempotency and why is it important in microservices?**
   - An operation that produces the same result regardless of how many times it's executed. Critical because distributed systems have retries, message redelivery, and network duplicates. Without idempotency, duplicate payments, double inventory deductions, etc.

2. **How to implement idempotency for a payment API?**
   - Client sends unique Idempotency-Key header. Server checks if key was processed before. If yes, return cached response. If no, process and store result keyed by idempotency key. Both check and store in same transaction.

3. **What is the "exactly-once" misconception?**
   - True exactly-once delivery across distributed systems is practically impossible. What we achieve: at-least-once delivery (guaranteed delivery, possibly duplicates) + idempotent consumer (handles duplicates) = effectively exactly-once processing.

4. **Idempotency key vs database unique constraint?**
   - Idempotency key: Explicit, client-controlled, can cache response. Unique constraint: Implicit, based on business rules, simpler but can't return original response. Use idempotency key for APIs, unique constraints for internal operations.

5. **How to handle concurrent duplicate requests?**
   - First request marks IN_PROGRESS. Second request sees IN_PROGRESS → rejects with 409 Conflict (client retries later). Or: Use database row-level lock on idempotency key.

6. **How long to keep idempotency records?**
   - Depends on retry window. Typically 24-48 hours. After that, same key could be reused (unlikely in practice with UUIDs). Scheduled cleanup of old records.

---

## Common Mistakes

1. **No idempotency on POST endpoints** — Retries create duplicates
2. **Checking idempotency outside transaction** — Race condition between check and write
3. **Not caching the response** — Idempotent but returns different response on retry
4. **Client not sending idempotency key** — Endpoint can't detect duplicates
5. **Using timestamps as idempotency key** — Not unique enough
6. **Forgetting Kafka consumer idempotency** — Events processed twice on rebalance

---

## Best Practices

1. **Client-generated idempotency keys** — UUID or hash of request params
2. **Store key + response** — Return same response on duplicate
3. **Transaction boundary** — Check + process + store in one transaction
4. **TTL on stored keys** — Clean up old records to prevent table growth
5. **IN_PROGRESS state** — Handle concurrent duplicate requests
6. **Log duplicates** — Monitor duplicate rate (might indicate client issues)
7. **All writes must be idempotent** — POST, event consumers, background jobs
