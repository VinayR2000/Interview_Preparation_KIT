# Azure Virtual Machines

## Theory

### What is an Azure VM?
An on-demand, scalable computing resource. Equivalent to AWS EC2. You control the OS, runtime, and applications.

### When to Use VMs
- Full OS control needed
- Legacy applications that can't containerize
- Custom software/drivers
- Lift-and-shift migrations
- Specific OS requirements (Windows Server, specific Linux distros)

---

## Internal Working

### VM Components

```
Azure Virtual Machine
├── VM Size (CPU, RAM, temp storage)
│   └── e.g., Standard_D4s_v3 (4 vCPU, 16 GB RAM)
├── OS Disk (Managed Disk)
│   └── e.g., 128 GB Premium SSD
├── Data Disks (optional, Managed Disks)
│   ├── Data Disk 1: 512 GB
│   └── Data Disk 2: 1 TB
├── Network Interface (NIC)
│   ├── Private IP: 10.0.1.4
│   ├── Public IP: 20.x.x.x (optional)
│   └── NSG (attached to NIC or subnet)
├── OS Image
│   └── Ubuntu 22.04 / Windows Server 2022 / Custom
└── Extensions (optional)
    ├── Custom Script Extension
    └── Azure Monitor Agent
```

### VM Size Families

| Series | Optimized For | Use Case |
|--------|--------------|----------|
| B | Burstable | Dev/test, low-traffic web |
| D | General purpose | Spring Boot apps, general workloads |
| E | Memory optimized | In-memory caches, databases |
| F | Compute optimized | Batch processing, gaming |
| L | Storage optimized | Big data, large databases |
| N | GPU | ML training, video rendering |
| M | Memory intensive | SAP HANA, large in-memory DBs |

**Naming convention**: Standard_D4s_v5
- D = Family (general purpose)
- 4 = vCPUs
- s = Premium storage capable
- v5 = Generation/version

### Managed Disks

| Type | IOPS | Use Case |
|------|------|----------|
| Standard HDD | Up to 2,000 | Backups, dev/test |
| Standard SSD | Up to 6,000 | Web servers, light workloads |
| Premium SSD | Up to 20,000 | Production databases, I/O intensive |
| Ultra Disk | Up to 160,000 | Mission-critical, top-tier databases |

---

## High Availability ⭐⭐⭐

### Availability Sets
- Protect against hardware failures within a data center
- **Fault Domain (FD)**: Separate physical rack (power + network)
- **Update Domain (UD)**: VMs restarted sequentially during maintenance

```
Availability Set: as-spring-boot
├── Fault Domain 0 (Rack A)
│   ├── Update Domain 0: VM-1
│   └── Update Domain 1: VM-2
├── Fault Domain 1 (Rack B)
│   ├── Update Domain 2: VM-3
│   └── Update Domain 3: VM-4
└── Fault Domain 2 (Rack C)
    └── Update Domain 4: VM-5

SLA: 99.95% (with 2+ VMs in Availability Set)
```

### Availability Zones ⭐⭐⭐
- Protect against entire data center failures
- Each zone = separate data center with independent power, cooling, networking
- Deploy VMs across multiple zones

```
Region: East US
├── Zone 1: VM-1, VM-2
├── Zone 2: VM-3, VM-4
└── Zone 3: VM-5, VM-6

SLA: 99.99% (with VMs across zones)
```

**Availability Set vs Availability Zone:**

| Feature | Availability Set | Availability Zone |
|---------|-----------------|-------------------|
| Protects against | Rack failure | Data center failure |
| Scope | Within one data center | Across data centers |
| SLA | 99.95% | 99.99% |
| Use when | Legacy/budget workloads | Production workloads |

---

## VM Scale Sets (VMSS) ⭐⭐⭐

Auto-scaling group of identical VMs. Equivalent to AWS Auto Scaling Group.

```
VM Scale Set: vmss-spring-boot
├── Configuration:
│   ├── Image: Custom Spring Boot image
│   ├── Size: Standard_D4s_v5
│   ├── Min instances: 2
│   ├── Max instances: 10
│   └── Autoscale rules:
│       ├── Scale out: CPU > 75% for 5 min → +2 VMs
│       └── Scale in: CPU < 25% for 10 min → -1 VM
│
├── Load Balancer (distributes traffic)
│
├── Instance 1 (Zone 1)
├── Instance 2 (Zone 2)
├── Instance 3 (Zone 3) — (auto-scaled)
└── Instance 4 (Zone 1) — (auto-scaled)
```

### VMSS + Spring Boot Deployment

```
GitHub → Azure DevOps Pipeline
    │
    ▼ (Build)
Custom VM Image (with Java + Spring Boot JAR)
    │
    ▼ (Deploy)
VM Scale Set
    │
    ├── Rolling upgrade (one instance at a time)
    ├── Health probes (only healthy instances receive traffic)
    └── Automatic OS upgrades
```

---

## VM Networking

```
VNet: 10.0.0.0/16
│
├── Subnet: 10.0.1.0/24 (web tier)
│   └── VM-Web
│       ├── NIC → Private IP: 10.0.1.4
│       ├── Public IP: 20.x.x.x
│       └── NSG: Allow 80, 443 from internet
│
├── Subnet: 10.0.2.0/24 (app tier)
│   └── VM-App
│       ├── NIC → Private IP: 10.0.2.4
│       ├── No Public IP
│       └── NSG: Allow 8080 from 10.0.1.0/24 only
│
└── Subnet: 10.0.3.0/24 (db tier)
    └── VM-DB
        ├── NIC → Private IP: 10.0.3.4
        ├── No Public IP
        └── NSG: Allow 5432 from 10.0.2.0/24 only
```

---

## Interview Questions

### Q: VM vs App Service vs AKS — when to use each?
**A:**
| Factor | VM | App Service | AKS |
|--------|----|-----------:|-----|
| Control | Full OS control | App-level only | Container + orchestration |
| Management | You manage everything | Microsoft manages infra | You manage K8s config |
| Scaling | VMSS (minutes) | Built-in autoscale (seconds) | HPA/cluster autoscaler |
| Use case | Legacy apps, custom OS | Simple web apps, APIs | Microservices, complex workloads |
| Cost | Pay for running VMs | Pay for plan | Pay for nodes |
| Deployment | Image/script-based | Git push, CI/CD | kubectl, Helm |

**Decision framework:**
- Need full OS control? → VM
- Simple Spring Boot web app? → App Service
- Multiple microservices with orchestration? → AKS

### Q: Availability Set vs Availability Zone?
**A:**
- **Availability Set**: Protects against rack-level failures within a single data center. Uses fault domains (separate racks) and update domains (sequential maintenance). SLA: 99.95%.
- **Availability Zone**: Protects against entire data center failures. Each zone is a physically separate data center. SLA: 99.99%.
- For production: use Availability Zones. Availability Sets are for legacy scenarios or regions without zones.

### Q: How does VM Scale Set autoscaling work?
**A:** VMSS autoscaling uses rules based on metrics:
1. Define metrics (CPU%, memory, custom metrics)
2. Set thresholds and cooldown periods
3. Scale out: Add instances when threshold exceeded
4. Scale in: Remove instances when demand drops
5. Health probes ensure only healthy instances get traffic
6. Across Availability Zones for high availability
7. Rolling upgrades for zero-downtime deployments
