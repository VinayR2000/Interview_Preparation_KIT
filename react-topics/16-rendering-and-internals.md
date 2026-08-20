# Rendering & React Internals

## Virtual DOM

### What is Virtual DOM?
- Lightweight JavaScript object representation of the real DOM
- React maintains a virtual copy of the UI in memory
- Changes are first applied to Virtual DOM, then diffed, then applied to real DOM

### Why Virtual DOM?
- Real DOM operations are expensive (layout, repaint)
- Virtual DOM allows batch updates and minimal real DOM changes
- Declarative: describe state, React figures out the minimum DOM operations

---

## Reconciliation

### Process
```
1. State/props change → Component re-renders → New Virtual DOM tree
2. React diffs new tree vs previous tree (Diffing Algorithm)
3. Calculates minimum changes needed
4. Applies changes to real DOM (Commit Phase)
```

### Diffing Algorithm Rules
1. **Different element types**: Tear down old tree, build new one
   - `<div>` → `<span>`: Unmount div and all children, mount span
2. **Same element type**: Update attributes only
   - `<div className="a">` → `<div className="b">`: Update class
3. **Same component type**: Keep instance, update props
4. **Lists**: Use `key` prop to match elements efficiently

---

## Fiber Architecture

### What is Fiber?
- React's internal reconciliation engine (React 16+)
- Each component/element is represented as a "fiber" node
- Enables **incremental rendering** (break work into chunks)
- Makes rendering interruptible and prioritizable

### Fiber Node Structure
```
{
  type: 'div' | MyComponent,
  key: null,
  props: { children: [...] },
  stateNode: DOM element or component instance,
  child: first child fiber,
  sibling: next sibling fiber,
  return: parent fiber,
  effectTag: 'PLACEMENT' | 'UPDATE' | 'DELETION',
}
```

### Two Phases

#### Render Phase (Can be interrupted)
- Build fiber tree
- Determine what changed (diffing)
- Calculate effects
- No visible changes to user
- Can be paused, resumed, or restarted

#### Commit Phase (Cannot be interrupted)
- Apply all DOM changes synchronously
- Call lifecycle methods (useLayoutEffect, componentDidMount)
- Must complete in one go (user sees consistent state)

---

## Re-renders

### What Triggers Re-render?
1. **State change** (`useState` setter called)
2. **Props change** (parent passes different props)
3. **Context change** (context value updates)
4. **Parent re-renders** (child re-renders by default)
5. **Force update** (forceUpdate in class components)

### Re-render ≠ DOM Update
```
Re-render: Component function called, new Virtual DOM created
DOM Update: Only if diffing finds differences in Virtual DOM
```

### Preventing Unnecessary Re-renders
```jsx
// React.memo - skip re-render if props unchanged
const Child = React.memo(function Child({ value }) {
  return <div>{value}</div>;
});

// useMemo - stable reference for objects
const options = useMemo(() => ({ sort: 'asc' }), []);

// useCallback - stable reference for functions
const handleClick = useCallback(() => {}, []);
```

---

## Mount, Update, Unmount

### Mount (Component added to DOM)
```jsx
useEffect(() => {
  console.log('Mounted');  // Runs once on mount
  return () => console.log('Unmounted');  // Cleanup on unmount
}, []);
```

### Update (Re-render with new state/props)
```jsx
useEffect(() => {
  console.log('Count updated to:', count);
}, [count]);  // Runs when count changes
```

### Unmount (Component removed from DOM)
- Cleanup functions run
- State is destroyed
- Event listeners removed
- Subscriptions cancelled

---

## Batching

### What is Batching?
- React groups multiple state updates into a single re-render
- Reduces unnecessary renders

```jsx
// React 18: Automatic batching everywhere
function handleClick() {
  setCount(c => c + 1);   // Does NOT re-render yet
  setFlag(f => !f);       // Does NOT re-render yet
  setText('hello');       // Does NOT re-render yet
  // React batches all three → ONE re-render
}

// Also batched in React 18 (wasn't in React 17):
setTimeout(() => {
  setCount(c => c + 1);
  setFlag(f => !f);
  // ONE re-render (in React 17, this was TWO re-renders)
}, 1000);

// Opt out of batching (rare):
import { flushSync } from 'react-dom';
flushSync(() => setCount(c => c + 1));  // Renders immediately
flushSync(() => setFlag(f => !f));      // Renders immediately
```

---

## Keys and Reconciliation

### Why Keys Matter
```jsx
// Without key: React updates all items (inefficient)
// With key: React knows which items moved/added/removed

// ❌ No key - React re-renders all list items
{items.map(item => <li>{item.name}</li>)}

// ✅ With key - React can identify and move items
{items.map(item => <li key={item.id}>{item.name}</li>)}
```

### Key Behavior
```jsx
// Same key = same component instance (preserves state)
// Different key = new component instance (destroys and recreates)

// Use key to RESET component state:
<UserForm key={userId} userId={userId} />
// When userId changes, entire form remounts (fresh state)
```

---

## State Update Scheduling

### Updates are Asynchronous
```jsx
const [count, setCount] = useState(0);

function handleClick() {
  setCount(count + 1);
  console.log(count);  // Still 0! (stale closure)
  // New value available on next render
}
```

### Priority Levels (React 18 Concurrent)
| Priority | Example | Can be Interrupted? |
|----------|---------|-------------------|
| Immediate | User input, clicks | No |
| User-blocking | Hover, typing | No |
| Normal | Data fetching results | Yes |
| Low | Analytics, logging | Yes |
| Idle | Pre-rendering offscreen | Yes |

---

## Key Interview Questions

**Q: How does React's diffing algorithm work?**
> Two assumptions: 1) Different types produce different trees (full rebuild). 2) Keys identify stable elements in lists. This makes O(n³) problem into O(n) by comparing nodes level by level.

**Q: What's the difference between render phase and commit phase?**
> Render phase: Pure computation - React calls component functions, builds fiber tree, calculates what changed. Can be paused/restarted. Commit phase: Actually applies DOM changes synchronously - can't be interrupted, user sees the update.

**Q: Why shouldn't you use index as key?**
> If items are reordered, inserted, or deleted, index-based keys make React think items just changed content (not position). This causes wrong state association, incorrect animations, and input value bugs.

**Q: Does a state update always cause a DOM update?**
> No. State update triggers a re-render (Virtual DOM recalculation). If diffing finds no differences, React skips the DOM update entirely.

**Q: What is concurrent rendering in React 18?**
> React can prepare multiple versions of UI simultaneously, pause and resume rendering, and prioritize urgent updates (user input) over non-urgent ones (data loading). Enabled via `useTransition` and `useDeferredValue`.
