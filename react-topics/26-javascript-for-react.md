# JavaScript Essentials for React

## Variables: let, const, var

| Feature | var | let | const |
|---------|-----|-----|-------|
| Scope | Function | Block | Block |
| Hoisting | Yes (undefined) | Yes (TDZ) | Yes (TDZ) |
| Reassign | Yes | Yes | No |
| Redeclare | Yes | No | No |

```javascript
const PI = 3.14;      // Cannot reassign (but objects/arrays can be mutated)
let count = 0;        // Can reassign
count = 1;            // ✅

const user = { name: 'John' };
user.name = 'Jane';   // ✅ Object mutation allowed
user = {};            // ❌ Reassignment not allowed
```

---

## Closures

A function that remembers variables from its outer scope even after the outer function returns.

```javascript
function createCounter() {
  let count = 0;  // Enclosed variable
  return {
    increment: () => ++count,
    getCount: () => count,
  };
}
const counter = createCounter();
counter.increment(); // 1 - count persists via closure

// React example: stale closure in useEffect
useEffect(() => {
  const timer = setInterval(() => {
    setCount(count + 1);  // `count` is captured (stale!)
  }, 1000);
  return () => clearInterval(timer);
}, []);  // Fix: setCount(prev => prev + 1)
```

---

## Destructuring

```javascript
// Object destructuring
const { name, age, city = 'Unknown' } = user;
const { name: userName } = user;  // Rename

// Array destructuring
const [first, second, ...rest] = [1, 2, 3, 4, 5];
const [count, setCount] = useState(0);  // React!

// Nested
const { address: { street } } = user;

// Function parameters (React props!)
function UserCard({ name, email, role = 'user' }) { }
```

---

## Spread & Rest Operators

```javascript
// Spread: Expand iterable
const newArr = [...arr1, ...arr2];
const newObj = { ...obj, name: 'updated' };
const props = { id: 1, name: 'test' };
<Component {...props} />  // Spread props in React

// Rest: Collect remaining
function sum(...numbers) { return numbers.reduce((a, b) => a + b); }
const { id, ...rest } = props;  // rest = everything except id
```

---

## Array Methods (Critical for React)

```javascript
// map - Transform each element (used for rendering lists)
const elements = items.map(item => <li key={item.id}>{item.name}</li>);

// filter - Keep items matching condition
const active = users.filter(u => u.isActive);

// reduce - Accumulate to single value
const total = cart.reduce((sum, item) => sum + item.price, 0);

// find - First match
const user = users.find(u => u.id === targetId);

// some - At least one matches?
const hasAdmin = users.some(u => u.role === 'admin');

// every - All match?
const allCompleted = todos.every(t => t.done);

// forEach - Side effects (no return value)
items.forEach(item => console.log(item));
```

---

## Optional Chaining & Nullish Coalescing

```javascript
// Optional chaining (?.) - safely access nested properties
const city = user?.address?.city;        // undefined if any is null/undefined
const first = arr?.[0];                   // Array access
const result = obj?.method?.();           // Method call

// Nullish coalescing (??) - default for null/undefined only
const name = user.name ?? 'Anonymous';    // Only null/undefined trigger default
const count = data.count ?? 0;

// Difference from ||
const value = 0 || 'default';   // 'default' (0 is falsy!)
const value = 0 ?? 'default';   // 0 (only null/undefined trigger ??)
```

---

## ES6 Modules

```javascript
// Named exports
export const API_URL = '/api';
export function fetchUsers() { }
export class UserService { }

// Default export
export default function App() { }

// Named imports
import { fetchUsers, API_URL } from './api';

// Default import
import App from './App';

// Rename
import { fetchUsers as getUsers } from './api';

// Dynamic import (code splitting)
const Module = await import('./heavy-module');
```

---

## Immutability Patterns

```javascript
// Objects
const updated = { ...user, name: 'New Name' };
const nested = { ...user, address: { ...user.address, city: 'NYC' } };

// Arrays
const added = [...items, newItem];
const removed = items.filter(i => i.id !== targetId);
const updated = items.map(i => i.id === id ? { ...i, done: true } : i);
const sorted = [...items].sort((a, b) => a.name.localeCompare(b.name));
```

---

## Key Interview Questions

**Q: What is hoisting?**
> Variables and function declarations are moved to the top of their scope during compilation. `var` is hoisted as `undefined`. `let`/`const` are hoisted but in a Temporal Dead Zone (can't access before declaration). Functions are fully hoisted.

**Q: What's the difference between `==` and `===`?**
> `==` performs type coercion before comparison (`"1" == 1` is true). `===` checks value AND type without coercion (`"1" === 1` is false). Always use `===` in React.

**Q: Why is immutability important in React?**
> React uses reference comparison to detect changes. If you mutate an object, the reference stays the same and React won't re-render. Always create new objects/arrays so React detects the change.

**Q: What is `this` in JavaScript?**
> Depends on how the function is called. In methods: the object. In regular functions: global (or undefined in strict). In arrow functions: inherited from enclosing scope (lexical this). This is why React uses arrow functions in class components.
