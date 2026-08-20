# 3. Docker Architecture ⭐⭐⭐

---

## Theory

Docker uses a client-server architecture where the Docker client communicates with the Docker daemon, which builds, runs, and manages containers.

### Docker Client

```
Docker CLI (docker command) — user-facing interface

Commands → REST API calls → Docker Daemon

Examples:
  docker build    → POST /build
  docker run      → POST /containers/create + POST /containers/{id}/start
  docker pull     → POST /images/create
  docker ps       → GET /containers/json

The client can connect to:
  - Local daemon (unix socket: /var/run/docker.sock)
  - Remote daemon (TCP: tcp://host:2375)
```

### Docker Host

```
The machine where Docker daemon runs:

Docker Host contains:
  - Docker Daemon (dockerd)
  - Container Runtime (containerd + runc)
  - Images (local cache)
  - Containers (running instances)
  - Volumes (persistent data)
  - Networks (bridge, overlay, etc.)
```

### Docker Daemon (dockerd)

```
dockerd: Background service managing Docker objects

Responsibilities:
  1. Listen for Docker API requests
  2. Manage images (pull, build, push)
  3. Manage containers (create, start, stop)
  4. Manage networks (create, connect)
  5. Manage volumes (create, mount)
  6. Orchestrate with containerd for container lifecycle

dockerd → containerd → runc → container process
```

### Docker Registry

```
Registry: Stores and distributes Docker images

Public:  Docker Hub (hub.docker.com)
Private: AWS ECR, Google GCR, Azure ACR, Harbor

Flow:
  docker pull nginx:1.25
    → Docker daemon contacts Docker Hub
    → Downloads image layers
    → Stores locally

  docker push myapp:v1
    → Docker daemon pushes layers to registry
    → Available for others to pull
```

### REST API

```
Docker exposes a REST API (used by CLI and SDKs):

Endpoints:
  /containers   - Container operations
  /images       - Image operations
  /volumes      - Volume operations
  /networks     - Network operations
  /system       - System information

Example:
  curl --unix-socket /var/run/docker.sock http://localhost/containers/json
  → Returns JSON list of running containers

SDKs: Docker SDK for Python, Go, Java, etc.
```

### containerd

```
containerd: Industry-standard container runtime

Docker's role simplified over time:
  Docker Engine = dockerd + containerd + runc

containerd handles:
  - Image pull/push (registry interaction)
  - Container lifecycle (create, start, stop, delete)
  - Snapshot management (filesystem layers)
  - Network namespace setup
  - Task execution

Kubernetes also uses containerd directly (no Docker needed)
```

### runc

```
runc: Low-level OCI runtime

containerd → runc → actual Linux container

runc responsibilities:
  - Create Linux namespaces (PID, NET, MNT, UTS, IPC)
  - Set up cgroups (resource limits)
  - Configure seccomp filters
  - Set up root filesystem
  - Execute container process (PID 1)

runc creates the container and exits (containerd manages lifecycle)
```

### OCI (Open Container Initiative)

```
OCI defines open standards:

1. Runtime Specification:
   - How to run a container
   - Filesystem bundle format
   - Lifecycle operations (create, start, kill, delete)

2. Image Specification:
   - Image manifest format
   - Layer format (tar+gzip)
   - Configuration format

3. Distribution Specification:
   - How to push/pull images from registries
   - API for registry interactions

Ensures interoperability:
  Docker images work in containerd, CRI-O, Podman, etc.
```

---

## Internal Working

```
Complete flow: docker run nginx

1. Docker CLI parses command
2. CLI sends REST API request to dockerd
3. dockerd checks if nginx image exists locally
4. If not: pull from registry (Docker Hub)
   a. Download manifest (layers list)
   b. Download each layer (parallel)
   c. Store in local image cache
5. dockerd calls containerd: create container
6. containerd prepares:
   a. Unpack image layers (union filesystem)
   b. Create writable container layer
   c. Prepare bundle (rootfs + config)
7. containerd calls runc: start container
8. runc creates:
   a. Linux namespaces (isolation)
   b. cgroups (resource limits)
   c. Root filesystem (overlay mount)
   d. Starts container process (PID 1)
9. runc exits, containerd monitors container
10. Container running, dockerd reports status
```

---

## Diagram

```
┌────────────────────── DOCKER ARCHITECTURE ───────────────────────┐
│                                                                    │
│  ┌──────────────┐     REST API      ┌──────────────────────┐    │
│  │ Docker CLI   │ ──────────────→   │   Docker Daemon      │    │
│  │ (docker)     │                    │   (dockerd)          │    │
│  └──────────────┘                    │                      │    │
│                                      │  ┌────────────────┐  │    │
│  ┌──────────────┐                    │  │  containerd    │  │    │
│  │ Docker       │ ← pull/push →     │  │                │  │    │
│  │ Registry     │                    │  │  ┌──────────┐  │  │    │
│  │ (Docker Hub) │                    │  │  │   runc   │  │  │    │
│  └──────────────┘                    │  │  └──────────┘  │  │    │
│                                      │  └────────────────┘  │    │
│                                      │                      │    │
│                                      │  ┌────────────────┐  │    │
│                                      │  │  Containers    │  │    │
│                                      │  │  Images        │  │    │
│                                      │  │  Volumes       │  │    │
│                                      │  │  Networks      │  │    │
│                                      │  └────────────────┘  │    │
│                                      └──────────────────────┘    │
└────────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: Explain Docker's architecture.

**A:** Docker uses a client-server model:
- **Docker Client (CLI):** Sends commands via REST API to the daemon
- **Docker Daemon (dockerd):** Manages images, containers, networks, volumes
- **containerd:** Container runtime that manages container lifecycle
- **runc:** Low-level OCI runtime that creates namespaces/cgroups and starts the process
- **Registry:** Stores and distributes images

Flow: CLI → dockerd → containerd → runc → container process

### Q2: What is the difference between Docker daemon and containerd?

**A:**
- **dockerd:** High-level management — builds images, handles CLI API, manages networks/volumes, orchestrates container operations
- **containerd:** Container runtime — handles actual container lifecycle (create, start, stop), image management, filesystem snapshots

dockerd delegates container execution to containerd. Kubernetes uses containerd directly without dockerd.

### Q3: What is runc and what is its role?

**A:** runc is the low-level OCI runtime that actually creates and starts containers. It sets up Linux namespaces (PID, NET, MNT isolation), cgroups (resource limits), mounts the filesystem, and executes the container's main process. After starting the process, runc exits — containerd takes over monitoring.

---

## Best Practices

1. **Secure the Docker socket** — `/var/run/docker.sock` is root-equivalent access
2. **Use TLS for remote daemons** — never expose Docker API without encryption
3. **Keep Docker updated** — security patches in daemon and runtime
4. **Understand the stack** — helps troubleshoot container issues
5. **Use containerd directly** for production K8s (no Docker overhead)

---

## Related Topics

- [22. Docker Internals](./22-docker-internals.md)
- [20. Docker Storage Internals](./20-docker-storage-internals.md)
- [26. Docker + Kubernetes](./26-docker-kubernetes.md)
