# 23. Deployment Patterns

## Theory

Deployment patterns determine how new versions of services are released to production. The goal is zero-downtime deployments with the ability to quickly rollback.

### Key Patterns:

| Pattern | How It Works | Risk | Rollback |
|---------|-------------|------|----------|
| Blue-Green | Two environments, switch traffic | Low | Instant (switch back) |
| Canary | Gradual traffic shift (1% → 100%) | Very Low | Fast (route back) |
| Rolling | Replace instances one at a time | Medium | Slower |
| A/B Testing | Route by user attributes | Low | Fast |

---

## Internal Working

### Blue-Green Deployment:

```
┌────────────────────────────────────────────────────────────┐
│ BLUE-GREEN DEPLOYMENT                                       │
│                                                             │
│ STATE 1: Blue is LIVE                                      │
│                                                             │
│ Users ──→ Load Balancer ──→ BLUE (v1.0) ← Production     │
│                              ┌─────────────┐              │
│                              │ 3 instances  │              │
│                              └─────────────┘              │
│                                                             │
│                              GREEN (v1.1) ← Idle/Testing  │
│                              ┌─────────────┐              │
│                              │ 3 instances  │              │
│                              │ (deployed &  │              │
│                              │  tested)     │              │
│                              └─────────────┘              │
│                                                             │
│ STATE 2: Switch traffic to Green                           │
│                                                             │
│ Users ──→ Load Balancer ──→ GREEN (v1.1) ← Production    │
│                              ┌─────────────┐              │
│                              │ 3 instances  │              │
│                              └─────────────┘              │
│                                                             │
│                              BLUE (v1.0) ← Standby        │
│                              (keep alive for quick rollback)│
│                                                             │
│ ROLLBACK: Just switch LB back to Blue                     │
│ Takes: seconds                                             │
│                                                             │
│ Pro: Instant switch, instant rollback, full testing       │
│ Con: Double resources during deployment                    │
└────────────────────────────────────────────────────────────┘
```

### Canary Deployment:

```
┌────────────────────────────────────────────────────────────┐
│ CANARY DEPLOYMENT                                           │
│                                                             │
│ Phase 1: Deploy canary (5% traffic)                       │
│                                                             │
│ Users ──→ Load Balancer                                   │
│                │                                           │
│           ┌────┴────┐                                     │
│           │95%   5% │                                     │
│           ↓         ↓                                     │
│      ┌────────┐ ┌────────┐                               │
│      │ V1.0   │ │ V1.1   │ ← Canary                    │
│      │(9 pods)│ │(1 pod) │                               │
│      └────────┘ └────────┘                               │
│                                                             │
│ Monitor: error rate, latency, logs                        │
│                                                             │
│ Phase 2: Increase (25% traffic)                           │
│      ┌────────┐ ┌────────┐                               │
│      │ V1.0   │ │ V1.1   │                               │
│      │(7 pods)│ │(3 pods)│                               │
│      └────────┘ └────────┘                               │
│                                                             │
│ Phase 3: Full rollout (100%)                              │
│      ┌────────┐                                           │
│      │ V1.1   │                                           │
│      │(10 pods)│                                          │
│      └────────┘                                           │
│                                                             │
│ IF errors at any phase → roll back canary immediately     │
│ Only small % of users affected by bugs                    │
└────────────────────────────────────────────────────────────┘
```

### Rolling Deployment (Kubernetes Default):

```
┌────────────────────────────────────────────────────────────┐
│ ROLLING DEPLOYMENT                                          │
│                                                             │
│ maxSurge: 1, maxUnavailable: 0                            │
│                                                             │
│ Start:     [V1] [V1] [V1]                                │
│                                                             │
│ Step 1:    [V1] [V1] [V1] [V2←starting]                 │
│                                                             │
│ Step 2:    [V1] [V1] [V2✓] [V2←starting]                │
│            (V1 terminated)                                 │
│                                                             │
│ Step 3:    [V1] [V2✓] [V2✓] [V2←starting]              │
│            (V1 terminated)                                 │
│                                                             │
│ Step 4:    [V2✓] [V2✓] [V2✓]                            │
│            (all V1 terminated)                             │
│                                                             │
│ At any point: mix of V1 and V2 serving traffic           │
│ Rollback: kubectl rollout undo deployment                 │
│                                                             │
│ Pro: No extra resources, Kubernetes native                │
│ Con: V1 and V2 coexist (API compatibility needed)        │
└────────────────────────────────────────────────────────────┘
```

---

## Code

### Kubernetes Rolling Deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1          # 1 extra pod during update
      maxUnavailable: 0    # Never reduce below desired count
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        version: v2
    spec:
      containers:
        - name: order-service
          image: order-service:2.0.0
          ports:
            - containerPort: 8081
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 10
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
```

### Canary with Istio:

```yaml
# Deploy canary version
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service-canary
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: order-service
        version: v2
    spec:
      containers:
        - name: order-service
          image: order-service:2.0.0

---
# Traffic splitting
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
spec:
  hosts:
    - order-service
  http:
    - route:
        - destination:
            host: order-service
            subset: stable
          weight: 95
        - destination:
            host: order-service
            subset: canary
          weight: 5

---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: order-service
spec:
  host: order-service
  subsets:
    - name: stable
      labels:
        version: v1
    - name: canary
      labels:
        version: v2
```

### Automated Canary with Argo Rollouts:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: order-service
spec:
  replicas: 5
  strategy:
    canary:
      steps:
        - setWeight: 5
        - pause: {duration: 5m}     # Monitor for 5 min
        - setWeight: 25
        - pause: {duration: 5m}
        - setWeight: 50
        - pause: {duration: 5m}
        - setWeight: 100
      analysis:
        templates:
          - templateName: success-rate
        startingStep: 1
        args:
          - name: service-name
            value: order-service

---
# Auto-rollback if error rate > 5%
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  metrics:
    - name: success-rate
      interval: 1m
      successCondition: result[0] >= 0.95
      provider:
        prometheus:
          address: http://prometheus:9090
          query: |
            sum(rate(http_requests_total{service="{{args.service-name}}",status!~"5.."}[5m])) /
            sum(rate(http_requests_total{service="{{args.service-name}}"}[5m]))
```

---

## Interview Questions

1. **Blue-Green vs Canary deployment?**
   - Blue-Green: Switch 100% traffic at once between two environments. Fast rollback but needs double resources. Canary: Gradually shift traffic (5% → 100%). Lower risk but slower. Canary catches issues before all users affected.

2. **How does Kubernetes rolling deployment work?**
   - Creates new pods with new version one at a time. Old pods terminated as new ones become ready. Uses readinessProbe to know when new pod is ready. maxSurge and maxUnavailable control the pace.

3. **How to rollback a failed deployment?**
   - Kubernetes: `kubectl rollout undo deployment/name`. Blue-Green: Switch LB back to previous color. Canary: Set weight back to 0% for new version. All should be automated with health checks.

4. **What is a readiness probe and why is it important for deployments?**
   - HTTP check that tells Kubernetes when a pod is ready to receive traffic. Without it, traffic is sent to pods still starting up (errors). Critical for zero-downtime rolling deployments.

5. **How to handle database migrations during deployment?**
   - Backward-compatible migrations only. Expand-contract pattern: add new column → deploy code using both → migrate data → remove old column. Never break old version.

---

## Best Practices

1. **Readiness probes** — Never serve traffic before ready
2. **Graceful shutdown** — Drain connections before terminating
3. **Backward-compatible changes** — V1 and V2 must coexist during rollout
4. **Automated rollback** — Health metrics trigger automatic rollback
5. **Database migrations separate from deploys** — Deploy schema first, then code
6. **Feature flags** — Decouple deploy from release
7. **Canary + metrics** — Automated canary with Prometheus-based analysis
