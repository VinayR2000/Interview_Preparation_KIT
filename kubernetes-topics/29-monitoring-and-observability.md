# 29. Monitoring & Observability ⭐⭐

---

## Theory

Observability in Kubernetes covers three pillars: metrics, logs, and traces — providing visibility into cluster and application health.

### Metrics

```
Types of metrics:
  System: CPU, memory, disk, network (per node/pod)
  Application: Request count, latency, error rate
  Kubernetes: Pod restarts, scheduling latency, API Server latency

Metrics pipeline:
  Application → Prometheus scrapes → stores time-series →
  Grafana queries → dashboards/alerts
```

### Logs

```
Log sources:
  - Container stdout/stderr (kubectl logs)
  - Application log files (mounted volumes)
  - Kubernetes events (kubectl get events)
  - Audit logs (API Server operations)

Log pipeline:
  Container → Node filesystem → DaemonSet (fluent-bit) →
  Aggregation (Elasticsearch/CloudWatch) → Query/Dashboard (Kibana)
```

### Traces

```
Distributed tracing: Follow a request across microservices

Request → API Gateway → Order Service → Payment Service → DB
  Trace ID: abc123 (same across all services)
  Span: Individual operation within the trace

Tools: Jaeger, Zipkin, OpenTelemetry, AWS X-Ray
```

### Metrics Server

```
Metrics Server: Lightweight, in-cluster metrics aggregator

Provides:
  - kubectl top pods/nodes
  - HPA CPU/memory metrics
  
Does NOT provide:
  - Historical data (only current snapshot)
  - Custom metrics
  - Alerting

Install:
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

### Prometheus

```
Prometheus: Time-series metrics database

Architecture:
  1. Prometheus Server: Scrapes targets, stores metrics
  2. Service Discovery: Finds pods/services to scrape
  3. Alertmanager: Routes and manages alerts
  4. Grafana: Visualization and dashboards

In K8s:
  - Auto-discovers pods via annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/metrics"
  
  - Uses ServiceMonitor CRDs (prometheus-operator)

Key metrics for K8s:
  container_cpu_usage_seconds_total
  container_memory_usage_bytes
  kube_pod_status_phase
  kube_deployment_status_replicas_available
  apiserver_request_total
  etcd_server_has_leader
```

### Grafana

```
Grafana: Metrics visualization and dashboarding

Features:
  - Pre-built K8s dashboards
  - Custom dashboards
  - Multi-datasource (Prometheus, CloudWatch, etc.)
  - Alerting integration

Essential K8s dashboards:
  - Cluster overview (nodes, pods, resources)
  - Node metrics (CPU, memory, disk per node)
  - Pod metrics (resource usage per pod)
  - API Server (request rate, latency, errors)
  - etcd (leader changes, disk I/O)
```

### Alertmanager

```
Alertmanager: Route, group, and manage Prometheus alerts

Alert rules in Prometheus:
  - Pod CrashLoopBackOff > 5 minutes
  - Node NotReady > 2 minutes
  - CPU > 90% for 5 minutes
  - Memory > 85% for 5 minutes
  - Disk > 80%
  - API Server latency > 1s
  - etcd leader changes > 3 in 1 hour

Routing:
  Critical → PagerDuty (wake up on-call)
  Warning → Slack channel
  Info → Email digest
```

### OpenTelemetry

```
OpenTelemetry: Unified observability framework

Provides:
  - Metrics (replace Prometheus client)
  - Traces (replace Jaeger/Zipkin client)
  - Logs (structured logging)
  - Single SDK for all three

In K8s:
  - OpenTelemetry Collector (DaemonSet or Deployment)
  - Collects from applications
  - Exports to any backend (Prometheus, Jaeger, CloudWatch, etc.)
```

### Container Logs

```
Container logs in K8s:
  - stdout/stderr captured by container runtime
  - Stored on node: /var/log/containers/
  - Accessed via: kubectl logs <pod>
  - Rotated by kubelet (max 10MB per container, 5 files)
  - Lost when pod is deleted (unless aggregated)
```

### Kubernetes Events

```
Events: System notifications about object state changes

Types:
  Normal:  Successful operations (Scheduled, Pulled, Started)
  Warning: Issues (FailedScheduling, Unhealthy, FailedMount)

Default retention: 1 hour
For persistent events: Export to external system

kubectl get events --sort-by=.lastTimestamp
kubectl get events --field-selector type=Warning
```

---

## Interview Questions

### Q1: What monitoring stack would you use for a production K8s cluster?

**A:** Prometheus + Grafana + Alertmanager (metrics), Fluent Bit + Elasticsearch + Kibana (logs), Jaeger/Tempo (traces). Or OpenTelemetry Collector as unified pipeline. Key metrics: pod resource usage, node health, API Server latency, application error rates. Key alerts: OOMKill, CrashLoopBackOff, NodeNotReady, high latency.

### Q2: What is the difference between metrics-server and Prometheus?

**A:**
- **Metrics-server:** Lightweight, current-snapshot only (no history). Powers `kubectl top` and HPA. Built-in K8s component.
- **Prometheus:** Full time-series database. Historical data, custom metrics, alerting, PromQL queries. Scrapes from many sources. Industry standard for K8s monitoring.

### Q3: What are the essential Kubernetes metrics to monitor?

**A:**
- **Cluster:** Node count, node conditions, API Server availability
- **Node:** CPU/memory/disk utilization, network I/O
- **Pod:** CPU/memory usage vs requests, restart count, OOMKills
- **Application:** Request rate, error rate, latency (RED method)
- **Control plane:** API Server latency, etcd disk I/O, scheduler queue depth

---

## Best Practices

1. **Deploy Prometheus + Grafana** (or cloud equivalent) from day one
2. **Set up alerting** — don't just monitor, act on issues
3. **Use RED method** for services: Rate, Errors, Duration
4. **Use USE method** for infrastructure: Utilization, Saturation, Errors
5. **Aggregate logs centrally** — don't rely on `kubectl logs`
6. **Retain metrics** — at least 15 days for troubleshooting
7. **Dashboard per service** — not just cluster-wide
8. **Implement distributed tracing** for microservices debugging

---

## Related Topics

- [30. Logging](./30-logging.md)
- [28. Troubleshooting](./28-troubleshooting.md)
- [36. Production Architecture](./36-production-architecture.md)
- [17. Scaling](./17-scaling.md)
