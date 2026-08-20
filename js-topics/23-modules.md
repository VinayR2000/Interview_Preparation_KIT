# JavaScript Modules

## ES Modules (ESM)

### Named Exports / Imports
```javascript
// math.js
export const PI = 3.14159;
export function add(a, b) { return a + b; }
export class Calculator { }

// main.js
import { add, PI, Calculator } from './math.js';
import { add as sum } from './math.js';     // Rename
import * as MathUtils from './math.js';     // All as namespace
MathUtils.add(1, 2);
```

### Default Export / Import
```javascript
// User.js
export default class User {
  constructor(name) { this.name = name; }
}

// main.js
import User from './User.js';        // Any name works
import MyUser from './User.js';      // Same thing, different name
```

### Re-export
```javascript
// index.js (barrel file)
export { add, subtract } from './math.js';
export { default as User } from './User.js';
export * from './utils.js';
```

### Dynamic Import
```javascript
// Load module only when needed (code splitting)
const button = document.getElementById('heavy-feature');
button.addEventListener('click', async () => {
  const { heavyFunction } = await import('./heavy-module.js');
  heavyFunction();
});
```

---

## CommonJS (CJS)

```javascript
// math.js
function add(a, b) { return a + b; }
module.exports = { add };
// or
exports.add = function(a, b) { return a + b; };

// main.js
const { add } = require('./math.js');
const math = require('./math.js');
math.add(1, 2);
```

---

## ESM vs CommonJS

| Feature | ESM | CommonJS |
|---------|-----|----------|
| Syntax | `import/export` | `require/module.exports` |
| Loading | Static (compile-time) | Dynamic (runtime) |
| Async | Yes (top-level await) | No |
| Tree shaking | ✅ (static analysis) | ❌ |
| Browser | ✅ Native | ❌ (needs bundler) |
| Node.js | ✅ (.mjs or "type": "module") | ✅ (default) |
| Strict mode | Always | Optional |
| `this` at top level | undefined | module object |

---

## Module Resolution

```javascript
// Relative paths
import './utils.js';       // Same directory
import '../lib/math.js';   // Parent directory

// Package imports (node_modules)
import React from 'react';
import { useState } from 'react';

// Node.js built-in
import fs from 'node:fs';
import path from 'node:path';
```

---

## Key Interview Questions

**Q: Why are ES modules better for bundling?**
> ES modules have static imports (known at compile time). Bundlers can analyze the dependency graph and tree-shake unused exports. CommonJS `require` is dynamic (runtime), so bundlers can't safely remove unused code.

**Q: What is tree shaking?**
> Dead code elimination by bundlers. If you `import { add } from 'math'` and math also exports `subtract`, tree shaking removes `subtract` from the final bundle since it's never imported.

**Q: What's a barrel file?**
> An `index.js` that re-exports from multiple modules, providing a single import point: `import { UserService, AuthService } from './services'` instead of importing from each file separately.

**Q: Can you use `require` and `import` in the same file?**
> Not in standard ESM. In Node.js ESM, you can use `createRequire` to access CommonJS modules. In bundlers like Webpack, you can mix them (but shouldn't). Pick one and be consistent.
