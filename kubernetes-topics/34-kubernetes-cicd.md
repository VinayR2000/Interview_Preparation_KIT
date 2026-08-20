# 34. Kubernetes + CI/CD ⭐⭐

---

## Theory

CI/CD with Kubernetes automates the path from code commit to production deployment, ensuring reliable and repeatable releases.

### CI/CD Pipeline for K8s

```
Code → Build → Test → Docker Build → Push → Deploy → Monitor

┌────┐   ┌───────┐   ┌──────┐   ┌───────┐   ┌──────────┐   ┌─────────┐
│Git │ → │Build/ │ → │Docker│ → │Push to│ → │Deploy to │ → │Monitor/ │
│Push│   │ Test  │   │Build │   │Registry│  │ K8s      │   │Rollback │
└────┘   └───────┘   └──────┘   └───────┘   └──────────┘   └─────────┘
```

### Git

```
Branching strategy for K8s deployments:
  main/master → Production
  develop     → Staging
  feature/*   → Development/Preview

Triggers:
  Push to main     → Deploy to production
  Push to develop  → Deploy to staging
  PR created       → Deploy to preview environment
  Tag created      → Release build
```

### Maven

```
Java/Spring Boot CI step:
  mvn clean package -DskipTests=false
  mvn verify (integration tests)
  
Produces: target/order-service-2.1.0.jar
Used in: Docker build
```

### Docker Build

```dockerfile
# Multi-stage build (production)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN adduser -D appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker build -t registry.example.com/order-service:2.1.0 .
docker build -t registry.example.com/order-service:${GIT_SHA} .
```

### Image Push

```bash
# Push to ECR
aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REGISTRY
docker push $ECR_REGISTRY/order-service:2.1.0
docker push $ECR_REGISTRY/order-service:$GIT_SHA
```

### Kubernetes Deployment

```bash
# Option 1: kubectl
kubectl set image deployment/order-service order-service=registry/order-service:2.1.0

# Option 2: Helm
helm upgrade --install order-service ./chart \
  --set image.tag=2.1.0 \
  -f values-production.yaml \
  -n production

# Option 3: Kustomize
kustomize edit set image order-service=registry/order-service:2.1.0
kubectl apply -k ./overlays/production

# Option 4: GitOps (Argo CD) — preferred
# Push image tag to Git → Argo CD detects → auto-deploys
```

### Helm Deployment

```bash
# CI/CD deploys via Helm
helm upgrade --install order-service ./charts/order-service \
  --namespace production \
  --set image.repository=$ECR_REGISTRY/order-service \
  --set image.tag=$IMAGE_TAG \
  --values ./values/production.yaml \
  --wait \
  --timeout 5m
```

### Jenkins

```groovy
// Jenkinsfile
pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        sh 'mvn clean package'
      }
    }
    stage('Docker Build') {
      steps {
        sh "docker build -t $ECR_REGISTRY/order-service:${env.BUILD_NUMBER} ."
      }
    }
    stage('Push') {
      steps {
        sh "docker push $ECR_REGISTRY/order-service:${env.BUILD_NUMBER}"
      }
    }
    stage('Deploy') {
      steps {
        sh """
          helm upgrade --install order-service ./chart \
            --set image.tag=${env.BUILD_NUMBER} \
            -n production
        """
      }
    }
  }
}
```

### GitHub Actions

```yaml
name: Deploy to EKS
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - name: Build and Test
      run: mvn clean verify
    - name: Docker Build & Push
      run: |
        aws ecr get-login-password | docker login --username AWS --password-stdin $ECR
        docker build -t $ECR/order-service:${{ github.sha }} .
        docker push $ECR/order-service:${{ github.sha }}
    - name: Deploy to EKS
      run: |
        aws eks update-kubeconfig --name my-cluster
        helm upgrade --install order-service ./chart \
          --set image.tag=${{ github.sha }} \
          -n production
```

### Argo CD

```
Argo CD: GitOps continuous delivery for Kubernetes

Principle: Git is the single source of truth
  - Desired state in Git (manifests/Helm/Kustomize)
  - Argo CD syncs cluster state to Git state
  - Any drift is detected and corrected

Flow:
  1. CI pipeline: Build → Test → Push image → Update Git (image tag)
  2. Argo CD: Detects Git change → Syncs to cluster → Rolling update
  3. Rollback: Revert Git commit → Argo CD syncs back

Benefits:
  - Audit trail (Git history)
  - Easy rollback (Git revert)
  - Multi-cluster deployments
  - Automatic drift detection
```

### GitOps

```
GitOps Principles:
  1. Declarative: All config in declarative format (YAML)
  2. Versioned: All changes tracked in Git
  3. Automated: Changes auto-applied by agent (Argo CD/Flux)
  4. Self-healing: Drift detected and corrected automatically

GitOps repo structure:
  gitops-repo/
  ├── apps/
  │   ├── order-service/
  │   │   ├── base/
  │   │   │   ├── deployment.yaml
  │   │   │   ├── service.yaml
  │   │   │   └── kustomization.yaml
  │   │   └── overlays/
  │   │       ├── staging/
  │   │       └── production/
  │   └── payment-service/
  └── infrastructure/
      ├── namespaces/
      ├── monitoring/
      └── ingress/
```

### Deployment Rollback

```
Rollback strategies:

1. kubectl: kubectl rollout undo deployment/order-service
2. Helm: helm rollback order-service 3
3. GitOps: git revert <commit> → Argo CD syncs

Automated rollback:
  - Monitor error rate after deploy
  - If error rate > threshold → auto-rollback
  - Argo Rollouts: Automated analysis + rollback
```

---

## Interview Questions

### Q1: What is GitOps and how does it work with Kubernetes?

**A:** GitOps uses Git as the single source of truth for infrastructure and application state. An agent (Argo CD/Flux) continuously syncs the cluster to match Git. Workflow: CI builds image → updates image tag in Git → Argo CD detects change → deploys to cluster. Benefits: audit trail, easy rollback (git revert), drift detection, declarative everything.

### Q2: How would you design a CI/CD pipeline for K8s microservices?

**A:**
1. **CI (per service):** Compile → Unit test → Integration test → Docker build → Push to ECR → Update GitOps repo with new tag
2. **CD (Argo CD):** Detects Git change → Syncs manifests to cluster → Rolling update with health checks
3. **Validation:** Readiness probes pass → Smoke tests → Progressive rollout (canary)
4. **Rollback:** Automated if error rate spikes, or manual git revert

### Q3: What is the difference between push-based and pull-based CD?

**A:**
- **Push-based (Jenkins, GitHub Actions):** CI pipeline pushes changes to cluster directly (`kubectl apply`, `helm upgrade`). Requires cluster credentials in CI. Simpler but less secure.
- **Pull-based (Argo CD, Flux):** Agent inside cluster polls Git for changes, pulls and applies them. No cluster credentials outside. More secure. Detects/corrects drift. Preferred for production.

---

## Best Practices

1. **Use GitOps** (Argo CD/Flux) for production deployments
2. **Separate CI and CD** — CI builds/tests, CD deploys (different repos)
3. **Immutable image tags** — use Git SHA or semantic version
4. **Automate rollbacks** — monitor error rate, rollback on failure
5. **Test in staging first** — promote same image to production
6. **Use Helm or Kustomize** — not raw kubectl apply
7. **Secret management** — don't store secrets in Git (use sealed-secrets/external-secrets)
8. **Progressive delivery** — canary or blue-green for critical services

---

## Related Topics

- [22. Helm](./22-helm.md)
- [23. Deployment Strategies](./23-deployment-strategies.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
- [36. Production Architecture](./36-production-architecture.md)
