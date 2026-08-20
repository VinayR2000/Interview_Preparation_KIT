# High Availability and Disaster Recovery ⭐⭐⭐

## Theory

| Concept | Goal | Approach |
|---------|------|----------|
| High Availability | Minimize downtime | Multi-AZ, redundancy, auto-failover |
| Fault Tolerance | Zero downtime | Full redundancy, instant failover |
| Disaster Recovery | Recover from catastrophe | Cross-region, backups, RPO/RTO targets |

---

## Diagram

### Multi-AZ High Availability Architecture

```
┌──────────────────── Region: us-east-1 ────────────────────┐
│                                                            │
│  Route 53 (DNS) ← Health Check                            │
│       ↓                                                    │
│  CloudFront (CDN + WAF)                                   │
│       ↓                                                    │
│  ALB (spans both AZs)                                     │
│       ↓                                                    │
│  ┌─── AZ-1a ─────────┐    ┌─── AZ-1b ─────────┐        │
│  │                     │    │                     │        │
│  │  ECS Tasks (x2)    │    │  ECS Tasks (x2)    │        │
│  │  (Auto Scaling)     │    │  (Auto Scaling)     │        │
│  │                     │    │                     │        │
│  │  RDS Primary        │    │  RDS Standby        │        │
│  │  Redis Primary      │    │  Redis Replica      │        │
│  │                     │    │                     │        │
│  └─────────────────────┘    └─────────────────────┘        │
│                                                            │
│  SQS (Multi-AZ by default)                                │
│  S3 (Multi-AZ by default, 11 nines durability)            │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### Cross-Region Disaster Recovery

```
┌─── Primary: us-east-1 ───┐    ┌─── DR: eu-west-1 ───────┐
│                            │    │                           │
│  Route 53 (Active)         │    │  Route 53 (Standby)      │
│       ↓                    │    │       ↓                   │
│  ALB → ECS → RDS          │───→│  ALB → ECS → RDS         │
│  (serving traffic)         │Async│  (ready or scaled down)  │
│                            │repl.│                           │
│  S3 ──────────────────────→│───→│  S3 (Cross-Region Repl)  │
│  RDS ─────────────────────→│───→│  RDS Read Replica        │
│                            │    │  (promotable to primary)  │
└────────────────────────────┘    └───────────────────────────┘

Failover: Route 53 health check fails → switch to DR region
```

---

## DR Strategies

| Strategy | RPO | RTO | Cost | Description |
|----------|-----|-----|------|-------------|
| Backup & Restore | Hours | Hours | $ | Backups in DR region, restore when needed |
| Pilot Light | Minutes | 10-30 min | $$ | Core infra running, scale up on failover |
| Warm Standby | Seconds-Minutes | Minutes | $$$ | Scaled-down copy always running |
| Active-Active | Zero | Zero | $$$$ | Both regions serving traffic |

### Pilot Light
```
Primary Region (Active):
├── Full infrastructure running
├── All traffic served here
└── Continuous backup/replication to DR

DR Region (Pilot Light):
├── RDS Read Replica (running)
├── AMIs/Container images (ready)
├── Infrastructure defined in Terraform (not running)
└── On failover: Promote RDS, launch ECS tasks, switch DNS
```

---

## AWS Services HA Summary

| Service | HA Mechanism | Recovery |
|---------|-------------|----------|
| EC2 | Multi-AZ via ASG | Auto-replace unhealthy |
| RDS | Multi-AZ (sync standby) | Auto-failover 60-120s |
| ElastiCache | Multi-AZ with replicas | Auto-failover |
| S3 | Multi-AZ by design (11 nines) | Built-in |
| SQS | Multi-AZ by design | Built-in |
| ALB | Multi-AZ by design | Built-in |
| ECS Fargate | Multi-AZ tasks | Auto-replace |
| EKS | Multi-AZ nodes | Pod rescheduling |
| DynamoDB | Multi-AZ (3 replicas) | Built-in |

---

## Interview Questions and Answers

**Q: Design a highly available architecture for a Spring Boot e-commerce application.**
> Route 53 (DNS failover) → CloudFront (CDN + WAF) → ALB (Multi-AZ) → ECS Fargate tasks in 2+ AZs (Auto Scaling) → RDS PostgreSQL Multi-AZ → ElastiCache Redis Multi-AZ. SQS for async order processing (with DLQ). S3 for media storage. CloudWatch alarms + SNS for alerting. All in private subnets. Everything encrypted. Results: survives AZ failure, auto-scales, self-heals.

**Q: What's the difference between Multi-AZ and Cross-Region?**
> Multi-AZ: Survives a single AZ failure within a region. Fast failover (seconds-minutes). Same region, low latency replication. Standard for all production workloads. Cross-Region: Survives an entire region outage. Higher latency replication (async). More complex, more expensive. Used for critical applications requiring disaster recovery from regional catastrophe.

**Q: How would you implement disaster recovery with RPO < 5 minutes and RTO < 30 minutes?**
> Warm Standby strategy: (1) Cross-region RDS Read Replica (async, minimal lag). (2) S3 Cross-Region Replication. (3) Infrastructure in DR region via Terraform (scaled down but running). (4) Route 53 health checks with DNS failover. (5) On failure: promote RDS replica, scale up ECS desired count, Route 53 auto-switches DNS. Total RTO: ~15-30 minutes. RPO: RDS replica lag (typically seconds).

**Q: What happens when an AZ goes down in your architecture?**
> ALB stops routing to unhealthy targets in failed AZ. ECS launches replacement tasks in healthy AZ (desired count maintained). RDS Multi-AZ auto-fails over to standby (60-120s, same endpoint). ElastiCache promotes replica. Application continues with brief impact during failover. No manual intervention needed.

---

## Best Practices

1. **Multi-AZ everything** — baseline for all production services
2. **Health checks everywhere** — Route 53, ALB, ECS, RDS
3. **Automate failover** — no manual intervention required
4. **Regular DR testing** — run DR drills quarterly
5. **Terraform both regions** — infrastructure ready to deploy
6. **Backup verification** — regularly test restore from backups
7. **RTO/RPO documentation** — agreed with stakeholders, tested
8. **Chaos engineering** — intentionally fail components to verify recovery
9. **Runbooks** — documented procedures for manual failover steps
10. **Cost optimization** — Pilot Light for most apps, Active-Active only for critical

---

## Related Topics
- → [01. AWS Fundamentals](./01-aws-fundamentals.md)
- → [08. RDS](./08-rds.md)
- → [16. Route 53 and CloudFront](./16-route53-cloudfront.md)
- → [20. AWS System Design](./20-aws-system-design.md)
