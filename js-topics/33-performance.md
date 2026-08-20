# JavaScript Performance

## Debouncing

Execute function only after user stops triggering it for a specified delay.

```javascript
function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

// Usage: Search input (only search after user stops typing)
const search = debounce((query) => fetchResults(query), 300);
input.addEventListener('input', (e) => search(e.target.value));
```

---

## Throttling

Execute function at most once per interval, regardless of how often triggered.

```javascript
function throttle(fn, interval) {
  let lastTime = 0;
  return (...args) => {
    const now = Date.now();
    if (now - lastTime >= interval) {
      lastTime = now;
      fn(...args);
    }
  };
}

// Usage: Scroll handler (at most once per 100ms)
window.addEventListener('scroll', throttle(handleScroll, 100));
```

---

## Memoization

Cache expensive function results.

```javascript
function memoize(fn) {
  const cache = new Map();
  return (...args) => {
    const key = JSON.stringify(args);
    if (cache.has(key)) return cache.get(key);
    const result = fn(...args);
    cache.set(key, result);
    return result;
  };
}

const expensiveCalc = memoize((n) => {
  console.log('Computing...');
  return n * n;
});
expensiveCalc(5);  // Computing... 25
expensiveCalc(5);  // 25 (cached, no "Computing...")
```

---

## Lazy Loading

```javascript
// Dynamic import (load module only when needed)
button.addEventListener('click', async () => {
  const { heavyFunction } = await import('./heavy-module.js');
  heavyFunction();
});

// IntersectionObserver for images
const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.src = entry.target.dataset.src;
      observer.unobserve(entry.target);
    }
  });
});
```

---

## Web Workers (Offload CPU-heavy tasks)

```javascript
// Main thread stays responsive
const worker = new Worker('compute.js');
worker.postMessage(data);
worker.onmessage = (e) => updateUI(e.data);
```

---

## Performance APIs

```javascript
// Measure execution time
performance.mark('start');
expensiveOperation();
performance.mark('end');
performance.measure('operation', 'start', 'end');
const duration = performance.getEntriesByName('operation')[0].duration;

// requestAnimationFrame (60fps animations)
function animate() {
  updatePosition();
  requestAnimationFrame(animate);
}
requestAnimationFrame(animate);

// requestIdleCallback (run during browser idle time)
requestIdleCallback((deadline) => {
  while (deadline.timeRemaining() > 0) {
    doLowPriorityWork();
  }
});
```

---

## Code Splitting & Tree Shaking

```javascript
// Code splitting with dynamic imports
const module = await import(`./routes/${routeName}.js`);

// Tree shaking: Use named imports (bundlers remove unused exports)
import { specificFunction } from 'library';  // ✅ Tree-shakeable
import library from 'library';               // ❌ Imports everything
```

---

## Key Interview Questions

**Q: When would you use debounce vs throttle?**
> Debounce: When you want to wait until activity stops (search autocomplete, window resize end, form validation after typing). Throttle: When you want to limit execution rate during continuous activity (scroll events, mouse movement, API polling).

**Q: How does memoization improve performance?**
> By caching the result of expensive computations based on their inputs. Subsequent calls with the same arguments return the cached result instantly instead of recomputing. Trade-off: uses more memory.

**Q: What is the difference between `requestAnimationFrame` and `setTimeout`?**
> `requestAnimationFrame` syncs with the browser's repaint cycle (60fps), more efficient for animations. `setTimeout` is not frame-aware and can cause jank. rAF is paused when tab is inactive.
