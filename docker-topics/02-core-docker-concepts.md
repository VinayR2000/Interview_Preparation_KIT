# 2. Core Docker Concepts ⭐⭐⭐

---

## Theory

Docker's ecosystem revolves around a set of core concepts that work together to build, ship, and run containerized applications. Understanding these building blocks is essential before diving deeper into any specific area.

### Image

```
Docker Image: Read-only template used to create containers

An image is a lightweight, standalone, executable package that includes:
  - Application code
  - Runtime (JRE, Node.js, Python)
  - Libraries and dependencies
  - Environment variables
  - Configuration files

Key characteristics:
  - Immutable (read-only)
  - Layered filesystem (Union FS)
  - Shareable (registry)
  - Versioned (tags)

Analogy:
  Image = Class (blueprint)
  Container = Object (instance)
```

### Container

```
Docker Container: Running instance of an image

Container = Image + Read-Write Layer + Isolation (namespaces + cgroups)

┌─────────────────────────────┐
│  Container (running)        │
│  ┌───────────────────────┐  │
│  │  Read-Write Layer     │  │  ← Container-specific changes
│  ├───────────────────────┤  │
│  │  Image Layer (R/O)    │  │  ← Shared, immutable
│  │  Image Layer (R/O)    │  │
│  │  Image Layer (R/O)    │  │
│  │  Base Image Layer     │  │
│  └───────────────────────┘  │
│  + PID namespace             │
│  + NET namespace             │
│  + MNT namespace             │
│  + Cgroups (resource limits) │
└─────────────────────────────┘

Container provides:
  - Process isolation (namespaces)
  - Resource control (cgroups)
  - Filesystem isolation (overlay)
  - Network isolation (veth pairs)
```

### Dockerfile

```
Dockerfile: Text file with instructions to build a Docker image

Each instruction creates a layer in the image:

┌─────────────────────────────────────────┐
│  Dockerfile           Image Layers      │
│                                         │
│  FROM ubuntu:22.04    → Base layer      │
│  RUN apt-get update   → Layer 2        │
│  COPY app.jar /app/   → Layer 3        │
│  EXPOSE 8080          → Metadata        │
│  CMD ["java","-jar"]  → Metadata        │
└─────────────────────────────────────────┘

Example:
```

```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/myapp.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Registry

```
Docker Registry: Server-side application that stores and distributes Docker images

Types:
  ┌────────────────────────────────────────────────────┐
  │  Public Registries        Private Registries       │
  ├────────────────────────────────────────────────────┤
  │  Docker Hub (default)     AWS ECR                  │
  │  GitHub Container Reg.    Google GCR / Artifact    │
  │  Quay.io                  Azure ACR                │
  │                           Harbor (self-hosted)     │
  │                           Nexus, JFrog Artifactory │
  └────────────────────────────────────────────────────┘

Registry operations:
  docker push  → Upload image to registry
  docker pull  → Download image from registry
  docker login → Authenticate to registry
```

### Repository

```
Repository: Collection of related images (same name, different tags)

Registry > Repository > Tag

Example:
  Registry:   docker.io
  Repository: library/nginx
  Tags:       1.25, 1.25-alpine, latest, stable

Full image reference:
  registry/repository:tag
  docker.io/library/nginx:1.25
  
  Short form (Docker Hub default):
  nginx:1.25  →  docker.io/library/nginx:1.25

Repository naming:
  Official:   library/nginx (or just nginx)
  User:       username/myapp
  Private:    registry.example.com/team/myapp
```

### Tag

```
Tag: Label that points to a specific image version within a repository

nginx:1.25        → Specific version
nginx:1.25-alpine → Alpine variant of 1.25
nginx:latest      → Most recent (mutable!)
myapp:v2.1.0      → Semantic versioning
myapp:abc123f     → Git commit hash

Tag rules:
  - Tags are mutable (can be reassigned)
  - :latest is NOT always the latest (it's just a tag name)
  - Multiple tags can point to same image
  - Untagged images = "dangling" images

Best practice:
  ✗ myapp:latest        → Unpredictable
  ✓ myapp:v2.1.0        → Immutable version
  ✓ myapp:20240115-abc  → Date + commit
```

### Layer

```
Image Layer: Single read-only filesystem change in an image

Each Dockerfile instruction that modifies filesystem = new layer

FROM ubuntu:22.04      → Layer 1 (base)
RUN apt-get install    → Layer 2 (+50MB)
COPY app.jar /app/     → Layer 3 (+30MB)
RUN chmod +x /app/     → Layer 4 (+0MB, metadata)

Layer characteristics:
  - Read-only once created
  - Content-addressable (SHA256 hash)
  - Shared across images (deduplication)
  - Cached for fast rebuilds
  - Union filesystem (OverlayFS)

Layer sharing example:
  Image A: [ubuntu] [apt-get] [app-a.jar]
  Image B: [ubuntu] [apt-get] [app-b.jar]
           ↑ shared layers = stored once on disk
```

### Volume

```
Docker Volume: Persistent storage mechanism that exists outside container lifecycle

Container filesystem = ephemeral (lost when container removed)
Volume = persistent (survives container removal)

Types:
  ┌─────────────────────────────────────────────────────────┐
  │  Type          │ Managed by │ Location                   │
  ├─────────────────────────────────────────────────────────┤
  │  Named Volume  │ Docker     │ /var/lib/docker/volumes/   │
  │  Bind Mount    │ User       │ Any host path              │
  │  tmpfs         │ Kernel     │ Memory only (not persisted)│
  └─────────────────────────────────────────────────────────┘

Usage:
  docker run -v mydata:/app/data         # Named volume
  docker run -v /host/path:/container    # Bind mount
  docker run --tmpfs /tmp                # tmpfs
```

### Network

```
Docker Network: Enables communication between containers and external world

Default networks:
  ┌──────────────────────────────────────────────────────────┐
  │  Network    │ Description                                 │
  ├──────────────────────────────────────────────────────────┤
  │  bridge     │ Default. Containers on same host communicate│
  │  host       │ Container shares host network stack         │
  │  none       │ No network. Complete isolation              │
  │  overlay    │ Multi-host networking (Swarm/K8s)          │
  └──────────────────────────────────────────────────────────┘

Container communication:
  Same network:      Container name as hostname (DNS)
  Different network: Must connect to same network
  External access:   Port mapping (-p 8080:80)
```

### Container Runtime

```
Container Runtime: Software responsible for running containers

Hierarchy:
  ┌────────────────────────────────────────────────────┐
  │  Docker Engine (high-level)                        │
  │    └── containerd (container lifecycle manager)    │
  │          └── runc (low-level OCI runtime)         │
  │                └── Linux kernel                    │
  │                    (namespaces + cgroups)          │
  └────────────────────────────────────────────────────┘

containerd:
  - Manages container lifecycle (create, start, stop)
  - Image management (pull, push, storage)
  - Used by Docker AND Kubernetes

runc:
  - Reference implementation of OCI Runtime Spec
  - Creates namespaces, cgroups
  - Actually starts the container process
  - Exits after container starts (not a daemon)

OCI (Open Container Initiative):
  - Runtime Specification (how to run containers)
  - Image Specification (how to package images)
```

---

## Concept Relationships

```
┌─────────────────────────────────────────────────────────────────┐
│                    DOCKER CONCEPT MAP                             │
│                                                                   │
│  Dockerfile ──build──→ Image ──run──→ Container                  │
│                          │                 │                      │
│                        push/pull         mount                    │
│                          │                 │                      │
│                       Registry          Volume                    │
│                          │                                        │
│                      Repository                                   │
│                          │                                        │
│                        Tag:version                                │
│                                                                   │
│  Image = Stack of Layers (read-only)                             │
│  Container = Image + R/W Layer + Namespaces + Cgroups            │
│  Container communicates via Network                              │
│  Container Runtime (containerd → runc) runs containers           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between a Docker image and a container?

**A:** An image is a read-only template (blueprint) containing application code, runtime, libraries, and configuration. A container is a running instance of an image — it adds a writable layer on top and provides process isolation via Linux namespaces and resource control via cgroups. You can create multiple containers from the same image, each with independent state.

### Q2: What are Docker image layers and why are they important?

**A:** Each Dockerfile instruction that modifies the filesystem creates a new layer. Layers are:
- **Read-only:** Once created, never modified
- **Content-addressable:** Identified by SHA256 hash
- **Shared:** Multiple images reuse common layers (saves disk and bandwidth)
- **Cached:** Docker skips rebuilding unchanged layers (fast builds)

This layered architecture enables efficient storage, fast builds, and minimal network transfer.

### Q3: What is the difference between a named volume and a bind mount?

**A:**
- **Named Volume:** Managed by Docker, stored in `/var/lib/docker/volumes/`, portable, best for persistent data that should survive container lifecycle.
- **Bind Mount:** Maps a specific host path into the container, managed by the user, useful for development (live code reload) but tied to host filesystem structure.

Named volumes are preferred for production; bind mounts for development.

### Q4: Explain Docker networking — how do containers communicate?

**A:** Docker provides multiple network drivers:
- **bridge** (default): Containers on the same bridge network communicate via container name DNS. Isolated from host.
- **host:** Container shares host's network stack directly (no NAT, no port mapping needed).
- **none:** No network connectivity.
- **overlay:** Multi-host networking for Swarm/Kubernetes clusters.

Containers on the same user-defined bridge network can resolve each other by name. Port mapping (`-p`) exposes container ports to the host.

### Q5: What is a container runtime and what is the relationship between Docker, containerd, and runc?

**A:**
- **Docker Engine:** High-level tooling (CLI, API, image build, networking, volumes)
- **containerd:** Mid-level runtime that manages container lifecycle, image storage, and execution. Used by both Docker and Kubernetes.
- **runc:** Low-level OCI runtime that actually creates Linux namespaces, sets up cgroups, and starts the container process. It exits after the container is running.

Docker Engine delegates to containerd, which delegates to runc for actual container creation.

### Q6: What happens to data when a container is removed?

**A:** The container's writable layer (any files created or modified inside the container) is deleted when the container is removed. Data stored in volumes (named volumes or bind mounts) persists independently of the container lifecycle. This is why volumes are essential for databases, logs, or any data that must survive container restarts or removal.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Storing data in container layer | Data lost on container removal | Use volumes for persistent data |
| Using `latest` tag in production | Unpredictable deployments | Pin specific versions (e.g., `nginx:1.25.3`) |
| Confusing image and container | Wrong mental model | Image = blueprint, Container = running instance |
| Not understanding layer caching | Slow builds, large images | Order Dockerfile instructions from least to most changing |
| Using default bridge network | No DNS resolution by name | Create custom bridge networks |
| Thinking containers are VMs | Over-provisioning, wrong patterns | Containers are isolated processes |

---

## Best Practices

1. **One process per container** — separate concerns, independent scaling
2. **Use named volumes for persistent data** — don't rely on container filesystem
3. **Pin image versions** — reproducible deployments (`nginx:1.25.3` not `nginx:latest`)
4. **Use custom networks** — enables DNS-based container discovery
5. **Keep images small** — fewer layers, smaller base images, multi-stage builds
6. **Use .dockerignore** — exclude unnecessary files from build context
7. **Don't run as root** — use USER instruction for security
8. **Tag images meaningfully** — semantic versions or git commit hashes

---

## Related Topics

- [01. Docker Fundamentals](./01-docker-fundamentals.md)
- [03. Docker Architecture](./03-docker-architecture.md)
- [04. Docker Images](./04-docker-images.md)
- [05. Docker Containers](./05-docker-containers.md)
- [06. Dockerfile](./06-dockerfile.md)
- [10. Docker Networking](./10-docker-networking.md)
- [11. Docker Volumes](./11-docker-volumes.md)
