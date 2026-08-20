# React Router

## Setup

```jsx
import { BrowserRouter, Routes, Route, Link, NavLink, Navigate, Outlet,
         useNavigate, useParams, useLocation, useSearchParams } from 'react-router-dom';

function App() {
  return (
    <BrowserRouter>
      <nav>
        <Link to="/">Home</Link>
        <Link to="/about">About</Link>
        <NavLink to="/users" className={({ isActive }) => isActive ? 'active' : ''}>
          Users
        </NavLink>
      </nav>
      
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/about" element={<About />} />
        <Route path="/users" element={<Users />} />
        <Route path="/users/:id" element={<UserProfile />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  );
}
```

---

## Navigation

### Link and NavLink
```jsx
// Link - basic navigation (no page reload)
<Link to="/products">Products</Link>
<Link to={`/users/${user.id}`}>View Profile</Link>

// NavLink - adds active styling
<NavLink 
  to="/dashboard" 
  className={({ isActive }) => isActive ? 'nav-active' : ''}
  style={({ isActive }) => ({ fontWeight: isActive ? 'bold' : 'normal' })}
>
  Dashboard
</NavLink>
```

### useNavigate - Programmatic Navigation
```jsx
function LoginForm() {
  const navigate = useNavigate();
  
  const handleSubmit = async (e) => {
    e.preventDefault();
    await login(credentials);
    navigate('/dashboard');          // Navigate forward
    navigate('/dashboard', { replace: true });  // Replace history entry
    navigate(-1);                    // Go back
  };
}
```

### Navigate Component - Declarative Redirect
```jsx
<Routes>
  <Route path="/old-page" element={<Navigate to="/new-page" replace />} />
  <Route path="/login" element={
    isLoggedIn ? <Navigate to="/dashboard" /> : <LoginForm />
  } />
</Routes>
```

---

## Route Parameters

### Dynamic Routes with useParams
```jsx
// Route definition
<Route path="/users/:userId" element={<UserProfile />} />
<Route path="/posts/:postId/comments/:commentId" element={<Comment />} />

// Component
function UserProfile() {
  const { userId } = useParams();  // Extract from URL
  
  const [user, setUser] = useState(null);
  useEffect(() => {
    fetchUser(userId).then(setUser);
  }, [userId]);

  return <div>{user?.name}</div>;
}
```

### Query Parameters with useSearchParams
```jsx
// URL: /products?category=electronics&sort=price

function Products() {
  const [searchParams, setSearchParams] = useSearchParams();
  
  const category = searchParams.get('category');  // 'electronics'
  const sort = searchParams.get('sort');          // 'price'
  
  const updateFilter = (category) => {
    setSearchParams({ category, sort });  // Updates URL query string
  };

  return (/* ... */);
}
```

### useLocation
```jsx
function CurrentPage() {
  const location = useLocation();
  
  console.log(location.pathname);   // '/users/123'
  console.log(location.search);     // '?tab=posts'
  console.log(location.hash);       // '#section1'
  console.log(location.state);      // { from: '/login' }
}
```

---

## Nested Routes

```jsx
function App() {
  return (
    <Routes>
      <Route path="/dashboard" element={<DashboardLayout />}>
        <Route index element={<DashboardHome />} />
        <Route path="analytics" element={<Analytics />} />
        <Route path="settings" element={<Settings />} />
      </Route>
    </Routes>
  );
}

// Parent layout with Outlet for child routes
function DashboardLayout() {
  return (
    <div className="dashboard">
      <Sidebar />
      <main>
        <Outlet />  {/* Child route renders here */}
      </main>
    </div>
  );
}
```

---

## Protected Routes

```jsx
function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) return <Spinner />;
  
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}

// Usage
<Routes>
  <Route path="/login" element={<Login />} />
  <Route path="/dashboard" element={
    <ProtectedRoute>
      <Dashboard />
    </ProtectedRoute>
  } />
</Routes>

// Role-based protection
function RoleRoute({ roles, children }) {
  const { user } = useAuth();
  if (!roles.includes(user.role)) return <Navigate to="/unauthorized" />;
  return children;
}
```

---

## 404 Route

```jsx
<Routes>
  <Route path="/" element={<Home />} />
  <Route path="/about" element={<About />} />
  {/* Catch-all: matches anything not matched above */}
  <Route path="*" element={<NotFound />} />
</Routes>

function NotFound() {
  return (
    <div>
      <h1>404 - Page Not Found</h1>
      <Link to="/">Go Home</Link>
    </div>
  );
}
```

---

## Key Interview Questions

**Q: What's the difference between Link and anchor tag `<a>`?**
> `<a>` causes full page reload (fetches HTML from server). `<Link>` uses client-side routing (JavaScript updates URL and renders component, no page reload). Much faster for SPAs.

**Q: How do you handle authentication with React Router?**
> Create a ProtectedRoute component that checks auth state. If not authenticated, redirect to login with `<Navigate>`. Store the intended destination in location state for redirect after login.

**Q: What's the difference between `useParams` and `useSearchParams`?**
> `useParams` reads route parameters (`/users/:id` → `{id: '123'}`). `useSearchParams` reads/writes query string (`?page=2&sort=name`). Route params define the resource; query params are filters/options.

**Q: How does nested routing work?**
> Parent route renders a layout component with an `<Outlet />`. Child routes render inside that Outlet. The URL builds up: parent path + child path. Enables shared layouts.
