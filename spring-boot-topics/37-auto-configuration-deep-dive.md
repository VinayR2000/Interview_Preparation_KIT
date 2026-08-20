# 37. Auto-Configuration Deep Dive

## Theory

Auto-configuration is the mechanism that makes Spring Boot "opinionated." It automatically configures beans based on what's on your classpath, what beans you've already defined, and what properties are set.

### How It Works:
1. Spring Boot loads auto-configuration classes from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. Each class is annotated with `@Conditional*` annotations
3. Conditions evaluated at startup — if ALL conditions pass, beans are created
4. User-defined beans ALWAYS take precedence (`@ConditionalOnMissingBean`)

### Key Conditional Annotations:

| Annotation | Condition |
|-----------|-----------|
| @ConditionalOnClass | Class exists on classpath |
| @ConditionalOnMissingClass | Class NOT on classpath |
| @ConditionalOnBean | Bean of type exists in context |
| @ConditionalOnMissingBean | Bean of type NOT in context |
| @ConditionalOnProperty | Property has specific value |
| @ConditionalOnWebApplication | Running as web app |
| @ConditionalOnNotWebApplication | NOT a web app |
| @ConditionalOnResource | Resource exists on classpath |
| @ConditionalOnExpression | SpEL expression evaluates to true |

### Starter → Auto-Configuration Flow:
```
Add spring-boot-starter-data-jpa to pom.xml
       ↓
Pulls in: hibernate-core, spring-data-jpa, HikariCP, spring-jdbc
       ↓
On classpath: DataSource.class, EntityManagerFactory.class, JpaRepository.class
       ↓
Auto-configuration triggered:
  DataSourceAutoConfiguration → Creates HikariDataSource
  HibernateJpaAutoConfiguration → Creates EntityManagerFactory
  JpaRepositoriesAutoConfiguration → Creates repository proxies
  TransactionAutoConfiguration → Creates TransactionManager
```

---

## Internal Working

```
SpringApplication.run()
       ↓
Load AutoConfiguration.imports file
  (Lists all auto-configuration classes)
       ↓
For each auto-configuration class:
       ↓
┌────────────────────────────────────────────────────┐
│ Evaluate @Conditional annotations:                  │
│                                                     │
│ @ConditionalOnClass(DataSource.class)              │
│   → Is javax.sql.DataSource on classpath?          │
│   → YES ✓                                          │
│                                                     │
│ @ConditionalOnMissingBean(DataSource.class)         │
│   → Did user define their own DataSource @Bean?    │
│   → NO (user hasn't defined one) ✓                 │
│                                                     │
│ @ConditionalOnProperty("spring.datasource.url")    │
│   → Is this property set?                          │
│   → YES ✓                                          │
│                                                     │
│ ALL conditions pass → Register BeanDefinitions      │
│ ANY condition fails → Skip this auto-configuration │
└────────────────────────────────────────────────────┘
       ↓
Beans created from registered definitions
```

### Auto-Configuration Ordering:
```
User @Configuration classes → processed FIRST (highest priority)
       ↓
Auto-configuration classes → processed AFTER user config
       ↓
Within auto-config:
  @AutoConfigureBefore(DataSourceAutoConfiguration.class)
  @AutoConfigureAfter(DataSourceAutoConfiguration.class)
  @AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│             AUTO-CONFIGURATION DECISION TREE                      │
│                                                                   │
│  spring-boot-starter-data-jpa added to classpath                 │
│       ↓                                                           │
│  ┌─── DataSourceAutoConfiguration ──────────────────────────┐   │
│  │                                                           │   │
│  │  @ConditionalOnClass(DataSource.class) → ✓ (on classpath)│   │
│  │  @ConditionalOnMissingBean(DataSource.class)             │   │
│  │       ↓                                                   │   │
│  │  ┌─── User defined DataSource? ─────────────────────┐    │   │
│  │  │ YES → SKIP (user bean wins)                       │    │   │
│  │  │ NO  → Create HikariDataSource from properties     │    │   │
│  │  └───────────────────────────────────────────────────┘    │   │
│  └───────────────────────────────────────────────────────────┘   │
│       ↓                                                           │
│  ┌─── HibernateJpaAutoConfiguration ────────────────────────┐   │
│  │                                                           │   │
│  │  @ConditionalOnClass(EntityManager.class) → ✓             │   │
│  │  @ConditionalOnBean(DataSource.class) → ✓ (just created) │   │
│  │  @ConditionalOnMissingBean(EntityManagerFactory.class)    │   │
│  │       → Create LocalContainerEntityManagerFactoryBean     │   │
│  └───────────────────────────────────────────────────────────┘   │
│       ↓                                                           │
│  ┌─── TransactionAutoConfiguration ─────────────────────────┐   │
│  │                                                           │   │
│  │  @ConditionalOnClass(PlatformTransactionManager.class) ✓  │   │
│  │  @ConditionalOnMissingBean(TransactionManager.class)      │   │
│  │       → Create JpaTransactionManager                      │   │
│  └───────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code

### Creating a Custom Auto-Configuration:

```java
// Step 1: Create auto-configuration class
@AutoConfiguration
@ConditionalOnClass(NotificationService.class)
@ConditionalOnProperty(prefix = "app.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationService notificationService(NotificationProperties properties) {
        return new DefaultNotificationService(properties.getProvider(), properties.getApiKey());
    }

    @Bean
    @ConditionalOnBean(NotificationService.class)
    @ConditionalOnMissingBean
    public NotificationHealthIndicator notificationHealthIndicator(NotificationService service) {
        return new NotificationHealthIndicator(service);
    }

    // Nested configuration for specific provider
    @Configuration
    @ConditionalOnProperty(prefix = "app.notification", name = "provider", havingValue = "email")
    static class EmailNotificationConfiguration {

        @Bean
        @ConditionalOnMissingBean(EmailSender.class)
        public EmailSender emailSender(NotificationProperties properties) {
            return new SmtpEmailSender(properties.getSmtp());
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.notification", name = "provider", havingValue = "sms")
    static class SmsNotificationConfiguration {

        @Bean
        @ConditionalOnMissingBean(SmsSender.class)
        public SmsSender smsSender(NotificationProperties properties) {
            return new TwilioSmsSender(properties.getTwilio());
        }
    }
}
```

### Configuration Properties:

```java
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {
    private boolean enabled = true;
    private String provider = "email";
    private String apiKey;
    private SmtpProperties smtp = new SmtpProperties();
    private TwilioProperties twilio = new TwilioProperties();
    // Getters and setters
}
```

### Registration (META-INF):

```
// src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.notification.NotificationAutoConfiguration
```

### Custom Starter (pom.xml):

```xml
<!-- notification-spring-boot-starter -->
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>notification-spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>notification-core</artifactId>
    </dependency>
</dependencies>
```

### Debugging Auto-Configuration:

```bash
# See what was auto-configured and why
java -jar app.jar --debug

# Output shows:
# Positive matches (configured):
#   DataSourceAutoConfiguration matched:
#     - @ConditionalOnClass found: javax.sql.DataSource ✓
#
# Negative matches (not configured):
#   RabbitAutoConfiguration:
#     - @ConditionalOnClass did not find: com.rabbitmq.client.Connection ✗
```

### Overriding Auto-Configuration:

```java
// User defines their own DataSource → auto-config skips
@Configuration
public class CustomDataSourceConfig {

    @Bean  // This wins over auto-configured DataSource
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://custom-host:5432/mydb");
        config.setMaximumPoolSize(30);
        return new HikariDataSource(config);
    }
}

// Or disable entirely
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class MyApplication { }
```

---

## Dry Run

### Startup with `--debug` flag:

```
$ java -jar order-service.jar --debug

============================
CONDITIONS EVALUATION REPORT
============================

Positive matches:
-----------------
   DataSourceAutoConfiguration matched:
      - @ConditionalOnClass found required class 'javax.sql.DataSource'
      - @ConditionalOnMissingBean did not find any beans of type 'DataSource'

   HibernateJpaAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'EntityManager', 'SessionImplementor'
      - @ConditionalOnBean found bean 'dataSource' of type 'DataSource'

Negative matches:
-----------------
   RabbitAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.rabbitmq.client.Connection'

   MongoAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.mongodb.client.MongoClient'

Exclusions:
-----------
   None

Unconditional classes:
----------------------
   org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Load AutoConfiguration.imports | O(1) - file read |
| Condition evaluation per class | O(1) - reflection checks |
| Total auto-config evaluation | O(n) - n = auto-config classes (~150 in typical app) |
| Classpath scanning for @ConditionalOnClass | O(1) - cached ClassLoader lookup |

---

## Real Project Usage

### Custom Starter for Company-Wide Observability:

```java
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
public class CompanyObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestLoggingFilter requestLoggingFilter() {
        return new CompanyRequestLoggingFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(Environment env) {
        return registry -> registry.config()
            .commonTags("team", env.getProperty("app.team", "unknown"))
            .commonTags("service", env.getProperty("spring.application.name", "unknown"));
    }
}
```

---

## Interview Questions

1. **How does Spring Boot auto-configuration work internally?**
   - Loads auto-config class names from AutoConfiguration.imports. Evaluates @Conditional annotations on each class. If all conditions pass, registers BeanDefinitions. User-defined beans take precedence via @ConditionalOnMissingBean.

2. **What is the difference between @ConditionalOnBean and @ConditionalOnMissingBean?**
   - @ConditionalOnBean: Condition passes if specified bean type EXISTS in context (register this bean only if dependency exists). @ConditionalOnMissingBean: Condition passes if bean type does NOT exist (don't override user's bean). Most auto-config uses @ConditionalOnMissingBean.

3. **How do you create a custom Spring Boot starter?**
   - Create two modules: starter (dependencies) + autoconfigure (auto-config classes). Add @AutoConfiguration class with @Conditional annotations. Register in AutoConfiguration.imports. Create @ConfigurationProperties for configuration.

4. **How do you debug auto-configuration (what's being configured)?**
   - Run with `--debug` flag or set `debug=true` in properties. Outputs ConditionEvaluationReport showing positive/negative matches and why. Also available at /actuator/conditions endpoint.

5. **What's the ordering of auto-configuration vs user configuration?**
   - User @Configuration always processed first. Auto-config processed after. Within auto-config: @AutoConfigureBefore/@AutoConfigureAfter control relative order. @ConditionalOnMissingBean ensures user beans win.

---

## Follow-up Questions

1. How do you exclude a specific auto-configuration class?
   - `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` or `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` in properties. Use when you don't want a specific auto-config at all.

2. What's the difference between @Configuration and @AutoConfiguration?
   - @AutoConfiguration is meta-annotated with @Configuration. Additionally: only loaded via imports file (not component scanning), supports ordering annotations (@AutoConfigureBefore/After), intended for library authors not application developers.

3. How does @ConditionalOnProperty work with missing properties?
   - Default: Condition fails if property is missing. Use `matchIfMissing=true` to pass when property isn't set. Use `havingValue="true"` to match specific value. Supports prefix + name combination.

4. How does Spring Boot know which auto-configuration classes to load?
   - Reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file. Each line is a fully-qualified class name. Previous versions used `spring.factories` file (deprecated in Boot 3.x).

5. Can auto-configuration create beans that depend on user beans?
   - Yes, via @ConditionalOnBean(UserService.class). But order matters — user beans must be created first. Auto-config classes are processed after user @Configuration, so user beans are available.

---

## Common Mistakes

1. **Using @ComponentScan to load auto-config** - Auto-config should only be loaded via imports file
2. **Missing @ConditionalOnMissingBean** - Auto-config overrides user's custom bean
3. **Wrong ordering** - @ConditionalOnBean for a bean that hasn't been created yet
4. **Not using --debug** - Guessing what's configured instead of checking the report
5. **Creating starter without autoconfigure module** - Mixing concerns, harder to maintain
6. **Forgetting to register in imports file** - Auto-config class exists but never loaded

---

## Best Practices

1. **Always use @ConditionalOnMissingBean** - Let users override your beans
2. **Use `--debug` or /actuator/conditions** to understand auto-config decisions
3. **Separate starter from autoconfigure** module in custom starters
4. **Use @AutoConfigureAfter** when your config depends on another auto-config
5. **Provide @ConfigurationProperties** for customization
6. **Test auto-configuration** with `ApplicationContextRunner` in unit tests
7. **Document all configuration properties** with spring-configuration-metadata.json

---

## Production Considerations

- **Startup time**: Many auto-config classes evaluated → use AOT for faster startup
- **Unnecessary auto-config**: Exclude what you don't need to speed up startup
- **Configuration drift**: Different classpaths in different environments → different auto-config behavior
- **Version upgrades**: New Spring Boot version may add/change auto-configuration behavior
- **Custom starters**: Version-pin dependencies to avoid conflicts

---

## Related Topics

- Spring Boot Fundamentals (@SpringBootApplication)
- Advanced Spring Internals (BeanFactoryPostProcessor)
- Spring Boot Configuration (properties)
- Spring Boot Startup Debugging
- Custom Starters
