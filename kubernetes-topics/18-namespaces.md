# 18. Namespaces

---

## Theory

**Namespaces** provide a mechanism for isolating groups of resources within a single cluster — virtual clusters within a physical cluster.

### What is Namespace?

```
Namespace = Virtual cluster partition

Use cases:
  - Environment isolation (dev, staging, production)
  - Team isolation (team-a, team-b)
  - Application isolation (frontend, backend, data)
  - Multi-tenancy (customer-a, customer-b)

Default namespaces:
  default:          Objects with no namespace specified
  kube-system:      System components (CoreDNS, kube-proxy)
  kube-public:      Publicly readable (cluster-info)
  kube-node-lease:  Node heartbeat leases
```

### Namespace Isolation

```
Namespace provides:
  ✓ Resource naming isolation (same name in different NS)
  ✓ RBAC scoping (roles apply per namespace)
  ✓ ResourceQuota per namespace
  ✓ NetworkPolicy per namespace
  ✓ LimitRange per namespace

Namespace does NOT provide:
  ✗ Network isolation (by default, all pods can talk)
  ✗ Node isolation (pods from any NS can run on any node)
  ✗ Complete security isolation (not a security boundary)

For true isolation: Use NetworkPolicy + RBAC + ResourceQuota together
```

### Resource Quota

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: team-quota
  namespace: team-backend
spec:
  hard:
    requests.cpu: "20"
    requests.memory: "40Gi"
    limits.cpu: "40"
    limits.memory: "80Gi"
    pods: "100"
    services: "20"
    persistentvolumeclaims: "30"
    configmaps: "50"
    secrets: "50"
```

### LimitRange

```yaml
apiVersion: v1
kind: LimitRange
metadata:
  name: default-limits
  namespace: team-backend
spec:
  limits:
  - type: Container
    default:
      cpu: "500m"
      memory: "512Mi"
    defaultRequest:
      cpu: "200m"
      memory: "256Mi"
    max:
      cpu: "4"
      memory: "8Gi"
    min:
      cpu: "50m"
      memory: "64Mi"
```

### Namespace-Level Resources

```
Namespaced resources (most resources):
  Pods, Deployments, Services, ConfigMaps, Secrets,
  Roles, RoleBindings, PVCs, Ingress, NetworkPolicy

Cluster-scoped resources (NOT namespaced):
  Nodes, PersistentVolumes, StorageClasses, Namespaces,
  ClusterRoles, ClusterRoleBindings, IngressClasses

kubectl api-resources --namespaced=true   # List namespaced resources
kubectl api-resources --namespaced=false  # List cluster-scoped resources
```

### Multi-Tenant Clusters

```
Strategy: Namespace per tenant + RBAC + NetworkPolicy + ResourceQuota

  ┌─── Cluster ──────────────────────────────────────┐
  │                                                    │
  │  ┌── NS: tenant-a ──┐  ┌── NS: tenant-b ──┐    │
  │  │ ResourceQuota     │  │ ResourceQuota     │    │
  │  │ NetworkPolicy     │  │ NetworkPolicy     │    │
  │  │ RBAC (own SA)     │  │ RBAC (own SA)     │    │
  │  │ [pods][pods]      │  │ [pods][pods]      │    │
  │  └──────────────────┘  └──────────────────┘    │
  │                                                    │
  │  NetworkPolicy: deny all cross-namespace traffic  │
  └────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is a Namespace and when should you use it?

**A:** Namespace is a virtual cluster partition for organizing and isolating resources. Use for: environment separation (dev/staging/prod), team isolation, multi-tenancy, and resource quota enforcement. Don't over-use — small projects can work in default namespace. Use when you need RBAC boundaries or resource limits per group.

### Q2: How do you communicate between namespaces?

**A:** By default, all pods can communicate across namespaces. Use full DNS: `service-name.namespace.svc.cluster.local`. To restrict: apply NetworkPolicy with namespaceSelector. Cross-namespace RBAC requires ClusterRole or RoleBinding referencing subjects in other namespaces.

### Q3: Is a namespace a security boundary?

**A:** No — namespace alone is not a security boundary. By default, pods in different namespaces can communicate freely. For true isolation, combine: Namespace + NetworkPolicy (network isolation) + RBAC (access control) + ResourceQuota (resource limits) + Pod Security Standards (runtime security). For hard multi-tenancy, consider separate clusters.

---

## Best Practices

1. **Use namespaces for organization** — minimum per environment (dev, staging, prod)
2. **Apply ResourceQuota** on every team/tenant namespace
3. **Set LimitRange** — default resources prevent BestEffort pods
4. **Use NetworkPolicy** — restrict cross-namespace traffic
5. **RBAC per namespace** — least privilege per team
6. **Don't over-partition** — too many namespaces add management complexity

---

## Related Topics

- [19. RBAC & Security](./19-rbac-and-security.md)
- [15. Resources](./15-resources.md)
- [31. Network Policies](./31-network-policies.md)
