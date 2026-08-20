# Execution Context & Call Stack

## Execution Context Types

| Type | Created When |
|------|-------------|
| Global Execution Context | Script starts running |
| Function Execution Context | Function is called |
| Eval Execution Context | eval() is used |

---

## Execution Context Phases

### 1. Creation Phase
```
┌─ Execution Context ──────────────────────┐
│                                           │
│  Lexical Environment:                     │
│    - Environment Record (variables/funcs) │
│    - Outer Reference (scope chain link)   │
│    - this binding                         │
│                                           │
│  Variable Environment:                    │
│    - var declarations (initialized: undefined) │
│    - function declarations (fully hoisted)│
│    - let/const declarations (TDZ)         │
│                                           │
└───────────────────────────────────────────┘
```

### 2. Execution Phase
- Code executes line by line
- Variables get assigned
- Functions get invoked (create new contexts)

---

## Call Stack

```javascript
function first() {
  console.log('first start');
  second();
  console.log('first end');
}

function second() {
  console.log('second start');
  third();
  console.log('second end');
}

function third() {
  console.log('third');
}

first();
```

### Stack Visualization
```
Step 1: | Global |
Step 2: | first()  |  ← first() called
        | Global   |
Step 3: | second() |  ← second() called inside first()
        | first()  |
        | Global   |
Step 4: | third()  |  ← third() called inside second()
        | second() |
        | first()  |
        | Global   |
Step 5: | second() |  ← third() returns, popped off
        | first()  |
        | Global   |
Step 6: | first()  |  ← second() returns
        | Global   |
Step 7: | Global   |  ← first() returns
```

---

## Scope Chain

```javascript
let x = 'global';

function outer() {
  let y = 'outer';
  
  function inner() {
    let z = 'inner';
    console.log(x, y, z);  // 'global', 'outer', 'inner'
    // Scope chain: inner → outer → global
  }
  inner();
}
outer();
```

### How Scope Chain Works
1. Look in current execution context's variable environment
2. If not found, follow `outerReference` to parent's environment
3. Continue up until global scope
4. If not found in global → ReferenceError

---

## Lexical vs Variable Environment

| Lexical Environment | Variable Environment |
|--------------------|--------------------|
| Stores let/const bindings | Stores var bindings |
| Block-scoped | Function-scoped |
| Respects blocks {} | Only respects function boundaries |

```javascript
function example() {
  var a = 1;      // Variable Environment
  let b = 2;      // Lexical Environment
  
  if (true) {
    var c = 3;    // Same Variable Environment (function-scoped)
    let d = 4;    // New Lexical Environment (block-scoped)
  }
  
  console.log(a, b, c);  // 1, 2, 3
  console.log(d);        // ReferenceError
}
```

---

## Stack Overflow

```javascript
// Infinite recursion = Stack Overflow
function infinite() {
  infinite();  // Never returns, keeps pushing
}
infinite();  // RangeError: Maximum call stack size exceeded

// Fix with tail call or iteration
function factorial(n, acc = 1) {
  if (n <= 1) return acc;
  return factorial(n - 1, n * acc);  // Tail call (optimized in strict mode)
}
```

---

## Key Interview Questions

**Q: What is an execution context?**
> The environment in which JavaScript code is evaluated and executed. Contains: variable bindings (Variable Object), scope chain (link to outer contexts), and `this` value. Every function call creates a new one.

**Q: What is the call stack?**
> A LIFO (Last-In-First-Out) data structure that tracks function execution. When a function is called, its execution context is pushed. When it returns, it's popped. If the stack exceeds its limit (deep recursion), you get a stack overflow.

**Q: How does the scope chain relate to execution context?**
> Each execution context has a reference to its outer (parent) environment. The scope chain is formed by following these outer references from current context up to global. This is how variable lookups work.

**Q: Why is understanding execution context important?**
> It explains hoisting (creation phase), `this` binding, closures (functions remember their lexical environment), and the scope chain. Most tricky JavaScript interview questions test these concepts.
