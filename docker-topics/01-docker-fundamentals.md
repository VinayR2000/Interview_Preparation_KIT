# 1. Docker Fundamentals ⭐⭐⭐

---

## Theory

**Docker** is an open-source platform for developing, shipping, and running applications in lightweight, isolated containers. It standardizes the packaging and deployment of software.

### What is Docker?

```
Docker is a containerization platform that packages applications
and their dependencies into portable, self-contained units (containers).

Docker guarantees:
  "If it works on my machine, it works everywhere"
  
  Developer laptop → CI/CD → Staging → Production
  Same container, same behavior everywhere.
```

### Why Docker?

| Problem | Docker Solution |
|---------|---------------|
| "Works on my machine" | Same environment everywhere (container) |
| Dependency conflicts | Isolated dependencies per container |
| Slow environment setup | `docker run` — seconds to start |
| Large VM overhead | Lightweight containers (~MB, not GB) |
| Inconsistent deployments | Immutable images, deterministic |
| Scaling difficulties | Fast startup, orchestration-ready |
| Resource waste | Shared kernel, efficient resource usage |

### Containerization

```
Containerization: Package application + dependencies into isolated unit

Traditional:
  App → installed on OS → shares libraries → conflicts possible

Containerized:
  App → packaged with own libs → isolated → no conflicts

Container includes:
  - Application code
  - Runtime (JRE, Node.js, Python)
  - Libraries and dependencies
  - Configuration files
  - Environment variables

Container does NOT include:
  - Operating system kernel (shares host kernel)
  - Hardware drivers
```

### Docker vs Virtual Machine

```
┌────────────────────────────────────────────────────────────┐
│         VM                        Container                 │
├────────────────────────────────────────────────────────────┤
│  ┌─────┐ ┌─────┐ ┌─────┐    ┌─────┐ ┌─────┐ ┌─────┐   │
│  │App A│ │App B│ │App C│    │App A│ │App B│ │App C│   │
│  ├─────┤ ├─────┤ ├─────┤    ├─────┤ ├─────┤ ├─────┤   │
│  │Bins │ │Bins │ │Bins │    │Bins │ │Bins │ │Bins │   │
│  ├─────┤ ├─────┤ ├─────┤    └──┬──┘ └──┬──┘ └──┬──┘   │
│  │Guest│ │Guest│ │Guest│       │       │       │        │
│  │ OS  │ │ OS  │ │ OS  │    ┌──┴───────┴───────┴──┐    │
│  └──┬──┘ └──┬──┘ └──┬──┘    │    Docker Engine    │    │
│  ┌──┴───────┴───────┴──┐    ├─────────────────────┤    │
│  │     Hypervisor       │    │      Host OS        │    │
│  ├──────────────────────┤    ├─────────────────────┤    │
│  │      Host OS         │    │     Hardware        │    │
│  ├──────────────────────┤    └─────────────────────┘    │
│  │     Hardware         │                                │
│  └──────────────────────┘                                │
└────────────────────────────────────────────────────────────┘

┌──────────────┬────────────────────┬────────────────────┐
│ Aspect       │ VM                 │ Container          │
├──────────────┼────────────────────┼────────────────────┤
│ Isolation    │ Full (own kernel)  │ Process-level      │
│ Startup      │ Minutes            │ Seconds            │
│ Size         │ GBs               │ MBs                │
│ Performance  │ ~5-10% overhead    │ Near-native        │
│ OS           │ Full guest OS      │ Shares host kernel │
│ Density      │ 10-20 per host     │ 100s per host      │
│ Security     │ Strong isolation   │ Good (namespace)   │
│ Portability  │ Hardware-dependent │ Highly portable    │
└──────────────┴────────────────────┴────────────────────┘
```

### Docker vs Kubernetes

```
Docker:      Build and RUN containers (single host)
Kubernetes:  ORCHESTRATE containers (multi-host, at scale)

Docker:      "Run this container on THIS machine"
Kubernetes:  "Run this container SOMEWHERE, keep it running, scale it"

They complement each other:
  Docker: Builds container images
  Kubernetes: Deploys and manages those containers in production
```

### Docker Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     DOCKER ARCHITECTURE                       │
│                                                              │
│  ┌──────────────┐        ┌────────────────────────────┐    │
│  │ Docker CLI   │──REST──│      Docker Daemon         │    │
│  │ (client)     │  API   │      (dockerd)             │    │
│  └──────────────┘        │                            │    │
│                           │  ┌──────────────────────┐ │    │
│                           │  │     containerd       │ │    │
│                           │  │  (container runtime) │ │    │
│                           │  └──────────┬───────────┘ │    │
│                           │             │             │    │
│                           │  ┌──────────▼───────────┐ │    │
│                           │  │        runc          │ │    │
│                           │  │   (OCI runtime)      │ │    │
│                           │  └──────────────────────┘ │    │
│                           └────────────────────────────┘    │
│                                        │                     │
│                           ┌────────────▼────────────┐       │
│                           │    Docker Registry      │       │
│                           │  (Docker Hub, ECR, etc.)│       │
│                           └─────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

### Docker Engine

```
Docker Engine = Docker Daemon + REST API + CLI

Components:
  1. Docker Daemon (dockerd): Background process managing containers
  2. REST API: Interface for programs to talk to daemon
  3. Docker CLI: Command-line tool for users

Docker Engine handles:
  - Building images
  - Managing containers
  - Managing networks
  - Managing volumes
  - Pulling/pushing images
```

### Docker CLI

```bash
# Docker CLI communicates with Docker Daemon via REST API

docker build      # Build image from Dockerfile
docker run        # Create and start container
docker ps         # List running containers
docker stop       # Stop container
docker rm         # Remove container
docker images     # List images
docker pull       # Pull image from registry
docker push       # Push image to registry
docker logs       # View container logs
docker exec       # Execute command in container
docker inspect    # Detailed info about container/image
```

### Docker Daemon

```
Docker Daemon (dockerd):
  - Long-running background process
  - Listens on Unix socket (/var/run/docker.sock) or TCP
  - Manages Docker objects (images, containers, networks, volumes)
  - Communicates with other daemons (Swarm)
  - Delegates container execution to containerd

Daemon responsibilities:
  - Image management (build, pull, push, cache)
  - Container lifecycle (create, start, stop, remove)
  - Network management (bridge, overlay, host)
  - Volume management (create, mount, unmount)
  - Security (user namespaces, seccomp, AppArmor)
```

### Docker Client

```
Docker Client:
  - CLI tool (docker command)
  - Sends commands to Docker Daemon
  - Can connect to remote daemons
  - Reads Dockerfile, context for builds

Client-Daemon communication:
  Local:  Unix socket (/var/run/docker.sock)
  Remote: TCP (with TLS for security)

DOCKER_HOST=tcp://remote-server:2376
```

### Docker Host

```
Docker Host:
  - Machine running Docker Daemon
  - Can be local (laptop) or remote (server)
  - Runs containers
  - Stores images locally
  - Manages networks and volumes

Host provides:
  - Kernel (shared with containers)
  - Filesystem (for image layers, volumes)
  - Network interfaces
  - CPU, memory resources
```

### Docker Registry

```
Docker Registry: Storage and distribution system for Docker images

Types:
  Public:  Docker Hub (default)
  Private: AWS ECR, Google GCR, Azure ACR, Harbor, Nexus
  Self-hosted: docker/registry image

Flow:
  docker build → local image
  docker push → registry (stored)
  docker pull → download to local

Default: docker.io (Docker Hub)
  nginx:1.25 → docker.io/library/nginx:1.25
```

---

## Internal Working

```
What happens when you run: docker run nginx

1. Docker CLI sends request to Docker Daemon (REST API)
2. Daemon checks if image exists locally
3. If not: pulls from registry (Docker Hub)
   - Downloads image manifest
   - Downloads layers (parallel, deduplicated)
4. Daemon calls containerd to create container
5. containerd calls runc to:
   - Create Linux namespaces (PID, NET, MNT, UTS, IPC)
   - Set up cgroups (resource limits)
   - Create container filesystem (overlay)
   - Start container process
6. Container running with PID 1 = nginx master process
7. Daemon returns container ID to CLI
```

---

## Interview Questions

### Q1: What is Docker and why do we use it?

**A:** Docker is a containerization platform that packages applications with their dependencies into portable, isolated containers. We use it because: consistent environments (no "works on my machine"), fast startup (seconds vs VM minutes), efficient resources (shared kernel, ~MB not GB), and standardized deployment pipeline (build once, run anywhere).

### Q2: What is the difference between a container and a VM?

**A:**
- **VM:** Full guest OS with own kernel. Strong isolation. Heavy (GBs). Minutes to start. Hypervisor overhead.
- **Container:** Process isolation via namespaces/cgroups. Shares host kernel. Light (MBs). Seconds to start. Near-native performance.

Containers are less isolated than VMs but far more efficient for application packaging.

### Q3: Explain Docker architecture.

**A:** Docker uses client-server architecture:
- **Client (CLI):** User interface, sends commands via REST API
- **Daemon (dockerd):** Background process managing images, containers, networks, volumes
- **containerd:** Container runtime managing container lifecycle
- **runc:** Low-level OCI runtime that creates containers (namespaces, cgroups)
- **Registry:** Stores and distributes images (Docker Hub, ECR)

### Q4: What is the difference between Docker and Kubernetes?

**A:** Docker builds and runs containers on a single host. Kubernetes orchestrates containers across multiple hosts — handling scheduling, scaling, self-healing, service discovery, and rolling updates. Docker creates the container images; Kubernetes deploys and manages them at scale. They're complementary tools.

### Q5: Can Docker containers run on any operating system?

**A:** Docker containers share the host kernel. Linux containers require a Linux kernel (or Linux VM on Mac/Windows via Docker Desktop). Windows containers require Windows kernel. You can't run a Windows container on Linux or vice versa directly. Docker Desktop on Mac/Windows runs a lightweight Linux VM transparently.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Thinking containers are VMs | Wrong mental model | Containers are isolated processes, not VMs |
| Running Docker in production without orchestration | No HA, no scaling | Use Kubernetes for production |
| Using Docker Compose for production | Single-host only | Use K8s or ECS for multi-host |
| Not understanding Docker ≠ containerd | Confusion about runtime | Docker is tooling around containerd |
| Ignoring host kernel sharing | Security misunderstanding | Containers share kernel = less isolation than VM |

---

## Best Practices

1. **Use Docker for packaging** — standardize build and ship
2. **One process per container** — separation of concerns
3. **Use orchestration for production** — Kubernetes, ECS
4. **Keep images small** — alpine, distroless base images
5. **Don't store state in containers** — use volumes or external storage
6. **Use .dockerignore** — reduce build context
7. **Pin versions** — both base images and packages
8. **Security first** — non-root, minimal images, scan for vulnerabilities

---

## Related Topics

- [02. Core Docker Concepts](./02-core-docker-concepts.md)
- [03. Docker Architecture](./03-docker-architecture.md)
- [22. Docker Internals](./22-docker-internals.md)
- [23. Docker vs Virtual Machines](./23-docker-vs-virtual-machines.md)
