# React Design Patterns

## Component Composition
```jsx
// Instead of one massive component, compose smaller ones
function Dashboard() {
  return (
    <Layout>
      <Header user={user} />
      <Sidebar navigation={nav} />
      <Content>
        <StatsCards data={stats} />
        <RecentActivity items={activities} />
      </Content>
    </Layout>
  );
}
```

## Container / Presentational
```jsx
// Presentational: How it looks (no logic)
function UserCard({ name, email, avatar }) {
  return <div><img src={avatar} /><h3>{name}</h3><p>{email}</p></div>;
}

// Container: How it works (logic + data)
function UserCardContainer({ userId }) {
  const { data: user, isLoading } = useQuery(['user', userId], () => fetchUser(userId));
  if (isLoading) return <Skeleton />;
  return <UserCard {...user} />;
}
```

## Provider Pattern
```jsx
function AppProviders({ children }) {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ThemeProvider>
          {children}
        </ThemeProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
```

## Compound Components
```jsx
<Select value={selected} onChange={setSelected}>
  <Select.Trigger>Choose option</Select.Trigger>
  <Select.Options>
    <Select.Option value="a">Option A</Select.Option>
    <Select.Option value="b">Option B</Select.Option>
  </Select.Options>
</Select>
```

## Custom Hook Pattern
```jsx
// Extract ALL reusable logic into hooks
function useToggle(initial = false) {
  const [value, setValue] = useState(initial);
  const toggle = useCallback(() => setValue(v => !v), []);
  const setTrue = useCallback(() => setValue(true), []);
  const setFalse = useCallback(() => setValue(false), []);
  return { value, toggle, setTrue, setFalse };
}
```

## State Reducer Pattern
```jsx
// Let consumers customize state transitions
function useToggle({ reducer = toggleReducer } = {}) {
  const [state, dispatch] = useReducer(reducer, { on: false });
  const toggle = () => dispatch({ type: 'TOGGLE' });
  return { on: state.on, toggle };
}

// Consumer can override behavior
function App() {
  const { on, toggle } = useToggle({
    reducer: (state, action) => {
      if (action.type === 'TOGGLE' && clickCount >= 4) return state;  // Custom limit
      return toggleReducer(state, action);
    }
  });
}
```

---

## Pattern Selection Guide

| Pattern | When to Use |
|---------|-------------|
| Composition | Always (default approach) |
| Custom Hooks | Reusable stateful logic |
| Compound Components | Related components sharing state |
| Provider | Global/subtree state distribution |
| Container/Presentational | Separate data fetching from display |
| HOC | Cross-cutting concerns (legacy) |
| Render Props | Flexible rendering (legacy, use hooks instead) |

---

## Key Interview Questions

**Q: What's your preferred React design pattern and why?**
> Custom hooks for logic reuse + composition for UI. Hooks replace HOCs and render props in most cases. Compound components for complex UI libraries. Provider for global state.

**Q: When would you still use HOC over hooks?**
> When you need to wrap multiple components with the same behavior without modifying them (e.g., withErrorBoundary, withAuth HOC for route protection). But in most cases, hooks are simpler.
