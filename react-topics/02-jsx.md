# JSX (JavaScript XML)

## JSX Syntax

### What is JSX?
- Syntax extension that lets you write HTML-like code in JavaScript
- Not valid JavaScript - compiled by Babel/SWC to `React.createElement()` calls
- Makes UI code more readable and intuitive

```jsx
// JSX
const element = <h1 className="greeting">Hello, World!</h1>;

// Compiled JavaScript
const element = React.createElement(
  'h1',
  { className: 'greeting' },
  'Hello, World!'
);
```

---

## JavaScript Inside JSX - Expressions `{}`

### Curly Braces = JavaScript Expressions
```jsx
function Profile({ user }) {
  const fullName = `${user.firstName} ${user.lastName}`;
  
  return (
    <div>
      {/* Variables */}
      <h1>{fullName}</h1>
      
      {/* Expressions */}
      <p>Age: {user.age + 1}</p>
      
      {/* Function calls */}
      <p>{user.name.toUpperCase()}</p>
      
      {/* Ternary */}
      <p>{user.isAdmin ? 'Admin' : 'User'}</p>
      
      {/* Template literals */}
      <p>{`Welcome, ${user.name}!`}</p>
    </div>
  );
}
```

### What CAN go inside `{}`
- ✅ Variables, expressions, function calls
- ✅ Ternary operator
- ✅ Array methods (map, filter)
- ✅ String concatenation
- ✅ Math operations

### What CANNOT go inside `{}`
- ❌ Statements (if/else, for, while)
- ❌ Object literals directly (use double braces: `{{key: value}}`)
- ❌ Variable declarations

---

## className (Not class)

```jsx
// ❌ HTML uses class
<div class="container">

// ✅ JSX uses className (class is reserved in JS)
<div className="container">

// Dynamic classes
<div className={`card ${isActive ? 'active' : ''}`}>
<div className={isError ? 'text-red' : 'text-green'}>
```

---

## Inline Styles

```jsx
// Inline styles use objects with camelCase properties
<div style={{ 
  backgroundColor: 'blue',    // background-color → backgroundColor
  fontSize: '16px',           // font-size → fontSize
  marginTop: '10px',          // margin-top → marginTop
  padding: '20px'
}}>
  Styled content
</div>

// As a variable
const cardStyle = {
  border: '1px solid #ddd',
  borderRadius: '8px',
  padding: '16px',
};

<div style={cardStyle}>Content</div>
```

### CSS Property vs JSX Style
| CSS | JSX |
|-----|-----|
| `background-color` | `backgroundColor` |
| `font-size` | `fontSize` |
| `margin-top` | `marginTop` |
| `z-index` | `zIndex` |
| `border-radius` | `borderRadius` |

---

## JSX Attributes

```jsx
// HTML attributes → camelCase in JSX
<label htmlFor="email">Email</label>      // for → htmlFor
<input tabIndex={1} />                     // tabindex → tabIndex
<div onClick={handleClick} />              // onclick → onClick
<input onChange={handleChange} />          // onchange → onChange
<img src={url} alt="description" />        // self-closing required
<input type="text" autoFocus />            // autofocus → autoFocus
<input readOnly value={text} />            // readonly → readOnly

// Boolean attributes
<input disabled />                         // Same as disabled={true}
<input disabled={false} />                 // Explicitly false

// Data attributes (keep lowercase)
<div data-testid="user-card" />
```

---

## Conditional JSX

```jsx
function Notification({ type, message }) {
  // 1. Early return
  if (!message) return null;

  // 2. Ternary
  return (
    <div className={type === 'error' ? 'alert-red' : 'alert-blue'}>
      {/* 3. Logical AND - render or nothing */}
      {type === 'error' && <ErrorIcon />}
      
      {/* 4. Logical OR - fallback */}
      <p>{message || 'No message'}</p>
      
      {/* 5. IIFE for complex logic */}
      {(() => {
        switch (type) {
          case 'success': return <SuccessIcon />;
          case 'error': return <ErrorIcon />;
          default: return <InfoIcon />;
        }
      })()}
    </div>
  );
}
```

### Common Patterns
```jsx
// Show/hide
{isVisible && <Modal />}

// Loading state
{isLoading ? <Spinner /> : <Content />}

// Multiple conditions
{!isLoading && !error && data && <DataTable data={data} />}

// Null = render nothing
{shouldShow ? <Component /> : null}
```

---

## map() in JSX

```jsx
// Rendering lists
function TodoList({ todos }) {
  return (
    <ul>
      {todos.map(todo => (
        <li key={todo.id} className={todo.done ? 'completed' : ''}>
          {todo.text}
        </li>
      ))}
    </ul>
  );
}

// With index (avoid as key for dynamic lists)
{items.map((item, index) => (
  <div key={item.id}>
    <span>{index + 1}. {item.name}</span>
  </div>
))}

// Filtered list
{users
  .filter(user => user.isActive)
  .map(user => <UserCard key={user.id} user={user} />)
}
```

---

## Fragment Syntax

```jsx
// Problem: JSX must return one root element
// ❌ This fails
return (
  <h1>Title</h1>
  <p>Content</p>
);

// ✅ Solution 1: Wrapper div (adds extra DOM node)
return (
  <div>
    <h1>Title</h1>
    <p>Content</p>
  </div>
);

// ✅ Solution 2: Fragment (no extra DOM node)
return (
  <>
    <h1>Title</h1>
    <p>Content</p>
  </>
);

// Fragment with key (e.g., in a list)
{items.map(item => (
  <Fragment key={item.id}>
    <dt>{item.term}</dt>
    <dd>{item.description}</dd>
  </Fragment>
))}
```

---

## JSX Compilation

```jsx
// What you write:
function App() {
  return (
    <div className="app">
      <Header title="My App" />
      <p>Hello!</p>
    </div>
  );
}

// What Babel/SWC compiles it to (React 17+ JSX transform):
import { jsx as _jsx, jsxs as _jsxs } from 'react/jsx-runtime';

function App() {
  return _jsxs('div', {
    className: 'app',
    children: [
      _jsx(Header, { title: 'My App' }),
      _jsx('p', { children: 'Hello!' }),
    ],
  });
}
```

---

## JSX vs HTML

| HTML | JSX |
|------|-----|
| `class` | `className` |
| `for` | `htmlFor` |
| `onclick` | `onClick` |
| `tabindex` | `tabIndex` |
| `<br>` | `<br />` (self-closing required) |
| `<img src="...">` | `<img src="..." />` |
| Style as string | Style as object |
| Comments `<!-- -->` | `{/* comment */}` |
| Can return multiple elements | Must return single root |
| Static | Dynamic with `{}` expressions |

---

## Key Interview Questions

**Q: Is JSX required for React?**
> No. JSX is syntactic sugar over `React.createElement()`. You can write React without JSX, but it's much less readable. Every JSX element compiles to a createElement call.

**Q: Why does JSX use className instead of class?**
> Because `class` is a reserved keyword in JavaScript (used for ES6 classes). Since JSX is JavaScript, not HTML, it uses `className` to avoid conflicts.

**Q: Can you write if/else inside JSX?**
> No. JSX only accepts expressions, not statements. Use ternary operators, logical AND (&&), or extract the logic outside the JSX return.

**Q: What happens if you don't provide a key in a list?**
> React uses array index as fallback key and shows a warning. Without stable keys, React can't efficiently track which items changed, leading to bugs with component state and unnecessary re-renders.

**Q: What does JSX compile to?**
> In React 17+, JSX compiles to `jsx()` / `jsxs()` calls from `react/jsx-runtime`. Before React 17, it compiled to `React.createElement()` calls, which is why React had to be in scope.
