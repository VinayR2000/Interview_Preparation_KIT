# Azure Key Vault

## Theory

### What is Azure Key Vault?
A centralized cloud service for securely storing and managing secrets, encryption keys, and certificates. Equivalent to AWS Secrets Manager + AWS KMS combined.

### What Does Key Vault Store?

| Type | Description | Example |
|------|-------------|---------|
| Secrets | Arbitrary string values | Database passwords, API keys, connection strings |
| Keys | Cryptographic keys (RSA, EC) | Encryption/decryption, signing |
| Certificates | X.509 certificates | TLS/SSL certificates, client certificates |

---

## Internal Working

### Key Vault Architecture

```
Applications / Services
    │
    ├── Spring Boot (via Managed Identity)
    ├── Azure Functions
    ├── AKS Pods (via Workload Identity)
    ├── Azure DevOps Pipelines
    │
    ▼
Microsoft Entra ID (Authentication)
    │
    ▼ (Access Token)
Azure Key Vault
    │
    ├── Secrets
    │   ├── db-password = "P@ssw0rd123"
    │   ├── redis-connection = "redis://..."
    │   └── api-key = "sk-..."
    │
    ├── Keys
    │   ├── encryption-key (RSA 2048)
    │   └── signing-key (EC P-256)
    │
    └── Certificates
        ├── api.contoso.com (TLS cert)
        └── client-auth-cert
```

### Access Control — Two Layers ⭐⭐⭐

**Layer 1: Management Plane (ARM)**
- Who can create/delete/modify the Key Vault itself
- Controlled via standard Azure RBAC
- Example: "Contributor" role on the Key Vault resource

**Layer 2: Data Plane (Secrets/Keys/Certificates)**
- Who can read/write/delete the actual secrets
- Controlled via Key Vault RBAC roles OR Access Policies

```
Management Plane (RBAC)          Data Plane (RBAC or Access Policies)
├── Create Key Vault             ├── Get Secret
├── Delete Key Vault             ├── Set Secret
├── Modify settings              ├── Delete Secret
├── View properties              ├── Get Key
└── Manage access policies       ├── Create Key
                                 ├── Get Certificate
                                 └── Import Certificate
```

### Key Vault RBAC Roles (Recommended)

| Role | Permissions |
|------|-------------|
| Key Vault Administrator | Full access to secrets, keys, certificates |
| Key Vault Secrets User | Read secrets only |
| Key Vault Secrets Officer | Read/write/delete secrets |
| Key Vault Crypto User | Use keys for encrypt/decrypt |
| Key Vault Certificates Officer | Manage certificates |
| Key Vault Reader | Read metadata (not secret values) |

---

## Secret Rotation ⭐⭐

### Manual Rotation
```
1. Generate new secret value
2. Update secret in Key Vault (creates new version)
3. Applications automatically pick up new version (if using latest)
4. Old version still accessible if needed
```

### Automatic Rotation
```
Event Grid
    │
    ▼ (SecretNearExpiry event)
Azure Function
    │
    ▼ (generates new credential)
Target Service (e.g., regenerate DB password)
    │
    ▼ (stores new value)
Key Vault (new secret version)
    │
    ▼ (applications use latest)
Spring Boot App
```

### Secret Versioning
```
Secret: db-password
├── Version 1: "OldPassword123" (disabled)
├── Version 2: "BetterPassword456" (disabled)
└── Version 3: "CurrentPassword789" (enabled, current)

Application requests "db-password" → Gets Version 3 (latest enabled)
```

---

## Spring Boot + Key Vault Integration ⭐⭐⭐

### Architecture

```
Spring Boot (App Service / AKS)
    │
    ├── Managed Identity (System-assigned)
    │
    ▼ (Authenticate via Entra ID)
Azure Key Vault
    │
    ├── spring-datasource-password → "DbPassword123"
    ├── spring-redis-password → "RedisPassword456"
    └── external-api-key → "sk-abc123"
    │
    ▼ (Secrets loaded as Spring properties)
Spring Boot Application
    │
    ├── spring.datasource.password = "DbPassword123"
    ├── spring.redis.password = "RedisPassword456"
    └── external.api.key = "sk-abc123"
```

### Configuration (spring-cloud-azure-starter-keyvault-secrets)

```yaml
# application.yml
spring:
  cloud:
    azure:
      keyvault:
        secret:
          property-sources:
            - endpoint: https://my-app-kv.vault.azure.net/
          # No credentials needed! Managed Identity handles it.
```

### Secret Naming Convention
Key Vault secret names use hyphens, Spring properties use dots:
```
Key Vault secret name:        Spring property:
spring-datasource-url    →    spring.datasource.url
spring-datasource-password →  spring.datasource.password
app-external-api-key     →    app.external.api-key
```

### Java Code — Accessing Secrets Directly

```java
@Service
public class SecretService {

    private final SecretClient secretClient;

    public SecretService(SecretClient secretClient) {
        this.secretClient = secretClient;
    }

    public String getDatabasePassword() {
        KeyVaultSecret secret = secretClient.getSecret("db-password");
        return secret.getValue();
    }
}
```

---

## Key Vault Networking ⭐⭐

### Public Access (default)
- Key Vault accessible from internet
- Protected by authentication (Entra ID tokens)
- Can restrict to specific IP ranges

### Private Endpoint (recommended for production) ⭐⭐⭐
```
VNet
├── Subnet: app-subnet
│   └── Spring Boot (App Service / AKS)
│
├── Subnet: private-endpoints
│   └── Private Endpoint → Key Vault
│       └── Private IP: 10.0.2.4
│
└── Private DNS Zone
    └── my-kv.vault.azure.net → 10.0.2.4

Key Vault: Public access DISABLED
Only accessible via Private Endpoint within VNet
```

---

## Key Vault Best Practices ⭐⭐⭐

| Practice | Reason |
|----------|--------|
| Use Managed Identity to access Key Vault | No secrets to manage for accessing secrets |
| Enable soft-delete and purge protection | Prevent accidental permanent deletion |
| Use Private Endpoints | No public internet exposure |
| Enable logging (diagnostic settings) | Audit who accessed what |
| Use separate Key Vaults per environment | Dev secrets ≠ Prod secrets |
| Grant least privilege | Use "Secrets User" not "Administrator" |
| Enable secret expiration | Force rotation |
| Use RBAC (not Access Policies) | More granular, consistent with Azure RBAC |

---

## Interview Questions

### Q: What is Azure Key Vault and why use it?
**A:** Key Vault is a centralized service for managing secrets, encryption keys, and certificates. Benefits:
1. **Centralization**: All secrets in one place, not scattered across config files
2. **Security**: Secrets encrypted at rest, access controlled via RBAC
3. **Audit**: Full logging of who accessed what secret and when
4. **Rotation**: Supports versioning and automatic rotation
5. **No secrets in code**: Applications use Managed Identity to access Key Vault

### Q: How does a Spring Boot app on AKS access Key Vault secrets?
**A:** 
1. AKS uses Workload Identity (pod-level Managed Identity)
2. Grant the identity "Key Vault Secrets User" role on the Key Vault
3. Use `spring-cloud-azure-starter-keyvault-secrets` dependency
4. Configure only the Key Vault endpoint in application.yml
5. Secrets are loaded as Spring properties automatically at startup
6. No credentials stored anywhere in code, config, or environment variables

### Q: How do you handle secret rotation without downtime?
**A:**
1. Key Vault supports versioning — update secret creates a new version
2. Applications requesting "latest" automatically get the new version
3. For database passwords:
   - Generate new password
   - Update database with new password
   - Update Key Vault secret
   - Applications pick up new value on next connection/restart
4. For zero-downtime: use connection pooling with retry logic, or have a brief period where both old and new credentials are valid

### Q: Key Vault vs storing secrets in environment variables?
**A:**
- Environment variables: visible in Azure Portal, logged in diagnostics, shared with child processes, no audit trail, no rotation support
- Key Vault: encrypted at rest, RBAC-controlled, full audit logging, versioning, rotation support, centralized across services
- For production: always Key Vault. Environment variables are acceptable only for non-sensitive configuration.

### Q: What is the difference between Secrets, Keys, and Certificates in Key Vault?
**A:**
- **Secrets**: Arbitrary string values (passwords, connection strings, API keys). Application reads the value directly.
- **Keys**: Cryptographic keys managed by Azure. Used for encrypt/decrypt/sign operations. The key material never leaves Key Vault (HSM-backed option available).
- **Certificates**: X.509 certificates with lifecycle management. Key Vault can auto-renew from supported CAs. Stores both the certificate and private key.
