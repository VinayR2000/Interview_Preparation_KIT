# JavaScript Interview Cheat Sheet (Last-Minute Revision)

## Quick Reference — Know These Cold

---

## 1. var vs let vs const
```
var  → function-scoped, hoisted as undefined, redeclarable
let  → block-scoped, TDZ, no redeclare
const → block-scoped, TDZ, no reassign (objects mutable)
```

## 2. Falsy Values (Everything else is truthy)
```
false, 0, -0, 0n, "", null, undefined, NaN
⚠️ [], {}, "0", "false" are ALL truthy!
```

## 3. == vs ===
```
== coerces types:  "5" == 5 → true, null == undefined → true
=== no coercion:   "5" === 5 → false
ALWAYS use ===
```

## 4. typeof Results
```javascript
typeof 42           // "number"
typeof "hi"         // "string"
typeof true         // "boolean"
typeof undefined    // "undefined"
typeof null         // "object" ← BUG
typeof {}           // "object"
typeof []           // "object" ← use Array.isArray()
typeof function(){} // "function"
typeof Symbol()     // "symbol"
typeof 10n          // "bigint"
```

## 5. this Rules (Priority Order)
```
1. new           → new object
2. call/apply/bind → specified object
3. obj.method()  → obj
4. plain call    → window (non-strict) / undefined (strict)
5. arrow fn      → inherits from enclosing scope (PERMANENT)
```

## 6. Event Loop Order
```
Sync code → ALL Microtasks → ONE Macrotask → repeat

Microtasks: Promise .then/.catch/.finally, queueMicrotask
Macrotasks: setTimeout, setInterval, I/O, UI rendering
```

## 7. Closure
```
Function + its lexical environment (outer variables)
= Function remembers where it was created, even after outer fn returns
```

## 8. Hoisting
```
var         → hoisted, initialized as undefined
let/const   → hoisted, but TDZ (ReferenceError if accessed before declaration)
function    → fully hoisted (body + name)
arrow/expr  → only variable name hoisted (as undefined with var)
```

## 9. Prototype Chain
```
instance.__proto__ === Constructor.prototype
Constructor.prototype.__proto__ === Object.prototype
Object.prototype.__proto__ === null
```

## 10. Promise States
```
Pending → Fulfilled (resolve) OR Rejected (reject)
.then() handles fulfilled
.catch() handles rejected
.finally() runs always
```

## 11. Array Methods
```
IMMUTABLE (new array): map, filter, slice, concat, flat, flatMap
MUTATING (original):   push, pop, shift, unshift, splice, sort, reverse
SEARCH:                find, findIndex, includes, indexOf, some, every
ACCUMULATE:            reduce
```

## 12. Object Operations
```javascript
Object.keys(obj)      // Own enumerable keys
Object.values(obj)    // Own enumerable values
Object.entries(obj)   // [[key, value], ...]
Object.assign({}, a)  // Shallow copy
Object.freeze(obj)    // Immutable (shallow)
{ ...obj }            // Shallow copy (spread)
structuredClone(obj)  // Deep copy
```

## 13. Async Patterns
```javascript
// Sequential
const a = await fetchA();
const b = await fetchB();  // Waits for A

// Parallel
const [a, b] = await Promise.all([fetchA(), fetchB()]);  // Simultaneous
```

## 14. Spread vs Rest
```javascript
// Spread: expand
const arr2 = [...arr1, 4, 5];
const obj2 = { ...obj1, key: 'new' };

// Rest: collect
function fn(...args) {}      // args = array
const { a, ...rest } = obj;  // rest = everything except a
```

## 15. Nullish Coalescing vs OR
```javascript
0 || 'default'    // 'default' (0 is falsy)
0 ?? 'default'    // 0 (only null/undefined trigger ??)
'' || 'default'   // 'default'
'' ?? 'default'   // ''
```

---

## Output Prediction Quick Drills

### Drill 1: Hoisting
```javascript
console.log(a);     // undefined (var hoisted)
console.log(b);     // ReferenceError (TDZ)
var a = 1;
let b = 2;
```

### Drill 2: Closure
```javascript
function create() {
  let x = 0;
  return () => ++x;
}
const fn = create();
fn(); fn(); fn();   // 1, 2, 3
```

### Drill 3: Event Loop
```javascript
console.log('A');
setTimeout(() => console.log('B'), 0);
Promise.resolve().then(() => console.log('C'));
console.log('D');
// Output: A, D, C, B
```

### Drill 4: this
```javascript
const obj = { x: 10, getX: () => this.x };
obj.getX();  // undefined (arrow inherits outer this, not obj)
```

### Drill 5: Closure in Loop
```javascript
for (var i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 0);
}
// Output: 3, 3, 3 (shared var i)
```

---

## Key Implementations (Write From Memory)

### Debounce
```javascript
function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}
```

### Throttle
```javascript
function throttle(fn, limit) {
  let last = 0;
  return (...args) => {
    const now = Date.now();
    if (now - last >= limit) { last = now; fn(...args); }
  };
}
```

### Promise.all
```javascript
function promiseAll(promises) {
  return new Promise((resolve, reject) => {
    const results = []; let count = 0;
    promises.forEach((p, i) => {
      Promise.resolve(p).then(val => {
        results[i] = val;
        if (++count === promises.length) resolve(results);
      }).catch(reject);
    });
  });
}
```

### Deep Clone
```javascript
function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(deepClone);
  return Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, deepClone(v)]));
}
```

---

## Study Flow (Read In This Order)

```
Fundamentals (01) → Variables (02) → Types (03) → Functions (04)
→ Objects (05) → Arrays (06) → this (13) → Execution Context (14)
→ Scope & Closures (02+04) → Hoisting (02) → Prototypes (05)
→ Async (07) → ES6+ (08) → DOM (10) → Advanced (11)
→ Interview Problems (36) ← DO THIS LAST
```
