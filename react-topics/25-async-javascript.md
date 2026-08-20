# Async JavaScript for React

## Callback → Promise → async/await

### Callback (Old Pattern)
```javascript
function fetchUser(id, callback) {
  setTimeout(() => callback({ id, name: 'John' }), 1000);
}
fetchUser(1, (user) => console.log(user));  // Callback hell with nesting
```

### Promise
```javascript
function fetchUser(id) {
  return new Promise((resolve, reject) => {
    setTimeout(() => resolve({ id, name: 'John' }), 1000);
  });
}

fetchUser(1)
  .then(user => console.log(user))
  .catch(err => console.error(err))
  .finally(() => console.log('Done'));
```

### async/await (Modern - Use This)
```javascript
async function loadUser() {
  try {
    const user = await fetchUser(1);
    console.log(user);
  } catch (err) {
    console.error(err);
  }
}
```

---

## Promise Combinators

### Promise.all - All must succeed
```javascript
const [users, posts, comments] = await Promise.all([
  fetchUsers(),
  fetchPosts(),
  fetchComments(),
]);
// Fails fast: If ANY rejects, entire Promise.all rejects
```

### Promise.allSettled - Get all results regardless
```javascript
const results = await Promise.allSettled([fetchA(), fetchB(), fetchC()]);
// results: [{status: 'fulfilled', value: ...}, {status: 'rejected', reason: ...}]
```

### Promise.race - First to settle wins
```javascript
const result = await Promise.race([fetchData(), timeout(5000)]);
// Whichever resolves/rejects first
```

---

## Event Loop

```
┌───────────────────────────┐
│        Call Stack          │  ← Synchronous code executes here
├───────────────────────────┤
│     Microtask Queue       │  ← Promises (.then), queueMicrotask
├───────────────────────────┤
│     Macrotask Queue       │  ← setTimeout, setInterval, I/O
└───────────────────────────┘

Execution Order:
1. Execute all synchronous code (call stack)
2. Execute ALL microtasks (Promise callbacks)
3. Execute ONE macrotask (setTimeout callback)
4. Repeat from step 2
```

### Example
```javascript
console.log('1');                          // Sync
setTimeout(() => console.log('2'), 0);    // Macrotask
Promise.resolve().then(() => console.log('3'));  // Microtask
console.log('4');                          // Sync

// Output: 1, 4, 3, 2
```

---

## AbortController

```javascript
// Cancel fetch requests
const controller = new AbortController();

fetch('/api/data', { signal: controller.signal })
  .then(res => res.json())
  .catch(err => {
    if (err.name === 'AbortError') console.log('Request cancelled');
  });

// Cancel the request
controller.abort();
```

### In React
```jsx
useEffect(() => {
  const controller = new AbortController();
  
  fetch(url, { signal: controller.signal })
    .then(res => res.json())
    .then(setData)
    .catch(err => {
      if (err.name !== 'AbortError') setError(err);
    });

  return () => controller.abort();  // Cancel on cleanup
}, [url]);
```

---

## Key Interview Questions

**Q: What's the difference between microtask and macrotask?**
> Microtasks (Promises, queueMicrotask) execute between macrotasks and have higher priority. ALL microtasks drain before the next macrotask runs. Macrotasks (setTimeout, events) execute one at a time.

**Q: Why use async/await over .then()?**
> Cleaner syntax, looks synchronous, easier error handling with try/catch, better debugging (stack traces), avoids deeply nested .then chains.

**Q: What happens if you await a non-Promise?**
> It's wrapped in Promise.resolve() automatically. `await 5` is equivalent to `await Promise.resolve(5)` and returns 5 immediately.
