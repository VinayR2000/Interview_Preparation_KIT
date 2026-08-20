# Azure Hybrid Networking

## Theory

### What is Hybrid Networking?
Connecting your Azure VNet to on-premises data centers, other clouds, or remote users. Enables hybrid cloud architectures.

### Options

| Service | Description | Use Case |
|---------|-------------|----------|
| VPN Gateway | Encrypted tunnel over internet | Small/medium on-prem connectivity |
| ExpressRoute | Dedicated private connection | Large enterprise, high bandwidth, low latency |
| Private Link | Private access to Azure PaaS from anywhere | Secure PaaS access without public internet |

---

## Internal Working

### VPN Gateway ⭐⭐

```
On-Premises Data Center                    Azure
┌─────────────────────────┐    IPsec/IKE    ┌──────────────────┐
│                         │    Tunnel        │                  │
│ Corporate Network       │ ◄─────────────► │  VNet            │
│ (10.1.0.0/16)          │    (over internet)│  (10.0.0.0/16)  │
│                         │                  │                  │
│ ┌─────────────────────┐ │                  │ ┌──────────────┐ │
│ │ VPN Device / Router │ │                  │ │ VPN Gateway  │ │
│ └─────────────────────┘ │                  │ └──────────────┘ │
└─────────────────────────┘                  └──────────────────┘
```

#### Types:
- **Site-to-Site (S2S)**: Connect on-prem network to Azure VNet (always-on tunnel)
- **Point-to-Site (P2S)**: Connect individual laptop/device to Azure VNet (remote work)
- **VNet-to-VNet**: Connect two Azure VNets via VPN Gateway

### ExpressRoute ⭐⭐

```
On-Premises                    Partner Edge         Microsoft Edge        Azure
┌───────────────┐    Private    ┌──────────┐    Private    ┌────────┐    ┌───────┐
│ Corporate DC  │ ◄──────────► │ Telecom  │ ◄──────────► │ MSEE   │ ◄─►│ VNet  │
│               │   Connection  │ Provider │   Connection  │ Router │    │       │
└───────────────┘    (Fiber)    └──────────┘    (Fiber)    └────────┘    └───────┘

Benefits:
├── Private connection (no internet)
├── Higher bandwidth (up to 100 Gbps)
├── Lower latency (predictable)
├── Higher reliability (SLA)
└── Required for some compliance scenarios
```

### Private Link ⭐⭐⭐

Access Azure PaaS services (Storage, SQL, Key Vault) over a private connection:

```
On-Premises → ExpressRoute/VPN → Azure VNet → Private Endpoint → Azure PaaS
                                                    │
                                                    └── Private IP (10.0.x.x)
                                                        No public internet exposure!
```

---

## Interview Questions

### Q: VPN Gateway vs ExpressRoute — when to use which?
**A:**
- **VPN Gateway**: Cost-effective, quick to set up, sufficient for most workloads. Traffic encrypted over public internet. Bandwidth limited (~1.25 Gbps max).
- **ExpressRoute**: Dedicated private connection (no internet). Required for: high bandwidth (>1 Gbps), consistent low latency, compliance requirements (data must not traverse public internet), or very high data transfer volumes.

Most companies start with VPN Gateway and move to ExpressRoute as requirements grow.

### Q: How does a developer connect to Azure resources from their laptop?
**A:** Point-to-Site VPN:
1. VPN Gateway configured with P2S
2. Developer installs Azure VPN client
3. Authenticates via Entra ID (certificate or RADIUS)
4. Gets a private IP from VNet address space
5. Can access Azure resources via private IPs (as if on the VNet)
6. Alternative: Azure Bastion for SSH/RDP to VMs (no VPN needed)
