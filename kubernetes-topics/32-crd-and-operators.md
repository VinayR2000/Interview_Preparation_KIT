# 32. CRD & Operators ⭐⭐

---

## Theory

**Custom Resource Definitions (CRDs)** extend Kubernetes API with custom resources. **Operators** are controllers that manage these custom resources, encoding operational knowledge.

### Custom Resource Definition

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: databases.example.com
spec:
  group: example.com
  versions:
  - name: v1
    served: true
    storage: true
    schema:
      openAPIV3Schema:
        type: object
        properties:
          spec:
            type: object
            properties:
              engine:
                type: string
                enum: [postgres, mysql]
              version:
                type: string
              replicas:
                type: integer
                minimum: 1
              storage:
                type: string
  scope: Namespaced
  names:
    plural: databases
    singular: database
    kind: Database
    shortNames:
    - db
```

### Custom Resources

```yaml
# Instance of the CRD (custom object)
apiVersion: example.com/v1
kind: Database
metadata:
  name: order-db
  namespace: production
spec:
  engine: postgres
  version: "15"
  replicas: 3
  storage: "100Gi"
```

```
After creating CRD:
  kubectl get databases
  kubectl describe database order-db
  kubectl delete database order-db
  
Custom resources are stored in etcd like native objects.
But without an Operator/Controller, they're just data — nothing acts on them.
```

### Operators

```
Operator = CRD + Custom Controller

Encodes operational knowledge:
  - How to deploy the application
  - How to scale it
  - How to back it up
  - How to upgrade it
  - How to recover from failures

Example: PostgreSQL Operator
  1. User creates: Database CR (engine: postgres, replicas: 3)
  2. Operator detects new Database CR
  3. Operator creates: StatefulSet, Services, Secrets, PVCs
  4. Operator monitors health, handles failover
  5. User updates replicas: 3 → 5
  6. Operator scales StatefulSet, configures replication
```

### Controllers

```
Custom Controller:
  - Watches for changes to custom resources
  - Reconciles desired state with current state
  - Uses Kubernetes client-go library

Controller loop:
  while true:
    event = watchCustomResource()
    currentState = getActualState()
    desiredState = event.spec
    if currentState != desiredState:
      reconcile(currentState, desiredState)
```

### Reconciliation

```
Operator reconciliation example (Database operator):

  Desired: Database with 3 replicas, postgres 15
  
  Reconcile:
    1. StatefulSet exists? → If no, create it
    2. StatefulSet has 3 replicas? → If no, scale it
    3. Pods running postgres 15? → If no, rolling update
    4. Primary healthy? → If no, promote replica
    5. Backups running? → If no, create CronJob
    6. Monitoring configured? → If no, create ServiceMonitor
    
  Update status:
    status.phase: Ready
    status.replicas: 3
    status.primaryEndpoint: postgres-0.postgres:5432
```

### Operator Pattern

```
Popular Operators:
  ┌─────────────────────────┬────────────────────────────────┐
  │ Operator                │ Manages                         │
  ├─────────────────────────┼────────────────────────────────┤
  │ Prometheus Operator     │ Prometheus, Alertmanager        │
  │ Strimzi                 │ Apache Kafka                    │
  │ CloudNativePG           │ PostgreSQL clusters             │
  │ Elasticsearch Operator  │ Elasticsearch clusters          │
  │ Cert-Manager            │ TLS certificates                │
  │ External Secrets        │ External secret sync            │
  │ Argo CD                 │ GitOps deployments              │
  └─────────────────────────┴────────────────────────────────┘

Operator Maturity Levels:
  Level 1: Basic install (Helm-like)
  Level 2: Upgrades (seamless version upgrades)
  Level 3: Lifecycle (backup, restore, failover)
  Level 4: Insights (metrics, alerts, log analysis)
  Level 5: Autopilot (auto-tune, auto-scale, auto-heal)
```

---

## Interview Questions

### Q1: What is a CRD and why would you use one?

**A:** CRD extends the Kubernetes API with custom resource types. Use when you want to manage custom application state through Kubernetes natively. Examples: define a "Database" resource that encapsulates StatefulSet + Service + PVC creation. Benefits: declarative management, kubectl integration, watch/reconciliation, RBAC integration.

### Q2: What is the Operator pattern?

**A:** An Operator packages human operational knowledge into a controller. It watches custom resources and takes automated actions to manage an application's full lifecycle — deployment, scaling, upgrades, backup, recovery. Example: PostgreSQL Operator watches "PostgresCluster" CRs and automatically manages StatefulSets, replication, failover, backups.

### Q3: When should you build a custom operator vs use Helm?

**A:**
- **Helm:** One-time deployment, basic upgrades, stateless apps. "Install and forget."
- **Operator:** Complex lifecycle management, stateful apps, ongoing automation (failover, backup, scaling decisions), encoding domain expertise. "Continuous management."

Use Helm for simple deployments, Operators for complex stateful systems.

---

## Best Practices

1. **Use existing operators** before building custom ones (OperatorHub.io)
2. **Follow controller-runtime patterns** if building custom
3. **Implement status subresource** — report current state in CR status
4. **Add finalizers** for cleanup logic on deletion
5. **Use owner references** — garbage collect child resources
6. **Test with envtest** — unit test reconciliation logic
7. **Version your CRDs** — support API evolution

---

## Related Topics

- [24. Kubernetes Internals](./24-kubernetes-internals.md)
- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
- [37. Advanced Production Topics](./37-advanced-production-topics.md)
