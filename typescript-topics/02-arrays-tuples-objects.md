# Arrays, Tuples, and Object Types

## Arrays ⭐⭐⭐

### Array Type Syntax
```typescript
// Two equivalent ways to declare arrays
let numbers: number[] = [1, 2, 3];
let names: Array<string> = ["Alice", "Bob"];   // Generic syntax

// Inferred
let fruits = ["apple", "banana"];   // string[]
let mixed = [1, "hello", true];     // (string | number | boolean)[]

// Empty arrays need explicit type
let users: User[] = [];             // Without annotation: never[]
let ids: number[] = [];
```

### Readonly Arrays
```typescript
// Cannot modify the array
const colors: readonly string[] = ["red", "green", "blue"];
// colors.push("yellow");   // ❌ Error: push does not exist on readonly
// colors[0] = "orange";    // ❌ Error: Index signature is readonly

// ReadonlyArray generic form
const nums: ReadonlyArray<number> = [1, 2, 3];

// as const makes deeply readonly
const rgb = [255, 128, 0] as const;
// Type: readonly [255, 128, 0] — literal tuple
```

### Array Methods — Type Inference
```typescript
const users: User[] = [
  { id: 1, name: "Alice", age: 30 },
  { id: 2, name: "Bob", age: 25 },
];

// map returns transformed array with inferred type
const names = users.map(u => u.name);           // string[]
const ages = users.map(u => u.age);             // number[]

// filter with type guard
const adults = users.filter(u => u.age >= 18);  // User[]

// find returns T | undefined
const bob = users.find(u => u.name === "Bob");  // User | undefined
if (bob) {
  console.log(bob.name);  // Safe: narrowed to User
}

// reduce with explicit accumulator type
const totalAge = users.reduce((sum, u) => sum + u.age, 0);  // number
```

---

## Tuples ⭐⭐

### What is a Tuple?
Fixed-length array where each position has a specific type.

```typescript
// Tuple declaration
let user: [number, string] = [1, "Vinay"];
let coordinate: [number, number] = [10, 20];
let entry: [string, number, boolean] = ["name", 25, true];

// Accessing elements — type-safe
const id = user[0];      // number
const name = user[1];    // string
// const x = user[2];    // ❌ Error: Tuple has no element at index 2
```

### Named Tuples (TypeScript 4.0+)
```typescript
type Point = [x: number, y: number];
type HttpResponse = [statusCode: number, body: string];

const point: Point = [10, 20];
const response: HttpResponse = [200, '{"data": "ok"}'];
```

### Optional and Rest Elements
```typescript
// Optional elements
type StringNumberOptionalBool = [string, number, boolean?];
const a: StringNumberOptionalBool = ["hi", 1];       // ✅
const b: StringNumberOptionalBool = ["hi", 1, true]; // ✅

// Rest elements
type StringAndNumbers = [string, ...number[]];
const c: StringAndNumbers = ["hello", 1, 2, 3, 4];  // ✅

// Readonly tuples
type ReadonlyPair = readonly [number, string];
```

### Array vs Tuple

| Feature | Array | Tuple |
|---------|-------|-------|
| Length | Variable | Fixed |
| Element types | Same type | Different types per position |
| Use case | Collections | Fixed structure (coordinates, key-value) |
| Type safety | Per element | Per position |

### Common Tuple Patterns
```typescript
// Function returning multiple values (like destructuring)
function getMinMax(arr: number[]): [number, number] {
  return [Math.min(...arr), Math.max(...arr)];
}
const [min, max] = getMinMax([3, 1, 4, 1, 5]);

// React useState hook returns a tuple
// const [count, setCount] = useState(0);  // [number, (n: number) => void]

// Map entries
const map = new Map<string, number>();
for (const [key, value] of map.entries()) {
  // key: string, value: number
}
```

---

## Object Types ⭐⭐⭐

### Inline Object Types
```typescript
// Inline type annotation
let user: { id: number; name: string; email: string } = {
  id: 1,
  name: "Vinay",
  email: "vinay@example.com"
};

// Function parameter with object type
function createUser(config: { name: string; age: number }): void {
  console.log(config.name, config.age);
}
```

### Optional Properties
```typescript
let user: {
  id: number;
  name: string;
  email?: string;   // Optional — may be undefined
} = {
  id: 1,
  name: "Vinay"    // email not required
};

// Accessing optional properties
console.log(user.email?.toUpperCase());  // Safe with optional chaining
```

### Readonly Properties
```typescript
let user: {
  readonly id: number;
  name: string;
} = { id: 1, name: "Vinay" };

user.name = "Updated";   // ✅ OK
// user.id = 2;          // ❌ Error: Cannot assign to 'id' because it is readonly
```

### Index Signatures
```typescript
// When you don't know all property names ahead of time
interface StringDictionary {
  [key: string]: string;
}

const headers: StringDictionary = {
  "Content-Type": "application/json",
  "Authorization": "Bearer token123"
};

// Mixed: known + dynamic properties
interface Config {
  name: string;
  version: number;
  [key: string]: string | number;  // Must be compatible with known properties
}
```

### Excess Property Checking
```typescript
interface User {
  name: string;
  age: number;
}

// Direct literal — excess properties caught
// const u: User = { name: "V", age: 25, email: "v@x.com" }; // ❌ Error!

// Through variable — excess properties allowed (structural typing)
const obj = { name: "V", age: 25, email: "v@x.com" };
const u: User = obj;  // ✅ OK — has all required properties
```

---

## Interfaces ⭐⭐⭐

### Defining Interfaces
```typescript
interface User {
  id: number;
  name: string;
  email: string;
  age?: number;              // Optional
  readonly createdAt: Date;  // Readonly
}

// Usage
const user: User = {
  id: 1,
  name: "Vinay",
  email: "vinay@example.com",
  createdAt: new Date()
};
```

### Method Signatures in Interfaces
```typescript
interface UserService {
  getUser(id: number): User;
  getUsers(): User[];
  createUser(user: Omit<User, "id" | "createdAt">): User;
  updateUser(id: number, data: Partial<User>): User;
  deleteUser(id: number): boolean;
}

// Function type in interface
interface MathOperation {
  (a: number, b: number): number;
}

const add: MathOperation = (a, b) => a + b;
const multiply: MathOperation = (a, b) => a * b;
```

### Extending Interfaces
```typescript
interface Person {
  name: string;
  age: number;
}

interface Employee extends Person {
  department: string;
  salary: number;
}

// Multiple inheritance
interface Manager extends Employee {
  reports: Employee[];
}

// Extending multiple interfaces
interface Admin extends Person, Serializable {
  permissions: string[];
}
```

### Declaration Merging (Interface-Only Feature)
```typescript
// Interfaces with same name merge automatically
interface Window {
  myCustomProp: string;
}

// Now Window has myCustomProp in addition to all standard properties
// This is how you augment third-party types

// Types CANNOT merge
// type Window = { myCustomProp: string; }  // ❌ Error: Duplicate identifier
```

---

## Type Aliases

### Defining Types
```typescript
type UserId = number;
type UserName = string;
type Status = "active" | "inactive" | "pending";

type User = {
  id: UserId;
  name: UserName;
  status: Status;
};

// Function types
type Callback = (data: string) => void;
type AsyncCallback = (data: string) => Promise<void>;
type Predicate<T> = (item: T) => boolean;
```

---

## Interface vs Type ⭐⭐⭐ (Interview Question)

| Feature | Interface | Type Alias |
|---------|-----------|-----------|
| Object shapes | ✅ | ✅ |
| Extend/inherit | `extends` keyword | Intersection `&` |
| Union types | ❌ | ✅ `string \| number` |
| Primitive aliases | ❌ | ✅ `type ID = number` |
| Tuple types | ❌ | ✅ `type Pair = [string, number]` |
| Declaration merging | ✅ | ❌ |
| Implements | ✅ | ✅ |
| Computed properties | ❌ | ✅ (mapped types) |
| Performance | Slightly better (cached) | Computed each time |

### When to Use Which
```typescript
// ✅ Use INTERFACE for:
// - Object shapes (DTOs, models, service contracts)
// - When you might need declaration merging
// - Class contracts (implements)
interface User {
  id: number;
  name: string;
}

// ✅ Use TYPE for:
// - Unions, intersections, tuples
// - Primitive aliases
// - Complex type transformations
type Status = "active" | "inactive";
type Result<T> = { success: true; data: T } | { success: false; error: string };
type Pair<A, B> = [A, B];
```

---

## Key Interview Questions

**Q: What's the difference between `interface` and `type` in TypeScript?**
> Both can define object shapes. Interfaces support declaration merging and `extends` keyword. Types support unions, intersections, primitives, and tuples. Use interfaces for object contracts/models, types for unions and complex transformations. Interfaces are slightly better for performance as they're cached by the compiler.

**Q: What's a tuple and when would you use it?**
> A tuple is a fixed-length array where each position has a specific type. Use for: function return values with multiple types (like `[error, result]`), React hooks (`useState` returns `[value, setter]`), or coordinates `[x, y]`. Unlike arrays, each index has its own type.

**Q: Explain excess property checking in TypeScript.**
> When you assign an object literal directly to a typed variable, TypeScript checks for excess (unexpected) properties. This catches typos and unintended properties. However, when assigning through a variable, only structural compatibility is checked — extra properties are allowed. This is by design: direct literals are likely mistakes, but existing objects may legitimately have extra properties.

**Q: Can you use index signatures with interfaces?**
> Yes. Index signatures like `[key: string]: value_type` allow dynamic property names. All explicitly declared properties must be compatible with the index signature type. This is useful for dictionaries, configuration objects, and when interfacing with dynamic data.
