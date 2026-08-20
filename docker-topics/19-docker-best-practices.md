# 19. Docker Best Practices and Production Patterns ⭐⭐⭐

---

## Theory

**Docker best practices** encompass image optimization, runtime hardening, operational patterns, and production-readiness. Following these reduces image size, improves security, speeds builds, and ensures reliability.

### Image Optimization Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│              IMAGE OPTIMIZATION PYRAMID                       │
│                                                              │
│  Level 5: Distroless / Scratch          (smallest, most secure) │
│  Level 4: Alpine-based                   (5-50MB)           │
│  Level 3: Slim variants                  (50-200MB)         │
│  Level 2: Standard (debian/ubuntu)       (200-500MB)        │
│  Level 1: Full images (with build tools) (500MB-2GB)        │
│                                                              │
│  Rule: Production = Level 4 or 5                            │
│        Development = Level 2 or 3 (need debug tools)        │
└─────────────────────────────────────────────────────────────┘
```

### Dockerfile Best Practices

```dockerfile
# ─── 1. Use specific base image versions ───
# BAD
FROM openjdk:latest
# GOOD
FROM eclipse-temurin:21.0.2_13-jre-alpine

# ─── 2. Order layers by change frequency ───
# Rarely changes → first (cached)
COPY pom.xml .
RUN mvn dependency:go-offline
# Frequently changes → last
COPY src/ src/
RUN mvn package

# ─── 3. Combine RUN commands (reduce layers) ───
# BAD (3 layers, apt cache kept)
RUN apt-get update
RUN apt-get install -y curl
RUN rm -rf /var/lib/apt/lists/*

# GOOD (1 layer, clean)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# ─── 4. Use COPY, not ADD ───
# ADD does extra magic (tar extraction, URL fetch)
# COPY is explicit and predictable
COPY app.jar /app/app.jar

# ─── 5. Use .dockerignore ───
# Reduces build context, prevents accidental secret inclusion

# ─── 6. Use exec form for ENTRYPOINT ───
# Shell form: PID 1 = shell (signals not forwarded)
ENTRYPOINT java -jar app.jar        # BAD

# Exec form: PID 1 = java (signals received correctly)
ENTRYPOINT ["java", "-jar", "app.jar"]  # GOOD

# ─── 7. One process per container ───
# BAD: run multiple services in one container
# GOOD: one container = one service = one responsibility
```

### .dockerignore

```
# .dockerignore — ALWAYS create this file
.git/
.gitignore
.idea/
*.iml
.vscode/
target/
build/
node_modules/
dist/
*.log
*.md
!README.md
docker-compose*.yml
.env*
!.env.example
__pycache__/
.pytest_cache/
coverage/
.nyc_output/
```

### Container Runtime Best Practices

```yaml
services:
  api:
    image: myapp:1.2.3              # Pin version (not :latest)
    read_only: true                  # Immutable filesystem
    tmpfs:
      - /tmp                         # Writable temp only where needed
    security_opt:
      - no-new-privileges:true       # No privilege escalation
    cap_drop:
      - ALL                          # Drop all capabilities
    cap_add:
      - NET_BIND_SERVICE             # Add only what's needed
    deploy:
      resources:
        limits:
          memory: 512M               # Prevent OOM
          cpus: "1.0"                # Prevent CPU starvation
        reservations:
          memory: 256M               # Guaranteed minimum
    pids_limit: 100                  # Prevent fork bombs
    logging:
      driver: json-file
      options:
        max-size: "10m"              # Log rotation
        max-file: "3"
    restart: unless-stopped          # Auto-restart on failure
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s
```

### Twelve-Factor App in Docker

```
┌────────────────────────────────────────────────────────────┐
│ 12-Factor Principle        │ Docker Implementation          │
├────────────────────────────┼────────────────────────────────┤
│ I.   Codebase              │ Git + Dockerfile               │
│ II.  Dependencies          │ Declared in Dockerfile         │
│ III. Config                │ Environment variables          │
│ IV.  Backing services      │ Docker Compose services        │
│ V.   Build/Release/Run     │ Build: image, Run: container   │
│ VI.  Processes             │ Stateless containers           │
│ VII. Port binding          │ EXPOSE + port mapping          │
│ VIII.Concurrency           │ Scale via replicas             │
│ IX.  Disposability         │ Fast start, graceful shutdown  │
│ X.   Dev/Prod parity       │ Same image everywhere          │
│ XI.  Logs                  │ stdout/stderr → logging driver │
│ XII. Admin processes       │ docker exec / one-off containers│
└────────────────────────────┴────────────────────────────────┘
```

### Anti-Patterns

```
┌─────────────────────────────────────────────────────────────┐
│ ANTI-PATTERN                    │ WHAT TO DO INSTEAD         │
├─────────────────────────────────┼────────────────────────────┤
│ Store data in containers        │ Use volumes / external DB  │
│ Run multiple processes          │ One container per service  │
│ Install SSH in containers       │ Use docker exec            │
│ Use :latest in production       │ Pin versions (semver/SHA)  │
│ Build in production image       │ Multi-stage builds         │
│ Hardcode config                 │ Environment variables      │
│ Run as root                     │ USER nonroot               │
│ Ignore health checks            │ Always define healthcheck  │
│ Manual container management     │ Use orchestration (K8s)    │
│ Treat containers like VMs       │ Ephemeral, replaceable     │
│ Put secrets in images           │ Runtime injection / Vault  │
│ No log rotation                 │ max-size + max-file        │
│ docker-compose in production    │ Kubernetes / ECS           │
└─────────────────────────────────┴────────────────────────────┘
```

### Production Readiness Checklist

```
Image:
  ☐ Multi-stage build (no build tools in prod)
  ☐ Minimal base (alpine / distroless)
  ☐ Pinned versions (base image + packages)
  ☐ Non-root user (USER directive)
  ☐ No secrets in image
  ☐ Scanned for vulnerabilities
  ☐ .dockerignore configured
  ☐ Labels (version, maintainer, description)

Runtime:
  ☐ Read-only filesystem
  ☐ All capabilities dropped (add back only needed)
  ☐ No-new-privileges set
  ☐ Resource limits (memory, CPU, PIDs)
  ☐ Log rotation configured
  ☐ Health check defined
  ☐ Graceful shutdown handling
  ☐ Restart policy set

Networking:
  ☐ Internal networks for databases
  ☐ No unnecessary port publishing
  ☐ TLS for external communication
  ☐ DNS-based service discovery (not IPs)

Operations:
  ☐ Centralized logging (EFK / CloudWatch)
  ☐ Monitoring (Prometheus / Grafana)
  ☐ Alerting on unhealthy containers
  ☐ Backup strategy for volumes
  ☐ Disaster recovery plan
  ☐ Image update strategy (scan + redeploy)
```

---

## Code

### Production-Ready Microservice Template:

```dockerfile
# ============================================
# Dockerfile — Production-Ready Spring Boot
# ============================================

# ─── Stage 1: Dependencies ───
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ─── Stage 2: Build ───
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY --from=deps /root/.m2 /root/.m2
COPY pom.xml .
COPY src/ src/
RUN mvn package -DskipTests -o -B

# ─── Stage 3: Production ───
FROM eclipse-temurin:21-jre-alpine AS production

LABEL maintainer="team@company.com" \
      version="1.0.0" \
      description="Order Service"

# Security: update + non-root user
RUN apk --no-cache upgrade && \
    addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=30s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

### Production Docker Compose (Non-K8s Environments):

```yaml
# compose.yaml — Production-ready (for simple deployments)
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

x-security: &default-security
  security_opt:
    - no-new-privileges:true
  read_only: true
  cap_drop:
    - ALL

services:
  api:
    image: registry.io/order-service:1.2.3
    <<: *default-security
    cap_add:
      - NET_BIND_SERVICE
    tmpfs:
      - /tmp
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/orders
    env_file:
      - .env.production
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 512M
          cpus: "1.0"
        reservations:
          memory: 256M
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s
    logging: *default-logging
    restart: unless-stopped

  postgres:
    image: postgres:15-alpine
    <<: *default-security
    cap_add:
      - CHOWN
      - SETUID
      - SETGID
      - FOWNER
      - DAC_READ_SEARCH
    tmpfs:
      - /tmp
      - /run/postgresql
    volumes:
      - postgres-data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    deploy:
      resources:
        limits:
          memory: 1G
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5
    logging: *default-logging
    restart: unless-stopped
    networks:
      - backend

  redis:
    image: redis:7-alpine
    <<: *default-security
    cap_add:
      - SETUID
      - SETGID
    tmpfs:
      - /tmp
    volumes:
      - redis-data:/data
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru
    deploy:
      resources:
        limits:
          memory: 256M
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3
    logging: *default-logging
    restart: unless-stopped
    networks:
      - backend

volumes:
  postgres-data:
  redis-data:

networks:
  backend:
    driver: bridge
    internal: true
```

---

## Interview Questions

### Q1: What are the most important Docker best practices for production?

**A:** Top 5:
1. **Multi-stage builds** — no build tools in production (70% smaller images)
2. **Non-root user** — limits damage from container escape
3. **Resource limits** — prevents OOM kills and noisy neighbors
4. **Health checks** — enables self-healing and proper load balancing
5. **Immutable tags** — Git SHA or semver, never `:latest` in production

### Q2: How do you optimize Docker image size?

**A:** In order of impact:
1. Multi-stage builds (remove build tools) — saves 200-600MB
2. Minimal base image (alpine/distroless) — saves 100-300MB
3. Combine RUN commands — fewer layers
4. Clean up in same layer (`rm -rf /var/lib/apt/lists/*`)
5. Use .dockerignore — smaller build context
6. Copy only what's needed (`COPY target/*.jar` not `COPY . .`)

### Q3: Why should containers be ephemeral and stateless?

**A:** Containers should be:
- **Disposable** — destroyed and recreated without data loss
- **Stateless** — state stored externally (databases, object storage, caches)
- **Immutable** — same image everywhere, config via environment variables

This enables: horizontal scaling, rolling updates, self-healing, fast recovery. State in containers = data loss when container dies.

### Q4: What is the difference between COPY and ADD?

**A:** 
- `COPY` — simple file/directory copy. Predictable. Preferred.
- `ADD` — everything COPY does PLUS: auto-extracts tar archives, can download from URLs

Use `COPY` by default. Only use `ADD` for tar extraction. Never use ADD for URL downloads (use `curl` in RUN instead for better caching control).

### Q5: How do you handle configuration in Docker?

**A:** Follow 12-factor app:
1. **Environment variables** — primary mechanism (`-e KEY=value`)
2. **env_file** — group related config (`.env.production`)
3. **Config volumes** — mount config files at runtime
4. **Spring profiles** — `SPRING_PROFILES_ACTIVE=docker`
5. Never hardcode config in Dockerfile — it's baked into image layers

---

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| No .dockerignore | 500MB+ build context | Always create .dockerignore |
| Installing unnecessary packages | Larger image, more CVEs | `--no-install-recommends` |
| Not cleaning apt cache | 50-200MB wasted | `rm -rf /var/lib/apt/lists/*` in same RUN |
| Using ADD for simple copy | Unexpected behavior | Use COPY always |
| Multiple CMD/ENTRYPOINT | Only last one takes effect | Understand override order |
| Storing state in containers | Data loss on restart | External volumes/databases |
| No restart policy | Services stay down after crash | `restart: unless-stopped` |
| Building in production environment | Slow, insecure | Build in CI, deploy images |

---

## Best Practices Summary

```
Image Building:
  1. Multi-stage builds (always)
  2. Pin base image versions
  3. Order layers by change frequency
  4. Combine RUN commands + clean up
  5. Use COPY not ADD
  6. Create .dockerignore
  7. Use exec form ENTRYPOINT

Security:
  8. Non-root USER
  9. Read-only filesystem
  10. Drop all capabilities
  11. No secrets in images
  12. Scan for vulnerabilities
  13. Update base images regularly

Operations:
  14. Resource limits (memory/CPU)
  15. Health checks on everything
  16. Log rotation configured
  17. Graceful shutdown handling
  18. Immutable image tags
  19. Centralized logging
  20. Monitoring and alerting
```

---

## Related Topics

- [13. Docker Security](./13-docker-security.md)
- [14. Multi-Stage Builds](./14-docker-multi-stage-builds.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
- [18. Docker in CI/CD](./18-docker-in-ci-cd.md)
- [20. Docker Interview Scenarios](./20-docker-interview-scenarios.md)
