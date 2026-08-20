# Azure System Design (HLD)

## Theory

### The Ultimate Azure Architecture
For interviews, you should be able to design and explain this complete architecture:

"I can take my Spring Boot microservices, containerize them, secure them with Entra ID/managed identities and Key Vault, deploy them on AKS, expose them through API Management/Application Gateway, use PostgreSQL/Redis/Service Bus, monitor them with Azure Monitor/Application Insights, and provision the infrastructure using Terraform."

---

## Complete Production Architecture ⭐⭐⭐

```
                              INTERNET
                                 │
                                 ▼
                    ┌─────────────────────────┐
                    │    Azure Front Door      │ ← Global LB + CDN + WAF
                    │    (Global Layer)        │
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   Azure API Management   │ ← Auth + Rate Limit + Routing
                    │   (Regional Gateway)     │
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  Application Gateway     │ ← L7 LB + WAF + TLS Termination
                    │  (Ingress Controller)    │
                    └───────────┬─────────────┘
                                │
        ┌───────────────────────┼────────────────────────┐
        │                       │                        │
        ▼                       ▼                        ▼
┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ order-service │    │  user-service    │    │ payment-service  │
│   (3 pods)   │    │   (3 pods)       │    │   (3 pods)       │
│  Spring Boot │    │  Spring Boot     │    │  Spring Boot     │
└──────┬───────┘    └────────┬─────────┘    └────────┬─────────┘
       │                     │                        │
       │      ┌──────────────┼────────────────────────┘
       │      │              │
       ▼      ▼              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Azure Services (via Private Endpoints)         │
│                                                                   │
│  ┌──────────┐  ┌───────────┐  ┌────────┐  ┌──────────────────┐ │
│  │PostgreSQL│  │   Redis   │  │  Key   │  │  Service Bus     │ │
│  │(Zone-HA) │  │(Zone-Red.)│  │ Vault  │  │  (Topics/Queues) │ │
│  └──────────┘  └───────────┘  └────────┘  └──────────────────┘ │
│                                                                   │
│  ┌──────────────┐  ┌─────────────────┐  ┌────────────────────┐ │
│  │ Blob Storage │  │  Event Hubs     │  │  Cosmos DB         │ │
│  │ (ZRS/GRS)   │  │  (Streaming)    │  │  (if needed)       │ │
│  └──────────────┘  └─────────────────┘  └────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│              Monitoring                      │
│  ┌─────────────────┐  ┌─────────────────┐  │
│  │App Insights     │  │Container Insights│  │
│  │(per service)    │  │(AKS cluster)    │  │
│  └─────────────────┘  └─────────────────┘  │
│  ┌─────────────────┐  ┌─────────────────┐  │
│  │Azure Monitor    │  │Managed Grafana  │  │
│  │(Alerts)         │  │(Dashboards)     │  │
│  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│              Infrastructure                  │
│  ┌────────────┐  ┌──────────────────────┐   │
│  │ Terraform  │  │ Azure DevOps / GH    │   │
│  │ (IaC)      │  │ Actions (CI/CD)      │   │
│  └────────────┘  └──────────────────────┘   │
│  ┌────────────┐  ┌──────────────────────┐   │
│  │    ACR     │  │  GitOps (ArgoCD)     │   │
│  │ (Images)   │  │  (Deployments)       │   │
│  └────────────┘  └──────────────────────┘   │
└─────────────────────────────────────────────┘
```

---

## Network Architecture Detail

```
VNet: vnet-prod (10.0.0.0/16)
│
├── Subnet: snet-appgw (10.0.0.0/24)
│   └── Application Gateway (WAF_v2)
│
├── Subnet: snet-apim (10.0.1.0/24)
│   └── Azure API Management (Premium, VNet-integrated)
│
├── Subnet: snet-aks (10.0.4.0/22)    ← /22 for 1000+ IPs
│   ├── System Node Pool (3 nodes, across AZs)
│   └── App Node Pool (3-20 nodes, autoscale, across AZs)
│
├── Subnet: snet-db (10.0.8.0/24)
│   └── PostgreSQL Flexible Server (delegated)
│
├── Subnet: snet-pe (10.0.9.0/24)
│   ├── PE → Redis
│   ├── PE → Key Vault
│   ├── PE → Storage
│   ├── PE → Service Bus
│   └── PE → Event Hubs
│
├── Subnet: snet-bastion (10.0.10.0/26)
│   └── Azure Bastion (secure management access)
│
└── NSG Rules:
    ├── snet-appgw: Allow 80/443 from Internet
    ├── snet-apim: Allow from snet-appgw only
    ├── snet-aks: Allow from snet-apim only
    ├── snet-db: Allow 5432 from snet-aks only
    └── snet-pe: Allow from snet-aks only
```

---

## Security Architecture

```
Security Layers:
│
├── Identity (Entra ID)
│   ├── Users: MFA + Conditional Access
│   ├── Services: Managed Identity / Workload Identity
│   └── APIs: JWT validation at APIM
│
├── Network
│   ├── WAF (OWASP protection)
│   ├── NSG (subnet isolation)
│   ├── Private Endpoints (no public PaaS exposure)
│   └── Network Policies (pod-to-pod in AKS)
│
├── Secrets
│   ├── Key Vault (all secrets centralized)
│   ├── Managed Identity access (no stored credentials)
│   └── Secret rotation (auto with Event Grid + Functions)
│
├── Data
│   ├── Encryption at rest (AES-256, Microsoft/CMK)
│   ├── TLS in transit (1.2+)
│   └── Passwordless DB auth (Managed Identity)
│
└── Monitoring
    ├── Defender for Cloud (posture)
    ├── Activity Log (who did what)
    ├── Key Vault diagnostics (secret access audit)
    └── Sentinel (SIEM for advanced threat detection)
```

---

## Data Flow — Order Processing Example

```
1. Client sends POST /api/orders (HTTPS)
       │
2. Front Door → routes to closest region
       │
3. APIM → validates JWT, rate limits, routes
       │
4. App Gateway → WAF inspection, TLS termination
       │
5. AKS Ingress → routes to order-service pod
       │
6. order-service (Spring Boot):
   ├── Reads secrets from Key Vault (via Managed Identity)
   ├── Saves order to PostgreSQL (via Private Endpoint)
   ├── Caches user profile from Redis (via Private Endpoint)
   ├── Publishes "OrderCreated" event to Service Bus Topic
   │       │
   │       ├── payment-service subscription → processes payment
   │       ├── inventory-service subscription → reserves stock
   │       └── notification-service subscription → sends email
   │
   └── Returns 201 Created to client

7. Application Insights → captures request trace across all services
8. Azure Monitor → alerts if error rate > threshold
```

---

## Deployment Flow

```
Developer Experience:
│
├── Feature branch → PR → Code review
│
├── PR merge → CI Pipeline triggers:
│   ├── mvn clean test (unit tests)
│   ├── SonarQube analysis
│   ├── docker build → docker push to ACR
│   └── Helm lint
│
├── Deploy to Dev (auto):
│   └── helm upgrade --install -n dev
│
├── Deploy to Staging (auto after dev tests pass):
│   ├── helm upgrade --install -n staging
│   └── Integration tests + smoke tests
│
└── Deploy to Production (manual approval):
    ├── Canary: 10% traffic to new version
    ├── Monitor: error rate, latency for 30 min
    ├── If OK: scale to 100%
    └── If NOT OK: rollback (helm rollback)
```

---

## Cost Estimation (Example Production Environment)

```
Monthly Cost Estimate (typical production):
├── AKS Nodes (6x Standard_D4s_v5): ~$1,200
├── PostgreSQL (GP, D4s, Zone-HA): ~$800
├── Redis (Premium P1, Zone-redundant): ~$500
├── APIM (Standard): ~$600
├── Application Gateway (WAF_v2): ~$400
├── Storage (100 GB Hot + GRS): ~$50
├── Service Bus (Premium): ~$700
├── Key Vault: ~$5
├── Monitoring (Log Analytics, App Insights): ~$300
├── Front Door: ~$200
├── ACR (Premium): ~$150
└── Networking (NAT Gateway, bandwidth): ~$200
                                          ─────────
                                Total: ~$5,100/month

Cost optimizations:
├── Reserved Instances (AKS nodes): -40% on compute
├── Dev/Test: Scale down or shut down overnight
├── Spot nodes for non-critical workloads: -60-90%
└── Right-size after observing actual usage
```

---

## Key Azure Comparisons (Interview Must-Know) ⭐⭐⭐

### VM vs App Service vs Container Apps vs AKS

| Factor | VM | App Service | Container Apps | AKS |
|--------|----|-----------:|:-------------:|:---:|
| Abstraction | Lowest | High | Higher | Medium |
| Control | Full OS | App only | Container | K8s full |
| Scaling | VMSS (min) | Built-in (sec) | Built-in + KEDA | HPA + CA |
| Cost model | Per VM | Per plan | Per request/vCPU | Per node |
| Best for | Legacy, custom | Simple web apps | Event-driven | Complex microservices |

### Azure Load Balancer vs Application Gateway vs Front Door

| Feature | Load Balancer | App Gateway | Front Door |
|---------|:------------:|:-----------:|:----------:|
| Layer | L4 | L7 | L7 |
| Scope | Regional | Regional | Global |
| WAF | No | Yes | Yes |
| SSL | No | Yes | Yes |
| URL routing | No | Yes | Yes |
| Use case | TCP/UDP, internal | Regional web | Global web |

### Service Bus vs Event Hubs vs Event Grid

| Feature | Service Bus | Event Hubs | Event Grid |
|---------|:-----------:|:----------:|:----------:|
| Pattern | Messaging | Streaming | Routing |
| Ordering | Sessions (FIFO) | Per partition | No |
| Replay | No | Yes | No |
| Scale | Moderate | Millions/sec | High |
| Use case | Reliable tasks | Analytics | React to events |

### Managed Identity vs Service Principal

| Factor | Managed Identity | Service Principal |
|--------|:---------------:|:-----------------:|
| Credential management | None (Azure manages) | You manage (secrets/certs) |
| Rotation | Automatic | Manual |
| Works outside Azure | No | Yes |
| Security risk | Lowest | Higher (credential leak possible) |
| Best for | Azure-hosted workloads | CI/CD, external systems |

---

## Interview Questions

### Q: Design an e-commerce system on Azure.
**A:**
1. **Ingress**: Front Door (global CDN + WAF) → APIM (auth + rate limiting) → App Gateway (regional L7)
2. **Compute**: AKS with microservices (order, product, user, payment, notification)
3. **Database**: PostgreSQL for transactional data, Redis for caching, Cosmos DB for product catalog (if global)
4. **Messaging**: Service Bus topics for reliable event-driven communication between services
5. **Storage**: Blob for product images, CDN for delivery
6. **Security**: Managed Identity everywhere, Key Vault for secrets, Private Endpoints, WAF
7. **Monitoring**: App Insights per service, Container Insights for AKS, alerts on error rate/latency
8. **DR**: Active-Passive in secondary region, Front Door failover, geo-redundant backups
9. **IaC**: Terraform for all infrastructure, GitOps for deployments
10. **CI/CD**: Azure DevOps → ACR → Helm → AKS (with staging validation)

### Q: Walk me through a request from client to database in your Azure architecture.
**A:**
1. Client → Front Door (anycast, closest PoP, WAF inspection)
2. Front Door → APIM (JWT validation, rate limiting, routing)
3. APIM → App Gateway (TLS termination, L7 routing)
4. App Gateway → AKS Ingress Controller → order-service pod
5. Pod → DefaultAzureCredential → Managed Identity → Key Vault (get config)
6. Pod → PostgreSQL via Private Endpoint (private IP, VNet-internal)
7. Response flows back: Pod → Ingress → App Gateway → APIM → Front Door → Client
8. Entire trace captured in Application Insights (correlation ID)

### Q: How do you ensure zero-downtime deployments?
**A:**
- AKS: Rolling update with maxSurge=1, maxUnavailable=0
- Readiness probes: Pod only receives traffic when healthy
- Canary: Deploy to 10% of pods, monitor, then full rollout
- Database migrations: Use backward-compatible changes (expand-contract pattern)
- Service Bus: Consumers handle old and new message formats
- Rollback: `helm rollback` within seconds if issues detected
- Feature flags: Decouple deployment from release

### Q: What would you do differently for a startup vs enterprise?
**A:**
**Startup (cost-focused):**
- App Service instead of AKS (simpler, cheaper)
- Basic PostgreSQL (no HA initially)
- Queue Storage instead of Service Bus (cheaper)
- Consumption Functions for event processing
- Single region (no DR initially)

**Enterprise (reliability + compliance):**
- AKS with multi-region active-active
- Premium everything (zone-redundant, geo-replicated)
- Dedicated APIM with VNet injection
- Sentinel for SIEM compliance
- Private clusters (no public API server)
- Customer-managed keys for encryption
- Azure Policy for governance at scale
