# 12. DaemonSet

---

## Theory

A **DaemonSet** ensures that a copy of a Pod runs on every (or selected) node in the cluster. When nodes are added, DaemonSet pods are automatically scheduled on them.

### What is DaemonSet?

```
DaemonSet = Exactly one Pod per Node (automatic)

Normal Deployment: "Run N replicas somewhere in cluster"
DaemonSet:         "Run one pod on EVERY node"
```

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: node-exporter
  namespace: monitoring
spec:
  selector:
    matchLabels:
      app: node-exporter
  template:
    metadata:
      labels:
        app: node-exporter
    spec:
      containers:
      - name: node-exporter
        image: prom/node-exporter:v1.7.0
        ports:
        - containerPort: 9100
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 100m
            memory: 128Mi
      tolerations:
      - operator: Exists    # Run on ALL nodes including control plane
```

### One Pod Per Node

```
Cluster with 5 nodes:

  Node 1: [DaemonSet Pod]
  Node 2: [DaemonSet Pod]
  Node 3: [DaemonSet Pod]
  Node 4: [DaemonSet Pod]
  Node 5: [DaemonSet Pod]

Add Node 6:
  Node 6: [DaemonSet Pod] ← automatically scheduled

Unlike Deployment where scheduler picks nodes,
DaemonSet GUARANTEES one pod per matching node.
```

### Node Addition

```
When a new node joins the cluster:
  1. Node registers with API Server
  2. DaemonSet Controller detects new node
  3. Creates Pod for that node (sets nodeName)
  4. kubelet on new node starts the Pod

No scheduler involvement needed!
```

### Node Removal

```
When a node is removed:
  1. Node cordoned/deleted
  2. DaemonSet Pod on that node is terminated
  3. No rescheduling (DaemonSet doesn't need to)

When a node is cordoned (unschedulable):
  - Existing DaemonSet pods continue running
  - Normal pods won't be scheduled there
```

### Logging Agents

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluent-bit
  namespace: logging
spec:
  selector:
    matchLabels:
      app: fluent-bit
  template:
    metadata:
      labels:
        app: fluent-bit
    spec:
      containers:
      - name: fluent-bit
        image: fluent/fluent-bit:2.2
        volumeMounts:
        - name: varlog
          mountPath: /var/log
          readOnly: true
        - name: containers
          mountPath: /var/lib/docker/containers
          readOnly: true
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
      - name: containers
        hostPath:
          path: /var/lib/docker/containers
```

### Monitoring Agents

```
Common DaemonSet deployments:
  - node-exporter (Prometheus metrics from every node)
  - datadog-agent (Datadog monitoring)
  - newrelic-infrastructure (New Relic)
  - collectd (system metrics)
```

### Networking Agents

```
CNI plugins run as DaemonSets:
  - calico-node (Calico networking)
  - cilium-agent (Cilium)
  - aws-node (AWS VPC CNI)
  - kube-proxy (network rules per node)

These MUST run on every node for networking to function.
```

---

## Internal Working

```
DaemonSet Controller:
  1. Lists all nodes in cluster
  2. For each node, checks if DaemonSet Pod exists
  3. If Pod missing on a node → creates Pod (with nodeName set)
  4. If Pod exists on removed node → garbage collected
  5. Respects nodeSelector and tolerations

Update Strategy:
  RollingUpdate (default): Updates one node at a time
  OnDelete: Only updates when pod manually deleted

DaemonSet vs Scheduler:
  - DaemonSet controller bypasses scheduler
  - Sets Pod.spec.nodeName directly
  - Even runs on unschedulable nodes (with toleration)
```

---

## Interview Questions

### Q1: What is a DaemonSet and when would you use it?

**A:** DaemonSet ensures exactly one pod runs on every (or selected) node. Use for node-level operations: log collection (Fluent Bit), monitoring (node-exporter), networking (CNI plugins), security scanning, storage daemons. These are infrastructure concerns that need presence on every node.

### Q2: How is DaemonSet different from Deployment with one replica per node?

**A:** DaemonSet guarantees exactly one pod per node without scheduler involvement. Deployment can't guarantee one-per-node (anti-affinity is "preferred" not "required" if nodes are full). DaemonSet auto-adds pods when new nodes join and doesn't try to reschedule when nodes leave. It also bypasses the scheduler by setting nodeName directly.

### Q3: Can you restrict a DaemonSet to specific nodes?

**A:** Yes, using nodeSelector or nodeAffinity:
```yaml
spec:
  template:
    spec:
      nodeSelector:
        disk: ssd          # Only nodes with this label
```
Or use tolerations to run on tainted nodes (like control plane).

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| No resource limits on DaemonSet | Affects node workloads | Set tight limits |
| Missing tolerations | Won't run on tainted nodes | Add tolerations for target nodes |
| Using Deployment for node agents | Can't guarantee one-per-node | Use DaemonSet |
| Not monitoring DaemonSet health | Silent failures go unnoticed | Alert on DaemonSet not ready |

---

## Best Practices

1. **Set resource limits** — DaemonSet pods run on every node, waste adds up
2. **Use tolerations** — ensure pods run on all desired nodes (including tainted)
3. **Use RollingUpdate strategy** — safe updates one node at a time
4. **Monitor DaemonSet pod count** — should equal node count
5. **Use priority classes** — ensure infrastructure pods aren't evicted

---

## Related Topics

- [14. Scheduling](./14-scheduling.md)
- [29. Monitoring & Observability](./29-monitoring-and-observability.md)
- [30. Logging](./30-logging.md)
- [05. Deployments](./05-deployments.md)
