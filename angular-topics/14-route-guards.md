# 14. Route Guards

---

## Theory

Route guards control access to routes. They run before navigation completes and can allow, deny, or redirect the user.

### Guard Types

| Guard | When it Runs | Use Case |
|-------|-------------|----------|
| `CanActivate` | Before activating a route | Authentication, authorization |
| `CanActivateChild` | Before activating child routes | Protect all children |
| `CanDeactivate` | Before leaving a route | Unsaved changes warning |
| `CanMatch` | Before matching a route | Role-based route availability |

### Functional Guards (Modern — Angular 15+)

```typescript
// Authentication guard
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Redirect to login with return URL
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};

// Role-based guard
export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requiredRoles = route.data['roles'] as string[];

  if (!requiredRoles || requiredRoles.length === 0) return true;

  const userRoles = authService.getCurrentUser()?.roles ?? [];
  const hasRole = requiredRoles.some(role => userRoles.includes(role));

  return hasRole || router.createUrlTree(['/unauthorized']);
};

// Unsaved changes guard
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (component.hasUnsavedChanges()) {
    return confirm('You have unsaved changes. Do you really want to leave?');
  }
  return true;
};

// Interface for components with unsaved changes
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

// Route configuration
export const routes: Routes = [
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['admin'] },
    children: [
      { path: 'users', component: AdminUsersComponent },
      { path: 'settings', component: AdminSettingsComponent, canDeactivate: [unsavedChangesGuard] }
    ]
  }
];
```

### Complete JWT Authentication Flow

```typescript
// auth.guard.ts
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Check if token exists and is not expired
  const token = authService.getToken();
  if (token && !authService.isTokenExpired(token)) {
    return true;
  }

  // Try refresh token
  if (authService.hasRefreshToken()) {
    return authService.refreshToken().pipe(
      map(() => true),
      catchError(() => {
        authService.logout();
        return of(router.createUrlTree(['/login'], {
          queryParams: { returnUrl: state.url }
        }));
      })
    );
  }

  authService.logout();
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};
```

### Guard Flow

```
User navigates to /admin/users:
    ↓
CanMatch: Does route exist for this user?
    ↓ yes
CanActivate [authGuard]: Is user authenticated?
    ↓ yes
CanActivate [roleGuard]: Does user have 'admin' role?
    ↓ yes
CanActivateChild: Can access child routes?
    ↓ yes
Component renders

User clicks "Back" from /admin/settings:
    ↓
CanDeactivate [unsavedChangesGuard]: Any unsaved changes?
    ↓ user confirms "Yes, leave"
Navigation proceeds
```

---

## Internal Working

### Guard Execution Order

```
Multiple guards on a route: [guard1, guard2, guard3]
Execution: Sequential (left to right)
Short-circuit: First rejection stops execution

guard1 → true → guard2 → true → guard3 → false → NAVIGATION CANCELLED
```

### Guard Return Types

```typescript
CanActivateFn can return:
  - true                     → allow navigation
  - false                    → block navigation (stays on current page)
  - UrlTree                  → redirect to different route
  - Observable<boolean|UrlTree>  → async decision
  - Promise<boolean|UrlTree>     → async decision
```

---

## Code

```typescript
// Complete guard system for enterprise app

// Permission-based guard using server validation
export const permissionGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const permissionService = inject(PermissionService);
  const requiredPermission = route.data['permission'] as string;

  if (!requiredPermission) return true;

  return permissionService.hasPermission(requiredPermission).pipe(
    map(hasPermission => {
      if (!hasPermission) {
        console.warn(`Access denied: missing permission '${requiredPermission}'`);
      }
      return hasPermission;
    })
  );
};

// CanMatch — hide routes from users who shouldn't see them
export const adminMatchGuard: CanMatchFn = (route, segments) => {
  const authService = inject(AuthService);
  return authService.getCurrentUser()?.roles.includes('admin') ?? false;
};

// Form component implementing unsaved changes
@Component({ ... })
export class SettingsComponent implements HasUnsavedChanges {
  form = inject(FormBuilder).group({ ... });
  private saved = false;

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saved;
  }

  save(): void {
    this.saved = true;
    // save logic
  }
}
```

---

## Interview Questions and Answers

**Q1: What are route guards and what types exist?**
> Route guards are functions that run during navigation to control access. `CanActivate` checks if a route can be accessed (auth/role checks). `CanDeactivate` checks if user can leave (unsaved changes). `CanActivateChild` protects all child routes. `CanMatch` determines if a route should even be considered during matching.

**Q2: How do you implement authentication guard in Angular?**
> Check if JWT token exists and is valid. If yes, return true. If expired, try refresh token. If no token or refresh fails, redirect to login with returnUrl: `router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })`. After login, navigate to returnUrl.

**Q3: How do you implement role-based access with guards?**
> Store required roles in route data: `data: { roles: ['admin'] }`. In the guard, read `route.data['roles']`, get user's roles from AuthService, check intersection. Return true if user has any required role, otherwise redirect to unauthorized page.

**Q4: What is CanDeactivate used for?**
> Preventing users from accidentally losing unsaved work. The component implements an interface like `hasUnsavedChanges()`. The guard calls this method — if true, shows a confirmation dialog. Common for forms and editors where losing data would be frustrating.

**Q5: What is the difference between CanActivate and CanMatch?**
> `CanActivate` runs after route matching — it blocks access but the route is still "known". `CanMatch` runs during route matching — if false, the route is skipped entirely and the router continues checking other routes. Use CanMatch to completely hide routes from certain users.

---

## Common Mistakes

1. **Not passing returnUrl for post-login redirect**
2. **Checking token existence without validating expiry**
3. **Not handling async guard properly (forgetting to return Observable)**
4. **Putting role check in every component instead of guard**

---

## Best Practices

1. **Use functional guards** (Angular 15+) — cleaner than class-based.
2. **Return UrlTree** for redirects instead of `router.navigate()` in guards.
3. **Store returnUrl** in query params for post-login redirect.
4. **Validate token expiry** in auth guard, not just existence.
5. **Use CanMatch** to completely hide admin routes from non-admins.
6. **Combine guards** — authGuard + roleGuard (single responsibility each).

---

## Production Considerations

- **Token refresh**: Handle expired tokens gracefully in guards with refresh logic.
- **Server validation**: For sensitive routes, validate permissions server-side too.
- **UX**: Show loading indicator during async guard resolution.
- **Security**: Guards are client-side only — always enforce on backend too.

---

## Related Topics

- → [13. Routing](./13-routing.md)
- → [15. Lazy Loading](./15-lazy-loading.md)
- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
- → [40. Security](./40-security.md)
