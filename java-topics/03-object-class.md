# 3. Object Class

---

## Theory

`java.lang.Object` is the **root of the class hierarchy**. Every class in Java implicitly extends `Object`. It provides fundamental methods that every Java object inherits.

### Key Methods

| Method | Purpose |
|--------|---------|
| `equals(Object obj)` | Logical equality comparison |
| `hashCode()` | Returns integer hash value for hash-based collections |
| `toString()` | String representation of the object |
| `clone()` | Creates a shallow copy (requires `Cloneable`) |
| `getClass()` | Returns runtime class of the object |
| `finalize()` | Called by GC before reclaiming (deprecated since Java 9) |
| `wait()` / `notify()` / `notifyAll()` | Thread communication |

### equals() — Logical Equality

```java
// Default implementation in Object class:
public boolean equals(Object obj) {
    return (this == obj); // reference equality
}
```

**Contract (must satisfy all):**
1. **Reflexive**: `x.equals(x)` → true
2. **Symmetric**: `x.equals(y)` ↔ `y.equals(x)`
3. **Transitive**: if `x.equals(y)` && `y.equals(z)` → `x.equals(z)`
4. **Consistent**: Multiple calls return same result (if objects unchanged)
5. **Null**: `x.equals(null)` → false

### hashCode() — Hash-Based Lookups

```java
// Default: returns memory address converted to integer (implementation-specific)
public native int hashCode();
```

**Contract:**
1. Consistent: Same object must return same hash during execution (if fields unchanged)
2. **If `equals()` returns true → `hashCode()` MUST be equal**
3. If `equals()` returns false → `hashCode()` MAY be equal (collision)

**Critical Rule**: If you override `equals()`, you **MUST** override `hashCode()`.

### Why This Matters: HashMap/HashSet

```
HashMap.put(key, value):
1. hashCode() → determines bucket index
2. equals() → handles collisions within the bucket

If hashCode() not overridden:
- Two logically equal objects → different hash codes → different buckets
- HashMap cannot find the key even though an "equal" one exists
```

### toString()
```java
// Default:
public String toString() {
    return getClass().getName() + "@" + Integer.toHexString(hashCode());
}
// Output: "com.example.Employee@1b6d3586"
// Not useful! Always override for meaningful output.
```

### clone()
```java
// Must implement Cloneable interface (marker interface)
// Default: creates SHALLOW copy (copies references, not objects)
protected native Object clone() throws CloneNotSupportedException;
```

### getClass()
```java
// Returns the runtime Class object
// Cannot be overridden (final method)
public final native Class<?> getClass();

Employee emp = new Manager();
emp.getClass(); // Manager.class (runtime type, not reference type)
```

---

## Internal Working

### How hashCode() Works with HashMap

```
Key: Employee("John", 101)

Step 1: hashCode() called → returns 12345
Step 2: HashMap computes bucket index:
        index = hashCode() ^ (hashCode() >>> 16)  // spread bits
        index = index & (capacity - 1)             // modulo using bitwise AND
        
Step 3: Go to bucket[index]
Step 4: If bucket is empty → insert new Node
Step 5: If bucket has entries → compare using equals()
        - If equals() match found → update value
        - If no match → add to linked list/tree
```

### equals() + hashCode() Contract Violations

```
Scenario: Override equals() but NOT hashCode()

Employee e1 = new Employee("John", 101);
Employee e2 = new Employee("John", 101);

e1.equals(e2) → true (we overrode equals to compare name+id)
e1.hashCode() → 7842 (from Object — memory address)
e2.hashCode() → 2341 (different address!)

HashMap<Employee, String> map = new HashMap<>();
map.put(e1, "Engineering");
map.get(e2) → null! 

WHY: e2's hashCode points to a DIFFERENT bucket than e1's
     HashMap never even calls equals() because it looks in the wrong bucket
```

### clone() Shallow vs Deep Copy

```
Shallow Copy (default clone()):
┌─────────────┐        ┌──────────┐
│ original    │───────→ │ Address  │
│ name="John" │        │ city="NY"│
└─────────────┘        └──────────┘
       │clone()              ↑
┌─────────────┐              │
│ copy        │──────────────┘ (same reference!)
│ name="John" │
└─────────────┘

Deep Copy:
┌─────────────┐        ┌──────────┐
│ original    │───────→ │ Address  │
│ name="John" │        │ city="NY"│
└─────────────┘        └──────────┘

┌─────────────┐        ┌──────────┐
│ copy        │───────→ │ Address  │ (NEW object)
│ name="John" │        │ city="NY"│
└─────────────┘        └──────────┘
```

---

## Diagram

```
Object Class Methods:
┌─────────────────────────────────────────┐
│            java.lang.Object             │
├─────────────────────────────────────────┤
│ + equals(Object): boolean               │
│ + hashCode(): int                       │
│ + toString(): String                    │
│ # clone(): Object                       │
│ + getClass(): Class<?>                  │
│ # finalize(): void  [DEPRECATED]        │
│ + wait() / notify() / notifyAll()       │
├─────────────────────────────────────────┤
│         Every class inherits these      │
└─────────────────────────────────────────┘
```

```
HashMap Lookup Flow:
┌──────────┐    hashCode()    ┌────────┐    equals()    ┌───────┐
│   Key    │ ───────────────→ │ Bucket │ ─────────────→ │ Node  │
│          │                  │ Index  │                │ Match │
└──────────┘                  └────────┘                └───────┘
```

---

## Code

```java
import java.util.Objects;

public class Employee {
    private final String name;
    private final int id;
    private final String department;

    public Employee(String name, int id, String department) {
        this.name = name;
        this.id = id;
        this.department = department;
    }

    // --- equals() — proper implementation ---
    @Override
    public boolean equals(Object o) {
        // 1. Reference check (performance optimization)
        if (this == o) return true;
        
        // 2. Null and type check
        if (o == null || getClass() != o.getClass()) return false;
        
        // 3. Field comparison
        Employee employee = (Employee) o;
        return id == employee.id && 
               Objects.equals(name, employee.name);
        // Note: department intentionally excluded — 
        // same employee in different dept is still same employee
    }

    // --- hashCode() — MUST override with equals() ---
    @Override
    public int hashCode() {
        return Objects.hash(name, id); // same fields as equals()
    }

    // --- toString() — meaningful representation ---
    @Override
    public String toString() {
        return "Employee{name='" + name + "', id=" + id + 
               ", department='" + department + "'}";
    }

    // --- clone() — deep copy example ---
    // Better alternative: copy constructor
    public Employee(Employee other) {
        this.name = other.name;         // String is immutable, safe to share
        this.id = other.id;
        this.department = other.department;
    }

    // Getters
    public String getName() { return name; }
    public int getId() { return id; }
    public String getDepartment() { return department; }
}

// --- Usage ---
public class ObjectClassDemo {
    public static void main(String[] args) {
        Employee e1 = new Employee("Alice", 101, "Engineering");
        Employee e2 = new Employee("Alice", 101, "Marketing");
        Employee e3 = new Employee("Bob", 102, "Engineering");

        // equals
        System.out.println(e1.equals(e2)); // true (same name, same id)
        System.out.println(e1.equals(e3)); // false

        // hashCode
        System.out.println(e1.hashCode() == e2.hashCode()); // true (contract!)

        // toString
        System.out.println(e1); // Employee{name='Alice', id=101, department='Engineering'}

        // getClass
        System.out.println(e1.getClass().getName()); // Employee
        System.out.println(e1.getClass() == e2.getClass()); // true

        // HashMap correctness
        Map<Employee, String> map = new HashMap<>();
        map.put(e1, "Senior Engineer");
        System.out.println(map.get(e2)); // "Senior Engineer" — works because equals+hashCode
    }
}
```

---

## Dry Run

### HashMap put() and get() with proper equals/hashCode
```
map.put(e1, "Senior Engineer"):
  Step 1: e1.hashCode() → Objects.hash("Alice", 101) → let's say 31742
  Step 2: bucket index = 31742 & (15) = 14 (assuming capacity 16)
  Step 3: bucket[14] is empty → create Node(e1, "Senior Engineer")

map.get(e2):
  Step 1: e2.hashCode() → Objects.hash("Alice", 101) → 31742 (SAME!)
  Step 2: bucket index = 31742 & (15) = 14 (same bucket)
  Step 3: bucket[14] has Node → compare: e2.equals(e1)?
  Step 4: id==id (101==101) ✓ and name.equals(name) ("Alice"=="Alice") ✓
  Step 5: Match found → return "Senior Engineer"
```

### What if hashCode() NOT overridden
```
map.put(e1, "Senior Engineer"):
  Step 1: e1.hashCode() → Object default → 7842 (memory address)
  Step 2: bucket index = 7842 & 15 = 2
  Step 3: bucket[2] → Node(e1, "Senior Engineer")

map.get(e2):
  Step 1: e2.hashCode() → Object default → 2341 (DIFFERENT address!)
  Step 2: bucket index = 2341 & 15 = 5
  Step 3: bucket[5] is EMPTY → return null!
  
  Result: LOST DATA! Even though e1.equals(e2) is true.
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| `equals()` | O(number of fields compared) |
| `hashCode()` | O(number of fields included) |
| `toString()` | O(string length) |
| `clone()` shallow | O(number of fields) |
| `clone()` deep | O(total object graph size) |
| `getClass()` | O(1) |

---

## Real Project Usage

```java
// JPA Entity with proper equals/hashCode
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String orderNumber;
    
    private BigDecimal amount;
    
    @ManyToOne
    private Customer customer;

    // For JPA entities: use BUSINESS KEY, not @Id
    // Because @Id is null before persist, breaking collections
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        // Use business key (orderNumber) — stable across entity lifecycle
        return Objects.equals(orderNumber, order.orderNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNumber); // consistent with equals
    }

    @Override
    public String toString() {
        return "Order{orderNumber='" + orderNumber + 
               "', amount=" + amount + "}";
        // Don't include lazy-loaded associations — triggers N+1!
    }
}

// Value Object (immutable, equality by content)
public record Money(BigDecimal amount, Currency currency) {
    // Java records auto-generate equals(), hashCode(), toString()
    // Based on ALL components — perfect for value objects
}
```

---

## Interview Questions and Answers

**Q1: What is the contract between equals() and hashCode()?**
> If two objects are equal according to `equals()`, they MUST have the same `hashCode()`. The reverse is not required — unequal objects MAY have the same hash code (collision). This contract is critical because HashMap uses hashCode to find the bucket and equals to confirm the match within that bucket.

**Q2: What happens if you override equals() but not hashCode()?**
> Hash-based collections (HashMap, HashSet, Hashtable) break. Two logically equal objects will have different hash codes (from Object's default — based on memory address). They'll end up in different buckets, so HashMap.get() returns null even for a key that "equals" an existing key.

**Q3: Why should you use getClass() instead of instanceof in equals()?**
> `instanceof` returns true for subclasses, which can break the symmetry contract. If `Employee.equals(Manager)` returns true (via instanceof), but `Manager.equals(Employee)` returns false (Manager checks for Manager-specific fields), symmetry is violated. Use `getClass()` for strict type matching. Exception: if the class is `final` or you're implementing a well-defined interface contract.

**Q4: What is the difference between shallow copy and deep copy?**
> Shallow copy (default `clone()`) copies field values — primitives are duplicated, but reference fields still point to the same objects. Deep copy recursively copies all referenced objects, creating completely independent copies. For deep copy, use copy constructors, serialization, or manual deep-clone logic.

**Q5: Why is finalize() deprecated?**
> It's unpredictable (no guarantee when or if it runs), expensive (objects with finalizers survive extra GC cycles), and error-prone (can resurrect objects, cause deadlocks). Use `try-with-resources` and `Cleaner` (Java 9+) instead.

---

## Follow-up Questions and Answers

**Q: Can two unequal objects have the same hashCode?**
> Yes, this is called a **hash collision**. hashCode() maps infinite possible objects to ~4 billion integers, so collisions are inevitable. HashMap handles collisions using linked lists (or trees when >8 nodes). A good hashCode() minimizes collisions for better performance.

**Q: Should equals() compare all fields?**
> No. Compare only fields that define **logical identity**. For example, an Employee's ID and name define who they are, but their current department doesn't change their identity. Also, never include mutable fields in hashCode if objects are stored in hash collections — changing them makes the object "invisible" in the collection.

**Q: What fields should be used in hashCode() for JPA entities?**
> Use the **business key** (natural unique identifier like email, order number, SSN) — NOT the database-generated `@Id`. The `@Id` is null before the entity is persisted, which would break HashSet/HashMap behavior if the entity is added before saving.

**Q: What is Objects.hash() vs manually computing hashCode?**
> `Objects.hash(fields...)` is convenient but creates a temporary array (autoboxing + array allocation). For performance-critical code, compute manually: `31 * hash + field.hashCode()`. The number 31 is used because it's an odd prime and the JVM can optimize `31 * i` to `(i << 5) - i`.

---

## Common Mistakes

1. **Not overriding hashCode() when overriding equals()**
   ```java
   // BROKEN: HashMap/HashSet will not work correctly
   @Override
   public boolean equals(Object o) { /* ... */ }
   // Missing hashCode()! ← BUG
   ```

2. **Using mutable fields in hashCode()**
   ```java
   Set<Employee> set = new HashSet<>();
   set.add(emp);
   emp.setName("New Name"); // hashCode changes!
   set.contains(emp); // false! Object is "lost" in the set
   ```

3. **Including lazy-loaded fields in toString() (JPA)**
   ```java
   @Override
   public String toString() {
       return "Order{items=" + items + "}"; // triggers lazy load → N+1 or LazyInitException
   }
   ```

4. **Using instanceof in equals() with inheritance**
   ```java
   // If parent.equals(child) is true but child.equals(parent) is false → BROKEN
   ```

5. **Relying on default clone() for deep copy**
   ```java
   Employee clone = (Employee) emp.clone(); // shallow!
   clone.getAddress().setCity("Boston"); // modifies ORIGINAL too!
   ```

---

## Best Practices

1. **Always override equals() AND hashCode() together.**
2. **Use `Objects.equals()` and `Objects.hash()`** for null-safe implementations.
3. **Use the same fields in both equals() and hashCode().**
4. **For JPA entities, use business keys** not generated IDs.
5. **Prefer copy constructors over clone()** — more readable, no `CloneNotSupportedException`.
6. **Use Java records** for simple value objects — auto-generates equals, hashCode, toString.
7. **Keep toString() informative but safe** — no sensitive data, no lazy-loaded collections.
8. **Make equals/hashCode fields immutable** when possible.

---

## Production Considerations

- **HashMap performance**: A bad hashCode() that returns the same value for all objects degrades HashMap from O(1) to O(n) — all entries go to one bucket.

- **Caching hashCode**: For immutable objects, cache the hashCode value (like `String` does internally). Avoids recomputation on every HashMap lookup.

- **Lombok `@EqualsAndHashCode`**: Generates equals/hashCode automatically. Use `@EqualsAndHashCode(of = {"id", "name"})` to control which fields are included. Beware of circular references with bidirectional relationships.

- **IDE generation**: All modern IDEs generate equals/hashCode. Always review the generated code — ensure correct fields are included and handle null safely.

- **Record classes (Java 16+)**: `record Employee(String name, int id) {}` — automatically implements equals, hashCode, toString using all components.

---

## Related Topics

- → [2. OOP Concepts](./02-oop-concepts.md)
- → [6. Collections Framework](./06-collections-framework.md)
- → [7. Collection Internals](./07-collection-internals.md)
- → [8. Generics](./08-generics.md)
- → [25. Reflection](./25-reflection.md)
