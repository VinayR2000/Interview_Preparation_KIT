# 9. Docker Registry ⭐⭐

---

## Theory

A Docker registry stores and distributes container images. It's the central repository for sharing images between development, CI/CD, and production environments.

### Docker Hub

```
Docker Hub: Default public registry (hub.docker.com)
  - Public images: nginx, postgres, redis, node, etc.
  - Official images: Verified, maintained
  - Rate limits: 100 pulls/6h anonymous, 200 pulls/6h authenticated
  - Free tier: 1 private repository
  - Image reference: docker.io/library/nginx:1.25

docker pull nginx:1.25  → pulls from docker.io/library/nginx:1.25
docker pull myuser/app  → pulls from docker.io/myuser/app
```

### Private Registry

```
Private registries:
  - AWS ECR (Elastic Container Registry)
  - Google GCR / Artifact Registry
  - Azure ACR (Azure Container Registry)
  - GitHub Container Registry (ghcr.io)
  - GitLab Container Registry
  - Self-hosted (Harbor, Nexus, Docker Registry)

Benefits:
  - No rate limits
  - Access control (IAM, tokens)
  - Vulnerability scanning
  - Geographic proximity (faster pulls)
  - Compliance (data residency)
```

### Repository

```
Registry → Repository → Image:Tag

registry.example.com / order-service : 2.1.0
│                      │               │
registry               repository      tag

Repository = collection of related images (different versions)
  registry.example.com/order-service:1.0
  registry.example.com/order-service:2.0
  registry.example.com/order-service:latest
```

### Image Tag

```
Tag = mutable label pointing to an image

Tagging conventions:
  my-app:2.1.0           Semantic version
  my-app:2.1.0-alpine    Version + variant
  my-app:abc123f         Git SHA (immutable reference)
  my-app:latest          Latest build (MUTABLE — avoid in prod!)
  my-app:main-abc123     Branch + SHA

Tags are mutable:
  my-app:latest today → image A
  my-app:latest tomorrow → image B (different!)
  
Use digests for true immutability: my-app@sha256:abc...
```

### docker login

```bash
# Docker Hub
docker login
# Enter username and password

# Private registry
docker login registry.example.com

# AWS ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789.dkr.ecr.us-east-1.amazonaws.com

# Credentials stored in ~/.docker/config.json
```

### docker push

```bash
# Tag for registry
docker tag my-app:1.0 registry.example.com/my-app:1.0

# Push to registry
docker push registry.example.com/my-app:1.0

# Push all tags
docker push registry.example.com/my-app --all-tags
```

### docker pull

```bash
# Pull from Docker Hub
docker pull nginx:1.25

# Pull from private registry
docker pull 123456789.dkr.ecr.us-east-1.amazonaws.com/my-app:2.1.0

# Pull by digest (immutable)
docker pull my-app@sha256:abc123def456...

# Pull specific platform
docker pull --platform linux/amd64 my-app:1.0
```

### Image Digest

```
Digest: Content-addressable, immutable identifier

docker pull my-app:1.0
# 1.0: Pulling from library/my-app
# Digest: sha256:abc123def456789...

Digest never changes for same content.
Even if tag is overwritten, digest identifies exact image.

Use in production for guaranteed reproducibility:
  image: registry.example.com/my-app@sha256:abc123def456...
```

### AWS ECR

```
AWS ECR features:
  - Private by default
  - IAM-based access control
  - Image scanning (on push or scheduled)
  - Lifecycle policies (auto-delete old images)
  - Cross-region replication
  - Immutable tags (optional)
  - Encryption at rest (KMS)

Setup:
  1. Create repository: aws ecr create-repository --repository-name my-app
  2. Authenticate: aws ecr get-login-password | docker login...
  3. Push: docker push <account>.dkr.ecr.<region>.amazonaws.com/my-app:tag

ECR Lifecycle Policy:
  - Delete untagged images after 1 day
  - Keep only last 10 tagged images
  - Delete images older than 90 days
```

### Registry Authentication

```
Authentication methods:
  1. Username/password (Docker Hub, basic auth)
  2. Token-based (GitHub, GitLab)
  3. IAM (AWS ECR — temporary credentials)
  4. Service Account (Google GCR)
  5. Docker credential helpers (ecr-login, gcloud)

For Kubernetes:
  - imagePullSecrets (manual credentials)
  - IRSA/Workload Identity (cloud-native, preferred)
  - Node-level credentials (all pods on node can pull)
```

---

## Interview Questions

### Q1: What is the difference between a registry, repository, and tag?

**A:** Registry is the server hosting images (Docker Hub, ECR). Repository is a collection of related images within a registry (e.g., `my-app`). Tag is a version label within a repository (e.g., `2.1.0`). Full reference: `registry/repository:tag`.

### Q2: Why should you avoid using :latest in production?

**A:** `:latest` is mutable — it can point to different images over time. Problems: can't reproduce deployments, can't rollback (which "latest" was it?), forces `imagePullPolicy: Always` (slower), different nodes might have different versions cached. Use specific version tags or digests instead.

### Q3: How does AWS ECR authentication work?

**A:** ECR uses temporary tokens via IAM. `aws ecr get-login-password` generates a 12-hour token. In Kubernetes with EKS, nodes with the proper IAM role can pull without any imagePullSecrets. For cross-account or non-EKS, use imagePullSecrets with the ECR token (requires refresh mechanism).

---

## Best Practices

1. **Use private registries** for production images
2. **Pin image tags** — never use `:latest` in production
3. **Use image digests** for critical workloads
4. **Enable vulnerability scanning** — scan on push
5. **Set lifecycle policies** — auto-delete old images
6. **Use IAM/IRSA** — not static credentials
7. **Enable immutable tags** — prevent accidental overwrite
8. **Replicate cross-region** — for multi-region deployments

---

## Related Topics

- [04. Docker Images](./04-docker-images.md)
- [08. Docker Build](./08-docker-build.md)
- [25. Docker + CI/CD](./25-docker-cicd.md)
- [27. Docker + AWS](./27-docker-aws.md)
