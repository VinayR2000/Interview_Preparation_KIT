# TypeScript with Angular and React ⭐⭐⭐

## TypeScript + Angular

### Component Typing
```typescript
import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';

interface User {
  id: number;
  name: string;
  email: string;
  role: 'admin' | 'user' | 'editor';
}

@Component({
  selector: 'app-user-card',
  template: `
    <div class="card">
      <h3>{{ user.name }}</h3>
      <p>{{ user.email }}</p>
      <button (click)="onEdit()">Edit</button>
    </div>
  `
})
export class UserCardComponent implements OnInit {
  @Input() user!: User;                              // Non-null assertion (parent provides)
  @Input() showActions: boolean = true;              // Default value
  @Output() edit = new EventEmitter<User>();         // Typed event emitter
  @Output() delete = new EventEmitter<number>();     // Emits user id

  ngOnInit(): void {
    console.log(`Loaded user: ${this.user.name}`);
  }

  onEdit(): void {
    this.edit.emit(this.user);
  }
}
```

### Service Typing
```typescript
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, throwError } from 'rxjs';

interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
}

interface CreateUserDTO {
  name: string;
  email: string;
  role: User['role'];  // Indexed access type — reuses role type
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly apiUrl = '/api/users';

  constructor(private http: HttpClient) {}

  getUsers(page: number, size: number): Observable<PaginatedResponse<User>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    
    return this.http.get<PaginatedResponse<User>>(this.apiUrl, { params });
  }

  getUserById(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  createUser(dto: CreateUserDTO): Observable<User> {
    return this.http.post<User>(this.apiUrl, dto);
  }

  updateUser(id: number, updates: Partial<User>): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, updates);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // With error handling
  searchUsers(query: string): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/search`, {
      params: { q: query }
    }).pipe(
      catchError(error => {
        console.error('Search failed:', error);
        return throwError(() => new Error('Search failed'));
      })
    );
  }
}
```

### RxJS Typing
```typescript
import { Observable, Subject, BehaviorSubject, combineLatest } from 'rxjs';
import { map, filter, switchMap, debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class StateService {
  // BehaviorSubject — has initial value, emits current value to new subscribers
  private usersSubject = new BehaviorSubject<User[]>([]);
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private errorSubject = new Subject<string>();

  // Public observables (readonly)
  users$: Observable<User[]> = this.usersSubject.asObservable();
  loading$: Observable<boolean> = this.loadingSubject.asObservable();
  error$: Observable<string> = this.errorSubject.asObservable();

  // Derived observables
  activeUsers$: Observable<User[]> = this.users$.pipe(
    map(users => users.filter(u => u.role !== 'admin'))
  );

  userCount$: Observable<number> = this.users$.pipe(
    map(users => users.length)
  );

  // Combined state
  viewModel$: Observable<{ users: User[]; loading: boolean }> = combineLatest([
    this.users$,
    this.loading$
  ]).pipe(
    map(([users, loading]) => ({ users, loading }))
  );
}

// Search with debounce
@Component({ /* ... */ })
export class SearchComponent {
  searchControl = new FormControl<string>('');

  results$: Observable<User[]> = this.searchControl.valueChanges.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    filter((term): term is string => term !== null && term.length >= 2),
    switchMap(term => this.userService.searchUsers(term))
  );
}
```

### Form Typing (Reactive Forms)
```typescript
import { FormGroup, FormControl, Validators } from '@angular/forms';

// Typed form (Angular 14+)
interface UserForm {
  name: FormControl<string>;
  email: FormControl<string>;
  age: FormControl<number | null>;
  role: FormControl<'admin' | 'user'>;
}

@Component({ /* ... */ })
export class UserFormComponent {
  form = new FormGroup<UserForm>({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    age: new FormControl<number | null>(null),
    role: new FormControl<'admin' | 'user'>('user', { nonNullable: true })
  });

  onSubmit(): void {
    if (this.form.valid) {
      const value = this.form.getRawValue();
      // value is: { name: string; email: string; age: number | null; role: 'admin' | 'user' }
    }
  }
}
```

---

## TypeScript + React

### Functional Component Typing
```typescript
import { FC, ReactNode } from 'react';

// Interface for props
interface UserCardProps {
  user: User;
  showActions?: boolean;         // Optional
  onEdit: (user: User) => void;  // Callback
  onDelete: (id: number) => void;
  children?: ReactNode;          // Children content
}

// Method 1: FC type (includes children by default in older React)
const UserCard: FC<UserCardProps> = ({ user, showActions = true, onEdit, onDelete }) => {
  return (
    <div className="card">
      <h3>{user.name}</h3>
      <p>{user.email}</p>
      {showActions && (
        <div>
          <button onClick={() => onEdit(user)}>Edit</button>
          <button onClick={() => onDelete(user.id)}>Delete</button>
        </div>
      )}
    </div>
  );
};

// Method 2: Direct typing (preferred in modern React)
function UserCard2({ user, showActions = true, onEdit, onDelete }: UserCardProps) {
  return (/* JSX */);
}
```

### Hooks Typing
```typescript
import { useState, useEffect, useCallback, useMemo, useRef } from 'react';

function UserProfile({ userId }: { userId: number }) {
  // useState — type inferred or explicit
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(false);        // inferred: boolean
  const [error, setError] = useState<string | null>(null);
  const [count, setCount] = useState(0);                // inferred: number

  // useRef — typed reference
  const inputRef = useRef<HTMLInputElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  // useEffect — no return type needed
  useEffect(() => {
    let cancelled = false;
    setLoading(true);

    fetchUser(userId)
      .then(data => { if (!cancelled) setUser(data); })
      .catch(err => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [userId]);

  // useCallback — typed callback
  const handleSubmit = useCallback((event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    // ...
  }, []);

  // useMemo — type inferred from return
  const sortedUsers = useMemo(
    () => users.sort((a, b) => a.name.localeCompare(b.name)),
    [users]
  );

  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!user) return null;

  return <div>{user.name}</div>;
}
```

### Event Typing in React
```typescript
// Common event types
function EventExamples() {
  const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value: string = e.target.value;
  };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') { /* ... */ }
  };

  const handleFocus = (e: React.FocusEvent<HTMLInputElement>) => { /* ... */ };

  return (
    <form onSubmit={handleSubmit}>
      <input
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onFocus={handleFocus}
      />
      <button onClick={handleClick}>Submit</button>
    </form>
  );
}
```

### Generic Components
```typescript
// Generic list component
interface ListProps<T> {
  items: T[];
  renderItem: (item: T, index: number) => ReactNode;
  keyExtractor: (item: T) => string | number;
  emptyMessage?: string;
}

function List<T>({ items, renderItem, keyExtractor, emptyMessage }: ListProps<T>) {
  if (items.length === 0) {
    return <p>{emptyMessage ?? "No items"}</p>;
  }

  return (
    <ul>
      {items.map((item, index) => (
        <li key={keyExtractor(item)}>{renderItem(item, index)}</li>
      ))}
    </ul>
  );
}

// Usage — T inferred as User
<List
  items={users}
  renderItem={(user) => <span>{user.name}</span>}
  keyExtractor={(user) => user.id}
/>

// Generic select component
interface SelectProps<T> {
  options: T[];
  value: T | null;
  onChange: (value: T) => void;
  getLabel: (item: T) => string;
  getValue: (item: T) => string | number;
}

function Select<T>({ options, value, onChange, getLabel, getValue }: SelectProps<T>) {
  return (
    <select
      value={value ? String(getValue(value)) : ''}
      onChange={(e) => {
        const selected = options.find(o => String(getValue(o)) === e.target.value);
        if (selected) onChange(selected);
      }}
    >
      {options.map(option => (
        <option key={String(getValue(option))} value={String(getValue(option))}>
          {getLabel(option)}
        </option>
      ))}
    </select>
  );
}
```

### Context Typing
```typescript
import { createContext, useContext, ReactNode } from 'react';

interface AuthContextType {
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Custom hook with type narrowing
function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}

// Provider component
function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  const login = async (email: string, password: string): Promise<void> => {
    const user = await api.post<LoginRequest, User>('/auth/login', { email, password });
    setUser(user);
  };

  const logout = (): void => { setUser(null); };

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
}
```

---

## Full-Stack Type Flow ⭐⭐⭐

```
Spring Boot (Java)          TypeScript (Frontend)
─────────────────           ────────────────────
@Entity User                interface User { ... }
  ↓                           ↑
UserDTO                     type UserResponse
  ↓                           ↑
@RestController             ApiService.getUsers()
  ↓                           ↑
JSON Response    ──────→    HTTP Response
                            ↓
                          Component renders typed data
```

### Keeping Types in Sync
```typescript
// Define types that mirror your Spring Boot DTOs:

// Matches: com.app.dto.UserResponse
interface UserResponse {
  id: number;
  name: string;
  email: string;
  role: "ADMIN" | "USER" | "EDITOR";
  createdAt: string;  // LocalDateTime → ISO string in JSON
}

// Matches: com.app.dto.CreateUserRequest
interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  role: UserResponse["role"];
}

// Matches: com.app.dto.PageResponse<T>
interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;  // current page
}

// Matches: com.app.dto.ErrorResponse
interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
```

---

## Key Interview Questions

**Q: How do you type Angular services that call Spring Boot APIs?**
> Use generic types with `HttpClient`: `http.get<User[]>(url)` returns `Observable<User[]>`. Define interface matching the Spring Boot DTO (UserResponse, PageResponse\<T\>). Use Partial for updates, Omit for creates. The generic ensures autocomplete and type safety throughout the component → service → API chain.

**Q: How do you type React component props?**
> Define a props interface with all properties typed, mark optional ones with `?`, and destructure in the function signature: `function Card({ title, onClick }: CardProps)`. For generic components, use `function List<T>(props: ListProps<T>)`. Event handlers use React's event types like `React.MouseEvent<HTMLButtonElement>`.

**Q: What's the difference between `useState<User | null>(null)` and `useState<User>(undefined as any)`?**
> The first is correct — it explicitly models that the state starts as null and will eventually be User. The second uses `any` to bypass TypeScript, hiding potential null-reference bugs. Always model nullable state explicitly. Use `null` for "intentionally empty" and `undefined` for "not yet set."

**Q: How do you share types between Angular/React frontend and Node.js backend?**
> Options: (1) Shared package in a monorepo (Nx, Turborepo) — cleanest approach. (2) Auto-generate types from OpenAPI/Swagger spec (openapi-typescript). (3) Manual duplication with naming convention (match DTO names). For Spring Boot backend, auto-generate TypeScript types from the OpenAPI docs the backend exports.
