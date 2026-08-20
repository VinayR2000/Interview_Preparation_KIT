# 15. Resources ⭐⭐⭐

---

## Theory

Kubernetes resource management ensures fair allocation, prevents starvation, and enables efficient scheduling through requests and limits.

### CPU

```
CPU is measured in cores (or millicores):
  1 CPU  = 1 core = 1000m (millicores)
  0.5 CPU = 500m
  0.1 CPU = 100m

CPU is compressible:
  - If container exceeds CPU limit → throttled (not killed)
  - Container still runs but gets less CPU time
```

### Memory

```
Memory is measured in bytes:
  128Mi = 128 Mebibytes (128 * 1024 * 1024 bytes)
  1Gi   = 1 Gibibyte
  256M  = 256 Megabytes (256 * 1000 * 1000 bytes)

Memory is incompressible:
  - If container exceeds memory limit → OOMKilled
  - Container is terminated and restarted
```

### Requests

```yaml
resources:
  requests:
    cpu: "250m"        # Guaranteed minimum CPU
    memory: "256Mi"    # Guaranteed minimum memory
```

```
Requests = GUARANTEED resources for scheduling

Scheduler uses requests to decide node placement:
  Node has 4 CPU, 8Gi available
  Pod requests 500m CPU, 1Gi memory
  → Node can fit this pod (4000m-500m=3500m remaining)

Container is guaranteed to get at least its requested resources.
If node has spare capacity, container can use more than requested
(up to its limit).
```

### Limits

```yaml
resources:
  limits:
    cpu: "500m"        # Maximum CPU allowed
    memory: "512Mi"    # Maximum memory allowed
```

```
Limits = MAXIMUM resources allowed

CPU limit exceeded → Throttled (still runs, just slower)
Memory limit exceeded → OOMKilled (container terminated)

Setting strategy:
  requests: Expected/normal usage
  limits:   Maximum tolerable usage

Example:
  Java app normally uses 256Mi, can spike to 512Mi
  requests: 256Mi (scheduling), limits: 512Mi (protection)
```

### QoS Classes

Kubernetes assigns QoS class based on resource configuration:

```
┌────────────────┬─────────────────────────────────────────────────┐
│ QoS Class      │ Criteria                                         │
├────────────────┼─────────────────────────────────────────────────┤
│ Guaranteed     │ requests == limits (for all containers)          │
│                │ Both CPU and memory set                          │
│                │ Highest priority (last to be evicted)            │
├────────────────┼─────────────────────────────────────────────────┤
│ Burstable      │ At least one container has requests or limits   │
│                │ requests ≠ limits                                │
│                │ Medium priority                                  │
├────────────────┼─────────────────────────────────────────────────┤
│ BestEffort     │ No requests or limits set                       │
│                │ Lowest priority (FIRST to be evicted)            │
└────────────────┴─────────────────────────────────────────────────┘
```

### Guaranteed

```yaml
# QoS: Guaranteed (requests == limits)
resources:
  requests:
    cpu: "500m"
    memory: "512Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"

# Use for: Critical production workloads
# Behavior: Last to be evicted under memory pressure
```

### Burstable

```yaml
# QoS: Burstable (requests < limits)
resources:
  requests:
    cpu: "250m"
    memory: "256Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"

# Use for: Most application workloads
# Behavior: Can burst above requests, evicted before Guaranteed
```

### BestEffort

```yaml
# QoS: BestEffort (no requests or limits)
spec:
  containers:
  - name: app
    image: my-app:1.0
    # No resources section!

# Use for: Non-critical batch jobs
# Behavior: First to be evicted, no guaranteed resources
# WARNING: Never use in production for important workloads!
```

### LimitRange

Enforces default and maximum resource constraints per namespace:

```yaml
apiVersion: v1
kind: LimitRange
metadata:
  name: resource-limits
  namespace: production
spec:
  limits:
  - type: Container
    default:          # Default limits if not specified
      cpu: "500m"
      memory: "512Mi"
    defaultRequest:   # Default requests if not specified
      cpu: "250m"
      memory: "256Mi"
    max:              # Maximum allowed
      cpu: "2"
      memory: "4Gi"
    min:              # Minimum required
      cpu: "100m"
      memory: "128Mi"
  - type: Pod
    max:
      cpu: "4"
      memory: "8Gi"
```

### ResourceQuota

Limits total resource consumption per namespace:

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: compute-quota
  namespace: team-a
spec:
  hard:
    requests.cpu: "10"         # Total CPU requests in namespace
    requests.memory: "20Gi"    # Total memory requests
    limits.cpu: "20"           # Total CPU limits
    limits.memory: "40Gi"      # Total memory limits
    pods: "50"                 # Max number of pods
    services: "10"             # Max services
    persistentvolumeclaims: "20"
    secrets: "50"
```

---

## Internal Working

```
Resource Enforcement:

CPU:
  - Linux CFS (Completely Fair Scheduler) manages CPU allocation
  - Request → cpu.shares (proportional allocation)
  - Limit → cpu.quota + cpu.period (hard ceiling)
  - 500m limit → 50ms of CPU per 100ms period

Memory:
  - Linux cgroups memory controller
  - Request → used for scheduling decisions only
  - Limit → memory.limit_in_bytes in cgroup
  - Exceeding limit → kernel OOM killer terminates process

Eviction (when node runs low on memory):
  1. BestEffort pods evicted first
  2. Burstable pods exceeding requests evicted next
  3. Guaranteed pods evicted last (only in extreme cases)
  
Node allocatable:
  Total node resources
  - kube-reserved (kubelet, container runtime)
  - system-reserved (OS processes)
  - eviction-threshold
  = allocatable (available for pods)
```

---

## Diagram

```
┌─────────────────── NODE RESOURCE ALLOCATION ─────────────────┐
│                                                                │
│  Total Node Resources: 8 CPU, 32Gi Memory                    │
│                                                                │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ kube-reserved:     500m CPU, 1Gi memory              │    │
│  │ system-reserved:   500m CPU, 1Gi memory              │    │
│  │ eviction-threshold:         100Mi memory             │    │
│  ├──────────────────────────────────────────────────────┤    │
│  │                                                        │    │
│  │ ALLOCATABLE: 7000m CPU, ~29.9Gi Memory                │    │
│  │                                                        │    │
│  │ ┌─────────────────────────────────────────────┐      │    │
│  │ │ Pod A (Guaranteed): req=500m, lim=500m      │      │    │
│  │ │ Pod B (Burstable):  req=250m, lim=1000m    │      │    │
│  │ │ Pod C (Burstable):  req=500m, lim=2000m    │      │    │
│  │ │ Pod D (BestEffort): req=0, lim=0           │      │    │
│  │ └─────────────────────────────────────────────┘      │    │
│  │                                                        │    │
│  │ Total requested: 1250m (from allocatable 7000m)       │    │
│  │ Remaining schedulable: 5750m                          │    │
│  └──────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between requests and limits?

**A:**
- **Requests:** Guaranteed minimum. Used by scheduler for placement decisions. Container always gets at least this much.
- **Limits:** Maximum ceiling. Container cannot exceed this. CPU is throttled if exceeded; memory causes OOMKill.

Set requests = expected normal usage, limits = maximum burst tolerance.

### Q2: Explain QoS classes and their eviction order.

**A:**
- **Guaranteed (requests=limits):** Highest priority, evicted last. For critical production services.
- **Burstable (requests<limits):** Medium priority. For most workloads that can burst.
- **BestEffort (no resources set):** Lowest priority, evicted first. For non-critical jobs.

Under memory pressure, kubelet evicts BestEffort first → Burstable exceeding requests → Guaranteed last.

### Q3: What happens when a container exceeds its CPU limit vs memory limit?

**A:**
- **CPU exceeded:** Container is throttled (CFS quota). Still runs but gets less CPU time. Not killed. Symptoms: increased latency, slower responses.
- **Memory exceeded:** Container is OOMKilled (terminated). kubelet restarts it based on restartPolicy. Exit code: 137 (128 + SIGKILL signal 9).

### Q4: What is the purpose of LimitRange vs ResourceQuota?

**A:**
- **LimitRange:** Per-container/pod defaults and limits. Ensures individual pods have reasonable resources. Sets default values if omitted.
- **ResourceQuota:** Per-namespace totals. Caps the aggregate resource usage of all pods in a namespace. Prevents one team from consuming all cluster resources.

Both are namespace-scoped and work together for multi-tenant clusters.

### Q5: How do you right-size resource requests and limits?

**A:**
1. Start with estimates based on load testing
2. Deploy with monitoring (Prometheus, metrics-server)
3. Observe actual usage: `kubectl top pods`
4. Use VPA recommendations for right-sizing
5. Set requests = P95 normal usage
6. Set limits = max acceptable burst (2-3x requests for CPU, 1.5-2x for memory)
7. Iterate based on production metrics

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| No resource limits | Pods can consume entire node | Always set limits |
| No resource requests | Bad scheduling decisions | Always set requests |
| Limits too low | Frequent OOMKills/throttling | Monitor and adjust |
| Requests too high | Wasted resources (over-provisioned) | Use VPA for right-sizing |
| BestEffort in production | Pods evicted under pressure | Set requests + limits |
| Same requests/limits for all pods | Inefficient | Size per workload |

---

## Best Practices

1. **Always set both requests and limits** — never deploy without them
2. **Requests = normal usage, limits = burst max** — not arbitrary
3. **Use Guaranteed QoS for critical workloads** (databases, payment services)
4. **Use Burstable for most apps** — allows efficient resource sharing
5. **Set LimitRange** on every namespace — prevents misconfiguration
6. **Set ResourceQuota** for multi-tenant clusters — fair sharing
7. **Monitor and adjust** — use VPA or manual observation
8. **Leave headroom** — don't request 100% of node capacity

---

## Related Topics

- [14. Scheduling](./14-scheduling.md)
- [17. Scaling](./17-scaling.md)
- [28. Troubleshooting](./28-troubleshooting.md)
- [18. Namespaces](./18-namespaces.md)
