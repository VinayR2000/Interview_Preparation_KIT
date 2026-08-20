# TypeScript Modules and Configuration

## Module System

### ES Modules (Standard)
```typescript
// Named exports
export interface User {
  id: number;
  name: string;
}

export class UserService {
  getUsers(): User[] { return []; }
}

export const API_URL = "http://localhost:8080";

export function createUser(name: string): User {
  return { id: Date.now(), name };
}

// Default export (one per file)
export default class AuthService {
  login(email: string, password: string): Promise<string> { /* ... */ }
}
```

### Imports
```typescript
// Named imports
import { User, UserService, API_URL } from "./user.service";

// Default import
import AuthService from "./auth.service";

// Rename import (avoid conflicts)
import { User as UserModel } from "./models";

// Namespace import
import * as UserModule from "./user.service";
UserModule.createUser("Vinay");

// Type-only imports (erased at compile time — no runtime import)
import type { User } from "./models";
import { type User, createUser } from "./user.service";
// Use when you only need the type, not the runtime value
```

### Barrel Files (index.ts)
```typescript
// src/models/index.ts — re-exports from a folder
export { User } from "./user.model";
export { Order } from "./order.model";
export { Product } from "./product.model";
export type { ApiResponse } from "./api.types";

// Consumer imports from the barrel
import { User, Order, Product } from "./models";
// Instead of:
// import { User } from "./models/user.model";
// import { Order } from "./models/order.model";
```

---

### CommonJS (Node.js Legacy)
```typescript
// CommonJS exports
module.exports = { UserService };
exports.createUser = createUser;

// CommonJS imports
const { UserService } = require("./user.service");

// In tsconfig, use esModuleInterop for compatibility:
// import UserService from "./user.service";  (even for CJS default)
```

### Module Resolution
```typescript
// TypeScript resolves modules in this order:
// 1. Relative path: import { User } from "./models/user"
//    → ./models/user.ts, ./models/user/index.ts
//
// 2. Non-relative (node_modules): import { Observable } from "rxjs"
//    → node_modules/rxjs/index.ts or package.json "types" field
//
// 3. Path aliases (from tsconfig paths):
//    import { User } from "@app/models/user"
//    → mapped via paths configuration
```

---

## tsconfig.json ⭐⭐⭐

The TypeScript configuration file — controls compilation behavior.

### Essential Configuration
```json
{
  "compilerOptions": {
    // === TYPE CHECKING ===
    "strict": true,                    // Enable ALL strict checks (recommended)
    "noImplicitAny": true,             // Error on implicit 'any'
    "strictNullChecks": true,          // null/undefined are distinct types
    "strictFunctionTypes": true,       // Strict function parameter types
    "noUnusedLocals": true,            // Error on unused variables
    "noUnusedParameters": true,        // Error on unused parameters
    "noImplicitReturns": true,         // Error if not all paths return

    // === MODULE SYSTEM ===
    "module": "ESNext",                // Output module format
    "moduleResolution": "bundler",     // How to find modules (Node, Bundler)
    "esModuleInterop": true,           // Allow default imports from CJS
    "resolveJsonModule": true,         // Allow importing .json files

    // === COMPILATION TARGET ===
    "target": "ES2022",                // JavaScript version to output
    "lib": ["ES2022", "DOM"],          // Available type definitions

    // === OUTPUT ===
    "outDir": "./dist",                // Output directory
    "rootDir": "./src",                // Source root directory
    "sourceMap": true,                 // Generate .map files for debugging
    "declaration": true,               // Generate .d.ts files

    // === PATH ALIASES ===
    "baseUrl": "./src",
    "paths": {
      "@app/*": ["app/*"],
      "@shared/*": ["shared/*"],
      "@models/*": ["models/*"]
    },

    // === MISC ===
    "skipLibCheck": true,              // Skip type checking .d.ts files (faster)
    "forceConsistentCasingInFileNames": true,
    "experimentalDecorators": true,    // Required for Angular
    "emitDecoratorMetadata": true      // Required for Angular DI
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "**/*.spec.ts"]
}
```

---

### Key Options Explained

#### strict: true ⭐⭐⭐
Enables ALL strict type-checking options at once:
```json
"strict": true
// Equivalent to enabling ALL of these:
// "noImplicitAny": true
// "noImplicitThis": true
// "alwaysStrict": true
// "strictBindCallApply": true
// "strictNullChecks": true
// "strictFunctionTypes": true
// "strictPropertyInitialization": true
// "useUnknownInCatchVariables": true
```

**Always use `strict: true`** — it's the most impactful single setting for code quality.

#### target
What JavaScript version to compile to:
```
"ES5"    → var, no arrow functions, no async/await
"ES2015" → let/const, arrow functions, classes
"ES2017" → async/await
"ES2020" → optional chaining, nullish coalescing
"ES2022" → class fields, top-level await
"ESNext" → Latest features
```

#### module
What module system to output:
```
"CommonJS" → require/exports (Node.js)
"ESNext"   → import/export (modern bundlers)
"AMD"      → define (RequireJS — legacy)
```

#### moduleResolution
How TypeScript finds imported modules:
```
"node"     → Node.js resolution (node_modules, index.js)
"bundler"  → Modern bundler resolution (recommended for Vite/Webpack)
"classic"  → Legacy TypeScript resolution (avoid)
```

---

### Angular tsconfig
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "strict": true,
    "sourceMap": true,
    "declaration": false,
    "experimentalDecorators": true,
    "emitDecoratorMetadata": true,
    "importHelpers": true,
    "lib": ["ES2022", "DOM"],
    "baseUrl": "./",
    "paths": {
      "@app/*": ["src/app/*"],
      "@env/*": ["src/environments/*"]
    }
  }
}
```

### React tsconfig (Vite)
```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "jsx": "react-jsx",
    "sourceMap": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

---

## Declaration Files (.d.ts)

### What are Declaration Files?
Type definitions for JavaScript libraries that don't include types.

```typescript
// node_modules/@types/lodash/index.d.ts
declare module "lodash" {
  export function get(object: any, path: string): any;
  export function debounce<T extends (...args: any[]) => any>(
    func: T,
    wait: number
  ): T;
}
```

### Installing Type Definitions
```bash
# Many libraries ship their own types
npm install axios  # includes types

# For libraries without types, install from DefinitelyTyped
npm install --save-dev @types/express
npm install --save-dev @types/node
npm install --save-dev @types/jest
```

### Writing Custom Declarations
```typescript
// src/types/custom.d.ts

// Declare a module without types
declare module "untyped-lib" {
  export function doSomething(input: string): number;
}

// Augment existing types
declare global {
  interface Window {
    __APP_CONFIG__: {
      apiUrl: string;
      version: string;
    };
  }
}

// Declare environment variables
declare namespace NodeJS {
  interface ProcessEnv {
    NODE_ENV: "development" | "production" | "test";
    API_URL: string;
    DATABASE_URL: string;
  }
}
```

---

## Module Patterns for Full-Stack

### Shared Types Between Frontend and Backend
```typescript
// shared/types/user.ts (shared package or monorepo)
export interface User {
  id: number;
  name: string;
  email: string;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
}

export interface ApiResponse<T> {
  data: T;
  message: string;
  timestamp: string;
}

// Frontend (Angular/React) imports the same types
// Backend (Node.js) uses them for validation
```

### Feature Module Organization
```
src/
├── features/
│   ├── users/
│   │   ├── index.ts          (barrel)
│   │   ├── user.model.ts
│   │   ├── user.service.ts
│   │   ├── user.component.ts
│   │   └── user.types.ts
│   └── orders/
│       ├── index.ts
│       ├── order.model.ts
│       └── order.service.ts
├── shared/
│   ├── index.ts
│   ├── api.types.ts
│   ├── http.service.ts
│   └── utils.ts
└── app.ts
```

---

## Key Interview Questions

**Q: What does `strict: true` enable and why should you always use it?**
> It enables all strict type-checking flags: `noImplicitAny`, `strictNullChecks`, `strictFunctionTypes`, etc. Without it, TypeScript allows implicit `any` types and null is assignable to everything — defeating the purpose of TypeScript. Microsoft research shows strict mode catches significantly more bugs. Always start new projects with strict mode.

**Q: What's the difference between `target` and `module` in tsconfig?**
> `target` determines what JavaScript version features to use in output (affects syntax: arrow functions, async/await, etc.). `module` determines the module system format (import/export → ESModules vs require/exports → CommonJS). You might target ES2015 (for syntax) but output CommonJS modules (for Node.js).

**Q: What are declaration files (.d.ts) and when do you need them?**
> Declaration files provide type information for JavaScript code without TypeScript source. You need them when: (1) using a JS library without bundled types — install `@types/libname`, (2) creating a library — generate with `declaration: true`, (3) augmenting global types — declare global interfaces. They have no runtime code, only type information.

**Q: How do path aliases work and why use them?**
> Path aliases (tsconfig `paths`) map import paths like `@app/models` to actual directories. Benefits: cleaner imports (no `../../../`), refactoring-friendly (move files without changing imports everywhere), and consistent convention. Must also configure bundler/build tool (Webpack, Vite) to understand the same aliases.

**Q: What is `import type` and when should you use it?**
> `import type { User }` imports only the type, ensuring it's completely erased at compile time with no runtime import. Use it when you only need the type for annotations, not the actual value. Benefits: prevents circular dependency issues, enables better tree-shaking, and makes intent clear. TypeScript 5.0+ has `verbatimModuleSyntax` which enforces this.
