# 9. Comparable vs Comparator

---

## Theory

Java provides two interfaces for defining object ordering:

- **`Comparable<T>`** — defines the **natural ordering** of a class. Implemented by the class itself.
- **`Comparator<T>`** — defines **custom/external ordering**. Implemented separately from the class.

### Core Difference

| Aspect | Comparable | Comparator |
|--------|-----------|------------|
| Package | `java.lang` | `java.util` |
| Method | `compareTo(T o)` | `compare(T o1, T o2)` |
| Ordering | Natural (single) | Custom (multiple) |
| Modifies class | Yes — class implements it | No — external |
| Functional interface | No (well, technically yes) | Yes — `@FunctionalInterface` |
| Usage | `Collections.sort(list)` | `Collections.sort(list, comparator)` |

### Return Value Contract

Both methods return an `int`:
- **Negative** → first object is LESS than second
- **Zero** → objects are EQUAL
- **Positive** → first object is GREATER than second

---

## Internal Working

### Comparable — Natural Ordering

```java
public interface Comparable<T> {
    int compareTo(T o);
}
```

When a class implements `Comparable`, it defines its **one and only** natural order. This is used by:
- `Collections.sort(list)` / `list.sort(null)`
- `TreeSet` / `TreeMap` (default ordering)
- `Arrays.sort()`
- `Stream.sorted()`

### Comparator — Custom Ordering

```java
@FunctionalInterface
public interface Comparator<T> {
    int compare(T o1, T o2);
    
    // Default methods (Java 8+):
    default Comparator<T> reversed() { ... }
    default Comparator<T> thenComparing(Comparator<? super T> other) { ... }
    
    // Static factory methods:
    static <T, U extends Comparable<? super U>> Comparator<T> comparing(
        Function<? super T, ? extends U> keyExtractor) { ... }
    static <T> Comparator<T> naturalOrder() { ... }
    static <T> Comparator<T> reverseOrder() { ... }
    static <T> Comparator<T> nullsFirst(Comparator<? super T> comparator) { ... }
    static <T> Comparator<T> nullsLast(Comparator<? super T> comparator) { ... }
}
```

---

## Diagram

```
Comparable vs Comparator Decision:

┌────────────────────────────────────────────┐
│ Need to sort objects?                       │
└────────────────┬───────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
   ONE natural       MULTIPLE custom
   ordering?         orderings needed?
        │                 │
        ▼                 ▼
   Comparable         Comparator
   (inside class)     (outside class)
        │                 │
        ▼                 ▼
   compareTo()        compare()
   
   
Sort Delegation Flow:

Collections.sort(list)
    │
    ├── list has Comparable elements? → uses compareTo()
    │
    └── Collections.sort(list, comparator) → uses compare()
         │
         └── TreeSet(comparator) → uses compare() for ordering
```

---

## Code Examples

### Comparable Implementation

```java
public class Employee implements Comparable<Employee> {
    private String name;
    private double salary;
    private int age;
    
    public Employee(String name, double salary, int age) {
        this.name = name;
        this.salary = salary;
        this.age = age;
    }
    
    // Natural ordering: by name (alphabetical)
    @Override
    public int compareTo(Employee other) {
        return this.name.compareTo(other.name);
    }
    
    // getters, toString...
    @Override
    public String toString() {
        return name + "($" + salary + ", age:" + age + ")";
    }
}

// Usage
List<Employee> employees = new ArrayList<>(List.of(
    new Employee("Charlie", 50000, 30),
    new Employee("Alice", 70000, 25),
    new Employee("Bob", 60000, 35)
));

Collections.sort(employees);  // uses compareTo → sorted by name
// [Alice($70000, age:25), Bob($60000, age:35), Charlie($50000, age:30)]
```

### Comparator Implementations

```java
// Sort by salary
Comparator<Employee> bySalary = new Comparator<Employee>() {
    @Override
    public int compare(Employee e1, Employee e2) {
        return Double.compare(e1.getSalary(), e2.getSalary());
    }
};

// Lambda version
Comparator<Employee> bySalaryLambda = (e1, e2) -> 
    Double.compare(e1.getSalary(), e2.getSalary());

// Method reference version (Java 8+)
Comparator<Employee> bySalaryModern = Comparator.comparingDouble(Employee::getSalary);

// Sort by age
Comparator<Employee> byAge = Comparator.comparingInt(Employee::getAge);

// Sort by name (descending)
Comparator<Employee> byNameDesc = Comparator.comparing(Employee::getName).reversed();

// Usage
employees.sort(bySalary);          // sorted by salary ascending
employees.sort(byAge.reversed());  // sorted by age descending
```

### Multi-field Sorting (Chaining)

```java
// Sort by department, then by salary (descending), then by name
Comparator<Employee> complexSort = Comparator
    .comparing(Employee::getDepartment)
    .thenComparing(Comparator.comparingDouble(Employee::getSalary).reversed())
    .thenComparing(Employee::getName);

employees.sort(complexSort);

// Equivalent with streams
List<Employee> sorted = employees.stream()
    .sorted(Comparator.comparing(Employee::getDepartment)
                      .thenComparingDouble(Employee::getSalary)
                      .reversed()
                      .thenComparing(Employee::getName))
    .collect(Collectors.toList());
```

### Null-safe Comparators

```java
// Handle null values
Comparator<Employee> nullSafe = Comparator.nullsLast(
    Comparator.comparing(Employee::getName)
);

// Handle null fields
Comparator<Employee> nullFieldSafe = Comparator.comparing(
    Employee::getDepartment,
    Comparator.nullsFirst(Comparator.naturalOrder())
);

List<Employee> list = Arrays.asList(
    new Employee("Alice", null),   // null department
    new Employee("Bob", "IT"),
    null,                          // null employee
    new Employee("Charlie", "HR")
);

list.sort(Comparator.nullsLast(
    Comparator.comparing(Employee::getDepartment, 
        Comparator.nullsFirst(Comparator.naturalOrder()))
));
```

### TreeSet and TreeMap with Comparator

```java
// TreeSet with natural ordering (Comparable)
TreeSet<Employee> byName = new TreeSet<>();  // uses compareTo

// TreeSet with custom ordering
TreeSet<Employee> bySalarySet = new TreeSet<>(
    Comparator.comparingDouble(Employee::getSalary)
);

// TreeMap with custom key ordering
TreeMap<String, Employee> caseInsensitive = new TreeMap<>(
    String.CASE_INSENSITIVE_ORDER
);

// Reverse order
TreeSet<Integer> descending = new TreeSet<>(Comparator.reverseOrder());
descending.addAll(List.of(3, 1, 4, 1, 5));
// [5, 4, 3, 1]
```

---

## Dry Run

### Sorting with compareTo

```java
List<Integer> nums = Arrays.asList(5, 2, 8, 1, 9);
Collections.sort(nums);

// Integer implements Comparable<Integer>
// Integer.compareTo uses Integer.compare(this.value, other.value)

// Merge sort steps (TimSort internally):
// [5, 2, 8, 1, 9]
// Compares: 5.compareTo(2) → positive → swap
// [2, 5, 8, 1, 9]
// Compares: 8.compareTo(1) → positive → swap
// Continue until sorted: [1, 2, 5, 8, 9]
```

### Custom Comparator Sorting

```java
List<String> names = Arrays.asList("Charlie", "alice", "Bob");

// Case-insensitive sort
names.sort(String.CASE_INSENSITIVE_ORDER);

// String.CASE_INSENSITIVE_ORDER.compare("Charlie", "alice"):
//   "charlie".compareTo("alice") → 'c' - 'a' = 2 (positive)
//   So "Charlie" > "alice" case-insensitively

// Result: ["alice", "Bob", "Charlie"]
```

---

## Complexity

| Operation | Time Complexity |
|-----------|----------------|
| `Collections.sort()` / `List.sort()` | O(n log n) — TimSort |
| `TreeSet.add()` | O(log n) — uses compare/compareTo |
| `TreeMap.put()` | O(log n) — uses compare/compareTo |
| Single comparison | O(1) for primitives, O(k) for strings (k = length) |
| Multi-field comparison | O(1) per comparison (short-circuit on first difference) |

---

## Real Project Usage

### Domain Object with Natural Ordering

```java
public class Version implements Comparable<Version> {
    private final int major;
    private final int minor;
    private final int patch;
    
    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(this.major, other.major);
        if (result != 0) return result;
        result = Integer.compare(this.minor, other.minor);
        if (result != 0) return result;
        return Integer.compare(this.patch, other.patch);
    }
}
```

### Sorting in REST APIs

```java
@GetMapping("/employees")
public List<Employee> getEmployees(@RequestParam String sortBy, 
                                    @RequestParam String direction) {
    Comparator<Employee> comparator = switch (sortBy) {
        case "name"   -> Comparator.comparing(Employee::getName);
        case "salary" -> Comparator.comparingDouble(Employee::getSalary);
        case "age"    -> Comparator.comparingInt(Employee::getAge);
        default       -> Comparator.comparing(Employee::getId);
    };
    
    if ("desc".equalsIgnoreCase(direction)) {
        comparator = comparator.reversed();
    }
    
    return employees.stream().sorted(comparator).collect(Collectors.toList());
}
```

### Priority Queue Ordering

```java
// Min-heap by default (natural ordering)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max-heap
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());

// Custom priority — process high-priority tasks first
PriorityQueue<Task> taskQueue = new PriorityQueue<>(
    Comparator.comparingInt(Task::getPriority).reversed()
              .thenComparing(Task::getCreatedAt)
);
```

---

## Interview Questions and Answers

### Q1: When would you use Comparable vs Comparator?

**A:**
- **Comparable** — when the class has ONE obvious natural ordering (e.g., `String` → alphabetical, `Integer` → numeric, `Date` → chronological). The class itself defines it.
- **Comparator** — when you need multiple sort orders, or you can't modify the class, or you need different ordering in different contexts.

**Rule of thumb:** If there's only one sensible way to order objects, use `Comparable`. If ordering depends on context, use `Comparator`.

### Q2: What happens if compareTo is inconsistent with equals?

**A:** If `a.compareTo(b) == 0` but `a.equals(b) == false`, collections like `TreeSet` will consider them equal (won't store both), while `HashSet` will store both. This causes confusing behavior:

```java
TreeSet<BigDecimal> tree = new TreeSet<>();
tree.add(new BigDecimal("1.0"));
tree.add(new BigDecimal("1.00"));
// tree.size() = 1  (compareTo returns 0)

HashSet<BigDecimal> hash = new HashSet<>();
hash.add(new BigDecimal("1.0"));
hash.add(new BigDecimal("1.00"));
// hash.size() = 2  (equals returns false — different scale)
```

### Q3: How do you sort by multiple fields?

**A:** Use `Comparator.comparing().thenComparing()`:

```java
Comparator<Employee> multiSort = Comparator
    .comparing(Employee::getDepartment)
    .thenComparingDouble(Employee::getSalary)
    .thenComparing(Employee::getName);
```

### Q4: Can a class implement Comparable AND be sorted with a Comparator?

**A:** Yes. `Comparable` defines the default ordering, but you can always override it with a `Comparator`:

```java
// Uses natural ordering (Comparable)
Collections.sort(employees);

// Overrides with Comparator
Collections.sort(employees, Comparator.comparingDouble(Employee::getSalary));
```

`TreeSet` and `TreeMap` will use the `Comparator` if provided, ignoring `Comparable`.

---

## Follow-up Questions and Answers

### Q: What is the contract for compareTo?

**A:**
1. **Antisymmetry:** `sgn(x.compareTo(y)) == -sgn(y.compareTo(x))`
2. **Transitivity:** If `x.compareTo(y) > 0` and `y.compareTo(z) > 0`, then `x.compareTo(z) > 0`
3. **Consistency:** If `x.compareTo(y) == 0`, then `sgn(x.compareTo(z)) == sgn(y.compareTo(z))` for all z
4. **Recommended:** `(x.compareTo(y) == 0) == (x.equals(y))`

### Q: Why does Comparator have so many static/default methods in Java 8+?

**A:** To enable fluent, composable comparisons without boilerplate:

```java
// Old way (verbose):
Comparator<Employee> comp = (e1, e2) -> {
    int result = e1.getDepartment().compareTo(e2.getDepartment());
    if (result != 0) return result;
    return Double.compare(e2.getSalary(), e1.getSalary());
};

// Java 8+ way (fluent):
Comparator<Employee> comp = Comparator
    .comparing(Employee::getDepartment)
    .thenComparingDouble(Employee::getSalary)
    .reversed();
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| `return this.salary - other.salary` | Integer overflow for large values | Use `Integer.compare()` or `Double.compare()` |
| Inconsistent compareTo/equals | TreeSet and HashSet behave differently | Ensure consistency |
| Not handling null in comparator | NullPointerException | Use `Comparator.nullsFirst/nullsLast` |
| Mutable sort keys | Object moves in TreeSet/TreeMap but isn't re-sorted | Use immutable fields as sort keys |
| Forgetting `Comparator.reversed()` | Writing `compare(b, a)` which is error-prone | Use `.reversed()` |

---

## Best Practices

1. **Use `Integer.compare()`, `Double.compare()`** — never subtract for comparison (overflow risk)
2. **Keep compareTo consistent with equals** — document if it isn't
3. **Use Comparator factory methods** — `comparing()`, `comparingInt()`, `thenComparing()`
4. **Handle nulls explicitly** — `nullsFirst()` / `nullsLast()`
5. **Make sort keys immutable** — mutable keys in TreeSet/TreeMap break ordering
6. **Prefer lambda/method reference** over anonymous class for Comparator
7. **Document natural ordering** — make it obvious what `compareTo` sorts by

---

## Production Considerations

- **Database sorting vs in-memory:** For large datasets, prefer SQL `ORDER BY` over Java sorting
- **Stability:** Java's `Arrays.sort()` (for objects) and `List.sort()` are **stable** (equal elements maintain relative order)
- **Thread safety:** Comparators should be stateless and thread-safe — use static final instances
- **Serialization:** If used with serializable collections (e.g., `TreeSet`), ensure Comparator is also `Serializable`

---

## Related Topics

- [6. Collections Framework](./06-collections-framework.md) — TreeSet, TreeMap use comparisons
- [8. Generics](./08-generics.md) — `Comparable<T>`, bounded types
- [10. Java 8 Features](./10-java8-features.md) — lambda comparators, method references
- [11. Stream API](./11-stream-api.md) — `sorted()` with Comparator
