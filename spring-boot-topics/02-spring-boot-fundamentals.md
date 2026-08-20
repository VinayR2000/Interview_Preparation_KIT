# 2. Spring Boot Fundamentals

## Theory

**What is Spring Boot?**
Spring Boot is an opinionated framework built on top of Spring Framework that simplifies the creation of production-ready, stand-alone Spring applications. It eliminates boilerplate configuration through auto-configuration, starter dependencies, and embedded servers.

**Spring vs Spring Boot:**

| Aspect | Spring Framework | Spring Boot |
|--------|-----------------|-------------|
| Configuration | Manual (XML/Java) | Auto-configured |
| Server | External (Tomcat WAR) | Embedded (JAR) |
| Dependencies | Manual version management | Starters with managed versions |
| Setup time | Hours | Minutes |
| Production features | Manual setup | Built-in (Actuator) |

**Advantages of Spring Boot:**
- Rapid application development
- No XML configuration needed
- Embedded servers (Tomcat, Jetty, Undertow)
- Production-ready features (health checks, metrics)
- Opinionated defaults with easy overrides
- Dependency version management via BOMs

**Convention over Configuration:**
Spring Boot makes assumptions about what you need based on your classpath. Add `spring-boot-starter-web` → auto-configures DispatcherServlet, Jackson, embedded Tomcat.

**Auto-configuration:**
Spring Boot examines your classpath and automatically configures beans. Uses `@Conditional` annotations to decide what to configure.

**Starter Dependencies:**
Curated sets of dependencies grouped by feature:
- `spring-boot-starter-web` — Web + REST
- `spring-boot-starter-data-jpa` — JPA + Hibernate
- `spring-boot-starter-security` — Spring Security
- `spring-boot-starter-test` — Testing libraries

**Important Annotations:**

```
@SpringBootApplication
       |
       +── @Configuration         (this class is a config source)
       +── @EnableAutoConfiguration (enable auto-config magic)
       +── @ComponentScan          (scan this package & sub-packages)
```

---

## Internal Working

```
main() method called
       ↓
SpringApplication.run()
       ↓
Creates ApplicationContext
       ↓
Loads application.properties/yml
       ↓
Component Scanning (@ComponentScan)
       ↓
Auto-configuration classes loaded
  (from META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
       ↓
@Conditional evaluation
  - @ConditionalOnClass
  - @ConditionalOnMissingBean
  - @ConditionalOnProperty
       ↓
Beans created & wired
       ↓
Embedded server started (Tomcat default, port 8080)
       ↓
Application ready
       ↓
ApplicationReadyEvent published
```

**Auto-configuration Decision Flow:**
```
Is DataSource.class on classpath?
  ├── YES → Is spring.datasource.url configured?
  │          ├── YES → Create DataSource bean
  │          └── NO  → Create embedded H2/HSQL
  └── NO  → Skip DataSource auto-config

Is there already a DataSource @Bean defined?
  ├── YES → Skip (user's bean wins) — @ConditionalOnMissingBean
  └── NO  → Create auto-configured one
```

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│                  Spring Boot App                      │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │            Your Application Code              │    │
│  │  @Controllers, @Services, @Repositories       │    │
│  └─────────────────────────────────────────────┘    │
│                       ↕                              │
│  ┌─────────────────────────────────────────────┐    │
│  │          Auto-Configuration Layer             │    │
│  │  DataSource, JPA, Security, Web MVC           │    │
│  └─────────────────────────────────────────────┘    │
│                       ↕                              │
│  ┌─────────────────────────────────────────────┐    │
│  │          Spring Framework Core                │    │
│  │  IoC Container, AOP, Events                   │    │
│  └─────────────────────────────────────────────┘    │
│                       ↕                              │
│  ┌─────────────────────────────────────────────┐    │
│  │          Embedded Server (Tomcat)             │    │
│  │  Port 8080, Servlet Container                 │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘

Executable JAR Structure:
my-app.jar
├── BOOT-INF/
│   ├── classes/          (your compiled code)
│   └── lib/              (all dependency JARs)
├── META-INF/
│   └── MANIFEST.MF       (Main-Class: JarLauncher)
└── org/springframework/boot/loader/  (Spring Boot loader)
```

---

## Code

```java
// === Main Application Class ===
@SpringBootApplication
public class ECommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceApplication.class, args);
    }
}

// === Customizing SpringApplication ===
public class ECommerceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ECommerceApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setDefaultProperties(Map.of("server.port", "9090"));
        app.run(args);
    }
}

// === ApplicationRunner (run logic after startup) ===
@Component
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;

    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            userRepository.save(new User("admin", "admin@example.com"));
        }
    }
}

// === CommandLineRunner alternative ===
@Bean
public CommandLineRunner initData(ProductRepository repo) {
    return args -> {
        repo.save(new Product("Laptop", BigDecimal.valueOf(999.99)));
        repo.save(new Product("Mouse", BigDecimal.valueOf(29.99)));
    };
}
```

**pom.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>ecommerce</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Dry Run

**Scenario**: `mvn spring-boot:run` with a simple REST app

```
1. JVM starts, loads ECommerceApplication.class
2. main() → SpringApplication.run()
3. SpringApplication:
   a. Determines WebApplicationType = SERVLET
   b. Creates AnnotationConfigServletWebServerApplicationContext
   c. Loads application.properties (server.port=8080)
4. Component scan finds: ProductController, ProductService, ProductRepository
5. Auto-configuration activates:
   - ServletWebServerFactoryAutoConfiguration → EmbeddedTomcat
   - JacksonAutoConfiguration → ObjectMapper bean
   - DataSourceAutoConfiguration → HikariDataSource (H2 on classpath)
   - JpaRepositoriesAutoConfiguration → JPA repos
6. Tomcat starts on port 8080
7. DispatcherServlet registered at "/"
8. Console: "Started ECommerceApplication in 2.3 seconds"
9. App ready to accept HTTP requests
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Application startup (simple) | 1-3 seconds |
| Application startup (large enterprise) | 10-30 seconds |
| Auto-configuration evaluation | O(n) — n = auto-config classes |
| Component scanning | O(n) — n = classes in scanned packages |
| First request handling | ~50ms (JIT not warm) |
| Subsequent requests | <5ms (JIT optimized) |

**Memory footprint:**
- Minimal Spring Boot app: ~100MB heap
- Medium app (JPA, Security): ~200-400MB
- Large microservice: ~512MB-1GB

---

## Real Project Usage

```java
// Typical microservice structure
com.company.orderservice
├── OrderServiceApplication.java        (@SpringBootApplication)
├── config/
│   ├── WebConfig.java                  (@Configuration)
│   └── SecurityConfig.java            (@Configuration)
├── controller/
│   └── OrderController.java           (@RestController)
├── service/
│   └── OrderService.java              (@Service)
├── repository/
│   └── OrderRepository.java           (JpaRepository)
├── model/
│   ├── entity/Order.java              (@Entity)
│   └── dto/OrderRequest.java
└── exception/
    ├── GlobalExceptionHandler.java    (@RestControllerAdvice)
    └── OrderNotFoundException.java
```

---

## Interview Questions

1. **What is Spring Boot and how is it different from Spring?**
   - Spring Boot is an opinionated framework on top of Spring that provides auto-configuration, embedded servers, and starter dependencies. Spring requires manual configuration; Spring Boot provides sensible defaults. Spring deploys as WAR; Spring Boot runs as executable JAR.

2. **What does @SpringBootApplication do internally?**
   - It's a composite annotation combining: @Configuration (config source), @EnableAutoConfiguration (trigger auto-config), @ComponentScan (scan current package and sub-packages for beans).

3. **How does auto-configuration work? Can you override it?**
   - Spring Boot loads auto-config classes from META-INF and evaluates @Conditional annotations (OnClass, OnMissingBean, OnProperty). Yes, you can override by defining your own @Bean — @ConditionalOnMissingBean ensures user beans win.

4. **What is the difference between executable JAR and WAR?**
   - JAR: Self-contained with embedded Tomcat, run with `java -jar`. WAR: Requires external Tomcat deployment. JAR is the standard for Spring Boot; WAR needed only for legacy app servers.

5. **How does Spring Boot decide which beans to auto-configure?**
   - Based on classpath contents and @Conditional annotations. E.g., DataSource auto-configured only if JDBC driver on classpath AND no DataSource @Bean already defined.

6. **What is the spring-boot-starter-parent? Is it mandatory?**
   - Parent POM providing dependency management (version alignment), plugin configuration, and default settings. Not mandatory — you can use spring-boot-dependencies BOM instead with dependencyManagement.

7. **How do you disable a specific auto-configuration?**
   - `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` or property `spring.autoconfigure.exclude=...`

8. **What is the difference between @SpringBootApplication and putting @Configuration + @EnableAutoConfiguration + @ComponentScan separately?**
   - Functionally identical. @SpringBootApplication is just a convenience annotation combining all three. Separate annotations give more control over scan paths.

9. **How does Spring Boot handle embedded servers?**
   - Auto-configures based on classpath: Tomcat (default), Jetty, or Undertow. Creates a WebServer bean, binds to configured port. No external server needed.

10. **What is the order of bean initialization in Spring Boot?**
    - Auto-configuration classes processed after user @Configuration. Within those: dependency order determines creation sequence. @DependsOn can force ordering. @Order affects advice/filter ordering, not bean creation.

---

## Follow-up Questions

1. **After Q2**: "What if I don't want component scanning on the main package?"
   → Use `@ComponentScan(basePackages = "com.specific.package")` or exclude filters.

2. **After Q5**: "What are @Conditional annotations? Name some."
   → `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`, `@ConditionalOnWebApplication`

3. **After Q7**: "How do you disable DataSource auto-configuration?"
   → `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` or `spring.autoconfigure.exclude` property.

4. **After Q4**: "Can you deploy Spring Boot as WAR to external Tomcat?"
   → Yes. Extend `SpringBootServletInitializer`, change packaging to WAR, mark embedded Tomcat as `provided`.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Putting @SpringBootApplication in root package | Scans ALL packages including libraries | Place in company-specific base package |
| Not using starters, adding individual deps | Version conflicts, missing transitive deps | Use spring-boot-starter-* |
| Ignoring auto-configuration reports | Don't know what's being configured | Run with `--debug` flag |
| Hardcoding port in application.properties | Breaks in containers/cloud | Use `server.port=0` for random or env vars |
| Fat main class with @Bean methods | Hard to maintain | Separate @Configuration classes |
| Not using spring-boot-devtools in dev | Slow development cycle | Add devtools dependency |

---

## Best Practices

1. **Use starters** — don't manually manage dependency versions
2. **Keep main class clean** — only @SpringBootApplication, no @Bean methods
3. **Use `--debug` flag** to understand auto-configuration decisions
4. **Externalize configuration** — don't hardcode values
5. **Use profiles** — dev, test, prod environments
6. **Add Actuator** — health checks, metrics from day one
7. **Use DevTools** in development — auto-restart, live reload
8. **Structure packages by feature**, not by layer
9. **Use Spring Boot BOM** if not using starter-parent
10. **Run `mvn dependency:tree`** to verify no conflicts

---

## Production Considerations

- **JVM tuning**: Set `-Xmx`, `-Xms` appropriately. Default heap may be too large.
- **Startup time**: Use Spring Boot 3.x AOT or GraalVM native for fast startup.
- **Graceful shutdown**: `server.shutdown=graceful` — waits for active requests.
- **Health endpoints**: Enable Actuator for Kubernetes probes.
- **Logging**: Configure structured logging (JSON) for log aggregation.
- **Security**: Disable DevTools in production, secure Actuator endpoints.
- **Container optimization**: Use layered JARs for efficient Docker builds.
- **Monitoring**: Expose Prometheus metrics via Actuator.

---

## Related Topics

- → [1. Spring Framework Fundamentals](#) (foundation)
- → [3. Spring Boot Configuration](#) (properties, profiles)
- → [4. Spring Bean Management](#) (bean lifecycle)
- → [24. Spring Boot Actuator](#) (production features)
- → [33. Spring Boot + Docker](#) (deployment)
