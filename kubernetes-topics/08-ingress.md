# 8. Ingress ⭐⭐⭐

---

## Theory

**Ingress** is a Kubernetes API object that manages external HTTP/HTTPS access to services within a cluster. It provides routing rules, TLS termination, and virtual hosting — consolidating multiple services behind a single load balancer.

### What is Ingress?

```
Without Ingress:
  Each service needs its own LoadBalancer ($$$)
  api-service     → LoadBalancer 1 (external IP)
  web-service     → LoadBalancer 2 (external IP)
  admin-service   → LoadBalancer 3 (external IP)

With Ingress:
  Single load balancer routes to all services
  api.example.com   ─┐
  web.example.com   ─┼─→ Ingress Controller (1 LB) → Services
  admin.example.com ─┘
```

### Ingress Resource

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.example.com
    secretName: tls-secret
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /orders
        pathType: Prefix
        backend:
          service:
            name: order-service
            port:
              number: 80
      - path: /users
        pathType: Prefix
        backend:
          service:
            name: user-service
            port:
              number: 80
```

### Ingress Controller

```
Ingress Resource alone does NOTHING!
You MUST deploy an Ingress Controller to implement the rules.

Ingress Controller:
  - Watches Ingress resources via API Server
  - Configures reverse proxy (nginx, envoy, etc.)
  - Handles actual traffic routing
  - Usually runs as a Deployment + Service (LoadBalancer)

Popular Ingress Controllers:
  ┌──────────────────────────┬───────────────────────────────┐
  │ Controller               │ Use Case                       │
  ├──────────────────────────┼───────────────────────────────┤
  │ NGINX Ingress            │ General purpose, most popular  │
  │ AWS LB Controller        │ AWS ALB/NLB native             │
  │ Traefik                  │ Auto-discovery, Let's Encrypt  │
  │ HAProxy                  │ High performance               │
  │ Istio Gateway            │ Service mesh integration       │
  │ Kong                     │ API gateway features           │
  └──────────────────────────┴───────────────────────────────┘
```

### Path-Based Routing

```yaml
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /api/orders
        pathType: Prefix
        backend:
          service:
            name: order-service
            port:
              number: 80
      - path: /api/payments
        pathType: Prefix
        backend:
          service:
            name: payment-service
            port:
              number: 80
      - path: /
        pathType: Prefix
        backend:
          service:
            name: frontend
            port:
              number: 80
```

```
Path Types:
  Prefix:        /api matches /api, /api/, /api/orders
  Exact:         /api matches only /api (not /api/)
  ImplementationSpecific: Controller decides behavior
```

### Host-Based Routing

```yaml
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-service
            port:
              number: 80
  - host: web.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: web-service
            port:
              number: 80
  - host: admin.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: admin-service
            port:
              number: 80
```

### TLS

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: tls-secret
type: kubernetes.io/tls
data:
  tls.crt: <base64-encoded-cert>
  tls.key: <base64-encoded-key>

---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: secure-ingress
spec:
  tls:
  - hosts:
    - api.example.com
    - web.example.com
    secretName: tls-secret
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-service
            port:
              number: 80
```

### SSL Termination

```
SSL Termination at Ingress:
  
  Client ──HTTPS──→ Ingress Controller ──HTTP──→ Service → Pod
                    (TLS terminated here)
  
  Benefits:
  - Centralized certificate management
  - Offload TLS from backend pods
  - Single place to rotate certs
  
  Alternatives:
  - End-to-end encryption (TLS passthrough to pods)
  - Re-encryption (TLS → Ingress → new TLS → Pod)
```

### NGINX Ingress

```yaml
# Install: kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.0/deploy/static/provider/cloud/deploy.yaml

# Common annotations:
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/rate-limit: "10"
    nginx.ingress.kubernetes.io/cors-allow-origin: "https://example.com"
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
spec:
  ingressClassName: nginx
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-service
            port:
              number: 80
```

### AWS Load Balancer Controller

```yaml
# Creates AWS ALB for Ingress
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:...
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/healthcheck-path: /health
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-service
            port:
              number: 80
```

### Ingress vs Service

```
┌────────────────────┬──────────────────────────────────────────┐
│ Feature            │ Service (LoadBalancer)  │ Ingress          │
├────────────────────┼────────────────────────┼──────────────────┤
│ Layer              │ L4 (TCP/UDP)           │ L7 (HTTP/HTTPS)  │
│ Protocol           │ Any                    │ HTTP/HTTPS        │
│ Routing            │ Port-based only        │ Path + Host based│
│ TLS                │ Passthrough            │ Termination       │
│ Cost               │ 1 LB per service       │ 1 LB for all     │
│ Features           │ Basic LB              │ Rewrites, CORS,   │
│                    │                        │ rate limiting     │
│ Use case           │ TCP services, gRPC    │ HTTP APIs, Web    │
└────────────────────┴────────────────────────┴──────────────────┘
```

---

## Internal Working

```
Ingress Controller Flow:

1. Admin deploys Ingress Controller (e.g., nginx)
   - Creates Deployment (nginx pods)
   - Creates Service (type: LoadBalancer) → gets external IP

2. Developer creates Ingress resource
   - Defines routing rules (paths, hosts, TLS)

3. Ingress Controller watches API Server for Ingress objects
   - Detects new/updated Ingress
   - Generates nginx.conf from Ingress rules
   - Reloads nginx with new config

4. Traffic flow:
   DNS: api.example.com → External LB IP
   Client → Cloud LB → Ingress Controller Pod
   → Reads Host header + path
   → Routes to correct backend Service
   → Service → Backend Pod

5. Health checking:
   Ingress Controller periodically checks backends
   Removes unhealthy backends from routing
```

---

## Diagram

```
┌─────────────────────── INGRESS ARCHITECTURE ──────────────────────┐
│                                                                     │
│  Internet                                                          │
│      │                                                             │
│      ▼                                                             │
│  ┌──────────────────┐                                             │
│  │ Cloud LB (AWS    │  ← Service type: LoadBalancer               │
│  │ ALB/NLB)         │                                             │
│  └────────┬─────────┘                                             │
│           │                                                        │
│           ▼                                                        │
│  ┌──────────────────────────────────────────┐                     │
│  │      INGRESS CONTROLLER (nginx)          │                     │
│  │                                           │                     │
│  │  Rules:                                   │                     │
│  │  api.example.com/orders → order-svc:80   │                     │
│  │  api.example.com/users  → user-svc:80    │                     │
│  │  web.example.com/*      → frontend:80    │                     │
│  └────────┬──────────┬──────────┬───────────┘                     │
│           │          │          │                                   │
│           ▼          ▼          ▼                                   │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐                          │
│  │ order   │  │ user    │  │ frontend │                          │
│  │ service │  │ service │  │ service  │                          │
│  └────┬────┘  └────┬────┘  └────┬─────┘                          │
│       │             │            │                                  │
│  ┌────┴────┐  ┌────┴────┐  ┌───┴─────┐                           │
│  │[P][P][P]│  │[P][P]   │  │[P][P]   │                           │
│  └─────────┘  └─────────┘  └─────────┘                           │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between Ingress and Service?

**A:** Service operates at L4 (TCP/UDP) — routes based on IP:port. Ingress operates at L7 (HTTP) — routes based on host headers and URL paths. Ingress consolidates multiple services behind one load balancer with path/host routing, TLS termination, and features like rewrites and rate limiting. Use Service (LoadBalancer) for non-HTTP traffic (gRPC, TCP databases), use Ingress for HTTP/HTTPS APIs and web apps.

### Q2: What is an Ingress Controller and why is it needed?

**A:** Ingress Controller is the actual implementation that reads Ingress resources and configures routing. The Ingress resource is just a config declaration — without a controller, nothing happens. The controller (nginx, traefik, ALB controller) watches for Ingress objects, generates proxy config, and handles actual traffic routing. It typically runs as a Deployment with a LoadBalancer Service.

### Q3: How does TLS work with Ingress?

**A:** 
1. Create a TLS Secret containing cert and key
2. Reference it in Ingress spec under `tls` section
3. Ingress Controller terminates TLS (SSL termination)
4. Backend traffic from controller to pods is HTTP (unencrypted)
5. For end-to-end encryption, use annotations to enable backend HTTPS

Cert-manager can automate certificate issuance and renewal (Let's Encrypt).

### Q4: What is the difference between path-based and host-based routing?

**A:**
- **Path-based:** Same domain, different paths → different services
  - `api.example.com/orders` → order-service
  - `api.example.com/users` → user-service
- **Host-based:** Different domains → different services
  - `api.example.com` → api-service
  - `web.example.com` → frontend-service

You can combine both in a single Ingress resource.

### Q5: How would you handle HTTPS redirect with Ingress?

**A:** Using NGINX Ingress annotation:
```yaml
annotations:
  nginx.ingress.kubernetes.io/ssl-redirect: "true"
  nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
```
This returns a 308 redirect from HTTP to HTTPS. For AWS ALB, use `alb.ingress.kubernetes.io/actions.ssl-redirect` with a fixed-response action.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Creating Ingress without controller | Rules not implemented | Deploy Ingress Controller first |
| Wrong ingressClassName | Controller doesn't pick up rules | Match className to installed controller |
| Missing TLS secret | HTTPS fails with default cert | Create secret or use cert-manager |
| Path conflicts | Wrong service receives traffic | Order paths specific → general |
| No default backend | 404 for unmatched paths | Configure defaultBackend |

---

## Best Practices

1. **Use one Ingress Controller** per cluster (or per team)
2. **Enable TLS** for all external-facing services
3. **Use cert-manager** for automated certificate management
4. **Set rate limiting** to prevent abuse
5. **Configure health checks** for backends
6. **Use annotations** for controller-specific features
7. **Plan path hierarchy** — more specific paths first
8. **Monitor Ingress** — track 4xx/5xx errors, latency

---

## Related Topics

- [06. Services](./06-services.md)
- [07. Networking](./07-networking.md)
- [31. Network Policies](./31-network-policies.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
