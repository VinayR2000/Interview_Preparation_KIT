# Elastic Load Balancing ⭐⭐⭐

## Theory

A Load Balancer distributes incoming traffic across multiple targets (EC2, containers, IPs) to ensure high availability and reliability.

---

## Diagram

### ALB Architecture

```
Internet
    │
    ↓
┌──────────────────────────────────────────────────┐
│              Application Load Balancer             │
│                                                    │
│  Listener: HTTPS:443                              │
│  ├── Rule: /api/*      → Target Group: API       │
│  ├── Rule: /admin/*    → Target Group: Admin     │
│  └── Default           → Target Group: Frontend  │
│                                                    │
└────────────────────┬─────────────────────────────┘
                     │
         ┌───────────┼───────────┐
         ↓           ↓           ↓
   ┌──────────┐ ┌──────────┐ ┌──────────┐
   │  EC2-1   │ │  EC2-2   │ │  EC2-3   │
   │ (AZ-1a)  │ │ (AZ-1b)  │ │ (AZ-1a)  │
   │ :8080    │ │ :8080    │ │ :8080    │
   └──────────┘ └──────────┘ └──────────┘
        Target Group: API (port 8080)
```

---

## Internal Working

### Load Balancer Types

| Type | Layer | Protocol | Use Case |
|------|-------|----------|----------|
| ALB | Layer 7 (Application) | HTTP/HTTPS, WebSocket | Web apps, REST APIs, microservices |
| NLB | Layer 4 (Transport) | TCP, UDP, TLS | High performance, gaming, IoT |
| CLB | Layer 4/7 (Legacy) | HTTP, TCP | Legacy — avoid for new projects |

### ALB vs NLB ⭐⭐⭐

| Feature | ALB | NLB |
|---------|-----|-----|
| Layer | 7 (HTTP) | 4 (TCP) |
| Routing | Path, host, header, query | Port-based only |
| Performance | Good | Extreme (millions of req/s) |
| Static IP | No (use alias) | Yes |
| SSL termination | Yes | Yes (TLS) |
| WebSocket | Yes | Yes |
| gRPC | Yes | Yes |
| Latency | Higher (inspects HTTP) | Ultra-low |
| Sticky sessions | Yes (cookie) | Yes (source IP) |
| Health checks | HTTP/HTTPS | TCP/HTTP |
| Use case | REST APIs, web apps | TCP services, high throughput |

**For Spring Boot REST APIs**: Always use ALB (path-based routing, HTTP health checks).

---

## Code

### ALB Components

#### Listeners
```
Listener: Port 443 (HTTPS)
├── SSL Certificate: *.example.com (ACM)
├── Default Action: Forward to default target group
└── Rules:
    ├── IF path = /api/users/*  THEN → user-service-tg
    ├── IF path = /api/orders/* THEN → order-service-tg
    ├── IF host = admin.example.com THEN → admin-tg
    └── Default → frontend-tg
```

#### Target Groups
```
Target Group: user-service-tg
├── Protocol: HTTP
├── Port: 8080
├── Health Check:
│   ├── Path: /actuator/health
│   ├── Interval: 30s
│   ├── Healthy threshold: 3
│   ├── Unhealthy threshold: 2
│   └── Timeout: 5s
├── Targets:
│   ├── i-1234 (AZ-1a) — healthy
│   ├── i-5678 (AZ-1b) — healthy
│   └── i-9012 (AZ-1a) — draining
└── Attributes:
    ├── Deregistration delay: 30s
    └── Stickiness: disabled
```

### Terraform ALB
```hcl
resource "aws_lb" "app" {
  name               = "app-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  enable_deletion_protection = true
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate.main.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_lb_target_group" "app" {
  name     = "app-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 3
    unhealthy_threshold = 2
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }

  deregistration_delay = 30
}

# Path-based routing
resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  condition {
    path_pattern { values = ["/api/*"] }
  }
}
```

---

## Real Project Usage

### Microservices Routing with ALB

```
ALB (single entry point)
├── /api/users/*     → User Service (ECS Fargate, port 8080)
├── /api/orders/*    → Order Service (ECS Fargate, port 8080)
├── /api/payments/*  → Payment Service (ECS Fargate, port 8080)
└── /*               → Frontend (S3 + CloudFront, or ECS)
```

### Spring Boot Health Check Configuration
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never  # Don't expose internals to ALB
  health:
    defaults:
      enabled: true
```

### Connection Draining (Deregistration Delay)
```
1. Instance marked unhealthy or scaling down
2. ALB stops sending NEW requests
3. Existing in-flight requests complete (deregistration delay: 30s)
4. Instance removed from target group
5. Instance terminated

→ Zero dropped requests during deployments!
```

---

## Interview Questions and Answers

**Q: What's the difference between ALB and NLB?**
> ALB operates at Layer 7 (HTTP) — it can route based on path, hostname, headers, and query parameters. Perfect for REST APIs and microservices. NLB operates at Layer 4 (TCP) — it's faster (ultra-low latency, millions of requests/sec) but can only route based on port. Use ALB for HTTP APIs; NLB for TCP services, gRPC at scale, or when you need a static IP.

**Q: How does ALB health check work?**
> ALB periodically sends HTTP requests to each target's health check path (e.g., /actuator/health). If it receives the expected status code within the timeout, the target is healthy. After N consecutive failures (unhealthy threshold), the target is marked unhealthy and receives no new traffic. After N consecutive successes, it's marked healthy again.

**Q: How would you do zero-downtime deployments with ALB?**
> Use rolling deployment: (1) Add new instances to target group, (2) Wait for health checks to pass, (3) Remove old instances with deregistration delay allowing in-flight requests to complete. With ECS/EKS: blue-green or rolling update strategy handles this automatically. Key settings: deregistration delay (30-60s) and health check grace period.

**Q: What are sticky sessions and when would you use them?**
> Sticky sessions (session affinity) route subsequent requests from the same client to the same target. ALB uses cookies for this. Use when: application stores session state in memory (not recommended) or WebSocket connections. Avoid when possible — prefer stateless applications with externalized session storage (Redis).

---

## Common Mistakes

1. **Not using HTTPS** — Always terminate TLS at the ALB with ACM certificate
2. **Health check on / instead of /actuator/health** — May return 200 even when app is unhealthy
3. **Deregistration delay too short** — Drops in-flight requests during deployments
4. **ALB in single AZ** — Must span at least 2 AZs for HA
5. **Not using path-based routing** — Deploying separate ALBs per service (expensive)
6. **Security group too open** — ALB should only allow 443/80 from 0.0.0.0/0

---

## Best Practices

1. **HTTPS everywhere** — Terminate TLS at ALB, use ACM for free certificates
2. **Health checks on /actuator/health** — Quick, doesn't hit database
3. **Cross-zone load balancing** — Distribute evenly across all AZs (enabled by default on ALB)
4. **Connection draining** — 30-60 seconds for graceful shutdown
5. **Access logs** — Enable and send to S3 for debugging and compliance
6. **WAF integration** — Attach AWS WAF to ALB for protection against common attacks
7. **Use target groups** per microservice for independent scaling and deployment

---

## Related Topics
- → [04. EC2](./04-ec2.md)
- → [06. Auto Scaling](./06-auto-scaling.md)
- → [12. ECS and EKS](./12-ecs-eks.md)
