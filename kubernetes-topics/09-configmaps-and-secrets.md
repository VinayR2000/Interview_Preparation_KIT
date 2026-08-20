# 9. ConfigMap & Secrets ⭐⭐⭐

---

## Theory

**ConfigMaps** and **Secrets** externalize configuration from container images, making applications portable and easier to manage across environments.

### ConfigMap

Stores non-sensitive configuration data as key-value pairs:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  # Simple key-value
  database_url: "jdbc:postgresql://db:5432/mydb"
  log_level: "INFO"
  max_connections: "100"
  
  # File-like keys
  application.properties: |
    spring.datasource.url=jdbc:postgresql://db:5432/mydb
    spring.jpa.show-sql=false
    server.port=8080
```

### Secret

Stores sensitive data (passwords, tokens, certificates) encoded in base64:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
data:
  username: YWRtaW4=            # echo -n "admin" | base64
  password: cGFzc3dvcmQxMjM=    # echo -n "password123" | base64

# Or use stringData (auto-encodes):
apiVersion: v1
kind: Secret
metadata:
  name: db-secret
type: Opaque
stringData:
  username: admin
  password: password123
```

### Environment Variables

```yaml
# From ConfigMap
spec:
  containers:
  - name: app
    image: my-app:1.0
    env:
    - name: DB_URL
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: database_url
    - name: LOG_LEVEL
      valueFrom:
        configMapKeyRef:
          name: app-config
          key: log_level

    # From Secret
    - name: DB_PASSWORD
      valueFrom:
        secretKeyRef:
          name: db-secret
          key: password

    # All keys from ConfigMap as env vars
    envFrom:
    - configMapRef:
        name: app-config
    - secretRef:
        name: db-secret
```

### Volume Mount

```yaml
spec:
  containers:
  - name: app
    image: my-app:1.0
    volumeMounts:
    - name: config-volume
      mountPath: /app/config
      readOnly: true
    - name: secret-volume
      mountPath: /app/secrets
      readOnly: true
  volumes:
  - name: config-volume
    configMap:
      name: app-config
      items:                    # Optional: select specific keys
      - key: application.properties
        path: application.properties
  - name: secret-volume
    secret:
      secretName: db-secret
      defaultMode: 0400        # Read-only for owner
```

```
Volume mount behavior:
  ConfigMap keys → files in mounted directory
  
  /app/config/
  ├── database_url          (content: jdbc:postgresql://db:5432/mydb)
  ├── log_level             (content: INFO)
  └── application.properties (content: full file content)
  
  Auto-update: When ConfigMap is updated, mounted files are
  eventually updated (kubelet sync period, ~1 minute)
  
  Note: Env vars are NOT auto-updated (require pod restart)
```

### Secret Types

```
┌────────────────────────────────┬──────────────────────────────────┐
│ Type                           │ Use Case                          │
├────────────────────────────────┼──────────────────────────────────┤
│ Opaque                         │ Generic key-value (default)       │
│ kubernetes.io/tls              │ TLS certificates (tls.crt, key)  │
│ kubernetes.io/dockerconfigjson │ Docker registry credentials       │
│ kubernetes.io/basic-auth       │ Username/password                 │
│ kubernetes.io/ssh-auth         │ SSH private key                   │
│ kubernetes.io/token            │ Bootstrap token                   │
└────────────────────────────────┴──────────────────────────────────┘
```

```yaml
# TLS Secret
apiVersion: v1
kind: Secret
metadata:
  name: tls-secret
type: kubernetes.io/tls
data:
  tls.crt: <base64-cert>
  tls.key: <base64-key>

# Docker Registry Secret
kubectl create secret docker-registry my-registry \
  --docker-server=registry.example.com \
  --docker-username=user \
  --docker-password=pass
```

### Immutable ConfigMaps

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config-v2
immutable: true               # Cannot be modified after creation
data:
  log_level: "INFO"
```

```
Benefits of immutable ConfigMaps/Secrets:
  - Prevents accidental changes
  - Improved performance (kubelet doesn't watch for changes)
  - Forces versioned config (app-config-v1, app-config-v2)
  - Must delete and recreate to change
```

### Immutable Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-secret-v3
immutable: true
type: Opaque
data:
  password: bmV3cGFzc3dvcmQ=
```

### External Secrets

```
Problem: K8s Secrets are just base64 encoded (not truly secure)
Solution: External secret management

External Secrets Operator:
  - Syncs secrets from external stores to K8s Secrets
  - Supports: AWS Secrets Manager, HashiCorp Vault, Azure Key Vault, GCP Secret Manager

┌────────────────┐     ┌─────────────────────┐     ┌──────────────┐
│ AWS Secrets    │ ←── │ External Secrets     │ ──→ │ K8s Secret   │
│ Manager        │     │ Operator             │     │ (auto-synced)│
└────────────────┘     └─────────────────────┘     └──────────────┘
```

```yaml
# ExternalSecret resource
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: db-credentials
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: db-secret
  data:
  - secretKey: password
    remoteRef:
      key: production/database
      property: password
```

### Kubernetes Secret Security

```
Default Secret Security:
  - Base64 encoded (NOT encrypted by default)
  - Stored in etcd (plain text unless encryption configured)
  - Anyone with API access can read them
  - Visible in Pod env vars (via /proc)

Hardening Secrets:
  1. Enable encryption at rest (EncryptionConfiguration)
  2. Use RBAC to restrict Secret access
  3. Use external secret managers (Vault, AWS SM)
  4. Avoid mounting as env vars (prefer volumes)
  5. Enable audit logging for Secret access
  6. Use short-lived tokens where possible

Encryption at Rest:
apiVersion: apiserver.config.k8s.io/v1
kind: EncryptionConfiguration
resources:
- resources:
  - secrets
  providers:
  - aescbc:
      keys:
      - name: key1
        secret: <base64-encoded-32-byte-key>
  - identity: {}
```

---

## Internal Working

```
ConfigMap/Secret Consumption:

1. Environment Variables:
   - kubelet reads ConfigMap/Secret at Pod creation
   - Injects as env vars into container
   - Static — NOT updated if ConfigMap changes
   - Pod restart required for updates

2. Volume Mount:
   - kubelet mounts ConfigMap/Secret as files
   - Uses symlinks for atomic updates
   - kubelet periodically syncs (default ~60s)
   - Files updated automatically (no pod restart)
   - Application must re-read files to pick up changes

Volume Mount Structure:
  /app/config/
  ├── ..data → ..2024_01_15_10_30 (symlink to timestamped dir)
  ├── ..2024_01_15_10_30/
  │   ├── database_url
  │   └── log_level
  ├── database_url → ..data/database_url (symlink)
  └── log_level → ..data/log_level (symlink)

Update: kubelet creates new timestamped dir, swaps ..data symlink
```

---

## Diagram

```
┌────────────────── CONFIGMAP & SECRET USAGE ──────────────────┐
│                                                                │
│  ┌──────────────┐                     ┌────────────┐         │
│  │  ConfigMap   │                     │   Secret   │         │
│  │              │                     │            │         │
│  │ db_url: ... │                     │ pass: ***  │         │
│  │ log: INFO   │                     │ token: *** │         │
│  └──────┬───────┘                     └──────┬─────┘         │
│         │                                     │               │
│         ├──── env vars ────┐    ┌── env vars ─┤               │
│         │                  │    │             │               │
│         ├──── volume ──┐   │    │  ┌─ volume ─┤               │
│         │              │   │    │  │          │               │
│  ┌──────┴──────────────┴───┴────┴──┴──────────┴─────────┐    │
│  │                         POD                            │    │
│  │                                                        │    │
│  │  Container:                                           │    │
│  │    ENV:                                               │    │
│  │      DB_URL = jdbc:postgresql://db:5432/mydb          │    │
│  │      DB_PASSWORD = *** (from secret)                  │    │
│  │                                                        │    │
│  │    Mounted Files:                                     │    │
│  │      /app/config/application.properties               │    │
│  │      /app/secrets/password                            │    │
│  └────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────┘
```

---

## Code

### Complete Example — Spring Boot App with ConfigMap and Secret:

```yaml
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: order-service-config
  namespace: production
data:
  SPRING_PROFILES_ACTIVE: "production"
  SERVER_PORT: "8080"
  LOGGING_LEVEL_ROOT: "INFO"
  application.yml: |
    spring:
      datasource:
        url: jdbc:postgresql://postgres-svc:5432/orders
        hikari:
          maximum-pool-size: 20
      kafka:
        bootstrap-servers: kafka-0.kafka:9092,kafka-1.kafka:9092

---
# Secret
apiVersion: v1
kind: Secret
metadata:
  name: order-service-secret
  namespace: production
type: Opaque
stringData:
  DB_USERNAME: order_user
  DB_PASSWORD: super-secret-password
  KAFKA_API_KEY: kafka-key-12345

---
# Deployment using both
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: production
spec:
  replicas: 3
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
      - name: order-service
        image: registry/order-service:2.0.0
        ports:
        - containerPort: 8080
        
        # Env from ConfigMap
        envFrom:
        - configMapRef:
            name: order-service-config
        
        # Env from Secret
        env:
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: order-service-secret
              key: DB_USERNAME
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: order-service-secret
              key: DB_PASSWORD
        
        # Volume mount for config file
        volumeMounts:
        - name: config
          mountPath: /app/config
          readOnly: true
      
      volumes:
      - name: config
        configMap:
          name: order-service-config
          items:
          - key: application.yml
            path: application.yml
```

---

## Interview Questions

### Q1: What is the difference between ConfigMap and Secret?

**A:**
- **ConfigMap:** Non-sensitive configuration (URLs, log levels, feature flags). Stored as plain text. No special handling.
- **Secret:** Sensitive data (passwords, tokens, keys). Base64 encoded. Can enable encryption at rest. Size limit 1MB. Accessed via RBAC.

Both can be consumed as env vars or volume mounts. Both are namespace-scoped.

### Q2: How do you update configuration without restarting pods?

**A:** Use volume mounts instead of env vars. When a ConfigMap is updated, kubelet automatically updates the mounted files (within ~60 seconds via sync period). The application must then detect file changes and reload. For Spring Boot, use `spring-cloud-kubernetes` or watch the config directory. Env vars require pod restart.

### Q3: Are Kubernetes Secrets really secure?

**A:** By default, no — they're just base64 encoded (not encrypted). To secure them:
1. Enable encryption at rest in etcd (EncryptionConfiguration)
2. Use RBAC to restrict who can read secrets
3. Use External Secrets Operator with AWS Secrets Manager/Vault
4. Enable audit logging for secret access
5. Mount as files (not env vars, which appear in /proc)
6. Use short-lived tokens

### Q4: What happens when a ConfigMap is updated?

**A:**
- **Volume mounts:** Files are updated automatically by kubelet (eventually, ~60s). Uses symlink swapping for atomic updates.
- **Env vars:** NOT updated. Requires pod restart (delete pods or rolling update with annotation change).
- **Immutable ConfigMaps:** Cannot be updated at all — must create new version.

### Q5: What is the External Secrets Operator?

**A:** External Secrets Operator syncs secrets from external secret management systems (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault) into Kubernetes Secrets. Benefits: centralized secret management, automatic rotation, audit trail, no secrets in YAML files/Git. It watches ExternalSecret custom resources and creates/updates K8s Secrets automatically.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Committing Secrets to Git | Credentials exposed | Use External Secrets, sealed-secrets |
| Using env vars for frequently changing config | Requires pod restart | Use volume mounts |
| Not setting RBAC for Secrets | Anyone can read secrets | Restrict with Role/RoleBinding |
| Large ConfigMaps (>1MB) | Exceeds size limit | Use PVC or external config |
| Not encoding Secret data in base64 | Creation fails | Use stringData or base64 encode |

---

## Best Practices

1. **Never commit secrets to Git** — use External Secrets or sealed-secrets
2. **Use volume mounts for dynamic config** — auto-updates without restart
3. **Enable encryption at rest** for Secrets in etcd
4. **Use immutable ConfigMaps** for stable config (better performance)
5. **Namespace your configs** — separate per environment
6. **Use descriptive naming** — include version or environment in name
7. **Set RBAC** — restrict Secret read access
8. **Prefer file mounts over env vars for secrets** — less exposure risk

---

## Related Topics

- [19. RBAC & Security](./19-rbac-and-security.md)
- [20. YAML](./20-yaml.md)
- [35. Kubernetes + Spring Boot](./35-kubernetes-spring-boot.md)
- [04. Pods](./04-pods.md)
