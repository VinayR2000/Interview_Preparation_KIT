# Azure Fundamentals

## Theory

### What is Microsoft Azure?
Microsoft's public cloud computing platform. Provides 200+ services for compute, storage, networking, databases, AI, DevOps, and more. Pay-as-you-go pricing model, similar to AWS.

### Cloud Service Models (Azure Context)

| Model | What You Manage | What Azure Manages | Azure Example |
|-------|----------------|-------------------|---------------|
| IaaS | OS, Runtime, App, Data | Hardware, Networking, Virtualization | Virtual Machines |
| PaaS | App, Data | OS, Runtime, Hardware | App Service |
| SaaS | Nothing (just use it) | Everything | Microsoft 365, Dynamics 365 |

### Azure vs AWS Terminology

| Concept | AWS | Azure |
|---------|-----|-------|
| Virtual Server | EC2 | Virtual Machine (VM) |
| Object Storage | S3 | Blob Storage |
| Virtual Network | VPC | VNet |
| IAM | IAM | Microsoft Entra ID (formerly Azure AD) |
| Container Orchestration | EKS | AKS |
| Serverless Functions | Lambda | Azure Functions |
| API Gateway | API Gateway | Azure API Management |
| Message Queue | SQS | Azure Service Bus Queue |
| Event Streaming | Kinesis / MSK | Event Hubs |
| Managed Kubernetes | EKS | AKS |
| Container Registry | ECR | ACR |
| Load Balancer (L4) | NLB | Azure Load Balancer |
| Load Balancer (L7) | ALB | Application Gateway |
| DNS | Route 53 | Azure DNS |
| CDN | CloudFront | Azure CDN / Front Door |
| Monitoring | CloudWatch | Azure Monitor |
| IaC | CloudFormation | ARM Templates / Bicep |
| CI/CD | CodePipeline | Azure DevOps Pipelines |
| Secrets | Secrets Manager | Key Vault |
| Managed PostgreSQL | RDS PostgreSQL | Azure Database for PostgreSQL |
| Cache | ElastiCache | Azure Cache for Redis |
| NoSQL | DynamoDB | Cosmos DB |

---

## Internal Working

### Azure Global Infrastructure

```
Azure Global Infrastructure
├── Geographies (60+ regions across 140+ countries)
│   ├── Americas
│   ├── Europe
│   ├── Asia Pacific
│   ├── Middle East & Africa
│   └── ...
├── Regions (60+)
│   ├── East US
│   ├── West Europe
│   ├── Central India
│   ├── Southeast Asia
│   └── ...
├── Availability Zones (3+ per enabled region)
│   └── Independent data centers within a region
├── Edge Locations / PoPs (190+)
│   └── Azure CDN, Front Door
└── Sovereign Clouds
    ├── Azure Government (US)
    └── Azure China (21Vianet)
```

### Region
- Geographic area containing one or more data centers
- Examples: East US, West Europe, Central India
- Choose based on: latency, compliance, service availability, pricing
- Some services are region-specific

### Availability Zone (AZ) ⭐⭐⭐
- Physically separate data centers within a region
- Independent power, cooling, networking
- Connected via high-speed private fiber
- Designed to fail independently
- Not all regions have Availability Zones

```
Region: East US
├── Availability Zone 1 (Data Center cluster)
├── Availability Zone 2 (Data Center cluster)
└── Availability Zone 3 (Data Center cluster)
```

### Region Pairs
- Each Azure region is paired with another region in the same geography
- Provides disaster recovery and data residency compliance
- Example: East US ↔ West US, North Europe ↔ West Europe

```
Geography: United States
├── Region Pair: East US ↔ West US
├── Region Pair: East US 2 ↔ Central US
└── Region Pair: South Central US ↔ North Central US
```

---

## Azure Resource Hierarchy ⭐⭐⭐

```
Azure Tenant (Microsoft Entra ID)
│
├── Management Group (optional, for organizing subscriptions)
│   ├── Management Group (can be nested)
│   │   ├── Subscription
│   │   └── Subscription
│   └── Subscription
│
├── Subscription (billing boundary + access control boundary)
│   ├── Resource Group (logical container)
│   │   ├── Resource (VM, VNet, Database, etc.)
│   │   ├── Resource
│   │   └── Resource
│   └── Resource Group
│       ├── Resource
│       └── Resource
│
└── Subscription
    └── Resource Group
        └── Resource
```

### Tenant
- Top-level identity boundary
- Represents your organization in Microsoft Entra ID
- One tenant per organization (typically)
- Contains all users, groups, apps

### Management Group
- Optional organizational layer above subscriptions
- Can be nested (up to 6 levels)
- Apply policies and RBAC at scale
- Root management group exists by default

### Subscription ⭐⭐⭐
- **Billing boundary**: Each subscription gets its own invoice
- **Access control boundary**: Apply RBAC at subscription level
- Common patterns:
  - Dev/Test/Prod subscriptions
  - Department-based subscriptions
  - Project-based subscriptions
- Has resource limits/quotas

### Resource Group ⭐⭐⭐
- **Logical container** for related resources
- Every resource must belong to exactly one resource group
- Resources in a group share the same lifecycle
- Can contain resources from different regions
- Delete a resource group → deletes all resources inside
- Apply RBAC, tags, policies at resource group level

```
Resource Group: rg-ecommerce-prod
├── VM: ecommerce-api-vm
├── VNet: ecommerce-vnet
├── Azure Database for PostgreSQL: ecommerce-db
├── Azure Cache for Redis: ecommerce-cache
├── Storage Account: ecommercestorage
└── Key Vault: ecommerce-kv
```

### Azure Resource Manager (ARM) ⭐⭐⭐
- The deployment and management layer for all Azure resources
- ALL requests go through ARM (Portal, CLI, SDK, Terraform)
- Provides:
  - Consistent management layer
  - Access control (RBAC)
  - Tags
  - Templates (ARM Templates / Bicep)
  - Locks

```
User / Tool
    │
    ▼
Azure Resource Manager (ARM)
    │
    ├── Authentication (Entra ID)
    ├── Authorization (RBAC)
    ├── Apply Policies
    │
    ▼
Resource Provider
    │
    ▼
Resource (VM, VNet, DB, etc.)
```

---

## Shared Responsibility Model ⭐⭐⭐

| Layer | IaaS (VM) | PaaS (App Service) | SaaS (M365) |
|-------|-----------|-------------------|--------------|
| Data & Access | You | You | You |
| Applications | You | You | Microsoft |
| Runtime | You | Microsoft | Microsoft |
| OS | You | Microsoft | Microsoft |
| Virtualization | Microsoft | Microsoft | Microsoft |
| Hardware | Microsoft | Microsoft | Microsoft |
| Networking | Microsoft | Microsoft | Microsoft |
| Physical Security | Microsoft | Microsoft | Microsoft |

**Key principle**: As you move from IaaS → PaaS → SaaS, Microsoft takes more responsibility.

---

## Core Cloud Concepts

### Scalability
- **Vertical Scaling (Scale Up)**: Increase VM size (more CPU, RAM)
- **Horizontal Scaling (Scale Out)**: Add more instances

### Elasticity
- Automatically scale resources based on demand
- Scale out during peak, scale in during low usage
- Azure VM Scale Sets, App Service autoscale

### High Availability
- System remains operational with minimal downtime
- Measured in "nines": 99.9% = ~8.7 hours downtime/year
- Achieved through: multiple AZs, load balancing, redundancy

### Fault Tolerance
- System continues to function even when components fail
- Redundancy, failover, replication

### Disaster Recovery
- Ability to recover from major failures
- RPO (Recovery Point Objective): How much data loss is acceptable?
- RTO (Recovery Time Objective): How long to recover?

---

## Azure Tools & Interfaces

| Tool | Purpose |
|------|---------|
| Azure Portal | Web-based GUI |
| Azure CLI | Command-line (cross-platform) |
| Azure PowerShell | PowerShell cmdlets |
| Azure Cloud Shell | Browser-based CLI (Bash/PowerShell) |
| Azure SDKs | Language-specific libraries (Java, Python, .NET) |
| Terraform | Infrastructure as Code |
| ARM Templates | JSON-based IaC (Azure-native) |
| Bicep | Domain-specific IaC language (compiles to ARM) |

---

## Interview Questions

### Q: What is the difference between a Region and an Availability Zone?
**A:** A Region is a geographic area (e.g., East US) containing multiple data centers. An Availability Zone is an individual data center (or cluster) within a region, with independent power, cooling, and networking. You deploy across AZs for high availability within a region.

### Q: What is a Resource Group and why is it important?
**A:** A Resource Group is a logical container for Azure resources that share the same lifecycle. It provides:
- Organization: Group related resources together
- Access Control: Apply RBAC at the group level
- Cost Management: View costs per resource group
- Lifecycle Management: Delete the group to delete all resources
- Policy Application: Enforce standards on all resources in the group

### Q: What is Azure Resource Manager?
**A:** ARM is the deployment and management service for Azure. Every interaction with Azure (Portal, CLI, SDK, Terraform) goes through ARM. It provides authentication, authorization, consistent API, template-based deployments, and resource locking.

### Q: How do Azure Subscriptions help with governance?
**A:** Subscriptions serve as both a billing boundary and an access control boundary. Organizations use multiple subscriptions to:
- Separate environments (Dev/Test/Prod)
- Isolate billing per department/project
- Apply different RBAC and policies
- Stay within resource quotas

### Q: What is the Shared Responsibility Model?
**A:** It defines what Microsoft manages vs. what the customer manages. For IaaS (VMs), the customer manages the OS and everything above it. For PaaS (App Service), Microsoft manages the OS and runtime. For SaaS, the customer only manages data and access. Security is always a shared concern.

### Q: How would you choose an Azure region?
**A:**
1. **Latency**: Choose closest region to your users
2. **Compliance**: Some data must stay in specific geographies (GDPR → Europe)
3. **Service availability**: Not all services are available in all regions
4. **Pricing**: Prices vary by region
5. **Paired region**: Consider DR with the paired region

### Q: Azure vs AWS — Key philosophical differences?
**A:**
- Azure integrates deeply with Microsoft ecosystem (Active Directory, Office 365, Windows Server)
- Azure naming follows enterprise/Microsoft conventions (vs. AWS's custom naming)
- Azure has a stronger hybrid cloud story (Azure Arc, Azure Stack)
- Both are functionally equivalent for most workloads
- Azure's IAM is built into Entra ID (formerly Azure AD), which is more centralized than AWS IAM
