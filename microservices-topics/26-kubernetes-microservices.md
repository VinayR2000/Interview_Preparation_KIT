# 26. Kubernetes + Microservices

## Theory

Kubernetes is the standard platform for running microservices in production. It provides built-in solutions for many microservices challenges: service discovery, load balancing, scaling, health checks, and deployment management.

### How Kubernetes Solves Microservices Challenges:

| Challenge | Kubernetes Solution |
|-----------|-------------------|
| Service Discovery | DNS-based (CoreDNS) + Service objects |
| Load Balancing | kube-proxy, Service ClusterIP |
| Scaling | HPA, VPA, KEDA |
| Health Checks | Liveness, Readiness, Startup probes |
| Configuration | ConfigMaps, Secrets |
| Deployment | Rolling update, blue-green via Service |
| Self-healing | ReplicaSet, restart policies |
| Resource isolation | Namespaces, resource quotas |

---

## Internal Working

### Microservices on Kubernetes:

```
┌──────────────────────────────────────────────────────────────┐
│ KUBERNETES CLUSTER                                            │
│                                                               │
│ Namespace: production                                        │
│                                                               │
│ ┌────────────────────────────────────────────────────────┐  │
│ │ Ingress Controller (nginx/ALB)                          │  │
│ │ api.company.com → api-gateway service                  │  │
│ └───────────────────────┬────────────────────────────────┘  │
│                         │                                    │
│ ┌───────────────────────┼────────────────────────────────┐  │
│ │ API Gateway (Deployment: 3 replicas)                    │  │
│ │ Service: api-gateway (ClusterIP)                        │  │
│ └───────────────────────┬────────────────────────────────┘  │
│                         │                                    │
│    ┌────────────────────┼────────────────────┐              │
│    ↓                    ↓                    ↓              │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│ │Order Service │ │Payment Service│ │ User Service │        │
│ │Deployment: 5 │ │Deployment: 3 │ │Deployment: 3 │        │
│ │HPA: 3-20     │ │HPA: 2-10    │ │HPA: 2-8     │        │
│ │Service: order│ │Service: pay  │ │Service: user │        │
│ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘        │
│        │                │                │                  │
│        ↓                ↓                ↓                  │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐        │
│ │ PostgreSQL   │ │ PostgreSQL   │ │  MongoDB     │        │
│ │ StatefulSet  │ │ StatefulSet  │ │ StatefulSet  │        │
│ │ PVC: 100Gi   │ │ PVC: 50Gi   │ │ PVC: 200Gi  │        │
│ └──────────────┘ └──────────────┘ └──────────────┘        │
│                                                               │
│ Shared Infrastructure:                                       │
│ ┌──────────┐ ┌──────────┐ ┌──────────────────────┐        │
│ │  Kafka   │ │  Redis   │ │ Prometheus + Grafana │        │
│ │StatefulSet│ │Deployment│ │    (Monitoring)      │        │
│ └──────────┘ └──────────┘ └──────────────────────┘        │
└──────────────────────────────────────────────────────────────┘
```

### Service Discovery in Kubernetes:

```
Order Service Pod wants to call Payment Service:

1. Application code:
   http://payment-service:8082/api/payments

2. CoreDNS resolves "payment-service" to ClusterIP:
   payment-service → 10.96.45.123 (virtual IP)

3. kube-proxy (iptables/IPVS) routes ClusterIP to Pod IP:
   10.96.45.123 → 10.0.2.15:8082 (actual pod)

4. Load balancing:
   Round-robin across all ready pods of Payment Service

No Eureka needed! Kubernetes handles it all.
```

---

## Code

### Complete Microservice Kubernetes Manifests:

```yaml
# Namespace
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce

---
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
  namespace: ecommerce
data:
  SPRING_PROFILES_ACTIVE: "prod"
  SERVER_PORT: "8081"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://order-db:5432/orders"
  KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  REDIS_HOST: "redis"

---
# Secret
apiVersion: v1
kind: Secret
metadata:
  name: order-service-secrets
  namespace: ecommerce
type: Opaque
data:
  DB_PASSWORD: cGFzc3dvcmQxMjM=  # base64
  JWT_SECRET: c2VjcmV0S2V5MTIz

---
# Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ecommerce
  labels:
    app: order-service
spec:
  replicas: 3
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
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8081"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: order-service
      containers:
        - name: order-service
          image: registry.company.com/order-service:2.1.0
          ports:
            - containerPort: 8081
          envFrom:
            - configMapRef:
                name: order-service-config
            - secretRef:
                name: order-service-secrets
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 15
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            failureThreshold: 30
            periodSeconds: 5
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 5"]

---
# Service
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ecommerce
spec:
  selector:
    app: order-service
  ports:
    - port: 8081
      targetPort: 8081
  type: ClusterIP

---
# HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
  namespace: ecommerce
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70

---
# Ingress
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-ingress
  namespace: ecommerce
  annotations:
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  tls:
    - hosts:
        - api.company.com
      secretName: api-tls
  rules:
    - host: api.company.com
      http:
        paths:
          - path: /api/orders
            pathType: Prefix
            backend:
              service:
                name: order-service
                port:
                  number: 8081
          - path: /api/payments
            pathType: Prefix
            backend:
              service:
                name: payment-service
                port:
                  number: 8082

---
# NetworkPolicy (restrict inter-service communication)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: order-service-netpol
  namespace: ecommerce
spec:
  podSelector:
    matchLabels:
      app: order-service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - port: 8081
  egress:
    - to:
        - podSelector:
            matchLabels:
              app: order-db
      ports:
        - port: 5432
    - to:
        - podSelector:
            matchLabels:
              app: kafka
      ports:
        - port: 9092
    - to:
        - podSelector:
            matchLabels:
              app: payment-service
      ports:
        - port: 8082
```

---

## Interview Questions

1. **How does Kubernetes help with microservices?**
   - Built-in service discovery (DNS), load balancing (Service), auto-scaling (HPA), health checks (probes), rolling deployments, config management (ConfigMaps/Secrets), self-healing (ReplicaSet). Removes need for many external tools.

2. **Do you still need Eureka on Kubernetes?**
   - No. Kubernetes Service + CoreDNS provides service discovery. Use `http://service-name:port` directly. Eureka adds complexity without benefit on K8s. Exception: hybrid environments (K8s + VMs).

3. **How to handle database per service in Kubernetes?**
   - StatefulSet for databases (stable network identity, persistent volumes). Or: use managed databases (RDS, Cloud SQL) outside the cluster. Each service has its own PVC or external DB connection.

4. **How to implement canary deployments in Kubernetes?**
   - Two Deployments (stable + canary) with same Service selector labels. Or: Istio VirtualService for weighted traffic splitting. Argo Rollouts for automated progressive delivery with metrics-based promotion.

5. **How to secure inter-service communication in Kubernetes?**
   - NetworkPolicy (restrict which pods can communicate). Service mesh (Istio) for mTLS. ServiceAccount per service. RBAC for API access. No service should accept traffic from unexpected sources.

---

## Best Practices

1. **One service per Deployment** — Independent scaling and deployment
2. **Resource requests and limits** — Prevent noisy neighbors
3. **NetworkPolicy** — Restrict communication to what's needed
4. **ServiceAccount per service** — Least privilege access
5. **Namespaces for isolation** — Separate environments or teams
6. **HPA based on custom metrics** — Not just CPU; use request rate, queue depth
7. **Managed databases** — Don't run production databases in Kubernetes unless you must
