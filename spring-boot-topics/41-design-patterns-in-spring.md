# 41. Design Patterns in Spring

## Theory

Spring Framework extensively uses design patterns internally and encourages their use in application code. Understanding which patterns Spring uses helps you leverage the framework effectively.

### Patterns in Spring:

| Pattern | Spring Usage |
|---------|-------------|
| Singleton | Default bean scope |
| Factory | BeanFactory, FactoryBean |
| Abstract Factory | BeanFactory (creates beans without knowing concrete type) |
| Builder | RestClient.builder(), WebClient.builder() |
| Proxy | @Transactional, @Cacheable, @Async (AOP proxies) |
| Template Method | JdbcTemplate, RestTemplate, JmsTemplate |
| Strategy | AuthenticationProvider, HandlerMapping |
| Observer | ApplicationEvent, @EventListener |
| Adapter | HandlerAdapter, MessageListenerAdapter |
| Decorator | BeanPostProcessor chain, Filter chain |
| Chain of Responsibility | Security Filter Chain, Interceptor chain |
| Repository | Spring Data Repositories |
| Front Controller | DispatcherServlet |

---

## Internal Working

### Proxy Pattern (most important in Spring):
```
@Transactional on OrderService:
       ↓
Spring creates CGLIB proxy (subclass of OrderService)
       ↓
Client calls: orderService.createOrder()
       ↓
Actually calls: Proxy.createOrder()
       ↓
Proxy: begin transaction → target.createOrder() → commit/rollback
       ↓
Same mechanism for: @Cacheable, @Async, @Retryable, security checks
```

### Template Method Pattern:
```
JdbcTemplate.query(sql, rowMapper):
  1. Get connection from pool       ← Template handles
  2. Create PreparedStatement       ← Template handles
  3. Execute query                   ← Template handles
  4. Map each row → rowMapper(rs)   ← YOU implement this
  5. Close resources                 ← Template handles
  6. Handle exceptions               ← Template handles

You provide the varying part; template handles the boilerplate.
```

### Strategy Pattern:
```
AuthenticationManager:
  Contains list of AuthenticationProviders (strategies)
  
  authenticate(token):
    for each provider:
      if provider.supports(token.class):
        return provider.authenticate(token)  ← Strategy selected at runtime

Implementations:
  - DaoAuthenticationProvider (username/password)
  - JwtAuthenticationProvider (JWT)
  - OAuth2AuthenticationProvider (OAuth2)
```

### Observer Pattern:
```
ApplicationEventPublisher.publishEvent(OrderCreatedEvent)
       ↓
ApplicationEventMulticaster
       ↓
Finds all @EventListener methods matching event type
       ↓
Invokes each listener (observers)
       ↓
Publisher doesn't know about or depend on listeners
```

---

## Diagram

```
┌──────────── PROXY PATTERN ───────────────────────────────────┐
│                                                               │
│  Client → PROXY → Target                                     │
│                                                               │
│  @Transactional:  Proxy adds transaction begin/commit        │
│  @Cacheable:      Proxy checks/updates cache                 │
│  @Async:          Proxy submits to thread pool               │
│  @Secured:        Proxy checks authorization                 │
│                                                               │
│  ┌────────┐    ┌─────────────────┐    ┌────────────────┐   │
│  │ Client │───→│ CGLIB Proxy      │───→│ OrderService   │   │
│  │        │    │                  │    │ (real bean)    │   │
│  │        │    │ TransactionAdv   │    │                │   │
│  │        │    │ CacheAdvisor     │    │ createOrder()  │   │
│  │        │    │ SecurityAdvisor  │    │                │   │
│  └────────┘    └─────────────────┘    └────────────────┘   │
└───────────────────────────────────────────────────────────────┘

┌──────────── TEMPLATE METHOD ─────────────────────────────────┐
│                                                               │
│  JdbcTemplate (invariant steps):                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 1. getConnection()                    [TEMPLATE]     │    │
│  │ 2. createStatement(sql)               [TEMPLATE]     │    │
│  │ 3. executeQuery()                     [TEMPLATE]     │    │
│  │ 4. for each row: rowMapper.mapRow(rs) [YOU PROVIDE]  │    │
│  │ 5. closeResources()                   [TEMPLATE]     │    │
│  │ 6. translateException()               [TEMPLATE]     │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  You only implement the part that varies (RowMapper)         │
└───────────────────────────────────────────────────────────────┘

┌──────────── CHAIN OF RESPONSIBILITY ─────────────────────────┐
│                                                               │
│  HTTP Request                                                 │
│       ↓                                                       │
│  SecurityFilter → CorsFilter → AuthFilter → RateLimitFilter  │
│       ↓              ↓              ↓              ↓          │
│  Each filter can:                                             │
│  - Process and pass to next                                   │
│  - Reject (break chain)                                       │
│  - Modify request/response                                    │
│       ↓                                                       │
│  DispatcherServlet (if all filters pass)                     │
└───────────────────────────────────────────────────────────────┘
```

---

## Code

### Strategy Pattern:

```java
// Strategy interface
public interface PricingStrategy {
    BigDecimal calculatePrice(Product product, Customer customer);
}

// Concrete strategies
@Component("regular")
public class RegularPricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(Product product, Customer customer) {
        return product.getBasePrice();
    }
}

@Component("premium")
public class PremiumPricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(Product product, Customer customer) {
        return product.getBasePrice().multiply(BigDecimal.valueOf(0.9)); // 10% discount
    }
}

@Component("wholesale")
public class WholesalePricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculatePrice(Product product, Customer customer) {
        return product.getBasePrice().multiply(BigDecimal.valueOf(0.7)); // 30% discount
    }
}

// Context — selects strategy at runtime
@Service
public class PricingService {

    private final Map<String, PricingStrategy> strategies;

    // Spring injects ALL PricingStrategy beans into this map (name → bean)
    public PricingService(Map<String, PricingStrategy> strategies) {
        this.strategies = strategies;
    }

    public BigDecimal getPrice(Product product, Customer customer) {
        String tier = customer.getTier(); // "regular", "premium", "wholesale"
        PricingStrategy strategy = strategies.getOrDefault(tier, strategies.get("regular"));
        return strategy.calculatePrice(product, customer);
    }
}
```

### Builder Pattern:

```java
// Spring's builder pattern usage
@Configuration
public class ClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
            .baseUrl("http://api.example.com")
            .defaultHeader("Accept", "application/json")
            .requestInterceptor(new LoggingInterceptor())
            .build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .build();  // Builder pattern
    }
}
```

### Observer Pattern (Spring Events):

```java
// Event (message)
public record OrderPlacedEvent(Long orderId, Long customerId, BigDecimal total) {}

// Publisher (subject)
@Service
public class OrderService {
    private final ApplicationEventPublisher publisher;

    @Transactional
    public Order placeOrder(OrderRequest request) {
        Order order = orderRepository.save(buildOrder(request));
        publisher.publishEvent(new OrderPlacedEvent(order.getId(), 
            order.getCustomerId(), order.getTotal()));
        return order;
    }
}

// Observers (listeners) — completely decoupled from publisher
@Component
public class EmailObserver {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendConfirmation(event.customerId(), event.orderId());
    }
}

@Component
public class AnalyticsObserver {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        analyticsService.trackOrder(event.total());
    }
}
```

### Decorator Pattern (custom filter):

```java
// Decorator adds behavior to existing filter chain
@Component
@Order(1)
public class RequestTimingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws Exception {
        long start = System.currentTimeMillis();
        
        chain.doFilter(request, response);  // Delegate to next in chain
        
        long duration = System.currentTimeMillis() - start;
        response.addHeader("X-Response-Time", duration + "ms");
    }
}
```

### Factory Pattern:

```java
// Factory that creates notification senders based on type
@Component
public class NotificationFactory {

    private final Map<String, NotificationSender> senders;

    public NotificationFactory(List<NotificationSender> allSenders) {
        this.senders = allSenders.stream()
            .collect(Collectors.toMap(NotificationSender::getType, Function.identity()));
    }

    public NotificationSender getSender(String type) {
        NotificationSender sender = senders.get(type);
        if (sender == null) {
            throw new IllegalArgumentException("Unknown notification type: " + type);
        }
        return sender;
    }
}

// Usage
notificationFactory.getSender("email").send(message);
notificationFactory.getSender("sms").send(message);
```

---

## Dry Run

### Strategy Pattern Execution:

```
1. Customer "John" has tier = "premium"
2. Product "Laptop" has basePrice = $1000

3. PricingService.getPrice(laptop, john):
   → tier = "premium"
   → strategies.get("premium") → PremiumPricingStrategy
   → calculatePrice($1000, john)
   → $1000 × 0.9 = $900

4. Customer "Acme Corp" has tier = "wholesale"
   → strategies.get("wholesale") → WholesalePricingStrategy
   → $1000 × 0.7 = $700

5. Adding new strategy: Just create new @Component implementing PricingStrategy
   → Spring auto-injects into Map → Available immediately
   → Open/Closed Principle: Open for extension, closed for modification
```

---

## Complexity

| Pattern | When to Use | Spring Integration |
|---------|-------------|-------------------|
| Strategy | Multiple algorithms, selectable at runtime | @Component map injection |
| Observer | Decouple event producers from consumers | ApplicationEvent system |
| Template Method | Reusable algorithm with customizable steps | JdbcTemplate, RestTemplate |
| Proxy | Add cross-cutting behavior transparently | AOP (@Transactional, etc.) |
| Builder | Complex object construction | RestClient.builder(), HttpSecurity |
| Factory | Object creation without specifying class | BeanFactory, custom factories |

---

## Real Project Usage

### E-commerce with Multiple Patterns:

```java
// Strategy: Payment processing
public interface PaymentProcessor {
    PaymentResult process(Order order, PaymentDetails details);
    String getType(); // "credit_card", "paypal", "crypto"
}

// Observer: Order lifecycle events
@EventListener
public void onPaymentProcessed(PaymentProcessedEvent event) { ... }

// Template: Email sending (template defines steps, you provide content)
public abstract class EmailTemplate {
    public final void send(String to) {
        String subject = getSubject();    // Abstract — you provide
        String body = buildBody();        // Abstract — you provide
        emailClient.send(to, subject, body); // Template handles
    }
    protected abstract String getSubject();
    protected abstract String buildBody();
}

// Proxy: Caching + Transaction on service methods
@Service
public class ProductService {
    @Cacheable("products")
    @Transactional(readOnly = true)
    public Product findById(Long id) { ... }  // Proxied by Spring
}
```

---

## Interview Questions

1. **What design patterns does Spring use internally?**
   - Singleton (bean scope), Factory (BeanFactory), Proxy (@Transactional AOP), Template Method (JdbcTemplate), Observer (ApplicationEvent), Strategy (AuthenticationProvider), Chain of Responsibility (Filter chain), Front Controller (DispatcherServlet).

2. **How does Spring implement the Proxy pattern?**
   - Through AOP. BeanPostProcessor creates CGLIB/JDK proxy wrapping the target bean. Proxy intercepts method calls to add behavior (transactions, caching, security) without modifying target code. Client is unaware they're calling a proxy.

3. **How would you implement Strategy pattern in Spring Boot?**
   - Define strategy interface. Create multiple @Component implementations. Inject all via `Map<String, Strategy>` or `List<Strategy>`. Select strategy at runtime based on context. Spring auto-wires all implementations.

4. **What is Template Method pattern in Spring? Give examples.**
   - Abstract class defines algorithm skeleton with customizable steps. JdbcTemplate: handles connection/exception/cleanup, you provide RowMapper. RestTemplate: handles HTTP boilerplate, you provide request/response types.

5. **How does Chain of Responsibility work in Spring Security?**
   - SecurityFilterChain: Each filter processes request and either passes to next filter (doFilter) or rejects (return error response). Order matters. Filters handle: CSRF, authentication, authorization, exception translation sequentially.

---

## Follow-up Questions

1. How does the Observer pattern in Spring differ from traditional GoF implementation?
   - Traditional: Observers register directly with subject. Spring: Observers are beans discovered by Spring (no registration needed). Publisher uses ApplicationEventPublisher (doesn't know listeners). More decoupled via IoC container.

2. When would you use Factory vs Strategy pattern?
   - Factory: Need to CREATE different objects based on input (object creation concern). Strategy: Need to EXECUTE different algorithms on same data (behavior concern). Factory returns objects; Strategy performs operations.

3. How does Singleton in Spring differ from GoF Singleton?
   - GoF Singleton: One instance per JVM (static, global access). Spring Singleton: One instance per ApplicationContext (multiple contexts = multiple instances). Spring singleton is NOT a true GoF singleton — it's container-managed.

4. Can you combine patterns? Give a Spring example.
   - @Transactional uses Proxy + Decorator (proxy wraps bean, TransactionInterceptor decorates the call). SecurityFilterChain is Chain of Responsibility + Strategy (each filter is a strategy for specific security concern).

5. How does DispatcherServlet implement Front Controller?
   - Single entry point for ALL web requests. Routes to appropriate handler based on URL/method mapping. Centralizes common concerns (exception handling, view resolution, content negotiation). Controllers don't handle raw servlet requests.

---

## Common Mistakes

1. **Over-engineering with patterns** - Don't add pattern if only one implementation exists
2. **Not leveraging Spring's built-in patterns** - Reimplementing what Spring provides (events, DI-based strategy)
3. **Singleton with mutable state** - Spring singletons must be thread-safe (stateless preferred)
4. **Ignoring proxy behavior** - Self-invocation bypasses proxied behavior (@Transactional, @Cacheable)
5. **God class instead of strategy** - Giant if/else blocks instead of strategy pattern
6. **Tight coupling instead of observer** - Direct method calls instead of events for side effects

---

## Best Practices

1. **Use Spring DI for Strategy pattern** - Inject Map/List of implementations
2. **Use ApplicationEvent for Observer** - Built-in, decoupled, transactional-aware
3. **Understand proxy implications** - Self-invocation, final methods, proxy type
4. **Prefer composition over inheritance** - Spring DI enables this naturally
5. **Keep patterns simple** - Use when they solve a real problem, not for resume
6. **Leverage Spring's Template classes** - Don't reinvent resource management
7. **Use Builder for complex configuration** - Fluent APIs for setup (RestClient, Security)

---

## Production Considerations

- **Strategy selection performance**: Map lookup is O(1) — negligible overhead
- **Event listener failures**: Consider error handling strategy (log, retry, DLQ)
- **Proxy overhead**: ~microseconds per call — measure only in extreme hot paths
- **Pattern testability**: Strategies are independently testable (mock each implementation)
- **Adding new strategies**: Just add new @Component — no existing code changes needed

---

## Related Topics

- Spring AOP (Proxy pattern implementation)
- Spring Events (Observer pattern)
- Spring Security (Chain of Responsibility)
- JdbcTemplate (Template Method)
- Spring Bean Management (Singleton, Factory)
- SOLID Principles (patterns enforce these)
