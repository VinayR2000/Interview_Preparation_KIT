# 16. Health Checks ⭐⭐⭐

---

## Theory

Health probes allow Kubernetes to detect and respond to application failures, ensuring only healthy pods receive traffic and unhealthy pods are restarted.

### Liveness Probe

```
"Is the application alive (not stuck/deadlocked)?"

Failure → kubelet RESTARTS the container

Use case:
  - Detect deadlocks
  - Detect infinite loops
  - Detect hung processes
  - Application is running but not functional
```

### Readiness Probe

```
"Can the application serve traffic?"

Failure → Pod removed from Service endpoints (no traffic)
Recovery → Pod added back to Service endpoints

Use case:
  - Application loading cache
  - Waiting for dependencies
  - Temporarily overloaded
  - Warming up
  
Key difference from liveness:
  - Readiness failure does NOT restart container
  - Just removes from traffic rotation
```

### Startup Probe

```
"Has the application finished starting?"

During startup probe period:
  - Liveness and readiness probes are DISABLED
  - Gives slow-starting apps time to initialize

Failure → Container restarted (like liveness)
Success → Liveness and readiness probes start

Use case:
  - Java/Spring Boot apps (30-60s startup)
  - Applications loading large datasets
  - Legacy apps with slow initialization
```

### HTTP Probe

```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
    httpHeaders:
    - name: Accept
      value: application/json
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
  successThreshold: 1
```

```
HTTP Probe:
  - Sends HTTP GET request to specified path/port
  - Success: Response code 200-399
  - Failure: Response code >= 400 or timeout/connection error
  
Best for: Web applications, REST APIs
```

### TCP Probe

```yaml
livenessProbe:
  tcpSocket:
    port: 5432
  initialDelaySeconds: 15
  periodSeconds: 10
```

```
TCP Probe:
  - Attempts TCP connection to specified port
  - Success: Connection established
  - Failure: Connection refused or timeout

Best for: Databases, message brokers, non-HTTP services
```

### Exec Probe

```yaml
livenessProbe:
  exec:
    command:
    - /bin/sh
    - -c
    - pg_isready -U postgres
  initialDelaySeconds: 30
  periodSeconds: 10
```

```
Exec Probe:
  - Executes command inside container
  - Success: Exit code 0
  - Failure: Non-zero exit code

Best for: Custom health checks, database connectivity, file existence
```

### Initial Delay

```yaml
initialDelaySeconds: 30   # Wait 30s after container starts before first probe
```

```
initialDelaySeconds:
  - Delay before first probe execution
  - Gives application time to start
  - Without startup probe, this is your only startup protection
  - If too short: pod gets killed before it's ready
  - If too long: slow failure detection

With startup probe: Set initialDelaySeconds=0 on liveness/readiness
  (startup probe handles the startup period)
```

### Timeout

```yaml
timeoutSeconds: 5   # Probe must respond within 5 seconds
```

```
timeoutSeconds (default: 1):
  - How long to wait for probe response
  - If probe doesn't respond in time → counts as failure
  - Set higher for slow endpoints (database checks)
  - Set lower for fast endpoints (simple /health)
```

### Period

```yaml
periodSeconds: 10   # Run probe every 10 seconds
```

```
periodSeconds (default: 10):
  - Interval between probe executions
  - Lower = faster detection but more load
  - Higher = less load but slower detection

Recommendations:
  Liveness: 10-30s (not too aggressive)
  Readiness: 5-10s (faster traffic routing)
  Startup: 5-10s (quick startup detection)
```

### Failure Threshold

```yaml
failureThreshold: 3   # 3 consecutive failures = probe failed
```

```
failureThreshold (default: 3):
  - Number of consecutive failures before action taken
  - Prevents false positives from transient issues
  - failureThreshold × periodSeconds = detection time
  - Example: 3 × 10s = 30s to detect failure
```

### Success Threshold

```yaml
successThreshold: 1   # 1 success = probe passes (default: 1)
```

```
successThreshold (default: 1, must be 1 for liveness/startup):
  - Number of consecutive successes before probe is considered passing
  - For readiness: Can require multiple successes before routing traffic
  - Prevents flapping (pod added/removed repeatedly)
```

---

## Internal Working

```
Probe Execution Flow:

kubelet (on the node):
  1. Starts probe timer after container starts + initialDelaySeconds
  2. Every periodSeconds:
     a. Execute probe (HTTP/TCP/Exec)
     b. Wait up to timeoutSeconds for response
     c. If success: reset failure counter
     d. If failure: increment failure counter
  3. If failure counter >= failureThreshold:
     - Liveness: restart container
     - Readiness: remove from endpoints
     - Startup: restart container
  4. If success counter >= successThreshold:
     - Readiness: add to endpoints
     - Startup: mark as started, enable liveness/readiness

Startup → Liveness → Readiness flow:
  Container starts
  → Startup probe begins (liveness/readiness disabled)
  → Startup succeeds → liveness + readiness probes start
  → Readiness passes → Pod added to Service
  → Liveness monitors ongoing health
```

---

## Diagram

```
┌──────────────────── PROBE LIFECYCLE ─────────────────────────┐
│                                                                │
│  Container Start                                              │
│       │                                                       │
│       ▼                                                       │
│  ┌─────────────────────────────────────────────┐             │
│  │         STARTUP PROBE                        │             │
│  │  (liveness & readiness DISABLED)             │             │
│  │                                              │             │
│  │  Checking every 5s... up to 30 attempts     │             │
│  │  Total: 150s max startup time               │             │
│  │                                              │             │
│  │  ✓ Success → proceed                        │             │
│  │  ✗ Failure (after threshold) → RESTART      │             │
│  └──────────────────────┬──────────────────────┘             │
│                          │                                     │
│                    ┌─────┴─────┐                              │
│                    ▼           ▼                              │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │  LIVENESS PROBE  │  │ READINESS PROBE  │                 │
│  │                   │  │                   │                 │
│  │  ✗ Fail (3x)     │  │  ✗ Fail (3x)     │                 │
│  │  → RESTART pod   │  │  → Remove from    │                 │
│  │                   │  │    Service        │                 │
│  │  ✓ Pass           │  │                   │                 │
│  │  → Continue       │  │  ✓ Pass           │                 │
│  │                   │  │  → Add to Service │                 │
│  └──────────────────┘  └──────────────────┘                 │
└────────────────────────────────────────────────────────────────┘
```

---

## Code

### Production Spring Boot Health Probes:

```yaml
spec:
  containers:
  - name: order-service
    image: order-service:2.0
    ports:
    - containerPort: 8080
    
    # Startup: Allow up to 150s for Spring Boot to start
    startupProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 10
      periodSeconds: 5
      failureThreshold: 30     # 30 × 5s = 150s max startup
      timeoutSeconds: 3
    
    # Liveness: Detect deadlocks/hangs
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 0   # Startup probe handles delay
      periodSeconds: 10
      failureThreshold: 3      # 3 × 10s = 30s to detect
      timeoutSeconds: 5
    
    # Readiness: Can serve traffic?
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 0
      periodSeconds: 5
      failureThreshold: 3      # 15s to remove from traffic
      successThreshold: 1
      timeoutSeconds: 3
```

---

## Interview Questions

### Q1: What is the difference between liveness, readiness, and startup probes?

**A:**
- **Startup:** Checks if app has finished starting. Disables liveness/readiness during startup. Failure = restart. For slow-starting apps.
- **Liveness:** Checks if app is alive (not deadlocked). Failure = restart container. Runs continuously after startup.
- **Readiness:** Checks if app can serve traffic. Failure = remove from Service endpoints (no traffic). Does NOT restart. For temporary unavailability.

### Q2: Why shouldn't the liveness probe check dependencies?

**A:** If liveness checks a dependency (database, external API) and it goes down, ALL pods restart simultaneously. This causes a cascading failure — pods restart, overload the dependency, fail again (crash loop). Liveness should only check if the process itself is healthy (not deadlocked). Use readiness to check dependencies — it removes traffic but doesn't restart.

### Q3: How do you configure probes for a Spring Boot app that takes 60s to start?

**A:** Use startup probe:
```yaml
startupProbe:
  httpGet: {path: /actuator/health/liveness, port: 8080}
  periodSeconds: 5
  failureThreshold: 24  # 24 × 5s = 120s max (2× startup time)
```
Set liveness/readiness with `initialDelaySeconds: 0` since startup probe handles the delay. This prevents liveness from killing the pod during startup.

### Q4: What happens during a rolling update with readiness probes?

**A:**
1. New pod created → starts running
2. Readiness probe checks → fails (app starting)
3. Pod NOT added to Service (no traffic)
4. Readiness probe passes → pod added to Service
5. Traffic now reaches new pod
6. Old pod receives termination signal
7. Old pod removed from Service endpoints

Without readiness probes, traffic would hit unready pods during rolling updates, causing errors.

### Q5: What is the "detection time" for a failed liveness probe?

**A:** Detection time = failureThreshold × periodSeconds. With failureThreshold=3 and periodSeconds=10, it takes 30 seconds to detect failure and trigger restart. Plus timeoutSeconds for each check. Total worst case: initialDelay + (failureThreshold × (periodSeconds + timeoutSeconds)).

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Liveness checks dependencies | Cascade restarts if DB goes down | Only check own health |
| No startup probe for slow apps | Liveness kills during startup | Add startup probe |
| Readiness same as liveness | Missing traffic control | Separate endpoints |
| Period too aggressive (1s) | Excessive load | 5-10s is reasonable |
| Timeout too short (1s) for complex checks | False positives | Increase to 3-5s |
| No probes at all | K8s can't detect failures | Always configure probes |

---

## Best Practices

1. **Always configure all three probes** in production
2. **Liveness: check only your process** — not dependencies
3. **Readiness: check dependencies** — DB connection, cache, external services
4. **Use startup probe** for apps > 10s startup time
5. **startupProbe.failureThreshold × periodSeconds > 2× normal startup time**
6. **Don't make probes too expensive** — lightweight health endpoints
7. **Separate liveness and readiness endpoints** — different logic
8. **Monitor probe failures** — track in metrics (readiness flapping = issue)

---

## Related Topics

- [04. Pods](./04-pods.md)
- [05. Deployments](./05-deployments.md)
- [28. Troubleshooting](./28-troubleshooting.md)
- [35. Kubernetes + Spring Boot](./35-kubernetes-spring-boot.md)
