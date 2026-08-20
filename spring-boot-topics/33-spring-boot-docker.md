# 33. Spring Boot + Docker

## Theory

Docker containerizes Spring Boot applications into lightweight, portable, reproducible units that run consistently across environments. A Docker image packages the JVM, application JAR, and all dependencies together.

### Key Concepts:
- **Dockerfile**: Instructions to build an image
- **Image**: Immutable template (like a class)
- **Container**: Running instance of an image (like an object)
- **Multi-stage Build**: Separate build and runtime stages (smaller images)
- **Docker Compose**: Multi-container orchestration for local development
- **Health Checks**: Container-level health monitoring

### Build Flow:
```
Source Code → Maven Build → JAR → Docker Image → Container
```

---

## Internal Working

```
docker build -t myapp:1.0 .
       ↓
Read Dockerfile instructions
       ↓
Layer 1: FROM eclipse-temurin:21-jre (base OS + JVM)
Layer 2: COPY app.jar (application)
Layer 3: EXPOSE 8080 (metadata)
Layer 4: ENTRYPOINT ["java", "-jar", "app.jar"]
       ↓
Image created (each layer is cached)
       ↓
docker run -p 8080:8080 myapp:1.0
       ↓
Container starts → JVM starts → Spring Boot starts
       ↓
Application ready at localhost:8080
```

### Layer Caching:
```
Unchanged layers = cached (instant)
Changed layers = rebuilt

Optimization: Put rarely-changing layers first
  Layer 1: Base image (almost never changes)
  Layer 2: Dependencies (changes on pom.xml update)
  Layer 3: Application code (changes every build)
```

---

## Diagram

```
┌──────────── Multi-Stage Build ─────────────────────────────┐
│                                                              │
│  Stage 1: BUILD                                             │
│  ┌────────────────────────────────────────────────────┐    │
│  │ FROM maven:3.9-eclipse-temurin-21 AS build          │    │
│  │                                                      │    │
│  │ Source Code + pom.xml                                │    │
│  │      ↓                                               │    │
│  │ mvn package -DskipTests                             │    │
│  │      ↓                                               │    │
│  │ target/app.jar (fat JAR ~80MB)                      │    │
│  └────────────────────────────────────────────────────┘    │
│                          │                                   │
│                          ↓                                   │
│  Stage 2: RUNTIME                                           │
│  ┌────────────────────────────────────────────────────┐    │
│  │ FROM eclipse-temurin:21-jre-alpine (slim image)     │    │
│  │                                                      │    │
│  │ COPY --from=build app.jar                           │    │
│  │      ↓                                               │    │
│  │ Final image: ~200MB (vs ~800MB with full JDK+Maven) │    │
│  └────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

---

## Code

### Basic Dockerfile:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Multi-Stage Dockerfile (Production):

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Layered JAR Dockerfile (Optimal Caching):

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# Stage 2: Runtime with layers
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# Copy layers (least changing first = better caching)
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

USER app
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Docker Compose (Development):

```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mydb
      - SPRING_DATASOURCE_USERNAME=admin
      - SPRING_DATASOURCE_PASSWORD=secret
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 40s

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: mydb
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d mydb"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093

volumes:
  postgres_data:
```

### Spring Boot Configuration for Docker:

```yaml
# application-docker.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mydb}
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:secret}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

server:
  port: ${SERVER_PORT:8080}
```

---

## Dry Run

### Building and running:

```
$ docker build -t order-service:1.0 .

Step 1/8: FROM maven AS build
  → Pulls maven image (cached if exists)
Step 2/8: COPY pom.xml
  → Layer cached if pom.xml unchanged
Step 3/8: RUN mvn dependency:go-offline
  → Layer cached if dependencies unchanged (huge time saver!)
Step 4/8: COPY src
  → Always rebuilt (code changes)
Step 5/8: RUN mvn package
  → Compiles and packages

Step 6/8: FROM eclipse-temurin:21-jre-alpine
  → Slim runtime image
Step 7/8: COPY --from=build app.jar
  → Only the JAR, not Maven/source
Step 8/8: ENTRYPOINT...

Image size: ~200MB (vs ~800MB without multi-stage)

$ docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod order-service:1.0

  → JVM starts with container-aware memory settings
  → Spring Boot starts on port 8080
  → Healthcheck passes after ~15s
  → Ready to serve traffic
```

---

## Complexity

| Aspect | Impact |
|--------|--------|
| Image build (first time) | ~3-5 minutes (downloads dependencies) |
| Image build (cached layers) | ~30-60 seconds (only rebuilds changed layers) |
| Image size (basic) | ~400-500MB |
| Image size (multi-stage + alpine) | ~200-250MB |
| Container startup | ~5-20 seconds (Spring Boot startup) |
| Container memory | 256MB-512MB typical (depends on app) |

---

## Real Project Usage

### CI/CD Pipeline:

```yaml
# GitHub Actions
jobs:
  build:
    steps:
      - uses: actions/checkout@v4
      - name: Build and push Docker image
        run: |
          docker build -t registry.example.com/order-service:${{ github.sha }} .
          docker push registry.example.com/order-service:${{ github.sha }}
```

---

## Interview Questions

1. **Why use multi-stage builds?**
   - Separates build tools from runtime. Final image only has JRE + JAR (not Maven, source, build artifacts). Much smaller, more secure.

2. **How does JVM handle container memory limits?**
   - `-XX:+UseContainerSupport` (default since Java 10) respects Docker memory limits. `-XX:MaxRAMPercentage=75.0` uses 75% of container memory for heap.

3. **How to optimize Docker layer caching for Spring Boot?**
   - Copy pom.xml and download dependencies BEFORE copying source code. Dependencies layer is cached unless pom.xml changes.

4. **How to pass configuration to containerized Spring Boot?**
   - Environment variables: `SPRING_DATASOURCE_URL=...`. Spring Boot auto-maps env vars to properties.

5. **Docker Compose vs Kubernetes?**
   - Compose: Local development, single-machine orchestration. K8s: Production, multi-node, auto-scaling, self-healing. Compose for dev, K8s for prod.

---

## Common Mistakes

1. **Using JDK instead of JRE** - JDK adds ~200MB; runtime only needs JRE
2. **Not using multi-stage build** - Final image includes Maven, source code (large + insecure)
3. **Running as root** - Security vulnerability; always use non-root user
4. **No health check** - Orchestrator can't determine container health
5. **Hardcoded configuration** - Should use environment variables for portability
6. **Not setting JVM memory limits** - JVM may use more memory than container allows → OOMKilled

---

## Best Practices

1. **Multi-stage builds** - Separate build and runtime
2. **Alpine-based images** - Smaller footprint
3. **Non-root user** - Security best practice
4. **Layer ordering** - Dependencies before source (better caching)
5. **Container-aware JVM** - UseContainerSupport + MaxRAMPercentage
6. **Health checks** - In Dockerfile and Docker Compose
7. **Environment variables** for all configuration
8. **.dockerignore** - Exclude .git, target, node_modules, etc.

---

## Production Considerations

- **Image scanning**: Scan for vulnerabilities (Trivy, Snyk)
- **Image registry**: Use private registry (ECR, GCR, Harbor)
- **Immutable tags**: Use SHA or version tags, not `latest`
- **Resource limits**: Always set memory and CPU limits
- **Logging**: Log to stdout (collected by Docker/K8s log driver)
- **Secrets**: Use Docker secrets or K8s secrets, NOT environment variables for sensitive data
- **Graceful shutdown**: SIGTERM handling with `server.shutdown=graceful`

---

## Related Topics

- Kubernetes (production orchestration)
- Spring Boot Actuator (health checks)
- CI/CD (automated builds)
- Spring Profiles (environment configuration)
- Microservices (containerized services)
