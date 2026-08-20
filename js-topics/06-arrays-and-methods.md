# Arrays and Array Methods

## Array Basics

```javascript
const arr = [1, 2, 3, 4, 5];
arr.length;      // 5
arr[0];          // 1
arr[arr.length - 1];  // 5 (last element)
Array.isArray(arr);   // true
```

---

## Transformation Methods (Return new array - IMMUTABLE)

```javascript
const nums = [1, 2, 3, 4, 5];

// map - Transform each element
nums.map(n => n * 2);           // [2, 4, 6, 8, 10]

// filter - Keep matching elements
nums.filter(n => n > 3);        // [4, 5]

// reduce - Accumulate to single value
nums.reduce((sum, n) => sum + n, 0);  // 15

// flat - Flatten nested arrays
[[1,2], [3,4], [5]].flat();    // [1, 2, 3, 4, 5]
[[1,[2,[3]]]].flat(Infinity);  // [1, 2, 3]

// flatMap - map + flat(1)
[1, 2, 3].flatMap(n => [n, n * 2]);  // [1, 2, 2, 4, 3, 6]

// slice - Extract portion (doesn't mutate)
nums.slice(1, 3);               // [2, 3] (start inclusive, end exclusive)
nums.slice(-2);                 // [4, 5] (last 2)
```

---

## Search Methods

```javascript
const users = [
  { id: 1, name: 'John', age: 25 },
  { id: 2, name: 'Jane', age: 30 },
  { id: 3, name: 'Bob', age: 25 },
];

// find - First match (returns element or undefined)
users.find(u => u.age === 25);         // { id: 1, name: 'John', age: 25 }

// findIndex - First match index (returns -1 if not found)
users.findIndex(u => u.name === 'Jane');  // 1

// some - At least one matches? (returns boolean)
users.some(u => u.age > 28);           // true

// every - All match? (returns boolean)
users.every(u => u.age > 20);          // true

// includes - Contains value? (primitives only)
[1, 2, 3].includes(2);                 // true

// indexOf / lastIndexOf
[1, 2, 3, 2].indexOf(2);              // 1 (first occurrence)
[1, 2, 3, 2].lastIndexOf(2);          // 3 (last occurrence)
```

---

## Mutating Methods (Modify original array)

```javascript
const arr = [1, 2, 3];

// push / pop - End
arr.push(4);         // [1, 2, 3, 4] returns new length
arr.pop();           // [1, 2, 3] returns removed element

// unshift / shift - Beginning
arr.unshift(0);      // [0, 1, 2, 3]
arr.shift();         // [1, 2, 3]

// splice - Insert/remove at index
arr.splice(1, 1);       // Remove 1 element at index 1 → [1, 3]
arr.splice(1, 0, 'a');  // Insert 'a' at index 1 → [1, 'a', 3]
arr.splice(1, 1, 'b');  // Replace at index 1 → [1, 'b', 3]

// sort (mutates!)
[3, 1, 2].sort();                    // [1, 2, 3] (lexicographic by default)
[10, 9, 2].sort();                   // [10, 2, 9] ← WRONG! (string sort)
[10, 9, 2].sort((a, b) => a - b);   // [2, 9, 10] ← Correct numeric sort

// reverse (mutates!)
[1, 2, 3].reverse();  // [3, 2, 1]

// Immutable alternatives:
[...arr].sort();     // Sort copy
[...arr].reverse();  // Reverse copy
```

---

## Iteration

```javascript
// forEach - Execute function for each (no return value)
arr.forEach((item, index) => console.log(index, item));

// for...of - Iterate values
for (const item of arr) { console.log(item); }

// for...in - Iterate keys/indices (avoid for arrays)
for (const index in arr) { console.log(index); }  // "0", "1", "2" (strings!)
```

---

## Common Patterns

```javascript
// Remove duplicates
const unique = [...new Set([1, 2, 2, 3, 3])];  // [1, 2, 3]

// Group by
const grouped = users.reduce((acc, user) => {
  const key = user.age;
  acc[key] = acc[key] || [];
  acc[key].push(user);
  return acc;
}, {});
// Or: Object.groupBy(users, u => u.age) (ES2024)

// Chunk array
function chunk(arr, size) {
  return Array.from({ length: Math.ceil(arr.length / size) }, (_, i) =>
    arr.slice(i * size, i * size + size)
  );
}

// Flatten to map/object
const map = users.reduce((acc, u) => ({ ...acc, [u.id]: u }), {});
```

---

## Key Interview Questions

**Q: What's the difference between map and forEach?**
> `map` returns a new array with transformed values. `forEach` returns undefined — use it for side effects only. Use `map` when you need the result; `forEach` when you just want to execute something.

**Q: How does reduce work?**
> It iterates through the array, passing an accumulator and current value to the callback. The return value becomes the next accumulator. The second argument is the initial accumulator value. Can transform arrays into any structure (object, number, array, etc.).

**Q: What's the difference between find and filter?**
> `find` returns the first matching element (or undefined). `filter` returns ALL matching elements in a new array. Use `find` for single lookups, `filter` for subsetting.

**Q: How to safely sort numbers?**
> Always provide a comparator: `arr.sort((a, b) => a - b)`. Default sort is lexicographic (converts to strings), so `[10, 9, 2].sort()` gives `[10, 2, 9]`.
