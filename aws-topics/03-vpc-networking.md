# VPC and Networking ⭐⭐⭐

## Theory

A VPC (Virtual Private Cloud) is your isolated network in AWS. It's where all your resources live. Think of it as your own private data center in the cloud.

---

## Internal Working

### VPC Architecture

```
┌─────────────────────────── VPC (10.0.0.0/16) ───────────────────────────┐
│                                                                          │
│  ┌──────────────── AZ-1a ──────────────┐  ┌──────── AZ-1b ────────────┐│
│  │                                      │  │                            ││
│  │  ┌─── Public Subnet (10.0.1.0/24)──┐│  │  ┌── Public Subnet ──────┐││
│  │  │  ALB, NAT Gateway, Bastion       ││  │  │  ALB (standby)        │││
│  │  └──────────────────────────────────┘│  │  └───────────────────────┘││
│  │                                      │  │                            ││
│  │  ┌── Private Subnet (10.0.3.0/24)──┐│  │  ┌── Private Subnet ─────┐││
│  │  │  EC2 (App), ECS Tasks, EKS Pods ││  │  │  EC2 (App), ECS, EKS  │││
│  │  └──────────────────────────────────┘│  │  └───────────────────────┘││
│  │                                      │  │                            ││
│  │  ┌── Data Subnet (10.0.5.0/24) ────┐│  │  ┌── Data Subnet ────────┐││
│  │  │  RDS Primary, ElastiCache        ││  │  │  RDS Standby          │││
│  │  └──────────────────────────────────┘│  │  └───────────────────────┘││
│  │                                      │  │                            ││
│  └──────────────────────────────────────┘  └────────────────────────────┘│
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Diagram

### Traffic Flow — The Most Important AWS Architecture Pattern ⭐⭐⭐

```
Internet (User)
      │
      ↓
┌──────────────┐
│ Route 53     │  DNS resolution
└──────┬───────┘
       ↓
┌──────────────┐
│ Internet     │  Gateway to VPC
│ Gateway      │
└──────┬───────┘
       ↓
┌──────────────┐
│ ALB          │  Public Subnet — routes to targets
│(Load Balancer)│
└──────┬───────┘
       ↓
┌──────────────┐
│ EC2 / ECS    │  Private Subnet — no direct internet access
│ (Application)│
└──────┬───────┘
       ↓
┌──────────────┐
│ RDS          │  Data Subnet — most restricted
│ (Database)   │
└──────────────┘
```

**This pattern should become second nature.**

---

## Key Components

### CIDR (IP Address Ranges)
```
VPC CIDR: 10.0.0.0/16 → 65,536 IP addresses
  ├── Public Subnet:  10.0.1.0/24 → 256 IPs
  ├── Private Subnet: 10.0.3.0/24 → 256 IPs
  └── Data Subnet:    10.0.5.0/24 → 256 IPs

/16 = 65,536 IPs (for VPC)
/24 = 256 IPs (for subnets)
/28 = 16 IPs (smallest allowed)
```

### Subnets

| Type | Internet Access | Use Case |
|------|----------------|----------|
| Public | Yes (via IGW) | ALB, NAT Gateway, Bastion |
| Private | Outbound only (via NAT) | Application servers, EKS nodes |
| Data/Isolated | None | Databases, caches |

**What makes a subnet public?**
1. Route table has route to Internet Gateway (0.0.0.0/0 → igw-xxx)
2. Resources have public IP or Elastic IP

### Internet Gateway (IGW)
- Enables internet access for public subnets
- Horizontally scaled, redundant, no bandwidth constraints
- One per VPC

### NAT Gateway
- Enables **outbound** internet access for private subnets
- Private instances can download packages, call external APIs
- Does NOT allow inbound connections from internet
- Costs money — deploy in public subnet

```
Private EC2 → NAT Gateway (public subnet) → Internet Gateway → Internet
Internet → ❌ Cannot reach private EC2 directly
```

### Route Tables

```
Public Subnet Route Table:
  10.0.0.0/16  → local (within VPC)
  0.0.0.0/0    → igw-12345 (Internet Gateway)

Private Subnet Route Table:
  10.0.0.0/16  → local (within VPC)
  0.0.0.0/0    → nat-12345 (NAT Gateway)

Data Subnet Route Table:
  10.0.0.0/16  → local (within VPC only — no internet)
```

---

## Security Groups vs NACLs ⭐⭐⭐

### Security Groups (Instance-Level Firewall)

```
┌─────────────────────────────────┐
│        Security Group           │
│  ┌─────────────────────────┐   │
│  │    EC2 Instance         │   │
│  └─────────────────────────┘   │
│                                 │
│  Inbound Rules:                 │
│  ├── Port 8080 from ALB-SG     │
│  └── Port 22 from Bastion-SG   │
│                                 │
│  Outbound Rules:                │
│  └── All traffic (default)      │
└─────────────────────────────────┘
```

### NACLs (Subnet-Level Firewall)

| Feature | Security Group | NACL |
|---------|---------------|------|
| Level | Instance (ENI) | Subnet |
| State | Stateful (return traffic auto-allowed) | Stateless (must allow return traffic) |
| Rules | Allow only | Allow AND Deny |
| Evaluation | All rules evaluated | Rules evaluated in order (first match) |
| Default | Deny all inbound | Allow all |
| Use case | Primary firewall | Additional layer, block IPs |

### Security Group Chaining ⭐⭐⭐
```
Internet → ALB-SG (port 443) → App-SG (port 8080 from ALB-SG) → DB-SG (port 5432 from App-SG)
```

```json
// App Security Group — only allows traffic from ALB
{
  "Inbound": [
    { "Port": 8080, "Source": "sg-alb-12345" }
  ]
}

// Database Security Group — only allows traffic from App
{
  "Inbound": [
    { "Port": 5432, "Source": "sg-app-67890" }
  ]
}
```

---

## Code

### Terraform VPC Example
```hcl
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true
  
  tags = { Name = "production-vpc" }
}

resource "aws_subnet" "public" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 1}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  
  tags = { Name = "public-subnet-${count.index + 1}" }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 3}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
  
  tags = { Name = "private-subnet-${count.index + 1}" }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
}

resource "aws_nat_gateway" "nat" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id
}
```

---

## Real Project Usage

### Production VPC Design for Spring Boot Microservices

```
VPC: 10.0.0.0/16
├── Public Subnets (10.0.1.0/24, 10.0.2.0/24)
│   ├── Application Load Balancer
│   ├── NAT Gateway
│   └── Bastion Host (SSH jump box)
│
├── Private Subnets (10.0.3.0/24, 10.0.4.0/24)
│   ├── EKS Worker Nodes
│   ├── ECS Tasks (Spring Boot containers)
│   └── EC2 Instances (if not containerized)
│
└── Data Subnets (10.0.5.0/24, 10.0.6.0/24)
    ├── RDS PostgreSQL (Multi-AZ)
    ├── ElastiCache Redis
    └── Amazon MQ / MSK
```

### Connecting to Private Resources
```
Developer Laptop
    ↓ SSH to
Bastion Host (public subnet)
    ↓ SSH tunnel to
EC2 / RDS in private subnet

# Or use AWS Systems Manager Session Manager (no bastion needed)
aws ssm start-session --target i-1234567890abcdef0
```

---

## Interview Questions and Answers

**Q: Explain the difference between public and private subnets.**
> A public subnet has a route to an Internet Gateway (0.0.0.0/0 → IGW) and resources can have public IPs for direct internet access. A private subnet has no IGW route — it can only reach the internet through a NAT Gateway (outbound only). Databases and application servers go in private subnets; load balancers go in public subnets.

**Q: How does a private EC2 instance access the internet (e.g., to download packages)?**
> Through a NAT Gateway deployed in a public subnet. The private subnet's route table sends 0.0.0.0/0 traffic to the NAT Gateway, which translates the private IP to its public IP and forwards to the Internet Gateway. Return traffic follows the reverse path. The NAT Gateway does NOT allow inbound connections from the internet.

**Q: What's the difference between Security Groups and NACLs?**
> Security Groups are stateful (return traffic is auto-allowed), operate at the instance level, and only allow rules. NACLs are stateless (must explicitly allow return traffic), operate at the subnet level, and support both allow and deny rules. Use Security Groups as primary firewall and NACLs as an additional layer for blocking specific IPs/ranges.

**Q: Design a VPC for a 3-tier web application.**
> Public subnets (2 AZs): ALB for incoming traffic. Private subnets (2 AZs): Application servers (EC2/ECS/EKS). Data subnets (2 AZs): RDS Multi-AZ, ElastiCache. Security group chaining: ALB-SG → App-SG → DB-SG. NAT Gateway in public subnet for app servers to reach external APIs. Each tier can only communicate with adjacent tiers.

---

## Common Mistakes

1. **Putting databases in public subnets** — Always use private/data subnets
2. **Using 0.0.0.0/0 in security group inbound** — Restrict to specific sources
3. **Single NAT Gateway** — Deploy one per AZ for HA (or accept single point of failure)
4. **Too small CIDR** — VPCs can't be resized easily; use /16 for production
5. **Not using security group references** — Use SG IDs instead of IP ranges for internal traffic

---

## Best Practices

1. **Use at least 2 AZs** for every subnet tier
2. **Security Group chaining** — reference SGs, not IPs, for internal communication
3. **Least privilege networking** — only open required ports between tiers
4. **VPC Flow Logs** — enable for security monitoring and troubleshooting
5. **Private endpoints (VPC Endpoints)** — access AWS services without internet transit
6. **Consistent CIDR planning** — plan for VPC peering and avoid overlaps

---

## Related Topics
- → [01. AWS Fundamentals](./01-aws-fundamentals.md)
- → [04. EC2](./04-ec2.md)
- → [05. Load Balancing](./05-load-balancing.md)
