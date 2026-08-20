# 20. YAML ⭐⭐⭐

---

## Theory

All Kubernetes objects are defined using YAML manifests — declarative configuration that describes the desired state.

### Kubernetes Manifest

```yaml
# Every K8s manifest has these four top-level fields:
apiVersion: apps/v1       # API version for this resource
kind: Deployment          # Type of resource
metadata:                 # Identity and organization
  name: my-app
  namespace: production
  labels:
    app: my-app
spec:                     # Desired state (what you configure)
  replicas: 3
  ...
```

### apiVersion

```
Common API versions:
  v1:              Core resources (Pod, Service, ConfigMap, Secret, Namespace)
  apps/v1:         Deployment, StatefulSet, DaemonSet, ReplicaSet
  batch/v1:        Job, CronJob
  networking.k8s.io/v1: Ingress, NetworkPolicy
  rbac.authorization.k8s.io/v1: Role, ClusterRole, Bindings
  storage.k8s.io/v1: StorageClass
  autoscaling/v2:  HPA

Find API version: kubectl api-resources | grep Deployment
```

### kind

```
Resource type:
  Pod, Deployment, Service, ConfigMap, Secret, Ingress,
  StatefulSet, DaemonSet, Job, CronJob, Namespace,
  Role, RoleBinding, ClusterRole, ClusterRoleBinding,
  PersistentVolume, PersistentVolumeClaim, StorageClass,
  NetworkPolicy, HorizontalPodAutoscaler, ServiceAccount
```

### metadata

```yaml
metadata:
  name: order-service              # Required: unique within namespace
  namespace: production            # Optional: default if omitted
  labels:                          # Key-value for selecting/organizing
    app: order-service
    version: v2.1.0
    team: backend
    environment: production
  annotations:                     # Non-identifying metadata
    description: "Order processing service"
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    kubectl.kubernetes.io/last-applied-configuration: "..."
```

### spec

```yaml
# spec is different for each kind:

# Deployment spec:
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-app
  template:
    metadata:
      labels:
        app: my-app
    spec:
      containers:
      - name: app
        image: my-app:v1

# Service spec:
spec:
  type: ClusterIP
  selector:
    app: my-app
  ports:
  - port: 80
    targetPort: 8080
```

### labels

```yaml
# Labels: Key-value pairs for identifying and selecting objects
metadata:
  labels:
    app: order-service       # Application name
    version: v2.1.0         # Version
    component: backend      # Component type
    team: platform          # Owning team
    environment: production # Environment

# Label conventions:
#   app.kubernetes.io/name: order-service
#   app.kubernetes.io/version: 2.1.0
#   app.kubernetes.io/component: backend
#   app.kubernetes.io/part-of: ecommerce
#   app.kubernetes.io/managed-by: helm
```

### selectors

```yaml
# Equality-based:
selector:
  matchLabels:
    app: order-service
    environment: production

# Set-based:
selector:
  matchExpressions:
  - key: environment
    operator: In
    values: [production, staging]
  - key: team
    operator: NotIn
    values: [legacy]
  - key: critical
    operator: Exists
```

### annotations

```yaml
# Annotations: Non-identifying metadata (not used for selection)
metadata:
  annotations:
    # Ingress configuration
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    
    # Monitoring
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    
    # Deployment info
    kubernetes.io/change-cause: "Update to v2.1.0"
    
    # Custom
    owner: "backend-team@company.com"
    docs: "https://wiki.company.com/order-service"
```

### Environment Variables

```yaml
spec:
  containers:
  - name: app
    env:
    # Static value
    - name: APP_ENV
      value: "production"
    
    # From ConfigMap
    - name: DB_URL
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: database_url
    
    # From Secret
    - name: DB_PASSWORD
      valueFrom:
        secretKeyRef:
          name: db-secret
          key: password
    
    # From Pod metadata
    - name: POD_NAME
      valueFrom:
        fieldRef:
          fieldPath: metadata.name
    - name: POD_IP
      valueFrom:
        fieldRef:
          fieldPath: status.podIP
    - name: NODE_NAME
      valueFrom:
        fieldRef:
          fieldPath: spec.nodeName
    
    # From resource limits
    - name: MEMORY_LIMIT
      valueFrom:
        resourceFieldRef:
          containerName: app
          resource: limits.memory
```

### Volumes

```yaml
spec:
  containers:
  - name: app
    volumeMounts:
    - name: config
      mountPath: /app/config
      readOnly: true
    - name: data
      mountPath: /app/data
    - name: tmp
      mountPath: /tmp
  
  volumes:
  - name: config
    configMap:
      name: app-config
  - name: data
    persistentVolumeClaim:
      claimName: app-data-pvc
  - name: tmp
    emptyDir: {}
```

### Probes

```yaml
containers:
- name: app
  startupProbe:
    httpGet:
      path: /health
      port: 8080
    failureThreshold: 30
    periodSeconds: 5
  livenessProbe:
    httpGet:
      path: /health
      port: 8080
    periodSeconds: 10
    failureThreshold: 3
  readinessProbe:
    httpGet:
      path: /ready
      port: 8080
    periodSeconds: 5
    failureThreshold: 3
```

### Resources

```yaml
containers:
- name: app
  resources:
    requests:
      cpu: "250m"
      memory: "256Mi"
    limits:
      cpu: "500m"
      memory: "512Mi"
```

### Multi-Document YAML

```yaml
# Multiple resources in one file, separated by ---
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  key: value

---
apiVersion: v1
kind: Service
metadata:
  name: my-app
spec:
  selector:
    app: my-app
  ports:
  - port: 80

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3
  ...
```

---

## Interview Questions

### Q1: What are the mandatory fields in a Kubernetes manifest?

**A:** Every manifest requires: `apiVersion` (API version for the resource), `kind` (resource type), `metadata.name` (unique identifier). Most resources also need `spec` (desired state). The `status` field is managed by K8s (not user-defined).

### Q2: What is the difference between labels and annotations?

**A:**
- **Labels:** Key-value pairs used for identification and selection. Can be used in selectors (`selector.matchLabels`). Keep them small and identifying. Used by controllers, services, and queries.
- **Annotations:** Key-value pairs for non-identifying metadata. Can't be used in selectors. Can store large data (URLs, JSON, timestamps). Used by tools, humans, and automation.

### Q3: How do you manage multiple environments with YAML?

**A:** Options:
1. **Kustomize:** Base manifests + overlays per environment (built into kubectl)
2. **Helm:** Templates with values.yaml per environment
3. **Separate directories:** `k8s/dev/`, `k8s/staging/`, `k8s/prod/`
4. **Variable substitution:** envsubst or sed in CI/CD pipeline

Kustomize and Helm are the standard approaches.

---

## Best Practices

1. **Use multi-document YAML** — related resources in one file (ConfigMap + Deployment + Service)
2. **Consistent labeling** — use recommended labels (`app.kubernetes.io/*`)
3. **Always specify namespace** — don't rely on context
4. **Use annotations for metadata** — descriptions, links, ownership
5. **Pin API versions** — don't use alpha/beta in production
6. **Validate before applying** — `kubectl apply --dry-run=client -f manifest.yaml`
7. **Version control all manifests** — GitOps
8. **Use `kubectl explain`** — discover fields: `kubectl explain deployment.spec.strategy`

---

## Related Topics

- [21. kubectl](./21-kubectl.md)
- [22. Helm](./22-helm.md)
- [03. Kubernetes Objects](./03-kubernetes-objects.md)
