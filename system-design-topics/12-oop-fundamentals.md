# OOP Fundamentals for LLD

## Encapsulation

### Theory
- Bundling data (fields) and methods that operate on that data into a single unit (class)
- Hiding internal state and requiring all interaction through well-defined interfaces
- Control access via access modifiers (private, protected, public)

### Code
```java
// Bad: Exposed internals
public class BankAccount {
    public double balance; // Anyone can modify directly!
}

// Good: Encapsulated
public class BankAccount {
    private double balance;
    
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balance += amount;
    }
    
    public void withdraw(double amount) {
        if (amount > balance) throw new InsufficientFundsException();
        this.balance -= amount;
    }
    
    public double getBalance() {
        return this.balance; // Read-only access
    }
}
```

### Why It Matters in LLD
- Prevents invalid state (negative balance, null fields)
- Changes to internal implementation don't affect callers
- Enforces business rules at the boundary

---

## Abstraction

### Theory
- Showing only essential features, hiding implementation details
- Users of a class don't need to know HOW it works, only WHAT it does
- Achieved through: abstract classes, interfaces

### Code
```java
// Abstraction: Client only sees "send notification"
// Doesn't care HOW it's sent
public interface NotificationSender {
    void send(String recipient, String message);
}

public class EmailSender implements NotificationSender {
    @Override
    public void send(String recipient, String message) {
        // Complex SMTP logic hidden
        smtpClient.connect(host, port);
        smtpClient.authenticate(user, pass);
        smtpClient.sendMail(recipient, message);
    }
}

public class SMSSender implements NotificationSender {
    @Override
    public void send(String recipient, String message) {
        // Complex SMS gateway logic hidden
        twilioClient.messages().create(recipient, message);
    }
}
```

---

## Inheritance

### Theory
- Creating new classes based on existing classes
- Child class inherits fields and methods from parent
- Represents "IS-A" relationship
- Enables code reuse and polymorphism

### Code
```java
public abstract class Vehicle {
    protected String licensePlate;
    protected VehicleType type;
    
    public abstract double calculateParkingFee(int hours);
}

public class Car extends Vehicle {
    @Override
    public double calculateParkingFee(int hours) {
        return hours * 20.0; // $20/hour for cars
    }
}

public class Truck extends Vehicle {
    @Override
    public double calculateParkingFee(int hours) {
        return hours * 40.0; // $40/hour for trucks
    }
}
```

### When NOT to Use Inheritance
- Don't use for code reuse alone (use composition)
- Avoid deep hierarchies (>3 levels)
- Don't inherit if relationship isn't truly "IS-A"

---

## Polymorphism

### Theory
- Same interface, different behavior based on actual type
- **Compile-time** (method overloading): Same method name, different parameters
- **Runtime** (method overriding): Parent reference, child implementation

### Code
```java
// Runtime polymorphism — key for LLD flexibility
public class PaymentProcessor {
    public void processPayment(PaymentMethod method, double amount) {
        // Same method call, different behavior at runtime
        method.charge(amount); // Could be CreditCard, UPI, Wallet
    }
}

public interface PaymentMethod {
    void charge(double amount);
    void refund(double amount);
}

public class CreditCard implements PaymentMethod {
    public void charge(double amount) { /* Stripe API */ }
    public void refund(double amount) { /* Stripe refund */ }
}

public class UPI implements PaymentMethod {
    public void charge(double amount) { /* UPI gateway */ }
    public void refund(double amount) { /* UPI refund */ }
}
```

---

## Composition

### Theory
- "HAS-A" relationship (Car HAS-A Engine)
- Object contains other objects as fields
- **Favor composition over inheritance** — more flexible, less coupling
- Can change behavior at runtime by swapping composed objects

### Composition vs Inheritance

| Aspect | Inheritance | Composition |
|--------|-------------|-------------|
| Relationship | IS-A | HAS-A |
| Coupling | Tight (child depends on parent) | Loose (can swap components) |
| Flexibility | Static (compile-time) | Dynamic (runtime) |
| Reuse | All of parent (even unwanted) | Only what's needed |
| Testing | Harder (can't mock parent) | Easier (mock components) |

### Code
```java
// Inheritance approach (rigid)
class FlyingCar extends Car { /* inherits all Car + adds flying */ }
class SwimmingCar extends Car { /* inherits all Car + adds swimming */ }
// FlyingSwimmingCar?? Multiple inheritance problem!

// Composition approach (flexible)
class Car {
    private Engine engine;
    private MovementStrategy movement; // Can be: Drive, Fly, Swim
    
    public Car(Engine engine, MovementStrategy movement) {
        this.engine = engine;
        this.movement = movement;
    }
    
    public void move() {
        movement.execute(engine);
    }
}
// Can create any combination at runtime!
```

---

## Association, Aggregation, Dependency

### Association
- General relationship between classes ("uses", "knows about")
- Both objects have independent lifecycle
```java
// Teacher teaches Students (many-to-many)
class Teacher {
    private List<Student> students; // Association
}
```

### Aggregation (Weak HAS-A)
- Special association: "part-of" but parts can exist independently
- Container destroyed → parts survive
```java
// Department has Employees, but employees exist without department
class Department {
    private List<Employee> employees; // Aggregation
    // If department deleted, employees still exist
}
```

### Composition (Strong HAS-A)
- Parts cannot exist without the container
- Container destroyed → parts destroyed
```java
// House has Rooms, rooms don't exist without house
class House {
    private List<Room> rooms; // Composition
    
    public House(int numRooms) {
        rooms = new ArrayList<>();
        for (int i = 0; i < numRooms; i++) {
            rooms.add(new Room()); // Created by House, dies with House
        }
    }
}
```

### Dependency
- Weakest relationship — uses temporarily (method parameter, local variable)
```java
class OrderService {
    public void processOrder(Order order, PaymentGateway gateway) {
        // Depends on PaymentGateway but doesn't own it
        gateway.charge(order.getTotal());
    }
}
```

### Relationship Strength
```
Dependency < Association < Aggregation < Composition
(weakest)                                  (strongest)
```

---

## UML Notation for LLD

```
Inheritance:      Child ——▷ Parent (hollow triangle)
Interface impl:   Class - -▷ Interface (dashed + hollow triangle)
Composition:      Whole ——◆ Part (filled diamond)
Aggregation:      Whole ——◇ Part (hollow diamond)
Association:      Class ——→ Class (arrow)
Dependency:       Class - -→ Class (dashed arrow)
```

---

## Interview Questions

**Q: When would you use inheritance vs composition?**
> Use inheritance when: True IS-A relationship exists, you need polymorphism with a shared base type, the relationship is stable and unlikely to change.
> Use composition when: You want to reuse behavior without being locked into a hierarchy, relationships might change, you need to combine multiple behaviors, you want better testability.
> Rule: Default to composition. Use inheritance only for genuine type hierarchies.

**Q: What is the Liskov Substitution Principle in OOP?**
> Any subclass should be usable wherever its parent class is expected, without breaking correctness. If Square extends Rectangle but setWidth() also changes height, it violates LSP because users of Rectangle expect independent width/height.

**Q: How does polymorphism help in system design?**
> It allows the system to be extended without modifying existing code. New payment methods, new vehicle types, new notification channels — all can be added by implementing an interface. The core system code works with the abstraction, not concrete implementations.

**Q: What's the difference between aggregation and composition?**
> Lifecycle ownership. Composition: Part can't exist without whole (Room without House). Aggregation: Part exists independently (Employee without Department). In code: Composition = create and own the part. Aggregation = receive the part via constructor/setter.

**Q: Why is "God class" an anti-pattern?**
> A class that knows too much or does too much violates Single Responsibility. It's hard to test, hard to modify, and creates tight coupling. In LLD, if a class has >5-7 responsibilities, it should be decomposed.

---

## Common Mistakes
- Using inheritance for code reuse (should be composition)
- Deep inheritance hierarchies (>3 levels become unmaintainable)
- Exposing internal collections (return unmodifiable copies)
- Not using interfaces for polymorphism
- God classes that do everything
- Confusing aggregation with composition in class diagrams

---

## Best Practices
- Favor composition over inheritance
- Program to interfaces, not implementations
- Keep classes focused (Single Responsibility)
- Use meaningful names that reflect domain concepts
- Encapsulate what varies (Strategy pattern)
- Design for extension (new behaviors via new classes, not modifying existing)

---

## Related Topics
- SOLID Principles (next)
- Design Patterns
- Class Diagrams
- LLD Interview Problems
