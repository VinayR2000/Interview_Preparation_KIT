# Authentication & Authorization

## Authentication vs Authorization

| Authentication (AuthN) | Authorization (AuthZ) |
|------------------------|----------------------|
| WHO are you? | WHAT can you do? |
| Verify identity | Verify permissions |
| Login credentials | Roles and permissions |
| Results in token/session | Results in access/deny |

---

## JWT Authentication Flow

```
1. User sends credentials (email/password) → Server
2. Server validates → Returns Access Token + Refresh Token
3. Client stores tokens → Sends Access Token with each request
4. Server validates token → Returns protected resource
5. Access Token expires → Use Refresh Token to get new Access Token
6. Refresh Token expires → User must re-login
```

### Token Structure
```
Access Token (short-lived: 15min - 1hr):
  - Contains user info (id, roles)
  - Sent with every API request
  - Stateless verification

Refresh Token (long-lived: 7-30 days):
  - Used only to get new access token
  - Stored in HttpOnly cookie (secure)
  - Can be revoked server-side
```

---

## Token Storage Options

| Storage | XSS Safe | CSRF Safe | Recommendation |
|---------|----------|-----------|----------------|
| localStorage | ❌ | ✅ | Avoid for tokens |
| sessionStorage | ❌ | ✅ | Better, tab-scoped |
| HttpOnly Cookie | ✅ | ❌ (need CSRF token) | Best for refresh token |
| Memory (variable) | ✅ | ✅ | Best for access token |

### Recommended Pattern
```
Access Token  → In-memory (React state/context)
Refresh Token → HttpOnly Secure Cookie (set by server)
```

---

## Implementation

```jsx
// AuthContext
const AuthContext = createContext(null);

function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);  // Access token in memory
  const [loading, setLoading] = useState(true);

  // Check auth on mount (try refresh)
  useEffect(() => {
    refreshAuth().then(({ user, accessToken }) => {
      setUser(user);
      setToken(accessToken);
    }).catch(() => {
      setUser(null);
      setToken(null);
    }).finally(() => setLoading(false));
  }, []);

  const login = async (credentials) => {
    const { user, accessToken } = await api.post('/auth/login', credentials);
    setUser(user);
    setToken(accessToken);
  };

  const logout = async () => {
    await api.post('/auth/logout');
    setUser(null);
    setToken(null);
  };

  const value = useMemo(() => ({
    user, token, loading, login, logout,
    isAuthenticated: !!user,
  }), [user, token, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
```

---

## Protected Routes

```jsx
function ProtectedRoute({ children, requiredRoles }) {
  const { user, isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) return <FullPageSpinner />;
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (requiredRoles && !requiredRoles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}

// Usage
<Route path="/admin" element={
  <ProtectedRoute requiredRoles={['admin']}>
    <AdminDashboard />
  </ProtectedRoute>
} />
```

---

## Security Concerns

### XSS (Cross-Site Scripting)
- Attacker injects malicious script into your page
- Script can read localStorage, cookies (non-HttpOnly)
- **Prevention**: Sanitize input, use HttpOnly cookies, CSP headers

### CSRF (Cross-Site Request Forgery)
- Attacker tricks user into making unwanted requests
- Works because browser auto-sends cookies
- **Prevention**: CSRF tokens, SameSite cookies, check Origin header

### CORS (Cross-Origin Resource Sharing)
- Browser blocks requests to different origins by default
- Server must explicitly allow your frontend origin
- Configure: `Access-Control-Allow-Origin: https://your-frontend.com`

---

## Key Interview Questions

**Q: Where should you store JWT tokens?**
> Access token in memory (React state), refresh token in HttpOnly Secure cookie. Never localStorage for sensitive tokens (XSS vulnerable).

**Q: How do you handle token expiration?**
> Axios interceptor catches 401 responses, calls refresh endpoint to get new access token, then retries the failed request. If refresh fails, redirect to login.

**Q: What's the difference between localStorage and sessionStorage?**
> localStorage persists until cleared. sessionStorage clears when tab closes. Both are accessible to JavaScript (XSS risk). Neither is ideal for auth tokens.

**Q: How do you implement role-based access in React?**
> Store user roles in auth context. Protected route components check roles before rendering. Hide UI elements based on roles. Always enforce server-side too (frontend is not security).
