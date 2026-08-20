# 31. Network Policies ⭐⭐

---

## Theory

**NetworkPolicy** is a Kubernetes resource that controls traffic flow between pods — essentially a firewall for pod communication.

### What is NetworkPolicy?

```
Default behavior: All pods can talk to all pods (no isolation)
With NetworkPolicy: Only explicitly allowed traffic passes

NetworkPolicy is:
  - Namespace-scoped
  - Additive (multiple policies combine with OR)
  - Applied via pod selector (which pods this policy affects)
  - Requires CNI that supports it (Calico, Cilium, NOT Flannel)
```

### Ingress Rules

```yaml
# Control incoming traffic to selected pods
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-frontend-to-backend
  namespace: production
spec:
  podSelector:
    matchLabels:
      role: backend           # Apply to backend pods
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          role: frontend      # Allow from frontend pods
    ports:
    - port: 8080
      protocol: TCP
```

### Egress Rules

```yaml
# Control outgoing traffic from selected pods
spec:
  podSelector:
    matchLabels:
      role: backend
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          role: database
    ports:
    - port: 5432
  - to:                        # Allow DNS
    - namespaceSelector: {}
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    ports:
    - port: 53
      protocol: UDP
```

### Namespace Selectors

```yaml
# Allow traffic from specific namespaces
ingress:
- from:
  - namespaceSelector:
      matchLabels:
        purpose: monitoring    # From monitoring namespace
    podSelector:
      matchLabels:
        app: prometheus        # Specifically prometheus pods
```

### Pod Selectors

```yaml
# Select by pod labels
podSelector:
  matchLabels:
    app: order-service
    tier: backend

# Empty selector = all pods in namespace
podSelector: {}
```

### IP Blocks

```yaml
# Allow/deny specific IP ranges
ingress:
- from:
  - ipBlock:
      cidr: 10.0.0.0/8        # Allow from this range
      except:
      - 10.0.1.0/24           # Except this subnet
```

### Default Deny

```yaml
# Default deny ALL ingress in namespace
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  namespace: production
spec:
  podSelector: {}             # All pods in namespace
  policyTypes:
  - Ingress                   # No ingress rules = deny all incoming

---
# Default deny ALL egress in namespace
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-egress
  namespace: production
spec:
  podSelector: {}
  policyTypes:
  - Egress                    # No egress rules = deny all outgoing

---
# Default deny ALL traffic (both directions)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: production
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
```

---

## Code

### Production Network Policy Set:

```yaml
# 1. Default deny all in namespace
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: production
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress

---
# 2. Allow DNS for all pods (required!)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns
  namespace: production
spec:
  podSelector: {}
  policyTypes:
  - Egress
  egress:
  - to:
    - namespaceSelector: {}
      podSelector:
        matchLabels:
          k8s-app: kube-dns
    ports:
    - port: 53
      protocol: UDP
    - port: 53
      protocol: TCP

---
# 3. Frontend can receive from Ingress, talk to Backend
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: frontend-policy
  namespace: production
spec:
  podSelector:
    matchLabels:
      tier: frontend
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          app: ingress-nginx
    ports:
    - port: 80
  egress:
  - to:
    - podSelector:
        matchLabels:
          tier: backend
    ports:
    - port: 8080

---
# 4. Backend can receive from Frontend, talk to Database
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: backend-policy
  namespace: production
spec:
  podSelector:
    matchLabels:
      tier: backend
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          tier: frontend
    ports:
    - port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          tier: database
    ports:
    - port: 5432
```

---

## Interview Questions

### Q1: How do NetworkPolicies work?

**A:** NetworkPolicies are firewall rules for pod traffic:
- By default, all traffic is allowed (no isolation)
- Once ANY policy selects a pod, default becomes DENY for that direction
- Must explicitly allow desired traffic via ingress/egress rules
- Policies are additive (union of all matching policies)
- Requires CNI support (Calico, Cilium — NOT Flannel)

### Q2: What is the recommended approach for production NetworkPolicies?

**A:** Start with default-deny-all, then explicitly allow required traffic:
1. Default deny all ingress + egress in namespace
2. Allow DNS egress for all pods (essential!)
3. Allow specific ingress/egress per application tier
4. Allow monitoring/observability access from monitoring namespace

This follows the principle of least privilege for networking.

### Q3: What happens if you forget to allow DNS in a default-deny policy?

**A:** All service discovery breaks! Pods can't resolve service names to IPs. DNS uses port 53 (UDP and TCP) to CoreDNS in kube-system. You MUST always include a DNS egress rule when using default-deny-egress.

---

## Best Practices

1. **Start with default deny** — then allow explicitly
2. **Always allow DNS** — first rule after deny-all
3. **Use labels consistently** — tiers (frontend, backend, database)
4. **Test policies** before production — use debug pods
5. **Use CNI that supports NetworkPolicy** — Calico or Cilium
6. **Allow monitoring access** — Prometheus needs to scrape pods
7. **Document traffic requirements** — which service talks to which

---

## Related Topics

- [07. Networking](./07-networking.md)
- [19. RBAC & Security](./19-rbac-and-security.md)
- [08. Ingress](./08-ingress.md)
- [18. Namespaces](./18-namespaces.md)
