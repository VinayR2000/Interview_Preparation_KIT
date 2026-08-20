# 6. Dockerfile ⭐⭐⭐

---

## Theory

A **Dockerfile** is a text file containing instructions for building a Docker image — each instruction creates a layer.

### FROM

```dockerfile
# Base image (MUST be first instruction)
FROM ubuntu:22.04
FROM alpine:3.18
FROM eclipse-temurin:21-jre-alpine
FROM scratch                        # Empty base (static binaries)
FROM node:20-alpine AS build        # Named stage (multi-stage)
```

### RUN

```dockerfile
# Execute commands during build (creates new layer)
RUN apt-get update && apt-get install -y curl

# Best practice: combine commands to reduce layers
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl wget && \
    rm -rf /var/lib/apt/lists/*

# Each RUN = new layer. Fewer RUN = smaller image.
```

### CMD

```dockerfile
# Default command when container starts (can be overridden)
CMD ["java", "-jar", "app.jar"]         # Exec form (preferred)
CMD java -jar app.jar                    # Shell form (runs in /bin/sh -c)

# Only ONE CMD per Dockerfile (last one wins)
# Overridden by: docker run my-app custom-command
```

### ENTRYPOINT

```dockerfile
# Main executable (harder to override than CMD)
ENTRYPOINT ["java", "-jar", "app.jar"]  # Exec form
ENTRYPOINT java -jar app.jar             # Shell form

# Override with: docker run --entrypoint sh my-app
# CMD becomes arguments to ENTRYPOINT when both are set
```

### COPY

```dockerfile
# Copy files from build context to image
COPY app.jar /app/app.jar
COPY . /app/                    # Copy everything
COPY --chown=1000:1000 . /app/  # Set ownership
COPY --from=build /app/target/*.jar /app/  # From another stage
```

### ADD

```dockerfile
# Like COPY but with extra features:
ADD https://example.com/file.tar.gz /app/   # Download from URL
ADD archive.tar.gz /app/                     # Auto-extract tar files

# Prefer COPY over ADD (more explicit, predictable)
# Only use ADD for URL downloads or tar extraction
```

### WORKDIR

```dockerfile
# Set working directory for subsequent instructions
WORKDIR /app
RUN pwd           # /app
COPY . .          # Copies to /app/
CMD ["./start"]   # Runs from /app/

# Creates directory if it doesn't exist
# Can use multiple times
```

### ENV

```dockerfile
# Set environment variables (persist in running container)
ENV JAVA_HOME=/usr/lib/jvm/java-21
ENV APP_PORT=8080 LOG_LEVEL=INFO

# Available during build AND at runtime
# Override at runtime: docker run -e APP_PORT=9090
```

### ARG

```dockerfile
# Build-time variables (NOT available at runtime)
ARG JAR_FILE=app.jar
ARG VERSION=1.0.0

COPY target/${JAR_FILE} /app/app.jar
LABEL version=${VERSION}

# Pass at build time: docker build --build-arg VERSION=2.0.0 .
# NOT available in running container (use ENV if needed at runtime)
```

### EXPOSE

```dockerfile
# Document which ports the container listens on (informational only!)
EXPOSE 8080
EXPOSE 8080/tcp 9090/udp

# Does NOT actually publish the port
# Still need: docker run -p 8080:8080 to map ports
# Serves as documentation for users of the image
```

### USER

```dockerfile
# Set user for subsequent RUN, CMD, ENTRYPOINT
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Security: Don't run as root!
# Numeric UID also works: USER 1000
```

### LABEL

```dockerfile
# Metadata for the image
LABEL maintainer="team@example.com"
LABEL version="2.1.0"
LABEL description="Order processing service"
LABEL org.opencontainers.image.source="https://github.com/..."
```

### VOLUME

```dockerfile
# Declare mount points (creates anonymous volume if not mounted)
VOLUME /app/data
VOLUME ["/data", "/logs"]

# Best practice: Use named volumes at runtime instead
# docker run -v my-data:/app/data
```

### HEALTHCHECK

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8080/health || exit 1

# Docker reports container health status: healthy/unhealthy
# Used by: docker compose, Swarm (NOT directly by Kubernetes)
```

### SHELL

```dockerfile
# Change default shell (default: /bin/sh -c)
SHELL ["/bin/bash", "-c"]
RUN echo "now using bash"

# Useful when you need bash features in RUN
```

### STOPSIGNAL

```dockerfile
# Signal sent to container on docker stop (default: SIGTERM)
STOPSIGNAL SIGQUIT    # For nginx graceful shutdown
STOPSIGNAL SIGINT     # For some apps that handle SIGINT
```

---

## Code

### Production Spring Boot Dockerfile:

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy artifact
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

# Metadata
LABEL maintainer="backend-team@example.com"
LABEL version="2.1.0"
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Run as non-root
USER spring

# JVM configuration
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

---

## Interview Questions

### Q1: What is the difference between COPY and ADD?

**A:** Both copy files into the image, but ADD has extra features: auto-extracts tar archives and can download from URLs. COPY is preferred because it's explicit and predictable. Use ADD only when you specifically need tar extraction or URL download.

### Q2: What is the difference between CMD and ENTRYPOINT?

**A:**
- **CMD:** Default command/arguments. Easily overridden by `docker run` arguments.
- **ENTRYPOINT:** Main executable. Not easily overridden (requires `--entrypoint` flag).
- **Combined:** ENTRYPOINT is the command, CMD provides default arguments that can be overridden.

### Q3: What is the difference between ENV and ARG?

**A:**
- **ARG:** Build-time only. Available during `docker build`. NOT in running container. Passed with `--build-arg`.
- **ENV:** Both build-time and runtime. Persists in the image. Available in running container. Overridden with `docker run -e`.

### Q4: Why should you combine RUN commands?

**A:** Each RUN creates a layer. Layers are additive — deleting a file in a later layer doesn't reduce image size (the file exists in the previous layer). Combining RUNs into one reduces layers and lets you clean up temp files in the same layer:
```dockerfile
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Running as root | Security vulnerability | Add USER instruction |
| COPY before dependency install | Cache invalidated on code change | COPY deps first, then code |
| Using ADD instead of COPY | Unexpected behavior | Use COPY unless tar/URL needed |
| Missing .dockerignore | Build context too large | Add .dockerignore |
| Using :latest in FROM | Non-reproducible builds | Pin specific versions |
| Separate RUN for install + cleanup | Files still in previous layer | Combine in one RUN |

---

## Best Practices

1. **Order by change frequency** — stable instructions first
2. **Combine RUN commands** — fewer layers, smaller image
3. **Use specific base image tags** — reproducibility
4. **Run as non-root USER** — security
5. **Use multi-stage builds** — separate build and runtime
6. **COPY over ADD** — explicit and predictable
7. **Set WORKDIR** — don't use absolute paths everywhere
8. **Add HEALTHCHECK** — container health monitoring

---

## Related Topics

- [07. CMD vs ENTRYPOINT](./07-cmd-vs-entrypoint.md)
- [08. Docker Build](./08-docker-build.md)
- [14. Multi-Stage Builds](./14-multi-stage-builds.md)
- [16. Docker Security](./16-docker-security.md)
