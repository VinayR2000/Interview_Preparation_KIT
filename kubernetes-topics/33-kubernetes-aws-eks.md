# 33. Kubernetes + AWS / EKS ⭐⭐⭐

---

## Theory

**Amazon EKS (Elastic Kubernetes Service)** is a managed Kubernetes service that handles control plane operations while you manage worker nodes.

### What is EKS?

```
EKS = AWS-managed Kubernetes control plane

AWS manages:
  - API Server (HA, multi-AZ)
  - etcd (HA, encrypted, backed up)
  - Control plane scaling
  - K8s version upgrades
  - Security patches

You manage:
  - Worker nodes (or use Fargate)
  - Application deployments
  - Networking configuration
  - IAM and security
  - Add-ons (CoreDNS, kube-proxy, CNI)
```

### EKS Control Plane

```
EKS Control Plane:
  - Runs in AWS-managed VPC (not yours)
  - Multi-AZ (at least 2 API Server instances)
  - etcd: Multi-AZ, encrypted, auto-backed up
  - Accessible via: public endpoint, private endpoint, or both
  - Managed upgrades (one minor version at a time)
```

### Worker Nodes

```
Worker node options:
  1. Managed Node Groups: AWS manages EC2 instances
  2. Self-managed nodes: You manage EC2 instances
  3. Fargate: Serverless (no nodes to manage)

Managed Node Groups (recommended):
  - Auto-provisioning of EC2 instances
  - Automatic OS updates and patching
  - Graceful node draining during updates
  - Integration with ASG (Auto Scaling Groups)
```

### Managed Node Groups

```yaml
# eksctl config
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig
metadata:
  name: my-cluster
  region: us-east-1
managedNodeGroups:
- name: app-nodes
  instanceType: m5.xlarge
  minSize: 2
  maxSize: 10
  desiredCapacity: 3
  volumeSize: 100
  labels:
    role: application
  tags:
    environment: production
- name: system-nodes
  instanceType: m5.large
  minSize: 2
  maxSize: 4
  labels:
    role: system
  taints:
  - key: dedicated
    value: system
    effect: NoSchedule
```

### Fargate

```
EKS Fargate: Serverless compute for pods

How it works:
  - Each pod runs on its own isolated micro-VM
  - No EC2 instances to manage
  - Pay per pod (CPU + memory used)
  - Pod scheduled by Fargate profile (namespace + labels)

Fargate Profile:
  - Namespace: production
  - Labels: compute: fargate
  → Pods matching this profile run on Fargate

Limitations:
  - No DaemonSets (no nodes!)
  - No privileged containers
  - No GPU workloads
  - Max 4 vCPU, 30GB memory per pod
  - Higher per-pod cost (but no idle node cost)
```

### EKS Networking

```
EKS uses VPC CNI plugin (aws-node DaemonSet):
  - Pods get real VPC IP addresses
  - No overlay network (native VPC routing)
  - Pod IPs are from subnet CIDR
  - Direct communication with other VPC resources

Implications:
  - Need enough IPs in subnets (plan CIDR carefully)
  - Security groups apply to pods (with SecurityGroupPolicy)
  - Pod traffic visible in VPC Flow Logs
  - No encapsulation overhead (best performance)
```

### VPC

```
EKS VPC architecture:
  ┌─── VPC (10.0.0.0/16) ───────────────────────────────┐
  │                                                        │
  │  Public Subnets (one per AZ):                         │
  │  ┌─────────────────┐  ┌─────────────────┐           │
  │  │ 10.0.1.0/24     │  │ 10.0.2.0/24     │           │
  │  │ NAT Gateway     │  │ NAT Gateway     │           │
  │  │ ALB             │  │ ALB             │           │
  │  └─────────────────┘  └─────────────────┘           │
  │                                                        │
  │  Private Subnets (one per AZ):                        │
  │  ┌─────────────────┐  ┌─────────────────┐           │
  │  │ 10.0.10.0/24    │  │ 10.0.11.0/24    │           │
  │  │ Worker Nodes    │  │ Worker Nodes    │           │
  │  │ Pods            │  │ Pods            │           │
  │  └─────────────────┘  └─────────────────┘           │
  └────────────────────────────────────────────────────────┘
```

### Subnets

```
Subnet requirements:
  - At least 2 AZs for HA
  - Public subnets: Load balancers, NAT Gateways
  - Private subnets: Worker nodes, pods
  - Large enough CIDR for pod IPs (VPC CNI uses subnet IPs)

Tags required:
  Public:  kubernetes.io/role/elb = 1
  Private: kubernetes.io/role/internal-elb = 1
  Both:    kubernetes.io/cluster/<name> = shared
```

### Security Groups

```
Security Groups in EKS:
  - Cluster SG: Control plane to nodes communication
  - Node SG: Inter-node and pod communication
  - Pod SG: Per-pod security groups (SecurityGroupPolicy)

Minimum rules:
  Cluster SG → Node SG: 443 (API Server to kubelet)
  Node SG → Cluster SG: 443 (kubelet to API Server)
  Node SG → Node SG: All (pod-to-pod communication)
```

### IAM

```
IAM in EKS:
  - Cluster role: EKS service permissions
  - Node role: EC2 + ECR + EKS permissions
  - Pod roles: IRSA (IAM Roles for Service Accounts)

Node role policies:
  AmazonEKSWorkerNodePolicy
  AmazonEKS_CNI_Policy
  AmazonEC2ContainerRegistryReadOnly
```

### IAM Roles for Service Accounts (IRSA)

```yaml
# 1. Create IAM role with trust policy for OIDC
# 2. Annotate ServiceAccount
apiVersion: v1
kind: ServiceAccount
metadata:
  name: order-service-sa
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789:role/order-service-role

# 3. Pod using this SA gets AWS credentials automatically
# No access keys needed! Temporary, auto-rotated credentials.

# IAM policy on role:
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:PutObject"],
  "Resource": "arn:aws:s3:::order-bucket/*"
}
```

### ECR

```
ECR in EKS:
  - Nodes with proper IAM role can pull without imagePullSecrets
  - Cross-account access via ECR policies
  - Image scanning built-in (on push or scheduled)
  - Lifecycle policies for image cleanup

Image reference:
  123456789.dkr.ecr.us-east-1.amazonaws.com/my-app:v2.1.0
```

### ALB (Application Load Balancer)

```
AWS Load Balancer Controller:
  - Deploys as a pod in the cluster
  - Watches Ingress resources
  - Creates/configures AWS ALB
  - Supports path-based and host-based routing
  - Native ALB features (WAF, Cognito auth, redirects)

vs NLB (Network Load Balancer):
  ALB: Layer 7 (HTTP/HTTPS), path routing, WebSocket
  NLB: Layer 4 (TCP/UDP), ultra-low latency, static IP
```

### AWS Load Balancer Controller

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:...
    alb.ingress.kubernetes.io/healthcheck-path: /health
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/ssl-redirect: "443"
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: api-service
            port:
              number: 80
```

### EBS (Storage)

```
EBS CSI Driver:
  - Dynamic provisioning of EBS volumes
  - StorageClass with gp3, io2, etc.
  - Snapshots for backup
  - Volume encryption

Limitation: EBS is AZ-specific (ReadWriteOnce only)
Use volumeBindingMode: WaitForFirstConsumer
```

### EFS (Shared Storage)

```
EFS CSI Driver:
  - Shared filesystem (ReadWriteMany)
  - Multi-AZ (unlike EBS)
  - Elastic (auto-grows)
  - Good for shared data across pods/nodes

Use case: Shared uploads, CMS content, model files
```

### CloudWatch

```
CloudWatch integration:
  - Container Insights: Cluster/node/pod metrics
  - Log groups: Container logs via Fluent Bit
  - Alarms: Alert on metrics thresholds
  - ServiceLens: Traces with X-Ray
```

### Cluster Autoscaler

```
Cluster Autoscaler on EKS:
  - Watches for Pending pods (insufficient capacity)
  - Scales ASG (adds EC2 instances)
  - Scales down underutilized nodes
  - Needs IAM permissions for ASG operations

Configuration:
  --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled,k8s.io/cluster-autoscaler/<cluster-name>
```

### Karpenter

```
Karpenter: AWS-native node provisioning (replaces Cluster Autoscaler)

Advantages over Cluster Autoscaler:
  - Faster provisioning (directly calls EC2, no ASG)
  - Right-sizes instances (picks optimal instance type)
  - Consolidation (replaces underutilized nodes)
  - Supports spot instances natively
  - No node group management

Provisioner:
  - Defines constraints (instance types, AZs, capacity type)
  - Karpenter picks best fit for pending pods
  - Provisions in seconds (vs minutes with CA)
```

---

## Interview Questions

### Q1: What is IRSA and why is it important?

**A:** IRSA (IAM Roles for Service Accounts) allows K8s pods to assume AWS IAM roles without static credentials. It uses OIDC federation between EKS and IAM. Benefits: no stored secrets, fine-grained per-pod permissions, automatic credential rotation, follows least privilege. Implementation: annotate ServiceAccount with role ARN.

### Q2: What is the difference between Cluster Autoscaler and Karpenter?

**A:**
- **Cluster Autoscaler:** Scales ASGs. Pre-defined node groups with fixed instance types. Slower (ASG API). Requires multiple node groups for different workloads.
- **Karpenter:** Calls EC2 directly. Picks optimal instance type per pod needs. Faster (seconds). Consolidation (replaces over-provisioned nodes). Spot support built-in.

Karpenter is the modern replacement for CA on EKS.

### Q3: How does VPC CNI differ from other CNI plugins?

**A:** VPC CNI assigns real VPC IP addresses to pods (not overlay IPs). Benefits: native VPC routing (no encapsulation overhead), security groups work on pods, visible in VPC Flow Logs, can talk to other VPC resources directly. Drawback: consumes subnet IPs (need large CIDRs). Unique to AWS EKS.

### Q4: How do you design an EKS cluster for high availability?

**A:**
- Multi-AZ: Nodes in at least 2 (preferably 3) AZs
- Pod anti-affinity: Spread replicas across AZs
- Multiple replicas: Min 3 for critical services
- PodDisruptionBudget: Maintain minimum during updates
- Topology spread constraints: Even distribution
- Fargate for critical control-plane add-ons (CoreDNS)
- ALB across AZs (automatic with AWS LB Controller)

---

## Best Practices

1. **Use Managed Node Groups** — let AWS handle updates
2. **Use IRSA** — no hardcoded AWS credentials
3. **Use Karpenter** — better autoscaling than CA
4. **Plan VPC CIDR** — enough IPs for pods (VPC CNI)
5. **Multi-AZ deployment** — minimum 2, preferably 3
6. **Private cluster** — API Server private endpoint
7. **Use ALB Ingress** — native AWS integration
8. **EBS for databases, EFS for shared storage**
9. **Enable encryption** — EBS, EFS, etcd, Secrets

---

## Related Topics

- [02. Kubernetes Architecture](./02-kubernetes-architecture.md)
- [17. Scaling](./17-scaling.md)
- [34. Kubernetes + CI/CD](./34-kubernetes-cicd.md)
- [36. Production Architecture](./36-production-architecture.md)
