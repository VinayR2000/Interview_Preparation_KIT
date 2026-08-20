# Microsoft Entra ID (Identity & Access Management)

## Theory

### What is Microsoft Entra ID?
Formerly Azure Active Directory (Azure AD). Microsoft's cloud-based identity and access management service. It is the **single identity platform** for Azure, Microsoft 365, and thousands of SaaS applications.

**Key difference from AWS IAM**: In AWS, IAM is a service within AWS. In Azure, Entra ID is the **foundation** — everything in Azure authenticates through Entra ID.

### Core Concepts

| Concept | Description |
|---------|-------------|
| Tenant | An instance of Entra ID representing an organization |
| User | An identity (person or application) in the tenant |
| Group | Collection of users for managing permissions |
| Service Principal | Identity for an application/service |
| Managed Identity | Azure-managed service principal (no credential management) |
| App Registration | Registering an application to use Entra ID for auth |
| Role | Set of permissions (Reader, Contributor, Owner, etc.) |
| RBAC | Role-Based Access Control |

---

## Internal Working

### Tenant ⭐⭐⭐
- One organization = one tenant (typically)
- Every Azure subscription is associated with one Entra ID tenant
- The tenant is the trust boundary for identity
- Has a unique tenant ID (GUID)

```
Entra ID Tenant: contoso.onmicrosoft.com
├── Users
│   ├── john@contoso.com
│   ├── jane@contoso.com
│   └── admin@contoso.com
├── Groups
│   ├── Developers
│   ├── DevOps-Team
│   └── Database-Admins
├── Service Principals
│   ├── sp-spring-boot-app
│   └── sp-terraform-pipeline
├── Managed Identities
│   ├── mi-aks-cluster
│   └── mi-app-service
├── App Registrations
│   ├── ecommerce-api
│   └── admin-portal
└── Enterprise Applications
    ├── GitHub
    └── Jira
```

### Users

#### Cloud-only users
- Created directly in Entra ID
- Identity exists only in the cloud

#### Synced users (Hybrid)
- Synced from on-premises Active Directory using Entra Connect
- Identity exists on-prem, synced to cloud

#### Guest users (B2B)
- External users invited to collaborate
- Use their own organization's credentials

### Groups ⭐⭐⭐

| Type | Description |
|------|-------------|
| Security Group | Used for access control (RBAC, resource access) |
| Microsoft 365 Group | Collaboration (shared mailbox, SharePoint, Teams) |

Assignment types:
- **Assigned**: Manually add/remove members
- **Dynamic**: Membership based on user attributes (e.g., department = "Engineering")

```
Group: Backend-Developers (Security Group, Dynamic)
├── Rule: user.department -eq "Backend Engineering"
├── Members (auto-populated):
│   ├── john@contoso.com
│   ├── jane@contoso.com
│   └── mike@contoso.com
└── RBAC Assignments:
    ├── Contributor on rg-backend-dev
    └── Reader on rg-backend-prod
```

---

## Service Principal vs Managed Identity ⭐⭐⭐

### Service Principal
- An identity for an application/service/automation tool
- Created when you register an app in Entra ID
- **You manage the credentials** (client secret or certificate)
- Credentials can expire → operational burden

```
App Registration: terraform-deployer
    │
    ▼
Service Principal (in Entra ID)
    │
    ├── Client ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
    ├── Client Secret: ******************************** (expires!)
    │
    ▼
RBAC Assignment: Contributor on Subscription
```

### Managed Identity ⭐⭐⭐
- Azure automatically manages the credentials
- **No secrets to rotate, store, or manage**
- Only works for Azure resources (can't use from outside Azure)
- **Always prefer Managed Identity over Service Principal** for Azure-hosted workloads

Two types:

| Type | Description | Use Case |
|------|-------------|----------|
| System-assigned | Tied to one resource. Deleted when resource is deleted. | Single-purpose (one App Service accessing one Key Vault) |
| User-assigned | Independent resource. Can be shared across multiple resources. | Shared identity (multiple VMs accessing same storage) |

```
Spring Boot on App Service
    │
    ├── System-assigned Managed Identity (auto-created)
    │   └── No credentials in code or config!
    │
    ▼
Azure Key Vault → DB password, API keys
Azure Storage → File uploads
Azure Service Bus → Message sending
```

**Interview Gold**: "Managed Identity eliminates the need to store credentials in application configuration. Azure handles token issuance and rotation automatically. The application uses the Azure SDK's DefaultAzureCredential which picks up the managed identity transparently."

---

## RBAC (Role-Based Access Control) ⭐⭐⭐

### How RBAC Works

```
Security Principal (Who?)     +     Role (What?)     +     Scope (Where?)
        │                              │                        │
        ▼                              ▼                        ▼
User / Group /              Reader / Contributor /      Management Group /
Service Principal /         Owner / Custom Role         Subscription /
Managed Identity                                       Resource Group /
                                                       Resource
```

### Built-in Roles

| Role | Permissions |
|------|-------------|
| Owner | Full access + can assign roles to others |
| Contributor | Full access EXCEPT cannot assign roles |
| Reader | View-only access |
| User Access Administrator | Manage user access (assign roles) |

Plus hundreds of service-specific roles:
- Virtual Machine Contributor
- Storage Blob Data Contributor
- AKS Cluster Admin
- Key Vault Secrets User
- etc.

### Scope Hierarchy (RBAC Inheritance) ⭐⭐⭐

```
Management Group (role assigned here)
    │
    ├── Subscription (inherits)
    │   ├── Resource Group (inherits)
    │   │   ├── Resource (inherits)
    │   │   └── Resource (inherits)
    │   └── Resource Group (inherits)
    │       └── Resource (inherits)
    └── Subscription (inherits)
```

**Key principle**: Roles assigned at a higher scope are inherited by all lower scopes.

Example:
```
User: jane@contoso.com
Role: Contributor
Scope: Subscription "Production"
Result: Jane is Contributor on ALL resource groups and resources in that subscription
```

### RBAC Assignment Example

```
Role Assignment:
├── Principal: Group "Backend-Developers"
├── Role: Contributor
├── Scope: Resource Group "rg-backend-dev"
│
└── Result: All members of Backend-Developers can
    create/modify/delete resources in rg-backend-dev
    but CANNOT assign roles to others
```

---

## Authentication vs Authorization

| Concept | Question | Azure Mechanism |
|---------|----------|-----------------|
| Authentication (AuthN) | "Who are you?" | Entra ID (tokens, MFA, passwords) |
| Authorization (AuthZ) | "What can you do?" | RBAC (roles + scopes) |

### Authentication Flow

```
User/App
    │
    ▼ (credentials / managed identity)
Microsoft Entra ID
    │
    ▼ (validates identity)
Access Token (JWT)
    │
    ▼ (token presented to Azure)
Azure Resource Manager
    │
    ▼ (checks RBAC)
Resource (allowed/denied)
```

---

## Conditional Access ⭐⭐

Policies that enforce access requirements based on signals:

```
IF (Signal/Condition)          THEN (Access Control)
├── User/Group                 ├── Allow access
├── Location (IP/country)      ├── Block access
├── Device state               ├── Require MFA
├── Application                ├── Require compliant device
├── Risk level                 ├── Require password change
└── Sign-in risk               └── Require specific auth method
```

Example:
```
Policy: "Require MFA for Azure Portal"
├── Assignments:
│   ├── Users: All users
│   ├── Cloud apps: Azure Management
│   └── Conditions: Any location
└── Access controls:
    └── Grant: Require multi-factor authentication
```

---

## App Registration ⭐⭐

When your Spring Boot application needs to authenticate users via Entra ID:

```
1. Register app in Entra ID
    │
    ▼
2. App Registration created
   ├── Application (client) ID
   ├── Directory (tenant) ID
   └── Client secret or certificate
    │
    ▼
3. Configure Spring Boot (spring-cloud-azure-starter-active-directory)
    │
    ▼
4. Users authenticate via Entra ID → Token → App validates token
```

### OAuth 2.0 / OpenID Connect with Entra ID

```
Browser
    │
    ▼ (1. Login request)
Spring Boot App
    │
    ▼ (2. Redirect to Entra ID)
Microsoft Entra ID
    │
    ▼ (3. User authenticates + MFA)
    │
    ▼ (4. Authorization code returned)
Spring Boot App
    │
    ▼ (5. Exchange code for tokens)
Microsoft Entra ID
    │
    ▼ (6. ID Token + Access Token)
Spring Boot App
    │
    ▼ (7. Validate token, create session)
User authenticated ✓
```

---

## Managed Identity + Spring Boot ⭐⭐⭐

### The Problem (Without Managed Identity)
```
application.properties:
spring.datasource.username=admin
spring.datasource.password=SuperSecret123!  ← SECURITY RISK!
azure.keyvault.client-secret=xxxx           ← MORE SECRETS!
```

### The Solution (With Managed Identity)
```
Spring Boot on App Service / AKS
    │
    ├── Uses DefaultAzureCredential (Azure SDK)
    │   └── Automatically detects Managed Identity
    │
    ▼
Azure Key Vault
    │
    ▼
Retrieves secrets at runtime (no secrets in code/config!)
```

```java
// No credentials needed in code!
@Configuration
public class AzureConfig {
    
    // Azure SDK automatically uses Managed Identity
    // when running on Azure (App Service, AKS, VM, etc.)
    
    @Bean
    public SecretClient secretClient() {
        return new SecretClientBuilder()
            .vaultUrl("https://my-keyvault.vault.azure.net/")
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
    }
}
```

---

## Interview Questions

### Q: What is Microsoft Entra ID and how is it different from AWS IAM?
**A:** Entra ID (formerly Azure AD) is Microsoft's cloud identity platform. Key differences:
- **Scope**: Entra ID is a full identity provider (users, groups, MFA, Conditional Access, B2B, B2C). AWS IAM is primarily for AWS resource access control.
- **Centrality**: Entra ID is the foundation — ALL Azure services authenticate through it. It also integrates with Microsoft 365 and thousands of SaaS apps.
- **Protocol**: Entra ID uses OAuth 2.0 / OpenID Connect / SAML. AWS IAM uses its own signature-based approach.
- **Hybrid**: Entra ID syncs with on-premises Active Directory, making it strong for enterprise hybrid scenarios.

### Q: Service Principal vs Managed Identity — when to use which?
**A:**
- **Managed Identity**: Use for any Azure-hosted workload (App Service, AKS, VM, Functions). No secrets to manage. Azure handles credential rotation.
- **Service Principal**: Use for external systems (GitHub Actions, on-prem servers, third-party tools) that can't use Managed Identity because they don't run on Azure.

**Rule**: If running on Azure → Managed Identity. If running outside Azure → Service Principal.

### Q: Explain RBAC with a real example.
**A:** RBAC = Who (principal) + What (role) + Where (scope).

Example: "The Backend-Developers group has Contributor role on the rg-backend-dev resource group."
- Who: Backend-Developers group
- What: Contributor (can create, modify, delete resources but cannot assign roles)
- Where: rg-backend-dev resource group (and everything inside it)

Roles are inherited downward: Management Group → Subscription → Resource Group → Resource.

### Q: How does a Spring Boot app on Azure access Key Vault without storing secrets?
**A:**
1. Enable Managed Identity on the App Service (or AKS pod via Workload Identity)
2. Grant the Managed Identity "Key Vault Secrets User" role on the Key Vault
3. In Spring Boot, use `DefaultAzureCredential` from the Azure SDK
4. The SDK automatically detects the Managed Identity and requests a token from Entra ID
5. The token is used to access Key Vault — no secrets anywhere in code or config

### Q: What is Conditional Access?
**A:** Conditional Access policies are "if-then" rules that enforce security requirements based on signals. For example: "If a user is accessing the Azure Portal from an untrusted location, then require MFA." Signals include user identity, location, device state, application being accessed, and risk level.

### Q: System-assigned vs User-assigned Managed Identity?
**A:**
- **System-assigned**: One-to-one with the resource. Created and deleted with the resource. Best for single-resource scenarios.
- **User-assigned**: Independent Azure resource. Can be assigned to multiple resources. Best when multiple resources need the same identity (e.g., multiple VMs accessing the same storage account).
