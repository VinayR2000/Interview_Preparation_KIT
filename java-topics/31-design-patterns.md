# 31. Design Patterns

## Theory

Design patterns are proven, reusable solutions to common software design problems. They are not code — they are templates/blueprints for how to solve a problem in a way that's been validated by decades of software engineering.

### Categories

| Category | Purpose | Examples |
|----------|---------|---------|
| **Creational** | Object creation mechanisms | Singleton, Factory, Builder, Abstract Factory, Prototype |
| **Structural** | Object composition and relationships | Adapter, Decorator, Proxy, Facade, Composite |
| **Behavioral** | Object interaction and responsibility | Strategy, Observer, Template Method, Chain of Responsibility, Command |

### Why Learn Patterns
- Common vocabulary among developers
- Proven solutions to recurring problems
- Frameworks (Spring, Java SE) use them extensively
- Frequently asked in interviews with real framework examples

---

## Internal Working

### Pattern Selection Guide

```
Need to control object creation?
├── Only one instance needed? → Singleton
├── Object creation is complex/multi-step? → Builder
├── Need to decide which class to instantiate at runtime? → Factory Method
├── Need families of related objects? → Abstract Factory
└── Need to copy existing objects? → Prototype

Need to compose objects or adapt interfaces?
├── Interface mismatch? → Adapter
├── Add behavior dynamically? → Decorator
├── Control access to an object? → Proxy
├── Simplify complex subsystem? → Facade
└── Tree structure of objects? → Composite

Need to manage object interactions?
├── Algorithm selection at runtime? → Strategy
├── Notify multiple objects of changes? → Observer
├── Define algorithm skeleton, defer steps? → Template Method
├── Chain of handlers for a request? → Chain of Responsibility
└── Encapsulate a request as an object? → Command
```

---

## Code

### Singleton Pattern

```java
// 1. Eager initialization (simplest, thread-safe)
public class EagerSingleton {
    private static final EagerSingleton INSTANCE = new EagerSingleton();
    
    private EagerSingleton() {} // Private constructor
    
    public static EagerSingleton getInstance() {
        return INSTANCE;
    }
}

// 2. Double-checked locking (lazy, thread-safe)
public class LazyDCLSingleton {
    private static volatile LazyDCLSingleton instance; // volatile prevents reordering
    
    private LazyDCLSingleton() {}
    
    public static LazyDCLSingleton getInstance() {
        if (instance == null) {                  // First check (no lock)
            synchronized (LazyDCLSingleton.class) {
                if (instance == null) {          // Second check (with lock)
                    instance = new LazyDCLSingleton();
                }
            }
        }
        return instance;
    }
}

// 3. Bill Pugh (lazy, thread-safe, no synchronization overhead)
public class BillPughSingleton {
    private BillPughSingleton() {}
    
    // Inner class loaded only when getInstance() is called
    private static class Holder {
        private static final BillPughSingleton INSTANCE = new BillPughSingleton();
    }
    
    public static BillPughSingleton getInstance() {
        return Holder.INSTANCE;
    }
}

// 4. Enum singleton (best - handles serialization and reflection)
public enum EnumSingleton {
    INSTANCE;
    
    private int value;
    
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
}

// Real-world Spring example: @Component classes are singletons by default
```

### Factory Method Pattern

```java
// Product interface
public interface Notification {
    void send(String recipient, String message);
}

// Concrete products
public class EmailNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Email to " + recipient + ": " + message);
    }
}

public class SmsNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("SMS to " + recipient + ": " + message);
    }
}

public class PushNotification implements Notification {
    @Override
    public void send(String recipient, String message) {
        System.out.println("Push to " + recipient + ": " + message);
    }
}

// Factory
public class NotificationFactory {
    
    public static Notification create(String type) {
        return switch (type.toUpperCase()) {
            case "EMAIL" -> new EmailNotification();
            case "SMS" -> new SmsNotification();
            case "PUSH" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}

// Usage
Notification notif = NotificationFactory.create("EMAIL");
notif.send("user@email.com", "Hello!");

// Spring equivalent: BeanFactory, ApplicationContext
// JDBC equivalent: DriverManager.getConnection() is a factory
```

### Builder Pattern

```java
public class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final int timeout;
    private final boolean followRedirects;
    
    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = Collections.unmodifiableMap(builder.headers);
        this.body = builder.body;
        this.timeout = builder.timeout;
        this.followRedirects = builder.followRedirects;
    }
    
    // Getters...
    
    public static class Builder {
        // Required
        private final String method;
        private final String url;
        
        // Optional with defaults
        private Map<String, String> headers = new HashMap<>();
        private String body = null;
        private int timeout = 30000;
        private boolean followRedirects = true;
        
        public Builder(String method, String url) {
            this.method = method;
            this.url = url;
        }
        
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this; // Fluent API
        }
        
        public Builder body(String body) {
            this.body = body;
            return this;
        }
        
        public Builder timeout(int millis) {
            this.timeout = millis;
            return this;
        }
        
        public Builder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }
        
        public HttpRequest build() {
            // Validation
            if (method == null || url == null) {
                throw new IllegalStateException("method and url are required");
            }
            return new HttpRequest(this);
        }
    }
}

// Usage
HttpRequest request = new HttpRequest.Builder("POST", "https://api.example.com/users")
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer token123")
    .body("{\"name\": \"John\"}")
    .timeout(5000)
    .build();

// Java SE examples: StringBuilder, Stream.Builder
// Spring: UriComponentsBuilder, MockMvcRequestBuilders
```

### Abstract Factory Pattern

```java
// Abstract products
interface Button { void render(); }
interface TextField { void render(); }
interface Checkbox { void render(); }

// Concrete products - Material Design
class MaterialButton implements Button {
    public void render() { System.out.println("Material Button"); }
}
class MaterialTextField implements TextField {
    public void render() { System.out.println("Material TextField"); }
}

// Concrete products - iOS Style
class IOSButton implements Button {
    public void render() { System.out.println("iOS Button"); }
}
class IOSTextField implements TextField {
    public void render() { System.out.println("iOS TextField"); }
}

// Abstract Factory
interface UIFactory {
    Button createButton();
    TextField createTextField();
}

// Concrete Factories
class MaterialUIFactory implements UIFactory {
    public Button createButton() { return new MaterialButton(); }
    public TextField createTextField() { return new MaterialTextField(); }
}

class IOSUIFactory implements UIFactory {
    public Button createButton() { return new IOSButton(); }
    public TextField createTextField() { return new IOSTextField(); }
}

// Usage
UIFactory factory = new MaterialUIFactory(); // or IOSUIFactory
Button btn = factory.createButton();   // Gets Material button
TextField tf = factory.createTextField(); // Gets Material text field
// All components from same family - guaranteed consistency
```

### Strategy Pattern

```java
// Strategy interface
@FunctionalInterface
public interface PaymentStrategy {
    PaymentResult pay(BigDecimal amount);
}

// Concrete strategies
public class CreditCardPayment implements PaymentStrategy {
    private final String cardNumber;
    
    public CreditCardPayment(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    @Override
    public PaymentResult pay(BigDecimal amount) {
        // Process credit card payment
        System.out.println("Paid " + amount + " via Credit Card " + 
            cardNumber.substring(cardNumber.length() - 4));
        return PaymentResult.success();
    }
}

public class PayPalPayment implements PaymentStrategy {
    private final String email;
    
    public PayPalPayment(String email) {
        this.email = email;
    }
    
    @Override
    public PaymentResult pay(BigDecimal amount) {
        System.out.println("Paid " + amount + " via PayPal (" + email + ")");
        return PaymentResult.success();
    }
}

// Context
public class OrderService {
    private PaymentStrategy paymentStrategy;
    
    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }
    
    public void checkout(Order order) {
        BigDecimal total = order.calculateTotal();
        PaymentResult result = paymentStrategy.pay(total);
        if (result.isSuccessful()) {
            order.markPaid();
        }
    }
}

// Usage
OrderService service = new OrderService();
service.setPaymentStrategy(new CreditCardPayment("4111-1111-1111-1234"));
service.checkout(order);

// With lambda (since it's a functional interface):
service.setPaymentStrategy(amount -> {
    // Custom inline strategy
    return PaymentResult.success();
});

// Spring example: Spring uses Strategy for:
// - ResourceLoader strategies
// - Transaction management strategies
// - Serialization/deserialization strategies (Jackson)
// Java SE: Comparator, java.util.function interfaces
```

### Observer Pattern

```java
import java.util.*;

// Event class
public class OrderEvent {
    private final String orderId;
    private final String eventType;
    private final LocalDateTime timestamp;
    
    public OrderEvent(String orderId, String eventType) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
    }
    // Getters...
}

// Observer interface
@FunctionalInterface
public interface OrderEventListener {
    void onEvent(OrderEvent event);
}

// Subject (Observable)
public class OrderService {
    private final Map<String, List<OrderEventListener>> listeners = new HashMap<>();
    
    public void subscribe(String eventType, OrderEventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }
    
    public void unsubscribe(String eventType, OrderEventListener listener) {
        listeners.getOrDefault(eventType, List.of()).remove(listener);
    }
    
    private void notify(OrderEvent event) {
        listeners.getOrDefault(event.getEventType(), List.of())
                 .forEach(listener -> listener.onEvent(event));
    }
    
    public void placeOrder(String orderId) {
        // Business logic...
        notify(new OrderEvent(orderId, "ORDER_PLACED"));
    }
    
    public void shipOrder(String orderId) {
        // Business logic...
        notify(new OrderEvent(orderId, "ORDER_SHIPPED"));
    }
}

// Concrete observers
public class EmailNotifier implements OrderEventListener {
    @Override
    public void onEvent(OrderEvent event) {
        System.out.println("Sending email for " + event.getEventType());
    }
}

public class InventoryUpdater implements OrderEventListener {
    @Override
    public void onEvent(OrderEvent event) {
        System.out.println("Updating inventory for " + event.getOrderId());
    }
}

// Usage
OrderService orderService = new OrderService();
orderService.subscribe("ORDER_PLACED", new EmailNotifier());
orderService.subscribe("ORDER_PLACED", new InventoryUpdater());
orderService.placeOrder("ORD-123");

// Spring equivalent: ApplicationEventPublisher + @EventListener
// Java SE: PropertyChangeListener, java.util.Observer (deprecated)
```

### Decorator Pattern

```java
// Component interface
public interface DataSource {
    void writeData(String data);
    String readData();
}

// Concrete component
public class FileDataSource implements DataSource {
    private final String filename;
    
    public FileDataSource(String filename) { this.filename = filename; }
    
    @Override
    public void writeData(String data) {
        // Write raw data to file
        System.out.println("Writing to " + filename + ": " + data);
    }
    
    @Override
    public String readData() {
        return "raw data from " + filename;
    }
}

// Base decorator
public abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrappedSource;
    
    public DataSourceDecorator(DataSource source) {
        this.wrappedSource = source;
    }
}

// Concrete decorator: Encryption
public class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        String encrypted = encrypt(data);
        wrappedSource.writeData(encrypted);
    }
    
    @Override
    public String readData() {
        return decrypt(wrappedSource.readData());
    }
    
    private String encrypt(String data) { return "ENC[" + data + "]"; }
    private String decrypt(String data) { return data.replace("ENC[", "").replace("]", ""); }
}

// Concrete decorator: Compression
public class CompressionDecorator extends DataSourceDecorator {
    public CompressionDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        String compressed = compress(data);
        wrappedSource.writeData(compressed);
    }
    
    @Override
    public String readData() {
        return decompress(wrappedSource.readData());
    }
    
    private String compress(String data) { return "ZIP[" + data + "]"; }
    private String decompress(String data) { return data.replace("ZIP[", "").replace("]", ""); }
}

// Usage - decorators can be stacked
DataSource source = new FileDataSource("data.txt");
source = new EncryptionDecorator(source);   // Add encryption
source = new CompressionDecorator(source);  // Add compression on top

source.writeData("Hello World");
// Compression → Encryption → File write

// Java SE: BufferedInputStream wrapping FileInputStream (I/O streams)
// Spring: HandlerInterceptor chain
```

### Proxy Pattern

```java
// Service interface
public interface UserService {
    User findById(long id);
    List<User> findAll();
    void delete(long id);
}

// Real service
public class UserServiceImpl implements UserService {
    @Override
    public User findById(long id) {
        // Database call
        return new User(id, "User_" + id);
    }
    
    @Override
    public List<User> findAll() {
        return List.of(new User(1, "Alice"), new User(2, "Bob"));
    }
    
    @Override
    public void delete(long id) {
        System.out.println("Deleted user " + id);
    }
}

// Proxy with caching + logging + access control
public class UserServiceProxy implements UserService {
    private final UserService realService;
    private final Map<Long, User> cache = new ConcurrentHashMap<>();
    private final String currentUserRole;
    
    public UserServiceProxy(UserService realService, String currentUserRole) {
        this.realService = realService;
        this.currentUserRole = currentUserRole;
    }
    
    @Override
    public User findById(long id) {
        // Caching proxy
        return cache.computeIfAbsent(id, key -> {
            System.out.println("[LOG] findById(" + key + ")");
            return realService.findById(key);
        });
    }
    
    @Override
    public List<User> findAll() {
        System.out.println("[LOG] findAll()");
        return realService.findAll();
    }
    
    @Override
    public void delete(long id) {
        // Access control proxy
        if (!"ADMIN".equals(currentUserRole)) {
            throw new SecurityException("Only ADMIN can delete users");
        }
        System.out.println("[LOG] delete(" + id + ") by " + currentUserRole);
        realService.delete(id);
        cache.remove(id);
    }
}

// Spring equivalent: @Transactional, @Cacheable, @PreAuthorize all use proxies
// JDK Proxy or CGLIB creates proxy objects at runtime
```

### Template Method Pattern

```java
// Abstract class defines the algorithm skeleton
public abstract class DataProcessor {
    
    // Template method - defines the algorithm
    public final void process() {
        openSource();
        String rawData = readData();
        String processedData = transform(rawData);
        writeData(processedData);
        closeSource();
        logCompletion();
    }
    
    // Steps to be implemented by subclasses
    protected abstract void openSource();
    protected abstract String readData();
    protected abstract String transform(String data);
    protected abstract void writeData(String data);
    protected abstract void closeSource();
    
    // Hook method - optional override
    protected void logCompletion() {
        System.out.println("Processing completed at " + LocalDateTime.now());
    }
}

// Concrete implementation: CSV processing
public class CsvProcessor extends DataProcessor {
    @Override protected void openSource() { System.out.println("Opening CSV file"); }
    @Override protected String readData() { return "csv,raw,data"; }
    @Override protected String transform(String data) { return data.toUpperCase(); }
    @Override protected void writeData(String data) { System.out.println("Writing: " + data); }
    @Override protected void closeSource() { System.out.println("Closing CSV"); }
}

// Concrete implementation: API processing
public class ApiProcessor extends DataProcessor {
    @Override protected void openSource() { System.out.println("Opening HTTP connection"); }
    @Override protected String readData() { return "{\"key\": \"value\"}"; }
    @Override protected String transform(String data) { return "transformed:" + data; }
    @Override protected void writeData(String data) { System.out.println("POST: " + data); }
    @Override protected void closeSource() { System.out.println("Closing connection"); }
}

// Spring examples: JdbcTemplate, RestTemplate, AbstractController
// JUnit: setUp() → test → tearDown() lifecycle
```

### Chain of Responsibility Pattern

```java
// Handler interface
public abstract class RequestHandler {
    private RequestHandler next;
    
    public RequestHandler setNext(RequestHandler handler) {
        this.next = handler;
        return handler; // For chaining
    }
    
    public void handle(HttpRequest request) {
        if (canHandle(request)) {
            doHandle(request);
        } else if (next != null) {
            next.handle(request);
        } else {
            System.out.println("No handler found for request");
        }
    }
    
    protected abstract boolean canHandle(HttpRequest request);
    protected abstract void doHandle(HttpRequest request);
}

// Concrete handlers
public class AuthenticationHandler extends RequestHandler {
    @Override
    protected boolean canHandle(HttpRequest request) {
        return true; // Always processes (filter style)
    }
    
    @Override
    protected void doHandle(HttpRequest request) {
        if (request.getHeader("Authorization") == null) {
            throw new SecurityException("Not authenticated");
        }
        System.out.println("Authentication passed");
        // Continue chain
        if (next != null) next.handle(request);
    }
}

public class RateLimitHandler extends RequestHandler {
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    
    @Override
    protected boolean canHandle(HttpRequest request) { return true; }
    
    @Override
    protected void doHandle(HttpRequest request) {
        String clientIp = request.getRemoteAddr();
        int count = requestCounts.merge(clientIp, 1, Integer::sum);
        if (count > 100) {
            throw new RuntimeException("Rate limit exceeded");
        }
        System.out.println("Rate limit check passed");
        if (next != null) next.handle(request);
    }
}

// Usage
RequestHandler chain = new AuthenticationHandler();
chain.setNext(new RateLimitHandler())
     .setNext(new LoggingHandler())
     .setNext(new BusinessHandler());

chain.handle(request);

// Spring: Filter chain in Spring Security
// Java SE: Logger handler chains
// Servlet: Filter chain (doFilter → chain.doFilter())
```

---

## Diagram

### Pattern Relationships and Spring Usage

```
Creational Patterns in Spring:
┌──────────────────────────────────────────────────────┐
│ Singleton  → @Component, @Service, @Repository       │
│ Factory    → BeanFactory, FactoryBean               │
│ Builder    → UriComponentsBuilder, ResponseEntity   │
│ Prototype  → @Scope("prototype")                    │
└──────────────────────────────────────────────────────┘

Structural Patterns in Spring:
┌──────────────────────────────────────────────────────┐
│ Proxy      → @Transactional, @Cacheable, AOP        │
│ Decorator  → BeanPostProcessor, HandlerInterceptor  │
│ Adapter    → HandlerAdapter, MessageConverter       │
│ Facade     → JdbcTemplate, RestTemplate             │
└──────────────────────────────────────────────────────┘

Behavioral Patterns in Spring:
┌──────────────────────────────────────────────────────┐
│ Strategy   → ResourceLoader, TransactionManager     │
│ Observer   → ApplicationEvent, @EventListener       │
│ Template   → JdbcTemplate, JmsTemplate              │
│ Chain      → Security Filter Chain                  │
└──────────────────────────────────────────────────────┘
```

---

## Dry Run

### Strategy Pattern Execution

```
// Setup
OrderService service = new OrderService();
service.setPaymentStrategy(new CreditCardPayment("4111-1111-1111-1234"));

// Execution: service.checkout(order)
Step 1: order.calculateTotal() → BigDecimal(99.99)
Step 2: paymentStrategy.pay(99.99)
  → CreditCardPayment.pay() is called (runtime polymorphism)
  → Processes credit card ending in 1234
  → Returns PaymentResult.success()
Step 3: result.isSuccessful() → true
Step 4: order.markPaid()

// Changing strategy at runtime:
service.setPaymentStrategy(new PayPalPayment("user@email.com"));
service.checkout(anotherOrder);
  → Now PayPalPayment.pay() is called
  → Different algorithm, same interface
```

---

## Real Project Usage

### Spring Boot Application Using Multiple Patterns

```java
// Singleton (default scope)
@Service
public class OrderService {
    
    // Dependency Injection (Factory internally)
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final ApplicationEventPublisher eventPublisher; // Observer
    
    public OrderService(OrderRepository orderRepository,
                       PaymentGateway paymentGateway,
                       ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }
    
    @Transactional // Proxy pattern (Spring creates proxy)
    public Order placeOrder(CreateOrderRequest request) {
        Order order = Order.builder() // Builder pattern
            .customerId(request.getCustomerId())
            .items(request.getItems())
            .status(OrderStatus.PENDING)
            .build();
        
        order = orderRepository.save(order);
        
        // Observer pattern: publish event, listeners react
        eventPublisher.publishEvent(new OrderPlacedEvent(order));
        
        return order;
    }
}

// Observer: Event listener
@Component
public class OrderEventHandler {
    
    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        // Send confirmation email
        // Update inventory
        // Notify warehouse
    }
}
```

---

## Interview Questions and Answers

### Q1: What is the Singleton pattern? How to break it?
**A**: Singleton ensures only one instance exists. Can be broken by: (1) Reflection — access private constructor, (2) Serialization — deserialize creates new instance (fix: readResolve()), (3) Cloning — clone() creates new instance (fix: throw CloneNotSupportedException). Enum singleton prevents all three.

### Q2: Factory vs Abstract Factory?
**A**: Factory Method creates a single type of product based on input. Abstract Factory creates families of related products that must be used together. Factory: `NotificationFactory.create("EMAIL")` → one product. Abstract Factory: `UIFactory.createButton()` + `UIFactory.createTextField()` → coherent family.

### Q3: When to use Builder pattern?
**A**: When an object has many fields (especially optional ones), when construction requires multiple steps, or when you want immutable objects with readable construction. Instead of a constructor with 10 parameters (or telescoping constructors), use a builder for clarity and safety.

### Q4: Decorator vs Proxy?
**A**: Both wrap an object, but with different intent:
- **Decorator**: Adds NEW behavior/functionality. Multiple decorators can be stacked. Client knows about decoration.
- **Proxy**: Controls ACCESS to the object (caching, security, lazy loading). Usually one proxy. Client typically unaware.

Example: BufferedInputStream (decorator) vs Spring @Transactional proxy.

### Q5: How does Spring use the Proxy pattern?
**A**: Spring creates proxies for beans annotated with `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`, or any AOP advice. It uses JDK dynamic proxy (for interfaces) or CGLIB (for concrete classes). The proxy intercepts method calls to add cross-cutting concerns (start transaction, check cache, verify permissions) before/after the actual method.

### Q6: Template Method vs Strategy?
**A**: Both vary behavior, but differently:
- **Template Method**: Uses inheritance. Defines algorithm skeleton in base class, subclasses override specific steps. Fixed algorithm structure.
- **Strategy**: Uses composition. Injects different algorithm implementations via interface. Entire algorithm is swappable at runtime.

Prefer Strategy (composition over inheritance) unless you need a fixed algorithm structure with customizable steps.

---

## Follow-up Questions and Answers

### Q: What is the difference between Adapter and Facade?
**A**: Adapter makes one interface compatible with another (1-to-1 wrapping). Facade simplifies a complex subsystem by providing a unified higher-level interface (1-to-many). Adapter changes the interface; Facade simplifies it.

### Q: What is the Prototype pattern?
**A**: Creates new objects by copying an existing prototype instance rather than calling constructors. Useful when object creation is expensive (complex initialization, database loading). Java's `Cloneable` interface supports this. Spring `@Scope("prototype")` creates a new bean instance for each request.

### Q: Real-world Chain of Responsibility examples?
**A**: Servlet Filter chain (authentication → logging → compression → servlet), Spring Security filter chain (CORS → CSRF → authentication → authorization → exception handling), Java logging handlers (ConsoleHandler → FileHandler → SocketHandler), and middleware in web frameworks.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Singleton without thread safety | Multiple instances under concurrency | Use enum or Bill Pugh approach |
| God Factory (one factory for everything) | Violates SRP, hard to maintain | Separate factories per domain |
| Overusing patterns | Unnecessary complexity | Apply only when problem matches pattern |
| Decorator with state changes | Unexpected behavior when decorators interact | Keep decorators stateless when possible |
| Strategy without interface | Can't switch algorithms | Always program to interface |
| Observer without unsubscribe | Memory leaks | Provide and use unsubscribe mechanism |

---

## Best Practices

1. **Don't force patterns** — Apply when the problem naturally fits, not to show you know them
2. **Prefer composition over inheritance** — Strategy > Template Method in most cases
3. **Use Spring's built-in patterns** — Don't reinvent Singleton, Observer, Proxy manually
4. **Keep patterns simple** — If explaining why you need the pattern takes longer than the pattern itself, reconsider
5. **Combine patterns** — Real systems use multiple patterns together (Builder + Factory, Strategy + Observer)
6. **Name classes after patterns** — `NotificationFactory`, `PaymentStrategy`, `CacheProxy` — improves readability

---

## Production Considerations

- **Singleton lifecycle**: In Spring, singleton beans are created at startup. Consider `@Lazy` for expensive singletons not always needed.
- **Factory registration**: Use service loader (SPI) or Spring's `@ConditionalOnProperty` for runtime factory selection.
- **Observer memory leaks**: Event listeners that hold references prevent garbage collection. Use `WeakReference` or explicit unsubscription.
- **Proxy overhead**: Each Spring AOP proxy adds ~1-2μs per method call. For hot paths, consider direct calls.
- **Chain ordering**: In security filter chains, order matters critically. Authentication must come before authorization.

---

## Related Topics

- [02. OOP Concepts](./02-oop-concepts.md) (Polymorphism, Composition)
- [08. Generics](./08-generics.md) (Type-safe patterns)
- [10. Java 8 Features](./10-java8-features.md) (Functional approach to Strategy, Observer)
- [25. Reflection](./25-reflection.md) (Proxy implementation)
