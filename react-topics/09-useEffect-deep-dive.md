# useEffect Deep Dive

## What is useEffect?

- Hook for performing side effects in functional components
- Side effects = anything that affects something outside the component's render
- Replaces `componentDidMount`, `componentDidUpdate`, `componentWillUnmount`

```jsx
useEffect(() => {
  // Effect logic (runs after render)
  
  return () => {
    // Cleanup (optional - runs before next effect or unmount)
  };
}, [dependencies]);  // When to re-run
```

---

## Side Effects (What belongs in useEffect)

- API calls / data fetching
- Subscriptions (WebSocket, event listeners)
- Timers (setTimeout, setInterval)
- DOM manipulation (document.title, scrolling)
- Logging / analytics
- Local storage operations

---

## Dependency Array

### Empty Array `[]` - Run Once on Mount
```jsx
useEffect(() => {
  fetchUserData();  // Runs only once after first render
}, []);
```

### No Array - Run Every Render
```jsx
useEffect(() => {
  console.log('Runs after EVERY render');  // Usually a bug!
});
```

### Specific Dependencies - Run When Dependencies Change
```jsx
useEffect(() => {
  fetchUser(userId);  // Runs when userId changes
}, [userId]);

useEffect(() => {
  const filtered = items.filter(i => i.category === category);
  setResults(filtered);
}, [items, category]);  // Runs when items OR category change
```

### Dependency Rules
- Include ALL values from component scope used inside effect
- React compares dependencies with `Object.is` (reference equality)
- Objects/arrays/functions create new references each render → careful!

```jsx
// ❌ Missing dependency
useEffect(() => {
  fetchData(userId);  // userId used but not in deps
}, []);  // Lint warning!

// ✅ Include all dependencies
useEffect(() => {
  fetchData(userId);
}, [userId]);
```

---

## Cleanup Function

Cleanup runs:
- Before the effect re-runs (when dependencies change)
- When the component unmounts

```jsx
// Event listener cleanup
useEffect(() => {
  const handleResize = () => setWidth(window.innerWidth);
  window.addEventListener('resize', handleResize);
  
  return () => {
    window.removeEventListener('resize', handleResize);  // Cleanup!
  };
}, []);

// Timer cleanup
useEffect(() => {
  const interval = setInterval(() => {
    setCount(prev => prev + 1);
  }, 1000);
  
  return () => clearInterval(interval);  // Cleanup!
}, []);

// Subscription cleanup
useEffect(() => {
  const subscription = dataSource.subscribe(handleData);
  return () => subscription.unsubscribe();  // Cleanup!
}, []);
```

---

## API Calls

```jsx
function UserProfile({ userId }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isCancelled = false;  // Prevent setting state on unmounted component
    
    setLoading(true);
    setError(null);
    
    fetch(`/api/users/${userId}`)
      .then(res => {
        if (!res.ok) throw new Error('Failed to fetch');
        return res.json();
      })
      .then(data => {
        if (!isCancelled) {
          setUser(data);
          setLoading(false);
        }
      })
      .catch(err => {
        if (!isCancelled) {
          setError(err.message);
          setLoading(false);
        }
      });

    return () => { isCancelled = true; };  // Cleanup on unmount or re-run
  }, [userId]);

  if (loading) return <Spinner />;
  if (error) return <Error message={error} />;
  return <UserCard user={user} />;
}
```

---

## Race Conditions

When multiple requests are in-flight, old responses may arrive after newer ones.

```jsx
// ❌ Race condition
useEffect(() => {
  fetch(`/api/search?q=${query}`)
    .then(res => res.json())
    .then(data => setResults(data));  // Old query might overwrite new results!
}, [query]);

// ✅ Fix with AbortController
useEffect(() => {
  const controller = new AbortController();
  
  fetch(`/api/search?q=${query}`, { signal: controller.signal })
    .then(res => res.json())
    .then(data => setResults(data))
    .catch(err => {
      if (err.name !== 'AbortError') setError(err.message);
    });

  return () => controller.abort();  // Cancel previous request
}, [query]);

// ✅ Fix with boolean flag
useEffect(() => {
  let ignore = false;
  
  fetchResults(query).then(data => {
    if (!ignore) setResults(data);
  });

  return () => { ignore = true; };
}, [query]);
```

---

## Common Pitfalls

### Infinite Loops
```jsx
// ❌ Object in dependency - new reference every render!
useEffect(() => {
  fetchData(options);
}, [options]);  // If options = {} inside render, infinite loop!

// ❌ Setting state that's also a dependency
useEffect(() => {
  setCount(count + 1);  // count changes → re-run → count changes → ...
}, [count]);

// ❌ No dependency array
useEffect(() => {
  setState(something);  // Runs every render, setState triggers render → loop
});
```

### Stale Closures
```jsx
// ❌ Stale closure - count is captured at effect creation time
useEffect(() => {
  const interval = setInterval(() => {
    setCount(count + 1);  // Always adds 1 to the SAME captured count value!
  }, 1000);
  return () => clearInterval(interval);
}, []);  // Empty deps - closure captures initial count (0)

// ✅ Fix with functional update
useEffect(() => {
  const interval = setInterval(() => {
    setCount(prev => prev + 1);  // Always gets latest value
  }, 1000);
  return () => clearInterval(interval);
}, []);
```

---

## Effect vs Event Handler

| Use Effect | Use Event Handler |
|-----------|-------------------|
| Runs after render automatically | Runs when user does something |
| Sync with external system | Respond to user action |
| API calls on mount/dependency change | API calls on button click |
| Start subscription | Toggle UI state |
| Set document title | Navigate |

```jsx
// Effect: Sync document title with state
useEffect(() => {
  document.title = `${count} items`;
}, [count]);

// Event handler: Action in response to user
const handleSubmit = async () => {
  await saveData(formData);  // Don't need useEffect for this!
};
```

---

## When NOT to Use useEffect

```jsx
// ❌ Transforming data for render - just calculate it!
useEffect(() => {
  setFilteredItems(items.filter(i => i.active));
}, [items]);

// ✅ Derive during render
const filteredItems = items.filter(i => i.active);

// ❌ Resetting state when prop changes
useEffect(() => {
  setSelectedItem(null);
}, [items]);

// ✅ Use key to reset component entirely
<ItemList key={categoryId} items={items} />

// ❌ Sending analytics on button click
useEffect(() => {
  if (submitted) trackEvent('form_submit');
}, [submitted]);

// ✅ Put it in the event handler
const handleSubmit = () => {
  submitForm();
  trackEvent('form_submit');
};
```

---

## Key Interview Questions

**Q: What's the difference between `useEffect(() => {}, [])` and no dependency array?**
> Empty array `[]` = run once on mount. No array = run after every render. No array is usually a bug (causes performance issues).

**Q: Why does the cleanup function run before re-running the effect?**
> To prevent stale subscriptions/listeners. If userId changes from 1 to 2, cleanup unsubscribes from user 1 before subscribing to user 2. Without cleanup, you'd have leaked subscriptions.

**Q: How do you handle async in useEffect?**
> You can't make the effect function itself async (it must return undefined or a cleanup function). Define an async function inside and call it: `useEffect(() => { async function fetchData() {...}; fetchData(); }, []);`

**Q: What causes an infinite loop in useEffect?**
> Setting state that triggers a re-render, which re-runs the effect (if it's in dependencies or no dep array). Also: object/array dependencies that create new references each render.

**Q: useEffect vs useLayoutEffect?**
> useEffect runs asynchronously after browser paint (non-blocking). useLayoutEffect runs synchronously after DOM update but before paint. Use useLayoutEffect only when you need to measure/mutate DOM before the user sees it (prevents flicker).
