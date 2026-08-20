# 24. Kubernetes Internals ⭐⭐⭐

---

## Theory

Understanding how K8s components work together internally helps debug issues and design better architectures.

### API Server

```
API Server is the brain of K8s — every operation goes through it.

Request Pipeline:
  1. TLS Termination
  2. Authentication (who are you?)
  3. Authorization (RBAC — what can you do?)
  4. Admission Control:
     - Mutating webhooks (modify request)
     - Validating webhooks (accept/reject)
  5. Validation (schema check)
  6. etcd persistence
  7. Response

Watch Mechanism:
  - Controllers open long-lived watch connections
  - API Server streams changes (events) to watchers
  - Efficient: no polling, immediate notification
  - Uses resourceVersion for consistency
```

### etcd

```
etcd internals:
  - Raft consensus protocol (leader + followers)
  - Strong consistency (linearizable reads)
  - All writes go through leader
  - Leader replicates to majority before acknowledging
  - Key-value with prefix-based hierarchy

Key structure:
  /registry/pods/default/my-pod
  /registry/deployments/production/order-service
  /registry/services/production/order-service
  /registry/secrets/production/db-secret

Performance:
  - Optimized for small values (<1.5MB per key)
  - Sequential writes (append-only log)
  - Periodic compaction (remove old revisions)
  - Snapshot for backup
```

### Scheduler

```
Scheduler internals:

Scheduling Queue:
  activeQ:           Pods ready to be scheduled
  backoffQ:          Pods that failed scheduling (exponential backoff)
  unschedulableQ:    Pods that can't be scheduled (waiting for conditions)

Scheduling Cycle (per pod):
  1. Pop pod from activeQ
  2. Filter plugins: eliminate unsuitable nodes
  3. Score plugins: rank remaining nodes (0-100)
  4. Normalize scores
  5. Select highest score (random tiebreaker)
  6. Assume binding (optimistic, parallel)
  7. Bind (write to API Server)

Preemption:
  If no node fits → try to evict lower-priority pods
  Find node where evicting pods makes room
  Evict pods → schedule high-priority pod
```

### Controller Manager

```
Controller Manager runs ~30 controllers in one process:

Each controller:
  1. Watch: Subscribe to relevant resource changes
  2. Queue: Events placed in work queue (rate-limited)
  3. Reconcile: Process each item (current state → desired state)
  4. Update: Write changes back via API Server

Key design patterns:
  - Level-triggered (not edge-triggered): handles state, not events
  - Idempotent: safe to run reconcile multiple times
  - Work queue with rate limiting: prevents API Server overload
  - Shared informer cache: reduces API Server load
```

### kubelet

```
kubelet internals:

Pod Lifecycle Manager:
  1. Watch API Server for Pods assigned to this node
  2. Use PLEG (Pod Lifecycle Event Generator) to detect changes
  3. Sync loop: compare desired pods vs running pods
  4. Call CRI (Container Runtime Interface) for container operations
  5. Manage volumes (CSI calls)
  6. Execute health probes
  7. Report status to API Server

PLEG (Pod Lifecycle Event Generator):
  - Periodically lists all containers (every 1s)
  - Compares with previous state
  - Generates events: ContainerStarted, ContainerDied, etc.
  - Triggers sync for affected pods

Node Status Reporting:
  - Reports every 10s (node-status-update-frequency)
  - CPU, memory, disk, PIDs available
  - Node conditions: Ready, MemoryPressure, DiskPressure
```

### kube-proxy

```
kube-proxy internals:

Watches: Services, Endpoints, EndpointSlices
Action: Programs network rules on the node

iptables mode (default):
  For each Service:
    - KUBE-SERVICES chain: match ClusterIP:port → jump to service chain
    - KUBE-SVC-xxx chain: probability-based jump to endpoint chains
    - KUBE-SEP-xxx chain: DNAT to pod IP:port
  
  Rule count = O(services × endpoints)
  Every service change → full iptables rewrite

IPVS mode:
  - Creates virtual server per Service (ClusterIP)
  - Adds real servers (pod IPs) to virtual server
  - Kernel IPVS handles load balancing
  - O(1) connection routing regardless of service count
  - Supports: rr, lc, dh, sh, sed, nq algorithms
```

### Controllers

```
Controller Types:

Built-in (kube-controller-manager):
  - Deployment Controller
  - ReplicaSet Controller
  - StatefulSet Controller
  - DaemonSet Controller
  - Job Controller
  - CronJob Controller
  - Node Controller
  - Service Controller
  - Endpoint Controller
  - Namespace Controller
  - ServiceAccount Controller
  - PV/PVC Controller
  - TTL Controller

Custom (CRD + Operator):
  - User-defined controllers
  - Watch custom resources
  - Implement custom reconciliation logic
```

### ReplicaSet Controller

```
ReplicaSet Controller reconciliation:

  current = count pods with matching labels
  desired = rs.spec.replicas

  if current < desired:
    create (desired - current) pods
  if current > desired:
    delete (current - desired) pods (select by age/status)
  if current == desired:
    no action

Pod selection: By label selector (NOT by ownership alone)
Ownership: OwnerReference in pod metadata
```

### Deployment Controller

```
Deployment Controller reconciliation:

1. Detect spec change (new pod template hash)
2. Create new ReplicaSet (hash based on pod template)
3. Scale up new RS (respecting maxSurge)
4. Wait for new pods to be Ready
5. Scale down old RS (respecting maxUnavailable)
6. Repeat until old RS = 0, new RS = desired
7. Update Deployment status

Rollback: Points Deployment to old RS template
Pause/Resume: Stops/starts the reconciliation
```

### Service Controller

```
Service Controller (for LoadBalancer type):

1. Detect Service type: LoadBalancer
2. Call cloud provider API (via Cloud Controller Manager)
3. Create external load balancer
4. Get external IP/hostname
5. Update Service status.loadBalancer.ingress
6. On Service delete: Remove cloud load balancer
```

### Reconciliation Loop

```
The reconciliation loop is the CORE pattern of K8s:

while true:
  currentState = observe()
  desiredState = readFromEtcd()
  
  if currentState != desiredState:
    actions = plan(currentState, desiredState)
    execute(actions)
  
  sleep(syncPeriod)

Properties:
  - Level-triggered: Makes decisions based on current state
  - Self-healing: Constantly corrects drift
  - Idempotent: Safe to run multiple times
  - Eventually consistent: State converges over time
```

### Control Plane Flow

```
Complete flow: User creates Deployment with 3 replicas

1. kubectl → API Server: POST /apis/apps/v1/deployments
2. API Server → etcd: Store Deployment object
3. Deployment Controller (watching Deployments):
   - Detects new Deployment
   - Computes pod template hash
   - Creates ReplicaSet → API Server → etcd
4. ReplicaSet Controller (watching ReplicaSets):
   - Detects new RS with 0 pods, desired=3
   - Creates 3 Pod objects → API Server → etcd
5. Scheduler (watching unscheduled Pods):
   - For each pod: filter → score → bind
   - Updates pod.spec.nodeName → API Server → etcd
6. kubelet on each node (watching assigned Pods):
   - Detects new pod assignment
   - Calls CRI: createPodSandbox, createContainer, startContainer
   - Starts probes
   - Reports status → API Server → etcd
7. Endpoints Controller (watching Pods + Services):
   - Pod Ready → add to Service endpoints
8. kube-proxy (watching Endpoints):
   - Updates iptables/IPVS rules
9. Traffic can now reach the pods!
```

---

## Diagram

```
┌──────────────── CONTROL PLANE FLOW ──────────────────────────┐
│                                                                │
│  kubectl apply                                                │
│       │                                                       │
│       ▼                                                       │
│  ┌──────────┐    ┌──────┐                                   │
│  │API Server│───→│ etcd │ (stores all objects)               │
│  └────┬─────┘    └──────┘                                   │
│       │                                                       │
│       │ (watch events)                                        │
│       ├────────────────────────────────────┐                 │
│       ▼                                    ▼                 │
│  ┌──────────────┐              ┌──────────────────┐         │
│  │  Deployment  │              │    Scheduler     │         │
│  │  Controller  │              │  (assigns nodes) │         │
│  └──────┬───────┘              └────────┬─────────┘         │
│         │ creates RS                     │ binds pod         │
│         ▼                                │                   │
│  ┌──────────────┐                        │                   │
│  │  ReplicaSet  │                        │                   │
│  │  Controller  │                        │                   │
│  └──────┬───────┘                        │                   │
│         │ creates Pods                   │                   │
│         ▼                                ▼                   │
│  ┌─────────────────────────────────────────────────┐        │
│  │  kubelet (on worker node)                        │        │
│  │  - Pulls image                                   │        │
│  │  - Starts container                             │        │
│  │  - Runs probes                                  │        │
│  │  - Reports status                              │        │
│  └─────────────────────────────────────────────────┘        │
└────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: Walk through what happens internally when you run `kubectl apply -f deployment.yaml`.

**A:** 
1. kubectl serializes YAML, sends HTTPS POST to API Server
2. API Server: authenticates → authorizes (RBAC) → runs admission webhooks → validates → stores in etcd
3. Deployment Controller: watches Deployments, detects new one, creates ReplicaSet
4. ReplicaSet Controller: watches RSs, creates Pod objects
5. Scheduler: watches unscheduled Pods, runs filter/score, binds Pod to Node
6. kubelet: watches Pods on its node, calls CRI to start containers, runs probes, reports status
7. Endpoints Controller: adds Ready pod to Service endpoints
8. kube-proxy: updates iptables rules for routing

### Q2: What is the difference between level-triggered and edge-triggered reconciliation?

**A:** 
- **Edge-triggered:** React to individual events (pod created, pod deleted). Can miss events if controller restarts.
- **Level-triggered (K8s approach):** Make decisions based on current state, not on events. "I see 2 pods but want 3, create 1." Handles missed events naturally since it always looks at actual state. More robust.

### Q3: How does the scheduler handle conflicts (two pods need the same last slot)?

**A:** The scheduler processes one pod at a time sequentially from its queue. First pod gets bound, second pod finds fewer resources available on re-evaluation. If no node fits the second pod, it goes to backoffQ/unschedulableQ and retries later. This is why the scheduler is single-threaded for a scheduling queue (parallelism only in bind phase).

### Q4: What happens to running pods when the control plane goes down?

**A:** Pods continue running! kubelet on worker nodes operates independently. It keeps containers running, executes probes, and restarts crashed containers. What stops working: no new pods can be scheduled, no scaling, no updates, no self-healing if a NODE fails (can't reschedule to other nodes). Existing pods on healthy nodes continue normally.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Thinking control plane down = pods down | Causes unnecessary panic | Understand: pods survive control plane outage |
| Not understanding watch mechanism | Debugging reconciliation issues | Learn how informers/watches work |
| Ignoring API Server latency | Controllers slow to reconcile | Monitor API Server response times |
| Too many objects in one namespace | etcd/API Server performance | Distribute across namespaces |

---

## Best Practices

1. **Monitor control plane components** — API Server latency, etcd disk I/O, scheduler queue
2. **Understand reconciliation** — helps debug "why isn't my change taking effect"
3. **Watch events** — `kubectl get events` shows the controller actions
4. **Use resource versions** — for conflict resolution in controllers
5. **Limit watch scope** — namespace-scoped watches reduce API Server load

---

## Related Topics

- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
- [25. etcd](./25-etcd.md)
- [32. CRD & Operators](./32-crd-and-operators.md)
- [28. Troubleshooting](./28-troubleshooting.md)
