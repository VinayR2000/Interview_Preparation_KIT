# TypeScript Functions ⭐⭐⭐

## Function Type Annotations

### Basic Function Typing
```typescript
// Named function with types
function add(a: number, b: number): number {
  return a + b;
}

// Arrow function with types
const multiply = (a: number, b: number): number => a * b;

// Return type inference (TypeScript can infer)
function divide(a: number, b: number) {
  return a / b;  // Return type inferred as: number
}
```

### Function Type Expressions
```typescript
// Define a function type
type MathOperation = (a: number, b: number) => number;

const add: MathOperation = (a, b) => a + b;
const subtract: MathOperation = (a, b) => a - b;

// As parameter type
function calculate(op: MathOperation, x: number, y: number): number {
  return op(x, y);
}

// Interface with call signature
interface Formatter {
  (value: unknown): string;
}

const jsonFormatter: Formatter = (value) => JSON.stringify(value);
```

---

## Parameter Types

### Optional Parameters
```typescript
function greet(name: string, greeting?: string): string {
  return `${greeting ?? "Hello"}, ${name}!`;
}

greet("Vinay");            // "Hello, Vinay!"
greet("Vinay", "Hi");     // "Hi, Vinay!"

// Optional parameters must come AFTER required parameters
// function bad(a?: string, b: number) {} // ❌ Error
```

### Default Parameters
```typescript
function createUser(name: string, role: string = "user"): User {
  return { name, role };
}

createUser("Vinay");          // role = "user"
createUser("Vinay", "admin"); // role = "admin"

// Default parameters are implicitly optional
// No need for ?: when you have a default
```

### Rest Parameters
```typescript
function sum(...numbers: number[]): number {
  return numbers.reduce((acc, n) => acc + n, 0);
}

sum(1, 2, 3);        // 6
sum(1, 2, 3, 4, 5);  // 15

// Rest with other params
function log(level: string, ...messages: string[]): void {
  console.log(`[${level}]`, ...messages);
}
```

### Destructured Parameters
```typescript
interface UserOptions {
  name: string;
  age: number;
  email?: string;
}

function createUser({ name, age, email = "none" }: UserOptions): User {
  return { name, age, email };
}

// With inline type
function display({ x, y }: { x: number; y: number }): void {
  console.log(`(${x}, ${y})`);
}
```

---

## Return Types

### Explicit Return Types
```typescript
// Primitive return
function getName(): string { return "Vinay"; }

// Object return
function getUser(): User { return { id: 1, name: "Vinay" }; }

// Array return
function getUsers(): User[] { return []; }

// Promise return (async)
async function fetchUser(): Promise<User> {
  const response = await fetch("/api/user");
  return response.json();
}
```

### void — Function doesn't return a useful value
```typescript
function logMessage(msg: string): void {
  console.log(msg);
  // return undefined; // Implicit
}

// Important: void in callback context allows any return
type Callback = () => void;
const cb: Callback = () => 42;  // ✅ OK! Return value ignored
// This is by design — allows Array.forEach(callback) even if callback returns
```

### never — Function never returns
```typescript
function throwError(msg: string): never {
  throw new Error(msg);
}

function infiniteLoop(): never {
  while (true) {}
}

// Useful for exhaustive checks
function assertNever(x: never): never {
  throw new Error(`Unexpected value: ${x}`);
}
```

---

## Function Overloads ⭐⭐

When a function can accept different argument types and return different types:

```typescript
// Overload signatures (what callers see)
function getItem(id: number): User;
function getItem(name: string): User[];
function getItem(query: { field: string; value: string }): User[];

// Implementation signature (must handle all overloads)
function getItem(idOrNameOrQuery: number | string | { field: string; value: string }): User | User[] {
  if (typeof idOrNameOrQuery === "number") {
    return findUserById(idOrNameOrQuery);
  } else if (typeof idOrNameOrQuery === "string") {
    return findUsersByName(idOrNameOrQuery);
  } else {
    return findUsersByQuery(idOrNameOrQuery);
  }
}

// Callers get specific return types based on argument
const user = getItem(1);          // Type: User
const users = getItem("Vinay");   // Type: User[]
```

### When to Use Overloads vs Unions
```typescript
// ❌ Overloads when union would suffice
function len(s: string): number;
function len(arr: any[]): number;
function len(x: string | any[]): number {
  return x.length;
}

// ✅ Better: just use union
function len(x: string | any[]): number {
  return x.length;
}

// ✅ Overloads when return type DEPENDS on input type
function createElement(tag: "div"): HTMLDivElement;
function createElement(tag: "span"): HTMLSpanElement;
function createElement(tag: string): HTMLElement;
function createElement(tag: string): HTMLElement {
  return document.createElement(tag);
}
```

---

## `this` Parameter

```typescript
interface User {
  name: string;
  greet(this: User): string;  // Explicit this type
}

const user: User = {
  name: "Vinay",
  greet() {
    return `Hello, I'm ${this.name}`;  // this is typed as User
  }
};

// Prevents calling with wrong context
// const greet = user.greet;
// greet();  // ❌ Error: 'this' context is not assignable

// Arrow functions capture 'this' from enclosing scope
class UserService {
  private users: User[] = [];

  // Arrow function: 'this' always refers to UserService
  loadUsers = async (): Promise<void> => {
    this.users = await fetchUsers();  // 'this' is safe
  };
}
```

---

## Callback Types

```typescript
// Callback type definition
type SuccessCallback<T> = (data: T) => void;
type ErrorCallback = (error: Error) => void;
type CompletionCallback<T> = (error: Error | null, data?: T) => void;

// Function accepting callbacks
function fetchData<T>(
  url: string,
  onSuccess: SuccessCallback<T>,
  onError: ErrorCallback
): void {
  fetch(url)
    .then(res => res.json())
    .then(data => onSuccess(data))
    .catch(err => onError(err));
}

// Usage
fetchData<User[]>(
  "/api/users",
  (users) => console.log(users),    // users: User[]
  (error) => console.error(error)   // error: Error
);

// Event handler types (DOM)
type ClickHandler = (event: MouseEvent) => void;
type InputHandler = (event: InputEvent) => void;
type SubmitHandler = (event: SubmitEvent) => void;
```

---

## Generic Functions ⭐⭐⭐

(Covered in depth in generics file — key patterns here)

```typescript
// Basic generic function
function identity<T>(value: T): T {
  return value;
}

// With constraint
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}

// Array utility
function first<T>(arr: T[]): T | undefined {
  return arr[0];
}

// Multiple generics
function map<T, U>(arr: T[], fn: (item: T) => U): U[] {
  return arr.map(fn);
}

// Generic arrow function (note the trailing comma in TSX files)
const identity2 = <T,>(value: T): T => value;
```

---

## Higher-Order Functions

Functions that accept or return other functions:

```typescript
// Function that returns a function
function createMultiplier(factor: number): (n: number) => number {
  return (n) => n * factor;
}

const double = createMultiplier(2);
const triple = createMultiplier(3);
double(5);  // 10
triple(5);  // 15

// Function that accepts a function (with generics)
function retry<T>(fn: () => Promise<T>, attempts: number): Promise<T> {
  return fn().catch(error => {
    if (attempts <= 1) throw error;
    return retry(fn, attempts - 1);
  });
}

// Decorator pattern
function withLogging<T extends (...args: any[]) => any>(fn: T): T {
  return ((...args: any[]) => {
    console.log(`Calling ${fn.name} with`, args);
    const result = fn(...args);
    console.log(`Result:`, result);
    return result;
  }) as T;
}
```

---

## Function Types Comparison: TypeScript vs Java

| Concept | Java | TypeScript |
|---------|------|-----------|
| Function type | `Function<T,R>`, `Consumer<T>` | `(param: T) => R` |
| Lambda | `(x) -> x * 2` | `(x) => x * 2` |
| Method reference | `User::getName` | Direct function reference |
| Optional params | Overloading | `param?: type` |
| Default params | Overloading | `param = default` |
| Varargs | `String... args` | `...args: string[]` |
| Return type | Before method name | After parameters |
| Void | `void` (lowercase) | `void` (same) |
| Never returns | No equivalent | `never` |

---

## Key Interview Questions

**Q: What is the difference between `void` and `never` as return types?**
> `void` means the function doesn't return a useful value (may return undefined implicitly). `never` means the function NEVER completes — it either throws an exception or runs forever. `void` functions finish normally; `never` functions never reach their end.

**Q: When should you use function overloads vs union parameters?**
> Use overloads when the return type depends on which argument type is passed. If the return type is always the same regardless of argument type, a union parameter is simpler and preferred. Overloads create better autocomplete but add complexity.

**Q: How does `this` work in TypeScript?**
> TypeScript can enforce `this` context with explicit `this` parameter (first parameter, erased at compile time). Arrow functions capture `this` from enclosing scope (like Java lambdas). Regular functions get `this` from call site. In classes, use arrow functions for callbacks to preserve `this`.

**Q: Explain `void` in callback position.**
> When a callback type returns `void` (e.g., `type CB = () => void`), implementations CAN return a value — it's just ignored. This is intentional: it allows `arr.forEach(item => arr.push(item))` where `push` returns a number but `forEach` expects `void` callback. It's a variance/substitutability feature.
