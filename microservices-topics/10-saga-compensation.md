# 10. Saga Compensation

## Theory

In distributed systems, there is no traditional rollback across independent databases. Saga compensation provides the mechanism to undo completed operations when a later step fails.

### Key Concepts:
- **Forward transaction**: The main business operation (create order, charge payment)
- **Compensating transaction**: Undoes a forward transaction (cancel order, refund payment)
- **Semantic undo**: Not a DB rollback — a new business operation that reverses the effect
- **Idempotency**: Compensation must be safe to execute multiple times

### Compensation Rules:
1. Every forward step must have a corresponding compensation
2. Compensations execute in reverse order
3. Compensations must be idempotent
4. Compensations can also fail — need retry/handling

---

## Internal Working

### Compensation Flow:

```
FORWARD FLOW (Happy Path):
Step 1: Create Order (PENDING)     → Compensation: Cancel Order
Step 2: Reserve Inventory          → Compensation: Release Inventory  
Step 3: Process Payment            → Compensation: Refund Payment
Step 4: Confirm Order (CONFIRMED)  → (No compensation needed — final state)

FAILURE AT STEP 3:
Step 1: Create Order ✓
Step 2: Reserve Inventory ✓
Step 3: Process Payment ✗ (failed)

COMPENSATION (reverse order):
Comp 2: Release Inventory (undo step 2)
Comp 1: Cancel Order (undo step 1)

Result: System returns to consistent state
```

### Compensation State Machine:

```
┌────────────────────────────────────────────────────┐
│              SAGA STATE MACHINE                      │
│                                                     │
│  STARTED                                           │
│    │                                                │
│    ↓ (execute step 1)                              │
│  STEP_1_COMPLETED                                  │
│    │                                                │
│    ↓ (execute step 2)                              │
│  STEP_2_COMPLETED                                  │
│    │                                                │
│    ├──→ (success) → STEP_3_COMPLETED → COMPLETED   │
│    │                                                │
│    └──→ (failure) → COMPENSATING                   │
│                       │                             │
│                       ↓ (undo step 2)              │
│                     STEP_2_COMPENSATED              │
│                       │                             │
│                       ↓ (undo step 1)              │
│                     STEP_1_COMPENSATED              │
│                       │                             │
│                       ↓                             │
│                     COMPENSATED (final)             │
│                                                     │
│  Edge cases:                                        │
│  - Compensation fails → COMPENSATION_FAILED        │
│  - Retry compensation → back to COMPENSATING       │
│  - Manual intervention required                     │
└────────────────────────────────────────────────────┘
```

### Why Compensation ≠ Rollback:

```
DATABASE ROLLBACK (Traditional):
  BEGIN TRANSACTION
    INSERT INTO orders ...
    INSERT INTO payments ...
    INSERT INTO inventory ...
  ROLLBACK  ← Atomically undoes ALL operations

SAGA COMPENSATION (Distributed):
  Service A: INSERT order (committed to DB A) ✓
  Service B: INSERT payment (committed to DB B) ✓
  Service C: UPDATE inventory → FAILS ✗
  
  Cannot rollback DB A or DB B — already committed!
  
  Must create NEW operations:
  Service B: INSERT refund (new record in DB B)
  Service A: UPDATE order SET status='CANCELLED' (update in DB A)
  
  The original records still exist — they're not deleted.
  Instead, new compensating records are added.
```

---

## Diagram

```
Real-World Order Saga with Compensation:

┌─────────────────────────────────────────────────────────┐
│ SAGA: Create Order                                       │
│                                                          │
│ Forward Steps:                  Compensations:          │
│ ┌─────────────────────┐       ┌──────────────────────┐ │
│ │1. Create Order      │ ←───→ │ Cancel Order         │ │
│ │   status=PENDING    │       │ status=CANCELLED     │ │
│ └─────────┬───────────┘       └──────────────────────┘ │
│           ↓                                             │
│ ┌─────────────────────┐       ┌──────────────────────┐ │
│ │2. Verify Customer   │ ←───→ │ (No compensation     │ │
│ │   credit check      │       │  - read-only step)   │ │
│ └─────────┬───────────┘       └──────────────────────┘ │
│           ↓                                             │
│ ┌─────────────────────┐       ┌──────────────────────┐ │
│ │3. Reserve Stock     │ ←───→ │ Release Stock        │ │
│ │   qty reserved      │       │ qty released         │ │
│ └─────────┬───────────┘       └──────────────────────┘ │
│           ↓                                             │
│ ┌─────────────────────┐       ┌──────────────────────┐ │
│ │4. Process Payment   │ ←───→ │ Refund Payment       │ │
│ │   amount charged    │       │ amount refunded      │ │
│ └─────────┬───────────┘       └──────────────────────┘ │
│           ↓                                             │
│ ┌─────────────────────┐                                │
│ │5. Confirm Order     │       (No compensation —      │
│ │   status=CONFIRMED  │        this is the end state) │
│ └─────────────────────┘                                │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Code

### Saga Step Definition:

```java
public interface SagaStep<T> {
    
    /**
     * Forward transaction — the main business operation
     */
    StepResult execute(T context);
    
    /**
     * Compensating transaction — undoes the forward operation
     * Must be idempotent!
     */
    StepResult compensate(T context);
    
    /**
     * Name for logging/tracking
     */
    String getName();
}
```

### Concrete Saga Steps:

```java
@Component
public class CreateOrderStep implements SagaStep<OrderSagaContext> {

    private final OrderRepository orderRepository;

    @Override
    public String getName() { return "CREATE_ORDER"; }

    @Override
    public StepResult execute(OrderSagaContext context) {
        Order order = Order.builder()
            .customerId(context.getCustomerId())
            .items(context.getItems())
            .status(OrderStatus.PENDING)
            .build();
        order = orderRepository.save(order);
        context.setOrderId(order.getId());
        return StepResult.success();
    }

    @Override
    public StepResult compensate(OrderSagaContext context) {
        // Idempotent: check if already cancelled
        orderRepository.findById(context.getOrderId())
            .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
            .ifPresent(order -> {
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(Instant.now());
                order.setCancelReason("Saga compensation");
                orderRepository.save(order);
            });
        return StepResult.success();
    }
}

@Component
public class ProcessPaymentStep implements SagaStep<OrderSagaContext> {

    private final PaymentServiceClient paymentClient;

    @Override
    public String getName() { return "PROCESS_PAYMENT"; }

    @Override
    public StepResult execute(OrderSagaContext context) {
        PaymentResponse response = paymentClient.processPayment(
            PaymentRequest.builder()
                .orderId(context.getOrderId())
                .amount(context.getTotalAmount())
                .customerId(context.getCustomerId())
                .idempotencyKey(context.getSagaId() + "-payment")  // Idempotent
                .build());
        
        context.setPaymentId(response.getPaymentId());
        return StepResult.success();
    }

    @Override
    public StepResult compensate(OrderSagaContext context) {
        if (context.getPaymentId() == null) {
            return StepResult.success();  // Payment never happened
        }
        
        // Refund is also idempotent (refund same payment only once)
        paymentClient.refundPayment(RefundRequest.builder()
            .paymentId(context.getPaymentId())
            .reason("Order saga compensation")
            .idempotencyKey(context.getSagaId() + "-refund")
            .build());
        
        return StepResult.success();
    }
}
```

### Saga Orchestrator with Compensation:

```java
@Service
@Slf4j
public class SagaOrchestrator<T> {

    private final List<SagaStep<T>> steps;
    private final SagaStateRepository stateRepository;

    public SagaResult execute(UUID sagaId, T context) {
        List<SagaStep<T>> completedSteps = new ArrayList<>();

        for (SagaStep<T> step : steps) {
            log.info("Saga {}: Executing step '{}'", sagaId, step.getName());
            
            try {
                StepResult result = step.execute(context);
                
                if (result.isSuccess()) {
                    completedSteps.add(step);
                    saveState(sagaId, step.getName(), "COMPLETED");
                } else {
                    log.error("Saga {}: Step '{}' failed: {}", 
                        sagaId, step.getName(), result.getError());
                    compensate(sagaId, completedSteps, context);
                    return SagaResult.failed(result.getError());
                }
            } catch (Exception e) {
                log.error("Saga {}: Step '{}' threw exception", 
                    sagaId, step.getName(), e);
                compensate(sagaId, completedSteps, context);
                return SagaResult.failed(e.getMessage());
            }
        }

        saveState(sagaId, "ALL", "COMPLETED");
        return SagaResult.success();
    }

    private void compensate(UUID sagaId, List<SagaStep<T>> completedSteps, T context) {
        log.info("Saga {}: Starting compensation for {} steps", 
            sagaId, completedSteps.size());
        
        saveState(sagaId, "COMPENSATION", "STARTED");

        // Compensate in REVERSE order
        List<SagaStep<T>> reversed = new ArrayList<>(completedSteps);
        Collections.reverse(reversed);

        for (SagaStep<T> step : reversed) {
            try {
                log.info("Saga {}: Compensating step '{}'", sagaId, step.getName());
                step.compensate(context);
                saveState(sagaId, step.getName(), "COMPENSATED");
            } catch (Exception e) {
                // Compensation failed — critical situation
                log.error("Saga {}: COMPENSATION FAILED for step '{}'. Manual intervention needed!", 
                    sagaId, step.getName(), e);
                saveState(sagaId, step.getName(), "COMPENSATION_FAILED");
                alertOps(sagaId, step.getName(), e);
                // Continue compensating other steps
            }
        }
        
        saveState(sagaId, "COMPENSATION", "COMPLETED");
    }
}
```

### Handling Compensation Failures:

```java
@Service
public class CompensationRetryService {

    @Scheduled(fixedDelay = 60000)  // Every minute
    public void retryFailedCompensations() {
        List<SagaState> failed = sagaRepository
            .findByStatus(SagaStatus.COMPENSATION_FAILED);
        
        for (SagaState saga : failed) {
            if (saga.getRetryCount() >= MAX_RETRIES) {
                // Alert for manual intervention
                alertService.critical(
                    "Saga " + saga.getId() + " compensation failed after " +
                    MAX_RETRIES + " retries. Manual intervention required.");
                saga.setStatus(SagaStatus.REQUIRES_MANUAL_INTERVENTION);
            } else {
                try {
                    retryCompensation(saga);
                    saga.setStatus(SagaStatus.COMPENSATED);
                } catch (Exception e) {
                    saga.setRetryCount(saga.getRetryCount() + 1);
                }
            }
            sagaRepository.save(saga);
        }
    }
}
```

---

## Interview Questions

1. **What is a compensating transaction?**
   - A new business operation that semantically undoes a previously completed step. Not a DB rollback — creates new records (refund record, cancellation record). Must be idempotent because it might execute multiple times.

2. **Why must compensations be idempotent?**
   - Compensation might be triggered multiple times (network retry, message redelivery, process crash + restart). E.g., refund should check "was this payment already refunded?" before refunding again.

3. **What happens if compensation itself fails?**
   - Retry with backoff. Track in saga state. After max retries, flag for manual intervention. Alert operations team. This is why saga state must be persistent.

4. **How is compensation different from DB rollback?**
   - DB rollback undoes uncommitted changes atomically. Compensation creates new operations against already-committed data. The original data still exists — compensation adds refund records, status updates, etc.

5. **How to handle non-compensatable operations?**
   - Some operations can't be undone (sent email, printed document). Strategy: delay non-compensatable steps until last, use "pending" states, or accept the side effect and handle it (send cancellation email).

6. **Compensation ordering — why reverse?**
   - Reverse order ensures dependencies are respected. If Step 3 depends on Step 2's output, undo Step 3 first before undoing Step 2. Like unwinding a stack.

---

## Common Mistakes

1. **Non-idempotent compensations** — Double refund to customer
2. **Missing compensation for a step** — Inconsistent state after failure
3. **Not persisting saga state** — Process crash = lost saga, inconsistent data
4. **Ignoring compensation failures** — System left in broken state
5. **Compensating read-only steps** — Credit check doesn't need undo
6. **Wrong compensation order** — Releasing stock before cancelling the reservation reference

---

## Best Practices

1. **Idempotency keys** — Every compensation uses a unique key to prevent duplicates
2. **Persist saga state** — Before and after each step, persist to survive crashes
3. **Compensation timeout** — Don't let compensation hang forever
4. **Manual intervention path** — When all retries fail, alert humans
5. **Non-compensatable steps last** — Move irreversible steps to end of saga
6. **Test compensations** — Inject failures at every step and verify correct compensation
7. **Audit trail** — Log every forward step and compensation for debugging
