# Azure Container Apps

## Theory

### What is Azure Container Apps?
A serverless container platform built on Kubernetes (but abstracts away Kubernetes). Deploy containerized microservices without managing clusters. Supports autoscaling, event-driven workloads, scale-to-zero, Dapr, and KEDA.

Think: "AKS simplicity without Kubernetes complexity."

### Container Apps vs AKS

| Feature | Container Apps | AKS |
|---------|---------------|-----|
| Kubernetes management | Hidden (fully managed) | You manage (nodes, upgrades) |
| kubectl access | No | Yes |
| Scale to zero | Yes (cost savings) | Only with KEDA + careful config |
| Pricing | Per request/vCPU-second | Per node (always running) |
| Service mesh | Dapr (built-in) | Istio/Linkerd (you install) |
| Networking | Simplified | Full control |
| Helm charts | No | Yes |
| Custom CRDs | No | Yes |
| Best for | Simple microservices, event-driven | Complex orchestration, full K8s |

---

## Internal Working

### Architecture

```
Container Apps Environment (shared infrastructure)
│
├── Container App: order-service
│   ├── Revision 1: v2.0.0 (traffic: 0%)
│   ├── Revision 2: v2.1.0 (traffic: 90%) ← Active
│   └── Revision 3: v2.2.0 (traffic: 10%) ← Canary
│
├── Container App: user-service
│   └── Revision 1: v1.5.0 (traffic: 100%)
│
├── Container App: payment-service
│   └── Revision 1: v3.0.0 (traffic: 100%)
│
├── Container App: order-processor (event-driven)
│   ├── Scale: 0 → 50 replicas (based on Service Bus queue)
│   └── KEDA trigger: azure-servicebus
│
└── Shared:
    ├── VNet integration
    ├── Log Analytics
    ├── Dapr components
    └── Managed certificates
```

### Key Concepts

| Concept | Description |
|---------|-------------|
| Environment | Shared boundary for apps (like a namespace/cluster) |
| Container App | Individual app deployment |
| Revision | Immutable snapshot of an app version |
| Replica | Running instance of a revision (like a pod) |
| Ingress | HTTP traffic configuration (external/internal) |
| KEDA Scaler | Event-driven autoscaling trigger |
| Dapr | Distributed Application Runtime (sidecars) |

---

## Autoscaling ⭐⭐⭐

### HTTP-based scaling
```
Container App: order-service
├── Min replicas: 1
├── Max replicas: 30
└── Scale rule:
    └── HTTP: 100 concurrent requests per replica

Traffic: 500 concurrent requests → 5 replicas
Traffic: 0 requests → Scale to min (1 or 0)
```

### Event-driven scaling (KEDA)
```yaml
# Scale based on Service Bus queue length
scale:
  minReplicas: 0    # Scale to zero when idle!
  maxReplicas: 50
  rules:
  - name: servicebus-scaler
    custom:
      type: azure-servicebus
      metadata:
        queueName: order-processing
        messageCount: "10"
```

### Scale to Zero ⭐⭐
- App has 0 replicas when not needed (no cost!)
- First request triggers cold start (few seconds)
- Perfect for: batch processors, queue consumers, scheduled jobs

---

## Traffic Splitting (Canary/Blue-Green) ⭐⭐⭐

```
Container App: order-service
├── Revision: v2.1.0 (label: stable)  → 90% traffic
└── Revision: v2.2.0 (label: canary)  → 10% traffic

Progressive rollout:
Day 1: 90/10
Day 2: 70/30 (if metrics OK)
Day 3: 0/100 (full cutover)
Rollback: 100/0 (instant if issues)
```

---

## Dapr Integration ⭐⭐

Dapr provides building blocks for microservices:

```
Container App: order-service
├── App Container: Spring Boot
└── Dapr Sidecar (auto-injected)
    ├── Service Invocation (call other services by name)
    ├── State Management (Redis-backed)
    ├── Pub/Sub (Service Bus-backed)
    ├── Secrets (Key Vault-backed)
    └── Bindings (input/output to external systems)
```

### Service-to-Service Communication with Dapr
```
order-service → (Dapr sidecar) → (Dapr sidecar) → payment-service
                                   service discovery
                                   retries
                                   mTLS encryption
                                   All automatic!
```

---

## When to Choose Container Apps

```
Choose Container Apps when:
├── Simple microservices (HTTP APIs, event processors)
├── Want scale-to-zero (cost optimization)
├── Don't need Kubernetes expertise on team
├── Event-driven workloads (queue processors)
├── Need quick deployment without infra management
└── Want built-in Dapr/KEDA without setup

Choose AKS when:
├── Need full Kubernetes API access
├── Need custom operators/CRDs
├── Complex networking requirements
├── Need service mesh (Istio) customization
├── Stateful workloads requiring PersistentVolumes
├── Team has Kubernetes expertise
└── Need Helm charts and advanced deployment strategies
```

---

## Interview Questions

### Q: Container Apps vs AKS — when to use which?
**A:** Container Apps is for teams who want to deploy containers without managing Kubernetes. It handles scaling, networking, and service discovery automatically. Use it for simple microservices, event-driven processors, and APIs.

AKS is for teams that need full Kubernetes control — custom networking, Helm charts, CRDs, kubectl access, advanced scheduling, and complex stateful workloads.

Think: Container Apps = "deploy my container." AKS = "I'll manage Kubernetes my way."

### Q: What is scale-to-zero and when is it useful?
**A:** Container Apps can reduce replicas to 0 when there's no work. You pay nothing when scaled to zero. Useful for:
- Queue processors that only need to run when messages arrive
- Scheduled batch jobs
- Low-traffic APIs (acceptable cold start delay)

Not suitable for: Latency-sensitive APIs where cold start is unacceptable.

### Q: How does traffic splitting work in Container Apps?
**A:** Each deployment creates an immutable "revision." You assign traffic percentages across revisions:
- Blue-green: 100% to new revision (instant cutover/rollback)
- Canary: 10% to new revision, monitor, gradually increase
- A/B testing: Split traffic by header or percentage

This is built-in — no need for Istio or custom ingress configuration.
