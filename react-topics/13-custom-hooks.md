# Custom Hooks

## What is a Custom Hook?

- A JavaScript function whose name starts with `use`
- Extracts reusable stateful logic from components
- Can call other hooks (useState, useEffect, etc.)
- Share logic, not state (each usage gets its own state)

```jsx
// Custom hook
function useCounter(initialValue = 0) {
  const [count, setCount] = useState(initialValue);
  const increment = () => setCount(prev => prev + 1);
  const decrement = () => setCount(prev => prev - 1);
  const reset = () => setCount(initialValue);
  return { count, increment, decrement, reset };
}

// Usage - each component gets independent state
function ComponentA() {
  const { count, increment } = useCounter(0);
  return <button onClick={increment}>{count}</button>;
}
```

---

## Hook Naming Convention

- Must start with `use` (required by React lint rules)
- Describes what it does: `useAuth`, `useFetch`, `useLocalStorage`
- Returns what the consumer needs

---

## Common Custom Hooks

### API / Fetch Hook
```jsx
function useFetch(url) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    
    fetch(url, { signal: controller.signal })
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then(data => { setData(data); setLoading(false); })
      .catch(err => {
        if (err.name !== 'AbortError') {
          setError(err.message); setLoading(false);
        }
      });

    return () => controller.abort();
  }, [url]);

  return { data, loading, error };
}

// Usage
function Users() {
  const { data: users, loading, error } = useFetch('/api/users');
  if (loading) return <Spinner />;
  if (error) return <Error message={error} />;
  return <UserList users={users} />;
}
```

### Authentication Hook
```jsx
function useAuth() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      validateToken(token).then(setUser).finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (credentials) => {
    const { token, user } = await api.login(credentials);
    localStorage.setItem('token', token);
    setUser(user);
  };

  const logout = () => {
    localStorage.removeItem('token');
    setUser(null);
  };

  return { user, loading, login, logout, isAuthenticated: !!user };
}
```

### Local Storage Hook
```jsx
function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    try {
      const item = localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } catch {
      return initialValue;
    }
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(value));
  }, [key, value]);

  const remove = () => {
    localStorage.removeItem(key);
    setValue(initialValue);
  };

  return [value, setValue, remove];
}

// Usage
const [theme, setTheme] = useLocalStorage('theme', 'light');
```

### Debounce Hook
```jsx
function useDebounce(value, delay = 500) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

// Usage
function Search() {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    if (debouncedQuery) searchAPI(debouncedQuery);
  }, [debouncedQuery]);

  return <input value={query} onChange={e => setQuery(e.target.value)} />;
}
```

### Form Hook
```jsx
function useForm(initialValues, validate) {
  const [values, setValues] = useState(initialValues);
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});

  const handleChange = (e) => {
    const { name, value } = e.target;
    setValues(prev => ({ ...prev, [name]: value }));
  };

  const handleBlur = (e) => {
    const { name } = e.target;
    setTouched(prev => ({ ...prev, [name]: true }));
    if (validate) setErrors(validate(values));
  };

  const handleSubmit = (callback) => (e) => {
    e.preventDefault();
    const validationErrors = validate ? validate(values) : {};
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length === 0) callback(values);
  };

  const reset = () => {
    setValues(initialValues);
    setErrors({});
    setTouched({});
  };

  return { values, errors, touched, handleChange, handleBlur, handleSubmit, reset };
}
```

### Pagination Hook
```jsx
function usePagination(totalItems, itemsPerPage = 10) {
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.ceil(totalItems / itemsPerPage);

  const nextPage = () => setCurrentPage(prev => Math.min(prev + 1, totalPages));
  const prevPage = () => setCurrentPage(prev => Math.max(prev - 1, 1));
  const goToPage = (page) => setCurrentPage(Math.max(1, Math.min(page, totalPages)));

  const startIndex = (currentPage - 1) * itemsPerPage;
  const endIndex = startIndex + itemsPerPage;

  return {
    currentPage, totalPages, nextPage, prevPage, goToPage,
    startIndex, endIndex,
    hasNext: currentPage < totalPages,
    hasPrev: currentPage > 1,
  };
}
```

---

## Key Interview Questions

**Q: How do custom hooks share logic but not state?**
> Each component that calls a custom hook gets its own isolated state. The hook logic (useState, useEffect calls) runs independently for each component. It's like calling a function—same logic, different variables.

**Q: What's the benefit of custom hooks over utility functions?**
> Custom hooks can use other hooks (useState, useEffect, useContext). Regular functions cannot. Custom hooks integrate with React's lifecycle and state management.

**Q: Can custom hooks return JSX?**
> Technically yes, but it's not recommended. If a hook returns JSX, it's really a component in disguise. Hooks should return data/functions, not UI.
