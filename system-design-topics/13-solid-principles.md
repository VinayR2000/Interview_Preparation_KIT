# SOLID Principles

## Why SOLID?

### Theory
- Five design principles for writing maintainable, extensible, testable object-oriented code
- Not rules but guidelines — apply with judgment
- Core goal: Manage dependencies and reduce coupling
- Key insight: Make software easy to change (because requirements ALWAYS change)

---

## S — Single Responsibility Principle (SRP)

### Definition
> A class should have only one reason to change.

### Why It Exists
- A class with multiple responsibilities is fragile — changing one responsibility might break another
- Hard to test (need to set up all responsibilities)
- Hard to name (vague names like "Manager", "Handler", "Processor")

### Code
```java
// ✗ BAD: Multiple responsibilities
public class UserService {
    public void createUser(User user) { /* DB logic */ }
    public void sendWelcomeEmail(User user) { /* Email logic */ }
    public String generateReport(List<User> users) { /* Report logic */ }
    public boolean validateUser(User user) { /* Validation logic */ }
}

// ✓ GOOD: Each class has one responsibility
public class UserRepository {
    public void save(User user) { /* Only DB operations */ }
    public Optional<User> findById(Long id) { /* DB query */ }
}

public class UserValidator {
    public ValidationResult validate(User user) { /* Only validation */ }
}

public class WelcomeEmailSender {
    public void send(User user) { /* Only email sending */ }
}

public class UserReportGenerator {
    public String generate(List<User> users) { /* Only report generation */ }
}
```

### How to Identify Violations
- Class has multiple unrelated methods
- Changes in one feature require modifying this class AND others
- Class name has "And" or is vague ("Manager", "Utils", "Helper")
- Class imports from many different domains

---

## O — Open/Closed Principle (OCP)

### Definition
> Classes should be open for extension but closed for modification.

### Why It Exists
- Modifying existing code risks breaking working functionality
- New requirements should be met by ADDING code, not CHANGING existing code
- Achieved through: interfaces, abstract classes, strategy pattern

### Code
```java
// ✗ BAD: Must modify this class for every new discount type
public class DiscountCalculator {
    public double calculate(Order order, String discountType) {
        if (discountType.equals("PERCENTAGE")) {
            return order.getTotal() * 0.1;
        } else if (discountType.equals("FLAT")) {
            return 50.0;
        } else if (discountType.equals("BUY_ONE_GET_ONE")) {
            return order.getTotal() / 2;
        }
        // Adding new type = modifying this class!
        return 0;
    }
}

// ✓ GOOD: Add new discount by creating new class (no modification)
public interface DiscountStrategy {
    double calculate(Order order);
}

public class PercentageDiscount implements DiscountStrategy {
    private final double percentage;
    public PercentageDiscount(double percentage) { this.percentage = percentage; }
    
    @Override
    public double calculate(Order order) {
        return order.getTotal() * percentage;
    }
}

public class FlatDiscount implements DiscountStrategy {
    private final double amount;
    public FlatDiscount(double amount) { this.amount = amount; }
    
    @Override
    public double calculate(Order order) {
        return Math.min(amount, order.getTotal());
    }
}

// New discount type? Just add a new class!
public class FirstOrderDiscount implements DiscountStrategy {
    @Override
    public double calculate(Order order) {
        return order.getTotal() * 0.2; // 20% for first order
    }
}

// Calculator never needs modification
public class DiscountCalculator {
    public double calculate(Order order, DiscountStrategy strategy) {
        return strategy.calculate(order);
    }
}
```

---

## L — Liskov Substitution Principle (LSP)

### Definition
> Subtypes must be substitutable for their base types without altering the correctness of the program.

### Why It Exists
- If a subclass can't fulfill the parent's contract, polymorphism breaks
- Code using the parent type will fail unexpectedly with the subclass
- Violations create fragile, error-prone code

### Code
```java
// ✗ BAD: Square violates Rectangle's contract
public class Rectangle {
    protected int width, height;
    
    public void setWidth(int w) { this.width = w; }
    public void setHeight(int h) { this.height = h; }
    public int getArea() { return width * height; }
}

public class Square extends Rectangle {
    @Override
    public void setWidth(int w) { this.width = w; this.height = w; } // Breaks expectation!
    @Override
    public void setHeight(int h) { this.width = h; this.height = h; }
}

// This breaks:
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(3);
assert r.getArea() == 15; // FAILS! Area is 9 because Square changed both

// ✓ GOOD: Separate abstractions
public interface Shape {
    int getArea();
}

public class Rectangle implements Shape {
    private final int width, height;
    public Rectangle(int w, int h) { this.width = w; this.height = h; }
    public int getArea() { return width * height; }
}

public class Square implements Shape {
    private final int side;
    public Square(int side) { this.side = side; }
    public int getArea() { return side * side; }
}
```

### LSP Rules
- Preconditions cannot be strengthened in subclass
- Postconditions cannot be weakened in subclass
- Invariants of parent must be maintained
- History constraint: Subclass shouldn't allow state that parent wouldn't

### Common Violations
| Violation | Problem |
|-----------|---------|
| `Stack extends ArrayList` | Stack doesn't support random access by index |
| `Square extends Rectangle` | Square can't have independent width/height |
| Throwing `UnsupportedOperationException` | Subclass can't fulfill contract |
| `ReadOnlyCollection extends Collection` | Can't support add/remove |

---

## I — Interface Segregation Principle (ISP)

### Definition
> Clients should not be forced to depend on interfaces they do not use.

### Why It Exists
- Fat interfaces force implementors to write empty/throwing methods
- Changes to unused methods still affect all implementors
- Violates SRP at the interface level

### Code
```java
// ✗ BAD: Fat interface forces unnecessary implementation
public interface Worker {
    void work();
    void eat();        // Robot doesn't eat!
    void sleep();      // Robot doesn't sleep!
    void attendMeeting();
}

public class Robot implements Worker {
    public void work() { /* OK */ }
    public void eat() { throw new UnsupportedOperationException(); } // Forced!
    public void sleep() { throw new UnsupportedOperationException(); } // Forced!
    public void attendMeeting() { throw new UnsupportedOperationException(); }
}

// ✓ GOOD: Segregated interfaces
public interface Workable {
    void work();
}

public interface Feedable {
    void eat();
}

public interface Sleepable {
    void sleep();
}

public class HumanWorker implements Workable, Feedable, Sleepable {
    public void work() { /* ... */ }
    public void eat() { /* ... */ }
    public void sleep() { /* ... */ }
}

public class Robot implements Workable {
    public void work() { /* ... */ }
    // Only implements what it actually needs!
}
```

### Real-World Example
```java
// ✗ BAD: One repository interface for everything
public interface UserRepository {
    User findById(Long id);
    List<User> findAll();
    void save(User user);
    void delete(User user);
    void bulkImport(List<User> users);
    UserReport generateReport();
}

// ✓ GOOD: Segregated by use case
public interface UserReader {
    User findById(Long id);
    List<User> findAll();
}

public interface UserWriter {
    void save(User user);
    void delete(User user);
}

public interface UserBulkOperations {
    void bulkImport(List<User> users);
}
```

---

## D — Dependency Inversion Principle (DIP)

### Definition
> High-level modules should not depend on low-level modules. Both should depend on abstractions.
> Abstractions should not depend on details. Details should depend on abstractions.

### Why It Exists
- Direct dependency on concrete classes creates tight coupling
- Can't swap implementations (for testing, or different environments)
- Changes in low-level module ripple up to high-level module

### Diagram
```
✗ BAD (High depends on Low):
┌──────────────────┐       ┌──────────────────┐
│ OrderService     │──────→│ MySQLDatabase    │
│ (High-level)     │       │ (Low-level)      │
└──────────────────┘       └──────────────────┘

✓ GOOD (Both depend on Abstraction):
┌──────────────────┐       ┌──────────────────┐
│ OrderService     │──────→│ «interface»      │
│ (High-level)     │       │ OrderRepository  │
└──────────────────┘       └────────▲─────────┘
                                    │ implements
                           ┌────────┴─────────┐
                           │MySQLOrderRepo    │
                           │(Low-level detail)│
                           └──────────────────┘
```

### Code
```java
// ✗ BAD: High-level depends on low-level directly
public class NotificationService {
    private final SmtpEmailClient emailClient; // Concrete!
    
    public NotificationService() {
        this.emailClient = new SmtpEmailClient(); // Creates its own dependency!
    }
    
    public void notify(User user, String message) {
        emailClient.send(user.getEmail(), message);
    }
}
// Problem: Can't switch to SendGrid, can't test without SMTP server

// ✓ GOOD: Depend on abstraction, inject dependency
public interface MessageSender {
    void send(String recipient, String message);
}

public class NotificationService {
    private final MessageSender sender; // Abstraction!
    
    public NotificationService(MessageSender sender) { // Injected!
        this.sender = sender;
    }
    
    public void notify(User user, String message) {
        sender.send(user.getEmail(), message);
    }
}

// Can swap implementations:
// Production: new NotificationService(new SmtpEmailSender())
// Testing:    new NotificationService(new MockEmailSender())
// Future:     new NotificationService(new SendGridSender())
```

---

## SOLID Summary Table

| Principle | One-Liner | Violation Symptom |
|-----------|-----------|-------------------|
| SRP | One class, one reason to change | Class doing too many things |
| OCP | Extend without modifying | if/else chains growing with new requirements |
| LSP | Subclass works wherever parent works | `UnsupportedOperationException` in override |
| ISP | Small, focused interfaces | Empty method implementations |
| DIP | Depend on abstractions, not concretions | `new` inside business logic, can't unit test |

---

## Interview Questions

**Q: Explain SRP with a real project example.**
> In an e-commerce system, separate OrderService (business logic) from OrderRepository (DB access) from OrderNotifier (sending emails) from OrderValidator (validation rules). If notification logic changes, only OrderNotifier changes. If we switch databases, only OrderRepository changes.

**Q: How does OCP relate to the Strategy pattern?**
> Strategy pattern IS the OCP implementation. The context class (closed for modification) delegates behavior to a strategy interface (open for extension). New strategies can be added without changing the context. Example: Payment processing — add new payment methods by implementing PaymentStrategy, never modify PaymentProcessor.

**Q: Give a real-world LSP violation.**
> Java's `Stack extends Vector`. Stack should only support push/pop/peek. But since it extends Vector, you can call `get(index)`, `remove(index)`, `add(index, element)` — operations that violate the LIFO contract. Users expecting a Stack get something that allows arbitrary access.

**Q: How do you apply DIP in Spring Boot?**
> Spring's DI container IS the DIP implementation. You define interfaces (abstractions), implement them in concrete classes, and let Spring inject the correct implementation. `@Service` classes depend on `@Repository` interfaces, not concrete JPA implementations. This enables: easy testing with `@MockBean`, swapping implementations via `@Profile`.

**Q: When is it OK to violate SOLID?**
> SOLID are guidelines, not laws. Simple CRUD apps don't need elaborate abstractions. Over-engineering a simple feature with multiple interfaces and strategies adds complexity without benefit. Apply SOLID when: code needs to change/extend frequently, multiple implementations exist, testability matters, team is large.

---

## Common Mistakes
- Over-applying SOLID to simple code (creates unnecessary abstraction)
- Applying OCP everywhere (not everything needs extension points)
- Creating too many tiny interfaces (ISP taken to extreme)
- Confusing DIP with dependency injection (DIP is the principle, DI is one technique)
- Using inheritance to share code (violates LSP often)

---

## Best Practices
- Apply SOLID at boundaries (where change is likely)
- Start simple, refactor toward SOLID when complexity demands it
- Use DIP at module boundaries (between layers, between services)
- ISP: If an interface has >5 methods, consider splitting
- OCP: Use when you see yourself repeatedly modifying the same class for new features
- SRP: If you can't describe a class in one sentence without "and", split it

---

## Production Considerations
- SOLID makes code testable → better test coverage → fewer production bugs
- OCP with strategy/plugin patterns enables feature flags
- DIP enables environment-specific implementations (mock payment in staging)
- ISP reduces deployment coupling in microservices (shared interface jars)

---

## Related Topics
- Design Patterns (implement SOLID in practice)
- Dependency Injection (Spring, Guice)
- Clean Architecture
- Refactoring techniques
