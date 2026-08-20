# 26. Container Runtime

---

## Theory

The container runtime is responsible for running containers on each node. Kubernetes uses the Container Runtime Interface (CRI) to interact with different runtimes.

### Docker

```
Docker history with Kubernetes:
  - K8s originally built for Docker
  - K8s 1.20: Dockershim deprecated
  - K8s 1.24: Dockershim removed
  
  Docker used to be: kubelet → dockershim → Docker → containerd
  Now:               kubelet → CRI → containerd (directly)

Docker for development: Still fine for building images
Docker in K8s: No longer supported as runtime
Existing Docker images: Work perfectly with containerd (same OCI format)
```

### containerd

```
containerd: Industry-standard container runtime

Features:
  - Lightweight, performant
  - Native CRI support (no shim needed)
  - Image management (pull, push, store)
  - Container lifecycle (create, start, stop, delete)
  - Snapshot-based storage
  - Namespace isolation

containerd was extracted from Docker:
  Docker = containerd + CLI + build + compose + swarm
  K8s only needs containerd (the runtime part)
```

### CRI (Container Runtime Interface)

```
CRI: gRPC-based API between kubelet and container runtime

kubelet ──CRI──→ containerd/CRI-O

CRI APIs:
  RuntimeService:
    - RunPodSandbox: Create pod network namespace
    - StopPodSandbox: Stop all containers in pod
    - RemovePodSandbox: Clean up pod
    - CreateContainer: Create container
    - StartContainer: Start container
    - StopContainer: Stop container
    - RemoveContainer: Remove container
    - ListContainers: List containers
    - ExecSync: Execute command synchronously
  
  ImageService:
    - PullImage: Pull container image
    - ListImages: List local images
    - RemoveImage: Remove local image
```

### OCI (Open Container Initiative)

```
OCI defines standards for containers:

1. Runtime Specification:
   - How to run a container (filesystem, environment, lifecycle)
   - Implemented by runc (default low-level runtime)

2. Image Specification:
   - Container image format (layers, manifest, config)
   - Ensures images work across any OCI-compliant runtime

3. Distribution Specification:
   - How to push/pull images from registries

Stack:
  kubelet → CRI → containerd → OCI runtime (runc) → container
```

### Image

```
Container image:
  - Immutable, layered filesystem
  - Contains application code + dependencies + runtime
  - Built from Dockerfile
  - Stored in registries (Docker Hub, ECR, GCR)

Image structure:
  Base layer:    OS filesystem (alpine, debian, distroless)
  App layer:     Application code, dependencies
  Config:        Entry point, env vars, ports, user

Image reference:
  registry.example.com/order-service:2.1.0
  │                    │              │
  registry             repository     tag
```

### Container

```
Container vs Image:
  Image:     Static template (blueprint)
  Container: Running instance of an image

Container is:
  - Isolated process (namespaces: pid, net, mnt, uts, ipc)
  - Resource-limited (cgroups: CPU, memory)
  - Filesystem from image (copy-on-write layer)
  - NOT a VM (shares host kernel)
```

### Image Registry

```
Registries:
  Public: Docker Hub, GitHub Container Registry
  Cloud:  AWS ECR, Google GCR, Azure ACR
  Self-hosted: Harbor, Nexus

Image pull flow:
  1. kubelet needs to start container
  2. Checks if image exists locally
  3. If not (or imagePullPolicy requires): pull from registry
  4. Authenticate with registry (imagePullSecrets)
  5. Download layers (parallel, deduplicated)
  6. Store locally on node
```

### Image Pulling

```yaml
spec:
  containers:
  - name: app
    image: registry.example.com/my-app:2.1.0
    imagePullPolicy: IfNotPresent

# imagePullPolicy:
#   Always:        Pull every time (for :latest or mutable tags)
#   IfNotPresent:  Pull only if not on node (default for versioned tags)
#   Never:         Never pull (must be pre-loaded on node)

# For private registries:
  imagePullSecrets:
  - name: registry-credentials
```

---

## Interview Questions

### Q1: Why was Docker removed from Kubernetes?

**A:** Docker was removed as a direct runtime because it was unnecessary overhead. K8s only needs a CRI-compliant runtime. Docker's stack: kubelet → dockershim → Docker daemon → containerd → runc. With containerd directly: kubelet → CRI → containerd → runc. Simpler, fewer layers, better performance. Docker images still work (they're OCI-compliant).

### Q2: What is the difference between containerd and Docker?

**A:** containerd is the container runtime (runs containers). Docker is a complete platform (build + run + compose + swarm + CLI). K8s only needs the "run" part. containerd was extracted from Docker. It's lighter, has native CRI support, and is the default K8s runtime.

### Q3: What is the difference between a container and a VM?

**A:**
- **VM:** Full OS with its own kernel. Hardware-level isolation. Heavy (GB). Minutes to start.
- **Container:** Process-level isolation using Linux namespaces/cgroups. Shares host kernel. Light (MB). Seconds to start.

Containers are less isolated than VMs but far more efficient for running applications.

---

## Best Practices

1. **Use containerd** or CRI-O as container runtime
2. **Use minimal base images** (distroless, alpine) — smaller attack surface
3. **Pin image tags** — never use `:latest` in production
4. **Scan images** for vulnerabilities (Trivy, Snyk)
5. **Use imagePullPolicy: IfNotPresent** for versioned tags
6. **Pre-pull critical images** on nodes for faster startup
7. **Use private registries** with authentication

---

## Related Topics

- [27. Container Registry](./27-container-registry.md)
- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
- [04. Pods](./04-pods.md)
