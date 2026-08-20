# Performance Optimization

## Preventing Unnecessary Re-renders

### React.memo
```jsx
const ExpensiveList = React.memo(function ExpensiveList({ items }) {
  return items.map(item => <Item key={item.id} {...item} />);
});
// Only re-renders when `items` reference changes
```

### useMemo + useCallback (with React.memo)
```jsx
function Parent() {
  const [count, setCount] = useState(0);
  const sortedItems = useMemo(() => [...items].sort(), [items]);
  const handleClick = useCallback((id) => deleteItem(id), []);

  return <MemoizedChild items={sortedItems} onClick={handleClick} />;
}
```

### Component Splitting
```jsx
// ❌ Entire component re-renders on every keystroke
function Page() {
  const [query, setQuery] = useState('');
  return (
    <div>
      <input value={query} onChange={e => setQuery(e.target.value)} />
      <ExpensiveChart />  {/* Re-renders unnecessarily! */}
    </div>
  );
}

// ✅ Extract input into its own component
function SearchInput() {
  const [query, setQuery] = useState('');  // Isolated state
  return <input value={query} onChange={e => setQuery(e.target.value)} />;
}
function Page() {
  return (
    <div>
      <SearchInput />
      <ExpensiveChart />  {/* Doesn't re-render on input */}
    </div>
  );
}
```

---

## Code Splitting & Lazy Loading

```jsx
import { lazy, Suspense } from 'react';

// Lazy load components (loaded only when needed)
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Settings = lazy(() => import('./pages/Settings'));

function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <Routes>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/settings" element={<Settings />} />
      </Routes>
    </Suspense>
  );
}
```

---

## List Virtualization

Only render items visible in the viewport (not thousands of DOM nodes).

```jsx
import { FixedSizeList } from 'react-window';

function VirtualizedList({ items }) {
  const Row = ({ index, style }) => (
    <div style={style}>{items[index].name}</div>
  );

  return (
    <FixedSizeList
      height={600}
      width="100%"
      itemCount={items.length}
      itemSize={50}
    >
      {Row}
    </FixedSizeList>
  );
}
```

---

## Debouncing & Throttling

```jsx
// Debounce: Wait until user stops typing
function Search() {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    if (debouncedQuery) fetchResults(debouncedQuery);
  }, [debouncedQuery]);
}

// Throttle: Execute at most once per interval
const handleScroll = useCallback(
  throttle(() => { checkScrollPosition(); }, 100),
  []
);
```

---

## Bundle Optimization

### Tree Shaking
```jsx
// ✅ Named imports (tree-shakeable)
import { Button } from '@mui/material';

// ❌ Default import (may import entire library)
import Material from '@mui/material';
```

### Dynamic Imports
```jsx
// Load heavy libraries only when needed
const handleExport = async () => {
  const { exportToPDF } = await import('./utils/pdfExport');
  exportToPDF(data);
};
```

---

## React DevTools Profiler

1. Open React DevTools → Profiler tab
2. Click Record → Interact with app → Stop
3. Analyze:
   - Which components re-rendered
   - How long each render took
   - Why a component re-rendered

### Identifying Issues
- Components rendering without prop changes → Need React.memo
- Expensive computations every render → Need useMemo
- Functions recreated every render → Need useCallback

---

## Performance Checklist

| Technique | What It Solves |
|-----------|---------------|
| React.memo | Unnecessary re-renders from parent |
| useMemo | Expensive recalculations |
| useCallback | Unstable function references |
| Code splitting | Large initial bundle |
| Virtualization | Long lists (1000+ items) |
| Debounce/Throttle | Too many state updates |
| Image optimization | Slow page load |
| Tree shaking | Dead code in bundle |
| Suspense + lazy | Route-level code splitting |

---

## Key Interview Questions

**Q: How do you identify performance issues in React?**
> React DevTools Profiler to see render times and causes. Chrome DevTools Performance tab for paint/layout. Lighthouse for overall metrics. Look for: unnecessary re-renders, expensive computations, large bundles.

**Q: What's the most common cause of performance issues?**
> Unnecessary re-renders, especially in lists. A parent state change re-renders all children by default. Solutions: React.memo, component splitting, state colocation.

**Q: When should you NOT optimize?**
> Don't optimize prematurely. If there's no measurable problem, optimization adds complexity without benefit. Profile first, then optimize the specific bottleneck.

**Q: What's code splitting and when should you use it?**
> Breaking your bundle into smaller chunks loaded on demand. Use for: route-level splitting (lazy load pages), heavy libraries used in one place, modals/dialogs not shown on initial load.
