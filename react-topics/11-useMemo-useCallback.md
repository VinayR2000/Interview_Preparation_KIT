# useMemo and useCallback

## Memoization Concept

- Caching the result of an expensive computation
- Return cached result when inputs haven't changed
- Trade memory for speed

---

## useMemo - Memoize Values

```jsx
// Only recomputes when dependencies change
const memoizedValue = useMemo(() => {
  return expensiveComputation(a, b);
}, [a, b]);
```

### Example: Expensive Filtering
```jsx
function ProductList({ products, filter }) {
  // ✅ Only refilters when products or filter change
  const filteredProducts = useMemo(() => {
    console.log('Filtering...');
    return products.filter(p => p.category === filter);
  }, [products, filter]);

  // ✅ Expensive sort
  const sortedProducts = useMemo(() => {
    return [...filteredProducts].sort((a, b) => a.price - b.price);
  }, [filteredProducts]);

  return <List items={sortedProducts} />;
}
```

---

## useCallback - Memoize Functions

```jsx
// Returns same function reference if dependencies haven't changed
const memoizedFn = useCallback((args) => {
  doSomething(args);
}, [dependencies]);
```

### Why? Referential Equality
```jsx
// Without useCallback: new function reference every render
function Parent() {
  // ❌ handleDelete is recreated every render
  const handleDelete = (id) => setItems(prev => prev.filter(i => i.id !== id));
  
  // Child re-renders even though logic is the same
  return <Child onDelete={handleDelete} />;
}

// With useCallback: stable function reference
function Parent() {
  // ✅ Same reference across renders
  const handleDelete = useCallback((id) => {
    setItems(prev => prev.filter(i => i.id !== id));
  }, []);  // No dependencies - uses functional update
  
  return <Child onDelete={handleDelete} />;
}
```

---

## React.memo - Memoize Components

```jsx
// Only re-renders when props change (shallow comparison)
const ExpensiveList = React.memo(function ExpensiveList({ items, onSelect }) {
  console.log('ExpensiveList rendered');
  return (
    <ul>
      {items.map(item => (
        <li key={item.id} onClick={() => onSelect(item.id)}>
          {item.name}
        </li>
      ))}
    </ul>
  );
});

// Custom comparison function
const MemoizedComponent = React.memo(MyComponent, (prevProps, nextProps) => {
  // Return true if props are equal (skip re-render)
  // Return false if props changed (re-render)
  return prevProps.id === nextProps.id;
});
```

---

## Referential Equality Problem

```jsx
function App() {
  const [count, setCount] = useState(0);
  const [name, setName] = useState('John');

  // ❌ New object/function every render - breaks React.memo
  const options = { sortBy: 'name', order: 'asc' };
  const handleClick = () => console.log('clicked');

  return (
    <div>
      <button onClick={() => setCount(count + 1)}>Count: {count}</button>
      {/* ExpensiveChild re-renders on EVERY count change because 
          options and handleClick are new references! */}
      <ExpensiveChild options={options} onClick={handleClick} />
    </div>
  );
}

// ✅ Fix with useMemo and useCallback
function App() {
  const [count, setCount] = useState(0);
  const [name, setName] = useState('John');

  const options = useMemo(() => ({ sortBy: 'name', order: 'asc' }), []);
  const handleClick = useCallback(() => console.log('clicked'), []);

  return (
    <div>
      <button onClick={() => setCount(count + 1)}>Count: {count}</button>
      {/* Now ExpensiveChild won't re-render on count changes */}
      <ExpensiveChild options={options} onClick={handleClick} />
    </div>
  );
}

const ExpensiveChild = React.memo(function ExpensiveChild({ options, onClick }) {
  // Expensive render...
  return <div>...</div>;
});
```

---

## When to Use

### ✅ Use useMemo When
- Expensive computations (sorting large arrays, complex calculations)
- Creating objects/arrays passed to memoized children
- Avoiding expensive re-renders in child components
- As dependency for other hooks (stable reference)

### ✅ Use useCallback When
- Passing callbacks to memoized children (React.memo)
- Function is a dependency of another hook (useEffect)
- Preventing unnecessary effect re-runs

### ❌ When NOT to Use
```jsx
// ❌ Simple operations - memoization overhead > savings
const fullName = useMemo(() => `${first} ${last}`, [first, last]);
// ✅ Just compute it
const fullName = `${first} ${last}`;

// ❌ No memoized child - useless useCallback
const handleClick = useCallback(() => setOpen(true), []);
<button onClick={handleClick}>Open</button>  // <button> is never memoized

// ❌ Component doesn't use React.memo - useMemo for props is useless
const data = useMemo(() => processData(raw), [raw]);
<NormalChild data={data} />  // NormalChild re-renders with parent anyway!
```

---

## Performance Trade-offs

### Cost of Memoization
1. Memory: Stores previous result
2. Comparison: Checks dependencies every render
3. Complexity: More code to maintain

### Rule of Thumb
- Don't optimize prematurely
- Profile first (React DevTools Profiler)
- Memoize when you measure actual performance problems
- React.memo + useCallback/useMemo work together (all three needed)

---

## Complete Pattern: React.memo + useCallback + useMemo

```jsx
function TodoApp() {
  const [todos, setTodos] = useState([]);
  const [filter, setFilter] = useState('all');
  const [inputValue, setInputValue] = useState('');

  // Memoized computed value
  const filteredTodos = useMemo(() => {
    return todos.filter(todo => {
      if (filter === 'active') return !todo.completed;
      if (filter === 'completed') return todo.completed;
      return true;
    });
  }, [todos, filter]);

  // Memoized callbacks
  const handleToggle = useCallback((id) => {
    setTodos(prev => prev.map(t => 
      t.id === id ? { ...t, completed: !t.completed } : t
    ));
  }, []);

  const handleDelete = useCallback((id) => {
    setTodos(prev => prev.filter(t => t.id !== id));
  }, []);

  return (
    <div>
      {/* Input changes don't re-render TodoList */}
      <input value={inputValue} onChange={e => setInputValue(e.target.value)} />
      <TodoList 
        todos={filteredTodos} 
        onToggle={handleToggle} 
        onDelete={handleDelete} 
      />
    </div>
  );
}

// Memoized child - only re-renders when its props change
const TodoList = React.memo(function TodoList({ todos, onToggle, onDelete }) {
  console.log('TodoList rendered');
  return (
    <ul>
      {todos.map(todo => (
        <TodoItem 
          key={todo.id} 
          todo={todo} 
          onToggle={onToggle} 
          onDelete={onDelete} 
        />
      ))}
    </ul>
  );
});
```

---

## Key Interview Questions

**Q: What's the difference between useMemo and useCallback?**
> `useMemo(() => value, deps)` memoizes the **return value**. `useCallback(fn, deps)` memoizes the **function itself**. `useCallback(fn, deps)` is equivalent to `useMemo(() => fn, deps)`.

**Q: Does React.memo work without useCallback?**
> Partially. React.memo prevents re-renders when props don't change. But if you pass inline functions as props, they're new references every render, so React.memo sees "changed" props and re-renders anyway.

**Q: When should you NOT use memoization?**
> When the computation is cheap, when the component always re-renders anyway, when props change every render, or when there's no measurable performance issue. Premature memoization adds complexity without benefit.

**Q: How does React.memo compare props?**
> Shallow comparison by default (Object.is for each prop). For deep comparison, provide a custom comparison function as second argument.

**Q: What happens if you put the wrong dependencies?**
> Missing deps: stale values (bug). Extra deps: unnecessary recalculations. Wrong deps: either stale data or infinite recomputation.
