# AWS Security ⭐⭐⭐

## Theory

Defense in depth — multiple layers of security from network to data.

---

## Diagram

### Security Layers

```
┌─────────── AWS Account Security ──────────────┐
│  Organizations, SCPs, CloudTrail, GuardDuty    │
├────────────────────────────────────────────────┤
│  ┌─────── Network Security ──────────────┐    │
│  │  VPC, NACLs, VPC Flow Logs, WAF       │    │
│  ├────────────────────────────────────────┤    │
│  │  ┌─── Instance Security ──────────┐   │    │
│  │  │  Security Groups, IAM Roles     │   │    │
│  │  ├────────────────────────────────┤   │    │
│  │  │  ┌── Application Security ──┐  │   │    │
│  │  │  │  Secrets Manager, KMS     │  │   │    │
│  │  │  ├──────────────────────────┤  │   │    │
│  │  │  │  ┌── Data Security ───┐  │  │   │    │
│  │  │  │  │  Encryption at rest │  │  │   │    │
│  │  │  │  │  Encryption in transit│ │  │   │    │
│  │  │  │  └────────────────────┘  │  │   │    │
│  │  │  └──────────────────────────┘  │   │    │
│  │  └────────────────────────────────┘   │    │
│  └────────────────────────────────────────┘    │
└────────────────────────────────────────────────┘
```

---

## Key Security Services

### KMS (Key Management Service)
```
KMS manages encryption keys for:
├── S3 (server-side encryption)
├── RDS (encryption at rest)
├── EBS (volume encryption)
├── Secrets Manager (secret encryption)
├── SQS (message encryption)
└── Custom application encryption

Types:
├── AWS Managed Keys (aws/s3, aws/rds) — free, auto-rotated
├── Customer Managed Keys (CMK) — you control, audit, rotate
└── Customer Provided Keys — bring your own (rare)
```

### Secrets Manager ⭐⭐⭐

```
Spring Boot Application
    │
    ├── Needs: DB password, API keys, certificates
    │
    └── Sources (in priority order):
        ├── ❌ Hardcoded in code
        ├── ❌ application.properties in git
        ├── ❌ Environment variables (visible in ECS console/API)
        ├── ✅ AWS Secrets Manager (encrypted, auto-rotated, audited)
        └── ✅ AWS Parameter Store (simpler, cheaper for non-secrets)
```

```java
// Spring Boot with Secrets Manager
@Configuration
public class SecretsConfig {
    private final SecretsManagerClient client = SecretsManagerClient.create();
    
    @Bean
    public DataSource dataSource() {
        String secret = client.getSecretValue(r -> r.secretId("prod/myapp/database")).secretString();
        JsonNode json = objectMapper.readTree(secret);
        
        return DataSourceBuilder.create()
            .url(json.get("url").asText())
            .username(json.get("username").asText())
            .password(json.get("password").asText())
            .build();
    }
}
```

### Secrets Manager vs Parameter Store

| Feature | Secrets Manager | Parameter Store |
|---------|----------------|-----------------|
| Cost | $0.40/secret/month | Free (standard) |
| Rotation | Built-in auto-rotation | Manual |
| Encryption | Always encrypted | Optional |
| Best for | DB passwords, API keys | Config values, feature flags |
| Size limit | 64 KB | 8 KB (standard) |

### WAF (Web Application Firewall)
```
Attach to ALB or CloudFront:
├── Block SQL injection
├── Block XSS attacks
├── Rate limiting (per IP)
├── Geo-blocking (by country)
├── IP whitelist/blacklist
└── Custom rules (regex patterns)
```

---

## Encryption

### At Rest
| Service | Default | Options |
|---------|---------|---------|
| S3 | SSE-S3 (AES-256) | SSE-KMS, SSE-C |
| RDS | Optional | KMS encryption |
| EBS | Optional | KMS encryption |
| DynamoDB | Default on | AWS owned or CMK |

### In Transit
| Layer | Mechanism |
|-------|-----------|
| Client → ALB | TLS 1.2+ (ACM certificate) |
| ALB → App | Optional TLS or HTTP internal |
| App → RDS | SSL/TLS enforced |
| App → Redis | TLS (ElastiCache encryption in transit) |
| App → S3 | HTTPS (always) |

---

## Interview Questions and Answers

**Q: How do you manage secrets for a Spring Boot application on AWS?**
> Store in AWS Secrets Manager with auto-rotation for database credentials. ECS/EKS task gets IAM role with secretsmanager:GetSecretValue permission. Spring Boot reads at startup via AWS SDK or Spring Cloud AWS. Never store in code, git, or plain environment variables. For simple config (non-sensitive): Parameter Store.

**Q: What is the difference between encryption at rest and in transit?**
> At rest: Data encrypted when stored (on disk — EBS, S3, RDS). Protects against physical theft or unauthorized disk access. In transit: Data encrypted during transfer (TLS/SSL). Protects against network eavesdropping. Both are required for compliance (HIPAA, PCI-DSS, SOC2). AWS makes both easy — KMS for at-rest, ACM for in-transit.

**Q: How do you secure a Spring Boot API on AWS?**
> Layers: (1) WAF on ALB — block common attacks (SQLi, XSS, rate limit). (2) ALB HTTPS only — TLS termination with ACM cert. (3) Security Groups — app only accessible from ALB. (4) Private subnet — no direct internet access. (5) IAM Roles — least privilege for AWS service access. (6) Secrets Manager — no hardcoded credentials. (7) VPC Endpoints — access AWS services without internet. (8) CloudTrail + GuardDuty — audit and threat detection.

---

## Best Practices

1. **Encrypt everything** — at rest (KMS) and in transit (TLS)
2. **Secrets Manager** for all credentials with auto-rotation
3. **IAM least privilege** — minimum permissions per service
4. **WAF on ALB** — protect against OWASP top 10
5. **VPC private subnets** — no direct internet to app/data layers
6. **CloudTrail** — enabled in all regions, log to S3 with integrity validation
7. **GuardDuty** — continuous threat detection (ML-based)
8. **Security Hub** — centralized security findings dashboard
9. **Regular IAM audits** — unused users/roles, overly permissive policies
10. **Separate AWS accounts** — prod/dev/staging isolation via Organizations

---

## Related Topics
- → [02. IAM](./02-iam.md)
- → [03. VPC and Networking](./03-vpc-networking.md)
- → [14. CloudWatch](./14-cloudwatch.md)
