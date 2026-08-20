# Azure DevOps & CI/CD

## Theory

### What is Azure DevOps?
A suite of development tools for planning, developing, testing, and delivering software. Includes repos, pipelines, boards, artifacts, and test plans.

### Azure DevOps Services

| Service | Purpose | Alternative |
|---------|---------|-------------|
| Azure Repos | Git repositories | GitHub |
| Azure Pipelines | CI/CD pipelines | GitHub Actions |
| Azure Boards | Work item tracking | Jira |
| Azure Artifacts | Package management (Maven, npm) | Nexus, JFrog |
| Azure Test Plans | Manual/automated testing | — |

---

## Internal Working

### CI/CD Pipeline Architecture ⭐⭐⭐

```
Developer
    │
    ▼ (git push)
Azure Repos / GitHub
    │
    ▼ (trigger)
Azure Pipeline
    │
    ├── Stage: Build
    │   ├── mvn clean package -DskipTests
    │   ├── mvn test (unit tests)
    │   ├── SonarQube analysis
    │   ├── docker build -t contosoacr.azurecr.io/order-service:$(Build.BuildId)
    │   └── docker push contosoacr.azurecr.io/order-service:$(Build.BuildId)
    │
    ├── Stage: Deploy to Dev
    │   ├── helm upgrade --install order-service ./helm \
    │   │     --namespace dev --set image.tag=$(Build.BuildId)
    │   └── Run integration tests
    │
    ├── Stage: Deploy to Staging
    │   ├── (Manual approval gate)
    │   ├── helm upgrade --install order-service ./helm \
    │   │     --namespace staging --set image.tag=$(Build.BuildId)
    │   └── Run smoke tests
    │
    └── Stage: Deploy to Production
        ├── (Manual approval gate)
        ├── helm upgrade --install order-service ./helm \
        │     --namespace production --set image.tag=$(Build.BuildId)
        └── Canary / Blue-Green verification
```

### YAML Pipeline Example (Spring Boot → AKS)

```yaml
trigger:
  branches:
    include:
      - main
  paths:
    include:
      - order-service/**

pool:
  vmImage: 'ubuntu-latest'

variables:
  acrName: 'contosoacr'
  imageName: 'order-service'
  tag: '$(Build.BuildId)'

stages:
  - stage: Build
    jobs:
      - job: BuildAndPush
        steps:
          - task: Maven@4
            inputs:
              mavenPomFile: 'order-service/pom.xml'
              goals: 'clean package'
              options: '-DskipTests'
          
          - task: Maven@4
            inputs:
              mavenPomFile: 'order-service/pom.xml'
              goals: 'test'
          
          - task: Docker@2
            inputs:
              containerRegistry: 'acr-service-connection'
              repository: '$(imageName)'
              command: 'buildAndPush'
              Dockerfile: 'order-service/Dockerfile'
              tags: '$(tag)'

  - stage: DeployStaging
    dependsOn: Build
    jobs:
      - deployment: DeployToStaging
        environment: 'staging'
        strategy:
          runOnce:
            deploy:
              steps:
                - task: HelmDeploy@0
                  inputs:
                    connectionType: 'Azure Resource Manager'
                    azureSubscription: 'prod-subscription'
                    azureResourceGroup: 'rg-aks'
                    kubernetesCluster: 'aks-prod'
                    namespace: 'staging'
                    command: 'upgrade'
                    chartType: 'FilePath'
                    chartPath: 'helm/order-service'
                    releaseName: 'order-service'
                    overrideValues: 'image.tag=$(tag)'

  - stage: DeployProduction
    dependsOn: DeployStaging
    jobs:
      - deployment: DeployToProd
        environment: 'production'  # Has approval gate
        strategy:
          runOnce:
            deploy:
              steps:
                - task: HelmDeploy@0
                  inputs:
                    connectionType: 'Azure Resource Manager'
                    azureSubscription: 'prod-subscription'
                    azureResourceGroup: 'rg-aks'
                    kubernetesCluster: 'aks-prod'
                    namespace: 'production'
                    command: 'upgrade'
                    chartType: 'FilePath'
                    chartPath: 'helm/order-service'
                    releaseName: 'order-service'
                    overrideValues: 'image.tag=$(tag)'
```

---

## GitHub Actions + Azure ⭐⭐

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy to AKS

on:
  push:
    branches: [main]

env:
  ACR_NAME: contosoacr
  AKS_CLUSTER: aks-prod
  AKS_RESOURCE_GROUP: rg-aks
  IMAGE_NAME: order-service

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build with Maven
        run: mvn clean package -DskipTests
        working-directory: order-service
      
      - name: Azure Login
        uses: azure/login@v2
        with:
          creds: ${{ secrets.AZURE_CREDENTIALS }}
      
      - name: Build and push to ACR
        run: |
          az acr build --registry $ACR_NAME \
            --image $IMAGE_NAME:${{ github.sha }} \
            order-service/
      
      - name: Set AKS context
        uses: azure/aks-set-context@v3
        with:
          resource-group: ${{ env.AKS_RESOURCE_GROUP }}
          cluster-name: ${{ env.AKS_CLUSTER }}
      
      - name: Deploy to AKS
        run: |
          helm upgrade --install order-service ./helm/order-service \
            --namespace production \
            --set image.tag=${{ github.sha }}
```

---

## Environments & Approvals ⭐⭐⭐

```
Pipeline Environments:
├── dev (auto-deploy on PR merge)
│   └── No approvals
│
├── staging (deploy after build succeeds)
│   └── Auto-approval or optional manual
│
├── production (requires approval)
│   ├── Approvers: [tech-lead, SRE-team]
│   ├── Business hours check
│   ├── Health check gate (previous stage healthy?)
│   └── Max deployment time window
│
└── Rollback:
    └── helm rollback order-service --namespace production
```

---

## Interview Questions

### Q: Describe your CI/CD pipeline for Spring Boot microservices on AKS.
**A:**
1. **Source**: Developer pushes to main branch (or PR merged)
2. **Build**: Maven compiles, runs unit tests, builds JAR
3. **Quality**: SonarQube static analysis, dependency vulnerability scan
4. **Containerize**: Docker multi-stage build → push to ACR
5. **Deploy Dev**: Helm upgrade to dev namespace (auto)
6. **Integration Tests**: Run against dev environment
7. **Deploy Staging**: Helm upgrade to staging (auto/approval)
8. **Smoke Tests**: Verify core functionality
9. **Deploy Production**: Helm upgrade to production (manual approval)
10. **Verification**: Health checks, canary metrics, rollback if needed

### Q: Azure Pipelines vs GitHub Actions — when to use which?
**A:**
- **Azure Pipelines**: Better for complex enterprise pipelines, multi-stage with approvals, integration with Azure Boards, template libraries, self-hosted agents behind corporate firewalls
- **GitHub Actions**: Better when code is already on GitHub, simpler syntax, marketplace of actions, cheaper for open source

Both work well with Azure deployments. Choice usually depends on where your code lives and organizational preferences.

### Q: How do you handle secrets in CI/CD pipelines?
**A:**
- Pipeline variables marked as "secret" (masked in logs)
- Azure Key Vault task to fetch secrets at runtime
- Service connections with Managed Identity (no stored credentials)
- Never hardcode secrets in YAML
- Workload Identity Federation for GitHub Actions (no secret storage)
- Environment-specific variable groups per stage

### Q: How do you implement rollback?
**A:**
- **Helm**: `helm rollback order-service` (reverts to previous release)
- **Kubernetes**: `kubectl rollout undo deployment/order-service`
- **App Service**: Swap slot back (instant)
- **Prevention**: Canary deployments catch issues before full rollout
- **Automated**: Health check gates that auto-rollback on failure
