# 18. Docker in CI/CD ⭐⭐⭐

---

## Theory

**Docker in CI/CD** standardizes build, test, and deployment pipelines. Every stage runs in containers — consistent, reproducible, and portable across CI platforms (Jenkins, GitHub Actions, GitLab CI).

### Why Docker in CI/CD?

```
Without Docker:
  - "Tests pass locally but fail in CI" (different JDK, OS, deps)
  - CI server polluted with multiple JDK/Node/Python versions
  - Flaky tests due to shared state between builds
  - Different environments: dev ≠ staging ≠ production

With Docker:
  - Same image runs everywhere (dev → CI → staging → prod)
  - Isolated builds (no shared state)
  - Reproducible (Dockerfile = build recipe)
  - Fast (layer caching between builds)
  - Portable (same pipeline on any CI platform)
```

### CI/CD Pipeline with Docker

```
┌──────────────────────────────────────────────────────────────┐
│                    CI/CD PIPELINE                              │
│                                                               │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────┐ │
│  │  Build   │──▶│   Test   │──▶│   Push   │──▶│ Deploy  │ │
│  │  Image   │   │ (in Docker)│  │ to Registry│  │(K8s/ECS)│ │
│  └──────────┘   └──────────┘   └──────────┘   └─────────┘ │
│       │               │              │               │       │
│  docker build    docker run      docker push    kubectl      │
│  --target test   tests in        to ECR/GCR    set image    │
│                  container                                    │
└──────────────────────────────────────────────────────────────┘

Image Tagging Strategy:
  myapp:latest          ← Don't use in production!
  myapp:1.2.3           ← Semantic version (releases)
  myapp:abc1234         ← Git commit SHA (traceability)
  myapp:main-abc1234    ← Branch + SHA
  myapp:pr-42           ← Pull request builds
```

### Docker Layer Caching in CI

```
Problem: CI builds from scratch every time (slow!)
Solution: Cache Docker layers between builds

Strategies:
  1. Registry-based caching (pull previous image as cache)
  2. BuildKit cache mounts (mount cache directories)
  3. GitHub Actions cache (local cache between runs)
  4. CI-native caching (GitLab CI Docker layer cache)
```

```bash
# Registry-based caching
docker pull myregistry/myapp:latest || true
docker build \
  --cache-from myregistry/myapp:latest \
  -t myregistry/myapp:$SHA \
  -t myregistry/myapp:latest \
  .
docker push myregistry/myapp:$SHA
docker push myregistry/myapp:latest
```

```bash
# BuildKit inline cache
DOCKER_BUILDKIT=1 docker build \
  --build-arg BUILDKIT_INLINE_CACHE=1 \
  --cache-from myregistry/myapp:latest \
  -t myregistry/myapp:$SHA \
  .
```

### Image Tagging Strategy

```bash
# Semantic versioning for releases
docker tag myapp:build myregistry/myapp:1.2.3
docker tag myapp:build myregistry/myapp:1.2
docker tag myapp:build myregistry/myapp:1

# Git SHA for traceability
docker tag myapp:build myregistry/myapp:$(git rev-parse --short HEAD)

# Branch-based for development
docker tag myapp:build myregistry/myapp:main-$(git rev-parse --short HEAD)

# NEVER rely on :latest in production
# :latest is mutable — you can't rollback to "the last latest"
```

### Image Scanning in CI

```bash
# Scan before pushing to registry
# Trivy (open source, fast)
trivy image --severity HIGH,CRITICAL myapp:1.0
# Exit code 1 if vulnerabilities found → fail the pipeline

# Docker Scout
docker scout cves myapp:1.0

# Snyk
snyk container test myapp:1.0

# Policy: block deployment if CRITICAL CVEs found
trivy image --exit-code 1 --severity CRITICAL myapp:1.0
```

---

## Code

### GitHub Actions Pipeline:

```yaml
# .github/workflows/ci-cd.yml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Cache Docker layers
        uses: actions/cache@v4
        with:
          path: /tmp/.buildx-cache
          key: ${{ runner.os }}-buildx-${{ github.sha }}
          restore-keys: ${{ runner.os }}-buildx-

      - name: Build test image
        uses: docker/build-push-action@v5
        with:
          context: .
          target: build
          load: true
          tags: myapp:test
          cache-from: type=local,src=/tmp/.buildx-cache
          cache-to: type=local,dest=/tmp/.buildx-cache-new,mode=max

      - name: Run tests
        run: |
          docker compose -f compose-test.yaml up --abort-on-container-exit
          docker compose -f compose-test.yaml down -v

      - name: Move cache
        run: |
          rm -rf /tmp/.buildx-cache
          mv /tmp/.buildx-cache-new /tmp/.buildx-cache

  push:
    needs: build-and-test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Log in to registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: .
          target: production
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
          cache-from: type=registry,ref=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest

      - name: Scan image
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
          exit-code: '1'
          severity: 'CRITICAL,HIGH'

  deploy:
    needs: push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/order-service \
            order-service=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
```

### GitLab CI Pipeline:

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - scan
  - deploy

variables:
  IMAGE: $CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA
  IMAGE_LATEST: $CI_REGISTRY_IMAGE:latest

build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker pull $IMAGE_LATEST || true
    - docker build --cache-from $IMAGE_LATEST -t $IMAGE -t $IMAGE_LATEST .
    - docker push $IMAGE
    - docker push $IMAGE_LATEST

test:
  stage: test
  image: docker:24
  services:
    - docker:24-dind
  script:
    - docker compose -f compose-test.yaml run --rm tests
  artifacts:
    reports:
      junit: test-results/*.xml

scan:
  stage: scan
  image: aquasec/trivy:latest
  script:
    - trivy image --exit-code 1 --severity CRITICAL $IMAGE
  allow_failure: false

deploy-staging:
  stage: deploy
  script:
    - kubectl set image deployment/api api=$IMAGE
  environment:
    name: staging
  only:
    - main
```

### Testing with Docker Compose:

```yaml
# compose-test.yaml — Integration tests with real dependencies
services:
  tests:
    build:
      context: .
      target: build
    command: mvn verify -Pintegration-test
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/testdb
      - SPRING_DATASOURCE_USERNAME=test
      - SPRING_DATASOURCE_PASSWORD=test
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: testdb
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U test"]
      interval: 3s
      timeout: 2s
      retries: 5

  redis:
    image: redis:7-alpine
```

```bash
# Run integration tests in CI
docker compose -f compose-test.yaml up --abort-on-container-exit --exit-code-from tests
EXIT_CODE=$?
docker compose -f compose-test.yaml down -v
exit $EXIT_CODE
```

### Dockerfile for CI (Multi-Target):

```dockerfile
# ─── Dependencies ───
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ─── Build ───
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY --from=deps /root/.m2 /root/.m2
COPY pom.xml .
COPY src/ src/
RUN mvn package -DskipTests -o

# ─── Test (CI can stop here) ───
FROM build AS test
RUN mvn test -o

# ─── Production ───
FROM eclipse-temurin:21-jre-alpine AS production
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## Interview Questions

### Q1: How does Docker improve CI/CD pipelines?

**A:** Docker provides:
1. **Consistency** — same image in CI, staging, production (no environment drift)
2. **Isolation** — builds don't affect each other (no shared state)
3. **Speed** — layer caching makes subsequent builds fast
4. **Portability** — same pipeline works on any CI platform
5. **Reproducibility** — Dockerfile is the exact build recipe

### Q2: How do you handle Docker layer caching in CI?

**A:** CI environments are ephemeral (no local cache between runs). Solutions:
1. **Registry-based:** Pull previous image as cache source (`--cache-from`)
2. **BuildKit cache:** Export/import cache to CI storage
3. **CI cache action:** Save buildx cache directory between runs
4. **Dependency ordering:** Put rarely-changing layers first (pom.xml before src/)

### Q3: What is your Docker image tagging strategy?

**A:** 
- **Git SHA** (`abc1234`) — immutable, traceable to exact commit
- **Semver** (`1.2.3`) — for releases, human-readable
- **Branch+SHA** (`main-abc1234`) — for development builds
- Never rely on `:latest` for production — it's mutable and unrollbackable

### Q4: How do you run integration tests with Docker in CI?

**A:** Use Docker Compose with real dependencies:
1. Define `compose-test.yaml` with app + postgres + redis + kafka
2. Use healthchecks to ensure dependencies are ready
3. Run tests: `docker compose up --abort-on-container-exit --exit-code-from tests`
4. Clean up: `docker compose down -v`

This gives real integration testing without mocking infrastructure.

---

## Best Practices

1. **Multi-stage Dockerfile** — separate build/test/production targets
2. **Cache dependencies** — separate layer for pom.xml/package.json
3. **Scan images** — fail pipeline on CRITICAL vulnerabilities
4. **Immutable tags** — use Git SHA, never deploy `:latest`
5. **Integration tests in containers** — real databases, not mocks
6. **Cache Docker layers in CI** — registry-based or BuildKit cache
7. **Minimal production images** — alpine/distroless, no build tools
8. **Sign images** — Docker Content Trust for supply chain security
9. **Fail fast** — run linting and unit tests before slow image build
10. **Clean up** — `docker compose down -v` after tests

---

## Related Topics

- [14. Multi-Stage Builds](./14-docker-multi-stage-builds.md)
- [13. Docker Security](./13-docker-security.md)
- [09. Docker Registry](./09-docker-registry.md)
- [19. Docker Best Practices](./19-docker-best-practices.md)
