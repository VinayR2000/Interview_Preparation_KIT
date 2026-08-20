# ECS and EKS — Container Services ⭐⭐⭐

## Theory

Run Docker containers on AWS without managing EC2 instances directly.

| Service | Description | Best For |
|---------|-------------|----------|
| ECR | Container Registry (store images) | All container workloads |
| ECS | AWS-native container orchestration | Simpler workloads, AWS-first |
| EKS | Managed Kubernetes | K8s expertise, multi-cloud, complex |
| Fargate | Serverless compute for ECS/EKS | No server management |

---

## Diagram

### Container Deployment Pipeline

```
Developer → Git Push
                ↓
         CI/CD Pipeline (CodePipeline / GitHub Actions)
                ↓
         Docker Build
                ↓
         Push to ECR (Elastic Container Registry)
                ↓
    ┌───────────┴───────────┐
    ↓                       ↓
   ECS                    EKS
(Task Definition)      (Deployment YAML)
    ↓                       ↓
  Fargate              Node Group
(serverless)           (EC2 or Fargate)
    ↓                       ↓
   ALB ←─────────────────→ ALB
    ↓
  Traffic
```

### ECS Architecture

```
┌────────────────── ECS Cluster ──────────────────┐
│                                                   │
│  Service: user-service (desired: 3)              │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐  │
│  │  Task      │ │  Task      │ │  Task      │  │
│  │ (Container)│ │ (Container)│ │ (Container)│  │
│  │  Port 8080 │ │  Port 8080 │ │  Port 8080 │  │
│  └─────┬──────┘ └─────┬──────┘ └─────┬──────┘  │
│        └───────────────┼───────────────┘         │
│                        ↓                         │
│              Target Group (ALB)                   │
│                                                   │
│  Launch Type: Fargate (serverless)               │
│  OR: EC2 (self-managed instances)                │
└───────────────────────────────────────────────────┘
```

### ECS Task Definition (like Docker Compose)
```json
{
  "family": "user-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "arn:aws:iam::123:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123:role/userServiceRole",
  "containerDefinitions": [
    {
      "name": "user-service",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/user-service:latest",
      "portMappings": [{ "containerPort": 8080, "protocol": "tcp" }],
      "environment": [
        { "name": "SPRING_PROFILES_ACTIVE", "value": "production" }
      ],
      "secrets": [
        { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:us-east-1:123:secret:db-password" }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/user-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      }
    }
  ]
}
```

---

## EKS (Managed Kubernetes) ⭐⭐⭐

### EKS Architecture

```
┌────────────────── EKS Cluster ──────────────────┐
│                                                   │
│  Control Plane (AWS Managed):                    │
│  ├── API Server                                  │
│  ├── etcd                                        │
│  ├── Controller Manager                          │
│  └── Scheduler                                   │
│                                                   │
│  Data Plane (Your Nodes):                        │
│  ├── Managed Node Group (EC2)                    │
│  │   ├── Node 1: [Pod, Pod, Pod]                │
│  │   └── Node 2: [Pod, Pod, Pod]                │
│  └── Fargate Profile (serverless pods)           │
│      └── Each pod gets its own microVM           │
│                                                   │
│  Networking:                                      │
│  ├── AWS VPC CNI (pods get VPC IPs)             │
│  ├── ALB Ingress Controller                      │
│  └── Service Mesh (optional: App Mesh)           │
│                                                   │
│  IAM:                                            │
│  └── IRSA (IAM Roles for Service Accounts)       │
└───────────────────────────────────────────────────┘
```

### EKS Key Integrations

| Kubernetes Concept | AWS Integration |
|-------------------|-----------------|
| Ingress | ALB Ingress Controller |
| Service (LoadBalancer) | NLB automatically provisioned |
| PersistentVolume | EBS CSI Driver |
| Secrets | AWS Secrets Manager CSI Driver |
| IAM | IRSA (per-pod IAM roles) |
| Logging | Fluent Bit → CloudWatch |
| Monitoring | CloudWatch Container Insights |
| DNS | ExternalDNS → Route 53 |
| Autoscaling | Cluster Autoscaler / Karpenter |

---

## ECS vs EKS

| Feature | ECS | EKS |
|---------|-----|-----|
| Learning curve | Lower (AWS native) | Higher (Kubernetes) |
| Portability | AWS only | Multi-cloud, on-prem |
| Community | AWS docs | Massive K8s ecosystem |
| Complexity | Simpler | More complex, more powerful |
| Networking | awsvpc mode | VPC CNI, many CNI options |
| Service mesh | App Mesh | Istio, Linkerd, App Mesh |
| Cost | Fargate pricing | Control plane ($0.10/hr) + nodes |
| Best for | Small-medium teams, AWS-only | Large teams, K8s expertise, multi-cloud |

**Recommendation**: If you already know Kubernetes → EKS. If starting fresh and AWS-only → ECS with Fargate is simpler.

---

## Real Project Usage

### Spring Boot on ECS Fargate — Complete Setup

```
1. Dockerfile → Multi-stage build with Java 17
2. Push to ECR
3. Task Definition → Image, CPU/Memory, ports, secrets, logging
4. ECS Service → Desired count, ALB integration, auto-scaling
5. ALB → Target Group, health check /actuator/health
6. Auto Scaling → Target tracking on CPU/request count
7. Secrets → AWS Secrets Manager for DB credentials
8. Logging → CloudWatch Logs via awslogs driver
```

### Dockerfile for Spring Boot
```dockerfile
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
COPY target/app.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

---

## Interview Questions and Answers

**Q: ECS vs EKS — how do you decide?**
> ECS: Simpler, less operational overhead, AWS-native integration, good for teams without K8s expertise. EKS: When you need Kubernetes features (Helm charts, CRDs, operators), multi-cloud portability, or team already knows K8s. Both support Fargate for serverless compute. Most Spring Boot microservices work well on either.

**Q: How do you deploy a new version with zero downtime on ECS?**
> Rolling update: ECS starts new tasks with new image, registers with ALB, waits for health checks, then drains old tasks. Configure: minimumHealthyPercent=100 (always full capacity), maximumPercent=200 (allows double during deployment), deregistration delay=60s. The ALB handles traffic shifting seamlessly.

**Q: How do ECS tasks get AWS credentials (e.g., to access S3)?**
> Through the Task Role (taskRoleArn in task definition). ECS injects temporary credentials via the task metadata endpoint. The AWS SDK automatically discovers these — no configuration in your Spring Boot app. Each service gets its own Task Role with least-privilege permissions.

**Q: What is Fargate and when would you choose it over EC2 launch type?**
> Fargate is serverless compute — you specify CPU/memory per task, AWS manages the infrastructure. Choose Fargate: no server management, pay-per-task, variable workloads. Choose EC2: GPU needs, cost optimization (Reserved Instances), spot instances, specific instance requirements. Most teams: start with Fargate, move specific workloads to EC2 for cost optimization later.

---

## Best Practices

1. **Use Fargate** for simplicity unless you need EC2-specific features
2. **Multi-AZ deployment** — spread tasks/pods across AZs
3. **IRSA** for EKS pod-level IAM (not node role)
4. **Secrets Manager** integration — inject secrets as environment variables
5. **Health checks** — always configure container and ALB health checks
6. **Resource limits** — set appropriate CPU/memory to prevent noisy neighbors
7. **Auto-scaling** — target tracking on CPU and/or request count per target
8. **Immutable deployments** — new image tag for every deployment (never :latest in prod)

---

## Related Topics
- → [05. Load Balancing](./05-load-balancing.md)
- → [06. Auto Scaling](./06-auto-scaling.md)
- → [14. CloudWatch](./14-cloudwatch.md)
- → [17. Terraform](./17-terraform.md)
