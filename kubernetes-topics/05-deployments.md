# 5. Deployments ⭐⭐⭐

---

## Theory

A **Deployment** is a higher-level controller that manages ReplicaSets and provides declarative updates, rolling deployments, and rollback capabilities for stateless applications.

### Deployment

```
Deployment hierarchy:
  Deployment → manages → ReplicaSet → manages → Pods

What Deployment adds over ReplicaSet:
  - Rolling update strategy
  - Rollback to previous versions
  - Pause and resume deployments
  - Deployment status tracking
  - Revision history
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
  labels:
    app: my-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-app
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: app
        image: my-app:v2
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

### ReplicaSet

ReplicaSet ensures a stable set of replica Pods are running at all times:

```
ReplicaSet responsibility:
  - Maintains desired number of Pod replicas
  - Creates new Pods when count is below desired
  - Deletes excess Pods when count is above desired
  - Uses label selector to identify its Pods

Note: Never create ReplicaSet directly — always use Deployment
```

### Desired Replicas

```yaml
spec:
  replicas: 3   # Desired state: always maintain 3 pods

# Scenarios:
# Current: 3 pods running → No action
# Current: 2 pods running (1 crashed) → Create 1 new pod
# Current: 4 pods running (manual create) → Delete 1 pod
# Scale to 5: kubectl scale deployment my-app --replicas=5
```

### Rolling Update

```
Rolling Update Strategy (default):
  Gradually replaces old pods with new ones

  replicas: 4
  maxSurge: 1        (can have 1 extra pod during update)
  maxUnavailable: 0  (all must be available during update)

  Step 1: Total=5 (4 old + 1 new) → wait for new to be Ready
  Step 2: Total=5 (3 old + 2 new) → old terminated, new created
  Step 3: Total=5 (2 old + 3 new)
  Step 4: Total=5 (1 old + 4 new)
  Step 5: Total=4 (0 old + 4 new) → Complete

Timeline:
  [v1][v1][v1][v1]           ← Before
  [v1][v1][v1][v1][v2]       ← maxSurge: +1
  [v1][v1][v1][v2][v2]       ← Rolling
  [v1][v1][v2][v2][v2]
  [v1][v2][v2][v2][v2]
  [v2][v2][v2][v2]           ← After
```

### Recreate Strategy

```
Recreate Strategy:
  Kill ALL old pods first, then create ALL new pods
  Results in downtime!

  Step 1: [v1][v1][v1][v1] → Kill all
  Step 2: [ ][ ][ ][ ]     → Downtime!
  Step 3: [v2][v2][v2][v2] → All new created

Use case: When you can't run two versions simultaneously
  - Database schema incompatibility
  - Resource constraints
  - Development/test environments
```

```yaml
spec:
  strategy:
    type: Recreate
```

### maxSurge

```
maxSurge: Maximum number of pods that can be created ABOVE desired count

Examples (replicas: 4):
  maxSurge: 1     → Max 5 pods during update (4 + 1)
  maxSurge: 2     → Max 6 pods during update (4 + 2)
  maxSurge: 25%   → Max 5 pods (4 + 1, rounded up)
  maxSurge: 50%   → Max 6 pods (4 + 2)

Higher maxSurge = faster rollout (more resources needed)
```

### maxUnavailable

```
maxUnavailable: Maximum number of pods that can be UNAVAILABLE during update

Examples (replicas: 4):
  maxUnavailable: 0     → All 4 must be available (safest, slowest)
  maxUnavailable: 1     → 3 must be available (1 can be down)
  maxUnavailable: 25%   → 3 must be available (1 can be down)
  maxUnavailable: 50%   → 2 must be available (2 can be down)

Common production settings:
  maxSurge: 1, maxUnavailable: 0      → Zero downtime, gradual
  maxSurge: 25%, maxUnavailable: 25%  → Balanced speed/safety
```

### Revision History

```
Each Deployment update creates a new ReplicaSet (revision):

  Deployment: my-app
    ├── ReplicaSet (revision 1): my-app:v1 [replicas: 0]
    ├── ReplicaSet (revision 2): my-app:v2 [replicas: 0]
    └── ReplicaSet (revision 3): my-app:v3 [replicas: 3] ← current

kubectl rollout history deployment/my-app
  REVISION  CHANGE-CAUSE
  1         Initial deployment
  2         Update to v2
  3         Update to v3

spec:
  revisionHistoryLimit: 10  # Keep 10 old ReplicaSets (default)
```

### Rollback

```
# Rollback to previous revision
kubectl rollout undo deployment/my-app

# Rollback to specific revision
kubectl rollout undo deployment/my-app --to-revision=1

# What happens:
# 1. Current ReplicaSet scaled down
# 2. Target ReplicaSet scaled up
# 3. Rolling update to old version
# 4. New revision number created (not same as original)
```

### Scaling

```
# Manual scaling
kubectl scale deployment my-app --replicas=5

# Autoscaling (HPA)
kubectl autoscale deployment my-app --min=2 --max=10 --cpu-percent=80

# Scale to zero (stop all pods)
kubectl scale deployment my-app --replicas=0
```

### Deployment Status

```
kubectl rollout status deployment/my-app

Conditions:
  Available: True    → Minimum replicas available
  Progressing: True  → Deployment is making progress
  
# Deployment is complete when:
#   - All replicas updated to latest version
#   - All replicas are available
#   - No old replicas running

# Check deployment
kubectl get deployment my-app
NAME     READY   UP-TO-DATE   AVAILABLE   AGE
my-app   3/3     3            3           5m
```

---

## Internal Working

```
Deployment Update Flow (Rolling Update):

1. User updates Deployment (e.g., new image version)
2. Deployment Controller detects change
3. Creates new ReplicaSet (revision N+1) with replicas=0
4. Scales UP new ReplicaSet by maxSurge
5. Waits for new Pods to be Ready
6. Scales DOWN old ReplicaSet by maxUnavailable
7. Repeats steps 4-6 until:
   - New ReplicaSet has desired replicas
   - Old ReplicaSet has 0 replicas
8. Old ReplicaSet retained (for rollback) with replicas=0

Controller Logic (simplified):
  while newRS.replicas < desired:
    if totalPods < desired + maxSurge:
      scaleUp(newRS)
    if newRS.readyPods > 0 and oldRS.replicas > 0:
      scaleDown(oldRS)
    wait for pod readiness
```

```
Rollback Internal Flow:

1. User: kubectl rollout undo deployment/my-app --to-revision=2
2. Deployment Controller reads ReplicaSet for revision 2
3. Updates Deployment spec to match revision 2's pod template
4. This triggers a NEW rolling update (not instant)
5. New revision number assigned (e.g., revision 4)
6. Old ReplicaSet (revision 2) is scaled up
7. Current ReplicaSet is scaled down
```

---

## Diagram

```
┌──────────────────── DEPLOYMENT ROLLING UPDATE ────────────────────┐
│                                                                     │
│  Deployment: my-app (replicas: 3, maxSurge: 1, maxUnavailable: 0) │
│                                                                     │
│  State 1 (Before):                                                  │
│  ReplicaSet-v1 (replicas: 3)                                       │
│  [Pod-v1] [Pod-v1] [Pod-v1]                                       │
│                                                                     │
│  State 2 (Update triggered - new RS created):                      │
│  ReplicaSet-v1 (replicas: 3)  ReplicaSet-v2 (replicas: 1)        │
│  [Pod-v1] [Pod-v1] [Pod-v1]  [Pod-v2 Starting...]                │
│                                                                     │
│  State 3 (New pod ready, old pod terminated):                      │
│  ReplicaSet-v1 (replicas: 2)  ReplicaSet-v2 (replicas: 2)        │
│  [Pod-v1] [Pod-v1]           [Pod-v2] [Pod-v2 Starting...]        │
│                                                                     │
│  State 4 (Continuing):                                              │
│  ReplicaSet-v1 (replicas: 1)  ReplicaSet-v2 (replicas: 3)        │
│  [Pod-v1]                    [Pod-v2] [Pod-v2] [Pod-v2 Starting]  │
│                                                                     │
│  State 5 (Complete):                                                │
│  ReplicaSet-v1 (replicas: 0)  ReplicaSet-v2 (replicas: 3)        │
│                               [Pod-v2] [Pod-v2] [Pod-v2]          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Code

### Production Deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: production
  labels:
    app: order-service
    team: backend
spec:
  replicas: 3
  revisionHistoryLimit: 5
  selector:
    matchLabels:
      app: order-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: order-service
        version: v2.1.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
    spec:
      serviceAccountName: order-service-sa
      terminationGracePeriodSeconds: 60
      containers:
      - name: order-service
        image: registry.example.com/order-service:2.1.0
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: order-db-secret
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          failureThreshold: 30
          periodSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          periodSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          periodSeconds: 5
          failureThreshold: 3
        lifecycle:
          preStop:
            exec:
              command: ["sh", "-c", "sleep 10"]
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchLabels:
                  app: order-service
              topologyKey: kubernetes.io/hostname
```

---

## Interview Questions

### Q1: What is the difference between Deployment and ReplicaSet?

**A:** ReplicaSet only ensures N replicas are running — it has no concept of updates or versions. Deployment manages ReplicaSets and adds: rolling updates, rollback capability, revision history, pause/resume. You should never create ReplicaSets directly; always use Deployments.

### Q2: Explain maxSurge and maxUnavailable with an example.

**A:** With replicas=4, maxSurge=1, maxUnavailable=0:
- maxSurge=1: During update, max 5 pods can exist (4 desired + 1 extra)
- maxUnavailable=0: All 4 desired pods must remain available at all times
- Result: New pod starts → becomes ready → old pod terminated → repeat
- This is the safest setting: zero-downtime but slowest rollout

### Q3: How does Kubernetes rollback work internally?

**A:** When you `kubectl rollout undo`:
1. K8s looks up the ReplicaSet for the target revision
2. Updates the Deployment's pod template to match that revision
3. This triggers a standard rolling update to the old version
4. A new revision number is created (rollback is just another update)
5. Old ReplicaSets are retained (controlled by revisionHistoryLimit)

### Q4: What happens if a rolling update Pod fails to become Ready?

**A:** The Deployment Controller pauses the rollout. Since maxUnavailable controls how many can be down, and the new pod isn't ready, the controller won't terminate more old pods. If `progressDeadlineSeconds` (default 600s) expires, the Deployment is marked as failed. You can then rollback with `kubectl rollout undo`.

### Q5: When would you use Recreate vs RollingUpdate strategy?

**A:**
- **RollingUpdate:** Default, zero-downtime, works for most apps
- **Recreate:** When you can't run two versions simultaneously:
  - Database schema migration that's incompatible with old version
  - Only one instance can hold a lock/resource
  - Strict resource constraints (can't afford extra pods)
  - Development/test environments where speed matters more than availability

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| maxSurge=0 AND maxUnavailable=0 | Deadlock — can't create or remove pods | At least one must be > 0 |
| No readiness probe | Bad pods get traffic, rolling update never completes | Always configure readiness probe |
| Using `:latest` tag | Can't rollback, unclear what's deployed | Use specific version tags |
| revisionHistoryLimit: 0 | Can't rollback | Keep at least 5-10 revisions |
| No resource limits | OOM kills during rollout | Set requests and limits |
| Ignoring progressDeadlineSeconds | Stuck deployments go unnoticed | Set appropriate deadline + alerts |

---

## Best Practices

1. **Always use RollingUpdate** strategy in production
2. **Set maxSurge=1, maxUnavailable=0** for zero-downtime deployments
3. **Use specific image tags** — never `:latest`
4. **Configure readiness probes** — critical for rolling updates
5. **Set progressDeadlineSeconds** — detect stuck deployments
6. **Keep revision history** — revisionHistoryLimit: 10
7. **Use pod anti-affinity** — spread replicas across nodes
8. **Add preStop hook with sleep** — allows load balancer to deregister

---

## Related Topics

- [04. Pods](./04-pods.md)
- [06. Services](./06-services.md)
- [17. Scaling](./17-scaling.md)
- [23. Deployment Strategies](./23-deployment-strategies.md)
