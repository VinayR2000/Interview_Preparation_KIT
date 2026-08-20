# 35. Kubernetes + Spring Boot ⭐⭐⭐

---

## Theory

Running Spring Boot applications in Kubernetes requires proper containerization, health probes, configuration externalization, and resource management.

### Dockerize Spring Boot

```dockerfile
# Multi-stage build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

```
Key considerations:
  - Multi-stage build (small final image)
  - Non-root user
  - JRE only (not JDK) in production
  - UseContainerSupport: JVM respects container memory limits
  - MaxRAMPercentage=75: Leave headroom for non-heap memory
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      serviceAccountName: order-service-sa
      terminationGracePeriodSeconds: 60
      containers:
      - name: order-service
        image: registry/order-service:2.1.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: JAVA_OPTS
          value: "-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  selector:
    app: order-service
  ports:
  - port: 80
    targetPort: 8080
```

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
data:
  application-kubernetes.yml: |
    server:
      port: 8080
      shutdown: graceful
    spring:
      lifecycle:
        timeout-per-shutdown-phase: 30s
      datasource:
        url: jdbc:postgresql://postgres-svc:5432/orders
        hikari:
          maximum-pool-size: 20
    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
      endpoint:
        health:
          probes:
            enabled: true
          show-details: always
```

### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: order-service-secret
type: Opaque
stringData:
  SPRING_DATASOURCE_USERNAME: order_user
  SPRING_DATASOURCE_PASSWORD: super-secret-pw
```

### Environment Variables

```yaml
env:
- name: SPRING_PROFILES_ACTIVE
  value: "kubernetes"
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: order-service-secret
      key: SPRING_DATASOURCE_USERNAME
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: order-service-secret
      key: SPRING_DATASOURCE_PASSWORD
```

### Actuator

```
Spring Boot Actuator provides K8s-friendly health endpoints:

/actuator/health/liveness  → Is the app alive?
/actuator/health/readiness → Can the app serve traffic?
/actuator/health           → Overall health
/actuator/prometheus       → Prometheus metrics

application.yml:
  management:
    endpoint:
      health:
        probes:
          enabled: true    # Enables liveness/readiness groups
    health:
      livenessState:
        enabled: true
      readinessState:
        enabled: true
```

### Health Probes

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 30        # 150s max startup
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  periodSeconds: 10
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  periodSeconds: 5
  failureThreshold: 3
```

### Liveness vs Readiness for Spring Boot

```
Liveness (/actuator/health/liveness):
  - Checks: Is the JVM running? Not deadlocked?
  - Should NOT check dependencies (DB, Redis)
  - Failure → restart pod

Readiness (/actuator/health/readiness):
  - Checks: DB connection pool ok? Cache ready? Dependencies up?
  - Checks all "readiness" health indicators
  - Failure → remove from Service (no traffic)

Configure readiness indicators:
  @Component
  public class DatabaseReadinessIndicator implements HealthIndicator {
    public Health health() {
      if (dbConnectionOk) return Health.up().build();
      return Health.down().build();
    }
  }
```

### Graceful Shutdown

```yaml
# application.yml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

# K8s manifest
spec:
  terminationGracePeriodSeconds: 60
  containers:
  - lifecycle:
      preStop:
        exec:
          command: ["sh", "-c", "sleep 10"]
```

```
Graceful shutdown flow:
  1. Pod receives SIGTERM
  2. preStop hook: sleep 10s (wait for LB to deregister)
  3. Spring Boot: stop accepting new requests
  4. Spring Boot: wait for in-flight requests to complete (30s max)
  5. Spring Boot: close connections, shutdown context
  6. If not done in terminationGracePeriodSeconds (60s) → SIGKILL
```

### Resource Requests/Limits

```
JVM memory calculation:
  Container limit: 1Gi
  MaxRAMPercentage=75%: Heap = 768Mi
  Remaining 256Mi: Metaspace, threads, native memory, OS

Recommendations:
  requests.memory: Expected steady-state (512Mi)
  limits.memory:   Max acceptable (1Gi)
  requests.cpu:    Normal load (500m)
  limits.cpu:      Peak/burst (1000m or remove for burstable)

JVM flags:
  -XX:+UseContainerSupport (default in JDK 11+)
  -XX:MaxRAMPercentage=75.0
  -XX:InitialRAMPercentage=50.0
```

### Horizontal Scaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
```

### Externalized Configuration

```
Spring Boot config sources in K8s (priority order):
  1. Environment variables (highest priority)
  2. ConfigMap mounted as application-kubernetes.yml
  3. Secret mounted or as env var
  4. application.yml in JAR (defaults)

Spring Cloud Kubernetes:
  - Auto-reload ConfigMap changes
  - Service discovery via K8s Services
  - Load balancer via K8s Services

Or use Spring profiles:
  SPRING_PROFILES_ACTIVE=kubernetes
  → Loads application-kubernetes.yml from ConfigMap mount
```

---

## Interview Questions

### Q1: How do you configure health probes for a Spring Boot app in K8s?

**A:** Enable Actuator probes in application.yml: `management.endpoint.health.probes.enabled=true`. This creates `/actuator/health/liveness` and `/actuator/health/readiness`. Use startup probe (failureThreshold × period > max startup time) to handle slow Spring Boot startup. Liveness checks JVM health, readiness checks dependencies.

### Q2: How do you handle graceful shutdown for Spring Boot in K8s?

**A:**
1. Set `server.shutdown=graceful` and `timeout-per-shutdown-phase=30s` in Spring Boot
2. Add `preStop: sleep 10` — allows load balancer to deregister the pod
3. Set `terminationGracePeriodSeconds` > preStop + shutdown timeout
4. Spring Boot will finish in-flight requests, then close connections

### Q3: How do you size JVM memory in a container?

**A:** Set container memory limit (e.g., 1Gi). Use `-XX:MaxRAMPercentage=75.0` so JVM uses 75% for heap, leaving 25% for metaspace, thread stacks, native memory. Set memory request to steady-state usage. Monitor actual usage and adjust. JDK 11+ respects container limits automatically (`UseContainerSupport` is default).

### Q4: How do you externalize Spring Boot configuration in K8s?

**A:** Multiple approaches:
1. **ConfigMap as env vars:** For simple key-value properties
2. **ConfigMap as volume:** Mount application-kubernetes.yml file
3. **Secrets as env vars:** For sensitive values (DB passwords)
4. **Spring Cloud Kubernetes:** Auto-reload on ConfigMap change
5. **Profile activation:** `SPRING_PROFILES_ACTIVE=kubernetes` loads env-specific config

---

## Best Practices

1. **Use multi-stage Docker builds** — small, secure images
2. **Run as non-root** — USER directive in Dockerfile
3. **Set MaxRAMPercentage=75** — leave room for non-heap memory
4. **Use startup probes** — Spring Boot can take 30-60s to start
5. **Implement graceful shutdown** — preStop + server.shutdown=graceful
6. **Externalize all config** — ConfigMaps and Secrets
7. **Expose Prometheus metrics** — /actuator/prometheus
8. **Use IRSA for AWS access** — no hardcoded credentials

---

## Related Topics

- [04. Pods](./04-pods.md)
- [16. Health Checks](./16-health-checks.md)
- [09. ConfigMap & Secrets](./09-configmaps-and-secrets.md)
- [17. Scaling](./17-scaling.md)
