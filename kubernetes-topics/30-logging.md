# 30. Logging

---

## Theory

Centralized logging in Kubernetes collects container logs from all nodes and aggregates them for search, analysis, and alerting.

### Container Logs

```
Container log flow:
  Application → stdout/stderr → Container Runtime →
  Node filesystem (/var/log/containers/) → Log collector → Central store

kubelet log rotation:
  - Max 10MB per container log file
  - Max 5 rotated files per container
  - Total: ~50MB per container on node disk

When pod is deleted:
  - Logs on node are eventually cleaned up
  - Without centralized logging: logs are LOST
```

### kubectl logs

```bash
kubectl logs <pod>                    # Current logs
kubectl logs <pod> --previous         # Crashed container logs
kubectl logs <pod> -c <container>     # Multi-container pod
kubectl logs <pod> -f                 # Stream (follow)
kubectl logs <pod> --since=1h         # Last hour
kubectl logs <pod> --tail=200         # Last 200 lines
kubectl logs -l app=my-app            # All pods matching label
```

### Log Aggregation

```
Log aggregation patterns:

1. Node-level agent (DaemonSet) — RECOMMENDED:
   DaemonSet on each node → reads /var/log/containers →
   forwards to central store
   
   Pros: No app changes, efficient, standard
   Cons: Only stdout/stderr

2. Sidecar container:
   Each pod has log-forwarder sidecar →
   reads log files from shared volume → forwards
   
   Pros: Custom log files, per-pod config
   Cons: Extra resource per pod

3. Direct push (application):
   Application → directly pushes logs to central store
   
   Pros: Structured logging control
   Cons: Coupling, SDK dependency
```

### Fluent Bit

```
Fluent Bit: Lightweight log processor (preferred for K8s)

Features:
  - Extremely lightweight (~450KB)
  - Native K8s metadata enrichment
  - Multiple outputs (Elasticsearch, CloudWatch, S3, etc.)
  - Filtering, parsing, buffering
  
Runs as DaemonSet:
  - Reads /var/log/containers/*.log
  - Adds K8s metadata (pod name, namespace, labels)
  - Ships to destination

Fluent Bit vs Fluentd:
  Fluent Bit: Lighter, lower resource usage, C-based
  Fluentd: More plugins, Ruby-based, heavier
  
  Common pattern: Fluent Bit (edge) → Fluentd (aggregator) → Storage
```

### Fluentd

```
Fluentd: Full-featured log collector/aggregator

Features:
  - 1000+ plugins
  - Rich routing and filtering
  - Buffering and retry
  - Heavy (compared to Fluent Bit)

Use as aggregator when Fluent Bit is the edge collector.
```

### Elasticsearch

```
Elasticsearch: Search and analytics engine for logs

EFK Stack: Elasticsearch + Fluent Bit + Kibana

Features:
  - Full-text search across logs
  - Real-time indexing
  - Scalable (sharding/replication)
  - Structured and unstructured queries

Alternatives:
  - OpenSearch (AWS-managed Elasticsearch fork)
  - Loki (Grafana, simpler, label-based)
```

### Logstash

```
ELK Stack: Elasticsearch + Logstash + Kibana

Logstash: Data processing pipeline
  Input → Filter (parse, transform) → Output

In K8s, often replaced by Fluent Bit/Fluentd
(Logstash is heavier and JVM-based)
```

### Kibana

```
Kibana: Visualization for Elasticsearch

Features:
  - Log search and filtering
  - Dashboards
  - Saved queries
  - Alerting

Alternative: Grafana (with Loki as log backend)
```

### CloudWatch (AWS)

```
AWS CloudWatch Logs for EKS:

Options:
  1. Fluent Bit → CloudWatch Logs (recommended)
  2. CloudWatch Container Insights (built-in)
  3. AWS for Fluent Bit image (AWS-maintained)

Benefits:
  - Managed service (no Elasticsearch to maintain)
  - Integration with AWS alerts, Lambda
  - Log Insights query language
  - Retention policies
```

---

## Interview Questions

### Q1: How do you implement centralized logging in Kubernetes?

**A:** Deploy Fluent Bit as a DaemonSet on every node. It reads container logs from `/var/log/containers/`, enriches with K8s metadata (pod name, namespace, labels), and forwards to a central store (Elasticsearch, CloudWatch, Loki). This is the node-agent pattern — no application changes needed, captures all stdout/stderr.

### Q2: What happens to logs when a pod is deleted?

**A:** Without centralized logging, logs are lost when the pod is deleted (kubelet cleans up log files). With centralized logging (Fluent Bit DaemonSet), logs are already forwarded to persistent storage before pod deletion. For crashed containers, `kubectl logs --previous` shows the last crash (only while pod exists).

### Q3: When would you use a sidecar logging pattern vs DaemonSet?

**A:**
- **DaemonSet (default):** Collects stdout/stderr from all pods. Simple, efficient, no per-pod overhead.
- **Sidecar:** When app writes to log files (not stdout), needs custom parsing, or requires per-pod routing. More resource overhead but more flexible.

---

## Best Practices

1. **Log to stdout/stderr** — let K8s handle collection
2. **Use structured logging** (JSON) — easier to parse and query
3. **Deploy Fluent Bit DaemonSet** — lightweight, efficient
4. **Add K8s metadata** — pod name, namespace, labels, node
5. **Set retention policies** — 30-90 days based on compliance
6. **Monitor log pipeline** — alert if logs stop flowing
7. **Include request/trace IDs** — correlate across services
8. **Don't log sensitive data** — mask PII, secrets

---

## Related Topics

- [29. Monitoring & Observability](./29-monitoring-and-observability.md)
- [12. DaemonSet](./12-daemonset.md)
- [28. Troubleshooting](./28-troubleshooting.md)
- [33. Kubernetes + AWS/EKS](./33-kubernetes-aws-eks.md)
