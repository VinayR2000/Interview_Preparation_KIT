# 29. Spring Cloud

## Theory

Spring Cloud provides tools for building distributed systems: service discovery, configuration management, API gateway, circuit breakers, and distributed tracing.

### Core Components:
- **Service Discovery** (Eureka/Consul): Services register and find each other
- **API Gateway** (Spring Cloud Gateway): Single entry point, routing, filtering
- **Config Server** (Spring Cloud Config): Centralized configuration management
- **Resilience** (Resilience4j): Circuit breaker, retry, rate limiting
- **Distributed Tracing** (Micrometer Tracing): Request tracking across services

---

## Internal Working

```
┌─── Service Registration Flow ───────────────────┐
│                                                   │
│ Service starts → Registers with Eureka Server    │
│ Heartbeat every 30s → "I'm alive"               │
│ Other services → Query Eureka for instances      │
│ Client-side load balancing → Pick an instance    │
└───────────────────────────────────────────────────┘

┌─── API Gateway Flow ───────────────────────────────┐
│                                                      │
│ Client → Gateway → Route matching (predicates)      │
│   → Pre-filters (auth, rate limit, logging)         │
│   → Load balance to service instance                │
│   → Post-filters (headers, response modification)   │
│   → Response to client                              │
└──────────────────────────────────────────────────────┘

┌─── Config Server Flow ─────────────────────────────┐
│                                                      │
│ Service starts → Fetches config from Config Server  │
│ Config Server → Reads from Git/Vault/File system    │
│ /actuator/refresh → Reload config without restart   │
│ Spring Cloud Bus → Broadcast config changes         │
└──────────────────────────────────────────────────────┘
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        SPRING CLOUD ECOSYSTEM                      │
│                                                                    │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    API GATEWAY                               │  │
│  │             (Spring Cloud Gateway)                           │  │
│  │  - Routing    - Auth    - Rate Limiting   - Load Balancing  │  │
│  └────────────────────────┬───────────────────────────────────┘  │
│                            │                                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌──────────────────┐  │
│  │Order Svc│  │User Svc │  │Pay Svc  │  │Config Server     │  │
│  │         │  │         │  │         │  │(Git-backed)       │  │
│  └────┬────┘  └────┬────┘  └────┬────┘  └──────────────────┘  │
│       │             │            │                               │
│       └─────────────┼────────────┘                               │
│                     │                                             │
│  ┌──────────────────┴─────────────────────────────────────────┐ │
│  │              SERVICE REGISTRY (Eureka/Consul)                │ │
│  │  Registered: ORDER-SERVICE(3), USER-SERVICE(2), PAY-SVC(2)  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │           DISTRIBUTED TRACING (Micrometer + Zipkin)          │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

---

## Code

### Service Discovery (Eureka Server):

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
# eureka-server application.yml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

### Service Registration (Client):

```yaml
# order-service application.yml
spring:
  application:
    name: ORDER-SERVICE
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

### API Gateway:

```yaml
# gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCB
                fallbackUri: forward:/fallback/orders
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
        - id: user-service
          uri: lb://USER-SERVICE
          predicates:
            - Path=/api/users/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins: "http://localhost:3000"
            allowedMethods: "*"
```

### Config Server:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# config-server application.yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/org/config-repo
          default-label: main
          search-paths: '{application}'
```

### Load-Balanced Client:

```java
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClient() {
        return WebClient.builder();
    }
}

@Service
public class OrderClient {

    private final WebClient.Builder webClientBuilder;

    public Mono<OrderDTO> getOrder(Long orderId) {
        return webClientBuilder.build()
            .get()
            .uri("http://ORDER-SERVICE/api/orders/{id}", orderId)  // Service name, not URL
            .retrieve()
            .bodyToMono(OrderDTO.class);
    }
}
```

### Distributed Tracing:

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling (reduce in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

---

## Dry Run

### Service Discovery + Load Balancing:

```
1. Order Service Instance-1 starts → Registers with Eureka
   "ORDER-SERVICE at 192.168.1.10:8081"

2. Order Service Instance-2 starts → Registers with Eureka
   "ORDER-SERVICE at 192.168.1.11:8081"

3. API Gateway needs to route to ORDER-SERVICE:
   → Queries Eureka: "Where is ORDER-SERVICE?"
   → Gets: [192.168.1.10:8081, 192.168.1.11:8081]
   → Load balancer picks one (round-robin)
   → Routes request to 192.168.1.10:8081

4. Instance-1 crashes (no heartbeat for 90s):
   → Eureka marks it as DOWN
   → Gateway only routes to Instance-2

5. Instance-1 restarts:
   → Re-registers with Eureka
   → Gateway resumes routing to both instances
```

---

## Interview Questions

1. **What is Service Discovery and why is it needed?**
   - Dynamic registration/lookup of service instances. Needed because in cloud/K8s, instances have dynamic IPs. Alternatives: Eureka, Consul, K8s DNS.

2. **API Gateway vs Load Balancer?**
   - API Gateway: Application-level routing, auth, rate limiting, protocol translation. LB: Network-level distribution of traffic. Gateway provides more intelligence.

3. **How does Spring Cloud Config work?**
   - Config Server reads from Git/Vault. Services fetch config on startup. Changes can be refreshed via /actuator/refresh or Spring Cloud Bus broadcast.

4. **Client-side vs Server-side load balancing?**
   - Client-side (Spring Cloud LoadBalancer): Client knows all instances, picks one. Server-side (Nginx/ALB): Central LB distributes. Client-side = no single point of failure.

5. **How to handle configuration across environments?**
   - Config Server with profiles: application-dev.yml, application-prod.yml. Services specify active profile. Secrets from Vault.

---

## Common Mistakes

1. **Eureka as single instance** - Must be clustered for HA
2. **Not setting timeouts on gateway** - Requests hang indefinitely
3. **Hardcoding service URLs** - Use service names with load balancer
4. **Config secrets in Git** - Use Vault or encrypt sensitive properties
5. **100% tracing in production** - Too much overhead; sample at 1-10%

---

## Best Practices

1. **Use Kubernetes Service Discovery** if already on K8s (instead of Eureka)
2. **Gateway for cross-cutting concerns** - Auth, rate limiting, CORS
3. **Externalize ALL configuration** via Config Server
4. **Encrypt sensitive config** with Vault or Spring Cloud Config encryption
5. **Set circuit breakers on gateway routes** for resilience
6. **Distributed tracing on all services** for debugging
7. **Health checks** for service registration/deregistration

---

## Production Considerations

- **Eureka clustering**: 3-node minimum for HA
- **Config Server HA**: Multiple instances behind load balancer
- **Gateway performance**: Non-blocking (WebFlux-based), size thread pools
- **Service mesh alternative**: Istio/Linkerd handle discovery, LB, mTLS at infrastructure level
- **K8s native**: Consider K8s Services + Ingress instead of Eureka + Gateway

---

## Related Topics

- Microservices with Spring Boot
- Resilience Patterns
- Docker + Kubernetes
- Distributed Tracing
- Inter-Service Communication
