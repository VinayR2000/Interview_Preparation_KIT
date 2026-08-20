# 15. Spring Security

## Theory

Spring Security is a powerful and highly customizable authentication and access-control framework for Java applications. It is the de-facto standard for securing Spring-based applications.

### Core Concepts:

- **Authentication**: Verifying WHO you are (identity verification)
- **Authorization**: Verifying WHAT you can do (access control)
- **Principal**: Currently authenticated user
- **SecurityContext**: Holds the Authentication object for current thread
- **SecurityContextHolder**: Strategy for storing SecurityContext (ThreadLocal by default)
- **Filter Chain**: Series of servlet filters that process security concerns

### Authentication Methods:
- Username/Password (Form-based, HTTP Basic)
- JWT (JSON Web Token)
- OAuth2 / OpenID Connect
- LDAP
- Certificate-based (X.509)

### Authorization Approaches:
- **Roles**: Coarse-grained (ROLE_ADMIN, ROLE_USER)
- **Authorities**: Fine-grained (READ_PRIVILEGE, WRITE_PRIVILEGE)
- **Permissions**: Resource-level access (can user X edit document Y?)

---

## Internal Working

```
HTTP Request arrives
       ↓
┌──────────────────────────────────────────────┐
│           Security Filter Chain                │
│                                               │
│  1. SecurityContextPersistenceFilter          │
│     → Loads SecurityContext from session      │
│                                               │
│  2. CsrfFilter                                │
│     → Validates CSRF token                    │
│                                               │
│  3. LogoutFilter                              │
│     → Handles logout requests                 │
│                                               │
│  4. UsernamePasswordAuthenticationFilter      │
│     → Processes login form submissions        │
│                                               │
│  5. BasicAuthenticationFilter                 │
│     → Processes HTTP Basic auth               │
│                                               │
│  6. BearerTokenAuthenticationFilter           │
│     → Processes JWT/OAuth2 tokens             │
│                                               │
│  7. AuthorizationFilter                       │
│     → Checks if user has required authority   │
│                                               │
│  8. ExceptionTranslationFilter                │
│     → Converts security exceptions to HTTP    │
│                                               │
│  9. FilterSecurityInterceptor                 │
│     → Final authorization decision            │
└──────────────────────────────────────────────┘
       ↓
Controller (if all filters pass)
```

### Authentication Flow:

```
UsernamePasswordAuthenticationFilter
       ↓
Creates UsernamePasswordAuthenticationToken (unauthenticated)
       ↓
AuthenticationManager (ProviderManager)
       ↓
Iterates AuthenticationProviders
       ↓
DaoAuthenticationProvider
       ↓
UserDetailsService.loadUserByUsername()
       ↓
PasswordEncoder.matches()
       ↓
Returns authenticated Authentication object
       ↓
SecurityContextHolder.getContext().setAuthentication(auth)
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      CLIENT REQUEST                           │
└──────────────────────────────┬──────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                    FILTER CHAIN PROXY                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ SecurityContextPersistenceFilter                        │ │
│  │ CsrfFilter                                             │ │
│  │ AuthenticationFilter                                    │ │
│  │ AuthorizationFilter                                     │ │
│  │ ExceptionTranslationFilter                              │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                  AUTHENTICATION MANAGER                       │
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ DaoAuthProvider       │  │ JwtAuthProvider       │        │
│  │                       │  │                       │        │
│  │ UserDetailsService    │  │ JwtDecoder            │        │
│  │ PasswordEncoder       │  │ TokenValidator        │        │
│  └──────────────────────┘  └──────────────────────┘        │
└──────────────────────────────┬──────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                   SECURITY CONTEXT                            │
│                                                              │
│  SecurityContextHolder → SecurityContext → Authentication    │
│                                              │               │
│                                    ┌─────────┴────────┐     │
│                                    │Principal          │     │
│                                    │Credentials        │     │
│                                    │Authorities (Roles)│     │
│                                    └──────────────────┘     │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                      CONTROLLER                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Code

### Security Configuration (Spring Boot 3.x):

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())  // Disable for REST APIs
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

### UserDetailsService Implementation:

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) 
            throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .roles(user.getRoles().stream()
                .map(Role::getName)
                .toArray(String[]::new))
            .accountLocked(!user.isActive())
            .build();
    }
}
```

### Method-Level Security:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDTO> getAllUsers() { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public UserDTO getUser(@PathVariable Long id) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and #id != authentication.principal.id")
    public void deleteUser(@PathVariable Long id) { ... }

    @PostAuthorize("returnObject.email == authentication.name")
    @GetMapping("/me")
    public UserDTO getCurrentUser() { ... }
}
```

### Custom Authentication Filter:

```java
public class CustomAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Validate token and set authentication
            Authentication auth = validateAndGetAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## Dry Run

### Scenario: User login flow

```
1. POST /api/auth/login {username: "john@example.com", password: "pass123"}

2. SecurityFilterChain checks:
   - /api/auth/** → permitAll() ✓ (passes through authentication)

3. AuthController receives request
   → Calls AuthenticationManager.authenticate()
   
4. AuthenticationManager delegates to DaoAuthenticationProvider
   → Calls UserDetailsService.loadUserByUsername("john@example.com")
   → Returns UserDetails with encoded password and roles

5. DaoAuthenticationProvider:
   → BCryptPasswordEncoder.matches("pass123", "$2a$12$...")
   → Passwords match ✓
   → Returns authenticated UsernamePasswordAuthenticationToken

6. SecurityContextHolder stores Authentication
   → Contains: Principal(john), Authorities[ROLE_USER]

7. Controller generates JWT and returns:
   → 200 OK { "token": "eyJhbGci...", "refreshToken": "..." }
```

### Scenario: Accessing protected resource

```
1. GET /api/users/5
   Header: Authorization: Bearer eyJhbGci...

2. Filter Chain:
   - JwtAuthFilter extracts token
   - Validates signature, expiration
   - Extracts username, creates Authentication
   - Sets SecurityContext

3. AuthorizationFilter:
   - Endpoint requires hasAnyRole("USER", "ADMIN")
   - User has ROLE_USER ✓
   - Access GRANTED

4. Controller processes request → Returns user data
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| Filter chain traversal | O(n) where n = number of filters |
| Authentication (DB lookup) | O(1) for indexed username lookup |
| BCrypt password verification | O(2^cost) - intentionally slow (cost=12 → ~250ms) |
| Authorization check | O(m) where m = number of authorities |
| JWT validation | O(1) - signature verification is constant time |

---

## Real Project Usage

### E-commerce Multi-Role Security:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers("/api/products/**").permitAll()
            .requestMatchers("/api/categories/**").permitAll()
            // Customer endpoints
            .requestMatchers("/api/cart/**").hasRole("CUSTOMER")
            .requestMatchers("/api/orders/**").hasRole("CUSTOMER")
            // Seller endpoints
            .requestMatchers("/api/seller/**").hasRole("SELLER")
            // Admin endpoints
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}
```

---

## Interview Questions

1. **What is the difference between Authentication and Authorization?**
   - Authentication = WHO are you (identity). Authorization = WHAT can you do (permissions)

2. **Explain the Security Filter Chain.**
   - Series of filters processing requests sequentially. Each filter handles specific security concern (CSRF, auth, authorization). Order matters.

3. **What is SecurityContextHolder?**
   - Storage mechanism for SecurityContext (which holds Authentication). Uses ThreadLocal by default so each thread has its own context.

4. **Difference between @Secured, @PreAuthorize, and @RolesAllowed?**
   - @Secured: Simple role check, Spring-specific
   - @PreAuthorize: SpEL expressions, most flexible
   - @RolesAllowed: JSR-250 standard, simple role check

5. **How does Spring Security handle CSRF?**
   - Generates unique token per session, validates on state-changing requests. Disabled for stateless REST APIs using JWT.

---

## Follow-up Questions

1. How do you implement OAuth2 Resource Server in Spring Security?
   - Add spring-boot-starter-oauth2-resource-server, configure JWT issuer-uri in properties. Spring auto-validates JWT tokens, extracts authorities from claims. Use `http.oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(...))`.

2. How does Spring Security work with microservices architecture?
   - API Gateway validates JWT and propagates it. Services validate token signature (shared public key or JWKS endpoint). Use mTLS between services. Consider service mesh (Istio) for infrastructure-level auth.

3. How to implement rate limiting with Spring Security?
   - Not built into Spring Security. Use: Spring Cloud Gateway rate limiter, Bucket4j library, or Redis-based token bucket in a custom filter placed before authentication filter.

4. What is the difference between FilterChainProxy and DelegatingFilterProxy?
   - DelegatingFilterProxy: Servlet filter that delegates to Spring bean. FilterChainProxy: The Spring Security bean that manages multiple SecurityFilterChains. DFP → FCP → SecurityFilterChain.

5. How to handle security for WebSocket connections?
   - Authenticate on handshake (HTTP upgrade request). Use token in query param or header. After handshake, SecurityContext set for the WebSocket session. Use @MessageMapping + @SendTo with security.

---

## Common Mistakes

1. **Not disabling CSRF for stateless APIs** - JWT-based APIs don't need CSRF protection
2. **Storing plain text passwords** - Always use BCryptPasswordEncoder
3. **Overly permissive rules** - `anyRequest().permitAll()` defeats the purpose
4. **Filter order issues** - Custom JWT filter must come before UsernamePasswordAuthenticationFilter
5. **Not clearing SecurityContext** - Can lead to authentication leaking between requests in thread pools
6. **Using @Secured instead of @PreAuthorize** - @PreAuthorize is more powerful with SpEL support

---

## Best Practices

1. **Use BCrypt with cost factor 12+** for password encoding
2. **Implement method-level security** with @PreAuthorize for fine-grained control
3. **Always use HTTPS** in production
4. **Implement account lockout** after N failed attempts
5. **Use security headers** (X-Content-Type-Options, X-Frame-Options, etc.)
6. **Audit authentication events** (login success/failure logging)
7. **Implement proper logout** (invalidate tokens, clear context)
8. **Follow principle of least privilege** - deny by default, grant explicitly

---

## Production Considerations

- **CORS configuration**: Properly configure for frontend domains
- **Rate limiting**: Protect login endpoints from brute force
- **Session fixation**: Spring Security handles by default (new session on auth)
- **Security headers**: Configure via `http.headers()` 
- **Actuator security**: Protect management endpoints separately
- **Multi-tenancy**: Consider tenant-aware security context
- **Token revocation**: Plan for JWT invalidation (blacklist, short expiry + refresh)

---

## Related Topics

- JWT Security
- OAuth2 / OpenID Connect
- Spring AOP (security proxying)
- Spring Boot Actuator (securing endpoints)
- Microservices Security
- CORS Configuration
