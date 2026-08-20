# 10. Docker Networking ⭐⭐⭐

---

## Theory

Docker networking enables containers to communicate with each other, the host, and external networks through different network drivers.

### Container Networking

```
Every container gets:
  - Its own network namespace (isolated network stack)
  - Its own IP address (within Docker network)
  - Ability to communicate with other containers on same network
  - Optional port mapping to host

Docker manages: IP allocation, routing, DNS, port mapping
```

### Bridge Network (default)

```
Bridge: Default network for containers on same host

docker run my-app  → attached to default bridge network

┌────── Docker Host ──────────────────────────┐
│                                              │
│  ┌────────┐    ┌────────┐    ┌────────┐   │
│  │ App A  │    │ App B  │    │ App C  │   │
│  │172.17.│    │172.17.│    │172.17.│   │
│  │  0.2   │    │  0.3   │    │  0.4   │   │
│  └───┬────┘    └───┬────┘    └───┬────┘   │
│      │             │             │          │
│  ────┴─────────────┴─────────────┴──────   │
│         docker0 bridge (172.17.0.1)         │
│                    │                         │
│         eth0 (host network interface)        │
└────────────────────┬─────────────────────────┘
                     │
              External Network

Default bridge limitations:
  - No automatic DNS (must use --link or IP)
  - All containers on same bridge can communicate
  
Custom bridge (recommended):
  - Automatic DNS resolution (container name)
  - Better isolation
  - Configurable
```

### Host Network

```bash
docker run --network host my-app

Host network:
  - Container shares host's network namespace
  - No network isolation
  - Container uses host IP directly
  - No port mapping needed (container port = host port)
  - Better performance (no NAT overhead)
  
Use cases:
  - Performance-critical networking
  - Container needs to bind many ports
  - Network monitoring tools
  
Limitations:
  - Port conflicts (two containers can't use same port)
  - No network isolation
  - Linux only
```

### None Network

```bash
docker run --network none my-app

None network:
  - No network interface (except loopback)
  - Complete network isolation
  - Container can't communicate with anything
  
Use cases:
  - Security (processing sensitive data)
  - Batch jobs that don't need network
  - Testing isolation
```

### Overlay Network

```
Overlay: Multi-host networking (Docker Swarm / Kubernetes)

  Host 1                    Host 2
  ┌──────────────┐         ┌──────────────┐
  │ Container A  │  VXLAN  │ Container B  │
  │ (10.0.0.2)  │ ←─────→ │ (10.0.0.3)  │
  └──────────────┘  tunnel └──────────────┘

Overlay encapsulates container traffic in VXLAN:
  - Containers on different hosts appear on same network
  - Used by Docker Swarm and older Docker Compose setups
  - Kubernetes uses CNI plugins instead
```

### Custom Networks

```bash
# Create custom bridge network
docker network create my-network
docker network create --driver bridge --subnet 172.20.0.0/16 my-network

# Run container on custom network
docker run --network my-network --name app my-app
docker run --network my-network --name db postgres

# Containers can communicate by name:
# From app: curl http://db:5432  ← DNS resolution!

# Connect running container to network
docker network connect my-network existing-container

# Disconnect
docker network disconnect my-network container-name
```

### Container-to-Container Communication

```
Same custom network:
  Container A → DNS: "container-b" → IP of Container B → direct

Different networks:
  Container A (network-1) ✗ Container B (network-2)
  Unless container is connected to both networks

docker-compose automatically creates a network:
  All services in same compose file can communicate by service name
```

### DNS

```
Docker's built-in DNS (custom networks only):

Container A wants to reach Container B:
  1. A queries Docker's DNS server (127.0.0.11)
  2. DNS resolves "container-b" → 172.20.0.3
  3. A connects to 172.20.0.3

DNS names:
  - Container name: my-container
  - Service name (Compose): order-service
  - Network alias: --network-alias my-alias

Note: Default bridge does NOT have DNS resolution
      Always use custom networks!
```

### Port Mapping

```bash
# Map host port to container port
docker run -p 8080:80 nginx          # host:container
docker run -p 127.0.0.1:8080:80 nginx  # bind to localhost only
docker run -p 8080:80/tcp nginx      # TCP only
docker run -p 8080:80/udp nginx      # UDP only
docker run -P nginx                   # Map all EXPOSE ports to random host ports

# Multiple port mappings
docker run -p 8080:80 -p 8443:443 nginx
```

### EXPOSE vs -p

```dockerfile
# EXPOSE: Documentation only (doesn't publish port)
EXPOSE 8080

# -p: Actually publishes port at runtime
docker run -p 8080:8080 my-app
```

```
EXPOSE:
  - Metadata/documentation in Dockerfile
  - Does NOT make port accessible from host
  - Used by -P flag (publish all exposed ports)
  
-p (--publish):
  - Actually maps host port → container port
  - Makes container accessible from outside
  - Required for external access
```

### Network Isolation

```
Networks provide isolation:

┌── network-frontend ──────────────┐
│  [nginx]  [react-app]           │
└──────────────────┬───────────────┘
                   │ (nginx on both networks)
┌── network-backend ───────────────┐
│  [nginx]  [api-server]  [redis] │
└──────────────────────────────────┘

┌── network-database ──────────────┐
│  [api-server]  [postgres]       │
└──────────────────────────────────┘

frontend can't reach database directly!
Only services on same network can communicate.
```

---

## Interview Questions

### Q1: What are the Docker network types and when to use each?

**A:**
- **Bridge (default):** Single-host container communication. Use custom bridge for DNS + isolation.
- **Host:** Container shares host network stack. Use for performance or many ports.
- **None:** No networking. Use for isolated processing.
- **Overlay:** Multi-host communication. Use for Docker Swarm / distributed apps.

### Q2: What is the difference between the default bridge and a custom bridge network?

**A:**
- **Default bridge:** No DNS resolution (only IP), all containers can communicate, manual `--link` required.
- **Custom bridge:** Automatic DNS (resolve by container name), better isolation (only same-network containers), configurable subnet/gateway.

Always use custom networks in production.

### Q3: What is the difference between EXPOSE and -p?

**A:** `EXPOSE` is documentation in the Dockerfile — it tells users which ports the container listens on but doesn't publish anything. `-p` (or `--publish`) at runtime actually maps a host port to a container port, making it accessible from outside. `EXPOSE` without `-p` = port not reachable from host.

### Q4: How do containers communicate in Docker Compose?

**A:** Docker Compose automatically creates a custom bridge network for all services in the file. Services communicate using their service name as DNS hostname. Example: a web service can reach a database at `postgres:5432` using the service name. No port publishing needed for inter-service communication.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using default bridge | No DNS resolution | Use custom networks |
| Publishing all ports (-P) | Exposes unnecessary ports | Publish only needed ports |
| Using --link | Deprecated, brittle | Use custom networks + DNS |
| Binding to 0.0.0.0 in production | Accessible from anywhere | Bind to 127.0.0.1 or use firewall |
| Not using network isolation | All containers can talk | Separate networks per tier |

---

## Best Practices

1. **Always use custom networks** — DNS + isolation
2. **Separate networks per tier** — frontend, backend, database
3. **Don't publish unnecessary ports** — only what's externally needed
4. **Use service names for communication** — not IPs
5. **Bind to localhost** when only local access needed
6. **Use overlay** for multi-host communication
7. **Document ports with EXPOSE** in Dockerfile

---

## Related Topics

- [12. Docker Compose](./12-docker-compose.md)
- [11. Docker Volumes](./11-docker-volumes.md)
- [05. Docker Containers](./05-docker-containers.md)
- [22. Docker Internals](./22-docker-internals.md)
