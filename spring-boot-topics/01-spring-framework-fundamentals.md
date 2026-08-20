# 1. Spring Framework Fundamentals

## Theory

**What is Spring?**
Spring is a comprehensive, lightweight, open-source application framework for Java that provides infrastructure support for developing enterprise applications. It handles the plumbing so developers can focus on business logic.

**Problems Spring Solves:**
- Tight coupling between components
- Boilerplate code for enterprise services (transactions, security, messaging)
- Hard-to-test code due to direct instantiation
- Complex configuration management
- Cross-cutting concerns (logging, security, transactions)

**Spring Framework Modules:**
- **Core Container**: Beans, Core, Context, SpEL
- **Data Access**: JDBC, ORM, JMS, Transactions
- **Web**: MVC, WebSocket, Servlet
- **AOP**: Aspect-Oriented Programming
- **Test**: Testing support

**IoC (Inversion of Control):**
The principle where the control of object creation and lifecycle is transferred from the application code to the Spring container. Instead of your code creating dependencies, the framework provides them.

**DI (Dependency Injection):**
A design pattern and mechanism to implement IoC. The container "injects" dependencies into objects rather than objects creating their own dependencies.

**Dependency Injection Types:**

| Type | Mechanism | Recommended |
|------|-----------|-------------|
| Constructor Injection | Via constructor parameters | ✅ Yes (immutable, testable) |
| Setter Injection | Via setter methods | For optional dependencies |
| Field Injection | Via @Autowired on fields | ❌ No (hard to test) |

**IoC Container:**
- **BeanFactory**: Basic container, lazy initialization, lightweight
- **ApplicationContext**: Advanced container, extends BeanFactory, eager initialization, event publishing, i18n, AOP integration

**Spring Bean:**
Any object managed by the Spring IoC container.

**Bean Scopes:**

| Scope | Description |
|-------|-------------|
| Singleton | One instance per Spring container (default) |
| Prototype | New instance every time requested |
| Request | One instance per HTTP request |
| Session | One instance per HTTP session |
| Application | One instance per ServletContext |
| WebSocket | One instance per WebSocket session |

**Component Scanning Annotations:**
- `@Component` — Generic stereotype
- `@Service` — Business logic layer
- `@Repository` — Data access layer (+ exception translation)
- `@Controller` — MVC controller (returns views)
- `@RestController` — REST controller (@Controller + @ResponseBody)
- `@Bean` — Method-level, explicit bean declaration
- `@Configuration` — Class declares @Bean methods (full CGLIB proxy)

---

## Internal Working

```
Application Starts
       ↓
Spring scans packages (@ComponentScan)
       ↓
Finds classes with @Component, @Service, @Repository, @Controller
       ↓
Creates BeanDefinition for each
       ↓
Resolves dependencies (topological sort)
       ↓
Creates bean instances (reflection)
       ↓
Injects dependencies
       ↓
Calls @PostConstruct / InitializingBean
       ↓
Bean is ready for use
       ↓
On shutdown: @PreDestroy / DisposableBean
```

**IoC Container Internals:**
1. `BeanDefinitionReader` reads config (annotations/XML)
2. `BeanDefinitionRegistry` stores definitions
3. `BeanFactory` creates instances
4. `BeanPostProcessor` hooks modify beans (AOP proxies created here)
5. `ApplicationContext` provides additional enterprise services

---

## Diagram

```
┌─────────────────────────────────────────────────┐
│              Spring IoC Container                 │
│                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐  │
│  │  Bean A  │───▶│  Bean B  │───▶│  Bean C  │  │
│  │(@Service)│    │(@Repo)   │    │(DataSrc) │  │
│  └──────────┘    └──────────┘    └──────────┘  │
│                                                  │
│  BeanFactory → ApplicationContext                │
│  BeanPostProcessors                              │
│  BeanDefinitionRegistry                          │
└─────────────────────────────────────────────────┘

Constructor Injection Flow:
┌────────────┐         ┌────────────┐
│ OrderService│◀───DI───│  Container │
│            │         │            │
│ constructor│         │ resolves   │
│ (repo)     │         │ OrderRepo  │
└────────────┘         └────────────┘
```

---

## Code

```java
// === Constructor Injection (Recommended) ===
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    // @Autowired is optional with single constructor (Spring 4.3+)
    public OrderService(OrderRepository orderRepository,
                        NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
    }

    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        notificationService.sendConfirmation(order);
        return order;
    }
}

// === Setter Injection (Optional dependencies) ===
@Service
public class ReportService {

    private EmailService emailService;

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }
}

// === Field Injection (Avoid in production) ===
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository; // Hard to test!
}

// === @Configuration + @Bean ===
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

// === Bean Lifecycle ===
@Component
public class CacheManager {

    @PostConstruct
    public void init() {
        System.out.println("Cache warming up...");
        // Load frequently accessed data
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Clearing cache...");
        // Release resources
    }
}

// === Prototype Scope ===
@Component
@Scope("prototype")
public class ShoppingCart {
    private List<Item> items = new ArrayList<>();
}

// === Qualifier ===
public interface PaymentGateway { void pay(BigDecimal amount); }

@Component("stripe")
public class StripeGateway implements PaymentGateway { ... }

@Component("paypal")
public class PayPalGateway implements PaymentGateway { ... }

@Service
public class PaymentService {
    public PaymentService(@Qualifier("stripe") PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

---

## Dry Run

**Scenario**: Application starts with `OrderService` depending on `OrderRepository`.

```
1. Spring Boot starts → triggers @ComponentScan on base package
2. Scanner finds:
   - OrderService.class (@Service)
   - OrderRepository.class (@Repository)
3. Creates BeanDefinition for each
4. Dependency resolver sees OrderService needs OrderRepository
5. Creates OrderRepository first (no dependencies)
6. Creates OrderService, passes OrderRepository via constructor
7. Both beans registered in ApplicationContext as singletons
8. Subsequent requests for OrderService return SAME instance
```

**Singleton vs Prototype dry run:**
```
// Singleton (default)
OrderService s1 = context.getBean(OrderService.class);
OrderService s2 = context.getBean(OrderService.class);
s1 == s2  // TRUE — same object

// Prototype
ShoppingCart c1 = context.getBean(ShoppingCart.class);
ShoppingCart c2 = context.getBean(ShoppingCart.class);
c1 == c2  // FALSE — different objects
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| Bean lookup by type | O(1) — cached in map |
| Bean lookup by name | O(1) — HashMap |
| Component scanning | O(n) — scans classpath once at startup |
| Dependency resolution | O(V+E) — topological sort on dependency graph |
| Singleton retrieval | O(1) — pre-instantiated |
| Prototype creation | O(1) per call — new instance each time |

**Startup cost**: Linear with number of beans. 1000 beans ~ 2-5 seconds typically.

---

## Real Project Usage

**E-Commerce Application:**
```java
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    // Constructor injection — all dependencies explicit
    public OrderService(OrderRepository orderRepository,
                        InventoryService inventoryService,
                        PaymentService paymentService,
                        NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }
}
```

**Third-party library integration:**
```java
@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
```

---

## Interview Questions

1. **What is IoC and how does Spring implement it?**
   - IoC (Inversion of Control) means the framework controls object creation/lifecycle, not your code. Spring implements it via the IoC container (ApplicationContext) which creates beans, resolves dependencies, and manages their lifecycle.

2. **What is the difference between BeanFactory and ApplicationContext?**
   - BeanFactory: Basic container, lazy bean initialization, lightweight. ApplicationContext: Extends BeanFactory with eager initialization, event publishing, AOP integration, i18n, and annotation support. Always use ApplicationContext in practice.

3. **Explain the Spring Bean lifecycle in detail.**
   - Instantiation → Dependency Injection → BeanNameAware → BeanFactoryAware → ApplicationContextAware → BeanPostProcessor.before → @PostConstruct → InitializingBean → custom init → BeanPostProcessor.after (AOP proxy) → Ready. Shutdown: @PreDestroy → DisposableBean → custom destroy.

4. **What are the different bean scopes? When would you use prototype?**
   - Singleton (default), Prototype, Request, Session, Application, WebSocket. Use Prototype when each consumer needs its own instance (e.g., a stateful object like ShoppingCart, or a builder/helper that holds mutable state).

5. **Why is constructor injection preferred over field injection?**
   - Immutability (final fields), explicit dependencies, easily testable (pass mocks via constructor), fails fast if dependency missing, no reflection needed, prevents circular dependencies.

6. **What happens if two beans of the same type exist? How do you resolve it?**
   - NoUniqueBeanDefinitionException at startup. Resolve with: @Primary (mark default), @Qualifier("name") (select specific one), or inject as List<T> to get all.

7. **What is the difference between @Component and @Bean?**
   - @Component: Class-level annotation, auto-detected via scanning. @Bean: Method-level in @Configuration class, gives you full control of instantiation. Use @Bean for third-party classes or complex creation logic.

8. **What is the difference between @Configuration and @Component?**
   - @Configuration classes are CGLIB-proxied so @Bean methods return the same singleton even when called multiple times internally. @Component classes are not proxied — calling @Bean methods returns new instances each time (lite mode).

9. **How does Spring resolve circular dependencies?**
   - For setter/field injection: Three-level cache — exposes early (incomplete) bean reference. Bean A partially created, early ref exposed, B gets ref to A, B completes, then A completes. Constructor injection: CANNOT resolve — throws BeanCurrentlyInCreationException.

10. **What is component scanning and how does it work?**
    - Spring scans specified packages (from @ComponentScan or @SpringBootApplication's package) for classes annotated with @Component/@Service/@Repository/@Controller, creates BeanDefinitions for them, and registers them in the container.

---

## Follow-up Questions

1. **After Q5**: "What if you have 10 dependencies in a constructor — isn't that unwieldy?"
   → Suggests class has too many responsibilities; refactor using SRP.

2. **After Q9**: "Can circular dependencies be resolved with constructor injection?"
   → No. Only with setter/field injection. Spring throws BeanCurrentlyInCreationException for constructor circular deps.

3. **After Q8**: "What does @Configuration's CGLIB proxy actually do?"
   → Ensures @Bean methods return the same singleton instance when called multiple times within the config class (inter-bean references).

4. **After Q4**: "What happens if a singleton bean depends on a prototype bean?"
   → The prototype is injected once; subsequent calls use the same instance. Fix with `ObjectFactory<T>`, `Provider<T>`, or `@Lookup`.

5. **After Q7**: "When would you use @Bean over @Component?"
   → For third-party classes you can't annotate, or when bean creation requires complex logic.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Using field injection everywhere | Untestable, hides dependencies | Use constructor injection |
| Forgetting @Configuration on config class | @Bean methods won't be proxied, singletons break | Always use @Configuration |
| Expecting prototype bean to create new instance inside singleton | Singleton holds one reference | Use ObjectFactory or @Lookup |
| Circular dependency with constructors | Impossible to resolve | Refactor or use setter injection |
| Not making injected fields `final` | Allows accidental mutation | Always `private final` with constructor DI |
| Scanning too broad a package | Picks up unintended beans, slows startup | Be specific with @ComponentScan |
| Multiple beans of same type without @Qualifier | NoUniqueBeanDefinitionException | Use @Qualifier or @Primary |

---

## Best Practices

1. **Always use constructor injection** — immutable, testable, explicit
2. **Make dependencies `private final`** — prevents accidental reassignment
3. **Use @Configuration for bean definitions** — ensures proper singleton semantics
4. **Keep @ComponentScan specific** — don't scan the whole classpath
5. **Use @Primary for default implementations** — cleaner than qualifiers everywhere
6. **Favor composition over inheritance** — Spring DI makes this natural
7. **Keep beans stateless** (especially singletons) — thread safety
8. **Use meaningful bean names** — helps debugging and qualification
9. **Avoid circular dependencies** — design smell, refactor
10. **Use profiles for environment-specific beans** — `@Profile("prod")`

---

## Production Considerations

- **Startup time**: Many beans = slower startup. Use `@Lazy` for rarely-used heavy beans.
- **Memory**: Each singleton lives for application lifetime. Watch for beans holding large state.
- **Thread safety**: Singleton beans are shared across threads. Keep them stateless or use ThreadLocal.
- **GC pressure**: Prototype-scoped beans without proper cleanup can cause memory leaks.
- **Classpath scanning**: In large monoliths, narrow the scan packages.
- **Bean post-processors**: Custom BPPs run for every bean — keep them fast.
- **Graceful shutdown**: Implement @PreDestroy for resource cleanup (connections, threads).

---

## Related Topics

- → [2. Spring Boot Fundamentals](#) (builds on Spring Core)
- → [4. Spring Bean Management](#) (deep dive into beans)
- → [17. Spring AOP](#) (proxy mechanism tied to DI)
- → [12. Transactions](#) (proxy-based, relies on DI)
- → [15. Spring Security](#) (filter chain uses DI)
