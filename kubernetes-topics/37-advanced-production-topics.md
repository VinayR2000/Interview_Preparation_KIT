# 37. Advanced / Production Topics

---

## Theory

Advanced Kubernetes concepts for production-grade clusters covering disruption management, priority scheduling, admission control, service mesh, and multi-cluster architectures.

### Pod Disruption Budget (PDB)

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service-pdb
spec:
  minAvailable: 2              # OR maxUnavailable: 1
  selector:
    matchLabels:
      app: order-service
```

```
PDB guarantees minimum availability during voluntary disruptions:
  - Node drain (kubectl drain)
  - Cluster upgrades
  - Cluster Autoscaler node removal
  
Does NOT protect against involuntary disruptions:
  - Node crash
  - Kernel panic
  - Cloud VM termination

minAvailable: 2    → At least 2 pods must remain running
maxUnavailable: 1  → At most 1 pod can be down at a time

Without PDB: kubectl drain can kill ALL pods simultaneously!
```

### PriorityClass

```yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: critical-service
value: 1000000                 # Higher = more important
globalDefault: false
preemptionPolicy: PreemptLowerPriority
description: "Critical production services"

---
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: batch-low
value: 100
preemptionPolicy: Never        # Don't preempt others
description: "Low priority batch jobs"
```

```
Priority levels:
  system-cluster-critical: 2000000000 (K8s system)
  system-node-critical:    2000001000 (node-level)
  critical-service:        1000000    (your critical apps)
  normal:                  0          (default)
  batch-low:               100        (batch/background)

Usage:
  spec:
    priorityClassName: critical-service
```

### Preemption

```
Preemption: Evict lower-priority pods to schedule higher-priority pods

Flow:
  1. High-priority pod can't be scheduled (no capacity)
  2. Scheduler finds node where evicting low-priority pods makes room
  3. Low-priority pods evicted (graceful termination)
  4. High-priority pod scheduled on freed node

preemptionPolicy:
  PreemptLowerPriority: Can evict lower-priority pods (default)
  Never: Never evict others (wait for capacity)
```

### Topology Spread Constraints

```yaml
spec:
  topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        app: order-service
  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway
    labelSelector:
      matchLabels:
        app: order-service
```

```
Ensures even pod distribution:
  3 zones, 6 pods → 2 per zone (maxSkew: 1)
  
  Zone A: [pod][pod]     ← 2
  Zone B: [pod][pod]     ← 2
  Zone C: [pod][pod]     ← 2
  
  If Zone C goes down → reschedule maintains skew ≤ 1
```

### Resource Quotas

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: production-quota
  namespace: production
spec:
  hard:
    requests.cpu: "50"
    requests.memory: "100Gi"
    limits.cpu: "100"
    limits.memory: "200Gi"
    pods: "200"
    services.loadbalancers: "5"
    persistentvolumeclaims: "50"
    count/deployments.apps: "50"
```

### Admission Controllers

```
Admission Controllers: Intercept API requests before persistence

Built-in:
  - LimitRanger: Enforce LimitRange defaults
  - ResourceQuota: Enforce namespace quotas
  - PodSecurity: Enforce pod security standards
  - MutatingAdmissionWebhook: Custom mutations
  - ValidatingAdmissionWebhook: Custom validations

Flow:
  API Request → Authentication → Authorization →
  Mutating Admission → Validating Admission → etcd
```

### Mutating Webhooks

```yaml
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: inject-sidecar
webhooks:
- name: inject-sidecar.example.com
  clientConfig:
    service:
      name: sidecar-injector
      namespace: system
      path: /inject
  rules:
  - operations: ["CREATE"]
    apiGroups: [""]
    resources: ["pods"]
  namespaceSelector:
    matchLabels:
      sidecar-injection: enabled
```

```
Mutating webhooks MODIFY requests:
  - Inject sidecar containers (Istio, logging)
  - Add default labels/annotations
  - Set resource defaults
  - Add environment variables
```

### Validating Webhooks

```
Validating webhooks ACCEPT or REJECT requests:
  - Enforce image registry policies (only pull from approved registries)
  - Require resource limits on all pods
  - Prevent privileged containers
  - Enforce naming conventions
  - Block specific labels/annotations

Tools: OPA Gatekeeper, Kyverno
```

### Service Mesh

```
Service Mesh: Infrastructure layer for service-to-service communication

Provides:
  - mTLS (mutual TLS between all services)
  - Traffic management (retries, timeouts, circuit breaking)
  - Observability (automatic metrics, traces)
  - Traffic splitting (canary, A/B testing)
  - Access control (authorization policies)

Architecture:
  Data plane: Sidecar proxies (envoy) in every pod
  Control plane: Manages proxy configuration

┌────── Pod ──────┐
│ ┌────┐ ┌─────┐ │
│ │App │ │Envoy│ │  ← Sidecar proxy
│ └────┘ └─────┘ │
└─────────────────┘
    ↕ mTLS ↕
┌────── Pod ──────┐
│ ┌────┐ ┌─────┐ │
│ │App │ │Envoy│ │
│ └────┘ └─────┘ │
└─────────────────┘
```

### Istio

```
Istio: Most popular service mesh

Components:
  - istiod: Control plane (pilot + citadel + galley)
  - Envoy: Sidecar proxy (data plane)

Features:
  - Automatic mTLS
  - VirtualService: Traffic routing rules
  - DestinationRule: Load balancing, circuit breaking
  - AuthorizationPolicy: Service-level access control
  - RequestAuthentication: JWT validation

Use case: Large microservice architectures (50+ services)
Overhead: ~100MB memory per sidecar, adds latency (~1ms)
```

### Linkerd

```
Linkerd: Lightweight service mesh

vs Istio:
  - Simpler (less config, faster setup)
  - Lower resource overhead
  - Rust-based proxy (not Envoy)
  - Fewer features but easier to operate
  
Choose Linkerd: Smaller teams, simpler needs
Choose Istio: Complex traffic management, large scale
```

### mTLS

```
mTLS (Mutual TLS):
  - Both client and server present certificates
  - All pod-to-pod traffic encrypted
  - Identity verified (not just IP-based)
  - Automatic with service mesh (zero app changes)

Without mesh: NetworkPolicy (L3/L4)
With mesh: mTLS + authorization policies (L7)
```

### Sidecar Pattern

```
Sidecar: Helper container running alongside main container

Common sidecars:
  - Envoy (service mesh proxy)
  - Fluent Bit (log forwarding)
  - Vault Agent (secret injection)
  - Config reloader (watch for config changes)

K8s 1.28+ native sidecar support:
  initContainers with restartPolicy: Always
  Guaranteed to start before and stop after main container
```

### Gateway API

```
Gateway API: Next-generation Ingress (more expressive)

Resources:
  GatewayClass: Infrastructure provider (like IngressClass)
  Gateway: Network gateway instance (like Ingress Controller)
  HTTPRoute: HTTP routing rules (replaces Ingress)
  TCPRoute/TLSRoute: L4 routing

Benefits over Ingress:
  - Role-based: Platform team manages Gateway, dev team manages Routes
  - Multi-protocol: HTTP, gRPC, TCP
  - More expressive routing (header matching, weight-based)
  - Standard cross-implementation behavior
```

### Multi-Cluster Kubernetes

```
Multi-cluster patterns:
  1. Active-Active: Both clusters serve traffic
  2. Active-Passive: Standby for disaster recovery
  3. Geographic: Different regions for latency

Tools:
  - Kubernetes Federation (KubeFed)
  - Istio multi-cluster
  - Cilium ClusterMesh
  - AWS EKS multi-cluster with Route53

Use cases:
  - Disaster recovery
  - Regional deployment (data sovereignty)
  - Blast radius reduction
  - Different security boundaries
```

### Cluster Federation

```
Federation: Manage multiple clusters as one

Features:
  - Single API to manage workloads across clusters
  - Federated Deployments (spread across clusters)
  - Federated Services (multi-cluster service discovery)
  - Policy-based placement

Tools:
  - KubeFed (Kubernetes Federation v2)
  - Admiralty (multi-cluster scheduling)
  - Liqo (virtual node approach)
```

### Disaster Recovery

```
DR for Kubernetes:

RPO/RTO targets determine strategy:
  RPO < 1 min: Active-Active (both clusters live)
  RPO < 15 min: Async replication (hot standby)
  RPO < 1 hour: Scheduled backups (cold standby)

Components to recover:
  1. Cluster state: etcd backup or GitOps rebuild
  2. Application data: Database replication/backups
  3. Persistent volumes: Snapshot replication
  4. Configuration: Git (GitOps) — always recoverable
  5. Secrets: External secret manager (cross-region)

DR drill checklist:
  □ Restore etcd from backup
  □ Rebuild cluster from GitOps repo
  □ Restore databases from backup/replica
  □ Verify DNS failover
  □ Validate application functionality
  □ Measure actual RTO
```

---

## Interview Questions

### Q1: What is a PodDisruptionBudget and why is it important?

**A:** PDB guarantees minimum pod availability during voluntary disruptions (node drain, upgrades, autoscaler). Without PDB, `kubectl drain` can kill all pods of a deployment simultaneously. With PDB `minAvailable: 2`, K8s ensures at least 2 pods remain running — it blocks the drain if removing a pod would violate the budget. Critical for zero-downtime maintenance.

### Q2: How does pod preemption work?

**A:** When a high-priority pod can't be scheduled (no capacity), the scheduler looks for a node where evicting lower-priority pods would make room. It selects the node with minimal disruption (fewest evictions, lowest-priority victims). Low-priority pods get graceful termination, then the high-priority pod is scheduled. PDBs are NOT respected during preemption (it's considered a priority override).

### Q3: What is a service mesh and when do you need one?

**A:** Service mesh is an infrastructure layer (sidecar proxies) handling service-to-service communication. Provides: mTLS, observability, traffic management, circuit breaking. Need it when: 50+ microservices, require mTLS everywhere, need traffic splitting for canary, want consistent observability without app changes. Don't need it for: small clusters (<10 services), simple architectures.

### Q4: How would you design a multi-cluster disaster recovery strategy?

**A:**
1. **GitOps:** All manifests in Git — rebuild cluster from scratch
2. **Database:** Multi-AZ primary + cross-region read replica
3. **etcd:** Regular backups to S3 (cross-region)
4. **DNS:** Route53 health checks → automatic failover
5. **Secrets:** AWS Secrets Manager (cross-region replication)
6. **Test regularly:** Monthly DR drills, measure actual RTO

### Q5: What are admission webhooks and give a real use case?

**A:** Admission webhooks intercept API requests before persistence:
- **Mutating:** Inject sidecars (Istio auto-injects envoy), add default labels, set resource defaults
- **Validating:** Enforce policies — reject images from untrusted registries, require resource limits, block privileged containers

Real use case: OPA Gatekeeper validating that all Deployments have resource limits, readiness probes, and use approved image registries.

---

## Best Practices

1. **Use PDBs on all production workloads** — protect during maintenance
2. **Define PriorityClasses** — ensure critical services survive resource pressure
3. **Use topology spread** — even distribution across failure domains
4. **Evaluate service mesh need** — adds complexity, only when justified
5. **Use Gateway API** over Ingress for new clusters (more expressive)
6. **Plan DR from day one** — test regularly, automate failover
7. **Admission policies** — enforce standards (OPA Gatekeeper/Kyverno)
8. **Multi-cluster for critical services** — reduce blast radius

---

## Related Topics

- [36. Production Architecture](./36-production-architecture.md)
- [14. Scheduling](./14-scheduling.md)
- [19. RBAC & Security](./19-rbac-and-security.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
