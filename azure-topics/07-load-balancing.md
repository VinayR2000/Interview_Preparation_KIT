# Azure Load Balancing

## Theory

### Azure Load Balancing Options

| Service | Layer | Scope | Use Case |
|---------|-------|-------|----------|
| Azure Load Balancer | Layer 4 (TCP/UDP) | Regional | VM/VMSS traffic distribution |
| Application Gateway | Layer 7 (HTTP/HTTPS) | Regional | Web app routing, WAF, TLS |
| Azure Front Door | Layer 7 (HTTP/HTTPS) | Global | Global load balancing, CDN, WAF |
| Traffic Manager | DNS-based | Global | DNS-level routing, failover |

### AWS Comparison

| Azure | AWS |
|-------|-----|
| Azure Load Balancer | Network Load Balancer (NLB) |
| Application Gateway | Application Load Balancer (ALB) |
| Azure Front Door | CloudFront + Global Accelerator |
| Traffic Manager | Route 53 (routing policies) |

---

## Azure Load Balancer (Layer 4) ⭐⭐⭐

### Architecture

```
Internet / Internal Traffic
    │
    ▼
Azure Load Balancer
├── Frontend IP: 20.x.x.x (public) or 10.0.1.4 (internal)
├── Health Probe: TCP 8080 every 15s
├── Load Balancing Rule: Port 80 → Backend Pool Port 8080
│
├── Backend Pool:
│   ├── VM-1 (10.0.2.4:8080) — Healthy ✓
│   ├── VM-2 (10.0.2.5:8080) — Healthy ✓
│   └── VM-3 (10.0.2.6:8080) — Unhealthy ✗ (no traffic)
│
└── Distribution: Hash-based (source IP, port, protocol)
```

### Types

| Type | Frontend IP | Use Case |
|------|-------------|----------|
| Public | Public IP address | Internet-facing workloads |
| Internal | Private IP from VNet | Internal service-to-service, database tier |

### Components

| Component | Description |
|-----------|-------------|
| Frontend IP | Entry point (public or private IP) |
| Backend Pool | Target VMs/VMSS instances |
| Health Probe | Checks backend health (TCP, HTTP, HTTPS) |
| Load Balancing Rule | Maps frontend IP:port to backend pool:port |
| Inbound NAT Rule | Port forwarding to specific VM |
| Outbound Rule | SNAT for outbound connections |

### Health Probes ⭐⭐⭐

```
Health Probe Configuration:
├── Protocol: HTTP
├── Path: /actuator/health
├── Port: 8080
├── Interval: 15 seconds
├── Unhealthy threshold: 2 consecutive failures
│
└── If probe fails:
    ├── VM marked unhealthy
    ├── No new connections routed to it
    └── Existing connections may drain
```

### SKUs

| SKU | Features |
|-----|----------|
| Basic | Free, limited features, no SLA, no AZ support |
| Standard | AZ support, HA ports, outbound rules, SLA 99.99% |

**Always use Standard SKU for production.**

---

## Application Gateway (Layer 7) ⭐⭐⭐

### What is it?
A web traffic load balancer that can route based on URL path, hostname, headers. Includes WAF (Web Application Firewall) capability.

### Architecture

```
Internet
    │
    ▼
Application Gateway (Layer 7)
├── Frontend IP: Public IP (20.x.x.x)
├── Listener: HTTPS on port 443
│   └── TLS Certificate (from Key Vault)
├── WAF: OWASP rules enabled
│
├── Routing Rules:
│   ├── /api/orders/* → Backend Pool: order-service
│   ├── /api/users/*  → Backend Pool: user-service
│   ├── /api/products/* → Backend Pool: product-service
│   └── /* → Backend Pool: frontend-app
│
├── Backend Pools:
│   ├── order-service: [VM1, VM2, VM3]
│   ├── user-service: [VM4, VM5]
│   ├── product-service: [VM6, VM7]
│   └── frontend-app: [VM8, VM9]
│
└── Health Probes:
    ├── order-service: GET /actuator/health
    ├── user-service: GET /actuator/health
    └── product-service: GET /actuator/health
```

### Routing Types

#### URL Path-based Routing ⭐⭐⭐
```
https://api.contoso.com/api/orders    → order-service backend
https://api.contoso.com/api/users     → user-service backend
https://api.contoso.com/api/products  → product-service backend
https://api.contoso.com/*             → default backend
```

#### Host-based (Multi-site) Routing
```
https://api.contoso.com     → API backend pool
https://admin.contoso.com   → Admin backend pool
https://www.contoso.com     → Frontend backend pool
```

### TLS Termination ⭐⭐⭐

```
Client ──HTTPS──> Application Gateway ──HTTP──> Backend VMs
                        │
                        └── TLS terminated here
                            Certificate managed centrally
                            Backend doesn't need certificates
```

Benefits:
- Centralized certificate management (from Key Vault)
- Offloads TLS processing from backend
- Single place to renew certificates
- Backend VMs only handle HTTP

### TLS Re-encryption (End-to-End TLS)
```
Client ──HTTPS──> Application Gateway ──HTTPS──> Backend VMs
                        │                           │
                        └── Decrypts, inspects,     └── Backend also
                            re-encrypts                 has certificate
```

### WAF (Web Application Firewall) ⭐⭐⭐
- Protects against OWASP Top 10 (SQL injection, XSS, etc.)
- Custom rules (IP blocking, rate limiting, geo-filtering)
- Detection mode (log only) or Prevention mode (block)
- Managed rule sets (updated by Microsoft)

```
WAF Modes:
├── Detection: Log threats but don't block
└── Prevention: Block threats + log

Custom Rules:
├── Block IPs from specific countries
├── Rate limit: Max 100 requests/minute per IP
├── Block requests with specific patterns
└── Allow-list for known good IPs
```

### Application Gateway SKUs

| SKU | Features |
|-----|----------|
| Standard_v2 | Layer 7 routing, autoscale, zone redundancy |
| WAF_v2 | Standard_v2 + WAF capabilities |

---

## Azure Load Balancer vs Application Gateway ⭐⭐⭐

| Feature | Azure Load Balancer | Application Gateway |
|---------|--------------------|--------------------|
| Layer | Layer 4 (TCP/UDP) | Layer 7 (HTTP/HTTPS) |
| Routing | IP + Port | URL path, hostname, headers |
| TLS termination | No | Yes |
| WAF | No | Yes (WAF_v2 SKU) |
| URL rewriting | No | Yes |
| Cookie affinity | No | Yes |
| Health probes | TCP/HTTP | HTTP/HTTPS with custom paths |
| Use case | Non-HTTP, raw TCP/UDP, internal | Web apps, APIs, microservices |
| Backend types | VMs, VMSS | VMs, VMSS, App Service, AKS, IPs |

### When to Use Which

```
Decision tree:
├── Non-HTTP traffic (TCP/UDP)?
│   └── Azure Load Balancer
│
├── HTTP/HTTPS traffic?
│   ├── Need URL-based routing?
│   │   └── Application Gateway
│   ├── Need WAF?
│   │   └── Application Gateway (WAF_v2)
│   ├── Need TLS termination?
│   │   └── Application Gateway
│   ├── Simple round-robin for HTTP?
│   │   └── Either works, but App Gateway preferred for HTTP
│   └── Internal service-to-service (non-HTTP)?
│       └── Internal Load Balancer
│
└── Global load balancing?
    └── Azure Front Door
```

---

## Common Architecture Pattern ⭐⭐⭐

```
Internet
    │
    ▼
Azure Front Door (Global LB + CDN + WAF)
    │
    ├──── Region: East US ────────────────────────────────┐
    │                                                      │
    │   Application Gateway (Regional L7 + WAF)           │
    │       │                                              │
    │       ├── /api/* → Internal Load Balancer            │
    │       │               ├── AKS Pod 1                  │
    │       │               ├── AKS Pod 2                  │
    │       │               └── AKS Pod 3                  │
    │       │                                              │
    │       └── /* → App Service (Frontend)                │
    │                                                      │
    └──── Region: West Europe ────────────────────────────┘
        │
        └── (Same architecture, DR region)
```

---

## Interview Questions

### Q: Azure Load Balancer vs Application Gateway — when to use which?
**A:**
- **Azure Load Balancer (L4)**: Use for non-HTTP protocols (TCP/UDP), internal service-to-service load balancing, or when you don't need URL-based routing. It's simpler and handles raw network traffic.
- **Application Gateway (L7)**: Use for HTTP/HTTPS workloads where you need URL-based routing, host-based routing, TLS termination, cookie affinity, or WAF. It understands HTTP and can make intelligent routing decisions.

For microservices behind an API layer → Application Gateway.
For database tier or internal TCP services → Internal Load Balancer.

### Q: How does TLS termination work at Application Gateway and why is it beneficial?
**A:** The Application Gateway decrypts HTTPS traffic at the gateway level and forwards HTTP (or re-encrypted HTTPS) to backends.

Benefits:
1. **Centralized certificate management**: One certificate at the gateway instead of on every backend
2. **Simplified backend**: Backend services only handle HTTP
3. **Performance**: SSL offloading from backend servers
4. **Certificate rotation**: Update in one place (Key Vault integration)
5. **Inspection**: WAF can inspect decrypted traffic for threats

### Q: How does WAF protect your applications?
**A:** WAF on Application Gateway inspects HTTP requests and blocks malicious traffic:
- SQL injection attempts
- Cross-site scripting (XSS)
- Protocol violations
- Bots and scanners
- Custom rules (rate limiting, geo-blocking, IP allowlisting)

Two modes: Detection (log only) and Prevention (actively block). Uses OWASP managed rule sets that Microsoft updates regularly.

### Q: Explain a multi-tier load balancing architecture.
**A:** For production microservices:
1. **Front Door** (Global): Routes users to nearest region, provides global CDN and WAF
2. **Application Gateway** (Regional): TLS termination, URL routing to different service pools, WAF for OWASP protection
3. **Internal Load Balancer** (Internal): Distributes traffic between AKS pods or VMs within the VNet, no public exposure
4. **Kubernetes Service** (Pod-level): kube-proxy routes to individual pods

Each layer handles a specific concern: global routing → regional HTTP routing → internal service distribution.
