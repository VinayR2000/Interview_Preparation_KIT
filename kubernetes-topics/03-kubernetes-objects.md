# 3. Kubernetes Objects ⭐⭐⭐

---

## Theory

Kubernetes objects are persistent entities in the Kubernetes system that represent the state of your cluster. They describe what containerized applications are running, what resources are available to them, and policies around behavior.

### Object Structure

Every Kubernetes object has a common structure:

```yaml
apiVersion: apps/v1          # API group/version
kind: Deployment             # Object type
metadata:                    # Object identity
  name: my-app
  namespace: default
  labels:
    app: my-app
  annotations:
    description: "My application"
spec:                        # Desired state (YOU define this)
  replicas: 3
  ...
status:                      # Current state (K8s fills this)
  availableReplicas: 3
  ...
```

### Core Objects Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                    KUBERNETES OBJECTS                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  WORKLOADS:                                                       │
│  ┌──────┐ ┌────────────┐ ┌────────────┐ ┌─────────────┐        │
│  │ Pod  │ │ ReplicaSet │ │ Deployment │ │ StatefulSet │        │
│  └──────┘ └────────────┘ └────────────┘ └─────────────┘        │
│  ┌───────────┐ ┌─────┐ ┌─────────┐                             │
│  │ DaemonSet │ │ Job │ │ CronJob │                             │
│  └───────────┘ └─────┘ └─────────┘                             │
│                                                                    │
│  NETWORKING:                                                      │
│  ┌─────────┐ ┌─────────┐ ┌───────────────┐                     │
│  │ Service │ │ Ingress │ │ NetworkPolicy │                     │
│  └─────────┘ └─────────┘ └───────────────┘                     │
│                                                                    │
│  CONFIGURATION:                                                   │
│  ┌───────────┐ ┌────────┐ ┌───────────┐                        │
│  │ ConfigMap │ │ Secret │ │ Namespace │                        │
│  └───────────┘ └────────┘ └───────────┘                        │
│                                                                    │
│  SECURITY/RBAC:                                                   │
│  ┌────────────────┐ ┌──────┐ ┌─────────────┐                   │
│  │ ServiceAccount │ │ Role │ │ RoleBinding │                   │
│  └────────────────┘ └──────┘ └─────────────┘                   │
│  ┌─────────────┐ ┌────────────────────┐                        │
│  │ ClusterRole │ │ ClusterRoleBinding │                        │
│  └─────────────┘ └────────────────────┘                        │
│                                                                    │
│  STORAGE:                                                         │
│  ┌──────────────────┐ ┌───────────────────────┐                 │
│  │ PersistentVolume │ │ PersistentVolumeClaim │                 │
│  └──────────────────┘ └───────────────────────┘                 │
│  ┌──────────────┐                                                │
│  │ StorageClass │                                                │
│  └──────────────┘                                                │
└──────────────────────────────────────────────────────────────────┘
```

### Pod

The smallest deployable unit. A group of one or more containers with shared storage and network.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-pod
spec:
  containers:
  - name: app
    image: nginx:1.25
    ports:
    - containerPort: 80
```

### ReplicaSet

Ensures a specified number of pod replicas are running at all times.

```yaml
apiVersion: apps/v1
kind: ReplicaSet
metadata:
  name: my-app-rs
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
```

### Deployment

Manages ReplicaSets and provides declarative updates, rolling deployments, and rollbacks.

```
Deployment → manages → ReplicaSet → manages → Pods

Deployment provides:
  - Rolling updates
  - Rollback capability
  - Scaling
  - Pause/Resume deployments
```

### StatefulSet

For stateful applications needing stable identity and persistent storage.

```
Use cases: Databases (PostgreSQL, MySQL), Kafka brokers, Elasticsearch

Provides:
  - Stable, unique network identifiers (pod-0, pod-1, pod-2)
  - Stable, persistent storage (PVC per pod)
  - Ordered, graceful deployment and scaling
  - Ordered, automated rolling updates
```

### DaemonSet

Ensures a copy of a Pod runs on every (or selected) node.

```
Use cases:
  - Log collection (fluentd, fluent-bit)
  - Node monitoring (node-exporter, datadog-agent)
  - Network plugins (calico, weave)
  - Storage daemons (ceph, gluster)
```

### Job

Creates one or more Pods and ensures they complete successfully.

```
Use cases: Database migrations, batch processing, one-time scripts
```

### CronJob

Creates Jobs on a repeating schedule (like cron in Linux).

```
Use cases: Periodic backups, report generation, cleanup tasks
Schedule format: "*/5 * * * *" (every 5 minutes)
```

### Service

An abstract way to expose an application running on a set of Pods as a network service.

```
Types:
  - ClusterIP: Internal only (default)
  - NodePort: Exposed on each node's IP at a static port
  - LoadBalancer: External load balancer (cloud)
  - ExternalName: Maps to a DNS name
```

### ConfigMap

Stores non-sensitive configuration data as key-value pairs.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  database_url: "jdbc:postgresql://db:5432/mydb"
  log_level: "INFO"
```

### Secret

Stores sensitive data (passwords, tokens, keys) in base64 encoding.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  username: YWRtaW4=        # base64 encoded
  password: cGFzc3dvcmQ=    # base64 encoded
```

### Namespace

Virtual cluster within a physical cluster for resource isolation.

```
Default namespaces:
  - default: Default namespace for objects with no namespace
  - kube-system: System components (coredns, kube-proxy, etc.)
  - kube-public: Publicly readable (cluster-info)
  - kube-node-lease: Node heartbeat leases
```

### Ingress

Manages external access to services, typically HTTP/HTTPS routing.

```
Features:
  - Path-based routing (/api → service-a, /web → service-b)
  - Host-based routing (api.example.com, web.example.com)
  - TLS termination
  - Load balancing
```

### RBAC Objects

```
ServiceAccount: Identity for processes in pods
Role:           Set of permissions within a namespace
ClusterRole:    Set of permissions cluster-wide
RoleBinding:    Grants Role to a user/SA in a namespace
ClusterRoleBinding: Grants ClusterRole cluster-wide
```

### NetworkPolicy

Controls traffic flow between Pods (like a firewall).

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-frontend
spec:
  podSelector:
    matchLabels:
      app: backend
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: frontend
    ports:
    - port: 8080
```

---

## Internal Working

```
Object Lifecycle:

1. Creation:
   User creates YAML → kubectl apply → API Server validates →
   Stored in etcd → Controller detects → Takes action

2. Modification:
   User updates YAML → kubectl apply → API Server validates →
   Updated in etcd → Controller detects change → Reconciles

3. Deletion:
   kubectl delete → API Server marks for deletion →
   Finalizers run → Object removed from etcd

Object Relationships:
  Deployment ──owns──→ ReplicaSet ──owns──→ Pod
  Service ──selects──→ Pods (via label selector)
  Ingress ──routes to──→ Service
  Pod ──uses──→ ConfigMap, Secret, PVC
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│              OBJECT RELATIONSHIP HIERARCHY                        │
│                                                                   │
│  Deployment                                                       │
│      │                                                            │
│      ├──→ ReplicaSet (current)                                   │
│      │        │                                                   │
│      │        ├──→ Pod-1 ──→ Container(s)                        │
│      │        ├──→ Pod-2 ──→ Container(s)                        │
│      │        └──→ Pod-3 ──→ Container(s)                        │
│      │                                                            │
│      └──→ ReplicaSet (old, scaled to 0)                          │
│                                                                   │
│  Service (label selector: app=my-app)                            │
│      │                                                            │
│      └──→ Endpoints: [Pod-1-IP, Pod-2-IP, Pod-3-IP]             │
│                                                                   │
│  Ingress                                                          │
│      │                                                            │
│      └──→ Rules → Service → Pods                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between a Pod, ReplicaSet, and Deployment?

**A:**
- **Pod:** Smallest unit, one or more containers sharing network/storage. Ephemeral — dies and doesn't come back.
- **ReplicaSet:** Ensures N pod replicas are running. Replaces failed pods. But no update strategy.
- **Deployment:** Manages ReplicaSets, adds rolling updates, rollback, versioning. This is what you use in production.

Hierarchy: Deployment → creates ReplicaSet → creates Pods

### Q2: When would you use StatefulSet vs Deployment?

**A:**
- **Deployment:** Stateless apps (web servers, APIs). Pods are interchangeable, no stable identity needed.
- **StatefulSet:** Stateful apps (databases, Kafka, Elasticsearch). Needs stable network identity (pod-0, pod-1), persistent storage per pod, and ordered deployment/scaling.

### Q3: What are the different Service types and when to use each?

**A:**
- **ClusterIP:** Internal communication between services (default)
- **NodePort:** Quick external access for development/testing (port range 30000-32767)
- **LoadBalancer:** Production external access in cloud environments (creates cloud LB)
- **ExternalName:** Alias to external DNS (e.g., map to RDS endpoint)
- **Headless (clusterIP: None):** Direct pod IPs, used with StatefulSets

### Q4: What is the difference between ConfigMap and Secret?

**A:**
- **ConfigMap:** Non-sensitive configuration (URLs, feature flags, log levels). Stored as plain text.
- **Secret:** Sensitive data (passwords, tokens, keys). Base64 encoded (not encrypted by default). Can enable encryption at rest. Size limit: 1MB.

Both can be consumed as environment variables or volume mounts.

### Q5: What is a Namespace and why use it?

**A:** Namespace is a virtual cluster for resource isolation. Use cases:
- Environment separation (dev, staging, prod)
- Team isolation (team-a, team-b)
- Resource quotas per namespace
- RBAC scoping (roles apply within namespace)
- Network policies per namespace

Note: Not all resources are namespaced — Nodes, PVs, ClusterRoles are cluster-scoped.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Creating Pods directly | No self-healing, no scaling | Always use Deployment/StatefulSet |
| Using ReplicaSet directly | No rolling update support | Use Deployment instead |
| Storing secrets in ConfigMap | Security risk | Use Secret with encryption at rest |
| Not using namespaces | Resources all mixed together | Organize by environment/team |
| Missing labels on objects | Can't select or query efficiently | Use consistent labeling strategy |

---

## Best Practices

1. **Never create bare Pods** — always use controllers (Deployment, StatefulSet, etc.)
2. **Use Deployments for stateless**, StatefulSets for stateful workloads
3. **Label everything consistently** — app, version, environment, team
4. **Use namespaces** — at minimum per environment, ideally per team
5. **ConfigMap for config, Secret for sensitive data** — never hardcode
6. **Use NetworkPolicy** — default deny, explicitly allow required traffic
7. **Set resource requests/limits** on every container
8. **Use annotations** for non-identifying metadata (descriptions, tool configs)

---

## Related Topics

- [04. Pods](./04-pods.md)
- [05. Deployments](./05-deployments.md)
- [06. Services](./06-services.md)
- [09. ConfigMap & Secrets](./09-configmaps-and-secrets.md)
