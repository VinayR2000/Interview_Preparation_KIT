# 26. Memory Leaks

---

## Theory

Memory leaks in Angular occur when resources (subscriptions, event listeners, timers) are not properly cleaned up when components are destroyed. This causes increasing memory usage, degraded performance, and eventually crashes.

### Common Sources of Memory Leaks

| Source | Cause | Fix |
|--------|-------|-----|
| Observable subscriptions | Not unsubscribing | takeUntilDestroyed, AsyncPipe |
| setInterval/setTimeout | Not clearing | clearInterval in ngOnDestroy |
| Event listeners | Not removing | Remove in ngOnDestroy |
| WebSocket connections | Not closing | Close in ngOnDestroy |
| Subject/BehaviorSubject | Not completing | complete() in ngOnDestroy |
| Third-party libraries | Not disconnecting | Cleanup in ngOnDestroy |

### The takeUntilDestroyed Pattern (Angular 16+)

```typescript
@Component({ ... })
export class UserComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  private userService = inject(UserService);

  ngOnInit(): void {
    // ✅ Auto-unsubscribes when component is destroyed
    this.userService.users$.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(users => this.users = users);

    this.route.params.pipe(
      takeUntilDestroyed(this.destroyRef),
      switchMap(params => this.userService.getUser(+params['id']))
    ).subscribe(user => this.user = user);
  }
}

// In field initializers (injection context) — no argument needed
@Component({ ... })
export class ModernComponent {
  users$ = inject(UserService).users$.pipe(
    takeUntilDestroyed() // Works because we're in injection context
  );
}
```

### The takeUntil Pattern (Pre-Angular 16)

```typescript
@Component({ ... })
export class LegacyComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.service.data$.pipe(
      takeUntil(this.destroy$)
    ).subscribe(data => this.data = data);

    this.anotherService.events$.pipe(
      takeUntil(this.destroy$)
    ).subscribe(event => this.handleEvent(event));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

### AsyncPipe — Best for Templates

```typescript
@Component({
  template: `
    @if (users$ | async; as users) {
      @for (user of users; track user.id) {
        <app-user-card [user]="user" />
      }
    }
  `
})
export class UserListComponent {
  users$ = inject(UserService).getUsers();
  // AsyncPipe subscribes on render, unsubscribes on destroy — zero leak risk
}
```

### Cleaning Up Non-Observable Resources

```typescript
@Component({ ... })
export class DashboardComponent implements OnInit, OnDestroy {
  private intervalId: number | null = null;
  private ws: WebSocket | null = null;
  private resizeHandler = () => this.onResize();

  ngOnInit(): void {
    // Interval
    this.intervalId = window.setInterval(() => this.refreshData(), 30000);

    // WebSocket
    this.ws = new WebSocket('wss://api.example.com/live');
    this.ws.onmessage = (e) => this.handleMessage(e);

    // DOM event listener
    window.addEventListener('resize', this.resizeHandler);
  }

  ngOnDestroy(): void {
    // ✅ Clear interval
    if (this.intervalId) clearInterval(this.intervalId);

    // ✅ Close WebSocket
    if (this.ws) this.ws.close();

    // ✅ Remove event listener
    window.removeEventListener('resize', this.resizeHandler);
  }
}
```

### Detecting Memory Leaks

```
Chrome DevTools:
1. Open Memory tab
2. Take heap snapshot (baseline)
3. Navigate to component, then away (several times)
4. Take another heap snapshot
5. Compare → growing "Detached" elements = leak

Angular DevTools:
1. Open profiler
2. Look for subscription counts that grow over time
3. Check component count during navigation

Symptoms:
- App gets slower over time
- Memory usage grows continuously
- "Detached" DOM nodes in heap snapshot
- Console warnings about destroyed views
```

---

## Code

```typescript
// ❌ Memory leak examples

// Leak 1: Subscription never unsubscribed
ngOnInit() {
  this.service.liveData$.subscribe(data => this.data = data);
  // If component is destroyed and recreated 100 times,
  // 100 subscriptions accumulate — each holding component reference
}

// Leak 2: setInterval not cleared
ngOnInit() {
  setInterval(() => this.poll(), 5000);
  // Runs forever even after component destroyed
}

// Leak 3: Event listener not removed
ngOnInit() {
  document.addEventListener('scroll', this.onScroll.bind(this));
  // Listener holds reference to destroyed component
}

// ✅ Fixed versions
@Component({ ... })
export class SafeComponent implements OnInit, OnDestroy {
  private destroyRef = inject(DestroyRef);
  private intervalId: number | null = null;
  private scrollHandler = this.onScroll.bind(this);

  ngOnInit(): void {
    // Fix 1: takeUntilDestroyed
    this.service.liveData$.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => this.data = data);

    // Fix 2: Store reference for cleanup
    this.intervalId = window.setInterval(() => this.poll(), 5000);

    // Fix 3: Named function for removal
    document.addEventListener('scroll', this.scrollHandler);
  }

  ngOnDestroy(): void {
    if (this.intervalId) clearInterval(this.intervalId);
    document.removeEventListener('scroll', this.scrollHandler);
  }
}
```

---

## Interview Questions and Answers

**Q1: What causes memory leaks in Angular?**
> Unsubscribed Observable subscriptions (router params, service subjects, WebSocket streams), uncleaned intervals/timeouts, event listeners not removed, and third-party library instances not destroyed. HTTP Observables are safe (they complete), but long-lived Observables (subjects, intervals, router) leak if not cleaned up.

**Q2: How do you prevent memory leaks from Observables?**
> Use `takeUntilDestroyed()` (Angular 16+) for programmatic subscriptions. Use `AsyncPipe` in templates (auto-unsubscribes). Use `take(1)` for one-shot operations. For pre-Angular 16: `takeUntil(destroy$)` pattern with Subject that emits in ngOnDestroy.

**Q3: Does HttpClient cause memory leaks?**
> No — HTTP Observables complete after emitting one response. However, if you navigate away BEFORE the response arrives, the subscription still holds a component reference until the response comes. Use `takeUntilDestroyed` to cancel the request on navigation. Also prevents unnecessary processing of stale responses.

**Q4: How do you detect memory leaks?**
> Chrome DevTools Memory tab: take heap snapshots before/after navigation, look for growing "Detached" DOM elements. Angular DevTools: check subscription counts. In code: log ngOnDestroy calls to verify cleanup. Watch for symptoms: app slowdown over time, growing memory in Task Manager.

---

## Best Practices

1. **Use `takeUntilDestroyed()`** for all non-template subscriptions (Angular 16+).
2. **Use `AsyncPipe`** for template-bound Observables.
3. **Clean up intervals, timeouts, WebSockets, event listeners** in ngOnDestroy.
4. **Use named functions** for event listeners (so they can be removed).
5. **Complete Subjects** in ngOnDestroy.
6. **Avoid creating subscriptions in loops** or frequently-called methods.
7. **Profile with Chrome DevTools** periodically during development.
8. **Use `{ refCount: true }`** with shareReplay to avoid stale subscriptions.

---

## Production Considerations

- **Long-lived SPAs** (dashboards, admin panels) are most vulnerable to leaks.
- **Route-heavy apps**: Components created/destroyed frequently multiply the impact.
- **WebSocket connections**: Must be properly closed or they hold connections and memory.
- **Third-party libraries** (charts, maps, editors): Always call their destroy/dispose methods.
- **Monitoring**: Track browser memory usage in production via Performance API.

---

## Related Topics

- → [7. Component Lifecycle](./07-component-lifecycle.md)
- → [17. RxJS](./17-rxjs.md)
- → [24. Change Detection](./24-change-detection.md)
- → [25. Angular Performance](./25-angular-performance.md)
