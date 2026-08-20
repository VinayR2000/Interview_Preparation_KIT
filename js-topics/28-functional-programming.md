# Functional Programming in JavaScript

## Core Principles

| Principle | Description |
|-----------|-------------|
| Pure Functions | Same input → same output, no side effects |
| Immutability | Never mutate data, create new copies |
| First-Class Functions | Functions as values (pass, return, assign) |
| Higher-Order Functions | Functions that take/return other functions |
| Function Composition | Combine simple functions into complex ones |

---

## Pure Functions

```javascript
// ✅ Pure: No side effects, same input → same output
function add(a, b) { return a + b; }
function fullName(first, last) { return `${first} ${last}`; }

// ❌ Impure: Side effects
let count = 0;
function increment() { return ++count; }  // Modifies external state
function getTime() { return Date.now(); }  // Different output each call
function log(msg) { console.log(msg); }    // Side effect (I/O)
```

---

## Immutability

```javascript
// ❌ Mutating
const arr = [1, 2, 3];
arr.push(4);                     // Mutates original
arr.sort();                      // Mutates original

// ✅ Immutable operations
const newArr = [...arr, 4];                    // Add
const filtered = arr.filter(x => x !== 2);     // Remove
const updated = arr.map(x => x === 2 ? 99 : x); // Update
const sorted = [...arr].sort();                // Sort copy

// ❌ Mutating object
user.name = 'Jane';

// ✅ Immutable object update
const updated = { ...user, name: 'Jane' };
const nested = { ...user, address: { ...user.address, city: 'NYC' } };
```

---

## Higher-Order Functions

```javascript
// Takes function as argument
const numbers = [1, 2, 3, 4, 5];
numbers.map(n => n * 2);          // [2, 4, 6, 8, 10]
numbers.filter(n => n > 2);       // [3, 4, 5]
numbers.reduce((sum, n) => sum + n, 0);  // 15

// Returns function
function multiplier(factor) {
  return (number) => number * factor;
}
const double = multiplier(2);
const triple = multiplier(3);
double(5);  // 10
triple(5);  // 15

// Both
function withLogging(fn) {
  return (...args) => {
    console.log(`Calling with:`, args);
    const result = fn(...args);
    console.log(`Result:`, result);
    return result;
  };
}
```

---

## Function Composition

```javascript
// compose: right-to-left
const compose = (...fns) => (x) => fns.reduceRight((acc, fn) => fn(acc), x);

// pipe: left-to-right (more readable)
const pipe = (...fns) => (x) => fns.reduce((acc, fn) => fn(acc), x);

// Example
const processUser = pipe(
  trimWhitespace,
  normalizeEmail,
  validateFields,
  saveToDatabase
);

// Each function is small, testable, reusable
const scream = (str) => str.toUpperCase();
const exclaim = (str) => `${str}!`;
const repeat = (str) => `${str} ${str}`;

const excited = pipe(scream, exclaim, repeat);
excited('hello');  // "HELLO! HELLO!"
```

---

## Currying

```javascript
// Transform f(a, b, c) → f(a)(b)(c)
function curry(fn) {
  return function curried(...args) {
    if (args.length >= fn.length) return fn(...args);
    return (...more) => curried(...args, ...more);
  };
}

// Practical usage: Partial application
const add = curry((a, b) => a + b);
const add5 = add(5);
add5(3);   // 8
add5(10);  // 15

// Configurable functions
const log = curry((level, timestamp, message) =>
  `[${level}] ${timestamp}: ${message}`
);
const errorLog = log('ERROR');
const errorNow = errorLog(new Date().toISOString());
errorNow('Connection failed');
```

---

## map, filter, reduce (Deep Dive)

```javascript
// map: Transform each element
const prices = [10, 20, 30];
const withTax = prices.map(p => p * 1.1);

// filter: Keep elements matching predicate
const adults = people.filter(p => p.age >= 18);

// reduce: Accumulate into any shape
// Sum
[1,2,3].reduce((sum, n) => sum + n, 0);  // 6

// Group by
const grouped = items.reduce((acc, item) => {
  (acc[item.category] ??= []).push(item);
  return acc;
}, {});

// Flatten
[[1,2],[3,4]].reduce((flat, arr) => [...flat, ...arr], []);

// Max
[3,1,4,1,5].reduce((max, n) => n > max ? n : max, -Infinity);

// Compose with reduce
const pipeline = [fn1, fn2, fn3];
const result = pipeline.reduce((val, fn) => fn(val), initialValue);
```

---

## Key Interview Questions

**Q: What is a pure function?**
> A function that: 1) Always returns the same output for same input (deterministic). 2) Has no side effects (doesn't modify external state, no I/O). Benefits: Testable, cacheable (memoization), parallelizable.

**Q: What is the difference between imperative and declarative?**
> Imperative: HOW to do it (step-by-step instructions, loops, mutations). Declarative: WHAT to do (describe the result, let the system figure out how). `map/filter/reduce` are declarative; `for` loops are imperative.

**Q: Why is immutability important?**
> Prevents bugs from shared mutable state. Enables predictable code (no hidden modifications). Required by React for change detection. Enables time-travel debugging. Makes code easier to reason about.
