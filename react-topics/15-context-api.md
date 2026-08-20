# Context API

## Overview

- Provides a way to pass data through the component tree without prop drilling
- Creates a "global" state for a subtree of components
- Three parts: `createContext`, `Provider`, `useContext`

---

## Basic Usage

```jsx
// 1. Create Context
const ThemeContext = createContext('light');  // default value

// 2. Provide value
function App() {
  const [theme, setTheme] = useState('dark');
  
  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      <Header />
      <Main />
      <Footer />
    </ThemeContext.Provider>
  );
}

// 3. Consume with useContext
function Header() {
  const { theme, setTheme } = useContext(ThemeContext);
  
  return (
    <header className={theme}>
      <button onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
        Toggle Theme
      </button>
    </header>
  );
}
```

---

## Context vs Props

| Props | Context |
|-------|---------|
| Explicit data flow (visible) | Implicit (hidden) |
| Good for 1-2 levels | Good for deeply nested |
| Component is reusable | Component coupled to context |
| Easy to trace data | Harder to trace |

### When to Use Context
- Theme (dark/light mode)
- Authentication (current user)
- Language/locale
- Feature flags
- Any data needed by many components at different levels

---

## Context vs Redux

| Context | Redux |
|---------|-------|
| Built-in React | External library |
| No middleware | Middleware support (thunk, saga) |
| Re-renders all consumers on change | Selective re-rendering with selectors |
| Simple state | Complex state with actions |
| No devtools | Redux DevTools |
| Good for low-frequency updates | Good for high-frequency updates |

---

## Multiple Contexts

```jsx
const AuthContext = createContext(null);
const ThemeContext = createContext('light');
const LocaleContext = createContext('en');

function App() {
  return (
    <AuthContext.Provider value={authValue}>
      <ThemeContext.Provider value={themeValue}>
        <LocaleContext.Provider value={localeValue}>
          <Main />
        </LocaleContext.Provider>
      </ThemeContext.Provider>
    </AuthContext.Provider>
  );
}
```

---

## Context Performance Issue

```jsx
// ❌ Problem: ALL consumers re-render when ANY value changes
function App() {
  const [user, setUser] = useState(null);
  const [theme, setTheme] = useState('light');
  
  // New object reference every render → all consumers re-render
  return (
    <AppContext.Provider value={{ user, setUser, theme, setTheme }}>
      <Children />
    </AppContext.Provider>
  );
}

// ✅ Solution 1: Split contexts
<UserContext.Provider value={{ user, setUser }}>
  <ThemeContext.Provider value={{ theme, setTheme }}>
    <Children />
  </ThemeContext.Provider>
</UserContext.Provider>

// ✅ Solution 2: Memoize the value
function App() {
  const [user, setUser] = useState(null);
  const value = useMemo(() => ({ user, setUser }), [user]);
  return <UserContext.Provider value={value}><Children /></UserContext.Provider>;
}
```

---

## Custom Provider Pattern

```jsx
const AuthContext = createContext(undefined);

function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    checkAuth().then(setUser).finally(() => setLoading(false));
  }, []);

  const login = async (creds) => { /* ... */ };
  const logout = () => { /* ... */ };

  const value = useMemo(() => ({
    user, loading, login, logout, isAuthenticated: !!user
  }), [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// Custom hook with error boundary
function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}

// Usage
function App() {
  return (
    <AuthProvider>
      <Router />
    </AuthProvider>
  );
}
```

---

## Key Interview Questions

**Q: When should you NOT use Context?**
> For frequently changing state (every keystroke, animations, real-time data). Context re-renders ALL consumers on any change. Use state management libraries or local state instead.

**Q: Does Context replace Redux?**
> For simple global state (theme, auth), yes. For complex state with middleware, devtools, selective re-rendering, and time-travel debugging, Redux is still better.

**Q: How do you prevent unnecessary re-renders with Context?**
> Split into multiple contexts, memoize the provider value with useMemo, use React.memo on consumers, or extract consuming logic into a separate component.
