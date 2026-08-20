# JavaScript Study Guide — Complete Index

## File → Outline Section Mapping

| File | Covers Outline Sections | Priority |
|------|------------------------|----------|
| `00-cheat-sheet.md` | Quick revision + drills | ⭐⭐⭐ Review last |
| `01-javascript-fundamentals.md` | §1 JS Fundamentals | — |
| `02-variables-and-scope.md` | §2 Variables, §11 Scope/Closures, §12 Hoisting | ⭐⭐ |
| `03-data-types-and-coercion.md` | §3 Data Types, §4 Operators, §5 Type Coercion | ⭐ |
| `04-functions.md` | §10 Functions, §11 Closures (deep) | ⭐⭐⭐ |
| `05-objects-and-prototypes.md` | §9 Objects, §15 Prototypes, §16 Classes | ⭐⭐ |
| `06-arrays-and-methods.md` | §8 Arrays | ⭐ |
| `07-async-javascript.md` | §17 Async JS, §18 Event Loop | ⭐⭐⭐ |
| `08-es6-plus-features.md` | §22 ES6+, §24 Map/Set, §25 Iterators/Generators | ⭐ |
| `09-error-handling.md` | §26 Error Handling | — |
| `10-dom-and-events.md` | §19 DOM | ⭐⭐ |
| `11-advanced-concepts.md` | §27 Memory, Debounce/Throttle, Currying, GC | ⭐⭐ |
| `12-design-patterns-and-patterns.md` | Design patterns, utility implementations | — |
| `13-this-keyword.md` | §13 this Keyword | ⭐⭐⭐ |
| `14-execution-context.md` | §14 Execution Context, Call Stack | ⭐⭐⭐ |
| `15-strings-and-numbers.md` | §6 Strings, §7 Numbers & Math | — |
| `20-browser-apis.md` | §20 Browser APIs | — |
| `21-http-and-api.md` | §21 HTTP & API, §31 JSON | ⭐ |
| `23-modules.md` | §23 Modules (ESM vs CJS) | ⭐ |
| `28-functional-programming.md` | §28 Functional Programming | ⭐ |
| `29-oop-javascript.md` | §29 OOP, §16 Classes (deep) | ⭐ |
| `30-regex.md` | §30 Regular Expressions | — |
| `32-security.md` | §32 Security (XSS, CSRF, CORS) | ⭐ |
| `33-performance.md` | §33 Performance | ⭐ |
| `34-tooling.md` | §34 Tooling (npm, Vite, Webpack) | — |
| `35-testing.md` | §35 Testing (Jest) | — |
| `36-advanced-interview-problems.md` | §36 Polyfills, Output Questions | ⭐⭐⭐ |

---

## Recommended Study Order

### Week 1: Core Language (Must-Know)
1. `01-javascript-fundamentals.md`
2. `02-variables-and-scope.md` (var/let/const, hoisting, TDZ, scope chain)
3. `03-data-types-and-coercion.md` (types, coercion, == vs ===)
4. `04-functions.md` (closures, this, call/apply/bind, HOFs)
5. `13-this-keyword.md` (dedicated deep-dive)
6. `14-execution-context.md` (call stack, creation/execution phases)

### Week 2: Data Structures + Async
7. `05-objects-and-prototypes.md` (prototype chain, classes)
8. `06-arrays-and-methods.md` (map, filter, reduce, find)
9. `07-async-javascript.md` (Promises, async/await, event loop)
10. `08-es6-plus-features.md` (destructuring, spread, Map/Set, generators)

### Week 3: Applied Knowledge
11. `10-dom-and-events.md` (event delegation, bubbling)
12. `11-advanced-concepts.md` (debounce, throttle, memoization, memory)
13. `21-http-and-api.md` (fetch, axios, status codes)
14. `23-modules.md` (ESM vs CJS, dynamic imports)
15. `32-security.md` (XSS, CSRF, CORS)
16. `33-performance.md` (lazy loading, web workers)

### Week 4: Interview Prep
17. `36-advanced-interview-problems.md` (polyfills, output problems)
18. `00-cheat-sheet.md` (daily revision from here)
19. Practice writing: debounce, throttle, Promise.all, deep clone, curry from memory

---

## Topic Cross-References

| If studying... | Also read... |
|---------------|-------------|
| Closures (04) | Execution Context (14), this (13), Scope (02) |
| this (13) | Functions (04), Classes (05), Arrow Functions (08) |
| Event Loop (07) | Execution Context (14), Browser APIs (20) |
| Prototypes (05) | OOP (29), Classes (05), this (13) |
| Async (07) | Event Loop (07), Error Handling (09), HTTP (21) |
| Performance (33) | Advanced Concepts (11), Browser APIs (20) |
| Security (32) | HTTP (21), Browser APIs (20) |

---

## Interview Focus Areas (By Experience Level)

### Junior (0-2 YOE)
- Variables, scope, hoisting
- Data types, coercion, falsy values
- Array methods (map, filter, reduce)
- Basic Promises and async/await
- DOM manipulation basics
- ES6 syntax

### Mid (2-5 YOE)
- Everything above PLUS:
- Closures (output questions)
- this binding (all rules)
- Event loop (execution order)
- Prototype chain
- Error handling patterns
- Debounce/throttle implementation
- Security basics (XSS, CORS)

### Senior (5+ YOE)
- Everything above PLUS:
- Memory management and leaks
- Performance optimization patterns
- Design patterns in JS
- Module systems (ESM vs CJS internals)
- Proxy/Reflect
- Generator use cases
- Architecture decisions
