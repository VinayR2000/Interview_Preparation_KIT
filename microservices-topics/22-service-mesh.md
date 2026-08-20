# 22. Service Mesh

## Theory

A service mesh is a dedicated infrastructure layer that handles service-to-service communication. It provides networking features (mTLS, load balancing, retries) without changing application code.

### What Service Mesh Provides:
- **Traffic management**: Routing, load balancing, traffic splitting
- **Security**: mTLS, authorization policies
- **Observability**: Metrics, tracing, logging between services
- **Resilience**: Retries, timeouts, circuit breaking

### Architecture:
- **Sidecar proxy**: Runs alongside each service pod (Envoy)
- **Control plane**: Configures all sidecars (Istiod)
- **Data plane**: All sidecar proxies handling actual traffic

### Service Mesh vs Application Libraries:

| Aspect | Service Mesh | Library (Resilience4j) |
|--------|-------------|------------------------|
| Code changes | None | Annotations/code required |
| Language | Any (polyglot) | Language-specific |
| Updates | Update mesh, not services | Update each service |
| Overhead | Sidecar resource usage | In-process |
| Visibility | Automatic metrics/traces | Must instrument |

---

## Internal Working

### Sidecar Proxy Pattern:

```
┌────────────────────────────────────────────────────────────┐
│ SERVICE MESH ARCHITECTURE                                   │
│                                                             │
│ CONTROL PLANE (Istiod):                                   │
│ ┌──────────────────────────────────────────────────────┐  │
│ │  - Distributes config to all sidecars                │  │
│ │  - Manages certificates                              │  │
│ │  - Defines routing rules                             │  │
│ │  - Authorization policies                            │  │
│ └──────────────────────────────────────────────────────┘  │
│        │ config push        │ config push                  │
│        ↓                    ↓                              │
│ DATA PLANE (Envoy Sidecars):                              │
│                                                             │
│ Pod A                          Pod B                       │
│ ┌───────────────────────┐    ┌───────────────────────┐   │
│ │ ┌─────────┐ ┌───────┐│    │┌───────┐ ┌─────────┐ │   │
│ │ │  Order  │ │ Envoy ││    ││ Envoy │ │ Payment │ │   │
│ │ │ Service │ │ Proxy ││    ││ Proxy │ │ Service │ │   │
│ │ │         │ │       ││    ││       │ │         │ │   │
│ │ │ localhost│→│ :15001││    ││:15001 │→│localhost│ │   │
│ │ └─────────┘ └───┬───┘│    │└───┬───┘ └─────────┘ │   │
│ └──────────────────┼────┘    └────┼──────────────────┘   │
│                    │              │                        │
│                    └──── mTLS ────┘                        │
│                                                             │
│ Application sends plain HTTP to localhost                  │
│ Sidecar intercepts → adds mTLS, headers, metrics         │
│ Sidecar on receiving end → strips mTLS, forwards to app  │
│                                                             │
│ Application is UNAWARE of:                                │
│   - TLS/encryption                                        │
│   - Retries                                               │
│   - Circuit breaking                                      │
│   - Load balancing                                        │
│   - Metrics collection                                    │
└────────────────────────────────────────────────────────────┘
```

### Traffic Management:

```
CANARY DEPLOYMENT via Service Mesh:

┌────────────────────────────────────────┐
│                                         │
│  Istio VirtualService:                 │
│    route:                              │
│      - destination: order-v1           │
│        weight: 95                      │
│      - destination: order-v2           │
│        weight: 5                       │
│                                         │
│  Traffic:                              │
│  ┌────────────┐                        │
│  │ Incoming   │                        │
│  │ Requests   │                        │
│  └─────┬──────┘                        │
│        │                                │
│   ┌────┴────┐                          │
│   │ 95%  5% │                          │
│   ↓         ↓                          │
│ ┌──────┐  ┌──────┐                    │
│ │ V1   │  │ V2   │                    │
│ │(3pods)│  │(1pod)│                    │
│ └──────┘  └──────┘                    │
│                                         │
│ Gradually increase V2 traffic:         │
│ 5% → 25% → 50% → 100%               │
│ If V2 has errors → route all back to V1│
└────────────────────────────────────────┘
```

---

## Code

### Istio Traffic Routing:

```yaml
# VirtualService — routing rules
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
spec:
  hosts:
    - order-service
  http:
    - match:
        - headers:
            x-canary:
              exact: "true"
      route:
        - destination:
            host: order-service
            subset: v2
    - route:
        - destination:
            host: order-service
            subset: v1
          weight: 95
        - destination:
            host: order-service
            subset: v2
          weight: 5

---
# DestinationRule — subsets + load balancing
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: order-service
spec:
  host: order-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        h2UpgradePolicy: DEFAULT
        http1MaxPendingRequests: 100
    outlierDetection:
      consecutiveErrors: 5
      interval: 10s
      baseEjectionTime: 30s
  subsets:
    - name: v1
      labels:
        version: v1
    - name: v2
      labels:
        version: v2
```

### Istio Retry and Timeout:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: payment-service
spec:
  hosts:
    - payment-service
  http:
    - timeout: 3s
      retries:
        attempts: 3
        perTryTimeout: 1s
        retryOn: 5xx,reset,connect-failure,retriable-4xx
      route:
        - destination:
            host: payment-service
```

### Authorization Policy (mTLS + RBAC):

```yaml
# Only order-service can call payment-service
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: payment-service-policy
  namespace: default
spec:
  selector:
    matchLabels:
      app: payment-service
  rules:
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/order-service"]
      to:
        - operation:
            methods: ["POST"]
            paths: ["/api/payments/*"]
    - from:
        - source:
            principals: ["cluster.local/ns/default/sa/refund-service"]
      to:
        - operation:
            methods: ["POST"]
            paths: ["/api/refunds/*"]
```

### PeerAuthentication (Strict mTLS):

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: default
spec:
  mtls:
    mode: STRICT  # All traffic must use mTLS
```

---

## Interview Questions

1. **What is a Service Mesh?**
   - Infrastructure layer handling service-to-service communication via sidecar proxies. Provides mTLS, load balancing, retries, observability without code changes. Control plane configures, data plane (sidecars) executes.

2. **Sidecar pattern — how does it work?**
   - Each pod gets an Envoy proxy container alongside the application. All inbound/outbound traffic goes through the sidecar. Sidecar applies policies (mTLS, retries, routing) transparently. App sends plain HTTP to localhost.

3. **When to use Service Mesh vs application library?**
   - Mesh: Polyglot services, consistent policies across all services, don't want code changes. Library: Single language, fine-grained control, lower resource overhead. Large production systems often use both.

4. **Service Mesh overhead?**
   - Each sidecar uses ~50MB RAM, adds ~1-2ms latency per hop. For 100 services = 100 sidecars = 5GB RAM overhead. Trade-off: resource cost vs operational simplicity and security.

5. **Istio vs Linkerd?**
   - Istio: Feature-rich, complex, larger resource footprint, widely adopted. Linkerd: Simpler, lighter, easier to operate, Rust-based proxy. Choose Istio for complex needs, Linkerd for simplicity.

---

## Best Practices

1. **Gradual adoption** — Start with observability, then security, then traffic management
2. **Strict mTLS** — Enforce for all services once stable
3. **Start without mesh** — Use application libraries, adopt mesh when complexity warrants
4. **Monitor sidecar resources** — Set appropriate resource limits
5. **Canary deployments** — Leverage traffic splitting for safe rollouts
6. **Authorization policies** — Define which services can communicate
7. **Don't fight the mesh** — If using Istio retries, disable application-level retries
