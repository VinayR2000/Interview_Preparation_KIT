# CloudWatch — Monitoring and Observability ⭐⭐⭐

## Theory

CloudWatch is AWS's monitoring service. It collects metrics, logs, and events from AWS resources and applications. Essential for production operations.

---

## Diagram

### CloudWatch Ecosystem

```
┌─── Data Sources ───┐     ┌─── CloudWatch ───┐     ┌─── Actions ───┐
│                     │     │                   │     │                │
│  EC2 Metrics        │────→│  Metrics          │────→│  Alarms        │
│  ECS/EKS Metrics    │     │  (time-series)    │     │  → SNS         │
│  RDS Metrics        │     │                   │     │  → Auto Scale  │
│  ALB Metrics        │     │  Logs             │     │  → Lambda      │
│  Custom Metrics     │     │  (application)    │     │                │
│                     │     │                   │     │  Dashboards    │
│  Application Logs   │────→│  Log Groups       │     │  (visualization)│
│  (Spring Boot)      │     │  Log Streams      │     │                │
│                     │     │  Log Insights     │     │  EventBridge   │
│  CloudTrail Logs    │     │  (query engine)   │     │  (automation)  │
└─────────────────────┘     └───────────────────┘     └────────────────┘
```

### Spring Boot Monitoring on AWS

```
Spring Boot Application
    │
    ├── Application Logs → CloudWatch Logs
    │   ├── Log Group: /ecs/user-service
    │   └── Structured JSON logs (searchable)
    │
    ├── Metrics → CloudWatch Metrics
    │   ├── JVM heap, GC, threads (via Micrometer)
    │   ├── HTTP request count, latency (via Micrometer)
    │   └── Custom business metrics
    │
    └── Health → ALB Health Checks
        └── /actuator/health
```

---

## Internal Working

### Key Metrics to Monitor

#### EC2 / ECS
| Metric | Warning | Critical |
|--------|---------|----------|
| CPUUtilization | > 70% | > 90% |
| MemoryUtilization | > 70% | > 90% |
| NetworkIn/Out | Sudden spike | - |
| StatusCheckFailed | Any failure | - |

#### RDS
| Metric | Warning | Critical |
|--------|---------|----------|
| CPUUtilization | > 70% | > 85% |
| FreeableMemory | < 2 GB | < 500 MB |
| DatabaseConnections | > 80% max | > 95% max |
| ReadIOPS/WriteIOPS | Sudden spike | - |
| FreeStorageSpace | < 20% | < 10% |
| ReplicaLag | > 10s | > 60s |

#### ALB
| Metric | Warning | Critical |
|--------|---------|----------|
| HTTPCode_Target_5XX | > 1% | > 5% |
| TargetResponseTime | > 2s (p99) | > 5s |
| UnHealthyHostCount | > 0 | > 50% |
| RequestCount | For scaling | - |

---

## Code

### CloudWatch Alarm (Terraform)
```hcl
resource "aws_cloudwatch_metric_alarm" "high_cpu" {
  alarm_name          = "user-service-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  
  dimensions = {
    ClusterName = "production"
    ServiceName = "user-service"
  }

  alarm_actions = [aws_sns_topic.alerts.arn]  # Notify on alarm
  ok_actions    = [aws_sns_topic.alerts.arn]  # Notify on recovery
}

resource "aws_cloudwatch_metric_alarm" "high_5xx" {
  alarm_name          = "alb-high-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10

  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
}
```

### Spring Boot Logging to CloudWatch

```yaml
# logback-spring.xml (structured JSON logging)
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"service":"user-service","environment":"production"}</customFields>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
```

ECS/Fargate `awslogs` driver automatically ships stdout to CloudWatch Logs.

### CloudWatch Logs Insights (Query Language)
```sql
-- Find errors in last hour
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 50

-- Response time percentiles
fields @timestamp, responseTime
| stats avg(responseTime) as avg_ms,
        pct(responseTime, 95) as p95_ms,
        pct(responseTime, 99) as p99_ms
  by bin(5m)

-- Count errors by type
fields @timestamp, errorType
| filter level = "ERROR"
| stats count() by errorType
| sort count desc
```

---

## CloudWatch vs CloudTrail

| | CloudWatch | CloudTrail |
|-|-----------|-----------|
| What | Performance metrics + app logs | API audit trail |
| Question | "Is my app healthy?" | "Who did what?" |
| Data | Metrics, logs, events | API calls (who, when, what) |
| Use case | Monitoring, alerting | Security audit, compliance |
| Example | "CPU is at 90%" | "User X deleted S3 bucket at 3pm" |

---

## Interview Questions and Answers

**Q: How would you set up monitoring for a Spring Boot microservice on ECS?**
> (1) Application logs → CloudWatch Logs (via awslogs driver, JSON format for searchability). (2) Metrics → CloudWatch via Micrometer + CloudWatch exporter (JVM, HTTP, custom). (3) Alarms → on 5XX rate, response time p99, CPU, memory. (4) Dashboard → key metrics per service. (5) Alerts → SNS → PagerDuty/Slack.

**Q: Your application is returning 500 errors. How do you debug using CloudWatch?**
> 1. Check ALB metrics → HTTPCode_Target_5XX_Count (confirms the issue)
> 2. CloudWatch Logs Insights → filter by ERROR, find stack traces
> 3. Check ECS metrics → CPU/Memory (resource exhaustion?)
> 4. Check RDS metrics → connections, CPU (database issue?)
> 5. Check target group → unhealthy targets?
> 6. X-Ray traces → find slow/failing dependency calls

**Q: What alarms would you set up for a production Spring Boot service?**
> Critical: (1) 5XX rate > 5%, (2) UnHealthyHostCount > 0, (3) CPU > 90% sustained, (4) Memory > 90%. Warning: (1) p99 latency > 2s, (2) Error rate > 1%, (3) DLQ message count > 0, (4) DB connections > 80%. Informational: (1) Deployment events, (2) Scaling events.

---

## Best Practices

1. **Structured JSON logs** — enables CloudWatch Logs Insights queries
2. **Custom metrics** — track business KPIs (orders/min, payments/sec)
3. **Alarms on symptoms, not causes** — alert on 5XX, not CPU (CPU is investigation)
4. **Composite alarms** — combine multiple conditions to reduce noise
5. **Log retention** — set appropriate retention (30 days for app logs, 90 for audit)
6. **Dashboards** — one per service with key metrics at a glance
7. **X-Ray tracing** — distributed tracing across microservices
8. **Anomaly detection** — CloudWatch ML-based anomaly detection for unpredictable patterns

---

## Related Topics
- → [06. Auto Scaling](./06-auto-scaling.md)
- → [12. ECS and EKS](./12-ecs-eks.md)
- → [15. AWS Security](./15-aws-security.md)
