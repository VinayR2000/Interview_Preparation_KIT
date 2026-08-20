# RDS — Relational Database Service ⭐⭐⭐

## Theory

RDS is a managed relational database. AWS handles: hardware provisioning, patching, backups, failover, scaling. You manage: schema, queries, access control, performance tuning.

---

## Diagram

### Multi-AZ Architecture ⭐⭐⭐

```
┌────────────────── Region: us-east-1 ──────────────────┐
│                                                        │
│  ┌──── AZ-1a ─────┐        ┌──── AZ-1b ─────┐      │
│  │                 │        │                 │      │
│  │  ┌───────────┐ │  Sync  │  ┌───────────┐ │      │
│  │  │    RDS    │─┼────────┼─→│    RDS    │ │      │
│  │  │ (Primary) │ │ Repli- │  │ (Standby) │ │      │
│  │  │           │ │ cation │  │           │ │      │
│  │  │ Writes +  │ │        │  │ No traffic│ │      │
│  │  │ Reads     │ │        │  │ (failover │ │      │
│  │  └───────────┘ │        │  │  only)    │ │      │
│  │                 │        │  └───────────┘ │      │
│  └─────────────────┘        └─────────────────┘      │
│                                                        │
│  Failover: Automatic (60-120 seconds)                 │
│  DNS endpoint stays the same                          │
└────────────────────────────────────────────────────────┘
```

### Multi-AZ vs Read Replicas ⭐⭐⭐

| Feature | Multi-AZ | Read Replica |
|---------|----------|--------------|
| Purpose | High Availability | Read scalability |
| Replication | Synchronous | Asynchronous |
| Failover | Automatic (60-120s) | Manual promotion |
| Reads | Primary only | Yes (read traffic) |
| Writes | Primary only | No (read-only) |
| Cross-region | No (same region) | Yes |
| Endpoint | Same DNS (failover transparent) | Separate endpoint |
| Cost | 2x (standby instance) | Per replica |

```
Write-heavy: Multi-AZ (HA, same endpoint)
Read-heavy:  Read Replicas (up to 15 replicas)
Both:        Multi-AZ Primary + Read Replicas

Spring Boot
├── DataSource (primary endpoint) → Writes
└── ReadOnlyDataSource (replica endpoint) → Reads
```

---

## Code

### Spring Boot + RDS Configuration

```yaml
# application-production.yml
spring:
  datasource:
    url: jdbc:postgresql://mydb.cluster-xxx.us-east-1.rds.amazonaws.com:5432/myapp
    username: ${DB_USERNAME}       # From Secrets Manager
    password: ${DB_PASSWORD}       # From Secrets Manager
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate            # NEVER use 'update' or 'create' in production
```

### Read/Write Splitting
```java
@Configuration
public class DataSourceConfig {
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        // RDS primary endpoint (writes + reads)
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://primary.cluster-xxx.rds.amazonaws.com:5432/myapp")
            .build();
    }

    @Bean("readOnlyDataSource")
    public DataSource readOnlyDataSource() {
        // RDS reader endpoint (read replicas)
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://reader.cluster-xxx.rds.amazonaws.com:5432/myapp")
            .build();
    }
}
```

---

## Real Project Usage

### RDS Best Configuration for Production

```
Engine: PostgreSQL 15
Instance: db.r6g.large (2 vCPU, 16 GB RAM)
Storage: gp3, 100 GB, auto-scaling to 500 GB
Multi-AZ: YES (always for production)
Read Replicas: 1-2 (for read-heavy workloads)
Backups: 7-day retention, preferred window: 3:00-4:00 UTC
Encryption: AES-256 (KMS)
Security Group: Allow 5432 only from app-sg
Subnet Group: Private/data subnets across 2+ AZs
Parameter Group: Custom (tuned for application)
```

### Connection from EKS/ECS
```
Application (EKS Pod) → Security Group allows 5432 → RDS (private subnet)
                      → IAM Auth (optional, token-based)
                      → SSL/TLS connection enforced
                      → Secrets Manager for credentials
```

---

## Interview Questions and Answers

**Q: Explain Multi-AZ vs Read Replicas. When would you use each?**
> Multi-AZ: For high availability. Synchronous replication to a standby that takes over automatically during failure (same DNS endpoint). Standby handles NO read traffic. Read Replicas: For read scalability. Asynchronous replication, separate endpoints. Application reads from replicas, writes to primary. Can be cross-region. Use both: Multi-AZ for HA + Read Replicas for scaling reads.

**Q: How do you handle database credentials in a Spring Boot app on AWS?**
> Store in AWS Secrets Manager with auto-rotation enabled. Spring Boot reads via AWS SDK or Spring Cloud AWS integration. The ECS task/EKS pod's IAM role grants secretsmanager:GetSecretValue permission. Never hardcode in application.properties or environment variables visible in task definitions.

**Q: Your database is slow. How do you troubleshoot on RDS?**
> 1. Check CloudWatch metrics: CPU, FreeableMemory, ReadIOPS, WriteIOPS
> 2. Enable Performance Insights — identify top SQL queries by wait time
> 3. Check connection count vs max_connections
> 4. Look for long-running queries (pg_stat_activity)
> 5. Check if Read Replicas can offload read traffic
> 6. Verify proper indexing (EXPLAIN ANALYZE)
> 7. Consider instance class upgrade or read replicas

---

## Best Practices

1. **Always Multi-AZ** for production (automated failover)
2. **Private subnets** — never expose RDS to internet
3. **Secrets Manager** for credentials with auto-rotation
4. **Encryption** at rest (KMS) and in transit (SSL)
5. **Automated backups** — 7+ day retention with point-in-time recovery
6. **Performance Insights** — enabled for query analysis
7. **Connection pooling** — HikariCP in Spring Boot, properly sized
8. **Monitoring**: CPU < 70%, FreeableMemory > 25%, connections < 80% max

---

## Related Topics
- → [09. DynamoDB](./09-dynamodb.md)
- → [10. ElastiCache](./10-elasticache.md)
- → [15. AWS Security](./15-aws-security.md)
