# 14. Scheduling ⭐⭐⭐

---

## Theory

The **kube-scheduler** assigns Pods to Nodes based on resource requirements, constraints, affinity rules, and policies.

### kube-scheduler

```
Scheduling Process:
  1. Watch for unscheduled Pods (spec.nodeName is empty)
  2. Filter: Eliminate unsuitable nodes
  3. Score: Rank remaining nodes
  4. Bind: Assign Pod to highest-scoring node

Filter plugins (predicates):
  - NodeResourcesFit: Does node have enough CPU/memory?
  - NodeName: Does Pod specify a particular node?
  - NodeSelector: Does node have required labels?
  - TaintToleration: Does Pod tolerate node taints?
  - NodeAffinity: Does node match affinity rules?
  - PodTopologySpread: Would this violate spread constraints?
  - VolumeBinding: Can required volumes be bound on this node?

Score plugins (priorities):
  - NodeResourcesBalancedAllocation: Balanced CPU/mem usage
  - ImageLocality: Image already cached on node?
  - InterPodAffinity: Matches pod affinity preferences
  - TaintToleration: Fewer unmatched taints = higher score
```

### Node Selection

```
Methods to control scheduling (from simple to complex):

1. nodeName (direct assignment, bypasses scheduler)
2. nodeSelector (label matching)
3. nodeAffinity (flexible rules with operators)
4. Pod Affinity/Anti-Affinity (relative to other pods)
5. Taints & Tolerations (node repels pods)
6. Topology Spread Constraints (even distribution)
```

### nodeSelector

```yaml
# Simple label matching
spec:
  nodeSelector:
    disktype: ssd
    zone: us-east-1a
```

```
nodeSelector: Pod only scheduled on nodes with ALL matching labels

kubectl label nodes node-1 disktype=ssd
kubectl label nodes node-1 zone=us-east-1a

Limitations:
  - Only equality matching (key=value)
  - No OR conditions
  - No "prefer but don't require"
  
Use nodeAffinity for more complex requirements.
```

### nodeAffinity

```yaml
spec:
  affinity:
    nodeAffinity:
      # HARD constraint (must match)
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: topology.kubernetes.io/zone
            operator: In
            values:
            - us-east-1a
            - us-east-1b
      # SOFT constraint (prefer but not required)
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 80
        preference:
          matchExpressions:
          - key: disktype
            operator: In
            values:
            - ssd
```

```
Operators:
  In:           value must be in list
  NotIn:        value must NOT be in list
  Exists:       key must exist (any value)
  DoesNotExist: key must NOT exist
  Gt:           value greater than
  Lt:           value less than

"IgnoredDuringExecution" = if node labels change after scheduling,
pod is NOT evicted (stays where it is)
```

### Pod Affinity

```yaml
# Schedule near other pods with specific labels
spec:
  affinity:
    podAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchLabels:
            app: cache
        topologyKey: kubernetes.io/hostname
```

```
Pod Affinity: "Schedule me on the same node/zone as pods with label X"

Use cases:
  - App pod co-located with its cache (same node for low latency)
  - Frontend pods near backend pods (same zone)

topologyKey defines "same":
  kubernetes.io/hostname       → same node
  topology.kubernetes.io/zone  → same availability zone
  topology.kubernetes.io/region → same region
```

### Pod Anti-Affinity

```yaml
# Spread away from other pods with specific labels
spec:
  affinity:
    podAntiAffinity:
      # Hard: NEVER on same node as another pod with app=my-app
      requiredDuringSchedulingIgnoredDuringExecution:
      - labelSelector:
          matchLabels:
            app: my-app
        topologyKey: kubernetes.io/hostname
      
      # Soft: PREFER different zones
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchLabels:
              app: my-app
          topologyKey: topology.kubernetes.io/zone
```

```
Pod Anti-Affinity: "DON'T schedule me near pods with label X"

Use cases:
  - Spread replicas across nodes (HA)
  - Spread across zones (fault tolerance)
  - Keep competing workloads apart (resource isolation)
```

### Taints

```
Taints: Applied to NODES to repel pods

kubectl taint nodes node-1 dedicated=gpu:NoSchedule

Taint effects:
  NoSchedule:       Don't schedule new pods (existing stay)
  PreferNoSchedule: Try not to schedule (soft)
  NoExecute:        Don't schedule + evict existing pods
```

```
Common taints:
  node-role.kubernetes.io/control-plane:NoSchedule  (master nodes)
  dedicated=gpu:NoSchedule                          (GPU nodes)
  maintenance=true:NoExecute                        (drain node)
```

### Tolerations

```yaml
# Pod tolerates a taint (allows scheduling on tainted node)
spec:
  tolerations:
  - key: "dedicated"
    operator: "Equal"
    value: "gpu"
    effect: "NoSchedule"
  
  # Tolerate all taints (DaemonSet pattern)
  - operator: "Exists"
```

```
Taint + Toleration relationship:
  Node taint: dedicated=gpu:NoSchedule
  
  Pod WITHOUT toleration → NOT scheduled on that node
  Pod WITH matching toleration → CAN be scheduled on that node
  
  Note: Toleration doesn't FORCE scheduling, it only ALLOWS it
  Use nodeSelector + toleration to force + allow
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
        app: my-app
  - maxSkew: 1
    topologyKey: kubernetes.io/hostname
    whenUnsatisfiable: ScheduleAnyway
    labelSelector:
      matchLabels:
        app: my-app
```

```
Topology Spread: Evenly distribute pods across topology domains

maxSkew: Maximum difference in pod count between any two domains
topologyKey: How to group nodes into domains
whenUnsatisfiable: DoNotSchedule (hard) or ScheduleAnyway (soft)

Example (maxSkew: 1, topologyKey: zone):
  Zone A: 3 pods
  Zone B: 2 pods  ← next pod goes here (skew would be 2 if in A)
  Zone C: 2 pods  ← or here

Better than pod anti-affinity for even distribution!
```

### Scheduling Constraints

```
Priority order (what matters most):

1. Resource availability (CPU, memory, GPU)
2. Taints (hard block unless tolerated)
3. nodeAffinity required (hard constraint)
4. Pod Affinity/Anti-Affinity required (hard)
5. Topology Spread (hard mode)
6. nodeAffinity preferred (soft, weighted)
7. Pod Affinity/Anti-Affinity preferred (soft, weighted)
8. Resource balance scoring
9. Image locality
```

---

## Internal Working

```
Scheduler Decision Flow:

1. New Pod detected (spec.nodeName empty)
2. FILTER phase (eliminate nodes):
   - Check resource capacity
   - Check nodeSelector/nodeAffinity (required)
   - Check taints vs tolerations
   - Check pod anti-affinity (required)
   - Check topology constraints (hard)
   - Check volume topology (same AZ)
   → Remaining: "feasible nodes"

3. SCORE phase (rank feasible nodes 0-100):
   - nodeAffinity preferred: +weight
   - Pod affinity preferred: +weight
   - Resource balance: +score
   - Image locality: +score (if image cached)
   - Topology spread: +score (even distribution)

4. SELECT highest-scoring node
5. BIND: Update Pod.spec.nodeName → stored in etcd
6. kubelet on target node detects binding → starts Pod
```

---

## Diagram

```
┌─────────────────── SCHEDULING FLOW ───────────────────────────┐
│                                                                 │
│  Unscheduled Pod                                               │
│       │                                                        │
│       ▼                                                        │
│  ┌──────────────── FILTER (Predicates) ─────────────────┐    │
│  │                                                        │    │
│  │  All Nodes: [N1] [N2] [N3] [N4] [N5]                 │    │
│  │                                                        │    │
│  │  After resource check:    [N1] [N2] [N3]  [N5]       │    │
│  │  After taint check:       [N1] [N2]       [N5]       │    │
│  │  After affinity check:    [N1] [N2]       [N5]       │    │
│  │                                                        │    │
│  │  Feasible: [N1, N2, N5]                               │    │
│  └────────────────────────────────────────────────────────┘    │
│       │                                                        │
│       ▼                                                        │
│  ┌──────────────── SCORE (Priorities) ──────────────────┐     │
│  │                                                        │     │
│  │  N1: resource=60, affinity=80, spread=70 → Total: 210│     │
│  │  N2: resource=70, affinity=40, spread=90 → Total: 200│     │
│  │  N5: resource=50, affinity=80, spread=80 → Total: 210│     │
│  │                                                        │     │
│  │  Winner: N1 (or N5, tiebreaker)                       │     │
│  └────────────────────────────────────────────────────────┘     │
│       │                                                        │
│       ▼                                                        │
│  BIND: Pod → Node 1                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between nodeSelector and nodeAffinity?

**A:**
- **nodeSelector:** Simple key=value label matching. All labels must match (AND). No soft preferences.
- **nodeAffinity:** Flexible — supports operators (In, NotIn, Exists, Gt, Lt), multiple terms (OR between terms), and both hard (required) and soft (preferred with weights) rules.

Use nodeSelector for simple cases, nodeAffinity for complex requirements.

### Q2: Explain taints and tolerations with a real example.

**A:** Taints repel pods from nodes; tolerations allow pods to bypass taints.

Example: GPU nodes should only run ML workloads.
- Taint GPU nodes: `kubectl taint nodes gpu-node-1 dedicated=gpu:NoSchedule`
- Regular pods: Can't schedule on GPU nodes (no toleration)
- ML pods: Add toleration + nodeSelector to specifically target GPU nodes

Taint allows, nodeSelector forces. Together they create dedicated node pools.

### Q3: How do you ensure high availability with pod scheduling?

**A:** Combine multiple strategies:
1. **Pod anti-affinity** (required, hostname): Spread replicas across nodes
2. **Topology spread constraints** (zone): Even distribution across AZs
3. **Multiple replicas**: At least 3 for critical services
4. **PodDisruptionBudget**: Maintain minimum available during disruptions

### Q4: What is topology spread and how is it different from anti-affinity?

**A:**
- **Anti-affinity:** Binary — "don't put two pods on same node/zone." Can lead to uneven distribution (3 pods in zone A, 1 in zone B).
- **Topology spread:** Controls the maximum skew (difference) between domains. Ensures even distribution. maxSkew=1 means at most 1 pod difference between any two zones.

Topology spread is better for load distribution; anti-affinity is better for strict separation.

### Q5: What happens if no node satisfies scheduling constraints?

**A:** Pod stays in Pending state indefinitely. Events will show `FailedScheduling` with reason (insufficient CPU, no matching nodes, etc.). Solutions:
- Add more nodes or scale existing ones
- Relax constraints (use preferred instead of required)
- Remove taints or add tolerations
- Cluster Autoscaler can add nodes automatically

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Only nodeSelector without toleration | Pod can't schedule on tainted node | Add both |
| Required anti-affinity with same replicas as nodes | Can't schedule if nodes < replicas | Use preferred |
| Forgetting topology key | Anti-affinity has no effect | Specify hostname/zone |
| Over-constraining scheduling | Pods stuck Pending | Start with soft constraints |

---

## Best Practices

1. **Use topology spread constraints** for even distribution
2. **Combine taints + nodeSelector** for dedicated node pools
3. **Use preferred (soft) anti-affinity** — hard can block scheduling
4. **Set weights thoughtfully** — higher weight = more influence
5. **Test scheduling in staging** before production constraints
6. **Monitor Pending pods** — alert on scheduling failures
7. **Use Cluster Autoscaler** to handle capacity issues

---

## Related Topics

- [15. Resources](./15-resources.md)
- [17. Scaling](./17-scaling.md)
- [37. Advanced Production Topics](./37-advanced-production-topics.md)
- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
