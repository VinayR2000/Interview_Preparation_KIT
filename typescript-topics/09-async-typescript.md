# Async TypeScript ⭐⭐⭐

## Promises with Types

### Basic Promise Typing
```typescript
// Function returning a Promise
function fetchUser(id: number): Promise<User> {
  return fetch(`/api/users/${id}`)
    .then(response => response.json());
}

// The type parameter defines what the Promise resolves to
const userPromise: Promise<User> = fetchUser(1);

// Explicit Promise construction
function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function fetchWithTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return Promise.race([
    promise,
    delay(ms).then(() => { throw new Error("Timeout"); })
  ]);
}
```

### Promise Combinators
```typescript
// Promise.all — all must succeed, returns tuple/array
async function loadDashboard(): Promise<[User, Order[], Notification[]]> {
  const [user, orders, notifications] = await Promise.all([
    fetchUser(1),           // Promise<User>
    fetchOrders(1),         // Promise<Order[]>
    fetchNotifications(1)   // Promise<Notification[]>
  ]);
  return [user, orders, notifications];
}

// Promise.allSettled — never rejects, returns status for each
async function loadMultiple(ids: number[]): Promise<User[]> {
  const results = await Promise.allSettled(
    ids.map(id => fetchUser(id))
  );
  
  // Type: PromiseSettledResult<User>[]
  // = (PromiseFulfilledResult<User> | PromiseRejectedResult)[]
  
  return results
    .filter((r): r is PromiseFulfilledResult<User> => r.status === "fulfilled")
    .map(r => r.value);
}

// Promise.race — first to resolve/reject wins
async function fetchWithFallback(primary: string, fallback: string): Promise<User> {
  return Promise.race([
    fetch(primary).then(r => r.json()),
    delay(3000).then(() => fetch(fallback).then(r => r.json()))
  ]);
}

// Promise.any — first to succeed (ignores rejections)
async function fetchFromAnyMirror(urls: string[]): Promise<Response> {
  return Promise.any(urls.map(url => fetch(url)));
  // Throws AggregateError if ALL fail
}
```

---

## async/await ⭐⭐⭐

### Basic async/await
```typescript
// async function always returns Promise<T>
async function getUser(id: number): Promise<User> {
  const response = await fetch(`/api/users/${id}`);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.json();  // Return type inferred from json()
}

// Arrow function async
const getUsers = async (): Promise<User[]> => {
  const response = await fetch("/api/users");
  return response.json();
};
```

### Error Handling
```typescript
// try/catch with typed errors
async function safeGetUser(id: number): Promise<User | null> {
  try {
    const response = await fetch(`/api/users/${id}`);
    if (!response.ok) throw new Error(`Status: ${response.status}`);
    return await response.json();
  } catch (error) {
    // error is 'unknown' in TypeScript (since 4.4 with useUnknownInCatchVariables)
    if (error instanceof Error) {
      console.error("Fetch failed:", error.message);
    }
    return null;
  }
}

// Result type pattern (functional error handling)
type Result<T, E = Error> =
  | { ok: true; data: T }
  | { ok: false; error: E };

async function safeApiCall<T>(fn: () => Promise<T>): Promise<Result<T>> {
  try {
    const data = await fn();
    return { ok: true, data };
  } catch (error) {
    return { ok: false, error: error instanceof Error ? error : new Error(String(error)) };
  }
}

// Usage
const result = await safeApiCall(() => getUser(1));
if (result.ok) {
  console.log(result.data.name);  // ✅ TypeScript knows data is User
} else {
  console.error(result.error.message);  // ✅ TypeScript knows error is Error
}
```

---

## Async Patterns

### Sequential vs Parallel
```typescript
// Sequential (slow) — each waits for previous
async function sequential(): Promise<void> {
  const user = await fetchUser(1);       // Wait
  const orders = await fetchOrders(1);   // Then wait
  const prefs = await fetchPrefs(1);     // Then wait
  // Total time: sum of all three
}

// Parallel (fast) — all start immediately
async function parallel(): Promise<void> {
  const [user, orders, prefs] = await Promise.all([
    fetchUser(1),       // Start
    fetchOrders(1),     // Start simultaneously
    fetchPrefs(1)       // Start simultaneously
  ]);
  // Total time: max of all three
}

// Parallel with individual error handling
async function parallelSafe(): Promise<void> {
  const [userResult, ordersResult] = await Promise.allSettled([
    fetchUser(1),
    fetchOrders(1)
  ]);
  
  if (userResult.status === "fulfilled") {
    const user = userResult.value;  // User
  }
}
```

### Async Iteration
```typescript
// Async generator
async function* paginate<T>(url: string): AsyncGenerator<T[]> {
  let page = 1;
  let hasMore = true;
  
  while (hasMore) {
    const response = await fetch(`${url}?page=${page}`);
    const data: { items: T[]; hasNext: boolean } = await response.json();
    yield data.items;
    hasMore = data.hasNext;
    page++;
  }
}

// Consuming async generator
async function loadAllUsers(): Promise<User[]> {
  const allUsers: User[] = [];
  for await (const batch of paginate<User>("/api/users")) {
    allUsers.push(...batch);
  }
  return allUsers;
}
```

### Retry Pattern
```typescript
async function retry<T>(
  fn: () => Promise<T>,
  options: { maxAttempts?: number; delayMs?: number; backoff?: boolean } = {}
): Promise<T> {
  const { maxAttempts = 3, delayMs = 1000, backoff = true } = options;
  
  let lastError: Error | undefined;
  
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      if (attempt < maxAttempts) {
        const wait = backoff ? delayMs * Math.pow(2, attempt - 1) : delayMs;
        await delay(wait);
      }
    }
  }
  
  throw lastError;
}

// Usage
const user = await retry(() => fetchUser(1), { maxAttempts: 3, delayMs: 500 });
```

---

## Typing HTTP Calls (Full-Stack Pattern) ⭐⭐⭐

### Generic API Client
```typescript
interface ApiResponse<T> {
  data: T;
  message: string;
  status: number;
}

interface ApiError {
  message: string;
  code: string;
  status: number;
}

class ApiClient {
  constructor(private baseUrl: string) {}

  async get<T>(path: string): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`);
    if (!response.ok) {
      const error: ApiError = await response.json();
      throw error;
    }
    return response.json();
  }

  async post<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!response.ok) {
      const error: ApiError = await response.json();
      throw error;
    }
    return response.json();
  }

  async put<TRequest, TResponse>(path: string, body: TRequest): Promise<TResponse> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
  }

  async delete(path: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}${path}`, { method: "DELETE" });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
  }
}

// Usage — fully typed
const api = new ApiClient("http://localhost:8080");

const users = await api.get<ApiResponse<User[]>>("/api/users");
// users.data is User[]

const created = await api.post<CreateUserDTO, ApiResponse<User>>(
  "/api/users",
  { name: "Vinay", email: "v@x.com", password: "secret" }
);
// created.data is User
```

---

## Async in Angular (Observable vs Promise)

```typescript
// Angular prefers Observables (RxJS), but async/await still useful

// Observable approach (Angular standard)
@Injectable({ providedIn: 'root' })
class UserService {
  constructor(private http: HttpClient) {}

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }

  // With error handling
  getUser(id: number): Observable<User> {
    return this.http.get<User>(`/api/users/${id}`).pipe(
      catchError(error => {
        console.error('Failed:', error);
        return throwError(() => error);
      })
    );
  }
}

// Promise approach (with lastValueFrom)
import { lastValueFrom } from 'rxjs';

async function loadUser(service: UserService, id: number): Promise<User> {
  return lastValueFrom(service.getUser(id));
}
```

---

## Async in React

```typescript
// Custom hook for async data fetching
function useAsync<T>(
  asyncFn: () => Promise<T>,
  deps: any[] = []
): { data: T | null; loading: boolean; error: Error | null } {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    
    asyncFn()
      .then(result => { if (!cancelled) setData(result); })
      .catch(err => { if (!cancelled) setError(err); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, deps);

  return { data, loading, error };
}

// Usage
function UserProfile({ userId }: { userId: number }) {
  const { data: user, loading, error } = useAsync(
    () => api.get<User>(`/api/users/${userId}`),
    [userId]
  );

  if (loading) return <Spinner />;
  if (error) return <ErrorMessage error={error} />;
  return <div>{user!.name}</div>;
}
```

---

## Key Interview Questions

**Q: How do you type async functions in TypeScript?**
> Async functions always return `Promise<T>` where T is the resolved value type. Use `async function f(): Promise<User>` for explicit typing. TypeScript can often infer the Promise return type from the return statement. The `Awaited<T>` utility type unwraps nested Promises.

**Q: How do you handle errors in TypeScript async code?**
> In try/catch, the caught error is typed as `unknown` (with `useUnknownInCatchVariables`). You must narrow it: `if (error instanceof Error)`. For functional error handling, use a Result/Either pattern: `type Result<T> = { ok: true; data: T } | { ok: false; error: Error }`. This makes error handling explicit and type-safe.

**Q: `Promise.all` vs `Promise.allSettled` — when to use each?**
> `Promise.all` fails fast — if ANY promise rejects, the entire call rejects. Use when all results are required. `Promise.allSettled` never rejects — returns status for each promise. Use when you want partial results even if some fail. Return type differs: `T[]` vs `PromiseSettledResult<T>[]`.

**Q: How would you type a generic API client for a Spring Boot backend?**
> Define response/error interfaces matching your Spring Boot DTOs. Create a generic class with methods like `get<T>(path): Promise<T>` and `post<TReq, TRes>(path, body): Promise<TRes>`. The generic parameters ensure each call site knows exactly what it sends and receives. Add error handling that throws typed API errors.
