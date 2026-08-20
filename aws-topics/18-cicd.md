# CI/CD on AWS

## Theory

Continuous Integration and Continuous Deployment — automate the path from code commit to production.

---

## Diagram

### Full Pipeline for Spring Boot Microservices

```
Developer
    │ git push
    ↓
┌─────────────────────── CI/CD Pipeline ───────────────────────┐
│                                                               │
│  ┌─── Build ────┐   ┌─── Test ────┐   ┌─── Deploy ────┐   │
│  │ Compile      │   │ Unit Tests  │   │ Push to ECR   │   │
│  │ Dependencies │──→│ Integration │──→│ Update ECS    │   │
│  │ Docker Build │   │ SonarQube   │   │ Health Check  │   │
│  └──────────────┘   └─────────────┘   └───────────────┘   │
│                                                               │
└───────────────────────────────────────────────────────────────┘
    ↓
Production (ECS Fargate / EKS)
```

### AWS-Native Pipeline

```
CodeCommit / GitHub
    ↓ (trigger)
CodePipeline (orchestrator)
    ├── Source Stage: Pull code
    ├── Build Stage: CodeBuild
    │   ├── mvn clean package
    │   ├── docker build
    │   └── docker push → ECR
    ├── Test Stage: CodeBuild
    │   └── Integration tests
    └── Deploy Stage: CodeDeploy / ECS Deploy
        └── Rolling update / Blue-Green
```

### GitHub Actions Alternative (More Common)

```yaml
# .github/workflows/deploy.yml
name: Deploy to ECS

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      id-token: write  # For OIDC auth to AWS
      contents: read

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Run Tests
        run: mvn test

      - name: Configure AWS Credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::123456789012:role/GitHubActionsRole
          aws-region: us-east-1

      - name: Login to ECR
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build and Push Docker Image
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPO:${{ github.sha }} .
          docker push $ECR_REGISTRY/$ECR_REPO:${{ github.sha }}

      - name: Deploy to ECS
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        with:
          task-definition: task-definition.json
          service: user-service
          cluster: production
          wait-for-service-stability: true
```

---

## Deployment Strategies

| Strategy | Downtime | Rollback | Risk |
|----------|----------|----------|------|
| Rolling | Zero | Slow (redeploy old) | Gradual |
| Blue-Green | Zero | Instant (switch back) | Low |
| Canary | Zero | Instant | Lowest |
| Recreate | Brief | Redeploy | High |

### Rolling (ECS Default)
```
[v1] [v1] [v1] [v1]  ← Start
[v2] [v1] [v1] [v1]  ← Replace one
[v2] [v2] [v1] [v1]  ← Replace another
[v2] [v2] [v2] [v1]  ← Continue
[v2] [v2] [v2] [v2]  ← Complete
```

### Blue-Green (ECS with CodeDeploy)
```
Blue (current): [v1] [v1] [v1]  ← Serving traffic
Green (new):    [v2] [v2] [v2]  ← Ready, health checked
                                    ↓ Switch ALB target group
Blue:           [v1] [v1] [v1]  ← Idle (keep for rollback)
Green:          [v2] [v2] [v2]  ← Now serving traffic
```

---

## Interview Questions and Answers

**Q: Describe your CI/CD pipeline for deploying Spring Boot to AWS.**
> Git push → GitHub Actions (or CodePipeline) triggers. Build: Maven package + Docker build. Test: Unit + integration tests. Security: SonarQube scan, dependency check. Push Docker image to ECR (tagged with git SHA). Deploy: Update ECS task definition with new image, ECS rolling update with health checks. Verify: Wait for service stability, smoke tests. Rollback: circuit breaker auto-rollback on deployment failures.

**Q: How do you achieve zero-downtime deployments?**
> (1) ECS rolling update: minimumHealthyPercent=100, maximumPercent=200 — always maintains full capacity. (2) ALB health checks ensure new tasks are healthy before old ones drain. (3) Deregistration delay (60s) completes in-flight requests. (4) ECS deployment circuit breaker auto-rolls back on repeated failures. (5) Blue-green for instant rollback capability.

**Q: How do you handle database migrations in CI/CD?**
> Separate DB migration from code deployment. Use Flyway/Liquibase. Strategy: (1) Make backward-compatible schema changes (add columns, don't rename/delete). (2) Run migration BEFORE deploying new code. (3) Old code still works with new schema. (4) Deploy new code. (5) Later: cleanup migration to remove unused columns. This decoupling allows safe rollbacks.

---

## Best Practices

1. **OIDC authentication** — GitHub Actions → AWS (no long-lived access keys)
2. **Immutable image tags** — use git SHA, never `:latest` in production
3. **Automated rollback** — ECS deployment circuit breaker
4. **Separate build and deploy** — build once, deploy to multiple environments
5. **Database migrations first** — backward-compatible schema changes
6. **Smoke tests post-deploy** — verify critical paths after deployment
7. **Feature flags** — decouple deployment from feature release
8. **Audit trail** — all deployments logged (who, what, when)

---

## Related Topics
- → [12. ECS and EKS](./12-ecs-eks.md)
- → [17. Terraform](./17-terraform.md)
- → [19. High Availability and DR](./19-high-availability-disaster-recovery.md)
