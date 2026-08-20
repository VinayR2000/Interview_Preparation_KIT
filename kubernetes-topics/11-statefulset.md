# 11. StatefulSet ⭐⭐

---

## Theory

A **StatefulSet** manages stateful applications that require stable network identity, persistent storage, and ordered deployment/scaling.

### StatefulSet

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres      # Required: headless service name
  replicas: 3
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15
        ports:
        - containerPort: 5432
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:      # Each pod gets its own PVC
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: gp3
      resources:
        requests:
          storage: 10Gi
```

### Stable Network Identity

```
Each Pod gets a predictable, stable hostname:
  <statefulset-name>-<ordinal>

  postgres-0
  postgres-1
  postgres-2

DNS records (with headless service "postgres"):
  postgres-0.postgres.default.svc.cluster.local
  postgres-1.postgres.default.svc.cluster.local
  postgres-2.postgres.default.svc.cluster.local

Even if pod is rescheduled to different node:
  postgres-1 always gets same DNS name and same PVC
```

### Stable Storage

```
volumeClaimTemplates creates one PVC per Pod:

  postgres-0 → data-postgres-0 (PVC) → pv-abc (10Gi EBS)
  postgres-1 → data-postgres-1 (PVC) → pv-def (10Gi EBS)
  postgres-2 → data-postgres-2 (PVC) → pv-ghi (10Gi EBS)

If postgres-1 Pod is deleted and recreated:
  - New Pod still named postgres-1
  - Still binds to data-postgres-1 PVC
  - Same data as before!
  
PVCs are NOT deleted when StatefulSet is scaled down
  (must manually delete if storage no longer needed)
```

### Ordered Deployment

```
Pods are created sequentially:
  postgres-0 created → wait until Ready
  postgres-1 created → wait until Ready
  postgres-2 created → Ready

Why? Database replication needs order:
  postgres-0 = primary (must be up first)
  postgres-1 = replica (needs primary to replicate from)
  postgres-2 = replica (needs primary to replicate from)
```

### Ordered Scaling

```
Scale Up (3 → 5):
  postgres-3 created → wait until Ready
  postgres-4 created → Ready

Scale Down (5 → 3):
  postgres-4 terminated first → wait
  postgres-3 terminated → Done
  (highest ordinal removed first)

PVCs for postgres-3 and postgres-4 are NOT deleted automatically
```

### PersistentVolumeClaim

```
volumeClaimTemplates in StatefulSet:
  - Automatically creates PVC for each pod
  - PVC naming: <volumeClaimTemplate-name>-<statefulset-name>-<ordinal>
  - Example: data-postgres-0, data-postgres-1, data-postgres-2

Lifecycle:
  - Created when Pod is first created
  - NOT deleted when Pod is deleted/rescheduled
  - NOT deleted on scale-down
  - Must manually delete PVCs if no longer needed
```

### Headless Service

```yaml
# Required for StatefulSet (provides DNS records for pods)
apiVersion: v1
kind: Service
metadata:
  name: postgres
spec:
  clusterIP: None          # Headless!
  selector:
    app: postgres
  ports:
  - port: 5432
```

```
Headless Service provides:
  - Individual DNS records for each Pod
  - No ClusterIP (no load balancing)
  - Direct pod addressing: postgres-0.postgres.ns.svc.cluster.local
  - Required for StatefulSet stable network identity
```

### StatefulSet vs Deployment

```
┌─────────────────────┬──────────────────┬──────────────────────┐
│ Feature             │ Deployment       │ StatefulSet           │
├─────────────────────┼──────────────────┼──────────────────────┤
│ Pod names           │ Random suffix    │ Ordinal (0,1,2)      │
│                     │ (app-7f8d4c...)  │ (app-0, app-1)       │
│ Pod identity        │ Interchangeable  │ Sticky identity       │
│ Storage             │ Shared (optional)│ Per-pod PVC          │
│ Deployment order    │ Parallel         │ Sequential           │
│ Scaling             │ Parallel         │ Ordered              │
│ Network identity    │ Ephemeral        │ Stable DNS per pod   │
│ Use case            │ Stateless apps   │ Databases, Kafka,    │
│                     │ (APIs, web)      │ Elasticsearch        │
│ Service type        │ ClusterIP        │ Headless Service     │
│ Update strategy     │ RollingUpdate    │ RollingUpdate/OnDelete│
└─────────────────────┴──────────────────┴──────────────────────┘
```

---

## Internal Working

```
StatefulSet Controller Behavior:

1. Creates Pods in order: pod-0, then pod-1, then pod-2
2. Waits for each Pod to be Running AND Ready before creating next
3. Creates PVC for each Pod using volumeClaimTemplates
4. On Pod deletion: creates new Pod with SAME name and SAME PVC
5. On scale-down: removes highest ordinal first
6. On update: updates pods in reverse order (2, 1, 0) by default

Update Strategies:
  RollingUpdate (default):
    - Updates from highest to lowest ordinal
    - One at a time, waits for Ready
    - partition field: only pods >= partition are updated
    
  OnDelete:
    - Only updates pod when manually deleted
    - Full control over update order
```

---

## Diagram

```
┌─────────────────── STATEFULSET ARCHITECTURE ─────────────────┐
│                                                                │
│  StatefulSet: postgres (replicas: 3)                          │
│  Headless Service: postgres                                   │
│                                                                │
│  ┌──────────────────────────────────────────────────────┐    │
│  │                                                        │    │
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐          │    │
│  │  │postgres-0│    │postgres-1│    │postgres-2│          │    │
│  │  │(primary) │    │(replica) │    │(replica) │          │    │
│  │  └────┬─────┘    └────┬─────┘    └────┬─────┘          │    │
│  │       │               │               │                │    │
│  │  ┌────┴─────┐    ┌────┴─────┐    ┌────┴─────┐        │    │
│  │  │data-     │    │data-     │    │data-     │        │    │
│  │  │postgres-0│    │postgres-1│    │postgres-2│        │    │
│  │  │(PVC/PV)  │    │(PVC/PV)  │    │(PVC/PV)  │        │    │
│  │  │10Gi EBS  │    │10Gi EBS  │    │10Gi EBS  │        │    │
│  │  └──────────┘    └──────────┘    └──────────┘        │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                                │
│  DNS:                                                         │
│    postgres-0.postgres.default.svc.cluster.local              │
│    postgres-1.postgres.default.svc.cluster.local              │
│    postgres-2.postgres.default.svc.cluster.local              │
└────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: When should you use StatefulSet vs Deployment?

**A:** Use StatefulSet when you need:
- Stable, unique network identifiers (databases need to address specific replicas)
- Stable persistent storage (each replica has its own data)
- Ordered deployment/scaling (primary must start before replicas)

Examples: PostgreSQL, MySQL, Kafka, Elasticsearch, ZooKeeper, Redis (clustered)

Use Deployment for stateless apps: REST APIs, web servers, microservices.

### Q2: Why does StatefulSet require a Headless Service?

**A:** Headless Service (clusterIP: None) provides individual DNS records for each Pod (pod-0.service.ns.svc). This enables:
- Direct addressing of specific pods (connect to primary database)
- Stable network identity that follows the pod across rescheduling
- Cluster formation where nodes need to discover each other by hostname

Without headless service, you can't address individual pods by name.

### Q3: What happens to PVCs when a StatefulSet is scaled down?

**A:** PVCs are NOT automatically deleted on scale-down. This is intentional — it prevents accidental data loss. If you scale from 3 to 2, the PVC for pod-2 still exists with its data. When you scale back to 3, pod-2 reconnects to its original PVC with all data intact. You must manually delete PVCs if you want to free the storage.

### Q4: How does StatefulSet rolling update work?

**A:** By default, it updates pods in reverse ordinal order (highest first): pod-2, then pod-1, then pod-0. This ensures the primary (typically pod-0) is updated last. You can use `partition` to do canary updates — setting partition=2 means only pods with ordinal >= 2 are updated, allowing testing before full rollout.

### Q5: How would you handle a database cluster with StatefulSet?

**A:**
1. Create headless Service for stable DNS
2. Create StatefulSet with volumeClaimTemplates
3. Init container or sidecar for replication setup
4. Pod-0 becomes primary, others join as replicas
5. Use pod DNS for replication configuration
6. Configure anti-affinity to spread across nodes/AZs
7. Use PodDisruptionBudget to prevent majority loss

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using Deployment for databases | No stable identity/storage | Use StatefulSet |
| Forgetting headless service | No stable DNS | Always create headless Service |
| Expecting PVCs to auto-delete | Orphan PVCs waste money | Manual cleanup policy |
| Not using pod anti-affinity | All replicas on same node | Spread across nodes |
| Ignoring update order | Primary updated before replicas | Use OnDelete for full control |

---

## Best Practices

1. **Always use headless Service** with StatefulSet
2. **Use pod anti-affinity** to spread replicas across nodes/AZs
3. **Use PodDisruptionBudget** to maintain quorum during maintenance
4. **Consider OnDelete update strategy** for databases (manual control)
5. **Backup before updates** — data can't always be recovered
6. **Monitor PVC usage** — set alerts before disk full
7. **Use partition for canary updates** — test on highest ordinal first
8. **Document data recovery procedures** — know how to restore from backup

---

## Related Topics

- [10. Storage](./10-storage.md)
- [05. Deployments](./05-deployments.md)
- [06. Services](./06-services.md)
- [14. Scheduling](./14-scheduling.md)
