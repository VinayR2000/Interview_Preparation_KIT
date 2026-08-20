# 38. Spring Boot Startup Debugging

## Theory

When a Spring Boot application fails to start, the errors can be cryptic. Understanding common startup exceptions, their causes, and debugging techniques is essential for rapid problem resolution.

### Common Startup Exceptions:

| Exception | Cause |
|-----------|-------|
| BeanCreationException | Bean instantiation failed (constructor/init error) |
| UnsatisfiedDependencyException | Required dependency not found |
| NoSuchBeanDefinitionException | Bean referenced but not registered |
| NoUniqueBeanDefinitionException | Multiple beans match, no @Qualifier/@Primary |
| BeanCurrentlyInCreationException | Circular dependency (constructor injection) |
| ConfigurationPropertiesBindException | Properties can't bind to @ConfigurationProperties |
| PortAlreadyInUseException | Server port already taken |
| DataSourceAutoConfiguration failure | DB connection properties missing/wrong |

### Debugging Tools:
- `--debug` flag: Shows auto-configuration report
- `TRACE` logging: Detailed bean creation logs
- Actuator `/conditions` endpoint (after startup)
- Stack trace analysis
- `spring.main.allow-circular-references` for circular dep debugging

---

## Internal Working

```
Spring Boot fails to start
       ↓
Exception thrown during context refresh
       ↓
┌──────────────────────────────────────────────────────┐
│ Exception Type Analysis:                              │
│                                                       │
│ BeanCreationException                                 │
│   └─ Caused by: UnsatisfiedDependencyException       │
│       └─ Caused by: NoSuchBeanDefinitionException    │
│                                                       │
│ Read bottom-up: Root cause is at the END             │
│                                                       │
│ Root cause: "No qualifying bean of type              │
│  'com.example.UserRepository' available"            │
│                                                       │
│ Translation: UserRepository not found because:       │
│  - Not in scanned package?                          │
│  - Missing @Repository annotation?                   │
│  - JPA auto-config disabled?                        │
│  - Wrong base package in @EnableJpaRepositories?     │
└──────────────────────────────────────────────────────┘
```

### Debug Flag Output:
```
$ java -jar app.jar --debug

============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
  DataSourceAutoConfiguration matched:
    - @ConditionalOnClass found 'javax.sql.DataSource'

Negative matches:
  DataSourceAutoConfiguration.EmbeddedDatabase:
    Did not match:
      - EmbeddedDatabase not found (no H2/HSQL on classpath)

  → If you expected a DataSource but it wasn't configured,
    check: JDBC driver on classpath? Properties set?
```

---

## Diagram

```
┌────────────────────────────────────────────────────────────────┐
│              STARTUP FAILURE DECISION TREE                       │
│                                                                  │
│  Exception?                                                      │
│  │                                                               │
│  ├── BeanCreationException                                       │
│  │   ├── Constructor threw exception → Fix constructor logic    │
│  │   ├── @PostConstruct failed → Fix init logic                 │
│  │   └── UnsatisfiedDependencyException                         │
│  │       ├── NoSuchBeanDefinition → Bean not registered         │
│  │       │   ├── Not in scanned package?                        │
│  │       │   ├── Missing annotation (@Service, @Component)?     │
│  │       │   ├── Conditional excluded it?                       │
│  │       │   └── Wrong profile active?                          │
│  │       └── NoUniqueBeanDefinition → Multiple candidates       │
│  │           └── Add @Primary or @Qualifier                     │
│  │                                                               │
│  ├── BeanCurrentlyInCreationException                           │
│  │   └── Circular dependency (constructor injection)            │
│  │       ├── Refactor to remove circularity                     │
│  │       ├── Use @Lazy on one injection                         │
│  │       └── Switch to setter injection (last resort)           │
│  │                                                               │
│  ├── ConfigurationPropertiesBindException                       │
│  │   └── Property value can't convert to field type            │
│  │       ├── Typo in property name?                             │
│  │       ├── Wrong type (String vs Integer)?                    │
│  │       └── Missing property that's @NotNull validated?        │
│  │                                                               │
│  └── PortAlreadyInUseException                                  │
│      └── Another process on port 8080                           │
│          ├── Kill existing process                              │
│          └── Use different port: --server.port=8081             │
└────────────────────────────────────────────────────────────────┘
```

---

## Code

### Debugging Configurations:

```yaml
# application.yml — debugging settings
logging:
  level:
    org.springframework.beans.factory: DEBUG
    org.springframework.boot.autoconfigure: DEBUG
    org.springframework.context.annotation: TRACE
    org.hibernate: WARN

# Show auto-configuration report
debug: true

# Allow circular references (TEMPORARY for debugging only)
spring:
  main:
    allow-circular-references: false  # true only for diagnosis
```

### Common Error Scenarios and Fixes:

```java
// ERROR: NoSuchBeanDefinitionException for Repository
// Cause: @SpringBootApplication in wrong package
@SpringBootApplication  // Scans com.example.app and below
package com.example.app;

// But Repository is in com.example.repository (OUTSIDE scan!)
// Fix 1: Move @SpringBootApplication to com.example
// Fix 2: @EnableJpaRepositories("com.example.repository")

// ERROR: NoUniqueBeanDefinitionException
// Cause: Two implementations of same interface
@Service
public class EmailNotifier implements Notifier { }
@Service
public class SmsNotifier implements Notifier { }

@Service
public class OrderService {
    // FAILS: Which Notifier?
    public OrderService(Notifier notifier) { }
}
// Fix: @Primary on one, or @Qualifier on injection point

// ERROR: BeanCurrentlyInCreationException
// Cause: A needs B, B needs A (constructor injection)
@Service
public class ServiceA {
    public ServiceA(ServiceB b) { }  // Needs B
}
@Service
public class ServiceB {
    public ServiceB(ServiceA a) { }  // Needs A → CIRCULAR!
}
// Fix: Break the cycle — use events, extract common logic, or @Lazy

// ERROR: ConfigurationPropertiesBindException
// Cause: Type mismatch
// application.yml has: app.timeout: "not-a-number"
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private int timeout;  // Can't bind "not-a-number" to int
}
```

### Startup Event Listener for Diagnostics:

```java
@Component
@Slf4j
public class StartupDiagnostics implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private ApplicationContext context;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Application started successfully");
        log.info("Active profiles: {}", Arrays.toString(context.getEnvironment().getActiveProfiles()));
        log.info("Bean count: {}", context.getBeanDefinitionCount());

        // List all beans (dev only)
        if (context.getEnvironment().acceptsProfiles(Profiles.of("dev"))) {
            Arrays.stream(context.getBeanDefinitionNames())
                .sorted()
                .forEach(name -> log.debug("Bean: {}", name));
        }
    }
}
```

### FailureAnalyzer (Custom Error Messages):

```java
// Custom failure analyzer for better error messages
public class DatabaseConnectionFailureAnalyzer 
        extends AbstractFailureAnalyzer<CannotGetJdbcConnectionException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, 
                                      CannotGetJdbcConnectionException cause) {
        return new FailureAnalysis(
            "Cannot connect to database: " + cause.getMessage(),
            "Check that:\n" +
            "  1. Database server is running\n" +
            "  2. spring.datasource.url is correct\n" +
            "  3. Credentials are valid\n" +
            "  4. Network allows connection (firewall, VPN)\n" +
            "  5. Database exists and user has access",
            cause
        );
    }
}

// Register in META-INF/spring.factories:
// org.springframework.boot.diagnostics.FailureAnalyzer=\
//   com.example.DatabaseConnectionFailureAnalyzer
```

---

## Dry Run

### Debugging "UnsatisfiedDependencyException":

```
Error:
  BeanCreationException: Error creating bean 'orderController':
    Unsatisfied dependency expressed through constructor parameter 0;
    nested exception: NoSuchBeanDefinitionException:
      No qualifying bean of type 'OrderService' available

Step 1: Read stack trace bottom-up
  → Root cause: OrderService bean not found

Step 2: Check OrderService class
  → Has @Service annotation? YES ✓
  → In a scanned package? Check...

Step 3: Check package structure
  @SpringBootApplication → com.example.app
  OrderService → com.example.service.OrderService
  
  Is com.example.service UNDER com.example.app? NO!
  → com.example.app only scans com.example.app.**

Step 4: Fix
  Option A: Move @SpringBootApplication to com.example
  Option B: @ComponentScan(basePackages = "com.example")
  
Step 5: Restart → Success ✓
```

---

## Complexity

| Issue | Time to Debug |
|-------|--------------|
| PortAlreadyInUseException | ~30 seconds |
| Missing @Service annotation | ~1-2 minutes |
| Wrong package scanning | ~2-5 minutes |
| Circular dependency | ~5-15 minutes |
| Auto-config condition failure | ~5-10 minutes (with --debug) |
| Configuration binding error | ~2-5 minutes |
| Classpath conflict | ~10-30 minutes |

---

## Real Project Usage

### CI/CD Startup Validation:

```yaml
# Docker health check that verifies startup
HEALTHCHECK --interval=5s --timeout=3s --start-period=60s --retries=10 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

```java
// Fail fast if critical config is missing
@Component
public class StartupValidator {

    @Value("${app.critical.api-key}")
    private String apiKey;

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "CRITICAL: app.critical.api-key must be set. " +
                "Set via environment variable APP_CRITICAL_API_KEY");
        }
    }
}
```

---

## Interview Questions

1. **How do you debug a Spring Boot application that fails to start?**
   - Read exception stack trace bottom-up for root cause. Use `--debug` flag for auto-config report. Check: package scanning, annotations, properties, classpath. Common issues: wrong packages, missing beans, property binding errors.

2. **What causes NoSuchBeanDefinitionException and how to fix it?**
   - Bean not registered in container. Causes: missing @Component/@Service annotation, bean not in scanned package, excluded by condition, wrong profile active. Fix: verify annotation, check package, use --debug.

3. **How do you resolve circular dependencies?**
   - Refactor to eliminate cycle (best). Break with @Lazy on one injection point. Use events for communication instead of direct dependency. Last resort: setter injection. Never enable allow-circular-references in production.

4. **What is the auto-configuration report and how to read it?**
   - Run with `--debug` or GET /actuator/conditions. Shows: Positive matches (conditions that passed → bean created), Negative matches (conditions that failed → bean NOT created), and why each condition passed/failed.

5. **How do you handle configuration binding failures?**
   - Check property names match (relaxed binding rules). Verify types are compatible. Add @Validated with constraints for clear error messages. Use `spring.config.import` for required config sources.

---

## Follow-up Questions

1. What is a FailureAnalyzer and how to create one?
   - Spring Boot component that translates exceptions into human-readable messages. Extend AbstractFailureAnalyzer<YourException>. Override analyze() to return FailureAnalysis with description + action. Register in spring.factories.

2. How to handle classpath conflicts causing startup failures?
   - Run `mvn dependency:tree` to identify version conflicts. Use `<exclusions>` to remove conflicting transitive deps. Spring Boot BOM manages most versions. Check for multiple versions of same library.

3. What's the difference between ApplicationFailedEvent and ApplicationReadyEvent?
   - ApplicationFailedEvent: Published when startup fails (context not created). ApplicationReadyEvent: Published on successful startup. Use FailedEvent to log/alert on startup failures in monitoring.

4. How to debug bean creation order issues?
   - Enable TRACE logging for org.springframework.beans.factory. Use @DependsOn for explicit ordering. Check if @PostConstruct depends on another bean not yet created. Circular deps often masked as ordering issues.

5. How do you test that your application starts correctly in CI?
   - @SpringBootTest verifies full context loads. Testcontainers for DB/Redis dependencies. Docker health check with Actuator. Smoke test hitting /actuator/health after deployment.

---

## Common Mistakes

1. **Not reading stack trace bottom-up** - Root cause is at the end, not the top
2. **Adding allow-circular-references=true** - Hides design problem, causes issues later
3. **Wrong @SpringBootApplication package** - Everything outside that package is invisible
4. **Ignoring the --debug report** - Guessing instead of reading what Spring tells you
5. **Missing JDBC driver** - DataSource auto-config fails silently without driver
6. **Environment variable typos** - SPRING_DATASOURC_URL (missing 'E') → property not bound

---

## Best Practices

1. **Always read full stack trace** - Root cause is nested at the bottom
2. **Use --debug flag** as first diagnostic step
3. **Fail fast** - Validate critical config in @PostConstruct
4. **Custom FailureAnalyzers** for team-specific common errors
5. **CI startup test** - @SpringBootTest catches most issues before deployment
6. **Keep packages under @SpringBootApplication** package
7. **Use @Validated on @ConfigurationProperties** - Clear binding error messages
8. **Log active profiles** at startup for environment verification

---

## Production Considerations

- **Startup time monitoring**: Track application startup duration in metrics
- **Health check timing**: Set K8s startup probe to accommodate slow starts
- **Configuration validation**: Fail fast if required config is missing (don't proceed with defaults)
- **Dependency health**: Check DB/Redis/Kafka connectivity on startup
- **Rollback strategy**: If new version fails to start, auto-rollback in CI/CD
- **Crash loop detection**: K8s CrashLoopBackOff indicates repeated startup failure

---

## Related Topics

- Advanced Spring Internals (bean creation pipeline)
- Auto-Configuration Deep Dive (conditional beans)
- Spring Boot Configuration (property binding)
- Spring Bean Management (lifecycle)
- Docker + Kubernetes (health probes for startup)
