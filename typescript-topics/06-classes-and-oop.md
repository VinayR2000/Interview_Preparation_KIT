# TypeScript Classes and OOP

## Classes — TypeScript vs Java

Since you already know Java OOP, this focuses on TypeScript differences.

### Basic Class
```typescript
class Employee {
  // Properties must be declared
  id: number;
  name: string;
  department: string;

  constructor(id: number, name: string, department: string) {
    this.id = id;
    this.name = name;
    this.department = department;
  }

  getInfo(): string {
    return `${this.name} (${this.department})`;
  }
}
```

### Parameter Properties (TypeScript Shorthand) ⭐⭐⭐
```typescript
// Instead of declaring + assigning in constructor:
class Employee {
  constructor(
    public id: number,
    public name: string,
    private salary: number,
    protected department: string,
    readonly createdAt: Date = new Date()
  ) {}
  // Properties are automatically declared AND assigned!
}

// Equivalent Java would need:
// private fields + constructor assignments + getters/setters = 30+ lines
// TypeScript: 7 lines
```

---

## Access Modifiers

| Modifier | TypeScript | Java Equivalent | Enforcement |
|----------|-----------|----------------|-------------|
| `public` | Default, accessible everywhere | Same | Compile-time ONLY |
| `private` | Only within class | Same | Compile-time ONLY |
| `protected` | Class + subclasses | Same | Compile-time ONLY |
| `readonly` | Cannot reassign after init | `final` field | Compile-time ONLY |

### Critical Difference from Java ⭐⭐⭐
```typescript
class Secret {
  private password: string = "abc123";
}

const s = new Secret();
// s.password;           // ❌ Compile-time error

// BUT at runtime (JavaScript):
(s as any).password;     // ✅ "abc123" — TypeScript privacy is NOT enforced at runtime!
s["password"];           // ✅ Also works at runtime

// For TRUE runtime privacy, use ES2022 private fields:
class TrueSecret {
  #password: string = "abc123";  // Truly private at runtime
}
```

---

## Inheritance

### extends
```typescript
class Animal {
  constructor(public name: string) {}

  move(distance: number): void {
    console.log(`${this.name} moved ${distance}m`);
  }
}

class Dog extends Animal {
  constructor(name: string, public breed: string) {
    super(name);  // Must call super() first
  }

  bark(): void {
    console.log("Woof!");
  }

  // Override parent method
  override move(distance: number): void {
    console.log("Running...");
    super.move(distance);
  }
}
```

### Abstract Classes
```typescript
abstract class Shape {
  abstract area(): number;         // Must be implemented
  abstract perimeter(): number;    // Must be implemented

  // Concrete method — inherited as-is
  describe(): string {
    return `Area: ${this.area()}, Perimeter: ${this.perimeter()}`;
  }
}

class Circle extends Shape {
  constructor(private radius: number) {
    super();
  }

  area(): number {
    return Math.PI * this.radius ** 2;
  }

  perimeter(): number {
    return 2 * Math.PI * this.radius;
  }
}

// const s = new Shape();  // ❌ Cannot instantiate abstract class
const c = new Circle(5);
c.describe();  // Uses concrete method from abstract class
```

---

## Interfaces with Classes

### implements
```typescript
interface Serializable {
  serialize(): string;
}

interface Comparable<T> {
  compareTo(other: T): number;
}

class User implements Serializable, Comparable<User> {
  constructor(
    public id: number,
    public name: string,
    public age: number
  ) {}

  serialize(): string {
    return JSON.stringify({ id: this.id, name: this.name });
  }

  compareTo(other: User): number {
    return this.age - other.age;
  }
}
```

### Interface vs Abstract Class — When to Use

| Use Case | Interface | Abstract Class |
|----------|-----------|---------------|
| Define a contract/shape | ✅ | ✅ |
| Provide default implementation | ❌ | ✅ |
| Multiple inheritance | ✅ (implement many) | ❌ (extend one) |
| Runtime cost | None (erased) | Has runtime code |
| Constructor | ❌ | ✅ |
| Private/protected members | ❌ | ✅ |
| Access modifiers | ❌ | ✅ |

---

## Static Members

```typescript
class MathUtils {
  static PI = 3.14159;

  static add(a: number, b: number): number {
    return a + b;
  }

  // Static blocks (TypeScript 4.4+)
  static {
    console.log("MathUtils loaded");
  }
}

// Usage without instantiation
MathUtils.PI;
MathUtils.add(1, 2);
```

### Singleton Pattern
```typescript
class Database {
  private static instance: Database;

  private constructor(private connectionString: string) {}

  static getInstance(): Database {
    if (!Database.instance) {
      Database.instance = new Database("postgresql://localhost:5432/db");
    }
    return Database.instance;
  }

  query(sql: string): void {
    console.log(`Executing: ${sql}`);
  }
}

const db = Database.getInstance();
```

---

## Getters and Setters

```typescript
class Circle {
  constructor(private _radius: number) {}

  // Getter — accessed like a property
  get radius(): number {
    return this._radius;
  }

  // Setter — with validation
  set radius(value: number) {
    if (value <= 0) throw new Error("Radius must be positive");
    this._radius = value;
  }

  get area(): number {
    return Math.PI * this._radius ** 2;
  }

  get diameter(): number {
    return this._radius * 2;
  }
}

const c = new Circle(5);
console.log(c.radius);    // 5 (calls getter)
c.radius = 10;            // Calls setter
console.log(c.area);      // 314.159... (computed property)
```

---

## Structural Typing with Classes ⭐⭐⭐

This is the BIGGEST difference from Java:

```typescript
class Point {
  constructor(public x: number, public y: number) {}
}

class Coordinate {
  constructor(public x: number, public y: number) {}
}

// In TypeScript: these are COMPATIBLE (same structure)
const p: Point = new Coordinate(1, 2);  // ✅ Works!

// In Java: this would FAIL (different class names = incompatible)
// Point p = new Coordinate(1, 2);  // ❌ Compilation error in Java

// This means: interfaces and classes are interchangeable for type checking
interface HasXY {
  x: number;
  y: number;
}

const point: HasXY = new Point(1, 2);      // ✅
const coord: HasXY = new Coordinate(1, 2); // ✅
const plain: HasXY = { x: 1, y: 2 };      // ✅ Plain object also works!
```

### Implication for Dependency Injection
```typescript
// You don't NEED interfaces for DI — structural typing handles it
class EmailService {
  send(to: string, body: string): void { /* ... */ }
}

class MockEmailService {
  send(to: string, body: string): void { /* mock */ }
}

// Both work where EmailService is expected — same structure!
function notify(service: EmailService, user: User): void {
  service.send(user.email, "Hello!");
}

notify(new EmailService(), user);      // ✅
notify(new MockEmailService(), user);  // ✅ (same shape)
```

---

## Design Patterns in TypeScript

### Strategy Pattern
```typescript
interface SortStrategy<T> {
  sort(items: T[]): T[];
}

class QuickSort<T> implements SortStrategy<T> {
  sort(items: T[]): T[] { /* ... */ return items; }
}

class MergeSort<T> implements SortStrategy<T> {
  sort(items: T[]): T[] { /* ... */ return items; }
}

class Sorter<T> {
  constructor(private strategy: SortStrategy<T>) {}

  setStrategy(strategy: SortStrategy<T>): void {
    this.strategy = strategy;
  }

  sort(items: T[]): T[] {
    return this.strategy.sort(items);
  }
}
```

### Observer Pattern
```typescript
type Listener<T> = (data: T) => void;

class EventEmitter<Events extends Record<string, any>> {
  private listeners = new Map<keyof Events, Set<Listener<any>>>();

  on<K extends keyof Events>(event: K, listener: Listener<Events[K]>): void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(listener);
  }

  emit<K extends keyof Events>(event: K, data: Events[K]): void {
    this.listeners.get(event)?.forEach(listener => listener(data));
  }

  off<K extends keyof Events>(event: K, listener: Listener<Events[K]>): void {
    this.listeners.get(event)?.delete(listener);
  }
}

// Type-safe events
interface AppEvents {
  userLogin: { userId: number; timestamp: Date };
  orderPlaced: { orderId: string; total: number };
  error: { message: string; code: number };
}

const events = new EventEmitter<AppEvents>();
events.on("userLogin", (data) => {
  console.log(data.userId);  // number — fully typed!
});
events.emit("userLogin", { userId: 1, timestamp: new Date() });
```

---

## Key Interview Questions

**Q: How does TypeScript's class system differ from Java's?**
> Key differences: (1) Structural typing — compatible if same shape, regardless of class name. (2) Access modifiers are compile-time only — no runtime enforcement. (3) Parameter properties shorthand. (4) No method overloading (use overload signatures). (5) `readonly` instead of `final`. (6) ES2022 `#private` for true runtime privacy. (7) Multiple interface implementation but single class extension (same as Java).

**Q: What is structural typing and how does it affect classes?**
> TypeScript checks compatibility by structure (properties and methods), not by name. Two unrelated classes with the same properties are interchangeable. This means: you can pass any object with the right shape where a class type is expected — you don't need `implements`. This enables duck typing with type safety.

**Q: Should you use classes or interfaces for models/DTOs in TypeScript?**
> For data models (DTOs, API responses): use **interfaces**. They have zero runtime cost (erased), define only shape, and are more flexible. For services with behavior: use **classes**. They provide constructors, methods, DI integration (Angular), and encapsulation. Java developers often overuse classes in TypeScript — plain objects + interfaces are usually sufficient for data.

**Q: What's the difference between `private` and `#private`?**
> `private` keyword is compile-time only — TypeScript enforces it during development, but at runtime the property is fully accessible (it's just JavaScript). `#private` (ES2022 class fields) is enforced at runtime by the JavaScript engine — truly inaccessible from outside. Use `private` for most cases; `#private` when runtime security matters.
