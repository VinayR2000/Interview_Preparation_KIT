# Linux + Docker + Kubernetes Connection ⭐⭐⭐

## Linux Foundations of Containers

### How Docker Uses Linux

```
┌─────────────────────────────────┐
│        Docker Engine            │
├─────────────────────────────────┤
│        containerd               │
├─────────────────────────────────┤
│      Linux Kernel Features      │
│  ┌───────────┬────────────────┐ │
│  │Namespaces │   cgroups      │ │
│  │(isolation)│(resource limits)│ │
│  └───────────┴────────────────┘ │
│  ┌───────────┬────────────────┐ │
│  │ UnionFS   │   Seccomp      │ │
│  │(layered   │   (syscall     │ │
│  │filesystem)│   filtering)   │ │
│  └───────────┴────────────────┘ │
├─────────────────────────────────┤
│         Linux Kernel            │
└─────────────────────────────────┘
```

---

## Linux Namespaces — Process Isolation

Namespaces make a process think it has its own isolated system.

| Namespace | Isolates | Effect |
|-----------|----------|--------|
| PID | Process IDs | Container sees its own PID 1 |
| NET | Network stack | Container has own IP, ports, routing |
| MNT | Filesystem mounts | Container has own root filesystem |
| UTS | Hostname | Container has its own hostname |
| IPC | Inter-process communication | Isolated shared memory, semaphores |
| USER | User/group IDs | Root in container ≠ root on host |

### Observing Namespaces

```bash
# List namespaces for a process
ls -la /proc/<PID>/ns/

# View namespace details
lsns                                # List all namespaces
lsns -t net                         # Network namespaces only

# Enter a container's namespace
nsenter -t <PID> -n ip addr         # Run command in container's network namespace
nsenter -t <PID> -m -p ps aux       # View processes from container's perspective
```

### PID Namespace Example
```bash
# On host: container process has a normal PID
ps aux | grep java
# app  12345 ... java -jar app.jar

# Inside container: same process appears as PID 1
docker exec container ps aux
# PID 1: java -jar app.jar
```

---

## cgroups — Resource Limits

Control groups limit how much CPU, memory, and I/O a process can use.

### Key cgroup Resources
| Resource | Controls |
|----------|----------|
| cpu | CPU time allocation |
| cpuset | Which CPUs process can use |
| memory | Memory limit + swap |
| blkio | Disk I/O bandwidth |
| pids | Max number of processes |

### Viewing cgroup Limits
```bash
# Container's cgroup
cat /sys/fs/cgroup/memory/docker/<container_id>/memory.limit_in_bytes
cat /sys/fs/cgroup/cpu/docker/<container_id>/cpu.shares

# Docker's resource limits
docker stats                         # Real-time resource usage
docker inspect <container> | grep -A 5 "Memory"
```

### Docker Resource Limits (use Linux cgroups underneath)
```bash
# Limit memory
docker run --memory=512m --memory-swap=1g myapp

# Limit CPU
docker run --cpus=2 myapp           # Max 2 CPUs
docker run --cpu-shares=512 myapp   # Relative weight

# Combined
docker run \
  --memory=2g \
  --cpus=1.5 \
  --pids-limit=100 \
  myapp
```

---

## Container Filesystem (UnionFS/OverlayFS)

```
┌─────────────────────────────────┐
│     Container Layer (writable)   │  ← Changes go here
├─────────────────────────────────┤
│     Application Layer (read-only)│  ← COPY app.jar
├─────────────────────────────────┤
│     JDK Layer (read-only)        │  ← RUN apt-get install openjdk
├─────────────────────────────────┤
│     Base Image Layer (read-only) │  ← FROM ubuntu:22.04
└─────────────────────────────────┘
```

```bash
# View overlay filesystem
mount | grep overlay

# Container filesystem location on host
docker inspect <container> | grep "UpperDir"
# /var/lib/docker/overlay2/<id>/diff/

# View layers
docker history myimage
```

---

## Container Networking (Linux Network Namespaces)

```
┌──────────────────────────────────────────┐
│                Host Network               │
│  eth0: 10.0.1.5                          │
│                                          │
│  ┌──────────┐  ┌──────────┐             │
│  │Container1│  │Container2│             │
│  │172.17.0.2│  │172.17.0.3│             │
│  │   veth   │  │   veth   │             │
│  └────┬─────┘  └────┬─────┘             │
│       │              │                   │
│  ─────┴──────────────┴────── docker0 ──  │
│              (bridge)        172.17.0.1   │
└──────────────────────────────────────────┘
```

```bash
# View Docker bridge
ip addr show docker0
brctl show docker0                  # Bridge details

# View container's network
docker exec <container> ip addr
docker exec <container> cat /etc/resolv.conf

# Host network namespace
ip netns list                       # List network namespaces
```

---

## Troubleshooting Containers with Linux Tools

### From the Host
```bash
# Find container's PID on host
docker inspect --format '{{.State.Pid}}' <container>
CONTAINER_PID=12345

# Check container's resource usage
cat /proc/$CONTAINER_PID/status | grep -i mem
cat /proc/$CONTAINER_PID/limits

# Check open files
ls /proc/$CONTAINER_PID/fd | wc -l

# Check network connections
nsenter -t $CONTAINER_PID -n ss -tlnp

# Check processes inside (from host)
nsenter -t $CONTAINER_PID -p -m ps aux

# Trace system calls
strace -p $CONTAINER_PID -f
```

### From Inside the Container
```bash
# Enter container shell
docker exec -it <container> /bin/bash
docker exec -it <container> /bin/sh    # Minimal images

# Inside container, use standard Linux commands
ps aux                               # Processes
cat /proc/1/cmdline                  # Main process command
df -h                                # Filesystem
env                                  # Environment variables
cat /etc/hosts                       # DNS entries
ss -tlnp                             # Listening ports
```

---

## Linux + Kubernetes

### Kubernetes Node Architecture

```
┌─────────────────────────────────────────────────┐
│                 Kubernetes Node                   │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │  kubelet (manages pods on this node)       │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │  Container Runtime (containerd/CRI-O)      │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────┐  ┌────────────┐  ┌──────────┐  │
│  │   Pod A    │  │   Pod B    │  │  Pod C   │  │
│  │┌──────────┐│  │┌──────────┐│  │┌────────┐│  │
│  ││Container ││  ││Container ││  ││Container││  │
│  ││(Java App)││  ││(Java App)││  ││(Redis)  ││  │
│  │└──────────┘│  │└──────────┘│  │└────────┘│  │
│  └────────────┘  └────────────┘  └──────────┘  │
│                                                  │
│  Linux Kernel (namespaces, cgroups, networking)  │
└─────────────────────────────────────────────────┘
```

### Key Linux Concepts in Kubernetes

| K8s Concept | Linux Foundation |
|-------------|-----------------|
| Pod isolation | Linux namespaces |
| Resource limits/requests | cgroups |
| Container filesystem | OverlayFS |
| Pod networking | veth pairs, bridges, iptables |
| Service routing | iptables/IPVS rules |
| PersistentVolume | Linux mount points |
| Node resources | /proc/meminfo, /proc/cpuinfo |

---

### Troubleshooting Pods with Linux

```bash
# SSH to the node
ssh node-worker-1

# Find pod's container
crictl ps | grep my-pod

# Get container PID
crictl inspect <container_id> | grep pid

# Use Linux tools
# Check memory
cat /proc/<PID>/status | grep VmRSS
# Check open files
ls /proc/<PID>/fd | wc -l
# Check network
nsenter -t <PID> -n ss -tlnp
# Check filesystem
nsenter -t <PID> -m df -h
```

### Kubernetes Resource Limits → cgroups

```yaml
# Pod spec
resources:
  requests:
    memory: "512Mi"      # → memory.soft_limit_in_bytes
    cpu: "500m"          # → cpu.shares
  limits:
    memory: "1Gi"        # → memory.limit_in_bytes (OOM kill if exceeded)
    cpu: "1000m"         # → cpu.cfs_quota_us
```

```bash
# On the node, these map to:
cat /sys/fs/cgroup/memory/kubepods/pod<uid>/memory.limit_in_bytes
cat /sys/fs/cgroup/cpu/kubepods/pod<uid>/cpu.cfs_quota_us
```

---

### Kubernetes Networking (Linux Level)

```bash
# View iptables rules created by kube-proxy
iptables -t nat -L KUBE-SERVICES -n
iptables -t nat -L KUBE-SEP-xxxxx -n

# View Pod network interfaces
ip addr                             # On the node
brctl show                          # Bridge details

# DNS resolution inside a pod
kubectl exec pod -- nslookup my-service
kubectl exec pod -- cat /etc/resolv.conf
# nameserver 10.96.0.10   (CoreDNS)
# search default.svc.cluster.local svc.cluster.local
```

---

## Production Troubleshooting Checklist ⭐⭐⭐

### Container/Pod is OOMKilled
```bash
# 1. Check events
kubectl describe pod <pod> | grep -A5 "Events"
# Look for: "OOMKilled"

# 2. Check container memory usage history
kubectl top pod <pod>

# 3. On the node
dmesg | grep -i "oom\|killed"
journalctl -k | grep -i "oom"

# 4. Solution
# Increase memory limits or fix memory leak
```

### Container Can't Start
```bash
# 1. Check pod status
kubectl describe pod <pod>

# 2. Check container logs
kubectl logs <pod> --previous        # Previous crashed instance

# 3. On the node
crictl logs <container_id>
journalctl -u kubelet | grep <pod>

# 4. Common causes
# - Image pull failed (permissions, wrong tag)
# - Entrypoint/CMD error
# - Port conflict
# - Volume mount issues
# - Resource limits too low
```

### Network Issues Between Pods
```bash
# 1. DNS resolution
kubectl exec pod -- nslookup service-name

# 2. Connectivity test
kubectl exec pod -- curl http://service-name:8080/health

# 3. Check network policies
kubectl get networkpolicies

# 4. On the node — check iptables
iptables -t nat -L | grep <service-cluster-ip>

# 5. Check pod IPs
kubectl get pods -o wide
```

---

## Key Interview Questions

**Q: How does Docker achieve process isolation?**
> Docker uses Linux namespaces for isolation (PID, NET, MNT, UTS, IPC, USER) and cgroups for resource limits. Namespaces make each container think it has its own isolated system. cgroups prevent containers from consuming unlimited resources.

**Q: What happens when a container exceeds its memory limit?**
> The Linux kernel's OOM killer terminates the process. In Kubernetes, the pod shows status `OOMKilled`. The cgroup memory limit triggers the kernel to kill processes when `memory.limit_in_bytes` is exceeded.

**Q: How are Kubernetes Services implemented at the Linux level?**
> kube-proxy creates iptables (or IPVS) rules on each node. When traffic hits a Service ClusterIP, iptables DNAT rules redirect it to one of the backend Pod IPs using round-robin. The networking is all done via standard Linux networking primitives.

**Q: Container is running but you can't connect to the application. How do you debug?**
> 1. `kubectl exec pod -- ss -tlnp` — Is the app listening?
> 2. `kubectl exec pod -- curl localhost:8080` — Does it respond internally?
> 3. Check the Service selector matches pod labels
> 4. `kubectl exec other-pod -- curl service:8080` — Service DNS works?
> 5. Check NetworkPolicies
> 6. On node: `nsenter -t <PID> -n ss -tlnp` — Verify from host perspective
