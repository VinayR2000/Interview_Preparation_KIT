# 31. Inter-Service Communication

## Theory

In microservices, services need to communicate over the network. The choice of communication pattern significantly impacts performance, resilience, and coupling.

### Communication Types:
- **Synchronous**: Caller waits for response (REST, gRPC)
- **Asynchronous**: Caller doesn't wait (Kafka, RabbitMQ)

### Spring HTTP Clients:
- **RestClient** (Spring 6.1+): Modern, synchronous, fluent API (recommended)
- **WebClient** (Spring WebFlux): Reactive, non-blocking HTTP client
- **OpenFeign**: Declarative REST client with interface definitions
- **RestTemplate**: Legacy (maintenance mode, avoid in new code)

---

## Internal Working

```
Synchronous (RestClient/WebClient):
  Service A → HTTP Request → Network → Service B
  Service A ← HTTP Response ← Network ← Service B
  (Thread blocked until response for RestClient)
  (Non-blocking for WebClient)

Declarative (Feign-style):
  Service A calls interface method
       ↓
  Proxy generates HTTP request
       ↓
  Load balancer selects instance
       ↓
  HTTP call to Service B
       ↓
  Response deserialized to return type

Asynchronous (Kafka):
  Service A → Publish event → Kafka → Consumer → Service B
  (Service A doesn't wait, Services decoupled)
```

---

## Diagram

```
┌──────────── Synchronous ─────────────────────────────────────┐
│                                                               │
│  Order Service        Network           Inventory Service     │
│  ┌──────────┐                           ┌──────────┐        │
│  │          │ ── GET /stock/P1 ────────→ │          │        │
│  │  WAITS   │                           │ PROCESSES │        │
│  │          │ ←── {available: true} ──── │          │        │
│  └──────────┘                           └──────────┘        │
│                                                               │
│  Pros: Simple, immediate response                            │
│  Cons: Tight coupling, cascading failures                    │
└───────────────────────────────────────────────────────────────┘

┌──────────── Asynchronous ────────────────────────────────────┐
│                                                               │
│  Order Service        Kafka              Inventory Service    │
│  ┌──────────┐    ┌──────────┐           ┌──────────┐       │
│  │ Publishes│───→│  Topic   │───→       │ Consumes │       │
│  │ event    │    │          │           │ event    │       │
│  │ CONTINUES│    └──────────┘           │ PROCESSES│       │
│  └──────────┘                           └──────────┘       │
│                                                               │
│  Pros: Decoupled, resilient, scalable                        │
│  Cons: Eventual consistency, complex debugging               │
└───────────────────────────────────────────────────────────────┘
```

---

## Code

### RestClient (Recommended for Spring Boot 3.2+):

```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient inventoryRestClient(
            @Value("${services.inventory.url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestInterceptor((request, body, execution) -> {
                // Propagate correlation ID
                String correlationId = MDC.get("correlationId");
                if (correlationId != null) {
                    request.getHeaders().set("X-Correlation-ID", correlationId);
                }
                return execution.execute(request, body);
            })
            .build();
    }
}

@Service
@Slf4j
public class InventoryClient {

    private final RestClient restClient;

    public StockResponse checkStock(String productId, int quantity) {
        log.debug("Checking stock for product: {}", productId);
        return restClient.get()
            .uri("/api/inventory/{productId}/stock?quantity={qty}", productId, quantity)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                throw new ProductNotFoundException("Product not found: " + productId);
            })
            .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                throw new ServiceUnavailableException("Inventory service unavailable");
            })
            .body(StockResponse.class);
    }

    public void reserveStock(ReserveStockRequest request) {
        restClient.post()
            .uri("/api/inventory/reserve")
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }
}
```

### WebClient (Reactive, Non-Blocking):

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient inventoryWebClient(
            @Value("${services.inventory.url}") String baseUrl) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(ExchangeFilterFunctions.basicAuthentication("user", "pass"))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }
}

@Service
public class InventoryWebClient {

    private final WebClient webClient;

    // Non-blocking call
    public Mono<StockResponse> checkStock(String productId) {
        return webClient.get()
            .uri("/api/inventory/{id}/stock", productId)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError,
                response -> Mono.error(new ProductNotFoundException(productId)))
            .bodyToMono(StockResponse.class)
            .timeout(Duration.ofSeconds(3))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                .filter(ex -> ex instanceof WebClientResponseException.ServiceUnavailable));
    }

    // Parallel calls
    public Mono<OrderEnrichment> enrichOrder(Long orderId) {
        Mono<UserDTO> userMono = webClient.get()
            .uri("http://USER-SERVICE/api/users/{id}", userId)
            .retrieve().bodyToMono(UserDTO.class);

        Mono<List<ProductDTO>> productsMono = webClient.get()
            .uri("http://PRODUCT-SERVICE/api/products/batch?ids={ids}", productIds)
            .retrieve().bodyToFlux(ProductDTO.class).collectList();

        return Mono.zip(userMono, productsMono)
            .map(tuple -> new OrderEnrichment(tuple.getT1(), tuple.getT2()));
    }
}
```

### Declarative Client (Feign-style with Spring HTTP Interface):

```java
// Spring 6+ HTTP Interface (replaces OpenFeign)
public interface InventoryClient {

    @GetExchange("/api/inventory/{productId}/stock")
    StockResponse checkStock(@PathVariable String productId,
                             @RequestParam int quantity);

    @PostExchange("/api/inventory/reserve")
    void reserveStock(@RequestBody ReserveStockRequest request);

    @DeleteExchange("/api/inventory/reservations/{reservationId}")
    void cancelReservation(@PathVariable String reservationId);
}

@Configuration
public class ClientConfig {

    @Bean
    public InventoryClient inventoryClient(
            @Value("${services.inventory.url}") String baseUrl) {
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
        
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(adapter).build();
        
        return factory.createClient(InventoryClient.class);
    }
}
```

### With Resilience:

```java
@Service
public class ResilientInventoryClient {

    private final InventoryClient inventoryClient;

    @CircuitBreaker(name = "inventory", fallbackMethod = "stockFallback")
    @Retry(name = "inventory")
    @TimeLimiter(name = "inventory")
    public StockResponse checkStock(String productId, int quantity) {
        return inventoryClient.checkStock(productId, quantity);
    }

    private StockResponse stockFallback(String productId, int quantity, Throwable t) {
        log.warn("Inventory unavailable, assuming in stock: {}", productId);
        return new StockResponse(productId, true, quantity);
    }
}
```

---

## Dry Run

### RestClient with error handling:

```
1. Order Service needs to check inventory:
   restClient.get().uri("/api/inventory/PROD-1/stock?quantity=5")

2. HTTP Request sent to Inventory Service (192.168.1.20:8082)

3. Scenario A: Success
   → 200 OK {"productId": "PROD-1", "available": true, "quantity": 50}
   → Deserialized to StockResponse
   → Returned to caller

4. Scenario B: Product not found
   → 404 Not Found
   → onStatus(4xx) triggers
   → Throws ProductNotFoundException
   → Handled by @RestControllerAdvice

5. Scenario C: Service down
   → Connection refused / timeout
   → IOException thrown
   → Retry (attempt 2 after 500ms)
   → Retry (attempt 3 after 1000ms)
   → Still failing → Circuit breaker opens
   → Fallback returns optimistic response
```

---

## Complexity

| Client | Blocking | Use Case |
|--------|----------|----------|
| RestClient | Yes (thread blocked) | Simple synchronous calls |
| WebClient | No (event loop) | High concurrency, reactive |
| HTTP Interface | Depends on adapter | Clean API, declarative |
| Kafka | No (fire and forget) | Event-driven, decoupled |

---

## Real Project Usage

### Service calling multiple downstream services:

```java
@Service
public class OrderEnrichmentService {

    private final RestClient userClient;
    private final RestClient productClient;
    private final ExecutorService executor;

    // Parallel synchronous calls using virtual threads
    public EnrichedOrder enrichOrder(Order order) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var userTask = scope.fork(() -> 
                userClient.get().uri("/users/{id}", order.getUserId())
                    .retrieve().body(UserDTO.class));
            
            var productsTask = scope.fork(() ->
                productClient.get().uri("/products/batch?ids={ids}", 
                    order.getProductIds())
                    .retrieve().body(new ParameterizedTypeReference<List<ProductDTO>>(){}));

            scope.join();
            scope.throwIfFailed();

            return new EnrichedOrder(order, userTask.get(), productsTask.get());
        }
    }
}
```

---

## Interview Questions

1. **RestClient vs WebClient — when to use which?**
   - RestClient: Synchronous, simple, most applications. WebClient: Non-blocking, high-concurrency, reactive pipelines, parallel calls.

2. **How to propagate context (auth, tracing) between services?**
   - Interceptors/filters add headers (JWT token, correlation ID, trace ID). Each client adds these automatically.

3. **How to handle partial failures (one service down)?**
   - Circuit breaker + fallback. Return partial data, cached data, or graceful degradation.

4. **Synchronous vs Asynchronous communication — trade-offs?**
   - Sync: Simple, immediate consistency, cascading failures. Async: Decoupled, resilient, eventually consistent, complex debugging.

5. **How does load balancing work with service-to-service calls?**
   - Client-side LB (Spring Cloud LoadBalancer): Client fetches instance list from registry, picks one. Works with `lb://SERVICE-NAME` URI prefix.

---

## Common Mistakes

1. **No timeout configured** - Thread hangs indefinitely on slow service
2. **No retry for transient failures** - Single failure = user error (should retry)
3. **Retrying non-idempotent calls** - POST without idempotency key creates duplicates
4. **Not propagating context** - Correlation ID, auth token lost between services
5. **Too tight coupling** - Calling 5 services synchronously in sequence
6. **No connection pool tuning** - Default settings inadequate for production load

---

## Best Practices

1. **Use RestClient for new projects** (Spring Boot 3.2+)
2. **Configure timeouts** on all HTTP clients (connect + read)
3. **Add resilience patterns** (circuit breaker + retry + timeout)
4. **Propagate correlation ID** for distributed tracing
5. **Prefer async (Kafka) for non-critical operations** (notifications, analytics)
6. **Use sync only when you need immediate response** (stock check before order)
7. **Implement proper error handling** with status-specific handlers
8. **Tune connection pools** for expected concurrent calls

---

## Production Considerations

- **Connection pooling**: Configure max connections per host
- **Timeouts**: Set aggressive timeouts (2-5s for inter-service calls)
- **mTLS**: Mutual TLS for service-to-service authentication
- **Service mesh**: Istio handles retries, timeouts, mTLS at infrastructure level
- **Tracing**: All calls should be traced (Micrometer Tracing / OpenTelemetry)
- **DNS caching**: Be aware of DNS TTL for service discovery

---

## Related Topics

- Microservices with Spring Boot
- Resilience Patterns
- Spring Cloud (service discovery, load balancing)
- Kafka (async communication)
- Reactive Spring (WebClient)
- Distributed Tracing
