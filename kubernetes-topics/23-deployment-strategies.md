# 23. Kubernetes Deployment Strategies ⭐⭐

---

## Theory

Deployment strategies determine how new versions of applications are rolled out to production with varying tradeoffs between speed, safety, and resource usage.

### Rolling Deployment

```
Default K8s strategy. Gradually replaces old pods with new.

[v1][v1][v1][v1] → [v1][v1][v1][v2] → [v1][v1][v2][v2] → [v2][v2][v2][v2]

Pros: Zero downtime, no extra infrastructure, automatic
Cons: Slow rollout, both versions running simultaneously
Config: strategy.type: RollingUpdate, maxSurge, maxUnavailable
```

### Recreate

```
Kill all old pods, then create all new pods.

[v1][v1][v1] → [ ][ ][ ] → [v2][v2][v2]
              (DOWNTIME)

Pros: Simple, clean switch, no version mixing
Cons: Causes downtime
Use: Schema-breaking DB changes, dev environments
Config: strategy.type: Recreate
```

### Blue-Green

```
Two identical environments. Switch traffic instantly.

Blue (v1): [v1][v1][v1] ← traffic (production)
Green (v2): [v2][v2][v2]  (staged, tested)

Switch: Update Service selector to point to Green
Rollback: Switch selector back to Blue

Implementation:
  1. Deploy v2 with different label (version: v2)
  2. Test v2 internally
  3. Update Service selector: version: v1 → version: v2
  4. Instant traffic switch
  5. Keep v1 running for quick rollback
```

```yaml
# Service selector switch:
apiVersion: v1
kind: Service
metadata:
  name: my-app
spec:
  selector:
    app: my-app
    version: v2      # ← Change this from v1 to v2
```

### Canary

```
Route small percentage of traffic to new version. Monitor. Gradually increase.

Step 1: 90% v1, 10% v2 (canary)
Step 2: 75% v1, 25% v2 (if metrics OK)
Step 3: 50% v1, 50% v2
Step 4: 0% v1, 100% v2

Implementation options:
  1. Multiple Deployments with pod ratio (simple)
  2. Istio traffic splitting (precise percentages)
  3. Ingress annotations (nginx canary)
  4. Argo Rollouts (automated canary)
```

```yaml
# Simple canary with pod count:
# Main: 9 replicas (v1)
# Canary: 1 replica (v2)
# Service selects both via shared label app: my-app
# Result: ~10% traffic to v2

# NGINX Ingress canary:
metadata:
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"  # 10% to canary
```

### A/B Testing

```
Route traffic based on conditions (headers, cookies, user attributes).

If user.country == "US" → v2 (new feature)
If user.country != "US" → v1 (old version)

Requires Istio, Nginx, or similar for header-based routing.
Not native K8s — needs traffic management layer.
```

### Rolling Update

```yaml
# Detailed configuration
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%          # Max extra pods during update
      maxUnavailable: 25%    # Max pods unavailable during update
```

### Rollback

```bash
# K8s native rollback
kubectl rollout undo deployment/my-app
kubectl rollout undo deployment/my-app --to-revision=3

# Helm rollback
helm rollback my-release 2

# Blue-Green rollback
# Switch service selector back to v1 (instant)

# Canary rollback
# Scale canary to 0 or delete canary deployment
```

---

## Interview Questions

### Q1: Compare Blue-Green vs Canary deployment strategies.

**A:**
- **Blue-Green:** All-or-nothing switch. Instant cutover. Higher resource cost (2x). Simple rollback (switch back). Good for: confident releases, database migrations.
- **Canary:** Gradual rollout (10% → 50% → 100%). Risk mitigation. Catches issues early. Lower blast radius. Good for: risk-averse releases, feature testing.

### Q2: How would you implement a canary deployment in Kubernetes?

**A:** Options from simple to advanced:
1. **Pod ratio:** Two Deployments with shared label. 9 pods v1 + 1 pod v2 = 10% canary
2. **NGINX Ingress:** Canary annotations with weight-based routing
3. **Istio VirtualService:** Precise traffic splitting with percentage-based routing
4. **Argo Rollouts:** Automated canary with analysis and promotion/rollback

### Q3: When would you use Recreate strategy?

**A:** When both versions can't run simultaneously:
- Database schema migration incompatible with old code
- Single-instance lock (only one pod can hold a resource)
- Legacy apps that can't handle mixed versions
- Resource-constrained environments (can't afford double pods)

---

## Best Practices

1. **Rolling Update** as default for most apps
2. **Canary** for critical services (gradual, measurable)
3. **Blue-Green** when instant rollback is critical
4. **Automate** with Argo Rollouts or Flagger (metrics-driven promotion)
5. **Monitor during rollout** — error rates, latency, 5xx
6. **Feature flags** complement deployment strategies (decouple deploy from release)

---

## Related Topics

- [05. Deployments](./05-deployments.md)
- [22. Helm](./22-helm.md)
- [34. Kubernetes + CI/CD](./34-kubernetes-cicd.md)
- [36. Production Architecture](./36-production-architecture.md)
