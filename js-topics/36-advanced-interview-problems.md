# Advanced Interview Problems & Polyfills

## Output-Based Questions

### Closure + Loop
```javascript
for (var i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 100);
}
// Output: 3, 3, 3 (var is function-scoped, one shared i)

// Fix 1: let
for (let i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 100);
}
// Output: 0, 1, 2

// Fix 2: IIFE
for (var i = 0; i < 3; i++) {
  ((j) => setTimeout(() => console.log(j), 100))(i);
}
```

### Event Loop Order
```javascript
console.log('1');
setTimeout(() => console.log('2'), 0);
Promise.resolve().then(() => console.log('3'));
Promise.resolve().then(() => setTimeout(() => console.log('4'), 0));
Promise.resolve().then(() => console.log('5'));
console.log('6');
// Output: 1, 6, 3, 5, 2, 4
```

### this Binding
```javascript
const obj = {
  name: 'Alice',
  getName: function() { return this.name; },
  getNameArrow: () => this.name,
};
console.log(obj.getName());        // 'Alice'
console.log(obj.getNameArrow());   // undefined (arrow → global this)

const fn = obj.getName;
console.log(fn());                 // undefined (lost binding)
```

---

## Polyfill Implementations

### Array.prototype.map
```javascript
Array.prototype.myMap = function(callback, thisArg) {
  const result = [];
  for (let i = 0; i < this.length; i++) {
    if (i in this) {
      result[i] = callback.call(thisArg, this[i], i, this);
    }
  }
  return result;
};
```

### Array.prototype.filter
```javascript
Array.prototype.myFilter = function(callback, thisArg) {
  const result = [];
  for (let i = 0; i < this.length; i++) {
    if (i in this && callback.call(thisArg, this[i], i, this)) {
      result.push(this[i]);
    }
  }
  return result;
};
```

### Array.prototype.reduce
```javascript
Array.prototype.myReduce = function(callback, initialValue) {
  let accumulator = initialValue;
  let startIndex = 0;
  
  if (accumulator === undefined) {
    if (this.length === 0) throw new TypeError('Reduce of empty array with no initial value');
    accumulator = this[0];
    startIndex = 1;
  }
  
  for (let i = startIndex; i < this.length; i++) {
    if (i in this) {
      accumulator = callback(accumulator, this[i], i, this);
    }
  }
  return accumulator;
};
```

### Function.prototype.bind
```javascript
Function.prototype.myBind = function(context, ...args) {
  const fn = this;
  return function(...moreArgs) {
    return fn.apply(context, [...args, ...moreArgs]);
  };
};
```

---

## Common Implementations

### Promise.all
```javascript
function promiseAll(promises) {
  return new Promise((resolve, reject) => {
    const results = [];
    let completed = 0;
    
    if (promises.length === 0) return resolve([]);
    
    promises.forEach((promise, index) => {
      Promise.resolve(promise).then(value => {
        results[index] = value;
        completed++;
        if (completed === promises.length) resolve(results);
      }).catch(reject);  // Reject immediately on first failure
    });
  });
}
```

### Debounce
```javascript
function debounce(fn, delay) {
  let timer;
  return function(...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}
```

### Throttle
```javascript
function throttle(fn, interval) {
  let lastTime = 0;
  return function(...args) {
    const now = Date.now();
    if (now - lastTime >= interval) {
      lastTime = now;
      return fn.apply(this, args);
    }
  };
}
```

### Deep Clone
```javascript
function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj;
  if (obj instanceof Date) return new Date(obj);
  if (obj instanceof RegExp) return new RegExp(obj);
  if (Array.isArray(obj)) return obj.map(item => deepClone(item));
  
  const clone = {};
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      clone[key] = deepClone(obj[key]);
    }
  }
  return clone;
}
```

### Curry
```javascript
function curry(fn) {
  return function curried(...args) {
    if (args.length >= fn.length) {
      return fn.apply(this, args);
    }
    return (...moreArgs) => curried(...args, ...moreArgs);
  };
}
```

### Function Composition
```javascript
function compose(...fns) {
  return (x) => fns.reduceRight((acc, fn) => fn(acc), x);
}

function pipe(...fns) {
  return (x) => fns.reduce((acc, fn) => fn(acc), x);
}
```

### Flat Array (any depth)
```javascript
function flatten(arr, depth = 1) {
  if (depth === 0) return [...arr];
  return arr.reduce((result, item) => {
    if (Array.isArray(item)) {
      result.push(...flatten(item, depth - 1));
    } else {
      result.push(item);
    }
    return result;
  }, []);
}
```

### EventEmitter
```javascript
class EventEmitter {
  constructor() { this.events = {}; }
  
  on(event, listener) {
    (this.events[event] ??= []).push(listener);
    return this;
  }
  
  off(event, listener) {
    this.events[event] = (this.events[event] || []).filter(l => l !== listener);
    return this;
  }
  
  emit(event, ...args) {
    (this.events[event] || []).forEach(listener => listener(...args));
    return this;
  }
  
  once(event, listener) {
    const wrapper = (...args) => {
      listener(...args);
      this.off(event, wrapper);
    };
    return this.on(event, wrapper);
  }
}
```

---

## Tricky Questions

### typeof quirks
```javascript
typeof undefined    // "undefined"
typeof null         // "object" (bug)
typeof NaN          // "number"
typeof []           // "object"
typeof function(){} // "function"
typeof 1n           // "bigint"
```

### Equality quirks
```javascript
[] == false         // true ([] → '' → 0, false → 0)
[] == ![]           // true ([] truthy, ![] = false, then coercion)
null == undefined   // true (special rule)
null === undefined  // false
NaN == NaN          // false
NaN === NaN         // false
```

### Hoisting combined
```javascript
var x = 1;
function foo() {
  console.log(x);  // undefined (var x hoisted in function scope)
  var x = 2;
  console.log(x);  // 2
}
foo();
console.log(x);    // 1 (global x untouched)
```

---

## Tips for Coding Interviews

1. **Closures**: Remember variables are captured by reference, not value
2. **Event Loop**: Sync → Microtasks (all) → Macrotask (one) → repeat
3. **this**: Check how function is CALLED, not where it's defined
4. **Hoisting**: var = undefined, function = fully hoisted, let/const = TDZ
5. **Coercion**: Know falsy values and `==` rules
6. **Prototypes**: `obj.__proto__ === Constructor.prototype`
