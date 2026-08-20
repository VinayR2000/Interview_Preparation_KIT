# 28. Troubleshooting ⭐⭐⭐

---

## Theory

Troubleshooting Kubernetes issues requires understanding pod lifecycle, common error states, and diagnostic tools.

### Pod Pending

```
Cause: Pod can't be scheduled to any node

Common reasons:
  1. Insufficient resources (CPU/memory) on all nodes
  2. No nodes match nodeSelector/affinity
  3. All nodes have taints without matching tolerations
  4. PVC can't be bound (no PV available, wrong StorageClass)
  5. Scheduler not running

Diagnosis:
  kubectl describe pod <name>  → Events section
  kubectl get events --field-selector involvedObject.name=<pod>
  
Fix:
  - Add nodes or reduce resource requests
  - Fix nodeSelector labels
  - Add tolerations
  - Fix PVC/StorageClass
```

### CrashLoopBackOff

```
Cause: Container starts, crashes, K8s restarts with exponential backoff

Common reasons:
  1. Application error (null pointer, missing dependency)
  2. Missing environment variable
  3. Wrong command/args
  4. Missing config file
  5. Port already in use
  6. Insufficient memory (OOMKilled)

Diagnosis:
  kubectl logs <pod> --previous     # Logs from crashed container
  kubectl describe pod <pod>        # Exit code, restart count
  kubectl get pod <pod> -o yaml     # Check state.terminated.exitCode

Fix:
  - Check logs for application error
  - Verify env vars and configmaps
  - Check if image has correct entrypoint
  - Increase memory limits if OOMKilled
```

### ImagePullBackOff / ErrImagePull

```
Cause: K8s can't pull the container image

Common reasons:
  1. Image doesn't exist (typo in name/tag)
  2. Private registry without imagePullSecrets
  3. Invalid credentials
  4. Registry rate limiting (Docker Hub)
  5. Network connectivity to registry

Diagnosis:
  kubectl describe pod <pod>  → Events: "Failed to pull image"
  kubectl get events

Fix:
  - Verify image name and tag exist
  - Create/fix imagePullSecrets
  - Check registry connectivity from node
  - Use authenticated pull for Docker Hub
```

### OOMKilled

```
Cause: Container exceeded memory limit → killed by kernel OOM killer

Exit code: 137 (128 + 9 = SIGKILL)

Diagnosis:
  kubectl describe pod <pod>
    State: Terminated
    Reason: OOMKilled
    Exit Code: 137

  kubectl top pod <pod>  # Current memory usage

Fix:
  - Increase memory limit
  - Fix memory leak in application
  - Tune JVM heap size (-Xmx)
  - Add memory-based HPA
```

### ContainerCreating

```
Cause: Pod scheduled but containers not yet running

Stuck in ContainerCreating:
  1. Image being pulled (large image, slow network)
  2. Volume mount failing (PVC not bound, CSI driver error)
  3. ConfigMap/Secret not found
  4. Network plugin issue (CNI error)

Diagnosis:
  kubectl describe pod <pod>  → Events
  kubectl get events --field-selector involvedObject.name=<pod>
```

### Pod Eviction

```
Cause: Node under resource pressure → kubelet evicts pods

Eviction signals:
  memory.available < 100Mi    → MemoryPressure
  nodefs.available < 10%      → DiskPressure
  imagefs.available < 15%     → DiskPressure
  pid.available < 100         → PIDPressure

Eviction order:
  1. BestEffort pods (no resource requests/limits)
  2. Burstable pods exceeding requests
  3. Guaranteed pods (last resort)

Fix:
  - Add more nodes
  - Set resource requests (avoid BestEffort)
  - Clean up disk space (prune images, logs)
  - Reduce replica count
```

### Readiness Failure

```
Cause: Readiness probe failing → pod removed from Service endpoints

Symptoms:
  - Pod running but not receiving traffic
  - kubectl get pod shows READY: 0/1
  - Service has fewer endpoints than expected

Diagnosis:
  kubectl describe pod <pod>  → Readiness probe failed
  kubectl exec <pod> -- curl localhost:8080/ready  # Test probe manually

Fix:
  - Fix application health endpoint
  - Increase timeout/period
  - Check if dependency is down (DB, cache)
  - Verify port numbers match
```

### Liveness Failure

```
Cause: Liveness probe failing → container restarted

Symptoms:
  - High restart count
  - Application seems to work then dies
  - CrashLoopBackOff after multiple restarts

Diagnosis:
  kubectl describe pod <pod>  → Liveness probe failed: HTTP 503
  kubectl logs <pod> --previous

Fix:
  - Don't check dependencies in liveness (only own health)
  - Increase initialDelaySeconds (if probe fires during startup)
  - Add startup probe (for slow-starting apps)
  - Fix application deadlock/hang
```

### Service Not Reachable

```
Diagnosis checklist:
  1. kubectl get endpoints <service>     # Has endpoints?
  2. kubectl get pods -l <selector>      # Pods running and ready?
  3. kubectl describe service <service>  # Selector correct?
  4. Labels match?                        # Pod labels == Service selector
  5. Port correct?                        # targetPort matches container port

Common issues:
  - Selector mismatch (labels don't match)
  - No ready pods (readiness probe failing)
  - Wrong targetPort (container listens on different port)
  - Pod in wrong namespace
```

### DNS Problems

```
Test DNS from a pod:
  kubectl exec -it <pod> -- nslookup my-service
  kubectl exec -it <pod> -- nslookup my-service.my-namespace.svc.cluster.local

DNS not working:
  1. Check CoreDNS pods: kubectl get pods -n kube-system -l k8s-app=kube-dns
  2. Check CoreDNS logs: kubectl logs -n kube-system -l k8s-app=kube-dns
  3. Check resolv.conf: kubectl exec <pod> -- cat /etc/resolv.conf
  4. Check CoreDNS ConfigMap: kubectl get cm coredns -n kube-system -o yaml
```

### Networking Problems

```
Pod-to-Pod connectivity:
  kubectl exec pod-a -- curl pod-b-ip:port
  kubectl exec pod-a -- ping pod-b-ip

Service connectivity:
  kubectl exec pod-a -- curl service-name:port
  kubectl exec pod-a -- nslookup service-name

Debug pod:
  kubectl run debug --image=nicolaka/netshoot --rm -it -- bash
  # tcpdump, dig, curl, netstat, ss, traceroute all available
```

### Scheduling Problems

```
kubectl describe pod <pod> → Events

Common scheduling failures:
  "0/3 nodes are available: 3 Insufficient cpu"
  "0/3 nodes are available: 3 node(s) didn't match node selector"
  "0/3 nodes are available: 3 node(s) had taints that pod didn't tolerate"

Fix:
  - kubectl describe node <node>  # Check allocatable vs allocated
  - kubectl top nodes              # Actual usage
  - Reduce resource requests or add nodes
```

### Resource Problems

```
kubectl top pods                          # Current usage
kubectl describe node <node>             # Allocated resources
kubectl get pods -o wide                 # Pod placement

Resource troubleshooting:
  Pod stuck pending → insufficient resources
  Pod OOMKilled → memory limit too low
  Pod throttled (slow) → CPU limit too low
  Node NotReady → node resource exhaustion
```

### kubectl logs

```bash
kubectl logs <pod>                        # Current container logs
kubectl logs <pod> --previous            # Previous (crashed) container
kubectl logs <pod> -c <container>        # Specific container
kubectl logs <pod> -f                    # Follow (stream)
kubectl logs <pod> --since=30m           # Last 30 minutes
kubectl logs -l app=my-app --all-containers  # All pods with label
```

### kubectl describe

```bash
kubectl describe pod <pod>               # Pod details + events
kubectl describe node <node>             # Node capacity + pods
kubectl describe service <svc>           # Endpoints, selector
kubectl describe deployment <deploy>     # Rollout status, conditions
kubectl describe ingress <ing>           # Rules, backend status
```

### kubectl events

```bash
kubectl get events --sort-by=.lastTimestamp
kubectl get events --field-selector type=Warning
kubectl get events --field-selector involvedObject.name=my-pod
kubectl events --for pod/my-pod          # K8s 1.26+
```

---

## Diagram

```
┌─────────────── TROUBLESHOOTING DECISION TREE ────────────────┐
│                                                                │
│  Pod Issue?                                                   │
│  │                                                            │
│  ├── Pending                                                  │
│  │   ├── Resources? → kubectl describe node                  │
│  │   ├── Scheduling? → Check nodeSelector, taints            │
│  │   └── PVC? → kubectl get pvc                              │
│  │                                                            │
│  ├── CrashLoopBackOff                                        │
│  │   ├── kubectl logs --previous                             │
│  │   ├── Exit code 137? → OOMKilled (increase memory)       │
│  │   ├── Exit code 1? → Application error (check logs)      │
│  │   └── Missing env/config? → Check ConfigMap/Secret       │
│  │                                                            │
│  ├── ImagePullBackOff                                        │
│  │   ├── Image exists? → Check registry                      │
│  │   ├── Private? → Check imagePullSecrets                   │
│  │   └── Network? → Check node connectivity                  │
│  │                                                            │
│  ├── Running but not working                                 │
│  │   ├── Ready=0/1? → Readiness probe failing               │
│  │   ├── Service no endpoints? → Label mismatch             │
│  │   └── DNS not resolving? → Check CoreDNS                 │
│  │                                                            │
│  └── Evicted                                                  │
│      ├── Node memory pressure → Add nodes, set requests      │
│      └── Node disk pressure → Clean images, logs             │
└────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions

### Q1: A pod is in CrashLoopBackOff. How do you troubleshoot?

**A:**
1. `kubectl describe pod <pod>` — check Events, exit code, state reason
2. `kubectl logs <pod> --previous` — see crash logs
3. Exit code 137 = OOMKilled → increase memory limit
4. Exit code 1 = app error → fix application code/config
5. Check env vars, ConfigMap/Secret mounts
6. Check if container command is correct
7. Try running the image locally to reproduce

### Q2: A service is not reachable. How do you diagnose?

**A:**
1. `kubectl get endpoints <svc>` — any endpoints? If empty:
2. `kubectl get pods -l <selector>` — pods running and Ready?
3. `kubectl describe svc` — verify selector matches pod labels
4. Verify targetPort matches container port
5. Test from within cluster: `kubectl exec debug -- curl svc:port`
6. Check NetworkPolicy blocking traffic
7. Check kube-proxy: iptables rules present?

### Q3: A pod is stuck in Pending state. What do you check?

**A:**
1. `kubectl describe pod` → Events section shows the reason
2. "Insufficient cpu/memory" → nodes full → scale cluster or reduce requests
3. "didn't match node selector" → fix labels or nodeSelector
4. "had taints" → add tolerations
5. "unbound PVC" → check PV availability, StorageClass
6. "FailedScheduling" → check scheduler is running

### Q4: How do you debug networking issues between pods?

**A:**
1. Deploy debug pod: `kubectl run debug --image=netshoot --rm -it -- bash`
2. Test DNS: `nslookup service-name`
3. Test connectivity: `curl pod-ip:port`, `ping pod-ip`
4. Check NetworkPolicy: `kubectl get networkpolicy -n <ns>`
5. Check kube-proxy: `iptables-save | grep <service-name>`
6. Check CNI pod logs: `kubectl logs -n kube-system -l app=calico-node`

### Q5: A pod was OOMKilled. How do you prevent it?

**A:**
1. Identify actual memory usage: `kubectl top pod`, Prometheus metrics
2. Set memory limit to 1.5-2× normal usage
3. Fix memory leaks in application (heap dumps, profiling)
4. For JVM apps: set `-Xmx` to 75% of container memory limit
5. Consider VPA for auto-right-sizing
6. Set memory-based HPA if load causes memory growth

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Not checking events | Miss scheduling/pull errors | Always kubectl describe first |
| Not using --previous for logs | Can't see crash reason | Use --previous for crashed containers |
| Force-deleting stuck pods | Can cause split-brain | Investigate root cause first |
| Ignoring exit codes | Miss OOM vs app error | 137=OOM, 1=app error, 143=SIGTERM |
| Not testing from inside cluster | External network different | kubectl exec debug pod |

---

## Best Practices

1. **kubectl describe first** — events tell the story
2. **Check logs (current + previous)** — `--previous` for crashes
3. **Use debug pods** (netshoot) for network troubleshooting
4. **Monitor proactively** — don't wait for issues
5. **Set up alerts** — OOMKills, CrashLoops, Pending pods, Evictions
6. **Know exit codes** — 137=OOM, 1=error, 143=graceful termination
7. **Document runbooks** — common issues and their fixes

---

## Related Topics

- [16. Health Checks](./16-health-checks.md)
- [15. Resources](./15-resources.md)
- [07. Networking](./07-networking.md)
- [14. Scheduling](./14-scheduling.md)
