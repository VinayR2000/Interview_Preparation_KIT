# IAM — Identity and Access Management ⭐⭐⭐

## Theory

IAM controls WHO can access WHAT in your AWS account. It's the foundation of AWS security.

### Core Concepts

| Concept | Description | Analogy |
|---------|-------------|---------|
| User | Individual identity | Employee badge |
| Group | Collection of users | Department |
| Role | Temporary identity (assumed) | Contractor badge |
| Policy | JSON permission document | Access control list |

---

## Internal Working

### IAM Architecture

```
AWS Account
├── Root User (full access — NEVER use for daily work)
│
├── IAM Users
│   ├── developer-vinay (console + CLI access)
│   └── ci-pipeline (programmatic access only)
│
├── IAM Groups
│   ├── Developers [developer-vinay, ...]
│   ├── DevOps [...]
│   └── Admins [...]
│
├── IAM Roles
│   ├── EC2-S3-Access (attached to EC2 instances)
│   ├── Lambda-DynamoDB (attached to Lambda)
│   └── EKS-Pod-Role (for Kubernetes pods)
│
└── IAM Policies
    ├── AWS Managed (AmazonS3ReadOnlyAccess, etc.)
    ├── Customer Managed (your custom policies)
    └── Inline (embedded in user/group/role)
```

### How IAM Evaluates Permissions

```
Request arrives
    ↓
1. Is there an explicit DENY? → DENIED (always wins)
    ↓ No
2. Is there an explicit ALLOW? → ALLOWED
    ↓ No
3. Default → DENIED (implicit deny)
```

**Key rule**: Explicit Deny > Explicit Allow > Implicit Deny

---

## Diagram

### IAM Policy Structure

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowS3Read",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::my-bucket",
        "arn:aws:s3:::my-bucket/*"
      ],
      "Condition": {
        "IpAddress": {
          "aws:SourceIp": "10.0.0.0/16"
        }
      }
    }
  ]
}
```

| Field | Purpose |
|-------|---------|
| Version | Always "2012-10-17" |
| Statement | Array of permission rules |
| Sid | Optional identifier |
| Effect | "Allow" or "Deny" |
| Action | What operations (s3:GetObject, ec2:StartInstances) |
| Resource | What resources (ARNs) |
| Condition | When (IP, time, MFA, tags) |

---

## Code

### Policy Types

#### Identity-Based Policy (attached to user/group/role)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "s3:*",
      "Resource": "arn:aws:s3:::my-app-bucket/*"
    }
  ]
}
```

#### Resource-Based Policy (attached to the resource itself)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::123456789012:role/MyAppRole"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::shared-bucket/*"
    }
  ]
}
```

#### Least Privilege Example (Spring Boot App)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowS3Upload",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::my-app-uploads/*"
    },
    {
      "Sid": "AllowSQSSend",
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage"
      ],
      "Resource": "arn:aws:sqs:us-east-1:123456789012:order-queue"
    },
    {
      "Sid": "AllowSecretsRead",
      "Effect": "Allow",
      "Action": "secretsmanager:GetSecretValue",
      "Resource": "arn:aws:secretsmanager:us-east-1:123456789012:secret:myapp/*"
    }
  ]
}
```

---

## IAM Roles ⭐⭐⭐

### Why Roles Instead of Access Keys?

```
❌ BAD: Access keys in application
Spring Boot → hardcoded AWS_ACCESS_KEY → S3
Problems:
- Keys can leak (git, logs, config files)
- Keys don't rotate automatically
- Keys are permanent credentials

✅ GOOD: IAM Role attached to EC2/ECS/EKS
Spring Boot on EC2 → EC2 Instance Role → S3
Benefits:
- No credentials in code
- Temporary credentials (auto-rotated)
- Managed by AWS STS
- Revocable instantly
```

### How Roles Work (STS — Security Token Service)

```
EC2 Instance
    │
    ├── Attached Role: "MyAppRole"
    │
    ├── Instance Metadata Service (169.254.169.254)
    │   └── Provides temporary credentials:
    │       ├── AccessKeyId (temporary)
    │       ├── SecretAccessKey (temporary)
    │       ├── SessionToken
    │       └── Expiration (auto-rotates before expiry)
    │
    └── AWS SDK automatically reads these credentials
        └── No configuration needed in your Spring Boot app!
```

### Role Trust Policy (Who Can Assume This Role)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

### Cross-Account Role Assumption
```
Account A (Dev: 111111111111)
    │
    │ sts:AssumeRole
    ↓
Account B (Prod: 222222222222)
    └── Role: "CrossAccountReadRole"
        └── Trust: Account A can assume this role
        └── Permissions: Read-only access to S3
```

---

## IAM for Kubernetes (IRSA) ⭐⭐⭐

### IAM Roles for Service Accounts (EKS)

```
EKS Pod
    │
    ├── ServiceAccount: "order-service-sa"
    │
    ├── Annotated with IAM Role ARN:
    │   eks.amazonaws.com/role-arn: arn:aws:iam::123:role/OrderServiceRole
    │
    └── Pod gets temporary credentials via OIDC
        └── Only this specific pod gets these permissions
        └── Other pods in same namespace: different permissions
```

This is the equivalent of EC2 instance roles but for individual Kubernetes pods.

---

## Real Project Usage

### Spring Boot on AWS — IAM Setup

```yaml
# No credentials in application.properties!
# The IAM Role attached to EC2/ECS task provides access.

# Spring Cloud AWS auto-discovers credentials from:
# 1. Environment variables
# 2. Java system properties
# 3. Instance profile (IAM Role) ← PREFERRED
# 4. Default profile (~/.aws/credentials) — local dev only
```

```java
// Spring Boot S3 client — no credentials needed on AWS
@Service
public class FileUploadService {
    private final S3Client s3Client;
    
    public FileUploadService() {
        // SDK automatically uses IAM Role credentials
        this.s3Client = S3Client.builder()
            .region(Region.US_EAST_1)
            .build();
    }
    
    public String uploadFile(MultipartFile file) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("my-app-uploads")
                .key("uploads/" + file.getOriginalFilename())
                .build(),
            RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );
        return "Upload successful";
    }
}
```

---

## Interview Questions and Answers

**Q: Why should an EC2 instance use an IAM Role instead of access keys?** ⭐⭐⭐
> IAM Roles provide temporary credentials that are automatically rotated by AWS STS. They're delivered via the instance metadata service — no keys in code, config files, or environment variables. Access keys are permanent, can leak through git/logs, and require manual rotation. Roles follow the principle of least privilege with zero credential management overhead.

**Q: What is the difference between identity-based and resource-based policies?**
> Identity-based policies are attached to users/groups/roles and define what THEY can do. Resource-based policies are attached to resources (S3 bucket, SQS queue) and define WHO can access THEM. Resource-based policies can grant cross-account access without assuming a role. Both types are evaluated together.

**Q: How does IAM determine if a request is allowed?**
> Explicit Deny always wins → then check for Explicit Allow → if neither, Implicit Deny (default). All applicable policies (identity-based, resource-based, permission boundaries, SCPs) are evaluated together. A single explicit deny from any policy overrides all allows.

**Q: What are IAM Roles for Service Accounts (IRSA) in EKS?**
> IRSA allows individual Kubernetes pods to assume specific IAM roles using OIDC federation. Each ServiceAccount is annotated with a role ARN. This provides fine-grained permissions at the pod level — different microservices in the same cluster get different AWS permissions. Much better than a single node-level role shared by all pods.

---

## Common Mistakes

1. **Using root account** for daily work — Create IAM users/roles instead
2. **Overly permissive policies** — `"Action": "*", "Resource": "*"` is never acceptable
3. **Hardcoding access keys** in application code or config files
4. **Not using MFA** for human users with console access
5. **Sharing credentials** between applications — each service gets its own role
6. **Not rotating access keys** when they must be used (CI/CD systems)

---

## Best Practices

1. **Root account**: Enable MFA, lock away, use only for billing/account changes
2. **Least privilege**: Grant minimum permissions needed, expand only when required
3. **Use Roles**: For EC2, ECS, Lambda, EKS — never embed credentials
4. **Use Groups**: Assign policies to groups, put users in groups (not direct user policies)
5. **Enable CloudTrail**: Audit all IAM actions
6. **Policy conditions**: Restrict by IP, MFA, time where appropriate
7. **Regular audits**: Review unused users, roles, and overly permissive policies
8. **Separate accounts**: Use AWS Organizations for prod/dev/staging isolation

---

## Production Considerations

- Use AWS Organizations with Service Control Policies (SCPs) for account-level guardrails
- Implement permission boundaries for delegated administration
- Use IAM Access Analyzer to identify resources shared externally
- Set up credential reports and unused credential alerts
- For CI/CD: Use OIDC federation (GitHub Actions → AWS) instead of long-lived access keys
- Tag all IAM resources for cost allocation and management

---

## Related Topics
- → [01. AWS Fundamentals](./01-aws-fundamentals.md)
- → [03. VPC and Networking](./03-vpc-networking.md)
- → [15. AWS Security](./15-aws-security.md)
