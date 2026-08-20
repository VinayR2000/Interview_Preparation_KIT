# 28. NgRx

---

## Theory

NgRx is Angular's implementation of the Redux pattern. It provides a predictable state container with unidirectional data flow.

### NgRx Flow

```
Component dispatches Action
    ↓
Reducer handles Action → produces new State
    ↓ (pure function, no side effects)
Store holds new State
    ↓
Selector reads specific slice of State
    ↓
Component receives updated data

Side effects (HTTP, navigation):
    Action → Effect → API call → Success/Failure Action → Reducer
```

### Core Concepts

| Concept | Purpose |
|---------|---------|
| **Store** | Single source of truth (application state) |
| **Actions** | Events describing what happened |
| **Reducers** | Pure functions that produce new state |
| **Selectors** | Functions to extract state slices (memoized) |
| **Effects** | Handle side effects (HTTP, navigation) |

### Actions

```typescript
import { createAction, props } from '@ngrx/store';

// Define actions
export const loadUsers = createAction('[User List] Load Users');
export const loadUsersSuccess = createAction('[User API] Load Users Success', props<{ users: User[] }>());
export const loadUsersFailure = createAction('[User API] Load Users Failure', props<{ error: string }>());

export const addUser = createAction('[User Form] Add User', props<{ user: CreateUserDTO }>());
export const addUserSuccess = createAction('[User API] Add User Success', props<{ user: User }>());

export const deleteUser = createAction('[User List] Delete User', props<{ id: number }>());
export const selectUser = createAction('[User List] Select User', props<{ user: User }>());
```

### Reducer

```typescript
import { createReducer, on } from '@ngrx/store';

export interface UserState {
  users: User[];
  selectedUser: User | null;
  loading: boolean;
  error: string | null;
}

const initialState: UserState = {
  users: [],
  selectedUser: null,
  loading: false,
  error: null
};

export const userReducer = createReducer(
  initialState,
  on(loadUsers, (state) => ({ ...state, loading: true, error: null })),
  on(loadUsersSuccess, (state, { users }) => ({ ...state, users, loading: false })),
  on(loadUsersFailure, (state, { error }) => ({ ...state, error, loading: false })),
  on(addUserSuccess, (state, { user }) => ({ ...state, users: [...state.users, user] })),
  on(deleteUser, (state, { id }) => ({ ...state, users: state.users.filter(u => u.id !== id) })),
  on(selectUser, (state, { user }) => ({ ...state, selectedUser: user }))
);
```

### Selectors

```typescript
import { createFeatureSelector, createSelector } from '@ngrx/store';

// Feature selector
const selectUserState = createFeatureSelector<UserState>('users');

// Derived selectors (memoized)
export const selectAllUsers = createSelector(selectUserState, state => state.users);
export const selectSelectedUser = createSelector(selectUserState, state => state.selectedUser);
export const selectLoading = createSelector(selectUserState, state => state.loading);
export const selectError = createSelector(selectUserState, state => state.error);

// Composed selectors
export const selectUserCount = createSelector(selectAllUsers, users => users.length);
export const selectActiveUsers = createSelector(selectAllUsers, users => users.filter(u => u.active));
```

### Effects

```typescript
import { Actions, createEffect, ofType } from '@ngrx/effects';

@Injectable()
export class UserEffects {
  private actions$ = inject(Actions);
  private userService = inject(UserService);
  private router = inject(Router);

  loadUsers$ = createEffect(() =>
    this.actions$.pipe(
      ofType(loadUsers),
      switchMap(() =>
        this.userService.getUsers().pipe(
          map(users => loadUsersSuccess({ users })),
          catchError(error => of(loadUsersFailure({ error: error.message })))
        )
      )
    )
  );

  addUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(addUser),
      exhaustMap(({ user }) =>
        this.userService.createUser(user).pipe(
          map(newUser => addUserSuccess({ user: newUser })),
          catchError(error => of(loadUsersFailure({ error: error.message })))
        )
      )
    )
  );

  // Navigate after successful add
  addUserSuccess$ = createEffect(() =>
    this.actions$.pipe(
      ofType(addUserSuccess),
      tap(() => this.router.navigate(['/users']))
    ),
    { dispatch: false } // Don't dispatch another action
  );
}
```

### Component Usage

```typescript
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [AsyncPipe, CommonModule],
  template: `
    @if (loading$ | async) { <app-spinner /> }
    @if (error$ | async; as error) { <app-error [message]="error" /> }
    @for (user of users$ | async; track user.id) {
      <app-user-card [user]="user" (click)="onSelect(user)" (delete)="onDelete(user.id)" />
    }
  `
})
export class UserListComponent implements OnInit {
  private store = inject(Store);

  users$ = this.store.select(selectAllUsers);
  loading$ = this.store.select(selectLoading);
  error$ = this.store.select(selectError);

  ngOnInit(): void {
    this.store.dispatch(loadUsers());
  }

  onSelect(user: User): void {
    this.store.dispatch(selectUser({ user }));
  }

  onDelete(id: number): void {
    this.store.dispatch(deleteUser({ id }));
  }
}
```

### Registration

```typescript
// app.config.ts
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';

export const appConfig: ApplicationConfig = {
  providers: [
    provideStore({ users: userReducer }),
    provideEffects([UserEffects]),
    provideStoreDevtools({ maxAge: 25, logOnly: environment.production })
  ]
};
```

---

## Diagram

```
NgRx Data Flow:
┌──────────────────────────────────────────────────────────┐
│ Component                                                 │
│  ┌────────────────────┐    ┌──────────────────────┐     │
│  │ store.dispatch(     │    │ store.select(        │     │
│  │   loadUsers()       │    │   selectAllUsers     │     │
│  │ )                   │    │ ) | async            │     │
│  └─────────┬──────────┘    └──────────┬───────────┘     │
└────────────┼───────────────────────────┼─────────────────┘
             │ Action                     ↑ State slice
             ↓                           │
┌────────────┴───────────────────────────┴─────────────────┐
│                        Store                              │
│  ┌─────────────┐  ┌──────────┐  ┌───────────────────┐   │
│  │   Actions    │→ │ Reducer  │→ │   State           │   │
│  │ (events)     │  │ (pure fn)│  │ { users: [...] }  │   │
│  └──────┬──────┘  └──────────┘  └─────────┬─────────┘   │
│         │                                   │             │
│         ↓                                   ↓             │
│  ┌─────────────┐              ┌───────────────────────┐  │
│  │  Effects     │              │  Selectors (memoized) │  │
│  │ (side effects│              │  selectAllUsers       │  │
│  │  HTTP calls) │              │  selectLoading        │  │
│  └─────────────┘              └───────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## Interview Questions and Answers

**Q1: What is NgRx and when should you use it?**
> NgRx implements the Redux pattern for Angular: single store, actions describe events, reducers produce new state (pure functions), selectors extract state, effects handle side effects. Use for large enterprise apps with complex shared state, many developers, need for DevTools, or complex async flows. Don't use for simple CRUD apps.

**Q2: What is the role of Effects in NgRx?**
> Effects handle side effects — anything that isn't pure state transformation: HTTP calls, navigation, localStorage, WebSocket. They listen for specific actions, perform async work, and dispatch new actions (success/failure). This keeps reducers pure and testable.

**Q3: What are selectors and why are they memoized?**
> Selectors are functions that extract specific slices of state. They're memoized — they only recompute when their input state changes. This prevents unnecessary re-renders. Composed selectors (`createSelector(selectA, selectB, (a, b) => ...)`) only recompute when either input selector's result changes.

**Q4: What is the difference between switchMap and exhaustMap in Effects?**
> In load effects: use `switchMap` — if user triggers reload, cancel previous load and use latest. In create/submit effects: use `exhaustMap` — ignore duplicate submissions while first is processing. Use `concatMap` for ordered operations. Use `mergeMap` for independent parallel operations.

---

## Best Practices

1. **Action naming**: `[Source] Event Description` — e.g., `[User List] Load Users`.
2. **Reducers must be pure** — no HTTP, no side effects, no mutations.
3. **Use selectors** for all state access (memoized, composable).
4. **Effects for side effects** — HTTP, navigation, localStorage.
5. **Feature state** — each feature has its own state slice, reducer, effects.
6. **Don't over-engineer** — use NgRx only when complexity justifies it.

---

## Related Topics

- → [12. Services](./12-services.md)
- → [17. RxJS](./17-rxjs.md)
- → [19. Signals](./19-signals.md)
- → [27. State Management](./27-state-management.md)
