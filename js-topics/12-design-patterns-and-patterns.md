# JavaScript Design Patterns and Common Patterns

## Module Pattern

```javascript
// IIFE Module (pre-ES6)
const Calculator = (() => {
  let result = 0;  // Private
  return {
    add: (n) => { result += n; return Calculator; },
    subtract: (n) => { result -= n; return Calculator; },
    getResult: () => result,
  };
})();

Calculator.add(5).add(3).subtract(1).getResult();  // 7
```

## Singleton

```javascript
class Database {
  static #instance = null;
  
  static getInstance() {
    if (!Database.#instance) {
      Database.#instance = new Database();
    }
    return Database.#instance;
  }

  constructor() {
    if (Database.#instance) throw new Error('Use getInstance()');
    this.connection = null;
  }
}
```

## Observer Pattern

```javascript
class EventEmitter {
  #listeners = {};

  on(event, callback) {
    this.#listeners[event] = this.#listeners[event] || [];
    this.#listeners[event].push(callback);
  }

  emit(event, ...args) {
    (this.#listeners[event] || []).forEach(cb => cb(...args));
  }

  off(event, callback) {
    this.#listeners[event] = (this.#listeners[event] || [])
      .filter(cb => cb !== callback);
  }
}

const emitter = new EventEmitter();
emitter.on('data', (data) => console.log('Received:', data));
emitter.emit('data', { id: 1 });
```

## Factory Pattern

```javascript
function createUser(type) {
  switch (type) {
    case 'admin': return { role: 'admin', permissions: ['read', 'write', 'delete'] };
    case 'user': return { role: 'user', permissions: ['read'] };
    default: throw new Error(`Unknown type: ${type}`);
  }
}
```

---

## Functional Patterns

### Composition
```javascript
const pipe = (...fns) => (x) => fns.reduce((acc, fn) => fn(acc), x);
const compose = (...fns) => (x) => fns.reduceRight((acc, fn) => fn(acc), x);

const processUser = pipe(
  normalize,
  validate,
  save
);
processUser(rawData);
```

### Immutability Helpers
```javascript
// Never mutate, always return new
const addItem = (arr, item) => [...arr, item];
const removeItem = (arr, id) => arr.filter(i => i.id !== id);
const updateItem = (arr, id, updates) => 
  arr.map(i => i.id === id ? { ...i, ...updates } : i);

const updateNested = (obj, path, value) => ({
  ...obj,
  [path]: value,
});
```

---

## Common Utility Implementations

### Deep Equal
```javascript
function deepEqual(a, b) {
  if (a === b) return true;
  if (typeof a !== 'object' || typeof b !== 'object') return false;
  if (a === null || b === null) return false;
  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  if (keysA.length !== keysB.length) return false;
  return keysA.every(key => deepEqual(a[key], b[key]));
}
```

### Flatten Object
```javascript
function flattenObject(obj, prefix = '') {
  return Object.keys(obj).reduce((acc, key) => {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof obj[key] === 'object' && obj[key] !== null && !Array.isArray(obj[key])) {
      Object.assign(acc, flattenObject(obj[key], path));
    } else {
      acc[path] = obj[key];
    }
    return acc;
  }, {});
}
// { a: { b: 1, c: { d: 2 } } } → { 'a.b': 1, 'a.c.d': 2 }
```

---

## Key Interview Questions

**Q: What is the module pattern?**
> Uses closures to create private state with a public API. Before ES modules, this was the primary way to encapsulate code and avoid global pollution.

**Q: What is the observer pattern and where is it used?**
> An object (subject) maintains a list of dependents (observers) and notifies them of state changes. Used in: EventEmitter (Node.js), DOM events, Redux subscriptions, RxJS.

**Q: What is function composition?**
> Combining multiple functions where output of one becomes input of the next. `compose(f, g, h)(x)` = `f(g(h(x)))`. Creates reusable pipelines from small, focused functions.

**Q: What is immutability and why does it matter?**
> Never modifying data, always creating new copies. Benefits: predictable state, easier debugging, enables time-travel debugging, required by React for change detection, prevents shared mutation bugs.
