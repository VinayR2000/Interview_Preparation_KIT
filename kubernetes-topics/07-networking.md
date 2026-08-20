# 7. Networking ⭐⭐⭐

---

## Theory

Kubernetes networking follows a flat network model where every Pod gets its own IP address and can communicate with every other Pod without NAT.

### Kubernetes Networking Rules

```
Fundamental Requirements:
1. Every Pod gets a unique IP address
2. Pods on same node can communicate without NAT
3. Pods on different nodes can communicate without NAT
4. Agents on a node can communicate with all Pods on that node

No NAT anywhere in pod-to-pod communication!
```

### Pod Networking

```
Each Pod gets:
  - Unique IP from Pod CIDR (e.g., 10.244.0.0/16)
  - Own network namespace (isolated network stack)
  - Virtual ethernet pair (veth) connecting to node bridge

Pod Network Stack:
  ┌──── Pod ────────────────┐
  │ eth0: 10.244.1.5        │
  │ Container A (port 8080) │
  │ Container B (port 9090) │ ← share eth0 (localhost between them)
  └─────────┬───────────────┘
            │ veth pair
  ──────────┴─────────── Node bridge (cbr0/cni0) ───────────
```

### Pod-to-Pod Communication

```
Same Node:
  Pod A (10.244.1.5) → bridge → Pod B (10.244.1.6)
  Direct L2 switching through node bridge

Different Nodes:
  Pod A (10.244.1.5) → Node 1 → Overlay/Route → Node 2 → Pod B (10.244.2.3)
  
  Mechanisms:
  - VXLAN overlay (encapsulate packets)
  - BGP routing (advertise pod CIDRs)
  - Cloud VPC routing (AWS VPC CNI)
```

### Pod-to-Service Communication

```
Pod → Service ClusterIP → kube-proxy (iptables/IPVS) → Backend Pod

Example:
  Pod A sends request to order-service:80
  1. DNS resolves order-service → ClusterIP 10.96.0.100
  2. Packet destined to 10.96.0.100:80
  3. iptables rule on Pod A's node intercepts
  4. DNAT rewrites dest to Pod IP: 10.244.2.3:8080
  5. Packet routed to target Pod (same/different node)
```

### Service-to-Service Communication

```
Microservice Architecture:
  
  API Gateway → Order Service → Payment Service
                     ↓
              Inventory Service

Each service accessed via DNS name:
  order-service.production.svc.cluster.local
  payment-service.production.svc.cluster.local
  
Or short form (same namespace):
  order-service
  payment-service
```

### Cluster DNS

```
DNS is the foundation of service discovery:

Pod DNS resolution:
  /etc/resolv.conf in every Pod:
    nameserver 10.96.0.10  (CoreDNS ClusterIP)
    search default.svc.cluster.local svc.cluster.local cluster.local

Resolution order for "order-service":
  1. order-service.default.svc.cluster.local → found!
  
Cross-namespace:
  "order-service.production" → order-service.production.svc.cluster.local
```

### CoreDNS

```
CoreDNS: Default DNS server in Kubernetes (replaced kube-dns)

Runs as a Deployment in kube-system namespace:
  - 2 replicas (HA)
  - Serves DNS for all cluster services and pods
  - Configurable via Corefile (ConfigMap)

Records served:
  Service A record:     service.ns.svc.cluster.local → ClusterIP
  Headless A record:    service.ns.svc.cluster.local → Pod IPs
  Pod A record:         10-244-1-5.ns.pod.cluster.local → 10.244.1.5
  SRV record:           _port._proto.service.ns.svc.cluster.local
```

### CNI (Container Network Interface)

```
CNI: Plugin interface for configuring Pod networking

kubelet → CNI plugin → Configure Pod network

CNI plugin responsibilities:
  1. Allocate IP to Pod (IPAM)
  2. Create veth pair
  3. Configure routing
  4. Set up network connectivity

Popular CNI Plugins:
  ┌──────────────┬──────────────────────────────────────┐
  │ Plugin       │ Features                              │
  ├──────────────┼──────────────────────────────────────┤
  │ Calico       │ BGP routing, NetworkPolicy, eBPF     │
  │ Cilium       │ eBPF-based, observability, security  │
  │ Flannel      │ Simple VXLAN overlay                  │
  │ Weave        │ Mesh overlay, encryption              │
  │ AWS VPC CNI  │ Native VPC IPs for pods (AWS)        │
  │ Azure CNI    │ Azure VNET IPs for pods              │
  └──────────────┴──────────────────────────────────────┘
```

### Network Plugins

```
Overlay Networks (Flannel, Weave):
  - Encapsulate pod traffic in outer packet (VXLAN)
  - Works anywhere (cloud, on-prem)
  - Slight overhead (encapsulation)

Routing-Based (Calico BGP):
  - Advertise pod CIDRs via BGP
  - No encapsulation overhead
  - Requires BGP support in network

Cloud-Native (AWS VPC CNI):
  - Pods get real VPC IPs
  - No overlay, native performance
  - Limited by ENI/IP limits per instance
```

### Ingress

```
Ingress: HTTP/HTTPS routing from external to internal Services

External → Ingress Controller (e.g., nginx) → Service → Pods

Features:
  - Path-based routing: /api → backend, /web → frontend
  - Host-based routing: api.example.com, web.example.com
  - TLS termination
  - Rate limiting, auth (via annotations)
```

### Ingress Controller

```
Ingress Controller: Implements Ingress rules (separate install)

Popular controllers:
  - NGINX Ingress Controller
  - AWS Load Balancer Controller (ALB)
  - Traefik
  - HAProxy
  - Istio Gateway

Ingress resource is useless without an Ingress Controller!
```

### NetworkPolicy

```
NetworkPolicy: Firewall rules for Pod-to-Pod traffic

Default: All pods can communicate with all pods (no isolation)
With NetworkPolicy: Only explicitly allowed traffic

Requires CNI plugin that supports NetworkPolicy:
  ✓ Calico, Cilium, Weave
  ✗ Flannel (no NetworkPolicy support)
```

### Ports

```
Port terminology in Kubernetes:

containerPort: Port the container listens on (informational in Pod spec)
port:          Service port (what clients connect to)
targetPort:    Port on the Pod (where Service forwards to)
nodePort:      Port on each Node (NodePort/LoadBalancer services)

Flow:
  External:nodePort → Service:port → Pod:targetPort → Container:containerPort
  
  Note: targetPort usually equals containerPort
```

### targetPort vs port vs nodePort

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
  - port: 80            # ClusterIP listens on 80
    targetPort: 8080    # Forwards to Pod port 8080
    nodePort: 30080     # Each node opens port 30080

# Access internally:  my-app:80 (or ClusterIP:80)
# Access externally:  <NodeIP>:30080
# Pod listens on:     8080
```

---

## Internal Working

```
Pod Network Setup (CNI):

1. Pod scheduled on Node
2. kubelet calls CNI plugin (ADD command)
3. CNI plugin:
   a. Creates network namespace for Pod
   b. Creates veth pair (one end in Pod, one in node)
   c. Assigns IP from IPAM (IP Address Management)
   d. Configures routes inside Pod namespace
   e. Connects node end of veth to bridge/route
4. Pod now has network connectivity

Packet Flow (Pod A on Node 1 → Pod B on Node 2):

1. Pod A sends packet: src=10.244.1.5, dst=10.244.2.3
2. Packet exits Pod via veth into node bridge
3. Node routing table: 10.244.2.0/24 → Node 2
4. Packet sent to Node 2 (via overlay or direct route)
5. Node 2 receives packet, routes to Pod B's veth
6. Packet arrives at Pod B
```

---

## Diagram

```
┌─────────────────────── K8S NETWORKING ─────────────────────────┐
│                                                                   │
│  ┌─── Node 1 ──────────────────────┐                           │
│  │                                   │                           │
│  │  ┌─── Pod A ───┐  ┌─── Pod B ─┐ │                           │
│  │  │ 10.244.1.5  │  │ 10.244.1.6│ │                           │
│  │  │  eth0       │  │  eth0     │ │                           │
│  │  └──────┬──────┘  └─────┬─────┘ │                           │
│  │         │ veth           │ veth   │                           │
│  │  ───────┴────────────────┴──────  │                           │
│  │         cni0 bridge (10.244.1.1)  │                           │
│  │              │                     │                           │
│  │         eth0 (192.168.1.10)       │                           │
│  └──────────────┬────────────────────┘                           │
│                  │                                                │
│          ────────┴───────── Physical Network ──────────          │
│                  │                                                │
│  ┌──────────────┴────────────────────┐                           │
│  │         eth0 (192.168.1.11)       │                           │
│  │              │                     │                           │
│  │  ───────┬────────────────┬──────  │                           │
│  │         cni0 bridge (10.244.2.1)  │                           │
│  │         │ veth           │ veth   │                           │
│  │  ┌──────┴──────┐  ┌─────┴─────┐ │                           │
│  │  │ 10.244.2.3  │  │ 10.244.2.4│ │                           │
│  │  │  Pod C      │  │  Pod D    │ │                           │
│  │  └─────────────┘  └───────────┘ │                           │
│  │                                   │                           │
│  └─── Node 2 ──────────────────────┘                           │
└───────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: Explain the Kubernetes networking model.

**A:** K8s uses a flat network model with these rules:
1. Every Pod gets a unique IP (no NAT between pods)
2. All Pods can communicate with all other Pods directly
3. Agents on a node can talk to all Pods on that node
This is implemented by CNI plugins that set up overlay networks (VXLAN) or routing (BGP) between nodes.

### Q2: How does Pod-to-Service communication work?

**A:**
1. Pod makes DNS request for service name → CoreDNS returns ClusterIP
2. Pod sends packet to ClusterIP:port
3. iptables/IPVS rules on the Pod's node intercept the packet
4. DNAT rewrites destination to a backend Pod IP:targetPort
5. Packet routed to target Pod (via CNI network)
6. Response follows reverse path

### Q3: What is CNI and why is it needed?

**A:** CNI (Container Network Interface) is a specification and plugin framework for configuring Pod networking. It's needed because K8s doesn't implement networking itself — it delegates to CNI plugins. When a Pod is created, kubelet calls the CNI plugin to: allocate an IP, create network interfaces, set up routing. Different plugins offer different features (Calico: NetworkPolicy + BGP, Cilium: eBPF + observability, AWS VPC CNI: native VPC IPs).

### Q4: What is the difference between overlay and non-overlay networking?

**A:**
- **Overlay (Flannel VXLAN, Weave):** Encapsulates pod traffic inside outer UDP/VXLAN packets. Works anywhere but adds ~50 bytes overhead and slight latency.
- **Non-overlay (Calico BGP, AWS VPC CNI):** Uses native routing (BGP) or cloud VPC routing. No encapsulation overhead, better performance, but requires network infrastructure support.

### Q5: How does CoreDNS work in Kubernetes?

**A:** CoreDNS runs as a Deployment (2 replicas) in kube-system. Every Pod's `/etc/resolv.conf` points to CoreDNS's ClusterIP. CoreDNS watches the API Server for Service/Pod changes and creates DNS records. It resolves `<service>.<namespace>.svc.cluster.local` to ClusterIP (or Pod IPs for headless). Search domains allow short names within the same namespace.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using Flannel but expecting NetworkPolicy | Flannel doesn't support NetworkPolicy | Use Calico or Cilium |
| Hardcoding Pod IPs | IPs are ephemeral | Use Service DNS names |
| Not understanding port vs targetPort | Connection failures | port=service port, targetPort=container port |
| DNS issues (CoreDNS overloaded) | Service discovery fails | Scale CoreDNS, use NodeLocal DNS Cache |
| Pod CIDR overlaps with node CIDR | Routing conflicts | Plan CIDR ranges before cluster creation |

---

## Best Practices

1. **Use DNS for service discovery** — never hardcode IPs
2. **Choose CNI based on needs** — NetworkPolicy support, performance, cloud integration
3. **Plan CIDR ranges** — Pod CIDR, Service CIDR, Node CIDR should not overlap
4. **Use NetworkPolicy** — default deny + explicit allow
5. **Scale CoreDNS** for large clusters, consider NodeLocal DNS Cache
6. **Use named ports** — makes configuration clearer and protocol-aware
7. **Monitor network** — track DNS latency, packet drops, connection counts

---

## Related Topics

- [06. Services](./06-services.md)
- [08. Ingress](./08-ingress.md)
- [31. Network Policies](./31-network-policies.md)
- [14. Scheduling](./14-scheduling.md)
