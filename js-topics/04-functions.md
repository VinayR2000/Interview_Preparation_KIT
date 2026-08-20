# Functions

## Function Types

### Function Declaration
```javascript
function greet(name) {
  return `Hello, ${name}!`;
}
// Hoisted fully (can call before declaration)
greet('John');  // Works even before the function definition
```

### Function Expression
```javascript
const greet = function(name) {
  return `Hello, ${name}!`;
};
// NOT hoisted (only variable name is hoisted)
```

### Arrow Function
```javascript
const greet = (name) => `Hello, ${name}!`;
const add = (a, b) => a + b;
const getUser = () => ({ name: 'John' });  // Return object (wrap in parens)

// Multi-line
const process = (data) => {
  const result = data.map(d => d * 2);
  return result;
};
```

### Arrow vs Regular Function
| Feature | Regular Function | Arrow Function |
|---------|-----------------|----------------|
| `this` binding | Dynamic (caller) | Lexical (enclosing scope) |
| `arguments` object | ✅ | ❌ (use rest params) |
| Hoisting | Yes (declarations) | No |
| Constructor (`new`) | ✅ | ❌ |
| `prototype` property | ✅ | ❌ |
| Method in object | ✅ (use this) | ❌ (wrong this) |

---

## `this` Keyword

### Rules (in priority order)
1. **`new`**: `this` = newly created object
2. **Explicit binding**: `call/apply/bind` sets `this`
3. **Implicit binding**: Object calling the method (`obj.method()`)
4. **Default**: `window` (non-strict) or `undefined` (strict)
5. **Arrow function**: Inherits `this` from enclosing scope

```javascript
// 1. new binding
function Person(name) { this.name = name; }
const p = new Person('John');  // this = new object

// 2. Explicit: call, apply, bind
function greet() { console.log(this.name); }
greet.call({ name: 'John' });    // 'John'
greet.apply({ name: 'Jane' });   // 'Jane'
const bound = greet.bind({ name: 'Bob' });
bound();                          // 'Bob'

// 3. Implicit: method call
const obj = {
  name: 'Alice',
  greet() { console.log(this.name); }
};
obj.greet();  // 'Alice' (obj is the caller)

const fn = obj.greet;
fn();  // undefined (lost implicit binding!)

// 4. Arrow: lexical this
const obj = {
  name: 'Alice',
  greet: () => console.log(this.name),  // ❌ Arrow inherits outer this (window)
  delayedGreet() {
    setTimeout(() => {
      console.log(this.name);  // ✅ Arrow inherits from delayedGreet's this
    }, 100);
  }
};
```

### call vs apply vs bind
```javascript
function introduce(greeting, punctuation) {
  console.log(`${greeting}, I'm ${this.name}${punctuation}`);
}

const user = { name: 'John' };

introduce.call(user, 'Hi', '!');        // call: args as list
introduce.apply(user, ['Hi', '!']);      // apply: args as Array
const bound = introduce.bind(user, 'Hey');  // bind: returns new function
bound('.');                              // "Hey, I'm John."
```

---

## Closures

### What is a Closure?
- A function that remembers variables from its outer scope even after the outer function has returned
- Created every time a function is created

```javascript
function createCounter() {
  let count = 0;  // Private variable
  return {
    increment: () => ++count,
    decrement: () => --count,
    getCount: () => count,
  };
}

const counter = createCounter();
counter.increment();  // 1
counter.increment();  // 2
counter.getCount();   // 2
// count is NOT accessible directly — it's enclosed (private)
```

### Practical Use Cases
```javascript
// 1. Data privacy / encapsulation
function createWallet(initial) {
  let balance = initial;
  return {
    deposit: (amount) => { balance += amount; },
    getBalance: () => balance,
  };
}

// 2. Function factories
function multiply(x) {
  return (y) => x * y;
}
const double = multiply(2);
const triple = multiply(3);
double(5);  // 10
triple(5);  // 15

// 3. Memoization
function memoize(fn) {
  const cache = {};
  return (...args) => {
    const key = JSON.stringify(args);
    if (key in cache) return cache[key];
    cache[key] = fn(...args);
    return cache[key];
  };
}
```

---

## Higher-Order Functions

Functions that take functions as arguments or return functions.

```javascript
// Takes function as argument
const numbers = [1, 2, 3, 4, 5];
const doubled = numbers.map(n => n * 2);
const evens = numbers.filter(n => n % 2 === 0);
const sum = numbers.reduce((acc, n) => acc + n, 0);

// Returns function
function withLogging(fn) {
  return (...args) => {
    console.log(`Calling with: ${args}`);
    const result = fn(...args);
    console.log(`Result: ${result}`);
    return result;
  };
}
const loggedAdd = withLogging((a, b) => a + b);
loggedAdd(2, 3);  // Logs args and result
```

---

## IIFE (Immediately Invoked Function Expression)

```javascript
(function() {
  const private = 'not accessible outside';
  console.log('Runs immediately');
})();

// With arrow function
(() => {
  console.log('Also runs immediately');
})();

// Use case: avoid polluting global scope (pre-modules)
const module = (() => {
  let count = 0;
  return { increment: () => ++count };
})();
```

---

## Key Interview Questions

**Q: What is a closure?**
> A closure is a function bundled with its lexical environment (the variables in scope when it was created). The inner function retains access to outer variables even after the outer function returns.

**Q: Explain `this` in JavaScript.**
> `this` depends on HOW a function is called: `new` → new object, `call/apply/bind` → specified object, `obj.method()` → obj, plain call → window/undefined. Arrow functions don't have their own `this` — they inherit from the enclosing scope.

**Q: What's the difference between call, apply, and bind?**
> `call` invokes with args as a list. `apply` invokes with args as an array. `bind` returns a new function with `this` permanently set (doesn't invoke immediately).

**Q: Why do arrow functions not have their own `this`?**
> By design. Arrow functions inherit `this` from the enclosing lexical context. This makes them perfect for callbacks (no `.bind(this)` needed) but unsuitable as object methods.
