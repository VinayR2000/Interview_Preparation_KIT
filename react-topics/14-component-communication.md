# Component Communication

## Parent → Child (Props)
```jsx
function Parent() {
  const [user, setUser] = useState({ name: 'John', role: 'admin' });
  return <Child user={user} />;
}

function Child({ user }) {
  return <p>{user.name} ({user.role})</p>;
}
```

## Child → Parent (Callback Props)
```jsx
function Parent() {
  const [items, setItems] = useState([]);
  const handleAdd = (item) => setItems([...items, item]);
  return <Child onAdd={handleAdd} />;
}

function Child({ onAdd }) {
  return <button onClick={() => onAdd('New Item')}>Add</button>;
}
```

## Sibling → Sibling (Lift State Up)
```jsx
function Parent() {
  const [selected, setSelected] = useState(null);
  return (
    <>
      <SiblingA onSelect={setSelected} />
      <SiblingB selected={selected} />
    </>
  );
}
```

## Deep Nesting → Context
```jsx
const UserContext = createContext(null);

function App() {
  const [user, setUser] = useState({ name: 'John' });
  return (
    <UserContext.Provider value={{ user, setUser }}>
      <DeeplyNested />
    </UserContext.Provider>
  );
}

function DeeplyNested() {
  const { user } = useContext(UserContext);  // No prop drilling!
  return <p>{user.name}</p>;
}
```

## Communication Pattern Summary

| Pattern | Use Case | Direction |
|---------|----------|-----------|
| Props | Parent → Child | Down |
| Callback Props | Child → Parent | Up |
| Lift State Up | Sibling ↔ Sibling | Via common parent |
| Context | Any → Any (deep tree) | Any |
| Redux/Zustand | Global state | Any |
| Ref | Parent → Child (imperative) | Down |
| Event Bus | Decoupled components | Any (avoid in React) |

---

## Key Interview Questions

**Q: What is prop drilling and how do you solve it?**
> Passing props through many intermediate components that don't use them. Solutions: Context API, state management libraries (Redux), component composition (render props, children).

**Q: When to use Context vs Redux?**
> Context: Low-frequency updates (theme, auth, locale). Redux: High-frequency updates, complex state, middleware needs, time-travel debugging.
