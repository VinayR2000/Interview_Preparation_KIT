# 11. Docker Volumes ⭐⭐⭐

---

## Theory

Docker volumes provide persistent storage for containers, allowing data to survive container restarts, removals, and image updates.

### Container Filesystem

```
Container filesystem is ephemeral:
  - Each container has its own writable layer
  - When container is removed → data is LOST
  - Container restarts preserve data (same writable layer)
  - New container from same image → fresh filesystem

Container layers:
  [Read-Only Image Layers] + [Writable Container Layer]
  
  Writes use Copy-on-Write:
  First write to a file → copied from image layer to writable layer
```

### Ephemeral Storage

```
Ephemeral = dies with the container

Default container filesystem:
  - Great for stateless apps (web servers, APIs)
  - Terrible for data that must persist (databases, uploads)
  
Problem scenarios:
  - docker rm my-db → all database data gone!
  - docker run new-version → fresh filesystem, old data lost
  - Container crash → data in writable layer preserved (until rm)
```

### Volume

```bash
# Docker-managed volumes (stored in /var/lib/docker/volumes/)
docker volume create my-data
docker run -v my-data:/app/data my-app

# Volume is managed by Docker:
#   - Stored on host filesystem
#   - Survives container lifecycle
#   - Can be shared between containers
#   - Backed up/restored independently
```

### Bind Mount

```bash
# Bind mount: Map host directory → container directory
docker run -v /host/path:/container/path my-app
docker run --mount type=bind,source=/host/data,target=/app/data my-app

# Development use case:
docker run -v $(pwd)/src:/app/src my-app
# Changes in ./src reflect immediately in container (live reload)

Bind Mount vs Volume:
  Bind Mount: YOU manage the host path
  Volume:     Docker manages the storage location
```

### tmpfs

```bash
# tmpfs: In-memory storage (RAM)
docker run --tmpfs /app/temp my-app
docker run --mount type=tmpfs,destination=/app/temp,tmpfs-size=256m my-app

# tmpfs characteristics:
#   - Stored in host RAM (not disk)
#   - Very fast (memory speed)
#   - Lost when container stops
#   - Not shared between containers
#   - Good for sensitive temp data (no disk write)
```

### Named Volume

```bash
# Named volume (preferred for persistence)
docker volume create postgres-data
docker run -v postgres-data:/var/lib/postgresql/data postgres

# Docker manages the volume:
docker volume ls
docker volume inspect postgres-data
docker volume rm postgres-data
```

### Anonymous Volume

```bash
# Anonymous volume (Docker generates random name)
docker run -v /var/lib/postgresql/data postgres

# Also created by VOLUME instruction in Dockerfile:
# VOLUME /var/lib/postgresql/data

# Anonymous volumes are hard to reference later
# Always use named volumes in production!
```

### Volume Mount (modern syntax)

```bash
# --mount syntax (more explicit, recommended)
docker run --mount type=volume,source=my-data,target=/app/data my-app
docker run --mount type=bind,source=/host/path,target=/app/data my-app
docker run --mount type=tmpfs,target=/app/temp my-app

# --mount vs -v:
#   --mount: Explicit, fails if source doesn't exist (safer)
#   -v:      Short syntax, auto-creates source if missing
```

### Backup

```bash
# Backup a volume
docker run --rm -v my-data:/source -v $(pwd):/backup alpine \
  tar czf /backup/my-data-backup.tar.gz -C /source .

# Restore a volume
docker run --rm -v my-data:/target -v $(pwd):/backup alpine \
  tar xzf /backup/my-data-backup.tar.gz -C /target
```

### Persistent Data

```
Persistence strategies:

Database (PostgreSQL, MySQL):
  docker run -v postgres-data:/var/lib/postgresql/data postgres
  → Data persists across container restarts/upgrades

File uploads:
  docker run -v uploads:/app/uploads my-app
  → User uploads survive deployments

Application logs:
  docker run -v logs:/var/log/app my-app
  → Logs available after container stops

Configuration:
  docker run -v ./config:/app/config:ro my-app
  → Read-only bind mount for config files
```

---

## Diagram

```
┌────────────── DOCKER STORAGE TYPES ──────────────────────┐
│                                                            │
│  Container                                                │
│  ┌─────────────────────────────────────────────┐         │
│  │  /app/data    → Volume (Docker-managed)     │         │
│  │  /app/src     → Bind Mount (host path)      │         │
│  │  /tmp         → tmpfs (RAM)                 │         │
│  │  /app/code    → Container layer (ephemeral) │         │
│  └─────────────────────────────────────────────┘         │
│       │               │              │                    │
│       ▼               ▼              ▼                    │
│  ┌─────────┐   ┌──────────┐   ┌─────────┐              │
│  │ Docker  │   │ Host     │   │  RAM    │              │
│  │ Volume  │   │ Directory│   │ (tmpfs) │              │
│  │/var/lib/│   │/home/user│   │         │              │
│  │docker/  │   │/project  │   │         │              │
│  │volumes/ │   │          │   │         │              │
│  └─────────┘   └──────────┘   └─────────┘              │
│  Persistent    Persistent      Ephemeral                 │
│  Docker-managed User-managed   Memory only               │
└────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between a volume and a bind mount?

**A:**
- **Volume:** Docker-managed storage in `/var/lib/docker/volumes/`. Docker handles the lifecycle. Portable, works on any host. Preferred for production data persistence.
- **Bind mount:** Maps a specific host path to container path. You manage the location. Content depends on host filesystem. Preferred for development (live code reload).

### Q2: When should you use tmpfs?

**A:** Use tmpfs when:
- Data is sensitive and shouldn't be written to disk (temporary secrets)
- Data is ephemeral (cache, temp files)
- Need maximum I/O performance (RAM speed)
- Don't need persistence (lost when container stops)

### Q3: How do you persist database data with Docker?

**A:** Use a named volume:
```bash
docker volume create postgres-data
docker run -v postgres-data:/var/lib/postgresql/data postgres:15
```
The volume survives container removal. When upgrading PostgreSQL versions, the data persists. Always back up volumes before major upgrades.

### Q4: What happens to data when a container is removed?

**A:**
- **Container writable layer:** Deleted (data lost)
- **Named volumes:** Preserved (data survives)
- **Bind mounts:** Host files unchanged (data survives)
- **tmpfs:** Lost (RAM cleared)
- **Anonymous volumes:** NOT auto-deleted (orphaned, use `docker volume prune`)

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| No volume for database | Data lost on container removal | Always use named volumes for databases |
| Anonymous volumes | Hard to manage, orphaned | Use named volumes |
| -v creates missing host dirs | Unintended empty mounts | Use --mount (fails if missing) |
| Root-owned volume files | Permission denied in container | Match container user UID with volume owner |
| Not backing up volumes | Data loss risk | Regular volume backups |

---

## Best Practices

1. **Use named volumes** for persistent data (not anonymous)
2. **Use bind mounts** for development (live reload)
3. **Use tmpfs** for sensitive temporary data
4. **Use read-only mounts** (`:ro`) for configuration
5. **Back up volumes** regularly (before upgrades!)
6. **Don't store data in container layer** — use volumes
7. **Use --mount syntax** — more explicit and safer
8. **Clean up unused volumes** — `docker volume prune`

---

## Related Topics

- [05. Docker Containers](./05-docker-containers.md)
- [12. Docker Compose](./12-docker-compose.md)
- [20. Docker Storage Internals](./20-docker-storage-internals.md)
- [10. Docker Networking](./10-docker-networking.md)
