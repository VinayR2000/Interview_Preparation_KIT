# 7. Component Lifecycle

---

## Theory

Angular components go through a series of lifecycle stages from creation to destruction. Angular provides **lifecycle hooks** — methods that allow you to tap into key moments in this lifecycle.

### All Lifecycle Hooks (In Order)

| Hook | When Called | Use Case |
|------|------------|----------|
| `constructor` | DI resolution, before Angular | Inject dependencies only |
| `ngOnChanges` | Before ngOnInit, on every @Input change | React to input changes |
| `ngOnInit` | Once, after first ngOnChanges | Initialization logic, HTTP calls |
| `ngDoCheck` | Every change detection cycle | Custom change detection |
| `ngAfterContentInit` | Once, after content projection | Access projected content |
| `ngAfterContentChecked` | After every content check | React to content changes |
| `ngAfterViewInit` | Once, after view (children) render | Access ViewChild, DOM |
| `ngAfterViewChecked` | After every view check | React to view changes |
| `ngOnDestroy` | Before component destruction | Cleanup (unsubscribe, etc.) |

### Execution Order

```
constructor()
    ↓
ngOnChanges()         ← called with SimpleChanges object
    ↓
ngOnInit()            ← MOST IMPORTANT — initialization here
    ↓
ngDoCheck()           ← every single CD cycle
    ↓
ngAfterContentInit()  ← <ng-content> available
    ↓
ngAfterContentChecked()
    ↓
ngAfterViewInit()     ← @ViewChild available, DOM ready
    ↓
ngAfterViewChecked()
    ↓
[... component lives, responds to changes ...]
    ↓ (on each change detection)
ngOnChanges() → ngDoCheck() → ngAfterContentChecked() → ngAfterViewChecked()
    ↓
ngOnDestroy()         ← cleanup
```

### constructor vs ngOnInit

| Feature | constructor | ngOnInit |
|---------|-------------|----------|
| Purpose | Dependency injection | Initialization logic |
| @Input available? | ❌ Not yet | ✅ Yes |
| DOM available? | ❌ | ❌ (use ngAfterViewInit) |
| When | Before Angular | After Angular sets inputs |
| Best for | Injecting services | HTTP calls, subscriptions |

```typescript
@Component({ ... })
export class UserComponent implements OnInit {
  @Input() userId!: number;
  user: User | null = null;

  // ❌ Wrong — @Input not available yet
  constructor(private userService: UserService) {
    // this.userId is undefined here!
    // this.userService.getUser(this.userId) — WRONG
  }

  // ✅ Correct — @Input is set
  ngOnInit(): void {
    this.userService.getUser(this.userId).subscribe(
      user => this.user = user
    );
  }
}
```

### ngOnChanges — Reacting to Input Changes

```typescript
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

@Component({ selector: 'app-user-detail', ... })
export class UserDetailComponent implements OnChanges {
  @Input() userId!: number;
  @Input() role!: string;
  
  user: User | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    // SimpleChanges contains all changed @Input properties
    console.log(changes);
    // {
    //   userId: { previousValue: 1, currentValue: 2, firstChange: false },
    //   role: { previousValue: 'user', currentValue: 'admin', firstChange: false }
    // }

    if (changes['userId']) {
      const change = changes['userId'];
      console.log(`userId: ${change.previousValue} → ${change.currentValue}`);
      console.log(`First change: ${change.firstChange}`);
      
      // Reload user when userId changes
      this.loadUser(change.currentValue);
    }

    if (changes['role'] && !changes['role'].firstChange) {
      // React to role change (skip first change)
      this.updatePermissions(changes['role'].currentValue);
    }
  }

  private loadUser(id: number): void { /* HTTP call */ }
  private updatePermissions(role: string): void { /* logic */ }
}
```

### ngOnInit — Initialization

```typescript
@Component({ ... })
export class DashboardComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  stats: DashboardStats | null = null;
  
  private statsService = inject(StatsService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    // ✅ HTTP calls
    this.statsService.getStats()
      .pipe(takeUntil(this.destroy$))
      .subscribe(stats => this.stats = stats);

    // ✅ Route parameter subscriptions
    this.route.params
      .pipe(takeUntil(this.destroy$))
      .subscribe(params => this.loadData(params['id']));

    // ✅ Complex initialization
    this.initializeWebSocket();
    this.setupEventListeners();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

### ngAfterViewInit — DOM and ViewChild Access

```typescript
@Component({
  template: `
    <div #chartContainer></div>
    <app-data-table #table></app-data-table>
  `
})
export class ReportComponent implements AfterViewInit {
  @ViewChild('chartContainer') chartEl!: ElementRef;
  @ViewChild('table') dataTable!: DataTableComponent;

  ngAfterViewInit(): void {
    // ✅ DOM element is available now
    this.initChart(this.chartEl.nativeElement);
    
    // ✅ Child component is available
    this.dataTable.refresh();
    
    // ⚠️ Don't modify component state here (causes ExpressionChangedAfterItHasBeenCheckedError)
    // Use setTimeout or ChangeDetectorRef.detectChanges() if needed
  }

  private initChart(element: HTMLElement): void {
    // Initialize third-party chart library
  }
}
```

### ngOnDestroy — Cleanup

```typescript
@Component({ ... })
export class LiveDataComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  private intervalId: number | null = null;
  private ws: WebSocket | null = null;

  ngOnInit(): void {
    // Subscription
    this.dataService.getLiveData()
      .pipe(takeUntil(this.destroy$))
      .subscribe(data => this.processData(data));

    // Interval
    this.intervalId = window.setInterval(() => this.poll(), 5000);

    // WebSocket
    this.ws = new WebSocket('wss://api.example.com/ws');
    this.ws.onmessage = (event) => this.handleMessage(event);
  }

  ngOnDestroy(): void {
    // ✅ Complete the destroy subject (unsubscribes all takeUntil pipes)
    this.destroy$.next();
    this.destroy$.complete();

    // ✅ Clear intervals
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }

    // ✅ Close WebSocket
    if (this.ws) {
      this.ws.close();
    }

    // ✅ Remove event listeners
    // ✅ Cancel pending HTTP requests
    // ✅ Disconnect from external libraries
  }
}
```

### ngDoCheck — Custom Change Detection

```typescript
@Component({ 
  changeDetection: ChangeDetectionStrategy.OnPush,
  ...
})
export class DeepCheckComponent implements DoCheck {
  @Input() config!: { theme: string; fontSize: number };
  
  private previousTheme = '';

  ngDoCheck(): void {
    // Custom deep-check that OnPush wouldn't catch
    if (this.config && this.config.theme !== this.previousTheme) {
      this.previousTheme = this.config.theme;
      this.applyTheme(this.config.theme);
    }
  }
}
// ⚠️ ngDoCheck runs VERY frequently — keep it lightweight!
```

---

## Internal Working

### Lifecycle Hook Invocation by Angular

```
Component Creation:
1. Angular resolves DI → constructor()
2. Angular sets @Input() properties
3. Angular calls ngOnChanges(changes) — all inputs as "firstChange: true"
4. Angular calls ngOnInit() — once only
5. Angular calls ngDoCheck()
6. Angular projects content → ngAfterContentInit() — once only
7. Angular checks content → ngAfterContentChecked()
8. Angular renders view → ngAfterViewInit() — once only
9. Angular checks view → ngAfterViewChecked()

Component Update (e.g., parent input changes):
1. Angular sets new @Input() values
2. ngOnChanges(changes) — with firstChange: false
3. ngDoCheck()
4. ngAfterContentChecked()
5. ngAfterViewChecked()

Component Destruction:
1. ngOnDestroy()
2. Remove from DOM
3. GC collects instance
```

### Why ngOnInit and not constructor?

```
constructor timing:
  - Before Angular processes the component
  - @Input() values NOT set yet
  - Template NOT rendered
  - Child components NOT created
  - Useful ONLY for DI

ngOnInit timing:
  - After Angular sets all @Input() values
  - After first ngOnChanges
  - Component is "ready" from Angular's perspective
  - Perfect for initialization that depends on inputs
```

### ExpressionChangedAfterItHasBeenCheckedError

```
This error occurs when you modify component state in ngAfterViewInit/ngAfterViewChecked:

CD Cycle:
1. Check bindings → record values
2. Render view
3. ngAfterViewInit/ngAfterViewChecked fires
4. You modify a property here
5. Angular (dev mode) re-checks → value different from step 1!
6. ERROR: Expression has changed after it was checked

Fix: 
  - setTimeout(() => this.value = newValue)
  - OR this.cdr.detectChanges()
  - OR restructure to avoid state change in these hooks
```

---

## Diagram

```
Component Lifecycle Timeline:
┌─────────────────────────────────────────────────────────────────┐
│ CREATION PHASE                                                   │
├─────────────────────────────────────────────────────────────────┤
│ constructor()        │ DI only, no Angular context               │
│         ↓            │                                           │
│ ngOnChanges()        │ @Input() values set (firstChange: true)  │
│         ↓            │                                           │
│ ngOnInit()           │ ★ Main initialization hook               │
│         ↓            │                                           │
│ ngDoCheck()          │ Custom change detection                   │
│         ↓            │                                           │
│ ngAfterContentInit() │ <ng-content> projected                   │
│         ↓            │                                           │
│ ngAfterContentChecked()                                         │
│         ↓            │                                           │
│ ngAfterViewInit()    │ ★ @ViewChild available, DOM ready        │
│         ↓            │                                           │
│ ngAfterViewChecked() │                                           │
├─────────────────────────────────────────────────────────────────┤
│ UPDATE PHASE (repeats on changes)                                │
├─────────────────────────────────────────────────────────────────┤
│ ngOnChanges()        │ @Input() reference changed               │
│         ↓            │                                           │
│ ngDoCheck()          │ Every CD cycle                           │
│         ↓            │                                           │
│ ngAfterContentChecked()                                         │
│         ↓            │                                           │
│ ngAfterViewChecked() │                                           │
├─────────────────────────────────────────────────────────────────┤
│ DESTRUCTION PHASE                                                │
├─────────────────────────────────────────────────────────────────┤
│ ngOnDestroy()        │ ★ Cleanup: unsubscribe, close, clear     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Code

```typescript
// Complete lifecycle demonstration
@Component({
  selector: 'app-lifecycle-demo',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>User: {{ user?.name }}</h2>
    <p>Render count: {{ renderCount }}</p>
    <div #container></div>
  `
})
export class LifecycleDemoComponent implements 
    OnChanges, OnInit, DoCheck, 
    AfterContentInit, AfterContentChecked,
    AfterViewInit, AfterViewChecked, 
    OnDestroy {

  @Input() userId!: number;
  @ViewChild('container') container!: ElementRef;
  @ContentChild('projected') projectedContent: any;

  user: User | null = null;
  renderCount = 0;

  private destroy$ = new Subject<void>();
  private userService = inject(UserService);
  private cdr = inject(ChangeDetectorRef);

  constructor() {
    console.log('1. constructor — DI complete');
    // this.userId is undefined here
  }

  ngOnChanges(changes: SimpleChanges): void {
    console.log('2. ngOnChanges', changes);
    if (changes['userId'] && !changes['userId'].firstChange) {
      // Reload user when ID changes
      this.loadUser(changes['userId'].currentValue);
    }
  }

  ngOnInit(): void {
    console.log('3. ngOnInit — userId:', this.userId);
    this.loadUser(this.userId);
  }

  ngDoCheck(): void {
    console.log('4. ngDoCheck');
    this.renderCount++;
  }

  ngAfterContentInit(): void {
    console.log('5. ngAfterContentInit — projected content ready');
  }

  ngAfterContentChecked(): void {
    console.log('6. ngAfterContentChecked');
  }

  ngAfterViewInit(): void {
    console.log('7. ngAfterViewInit — DOM ready');
    // Safe to access @ViewChild here
    console.log('Container:', this.container.nativeElement);
  }

  ngAfterViewChecked(): void {
    console.log('8. ngAfterViewChecked');
  }

  ngOnDestroy(): void {
    console.log('9. ngOnDestroy — cleanup');
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadUser(id: number): void {
    this.userService.getUser(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => this.user = user);
  }
}
```

```typescript
// Modern Angular pattern with DestroyRef (Angular 16+)
import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({ ... })
export class ModernComponent implements OnInit {
  private userService = inject(UserService);
  private destroyRef = inject(DestroyRef);
  
  // Can also use takeUntilDestroyed() in injection context
  users$ = this.userService.getUsers().pipe(
    takeUntilDestroyed() // Works in field initializers (injection context)
  );

  ngOnInit(): void {
    // For subscriptions outside injection context
    this.userService.notifications$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(notification => this.showNotification(notification));
  }
}
```

---

## Dry Run

### Parent Changes Input

```
Parent template: <app-user [userId]="selectedId">
Parent sets: selectedId = 1

CREATION:
1. constructor() — UserService injected
2. ngOnChanges({ userId: { prev: undefined, curr: 1, firstChange: true } })
3. ngOnInit() — this.userId = 1 → loadUser(1)
4. ngDoCheck()
5. ngAfterContentInit()
6. ngAfterContentChecked()
7. ngAfterViewInit() — @ViewChild available
8. ngAfterViewChecked()

Parent changes: selectedId = 2

UPDATE:
9. ngOnChanges({ userId: { prev: 1, curr: 2, firstChange: false } })
   → loadUser(2) called
10. ngDoCheck()
11. ngAfterContentChecked()
12. ngAfterViewChecked()

Component removed from DOM (e.g., @if becomes false):
13. ngOnDestroy() → destroy$.next() → all subscriptions cleaned up
```

---

## Complexity

| Hook | Frequency | Performance Impact |
|------|-----------|-------------------|
| constructor | Once | None |
| ngOnChanges | Per input change | Low (only on input change) |
| ngOnInit | Once | None |
| ngDoCheck | Every CD cycle | ⚠️ HIGH if logic is heavy |
| ngAfterContentInit | Once | None |
| ngAfterContentChecked | Every CD cycle | ⚠️ Keep lightweight |
| ngAfterViewInit | Once | None |
| ngAfterViewChecked | Every CD cycle | ⚠️ Keep lightweight |
| ngOnDestroy | Once | None |

---

## Real Project Usage

```typescript
// Real-world: Component with route params, WebSocket, and cleanup
@Component({
  selector: 'app-chat-room',
  standalone: true,
  imports: [CommonModule, FormsModule, AsyncPipe],
  template: `
    <div class="messages" #messageContainer>
      @for (msg of messages; track msg.id) {
        <div class="message" [class.own]="msg.senderId === currentUserId">
          <strong>{{ msg.senderName }}</strong>
          <p>{{ msg.text }}</p>
          <small>{{ msg.timestamp | date:'shortTime' }}</small>
        </div>
      }
    </div>
    <input [(ngModel)]="newMessage" (keyup.enter)="send()">
  `
})
export class ChatRoomComponent implements OnInit, AfterViewChecked, OnDestroy {
  @ViewChild('messageContainer') messageContainer!: ElementRef;
  
  private route = inject(ActivatedRoute);
  private chatService = inject(ChatService);
  private destroyRef = inject(DestroyRef);
  
  messages: ChatMessage[] = [];
  newMessage = '';
  currentUserId = '';
  private shouldScrollToBottom = false;

  ngOnInit(): void {
    // Subscribe to route changes
    this.route.params.pipe(
      takeUntilDestroyed(this.destroyRef),
      switchMap(params => {
        const roomId = params['roomId'];
        return this.chatService.joinRoom(roomId);
      })
    ).subscribe(messages => {
      this.messages = messages;
      this.shouldScrollToBottom = true;
    });

    // Listen for new messages via WebSocket
    this.chatService.onNewMessage$.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(msg => {
      this.messages = [...this.messages, msg];
      this.shouldScrollToBottom = true;
    });
  }

  ngAfterViewChecked(): void {
    // Scroll to bottom when new messages arrive
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  ngOnDestroy(): void {
    // Leave chat room (WebSocket cleanup handled by service)
    this.chatService.leaveCurrentRoom();
  }

  send(): void {
    if (this.newMessage.trim()) {
      this.chatService.sendMessage(this.newMessage);
      this.newMessage = '';
    }
  }

  private scrollToBottom(): void {
    const el = this.messageContainer.nativeElement;
    el.scrollTop = el.scrollHeight;
  }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between constructor and ngOnInit?**
> Constructor is a TypeScript/ES6 class feature used only for dependency injection. At constructor time, Angular hasn't set @Input values or rendered the template. ngOnInit is an Angular lifecycle hook called after inputs are set and the component is initialized. Always put initialization logic in ngOnInit, not the constructor.

**Q2: When is ngOnChanges called and what does it receive?**
> ngOnChanges is called before ngOnInit (with firstChange: true) and again whenever any @Input() property's reference changes. It receives a `SimpleChanges` object mapping input names to `SimpleChange` objects containing `previousValue`, `currentValue`, and `firstChange` boolean. It's not called if you mutate an object's property — only on reference change.

**Q3: How do you prevent memory leaks in Angular components?**
> Use `takeUntilDestroyed()` (Angular 16+) or the `takeUntil(destroy$)` pattern with a Subject that emits in ngOnDestroy. AsyncPipe auto-unsubscribes. Also clear intervals, close WebSockets, and remove event listeners in ngOnDestroy. For Angular 16+, `DestroyRef` with `takeUntilDestroyed()` is the cleanest approach.

**Q4: Why do you get ExpressionChangedAfterItHasBeenCheckedError?**
> In dev mode, Angular runs change detection twice to verify no binding changed between the check and re-check. If you modify a property in ngAfterViewInit/ngAfterViewChecked, the second check finds a different value and throws. Fixes: use `setTimeout()`, `ChangeDetectorRef.detectChanges()`, or restructure to avoid late state changes.

**Q5: What is the difference between ngAfterContentInit and ngAfterViewInit?**
> `ngAfterContentInit` fires after content projected via `<ng-content>` is initialized — use it to access `@ContentChild`. `ngAfterViewInit` fires after the component's own view (including child components in its template) is initialized — use it to access `@ViewChild` and DOM elements.

---

## Follow-up Questions and Answers

**Q: What is DestroyRef and takeUntilDestroyed?**
> Angular 16 introduced `DestroyRef` — an injectable reference to the component's destruction event. `takeUntilDestroyed()` from `@angular/core/rxjs-interop` uses it to automatically unsubscribe when the component is destroyed. In injection context (field initializers), no argument needed; elsewhere, pass `inject(DestroyRef)`.

**Q: Can you call ngOnInit manually?**
> Technically yes — it's just a method. But Angular won't re-call it, and it won't re-trigger initialization. If you need to re-initialize, extract logic to a separate method and call it from both ngOnInit and wherever else needed.

**Q: Why should you avoid logic in ngDoCheck?**
> ngDoCheck runs on every single change detection cycle — potentially hundreds of times per second. Heavy logic here kills performance. Use it only for lightweight custom comparisons that Angular's default check misses (e.g., deep object comparison). Never put HTTP calls or complex computation here.

---

## Common Mistakes

1. **Putting initialization logic in constructor**
   ```typescript
   // ❌ @Input not available yet
   constructor(private service: UserService) {
     this.service.getUser(this.userId); // userId is undefined!
   }
   
   // ✅ Use ngOnInit
   ngOnInit() { this.service.getUser(this.userId); }
   ```

2. **Not unsubscribing from Observables**
   ```typescript
   // ❌ Memory leak — subscription lives forever
   ngOnInit() { this.service.data$.subscribe(d => this.data = d); }
   
   // ✅ Auto-cleanup
   ngOnInit() {
     this.service.data$.pipe(takeUntilDestroyed(this.destroyRef))
       .subscribe(d => this.data = d);
   }
   ```

3. **Modifying state in ngAfterViewInit without detectChanges**
   ```typescript
   // ❌ ExpressionChangedAfterItHasBeenCheckedError
   ngAfterViewInit() { this.title = 'Loaded'; }
   
   // ✅ Trigger CD explicitly
   ngAfterViewInit() {
     this.title = 'Loaded';
     this.cdr.detectChanges();
   }
   ```

4. **Heavy computation in ngDoCheck/ngAfterViewChecked**
   ```typescript
   // ❌ Runs every CD cycle (many times/sec)
   ngDoCheck() { this.filteredItems = this.items.filter(/* complex */); }
   
   // ✅ Use ngOnChanges or computed signals
   ngOnChanges() { this.filteredItems = this.items.filter(/* complex */); }
   ```

---

## Best Practices

1. **Use ngOnInit** for all initialization (HTTP, subscriptions, setup).
2. **Use ngOnDestroy** (or `takeUntilDestroyed`) for all cleanup.
3. **Use ngOnChanges** to react to specific @Input changes.
4. **Use ngAfterViewInit** to access ViewChild/DOM.
5. **Avoid ngDoCheck** unless you have a specific custom detection need.
6. **Keep "checked" hooks lightweight** (ngAfterContentChecked, ngAfterViewChecked).
7. **Prefer `takeUntilDestroyed()`** (Angular 16+) over manual destroy$ subject.
8. **Never put async calls in constructor**.

---

## Production Considerations

- **Memory leaks** from unsubscribed Observables are the #1 Angular production bug.
- **Route navigation** doesn't destroy components if the same route/component is reused — use ngOnChanges or route params subscription.
- **Heavy ngDoCheck** can cause frame drops — profile with Angular DevTools.
- **OnPush components**: ngOnChanges still fires on input changes, but ngDoCheck fires less frequently.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [8. Component Communication](./08-component-communication.md)
- → [10. View Queries](./10-view-queries.md)
- → [17. RxJS](./17-rxjs.md)
- → [24. Change Detection](./24-change-detection.md)
- → [26. Memory Leaks](./26-memory-leaks.md)
