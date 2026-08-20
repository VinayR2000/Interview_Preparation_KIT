# 2. OOP (Object-Oriented Programming) — Extremely Important

---

## Theory

OOP is a programming paradigm based on the concept of **objects** — entities that contain data (fields) and behavior (methods). Java is fundamentally object-oriented.

### The 4 Pillars

#### 1. Encapsulation
- Bundling data and methods that operate on that data within a single unit (class)
- Hiding internal state and requiring all interaction through well-defined interfaces
- Achieved through **access modifiers** (private, protected, public, default)

```java
public class BankAccount {
    private double balance; // hidden state
    
    public void deposit(double amount) { // controlled access
        if (amount > 0) balance += amount;
    }
    
    public double getBalance() { return balance; }
}
```

#### 2. Inheritance
- Mechanism where a new class (child) inherits properties and behaviors from an existing class (parent)
- Promotes code reuse and establishes an IS-A relationship
- Java supports **single inheritance** for classes, **multiple inheritance** through interfaces

```java
public class Animal {
    protected String name;
    public void eat() { System.out.println(name + " eats"); }
}

public class Dog extends Animal {
    public void bark() { System.out.println(name + " barks"); }
}
```

#### 3. Polymorphism
- Ability of an object to take many forms
- **Compile-time (Static)**: Method overloading — same method name, different parameters
- **Runtime (Dynamic)**: Method overriding — subclass provides specific implementation

```java
// Compile-time polymorphism
public int add(int a, int b) { return a + b; }
public double add(double a, double b) { return a + b; }

// Runtime polymorphism
Animal animal = new Dog(); // reference type: Animal, object type: Dog
animal.eat(); // calls Dog's eat() if overridden
```

#### 4. Abstraction
- Hiding complex implementation details and showing only necessary features
- Achieved through **abstract classes** and **interfaces**
- Focus on WHAT an object does, not HOW it does it

```java
public abstract class Shape {
    abstract double area(); // what to do
    // how to do it → left to subclasses
}

public class Circle extends Shape {
    private double radius;
    double area() { return Math.PI * radius * radius; } // how
}
```

### Class, Object, Constructor

```java
public class Employee {
    // Fields (state)
    private String name;
    private int age;
    
    // Constructor (initializes object)
    public Employee(String name, int age) {
        this.name = name;  // 'this' refers to current instance
        this.age = age;
    }
    
    // Default constructor (if no constructor defined, compiler adds this)
    public Employee() {
        this("Unknown", 0); // constructor chaining
    }
}

// Object creation
Employee emp = new Employee("John", 30);
// 1. Memory allocated on heap
// 2. Constructor called
// 3. Reference 'emp' stored on stack
```

### this vs super

| `this` | `super` |
|--------|---------|
| Refers to current instance | Refers to parent class |
| `this.field` — current object's field | `super.field` — parent's field |
| `this()` — calls another constructor | `super()` — calls parent constructor |
| Must be first statement (in constructor) | Must be first statement (in constructor) |
| Cannot use both `this()` and `super()` in same constructor |

### Method Overloading vs Overriding

| Overloading | Overriding |
|-------------|-----------|
| Same class | Different classes (inheritance) |
| Same name, different parameters | Same name, same parameters |
| Compile-time binding | Runtime binding |
| Return type can differ | Return type must be same or covariant |
| Access modifier can be anything | Cannot be more restrictive |
| Can throw any exception | Cannot throw broader checked exception |
| Static methods can be overloaded | Static methods cannot be overridden (hidden) |

### Static Members
```java
public class Counter {
    private static int count = 0; // belongs to class, not instance
    
    public Counter() { count++; }
    
    public static int getCount() { return count; } // no 'this' access
}
// Static members: loaded once when class loads
// Access: Counter.getCount() — no object needed
```

### Final Keyword
```java
final int MAX = 100;           // constant variable
final class Utility { }         // cannot be extended
final void process() { }       // cannot be overridden

// final reference — reference can't change, but object CAN be mutated
final List<String> list = new ArrayList<>();
list.add("hello"); // OK — modifying object
// list = new ArrayList<>(); // COMPILE ERROR — reassigning reference
```

### Abstract Class vs Interface

| Abstract Class | Interface |
|---------------|-----------|
| Can have constructors | No constructors |
| Can have instance fields | Only constants (public static final) |
| Can have any access modifier | Methods are public by default |
| Single inheritance | Multiple inheritance |
| Can have concrete methods | Default/static methods (Java 8+) |
| Use when classes share state | Use for capability/contract |
| IS-A relationship | CAN-DO relationship |

### Composition, Association, Aggregation

```java
// Association — general relationship (uses-a)
class Teacher {
    private List<Student> students; // Teacher knows Students
}

// Aggregation — weak HAS-A (parts can exist independently)
class Department {
    private List<Employee> employees; // Employees exist without Department
}

// Composition — strong HAS-A (parts cannot exist without whole)
class House {
    private final Room room; // Room cannot exist without House
    
    public House() {
        this.room = new Room(); // created WITH House
    }
}
```

### Composition vs Inheritance

| Composition | Inheritance |
|------------|-------------|
| HAS-A | IS-A |
| Runtime flexibility | Compile-time rigid |
| Loose coupling | Tight coupling |
| Preferred in most cases | Use when true subtype relationship |
| Can change behavior at runtime | Fixed hierarchy |

**Rule**: Favor composition over inheritance (Effective Java, Item 18)

---

## Internal Working

### Object Memory Layout (HotSpot JVM)

```
┌─────────────────────────────────┐
│         Object Header           │
│  ┌─────────────────────────┐   │
│  │ Mark Word (8 bytes)     │   │ → hash, GC age, lock info
│  │ Klass Pointer (4/8 bytes)│  │ → pointer to class metadata
│  └─────────────────────────┘   │
├─────────────────────────────────┤
│       Instance Fields           │
│  field1, field2, ...            │ → actual data
├─────────────────────────────────┤
│       Padding                   │ → alignment to 8 bytes
└─────────────────────────────────┘
```

### Virtual Method Table (vtable) — How Polymorphism Works

```
Animal class vtable:
┌──────────────────┐
│ eat()  → Animal.eat │
│ sleep()→ Animal.sleep│
└──────────────────┘

Dog class vtable:
┌──────────────────┐
│ eat()  → Dog.eat    │  ← overridden, points to Dog's implementation
│ sleep()→ Animal.sleep│  ← inherited
│ bark() → Dog.bark   │  ← new method
└──────────────────┘

Runtime dispatch:
Animal a = new Dog();
a.eat(); 
→ JVM looks at actual object type (Dog)
→ Finds Dog's vtable
→ Calls Dog.eat()
```

### Constructor Chaining Internally

```
new Dog("Rex") is called:
1. JVM allocates memory for Dog object (including inherited fields)
2. super() is called → Animal constructor runs FIRST
3. Dog constructor body runs
4. Reference returned

Order: Object() → Animal() → Dog()
```

### Static Binding vs Dynamic Binding

```
Compile time (static binding):
- Method overloading → resolved by compiler based on reference type
- Private, static, final methods → cannot be overridden

Runtime (dynamic binding):
- Method overriding → resolved by JVM based on actual object type
- Uses vtable lookup at runtime
```

---

## Diagram

```
OOP Pillars:
┌─────────────────────────────────────────────────────┐
│                    OOP                               │
├──────────┬──────────┬──────────────┬────────────────┤
│Encapsulat│Inheritance│Polymorphism  │ Abstraction    │
│ion       │           │              │                │
│Hide data │Reuse code │Many forms    │Hide complexity │
│Access    │IS-A       │Overloading   │Abstract class  │
│modifiers │extends    │Overriding    │Interface       │
└──────────┴──────────┴──────────────┴────────────────┘
```

```
Inheritance Hierarchy:
         Object
           │
         Animal (abstract)
        /      \
     Dog       Cat
      │
  GoldenRetriever
```

```
Composition vs Inheritance:
Inheritance:                     Composition:
┌─────────┐                     ┌─────────┐
│  Engine  │                     │   Car   │
└────┬────┘                     │ ┌─────┐ │
     │ extends                   │ │Engine│ │ has-a
┌────┴────┐                     │ └─────┘ │
│   Car   │                     └─────────┘
└─────────┘                     
(Car IS-A Engine? No!)          (Car HAS-A Engine ✓)
```

---

## Code

```java
// Complete OOP demonstration

// --- Abstraction ---
public interface Flyable {
    void fly(); // contract
    
    default void land() { // Java 8+ default method
        System.out.println("Landing...");
    }
}

public interface Swimmable {
    void swim();
}

// --- Abstract class ---
public abstract class Bird {
    protected String name;
    protected int weight;
    
    // Constructor in abstract class
    public Bird(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }
    
    // Concrete method
    public void breathe() {
        System.out.println(name + " breathes");
    }
    
    // Abstract method — subclass MUST implement
    public abstract String getSound();
}

// --- Inheritance + Multiple Interface Implementation ---
public class Duck extends Bird implements Flyable, Swimmable {
    
    // Encapsulation — private field
    private boolean isDomestic;
    
    public Duck(String name, int weight, boolean isDomestic) {
        super(name, weight); // calls Bird constructor
        this.isDomestic = isDomestic;
    }
    
    // --- Polymorphism (Overriding) ---
    @Override
    public String getSound() {
        return "Quack!";
    }
    
    @Override
    public void fly() {
        if (!isDomestic) {
            System.out.println(name + " flies");
        } else {
            System.out.println(name + " can barely fly");
        }
    }
    
    @Override
    public void swim() {
        System.out.println(name + " swims gracefully");
    }
    
    // --- Polymorphism (Overloading) ---
    public void feed(String food) {
        System.out.println(name + " eats " + food);
    }
    
    public void feed(String food, int amount) {
        System.out.println(name + " eats " + amount + "g of " + food);
    }
}

// --- Composition ---
public class Pond {
    // Strong composition — Pond owns the Water
    private final Water water;
    // Aggregation — Ducks can exist without Pond
    private List<Duck> ducks;
    
    public Pond(int waterVolume) {
        this.water = new Water(waterVolume); // created with Pond
        this.ducks = new ArrayList<>();
    }
    
    public void addDuck(Duck duck) {
        ducks.add(duck);
    }
}

// --- Usage with Runtime Polymorphism ---
public class Main {
    public static void main(String[] args) {
        // Polymorphic reference
        Bird bird = new Duck("Donald", 3, true);
        System.out.println(bird.getSound()); // "Quack!" — runtime dispatch
        
        // Interface reference
        Flyable flyer = new Duck("Daffy", 2, false);
        flyer.fly(); // "Daffy flies"
        
        // Cannot call swim() through Flyable reference
        // flyer.swim(); // COMPILE ERROR
        
        // Type checking and casting
        if (flyer instanceof Duck duck) { // pattern matching Java 16+
            duck.swim(); // now we can access Duck methods
        }
    }
}
```

---

## Dry Run

### Runtime Polymorphism
```
Code: Bird bird = new Duck("Donald", 3, true);
      bird.getSound();

Step 1: new Duck("Donald", 3, true)
  → Memory allocated for Duck object on heap
  → super("Donald", 3) called → Bird fields initialized: name="Donald", weight=3
  → isDomestic = true

Step 2: Bird bird = ...
  → Reference type is Bird (compile-time type)
  → Object type is Duck (runtime type)
  → Reference 'bird' stored on stack, pointing to Duck object on heap

Step 3: bird.getSound()
  → Compiler checks: does Bird have getSound()? Yes → compiles
  → Runtime: JVM looks at actual object type → Duck
  → Finds Duck's vtable → getSound() → Duck.getSound()
  → Returns "Quack!"
```

### Constructor Chaining
```
Code: new Duck("Donald", 3, true)

Execution Order:
1. Object() constructor (implicit)
2. Bird("Donald", 3) constructor
   → this.name = "Donald"
   → this.weight = 3
3. Duck constructor body
   → this.isDomestic = true
4. Duck object fully initialized, reference returned
```

---

## Complexity

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Object creation | O(1) | Memory allocation + constructor |
| Virtual method call | O(1) | vtable lookup is constant time |
| instanceof check | O(depth) | Traverses class hierarchy |
| Method overload resolution | Compile-time | No runtime cost |
| Field access | O(1) | Direct memory offset |

---

## Real Project Usage

```java
// Service layer using OOP principles (Spring Boot style)

// Interface (abstraction)
public interface PaymentService {
    PaymentResult processPayment(PaymentRequest request);
    void refund(String transactionId);
}

// Base class with common logic
public abstract class AbstractPaymentService implements PaymentService {
    
    protected final PaymentRepository repository;
    protected final AuditLogger auditLogger;
    
    protected AbstractPaymentService(PaymentRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }
    
    @Override
    public final PaymentResult processPayment(PaymentRequest request) {
        // Template Method pattern
        validate(request);
        PaymentResult result = executePayment(request); // abstract
        auditLogger.log(request, result);
        return result;
    }
    
    protected abstract PaymentResult executePayment(PaymentRequest request);
    
    private void validate(PaymentRequest request) {
        if (request.getAmount() <= 0) {
            throw new InvalidPaymentException("Amount must be positive");
        }
    }
}

// Concrete implementation (inheritance + polymorphism)
@Service
public class StripePaymentService extends AbstractPaymentService {
    
    private final StripeClient stripeClient; // composition
    
    public StripePaymentService(PaymentRepository repo, AuditLogger logger, StripeClient client) {
        super(repo, logger);
        this.stripeClient = client;
    }
    
    @Override
    protected PaymentResult executePayment(PaymentRequest request) {
        StripeCharge charge = stripeClient.charge(request.getAmount(), request.getCurrency());
        return new PaymentResult(charge.getId(), PaymentStatus.SUCCESS);
    }
    
    @Override
    public void refund(String transactionId) {
        stripeClient.refund(transactionId);
        repository.updateStatus(transactionId, PaymentStatus.REFUNDED);
    }
}

// Usage with polymorphism
@RestController
public class PaymentController {
    private final PaymentService paymentService; // interface reference
    
    // Spring injects the concrete implementation
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}
```

---

## Interview Questions and Answers

**Q1: What are the 4 pillars of OOP?**
> Encapsulation (data hiding via access modifiers), Inheritance (code reuse via extends), Polymorphism (one interface, many implementations — overloading + overriding), Abstraction (hiding complexity via abstract classes/interfaces).

**Q2: Why is multiple inheritance not supported in Java for classes?**
> The **Diamond Problem**: If class C extends both A and B, and both have a method `foo()`, which one does C inherit? Java avoids this ambiguity. Interfaces solve it because: (a) before Java 8, interfaces had no implementations, (b) from Java 8+, if two interfaces have conflicting defaults, the implementing class MUST override to resolve.

**Q3: Can you call a constructor from another constructor?**
> Yes, using `this()` for same-class constructors or `super()` for parent constructors. Must be the first statement. You cannot use both `this()` and `super()` in the same constructor.

**Q4: What is covariant return type?**
> When overriding a method, the return type can be a subclass of the original return type. Example: if parent returns `Animal`, child can return `Dog`. This is valid because Dog IS-A Animal.

**Q5: Can we override static methods?**
> No. Static methods belong to the class, not instances. If a subclass defines the same static method, it's **method hiding**, not overriding. The method called depends on the reference type, not the object type.

**Q6: Explain the difference between composition and inheritance. When would you use each?**
> Use inheritance for genuine IS-A relationships (Dog IS-A Animal). Use composition for HAS-A relationships (Car HAS-A Engine). Prefer composition because: (1) it's more flexible — can change behavior at runtime, (2) avoids fragile base class problem, (3) enables loose coupling. Only use inheritance when the Liskov Substitution Principle holds.

---

## Follow-up Questions and Answers

**Q: What is the Liskov Substitution Principle (LSP)?**
> Objects of a superclass should be replaceable with objects of a subclass without breaking the program. If `Square extends Rectangle` and you set width/height independently, Square breaks LSP because setting width must also change height. This indicates the IS-A relationship is incorrect.

**Q: How does JVM achieve runtime polymorphism internally?**
> Through the vtable (virtual method table). Each class has a vtable mapping method signatures to actual implementations. When a virtual method is called, JVM looks up the actual object's class vtable to find the correct implementation. This is a constant-time O(1) lookup.

**Q: What's the difference between `this()` and `this`?**
> `this` is a reference to the current object — used to access instance members or pass the current object. `this()` is a constructor call — used to invoke another constructor in the same class, must be the first statement.

**Q: Can an abstract class have a constructor?**
> Yes. Abstract classes can have constructors. They cannot be instantiated directly, but their constructors are called when a concrete subclass is instantiated (via `super()`). This is useful for initializing common fields.

---

## Common Mistakes

1. **Breaking encapsulation by returning mutable references**
   ```java
   public List<String> getItems() { return items; } // exposes internal list!
   // Fix: return Collections.unmodifiableList(items);
   //   or: return new ArrayList<>(items); // defensive copy
   ```

2. **Forgetting to call super() in constructors**
   ```java
   class Child extends Parent {
       Child(String name) {
           // super() is implicitly called — but only no-arg super()
           // If Parent has no no-arg constructor → COMPILE ERROR
       }
   }
   ```

3. **Using inheritance for code reuse (not IS-A)**
   ```java
   class Stack extends ArrayList { } // WRONG — Stack IS NOT an ArrayList
   // Fix: class Stack { private ArrayList list; } // composition
   ```

4. **Not overriding equals/hashCode when overriding one**
   ```java
   // Contract: if a.equals(b), then a.hashCode() == b.hashCode()
   // Breaking this causes HashMap/HashSet failures
   ```

5. **Calling overridable methods from constructors**
   ```java
   class Parent {
       Parent() { init(); } // DANGEROUS — init() may be overridden
       void init() { }
   }
   class Child extends Parent {
       private String name;
       Child() { super(); name = "test"; }
       void init() { System.out.println(name.length()); } // NPE! name is null
   }
   ```

---

## Best Practices

1. **Favor composition over inheritance** — more flexible, less coupled.
2. **Program to interfaces, not implementations** — `List<String> list = new ArrayList<>()`
3. **Keep classes focused** — Single Responsibility Principle.
4. **Make classes immutable when possible** — thread-safe, predictable.
5. **Use access modifiers strictly** — start with `private`, widen only as needed.
6. **Override `toString()`** for meaningful debug output.
7. **Mark utility classes as `final` with private constructor** — prevent instantiation/extension.
8. **Use `@Override` annotation** — compiler catches mistakes.

---

## Production Considerations

- **Object creation cost**: In tight loops, excessive object creation triggers GC pressure. Consider object pooling for expensive objects (database connections, thread pools).

- **Inheritance depth**: Deep hierarchies (>3 levels) become hard to maintain. Prefer flat hierarchies with composition.

- **Interface segregation**: Don't create "fat" interfaces. Split into focused interfaces (ISP from SOLID).

- **Sealed classes (Java 17+)**: Use to restrict inheritance to known subtypes — better for exhaustive pattern matching and API design.

- **Record classes (Java 16+)**: Use for immutable data carriers — automatically generates constructor, equals, hashCode, toString.

---

## Related Topics

- → [1. Java Fundamentals](./01-java-fundamentals.md)
- → [3. Object Class](./03-object-class.md)
- → [8. Generics](./08-generics.md)
- → [25. Reflection](./25-reflection.md)
- → [31. Design Patterns](./31-design-patterns.md)
- → [32. Modern Java](./32-modern-java.md)
