# Browser APIs

## Web Workers

```javascript
// main.js - Offload heavy computation
const worker = new Worker('worker.js');
worker.postMessage({ data: largeArray });
worker.onmessage = (e) => console.log('Result:', e.data);
worker.terminate();

// worker.js - Runs in separate thread
self.onmessage = (e) => {
  const result = heavyComputation(e.data);
  self.postMessage(result);
};
```

### Worker Limitations
- No DOM access
- No window object
- Communicate via postMessage only
- Separate memory space

---

## Service Workers

```javascript
// Register
navigator.serviceWorker.register('/sw.js');

// sw.js - Intercept network requests
self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request).then(cached => cached || fetch(event.request))
  );
});
```

### Use Cases
- Offline support (cache-first strategy)
- Push notifications
- Background sync

---

## Fetch API

```javascript
// GET
const response = await fetch('/api/users');
const data = await response.json();

// POST
await fetch('/api/users', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'John' }),
});

// With AbortController
const controller = new AbortController();
fetch('/api/data', { signal: controller.signal });
controller.abort();  // Cancel request

// Error handling
const response = await fetch(url);
if (!response.ok) throw new Error(`HTTP ${response.status}`);
```

---

## History API

```javascript
history.pushState({ page: 2 }, '', '/page/2');  // Add to history
history.replaceState({}, '', '/new-url');        // Replace current
history.back();   // Go back
history.forward(); // Go forward
window.onpopstate = (e) => console.log(e.state); // Listen to navigation
```

---

## Notifications API

```javascript
// Request permission
const permission = await Notification.requestPermission();

if (permission === 'granted') {
  new Notification('Hello!', {
    body: 'This is a notification',
    icon: '/icon.png',
  });
}
```

---

## Clipboard API

```javascript
// Copy
await navigator.clipboard.writeText('Copied text');

// Paste
const text = await navigator.clipboard.readText();
```

---

## IntersectionObserver (Lazy Loading)

```javascript
const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.src = entry.target.dataset.src;
      observer.unobserve(entry.target);
    }
  });
}, { threshold: 0.1 });

document.querySelectorAll('img[data-src]').forEach(img => observer.observe(img));
```

---

## Key Interview Questions

**Q: What's the difference between Web Workers and Service Workers?**
> Web Workers: General-purpose background threads for heavy computation. Service Workers: Specialized proxy between browser and network — intercept requests, enable offline, push notifications. Service Workers persist across page loads.

**Q: How does the Fetch API differ from XMLHttpRequest?**
> Fetch: Promise-based, cleaner API, no callback hell, streaming support, AbortController for cancellation. XHR: Callback-based, older API, has progress events (Fetch doesn't natively), widely supported.

**Q: What is the same-origin policy?**
> Browser security: Scripts from one origin (protocol + domain + port) can't access resources from a different origin. CORS headers on the server explicitly allow cross-origin access.
