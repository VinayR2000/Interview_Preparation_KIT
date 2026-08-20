# 50. Angular Scenario Questions — Interview Ready

---

## Theory

Scenario questions test your ability to apply Angular knowledge to real-world problems. These are common in 3-5 year experience interviews.

---

## Questions and Answers

### Authentication & Security

**Q1: How do you secure an Angular application?**
> 1. JWT authentication with HTTP interceptor adding tokens. 2. Route guards (CanActivate) blocking unauthorized access. 3. Role-based UI hiding (but never rely on client-side alone). 4. XSS protection — Angular sanitizes by default, avoid bypassSecurityTrust. 5. CSRF — Angular's HttpClient handles XSRF tokens. 6. Content Security Policy headers. 7. HTTPS only. 8. Always enforce security on backend — frontend is cosmetic.

**Q2: How do you attach JWT to every request?**
> Create an HTTP interceptor that reads the token from localStorage/service, clones the request with `Authorization: Bearer <token>` header, and passes to next(). Skip auth endpoints (login, register) to avoid circular issues. Register with `provideHttpClient(withInterceptors([authInterceptor]))`.

**Q3: What happens when JWT expires?**
> The interceptor catches the 401 response. It attempts to refresh the token by calling the refresh endpoint with the refresh token. If successful, retries the original failed request with the new token. If refresh fails (refresh token also expired), logs out the user and redirects to login page with returnUrl.

**Q4: How do you implement refresh token logic?**
> In the error interceptor: detect 401, check if refresh token exists, call POST /auth/refresh. Use a flag to prevent multiple simultaneous refresh calls. Queue other failed requests and retry them after refresh completes. If refresh fails, logout. Use BehaviorSubject to coordinate queued requests.

**Q5: How do you prevent unauthorized routes?**
> Use CanActivate guards on protected routes. Guard checks AuthService.isAuthenticated() and token validity. Return true to allow, or router.createUrlTree(['/login']) to redirect. For role-based: check user.roles against route.data.roles. Use CanMatch to completely hide routes from non-admin users.

**Q6: How do you implement role-based access?**
> Decode roles from JWT payload. AuthService.hasRole('admin') method. RoleGuard reads route.data.roles and checks user's roles. In templates: `@if (authService.hasRole('admin'))` to show/hide buttons. Structural directive *appRole="['admin']" for reusable pattern. Always enforce on backend.

---

### Component Communication

**Q7: How do components communicate?**
> Parent→Child: @Input(). Child→Parent: @Output()+EventEmitter. Siblings: shared service with BehaviorSubject. Any→Any: shared service or state management (NgRx/Signals). Deep nesting: shared service (avoids prop drilling). Projected content: ng-content. Direct access: @ViewChild (use sparingly).

**Q8: How do you share state across components?**
> For simple cases: service with BehaviorSubject, expose as Observable, components subscribe via AsyncPipe. For complex apps: NgRx (store + actions + reducers + selectors + effects). For modern Angular: signals with computed values in a service. Choice depends on app complexity.

---

### Performance

**Q9: How do you prevent memory leaks?**
> 1. takeUntilDestroyed() for all subscriptions (Angular 16+). 2. AsyncPipe in templates (auto-unsubscribes). 3. Clear intervals/timeouts in ngOnDestroy. 4. Close WebSocket connections. 5. Remove event listeners. 6. Complete subjects. 7. Destroy third-party library instances. HTTP observables are safe (they complete).

**Q10: How do you improve Angular performance?**
> 1. OnPush change detection (50-80% fewer checks). 2. Lazy loading routes (smaller initial bundle). 3. Virtual scrolling for large lists (CDK). 4. Pure pipes instead of template methods. 5. track in @for loops. 6. debounceTime for search inputs. 7. shareReplay to prevent duplicate HTTP calls. 8. Production build with AOT + tree shaking.

**Q11: Why use OnPush?**
> Reduces change detection cycles. Angular skips checking the component unless inputs change reference, events fire from it, AsyncPipe emits, or markForCheck() is called. In a 100-component tree, only changed components are checked. Requires immutable data patterns (new object references for changes).

**Q12: How does change detection work?**
> Zone.js intercepts async events → Angular starts CD from root → walks tree top-down → evaluates all template bindings → compares with previous values → updates DOM where different. Default checks everything; OnPush skips unchanged subtrees. Signals enable more granular, zone-less detection.

---

### RxJS

**Q13: Observable vs Promise?**
> Observable: lazy (runs on subscribe), multiple values over time, cancellable (unsubscribe), rich operators (map, filter, switchMap), retry built-in. Promise: eager (runs immediately), single value, not cancellable, limited chaining (.then). Angular uses Observables for HTTP, routing, forms, events.

**Q14: Subject vs BehaviorSubject?**
> Subject: no initial value, only current subscribers receive emissions, late subscribers miss past values. BehaviorSubject: requires initial value, immediately emits current value to new subscribers. Use BehaviorSubject for state (always need current value). Use Subject for events (fire-and-forget notifications).

**Q15: switchMap vs mergeMap?**
> switchMap cancels previous inner Observable when new value arrives — use for search (only latest result matters). mergeMap runs all concurrently — use for parallel independent operations. switchMap prevents race conditions in search; mergeMap would show stale results if slow response arrives after fast one.

**Q16: How do you cancel HTTP requests?**
> switchMap automatically cancels previous request. Unsubscribing from HTTP Observable cancels the XHR. Use takeUntil(cancel$) to cancel on demand. In practice: switchMap for route/search-based loading, unsubscribe on component destroy prevents processing stale responses.

---

### Error Handling

**Q17: How do you handle global errors?**
> Implement custom ErrorHandler (catches all unhandled errors). HTTP interceptor catches HTTP errors (401, 403, 500). Service-level catchError for specific error handling. Component shows error state with retry button. Log to Sentry/Datadog in production. Never show raw errors to users.

**Q18: How do you handle 401 globally?**
> Error interceptor catches 401 → attempts token refresh → if refresh succeeds, retries failed request → if refresh fails, calls authService.logout() and navigates to /login with returnUrl. Queue concurrent 401s to avoid multiple refresh calls. Skip 401 handling for auth endpoints themselves.

---

### Architecture

**Q19: How do you implement lazy loading?**
> In routes: `loadChildren: () => import('./feature/routes').then(m => m.ROUTES)` or `loadComponent: () => import('./comp').then(m => m.Comp)`. Each lazy route becomes a separate JS chunk. Use preloading strategy (PreloadAllModules) to load chunks in background after initial render.

**Q20: How do you handle large lists?**
> Virtual scrolling (CDK) renders only visible items. For 100K items, only ~10-20 DOM nodes exist. Alternative: server-side pagination (load 20 items at a time). For search: debounceTime + server-side filtering. Never render all items in DOM — browser will hang.

**Q21: How do you optimize API calls?**
> debounceTime(300) for search input. distinctUntilChanged to skip same value. switchMap to cancel stale requests. shareReplay(1) to share results across subscribers. Caching service/interceptor for frequently-accessed data. Pagination for large datasets.

**Q22: How do you implement search with debounce?**
> FormControl.valueChanges → debounceTime(300) → distinctUntilChanged() → filter(term => term.length >= 2) → switchMap(term => api.search(term)) → catchError → subscribe. debounceTime waits for user to pause. switchMap cancels previous. distinctUntilChanged avoids duplicate calls.

---

### Deployment

**Q23: How do you deploy Angular?**
> `ng build --configuration production` generates optimized dist/ folder. Serve via Nginx (most common), Apache, or CDN. Configure server to return index.html for all routes (SPA routing). Use Docker multi-stage build: Node builds → Nginx serves. Set up CI/CD (GitHub Actions, Jenkins) for automated builds.

**Q24: How does Angular communicate with Spring Boot?**
> HttpClient sends REST calls (JSON over HTTP). In development: proxy.conf.json routes /api/* to Spring Boot (localhost:8080). In production: Nginx serves Angular static files and proxies /api to Spring Boot service. JWT in Authorization header. Spring Security validates token on each request.

**Q25: How do you handle CORS?**
> Development: Angular proxy (no CORS needed). Production: Spring Boot CORS config — allowedOrigins (your domain), allowedMethods (GET, POST, PUT, DELETE), allowedHeaders, allowCredentials. Or same-origin setup (Nginx serves both Angular and proxies API). Preflight OPTIONS request handled by Spring.

**Q26: How do you Dockerize Angular?**
> Multi-stage Dockerfile: Stage 1 (Node) — npm install, ng build --production. Stage 2 (Nginx) — copy dist/ to Nginx html folder, custom nginx.conf (try_files for SPA routing). Result: ~25MB image. Use .dockerignore for node_modules. Configure via environment.js or runtime config loading.

---

### Patterns

**Q27: Smart vs Presentational components?**
> Smart (container): injects services, manages state, handles business logic, orchestrates children. Presentational (dumb): receives data via @Input, emits events via @Output, pure display logic, highly reusable. Smart components are app-specific; presentational are generic. This separation improves testability and reusability.

**Q28: When would you use NgRx vs simple services?**
> Simple services (BehaviorSubject): small-medium apps, few shared states, simple data flow. NgRx: large enterprise apps, many developers, complex state interactions, need for devtools/time-travel debugging, audit trail of actions. NgRx adds boilerplate — don't use it unless the complexity justifies it.

---

## Quick Reference — Common Patterns

```typescript
// Search with debounce
searchControl.valueChanges.pipe(
  debounceTime(300), distinctUntilChanged(),
  switchMap(term => api.search(term)),
  catchError(() => of([]))
)

// Auth interceptor
const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  if (token) req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  return next(req);
};

// Auth guard
const authGuard: CanActivateFn = (route, state) => {
  return inject(AuthService).isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

// Cleanup pattern
private destroyRef = inject(DestroyRef);
ngOnInit() {
  this.service.data$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
}

// OnPush + immutable
@Component({ changeDetection: ChangeDetectionStrategy.OnPush })
// Always: this.items = [...this.items, newItem] (not push)
```

---

## Related Topics

- → [14. Route Guards](./14-route-guards.md)
- → [17. RxJS](./17-rxjs.md)
- → [18. RxJS Comparisons](./18-rxjs-comparisons.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
- → [22. Angular + JWT Auth](./22-angular-jwt-auth.md)
- → [24. Change Detection](./24-change-detection.md)
- → [25. Performance](./25-angular-performance.md)
- → [38. Angular + Spring Boot](./38-angular-spring-boot.md)
