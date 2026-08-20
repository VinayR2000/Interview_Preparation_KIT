# Azure App Service

## Theory

### What is Azure App Service?
A fully managed PaaS for hosting web applications, REST APIs, and backends. You deploy your code/container — Azure handles infrastructure, OS patching, scaling, and load balancing.

**Key for Java developers**: App Service has first-class support for Java (Tomcat, Java SE, JBoss EAP). You deploy your Spring Boot JAR/WAR and it just works.

### AWS Equivalents
- App Service ≈ AWS Elastic Beanstalk (managed platform)
- App Service Plan ≈ EC2 instances backing the environment

---

## Internal Working

### App Service Architecture

```
App Service Plan (compute resources)
├── Size: P2v3 (2 vCPU, 8 GB RAM)
├── OS: Linux
├── Instances: 3 (across Availability Zones)
│
├── Web App 1: order-service
│   ├── Runtime: Java 17
│   ├── Spring Boot JAR
│   └── Custom domain: api.contoso.com
│
├── Web App 2: user-service
│   ├── Runtime: Java 17
│   ├── Spring Boot JAR
│   └── Custom domain: users.contoso.com
│
└── Web App 3: admin-portal
    ├── Runtime: Node.js 20
    └── Custom domain: admin.contoso.com
```

### App Service Plan Tiers

| Tier | Features | Use Case |
|------|----------|----------|
| Free (F1) | Shared compute, 1 GB, no SLA | Testing |
| Basic (B1-B3) | Dedicated, manual scale, custom domain | Dev/Test |
| Standard (S1-S3) | Autoscale, deployment slots, VNet integration | Production |
| Premium (P1v3-P3v3) | More CPU/RAM, more slots, faster scaling | High-traffic production |
| Isolated (I1v2-I3v2) | Dedicated VNet (ASE), maximum isolation | Enterprise/compliance |

### Key Concept: Apps share the App Service Plan
- Multiple apps can run on one plan (share compute)
- Plan defines the VM size and count
- Apps compete for resources on the same plan
- Use separate plans for production isolation

---

## Deployment Slots ⭐⭐⭐

Enable zero-downtime deployments and blue-green / canary strategies.

```
App Service: order-service
├── Production Slot (receives live traffic)
│   └── Running: v2.1.0
│
├── Staging Slot (for testing before swap)
│   └── Running: v2.2.0 (new version deployed here)
│
└── Dev Slot (for development testing)
    └── Running: v2.3.0-SNAPSHOT

Deployment flow:
1. Deploy v2.2.0 to Staging slot
2. Test in Staging (different URL: order-service-staging.azurewebsites.net)
3. Swap Staging ↔ Production (instant, no downtime)
4. If issues → Swap back (instant rollback)
```

### Slot Swap Mechanics
```
Before Swap:
Production → v2.1.0 (traffic goes here)
Staging    → v2.2.0

Swap operation:
- Azure warms up the staging slot
- Routes change instantly (no restart)
- Connection strings/app settings can be "slot-sticky"

After Swap:
Production → v2.2.0 (traffic goes here now)
Staging    → v2.1.0 (rollback ready)
```

**Slot-sticky settings**: Settings that stay with the slot (not swapped):
- Database connection strings for slot-specific databases
- Feature flags for staging testing
- Application Insights instrumentation key per slot

---

## Autoscaling ⭐⭐⭐

```
Autoscale Rules (App Service Plan level):
├── Scale Out:
│   ├── Rule 1: CPU > 70% for 5 min → +1 instance
│   ├── Rule 2: HTTP Queue > 100 → +2 instances
│   └── Max instances: 10
│
├── Scale In:
│   ├── Rule 1: CPU < 30% for 10 min → -1 instance
│   └── Min instances: 2
│
└── Schedule-based:
    ├── Weekdays 8AM-6PM: Min 4, Max 10
    └── Weekends: Min 2, Max 5
```

---

## VNet Integration ⭐⭐⭐

By default, App Service is multi-tenant and outbound traffic goes via public internet. VNet Integration gives your app a presence in your VNet.

```
Without VNet Integration:
App Service → (Public Internet) → Azure PostgreSQL (public endpoint)
                                   Problem: traffic over internet

With VNet Integration:
App Service → (VNet, private traffic) → Private Endpoint → PostgreSQL
                                        Traffic stays private!
```

```
VNet: vnet-prod (10.0.0.0/16)
├── Subnet: snet-app-integration (10.0.10.0/24) ← App Service connects here
│   └── App Service outbound traffic enters VNet
│
├── Subnet: snet-private-endpoints (10.0.4.0/24)
│   ├── PE → PostgreSQL (10.0.4.5)
│   ├── PE → Redis (10.0.4.6)
│   └── PE → Key Vault (10.0.4.7)
│
└── Result: App Service talks to DB/Redis/KV via private network
```

---

## Spring Boot on App Service ⭐⭐⭐

### Deployment Options

| Method | Description |
|--------|-------------|
| JAR deployment | Upload Spring Boot fat JAR directly |
| WAR deployment | Deploy to managed Tomcat |
| Docker container | Deploy custom container image |
| ZIP deploy | Upload build artifacts |
| GitHub Actions | CI/CD pipeline |
| Azure DevOps | Pipeline deployment |

### Configuration

```
App Service Configuration:
├── Application Settings (environment variables)
│   ├── SPRING_PROFILES_ACTIVE = prod
│   ├── JAVA_OPTS = -Xms512m -Xmx1024m
│   └── SERVER_PORT = 80 (App Service uses 80/443)
│
├── Connection Strings
│   └── (or use Key Vault references)
│
├── General Settings
│   ├── Stack: Java 17
│   ├── Java web server: Java SE (embedded Tomcat)
│   └── Always On: true (prevents cold starts)
│
└── Key Vault References
    ├── @Microsoft.KeyVault(VaultName=my-kv;SecretName=db-password)
    └── Resolved at runtime via Managed Identity
```

### Health Checks
```
App Service Health Check:
├── Path: /actuator/health
├── Interval: 30 seconds
├── Unhealthy threshold: 3 consecutive failures
└── Action: Route traffic away from unhealthy instance
```

---

## App Service + Managed Identity Flow

```
1. Enable System-assigned Managed Identity on App Service
2. Grant RBAC roles:
   ├── Key Vault Secrets User → on Key Vault
   ├── Storage Blob Data Contributor → on Storage Account
   └── Azure Service Bus Data Sender → on Service Bus

3. In Spring Boot:
   └── Use DefaultAzureCredential (auto-detects Managed Identity)

4. At runtime:
   App Service
       │
       ▼ (Managed Identity)
   Entra ID
       │
       ▼ (Access Token)
   Key Vault / Storage / Service Bus
       │
       ▼ (Authorized access)
   Secret value / Blob data / Message sent
```

---

## Custom Domains + TLS ⭐⭐

```
Setup:
1. Add custom domain: api.contoso.com
2. Verify domain ownership (CNAME or TXT record)
3. Bind TLS certificate:
   ├── Option A: App Service Managed Certificate (free, auto-renew)
   ├── Option B: Import from Key Vault
   └── Option C: Upload PFX certificate
4. Enforce HTTPS only

Traffic flow:
User → api.contoso.com → DNS → App Service → Spring Boot
                                    │
                                    └── TLS terminated at App Service
```

---

## Interview Questions

### Q: What is Azure App Service and when would you use it?
**A:** App Service is a fully managed PaaS for hosting web applications. Use it when:
- You want to deploy Spring Boot apps without managing VMs or containers
- You need built-in scaling, load balancing, deployment slots
- You don't need full OS/container orchestration control
- You want managed TLS, custom domains, authentication

Don't use it when:
- You need multiple interacting microservices (use AKS)
- You need full OS control (use VM)
- You need custom networking configurations beyond VNet integration

### Q: How do deployment slots enable zero-downtime deployments?
**A:**
1. Deploy new version to staging slot (production unaffected)
2. Test the staging slot independently (has its own URL)
3. Warm up staging (pre-load application, establish connections)
4. Swap: routes change instantly — no restart, no downtime
5. If issues: swap back immediately (rollback in seconds)

Slot-sticky settings (like connection strings) stay with the slot, so staging connects to test DB and production connects to prod DB.

### Q: App Service vs VM vs Container Apps vs AKS — decision framework?
**A:**
- **App Service**: Single web app/API, simple scaling, managed everything. Best for 1-3 services.
- **VM**: Full OS control, legacy apps, specific OS requirements.
- **Container Apps**: Containerized microservices, event-driven, scale-to-zero, but don't want to manage Kubernetes.
- **AKS**: Full Kubernetes control, complex microservice architectures, need custom networking/service mesh/advanced scheduling.

For a Java developer with 3-5 Spring Boot services → App Service is often sufficient.
For 10+ microservices with complex interactions → AKS.

### Q: How do you configure Spring Boot on App Service?
**A:**
1. Set Java version (Java 17/21) in Configuration
2. Set `SERVER_PORT=80` (App Service routes to port 80)
3. Set `SPRING_PROFILES_ACTIVE=prod`
4. Configure JVM options via `JAVA_OPTS`
5. Enable "Always On" to prevent cold starts
6. Use Key Vault references for secrets
7. Enable Managed Identity for secure access to other Azure services
8. Configure health check path to `/actuator/health`
9. Enable VNet Integration for private backend access

### Q: What is the App Service Plan and how does pricing work?
**A:** The App Service Plan defines the compute resources (VM size and count). Multiple apps can share one plan. You pay for the plan regardless of whether apps are running. Key decisions:
- Tier (Standard/Premium) determines features (slots, VNet, autoscale)
- Size (S1/P2v3) determines CPU/RAM per instance
- Instance count (manual or autoscale) determines capacity
- Linux plans are generally cheaper than Windows plans
