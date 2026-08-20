# Components

## Functional Components

### Basic Structure
```jsx
// Named function
function Welcome({ name }) {
  return <h1>Hello, {name}!</h1>;
}

// Arrow function
const Welcome = ({ name }) => {
  return <h1>Hello, {name}!</h1>;
};

// Arrow function with implicit return
const Welcome = ({ name }) => <h1>Hello, {name}!</h1>;
```

### Rules
- Name must start with uppercase letter (`<Welcome />` not `<welcome />`)
- Must return JSX (or null)
- Receives props as first argument
- Can have state (via hooks)

---

## Class Components (Legacy)

```jsx
class Welcome extends React.Component {
  constructor(props) {
    super(props);
    this.state = { count: 0 };
  }

  componentDidMount() {
    console.log('Mounted');
  }

  componentDidUpdate(prevProps, prevState) {
    console.log('Updated');
  }

  componentWillUnmount() {
    console.log('Unmounting');
  }

  render() {
    return (
      <div>
        <h1>Hello, {this.props.name}!</h1>
        <p>Count: {this.state.count}</p>
        <button onClick={() => this.setState({ count: this.state.count + 1 })}>
          +1
        </button>
      </div>
    );
  }
}
```

---

## Component Composition

### Building Complex UI from Simple Components
```jsx
function App() {
  return (
    <Layout>
      <Header />
      <Main>
        <Sidebar />
        <Content />
      </Main>
      <Footer />
    </Layout>
  );
}

function Layout({ children }) {
  return <div className="layout">{children}</div>;
}
```

### Specialization
```jsx
// Generic component
function Button({ variant, children, onClick }) {
  return (
    <button className={`btn btn-${variant}`} onClick={onClick}>
      {children}
    </button>
  );
}

// Specialized components
function PrimaryButton({ children, onClick }) {
  return <Button variant="primary" onClick={onClick}>{children}</Button>;
}

function DangerButton({ children, onClick }) {
  return <Button variant="danger" onClick={onClick}>{children}</Button>;
}
```

---

## Reusable Components

### Design for Reusability
```jsx
// ✅ Reusable: Accepts configuration via props
function Card({ title, description, image, actions }) {
  return (
    <div className="card">
      {image && <img src={image} alt={title} />}
      <div className="card-body">
        <h3>{title}</h3>
        <p>{description}</p>
        {actions && <div className="card-actions">{actions}</div>}
      </div>
    </div>
  );
}

// Usage in different contexts
<Card title="Product" description="..." image="/product.jpg" 
      actions={<button>Buy</button>} />
<Card title="User" description="..." 
      actions={<><button>Edit</button><button>Delete</button></>} />
```

---

## Presentational vs Container Components

### Presentational (Dumb) Components
- Concerned with **how things look**
- Receive data via props
- No state management logic (may have UI state like isOpen)
- No side effects, no API calls

```jsx
function UserCard({ name, email, avatar }) {
  return (
    <div className="user-card">
      <img src={avatar} alt={name} />
      <h3>{name}</h3>
      <p>{email}</p>
    </div>
  );
}
```

### Container (Smart) Components
- Concerned with **how things work**
- Fetch data, manage state, handle business logic
- Pass data to presentational components

```jsx
function UserCardContainer({ userId }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchUser(userId).then(data => {
      setUser(data);
      setLoading(false);
    });
  }, [userId]);

  if (loading) return <Spinner />;
  return <UserCard name={user.name} email={user.email} avatar={user.avatar} />;
}
```

### Modern Approach (Custom Hooks)
```jsx
// Instead of container components, use custom hooks
function useUser(userId) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchUser(userId).then(data => {
      setUser(data);
      setLoading(false);
    });
  }, [userId]);

  return { user, loading };
}

function UserProfile({ userId }) {
  const { user, loading } = useUser(userId);
  if (loading) return <Spinner />;
  return <UserCard {...user} />;
}
```

---

## Parent-Child Relationship

```jsx
// Parent passes data down via props
function Parent() {
  const [items, setItems] = useState(['A', 'B', 'C']);

  const addItem = (item) => setItems([...items, item]);

  return (
    <div>
      <Child items={items} onAdd={addItem} />
    </div>
  );
}

// Child receives props, communicates up via callbacks
function Child({ items, onAdd }) {
  return (
    <div>
      <ul>{items.map((item, i) => <li key={i}>{item}</li>)}</ul>
      <button onClick={() => onAdd('New')}>Add Item</button>
    </div>
  );
}
```

---

## Component Nesting

```jsx
// Deep nesting is fine, but avoid prop drilling
function App() {
  return (
    <Page>
      <Section>
        <Article>
          <Paragraph />
        </Article>
      </Section>
    </Page>
  );
}

// Each level can add its own styling/logic
function Section({ children }) {
  return <section className="section">{children}</section>;
}
```

---

## Children Prop

```jsx
// children = anything between opening and closing tags
function Modal({ isOpen, onClose, children }) {
  if (!isOpen) return null;
  
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}

// Usage - children can be anything
<Modal isOpen={showModal} onClose={() => setShowModal(false)}>
  <h2>Confirm Delete</h2>
  <p>Are you sure you want to delete this item?</p>
  <button onClick={handleDelete}>Delete</button>
</Modal>
```

### Children Variants
```jsx
// String
<Button>Click me</Button>

// Elements
<Card><h1>Title</h1><p>Content</p></Card>

// Components
<Layout><Sidebar /><Content /></Layout>

// Functions (Render Props pattern)
<DataFetcher>{(data) => <List items={data} />}</DataFetcher>
```

---

## Component Design Principles

### 1. Single Responsibility
Each component should do one thing well.

### 2. DRY (Don't Repeat Yourself)
Extract repeated UI patterns into reusable components.

### 3. Props as API
Design props as the component's public API - clear, minimal, well-documented.

### 4. Composition over Configuration
Prefer composing components over adding many configuration props.

```jsx
// ❌ Over-configured
<Card showImage showActions showFooter headerStyle="large" />

// ✅ Composable
<Card>
  <Card.Image src="..." />
  <Card.Body>Content</Card.Body>
  <Card.Footer><Button>Action</Button></Card.Footer>
</Card>
```

### 5. Appropriate Abstraction Level
Don't abstract too early. Wait until you see repetition.

---

## Key Interview Questions

**Q: When should you split a component?**
> When it gets too large (>200 lines), does multiple things, has reusable parts, or when you need to optimize re-renders for a specific section.

**Q: What's the difference between controlled and uncontrolled components?**
> Controlled: React state drives the component value (via value prop + onChange). Uncontrolled: Component manages its own state (via ref to read DOM value).

**Q: Why prefer composition over inheritance in React?**
> React components are designed for composition (nesting, children prop, render props). Inheritance creates tight coupling and is harder to reason about. The React team has found no use cases where inheritance is better.

**Q: What makes a good reusable component?**
> Clear props interface, single responsibility, no hardcoded data, configurable via props, sensible defaults, works in different contexts.
