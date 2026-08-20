# TypeScript Generics ⭐⭐⭐

## Why Generics?

```
Without Generics:
  function identity(value: any): any → Loses type information!
  
With Generics:
  function identity<T>(value: T): T → Type preserved!
  
  identity<string>("hello") → returns string
  identity<number>(42)      → returns number
```

Generics provide **reusability with type safety** — the same function/class works with many types without losing type information.

---

## Generic Functions

### Basic Syntax
```typescript
function identity<T>(value: T): T {
  return value;
}

// Explicit type argument
const str = identity<string>("hello");   // string
const num = identity<number>(42);        // number

// Type inference (preferred when possible)
const str2 = identity("hello");          // TypeScript infers T = string
const num2 = identity(42);              // TypeScript infers T = number
```

### Multiple Type Parameters
```typescript
function pair<A, B>(first: A, second: B): [A, B] {
  return [first, second];
}

const p = pair("hello", 42);    // [string, number]
const p2 = pair(true, [1, 2]); // [boolean, number[]]

// Map function
function map<T, U>(arr: T[], fn: (item: T) => U): U[] {
  return arr.map(fn);
}

const lengths = map(["hello", "world"], s => s.length);  // number[]
```

### Generic Arrow Functions
```typescript
// Standard syntax
const identity = <T>(value: T): T => value;

// In TSX files, trailing comma avoids JSX ambiguity
const identity2 = <T,>(value: T): T => value;

// With multiple params
const swap = <A, B>(pair: [A, B]): [B, A] => [pair[1], pair[0]];
```

---

## Generic Interfaces

```typescript
// Generic response wrapper
interface ApiResponse<T> {
  data: T;
  status: number;
  message: string;
  timestamp: Date;
}

// Usage with different types
const userResponse: ApiResponse<User> = {
  data: { id: 1, name: "Vinay" },
  status: 200,
  message: "Success",
  timestamp: new Date()
};

const usersResponse: ApiResponse<User[]> = {
  data: [{ id: 1, name: "Vinay" }, { id: 2, name: "Alice" }],
  status: 200,
  message: "Success",
  timestamp: new Date()
};

// Paginated response
interface PaginatedResponse<T> {
  data: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}
```

### Generic Interface for Repository Pattern
```typescript
interface Repository<T> {
  findAll(): Promise<T[]>;
  findById(id: number): Promise<T | null>;
  create(entity: Omit<T, "id">): Promise<T>;
  update(id: number, data: Partial<T>): Promise<T>;
  delete(id: number): Promise<boolean>;
}

// Implementation
class UserRepository implements Repository<User> {
  async findAll(): Promise<User[]> { /* ... */ }
  async findById(id: number): Promise<User | null> { /* ... */ }
  async create(user: Omit<User, "id">): Promise<User> { /* ... */ }
  async update(id: number, data: Partial<User>): Promise<User> { /* ... */ }
  async delete(id: number): Promise<boolean> { /* ... */ }
}
```

---

## Generic Classes

```typescript
class Stack<T> {
  private items: T[] = [];

  push(item: T): void {
    this.items.push(item);
  }

  pop(): T | undefined {
    return this.items.pop();
  }

  peek(): T | undefined {
    return this.items[this.items.length - 1];
  }

  isEmpty(): boolean {
    return this.items.length === 0;
  }

  size(): number {
    return this.items.length;
  }
}

// Usage — type-safe!
const numberStack = new Stack<number>();
numberStack.push(1);
numberStack.push(2);
// numberStack.push("hello");  // ❌ Error: string not assignable to number
const top = numberStack.pop(); // number | undefined

const stringStack = new Stack<string>();
stringStack.push("hello");
```

### Generic Class with Multiple Parameters
```typescript
class KeyValuePair<K, V> {
  constructor(
    public key: K,
    public value: V
  ) {}
}

class Cache<K, V> {
  private store = new Map<K, V>();

  set(key: K, value: V): void {
    this.store.set(key, value);
  }

  get(key: K): V | undefined {
    return this.store.get(key);
  }

  has(key: K): boolean {
    return this.store.has(key);
  }
}

const userCache = new Cache<number, User>();
userCache.set(1, { id: 1, name: "Vinay" });
const user = userCache.get(1);  // User | undefined
```

---

## Generic Constraints ⭐⭐⭐

Restrict what types can be used as a generic parameter.

### extends Constraint
```typescript
// T must have an 'id' property
function getId<T extends { id: number }>(entity: T): number {
  return entity.id;
}

getId({ id: 1, name: "Vinay" });  // ✅ Has id
getId({ id: 2 });                  // ✅ Has id
// getId({ name: "Vinay" });       // ❌ Missing id

// T must have a 'length' property
function logLength<T extends { length: number }>(item: T): void {
  console.log(item.length);
}

logLength("hello");     // ✅ string has length
logLength([1, 2, 3]);   // ✅ array has length
// logLength(42);        // ❌ number has no length
```

### keyof Constraint ⭐⭐⭐
```typescript
// K must be a key of T
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}

const user = { id: 1, name: "Vinay", age: 25 };
const name = getProperty(user, "name");   // string
const age = getProperty(user, "age");     // number
// getProperty(user, "email");             // ❌ "email" is not a key of user
```

### Multiple Constraints
```typescript
// T must satisfy both constraints
interface HasId { id: number; }
interface Serializable { serialize(): string; }

function process<T extends HasId & Serializable>(item: T): string {
  console.log(`Processing item ${item.id}`);
  return item.serialize();
}
```

---

## Default Generic Types

```typescript
// Default type parameter
interface ApiResponse<T = unknown> {
  data: T;
  status: number;
}

// Can use without specifying T
const response: ApiResponse = { data: "anything", status: 200 };
// Or specify explicitly
const userResponse: ApiResponse<User> = { data: user, status: 200 };

// Default with constraint
interface Collection<T extends object = Record<string, unknown>> {
  items: T[];
  add(item: T): void;
}

// Event emitter with defaults
class EventEmitter<Events extends Record<string, any> = Record<string, any>> {
  on<K extends keyof Events>(event: K, handler: (data: Events[K]) => void): void {
    // ...
  }
}

// Typed events
interface AppEvents {
  login: { userId: number };
  logout: { reason: string };
  error: { message: string; code: number };
}

const emitter = new EventEmitter<AppEvents>();
emitter.on("login", (data) => data.userId);  // data typed as { userId: number }
emitter.on("error", (data) => data.code);    // data typed as { message, code }
```

---

## Generic Utility Patterns ⭐⭐⭐

### Wrapper/Container Pattern
```typescript
// Result type (like Java Optional but better)
type Result<T, E = Error> =
  | { success: true; data: T }
  | { success: false; error: E };

function divide(a: number, b: number): Result<number, string> {
  if (b === 0) return { success: false, error: "Division by zero" };
  return { success: true, data: a / b };
}

const result = divide(10, 2);
if (result.success) {
  console.log(result.data);   // number — safely narrowed
} else {
  console.log(result.error);  // string — safely narrowed
}
```

### Builder Pattern with Generics
```typescript
class QueryBuilder<T> {
  private conditions: string[] = [];
  private orderByField?: keyof T;

  where<K extends keyof T>(field: K, value: T[K]): this {
    this.conditions.push(`${String(field)} = ${value}`);
    return this;
  }

  orderBy(field: keyof T): this {
    this.orderByField = field;
    return this;
  }

  build(): string {
    let query = `SELECT * FROM table`;
    if (this.conditions.length) {
      query += ` WHERE ${this.conditions.join(" AND ")}`;
    }
    if (this.orderByField) {
      query += ` ORDER BY ${String(this.orderByField)}`;
    }
    return query;
  }
}

const query = new QueryBuilder<User>()
  .where("name", "Vinay")     // ✅ "name" is key of User, value is string
  .where("age", 25)           // ✅ "age" is key of User, value is number
  // .where("name", 42)       // ❌ Error: 42 not assignable to string
  .orderBy("age")
  .build();
```

### Factory Pattern
```typescript
interface Constructor<T> {
  new (...args: any[]): T;
}

function createInstance<T>(ctor: Constructor<T>, ...args: any[]): T {
  return new ctor(...args);
}

class UserService { constructor(public apiUrl: string) {} }
const service = createInstance(UserService, "http://api.com");
// service is typed as UserService
```

---

## Generics in Angular/React Context

### Angular HTTP Service
```typescript
// Angular HttpClient uses generics
@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  get<T>(url: string): Observable<T> {
    return this.http.get<T>(url);
  }

  post<TRequest, TResponse>(url: string, body: TRequest): Observable<TResponse> {
    return this.http.post<TResponse>(url, body);
  }

  getUsers(): Observable<ApiResponse<User[]>> {
    return this.get<ApiResponse<User[]>>("/api/users");
  }
}
```

### React Component Props
```typescript
// Generic React component
interface ListProps<T> {
  items: T[];
  renderItem: (item: T) => React.ReactNode;
  keyExtractor: (item: T) => string;
}

function List<T>({ items, renderItem, keyExtractor }: ListProps<T>) {
  return (
    <ul>
      {items.map(item => (
        <li key={keyExtractor(item)}>{renderItem(item)}</li>
      ))}
    </ul>
  );
}

// Usage — T inferred from items
<List
  items={users}
  renderItem={(user) => <span>{user.name}</span>}  // user: User
  keyExtractor={(user) => user.id.toString()}
/>
```

---

## TypeScript Generics vs Java Generics

| Feature | Java | TypeScript |
|---------|------|-----------|
| Syntax | `<T>` | `<T>` |
| Erasure | Generics erased at runtime | ALL types erased |
| Runtime type check | Cannot (`instanceof T`) | Cannot |
| Constraints | `<T extends Comparable>` | `<T extends { compareTo(): number }>` |
| Variance | Use-site (`? extends`, `? super`) | Declaration-site (`in`, `out` keywords) |
| Default types | Not supported | `<T = DefaultType>` |
| Conditional types | Not supported | `T extends U ? X : Y` |
| Mapped types | Not supported | `{ [K in keyof T]: ... }` |
| keyof | Not supported | `keyof T` → union of keys |
| Wildcard | `?` | `unknown` or conditional types |

---

## Key Interview Questions

**Q: Explain generics in TypeScript with a real example.**
> Generics provide type-safe reusability. Example: `ApiResponse<T>` interface can wrap any response type — `ApiResponse<User>`, `ApiResponse<Product[]>`. The generic parameter `T` is replaced with the actual type at usage. This gives type safety (autocomplete, error detection) without duplicating code. Angular's `HttpClient.get<T>()` and React's `useState<T>()` are classic examples.

**Q: What is `keyof` and how is it used with generics?**
> `keyof T` produces a union of all property names of type T as string literals. Combined with generics: `function get<T, K extends keyof T>(obj: T, key: K): T[K]` ensures you can only access valid keys, and the return type matches the property type. It's TypeScript's way of providing compile-time property name checking.

**Q: How do generic constraints work?**
> `<T extends SomeType>` restricts T to only types that satisfy SomeType's structure. It ensures the generic type has certain properties you need. Example: `<T extends { id: number }>` means T must have a numeric id property. This gives you type safety inside the function while keeping it generic.

**Q: What's the difference between `any` and generics?**
> `any` discards type information — input type is forgotten for the output. Generics preserve type information through the operation. `identity(x: any): any` → caller loses the type. `identity<T>(x: T): T` → caller gets back the same type they put in. Generics = type-safe reusability.
