# Design Patterns — Structural

## Adapter

### Theory
- Converts the interface of a class into another interface clients expect
- Allows incompatible interfaces to work together
- "Wrapper" that translates one interface to another

### Diagram
```
┌──────────┐       ┌───────────┐       ┌──────────────────┐
│  Client  │──────→│  Adapter  │──────→│ Adaptee (legacy) │
│(expects  │       │(translates│       │ (incompatible    │
│ Target)  │       │ Target→   │       │  interface)      │
│          │       │  Adaptee) │       │                  │
└──────────┘       └───────────┘       └──────────────────┘
```

### Code
```java
// Existing (legacy) payment gateway with incompatible interface
public class LegacyPaymentGateway {
    public boolean makePayment(String cardNumber, int amountInCents, String curr) {
        // Legacy API uses cents and different parameter order
        return true;
    }
}

// Our system expects this interface
public interface PaymentProcessor {
    PaymentResult processPayment(PaymentRequest request);
}

// Adapter bridges the gap
public class LegacyPaymentAdapter implements PaymentProcessor {
    private final LegacyPaymentGateway legacyGateway;
    
    public LegacyPaymentAdapter(LegacyPaymentGateway legacyGateway) {
        this.legacyGateway = legacyGateway;
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Translate our interface to legacy interface
        int amountInCents = (int) (request.getAmount() * 100);
        boolean success = legacyGateway.makePayment(
            request.getCardNumber(),
            amountInCents,
            request.getCurrency()
        );
        return new PaymentResult(success, success ? "OK" : "FAILED");
    }
}
```

### When to Use
- Integrating with third-party libraries/APIs
- Working with legacy code that can't be modified
- Unifying multiple similar but incompatible interfaces
- Real: JDBC drivers (adapt DB protocol to JDBC interface), SLF4J (adapts logging frameworks)

---

## Decorator

### Theory
- Adds behavior to objects dynamically without modifying their class
- Wraps original object and delegates, adding behavior before/after
- Alternative to subclassing for extending functionality
- Can stack multiple decorators

### Diagram
```
┌──────────────────────────────────────────────────────────┐
│ EncryptionDecorator                                       │
│  ┌──────────────────────────────────────────────────┐    │
│  │ CompressionDecorator                              │    │
│  │  ┌──────────────────────────────────────────┐    │    │
│  │  │ LoggingDecorator                          │    │    │
│  │  │  ┌──────────────────────────────────┐    │    │    │
│  │  │  │  BaseDataSource (actual work)    │    │    │    │
│  │  │  └──────────────────────────────────┘    │    │    │
│  │  └──────────────────────────────────────────┘    │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
```

### Code
```java
public interface DataSource {
    void writeData(String data);
    String readData();
}

public class FileDataSource implements DataSource {
    private final String filename;
    
    public void writeData(String data) { /* write to file */ }
    public String readData() { /* read from file */ }
}

// Base decorator
public abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrappee;
    
    public DataSourceDecorator(DataSource source) {
        this.wrappee = source;
    }
    
    @Override
    public void writeData(String data) { wrappee.writeData(data); }
    @Override
    public String readData() { return wrappee.readData(); }
}

// Concrete decorators
public class CompressionDecorator extends DataSourceDecorator {
    public CompressionDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        String compressed = compress(data); // Add behavior
        super.writeData(compressed);        // Delegate
    }
    
    @Override
    public String readData() {
        return decompress(super.readData()); // Add behavior after
    }
}

public class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) { super(source); }
    
    @Override
    public void writeData(String data) {
        String encrypted = encrypt(data);
        super.writeData(encrypted);
    }
}

// Usage: Stack decorators
DataSource source = new EncryptionDecorator(
    new CompressionDecorator(
        new FileDataSource("data.txt")
    )
);
source.writeData("secret"); // Compressed → Encrypted → Written to file
```

### When to Use
- Add responsibilities dynamically at runtime
- Combination of behaviors (compress+encrypt, log+cache+retry)
- Alternative to explosion of subclasses
- Real: Java I/O (BufferedInputStream(FileInputStream)), Spring's AOP proxy

---

## Facade

### Theory
- Provides a simplified interface to a complex subsystem
- Hides complexity behind a single, clean entry point
- Doesn't prevent access to subsystem if needed

### Code
```java
// Complex subsystem
class VideoDecoder { void decode(File file) { /* ... */ } }
class AudioExtractor { void extract(File file) { /* ... */ } }
class SubtitleGenerator { void generate(File file) { /* ... */ } }
class ThumbnailCreator { void create(File file, int timestamp) { /* ... */ } }
class CDNUploader { void upload(File file, String path) { /* ... */ } }
class MetadataService { void index(String videoId, Map<String, String> meta) { /* ... */ } }

// Facade: Simple interface for "upload video"
public class VideoUploadFacade {
    private final VideoDecoder decoder;
    private final AudioExtractor audioExtractor;
    private final SubtitleGenerator subtitleGenerator;
    private final ThumbnailCreator thumbnailCreator;
    private final CDNUploader cdnUploader;
    private final MetadataService metadataService;
    
    public VideoUploadResult upload(File videoFile, VideoMetadata metadata) {
        // Orchestrates complex process behind simple method
        decoder.decode(videoFile);
        audioExtractor.extract(videoFile);
        subtitleGenerator.generate(videoFile);
        thumbnailCreator.create(videoFile, metadata.getThumbnailTime());
        cdnUploader.upload(videoFile, "/videos/" + metadata.getId());
        metadataService.index(metadata.getId(), metadata.toMap());
        
        return new VideoUploadResult(metadata.getId(), Status.SUCCESS);
    }
}

// Client: Simple!
facade.upload(videoFile, metadata);
```

### When to Use
- Complex subsystem with many classes and interactions
- Need a simple entry point for common operations
- Layer between client and complex library
- Real: JDBC (hides socket/protocol complexity), Spring's JdbcTemplate, SLF4J

---

## Proxy

### Theory
- Controls access to another object
- Same interface as the real object
- Intercepts requests and adds behavior (access control, caching, logging, lazy loading)

### Types of Proxy
| Type | Purpose | Example |
|------|---------|---------|
| Virtual Proxy | Lazy initialization | Load image only when displayed |
| Protection Proxy | Access control | Check permissions before method |
| Caching Proxy | Cache results | Cache DB query results |
| Logging Proxy | Log method calls | Audit trail |
| Remote Proxy | Remote object access | RMI, gRPC stub |

### Code
```java
public interface UserService {
    User getUser(Long id);
    void updateUser(User user);
}

public class UserServiceImpl implements UserService {
    public User getUser(Long id) { /* DB query */ }
    public void updateUser(User user) { /* DB update */ }
}

// Caching + Logging Proxy
public class UserServiceProxy implements UserService {
    private final UserService realService;
    private final Cache cache;
    private final Logger log;
    
    @Override
    public User getUser(Long id) {
        log.info("getUser called for id: {}", id);
        
        // Caching behavior
        User cached = cache.get("user:" + id);
        if (cached != null) return cached;
        
        User user = realService.getUser(id);
        cache.put("user:" + id, user);
        return user;
    }
    
    @Override
    public void updateUser(User user) {
        log.info("updateUser called for: {}", user.getId());
        realService.updateUser(user);
        cache.evict("user:" + user.getId()); // Invalidate cache
    }
}
```

### When to Use
- Lazy loading (expensive object creation deferred)
- Access control (security checks)
- Caching (transparent caching layer)
- Logging/monitoring (AOP-like behavior)
- Real: Spring AOP Proxies, JPA lazy loading, Hibernate proxy for entities

---

## Composite

### Theory
- Composes objects into tree structures to represent part-whole hierarchies
- Clients treat individual objects and compositions uniformly
- "Container" and "Leaf" share the same interface

### Code
```java
// File system example
public interface FileSystemItem {
    String getName();
    long getSize();
    void display(String indent);
}

public class File implements FileSystemItem {
    private final String name;
    private final long size;
    
    public String getName() { return name; }
    public long getSize() { return size; }
    public void display(String indent) {
        System.out.println(indent + "📄 " + name + " (" + size + " bytes)");
    }
}

public class Directory implements FileSystemItem {
    private final String name;
    private final List<FileSystemItem> children = new ArrayList<>();
    
    public void add(FileSystemItem item) { children.add(item); }
    public void remove(FileSystemItem item) { children.remove(item); }
    
    public String getName() { return name; }
    
    public long getSize() {
        return children.stream()
            .mapToLong(FileSystemItem::getSize)
            .sum(); // Recursive calculation
    }
    
    public void display(String indent) {
        System.out.println(indent + "📁 " + name);
        children.forEach(child -> child.display(indent + "  "));
    }
}

// Usage: Uniform treatment
Directory root = new Directory("src");
root.add(new File("Main.java", 1024));
Directory utils = new Directory("utils");
utils.add(new File("Helper.java", 512));
root.add(utils);

root.getSize(); // 1536 (recursive sum)
root.display(""); // Tree display
```

### When to Use
- Tree structures (file systems, UI components, organizational charts)
- Need to treat leaf and container objects the same way
- Recursive composition
- Real: Swing components, React component tree, XML/JSON parsing

---

## Bridge

### Theory
- Separates abstraction from implementation so both can vary independently
- Prevents "cartesian product" explosion of subclasses
- "Two dimensions of variation"

### Code
```java
// Problem: Without Bridge — class explosion
// RemoteControl × Device = many classes
// BasicRemoteTV, BasicRemoteRadio, AdvancedRemoteTV, AdvancedRemoteRadio...

// Solution: Bridge separates the two hierarchies
public interface Device {  // Implementation hierarchy
    void turnOn();
    void turnOff();
    void setVolume(int volume);
    int getVolume();
}

public class TV implements Device { /* TV-specific implementation */ }
public class Radio implements Device { /* Radio-specific implementation */ }
public class SmartSpeaker implements Device { /* Speaker implementation */ }

public abstract class RemoteControl {  // Abstraction hierarchy
    protected Device device;  // Bridge to implementation
    
    public RemoteControl(Device device) { this.device = device; }
    
    public void togglePower() { /* ... */ }
    public void volumeUp() { device.setVolume(device.getVolume() + 1); }
    public void volumeDown() { device.setVolume(device.getVolume() - 1); }
}

public class AdvancedRemote extends RemoteControl {
    public AdvancedRemote(Device device) { super(device); }
    
    public void mute() { device.setVolume(0); }
    public void setChannel(int channel) { /* ... */ }
}

// Usage: Any remote works with any device
RemoteControl remote = new AdvancedRemote(new TV());
remote.volumeUp();
```

### When to Use
- Two independent dimensions that need to vary
- Prevent subclass explosion (M × N combinations)
- Runtime binding of implementation
- Real: JDBC (DriverManager is abstraction, JDBC Driver is implementation)

---

## Comparison Table

| Pattern | Purpose | Key Relationship |
|---------|---------|-----------------|
| Adapter | Make incompatible interfaces work together | Wraps ONE object, changes interface |
| Decorator | Add behavior dynamically | Wraps ONE object, same interface |
| Facade | Simplify complex subsystem | Wraps MANY objects |
| Proxy | Control access to object | Same interface, controls access |
| Composite | Treat individual/group uniformly | Tree structure |
| Bridge | Separate abstraction from implementation | Two hierarchies connected |

---

## Interview Questions

**Q: What's the difference between Adapter and Decorator?**
> Adapter changes the interface (converts one interface to another). Decorator keeps the same interface and adds behavior. Adapter is used for compatibility. Decorator is used for enhancement. An adapter wraps an incompatible object, a decorator wraps a compatible object and enhances it.

**Q: When would you use Proxy vs Decorator?**
> Proxy controls access (security, lazy loading, caching — the client may not know it's using a proxy). Decorator adds visible functionality (compression, encryption — behavior is accumulated). Key difference: Proxy manages lifecycle/access, Decorator adds responsibilities.

**Q: How is the Facade pattern used in microservices?**
> API Gateway IS a facade — provides simple, unified interface for clients while hiding the complexity of multiple microservices behind it. Also: BFF (Backend for Frontend) pattern is a specialized facade that tailors the API for specific client types (web, mobile).

**Q: Give a real-world example of the Composite pattern.**
> E-commerce categories: Category can contain products (leaf) or sub-categories (composite). "Electronics" contains "Laptops" (sub-category) and "USB Cable" (product). `getItemCount()` recursively counts all products in all sub-categories. UI components work the same way — Panel contains Buttons and other Panels.

---

## Common Mistakes
- Confusing Adapter with Facade (adapter wraps one object, facade wraps subsystem)
- Over-decorating (too many layers make debugging hard)
- Proxy that does too much (should focus on ONE concern)
- Using Bridge when simple inheritance suffices
- Making Composite operations too generic (not all operations make sense for leaf + container)

---

## Best Practices
- Adapter: Use when integrating third-party code you can't modify
- Decorator: Prefer over inheritance for behavior extension
- Facade: Create for every complex subsystem interaction
- Proxy: Use for cross-cutting concerns (caching, logging, security)
- Composite: Ensure leaf and composite share meaningful interface
- Bridge: Use when you identify two independent dimensions of variation

---

## Related Topics
- Behavioral Design Patterns
- Spring AOP (uses Proxy pattern)
- Java I/O (uses Decorator heavily)
- API Gateway (Facade in microservices)
