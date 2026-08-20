# React Fundamentals

## What is React?
- A JavaScript library for building user interfaces (UI)
- Developed by Facebook (Meta) in 2013
- Focuses only on the View layer (not a full framework)
- Component-based architecture
- Uses Virtual DOM for performance
- Declarative: You describe WHAT the UI should look like, React handles HOW

---

## Why React?

| Benefit | Description |
|---------|-------------|
| Component-Based | Reusable, composable UI building blocks |
| Virtual DOM | Efficient updates, minimal real DOM manipulation |
| One-Way Data Flow | Predictable state management |
| Large Ecosystem | Huge community, libraries, tools |
| Cross-Platform | React Native for mobile, React for web |
| JSX | Write HTML-like syntax in JavaScript |
| Performance | Smart diffing algorithm, batched updates |

---

## React vs Angular vs Vue

| Feature | React | Angular | Vue |
|---------|-------|---------|-----|
| Type | Library | Framework | Framework |
| Language | JavaScript/JSX | TypeScript | JavaScript/Template |
| DOM | Virtual DOM | Real DOM (Incremental) | Virtual DOM |
| Data Binding | One-way | Two-way | Two-way |
| Learning Curve | Medium | Steep | Easy |
| Bundle Size | Small (~40KB) | Large (~140KB) | Small (~30KB) |
| State Management | External (Redux, Context) | Built-in (Services/RxJS) | Vuex/Pinia |
| Maintained By | Meta | Google | Community/Evan You |
| Best For | Flexible, large apps | Enterprise apps | Quick prototyping |

---

## SPA vs MPA

### SPA (Single Page Application)
- One HTML page, content updated dynamically via JavaScript
- No full page reload on navigation
- Client-side routing
- Examples: Gmail, Twitter, Netflix

**Pros**: Fast navigation, smooth UX, less server load
**Cons**: Slow initial load, SEO challenges, JavaScript-dependent

### MPA (Multi-Page Application)
- Each route is a separate HTML page from server
- Full page reload on navigation
- Server-side routing
- Examples: Amazon, Wikipedia

**Pros**: Better SEO, faster initial load, works without JS
**Cons**: Slower navigation, more server requests, full reloads

---

## Component-Based Architecture

### What is a Component?
- Independent, reusable piece of UI
- Has its own logic, markup, and styling
- Like a JavaScript function that returns HTML (JSX)

```jsx
// Component = Function that returns UI
function Welcome({ name }) {
  return <h1>Hello, {name}!</h1>;
}
```

### Component Hierarchy
```
<App>
  ├── <Header />
  ├── <Main>
  │   ├── <Sidebar />
  │   └── <Content>
  │       ├── <Article />
  │       └── <Comments />
  └── <Footer />
```

---

## Functional Components vs Class Components

### Functional Components (Modern - USE THIS)
```jsx
function Greeting({ name }) {
  const [count, setCount] = useState(0);
  
  return (
    <div>
      <h1>Hello, {name}!</h1>
      <p>Count: {count}</p>
      <button onClick={() => setCount(count + 1)}>Increment</button>
    </div>
  );
}
```

### Class Components (Legacy)
```jsx
class Greeting extends React.Component {
  constructor(props) {
    super(props);
    this.state = { count: 0 };
  }

  render() {
    return (
      <div>
        <h1>Hello, {this.props.name}!</h1>
        <p>Count: {this.state.count}</p>
        <button onClick={() => this.setState({ count: this.state.count + 1 })}>
          Increment
        </button>
      </div>
    );
  }
}
```

### Functional vs Class
| Functional | Class |
|-----------|-------|
| Simple function | ES6 class |
| Hooks for state/lifecycle | this.state, lifecycle methods |
| Less boilerplate | More boilerplate |
| Easier to test | Harder to test |
| Better performance (slightly) | `this` binding issues |
| Modern React standard | Legacy (still works) |

---

## JSX

### What is JSX?
- JavaScript XML - syntax extension for JavaScript
- Looks like HTML but is JavaScript underneath
- Gets compiled to `React.createElement()` calls by Babel

```jsx
// JSX
const element = <h1 className="title">Hello!</h1>;

// Compiles to:
const element = React.createElement('h1', { className: 'title' }, 'Hello!');
```

### JSX Rules
1. Return a single root element (or use Fragment)
2. Close all tags (`<br />`, `<img />`)
3. Use `className` instead of `class`
4. Use `htmlFor` instead of `for`
5. CamelCase for attributes (`onClick`, `onChange`)
6. JavaScript expressions in `{}`

---

## Props vs State

| Props | State |
|-------|-------|
| Passed from parent | Managed within component |
| Read-only (immutable) | Mutable (via setState/useState) |
| Function parameters | Component's memory |
| External configuration | Internal data |
| Trigger re-render when changed from parent | Trigger re-render when updated |

```jsx
// Props: passed in, read-only
function Card({ title, description }) {
  return <div><h2>{title}</h2><p>{description}</p></div>;
}

// State: internal, mutable
function Counter() {
  const [count, setCount] = useState(0);
  return <button onClick={() => setCount(count + 1)}>{count}</button>;
}
```

---

## Children

```jsx
// Children prop - content between opening and closing tags
function Card({ children }) {
  return <div className="card">{children}</div>;
}

// Usage
<Card>
  <h2>Title</h2>
  <p>Content goes here</p>
</Card>
```

---

## Fragments

```jsx
// Problem: Components must return single root element
// Solution: Fragment - wraps without adding extra DOM node

// Long syntax
import { Fragment } from 'react';
<Fragment>
  <h1>Title</h1>
  <p>Content</p>
</Fragment>

// Short syntax (preferred)
<>
  <h1>Title</h1>
  <p>Content</p>
</>
```

---

## Conditional Rendering

```jsx
// 1. if/else (outside JSX)
function Greeting({ isLoggedIn }) {
  if (isLoggedIn) return <Dashboard />;
  return <Login />;
}

// 2. Ternary (inside JSX)
{isLoggedIn ? <Dashboard /> : <Login />}

// 3. Logical AND (show or nothing)
{isLoggedIn && <Dashboard />}

// 4. Logical OR (fallback)
{username || 'Guest'}
```

---

## Lists and Keys

```jsx
function UserList({ users }) {
  return (
    <ul>
      {users.map(user => (
        <li key={user.id}>{user.name}</li>
      ))}
    </ul>
  );
}
```

### Why Keys?
- Help React identify which items changed, added, or removed
- Must be unique among siblings
- Should be stable (don't use array index for dynamic lists)
- Enables efficient reconciliation

### Key Rules
- ✅ Use unique IDs from data (`key={user.id}`)
- ❌ Don't use array index (`key={index}`) for dynamic lists
- ❌ Don't use `Math.random()` (new key every render = re-mount)

---

## Composition vs Inheritance

### React favors Composition over Inheritance

```jsx
// Composition: Building complex UI from simple components
function Dialog({ title, children }) {
  return (
    <div className="dialog">
      <h2>{title}</h2>
      <div className="body">{children}</div>
    </div>
  );
}

function ConfirmDialog() {
  return (
    <Dialog title="Are you sure?">
      <p>This action cannot be undone.</p>
      <button>Confirm</button>
    </Dialog>
  );
}
```

---

## Components vs Elements

| Element | Component |
|---------|-----------|
| Plain object describing what to render | Function/class that returns elements |
| `<div>`, `<h1>` (DOM elements) | `<App>`, `<Header>` (custom) |
| Immutable | Can have state and lifecycle |
| Cheap to create | Can be complex |
| `React.createElement('div', ...)` | `function App() { return ... }` |

---

## Key Interview Questions

**Q: What is the Virtual DOM?**
> A lightweight JavaScript representation of the real DOM. When state changes, React creates a new Virtual DOM, diffs it with the previous one, and updates only the changed parts in the real DOM (reconciliation).

**Q: Why can't you directly mutate state in React?**
> React uses reference comparison to detect changes. If you mutate directly, the reference stays the same, so React won't know to re-render. Always create new objects/arrays.

**Q: What happens when you call setState?**
> React schedules a re-render. It batches multiple setState calls, creates new Virtual DOM, diffs with previous, and commits minimal changes to real DOM.

**Q: Why does React use one-way data flow?**
> Makes apps more predictable and easier to debug. Data flows down (parent to child via props). Events flow up (child to parent via callbacks). You always know where data comes from.
