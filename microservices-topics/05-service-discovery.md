# 5. Service Discovery ⭐⭐⭐⭐⭐

## Theory

In microservices, services have dynamic locations (IP addresses change with scaling, restarts, deployments). Service discovery solves: "How does Service A find Service B?"

### The Problem:
- Services run on dynamic IPs and ports
- Multiple instances per service (scaling)
- Instances come and go (auto-scaling, failures)
- Hardcoding URLs doesn't work

### Solutions:

| Pattern | How It Works | Example |
|---------|-------------|---------|
| Client-side discovery | Client queries registry, picks instance | Eureka + Spring Cloud LoadBalancer |
| Server-side discovery | Load balancer queries registry | Kubernetes Service, AWS ALB |
| DNS-based | DNS resolves to service instances | Kubernetes CoreDNS, Consul DNS |

### Service Registry:
- Central database of service instances
- Services register on startup, deregister on shutdown
- Health checks remove unhealthy instances
- Examples: Eureka, Consul, Zookeeper, Kubernetes etcd

---

## Internal Working

### Client-Side Discovery:

```
┌──────────────────────────────────────────────────┐
│ CLIENT-SIDE DISCOVERY (Eureka + Spring Cloud)    │
│                                                   │
│  1. Payment Service registers                    │
│     ┌─────────────────────────────┐              │
│     │    Eureka Server (Registry) │              │
│     │                             │              │
│     │  payment-service:           │              │
│     │    - 10.0.1.5:8082          │              │
│     │    - 10.0.1.6:8082          │              │
│     │    - 10.0.1.7:8082          │              │
│     │                             │              │
│     │  order-service:             │              │
│     │    - 10.0.2.3:8081          │              │
│     └─────────────────────────────┘              │
│              ↑                  │                 │
│  2. Register │     3. Fetch    │ registry        │
│              │                 ↓                  │
│  ┌──────────────┐    ┌──────────────┐           │
│  │Payment Service│    │Order Service │           │
│  │(registers    │    │(fetches      │           │
│  │ itself)      │    │ registry)    │           │
│  └──────────────┘    └──────┬───────┘           │
│                             │                    │
│              4. Client-side load balancing        │
│                 Pick: 10.0.1.6:8082              │
│                             │                    │
│              5. Direct call ↓                    │
│                    ┌──────────────┐              │
│                    │Payment Service│              │
│                    │ 10.0.1.6:8082│              │
│                    └──────────────┘              │
└──────────────────────────────────────────────────┘
```

### Server-Side Discovery (Kubernetes):

```
┌──────────────────────────────────────────────────┐
│ SERVER-SIDE DISCOVERY (Kubernetes)                │
│                                                   │
│  Order Service doesn't know instance IPs         │
│                                                   │
│  ┌─────────────┐                                 │
│  │Order Service│                                 │
│  │Pod          │                                 │
│  └──────┬──────┘                                 │
│         │                                         │
│         │ Call: http://payment-service:8082       │
│         ↓                                         │
│  ┌──────────────────────┐                        │
│  │ Kubernetes Service   │                        │
│  │ (payment-service)    │ ← Virtual IP (ClusterIP)│
│  │                      │                        │
│  │ Endpoints:           │                        │
│  │  - Pod 10.0.1.5:8082│                        │
│  │  - Pod 10.0.1.6:8082│                        │
│  │  - Pod 10.0.1.7:8082│                        │
│  └──────────┬───────────┘                        │
│             │ kube-proxy / iptables               │
│             │ (server-side load balancing)        │
│             ↓                                     │
│  ┌──────────────┐                                │
│  │Payment Pod   │                                │
│  │10.0.1.6:8082 │ (selected by kube-proxy)       │
│  └──────────────┘                                │
└──────────────────────────────────────────────────┘

Key difference: Order Service just uses DNS name
Kubernetes handles discovery + load balancing
```

### Registration and Health Check Flow:

```
Service Lifecycle with Eureka:

1. STARTUP:
   Service starts → registers with Eureka
   Registration: {name: "payment-service", host: "10.0.1.5", port: 8082}

2. HEARTBEAT:
   Every 30s → service sends heartbeat to Eureka
   If Eureka misses 3 heartbeats → marks instance as DOWN

3. DISCOVERY:
   Other services fetch registry every 30s (cached locally)
   If Eureka goes down → services use local cache

4. SHUTDOWN:
   Service deregisters from Eureka
   Other services refresh cache → stop routing to it

5. FAILURE:
   Service crashes (no deregister)
   → Heartbeat stops
   → Eureka waits 90s (3 × 30s)
   → Marks as DOWN
   → Evicts instance
```

---

## Diagram

```
Eureka High Availability:

┌──────────────────────────────────────────────────┐
│                                                   │
│  ┌──────────┐    replicate    ┌──────────┐      │
│  │ Eureka 1 │ ←─────────────→ │ Eureka 2 │      │
│  │(Zone A)  │                 │(Zone B)  │      │
│  └────┬─────┘                 └────┬─────┘      │
│       │                            │             │
│   ┌───┴───────────┐    ┌──────────┴───┐        │
│   │               │    │              │         │
│   ↓               ↓    ↓              ↓         │
│ ┌──────┐    ┌──────┐ ┌──────┐   ┌──────┐      │
│ │Order │    │Payment│ │Order │   │Payment│      │
│ │Svc-1 │    │Svc-1 │ │Svc-2 │   │Svc-2 │      │
│ │(Zone A)│  │(Zone A)││(Zone B)│ │(Zone B)│    │
│ └──────┘    └──────┘ └──────┘   └──────┘      │
│                                                   │
│ Each service registers with nearest Eureka       │
│ Eureka servers replicate to each other           │
│ If one Eureka goes down, services use the other  │
└──────────────────────────────────────────────────┘
```

---

## Code

### Eureka Server Setup:

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

```yaml
# Eureka Server application.yml
server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false  # Don't register itself
    fetch-registry: false        # Don't fetch from itself
  server:
    eviction-interval-timer-in-ms: 60000  # Check for expired instances
    enable-self-preservation: true         # Don't evict during network partition
```

### Service Registration (Client):

```yaml
# Order Service application.yml
spring:
  application:
    name: order-service

eureka:
  client:
    service-url:
      defaultZone: http://eureka1:8761/eureka/,http://eureka2:8762/eureka/
    registry-fetch-interval-seconds: 30
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 30
    lease-expiration-duration-in-seconds: 90
    metadata-map:
      version: "2.1.0"
      region: "us-east-1"
```

### Service-to-Service Call with Discovery:

```java
@Service
public class OrderService {

    private final WebClient.Builder webClientBuilder;

    // Uses service name — discovery resolves to actual IP
    public UserDto getUser(String userId) {
        return webClientBuilder.build()
            .get()
            .uri("http://user-service/api/users/{id}", userId)  // Logical name
            .retrieve()
            .bodyToMono(UserDto.class)
            .block();
    }
}

// Spring Cloud LoadBalancer resolves "user-service" to actual instance
// e.g., http://user-service → http://10.0.1.5:8083
```

### Load Balancer Configuration:

```java
@Configuration
@LoadBalancerClient(name = "payment-service", 
                    configuration = PaymentLoadBalancerConfig.class)
public class PaymentLoadBalancerConfig {

    @Bean
    public ServiceInstanceListSupplier serviceInstanceListSupplier(
            ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
            .withDiscoveryClient()
            .withHealthChecks()          // Only route to healthy instances
            .withZonePreference()        // Prefer same zone
            .withCaching()               // Cache results
            .build(context);
    }
}
```

### Kubernetes Service Discovery (No Eureka needed):

```yaml
# Kubernetes Service definition
apiVersion: v1
kind: Service
metadata:
  name: payment-service
spec:
  selector:
    app: payment-service
  ports:
    - port: 8082
      targetPort: 8082
  type: ClusterIP

---
# Spring Boot config for Kubernetes
spring:
  application:
    name: order-service
  cloud:
    kubernetes:
      discovery:
        enabled: true
        all-namespaces: false
```

```java
// In Kubernetes, just use the service DNS name
@FeignClient(name = "payment-service", url = "http://payment-service:8082")
public interface PaymentClient {
    
    @PostMapping("/api/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);
}
```

---

## Interview Questions

1. **What is Service Discovery and why is it needed?**
   - In dynamic environments, service instances have changing IPs. Service discovery maintains a registry of available instances and their locations so services can find each other without hardcoded addresses.

2. **Client-side vs Server-side discovery?**
   - Client-side: Client fetches registry, picks instance, calls directly (Eureka). Server-side: Client calls a known endpoint, server routes to instance (Kubernetes Service, AWS ALB). Kubernetes made server-side dominant.

3. **What happens if Eureka goes down?**
   - Services cache the registry locally. Existing services continue to discover each other using cache. New services can't register until Eureka recovers. HA setup: run multiple Eureka instances.

4. **Eureka self-preservation mode?**
   - If Eureka stops receiving heartbeats from many services simultaneously, it assumes network partition (not mass failure). It stops evicting instances to prevent cascading issues. Better to route to potentially-down service than to route to none.

5. **Why Kubernetes Service Discovery over Eureka?**
   - Kubernetes provides built-in service discovery via DNS and kube-proxy. No extra infrastructure needed. Simpler architecture. Eureka is useful for non-Kubernetes deployments or hybrid environments.

6. **Health checks in service discovery?**
   - Registry periodically checks if instances are healthy (heartbeat or HTTP health endpoint). Unhealthy instances are removed from registry so traffic isn't routed to them.

---

## Common Mistakes

1. **No health checks** — Dead instances still receiving traffic
2. **Single Eureka instance** — Single point of failure
3. **Hardcoded URLs** — Defeats the purpose of discovery
4. **Not caching registry** — Every call queries Eureka (high latency)
5. **Long eviction timeout** — Dead instances served for too long
6. **Using Eureka on Kubernetes** — Redundant; Kubernetes has built-in discovery

---

## Best Practices

1. **Use platform-native discovery** — Kubernetes DNS if on K8s, Eureka for VM-based
2. **High availability** — Multiple registry instances in different zones
3. **Health checks** — Both liveness and readiness
4. **Prefer IP over hostname** — Avoids DNS lookup delays
5. **Zone-aware routing** — Prefer instances in same zone (lower latency)
6. **Graceful shutdown** — Deregister before stopping (drain connections)
7. **Client-side caching** — Cache registry with periodic refresh
