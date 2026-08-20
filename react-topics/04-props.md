# Props

## Passing Props

```jsx
// Parent passes props to child
function App() {
  return <UserCard name="John" age={25} isAdmin={true} />;
}

// Child receives props
function UserCard({ name, age, isAdmin }) {
  return (
    <div>
      <h2>{name}</h2>
      <p>Age: {age}</p>
      {isAdmin && <span>Admin</span>}
    </div>
  );
}
```

---

## Receiving Props

```jsx
// Method 1: Destructuring in parameter (preferred)
function Greeting({ name, message }) {
  return <p>{message}, {name}!</p>;
}

// Method 2: Props object
function Greeting(props) {
  return <p>{props.message}, {props.name}!</p>;
}

// Method 3: Destructure inside body
function Greeting(props) {
  const { name, message } = props;
  return <p>{message}, {name}!</p>;
}
```

---

## Default Props

```jsx
// Method 1: Destructuring defaults (modern, preferred)
function Button({ variant = 'primary', size = 'medium', children }) {
  return <button className={`btn-${variant} btn-${size}`}>{children}</button>;
}

// Method 2: defaultProps (legacy, still works)
function Button({ variant, size, children }) {
  return <button className={`btn-${variant} btn-${size}`}>{children}</button>;
}
Button.defaultProps = {
  variant: 'primary',
  size: 'medium',
};
```

---

## Props Destructuring

```jsx
// Nested destructuring
function UserCard({ user: { name, email, address: { city } } }) {
  return (
    <div>
      <h2>{name}</h2>
      <p>{email}</p>
      <p>{city}</p>
    </div>
  );
}

// Rest operator - collect remaining props
function Button({ variant, ...rest }) {
  return <button className={`btn-${variant}`} {...rest} />;
}
// Usage: <Button variant="primary" onClick={fn} disabled>Submit</Button>
```

---

## Props Validation with PropTypes

```jsx
import PropTypes from 'prop-types';

function UserCard({ name, age, email, role, onDelete }) {
  return (/* ... */);
}

UserCard.propTypes = {
  name: PropTypes.string.isRequired,
  age: PropTypes.number,
  email: PropTypes.string,
  role: PropTypes.oneOf(['admin', 'user', 'moderator']),
  onDelete: PropTypes.func,
  items: PropTypes.arrayOf(PropTypes.string),
  user: PropTypes.shape({
    id: PropTypes.number.isRequired,
    name: PropTypes.string.isRequired,
  }),
};
```

### TypeScript Alternative (Preferred in Modern React)
```tsx
interface UserCardProps {
  name: string;
  age?: number;
  email: string;
  role: 'admin' | 'user' | 'moderator';
  onDelete?: (id: number) => void;
}

function UserCard({ name, age = 25, email, role, onDelete }: UserCardProps) {
  return (/* ... */);
}
```

---

## Passing Different Types

### Passing Objects
```jsx
const user = { name: 'John', age: 25 };

// Pass entire object
<UserCard user={user} />

// Spread props (pass all properties individually)
<UserCard {...user} />  // Same as name="John" age={25}
```

### Passing Arrays
```jsx
<TodoList items={['Buy milk', 'Clean house', 'Code']} />
<Chart data={[10, 20, 30, 40, 50]} />
```

### Passing Functions (Callbacks)
```jsx
function Parent() {
  const handleDelete = (id) => {
    console.log(`Delete item ${id}`);
  };

  return <Child onDelete={handleDelete} />;
}

function Child({ onDelete }) {
  return <button onClick={() => onDelete(42)}>Delete</button>;
}
```

### Passing Components (Render Props / Slots)
```jsx
function Layout({ header, sidebar, children }) {
  return (
    <div className="layout">
      <div className="header">{header}</div>
      <div className="sidebar">{sidebar}</div>
      <div className="content">{children}</div>
    </div>
  );
}

<Layout 
  header={<Header />} 
  sidebar={<Sidebar />}
>
  <MainContent />
</Layout>
```

---

## Children Prop

```jsx
// children = anything between opening/closing tags
function Card({ children }) {
  return <div className="card">{children}</div>;
}

// String children
<Card>Hello World</Card>

// JSX children
<Card>
  <h2>Title</h2>
  <p>Description</p>
</Card>

// Multiple children utilities
import { Children } from 'react';
function List({ children }) {
  return (
    <ul>
      {Children.map(children, (child, index) => (
        <li key={index}>{child}</li>
      ))}
    </ul>
  );
}
```

---

## Props Immutability

```jsx
// ❌ NEVER mutate props
function BadComponent(props) {
  props.name = 'Modified';  // ERROR: Props are read-only!
  props.items.push('new');  // ERROR: Don't mutate!
  return <div>{props.name}</div>;
}

// ✅ Props are read-only - use them, don't change them
function GoodComponent({ name, items }) {
  // Create new data based on props
  const uppercaseName = name.toUpperCase();
  const sortedItems = [...items].sort();
  
  return (
    <div>
      <h1>{uppercaseName}</h1>
      {sortedItems.map(item => <p key={item}>{item}</p>)}
    </div>
  );
}
```

### Why Immutable?
- Predictable data flow
- React can efficiently detect changes (reference comparison)
- Parent controls the data, child just displays it
- Avoids bugs from shared mutable state

---

## Key Interview Questions

**Q: What's the difference between props and state?**
> Props are external inputs (passed from parent, read-only). State is internal data (managed within component, mutable). Props are like function parameters; state is like local variables.

**Q: What is prop drilling and how to avoid it?**
> Passing props through multiple intermediate components that don't need them. Solutions: Context API, Redux, component composition (passing children/render props).

**Q: Can you modify props in a child component?**
> No. Props are immutable. If a child needs to "change" a prop, it calls a callback function passed by the parent, and the parent updates its state.

**Q: What happens when props change?**
> The component re-renders with the new props. React calls the function again with updated props, generates new JSX, diffs with previous output, and updates the DOM.

**Q: Should you use PropTypes or TypeScript?**
> TypeScript is preferred in modern projects. It catches errors at compile time (not runtime), provides IDE autocomplete, and is more powerful. PropTypes only warn at runtime in development.
