# 29. Serialization

## Theory

Serialization is the process of converting an object's state into a byte stream, and deserialization is the reverse — reconstructing the object from the byte stream. This enables:

- Persisting objects to disk
- Transmitting objects over a network
- Deep copying objects
- Storing objects in caches (Redis, Memcached)

### Java Native Serialization

Java provides built-in serialization via `java.io.Serializable` interface. An object is serializable if its class implements `Serializable` (a marker interface with no methods).

### Key Concepts

- **Serializable**: Marker interface enabling default serialization
- **serialVersionUID**: Version number for class compatibility during deserialization
- **transient**: Fields marked transient are excluded from serialization
- **Externalizable**: Interface for complete custom control over serialization
- **ObjectInputStream / ObjectOutputStream**: Streams that handle object serialization

---

## Internal Working

### Serialization Process

```
Object in Memory                    Byte Stream
┌──────────────────┐               ┌──────────────────────────────┐
│ Employee         │               │ AC ED (magic number)          │
│ ├── name: "John" │  ──────→     │ 00 05 (version)              │
│ ├── age: 30      │ serialize     │ 73 (TC_OBJECT)               │
│ └── salary: 5000 │               │ Class descriptor...           │
└──────────────────┘               │ Field values: "John", 30, 5000│
                                   └──────────────────────────────┘

Deserialization (reverse):
Byte Stream → ObjectInputStream → Object reconstructed
(Constructor is NOT called during deserialization!)
```

### What Gets Serialized

```
For a class implementing Serializable:
├── All instance fields (recursively, if they're also Serializable)
├── Class metadata (class name, serialVersionUID, field descriptors)
├── Superclass state (if superclass is Serializable)
│
NOT serialized:
├── static fields (belong to class, not instance)
├── transient fields (explicitly excluded)
├── non-Serializable fields → NotSerializableException
└── methods (only state is serialized)
```

### serialVersionUID Resolution

```
During Deserialization:
1. Read class name from byte stream
2. Load class via ClassLoader
3. Compare serialVersionUID:
   Stream UID == Class UID?  → Proceed with deserialization
   Stream UID != Class UID?  → throw InvalidClassException

If no explicit serialVersionUID:
  JVM computes one based on:
  - Class name
  - Field names and types
  - Method signatures
  - Interface names
  
  Problem: Adding ANY field/method changes the computed UID
           → Old serialized data becomes unreadable!
```

---

## Diagram

### Object Graph Serialization

```
When serializing an object with references:

Employee emp:
  name: "John"              String is Serializable ✓
  department: Department    Department must be Serializable ✓
    └── name: "Engineering"
    └── manager: Employee   Circular reference handled! (back-reference)
  address: Address          Address must be Serializable ✓
    └── city: "NYC"
  logger: Logger            transient → skipped

Byte Stream contains:
[Employee descriptor]
[name = "John"]
[Department descriptor]
  [name = "Engineering"]
  [manager = back-reference to Employee] ← avoids infinite loop
[Address descriptor]
  [city = "NYC"]
[logger = skipped (transient)]
```

### Transient Keyword Effect

```
class User implements Serializable {
    String username;           // ✓ Serialized
    transient String password; // ✗ NOT serialized
    transient Logger logger;   // ✗ NOT serialized
    int loginCount;            // ✓ Serialized
}

After deserialization:
  username = "john"     (restored from stream)
  password = null       (default value - was transient)
  logger = null         (default value - was transient)
  loginCount = 5        (restored from stream)
```

---

## Code

### Basic Serialization

```java
import java.io.*;

public class SerializationBasics {
    
    static class Employee implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private int age;
        private double salary;
        private transient String password; // Won't be serialized
        
        public Employee(String name, int age, double salary, String password) {
            this.name = name;
            this.age = age;
            this.salary = salary;
            this.password = password;
        }
        
        @Override
        public String toString() {
            return String.format("Employee{name='%s', age=%d, salary=%.2f, password='%s'}",
                name, age, salary, password);
        }
    }
    
    public static void main(String[] args) throws Exception {
        Employee emp = new Employee("John", 30, 75000.0, "secret123");
        
        // Serialize
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream("employee.ser"))) {
            oos.writeObject(emp);
            System.out.println("Serialized: " + emp);
        }
        
        // Deserialize
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream("employee.ser"))) {
            Employee restored = (Employee) ois.readObject();
            System.out.println("Deserialized: " + restored);
            // password will be null (transient)
        }
    }
}
```

### Custom Serialization (writeObject/readObject)

```java
import java.io.*;
import java.util.Base64;

public class CustomSerializationDemo {
    
    static class SecureUser implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String username;
        private transient String password; // transient but we handle it ourselves
        private int age;
        
        public SecureUser(String username, String password, int age) {
            this.username = username;
            this.password = password;
            this.age = age;
        }
        
        // Custom serialization: encrypt password before writing
        private void writeObject(ObjectOutputStream oos) throws IOException {
            oos.defaultWriteObject(); // Serialize non-transient fields normally
            
            // Manually serialize transient field (encrypted)
            String encrypted = Base64.getEncoder().encodeToString(password.getBytes());
            oos.writeObject(encrypted);
        }
        
        // Custom deserialization: decrypt password after reading
        private void readObject(ObjectInputStream ois) 
                throws IOException, ClassNotFoundException {
            ois.defaultReadObject(); // Deserialize non-transient fields normally
            
            // Manually deserialize transient field (decrypt)
            String encrypted = (String) ois.readObject();
            this.password = new String(Base64.getDecoder().decode(encrypted));
        }
        
        @Override
        public String toString() {
            return String.format("SecureUser{username='%s', password='%s', age=%d}",
                username, password, age);
        }
    }
    
    public static void main(String[] args) throws Exception {
        SecureUser user = new SecureUser("admin", "p@ssw0rd", 28);
        
        // Serialize and deserialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(user);
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()))) {
            SecureUser restored = (SecureUser) ois.readObject();
            System.out.println(restored);
            // SecureUser{username='admin', password='p@ssw0rd', age=28}
        }
    }
}
```

### Externalizable (Full Custom Control)

```java
import java.io.*;

public class ExternalizableDemo {
    
    static class CompactEmployee implements Externalizable {
        private String name;
        private int age;
        private double salary;
        
        // MUST have no-arg constructor for Externalizable
        public CompactEmployee() {}
        
        public CompactEmployee(String name, int age, double salary) {
            this.name = name;
            this.age = age;
            this.salary = salary;
        }
        
        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            // Full control over what and how to write
            out.writeUTF(name);
            out.writeInt(age);
            // Skip salary if below threshold (example of selective serialization)
            out.writeBoolean(salary > 0);
            if (salary > 0) {
                out.writeDouble(salary);
            }
        }
        
        @Override
        public void readExternal(ObjectInput in) throws IOException {
            // Must read in SAME order as written
            name = in.readUTF();
            age = in.readInt();
            boolean hasSalary = in.readBoolean();
            salary = hasSalary ? in.readDouble() : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("CompactEmployee{name='%s', age=%d, salary=%.2f}",
                name, age, salary);
        }
    }
}
```

### Serialization with Inheritance

```java
import java.io.*;

public class InheritanceSerializationDemo {
    
    // Non-serializable parent (must have no-arg constructor)
    static class Person {
        protected String name;
        protected int age;
        
        public Person() {
            // Called during deserialization for non-serializable parent
            this.name = "Default";
            this.age = 0;
        }
        
        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
    
    // Serializable child
    static class Employee extends Person implements Serializable {
        private static final long serialVersionUID = 1L;
        private String department;
        
        public Employee(String name, int age, String department) {
            super(name, age);
            this.department = department;
        }
        
        // Must handle parent's fields manually since parent is NOT Serializable
        private void writeObject(ObjectOutputStream oos) throws IOException {
            oos.defaultWriteObject();
            // Manually save parent fields
            oos.writeObject(name);
            oos.writeInt(age);
        }
        
        private void readObject(ObjectInputStream ois) 
                throws IOException, ClassNotFoundException {
            ois.defaultReadObject();
            // Manually restore parent fields
            this.name = (String) ois.readObject();
            this.age = ois.readInt();
        }
        
        @Override
        public String toString() {
            return String.format("Employee{name='%s', age=%d, dept='%s'}",
                name, age, department);
        }
    }
}
```

### Singleton Serialization Problem and Fix

```java
import java.io.*;

public class SingletonSerializationDemo {
    
    static class Singleton implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final Singleton INSTANCE = new Singleton();
        
        private String config = "production";
        
        private Singleton() {}
        
        public static Singleton getInstance() { return INSTANCE; }
        
        // Without this: deserialization creates a NEW instance!
        // This method is called after deserialization to replace the object
        private Object readResolve() throws ObjectStreamException {
            return INSTANCE; // Always return the same instance
        }
        
        @Override
        public String toString() {
            return "Singleton{config='" + config + "'}@" + System.identityHashCode(this);
        }
    }
    
    public static void main(String[] args) throws Exception {
        Singleton original = Singleton.getInstance();
        
        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(original);
        
        // Deserialize
        ObjectInputStream ois = new ObjectInputStream(
            new ByteArrayInputStream(baos.toByteArray()));
        Singleton deserialized = (Singleton) ois.readObject();
        
        System.out.println("Same instance: " + (original == deserialized)); // true
    }
}
```

### Serialization Proxy Pattern (Best Practice)

```java
import java.io.*;
import java.time.LocalDate;

public class SerializationProxyDemo {
    
    // The actual class
    static class Period implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final LocalDate start;
        private final LocalDate end;
        
        public Period(LocalDate start, LocalDate end) {
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("start after end");
            }
            this.start = start;
            this.end = end;
        }
        
        // Replace this object with its proxy during serialization
        private Object writeReplace() {
            return new SerializationProxy(this);
        }
        
        // Prevent direct deserialization (security)
        private void readObject(ObjectInputStream ois) throws InvalidObjectException {
            throw new InvalidObjectException("Use SerializationProxy");
        }
        
        // Inner proxy class
        private static class SerializationProxy implements Serializable {
            private static final long serialVersionUID = 1L;
            private final LocalDate start;
            private final LocalDate end;
            
            SerializationProxy(Period period) {
                this.start = period.start;
                this.end = period.end;
            }
            
            // Replace proxy with real object during deserialization
            private Object readResolve() {
                return new Period(start, end); // Validates invariants via constructor!
            }
        }
        
        @Override
        public String toString() {
            return String.format("Period[%s to %s]", start, end);
        }
    }
}
```

---

## Dry Run

### Serialization Process

```
Object: Employee{name="John", age=30, transient password="secret"}
serialVersionUID = 1L

ObjectOutputStream.writeObject(emp):

Step 1: Check if Employee implements Serializable → YES
Step 2: Check if custom writeObject() exists → NO (use default)
Step 3: Write stream header: AC ED 00 05 (magic + version)
Step 4: Write TC_OBJECT marker
Step 5: Write class descriptor:
  - Class name: "Employee"
  - serialVersionUID: 1L
  - Field descriptors: [name:String, age:int] (skip transient password)
Step 6: Write field values:
  - name: "John" (String object)
  - age: 30 (primitive int)
Step 7: Flush stream

Deserialization (ObjectInputStream.readObject()):
Step 1: Read header, verify magic number
Step 2: Read TC_OBJECT
Step 3: Read class descriptor, load class
Step 4: Compare serialVersionUID: stream(1L) == class(1L) → OK
Step 5: Allocate Employee WITHOUT calling constructor
Step 6: Read field values:
  - name = "John"
  - age = 30
  - password = null (transient, gets default)
Step 7: Return Employee instance
```

---

## Complexity

| Operation | Space | Time | Notes |
|-----------|-------|------|-------|
| Serialization | O(object graph size) | O(n) | n = total fields in graph |
| Deserialization | O(object graph size) | O(n) | Plus class loading if needed |
| Object graph traversal | O(V + E) | O(V + E) | Handles cycles via back-references |

Java native serialization is verbose — objects are typically 3-10x larger than JSON equivalents.

---

## Real Project Usage

### 1. Deep Copy via Serialization

```java
public class DeepCopyUtil {
    
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T deepCopy(T object) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new ObjectOutputStream(baos).writeObject(object);
            
            ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(baos.toByteArray()));
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Deep copy failed", e);
        }
    }
}
```

### 2. Session Storage (Why Serializable Matters in Web Apps)

```java
// In Spring/Java EE, session attributes must be Serializable
// for session replication across servers
@Controller
public class CartController {
    
    @PostMapping("/cart/add")
    public String addToCart(HttpSession session, @RequestParam Long productId) {
        // ShoppingCart must implement Serializable!
        ShoppingCart cart = (ShoppingCart) session.getAttribute("cart");
        if (cart == null) {
            cart = new ShoppingCart();
        }
        cart.addItem(productId);
        session.setAttribute("cart", cart); // Must be serializable for session replication
        return "redirect:/cart";
    }
}
```

### 3. Modern Alternative: JSON Serialization (Jackson)

```java
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializationDemo {
    
    static class Employee {
        private String name;
        private int age;
        @JsonIgnore // Equivalent to transient
        private String password;
        
        // Getters, setters, no-arg constructor required for Jackson
    }
    
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Employee emp = new Employee("John", 30, "secret");
        
        // Serialize to JSON
        String json = mapper.writeValueAsString(emp);
        // {"name":"John","age":30}
        
        // Deserialize from JSON
        Employee restored = mapper.readValue(json, Employee.class);
    }
}
```

---

## Interview Questions and Answers

### Q1: What is serialization in Java?
**A**: Serialization converts an object's state to a byte stream (for storage or transmission), and deserialization reconstructs the object from bytes. Java provides this via `Serializable` interface, `ObjectOutputStream` (write), and `ObjectInputStream` (read). The object's entire graph (referenced objects) is serialized recursively.

### Q2: What is serialVersionUID and why is it important?
**A**: `serialVersionUID` is a version identifier for a serializable class. During deserialization, the JVM compares the stream's UID with the loaded class's UID. If they don't match, `InvalidClassException` is thrown. If you don't declare it explicitly, the JVM computes one based on class structure — any change (adding a field, method) changes the computed UID, breaking backward compatibility. Always declare it explicitly.

### Q3: What is the transient keyword?
**A**: `transient` marks a field to be excluded from serialization. Use for: sensitive data (passwords), non-serializable fields (Logger, Thread), derived/computed fields, or fields that should be re-initialized. Transient fields get their type's default value after deserialization (null for objects, 0 for primitives).

### Q4: What happens if a superclass is not Serializable?
**A**: If the parent class doesn't implement `Serializable`, its no-arg constructor is called during deserialization (parent fields get default/constructor values, not the serialized values). The child's serializable fields are restored normally. If the parent has no no-arg constructor, you get `InvalidClassException`.

### Q5: What is the difference between Serializable and Externalizable?
**A**: 
- `Serializable` — Marker interface, JVM handles serialization automatically. Uses reflection (slower). Optional `writeObject`/`readObject` for customization.
- `Externalizable` — Must implement `writeExternal()`/`readExternal()` explicitly. Full manual control. Requires no-arg constructor. Faster (no reflection) but more code.

### Q6: Why is Java native serialization generally avoided in modern systems?
**A**:
- **Security vulnerabilities**: Deserializing untrusted data can execute arbitrary code (gadget chains)
- **Performance**: Slow compared to Protocol Buffers, JSON, Avro
- **Verbose**: Large byte streams (includes full class metadata)
- **Versioning fragility**: Class changes easily break compatibility
- **Language-locked**: Only Java can read Java serialized data

Modern alternatives: JSON (Jackson), Protocol Buffers, Avro, MessagePack.

---

## Follow-up Questions and Answers

### Q: What is the readResolve() method?
**A**: `readResolve()` is called after deserialization and its return value replaces the deserialized object. Used to preserve singletons (return the canonical instance instead of the deserialized copy) and implement serialization proxies. Signature: `private Object readResolve() throws ObjectStreamException`.

### Q: What is the serialization proxy pattern?
**A**: The class writes a proxy object instead of itself (`writeReplace()`), and blocks direct deserialization (`readObject()` throws exception). The proxy's `readResolve()` creates the real object via its public constructor, ensuring all invariants are validated. This is the safest serialization approach (Effective Java Item 90).

### Q: What are deserialization vulnerabilities?
**A**: Attackers craft malicious byte streams that, when deserialized, trigger a chain of method calls on existing classes in the classpath (gadget chains) leading to remote code execution. Libraries like Commons Collections had exploitable gadgets. Mitigation: never deserialize untrusted data, use `ObjectInputFilter` (Java 9+), or avoid native serialization entirely.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Not declaring serialVersionUID | Class changes break deserialization | Always declare `private static final long serialVersionUID` |
| Serializing non-serializable fields | `NotSerializableException` | Make field transient or implement Serializable |
| Deserializing untrusted data | Remote Code Execution vulnerability | Use JSON/Protobuf, or ObjectInputFilter |
| Forgetting no-arg constructor for Externalizable | `InvalidClassException` | Always provide public no-arg constructor |
| Not handling transient field initialization | NullPointerException after deserialization | Use readObject() to initialize transient fields |
| Serializing singletons without readResolve() | Multiple instances after deserialization | Implement readResolve() |

---

## Best Practices

1. **Avoid Java native serialization for new code** — Use JSON, Protocol Buffers, or Avro instead
2. **Always declare serialVersionUID** — Prevents accidental incompatibility
3. **Use transient for sensitive data** — Passwords, tokens, keys
4. **Use serialization proxy pattern** — Safest approach when native serialization is required
5. **Implement readObject validation** — Validate invariants after deserialization
6. **Use ObjectInputFilter (Java 9+)** — Whitelist allowed classes during deserialization
7. **Consider record classes (Java 16+)** — Records have built-in serialization support with constructor validation

---

## Production Considerations

- **Security first**: Never deserialize untrusted data. Use `ObjectInputFilter` to restrict allowed classes. In production, prefer JSON/Protobuf for cross-service communication.
- **Cache serialization**: Redis, Hazelcast, and other caches require serializable objects. Consider using Kryo or Protobuf serializers instead of Java native.
- **Session replication**: Web containers serialize sessions for failover. All session-stored objects must be Serializable with proper versioning.
- **Message queues**: JMS `ObjectMessage` uses Java serialization. Prefer `TextMessage` with JSON for interoperability.
- **Version evolution**: Plan for class changes. Add fields with defaults, never remove fields (mark deprecated). Use serialization proxy for complex versioning.

---

## Related Topics

- [27. I/O](./27-io.md)
- [28. NIO](./28-nio.md)
- [31. Design Patterns](./31-design-patterns.md) (Proxy Pattern, Singleton Pattern)
