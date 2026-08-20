# Union, Intersection, and Literal Types ⭐⭐⭐

## Union Types ⭐⭐⭐

### Concept
A union type means a value can be one of several types.

```
number | string → "this value is EITHER a number OR a string"
```

### Basic Usage
```typescript
// Simple union
let id: number | string;
id = 101;       // ✅
id = "ABC-101"; // ✅
// id = true;   // ❌ Error: boolean not in union

// Union in function parameters
function printId(id: number | string): void {
  console.log(`ID: ${id}`);
}
printId(42);        // ✅
printId("ABC-42");  // ✅

// Union in return types
function findUser(id: number): User | null {
  // Returns User if found, null otherwise
  return users.find(u => u.id === id) ?? null;
}
```

### Narrowing Union Types ⭐⭐⭐
You cannot use type-specific methods without narrowing first:

```typescript
function formatValue(value: string | number): string {
  // value.toUpperCase();  // ❌ Error: number doesn't have toUpperCase

  // Narrowing with typeof
  if (typeof value === "string") {
    return value.toUpperCase();  // ✅ TypeScript knows it's string here
  } else {
    return value.toFixed(2);     // ✅ TypeScript knows it's number here
  }
}
```

### Discriminated Unions ⭐⭐⭐
A powerful pattern for modeling states:

```typescript
// Each type has a common "discriminant" property
interface Loading {
  status: "loading";
}

interface Success {
  status: "success";
  data: User[];
}

interface Error {
  status: "error";
  message: string;
}

type ApiState = Loading | Success | Error;

function render(state: ApiState): string {
  switch (state.status) {
    case "loading":
      return "Loading...";
    case "success":
      return `Found ${state.data.length} users`;  // ✅ data accessible
    case "error":
      return `Error: ${state.message}`;           // ✅ message accessible
  }
}
```

This is heavily used in:
- Angular: NgRx action types
- React: useReducer actions
- API response handling

---

## Intersection Types ⭐⭐⭐

### Concept
An intersection type combines multiple types into one — the result has ALL properties.

```
A & B → "this value has ALL properties from A AND all properties from B"
```

### Basic Usage
```typescript
interface Person {
  name: string;
  age: number;
}

interface Employee {
  department: string;
  salary: number;
}

// Intersection: combines both
type FullEmployee = Person & Employee;

const emp: FullEmployee = {
  name: "Vinay",      // from Person
  age: 25,            // from Person
  department: "Eng",  // from Employee
  salary: 100000      // from Employee
};
// Must have ALL properties from both types
```

### Intersection vs Extends
```typescript
// Using intersection (type)
type Admin = Person & {
  permissions: string[];
};

// Using extends (interface) — equivalent
interface Admin2 extends Person {
  permissions: string[];
}

// Intersection is more flexible — works with any types
type Serializable = { serialize(): string };
type SerializableUser = User & Serializable;
```

### Practical Intersection Patterns
```typescript
// Adding metadata to any type
type WithTimestamps<T> = T & {
  createdAt: Date;
  updatedAt: Date;
};

type UserWithTimestamps = WithTimestamps<User>;

// Mixins
type Loggable = { log(): void };
type Validatable = { validate(): boolean };
type Serializable = { serialize(): string };

type EnhancedUser = User & Loggable & Validatable & Serializable;
```

---

## Union vs Intersection

| | Union (`\|`) | Intersection (`&`) |
|-|-----------|---------------|
| Meaning | One OR the other | All combined |
| Access | Only shared members | All members from all types |
| Requires | At least one type's shape | ALL types' shapes |
| Analogous to | Java: method accepting multiple types | Java: class implementing multiple interfaces |

```typescript
// Union: EITHER has name OR has title
type Named = { name: string } | { title: string };
// Can only access properties common to ALL union members without narrowing

// Intersection: has BOTH name AND title
type NamedAndTitled = { name: string } & { title: string };
// Must provide all properties from all intersected types
```

---

## Literal Types ⭐⭐⭐

### What are Literal Types?
Specific values as types — not just `string`, but a specific string.

```typescript
// String literal type
let direction: "up" | "down" | "left" | "right";
direction = "up";    // ✅
// direction = "diagonal";  // ❌ Not in the union

// Number literal type
type DiceRoll = 1 | 2 | 3 | 4 | 5 | 6;
let roll: DiceRoll = 3;   // ✅
// let roll2: DiceRoll = 7;  // ❌

// Boolean literal type
type True = true;
```

### Union of Literals (String Enum Alternative) ⭐⭐⭐
```typescript
// Instead of enum:
enum StatusEnum {
  Active = "ACTIVE",
  Inactive = "INACTIVE",
  Pending = "PENDING"
}

// Prefer union of literals:
type Status = "ACTIVE" | "INACTIVE" | "PENDING";

// Why prefer unions?
// 1. No runtime cost (erased at compile time)
// 2. Simpler JavaScript output
// 3. Better tree-shaking
// 4. Works with discriminated unions
// 5. More idiomatic TypeScript
```

### Template Literal Types (TypeScript 4.1+)
```typescript
type Color = "red" | "blue" | "green";
type Size = "small" | "medium" | "large";

// Generates all combinations
type ColorSize = `${Color}-${Size}`;
// "red-small" | "red-medium" | "red-large" | "blue-small" | ... (9 total)

// Event names
type EventName = `on${Capitalize<"click" | "focus" | "blur">}`;
// "onClick" | "onFocus" | "onBlur"

// HTTP methods
type HttpMethod = "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
type Endpoint = `/api/${"users" | "products" | "orders"}`;
```

---

## Enums

### Numeric Enums
```typescript
enum Direction {
  Up,      // 0
  Down,    // 1
  Left,    // 2
  Right    // 3
}

let dir: Direction = Direction.Up;  // 0
```

### String Enums
```typescript
enum HttpStatus {
  OK = "OK",
  NotFound = "NOT_FOUND",
  ServerError = "SERVER_ERROR"
}

// String enums don't auto-increment — each value must be explicit
```

### Enum Problems and Alternatives
```typescript
// Problem 1: Enums generate runtime code
// enum Color { Red, Green, Blue }
// Compiles to:
// var Color;
// (function (Color) {
//   Color[Color["Red"] = 0] = "Red";
//   ...
// })(Color || (Color = {}));

// Problem 2: Numeric enums allow invalid values
enum Status { Active, Inactive }
let s: Status = 999;  // ✅ No error! (bad)

// Solution: const enum (inlined, no runtime object)
const enum Direction {
  Up = "UP",
  Down = "DOWN"
}
// Compiles to: "UP" directly (no object)

// Best solution: Union literal types
type Direction = "UP" | "DOWN" | "LEFT" | "RIGHT";
// Zero runtime cost, full type safety
```

### When to Use Enums vs Union Literals

| Use Case | Enum | Union Literal |
|----------|------|---------------|
| Need runtime object (iteration) | ✅ | ❌ |
| Zero runtime cost | ❌ | ✅ |
| Tree-shaking | Poor | ✅ |
| Interop with Angular | Either | Either |
| Backend status codes | ✅ (readable) | ✅ (simpler) |
| Feature flags | ❌ | ✅ |

---

## Type Narrowing ⭐⭐⭐

One of the most important TypeScript concepts.

### typeof Narrowing
```typescript
function padLeft(value: string, padding: string | number): string {
  if (typeof padding === "number") {
    return " ".repeat(padding) + value;  // padding is number
  }
  return padding + value;                 // padding is string
}
```

### instanceof Narrowing
```typescript
function formatError(error: Error | string): string {
  if (error instanceof Error) {
    return error.message;   // Error type
  }
  return error;             // string type
}
```

### `in` Operator Narrowing
```typescript
interface Fish { swim(): void; }
interface Bird { fly(): void; }

function move(animal: Fish | Bird): void {
  if ("swim" in animal) {
    animal.swim();   // Fish
  } else {
    animal.fly();    // Bird
  }
}
```

### Equality Narrowing
```typescript
function example(x: string | number, y: string | boolean) {
  if (x === y) {
    // Only string satisfies both — TypeScript knows x and y are strings
    x.toUpperCase();
    y.toUpperCase();
  }
}
```

### Truthiness Narrowing
```typescript
function printName(name: string | null | undefined): void {
  if (name) {
    console.log(name.toUpperCase()); // Narrowed to string (truthy)
  } else {
    console.log("No name provided");
  }
}
```

### Control Flow Analysis
```typescript
function example(value: string | number | null) {
  if (value === null) {
    return;  // After this: value is string | number
  }
  // TypeScript eliminates null from the type
  if (typeof value === "string") {
    value.toUpperCase();  // string
  } else {
    value.toFixed(2);     // number
  }
}
```

---

## Key Interview Questions

**Q: What are discriminated unions and why are they useful?**
> Discriminated unions have a common literal property (the "discriminant") that TypeScript can use to narrow types in a switch/if. They're the TypeScript equivalent of sealed classes in Java/Kotlin. Extremely useful for modeling states (loading/success/error), Redux actions, and event systems. TypeScript ensures exhaustive handling of all cases.

**Q: When would you use intersection types?**
> Intersection types combine multiple types into one that has all properties. Use them for: mixins (adding capabilities like Serializable, Timestamped), composing DTOs from smaller types, and extending types without `extends` keyword. In Angular/React, they're useful for HOC props and middleware types.

**Q: Union literal types vs enums — which do you prefer?**
> Union literal types (`type Status = "active" | "inactive"`) are preferred in most cases. They have zero runtime cost, better tree-shaking, simpler compiled output, and work perfectly with discriminated unions. Enums are only preferred when you need to iterate over values at runtime or want reverse mapping (numeric enums).

**Q: How does TypeScript narrow types inside if/switch blocks?**
> TypeScript uses control flow analysis. After a type guard (`typeof`, `instanceof`, `in`, equality check), it narrows the type within that branch. After an early return, it eliminates the checked type from subsequent code. This is all compile-time — no runtime cost.
