# 36. Production Architecture ⭐⭐⭐

---

## Theory

Production Kubernetes architecture requires high availability, reliability, security, and observability across all components.

### High Availability

```
HA at every layer:
  Control Plane: 3+ nodes, multi-AZ
  Worker Nodes:  Multiple nodes across AZs
  Application:   Multiple replicas with anti-affinity
  Storage:       Replicated databases, multi-AZ volumes
  Networking:    Multi-AZ load balancers, redundant DNS

Single points of failure to eliminate:
  ✗ Single control plane node
  ✗ Single replica deployment
  ✗ Single AZ
  ✗ Single database instance
  ✗ No health probes
```

### Multi-Node Cluster

```
Production cluster sizing:

Control Plane (EKS managed):
  - AWS manages 3+ API Servers across AZs
  - etcd: 3-node cluster (AWS managed)

Worker Nodes:
  - System node group: 2-3 nodes (monitoring, logging, ingress)
  - Application node group: 3-20+ nodes (auto-scaled)
  - Specialized groups: GPU nodes, high-memory nodes (optional)

Right-sizing:
  - Don't use very large nodes (blast radius too high)
  - Don't use very small nodes (overhead too high)
  - m5.xlarge or m5.2xlarge is common sweet spot
```

### Multi-AZ

```
┌────────────────────────────────────────────────────────┐
│                      VPC                                 │
│                                                          │
│  ┌──── AZ-a ────┐  ┌──── AZ-b ────┐  ┌──── AZ-c ──┐ │
│  │ Nodes: 3     │  │ Nodes: 3     │  │ Nodes: 3   │ │
│  │ Pods spread  │  │ Pods spread  │  │ Pods spread │ │
│  │ ALB target   │  │ ALB target   │  │ ALB target  │ │
│  │ NAT Gateway  │  │ NAT Gateway  │  │ NAT Gateway │ │
│  └──────────────┘  └──────────────┘  └─────────────┘ │
│                                                          │
│  Pod anti-affinity + topology spread = even across AZs  │
└────────────────────────────────────────────────────────────┘
```

### Load Balancing

```
External: ALB/NLB → Ingress Controller → Services → Pods
Internal: Service (ClusterIP) → kube-proxy → Pods

Production pattern:
  Internet → Route53 → ALB (multi-AZ) → 
  Ingress Controller → K8s Service → Pods (spread across AZs)
```

### Auto Scaling

```
Three levels combined:
  1. HPA: Scale pods based on CPU/memory/custom metrics
  2. Karpenter/CA: Scale nodes when pods can't be scheduled
  3. Fargate: Serverless pods (for burst capacity)

Scaling strategy:
  - Base capacity: Managed Node Group (always available)
  - Burst capacity: Karpenter with spot instances
  - Predictive: Pre-scale before known traffic patterns
```

### Rolling Deployment

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    spec:
      terminationGracePeriodSeconds: 60
      containers:
      - name: app
        lifecycle:
          preStop:
            exec:
              command: ["sh", "-c", "sleep 10"]
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
```

### Canary Deployment

```
Production canary with Argo Rollouts:
  1. Deploy canary (10% traffic)
  2. Monitor metrics (error rate, latency)
  3. Automated analysis: pass/fail
  4. If pass: promote to 100%
  5. If fail: automatic rollback
```

### Service Discovery

```
Internal: K8s DNS (CoreDNS)
  order-service.production.svc.cluster.local

External: AWS Route53 + ExternalDNS
  api.example.com → ALB → Ingress → Service → Pods
  
  ExternalDNS controller:
  - Watches Ingress/Service objects
  - Creates/updates Route53 records automatically
```

### Secrets Management

```
Production secrets strategy:
  1. AWS Secrets Manager: Store secrets
  2. External Secrets Operator: Sync to K8s Secrets
  3. IRSA: Pod access to AWS services
  4. Sealed Secrets: Encrypt for GitOps

Never:
  - Store secrets in Git (even base64)
  - Use default ServiceAccount for everything
  - Share secrets across teams/environments
```

### Monitoring

```
Production monitoring stack:
  Metrics:  Prometheus + Grafana + Alertmanager
  Logs:     Fluent Bit → CloudWatch/Elasticsearch
  Traces:   OpenTelemetry → Jaeger/X-Ray
  Events:   K8s events → persistent store

Key alerts:
  - Pod CrashLoopBackOff > 5 min
  - Node NotReady
  - CPU/Memory > 80% sustained
  - 5xx error rate > 1%
  - API Server latency > 1s
  - etcd leader changes
  - Certificate expiry < 30 days
```

### Logging

```
Centralized logging:
  Fluent Bit (DaemonSet) → CloudWatch Logs / Elasticsearch
  
Structured logging format (JSON):
  {"timestamp":"2024-01-15T10:30:00Z","level":"ERROR","service":"order-service",
   "traceId":"abc123","message":"Database connection failed","exception":"..."}
```

### Disaster Recovery

```
DR strategy:
  1. etcd backups: Every 30 minutes → S3 (cross-region)
  2. GitOps: All manifests in Git (recreate from scratch)
  3. Database: Multi-AZ RDS, cross-region read replicas
  4. Volumes: EBS snapshots → cross-region copy
  5. DNS: Route53 health checks → failover to DR region

RTO (Recovery Time Objective): Time to recover
RPO (Recovery Point Objective): Acceptable data loss

Typical targets:
  Critical services: RTO < 1h, RPO < 5 min
  Standard services: RTO < 4h, RPO < 1h
```

### Backup

```
What to back up:
  1. etcd: Cluster state (snapshots)
  2. PersistentVolumes: Data (EBS snapshots)
  3. Databases: RDS automated backups
  4. Git repos: All manifests (already versioned)
  5. Secrets: AWS Secrets Manager (replicated)

Velero:
  - Open-source K8s backup/restore tool
  - Backs up K8s resources + PV data
  - Supports scheduled backups
  - Restores to same or different cluster
```

### Security

```
Production security checklist:
  □ RBAC with least privilege
  □ NetworkPolicy (default deny)
  □ Pod Security Standards (restricted)
  □ SecurityContext (non-root, read-only, drop ALL)
  □ Image scanning (Trivy, Snyk)
  □ Private registries only
  □ Secrets encrypted at rest
  □ IRSA for AWS access
  □ Private API Server endpoint
  □ Audit logging enabled
  □ Regular security updates
  □ PodDisruptionBudgets
```

---

## Diagram

```
┌──────────────────── PRODUCTION ARCHITECTURE ──────────────────────┐
│                                                                     │
│  Internet → Route53 → ALB (multi-AZ, WAF)                        │
│                          │                                         │
│  ┌───────────────────────┼─────────────────────────────────────┐  │
│  │           EKS CLUSTER │                                      │  │
│  │                       ▼                                      │  │
│  │  ┌─────────── Ingress Controller ──────────────┐           │  │
│  │  └──────┬──────────────────┬────────────────────┘           │  │
│  │         │                  │                                │  │
│  │  ┌──────┴──────┐   ┌──────┴──────┐                        │  │
│  │  │ API Service │   │ Web Service │                        │  │
│  │  │ (3 replicas)│   │ (3 replicas)│                        │  │
│  │  └──────┬──────┘   └─────────────┘                        │  │
│  │         │                                                   │  │
│  │  ┌──────┴──────┐   ┌─────────────┐                        │  │
│  │  │ Order Svc   │   │ Payment Svc │                        │  │
│  │  │ (HPA: 2-10) │   │ (HPA: 2-10) │                        │  │
│  │  └──────┬──────┘   └──────┬──────┘                        │  │
│  │         │                  │                                │  │
│  │  ┌──────┴──────────────────┴──────┐                        │  │
│  │  │         Kafka (Strimzi)         │                        │  │
│  │  │         (3 brokers)             │                        │  │
│  │  └────────────────────────────────┘                        │  │
│  │                                                             │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐     │  │
│  │  │ Prometheus  │  │ Fluent Bit   │  │ Cert-Manager │     │  │
│  │  │ Grafana     │  │ (DaemonSet)  │  │              │     │  │
│  │  │ Alertmanager│  │              │  │              │     │  │
│  │  └─────────────┘  └──────────────┘  └──────────────┘     │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  External Services:                                                │
│  ┌──────────┐ ┌──────────┐ ┌───────────────┐ ┌──────────────┐   │
│  │ RDS      │ │ ElastiC. │ │ Secrets Mgr   │ │ S3           │   │
│  │(Multi-AZ)│ │ (Redis)  │ │               │ │              │   │
│  └──────────┘ └──────────┘ └───────────────┘ └──────────────┘   │
└───────────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: How would you design a production Kubernetes architecture?

**A:** Key components:
- **HA:** Multi-AZ, multiple replicas, pod anti-affinity, PDB
- **Networking:** VPC with private subnets, ALB, NetworkPolicy
- **Security:** RBAC, SecurityContext, IRSA, private API endpoint
- **Scaling:** HPA + Karpenter, right-sized resources
- **Observability:** Prometheus/Grafana (metrics), Fluent Bit (logs), tracing
- **CI/CD:** GitOps with Argo CD, Helm charts
- **DR:** etcd backups, cross-region replication, Velero

### Q2: How do you ensure zero-downtime deployments?

**A:**
1. Multiple replicas (min 3)
2. RollingUpdate with maxSurge=1, maxUnavailable=0
3. Readiness probes (only route traffic to ready pods)
4. preStop hook (sleep 10s for LB deregistration)
5. Graceful shutdown (finish in-flight requests)
6. PodDisruptionBudget (maintain minimum during maintenance)
7. Pod anti-affinity (survive node failure)

### Q3: How do you handle secrets in a production K8s cluster?

**A:** Multi-layer approach:
1. **Storage:** AWS Secrets Manager (not K8s Secrets directly)
2. **Sync:** External Secrets Operator syncs to K8s Secrets
3. **Encryption:** Enable encryption at rest in etcd
4. **Access:** RBAC restricts who can read secrets
5. **Pod access:** Mount as files (not env vars, less exposure)
6. **GitOps:** Use Sealed Secrets or External Secrets CRs (safe for Git)
7. **Rotation:** Automated via Secrets Manager rotation

---

## Best Practices

1. **Multi-AZ everything** — nodes, pods, databases, load balancers
2. **GitOps for all deployments** — Argo CD, version-controlled
3. **Defense in depth security** — RBAC + NetworkPolicy + SecurityContext
4. **Observability from day one** — metrics, logs, traces, alerts
5. **Automated scaling** — HPA + Karpenter, test scaling behavior
6. **Disaster recovery tested** — regular DR drills, backup restores
7. **Progressive rollouts** — canary with automated analysis
8. **Resource governance** — ResourceQuota, LimitRange per namespace
9. **Cost optimization** — right-size, spot instances, scale-to-zero non-prod
10. **Documentation** — runbooks, architecture diagrams, incident playbooks

---

## Related Topics

- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
- [34. Kubernetes + CI/CD](./34-kubernetes-cicd.md)
- [19. RBAC & Security](./19-rbac-and-security.md)
- [37. Advanced Production Topics](./37-advanced-production-topics.md)
