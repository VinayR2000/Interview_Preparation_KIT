# 25. Angular Performance

---

## Theory

Angular performance optimization focuses on reducing bundle size, minimizing change detection cycles, and efficient rendering.

### Performance Optimization Strategies

| Category | Techniques |
|----------|-----------|
| Bundle Size | Lazy loading, tree shaking, code splitting |
| Change Detection | OnPush, signals, trackBy, pure pipes |
| Rendering | Virtual scrolling, pagination, @defer |
| Network | Caching, debouncing, shareReplay |
| Runtime | runOutsideAngular, Web Workers |

### OnPush Change Detection

```typescript
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (item of items; track item.id) {
      <app-item [data]="item" />
    }
  `
})
export class OptimizedListComponent {
  @Input() items: Item[] = [];
  // Only checks when items reference changes (not mutation)
}
```

### Track in @for (trackBy in *ngFor)

```html
<!-- ❌ Without track — recreates ALL DOM nodes on any change -->
@for (user of users; track $index) { }

<!-- ✅ With track by ID — reuses existing DOM nodes -->
@for (user of users; track user.id) {
  <app-user-card [user]="user" />
}
```

### Avoid Method Calls in Templates

```typescript
// ❌ getFullName() called every CD cycle (many times/sec)
template: `<p>{{ getFullName() }}</p>`

// ✅ Pure pipe — cached until input changes
template: `<p>{{ user | fullName }}</p>`

// ✅ Precomputed property
template: `<p>{{ fullName }}</p>`
ngOnInit() { this.fullName = `${this.user.first} ${this.user.last}`; }

// ✅ Computed signal — cached until dependencies change
fullName = computed(() => `${this.firstName()} ${this.lastName()}`);
```

### Virtual Scrolling (Large Lists)

```typescript
import { ScrollingModule } from '@angular/cdk/scrolling';

@Component({
  standalone: true,
  imports: [ScrollingModule],
  template: `
    <cdk-virtual-scroll-viewport itemSize="50" class="viewport">
      <div *cdkVirtualFor="let item of items; trackBy: trackById" class="item">
        {{ item.name }}
      </div>
    </cdk-virtual-scroll-viewport>
  `,
  styles: [`.viewport { height: 400px; }`]
})
export class VirtualListComponent {
  items: Item[] = []; // Could be 100,000+ items
  trackById = (index: number, item: Item) => item.id;
}
// Only renders ~10-20 visible items instead of all 100,000
```

### Debouncing User Input

```typescript
// ❌ Fires HTTP on every keystroke
<input (input)="search($event.target.value)">

// ✅ Debounce with RxJS
searchControl = new FormControl('');
results$ = this.searchControl.valueChanges.pipe(
  debounceTime(300),
  distinctUntilChanged(),
  switchMap(term => this.api.search(term))
);
```

### shareReplay — Prevent Duplicate Requests

```typescript
@Injectable({ providedIn: 'root' })
export class ConfigService {
  // Without shareReplay: each subscriber triggers new HTTP request
  // With shareReplay: one request, result shared to all subscribers
  private config$ = this.http.get<AppConfig>('/api/config').pipe(
    shareReplay({ bufferSize: 1, refCount: true })
  );

  getConfig(): Observable<AppConfig> {
    return this.config$;
  }
}
```

### Lazy Loading and Code Splitting

```typescript
// Route-level lazy loading
{ path: 'admin', loadChildren: () => import('./admin/admin.routes').then(m => m.ADMIN_ROUTES) }

// @defer (Angular 17+) — lazy load in template
@defer (on viewport) {
  <app-heavy-chart [data]="chartData" />
} @placeholder {
  <div class="chart-placeholder">Loading chart...</div>
} @loading (minimum 500ms) {
  <app-spinner />
}
```

### Production Build Optimizations

```bash
# Production build with all optimizations
ng build --configuration production

# Analyzes:
# - Tree shaking (removes unused code)
# - Minification (smaller file sizes)
# - AOT compilation (no template compiler in bundle)
# - Code splitting (lazy routes = separate chunks)
# - Dead code elimination
```

### Memory Leak Prevention

```typescript
// ✅ takeUntilDestroyed — auto-cleanup
private destroyRef = inject(DestroyRef);

ngOnInit() {
  this.service.data$.pipe(
    takeUntilDestroyed(this.destroyRef)
  ).subscribe();
}

// ✅ AsyncPipe — auto-subscribes and unsubscribes
template: `{{ data$ | async }}`
```

---

## Diagram

```
Performance Optimization Checklist:

Bundle Size:
├── Lazy load features ─────────── 30-60% reduction
├── Tree shaking ────────────────── Automatic (prod build)
├── @defer for heavy components ── Defer non-critical UI
└── Import only what you need ──── Avoid barrel files with side effects

Change Detection:
├── OnPush strategy ─────────────── 50-80% fewer checks
├── track in @for ───────────────── Reuse DOM nodes
├── Pure pipes ──────────────────── Cached computation
├── Signals / computed ──────────── Granular updates
└── Avoid template methods ──────── Use pipes or signals

Rendering:
├── Virtual scrolling ───────────── 100K items → render 20
├── Pagination ──────────────────── Load data in chunks
├── @defer (on viewport) ────────── Load when visible
└── Progressive loading ─────────── Skeleton screens

Network:
├── shareReplay ─────────────────── Prevent duplicate HTTP
├── debounceTime ────────────────── Reduce API calls
├── Caching (service/interceptor)── Avoid redundant fetches
└── Compression (gzip/brotli) ───── 60-80% smaller transfers
```

---

## Code

```typescript
// Performance-optimized data table component
@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [ScrollingModule, AsyncPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar">
      <input [formControl]="searchControl" placeholder="Search...">
      <span>{{ (filteredCount$ | async) ?? 0 }} results</span>
    </div>

    <cdk-virtual-scroll-viewport itemSize="48" class="table-viewport">
      <div *cdkVirtualFor="let row of filteredData$ | async; trackBy: trackById" class="row">
        <span>{{ row.name }}</span>
        <span>{{ row.email }}</span>
        <span>{{ row.department }}</span>
      </div>
    </cdk-virtual-scroll-viewport>
  `
})
export class DataTableComponent {
  @Input() set data(value: Employee[]) { this.data$.next(value); }
  
  private data$ = new BehaviorSubject<Employee[]>([]);
  searchControl = new FormControl('');

  filteredData$ = combineLatest([
    this.data$,
    this.searchControl.valueChanges.pipe(startWith(''), debounceTime(200))
  ]).pipe(
    map(([data, search]) => {
      if (!search) return data;
      const term = search.toLowerCase();
      return data.filter(d => 
        d.name.toLowerCase().includes(term) ||
        d.email.toLowerCase().includes(term)
      );
    }),
    shareReplay(1)
  );

  filteredCount$ = this.filteredData$.pipe(map(d => d.length));
  trackById = (_: number, item: Employee) => item.id;
}
```

---

## Interview Questions and Answers

**Q1: How do you improve Angular application performance?**
> Bundle: lazy loading, tree shaking, build budgets. Change Detection: OnPush strategy, pure pipes, avoid template methods, track in loops. Rendering: virtual scrolling for large lists, pagination, @defer. Network: debounce inputs, shareReplay to prevent duplicate requests, HTTP caching. These combined can reduce load time by 50%+ and make the app feel instant.

**Q2: What is virtual scrolling and when do you use it?**
> Virtual scrolling (CDK ScrollingModule) only renders DOM nodes for visible items in a scrollable list. For 10,000 items with 50px height in a 400px container, it renders ~8-10 nodes instead of 10,000. Use when displaying large lists (>100 items). Dramatically reduces initial render time and memory usage.

**Q3: Why should you avoid calling methods in templates?**
> Template methods execute on every change detection cycle — potentially many times per second. A filter method on 1000 items runs O(1000) * times_per_second. Solutions: pure pipes (cached), computed signals (cached), precomputed properties (computed once). These only recalculate when inputs actually change.

**Q4: How does lazy loading improve performance?**
> Lazy loading defers downloading JavaScript for features until needed. Initial bundle might be 400KB instead of 1.2MB. First Contentful Paint is faster. Users navigating to lazy routes experience a brief delay on first visit, but subsequent visits use cached chunks. Combined with preloading, the delay is eliminated.

**Q5: What is the impact of OnPush change detection?**
> OnPush skips change detection for components whose inputs haven't changed. In a tree of 100 components, if only 5 have input changes, Angular checks 5 instead of 100. Real-world reduction: 50-80% fewer CD cycles. Requires immutable data patterns (new references for changes).

---

## Best Practices

1. **Use OnPush everywhere** — default for all new components.
2. **Lazy load all feature routes** — measure with build budgets.
3. **Virtual scroll for lists > 100 items**.
4. **Debounce user input** — 300ms is standard.
5. **Use `track` with unique IDs** in all @for loops.
6. **Use pure pipes** instead of template methods.
7. **Use `shareReplay`** for shared data Observables.
8. **Profile with Angular DevTools** before optimizing.
9. **Set build budgets** in angular.json (500KB warning, 1MB error).

---

## Production Considerations

- **Core Web Vitals**: LCP < 2.5s, FID < 100ms, CLS < 0.1.
- **Bundle analysis**: `ng build --stats-json` + `webpack-bundle-analyzer`.
- **CDN**: Serve static assets from edge locations.
- **Compression**: Enable Brotli/Gzip on server (60-80% size reduction).
- **Service Worker**: Cache assets for instant repeat visits.
- **Preloading**: PreloadAllModules for seamless lazy route navigation.

---

## Related Topics

- → [15. Lazy Loading](./15-lazy-loading.md)
- → [24. Change Detection](./24-change-detection.md)
- → [26. Memory Leaks](./26-memory-leaks.md)
- → [36. Build and Deployment](./36-build-deployment.md)
- → [48. Frontend Performance](./48-frontend-performance.md)
