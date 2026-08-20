# Design Patterns — Creational

## Singleton

### Theory
- Ensures a class has only ONE instance and provides global access to it
- Used for: Configuration, Connection pools, Caches, Loggers

### Code
```java
// Thread-safe Singleton (Bill Pugh approach — recommended)
public class DatabaseConnectionPool {
    
    private DatabaseConnectionPool() {
        // Initialize pool
    }
    
    private static class Holder {
        private static final DatabaseConnectionPool INSTANCE = new DatabaseConnectionPool();
    }
    
    public static DatabaseConnectionPool getInstance() {
        return Holder.INSTANCE; // Lazy, thread-safe (class loading guarantee)
    }
    
    public Connection getConnection() { /* ... */ }
}

// Enum Singleton (simplest, handles serialization)
public enum AppConfig {
    INSTANCE;
    
    private final Properties properties = new Properties();
    
    AppConfig() {
        // Load configuration
    }
    
    public String get(String key) {
        return properties.getProperty(key);
    }
}
```

### When to Use
- Database connection pool
- Application configuration
- Thread pool manager
- Logger instance

### Problems with Singleton
- Hard to unit test (global state)
- Hides dependencies
- Violates SRP (manages its own lifecycle + business logic)
- **Prefer DI container** (Spring @Scope("singleton")) over manual singleton

---

## Factory Method

### Theory
- Defines an interface for creating objects but lets subclasses decide WHICH class to instantiate
- Decouples object creation from usage
- Eliminates `new ConcreteClass()` scattered in business logic

### Code
```java
// Without Factory: Client coupled to all concrete types
public class NotificationService {
    public void send(String type, String message) {
        if (type.equals("EMAIL")) {
            EmailSender sender = new EmailSender(); // Tight coupling!
            sender.send(message);
        } else if (type.equals("SMS")) {
            SMSSender sender = new SMSSender(); // More coupling!
            sender.send(message);
        }
    }
}

// With Factory:
public interface Notification {
    void send(String message);
}

public class EmailNotification implements Notification {
    public void send(String message) { /* SMTP logic */ }
}

public class SMSNotification implements Notification {
    public void send(String message) { /* Twilio logic */ }
}

public class PushNotification implements Notification {
    public void send(String message) { /* Firebase logic */ }
}

// Factory
public class NotificationFactory {
    public static Notification create(String channel) {
        return switch (channel) {
            case "EMAIL" -> new EmailNotification();
            case "SMS" -> new SMSNotification();
            case "PUSH" -> new PushNotification();
            default -> throw new IllegalArgumentException("Unknown channel: " + channel);
        };
    }
}

// Usage: Adding new channel requires only new class + factory entry
Notification notification = NotificationFactory.create("EMAIL");
notification.send("Hello!");
```

### When to Use
- Object type determined at runtime
- Complex object creation logic
- Need to decouple creation from usage
- Multiple implementations of an interface

---

## Abstract Factory

### Theory
- Factory of factories
- Creates families of related objects without specifying concrete classes
- Ensures objects from same family are used together

### Code
```java
// Theme factory — creates consistent UI component families
public interface UIFactory {
    Button createButton();
    TextField createTextField();
    Checkbox createCheckbox();
}

public class DarkThemeFactory implements UIFactory {
    public Button createButton() { return new DarkButton(); }
    public TextField createTextField() { return new DarkTextField(); }
    public Checkbox createCheckbox() { return new DarkCheckbox(); }
}

public class LightThemeFactory implements UIFactory {
    public Button createButton() { return new LightButton(); }
    public TextField createTextField() { return new LightTextField(); }
    public Checkbox createCheckbox() { return new LightCheckbox(); }
}

// Usage: Entire UI is consistent
UIFactory factory = isDarkMode ? new DarkThemeFactory() : new LightThemeFactory();
Button btn = factory.createButton();     // Matches theme
TextField tf = factory.createTextField(); // Matches theme
```

### Factory Method vs Abstract Factory
| Factory Method | Abstract Factory |
|---------------|-----------------|
| Creates ONE product | Creates FAMILY of products |
| Single method | Multiple creation methods |
| Subclass decides type | Family consistency guaranteed |

---

## Builder

### Theory
- Constructs complex objects step by step
- Separates construction from representation
- Useful when object has many optional parameters

### Code
```java
// Without Builder: Telescoping constructors
new Pizza(Size.LARGE, true, false, true, false, true, "thin"); // What do these mean?!

// With Builder:
public class Pizza {
    private final Size size;
    private final boolean cheese;
    private final boolean pepperoni;
    private final boolean mushrooms;
    private final String crust;
    
    private Pizza(Builder builder) {
        this.size = builder.size;
        this.cheese = builder.cheese;
        this.pepperoni = builder.pepperoni;
        this.mushrooms = builder.mushrooms;
        this.crust = builder.crust;
    }
    
    public static class Builder {
        // Required
        private final Size size;
        
        // Optional (defaults)
        private boolean cheese = false;
        private boolean pepperoni = false;
        private boolean mushrooms = false;
        private String crust = "regular";
        
        public Builder(Size size) {
            this.size = size;
        }
        
        public Builder cheese() { this.cheese = true; return this; }
        public Builder pepperoni() { this.pepperoni = true; return this; }
        public Builder mushrooms() { this.mushrooms = true; return this; }
        public Builder crust(String crust) { this.crust = crust; return this; }
        
        public Pizza build() {
            validate();
            return new Pizza(this);
        }
        
        private void validate() {
            if (size == null) throw new IllegalStateException("Size is required");
        }
    }
}

// Usage: Readable, immutable
Pizza pizza = new Pizza.Builder(Size.LARGE)
    .cheese()
    .pepperoni()
    .crust("thin")
    .build();
```

### When to Use
- Object has >4 constructor parameters
- Many optional parameters
- Object should be immutable after construction
- Construction has multiple steps or variations
- Real examples: StringBuilder, HttpClient.Builder, Lombok @Builder

---

## Prototype

### Theory
- Create new objects by cloning existing ones
- Useful when object creation is expensive
- Avoids repeating complex initialization

### Code
```java
public abstract class Shape implements Cloneable {
    protected String color;
    protected int x, y;
    
    public abstract Shape clone();
    
    // Copy constructor approach (preferred over Cloneable)
    protected Shape(Shape source) {
        this.color = source.color;
        this.x = source.x;
        this.y = source.y;
    }
}

public class Circle extends Shape {
    private int radius;
    
    public Circle(Circle source) {
        super(source);
        this.radius = source.radius;
    }
    
    @Override
    public Shape clone() {
        return new Circle(this);
    }
}

// Prototype Registry
public class ShapeCache {
    private static Map<String, Shape> cache = new HashMap<>();
    
    static {
        cache.put("red-circle", new Circle(10, "red"));
        cache.put("blue-rectangle", new Rectangle(20, 30, "blue"));
    }
    
    public static Shape get(String key) {
        return cache.get(key).clone(); // Return copy, not original
    }
}

// Usage
Shape circle1 = ShapeCache.get("red-circle");
Shape circle2 = ShapeCache.get("red-circle"); // Different instance, same properties
```

### When to Use
- Object creation is expensive (DB queries, network calls, complex calculations)
- Need many similar objects with slight variations
- Want to avoid subclassing just for initialization differences
- Real examples: Object pooling, game object spawning

---

## Comparison Table

| Pattern | Intent | Complexity | Example in Java |
|---------|--------|-----------|-----------------|
| Singleton | One instance globally | Low | Runtime, Logger |
| Factory Method | Delegate creation to factory | Medium | Calendar.getInstance() |
| Abstract Factory | Create families of objects | High | UI toolkit factories |
| Builder | Step-by-step construction | Medium | StringBuilder, HttpClient |
| Prototype | Clone existing object | Medium | Object.clone() |

---

## Interview Questions

**Q: Why is Singleton considered an anti-pattern by some?**
> 1. Global state makes testing difficult (hard to mock)
> 2. Hides dependencies (not visible in constructor)
> 3. Violates SRP (manages own lifecycle + business logic)
> 4. Thread safety concerns with lazy initialization
> Better alternative: DI framework with singleton scope (Spring @Scope("singleton"))

**Q: When would you use Builder over Factory?**
> Builder: Complex object construction with many parameters, step-by-step building, immutable objects.
> Factory: Choosing WHICH class to instantiate (polymorphism), simple object creation, type determined at runtime.
> Think: Builder answers "how to create", Factory answers "what to create".

**Q: How would you implement a thread-safe Singleton in Java?**
> Options ranked:
> 1. Enum singleton (simplest, handles serialization)
> 2. Bill Pugh (static inner class holder — lazy, thread-safe via class loading)
> 3. Double-checked locking with volatile (legacy, error-prone)
> 4. Eager initialization (static final field — if always needed)
> In practice: Use Spring @Component (framework manages singleton lifecycle)

**Q: How does Abstract Factory relate to OCP?**
> Adding a new product family (e.g., new theme) means adding a new factory class — no modification to existing code. The client works with the UIFactory interface and never changes. OCP achieved: extend by adding new factory implementations.

**Q: Explain the Prototype pattern with a real use case.**
> Game development: Creating enemies. A "base enemy" prototype is configured with stats, abilities, and appearance. Each new enemy on screen is cloned from the prototype, then slightly customized (position, difficulty scaling). Avoids repeating expensive initialization (loading assets, calculating stats).

---

## Common Mistakes
- Using Singleton for everything (makes testing a nightmare)
- Builder without validation (invalid objects slip through)
- Factory with too many if/else (use registry/map instead)
- Shallow clone when deep clone is needed (shared mutable references)
- Abstract Factory when simple Factory Method suffices (over-engineering)

---

## Best Practices
- Prefer DI frameworks over manual Singleton
- Use Builder for immutable objects with many properties
- Factory Method + Strategy = powerful extension point
- Make Builders validate before build()
- Use copy constructors over Cloneable (Cloneable is broken in Java)
- Combine patterns: Factory creates Builders, Builder uses Prototype for defaults

---

## Real Project Usage
- **Singleton**: Spring beans (default scope), connection pools (HikariCP)
- **Factory**: Spring BeanFactory, DriverManager.getConnection()
- **Builder**: Lombok @Builder, OkHttp Request.Builder, Stream API
- **Prototype**: Spring @Scope("prototype"), object pooling
- **Abstract Factory**: JDBC (each driver = factory for Connection, Statement, ResultSet)

---

## Related Topics
- Structural Design Patterns
- Behavioral Design Patterns
- Dependency Injection
- SOLID Principles (patterns implement SOLID)
