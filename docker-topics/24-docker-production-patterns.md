# 24. Docker Production Patterns ⭐⭐⭐

---

## Theory

**Docker production patterns** are architectural approaches for deploying, managing, and scaling containerized applications reliably. These patterns solve real-world problems like service communication, configuration management, data persistence, and failure handling.

### Sidecar Pattern

```
┌─────────────────────────────────────────────────────────┐
│                    SIDECAR PATTERN                        │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Pod / Task / Container Group                      │   │
│  │                                                   │   │
│  │  ┌─────────────┐    ┌─────────────────────────┐ │   │
│  │  │  Main App   │    │  Sidecar Container      │ │   │
│  │  │ (Order API) │    │  (log agent / proxy /   │ │   │
│  │  │             │◄──►│   config reload)        │ │   │
│  │  └─────────────┘    └─────────────────────────┘ │   │
│  │        │                       │                  │   │
│  │  Shared: network namespace, volumes              │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  Use cases:                                              │
│    - Log collection (Fluentd sidecar)                   │
│    - Service mesh proxy (Envoy sidecar)                 │
│    - Configuration reloader                              │
│    - TLS termination                                     │
│    - Monitoring agent                                    │
└─────────────────────────────────────────────────────────┘
```

```yaml
# Docker Compose sidecar pattern
services:
  api:
    image: order-service:1.0
    volumes:
      - app-logs:/var/log/app
    networks:
      - backend

  log-agent:
    image: fluent/fluent-bit:latest
    volumes:
      - app-logs:/var/log/app:ro
      - ./fluent-bit.conf:/fluent-bit/etc/fluent-bit.conf
    depends_on:
      - api
    networks:
      - backend

volumes:
  app-logs:
```

### Ambassador Pattern

```
┌─────────────────────────────────────────────────────────┐
│                  AMBASSADOR PATTERN                       │
│                                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │                                                  │    │
│  │  ┌─────────────┐    ┌───────────────────────┐  │    │
│  │  │  Main App   │───▶│  Ambassador (Proxy)   │──┼──▶ External Service
│  │  │             │    │  - Connection pooling  │  │    │
│  │  │ localhost:  │    │  - Retry logic         │  │    │
│  │  │ 5432       │    │  - Circuit breaking    │  │    │
│  │  └─────────────┘    └───────────────────────┘  │    │
│  │                                                  │    │
│  └─────────────────────────────────────────────────┘    │
│                                                          │
│  App thinks it's connecting to localhost:5432            │
│  Ambassador handles: discovery, auth, retry, failover   │
└─────────────────────────────────────────────────────────┘
```

```yaml
# Ambassador for database connection
services:
  api:
    image: order-service:1.0
    environment:
      - DB_HOST=localhost        # Connects to ambassador!
      - DB_PORT=5432

  pgbouncer:                    # Ambassador: connection pool
    image: edoburu/pgbouncer
    environment:
      - DATABASE_URL=postgres://admin:secret@postgres:5432/orders
      - MAX_CLIENT_CONN=200
      - DEFAULT_POOL_SIZE=20
    network_mode: "service:api"  # Shares network namespace with api
```

### Init Container Pattern

```
┌─────────────────────────────────────────────────────────┐
│                 INIT CONTAINER PATTERN                    │
│                                                          │
│  Sequential execution:                                   │
│                                                          │
│  ┌────────────────┐   ┌────────────────┐   ┌────────┐ │
│  │ Init 1:        │──▶│ Init 2:        │──▶│ Main   │ │
│  │ Wait for DB    │   │ Run migrations │   │ App    │ │
│  │ (must succeed) │   │ (must succeed) │   │ Start  │ │
│  └────────────────┘   └────────────────┘   └────────┘ │
│                                                          │
│  Use cases:                                              │
│    - Wait for dependencies (DB, Kafka ready?)           │
│    - Run database migrations before app starts          │
│    - Download config/secrets                             │
│    - Set file permissions                                │
└─────────────────────────────────────────────────────────┘
```

```yaml
# Docker Compose init pattern using depends_on + healthcheck
services:
  migrate:
    image: flyway/flyway
    command: migrate
    environment:
      - FLYWAY_URL=jdbc:postgresql://postgres:5432/orders
      - FLYWAY_USER=admin
      - FLYWAY_PASSWORD=secret
    volumes:
      - ./db/migrations:/flyway/sql
    depends_on:
      postgres:
        condition: service_healthy

  api:
    image: order-service:1.0
    depends_on:
      migrate:
        condition: service_completed_successfully
      postgres:
        condition: service_healthy
```

### Health Check Patterns

```yaml
# ─── HTTP Health Check (Spring Boot) ───
services:
  api:
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 40s    # Grace period for startup

# ─── TCP Health Check (Database) ───
  postgres:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d orders"]
      interval: 5s
      timeout: 3s
      retries: 5

# ─── Command Health Check (Kafka) ───
  kafka:
    healthcheck:
      test: kafka-topics --bootstrap-server localhost:9092 --list
      interval: 10s
      timeout: 5s
      retries: 5

# ─── Custom Script Health Check ───
  api:
    healthcheck:
      test: ["CMD", "/app/healthcheck.sh"]
      interval: 30s
```

### Blue-Green Deployment

```
┌─────────────────────────────────────────────────────────┐
│              BLUE-GREEN DEPLOYMENT                        │
│                                                          │
│  Load Balancer (nginx/traefik)                          │
│       │                                                  │
│       ├──[current traffic]──▶ BLUE (v1.0) ✓ running    │
│       │                                                  │
│       └──[new deployment]───▶ GREEN (v2.0) ✓ healthy   │
│                                                          │
│  Steps:                                                  │
│    1. Deploy v2.0 as GREEN (alongside BLUE)             │
│    2. Test GREEN independently                           │
│    3. Switch load balancer to GREEN                      │
│    4. Monitor for errors                                 │
│    5. If OK: remove BLUE                                 │
│    6. If FAIL: switch back to BLUE (instant rollback)   │
└─────────────────────────────────────────────────────────┘
```

```yaml
# Blue-Green with Traefik
services:
  traefik:
    image: traefik:v3.0
    command:
      - --providers.docker=true
      - --entrypoints.web.address=:80
    ports:
      - "80:80"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro

  api-blue:
    image: order-service:1.0
    labels:
      - "traefik.http.routers.api.rule=Host(`api.example.com`)"
      - "traefik.http.services.api.loadbalancer.server.port=8080"

  # Deploy green, verify, then update labels to switch traffic
  api-green:
    image: order-service:2.0
    labels:
      - "traefik.enable=false"  # Not receiving traffic yet
```

### Data Persistence Patterns

```yaml
# ─── Pattern 1: Named Volumes (recommended) ───
services:
  postgres:
    image: postgres:15-alpine
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
    driver: local

# Data persists across container restarts and recreates
# Managed by Docker (backed up with docker volume commands)

# ─── Pattern 2: Bind Mounts (development) ───
services:
  api:
    volumes:
      - ./src:/app/src          # Live code sync
      - ./config:/app/config    # Configuration

# ─── Pattern 3: External Storage (production) ───
# Don't run databases in containers for production!
# Use managed services: AWS RDS, Cloud SQL, Azure SQL
services:
  api:
    environment:
      - DB_HOST=my-db.rds.amazonaws.com  # External managed DB
```

### Configuration Patterns

```yaml
# ─── Pattern 1: Environment Variables ───
services:
  api:
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SERVER_PORT=8080

# ─── Pattern 2: Environment File ───
services:
  api:
    env_file:
      - .env.common
      - .env.production

# ─── Pattern 3: Config Volume Mount ───
services:
  api:
    volumes:
      - ./config/application-prod.yml:/app/config/application.yml:ro

# ─── Pattern 4: Docker Configs (Swarm) ───
services:
  api:
    configs:
      - source: app_config
        target: /app/config/application.yml

configs:
  app_config:
    file: ./config/application-prod.yml

# ─── Pattern 5: External Config Service ───
services:
  api:
    environment:
      - SPRING_CLOUD_CONFIG_URI=http://config-server:8888
```

---

## Code

### Complete Production Pattern — Microservice with Sidecar:

```yaml
# compose-production.yaml
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"

x-healthcheck-defaults: &healthcheck-defaults
  interval: 15s
  timeout: 5s
  retries: 3
  start_period: 30s

services:
  # ─── Main Application ───
  order-service:
    image: registry.io/order-service:${APP_VERSION}
    read_only: true
    tmpfs:
      - /tmp
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 512M
          cpus: "1.0"
      update_config:
        parallelism: 1
        delay: 15s
        order: start-first
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
    env_file:
      - .env.production
    healthcheck:
      <<: *healthcheck-defaults
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
    depends_on:
      db-migration:
        condition: service_completed_successfully
    logging: *default-logging
    restart: unless-stopped
    networks:
      - frontend
      - backend

  # ─── Init: Database Migration ───
  db-migration:
    image: flyway/flyway:latest
    command: migrate
    environment:
      - FLYWAY_URL=jdbc:postgresql://postgres:5432/orders
    env_file:
      - .env.production
    volumes:
      - ./db/migrations:/flyway/sql:ro
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - backend

  # ─── Sidecar: Observability Collector ───
  otel-collector:
    image: otel/opentelemetry-collector:latest
    volumes:
      - ./otel-config.yaml:/etc/otel/config.yaml:ro
    command: ["--config", "/etc/otel/config.yaml"]
    deploy:
      resources:
        limits:
          memory: 256M
    logging: *default-logging
    networks:
      - backend
      - monitoring

  # ─── Infrastructure ───
  postgres:
    image: postgres:15-alpine
    read_only: true
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
      <<: *healthcheck-defaults
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d orders"]
      interval: 5s
    logging: *default-logging
    restart: unless-stopped
    networks:
      - backend

  # ─── Reverse Proxy ───
  nginx:
    image: nginx:alpine
    ports:
      - "443:443"
      - "80:80"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/certs:/etc/nginx/certs:ro
    depends_on:
      order-service:
        condition: service_healthy
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 128M
    logging: *default-logging
    restart: unless-stopped
    networks:
      - frontend

volumes:
  postgres-data:

networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true
  monitoring:
    driver: bridge
    internal: true
```

---

## Interview Questions

### Q1: Explain the sidecar pattern in Docker/Kubernetes.

**A:** A sidecar is an auxiliary container that runs alongside the main application container, sharing the same network and/or volume namespace. Common uses:
- **Log agent** — collects and forwards logs (Fluentd, Filebeat)
- **Service mesh proxy** — handles mTLS, routing, observability (Envoy/Istio)
- **Config reloader** — watches for config changes and signals the main app

The main container focuses on business logic; sidecars handle cross-cutting concerns.

### Q2: How do you handle database migrations with Docker?

**A:** Use the init container pattern:
1. Define a migration service (Flyway/Liquibase) that runs migrations
2. Use `depends_on` with `condition: service_healthy` to wait for DB
3. Main app uses `depends_on` with `condition: service_completed_successfully` on migration service
4. Migration runs once, succeeds, then app starts with correct schema

Never run migrations inside the application container (race conditions with replicas).

### Q3: What is blue-green deployment with Docker?

**A:** Deploy the new version (green) alongside the current version (blue):
1. Start green containers with new image
2. Verify green is healthy (health checks, smoke tests)
3. Switch load balancer/reverse proxy to point to green
4. Monitor for errors
5. If OK: remove blue. If errors: switch back instantly.

Benefits: instant rollback, zero-downtime, full testing before traffic switch.

### Q4: Should you run databases in Docker containers in production?

**A:** Generally no for stateful workloads requiring:
- High durability (data must survive any failure)
- Complex replication (primary/replica, failover)
- Performance-sensitive I/O
- Professional backups and point-in-time recovery

Use managed services (RDS, Cloud SQL). Containers are ideal for stateless services.
Exception: Development/testing environments — databases in containers are fine.

### Q5: How do you implement graceful shutdown in a Docker production pattern?

**A:** Multiple layers:
1. **Application:** Handle SIGTERM, drain connections, finish in-flight requests
2. **Docker:** Use exec form ENTRYPOINT (PID 1 receives signal), set `stop_grace_period`
3. **Load balancer:** Deregister container before stopping (preStop hook in K8s)
4. **Orchestration:** `order: stop-last` or rolling update with `start-first`
5. **Health check:** Mark unhealthy during drain → LB stops sending new traffic

---

## Best Practices

1. **One service per container** — separation of concerns
2. **Sidecars for cross-cutting concerns** — logging, monitoring, proxying
3. **Init containers for prerequisites** — migrations, config fetch, dependency wait
4. **Health checks everywhere** — enables all orchestration features
5. **Externalize state** — managed databases, object storage
6. **Immutable containers** — never modify running containers
7. **Config via environment** — 12-factor app principles
8. **Log to stdout** — let infrastructure handle collection
9. **Graceful shutdown** — handle SIGTERM, drain connections
10. **Version everything** — images, configs, compose files in Git

---

## Related Topics

- [12. Docker Compose](./12-docker-compose.md)
- [19. Docker Best Practices](./19-docker-best-practices.md)
- [21. Docker Orchestration Overview](./21-docker-orchestration-overview.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
