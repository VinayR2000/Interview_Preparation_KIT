# 25. Docker Networking Deep Dive ⭐⭐

---

## Theory

**Docker networking deep dive** covers the internals of how containers communicate — virtual ethernet pairs, Linux bridges, iptables rules, DNS resolution, and multi-host overlay networking.

### Network Driver Types

```
┌───────────────────────────────────────────────────────────────┐
│               DOCKER NETWORK DRIVERS                           │
├───────────────────────────────────────────────────────────────┤
│                                                                │
│  bridge (default):                                            │
│    - Containers on same host communicate via Linux bridge     │
│    - Each container gets own IP on bridge subnet              │
│    - NAT for external access (iptables MASQUERADE)            │
│    - DNS resolution by container name                         │
│    - Default for single-host deployments                      │
│                                                                │
│  host:                                                         │
│    - Container shares host network namespace                  │
│    - No network isolation (container sees host's eth0)        │
│    - No port mapping needed (app binds directly to host port) │
│    - Fastest performance (no NAT overhead)                    │
│    - Use: performance-critical, single instance per host      │
│                                                                │
│  none:                                                         │
│    - Container has no network (only loopback)                 │
│    - Complete network isolation                                │
│    - Use: batch jobs, security-sensitive processing           │
│                                                                │
│  overlay:                                                      │
│    - Multi-host networking (Swarm/K8s)                        │
│    - VXLAN encapsulation                                       │
│    - Containers across hosts communicate as if same network   │
│                                                                │
│  macvlan:                                                      │
│    - Container gets own MAC address on physical network       │
│    - Appears as physical device on LAN                        │
│    - Use: legacy apps expecting to be on physical network     │
│                                                                │
│  ipvlan:                                                       │
│    - Similar to macvlan but shares parent MAC                 │
│    - L2 or L3 mode                                            │
│    - Use: environments with MAC address restrictions          │
│                                                                │
└───────────────────────────────────────────────────────────────┘
```

### Bridge Network Internals

```
┌─────────────────────────────────────────────────────────────────┐
│                    BRIDGE NETWORK INTERNALS                       │
│                                                                   │
│  HOST                                                            │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                          │    │
│  │  eth0 (192.168.1.100) ← Host's physical NIC            │    │
│  │       │                                                  │    │
│  │       │ iptables NAT (MASQUERADE)                       │    │
│  │       │                                                  │    │
│  │  docker0 (172.17.0.1) ← Linux bridge (virtual switch)  │    │
│  │       │                                                  │    │
│  │  ┌────┴────────────┬────────────────┐                   │    │
│  │  │                 │                │                    │    │
│  │  │ vethXXX         │ vethYYY        │ vethZZZ          │    │
│  │  │ (host end)      │ (host end)     │ (host end)       │    │
│  │  │    │            │    │           │    │              │    │
│  │  │    │            │    │           │    │              │    │
│  │  ┌────┴────┐  ┌───┴────┐  ┌───────┴────┐              │    │
│  │  │Container│  │Container│  │Container   │              │    │
│  │  │  A      │  │  B      │  │  C         │              │    │
│  │  │eth0:    │  │eth0:    │  │eth0:       │              │    │
│  │  │.17.0.2  │  │.17.0.3  │  │.17.0.4    │              │    │
│  │  └─────────┘  └─────────┘  └────────────┘              │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                   │
│  Traffic flow (Container A → Container B):                       │
│    A's eth0 → vethXXX → docker0 bridge → vethYYY → B's eth0    │
│                                                                   │
│  Traffic flow (Container A → Internet):                          │
│    A's eth0 → vethXXX → docker0 → iptables NAT → eth0 → internet│
└─────────────────────────────────────────────────────────────────┘
```

### veth Pairs (Virtual Ethernet)

```bash
# Each container creates a veth pair:
#   One end inside container (seen as eth0)
#   Other end on host (attached to bridge)

# View veth pairs on host
ip link show type veth
# veth8a2f3c@if5: ... master docker0

# View inside container
docker exec mycontainer ip addr show eth0
# eth0@if12: inet 172.17.0.2/16

# The @if numbers link the pair:
#   Container eth0@if12 ↔ Host vethXXX@if5
```

### DNS Resolution

```
┌─────────────────────────────────────────────────────────────┐
│               DOCKER DNS RESOLUTION                          │
│                                                              │
│  Container /etc/resolv.conf:                                │
│    nameserver 127.0.0.11   ← Docker's embedded DNS server  │
│                                                              │
│  Resolution flow:                                            │
│    1. Container queries 127.0.0.11 for "postgres"           │
│    2. Docker DNS looks up container name → IP mapping        │
│    3. Returns 172.18.0.3 (postgres container's IP)          │
│    4. Container connects to 172.18.0.3:5432                 │
│                                                              │
│  DNS works ONLY on user-defined networks!                    │
│    - Default bridge (docker0): NO DNS (use --link, legacy)  │
│    - User-defined bridge: YES DNS by service/container name │
│                                                              │
│  docker compose automatically creates user-defined network  │
│  → that's why service names work as hostnames in compose    │
└─────────────────────────────────────────────────────────────┘
```

```bash
# Verify DNS from inside container
docker exec api nslookup postgres
# Server:    127.0.0.11
# Address:   127.0.0.11:53
# Name:      postgres
# Address:   172.18.0.3

# DNS aliases
docker run --network mynet --network-alias db postgres:15
# Now reachable as both container name AND "db"
```

### Port Mapping (iptables)

```bash
# docker run -p 8080:80 nginx

# This creates iptables rules:
iptables -t nat -L -n | grep 8080
# DNAT  tcp  -- 0.0.0.0/0  0.0.0.0/0  tcp dpt:8080 to:172.17.0.2:80

# Flow:
#   External request → host:8080
#     → iptables DNAT → container:80
#   Response → container → iptables SNAT → external

# Port mapping options:
-p 8080:80              # host 8080 → container 80 (all interfaces)
-p 127.0.0.1:8080:80   # only localhost
-p 8080:80/udp          # UDP
-p 8080-8090:80-90      # Range
--publish-all / -P      # Map all EXPOSE ports to random host ports
```

### Custom Bridge Networks

```bash
# Create network with custom subnet
docker network create \
  --driver bridge \
  --subnet 10.0.1.0/24 \
  --gateway 10.0.1.1 \
  --ip-range 10.0.1.128/25 \
  my-network

# Connect container to multiple networks
docker network connect my-network existing-container
docker network disconnect bridge existing-container

# Inspect network
docker network inspect my-network
```

```yaml
# Docker Compose custom networks
networks:
  frontend:
    driver: bridge
    ipam:
      config:
        - subnet: 10.0.1.0/24
          gateway: 10.0.1.1

  backend:
    driver: bridge
    internal: true          # No internet access!
    ipam:
      config:
        - subnet: 10.0.2.0/24
```

### Host Networking

```bash
# Container uses host's network namespace directly
docker run --network host nginx

# Container sees host's interfaces:
#   eth0, docker0, lo (same as host)
# No port mapping needed (or possible)
# No network isolation

# Use when:
#   - Maximum network performance needed
#   - Application needs to see all host network traffic
#   - Running monitoring tools (Prometheus node-exporter)
#   - Only one instance per host (port conflicts otherwise!)
```

### Network Isolation Patterns

```yaml
# Three-tier isolation
services:
  # PUBLIC tier — accessible from internet
  nginx:
    networks:
      - public
      - app-tier

  # APP tier — only reachable from nginx
  api:
    networks:
      - app-tier
      - data-tier

  # DATA tier — only reachable from api, NO internet
  postgres:
    networks:
      - data-tier

  redis:
    networks:
      - data-tier

networks:
  public:
    driver: bridge
  app-tier:
    driver: bridge
  data-tier:
    driver: bridge
    internal: true    # ← No external access (no internet!)

# Result:
#   Internet ↔ nginx ↔ api ↔ postgres/redis
#   Internet ✗ api (not on public network)
#   Internet ✗ postgres (internal network, no route out)
```

---

## Code

### Complete Network Debugging Setup:

```yaml
services:
  api:
    image: order-service:1.0
    networks:
      app-net:
        ipv4_address: 10.0.1.10      # Fixed IP (unusual, but useful for debugging)
    dns:
      - 8.8.8.8                       # Custom DNS
    extra_hosts:
      - "host.docker.internal:host-gateway"  # Access host from container

  # Network debugging sidecar
  netdebug:
    image: nicolaka/netshoot
    profiles: ["debug"]
    networks:
      - app-net
      - data-net
    command: sleep infinity
    # Usage: docker compose --profile debug exec netdebug bash
    #   dig postgres
    #   curl http://api:8080/health
    #   tcpdump -i eth0 port 5432
    #   iperf3 -c api (bandwidth test)

  postgres:
    image: postgres:15
    networks:
      data-net:
        aliases:
          - db
          - database     # Multiple DNS names

networks:
  app-net:
    driver: bridge
    ipam:
      config:
        - subnet: 10.0.1.0/24
  data-net:
    driver: bridge
    internal: true
```

### iptables Inspection Script:

```bash
#!/bin/bash
# inspect-docker-networking.sh

echo "=== Docker Networks ==="
docker network ls

echo ""
echo "=== Bridge Network Details ==="
docker network inspect bridge --format='{{range .Containers}}{{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}'

echo ""
echo "=== Port Mappings (NAT rules) ==="
iptables -t nat -L DOCKER -n 2>/dev/null || echo "Need root"

echo ""
echo "=== Docker Proxy Processes ==="
ps aux | grep docker-proxy

echo ""
echo "=== veth Pairs ==="
ip link show type veth

echo ""
echo "=== Bridge Info ==="
brctl show docker0 2>/dev/null || bridge link show
```

---

## Interview Questions

### Q1: How do containers communicate on the same Docker host?

**A:** Via a Linux bridge network:
1. Each container gets a veth pair (virtual cable) — one end as container's eth0, other attached to the bridge
2. Docker's bridge (docker0 or custom) acts as a virtual switch
3. Container A sends packet → veth → bridge → routes to correct veth → Container B
4. DNS resolution: Docker's embedded DNS (127.0.0.11) maps service names to IPs
5. This only works on user-defined networks (not the default bridge)

### Q2: What is the difference between bridge, host, and none network modes?

**A:**
- **bridge:** Container gets own network namespace with veth pair connected to bridge. Isolated. Port mapping via iptables NAT. Default and most common.
- **host:** Container shares host's network namespace. No isolation. No port mapping (binds directly to host ports). Best performance.
- **none:** Container has only loopback. Complete network isolation. For batch processing or security-sensitive workloads.

### Q3: Why doesn't container DNS work on the default bridge network?

**A:** The default bridge network (docker0) is a legacy network that doesn't have Docker's embedded DNS server configured. Only user-defined bridge networks get automatic DNS resolution. That's why Docker Compose always creates a custom network — so services can discover each other by name.

Fix: Always use user-defined networks (`docker network create`), which Docker Compose does automatically.

### Q4: How does port mapping (-p 8080:80) work internally?

**A:** Docker creates iptables NAT rules:
1. DNAT rule: traffic to host:8080 → redirected to container_ip:80
2. Docker-proxy process: listens on host:8080 for connections from localhost
3. Response goes through reverse SNAT back to the client

The container doesn't know about the mapping — it only binds to port 80 internally.

### Q5: How do you isolate database containers from the internet?

**A:** Use Docker's `internal: true` network option:
```yaml
networks:
  db-network:
    internal: true    # No default gateway → no internet access
```
Containers on internal networks can communicate with each other but have no route to the outside world. Combine with tiered networking where only the API tier bridges between public and internal networks.

---

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| Using default bridge | No DNS resolution | Always create custom networks |
| Using `localhost` between containers | Connection refused | Use service name as hostname |
| Exposing DB port to host | Security risk | Remove port mapping, use internal network |
| Thinking EXPOSE publishes ports | Ports not accessible | EXPOSE is docs-only; use -p to publish |
| Not using internal networks | DB accessible from internet | Set `internal: true` for data tier |
| Hardcoding container IPs | Breaks on recreate | Use DNS names (service names) |
| Port conflicts with host network | Container won't start | Check `lsof -i :<port>` first |

---

## Best Practices

1. **Always use custom networks** — enable DNS, better isolation
2. **Tier your networks** — public, application, data
3. **Internal networks for data** — no internet for databases
4. **Never expose DB ports** — unless debugging locally
5. **Use service names** — not IPs (IPs change)
6. **Network aliases** — multiple names for same service
7. **Host network sparingly** — only when performance is critical
8. **Debug with netshoot** — full networking tools in a container
9. **Document your network topology** — which services can reach what
10. **Limit published ports** — only expose what's needed externally

---

## Related Topics

- [10. Docker Networking](./10-docker-networking.md)
- [22. Docker Internals](./22-docker-internals.md)
- [13. Docker Security](./13-docker-security.md)
- [12. Docker Compose](./12-docker-compose.md)
