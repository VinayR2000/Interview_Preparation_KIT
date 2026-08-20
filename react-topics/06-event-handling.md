# Event Handling

## Event Handler Basics

```jsx
function App() {
  // Define handler
  const handleClick = () => {
    console.log('Button clicked!');
  };

  // Attach to element
  return <button onClick={handleClick}>Click Me</button>;
}
```

### Naming Convention
- Handler functions: `handle` + event name → `handleClick`, `handleSubmit`
- Props: `on` + event name → `onClick`, `onSubmit`

---

## Common Events

### onClick
```jsx
function App() {
  const handleClick = (event) => {
    console.log('Clicked!', event.target);
  };

  return <button onClick={handleClick}>Click</button>;
}
```

### onChange
```jsx
function Form() {
  const [value, setValue] = useState('');

  const handleChange = (e) => {
    setValue(e.target.value);
  };

  return <input value={value} onChange={handleChange} />;
}
```

### onSubmit
```jsx
function LoginForm() {
  const handleSubmit = (e) => {
    e.preventDefault();  // Prevent page reload!
    // Handle form data
  };

  return (
    <form onSubmit={handleSubmit}>
      <input type="email" />
      <button type="submit">Login</button>
    </form>
  );
}
```

### Mouse Events
```jsx
<div
  onMouseEnter={() => setHovered(true)}
  onMouseLeave={() => setHovered(false)}
  onMouseMove={(e) => setPosition({ x: e.clientX, y: e.clientY })}
  onDoubleClick={handleDoubleClick}
>
  Hover me
</div>
```

### Keyboard Events
```jsx
<input
  onKeyDown={(e) => {
    if (e.key === 'Enter') handleSubmit();
    if (e.key === 'Escape') handleCancel();
  }}
  onKeyUp={(e) => console.log('Key released:', e.key)}
/>
```

---

## Event Arguments

```jsx
// React passes SyntheticEvent automatically
function App() {
  const handleClick = (event) => {
    console.log(event.target);        // The element that was clicked
    console.log(event.currentTarget); // The element with the handler
    console.log(event.type);          // 'click'
    console.log(event.clientX);       // Mouse X coordinate
  };

  return <button onClick={handleClick}>Click</button>;
}
```

---

## Passing Parameters to Event Handlers

```jsx
function TodoList({ todos, onDelete }) {
  return (
    <ul>
      {todos.map(todo => (
        <li key={todo.id}>
          {todo.text}
          {/* ✅ Arrow function wrapper */}
          <button onClick={() => onDelete(todo.id)}>Delete</button>
          
          {/* ❌ WRONG - calls immediately on render! */}
          <button onClick={onDelete(todo.id)}>Delete</button>
        </li>
      ))}
    </ul>
  );
}

// Alternative: Create a handler that returns a handler
const handleDelete = (id) => () => {
  console.log('Deleting:', id);
};
<button onClick={handleDelete(todo.id)}>Delete</button>
```

---

## preventDefault() and stopPropagation()

### preventDefault()
Prevents the browser's default behavior for an event.

```jsx
// Prevent form submission (page reload)
const handleSubmit = (e) => {
  e.preventDefault();
  // Custom form handling
};

// Prevent link navigation
const handleLinkClick = (e) => {
  e.preventDefault();
  // Custom navigation logic
};

<a href="/page" onClick={handleLinkClick}>Custom Link</a>
```

### stopPropagation()
Prevents event from bubbling up to parent elements.

```jsx
function Card() {
  return (
    <div onClick={() => console.log('Card clicked')}>
      <button onClick={(e) => {
        e.stopPropagation();  // Parent's onClick won't fire
        console.log('Button clicked');
      }}>
        Click Me
      </button>
    </div>
  );
}
```

---

## Event Bubbling and Capturing

### Event Phases
```
1. Capturing Phase: Window → Document → html → body → div → button (top-down)
2. Target Phase: Event reaches the target element
3. Bubbling Phase: button → div → body → html → Document → Window (bottom-up)
```

### Bubbling (Default in React)
```jsx
function App() {
  return (
    <div onClick={() => console.log('Grandparent')}>
      <div onClick={() => console.log('Parent')}>
        <button onClick={() => console.log('Button')}>
          Click
        </button>
      </div>
    </div>
  );
}
// Click button → logs: "Button", "Parent", "Grandparent"
```

### Capturing Phase (Use `onClickCapture`)
```jsx
<div onClickCapture={() => console.log('Capturing phase!')}>
  <button onClick={() => console.log('Bubbling phase!')}>Click</button>
</div>
// Click → logs: "Capturing phase!", "Bubbling phase!"
```

---

## Synthetic Events

### What are Synthetic Events?
- React wraps native browser events in SyntheticEvent objects
- Cross-browser compatible (normalizes differences)
- Same interface as native events
- Pooled for performance (in React < 17)

### Key Properties
```jsx
const handleClick = (e) => {
  // Common properties
  e.type           // 'click', 'change', etc.
  e.target         // DOM element that triggered event
  e.currentTarget  // DOM element with the handler
  e.preventDefault()
  e.stopPropagation()
  
  // Access native event
  e.nativeEvent    // Underlying browser event
  
  // Mouse events
  e.clientX, e.clientY  // Viewport coordinates
  e.pageX, e.pageY      // Page coordinates
  
  // Keyboard events
  e.key            // 'Enter', 'Escape', 'a', etc.
  e.keyCode        // (deprecated) numeric code
  e.ctrlKey        // true if Ctrl was held
  e.shiftKey       // true if Shift was held
  e.altKey         // true if Alt was held
};
```

### Synthetic vs Native Events
| Synthetic Event | Native Event |
|----------------|--------------|
| Cross-browser consistent | Browser-specific |
| Created by React | Created by browser |
| Access via `e.nativeEvent` | Direct access |
| Attached to root (delegation) | Attached to elements |

---

## Common Patterns

### Debounced Input
```jsx
function Search() {
  const [query, setQuery] = useState('');
  
  const debouncedSearch = useMemo(
    () => debounce((value) => fetchResults(value), 300),
    []
  );

  const handleChange = (e) => {
    setQuery(e.target.value);
    debouncedSearch(e.target.value);
  };

  return <input value={query} onChange={handleChange} />;
}
```

### Toggle Handler
```jsx
const [isOpen, setIsOpen] = useState(false);
const toggle = () => setIsOpen(prev => !prev);

<button onClick={toggle}>{isOpen ? 'Close' : 'Open'}</button>
```

---

## Key Interview Questions

**Q: Why use `onClick={handleClick}` not `onClick={handleClick()}`?**
> With parentheses, the function executes immediately during render, not on click. Without parentheses, you pass a reference to the function that React calls when the event occurs.

**Q: What is event delegation in React?**
> React attaches a single event listener to the root DOM node (React 17+) instead of individual elements. Events bubble up and React determines which component handler to call. This is more efficient than attaching listeners to every element.

**Q: How do you handle events in a list efficiently?**
> Use event delegation pattern: attach one handler to the parent, use `event.target` or data attributes to identify which item was clicked. Or use an arrow function wrapper passing the item ID.

**Q: What's the difference between `e.target` and `e.currentTarget`?**
> `e.target` is the element that actually triggered the event (could be a child). `e.currentTarget` is the element that has the event handler attached.

**Q: Why does React use Synthetic Events?**
> For cross-browser compatibility and performance. Synthetic events normalize browser differences and allow React to use event delegation (single listener at root) efficiently.
