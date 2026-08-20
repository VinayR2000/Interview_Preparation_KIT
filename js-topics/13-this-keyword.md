# The `this` Keyword

## Rules (Priority Order)

| # | Rule | `this` value |
|---|------|-------------|
| 1 | `new` keyword | Newly created object |
| 2 | `call`/`apply`/`bind` | Specified object |
| 3 | Method call (`obj.fn()`) | The object before dot |
| 4 | Default (plain call) | `window` (non-strict) / `undefined` (strict) |
| 5 | Arrow function | Inherits from enclosing lexical scope |

---

## Global Context

```javascript
// Non-strict mode
console.log(this);  // window (browser) / globalThis (Node)

// Strict mode
"use strict";
function show() {
  console.log(this);  // undefined
}
show();
```

---

## Object Method

```javascript
const user = {
  name: 'John',
  greet() {
    console.log(this.name);  // 'John' — this = user
  }
};
user.greet();

// Lost binding
const fn = user.greet;
fn();  // undefined — plain function call, this = window/undefined
```

---

## Constructor (`new`)

```javascript
function Person(name) {
  // new creates empty object, assigns to this
  this.name = name;
  // implicitly returns this
}
const p = new Person('John');  // this = {} → {name: 'John'}
```

---

## call(), apply(), bind()

```javascript
function introduce(greeting, punct) {
  console.log(`${greeting}, I'm ${this.name}${punct}`);
}

const user = { name: 'John' };

// call - args as comma-separated list
introduce.call(user, 'Hi', '!');      // "Hi, I'm John!"

// apply - args as array
introduce.apply(user, ['Hello', '.']); // "Hello, I'm John."

// bind - returns new function with this permanently set
const boundFn = introduce.bind(user, 'Hey');
boundFn('?');                          // "Hey, I'm John?"
// bind is permanent - can't rebind
boundFn.call({ name: 'Jane' }, '!');  // Still "Hey, I'm John!"
```

---

## Arrow Function `this`

```javascript
// Arrow functions do NOT have their own this
// They inherit this from enclosing lexical scope

const user = {
  name: 'John',
  // ❌ Arrow as method — this = outer scope (window), NOT user
  greet: () => console.log(this.name),  // undefined
  
  // ✅ Regular method
  hello() {
    console.log(this.name);  // 'John'
    
    // ✅ Arrow in callback — inherits this from hello()
    setTimeout(() => {
      console.log(this.name);  // 'John' (captured from hello)
    }, 100);
  }
};

user.greet();  // undefined (arrow doesn't bind this)
user.hello();  // 'John', then 'John' after timeout
```

### Arrow vs Regular in Callbacks
```javascript
const obj = {
  count: 0,
  
  // ❌ Regular function in callback loses this
  startBad() {
    setTimeout(function() {
      this.count++;  // this = window, not obj
    }, 100);
  },
  
  // ✅ Arrow function captures this from startGood
  startGood() {
    setTimeout(() => {
      this.count++;  // this = obj ✓
    }, 100);
  },
  
  // ✅ Alternative: bind
  startBind() {
    setTimeout(function() {
      this.count++;
    }.bind(this), 100);
  }
};
```

---

## Common Tricky Scenarios

```javascript
// 1. Method passed as callback
const obj = {
  value: 42,
  getValue() { return this.value; }
};
const fn = obj.getValue;
fn();           // undefined (lost binding)
fn.call(obj);   // 42

// 2. Nested function
const obj = {
  name: 'test',
  method() {
    function inner() {
      console.log(this.name);  // undefined (plain call)
    }
    inner();
    
    const arrowInner = () => {
      console.log(this.name);  // 'test' (arrow inherits)
    };
    arrowInner();
  }
};

// 3. Event handlers
button.addEventListener('click', function() {
  console.log(this);  // <button> element (implicit binding)
});
button.addEventListener('click', () => {
  console.log(this);  // window/undefined (arrow - no own this)
});

// 4. Class methods
class Timer {
  count = 0;
  start() {
    // ❌ this lost in setInterval callback
    setInterval(function() { this.count++; }, 1000);
    // ✅ Arrow keeps this
    setInterval(() => { this.count++; }, 1000);
  }
}
```

---

## Key Interview Questions

**Q: What is `this` in JavaScript?**
> `this` is a runtime binding that refers to the execution context of a function. Its value depends on HOW the function is called, not where it's defined (except for arrow functions which use lexical binding).

**Q: Why don't arrow functions have their own `this`?**
> By design. They inherit `this` from the enclosing lexical scope. This makes them ideal for callbacks (no need for `.bind(this)` or `const self = this`). But it makes them wrong for object methods.

**Q: How to fix lost `this` binding?**
> Three options: 1) Arrow function (captures this from outer scope). 2) `.bind(this)` to create a permanently bound function. 3) Store reference: `const self = this` (old pattern).

**Q: What does `new` do to `this`?**
> 1) Creates empty object. 2) Sets `this` to that object. 3) Links `__proto__` to constructor's prototype. 4) Executes constructor body. 5) Returns `this` (unless constructor returns another object).

**Q: Can you `call`/`apply` an arrow function to change its `this`?**
> No. Arrow functions permanently inherit `this` from their enclosing scope. `call`, `apply`, and `bind` have no effect on an arrow function's `this`.
