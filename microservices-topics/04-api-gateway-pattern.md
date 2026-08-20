# 4. API Gateway Pattern ⭐⭐⭐⭐⭐

## Theory

An API Gateway is the single entry point for all client requests. It acts as a reverse proxy, routing requests to appropriate microservices and handling cross-cutting concerns.

### Why API Gateway?
- Clients shouldn't know about individual service addresses
- Cross-cutting concerns (auth, rate limiting) in one place
- Request aggregation — one client call, multiple backend calls
- Protocol translation — REST externally, gRPC internally

### Responsibilities:
- **Routing**: Forward requests to correct microservice
- **Authentication**: Validate JWT tokens, API keys
- **Authorization**: Check permissions before forwarding
- **Rate Limiting**: Prevent abuse and overload
- **Request Aggregation**: Combine multiple service calls into one response
- **SSL Termination**: Handle HTTPS, forward HTTP internally
- **Load Balancing**: Distribute requests across service instances
- **Caching**: Cache frequent responses
- **Logging/Monitoring**: Centralized request logging

### API Gateway vs Load Balancer:
| Aspect | API Gateway | Load Balancer |
|--------|-------------|---------------|
| Layer | Application (L7) | Transport (L4) / Application (L7) |
| Routing | Content-based (path, headers) | Server-based (round robin) |
| Features | Auth, rate limiting, transformation | Distribution only |
| Intelligence | Business logic aware | Network-level only |

### API Gateway vs Service Mesh:
| Aspect | API Gateway | Service Mesh |
|--------|-------------|--------------|
| Traffic | North-South (client → services) | East-West (service → service) |
| Position | Edge of network | Between all services |
| Implementation | Centralized | Sidecar per service |
| Examples | Spring Cloud Gateway, Kong | Istio, Linkerd |

---

## Internal Working

### Request Flow Through Gateway:

```
┌──────────────────────────────────────────────────────────────┐
│                      API GATEWAY                              │
│                                                               │
│  Client Request                                               │
│       │                                                       │
│       ↓                                                       │
│  ┌──────────────┐                                            │
│  │SSL Termination│ → HTTPS → HTTP                            │
│  └──────┬───────┘                                            │
│         ↓                                                     │
│  ┌──────────────┐                                            │
│  │Authentication│ → Validate JWT → Extract claims            │
│  └──────┬───────┘                                            │
│         ↓                                                     │
│  ┌──────────────┐                                            │
│  │Rate Limiter  │ → Check quota → 429 if exceeded            │
│  └──────┬───────┘                                            │
│         ↓                                                     │
│  ┌──────────────┐                                            │
│  │  Routing     │ → Match path → Select service              │
│  └──────┬───────┘                                            │
│         ↓                                                     │
│  ┌──────────────┐                                            │
│  │Load Balancer │ → Select instance (round robin)            │
│  └──────┬───────┘                                            │
│         ↓                                                     │
│  Forward to service instance                                  │
└──────────────────────────────────────────────────────────────┘
```

### Gateway Routing Architecture:

```
Client (Browser/Mobile)
       │
       ↓
┌────────────────────────────────┐
│          API GATEWAY           │
│  https://api.company.com       │
├────────────────────────────────┤
│                                │
│  /api/orders/**   → order-service (port 8081)
│  /api/payments/** → payment-service (port 8082)
│  /api/users/**    → user-service (port 8083)
│  /api/products/** → product-service (port 8084)
│                                │
└────────────────────────────────┘
       │         │         │         │
       ↓         ↓         ↓         ↓
   ┌──────┐ ┌───────┐ ┌──────┐ ┌───────┐
   │Order │ │Payment│ │ User │ │Product│
   │ Svc  │ │  Svc  │ │ Svc  │ │  Svc  │
   │(x3)  │ │ (x2)  │ │ (x2) │ │ (x3)  │
   └──────┘ └───────┘ └──────┘ └───────┘
```

---

## Diagram

```
Request Aggregation Pattern:

WITHOUT GATEWAY (Client makes multiple calls):
┌────────┐
│ Client │
└──┬─┬─┬─┘
   │ │ │     3 separate HTTP calls
   │ │ └──→ GET /api/users/123      → User Service
   │ └────→ GET /api/orders?user=123 → Order Service
   └──────→ GET /api/reviews?user=123 → Review Service

WITH GATEWAY (Single call, gateway aggregates):
┌────────┐
│ Client │
└────┬───┘
     │        1 HTTP call
     ↓
┌──────────────────┐
│   API GATEWAY    │
│                  │
│ GET /api/profile │
│   Aggregates:    │
│   → User Service │
│   → Order Service│
│   → Review Svc   │
│   → Merge & Return│
└──────────────────┘

Result: Less bandwidth, fewer round-trips for mobile clients
```

---

## Code

### Spring Cloud Gateway Configuration:

```yaml
# application.yml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=0
            - name: CircuitBreaker
              args:
                name: orderCircuitBreaker
                fallbackUri: forward:/fallback/orders

        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
          filters:
            - StripPrefix=0

        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
            - Method=GET,POST,PUT
          filters:
            - StripPrefix=0
            - AddRequestHeader=X-Gateway-Source, api-gateway

      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 100
            redis-rate-limiter.burstCapacity: 200
            key-resolver: "#{@userKeyResolver}"
```

### JWT Authentication Filter:

```java
@Component
public class JwtAuthenticationFilter implements GatewayFilter, Ordered {

    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateToken(token);
            
            // Add user info to headers for downstream services
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Roles", claims.get("roles", String.class))
                .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (JwtException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -1;  // Run before other filters
    }
}
```

### Rate Limiting Configuration:

```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null) {
                return Mono.just(userId);
            }
            // Fallback to IP-based rate limiting
            return Mono.just(
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            );
        };
    }
}
```

### Request Aggregation:

```java
@RestController
@RequestMapping("/api/profile")
public class ProfileAggregationController {

    private final WebClient.Builder webClientBuilder;

    @GetMapping("/{userId}")
    public Mono<ProfileResponse> getUserProfile(@PathVariable String userId) {
        Mono<UserDto> user = webClientBuilder.build()
            .get().uri("http://user-service/api/users/{id}", userId)
            .retrieve().bodyToMono(UserDto.class);

        Mono<List<OrderDto>> orders = webClientBuilder.build()
            .get().uri("http://order-service/api/orders?userId={id}", userId)
            .retrieve().bodyToFlux(OrderDto.class).collectList();

        Mono<List<ReviewDto>> reviews = webClientBuilder.build()
            .get().uri("http://review-service/api/reviews?userId={id}", userId)
            .retrieve().bodyToFlux(ReviewDto.class).collectList();

        // Aggregate all responses in parallel
        return Mono.zip(user, orders, reviews)
            .map(tuple -> ProfileResponse.builder()
                .user(tuple.getT1())
                .recentOrders(tuple.getT2())
                .reviews(tuple.getT3())
                .build());
    }
}
```

### Global Error Handling:

```java
@Component
public class GlobalErrorFilter extends AbstractGatewayFilterFactory<Object> {

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> chain.filter(exchange)
            .onErrorResume(throwable -> {
                ServerHttpResponse response = exchange.getResponse();
                
                if (throwable instanceof TimeoutException) {
                    response.setStatusCode(HttpStatus.GATEWAY_TIMEOUT);
                } else if (throwable instanceof ServiceUnavailableException) {
                    response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                } else {
                    response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                }

                ErrorResponse error = new ErrorResponse(
                    response.getStatusCode().value(),
                    throwable.getMessage(),
                    Instant.now()
                );

                byte[] bytes = objectMapper.writeValueAsBytes(error);
                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return response.writeWith(Mono.just(buffer));
            });
    }
}
```

---

## Interview Questions

1. **Why do we need an API Gateway?**
   - Single entry point for clients, centralizes cross-cutting concerns (auth, rate limiting, logging), hides internal architecture, enables request aggregation, simplifies client code.

2. **API Gateway vs Load Balancer?**
   - Gateway: L7, content-aware routing, auth, transformation, aggregation. Load Balancer: L4/L7, distributes traffic to instances of same service. Gateway often has a load balancer behind it.

3. **What is the BFF pattern (Backend for Frontend)?**
   - Separate gateway per client type. Mobile BFF aggregates more (fewer calls), Web BFF returns richer data. Each BFF is tailored to its client's needs.

4. **Doesn't the gateway become a single point of failure?**
   - Yes, so it must be highly available: multiple instances behind a load balancer, stateless design, auto-scaling, health checks, graceful degradation.

5. **How does Spring Cloud Gateway differ from Zuul?**
   - SCG: Non-blocking (Reactor/Netty), better performance, WebSocket support, modern. Zuul 1: Blocking (Servlet), older. Zuul 2: Non-blocking but Netflix-specific.

6. **How to handle API versioning at gateway level?**
   - Path-based (/v1/orders, /v2/orders), Header-based (Accept: application/vnd.api.v2+json), or route to different service versions based on version identifier.

---

## Common Mistakes

1. **Business logic in gateway** — Gateway should only route and handle cross-cutting concerns
2. **Single gateway for everything** — Consider BFF pattern for different clients
3. **No circuit breaker** — Gateway should protect itself from slow backends
4. **Tight coupling to services** — Gateway shouldn't know about service internals
5. **No caching** — Missing opportunity for performance optimization
6. **Synchronous aggregation without timeout** — One slow service blocks entire response

---

## Best Practices

1. **Keep it thin** — No business logic in the gateway
2. **Circuit breakers per route** — Isolate failures per downstream service
3. **Rate limit per user/IP** — Protect backend services from abuse
4. **Cache at gateway** — Reduce load on backend services
5. **Health checks** — Remove unhealthy instances from routing
6. **Observability** — Log all requests with correlation IDs
7. **Graceful degradation** — Return partial responses if one service is down
