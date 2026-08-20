# Data Fetching (React Query / TanStack Query)

## Server State vs Client State

| Server State | Client State |
|-------------|--------------|
| Stored on server | Stored in browser |
| Fetched asynchronously | Synchronous access |
| Can become stale | Always up-to-date |
| Shared across users | Unique to user session |
| Needs caching/revalidation | Simple state management |
| Examples: API data, DB records | Examples: UI state, form data |

---

## TanStack Query (React Query)

### Setup
```jsx
import { QueryClient, QueryClientProvider, useQuery, useMutation } from '@tanstack/react-query';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <MyApp />
    </QueryClientProvider>
  );
}
```

### useQuery (Read Data)
```jsx
function UserList() {
  const { data, isLoading, error, isError, refetch } = useQuery({
    queryKey: ['users'],              // Cache key
    queryFn: () => fetchUsers(),      // Fetch function
    staleTime: 5 * 60 * 1000,        // Data fresh for 5 min
    gcTime: 10 * 60 * 1000,          // Cache kept for 10 min
    retry: 3,                         // Retry failed requests
    refetchOnWindowFocus: true,       // Refetch when tab focused
  });

  if (isLoading) return <Spinner />;
  if (isError) return <Error message={error.message} />;
  return <ul>{data.map(u => <li key={u.id}>{u.name}</li>)}</ul>;
}
```

### useMutation (Write Data)
```jsx
function CreateUser() {
  const queryClient = useQueryClient();
  
  const mutation = useMutation({
    mutationFn: (newUser) => api.post('/users', newUser),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });  // Refetch list
    },
  });

  return (
    <button onClick={() => mutation.mutate({ name: 'John' })} disabled={mutation.isPending}>
      {mutation.isPending ? 'Creating...' : 'Create User'}
    </button>
  );
}
```

---

## Caching & Query Invalidation

```jsx
// Manual invalidation (e.g., after mutation)
queryClient.invalidateQueries({ queryKey: ['users'] });      // All user queries
queryClient.invalidateQueries({ queryKey: ['users', 1] });   // Specific user

// Prefetch (e.g., on hover)
queryClient.prefetchQuery({
  queryKey: ['user', userId],
  queryFn: () => fetchUser(userId),
});

// Set cache directly
queryClient.setQueryData(['user', userId], updatedUser);
```

---

## Pagination & Infinite Queries

```jsx
// Pagination
function Users() {
  const [page, setPage] = useState(1);
  const { data } = useQuery({
    queryKey: ['users', page],
    queryFn: () => fetchUsers(page),
    keepPreviousData: true,  // Show old data while loading new page
  });
}

// Infinite scroll
function Feed() {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['feed'],
    queryFn: ({ pageParam = 1 }) => fetchFeed(pageParam),
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });

  return (
    <div>
      {data.pages.map(page => page.items.map(item => <Post key={item.id} {...item} />))}
      {hasNextPage && (
        <button onClick={fetchNextPage} disabled={isFetchingNextPage}>
          Load More
        </button>
      )}
    </div>
  );
}
```

---

## Optimistic Updates

```jsx
const mutation = useMutation({
  mutationFn: updateTodo,
  onMutate: async (newTodo) => {
    await queryClient.cancelQueries({ queryKey: ['todos'] });
    const previousTodos = queryClient.getQueryData(['todos']);
    
    // Optimistically update cache
    queryClient.setQueryData(['todos'], (old) =>
      old.map(t => t.id === newTodo.id ? { ...t, ...newTodo } : t)
    );
    
    return { previousTodos };  // Context for rollback
  },
  onError: (err, newTodo, context) => {
    queryClient.setQueryData(['todos'], context.previousTodos);  // Rollback
  },
  onSettled: () => {
    queryClient.invalidateQueries({ queryKey: ['todos'] });  // Refetch
  },
});
```

---

## React Query vs Manual Fetching

| React Query | useEffect + useState |
|-------------|---------------------|
| Built-in caching | Manual cache management |
| Automatic refetch | Manual refetch logic |
| Loading/error states free | Manual state management |
| Deduplication | Duplicate requests |
| Background updates | Manual polling |
| Pagination built-in | Manual implementation |
| Devtools | No devtools |

---

## Key Interview Questions

**Q: Why use React Query over useEffect for data fetching?**
> React Query handles caching, deduplication, background refetching, pagination, optimistic updates, retry logic, and loading/error states automatically. useEffect requires manual implementation of all these.

**Q: What is stale time vs cache time?**
> staleTime: How long data is considered fresh (won't refetch). gcTime (cache time): How long inactive data stays in cache. After gcTime expires, data is garbage collected.

**Q: What is query invalidation?**
> Marking cached data as stale, triggering a refetch. Used after mutations to ensure UI shows latest server data. `queryClient.invalidateQueries(['users'])`.
