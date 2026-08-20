# 21. HTTP Interceptors — Very Important for Spring Boot + JWT

---

## Theory

HTTP Interceptors sit between the HttpClient and the server. They can modify requests (add headers, tokens) and responses (handle errors, transform data) globally.

### Functional Interceptor (Modern — Angular 15+)

```typescript
import { HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';

// Auth interceptor — adds JWT to every request
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (token) {
    const clonedReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(clonedReq);
  }

  return next(req);
};

// Registration
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor, loggingInterceptor]))
  ]
};
```

### Common Interceptor Patterns

```typescript
// 1. Auth Token Interceptor
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(TokenStorageService).getToken();
  
  // Skip for login/register endpoints
  if (req.url.includes('/auth/login') || req.url.includes('/auth/register')) {
    return next(req);
  }

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }
  return next(req);
};

// 2. Error Handling Interceptor
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const notification = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      switch (error.status) {
        case 401:
          inject(AuthService).logout();
          router.navigate(['/login']);
          break;
        case 403:
          notification.show('Access denied', 'error');
          router.navigate(['/unauthorized']);
          break;
        case 404:
          notification.show('Resource not found', 'error');
          break;
        case 500:
          notification.show('Server error. Please try again.', 'error');
          break;
        case 0:
          notification.show('Network error. Check connection.', 'error');
          break;
      }
      return throwError(() => error);
    })
  );
};

// 3. Loading Indicator Interceptor
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);
  loadingService.show();

  return next(req).pipe(
    finalize(() => loadingService.hide())
  );
};

// 4. Logging Interceptor
export const loggingInterceptor: HttpInterceptorFn = (req, next) => {
  const started = Date.now();
  
  return next(req).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          const elapsed = Date.now() - started;
          console.log(`${req.method} ${req.urlWithParams} → ${event.status} (${elapsed}ms)`);
        }
      },
      error: (error) => {
        const elapsed = Date.now() - started;
        console.error(`${req.method} ${req.urlWithParams} → ERROR (${elapsed}ms)`, error);
      }
    })
  );
};

// 5. Token Refresh Interceptor
export const tokenRefreshInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/auth/refresh')) {
        return authService.refreshToken().pipe(
          switchMap(newToken => {
            const cloned = req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` }
            });
            return next(cloned);
          }),
          catchError(() => {
            authService.logout();
            inject(Router).navigate(['/login']);
            return throwError(() => error);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
```

### Interceptor Execution Order

```
Request flow (in order):
  HttpClient.get()
    → Interceptor 1 (auth) — adds token
    → Interceptor 2 (logging) — logs request
    → Interceptor 3 (loading) — shows spinner
    → Server

Response flow (reverse order):
  Server response
    → Interceptor 3 (loading) — hides spinner
    → Interceptor 2 (logging) — logs response
    → Interceptor 1 (auth/error) — handles 401
    → Subscriber receives response
```

---

## Internal Working

### Request/Response Pipeline

```
Angular HttpClient
    ↓ creates HttpRequest
Interceptor Chain:
    ↓ interceptor1(req, next) → next(modifiedReq)
    ↓ interceptor2(req, next) → next(modifiedReq)
    ↓ interceptor3(req, next) → next(req)
    ↓
HttpBackend (actual XHR/fetch)
    ↓ returns HttpResponse Observable
    ↓
interceptor3 pipe(tap/catchError)
    ↓
interceptor2 pipe(tap/catchError)
    ↓
interceptor1 pipe(tap/catchError)
    ↓
Subscriber receives final HttpResponse
```

### req.clone() Immutability

```
HttpRequest is immutable — you cannot modify it directly.
Must use clone() to create modified copy:

const original = req;  // { url: '/api', headers: {} }
const modified = req.clone({ setHeaders: { Auth: 'Bearer xyz' } });
// original unchanged: { url: '/api', headers: {} }
// modified: { url: '/api', headers: { Auth: 'Bearer xyz' } }
```

---

## Diagram

```
Complete JWT Interceptor Flow:
┌─────────────┐
│ Component    │
│ http.get()   │
└──────┬──────┘
       ↓
┌──────────────────────────────────────────────┐
│ Auth Interceptor                              │
│ Token exists?                                 │
│ ├── YES → clone req + add Authorization      │
│ └── NO → pass through                        │
└──────┬───────────────────────────────────────┘
       ↓
┌──────────────────────────────────────────────┐
│ Error Interceptor                             │
│ (processes response on way back)              │
│ ├── 401 → refresh token or logout            │
│ ├── 403 → redirect to unauthorized           │
│ ├── 500 → show error notification            │
│ └── Success → pass through                   │
└──────┬───────────────────────────────────────┘
       ↓
┌──────────────┐        ┌──────────────────┐
│ HTTP Backend │ ──────→│ Spring Boot API   │
│ (XHR/fetch)  │ ←──────│ Spring Security   │
└──────────────┘        └──────────────────┘
```

---

## Code

```typescript
// Production-ready interceptor setup
// app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withInterceptors([
        authInterceptor,
        tokenRefreshInterceptor,
        errorInterceptor,
        loadingInterceptor
      ])
    )
  ]
};

// Comprehensive token refresh with queue
let isRefreshing = false;
let refreshSubject = new BehaviorSubject<string | null>(null);

export const tokenRefreshInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || req.url.includes('/auth')) {
        return throwError(() => error);
      }

      if (!isRefreshing) {
        isRefreshing = true;
        refreshSubject.next(null);

        return authService.refreshToken().pipe(
          switchMap(token => {
            isRefreshing = false;
            refreshSubject.next(token);
            return next(req.clone({
              setHeaders: { Authorization: `Bearer ${token}` }
            }));
          }),
          catchError(err => {
            isRefreshing = false;
            authService.logout();
            return throwError(() => err);
          })
        );
      }

      // Queue other requests while refreshing
      return refreshSubject.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => next(req.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        })))
      );
    })
  );
};
```

---

## Interview Questions and Answers

**Q1: What are HTTP interceptors in Angular?**
> Interceptors are middleware that sit between HttpClient and the server. They can modify outgoing requests (add auth tokens, headers) and incoming responses (handle errors, transform data). They're registered globally and apply to ALL HTTP requests — perfect for cross-cutting concerns.

**Q2: How do you add JWT to every request using an interceptor?**
> Create a functional interceptor that reads the token from storage, clones the request with `req.clone({ setHeaders: { Authorization: 'Bearer ' + token } })`, and passes it to `next()`. Skip token addition for auth endpoints (login/register). Register with `provideHttpClient(withInterceptors([authInterceptor]))`.

**Q3: How do you handle 401 errors globally?**
> In an error interceptor, catch 401 responses. Try refreshing the token — if successful, retry the failed request with the new token. If refresh fails, logout the user and redirect to login. Queue other requests while refresh is in progress to avoid multiple refresh calls.

**Q4: What is the execution order of interceptors?**
> Interceptors execute in registration order for requests (first registered runs first) and reverse order for responses (last registered processes response first). Think of it like a stack: request goes in left-to-right, response comes back right-to-left.

**Q5: Why must you clone the request instead of modifying it?**
> `HttpRequest` is immutable by design. This prevents accidental side effects — each interceptor works with its own copy. `req.clone()` creates a shallow copy with specified modifications. This also enables retry logic (original request preserved).

---

## Common Mistakes

1. **Not skipping auth endpoints**
   ```typescript
   // ❌ Adds token to login request (circular issue)
   // ✅ Skip: if (req.url.includes('/auth')) return next(req);
   ```

2. **Infinite loop on token refresh**
   ```typescript
   // ❌ Refresh endpoint returns 401 → triggers another refresh → loop
   // ✅ Check: if (req.url.includes('/auth/refresh')) return throwError(...)
   ```

3. **Not queuing requests during token refresh**

---

## Best Practices

1. **Use functional interceptors** (Angular 15+).
2. **Clone requests** — never mutate the original.
3. **Skip auth for login/register** endpoints.
4. **Queue requests** during token refresh.
5. **Order interceptors carefully** — auth before error handling.
6. **Keep interceptors focused** — single responsibility each.
7. **Handle network errors** (status 0) for offline detection.

---

## Production Considerations

- **Token refresh**: Implement proper queue to avoid multiple simultaneous refresh calls.
- **Retry logic**: Use exponential backoff for transient failures.
- **Performance**: Interceptors run for every request — keep them lightweight.
- **Logging**: Log request/response in development, reduce in production.
- **Timeout**: Add timeout interceptor to prevent hanging requests.

---

## Related Topics

- → [20. HTTP Client](./20-http-client.md)
- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
- → [23. Error Handling](./23-error-handling.md)
- → [38. Angular + Spring Boot Integration](./38-angular-spring-boot.md)
