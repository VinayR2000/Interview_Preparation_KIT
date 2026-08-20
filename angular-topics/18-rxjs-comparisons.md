# 18. RxJS Interview Comparisons — Very Common Interview Area

---

## Theory

Understanding the differences between RxJS flattening operators is one of the most common Angular interview questions. Each operator handles inner Observable subscriptions differently.

### The Big Four — switchMap, mergeMap, concatMap, exhaustMap

| Operator | Behavior | Cancel Previous? | Concurrent? | Use Case |
|----------|----------|-----------------|-------------|----------|
| `switchMap` | Cancels previous, uses latest | ✅ Yes | ❌ No (1 at a time) | Search, route changes |
| `mergeMap` | Runs all concurrently | ❌ No | ✅ Yes (unlimited) | Parallel independent requests |
| `concatMap` | Queues, runs sequentially | ❌ No | ❌ No (sequential) | Ordered operations, saves |
| `exhaustMap` | Ignores new while busy | ❌ No (ignores) | ❌ No (1 at a time) | Prevent duplicate submissions |

### switchMap — Cancel Previous, Use Latest

```typescript
// USE CASE: Search autocomplete, route-based data loading
// BEHAVIOR: When new value arrives, CANCELS previous inner Observable

// Search — only care about latest search term
this.searchInput.valueChanges.pipe(
  debounceTime(300),
  switchMap(term => this.searchService.search(term))
  // Previous HTTP request cancelled if user types again
).subscribe(results => this.results = results);

// Route params — only care about current route
this.route.params.pipe(
  switchMap(params => this.userService.getUser(params['id']))
  // If user navigates quickly, previous request cancelled
).subscribe(user => this.user = user);
```

```
Timeline: switchMap
Input:  ──A──────B──────C──→
              
A: ──a1──a2──╳ (CANCELLED when B arrives)
B: ────b1──b2──╳ (CANCELLED when C arrives)  
C: ──────c1──c2──c3─→

Output: ──a1──b1──c1──c2──c3─→
```

### mergeMap — Run All Concurrently

```typescript
// USE CASE: Independent parallel operations, fire-and-forget
// BEHAVIOR: Subscribes to ALL inner Observables, runs concurrently

// Bulk operations — process all items in parallel
this.items.pipe(
  mergeMap(item => this.apiService.processItem(item))
  // All requests fire simultaneously
).subscribe(result => this.processedResults.push(result));

// ⚠️ With concurrency limit
this.items.pipe(
  mergeMap(item => this.apiService.upload(item), 3) // Max 3 concurrent
).subscribe();
```

```
Timeline: mergeMap
Input:  ──A──B──C──→

A: ──a1──a2──a3─→
B: ──b1──b2──b3─→  (runs simultaneously with A)
C: ──c1──c2──c3─→  (runs simultaneously with A and B)

Output: ──a1─b1─c1─a2─b2─c2─a3─b3─c3─→ (interleaved)
```

### concatMap — Queue and Run Sequentially

```typescript
// USE CASE: Ordered operations, sequential saves, dependent requests
// BEHAVIOR: Queues new values, processes one at a time, in order

// Sequential file uploads (order matters)
this.files.pipe(
  concatMap(file => this.uploadService.upload(file))
  // Each upload waits for previous to complete
).subscribe(result => console.log('Uploaded:', result));

// Chat messages (must arrive in order)
this.messageQueue.pipe(
  concatMap(msg => this.chatService.send(msg))
).subscribe();
```

```
Timeline: concatMap
Input:  ──A──B──C──→

A: ──a1──a2──a3─| (completes)
B:              ──b1──b2──b3─| (starts after A completes)
C:                           ──c1──c2──c3─→ (starts after B)

Output: ──a1──a2──a3──b1──b2──b3──c1──c2──c3─→ (strict order)
```

### exhaustMap — Ignore While Busy

```typescript
// USE CASE: Prevent duplicate submissions, login buttons
// BEHAVIOR: Ignores new values while inner Observable is still running

// Form submit — ignore rapid clicks
this.submitButton.pipe(
  exhaustMap(() => this.orderService.placeOrder(this.form.value))
  // Rapid clicks are IGNORED while first request is pending
).subscribe(order => this.router.navigate(['/orders', order.id]));

// Login — prevent multiple login attempts
this.loginForm.submit.pipe(
  exhaustMap(credentials => this.authService.login(credentials))
).subscribe();
```

```
Timeline: exhaustMap
Input:  ──A──B──C────D──→

A: ──a1──a2──a3─| (processing)
B: (IGNORED — A still running)
C: (IGNORED — A still running)
D:              ──d1──d2─→ (A finished, D accepted)

Output: ──a1──a2──a3──d1──d2─→
```

---

## Internal Working

### How switchMap Cancels

```
switchMap(fn):
  - Maintains reference to ONE inner subscription
  - When new outer value arrives:
    1. Unsubscribe from current inner subscription (cancel)
    2. Call fn(newValue) to create new inner Observable
    3. Subscribe to new inner Observable
  
  For HTTP: unsubscribing triggers AbortController → request cancelled
  Network tab shows cancelled requests
```

### How mergeMap Manages Concurrency

```
mergeMap(fn, concurrent = Infinity):
  - Maintains SET of active inner subscriptions
  - When new outer value arrives:
    1. If active < concurrent: subscribe to new inner immediately
    2. If active >= concurrent: buffer the value, subscribe when one completes
  - Completes only when ALL inners complete AND outer completes
```

---

## Diagram

```
Decision Tree — Which Operator to Use:

Should previous request be cancelled?
├── YES → switchMap (search, route loading)
└── NO
    ├── Must operations run in order?
    │   ├── YES → concatMap (sequential saves, ordered operations)
    │   └── NO
    │       ├── Should new requests be ignored while busy?
    │       │   ├── YES → exhaustMap (form submit, login)
    │       │   └── NO → mergeMap (parallel independent operations)
```

```
Visual Comparison:

Input: Click Click Click (3 rapid clicks)

switchMap:  [Request 1 ╳] [Request 2 ╳] [Request 3 ✓]
            Only last request completes

mergeMap:   [Request 1 ✓] [Request 2 ✓] [Request 3 ✓]
            All 3 requests run and complete

concatMap:  [Request 1 ✓] → [Request 2 ✓] → [Request 3 ✓]
            All 3 run sequentially

exhaustMap: [Request 1 ✓] (clicks 2 & 3 ignored)
            Only first request runs
```

---

## Code

```typescript
// Comparison in the same app context

// 1. switchMap — Live search
searchUsers(term: string): Observable<User[]> {
  return this.searchInput.valueChanges.pipe(
    debounceTime(300),
    switchMap(query => this.http.get<User[]>(`/api/users?q=${query}`))
    // Only latest search matters — cancel stale requests
  );
}

// 2. mergeMap — Batch notification sending
sendNotifications(userIds: number[]): Observable<void> {
  return from(userIds).pipe(
    mergeMap(id => this.http.post<void>(`/api/notify/${id}`, {}), 5)
    // Send to all users, max 5 concurrent requests
  );
}

// 3. concatMap — Sequential form saves (order-dependent)
saveFormSteps(steps: FormStep[]): Observable<SaveResult> {
  return from(steps).pipe(
    concatMap(step => this.http.post<SaveResult>(`/api/steps`, step))
    // Each step depends on previous — must be sequential
  );
}

// 4. exhaustMap — Prevent double-submit
onSubmit(): void {
  this.submit$.pipe(
    exhaustMap(() => this.http.post('/api/orders', this.form.value))
    // Ignore additional clicks while first request is pending
  ).subscribe(order => this.showSuccess(order));
}

// Real interview scenario: Explain why switchMap for search
@Component({
  template: `<input (input)="onSearch($event)">`
})
export class SearchComponent {
  private search$ = new Subject<string>();

  results$ = this.search$.pipe(
    debounceTime(300),        // Wait for user to pause
    distinctUntilChanged(),   // Skip same term
    switchMap(term =>         // ✅ switchMap — cancel previous search
      this.api.search(term).pipe(
        catchError(() => of([]))
      )
    )
  );

  // Why NOT mergeMap?
  // If user types 'ang' then 'angular', mergeMap would keep BOTH requests
  // The 'ang' response might arrive AFTER 'angular' response
  // Result: stale results shown! (race condition)
  
  // switchMap cancels 'ang' request → only 'angular' results shown ✅
}
```

---

## Dry Run

### switchMap Race Condition Prevention

```
Without switchMap (using mergeMap — BAD):
  t=0: User types 'a' → Request A fires (slow server: 800ms)
  t=200: User types 'ab' → Request B fires (fast: 200ms)
  
  t=400: Response B arrives → shows results for 'ab' ✅
  t=800: Response A arrives → OVERWRITES with results for 'a' ❌ (STALE!)

With switchMap (GOOD):
  t=0: User types 'a' → Request A fires
  t=200: User types 'ab' → Request A CANCELLED, Request B fires
  
  t=400: Response B arrives → shows results for 'ab' ✅
  Request A was cancelled — no stale response possible ✅
```

### exhaustMap Duplicate Prevention

```
User rapidly clicks "Place Order" 3 times:
  Click 1 (t=0): exhaustMap subscribes → HTTP POST fires
  Click 2 (t=50): exhaustMap checks → inner still active → IGNORED
  Click 3 (t=100): exhaustMap checks → inner still active → IGNORED
  
  t=500: HTTP response arrives → order placed ONCE ✅
  
  Without exhaustMap: 3 orders would be placed! ❌
```

---

## Interview Questions and Answers

**Q1: Explain the difference between switchMap, mergeMap, concatMap, and exhaustMap.**
> switchMap: cancels previous, uses latest (search autocomplete). mergeMap: runs all concurrently (parallel bulk operations). concatMap: queues and runs sequentially (ordered saves). exhaustMap: ignores new while current is running (prevent double-submit). The key difference is how they handle multiple active inner subscriptions.

**Q2: Why should you use switchMap for search and not mergeMap?**
> mergeMap runs all requests concurrently. If a user types 'a' then 'ab', both requests run. If the 'a' response arrives AFTER 'ab' response (network timing), it would overwrite the correct results with stale ones (race condition). switchMap cancels the 'a' request when 'ab' is typed — only the latest result is used.

**Q3: When would you use concatMap over switchMap?**
> When order matters and you can't afford to lose operations. Example: saving form steps where step 2 depends on step 1 completing. With switchMap, step 1 would be cancelled when step 2 starts. concatMap ensures step 1 completes before step 2 begins. Use for sequential writes, queue processing.

**Q4: How does exhaustMap prevent duplicate form submissions?**
> When the first click triggers a request, exhaustMap subscribes to it. Any subsequent clicks while that request is pending are completely ignored — no new subscription is created. Only after the first request completes (or errors) will the next click be processed.

**Q5: What happens if you use mergeMap for a form submit button?**
> Every click creates a new HTTP request. If the user clicks 5 times, 5 identical POST requests are sent. This could create 5 duplicate orders, 5 duplicate records, etc. Use exhaustMap instead to ignore rapid clicks, or debounce the click event.

---

## Common Mistakes

1. **Using mergeMap for search (race condition)**
2. **Using switchMap for saves (cancels the save!)**
3. **Using concatMap for search (slow — queues all requests)**
4. **Not understanding that switchMap cancels HTTP requests**

---

## Best Practices

| Scenario | Use |
|----------|-----|
| Search autocomplete | `switchMap` |
| Route parameter changes | `switchMap` |
| Tab selection loading data | `switchMap` |
| Form submission | `exhaustMap` |
| Login button | `exhaustMap` |
| Refresh button (debounced) | `exhaustMap` |
| Sequential file uploads | `concatMap` |
| Chat message sending | `concatMap` |
| Bulk email sending | `mergeMap` (with concurrency limit) |
| Independent parallel fetches | `mergeMap` |
| Analytics event tracking | `mergeMap` |

---

## Related Topics

- → [17. RxJS](./17-rxjs.md)
- → [20. HTTP Client](./20-http-client.md)
- → [41. Advanced RxJS](./41-advanced-rxjs.md)
