# Variables, Scope, and Hoisting

## var vs let vs const

| Feature | var | let | const |
|---------|-----|-----|-------|
| Scope | Function | Block | Block |
| Hoisting | Yes (initialized as `undefined`) | Yes (TDZ) | Yes (TDZ) |
| Reassignment | ✅ | ✅ | ❌ |
| Redeclaration | ✅ | ❌ | ❌ |
| Global object property | ✅ (`window.x`) | ❌ | ❌ |

```javascript
// var: function-scoped
function example() {
  if (true) {
    var x = 10;
  }
  console.log(x); // 10 (accessible outside block!)
}

// let: block-scoped
function example() {
  if (true) {
    let y = 10;
  }
  console.log(y); // ReferenceError
}

// const: block-scoped, cannot reassign
const PI = 3.14;
PI = 3.15; // ❌ TypeError

// BUT objects/arrays can be mutated
const user = { name: 'John' };
user.name = 'Jane';    // ✅ Mutation allowed
user = {};             // ❌ Reassignment not allowed
```

---

## Scope

### Types of Scope
```javascript
// 1. Global Scope
var globalVar = 'global';  // Available everywhere

// 2. Function Scope
function myFunc() {
  var funcVar = 'function';  // Only inside this function
}

// 3. Block Scope (let/const only)
if (true) {
  let blockVar = 'block';   // Only inside this block
}

// 4. Lexical Scope (Closures)
function outer() {
  let x = 10;
  function inner() {
    console.log(x);  // Can access outer's variables
  }
  return inner;
}
```

### Scope Chain
```javascript
let a = 'global';
function outer() {
  let b = 'outer';
  function inner() {
    let c = 'inner';
    console.log(a, b, c);  // Looks up: inner → outer → global
  }
  inner();
}
// JS engine looks up the scope chain until it finds the variable
```

---

## Hoisting

### What is Hoisting?
- JavaScript moves declarations to the top of their scope during compilation
- Only declarations are hoisted, not initializations

### var Hoisting
```javascript
console.log(x);  // undefined (not ReferenceError!)
var x = 5;
console.log(x);  // 5

// What JS sees:
var x;            // Declaration hoisted
console.log(x);  // undefined
x = 5;           // Initialization stays
console.log(x);  // 5
```

### let/const Hoisting (Temporal Dead Zone)
```javascript
console.log(y);  // ReferenceError: Cannot access 'y' before initialization
let y = 5;

// let/const ARE hoisted but placed in TDZ (Temporal Dead Zone)
// TDZ = from start of block until declaration is reached
{
  // TDZ starts for 'z'
  console.log(z);  // ReferenceError (TDZ)
  let z = 10;      // TDZ ends here
  console.log(z);  // 10
}
```

### Function Hoisting
```javascript
// Function declarations are FULLY hoisted (body + name)
greet();  // "Hello!" ← Works!
function greet() { console.log("Hello!"); }

// Function expressions are NOT hoisted (only variable name)
sayHi();  // TypeError: sayHi is not a function
var sayHi = function() { console.log("Hi!"); };

// Arrow functions behave like function expressions
getName(); // TypeError
var getName = () => "John";
```

---

## Temporal Dead Zone (TDZ)

```javascript
let x = 'outer';

function example() {
  // TDZ for 'x' starts here (shadows outer 'x')
  console.log(x);  // ReferenceError! Not 'outer'!
  let x = 'inner'; // TDZ ends
  console.log(x);  // 'inner'
}
```

**Why TDZ exists**: Catches bugs where you accidentally use a variable before it's properly initialized. With `var`, you'd silently get `undefined`.

---

## Key Interview Questions

**Q: What's the difference between `undefined` and `not defined`?**
> `undefined`: Variable exists but has no value assigned. `not defined` (ReferenceError): Variable doesn't exist in any accessible scope.

**Q: Why prefer const over let?**
> `const` communicates intent (value won't change), prevents accidental reassignment, enables engine optimizations. Use `let` only when you genuinely need to reassign.

**Q: What is the Temporal Dead Zone?**
> The period between entering a scope and the `let`/`const` declaration being reached. Accessing the variable in this zone throws a ReferenceError. It exists to catch bugs early.

**Q: Does hoisting happen with let/const?**
> Yes. They are hoisted (the engine knows about them) but are NOT initialized. They exist in the TDZ until the declaration line. With `var`, hoisting initializes to `undefined`.

**Q: Why does `var` in a loop cause closure bugs?**
```javascript
for (var i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 100);  // 3, 3, 3
}
// Fix: use let (block-scoped per iteration)
for (let i = 0; i < 3; i++) {
  setTimeout(() => console.log(i), 100);  // 0, 1, 2
}
```
> `var` is function-scoped — there's one shared `i`. By the time callbacks run, `i` is 3. `let` creates a new binding per iteration.
