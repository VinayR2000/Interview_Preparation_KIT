# 20. Docker Interview Scenarios ⭐⭐⭐

---

## Theory

This topic covers real-world Docker scenarios commonly asked in senior software engineer interviews. These questions test practical Docker knowledge, problem-solving, and production experience.

---

## Scenario Questions

### Scenario 1: Design a Dockerized Microservices Architecture

**Q:** You're building an order management system with 3 microservices (Order, Payment, Notification), PostgreSQL, Redis, and Kafka. Design the Docker setup.

**A:**

```yaml
# compose.yaml
services:
  # ─── Application Services ───
  order-service:
    build:
      context: ./order-service
      target: production
    ports:
      - "8081:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_URL=jdbc:postgresql://postgres:5432/orders
      - KAFKA_BOOTSTRAP=kafka:9092
      - REDIS_HOST=redis
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 512M
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      retries: 3
    networks:
      - app-network
      - kafka-network

  payment-service:
    build:
      context: ./payment-service
      target: production
    environment:
      - KAFKA_BOOTSTRAP=kafka:9092
    depends_on:
      kafka:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 512M
    networks:
      - kafka-network

  notification-service:
    build:
      context: ./notification-service
      target: production
    environment:
      - KAFKA_BOOTSTRAP=kafka:9092
    depends_on:
      kafka:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 256M
    networks:
      - kafka-network

  # ─── Infrastructure ───
  postgres:
    image: postgres:15-alpine
    volumes:
      - postgres-data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 5s
      retries: 5
    networks:
      - app-network

  redis:
    image: redis:7-alpine
    command: redis-server --maxmemory 128mb --maxmemory-policy allkeys-lru
    networks:
      - app-network

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
    healthcheck:
      test: kafka-topics --bootstrap-server localhost:9092 --list
      interval: 10s
      retries: 5
    networks:
      - kafka-network

volumes:
  postgres-data:

networks:
  app-network:
    driver: bridge
  kafka-network:
    driver: bridge
```

**Key Design Decisions:**
- Separate networks (app-network, kafka-network) — isolation by concern
- Health checks on all infrastructure — prevents premature service starts
- Resource limits — predictable resource usage
- Named volumes — data persistence across restarts
- Multi-replica for order-service — handle load

---

### Scenario 2: Container is Consuming 100% Memory and Getting OOM Killed

**Q:** Your Java service container keeps getting OOM killed. Memory limit is 512MB. How do you diagnose and fix?

**A:**

```bash
# Step 1: Confirm OOM kill
docker inspect --format='{{.State.OOMKilled}}' order-service
# true

# Step 2: Check memory usage breakdown
docker stats --no-stream order-service
# MEM USAGE: 510MiB / 512MiB (99.6%)

# Step 3: Get into container to analyze
docker exec order-service jcmd 1 VM.native_memory summary
docker exec order-service jcmd 1 GC.heap_info

# Step 4: Check JVM flags
docker exec order-service java -XX:+PrintFlagsFinal -version | grep -i heap
```

**Root Causes & Fixes:**

```bash
# Cause 1: JVM heap too large (no room for non-heap)
# Fix: Reduce MaxRAMPercentage from 75% to 65%
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]

# Cause 2: Metaspace growing unbounded
# Fix: Set limit
ENTRYPOINT ["java", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]

# Cause 3: Too many threads (each takes 1MB stack)
# Fix: Reduce thread stack size or thread count
ENTRYPOINT ["java", "-Xss512k", "-jar", "app.jar"]

# Cause 4: Memory leak in application
# Fix: Heap dump and analyze
docker exec order-service jcmd 1 GC.heap_dump /tmp/heap.hprof
docker cp order-service:/tmp/heap.hprof ./heap.hprof
# Analyze with Eclipse MAT or VisualVM

# Cause 5: Container limit too low for the workload
# Fix: Increase limit
deploy:
  resources:
    limits:
      memory: 768M  # Increased from 512M
```

---

### Scenario 3: Two Containers Can't Communicate

**Q:** Your API container can't connect to the database container. `Connection refused` error. How do you troubleshoot?

**A:**

```bash
# Step 1: Check both containers are running
docker compose ps
# NAME       STATUS        PORTS
# api        Up (healthy)  8080
# postgres   Up (healthy)  5432

# Step 2: Check they're on the same network
docker network inspect app-network
# Check both containers listed under "Containers"

# Step 3: DNS resolution from API container
docker exec api nslookup postgres
# If fails → not on same network or service name wrong

# Step 4: TCP connectivity test
docker exec api nc -zv postgres 5432
# If fails → postgres not listening on that port/network

# Step 5: Check connection string
docker exec api env | grep DB
# Verify using service name "postgres", not "localhost"!

# Common fixes:
# 1. Wrong hostname: using 'localhost' instead of 'postgres'
# 2. Different networks: add both to same network
# 3. Port mismatch: container port vs host port confusion
# 4. Service not ready: needs healthcheck + depends_on condition
# 5. Firewall/security group: in cloud environments
```

---

### Scenario 4: Docker Image is 1.2GB — Optimize It

**Q:** Your team's Docker image is 1.2GB. Walk me through optimization.

**A:**

```bash
# Step 1: Analyze current image
docker history myapp:latest
# Shows each layer and size — find the big ones

# Step 2: Check build context
docker build . 2>&1 | head -3
# "Sending build context to Docker daemon  450MB"
# → Need .dockerignore!
```

```dockerfile
# BEFORE: 1.2GB
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y openjdk-21-jdk maven
COPY . /app
WORKDIR /app
RUN mvn package
EXPOSE 8080
CMD ["java", "-jar", "target/app.jar"]

# AFTER: ~200MB
# Stage 1: Build (discarded)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn package -DskipTests -o

# Stage 2: Production
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

```
Size breakdown:
  Before:
    ubuntu base:     77MB
    JDK:            400MB
    Maven:          300MB
    Source + deps:  200MB+
    App:             ~2MB
    Total:         ~1.2GB

  After:
    alpine base:      7MB
    JRE:            180MB
    App:              2MB
    Total:          ~200MB (83% reduction!)
```

---

### Scenario 5: Zero-Downtime Deployment with Docker

**Q:** How do you achieve zero-downtime deployments with Docker?

**A:**

```yaml
# Option 1: Docker Compose with load balancer (simple)
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - api

  api:
    image: myapp:1.0
    deploy:
      replicas: 3
      update_config:
        parallelism: 1        # Update one at a time
        delay: 10s            # Wait between updates
        order: start-first    # Start new before stopping old
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/health"]
      interval: 10s
      retries: 3
```

```bash
# Rolling update procedure:
# 1. Build new image
docker build -t myapp:2.0 .

# 2. Update compose file to use new version
# 3. Rolling restart
docker compose up -d --no-deps api

# Kubernetes approach (production):
kubectl set image deployment/api api=myapp:2.0
# K8s handles rolling update automatically
```

**Requirements for zero-downtime:**
1. Health checks — new container must be healthy before old dies
2. Graceful shutdown — drain existing connections (30s grace period)
3. Multiple replicas — always have running instances
4. Load balancer — routes traffic away from stopping containers
5. Backward-compatible changes — new version must work with old data

---

### Scenario 6: Secure Secrets in Docker

**Q:** Your team hardcodes database passwords in Dockerfiles and compose files. Fix this.

**A:**

```yaml
# ─── BAD (secrets exposed) ───
services:
  api:
    environment:
      - DB_PASSWORD=SuperSecret123    # Visible in docker inspect!

# ─── GOOD: Level 1 — .env file (dev only) ───
services:
  api:
    env_file:
      - .env.local    # Git-ignored, local only
# .env.local
# DB_PASSWORD=SuperSecret123

# ─── GOOD: Level 2 — Docker secrets (Swarm) ───
services:
  api:
    secrets:
      - db_password
    environment:
      - DB_PASSWORD_FILE=/run/secrets/db_password
secrets:
  db_password:
    external: true    # Pre-created via: docker secret create

# ─── GOOD: Level 3 — External secret manager (production) ───
# Application fetches from HashiCorp Vault / AWS Secrets Manager at startup
services:
  api:
    environment:
      - VAULT_ADDR=http://vault:8200
      - VAULT_ROLE=order-service
      # App uses Spring Cloud Vault to fetch secrets at boot

# ─── CI/CD: inject at deploy time ───
# Secrets stored in CI/CD secret store (GitHub Secrets, GitLab Variables)
# Injected as environment variables during deployment
```

---

### Scenario 7: Container Filesystem Full

**Q:** Container keeps crashing with "no space left on device" but host has plenty of space.

**A:**

```bash
# Step 1: Check Docker storage
docker system df
# TYPE          TOTAL    ACTIVE   SIZE     RECLAIMABLE
# Images        45       10       12.5GB   8.2GB (65%)
# Containers    15       5        2.1GB    1.8GB
# Volumes       20       8        5.6GB    3.2GB
# Build Cache                     4.3GB    4.3GB

# Step 2: Identify the problem
# a) Too many images cached
docker image prune -a    # Remove unused images

# b) Container logs too large (no rotation!)
docker inspect --format='{{.LogPath}}' <container>
ls -lh /var/lib/docker/containers/<id>/<id>-json.log
# Fix: add log rotation

# c) Large overlay filesystem (container writing internally)
docker exec <container> du -sh /tmp /var/log /app
# Fix: use volumes for data, read-only filesystem + tmpfs

# Step 3: Clean up
docker system prune -a --volumes  # Nuclear option (removes everything unused)

# Step 4: Prevention
# - Log rotation: max-size: 10m, max-file: 3
# - Read-only containers with tmpfs
# - External volumes for data
# - Periodic `docker system prune` in cron
# - Monitor disk usage with alerts
```

---

### Scenario 8: Docker Compose for Local Development with Hot Reload

**Q:** Set up a development environment where code changes reflect immediately without rebuilding.

**A:**

```yaml
# compose-dev.yaml
services:
  api:
    build:
      context: .
      target: development
    ports:
      - "8080:8080"
      - "5005:5005"                    # Remote debug
      - "35729:35729"                  # LiveReload
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - SPRING_DEVTOOLS_RESTART_ENABLED=true
    volumes:
      - ./src:/app/src                  # Mount source (hot reload)
      - ./target/classes:/app/classes   # Compiled classes
      - maven-cache:/root/.m2          # Cache deps across rebuilds
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      target: development
    ports:
      - "4200:4200"
    volumes:
      - ./frontend/src:/app/src         # Angular source
      - /app/node_modules               # Preserve node_modules from image
    command: ng serve --host 0.0.0.0 --poll 2000

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: mydb
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    ports:
      - "5432:5432"                     # Access from host tools (DBeaver)
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dev"]
      interval: 3s
      retries: 5

volumes:
  postgres-data:
  maven-cache:
```

```bash
# Start development environment
docker compose -f compose-dev.yaml up

# Make code change → Spring DevTools detects → auto-restart (seconds)
# Frontend change → Angular CLI detects → hot module replacement
```

---

## Quick-Fire Interview Questions

### Q: What's the difference between CMD and ENTRYPOINT?
**A:** ENTRYPOINT defines the executable (can't be overridden easily). CMD provides default arguments (overridden by docker run args). Together: `ENTRYPOINT ["java", "-jar"]` + `CMD ["app.jar"]` → user can override the JAR name.

### Q: EXPOSE does what exactly?
**A:** Documentation only. EXPOSE doesn't publish ports. It tells humans/tools which ports the container listens on. Actual publishing requires `-p 8080:8080` or `ports:` in compose.

### Q: How do you debug a container that won't start?
**A:** `docker logs <container>`, check exit code with `docker ps -a`, override entrypoint: `docker run -it --entrypoint sh <image>`, inspect environment and file permissions.

### Q: What happens to data when a container is removed?
**A:** Container filesystem is lost. Data in named volumes persists. Data in anonymous volumes persists until pruned. Data in bind mounts persists (it's on the host).

### Q: How would you share data between containers?
**A:** Named volumes (shared mount), bind mounts (same host path), or container networking (API calls). Prefer API calls for microservices. Use volumes for shared files (rare).

### Q: What's the difference between docker stop and docker kill?
**A:** `docker stop` sends SIGTERM (graceful, 10s timeout) then SIGKILL. `docker kill` sends SIGKILL immediately (no cleanup). Always prefer stop for graceful shutdown.

### Q: How do you limit a container's resources?
**A:** `--memory=512m` (hard memory limit), `--cpus=1.5` (CPU limit), `--pids-limit=100` (process limit). In compose: use `deploy.resources.limits`.

---

## Related Topics

- [13. Docker Security](./13-docker-security.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
- [16. Docker Troubleshooting](./16-docker-troubleshooting.md)
- [19. Docker Best Practices](./19-docker-best-practices.md)
