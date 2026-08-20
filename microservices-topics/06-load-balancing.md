# 6. Load Balancing

## Theory

Load balancing distributes incoming requests across multiple service instances to ensure no single instance is overwhelmed. Critical for high availability and horizontal scaling.

### Types:
- **Client-side**: Client chooses which instance to call (Spring Cloud LoadBalancer)
- **Server-side**: A dedicated component distributes traffic (Nginx, AWS ALB, Kubernetes Service)

### Algorithms:

| Algorithm | How It Works | Use Case |
|-----------|-------------|----------|
| Round Robin | Sequential rotation | Equal capacity instances |
| Weighted Round Robin | More traffic to stronger instances | Mixed capacity |
| Least Connections | Route to instance with fewest active connections | Varying request duration |
| Random | Random selection | Simple, works well at scale |
| IP Hash | Same client always goes to same instance | Session affinity (avoid if possible) |

---

## Internal Working

### Client-Side Load Balancing:

```
┌────────────────────────────────────────────────────┐
│ CLIENT-SIDE LOAD BALANCING                          │
│                                                     │
│ Order Service has a local copy of service registry │
│                                                     │
│ ┌─────────────────────────────────┐               │
│ │        Order Service            │               │
│ │                                 │               │
│ │  ┌──────────────────────────┐  │               │
│ │  │ Local Registry Cache     │  │               │
│ │  │                          │  │               │
│ │  │ payment-service:         │  │               │
│ │  │   - 10.0.1.5:8082 (UP)  │  │               │
│ │  │   - 10.0.1.6:8082 (UP)  │  │               │
│ │  │   - 10.0.1.7:8082 (DOWN)│  │               │
│ │  └──────────────────────────┘  │               │
│ │                                 │               │
│ │  ┌──────────────────────────┐  │               │
│ │  │ Load Balancer Algorithm  │  │               │
│ │  │  Round Robin →           │  │               │
│ │  │  Request 1 → 10.0.1.5   │  │               │
│ │  │  Request 2 → 10.0.1.6   │  │               │
│ │  │  Request 3 → 10.0.1.5   │  │               │
│ │  │  (skip .7 — it's DOWN)  │  │               │
│ │  └──────────────────────────┘  │               │
│ └─────────────────────────────────┘               │
└────────────────────────────────────────────────────┘
```

### Server-Side Load Balancing (Kubernetes):

```
┌────────────────────────────────────────────────────┐
│ SERVER-SIDE LOAD BALANCING (Kubernetes)             │
│                                                     │
│  ┌─────────────┐                                   │
│  │Order Service│                                   │
│  │    Pod      │                                   │
│  └──────┬──────┘                                   │
│         │ http://payment-service:8082              │
│         ↓                                          │
│  ┌─────────────────────────┐                      │
│  │ Kubernetes Service      │ (ClusterIP)          │
│  │ "payment-service"       │                      │
│  │                         │                      │
│  │ iptables/IPVS rules:    │                      │
│  │  → Pod 1 (33%)          │                      │
│  │  → Pod 2 (33%)          │                      │
│  │  → Pod 3 (33%)          │                      │
│  └──┬───────┬────────┬────┘                      │
│     ↓       ↓        ↓                            │
│  ┌─────┐ ┌─────┐ ┌─────┐                        │
│  │Pod 1│ │Pod 2│ │Pod 3│                        │
│  └─────┘ └─────┘ └─────┘                        │
│                                                     │
│ Order Service doesn't know about load balancing    │
│ It just calls the service name                     │
└────────────────────────────────────────────────────┘
```

### Algorithm Comparison:

```
ROUND ROBIN:
Requests: R1, R2, R3, R4, R5, R6
Instance A: R1, R3, R5
Instance B: R2, R4, R6
Simple, fair distribution.

WEIGHTED ROUND ROBIN:
Instance A (weight=3): R1, R2, R3, R6, R7, R8
Instance B (weight=1): R4, R9
More powerful instance gets more traffic.

LEAST CONNECTIONS:
Instance A: 5 active connections
Instance B: 2 active connections
Instance C: 8 active connections
→ Next request goes to B (fewest connections)
Good for long-running requests with variable duration.

RANDOM:
Statistically even at high volume.
Simple to implement.
No state to maintain.
```

---

## Code

### Spring Cloud LoadBalancer (Client-Side):

```java
@Configuration
public class LoadBalancerConfig {

    @Bean
    @LoadBalanced  // Enables client-side load balancing
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

@Service
public class PaymentClient {

    private final WebClient.Builder webClientBuilder;

    // "payment-service" resolved via service discovery + load balanced
    public PaymentResponse processPayment(PaymentRequest request) {
        return webClientBuilder.build()
            .post()
            .uri("http://payment-service/api/payments")  // Logical name
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentResponse.class)
            .block();
    }
}
```

### Custom Load Balancer Strategy:

```java
public class CustomLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final ObjectProvider<ServiceInstanceListSupplier> supplier;
    private final AtomicInteger position = new AtomicInteger(0);

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return supplier.getIfAvailable()
            .get(request)
            .next()
            .map(instances -> {
                if (instances.isEmpty()) {
                    return new EmptyResponse();
                }
                // Weighted selection based on metadata
                List<ServiceInstance> healthy = instances.stream()
                    .filter(this::isHealthy)
                    .collect(Collectors.toList());
                    
                int index = position.getAndIncrement() % healthy.size();
                return new DefaultResponse(healthy.get(index));
            });
    }

    private boolean isHealthy(ServiceInstance instance) {
        String status = instance.getMetadata().get("status");
        return !"DOWN".equals(status);
    }
}
```

### Kubernetes Load Balancing:

```yaml
# Standard ClusterIP Service (round-robin by default)
apiVersion: v1
kind: Service
metadata:
  name: payment-service
spec:
  type: ClusterIP
  selector:
    app: payment-service
  ports:
    - port: 8082
      targetPort: 8082

---
# For external traffic — LoadBalancer type
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
spec:
  type: LoadBalancer
  selector:
    app: api-gateway
  ports:
    - port: 443
      targetPort: 8080
```

---

## Interview Questions

1. **Client-side vs Server-side load balancing?**
   - Client-side: Client picks instance from cached registry (fewer hops, more control). Server-side: Dedicated component routes (simpler client, centralized management). Kubernetes uses server-side via kube-proxy.

2. **What is the best load balancing algorithm?**
   - Depends on context. Round Robin for stateless, equal-capacity instances. Least Connections for variable-duration requests. Weighted for mixed-capacity servers. No universal best.

3. **How does Kubernetes Service load balancing work?**
   - kube-proxy maintains iptables/IPVS rules that distribute traffic to pod IPs. Uses round-robin by default. The Service ClusterIP is virtual — iptables redirect to actual pod IPs.

4. **What is session affinity and when to use it?**
   - Same client always goes to same instance (sticky sessions). Avoid in microservices — make services stateless. If unavoidable, use IP-hash or cookie-based affinity.

5. **Health check + load balancing interaction?**
   - Load balancer periodically checks instance health. Unhealthy instances removed from rotation. When instance recovers, added back. Prevents routing to dead instances.

---

## Common Mistakes

1. **Sticky sessions in stateless architecture** — Forces vertical scaling
2. **No health checks** — Traffic sent to dead instances
3. **Not handling instance failure** — Retry on different instance
4. **Single load balancer** — Single point of failure
5. **Ignoring zone awareness** — Cross-zone traffic adds latency and cost

---

## Best Practices

1. **Stateless services** — Any instance can handle any request
2. **Health checks** — Liveness and readiness probes
3. **Zone-aware routing** — Prefer same availability zone
4. **Graceful shutdown** — Drain connections before stopping
5. **Auto-scaling** — Add/remove instances based on load
6. **Connection pooling** — Reuse connections to reduce overhead
