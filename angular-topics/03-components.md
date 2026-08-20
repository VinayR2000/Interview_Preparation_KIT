# 3. Components — Extremely Important

---

## Theory

Components are the **fundamental building blocks** of Angular applications. Every Angular app has at least one component (the root component). A component controls a portion of the screen called a **view**.

### Component Anatomy

A component consists of three parts:
1. **Class** (TypeScript) — logic, data, event handlers
2. **Template** (HTML) — view structure with Angular syntax
3. **Styles** (CSS/SCSS) — scoped appearance

### @Component Decorator

```typescript
@Component({
  selector: 'app-user-card',        // HTML tag name
  standalone: true,                  // Self-contained (modern Angular)
  imports: [CommonModule, RouterLink], // Dependencies
  templateUrl: './user-card.component.html',  // External template
  // template: `<h1>Inline</h1>`,   // OR inline template
  styleUrls: ['./user-card.component.css'],   // External styles
  // styles: [`h1 { color: red; }`], // OR inline styles
  changeDetection: ChangeDetectionStrategy.OnPush, // Performance
  encapsulation: ViewEncapsulation.Emulated  // Style scoping
})
export class UserCardComponent {
  // Component logic
}
```

### Selector Types

```typescript
// Element selector (most common)
selector: 'app-user-card'        // Usage: <app-user-card></app-user-card>

// Attribute selector
selector: '[appHighlight]'       // Usage: <div appHighlight></div>

// Class selector (rare)
selector: '.app-widget'          // Usage: <div class="app-widget"></div>
```

### Standalone Components (Modern — Angular 14+)

```typescript
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule,          // NgIf, NgFor, pipes
    ReactiveFormsModule,   // Reactive forms
    RouterLink,            // Router directives
    UserCardComponent,     // Other standalone components
    LoadingSpinnerComponent
  ],
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.css']
})
export class UserListComponent {
  users: User[] = [];
}
```

### NgModule-Based Components (Legacy)

```typescript
// user-card.component.ts — no standalone, no imports
@Component({
  selector: 'app-user-card',
  templateUrl: './user-card.component.html',
  styleUrls: ['./user-card.component.css']
})
export class UserCardComponent { }

// user.module.ts — must declare here
@NgModule({
  declarations: [UserCardComponent, UserListComponent],
  imports: [CommonModule, ReactiveFormsModule],
  exports: [UserCardComponent] // To use in other modules
})
export class UserModule { }
```

### Smart vs Presentational Components

| Aspect | Smart (Container) | Presentational (Dumb) |
|--------|-------------------|----------------------|
| Purpose | Manages data and state | Displays data |
| Dependencies | Services, store, router | Only @Input/@Output |
| Template | Orchestrates children | Pure UI rendering |
| Reusability | Low (app-specific) | High (generic) |
| Testing | Integration tests | Unit tests (easy) |
| Example | UserListPage | UserCard, Button, Modal |

```typescript
// Smart component — fetches data, handles business logic
@Component({
  selector: 'app-user-list-page',
  standalone: true,
  imports: [UserCardComponent, LoadingComponent],
  template: `
    @if (loading) {
      <app-loading />
    } @else {
      @for (user of users; track user.id) {
        <app-user-card 
          [user]="user" 
          (delete)="onDelete($event)" />
      }
    }
  `
})
export class UserListPageComponent implements OnInit {
  users: User[] = [];
  loading = true;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.userService.getUsers().subscribe(users => {
      this.users = users;
      this.loading = false;
    });
  }

  onDelete(userId: number): void {
    this.userService.deleteUser(userId).subscribe(() => {
      this.users = this.users.filter(u => u.id !== userId);
    });
  }
}

// Presentational component — only receives data and emits events
@Component({
  selector: 'app-user-card',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="card">
      <h3>{{ user.name }}</h3>
      <p>{{ user.email }}</p>
      <p>Joined: {{ user.joinDate | date:'mediumDate' }}</p>
      <button (click)="delete.emit(user.id)">Delete</button>
    </div>
  `
})
export class UserCardComponent {
  @Input({ required: true }) user!: User;
  @Output() delete = new EventEmitter<number>();
}
```

### Component Communication Patterns

```
Parent → Child:        @Input()
Child → Parent:        @Output() + EventEmitter
Sibling ↔ Sibling:    Shared Service (or Signals/BehaviorSubject)
Any ↔ Any:            Shared Service / State Management
```

### View Encapsulation

```typescript
@Component({
  encapsulation: ViewEncapsulation.Emulated   // Default — scoped via attributes
  // ViewEncapsulation.None      — No scoping — global styles
  // ViewEncapsulation.ShadowDom — Native Shadow DOM (true isolation)
})
```

| Mode | Behavior | Use Case |
|------|----------|----------|
| Emulated | Adds `_ngcontent-xxx` attributes to scope CSS | Default — works everywhere |
| None | Styles bleed globally | Theme styles, overrides |
| ShadowDom | True browser isolation | Web components, strict isolation |

---

## Internal Working

### How Angular Creates Components

```
1. Angular encounters <app-user-card> in parent template
       ↓
2. Looks up component factory for selector 'app-user-card'
       ↓
3. Creates component instance:
   a. Resolves constructor dependencies (DI)
   b. Creates the component class instance
   c. Sets @Input() values from parent bindings
       ↓
4. Calls lifecycle hooks:
   constructor → ngOnChanges → ngOnInit → ngDoCheck → 
   ngAfterContentInit → ngAfterViewInit
       ↓
5. Renders template: evaluates bindings, creates DOM nodes
       ↓
6. Attaches rendered DOM to parent's view
       ↓
7. Change detection monitors for updates
```

### View Encapsulation Internals

```html
<!-- Emulated (default) — Angular adds attribute selectors -->
<!-- Component: app-user-card -->
<div _ngcontent-abc-1 class="card">
  <h3 _ngcontent-abc-1>John</h3>
</div>

<!-- Generated CSS -->
.card[_ngcontent-abc-1] { border: 1px solid #ccc; }

<!-- ShadowDom — uses native Shadow DOM -->
<app-user-card>
  #shadow-root
    <div class="card">
      <h3>John</h3>
    </div>
</app-user-card>
```

### Component Tree and Change Detection

```
AppComponent
├── HeaderComponent
├── SidebarComponent
└── MainContentComponent
    ├── UserListComponent (OnPush)
    │   ├── UserCardComponent
    │   └── UserCardComponent
    └── FooterComponent

Change Detection Flow (Default):
App → Header → Sidebar → Main → UserList → UserCard → UserCard → Footer
(checks every component on every event)

Change Detection Flow (OnPush on UserList):
App → Header → Sidebar → Main → [Skip UserList if inputs unchanged] → Footer
```

---

## Diagram

```
Component Lifecycle:
┌────────────────────────────────────────────────────────┐
│                                                         │
│  constructor()          ← DI resolution                │
│       ↓                                                │
│  ngOnChanges()          ← @Input values set/changed    │
│       ↓                                                │
│  ngOnInit()             ← Initialization logic         │
│       ↓                                                │
│  ngDoCheck()            ← Custom change detection      │
│       ↓                                                │
│  ngAfterContentInit()   ← <ng-content> projected       │
│       ↓                                                │
│  ngAfterContentChecked()                               │
│       ↓                                                │
│  ngAfterViewInit()      ← View (children) rendered     │
│       ↓                                                │
│  ngAfterViewChecked()                                  │
│       ↓                                                │
│  [Component lives — responds to changes]               │
│       ↓                                                │
│  ngOnDestroy()          ← Cleanup (unsubscribe, etc.)  │
│                                                         │
└────────────────────────────────────────────────────────┘
```

```
Component Communication:
┌─────────────────────────────────────┐
│        Parent Component              │
│                                      │
│  [data]="value"    (event)="fn($e)" │
│       │                    ↑         │
│       ↓ @Input()    @Output() ↑      │
│  ┌────────────────────────────────┐  │
│  │       Child Component          │  │
│  │                                │  │
│  │  Receives data   Emits events  │  │
│  └────────────────────────────────┘  │
└─────────────────────────────────────┘
```

---

## Code

```typescript
// Complete component example with all common patterns
import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, 
         ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';

interface Employee {
  id: number;
  name: string;
  email: string;
  department: string;
  avatar?: string;
}

@Component({
  selector: 'app-employee-card',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card" [class.selected]="isSelected">
      <img [src]="employee.avatar ?? 'assets/default-avatar.png'" 
           [alt]="employee.name + ' avatar'" />
      <div class="info">
        <h3>{{ employee.name }}</h3>
        <p>{{ employee.email }}</p>
        <span class="badge">{{ employee.department }}</span>
      </div>
      <div class="actions">
        <button (click)="onEdit()">Edit</button>
        <button (click)="onDelete()" class="danger">Delete</button>
      </div>
    </div>
  `,
  styles: [`
    .card { display: flex; padding: 16px; border: 1px solid #e0e0e0; border-radius: 8px; }
    .card.selected { border-color: #1976d2; background: #e3f2fd; }
    .badge { background: #e8f5e9; padding: 4px 8px; border-radius: 4px; }
    .danger { color: #d32f2f; }
  `]
})
export class EmployeeCardComponent {
  @Input({ required: true }) employee!: Employee;
  @Input() isSelected = false;
  
  @Output() edit = new EventEmitter<Employee>();
  @Output() delete = new EventEmitter<number>();

  onEdit(): void {
    this.edit.emit(this.employee);
  }

  onDelete(): void {
    this.delete.emit(this.employee.id);
  }
}

// Container component using the card
@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [CommonModule, EmployeeCardComponent],
  template: `
    <div class="list">
      <h2>Employees ({{ employees.length }})</h2>
      @for (emp of employees; track emp.id) {
        <app-employee-card
          [employee]="emp"
          [isSelected]="selectedId === emp.id"
          (edit)="onEdit($event)"
          (delete)="onDelete($event)" />
      } @empty {
        <p>No employees found.</p>
      }
    </div>
  `
})
export class EmployeeListComponent implements OnInit, OnDestroy {
  private employeeService = inject(EmployeeService);
  private destroy$ = new Subject<void>();

  employees: Employee[] = [];
  selectedId: number | null = null;

  ngOnInit(): void {
    this.employeeService.getEmployees()
      .pipe(takeUntil(this.destroy$))
      .subscribe(employees => this.employees = employees);
  }

  onEdit(employee: Employee): void {
    this.selectedId = employee.id;
    // Navigate to edit page or open dialog
  }

  onDelete(id: number): void {
    this.employeeService.deleteEmployee(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.employees = this.employees.filter(e => e.id !== id);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

---

## Dry Run

### Component Rendering

```
Template:
@for (emp of employees; track emp.id) {
  <app-employee-card [employee]="emp" (delete)="onDelete($event)" />
}

Data: employees = [
  { id: 1, name: 'John', email: 'j@t.com', department: 'Engineering' },
  { id: 2, name: 'Jane', email: 'jane@t.com', department: 'Marketing' }
]

Step 1: @for iterates over employees array
Step 2: For id=1: creates EmployeeCardComponent, sets employee input
Step 3: EmployeeCardComponent renders with John's data
Step 4: For id=2: creates another EmployeeCardComponent, sets employee input  
Step 5: EmployeeCardComponent renders with Jane's data
Step 6: track emp.id — Angular uses id for DOM reuse on re-renders

When user clicks Delete on John's card:
Step 7: (delete) event fires → onDelete(1) called
Step 8: employees filtered → [{ id: 2, name: 'Jane', ... }]
Step 9: Change detection runs → John's card removed from DOM
Step 10: Jane's card is reused (same trackBy id)
```

---

## Complexity

| Operation | Performance |
|-----------|-------------|
| Component creation | O(1) — DI cached, template pre-compiled (AOT) |
| @for rendering | O(n) — n = list items |
| @for with trackBy | O(k) — k = changed items (DOM reuse) |
| Change detection (Default) | O(bindings in subtree) |
| Change detection (OnPush) | O(1) if inputs unchanged (skipped) |

---

## Real Project Usage

```typescript
// Real-world pattern: Feature component with loading/error states
@Component({
  selector: 'app-order-dashboard',
  standalone: true,
  imports: [CommonModule, OrderTableComponent, LoadingComponent, ErrorComponent],
  template: `
    @if (loading) {
      <app-loading message="Loading orders..." />
    } @else if (error) {
      <app-error [message]="error" (retry)="loadOrders()" />
    } @else {
      <app-order-table 
        [orders]="orders" 
        [sortColumn]="sortColumn"
        (sort)="onSort($event)"
        (select)="onOrderSelect($event)" />
    }
  `
})
export class OrderDashboardComponent implements OnInit {
  private orderService = inject(OrderService);
  
  orders: Order[] = [];
  loading = true;
  error: string | null = null;
  sortColumn = 'date';

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading = true;
    this.error = null;
    this.orderService.getOrders().subscribe({
      next: orders => { this.orders = orders; this.loading = false; },
      error: err => { this.error = err.message; this.loading = false; }
    });
  }

  onSort(column: string): void { this.sortColumn = column; }
  onOrderSelect(order: Order): void { /* navigate to detail */ }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between standalone and NgModule-based components?**
> Standalone components (Angular 14+) are self-contained — they declare their dependencies directly in `@Component({ imports: [...] })`. They don't need to be declared in any NgModule. NgModule components must be in a module's `declarations` and rely on the module's `imports`. Standalone is the recommended approach for all new Angular development.

**Q2: Explain smart vs presentational components.**
> Smart (container) components handle data fetching, state management, and business logic. They inject services and coordinate child components. Presentational (dumb) components receive data via `@Input()`, emit events via `@Output()`, and contain only display logic. This separation improves testability, reusability, and maintainability.

**Q3: What is `changeDetection: ChangeDetectionStrategy.OnPush`?**
> OnPush tells Angular to skip change detection for this component unless: (1) an `@Input()` reference changes, (2) an event originates from this component or its children, (3) an Observable bound with `async` pipe emits, or (4) `markForCheck()` is called manually. This dramatically reduces unnecessary checks in large apps.

**Q4: How do you choose between inline and external templates?**
> Use inline templates for small components (< 5-10 lines of HTML). Use external templates for anything larger. Inline keeps everything in one file and is good for simple presentational components. External provides better IDE support, separation of concerns, and is required for complex templates.

**Q5: What is ViewEncapsulation and when would you change it?**
> ViewEncapsulation controls CSS scoping. `Emulated` (default) scopes styles via generated attributes — safe and predictable. `None` makes styles global — useful for theme overrides but dangerous. `ShadowDom` uses native browser Shadow DOM — true isolation but limited browser support for some features. Change to `None` only for global theme styles; use `ShadowDom` for web component libraries.

---

## Follow-up Questions and Answers

**Q: What happens if you forget `standalone: true` in a new component?**
> The component becomes NgModule-based by default. It won't work until you add it to a module's `declarations`. If you try to import it in another standalone component, Angular will throw an error saying the component is not standalone and cannot be imported directly.

**Q: Can a component have multiple selectors?**
> Yes, you can use a comma-separated list: `selector: 'app-user, [appUser]'`. This allows the component to be used both as an element and as an attribute. This is uncommon but used in some UI libraries.

**Q: What is `host` in @Component metadata?**
> `host` lets you bind to the host element (the component's root DOM element): `host: { '[class.active]': 'isActive', '(click)': 'onClick()' }`. Alternatively, use `@HostBinding` and `@HostListener` decorators. Useful for adding CSS classes or listening to events on the component's wrapper element.

---

## Common Mistakes

1. **Not using `track` in @for (or trackBy in *ngFor)**
   ```html
   <!-- ❌ Rebuilds all DOM nodes on every change -->
   @for (user of users; track $index) { }
   
   <!-- ✅ Reuses DOM nodes by identity -->
   @for (user of users; track user.id) { }
   ```

2. **Putting business logic in components**
   ```typescript
   // ❌ Component does HTTP, caching, transformation
   export class UserListComponent {
     async loadUsers() {
       const cached = localStorage.getItem('users');
       if (cached) return JSON.parse(cached);
       const users = await fetch('/api/users').then(r => r.json());
       localStorage.setItem('users', JSON.stringify(users));
       return users;
     }
   }
   
   // ✅ Extract to service
   export class UserListComponent {
     constructor(private userService: UserService) {}
     ngOnInit() { this.users$ = this.userService.getUsers(); }
   }
   ```

3. **Not unsubscribing from Observables**
   ```typescript
   // ❌ Memory leak
   ngOnInit() { this.service.getData().subscribe(d => this.data = d); }
   
   // ✅ Proper cleanup
   private destroy$ = new Subject<void>();
   ngOnInit() {
     this.service.getData()
       .pipe(takeUntil(this.destroy$))
       .subscribe(d => this.data = d);
   }
   ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }
   ```

4. **Using `any` for @Input types**
   ```typescript
   // ❌ No type safety
   @Input() data: any;
   
   // ✅ Typed input
   @Input({ required: true }) user!: User;
   ```

---

## Best Practices

1. **Use standalone components** for all new development.
2. **Prefer OnPush change detection** for presentational components.
3. **Keep components small** — extract logic to services, child components.
4. **Follow smart/presentational pattern** — separate data from display.
5. **Always use `track`** in @for loops with a unique identifier.
6. **Clean up subscriptions** — use `takeUntil`, `takeUntilDestroyed`, or `async` pipe.
7. **Use `inject()` function** instead of constructor injection (modern Angular).
8. **Mark required inputs** — `@Input({ required: true })`.

---

## Production Considerations

- **Lazy load feature components** via route-level code splitting.
- **Use OnPush everywhere possible** — reduces change detection cycles by 50-80%.
- **Avoid heavy computation in templates** — use pure pipes or precomputed values.
- **Monitor component tree depth** — deeply nested trees slow change detection.
- **Profile with Angular DevTools** — identify slow components and unnecessary re-renders.

---

## Related Topics

- → [4. Data Binding](./04-data-binding.md)
- → [7. Component Lifecycle](./07-component-lifecycle.md)
- → [8. Component Communication](./08-component-communication.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
- → [24. Change Detection](./24-change-detection.md)
