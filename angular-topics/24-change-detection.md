# 24. Change Detection — Very Important for Experienced Angular Interviews

---

## Theory

Change detection is Angular's mechanism for keeping the DOM in sync with component data. Angular checks all bindings in the component tree and updates the DOM where values have changed.

### How Change Detection Works

```
User Event (click, input, timer, HTTP response)
    ↓
Zone.js intercepts async operation
    ↓
Zone.js notifies Angular: "async completed"
    ↓
Angular triggers change detection
    ↓
Walks component tree TOP-DOWN
    ↓
For each component: checks all template bindings
    ↓
If binding value changed → update DOM
```

### Default vs OnPush Strategy

| Feature | Default | OnPush |
|---------|---------|--------|
| Check trigger | Every CD cycle | Only when inputs change / events fire |
| Checks | All bindings every time | Skip if inputs unchanged |
| Performance | O(all bindings) | O(changed subtree) |
| Complexity | Simple (just works) | Requires immutable patterns |
| Use case | Simple apps, learning | Production, performance-critical |

### Default Change Detection

```typescript
@Component({
  // Default — no changeDetection specified
  template: `<p>{{ computeValue() }}</p>`
})
export class DefaultComponent {
  // computeValue() called on EVERY change detection cycle
  // Even if nothing in this component changed
  // Even if the click was in a completely unrelated component
}
```

### OnPush Change Detection

```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ user.name }}</h1>
    <p>{{ formattedDate }}</p>
  `
})
export class OnPushComponent {
  @Input() user!: User;
  formattedDate = '';

  // This component is ONLY checked when:
  // 1. @Input() reference changes (user = newObject)
  // 2. An event fires FROM this component (click, input, etc.)
  // 3. AsyncPipe emits a new value
  // 4. markForCheck() is called manually
  // 5. A signal read in the template changes
}
```

### OnPush Triggers

```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ title }}</h1>
    <button (click)="onClick()">Click</button>
    <p>{{ data$ | async }}</p>
  `
})
export class SmartOnPushComponent {
  @Input() title!: string; // ✅ Trigger 1: Input reference change
  
  data$ = this.service.getData(); // ✅ Trigger 3: AsyncPipe

  onClick(): void {
    // ✅ Trigger 2: Event from this component
    this.doSomething();
  }

  // For programmatic updates:
  private cdr = inject(ChangeDetectorRef);
  
  externalUpdate(): void {
    // ✅ Trigger 4: Manual markForCheck
    this.cdr.markForCheck();
  }
}
```

### ChangeDetectorRef

```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ManualCdComponent {
  private cdr = inject(ChangeDetectorRef);

  // markForCheck() — mark this component and ancestors for checking
  updateFromWebSocket(data: any): void {
    this.data = data;
    this.cdr.markForCheck(); // Schedule check in next CD cycle
  }

  // detectChanges() — run CD immediately for this component subtree
  forceUpdate(): void {
    this.cdr.detectChanges(); // Synchronous, immediate check
  }

  // detach() — completely remove from CD tree (manual control)
  detachFromCd(): void {
    this.cdr.detach(); // No automatic checks
  }

  // reattach() — rejoin CD tree
  reattachToCd(): void {
    this.cdr.reattach();
  }
}
```

### Zone.js

```typescript
// Zone.js monkey-patches all async APIs:
// setTimeout, setInterval, Promise, addEventListener, XMLHttpRequest, fetch

// Every async operation completion triggers:
// NgZone → ApplicationRef.tick() → Change Detection

// Running outside Angular (no CD trigger):
export class PerformanceComponent {
  private ngZone = inject(NgZone);

  startHeavyAnimation(): void {
    // Run outside Angular — won't trigger CD on each frame
    this.ngZone.runOutsideAngular(() => {
      requestAnimationFrame(function animate() {
        // Update DOM directly — no CD overhead
        element.style.transform = `translateX(${x}px)`;
        requestAnimationFrame(animate);
      });
    });
  }

  // When done, re-enter Angular zone
  onAnimationComplete(result: any): void {
    this.ngZone.run(() => {
      this.animationResult = result; // This triggers CD
    });
  }
}
```

### Zoneless Angular (Future)

```typescript
// With Signals, Angular can detect changes without Zone.js
// Components using signals get granular, signal-based change detection

@Component({
  template: `<p>{{ count() }}</p>`
})
export class ZonelessComponent {
  count = signal(0);
  
  increment(): void {
    this.count.update(c => c + 1);
    // Angular knows EXACTLY which binding changed
    // Only this component's template re-checks
  }
}
```

---

## Internal Working

### CD Tree Traversal

```
Default Strategy — checks ALL:

AppComponent ✓
├── HeaderComponent ✓
├── SidebarComponent ✓
└── ContentComponent ✓
    ├── UserListComponent ✓
    │   ├── UserCardComponent ✓
    │   └── UserCardComponent ✓
    └── FooterComponent ✓

Every component checked on EVERY event (even unrelated clicks)

OnPush Strategy — skips unchanged:

AppComponent ✓
├── HeaderComponent (OnPush, input unchanged) ✗ SKIP
├── SidebarComponent (OnPush, input unchanged) ✗ SKIP
└── ContentComponent ✓
    ├── UserListComponent (OnPush, input CHANGED) ✓
    │   ├── UserCardComponent (OnPush, input CHANGED) ✓
    │   └── UserCardComponent (OnPush, input unchanged) ✗ SKIP
    └── FooterComponent (OnPush, input unchanged) ✗ SKIP

Only 3 components checked instead of 8!
```

### Why OnPush + Immutability

```
@Input() users: User[]

Mutable update (OnPush MISSES it):
  this.users.push(newUser);  // Same reference! OnPush doesn't detect
  
Immutable update (OnPush DETECTS it):
  this.users = [...this.users, newUser];  // New reference! OnPush triggers

OnPush uses === (reference comparison):
  oldRef === newRef → SKIP (same object)
  oldRef !== newRef → CHECK (new object)
```

---

## Diagram

```
Change Detection Cycle:
┌─────────────────────────────────────────────────────┐
│ 1. Async event completes (click, HTTP, timer)        │
│    ↓                                                 │
│ 2. Zone.js detects → notifies Angular                │
│    ↓                                                 │
│ 3. Angular starts CD from root                       │
│    ↓                                                 │
│ 4. For each component (top-down):                    │
│    ├── Check OnPush: skip if inputs unchanged        │
│    ├── Evaluate all template bindings                │
│    ├── Compare with previous values                  │
│    └── Update DOM where values differ                │
│    ↓                                                 │
│ 5. CD complete → DOM in sync                         │
│    ↓                                                 │
│ 6. (Dev mode only) Run CD again to verify stability  │
│    → If different → ExpressionChangedAfterChecked    │
└─────────────────────────────────────────────────────┘
```

---

## Code

```typescript
// Performance-optimized component with OnPush
@Component({
  selector: 'app-stock-ticker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (stock of stocks; track stock.symbol) {
      <div class="stock" [class.up]="stock.change > 0" [class.down]="stock.change < 0">
        <span>{{ stock.symbol }}</span>
        <span>{{ stock.price | currency }}</span>
        <span>{{ stock.change | number:'1.2-2' }}%</span>
      </div>
    }
  `
})
export class StockTickerComponent {
  @Input() stocks: Stock[] = [];
  // OnPush: Only re-renders when parent passes NEW array reference
  // Parent must do: this.stocks = [...updatedStocks] (not push/mutate)
}

// Parent component managing the data
@Component({
  template: `<app-stock-ticker [stocks]="stocks" />`
})
export class DashboardComponent implements OnInit {
  stocks: Stock[] = [];
  private ws = inject(WebSocketService);
  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.ws.stockUpdates$.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(update => {
      // ✅ Create new array reference for OnPush
      this.stocks = this.stocks.map(s =>
        s.symbol === update.symbol ? { ...s, ...update } : s
      );
    });
  }
}
```

---

## Interview Questions and Answers

**Q1: How does Angular's change detection work?**
> Angular uses Zone.js to detect async operations (clicks, HTTP, timers). When async completes, Zone.js triggers change detection. Angular walks the component tree top-down, evaluates all template bindings, compares with previous values, and updates DOM where changes occurred. Default strategy checks every component; OnPush skips components with unchanged inputs.

**Q2: What is OnPush and why use it?**
> OnPush tells Angular to skip change detection for a component unless: its @Input reference changes, an event fires from it, AsyncPipe emits, or markForCheck() is called. This dramatically reduces the number of components checked per CD cycle. In apps with 100+ components, OnPush can reduce CD work by 50-80%.

**Q3: What is Zone.js and can Angular work without it?**
> Zone.js monkey-patches all async browser APIs (setTimeout, addEventListener, fetch) to detect when async operations complete. It's how Angular knows "something happened" and triggers CD. Angular is moving toward Zoneless mode using Signals — signals provide fine-grained reactivity without needing to patch async APIs.

**Q4: What causes ExpressionChangedAfterItHasBeenCheckedError?**
> In dev mode, Angular runs CD twice. If a binding value changes between the first and second check (e.g., you modify state in ngAfterViewInit), Angular throws this error. It means your component is unstable — values change during the same CD cycle. Fix: use setTimeout, detectChanges(), or restructure logic.

**Q5: How do you optimize change detection performance?**
> 1. Use OnPush on all presentational components. 2. Use immutable data (new references). 3. Use trackBy/track in loops. 4. Avoid method calls in templates (use pipes or computed). 5. Use AsyncPipe for observables. 6. Use runOutsideAngular for animations/timers that don't affect UI. 7. Detach components that rarely change.

---

## Common Mistakes

1. **Mutating objects with OnPush**
   ```typescript
   // ❌ OnPush won't detect — same reference
   this.user.name = 'New Name';
   
   // ✅ Create new reference
   this.user = { ...this.user, name: 'New Name' };
   ```

2. **Method calls in templates with OnPush**
   ```html
   <!-- ❌ Still calls on every CD cycle (when component IS checked) -->
   <p>{{ getFullName() }}</p>
   
   <!-- ✅ Use pipe or computed signal -->
   <p>{{ fullName }}</p>
   ```

3. **Not using markForCheck with external updates**
   ```typescript
   // ❌ OnPush component won't update from WebSocket
   this.ws.onMessage(data => this.data = data);
   
   // ✅ Mark for check
   this.ws.onMessage(data => {
     this.data = data;
     this.cdr.markForCheck();
   });
   ```

---

## Interview Gotcha Scenarios

**Gotcha 1: This OnPush component doesn't update when I push to the array. Why?**
```typescript
@Component({ changeDetection: ChangeDetectionStrategy.OnPush })
export class ListComponent {
  @Input() items: string[] = [];
}

// Parent does:
this.items.push('new item'); // ❌ Same reference! OnPush skips.

// Angular uses === to compare @Input references.
// push() mutates but doesn't change the reference.
// Fix: this.items = [...this.items, 'new item']; // New reference ✅
```

**Gotcha 2: My OnPush component updates when I click inside it but not from a setTimeout. Why?**
```typescript
@Component({ changeDetection: ChangeDetectionStrategy.OnPush })
export class TimerComponent {
  count = 0;
  
  onClick() { this.count++; } // ✅ Works — event from THIS component triggers CD
  
  ngOnInit() {
    setInterval(() => this.count++, 1000); // ❌ Doesn't update — not an input change, 
  }                                         // not a component event, no async pipe
}

// OnPush triggers: @Input change, component event, async pipe emit, markForCheck()
// setInterval is a Zone.js event but OnPush ignores it unless triggered from within
// Fix: this.cdr.markForCheck() inside the interval, or use Observable + async pipe
```

**Gotcha 3: I'm using async pipe but my OnPush component still shows stale data. What's wrong?**
```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p>{{ data$ | async }}</p>`
})
export class DataComponent {
  data$ = this.service.getData(); // HTTP Observable — emits once, completes

  refresh() {
    // ❌ data$ still points to the SAME completed Observable!
    // async pipe already got the value, Observable completed, won't emit again
  }
}

// Fix: Reassign the Observable to trigger new subscription
refresh() {
  this.data$ = this.service.getData(); // New Observable → async pipe resubscribes
}
```

**Gotcha 4: markForCheck() vs detectChanges() — when to use which?**
```typescript
// markForCheck(): schedules check for NEXT CD cycle
// - Marks this component AND all ancestors as dirty
// - Angular will check them on next tick
// - Safe to call from anywhere

// detectChanges(): runs CD IMMEDIATELY, synchronously
// - Checks ONLY this component and its children
// - Does NOT mark ancestors
// - Can cause ExpressionChangedAfterItHasBeenCheckedError if called at wrong time
// - Use only when you need immediate synchronous update

// Rule: Use markForCheck() 95% of the time. Use detectChanges() only for
// immediate visual feedback (animations, third-party library integration).
```

**Gotcha 5: Why is my app slow? I'm using OnPush everywhere.**
```typescript
// OnPush doesn't help if you're creating new object references on every CD cycle:
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-child [config]="getConfig()"></app-child>`
})
export class ParentComponent {
  // ❌ Creates NEW object every time template is evaluated
  getConfig() { return { theme: 'dark', size: 'large' }; }
  // Child's OnPush sees "new reference" → checks every time → defeats purpose!
}

// Fix: Precompute or memoize
config = { theme: 'dark', size: 'large' }; // Same reference
// <app-child [config]="config">
```

---

## Best Practices

1. **Use OnPush on ALL presentational/leaf components**.
2. **Use immutable data patterns** (spread operator, map/filter).
3. **Use AsyncPipe** — it calls markForCheck automatically.
4. **Use Signals** — granular CD without Zone.js.
5. **Avoid template method calls** — use pipes or precomputed values.
6. **Use `runOutsideAngular`** for animations and frequent timers.
7. **Profile with Angular DevTools** to find slow components.

---

## Production Considerations

- **OnPush reduces CPU usage** by 50-80% in large component trees.
- **Memory**: Immutable patterns create more objects — GC handles it efficiently.
- **Debugging**: OnPush components not updating? Check input references and markForCheck.
- **Signals + Zoneless**: The future of Angular CD — prepare by adopting signals now.
- **Performance budgets**: Aim for < 16ms per CD cycle (60fps).

---

## Related Topics

- → [3. Components](./03-components.md)
- → [19. Signals](./19-signals.md)
- → [25. Angular Performance](./25-angular-performance.md)
- → [26. Memory Leaks](./26-memory-leaks.md)
