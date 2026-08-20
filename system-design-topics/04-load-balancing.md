# Load Balancing

## Why Load Balancer?

### Theory
- Distributes incoming network traffic across multiple servers
- Prevents any single server from becoming a bottleneck
- Increases availability, reliability, and throughput
- Provides single point of contact for clients

### Diagram
```
                    ┌──────────────┐
                    │   Client     │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │    Load      │
                    │   Balancer   │
                    └──┬───┬───┬──┘
                       │   │   │
              ┌────────┘   │   └────────┐
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Server 1 │ │ Server 2 │ │ Server 3 │
        └──────────┘ └──────────┘ └──────────┘
```

### Benefits
- **High Availability**: If one server fails, traffic routes to others
- **Scalability**: Add more servers to handle increased load
- **Performance**: Distribute load evenly for faster response
- **Flexibility**: Maintain/update servers without downtime
- **Security**: Hide backend servers, DDoS mitigation

---

## L4 vs L7 Load Balancing

### Layer 4 (Transport Layer)
- Operates on TCP/UDP level
- Routes based on: IP address, port number
- Cannot inspect packet content
- Faster (less processing)
- Use case: Simple routing, TCP proxying

### Layer 7 (Application Layer)
- Operates on HTTP/HTTPS level
- Routes based on: URL path, headers, cookies, body content
- Can inspect and modify requests
- More intelligent routing
- Use case: API gateway, content-based routing

| Aspect | L4 | L7 |
|--------|----|----|
| OSI Layer | Transport | Application |
| Decision basis | IP, port, protocol | URL, headers, cookies, body |
| SSL termination | No (pass-through) | Yes |
| Content awareness | No | Yes |
| Performance | Faster (less processing) | Slightly slower |
| Examples | AWS NLB, HAProxy (TCP mode) | AWS ALB, Nginx, HAProxy (HTTP mode) |
| Use cases | Database LB, TCP services | HTTP APIs, microservices routing |

### Example: L7 Content-Based Routing
```
/api/users/*    → User Service (3 instances)
/api/orders/*   → Order Service (5 instances)
/api/payments/* → Payment Service (2 instances)
/static/*       → CDN / Static Server
```

---

## Load Balancing Algorithms

### Round Robin
```
Request 1 → Server A
Request 2 → Server B
Request 3 → Server C
Request 4 → Server A  (cycle repeats)
```
- Simplest algorithm
- Assumes all servers have equal capacity
- Good for: Homogeneous servers, stateless services

### Weighted Round Robin
```
Server A (weight=5): Gets 5 out of every 8 requests
Server B (weight=2): Gets 2 out of every 8 requests
Server C (weight=1): Gets 1 out of every 8 requests
```
- Assign weights based on server capacity
- Higher weight = more traffic
- Good for: Heterogeneous servers

### Least Connections
```
Server A: 15 active connections → Skip
Server B: 3 active connections  → Choose this one
Server C: 12 active connections → Skip
```
- Routes to server with fewest active connections
- Good for: Long-lived connections, variable request processing time
- Accounts for current server load

### Weighted Least Connections
- Combines least connections with server weights
- Formula: connections / weight (lowest ratio gets request)

### IP Hash
```
hash(client_ip) % number_of_servers = target_server
```
- Same client always goes to same server
- Natural sticky sessions without cookies
- Problem: Uneven distribution if many clients behind same NAT

### Least Response Time
- Routes to server with fastest response time + fewest connections
- Requires active monitoring of server response times
- Best for: Minimizing client-perceived latency

### Random
- Randomly selects a server
- Simple, no state needed
- Statistically even distribution with enough requests

---

## Health Checks

### Types
| Type | How | Checks |
|------|-----|--------|
| TCP | Open TCP connection | Server is listening |
| HTTP | Send HTTP request | Application is responding |
| Custom | Run script/query | Application logic is working |

### Health Check Configuration
```
health_check:
  protocol: HTTP
  path: /health
  port: 8080
  interval: 10s        # Check every 10 seconds
  timeout: 5s          # Fail if no response in 5s
  healthy_threshold: 3  # 3 consecutive successes → healthy
  unhealthy_threshold: 2 # 2 consecutive failures → unhealthy
```

### Health Check Flow
```
LB ──── /health ────→ Server A (200 OK) ✓ Keep in pool
LB ──── /health ────→ Server B (503)    ✗ Mark unhealthy
LB ──── /health ────→ Server B (503)    ✗ Remove from pool
...
LB ──── /health ────→ Server B (200 OK) ✓ (1/3 threshold)
LB ──── /health ────→ Server B (200 OK) ✓ (2/3)
LB ──── /health ────→ Server B (200 OK) ✓ (3/3) Add back to pool
```

---

## Sticky Sessions (Session Affinity)

### Theory
- Ensures requests from same client always go to same server
- Required when server maintains session state

### Implementation Methods
| Method | How |
|--------|-----|
| Cookie-based | LB inserts cookie with server ID |
| IP-based | Hash of client IP |
| URL parameter | Session ID in URL |

### Problems with Sticky Sessions
- Uneven load distribution
- Server failure loses all sessions on that server
- Harder to scale (sessions are "pinned")
- **Solution**: Externalize session state (Redis) → eliminate sticky sessions

---

## Reverse Proxy

### Theory
- Sits between clients and servers
- Clients don't know about backend servers
- Load balancer IS a type of reverse proxy

### Reverse Proxy vs Forward Proxy
| Aspect | Forward Proxy | Reverse Proxy |
|--------|--------------|---------------|
| Sits in front of | Clients | Servers |
| Hides | Client identity | Server identity |
| Use case | Corporate network, VPN | Load balancing, SSL, caching |
| Who configures | Client | Server admin |
| Example | Squid, corporate proxy | Nginx, HAProxy |

### Reverse Proxy Functions Beyond Load Balancing
- SSL/TLS termination
- Response caching
- Compression (gzip, brotli)
- Static file serving
- Request filtering/firewall
- Rate limiting

---

## Internal Working of Load Balancer

### Connection Flow
```
1. Client initiates TCP connection to LB (VIP - Virtual IP)
2. LB accepts connection
3. LB selects backend server (algorithm)
4. LB establishes connection to backend
5. LB forwards request (may modify headers)
6. Backend processes and responds
7. LB forwards response to client
```

### DNS-Based Load Balancing
- DNS returns multiple IPs for same domain
- Client picks one (usually first)
- Simple but limited control
- TTL affects failover speed
- No health checks at DNS level

### High Availability for Load Balancer Itself
```
┌─────────────────────┐
│ Virtual IP (VIP)    │
│   (floating IP)     │
└──────┬──────────────┘
       │ Heartbeat
┌──────▼──────┐  ┌─────────────┐
│  Active LB  │←→│ Standby LB  │
│  (Primary)  │  │  (Backup)   │
└─────────────┘  └─────────────┘

If Active fails → Standby takes over VIP
```

---

## Interview Questions

**Q: How do you ensure the load balancer itself doesn't become a single point of failure?**
> Deploy LB in active-passive or active-active configuration. Use a floating/virtual IP. Active-passive: standby monitors active via heartbeat, takes over if active fails (VRRP protocol). Active-active: both handle traffic, DNS returns both IPs. Cloud: managed LBs (ALB/NLB) are inherently HA.

**Q: When would you choose L4 over L7 load balancing?**
> L4 when: you need raw TCP/UDP forwarding (databases, message queues), maximum performance with minimal latency, SSL passthrough (don't want to terminate at LB), simple port-based routing.
> L7 when: you need content-based routing (URL, headers), SSL termination, request modification, WebSocket routing, API gateway functionality.

**Q: How does least-connections handle long-lived WebSocket connections?**
> Least connections naturally handles this — servers with many WebSocket connections (long-lived) will get fewer new connections. However, active WebSocket connections may have very different resource usage. Combine with weighted approach based on actual CPU/memory metrics for better distribution.

**Q: What happens to in-flight requests when a server goes down?**
> 1. New requests are immediately routed elsewhere (health check detects failure)
> 2. In-flight requests on failed server: connection reset, client gets error
> 3. With retry logic: client retries → LB routes to healthy server
> 4. With connection draining: LB stops sending new requests but lets existing ones complete before removing server

**Q: How would you handle load balancing for a global application?**
> Multi-tier approach:
> 1. DNS-based (GeoDNS/Anycast): Route to nearest region
> 2. Regional load balancer: Distribute within region
> 3. Local load balancer: Distribute within availability zone
> Use GSLB (Global Server Load Balancing) for intelligent routing based on latency, health, and capacity.

---

## Common Mistakes
- Not configuring health checks (dead servers still receive traffic)
- Using sticky sessions when stateless design is possible
- Not planning for LB failure (single point of failure)
- Using Round Robin for heterogeneous servers
- Not considering connection draining during deployments
- Ignoring the overhead of L7 inspection for high-throughput services

---

## Best Practices
- Always configure health checks with appropriate thresholds
- Use connection draining for graceful server removal
- Design services to be stateless (avoid sticky sessions)
- Monitor LB metrics: active connections, error rates, latency distribution
- Use L7 for HTTP services, L4 for TCP/UDP services
- Implement circuit breaker pattern alongside load balancing
- Consider auto-scaling integration with LB

---

## Production Considerations
- Connection draining timeout (30-60s typical)
- Health check interval vs detection speed trade-off
- SSL termination at LB vs end-to-end encryption
- Cross-zone load balancing (distribute across AZs)
- Pre-warming for predictable traffic spikes
- Monitoring: 5xx rates, connection counts, latency percentiles
- Auto-scaling triggers based on LB metrics

---

## Related Topics
- API Gateway
- Reverse Proxy (Nginx)
- Health Checks
- Service Discovery
- Auto Scaling
- CDN (Content Delivery Network)
