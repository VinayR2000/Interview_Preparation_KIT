# 41. Advanced RxJS

---

## Theory

Advanced RxJS covers higher-order Observables, multicasting, custom operators, error propagation strategies, and complex composition patterns.

### Higher-Order Observables

```typescript
// Observable that emits Observables (Observable<Observable<T>>)
// Flattening operators convert them to Observable<T>

// Example: Each click triggers an HTTP request (Observable of Observables)
const clicks$ = fromEvent(button, 'click');
const responses$ = clicks$.pipe(
  switchMap(() => http.get('/api/data'))  // Flatten: Observable<Observable<Data>> → Observable<Data>
);
```

### Multicasting with share/shareReplay

```typescript
// Problem: Cold Observable creates new execution per subscriber
const data$ = http.get('/api/users');
data$.subscribe(a => ...); // HTTP request 1
data$.subscribe(b => ...); // HTTP request 2 (duplicate!)

// Solution: shareReplay makes it hot (shared)
const sharedData$ = http.get('/api/users').pipe(
  shareReplay({ bufferSize: 1, refCount: true })
);
sharedData$.subscribe(a => ...); // HTTP request 1
sharedData$.subscribe(b => ...); // Gets cached result (no new request)

// refCount: true — unsubscribe all → resets (next subscribe triggers new request)
// refCount: false — cached forever (even after all unsubscribe)
```

### Custom Operators

```typescript
// Custom operator: retry with exponential backoff
function retryWithBackoff<T>(maxRetries: number, baseDelay: number): MonoTypeOperatorFunction<T> {
  return (source: Observable<T>) => source.pipe(
    retry({
      count: maxRetries,
      delay: (error, retryCount) => {
        const delay = baseDelay * Math.pow(2, retryCount - 1);
        console.log(`Retry ${retryCount} in ${delay}ms`);
        return timer(delay);
      }
    })
  );
}

// Usage
this.http.get('/api/data').pipe(
  retryWithBackoff(3, 1000) // Retry 3 times: 1s, 2s, 4s
);

// Custom operator: debug/log
function debug<T>(tag: string): MonoTypeOperatorFunction<T> {
  return tap({
    next: value => console.log(`[${tag}] next:`, value),
    error: err => console.error(`[${tag}] error:`, err),
    complete: () => console.log(`[${tag}] complete`)
  });
}
```

### Error Propagation and Recovery

```typescript
// Error kills the stream — subsequent values are lost
source$.pipe(
  map(v => { if (v < 0) throw new Error('negative'); return v; }),
  // After error: stream is DEAD, no more values
);

// Recovery: catchError returns new Observable
source$.pipe(
  mergeMap(id => this.http.get(`/api/${id}`).pipe(
    catchError(err => {
      console.error(`Failed for ${id}:`, err);
      return EMPTY; // Skip this item, continue with others
    })
  ))
);

// Retry then fallback
source$.pipe(
  retryWithBackoff(3, 1000),
  catchError(() => of(defaultValue)) // Final fallback after all retries fail
);
```

### Complex Composition

```typescript
// combineLatest — emit when ANY source emits (after all emit once)
const filters$ = combineLatest([
  this.search$.pipe(debounceTime(300)),
  this.category$,
  this.sortOrder$
]).pipe(
  switchMap(([search, category, sort]) => 
    this.api.getProducts({ search, category, sort })
  )
);

// forkJoin — wait for ALL to complete (parallel requests)
const dashboard$ = forkJoin({
  users: this.userService.getCount(),
  orders: this.orderService.getRecent(),
  revenue: this.statsService.getRevenue()
});

// withLatestFrom — combine with latest from another (on source emission only)
this.saveButton$.pipe(
  withLatestFrom(this.form.valueChanges),
  exhaustMap(([_, formValue]) => this.api.save(formValue))
);

// scan — running state (like Redux reducer)
const notifications$ = this.notificationService.events$.pipe(
  scan((state: Notification[], event) => {
    if (event.type === 'add') return [...state, event.notification];
    if (event.type === 'remove') return state.filter(n => n.id !== event.id);
    return state;
  }, [])
);
```

### Subjects for Event Bus

```typescript
// Application-wide event bus
@Injectable({ providedIn: 'root' })
export class EventBus {
  private events$ = new Subject<AppEvent>();

  emit(event: AppEvent): void {
    this.events$.next(event);
  }

  on<T extends AppEvent>(eventType: string): Observable<T> {
    return this.events$.pipe(
      filter(event => event.type === eventType)
    ) as Observable<T>;
  }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between share() and shareReplay()?**
> `share()` multicasts to current subscribers but late subscribers miss past values. `shareReplay(n)` buffers the last n values and replays them to new subscribers. For HTTP caching: `shareReplay(1)` caches one response. With `refCount: true`, the source resets when all unsubscribe.

**Q2: How do you create custom RxJS operators?**
> A custom operator is a function that takes an Observable and returns an Observable. Use `pipe()` internally to compose existing operators. Return type is `MonoTypeOperatorFunction<T>` (same type in/out) or `OperatorFunction<T, R>` (type transform). Example: retry with backoff, debug logging, caching.

**Q3: What is the difference between combineLatest and forkJoin?**
> `combineLatest` emits whenever ANY source emits (after all have emitted at least once) — good for reactive filters. `forkJoin` emits once when ALL sources COMPLETE — good for parallel HTTP requests where you need all results together. forkJoin is like Promise.all(); combineLatest has no Promise equivalent.

**Q4: How do you handle errors in mergeMap without killing the outer stream?**
> Put `catchError` inside the mergeMap's inner Observable: `mergeMap(id => http.get(id).pipe(catchError(() => EMPTY)))`. This catches errors per-item without terminating the outer stream. If catchError is outside mergeMap, one error kills everything.

---

## Best Practices

1. **Use `shareReplay({ bufferSize: 1, refCount: true })`** for HTTP caching.
2. **Put catchError inside inner Observables** to keep outer stream alive.
3. **Create custom operators** for reusable patterns (retry, logging).
4. **Use combineLatest** for reactive filter/sort/search combinations.
5. **Use forkJoin** for parallel requests where you need all results.
6. **Use scan** for running state accumulation (notification lists, undo history).

---

## Related Topics

- → [17. RxJS](./17-rxjs.md)
- → [18. RxJS Comparisons](./18-rxjs-comparisons.md)
- → [27. State Management](./27-state-management.md)
