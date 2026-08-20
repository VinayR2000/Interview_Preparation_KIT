# 19. RBAC & Security ⭐⭐⭐

---

## Theory

Kubernetes security is built on authentication, authorization, and admission control. RBAC (Role-Based Access Control) is the standard authorization mechanism.

### Authentication

```
"Who are you?"

Authentication methods:
  1. X.509 Client Certificates (most common for admins)
  2. Bearer Tokens (static tokens, bootstrap tokens)
  3. Service Account Tokens (for pods/applications)
  4. OpenID Connect (OIDC) — OAuth2 providers (Google, Azure AD)
  5. Webhook Token Authentication (custom auth servers)

All API requests must be authenticated:
  kubectl → sends certificate/token → API Server → verifies identity
```

### Authorization

```
"What can you do?"

Authorization modes:
  1. RBAC (Role-Based Access Control) — standard
  2. ABAC (Attribute-Based) — legacy, less used
  3. Node Authorization — kubelet permissions
  4. Webhook — external authorization server

RBAC answers: Can user X perform action Y on resource Z in namespace N?
```

### RBAC

```
RBAC has four main objects:

1. Role:              Permissions in a namespace
2. ClusterRole:       Permissions cluster-wide
3. RoleBinding:       Grants Role to user/SA in a namespace
4. ClusterRoleBinding: Grants ClusterRole cluster-wide

RBAC Rule Structure:
  - apiGroups: ["", "apps", "batch"]    # API group
  - resources: ["pods", "deployments"]   # What resources
  - verbs: ["get", "list", "create"]     # What actions

Available verbs:
  get, list, watch, create, update, patch, delete, deletecollection
```

### ServiceAccount

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: order-service-sa
  namespace: production
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456:role/order-service
```

```
ServiceAccount: Identity for processes running in pods

Every pod runs with a ServiceAccount:
  - Default: "default" SA in its namespace (minimal permissions)
  - Custom: Specify in pod spec for specific permissions

Token automatically mounted at:
  /var/run/secrets/kubernetes.io/serviceaccount/token

Use case: 
  - Pod needs to call K8s API (list pods, read configmaps)
  - AWS IAM integration (IRSA: IAM Roles for Service Accounts)
```

### Role

```yaml
# Namespace-scoped permissions
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
  namespace: production
rules:
- apiGroups: [""]
  resources: ["pods", "pods/log"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["get", "list"]
```

### RoleBinding

```yaml
# Binds Role to subjects in a namespace
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: read-pods-binding
  namespace: production
subjects:
- kind: ServiceAccount
  name: order-service-sa
  namespace: production
- kind: User
  name: developer@company.com
- kind: Group
  name: backend-team
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

### ClusterRole

```yaml
# Cluster-wide permissions
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: cluster-monitoring
rules:
- apiGroups: [""]
  resources: ["nodes", "pods", "services"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["metrics.k8s.io"]
  resources: ["pods", "nodes"]
  verbs: ["get", "list"]
- nonResourceURLs: ["/metrics", "/healthz"]
  verbs: ["get"]
```

### ClusterRoleBinding

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: monitoring-binding
subjects:
- kind: ServiceAccount
  name: prometheus
  namespace: monitoring
roleRef:
  kind: ClusterRole
  name: cluster-monitoring
  apiGroup: rbac.authorization.k8s.io
```

### Least Privilege

```
Principle: Grant MINIMUM permissions needed

Bad:  ClusterRole with "*" verbs on all resources (admin to everyone)
Good: Namespace-scoped Role with specific resources and verbs

Example progression:
  Developer: get, list, watch pods/logs in their namespace
  DevOps:    create, update deployments in staging
  Admin:     full access to cluster resources
  Pod SA:    only read configmaps/secrets it needs
```

### NetworkPolicy

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: backend-policy
  namespace: production
spec:
  podSelector:
    matchLabels:
      role: backend
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          role: frontend
    - namespaceSelector:
        matchLabels:
          purpose: monitoring
    ports:
    - port: 8080
      protocol: TCP
  egress:
  - to:
    - podSelector:
        matchLabels:
          role: database
    ports:
    - port: 5432
```

### Pod Security

```
Pod Security Standards (PSS) — replaces PodSecurityPolicy:

Levels:
  Privileged: Unrestricted (system workloads only)
  Baseline:   Minimally restrictive (common apps)
  Restricted: Heavily restricted (best practices)

Modes:
  enforce: Reject violating pods
  audit:   Log violations
  warn:    Warn user

Applied at namespace level:
  kubectl label namespace production \
    pod-security.kubernetes.io/enforce=restricted \
    pod-security.kubernetes.io/warn=restricted
```

### SecurityContext

```yaml
spec:
  securityContext:                    # Pod level
    runAsUser: 1000
    runAsGroup: 3000
    fsGroup: 2000
    seccompProfile:
      type: RuntimeDefault
  containers:
  - name: app
    securityContext:                  # Container level
      runAsNonRoot: true
      readOnlyRootFilesystem: true
      allowPrivilegeEscalation: false
      capabilities:
        drop:
        - ALL
        add:
        - NET_BIND_SERVICE           # Only if needed (port < 1024)
```

### runAsUser

```
runAsUser: 1000
  - Container process runs as UID 1000
  - NOT root (UID 0)
  - Must match user in container image or be numeric
```

### runAsNonRoot

```
runAsNonRoot: true
  - K8s validates that container doesn't run as root
  - If image's USER is root, pod fails to start
  - Defense-in-depth: even if image misconfigured
```

### Capabilities

```
Linux capabilities: Fine-grained root privileges

Default Docker capabilities (dropped in K8s restricted):
  NET_RAW, MKNOD, etc.

Best practice:
  Drop ALL, add back only what's needed

capabilities:
  drop: ["ALL"]
  add:
  - NET_BIND_SERVICE    # Bind to ports < 1024
  - SYS_TIME           # Modify system clock (rare)
```

### Read-Only Root Filesystem

```
readOnlyRootFilesystem: true
  - Container can't write to its filesystem
  - Prevents malware installation
  - Forces writing only to mounted volumes

Must provide writable volumes for:
  /tmp, /var/log, or app-specific write paths
  Use emptyDir volumes for these
```

---

## Internal Working

```
API Request Authorization Flow:

1. Request arrives at API Server
2. Authentication: Identify the user/SA
3. Authorization (RBAC):
   - API Server checks all RoleBindings/ClusterRoleBindings
   - Finds bindings for this subject
   - Checks if any bound Role allows the requested action
   - Decision: Allow or Deny
4. Admission Control: Final validation

RBAC evaluation:
  User: developer@company.com
  Request: GET /api/v1/namespaces/production/pods
  
  Check: Is there a RoleBinding in "production" that:
    - References this user as a subject
    - Points to a Role with:
      - apiGroups: [""]
      - resources: ["pods"]
      - verbs: ["get"] or ["*"]
  
  If found → Allow
  If not found → Deny (deny by default)
```

---

## Interview Questions

### Q1: Explain the difference between Role/ClusterRole and RoleBinding/ClusterRoleBinding.

**A:**
- **Role:** Defines permissions (what can be done) within a single namespace
- **ClusterRole:** Defines permissions cluster-wide or for non-namespaced resources
- **RoleBinding:** Grants a Role to subjects within a namespace
- **ClusterRoleBinding:** Grants a ClusterRole to subjects cluster-wide

You can also use RoleBinding to bind a ClusterRole but scoped to one namespace (useful for reusable permission sets).

### Q2: How do you secure a Pod in production?

**A:** Apply defense-in-depth:
1. SecurityContext: runAsNonRoot, readOnlyRootFilesystem, drop all capabilities
2. ServiceAccount: Custom SA with minimal RBAC permissions
3. NetworkPolicy: Restrict ingress/egress to required services only
4. Resource limits: Prevent resource abuse
5. Image security: Scan images, use distroless base, pin versions
6. Pod Security Standards: Apply "restricted" level to namespace

### Q3: What is IRSA (IAM Roles for Service Accounts) in EKS?

**A:** IRSA allows Kubernetes ServiceAccounts to assume AWS IAM roles without storing credentials. How it works:
1. Create IAM role with trust policy for OIDC provider
2. Annotate K8s ServiceAccount with IAM role ARN
3. Pod using that SA gets temporary AWS credentials injected
4. AWS SDK automatically uses these credentials

Benefits: No hardcoded AWS credentials, fine-grained access, auto-rotation.

### Q4: What is the principle of least privilege in K8s RBAC?

**A:** Grant only the minimum permissions needed:
- Use namespace-scoped Roles (not ClusterRoles) when possible
- Specify exact resources (not `*`)
- Specify exact verbs (not `*`)
- Create separate ServiceAccounts per application
- Avoid cluster-admin binding
- Audit RBAC regularly (who has what access)
- Use time-bound access for break-glass scenarios

### Q5: How do NetworkPolicies work?

**A:** NetworkPolicies are firewall rules for pod traffic:
- Default: All traffic allowed (no NetworkPolicy = allow all)
- Once any NetworkPolicy selects a pod: default becomes deny
- Must explicitly allow desired traffic (ingress/egress rules)
- Rules match by pod labels, namespace labels, or IP blocks
- Requires CNI that supports NetworkPolicy (Calico, Cilium — not Flannel)

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Default ServiceAccount everywhere | Overprivileged pods | Create per-app SA |
| ClusterRoleBinding for all users | Too broad access | Use namespace RoleBinding |
| No NetworkPolicy | All pods can talk | Default deny + allow rules |
| Running as root | Container compromise = node compromise | runAsNonRoot: true |
| Not dropping capabilities | Extra attack surface | drop: ALL |

---

## Best Practices

1. **Least privilege RBAC** — specific verbs, resources, namespaces
2. **One ServiceAccount per application** — not shared
3. **Default deny NetworkPolicy** — then allow explicitly
4. **SecurityContext on every pod** — runAsNonRoot, readOnly, drop ALL
5. **Audit RBAC** — regularly review who has what access
6. **Use Pod Security Standards** — enforce "restricted" in production
7. **IRSA/Workload Identity** — no hardcoded cloud credentials
8. **Secret encryption at rest** — enable in API Server config

---

## Related Topics

- [18. Namespaces](./18-namespaces.md)
- [31. Network Policies](./31-network-policies.md)
- [09. ConfigMap & Secrets](./09-configmaps-and-secrets.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
