# JWT Authentication Flow - Spring Boot

## Question: How do you secure a REST API using JWT authentication? Explain the complete flow.

## Flow Summary

```
Client → Login (username/password) → Spring Security authenticates → JWT generated → Client stores token
Client → Authorization: Bearer <JWT> → JWT Filter validates → Request reaches Controller
```

---

## Detailed Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        JWT AUTHENTICATION FLOW                               │
└─────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════
 PHASE 1: LOGIN (One-time)
═══════════════════════════════════════════════════════════════════════════════

┌────────┐         POST /auth/login          ┌──────────────────┐
│        │  ───── {username, password} ─────► │                  │
│        │                                    │  AuthController   │
│        │                                    │                  │
│ Client │                                    └────────┬─────────┘
│        │                                             │
│        │                                             ▼
│        │                                    ┌──────────────────┐
│        │                                    │ Authentication   │
│        │                                    │    Manager       │
│        │                                    └────────┬─────────┘
│        │                                             │
│        │                                             ▼
│        │                                    ┌──────────────────┐
│        │                                    │UserDetailsService│
│        │                                    │  + PasswordEncoder│
│        │                                    └────────┬─────────┘
│        │                                             │
│        │                                             ▼ (authenticated)
│        │                                    ┌──────────────────┐
│        │  ◄──── { "token": "eyJhb..." } ── │  JwtUtil         │
│        │                                    │  .generateToken()│
└────────┘                                    └──────────────────┘
     │
     │ stores token
     ▼

═══════════════════════════════════════════════════════════════════════════════
 PHASE 2: SUBSEQUENT REQUESTS (Every request)
═══════════════════════════════════════════════════════════════════════════════

┌────────┐  GET /api/resource               ┌──────────────────┐
│        │  Header: Authorization:           │                  │
│ Client │  Bearer eyJhbG...                 │  JwtAuthFilter   │
│        │  ─────────────────────────────►   │  (OncePerRequest)│
└────────┘                                   └────────┬─────────┘
                                                      │
                                             ┌────────▼─────────┐
                                             │ Extract token    │
                                             │ from header      │
                                             └────────┬─────────┘
                                                      │
                                             ┌────────▼─────────┐
                                             │ Validate token   │
                                             │ (signature,      │
                                             │  expiry, claims) │
                                             └────────┬─────────┘
                                                      │
                                        ┌─────────────┼─────────────┐
                                        │             │             │
                                   INVALID         EXPIRED        VALID
                                        │             │             │
                                        ▼             ▼             ▼
                                   ┌─────────┐  ┌─────────┐  ┌──────────────┐
                                   │ 401     │  │ 401     │  │ Set Security │
                                   │Unauthor-│  │ Token   │  │ Context      │
                                   │ized     │  │ Expired │  │ Holder       │
                                   └─────────┘  └─────────┘  └──────┬───────┘
                                                                     │
                                                            ┌────────▼─────────┐
                                                            │ Authorization    │
                                                            │ Check            │
                                                            │ (@PreAuthorize)  │
                                                            └────────┬─────────┘
                                                                     │
                                                            ┌────────▼─────────┐
                                                            │   Controller     │
                                                            │   Method         │
                                                            └──────────────────┘
```

═══════════════════════════════════════════════════════════════════════════════
 PHASE 3: TOKEN REFRESH (When access token expires)
═══════════════════════════════════════════════════════════════════════════════

┌────────┐  POST /auth/refresh               ┌──────────────────┐
│        │  Body: { refreshToken: "xyz..." }  │                  │
│ Client │  ─────────────────────────────►    │  AuthController   │
│        │                                    │  /auth/refresh    │
└────────┘                                    └────────┬─────────┘
     ▲                                                 │
     │                                        ┌────────▼─────────┐
     │                                        │ Validate refresh │
     │                                        │ token (not       │
     │                                        │ expired/revoked) │
     │                                        └────────┬─────────┘
     │                                                 │
     │                                        ┌────────▼─────────┐
     │                                        │ Check DB:        │
     │                                        │ token exists &   │
     │                                        │ not revoked?     │
     │                                        └────────┬─────────┘
     │                                                 │
     │                                   ┌─────────────┼──────────────┐
     │                                   │                            │
     │                                VALID                       INVALID
     │                                   │                            │
     │                                   ▼                            ▼
     │                          ┌──────────────────┐         ┌──────────────┐
     │                          │ Generate new     │         │ 401          │
     │                          │ access token     │         │ Re-login     │
     │                          │ (+ optionally    │         │ required     │
     │                          │  rotate refresh) │         └──────────────┘
     │                          └────────┬─────────┘
     │                                   │
     │  { accessToken: "new...",         │
     │    refreshToken: "rotated..." }   │
     └───────────────────────────────────┘
```

**Refresh Token Key Points:**
- Access token: short-lived (15-30 min)
- Refresh token: longer-lived (7-30 days), stored in DB
- Refresh tokens are revocable (unlike access tokens)
- Refresh token rotation: issue new refresh token on each use, invalidate old one (prevents replay attacks)

```

---

## Key Components

| Component | Class | Responsibility |
|-----------|-------|----------------|
| JWT Filter | `OncePerRequestFilter` (custom) | Intercepts every request, extracts & validates token |
| Token Utility | `JwtUtil` / `JwtTokenProvider` | Generate, validate, parse tokens |
| Security Config | `SecurityFilterChain` bean | Configure filter chain, permit/deny paths |
| User Lookup | `UserDetailsService` impl | Load user from DB |
| Password Check | `BCryptPasswordEncoder` | Verify password hash |

---

## Filter Chain Order

```
Request
  │
  ▼
┌─────────────────────────┐
│ JwtAuthenticationFilter │  ← Added BEFORE UsernamePasswordAuthenticationFilter
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ UsernamePasswordAuth    │
│ Filter (disabled for    │
│ stateless API)          │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ AuthorizationFilter     │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ Controller / Endpoint   │
└─────────────────────────┘
```

**Registration:**
```java
http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

---

## Securing REST API - Checklist

### Authentication & Authorization
- JWT-based stateless authentication
- Role/authority-based access (`@PreAuthorize`, `hasRole()`)
- OAuth2/OpenID Connect for SSO scenarios

### Transport Security
- HTTPS everywhere (TLS)
- Disable HTTP or redirect to HTTPS

### Input Protection
- Validate all inputs (`@Valid`, Bean Validation)
- Parameterized queries (prevent SQL injection)
- Disable CSRF for stateless APIs (no cookies = no CSRF risk)
- Configure CORS restrictively

### Rate Limiting & Abuse Prevention
- Rate limiting (Bucket4j, API gateway)
- Request size limits

### Security Headers
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security`

### Operational
- Short token expiry + refresh tokens
- Audit logging of auth events
- Secure/disable Actuator endpoints in production


---

## Interview Questions & Answers

### Q1: Explain the complete JWT authentication flow in a Spring Boot application.

**Answer:**
1. Client sends POST `/auth/login` with username/password
2. `AuthenticationManager` delegates to `UserDetailsService` to load user and `PasswordEncoder` to verify password
3. On success, `JwtUtil.generateToken()` creates a signed JWT with claims (subject, roles, expiry)
4. Token is returned to the client in the response body
5. Client stores the token and includes it in subsequent requests as `Authorization: Bearer <token>`
6. `JwtAuthenticationFilter` (extends `OncePerRequestFilter`) intercepts every request, extracts the token, validates signature and expiry
7. If valid, sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
8. Request proceeds through authorization checks and reaches the controller

---

### Q2: Why do we use `OncePerRequestFilter` for the JWT filter?

**Answer:**
`OncePerRequestFilter` guarantees the filter executes exactly once per request, even if the request is internally forwarded or dispatched multiple times. Without it, token validation could run multiple times unnecessarily.

---

### Q3: Why is CSRF disabled in JWT-based stateless APIs?

**Answer:**
CSRF attacks exploit browser-stored cookies that are automatically sent with requests. JWT-based APIs don't use cookies for authentication — the token is sent explicitly in the `Authorization` header. Since the browser never automatically attaches the token, CSRF attacks aren't possible, so CSRF protection can be safely disabled.

---

### Q4: What happens when a JWT token expires?

**Answer:**
- The `JwtAuthFilter` detects the expired token during validation
- Returns `401 Unauthorized` with a message like "Token expired"
- Client must either re-login or use a refresh token to get a new access token
- Best practice: short-lived access tokens (15-30 min) + longer-lived refresh tokens

---

### Q5: How do you handle token refresh in Spring Boot?

**Answer:**
- Issue two tokens on login: access token (short expiry) + refresh token (longer expiry)
- Store refresh token in DB (for revocation capability)
- Client calls `/auth/refresh` with the refresh token when access token expires
- Server validates refresh token, checks it hasn't been revoked, issues new access token
- Optionally rotate refresh token on each use (refresh token rotation)

---

### Q6: How do you secure specific endpoints with role-based access?

**Answer:**
```java
// Method-level security
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public List<User> getAllUsers() { ... }

// Or in SecurityFilterChain config
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
    .requestMatchers("/auth/**").permitAll()
    .anyRequest().authenticated()
);
```

---

### Q7: What's the difference between `hasRole()` and `hasAuthority()`?

**Answer:**
- `hasRole("ADMIN")` → checks for authority `ROLE_ADMIN` (automatically adds `ROLE_` prefix)
- `hasAuthority("ADMIN")` → checks for exact authority string `ADMIN` (no prefix)
- Use `hasRole()` for role-based access, `hasAuthority()` for fine-grained permissions

---

### Q8: How would you invalidate/revoke a JWT token?

**Answer:**
JWTs are stateless, so you can't directly invalidate them. Common strategies:
1. **Short expiry** — tokens expire quickly, limiting damage window
2. **Token blacklist** — store revoked token IDs in Redis/DB, check on each request
3. **Token versioning** — store a version per user in DB; if token version doesn't match, reject
4. **Refresh token revocation** — revoke the refresh token so no new access tokens can be issued

---

### Q9: How do you secure REST APIs beyond authentication?

**Answer:**
- HTTPS/TLS for transport security
- Input validation (`@Valid`, Bean Validation)
- Rate limiting (Bucket4j, API Gateway)
- CORS configuration (restrict origins)
- Security headers (X-Content-Type-Options, HSTS, X-Frame-Options)
- Parameterized queries (prevent SQL injection)
- Audit logging
- Actuator endpoints secured in production
