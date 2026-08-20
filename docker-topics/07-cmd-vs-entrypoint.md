# 7. CMD vs ENTRYPOINT ⭐⭐⭐

---

## Theory

CMD and ENTRYPOINT define what command runs when a container starts. Understanding their interaction is critical for building flexible, production-ready images.

### CMD

```dockerfile
# CMD: Default command (can be overridden)
CMD ["java", "-jar", "app.jar"]         # Exec form (preferred)
CMD java -jar app.jar                    # Shell form

# Overridden at runtime:
# docker run my-app echo "hello"  ← replaces CMD entirely
```

```
CMD provides defaults for an executing container:
  - Can be overridden completely with docker run arguments
  - Only the LAST CMD in Dockerfile takes effect
  - If ENTRYPOINT exists, CMD provides default arguments to it
```

### ENTRYPOINT

```dockerfile
# ENTRYPOINT: Main command (harder to override)
ENTRYPOINT ["java", "-jar", "app.jar"]  # Exec form (preferred)
ENTRYPOINT java -jar app.jar             # Shell form

# NOT overridden by docker run arguments:
# docker run my-app echo "hello"  ← "echo hello" APPENDED to ENTRYPOINT
# Override: docker run --entrypoint /bin/sh my-app
```

```
ENTRYPOINT defines the container's main executable:
  - NOT replaced by docker run arguments (they're appended)
  - Can be overridden with --entrypoint flag
  - Preferred for defining the application that always runs
```

### Shell Form

```dockerfile
# Shell form: command is wrapped in /bin/sh -c "..."
CMD java -jar app.jar
ENTRYPOINT java -jar app.jar

# Internally becomes:
# /bin/sh -c "java -jar app.jar"

Problems with shell form:
  - PID 1 is /bin/sh, not your application
  - Signals (SIGTERM) go to shell, not your app
  - No graceful shutdown!
  - Shell features available ($VAR expansion, pipes)
```

### Exec Form

```dockerfile
# Exec form: JSON array, no shell wrapper
CMD ["java", "-jar", "app.jar"]
ENTRYPOINT ["java", "-jar", "app.jar"]

# Application is PID 1 directly
# Receives signals directly (SIGTERM for graceful shutdown)
# No shell variable expansion ($VAR won't work)
# ALWAYS use exec form in production!
```

```
Shell Form vs Exec Form:

┌──────────────┬──────────────────────┬──────────────────────┐
│ Aspect       │ Shell Form           │ Exec Form            │
├──────────────┼──────────────────────┼──────────────────────┤
│ PID 1        │ /bin/sh              │ Your process         │
│ Signals      │ Go to shell          │ Go to your app       │
│ Graceful     │ ✗ No                 │ ✓ Yes                │
│ Var expansion│ ✓ Yes ($HOME works)  │ ✗ No                 │
│ Syntax       │ CMD command arg      │ CMD ["cmd", "arg"]   │
│ Use in prod  │ ✗ Avoid              │ ✓ Always             │
└──────────────┴──────────────────────┴──────────────────────┘
```

### Override CMD

```bash
# CMD in Dockerfile: CMD ["python", "app.py"]

# Override at runtime:
docker run my-app python test.py        # Replaces CMD entirely
docker run my-app /bin/bash             # Interactive shell instead

# docker-compose.yml:
services:
  app:
    image: my-app
    command: ["python", "test.py"]       # Overrides CMD
```

### Override ENTRYPOINT

```bash
# ENTRYPOINT in Dockerfile: ENTRYPOINT ["java", "-jar", "app.jar"]

# Override at runtime:
docker run --entrypoint /bin/sh my-app   # Replace ENTRYPOINT
docker run --entrypoint "" my-app cmd    # Clear ENTRYPOINT

# docker-compose.yml:
services:
  app:
    image: my-app
    entrypoint: ["/bin/sh", "-c"]
    command: ["echo hello"]
```

### Combining CMD + ENTRYPOINT

```dockerfile
# Pattern: ENTRYPOINT = command, CMD = default arguments
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=production"]

# docker run my-app
# → java -jar app.jar --spring.profiles.active=production

# docker run my-app --spring.profiles.active=dev
# → java -jar app.jar --spring.profiles.active=dev (CMD replaced)
```

```
Best pattern for flexibility:

ENTRYPOINT ["executable"]     ← Always runs
CMD ["default-args"]          ← Override with docker run args

Examples:
  ENTRYPOINT ["nginx"]
  CMD ["-g", "daemon off;"]
  
  ENTRYPOINT ["python"]
  CMD ["app.py"]
  
  ENTRYPOINT ["java", "-jar"]
  CMD ["app.jar"]
```

---

## Internal Working

```
Execution resolution:

1. No ENTRYPOINT, no CMD:        Error (nothing to run)
2. Only CMD:                     Run CMD
3. Only ENTRYPOINT:              Run ENTRYPOINT
4. ENTRYPOINT + CMD:             Run ENTRYPOINT with CMD as args
5. ENTRYPOINT + docker run args: Run ENTRYPOINT with run args (CMD ignored)

Signal handling:
  Shell form:    SIGTERM → /bin/sh → (may not forward) → app
  Exec form:     SIGTERM → app directly → graceful shutdown

Docker stop flow:
  1. Docker sends SIGTERM to PID 1
  2. Waits 10 seconds (--stop-timeout)
  3. If still running: sends SIGKILL (force kill)
  
  With shell form: app never gets SIGTERM → always SIGKILL after 10s
  With exec form: app gets SIGTERM → graceful shutdown
```

---

## Diagram

```
┌──────────────── CMD vs ENTRYPOINT ────────────────────────┐
│                                                             │
│  Dockerfile:                                               │
│    ENTRYPOINT ["java", "-jar"]                             │
│    CMD ["app.jar", "--profile=prod"]                       │
│                                                             │
│  Scenario 1: docker run my-app                             │
│  → java -jar app.jar --profile=prod                        │
│    (ENTRYPOINT + CMD)                                      │
│                                                             │
│  Scenario 2: docker run my-app app.jar --profile=dev       │
│  → java -jar app.jar --profile=dev                         │
│    (ENTRYPOINT + runtime args, CMD replaced)               │
│                                                             │
│  Scenario 3: docker run --entrypoint /bin/sh my-app        │
│  → /bin/sh                                                 │
│    (ENTRYPOINT replaced, CMD ignored)                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Code

### Production Spring Boot Dockerfile:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/target/*.jar app.jar

USER appuser

# ENTRYPOINT: always run java
# CMD: default JVM flags (overridable)
ENTRYPOINT ["java"]
CMD ["-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-jar", "app.jar"]

# Override JVM flags:
# docker run my-app -Xmx512m -jar app.jar
```

### Wrapper Script Pattern:

```dockerfile
# Use entrypoint script for setup before main process
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]
```

```bash
#!/bin/sh
# docker-entrypoint.sh

# Setup tasks
echo "Starting with profile: $SPRING_PROFILES_ACTIVE"
echo "Waiting for database..."
until nc -z $DB_HOST 5432; do sleep 1; done

# Execute CMD (passes all arguments)
exec "$@"
```

```
Key: exec "$@" replaces the shell with CMD process
  - CMD becomes PID 1 (receives signals)
  - Graceful shutdown works correctly
  - Without exec: shell is PID 1 (bad!)
```

---

## Interview Questions

### Q1: What is the difference between CMD and ENTRYPOINT?

**A:**
- **CMD:** Default command/arguments. Easily overridden by `docker run` arguments. Provides defaults.
- **ENTRYPOINT:** Main executable. NOT replaced by `docker run` arguments (they're appended). Overridden only with `--entrypoint` flag.

Combined: ENTRYPOINT is the command, CMD provides default arguments that can be overridden at runtime.

### Q2: What is the difference between shell form and exec form?

**A:**
- **Shell form** (`CMD command arg`): Wrapped in `/bin/sh -c`. Shell is PID 1, app doesn't receive signals, no graceful shutdown. Use only when you need shell features (pipes, variable expansion).
- **Exec form** (`CMD ["cmd", "arg"]`): Direct execution. App is PID 1, receives SIGTERM directly, supports graceful shutdown. Always use in production.

### Q3: Why is `exec "$@"` important in entrypoint scripts?

**A:** `exec` replaces the current shell process with the command (CMD). Without exec, the shell remains as PID 1 and the app runs as a child process. This means SIGTERM goes to the shell (which may not forward it), preventing graceful shutdown. With exec, the app becomes PID 1 and receives signals directly.

### Q4: How do CMD and ENTRYPOINT interact in Kubernetes?

**A:** In K8s pod spec:
- `command:` overrides ENTRYPOINT
- `args:` overrides CMD
- If only `args:` specified, ENTRYPOINT runs with those args
- If only `command:` specified, CMD is ignored

### Q5: What happens when you `docker stop` a container using shell form?

**A:** Docker sends SIGTERM to PID 1 (which is `/bin/sh`, not your app). The shell typically doesn't forward SIGTERM to child processes. After 10 seconds, Docker sends SIGKILL (force kill). Result: no graceful shutdown — in-flight requests dropped, connections not closed, resources not cleaned up.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using shell form in production | No graceful shutdown | Always use exec form |
| Both CMD and ENTRYPOINT as shell form | Confusing behavior | Use exec form for both |
| Not using exec in entrypoint scripts | Shell is PID 1 | Add `exec "$@"` at end |
| Multiple CMD instructions | Only last one takes effect | Use only one CMD |
| Hardcoding args in ENTRYPOINT | Can't override at runtime | Put overridable args in CMD |

---

## Best Practices

1. **Always use exec form** in production Dockerfiles
2. **ENTRYPOINT for the executable** — what always runs
3. **CMD for default arguments** — what's overridable
4. **Use wrapper scripts with `exec "$@"`** for setup tasks
5. **Test signal handling** — `docker stop` should be graceful
6. **Keep ENTRYPOINT minimal** — just the executable
7. **Document override options** — make it clear what's configurable

---

## Related Topics

- [06. Dockerfile](./06-dockerfile.md)
- [21. Docker Process Model](./21-docker-process-model.md)
- [15. Docker + Java/Spring Boot](./15-docker-java-spring-boot.md)
- [08. Docker Build](./08-docker-build.md)
