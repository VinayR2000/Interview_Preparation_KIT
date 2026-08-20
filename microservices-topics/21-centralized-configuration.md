# 21. Centralized Configuration

## Theory

In microservices, each service has its own configuration (database URLs, feature flags, secrets). Managing configuration across dozens of services and environments needs centralization.

### Approaches:

| Approach | How It Works | When to Use |
|----------|-------------|-------------|
| Config Server | Central server serves config | Spring Cloud Config, traditional |
| Environment Variables | Injected at deployment time | Kubernetes, Docker |
| ConfigMaps/Secrets | Kubernetes-native config | K8s deployments |
| Dynamic Configuration | Runtime changes without restart | Feature flags, tuning |

### Key Requirements:
- Environment-specific values (dev, staging, prod)
- Secrets management (encrypted, not in code)
- Dynamic refresh (change without redeployment)
- Audit trail (who changed what, when)
- Versioning (rollback bad config changes)

---

## Internal Working

### Spring Cloud Config Server:

```
┌────────────────────────────────────────────────────────┐
│ CENTRALIZED CONFIGURATION                               │
│                                                         │
│ ┌──────────────────────────────────────────┐          │
│ │           Git Repository                  │          │
│ │                                           │          │
│ │ application.yml (shared)                  │          │
│ │ order-service.yml                         │          │
│ │ order-service-prod.yml                    │          │
│ │ payment-service.yml                       │          │
│ │ payment-service-prod.yml                  │          │
│ └──────────────────┬───────────────────────┘          │
│                    │ pull                               │
│                    ↓                                    │
│ ┌──────────────────────────────────────────┐          │
│ │         Config Server                     │          │
│ │   http://config-server:8888              │          │
│ │                                           │          │
│ │   /order-service/prod                    │          │
│ │   /payment-service/dev                   │          │
│ └──────────────────┬───────────────────────┘          │
│                    │                                    │
│         ┌──────────┼──────────┐                       │
│         ↓          ↓          ↓                       │
│    ┌─────────┐ ┌────────┐ ┌────────┐                │
│    │ Order   │ │Payment │ │ User   │                │
│    │ Service │ │Service │ │Service │                │
│    │         │ │        │ │        │                │
│    │ Fetches │ │Fetches │ │Fetches │                │
│    │ config  │ │config  │ │config  │                │
│    │on start │ │on start│ │on start│                │
│    └─────────┘ └────────┘ └────────┘                │
└────────────────────────────────────────────────────────┘
```

### Kubernetes ConfigMap + Secrets:

```
┌────────────────────────────────────────────────────────┐
│ KUBERNETES CONFIGURATION                                │
│                                                         │
│ ConfigMap (non-sensitive):                             │
│ ┌──────────────────────────────────────────┐          │
│ │ apiVersion: v1                            │          │
│ │ kind: ConfigMap                           │          │
│ │ data:                                     │          │
│ │   application.yml: |                      │          │
│ │     server.port: 8081                     │          │
│ │     feature.new-ui: true                  │          │
│ │     cache.ttl: 300                        │          │
│ └──────────────────────────────────────────┘          │
│                                                         │
│ Secret (sensitive — base64 encoded, encrypted at rest):│
│ ┌──────────────────────────────────────────┐          │
│ │ apiVersion: v1                            │          │
│ │ kind: Secret                              │          │
│ │ data:                                     │          │
│ │   DB_PASSWORD: cGFzc3dvcmQxMjM=          │          │
│ │   JWT_SECRET: c2VjcmV0S2V5MTIz           │          │
│ └──────────────────────────────────────────┘          │
│                                                         │
│ Mounted as env vars or files in Pod                   │
│                                                         │
│ Pod spec:                                              │
│   envFrom:                                             │
│     - configMapRef: {name: order-config}              │
│     - secretRef: {name: order-secrets}                │
└────────────────────────────────────────────────────────┘
```

### Dynamic Configuration Refresh:

```
┌────────────────────────────────────────────────────────┐
│ DYNAMIC REFRESH (No Restart)                            │
│                                                         │
│ Change config in Git / ConfigMap                       │
│       │                                                │
│       ↓                                                │
│ Option 1: Spring Cloud Bus (event-driven)             │
│   Config Server → Kafka/RabbitMQ → All services refresh│
│                                                         │
│ Option 2: POST /actuator/refresh                      │
│   Manual trigger per service                          │
│                                                         │
│ Option 3: Kubernetes ConfigMap watcher                 │
│   Spring Cloud Kubernetes reloads on ConfigMap change │
│                                                         │
│ @RefreshScope beans get recreated with new values     │
└────────────────────────────────────────────────────────┘
```

---

## Code

### Spring Cloud Config Server:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# Config Server application.yml
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/company/config-repo
          default-label: main
          search-paths: '{application}'
        encrypt:
          enabled: true

encrypt:
  key: ${ENCRYPT_KEY}  # For encrypting secrets in Git
```

### Service Configuration (Client):

```yaml
# bootstrap.yml (or spring.config.import in newer Spring Boot)
spring:
  application:
    name: order-service
  config:
    import: configserver:http://config-server:8888
  cloud:
    config:
      fail-fast: true
      retry:
        max-attempts: 5
        initial-interval: 1000
```

### Dynamic Refresh:

```java
@RestController
@RefreshScope  // Bean recreated on /actuator/refresh
public class FeatureFlagController {

    @Value("${feature.new-checkout:false}")
    private boolean newCheckoutEnabled;

    @Value("${rate-limit.requests-per-minute:100}")
    private int rateLimit;

    @GetMapping("/api/features")
    public Map<String, Object> getFeatures() {
        return Map.of(
            "newCheckout", newCheckoutEnabled,
            "rateLimit", rateLimit
        );
    }
}
```

### Kubernetes ConfigMap with Spring Boot:

```yaml
# Kubernetes ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
data:
  application.yml: |
    server:
      port: 8081
    spring:
      datasource:
        url: jdbc:postgresql://order-db:5432/orders
    feature:
      new-checkout: true
    cache:
      ttl-seconds: 300

---
# Deployment using ConfigMap
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  template:
    spec:
      containers:
        - name: order-service
          image: order-service:latest
          envFrom:
            - configMapRef:
                name: order-service-config
            - secretRef:
                name: order-service-secrets
          volumeMounts:
            - name: config-volume
              mountPath: /config
      volumes:
        - name: config-volume
          configMap:
            name: order-service-config
```

---

## Interview Questions

1. **Why centralized configuration?**
   - Single source of truth. Environment-specific without code changes. Dynamic updates without redeployment. Audit trail. Secrets not in source code. Consistent across all services.

2. **How to handle secrets in microservices?**
   - Never in code/git. Use: Kubernetes Secrets (encrypted at rest), HashiCorp Vault (dynamic secrets), AWS Secrets Manager. Mount as environment variables. Rotate regularly.

3. **How to refresh configuration without restart?**
   - Spring Cloud Config: @RefreshScope + /actuator/refresh or Spring Cloud Bus. Kubernetes: ConfigMap reload with spring-cloud-kubernetes. Feature flags: dedicated service (LaunchDarkly, Unleash).

4. **Config Server vs Kubernetes ConfigMaps?**
   - Config Server: Git-backed, versioned, encrypted secrets, works outside K8s. ConfigMaps: K8s native, simpler, but secrets handling is basic. On K8s, prefer ConfigMaps + external secret operator.

5. **How to manage config across environments?**
   - Profile-based: application-dev.yml, application-prod.yml. Environment variables override base config. GitOps: separate config repos per environment. Never deploy dev config to prod.

---

## Best Practices

1. **Secrets separate from config** — Different access controls and lifecycle
2. **Encrypt secrets at rest** — Even in Git (Spring Cloud Config encryption)
3. **Environment-specific overrides** — Base + profile-specific config
4. **Fail fast** — Service fails to start if config server unreachable
5. **Audit changes** — Git history or vault audit log
6. **Feature flags for rollout** — Change behavior without redeployment
7. **Validate config on startup** — Catch misconfigurations early
