# 21. Docker Orchestration Overview ⭐⭐

---

## Theory

**Container orchestration** manages the lifecycle of containers at scale across multiple hosts. It handles scheduling, scaling, networking, service discovery, and self-healing — everything Docker alone can't do in production.

### Why Orchestration?

```
Docker alone (single host):
  ✗ No auto-restart on crash (only restart policy)
  ✗ No scaling across multiple machines
  ✗ No rolling updates
  ✗ No load balancing (native)
  ✗ No service discovery across hosts
  ✗ No resource-aware scheduling
  ✗ No secrets management at scale
  ✗ Single point of failure (one host dies = everything dies)

With Orchestration:
  ✓ Auto-healing (container dies → restarted automatically)
  ✓ Horizontal scaling (replicas across nodes)
  ✓ Rolling updates (zero downtime deploys)
  ✓ Service discovery (DNS-based, dynamic)
  ✓ Load balancing (built-in)
  ✓ Resource scheduling (place on best-fit node)
  ✓ Secrets management (encrypted at rest)
  ✓ High availability (multi-node, no SPOF)
```

### Orchestration Options

```
┌───────────────────────────────────────────────────────────────┐
│              ORCHESTRATION PLATFORMS                            │
├───────────────────────────────────────────────────────────────┤
│                                                                │
│  Kubernetes (K8s)                                             │
│    - Industry standard, most widely adopted                   │
│    - Complex but extremely powerful                           │
│    - Managed: EKS (AWS), GKE (Google), AKS (Azure)          │
│    - Best for: large-scale production workloads              │
│                                                                │
│  Docker Swarm                                                  │
│    - Built into Docker Engine                                 │
│    - Simple (uses compose files!)                             │
│    - Limited scaling and features                             │
│    - Best for: simple deployments, learning                   │
│    - Status: Maintenance mode (Docker focuses on K8s)        │
│                                                                │
│  AWS ECS/Fargate                                              │
│    - AWS-native container orchestration                       │
│    - Fargate = serverless (no EC2 management)                │
│    - Deep AWS integration (IAM, ALB, CloudWatch)             │
│    - Best for: AWS-centric teams                             │
│                                                                │
│  Nomad (HashiCorp)                                            │
│    - Supports containers + non-container workloads           │
│    - Simpler than K8s, more flexible                         │
│    - Best for: multi-workload scheduling                     │
│                                                                │
└───────────────────────────────────────────────────────────────┘
```

### Docker Compose vs Swarm vs Kubernetes

```
┌──────────────────┬────────────────┬────────────────┬──────────────────┐
│ Feature          │ Compose        │ Swarm          │ Kubernetes       │
├──────────────────┼────────────────┼────────────────┼──────────────────┤
│ Hosts            │ Single         │ Multi-node     │ Multi-node       │
│ Use case         │ Dev/Testing    │ Simple prod    │ Production       │
│ Scaling          │ Manual         │ Basic          │ Advanced (HPA)   │
│ Self-healing     │ restart policy │ Yes            │ Yes (advanced)   │
│ Rolling updates  │ No             │ Basic          │ Advanced         │
│ Service mesh     │ No             │ No             │ Istio/Linkerd    │
│ RBAC             │ No             │ Basic          │ Advanced         │
│ Secrets          │ File-based     │ Encrypted      │ Encrypted + Vault│
│ Networking       │ Bridge         │ Overlay        │ CNI plugins      │
│ Complexity       │ Low            │ Medium         │ High             │
│ Community        │ Large          │ Declining      │ Massive          │
│ Learning curve   │ Easy           │ Moderate       │ Steep            │
└──────────────────┴────────────────┴────────────────┴──────────────────┘
```

### Docker Swarm (Brief Overview)

```bash
# Initialize Swarm (current node becomes manager)
docker swarm init

# Join worker nodes
docker swarm join --token <worker-token> <manager-ip>:2377

# Deploy a stack (uses compose file!)
docker stack deploy -c compose.yaml myapp

# Scale a service
docker service scale myapp_api=5

# Rolling update
docker service update --image myapp:2.0 myapp_api

# List services
docker service ls
docker service ps myapp_api
```

```yaml
# Swarm-compatible compose.yaml
services:
  api:
    image: myapp:1.0
    deploy:
      replicas: 3
      update_config:
        parallelism: 1
        delay: 10s
        failure_action: rollback
      rollback_config:
        parallelism: 0
        order: stop-first
      restart_policy:
        condition: on-failure
        max_attempts: 3
      resources:
        limits:
          memory: 512M
          cpus: "1.0"
    ports:
      - "8080:8080"
    networks:
      - backend

networks:
  backend:
    driver: overlay    # Multi-host networking
```

### When to Use What

```
Use Docker Compose when:
  - Local development
  - CI/CD testing
  - Single-server hobby projects
  - Demos and prototypes

Use Docker Swarm when:
  - Small team, simple needs
  - Already using Docker Compose (minimal changes)
  - Don't need advanced orchestration features
  - Quick setup without K8s complexity

Use Kubernetes when:
  - Production workloads at scale
  - Need auto-scaling (HPA, VPA, cluster autoscaler)
  - Multiple teams/namespaces
  - Complex networking (service mesh)
  - Compliance requirements (RBAC, audit logs)
  - Multi-cloud / hybrid-cloud strategy

Use ECS/Fargate when:
  - All-in on AWS
  - Want serverless containers (no node management)
  - Deep AWS service integration needed
  - Team doesn't want to manage K8s control plane
```

---

## Code

### Docker Swarm Deployment Example:

```yaml
# stack.yaml — Production Swarm deployment
version: "3.8"

services:
  api:
    image: registry.io/order-service:1.2.3
    deploy:
      replicas: 3
      update_config:
        parallelism: 1
        delay: 15s
        order: start-first
        failure_action: rollback
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
      resources:
        limits:
          memory: 512M
          cpus: "1.0"
        reservations:
          memory: 256M
          cpus: "0.25"
      placement:
        constraints:
          - node.role == worker
    ports:
      - "8080:8080"
    secrets:
      - db_password
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DB_HOST=postgres
      - DB_PASSWORD_FILE=/run/secrets/db_password
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s
    networks:
      - frontend
      - backend

  postgres:
    image: postgres:15-alpine
    deploy:
      replicas: 1
      placement:
        constraints:
          - node.labels.db == true
    volumes:
      - postgres-data:/var/lib/postgresql/data
    secrets:
      - db_password
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    networks:
      - backend

  nginx:
    image: nginx:alpine
    deploy:
      replicas: 2
      update_config:
        parallelism: 1
    ports:
      - "80:80"
      - "443:443"
    networks:
      - frontend

secrets:
  db_password:
    external: true

volumes:
  postgres-data:
    driver: local

networks:
  frontend:
    driver: overlay
  backend:
    driver: overlay
    internal: true
```

```bash
# Deploy the stack
docker stack deploy -c stack.yaml order-app

# Check deployment status
docker stack services order-app
docker service ps order-app_api

# Scale
docker service scale order-app_api=5

# Update image (rolling)
docker service update --image registry.io/order-service:1.3.0 order-app_api

# Rollback
docker service rollback order-app_api

# Remove stack
docker stack rm order-app
```

---

## Interview Questions

### Q1: Why can't you just use Docker Compose in production?

**A:** Docker Compose is single-host only:
- No high availability (host dies = everything dies)
- No automatic scaling across machines
- No rolling updates (just recreate containers)
- No resource-aware scheduling
- No built-in service discovery across hosts
- No encrypted secrets (at rest)

Production needs multi-node orchestration (Kubernetes, ECS) for reliability.

### Q2: Docker Swarm vs Kubernetes — when to use which?

**A:**
- **Swarm:** Small teams, simple apps, already using Docker Compose (reuse YAML), quick setup (5 min), limited advanced features
- **Kubernetes:** Enterprise production, complex applications, auto-scaling, service mesh, RBAC, multi-cloud, massive ecosystem, industry standard

Most companies choose Kubernetes. Swarm is in maintenance mode.

### Q3: What is a rolling update and how does Docker handle it?

**A:** A rolling update replaces container instances one at a time:
1. Start new container with new image
2. Wait until healthy
3. Stop one old container
4. Repeat until all replaced

In Swarm: `docker service update --image myapp:2.0`. In Kubernetes: `kubectl set image deployment/api api=myapp:2.0`. Configuration controls parallelism, delay, and rollback behavior.

### Q4: What is the overlay network in Docker Swarm?

**A:** Overlay network spans multiple Docker hosts, allowing containers on different machines to communicate as if on the same network. It uses VXLAN encapsulation. Services connect by name (DNS-based discovery). Bridge networks are single-host; overlay is multi-host.

---

## Best Practices

1. **Don't use Compose in production** — use Kubernetes or managed services
2. **Health checks are mandatory** — orchestrators rely on them for self-healing
3. **Graceful shutdown** — handle SIGTERM, drain connections
4. **External state** — databases outside orchestration (managed RDS, etc.)
5. **Immutable deployments** — never update containers in place
6. **Resource limits** — let the scheduler make good placement decisions
7. **Multiple replicas** — minimum 2 for availability
8. **Rolling updates** — zero-downtime deployments
9. **Use managed K8s** — EKS, GKE, AKS (don't manage control plane yourself)
10. **GitOps** — infrastructure as code, version-controlled deployments

---

## Related Topics

- [12. Docker Compose](./12-docker-compose.md)
- [19. Docker Best Practices](./19-docker-best-practices.md)
- [20. Docker Interview Scenarios](./20-docker-interview-scenarios.md)
- [Kubernetes Fundamentals](../kubernetes-topics/01-kubernetes-fundamentals.md)
