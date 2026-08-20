# 17. Spring AOP (Aspect-Oriented Programming)

## Theory

AOP is a programming paradigm that separates cross-cutting concerns (logging, security, transactions) from business logic. Instead of scattering these concerns across every class, you define them once in an **Aspect** and apply them declaratively.

### Key Terminology:
- **Aspect**: A module encapsulating cross-cutting concern (e.g., LoggingAspect)
- **Advice**: The action taken by an aspect (the code that runs)
- **Pointcut**: An expression that matches join points (WHERE advice applies)
- **Join Point**: A point during execution (method call, exception throw)
- **Weaving**: Process of linking aspects to target objects (compile-time, load-time, or runtime)
- **Proxy**: The object created by AOP framework that wraps the target (JDK dynamic proxy or CGLIB)

### Advice Types:
- `@Before` - Runs before the method
- `@After` - Runs after method (regardless of outcome)
- `@AfterReturning` - Runs after successful return
- `@AfterThrowing` - Runs after exception is thrown
- `@Around` - Wraps the method (most powerful, controls execution)

### Spring AOP vs AspectJ:
- Spring AOP: Runtime weaving via proxies, method-level only, simpler
- AspectJ: Compile-time/load-time weaving, field-level possible, more powerful

---

## Internal Working

```
Client calls bean.method()
       ↓
Spring Container (bean is actually a PROXY)
       ↓
┌──────────────────────────────────────┐
│          PROXY OBJECT                 │
│                                       │
│  1. Check pointcut expressions        │
│  2. Match against method signature    │
│  3. If matched:                       │
│     - Execute @Before advice          │
│     - Call actual target method        │
│     - Execute @AfterReturning or      │
│       @AfterThrowing                  │
│     - Execute @After advice           │
│                                       │
│  For @Around:                         │
│     - Entire flow controlled by       │
│       ProceedingJoinPoint.proceed()   │
└──────────────────────────────────────┘
       ↓
Target Object (actual bean with business logic)
```

### Proxy Creation:
```
Application startup
       ↓
BeanPostProcessor (AnnotationAwareAspectJAutoProxyCreator)
       ↓
For each bean:
  - Check if any pointcut matches bean's methods
  - If YES → Create proxy
    - Interface present → JDK Dynamic Proxy
    - No interface → CGLIB Proxy (subclass)
  - If NO → Return original bean
       ↓
Proxy stored in ApplicationContext (not the original bean)
```

### @Around Execution:
```
@Around advice code starts
       ↓
Code BEFORE proceed()
       ↓
joinPoint.proceed()  ← Actually calls target method
       ↓
Target method executes
       ↓
Return value comes back to @Around
       ↓
Code AFTER proceed()
       ↓
Return (can modify return value!)
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────┐
│                     CLIENT CODE                           │
│         userService.createUser(dto)                       │
└────────────────────────┬─────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────┐
│                   PROXY (CGLIB)                            │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Interceptor Chain                                    │ │
│  │                                                      │ │
│  │  ┌──────────────┐                                   │ │
│  │  │ @Before      │ → LoggingAspect.logBefore()       │ │
│  │  │ Security     │ → SecurityAspect.checkAccess()    │ │
│  │  └──────────────┘                                   │ │
│  │         ↓                                            │ │
│  │  ┌──────────────┐                                   │ │
│  │  │ @Around      │ → TransactionAspect               │ │
│  │  │ (begin tx)   │    .beginTransaction()            │ │
│  │  └──────────────┘                                   │ │
│  │         ↓                                            │ │
│  │  ┌──────────────────────────┐                       │ │
│  │  │   TARGET METHOD          │                       │ │
│  │  │   userService.createUser │                       │ │
│  │  └──────────────────────────┘                       │ │
│  │         ↓                                            │ │
│  │  ┌──────────────┐                                   │ │
│  │  │ @Around      │ → TransactionAspect               │ │
│  │  │ (commit tx)  │    .commitTransaction()           │ │
│  │  └──────────────┘                                   │ │
│  │         ↓                                            │ │
│  │  ┌──────────────┐                                   │ │
│  │  │ @After       │ → LoggingAspect.logAfter()        │ │
│  │  └──────────────┘                                   │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

---

## Code

### Logging Aspect:

```java
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // Pointcut: all methods in service package
    @Pointcut("execution(* com.example.service.*.*(..))")
    public void serviceLayer() {}

    // Pointcut: all methods annotated with @Loggable
    @Pointcut("@annotation(com.example.annotation.Loggable)")
    public void loggableMethods() {}

    @Before("serviceLayer()")
    public void logBefore(JoinPoint joinPoint) {
        log.info("Entering: {}.{}() with args: {}",
            joinPoint.getTarget().getClass().getSimpleName(),
            joinPoint.getSignature().getName(),
            Arrays.toString(joinPoint.getArgs()));
    }

    @AfterReturning(pointcut = "serviceLayer()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        log.info("Exiting: {}.{}() with result: {}",
            joinPoint.getTarget().getClass().getSimpleName(),
            joinPoint.getSignature().getName(),
            result);
    }

    @AfterThrowing(pointcut = "serviceLayer()", throwing = "exception")
    public void logException(JoinPoint joinPoint, Throwable exception) {
        log.error("Exception in {}.{}(): {}",
            joinPoint.getTarget().getClass().getSimpleName(),
            joinPoint.getSignature().getName(),
            exception.getMessage());
    }
}
```

### Performance Monitoring Aspect:

```java
@Aspect
@Component
@Slf4j
public class PerformanceAspect {

    @Around("@annotation(com.example.annotation.TrackExecutionTime)")
    public Object trackTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            return result;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{}.{}() executed in {} ms",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                duration);

            if (duration > 1000) {
                log.warn("SLOW METHOD DETECTED: {}.{}() took {} ms",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    duration);
            }
        }
    }
}
```

### Retry Aspect:

```java
@Aspect
@Component
public class RetryAspect {

    @Around("@annotation(retryable)")
    public Object retry(ProceedingJoinPoint joinPoint, Retryable retryable) throws Throwable {
        int maxAttempts = retryable.maxAttempts();
        long delay = retryable.delay();
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return joinPoint.proceed();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Attempt {} failed for {}, retrying in {}ms...",
                        attempt, joinPoint.getSignature().getName(), delay);
                    Thread.sleep(delay);
                    delay *= 2;  // Exponential backoff
                }
            }
        }
        throw lastException;
    }
}

// Custom annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {
    int maxAttempts() default 3;
    long delay() default 1000;
}
```

### Pointcut Expression Examples:

```java
// All public methods in service package
@Pointcut("execution(public * com.example.service.*.*(..))")

// Methods returning void
@Pointcut("execution(void com.example..*.*(..))")

// Methods with specific parameter
@Pointcut("execution(* *..UserService.findById(Long))")

// All methods annotated with @Transactional
@Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")

// All classes annotated with @Service
@Pointcut("@within(org.springframework.stereotype.Service)")

// Methods taking specific argument type
@Pointcut("args(com.example.dto.UserDTO, ..)")

// Combine pointcuts
@Pointcut("serviceLayer() && !loggableMethods()")
```

---

## Dry Run

### @Around Advice Execution:

```
Method call: orderService.placeOrder(orderDTO)

1. PROXY intercepts call
2. Matches pointcut: execution(* *.service.*.*(..))

3. @Around advice (PerformanceAspect):
   - startTime = 1699000000000
   - Calls joinPoint.proceed()
   
4. @Before advice (LoggingAspect):
   - Logs: "Entering: OrderService.placeOrder() with args: [OrderDTO{...}]"

5. TARGET METHOD executes:
   - orderService.placeOrder(orderDTO) runs
   - Returns Order{id=1, status=CREATED}

6. @AfterReturning advice (LoggingAspect):
   - Logs: "Exiting: OrderService.placeOrder() with result: Order{id=1}"

7. @After advice:
   - Always runs (cleanup)

8. Back in @Around:
   - duration = currentTime - startTime = 150ms
   - Logs: "OrderService.placeOrder() executed in 150 ms"
   - Returns result to caller

Final: Client receives Order{id=1, status=CREATED}
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| Proxy creation (startup) | O(n × m) where n=beans, m=pointcuts |
| Method interception | O(k) where k = number of matching advices |
| Pointcut matching | O(1) after initial caching |
| JDK proxy call overhead | ~few microseconds per call |
| CGLIB proxy call overhead | ~few microseconds per call |

---

## Real Project Usage

### Audit Logging:
```java
@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditRepo;

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void audit(JoinPoint joinPoint, Auditable auditable, Object result) {
        AuditLog log = AuditLog.builder()
            .action(auditable.action())
            .entity(auditable.entity())
            .userId(SecurityContextHolder.getContext()
                .getAuthentication().getName())
            .timestamp(Instant.now())
            .details(joinPoint.getArgs())
            .build();
        auditRepo.save(log);
    }
}
```

---

## Interview Questions

1. **What is AOP and why do we use it?**
   - Separates cross-cutting concerns from business logic. Avoids code duplication for logging, security, transactions across many classes.

2. **How does Spring AOP work internally?**
   - Uses proxies (JDK Dynamic Proxy for interfaces, CGLIB for classes). BeanPostProcessor creates proxies at startup for beans matching pointcuts.

3. **What's the difference between @Around and @Before + @After?**
   - @Around controls method execution (can prevent it, modify args/return). @Before/@After only observe, can't control execution.

4. **Why doesn't AOP work on self-invocation?**
   - When method A calls method B within the same class, it bypasses the proxy (uses `this` reference, not the proxy). Solution: inject self or use AopContext.

5. **JDK Dynamic Proxy vs CGLIB?**
   - JDK: Requires interface, creates proxy implementing that interface
   - CGLIB: No interface needed, creates subclass of target class. Cannot proxy final methods.

---

## Follow-up Questions

1. How does @Transactional use AOP internally?
   - TransactionInterceptor is an @Around advice. Proxy intercepts method call → begins transaction → calls target → commits on success / rolls back on exception. The proxy is created by BeanPostProcessor during container startup.

2. How to order multiple aspects on the same join point?
   - Use @Order annotation on the @Aspect class. Lower value = higher priority (runs first for @Before, last for @After). E.g., Security @Order(1) runs before Logging @Order(2).

3. Can you apply AOP to private methods? Why not?
   - No. Spring AOP uses proxies (CGLIB subclass or JDK interface proxy). Private methods aren't visible to subclass/proxy. Only full AspectJ (compile-time weaving) supports private methods.

4. What's the difference between Spring AOP and full AspectJ?
   - Spring AOP: Runtime proxies, method-level only, simpler, no special compiler needed. AspectJ: Compile/load-time weaving, supports field access, constructor, private methods. Much more powerful but complex.

5. How does AOP affect performance in high-throughput applications?
   - Proxy adds ~few microseconds per method call. For high-throughput hot paths (called millions/sec), this can accumulate. Measure with profiler. Keep aspect logic lightweight. Avoid broad pointcuts on hot methods.

---

## Common Mistakes

1. **Self-invocation trap** - Calling an advised method from within the same class bypasses proxy
2. **Forgetting to call proceed()** in @Around - Method never executes
3. **Too broad pointcuts** - `execution(* *.*(..))` intercepts everything, destroys performance
4. **Aspect not as @Component** - Won't be picked up by Spring
5. **Ordering issues** - Multiple aspects without @Order can execute in unpredictable order
6. **Catching exception in @Around without re-throwing** - Silently swallows errors

---

## Best Practices

1. **Use specific pointcuts** - Target exact packages/annotations
2. **Use @Order annotation** to control aspect execution order
3. **Prefer @Around for complex scenarios** (timing, retry, transactions)
4. **Create custom annotations** for readable pointcut definitions
5. **Don't put business logic in aspects** - Only cross-cutting concerns
6. **Test aspects in isolation** - Unit test the advice logic
7. **Be mindful of performance** - Heavy logic in @Around affects every matched call

---

## Production Considerations

- **Performance impact**: Each proxy adds ~microseconds overhead. For high-throughput paths, measure impact.
- **Debugging complexity**: Stack traces include proxy layers, making debugging harder
- **Spring Boot DevTools**: Proxy recreation on reload can cause ClassCastException
- **Memory**: Each proxy is an additional object in heap
- **Thread safety**: Aspects are singletons by default — store no mutable state
- **Monitoring**: Track aspect execution time separately from business logic

---

## Related Topics

- Transactions (@Transactional uses AOP)
- Spring Security (method security via AOP)
- Caching (@Cacheable uses AOP)
- Async (@Async uses AOP proxy)
- Retry patterns (Resilience4j uses AOP)
- Logging (cross-cutting concern)
