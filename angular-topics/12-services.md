# 12. Services

---

## Theory

Services are classes that encapsulate reusable business logic, data access, and state management. They're the primary way to share functionality across components.

### Creating Services

```typescript
@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private apiUrl = '/api/users';

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  createUser(user: CreateUserDTO): Observable<User> {
    return this.http.post<User>(this.apiUrl, user);
  }

  updateUser(id: number, user: UpdateUserDTO): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, user);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
```

### Singleton Services

```typescript
// providedIn: 'root' → one instance for entire app
@Injectable({ providedIn: 'root' })
export class AuthService {
  private currentUser = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUser.asObservable();
  
  isLoggedIn$ = this.currentUser$.pipe(map(user => !!user));

  login(credentials: LoginDTO): Observable<User> {
    return this.http.post<AuthResponse>('/api/auth/login', credentials).pipe(
      tap(response => {
        localStorage.setItem('token', response.token);
        this.currentUser.next(response.user);
      }),
      map(response => response.user)
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    this.currentUser.next(null);
  }
}
```

### State Management Through Services

```typescript
@Injectable({ providedIn: 'root' })
export class CartService {
  private items = new BehaviorSubject<CartItem[]>([]);
  
  readonly items$ = this.items.asObservable();
  readonly itemCount$ = this.items$.pipe(map(items => items.length));
  readonly total$ = this.items$.pipe(
    map(items => items.reduce((sum, item) => sum + item.price * item.quantity, 0))
  );

  addItem(product: Product, quantity = 1): void {
    const current = this.items.value;
    const existing = current.find(i => i.productId === product.id);
    
    if (existing) {
      this.items.next(
        current.map(i => i.productId === product.id
          ? { ...i, quantity: i.quantity + quantity }
          : i
        )
      );
    } else {
      this.items.next([...current, {
        productId: product.id,
        name: product.name,
        price: product.price,
        quantity
      }]);
    }
  }

  removeItem(productId: number): void {
    this.items.next(this.items.value.filter(i => i.productId !== productId));
  }

  clear(): void {
    this.items.next([]);
  }
}
```

### Service-to-Service Communication

```typescript
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private notifications = new Subject<AppNotification>();
  notifications$ = this.notifications.asObservable();

  show(message: string, type: 'success' | 'error' | 'info' = 'info'): void {
    this.notifications.next({ message, type, id: Date.now() });
  }
}

@Injectable({ providedIn: 'root' })
export class OrderService {
  private http = inject(HttpClient);
  private notification = inject(NotificationService);
  private cart = inject(CartService);

  placeOrder(order: CreateOrderDTO): Observable<Order> {
    return this.http.post<Order>('/api/orders', order).pipe(
      tap(result => {
        this.cart.clear();
        this.notification.show('Order placed successfully!', 'success');
      }),
      catchError(err => {
        this.notification.show('Failed to place order', 'error');
        throw err;
      })
    );
  }
}
```

### Service Lifecycle

| Provider Level | Instance Created | Instance Destroyed |
|---------------|-----------------|-------------------|
| `providedIn: 'root'` | First injection | App shutdown |
| Lazy module | Module loaded | Never (stays loaded) |
| Component | Component created | Component destroyed |

---

## Internal Working

### Service Instantiation

```
First inject(UserService):
1. Angular checks root injector for UserService
2. Not yet instantiated → reads @Injectable metadata
3. Resolves UserService's dependencies (HttpClient, etc.)
4. Creates UserService instance
5. Caches instance in injector
6. Returns instance

Subsequent inject(UserService):
1. Angular checks root injector → found cached instance
2. Returns same instance (singleton)
```

### BehaviorSubject State Pattern

```
CartService initialized:
  items BehaviorSubject = []
  
Component A subscribes to items$:
  → Immediately receives [] (current value)

User adds item:
  items.next([itemA])
  → Component A receives [itemA]

Component B subscribes to items$:
  → Immediately receives [itemA] (current value)

User adds another item:
  items.next([itemA, itemB])
  → Component A receives [itemA, itemB]
  → Component B receives [itemA, itemB]
```

---

## Diagram

```
Service Architecture Pattern:
┌──────────────────────────────────────────────┐
│ Components (UI Layer)                         │
│  ┌──────┐  ┌──────┐  ┌──────┐              │
│  │List  │  │Detail│  │Form  │              │
│  └──┬───┘  └──┬───┘  └──┬───┘              │
└─────┼─────────┼─────────┼───────────────────┘
      │         │         │
      ▼         ▼         ▼
┌──────────────────────────────────────────────┐
│ Services (Business Logic Layer)               │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │UserService│  │AuthService│  │CartService│ │
│  └─────┬────┘  └─────┬────┘  └─────┬─────┘ │
└────────┼──────────────┼─────────────┼────────┘
         │              │             │
         ▼              ▼             ▼
┌──────────────────────────────────────────────┐
│ HttpClient (Data Access Layer)                │
│  → REST API calls to Spring Boot backend     │
└──────────────────────────────────────────────┘
```

---

## Code

```typescript
// Complete service with caching, error handling, and state
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  private notification = inject(NotificationService);
  private apiUrl = '/api/employees';

  // Cached data with refresh capability
  private employeesCache$ = new BehaviorSubject<Employee[]>([]);
  private loading$ = new BehaviorSubject<boolean>(false);

  readonly employees$ = this.employeesCache$.asObservable();
  readonly isLoading$ = this.loading$.asObservable();

  loadEmployees(params?: EmployeeSearchParams): void {
    this.loading$.next(true);
    this.http.get<Employee[]>(this.apiUrl, { params: params as any }).pipe(
      finalize(() => this.loading$.next(false))
    ).subscribe({
      next: employees => this.employeesCache$.next(employees),
      error: err => this.notification.show('Failed to load employees', 'error')
    });
  }

  getEmployee(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.apiUrl}/${id}`);
  }

  createEmployee(dto: CreateEmployeeDTO): Observable<Employee> {
    return this.http.post<Employee>(this.apiUrl, dto).pipe(
      tap(newEmployee => {
        const current = this.employeesCache$.value;
        this.employeesCache$.next([...current, newEmployee]);
        this.notification.show('Employee created', 'success');
      })
    );
  }

  updateEmployee(id: number, dto: UpdateEmployeeDTO): Observable<Employee> {
    return this.http.put<Employee>(`${this.apiUrl}/${id}`, dto).pipe(
      tap(updated => {
        const current = this.employeesCache$.value;
        this.employeesCache$.next(
          current.map(e => e.id === id ? updated : e)
        );
        this.notification.show('Employee updated', 'success');
      })
    );
  }

  deleteEmployee(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
      tap(() => {
        const current = this.employeesCache$.value;
        this.employeesCache$.next(current.filter(e => e.id !== id));
        this.notification.show('Employee deleted', 'success');
      })
    );
  }
}

// Component using the service
@Component({
  selector: 'app-employee-page',
  standalone: true,
  imports: [CommonModule, AsyncPipe],
  template: `
    @if (isLoading$ | async) {
      <app-spinner />
    }
    @for (emp of employees$ | async; track emp.id) {
      <app-employee-card [employee]="emp" (delete)="onDelete($event)" />
    }
  `
})
export class EmployeePageComponent implements OnInit {
  private employeeService = inject(EmployeeService);
  
  employees$ = this.employeeService.employees$;
  isLoading$ = this.employeeService.isLoading$;

  ngOnInit(): void {
    this.employeeService.loadEmployees();
  }

  onDelete(id: number): void {
    this.employeeService.deleteEmployee(id).subscribe();
  }
}
```

---

## Dry Run

### Service State Update Flow

```
Initial state: employeesCache$ = [{id:1, name:'John'}, {id:2, name:'Jane'}]

User creates new employee:
Step 1: Component calls employeeService.createEmployee({name: 'Bob'})
Step 2: HTTP POST /api/employees → returns {id:3, name:'Bob'}
Step 3: tap() fires:
  - current = [{id:1}, {id:2}]
  - employeesCache$.next([{id:1}, {id:2}, {id:3, name:'Bob'}])
  - notification.show('Employee created', 'success')
Step 4: All subscribed components receive updated array
Step 5: @for re-renders with 3 items (track by id → reuses existing 2 nodes)
```

---

## Interview Questions and Answers

**Q1: What are Angular services and why are they important?**
> Services are singleton classes that encapsulate business logic, data access, and shared state. They separate concerns — components handle UI, services handle logic. This makes code reusable (multiple components use same service), testable (mock services in tests), and maintainable (centralized logic).

**Q2: How do you share data between components using a service?**
> Use a BehaviorSubject in the service. One component calls a method to update state (e.g., `service.selectUser(user)`), which calls `subject.next(value)`. Other components subscribe to the exposed Observable (`service.selectedUser$`) and receive updates reactively. AsyncPipe handles subscription/cleanup.

**Q3: What is the difference between a service and a component?**
> Components handle UI (template, styles, user interaction). Services handle business logic (HTTP calls, data transformation, state management). Components are tied to the DOM; services are pure TypeScript classes. A component should delegate all non-UI work to services.

**Q4: Can a service inject another service?**
> Yes. Services can inject other services via DI. Example: OrderService injects HttpClient, CartService, and NotificationService. There's no limit to nesting, but avoid circular dependencies (A→B→A). If circular, extract shared logic to a third service.

---

## Common Mistakes

1. **Putting HTTP logic in components**
   ```typescript
   // ❌ Component handles HTTP directly
   export class UserListComponent {
     ngOnInit() { this.http.get('/api/users').subscribe(...); }
   }
   
   // ✅ Delegate to service
   export class UserListComponent {
     users$ = inject(UserService).getUsers();
   }
   ```

2. **Not exposing Observable from BehaviorSubject**
   ```typescript
   // ❌ Exposes Subject — consumers can call .next()
   public users = new BehaviorSubject<User[]>([]);
   
   // ✅ Expose as Observable (read-only)
   private usersSubject = new BehaviorSubject<User[]>([]);
   readonly users$ = this.usersSubject.asObservable();
   ```

3. **Subscribing inside services without cleanup**
   ```typescript
   // ❌ Root service subscription never ends
   constructor() { this.http.get(...).subscribe(); }
   
   // ✅ Use tap() within pipe for side effects
   // Or if long-lived subscription needed, track and clean up
   ```

---

## Best Practices

1. **One responsibility per service** — UserService, AuthService, NotificationService.
2. **Use BehaviorSubject** for state that components need current value on subscribe.
3. **Expose Observables** (readonly), keep Subjects private.
4. **Return Observables** from data methods — let consumers subscribe.
5. **Use `tap()`** for side effects in Observable chains.
6. **Prefer `providedIn: 'root'`** for most services.
7. **Keep services thin** — orchestrate, don't accumulate all logic.
8. **Name services descriptively** — `EmployeeService`, not `DataService`.

---

## Production Considerations

- **Caching**: Use `shareReplay(1)` for expensive HTTP calls that multiple components need.
- **Error handling**: Centralize error handling in services or interceptors.
- **State size**: BehaviorSubject holds value in memory — large datasets should use pagination.
- **Memory**: Root services live forever — clean up internal subscriptions if they accumulate.
- **Testing**: Mock services with `jasmine.createSpyObj` or jest mocks for component tests.

---

## Related Topics

- → [11. Dependency Injection](./11-dependency-injection.md)
- → [17. RxJS](./17-rxjs.md)
- → [20. HTTP Client](./20-http-client.md)
- → [27. State Management](./27-state-management.md)
