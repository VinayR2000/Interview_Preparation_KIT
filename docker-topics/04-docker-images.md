# 4. Docker Images ⭐⭐⭐

---

## Theory

A Docker **image** is a read-only template containing application code, runtime, libraries, and configuration — everything needed to run a container.

### What is an Image?

```
Image = Blueprint for containers

Image properties:
  - Immutable (read-only, can't change after creation)
  - Layered (built from stack of layers)
  - Portable (runs same everywhere)
  - Shareable (push/pull from registries)

Image vs Container:
  Image:     Static template (like a class)
  Container: Running instance (like an object)
```

### Image Layers

```
Every Dockerfile instruction creates a new layer:

FROM ubuntu:22.04           ← Layer 1: Base OS (~78MB)
RUN apt-get update          ← Layer 2: Package cache (~30MB)
RUN apt-get install -y curl ← Layer 3: curl binary (~5MB)
COPY app.jar /app/          ← Layer 4: Application (~50MB)
CMD ["java", "-jar", "app.jar"]  ← Layer 5: Metadata (0B)

Layers are:
  - Cached (reused across builds)
  - Shared (across images using same base)
  - Stacked (union filesystem)
  - Read-only (container adds writable layer on top)
```

```
┌────────────────────────────────┐
│  Writable Container Layer      │ ← Per container (ephemeral)
├────────────────────────────────┤
│  Layer 4: COPY app.jar         │ ← Image layers (read-only)
├────────────────────────────────┤
│  Layer 3: RUN install curl     │
├────────────────────────────────┤
│  Layer 2: RUN apt-get update   │
├────────────────────────────────┤
│  Layer 1: FROM ubuntu:22.04    │
└────────────────────────────────┘
```

### Image ID

```
Image ID: SHA256 hash of image configuration

docker images
REPOSITORY   TAG      IMAGE ID       SIZE
my-app       v1       sha256:abc123  150MB
nginx        1.25     sha256:def456  187MB

Image ID uniquely identifies an image regardless of tag.
Same content = same ID (content-addressable storage).
```

### Image Tags

```
Tag: Human-readable label pointing to a specific image

Naming: registry/repository:tag
  docker.io/library/nginx:1.25
  123456.dkr.ecr.us-east-1.amazonaws.com/my-app:2.1.0
  my-app:latest

Tag conventions:
  latest:    Most recent (DON'T use in production)
  v2.1.0:    Semantic versioning (recommended)
  abc123f:   Git commit SHA (CI/CD builds)
  stable:    Tested version

Tags are MUTABLE — same tag can point to different images!
```

### Image Digest

```
Digest: Immutable content-addressed identifier

nginx@sha256:a3ed95caeb02ffe...

Unlike tags (mutable), digests NEVER change.
Guarantees exact same image content every time.

docker pull nginx@sha256:a3ed95caeb02ffe...
→ Always gets the exact same image, forever.

Use digests for: Production deployments where reproducibility matters.
```

### Base Image

```dockerfile
FROM ubuntu:22.04    # Base image (first layer)
FROM alpine:3.18     # Minimal base (5MB!)
FROM scratch         # Empty base (for static binaries)

Common base images:
  ubuntu:22.04       ~78MB   Full OS
  debian:bookworm    ~124MB  Full OS
  alpine:3.18        ~5MB    Minimal (musl libc)
  distroless/java    ~200MB  Minimal JRE (no shell!)
  eclipse-temurin    ~300MB  JDK for Java
```

### Parent Image

```
Parent Image vs Base Image:
  FROM ubuntu:22.04        ← Base image (first FROM)
  
  If someone builds:
  FROM ubuntu:22.04
  RUN apt-get install java
  → Tags as: my-java-base:v1

  Then:
  FROM my-java-base:v1     ← Parent image (built on base)
  COPY app.jar /app/

Base = root of the chain
Parent = immediate predecessor (may not be the base)
```

### Image Caching

```
Docker caches each layer — unchanged layers are reused:

Build 1:
  FROM node:20              ← Cached (already pulled)
  COPY package.json .       ← New layer built
  RUN npm install           ← New layer built (SLOW)
  COPY . .                  ← New layer built

Build 2 (only app code changed):
  FROM node:20              ← Cached ✓
  COPY package.json .       ← Cached ✓ (unchanged)
  RUN npm install           ← Cached ✓ (package.json unchanged)
  COPY . .                  ← Rebuilt (source code changed)

Cache invalidation: Any change invalidates ALL subsequent layers!
  → Put rarely-changing instructions FIRST (dependencies before code)
```

### Image History

```bash
docker history my-app:v1

IMAGE          CREATED       SIZE    COMMAND
sha256:abc123  1 hour ago    50MB    COPY app.jar /app/
sha256:def456  1 hour ago    5MB     RUN apt-get install curl
sha256:ghi789  2 weeks ago   78MB    /bin/sh -c #(nop) FROM ubuntu
```

### Image Size

```bash
docker images
docker image ls
docker system df          # Disk usage summary

# Reduce image size:
1. Use smaller base (alpine, distroless)
2. Multi-stage builds (only copy final artifacts)
3. Combine RUN commands (fewer layers)
4. Remove unnecessary files in same layer
5. Use .dockerignore
```

### Image Inspection

```bash
docker inspect nginx:1.25
docker inspect --format='{{.Config.Env}}' my-app:v1
docker inspect --format='{{.Config.ExposedPorts}}' my-app:v1

# Shows: layers, env vars, ports, entrypoint, volumes, labels, etc.
```

### Pull Image

```bash
docker pull nginx                   # Pulls nginx:latest
docker pull nginx:1.25              # Specific tag
docker pull nginx@sha256:abc123...  # Specific digest
docker pull registry.example.com/my-app:v1  # Private registry
```

### Push Image

```bash
docker login registry.example.com
docker tag my-app:v1 registry.example.com/my-app:v1
docker push registry.example.com/my-app:v1
```

### Delete Image

```bash
docker rmi nginx:1.25              # Remove by tag
docker rmi sha256:abc123           # Remove by ID
docker image prune                 # Remove unused images
docker image prune -a              # Remove ALL unused images
docker system prune -a             # Remove everything unused
```

---

## Interview Questions

### Q1: What are Docker image layers and why do they matter?

**A:** Each Dockerfile instruction creates a read-only layer. Layers are cached and shared — if two images share the same base, they reuse those layers on disk. This saves storage and speeds up builds (unchanged layers are cached). Layers stack using a union filesystem. Understanding layers is key to optimizing build time and image size.

### Q2: What is the difference between an image tag and a digest?

**A:**
- **Tag:** Mutable human-readable label (`:v1`, `:latest`). Same tag can point to different images over time (if overwritten).
- **Digest:** Immutable content-addressed hash (`@sha256:...`). Guaranteed to always reference exact same content. Use digests for production reproducibility.

### Q3: How do you optimize Docker image size?

**A:**
1. Use minimal base images (alpine ~5MB, distroless)
2. Multi-stage builds (build in one stage, copy only artifacts to final)
3. Combine RUN commands to reduce layers
4. Delete temp files in the same RUN instruction
5. Use `.dockerignore` to exclude unnecessary files from build context
6. Don't install unnecessary packages

### Q4: How does Docker build cache work?

**A:** Docker caches each layer. On rebuild, if an instruction and its inputs haven't changed, the cached layer is reused. Cache invalidation cascades — when one layer changes, all subsequent layers are rebuilt. Optimization: put rarely-changing instructions first (deps before code), use `COPY package.json` before `COPY .`.

---

## Best Practices

1. **Use specific tags** — never `:latest` in production
2. **Multi-stage builds** — separate build and runtime
3. **Minimize layers** — combine related RUN commands
4. **Order by change frequency** — stable deps first, volatile code last
5. **Use `.dockerignore`** — exclude node_modules, .git, etc.
6. **Scan for vulnerabilities** — Trivy, Snyk, Docker Scout
7. **Use distroless/alpine** — minimal attack surface
8. **Pin base image versions** — reproducible builds

---

## Related Topics

- [06. Dockerfile](./06-dockerfile.md)
- [08. Docker Build](./08-docker-build.md)
- [14. Multi-Stage Builds](./14-multi-stage-builds.md)
- [20. Docker Storage Internals](./20-docker-storage-internals.md)
