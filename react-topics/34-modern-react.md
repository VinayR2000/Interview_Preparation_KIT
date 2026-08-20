# Modern React (React 18+)

## Automatic Batching

React 18 batches state updates everywhere (not just event handlers).

```jsx
// React 17: Only batched in event handlers
setTimeout(() => {
  setCount(c => c + 1);  // Re-render
  setFlag(f => !f);      // Re-render (2 total)
}, 1000);

// React 18: Batched everywhere
setTimeout(() => {
  setCount(c => c + 1);  // ─┐
  setFlag(f => !f);      // ─┘ ONE re-render
}, 1000);

// Also batched in: promises, setTimeout, native event handlers
fetch('/api').then(() => {
  setData(result);       // ─┐
  setLoading(false);     // ─┘ ONE re-render
});
```

---

## Concurrent Rendering

React can prepare multiple versions of UI at the same time.

### Key Principles
- Rendering is **interruptible** (can pause for urgent updates)
- Urgent updates (typing, clicking) take priority
- Non-urgent updates (search results, transitions) can be deferred

---

## useTransition

Mark state updates as non-urgent (can be interrupted).

```jsx
function Search() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [isPending, startTransition] = useTransition();

  const handleChange = (e) => {
    const value = e.target.value;
    setQuery(value);  // Urgent: Update input immediately

    startTransition(() => {
      // Non-urgent: Can be interrupted by user typing
      setResults(filterLargeDataset(value));
    });
  };

  return (
    <div>
      <input value={query} onChange={handleChange} />
      {isPending && <p>Updating...</p>}
      <ResultsList results={results} style={{ opacity: isPending ? 0.7 : 1 }} />
    </div>
  );
}
```

---

## useDeferredValue

Defer a value to keep UI responsive.

```jsx
function SearchResults({ query }) {
  const deferredQuery = useDeferredValue(query);
  const isStale = query !== deferredQuery;

  // Expensive computation uses deferred (stale) value
  const results = useMemo(() => search(deferredQuery), [deferredQuery]);

  return (
    <div style={{ opacity: isStale ? 0.5 : 1 }}>
      <List items={results} />
    </div>
  );
}
```

### useTransition vs useDeferredValue
| useTransition | useDeferredValue |
|---------------|-----------------|
| Wraps state setter | Wraps a value |
| You control when to transition | React decides when to defer |
| Provides isPending | Compare old vs new for staleness |
| Use when you own the state update | Use when you receive a value (props) |

---

## Suspense

Declarative loading states.

```jsx
import { Suspense, lazy } from 'react';

// Code splitting
const Dashboard = lazy(() => import('./Dashboard'));

function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <Dashboard />
    </Suspense>
  );
}

// Nested suspense boundaries
<Suspense fallback={<PageSkeleton />}>
  <Header />
  <Suspense fallback={<ContentSpinner />}>
    <MainContent />
  </Suspense>
  <Suspense fallback={<SidebarSkeleton />}>
    <Sidebar />
  </Suspense>
</Suspense>
```

---

## Server Components (React 19 / Next.js)

### Server Components
- Run on the server only
- Can access databases, file system directly
- Zero client-side JavaScript
- Cannot have state or effects
- Default in Next.js App Router

```jsx
// Server Component (default in Next.js app/)
async function UserList() {
  const users = await db.query('SELECT * FROM users');  // Direct DB access!
  return <ul>{users.map(u => <li key={u.id}>{u.name}</li>)}</ul>;
}
```

### Client Components
```jsx
'use client';  // Opt-in to client component

import { useState } from 'react';

function Counter() {
  const [count, setCount] = useState(0);  // State = must be client
  return <button onClick={() => setCount(count + 1)}>{count}</button>;
}
```

### Server vs Client Components
| Server Component | Client Component |
|-----------------|------------------|
| No bundle cost | Adds to JS bundle |
| Direct data access | Needs API calls |
| No state/effects | Full interactivity |
| No event handlers | Event handlers |
| Async supported | No async component |
| Default in Next.js | Opt-in with 'use client' |

---

## Streaming SSR

React 18 can stream HTML to the browser progressively.

```
Traditional SSR: Wait for ALL data → Render ALL HTML → Send
Streaming SSR:   Send shell immediately → Stream parts as ready
```

- Users see content faster (no blank page while waiting)
- Suspense boundaries define streaming chunks
- Each chunk hydrates independently

---

## Key Interview Questions

**Q: What is Concurrent Rendering?**
> React can work on multiple state updates simultaneously, prioritizing urgent ones (user input) over non-urgent ones (data loading). Rendering is interruptible - React can pause a low-priority render to handle a high-priority update.

**Q: What changed in React 18?**
> Automatic batching everywhere, concurrent features (useTransition, useDeferredValue, Suspense for data), streaming SSR, new APIs (useId, useSyncExternalStore).

**Q: When should you use Server Components?**
> For data fetching, accessing backend resources, large dependencies that don't need interactivity. They reduce bundle size and improve performance. Use Client Components only when you need state, effects, or event handlers.

**Q: What's the difference between Suspense and loading state?**
> Loading state is imperative (you manage `isLoading` state). Suspense is declarative (component "suspends" and React shows fallback). Suspense enables streaming, parallel loading, and cleaner component code.
