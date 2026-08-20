# TypeScript Fundamentals

## What is TypeScript?

```
JavaScript
    ↓ Dynamic typing (errors at runtime)
TypeScript
    ↓ Static type checking (errors at compile time)
    ↓ Transpiles to JavaScript
JavaScript (runs in browser/Node.js)
```

### TypeScript = JavaScript + Types

- Developed by Microsoft (2012, Anders Hejlsberg — also created C#)
- Strict superset of JavaScript — all valid JS is valid TS
- Compiles/transpiles to plain JavaScript
- Types exist ONLY at compile time — zero runtime overhead
- Does NOT run directly in browsers or Node.js

---

## Why TypeScript?

| Problem in JavaScript | TypeScript Solution |
|----------------------|---------------------|
| No type errors until runtime | Compile-time type checking |
| Poor IDE support | Rich autocomplete, refactoring |
| Hard to refactor safely | Rename symbol across codebase |
| Implicit `any` everywhere | Explicit contracts |
| Runtime `TypeError` crashes | Caught before deployment |
| Documentation gets stale | Types ARE documentation |

### TypeScript vs Java — Key Differences

| Feature | Java | TypeScript |
|---------|------|-----------|
| Type system | Nominal (class name matters) | Structural (shape matters) |
| Type erasure | Generics erased at runtime | ALL types erased at runtime |
| Null safety | Optional (records, Optional) | Built-in (`strictNullChecks`) |
| Union types | Not supported | `string \| number` |
| Type inference | Limited (var since Java 10) | Very powerful |
| Overloading | Method overloading | Overload signatures |
| Enums | Full-featured class | Simple constants or union types |
| Decorators | Annotations | Experimental decorators |
| Access modifiers | Runtime enforcement | Compile-time only |
| Compilation target | Bytecode | JavaScript (ES3-ESNext) |

### Structural vs Nominal Typing ⭐⭐⭐

```typescript
// TypeScript uses STRUCTURAL typing
// If the shape matches, it's compatible — regardless of name

interface Point {
  x: number;
  y: number;
}

interface Coordinate {
  x: number;
  y: number;
}

const p: Point = { x: 1, y: 2 };
const c: Coordinate = p; // ✅ Works! Same structure = compatible

// In Java (nominal typing):
// Point and Coordinate would NOT be compatible
// even with identical fields — different class names
```

---

## TypeScript Compilation

### How It Works
```
┌──────────────────────────────────────────────────────┐
│                TypeScript Compiler (tsc)               │
├──────────────────────────────────────────────────────┤
│  1. Parse .ts files → AST (Abstract Syntax Tree)     │
│  2. Type Check → Verify type correctness             │
│  3. Emit → Generate .js + .d.ts + .map files         │
└──────────────────────────────────────────────────────┘

Input:  app.ts, user.ts, service.ts
Output: app.js, user.js, service.js          (JavaScript)
        app.d.ts, user.d.ts, service.d.ts    (Type declarations)
        app.js.map, user.js.map              (Source maps)
```

### Type Erasure — What Disappears at Runtime
```typescript
// TypeScript (source)
interface User {                    // ← ERASED
  id: number;
  name: string;
}

const user: User = {               // ← ": User" ERASED
  id: 1,
  name: "Vinay"
};

function greet(name: string): string {  // ← types ERASED
  return `Hello, ${name}`;
}

// JavaScript (output)
const user = {
  id: 1,
  name: "Vinay"
};

function greet(name) {
  return `Hello, ${name}`;
}
```

**What survives at runtime**: Enums (become objects), Decorators (become function calls), Classes (become constructor functions).

---

## Basic Types ⭐⭐⭐

### Primitive Types
```typescript
// The basics
let name: string = "Vinay";
let age: number = 25;
let active: boolean = true;
let nothing: null = null;
let notDefined: undefined = undefined;

// number includes integers, floats, Infinity, NaN
let int: number = 42;
let float: number = 3.14;
let hex: number = 0xff;
let binary: number = 0b1010;

// string includes template literals
let greeting: string = `Hello, ${name}`;

// bigint for large numbers
let big: bigint = 100n;

// symbol for unique identifiers
let sym: symbol = Symbol("unique");
```

### Special Types

#### `any` — Opt out of type checking (AVOID)
```typescript
let anything: any = "hello";
anything = 42;            // ✅ No error
anything.foo();           // ✅ No error — but WILL crash at runtime!
anything.bar.baz;         // ✅ No error — extremely dangerous

// any is basically JavaScript — defeats the purpose of TypeScript
```

#### `unknown` — Type-safe alternative to any ⭐⭐⭐
```typescript
let value: unknown = "hello";
// value.toUpperCase();   // ❌ Error! Must narrow first

if (typeof value === "string") {
  value.toUpperCase();    // ✅ Safe — TypeScript knows it's string
}

// unknown forces you to check before using — much safer than any
```

#### `any` vs `unknown` — Interview Question ⭐⭐⭐

| Feature | `any` | `unknown` |
|---------|-------|-----------|
| Assign anything to it | ✅ | ✅ |
| Assign it to other types | ✅ (unsafe) | ❌ (must narrow) |
| Call methods on it | ✅ (unsafe) | ❌ (must narrow) |
| Type checking | Disabled | Enforced |
| When to use | Migration from JS | External data, APIs |

#### `void` — Function returns nothing
```typescript
function logMessage(msg: string): void {
  console.log(msg);
  // No return statement (or return undefined)
}
```

#### `never` — Function never returns
```typescript
// Throws exception
function throwError(msg: string): never {
  throw new Error(msg);
}

// Infinite loop
function infinite(): never {
  while (true) {}
}

// Exhaustive checking
type Status = "active" | "inactive";
function handle(s: Status): string {
  switch (s) {
    case "active": return "Active";
    case "inactive": return "Inactive";
    default:
      const _exhaustive: never = s; // Error if new status added
      return _exhaustive;
  }
}
```

#### `object` — Non-primitive type
```typescript
let obj: object = {};           // Any non-primitive
let obj2: object = [];          // Arrays are objects
let obj3: object = () => {};    // Functions are objects
// let obj4: object = "hello";  // ❌ Primitives not allowed
```

---

## Type Inference ⭐⭐

TypeScript can infer types without explicit annotation.

```typescript
// Explicit typing (verbose)
let name: string = "Vinay";
let age: number = 25;

// Type inference (preferred when obvious)
let name = "Vinay";    // TypeScript infers: string
let age = 25;          // TypeScript infers: number
let active = true;     // TypeScript infers: boolean

// Inference from return value
function add(a: number, b: number) {
  return a + b;        // Return type inferred as: number
}

// Inference from context
const names = ["Alice", "Bob"];  // string[]
names.map(name => name.toUpperCase()); // name inferred as string
```

### When to Use Explicit Types
```typescript
// ✅ Use explicit types for:
// 1. Function parameters (always)
function greet(name: string): string { ... }

// 2. Complex return types
function getUser(): User | null { ... }

// 3. Empty initializations
let users: User[] = [];
let cache: Map<string, User> = new Map();

// 4. Public API / exported functions
export function createOrder(items: OrderItem[]): Order { ... }

// ✅ Skip explicit types for:
// 1. Obvious literals
let count = 0;           // Obviously number
let name = "Vinay";      // Obviously string

// 2. Return types of simple functions
const double = (n: number) => n * 2;  // Return type obvious
```

---

## Type Assertions

When you know more than TypeScript about a type:

```typescript
// as syntax (preferred)
const input = document.getElementById("name") as HTMLInputElement;
input.value = "Hello";

// angle bracket syntax (doesn't work in JSX/TSX)
const input2 = <HTMLInputElement>document.getElementById("name");

// Double assertion (for incompatible types — use sparingly)
const value = "hello" as unknown as number; // Dangerous!

// const assertion
const colors = ["red", "green", "blue"] as const;
// Type: readonly ["red", "green", "blue"] (not string[])

// satisfies (TypeScript 4.9+) — validates without widening
const palette = {
  red: [255, 0, 0],
  green: "#00ff00",
} satisfies Record<string, string | number[]>;
// palette.red is still number[] (not string | number[])
```

---

## Key Interview Questions

**Q: What is TypeScript and why use it over JavaScript?**
> TypeScript is a statically-typed superset of JavaScript. It adds compile-time type checking, catching errors before runtime. Benefits: better IDE support, safer refactoring, self-documenting code, and catching 15% of bugs at compile time (Microsoft research). It transpiles to JavaScript — zero runtime cost.

**Q: Does TypeScript run in the browser?**
> No. TypeScript is compiled to JavaScript by the TypeScript compiler (tsc). The browser/Node.js runs the resulting JavaScript. Types are completely erased at compile time — they have zero runtime presence.

**Q: What is structural typing and how does it differ from Java's nominal typing?**
> In TypeScript (structural), compatibility is determined by shape — if an object has the required properties, it's compatible regardless of its declared type. In Java (nominal), objects must explicitly extend/implement the required type by name. This means in TypeScript, you can pass any object with matching structure where an interface is expected.

**Q: When would you use `any` vs `unknown`?**
> Almost never use `any` — it disables type checking entirely. Use `unknown` when you genuinely don't know the type (API responses, user input). `unknown` forces you to narrow the type before using it, maintaining type safety. Only use `any` during JS-to-TS migration as a temporary escape hatch.

**Q: What is `never` used for?**
> `never` represents values that never occur. Used for: functions that always throw, infinite loops, and exhaustive checking in switch statements. If you add a new union member and forget to handle it, the `never` assignment will cause a compile error.
