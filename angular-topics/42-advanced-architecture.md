# 42. Advanced Angular Architecture

---

## Theory

Scalable Angular architecture for large applications follows domain-driven organization, smart/presentational patterns, and clear module boundaries.

### Feature-Based Architecture

```
src/app/
├── core/                          # Singletons, app-wide concerns
│   ├── guards/
│   ├── interceptors/
│   ├── services/
│   └── models/
├── shared/                        # Reusable UI components
│   ├── components/ (button, modal, table, pagination)
│   ├── directives/
│   └── pipes/
├── features/                      # Domain features
│   ├── auth/
│   │   ├── login/
│   │   ├── register/
│   │   └── services/
│   ├── employees/
│   │   ├── employee-list/
│   │   ├── employee-detail/
│   │   ├── employee-form/
│   │   ├── services/
│   │   └── employees.routes.ts
│   └── orders/
│       ├── order-list/
│       ├── order-detail/
│       ├── services/
│       └── orders.routes.ts
├── app.component.ts
├── app.config.ts
└── app.routes.ts
```

### Smart/Container vs Presentational

```typescript
// SMART (Container) — manages state, injects services
@Component({
  selector: 'app-employee-page',
  template: `
    <app-employee-filters (filterChange)="onFilter($event)" />
    <app-employee-table 
      [employees]="employees"
      [loading]="loading"
      (edit)="onEdit($event)"
      (delete)="onDelete($event)" />
    <app-pagination [page]="page" [total]="totalPages" (pageChange)="onPage($event)" />
  `
})
export class EmployeePageComponent {
  private store = inject(EmployeeStore);
  employees = this.store.filteredEmployees;
  loading = this.store.loading;
  // Delegates to store, no direct HTTP
}

// PRESENTATIONAL (Dumb) — pure display, only @Input/@Output
@Component({
  selector: 'app-employee-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `...`
})
export class EmployeeTableComponent {
  @Input({ required: true }) employees!: Employee[];
  @Input() loading = false;
  @Output() edit = new EventEmitter<Employee>();
  @Output() delete = new EventEmitter<number>();
  // No services injected, no business logic
}
```

### Facade Pattern

```typescript
// Facade simplifies complex subsystem interactions
@Injectable({ providedIn: 'root' })
export class EmployeeFacade {
  private api = inject(EmployeeApiService);
  private store = inject(EmployeeStore);
  private notification = inject(NotificationService);
  private router = inject(Router);

  // Expose state
  readonly employees$ = this.store.employees$;
  readonly loading$ = this.store.loading$;
  readonly selectedEmployee$ = this.store.selected$;

  // Expose actions (encapsulate orchestration)
  loadEmployees(params?: SearchParams): void {
    this.store.setLoading(true);
    this.api.getEmployees(params).subscribe({
      next: data => this.store.setEmployees(data),
      error: () => this.notification.error('Failed to load'),
      complete: () => this.store.setLoading(false)
    });
  }

  createEmployee(dto: CreateEmployeeDTO): void {
    this.api.create(dto).subscribe({
      next: emp => {
        this.store.addEmployee(emp);
        this.notification.success('Employee created');
        this.router.navigate(['/employees', emp.id]);
      },
      error: () => this.notification.error('Creation failed')
    });
  }
}

// Component becomes very thin
@Component({ ... })
export class EmployeeListComponent implements OnInit {
  facade = inject(EmployeeFacade);
  ngOnInit() { this.facade.loadEmployees(); }
}
```

### Barrel Files (index.ts)

```typescript
// features/employees/index.ts
export { EmployeeListComponent } from './employee-list/employee-list.component';
export { EmployeeDetailComponent } from './employee-detail/employee-detail.component';
export { EmployeeService } from './services/employee.service';
export { EMPLOYEE_ROUTES } from './employees.routes';

// Usage: import { EmployeeService } from '@features/employees';
```

### Path Aliases (tsconfig.json)

```json
{
  "compilerOptions": {
    "paths": {
      "@app/*": ["src/app/*"],
      "@core/*": ["src/app/core/*"],
      "@shared/*": ["src/app/shared/*"],
      "@features/*": ["src/app/features/*"],
      "@env": ["src/environments/environment"]
    }
  }
}
// Usage: import { AuthService } from '@core/services/auth.service';
```

---

## Internal Working

### Smart/Presentational Communication Flow

```
Smart Component (EmployeePageComponent):
  1. Injects EmployeeFacade (or Store)
  2. Calls facade.loadEmployees() on init
  3. Subscribes to facade.employees$ / reads signal
  4. Receives data → passes to presentational children via @Input
  5. Listens to @Output events from children
  6. Delegates actions back to facade

Presentational Component (EmployeeTableComponent):
  1. Receives data via @Input (employees, loading state)
  2. Renders data — pure display logic only
  3. User interacts → emits @Output events
  4. Has NO knowledge of where data comes from or what happens next
  5. Can be reused in different contexts with different data sources

Key benefits:
  - Presentational components are trivially testable (just set inputs, check outputs)
  - Smart components can be tested by mocking the facade
  - Presentational components are reusable across features
  - Clear separation makes onboarding new developers easier
```

### Facade Pattern Internals

```
Without Facade:
  Component → UserApiService (HTTP)
  Component → UserStateService (BehaviorSubject)
  Component → NotificationService (toast)
  Component → Router (navigation)
  Component → LoggerService (logging)
  
  Component has 5 dependencies, complex orchestration logic

With Facade:
  Component → UserFacade (single dependency)
  
  UserFacade → UserApiService (HTTP)
  UserFacade → UserStateService (state)
  UserFacade → NotificationService (toast)
  UserFacade → Router (navigation)
  UserFacade → LoggerService (logging)

  Component is thin: just reads state + calls actions
  Facade centralizes orchestration
  Testing: mock only the facade
```

---

## Diagram

```
Feature-Based Architecture:

src/app/
├── core/                          # App-wide singletons
│   ├── guards/auth.guard.ts
│   ├── interceptors/
│   │   ├── auth.interceptor.ts
│   │   └── error.interceptor.ts
│   ├── services/
│   │   ├── auth.service.ts       # Authentication state
│   │   ├── notification.service.ts
│   │   └── storage.service.ts
│   └── models/
│       ├── user.model.ts
│       └── api-response.model.ts
│
├── shared/                        # Reusable UI (no business logic)
│   ├── components/
│   │   ├── button/
│   │   ├── modal/
│   │   ├── pagination/
│   │   ├── data-table/
│   │   └── confirm-dialog/
│   ├── directives/
│   │   ├── click-outside.directive.ts
│   │   └── debounce-click.directive.ts
│   └── pipes/
│       ├── truncate.pipe.ts
│       └── time-ago.pipe.ts
│
├── features/                      # Domain-specific
│   ├── employees/
│   │   ├── components/            # Presentational
│   │   │   ├── employee-card/
│   │   │   ├── employee-table/
│   │   │   └── employee-filters/
│   │   ├── pages/                 # Smart/Container
│   │   │   ├── employee-list-page/
│   │   │   └── employee-detail-page/
│   │   ├── services/
│   │   │   ├── employee-api.service.ts
│   │   │   └── employee.store.ts  # Feature state
│   │   ├── models/
│   │   │   └── employee.model.ts
│   │   └── employees.routes.ts
│   │
│   └── orders/
│       ├── components/
│       ├── pages/
│       ├── services/
│       └── orders.routes.ts
│
├── app.component.ts
├── app.config.ts
└── app.routes.ts


Data Flow in Feature:
┌────────────────────────────────────────────────────────┐
│ Page (Smart Component)                                  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ inject(EmployeeStore)                             │  │
│  │ employees = store.filteredEmployees               │  │
│  │ loading = store.loading                           │  │
│  └──────────────┬───────────────────────────────────┘  │
│                  │ @Input                                │
│  ┌───────────────▼──────────────────────────────────┐  │
│  │ Table (Presentational)          @Output           │  │
│  │ @Input() employees              (edit)="..."      │  │
│  │ @Input() loading                (delete)="..."    │  │
│  └──────────────────────────────────────────────────┘  │
│                  │ Action                                │
│  ┌───────────────▼──────────────────────────────────┐  │
│  │ EmployeeStore                                     │  │
│  │ → EmployeeApiService (HTTP)                       │  │
│  │ → NotificationService (toast)                     │  │
│  │ → signal/BehaviorSubject (state)                  │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## Code

```typescript
// Complete feature architecture: Employee Management

// 1. Model
export interface Employee {
  id: number;
  name: string;
  email: string;
  department: string;
  salary: number;
  joinDate: string;
}

// 2. API Service (pure HTTP, no state)
@Injectable({ providedIn: 'root' })
export class EmployeeApiService {
  private http = inject(HttpClient);
  private baseUrl = '/api/employees';

  getAll(params?: HttpParams): Observable<PaginatedResponse<Employee>> {
    return this.http.get<PaginatedResponse<Employee>>(this.baseUrl, { params });
  }
  getById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }
  create(dto: Omit<Employee, 'id'>): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, dto);
  }
  update(id: number, dto: Partial<Employee>): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, dto);
  }
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}

// 3. Feature Store (state + orchestration)
@Injectable({ providedIn: 'root' })
export class EmployeeStore {
  private api = inject(EmployeeApiService);
  private notification = inject(NotificationService);
  private router = inject(Router);

  // Private state
  private state = signal<{
    employees: Employee[];
    selected: Employee | null;
    loading: boolean;
    error: string | null;
    filters: { search: string; department: string };
    pagination: { page: number; totalPages: number; totalElements: number };
  }>({
    employees: [], selected: null, loading: false, error: null,
    filters: { search: '', department: '' },
    pagination: { page: 0, totalPages: 0, totalElements: 0 }
  });

  // Public selectors
  readonly employees = computed(() => this.state().employees);
  readonly selected = computed(() => this.state().selected);
  readonly loading = computed(() => this.state().loading);
  readonly error = computed(() => this.state().error);
  readonly pagination = computed(() => this.state().pagination);
  
  readonly filteredEmployees = computed(() => {
    const { employees, filters } = this.state();
    return employees.filter(e =>
      (!filters.search || e.name.toLowerCase().includes(filters.search.toLowerCase())) &&
      (!filters.department || e.department === filters.department)
    );
  });

  // Actions
  load(page = 0): void {
    this.patch({ loading: true, error: null });
    const params = new HttpParams().set('page', page).set('size', '20');
    this.api.getAll(params).subscribe({
      next: res => this.patch({
        employees: res.content,
        pagination: { page: res.number, totalPages: res.totalPages, totalElements: res.totalElements },
        loading: false
      }),
      error: err => this.patch({ error: 'Failed to load employees', loading: false })
    });
  }

  create(dto: Omit<Employee, 'id'>): void {
    this.api.create(dto).subscribe({
      next: emp => {
        this.patch({ employees: [...this.state().employees, emp] });
        this.notification.success('Employee created');
        this.router.navigate(['/employees', emp.id]);
      },
      error: () => this.notification.error('Failed to create employee')
    });
  }

  delete(id: number): void {
    this.api.delete(id).subscribe({
      next: () => {
        this.patch({ employees: this.state().employees.filter(e => e.id !== id) });
        this.notification.success('Employee deleted');
      },
      error: () => this.notification.error('Failed to delete employee')
    });
  }

  setFilters(filters: Partial<{ search: string; department: string }>): void {
    this.patch({ filters: { ...this.state().filters, ...filters } });
  }

  select(employee: Employee | null): void {
    this.patch({ selected: employee });
  }

  private patch(partial: Partial<typeof this.state extends Signal<infer T> ? T : never>): void {
    this.state.update(s => ({ ...s, ...partial } as any));
  }
}

// 4. Smart Page Component (thin — just connects store to UI)
@Component({
  selector: 'app-employee-list-page',
  standalone: true,
  imports: [EmployeeTableComponent, EmployeeFiltersComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Employees</h1>
    <app-employee-filters (filterChange)="store.setFilters($event)" />
    <app-employee-table
      [employees]="store.filteredEmployees()"
      [loading]="store.loading()"
      (edit)="onEdit($event)"
      (delete)="store.delete($event)" />
    <app-pagination
      [page]="store.pagination().page"
      [totalPages]="store.pagination().totalPages"
      (pageChange)="store.load($event)" />
  `
})
export class EmployeeListPageComponent implements OnInit {
  store = inject(EmployeeStore);
  private router = inject(Router);

  ngOnInit(): void { this.store.load(); }
  onEdit(emp: Employee): void { this.router.navigate(['/employees', emp.id, 'edit']); }
}

// 5. Presentational Component (pure display, no services)
@Component({
  selector: 'app-employee-table',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading) { <app-skeleton-rows [count]="5" /> }
    @else {
      <table>
        <thead><tr><th>Name</th><th>Email</th><th>Department</th><th>Salary</th><th>Actions</th></tr></thead>
        <tbody>
          @for (emp of employees; track emp.id) {
            <tr>
              <td>{{ emp.name }}</td>
              <td>{{ emp.email }}</td>
              <td>{{ emp.department }}</td>
              <td>{{ emp.salary | currency }}</td>
              <td>
                <button (click)="edit.emit(emp)">Edit</button>
                <button (click)="delete.emit(emp.id)">Delete</button>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5">No employees found</td></tr>
          }
        </tbody>
      </table>
    }
  `
})
export class EmployeeTableComponent {
  @Input({ required: true }) employees!: Employee[];
  @Input() loading = false;
  @Output() edit = new EventEmitter<Employee>();
  @Output() delete = new EventEmitter<number>();
}
```

---

## Dry Run

### Feature Data Flow

```
User navigates to /employees:

Step 1: EmployeeListPageComponent.ngOnInit() → store.load()
Step 2: Store sets loading=true, calls api.getAll()
Step 3: HTTP GET /api/employees → Spring Boot returns paginated data
Step 4: Store sets employees=[...], loading=false, pagination={...}
Step 5: Template reads store.filteredEmployees() → computed recalculates
Step 6: [employees]="store.filteredEmployees()" → @Input on EmployeeTableComponent
Step 7: Table renders rows

User types 'john' in search filter:
Step 8: EmployeeFiltersComponent emits filterChange({ search: 'john' })
Step 9: Page handles: store.setFilters({ search: 'john' })
Step 10: Store patches filters.search = 'john'
Step 11: filteredEmployees computed re-evaluates (includes 'john' check)
Step 12: Only employees with 'john' in name returned
Step 13: Table @Input receives new array → re-renders filtered list

User clicks Delete on employee id=5:
Step 14: Table emits delete(5)
Step 15: Page handles: store.delete(5)
Step 16: Store calls api.delete(5) → HTTP DELETE /api/employees/5
Step 17: 204 No Content → Store patches employees (removes id=5)
Step 18: notification.success('Employee deleted')
Step 19: filteredEmployees recomputes → employee gone from list
Step 20: Table re-renders without that row
```

---

## Interview Questions and Answers

**Q1: How do you structure a large Angular application?**
> Feature-based architecture: `core/` (singletons, guards, interceptors), `shared/` (reusable UI), `features/` (domain modules with own routes, components, services). Each feature is self-contained and lazy-loaded. Within features: separate pages (smart) from components (presentational). Use store/facade per feature for state management.

**Q2: What is the Facade pattern in Angular?**
> A Facade sits between components and complex subsystems (API service, store, notification, router). It exposes a simplified API — components call one method instead of orchestrating multiple services. Benefits: thin components (easy to test), centralized orchestration logic (easy to change), single dependency to mock in tests.

**Q3: What is the difference between core and shared?**
> Core: app-wide singletons (AuthService, interceptors, guards) — things that exist ONCE and provide infrastructure. Shared: reusable UI building blocks (components, pipes, directives) — things that are IMPORTED by many features. Core provides services; Shared provides presentation. With standalone + providedIn, the distinction matters less but the organizational clarity remains valuable.

**Q4: How do you decide where to put a component?**
> Is it used in only ONE feature? → `features/[name]/components/`. Is it used across multiple features? → `shared/components/`. Is it the main page view? → `features/[name]/pages/`. Does it have zero business logic, only @Input/@Output? → Presentational (components folder). Does it inject services? → Smart (pages folder).

**Q5: What are barrel files and when should you use them?**
> Barrel files (index.ts) re-export multiple modules from a directory: `export { UserService } from './user.service'`. They enable clean imports: `import { UserService } from '@features/users'`. Use for public API of a feature. Avoid for internal files. Be careful of barrel files causing tree-shaking issues (pulling in entire features).

---

## Follow-up Questions and Answers

**Q: How do you handle cross-feature communication?**
> Option 1: Shared service at root level (BehaviorSubject/Signal). Option 2: Router events/query params (URL state). Option 3: NgRx global store (enterprise). Avoid direct imports between features — creates coupling. Features communicate through shared abstractions (services, events, URL), not by importing each other's internals.

**Q: How does Nx help with Angular architecture?**
> Nx is a monorepo tool that enforces architecture boundaries. Define libraries (feature-lib, ui-lib, data-access-lib, util-lib) with strict dependency rules. Nx linting prevents feature A from importing feature B's internals. It provides affected builds (only rebuild changed libraries), shared code management, and generator schematics for consistency.

---

## Common Mistakes

1. **Smart component does everything (god component)**
   ```typescript
   // ❌ 500-line component with HTTP, state, routing, validation
   // ✅ Extract: ApiService, Store/Facade, presentational children
   ```

2. **Shared components with business logic**
   ```typescript
   // ❌ SharedModule's DataTable knows about Employee model
   // ✅ Generic: @Input() columns, @Input() data, @Output() rowClick
   ```

3. **Feature A imports Feature B's components directly**
   ```typescript
   // ❌ Tight coupling — can't deploy features independently
   import { OrderCard } from '../orders/components/order-card';
   
   // ✅ Move shared UI to shared/, or communicate via service
   ```

4. **Circular dependencies between features**

---

## Best Practices

1. **Feature-based structure** — group by domain, not by type.
2. **Smart/presentational split** — thin containers, reusable presentations.
3. **Lazy load features** — each feature is a separate route chunk.
4. **Store/Facade per feature** — encapsulate state management.
5. **Path aliases** — clean imports (`@app`, `@core`, `@shared`, `@features`).
6. **One component per file** — Angular CLI convention.
7. **Keep shared components generic** — no feature-specific logic.
8. **Enforce boundaries** — use Nx or ESLint rules to prevent cross-feature imports.

---

## Production Considerations

- **Code ownership**: Each feature can be owned by a team (aligns with org structure).
- **Independent deployment**: Lazy-loaded features can be deployed separately (micro-frontends).
- **Build performance**: Nx affected builds only rebuild changed features.
- **Onboarding**: New developers understand one feature without knowing the whole app.
- **Refactoring**: Well-isolated features can be rewritten without affecting others.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
- → [15. Lazy Loading](./15-lazy-loading.md)
- → [27. State Management](./27-state-management.md)
- → [43. Micro Frontends](./43-micro-frontends.md)
