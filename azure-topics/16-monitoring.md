# Azure Monitoring

## Theory

### Azure Monitoring Stack

| Service | Purpose | AWS Equivalent |
|---------|---------|----------------|
| Azure Monitor | Platform for all monitoring data | CloudWatch |
| Application Insights | Application Performance Monitoring (APM) | X-Ray + CloudWatch |
| Log Analytics | Log query and analysis (KQL) | CloudWatch Logs Insights |
| Azure Monitor Alerts | Alerting on metrics/logs | CloudWatch Alarms |
| Azure Dashboards | Visualization | CloudWatch Dashboards |
| Azure Managed Grafana | Grafana as a service | Amazon Managed Grafana |
| Azure Managed Prometheus | Prometheus for AKS | Amazon Managed Prometheus |

---

## Internal Working

### Azure Monitor Architecture ⭐⭐⭐

```
Data Sources                          Azure Monitor                    Actions
├── Applications                         │                            ├── Alerts
│   └── App Insights SDK          ─────► │                            │   ├── Email
├── Infrastructure                       │ ┌──────────────┐           │   ├── SMS
│   ├── VM metrics                ─────► │ │   Metrics    │           │   ├── Azure Function
│   ├── Container Insights        ─────► │ │  (numeric    │ ─────────►│   ├── Logic App
│   └── Network Watcher          ─────► │ │   time-series)│           │   └── Webhook
├── Azure Services                       │ └──────────────┘           │
│   ├── Activity Log              ─────► │                            ├── Dashboards
│   ├── Diagnostic Settings       ─────► │ ┌──────────────┐           │   ├── Azure Dashboards
│   └── Resource Logs             ─────► │ │    Logs      │ ─────────►│   ├── Grafana
├── Custom                               │ │   (KQL       │           │   └── Power BI
│   ├── Custom metrics            ─────► │ │   queryable) │           │
│   └── Custom logs               ─────► │ └──────────────┘           ├── Autoscale
│                                        │                            │   └── Scale based on metrics
└── Network                              │ ┌──────────────┐           │
    └── NSG flow logs             ─────► │ │  Activity    │           └── Export
                                         │ │    Log       │               ├── Storage Account
                                         │ └──────────────┘               ├── Event Hubs
                                         │                                └── Log Analytics
                                         └── Log Analytics Workspace
```

### Metrics vs Logs

| Feature | Metrics | Logs |
|---------|---------|------|
| Data type | Numeric time-series | Structured/unstructured text |
| Example | CPU%, request count, latency | Error stack traces, audit events |
| Query language | Metrics Explorer (visual) | KQL (Kusto Query Language) |
| Retention | 93 days (auto) | Configurable (30-730 days) |
| Cost | Low (aggregated) | Higher (per GB ingested) |
| Alerting | Fast (near real-time) | Slower (query-based) |
| Use case | Real-time monitoring, dashboards | Troubleshooting, analysis |

---

## Application Insights ⭐⭐⭐

### What is Application Insights?
An APM service for monitoring live applications. Auto-collects telemetry from your Spring Boot apps: requests, dependencies, exceptions, performance.

### Architecture for Spring Boot

```
Spring Boot Application
├── Application Insights Java Agent (auto-instrumentation)
│   │
│   ├── Requests (HTTP endpoints called)
│   │   └── GET /api/orders → 200, 45ms
│   ├── Dependencies (external calls made)
│   │   ├── PostgreSQL query → 12ms
│   │   ├── Redis GET → 2ms
│   │   └── HTTP call to payment-service → 150ms
│   ├── Exceptions (unhandled errors)
│   │   └── NullPointerException at OrderService.java:42
│   ├── Custom Events & Metrics
│   │   └── order_placed, revenue_total
│   └── Distributed Traces
│       └── Correlation ID across services
│
└── Sends telemetry → Application Insights → Log Analytics
```

### Spring Boot Integration

```yaml
# applicationinsights.json (agent config)
{
  "connectionString": "InstrumentationKey=xxx;IngestionEndpoint=...",
  "role": {
    "name": "order-service"
  },
  "preview": {
    "sampling": {
      "percentage": 50
    }
  }
}
```

```dockerfile
# Dockerfile with App Insights agent
FROM eclipse-temurin:17-jre
COPY applicationinsights-agent-3.x.x.jar /app/
COPY app.jar /app/
ENTRYPOINT ["java", "-javaagent:/app/applicationinsights-agent-3.x.x.jar", "-jar", "/app/app.jar"]
```

### Distributed Tracing ⭐⭐⭐

```
Client → API Gateway → order-service → payment-service → PostgreSQL
                            │                   │
                            ▼                   ▼
                     Application Insights (Correlation ID: abc-123)

Application Map shows:
order-service ──[150ms]──> payment-service ──[12ms]──> DB
                    │
                    └──[2ms]──> Redis
                    └──[45ms]──> Service Bus

You can see the ENTIRE request path across microservices!
```

### Key Features

| Feature | Description |
|---------|-------------|
| Live Metrics | Real-time request rates, failure rates |
| Application Map | Visual dependency graph between services |
| Transaction Search | Find specific requests/traces |
| Failures | Aggregate error analysis |
| Performance | P50/P95/P99 latency breakdown |
| Availability Tests | Synthetic monitoring (ping tests) |
| Smart Detection | AI-based anomaly detection |

---

## Log Analytics & KQL ⭐⭐

### What is KQL?
Kusto Query Language — used to query all logs in Azure Monitor.

### Common KQL Queries

```kql
// Failed requests in last 24 hours
requests
| where timestamp > ago(24h)
| where success == false
| summarize count() by operation_Name
| order by count_ desc

// Slow requests (>2 seconds)
requests
| where timestamp > ago(1h)
| where duration > 2000
| project timestamp, name, duration, resultCode
| order by duration desc

// Exception trends
exceptions
| where timestamp > ago(7d)
| summarize count() by bin(timestamp, 1h), type
| render timechart

// Dependency failures (DB, Redis, external APIs)
dependencies
| where timestamp > ago(1h)
| where success == false
| summarize count() by target, type, resultCode
| order by count_ desc

// End-to-end transaction trace
union requests, dependencies, exceptions
| where operation_Id == "abc-123-def"
| order by timestamp asc
```

---

## Alerting ⭐⭐⭐

### Alert Types

| Type | Trigger | Use Case |
|------|---------|----------|
| Metric Alert | Metric threshold breached | CPU > 85%, response time > 2s |
| Log Alert | KQL query returns results | Error count > 10 in 5 min |
| Activity Log Alert | Azure resource event | VM deleted, deployment failed |
| Smart Detection | AI detects anomaly | Sudden spike in failures |

### Alert Architecture

```
Alert Rule
├── Condition: requests failures > 10 in 5 minutes
├── Scope: Application Insights (order-service)
│
└── Action Group:
    ├── Email: oncall-team@contoso.com
    ├── SMS: +1-555-0123
    ├── Azure Function: auto-scale-trigger
    ├── Logic App: create-incident-in-PagerDuty
    └── Webhook: https://slack.contoso.com/alerts
```

---

## Container Insights (AKS Monitoring) ⭐⭐⭐

```
AKS Cluster
    │
    ▼
Container Insights (Azure Monitor Agent)
├── Node metrics
│   ├── CPU utilization per node
│   ├── Memory utilization per node
│   └── Disk I/O
├── Pod metrics
│   ├── CPU/memory per pod
│   ├── Pod restarts
│   └── Pod state (Running/Pending/Failed)
├── Container logs
│   ├── stdout → Log Analytics
│   └── stderr → Log Analytics
└── Kubernetes events
    ├── Pod scheduled/evicted
    ├── Node not ready
    └── Image pull failures
```

### Recommended Monitoring Setup for Production

```
Spring Boot on AKS — Full Observability:
│
├── Application Insights (per service)
│   ├── Request/response metrics
│   ├── Dependency tracking
│   ├── Distributed tracing
│   └── Custom business metrics
│
├── Container Insights (cluster level)
│   ├── Node/pod health
│   ├── Container logs
│   └── Kubernetes events
│
├── Azure Monitor Metrics
│   ├── AKS cluster metrics
│   ├── PostgreSQL metrics
│   ├── Redis metrics
│   └── Service Bus metrics
│
├── Azure Managed Prometheus + Grafana
│   ├── Custom Prometheus metrics
│   ├── Service-level dashboards
│   └── SLI/SLO tracking
│
├── Alerts
│   ├── Error rate > threshold
│   ├── P99 latency > 2 seconds
│   ├── Pod restart count > 3
│   ├── CPU > 85% for 5 minutes
│   └── Dead-letter queue messages > 0
│
└── Dashboards
    ├── Service health overview
    ├── Business metrics (orders/min, revenue)
    └── Infrastructure (nodes, pods, storage)
```

---

## Interview Questions

### Q: How do you monitor Spring Boot microservices on Azure?
**A:** Three layers:
1. **Application Insights**: APM for each service — request metrics, dependency tracking, distributed tracing, exceptions. Java agent auto-instruments without code changes.
2. **Container Insights**: AKS cluster health — node metrics, pod status, container logs, Kubernetes events.
3. **Azure Monitor**: Infrastructure metrics — database performance, Redis hit rates, Service Bus queue depth.

Combined with alerts on SLIs (error rate, latency) and Grafana dashboards for visualization.

### Q: What is distributed tracing and how does it work in Azure?
**A:** Distributed tracing tracks a single request as it flows across multiple microservices. Application Insights assigns a correlation ID (operation_Id) to the initial request. As the request propagates (via HTTP headers), all services record their spans under the same correlation ID.

Result: You see the full journey — order-service (45ms) → payment-service (150ms) → database (12ms) — and can pinpoint which service caused a slow response.

### Q: How would you set up alerting for a production microservices system?
**A:**
- **P1 (Critical)**: Error rate > 5%, service down (0 successful requests), database unreachable → PagerDuty + SMS
- **P2 (Warning)**: P99 latency > 2s, CPU > 85% sustained, dead-letter queue > 0 → Slack + email
- **P3 (Info)**: Pod restarts > 3, disk usage > 75%, certificate expiring in 30 days → Email

Use Action Groups to route alerts to the right team/channel. Smart Detection for anomaly-based alerts without manual thresholds.

### Q: What is KQL and where do you use it?
**A:** KQL (Kusto Query Language) is used to query logs in Azure Monitor/Log Analytics. It's a powerful query language for:
- Troubleshooting: Find specific errors across services
- Analysis: Aggregate performance metrics over time
- Reporting: Dashboard queries
- Alerts: Log-based alert conditions

Example: Find all failed requests to order-service in the last hour with their error details and contributing dependency failures.
