# 22. Helm ⭐⭐⭐

---

## Theory

**Helm** is the package manager for Kubernetes — it packages multiple K8s manifests into a reusable, versioned, configurable unit called a Chart.

### What is Helm?

```
Problem without Helm:
  - 10+ YAML files per application
  - Copy-paste for different environments
  - No versioning of deployments
  - Manual value substitution

Helm provides:
  - Package manager (install/upgrade/rollback applications)
  - Templating engine (dynamic YAML generation)
  - Release management (track deployments)
  - Dependency management (chart dependencies)
  - Repository system (share charts)
```

### Helm Chart

```
Chart structure:
  my-chart/
  ├── Chart.yaml          # Chart metadata (name, version, description)
  ├── values.yaml         # Default configuration values
  ├── charts/             # Dependency charts
  ├── templates/          # Template files (K8s manifests with Go templating)
  │   ├── deployment.yaml
  │   ├── service.yaml
  │   ├── ingress.yaml
  │   ├── configmap.yaml
  │   ├── _helpers.tpl    # Template helpers/partials
  │   ├── NOTES.txt       # Post-install instructions
  │   └── tests/
  └── .helmignore         # Files to ignore
```

### Chart.yaml

```yaml
apiVersion: v2
name: order-service
description: Order processing microservice
type: application          # application or library
version: 1.2.0            # Chart version (changes with chart updates)
appVersion: "2.1.0"       # Application version (your app)
dependencies:
- name: postgresql
  version: "12.x.x"
  repository: https://charts.bitnami.com/bitnami
  condition: postgresql.enabled
```

### values.yaml

```yaml
# Default values (overridden at install/upgrade time)
replicaCount: 3
image:
  repository: registry.example.com/order-service
  tag: "2.1.0"
  pullPolicy: IfNotPresent

service:
  type: ClusterIP
  port: 80

ingress:
  enabled: true
  hostname: api.example.com
  tls: true

resources:
  requests:
    cpu: "250m"
    memory: "256Mi"
  limits:
    cpu: "500m"
    memory: "512Mi"

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPU: 70

postgresql:
  enabled: true
  auth:
    database: orders
```

### templates

```yaml
# templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "mychart.fullname" . }}
  labels:
    {{- include "mychart.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "mychart.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "mychart.selectorLabels" . | nindent 8 }}
    spec:
      containers:
      - name: {{ .Chart.Name }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        ports:
        - containerPort: 8080
        resources:
          {{- toYaml .Values.resources | nindent 12 }}
        {{- if .Values.autoscaling.enabled }}
        # HPA handles scaling
        {{- end }}
```

### Helm Install

```bash
# Install a chart
helm install my-release ./my-chart
helm install my-release ./my-chart -f custom-values.yaml
helm install my-release ./my-chart --set replicaCount=5
helm install my-release ./my-chart -n production --create-namespace

# Install from repository
helm install my-release bitnami/postgresql --version 12.5.0
```

### Helm Upgrade

```bash
# Upgrade an existing release
helm upgrade my-release ./my-chart
helm upgrade my-release ./my-chart -f production-values.yaml
helm upgrade my-release ./my-chart --set image.tag=2.2.0
helm upgrade --install my-release ./my-chart  # Install if not exists, upgrade if exists
```

### Helm Rollback

```bash
helm rollback my-release 1              # Rollback to revision 1
helm rollback my-release                # Rollback to previous revision
helm history my-release                 # Show revision history
```

### Helm Uninstall

```bash
helm uninstall my-release
helm uninstall my-release -n production
helm uninstall my-release --keep-history  # Keep history for audit
```

### Helm Repository

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus https://prometheus-community.github.io/helm-charts
helm repo update
helm search repo postgresql
helm search hub kafka                   # Search Artifact Hub
```

### Helm Variables

```yaml
# Built-in objects:
{{ .Release.Name }}        # Release name
{{ .Release.Namespace }}   # Release namespace
{{ .Chart.Name }}          # Chart name
{{ .Chart.Version }}       # Chart version
{{ .Values.key }}          # Values from values.yaml

# Conditional:
{{- if .Values.ingress.enabled }}
# Ingress resource here
{{- end }}

# Loop:
{{- range .Values.env }}
- name: {{ .name }}
  value: {{ .value | quote }}
{{- end }}
```

### Helm Functions

```yaml
# Common functions:
{{ .Values.name | quote }}              # Add quotes
{{ .Values.name | upper }}              # Uppercase
{{ .Values.name | default "app" }}      # Default value
{{ .Values.resources | toYaml }}        # Convert to YAML
{{ include "mychart.labels" . }}        # Include template
{{ required "image.tag is required" .Values.image.tag }}  # Fail if missing
{{ .Values.name | b64enc }}             # Base64 encode
{{ printf "%s-%s" .Release.Name .Chart.Name }}  # String formatting
```

### Helm Hooks

```yaml
# Pre/post install, upgrade, delete, rollback hooks
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
  annotations:
    "helm.sh/hook": pre-upgrade
    "helm.sh/hook-weight": "1"
    "helm.sh/hook-delete-policy": hook-succeeded
spec:
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: migrate
        image: my-app:2.0
        command: ["./migrate", "--up"]
```

### Helm Dependency Management

```bash
# In Chart.yaml: define dependencies
# Then:
helm dependency update ./my-chart    # Download dependencies to charts/
helm dependency build ./my-chart     # Build from lock file
helm dependency list ./my-chart      # List dependencies
```

---

## Interview Questions

### Q1: What is Helm and why use it over raw YAML?

**A:** Helm is K8s package manager providing: templating (dynamic values per environment), versioning (track what's deployed), rollback (revert to previous), dependencies (bundle related charts), and sharing (chart repositories). Raw YAML becomes unmanageable at scale — Helm adds parameterization, reusability, and lifecycle management.

### Q2: What is the difference between `helm install` and `helm upgrade --install`?

**A:** `helm install` creates a new release and fails if it already exists. `helm upgrade --install` creates if not exists, upgrades if it does — idempotent, safe for CI/CD pipelines. Always use `upgrade --install` in automation.

### Q3: How do you manage different environments with Helm?

**A:** Use separate values files:
```bash
helm upgrade --install my-app ./chart -f values-dev.yaml
helm upgrade --install my-app ./chart -f values-prod.yaml
```
Base `values.yaml` has defaults, environment-specific files override. Can also use `--set` for individual overrides in CI/CD.

### Q4: How does Helm rollback work?

**A:** Helm stores release history (revision 1, 2, 3...). `helm rollback my-release 2` re-applies the manifests from revision 2. It creates a NEW revision (4) with the old config. K8s then performs standard rolling update to the old state. Works like Deployment rollback but at the Helm chart level.

---

## Best Practices

1. **Use `upgrade --install`** in CI/CD (idempotent)
2. **Pin chart versions** — don't use `latest`
3. **Use values files per environment** — not inline `--set`
4. **Keep secrets out of values** — use external-secrets or sealed-secrets
5. **Validate templates** — `helm template ./chart | kubectl apply --dry-run=server -f -`
6. **Use `required` function** — fail fast on missing values
7. **Document values.yaml** — comments explain each parameter
8. **Use library charts** — share common templates across charts

---

## Related Topics

- [20. YAML](./20-yaml.md)
- [21. kubectl](./21-kubectl.md)
- [34. Kubernetes + CI/CD](./34-kubernetes-cicd.md)
- [23. Deployment Strategies](./23-deployment-strategies.md)
