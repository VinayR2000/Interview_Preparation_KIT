# 22. Angular + JWT Authentication — Critical for Full-Stack Profile

---

## Theory

JWT (JSON Web Token) authentication is the standard for securing Angular + Spring Boot applications. Angular handles the client-side: login, token storage, token attachment, and route protection.

### Complete Authentication Flow

```
┌─────────┐         POST /api/auth/login         ┌──────────────┐
│ Angular  │ ──────────────────────────────────→  │ Spring Boot  │
│ (Login   │  { email, password }                 │ Spring       │
│  Form)   │                                      │ Security     │
└─────────┘                                      └──────┬───────┘
     ↑                                                   │
     │         { token, refreshToken, user }             │
     │ ←─────────────────────────────────────────────────┘
     │
     ↓ Store token (localStorage/sessionStorage)
     │
     ↓ Subsequent requests:
┌─────────┐    Authorization: Bearer <JWT>       ┌──────────────┐
│ Angular  │ ──────────────────────────────────→ │ Spring Boot  │
│ (HTTP    │     GET /api/users                  │ (validates   │
│  Client) │ ←────────────────────────────────── │  JWT token)  │
└─────────┘         { users: [...] }             └──────────────┘
```

### Implementation Components

| Component | Responsibility |
|-----------|---------------|
| Login Component | Collect credentials, call auth service |
| Auth Service | Login/logout, token storage, user state |
| Token Storage | Store/retrieve/remove JWT |
| HTTP Interceptor | Add token to every request |
| Auth Guard | Protect routes from unauthenticated users |
| Token Refresh | Handle expired tokens transparently |

---

## Code

### Auth Service

```typescript
interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
  expiresIn: number;
}

interface User {
  id: number;
  email: string;
  name: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private apiUrl = '/api/auth';
  
  private currentUser = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUser.asObservable();
  isLoggedIn$ = this.currentUser$.pipe(map(user => !!user));

  constructor() {
    // Restore user from token on app start
    this.loadUserFromToken();
  }

  login(email: string, password: string): Observable<User> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, { email, password }).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
        this.currentUser.next(response.user);
      }),
      map(response => response.user)
    );
  }

  register(data: RegisterDTO): Observable<User> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
        this.currentUser.next(response.user);
      }),
      map(response => response.user)
    );
  }

  logout(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    this.currentUser.next(null);
    this.router.navigate(['/login']);
  }

  refreshToken(): Observable<string> {
    const refreshToken = localStorage.getItem('refresh_token');
    return this.http.post<AuthResponse>(`${this.apiUrl}/refresh`, { refreshToken }).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
        this.currentUser.next(response.user);
      }),
      map(response => response.accessToken)
    );
  }

  getToken(): string | null {
    return localStorage.getItem('access_token');
  }

  isTokenExpired(token?: string): boolean {
    const t = token || this.getToken();
    if (!t) return true;
    
    try {
      const payload = JSON.parse(atob(t.split('.')[1]));
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpired(token);
  }

  getCurrentUser(): User | null {
    return this.currentUser.value;
  }

  hasRole(role: string): boolean {
    return this.currentUser.value?.roles.includes(role) ?? false;
  }

  private storeTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
  }

  private loadUserFromToken(): void {
    const token = this.getToken();
    if (token && !this.isTokenExpired(token)) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        this.currentUser.next({
          id: payload.sub,
          email: payload.email,
          name: payload.name,
          roles: payload.roles || []
        });
      } catch {
        this.logout();
      }
    }
  }
}
```

### Login Component

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
      <h2>Login</h2>
      
      @if (errorMessage) {
        <div class="alert error">{{ errorMessage }}</div>
      }

      <div class="form-group">
        <label>Email</label>
        <input formControlName="email" type="email" autocomplete="email">
      </div>

      <div class="form-group">
        <label>Password</label>
        <input formControlName="password" type="password" autocomplete="current-password">
      </div>

      <button type="submit" [disabled]="loginForm.invalid || isLoading">
        {{ isLoading ? 'Logging in...' : 'Login' }}
      </button>

      <p>Don't have an account? <a routerLink="/register">Register</a></p>
    </form>
  `
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]]
  });

  isLoading = false;
  errorMessage = '';

  onSubmit(): void {
    if (this.loginForm.invalid) return;

    this.isLoading = true;
    this.errorMessage = '';

    const { email, password } = this.loginForm.value;
    this.authService.login(email!, password!).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 401) {
          this.errorMessage = 'Invalid email or password';
        } else {
          this.errorMessage = 'Login failed. Please try again.';
        }
      }
    });
  }
}
```

### Auth Guard

```typescript
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

export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const requiredRoles = route.data['roles'] as string[];

  if (!requiredRoles?.length) return true;

  const hasRole = requiredRoles.some(role => authService.hasRole(role));
  return hasRole || router.createUrlTree(['/unauthorized']);
};
```

### Auth Interceptor

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  
  // Skip for auth endpoints
  if (req.url.includes('/auth/')) {
    return next(req);
  }

  const token = authService.getToken();
  if (token && !authService.isTokenExpired(token)) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(req);
};
```

### Route Configuration

```typescript
export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'profile', component: ProfileComponent },
      {
        path: 'admin',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadChildren: () => import('./admin/admin.routes').then(m => m.ADMIN_ROUTES)
      }
    ]
  }
];
```

---

## Diagram

```
Complete JWT Auth Architecture:

┌────────────────────────────────────────────────────────────────┐
│                        Angular Frontend                          │
│                                                                  │
│  ┌──────────┐  ┌────────────┐  ┌────────────┐  ┌───────────┐ │
│  │Login Form│→ │Auth Service │→ │Token Store │  │Auth Guard │ │
│  └──────────┘  └─────┬──────┘  │(localStorage)│  └───────────┘ │
│                       │         └────────────┘                   │
│                       ↓                                          │
│  ┌─────────────────────────────────────────┐                    │
│  │         HTTP Interceptor                 │                    │
│  │  req.clone({ Authorization: Bearer JWT })│                    │
│  └──────────────────────┬──────────────────┘                    │
└─────────────────────────┼───────────────────────────────────────┘
                          │ HTTP
                          ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Backend                         │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Spring Security Filter Chain                              │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │   │
│  │  │JWT Filter    │→ │Validate Token│→ │Set Auth Context│  │   │
│  │  │(OncePerReq.) │  │(parse claims)│  │(SecurityContext)│  │   │
│  │  └─────────────┘  └──────────────┘  └────────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              ↓                                   │
│  ┌────────────┐  ┌──────────┐  ┌────────────┐  ┌───────────┐  │
│  │ Controller  │→ │ Service  │→ │ Repository │→ │ Database  │  │
│  └────────────┘  └──────────┘  └────────────┘  └───────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions and Answers

**Q1: How do you implement JWT authentication in Angular?**
> Login component sends credentials to backend via AuthService. Backend returns JWT + refresh token. Store in localStorage. HTTP Interceptor reads token and adds `Authorization: Bearer <token>` header to every request. Auth Guard checks token validity before allowing route access. On 401, try refresh token; if that fails, logout.

**Q2: Where should you store JWT in Angular?**
> localStorage (persists across sessions, survives refresh) or sessionStorage (cleared on tab close). localStorage is more common. httpOnly cookies are more secure against XSS but require backend support. Never store in component state alone (lost on refresh). Use a service to abstract storage.

**Q3: How do you handle token expiration?**
> Check expiry before adding token in interceptor. When 401 received: attempt silent refresh using refresh token. If refresh succeeds, retry the failed request with new token. If refresh fails, logout user and redirect to login. Queue concurrent requests during refresh to avoid multiple refresh calls.

**Q4: How do you implement role-based access in Angular?**
> Extract roles from JWT payload (decode base64). AuthService provides `hasRole(role)` method. RoleGuard checks route's `data.roles` against user's roles. In templates, use `@if (authService.hasRole('admin'))` to show/hide UI elements. Always enforce roles server-side too — client checks are UX only.

**Q5: What is the complete flow when a user accesses a protected route?**
> 1. User navigates to /dashboard. 2. Auth Guard runs — checks if token exists and is valid. 3. If no token → redirect to /login?returnUrl=/dashboard. 4. User logs in → token stored. 5. Redirect to /dashboard. 6. Component makes HTTP call → interceptor adds token. 7. Backend validates token → returns data. 8. If token expired → interceptor catches 401 → refreshes → retries.

---

## Common Mistakes

1. **Storing token in component state (lost on refresh)**
2. **Not checking token expiry before sending requests**
3. **Not handling token refresh (user forced to re-login frequently)**
4. **Not redirecting to returnUrl after login**
5. **Not skipping auth endpoints in interceptor (circular)**
6. **Client-only role checks without server enforcement**

---

## Best Practices

1. **Store tokens in localStorage** with proper key names.
2. **Decode JWT client-side** for user info (avoid extra API call).
3. **Implement silent refresh** before token expires.
4. **Queue requests** during token refresh.
5. **Use guards for routes**, interceptors for HTTP, service for state.
6. **Always validate on server** — client security is cosmetic.
7. **Clear all auth state on logout** (tokens, user, cached data).
8. **Use `returnUrl`** for seamless post-login redirect.

---

## Production Considerations

- **Token storage security**: localStorage is vulnerable to XSS. Sanitize all content, use CSP headers.
- **Refresh token rotation**: Backend should issue new refresh token on each refresh (limits damage from stolen tokens).
- **Token lifetime**: Access token 15-30min, refresh token 7-30 days.
- **Logout everywhere**: Backend should have endpoint to invalidate refresh tokens.
- **Auto-logout**: Detect inactivity and logout after timeout.

---

## Related Topics

- → [14. Route Guards](./14-route-guards.md)
- → [20. HTTP Client](./20-http-client.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
- → [38. Angular + Spring Boot Integration](./38-angular-spring-boot.md)
- → [40. Security](./40-security.md)
