# 1. JavaScript Fundamentals

---

## Theory

JavaScript is a **dynamically-typed, interpreted (JIT-compiled), multi-paradigm** programming language. It is the language of the web — runs in browsers and on servers (Node.js).

### What is JavaScript?

- Created by Brendan Eich in 1995 (in 10 days at Netscape)
- Originally called "Mocha" → "LiveScript" → "JavaScript"
- NOT related to Java (marketing decision)
- Single-threaded, non-blocking, event-driven
- Supports OOP, functional, and event-driven programming paradigms

### JavaScript vs Java

| Feature | JavaScript | Java |
|---------|-----------|------|
| Typing | Dynamic | Static |
| Compilation | JIT-compiled/interpreted | Compiled to bytecode |
| Paradigm | Multi-paradigm | Object-oriented |
| Inheritance | Prototypal | Class-based |
| Threading | Single-threaded (event loop) | Multi-threaded |
| Platform | Browser + Node.js | JVM |
| Syntax | C-like but flexible | C-like, strict |

### ECMAScript

- **ECMAScript** is the specification; **JavaScript** is the implementation
- Maintained by TC39 committee
- Key versions:
  - ES3 (1999) — Regular expressions, try/catch
  - ES5 (2009) — strict mode, JSON, Array methods
  - ES6/ES2015 — let/const, arrow functions, classes, Promises, modules
  - ES2016+ — yearly releases (async/await, optional chaining, etc.)

### JavaScript Engine

| Engine | Browser/Runtime |
|--------|----------------|
| V8 | Chrome, Node.js, Deno |
| SpiderMonkey | Firefox |
| JavaScriptCore (Nitro) | Safari |
| Chakra | Edge (legacy) |

### V8 Engine

```
Source Code
     ↓
Parser → AST (Abstract Syntax Tree)
     ↓
Ignition (Interpreter) → Bytecode
     ↓ (hot code detected)
TurboFan (Optimizing Compiler) → Machine Code
     ↓ (deoptimization if assumptions break)
Back to Ignition
```

**Key V8 concepts:**
- **Hidden Classes** — Optimizes property access (like Java class structure)
- **Inline Caching** — Speeds up repeated property lookups
- **Garbage Collection** — Generational (Young Generation + Old Generation)

### Interpreted vs JIT Compiled

| Aspect | Pure Interpreted | JIT Compiled |
|--------|-----------------|--------------|
| Execution | Line by line | Compiles hot paths to machine code |
| Speed | Slower | Near-native performance |
| Startup | Fast | Slightly slower (compilation overhead) |
| Example | Early JS engines | V8, SpiderMonkey |

**JavaScript is JIT-compiled** — starts interpreting immediately, then optimizes frequently-executed code paths into native machine code.

### Strict Mode

```javascript
"use strict";

// What changes in strict mode:
// 1. No undeclared variables
x = 10;                    // ❌ ReferenceError

// 2. No duplicate parameters
function f(a, a) {}        // ❌ SyntaxError

// 3. No octal literals
var n = 010;               // ❌ SyntaxError

// 4. No deleting undeletable properties
delete Object.prototype;   // ❌ TypeError

// 5. 'this' is undefined in functions (not window)
function f() { console.log(this); }  // undefined (not window)

// 6. No 'with' statement
// 7. eval doesn't create variables in surrounding scope
// 8. arguments object doesn't alias parameters
```

**When to use:** Always. ES modules are strict mode by default.

### Script vs Module

| Feature | Script | Module |
|---------|--------|--------|
| Scope | Global | Own scope |
| Strict mode | Optional | Always strict |
| Top-level `this` | `window` | `undefined` |
| Import/Export | ❌ | ✅ |
| Loading | Synchronous by default | Deferred by default |
| Duplicate execution | Multiple times | Once (cached) |

```html
<!-- Script -->
<script src="app.js"></script>

<!-- Module -->
<script type="module" src="app.js"></script>
```

### Dynamic Typing

```javascript
let x = 42;        // x is a number
x = "hello";       // x is now a string — NO ERROR
x = true;          // x is now a boolean
x = { a: 1 };     // x is now an object

// Type is determined at runtime, not compile time
// Variables don't have types — VALUES have types
```

---

## Internal Working

### How JavaScript Executes Code

```
1. JavaScript file loaded
2. Parser tokenizes source code
3. AST (Abstract Syntax Tree) generated
4. Interpreter (Ignition) generates bytecode
5. Code starts executing
6. Profiler identifies hot code paths
7. Optimizing compiler (TurboFan) compiles hot code to machine code
8. Deoptimization if assumptions are violated
```

### Memory Model

```
┌─────────────────────────────────────┐
│              STACK                   │
│  (Primitives, References,           │
│   Execution Contexts)               │
├─────────────────────────────────────┤
│              HEAP                    │
│  (Objects, Arrays, Functions,       │
│   Closures)                         │
├─────────────────────────────────────┤
│         CODE SEGMENT                │
│  (Compiled machine code)            │
└─────────────────────────────────────┘
```

### Execution Context Lifecycle (Brief)

```
Global Execution Context created
  ↓
Creation Phase:
  - Create global object (window/global)
  - Create 'this' binding
  - Set up memory for variables (hoisting)
  ↓
Execution Phase:
  - Execute code line by line
  - Function calls create new Execution Contexts
```

---

## Diagram

```
JavaScript Runtime Architecture:
┌──────────────────────────────────────────────────────┐
│                    JavaScript Engine (V8)             │
│  ┌─────────────────┐  ┌──────────────────────────┐  │
│  │   Call Stack     │  │     Memory Heap          │  │
│  │                  │  │                          │  │
│  │  ┌───────────┐  │  │  ┌────┐ ┌────┐ ┌────┐   │  │
│  │  │  func()   │  │  │  │Obj1│ │Obj2│ │Obj3│   │  │
│  │  ├───────────┤  │  │  └────┘ └────┘ └────┘   │  │
│  │  │  main()   │  │  │                          │  │
│  │  └───────────┘  │  │                          │  │
│  └─────────────────┘  └──────────────────────────┘  │
└──────────────────────────────────────────────────────┘
              │
              ↓ (async operations)
┌──────────────────────────────────────────────────────┐
│              Web APIs / Node APIs                     │
│  (setTimeout, fetch, DOM events, I/O)                │
└──────────────────────────────────────────────────────┘
              │
              ↓ (callbacks ready)
┌──────────────────────────────────────────────────────┐
│  ┌─────────────────────┐  ┌───────────────────────┐ │
│  │  Microtask Queue    │  │  Macrotask Queue      │ │
│  │  (Promises, queueM) │  │  (setTimeout, I/O)    │ │
│  └─────────────────────┘  └───────────────────────┘ │
│                    EVENT LOOP                         │
└──────────────────────────────────────────────────────┘
```

```
V8 Compilation Pipeline:
┌──────────┐     ┌────────┐     ┌──────────┐     ┌──────────┐
│  Source  │ ──→ │ Parser │ ──→ │   AST    │ ──→ │ Ignition │
│  Code   │     │        │     │          │     │(Bytecode)│
└──────────┘     └────────┘     └──────────┘     └──────────┘
                                                       │
                                          (hot code)   ↓
                                                 ┌──────────┐
                                                 │ TurboFan │
                                                 │(Opt. Code)│
                                                 └──────────┘
```

---

## Code

```javascript
// === JavaScript Fundamentals Demo ===

// 1. Dynamic Typing
let value = 42;
console.log(typeof value);  // "number"
value = "hello";
console.log(typeof value);  // "string"

// 2. Strict Mode
"use strict";
// x = 10; // ReferenceError: x is not defined

// 3. Type checking
console.log(typeof 42);           // "number"
console.log(typeof "hello");      // "string"
console.log(typeof true);         // "boolean"
console.log(typeof undefined);    // "undefined"
console.log(typeof null);         // "object" ← KNOWN BUG
console.log(typeof {});           // "object"
console.log(typeof []);           // "object" ← arrays are objects
console.log(typeof function(){}); // "function"
console.log(typeof Symbol());     // "symbol"
console.log(typeof 10n);          // "bigint"

// 4. Script vs Module behavior
// In a module:
// - Top-level variables are NOT on globalThis
// - 'this' at top level is undefined
// - import/export available

// 5. Engine optimization example
function add(a, b) {
  return a + b;
}
// V8 will optimize this for numbers
// If you call add("1", "2"), it deoptimizes (monomorphic → polymorphic)
for (let i = 0; i < 100000; i++) {
  add(i, i + 1);  // V8 optimizes for number+number
}
```

---

## Interview Questions

### Q1: What is the difference between JavaScript and ECMAScript?
**A:** ECMAScript is the specification/standard maintained by TC39. JavaScript is an implementation of that specification. Other implementations include JScript (Microsoft) and ActionScript (Adobe). When we say "ES6 features," we mean features defined in the ECMAScript 2015 specification.

### Q2: Is JavaScript interpreted or compiled?
**A:** Modern JavaScript engines use **JIT (Just-In-Time) compilation** — a hybrid approach. The code is initially interpreted (for fast startup), then frequently-executed "hot" code paths are compiled to optimized machine code by the compiler (TurboFan in V8). If assumptions break (e.g., types change), it deoptimizes back to interpreted bytecode.

### Q3: Why does `typeof null` return "object"?
**A:** This is a historical bug from JavaScript's first implementation. In the original implementation, values were represented as a type tag + value. Objects had a type tag of 0, and `null` was represented as the NULL pointer (0x00), so its type tag was also 0, making it appear as an object. It was never fixed because too much existing code depends on this behavior.

### Q4: What is strict mode and why use it?
**A:** Strict mode (`"use strict"`) makes JavaScript fail loudly on bad practices:
- Prevents accidental globals (undeclared variables throw errors)
- Disallows duplicate parameters
- Makes `this` undefined in plain functions (not `window`)
- Forbids `with` statement
- Makes `eval` safer

**Use it always.** ES modules are automatically in strict mode.

### Q5: What does "JavaScript is single-threaded" mean?
**A:** JavaScript has ONE call stack — it can execute only one piece of code at a time. However, it achieves concurrency through the **event loop** and **asynchronous APIs** (Web APIs in browsers, C++ APIs in Node.js). Long-running operations (network requests, timers) are offloaded to the environment, and their callbacks are queued for later execution.

### Q6: How does V8 optimize JavaScript code?
**A:** V8 uses a multi-tier compilation strategy:
1. **Ignition** (interpreter) — Quick startup, generates bytecode
2. **Profiler** — Tracks execution frequency and type information
3. **TurboFan** (optimizing compiler) — Compiles hot functions to machine code using type specialization, inlining, and dead code elimination
4. **Deoptimization** — Falls back to bytecode if assumptions break (e.g., unexpected types)

---

## Common Mistakes

1. **Confusing JavaScript with Java** — They are completely different languages
2. **Thinking JS is purely interpreted** — Modern engines JIT-compile
3. **Not using strict mode** — Allows silent failures and bad patterns
4. **Relying on `typeof null === "object"`** — Use `value === null` for null checks
5. **Assuming script order doesn't matter** — Scripts without `defer`/`async` block parsing
6. **Not understanding dynamic typing implications** — Types change at runtime; use `===` over `==`

---

## Best Practices

1. Always use strict mode (or ES modules which are strict by default)
2. Use `const` by default, `let` when reassignment is needed, never `var`
3. Write monomorphic code for engine optimization (consistent types)
4. Use `===` instead of `==` to avoid type coercion surprises
5. Load scripts with `defer` or `type="module"` for better performance
6. Keep functions small and focused for JIT optimization

---

## Production Considerations

- **Minification** — Reduces file size (Terser, esbuild)
- **Bundling** — Combines modules (Webpack, Rollup, Vite)
- **Transpilation** — Convert modern JS to older syntax for compatibility (Babel)
- **Source Maps** — Map minified code to original for debugging
- **Polyfills** — Add missing features for older browsers (core-js)
- **Tree Shaking** — Remove unused exports from bundles

---

## Related Topics

- [02-variables-and-declarations](./02-variables-and-declarations.md)
- [03-data-types](./03-data-types.md)
- [14-execution-context](./14-execution-context.md)
- [18-event-loop](./18-event-loop.md)
- [22-es6-plus](./22-es6-plus.md)
