# Route 53 and CloudFront

## Route 53 — DNS Service

### Record Types
| Type | Purpose | Example |
|------|---------|---------|
| A | IPv4 address | api.example.com → 1.2.3.4 |
| AAAA | IPv6 address | api.example.com → ::1 |
| CNAME | Alias to another domain | www → example.com |
| Alias | AWS-native alias (free, works at zone apex) | example.com → ALB DNS |
| MX | Mail server | - |
| TXT | Text records (verification) | - |

**Always use Alias** for AWS resources (ALB, CloudFront, S3) — free, works at zone apex (example.com).

### Routing Policies
| Policy | Use Case |
|--------|----------|
| Simple | Single resource |
| Weighted | A/B testing (90% v1, 10% v2) |
| Latency | Route to nearest region |
| Failover | Primary/standby (disaster recovery) |
| Geolocation | Route by user location (compliance) |
| Multi-value | Multiple healthy targets (simple LB) |

### Health Checks + Failover
```
Route 53 Health Check
    ↓ monitors
Primary ALB (us-east-1) → HEALTHY → Route traffic here
    ↓ fails
Failover ALB (eu-west-1) → Route traffic here
```

---

## CloudFront — CDN

### Architecture
```
User (Australia)
    ↓ DNS → nearest Edge Location
Edge Location (Sydney)
    ├── Cache HIT → Return immediately (low latency)
    └── Cache MISS → Fetch from Origin
                         ↓
                    Origin:
                    ├── S3 Bucket (static assets)
                    ├── ALB (dynamic API)
                    └── Custom origin (any HTTP server)
```

### Use Cases for Spring Boot
```
CloudFront Distribution:
├── /static/*  → S3 Origin (CSS, JS, images)
├── /api/*     → ALB Origin (Spring Boot, no caching)
└── /*         → S3 Origin (React/Angular SPA)

Headers:
├── Cache-Control: public, max-age=31536000 (static assets)
└── Cache-Control: no-cache (API responses)
```

### CloudFront + S3 (Static Website)
```
React/Angular Build → S3 Bucket (private)
                            ↓
                    CloudFront (HTTPS, global)
                            ↓
                    User gets fast response from nearest edge
```

---

## Interview Questions and Answers

**Q: How would you set up a global web application on AWS?**
> Route 53 (DNS) → CloudFront (CDN, HTTPS, edge caching) → Origins: S3 for static assets (React/Angular build), ALB for API (Spring Boot). CloudFront provides global low-latency access, DDoS protection (Shield), and WAF integration. Use Cache-Control headers to control what gets cached.

**Q: What's the difference between CNAME and Alias records?**
> CNAME: Standard DNS alias, cannot be used at zone apex (example.com), costs per query. Alias: AWS-specific, works at zone apex, free for AWS resources, responds with IP directly (no extra DNS hop). Always use Alias for ALB, CloudFront, S3 endpoints.

**Q: How do you do blue-green deployments with Route 53?**
> Weighted routing: Start with 100% to blue, shift 10% → 50% → 100% to green. Health checks ensure only healthy targets receive traffic. Instant rollback by changing weights back. For zero-downtime: combine with ALB target group switching for faster transitions.

---

## Related Topics
- → [05. Load Balancing](./05-load-balancing.md)
- → [07. S3](./07-s3.md)
- → [19. High Availability and DR](./19-high-availability-disaster-recovery.md)
