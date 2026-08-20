# Advanced Java Design

## Immutability

### Theory
- Immutable objects cannot be modified after creation
- Inherently thread-safe (no synchronization needed)
- Easier to reason about (no unexpected state changes)
- Safe as map keys and set elements

### Rules for Immutable Class
```java
public final class Transaction {  // 1. Class is final (can't be subclassed)
    private final String id;           // 2. All fields are final
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final List<String> tags;   // Mutable field needs defensive copy
    
    public Transaction(String id, BigDecimal amount, LocalDateTime timestamp, List<String> tags) {
        this.id = id;
        this.amount = amount;
        this.timestamp = timestamp;
        this.tags = List.copyOf(tags); // 3. Defensive copy on construction
    }
    
    // 4. No setters
    
    public List<String> getTags() {
        return tags; // Already unmodifiable (List.copyOf returns unmodifiable)
    }
    
    // 5. Return new object for "modifications"
    public Transaction withAmount(BigDecimal newAmount) {
        return new Transaction(this.id, newAmount, this.timestamp, this.tags);
    }
}
```

### Java Records (Java 16+)
```java
// Compact immutable data carrier
public record Money(BigDecimal amount, Currency currency) {
    // Compact constructor for validation
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        Objects.requireNonNull(currency);
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new CurrencyMismatchException();
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
```

---

## Generics for Design

### Bounded Type Parameters
```java
// Repository pattern with generics
public interface Repository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    T save(T entity);
    void deleteById(ID id);
}

public class JpaRepository<T extends BaseEntity, ID extends Serializable> 
    implements Repository<T, ID> {
    // Generic implementation for any entity
}

// Usage
public class UserRepository extends JpaRepository<User, Long> { }
public class OrderRepository extends JpaRepository<Order, UUID> { }
```

### Wildcard Patterns
```java
// Producer Extends, Consumer Super (PECS)
public class Collections {
    // src produces elements (extends)
    // dest consumes elements (super)
    public static <T> void copy(List<? super T> dest, List<? extends T> src) {
        for (T item : src) {
            dest.add(item);
        }
    }
}

// Event system with generics
public interface EventHandler<E extends Event> {
    void handle(E event);
}

public class EventBus {
    private final Map<Class<?>, List<EventHandler<?>>> handlers = new ConcurrentHashMap<>();
    
    public <E extends Event> void register(Class<E> eventType, EventHandler<E> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }
    
    @SuppressWarnings("unchecked")
    public <E extends Event> void publish(E event) {
        List<EventHandler<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            eventHandlers.forEach(h -> ((EventHandler<E>) h).handle(event));
        }
    }
}
```

---

## Functional Interfaces

### Theory
- Interface with exactly one abstract method
- Can be implemented with lambda expressions
- Core functional interfaces: Function, Predicate, Consumer, Supplier

### Design with Functional Interfaces
```java
// Validation framework using Predicate composition
public class Validator<T> {
    private final List<ValidationRule<T>> rules = new ArrayList<>();
    
    public Validator<T> addRule(Predicate<T> condition, String errorMessage) {
        rules.add(new ValidationRule<>(condition, errorMessage));
        return this;
    }
    
    public ValidationResult validate(T object) {
        List<String> errors = rules.stream()
            .filter(rule -> !rule.condition().test(object))
            .map(ValidationRule::message)
            .collect(toList());
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
}

// Usage: Declarative validation
Validator<User> userValidator = new Validator<User>()
    .addRule(u -> u.getName() != null && !u.getName().isBlank(), "Name is required")
    .addRule(u -> u.getEmail().contains("@"), "Invalid email")
    .addRule(u -> u.getAge() >= 18, "Must be 18+");

ValidationResult result = userValidator.validate(user);

// Pipeline pattern with Function composition
public class Pipeline<I, O> {
    private final Function<I, O> function;
    
    private Pipeline(Function<I, O> function) {
        this.function = function;
    }
    
    public static <I> Pipeline<I, I> of(Function<I, I> step) {
        return new Pipeline<>(step);
    }
    
    public <N> Pipeline<I, N> then(Function<O, N> next) {
        return new Pipeline<>(function.andThen(next));
    }
    
    public O execute(I input) {
        return function.apply(input);
    }
}

// Usage
Pipeline<String, OrderResult> orderPipeline = Pipeline
    .<String>of(this::parseRequest)
    .then(this::validateOrder)
    .then(this::reserveInventory)
    .then(this::processPayment)
    .then(this::sendConfirmation);

OrderResult result = orderPipeline.execute(requestJson);
```

---

## Streams for Design

### Collector for Complex Aggregation
```java
// Custom collector: Group and summarize
Map<Category, ProductSummary> summary = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.collectingAndThen(
            Collectors.toList(),
            list -> new ProductSummary(
                list.size(),
                list.stream().mapToDouble(Product::getPrice).average().orElse(0),
                list.stream().mapToDouble(Product::getPrice).max().orElse(0)
            )
        )
    ));

// Stream-based query builder
public class QueryBuilder {
    private final List<Predicate<Product>> filters = new ArrayList<>();
    private Comparator<Product> sorter;
    private int limit = Integer.MAX_VALUE;
    
    public QueryBuilder where(Predicate<Product> filter) {
        filters.add(filter);
        return this;
    }
    
    public QueryBuilder sortBy(Comparator<Product> comparator) {
        this.sorter = comparator;
        return this;
    }
    
    public QueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }
    
    public List<Product> execute(List<Product> products) {
        Stream<Product> stream = products.stream();
        
        Predicate<Product> combined = filters.stream()
            .reduce(Predicate::and)
            .orElse(p -> true);
        
        stream = stream.filter(combined);
        if (sorter != null) stream = stream.sorted(sorter);
        
        return stream.limit(limit).collect(toList());
    }
}
```

---

## Sealed Classes (Java 17+)

### Theory
- Restrict which classes can extend/implement a type
- Exhaustive pattern matching in switch
- Model closed hierarchies (known set of subtypes)

### Code
```java
// Closed hierarchy — all subtypes known at compile time
public sealed interface PaymentEvent 
    permits PaymentInitiated, PaymentSucceeded, PaymentFailed, PaymentRefunded {
    
    String transactionId();
    Instant timestamp();
}

public record PaymentInitiated(String transactionId, Instant timestamp, 
                                BigDecimal amount) implements PaymentEvent {}
public record PaymentSucceeded(String transactionId, Instant timestamp,
                                String providerRef) implements PaymentEvent {}
public record PaymentFailed(String transactionId, Instant timestamp,
                             String errorCode, String reason) implements PaymentEvent {}
public record PaymentRefunded(String transactionId, Instant timestamp,
                               BigDecimal refundAmount) implements PaymentEvent {}

// Exhaustive switch (compiler ensures all cases handled)
public String formatEvent(PaymentEvent event) {
    return switch (event) {
        case PaymentInitiated e -> "Payment of " + e.amount() + " initiated";
        case PaymentSucceeded e -> "Payment succeeded: " + e.providerRef();
        case PaymentFailed e -> "Payment failed: " + e.reason();
        case PaymentRefunded e -> "Refund of " + e.refundAmount() + " processed";
        // No default needed — compiler knows all cases covered
    };
}
```

---

## Virtual Threads (Java 21+)

### Theory
- Lightweight threads managed by JVM (not OS)
- Millions of virtual threads possible (vs thousands of platform threads)
- Perfect for I/O-bound workloads
- Same Thread API — no code changes needed

### Code
```java
// Create virtual threads
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // Each task gets its own virtual thread — no pool sizing needed
    List<Future<Response>> futures = requests.stream()
        .map(req -> executor.submit(() -> httpClient.send(req)))
        .toList();
    
    // Process results
    for (Future<Response> future : futures) {
        Response response = future.get(); // Blocks virtual thread, not OS thread
        process(response);
    }
}

// Structured Concurrency (Preview in Java 21)
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<User> user = scope.fork(() -> fetchUser(userId));
    Subtask<Orders> orders = scope.fork(() -> fetchOrders(userId));
    Subtask<Reviews> reviews = scope.fork(() -> fetchReviews(userId));
    
    scope.join();            // Wait for all
    scope.throwIfFailed();   // Propagate first failure
    
    return new UserProfile(user.get(), orders.get(), reviews.get());
}
```

### Virtual Threads vs Platform Threads
| Aspect | Platform Threads | Virtual Threads |
|--------|-----------------|-----------------|
| Created by | OS | JVM |
| Memory | ~1MB stack each | ~few KB |
| Scalability | Thousands | Millions |
| Context switch | Expensive (OS) | Cheap (JVM) |
| Best for | CPU-bound | I/O-bound |
| Pooling needed | Yes (expensive to create) | No (cheap to create) |
| synchronized | OK | Avoid (pins carrier thread) |

---

## Thread-Safe Collections

### Comparison
| Collection | Thread-Safe? | Mechanism | Performance |
|-----------|-------------|-----------|-------------|
| HashMap | No | N/A | Fastest (single-thread) |
| Hashtable | Yes | synchronized on everything | Slowest |
| Collections.synchronizedMap | Yes | Wrapper synchronized | Medium |
| ConcurrentHashMap | Yes | CAS + fine-grained locks | Best concurrent |
| CopyOnWriteArrayList | Yes | Copy on every write | Best for read-heavy |

### CopyOnWriteArrayList
```java
// Perfect for: observer lists (rarely modified, frequently iterated)
private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

public void addListener(EventListener l) { listeners.add(l); }
public void removeListener(EventListener l) { listeners.remove(l); }

public void fireEvent(Event e) {
    // No ConcurrentModificationException — iterates over snapshot
    for (EventListener listener : listeners) {
        listener.onEvent(e);
    }
}
```

---

## Interview Questions

**Q: How would you design a thread-safe cache with expiry?**
> Use ConcurrentHashMap with entries containing value + timestamp. Background thread (ScheduledExecutorService) periodically scans and removes expired entries. Use computeIfAbsent for atomic get-or-load. Consider Caffeine library for production (handles all edge cases).

**Q: When should you use virtual threads?**
> Use virtual threads for I/O-bound workloads: HTTP calls, database queries, file I/O. Don't use for CPU-bound computation (no benefit, same CPU cores). Avoid synchronized inside virtual threads (pins the carrier thread) — use ReentrantLock instead.

**Q: How do you handle the "write amplification" problem with CopyOnWriteArrayList?**
> CopyOnWriteArrayList copies the entire array on every write. Only use when reads vastly outnumber writes (>100:1 ratio). For higher write frequency, use ConcurrentLinkedQueue or synchronized with manual list. Profile to verify write frequency before choosing.

**Q: Design a generic, type-safe event bus using generics.**
> Map<Class<E>, List<EventHandler<E>>> — keyed by event type. Use bounded wildcards for registration. The challenge is type erasure — need @SuppressWarnings for the cast. Alternative: use a framework (Spring ApplicationEventPublisher, Guava EventBus) that handles this.

---

## Common Mistakes
- Returning mutable internal state from "immutable" class (missing defensive copy)
- Using synchronized inside virtual threads (use ReentrantLock instead)
- Overusing CopyOnWriteArrayList for write-heavy collections
- Creating raw types instead of proper generics
- Not considering memory overhead of immutable objects (object creation pressure)
- Using Streams where simple loops are clearer (over-engineering)

---

## Best Practices
- Use Records for data carriers (DTOs, events, value objects)
- Sealed classes for closed hierarchies (state machines, event types)
- Functional interfaces for pluggable behavior
- Virtual threads for I/O-heavy services (HTTP clients, DB queries)
- Generics for type-safe reusable components
- Immutability by default — mutability only where needed

---

## Production Considerations
- Virtual threads: Monitor carrier thread pinning (jdk.tracePinnedThreads)
- Immutability: Consider memory impact for large objects (object pooling)
- Sealed classes: Plan for extensibility (once sealed, can't add subtypes externally)
- Streams: Avoid parallel streams in web servers (shared ForkJoinPool)
- Generics: Type erasure means no runtime type checks (design accordingly)

---

## Related Topics
- Concurrent Design
- Java Memory Model
- Design Patterns with Java 17+ features
- Reactive Programming (Project Reactor)
