# 8. Component Communication

---

## Theory

Angular components need to share data and trigger actions across the component tree. There are several patterns for different relationships.

### Communication Patterns

| Pattern | Relationship | Direction |
|---------|-------------|-----------|
| `@Input()` | Parent → Child | One-way down |
| `@Output()` + EventEmitter | Child → Parent | One-way up |
| Shared Service | Any ↔ Any | Bidirectional |
| Signals | Any ↔ Any | Reactive |
| Content Projection | Parent → Child (template) | Down |
| ViewChild | Parent → Child (direct access) | Down (imperative) |

### @Input() — Parent to Child

```typescript
// Child component
@Component({
  selector: 'app-user-card',
  standalone: true,
  template: `
    <div class="card">
      <h3>{{ user.name }}</h3>
      <p>{{ user.email }}</p>
      @if (showActions) {
        <div class="actions">
          <button (click)="onEdit()">Edit</button>
        </div>
      }
    </div>
  `
})
export class UserCardComponent {
  @Input({ required: true }) user!: User;   // Required input
  @Input() showActions = true;              // Optional with default
  @Input({ alias: 'highlighted' }) isHighlighted = false; // Aliased input
  
  // Transform input (Angular 16+)
  @Input({ transform: booleanAttribute }) disabled = false;
  @Input({ transform: numberAttribute }) size = 16;
}

// Parent template
// <app-user-card [user]="selectedUser" [showActions]="canEdit" [highlighted]="true" />
```

### @Output() + EventEmitter — Child to Parent

```typescript
// Child component
@Component({
  selector: 'app-user-card',
  standalone: true,
  template: `
    <div class="card">
      <h3>{{ user.name }}</h3>
      <button (click)="onEdit()">Edit</button>
      <button (click)="onDelete()">Delete</button>
      <button (click)="onSelect()">Select</button>
    </div>
  `
})
export class UserCardComponent {
  @Input({ required: true }) user!: User;
  
  @Output() edit = new EventEmitter<User>();
  @Output() delete = new EventEmitter<number>();
  @Output() select = new EventEmitter<User>();

  onEdit(): void {
    this.edit.emit(this.user);
  }

  onDelete(): void {
    this.delete.emit(this.user.id);
  }

  onSelect(): void {
    this.select.emit(this.user);
  }
}

// Parent template
// <app-user-card
//   [user]="user"
//   (edit)="openEditDialog($event)"
//   (delete)="confirmDelete($event)"
//   (select)="selectUser($event)" />

// Parent component
export class UserListComponent {
  openEditDialog(user: User): void { /* open dialog */ }
  confirmDelete(userId: number): void { /* confirm and delete */ }
  selectUser(user: User): void { this.selectedUser = user; }
}
```

### Two-Way Binding (Custom)

```typescript
// Child component with custom two-way binding
@Component({
  selector: 'app-counter',
  standalone: true,
  template: `
    <button (click)="decrement()">-</button>
    <span>{{ count }}</span>
    <button (click)="increment()">+</button>
  `
})
export class CounterComponent {
  @Input() count = 0;
  @Output() countChange = new EventEmitter<number>(); // Name: inputName + 'Change'

  increment(): void {
    this.count++;
    this.countChange.emit(this.count);
  }

  decrement(): void {
    this.count--;
    this.countChange.emit(this.count);
  }
}

// Parent usage — banana-in-a-box syntax
// <app-counter [(count)]="orderQuantity"></app-counter>
// Equivalent to: <app-counter [count]="orderQuantity" (countChange)="orderQuantity = $event">
```

### Sibling Communication via Shared Service

```typescript
// Shared service
@Injectable({ providedIn: 'root' })
export class SelectionService {
  private selectedUserSubject = new BehaviorSubject<User | null>(null);
  selectedUser$ = this.selectedUserSubject.asObservable();

  selectUser(user: User): void {
    this.selectedUserSubject.next(user);
  }

  clearSelection(): void {
    this.selectedUserSubject.next(null);
  }
}

// Sibling A — sends data
@Component({
  selector: 'app-user-list',
  template: `
    @for (user of users; track user.id) {
      <div (click)="select(user)">{{ user.name }}</div>
    }
  `
})
export class UserListComponent {
  users: User[] = [];
  private selectionService = inject(SelectionService);

  select(user: User): void {
    this.selectionService.selectUser(user);
  }
}

// Sibling B — receives data
@Component({
  selector: 'app-user-detail',
  template: `
    @if (selectedUser$ | async; as user) {
      <h2>{{ user.name }}</h2>
      <p>{{ user.email }}</p>
    } @else {
      <p>Select a user</p>
    }
  `
})
export class UserDetailComponent {
  selectedUser$ = inject(SelectionService).selectedUser$;
}
```

### Signals-Based Communication (Angular 16+)

```typescript
// Shared signal service
@Injectable({ providedIn: 'root' })
export class CartService {
  // Writable signals for state
  private items = signal<CartItem[]>([]);
  
  // Computed signals (derived state)
  readonly cartItems = this.items.asReadonly();
  readonly itemCount = computed(() => this.items().length);
  readonly total = computed(() => 
    this.items().reduce((sum, item) => sum + item.price * item.quantity, 0)
  );

  addItem(item: CartItem): void {
    this.items.update(items => [...items, item]);
  }

  removeItem(id: number): void {
    this.items.update(items => items.filter(i => i.id !== id));
  }

  clear(): void {
    this.items.set([]);
  }
}

// Component using signals
@Component({
  selector: 'app-cart-badge',
  template: `<span class="badge">{{ cartService.itemCount() }}</span>`
})
export class CartBadgeComponent {
  cartService = inject(CartService);
}

// Signal inputs (Angular 17.1+)
@Component({
  selector: 'app-greeting',
  template: `<h1>Hello, {{ name() }}!</h1>`
})
export class GreetingComponent {
  name = input.required<string>();     // Required signal input
  title = input('Mr.');                // Optional with default
}
// Usage: <app-greeting [name]="userName" />
```

### Content Projection

```typescript
// Card component with projection slots
@Component({
  selector: 'app-card',
  standalone: true,
  template: `
    <div class="card">
      <div class="card-header">
        <ng-content select="[card-header]"></ng-content>
      </div>
      <div class="card-body">
        <ng-content></ng-content>  <!-- Default slot -->
      </div>
      <div class="card-footer">
        <ng-content select="[card-footer]"></ng-content>
      </div>
    </div>
  `
})
export class CardComponent {}

// Usage
// <app-card>
//   <h3 card-header>User Profile</h3>
//   <p>This goes in the default slot (body)</p>
//   <button card-footer>Save</button>
// </app-card>
```

---

## Internal Working

### @Input() Binding Flow

```
Parent Component                       Child Component
┌─────────────────────┐               ┌──────────────────────┐
│ selectedUser = {...} │               │ @Input() user: User  │
│                      │               │                      │
│ Template:            │    Binding    │                      │
│ [user]="selectedUser"│───────────────→│ user = selectedUser  │
│                      │               │                      │
│ When selectedUser    │               │ ngOnChanges fires    │
│ reference changes ───│───────────────→│ with new value       │
└─────────────────────┘               └──────────────────────┘
```

### EventEmitter Internal Mechanism

```
EventEmitter extends Subject (RxJS):

Child: this.edit.emit(user)
  ↓
EventEmitter.next(user)  — it's a Subject under the hood
  ↓
Parent subscription (via (edit)="handler($event)"):
  handler receives the emitted value
  ↓
Parent's handler executes
  ↓
Change detection runs on parent
```

### BehaviorSubject in Shared Service

```
Service: BehaviorSubject<User | null>(null)

Initial state: null
  ↓
Component A subscribes → receives null immediately
Component B subscribes → receives null immediately
  ↓
Component C calls service.selectUser(john)
  ↓
BehaviorSubject.next(john)
  ↓
Component A receives john → updates view
Component B receives john → updates view
  ↓
New Component D subscribes → receives john immediately (last value)
```

---

## Diagram

```
Communication Patterns:

1. Parent → Child (@Input):
┌──────────┐    [data]="x"    ┌──────────┐
│  Parent   │────────────────→│  Child    │
└──────────┘                  └──────────┘

2. Child → Parent (@Output):
┌──────────┐  (event)="fn()"  ┌──────────┐
│  Parent   │←────────────────│  Child    │
└──────────┘                  └──────────┘

3. Sibling via Service:
┌──────────┐                  ┌──────────┐
│ Sibling A │──→┌──────────┐←──│ Sibling B │
└──────────┘   │  Service  │  └──────────┘
               │(Subject)  │
               └──────────┘

4. Any via Service:
┌──────────┐
│ Header   │──subscribe──┐
└──────────┘             │
┌──────────┐         ┌───▼────┐
│ Sidebar  │──emit──→│ Service│
└──────────┘         └───┬────┘
┌──────────┐             │
│ Content  │──subscribe──┘
└──────────┘
```

---

## Code

```typescript
// Complete example: Product selection system

// Models
interface Product {
  id: number;
  name: string;
  price: number;
  category: string;
}

// Shared service for cross-component communication
@Injectable({ providedIn: 'root' })
export class ProductSelectionService {
  private selectedProduct = new BehaviorSubject<Product | null>(null);
  private compareList = new BehaviorSubject<Product[]>([]);

  selectedProduct$ = this.selectedProduct.asObservable();
  compareList$ = this.compareList.asObservable();
  compareCount$ = this.compareList.pipe(map(list => list.length));

  select(product: Product): void {
    this.selectedProduct.next(product);
  }

  addToCompare(product: Product): void {
    const current = this.compareList.value;
    if (current.length < 4 && !current.find(p => p.id === product.id)) {
      this.compareList.next([...current, product]);
    }
  }

  removeFromCompare(productId: number): void {
    const current = this.compareList.value;
    this.compareList.next(current.filter(p => p.id !== productId));
  }
}

// Parent: Product List Page
@Component({
  selector: 'app-product-page',
  standalone: true,
  imports: [ProductGridComponent, ProductDetailPanelComponent, CompareBarComponent],
  template: `
    <div class="layout">
      <app-product-grid
        [products]="products"
        [selectedId]="(selectedProduct$ | async)?.id ?? null"
        (productClick)="onProductClick($event)"
        (addToCompare)="onAddToCompare($event)" />
      
      <app-product-detail-panel />
      <app-compare-bar />
    </div>
  `
})
export class ProductPageComponent implements OnInit {
  private productService = inject(ProductService);
  private selectionService = inject(ProductSelectionService);
  
  products: Product[] = [];
  selectedProduct$ = this.selectionService.selectedProduct$;

  ngOnInit(): void {
    this.productService.getProducts().subscribe(p => this.products = p);
  }

  onProductClick(product: Product): void {
    this.selectionService.select(product);
  }

  onAddToCompare(product: Product): void {
    this.selectionService.addToCompare(product);
  }
}

// Child: Product Grid (presentational)
@Component({
  selector: 'app-product-grid',
  standalone: true,
  imports: [CommonModule, CurrencyPipe],
  template: `
    @for (product of products; track product.id) {
      <div class="product-card" 
           [class.selected]="product.id === selectedId"
           (click)="productClick.emit(product)">
        <h3>{{ product.name }}</h3>
        <p>{{ product.price | currency }}</p>
        <button (click)="onCompareClick($event, product)">Compare</button>
      </div>
    }
  `
})
export class ProductGridComponent {
  @Input({ required: true }) products!: Product[];
  @Input() selectedId: number | null = null;
  
  @Output() productClick = new EventEmitter<Product>();
  @Output() addToCompare = new EventEmitter<Product>();

  onCompareClick(event: Event, product: Product): void {
    event.stopPropagation(); // Don't trigger productClick
    this.addToCompare.emit(product);
  }
}

// Sibling: Detail Panel (reads from service)
@Component({
  selector: 'app-product-detail-panel',
  standalone: true,
  imports: [AsyncPipe, CurrencyPipe],
  template: `
    @if (product$ | async; as product) {
      <div class="detail-panel">
        <h2>{{ product.name }}</h2>
        <p class="price">{{ product.price | currency }}</p>
        <p class="category">{{ product.category }}</p>
      </div>
    } @else {
      <p class="placeholder">Click a product to see details</p>
    }
  `
})
export class ProductDetailPanelComponent {
  product$ = inject(ProductSelectionService).selectedProduct$;
}

// Another sibling: Compare Bar
@Component({
  selector: 'app-compare-bar',
  standalone: true,
  imports: [AsyncPipe],
  template: `
    @if (compareList$ | async; as items) {
      @if (items.length > 0) {
        <div class="compare-bar">
          <span>Comparing {{ items.length }} products</span>
          @for (item of items; track item.id) {
            <span class="chip">
              {{ item.name }}
              <button (click)="remove(item.id)">×</button>
            </span>
          }
        </div>
      }
    }
  `
})
export class CompareBarComponent {
  private selectionService = inject(ProductSelectionService);
  compareList$ = this.selectionService.compareList$;

  remove(id: number): void {
    this.selectionService.removeFromCompare(id);
  }
}
```

---

## Dry Run

### @Input/@Output Flow

```
User clicks product "Angular Book" in ProductGrid:

Step 1: (click) event fires on product card div
Step 2: productClick.emit({ id: 1, name: 'Angular Book', ... })
Step 3: Parent receives via (productClick)="onProductClick($event)"
Step 4: Parent calls selectionService.select(product)
Step 5: BehaviorSubject emits { id: 1, name: 'Angular Book', ... }
Step 6: ProductDetailPanel (subscribed via async pipe) receives value
Step 7: Template renders product details
Step 8: Parent updates selectedId → [selectedId]="1"
Step 9: ProductGrid child ngOnChanges fires
Step 10: CSS class .selected applied to Angular Book card
```

### Service-Based Sibling Communication

```
Initial state:
  BehaviorSubject value = null
  DetailPanel shows: "Click a product to see details"
  CompareBar shows: nothing (empty array)

User clicks "Add to Compare" on Product A:
Step 1: onCompareClick(event, productA) — stopPropagation
Step 2: addToCompare.emit(productA)
Step 3: Parent calls selectionService.addToCompare(productA)
Step 4: compareList BehaviorSubject: [] → [productA]
Step 5: CompareBar (async pipe) receives [productA]
Step 6: Compare bar shows: "Comparing 1 products" with chip

User clicks "Add to Compare" on Product B:
Step 7: compareList: [productA] → [productA, productB]
Step 8: CompareBar updates: "Comparing 2 products" with 2 chips
```

---

## Complexity

| Pattern | Setup Complexity | Scalability | Performance |
|---------|-----------------|-------------|-------------|
| @Input/@Output | Low | Limited to parent-child | Optimal |
| Shared Service (Subject) | Medium | Any relationship | Good |
| Signals | Low | Any relationship | Optimal |
| ViewChild direct access | Low | Parent-child only | Good |
| NgRx | High | Enterprise scale | Good (memoized) |

---

## Real Project Usage

```typescript
// Real-world notification system across the app
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private notifications$ = new Subject<Notification>();
  
  // Components subscribe to this
  readonly notifications = this.notifications$.pipe(
    scan((acc, notif) => [...acc.slice(-4), notif], [] as Notification[]),
    shareReplay(1)
  );

  success(message: string): void {
    this.notifications$.next({ type: 'success', message, id: Date.now() });
  }

  error(message: string): void {
    this.notifications$.next({ type: 'error', message, id: Date.now() });
  }

  info(message: string): void {
    this.notifications$.next({ type: 'info', message, id: Date.now() });
  }
}

// Used anywhere in the app:
// inject(NotificationService).success('User saved!');
```

---

## Interview Questions and Answers

**Q1: How do you pass data from parent to child component?**
> Use `@Input()` decorator. Parent binds data in template: `<app-child [data]="value">`. Child declares: `@Input() data: Type`. The child receives the value and is notified of changes via ngOnChanges. For required inputs, use `@Input({ required: true })`.

**Q2: How do you pass data from child to parent?**
> Use `@Output()` with `EventEmitter`. Child declares: `@Output() event = new EventEmitter<Type>()` and calls `this.event.emit(value)`. Parent listens in template: `<app-child (event)="handler($event)">`. The `$event` variable contains the emitted value.

**Q3: How do sibling components communicate?**
> Through a shared service using `BehaviorSubject` or signals. One sibling calls a service method to update state. Other siblings subscribe to the service's Observable or read the signal. BehaviorSubject is preferred over Subject because it provides the last value to new subscribers immediately.

**Q4: What is the convention for custom two-way binding?**
> If the @Input is named `x`, the @Output must be named `xChange`. This enables the `[(x)]` banana-in-a-box syntax. Example: `@Input() count` + `@Output() countChange` enables `[(count)]="value"` on the parent.

**Q5: When would you use ViewChild to communicate vs @Output?**
> Use @Output for declarative, reactive communication (recommended). Use ViewChild for imperative access when you need to call methods directly on a child: `@ViewChild(ChildComponent) child!: ChildComponent; this.child.reset()`. ViewChild creates tight coupling — prefer @Output for most cases.

---

## Follow-up Questions and Answers

**Q: BehaviorSubject vs Subject for shared services?**
> BehaviorSubject has an initial value and emits the last value to new subscribers. Subject has no initial value and only emits to current subscribers. Use BehaviorSubject when components need the current state immediately upon subscribing (most cases). Use Subject for one-time events (like notifications) where you don't need history.

**Q: How do you avoid prop-drilling (passing inputs through many levels)?**
> Use a shared service at the appropriate injector level. If data needs to pass through grandparent → parent → child, put it in a service instead. The consuming component injects the service directly. Alternatively, use signals or state management for deeply nested communication.

**Q: Can @Output emit complex objects?**
> Yes. `EventEmitter<T>` is generic — T can be any type including complex objects, arrays, or custom interfaces. Example: `@Output() formSubmit = new EventEmitter<{ user: User, action: 'create' | 'update' }>()`.

---

## Common Mistakes

1. **Mutating @Input values in child component**
   ```typescript
   // ❌ Mutating parent's data — unpredictable side effects
   @Input() user!: User;
   updateName() { this.user.name = 'New Name'; } // Mutates parent's object!
   
   // ✅ Emit event, let parent decide
   @Output() nameChange = new EventEmitter<string>();
   updateName() { this.nameChange.emit('New Name'); }
   ```

2. **Using Subject instead of BehaviorSubject**
   ```typescript
   // ❌ Late subscribers miss the value
   private data = new Subject<User>();
   
   // ✅ Late subscribers get current value
   private data = new BehaviorSubject<User | null>(null);
   ```

3. **Not unsubscribing from service observables**
   ```typescript
   // ❌ Memory leak
   ngOnInit() { this.service.data$.subscribe(d => this.data = d); }
   
   // ✅ Use async pipe (auto-unsubscribes)
   // template: {{ data$ | async }}
   // OR takeUntilDestroyed
   ```

4. **Tight coupling via ViewChild**
   ```typescript
   // ❌ Parent knows too much about child's internals
   @ViewChild(UserFormComponent) form!: UserFormComponent;
   save() { this.form.internalValidate(); this.form.privateMethod(); }
   
   // ✅ Use @Output from child
   // <app-user-form (submit)="onSubmit($event)" />
   ```

---

## Best Practices

1. **@Input/@Output for parent-child** — keep it declarative.
2. **Shared service with BehaviorSubject** for sibling/cross-tree communication.
3. **Signals** for simple reactive state (Angular 16+).
4. **Don't mutate inputs** — treat them as read-only, emit changes up.
5. **Use AsyncPipe** to subscribe to service observables.
6. **Keep EventEmitter events specific** — emit what the parent needs, not internal state.
7. **Single responsibility** — one service per domain concern.
8. **Use `required: true`** on inputs that must be provided.

---

## Production Considerations

- **OnPush + Immutability**: When using OnPush, always create new object references for @Input changes to trigger change detection.
- **Service scope**: `providedIn: 'root'` makes a singleton. Component-level providers create per-component instances (useful for per-instance state).
- **Memory**: BehaviorSubject holds value in memory — clear it when no longer needed.
- **Large apps**: Consider NgRx or signal-based state management for complex cross-cutting state.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [7. Component Lifecycle](./07-component-lifecycle.md)
- → [9. Content Projection](./09-content-projection.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
- → [12. Services](./12-services.md)
- → [17. RxJS](./17-rxjs.md)
- → [19. Signals](./19-signals.md)
