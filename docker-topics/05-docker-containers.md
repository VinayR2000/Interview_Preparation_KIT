# 5. Docker Containers ⭐⭐⭐

---

## Theory

A **container** is a running instance of an image — an isolated process with its own filesystem, network, and process space.

### What is a Container?

```
Container = Running process + Isolated environment

Container is NOT a VM:
  - Shares host kernel
  - Process-level isolation (namespaces)
  - Resource-limited (cgroups)
  - Millisecond startup
  - Minimal overhead

Container lifecycle:
  Created → Running → Paused → Stopped → Removed
```

### Container Lifecycle

```
┌──────────┐   start   ┌─────────┐   pause   ┌────────┐
│ Created  │ ────────→ │ Running │ ────────→ │ Paused │
└──────────┘           └─────────┘           └────────┘
      │                     │                      │
      │                     │ stop/kill        unpause
      │                     ▼                      │
      │               ┌──────────┐                │
      └───────────→   │ Stopped  │ ←──────────────┘
                      └──────────┘
                           │
                      remove│
                           ▼
                      ┌──────────┐
                      │ Removed  │
                      └──────────┘
```

### Create

```bash
# Create container without starting
docker create --name my-app -p 8080:8080 my-app:v1

# Creates container, assigns ID, sets up filesystem
# Does NOT start the process
```

### Start

```bash
docker start my-app         # Start created/stopped container
docker start -ai my-app     # Start and attach (interactive)
```

### Stop

```bash
docker stop my-app           # Sends SIGTERM, waits 10s, then SIGKILL
docker stop -t 30 my-app     # Wait 30s before SIGKILL
```

### Restart

```bash
docker restart my-app        # stop + start
docker restart -t 5 my-app   # 5s grace period
```

### Pause / Unpause

```bash
docker pause my-app          # Freeze container (SIGSTOP to all processes)
docker unpause my-app        # Resume container (SIGCONT)

# Paused containers still exist, use memory, but consume no CPU
```

### Kill

```bash
docker kill my-app           # Sends SIGKILL immediately (no grace period)
docker kill -s SIGTERM my-app  # Send specific signal
```

### Remove

```bash
docker rm my-app             # Remove stopped container
docker rm -f my-app          # Force remove (even if running)
docker container prune        # Remove all stopped containers
```

### Container ID

```bash
docker ps
CONTAINER ID   IMAGE      STATUS    NAMES
a1b2c3d4e5f6   nginx:1.25 Up 5m     web-server

# Full ID: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6 (64 chars)
# Short ID: a1b2c3d4e5f6 (12 chars, usually unique)
```

### Container Name

```bash
docker run --name order-service my-app:v1

# If no name: Docker assigns random name (e.g., "quirky_einstein")
# Names must be unique (can't have two containers with same name)
# Used for DNS in custom networks
```

### Logs

```bash
docker logs my-app               # All logs
docker logs my-app --tail 100    # Last 100 lines
docker logs my-app -f            # Follow (stream)
docker logs my-app --since 1h    # Last hour
docker logs my-app --timestamps  # Show timestamps
```

### Inspect

```bash
docker inspect my-app            # Full JSON details
docker inspect --format='{{.State.Status}}' my-app
docker inspect --format='{{.NetworkSettings.IPAddress}}' my-app
docker inspect --format='{{.Config.Env}}' my-app

# Shows: state, network, mounts, env, config, etc.
```

### Exec

```bash
docker exec my-app ls /app              # Run command
docker exec -it my-app /bin/sh          # Interactive shell
docker exec -it my-app bash             # Bash shell
docker exec -u root my-app cat /etc/passwd  # Run as root
docker exec -e VAR=value my-app env     # With env var
```

---

## Code

### Common `docker run` patterns:

```bash
# Basic
docker run nginx:1.25

# Detached (background)
docker run -d --name web nginx:1.25

# Interactive
docker run -it ubuntu:22.04 bash

# Port mapping
docker run -d -p 8080:80 nginx:1.25

# Environment variables
docker run -d -e DB_HOST=postgres -e DB_PORT=5432 my-app:v1

# Volume mount
docker run -d -v /host/data:/container/data my-app:v1

# Named volume
docker run -d -v app-data:/app/data my-app:v1

# Resource limits
docker run -d --memory=512m --cpus=1.0 my-app:v1

# Remove after exit
docker run --rm my-app:v1 ./run-migration.sh

# Network
docker run -d --network my-network --name order-service my-app:v1

# Full production-like
docker run -d \
  --name order-service \
  --restart unless-stopped \
  --memory 1g --cpus 2 \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e DB_PASSWORD_FILE=/run/secrets/db_pass \
  -v order-data:/app/data \
  --health-cmd="curl -f http://localhost:8080/health || exit 1" \
  --health-interval=10s \
  my-app:2.1.0
```

---

## Interview Questions

### Q1: What is the difference between `docker stop` and `docker kill`?

**A:**
- **docker stop:** Sends SIGTERM (graceful shutdown signal), waits 10s (configurable with `-t`), then sends SIGKILL if still running. Allows cleanup.
- **docker kill:** Sends SIGKILL immediately. No grace period, no cleanup. Process is forcefully terminated.

Always prefer `stop` for graceful shutdown (close connections, finish requests).

### Q2: What is the difference between `docker run` and `docker create` + `docker start`?

**A:** `docker run` = `docker create` + `docker start` in one command. `create` sets up the container (filesystem, networking, config) without starting the process. `start` begins execution. Separating them is useful when you need to inspect the container before starting or attach to it.

### Q3: What happens to container data when the container is removed?

**A:** All data in the container's writable layer is lost. This includes any files created/modified during runtime. To persist data:
- Use **volumes** (`-v my-vol:/data`) — managed by Docker, survives container removal
- Use **bind mounts** (`-v /host/path:/container/path`) — maps to host filesystem

### Q4: How do you troubleshoot a container that keeps restarting?

**A:**
1. `docker logs <container> --tail 50` — check error messages
2. `docker inspect <container>` — check State.ExitCode (137=OOM, 1=app error)
3. `docker events` — see container lifecycle events
4. Create without auto-restart: `docker run --restart=no` then check logs
5. Override entrypoint: `docker run -it --entrypoint sh my-app:v1` to inspect filesystem

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Not naming containers | Hard to manage | Always use `--name` |
| Using `docker kill` as default | No graceful shutdown | Use `docker stop` |
| Not using `--rm` for one-off | Stopped containers accumulate | Use `--rm` for temporary containers |
| Storing data in container | Data lost on remove | Use volumes |
| Running without resource limits | Container can consume all resources | Set `--memory` and `--cpus` |

---

## Best Practices

1. **Always name containers** — `--name` for clarity
2. **Use `--rm` for one-off tasks** — auto-cleanup
3. **Set restart policies** — `--restart unless-stopped` for production
4. **Limit resources** — `--memory` and `--cpus`
5. **Use health checks** — `--health-cmd`
6. **Don't store data in containers** — use volumes
7. **Use `docker stop`** not `kill` — allow graceful shutdown
8. **Clean up regularly** — `docker container prune`

---

## Related Topics

- [04. Docker Images](./04-docker-images.md)
- [11. Docker Volumes](./11-docker-volumes.md)
- [10. Docker Networking](./10-docker-networking.md)
- [21. Docker Process Model](./21-docker-process-model.md)
