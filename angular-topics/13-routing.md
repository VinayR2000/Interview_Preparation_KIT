# 13. Routing — Extremely Important

---

## Theory

Angular Router enables navigation between views (components) based on URL. It maps URLs to components and manages browser history.

### Core Concepts

| Concept | Purpose |
|---------|---------|
| `Routes` | Array of route configurations |
| `RouterOutlet` | Placeholder where routed components render |
| `RouterLink` | Directive for navigation links |
| `ActivatedRoute` | Current route information |
| `Router` | Programmatic navigation |

### Basic Route Configuration

```typescript
// app.routes.ts
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'users', component: UserListComponent },
  { path: 'users/:id', component: UserDetailComponent },
  { path: 'users/:id/edit', component: UserEditComponent },
  { path: '**', component: NotFoundComponent }  // Wildcard — must be last
];

// app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes)]
};
```

### RouterOutlet

```html
<!-- app.component.html -->
<app-header />
<nav>
  <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
  <a routerLink="/users" routerLinkActive="active">Users</a>
</nav>
<main>
  <router-outlet></router-outlet>  <!-- Routed component renders here -->
</main>
<app-footer />
```

### RouterLink and routerLinkActive

```html
<!-- Static link -->
<a routerLink="/users">Users</a>

<!-- Dynamic link with parameters -->
<a [routerLink]="['/users', user.id]">{{ user.name }}</a>
<!-- Generates: /users/42 -->

<!-- With query parameters -->
<a [routerLink]="['/users']" [queryParams]="{ page: 1, sort: 'name' }">
  Users
</a>
<!-- Generates: /users?page=1&sort=name -->

<!-- Active class -->
<a routerLink="/users" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
  Users
</a>
```

### Route Parameters

```typescript
// Route: { path: 'users/:id', component: UserDetailComponent }

@Component({ ... })
export class UserDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private userService = inject(UserService);
  
  user: User | null = null;

  ngOnInit(): void {
    // Snapshot (one-time read)
    const id = this.route.snapshot.params['id'];
    
    // Observable (reacts to changes — e.g., navigating between /users/1 and /users/2)
    this.route.params.pipe(
      switchMap(params => this.userService.getUser(+params['id']))
    ).subscribe(user => this.user = user);
  }
}
```

### Query Parameters

```typescript
// URL: /users?page=2&sort=name&order=asc

@Component({ ... })
export class UserListComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const page = +params['page'] || 1;
      const sort = params['sort'] || 'id';
      const order = params['order'] || 'asc';
      this.loadUsers(page, sort, order);
    });
  }

  changePage(page: number): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge' // Keep existing params
    });
  }
}
```

### Programmatic Navigation

```typescript
export class UserFormComponent {
  private router = inject(Router);

  onSave(): void {
    // Navigate by URL
    this.router.navigate(['/users', this.userId]);
    
    // With query params
    this.router.navigate(['/users'], { queryParams: { created: true } });
    
    // Relative navigation
    this.router.navigate(['../'], { relativeTo: this.route });
    
    // Replace history (back button won't return here)
    this.router.navigate(['/login'], { replaceUrl: true });
    
    // Navigate by URL string
    this.router.navigateByUrl('/users/42/edit');
  }
}
```

### Child Routes (Nested)

```typescript
export const routes: Routes = [
  {
    path: 'admin',
    component: AdminLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AdminDashboardComponent },
      { path: 'users', component: AdminUsersComponent },
      { path: 'settings', component: AdminSettingsComponent }
    ]
  }
];

// admin-layout.component.html
// <app-admin-sidebar />
// <div class="content">
//   <router-outlet></router-outlet>  ← Child routes render here
// </div>
```

### Lazy-Loaded Routes

```typescript
export const routes: Routes = [
  { path: 'dashboard', component: DashboardComponent },
  
  // Lazy load entire route
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin.component')
      .then(m => m.AdminComponent)
  },
  
  // Lazy load with children
  {
    path: 'orders',
    loadChildren: () => import('./orders/orders.routes')
      .then(m => m.ORDER_ROUTES)
  }
];

// orders/orders.routes.ts
export const ORDER_ROUTES: Routes = [
  { path: '', component: OrderListComponent },
  { path: ':id', component: OrderDetailComponent },
  { path: 'new', component: OrderFormComponent }
];
```

### Route Data and Resolvers

```typescript
// Static route data
{ path: 'admin', component: AdminComponent, data: { role: 'admin', title: 'Admin Panel' } }

// Access in component
this.route.data.subscribe(data => {
  this.requiredRole = data['role'];
  this.pageTitle = data['title'];
});

// Resolver — fetch data before navigation
export const userResolver: ResolveFn<User> = (route) => {
  const userService = inject(UserService);
  const id = +route.params['id'];
  return userService.getUser(id);
};

// Route config
{ path: 'users/:id', component: UserDetailComponent, resolve: { user: userResolver } }

// Component — data already loaded
export class UserDetailComponent {
  private route = inject(ActivatedRoute);
  user = this.route.snapshot.data['user'] as User;
}
```

---

## Internal Working

### Router Navigation Flow

```
User clicks routerLink="/users/42":
1. Router intercepts click
2. Runs route guards (canDeactivate → canActivate → canMatch)
3. Runs resolvers (pre-fetch data)
4. If all pass → creates target component
5. Inserts component in <router-outlet>
6. Updates browser URL and history
7. Fires navigation events

If guard returns false:
  → Navigation cancelled
  → URL stays unchanged
  → No component change
```

### Route Matching

```
Routes: [
  { path: 'users', ... },
  { path: 'users/:id', ... },
  { path: 'users/:id/edit', ... },
  { path: '**', ... }
]

URL: /users/42/edit

Matching (first match wins):
  'users' → matches /users but not /users/42/edit (no segments left)
  'users/:id' → matches /users/42 but not /users/42/edit
  'users/:id/edit' → MATCHES! :id = 42
  
URL: /unknown-page
  None match → '**' wildcard matches everything
```

### Lazy Loading Internals

```
Initial bundle: main.js (includes eagerly-loaded routes)
Lazy chunk: admin-module.js (loaded on first navigation)

User navigates to /admin:
1. Router recognizes loadChildren/loadComponent
2. Dynamic import() fires → browser fetches admin-module.js
3. Module/component loaded and registered
4. Route navigation continues normally
5. Subsequent navigations use cached module (no re-fetch)
```

---

## Diagram

```
Routing Architecture:
┌────────────────────────────────────────────────────────┐
│ app.component.html                                      │
│ ┌──────────────────────────────────────────────────┐   │
│ │ <app-header>  [Dashboard] [Users] [Orders]       │   │
│ └──────────────────────────────────────────────────┘   │
│ ┌──────────────────────────────────────────────────┐   │
│ │ <router-outlet>                                   │   │
│ │   ┌────────────────────────────────────────────┐ │   │
│ │   │ Routed Component (based on URL)            │ │   │
│ │   │                                            │ │   │
│ │   │ /dashboard → DashboardComponent            │ │   │
│ │   │ /users → UserListComponent                 │ │   │
│ │   │ /users/42 → UserDetailComponent            │ │   │
│ │   │ /orders → (lazy loaded) OrderListComponent │ │   │
│ │   └────────────────────────────────────────────┘ │   │
│ └──────────────────────────────────────────────────┘   │
│ ┌──────────────────────────────────────────────────┐   │
│ │ <app-footer>                                      │   │
│ └──────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

```
Navigation with Guards:
User Action → Router
    ↓
CanDeactivate (current component ok to leave?)
    ↓ Yes
CanMatch / CanActivate (target route allowed?)
    ↓ Yes
Resolve (pre-fetch data)
    ↓ Complete
Activate Component → Render in RouterOutlet → Update URL
```

---

## Code

```typescript
// Complete routing setup for an application
// app.routes.ts
export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard],
    title: 'Dashboard'
  },
  {
    path: 'users',
    canActivate: [authGuard],
    children: [
      { path: '', component: UserListComponent, title: 'Users' },
      { path: 'new', component: UserFormComponent, title: 'Create User' },
      {
        path: ':id',
        component: UserDetailComponent,
        resolve: { user: userResolver },
        title: userTitleResolver
      },
      { path: ':id/edit', component: UserFormComponent, title: 'Edit User' }
    ]
  },
  {
    path: 'admin',
    loadChildren: () => import('./admin/admin.routes').then(m => m.ADMIN_ROUTES),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['admin'] }
  },
  { path: '**', component: NotFoundComponent, title: 'Not Found' }
];

// Functional route guard
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  if (authService.isAuthenticated()) {
    return true;
  }
  
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};

// Functional resolver
export const userResolver: ResolveFn<User> = (route) => {
  const userService = inject(UserService);
  const router = inject(Router);
  const id = +route.params['id'];
  
  return userService.getUser(id).pipe(
    catchError(() => {
      router.navigate(['/users']);
      return EMPTY;
    })
  );
};

// Dynamic title resolver
export const userTitleResolver: ResolveFn<string> = (route) => {
  const userService = inject(UserService);
  return userService.getUser(+route.params['id']).pipe(
    map(user => `User: ${user.name}`)
  );
};
```

---

## Dry Run

### Route Navigation

```
Current URL: /dashboard
User clicks: <a [routerLink]="['/users', 42]">

Step 1: Router processes navigation to /users/42
Step 2: canDeactivate on DashboardComponent → true (no guard)
Step 3: canActivate (authGuard) → authService.isAuthenticated() → true
Step 4: Route matches 'users/:id' → :id = 42
Step 5: Resolve: userResolver fetches GET /api/users/42
Step 6: HTTP returns { id: 42, name: 'John' }
Step 7: UserDetailComponent created
Step 8: ActivatedRoute.data = { user: { id: 42, name: 'John' } }
Step 9: Component inserted into <router-outlet>
Step 10: Browser URL updates to /users/42
Step 11: Browser history entry added
Step 12: routerLinkActive removes 'active' from Dashboard, adds to Users
```

---

## Complexity

| Operation | Performance |
|-----------|-------------|
| Route matching | O(routes) — first match wins |
| Guard execution | Sequential — stops on first rejection |
| Lazy loading (first) | Network latency (JS chunk download) |
| Lazy loading (subsequent) | O(1) — cached |
| Navigation events | ~8 events per navigation |

---

## Real Project Usage

```typescript
// Enterprise app routing with layouts and lazy modules
export const routes: Routes = [
  // Public routes (no layout)
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  
  // Authenticated routes (with layout)
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'employees', loadChildren: () => import('./features/employees/employees.routes').then(m => m.EMPLOYEE_ROUTES) },
      { path: 'orders', loadChildren: () => import('./features/orders/orders.routes').then(m => m.ORDER_ROUTES) },
      { path: 'reports', loadChildren: () => import('./features/reports/reports.routes').then(m => m.REPORT_ROUTES) },
    ]
  },
  
  // Admin routes (separate layout)
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['admin'] },
    loadChildren: () => import('./admin/admin.routes').then(m => m.ADMIN_ROUTES)
  },
  
  { path: '**', component: NotFoundComponent }
];
```

---

## Interview Questions and Answers

**Q1: What is the Angular Router and how does it work?**
> Angular Router maps URL paths to components. When the URL changes (via link click or programmatic navigation), the router matches the URL against configured routes, runs guards, resolves data, and renders the matched component in `<router-outlet>`. It manages browser history and enables SPA navigation without full page reloads.

**Q2: What is the difference between route params and query params?**
> Route params (`:id`) are part of the path — they define which resource: `/users/42`. They're required for the route to match. Query params (`?page=2&sort=name`) are optional key-value pairs appended to the URL. They don't affect route matching and are used for filtering, pagination, and optional state.

**Q3: What is lazy loading and why is it important?**
> Lazy loading defers loading route modules/components until the user navigates to them. Instead of loading the entire app upfront, only the initial route's code is loaded. This reduces initial bundle size and startup time. Use `loadChildren` or `loadComponent` in route config. Critical for large enterprise apps.

**Q4: Explain the navigation lifecycle.**
> Navigation triggers → Guards run (canDeactivate → canActivate) → Resolvers run (pre-fetch data) → Component activated → URL updated. If any guard returns false/UrlTree, navigation is cancelled. Events fire at each stage: NavigationStart, GuardsCheckStart, ResolveStart, NavigationEnd, etc.

**Q5: What is pathMatch: 'full' vs 'prefix'?**
> `pathMatch: 'full'` requires the entire remaining URL to match the path. `pathMatch: 'prefix'` (default) matches if the URL starts with the path. For redirect routes at `path: ''`, you MUST use `pathMatch: 'full'` — otherwise every URL would match (all URLs start with empty string).

---

## Common Mistakes

1. **Wildcard route not last**
   ```typescript
   // ❌ Wildcard catches everything — routes below never match
   { path: '**', component: NotFoundComponent },
   { path: 'users', component: UserListComponent }  // Never reached!
   
   // ✅ Wildcard always last
   { path: 'users', component: UserListComponent },
   { path: '**', component: NotFoundComponent }
   ```

2. **Using snapshot for routes that change**
   ```typescript
   // ❌ Won't update when navigating /users/1 → /users/2 (same component reused)
   ngOnInit() { this.id = this.route.snapshot.params['id']; }
   
   // ✅ Subscribe to params
   ngOnInit() {
     this.route.params.pipe(switchMap(p => this.load(p['id']))).subscribe();
   }
   ```

3. **Missing pathMatch: 'full' on redirect**
   ```typescript
   // ❌ Redirects ALL routes to /dashboard
   { path: '', redirectTo: '/dashboard' }
   
   // ✅ Only redirect exact empty path
   { path: '', redirectTo: '/dashboard', pathMatch: 'full' }
   ```

---

## Best Practices

1. **Lazy load all feature routes** — reduces initial bundle.
2. **Use functional guards and resolvers** (Angular 15+).
3. **Subscribe to params** for reusable route components.
4. **Use `pathMatch: 'full'`** on empty-path redirects.
5. **Put wildcard route last** in the routes array.
6. **Use `title`** property for page titles (SEO, accessibility).
7. **Use resolvers** for data that must exist before rendering.
8. **Use `queryParamsHandling: 'merge'`** to preserve existing query params.

---

## Production Considerations

- **Preloading**: Use `withPreloading(PreloadAllModules)` to preload lazy modules after initial load.
- **Route-level code splitting**: Each lazy route becomes a separate JS chunk.
- **SSR**: Server-side rendering needs route configuration for proper HTML generation.
- **Deep linking**: Ensure server returns index.html for all routes (SPA fallback).
- **Analytics**: Subscribe to router events for page view tracking.

---

## Related Topics

- → [14. Route Guards](./14-route-guards.md)
- → [15. Lazy Loading](./15-lazy-loading.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
