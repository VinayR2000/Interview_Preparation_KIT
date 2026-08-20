# 16. Docker Troubleshooting ⭐⭐

---

## Theory

**Docker troubleshooting** involves diagnosing container failures, networking issues, performance problems, and image build errors. Mastering Docker debug commands is essential for production incident response.

### Container Lifecycle States

```
┌──────────────────────────────────────────────────────────────┐
│                  CONTAINER STATES                              │
│                                                               │
│  Created → Running → Paused → Running → Stopped → Removed   │
│     │         │                             │                 │
│     │         └─── Exited (crash/error) ────┘                │
│     │                    │                                    │
│     └── Never started    └── Exit code matters!              │
│                                                               │
│  Exit Codes:                                                  │
│    0   = Normal exit (success)                               │
│    1   = Application error                                    │
│    137 = SIGKILL (OOM killed or docker kill)                 │
│    143 = SIGTERM (graceful stop)                              │
│    139 = SIGSEGV (segmentation fault)                        │
│    126 = Permission denied (can't execute)                    │
│    127 = Command not found                                    │
└──────────────────────────────────────────────────────────────┘
```

### Essential Debug Commands

```bash
# Container status and exit code
docker ps -a                          # Show ALL containers (including stopped)
docker inspect <container>            # Full JSON details
docker inspect --format='{{.State.ExitCode}}' <container>

# Logs
docker logs <container>               # All logs
docker logs --tail 100 <container>    # Last 100 lines
docker logs -f <container>            # Stream (follow)
docker logs --since 5m <container>    # Last 5 minutes
docker logs --timestamps <container>  # With timestamps

# Execute commands in running container
docker exec -it <container> sh        # Shell access
docker exec <container> cat /etc/hosts  # Check DNS
docker exec <container> env           # Check environment variables
docker exec <container> ps aux        # Check processes

# Resource usage
docker stats                          # Live resource monitoring
docker stats --no-stream              # Snapshot (one-time)
docker top <container>                # Processes in container

# Events
docker events                         # Real-time Docker events
docker events --filter container=<id>
```

### Debugging Container Startup Failures

```bash
# Container exits immediately?
# 1. Check exit code
docker ps -a --filter "name=myapp"
#  STATUS: Exited (1) 30 seconds ago

# 2. Check logs
docker logs myapp
#  Error: Cannot connect to database

# 3. Run interactively to debug
docker run -it --entrypoint sh myapp
# Now you're inside — check files, env, connectivity

# 4. Override command to keep container alive
docker run -d --entrypoint tail myapp -- -f /dev/null
docker exec -it <container_id> sh
```

### Networking Troubleshooting

```bash
# Check container IP and network
docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' <container>

# DNS resolution inside container
docker exec <container> nslookup postgres
docker exec <container> getent hosts postgres

# Connectivity test
docker exec <container> wget -qO- http://api:8080/health
docker exec <container> nc -zv postgres 5432

# List networks and connected containers
docker network ls
docker network inspect <network>

# Common networking issues:
#   1. Services on different networks → can't communicate
#   2. Using localhost instead of service name
#   3. Port not exposed (EXPOSE in Dockerfile is documentation only)
#   4. Service not ready (need healthcheck + depends_on)
```

### OOM (Out of Memory) Debugging

```bash
# Check if OOM killed
docker inspect --format='{{.State.OOMKilled}}' <container>
# true = container was OOM killed

# Check memory usage
docker stats --no-stream <container>
# MEM USAGE / LIMIT     MEM %
# 512MiB / 512MiB       100.00%  ← hitting limit!

# Check system OOM events
dmesg | grep -i "oom\|killed"

# Fix: increase memory limit or reduce usage
docker run -m 1g myapp
# OR tune JVM: -XX:MaxRAMPercentage=65.0
```

### Volume and Permission Issues

```bash
# Permission denied on mounted volume?
# Check file ownership inside container
docker exec <container> ls -la /data

# Common fix: match container user UID with host file owner
docker run --user $(id -u):$(id -g) -v ./data:/data myapp

# Or fix ownership in Dockerfile
RUN chown -R appuser:appgroup /data

# Check volume mount
docker inspect --format='{{json .Mounts}}' <container> | jq
```

### Image Build Debugging

```bash
# Build with verbose output
docker build --no-cache --progress=plain .

# Build up to specific stage
docker build --target build .

# Check intermediate layers
docker history myimage:latest

# Dive into image layers (third-party tool)
# dive myimage:latest

# Build context too large?
# Check what's being sent:
docker build . 2>&1 | head -5
# "Sending build context to Docker daemon  500MB"
# Fix: add .dockerignore
```

### Docker Compose Troubleshooting

```bash
# Check service status
docker compose ps

# Check why a service failed
docker compose logs <service>
docker compose logs --tail 50 <service>

# Rebuild after Dockerfile changes
docker compose up --build

# Force recreate containers
docker compose up --force-recreate

# Check config validity
docker compose config

# Check dependencies
docker compose ps --format json | jq '.[] | {Name, State, Health}'
```

---

## Code

### Debugging Sidecar Container:

```yaml
# Add a debug sidecar for network troubleshooting
services:
  api:
    image: myapp:1.0
    networks:
      - backend

  # Debug container with networking tools
  debug:
    image: nicolaka/netshoot
    profiles: ["debug"]
    networks:
      - backend
    command: sleep infinity

# Usage:
# docker compose --profile debug up -d
# docker compose exec debug bash
# curl http://api:8080/health
# dig postgres
# tcpdump -i eth0 port 5432
```

### Health Check Debugging Script:

```bash
#!/bin/bash
# debug-container.sh — Quick container diagnostic

CONTAINER=$1

if [ -z "$CONTAINER" ]; then
  echo "Usage: ./debug-container.sh <container_name>"
  exit 1
fi

echo "=== Container Status ==="
docker inspect --format='
  State:    {{.State.Status}}
  Running:  {{.State.Running}}
  ExitCode: {{.State.ExitCode}}
  OOMKilled: {{.State.OOMKilled}}
  Started:  {{.State.StartedAt}}
  Finished: {{.State.FinishedAt}}
' $CONTAINER

echo ""
echo "=== Resource Usage ==="
docker stats --no-stream $CONTAINER

echo ""
echo "=== Last 20 Log Lines ==="
docker logs --tail 20 $CONTAINER

echo ""
echo "=== Network ==="
docker inspect --format='{{range $k,$v := .NetworkSettings.Networks}}Network: {{$k}} IP: {{$v.IPAddress}}{{"\n"}}{{end}}' $CONTAINER

echo ""
echo "=== Mounts ==="
docker inspect --format='{{range .Mounts}}{{.Type}}: {{.Source}} → {{.Destination}}{{"\n"}}{{end}}' $CONTAINER
```

### Common Error Patterns and Fixes:

```bash
# ─── Error: "port is already allocated" ───
# Another container or process using the port
docker ps --filter "publish=8080"    # Find who's using it
lsof -i :8080                        # Check host processes
# Fix: stop conflicting container or use different port

# ─── Error: "network not found" ───
docker compose down                  # Clean up
docker network prune                 # Remove unused networks
docker compose up                    # Recreate

# ─── Error: "no space left on device" ───
docker system df                     # Check Docker disk usage
docker system prune -a               # Remove unused everything
docker volume prune                  # Remove unused volumes
docker builder prune                 # Clear build cache

# ─── Error: "max depth exceeded" (image layers) ───
# Too many RUN commands → combine them
RUN apt-get update && apt-get install -y pkg1 pkg2 && rm -rf /var/lib/apt/lists/*

# ─── Error: Container keeps restarting ───
docker inspect --format='{{.HostConfig.RestartPolicy.Name}}' <container>
docker update --restart=no <container>   # Stop restart loop
docker logs <container>                   # Check WHY it's crashing
```

---

## Interview Questions

### Q1: A container starts and immediately exits. How do you debug?

**A:** Step-by-step:
1. `docker ps -a` — check exit code (1=app error, 137=OOM, 127=command not found)
2. `docker logs <container>` — read error messages
3. `docker run -it --entrypoint sh <image>` — get shell, check files/env manually
4. Verify environment variables, config files, and connectivity to dependencies
5. If OOM: `docker inspect --format='{{.State.OOMKilled}}'` and increase memory

### Q2: How do you troubleshoot networking between containers?

**A:** 
1. Verify both containers are on the same Docker network: `docker network inspect <network>`
2. Check DNS resolution: `docker exec <container> nslookup <service_name>`
3. Test connectivity: `docker exec <container> nc -zv <service> <port>`
4. Ensure service is ready (not just started) — use healthcheck + depends_on with condition
5. Common mistake: using `localhost` instead of service name for inter-container communication

### Q3: Container is being OOM killed. How do you diagnose and fix?

**A:** Diagnosis:
- `docker inspect --format='{{.State.OOMKilled}}'` → true
- `docker stats` → memory at 100%
- `dmesg | grep oom` → kernel OOM killer logs

Fix:
- Increase container memory limit (`-m 1g`)
- For Java: reduce `MaxRAMPercentage` to 65% (leave room for non-heap)
- Check for memory leaks (heap dumps, profiling)
- Consider if workload needs horizontal scaling

### Q4: How do you clean up Docker disk space?

**A:** 
```bash
docker system df           # See what's using space
docker system prune -a     # Remove all unused (images, containers, networks)
docker volume prune        # Remove unused volumes (DATA LOSS!)
docker builder prune       # Clear build cache
```
For prevention: use multi-stage builds (smaller images), set log rotation, periodically prune in CI/CD.

---

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| Not checking exit codes | Blind debugging | Always start with `docker ps -a` |
| Using localhost between containers | Connection refused | Use service name as hostname |
| Ignoring OOMKilled flag | Misdiagnosing crashes | Check `docker inspect` State |
| Not using `--no-cache` on build issues | Stale layers | `docker build --no-cache .` |
| Forgetting `docker compose down` | Stale networks/containers | Clean up between changes |
| No .dockerignore | Slow builds, large context | Exclude target/, node_modules/ |
| Missing health checks | Cascading failures | Add healthcheck to all services |

---

## Best Practices

1. **Always check exit codes first** — they tell you the category of failure
2. **Use structured logging** — JSON logs for easier parsing
3. **Set resource limits** — catch problems before OOM kill
4. **Health checks on everything** — databases, caches, application
5. **Debug sidecar** — netshoot container for network debugging
6. **docker system prune** regularly — prevent disk exhaustion
7. **Log rotation** — `--log-opt max-size=10m --log-opt max-file=3`
8. **Monitor with docker stats** — catch resource issues early

---

## Related Topics

- [05. Docker Containers](./05-docker-containers.md)
- [10. Docker Networking](./10-docker-networking.md)
- [12. Docker Compose](./12-docker-compose.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
