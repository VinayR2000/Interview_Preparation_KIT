# 10. Entity Relationships

## Theory

**Relationship Types:**

| Relationship | Example | Annotation |
|-------------|---------|-----------|
| One-to-One | User ↔ UserProfile | @OneToOne |
| One-to-Many | Department → Employees | @OneToMany |
| Many-to-One | Employee → Department | @ManyToOne |
| Many-to-Many | Student ↔ Course | @ManyToMany |

**Key Concepts:**
- `@JoinColumn` — Defines the FK column (owning side)
- `@JoinTable` — Defines the join table (Many-to-Many)
- `mappedBy` — Identifies the inverse/non-owning side
- `cascade` — Propagate operations to related entities
- `fetch` — EAGER (load immediately) or LAZY (load on access)
- `orphanRemoval` — Delete child when removed from parent collection

**Owning Side vs Inverse Side:**
- Owning side has the `@JoinColumn` (FK in its table)
- Inverse side has `mappedBy`
- **Only changes on the owning side are persisted to DB!**

**Fetch Types:**

| Association | Default Fetch |
|-------------|---------------|
| @ManyToOne | EAGER |
| @OneToOne | EAGER |
| @OneToMany | LAZY |
| @ManyToMany | LAZY |

**Cascade Types:**
- `PERSIST` — Parent saved → children saved
- `MERGE` — Parent updated → children updated
- `REMOVE` — Parent deleted → children deleted
- `REFRESH` — Parent refreshed → children refreshed
- `ALL` — All of the above + DETACH

---

## Internal Working

```
@OneToMany (LAZY):
Department loaded → employees = proxy (not loaded)
       ↓
department.getEmployees() called within transaction
       ↓
Proxy interceptor detects access
       ↓
SQL: SELECT * FROM employees WHERE department_id = ?
       ↓
Collection populated

@ManyToOne (EAGER by default):
Employee loaded
       ↓
SQL: SELECT e.*, d.* FROM employees e JOIN departments d ON e.dept_id = d.id
       ↓
Both employee and department available immediately

Cascade PERSIST:
department.addEmployee(new Employee("John"))
       ↓
em.persist(department)
       ↓
Hibernate detects cascade=PERSIST
       ↓
em.persist(employee) called automatically
       ↓
INSERT INTO departments ... 
INSERT INTO employees ...

orphanRemoval:
department.getEmployees().remove(employee)
       ↓
Hibernate detects orphan
       ↓
DELETE FROM employees WHERE id = ?
```

---

## Diagram

```
One-to-Many / Many-to-One:
┌──────────────────┐         ┌──────────────────┐
│   Department     │         │    Employee      │
│──────────────────│         │──────────────────│
│ id (PK)          │◀────┐   │ id (PK)          │
│ name             │     └───│ department_id(FK)│ ← Owning side
│                  │         │ name             │
│ @OneToMany       │         │ @ManyToOne       │
│ (mappedBy=       │         │ @JoinColumn      │
│  "department")   │         │                  │
└──────────────────┘         └──────────────────┘
   Inverse side                  Owning side

Many-to-Many:
┌──────────┐     ┌───────────────────┐     ┌──────────┐
│ Student  │     │ student_course    │     │  Course  │
│──────────│     │───────────────────│     │──────────│
│ id (PK)  │◀───│ student_id (FK)   │     │ id (PK)  │
│ name     │     │ course_id (FK)    │───▶│ name     │
└──────────┘     │ enrolled_date     │     └──────────┘
                 └───────────────────┘
                    @JoinTable

N+1 Problem:
Query 1: SELECT * FROM departments            (1 query)
         ↓ for each department...
Query 2: SELECT * FROM employees WHERE dept_id = 1  (+1)
Query 3: SELECT * FROM employees WHERE dept_id = 2  (+1)
Query 4: SELECT * FROM employees WHERE dept_id = 3  (+1)
...                                              = N+1 queries!

Solution — JOIN FETCH:
SELECT d FROM Department d JOIN FETCH d.employees  (1 query!)
```

---

## Code

```java
// === One-to-Many / Many-to-One ===
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Employee> employees = new ArrayList<>();

    // Helper methods (maintain both sides!)
    public void addEmployee(Employee employee) {
        employees.add(employee);
        employee.setDepartment(this);
    }

    public void removeEmployee(Employee employee) {
        employees.remove(employee);
        employee.setDepartment(null);
    }
}

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)  // Override EAGER default!
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;
}

// === One-to-One ===
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "profile_id", unique = true)
    private UserProfile profile;
}

@Entity
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bio;
    private String avatarUrl;

    @OneToOne(mappedBy = "profile")
    private User user;
}

// === Many-to-Many ===
@Entity
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "student_courses",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> courses = new HashSet<>();

    public void enrollIn(Course course) {
        courses.add(course);
        course.getStudents().add(this);
    }

    public void dropCourse(Course course) {
        courses.remove(course);
        course.getStudents().remove(this);
    }
}

@Entity
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @ManyToMany(mappedBy = "courses")
    private Set<Student> students = new HashSet<>();
}

// === Solving N+1 Problem ===
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // JOIN FETCH — single query loads departments + employees
    @Query("SELECT d FROM Department d JOIN FETCH d.employees")
    List<Department> findAllWithEmployees();

    // Entity Graph — declarative fetch strategy
    @EntityGraph(attributePaths = {"employees"})
    List<Department> findAll();

    // Named Entity Graph
    @EntityGraph(value = "Department.withEmployees")
    Optional<Department> findById(Long id);
}

@Entity
@NamedEntityGraph(
        name = "Department.withEmployees",
        attributeNodes = @NamedAttributeNode("employees")
)
public class Department { ... }

// === Pagination with relationships ===
@Query("SELECT d FROM Department d")
@EntityGraph(attributePaths = {"employees"})
Page<Department> findAllPaged(Pageable pageable);

// === Projections to avoid loading full entities ===
public interface EmployeeSummary {
    Long getId();
    String getName();
    String getDepartmentName();  // Derived from join
}

@Query("SELECT e.id as id, e.name as name, d.name as departmentName " +
       "FROM Employee e JOIN e.department d")
List<EmployeeSummary> findAllSummaries();
```

---

## Dry Run

**Scenario**: N+1 problem demonstration

```java
// Code:
List<Department> departments = departmentRepository.findAll(); // Default query
for (Department dept : departments) {
    System.out.println(dept.getName() + ": " + dept.getEmployees().size());
}
```

```
SQL executed:
1. SELECT * FROM departments;              ← 1 query (returns 5 departments)
2. SELECT * FROM employees WHERE department_id = 1;   ← triggered by getEmployees()
3. SELECT * FROM employees WHERE department_id = 2;
4. SELECT * FROM employees WHERE department_id = 3;
5. SELECT * FROM employees WHERE department_id = 4;
6. SELECT * FROM employees WHERE department_id = 5;
Total: 6 queries (1 + N where N=5)

With JOIN FETCH:
1. SELECT d.*, e.* FROM departments d LEFT JOIN employees e ON d.id = e.department_id;
Total: 1 query!
```

**Scenario**: Cascade PERSIST

```java
Department dept = new Department("Engineering");
dept.addEmployee(new Employee("Alice"));
dept.addEmployee(new Employee("Bob"));
departmentRepository.save(dept);
```

```
SQL:
1. INSERT INTO departments (name) VALUES ('Engineering') → id=1
2. INSERT INTO employees (name, department_id) VALUES ('Alice', 1)
3. INSERT INTO employees (name, department_id) VALUES ('Bob', 1)

All from a single save() call — cascade propagated persist to children.
```

---

## Complexity

| Operation | Queries |
|-----------|---------|
| Load parent only | 1 query |
| Load parent + lazy children (access) | 1 + 1 per access |
| N+1 problem (N parents) | 1 + N queries |
| JOIN FETCH | 1 query (but larger result set) |
| Entity Graph | 1 query |
| Batch fetching (hibernate.default_batch_fetch_size) | 1 + ⌈N/batch_size⌉ queries |

---

## Real Project Usage

```java
// E-commerce: Order → OrderItems
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public BigDecimal getTotal() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer quantity;
    private BigDecimal unitPrice;

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
```

---

## Interview Questions

1. **What is the N+1 problem? How do you solve it?**
   - Loading a list of entities (1 query) then accessing each entity's lazy association triggers N additional queries. Solutions: JOIN FETCH in JPQL, @EntityGraph, @BatchSize, or projections/DTOs.

2. **What is the difference between LAZY and EAGER fetching?**
   - LAZY: Association loaded only when accessed (proxy). EAGER: Association loaded immediately with parent query. Default: @ManyToOne/@OneToOne = EAGER, @OneToMany/@ManyToMany = LAZY. Always prefer LAZY.

3. **What is the owning side of a relationship? Why does it matter?**
   - The side with the foreign key column (without `mappedBy`). Only changes to the owning side are persisted to DB. Non-owning side uses `mappedBy` and is read-only for relationship state.

4. **What is cascade? Explain each type.**
   - Propagates operations from parent to child. PERSIST: Save child with parent. MERGE: Update child with parent. REMOVE: Delete child with parent. REFRESH: Reload child with parent. ALL: All of the above. Only use on parent (OneToMany) side.

5. **What is orphanRemoval? How is it different from CascadeType.REMOVE?**
   - orphanRemoval=true: Deletes child when removed from parent's collection (even without deleting parent). CascadeType.REMOVE: Only deletes children when parent is deleted. Orphan removal handles `parent.getItems().remove(item)`.

6. **What is LazyInitializationException? How do you fix it?**
   - Thrown when accessing an unloaded lazy proxy outside an open session/transaction. Fixes: JOIN FETCH in query, @EntityGraph, @Transactional on calling method, DTO projections. Avoid: Open Session in View (anti-pattern).

7. **When would you use Many-to-Many vs a join entity?**
   - @ManyToMany: Simple relationship with no extra attributes. Join entity (@Entity with two @ManyToOne): When the relationship has additional data (e.g., quantity, date, role). Most real-world M:N use join entities.

8. **How does mappedBy work?**
   - Declares the non-owning (inverse) side of a bidirectional relationship. Points to the field name on the owning side. E.g., `@OneToMany(mappedBy = "order")` means OrderItem.order field owns the FK.

9. **Why should @ManyToOne default be changed to LAZY?**
   - Default is EAGER, meaning every time you load the child, the parent is also loaded (extra JOIN/query). For collections of children, this causes N+1 or unnecessary data loading. Set `fetch = FetchType.LAZY`.

10. **How do Entity Graphs work?**
    - Define which associations to fetch eagerly for a specific query. `@EntityGraph(attributePaths = {"items", "customer"})` on repository method. Overrides LAZY setting for that specific query without affecting global fetch strategy.

---

## Follow-up Questions

1. **After Q1**: "What is batch fetching? How does it compare to JOIN FETCH?"
   → `@BatchSize(size=20)` loads related entities in batches. JOIN FETCH loads in one query but can multiply the result set (Cartesian product with multiple collections).

2. **After Q5**: "If I remove an item from a list with orphanRemoval=true, is it immediately deleted?"
   → No. Deleted at flush time (transaction commit). Until then, it's marked for removal.

3. **After Q6**: "What are all the solutions for LazyInitializationException?"
   → JOIN FETCH in query, Entity Graphs, `@Transactional` on calling method, Open Session in View (anti-pattern), DTOs.

4. **After Q7**: "When should you use a join entity over @ManyToMany?"
   → When the relationship has extra attributes (e.g., enrollment date, grade). Use a join entity with two @ManyToOne.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Not maintaining both sides of bidirectional | Only owning side persists; inconsistent in-memory | Use addX()/removeX() helpers |
| CascadeType.ALL on @ManyToOne | Deleting child deletes parent! | Only cascade on parent (OneToMany) |
| EAGER on @OneToMany | Loads entire collection always | Use LAZY + fetch when needed |
| toString() including lazy collections | Triggers load or throws exception | Exclude associations from toString |
| equals/hashCode on entity id | Breaks before persist (id is null) | Use business key or constant hashCode |
| @ManyToMany with List | Hibernate deletes all + re-inserts on change | Use Set instead |
| No index on FK column | Slow joins | Add index or use @JoinColumn with explicit index |

---

## Best Practices

1. **Always use LAZY fetching** — fetch explicitly when needed
2. **Maintain both sides** of bidirectional relationships with helper methods
3. **Use Set for @ManyToMany** — better performance than List
4. **Cascade only from parent to child** — never child to parent
5. **Use orphanRemoval** for true parent-child composition
6. **JOIN FETCH or Entity Graphs** to solve N+1
7. **Use DTOs/projections** for read-only queries — avoids lazy issues
8. **@BatchSize** as a global safety net for unexpected N+1
9. **Avoid deep nesting** — Order → Items → Product → Category (too many joins)
10. **Use join entities** for many-to-many with attributes

---

## Production Considerations

- **N+1 detection**: Use `spring.jpa.properties.hibernate.generate_statistics=true` in dev
- **Batch fetch size**: Set `spring.jpa.properties.hibernate.default_batch_fetch_size=20` globally
- **Cartesian product**: JOIN FETCH on multiple collections causes multiplication — use `Set` or separate queries
- **Pagination + JOIN FETCH**: HHH000104 warning — pagination in memory! Use separate queries.
- **Cascade deletes**: Consider soft deletes instead for audit trails
- **FK indexes**: Ensure all FK columns are indexed for join performance
- **Monitoring**: Log slow queries > 100ms in production

---

## Related Topics

- → [9. Spring Data JPA](#) (entities, persistence context)
- → [11. Spring Data JPA Repository](#) (query methods, JPQL)
- → [12. Transactions](#) (lazy loading requires open transaction)
- → [14. Database Connection Pool](#) (connection per transaction)
