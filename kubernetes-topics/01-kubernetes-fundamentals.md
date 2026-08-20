# 1. Kubernetes Fundamentals

---

## Theory

**Kubernetes (K8s)** is an open-source container orchestration platform originally developed by Google (based on their internal system Borg) and donated to CNCF in 2014. It automates deployment, scaling, and management of containerized applications.

### What is Kubernetes?

Kubernetes is a **container orchestrator** — it manages the lifecycle of containers across a cluster of machines, ensuring applications run reliably, scale automatically, and recover from failures.

```
Without K8s: Manually deploy containers, manage failures, scale manually
With K8s:    Declare desired state → K8s maintains it automatically
```

### Why Kubernetes?

| Problem | Kubernetes Solution |
|---------|-------------------|
| Manual container management | Automated orchestration |
| Single point of failure | Self-healing, replication |
| Manual scaling | Auto-scaling (HPA, VPA, Cluster) |
| Downtime during deployments | Rolling updates, zero-downtime |
| Service discovery complexity | Built-in DNS and service discovery |
| Configuration management | ConfigMaps and Secrets |
| Resource waste | Bin packing and resource optimization |

### Container Orchestration

Container orchestration automates the operational effort of running containerized workloads:

```
Container Orchestration handles:
  1. Scheduling    — Where to place containers
  2. Scaling       — How many instances to run
  3. Networking    — How containers communicate
  4. Storage       — How data persists
  5. Health        — Detecting and replacing failures
  6. Updates       — Rolling out new versions safely
  7. Security      — Access control and isolation
```

### Kubernetes vs Docker

| Aspect | Docker | Kubernetes |
|--------|--------|-----------|
| Purpose | Build and run containers | Orchestrate containers at scale |
| Scope | Single host | Multi-node cluster |
| Scaling | Manual | Automatic |
| Networking | Bridge/host network | Overlay network, service mesh |
| Self-healing | No (container stops = manual restart) | Yes (auto-restarts, reschedules) |
| Load balancing | Manual (nginx, etc.) | Built-in (Services) |
| State management | Volumes | PV, PVC, StorageClass |

```
Docker:      "Run this container on THIS machine"
Kubernetes:  "Run this container SOMEWHERE in the cluster, keep it running, scale it"
```

### Kubernetes vs Docker Compose

| Aspect | Docker Compose | Kubernetes |
|--------|---------------|-----------|
| Environment | Development, single host | Production, multi-node |
| Definition | docker-compose.yml | Multiple YAML manifests |
| Scaling | `scale: 3` (same host) | HPA across nodes |
| Self-healing | Restart policy only | Full self-healing |
| Networking | Docker network | CNI plugins, Services |
| Storage | Docker volumes | PV/PVC/StorageClass |
| Rolling updates | No | Yes |
| Service discovery | Container name | DNS-based |

### Kubernetes Architecture (Overview)

```
┌─────────────────────────────────────────────────────────────┐
│                    KUBERNETES CLUSTER                         │
│                                                              │
│  ┌────────────────────── CONTROL PLANE ──────────────────┐  │
│  │  API Server │ etcd │ Scheduler │ Controller Manager   │  │
│  └───────────────────────────────────────────────────────┘  │
│                           │                                  │
│  ┌─── Worker Node 1 ───┐ │ ┌─── Worker Node 2 ───┐       │
│  │ kubelet │ kube-proxy │ │ │ kubelet │ kube-proxy │       │
│  │ Pod Pod Pod          │ │ │ Pod Pod Pod          │       │
│  └──────────────────────┘ │ └──────────────────────┘       │
│                           │                                  │
│  ┌─── Worker Node 3 ───┐ │                                 │
│  │ kubelet │ kube-proxy │ │                                 │
│  │ Pod Pod Pod          │ │                                 │
│  └──────────────────────┘ │                                 │
└─────────────────────────────────────────────────────────────┘
```

### Declarative Configuration

Kubernetes uses a **declarative** model — you describe the desired state, and K8s works to achieve and maintain it.

```yaml
# Declarative: "I want 3 replicas of my app"
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3   # ← Desired state
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: my-app
        image: my-app:v1
```

```
Imperative:    "Create 3 pods now" (kubectl run...)
Declarative:   "I want 3 pods running" (kubectl apply -f deployment.yaml)

Declarative is preferred because:
  - Version controlled (GitOps)
  - Reproducible
  - Self-documenting
  - Idempotent (apply multiple times = same result)
```

### Desired State

```
Desired State (what you declare):
  - 3 replicas of web-app
  - Each with 256Mi memory
  - Exposed on port 80

Current State (what's actually running):
  - 2 replicas running (one crashed)

Kubernetes Reconciliation:
  Desired (3) ≠ Current (2) → Create 1 more Pod
```

### Reconciliation

The **reconciliation loop** (or control loop) continuously compares desired state with actual state and takes corrective action:

```
┌──────────────┐
│  Observe     │ ← Read current state from cluster
└──────┬───────┘
       │
┌──────▼───────┐
│  Compare     │ ← Current state vs Desired state
└──────┬───────┘
       │
┌──────▼───────┐
│  Act         │ ← Take action to reconcile (create/delete/update)
└──────┬───────┘
       │
       └──────→ (loop back to Observe)
```

### Self-Healing

Kubernetes automatically detects and recovers from failures:

```
Self-Healing Scenarios:
  1. Pod crashes       → Restart container (restartPolicy)
  2. Pod fails health  → Remove from service, restart
  3. Node dies         → Reschedule Pods to healthy nodes
  4. Pod deleted       → Controller creates replacement
  5. Resource pressure → Evict low-priority Pods
```

### Auto Scaling

```
Three levels of auto-scaling:

1. Horizontal Pod Autoscaler (HPA):
   - Scales Pod count based on CPU/memory/custom metrics
   - Example: 2 pods at 50% CPU → scales to 4 pods at 80% CPU

2. Vertical Pod Autoscaler (VPA):
   - Adjusts resource requests/limits per Pod
   - Example: Pod needs more memory → VPA increases memory request

3. Cluster Autoscaler:
   - Scales the cluster nodes
   - Example: Pods pending (no capacity) → add new node
```

### Rolling Updates

```
Rolling Update (default strategy):

Before:  [v1] [v1] [v1] [v1]

Step 1:  [v1] [v1] [v1] [v2] ← New pod created
Step 2:  [v1] [v1] [v2] [v2] ← Old pod terminated, new created
Step 3:  [v1] [v2] [v2] [v2]
Step 4:  [v2] [v2] [v2] [v2] ← Complete

Benefits:
  - Zero downtime
  - Gradual rollout
  - Can be paused/resumed
  - Automatic rollback on failure
```

### Rollbacks

```
Deployment History:
  Revision 1: my-app:v1
  Revision 2: my-app:v2  ← current (buggy)

Rollback:
  kubectl rollout undo deployment/my-app
  → Reverts to Revision 1 (my-app:v1)

Rollback to specific revision:
  kubectl rollout undo deployment/my-app --to-revision=1
```

---

## Internal Working

```
What happens when you run: kubectl apply -f deployment.yaml

1. kubectl sends HTTP request to API Server
2. API Server authenticates and authorizes the request
3. API Server validates the manifest
4. API Server stores the Deployment object in etcd
5. Deployment Controller detects new Deployment
6. Deployment Controller creates a ReplicaSet
7. ReplicaSet Controller detects new ReplicaSet
8. ReplicaSet Controller creates Pod objects
9. Scheduler detects unscheduled Pods
10. Scheduler assigns Pods to Nodes (binding)
11. kubelet on assigned Node detects new Pod
12. kubelet pulls container image
13. kubelet starts container via Container Runtime
14. kubelet reports Pod status back to API Server
15. Pod status updated in etcd
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                   K8s CONTROL FLOW                                │
│                                                                   │
│  User → kubectl → API Server → etcd (store)                     │
│                       ↓                                          │
│              Controller Manager                                   │
│           (watches for changes)                                   │
│                       ↓                                          │
│               Creates/Updates objects                             │
│                       ↓                                          │
│              Scheduler assigns Pods                               │
│                       ↓                                          │
│              kubelet starts containers                            │
│                       ↓                                          │
│              Reports status → API Server → etcd                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is Kubernetes and why do we need it?

**A:** Kubernetes is an open-source container orchestration platform that automates deployment, scaling, and management of containerized applications. We need it because managing containers manually at scale is impractical — K8s provides self-healing, auto-scaling, rolling updates, service discovery, and declarative configuration, making it the standard for running production workloads.

### Q2: What is the difference between imperative and declarative approaches in Kubernetes?

**A:**
- **Imperative:** You tell K8s exactly what to do step by step (`kubectl create`, `kubectl scale`). Good for quick ad-hoc operations.
- **Declarative:** You describe the desired end state in YAML manifests and `kubectl apply` them. K8s figures out the steps to reach that state. Preferred for production because it's reproducible, version-controlled, and idempotent.

### Q3: Explain the reconciliation loop in Kubernetes.

**A:** The reconciliation loop (control loop) is the core mechanism of K8s controllers. It continuously: (1) Observes the current state of the cluster, (2) Compares it with the desired state stored in etcd, (3) Takes corrective action to bring current state to match desired state. For example, if a Deployment specifies 3 replicas but only 2 are running, the ReplicaSet controller creates 1 more Pod.

### Q4: How does Kubernetes self-healing work?

**A:** Self-healing operates at multiple levels:
- **Container level:** kubelet restarts crashed containers based on restartPolicy
- **Pod level:** Liveness probes detect unhealthy pods and trigger restarts
- **Node level:** If a node becomes unreachable, the node controller marks pods for rescheduling
- **Controller level:** ReplicaSet/Deployment controllers ensure the desired number of pods are always running

### Q5: What is the difference between Kubernetes and Docker Swarm?

**A:**
| Aspect | Kubernetes | Docker Swarm |
|--------|-----------|-------------|
| Complexity | Higher learning curve | Simpler |
| Scaling | More advanced (HPA, VPA, CA) | Basic scaling |
| Networking | CNI plugins, advanced policies | Overlay network |
| Community | Massive ecosystem | Smaller |
| Features | Full-featured (RBAC, CRDs, etc.) | Basic orchestration |
| Production use | Industry standard | Limited adoption |

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using imperative commands in production | Not reproducible, not version controlled | Use declarative YAML with `kubectl apply` |
| Running single replica in production | No high availability | Always use multiple replicas |
| Ignoring resource requests/limits | OOM kills, noisy neighbors | Always set requests and limits |
| Not using health probes | K8s can't detect app failures | Configure liveness and readiness probes |
| Hardcoding configuration | Can't change without rebuild | Use ConfigMaps and Secrets |
| Running as root in containers | Security vulnerability | Use SecurityContext, runAsNonRoot |

---

## Best Practices

1. **Always use declarative manifests** — version control all YAML files (GitOps)
2. **Set resource requests and limits** — enables proper scheduling and prevents resource starvation
3. **Use namespaces** — isolate environments and teams
4. **Implement health probes** — liveness, readiness, and startup probes
5. **Use labels and selectors** — organize and query resources effectively
6. **Pin image versions** — never use `:latest` in production
7. **Enable RBAC** — principle of least privilege
8. **Use Pod Disruption Budgets** — maintain availability during maintenance

---

## Production Considerations

- Multi-node, multi-AZ cluster for high availability
- Separate control plane and worker nodes
- etcd backup strategy
- Monitoring with Prometheus + Grafana
- Centralized logging (EFK/ELK stack)
- Network policies for pod-to-pod security
- Secrets management (external secrets operator, Vault)
- CI/CD pipeline integration (ArgoCD, Flux)

---

## Related Topics

- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
- [03. Kubernetes Objects](./03-kubernetes-objects.md)
- [04. Pods](./04-pods.md)
- [05. Deployments](./05-deployments.md)
