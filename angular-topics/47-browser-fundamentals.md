# 47. Browser Fundamentals

---

## Theory

Understanding browser internals is essential since Angular runs in the browser. Key concepts include the DOM, event loop, rendering pipeline, and storage APIs.

### DOM (Document Object Model)

```
HTML Document → DOM Tree (live, in-memory representation)

Angular doesn't use Virtual DOM (unlike React).
Angular uses change detection + direct DOM updates via Renderer2.
Zone.js triggers re-evaluation of bindings → DOM updated in place.
```

### Browser Rendering Pipeline

```
HTML/CSS/JS received
    ↓
1. Parse HTML → DOM Tree
2. Parse CSS → CSSOM
3. DOM + CSSOM → Render Tree
4. Layout (calculate positions/sizes)
5. Paint (draw pixels)
6. Composite (layer assembly)

Angular change detection triggers steps 4-6 for modified elements.
Minimize layout thrashing (reading then writing DOM repeatedly).
```

### JavaScript Event Loop

```
┌─────────────────────────────────────────────┐
│ Call Stack (synchronous execution)           │
│ main() → functionA() → functionB()          │
└─────────────────────┬───────────────────────┘
                      │ When empty, check:
                      ↓
┌─────────────────────────────────────────────┐
│ Microtask Queue (Promises, queueMicrotask)   │
│ Priority: ALL microtasks before next task    │
└─────────────────────┬───────────────────────┘
                      │ When empty:
                      ↓
┌─────────────────────────────────────────────┐
│ Macrotask Queue (setTimeout, setInterval,    │
│ events, HTTP callbacks)                      │
│ Process ONE task, then check microtasks      │
└─────────────────────────────────────────────┘

Zone.js patches: setTimeout, Promise, addEventListener, XHR
→ Knows when async operations complete
→ Triggers Angular change detection
```

### Event Bubbling and Capturing

```
Capturing (top-down):   document → body → div → button
Bubbling (bottom-up):   button → div → body → document

Angular (event) binding uses bubbling by default.
event.stopPropagation() stops bubbling.
event.preventDefault() prevents default behavior.
```

### Browser Storage

| Storage | Capacity | Lifetime | Accessible |
|---------|----------|----------|-----------|
| localStorage | 5-10MB | Permanent | Same origin |
| sessionStorage | 5-10MB | Tab session | Same origin, same tab |
| Cookies | 4KB | Configurable | Sent with HTTP requests |
| IndexedDB | Large (GB) | Permanent | Same origin |

```typescript
// In Angular — use service abstraction
@Injectable({ providedIn: 'root' })
export class StorageService {
  set(key: string, value: any): void {
    localStorage.setItem(key, JSON.stringify(value));
  }
  get<T>(key: string): T | null {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : null;
  }
  remove(key: string): void {
    localStorage.removeItem(key);
  }
}
```

### HTTP/HTTPS

```
HTTP:  Unencrypted, port 80
HTTPS: TLS encrypted, port 443 (required for production)

Angular HttpClient works with both.
Modern browsers block mixed content (HTTPS page loading HTTP resources).
Service Workers require HTTPS.
```

### WebSockets

```typescript
// Real-time communication (full-duplex)
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private ws: WebSocket | null = null;
  private messages$ = new Subject<any>();

  connect(url: string): Observable<any> {
    this.ws = new WebSocket(url);
    this.ws.onmessage = (event) => this.messages$.next(JSON.parse(event.data));
    this.ws.onerror = (error) => this.messages$.error(error);
    this.ws.onclose = () => this.messages$.complete();
    return this.messages$.asObservable();
  }

  send(data: any): void {
    this.ws?.send(JSON.stringify(data));
  }

  disconnect(): void {
    this.ws?.close();
  }
}
```

### Browser Caching

```
Cache-Control headers determine caching:
  - max-age=31536000: Cache for 1 year (hashed Angular assets)
  - no-cache: Always revalidate (index.html)
  - no-store: Never cache (sensitive data)

Angular production build: files have content hashes (main.abc123.js)
  → Safe to cache forever (new build = new hash = new file)
  → index.html must NOT be cached (references current hashes)
```

---

## Interview Questions and Answers

**Q1: How does the JavaScript event loop work?**
> The call stack executes synchronous code. When empty, it processes all microtasks (Promises, queueMicrotask), then one macrotask (setTimeout, events, HTTP), then microtasks again. Angular/Zone.js hooks into this to know when async work completes and trigger change detection after each task.

**Q2: What is the difference between localStorage and sessionStorage?**
> localStorage persists until explicitly cleared (survives browser restart). sessionStorage is cleared when the tab closes. Both are same-origin, synchronous, ~5-10MB. Use localStorage for auth tokens (persist login), sessionStorage for temporary tab state (form drafts, navigation history within session).

**Q3: What is event bubbling and how does Angular use it?**
> Bubbling: events propagate from target element up through ancestors (button → div → body → document). Angular's (event) binding listens during bubbling phase. `$event.stopPropagation()` stops bubbling. Useful: click event on parent catches clicks from all children.

**Q4: How do WebSockets differ from HTTP in Angular?**
> HTTP: request-response (client initiates), stateless, one response per request. WebSocket: persistent bidirectional connection, server can push data anytime. Angular wraps WebSocket in Observable for reactive handling. Use for: real-time chat, live dashboards, notifications, collaborative editing.

---

## Related Topics

- → [17. RxJS](./17-rxjs.md)
- → [24. Change Detection](./24-change-detection.md)
- → [40. Security](./40-security.md)
- → [48. Frontend Performance](./48-frontend-performance.md)
