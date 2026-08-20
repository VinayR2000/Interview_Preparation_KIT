# TypeScript Utility Types ⭐⭐⭐

## Why Utility Types?

Utility types transform existing types into new ones without rewriting. They're TypeScript's equivalent of "type-level functions."

```
Base Type → Utility Type → Transformed Type

User → Partial<User> → All properties optional
User → Pick<User, "id" | "name"> → Only id and name
User → Omit<User, "password"> → Everything except password
```

---

## Reference Type

```typescript
interface User {
  id: number;
  name: string;
  email: string;
  age: number;
  password: string;
  createdAt: Date;
}
```

---

## Partial\<T\> ⭐⭐⭐

Makes ALL properties optional.

```typescript
type PartialUser = Partial<User>;
// Equivalent to:
// {
//   id?: number;
//   name?: string;
//   email?: string;
//   age?: number;
//   password?: string;
//   createdAt?: Date;
// }

// Use case: Update DTOs (only send fields being changed)
function updateUser(id: number, updates: Partial<User>): User {
  const existing = findUser(id);
  return { ...existing, ...updates };
}

updateUser(1, { name: "New Name" });           // ✅ Only update name
updateUser(1, { name: "X", age: 30 });         // ✅ Update multiple
updateUser(1, {});                              // ✅ Update nothing
```

---

## Required\<T\>

Makes ALL properties required (opposite of Partial).

```typescript
interface Config {
  host?: string;
  port?: number;
  debug?: boolean;
}

type FullConfig = Required<Config>;
// {
//   host: string;    // No longer optional
//   port: number;
//   debug: boolean;
// }

// Use case: Ensure all config is set after defaults are applied
function initializeApp(config: Required<Config>): void {
  // All properties guaranteed present
  console.log(`Connecting to ${config.host}:${config.port}`);
}
```

---

## Readonly\<T\> ⭐⭐

Makes ALL properties readonly.

```typescript
type ReadonlyUser = Readonly<User>;
// All properties become readonly — cannot be reassigned

const user: ReadonlyUser = {
  id: 1, name: "Vinay", email: "v@x.com",
  age: 25, password: "hash", createdAt: new Date()
};

// user.name = "New";  // ❌ Error: Cannot assign to 'name'

// Use case: Immutable state (NgRx, Redux)
interface AppState {
  users: User[];
  loading: boolean;
}

type ImmutableState = Readonly<AppState>;
// Note: Readonly is shallow — users array contents can still change
// For deep readonly, use: type DeepReadonly<T> = { readonly [K in keyof T]: DeepReadonly<T[K]> }
```

---

## Pick\<T, K\> ⭐⭐⭐

Select ONLY specified properties from a type.

```typescript
type UserSummary = Pick<User, "id" | "name">;
// { id: number; name: string; }

type LoginCredentials = Pick<User, "email" | "password">;
// { email: string; password: string; }

// Use case: API responses with subset of fields
type UserListItem = Pick<User, "id" | "name" | "email">;

function getUserList(): UserListItem[] {
  return users.map(({ id, name, email }) => ({ id, name, email }));
}
```

---

## Omit\<T, K\> ⭐⭐⭐

Remove specified properties from a type (opposite of Pick).

```typescript
type UserWithoutPassword = Omit<User, "password">;
// { id, name, email, age, createdAt } — everything except password

type CreateUserDTO = Omit<User, "id" | "createdAt">;
// { name, email, age, password } — server generates id and createdAt

// Use case: API DTOs
interface ApiUser extends Omit<User, "password"> {}
// Safe to send to frontend — no password field
```

---

## Record\<K, V\> ⭐⭐⭐

Create an object type with specified keys and value type.

```typescript
// All keys have the same value type
type UserRoles = Record<string, string[]>;
// { [key: string]: string[] }

const roles: UserRoles = {
  admin: ["read", "write", "delete"],
  user: ["read"],
  editor: ["read", "write"]
};

// With literal union keys — ensures all keys present
type Status = "loading" | "success" | "error";
type StatusMessages = Record<Status, string>;

const messages: StatusMessages = {
  loading: "Please wait...",
  success: "Done!",
  error: "Something went wrong"
  // Must include ALL Status values — TypeScript enforces it
};

// Use case: Lookup tables, configuration maps
type HttpStatusCodes = Record<number, string>;
const codes: HttpStatusCodes = {
  200: "OK",
  404: "Not Found",
  500: "Internal Server Error"
};
```

---

## Exclude\<T, U\>

Remove types from a union.

```typescript
type AllStatus = "active" | "inactive" | "pending" | "deleted";

type ActiveStatuses = Exclude<AllStatus, "deleted">;
// "active" | "inactive" | "pending"

type NonNullString = Exclude<string | null | undefined, null | undefined>;
// string

// Use case: Remove certain options
type WritableFields = Exclude<keyof User, "id" | "createdAt">;
// "name" | "email" | "age" | "password"
```

---

## Extract\<T, U\>

Keep only types that match (opposite of Exclude).

```typescript
type AllTypes = string | number | boolean | object;

type Primitives = Extract<AllTypes, string | number | boolean>;
// string | number | boolean

// Use case: Filter event types
type AllEvents = "click" | "focus" | "blur" | "mouseenter" | "mouseleave";
type MouseEvents = Extract<AllEvents, `mouse${string}`>;
// "mouseenter" | "mouseleave"
```

---

## NonNullable\<T\>

Remove `null` and `undefined` from a type.

```typescript
type MaybeUser = User | null | undefined;
type DefiniteUser = NonNullable<MaybeUser>;
// User

// Use case: After null checking
function processUser(user: User | null): void {
  if (!user) return;
  const definiteUser: NonNullable<typeof user> = user;
  // Now TypeScript knows it's User (not null)
}
```

---

## ReturnType\<T\> ⭐⭐

Extract the return type of a function.

```typescript
function createUser(name: string, age: number) {
  return { id: Math.random(), name, age, createdAt: new Date() };
}

type CreatedUser = ReturnType<typeof createUser>;
// { id: number; name: string; age: number; createdAt: Date }

// Use case: Type derived from function without separate interface
async function fetchUsers(): Promise<User[]> {
  const response = await fetch("/api/users");
  return response.json();
}

type FetchUsersResult = ReturnType<typeof fetchUsers>;
// Promise<User[]>

type UnwrappedResult = Awaited<ReturnType<typeof fetchUsers>>;
// User[]
```

---

## Parameters\<T\>

Extract parameter types as a tuple.

```typescript
function createUser(name: string, age: number, email: string): User {
  return { id: 1, name, age, email, password: "", createdAt: new Date() };
}

type CreateUserParams = Parameters<typeof createUser>;
// [string, number, string]

// Use case: Wrapping functions
function logAndCall<F extends (...args: any[]) => any>(
  fn: F,
  ...args: Parameters<F>
): ReturnType<F> {
  console.log("Calling with:", args);
  return fn(...args);
}
```

---

## Awaited\<T\> (TypeScript 4.5+)

Unwrap Promise types.

```typescript
type A = Awaited<Promise<string>>;           // string
type B = Awaited<Promise<Promise<number>>>;  // number (recursive unwrap)
type C = Awaited<string | Promise<number>>;  // string | number

// Use case: Getting the resolved type of async functions
async function fetchData(): Promise<{ users: User[]; total: number }> {
  // ...
}

type Data = Awaited<ReturnType<typeof fetchData>>;
// { users: User[]; total: number }
```

---

## Combining Utility Types ⭐⭐⭐

Real-world patterns combining multiple utilities:

```typescript
// Create DTO: omit auto-generated fields, make everything required
type CreateUserDTO = Required<Omit<User, "id" | "createdAt">>;

// Update DTO: omit immutable fields, make everything optional
type UpdateUserDTO = Partial<Omit<User, "id" | "createdAt">>;

// Public user: omit sensitive fields, make readonly
type PublicUser = Readonly<Omit<User, "password">>;

// Form state: all fields optional strings (form inputs are always strings)
type UserForm = Record<keyof Omit<User, "id" | "createdAt">, string>;

// API response wrapper
type ApiResponse<T> = {
  data: T;
  meta: { total: number; page: number };
};

type UserListResponse = ApiResponse<Pick<User, "id" | "name" | "email">[]>;
```

---

## Practical Full-Stack Patterns

### Spring Boot ↔ Angular/React DTO Mapping
```typescript
// Match your Spring Boot DTOs:

// @Entity User (all fields)
interface UserEntity {
  id: number;
  name: string;
  email: string;
  password: string;
  role: "USER" | "ADMIN";
  createdAt: string;  // ISO date string from JSON
  updatedAt: string;
}

// CreateUserRequest DTO
type CreateUserRequest = Pick<UserEntity, "name" | "email" | "password" | "role">;

// UpdateUserRequest DTO
type UpdateUserRequest = Partial<Pick<UserEntity, "name" | "email" | "role">>;

// UserResponse DTO (no password)
type UserResponse = Omit<UserEntity, "password">;

// UserSummary (for list views)
type UserSummary = Pick<UserEntity, "id" | "name" | "email" | "role">;

// Login request
type LoginRequest = Pick<UserEntity, "email" | "password">;
```

---

## Utility Type Cheat Sheet

| Utility | Effect | Use Case |
|---------|--------|----------|
| `Partial<T>` | All optional | Update DTOs |
| `Required<T>` | All required | After applying defaults |
| `Readonly<T>` | All readonly | Immutable state |
| `Pick<T, K>` | Only listed keys | Summary/subset types |
| `Omit<T, K>` | All except listed keys | Remove sensitive fields |
| `Record<K, V>` | Object with K keys, V values | Dictionaries, lookup tables |
| `Exclude<T, U>` | Remove from union | Filter union members |
| `Extract<T, U>` | Keep matching union | Get matching members |
| `NonNullable<T>` | Remove null/undefined | After null checks |
| `ReturnType<T>` | Function's return type | Derive types from functions |
| `Parameters<T>` | Function's param types | Wrapping functions |
| `Awaited<T>` | Unwrap Promise | Async function results |
| `InstanceType<T>` | Class instance type | Factory patterns |
| `ConstructorParameters<T>` | Constructor params | DI, factories |

---

## Key Interview Questions

**Q: What are utility types and name the ones you use most frequently?**
> Utility types are built-in generic types that transform other types. Most used: `Partial<T>` for update DTOs (all optional), `Omit<T, K>` for removing fields (like password from API response), `Pick<T, K>` for selecting fields (summary views), `Record<K, V>` for typed dictionaries. They reduce duplication and keep types in sync with the source type.

**Q: How would you type a CRUD API's DTOs using utility types?**
> Start with the entity interface, then derive: `CreateDTO = Omit<Entity, "id" | "createdAt">` (server generates these), `UpdateDTO = Partial<Omit<Entity, "id" | "createdAt">>` (partial updates), `ResponseDTO = Omit<Entity, "password">` (hide sensitive data). Changes to the entity auto-propagate to all DTOs.

**Q: What's the difference between `Pick` and `Omit`?**
> `Pick<T, K>` creates a type with ONLY the listed properties (whitelist). `Omit<T, K>` creates a type with all properties EXCEPT the listed ones (blacklist). Use Pick when you want a small subset, Omit when you want most properties minus a few.

**Q: How does `Record` differ from an index signature?**
> `Record<K, V>` with a union key type ensures ALL keys are present: `Record<"a" | "b", number>` requires both "a" and "b". An index signature `{ [key: string]: number }` allows any string key with no completeness requirement. Use Record for exhaustive mappings, index signatures for open-ended dictionaries.
