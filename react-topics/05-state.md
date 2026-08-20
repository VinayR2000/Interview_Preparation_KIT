# State

## Local State with useState

```jsx
import { useState } from 'react';

function Counter() {
  const [count, setCount] = useState(0);  // [value, setter] = useState(initialValue)
  
  return (
    <div>
      <p>Count: {count}</p>
      <button onClick={() => setCount(count + 1)}>Increment</button>
    </div>
  );
}
```

---

## State Initialization

```jsx
// Simple initial value
const [name, setName] = useState('');
const [count, setCount] = useState(0);
const [isOpen, setIsOpen] = useState(false);
const [items, setItems] = useState([]);
const [user, setUser] = useState(null);

// Lazy initialization (expensive computation - runs only on first render)
const [data, setData] = useState(() => {
  return JSON.parse(localStorage.getItem('data')) || [];
});

// ❌ Don't do this - runs every render (result is just ignored after first)
const [data, setData] = useState(JSON.parse(localStorage.getItem('data')));
```

---

## State Updates

### Basic Updates
```jsx
setCount(5);           // Direct value
setName('John');       // Direct value
setIsOpen(true);       // Direct value
```

### Functional Updates (When new state depends on previous)
```jsx
// ❌ May not work correctly with batching
setCount(count + 1);
setCount(count + 1);  // Still only increments by 1!

// ✅ Functional update - always gets latest state
setCount(prev => prev + 1);
setCount(prev => prev + 1);  // Correctly increments by 2

// Why? React batches updates. Both read the same `count` value from closure.
// Functional form guarantees access to the latest state.
```

### When to Use Functional Updates
- When new state depends on previous state
- In event handlers with multiple updates
- In async callbacks (setTimeout, API responses)
- Inside loops

---

## Multiple State Variables

```jsx
// ✅ Prefer separate state for unrelated values
function UserForm() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [age, setAge] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  return (/* ... */);
}

// ✅ Group related state in an object (when they always change together)
function MouseTracker() {
  const [position, setPosition] = useState({ x: 0, y: 0 });
  
  const handleMove = (e) => {
    setPosition({ x: e.clientX, y: e.clientY });
  };
  
  return <div onMouseMove={handleMove}>x: {position.x}, y: {position.y}</div>;
}
```

---

## State Immutability

### Updating Objects
```jsx
const [user, setUser] = useState({ name: 'John', age: 25, city: 'NYC' });

// ❌ WRONG - Mutating state directly
user.name = 'Jane';
setUser(user);  // Same reference, React won't re-render!

// ✅ CORRECT - Create new object with spread
setUser({ ...user, name: 'Jane' });

// ✅ Nested objects - spread at each level
const [form, setForm] = useState({
  personal: { name: '', age: 0 },
  address: { street: '', city: '' }
});

setForm({
  ...form,
  personal: { ...form.personal, name: 'John' }
});
```

### Updating Arrays
```jsx
const [items, setItems] = useState(['A', 'B', 'C']);

// ✅ Add item
setItems([...items, 'D']);                    // Add to end
setItems(['Z', ...items]);                   // Add to start
setItems([...items.slice(0, 1), 'X', ...items.slice(1)]); // Insert at index

// ✅ Remove item
setItems(items.filter(item => item !== 'B'));
setItems(items.filter((_, index) => index !== 1));

// ✅ Update item
setItems(items.map(item => item === 'B' ? 'B2' : item));
setItems(items.map((item, i) => i === 1 ? 'B2' : item));

// ✅ Sort (spread first to avoid mutating)
setItems([...items].sort());

// ❌ NEVER use mutating methods directly
items.push('D');      // Mutates!
items.splice(1, 1);  // Mutates!
items.sort();         // Mutates!
items[0] = 'Z';      // Mutates!
```

### Array Cheat Sheet
| Operation | Immutable Method |
|-----------|-----------------|
| Add | `[...arr, item]` or `[item, ...arr]` |
| Remove | `arr.filter(...)` |
| Update | `arr.map(...)` |
| Sort | `[...arr].sort()` |
| Reverse | `[...arr].reverse()` |

---

## Derived State

```jsx
// ❌ Storing derived data as state (anti-pattern)
function TodoList({ todos }) {
  const [filteredTodos, setFilteredTodos] = useState(todos);
  const [filter, setFilter] = useState('all');
  
  useEffect(() => {
    setFilteredTodos(todos.filter(/* ... */));  // Unnecessary state!
  }, [todos, filter]);
}

// ✅ Calculate derived data during render
function TodoList({ todos }) {
  const [filter, setFilter] = useState('all');
  
  // Derive from existing state - no extra state needed
  const filteredTodos = todos.filter(todo => {
    if (filter === 'completed') return todo.done;
    if (filter === 'active') return !todo.done;
    return true;
  });
  
  const completedCount = todos.filter(t => t.done).length;  // Derived
  
  return (/* ... */);
}
```

---

## Lifting State Up

When multiple components need the same state, lift it to their closest common ancestor.

```jsx
// ❌ Duplicate state in siblings
function TemperatureInput() {
  const [temp, setTemp] = useState('');  // Each has own state
  return <input value={temp} onChange={e => setTemp(e.target.value)} />;
}

// ✅ Lift state to parent
function TemperatureCalculator() {
  const [celsius, setCelsius] = useState('');
  
  const fahrenheit = celsius ? (celsius * 9/5 + 32).toFixed(1) : '';
  
  return (
    <div>
      <TemperatureInput 
        label="Celsius" 
        value={celsius} 
        onChange={setCelsius} 
      />
      <TemperatureInput 
        label="Fahrenheit" 
        value={fahrenheit} 
        onChange={f => setCelsius(((f - 32) * 5/9).toFixed(1))} 
      />
    </div>
  );
}

function TemperatureInput({ label, value, onChange }) {
  return (
    <label>
      {label}: 
      <input value={value} onChange={e => onChange(e.target.value)} />
    </label>
  );
}
```

---

## State Colocation

Keep state as close as possible to where it's used.

```jsx
// ❌ State too high - causes unnecessary re-renders
function App() {
  const [searchTerm, setSearchTerm] = useState('');  // Only SearchBar needs this!
  return (
    <div>
      <SearchBar value={searchTerm} onChange={setSearchTerm} />
      <UserList />  {/* Re-renders unnecessarily */}
      <Footer />    {/* Re-renders unnecessarily */}
    </div>
  );
}

// ✅ State colocated - only SearchBar re-renders
function App() {
  return (
    <div>
      <SearchBar />
      <UserList />
      <Footer />
    </div>
  );
}

function SearchBar() {
  const [searchTerm, setSearchTerm] = useState('');  // Local!
  return <input value={searchTerm} onChange={e => setSearchTerm(e.target.value)} />;
}
```

---

## Key Interview Questions

**Q: Why can't you call useState conditionally?**
> Hooks rely on call order. React tracks hooks by their position in the call sequence. Conditional calls would change the order between renders, breaking React's hook tracking.

**Q: Why does React use immutable state updates?**
> React uses reference comparison (Object.is) to detect changes. If you mutate an object, the reference stays the same, so React thinks nothing changed and skips re-rendering.

**Q: What's the difference between `setState(value)` and `setState(prev => newValue)`?**
> Direct value: uses the value from the current closure (may be stale if batched). Functional form: receives the latest state as argument, always correct for derived updates.

**Q: State update is asynchronous - what does that mean?**
> Calling setState doesn't immediately change the state variable. React batches updates and applies them before the next render. You can't read the new value immediately after setState.

**Q: When should you use one state object vs multiple useState?**
> Use separate states for unrelated values. Group in one object when values always change together. If updating becomes complex, consider useReducer.
