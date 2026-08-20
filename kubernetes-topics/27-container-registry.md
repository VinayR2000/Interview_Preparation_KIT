# 27. Container Registry

---

## Theory

A container registry stores and distributes container images. Kubernetes pulls images from registries to run containers.

### Docker Hub

```
Default public registry: hub.docker.com
  - Public images: nginx, postgres, redis, etc.
  - Rate limited for anonymous pulls (100/6h)
  - Free tier: 1 private repo

Image reference without registry defaults to Docker Hub:
  nginx:1.25 → docker.io/library/nginx:1.25
  myuser/myapp:v1 → docker.io/myuser/myapp:v1
```

### AWS ECR (Elastic Container Registry)

```
Private registry per AWS account:
  <account-id>.dkr.ecr.<region>.amazonaws.com/my-app:v1

Features:
  - Private by default
  - IAM-based authentication
  - Image scanning (vulnerabilities)
  - Cross-region replication
  - Lifecycle policies (auto-delete old images)
  - Immutable tags option

Authentication:
  aws ecr get-login-password | docker login --username AWS --password-stdin <ecr-url>
  
  In K8s: Use IRSA (IAM Roles for Service Accounts) — no imagePullSecrets needed!
```

### Image Tags

```
Tags: Human-readable references to image versions

Best practices:
  ✓ Semantic versioning: my-app:2.1.0
  ✓ Git SHA: my-app:abc123f
  ✓ Combined: my-app:2.1.0-abc123f
  ✗ :latest (mutable, unpredictable)

Tags are mutable by default:
  my-app:v1 can point to different images over time
  Use immutable tags or digests for guaranteed consistency
```

### Image Digest

```
Digest: Immutable, content-addressable reference

my-app@sha256:a3ed95caeb02...

Guaranteed to always reference the exact same image content.
Even if tag is overwritten, digest never changes.

Use in production for maximum reproducibility:
  image: registry.example.com/my-app@sha256:abc123...
```

### ImagePullPolicy

```yaml
# Always: Pull on every pod start (use for :latest)
imagePullPolicy: Always

# IfNotPresent: Pull only if not cached on node (default for versioned tags)
imagePullPolicy: IfNotPresent

# Never: Require image to be pre-loaded on node
imagePullPolicy: Never

# Defaults:
#   Tag = :latest → Always
#   Tag = specific version → IfNotPresent
```

### Private Registry

```yaml
# Create secret for private registry
kubectl create secret docker-registry my-registry-secret \
  --docker-server=registry.example.com \
  --docker-username=user \
  --docker-password=pass \
  --docker-email=user@example.com

# Use in pod spec
spec:
  imagePullSecrets:
  - name: my-registry-secret
  containers:
  - name: app
    image: registry.example.com/my-app:v1
```

### imagePullSecrets

```yaml
# Pod-level (per pod)
spec:
  imagePullSecrets:
  - name: ecr-credentials

# ServiceAccount-level (all pods using this SA)
apiVersion: v1
kind: ServiceAccount
metadata:
  name: my-app-sa
imagePullSecrets:
- name: ecr-credentials

# For EKS with ECR: Use IRSA instead (no secrets needed)
```

---

## Interview Questions

### Q1: How does Kubernetes pull images from a private registry?

**A:** Two methods:
1. **imagePullSecrets:** Create docker-registry Secret with credentials, reference in pod spec or ServiceAccount.
2. **Cloud-native auth (preferred):** In EKS, use IRSA — nodes have IAM permissions to pull from ECR without any Secret.

### Q2: What is the difference between image tag and digest?

**A:** Tags are mutable labels (`:v1`, `:latest`) — same tag can point to different content over time. Digests (`@sha256:...`) are immutable content-addressed hashes — guaranteed to reference the exact same image bytes forever. Use digests for production reproducibility.

### Q3: Why should you avoid using `:latest` in production?

**A:** `:latest` is:
- Mutable (changes without notice)
- Forces `imagePullPolicy: Always` (slower startup)
- Can't rollback (which "latest" was running?)
- Not reproducible across environments
- Can cause different pods to run different versions

---

## Best Practices

1. **Use specific version tags** — not `:latest`
2. **Consider image digests** for critical workloads
3. **Use IRSA/Workload Identity** instead of imagePullSecrets in cloud
4. **Enable image scanning** (Trivy, ECR scanning)
5. **Set lifecycle policies** — auto-delete old images
6. **Use image caching** — pre-pull on nodes for faster startup
7. **Enable immutable tags** — prevent accidental overwrite

---

## Related Topics

- [26. Container Runtime](./26-container-runtime.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
- [34. Kubernetes + CI/CD](./34-kubernetes-cicd.md)
