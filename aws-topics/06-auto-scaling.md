# Auto Scaling ⭐⭐⭐

## Theory

Auto Scaling automatically adjusts the number of EC2 instances (or ECS tasks) based on demand. Scale out during peak, scale in during off-peak — pay only for what you need.

---

## Diagram

```
Traffic Pattern:
  High ─────────────────╮         ╭──────────
                        │         │
  Low  ────╮           │         │         ╭────
           │           │         │         │
  ─────────┴───────────┴─────────┴─────────┴──→ Time
           6am        12pm       6pm      12am

Auto Scaling Response:
  Instances: 2 → 2 → 4 → 6 → 6 → 4 → 2 → 2
```

### Auto Scaling Group (ASG) Architecture

```
┌─────────────── Auto Scaling Group ───────────────┐
│                                                   │
│  Min: 2    Desired: 4    Max: 10                 │
│                                                   │
│  ┌─── AZ-1a ────┐    ┌─── AZ-1b ────┐          │
│  │  ┌────────┐  │    │  ┌────────┐  │          │
│  │  │  EC2   │  │    │  │  EC2   │  │          │
│  │  └────────┘  │    │  └────────┘  │          │
│  │  ┌────────┐  │    │  ┌────────┐  │          │
│  │  │  EC2   │  │    │  │  EC2   │  │          │
│  │  └────────┘  │    │  └────────┘  │          │
│  └───────────────┘    └──────────────┘          │
│                                                   │
│  Scaling Policy:                                  │
│  └── Target: CPU < 60%                           │
│                                                   │
└───────────────────────────────────────────────────┘
         ↑                           ↑
    ALB Health Check            CloudWatch Alarm
    (replace unhealthy)         (add/remove instances)
```

---

## Internal Working

### ASG Components

| Component | Description |
|-----------|-------------|
| Launch Template | What to launch (AMI, instance type, SG, user data) |
| Min Capacity | Minimum instances always running |
| Max Capacity | Maximum instances allowed |
| Desired Capacity | Current target count |
| Scaling Policy | When and how to scale |
| Health Check | How to detect unhealthy instances |
| Cooldown Period | Wait time between scaling actions |

### Scaling Types

#### Target Tracking (Recommended) ⭐⭐⭐
```
"Keep average CPU at 60%"
- CPU goes above 60% → add instances
- CPU drops below 60% → remove instances
- Simple, self-adjusting
```

#### Step Scaling
```
CPU 60-70% → add 1 instance
CPU 70-80% → add 2 instances
CPU > 80%  → add 3 instances
CPU < 40%  → remove 1 instance
```

#### Scheduled Scaling
```
Mon-Fri 8am  → Desired = 6 (work hours)
Mon-Fri 7pm  → Desired = 2 (off hours)
Sat-Sun      → Desired = 2 (weekend)
```

#### Predictive Scaling
- Uses ML to predict traffic patterns
- Pre-scales before anticipated demand
- Best for recurring patterns (daily, weekly)

---

## Code

### Terraform ASG
```hcl
resource "aws_launch_template" "app" {
  name_prefix   = "spring-boot-app-"
  image_id      = "ami-0123456789abcdef0"
  instance_type = "t3.medium"

  iam_instance_profile {
    name = aws_iam_instance_profile.app.name
  }

  vpc_security_group_ids = [aws_security_group.app.id]

  user_data = base64encode(file("userdata.sh"))

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size = 30
      volume_type = "gp3"
      encrypted   = true
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags = { Name = "spring-boot-app" }
  }
}

resource "aws_autoscaling_group" "app" {
  name                = "app-asg"
  min_size            = 2
  max_size            = 10
  desired_capacity    = 2
  vpc_zone_identifier = aws_subnet.private[*].id
  target_group_arns   = [aws_lb_target_group.app.arn]
  health_check_type   = "ELB"  # Use ALB health checks
  health_check_grace_period = 120  # Wait 2 min for app to start

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 75
    }
  }

  tag {
    key                 = "Name"
    value               = "spring-boot-app"
    propagate_at_launch = true
  }
}

# Target Tracking Policy
resource "aws_autoscaling_policy" "cpu" {
  name                   = "cpu-target-tracking"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 60.0
  }
}

# Scale on custom metric (request count per target)
resource "aws_autoscaling_policy" "requests" {
  name                   = "request-count-tracking"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.app.arn_suffix}/${aws_lb_target_group.app.arn_suffix}"
    }
    target_value = 1000.0  # 1000 requests per target
  }
}
```

---

## Real Project Usage

### Spring Boot + ASG Configuration

```
Launch Template:
├── AMI: Custom (Java 17 + CloudWatch Agent + app JAR)
├── Instance Type: t3.medium
├── IAM Role: AppRole (S3, SQS, Secrets Manager, CloudWatch)
├── Security Group: Allow 8080 from ALB-SG
└── User Data:
    ├── Start CloudWatch Agent
    └── Start Spring Boot (systemd)

ASG:
├── Min: 2 (always running)
├── Max: 10 (cost limit)
├── Desired: 2 (starting point)
├── Health Check: ELB (ALB checks /actuator/health)
├── Grace Period: 120s (Spring Boot startup time)
└── Scaling: Target CPU 60%

ALB Integration:
├── Target Group: app-tg (port 8080)
├── Health Check: /actuator/health
└── Deregistration Delay: 60s
```

### Scaling for ECS Fargate (Containers)
```hcl
resource "aws_appautoscaling_target" "ecs" {
  max_capacity       = 10
  min_capacity       = 2
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_cpu" {
  name               = "cpu-tracking"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value = 60
  }
}
```

---

## Interview Questions and Answers

**Q: Explain how Auto Scaling works with an ALB.**
> ASG launches instances and registers them with ALB's target group. ALB health checks determine if instances are healthy. If an instance fails health checks, ASG terminates it and launches a replacement (self-healing). Scaling policies add/remove instances based on metrics (CPU, request count). New instances receive traffic only after passing health checks.

**Q: What scaling metric would you use for a Spring Boot REST API?**
> Primary: `ALBRequestCountPerTarget` — directly measures load per instance. Secondary: `ASGAverageCPUUtilization` (target 60-70%). For memory-bound apps: custom CloudWatch metric for JVM heap usage. Request-count-per-target is preferred because it's predictive — CPU reacts after the fact.

**Q: How do you handle rolling deployments with Auto Scaling?**
> Use ASG Instance Refresh: set min_healthy_percentage (e.g., 75%), update launch template with new AMI/config, trigger refresh. ASG replaces instances in batches: terminates old → launches new → waits for health check → continues. With ECS: rolling update strategy with minimumHealthyPercent=100, maximumPercent=200.

**Q: What is a cooldown period and why is it important?**
> Cooldown is the wait time after a scaling action before another can occur (default 300s). It prevents thrashing — without it, metrics from just-launched instances (not yet warmed up) could trigger more scaling, creating an oscillation loop. Set shorter for fast-launching containers, longer for EC2 with slow startup.

---

## Common Mistakes

1. **Health check grace period too short** — Instances killed before app starts up
2. **Max capacity too low** — Can't scale during unexpected traffic spikes
3. **Scaling on wrong metric** — CPU may not correlate with actual load for I/O-bound apps
4. **No scheduled scaling** — Known traffic patterns (9-5) should be pre-scaled
5. **Same AZ instances** — Not spreading across AZs loses the HA benefit

---

## Best Practices

1. **Use target tracking** — simpler, self-adjusting, handles both scale-out and scale-in
2. **Health check grace period** = application startup time + buffer
3. **Min 2, across 2 AZs** — Always maintain HA baseline
4. **Instance refresh for deployments** — Zero-downtime rolling updates
5. **Warm pools** — Pre-initialized instances for faster scaling (when startup is slow)
6. **Multiple metrics** — Scale on both CPU AND request count for safety
7. **Tag instances** — Track Auto Scaling group membership for monitoring/costs

---

## Related Topics
- → [04. EC2](./04-ec2.md)
- → [05. Load Balancing](./05-load-balancing.md)
- → [14. CloudWatch](./14-cloudwatch.md)
