# AWS System Design ⭐⭐⭐

## Theory

Combine all AWS services into complete architectures. This is what interview questions test — not individual services, but how you combine them.

---

## Architecture Template

Every AWS system design follows this pattern:

```
DNS (Route 53)
    ↓
CDN (CloudFront + WAF)
    ↓
Load Balancer (ALB)
    ↓
Compute (ECS/EKS/EC2)
    ↓
Cache (ElastiCache Redis)
    ↓
Database (RDS/DynamoDB)
    ↓
Async Processing (SQS/SNS)
    ↓
Storage (S3)
    ↓
Monitoring (CloudWatch)
```

---

## Beginner: Scalable Spring Boot API

```
Route 53 → ALB → ECS Fargate (Auto Scaling)
                       │
                  ┌────┴────┐
                  ↓         ↓
            ElastiCache    RDS PostgreSQL
              (Redis)      (Multi-AZ)

Infrastructure:
├── VPC: 2 AZs, public + private + data subnets
├── ALB: HTTPS, /actuator/health checks
├── ECS: 2-10 tasks, CPU target tracking 60%
├── RDS: Multi-AZ, gp3, automated backups
├── Redis: Cluster mode, Multi-AZ
├── IAM: Task roles with least privilege
├── Secrets Manager: DB credentials
└── CloudWatch: Alarms on 5XX, CPU, memory
```

---

## Intermediate: E-Commerce Backend

```
┌─── Client ───┐
│ React/Angular │
└──────┬────────┘
       ↓
Route 53 → CloudFront → ALB
                          │
            ┌─────────────┼──────────────────┐
            ↓             ↓                  ↓
      User Service   Order Service    Product Service
      (ECS Fargate)  (ECS Fargate)   (ECS Fargate)
            │             │                  │
            ↓             ↓                  ↓
         RDS          ┌───┴───┐           DynamoDB
      (PostgreSQL)    ↓       ↓          (Product Catalog)
                    SQS    ElastiCache
            (Order Queue)   (Redis)
                    ↓
            Payment Service
            (ECS Fargate)
                    ↓
               SNS (Fan-out)
            ┌───┼───────┐
            ↓   ↓       ↓
         Email Inventory Analytics
         SQS   SQS      Kinesis

Storage: S3 (product images, invoices)
Monitoring: CloudWatch + X-Ray (distributed tracing)
Security: WAF + Secrets Manager + KMS + private subnets
```

---

## Advanced: High-Scale Event-Driven System

```
┌─── Global ────────────────────────────────────────────────────┐
│ Route 53 (Latency-based routing)                              │
│    ↓           ↓                                              │
│ us-east-1    eu-west-1                                        │
└───────────────────────────────────────────────────────────────┘

┌─── Region: us-east-1 ────────────────────────────────────────┐
│                                                                │
│  CloudFront + WAF                                             │
│       ↓                                                        │
│  ALB (HTTPS)                                                  │
│       ↓                                                        │
│  EKS Cluster                                                  │
│  ├── API Gateway Service                                      │
│  ├── User Service                                             │
│  ├── Order Service                                            │
│  ├── Payment Service                                          │
│  └── Notification Service                                     │
│                                                                │
│  Data Layer:                                                   │
│  ├── RDS Aurora (PostgreSQL, Multi-AZ, Read Replicas)         │
│  ├── ElastiCache Redis (Cluster Mode)                         │
│  ├── DynamoDB (Session Store, Carts)                          │
│  └── Amazon MSK (Kafka — event streaming)                     │
│                                                                │
│  Async Layer:                                                  │
│  ├── SQS (Task queues with DLQ)                              │
│  ├── SNS (Fan-out notifications)                             │
│  ├── EventBridge (Event routing)                             │
│  └── Step Functions (Saga orchestration)                      │
│                                                                │
│  Storage:                                                      │
│  ├── S3 (Media, documents, backups)                          │
│  └── CloudFront → S3 (Static assets)                         │
│                                                                │
│  Observability:                                               │
│  ├── CloudWatch (Metrics, Logs, Alarms)                      │
│  ├── X-Ray (Distributed tracing)                             │
│  ├── OpenSearch (Log aggregation, search)                    │
│  └── Grafana/Prometheus (K8s metrics)                        │
│                                                                │
│  Security:                                                     │
│  ├── IAM (IRSA for pods)                                     │
│  ├── Secrets Manager (Credentials)                           │
│  ├── KMS (Encryption)                                        │
│  ├── WAF (Attack protection)                                 │
│  └── GuardDuty (Threat detection)                            │
│                                                                │
│  CI/CD:                                                        │
│  ├── GitHub Actions → ECR → EKS                              │
│  ├── Terraform (Infrastructure)                              │
│  ├── Helm (Kubernetes deployments)                           │
│  └── ArgoCD (GitOps)                                         │
└────────────────────────────────────────────────────────────────┘
```

---

## Design Decisions Framework

### When asked "Design X on AWS", follow this:

```
1. Requirements
   ├── Functional: What does it do?
   ├── Non-functional: Scale, latency, availability targets
   └── Constraints: Budget, team size, timeline

2. High-Level Architecture
   ├── Draw the flow: User → DNS → CDN → LB → Compute → Data
   └── Identify components needed

3. Deep Dive
   ├── Database choice: RDS vs DynamoDB vs both
   ├── Compute choice: ECS vs EKS vs Lambda
   ├── Messaging: SQS vs Kafka vs EventBridge
   └── Caching strategy: What to cache, TTL, invalidation

4. Availability & Scaling
   ├── Multi-AZ deployment
   ├── Auto Scaling policies
   ├── Database scaling (read replicas, sharding)
   └── Caching layer

5. Security
   ├── Network: VPC, private subnets, SGs
   ├── Access: IAM roles, least privilege
   ├── Data: Encryption at rest + in transit
   └── Secrets: Secrets Manager

6. Monitoring & Operations
   ├── Metrics, logs, traces
   ├── Alarms and alerting
   ├── CI/CD pipeline
   └── Disaster recovery plan
```

---

## Interview Questions and Answers

**Q: Design a notification system on AWS.**
> Event source (order placed) → SNS topic (fan-out) → SQS queues per channel (email-queue, push-queue, sms-queue) → Worker services (ECS) consume from each queue → Send via SES (email), Pinpoint (push), SNS (SMS). DLQ for failed messages. CloudWatch alarms on DLQ depth and queue age. Advantages: channels decouple, scale independently, failures isolated.

**Q: How would you migrate a monolithic Spring Boot app to microservices on AWS?**
> Strangler Fig pattern: (1) Deploy monolith on ECS. (2) Add ALB with path-based routing. (3) Extract one service at a time (start with highest-change modules). (4) New service gets its own ECS service, database, SQS queue. (5) ALB routes new paths to new service, rest to monolith. (6) Gradually extract until monolith is empty. Use feature flags for safe cutover.

**Q: Design for 10,000 requests/second with 99.9% availability.**
> CloudFront (edge caching) → ALB (cross-AZ) → ECS Fargate 20+ tasks (auto-scaling on request count) → ElastiCache Redis cluster (cache DB results, cache-hit ratio >95%) → RDS Aurora with read replicas (handle cache misses). Connection pooling (HikariCP). SQS for async operations. Multi-AZ everything. Result: most requests served from cache (sub-ms), DB handles <500 rps (5% cache miss of 10K).

---

## AWS Service Selection Cheat Sheet

| Need | Service |
|------|---------|
| Run containers | ECS Fargate / EKS |
| Relational DB | RDS PostgreSQL / Aurora |
| NoSQL / key-value | DynamoDB |
| Cache | ElastiCache Redis |
| File storage | S3 |
| Message queue | SQS |
| Pub/sub | SNS |
| Event routing | EventBridge |
| Event streaming | MSK (Kafka) |
| Serverless compute | Lambda |
| DNS | Route 53 |
| CDN | CloudFront |
| Load balancing | ALB |
| Container registry | ECR |
| Secrets | Secrets Manager |
| Monitoring | CloudWatch |
| IaC | Terraform |
| CI/CD | GitHub Actions + CodeDeploy |

---

## Best Practices

1. **Start simple, evolve** — don't over-engineer day one
2. **Use managed services** — less ops, more building
3. **Design for failure** — every component can fail
4. **Cache aggressively** — reduce DB load, improve latency
5. **Async where possible** — SQS for non-real-time operations
6. **Observe everything** — you can't fix what you can't see
7. **Security by default** — encrypt, least privilege, private subnets
8. **Cost awareness** — right-size, use savings plans, auto-scale down
9. **Immutable infrastructure** — replace, don't patch
10. **Document decisions** — ADRs for "why" not just "what"

---

## Related Topics
- → All previous AWS topics
- → System Design Topics folder
- → Microservices Topics folder
