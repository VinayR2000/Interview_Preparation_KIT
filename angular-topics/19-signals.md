# 19. Signals — Modern Angular

---

## Theory

Signals (Angular 16+) are a reactive primitive for managing state. They provide synchronous, glitch-free reactivity with automatic dependency tracking — a simpler alternative to RxJS for component state.

### Core Signal APIs

```typescript
import { signal, computed, effect } from '@angular/core';

// signal() — writable reactive value
const count = signal(0);
console.log(count());     // Read: 0

count.set(5);             // Set new value
count.update(v => v + 1); // Update based on current value

// computed() — derived value (auto-tracked, cached)
const doubled = computed(() => count() * 2);
console.log(doubled());   // 10 (2 * 5... wait, count is 6 from update)

// effect() — side effect that runs when dependencies change
effect(() => {
  console.log(`Count changed to: ${count()}`);
  // Runs immediately, then whenever count() changes
});
```

### signal() — Writable Signal

```typescript
// Primitive values
const name = signal('John');
const age = signal(30);
const isActive = signal(true);

// Object values
const user = signal<User>({ id: 1, name: 'John', email: 'j@t.com' });

// Reading
const currentName = name(); // 'John'

// Writing
name.set('Jane');                          // Replace value
age.update(current => current + 1);        // Update with function
user.update(u => ({ ...u, name: 'Jane' })); // Immutable object update

// Mutate (for objects/arrays — updates in place)
user.mutate(u => { u.name = 'Jane'; });    // Deprecated in newer versions
```

### computed() — Derived Signals

```typescript
const firstName = signal('John');
const lastName = signal('Doe');

// Computed signal — automatically tracks dependencies
const fullName = computed(() => `${firstName()} ${lastName()}`);
console.log(fullName()); // 'John Doe'

firstName.set('Jane');
console.log(fullName()); // 'Jane Doe' (auto-updated)

// Complex computed
const users = signal<User[]>([]);
const searchTerm = signal('');
const sortField = signal<'name' | 'email'>('name');

const filteredUsers = computed(() => {
  const term = searchTerm().toLowerCase();
  const field = sortField();
  
  return users()
    .filter(u => u.name.toLowerCase().includes(term) || u.email.toLowerCase().includes(term))
    .sort((a, b) => a[field].localeCompare(b[field]));
});

// Computed is:
// - Lazy (only computes when read)
// - Cached (returns same value if dependencies unchanged)
// - Auto-tracked (knows which signals it depends on)
```

### effect() — Side Effects

```typescript
// effect runs when any signal it reads changes
const theme = signal<'light' | 'dark'>('light');

effect(() => {
  document.body.classList.toggle('dark-mode', theme() === 'dark');
  // Runs immediately, then whenever theme() changes
});

// effect with cleanup
effect((onCleanup) => {
  const interval = setInterval(() => console.log(count()), 1000);
  
  onCleanup(() => clearInterval(interval)); // Cleanup on re-run or destroy
});

// effect is useful for:
// - Logging state changes
// - Syncing with external systems (localStorage, DOM)
// - Analytics tracking
// - WebSocket messages based on state
```

### Signal Inputs (Angular 17.1+)

```typescript
import { input, output } from '@angular/core';

@Component({
  selector: 'app-user-card',
  template: `
    <h3>{{ name() }}</h3>
    <p>{{ email() }}</p>
    @if (showActions()) {
      <button (click)="onDelete()">Delete</button>
    }
  `
})
export class UserCardComponent {
  // Signal-based inputs (read with ())
  name = input.required<string>();     // Required
  email = input.required<string>();    // Required
  showActions = input(true);           // Optional with default

  // Signal-based output
  delete = output<number>();
  
  onDelete(): void {
    this.delete.emit(/* id */);
  }
}

// Usage: <app-user-card [name]="user.name" [email]="user.email" />
```

### Signals vs RxJS

| Feature | Signals | RxJS Observables |
|---------|---------|-----------------|
| Values | Synchronous, always available | Async, may not have value |
| Reading | `signal()` — no subscribe needed | Must subscribe |
| Push/Pull | Pull (you read when needed) | Push (values pushed to you) |
| Async operations | ❌ Not designed for | ✅ Perfect for |
| HTTP requests | ❌ Use RxJS | ✅ HttpClient returns Observable |
| Component state | ✅ Perfect | Overkill for simple state |
| Event streams | ❌ Limited | ✅ Designed for |
| Operators | None (use computed) | Rich operator library |
| Cleanup | Automatic (GC) | Must unsubscribe |
| Change detection | Granular (signal-level) | Component-level |

### Interoperability

```typescript
import { toSignal, toObservable } from '@angular/core/rxjs-interop';

// Observable → Signal
const users$: Observable<User[]> = this.userService.getUsers();
const users = toSignal(users$, { initialValue: [] });
// Now use: users() in template

// Signal → Observable
const searchTerm = signal('');
const searchTerm$ = toObservable(this.searchTerm);
// Now use with RxJS operators

// Real usage: HTTP to signal
@Component({ ... })
export class UserListComponent {
  private userService = inject(UserService);
  
  // Convert Observable to Signal with initial value
  users = toSignal(this.userService.getUsers(), { initialValue: [] });
  
  // Computed from signal
  userCount = computed(() => this.users().length);
}
```

---

## Internal Working

### Signal Dependency Tracking

```
const a = signal(1);
const b = signal(2);
const sum = computed(() => a() + b());

When sum is first read:
1. Angular enters "tracking" mode
2. sum's computation runs: a() + b()
3. Reading a() registers a as dependency of sum
4. Reading b() registers b as dependency of sum
5. Result (3) cached

When a.set(5):
1. a notified it changed
2. Angular marks sum as "dirty" (needs recomputation)
3. Next time sum() is read → recomputes: 5 + 2 = 7
4. New value cached

Key: Signals use PULL-based reactivity
  - Computed values are lazy — only recompute when READ
  - Unlike RxJS which is PUSH (values pushed immediately)
```

### Change Detection with Signals

```
Traditional (Zone.js):
  Event → Zone.js detects → Check ENTIRE component tree → Update DOM

With Signals (future Zoneless):
  Signal changes → Only components reading that signal are marked dirty
  → Only those components re-render

Benefits:
  - More granular (component-level, not tree-level)
  - No unnecessary checks
  - Path to removing Zone.js entirely
```

---

## Diagram

```
Signal Dependency Graph:
┌─────────┐    ┌─────────┐
│ signal:  │    │ signal:  │
│ firstName│    │ lastName │
└─────┬───┘    └────┬─────┘
      │              │
      └──────┬───────┘
             ▼
┌────────────────────────┐
│ computed: fullName      │
│ = firstName + lastName  │
└────────────┬───────────┘
             │
             ▼
┌────────────────────────┐
│ effect: log changes     │
│ console.log(fullName()) │
└────────────────────────┘

When firstName.set('Jane'):
  → fullName marked dirty
  → effect scheduled to re-run
  → On next read: fullName recomputes → 'Jane Doe'
```

---

## Code

```typescript
// Signal-based state management
@Injectable({ providedIn: 'root' })
export class CartStore {
  // State signals
  private items = signal<CartItem[]>([]);
  private discount = signal(0);

  // Public read-only signals
  readonly cartItems = this.items.asReadonly();
  readonly itemCount = computed(() => this.items().length);
  readonly subtotal = computed(() =>
    this.items().reduce((sum, item) => sum + item.price * item.quantity, 0)
  );
  readonly total = computed(() => this.subtotal() * (1 - this.discount()));
  readonly isEmpty = computed(() => this.items().length === 0);

  addItem(product: Product): void {
    this.items.update(items => {
      const existing = items.find(i => i.id === product.id);
      if (existing) {
        return items.map(i => i.id === product.id
          ? { ...i, quantity: i.quantity + 1 }
          : i
        );
      }
      return [...items, { ...product, quantity: 1 }];
    });
  }

  removeItem(id: number): void {
    this.items.update(items => items.filter(i => i.id !== id));
  }

  applyDiscount(percent: number): void {
    this.discount.set(percent / 100);
  }

  clear(): void {
    this.items.set([]);
    this.discount.set(0);
  }
}

// Component using signals
@Component({
  selector: 'app-cart-summary',
  standalone: true,
  template: `
    <div class="cart-summary">
      <span>Items: {{ cart.itemCount() }}</span>
      <span>Subtotal: {{ cart.subtotal() | currency }}</span>
      @if (cart.total() !== cart.subtotal()) {
        <span class="discount">Discount applied!</span>
      }
      <span class="total">Total: {{ cart.total() | currency }}</span>
    </div>
  `
})
export class CartSummaryComponent {
  cart = inject(CartStore);
}
```

---

## Dry Run

### Computed Signal Caching

```
const items = signal([{ price: 10, qty: 2 }, { price: 20, qty: 1 }]);
const total = computed(() => items().reduce((s, i) => s + i.price * i.qty, 0));

Step 1: total() called first time
  → Computes: 10*2 + 20*1 = 40
  → Cached: 40
  → Dependencies tracked: [items]

Step 2: total() called again (items unchanged)
  → items not dirty → return cached 40 (NO recomputation)

Step 3: items.update(i => [...i, { price: 5, qty: 3 }])
  → items changed → total marked "dirty"

Step 4: total() called
  → Recomputes: 10*2 + 20*1 + 5*3 = 55
  → Cached: 55
```

---

## Interview Questions and Answers

**Q1: What are Angular Signals and why were they introduced?**
> Signals are synchronous reactive primitives for managing state. They were introduced to provide simpler state management (vs RxJS complexity), enable more granular change detection (only re-render affected components), and pave the way for removing Zone.js. They complement RxJS — signals for synchronous state, RxJS for async operations.

**Q2: What is the difference between signal, computed, and effect?**
> `signal()`: writable reactive value — create with initial value, update with set/update. `computed()`: read-only derived value — automatically tracks dependencies, cached until dependencies change. `effect()`: side effect that re-runs when any signal it reads changes — used for syncing external systems (DOM, localStorage, logging).

**Q3: When should you use Signals vs RxJS?**
> Use Signals for: component state, derived UI values, simple shared state. Use RxJS for: HTTP calls, WebSocket streams, complex async flows, event debouncing, race condition handling (switchMap). They interoperate via `toSignal()` and `toObservable()`. In practice, most apps use both.

**Q4: How does computed() differ from just calling a method in the template?**
> `computed()` is cached — it only recomputes when its dependencies change. A method in the template runs on every change detection cycle. For a list filter, computed runs once when the list or filter changes; a method runs potentially hundreds of times per second. Computed is essentially a pure pipe for signals.

**Q5: How do you convert between Signals and Observables?**
> `toSignal(observable$, { initialValue })` converts Observable to Signal — subscribes and updates signal on each emission. `toObservable(signal)` converts Signal to Observable — emits whenever signal value changes. Both are in `@angular/core/rxjs-interop`.

---

## Common Mistakes

1. **Using signals for async operations**
   ```typescript
   // ❌ Don't try to make HTTP calls signal-based manually
   const users = signal<User[]>([]);
   http.get<User[]>('/api').subscribe(u => users.set(u)); // Awkward
   
   // ✅ Use toSignal for conversion
   const users = toSignal(http.get<User[]>('/api'), { initialValue: [] });
   ```

2. **Mutating objects in signals without creating new reference**
   ```typescript
   // ❌ Won't trigger change detection
   user().name = 'Jane'; // Mutating existing object
   
   // ✅ Create new reference
   user.update(u => ({ ...u, name: 'Jane' }));
   ```

3. **Creating effect inside loops or conditionals**

---

## Best Practices

1. **Use signals for component state** (replaces BehaviorSubject for simple cases).
2. **Use computed for derived values** (replaces RxJS map/combineLatest for sync data).
3. **Use toSignal()** to bridge HTTP Observables into signal-based templates.
4. **Keep effects minimal** — they're for external synchronization, not business logic.
5. **Prefer signal inputs** (`input()`) over decorator-based `@Input()` in new code.
6. **Use `.asReadonly()`** to expose signals without write access.

---

## Production Considerations

- **Zoneless Angular**: Signals are the foundation for removing Zone.js entirely.
- **Performance**: Signal-based change detection is more granular than Zone.js.
- **Migration**: Existing apps can adopt signals incrementally — they work alongside RxJS.
- **SSR**: Signals work with server-side rendering (synchronous nature helps).

---

## Related Topics

- → [8. Component Communication](./08-component-communication.md)
- → [17. RxJS](./17-rxjs.md)
- → [24. Change Detection](./24-change-detection.md)
- → [27. State Management](./27-state-management.md)
