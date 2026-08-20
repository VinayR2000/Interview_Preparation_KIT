# Asynchronous JavaScript

## Event Loop

```
┌─────────────────────────────────────┐
│           Call Stack                  │  Executes synchronous code
├─────────────────────────────────────┤
│     Web APIs / Node APIs             │  setTimeout, fetch, DOM events
├─────────────────────────────────────┤
│  Microtask Queue (high priority)     │  Promise .then, queueMicrotask
├─────────────────────────────────────┤
│  Macrotask Queue (low priority)      │  setTimeout, setInterval, I/O
└─────────────────────────────────────┘
         ↑ Event Loop checks:
         1. Call stack empty?
         2. Drain ALL microtasks
         3. Execute ONE macrotask
         4. Repeat
```

### Execution Order Example
```javascript
console.log('1');                              // Sync
setTimeout(() => console.log('2'), 0);        // Macrotask
Promise.resolve().then(() => console.log('3')); // Microtask
queueMicrotask(() => console.log('4'));       // Microtask
console.log('5');                              // Sync

// Output: 1, 5, 3, 4, 2
```

---

## Callbacks

```javascript
function fetchData(callback) {
  setTimeout(() => {
    callback(null, { id: 1, name: 'John' });
  }, 1000);
}

fetchData((error, data) => {
  if (error) return console.error(error);
  console.log(data);
});

// Callback Hell (Pyramid of Doom)
getUser(userId, (user) => {
  getOrders(user.id, (orders) => {
    getItems(orders[0].id, (items) => {
      // Deeply nested, hard to read/maintain
    });
  });
});
```

---

## Promises

```javascript
// Creating a Promise
const promise = new Promise((resolve, reject) => {
  const success = true;
  if (success) resolve('Data loaded');
  else reject(new Error('Failed'));
});

// Consuming
promise
  .then(data => console.log(data))
  .catch(err => console.error(err))
  .finally(() => console.log('Done'));

// Chaining (each .then returns a new Promise)
fetch('/api/user')
  .then(res => res.json())         // Returns Promise
  .then(user => fetch(`/api/orders/${user.id}`))
  .then(res => res.json())
  .then(orders => console.log(orders))
  .catch(err => console.error(err));  // Catches any error in chain
```

### Promise States
| State | Description | Transition |
|-------|-------------|------------|
| Pending | Initial state | → Fulfilled or Rejected |
| Fulfilled | Resolved successfully | Terminal |
| Rejected | Failed with error | Terminal |

---

## async/await

```javascript
async function fetchUser(id) {
  try {
    const response = await fetch(`/api/users/${id}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const user = await response.json();
    return user;
  } catch (error) {
    console.error('Failed:', error.message);
    throw error;
  }
}

// await pauses execution until Promise settles
// async function always returns a Promise
```

---

## Promise Combinators

```javascript
const p1 = fetch('/api/users');
const p2 = fetch('/api/posts');
const p3 = fetch('/api/comments');

// Promise.all - ALL must succeed (fails fast on first rejection)
const [users, posts, comments] = await Promise.all([p1, p2, p3]);

// Promise.allSettled - Get all results regardless of success/failure
const results = await Promise.allSettled([p1, p2, p3]);
// [{status:'fulfilled', value:...}, {status:'rejected', reason:...}]

// Promise.race - First to settle (resolve OR reject) wins
const fastest = await Promise.race([fetchFromCDN1(), fetchFromCDN2()]);

// Promise.any - First to SUCCEED wins (ignores rejections)
const first = await Promise.any([p1, p2, p3]);
// Only rejects if ALL reject (AggregateError)
```

---

## setTimeout / setInterval

```javascript
// setTimeout - Execute once after delay
const timerId = setTimeout(() => console.log('Later'), 2000);
clearTimeout(timerId);  // Cancel

// setInterval - Execute repeatedly at interval
const intervalId = setInterval(() => console.log('Tick'), 1000);
clearInterval(intervalId);  // Cancel

// setTimeout with 0ms - still async (goes to macrotask queue)
setTimeout(() => console.log('async'), 0);
console.log('sync');
// Output: 'sync', 'async'
```

---

## Key Interview Questions

**Q: What is the event loop?**
> The mechanism that handles async operations in single-threaded JavaScript. It monitors the call stack and task queues. When the stack is empty, it processes all microtasks, then one macrotask, then repeats.

**Q: Microtask vs Macrotask?**
> Microtasks (Promise callbacks, queueMicrotask) have higher priority — ALL drain before the next macrotask. Macrotasks (setTimeout, setInterval, I/O) execute one at a time with microtask checks between them.

**Q: What happens if a Promise is never resolved or rejected?**
> It stays in "pending" state forever. The `.then`/`.catch` callbacks never fire. The Promise object may eventually be garbage collected if nothing references it.

**Q: What's the difference between Promise.all and Promise.allSettled?**
> `Promise.all` fails fast — if any promise rejects, the entire thing rejects immediately. `Promise.allSettled` waits for ALL promises to complete (regardless of success/failure) and returns all results.

**Q: Can you use await at the top level?**
> Yes, in ES modules (top-level await). Not in regular scripts or CommonJS modules. The module execution pauses until the awaited promise settles.
