# 14. Docker Multi-Stage Builds ⭐⭐⭐

---

## Theory

**Multi-stage builds** use multiple `FROM` statements in a single Dockerfile. Each stage can use a different base image. Only the final stage becomes the production image, dramatically reducing image size and attack surface.

### Problem Without Multi-Stage

```
Single-stage build:
  ┌────────────────────────────────────┐
  │ Production Image (600MB+)          │
  │   - JDK (not needed at runtime)    │
  │   - Maven (not needed at runtime)  │
  │   - Build cache                    │
  │   - Source code                    │
  │   - Compiled application           │
  └────────────────────────────────────┘

Multi-stage build:
  ┌────────────────────────────────────┐
  │ Build Stage (discarded)            │
  │   - JDK + Maven + source code     │
  │   - Compiles → produces .jar      │
  └────────────────────────────────────┘
              │ COPY --from=build
              ▼
  ┌────────────────────────────────────┐
  │ Production Image (200MB)           │
  │   - JRE only                       │
  │   - Compiled .jar                  │
  │   - Nothing else                   │
  └────────────────────────────────────┘
```

### Multi-Stage Concept

```dockerfile
# Stage 1: Build (this entire stage is discarded)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src/ src/
RUN mvn package -DskipTests

# Stage 2: Production (this becomes the final image)
FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```
Key points:
  - Multiple FROM statements = multiple stages
  - Each stage starts fresh (own filesystem)
  - AS <name> gives stage a reference name
  - COPY --from=<stage> copies between stages
  - Only LAST stage becomes the final image
  - Earlier stages are build-time only (not in output)
```

### Named Stages

```dockerfile
# Named stages for clarity
FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci --production

FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS production
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
USER node
EXPOSE 3000
CMD ["node", "dist/main.js"]
```

### Build Target (Partial Builds)

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jdk AS development
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080 5005
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]

FROM eclipse-temurin:21-jre-alpine AS production
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build only up to a specific target
docker build --target development -t myapp:dev .
docker build --target production -t myapp:prod .
```

### Layer Caching Optimization

```dockerfile
# OPTIMIZED — dependency layer cached separately
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Step 1: Copy only dependency descriptors
COPY pom.xml .

# Step 2: Download dependencies (CACHED if pom.xml unchanged)
RUN mvn dependency:go-offline

# Step 3: Copy source (changes frequently)
COPY src/ src/

# Step 4: Build (only reruns if source changed)
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```
Cache behavior:
  If pom.xml unchanged → Step 2 cached (saves minutes!)
  If only src/ changed → Steps 1-2 cached, only 3-4 rerun
  
  Result: Rebuild goes from 3 minutes → 20 seconds
```

### Copying from External Images

```dockerfile
# Copy from any image — not just previous stages
FROM eclipse-temurin:21-jre-alpine

# Copy from a published image
COPY --from=busybox:latest /bin/wget /usr/local/bin/wget

# Copy from a specific stage
COPY --from=build /app/target/*.jar app.jar
```

---

## Code

### Java/Spring Boot Multi-Stage:

```dockerfile
# ============================================
# Stage 1: Dependency Resolution (cacheable)
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# ============================================
# Stage 2: Build
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY --from=deps /root/.m2 /root/.m2
COPY pom.xml .
COPY src/ src/
RUN mvn package -DskipTests -o

# ============================================
# Stage 3: Production Image
# ============================================
FROM eclipse-temurin:21-jre-alpine AS production

# Security: non-root user
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
```

### Angular Multi-Stage:

```dockerfile
# Stage 1: Build Angular app
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve with nginx
FROM nginx:alpine AS production
COPY --from=build /app/dist/my-app/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Go Multi-Stage (Extreme Size Reduction):

```dockerfile
# Stage 1: Build (1GB+)
FROM golang:1.22 AS build
WORKDIR /app
COPY go.* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o server .

# Stage 2: Scratch image (< 10MB!)
FROM scratch
COPY --from=build /app/server /server
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
EXPOSE 8080
ENTRYPOINT ["/server"]

# Result: 1GB+ build → < 10MB production image
```

### Docker Compose with Build Targets:

```yaml
services:
  api-dev:
    build:
      context: .
      target: development
    ports:
      - "8080:8080"
      - "5005:5005"          # Debug port
    volumes:
      - ./src:/app/src       # Hot reload

  api-prod:
    build:
      context: .
      target: production
    ports:
      - "8080:8080"
```

---

## Image Size Comparison

```
┌──────────────────────────────────────────────────────────┐
│ Base Image                        │ Size    │ Use Case   │
├───────────────────────────────────┼─────────┼────────────┤
│ ubuntu:22.04                      │ 77 MB   │ Dev only   │
│ eclipse-temurin:21-jdk            │ 460 MB  │ Dev only   │
│ eclipse-temurin:21-jre            │ 270 MB  │ Standard   │
│ eclipse-temurin:21-jre-alpine     │ 190 MB  │ Production │
│ gcr.io/distroless/java21         │ 230 MB  │ Hardened   │
│ node:20                           │ 350 MB  │ Dev only   │
│ node:20-alpine                    │ 130 MB  │ Production │
│ nginx:alpine                      │ 40 MB   │ Serving    │
│ golang:1.22                       │ 800 MB  │ Dev only   │
│ scratch                           │ 0 MB    │ Go/Rust    │
└───────────────────────────────────┴─────────┴────────────┘

Spring Boot app image sizes:
  Without multi-stage (JDK + Maven):  ~800 MB
  With multi-stage (JRE-alpine):      ~210 MB
  With multi-stage (distroless):      ~250 MB
  
  Size reduction: 70-75%
```

---

## Interview Questions

### Q1: What are multi-stage builds and why use them?

**A:** Multi-stage builds use multiple `FROM` statements in a Dockerfile. Each stage starts with a clean filesystem. Only the final stage becomes the output image. Benefits:
1. **Smaller images** — production image has no build tools (70%+ size reduction)
2. **Security** — no compilers, package managers, source code in production
3. **Single Dockerfile** — no need for separate Dockerfile.build and Dockerfile.prod
4. **Layer caching** — dependencies cached separately from source code

### Q2: How does COPY --from work?

**A:** `COPY --from=<stage>` copies files from a previous build stage or external image into the current stage. The source stage's filesystem is accessed to copy specific artifacts (like compiled JARs). The rest of that stage is discarded.

```dockerfile
COPY --from=build /app/target/app.jar .    # From named stage
COPY --from=0 /output/file.txt .           # From stage index
COPY --from=nginx:alpine /etc/nginx .      # From external image
```

### Q3: How do you optimize Docker layer caching for Java projects?

**A:** Copy dependency descriptors (pom.xml) first, download dependencies, THEN copy source code. Since pom.xml changes rarely, the dependency download layer stays cached across builds:

```dockerfile
COPY pom.xml .                    # Rarely changes
RUN mvn dependency:go-offline     # CACHED unless pom.xml changes
COPY src/ src/                    # Changes frequently
RUN mvn package                   # Only recompiles source
```

This reduces rebuild time from minutes to seconds when only source changes.

### Q4: What is a build target and when would you use it?

**A:** A build target (`--target <stage_name>`) stops the build at a specific stage. Use it for:
- **Development image** — includes debug tools, JDK, hot reload
- **Testing image** — includes test frameworks
- **Production image** — minimal, hardened, JRE only

Same Dockerfile, different outputs for different environments.

---

## Common Mistakes

| Mistake | Impact | Fix |
|---------|--------|-----|
| Not using multi-stage | Huge images (800MB+) | Always separate build/runtime |
| Copying source before deps | No layer caching | Copy pom.xml first, then source |
| Using JDK in production | 200MB+ wasted | Use JRE or distroless |
| Forgetting --from in COPY | Wrong files copied | Always reference build stage |
| Not using .dockerignore | Huge build context | Exclude target/, .git/, node_modules/ |
| Single fat RUN command | No granular caching | Split into cacheable steps |

---

## Best Practices

1. **Always multi-stage** — separate build from runtime
2. **Cache dependencies first** — copy lock files before source
3. **Use alpine/distroless** — smallest possible production base
4. **Name your stages** — `AS build`, `AS production` for clarity
5. **Use build targets** — dev/test/prod from one Dockerfile
6. **Pin base image versions** — reproducible builds
7. **Use .dockerignore** — exclude unnecessary files from context
8. **Non-root in final stage** — security best practice

---

## Related Topics

- [06. Dockerfile](./06-dockerfile.md)
- [08. Docker Build](./08-docker-build.md)
- [13. Docker Security](./13-docker-security.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
