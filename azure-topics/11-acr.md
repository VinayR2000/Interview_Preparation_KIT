# Azure Container Registry (ACR)

## Theory

### What is ACR?
A managed Docker container registry (equivalent to AWS ECR). Stores and manages container images and OCI artifacts for deployment to AKS, App Service, Container Apps, etc.

---

## Internal Working

### ACR Architecture

```
Developer / CI Pipeline
    │
    ├── docker build → Creates image
    ├── docker tag → Tags for ACR
    └── docker push → Pushes to ACR
    │
    ▼
Azure Container Registry: contosoacr.azurecr.io
├── Repository: order-service
│   ├── Tag: latest
│   ├── Tag: v2.1.0
│   ├── Tag: v2.0.0
│   └── Tag: sha-abc123 (Git SHA)
│
├── Repository: user-service
│   ├── Tag: latest
│   ├── Tag: v1.5.0
│   └── Tag: v1.4.0
│
├── Repository: payment-service
│   └── Tag: v3.0.0
│
└── Repository: nginx-custom
    └── Tag: 1.25-custom
    │
    ▼ (Pull)
Deployment Targets:
├── AKS Cluster
├── App Service (Container)
├── Container Apps
└── Azure Functions (Container)
```

### SKUs

| SKU | Storage | Throughput | Features |
|-----|---------|-----------|----------|
| Basic | 10 GB | Low | Dev/test |
| Standard | 100 GB | Medium | Production (most workloads) |
| Premium | 500 GB+ | High | Geo-replication, Private Link, content trust |

### Authentication Methods

| Method | Description | Use Case |
|--------|-------------|----------|
| Managed Identity | Azure resource pulls without credentials | AKS, App Service, Container Apps |
| Admin Account | Username/password (disable in prod!) | Quick testing only |
| Service Principal | Client ID + secret | CI/CD pipelines (non-Azure) |
| Token (Repository-scoped) | Limited access per repository | External partners |
| az acr login | Azure CLI auth (developer) | Local development |

### AKS + ACR Integration ⭐⭐⭐

```
# Attach ACR to AKS (grants AcrPull role to AKS identity)
az aks update -n myAKS -g myRG --attach-acr contosoacr

Result:
AKS Managed Identity → AcrPull role → contosoacr
Pods can pull images without imagePullSecrets!
```

```yaml
# Kubernetes deployment - just reference ACR image
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: order-service
        image: contosoacr.azurecr.io/order-service:v2.1.0
        # No imagePullSecrets needed!
```

### ACR Tasks (Build in Cloud)
```
# Build image in ACR (no local Docker needed)
az acr build --registry contosoacr --image order-service:v2.1.0 .

Pipeline:
Git Push → ACR Task → Build Image → Push to ACR → Trigger AKS Deploy
```

### Geo-Replication (Premium SKU)
```
ACR: contosoacr.azurecr.io
├── Replica: East US (primary)
├── Replica: West Europe
└── Replica: Southeast Asia

AKS in East US pulls from East US replica (low latency)
AKS in West Europe pulls from West Europe replica (low latency)
```

---

## CI/CD Pipeline with ACR ⭐⭐⭐

```
GitHub Repository
    │
    ▼ (Push to main)
Azure DevOps Pipeline / GitHub Actions
    │
    ├── Stage: Build
    │   ├── mvn clean package (Spring Boot)
    │   ├── docker build -t contosoacr.azurecr.io/order-service:v2.1.0 .
    │   └── docker push contosoacr.azurecr.io/order-service:v2.1.0
    │
    ├── Stage: Deploy to Staging
    │   └── kubectl set image deployment/order-service \
    │         order-service=contosoacr.azurecr.io/order-service:v2.1.0 \
    │         --namespace staging
    │
    └── Stage: Deploy to Production (approval gate)
        └── kubectl set image deployment/order-service \
              order-service=contosoacr.azurecr.io/order-service:v2.1.0 \
              --namespace production
```

---

## Security Best Practices

| Practice | Reason |
|----------|--------|
| Disable admin account | Use Managed Identity or service principals |
| Use Private Endpoint | No public internet exposure |
| Enable content trust | Image signing/verification |
| Scan images | Vulnerability scanning (Defender for Containers) |
| Use immutable tags | Prevent tag overwriting in production |
| Limit repository access | Token-based scoped access |
| Use specific tags, not :latest | Reproducible deployments |

---

## Interview Questions

### Q: How does AKS pull images from ACR securely?
**A:** Use `az aks update --attach-acr` which grants the AKS cluster's managed identity the `AcrPull` role on the ACR. Pods can then reference images by their ACR URL without needing `imagePullSecrets`. No credentials stored in Kubernetes secrets.

### Q: ACR vs Docker Hub — why use ACR?
**A:**
- Private: Your images aren't publicly accessible
- Integrated: Native integration with AKS, App Service via Managed Identity
- Geo-replication: Images close to your clusters (low pull latency)
- Security: Vulnerability scanning, content trust, Private Endpoints
- Speed: ACR Tasks for cloud-based builds, no local Docker needed
- Compliance: Data stays in your chosen Azure regions

### Q: What is your image tagging strategy?
**A:** Use semantic versioning + Git SHA:
- `v2.1.0` — Release version (for production)
- `sha-abc123f` — Git commit SHA (for traceability)
- `latest` — Only for dev/convenience (never in production)
- Immutable tags in production (prevent overwrite)
- Image lifecycle policies to clean up old images
