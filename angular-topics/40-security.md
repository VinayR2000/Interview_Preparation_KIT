# 40. Security

---

## Theory

Angular application security covers authentication, authorization, XSS prevention, CSRF protection, and secure communication with the backend.

### Security Threats and Angular Protections

| Threat | Description | Angular Protection |
|--------|-------------|-------------------|
| **XSS** | Inject malicious scripts | Auto-sanitization of bindings |
| **CSRF** | Forged requests with user's session | HttpClient XSRF support |
| **JWT theft** | Token stolen via XSS | Store securely, short expiry |
| **Clickjacking** | Embedding app in iframe | X-Frame-Options header |
| **Data exposure** | Sensitive data in client | Never store secrets client-side |

### XSS Protection (Built-in)

```typescript
// Angular auto-sanitizes ALL template bindings
@Component({
  template: `
    <!-- ✅ SAFE — Angular sanitizes HTML -->
    <p>{{ userInput }}</p>
    <!-- If userInput = '<script>alert("xss")</script>'
         Rendered as: &lt;script&gt;alert("xss")&lt;/script&gt; -->
    
    <!-- ✅ SAFE — innerHTML is sanitized -->
    <div [innerHTML]="htmlContent"></div>
    <!-- Scripts and event handlers are stripped -->
    
    <!-- ⚠️ DANGEROUS — bypasses sanitization -->
    <div [innerHTML]="trustedHtml"></div>
  `
})
export class SafeComponent {
  userInput = '<script>alert("xss")</script>'; // Escaped automatically
  
  // Only bypass when you TRUST the source (e.g., server-rendered CMS content)
  private sanitizer = inject(DomSanitizer);
  trustedHtml = this.sanitizer.bypassSecurityTrustHtml(this.serverHtml);
  // ⚠️ Never use with user input!
}
```

### JWT Security Best Practices

```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  // Token storage considerations:
  // localStorage: Persists, but vulnerable to XSS
  // sessionStorage: Tab-scoped, cleared on close
  // httpOnly cookie: Most secure, but requires backend support + same origin
  
  // For localStorage approach — minimize exposure:
  private readonly TOKEN_KEY = 'auth_token';

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  // Decode token WITHOUT external libraries (reduce attack surface)
  decodeToken(token: string): any {
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload));
    } catch {
      return null;
    }
  }

  isTokenExpired(token: string): boolean {
    const decoded = this.decodeToken(token);
    if (!decoded?.exp) return true;
    return decoded.exp * 1000 < Date.now();
  }
}
```

### Content Security Policy

```html
<!-- index.html or server headers -->
<meta http-equiv="Content-Security-Policy" 
      content="default-src 'self'; 
               script-src 'self'; 
               style-src 'self' 'unsafe-inline';
               img-src 'self' data: https:;
               connect-src 'self' https://api.myapp.com;">
```

### CSRF/XSRF Protection

```typescript
// Angular HttpClient automatically reads XSRF-TOKEN cookie
// and sends it as X-XSRF-TOKEN header

// Spring Boot configuration:
// CookieCsrfTokenRepository.withHttpOnlyFalse()
// Angular reads the cookie and sends the header

// For JWT-only APIs (no cookies): CSRF is not needed
// JWT in Authorization header is not automatically sent (unlike cookies)
```

### Secure Route Guards

```typescript
// Always combine client-side guards with server-side authorization
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }

  // Check token expiry
  const token = auth.getToken();
  if (token && auth.isTokenExpired(token)) {
    auth.logout();
    return router.createUrlTree(['/login']);
  }

  return true;
};

// IMPORTANT: Client-side guards are for UX only!
// Server MUST validate JWT on every API request
// A user can bypass Angular guards via browser DevTools
```

### Secure HTTP Communication

```typescript
// Always use HTTPS in production
// Interceptor to enforce HTTPS
export const httpsInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('https') && !req.url.startsWith('/')) {
    // In production, force HTTPS for absolute URLs
    const secureReq = req.clone({ url: req.url.replace('http://', 'https://') });
    return next(secureReq);
  }
  return next(req);
};
```

---

## Diagram

```
Angular Security Layers:
┌──────────────────────────────────────────────────────────────┐
│ Browser Security                                              │
│  ├── Same-Origin Policy                                       │
│  ├── Content Security Policy (CSP)                           │
│  └── HTTPS (TLS encryption)                                  │
├──────────────────────────────────────────────────────────────┤
│ Angular Framework Security                                    │
│  ├── Template auto-sanitization (XSS prevention)             │
│  ├── DomSanitizer (controlled bypass)                        │
│  ├── HttpClient XSRF support                                │
│  └── AOT compilation (prevents template injection)           │
├──────────────────────────────────────────────────────────────┤
│ Application Security                                          │
│  ├── Route Guards (client-side access control)               │
│  ├── HTTP Interceptors (token attachment, error handling)     │
│  ├── Token management (storage, refresh, expiry)             │
│  └── Role-based UI rendering                                 │
├──────────────────────────────────────────────────────────────┤
│ Backend Security (Spring Security) — ALWAYS ENFORCE HERE     │
│  ├── JWT validation on every request                         │
│  ├── Role-based authorization (@PreAuthorize)                │
│  ├── Input validation (@Valid)                               │
│  ├── Rate limiting                                           │
│  └── CORS configuration                                      │
└──────────────────────────────────────────────────────────────┘
```

---

## Interview Questions and Answers

**Q1: How does Angular prevent XSS?**
> Angular sanitizes all values bound to the DOM automatically. Interpolation (`{{ }}`) HTML-escapes values. `[innerHTML]` strips scripts and event handlers. `[src]`, `[href]` validate URLs. Only `bypassSecurityTrust*()` methods disable sanitization — use only with trusted server content, never user input.

**Q2: How do you secure an Angular application end-to-end?**
> 1. HTTPS everywhere. 2. JWT for authentication (interceptor attaches token). 3. Route guards for client-side access control. 4. Angular's built-in XSS protection (never bypass for user input). 5. CSP headers. 6. Short token expiry + refresh tokens. 7. Input validation on both frontend and backend. 8. Backend enforces all security — frontend is cosmetic only.

**Q3: Is it safe to store JWT in localStorage?**
> localStorage is accessible to any JavaScript on the page — if XSS exists, tokens can be stolen. Mitigations: Angular's XSS protection, CSP headers, short token expiry (15min), refresh token rotation. Most secure: httpOnly cookies (not accessible to JS), but requires same-origin and backend CSRF handling. For most apps, localStorage + proper XSS prevention is acceptable.

**Q4: Why can't you rely solely on Angular guards for security?**
> Angular guards run in the browser — users can modify JavaScript, disable guards via DevTools, or call APIs directly (Postman, curl). Guards are for user experience (hiding routes, showing login). All authorization MUST be enforced on the backend — Spring Security validates JWT and checks roles on every API call.

---

## Best Practices

1. **Never bypass DomSanitizer** for user-provided content.
2. **HTTPS only** in production — redirect HTTP to HTTPS.
3. **Short JWT expiry** (15-30 min) + refresh token.
4. **Backend enforces everything** — client security is UX only.
5. **CSP headers** to prevent inline scripts and unauthorized sources.
6. **Avoid storing sensitive data** in the browser (API keys, secrets).
7. **Validate inputs** on both frontend (UX) and backend (security).
8. **Use `angular.json` budgets** to detect unexpected code inclusion.

---

## Production Considerations

- **Security headers**: Strict-Transport-Security, X-Frame-Options, X-Content-Type-Options.
- **Dependency auditing**: `npm audit` regularly to find vulnerable packages.
- **Subresource Integrity**: Hash third-party scripts to prevent CDN tampering.
- **Error messages**: Never expose stack traces or internal details to users.
- **Logging**: Log auth events (login, logout, failed attempts) for audit.

---

## Related Topics

- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
- → [39. CORS](./39-cors.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
