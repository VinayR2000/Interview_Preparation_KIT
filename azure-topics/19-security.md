# Azure Security

## Theory

### Azure Security — Defense in Depth ⭐⭐⭐

```
Layer 1: Physical Security (Microsoft manages)
    │
Layer 2: Identity & Access (Entra ID, RBAC, Conditional Access)
    │
Layer 3: Perimeter (DDoS Protection, Azure Firewall, WAF)
    │
Layer 4: Network (VNet, NSG, Private Endpoints, Network Segmentation)
    │
Layer 5: Compute (VM security, container security, patching)
    │
Layer 6: Application (Secure code, API security, secrets management)
    │
Layer 7: Data (Encryption at rest/transit, classification, backup)
```

---

## Internal Working

### Zero Trust Architecture ⭐⭐⭐

**Principle**: Never trust, always verify. Every access request is authenticated and authorized regardless of origin.

```
Zero Trust Pillars:
├── Verify explicitly
│   └── Always authenticate and authorize (Entra ID + RBAC)
├── Use least privilege
│   └── Just-In-Time (JIT), Just-Enough-Access (JEA)
└── Assume breach
    └── Segment access, encrypt, monitor everything
```

### Applied Zero Trust for Microservices:
```
Client Request
    │
    ▼
1. Entra ID authentication (JWT token)
2. APIM validates token + rate limits
3. Network: only APIM can reach AKS (NSG)
4. AKS: Network Policy restricts pod-to-pod
5. Pod: Workload Identity for service-to-service
6. Database: Managed Identity auth (no passwords)
7. All traffic encrypted (TLS)
8. All access logged (Monitor + Sentinel)
```

---

## Key Security Services

### Microsoft Entra ID (Identity Layer)
- Authentication (MFA, Conditional Access)
- Authorization (RBAC)
- Managed Identity (no credentials)
- See: `02-entra-id.md`

### Azure Key Vault (Secrets Layer)
- Centralized secret management
- Certificate management
- Encryption key management
- See: `03-key-vault.md`

### Network Security Group (Network Layer)
- Stateful firewall rules
- Subnet and NIC level
- Service tags for Azure services
- See: `04-virtual-network.md`

---

## Azure Firewall ⭐⭐

Managed, cloud-based network security service. Different from NSG — provides centralized network filtering.

```
NSG vs Azure Firewall:

NSG:
├── Layer 3/4 (IP + port)
├── Per subnet or NIC
├── Simple allow/deny
└── Free (included)

Azure Firewall:
├── Layer 3/4/7 (FQDN, URL filtering)
├── Centralized for entire VNet
├── Threat intelligence
├── TLS inspection
├── DNS proxy
└── Premium service ($$$)
```

### When to use Azure Firewall:
- Centralized egress control (restrict what your apps can access on internet)
- FQDN filtering (allow only specific domains)
- Compliance requirements
- Threat intelligence-based blocking
- Hub-spoke network architecture

---

## WAF (Web Application Firewall) ⭐⭐⭐

```
Internet → WAF (Application Gateway / Front Door) → Your Services

WAF Protects Against:
├── SQL Injection
├── Cross-Site Scripting (XSS)
├── Command Injection
├── Local/Remote File Inclusion
├── Protocol Attacks
├── Bots and Scanners
└── Custom rules (geo-blocking, rate limiting)
```

---

## Microsoft Defender for Cloud ⭐⭐

Security posture management + threat protection:

```
Defender for Cloud:
├── CSPM (Cloud Security Posture Management)
│   ├── Secure Score (0-100%)
│   ├── Recommendations
│   │   ├── "Enable MFA for admin accounts"
│   │   ├── "Enable encryption for storage accounts"
│   │   ├── "Restrict public network access to PostgreSQL"
│   │   └── "Enable vulnerability assessment on AKS"
│   └── Regulatory compliance (PCI DSS, HIPAA, etc.)
│
└── CWP (Cloud Workload Protection)
    ├── Defender for Servers (VM threat detection)
    ├── Defender for Containers (AKS security)
    │   ├── Image vulnerability scanning
    │   ├── Runtime protection
    │   └── Kubernetes audit log analysis
    ├── Defender for Databases (SQL/PostgreSQL)
    ├── Defender for Storage
    ├── Defender for Key Vault
    └── Defender for App Service
```

---

## Microsoft Sentinel ⭐

Cloud-native SIEM (Security Information and Event Management) + SOAR (Security Orchestration, Automation, and Response).

```
Data Sources                    Sentinel                    Response
├── Entra ID logs        ────►                      ────► Automated playbooks
├── Azure Activity logs  ────►  Analytics Rules            Alert notification
├── NSG flow logs        ────►  (detect threats)           Incident creation
├── App logs             ────►                             Auto-remediation
├── Firewall logs        ────►  Threat Intelligence
├── Defender alerts      ────►  Workbooks (dashboards)
└── Custom sources       ────►  Hunting queries
```

---

## Private Endpoints — Security Pattern ⭐⭐⭐

```
WITHOUT Private Endpoints:
App → (Public Internet) → PostgreSQL (public IP exposed)
Risk: Public internet exposure, potential interception

WITH Private Endpoints:
VNet (10.0.0.0/16)
├── App Subnet
│   └── Spring Boot App
│       │
│       ▼ (private IP, VNet-internal traffic only)
├── PE Subnet
│   ├── PE → PostgreSQL (10.0.4.5)
│   ├── PE → Redis (10.0.4.6)
│   ├── PE → Key Vault (10.0.4.7)
│   ├── PE → Storage (10.0.4.8)
│   └── PE → Service Bus (10.0.4.9)
│
└── All PaaS services: Public access = DISABLED

Result: No PaaS service is accessible from internet
All access is via private IP within VNet only
```

---

## Encryption ⭐⭐⭐

### At Rest
| Service | Default Encryption | Customer-Managed Keys |
|---------|-------------------|----------------------|
| Storage Account | AES-256 (Microsoft-managed) | Key Vault CMK |
| PostgreSQL | AES-256 (Microsoft-managed) | Key Vault CMK |
| Cosmos DB | AES-256 (Microsoft-managed) | Key Vault CMK |
| Managed Disks | AES-256 (Microsoft-managed) | Key Vault CMK / Disk Encryption Set |
| Key Vault | HSM-protected | N/A (it IS the key store) |

### In Transit
- TLS 1.2+ enforced for all Azure services
- HTTPS-only settings on Storage, App Service, etc.
- mTLS between services (optional, recommended for high-security)

---

## Security Best Practices for Microservices on Azure ⭐⭐⭐

```
1. Identity
   ├── Managed Identity for all Azure resource access
   ├── Workload Identity for AKS pods
   ├── Least privilege RBAC (not Owner/Contributor everywhere)
   └── Conditional Access for admin accounts

2. Secrets
   ├── All secrets in Key Vault (never in code/config)
   ├── Managed Identity to access Key Vault
   ├── Enable secret rotation
   └── Disable Key Vault public access (Private Endpoint)

3. Network
   ├── Private Endpoints for all PaaS services
   ├── NSG on every subnet
   ├── No public IPs on backend services
   ├── WAF on Application Gateway / Front Door
   └── Network Policies in AKS

4. Application
   ├── APIM for centralized auth + rate limiting
   ├── JWT validation at gateway level
   ├── Input validation in every service
   └── Dependency vulnerability scanning in CI/CD

5. Data
   ├── Encryption at rest (default + CMK for sensitive)
   ├── TLS in transit (HTTPS only)
   ├── Passwordless DB auth (Managed Identity)
   └── Data classification and retention policies

6. Monitoring
   ├── Azure Monitor + Application Insights
   ├── Defender for Cloud (security posture)
   ├── Activity Log monitoring
   ├── Key Vault access logging
   └── Sentinel for SIEM (advanced)
```

---

## Interview Questions

### Q: How do you secure microservices on Azure?
**A:** Defense in depth:
1. **Identity**: Managed Identity for service-to-service, Workload Identity for AKS pods, Entra ID for user authentication
2. **Network**: VNet with subnets + NSGs, Private Endpoints for all PaaS services, no public access to backends
3. **Gateway**: APIM for centralized JWT validation, rate limiting, IP filtering, WAF for OWASP protection
4. **Secrets**: All in Key Vault, accessed via Managed Identity, never in code/config
5. **Runtime**: Container vulnerability scanning, network policies in AKS, pod security standards
6. **Monitoring**: Defender for Cloud, audit logging, alerts on suspicious activity

### Q: Managed Identity vs Service Principal — security implications?
**A:**
- **Managed Identity**: No credentials to manage, rotate, or leak. Azure handles token lifecycle. Cannot be used outside Azure. Most secure for Azure-hosted workloads.
- **Service Principal**: Has a client secret or certificate that can expire, leak, or be stolen. Must be rotated manually. Necessary for external systems but higher risk.

**Security rule**: Use Managed Identity whenever the workload runs on Azure. Service Principals only for CI/CD pipelines and external systems.

### Q: What is NSG vs Azure Firewall vs WAF?
**A:**
- **NSG**: Basic L3/L4 firewall (IP + port rules). Per subnet/NIC. Free. Use for: subnet isolation, basic traffic filtering.
- **Azure Firewall**: Centralized L3/L4/L7 firewall. FQDN filtering, threat intelligence, TLS inspection. Use for: centralized egress control, compliance.
- **WAF**: L7 web application firewall. OWASP protection, custom rules. Use for: protecting web APIs from SQL injection, XSS, bots.

They complement each other — NSG for microsegmentation, WAF for application protection, Azure Firewall for centralized network control.

### Q: How do you implement Zero Trust for a Spring Boot + AKS architecture?
**A:**
1. Every request authenticated (Entra ID JWT at APIM/ingress)
2. Least privilege RBAC (service-specific roles, not Contributor)
3. Network segmentation (pods can only reach what they need — Network Policies)
4. No implicit trust between services (Workload Identity + token validation)
5. All PaaS access via Private Endpoints (no public internet paths)
6. Encrypt everywhere (TLS in transit, AES at rest)
7. Log everything (Application Insights, Activity Log, Defender)
8. Assume breach: monitor for anomalies, automated response with Sentinel
