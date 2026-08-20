# 15. Docker + Java/Spring Boot ⭐⭐⭐

---

## Theory

**Dockerizing Java/Spring Boot** requires understanding JVM memory behavior in containers, layered JARs for caching, and production-ready configuration. Java 10+ is container-aware, but proper tuning is still essential.

### JVM Container Awareness

```
Before Java 10:
  JVM saw HOST memory/CPUs, not container limits
  Container limit: 512MB, JVM allocates: 4GB (host) → OOM kill!

Java 10+ (UseContainerSupport — default ON):
  JVM correctly reads cgroup limits
  Container limit: 512MB → JVM allocates ~128MB heap (25%)

Key JVM flags for containers:
  -XX:+UseContainerSupport          # Default ON since Java 10
  -XX:MaxRAMPercentage=75.0         # Use 75% of container memory for heap
  -XX:InitialRAMPercentage=50.0     # Start with 50% of container memory
  -XX:MinRAMPercentage=25.0         # Min heap when memory is limited

Why not -Xmx/-Xms?
  Hardcoded values break when container limits change.
  Percentage-based = adapts to container memory automatically.
```

### Memory Planning for Java Containers

```
Container Memory Budget (512MB example):
  ┌──────────────────────────────────────┐
  │ Container Memory Limit: 512MB        │
  ├──────────────────────────────────────┤
  │ JVM Heap (MaxRAMPercentage=75%)      │
  │   → ~384MB max heap                 │
  ├──────────────────────────────────────┤
  │ JVM Non-Heap:                        │
  │   Metaspace: ~64MB                   │
  │   Thread stacks: ~40MB (200×1MB)     │
  │   Code cache: ~48MB                  │
  │   Direct buffers: variable           │
  ├──────────────────────────────────────┤
  │ OS overhead: ~20MB                   │
  └──────────────────────────────────────┘

Rule of thumb:
  Container memory = Heap + 128-256MB (non-heap + OS)
  Or use MaxRAMPercentage=75% and let JVM figure it out
```

### Spring Boot Layered JAR

```
Standard JAR (no layering):
  app.jar (80MB) → any change rebuilds entire layer

Layered JAR (Spring Boot 2.3+):
  Layer 1: dependencies/         (60MB — rarely changes)
  Layer 2: spring-boot-loader/   (300KB — almost never changes)
  Layer 3: snapshot-dependencies/ (variable — changes sometimes)
  Layer 4: application/          (2MB — changes every build)

Benefit: Docker caches layers 1-3, only rebuilds layer 4
  → 80MB rebuild → 2MB rebuild (97.5% cached!)
```

```dockerfile
# Layered JAR Dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Extract layers (Spring Boot 2.3+ / 3.x)
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Copy layers in order of change frequency
COPY --from=0 /app/dependencies/ ./
COPY --from=0 /app/spring-boot-loader/ ./
COPY --from=0 /app/snapshot-dependencies/ ./
COPY --from=0 /app/application/ ./

USER 1000
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Spring Boot Buildpacks (No Dockerfile Needed)

```bash
# Spring Boot 2.3+ can build OCI images without Dockerfile
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=myapp:1.0

# Or with Gradle
./gradlew bootBuildImage --imageName=myapp:1.0

# Uses Cloud Native Buildpacks (Paketo)
# Automatically creates optimized, layered, secure image
# Includes memory calculator, security updates, non-root user
```

```xml
<!-- pom.xml — customize buildpack -->
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <image>
            <name>registry.io/${project.artifactId}:${project.version}</name>
            <env>
                <BP_JVM_VERSION>21</BP_JVM_VERSION>
                <BPE_JAVA_TOOL_OPTIONS>-XX:MaxRAMPercentage=75.0</BPE_JAVA_TOOL_OPTIONS>
            </env>
        </image>
    </configuration>
</plugin>
```

### Spring Profiles in Docker

```yaml
# application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mydb}
    username: ${DB_USER:admin}
    password: ${DB_PASSWORD:secret}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

server:
  port: ${SERVER_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

```bash
# Activate docker profile
docker run -e SPRING_PROFILES_ACTIVE=docker \
  -e DB_HOST=postgres \
  -e DB_PASSWORD=secret \
  myapp:1.0
```

### Graceful Shutdown

```yaml
# application.yml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```dockerfile
# Ensure SIGTERM reaches Java process (exec form!)
ENTRYPOINT ["java", "-jar", "app.jar"]

# NOT this (shell form — SIGTERM goes to shell, not Java):
# ENTRYPOINT java -jar app.jar
```

```yaml
# Docker Compose
services:
  api:
    stop_grace_period: 30s    # Wait before SIGKILL
```

---

## Code

### Production Dockerfile (Complete):

```dockerfile
# ============================================
# Stage 1: Build
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src/ src/
RUN mvn package -DskipTests -B

# ============================================
# Stage 2: Extract layers
# ============================================
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ============================================
# Stage 3: Production
# ============================================
FROM eclipse-temurin:21-jre-alpine AS production

# Security updates
RUN apk --no-cache upgrade

# Non-root user
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy layers (ordered by change frequency)
COPY --from=layers --chown=spring:spring /app/dependencies/ ./
COPY --from=layers --chown=spring:spring /app/spring-boot-loader/ ./
COPY --from=layers --chown=spring:spring /app/snapshot-dependencies/ ./
COPY --from=layers --chown=spring:spring /app/application/ ./

USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker}", \
  "org.springframework.boot.loader.launch.JarLauncher"]
```

### Development Docker Compose:

```yaml
# compose.yaml — Spring Boot development environment
services:
  api:
    build:
      context: .
      target: production
    ports:
      - "8080:8080"
      - "5005:5005"              # Remote debug
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/orders
      - SPRING_DATASOURCE_USERNAME=admin
      - SPRING_DATASOURCE_PASSWORD=secret
      - SPRING_DATA_REDIS_HOST=redis
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 40s
    deploy:
      resources:
        limits:
          memory: 512M

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: secret
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d orders"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
    ports:
      - "9092:9092"
    healthcheck:
      test: kafka-topics --bootstrap-server localhost:9092 --list
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
```

### .dockerignore:

```
# .dockerignore
target/
!target/*.jar
.git/
.gitignore
.idea/
*.iml
.mvn/wrapper/maven-wrapper.jar
node_modules/
docker-compose*.yml
*.md
.env
```

---

## Interview Questions

### Q1: How does the JVM behave in containers?

**A:** Since Java 10, the JVM is container-aware (`UseContainerSupport=true` by default). It reads cgroup memory/CPU limits instead of host resources. Use `-XX:MaxRAMPercentage=75.0` to set heap as a percentage of container memory. Never hardcode `-Xmx` in containers — it doesn't adapt when limits change.

### Q2: What are Spring Boot layered JARs and why use them?

**A:** Layered JARs split the application into four Docker layers: dependencies (rarely changes, ~60MB), spring-boot-loader, snapshot-dependencies, and application code (changes every build, ~2MB). Docker caches unchanged layers. Result: rebuilds only transfer the 2MB application layer instead of the full 80MB JAR. Dramatically faster builds and deploys.

### Q3: How do you ensure graceful shutdown in Docker?

**A:** Three requirements:
1. Use exec form in ENTRYPOINT (`["java", "-jar", "app.jar"]`) so SIGTERM reaches Java directly
2. Configure Spring Boot: `server.shutdown=graceful` with timeout
3. Set `stop_grace_period` in Compose (or `terminationGracePeriodSeconds` in K8s)

This allows in-flight requests to complete before shutdown.

### Q4: Dockerfile vs Buildpacks — when to use which?

**A:**
- **Dockerfile:** Full control, custom optimization, CI/CD integration, team already knows Docker
- **Buildpacks:** Zero Dockerfile needed, auto-optimized (layering, memory calculator, security patches), consistent across teams. Use `./mvnw spring-boot:build-image`

Buildpacks are better for standardization; Dockerfiles for custom requirements.

### Q5: What memory should you allocate for a Spring Boot container?

**A:** Formula: Container memory = JVM heap + non-heap (Metaspace + threads + code cache) + OS.
- Simple app: 512MB container, 75% → ~384MB heap
- Microservice: 256-512MB typical
- Large app: 1-2GB

Use `MaxRAMPercentage=75.0` to leave 25% for non-heap. Monitor with Actuator `/metrics` and adjust.

---

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| Hardcoded -Xmx | Doesn't adapt to container limits | Use MaxRAMPercentage |
| Shell form ENTRYPOINT | SIGTERM not received by Java | Use exec form `["java"...]` |
| No layered JAR | Full 80MB rebuild every time | Use layertools extract |
| JDK in production image | 200MB+ wasted | Use JRE or distroless |
| No health check | Orchestrator can't detect failures | Use /actuator/health |
| Java 8 in containers | Not container-aware | Use Java 11+ minimum |
| Fat build context | Slow builds, large context | Use .dockerignore |
| No resource limits | OOM kills, noisy neighbors | Set memory/CPU limits |

---

## Best Practices

1. **Use Java 17/21 LTS** — full container support
2. **MaxRAMPercentage=75%** — leave room for non-heap
3. **Layered JARs** — efficient Docker layer caching
4. **Multi-stage builds** — build tools not in production
5. **Alpine or distroless** — minimal production base
6. **Exec form ENTRYPOINT** — proper signal handling
7. **Health checks** — actuator health endpoint
8. **Graceful shutdown** — drain connections before exit
9. **.dockerignore** — exclude target/, .git/, .idea/
10. **Container memory limits** — always set in compose/K8s

---

## Related Topics

- [14. Multi-Stage Builds](./14-docker-multi-stage-builds.md)
- [13. Docker Security](./13-docker-security.md)
- [12. Docker Compose](./12-docker-compose.md)
- [16. Docker Troubleshooting](./16-docker-troubleshooting.md)
