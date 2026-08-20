# API Integration

## fetch() API

```jsx
// GET
const response = await fetch('/api/users');
const data = await response.json();

// POST
const response = await fetch('/api/users', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: 'John', email: 'john@example.com' }),
});

// PUT
await fetch(`/api/users/${id}`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(updatedUser),
});

// DELETE
await fetch(`/api/users/${id}`, { method: 'DELETE' });
```

---

## Axios

```jsx
import axios from 'axios';

// Create instance with defaults
const api = axios.create({
  baseURL: 'https://api.example.com',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
});

// GET
const { data } = await api.get('/users');
const { data } = await api.get('/users', { params: { page: 1, limit: 10 } });

// POST
const { data } = await api.post('/users', { name: 'John', email: 'john@test.com' });

// PUT / PATCH / DELETE
await api.put(`/users/${id}`, updatedUser);
await api.patch(`/users/${id}`, { name: 'Jane' });
await api.delete(`/users/${id}`);
```

---

## Complete Data Fetching Pattern

```jsx
function UserList() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();

    async function fetchUsers() {
      try {
        setLoading(true);
        setError(null);
        const response = await fetch('/api/users', { signal: controller.signal });
        
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const data = await response.json();
        setUsers(data);
      } catch (err) {
        if (err.name !== 'AbortError') {
          setError(err.message);
        }
      } finally {
        setLoading(false);
      }
    }

    fetchUsers();
    return () => controller.abort();  // Cancel on unmount
  }, []);

  if (loading) return <Spinner />;
  if (error) return <ErrorMessage message={error} />;
  return <ul>{users.map(u => <li key={u.id}>{u.name}</li>)}</ul>;
}
```

---

## Loading, Error, and Cancellation States

```jsx
function useApi(url) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const execute = useCallback(async (options = {}) => {
    const controller = new AbortController();
    
    try {
      setLoading(true);
      setError(null);
      const response = await fetch(url, { ...options, signal: controller.signal });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const result = await response.json();
      setData(result);
      return result;
    } catch (err) {
      if (err.name !== 'AbortError') {
        setError(err.message);
        throw err;
      }
    } finally {
      setLoading(false);
    }
    
    return () => controller.abort();
  }, [url]);

  return { data, loading, error, execute };
}
```

---

## Axios Interceptors

```jsx
// Request interceptor - add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - handle errors globally
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // Token expired - try refresh
      const newToken = await refreshToken();
      if (newToken) {
        error.config.headers.Authorization = `Bearer ${newToken}`;
        return api(error.config);  // Retry original request
      }
      // Refresh failed - logout
      logout();
    }
    return Promise.reject(error);
  }
);
```

---

## API Service Layer

```jsx
// services/userService.js
const API_BASE = '/api';

export const userService = {
  getAll: (params) => api.get(`${API_BASE}/users`, { params }),
  getById: (id) => api.get(`${API_BASE}/users/${id}`),
  create: (data) => api.post(`${API_BASE}/users`, data),
  update: (id, data) => api.put(`${API_BASE}/users/${id}`, data),
  delete: (id) => api.delete(`${API_BASE}/users/${id}`),
};

// Usage in component
function UserProfile({ userId }) {
  const [user, setUser] = useState(null);
  
  useEffect(() => {
    userService.getById(userId).then(res => setUser(res.data));
  }, [userId]);
}
```

---

## Key Interview Questions

**Q: fetch vs axios?**
> fetch: Built-in, no install, need manual JSON parsing, no request cancel (use AbortController), no interceptors. Axios: Need install, auto JSON, built-in cancel tokens, interceptors, better error handling (throws on 4xx/5xx).

**Q: How do you handle race conditions in API calls?**
> Use AbortController to cancel previous requests when dependencies change. Or use a boolean flag (`let ignore = false`) in the cleanup function to discard stale responses.

**Q: How do you handle token refresh in React?**
> Use axios response interceptor. On 401, attempt token refresh. If successful, retry the original request. If refresh fails, redirect to login.
