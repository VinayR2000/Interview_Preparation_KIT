# 13. Docker Security ⭐⭐⭐

---

## Theory

**Docker Security** encompasses practices to protect containers, images, the Docker daemon, and the host system from threats. Containers share the host kernel, making security critical.

### Security Threat Model

```
Attack Surfaces in Docker:
  1. Container escape → access host from container
  2. Malicious images → supply chain attacks
  3. Daemon exposure → unauthorized control
  4. Network attacks → inter-container lateral movement
  5. Data leakage → secrets in images/logs
  6. Resource abuse → DoS via unbound containers

Security is layered:
  Image → Build → Runtime → Network → Host → Orchestration
```

### Linux Security Primitives (How Containers Isolate)

```
┌─────────────────────────────────────────────────────────┐
│              CONTAINER ISOLATION MECHANISMS               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Namespaces (WHAT a container can see):                  │
│    PID  — own process tree (PID 1 inside)               │
│    NET  — own network stack (interfaces, IPs)           │
│    MNT  — own filesystem mount points                   │
│    UTS  — own hostname                                   │
│    IPC  — own shared memory, semaphores                 │
│    USER — own user/group ID mappings                    │
│                                                          │
│  Cgroups (HOW MUCH a container can use):                │
│    CPU    — CPU shares, quotas                          │
│    Memory — hard/soft limits                            │
│    I/O    — disk bandwidth limits                       │
│    PIDs   — max number of processes                     │
│                                                          │
│  Capabilities (WHAT a container can do):                │
│    Drop all Linux capabilities except what's needed     │
│    Default: ~14 capabilities enabled                    │
│    Best: drop ALL, add only required                    │
│                                                          │
│  Seccomp (WHICH syscalls allowed):                      │
│    Filter system calls                                   │
│    Default profile blocks ~44 dangerous syscalls        │
│                                                          │
│  AppArmor/SELinux (mandatory access control):           │
│    Restrict file access, network, capabilities          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Running as Non-Root

```dockerfile
# BAD — container runs as root (default)
FROM openjdk:21-slim
COPY app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

# GOOD — non-root user
FROM openjdk:21-slim
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
COPY --chown=appuser:appgroup app.jar /app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
# Verify non-root
docker exec container-name whoami
# appuser

# Run as specific user (override)
docker run --user 1000:1000 myimage

# Check if running as root
docker inspect --format='{{.Config.User}}' container-name
```

### Read-Only Filesystem

```bash
# Make container filesystem read-only
docker run --read-only myapp

# Allow specific writable directories
docker run --read-only \
  --tmpfs /tmp \
  --tmpfs /var/run \
  -v logs:/var/log \
  myapp
```

```yaml
# Docker Compose
services:
  api:
    image: myapp
    read_only: true
    tmpfs:
      - /tmp
      - /var/run
```

### Capabilities

```bash
# Drop all capabilities and add only needed
docker run --cap-drop ALL --cap-add NET_BIND_SERVICE myapp

# Common capabilities:
#   NET_BIND_SERVICE  — bind to ports < 1024
#   CHOWN             — change file ownership
#   SETUID/SETGID     — set user/group ID
#   SYS_TIME          — set system clock
#   NET_RAW           — raw sockets (ping)

# Dangerous — never add unless absolutely needed:
#   SYS_ADMIN    — mount, namespace ops (near-root)
#   NET_ADMIN    — network config changes
#   SYS_PTRACE   — debug other processes
```

### No New Privileges

```bash
# Prevent privilege escalation inside container
docker run --security-opt no-new-privileges myapp
```

```yaml
# Docker Compose
services:
  api:
    security_opt:
      - no-new-privileges:true
```

### Resource Limits (Prevent DoS)

```bash
# Memory limit
docker run -m 512m --memory-swap 512m myapp

# CPU limit
docker run --cpus="1.5" myapp

# PID limit (prevent fork bombs)
docker run --pids-limit 100 myapp

# Ulimits
docker run --ulimit nofile=1024:2048 myapp
```

```yaml
# Docker Compose
services:
  api:
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: "1.5"
        reservations:
          memory: 256M
          cpus: "0.5"
    pids_limit: 100
```

### Image Security

```bash
# Scan images for vulnerabilities
docker scout cves myimage:1.0

# Use minimal base images
FROM eclipse-temurin:21-jre-alpine    # Alpine (5MB base)
FROM gcr.io/distroless/java21        # Distroless (no shell!)

# Pin digests for reproducibility
FROM openjdk@sha256:abc123...

# Multi-stage build — no build tools in production image
FROM maven:3.9-eclipse-temurin-21 AS build
COPY . .
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /target/app.jar /app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Secrets Management

```bash
# NEVER do this — secrets baked in image
ENV DB_PASSWORD=secret123             # Visible in docker inspect!
COPY credentials.json /app/           # Stays in image layers!

# CORRECT approaches:
# 1. Runtime environment variables (not in Dockerfile)
docker run -e DB_PASSWORD=secret myapp

# 2. Docker secrets (Swarm mode)
docker secret create db_pass ./password.txt
docker service create --secret db_pass myapp

# 3. Mount secrets at runtime
docker run -v /secrets/db_pass:/run/secrets/db_pass:ro myapp

# 4. External secret managers (Vault, AWS Secrets Manager)
```

```yaml
# Docker Compose secrets
services:
  api:
    secrets:
      - db_password
    environment:
      - DB_PASSWORD_FILE=/run/secrets/db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt    # Development
    # external: true                   # Production (pre-created)
```

### Docker Daemon Security

```bash
# Never expose Docker daemon over TCP without TLS
# BAD:
dockerd -H tcp://0.0.0.0:2375        # Unencrypted! Full host access!

# GOOD: TLS mutual authentication
dockerd --tlsverify \
  --tlscacert=ca.pem \
  --tlscert=server-cert.pem \
  --tlskey=server-key.pem \
  -H tcp://0.0.0.0:2376

# Restrict socket access
# /var/run/docker.sock access = root access to host
# Never mount docker.sock into containers unless absolutely required
```

### Network Security

```yaml
# Isolate networks — services can only reach what they need
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
    internal: true    # No external internet access!
```

```bash
# Disable inter-container communication (default bridge)
dockerd --icc=false

# Use internal networks for databases
docker network create --internal db-network
```

### Image Signing and Trust

```bash
# Docker Content Trust — only pull signed images
export DOCKER_CONTENT_TRUST=1
docker pull myregistry/myimage:1.0    # Fails if unsigned

# Sign images
docker trust sign myregistry/myimage:1.0

# Verify image
docker trust inspect myregistry/myimage:1.0
```

---

## Code

### Production-Hardened Dockerfile:

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ src/
RUN mvn package -DskipTests

# Production stage — HARDENED
FROM eclipse-temurin:21-jre-alpine

# Security: create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Security: remove unnecessary packages
RUN apk --no-cache upgrade && \
    rm -rf /var/cache/apk/*

WORKDIR /app

# Security: copy with correct ownership
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Security: non-root user
USER appuser

# Security: expose only needed port
EXPOSE 8080

# Security: no shell needed for distroless, use exec form
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

### Hardened Docker Compose:

```yaml
services:
  api:
    image: myapp:1.0
    read_only: true
    tmpfs:
      - /tmp
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: "1.0"
    pids_limit: 100
    networks:
      - backend
    healthcheck:
      test: ["CMD", "java", "-cp", "app.jar", "HealthCheck"]
      interval: 30s
      timeout: 5s
      retries: 3

  postgres:
    image: postgres:15-alpine
    read_only: true
    tmpfs:
      - /tmp
      - /run/postgresql
    volumes:
      - postgres-data:/var/lib/postgresql/data
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    cap_add:
      - CHOWN
      - SETUID
      - SETGID
      - FOWNER
      - DAC_READ_SEARCH
    deploy:
      resources:
        limits:
          memory: 1G
    networks:
      - backend

networks:
  backend:
    driver: bridge
    internal: true

volumes:
  postgres-data:
```

---

## Interview Questions

### Q1: How do you secure a Docker container?

**A:** Layer defense:
1. **Non-root user** — never run as root
2. **Read-only filesystem** — prevent writes except to tmpfs/volumes
3. **Drop capabilities** — `--cap-drop ALL`, add only needed
4. **No-new-privileges** — prevent privilege escalation
5. **Resource limits** — memory, CPU, PIDs (prevent DoS)
6. **Minimal image** — Alpine/distroless (less attack surface)
7. **Image scanning** — detect CVEs before deployment
8. **Network isolation** — internal networks, restrict communication

### Q2: Why should containers not run as root?

**A:** If a container runs as root and an attacker exploits a vulnerability (container escape), they become root on the host. With a non-root user, even if they escape, they're limited to an unprivileged user. Defense in depth — reduce blast radius of compromise.

### Q3: How do you manage secrets in Docker?

**A:** Never bake secrets in images (ENV, COPY). Options:
1. **Runtime environment variables** — `docker run -e SECRET=value` (visible in inspect)
2. **Docker secrets** — encrypted at rest, mounted as files (Swarm only)
3. **Volume-mounted files** — mount secrets from host at runtime
4. **External managers** — HashiCorp Vault, AWS Secrets Manager (best for production)

### Q4: What is Docker Content Trust?

**A:** Docker Content Trust (DCT) uses digital signatures to verify image integrity and publisher. When enabled (`DOCKER_CONTENT_TRUST=1`), Docker only pulls/runs signed images. Prevents pulling tampered or unauthorized images. Based on The Update Framework (TUF).

### Q5: What is the principle of least privilege in Docker?

**A:** Give containers only the minimum permissions needed:
- Drop ALL capabilities, add only required
- Non-root user
- Read-only filesystem
- Internal networks (no external access for DB)
- Resource limits
- No Docker socket mounting
- Minimal base image (no curl, wget, shell if not needed)

---

## Common Mistakes

| Mistake | Risk | Fix |
|---------|------|-----|
| Running as root | Container escape = host root | Use `USER nonroot` |
| Secrets in ENV/COPY | Visible in image layers | Use runtime injection or secrets |
| Using `:latest` tag | Unknown, possibly vulnerable | Pin versions + digests |
| No resource limits | DoS, resource starvation | Set memory/CPU/PID limits |
| Mounting docker.sock | Full host access from container | Avoid, or use read-only proxy |
| `--privileged` flag | Disables ALL security | Never use in production |
| Fat images (ubuntu) | Large attack surface | Use alpine or distroless |
| No vulnerability scanning | Unknown CVEs deployed | Scan in CI/CD pipeline |

---

## Best Practices

1. **Non-root by default** — every Dockerfile should have `USER`
2. **Minimal base images** — distroless > alpine > slim > full
3. **Scan images in CI/CD** — Trivy, Snyk, Docker Scout
4. **Read-only filesystem** — prevent runtime modification
5. **Drop all capabilities** — add back only what's needed
6. **No secrets in images** — use external secret management
7. **Internal networks** — databases should never face internet
8. **Pin image versions** — reproducible + auditable
9. **Enable Content Trust** — verify image signatures
10. **Update base images regularly** — patch known vulnerabilities

---

## Related Topics

- [01. Docker Fundamentals](./01-docker-fundamentals.md)
- [06. Dockerfile](./06-dockerfile.md)
- [10. Docker Networking](./10-docker-networking.md)
- [14. Docker Multi-Stage Builds](./14-docker-multi-stage-builds.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
