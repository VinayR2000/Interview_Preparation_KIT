# 6. Services ⭐⭐⭐

---

## Theory

A **Service** is an abstraction that defines a logical set of Pods and a policy to access them. Services provide stable networking for ephemeral Pods — a fixed IP and DNS name that doesn't change even as Pods are created/destroyed.

### Why Service?

```
Problem without Services:
  - Pods have ephemeral IPs (change on restart)
  - Client can't hardcode Pod IPs
  - Need load balancing across replicas
  - No service discovery mechanism

Solution with Services:
  - Stable virtual IP (ClusterIP) that never changes
  - DNS name (my-service.namespace.svc.cluster.local)
  - Automatic load balancing
  - Auto-updates when Pods come/go
```

### ClusterIP (Default)

Internal-only Service — accessible only within the cluster:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  type: ClusterIP          # Default (can be omitted)
  selector:
    app: order-service     # Matches pods with this label
  ports:
  - port: 80              # Service port (what clients use)
    targetPort: 8080      # Pod port (what container listens on)
    protocol: TCP
```

```
ClusterIP:
  - Gets a virtual IP from cluster IP range (e.g., 10.96.0.0/12)
  - Only accessible within the cluster
  - Used for inter-service communication
  - DNS: order-service.default.svc.cluster.local
  
  Client Pod → ClusterIP:80 → kube-proxy → Pod:8080 (random)
```

### NodePort

Exposes the Service on each Node's IP at a static port:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-app
spec:
  type: NodePort
  selector:
    app: my-app
  ports:
  - port: 80              # ClusterIP port
    targetPort: 8080      # Pod port
    nodePort: 30080       # Node port (30000-32767)
```

```
NodePort:
  - Opens a port on EVERY node (range: 30000-32767)
  - Also creates a ClusterIP (accessible internally)
  - External access via: <NodeIP>:30080
  
  External → NodeIP:30080 → kube-proxy → Pod:8080
  
  Use cases: Development, testing, non-cloud environments
  Not ideal for production (limited port range, no HA)
```

### LoadBalancer

Provisions an external load balancer (cloud provider):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-app
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
spec:
  type: LoadBalancer
  selector:
    app: my-app
  ports:
  - port: 80
    targetPort: 8080
```

```
LoadBalancer:
  - Creates cloud load balancer (AWS ELB/NLB, GCP LB, Azure LB)
  - Gets external IP/hostname
  - Also creates NodePort + ClusterIP
  
  Internet → Cloud LB → NodePort → kube-proxy → Pod:8080
  
  Use cases: Production external access
  Note: Each LoadBalancer Service = separate cloud LB ($$$)
        Use Ingress for HTTP routing with single LB
```

### ExternalName

Maps a Service to an external DNS name (no proxy, just CNAME):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-database
spec:
  type: ExternalName
  externalName: my-db.us-east-1.rds.amazonaws.com
```

```
ExternalName:
  - No ClusterIP, no proxy
  - Returns CNAME record when DNS queried
  - Used to reference external services with K8s DNS
  
  Pod → DNS: my-database → CNAME → my-db.us-east-1.rds.amazonaws.com
  
  Use cases:
  - Point to external databases (RDS, Cloud SQL)
  - Migration (change backend without changing app code)
  - Multi-cluster service references
```

### Headless Service

A Service without a ClusterIP — returns individual Pod IPs directly:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-db
spec:
  clusterIP: None          # ← Makes it headless
  selector:
    app: my-db
  ports:
  - port: 5432
```

```
Headless Service:
  - No virtual IP allocated
  - DNS returns Pod IPs directly (A records for each Pod)
  - No load balancing by kube-proxy
  - Client decides which Pod to connect to
  
  DNS query: my-db.default.svc.cluster.local
  Returns: 10.244.1.5, 10.244.2.3, 10.244.3.7 (Pod IPs)
  
  Use cases:
  - StatefulSets (connect to specific pod: pod-0.my-db.ns.svc)
  - Client-side load balancing
  - Service discovery for databases (leader election)
```

### Service Discovery

```
Two mechanisms:

1. DNS (preferred):
   - CoreDNS automatically creates records for Services
   - <service>.<namespace>.svc.cluster.local
   - Short form within same namespace: just <service>
   
   Example: curl http://order-service:80/api/orders

2. Environment Variables:
   - Kubelet injects env vars for each Service
   - ORDER_SERVICE_SERVICE_HOST=10.96.0.100
   - ORDER_SERVICE_SERVICE_PORT=80
   - Only for Services created BEFORE the Pod
   - DNS is preferred (no ordering dependency)
```

### DNS

```
DNS Records created by CoreDNS:

ClusterIP Service:
  order-service.default.svc.cluster.local → 10.96.0.100

Headless Service:
  my-db.default.svc.cluster.local → 10.244.1.5, 10.244.2.3

StatefulSet Pods (with Headless Service):
  my-db-0.my-db.default.svc.cluster.local → 10.244.1.5
  my-db-1.my-db.default.svc.cluster.local → 10.244.2.3

DNS Search Domains (in Pod):
  1. default.svc.cluster.local
  2. svc.cluster.local
  3. cluster.local
  
  So "order-service" resolves within same namespace
  "order-service.production" for cross-namespace
```

### Selectors

```yaml
# Service selects Pods by matching labels
spec:
  selector:
    app: my-app       # Must match Pod labels
    version: v2       # Can match multiple labels (AND logic)
```

```
Label matching:
  Service selector: {app: order-service}
  
  Pod-1 labels: {app: order-service, version: v1} → MATCH ✓
  Pod-2 labels: {app: order-service, version: v2} → MATCH ✓
  Pod-3 labels: {app: payment-service}            → NO MATCH ✗
  
  Service routes to: Pod-1, Pod-2
```

### Endpoints

```
Endpoints: The list of Pod IPs that a Service routes to

When Service selector matches Pods:
  K8s automatically creates Endpoints object

kubectl get endpoints order-service
NAME            ENDPOINTS
order-service   10.244.1.5:8080,10.244.2.3:8080,10.244.3.7:8080

When Pod becomes Ready: added to Endpoints
When Pod becomes NotReady: removed from Endpoints
When Pod is deleted: removed from Endpoints
```

### EndpointSlices

```
EndpointSlices: Scalable replacement for Endpoints (K8s 1.21+)

Problem with Endpoints:
  - Single object grows large with many pods
  - Every change requires full object update
  - O(n) updates for n pods

EndpointSlices:
  - Split endpoints into smaller groups (max 100 per slice)
  - Partial updates (only changed slice)
  - Better performance for large clusters

kubectl get endpointslices
NAME                  ADDRESSTYPE   PORTS   ENDPOINTS
order-service-abc     IPv4          8080    10.244.1.5,10.244.2.3...
```

### kube-proxy

```
kube-proxy runs on every node and implements Service networking:

Modes:
1. iptables (default):
   - Creates iptables rules for each Service
   - DNAT from ClusterIP:port to Pod IP:targetPort
   - Random or round-robin selection
   - No real proxy process (kernel handles routing)

2. IPVS:
   - Uses Linux Virtual Server in kernel
   - Better performance for many services
   - Multiple algorithms: rr, lc, dh, sh, sed, nq

Traffic flow:
  Pod A → ClusterIP:80 → iptables rule → DNAT → Pod B:8080
  (kube-proxy doesn't handle traffic, just configures rules)
```

---

## Internal Working

```
Service Creation and Traffic Flow:

1. User creates Service with selector
2. API Server stores Service in etcd
3. Endpoints Controller watches Services and Pods
4. Endpoints Controller creates Endpoints object matching selector
5. kube-proxy on each node watches Services and Endpoints
6. kube-proxy creates iptables/IPVS rules on its node
7. CoreDNS watches Services → creates DNS records

Traffic Flow:
  App Pod → DNS: order-service → ClusterIP: 10.96.0.100
  → iptables rule (on same node) → DNAT → Pod IP: 10.244.2.3:8080

When Pod dies:
  1. Pod marked NotReady (readiness probe fails)
  2. Endpoints Controller removes Pod IP from Endpoints
  3. kube-proxy updates iptables rules (removes Pod)
  4. Traffic no longer routed to dead Pod
```

---

## Diagram

```
┌────────────────────── SERVICE TYPES ──────────────────────────┐
│                                                                 │
│  ClusterIP (Internal):                                         │
│  ┌────────┐     ┌──────────────┐     ┌─────┐                 │
│  │ Pod A  │ ──→ │ ClusterIP:80 │ ──→ │Pod B│                 │
│  └────────┘     │ (10.96.0.100)│     │Pod C│                 │
│                  └──────────────┘     │Pod D│                 │
│                                       └─────┘                 │
│                                                                 │
│  NodePort (External via Node):                                 │
│  ┌──────────┐   ┌───────────────┐   ┌──────────────┐         │
│  │ External │→  │ NodeIP:30080  │→  │ ClusterIP:80 │→ Pods   │
│  │ Client   │   │ (any node)    │   └──────────────┘         │
│  └──────────┘   └───────────────┘                             │
│                                                                 │
│  LoadBalancer (External via Cloud LB):                         │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐                  │
│  │ Internet │→  │ Cloud LB │→  │ NodePort │→ ClusterIP → Pods│
│  └──────────┘   │(ext. IP) │   └──────────┘                  │
│                  └──────────┘                                   │
│                                                                 │
│  Headless (Direct Pod IPs):                                    │
│  ┌────────┐     DNS returns       ┌─────┐                    │
│  │ Pod A  │ ──→ Pod IPs directly → │Pod B│ (10.244.1.5)      │
│  └────────┘                        │Pod C│ (10.244.2.3)      │
│                                    └─────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code

### Complete Service Examples:

```yaml
# ClusterIP - Internal microservice communication
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: production
  labels:
    app: order-service
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
  - name: http
    port: 80
    targetPort: 8080
    protocol: TCP
  - name: grpc
    port: 9090
    targetPort: 9090
    protocol: TCP

---
# NodePort - Development/testing access
apiVersion: v1
kind: Service
metadata:
  name: my-app-nodeport
spec:
  type: NodePort
  selector:
    app: my-app
  ports:
  - port: 80
    targetPort: 8080
    nodePort: 30080

---
# LoadBalancer - Production external access
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-scheme: "internet-facing"
spec:
  type: LoadBalancer
  selector:
    app: api-gateway
  ports:
  - port: 443
    targetPort: 8443

---
# Headless - StatefulSet (database cluster)
apiVersion: v1
kind: Service
metadata:
  name: postgres
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
```

---

## Interview Questions

### Q1: What are the different Service types and their use cases?

**A:**
- **ClusterIP:** Internal inter-service communication (default). Gets virtual IP, only reachable within cluster.
- **NodePort:** Exposes on each node's IP at a static port (30000-32767). Good for development/testing.
- **LoadBalancer:** Creates external cloud load balancer. Production external access. Each service = separate LB (costly).
- **ExternalName:** DNS CNAME alias to external service. No proxy, just DNS mapping.
- **Headless (clusterIP: None):** Returns Pod IPs directly. Used with StatefulSets for direct pod addressing.

### Q2: What is the difference between port, targetPort, and nodePort?

**A:**
- **port:** The port the Service listens on (ClusterIP port). What clients use.
- **targetPort:** The port on the Pod/container. What the app listens on.
- **nodePort:** The port opened on each node (NodePort service only, 30000-32767).

Flow: External:nodePort → Service:port → Pod:targetPort

### Q3: How does service discovery work in Kubernetes?

**A:** Two mechanisms:
1. **DNS (preferred):** CoreDNS creates records for each Service. Format: `<service>.<namespace>.svc.cluster.local`. Within same namespace, just `<service>` works.
2. **Environment variables:** kubelet injects `<SERVICE>_SERVICE_HOST` and `<SERVICE>_SERVICE_PORT` env vars. Only works for Services created before the Pod.

### Q4: What is a Headless Service and when would you use it?

**A:** A Headless Service (clusterIP: None) doesn't allocate a virtual IP. DNS returns individual Pod IPs instead. Use cases:
- StatefulSets: address specific pods (pod-0.service.namespace)
- Client-side load balancing
- Database clusters where leader needs to be directly addressable
- When you need to know all backend Pod IPs

### Q5: How does kube-proxy implement load balancing?

**A:** kube-proxy watches Services and Endpoints, then configures network rules:
- **iptables mode:** Creates DNAT rules with probabilistic selection. Kernel handles traffic (no actual proxy). O(n) rule evaluation.
- **IPVS mode:** Uses kernel-level virtual server. O(1) lookup. Supports round-robin, least-connection, etc. Better for large clusters.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Selector doesn't match Pod labels | Service has no endpoints | Verify labels match exactly |
| Using LoadBalancer for every service | Expensive (one LB each) | Use Ingress for HTTP routing |
| Hardcoding ClusterIP in app | IP might change after recreation | Use DNS names |
| targetPort doesn't match container port | Connection refused | Ensure container listens on targetPort |
| Missing readiness probe | Traffic sent to unready pods | Configure readiness probes |

---

## Best Practices

1. **Use ClusterIP for internal** communication, LoadBalancer/Ingress for external
2. **Name your ports** — enables protocol detection and clarity
3. **Use DNS for service discovery** — not environment variables
4. **Configure readiness probes** — ensures only healthy pods receive traffic
5. **Use Ingress** for HTTP/HTTPS external access (consolidates LBs)
6. **Use Headless Services** with StatefulSets
7. **Consider IPVS mode** for clusters with many services (500+)

---

## Related Topics

- [07. Networking](./07-networking.md)
- [08. Ingress](./08-ingress.md)
- [11. StatefulSet](./11-statefulset.md)
- [04. Pods](./04-pods.md)
