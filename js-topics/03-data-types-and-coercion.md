# Data Types and Type Coercion

## Primitive Types (7 + 1)

| Type | Example | typeof |
|------|---------|--------|
| Number | `42`, `3.14`, `NaN`, `Infinity` | "number" |
| String | `"hello"`, `'world'`, `` `template` `` | "string" |
| Boolean | `true`, `false` | "boolean" |
| Undefined | `undefined` | "undefined" |
| Null | `null` | "object" (bug) |
| Symbol | `Symbol('id')` | "symbol" |
| BigInt | `10n`, `BigInt(10)` | "bigint" |

### Primitive Properties
- Immutable (can't change the value itself)
- Stored on the stack (value directly)
- Compared by value
- Passed by value (copy)

## Reference Types (Objects)

| Type | Example |
|------|---------|
| Object | `{ key: 'value' }` |
| Array | `[1, 2, 3]` |
| Function | `function() {}` |
| Date | `new Date()` |
| RegExp | `/pattern/g` |
| Map, Set | `new Map()` |

### Reference Properties
- Mutable
- Stored on the heap (variable holds pointer)
- Compared by reference
- Passed by reference (same object)

```javascript
// Primitive: copy
let a = 5;
let b = a;
b = 10;
console.log(a);  // 5 (unchanged)

// Reference: shared
let obj1 = { x: 1 };
let obj2 = obj1;
obj2.x = 99;
console.log(obj1.x);  // 99 (same object!)
```

---

## Type Coercion

### Implicit Coercion (Automatic)
```javascript
// String coercion (+ with string)
'5' + 3        // '53' (number → string)
'5' + true     // '5true'
'5' + {}       // '5[object Object]'

// Number coercion (-, *, /, comparison)
'5' - 3        // 2 (string → number)
'5' * '2'      // 10
true + true    // 2 (true → 1)
false + 1      // 1 (false → 0)
'' - 1         // -1 ('' → 0)
null + 1       // 1 (null → 0)

// Boolean coercion (if, !, &&, ||)
if ('hello') {}   // truthy
if (0) {}         // falsy
```

### Falsy Values (Everything else is truthy)
```javascript
false, 0, -0, 0n, '', null, undefined, NaN
```

### == vs === (Equality)
```javascript
// == (loose): coerces types before comparing
5 == '5'       // true (string → number)
0 == false     // true (false → 0)
null == undefined  // true (special rule)
'' == false    // true

// === (strict): no coercion, checks type AND value
5 === '5'      // false
0 === false    // false
null === undefined  // false

// ALWAYS use === unless you specifically want coercion
```

### Explicit Coercion
```javascript
// To Number
Number('42')       // 42
Number('')         // 0
Number('abc')      // NaN
Number(true)       // 1
parseInt('42px')   // 42
parseFloat('3.14') // 3.14
+'42'              // 42 (unary +)

// To String
String(42)         // '42'
String(null)       // 'null'
(42).toString()    // '42'
`${42}`            // '42'

// To Boolean
Boolean(0)         // false
Boolean('')        // false
Boolean('hello')   // true
Boolean({})        // true (empty object is truthy!)
Boolean([])        // true (empty array is truthy!)
!!value            // double negation
```

---

## Special Values

### NaN
```javascript
typeof NaN          // "number" (ironic)
NaN === NaN         // false (only value not equal to itself!)
isNaN('hello')      // true (coerces to number first - unreliable)
Number.isNaN('hello')  // false (no coercion - use this!)
```

### null vs undefined
| null | undefined |
|------|-----------|
| Intentional absence | Unintentional absence |
| Explicitly assigned | Default for uninitialized |
| `typeof` → "object" | `typeof` → "undefined" |
| `Number(null)` → 0 | `Number(undefined)` → NaN |

---

## Key Interview Questions

**Q: What are falsy values in JavaScript?**
> `false`, `0`, `-0`, `0n`, `""`, `null`, `undefined`, `NaN`. Everything else is truthy, including `[]`, `{}`, and `"0"`.

**Q: What's the difference between `null` and `undefined`?**
> `undefined` means a variable was declared but not assigned. `null` is an intentional assignment meaning "no value." Use `null` to explicitly clear a value; `undefined` is what JS gives you by default.

**Q: Why does `[] == false` return true?**
> Coercion chain: `[] → '' → 0`, and `false → 0`. So `0 == 0` is true. This is why you always use `===`.

**Q: How does `+` behave differently from `-`?**
> `+` with a string concatenates (prefers string coercion). `-`, `*`, `/` always coerce to numbers. `'5' + 3 = '53'` but `'5' - 3 = 2`.

**Q: Is JavaScript pass-by-value or pass-by-reference?**
> Always pass-by-value. But for objects, the "value" is a reference (memory address). So you can mutate the object through the reference, but reassigning the parameter doesn't affect the original.
