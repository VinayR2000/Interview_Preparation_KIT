# 17. Scaling ⭐⭐⭐

---

## Theory

Kubernetes provides multiple scaling mechanisms to handle varying workload demands automatically.

### Manual Scaling

```bash
kubectl scale deployment my-app --replicas=5
kubectl scale statefulset postgres --replicas=3

# Scale to zero (stop all pods)
kubectl scale deployment my-app --replicas=0
```

### Horizontal Pod Autoscaler (HPA)

Automatically scales the number of pod replicas based on observed metrics:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: my-app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300   # Wait 5 min before scaling down
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60               # Max 10% scale down per minute
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
      - type: Pods
        value: 4
        periodSeconds: 60               # Max 4 pods scale up per minute
```

### HPA Algorithm

```
desiredReplicas = ceil[currentReplicas × (currentMetricValue / targetMetricValue)]

Example:
  Current replicas: 3
  Current CPU utilization: 90%
  Target CPU utilization: 70%
  
  desiredReplicas = ceil[3 × (90/70)] = ceil[3.86] = 4

Tolerance: 10% (won't scale if within 10% of target)
  If target is 70%, won't scale between 63% and 77%
```

### CPU-Based Scaling

```yaml
metrics:
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 70    # Average across all pods

# How it works:
# All pods avg CPU = 85% → scale UP
# All pods avg CPU = 50% → scale DOWN
# All pods avg CPU = 70% → no change
```

### Memory-Based Scaling

```yaml
metrics:
- type: Resource
  resource:
    name: memory
    target:
      type: Utilization
      averageUtilization: 80

# Caution with memory-based scaling:
# Memory often doesn't decrease after load drops (JVM, caches)
# Can lead to scale-up but never scale-down
# Better for memory-intensive batch workloads
```

### Custom Metrics

```yaml
metrics:
- type: Pods
  pods:
    metric:
      name: http_requests_per_second
    target:
      type: AverageValue
      averageValue: "100"     # Scale when > 100 RPS per pod

- type: Object
  object:
    describedObject:
      apiVersion: networking.k8s.io/v1
      kind: Ingress
      name: my-ingress
    metric:
      name: requests-per-second
    target:
      type: Value
      value: "1000"           # Scale when ingress > 1000 RPS
```

```
Custom metrics require:
  1. Metrics source (Prometheus, Datadog)
  2. Metrics adapter (prometheus-adapter, datadog-cluster-agent)
  3. Metric exposed via custom.metrics.k8s.io API

Common custom metrics:
  - HTTP requests per second
  - Queue depth (Kafka consumer lag)
  - Active connections
  - Response latency (P99)
```

### Vertical Pod Autoscaler (VPA)

Adjusts resource requests/limits based on actual usage:

```yaml
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: my-app-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: my-app
  updatePolicy:
    updateMode: "Auto"    # Auto, Initial, Off
  resourcePolicy:
    containerPolicies:
    - containerName: app
      minAllowed:
        cpu: "100m"
        memory: "128Mi"
      maxAllowed:
        cpu: "2"
        memory: "4Gi"
```

```
VPA Modes:
  Off:     Only provides recommendations (no changes)
  Initial: Sets resources only at pod creation
  Auto:    Evicts and recreates pods with new resources

VPA components:
  Recommender: Analyzes usage, suggests resources
  Updater:     Evicts pods needing resource changes
  Admission:   Sets resources on new pods

Limitation: VPA restarts pods to change resources (no live resize yet)
```

### Cluster Autoscaler

Adjusts the number of nodes in the cluster:

```
Scale Up:
  1. Pod stuck in Pending (insufficient node resources)
  2. Cluster Autoscaler detects Pending pods
  3. Simulates scheduling with additional nodes
  4. Requests new node from cloud provider (EC2, GKE node)
  5. New node joins cluster
  6. Pending pod scheduled on new node

Scale Down:
  1. Node utilization below threshold (default 50%)
  2. All pods on node can be scheduled elsewhere
  3. No PDB violations if pods are moved
  4. Node cordoned and drained
  5. Cloud provider terminates instance

Timing:
  Scale up: ~2-5 minutes (new VM provisioning)
  Scale down: After 10 minutes below threshold (configurable)
```

### KEDA (Kubernetes Event-Driven Autoscaling)

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: kafka-consumer-scaler
spec:
  scaleTargetRef:
    name: kafka-consumer
  minReplicaCount: 1
  maxReplicaCount: 50
  triggers:
  - type: kafka
    metadata:
      bootstrapServers: kafka:9092
      consumerGroup: my-group
      topic: orders
      lagThreshold: "100"   # Scale when lag > 100 per partition
```

```
KEDA: Event-driven autoscaling (beyond CPU/memory)

Supported triggers:
  - Kafka consumer lag
  - RabbitMQ queue depth
  - AWS SQS queue length
  - Redis streams
  - PostgreSQL queries
  - HTTP requests
  - Cron schedules

Key advantage:
  - Can scale to ZERO (saves resources when no events)
  - Fine-grained scaling based on business metrics
  - 50+ built-in scalers
```

### HPA vs VPA vs Cluster Autoscaler

```
┌─────────────────────┬──────────────────────────────────────────┐
│ Scaler              │ What it scales                            │
├─────────────────────┼──────────────────────────────────────────┤
│ HPA                 │ Number of Pod replicas                   │
│ VPA                 │ Resource requests/limits per Pod         │
│ Cluster Autoscaler  │ Number of Nodes in cluster              │
│ KEDA                │ Pod replicas based on events/queues     │
└─────────────────────┴──────────────────────────────────────────┘

Typical combination:
  HPA scales pods → Pods go Pending → Cluster Autoscaler adds nodes
  VPA right-sizes pods → More efficient packing on existing nodes

Note: HPA and VPA should NOT target the same metric on same deployment
  (they'll fight each other). Use HPA for scaling, VPA for right-sizing.
```

---

## Internal Working

```
HPA Control Loop (runs every 15s by default):

1. Metrics Server collects CPU/memory from kubelet (cAdvisor)
2. HPA Controller queries metrics API
3. Calculates desired replicas:
   desired = ceil(current × currentMetric / targetMetric)
4. Applies stabilization window (prevents flapping):
   - Scale up: use max of recent window (aggressive)
   - Scale down: use min of recent window (conservative)
5. Applies scaling policies (rate limiting)
6. If change needed: updates Deployment replicas
7. Deployment controller handles rolling update

Metrics pipeline:
  Container → cAdvisor → kubelet → metrics-server → 
  metrics.k8s.io API → HPA Controller
```

---

## Diagram

```
┌───────────────────── SCALING ARCHITECTURE ────────────────────┐
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    HPA                                    │  │
│  │  Monitors: CPU, Memory, Custom Metrics                   │  │
│  │  Action: Scale pod replicas (2 → 5 → 10)               │  │
│  └────────────────────────┬────────────────────────────────┘  │
│                           │                                    │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │                  DEPLOYMENT                               │  │
│  │  [Pod][Pod][Pod][Pod][Pod]                               │  │
│  └────────────────────────┬────────────────────────────────┘  │
│                           │                                    │
│              (Pods Pending — no node capacity)                 │
│                           │                                    │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │              CLUSTER AUTOSCALER                            │  │
│  │  Detects Pending pods → Adds new Node                    │  │
│  │  Low utilization → Removes Node                          │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │                    VPA                                     │  │
│  │  Monitors: Actual resource usage                         │  │
│  │  Action: Adjust requests/limits (256Mi → 512Mi)         │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: How does HPA work internally?

**A:** HPA runs a control loop every 15 seconds:
1. Queries metrics API (metrics-server or custom metrics adapter)
2. Calculates desired replicas: `ceil(current × currentMetric / targetMetric)`
3. Applies 10% tolerance (won't scale within ±10% of target)
4. Applies stabilization window (prevents flapping)
5. Applies scaling policies (rate limiting)
6. Updates Deployment replicas if needed

Requires: metrics-server installed, resource requests set on pods.

### Q2: When would you use KEDA over HPA?

**A:** Use KEDA when:
- Scaling based on external metrics (Kafka lag, SQS queue depth)
- Need to scale to zero (HPA can't go below 1)
- Event-driven workloads (not just CPU/memory)
- Need 50+ built-in scalers (databases, queues, HTTP)

Use HPA for simple CPU/memory-based scaling.

### Q3: Why shouldn't you use HPA and VPA together on the same Deployment?

**A:** They conflict: HPA scales replicas based on CPU utilization. VPA changes CPU requests/limits. If VPA increases CPU request, HPA recalculates utilization (now lower) and scales down. Then load increases, HPA scales up, VPA sees higher usage per pod... oscillation.

Solution: Use HPA for horizontal scaling + VPA in "Off" mode for recommendations only. Or use VPA on different Deployments than HPA targets.

### Q4: How do you handle slow scale-up (cold start)?

**A:**
- Set `scaleUp.stabilizationWindowSeconds` to 0 (immediate)
- Use aggressive scaleUp policies (more pods per period)
- Keep `minReplicas` high enough for baseline traffic
- Use KEDA with predictive scaling (cron-based pre-scaling)
- Warm pods: keep minimum alive with lower cost during off-peak

### Q5: How does Cluster Autoscaler decide to remove a node?

**A:** Node is removed when:
1. Utilization below threshold (default 50%) for 10+ minutes
2. All pods on node can be rescheduled elsewhere
3. No pods with local storage (emptyDir)
4. No pods with restrictive PodDisruptionBudget violations
5. No pods with `cluster-autoscaler.kubernetes.io/safe-to-evict: false`
6. No system pods (kube-system without PDB)

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| No resource requests | HPA can't calculate utilization | Always set requests |
| HPA min=1 | Single pod during scale-up latency | min >= 2 for HA |
| Memory-based HPA for JVM apps | Never scales down (JVM holds memory) | Use CPU or custom metrics |
| No scaleDown stabilization | Flapping (scale up/down rapidly) | Set 5 min stabilization |
| HPA + VPA on same deployment | Conflicting actions | Use only one, or VPA in Off mode |

---

## Best Practices

1. **Set resource requests** — HPA needs them for utilization calculation
2. **Use CPU for HPA target** — most predictable for web workloads
3. **Set stabilization windows** — 300s for scale-down, 30s for scale-up
4. **Use behavior policies** — rate-limit scaling actions
5. **KEDA for event-driven** — Kafka, SQS, queue-based workloads
6. **Monitor HPA decisions** — track scaling events
7. **Combine HPA + Cluster Autoscaler** — scale pods AND nodes
8. **Test scaling** — simulate load and verify behavior

---

## Related Topics

- [15. Resources](./15-resources.md)
- [14. Scheduling](./14-scheduling.md)
- [05. Deployments](./05-deployments.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
