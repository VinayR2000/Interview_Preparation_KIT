# Strings and Numbers

## String Methods

```javascript
const str = 'Hello, World!';

// Access
str.length;                  // 13
str.charAt(0);               // 'H'
str[0];                      // 'H'

// Search
str.includes('World');       // true
str.startsWith('Hello');     // true
str.endsWith('!');           // true
str.indexOf('o');            // 4 (first occurrence)
str.lastIndexOf('o');        // 8 (last occurrence)

// Extract
str.slice(0, 5);             // 'Hello' (start, end)
str.slice(-6);               // 'orld!' (from end)
str.substring(7, 12);       // 'World'

// Transform
str.toUpperCase();           // 'HELLO, WORLD!'
str.toLowerCase();           // 'hello, world!'
str.trim();                  // Remove whitespace both ends
str.trimStart();             // Remove leading whitespace
str.trimEnd();               // Remove trailing whitespace
str.padStart(15, '0');       // '00Hello, World!'
str.padEnd(15, '.');         // 'Hello, World!..'
str.repeat(2);               // 'Hello, World!Hello, World!'

// Replace
str.replace('World', 'JS');      // 'Hello, JS!' (first only)
str.replaceAll('l', 'L');        // 'HeLLo, WorLd!'
str.replace(/[aeiou]/gi, '*');   // 'H*ll*, W*rld!' (regex)

// Split
'a,b,c'.split(',');          // ['a', 'b', 'c']
'hello'.split('');           // ['h', 'e', 'l', 'l', 'o']

// Template Literals
const name = 'World';
`Hello, ${name}!`;          // 'Hello, World!'
`Line 1\nLine 2`;           // Multi-line
`${2 + 3}`;                 // '5' (expressions)
```

### String Immutability
```javascript
// Strings are immutable - methods return NEW strings
const str = 'hello';
str.toUpperCase();    // Returns 'HELLO'
console.log(str);     // Still 'hello'
str[0] = 'H';        // Silent failure (no error, but no effect)
```

---

## Numbers & Math

```javascript
// Number quirks
typeof NaN;                  // 'number'
NaN === NaN;                 // false
0.1 + 0.2 === 0.3;         // false (floating point!)
0.1 + 0.2;                  // 0.30000000000000004

// Checking numbers
Number.isNaN(NaN);           // true (use this, not global isNaN)
Number.isFinite(42);         // true
Number.isFinite(Infinity);   // false
Number.isInteger(5.0);       // true
Number.isInteger(5.5);       // false

// Parsing
parseInt('42px');            // 42
parseInt('0xFF', 16);       // 255
parseFloat('3.14abc');      // 3.14
Number('42');               // 42
Number('42px');             // NaN (stricter than parseInt)

// Math object
Math.round(4.5);            // 5
Math.floor(4.9);            // 4
Math.ceil(4.1);             // 5
Math.trunc(4.9);            // 4 (remove decimal)
Math.abs(-5);               // 5
Math.max(1, 2, 3);          // 3
Math.min(1, 2, 3);          // 1
Math.random();              // 0 to 0.999...
Math.pow(2, 3);             // 8
Math.sqrt(16);              // 4

// Random integer between min and max (inclusive)
const random = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;

// BigInt (arbitrary precision integers)
const big = 9007199254740993n;
typeof big;                 // 'bigint'
big + 1n;                   // 9007199254740994n
// Cannot mix BigInt with Number: big + 1 → TypeError
```

---

## Key Interview Questions

**Q: Why does `0.1 + 0.2 !== 0.3`?**
> Floating-point precision issue. Numbers are stored in IEEE 754 binary format, and 0.1/0.2 can't be represented exactly in binary. Fix: `Math.abs(0.1 + 0.2 - 0.3) < Number.EPSILON` or use `toFixed()` for display.

**Q: What's the difference between `parseInt` and `Number()`?**
> `parseInt` parses from left until invalid char: `parseInt('42px')` → 42. `Number()` converts entire string: `Number('42px')` → NaN. Use parseInt for extracting numbers from mixed strings.

**Q: Are strings mutable in JavaScript?**
> No. Strings are immutable primitives. All string methods return new strings without modifying the original.
