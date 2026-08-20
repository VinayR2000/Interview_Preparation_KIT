# Advanced Type System ⭐⭐⭐

## keyof Operator

Produces a union of all property names of a type.

```typescript
interface User {
  id: number;
  name: string;
  email: string;
  age: number;
}

type UserKeys = keyof User;
// "id" | "name" | "email" | "age"

// Use with generics for type-safe property access
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}

const user: User = { id: 1, name: "Vinay", email: "v@x.com", age: 25 };
const name = getProperty(user, "name");    // string
const age = getProperty(user, "age");      // number
// getProperty(user, "phone");              // ❌ Error: "phone" not in keyof User
```

---

## typeof Operator (Type Level)

Gets the TypeScript type of a value.

```typescript
const config = {
  apiUrl: "http://localhost:8080",
  timeout: 5000,
  retries: 3
};

type Config = typeof config;
// { apiUrl: string; timeout: number; retries: number }

// Useful for deriving types from runtime values
const defaultUser = { id: 0, name: "", email: "", active: true };
type User = typeof defaultUser;
// { id: number; name: string; email: string; active: boolean }

// Combined with ReturnType
function createApi() {
  return { get: () => {}, post: () => {} };
}
type Api = ReturnType<typeof createApi>;
```

---

## Indexed Access Types

Access a type's property type using bracket notation.

```typescript
interface User {
  id: number;
  name: string;
  address: {
    street: string;
    city: string;
    zip: string;
  };
  roles: string[];
}

type UserId = User["id"];              // number
type UserName = User["name"];          // string
type Address = User["address"];        // { street: string; city: string; zip: string }
type City = User["address"]["city"];   // string
type Role = User["roles"][number];     // string (element type of array)

// With keyof
type UserValues = User[keyof User];
// number | string | { street: string; city: string; zip: string } | string[]

// Practical use: extract nested types
interface ApiResponse {
  data: {
    users: User[];
    pagination: { page: number; total: number };
  };
  status: number;
}

type UsersData = ApiResponse["data"]["users"];        // User[]
type Pagination = ApiResponse["data"]["pagination"];  // { page: number; total: number }
```

---

## Conditional Types ⭐⭐⭐

```
T extends U ? X : Y
```
If T is assignable to U, type is X, otherwise Y.

```typescript
// Basic conditional
type IsString<T> = T extends string ? true : false;

type A = IsString<string>;    // true
type B = IsString<number>;    // false
type C = IsString<"hello">;   // true (literal extends string)

// Practical: unwrap Promise
type Unwrap<T> = T extends Promise<infer U> ? U : T;

type D = Unwrap<Promise<string>>;  // string
type E = Unwrap<number>;           // number (not a Promise, returns T)

// Unwrap Observable (Angular)
type UnwrapObservable<T> = T extends Observable<infer U> ? U : T;

// Extract array element type
type ElementType<T> = T extends (infer E)[] ? E : never;

type F = ElementType<string[]>;   // string
type G = ElementType<User[]>;     // User
type H = ElementType<number>;     // never
```

### Distributive Conditional Types
```typescript
// When T is a union, conditional distributes over each member
type ToArray<T> = T extends any ? T[] : never;

type Result = ToArray<string | number>;
// string[] | number[] (NOT (string | number)[])

// Prevent distribution with brackets
type ToArrayNonDist<T> = [T] extends [any] ? T[] : never;
type Result2 = ToArrayNonDist<string | number>;
// (string | number)[]
```

---

## infer Keyword ⭐⭐

Extract types within conditional types.

```typescript
// Extract return type
type GetReturnType<T> = T extends (...args: any[]) => infer R ? R : never;

type A = GetReturnType<() => string>;           // string
type B = GetReturnType<(x: number) => boolean>; // boolean

// Extract first argument
type FirstArg<T> = T extends (first: infer F, ...rest: any[]) => any ? F : never;

type C = FirstArg<(name: string, age: number) => void>;  // string

// Extract Promise inner type
type UnwrapPromise<T> = T extends Promise<infer U> ? U : T;

// Extract array element
type Flatten<T> = T extends Array<infer Item> ? Item : T;

// Extract generic parameter
type ExtractGeneric<T> = T extends ApiResponse<infer D> ? D : never;
type UserData = ExtractGeneric<ApiResponse<User>>;  // User
```

---

## Mapped Types ⭐⭐⭐

Transform properties of a type systematically.

```typescript
// Basic mapped type: make all properties optional
type MyPartial<T> = {
  [K in keyof T]?: T[K];
};

// Make all properties readonly
type MyReadonly<T> = {
  readonly [K in keyof T]: T[K];
};

// Make all properties nullable
type Nullable<T> = {
  [K in keyof T]: T[K] | null;
};

// Make all properties required (remove ?)
type MyRequired<T> = {
  [K in keyof T]-?: T[K];  // -? removes optionality
};

// Remove readonly
type Mutable<T> = {
  -readonly [K in keyof T]: T[K];  // -readonly removes readonly
};
```

### Key Remapping (TypeScript 4.1+)
```typescript
// Rename keys using `as`
type Getters<T> = {
  [K in keyof T as `get${Capitalize<string & K>}`]: () => T[K];
};

type UserGetters = Getters<User>;
// {
//   getId: () => number;
//   getName: () => string;
//   getEmail: () => string;
//   getAge: () => number;
// }

// Filter keys
type OnlyStrings<T> = {
  [K in keyof T as T[K] extends string ? K : never]: T[K];
};

type StringFields = OnlyStrings<User>;
// { name: string; email: string }

// Prefix keys
type Prefixed<T, P extends string> = {
  [K in keyof T as `${P}${Capitalize<string & K>}`]: T[K];
};

type FormUser = Prefixed<Pick<User, "name" | "email">, "form">;
// { formName: string; formEmail: string }
```

---

## Template Literal Types

```typescript
// Basic template literals
type EventName = `on${string}`;       // any string starting with "on"
type HttpMethod = "GET" | "POST" | "PUT" | "DELETE";
type ApiPath = `/api/${string}`;

// Intrinsic string manipulation types
type Upper = Uppercase<"hello">;       // "HELLO"
type Lower = Lowercase<"HELLO">;       // "hello"
type Cap = Capitalize<"hello">;        // "Hello"
type Uncap = Uncapitalize<"Hello">;    // "hello"

// Combining unions (generates all permutations)
type Size = "sm" | "md" | "lg";
type Color = "primary" | "secondary";
type ButtonClass = `btn-${Size}-${Color}`;
// "btn-sm-primary" | "btn-sm-secondary" | "btn-md-primary" | ... (6 total)

// Event handler types
type DomEvents = "click" | "focus" | "blur" | "change";
type EventHandler = `on${Capitalize<DomEvents>}`;
// "onClick" | "onFocus" | "onBlur" | "onChange"
```

---

## Custom Type Guards ⭐⭐

### Type Predicates
```typescript
// Return type: `value is Type` narrows the type for callers
function isString(value: unknown): value is string {
  return typeof value === "string";
}

function isUser(value: unknown): value is User {
  return (
    typeof value === "object" &&
    value !== null &&
    "id" in value &&
    "name" in value &&
    "email" in value
  );
}

// Usage — TypeScript trusts the predicate
function process(data: unknown): void {
  if (isUser(data)) {
    console.log(data.name);   // ✅ TypeScript knows it's User
    console.log(data.email);  // ✅
  }
}
```

### Assertion Functions
```typescript
// Throws if not the expected type (narrows after the call)
function assertIsUser(value: unknown): asserts value is User {
  if (!isUser(value)) {
    throw new Error("Not a valid User object");
  }
}

function processData(data: unknown): void {
  assertIsUser(data);
  // After this line, TypeScript knows data is User
  console.log(data.name);  // ✅ No if-check needed
}

// Assert not null
function assertDefined<T>(value: T | null | undefined, msg?: string): asserts value is T {
  if (value === null || value === undefined) {
    throw new Error(msg ?? "Value is null/undefined");
  }
}
```

---

## Advanced Patterns

### Recursive Types
```typescript
// JSON value type
type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

// Deep readonly
type DeepReadonly<T> = T extends object
  ? { readonly [K in keyof T]: DeepReadonly<T[K]> }
  : T;

// Deep partial
type DeepPartial<T> = T extends object
  ? { [K in keyof T]?: DeepPartial<T[K]> }
  : T;

// Nested path type
type Path<T, Key extends keyof T = keyof T> =
  Key extends string
    ? T[Key] extends object
      ? Key | `${Key}.${Path<T[Key]>}`
      : Key
    : never;

type UserPaths = Path<User>;
// "id" | "name" | "email" | "age" | "address" | "address.street" | ...
```

### Branded Types (Nominal-like typing)
```typescript
// Make structurally identical types incompatible
type Brand<T, B> = T & { __brand: B };

type UserId = Brand<number, "UserId">;
type OrderId = Brand<number, "OrderId">;

function getUser(id: UserId): User { /* ... */ }
function getOrder(id: OrderId): Order { /* ... */ }

const userId = 1 as UserId;
const orderId = 2 as OrderId;

getUser(userId);    // ✅
// getUser(orderId);  // ❌ Error! OrderId not assignable to UserId
// getUser(1);        // ❌ Error! number not assignable to UserId
```

---

## Type Manipulation Summary

```
┌──────────────────────────────────────────────┐
│           Type-Level Operations               │
├──────────────────────────────────────────────┤
│  keyof T          → union of property names  │
│  T[K]             → property type at key K   │
│  typeof value     → type of runtime value    │
│  T extends U?X:Y  → conditional type         │
│  infer            → extract type variable    │
│  {[K in keyof T]} → iterate over properties  │
│  `template${T}`   → string pattern types     │
│  as const         → narrow to literal types  │
│  satisfies T      → validate without widen   │
└──────────────────────────────────────────────┘
```

---

## Key Interview Questions

**Q: What is `keyof` and how is it useful?**
> `keyof T` produces a union of all property names of T as string literal types. Combined with generics, it enables type-safe property access: `function get<T, K extends keyof T>(obj: T, key: K): T[K]`. This ensures only valid keys are used and the return type matches the property type automatically.

**Q: Explain conditional types with an example.**
> Conditional types work like ternary operators at the type level: `T extends U ? X : Y`. Example: `type Unwrap<T> = T extends Promise<infer U> ? U : T` — if T is a Promise, extract the inner type; otherwise return T as-is. They enable type-level logic and are how utility types like `Exclude`, `Extract`, and `ReturnType` work internally.

**Q: What are mapped types?**
> Mapped types iterate over keys of a type and transform each property. Syntax: `{ [K in keyof T]: NewType }`. TypeScript's built-in `Partial`, `Readonly`, `Required` are all mapped types. You can add/remove modifiers (`?`, `readonly`), transform value types, and remap keys. They're the "map function" for types.

**Q: How would you implement a type-safe event emitter?**
> Use a generic with a Record constraint: `class EventEmitter<Events extends Record<string, any>>`. The `on` method uses `<K extends keyof Events>` to restrict event names and type the handler: `on(event: K, handler: (data: Events[K]) => void)`. This gives autocomplete for event names and typed payloads.
