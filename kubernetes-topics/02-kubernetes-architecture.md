# 2. Kubernetes Architecture ⭐⭐⭐

---

## Theory

Kubernetes follows a **master-worker architecture** (now called control plane and data plane). The control plane makes global decisions, while worker nodes run the actual application workloads.

### Cluster

A Kubernetes cluster consists of a set of machines (nodes) that run containerized applications:

```
Cluster = Control Plane Node(s) + Worker Node(s)

Minimum Production Setup:
  - 3 Control Plane nodes (HA)
  - 3+ Worker nodes (across availability zones)

Development Setup:
  - 1 Control Plane node (minikube, kind)
  - 1-2 Worker nodes
```

### Control Plane

The control plane manages the overall cluster state and makes scheduling decisions:

```
┌─────────────────────────── CONTROL PLANE ─────────────────────────┐
│                                                                     │
│  ┌──────────────┐  ┌────────┐  ┌───────────┐  ┌──────────────┐  │
│  │  API Server  │  │  etcd  │  │ Scheduler │  │  Controller  │  │
│  │  (kube-      │  │        │  │ (kube-    │  │  Manager     │  │
│  │   apiserver) │  │        │  │  scheduler)│  │  (kube-cm)   │  │
│  └──────────────┘  └────────┘  └───────────┘  └──────────────┘  │
│                                                                     │
│  ┌────────────────────────┐                                        │
│  │  Cloud Controller Mgr  │  (only in cloud environments)         │
│  └────────────────────────┘                                        │
└─────────────────────────────────────────────────────────────────────┘
```

### API Server (kube-apiserver)

The **single entry point** for all cluster operations. Every component communicates through the API Server.

```
Responsibilities:
  1. REST API endpoint (HTTPS)
  2. Authentication & Authorization (RBAC)
  3. Admission Control (mutating/validating webhooks)
  4. Validation of objects
  5. etcd gateway (only component that talks to etcd)
  6. Watch mechanism for controllers

Flow:
  kubectl/client → API Server → Authenticate → Authorize → 
  Admission Control → Validate → Store in etcd
```

```
API Server Request Processing:
  
  1. Authentication: Who are you?
     - Client certificates
     - Bearer tokens
     - Service account tokens
  
  2. Authorization: What can you do?
     - RBAC (Role-Based Access Control)
     - Node authorization
     - Webhook authorization
  
  3. Admission Control: Should we allow/modify this?
     - Mutating webhooks (modify request)
     - Validating webhooks (accept/reject)
     - Built-in: LimitRanger, ResourceQuota
  
  4. Validation: Is the object valid?
     - Schema validation
     - Required fields check
  
  5. Persistence: Store in etcd
```

### etcd

A distributed, consistent key-value store that stores ALL cluster state:

```
etcd stores:
  - All Kubernetes objects (Pods, Services, Deployments, etc.)
  - Cluster configuration
  - Secrets (encrypted at rest)
  - Current state and desired state

Properties:
  - Strongly consistent (Raft consensus)
  - Distributed (3 or 5 node cluster for HA)
  - Watch support (notify on changes)
  - Key-value with prefix-based queries

Key structure: /registry/{resource_type}/{namespace}/{name}
  Example: /registry/pods/default/my-app-abc123
```

### Scheduler (kube-scheduler)

Assigns Pods to Nodes based on constraints and resource availability:

```
Scheduling Process:

1. Filter Phase (find feasible nodes):
   - Node has enough resources?
   - Node matches nodeSelector/affinity?
   - Node has required taints/tolerations?
   - Pod port available on node?
   - Volume can be mounted on node?

2. Score Phase (rank feasible nodes):
   - Resource balance (spread CPU/memory usage)
   - Pod affinity/anti-affinity
   - Topology spread constraints
   - Image locality (image already cached?)

3. Bind Phase:
   - Select highest-scoring node
   - Update Pod's .spec.nodeName in API Server

Example:
  Pod needs: 2 CPU, 4Gi memory, SSD storage
  Node A: 4 CPU free, 8Gi free, has SSD → Score: 80
  Node B: 1 CPU free, 2Gi free, has SSD → Filtered out (insufficient)
  Node C: 8 CPU free, 16Gi free, no SSD → Filtered out (no SSD)
  → Pod scheduled on Node A
```

### Controller Manager (kube-controller-manager)

Runs multiple controllers that watch for state changes and act to reconcile:

```
Key Controllers:
  ┌─────────────────────────────────────────────────────┐
  │ Controller              │ Responsibility             │
  ├─────────────────────────┼───────────────────────────┤
  │ Deployment Controller   │ Manages ReplicaSets        │
  │ ReplicaSet Controller   │ Maintains Pod count        │
  │ Node Controller         │ Monitors node health       │
  │ Job Controller          │ Manages batch jobs         │
  │ Service Controller      │ Manages load balancers     │
  │ Endpoint Controller     │ Populates Service endpoints│
  │ Namespace Controller    │ Handles namespace deletion │
  │ ServiceAccount Ctrl     │ Creates default SA         │
  └─────────────────────────┴───────────────────────────┘
```

### Cloud Controller Manager

Integrates with cloud provider APIs (AWS, GCP, Azure):

```
Responsibilities:
  - Node Controller: Detect if cloud VM is deleted
  - Route Controller: Configure cloud network routes
  - Service Controller: Create cloud load balancers
  - Volume Controller: Attach/detach cloud storage

Example (AWS):
  Service type: LoadBalancer → Creates AWS ELB/ALB
  PersistentVolume → Creates EBS volume
  Node unhealthy → Checks EC2 instance status
```

---

### Worker Node

Runs the actual application workloads (Pods):

```
┌────────────────────── WORKER NODE ──────────────────────────┐
│                                                               │
│  ┌──────────┐  ┌────────────┐  ┌────────────────────────┐  │
│  │  kubelet │  │ kube-proxy │  │   Container Runtime    │  │
│  │          │  │            │  │   (containerd/CRI-O)   │  │
│  └──────────┘  └────────────┘  └────────────────────────┘  │
│                                                               │
│  ┌──── Pod ────┐  ┌──── Pod ────┐  ┌──── Pod ────┐        │
│  │ Container 1 │  │ Container 1 │  │ Container 1 │        │
│  │ Container 2 │  │             │  │ Container 2 │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
└───────────────────────────────────────────────────────────────┘
```

### kubelet

The primary agent on each worker node:

```
kubelet responsibilities:
  1. Register node with API Server
  2. Watch API Server for Pod assignments (to this node)
  3. Pull container images
  4. Start/stop containers via Container Runtime
  5. Execute health probes (liveness, readiness, startup)
  6. Report Pod and Node status to API Server
  7. Manage volumes (mount/unmount)
  8. Handle container logs

kubelet does NOT:
  - Schedule Pods (that's the scheduler's job)
  - Manage Pods not assigned to its node
  - Talk to etcd directly (goes through API Server)
```

### kube-proxy

Maintains network rules on each node for Service abstraction:

```
kube-proxy modes:

1. iptables mode (default):
   - Creates iptables rules for each Service
   - Random selection among backend Pods
   - Fast for small number of services

2. IPVS mode:
   - Uses Linux IPVS (IP Virtual Server)
   - Better performance for large clusters
   - Supports multiple load balancing algorithms
     (round-robin, least-connection, etc.)

3. userspace mode (deprecated):
   - Old, slow, not recommended

Example (iptables):
  Service: my-app (ClusterIP: 10.96.0.100:80)
  Backends: Pod1 (10.244.1.5:8080), Pod2 (10.244.2.3:8080)
  
  iptables rule:
    -A KUBE-SVC-XXXXX -m statistic --mode random --probability 0.5
    -j KUBE-SEP-POD1 (DNAT to 10.244.1.5:8080)
    -A KUBE-SVC-XXXXX
    -j KUBE-SEP-POD2 (DNAT to 10.244.2.3:8080)
```

### Container Runtime

```
Container Runtime Interface (CRI):
  kubelet → CRI API → Container Runtime → Container

Runtimes:
  ┌────────────────────────────────────────────────┐
  │ Runtime      │ Status          │ Notes          │
  ├──────────────┼─────────────────┼────────────────┤
  │ containerd   │ Default (most)  │ Industry std   │
  │ CRI-O       │ Red Hat/OpenShift│ K8s focused    │
  │ Docker       │ Deprecated in   │ Removed in     │
  │              │ K8s 1.20        │ K8s 1.24       │
  └──────────────┴─────────────────┴────────────────┘

containerd:
  - Lightweight container runtime
  - Manages complete container lifecycle
  - Image pull, storage, networking, execution
  - Used by Docker internally (containerd was extracted from Docker)
```

### CRI (Container Runtime Interface)

```
CRI is a plugin interface that allows kubelet to work with any container runtime:

kubelet → CRI gRPC API → containerd/CRI-O

CRI Operations:
  - RunPodSandbox: Create pod network namespace
  - CreateContainer: Create container in pod
  - StartContainer: Start a created container
  - StopContainer: Stop a running container
  - RemoveContainer: Delete a stopped container
  - ListContainers: List all containers
```

---

### Kubernetes Communication

```
Communication Patterns:

1. User → API Server (external):
   kubectl → HTTPS → API Server (port 6443)

2. API Server → etcd (internal):
   API Server → gRPC → etcd (port 2379)

3. API Server → kubelet:
   API Server → HTTPS → kubelet (port 10250)
   (for exec, logs, port-forward)

4. kubelet → API Server:
   kubelet → HTTPS → API Server (watches for pod assignments)

5. kube-proxy → API Server:
   kube-proxy → watches Service/Endpoint changes

6. Pod → Pod:
   Direct IP routing (via CNI network)

7. Pod → Service:
   ClusterIP (virtual IP) → kube-proxy → Pod
```

---

## Internal Working

```
Complete Flow: Deploying an Application

1. User: kubectl apply -f deployment.yaml
2. kubectl: Sends POST to API Server with Deployment spec
3. API Server: 
   - Authenticates (client cert/token)
   - Authorizes (RBAC check)
   - Admission control (webhooks)
   - Validates manifest
   - Stores Deployment in etcd
4. Deployment Controller (watching Deployments):
   - Detects new Deployment
   - Creates ReplicaSet object → stored in etcd
5. ReplicaSet Controller (watching ReplicaSets):
   - Detects new ReplicaSet
   - Creates Pod objects (spec.nodeName empty) → stored in etcd
6. Scheduler (watching Pods with no nodeName):
   - Detects unscheduled Pods
   - Filters + Scores nodes
   - Binds Pod to Node (updates spec.nodeName) → stored in etcd
7. kubelet on target Node (watching Pods assigned to it):
   - Detects new Pod assignment
   - Pulls container image (containerd)
   - Creates pod sandbox (network namespace)
   - Starts containers
   - Starts health probes
   - Reports Pod status → API Server → etcd
8. kube-proxy (watching Services/Endpoints):
   - Updates iptables/IPVS rules for Service routing
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        KUBERNETES ARCHITECTURE                            │
│                                                                           │
│  ┌───────────────────── CONTROL PLANE (Master) ─────────────────────┐   │
│  │                                                                    │   │
│  │  ┌─────────────┐  ┌────────────────┐  ┌──────────────────────┐  │   │
│  │  │ API Server  │←→│     etcd       │  │  Controller Manager  │  │   │
│  │  │ (Gateway)   │  │ (State Store)  │  │  (Reconciliation)    │  │   │
│  │  └──────┬──────┘  └────────────────┘  └──────────────────────┘  │   │
│  │         │                                                         │   │
│  │  ┌──────┴──────┐  ┌────────────────────────────────────────┐    │   │
│  │  │  Scheduler  │  │  Cloud Controller Manager (optional)   │    │   │
│  │  │ (Placement) │  │  (AWS/GCP/Azure integration)           │    │   │
│  │  └─────────────┘  └────────────────────────────────────────┘    │   │
│  └───────────────────────────────────────────────────────────────────┘   │
│                              │                                            │
│              ┌───────────────┼───────────────┐                           │
│              ↓               ↓               ↓                           │
│  ┌──── Worker Node 1 ────┐  ┌──── Worker Node 2 ────┐                  │
│  │                        │  │                        │                  │
│  │  ┌────────────────┐   │  │  ┌────────────────┐   │                  │
│  │  │    kubelet     │   │  │  │    kubelet     │   │                  │
│  │  │ (Node Agent)   │   │  │  │ (Node Agent)   │   │                  │
│  │  └────────────────┘   │  │  └────────────────┘   │                  │
│  │                        │  │                        │                  │
│  │  ┌────────────────┐   │  │  ┌────────────────┐   │                  │
│  │  │  kube-proxy    │   │  │  │  kube-proxy    │   │                  │
│  │  │ (Networking)   │   │  │  │ (Networking)   │   │                  │
│  │  └────────────────┘   │  │  └────────────────┘   │                  │
│  │                        │  │                        │                  │
│  │  ┌────────────────┐   │  │  ┌────────────────┐   │                  │
│  │  │  Container     │   │  │  │  Container     │   │                  │
│  │  │  Runtime       │   │  │  │  Runtime       │   │                  │
│  │  │  (containerd)  │   │  │  │  (containerd)  │   │                  │
│  │  └────────────────┘   │  │  └────────────────┘   │                  │
│  │                        │  │                        │                  │
│  │  [Pod][Pod][Pod]       │  │  [Pod][Pod][Pod]       │                  │
│  └────────────────────────┘  └────────────────────────┘                  │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: Explain the Kubernetes architecture and the role of each component.

**A:** Kubernetes has a master-worker architecture:
- **Control Plane:** API Server (entry point, authentication, authorization), etcd (distributed state store), Scheduler (assigns pods to nodes), Controller Manager (runs reconciliation loops)
- **Worker Nodes:** kubelet (manages pods on the node), kube-proxy (network routing for services), Container Runtime (runs containers via CRI)

All communication goes through the API Server. etcd is the single source of truth. Controllers watch for changes and reconcile state.

### Q2: What happens when a node goes down?

**A:** 
1. kubelet on the failed node stops sending heartbeats to API Server
2. Node Controller (in Controller Manager) detects missed heartbeats
3. After `node-monitor-grace-period` (default 40s), node marked `NotReady`
4. After `pod-eviction-timeout` (default 5min), pods are evicted
5. ReplicaSet controller detects fewer pods than desired
6. New pods are scheduled on healthy nodes
7. Services automatically route traffic away from the failed node

### Q3: Why does only the API Server communicate with etcd?

**A:** This is a deliberate design decision for:
- **Security:** Single point of access control (reduces attack surface)
- **Consistency:** Only API Server validates objects before writing
- **Abstraction:** Other components don't need to know storage details
- **Watch mechanism:** API Server provides efficient watch/notify to all controllers
- **Audit:** All changes go through a single gateway

### Q4: What is the difference between kube-proxy iptables and IPVS mode?

**A:**
- **iptables:** Creates rules for each Service. Simple, default. Performance degrades with many services (O(n) rule evaluation). Random load balancing only.
- **IPVS:** Uses kernel-level IP Virtual Server. O(1) lookup regardless of service count. Supports round-robin, least-connection, destination-hashing. Better for large clusters (1000+ services).

### Q5: Can Kubernetes run without etcd?

**A:** No. etcd is the only state store for the entire cluster. Without it, K8s cannot store or retrieve any object (pods, services, secrets, etc.). This is why etcd HA (3 or 5 nodes with Raft consensus) and regular backups are critical in production.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Single control plane node | Single point of failure | Use 3+ control plane nodes |
| Not backing up etcd | Cluster state can be lost | Regular etcd snapshots |
| Running workloads on control plane | Security risk, resource contention | Taint control plane nodes |
| Ignoring kubelet resource reservations | System processes get OOM killed | Configure system-reserved |
| Using Docker as runtime in K8s 1.24+ | Not supported | Use containerd or CRI-O |

---

## Best Practices

1. **High Availability:** 3+ control plane nodes across availability zones
2. **etcd:** Dedicated nodes with SSD storage, regular backups
3. **Node sizing:** Right-size worker nodes for your workload patterns
4. **Separation:** Control plane nodes should not run application workloads
5. **Monitoring:** Monitor all components (API Server latency, etcd health, scheduler)
6. **Upgrade strategy:** Rolling upgrades, one minor version at a time
7. **Network:** Use IPVS mode for large clusters (500+ services)
8. **Security:** Enable audit logging on API Server

---

## Related Topics

- [01. Kubernetes Fundamentals](./01-kubernetes-fundamentals.md)
- [24. Kubernetes Internals](./24-kubernetes-internals.md)
- [25. etcd](./25-etcd.md)
- [26. Container Runtime](./26-container-runtime.md)
