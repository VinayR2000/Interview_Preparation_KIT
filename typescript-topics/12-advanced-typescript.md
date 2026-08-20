# Advanced TypeScript

## Decorators

TypeScript decorators (used heavily in Angular):

```typescript
// Class decorator
function Component(config: { selector: string }) {
  return function <T extends { new (...args: any[]): {} }>(constructor: T) {
    return class extends constructor {
      selector = config.selector;
    };
  };
}

// Method decorator
function Log(target: any, propertyKey: string, descriptor: PropertyDescriptor) {
  const originalMethod = descriptor.value;
  descriptor.value = function (...args: any[]) {
    console.log(`Calling ${propertyKey} with`, args);
    const result = originalMethod.apply(this, args);
    console.log(`Result:`, result);
    return result;
  };
}

// Property decorator
function Required(target: any, propertyKey: string) {
  let value: any;
  Object.defineProperty(target, propertyKey, {
    get() { return value; },
    set(newValue) {
      if (newValue === undefined || newValue === null) {
        throw new Error(`${propertyKey} is required`);
      }
      value = newValue;
    }
  });
}

// Usage
@Component({ selector: 'app-user' })
class UserComponent {
  @Required
  name!: string;

  @Log
  greet(greeting: string): string {
    return `${greeting}, ${this.name}`;
  }
}
```

### Decorator Factories (Angular Style)
```typescript
// Angular uses decorators extensively:
@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent { }

@Injectable({ providedIn: 'root' })
export class UserService { }

@Pipe({ name: 'capitalize' })
export class CapitalizePipe { }
```

---

## Declaration Merging

When multiple declarations with the same name exist, TypeScript merges them:

```typescript
// Interface merging (most common)
interface User {
  name: string;
}

interface User {
  age: number;
}

// Merged result:
// interface User { name: string; age: number; }

// Use case: Augmenting third-party types
declare module 'express' {
  interface Request {
    user?: { id: number; role: string };
  }
}

// Namespace merging with function
function buildUser(name: string): User {
  return { name, age: 0 };
}
namespace buildUser {
  export function withAge(name: string, age: number): User {
    return { name, age };
  }
}

buildUser("Vinay");              // Function
buildUser.withAge("Vinay", 25); // Namespace member
```

---

## Module Augmentation

Extend types from external modules:

```typescript
// Augment Express Request (in your project)
import 'express';

declare module 'express' {
  interface Request {
    userId?: number;
    token?: string;
  }
}

// Augment Angular's Router
import '@angular/router';

declare module '@angular/router' {
  interface Route {
    data?: {
      title?: string;
      roles?: string[];
    };
  }
}

// Augment global Window
declare global {
  interface Window {
    __APP_VERSION__: string;
    analytics: {
      track(event: string, data?: Record<string, unknown>): void;
    };
  }
}
```

---

## `as const` and `satisfies`

### as const — Narrow to Literal Types
```typescript
// Without as const: types are widened
const config = {
  apiUrl: "http://localhost:8080",    // string (widened)
  port: 3000,                         // number (widened)
  modes: ["development", "production"] // string[] (widened)
};

// With as const: literal types preserved
const config2 = {
  apiUrl: "http://localhost:8080",    // "http://localhost:8080" (literal)
  port: 3000,                         // 3000 (literal)
  modes: ["development", "production"] // readonly ["development", "production"]
} as const;

// Use case: Creating const enums from objects
const HttpMethods = {
  GET: "GET",
  POST: "POST",
  PUT: "PUT",
  DELETE: "DELETE"
} as const;

type HttpMethod = typeof HttpMethods[keyof typeof HttpMethods];
// "GET" | "POST" | "PUT" | "DELETE"
```

### satisfies — Validate Without Widening (TypeScript 4.9+)
```typescript
// Problem: explicit type annotation widens
type Colors = Record<string, string | number[]>;
const palette: Colors = {
  red: [255, 0, 0],
  green: "#00ff00",
};
palette.red;  // string | number[] — lost the specific type!

// Solution: satisfies validates but preserves specific type
const palette2 = {
  red: [255, 0, 0],
  green: "#00ff00",
} satisfies Colors;

palette2.red;    // number[] — specific type preserved!
palette2.green;  // string — specific type preserved!

// Use case: Config objects with validation
interface AppConfig {
  apiUrl: string;
  port: number;
  debug: boolean;
}

const config = {
  apiUrl: "http://localhost:8080",
  port: 3000,
  debug: true,
} satisfies AppConfig;
// TypeScript validates it matches AppConfig
// BUT preserves literal types for each property
```

---

## Const Type Parameters (TypeScript 5.0+)

```typescript
// Without const: array inferred as string[]
function getNames<T extends readonly string[]>(names: T): T {
  return names;
}
const names = getNames(["Alice", "Bob"]);  // string[]

// With const type parameter: inferred as tuple of literals
function getNames2<const T extends readonly string[]>(names: T): T {
  return names;
}
const names2 = getNames2(["Alice", "Bob"]);  // readonly ["Alice", "Bob"]
```

---

## Type-Only Imports

```typescript
// Type-only import — guaranteed erasure, no runtime dependency
import type { User } from './models';
import type { Observable } from 'rxjs';

// Mixed import (TypeScript 4.5+)
import { createUser, type User, type CreateUserDTO } from './user.service';
// createUser → kept at runtime (it's a function)
// User, CreateUserDTO → erased (type-only)

// Type-only export
export type { User, CreateUserDTO };

// verbatimModuleSyntax (TypeScript 5.0+)
// Enforces explicit type imports — if you use `import type`, the import is erased
// If you use `import`, it must have a runtime value
```

---

## Ambient Declarations

Declare types for things that exist at runtime but TypeScript doesn't know about:

```typescript
// declare — tells TypeScript "this exists, trust me"

// Global variable (loaded from CDN script tag)
declare const $: JQuery;
declare const gtag: (...args: any[]) => void;

// Global function
declare function require(module: string): any;

// Class from external JS
declare class Chart {
  constructor(ctx: CanvasRenderingContext2D, config: ChartConfig);
  destroy(): void;
  update(): void;
}

// Namespace
declare namespace NodeJS {
  interface ProcessEnv {
    NODE_ENV: 'development' | 'production' | 'test';
    PORT: string;
  }
}
```

---

## Namespaces (Rare in Modern TypeScript)

```typescript
// Namespaces — legacy way to organize code (prefer ES modules)
namespace Validation {
  export interface Validator {
    validate(value: string): boolean;
  }

  export class EmailValidator implements Validator {
    validate(value: string): boolean {
      return /^[^@]+@[^@]+$/.test(value);
    }
  }

  export class PhoneValidator implements Validator {
    validate(value: string): boolean {
      return /^\d{10}$/.test(value);
    }
  }
}

// Usage
const emailValidator = new Validation.EmailValidator();

// NOTE: Prefer ES modules (import/export) over namespaces
// Namespaces are mainly useful for:
// 1. Declaration files (.d.ts)
// 2. Augmenting global scope
// 3. Legacy code compatibility
```

---

## Runtime Validation (Production TypeScript) ⭐⭐⭐

TypeScript types don't exist at runtime. External data needs validation.

```
External JSON (API response, user input)
    ↓ UNTRUSTED — no type guarantees
Runtime Validation (Zod, io-ts, class-validator)
    ↓ VALIDATED — matches expected shape
TypeScript Type
    ↓ TRUSTED — type-safe usage
Application Code
```

### Zod — Schema Validation
```typescript
import { z } from 'zod';

// Define schema (runtime) + infer type (compile time)
const UserSchema = z.object({
  id: z.number(),
  name: z.string().min(1),
  email: z.string().email(),
  age: z.number().min(0).max(150).optional(),
  role: z.enum(["admin", "user", "editor"]),
  createdAt: z.string().datetime()
});

// Infer TypeScript type from schema
type User = z.infer<typeof UserSchema>;
// { id: number; name: string; email: string; age?: number; role: "admin" | "user" | "editor"; createdAt: string }

// Validate at runtime
function processApiResponse(data: unknown): User {
  const result = UserSchema.safeParse(data);
  if (!result.success) {
    console.error("Validation failed:", result.error.issues);
    throw new Error("Invalid data");
  }
  return result.data;  // Type-safe User
}

// Use in API layer
async function fetchUser(id: number): Promise<User> {
  const response = await fetch(`/api/users/${id}`);
  const json = await response.json();  // unknown at type level
  return UserSchema.parse(json);       // Throws if invalid, returns User if valid
}
```

---

## ESLint and TypeScript

```json
// .eslintrc.json for TypeScript
{
  "parser": "@typescript-eslint/parser",
  "plugins": ["@typescript-eslint"],
  "extends": [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:@typescript-eslint/recommended-requiring-type-checking"
  ],
  "rules": {
    "@typescript-eslint/no-explicit-any": "error",
    "@typescript-eslint/no-unused-vars": "error",
    "@typescript-eslint/explicit-function-return-type": "warn",
    "@typescript-eslint/no-floating-promises": "error",
    "@typescript-eslint/strict-boolean-expressions": "warn"
  }
}
```

---

## Key Interview Questions

**Q: What's the difference between `as const` and `satisfies`?**
> `as const` makes values deeply readonly with literal types — it narrows types. `satisfies` validates that a value matches a type WITHOUT changing the inferred type — it checks constraints while preserving specifics. Use `as const` for constant configs/enums. Use `satisfies` when you want validation + specific types.

**Q: TypeScript provides compile-time safety. How do you handle runtime data (API responses)?**
> TypeScript types are erased at runtime — external JSON has no type guarantees. Use runtime validation libraries (Zod, io-ts, class-validator) to validate incoming data against schemas. This bridges the gap: schema validates at runtime, TypeScript type (inferred from schema) provides compile-time safety. Essential for any data crossing a trust boundary.

**Q: What are decorators and how does Angular use them?**
> Decorators are functions that modify classes/methods/properties at runtime. Angular uses them for metadata: `@Component` marks a class as a component with template/styles, `@Injectable` marks it for DI, `@Input`/`@Output` mark component communication points. Under the hood, decorators attach metadata that Angular's compiler and DI system read. TypeScript's `experimentalDecorators` flag is required.

**Q: How would you organize types in a large TypeScript project?**
> (1) Co-locate types with their feature (user.types.ts next to user.service.ts). (2) Shared types in a `shared/types` or `models` folder with barrel exports. (3) Use `import type` for type-only imports. (4) Derive types from source of truth (Omit/Pick/Partial from base interface). (5) For full-stack, consider auto-generating from OpenAPI spec or sharing types via monorepo package.
