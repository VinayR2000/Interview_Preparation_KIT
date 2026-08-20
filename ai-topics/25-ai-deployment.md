# AI Deployment

## Overview
Deploying AI applications uses the same infrastructure patterns you already know — Docker, Kubernetes, CI/CD — with AI-specific considerations for model configuration, secrets management, and scaling characteristics.

---

## Docker for AI Services

```dockerfile
# Dockerfile for Spring Boot AI Service
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Add health check dependencies
RUN apk add --no-cache curl

# Copy the built artifact
COPY target/ai-service-*.jar app.jar

# AI services may need more memory for embedding caches
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"

# Don't store secrets in image
# Use environment variables or secrets manager at runtime

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Multi-Stage Build
```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/ai-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-service
  labels:
    app: ai-service
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0  # Zero downtime
  selector:
    matchLabels:
      app: ai-service
  template:
    metadata:
      labels:
        app: ai-service
    spec:
      containers:
        - name: ai-service
          image: your-registry/ai-service:v1.2.3
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "1Gi"
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "production"
            - name: SPRING_AI_OPENAI_API_KEY
              valueFrom:
                secretKeyRef:
                  name: ai-secrets
                  key: openai-api-key
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                configMapKeyRef:
                  name: ai-config
                  key: database-url
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          startupProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 10
---
apiVersion: v1
kind: Service
metadata:
  name: ai-service
spec:
  selector:
    app: ai-service
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
```

---

## Secrets Management

```yaml
# Kubernetes Secret
apiVersion: v1
kind: Secret
metadata:
  name: ai-secrets
type: Opaque
data:
  openai-api-key: base64-encoded-key
  bedrock-access-key: base64-encoded-key
  database-password: base64-encoded-password

---
# Better: Use External Secrets Operator with AWS Secrets Manager
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: ai-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-store
    kind: ClusterSecretStore
  target:
    name: ai-secrets
  data:
    - secretKey: openai-api-key
      remoteRef:
        key: /ai-service/openai-api-key
    - secretKey: database-password
      remoteRef:
        key: /ai-service/database-password
```

---

## Environment Management

```yaml
# application-production.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.3  # Lower in production for consistency
    vectorstore:
      pgvector:
        dimensions: 1536
        index-type: HNSW
        distance-type: COSINE_DISTANCE
  datasource:
    url: ${DATABASE_URL}
    hikari:
      maximum-pool-size: 20

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    org.springframework.ai: INFO  # Not DEBUG in production
```

---

## Horizontal Scaling & Autoscaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ai-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ai-service
  minReplicas: 2
  maxReplicas: 20
  metrics:
    # Scale on CPU
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    # Scale on custom metric (request queue depth)
    - type: Pods
      pods:
        metric:
          name: ai_pending_requests
        target:
          type: AverageValue
          averageValue: "5"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300  # Slow scale-down
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
```

### Scaling Considerations for AI
```
AI services scale differently:
- Latency is dominated by LLM API call (not your code)
- More replicas = more concurrent LLM calls
- But LLM providers have rate limits
- Scale based on queue depth, not CPU

Key metrics for scaling:
- Pending request count
- Average response time
- LLM API rate limit remaining
- Token budget consumption rate
```

---

## CI/CD Pipeline

```yaml
# GitHub Actions / GitLab CI example
name: AI Service Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build & Test
        run: mvn clean verify
      
      - name: AI Evaluation Tests
        run: mvn test -Dtest=AIEvaluationTest
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
      
      - name: Build Docker Image
        run: docker build -t ai-service:${{ github.sha }} .
      
      - name: Push to Registry
        run: |
          docker tag ai-service:${{ github.sha }} $ECR_REGISTRY/ai-service:${{ github.sha }}
          docker push $ECR_REGISTRY/ai-service:${{ github.sha }}
      
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/ai-service \
            ai-service=$ECR_REGISTRY/ai-service:${{ github.sha }}
          kubectl rollout status deployment/ai-service --timeout=300s
      
      - name: Post-Deploy Smoke Test
        run: |
          # Quick health check
          curl -f https://ai-service.internal/actuator/health
          # Quick AI functionality check
          curl -X POST https://ai-service.internal/api/v1/ai/chat \
            -H "Content-Type: application/json" \
            -d '{"message": "Hello, are you working?"}' | grep -q "content"
```

---

## Observability in Production

```yaml
# Prometheus ServiceMonitor
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: ai-service-monitor
spec:
  selector:
    matchLabels:
      app: ai-service
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s

---
# Grafana Dashboard queries
# Token usage rate: rate(ai_tokens_total[5m])
# Request latency: histogram_quantile(0.95, ai_chat_duration_seconds_bucket)
# Error rate: rate(ai_chat_requests_total{status="error"}[5m])
# Cost per hour: increase(ai_cost_usd_total[1h])
```

---

## Interview Questions

**Q: How does deploying an AI service differ from a regular Spring Boot service?**
Higher memory requirements (embedding caches, model data), longer startup times (model initialization), external API dependencies (LLM providers), secret management for API keys, different scaling characteristics (scale on queue depth, not CPU), need for AI-specific health checks (can the model respond?), and cost monitoring as a first-class concern.

**Q: How do you handle zero-downtime deployments for AI services?**
Rolling updates with maxUnavailable: 0, startup probes with generous timeout (models load slowly), readiness probes that verify LLM connectivity, graceful shutdown (complete in-flight requests), and connection draining for streaming responses.

**Q: How do you manage model configuration across environments?**
ConfigMaps for model selection and parameters (dev: cheap model, staging: production model with lower rate, prod: full production). Secrets for API keys. Feature flags for gradual rollout of new models. Environment-specific temperature/token limits.

---

## Key Takeaways

1. **Same infrastructure, new considerations** — Docker, K8s, CI/CD apply directly
2. **Memory and startup** — AI services need more of both
3. **Secrets are critical** — API keys must never be in code or images
4. **Scale on queue depth** — not CPU utilization
5. **AI evaluation in CI/CD** — catch quality regressions before deploy
6. **Observability** — token usage and cost are new key metrics
