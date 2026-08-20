# ES6+ Modern JavaScript

## Destructuring

```javascript
// Object destructuring
const { name, age, city = 'Unknown' } = user;
const { name: userName, address: { street } } = user;  // Rename + nested

// Array destructuring
const [first, second, ...rest] = [1, 2, 3, 4, 5];
const [a, , c] = [1, 2, 3];  // Skip second element

// Function parameters
function createUser({ name, age = 25, role = 'user' }) { }

// Swap variables
[a, b] = [b, a];
```

---

## Spread and Rest

```javascript
// Spread (...) - Expand iterable into individual elements
const merged = [...arr1, ...arr2];
const clone = { ...original, updated: true };
Math.max(...numbers);

// Rest (...) - Collect remaining into array/object
function sum(...nums) { return nums.reduce((a, b) => a + b, 0); }
const { id, ...rest } = user;  // rest = everything except id
const [head, ...tail] = [1, 2, 3, 4];  // head=1, tail=[2,3,4]
```

---

## Template Literals

```javascript
const name = 'John';
const greeting = `Hello, ${name}!`;
const multiline = `
  Line 1
  Line 2
`;
const expression = `Total: ${price * quantity}`;

// Tagged templates
function highlight(strings, ...values) {
  return strings.reduce((result, str, i) => 
    `${result}${str}<b>${values[i] || ''}</b>`, '');
}
highlight`Hello ${name}, you are ${age} years old`;
```

---

## Optional Chaining & Nullish Coalescing

```javascript
// Optional chaining (?.) - Short-circuits to undefined
user?.address?.city;         // undefined if any part is null/undefined
users?.[0]?.name;            // Array element
obj.method?.();              // Method call

// Nullish coalescing (??) - Default for null/undefined ONLY
const value = input ?? 'default';  // Only null/undefined trigger default
0 ?? 'default';     // 0 (0 is NOT null/undefined)
'' ?? 'default';    // '' (empty string is NOT null/undefined)

// vs logical OR (||) - Default for ANY falsy value
0 || 'default';     // 'default' (0 is falsy)
'' || 'default';    // 'default' ('' is falsy)
```

---

## Modules (import/export)

```javascript
// Named exports
export const PI = 3.14;
export function add(a, b) { return a + b; }

// Default export (one per module)
export default class User { }

// Named imports
import { add, PI } from './math.js';
import { add as sum } from './math.js';  // Rename

// Default import
import User from './User.js';

// All imports
import * as MathUtils from './math.js';
MathUtils.add(1, 2);

// Dynamic import (code splitting)
const module = await import('./heavy-module.js');
```

---

## Symbol, Map, Set, WeakMap, WeakSet

### Symbol
```javascript
const id = Symbol('id');
const obj = { [id]: 123, name: 'John' };
obj[id];        // 123
Object.keys(obj);  // ['name'] — Symbols are NOT enumerable

// Well-known symbols
Symbol.iterator  // Custom iteration
Symbol.toPrimitive  // Custom type conversion
```

### Map (any key type, ordered)
```javascript
const map = new Map();
map.set('key', 'value');
map.set(42, 'number key');
map.set({}, 'object key');
map.get('key');   // 'value'
map.has('key');   // true
map.size;         // 3
map.delete('key');
for (const [key, value] of map) { }
```

### Set (unique values)
```javascript
const set = new Set([1, 2, 2, 3, 3]);  // {1, 2, 3}
set.add(4);
set.has(2);    // true
set.size;      // 4
set.delete(2);

// Remove duplicates from array
const unique = [...new Set(array)];
```

### WeakMap / WeakSet
- Keys must be objects (WeakMap) or values must be objects (WeakSet)
- Keys are weakly held — can be garbage collected
- Not iterable, no `.size`
- Use case: Private data, caching without memory leaks

---

## Generators and Iterators

```javascript
// Generator function (yields values lazily)
function* fibonacci() {
  let [a, b] = [0, 1];
  while (true) {
    yield a;
    [a, b] = [b, a + b];
  }
}

const fib = fibonacci();
fib.next();  // { value: 0, done: false }
fib.next();  // { value: 1, done: false }
fib.next();  // { value: 1, done: false }
fib.next();  // { value: 2, done: false }

// Custom iterator (Symbol.iterator)
const range = {
  from: 1,
  to: 5,
  [Symbol.iterator]() {
    let current = this.from;
    return {
      next: () => current <= this.to
        ? { value: current++, done: false }
        : { done: true }
    };
  }
};

for (const n of range) console.log(n);  // 1, 2, 3, 4, 5
```

---

## Proxy and Reflect

```javascript
const handler = {
  get(target, prop) {
    return prop in target ? target[prop] : `Property ${prop} not found`;
  },
  set(target, prop, value) {
    if (prop === 'age' && typeof value !== 'number') {
      throw new TypeError('Age must be a number');
    }
    target[prop] = value;
    return true;
  }
};

const user = new Proxy({}, handler);
user.name = 'John';
user.age = 25;
console.log(user.missing);  // "Property missing not found"
user.age = 'old';           // TypeError: Age must be a number
```

---

## Key Interview Questions

**Q: What's the difference between Map and Object?**
> Map: Any type as key, maintains insertion order, has `.size`, better for frequent additions/deletions. Object: String/Symbol keys only, has prototype chain, good for structured data.

**Q: What's the difference between `??` and `||`?**
> `||` returns the right side for any falsy value (0, '', false, null, undefined). `??` returns the right side ONLY for null or undefined. Use `??` when 0 or '' are valid values.

**Q: What is a generator and when would you use it?**
> A function that can pause and resume execution via `yield`. Useful for: lazy evaluation (infinite sequences), custom iterators, cooperative multitasking, handling streams of data.

**Q: What is a Proxy?**
> An object that wraps another object and intercepts operations (get, set, delete, etc.) via handler traps. Use cases: validation, logging, reactive systems, virtual properties.
