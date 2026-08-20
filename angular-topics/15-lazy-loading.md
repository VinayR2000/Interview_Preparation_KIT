# 15. Lazy Loading

---

## Theory

Lazy loading defers loading of feature modules/components until the user navigates to them. This reduces initial bundle size and improves application startup time.

### Lazy Loading Components

```typescript
// app.routes.ts
export const routes: Routes = [
  { path: 'dashboard', component: DashboardComponent }, // Eager (in main bundle)
  
  // Lazy-loaded standalone component
  {
    path: 'profile',
    loadComponent: () => import('./features/profile/profile.component')
      .then(m => m.ProfileComponent)
  },
  
  // Lazy-loaded route group
  {
    path: 'admin',
    loadChildren: () => import('./features/admin/admin.routes')
      .then(m => m.ADMIN_ROUTES)
  }
];
```

### Lazy-Loaded Route Group

```typescript
// features/admin/admin.routes.ts
export const ADMIN_ROUTES: Routes = [
  { path: '', component: AdminDashboardComponent },
  { path: 'users', component: AdminUsersComponent },
  { path: 'settings', component: AdminSettingsComponent }
];
```

### Preloading Strategies

```typescript
import { provideRouter, withPreloading, PreloadAllModules } from '@angular/router';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withPreloading(PreloadAllModules))
    // After initial load, preloads all lazy modules in background
  ]
};

// Custom preloading strategy
@Injectable({ providedIn: 'root' })
export class SelectivePreloadStrategy implements PreloadingStrategy {
  preload(route: Route, load: () => Observable<any>): Observable<any> {
    // Only preload routes marked with data.preload = true
    if (route.data?.['preload']) {
      return load();
    }
    return of(null);
  }
}

// Route config
{ path: 'orders', loadChildren: () => ..., data: { preload: true } }
```

### Eager vs Lazy Loading

| Feature | Eager | Lazy |
|---------|-------|------|
| Bundle | Included in main.js | Separate chunk file |
| Load time | At app startup | On first navigation |
| Initial bundle size | Larger | Smaller |
| Navigation speed | Instant (already loaded) | First visit has delay |
| Use case | Core features, small modules | Large features, admin panels |

---

## Internal Working

### How Lazy Loading Works

```
Initial Load:
  Browser downloads: main.js (contains eagerly-loaded code)
  
User navigates to /admin:
  1. Router detects loadChildren/loadComponent
  2. Dynamic import() → browser fetches admin-chunk.js
  3. JavaScript parsed and executed
  4. Routes/components registered
  5. Navigation completes, component renders

Subsequent visits to /admin:
  1. Chunk already cached by browser
  2. No network request
  3. Instant navigation
```

### Bundle Splitting

```
Build output:
  dist/
  ├── main.js              (400KB) — core + eager routes
  ├── chunk-admin.js       (150KB) — admin feature
  ├── chunk-orders.js      (100KB) — orders feature
  ├── chunk-reports.js     (200KB) — reports feature
  └── chunk-shared.js      (50KB)  — shared between lazy chunks

Without lazy loading: main.js would be 900KB
With lazy loading: initial load = 400KB (55% reduction)
```

---

## Code

```typescript
// Complete lazy loading setup
export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./auth/login.component').then(m => m.LoginComponent) },
  
  // Main authenticated area
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent }, // Eager — most visited
      {
        path: 'employees',
        loadChildren: () => import('./features/employees/employees.routes').then(m => m.EMPLOYEE_ROUTES),
        data: { preload: true } // Preload after initial render
      },
      {
        path: 'orders',
        loadChildren: () => import('./features/orders/orders.routes').then(m => m.ORDER_ROUTES),
        data: { preload: true }
      },
      {
        path: 'reports',
        loadChildren: () => import('./features/reports/reports.routes').then(m => m.REPORT_ROUTES)
        // Don't preload — heavy, rarely used
      },
      {
        path: 'admin',
        loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES),
        canActivate: [roleGuard],
        data: { roles: ['admin'] }
      }
    ]
  },
  { path: '**', loadComponent: () => import('./not-found.component').then(m => m.NotFoundComponent) }
];
```

---

## Interview Questions and Answers

**Q1: What is lazy loading in Angular and why use it?**
> Lazy loading loads feature code only when the user navigates to that route. Benefits: smaller initial bundle (faster first paint), reduced memory usage, faster startup. Without it, the entire app downloads upfront even for features the user may never visit.

**Q2: What is the difference between loadComponent and loadChildren?**
> `loadComponent` lazily loads a single standalone component. `loadChildren` lazily loads an entire set of child routes. Use `loadComponent` for simple single-page features. Use `loadChildren` for feature areas with multiple routes.

**Q3: What are preloading strategies?**
> Preloading loads lazy modules in the background after the initial load completes. `PreloadAllModules` preloads everything. Custom strategies can selectively preload (e.g., based on user role or route priority). This gives fast initial load AND fast subsequent navigation.

**Q4: How does lazy loading affect bundle size?**
> Each lazy-loaded route becomes a separate JavaScript chunk. The main bundle only contains eagerly-loaded code. Example: 1MB app → with lazy loading becomes 400KB main + 200KB + 200KB + 200KB chunks. User downloads 400KB initially instead of 1MB.

---

## Best Practices

1. **Lazy load all feature routes** except the most-visited landing page.
2. **Use preloading** for high-priority lazy routes.
3. **Keep shared code in a common chunk** — Angular handles this automatically.
4. **Monitor bundle sizes** with `ng build --stats-json` and webpack-bundle-analyzer.
5. **Set build budgets** in angular.json to catch size regressions.
6. **Use route-level code splitting** for large features.

---

## Production Considerations

- **Network latency**: First navigation to lazy route has download delay — show loading indicator.
- **Caching**: Browser caches chunks — subsequent visits are instant.
- **CDN**: Serve chunks from CDN for faster global delivery.
- **Build budgets**: Set `maximumWarning: 500kb` for initial bundle.
- **Preloading**: Balance between preloading (faster navigation) and bandwidth usage.

---

## Related Topics

- → [13. Routing](./13-routing.md)
- → [14. Route Guards](./14-route-guards.md)
- → [25. Angular Performance](./25-angular-performance.md)
- → [36. Build and Deployment](./36-build-deployment.md)
