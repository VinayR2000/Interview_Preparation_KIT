# 12. Docker Compose ⭐⭐⭐

---

## Theory

**Docker Compose** defines and runs multi-container applications using a YAML file. It manages the entire application stack (services, networks, volumes) with a single command.

### What is Docker Compose?

```
Docker Compose:
  - Define multi-container apps in compose.yaml
  - Start/stop entire stack with one command
  - Automatic networking between services
  - Volume management
  - Environment configuration
  - Development + testing workflow

Without Compose: 10+ docker run commands with flags
With Compose:    docker compose up
```

### compose.yaml

```yaml
# compose.yaml (or docker-compose.yml)
services:
  api:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_HOST=postgres
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - backend

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: orders
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: secret
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 5s
      timeout: 3s
      retries: 5
    networks:
      - backend

  redis:
    image: redis:7-alpine
    networks:
      - backend

volumes:
  postgres-data:

networks:
  backend:
    driver: bridge
```

### Services

```yaml
services:
  order-service:
    image: order-service:2.1.0        # Use existing image
    # OR
    build:                             # Build from Dockerfile
      context: .
      dockerfile: Dockerfile
      args:
        APP_VERSION: 2.1.0
    container_name: order-service      # Fixed name (optional)
    restart: unless-stopped            # Restart policy
    ports:
      - "8080:8080"
    expose:
      - "9090"                         # Internal only (no host mapping)
```

### Networks

```yaml
services:
  frontend:
    networks:
      - frontend-net
  api:
    networks:
      - frontend-net
      - backend-net
  database:
    networks:
      - backend-net

networks:
  frontend-net:
    driver: bridge
  backend-net:
    driver: bridge
    internal: true      # No external access

# frontend ↔ api ↔ database
# frontend ✗ database (different networks!)
```

### Volumes

```yaml
services:
  postgres:
    volumes:
      - postgres-data:/var/lib/postgresql/data    # Named volume
      - ./init.sql:/docker-entrypoint-initdb.d/   # Bind mount
      - ./config:/etc/postgres:ro                  # Read-only

volumes:
  postgres-data:                    # Named volume (Docker-managed)
    driver: local
```

### Environment Variables

```yaml
services:
  api:
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - SPRING_PROFILES_ACTIVE=docker
    # OR from file:
    env_file:
      - .env
      - .env.local
```

### depends_on

```yaml
services:
  api:
    depends_on:
      postgres:
        condition: service_healthy    # Wait for health check
      redis:
        condition: service_started    # Just wait for start

# Without condition: only controls startup ORDER
# With service_healthy: waits until healthcheck passes
```

### Health Checks

```yaml
services:
  postgres:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  api:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 3s
      retries: 3
```

### Scaling

```bash
# Scale a service
docker compose up -d --scale api=3

# In compose.yaml:
services:
  api:
    deploy:
      replicas: 3
```

### Profiles

```yaml
services:
  api:
    profiles: []              # Always starts (no profile)
  
  debug-tools:
    image: nicolaka/netshoot
    profiles: ["debug"]       # Only starts with --profile debug
  
  monitoring:
    image: prometheus
    profiles: ["monitoring"]  # Only with --profile monitoring

# docker compose up                        → only api
# docker compose --profile debug up        → api + debug-tools
# docker compose --profile monitoring up   → api + monitoring
```

### Build

```yaml
services:
  api:
    build:
      context: ./order-service
      dockerfile: Dockerfile
      args:
        JAVA_VERSION: "21"
      target: production        # Multi-stage build target
      cache_from:
        - registry/order-service:latest
```

### docker compose up

```bash
docker compose up              # Start all services (foreground)
docker compose up -d           # Detached (background)
docker compose up --build      # Rebuild images before starting
docker compose up api          # Start specific service + dependencies
docker compose up --scale api=3  # Scale during startup
```

### docker compose down

```bash
docker compose down            # Stop and remove containers + networks
docker compose down -v         # Also remove volumes (DATA LOSS!)
docker compose down --rmi all  # Also remove images
```

### docker compose logs

```bash
docker compose logs            # All service logs
docker compose logs api        # Specific service
docker compose logs -f         # Follow (stream)
docker compose logs --tail=50  # Last 50 lines
```

### docker compose exec

```bash
docker compose exec api sh                   # Shell into running container
docker compose exec postgres psql -U admin   # Run command
docker compose exec api curl localhost:8080   # Test connectivity
```

---

## Code

### Complete Development Environment:

```yaml
# compose.yaml - Full microservices development stack
services:
  order-service:
    build:
      context: ./order-service
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
      - "5005:5005"              # Debug port
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/orders
      - SPRING_DATASOURCE_USERNAME=admin
      - SPRING_DATASOURCE_PASSWORD=secret
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - app-network

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
      timeout: 5s
      retries: 5
    networks:
      - app-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    networks:
      - app-network

volumes:
  postgres-data:

networks:
  app-network:
    driver: bridge
```

---

## Interview Questions

### Q1: What is Docker Compose and why use it?

**A:** Docker Compose defines multi-container applications in a YAML file. Instead of managing multiple `docker run` commands, you describe the entire stack (services, networks, volumes, dependencies) declaratively and manage it with `docker compose up/down`. Essential for development environments, testing, and simple deployments.

### Q2: How does Docker Compose handle networking?

**A:** Compose automatically creates a bridge network for all services in the file. Services communicate using their service name as DNS hostname (e.g., `postgres:5432`). You can define custom networks for isolation — services on different networks can't communicate. No port publishing needed for inter-service communication.

### Q3: What is the difference between depends_on and healthcheck?

**A:** `depends_on` alone only controls startup order (waits for container to start, not for app to be ready). Combined with `condition: service_healthy`, it waits until the dependency's healthcheck passes. This prevents your app from starting before the database is actually accepting connections.

### Q4: What is the difference between Docker Compose and Kubernetes?

**A:**
- **Compose:** Single-host, development/testing. Simple YAML. No auto-healing, no auto-scaling, no rolling updates. Great for local dev.
- **Kubernetes:** Multi-node production orchestration. Auto-scaling, self-healing, rolling updates, service discovery, RBAC. Complex but production-ready.

---

## Best Practices

1. **Use depends_on with healthcheck** — ensure dependencies are ready
2. **Use named volumes** — persist data across compose down/up
3. **Separate networks** — isolate frontend/backend/database tiers
4. **Use .env files** — externalize configuration
5. **Use profiles** — optional services (monitoring, debug tools)
6. **Pin image versions** — don't use `:latest`
7. **Use build target** for multi-stage builds
8. **Never use compose in production** — use Kubernetes

---

## Related Topics

- [10. Docker Networking](./10-docker-networking.md)
- [11. Docker Volumes](./11-docker-volumes.md)
- [13. Environment Configuration](./13-environment-configuration.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
