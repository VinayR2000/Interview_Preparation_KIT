# 23. Error Handling

---

## Theory

Proper error handling ensures a robust user experience. Angular provides multiple layers: component-level, service-level, interceptor-level, and global error handlers.

### Error Handling Layers

| Layer | Mechanism | Scope |
|-------|-----------|-------|
| Template | `@if` with error state | Single component |
| Service | `catchError` operator | Per-service |
| Interceptor | HTTP error interceptor | All HTTP calls |
| Global | `ErrorHandler` class | Entire application |

### HTTP Error Handling in Services

```typescript
@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`/api/users/${id}`).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 404) {
          return throwError(() => new Error('User not found'));
        }
        if (error.status === 403) {
          return throwError(() => new Error('Access denied'));
        }
        if (error.status === 0) {
          return throwError(() => new Error('Network unavailable'));
        }
        return throwError(() => new Error('An unexpected error occurred'));
      })
    );
  }

  // With retry for transient failures
  getUsers(): Observable<User[]> {
    return this.http.get<User[]>('/api/users').pipe(
      retry({ count: 3, delay: (_, retryCount) => timer(1000 * retryCount) }),
      catchError(err => {
        console.error('Failed after 3 retries:', err);
        return of([]); // Return empty array as fallback
      })
    );
  }
}
```

### Component-Level Error Handling

```typescript
@Component({
  template: `
    @if (loading) {
      <app-spinner />
    } @else if (error) {
      <div class="error-state">
        <p>{{ error }}</p>
        <button (click)="retry()">Retry</button>
      </div>
    } @else {
      @for (user of users; track user.id) {
        <app-user-card [user]="user" />
      }
    }
  `
})
export class UserListComponent implements OnInit {
  users: User[] = [];
  loading = true;
  error: string | null = null;

  private userService = inject(UserService);

  ngOnInit(): void { this.loadUsers(); }

  loadUsers(): void {
    this.loading = true;
    this.error = null;
    this.userService.getUsers().subscribe({
      next: users => { this.users = users; this.loading = false; },
      error: err => { this.error = err.message; this.loading = false; }
    });
  }

  retry(): void { this.loadUsers(); }
}
```

### Global Error Handler

```typescript
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private notification = inject(NotificationService);
  private logger = inject(LoggingService);

  handleError(error: any): void {
    // Log to monitoring service (Sentry, etc.)
    this.logger.logError(error);

    // Show user-friendly message
    if (error instanceof HttpErrorResponse) {
      this.handleHttpError(error);
    } else {
      this.notification.show('Something went wrong. Please refresh.', 'error');
    }

    // Still log to console in development
    console.error('Global error:', error);
  }

  private handleHttpError(error: HttpErrorResponse): void {
    switch (error.status) {
      case 401: this.notification.show('Session expired', 'warning'); break;
      case 403: this.notification.show('Access denied', 'error'); break;
      case 500: this.notification.show('Server error', 'error'); break;
      default: this.notification.show('Network error', 'error');
    }
  }
}

// Registration
export const appConfig: ApplicationConfig = {
  providers: [
    { provide: ErrorHandler, useClass: GlobalErrorHandler }
  ]
};
```

### HTTP Error Interceptor

```typescript
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  const notification = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.logout();
        router.navigate(['/login']);
      } else if (error.status === 403) {
        router.navigate(['/forbidden']);
      } else if (error.status >= 500) {
        notification.show('Server is temporarily unavailable', 'error');
      } else if (error.status === 0) {
        notification.show('Please check your internet connection', 'warning');
      }
      return throwError(() => error);
    })
  );
};
```

---

## Internal Working

### Error Propagation in RxJS

```
Observable pipe:
  source$ → operator1 → operator2 → catchError → subscriber

If error occurs at operator1:
  1. Error bypasses operator2 (skipped)
  2. catchError intercepts
  3. If catchError returns new Observable → stream recovers
  4. If catchError rethrows → subscriber.error() called
  5. Observable terminates (no more values)

Key: catchError recovers the stream
     throwError propagates the error
```

### Global ErrorHandler vs Interceptor

```
HTTP Error Flow:
  HttpClient → Interceptor catches → may rethrow → 
  subscriber.error() → if unhandled → Global ErrorHandler

Non-HTTP Error Flow:
  Component throws → Global ErrorHandler directly

Interceptor: HTTP-specific, can modify/retry/redirect
ErrorHandler: Catches ALL unhandled errors (HTTP + JS + template)
```

---

## Code

```typescript
// Production error handling pattern
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private notification = inject(NotificationService);

  request<T>(method: string, url: string, options?: any): Observable<T> {
    return this.http.request<T>(method, url, options).pipe(
      retry({
        count: 2,
        delay: (error, retryCount) => {
          // Only retry on 5xx or network errors
          if (error.status >= 500 || error.status === 0) {
            return timer(1000 * retryCount); // Exponential backoff
          }
          return throwError(() => error); // Don't retry 4xx
        }
      }),
      catchError((error: HttpErrorResponse) => {
        const message = this.getErrorMessage(error);
        this.notification.show(message, 'error');
        return throwError(() => error);
      })
    );
  }

  private getErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 0) return 'Unable to connect to server';
    if (error.error?.message) return error.error.message; // Backend error message
    if (error.status === 400) return 'Invalid request';
    if (error.status === 404) return 'Resource not found';
    if (error.status === 409) return 'Conflict — resource already exists';
    if (error.status >= 500) return 'Server error — please try again later';
    return 'An unexpected error occurred';
  }
}
```

---

## Interview Questions and Answers

**Q1: How do you handle HTTP errors globally in Angular?**
> Use an HTTP interceptor that catches errors via `catchError`. Handle specific status codes: 401 → logout/redirect to login, 403 → forbidden page, 500 → user notification. For truly unhandled errors, implement a custom `ErrorHandler` class that logs to monitoring services (Sentry) and shows generic error messages.

**Q2: What is the difference between catchError returning of() vs throwError()?**
> `catchError(() => of(fallback))` recovers the stream — downstream subscribers receive the fallback value and the Observable continues (or completes normally). `catchError(() => throwError(...))` propagates the error — downstream error handler fires. Use `of()` for graceful degradation; `throwError()` when callers need to know about the error.

**Q3: How do you implement retry with exponential backoff?**
> Use `retry({ count: 3, delay: (error, retryCount) => timer(1000 * Math.pow(2, retryCount)) })`. Only retry on retryable errors (5xx, network). Don't retry 4xx (client errors). Show loading indicator during retries. After max retries, show error to user.

**Q4: What does Angular's ErrorHandler do?**
> `ErrorHandler` is a global service that catches any unhandled JavaScript error in the application — template errors, unhandled promise rejections, errors in event handlers. Override it to send errors to monitoring services, show user notifications, and log structured errors. It's the last line of defense.

---

## Best Practices

1. **Layer your error handling**: service → interceptor → global handler.
2. **Show user-friendly messages** — never expose raw error objects.
3. **Retry transient failures** (5xx, network) with backoff.
4. **Don't retry client errors** (4xx) — they won't self-resolve.
5. **Log errors to monitoring** (Sentry, Datadog) in production.
6. **Provide retry/refresh** actions in error UI states.
7. **Use typed error responses** from your Spring Boot backend.

---

## Production Considerations

- **Error monitoring**: Integrate Sentry/Datadog for production error tracking.
- **Source maps**: Upload source maps to error monitoring service for readable stack traces.
- **Error budgets**: Track error rates and alert on spikes.
- **Graceful degradation**: Show cached/stale data when network fails.
- **Offline detection**: Use `navigator.onLine` + interceptor for offline-first UX.

---

## Related Topics

- → [20. HTTP Client](./20-http-client.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
- → [17. RxJS](./17-rxjs.md)
