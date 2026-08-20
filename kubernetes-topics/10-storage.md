# 10. Storage ⭐⭐

---

## Theory

Kubernetes provides a storage abstraction layer that decouples storage provisioning from consumption, allowing pods to use persistent storage that survives pod restarts and rescheduling.

### Ephemeral Storage

```
Ephemeral storage = lost when Pod is deleted/rescheduled

Types:
  - Container filesystem (default) — dies with container
  - emptyDir — dies with Pod
  - ConfigMap/Secret volumes — backed by API objects

Use cases:
  - Temporary files, caches, scratch space
  - Shared data between containers in same Pod
```

### Volume

A directory accessible to containers in a Pod. Lifetime depends on volume type:

```yaml
spec:
  containers:
  - name: app
    volumeMounts:
    - name: data
      mountPath: /app/data
  volumes:
  - name: data
    emptyDir: {}      # or hostPath, PVC, configMap, secret, etc.
```

### emptyDir

```yaml
# Created when Pod starts, deleted when Pod dies
volumes:
- name: cache
  emptyDir: {}              # Stored on node disk

- name: fast-cache
  emptyDir:
    medium: Memory          # Stored in RAM (tmpfs)
    sizeLimit: 256Mi
```

```
emptyDir:
  - Created when Pod is assigned to Node
  - Initially empty
  - Shared between all containers in the Pod
  - Deleted when Pod is removed from Node
  - Survives container restart (within same Pod)
  
Use cases:
  - Scratch space for sorting algorithms
  - Shared data between sidecar containers
  - Cache that can be recreated
```

### hostPath

```yaml
# Mounts a file or directory from the host Node
volumes:
- name: docker-sock
  hostPath:
    path: /var/run/docker.sock
    type: Socket

- name: node-logs
  hostPath:
    path: /var/log
    type: Directory
```

```
hostPath:
  - Mounts from Node filesystem
  - Data persists beyond Pod lifetime (on same node)
  - NOT portable (Pod must be on same node)
  - Security risk (access to host filesystem)
  
Use cases (limited):
  - Node-level log collection (DaemonSets)
  - Docker socket access
  - Development/testing only
  
WARNING: Avoid in production for application data!
```

### PersistentVolume (PV)

A piece of storage in the cluster provisioned by an admin or dynamically:

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: pv-10gi
spec:
  capacity:
    storage: 10Gi
  accessModes:
  - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  storageClassName: standard
  csi:
    driver: ebs.csi.aws.com
    volumeHandle: vol-0123456789abcdef
```

```
PersistentVolume:
  - Cluster-level resource (not namespaced)
  - Provisioned by admin or dynamically by StorageClass
  - Lifecycle independent of any Pod
  - Has capacity, access modes, reclaim policy
```

### PersistentVolumeClaim (PVC)

A request for storage by a user/Pod:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: my-app-data
  namespace: production
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
  storageClassName: gp3
```

```
PVC binds to a PV that satisfies its requirements:
  - Access mode matches
  - Storage capacity >= requested
  - StorageClass matches

PVC Lifecycle:
  Pending → Bound → Released

Pod uses PVC:
spec:
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: my-app-data
```

### StorageClass

Defines types of storage (performance tiers, provisioners):

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  iops: "3000"
  throughput: "125"
  encrypted: "true"
reclaimPolicy: Delete
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
```

```
StorageClass enables:
  - Dynamic provisioning (create PV automatically when PVC created)
  - Different storage tiers (fast SSD, cheap HDD)
  - Provider-specific parameters

Common StorageClasses:
  ┌─────────────┬──────────────────────────────┐
  │ Name        │ Backend                       │
  ├─────────────┼──────────────────────────────┤
  │ gp3         │ AWS EBS gp3                  │
  │ io2         │ AWS EBS io2 (high IOPS)      │
  │ efs         │ AWS EFS (shared filesystem)  │
  │ standard    │ Default cloud disk           │
  └─────────────┴──────────────────────────────┘
```

### Dynamic Provisioning

```
Static Provisioning:
  Admin creates PV → User creates PVC → Binding
  
Dynamic Provisioning:
  Admin creates StorageClass → User creates PVC →
  StorageClass auto-creates PV → Binding

Dynamic provisioning flow:
  1. User creates PVC with storageClassName: fast-ssd
  2. K8s finds StorageClass "fast-ssd"
  3. StorageClass provisioner creates actual storage (e.g., EBS volume)
  4. PV is automatically created
  5. PVC binds to new PV
  6. Pod can now use the PVC
```

### CSI (Container Storage Interface)

```
CSI: Standard interface for storage vendors to provide drivers

kubelet → CSI driver → Cloud storage (EBS, EFS, Azure Disk, etc.)

Popular CSI Drivers:
  - aws-ebs-csi-driver (EBS volumes)
  - aws-efs-csi-driver (EFS filesystem)
  - csi-driver-nfs (NFS shares)
  - azuredisk-csi-driver (Azure Disk)
  - pd-csi-driver (GCP Persistent Disk)
```

### Access Modes

```
┌────────────────────┬───────────────────────────────────────────┐
│ Mode               │ Description                                │
├────────────────────┼───────────────────────────────────────────┤
│ ReadWriteOnce (RWO)│ Single node can mount as read-write       │
│                    │ (most block storage: EBS, Azure Disk)      │
├────────────────────┼───────────────────────────────────────────┤
│ ReadOnlyMany (ROX) │ Multiple nodes can mount as read-only     │
│                    │ (good for shared config/data)              │
├────────────────────┼───────────────────────────────────────────┤
│ ReadWriteMany (RWX)│ Multiple nodes can mount as read-write    │
│                    │ (NFS, EFS, Azure Files)                    │
├────────────────────┼───────────────────────────────────────────┤
│ ReadWriteOncePod   │ Only ONE pod can mount as read-write      │
│ (RWOP) K8s 1.22+  │ (stricter than RWO)                        │
└────────────────────┴───────────────────────────────────────────┘
```

### Stateful Storage

```
Stateful workloads need:
  - Persistent storage that survives pod restart
  - Storage identity (each replica has its own volume)
  - Ordered provisioning

StatefulSet + PVC Template:
  pod-0 → pvc-0 → pv-0 (10Gi EBS)
  pod-1 → pvc-1 → pv-1 (10Gi EBS)
  pod-2 → pvc-2 → pv-2 (10Gi EBS)

Each pod gets its own dedicated persistent volume
```

---

## Internal Working

```
PV/PVC Binding Flow:

1. Admin creates StorageClass (or uses default)
2. User creates PVC (requests 10Gi, RWO, storageClass: gp3)
3. PV Controller in kube-controller-manager detects unbound PVC
4. If static PV exists matching requirements → bind
5. If no static PV but StorageClass has provisioner:
   a. External provisioner watches for unbound PVCs
   b. Calls cloud API to create volume (e.g., aws ec2 create-volume)
   c. Creates PV object representing the volume
   d. PV Controller binds PVC to PV
6. Pod references PVC
7. Scheduler considers volume topology (same AZ)
8. kubelet calls CSI driver to mount volume
9. Volume available at mountPath in container

Volume Lifecycle:
  Provisioning → Binding → Using → Releasing → Reclaiming

Reclaim Policies:
  Retain:  PV stays after PVC deleted (manual cleanup)
  Delete:  PV and backing storage deleted automatically
  Recycle: Deprecated (basic rm -rf)
```

---

## Diagram

```
┌────────────────────── STORAGE ARCHITECTURE ──────────────────┐
│                                                                │
│  ┌────────────────────────────────────────┐                  │
│  │           StorageClass: gp3            │                  │
│  │  provisioner: ebs.csi.aws.com          │                  │
│  │  parameters: type=gp3, encrypted=true  │                  │
│  └───────────────────┬────────────────────┘                  │
│                      │ (dynamic provisioning)                 │
│                      ▼                                        │
│  ┌──────────────┐  bind  ┌──────────────┐                   │
│  │     PVC      │ ←────→ │      PV      │                   │
│  │ (user claim) │        │ (actual vol) │                   │
│  │ 10Gi, RWO    │        │ 10Gi EBS     │                   │
│  └──────┬───────┘        └──────────────┘                   │
│         │                                                     │
│         │ (referenced in Pod spec)                            │
│         ▼                                                     │
│  ┌─────────────────────────────────────┐                     │
│  │              POD                     │                     │
│  │                                      │                     │
│  │  Container:                          │                     │
│  │    mountPath: /data ← volume: pvc    │                     │
│  │    (reads/writes to EBS volume)      │                     │
│  └─────────────────────────────────────┘                     │
│                                                                │
│  CSI Driver (ebs.csi.aws.com):                               │
│    - Attaches EBS to Node (EC2 instance)                     │
│    - Formats filesystem (ext4/xfs)                           │
│    - Mounts into Pod                                          │
└────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: What is the difference between PV and PVC?

**A:**
- **PV (PersistentVolume):** Actual storage resource in the cluster (like a physical disk). Cluster-scoped, provisioned by admin or dynamically. Has capacity, access mode, reclaim policy.
- **PVC (PersistentVolumeClaim):** A request for storage by a user/application. Namespace-scoped. Specifies desired size and access mode. K8s binds it to a suitable PV.

Think of PV as a hotel room and PVC as a reservation.

### Q2: Explain dynamic provisioning with StorageClass.

**A:**
1. Admin creates StorageClass (defines provisioner + parameters)
2. User creates PVC referencing the StorageClass
3. StorageClass provisioner automatically creates backing storage (e.g., EBS volume)
4. PV is auto-created and bound to PVC
5. No manual PV creation needed

This eliminates admin bottleneck and enables self-service storage.

### Q3: What are access modes and when to use each?

**A:**
- **RWO (ReadWriteOnce):** One node mounts as read-write. Most block storage (EBS). Use for single-pod databases.
- **ROX (ReadOnlyMany):** Many nodes mount read-only. Use for shared config, static assets.
- **RWX (ReadWriteMany):** Many nodes mount read-write. Requires shared filesystem (EFS, NFS). Use for shared uploads, CMS.
- **RWOP:** Single pod only. Strictest, prevents multi-attach issues.

### Q4: What happens when a PVC is deleted?

**A:** Depends on `reclaimPolicy`:
- **Retain:** PV and data preserved. Admin must manually clean up and make PV available again.
- **Delete:** PV and backing storage (EBS volume) automatically deleted.

For production databases, always use Retain to prevent accidental data loss.

### Q5: How does volumeBindingMode: WaitForFirstConsumer work?

**A:** Instead of binding PVC to PV immediately, it waits until a Pod using the PVC is scheduled. This ensures the PV is created in the same availability zone as the Pod. Without it, PV might be in a different AZ, making it unmountable. Critical for cloud block storage (EBS) which is AZ-specific.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using hostPath for persistent data | Data lost on rescheduling | Use PV/PVC |
| RWO volume with multi-replica Deployment | Mount fails on different node | Use RWX (EFS) or StatefulSet |
| Delete reclaim policy for databases | Data lost when PVC deleted | Use Retain policy |
| Not setting WaitForFirstConsumer | PV in wrong AZ | Set volumeBindingMode |
| Ignoring storage limits | Node disk full | Set resource limits, monitor |

---

## Best Practices

1. **Use dynamic provisioning** — StorageClass eliminates manual PV management
2. **Set volumeBindingMode: WaitForFirstConsumer** — AZ-aware binding
3. **Use Retain policy for important data** — prevent accidental deletion
4. **Enable volume expansion** — allowVolumeExpansion: true
5. **Use CSI drivers** — standard, well-maintained storage integration
6. **Monitor storage utilization** — alerts before disk full
7. **Backup PVs regularly** — volume snapshots (VolumeSnapshot API)
8. **Use emptyDir for caches** — don't waste persistent storage on temporary data

---

## Related Topics

- [11. StatefulSet](./11-statefulset.md)
- [04. Pods](./04-pods.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
- [15. Resources](./15-resources.md)
