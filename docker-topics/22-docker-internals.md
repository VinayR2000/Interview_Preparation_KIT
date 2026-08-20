# 22. Docker Internals ⭐⭐

---

## Theory

**Docker internals** covers how containers actually work at the Linux kernel level — namespaces, cgroups, union filesystems, and the container runtime chain. Understanding internals is essential for debugging, security hardening, and interview depth.

### Container = Process with Isolation

```
Key insight: A container is NOT a VM.
  A container is just a Linux process with:
    1. Restricted view (namespaces)
    2. Limited resources (cgroups)
    3. Isolated filesystem (overlay fs)

Proof:
  Inside container:  PID 1 (java -jar app.jar)
  On host:           PID 28453 (same process, visible!)

  docker top mycontainer
  # PID   USER   COMMAND
  # 28453 1000   java -jar app.jar

  # The container process is just a normal Linux process
  # with restrictions applied by the kernel
```

### Linux Namespaces (Isolation)

```
┌──────────────────────────────────────────────────────────────┐
│                    LINUX NAMESPACES                            │
│                                                               │
│  Namespace │ What it isolates          │ Effect in container  │
│  ──────────┼───────────────────────────┼────────────────────  │
│  PID       │ Process IDs               │ Own PID tree (1,2..) │
│  NET       │ Network interfaces, IPs   │ Own eth0, own IP     │
│  MNT       │ Filesystem mounts         │ Own root filesystem  │
│  UTS       │ Hostname, domain name     │ Own hostname         │
│  IPC       │ Shared memory, semaphores │ Own IPC resources    │
│  USER      │ User and group IDs        │ UID 0 maps to 1000  │
│  CGROUP    │ Cgroup root directory     │ Own cgroup view      │
│                                                               │
│  Each container gets its own set of namespaces               │
│  = Containers can't see each other's processes, network, etc.│
└──────────────────────────────────────────────────────────────┘
```

```bash
# View namespaces of a container process
ls -la /proc/<container_pid>/ns/
# lrwxrwxrwx cgroup -> cgroup:[4026532468]
# lrwxrwxrwx ipc    -> ipc:[4026532396]
# lrwxrwxrwx mnt    -> mnt:[4026532394]
# lrwxrwxrwx net    -> net:[4026532399]
# lrwxrwxrwx pid    -> pid:[4026532397]
# lrwxrwxrwx user   -> user:[4026531837]
# lrwxrwxrwx uts    -> uts:[4026532395]

# Each namespace has a unique ID
# Containers with different IDs = different views
```

### PID Namespace

```
Host PID tree:                    Container PID tree:
  PID 1 (systemd)                  PID 1 (java) ← thinks it's PID 1
  ├── PID 500 (dockerd)            ├── PID 2 (thread)
  ├── PID 28453 (java) ← same!    └── PID 3 (thread)
  └── PID 29000 (nginx)

Container sees: only its own processes (PID 1 = app)
Host sees: all processes (container process has normal PID)

Implications:
  - PID 1 in container receives SIGTERM on docker stop
  - Container can't see or kill host processes
  - Container can't see other containers' processes
```

### Network Namespace

```
┌─────────────────────────────────────────────────────────────┐
│ HOST                                                         │
│  eth0: 192.168.1.100                                        │
│  docker0 (bridge): 172.17.0.1                               │
│       │                                                      │
│  ┌────┴───────────────────────────────────────────┐         │
│  │ veth pair (virtual ethernet)                    │         │
│  │                                                 │         │
│  │  ┌─────────────────┐   ┌─────────────────┐    │         │
│  │  │ Container A      │   │ Container B      │    │         │
│  │  │ NET namespace    │   │ NET namespace    │    │         │
│  │  │ eth0: 172.17.0.2│   │ eth0: 172.17.0.3│    │         │
│  │  │ lo: 127.0.0.1   │   │ lo: 127.0.0.1   │    │         │
│  │  └─────────────────┘   └─────────────────┘    │         │
│  └────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────┘

Each container:
  - Gets its own network namespace
  - Has its own eth0 interface
  - Has its own IP address
  - Connected to host via veth pair (virtual cable)
  - Bridge network routes traffic between containers and host
```

### Cgroups (Resource Limits)

```
┌──────────────────────────────────────────────────────────────┐
│                    CONTROL GROUPS (cgroups)                    │
│                                                               │
│  Cgroup Resource │ What it limits        │ Docker flag        │
│  ────────────────┼───────────────────────┼───────────────────│
│  memory          │ RAM usage             │ --memory=512m      │
│  cpu             │ CPU time/shares       │ --cpus=1.5         │
│  cpuset          │ Which CPUs to use     │ --cpuset-cpus=0,1  │
│  blkio           │ Disk I/O bandwidth    │ --device-read-bps  │
│  pids            │ Number of processes   │ --pids-limit=100   │
│  devices         │ Device access         │ --device           │
│                                                               │
│  Cgroups v2 (unified hierarchy):                             │
│    /sys/fs/cgroup/system.slice/docker-<id>.scope/            │
│    ├── memory.max          (hard limit)                      │
│    ├── memory.current      (current usage)                   │
│    ├── cpu.max             (quota/period)                    │
│    └── pids.max            (process limit)                   │
└──────────────────────────────────────────────────────────────┘
```

```bash
# Check cgroup limits for a container
# cgroups v2 path:
cat /sys/fs/cgroup/system.slice/docker-<container_id>.scope/memory.max
# 536870912 (512MB in bytes)

cat /sys/fs/cgroup/system.slice/docker-<container_id>.scope/cpu.max
# 150000 100000 (1.5 CPUs: 150ms per 100ms period)

# What happens when limit exceeded:
#   Memory: OOM killer terminates container (exit code 137)
#   CPU:    Throttled (slowed down, not killed)
#   PIDs:   Fork fails (cannot create new processes)
```

### Union/Overlay Filesystem

```
┌─────────────────────────────────────────────────────────────┐
│              OVERLAY FILESYSTEM (OverlayFS)                   │
│                                                              │
│  Container View (merged):                                    │
│    /app/app.jar                                              │
│    /usr/lib/jvm/java-21/                                     │
│    /etc/os-release                                           │
│                                                              │
│  Actual Layers (stacked):                                    │
│    ┌─────────────────────┐                                   │
│    │ Container Layer (RW)│  ← Writable (container changes)  │
│    ├─────────────────────┤                                   │
│    │ Layer 4: COPY app   │  ← Read-only                     │
│    ├─────────────────────┤                                   │
│    │ Layer 3: RUN apk    │  ← Read-only                     │
│    ├─────────────────────┤                                   │
│    │ Layer 2: ENV/WORKDIR│  ← Read-only                     │
│    ├─────────────────────┤                                   │
│    │ Layer 1: base image │  ← Read-only (alpine)            │
│    └─────────────────────┘                                   │
│                                                              │
│  Copy-on-Write (CoW):                                        │
│    Read: looks through layers top-down                       │
│    Write: copies file to container layer, modifies there     │
│    Delete: "whiteout" file in container layer (hides below)  │
│                                                              │
│  Sharing: 100 containers from same image = 1 copy of layers │
│           Only container layer is unique per container        │
└─────────────────────────────────────────────────────────────┘
```

```bash
# Inspect image layers
docker image inspect myapp:1.0 --format='{{json .RootFS.Layers}}' | jq
# ["sha256:abc...", "sha256:def...", "sha256:ghi..."]

# View overlay mount for a container
docker inspect --format='{{.GraphDriver.Data.MergedDir}}' <container>
# /var/lib/docker/overlay2/<id>/merged

# Each layer stored at:
# /var/lib/docker/overlay2/<layer_id>/diff/
```

### Container Runtime Chain

```
┌─────────────────────────────────────────────────────────────┐
│              CONTAINER RUNTIME CHAIN                          │
│                                                              │
│  docker CLI                                                  │
│       │ REST API                                             │
│       ▼                                                      │
│  dockerd (Docker Daemon)                                     │
│       │ gRPC                                                 │
│       ▼                                                      │
│  containerd (high-level runtime)                             │
│       │ - Image management (pull, push, store)              │
│       │ - Container lifecycle (create, start, stop)         │
│       │ - Snapshot (filesystem) management                  │
│       │                                                      │
│       │ OCI Runtime Spec                                     │
│       ▼                                                      │
│  runc (low-level OCI runtime)                                │
│       │ - Creates namespaces                                │
│       │ - Configures cgroups                                │
│       │ - Sets up rootfs (pivot_root)                       │
│       │ - Executes container process                        │
│       │ - Exits (runc is NOT long-running!)                 │
│       ▼                                                      │
│  Container Process (PID 1 inside container)                  │
│       │ - Runs as isolated process on host                  │
│       │ - Monitored by containerd-shim                      │
│       └── containerd-shim (keeps stdin/stdout/stderr open)  │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Important: runc creates the container then exits!
  containerd-shim stays to:
    - Keep IO pipes open
    - Report exit status to containerd
    - Allow dockerd to restart without killing containers
```

### What Happens During `docker run`

```
docker run -d -p 8080:8080 --name api myapp:1.0

Step 1: CLI → Daemon (REST API)
  POST /containers/create
  POST /containers/{id}/start

Step 2: Daemon → containerd (gRPC)
  Check if image exists locally
  If not: pull from registry (download manifest + layers)

Step 3: containerd prepares container
  - Create snapshot (overlay filesystem from image layers)
  - Generate OCI runtime spec (config.json)
  - Create container bundle

Step 4: containerd → runc
  runc creates:
    - New PID namespace
    - New NET namespace (create veth pair)
    - New MNT namespace (mount overlay)
    - New UTS namespace (set hostname)
    - Set up cgroups (memory, CPU limits)
    - pivot_root to container filesystem
    - Drop capabilities
    - Execute ENTRYPOINT command as PID 1

Step 5: runc exits, containerd-shim monitors
  - shim keeps stdout/stderr pipes open
  - shim reports status to containerd
  - Container process runs independently

Step 6: Network setup
  - Create veth pair (host ↔ container)
  - Attach to bridge network
  - Set up iptables rules for port mapping (-p 8080:8080)
  - Configure DNS (/etc/resolv.conf)

Total time: ~1 second for a typical container
```

### Docker Storage Driver

```
Storage Drivers (manage image layers):
  overlay2  — default, most performant (recommended)
  devicemapper — block-level, legacy
  btrfs     — if using btrfs filesystem
  zfs       — if using zfs filesystem

overlay2 internals:
  /var/lib/docker/overlay2/
    ├── <layer-id>/
    │   ├── diff/        ← actual layer content
    │   ├── link         ← shortened identifier
    │   ├── lower        ← reference to parent layers
    │   └── work/        ← overlayfs workdir
    └── <container-id>/
        ├── diff/        ← container writes go here
        ├── merged/      ← union view (what container sees)
        └── work/

Check current driver:
  docker info | grep "Storage Driver"
  # Storage Driver: overlay2
```

---

## Code

### Exploring Container Internals:

```bash
#!/bin/bash
# explore-container.sh — See container internals

CONTAINER=$1
PID=$(docker inspect --format='{{.State.Pid}}' $CONTAINER)

echo "=== Container PID on host: $PID ==="

echo ""
echo "=== Namespaces ==="
ls -la /proc/$PID/ns/

echo ""
echo "=== Cgroup Memory Limit ==="
cat /sys/fs/cgroup/system.slice/docker-$(docker inspect --format='{{.Id}}' $CONTAINER).scope/memory.max 2>/dev/null || \
cat /proc/$PID/cgroup

echo ""
echo "=== Mount Info (rootfs) ==="
cat /proc/$PID/mountinfo | grep "overlay"

echo ""
echo "=== Network Namespace ==="
nsenter -t $PID -n ip addr show

echo ""
echo "=== Process Tree (from host view) ==="
pstree -p $PID
```

### Creating a Container "By Hand" (conceptual):

```bash
# This is essentially what runc does:

# 1. Create namespaces
unshare --pid --net --mnt --uts --ipc --fork

# 2. Set up filesystem (simplified)
mount -t overlay overlay -o \
  lowerdir=/image/layer1:/image/layer2,\
  upperdir=/container/diff,\
  workdir=/container/work \
  /container/merged

# 3. Change root filesystem
pivot_root /container/merged /container/merged/.pivot_root
umount /.pivot_root

# 4. Set up cgroups
echo $$ > /sys/fs/cgroup/memory/docker/container1/cgroup.procs
echo 536870912 > /sys/fs/cgroup/memory/docker/container1/memory.max

# 5. Drop capabilities
capsh --drop=all --keep=1 -- -c "/app/start.sh"

# 6. Execute application
exec /usr/bin/java -jar /app/app.jar
```

---

## Interview Questions

### Q1: What is a container at the Linux level?

**A:** A container is a regular Linux process isolated using kernel features:
- **Namespaces** — restrict what the process can see (own PID tree, network, filesystem, hostname, users)
- **Cgroups** — restrict what the process can use (memory, CPU, PIDs, I/O)
- **Union filesystem** — provide isolated filesystem view using layered image

There's no "container" kernel object. It's a combination of existing kernel primitives applied to a process.

### Q2: Explain the Docker runtime chain (docker → runc).

**A:** 
1. **docker CLI** — user interface, sends REST API calls
2. **dockerd** — daemon, manages high-level Docker objects
3. **containerd** — container lifecycle manager (image pull, snapshots, container create)
4. **runc** — OCI runtime, creates namespaces/cgroups, starts process, then exits
5. **containerd-shim** — stays alive to keep IO pipes open and report exit status

runc is ephemeral (exits after setup). The container process runs independently.

### Q3: How does overlay filesystem work in Docker?

**A:** Docker images are stacked read-only layers. A container adds a writable layer on top. OverlayFS merges them into a single view:
- **Read:** looks through layers top-down (first match wins)
- **Write:** copy-on-write — copies file to writable layer, modifies there
- **Delete:** creates a "whiteout" file that hides the lower layer file
- **Sharing:** 100 containers from one image share the same read-only layers (only writable layers are unique)

### Q4: What happens when a container exceeds its memory limit?

**A:** The kernel's OOM (Out Of Memory) killer terminates the container's main process. The container exits with code 137 (128 + 9 = SIGKILL). Docker marks `OOMKilled: true` in inspect output. The cgroup enforces the hard limit — there's no "swap" by default. Fix: increase memory limit or reduce application memory usage.

### Q5: Why can containers start in seconds while VMs take minutes?

**A:** Containers don't boot an OS:
- No kernel boot (shares host kernel)
- No hardware initialization
- No init system (systemd) startup
- Just: create namespaces, set cgroups, mount filesystem, exec process
- Total kernel operations: ~100ms
- Image layer caching: no download if already present
- VMs must boot full guest OS from scratch every time

---

## Common Mistakes

| Mistake | Reality |
|---------|---------|
| "Containers are lightweight VMs" | Containers are isolated processes |
| "Containers have their own kernel" | They share the host kernel |
| "Container filesystem is separate disk" | It's overlay layers on host disk |
| "docker run creates a VM" | It creates namespaces + cgroups for a process |
| "Containers are 100% isolated" | Kernel is shared = smaller isolation boundary than VM |
| "runc manages containers" | runc creates and exits; shim + containerd manage |

---

## Best Practices

1. **Understand namespaces** — explains why `localhost` differs inside containers
2. **Monitor cgroups** — explains OOM kills and throttling
3. **Minimize layers** — each layer adds overlay overhead
4. **Use `--read-only`** — prevents writes to overlay (use volumes)
5. **User namespaces** — map container root to unprivileged host user
6. **Know the runtime chain** — helps debug startup failures
7. **Shared layers** — reuse base images across services for efficiency

---

## Related Topics

- [01. Docker Fundamentals](./01-docker-fundamentals.md)
- [03. Docker Architecture](./03-docker-architecture.md)
- [04. Docker Images](./04-docker-images.md)
- [13. Docker Security](./13-docker-security.md)
- [23. Docker vs Virtual Machines](./23-docker-vs-virtual-machines.md)
