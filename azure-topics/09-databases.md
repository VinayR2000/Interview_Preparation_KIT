# Azure Databases

## Theory

### Azure Database Options

| Service | Type | AWS Equivalent | Use Case |
|---------|------|----------------|----------|
| Azure SQL Database | Managed SQL Server | RDS SQL Server | Enterprise apps, .NET |
| Azure Database for PostgreSQL | Managed PostgreSQL | RDS PostgreSQL | Spring Boot, open-source |
| Azure Database for MySQL | Managed MySQL | RDS MySQL | WordPress, PHP apps |
| Azure Cosmos DB | Multi-model NoSQL | DynamoDB | Global distribution, low latency |
| Azure Cache for Redis | In-memory cache | ElastiCache Redis | Caching, session store |

---

## Azure Database for PostgreSQL ⭐⭐⭐

### Deployment Option: Flexible Server (Current)
The recommended option. Provides full PostgreSQL compatibility with managed infrastructure.

```
Azure Database for PostgreSQL — Flexible Server
├── Engine: PostgreSQL 16
├── Compute: Standard_D4ds_v5 (4 vCPUs, 16 GB RAM)
├── Storage: 512 GB (auto-grow enabled)
├── High Availability: Zone-redundant
│   ├── Primary: Zone 1
│   └── Standby: Zone 2 (synchronous replication)
├── Backups: 35-day retention, geo-redundant
├── Networking: Private access (VNet integration)
└── Read Replicas: 5 (async replication)
```

### High Availability ⭐⭐⭐

```
Zone-Redundant HA:
┌─────────────────┐    ┌─────────────────┐
│   Zone 1        │    │   Zone 2        │
│                 │    │                 │
│ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │  Primary    │ │    │ │  Standby    │ │
│ │  (Read/Write│ │────│►│  (Sync Rep) │ │
│ │   + WAL)    │ │    │ │             │ │
│ └─────────────┘ │    │ └─────────────┘ │
│                 │    │                 │
│ ┌─────────────┐ │    │ ┌─────────────┐ │
│ │  Storage    │ │    │ │  Storage    │ │
│ └─────────────┘ │    │ └─────────────┘ │
└─────────────────┘    └─────────────────┘

Failover: Automatic (60-120 seconds)
Data loss: Zero (synchronous replication)
```

### Read Replicas ⭐⭐
```
Primary (East US) — Read/Write
    │
    ├── Async replication
    │
    ├── Read Replica 1 (East US) — Read-only
    ├── Read Replica 2 (East US) — Read-only
    └── Read Replica 3 (West Europe) — Read-only (cross-region)

Spring Boot Configuration:
├── Write operations → Primary endpoint
└── Read operations → Replica endpoint (read scaling)
```

### Networking ⭐⭐⭐

**Private Access (Recommended for production):**
```
VNet: vnet-prod (10.0.0.0/16)
├── Subnet: snet-app (10.0.1.0/24)
│   └── Spring Boot (App Service / AKS)
│
├── Subnet: snet-db (10.0.3.0/24) — Delegated to PostgreSQL
│   └── PostgreSQL Flexible Server (10.0.3.4)
│       └── Private DNS Zone: privatelink.postgres.database.azure.com
│
└── NSG on snet-db: Allow 5432 from snet-app only

Connection string:
jdbc:postgresql://mydb.postgres.database.azure.com:5432/appdb
(resolves to private IP 10.0.3.4 within VNet)
```

### Spring Boot + PostgreSQL Configuration

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://mydb.postgres.database.azure.com:5432/appdb
    # Using Managed Identity (passwordless)
    # OR using Key Vault reference for password
  cloud:
    azure:
      credential:
        managed-identity-enabled: true
```

### Passwordless Authentication with Managed Identity ⭐⭐⭐
```
Spring Boot (App Service)
    │
    ├── Managed Identity
    │
    ▼ (Request token from Entra ID)
Microsoft Entra ID
    │
    ▼ (Access token)
Azure PostgreSQL
    │
    └── Validates token, grants access
        (No password stored anywhere!)
```

---

## Azure SQL Database ⭐⭐

### Purchasing Models

| Model | Description | Use Case |
|-------|-------------|----------|
| DTU | Bundled CPU + IO + Memory | Simpler, predictable workloads |
| vCore | Choose CPU, memory, storage independently | Flexible, similar to VM sizing |

### Service Tiers (vCore)

| Tier | Use Case |
|------|----------|
| General Purpose | Most production workloads |
| Business Critical | Low latency, high IOPS, built-in HA replicas |
| Hyperscale | Very large databases (up to 100 TB), fast scaling |

### Elastic Pools
Share resources across multiple databases:
```
Elastic Pool: pool-ecommerce (200 vCores)
├── Database: orders-db (using 50 vCores right now)
├── Database: users-db (using 20 vCores right now)
├── Database: products-db (using 10 vCores right now)
└── Database: analytics-db (using 5 vCores right now)

Total used: 85/200 vCores
Benefit: Pay for pool, not each database at peak
```

---

## Azure Cosmos DB ⭐⭐⭐

### What is Cosmos DB?
A globally distributed, multi-model NoSQL database. Provides single-digit millisecond latency, automatic scaling, and multiple consistency levels.

### Core Concepts

```
Cosmos DB Account
├── Database: ecommerce
│   ├── Container: orders (≈ table/collection)
│   │   ├── Partition Key: /customerId
│   │   ├── Items (JSON documents)
│   │   │   ├── {id: "1", customerId: "C1", total: 99.99}
│   │   │   └── {id: "2", customerId: "C2", total: 149.99}
│   │   └── Throughput: 4000 RU/s
│   │
│   └── Container: products
│       ├── Partition Key: /category
│       └── Throughput: 2000 RU/s
│
└── Replication:
    ├── East US (Write region)
    ├── West Europe (Read region)
    └── Southeast Asia (Read region)
```

### Request Units (RU/s) ⭐⭐⭐
- Unit of throughput in Cosmos DB
- 1 RU = reading a 1 KB document by ID
- All operations cost RUs (reads, writes, queries)
- You provision RU/s (or use autoscale / serverless)

```
Operation costs (approximate):
├── Point read (1 KB by ID): 1 RU
├── Write (1 KB): 5 RUs
├── Query (returns 5 docs): 10-50 RUs (depends on complexity)
└── Cross-partition query: Higher RU cost
```

### Partition Key ⭐⭐⭐
The most critical design decision in Cosmos DB.

```
Good partition key: /customerId
├── Distributes data evenly
├── Queries within one customer are efficient (single partition)
└── High cardinality (many distinct values)

Bad partition key: /country
├── Hot partition (US has 80% of data)
├── Uneven distribution
└── Throttling on busy partitions
```

### Consistency Levels ⭐⭐⭐

| Level | Guarantee | Performance | Use Case |
|-------|-----------|-------------|----------|
| Strong | Latest write always visible | Highest latency | Financial transactions |
| Bounded Staleness | Reads lag by max K versions or T time | High latency | Leaderboards |
| Session | Read your own writes | Medium | Shopping carts, user profiles |
| Consistent Prefix | Reads never see out-of-order writes | Low latency | Social feeds |
| Eventual | No ordering guarantee | Lowest latency | Analytics, counters |

**Default: Session consistency** (most common for web apps)

### Global Distribution ⭐⭐⭐
```
Cosmos DB — Multi-region writes:
├── East US (Read + Write)
├── West Europe (Read + Write)
└── Southeast Asia (Read + Write)

Any region can accept writes → <10ms write latency globally
Conflict resolution: Last-writer-wins or custom merge policy
```

### When to Choose Cosmos DB vs PostgreSQL ⭐⭐⭐

| Factor | Cosmos DB | PostgreSQL |
|--------|-----------|------------|
| Data model | Schema-less JSON | Relational (tables, schemas) |
| Scaling | Horizontal (partitioning) | Vertical (bigger server) + read replicas |
| Global distribution | Built-in multi-region | Manual (read replicas per region) |
| Latency | Single-digit ms guaranteed | Depends on query complexity |
| Transactions | Within partition key | Full ACID across tables |
| Joins | Not supported | Full SQL joins |
| Cost model | Per RU/s (can be expensive) | Per compute (more predictable) |
| Schema changes | Flexible (add fields anytime) | Migrations needed |
| Best for | High-throughput, global, schema-flexible | Complex queries, relationships, transactions |

**Decision**: If you need joins, complex transactions, or relational integrity → PostgreSQL. If you need global distribution, massive scale, or flexible schema → Cosmos DB.

---

## Azure Cache for Redis ⭐⭐

### Architecture
```
Spring Boot Application
    │
    ├── Cache hit? → Return cached response (fast)
    │
    ├── Cache miss? → Query PostgreSQL → Store in Redis → Return
    │
    ▼
Azure Cache for Redis
├── Tier: Premium (P1)
├── Size: 6 GB
├── Clustering: Enabled (3 shards)
├── Replication: Zone-redundant
├── Persistence: AOF (append-only file)
└── Private Endpoint: 10.0.4.20

Use Cases:
├── Session caching (Spring Session)
├── API response caching
├── Rate limiting
├── Distributed locks
├── Pub/Sub messaging
└── Leaderboards (sorted sets)
```

### Tiers

| Tier | Features |
|------|----------|
| Basic | Single node, no SLA, dev/test only |
| Standard | Replicated (primary + replica), 99.9% SLA |
| Premium | Clustering, persistence, VNet, zone redundancy |
| Enterprise | Redis Enterprise features, Active-Active geo |

### Spring Boot + Redis
```yaml
spring:
  data:
    redis:
      host: myredis.redis.cache.windows.net
      port: 6380
      ssl:
        enabled: true
      # Password from Key Vault or use Managed Identity (preview)
```

---

## Interview Questions

### Q: When would you choose Cosmos DB over PostgreSQL?
**A:** Choose Cosmos DB when:
1. Need global distribution with multi-region writes
2. Need guaranteed single-digit millisecond latency at any scale
3. Data is schema-flexible (varying JSON structures)
4. Need horizontal scaling to millions of operations/second
5. Data access patterns are key-based (no complex joins)

Choose PostgreSQL when:
1. Need complex SQL queries with joins
2. Need full ACID transactions across tables
3. Data is highly relational
4. Need schema integrity and constraints
5. Cost predictability is important

### Q: How do you achieve high availability for Azure PostgreSQL?
**A:** Zone-redundant HA:
- Primary in Zone 1, synchronous standby in Zone 2
- Automatic failover (60-120 seconds) on primary failure
- Zero data loss (synchronous replication)
- Read replicas for read scaling (async, up to 5 replicas)
- Geo-redundant backups for cross-region DR
- Connection string stays the same after failover

### Q: Explain Cosmos DB partition key design.
**A:** The partition key determines how data is distributed across physical partitions. Good partition key:
1. High cardinality (many distinct values) — distributes evenly
2. Even distribution — no "hot" partition
3. Matches query patterns — queries within one partition are cheapest
4. Example: `/customerId` for an order system (each customer's orders on one partition, queries per customer are single-partition)

Bad partition key: `/status` (only 3 values: pending/completed/cancelled → hot partitions)

### Q: How does Azure Cache for Redis fit in a microservices architecture?
**A:** Redis serves multiple purposes:
1. **Response caching**: Cache expensive query results (cache-aside pattern)
2. **Session store**: Shared session across multiple service instances
3. **Rate limiting**: Track request counts per user/IP
4. **Distributed locks**: Coordinate between service instances
5. **Pub/Sub**: Lightweight event notification between services

Architecture: Services check Redis first → if cache miss → query database → store in Redis with TTL → next request gets cached response.
