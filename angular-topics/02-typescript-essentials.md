# 2. TypeScript — Must Know for Angular

---

## Theory

TypeScript is a **statically-typed superset of JavaScript** that compiles to plain JavaScript. Angular is built entirely in TypeScript and requires it for development.

### Why TypeScript for Angular?

- **Type Safety**: Catch errors at compile time, not runtime
- **IDE Support**: Better autocompletion, refactoring, navigation
- **Decorators**: Angular relies heavily on decorators (`@Component`, `@Injectable`)
- **Interfaces**: Define contracts for component inputs, service responses
- **Generics**: Type-safe collections, HTTP responses, observables

### Variables: let, const, var

```typescript
var x = 10;    // Function-scoped, hoisted, can be redeclared — AVOID
let y = 20;    // Block-scoped, not hoisted, cannot be redeclared
const z = 30;  // Block-scoped, cannot be reassigned (reference is fixed)

// const with objects — properties can change
const user = { name: 'John' };
user.name = 'Jane';  // ✅ Valid — object reference unchanged
// user = {};         // ❌ Error — cannot reassign const
```

### Data Types

```typescript
// Primitives
let name: string = 'Angular';
let age: number = 4;
let isActive: boolean = true;
let nothing: null = null;
let notDefined: undefined = undefined;

// Arrays
let numbers: number[] = [1, 2, 3];
let names: Array<string> = ['a', 'b', 'c'];

// Tuple — fixed-length array with specific types
let pair: [string, number] = ['age', 25];

// Enum
enum Status { Active, Inactive, Pending }
let s: Status = Status.Active;  // 0

enum HttpStatus {
  OK = 200,
  NotFound = 404,
  ServerError = 500
}

// String enum
enum Direction {
  Up = 'UP',
  Down = 'DOWN',
  Left = 'LEFT',
  Right = 'RIGHT'
}
```

### Interfaces

```typescript
// Basic interface
interface User {
  id: number;
  name: string;
  email: string;
  age?: number;          // Optional property
  readonly createdAt: Date; // Cannot be modified after creation
}

// Method signature in interface
interface UserService {
  getUser(id: number): User;
  getUsers(): User[];
  createUser(user: Omit<User, 'id'>): User;
}

// Extending interfaces
interface Employee extends User {
  department: string;
  salary: number;
}

// Index signature
interface Dictionary {
  [key: string]: string;
}
```

### Classes

```typescript
class Employee {
  // Access modifiers
  public name: string;         // Accessible everywhere (default)
  private salary: number;      // Only within class
  protected department: string; // Within class and subclasses
  readonly id: number;         // Cannot change after initialization

  constructor(name: string, salary: number, department: string, id: number) {
    this.name = name;
    this.salary = salary;
    this.department = department;
    this.id = id;
  }

  // Shorthand constructor (parameter properties)
  // Equivalent to above:
  // constructor(
  //   public name: string,
  //   private salary: number,
  //   protected department: string,
  //   readonly id: number
  // ) {}

  getSalary(): number {
    return this.salary;
  }
}
```

### Union and Intersection Types

```typescript
// Union — value can be one of several types
type Status = 'active' | 'inactive' | 'pending';
type ID = string | number;

function printId(id: ID): void {
  if (typeof id === 'string') {
    console.log(id.toUpperCase()); // Type narrowing
  } else {
    console.log(id.toFixed(2));
  }
}

// Intersection — combines multiple types
interface HasName { name: string; }
interface HasAge { age: number; }

type Person = HasName & HasAge;
// Person must have both name AND age

const person: Person = { name: 'John', age: 30 }; // ✅
```

### Generics

```typescript
// Generic function
function identity<T>(value: T): T {
  return value;
}
const result = identity<string>('hello'); // Type inferred: string

// Generic interface
interface ApiResponse<T> {
  data: T;
  status: number;
  message: string;
  timestamp: Date;
}

// Usage
const userResponse: ApiResponse<User> = {
  data: { id: 1, name: 'John', email: 'john@example.com', createdAt: new Date() },
  status: 200,
  message: 'Success',
  timestamp: new Date()
};

// Generic class
class Repository<T extends { id: number }> {
  private items: T[] = [];

  add(item: T): void { this.items.push(item); }
  findById(id: number): T | undefined {
    return this.items.find(item => item.id === id);
  }
  getAll(): T[] { return [...this.items]; }
}

// Generic constraints
function getProperty<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}
```

### Type Aliases

```typescript
type StringOrNumber = string | number;
type Callback = (data: string) => void;
type Nullable<T> = T | null;
type ReadonlyUser = Readonly<User>;
type PartialUser = Partial<User>;
type RequiredUser = Required<User>;
type UserKeys = keyof User; // 'id' | 'name' | 'email' | 'age' | 'createdAt'
type PickedUser = Pick<User, 'id' | 'name'>;
type OmittedUser = Omit<User, 'createdAt'>;
```

### Access Modifiers

| Modifier | Class | Subclass | Outside |
|----------|-------|----------|---------|
| `public` | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ❌ |
| `private` | ✅ | ❌ | ❌ |
| `readonly` | ✅ (read) | ✅ (read) | ✅ (read) |

### Abstract Classes

```typescript
abstract class Shape {
  abstract area(): number;           // Must be implemented by subclass
  abstract perimeter(): number;

  describe(): string {               // Concrete method — inherited as-is
    return `Area: ${this.area()}, Perimeter: ${this.perimeter()}`;
  }
}

class Circle extends Shape {
  constructor(private radius: number) { super(); }

  area(): number { return Math.PI * this.radius ** 2; }
  perimeter(): number { return 2 * Math.PI * this.radius; }
}
```

### Interfaces vs Abstract Classes

| Feature | Interface | Abstract Class |
|---------|-----------|---------------|
| Implementation | No (only signatures) | Can have concrete methods |
| Multiple | Can implement many | Can extend only one |
| Constructor | ❌ | ✅ |
| Access modifiers | ❌ | ✅ |
| Performance | No runtime cost | Has runtime cost |
| Use case | Contracts, shapes | Shared base behavior |

### Arrow Functions

```typescript
// Traditional function
function add(a: number, b: number): number {
  return a + b;
}

// Arrow function
const add = (a: number, b: number): number => a + b;

// Key differences:
// 1. Arrow functions don't have their own 'this' — they capture from enclosing scope
// 2. Can't be used as constructors
// 3. Don't have 'arguments' object

// Important for Angular:
// In callbacks, arrow functions preserve 'this' context
class UserComponent {
  users: string[] = [];
  
  loadUsers(): void {
    this.userService.getUsers().subscribe(
      (users) => this.users = users  // 'this' refers to UserComponent ✅
    );
  }
}
```

### Destructuring

```typescript
// Object destructuring
const { name, age, email } = user;
const { name: userName, age: userAge } = user; // Rename

// Array destructuring
const [first, second, ...rest] = [1, 2, 3, 4, 5];

// Function parameter destructuring
function displayUser({ name, email }: User): void {
  console.log(`${name}: ${email}`);
}

// Default values
const { name = 'Anonymous', age = 0 } = partialUser;
```

### Spread/Rest Operators

```typescript
// Spread — expand elements
const arr1 = [1, 2, 3];
const arr2 = [...arr1, 4, 5]; // [1, 2, 3, 4, 5]

const obj1 = { name: 'John', age: 30 };
const obj2 = { ...obj1, email: 'john@test.com' }; // Merge objects

// Rest — collect remaining elements
function sum(...numbers: number[]): number {
  return numbers.reduce((acc, n) => acc + n, 0);
}
```

### Optional Chaining and Nullish Coalescing

```typescript
// Optional chaining — safely access nested properties
const street = user?.address?.street; // undefined if any is null/undefined

// Nullish coalescing — default value only for null/undefined
const name = user.name ?? 'Anonymous'; // '' stays as '' (not replaced)
const name2 = user.name || 'Anonymous'; // '' becomes 'Anonymous' (falsy)

// Difference:
// ?? → only null/undefined triggers default
// || → any falsy value (0, '', false, null, undefined) triggers default
```

### Type Guards

```typescript
// typeof guard
function process(value: string | number): void {
  if (typeof value === 'string') {
    console.log(value.toUpperCase()); // TypeScript knows it's string here
  }
}

// instanceof guard
function handleError(error: Error | HttpErrorResponse): void {
  if (error instanceof HttpErrorResponse) {
    console.log(error.status); // TypeScript knows it's HttpErrorResponse
  }
}

// Custom type guard
interface Cat { meow(): void; }
interface Dog { bark(): void; }

function isCat(animal: Cat | Dog): animal is Cat {
  return (animal as Cat).meow !== undefined;
}

// 'in' operator guard
function move(pet: Cat | Dog): void {
  if ('meow' in pet) {
    pet.meow(); // Cat
  } else {
    pet.bark(); // Dog
  }
}
```

### any, unknown, never

```typescript
// any — opt out of type checking (AVOID)
let anything: any = 'hello';
anything = 42;
anything.foo(); // No error at compile time — may crash at runtime

// unknown — type-safe any (PREFER)
let notSure: unknown = 'hello';
// notSure.foo(); // ❌ Error — must narrow type first
if (typeof notSure === 'string') {
  notSure.toUpperCase(); // ✅ Now safe
}

// never — function never returns
function throwError(msg: string): never {
  throw new Error(msg);
}

function infiniteLoop(): never {
  while (true) { }
}

// Exhaustive check
type Shape = 'circle' | 'square';
function getArea(shape: Shape): number {
  switch (shape) {
    case 'circle': return Math.PI;
    case 'square': return 1;
    default:
      const _exhaustive: never = shape; // Error if new shape added
      return _exhaustive;
  }
}
```

### Promises and Async/Await

```typescript
// Promise
function fetchUser(id: number): Promise<User> {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (id > 0) resolve({ id, name: 'John', email: 'j@test.com', createdAt: new Date() });
      else reject(new Error('Invalid ID'));
    }, 1000);
  });
}

// Async/Await
async function loadUser(id: number): Promise<void> {
  try {
    const user = await fetchUser(id);
    console.log(user.name);
  } catch (error) {
    console.error('Failed to load user:', error);
  }
}

// In Angular, prefer RxJS Observables over Promises
// Promises: single value, eager, not cancellable
// Observables: stream of values, lazy, cancellable
```

### Modules

```typescript
// Named exports
export interface User { id: number; name: string; }
export class UserService { }
export const API_URL = 'http://localhost:8080';

// Default export
export default class AuthService { }

// Imports
import { User, UserService, API_URL } from './user.service';
import AuthService from './auth.service'; // Default
import * as Utils from './utils'; // Namespace import

// Re-export (barrel file — index.ts)
export { User } from './user.model';
export { UserService } from './user.service';
export { AuthService } from './auth.service';
```

---

## Internal Working

### TypeScript Compilation Pipeline

```
TypeScript Source (.ts)
       ↓ tsc (TypeScript Compiler)
       ↓ 1. Parsing → AST
       ↓ 2. Type Checking → Error detection
       ↓ 3. Emit → JavaScript output
JavaScript (.js) + Type Declarations (.d.ts) + Source Maps (.map)
```

### Type Erasure

```
TypeScript (compile time):     JavaScript (runtime):
interface User { }             // Interface REMOVED
const x: number = 5;          const x = 5;          // Type annotation REMOVED
function f(a: string) {}      function f(a) {}      // Type REMOVED

// Types exist ONLY at compile time — zero runtime cost
// Decorators and enums DO have runtime representation
```

---

## Diagram

```
TypeScript Type System Hierarchy:
                    any
                     │
              ┌──────┼──────┐
              │      │      │
           unknown   │    never (bottom)
              │      │
    ┌─────────┼──────┼─────────┐
    │         │      │         │
  string   number  boolean   object
    │         │      │         │
    │         │      │    ┌────┼────┐
    │         │      │    │    │    │
 literal   literal literal Array Function Class
  types    types   types
```

---

## Code

```typescript
// Comprehensive TypeScript example for Angular development
interface ApiResponse<T> {
  data: T;
  meta: {
    total: number;
    page: number;
    pageSize: number;
  };
  errors?: string[];
}

interface Employee {
  id: number;
  name: string;
  email: string;
  department: Department;
  salary: number;
  joinDate: Date;
  manager?: Employee;
}

enum Department {
  Engineering = 'ENGINEERING',
  Marketing = 'MARKETING',
  Sales = 'SALES',
  HR = 'HR'
}

type EmployeeCreateDTO = Omit<Employee, 'id' | 'joinDate'>;
type EmployeeUpdateDTO = Partial<EmployeeCreateDTO>;
type EmployeeSummary = Pick<Employee, 'id' | 'name' | 'department'>;

// Generic repository pattern
class BaseRepository<T extends { id: number }> {
  protected items: T[] = [];

  findAll(): T[] {
    return [...this.items];
  }

  findById(id: number): T | undefined {
    return this.items.find(item => item.id === id);
  }

  create(item: T): T {
    this.items.push(item);
    return item;
  }

  update(id: number, updates: Partial<T>): T | undefined {
    const index = this.items.findIndex(item => item.id === id);
    if (index === -1) return undefined;
    this.items[index] = { ...this.items[index], ...updates };
    return this.items[index];
  }

  delete(id: number): boolean {
    const index = this.items.findIndex(item => item.id === id);
    if (index === -1) return false;
    this.items.splice(index, 1);
    return true;
  }
}

// Type guard example
function isApiError(response: ApiResponse<unknown>): boolean {
  return !!response.errors && response.errors.length > 0;
}

// Mapped type utility
type Readonly<T> = { readonly [P in keyof T]: T[P] };
type Optional<T> = { [P in keyof T]?: T[P] };
```

---

## Dry Run

### Generic Type Resolution

```
Code: const repo = new BaseRepository<Employee>();
      repo.create({ id: 1, name: 'John', ... });
      const emp = repo.findById(1);

Step 1: T resolves to Employee
Step 2: create(item: Employee) — type checked
Step 3: findById returns Employee | undefined
Step 4: Must handle undefined before accessing properties
        if (emp) { emp.name } // TypeScript narrows to Employee
```

---

## Complexity

| Feature | Compile-Time Cost | Runtime Cost |
|---------|-------------------|--------------|
| Interfaces | Type checking | Zero (erased) |
| Generics | Type checking | Zero (erased) |
| Enums | Minimal | Object creation |
| Decorators | Metadata generation | Function wrapping |
| Type Guards | Type checking | Runtime check (typeof/instanceof) |

---

## Real Project Usage

```typescript
// In Angular services — typed HTTP responses
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private apiUrl = '/api/employees';

  constructor(private http: HttpClient) {}

  getEmployees(page: number, size: number): Observable<ApiResponse<Employee[]>> {
    return this.http.get<ApiResponse<Employee[]>>(this.apiUrl, {
      params: { page: page.toString(), size: size.toString() }
    });
  }

  createEmployee(dto: EmployeeCreateDTO): Observable<ApiResponse<Employee>> {
    return this.http.post<ApiResponse<Employee>>(this.apiUrl, dto);
  }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between `interface` and `type` in TypeScript?**
> Both can define object shapes. Key differences: interfaces can be extended/merged (declaration merging), types cannot. Types can represent unions, intersections, primitives, tuples — interfaces cannot. In Angular, use interfaces for object shapes (DTOs, models) and types for unions/utilities.

**Q2: What is the difference between `any` and `unknown`?**
> `any` disables type checking entirely — you can call any method on it without error. `unknown` is type-safe: you must narrow the type (using typeof, instanceof, or type guards) before accessing properties. Always prefer `unknown` over `any` when the type is truly unknown.

**Q3: Explain generics with a real Angular example.**
> Generics provide type-safe reusability. Angular's `HttpClient.get<T>()` is generic — you specify the response type: `http.get<User[]>('/api/users')`. The Observable returned is `Observable<User[]>`, giving autocomplete and type checking on the response. Without generics, you'd need manual type casting or `any`.

**Q4: What are TypeScript utility types you use in Angular?**
> `Partial<T>` — all properties optional (update DTOs). `Required<T>` — all required. `Pick<T, K>` — subset of properties. `Omit<T, K>` — exclude properties. `Record<K, V>` — dictionary type. `Readonly<T>` — immutable. Example: `Omit<User, 'id'>` for create DTOs where ID is server-generated.

**Q5: How does TypeScript handle null safety?**
> With `strictNullChecks: true` (recommended), `null` and `undefined` are not assignable to other types unless explicitly included: `let name: string | null`. Use optional chaining (`user?.address?.city`) and nullish coalescing (`name ?? 'default'`). Non-null assertion (`user!.name`) tells compiler you're sure it's not null — use sparingly.

---

## Follow-up Questions and Answers

**Q: Why does Angular require TypeScript?**
> Angular's decorator system (`@Component`, `@Injectable`) uses TypeScript's experimental decorators and metadata reflection. The DI system relies on type metadata emitted by TypeScript. Also, Angular's template compiler performs type checking against component TypeScript types for template type safety.

**Q: What is declaration merging and when is it useful?**
> When you declare two interfaces with the same name, TypeScript merges them. This is useful for augmenting third-party types: adding properties to Express's `Request`, extending Angular's `Router` events, or adding custom properties to `Window`.

**Q: What are conditional types?**
> `T extends U ? X : Y` — if T is assignable to U, type is X, otherwise Y. Used in Angular for extracting types: `type UnwrapObservable<T> = T extends Observable<infer U> ? U : T`. This extracts the inner type from an Observable.

---

## Common Mistakes

1. **Using `any` to silence compiler errors**
   ```typescript
   // ❌ Defeats the purpose of TypeScript
   const data: any = response;
   
   // ✅ Use proper typing or unknown
   const data: unknown = response;
   if (isUser(data)) { /* safe */ }
   ```

2. **Not using strict mode**
   ```json
   // tsconfig.json — always enable
   { "compilerOptions": { "strict": true } }
   ```

3. **Confusing `==` with `===`**
   ```typescript
   // ❌ Loose equality — type coercion
   if (value == null) { }
   
   // ✅ Strict equality (except null check above is actually OK)
   if (value === undefined || value === null) { }
   // Note: == null is acceptable idiom for null/undefined check
   ```

4. **Ignoring return type of async functions**
   ```typescript
   // ❌ Returns Promise<void>, not void
   async onClick(): void { } // ERROR
   
   // ✅ Correct
   async onClick(): Promise<void> { }
   ```

---

## Best Practices

1. **Enable strict mode** — `strict: true` in tsconfig.json.
2. **Prefer interfaces for object shapes**, types for unions/utilities.
3. **Never use `any`** — use `unknown` and narrow with type guards.
4. **Use readonly** for properties that shouldn't change.
5. **Use enums for fixed sets** of related values.
6. **Use generics** for reusable, type-safe code.
7. **Use barrel files** (`index.ts`) for clean imports.
8. **Use utility types** (`Partial`, `Pick`, `Omit`) for DTO variations.

---

## Production Considerations

- **Strict null checks prevent runtime `TypeError: Cannot read property of undefined`** — the #1 JavaScript error in production.
- **Compile-time type safety reduces bugs** — Microsoft research shows TypeScript catches 15% of bugs at compile time.
- **Source maps** should be disabled in production builds for security.
- **tsconfig paths** enable clean imports: `@app/services/user.service` instead of relative paths.

---

## Related Topics

- → [1. Angular Fundamentals](./01-angular-fundamentals.md)
- → [3. Components](./03-components.md)
- → [8. Generics in Services](./08-generics.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
