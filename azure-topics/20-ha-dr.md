# High Availability & Disaster Recovery

## Theory

### Key Concepts

| Concept | Definition |
|---------|-----------|
| High Availability (HA) | System remains operational with minimal downtime |
| Disaster Recovery (DR) | Ability to recover from major failures |
| RPO | Recovery Point Objective — max acceptable data loss |
| RTO | Recovery Time Objective — max acceptable downtime |
| Failover | Switching from primary to secondary |
| Failback | Returning to primary after recovery |
| SLA | Service Level Agreement (uptime guarantee) |

### SLA Nines

| SLA | Downtime/Year | Downtime/Month |
|-----|---------------|----------------|
| 99% | 3.65 days | 7.3 hours |
| 99.9% | 8.76 hours | 43.8 minutes |
| 99.95% | 4.38 hours | 21.9 minutes |
| 99.99% | 52.6 minutes | 4.38 minutes |
| 99.999% | 5.26 minutes | 26.3 seconds |

---

## Internal Working

### High Availability in Azure ⭐⭐⭐

#### Single Region HA

```
Region: East US
├── Availability Zone 1
│   ├── AKS Node 1
│   ├── PostgreSQL Primary
│   └── Redis Primary
│
├── Availability Zone 2
│   ├── AKS Node 2
│   ├── PostgreSQL Standby (sync)
│   └── Redis Replica
│
└── Availability Zone 3
    ├── AKS Node 3
    └── Redis Replica

Load Balancer distributes across zones
Automatic failover within region
```

#### HA Per Service

| Service | HA Mechanism | SLA |
|---------|-------------|-----|
| AKS | Nodes across 3 AZs, pod replicas | 99.95% (99.99% with AZ) |
| PostgreSQL | Zone-redundant standby (sync) | 99.99% |
| Redis | Zone-redundant replicas | 99.99% |
| Storage | ZRS (3 copies across zones) | 99.99% |
| Service Bus | Premium: zone-redundant | 99.99% |
| Key Vault | Built-in geo-redundancy | 99.99% |
| App Gateway | Zone-redundant deployment | 99.99% |

---

### Disaster Recovery ⭐⭐⭐

#### Multi-Region Active-Passive

```
Primary Region: East US (Active)          DR Region: West US (Passive)
├── AKS Cluster (serving traffic)         ├── AKS Cluster (standby/scaled-down)
├── PostgreSQL Primary (read/write)       ├── PostgreSQL Read Replica (async)
├── Redis (active)                        ├── Redis (geo-replication)
├── Storage (GRS → replicated)            ├── Storage (secondary, read-only)
├── Service Bus (Geo-DR paired)           ├── Service Bus (secondary namespace)
└── Key Vault (auto-replicated)           └── Key Vault (auto-replicated)

Traffic Manager / Front Door
├── Primary: East US endpoints
└── Failover: West US endpoints (activated on failure)
```

#### Multi-Region Active-Active

```
Azure Front Door (Global Load Balancer)
    │
    ├── Region: East US (Active)
    │   ├── AKS (full capacity)
    │   ├── PostgreSQL (local primary)
    │   └── Redis (local)
    │
    └── Region: West Europe (Active)
        ├── AKS (full capacity)
        ├── PostgreSQL (local primary)
        └── Redis (local)

Cosmos DB: Multi-region writes (both regions write)
PostgreSQL: Application-level routing (writes to designated primary)
Front Door: Routes users to nearest region
```

---

### RPO/RTO Scenarios ⭐⭐⭐

| Scenario | RPO | RTO | Solution |
|----------|-----|-----|----------|
| AZ failure | 0 | < 1 min | Zone-redundant deployments |
| Region failure (Active-Passive) | Minutes | 15-60 min | Geo-replication + Traffic Manager failover |
| Region failure (Active-Active) | 0 (Cosmos) / seconds (others) | < 1 min | Multi-region active + Front Door |
| Data corruption | 0 (with PITR) | Minutes | Point-in-Time Restore (PostgreSQL) |
| Accidental deletion | 0 | Minutes | Soft delete, versioning, locks |

---

### Disaster Recovery Per Service

#### PostgreSQL DR
```
Strategy: Geo-Redundant Backup + Read Replica
├── Backup: Automated daily + continuous WAL archiving
│   ├── Retention: 35 days
│   ├── Geo-redundant: replicated to paired region
│   └── Point-in-Time Restore: any second in retention window
│
├── Read Replica (cross-region):
│   ├── Primary: East US (read/write)
│   └── Replica: West US (read-only, async, promotable)
│
└── Failover plan:
    1. Promote read replica to standalone primary
    2. Update connection strings (or use Traffic Manager for DB)
    3. RTO: 5-10 minutes, RPO: seconds (replication lag)
```

#### AKS DR
```
Strategy: Multi-cluster + GitOps
├── Primary Cluster: aks-eastus (serving traffic)
├── DR Cluster: aks-westus (deployed, minimal replicas)
│
├── GitOps (ArgoCD/Flux): Same config deployed to both clusters
├── ACR Geo-Replication: Images available in both regions
│
└── Failover:
    1. Front Door / Traffic Manager routes to DR cluster
    2. Scale up DR cluster (cluster autoscaler)
    3. RTO: 5-15 minutes (depending on scale-up time)
```

#### Storage DR
```
Strategy: GRS/GZRS (Geo-Redundant Storage)
├── Primary: East US (3 copies, zone-redundant)
├── Secondary: West US (3 copies, async replicated)
│
├── Normal: Read/Write to primary
├── Failover: Account failover → secondary becomes primary
│   └── RPO: ~15 minutes (async replication lag)
│
└── RA-GRS: Read Access Geo-Redundant
    └── Secondary is read-only during normal operation
    └── Read from secondary for DR-aware applications
```

---

## Backup Strategy ⭐⭐⭐

```
Azure Backup:
├── PostgreSQL: Automated (35-day PITR, geo-redundant)
├── Cosmos DB: Continuous backup (30-day PITR)
├── Blob Storage: Versioning + soft delete + lifecycle
├── VMs: Azure Backup (daily/weekly snapshots)
├── AKS: Velero for cluster state + persistent volumes
├── Key Vault: Built-in soft delete + purge protection
└── Service Bus: Geo-DR (metadata + messages replicated)
```

---

## Azure Front Door for Global HA ⭐⭐⭐

```
Users (Global)
    │
    ▼
Azure Front Door
├── Anycast IP (closest PoP)
├── Global WAF
├── HTTP/HTTPS Load Balancing
├── Health Probes per backend
│
├── Backend Pool:
│   ├── East US: AKS/App Service (Priority 1, Weight 100)
│   ├── West Europe: AKS/App Service (Priority 1, Weight 100)
│   └── Southeast Asia: AKS/App Service (Priority 2)
│
├── Routing Rules:
│   ├── Latency-based: Route to fastest backend
│   ├── Priority-based: Primary → DR failover
│   └── Weighted: Traffic splitting (canary)
│
└── If East US fails:
    └── Automatically routes to West Europe (< 30 seconds)
```

---

## Resource Locks ⭐⭐

Prevent accidental deletion/modification of critical resources:

| Lock Type | Effect |
|-----------|--------|
| CanNotDelete | Can modify but cannot delete |
| ReadOnly | Cannot modify or delete |

```hcl
# Terraform — lock production database
resource "azurerm_management_lock" "db_lock" {
  name       = "no-delete-db"
  scope      = azurerm_postgresql_flexible_server.main.id
  lock_level = "CanNotDelete"
  notes      = "Production database - cannot be deleted"
}
```

---

## Interview Questions

### Q: How do you design for high availability in Azure?
**A:** HA at every layer:
1. **Compute**: Deploy AKS nodes across 3 Availability Zones, minimum 3 pod replicas
2. **Database**: Zone-redundant PostgreSQL (sync standby in another zone)
3. **Cache**: Zone-redundant Redis (replicas across zones)
4. **Storage**: ZRS (Zone-Redundant Storage)
5. **Messaging**: Premium Service Bus (zone-redundant)
6. **Load Balancing**: Zone-redundant Application Gateway / Load Balancer
7. **DNS**: Azure Front Door for global routing

Result: Survive any single AZ failure with zero downtime.

### Q: Explain your DR strategy for a microservices system.
**A:**
1. **Active-Passive**: Primary region (East US) serves all traffic. DR region (West US) has a warm standby.
2. **Data replication**: PostgreSQL read replica (async, cross-region). Storage GRS. Service Bus Geo-DR.
3. **Deployment**: GitOps ensures same config in both clusters. ACR geo-replicated.
4. **Failover trigger**: Azure Front Door health probes detect primary failure. Routes to DR within 30 seconds.
5. **RPO/RTO**: RPO = seconds (async replication lag). RTO = 5-15 minutes (scale-up + propagation).
6. **Testing**: Regular DR drills (quarterly failover tests).

### Q: RPO vs RTO — explain with a scenario.
**A:**
- **RPO (Recovery Point Objective)**: "How much data can we afford to lose?" If RPO = 5 minutes, then async replication with 5-minute lag is acceptable. If RPO = 0, need synchronous replication (within a region).
- **RTO (Recovery Time Objective)**: "How long can we be down?" If RTO = 15 minutes, the DR cluster must be up and serving within 15 minutes of failure detection.

Example: E-commerce system — RPO = 30 seconds (can lose last 30s of orders), RTO = 5 minutes (must be back within 5 min).
Solution: PostgreSQL read replica (async, seconds of lag) + pre-deployed DR cluster + Front Door automatic failover.

### Q: How do you protect against accidental deletion?
**A:**
1. **Resource Locks**: CanNotDelete on critical resources (database, Key Vault)
2. **Soft Delete**: Enabled on Key Vault (90 days), Storage (14 days)
3. **Versioning**: Blob versioning for object history
4. **RBAC**: Limit who can delete production resources
5. **Terraform**: `prevent_destroy` lifecycle rule on critical resources
6. **Backups**: Even with all protections, regular backups as last resort
7. **Policy**: Azure Policy to enforce locks and soft delete
