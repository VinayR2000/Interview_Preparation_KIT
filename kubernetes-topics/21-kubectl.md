# 21. kubectl ⭐⭐⭐

---

## Theory

**kubectl** is the command-line tool for interacting with the Kubernetes API Server. It's the primary interface for managing clusters.

### kubectl get

```bash
# List resources
kubectl get pods                          # Pods in current namespace
kubectl get pods -n production            # Pods in specific namespace
kubectl get pods --all-namespaces         # All namespaces
kubectl get pods -o wide                  # Extra info (node, IP)
kubectl get pods -o yaml                  # Full YAML output
kubectl get pods -o json                  # JSON output
kubectl get pods -l app=my-app            # Filter by label
kubectl get pods --field-selector status.phase=Running
kubectl get all                           # Pods, Services, Deployments, etc.

# Custom columns
kubectl get pods -o custom-columns=NAME:.metadata.name,STATUS:.status.phase,NODE:.spec.nodeName

# Watch for changes
kubectl get pods -w
```

### kubectl describe

```bash
# Detailed info about a resource (human-readable)
kubectl describe pod my-pod
kubectl describe deployment my-app
kubectl describe node node-1
kubectl describe service my-service

# Shows:
# - Resource spec
# - Current status
# - Events (scheduling, pulling, errors)
# - Conditions
```

### kubectl create

```bash
# Imperative creation (one-time)
kubectl create namespace production
kubectl create configmap my-config --from-literal=key=value
kubectl create secret generic db-secret --from-literal=password=mysecret
kubectl create deployment my-app --image=nginx:1.25
kubectl create service clusterip my-svc --tcp=80:8080

# Generate YAML without creating (useful for templates)
kubectl create deployment my-app --image=nginx --dry-run=client -o yaml > deployment.yaml
```

### kubectl apply

```bash
# Declarative management (preferred for production)
kubectl apply -f deployment.yaml         # Apply single file
kubectl apply -f ./k8s/                  # Apply all files in directory
kubectl apply -f https://url/manifest.yaml  # Apply from URL
kubectl apply -k ./kustomize/            # Apply Kustomize

# Dry run (validate without applying)
kubectl apply -f deployment.yaml --dry-run=client
kubectl apply -f deployment.yaml --dry-run=server  # Server-side validation
```

### kubectl delete

```bash
kubectl delete pod my-pod
kubectl delete deployment my-app
kubectl delete -f deployment.yaml        # Delete resources in file
kubectl delete pods --all -n development # Delete all pods in namespace
kubectl delete pod my-pod --force --grace-period=0  # Force delete (stuck pods)

# WARNING: Force delete can cause issues (split-brain in StatefulSets)
```

### kubectl edit

```bash
# Opens resource in editor (vim/nano)
kubectl edit deployment my-app
kubectl edit configmap my-config -n production

# Changes applied immediately after save
# Not recommended for production (use apply with version-controlled YAML)
```

### kubectl logs

```bash
kubectl logs my-pod                      # Current logs
kubectl logs my-pod --previous           # Logs from previous container (crashed)
kubectl logs my-pod -c sidecar           # Specific container in multi-container pod
kubectl logs my-pod -f                   # Follow (stream) logs
kubectl logs my-pod --tail=100           # Last 100 lines
kubectl logs my-pod --since=1h           # Logs from last hour
kubectl logs -l app=my-app               # Logs from all pods with label
kubectl logs deployment/my-app           # Logs from one pod of deployment
```

### kubectl exec

```bash
# Execute command in container
kubectl exec my-pod -- ls /app
kubectl exec my-pod -- cat /etc/config
kubectl exec -it my-pod -- /bin/sh       # Interactive shell
kubectl exec -it my-pod -c sidecar -- bash  # Specific container

# Common debugging:
kubectl exec -it my-pod -- curl localhost:8080/health
kubectl exec -it my-pod -- env | grep DB
kubectl exec -it my-pod -- nslookup order-service
```

### kubectl port-forward

```bash
# Forward local port to pod/service port
kubectl port-forward pod/my-pod 8080:8080         # local:pod
kubectl port-forward service/my-service 9090:80   # local:service
kubectl port-forward deployment/my-app 3000:3000

# Access: localhost:8080 → pod:8080
# Useful for debugging without exposing externally
```

### kubectl scale

```bash
kubectl scale deployment my-app --replicas=5
kubectl scale statefulset postgres --replicas=3
kubectl scale deployment my-app --replicas=0   # Scale to zero
```

### kubectl rollout

```bash
kubectl rollout status deployment/my-app     # Watch rollout progress
kubectl rollout history deployment/my-app    # List revisions
kubectl rollout undo deployment/my-app       # Rollback to previous
kubectl rollout undo deployment/my-app --to-revision=2  # Specific revision
kubectl rollout pause deployment/my-app      # Pause rollout
kubectl rollout resume deployment/my-app     # Resume rollout
kubectl rollout restart deployment/my-app    # Trigger rolling restart
```

### kubectl top

```bash
# Requires metrics-server installed
kubectl top pods                      # CPU/memory per pod
kubectl top pods -n production        # Specific namespace
kubectl top nodes                     # CPU/memory per node
kubectl top pod my-pod --containers   # Per container
```

### kubectl config

```bash
# Manage kubeconfig (contexts, clusters, users)
kubectl config get-contexts              # List all contexts
kubectl config current-context           # Show active context
kubectl config use-context production    # Switch context
kubectl config set-context --current --namespace=production  # Set default namespace
kubectl config view                      # Show kubeconfig
```

### kubectl explain

```bash
# Discover resource fields and their documentation
kubectl explain pod
kubectl explain pod.spec
kubectl explain pod.spec.containers
kubectl explain deployment.spec.strategy
kubectl explain service.spec.type

# Recursive (show all nested fields)
kubectl explain pod.spec --recursive
```

### kubectl events

```bash
kubectl events                           # All events (K8s 1.26+)
kubectl events --for pod/my-pod          # Events for specific resource
kubectl get events --sort-by=.lastTimestamp  # Sorted by time
kubectl get events --field-selector type=Warning  # Only warnings
```

---

## Code

### Useful kubectl shortcuts and patterns:

```bash
# Aliases (add to .bashrc/.zshrc)
alias k='kubectl'
alias kg='kubectl get'
alias kd='kubectl describe'
alias kl='kubectl logs'
alias ka='kubectl apply -f'
alias kx='kubectl exec -it'

# Quick pod debugging
kubectl run debug --image=busybox --rm -it -- sh
kubectl run debug --image=nicolaka/netshoot --rm -it -- bash

# Copy files to/from pod
kubectl cp my-pod:/app/logs/error.log ./error.log
kubectl cp ./config.yaml my-pod:/app/config/

# Get pod IPs
kubectl get pods -o wide

# Watch all events
kubectl get events -w --all-namespaces

# Check which pods use most resources
kubectl top pods --sort-by=memory
kubectl top pods --sort-by=cpu

# Find pods not running
kubectl get pods --field-selector status.phase!=Running

# JSON path queries
kubectl get pods -o jsonpath='{.items[*].metadata.name}'
kubectl get nodes -o jsonpath='{.items[*].status.addresses[?(@.type=="InternalIP")].address}'
```

---

## Interview Questions

### Q1: What is the difference between `kubectl create` and `kubectl apply`?

**A:**
- **create:** Imperative — creates resource, fails if already exists. One-time operations.
- **apply:** Declarative — creates if doesn't exist, updates if it does. Idempotent (safe to run multiple times). Preferred for production and GitOps.

`apply` tracks changes via annotations and merges. `create` is fire-and-forget.

### Q2: How do you debug a pod that's in CrashLoopBackOff?

**A:**
```bash
kubectl describe pod my-pod         # Check events, exit codes
kubectl logs my-pod --previous      # Logs from crashed container
kubectl get pod my-pod -o yaml      # Full status (OOMKilled?)
kubectl exec -it my-pod -- sh       # If container starts briefly
kubectl events --for pod/my-pod     # Related events
```
Common causes: missing env vars, wrong command, OOM, missing dependencies.

### Q3: How do you check resource usage of pods?

**A:**
```bash
kubectl top pods                    # Requires metrics-server
kubectl top pods --sort-by=cpu
kubectl top pods --containers       # Per-container breakdown
```
For historical data: use Prometheus + Grafana. `kubectl top` only shows current usage.

---

## Best Practices

1. **Use `kubectl apply`** over `create/edit` for production
2. **Use `--dry-run=client -o yaml`** to generate templates
3. **Set namespace in context** — avoid mistakes with `-n`
4. **Use labels for filtering** — `kubectl get pods -l app=my-app`
5. **Use `kubectl explain`** — self-documenting API reference
6. **Use aliases** — save time with frequent commands
7. **Use `kubectl diff`** — preview changes before applying
8. **Use JSONPath/custom-columns** — extract specific information

---

## Related Topics

- [20. YAML](./20-yaml.md)
- [22. Helm](./22-helm.md)
- [28. Troubleshooting](./28-troubleshooting.md)
