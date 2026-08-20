# 24. Scalability Patterns

## Theory

Scalability is the ability of a system to handle growing workload by adding resources. Microservices enable fine-grained scaling — scale only the services that need it.

### Scaling Types:
- **Horizontal scaling**: Add more instances (preferred for microservices)
- **Vertical scaling**: Add more CPU/RAM to existing instance (limited)

### Key Patterns:

| Pattern | What It Does | Example |
|---------|-------------|---------|
| Stateless services | Any instance handles any request | JWT auth, no sessions |
| Auto-scaling | Add/remove instances based on load | Kubernetes HPA |
| Database scaling | Handle DB becoming bottleneck | Read replicas, sharding |
| Asynchronous processing | Queue work for later | Kafka consumers |
| Caching | Reduce DB load | Redis cache layer |

---

## Internal Working

### Horizontal Scaling:

```
┌────────────────────────────────────────────────────────────┐
│ HORIZONTAL SCALING                                          │
│                                                             │
│ Normal Load (3 instances):                                │
│ ┌───────────┐                                             │
│ │    LB     │                                             │
│ └─┬───┬───┬─┘                                             │
│   │   │   │                                               │
│   ↓   ↓   ↓                                               │
│ [Pod][Pod][Pod]  → handling 300 req/s (100 each)         │
│                                                             │
│ Peak Load (auto-scaled to 10 instances):                  │
│ ┌───────────┐                                             │
│ │    LB     │                                             │
│ └─┬─┬─┬─┬─┬─┬─┬─┬─┬─┘                                   │
│   ↓ ↓ ↓ ↓ ↓ ↓ ↓ ↓ ↓ ↓                                   │
│ [Pod][Pod][Pod][Pod][Pod][Pod][Pod][Pod][Pod][Pod]         │
│ → handling 1000 req/s (100 each)                          │
│                                                             │
│ Requirements for horizontal scaling:                       │
│ ✓ Stateless (no local state)                             │
│ ✓ Shared-nothing (own database per service)              │
│ ✓ Load balancer distributes traffic                      │
│ ✓ Health checks to route only to healthy pods            │
└────────────────────────────────────────────────────────────┘
```

### Database Scaling Strategies:

```
┌────────────────────────────────────────────────────────────┐
│ DATABASE SCALING                                            │
│                                                             │
│ Strategy 1: READ REPLICAS                                 │
│   ┌────────┐  writes   ┌──────────┐                     │
│   │Service │ ─────────→│  Primary │                      │
│   │        │            │   DB     │                      │
│   │        │  reads     └────┬─────┘                      │
│   │        │ ─────→         │ replication                 │
│   └────────┘        ┌──────┴──────┐                      │
│                     ↓              ↓                       │
│               ┌──────────┐  ┌──────────┐                 │
│               │ Replica 1│  │ Replica 2│                 │
│               └──────────┘  └──────────┘                 │
│                                                             │
│ Strategy 2: SHARDING (Partitioning)                       │
│   Orders 1-1M    → Shard A                               │
│   Orders 1M-2M   → Shard B                               │
│   Orders 2M-3M   → Shard C                               │
│                                                             │
│   User A (hash=1) → Shard A                              │
│   User B (hash=2) → Shard B                              │
│                                                             │
│ Strategy 3: CQRS (Separate read/write databases)         │
│   Writes → PostgreSQL (normalized)                       │
│   Reads  → Elasticsearch (denormalized, optimized)       │
│                                                             │
│ Strategy 4: CACHING (Reduce DB hits)                     │
│   Hot data → Redis (microseconds)                        │
│   Cold data → Database (milliseconds)                    │
└────────────────────────────────────────────────────────────┘
```

### Auto-Scaling Decision:

```
┌────────────────────────────────────────────────────────────┐
│ KUBERNETES HPA (Horizontal Pod Autoscaler)                  │
│                                                             │
│ Metrics monitored:                                        │
│   CPU: avg across pods > 70% → scale up                  │
│   Memory: avg > 80% → scale up                           │
│   Custom: request queue depth > 100 → scale up           │
│                                                             │
│ Timeline:                                                  │
│                                                             │
│ T=0:  3 pods, CPU=40%                                    │
│ T=1:  Traffic spike → CPU=75% (>70% threshold)           │
│ T=2:  HPA: scale to 5 pods                              │
│ T=3:  5 pods, CPU=50% (stabilized)                       │
│ T=4:  Traffic drops → CPU=20%                            │
│ T=5:  HPA: scale down to 3 pods (cool-down period)      │
│                                                             │
│ Scale Up: Fast (seconds)                                  │
│ Scale Down: Slow (stabilization window, 5 min default)   │
└────────────────────────────────────────────────────────────┘
```

---

## Code

### Kubernetes HPA:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 4
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # 5 min cooldown
      policies:
        - type: Percent
          value: 25
          periodSeconds: 60
```

### Stateless Service Design:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // NO session state, NO local cache (use Redis)
    // NO local file system (use S3/object storage)
    // Authentication via JWT (stateless)
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") String userId,  // From JWT, no session
            @Valid @RequestBody CreateOrderRequest request) {
        
        // State in database (shared across instances)
        // Cache in Redis (shared across instances)
        // Files in S3 (shared across instances)
        
        return ResponseEntity.ok(orderService.createOrder(userId, request));
    }
}
```

### Asynchronous Processing for Scale:

```java
@Service
public class OrderProcessingService {

    // Instead of processing synchronously (blocks thread):
    // Queue work and return immediately
    
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Quick: validate + save + respond
        Order order = new Order(request);
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
        
        // Heavy work: async via Kafka (scaled independently)
        kafkaTemplate.send("order-processing", order.getId().toString(),
            new ProcessOrderEvent(order.getId(), order.getItems()));
        
        return new OrderResponse(order.getId(), "PENDING");
    }
}

// Kafka consumer — scale independently (add more consumers for more throughput)
@Service
public class OrderProcessor {

    @KafkaListener(topics = "order-processing", 
                   groupId = "order-processor",
                   concurrency = "5")  // 5 concurrent consumers
    public void processOrder(ProcessOrderEvent event) {
        // Heavy work: inventory check, payment, notification
        // Scaled by adding more consumer instances
    }
}
```

---

## Interview Questions

1. **How to scale microservices?**
   - Stateless services + horizontal scaling. Auto-scaling (HPA) based on CPU/memory/custom metrics. Database scaling via read replicas, caching, sharding. Async processing via message queues. Scale each service independently based on its bottleneck.

2. **Why must services be stateless for horizontal scaling?**
   - If service stores state locally (session, file, local cache), scaling adds instances that don't have that state. Load balancer sends request to instance without context → failure. Stateless: any instance can handle any request.

3. **How to handle database bottleneck?**
   - Read replicas for read-heavy workloads. Caching (Redis) for hot data. Connection pooling (HikariCP). Query optimization. CQRS for separating read/write patterns. Sharding for extreme scale.

4. **What is auto-scaling and how does it work in Kubernetes?**
   - HPA monitors metrics (CPU, memory, custom). When threshold exceeded, adds pods. When load drops, removes pods (with cooldown). KEDA for event-driven scaling (e.g., scale based on Kafka lag).

5. **Scaling consumers vs scaling producers?**
   - Producers: scale independently, Kafka handles throughput. Consumers: limited by partition count (max consumers = partitions). Add partitions to scale consumers. Use consumer groups for parallel processing.

---

## Best Practices

1. **Design for statelessness** — No local state, sessions in Redis, files in object storage
2. **Auto-scale based on business metrics** — Request rate, queue depth, not just CPU
3. **Set resource limits** — Prevent one pod from consuming all node resources
4. **Scale down slowly** — Avoid thrashing (rapid scale up/down cycles)
5. **Load test to find limits** — Know breaking points before production
6. **Cache aggressively** — Reduce DB load for read-heavy workloads
7. **Async for heavy operations** — Queue work instead of blocking threads
