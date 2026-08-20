# Azure Kubernetes Service (AKS)

## Theory

### What is AKS?
Managed Kubernetes service on Azure. Azure manages the control plane (API server, etcd, scheduler, controller manager). You manage the worker nodes and workloads. Equivalent to AWS EKS.

### AKS vs EKS Key Differences

| Feature | AKS | EKS |
|---------|-----|-----|
| Control plane cost | Free | ~$73/month per cluster |
| Networking | Azure CNI / kubenet | AWS VPC CNI |
| Identity | Workload Identity (Entra ID) | IRSA (IAM Roles for Service Accounts) |
| Registry | ACR (attach with one command) | ECR |
| Ingress | NGINX / App Gateway Ingress | AWS ALB Ingress |
| Service Mesh | Istio (built-in add-on) | App Mesh / Istio |
| Autoscaling | KEDA + HPA + Cluster Autoscaler | Karpenter / Cluster Autoscaler |

---

## Internal Working

### AKS Architecture ⭐⭐⭐

```
Azure-Managed Control Plane (FREE)
├── API Server
├── etcd (cluster state)
├── Scheduler
├── Controller Manager
└── Cloud Controller Manager
    │
    │ (Kubernetes API)
    │
    ▼
Customer-Managed Node Pools (YOU PAY FOR THESE)
├── System Node Pool (required)
│   ├── Node 1 (Standard_D4s_v5)
│   │   ├── Pod: coredns
│   │   ├── Pod: kube-proxy
│   │   └── Pod: metrics-server
│   ├── Node 2
│   └── Node 3
│
├── User Node Pool: "apppool" (for your workloads)
│   ├── Node 1 (Standard_D8s_v5)
│   │   ├── Pod: order-service (2 replicas)
│   │   ├── Pod: user-service
│   │   └── Pod: payment-service
│   ├── Node 2
│   │   ├── Pod: order-service (2 replicas)
│   │   ├── Pod: product-service
│   │   └── Pod: notification-service
│   └── Node 3
│       └── ...
│
└── User Node Pool: "gpupool" (optional, for ML)
    └── Node 1 (Standard_NC6s_v3 — GPU)
        └── Pod: ml-inference-service
```

### Node Pools ⭐⭐⭐

| Type | Purpose | Requirements |
|------|---------|-------------|
| System | Core cluster components (CoreDNS, metrics-server) | Required, always running |
| User | Application workloads | Optional, can scale to zero |

Node pool features:
- Different VM sizes per pool (cost optimization)
- Different scaling rules per pool
- Taints/tolerations (GPU nodes only for ML pods)
- Availability Zones (spread across zones)
- Spot instances (cost savings for non-critical workloads)

---

## Networking ⭐⭐⭐

### Azure CNI vs kubenet

| Feature | Azure CNI | kubenet |
|---------|-----------|---------|
| Pod IPs | From VNet subnet (routable) | NAT'd behind node IP |
| Performance | Better (no NAT) | Slightly worse |
| IP consumption | High (one IP per pod) | Low |
| VNet integration | Full (pods directly on VNet) | Limited |
| Recommended | Yes (production) | Budget/simple clusters |

### AKS Networking with Azure CNI

```
VNet: vnet-prod (10.0.0.0/16)
│
├── Subnet: snet-aks (10.0.0.0/22) — /22 = 1021 IPs for nodes + pods
│   ├── Node 1: 10.0.0.4
│   │   ├── Pod: 10.0.0.10
│   │   ├── Pod: 10.0.0.11
│   │   └── Pod: 10.0.0.12
│   ├── Node 2: 10.0.0.5
│   │   ├── Pod: 10.0.0.20
│   │   └── Pod: 10.0.0.21
│   └── Node 3: 10.0.0.6
│       └── Pod: 10.0.0.30
│
├── Subnet: snet-appgw (10.0.4.0/24)
│   └── Application Gateway (Ingress)
│
├── Subnet: snet-pe (10.0.8.0/24)
│   ├── PE → PostgreSQL
│   ├── PE → Redis
│   ├── PE → Key Vault
│   └── PE → Service Bus
│
└── Subnet: snet-internal-lb (10.0.9.0/24)
    └── Internal Load Balancer (for Kubernetes Services)
```

### Ingress Options

| Option | Description | Use Case |
|--------|-------------|----------|
| NGINX Ingress Controller | Community standard, L7 routing | General purpose |
| Application Gateway Ingress (AGIC) | Azure-native, WAF integration | Enterprise, WAF needed |
| Azure Service Mesh (Istio) | Full service mesh with ingress | Advanced traffic management |

```
Internet
    │
    ▼
Application Gateway (AGIC) — TLS termination + WAF
    │
    ▼ (Ingress rules)
NGINX Ingress Controller (in AKS)
    │
    ├── /api/orders → order-service (ClusterIP)
    ├── /api/users → user-service (ClusterIP)
    └── /api/products → product-service (ClusterIP)
        │
        ▼
    Kubernetes Service → Pods
```

---

## Workload Identity ⭐⭐⭐

Allows AKS pods to authenticate to Azure services using Entra ID (replaces pod-managed identity).

```
1. Create User-Assigned Managed Identity in Azure
2. Create Kubernetes Service Account with annotation
3. Establish federated credential (trust between K8s SA and Azure MI)
4. Pod uses Service Account → gets Azure token → accesses Key Vault/Storage/etc.

Flow:
Pod (with Service Account)
    │
    ▼ (Federated token)
Entra ID
    │
    ▼ (Access token)
Azure Service (Key Vault, Storage, PostgreSQL)
```

```yaml
# Service Account with Workload Identity
apiVersion: v1
kind: ServiceAccount
metadata:
  name: order-service-sa
  namespace: production
  annotations:
    azure.workload.identity/client-id: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
---
# Pod using the Service Account
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  template:
    metadata:
      labels:
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: order-service-sa
      containers:
      - name: order-service
        image: contosoacr.azurecr.io/order-service:v2.1.0
```

---

## Autoscaling ⭐⭐⭐

### Three Levels of Scaling

```
Level 1: Pod-level (HPA)
├── Scale pods based on CPU/memory/custom metrics
├── Example: 2 → 10 pods when CPU > 70%
│
Level 2: Node-level (Cluster Autoscaler)
├── Add/remove nodes when pods can't be scheduled
├── Example: 3 → 8 nodes when pods are pending
│
Level 3: Event-driven (KEDA)
├── Scale based on external metrics (queue length, event count)
├── Example: Scale to 0 when Service Bus queue is empty
└── Scale to 50 when queue has 1000 messages
```

### KEDA (Kubernetes Event-Driven Autoscaling) ⭐⭐⭐
```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: order-processor-scaler
spec:
  scaleTargetRef:
    name: order-processor
  minReplicaCount: 0    # Scale to zero!
  maxReplicaCount: 50
  triggers:
  - type: azure-servicebus
    metadata:
      queueName: order-processing
      messageCount: "10"  # 1 pod per 10 messages
      connectionFromEnv: SERVICE_BUS_CONNECTION
```

---

## AKS Deployment Patterns ⭐⭐⭐

### Complete Microservices Deployment

```yaml
# Namespace
apiVersion: v1
kind: Namespace
metadata:
  name: production
---
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
  namespace: production
data:
  SPRING_PROFILES_ACTIVE: "prod"
  SERVER_PORT: "8080"
---
# Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: production
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: order-service-sa
      containers:
      - name: order-service
        image: contosoacr.azurecr.io/order-service:v2.1.0
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: order-service-config
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "1000m"
            memory: "1Gi"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
---
# Service
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: production
spec:
  selector:
    app: order-service
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
---
# HPA
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
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

---

## Helm + AKS ⭐⭐

```
Helm Chart: order-service/
├── Chart.yaml
├── values.yaml
├── values-staging.yaml
├── values-production.yaml
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── hpa.yaml
    ├── configmap.yaml
    └── serviceaccount.yaml

Deploy:
helm upgrade --install order-service ./order-service \
  --namespace production \
  --values values-production.yaml \
  --set image.tag=v2.1.0
```

---

## AKS Monitoring ⭐⭐⭐

```
AKS Cluster
├── Container Insights (Azure Monitor)
│   ├── Node-level metrics (CPU, memory, disk)
│   ├── Pod-level metrics
│   ├── Container logs (stdout/stderr)
│   └── Kubernetes events
│
├── Application Insights (per service)
│   ├── Request rates, latency, errors
│   ├── Dependency tracking
│   ├── Distributed tracing
│   └── Custom metrics
│
├── Prometheus + Grafana (Azure Managed)
│   ├── Custom metrics scraping
│   ├── Dashboards
│   └── Alert rules
│
└── Log Analytics Workspace
    └── KQL queries on all logs
```

---

## Interview Questions

### Q: Explain AKS architecture — what does Azure manage vs what do you manage?
**A:**
- **Azure manages**: Control plane (API server, etcd, scheduler, controller manager). Free, SLA-backed, auto-upgraded.
- **You manage**: Node pools (VM selection, scaling), workloads (deployments, services), networking configuration, security (RBAC, pod security), monitoring setup.

You pay only for the worker nodes (VMs), not the control plane.

### Q: How do AKS pods access Azure services securely?
**A:** Workload Identity:
1. Create Azure Managed Identity with required RBAC roles
2. Create Kubernetes Service Account annotated with the identity's client ID
3. Establish federated credential linking the K8s SA to the Azure MI
4. Pod uses the Service Account → Azure SDK's DefaultAzureCredential automatically gets a token
5. No secrets stored in Kubernetes — identity-based authentication only

### Q: AKS vs Container Apps — when to use which?
**A:**
- **AKS**: Full Kubernetes control. Use when you need custom networking (service mesh, specific CNI), advanced scheduling (node affinity, taints), Helm charts, direct kubectl access, multi-container pod patterns, or complex stateful workloads.
- **Container Apps**: Simplified container platform. Use when you want microservices without Kubernetes complexity, built-in Dapr/KEDA, scale-to-zero, simple HTTP/event-driven services, and don't need low-level K8s customization.

Rule: If your team knows Kubernetes and needs fine-grained control → AKS. If you want "just deploy my containers" → Container Apps.

### Q: How does autoscaling work in AKS?
**A:** Three levels:
1. **HPA**: Scales pod replicas based on CPU/memory/custom metrics
2. **Cluster Autoscaler**: Adds/removes nodes when pods can't be scheduled (pending pods trigger scale-out)
3. **KEDA**: Event-driven scaling based on external sources (Service Bus queue length, Event Hub lag, HTTP traffic). Can scale to zero.

These work together: KEDA/HPA creates more pods → Cluster Autoscaler provisions nodes to run them.

### Q: Describe a production AKS networking setup.
**A:**
1. Azure CNI for direct VNet integration (pods get VNet IPs)
2. Dedicated /22+ subnet for AKS (enough IPs for nodes + pods)
3. Application Gateway Ingress Controller (AGIC) for external traffic + WAF
4. Internal Load Balancer for internal services
5. Private Endpoints for database, Redis, Key Vault, Storage
6. NSG on subnets for network-level filtering
7. Network Policies for pod-to-pod traffic control
8. Private cluster option (API server not publicly accessible)
