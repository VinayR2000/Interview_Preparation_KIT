# 3. Spring Boot Configuration

## Theory

**Configuration Files:**
- `application.properties` — Key-value pairs (simple, flat)
- `application.yml` — YAML format (hierarchical, readable)

Both are equivalent; YAML is preferred for complex nested configs.

**Configuration Concepts:**
Spring Boot resolves properties from multiple sources with a defined precedence order.

**Property Precedence (highest to lowest):**
1. Command-line arguments (`--server.port=9090`)
2. JNDI attributes
3. System properties (`-Dserver.port=9090`)
4. OS environment variables (`SERVER_PORT=9090`)
5. Profile-specific properties (`application-prod.yml`)
6. `application.properties` / `application.yml`
7. Default properties (in code)

**Key Annotations:**
- `@Value("${property.name}")` — Inject single property
- `@ConfigurationProperties(prefix)` — Bind group of properties to POJO
- `@Profile("env")` — Activate bean only in specific profile

**Profiles:**
Named logical groups of configuration. Commonly: `dev`, `test`, `staging`, `prod`.

Activated via:
- `spring.profiles.active=prod`
- Environment variable: `SPRING_PROFILES_ACTIVE=prod`
- Command line: `--spring.profiles.active=prod`
- Programmatic: `SpringApplication.setAdditionalProfiles("prod")`

---

## Internal Working

```
Application Starts
       ↓
PropertySource loading order:
  1. Command-line args
  2. System.getProperties()
  3. OS env variables (relaxed binding: SERVER_PORT → server.port)
  4. application-{profile}.yml
  5. application.yml
  6. @PropertySource annotations
  7. Default properties
       ↓
Environment object populated
       ↓
@Value resolution via PropertySourcesPlaceholderConfigurer
       ↓
@ConfigurationProperties binding via Binder
       ↓
Validation (@Validated) runs on config classes
       ↓
Beans receive resolved config values
```

**Relaxed Binding Rules:**
```
application.yml:     server.port
Environment var:     SERVER_PORT
System property:     server.port
Command line:        --server.port

All map to the same property!

Binding to Java:
my-app.base-url → myApp.baseUrl (camelCase)
MY_APP_BASE_URL → myApp.baseUrl (from env var)
```

---

## Diagram

```
┌─────────────────────────────────────────────────────┐
│                Property Resolution                    │
│                                                      │
│  Priority:                                           │
│  ┌─────────────────────┐ ←── Highest                │
│  │ Command-line args    │                            │
│  ├─────────────────────┤                            │
│  │ System properties    │                            │
│  ├─────────────────────┤                            │
│  │ Environment vars     │                            │
│  ├─────────────────────┤                            │
│  │ application-prod.yml │ ←── Profile-specific       │
│  ├─────────────────────┤                            │
│  │ application.yml      │ ←── Default                │
│  ├─────────────────────┤                            │
│  │ @PropertySource      │                            │
│  ├─────────────────────┤                            │
│  │ Defaults in code     │ ←── Lowest                 │
│  └─────────────────────┘                            │
│                                                      │
│  Higher priority OVERRIDES lower priority            │
└─────────────────────────────────────────────────────┘

Profile Activation:
┌──────────────┐    SPRING_PROFILES_ACTIVE=prod
│ application  │──────────────────────────────────┐
│    .yml      │                                  │
└──────────────┘                                  ▼
                                          ┌──────────────┐
┌──────────────┐                          │ application- │
│ application- │◀── Only loaded when      │   prod.yml   │
│   dev.yml    │    profile = dev         └──────────────┘
└──────────────┘                           (LOADED ✓)
```

---

## Code

```yaml
# === application.yml ===
server:
  port: 8080
  shutdown: graceful

spring:
  application:
    name: order-service
  profiles:
    active: dev

app:
  name: Order Service
  version: 1.0.0
  api:
    base-url: http://localhost:8080
    timeout: 5000
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:4200
  features:
    email-notifications: true
    sms-notifications: false
```

```yaml
# === application-dev.yml ===
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: create-drop
  h2:
    console:
      enabled: true

logging:
  level:
    com.example: DEBUG
    org.hibernate.SQL: DEBUG
```

```yaml
# === application-prod.yml ===
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate

logging:
  level:
    root: WARN
    com.example: INFO
```

```java
// === @Value usage ===
@Service
public class NotificationService {

    @Value("${app.features.email-notifications:false}")
    private boolean emailEnabled;

    @Value("${app.api.timeout:3000}")
    private int timeout;

    @Value("${APP_SECRET_KEY}") // from environment variable
    private String secretKey;
}

// === @ConfigurationProperties (Recommended) ===
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @NotBlank
    private String name;
    private String version;
    private Api api = new Api();
    private Cors cors = new Cors();
    private Features features = new Features();

    // Getters and setters

    public static class Api {
        private String baseUrl;
        private int timeout = 5000;
        // Getters and setters
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        // Getters and setters
    }

    public static class Features {
        private boolean emailNotifications;
        private boolean smsNotifications;
        // Getters and setters
    }
}

// === Using ConfigurationProperties ===
@Service
public class OrderService {

    private final AppProperties appProperties;

    public OrderService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void processOrder() {
        if (appProperties.getFeatures().isEmailNotifications()) {
            // send email
        }
        int timeout = appProperties.getApi().getTimeout();
        // use timeout
    }
}

// === Profile-specific beans ===
@Configuration
public class StorageConfig {

    @Bean
    @Profile("dev")
    public StorageService localStorage() {
        return new LocalFileStorageService("/tmp/uploads");
    }

    @Bean
    @Profile("prod")
    public StorageService s3Storage() {
        return new S3StorageService();
    }
}

// === Environment object ===
@Service
public class EnvironmentService {

    private final Environment environment;

    public EnvironmentService(Environment environment) {
        this.environment = environment;
    }

    public boolean isProduction() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    public String getProperty(String key) {
        return environment.getProperty(key);
    }
}
```

---

## Dry Run

**Scenario**: App starts with `SPRING_PROFILES_ACTIVE=prod` and `--server.port=9090`

```
1. Spring Boot starts
2. Loads application.yml:
   - server.port = 8080
   - app.name = "Order Service"
3. Detects active profile = "prod"
4. Loads application-prod.yml:
   - spring.datasource.url = jdbc:postgresql://...
   - Overrides logging levels
5. Evaluates command-line args:
   - --server.port=9090 OVERRIDES both yml files
6. Final resolved values:
   - server.port = 9090 (from command-line — highest priority)
   - spring.datasource.url = jdbc:postgresql://... (from prod profile)
   - app.name = "Order Service" (from base application.yml)
7. ${DB_HOST} resolved from OS environment variable
8. Beans created with resolved properties
9. @ConfigurationProperties classes populated
10. Server starts on port 9090
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Property file parsing | O(n) — n = number of properties |
| @Value resolution | O(1) per injection — property lookup is map-based |
| @ConfigurationProperties binding | O(n) — n = number of fields to bind |
| Profile activation | O(1) — simple string comparison |
| Environment variable lookup | O(1) — OS provides hash-based access |

---

## Real Project Usage

**Docker Compose environment:**
```yaml
# docker-compose.yml
services:
  order-service:
    image: order-service:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=postgres
      - DB_NAME=orders
      - DB_USER=app_user
      - DB_PASSWORD=${DB_PASSWORD}  # from .env file
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    ports:
      - "8080:8080"
```

**Kubernetes ConfigMap:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  DB_HOST: "postgres-service"
  APP_FEATURES_EMAIL_NOTIFICATIONS: "true"
```

---

## Interview Questions

1. **What is the property resolution order in Spring Boot?**
   - Command-line args > System properties > OS env vars > profile-specific yml > application.yml > @PropertySource > defaults. Higher priority overrides lower.

2. **What is the difference between @Value and @ConfigurationProperties?**
   - @Value: Injects single property, supports SpEL, no type-safe grouping. @ConfigurationProperties: Binds a group of properties to a POJO, type-safe, supports validation, relaxed binding, IDE auto-complete.

3. **How do Spring profiles work? How do you activate them?**
   - Profiles let you define environment-specific config. Activate via: `SPRING_PROFILES_ACTIVE=prod` (env var), `--spring.profiles.active=prod` (CLI), or `spring.profiles.active` in yml. Profile-specific files: application-{profile}.yml loaded and merged.

4. **How does relaxed binding work in Spring Boot?**
   - Spring Boot maps various naming conventions to the same property: `server.port`, `SERVER_PORT`, `server-port`, `server_port` all resolve to `serverPort` in Java. Environment variables use uppercase + underscores.

5. **How do you externalize configuration for Docker/Kubernetes?**
   - Environment variables (Spring auto-maps), K8s ConfigMaps/Secrets (mounted as env vars or volumes), Spring Cloud Config Server for centralized config.

6. **What happens if the same property is defined in application.yml and application-prod.yml?**
   - Profile-specific file (application-prod.yml) overrides the base file (application.yml) for that property. Other base properties not overridden are preserved.

7. **How do you validate configuration properties?**
   - Add @Validated on the @ConfigurationProperties class and use Jakarta Validation annotations (@NotBlank, @Min, @Max, etc.). App fails to start if validation fails.

8. **What is the difference between application.properties and application.yml?**
   - Functionally equivalent. YAML supports hierarchical structure, lists, multi-document within one file. Properties is flat key=value. Choose one format per project for consistency.

9. **How do you inject a list or map from configuration?**
   - YAML: `items: [a, b, c]` or `map: {key1: val1, key2: val2}`. Bind to `List<String>` or `Map<String,String>` in @ConfigurationProperties. With @Value: `@Value("${items}")` with comma-separated values.

10. **How do you use environment variables with Spring Boot?**
    - Spring auto-maps env vars to properties using relaxed binding: `SPRING_DATASOURCE_URL` maps to `spring.datasource.url`. Use ${ENV_VAR} placeholders in yml. Set via Docker, K8s, or OS.

---

## Follow-up Questions

1. **After Q1**: "Where do Kubernetes ConfigMaps fit in the precedence?"
   → They become environment variables, so they sit above application.yml but below command-line args.

2. **After Q2**: "When would you prefer @Value over @ConfigurationProperties?"
   → For single, simple values. @ConfigurationProperties is better for groups of related properties.

3. **After Q3**: "Can you have multiple profiles active simultaneously?"
   → Yes. `spring.profiles.active=prod,kafka,monitoring`. Later profiles override earlier ones for conflicts.

4. **After Q6**: "What if you need a property from a non-active profile?"
   → You can't directly. Use `spring.profiles.include` or `spring.profiles.group` to compose profiles.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Hardcoding secrets in application.yml | Security risk in version control | Use env vars or secret managers |
| Using @Value without default | App crashes if property missing | `@Value("${key:default}")` |
| Mixing .properties and .yml in same project | Confusing, precedence unclear | Pick one format |
| Not validating @ConfigurationProperties | Silent failures with wrong config | Add @Validated + constraints |
| Using spring.profiles.active in application.yml | Gets overridden; confusing | Set externally (env var, CLI) |
| Committing application-prod.yml with secrets | Security breach | Use environment variables |

---

## Best Practices

1. **Use @ConfigurationProperties** over @Value for grouped configs
2. **Always provide defaults** for non-critical properties
3. **Validate config** with @Validated + Jakarta constraints
4. **Never commit secrets** — use env vars, Vault, AWS Secrets Manager
5. **Use profile groups** for composing environments: `spring.profiles.group.prod=prod-db,prod-kafka`
6. **Document properties** — use `spring-configuration-metadata.json`
7. **Use immutable @ConfigurationProperties** (record or constructor binding)
8. **Keep application.yml minimal** — most config in profile-specific files
9. **Use YAML anchors** to avoid repetition in complex configs
10. **Test configuration loading** with `@SpringBootTest` + `@ActiveProfiles`

---

## Production Considerations

- **Secret management**: Use HashiCorp Vault, AWS Secrets Manager, or Kubernetes Secrets
- **Config refresh**: Spring Cloud Config supports runtime config changes without restart
- **Audit**: Log which profile/properties are active at startup
- **Immutable config**: Use record-based @ConfigurationProperties (Spring Boot 3.x)
- **Fail fast**: `spring.config.import=optional:` vs required imports
- **12-Factor App**: All config via environment variables for cloud-native deployment
- **Config validation**: App should fail to start if critical config is missing/invalid

---

## Related Topics

- → [2. Spring Boot Fundamentals](#) (auto-configuration uses properties)
- → [4. Spring Bean Management](#) (beans configured via properties)
- → [14. Database Connection Pool](#) (HikariCP configured via properties)
- → [33. Spring Boot + Docker](#) (environment variable injection)
- → [34. Spring Boot + Kubernetes](#) (ConfigMaps, Secrets)
