# 34. Spring Boot + Kubernetes

## Theory

Kubernetes (K8s) orchestrates containerized Spring Boot applications in production, providing auto-scaling, self-healing, service discovery, load balancing, and rolling deployments.

### Key K8s Resources:
- **Pod**: Smallest deployable unit (one or more containers)
- **Deployment**: Manages Pod replicas, rolling updates
- **Service**: Stable network endpoint for Pods (load balancing)
- **ConfigMap**: External configuration (non-sensitive)
- **Secret**: Sensitive configuration (passwords, tokens)
- **Ingress**: External HTTP routing to Services
- **HPA (Horizontal Pod Autoscaler)**: Auto-scale based on metrics
- **Namespace**: Logical isolation of resources

### Spring Boot + K8s Integration:
- Health probes (liveness, readiness, startup)
- ConfigMap/Secret mounted as properties
- Service discovery via K8s DNS
- Graceful shutdown on SIGTERM
- Prometheus metrics for HPA

---

## Internal Working

```
Deployment Process:
  docker push registry/app:v2
       ↓
  kubectl apply -f deployment.yaml
       ↓
  K8s creates new ReplicaSet
       ↓
  New Pods scheduled on nodes
       ↓
  Startup probe passes → Pod starting
       ↓
  Readiness probe passes → Pod receives traffic
       ↓
  Old Pods terminated (graceful shutdown)
       ↓
  Rolling update complete

Service Discovery:
  Pod A needs to call order-service
       ↓
  DNS: order-service.namespace.svc.cluster.local
       ↓
  K8s Service → Load balances to healthy Pods
       ↓
  Pod B (order-service) receives request
```

### Health Probes:
```
┌─────────────────────────────────────────────────────────┐
│                                                          │
│ Startup Probe:                                           │
│   "Has the app finished starting?"                      │
│   Fails → K8s keeps waiting (won't restart yet)         │
│   Path: /actuator/health/liveness                       │
│   Period: 5s, Failure: 30 attempts = 150s max startup   │
│                                                          │
│ Readiness Probe:                                         │
│   "Can the app serve traffic?"                          │
│   Fails → K8s removes from Service (no traffic)         │
│   Path: /actuator/health/readiness                      │
│   Period: 5s, Failure: 3 → removed from endpoints       │
│                                                          │
│ Liveness Probe:                                          │
│   "Is the app alive (not deadlocked)?"                  │
│   Fails → K8s RESTARTS the Pod                          │
│   Path: /actuator/health/liveness                       │
│   Period: 10s, Failure: 3 → restart                     │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                     KUBERNETES CLUSTER                             │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                      INGRESS                               │    │
│  │    api.example.com → order-service (Service)              │    │
│  │    web.example.com → frontend (Service)                   │    │
│  └──────────────────────────┬───────────────────────────────┘    │
│                              │                                     │
│  ┌──────────────────────────┴───────────────────────────────┐    │
│  │              SERVICE: order-service                         │    │
│  │         (ClusterIP, Load Balancer)                         │    │
│  └──────────┬──────────────┬──────────────┬─────────────────┘    │
│             │              │              │                        │
│  ┌──────────┴──┐ ┌────────┴────┐ ┌──────┴────────┐             │
│  │   Pod 1     │ │   Pod 2     │ │   Pod 3       │             │
│  │             │ │             │ │               │             │
│  │ order-svc   │ │ order-svc   │ │ order-svc     │             │
│  │ :8080       │ │ :8080       │ │ :8080         │             │
│  │             │ │             │ │               │             │
│  │ CPU: 500m   │ │ CPU: 500m   │ │ CPU: 500m     │             │
│  │ Mem: 512Mi  │ │ Mem: 512Mi  │ │ Mem: 512Mi    │             │
│  └─────────────┘ └─────────────┘ └───────────────┘             │
│                                                                    │
│  ┌──────────────────────┐  ┌──────────────────────┐             │
│  │ ConfigMap             │  │ Secret                │             │
│  │ spring.datasource.url│  │ DB_PASSWORD           │             │
│  │ app.feature.flags    │  │ JWT_SECRET            │             │
│  └──────────────────────┘  └──────────────────────┘             │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ HPA: min=2, max=10, target CPU=70%                        │    │
│  │ Currently: 3 replicas (CPU avg: 65%)                      │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## Code

### Deployment YAML:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: production
  labels:
    app: order-service
    version: v2.1.0
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0  # Zero downtime
  template:
    metadata:
      labels:
        app: order-service
        version: v2.1.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: order-service
      containers:
        - name: order-service
          image: registry.example.com/order-service:v2.1.0
          ports:
            - containerPort: 8080
              name: http
          
          # Resource limits
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
            limits:
              cpu: 1000m
              memory: 512Mi
          
          # Environment from ConfigMap and Secret
          envFrom:
            - configMapRef:
                name: order-service-config
            - secretRef:
                name: order-service-secrets
          
          # Health probes
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 30  # 30 × 5s = 150s max startup
          
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 10
            failureThreshold: 3
            timeoutSeconds: 3
          
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
            failureThreshold: 3
            timeoutSeconds: 3
          
          # Graceful shutdown
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 5"]  # Wait for LB to drain
```

### Service:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: production
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
      name: http
```

### ConfigMap:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
  namespace: production
data:
  SPRING_PROFILES_ACTIVE: "prod"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres-service:5432/orders"
  SPRING_DATA_REDIS_HOST: "redis-service"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka-service:9092"
  SERVER_SHUTDOWN: "graceful"
  SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: "30s"
  JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

### Secret:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: order-service-secrets
  namespace: production
type: Opaque
data:
  SPRING_DATASOURCE_USERNAME: YWRtaW4=          # base64("admin")
  SPRING_DATASOURCE_PASSWORD: c2VjcmV0UGFzcw==  # base64("secretPass")
  JWT_SECRET: bXlTdXBlclNlY3JldEtleQ==          # base64("mySuperSecretKey")
```

### Ingress:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-ingress
  namespace: production
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/rate-limit: "100"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
    - hosts:
        - api.example.com
      secretName: api-tls
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /api/orders
            pathType: Prefix
            backend:
              service:
                name: order-service
                port:
                  number: 80
          - path: /api/users
            pathType: Prefix
            backend:
              service:
                name: user-service
                port:
                  number: 80
```

### Horizontal Pod Autoscaler:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "100"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60
```

### Spring Boot Configuration for K8s:

```yaml
# application-prod.yml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

server:
  shutdown: graceful  # Wait for in-flight requests

management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: db, redis, kafka
        liveness:
          include: ping
  health:
    readinessstate:
      enabled: true
    livenessstate:
      enabled: true
```

---

## Dry Run

### Rolling Update (v1 → v2):

```
Initial state: 3 pods running v1

1. kubectl apply (new Deployment with v2 image)
   maxSurge=1, maxUnavailable=0

2. K8s creates Pod-4 (v2)
   → Startup probe checking... (waiting for app start)
   → Startup probe passes after 15s
   → Readiness probe passes → Pod-4 receives traffic
   
   State: [v1-Pod1 ✓] [v1-Pod2 ✓] [v1-Pod3 ✓] [v2-Pod4 ✓]

3. K8s terminates Pod-1 (v1)
   → SIGTERM sent to JVM
   → preStop: sleep 5 (LB drains connections)
   → Spring Boot graceful shutdown (finishes in-flight requests)
   → Pod-1 terminated after 30s max
   
   State: [v1-Pod2 ✓] [v1-Pod3 ✓] [v2-Pod4 ✓]

4. K8s creates Pod-5 (v2)
   → Startup + readiness passes
   → K8s terminates Pod-2 (v1)
   
   State: [v1-Pod3 ✓] [v2-Pod4 ✓] [v2-Pod5 ✓]

5. K8s creates Pod-6 (v2)
   → Passes probes
   → K8s terminates Pod-3 (v1)
   
   State: [v2-Pod4 ✓] [v2-Pod5 ✓] [v2-Pod6 ✓]

Rolling update complete! Zero downtime!
```

### HPA Scaling:

```
Normal load: 2 pods, CPU avg = 40%

Traffic spike:
  CPU avg rises to 75% (above 70% target)
  → HPA calculates: desired = 2 × (75/70) = 2.14 → rounds to 3
  → Scales to 3 pods

Continued spike:
  CPU avg at 85%
  → desired = 3 × (85/70) = 3.6 → rounds to 4
  → Scales to 4 pods (max 2 pods per 60s)

Traffic decreases:
  CPU avg drops to 30%
  → Stabilization window: 300s (waits before scaling down)
  → After 5 min stable: scales down by 1 pod
  → Eventually returns to min=2 pods
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Pod scheduling | ~1-5 seconds |
| Container pull (cached) | ~1-2 seconds |
| Spring Boot startup | ~5-20 seconds |
| Readiness probe pass | ~20-30 seconds total |
| Rolling update (3 pods) | ~2-3 minutes |
| HPA scale-up reaction | ~30-60 seconds |
| HPA scale-down reaction | ~5+ minutes (stabilization) |

---

## Real Project Usage

### Full Deployment Pipeline:

```yaml
# Kustomize structure
├── base/
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── hpa.yaml
│   └── kustomization.yaml
├── overlays/
│   ├── dev/
│   │   ├── configmap.yaml
│   │   ├── kustomization.yaml
│   │   └── patches/
│   │       └── replicas.yaml  (replicas: 1)
│   └── prod/
│       ├── configmap.yaml
│       ├── secrets.yaml
│       ├── kustomization.yaml
│       └── patches/
│           └── replicas.yaml  (replicas: 3)
```

---

## Interview Questions

1. **How does K8s know when a Spring Boot app is ready?**
   - Readiness probe calls /actuator/health/readiness. Returns UP when DB, Redis, Kafka connections are established. K8s only routes traffic to ready pods.

2. **Difference between liveness and readiness probes?**
   - Liveness: "Is app stuck?" Failure → restart pod. Readiness: "Can app serve?" Failure → remove from service (no traffic). Liveness should check app-internal state only (not external deps).

3. **How to achieve zero-downtime deployments?**
   - Rolling update with maxUnavailable=0. Readiness probe gates traffic. Graceful shutdown (server.shutdown=graceful). preStop hook for LB drain time.

4. **How does Spring Boot read K8s ConfigMaps?**
   - As environment variables (envFrom: configMapRef) which Spring auto-maps to properties. Or as mounted volumes (application.yml files).

5. **How does HPA work with Spring Boot?**
   - HPA monitors metrics (CPU, memory, custom). When threshold exceeded, scales replicas. Spring Boot exposes metrics via /actuator/prometheus for custom metrics-based scaling.

---

## Common Mistakes

1. **Liveness depends on external services** - DB down → liveness fails → all pods restart → outage! Use readiness for external deps.
2. **No resource limits** - Pod can consume all node resources, affecting other pods
3. **No graceful shutdown** - In-flight requests lost during rolling update
4. **Image tag `latest`** - Non-deterministic deployments. Use specific versions.
5. **Secrets in ConfigMap** - Use K8s Secrets (or external secret managers)
6. **No startup probe** - Liveness kills slow-starting apps before they're ready

---

## Best Practices

1. **Always set resource requests AND limits**
2. **Use startup probe** for slow-starting Spring Boot apps
3. **Separate liveness from readiness** concerns
4. **Graceful shutdown** with `server.shutdown=graceful` + preStop hook
5. **Use Secrets for sensitive config** (or external secret managers like Vault)
6. **Set PodDisruptionBudget** to prevent voluntary eviction of all pods
7. **Use namespaces** for environment isolation
8. **Immutable image tags** (v2.1.0, not latest)
9. **Monitor with Prometheus + Grafana** using Actuator metrics

---

## Production Considerations

- **Resource right-sizing**: Monitor actual usage, adjust requests/limits. Over-provisioning = waste, under-provisioning = OOMKilled/throttling.
- **Pod Disruption Budget**: Ensure minimum available during maintenance
- **Network Policies**: Restrict which services can communicate
- **Service Mesh (Istio)**: mTLS, traffic management, observability at platform level
- **External Secrets Operator**: Sync secrets from Vault/AWS Secrets Manager
- **Multi-AZ deployment**: Spread pods across availability zones (topology constraints)
- **Cost optimization**: Use node auto-scaling, spot instances for non-critical workloads
- **Monitoring**: Pod restarts, OOMKilled events, HPA activity, probe failures

---

## Related Topics

- Docker (container basics)
- Spring Boot Actuator (health probes)
- Microservices (service architecture)
- Spring Cloud (service discovery alternative)
- Resilience Patterns (failure handling)
- CI/CD (deployment automation)
