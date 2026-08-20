# 4. Spring Bean Management

## Theory

**Bean Creation Flow:**
```
Application starts
       ↓
Component scanning
       ↓
Bean definitions created
       ↓
Bean instantiation
       ↓
Dependency injection
       ↓
@PostConstruct
       ↓
Bean ready
```

**Core Concepts:**

- **BeanDefinition**: Metadata about a bean (class, scope, dependencies, init/destroy methods)
- **BeanFactory**: Basic container — creates, configures, manages beans
- **ApplicationContext**: Enhanced BeanFactory + AOP, events, i18n, resource loading
- **Dependency Resolution**: Container determines creation order via dependency graph

**Dependency Annotations:**
- `@Autowired` — Inject dependency (by type)
- `@Qualifier("name")` — Disambiguate when multiple beans of same type exist
- `@Primary` — Default bean when multiple candidates exist
- `@Lazy` — Initialize bean on first use, not at startup
- `@DependsOn("beanName")` — Force creation order

**Circular Dependency:**
A depends on B, B depends on A. Spring can resolve this with setter/field injection (not constructors). Spring Boot 2.6+ disallows circular deps by default.

**Bean Lifecycle Callbacks:**

| Phase | Interface | Annotation | Custom |
|-------|-----------|------------|--------|
| Init | InitializingBean | @PostConstruct | initMethod |
| Destroy | DisposableBean | @PreDestroy | destroyMethod |

---

## Internal Working

```
BeanDefinition Registration
       ↓
BeanFactoryPostProcessor (modify definitions BEFORE beans created)
  e.g., PropertySourcesPlaceholderConfigurer resolves ${...}
       ↓
Bean Instantiation (constructor called)
       ↓
Populate Properties (dependency injection)
       ↓
BeanNameAware.setBeanName()
       ↓
BeanFactoryAware.setBeanFactory()
       ↓
ApplicationContextAware.setApplicationContext()
       ↓
BeanPostProcessor.postProcessBeforeInitialization()
  (e.g., @PostConstruct handled here by CommonAnnotationBeanPostProcessor)
       ↓
InitializingBean.afterPropertiesSet()
       ↓
Custom init-method
       ↓
BeanPostProcessor.postProcessAfterInitialization()
  (AOP proxies created here!)
       ↓
Bean READY
       ↓ (on shutdown)
@PreDestroy
       ↓
DisposableBean.destroy()
       ↓
Custom destroy-method
```

**Singleton Creation:**
```
First request for bean
  → Check singleton cache (ConcurrentHashMap)
    → Found? Return cached instance
    → Not found?
      → Check "currently in creation" set (circular dep detection)
      → Create instance
      → Put in singleton cache
      → Return
```

**Prototype Creation:**
```
Every request for bean
  → Create NEW instance
  → Inject dependencies
  → Run @PostConstruct
  → Return (Spring does NOT manage lifecycle after this)
  → @PreDestroy is NEVER called by Spring for prototypes!
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────┐
│                  Bean Lifecycle                           │
│                                                          │
│  ┌───────────┐    ┌───────────┐    ┌───────────────┐   │
│  │ Instantiate│───▶│  Populate │───▶│  Aware        │   │
│  │(constructor)│    │(DI inject)│    │  interfaces   │   │
│  └───────────┘    └───────────┘    └───────────────┘   │
│                                           │              │
│                                           ▼              │
│  ┌───────────┐    ┌───────────┐    ┌───────────────┐   │
│  │   READY   │◀───│ BPP.after │◀───│ BPP.before    │   │
│  │           │    │(AOP proxy)│    │(@PostConstruct)│   │
│  └───────────┘    └───────────┘    └───────────────┘   │
│        │                                                 │
│        │ (shutdown)                                      │
│        ▼                                                 │
│  ┌───────────┐    ┌───────────┐    ┌───────────────┐   │
│  │ @PreDestroy│───▶│Disposable │───▶│Custom destroy │   │
│  └───────────┘    │Bean.destroy│    │   method      │   │
│                   └───────────┘    └───────────────┘   │
└─────────────────────────────────────────────────────────┘

Circular Dependency Resolution (Setter Injection):
┌─────────┐          ┌─────────┐
│  Bean A  │─ needs ─▶│  Bean B  │
│(created) │          │(creating)│
└─────────┘          └─────────┘
      ▲                    │
      └──── needs ─────────┘
      
Spring exposes "early reference" of A (before fully initialized)
B gets this early reference, completes.
Then A completes with fully initialized B.
```

---

## Code

```java
// === Complete Lifecycle Demo ===
@Component
public class OrderProcessor implements InitializingBean, DisposableBean,
        BeanNameAware, ApplicationContextAware {

    private String beanName;
    private ApplicationContext applicationContext;
    private ExecutorService executor;

    // 1. Constructor (instantiation)
    public OrderProcessor() {
        System.out.println("1. Constructor called");
    }

    // 2. BeanNameAware
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("2. BeanNameAware: " + name);
    }

    // 3. ApplicationContextAware
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.applicationContext = ctx;
        System.out.println("3. ApplicationContextAware");
    }

    // 4. @PostConstruct (BeanPostProcessor handles this)
    @PostConstruct
    public void postConstruct() {
        System.out.println("4. @PostConstruct");
        this.executor = Executors.newFixedThreadPool(10);
    }

    // 5. InitializingBean
    @Override
    public void afterPropertiesSet() {
        System.out.println("5. InitializingBean.afterPropertiesSet()");
    }

    // 6. @PreDestroy
    @PreDestroy
    public void preDestroy() {
        System.out.println("6. @PreDestroy");
        executor.shutdown();
    }

    // 7. DisposableBean
    @Override
    public void destroy() {
        System.out.println("7. DisposableBean.destroy()");
    }
}

// === @Qualifier and @Primary ===
public interface NotificationSender {
    void send(String to, String message);
}

@Component
@Primary  // This is the default
public class EmailNotificationSender implements NotificationSender {
    @Override
    public void send(String to, String message) {
        // send email
    }
}

@Component("sms")
public class SmsNotificationSender implements NotificationSender {
    @Override
    public void send(String to, String message) {
        // send SMS
    }
}

@Service
public class AlertService {

    private final NotificationSender defaultSender;  // Gets @Primary (email)
    private final NotificationSender smsSender;

    public AlertService(NotificationSender defaultSender,
                        @Qualifier("sms") NotificationSender smsSender) {
        this.defaultSender = defaultSender;
        this.smsSender = smsSender;
    }
}

// === @Lazy ===
@Component
@Lazy  // Only created when first injected/requested
public class HeavyReportGenerator {

    public HeavyReportGenerator() {
        // Expensive initialization — loads templates, connects to services
        System.out.println("Heavy initialization...");
    }
}

// === @DependsOn ===
@Component
@DependsOn("databaseMigration")  // Ensure migrations run first
public class DataService {
    // ...
}

// === Prototype with Singleton Problem & Solution ===
@Component
@Scope("prototype")
public class ShoppingCart {
    private List<String> items = new ArrayList<>();
    public void addItem(String item) { items.add(item); }
}

// WRONG: Cart is injected once, same instance always used
@Service
public class ShoppingServiceWrong {
    private final ShoppingCart cart; // Same cart for all users!
    public ShoppingServiceWrong(ShoppingCart cart) {
        this.cart = cart;
    }
}

// CORRECT: Use ObjectFactory
@Service
public class ShoppingServiceCorrect {

    private final ObjectFactory<ShoppingCart> cartFactory;

    public ShoppingServiceCorrect(ObjectFactory<ShoppingCart> cartFactory) {
        this.cartFactory = cartFactory;
    }

    public ShoppingCart getNewCart() {
        return cartFactory.getObject(); // New instance every time
    }
}

// === Custom BeanPostProcessor ===
@Component
public class LoggingBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean.getClass().isAnnotationPresent(Service.class)) {
            System.out.println("Initializing service: " + beanName);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // AOP proxies are typically created here
        return bean;
    }
}
```

---

## Dry Run

**Scenario**: App with OrderService → OrderRepository, both @PostConstruct

```
1. Spring starts, scans components
2. Found: OrderService (depends on OrderRepository), OrderRepository
3. Dependency graph: OrderService → OrderRepository
4. Create OrderRepository first:
   a. Constructor called
   b. No dependencies to inject
   c. BeanPostProcessor.before → @PostConstruct runs
   d. BeanPostProcessor.after → check if proxy needed
   e. Stored in singleton cache
5. Create OrderService:
   a. Constructor called with OrderRepository reference
   b. Field/setter injection if any
   c. BeanPostProcessor.before → @PostConstruct runs
   d. BeanPostProcessor.after → AOP proxy created if @Transactional
   e. Stored in singleton cache (the PROXY is stored, not raw object)
6. All beans ready

On shutdown (Ctrl+C / SIGTERM):
1. OrderService.@PreDestroy called
2. OrderRepository.@PreDestroy called
   (reverse order of creation)
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| Bean lookup from singleton cache | O(1) — ConcurrentHashMap |
| Dependency resolution | O(V+E) — topological sort |
| Circular dependency detection | O(V) — "in creation" set check |
| @PostConstruct invocation | O(1) per bean |
| BeanPostProcessor chain | O(n*m) — n beans × m processors |
| Full context refresh | O(n) — n = total beans |

---

## Real Project Usage

```java
// Real-world: Database migration must run before JPA
@Component
@DependsOn("flywayMigration")
public class JpaDataInitializer {
    @PostConstruct
    public void seedData() { /* insert default data */ }
}

// Real-world: Warm up cache on startup
@Service
public class ProductCacheWarmer {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    @PostConstruct
    public void warmUp() {
        List<Product> popular = productRepository.findTopSelling(100);
        Cache cache = cacheManager.getCache("products");
        popular.forEach(p -> cache.put(p.getId(), p));
    }
}

// Real-world: Graceful shutdown
@Service
public class OrderProcessingService {

    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private volatile boolean shuttingDown = false;

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## Interview Questions

1. **Describe the complete Spring Bean lifecycle.**
   - Constructor → Dependency Injection → Aware interfaces (BeanNameAware, ApplicationContextAware) → BeanPostProcessor.before → @PostConstruct → InitializingBean.afterPropertiesSet → custom init → BeanPostProcessor.after (proxy creation) → READY. Shutdown: @PreDestroy → DisposableBean.destroy → custom destroy.

2. **What is the difference between BeanFactory and ApplicationContext?**
   - BeanFactory: Lazy initialization, basic DI. ApplicationContext: Eager initialization, adds AOP, events, i18n, environment abstraction, annotation processing. ApplicationContext is what we use in Spring Boot.

3. **How does Spring resolve circular dependencies? When can't it?**
   - For setter/field injection: Three-level cache (singletonObjects, earlySingletonObjects, singletonFactories). Exposes early reference of partially-created bean. Cannot resolve with constructor injection — both beans need the other's constructor to complete.

4. **What is a BeanPostProcessor? Give an example of a built-in one.**
   - Interface with before/after initialization hooks that run for EVERY bean. Built-in: AutowiredAnnotationBeanPostProcessor (handles @Autowired), CommonAnnotationBeanPostProcessor (handles @PostConstruct/@PreDestroy), AbstractAutoProxyCreator (creates AOP proxies).

5. **What happens when a singleton bean depends on a prototype bean?**
   - The prototype is injected once at singleton creation time. All subsequent uses of the singleton use the SAME prototype instance. Fix: Use ObjectFactory<T>, Provider<T>, @Lookup method, or ApplicationContext.getBean().

6. **Explain @Qualifier vs @Primary. When would you use each?**
   - @Primary: Marks one bean as the default when multiple candidates exist (implicit selection). @Qualifier: Explicitly names which bean to inject (override @Primary). Use @Primary for common case, @Qualifier for specific injections.

7. **What is @Lazy and when should you use it?**
   - Delays bean initialization until first access (not at startup). Use for: expensive beans that aren't always needed, breaking circular dependencies, reducing startup time. Apply on class or injection point.

8. **What is the difference between @PostConstruct and InitializingBean?**
   - @PostConstruct: Standard Java annotation (javax/jakarta), cleaner, recommended. InitializingBean: Spring-specific interface, couples code to Spring. Both run after DI completes. @PostConstruct runs slightly before InitializingBean.afterPropertiesSet().

9. **How does @DependsOn work? When is it needed?**
   - Forces creation order between beans with no direct injection dependency. Needed when: bean needs side effect from another (DB migration before data access), or initialization ordering matters (cache warming after data load).

10. **What happens if @PostConstruct throws an exception?**
    - Bean creation fails, the bean is NOT registered in the container, application context fails to start (in most cases). @PreDestroy will NOT be called since the bean never fully initialized.

---

## Follow-up Questions

1. **After Q3**: "How does Spring Boot 2.6+ handle circular dependencies differently?"
   → Disallows by default. Must set `spring.main.allow-circular-references=true` to enable (not recommended).

2. **After Q4**: "What's the difference between BeanPostProcessor and BeanFactoryPostProcessor?"
   → BeanFactoryPostProcessor modifies BeanDefinitions BEFORE beans are created. BeanPostProcessor modifies bean instances AFTER creation.

3. **After Q5**: "What are all the solutions for prototype-in-singleton?"
   → `ObjectFactory<T>`, `Provider<T>`, `@Lookup` method, `ApplicationContext.getBean()`.

4. **After Q10**: "Does @PreDestroy run if @PostConstruct failed?"
   → No. If @PostConstruct throws, the bean is not registered, so @PreDestroy won't be called.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Expecting @PreDestroy on prototype beans | Spring doesn't manage prototype lifecycle | Manually cleanup or use custom destroyer |
| Circular deps with constructor injection | Cannot be resolved | Refactor or switch to setter injection |
| Heavy logic in constructors | Slows startup, hard to test | Move to @PostConstruct |
| Not understanding proxy wrapping | @Transactional on private methods won't work | Keep proxied methods public |
| Using @Autowired on multiple constructors | Only one can be @Autowired | Use single constructor (implicit @Autowired) |
| Forgetting @DependsOn for ordering | Race conditions at startup | Explicit ordering when needed |

---

## Best Practices

1. **Favor @PostConstruct over InitializingBean** — cleaner, standard annotation
2. **Use constructor injection** — makes dependencies explicit, enables immutability
3. **Avoid circular dependencies** — redesign with events or a mediator pattern
4. **Keep @PostConstruct fast** — delays startup; async init if heavy
5. **Always implement @PreDestroy** for resource-holding beans (threads, connections)
6. **Use @Primary for the common case**, @Qualifier for exceptions
7. **Prototype beans**: use ObjectFactory injection to get fresh instances
8. **Understand proxy creation** happens in BeanPostProcessor.after — this is why self-invocation breaks @Transactional
9. **Use @Lazy** for beans that are expensive but rarely used
10. **Monitor bean count** — fewer beans = faster startup

---

## Production Considerations

- **Startup time**: 500+ beans can take 5-15 seconds. Profile with `spring.main.lazy-initialization=true` for dev.
- **Memory leaks**: Prototype beans that hold resources but never get @PreDestroy.
- **Thread safety**: Singleton beans must be stateless or properly synchronized.
- **Graceful shutdown**: Always clean up threads, connections, file handles in @PreDestroy.
- **Monitoring**: Actuator's `/beans` endpoint shows all registered beans.
- **AOT (Ahead-of-Time)**: Spring Boot 3 can pre-compute bean definitions for faster startup.
- **Native images**: GraalVM eliminates runtime reflection for bean creation.

---

## Related Topics

- → [1. Spring Framework Fundamentals](#) (IoC/DI basics)
- → [17. Spring AOP](#) (proxies created in BeanPostProcessor)
- → [12. Transactions](#) (proxy-based, affected by bean lifecycle)
- → [20. Async Processing](#) (async proxy wrapping)
- → [21. Caching](#) (cache proxy wrapping)
