# React Application Architecture

## Folder Structure

### Feature-Based (Recommended for Medium-Large Apps)
```
src/
├── features/
│   ├── auth/
│   │   ├── components/    (LoginForm, SignupForm)
│   │   ├── hooks/         (useAuth, useLogin)
│   │   ├── services/      (authService.ts)
│   │   ├── types/         (auth.types.ts)
│   │   └── index.ts       (public API)
│   ├── users/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── services/
│   │   └── types/
│   └── products/
├── shared/
│   ├── components/        (Button, Modal, Spinner)
│   ├── hooks/             (useDebounce, useFetch)
│   ├── utils/             (formatDate, validators)
│   └── types/             (common.types.ts)
├── layouts/               (MainLayout, AuthLayout)
├── pages/                 (route-level components)
├── services/              (API client, axios config)
├── store/                 (Redux store, slices)
├── config/                (env vars, constants)
├── App.tsx
└── main.tsx
```

---

## Layered Architecture

```
┌─────────────────────────────────────┐
│           UI Layer                   │  Components, styles
├─────────────────────────────────────┤
│        State Layer                   │  Context, Redux, local state
├─────────────────────────────────────┤
│      Business Logic                  │  Custom hooks, utilities
├─────────────────────────────────────┤
│         API Layer                    │  Service classes, API calls
├─────────────────────────────────────┤
│    Configuration Layer               │  Env vars, feature flags
└─────────────────────────────────────┘
```

### Separation of Concerns
```tsx
// UI Layer - Only renders
function UserCard({ user, onDelete }: UserCardProps) {
  return (
    <div>
      <h3>{user.name}</h3>
      <button onClick={() => onDelete(user.id)}>Delete</button>
    </div>
  );
}

// Business Logic - Custom hook
function useUsers() {
  const { data, isLoading } = useQuery({ queryKey: ['users'], queryFn: userService.getAll });
  const deleteMutation = useMutation({ mutationFn: userService.delete });
  return { users: data, isLoading, deleteUser: deleteMutation.mutate };
}

// API Layer - Service
const userService = {
  getAll: () => api.get('/users').then(r => r.data),
  delete: (id: string) => api.delete(`/users/${id}`),
};

// Page - Composes everything
function UsersPage() {
  const { users, isLoading, deleteUser } = useUsers();
  if (isLoading) return <Spinner />;
  return users.map(u => <UserCard key={u.id} user={u} onDelete={deleteUser} />);
}
```

---

## Key Architecture Principles

| Principle | Description |
|-----------|-------------|
| Single Responsibility | Each component/module does one thing |
| Separation of Concerns | UI, logic, data access in separate layers |
| DRY | Shared components and hooks |
| Colocation | Keep related files together (feature folders) |
| Abstraction | API layer hides HTTP details from components |
| Encapsulation | Features expose clean public API (index.ts) |

---

## Key Interview Questions

**Q: How do you structure a large React application?**
> Feature-based folder structure. Each feature has its own components, hooks, services, and types. Shared code lives in a `shared/` directory. Pages compose features. API layer is abstracted into services.

**Q: What's the benefit of an API service layer?**
> Single place to configure HTTP client, interceptors, error handling. Components don't know about axios/fetch. Easy to mock in tests. Easy to swap API clients.

**Q: How do you decide where state should live?**
> Start local (useState). Lift up only when siblings need it. Use Context for tree-wide data (theme, auth). Use Redux/React Query for complex global state. Keep state as close to usage as possible.
