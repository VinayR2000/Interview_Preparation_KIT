# 27. State Management

---

## Theory

State management is about how you store, update, and share data across your Angular application. The complexity of your state solution should match your app's needs.

### State Management Options (Simple → Complex)

| Approach | Complexity | Use Case |
|----------|-----------|----------|
| Component state | Low | Local UI state |
| Shared service + BehaviorSubject | Low-Medium | Most apps |
| Signals | Low-Medium | Modern Angular apps |
| ComponentStore (NgRx) | Medium | Feature-level state |
| NgRx Store | High | Enterprise, complex state |

### Service + BehaviorSubject (Most Common)

```typescript
@Injectable({ providedIn: 'root' })
export class UserStore {
  private usersSubject = new BehaviorSubject<User[]>([]);
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private selectedSubject = new BehaviorSubject<User | null>(null);

  readonly users$ = this.usersSubject.asObservable();
  readonly loading$ = this.loadingSubject.asObservable();
  readonly selected$ = this.selectedSubject.asObservable();
  readonly userCount$ = this.users$.pipe(map(u => u.length));

  private http = inject(HttpClient);

  loadUsers(): void {
    this.loadingSubject.next(true);
    this.http.get<User[]>('/api/users').pipe(
      finalize(() => this.loadingSubject.next(false))
    ).subscribe(users => this.usersSubject.next(users));
  }

  addUser(user: User): void {
    this.usersSubject.next([...this.usersSubject.value, user]);
  }

  removeUser(id: number): void {
    this.usersSubject.next(this.usersSubject.value.filter(u => u.id !== id));
  }

  selectUser(user: User | null): void {
    this.selectedSubject.next(user);
  }
}
```

### Signal-Based State (Modern Angular)

```typescript
@Injectable({ providedIn: 'root' })
export class TodoStore {
  // State signals
  private todos = signal<Todo[]>([]);
  private filter = signal<'all' | 'active' | 'completed'>('all');

  // Public read-only access
  readonly allTodos = this.todos.asReadonly();
  readonly currentFilter = this.filter.asReadonly();

  // Derived state (computed)
  readonly filteredTodos = computed(() => {
    const todos = this.todos();
    const filter = this.filter();
    switch (filter) {
      case 'active': return todos.filter(t => !t.completed);
      case 'completed': return todos.filter(t => t.completed);
      default: return todos;
    }
  });

  readonly activeCount = computed(() => this.todos().filter(t => !t.completed).length);
  readonly completedCount = computed(() => this.todos().filter(t => t.completed).length);

  // Actions
  addTodo(title: string): void {
    this.todos.update(todos => [...todos, { id: Date.now(), title, completed: false }]);
  }

  toggleTodo(id: number): void {
    this.todos.update(todos =>
      todos.map(t => t.id === id ? { ...t, completed: !t.completed } : t)
    );
  }

  removeTodo(id: number): void {
    this.todos.update(todos => todos.filter(t => t.id !== id));
  }

  setFilter(filter: 'all' | 'active' | 'completed'): void {
    this.filter.set(filter);
  }
}
```

### When NOT to Use NgRx

- Small to medium apps (< 10 features)
- Simple data flow (parent → service → child)
- Team is not familiar with Redux pattern
- App doesn't need time-travel debugging
- State is mostly server-driven (fetch and display)

### When TO Use NgRx

- Large enterprise apps (20+ features, 10+ developers)
- Complex cross-cutting state interactions
- Need for action audit trail
- Need for time-travel debugging (DevTools)
- Multiple state sources that interact
- Offline-first with complex sync logic

---

## Interview Questions and Answers

**Q1: How do you manage state in Angular?**
> For most apps: service with BehaviorSubject or signals. Service holds state, exposes read-only Observables/signals, and provides methods to modify state. Components subscribe via AsyncPipe or read signals directly. For enterprise apps with complex state: NgRx (Redux pattern) with store, actions, reducers, selectors, and effects.

**Q2: BehaviorSubject vs Signal for state?**
> BehaviorSubject: works with RxJS operators, async-friendly, established pattern. Signals: synchronous, computed values are cached/lazy, simpler API, better for template binding, future of Angular. For new code, prefer signals. For complex async flows, BehaviorSubject + RxJS operators still valuable.

**Q3: When would you introduce NgRx?**
> When app has: complex shared state across many features, multiple developers who need predictable state flow, need for DevTools/time-travel debugging, complex side effects (retry, polling, caching). Don't use NgRx just because the app is "large" — if data flow is simple, services suffice.

---

---

## Internal Working

### BehaviorSubject State Flow

```
Service creates BehaviorSubject<User[]>([])
   ↓
Component A subscribes → receives [] immediately
Component B subscribes → receives [] immediately
   ↓
Service: this.usersSubject.next([user1, user2])
   ↓
Component A receives [user1, user2] → template updates
Component B receives [user1, user2] → template updates
   ↓
Component C subscribes LATER → receives [user1, user2] (current value)
   ↓
Service: this.usersSubject.next([user1, user2, user3])
   ↓
ALL 3 components receive new value → templates update
```

### Signal State Flow

```
Service creates signal<User[]>([])
   ↓
Component reads cartStore.items() in template
Angular tracks this read as a dependency
   ↓
Service: this.items.update(i => [...i, newItem])
   ↓
Signal notifies Angular: "this signal changed"
   ↓
ONLY components reading items() are marked dirty
   ↓
Change detection updates ONLY those components
   ↓
Much more granular than BehaviorSubject + zone.js approach
```

---

## Diagram

```
State Management Decision Matrix:

App Complexity    →  Low        Medium       High         Enterprise
Team Size         →  1-2        3-5          5-10         10+
                     │           │            │            │
Solution          →  Component  Service +    Signals +    NgRx
                     state      BehaviorSubj  computed     (full Redux)
                     │           │            │            │
Boilerplate       →  None       Low          Low          High
Debugging tools   →  DevTools   console.log  DevTools     NgRx DevTools
Time-travel debug →  ❌          ❌            ❌            ✅
Action audit log  →  ❌          ❌            ❌            ✅
```

---

## Code

```typescript
// Real-world: Complete feature store with loading/error states
interface EmployeeState {
  employees: Employee[];
  selectedEmployee: Employee | null;
  loading: boolean;
  error: string | null;
  filters: { search: string; department: string; };
}

@Injectable({ providedIn: 'root' })
export class EmployeeStore {
  private http = inject(HttpClient);
  private notification = inject(NotificationService);

  // State
  private state = signal<EmployeeState>({
    employees: [],
    selectedEmployee: null,
    loading: false,
    error: null,
    filters: { search: '', department: '' }
  });

  // Selectors (computed — cached, reactive)
  readonly employees = computed(() => this.state().employees);
  readonly selectedEmployee = computed(() => this.state().selectedEmployee);
  readonly loading = computed(() => this.state().loading);
  readonly error = computed(() => this.state().error);
  readonly filters = computed(() => this.state().filters);
  
  readonly filteredEmployees = computed(() => {
    const { employees, filters } = this.state();
    return employees.filter(e => {
      const matchesSearch = !filters.search || 
        e.name.toLowerCase().includes(filters.search.toLowerCase());
      const matchesDept = !filters.department || 
        e.department === filters.department;
      return matchesSearch && matchesDept;
    });
  });

  readonly employeeCount = computed(() => this.filteredEmployees().length);

  // Actions
  loadEmployees(): void {
    this.patchState({ loading: true, error: null });
    this.http.get<Employee[]>('/api/employees').subscribe({
      next: employees => this.patchState({ employees, loading: false }),
      error: err => this.patchState({ error: err.message, loading: false })
    });
  }

  selectEmployee(employee: Employee | null): void {
    this.patchState({ selectedEmployee: employee });
  }

  setFilters(filters: Partial<EmployeeState['filters']>): void {
    this.patchState({ 
      filters: { ...this.state().filters, ...filters } 
    });
  }

  addEmployee(employee: Employee): void {
    this.patchState({ employees: [...this.state().employees, employee] });
  }

  removeEmployee(id: number): void {
    this.patchState({ 
      employees: this.state().employees.filter(e => e.id !== id) 
    });
  }

  // Helper
  private patchState(patch: Partial<EmployeeState>): void {
    this.state.update(state => ({ ...state, ...patch }));
  }
}
```

---

## Dry Run

### Filter Update Flow

```
Initial state: employees=[Alice(Eng), Bob(Mkt), Carol(Eng)], filters={search:'', dept:''}
filteredEmployees computed: [Alice, Bob, Carol] (all pass)

User types 'a' in search:
Step 1: Component calls store.setFilters({ search: 'a' })
Step 2: patchState({ filters: { search: 'a', department: '' } })
Step 3: state signal updates → filters computed updates → filteredEmployees recomputes
Step 4: Filter: 'alice'.includes('a')=true, 'bob'.includes('a')=false, 'carol'.includes('a')=true
Step 5: filteredEmployees = [Alice, Carol]
Step 6: Template bound to filteredEmployees() → re-renders with 2 items

User selects department='Engineering':
Step 7: store.setFilters({ department: 'Engineering' })
Step 8: filteredEmployees recomputes: search='a' AND dept='Engineering'
Step 9: Alice(Eng, has 'a') ✅, Carol(Eng, has 'a') ✅
Step 10: filteredEmployees = [Alice, Carol] (same result here)
```

---

## Follow-up Questions and Answers

**Q: How do you choose between signals and BehaviorSubject for state?**
> Signals: simpler API, synchronous reads, better performance (granular CD), cached computed values. BehaviorSubject: works with RxJS operators (debounce, switchMap), better for async flows (HTTP-driven state), established pattern. For new Angular 16+ projects, prefer signals. For complex async orchestration, use RxJS.

**Q: How do you handle optimistic updates?**
> Update local state immediately (before server confirms), then either: (a) on success, keep the optimistic state; (b) on failure, rollback to previous state and show error. Provides instant UI feedback while server processes. Store the previous state before the optimistic update for rollback capability.

---

## Common Mistakes

1. **Over-engineering small apps with NgRx**
   ```typescript
   // ❌ 5 files for a simple counter (action, reducer, selector, effect, component)
   // ✅ signal(0) in a service — done
   ```

2. **Mutating state instead of creating new references**
   ```typescript
   // ❌ Mutation — downstream won't detect change
   this.state().employees.push(newEmployee);
   
   // ✅ Immutable update
   this.patchState({ employees: [...this.state().employees, newEmployee] });
   ```

3. **Not centralizing state changes**
   ```typescript
   // ❌ Multiple components directly setting state
   componentA: this.store.state().loading = true;
   componentB: this.store.state().employees = [...];
   
   // ✅ All changes go through store methods
   this.store.loadEmployees(); // Store manages loading state internally
   ```

---

## Best Practices

1. **Start simple** — component state → service → signals → NgRx (escalate as needed).
2. **Single source of truth** — one store per domain (EmployeeStore, CartStore).
3. **Immutable updates** — always create new references (spread operator).
4. **Expose read-only** — .asReadonly() for signals, .asObservable() for subjects.
5. **Centralize mutations** — all state changes through store methods, never external.
6. **Use computed/selectors** — derived state is computed, not stored redundantly.
7. **Keep components thin** — read state, call actions, render.

---

## Production Considerations

- **Debugging**: Use Angular DevTools to inspect signal values and component state.
- **Performance**: Signals with computed are more efficient than BehaviorSubject for template binding.
- **Persistence**: Sync critical state to localStorage for crash recovery.
- **DevTools**: NgRx DevTools provide time-travel debugging for complex state.
- **Memory**: Large state objects held in signals/subjects stay in memory — paginate data.

---

## Related Topics

- → [8. Component Communication](./08-component-communication.md)
- → [12. Services](./12-services.md)
- → [19. Signals](./19-signals.md)
- → [28. NgRx](./28-ngrx.md)
