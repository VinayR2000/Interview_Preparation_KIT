# Advanced React Patterns

## Higher-Order Components (HOC)

A function that takes a component and returns a new enhanced component.

```jsx
// HOC that adds loading state
function withLoading(WrappedComponent) {
  return function WithLoadingComponent({ isLoading, ...props }) {
    if (isLoading) return <Spinner />;
    return <WrappedComponent {...props} />;
  };
}

// Usage
const UserListWithLoading = withLoading(UserList);
<UserListWithLoading isLoading={loading} users={users} />
```

### HOC Conventions
- Name: `withXxx` (withAuth, withLoading, withTheme)
- Pass through unrelated props (`...props`)
- Don't mutate the original component

---

## Render Props

A component that takes a function as prop and calls it to render.

```jsx
function MouseTracker({ render }) {
  const [position, setPosition] = useState({ x: 0, y: 0 });
  
  const handleMove = (e) => setPosition({ x: e.clientX, y: e.clientY });

  return (
    <div onMouseMove={handleMove}>
      {render(position)}  {/* Call the render function */}
    </div>
  );
}

// Usage
<MouseTracker render={({ x, y }) => <p>Mouse: {x}, {y}</p>} />

// Or using children as render prop
<MouseTracker>
  {({ x, y }) => <p>Mouse: {x}, {y}</p>}
</MouseTracker>
```

### Modern Alternative: Custom Hooks
```jsx
// Custom hooks replace most HOC/render prop patterns
function useMousePosition() {
  const [position, setPosition] = useState({ x: 0, y: 0 });
  useEffect(() => { /* listener logic */ }, []);
  return position;
}
```

---

## Compound Components

Components that work together sharing implicit state.

```jsx
const TabContext = createContext();

function Tabs({ children, defaultTab }) {
  const [activeTab, setActiveTab] = useState(defaultTab);
  return (
    <TabContext.Provider value={{ activeTab, setActiveTab }}>
      <div className="tabs">{children}</div>
    </TabContext.Provider>
  );
}

Tabs.List = function TabList({ children }) {
  return <div className="tab-list">{children}</div>;
};

Tabs.Tab = function Tab({ value, children }) {
  const { activeTab, setActiveTab } = useContext(TabContext);
  return (
    <button className={activeTab === value ? 'active' : ''} 
            onClick={() => setActiveTab(value)}>
      {children}
    </button>
  );
};

Tabs.Panel = function TabPanel({ value, children }) {
  const { activeTab } = useContext(TabContext);
  return activeTab === value ? <div className="panel">{children}</div> : null;
};

// Usage - flexible, readable API
<Tabs defaultTab="profile">
  <Tabs.List>
    <Tabs.Tab value="profile">Profile</Tabs.Tab>
    <Tabs.Tab value="settings">Settings</Tabs.Tab>
  </Tabs.List>
  <Tabs.Panel value="profile"><ProfileContent /></Tabs.Panel>
  <Tabs.Panel value="settings"><SettingsContent /></Tabs.Panel>
</Tabs>
```

---

## Portals

Render children into a DOM node outside the parent hierarchy.

```jsx
import { createPortal } from 'react-dom';

function Modal({ isOpen, onClose, children }) {
  if (!isOpen) return null;
  
  return createPortal(
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        {children}
      </div>
    </div>,
    document.getElementById('modal-root')  // Renders here in DOM
  );
}

// Events still bubble through React tree (not DOM tree)
```

### Use Cases for Portals
- Modals / Dialogs
- Tooltips / Popovers
- Toast notifications
- Dropdown menus (overflow issues)

---

## Error Boundaries

Catch JavaScript errors in child components and show fallback UI.

```jsx
class ErrorBoundary extends React.Component {
  state = { hasError: false, error: null };

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('Error caught:', error, errorInfo);
    // Log to error reporting service
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback || <h1>Something went wrong.</h1>;
    }
    return this.props.children;
  }
}

// Usage
<ErrorBoundary fallback={<ErrorPage />}>
  <App />
</ErrorBoundary>

// Granular error boundaries
<ErrorBoundary fallback={<p>Chart failed to load</p>}>
  <Chart data={data} />
</ErrorBoundary>
```

**Note**: Error boundaries don't catch errors in:
- Event handlers (use try/catch)
- Async code (use try/catch)
- Server-side rendering
- The error boundary itself

---

## ForwardRef

```jsx
const Input = forwardRef(function Input({ label, ...props }, ref) {
  return (
    <label>
      {label}
      <input ref={ref} {...props} />
    </label>
  );
});

// Parent can now access the input DOM element
function Form() {
  const inputRef = useRef(null);
  return <Input ref={inputRef} label="Email" />;
}
```

---

## Pattern Comparison

| Pattern | Use Case | Modern Alternative |
|---------|----------|-------------------|
| HOC | Cross-cutting concerns | Custom Hooks |
| Render Props | Flexible rendering logic | Custom Hooks |
| Compound Components | Related component groups | Still relevant |
| Provider Pattern | Shared state/config | Still relevant (Context) |
| Error Boundaries | Error handling | Still relevant (class-only) |
| Portals | Render outside DOM tree | Still relevant |

---

## Key Interview Questions

**Q: HOC vs Custom Hooks?**
> Custom hooks are simpler, don't create extra components in the tree, no prop collision issues, better TypeScript support. Use hooks for new code. HOCs are legacy pattern but still found in codebases.

**Q: When would you use a Portal?**
> When a component needs to visually break out of its parent (modals, tooltips) but z-index/overflow CSS prevents it. Portal renders in a different DOM location while maintaining React tree behavior (events bubble normally).

**Q: Why are Error Boundaries class components?**
> There's no hook equivalent for `getDerivedStateFromError` or `componentDidCatch` yet. React team may add this in the future. For now, Error Boundaries must be class components.
