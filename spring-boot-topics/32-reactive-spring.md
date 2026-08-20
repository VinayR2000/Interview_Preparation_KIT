# 32. Reactive Spring (WebFlux)

## Theory

Spring WebFlux is the reactive-stack web framework in Spring, built on Project Reactor. It enables non-blocking, event-driven applications that handle more concurrent connections with fewer threads.

### Core Concepts:
- **Reactive Programming**: Data flows as streams, processed asynchronously
- **Non-blocking I/O**: Thread doesn't wait for I/O (database, HTTP); gets notified when ready
- **Backpressure**: Consumer controls how fast producer sends data
- **Publisher/Subscriber**: Reactive Streams specification

### Reactor Types:
- **Mono<T>**: 0 or 1 element (like Optional but async)
- **Flux<T>**: 0 to N elements (like Stream but async)

### MVC vs WebFlux:
```
MVC: 1 thread per request → thread waits during I/O → needs many threads
WebFlux: Event loop → thread never waits → few threads handle many requests
```

### When to Use WebFlux:
- High concurrency with many I/O-bound operations
- Streaming data (SSE, WebSocket)
- Microservices with many downstream calls
- NOT for CPU-bound operations (no benefit)

---

## Internal Working

```
Traditional MVC:
  Request → Thread assigned → DB call → Thread WAITS → Response → Thread released
  (200 threads handle 200 concurrent requests max)

WebFlux (Event Loop):
  Request → Event loop registers callback → Thread freed
  DB response ready → Event loop picks it up → Sends response
  (4 threads handle thousands of concurrent requests)

┌── Reactor Execution Model ─────────────────────────────────┐
│                                                              │
│  Mono.just("hello")                                         │
│    .map(s -> s.toUpperCase())    ← Transformation           │
│    .flatMap(s -> callDb(s))      ← Async operation          │
│    .subscribe(result -> ...)     ← Terminal (starts flow)   │
│                                                              │
│  Nothing happens until subscribe()!                         │
│  (Lazy evaluation - assembly vs execution)                  │
└──────────────────────────────────────────────────────────────┘
```

---

## Diagram

```
┌──────────── Spring MVC (Thread-per-Request) ───────────────┐
│                                                              │
│  Request-1 ──→ [Thread-1] ──→ DB ─ WAIT ─ Response ──→ ✓  │
│  Request-2 ──→ [Thread-2] ──→ DB ─ WAIT ─ Response ──→ ✓  │
│  Request-3 ──→ [Thread-3] ──→ DB ─ WAIT ─ Response ──→ ✓  │
│  Request-4 ──→ WAITING (no threads available)               │
│                                                              │
│  Thread Pool: [T1-busy] [T2-busy] [T3-busy]                │
│  Problem: 200 threads → max 200 concurrent requests        │
└──────────────────────────────────────────────────────────────┘

┌──────────── Spring WebFlux (Event Loop) ───────────────────┐
│                                                              │
│  Event Loop Thread (few threads, e.g., 4):                  │
│                                                              │
│  Request-1 ──→ Register DB callback ──→ Thread FREE        │
│  Request-2 ──→ Register DB callback ──→ Thread FREE        │
│  Request-3 ──→ Register DB callback ──→ Thread FREE        │
│  ...thousands more...                                       │
│                                                              │
│  DB-1 ready ──→ Event loop picks up ──→ Send Response-1    │
│  DB-2 ready ──→ Event loop picks up ──→ Send Response-2    │
│                                                              │
│  4 threads handling 10,000+ concurrent connections!         │
└──────────────────────────────────────────────────────────────┘
```

---

## Code

### WebFlux Controller:

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserDTO>> getUser(@PathVariable Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<UserDTO> getAllUsers() {
        return userService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserDTO> createUser(@Valid @RequestBody Mono<CreateUserRequest> request) {
        return request.flatMap(userService::create);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser(@PathVariable Long id) {
        return userService.delete(id);
    }

    // Server-Sent Events (streaming)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserDTO> streamUsers() {
        return userService.findAll()
            .delayElements(Duration.ofSeconds(1));  // One per second
    }
}
```

### Reactive Service:

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;  // R2DBC reactive repository
    private final WebClient notificationClient;

    public Mono<UserDTO> findById(Long id) {
        return userRepository.findById(id)
            .map(this::toDTO)
            .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    }

    public Flux<UserDTO> findAll() {
        return userRepository.findAll()
            .map(this::toDTO);
    }

    public Mono<UserDTO> create(CreateUserRequest request) {
        return Mono.just(request)
            .map(this::toEntity)
            .flatMap(userRepository::save)
            .map(this::toDTO)
            .doOnSuccess(user -> sendWelcomeNotification(user).subscribe());
    }

    // Parallel operations
    public Mono<UserProfile> getFullProfile(Long userId) {
        Mono<User> userMono = userRepository.findById(userId);
        Mono<List<Order>> ordersMono = getOrders(userId).collectList();
        Mono<Preferences> prefsMono = getPreferences(userId);

        return Mono.zip(userMono, ordersMono, prefsMono)
            .map(tuple -> new UserProfile(
                tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private Mono<Void> sendWelcomeNotification(UserDTO user) {
        return notificationClient.post()
            .uri("/api/notifications")
            .bodyValue(new WelcomeNotification(user.getEmail()))
            .retrieve()
            .bodyToMono(Void.class)
            .onErrorResume(e -> {
                log.warn("Failed to send notification: {}", e.getMessage());
                return Mono.empty();  // Don't fail user creation
            });
    }
}
```

### Reactive Repository (R2DBC):

```java
public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    Flux<User> findByStatus(String status);

    @Query("SELECT * FROM users WHERE email = :email")
    Mono<User> findByEmail(String email);

    Flux<User> findByNameContainingIgnoreCase(String name);
}
```

### Error Handling:

```java
@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<ErrorResponse> handleNotFound(UserNotFoundException ex) {
        return Mono.just(new ErrorResponse(404, ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<ErrorResponse> handleValidation(WebExchangeBindException ex) {
        List<String> errors = ex.getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
        return Mono.just(new ErrorResponse(400, "Validation failed", errors));
    }
}
```

### WebClient with Reactor Operators:

```java
@Service
public class OrderAggregationService {

    private final WebClient userClient;
    private final WebClient productClient;

    public Mono<EnrichedOrder> enrichOrder(Order order) {
        Mono<UserDTO> userMono = userClient.get()
            .uri("/api/users/{id}", order.getUserId())
            .retrieve()
            .bodyToMono(UserDTO.class)
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn(new UserDTO("Unknown User"));

        Flux<ProductDTO> productsFlux = Flux.fromIterable(order.getProductIds())
            .flatMap(id -> productClient.get()
                .uri("/api/products/{id}", id)
                .retrieve()
                .bodyToMono(ProductDTO.class)
                .onErrorResume(e -> Mono.empty()), 5);  // concurrency = 5

        return Mono.zip(userMono, productsFlux.collectList())
            .map(tuple -> new EnrichedOrder(order, tuple.getT1(), tuple.getT2()));
    }
}
```

---

## Dry Run

### Reactive pipeline execution:

```
userService.findById(42)

1. Pipeline ASSEMBLED (nothing executed yet):
   findById(42) → map(toDTO) → switchIfEmpty(error)

2. Subscriber subscribes (framework does this):
   → R2DBC sends non-blocking query to PostgreSQL
   → Event loop thread is FREE (handles other requests)

3. Database responds:
   → Event loop picks up response
   → map(toDTO) transforms User → UserDTO
   → Result delivered to subscriber
   → Response sent to client

Timeline:
  T=0ms:  Request received, pipeline subscribed
  T=0ms:  Thread released (non-blocking DB call)
  T=15ms: DB responds, event loop processes
  T=16ms: map() transforms data
  T=16ms: Response sent to client
  
  Thread was FREE from T=0 to T=15ms (handled other requests!)
```

### Parallel Mono.zip():

```
getFullProfile(userId = 42):

  T=0ms: All three Monos subscribed simultaneously:
    - userRepository.findById(42) → DB query sent
    - getOrders(42) → HTTP call sent  
    - getPreferences(42) → HTTP call sent

  T=15ms: User query returns
  T=25ms: Orders HTTP returns
  T=30ms: Preferences HTTP returns

  T=30ms: Mono.zip combines all three results
  T=31ms: UserProfile created and returned

  Total: 31ms (vs ~70ms if sequential: 15+25+30)
```

---

## Complexity

| Aspect | MVC | WebFlux |
|--------|-----|---------|
| Threads for 10K connections | ~10K | ~4-8 |
| Memory per connection | ~1MB (thread stack) | ~few KB |
| Throughput (I/O bound) | Limited by thread pool | Much higher |
| Throughput (CPU bound) | Similar | Similar (no benefit) |
| Code complexity | Simple | Higher (reactive operators) |
| Debugging | Easy (stack traces) | Hard (async, no clear stack) |

---

## Real Project Usage

### Streaming API for real-time dashboard:

```java
@GetMapping(value = "/orders/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<OrderEvent> liveOrderStream() {
    return orderEventPublisher.getEvents()
        .filter(event -> event.getType() == EventType.CREATED)
        .map(event -> new OrderEvent(event.getOrderId(), event.getTimestamp()));
}
```

---

## Interview Questions

1. **When should you choose WebFlux over MVC?**
   - High concurrency with I/O-bound operations. Many simultaneous connections (chat, streaming). When using reactive databases (R2DBC, MongoDB Reactive). NOT for CPU-bound work.

2. **What are Mono and Flux?**
   - Mono: Publisher of 0-1 elements (async Optional). Flux: Publisher of 0-N elements (async Stream). Both lazy — nothing happens until subscribed.

3. **What is backpressure?**
   - Consumer signals producer to slow down when overwhelmed. Prevents memory overflow. Reactor handles via request(n) signaling.

4. **Can you mix WebFlux and blocking calls?**
   - Technically yes, but defeats the purpose. Blocking calls in reactive pipeline block the event loop = performance disaster. Use `.subscribeOn(Schedulers.boundedElastic())` to offload blocking calls.

5. **Why is debugging reactive code harder?**
   - Async execution = no meaningful stack trace. Use `.checkpoint("description")`, `.log()`, and Hooks.onOperatorDebug() for better diagnostics.

---

## Common Mistakes

1. **Blocking calls in reactive pipeline** - Thread.sleep(), JDBC calls, synchronized blocks. Kills performance.
2. **Not subscribing** - Pipeline assembled but never executed (nothing happens!)
3. **subscribe() in service layer** - Breaks the reactive chain. Return Mono/Flux, let framework subscribe.
4. **Using WebFlux with JDBC** - Must use R2DBC for reactive DB access
5. **Treating Mono like CompletableFuture** - Mono is lazy, CF is eager
6. **Ignoring errors in reactive chains** - Errors propagate silently, killing the stream

---

## Best Practices

1. **Never block** in reactive pipeline (use .subscribeOn(boundedElastic) if unavoidable)
2. **Return Mono/Flux from controllers** (let framework subscribe)
3. **Use R2DBC** for reactive database access
4. **Handle errors** with onErrorResume, onErrorReturn, retry operators
5. **Use parallel operators** (flatMap with concurrency) for fan-out patterns
6. **Add timeouts** to all external calls
7. **Use .checkpoint()** for debugging

---

## Production Considerations

- **Thread pool sizing**: Event loop threads = CPU cores (don't increase)
- **Bounded elastic pool**: For rare blocking calls (limited, not unlimited)
- **Memory**: Flux with millions of items needs backpressure to prevent OOM
- **Monitoring**: Reactor metrics integrate with Micrometer
- **R2DBC maturity**: Less mature than JPA, fewer features (no lazy loading, etc.)
- **Team readiness**: Reactive has steep learning curve, team must be comfortable

---

## Related Topics

- Inter-Service Communication (WebClient)
- Spring Data (R2DBC for reactive)
- Kafka (reactive consumers)
- WebSocket (full-duplex reactive)
- Spring MVC (compare/contrast)
