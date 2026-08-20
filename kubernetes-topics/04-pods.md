# 4. Pods ⭐⭐⭐

---

## Theory

A **Pod** is the smallest deployable unit in Kubernetes — a group of one or more containers that share storage, network, and a specification for how to run them.

### What is a Pod?

```
Pod = One or more containers + Shared network namespace + Shared storage

Think of a Pod as a "logical host" — containers in a Pod are like
processes on the same machine that can communicate via localhost.

Pod characteristics:
  - Unique IP address (Pod IP)
  - Shared network namespace (localhost communication)
  - Shared storage volumes
  - Co-scheduled on same node
  - Ephemeral (can be killed and recreated)
  - Immutable (can't change spec of running pod, must recreate)
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-app
  labels:
    app: my-app
    version: v1
spec:
  containers:
  - name: app
    image: my-app:1.0
    ports:
    - containerPort: 8080
    resources:
      requests:
        memory: "256Mi"
        cpu: "250m"
      limits:
        memory: "512Mi"
        cpu: "500m"
    livenessProbe:
      httpGet:
        path: /health
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
    readinessProbe:
      httpGet:
        path: /ready
        port: 8080
      initialDelaySeconds: 5
      periodSeconds: 5
```

### Pod Lifecycle

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌───────────┐
│ Pending  │ →  │ Running  │ →  │Succeeded │ or │  Failed   │
└──────────┘    └──────────┘    └──────────┘    └───────────┘
     │                │
     │                └── Can also go to → Unknown (node lost)
     │
     └── Waiting for: scheduling, image pull, init containers
```

**Phases:**
1. **Pending:** Pod accepted but not running (scheduling, pulling images, init containers)
2. **Running:** At least one container is running or starting/restarting
3. **Succeeded:** All containers terminated successfully (exit 0), won't restart
4. **Failed:** All containers terminated, at least one failed (non-zero exit)
5. **Unknown:** Pod state cannot be determined (node communication failure)

### Pod States (Container States)

```
Container States:
  ┌───────────┐    ┌───────────┐    ┌──────────────┐
  │  Waiting  │ →  │  Running  │ →  │  Terminated  │
  └───────────┘    └───────────┘    └──────────────┘

Waiting reasons:
  - ContainerCreating (setting up networking, volumes)
  - ImagePullBackOff (can't pull image)
  - CrashLoopBackOff (container keeps crashing)
  - ErrImagePull (image doesn't exist or auth failure)

Terminated reasons:
  - Completed (exit 0)
  - Error (non-zero exit)
  - OOMKilled (out of memory)
```

### Pod Conditions

```
Pod Conditions (booleans that indicate state):
  ┌──────────────────┬─────────────────────────────────────┐
  │ Condition        │ Meaning                              │
  ├──────────────────┼─────────────────────────────────────┤
  │ PodScheduled     │ Pod assigned to a node              │
  │ Initialized      │ All init containers completed       │
  │ ContainersReady  │ All containers are ready            │
  │ Ready            │ Pod is ready to serve traffic       │
  └──────────────────┴─────────────────────────────────────┘

kubectl get pod my-app -o jsonpath='{.status.conditions}'
```

### Init Containers

Containers that run before app containers start. Used for setup tasks:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-app
spec:
  initContainers:
  - name: wait-for-db
    image: busybox
    command: ['sh', '-c', 'until nc -z postgres-svc 5432; do sleep 2; done']
  - name: run-migrations
    image: my-app:1.0
    command: ['./migrate', '--up']
  containers:
  - name: app
    image: my-app:1.0
```

```
Init Container behavior:
  - Run sequentially (one after another)
  - Must complete successfully before app containers start
  - If init container fails, Pod restarts (reruns init containers)
  - Use different image from app container (security principle)

Use cases:
  - Wait for dependencies (database, service)
  - Run database migrations
  - Download configuration files
  - Set up file permissions
  - Register with service mesh
```

### Sidecar Containers

Additional containers that enhance or support the main container:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: web-app
spec:
  containers:
  - name: app                    # Main container
    image: my-app:1.0
    ports:
    - containerPort: 8080
  - name: log-agent              # Sidecar: log forwarding
    image: fluent-bit:latest
    volumeMounts:
    - name: logs
      mountPath: /var/log/app
  - name: envoy                  # Sidecar: service mesh proxy
    image: envoyproxy/envoy:v1.28
    ports:
    - containerPort: 9901
  volumes:
  - name: logs
    emptyDir: {}
```

```
Common sidecar patterns:
  1. Logging agent (fluent-bit, filebeat)
  2. Service mesh proxy (envoy, istio-proxy)
  3. Configuration watcher (reload config on change)
  4. Data synchronizer (sync files from remote)
  5. TLS proxy (handle mTLS termination)
```

### Multi-Container Pods

```
Multi-Container Patterns:

1. Sidecar Pattern:
   Main container + helper container
   Example: App + log forwarder

2. Ambassador Pattern:
   Main container + proxy container
   Example: App + DB proxy (handles connection pooling)

3. Adapter Pattern:
   Main container + transformer container
   Example: App + metrics adapter (converts metrics format)

┌─────────── Pod ────────────┐
│                             │
│  ┌─────────┐  ┌─────────┐ │
│  │  Main   │  │ Sidecar │ │
│  │  App    │←→│ (proxy) │ │
│  │ :8080   │  │ :9090   │ │
│  └─────────┘  └─────────┘ │
│        │           │       │
│        └─── shared volume ─┘│
│         localhost network   │
└─────────────────────────────┘
```

### Shared Network Namespace

All containers in a Pod share the same network namespace:

```
Implications:
  - Same IP address (Pod IP)
  - Can communicate via localhost
  - Share port space (two containers can't use same port)
  - Same network interfaces

Container A (port 8080) ←→ Container B (port 9090)
  Communication: localhost:9090 from A, localhost:8080 from B
```

### Shared Volumes

Containers can share data through volumes:

```yaml
spec:
  containers:
  - name: writer
    image: busybox
    command: ['sh', '-c', 'echo "data" > /shared/data.txt; sleep 3600']
    volumeMounts:
    - name: shared-data
      mountPath: /shared
  - name: reader
    image: busybox
    command: ['sh', '-c', 'cat /shared/data.txt; sleep 3600']
    volumeMounts:
    - name: shared-data
      mountPath: /shared
  volumes:
  - name: shared-data
    emptyDir: {}   # Ephemeral volume, shared between containers
```

### Pod IP

```
Every Pod gets a unique IP address:
  - Allocated from the Pod CIDR range
  - Routable within the cluster
  - Ephemeral (changes when Pod is recreated)
  - All containers in Pod share this IP

Pod-to-Pod communication:
  Pod A (10.244.1.5) → Pod B (10.244.2.3)
  Direct routing via CNI network plugin
```

### Restart Policy

```yaml
spec:
  restartPolicy: Always    # Default for Deployments

# Options:
# Always:     Always restart (default, used by Deployments)
# OnFailure:  Restart only on non-zero exit (used by Jobs)
# Never:      Never restart (used by one-shot Pods)
```

```
Restart backoff:
  1st restart: immediate
  2nd restart: 10s delay
  3rd restart: 20s delay
  4th restart: 40s delay
  ...
  Max: 5 minutes (CrashLoopBackOff)
```

### Pod Security

```yaml
spec:
  securityContext:          # Pod-level security
    runAsUser: 1000
    runAsGroup: 3000
    fsGroup: 2000
  containers:
  - name: app
    image: my-app:1.0
    securityContext:        # Container-level security
      runAsNonRoot: true
      readOnlyRootFilesystem: true
      allowPrivilegeEscalation: false
      capabilities:
        drop:
        - ALL
```

---

## Internal Working

```
Pod Creation Flow:

1. kubectl apply -f pod.yaml
2. API Server validates and stores Pod in etcd (status: Pending)
3. Scheduler detects unscheduled Pod
4. Scheduler selects a Node → updates Pod.spec.nodeName
5. kubelet on selected Node detects new Pod assignment
6. kubelet calls Container Runtime (CRI):
   a. RunPodSandbox → creates network namespace (pause container)
   b. For each init container (sequentially):
      - PullImage
      - CreateContainer
      - StartContainer
      - Wait for completion
   c. For each app container (in parallel):
      - PullImage
      - CreateContainer
      - StartContainer
7. kubelet starts health probes
8. kubelet reports Pod status to API Server

Pod Deletion Flow:
1. kubectl delete pod my-app
2. API Server sets deletionTimestamp on Pod
3. Pod removed from Service endpoints (stops receiving traffic)
4. kubelet receives Pod deletion event
5. kubelet sends SIGTERM to containers
6. Waits for terminationGracePeriodSeconds (default: 30s)
7. If container still running → sends SIGKILL
8. kubelet reports Pod terminated
9. API Server removes Pod from etcd
```

---

## Diagram

```
┌─────────────────────── POD LIFECYCLE ────────────────────────┐
│                                                                │
│  kubectl apply          Scheduler           kubelet            │
│       │                    │                   │               │
│       ▼                    ▼                   ▼               │
│  ┌─────────┐      ┌──────────────┐    ┌──────────────┐      │
│  │ Pending │ ──→  │ Scheduled    │ →  │ Init         │      │
│  │(no node)│      │ (node bound) │    │ Containers   │      │
│  └─────────┘      └──────────────┘    └──────┬───────┘      │
│                                                │              │
│                                         ┌──────▼───────┐     │
│                                         │   Running    │     │
│                                         │ (app started)│     │
│                                         └──────┬───────┘     │
│                                                │              │
│                              ┌─────────────────┼──────┐      │
│                              ▼                  ▼      ▼      │
│                       ┌───────────┐    ┌────────┐ ┌───────┐  │
│                       │ Succeeded │    │ Failed │ │Unknown│  │
│                       │ (exit 0)  │    │(error) │ │(lost) │  │
│                       └───────────┘    └────────┘ └───────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## Code

### Complete Pod with all features:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: production-app
  namespace: production
  labels:
    app: my-app
    version: v2.1
    environment: production
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
spec:
  serviceAccountName: my-app-sa
  terminationGracePeriodSeconds: 60
  
  # Pod-level security
  securityContext:
    runAsUser: 1000
    runAsGroup: 3000
    fsGroup: 2000

  # Init containers
  initContainers:
  - name: wait-for-db
    image: busybox:1.36
    command: ['sh', '-c', 'until nc -z postgres-svc 5432; do echo waiting; sleep 2; done']
  
  # App containers
  containers:
  - name: app
    image: my-app:2.1.0
    ports:
    - containerPort: 8080
      name: http
    
    # Environment
    env:
    - name: DB_HOST
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: db_host
    - name: DB_PASSWORD
      valueFrom:
        secretKeyRef:
          name: db-secret
          key: password
    
    # Resources
    resources:
      requests:
        memory: "256Mi"
        cpu: "250m"
      limits:
        memory: "512Mi"
        cpu: "500m"
    
    # Health probes
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
      initialDelaySeconds: 0
      periodSeconds: 10
      failureThreshold: 3
    readinessProbe:
      httpGet:
        path: /ready
        port: 8080
      initialDelaySeconds: 0
      periodSeconds: 5
      failureThreshold: 3
    
    # Security
    securityContext:
      runAsNonRoot: true
      readOnlyRootFilesystem: true
      allowPrivilegeEscalation: false
      capabilities:
        drop: ["ALL"]
    
    # Volume mounts
    volumeMounts:
    - name: tmp
      mountPath: /tmp
    - name: config
      mountPath: /app/config
      readOnly: true

  # Sidecar
  - name: log-forwarder
    image: fluent-bit:2.1
    resources:
      requests:
        memory: "64Mi"
        cpu: "50m"
      limits:
        memory: "128Mi"
        cpu: "100m"
    volumeMounts:
    - name: logs
      mountPath: /var/log/app

  # Volumes
  volumes:
  - name: tmp
    emptyDir: {}
  - name: logs
    emptyDir: {}
  - name: config
    configMap:
      name: app-config

  # Scheduling
  nodeSelector:
    disktype: ssd
  tolerations:
  - key: "dedicated"
    operator: "Equal"
    value: "app"
    effect: "NoSchedule"
```

---

## Interview Questions

### Q1: What is a Pod and why not just run containers directly?

**A:** A Pod is a group of one or more tightly coupled containers that share network namespace and storage. We use Pods instead of bare containers because:
- Shared networking (localhost communication between sidecars)
- Shared storage (volumes accessible by all containers)
- Co-scheduling guarantee (always on same node)
- Atomic deployment unit (deploy together, fail together)
- Abstraction over container runtime (CRI interface)

### Q2: Explain the Pod lifecycle phases.

**A:**
- **Pending:** Pod accepted, waiting for scheduling or image pull or init containers
- **Running:** At least one container running
- **Succeeded:** All containers completed with exit code 0 (Jobs)
- **Failed:** At least one container failed (non-zero exit)
- **Unknown:** Node communication lost

### Q3: What is the difference between init containers and sidecar containers?

**A:**
- **Init containers:** Run before main containers, sequentially, must complete successfully. Used for one-time setup (wait for deps, migrations, download configs).
- **Sidecar containers:** Run alongside main container for the entire Pod lifecycle. Used for ongoing support (logging, proxying, monitoring).

### Q4: What happens when you delete a Pod?

**A:**
1. API Server sets deletionTimestamp
2. Pod removed from Service endpoints (stops receiving new requests)
3. kubelet sends SIGTERM to containers
4. PreStop hook executes (if configured)
5. Waits terminationGracePeriodSeconds (default 30s) for graceful shutdown
6. If still running → SIGKILL (forced kill)
7. Pod removed from etcd

### Q5: What is CrashLoopBackOff?

**A:** CrashLoopBackOff means a container keeps crashing and K8s is waiting with exponential backoff before restarting it. The backoff goes: 10s, 20s, 40s, 80s... up to 5 minutes. Common causes: missing env vars, wrong command, missing configs, OOM, app bugs, missing dependencies.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Running Pods directly (no controller) | No self-healing or scaling | Use Deployment/StatefulSet |
| Using `:latest` image tag | Non-deterministic deploys | Pin specific versions |
| No resource limits | OOM kills, noisy neighbor | Set requests AND limits |
| No health probes | K8s can't detect failures | Add liveness + readiness |
| Container runs as root | Security vulnerability | runAsNonRoot: true |
| Ignoring graceful shutdown | Requests dropped during termination | Handle SIGTERM, use preStop |

---

## Best Practices

1. **One process per container** — separation of concerns
2. **Always use controllers** — Deployment, StatefulSet, DaemonSet
3. **Set resource requests = expected usage, limits = max tolerable**
4. **Use all three probes** — startup, liveness, readiness
5. **Handle SIGTERM gracefully** — finish in-flight requests before shutdown
6. **Use preStop hook** for services needing deregistration time
7. **Keep containers lightweight** — minimal base images (distroless, alpine)
8. **Never store state in containers** — use volumes or external storage

---

## Related Topics

- [05. Deployments](./05-deployments.md)
- [07. Networking](./07-networking.md)
- [16. Health Checks](./16-health-checks.md)
- [15. Resources](./15-resources.md)
