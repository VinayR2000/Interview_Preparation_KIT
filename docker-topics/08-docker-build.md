# 8. Docker Build ⭐⭐⭐

---

## Theory

`docker build` converts a Dockerfile into a Docker image by executing instructions layer by layer, using caching for efficiency.

### docker build

```bash
# Basic build
docker build -t my-app:1.0 .

# Build with specific Dockerfile
docker build -t my-app:1.0 -f Dockerfile.production .

# Build with build arguments
docker build --build-arg VERSION=2.1.0 -t my-app:2.1.0 .

# Build for specific platform
docker build --platform linux/amd64 -t my-app:1.0 .

# Build with no cache
docker build --no-cache -t my-app:1.0 .

# Build and push (BuildKit)
docker buildx build --push -t registry.example.com/my-app:1.0 .
```

### Build Context

```
Build context = directory sent to Docker daemon for building

docker build -t my-app .
                       ^ build context (current directory)

Everything in this directory is sent to daemon:
  - Source code
  - Config files
  - .git directory (unless in .dockerignore!)

Large context = slow build (everything copied to daemon)
Use .dockerignore to exclude unnecessary files
```

### Build Cache

```
Docker caches each layer (instruction result):

FROM node:18           ← cached (base image)
COPY package.json .    ← cached if package.json unchanged
RUN npm install        ← cached if previous layer unchanged
COPY . .               ← INVALIDATED if any file changed
RUN npm run build      ← invalidated (depends on previous)

Cache invalidation rules:
  - If instruction text changes → cache miss
  - If files in COPY/ADD change → cache miss
  - Once a layer misses cache → ALL subsequent layers rebuild

Strategy: Put rarely-changing instructions FIRST
  1. Base image
  2. Install dependencies (package.json/pom.xml)
  3. Copy source code (changes frequently)
  4. Build application
```

### Build Layers

```
Each instruction creates a new layer:

Layer 1: FROM ubuntu:22.04         (base OS)
Layer 2: RUN apt-get install -y... (packages)
Layer 3: COPY requirements.txt .   (dependency file)
Layer 4: RUN pip install -r ...    (dependencies)
Layer 5: COPY . .                  (application code)
Layer 6: RUN python setup.py build (build)

Total image size = sum of all layers
Shared layers between images are stored once

Minimize layers:
  BAD:  RUN apt-get update
        RUN apt-get install -y curl
        RUN apt-get install -y vim
  
  GOOD: RUN apt-get update && \
          apt-get install -y curl vim && \
          rm -rf /var/lib/apt/lists/*
```

### .dockerignore

```
# .dockerignore - exclude from build context
.git
.gitignore
node_modules
target
*.md
*.log
.env
.DS_Store
Dockerfile
docker-compose.yml
.idea
.vscode
**/*.test.js
**/*.spec.ts
```

```
Benefits of .dockerignore:
  - Faster builds (smaller context transfer)
  - Smaller images (no unnecessary files copied)
  - Security (no .env, .git, secrets in image)
  - Prevent cache busting (ignore test files, docs)
```

### Build Arguments

```dockerfile
# ARG: Build-time variables (not in final image)
ARG JAVA_VERSION=21
ARG APP_VERSION=1.0.0

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine
LABEL version=${APP_VERSION}
```

```bash
docker build --build-arg JAVA_VERSION=17 --build-arg APP_VERSION=2.0.0 -t my-app .
```

```
ARG vs ENV:
  ARG: Available only during build. Not in running container.
  ENV: Available during build AND in running container.

ARG scope:
  - Before FROM: Available only in FROM instruction
  - After FROM: Available in that build stage only
```

### Image Tagging

```bash
# Tag during build
docker build -t registry.example.com/my-app:2.1.0 .
docker build -t registry.example.com/my-app:latest .

# Tag existing image
docker tag my-app:1.0 registry.example.com/my-app:1.0
docker tag my-app:1.0 registry.example.com/my-app:latest

# Multiple tags
docker build \
  -t my-app:2.1.0 \
  -t my-app:latest \
  -t registry.example.com/my-app:2.1.0 \
  .

# Tagging strategy:
#   my-app:2.1.0       (semantic version)
#   my-app:abc123f     (git SHA)
#   my-app:2.1.0-abc   (combined)
#   my-app:latest      (mutable, avoid in production)
```

### BuildKit

```
BuildKit: Next-generation Docker build engine (default since Docker 23.0)

Advantages:
  - Parallel execution of independent stages
  - Better caching (mount cache for package managers)
  - Secret mounting (no secrets in layers)
  - SSH forwarding (private repos during build)
  - Progress output improvements

# Enable BuildKit (if not default)
DOCKER_BUILDKIT=1 docker build -t my-app .

# BuildKit cache mounts (package manager cache)
RUN --mount=type=cache,target=/root/.m2 mvn package
RUN --mount=type=cache,target=/root/.npm npm install

# BuildKit secrets (not stored in layer)
RUN --mount=type=secret,id=npmrc,target=/root/.npmrc npm install
docker build --secret id=npmrc,src=.npmrc -t my-app .
```

### Reproducible Builds

```
For reproducible builds:
  1. Pin base image versions: FROM node:18.19.0-alpine3.19
  2. Pin dependency versions: package-lock.json / pom.xml
  3. Use specific package versions: apt-get install curl=7.88.1-10
  4. Set build args for versions
  5. Use image digest: FROM node@sha256:abc123...

# Fully reproducible
FROM eclipse-temurin:21.0.2_13-jre-alpine@sha256:abc123...
```

---

## Internal Working

```
Docker Build Process:

1. Client sends build context to Docker daemon (tar archive)
2. Daemon reads Dockerfile instruction by instruction
3. For each instruction:
   a. Check cache: same instruction + same context → use cached layer
   b. If cache miss: execute instruction, create new layer
   c. Each layer = read-only filesystem snapshot
4. Final image = stack of all layers + metadata (CMD, ENTRYPOINT, etc.)
5. Image stored locally (can be pushed to registry)

BuildKit parallelism:
  FROM base AS deps         ←─┐
  RUN npm install              │ These run in parallel!
                               │
  FROM base AS build       ←─┘
  RUN npm run build

  FROM runtime
  COPY --from=deps ...
  COPY --from=build ...
```

---

## Interview Questions

### Q1: How does Docker build cache work?

**A:** Docker caches each layer (instruction result). If an instruction and its inputs haven't changed, Docker reuses the cached layer. Cache invalidation is cascading — once one layer changes, all subsequent layers rebuild. Strategy: order instructions from least to most frequently changing (base image → dependencies → source code → build).

### Q2: What is .dockerignore and why is it important?

**A:** `.dockerignore` excludes files from the build context sent to Docker daemon. Benefits: faster builds (smaller context), prevents secrets from entering image (.env, .git), avoids cache busting (changing README doesn't invalidate COPY), smaller images. Essential entries: .git, node_modules, target, .env, test files.

### Q3: How do you optimize Docker build speed?

**A:**
1. Order Dockerfile: stable layers first, changing layers last
2. Copy dependency files before source (package.json before code)
3. Use .dockerignore (smaller context)
4. Use BuildKit cache mounts (`--mount=type=cache`)
5. Multi-stage builds (parallel stages)
6. Use smaller base images (alpine)
7. Combine RUN commands (fewer layers)

### Q4: What is the difference between ARG and ENV?

**A:**
- **ARG:** Build-time only. Available during `docker build`. Not in running container. Can be set with `--build-arg`. Used for version pins, build flags.
- **ENV:** Build-time AND runtime. Persists in the image. Available in running container. Used for application configuration.

### Q5: What is BuildKit and what are its advantages?

**A:** BuildKit is Docker's modern build engine. Advantages: parallel execution of independent build stages, cache mounts (persist package manager caches), secret mounting (secrets don't end up in layers), SSH forwarding (access private repos during build), better output/progress display, and skip unused stages.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| COPY . . before dependency install | Cache invalidated on any code change | Copy dependency file first, install, then copy code |
| No .dockerignore | Large context, slow builds, secrets leaked | Add .dockerignore with .git, node_modules, etc. |
| Using :latest base image | Non-reproducible builds | Pin specific version |
| Too many RUN instructions | Many layers, larger image | Combine with && |
| Secrets in ARG/ENV | Visible in image history | Use BuildKit --mount=type=secret |

---

## Best Practices

1. **Order for cache efficiency** — stable layers first
2. **Use .dockerignore** — exclude everything not needed
3. **Pin all versions** — base images, packages, dependencies
4. **Use multi-stage builds** — separate build from runtime
5. **Use BuildKit features** — cache mounts, secrets, parallelism
6. **Minimize layers** — combine RUN commands
7. **Clean up in same layer** — `apt-get install && rm -rf /var/lib/apt/lists/*`
8. **Tag meaningfully** — semantic version + git SHA

---

## Related Topics

- [06. Dockerfile](./06-dockerfile.md)
- [14. Multi-Stage Builds](./14-multi-stage-builds.md)
- [04. Docker Images](./04-docker-images.md)
- [09. Docker Registry](./09-docker-registry.md)
