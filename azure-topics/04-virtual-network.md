# Azure Virtual Network (VNet)

## Theory

### What is a VNet?
A logically isolated network in Azure. Equivalent to AWS VPC. Enables Azure resources to securely communicate with each other, the internet, and on-premises networks.

### Key Concepts

| Concept | Description | AWS Equivalent |
|---------|-------------|----------------|
| VNet | Virtual network | VPC |
| Subnet | Network segment within a VNet | Subnet |
| NSG | Network Security Group (firewall rules) | Security Group |
| Route Table | Custom routing rules | Route Table |
| NIC | Network Interface Card | ENI |
| Public IP | Internet-routable address | Elastic IP |
| Private IP | Internal address (from subnet CIDR) | Private IP |
| NAT Gateway | Outbound internet for private resources | NAT Gateway |
| VNet Peering | Connect two VNets | VPC Peering |
| Private Endpoint | Private access to Azure PaaS services | VPC Endpoint |
| Service Endpoint | Optimized route to Azure services | Gateway Endpoint |

---

## Internal Working

### VNet Architecture ⭐⭐⭐

```
VNet: vnet-prod (10.0.0.0/16)
│
├── Subnet: snet-web (10.0.1.0/24) — 251 usable IPs
│   ├── NSG: nsg-web (allow HTTP/HTTPS from internet)
│   ├── App Service (VNet-integrated)
│   └── Application Gateway
│
├── Subnet: snet-app (10.0.2.0/24) — 251 usable IPs
│   ├── NSG: nsg-app (allow from snet-web only)
│   ├── AKS Node Pool
│   └── VM Scale Set
│
├── Subnet: snet-db (10.0.3.0/24) — 251 usable IPs
│   ├── NSG: nsg-db (allow from snet-app only, port 5432)
│   ├── PostgreSQL Flexible Server
│   └── Redis Cache
│
├── Subnet: snet-private-endpoints (10.0.4.0/24)
│   ├── Private Endpoint → Key Vault
│   ├── Private Endpoint → Storage Account
│   └── Private Endpoint → Service Bus
│
└── Subnet: AzureBastionSubnet (10.0.5.0/26)
    └── Azure Bastion (secure VM access)
```

### CIDR and IP Addressing ⭐⭐⭐

```
VNet CIDR: 10.0.0.0/16  →  65,536 addresses
│
├── Subnet: 10.0.1.0/24  →  256 addresses (251 usable)
├── Subnet: 10.0.2.0/24  →  256 addresses (251 usable)
├── Subnet: 10.0.3.0/24  →  256 addresses (251 usable)
└── Subnet: 10.0.4.0/24  →  256 addresses (251 usable)

Azure reserves 5 IPs per subnet:
- .0 = Network address
- .1 = Default gateway
- .2, .3 = Azure DNS
- .255 = Broadcast

/24 subnet = 256 - 5 = 251 usable IPs
```

### Subnet Design Principles
1. **Separate by function**: Web tier, App tier, DB tier, Management
2. **Separate by security**: Different NSGs per subnet
3. **Plan for growth**: Don't use /28 if you might scale
4. **Dedicated subnets**: Some services require dedicated subnets (AKS, App Gateway, Bastion)

---

## Network Security Groups (NSG) ⭐⭐⭐

### What is an NSG?
A stateful firewall that filters network traffic to/from Azure resources. Contains security rules (allow/deny) evaluated by priority.

### NSG Rule Structure

| Property | Description |
|----------|-------------|
| Priority | 100-4096 (lower = evaluated first) |
| Source | IP, CIDR, Service Tag, ASG |
| Source Port | Port or range |
| Destination | IP, CIDR, Service Tag, ASG |
| Destination Port | Port or range |
| Protocol | TCP, UDP, ICMP, Any |
| Action | Allow or Deny |
| Direction | Inbound or Outbound |

### Default Rules (cannot be deleted)

**Inbound defaults:**
| Priority | Name | Action |
|----------|------|--------|
| 65000 | AllowVNetInBound | Allow VNet → VNet |
| 65001 | AllowAzureLoadBalancerInBound | Allow LB health probes |
| 65500 | DenyAllInBound | Deny everything else |

**Outbound defaults:**
| Priority | Name | Action |
|----------|------|--------|
| 65000 | AllowVNetOutBound | Allow VNet → VNet |
| 65001 | AllowInternetOutBound | Allow → Internet |
| 65500 | DenyAllOutBound | Deny everything else |

### NSG Example — Three-Tier Architecture

```
NSG: nsg-web (attached to snet-web)
├── Inbound Rules:
│   ├── Priority 100: Allow TCP 443 from Internet → snet-web
│   ├── Priority 110: Allow TCP 80 from Internet → snet-web
│   └── Priority 65500: Deny All (default)
└── Outbound Rules:
    ├── Priority 100: Allow TCP 8080 to snet-app
    └── Priority 65500: Deny All (default — overridden by VNet allow)

NSG: nsg-app (attached to snet-app)
├── Inbound Rules:
│   ├── Priority 100: Allow TCP 8080 from snet-web → snet-app
│   ├── Priority 200: Deny All from Internet → snet-app
│   └── Priority 65500: Deny All (default)
└── Outbound Rules:
    ├── Priority 100: Allow TCP 5432 to snet-db
    ├── Priority 110: Allow TCP 6379 to snet-db
    └── Priority 65500: Deny All (default)

NSG: nsg-db (attached to snet-db)
├── Inbound Rules:
│   ├── Priority 100: Allow TCP 5432 from snet-app → snet-db
│   ├── Priority 110: Allow TCP 6379 from snet-app → snet-db
│   ├── Priority 200: Deny All from Internet → snet-db
│   └── Priority 65500: Deny All (default)
└── Outbound Rules:
    └── Deny All from Internet (default)
```

### NSG Attachment
- Can attach to a **subnet** (all resources in subnet)
- Can attach to a **NIC** (specific VM/resource)
- If both: rules from both are evaluated (most restrictive wins)

### Service Tags ⭐⭐
Pre-defined IP address groups managed by Microsoft:

| Service Tag | Represents |
|-------------|-----------|
| Internet | All public IPs |
| VirtualNetwork | VNet + peered VNets + on-prem |
| AzureLoadBalancer | Azure health probes |
| Storage | Azure Storage IPs |
| Sql | Azure SQL IPs |
| AzureKeyVault | Key Vault IPs |
| AzureContainerRegistry | ACR IPs |

---

## NAT Gateway ⭐⭐

Provides outbound internet connectivity for resources in private subnets (no public IP needed on each resource).

```
Private Subnet (no public IPs)
├── VM-1 (10.0.2.4)
├── VM-2 (10.0.2.5)
└── VM-3 (10.0.2.6)
    │
    ▼
NAT Gateway (with Public IP: 20.x.x.x)
    │
    ▼
Internet (all outbound traffic appears from 20.x.x.x)
```

---

## VNet Peering ⭐⭐⭐

Connect two VNets so resources communicate via private IPs.

```
VNet A: vnet-app (10.0.0.0/16)         VNet B: vnet-shared (10.1.0.0/16)
├── Spring Boot services                ├── Shared databases
├── AKS cluster                         ├── Key Vault
│                                       ├── Monitoring
│         VNet Peering                  │
└────────── ←──────────────────────────→┘
         (Private IP communication)
         (Non-transitive by default)
```

**Key properties:**
- Traffic stays on Microsoft backbone (not over internet)
- Low latency, high bandwidth
- Non-transitive: If A peers with B, and B peers with C, A cannot reach C (unless A also peers with C)
- Can peer across regions (Global VNet Peering)
- Can peer across subscriptions/tenants

---

## Private Endpoints ⭐⭐⭐

Bring Azure PaaS services (Storage, SQL, Key Vault, etc.) into your VNet with a private IP.

### Without Private Endpoint
```
VNet
└── App Service → (Public Internet) → Azure Storage (public endpoint)
    Problem: Traffic goes over internet, public exposure
```

### With Private Endpoint ⭐⭐⭐
```
VNet (10.0.0.0/16)
├── Subnet: snet-app (10.0.1.0/24)
│   └── App Service (VNet-integrated)
│       │
│       ▼ (private traffic, stays in VNet)
│
├── Subnet: snet-private-endpoints (10.0.2.0/24)
│   └── Private Endpoint: pe-storage
│       └── Private IP: 10.0.2.4
│           │
│           ▼ (maps to)
│
└── Azure Storage Account (public access disabled)
    └── Only accessible via Private Endpoint

Private DNS Zone:
mystorage.blob.core.windows.net → 10.0.2.4 (private IP)
```

**Benefits:**
- No public internet exposure
- Traffic stays on Microsoft backbone
- Consistent private IP for PaaS services
- Can disable public access entirely

---

## Typical Production Network Architecture ⭐⭐⭐

```
Internet
    │
    ▼
Azure Front Door / CDN (global load balancing, WAF)
    │
    ▼
VNet: vnet-prod (10.0.0.0/16)
    │
    ├── Subnet: snet-appgw (10.0.0.0/24)
    │   └── Application Gateway + WAF
    │       │
    │       ▼
    ├── Subnet: snet-aks (10.0.1.0/22) — /22 for AKS (1021 IPs)
    │   └── AKS Node Pool
    │       ├── Pod: order-service
    │       ├── Pod: payment-service
    │       └── Pod: user-service
    │           │
    │           ▼ (Private Endpoints)
    ├── Subnet: snet-pe (10.0.8.0/24)
    │   ├── PE → Azure PostgreSQL
    │   ├── PE → Azure Cache for Redis
    │   ├── PE → Key Vault
    │   ├── PE → Storage Account
    │   └── PE → Service Bus
    │
    ├── Subnet: snet-mgmt (10.0.9.0/24)
    │   └── Jump Box / Bastion
    │
    └── Subnet: AzureBastionSubnet (10.0.10.0/26)
        └── Azure Bastion
```

---

## Interview Questions

### Q: What is a VNet and how is it different from AWS VPC?
**A:** A VNet is Azure's virtual network for resource isolation and communication. Key differences from VPC:
- VNet subnets are private by default (no "public subnet" concept — use NAT Gateway or public IPs)
- VNet has no Internet Gateway concept — outbound is allowed by default for VMs with public IPs
- NSG is the equivalent of Security Groups (but can attach to subnets or NICs)
- Azure doesn't have NACLs — NSGs serve both purposes
- VNet address space can be expanded after creation

### Q: How do you secure a database from internet access?
**A:**
1. Deploy database with Private Endpoint (gets private IP in VNet)
2. Disable public access on the database
3. Apply NSG on the database subnet — allow only application subnet
4. Use Private DNS Zone for name resolution
5. Use Managed Identity for authentication (no password over network)

### Q: Explain NSG rule evaluation.
**A:** Rules are evaluated by priority (lowest number = highest priority):
1. Each rule has priority 100-4096
2. Rules evaluated in order of priority for each direction (inbound/outbound)
3. First matching rule wins (rest are ignored)
4. Default deny-all rule exists at priority 65500
5. If NSG is on both subnet and NIC, both are evaluated (inbound: subnet NSG first, then NIC NSG)

### Q: What is the difference between Private Endpoint and Service Endpoint?
**A:**
- **Private Endpoint**: Creates a private IP in your VNet for the PaaS service. Traffic goes through your VNet. Most secure — can disable public access entirely.
- **Service Endpoint**: Optimizes routing to PaaS service (stays on Azure backbone) but the service still uses its public IP. Less secure than Private Endpoint.
- **Recommendation**: Use Private Endpoints for production workloads.

### Q: How would you design networking for a microservices architecture on AKS?
**A:**
1. Single VNet with multiple subnets
2. Dedicated subnet for AKS (/22 or larger for pod IPs with Azure CNI)
3. Dedicated subnet for private endpoints
4. Application Gateway subnet for ingress + WAF
5. NSG rules: only App Gateway can reach AKS, only AKS can reach databases
6. All PaaS services via Private Endpoints (no public access)
7. Private DNS zones for endpoint resolution
8. NAT Gateway for outbound internet (pulling images, external APIs)
