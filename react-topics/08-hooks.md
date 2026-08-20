# Hooks

## Rules of Hooks

1. **Only call at the top level** - Never inside loops, conditions, or nested functions
2. **Only call in React functions** - In functional components or custom hooks
3. **Call in the same order every render** - React tracks hooks by call order

```jsx
// ❌ WRONG
function App() {
  if (condition) {
    const [name, setName] = useState('');  // Conditional hook!
  }
  for (let i = 0; i < 5; i++) {
    useEffect(() => {});  // Hook in loop!
  }
}

// ✅ CORRECT
function App() {
  const [name, setName] = useState('');
  const [age, setAge] = useState(0);
  
  useEffect(() => {
    if (condition) {  // Condition INSIDE the hook
      // do something
    }
  }, [condition]);
}
```

---

## useState
```jsx
const [state, setState] = useState(initialValue);
const [count, setCount] = useState(0);
const [user, setUser] = useState(null);
const [items, setItems] = useState(() => expensiveComputation());
```

---

## useEffect
```jsx
useEffect(() => {
  // Side effect logic
  return () => {
    // Cleanup (optional)
  };
}, [dependencies]);
```

---

## useContext
```jsx
const ThemeContext = createContext('light');

function App() {
  return (
    <ThemeContext.Provider value="dark">
      <Child />
    </ThemeContext.Provider>
  );
}

function Child() {
  const theme = useContext(ThemeContext);  // 'dark'
  return <div className={theme}>Themed content</div>;
}
```

---

## useRef
```jsx
// DOM access
const inputRef = useRef(null);
<input ref={inputRef} />
inputRef.current.focus();

// Mutable value (doesn't cause re-render)
const renderCount = useRef(0);
renderCount.current++;  // No re-render!
```

---

## useReducer
```jsx
const initialState = { count: 0 };

function reducer(state, action) {
  switch (action.type) {
    case 'increment': return { count: state.count + 1 };
    case 'decrement': return { count: state.count - 1 };
    case 'reset': return initialState;
    default: throw new Error(`Unknown action: ${action.type}`);
  }
}

function Counter() {
  const [state, dispatch] = useReducer(reducer, initialState);
  
  return (
    <div>
      Count: {state.count}
      <button onClick={() => dispatch({ type: 'increment' })}>+</button>
      <button onClick={() => dispatch({ type: 'decrement' })}>-</button>
      <button onClick={() => dispatch({ type: 'reset' })}>Reset</button>
    </div>
  );
}
```

---

## useMemo
```jsx
// Memoize expensive computation
const sortedList = useMemo(() => {
  return items.sort((a, b) => a.name.localeCompare(b.name));
}, [items]);  // Only recompute when items change
```

---

## useCallback
```jsx
// Memoize function reference
const handleClick = useCallback((id) => {
  setItems(prev => prev.filter(item => item.id !== id));
}, []);  // Stable reference across renders
```

---

## useLayoutEffect
```jsx
// Runs synchronously AFTER DOM mutation, BEFORE browser paint
// Use for DOM measurements
useLayoutEffect(() => {
  const { height } = elementRef.current.getBoundingClientRect();
  setElementHeight(height);
}, []);
```

### useEffect vs useLayoutEffect
| useEffect | useLayoutEffect |
|-----------|-----------------|
| Runs after paint (async) | Runs before paint (sync) |
| Non-blocking | Blocks visual update |
| Most side effects | DOM measurements, preventing flicker |

---

## useImperativeHandle
```jsx
// Customize what's exposed when parent uses ref
const FancyInput = forwardRef((props, ref) => {
  const inputRef = useRef();

  useImperativeHandle(ref, () => ({
    focus: () => inputRef.current.focus(),
    clear: () => { inputRef.current.value = ''; },
  }));

  return <input ref={inputRef} />;
});

// Parent
const ref = useRef();
<FancyInput ref={ref} />
ref.current.focus();  // Only exposed methods available
```

---

## useId
```jsx
// Generate unique IDs for accessibility
function FormField({ label }) {
  const id = useId();
  
  return (
    <div>
      <label htmlFor={id}>{label}</label>
      <input id={id} />
    </div>
  );
}
```

---

## useTransition
```jsx
// Mark state updates as non-urgent (won't block UI)
function Search() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [isPending, startTransition] = useTransition();

  const handleChange = (e) => {
    setQuery(e.target.value);  // Urgent: update input immediately
    
    startTransition(() => {
      setResults(filterLargeList(e.target.value));  // Non-urgent: can be interrupted
    });
  };

  return (
    <div>
      <input value={query} onChange={handleChange} />
      {isPending && <Spinner />}
      <ResultsList results={results} />
    </div>
  );
}
```

---

## useDeferredValue
```jsx
// Defer a value update (shows stale value while computing new one)
function SearchResults({ query }) {
  const deferredQuery = useDeferredValue(query);
  const isStale = query !== deferredQuery;
  
  const results = useMemo(() => filterLargeList(deferredQuery), [deferredQuery]);
  
  return (
    <div style={{ opacity: isStale ? 0.5 : 1 }}>
      <ResultsList results={results} />
    </div>
  );
}
```

---

## Custom Hooks

```jsx
// Extract reusable logic into custom hooks
function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    const stored = localStorage.getItem(key);
    return stored ? JSON.parse(stored) : initialValue;
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  return [value, setValue];
}

// Usage
const [theme, setTheme] = useLocalStorage('theme', 'light');
```

---

## Hooks Summary Table

| Hook | Purpose | Triggers Re-render? |
|------|---------|-------------------|
| useState | Local state | Yes |
| useEffect | Side effects | No (causes them) |
| useContext | Access context | Yes (on context change) |
| useRef | Mutable ref / DOM | No |
| useReducer | Complex state logic | Yes |
| useMemo | Memoize value | No (returns cached) |
| useCallback | Memoize function | No (returns cached) |
| useLayoutEffect | Sync DOM effects | No |
| useId | Generate unique ID | No |
| useTransition | Non-urgent updates | Yes (isPending) |
| useDeferredValue | Defer value | Yes (deferred) |

---

## Key Interview Questions

**Q: Why can't hooks be called conditionally?**
> React identifies hooks by their call order (index). If you conditionally skip a hook, the order changes between renders, and React associates wrong state with wrong hooks.

**Q: What's the difference between useMemo and useCallback?**
> `useMemo` memoizes a computed value. `useCallback` memoizes a function reference. `useCallback(fn, deps)` is equivalent to `useMemo(() => fn, deps)`.

**Q: When should you use useReducer over useState?**
> When state logic is complex (multiple sub-values), when next state depends on previous, when actions are well-defined, or when you want to pass dispatch down instead of multiple callbacks.

**Q: What problem do hooks solve over class components?**
> Reusing stateful logic (custom hooks vs HOC/render props), avoiding `this` binding confusion, grouping related logic together (vs scattered across lifecycle methods), simpler code.
