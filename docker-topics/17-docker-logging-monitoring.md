# 17. Docker Logging and Monitoring ⭐⭐

---

## Theory

**Docker logging and monitoring** covers how to collect, store, and analyze container logs and metrics. In ephemeral containers, proper logging strategy is critical — containers die, logs shouldn't.

### Docker Logging Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  CONTAINER LOGGING FLOW                       │
│                                                              │
│  Container Process (PID 1)                                   │
│       │                                                      │
│       ├── stdout ──┐                                         │
│       │            ├── Docker Logging Driver                 │
│       └── stderr ──┘         │                               │
│                              ├── json-file (default)         │
│                              ├── syslog                      │
│                              ├── journald                    │
│                              ├── fluentd                     │
│                              ├── awslogs                     │
│                              ├── gcplogs                     │
│                              └── splunk                      │
│                                                              │
│  Rule: Applications should log to stdout/stderr             │
│        Docker handles the rest (collection, routing)         │
└─────────────────────────────────────────────────────────────┘
```

### Logging Drivers

```bash
# Check current logging driver
docker info --format '{{.LoggingDriver}}'

# Available drivers:
#   json-file  — default, stores on disk as JSON
#   local      — optimized local storage
#   syslog     — sends to syslog daemon
#   journald   — sends to systemd journal
#   fluentd    — sends to Fluentd
#   awslogs    — sends to AWS CloudWatch
#   gcplogs    — sends to Google Cloud Logging
#   splunk     — sends to Splunk
#   none       — discard logs

# Set driver per container
docker run --log-driver=json-file \
  --log-opt max-size=10m \
  --log-opt max-file=3 \
  myapp

# Set default driver (daemon.json)
# /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5",
    "labels": "service,environment"
  }
}
```

### Log Rotation (Critical for Production!)

```bash
# WITHOUT rotation: logs grow until disk full → host crash!
# Default json-file driver: NO rotation!

# Configure rotation:
docker run --log-opt max-size=10m --log-opt max-file=3 myapp

# max-size: max size per file before rotation
# max-file: number of rotated files to keep
# Total max: max-size × max-file = 30MB per container
```

```yaml
# Docker Compose — log rotation
services:
  api:
    image: myapp
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"
        labels: "service"
        tag: "{{.Name}}"
```

### Structured Logging (JSON)

```
# Unstructured (hard to parse):
2024-01-15 10:30:45 INFO OrderService - Order created: 12345

# Structured JSON (machine-parseable):
{"timestamp":"2024-01-15T10:30:45Z","level":"INFO","service":"order-service","msg":"Order created","orderId":"12345","userId":"user-789"}

Benefits of structured logging:
  - Searchable fields (filter by orderId, userId)
  - Aggregatable (count errors per service)
  - Works with ELK, Datadog, Splunk out of the box
```

```yaml
# Spring Boot — JSON logging (logback-spring.xml)
# Or via application.yml:
logging:
  pattern:
    console: '{"timestamp":"%d","level":"%p","service":"order-service","msg":"%m"}%n'
```

### Centralized Logging Stack (ELK/EFK)

```
┌───────────────────────────────────────────────────────────┐
│              CENTRALIZED LOGGING (EFK Stack)                │
│                                                            │
│  Container 1 ──┐                                          │
│  Container 2 ──┼── Fluentd/Filebeat ── Elasticsearch     │
│  Container 3 ──┘        (collect)         (store/index)   │
│                                               │            │
│                                            Kibana          │
│                                         (visualize/query)  │
│                                                            │
│  Alternative: ELK (Elasticsearch + Logstash + Kibana)     │
│  Cloud: AWS CloudWatch, GCP Cloud Logging, Datadog        │
└───────────────────────────────────────────────────────────┘
```

### Docker Metrics and Monitoring

```bash
# Built-in resource monitoring
docker stats                    # Live CPU, MEM, NET, DISK I/O
docker stats --no-stream        # Single snapshot
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# Output example:
# NAME          CPU %   MEM USAGE / LIMIT
# order-api     2.5%    256MiB / 512MiB
# postgres      1.2%    128MiB / 1GiB
# redis         0.1%    12MiB / 256MiB
```

### Prometheus + Grafana Stack

```yaml
# compose.yaml — Monitoring stack
services:
  api:
    image: myapp:1.0
    ports:
      - "8080:8080"

  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana

  cadvisor:
    image: gcr.io/cadvisor/cadvisor:latest
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
    ports:
      - "8081:8080"

volumes:
  grafana-data:
```

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot'
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['api:8080']

  - job_name: 'cadvisor'
    static_configs:
      - targets: ['cadvisor:8080']
```

### Container Health Monitoring

```yaml
services:
  api:
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s

# Health states:
#   starting  — within start_period
#   healthy   — healthcheck passing
#   unhealthy — retries exceeded

# Check health:
# docker inspect --format='{{.State.Health.Status}}' <container>
```

---

## Code

### Complete Observability Stack:

```yaml
# compose-monitoring.yaml
services:
  # Application
  api:
    image: order-service:1.0
    environment:
      - MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus
    ports:
      - "8080:8080"
    logging:
      driver: fluentd
      options:
        fluentd-address: localhost:24224
        tag: "api.{{.Name}}"
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3

  # Log Collection
  fluentd:
    image: fluent/fluentd:v1.16
    volumes:
      - ./fluentd/conf:/fluentd/etc
    ports:
      - "24224:24224"

  # Log Storage + Search
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"

  # Log Visualization
  kibana:
    image: docker.elastic.co/kibana/kibana:8.11.0
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch

  # Metrics Collection
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  # Metrics Visualization
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana

volumes:
  es-data:
  grafana-data:
```

### Docker Compose Logging Configuration:

```yaml
# Production-ready logging for all services
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "5"
    labels: "service,environment"

services:
  api:
    image: myapp:1.0
    logging: *default-logging
    labels:
      service: "order-api"
      environment: "production"

  worker:
    image: worker:1.0
    logging: *default-logging
    labels:
      service: "order-worker"
      environment: "production"

  postgres:
    image: postgres:15
    logging: *default-logging
    labels:
      service: "postgres"
      environment: "production"
```

---

## Interview Questions

### Q1: How does Docker logging work?

**A:** Docker captures stdout/stderr from the container's PID 1 process and routes it through a logging driver. The default `json-file` driver stores logs on disk. Applications should write to stdout/stderr (not files inside the container), allowing Docker and orchestration platforms to handle log collection, rotation, and forwarding.

### Q2: Why is log rotation critical in Docker?

**A:** The default `json-file` driver has NO rotation by default. Logs grow indefinitely until the disk fills, crashing the host and all containers. Fix: always set `max-size` and `max-file` options. In production, use a centralized logging driver (fluentd, awslogs) that doesn't store locally.

### Q3: How do you implement centralized logging for containers?

**A:** Options:
1. **Logging driver** — send directly (fluentd, awslogs) from Docker
2. **Sidecar pattern** — log collector container alongside app
3. **DaemonSet** — one collector per host (Filebeat, Fluentd in K8s)
4. **Cloud-native** — AWS CloudWatch, GCP Cloud Logging

Stack: Fluentd/Filebeat (collect) → Elasticsearch (store) → Kibana (query/visualize)

### Q4: How do you monitor Docker container resource usage?

**A:** 
- **Basic:** `docker stats` — live CPU, memory, network, disk I/O
- **Container metrics:** cAdvisor — detailed per-container metrics
- **Application metrics:** Prometheus scraping /actuator/prometheus
- **Visualization:** Grafana dashboards
- **Alerting:** Prometheus Alertmanager

In Kubernetes: all handled by the monitoring stack (Prometheus Operator + Grafana).

---

## Best Practices

1. **Always configure log rotation** — `max-size` + `max-file`
2. **Log to stdout/stderr** — never write to files inside container
3. **Use structured logging (JSON)** — searchable, aggregatable
4. **Centralized logging in production** — EFK/ELK or cloud service
5. **Health checks on all services** — enable self-healing
6. **Monitor resource usage** — Prometheus + Grafana
7. **Set resource limits** — detect problems before OOM
8. **Use YAML anchors** for consistent logging config across services
9. **Include correlation IDs** — trace requests across services
10. **Alert on unhealthy containers** — don't wait for user reports

---

## Related Topics

- [16. Docker Troubleshooting](./16-docker-troubleshooting.md)
- [12. Docker Compose](./12-docker-compose.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
- [18. Docker in CI/CD](./18-docker-in-ci-cd.md)
