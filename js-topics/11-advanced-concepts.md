# Advanced JavaScript Concepts

## Execution Context

### Types
1. **Global Execution Context** - Created when script starts
2. **Function Execution Context** - Created when function is called
3. **Eval Execution Context** - Created by eval()

### Phases
```
1. Creation Phase:
   - Create Variable Object (hoisting)
   - Create Scope Chain
   - Determine 'this' value

2. Execution Phase:
   - Execute code line by line
   - Assign values to variables
```

### Call Stack
```javascript
function first() { second(); }
function second() { third(); }
function third() { console.log('hello'); }
first();

// Call Stack:
// | third()  |
// | second() |
// | first()  |
// | global   |
```

---

## Debounce and Throttle

### Debounce (Wait until user stops)
```javascript
function debounce(fn, delay) {
  let timeoutId;
  return (...args) => {
    clearTimeout(timeoutId);
    timeoutId = setTimeout(() => fn(...args), delay);
  };
}

// Usage: search input
const search = debounce((query) => fetchResults(query), 300);
input.addEventListener('input', (e) => search(e.target.value));
```

### Throttle (Execute at most once per interval)
```javascript
function throttle(fn, interval) {
  let lastTime = 0;
  return (...args) => {
    const now = Date.now();
    if (now - lastTime >= interval) {
      lastTime = now;
      fn(...args);
    }
  };
}

// Usage: scroll handler
window.addEventListener('scroll', throttle(handleScroll, 100));
```

### Comparison
| Debounce | Throttle |
|----------|----------|
| Fires AFTER delay of inactivity | Fires AT MOST once per interval |
| Resets timer on each call | Ignores calls during interval |
| Use: Search input, resize | Use: Scroll, mousemove, API calls |

---

## Currying

```javascript
// Transform f(a, b, c) into f(a)(b)(c)
function curry(fn) {
  return function curried(...args) {
    if (args.length >= fn.length) return fn(...args);
    return (...moreArgs) => curried(...args, ...moreArgs);
  };
}

const add = curry((a, b, c) => a + b + c);
add(1)(2)(3);    // 6
add(1, 2)(3);    // 6
add(1)(2, 3);    // 6

// Practical: Reusable partially applied functions
const multiply = curry((a, b) => a * b);
const double = multiply(2);
const triple = multiply(3);
double(5);  // 10
triple(5);  // 15
```

---

## Memoization

```javascript
function memoize(fn) {
  const cache = new Map();
  return (...args) => {
    const key = JSON.stringify(args);
    if (cache.has(key)) return cache.get(key);
    const result = fn(...args);
    cache.set(key, result);
    return result;
  };
}

const factorial = memoize((n) => n <= 1 ? 1 : n * factorial(n - 1));
factorial(100);  // Computed
factorial(100);  // Cached!
```

---

## Deep Copy vs Shallow Copy

```javascript
// Shallow copy (nested objects still shared)
const shallow = { ...original };
const shallow2 = Object.assign({}, original);
const shallowArr = [...arr];

// Deep copy
const deep = structuredClone(original);   // Modern (recommended)
const deep2 = JSON.parse(JSON.stringify(original));  // Loses: functions, undefined, Date, RegExp

// When matters:
const user = { name: 'John', address: { city: 'NYC' } };
const copy = { ...user };
copy.address.city = 'LA';
console.log(user.address.city);  // 'LA' — shared reference!
```

---

## Memory Management & Garbage Collection

### How GC Works
- **Mark-and-Sweep**: Mark all reachable objects from roots, sweep unmarked
- **Generational**: Young generation (frequent GC) + Old generation (infrequent)
- Reachability: If not accessible from root (global, stack), it's garbage collected

### Memory Leaks
```javascript
// 1. Accidental globals
function leak() { x = 'global'; }  // Without var/let/const

// 2. Forgotten timers
const interval = setInterval(() => { /* uses data */ }, 1000);
// Fix: clearInterval when no longer needed

// 3. Detached DOM references
const element = document.getElementById('button');
document.body.removeChild(element);
// element still references the DOM node → not GC'd

// 4. Closures holding large data
function createClosure() {
  const hugeData = new Array(1000000).fill('x');
  return () => hugeData.length;  // hugeData stays in memory
}
```

---

## Regular Expressions

```javascript
// Creating
const regex = /pattern/flags;
const regex = new RegExp('pattern', 'flags');

// Flags: g (global), i (case-insensitive), m (multiline), s (dotAll)

// Methods
regex.test('string');        // true/false
'string'.match(regex);       // Matches array
'string'.replace(regex, 'replacement');
'string'.split(regex);

// Common patterns
/^\d+$/           // Only digits
/^[\w.-]+@[\w.-]+\.\w+$/  // Basic email
/^https?:\/\//    // URL starts with http(s)
```

---

## Key Interview Questions

**Q: What is the difference between debounce and throttle?**
> Debounce waits for a pause in calls (fires once after user stops). Throttle fires at a regular interval (at most once per X ms). Debounce for search inputs; throttle for scroll/resize handlers.

**Q: What is currying?**
> Transforming a function with multiple arguments into a sequence of functions that each take one argument: `f(a, b)` becomes `f(a)(b)`. Enables partial application and function composition.

**Q: How does garbage collection work in JavaScript?**
> Mark-and-sweep: The GC starts from root references (global object, call stack), marks all reachable objects, then frees memory of unreachable objects. V8 uses generational GC for efficiency.

**Q: What causes memory leaks in JavaScript?**
> Accidental globals, uncleared timers/intervals, references to removed DOM elements, closures holding large unused data, and event listeners not removed on cleanup.

**Q: What is the call stack and what happens on overflow?**
> LIFO structure tracking function execution. Each call pushes a frame; each return pops it. Stack overflow (RangeError) occurs with deep/infinite recursion when the stack exceeds its limit.
