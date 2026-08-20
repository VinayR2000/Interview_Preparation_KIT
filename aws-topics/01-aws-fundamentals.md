# AWS Fundamentals

## Theory

### What is Cloud Computing?
On-demand delivery of IT resources over the internet with pay-as-you-go pricing. Instead of buying and maintaining physical servers, you rent compute, storage, and networking from AWS.

### Cloud Service Models

| Model | What You Manage | What AWS Manages | Example |
|-------|----------------|-----------------|---------|
| IaaS | OS, Runtime, App, Data | Hardware, Networking, Virtualization | EC2 |
| PaaS | App, Data | OS, Runtime, Hardware | Elastic Beanstalk |
| SaaS | Nothing (just use it) | Everything | Gmail, Salesforce |

### Cloud vs On-Premises

| Aspect | On-Premises | Cloud (AWS) |
|--------|-------------|-------------|
| Cost model | CapEx (upfront) | OpEx (pay-per-use) |
| Scaling | Weeks/months | Minutes |
| Availability | Your responsibility | Shared responsibility |
| Maintenance | You | AWS (hardware) |
| Global reach | Build data centers | Already global |

---

## Internal Working

### AWS Global Infrastructure

```
AWS Global Infrastructure
├── Regions (33+)
│   ├── us-east-1 (N. Virginia)
│   ├── us-west-2 (Oregon)
│   ├── eu-west-1 (Ireland)
│   ├── ap-south-1 (Mumbai)
│   └── ...
├── Availability Zones (105+)
│   └── Each Region has 2-6 AZs
├── Edge Locations (600+)
│   └── CloudFront CDN, Route 53
└── Local Zones / Wavelength
```

### Region
- Geographic area (e.g., us-east-1 = N. Virginia)
- Completely independent and isolated
- Choose based on: latency, compliance, service availability, cost

### Availability Zone (AZ) ⭐⭐⭐
- One or more data centers within a Region
- Connected via low-latency links
- Physically separated (different buildings, power, flood zones)
- Designed to fail independently

```
Region: us-east-1
├── AZ: us-east-1a (Data Center cluster A)
├── AZ: us-east-1b (Data Center cluster B)
├── AZ: us-east-1c (Data Center cluster C)
├── AZ: us-east-1d
├── AZ: us-east-1e
└── AZ: us-east-1f
```

### Why Deploy Across Multiple AZs? ⭐⭐⭐
```
                    Region: us-east-1
┌─────────────────────────────────────────────┐
│                                             │
│  ┌──────────────┐    ┌──────────────┐      │
│  │    AZ-1a     │    │    AZ-1b     │      │
│  │              │    │              │      │
│  │  ┌────────┐  │    │  ┌────────┐  │      │
│  │  │  EC2   │  │    │  │  EC2   │  │      │
│  │  │ (App)  │  │    │  │ (App)  │  │      │
│  │  └────────┘  │    │  └────────┘  │      │
│  │  ┌────────┐  │    │  ┌────────┐  │      │
│  │  │  RDS   │  │    │  │  RDS   │  │      │
│  │  │(Primary)│  │    │  │(Standby)│ │      │
│  │  └────────┘  │    │  └────────┘  │      │
│  └──────────────┘    └──────────────┘      │
│                                             │
│  If AZ-1a goes down → AZ-1b takes over     │
└─────────────────────────────────────────────┘
```

**Answer**: To survive an AZ failure. If one data center has a fire, power outage, or network issue, your application continues running in another AZ with zero downtime.

---

## Diagram

### Shared Responsibility Model ⭐⭐⭐

```
┌──────────────────────────────────────────┐
│          CUSTOMER RESPONSIBILITY          │
│         "Security IN the Cloud"          │
├──────────────────────────────────────────┤
│  Customer Data                           │
│  Platform, Applications, IAM             │
│  Operating System, Network Config        │
│  Client-side & Server-side Encryption    │
│  Network Traffic Protection              │
├──────────────────────────────────────────┤
│           AWS RESPONSIBILITY             │
│         "Security OF the Cloud"          │
├──────────────────────────────────────────┤
│  Hardware / AWS Global Infrastructure    │
│  Regions, AZs, Edge Locations            │
│  Compute, Storage, Database, Networking  │
│  Managed Services Software               │
└──────────────────────────────────────────┘
```

| AWS Responsibility | Customer Responsibility |
|-------------------|------------------------|
| Physical security of data centers | Data encryption |
| Hardware maintenance | IAM user permissions |
| Network infrastructure | Security group rules |
| Hypervisor patching | OS patching (EC2) |
| Managed service patching | Application security |

---

## Key Concepts

### Scalability vs Elasticity

| Concept | Definition | Example |
|---------|-----------|---------|
| Scalability | Ability to handle growth | Adding more EC2 instances |
| Elasticity | Auto-scale up AND down | Auto Scaling during peak/off-peak |
| Vertical Scaling | Bigger instance (scale up) | t3.micro → t3.xlarge |
| Horizontal Scaling | More instances (scale out) | 2 instances → 10 instances |

### High Availability vs Fault Tolerance

| Concept | Definition | Approach |
|---------|-----------|----------|
| High Availability | Minimize downtime | Multi-AZ deployment |
| Fault Tolerance | Zero downtime during failure | Redundancy, auto-failover |
| Disaster Recovery | Recover from major failure | Cross-region backup/replication |

### RPO and RTO
```
                    Disaster
                       │
  ───────────────┬─────┼──────────────────→ Time
                 │     │         │
              Last     │      Recovery
              Backup   │      Complete
                 │     │         │
                 ├─────┤         │
                 RPO   ├─────────┤
                          RTO
```

- **RPO** (Recovery Point Objective): Maximum acceptable data loss (time since last backup)
- **RTO** (Recovery Time Objective): Maximum acceptable downtime

---

## Real Project Usage

### Typical Spring Boot Microservices Architecture on AWS

```
Internet
    ↓
Route 53 (DNS)
    ↓
CloudFront (CDN)
    ↓
ALB (Load Balancer)
    ↓
┌────────────────────────────────────────┐
│              EKS Cluster               │
│  ┌──────────┐  ┌──────────┐          │
│  │  Pod:    │  │  Pod:    │          │
│  │ User Svc │  │ Order Svc│          │
│  └────┬─────┘  └────┬─────┘          │
└───────┼──────────────┼────────────────┘
        │              │
   ┌────┴────┐    ┌───┴────┐
   │   RDS   │    │  SQS   │
   │PostgreSQL│    │ Queue  │
   └─────────┘    └────────┘
```

---

## Interview Questions and Answers

**Q: What is the difference between a Region and an Availability Zone?**
> A Region is a geographic area (e.g., us-east-1) containing multiple AZs. An AZ is one or more data centers within a Region, physically separated but connected via low-latency links. You deploy across multiple AZs for high availability within a Region.

**Q: Explain the Shared Responsibility Model.**
> AWS is responsible for security OF the cloud (hardware, data centers, network infrastructure). The customer is responsible for security IN the cloud (data encryption, IAM configuration, security groups, OS patching on EC2, application security). For managed services like RDS, AWS handles more (OS patching), but you still manage access control and data encryption.

**Q: Why would you choose one Region over another?**
> Consider: (1) Latency — pick Region closest to users, (2) Compliance — data residency requirements (GDPR requires EU), (3) Cost — pricing varies by Region, (4) Service availability — not all services are in all Regions.

**Q: What happens when an entire AZ goes down?**
> If you've deployed across multiple AZs (which you should), your ALB routes traffic to healthy instances in remaining AZs. RDS Multi-AZ automatically fails over to standby. Application continues with minimal (usually zero) user impact.

---

## Common Mistakes

1. **Deploying in a single AZ** — Always use Multi-AZ for production
2. **Choosing Region by name, not by need** — us-east-1 is cheapest but may not be closest to users
3. **Ignoring data transfer costs** — Cross-region and internet egress charges add up
4. **Not understanding shared responsibility** — Thinking AWS handles everything security-related

---

## Best Practices

1. **Always deploy across at least 2 AZs** for production workloads
2. **Choose Region based on**: user proximity > compliance > cost > services
3. **Use managed services** when possible — less operational burden
4. **Design for failure** — assume any component can fail
5. **Use Infrastructure as Code** — reproducible, version-controlled infrastructure

---

## Production Considerations

- AWS provides 99.99% availability SLA for most services across Multi-AZ
- Cross-region deployments add complexity but provide disaster recovery
- Edge locations improve latency for global users (CloudFront)
- Consider cost: data transfer between AZs is charged, within AZ is free
- Use AWS Well-Architected Framework as design guide

---

## Related Topics
- → [02. IAM](./02-iam.md)
- → [03. VPC and Networking](./03-vpc-networking.md)
- → [19. High Availability and Disaster Recovery](./19-high-availability-disaster-recovery.md)
