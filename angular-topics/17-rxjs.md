# 17. RxJS — Extremely Important

---

## Theory

RxJS (Reactive Extensions for JavaScript) is the backbone of Angular's async programming model. It provides Observables for handling asynchronous data streams, events, and state.

### Core Concepts

| Concept | Description |
|---------|-------------|
| **Observable** | Lazy push-based data stream |
| **Observer** | Consumer that receives values |
| **Subscription** | Represents execution, used to cancel |
| **Subject** | Both Observable and Observer |
| **Operators** | Functions that transform streams |
| **Scheduler** | Controls execution timing |

### Observable vs Promise

| Feature | Observable | Promise |
|---------|-----------|---------|
| Values | Multiple over time | Single value |
| Execution | Lazy (runs on subscribe) | Eager (runs immediately) |
| Cancellation | ✅ unsubscribe() | ❌ Cannot cancel |
| Operators | Rich operator library | .then()/.catch() only |
| Retry | Built-in retry/retryWhen | Manual implementation |
| Use in Angular | HttpClient, Router, Forms | Rare |

### Creating Observables

```typescript
import { Observable, of, from, interval, timer, fromEvent, EMPTY, NEVER, throwError } from 'rxjs';

// of — emit values synchronously
const numbers$ = of(1, 2, 3, 4, 5);

// from — convert array/promise/iterable to Observable
const arr$ = from([10, 20, 30]);
const promise$ = from(fetch('/api/data'));

// interval — emit sequential numbers at interval
const ticker$ = interval(1000); // 0, 1, 2, 3... every second

// timer — emit after delay, then optionally at interval
const delayed$ = timer(3000); // emit 0 after 3 seconds
const periodic$ = timer(1000, 5000); // emit after 1s, then every 5s

// fromEvent — DOM events as Observable
const clicks$ = fromEvent(document, 'click');
const input$ = fromEvent(inputEl, 'input');

// Custom Observable
const custom$ = new Observable<number>(subscriber => {
  subscriber.next(1);
  subscriber.next(2);
  setTimeout(() => {
    subscriber.next(3);
    subscriber.complete();
  }, 1000);
  
  // Cleanup function (on unsubscribe)
  return () => console.log('Cleaned up');
});

// EMPTY — completes immediately, emits nothing
// NEVER — never emits, never completes
// throwError — emits error immediately
```

### Subscribing

```typescript
// Full observer object
const subscription = data$.subscribe({
  next: value => console.log('Value:', value),
  error: err => console.error('Error:', err),
  complete: () => console.log('Done')
});

// Shorthand (next only)
data$.subscribe(value => console.log(value));

// Unsubscribe (cancel)
subscription.unsubscribe();
```

### Subjects

```typescript
import { Subject, BehaviorSubject, ReplaySubject, AsyncSubject } from 'rxjs';

// Subject — no initial value, only emits to current subscribers
const subject = new Subject<string>();
subject.subscribe(v => console.log('A:', v));
subject.next('Hello'); // A: Hello
subject.subscribe(v => console.log('B:', v));
subject.next('World'); // A: World, B: World
// B missed 'Hello'

// BehaviorSubject — has initial value, new subscribers get last value
const behavior = new BehaviorSubject<number>(0);
behavior.subscribe(v => console.log('A:', v)); // A: 0 (immediately)
behavior.next(1); // A: 1
behavior.subscribe(v => console.log('B:', v)); // B: 1 (last value)
behavior.next(2); // A: 2, B: 2

// ReplaySubject — replays N last values to new subscribers
const replay = new ReplaySubject<string>(3); // Buffer 3 values
replay.next('a');
replay.next('b');
replay.next('c');
replay.next('d');
replay.subscribe(v => console.log(v)); // b, c, d (last 3)

// AsyncSubject — emits only the LAST value, only on complete
const async$ = new AsyncSubject<number>();
async$.next(1);
async$.next(2);
async$.next(3);
async$.subscribe(v => console.log(v)); // nothing yet
async$.complete(); // NOW emits: 3 (last value)
```

### Cold vs Hot Observables

```
Cold Observable:
  - Created fresh for each subscriber
  - Each subscriber gets all values from start
  - Example: HTTP request (each subscriber triggers new request)
  
  subscriber1 → [1, 2, 3, 4, 5]
  subscriber2 → [1, 2, 3, 4, 5]  (independent execution)

Hot Observable:
  - Shared source, values emitted regardless of subscribers
  - Late subscribers miss past values
  - Example: mouse clicks, WebSocket messages
  
  subscriber1 (from start) → [1, 2, 3, 4, 5]
  subscriber2 (joins at 3) → [3, 4, 5]  (missed 1, 2)
```

### Essential Operators

```typescript
import { map, filter, tap, switchMap, mergeMap, concatMap, exhaustMap,
         debounceTime, distinctUntilChanged, catchError, retry, finalize,
         take, takeUntil, first, startWith, scan, shareReplay,
         forkJoin, combineLatest, zip, withLatestFrom } from 'rxjs/operators';

// Transformation
map(value => value * 2)              // Transform each value
scan((acc, val) => acc + val, 0)     // Running accumulation (like reduce)
startWith(initialValue)              // Emit initial value before source

// Filtering
filter(value => value > 10)          // Only pass values matching condition
take(5)                              // Take first 5 values then complete
takeUntil(destroy$)                  // Take until another Observable emits
first()                              // Take first value then complete
distinctUntilChanged()               // Skip consecutive duplicates
debounceTime(300)                    // Wait 300ms of silence before emitting

// Side Effects
tap(value => console.log(value))     // Peek at values (no modification)

// Error Handling
catchError(err => of(defaultValue))  // Catch error, return fallback
retry(3)                             // Retry failed operation 3 times
finalize(() => cleanup())            // Run on complete OR error

// Higher-Order (flattening)
switchMap(id => http.get(`/api/${id}`))   // Cancel previous, use latest
mergeMap(id => http.get(`/api/${id}`))    // Run all concurrently
concatMap(id => http.get(`/api/${id}`))   // Queue, run sequentially
exhaustMap(id => http.get(`/api/${id}`))  // Ignore new while current runs

// Combination
forkJoin([obs1$, obs2$, obs3$])      // Wait for all to complete
combineLatest([obs1$, obs2$])        // Emit when any emits (all must emit once)
zip(obs1$, obs2$)                    // Pair up emissions 1:1
withLatestFrom(other$)               // Combine with latest from another
```

### pipe() — Composing Operators

```typescript
// All operators are composed using pipe()
this.searchControl.valueChanges.pipe(
  debounceTime(300),
  distinctUntilChanged(),
  filter(term => term.length >= 2),
  switchMap(term => this.searchService.search(term)),
  catchError(err => {
    console.error(err);
    return of([]);
  })
).subscribe(results => this.results = results);
```

---

## Internal Working

### Observable Execution Model

```
Observable created (lazy — nothing happens):
  const obs$ = new Observable(subscriber => { ... });

Subscribe triggers execution:
  obs$.subscribe(observer)
    ↓
  Producer function executes
    ↓
  subscriber.next(value) → observer.next(value)
  subscriber.next(value) → observer.next(value)
  subscriber.complete() → observer.complete()
    ↓
  Subscription ends

Unsubscribe:
  subscription.unsubscribe()
    ↓
  Teardown function executes (cleanup)
    ↓
  No more values delivered
```

### Operator Chain Execution

```
Source: of(1, 2, 3, 4, 5)
  .pipe(
    filter(x => x % 2 === 0),  // Only even
    map(x => x * 10),           // Multiply
    take(1)                     // First only
  )

Execution (value by value):
  1 → filter(odd) → DROPPED
  2 → filter(even) → 2 → map → 20 → take(1) → EMIT 20, COMPLETE
  3, 4, 5 → never processed (take completed the chain)
```

### switchMap Cancellation

```
Source: search input changes
  'a' at t=0ms
  'an' at t=100ms
  'ang' at t=200ms

With switchMap:
  'a' → starts HTTP request 1
  'an' → CANCELS request 1, starts HTTP request 2
  'ang' → CANCELS request 2, starts HTTP request 3
  
  Only request 3's response is used
```

---

## Diagram

```
Observable Stream:
──1──2──3──4──5──|  (| = complete)
──1──2──X         (X = error)

Operator Pipeline:
Source ──→ operator1 ──→ operator2 ──→ operator3 ──→ Subscriber

switchMap vs mergeMap vs concatMap:
Input:  ──A────B────C──→

switchMap (cancel previous):
  A: ──a1──a2──╳ (cancelled by B)
  B: ────b1──b2──╳ (cancelled by C)
  C: ──────c1──c2──c3─→
  Output: ──a1──b1──c1──c2──c3─→

mergeMap (concurrent):
  A: ──a1──a2──a3─→
  B: ────b1──b2──b3─→
  C: ──────c1──c2──c3─→
  Output: ──a1─b1─a2─c1─b2─a3─c2─b3─c3─→

concatMap (sequential):
  A: ──a1──a2──a3─|
  B:              ──b1──b2──b3─|
  C:                           ──c1──c2──c3─→
  Output: ──a1──a2──a3──b1──b2──b3──c1──c2──c3─→

exhaustMap (ignore while busy):
  A: ──a1──a2──a3─|
  B: (ignored — A still running)
  C:              ──c1──c2──c3─→
  Output: ──a1──a2──a3──c1──c2──c3─→
```

---

## Code

```typescript
// Real-world: Search with autocomplete
@Component({
  selector: 'app-search',
  standalone: true,
  imports: [ReactiveFormsModule, AsyncPipe, CommonModule],
  template: `
    <input [formControl]="searchControl" placeholder="Search users...">
    @if (loading) { <span class="spinner"></span> }
    @for (user of results$ | async; track user.id) {
      <div class="result" (click)="select(user)">{{ user.name }}</div>
    }
  `
})
export class SearchComponent {
  searchControl = new FormControl('');
  loading = false;
  
  private userService = inject(UserService);

  results$ = this.searchControl.valueChanges.pipe(
    debounceTime(300),                    // Wait for user to stop typing
    distinctUntilChanged(),               // Skip if same value
    filter(term => !!term && term.length >= 2), // Min 2 chars
    tap(() => this.loading = true),
    switchMap(term =>                     // Cancel previous request
      this.userService.search(term).pipe(
        catchError(() => of([])),         // Return empty on error
        finalize(() => this.loading = false)
      )
    )
  );

  select(user: User): void {
    this.searchControl.setValue(user.name, { emitEvent: false });
  }
}

// Real-world: Polling with pause/resume
@Component({ ... })
export class LiveDashboardComponent implements OnInit {
  private destroyRef = inject(DestroyRef);
  private dashboardService = inject(DashboardService);
  
  isPaused = false;
  data$!: Observable<DashboardData>;

  ngOnInit(): void {
    const pause$ = new BehaviorSubject<boolean>(false);
    
    this.data$ = pause$.pipe(
      switchMap(paused => paused
        ? NEVER  // Stop polling when paused
        : interval(5000).pipe(startWith(0)) // Poll every 5s
      ),
      switchMap(() => this.dashboardService.getData()),
      shareReplay(1),
      takeUntilDestroyed(this.destroyRef)
    );
  }
}

// Real-world: Combine multiple data sources
@Component({ ... })
export class UserProfileComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private userService = inject(UserService);
  private orderService = inject(OrderService);

  vm$!: Observable<{ user: User; orders: Order[]; stats: UserStats }>;

  ngOnInit(): void {
    this.vm$ = this.route.params.pipe(
      map(params => +params['id']),
      switchMap(userId => forkJoin({
        user: this.userService.getUser(userId),
        orders: this.orderService.getUserOrders(userId),
        stats: this.userService.getUserStats(userId)
      }))
    );
  }
}
```

---

## Dry Run

### Search with debounce

```
User types "angular" character by character:

t=0ms: 'a' → debounceTime starts 300ms timer
t=100ms: 'an' → timer reset to 300ms
t=200ms: 'ang' → timer reset to 300ms
t=350ms: 'angu' → timer reset to 300ms
t=500ms: 'angul' → timer reset to 300ms
t=700ms: 'angula' → timer reset to 300ms
t=900ms: 'angular' → timer reset to 300ms
t=1200ms: timer expires! 'angular' emitted
  → distinctUntilChanged: new value → passes
  → filter: length >= 2 → passes
  → switchMap: HTTP GET /api/search?q=angular
  → Response: [{name: 'Angular'}, {name: 'AngularJS'}]
  → results$ emits → template renders 2 results
```

---

## Complexity

| Operator | Time Complexity | Space |
|----------|----------------|-------|
| map, filter, tap | O(1) per emission | O(1) |
| debounceTime | O(1) | O(1) timer |
| distinctUntilChanged | O(1) | O(1) previous value |
| switchMap | O(1) per switch | O(1) inner subscription |
| mergeMap | O(1) per emission | O(n) concurrent subscriptions |
| scan | O(1) per emission | O(1) accumulator |
| shareReplay(n) | O(1) per emission | O(n) buffer |
| forkJoin | O(1) | O(n) pending observables |

---

## Interview Questions and Answers

**Q1: What is an Observable and how is it different from a Promise?**
> Observable is a lazy, push-based data stream that can emit multiple values over time. Promises are eager (execute immediately), emit one value, and can't be cancelled. Observables are lazy (execute on subscribe), emit 0-N values, and can be cancelled via unsubscribe(). Angular uses Observables for HTTP, routing, forms, and events.

**Q2: What is the difference between Subject and BehaviorSubject?**
> Subject has no initial value and only emits to current subscribers — late subscribers miss past values. BehaviorSubject requires an initial value and immediately emits the current (last) value to new subscribers. Use BehaviorSubject for state (always need current value); Subject for events (fire-and-forget).

**Q3: What is the difference between Cold and Hot Observables?**
> Cold Observables create a new execution for each subscriber (like HTTP — each subscribe triggers a new request). Hot Observables share execution — values emit regardless of subscribers (like mouse events). Use `shareReplay()` to make a Cold Observable Hot (share among subscribers).

**Q4: Explain switchMap vs mergeMap vs concatMap vs exhaustMap.**
> switchMap: cancels previous inner Observable when new value arrives (search autocomplete). mergeMap: runs all inner Observables concurrently (parallel requests). concatMap: queues inner Observables, runs sequentially (ordered operations). exhaustMap: ignores new values while current inner is running (prevent duplicate form submissions).

**Q5: How do you prevent memory leaks with Observables?**
> Use takeUntilDestroyed() (Angular 16+), takeUntil(destroy$) pattern, AsyncPipe (auto-unsubscribes), take(1) for one-shot operations, or manually unsubscribe in ngOnDestroy. HTTP Observables complete automatically but router/form/subject observables don't — always clean them up.

---

## Follow-up Questions and Answers

**Q: When should you use shareReplay?**
> When multiple subscribers need the same HTTP response without triggering duplicate requests. `shareReplay(1)` caches the last emission and shares it. Use for data that multiple components need simultaneously (current user, config). Always use `{ refCount: true }` to prevent memory leaks when all unsubscribe.

**Q: What is the difference between catchError and throwing?**
> `catchError` intercepts errors and can return a fallback Observable (graceful degradation). If you re-throw, the error propagates and the Observable terminates. Use catchError for recovery (return default value); use throwError for propagation to global error handler.

---

## Common Mistakes

1. **Subscribing inside subscribe (nested subscriptions)**
   ```typescript
   // ❌ Callback hell, no cancellation
   this.route.params.subscribe(params => {
     this.http.get(`/api/${params['id']}`).subscribe(data => { ... });
   });
   
   // ✅ Use switchMap
   this.route.params.pipe(
     switchMap(params => this.http.get(`/api/${params['id']}`))
   ).subscribe(data => { ... });
   ```

2. **Not unsubscribing from long-lived Observables**
3. **Using mergeMap for search (causes race conditions)**
4. **Multiple subscribes to same HTTP Observable (duplicate requests)**

---

## Interview Gotcha Scenarios

**Gotcha 1: What happens when you subscribe to an HTTP Observable twice?**
```typescript
const users$ = this.http.get<User[]>('/api/users');
users$.subscribe(a => console.log('A', a)); // HTTP request 1
users$.subscribe(b => console.log('B', b)); // HTTP request 2 (ANOTHER request!)

// Cold Observable = new execution per subscriber
// Fix: shareReplay(1) makes it hot (shared)
const shared$ = this.http.get<User[]>('/api/users').pipe(shareReplay(1));
shared$.subscribe(a => ...); // Request fires
shared$.subscribe(b => ...); // Gets cached result, no new request
```

**Gotcha 2: Why does this search have a race condition?**
```typescript
// ❌ mergeMap — responses can arrive out of order
searchTerm$.pipe(
  mergeMap(term => http.get(`/api/search?q=${term}`))
).subscribe(results => this.results = results);

// User types: 'a' (slow response 800ms) then 'ab' (fast response 200ms)
// 'ab' response arrives first → shows correct results
// 'a' response arrives later → OVERWRITES with stale results!

// ✅ switchMap — cancels previous
searchTerm$.pipe(
  switchMap(term => http.get(`/api/search?q=${term}`))
).subscribe(results => this.results = results);
// 'a' request CANCELLED when 'ab' typed → only 'ab' results shown
```

**Gotcha 3: This Observable never emits. Why?**
```typescript
// ❌ No subscribe = no execution (Observables are LAZY)
this.http.get('/api/trigger-job'); // Nothing happens!

// ✅ Must subscribe for Observable to execute
this.http.get('/api/trigger-job').subscribe();
```

**Gotcha 4: Memory leak in this component. Find it.**
```typescript
@Component({ ... })
export class ChatComponent implements OnInit {
  ngOnInit() {
    this.chatService.messages$.subscribe(msg => {  // ❌ Never unsubscribed!
      this.messages.push(msg);
    });
    // chatService.messages$ is a Subject — never completes
    // This subscription lives FOREVER, even after component destroyed
    // If user navigates away and back 10 times → 10 subscriptions accumulate
  }
}
// Fix: takeUntilDestroyed(this.destroyRef)
```

**Gotcha 5: Why doesn't distinctUntilChanged work with objects?**
```typescript
// ❌ Doesn't work — objects compared by REFERENCE (===)
this.formGroup.valueChanges.pipe(
  distinctUntilChanged() // { name: 'a' } !== { name: 'a' } (different objects!)
).subscribe();

// ✅ Custom comparator for objects
this.formGroup.valueChanges.pipe(
  distinctUntilChanged((prev, curr) => JSON.stringify(prev) === JSON.stringify(curr))
).subscribe();
```

---

## Best Practices

1. **Use `pipe()` and operators** instead of nested subscribes.
2. **Use `switchMap`** for search/autocomplete/route-based loading.
3. **Use `takeUntilDestroyed()`** for cleanup (Angular 16+).
4. **Use `AsyncPipe`** in templates — handles subscription lifecycle.
5. **Use `shareReplay(1)`** to prevent duplicate HTTP calls.
6. **Use `catchError`** for graceful error recovery.
7. **Use `debounceTime` + `distinctUntilChanged`** for user input.
8. **Prefer declarative streams** over imperative subscribe-and-set.

---

## Production Considerations

- **Memory leaks**: Unsubscribed Observables are the #1 Angular production issue.
- **Error recovery**: Use `catchError` to keep streams alive after errors.
- **Backpressure**: `debounceTime` and `throttleTime` prevent overwhelming the system.
- **Debugging**: Use `tap()` for logging, Angular DevTools for subscription tracking.
- **Performance**: `shareReplay` prevents redundant API calls across components.

---

## Related Topics

- → [6. Pipes (AsyncPipe)](./06-pipes.md)
- → [12. Services](./12-services.md)
- → [18. RxJS Interview Comparisons](./18-rxjs-comparisons.md)
- → [19. Signals](./19-signals.md)
- → [20. HTTP Client](./20-http-client.md)
- → [41. Advanced RxJS](./41-advanced-rxjs.md)
