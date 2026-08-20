# 36. Advanced Spring Internals

## Theory

Understanding Spring internals separates a Spring Boot developer from someone who truly understands the framework. This covers the full startup sequence, bean creation pipeline, proxy mechanisms, and internal abstractions.

### Application Startup Flow:
```
main() → SpringApplication.run()
  → Determine WebApplicationType (SERVLET/REACTIVE/NONE)
  → Create ApplicationContext
  → Load Environment (properties, profiles)
  → Publish ApplicationStartingEvent
  → Configure Environment
  → Create BeanFactory
  → Load BeanDefinitions (scanning + auto-config)
  → BeanFactoryPostProcessors execute
  → Register BeanPostProcessors
  → Initialize MessageSource (i18n)
  → Initialize ApplicationEventMulticaster
  → Create embedded web server
  → Create all singleton beans
  → Publish ApplicationReadyEvent
```

### Key Internal Classes:
- **BeanDefinition**: Blueprint for a bean (class, scope, dependencies, init method)
- **BeanFactory**: Core container — creates and manages beans
- **BeanFactoryPostProcessor**: Modifies BeanDefinitions BEFORE beans are created
- **BeanPostProcessor**: Modifies bean instances AFTER creation (proxies created here)
- **ApplicationContext**: Extended container with enterprise features
- **FactoryBean<T>**: Special bean that produces other beans
- **ProxyFactory**: Creates AOP proxies (CGLIB or JDK Dynamic Proxy)

### Proxy Types:
- **JDK Dynamic Proxy**: Interface-based, creates proxy implementing the interface
- **CGLIB Proxy**: Subclass-based, creates subclass of target class (Spring default)

---

## Internal Working

### Full Bean Creation Pipeline:

```
Step 1: BeanDefinition Loading
  ┌─────────────────────────────────────────────────────┐
  │ Sources:                                             │
  │  - @ComponentScan → ClassPathBeanDefinitionScanner  │
  │  - @Configuration + @Bean → ConfigurationClassParser│
  │  - AutoConfiguration.imports → deferred loading     │
  │  - @Import → direct import of config classes        │
  └────────────────────────┬────────────────────────────┘
                           ↓
Step 2: BeanFactoryPostProcessor Execution
  ┌─────────────────────────────────────────────────────┐
  │ ConfigurationClassPostProcessor:                     │
  │  - Processes @Configuration classes                 │
  │  - Handles @Import, @ComponentScan                  │
  │  - Creates CGLIB proxy for @Configuration classes   │
  │                                                     │
  │ PropertySourcesPlaceholderConfigurer:               │
  │  - Resolves ${...} placeholders in BeanDefinitions  │
  └────────────────────────┬────────────────────────────┘
                           ↓
Step 3: Bean Instantiation
  ┌─────────────────────────────────────────────────────┐
  │ For each BeanDefinition (topological order):        │
  │  1. Resolve constructor arguments (dependencies)    │
  │  2. Instantiate via reflection (or CGLIB)           │
  │  3. Populate properties (field/setter injection)    │
  │  4. Aware interface callbacks                       │
  │  5. BeanPostProcessor.before (→ @PostConstruct)    │
  │  6. InitializingBean.afterPropertiesSet()          │
  │  7. Custom init-method                              │
  │  8. BeanPostProcessor.after (→ AOP PROXY CREATED) │
  │  9. Register in singleton cache                     │
  └─────────────────────────────────────────────────────┘
                           ↓
Step 4: Application Ready
  ApplicationReadyEvent published
```

### Proxy Creation (BeanPostProcessor.postProcessAfterInitialization):

```
AbstractAutoProxyCreator.postProcessAfterInitialization(bean, beanName)
       ↓
Check: Does any Advisor/Aspect match this bean's methods?
  - @Transactional → TransactionAttributeSourceAdvisor
  - @Cacheable → BeanFactoryCacheOperationSourceAdvisor
  - @Async → AsyncAnnotationAdvisor
  - Custom @Aspect pointcuts
       ↓
If match found:
  - ProxyFactory created
  - Target bean wrapped
  - Advisors added to proxy
       ↓
Proxy type decision:
  - Bean implements interface? → JDK Dynamic Proxy (default pre-Spring Boot 2.0)
  - No interface OR proxyTargetClass=true? → CGLIB Proxy (Spring Boot default)
       ↓
PROXY returned to container (original bean hidden behind proxy)
       ↓
When method called:
  Client → Proxy → Interceptor chain → Target method
```

### Three-Level Cache (Circular Dependency Resolution):

```
singletonObjects (1st cache):
  Fully initialized beans (final state)

earlySingletonObjects (2nd cache):
  Early references (partially initialized, proxy if needed)

singletonFactories (3rd cache):
  ObjectFactory that can create early reference

Resolution flow for A → B → A:
1. Creating A: constructor called, put ObjectFactory in 3rd cache
2. A needs B: start creating B
3. B needs A: found ObjectFactory in 3rd cache
4. Call ObjectFactory → creates early reference of A → put in 2nd cache
5. B gets early reference of A, B completes
6. A gets B, A completes → moved to 1st cache
```

---

## Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                    SPRING CONTAINER INTERNALS                    │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              BeanDefinition Registry                       │  │
│  │  {orderService → BD(class, scope, deps, init)}            │  │
│  │  {orderRepo → BD(class, scope, deps)}                    │  │
│  └──────────────────────────┬───────────────────────────────┘  │
│                              ↓                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           BeanFactoryPostProcessors                       │  │
│  │  ConfigurationClassPostProcessor                          │  │
│  │  PropertySourcesPlaceholderConfigurer                     │  │
│  │  (Modify BeanDefinitions BEFORE bean creation)            │  │
│  └──────────────────────────┬───────────────────────────────┘  │
│                              ↓                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Bean Creation Engine                          │  │
│  │                                                            │  │
│  │  createBean() → instantiate → inject → init              │  │
│  └──────────────────────────┬───────────────────────────────┘  │
│                              ↓                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              BeanPostProcessors                            │  │
│  │                                                            │  │
│  │  AutowiredAnnotationBPP → handles @Autowired              │  │
│  │  CommonAnnotationBPP → handles @PostConstruct, @Resource  │  │
│  │  AbstractAutoProxyCreator → creates AOP PROXIES           │  │
│  │  AsyncAnnotationBPP → creates @Async proxies              │  │
│  └──────────────────────────┬───────────────────────────────┘  │
│                              ↓                                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Singleton Cache                               │  │
│  │  {orderService → PROXY(OrderService)}                     │  │
│  │  {orderRepo → SimpleJpaRepository proxy}                  │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘

Proxy Wrapping:
┌──────────────┐     ┌──────────────────────────────────────┐
│   Client     │ ──→ │  CGLIB Proxy ($$EnhancerBySpring)    │
│              │     │    │                                  │
│              │     │    ├── TransactionInterceptor         │
│              │     │    ├── CacheInterceptor               │
│              │     │    └── Target: OrderService (real)    │
└──────────────┘     └──────────────────────────────────────┘
```

---

## Code

### Custom BeanFactoryPostProcessor:

```java
@Component
public class CustomBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // Executes BEFORE any beans are created
        // Can modify BeanDefinitions

        String[] beanNames = beanFactory.getBeanDefinitionNames();
        for (String name : beanNames) {
            BeanDefinition bd = beanFactory.getBeanDefinition(name);
            // Example: Force all services to be lazy-init
            if (bd.getBeanClassName() != null && 
                bd.getBeanClassName().endsWith("Service")) {
                bd.setLazyInit(true);
            }
        }
    }
}
```

### Custom BeanPostProcessor:

```java
@Component
public class TimingBeanPostProcessor implements BeanPostProcessor {

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        startTimes.put(beanName, System.currentTimeMillis());
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        long startTime = startTimes.remove(beanName);
        long duration = System.currentTimeMillis() - startTime;
        if (duration > 100) {
            log.warn("Slow bean initialization: {} took {}ms", beanName, duration);
        }
        return bean;  // Must return bean (or a proxy wrapping it)
    }
}
```

### FactoryBean:

```java
// FactoryBean creates complex objects
public class HttpClientFactoryBean implements FactoryBean<HttpClient> {

    private int connectTimeout = 5000;
    private int readTimeout = 10000;

    @Override
    public HttpClient getObject() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeout))
            .build();
    }

    @Override
    public Class<?> getObjectType() {
        return HttpClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}

// Usage: getBean("httpClientFactory") → returns HttpClient
// getBean("&httpClientFactory") → returns the FactoryBean itself
```

### Understanding Proxy Behavior:

```java
@Service
@Transactional
public class OrderService {

    // THIS WORKS: Called from outside → goes through proxy
    public void placeOrder(Order order) {
        saveOrder(order);  // Self-invocation — NO proxy!
    }

    // THIS IS IGNORED: @Transactional here has no effect via self-call
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }
}

// Why: placeOrder() is proxied, but this.saveOrder() bypasses proxy
// Fix: Extract saveOrder to separate service
```

### Inspecting Bean Definitions:

```java
@Component
public class BeanInspector implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        ConfigurableApplicationContext configCtx = (ConfigurableApplicationContext) ctx;
        BeanFactory factory = configCtx.getBeanFactory();

        String[] names = ctx.getBeanDefinitionNames();
        log.info("Total beans: {}", names.length);

        for (String name : names) {
            Object bean = ctx.getBean(name);
            boolean isProxy = AopUtils.isAopProxy(bean);
            if (isProxy) {
                log.info("PROXY bean: {} → target: {}", name, 
                    AopUtils.getTargetClass(bean).getSimpleName());
            }
        }
    }
}
```

---

## Dry Run

### Bean Creation with @Transactional:

```
1. ComponentScan finds OrderService.class
2. BeanDefinition created: {class=OrderService, scope=singleton}

3. Bean creation starts:
   a. Constructor: new OrderService(orderRepository)
   b. Field injection: none
   c. BeanPostProcessor.before:
      - CommonAnnotationBPP: looks for @PostConstruct → found, executes
   d. BeanPostProcessor.after:
      - AbstractAutoProxyCreator: checks advisors
      - TransactionAttributeSourceAdvisor matches (class has @Transactional)
      - ProxyFactory created
      - CGLIB proxy generated (OrderService$$EnhancerBySpringCGLIB)
      - Proxy wraps original OrderService

4. Singleton cache: "orderService" → CGLIB Proxy

5. When controller calls orderService.placeOrder():
   → Proxy.placeOrder()
   → TransactionInterceptor.invoke()
   → Begin transaction
   → Target OrderService.placeOrder() (real method)
   → Commit transaction
   → Return to caller
```

---

## Complexity

| Operation | Time |
|-----------|------|
| BeanDefinition scanning | O(n) - classpath scan |
| BeanFactoryPostProcessor | O(n × m) - n definitions × m processors |
| Bean creation | O(1) per bean (constructor + reflection) |
| Dependency resolution | O(V+E) - topological sort |
| Proxy creation | O(k) - k = advisors to check per bean |
| Total startup (medium app) | 5-15 seconds |

---

## Real Project Usage

### Custom Auto-Configuration:

```java
@AutoConfiguration
@ConditionalOnClass(AuditService.class)
@ConditionalOnProperty(prefix = "app.audit", name = "enabled", havingValue = "true")
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditRepository repository) {
        return new DefaultAuditService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAspect auditAspect(AuditService auditService) {
        return new AuditAspect(auditService);
    }
}

// META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:
// com.example.audit.AuditAutoConfiguration
```

---

## Interview Questions

1. **What is the difference between BeanFactoryPostProcessor and BeanPostProcessor?**
   - BeanFactoryPostProcessor: Runs BEFORE beans are created. Modifies BeanDefinitions (metadata). Example: PropertySourcesPlaceholderConfigurer resolves ${} placeholders. BeanPostProcessor: Runs AFTER each bean is created. Modifies bean instances. Example: creates AOP proxies, handles @Autowired.

2. **How does Spring create proxies? When is CGLIB used vs JDK Dynamic Proxy?**
   - Spring Boot defaults to CGLIB (proxyTargetClass=true). CGLIB: Creates subclass of target class, works without interface. JDK: Creates proxy implementing target's interface, faster for interface-heavy code. CGLIB is default because not all services have interfaces.

3. **What is FactoryBean and when would you use it?**
   - A bean that acts as a factory for other beans. getObject() returns the produced bean. Use for: complex third-party object creation, conditional logic in bean creation, objects requiring non-trivial setup (connections, clients).

4. **How does @Configuration class work internally (CGLIB proxying)?**
   - Spring creates a CGLIB subclass of @Configuration classes. When @Bean methods call other @Bean methods, the proxy intercepts and returns the existing singleton instead of creating a new instance. This ensures inter-bean references maintain singleton semantics.

5. **What happens during `SpringApplication.run()` step by step?**
   - Create SpringApplication → Determine web type → Create ApplicationContext → Load Environment → Apply Initializers → Load BeanDefinitions (scan + auto-config) → Refresh context (create beans) → Start embedded server → Publish ReadyEvent.

---

## Follow-up Questions

1. How does Spring handle the circular dependency three-level cache?
   - singletonObjects (complete), earlySingletonObjects (proxied early ref), singletonFactories (ObjectFactory). On circular dep: A starts creation, adds factory to 3rd cache. B needs A, gets early reference from factory → promotes to 2nd cache. B completes. A completes → moved to 1st cache.

2. Why does self-invocation bypass the proxy?
   - `this.method()` calls the actual object directly, not the proxy. The proxy only intercepts external calls. The target bean holds a reference to itself (`this`), not to its proxy. Fix: inject self, extract to separate class, or use AopContext.currentProxy().

3. How does @ConditionalOnMissingBean work internally?
   - During auto-configuration processing, Spring checks if a bean of the specified type already exists in the BeanDefinitionRegistry. If user defined their own @Bean, auto-config skips. User beans always win over auto-configured ones.

4. What is the difference between @Import and @ComponentScan?
   - @ComponentScan: Scans packages for @Component classes (classpath scanning). @Import: Directly imports specific @Configuration or @Component classes (no scanning). @Import is faster and more explicit.

5. How do conditional annotations affect startup order?
   - Auto-config classes are processed AFTER user @Configuration. @Conditional evaluated at BeanDefinition registration time. Order matters: if condition depends on another bean, that bean must be defined first. @AutoConfigureAfter/@AutoConfigureBefore control ordering.

---

## Common Mistakes

1. **Confusing BeanFactoryPostProcessor with BeanPostProcessor** - Former modifies definitions, latter modifies instances
2. **Expecting @Bean inter-method calls to work in @Component** - Only @Configuration has CGLIB proxy for singleton semantics
3. **Not understanding proxy wrapping** - The bean in the container IS the proxy, not the original object
4. **Putting logic that depends on other beans in BeanFactoryPostProcessor** - Beans don't exist yet at that phase
5. **Circular dependency with constructor injection** - Three-level cache only works with setter/field injection

---

## Best Practices

1. **Understand the startup order** - BeanDefinitions → BeanFactoryPostProcessors → Bean creation → BeanPostProcessors
2. **Use @Configuration for @Bean methods** that reference each other (CGLIB singleton guarantee)
3. **Know when proxies are created** - After BeanPostProcessor.after phase
4. **Use FactoryBean for complex object creation** instead of cramming logic into @Bean methods
5. **Minimize custom BeanPostProcessors** - They run for EVERY bean (performance impact)
6. **Understand self-invocation** - Design services to avoid internal @Transactional/@Cacheable calls

---

## Production Considerations

- **Startup time**: More beans = slower startup. Use @Lazy, AOT compilation (Spring Boot 3), or GraalVM native image
- **Proxy overhead**: Minimal per-call (~microseconds), but understand proxy behavior for correctness
- **Memory**: Each proxy and BeanDefinition consumes memory. Monitor with Actuator /beans endpoint
- **Debugging**: Use `--debug` flag to see auto-configuration report. AopUtils.getTargetClass() to inspect proxied beans

---

## Related Topics

- Spring Framework Fundamentals (IoC/DI)
- Spring AOP (proxy mechanism)
- Spring Bean Management (lifecycle)
- Auto-Configuration Deep Dive
- Spring Boot Configuration
- Transactions (proxy-dependent)
