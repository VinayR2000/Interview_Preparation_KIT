# 16. JWT Security

## Theory

JWT (JSON Web Token) is a compact, URL-safe means of representing claims between two parties. In Spring Security, JWT enables **stateless authentication** — the server doesn't need to store session data because all necessary information is encoded in the token itself.

### JWT Structure:
```
Header.Payload.Signature

eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaWF0IjoxNjk5..._signature_
```

- **Header**: Algorithm and token type `{"alg": "HS256", "typ": "JWT"}`
- **Payload**: Claims (user data, expiration, issuer) `{"sub": "user@email.com", "exp": 1699999999, "roles": ["USER"]}`
- **Signature**: HMACSHA256(base64(header) + "." + base64(payload), secret)

### Key Concepts:
- **Access Token**: Short-lived token (15-30 min) for accessing resources
- **Refresh Token**: Long-lived token (7-30 days) for obtaining new access tokens
- **Stateless**: Server doesn't store session — token contains all needed info
- **Claims**: Key-value pairs inside the payload (sub, exp, iat, custom claims)
- **Token Rotation**: Issuing new refresh token with each refresh to detect reuse

---

## Internal Working

### Login Flow:
```
Client sends POST /api/auth/login
  { "email": "john@example.com", "password": "pass123" }
       ↓
AuthController
       ↓
AuthenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(email, password))
       ↓
DaoAuthenticationProvider
       ↓
UserDetailsService.loadUserByUsername("john@example.com")
       ↓
PasswordEncoder.matches("pass123", storedHash)
       ↓
Authentication successful → Generate JWT
       ↓
JwtService.generateToken(userDetails)
  - Set subject (username)
  - Set issued at (now)
  - Set expiration (now + 15 min)
  - Set custom claims (roles, userId)
  - Sign with secret key (HMAC-SHA256)
       ↓
Return { accessToken, refreshToken } to client
```

### Request Authentication Flow:
```
Client sends GET /api/resource
  Header: Authorization: Bearer eyJhbGci...
       ↓
JwtAuthenticationFilter (extends OncePerRequestFilter)
       ↓
Extract token from Authorization header
       ↓
JwtService.extractUsername(token)
  - Decode payload
  - Get "sub" claim
       ↓
UserDetailsService.loadUserByUsername(username)
       ↓
JwtService.isTokenValid(token, userDetails)
  - Verify signature (tamper check)
  - Check expiration (not expired)
  - Match username (token user == loaded user)
       ↓
If valid:
  - Create UsernamePasswordAuthenticationToken
  - Set in SecurityContextHolder
  - Continue filter chain → Controller
       ↓
If invalid:
  - Don't set authentication
  - Continue chain → 401 Unauthorized
```

---

## Diagram

```
┌─────────── LOGIN FLOW ───────────────────────────────────────┐
│                                                               │
│  Client                Server                                 │
│    │                      │                                   │
│    │─── POST /login ─────→│                                   │
│    │    {email, password}  │                                   │
│    │                      │── AuthenticationManager            │
│    │                      │── UserDetailsService               │
│    │                      │── PasswordEncoder.matches()        │
│    │                      │── JwtService.generateToken()       │
│    │                      │                                   │
│    │←── 200 OK ──────────│                                   │
│    │    {accessToken,      │                                   │
│    │     refreshToken}     │                                   │
│    │                      │                                   │
└───────────────────────────────────────────────────────────────┘

┌─────────── REQUEST FLOW ─────────────────────────────────────┐
│                                                               │
│  Client                Server                                 │
│    │                      │                                   │
│    │─── GET /api/data ───→│                                   │
│    │  Authorization:       │                                   │
│    │  Bearer eyJhbG...     │                                   │
│    │                      │── JwtAuthFilter                    │
│    │                      │   ├── Extract token                │
│    │                      │   ├── Validate signature           │
│    │                      │   ├── Check expiration             │
│    │                      │   ├── Load UserDetails             │
│    │                      │   └── Set SecurityContext          │
│    │                      │── AuthorizationFilter              │
│    │                      │   └── Check authorities            │
│    │                      │── Controller                       │
│    │                      │                                   │
│    │←── 200 OK ──────────│                                   │
│    │    {response data}    │                                   │
│    │                      │                                   │
└───────────────────────────────────────────────────────────────┘

┌─────────── REFRESH FLOW ─────────────────────────────────────┐
│                                                               │
│  Client                Server                                 │
│    │                      │                                   │
│    │─── POST /refresh ───→│                                   │
│    │  {refreshToken}       │                                   │
│    │                      │── Validate refresh token           │
│    │                      │── Check not revoked                │
│    │                      │── Generate new access token        │
│    │                      │── (Optional) Rotate refresh token  │
│    │                      │                                   │
│    │←── 200 OK ──────────│                                   │
│    │  {newAccessToken,     │                                   │
│    │   newRefreshToken}    │                                   │
└───────────────────────────────────────────────────────────────┘
```

---

## Code

### JWT Service:

```java
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;  // 15 minutes in ms

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;  // 7 days in ms

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof CustomUserDetails customUser) {
            claims.put("userId", customUser.getId());
            claims.put("roles", customUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        }
        return buildToken(claims, userDetails.getUsername(), accessTokenExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), refreshTokenExpiration);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

### JWT Authentication Filter:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String username = jwtService.extractUsername(jwt);

            if (username != null && 
                SecurityContextHolder.getContext().getAuthentication() == null) {
                
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Token expired\"}");
            return;
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/");
    }
}
```

### Auth Controller:

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(userDetails.getUsername());

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenService
            .findByToken(request.getRefreshToken())
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        refreshTokenService.verifyExpiration(refreshToken);

        UserDetails userDetails = userDetailsService
            .loadUserByUsername(refreshToken.getUsername());
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);

        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        refreshTokenService.revokeToken(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
```

### Security Configuration with JWT:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

## Dry Run

### Complete Login → Access → Refresh Cycle:

```
Step 1: Login
  POST /api/auth/login
  Body: {"email": "john@example.com", "password": "pass123"}

  → AuthenticationManager authenticates
  → Generate access token (expires in 15 min)
    Payload: {"sub":"john@example.com","userId":1,"roles":["ROLE_USER"],"exp":1699999999}
  → Generate refresh token (expires in 7 days)
  → Store refresh token in DB
  → Response: {"accessToken": "eyJ...", "refreshToken": "abc-def-ghi"}

Step 2: Access Protected Resource
  GET /api/users/profile
  Header: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWI...

  → JwtAuthFilter:
    - Extracts "eyJhbG..." from header
    - Decodes → username = "john@example.com"
    - Loads UserDetails from DB
    - Validates: signature ✓, not expired ✓, username matches ✓
    - Sets SecurityContext with authorities [ROLE_USER]
  → AuthorizationFilter: authenticated() ✓
  → Controller returns profile data

Step 3: Token Expired (after 15 min)
  GET /api/users/profile
  Header: Authorization: Bearer eyJ... (expired)

  → JwtAuthFilter:
    - Extracts token
    - Catches ExpiredJwtException
    - Returns 401 {"error": "Token expired"}

Step 4: Refresh Token
  POST /api/auth/refresh
  Body: {"refreshToken": "abc-def-ghi"}

  → Find refresh token in DB ✓
  → Check not expired ✓
  → Generate new access token
  → Rotate refresh token (invalidate old, create new)
  → Response: {"accessToken": "eyJ...(new)", "refreshToken": "xyz-uvw-rst"}
```

---

## Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Token generation | O(1) | HMAC-SHA256 is constant time |
| Token validation (signature) | O(1) | Hash comparison |
| Token parsing (claims extraction) | O(n) | n = payload size (typically small) |
| User lookup for validation | O(1) | Indexed DB lookup |
| Refresh token lookup | O(1) | Indexed DB lookup |
| Token size | ~500-1000 bytes | Grows with claims |

---

## Real Project Usage

### Microservice JWT Propagation:

```java
// Service A calls Service B with JWT
@Component
public class ServiceBClient {

    private final WebClient webClient;

    public Mono<OrderDTO> getOrder(Long orderId) {
        String token = SecurityContextHolder.getContext()
            .getAuthentication().getCredentials().toString();

        return webClient.get()
            .uri("/api/orders/{id}", orderId)
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .bodyToMono(OrderDTO.class);
    }
}
```

### Token Blacklisting with Redis:

```java
@Service
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    public void blacklistToken(String token) {
        Date expiration = jwtService.extractExpiration(token);
        long ttl = expiration.getTime() - System.currentTimeMillis();
        redisTemplate.opsForValue()
            .set("blacklist:" + token, "revoked", ttl, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey("blacklist:" + token));
    }
}
```

---

## Interview Questions

1. **What is JWT and how does it enable stateless authentication?**
   - Self-contained token with encoded claims. Server doesn't need sessions — all info is in the token. Verified by checking signature.

2. **How do you handle JWT expiration?**
   - Short-lived access tokens (15 min) + long-lived refresh tokens (7 days). Client refreshes access token when expired.

3. **How do you invalidate/revoke a JWT?**
   - JWT is stateless so can't be truly "invalidated". Options: token blacklist (Redis), short expiry, refresh token revocation, token versioning in DB.

4. **What's the difference between access token and refresh token?**
   - Access: short-lived, sent with every request, contains permissions. Refresh: long-lived, only sent to refresh endpoint, used to get new access tokens.

5. **Why shouldn't you store sensitive data in JWT payload?**
   - Payload is Base64-encoded (NOT encrypted). Anyone can decode and read it. Only put non-sensitive identifiers.

---

## Follow-up Questions

1. How do you handle JWT in a microservices architecture (token propagation)?
   - Gateway validates token, forwards it in Authorization header to downstream services. Each service validates signature using shared public key (asymmetric) or JWKS endpoint. No shared secret needed.

2. What's the difference between symmetric (HMAC) and asymmetric (RSA) JWT signing?
   - HMAC (HS256): Same secret for signing and verifying. All services need the secret. RSA (RS256): Private key signs (auth server only), public key verifies (any service). Better for microservices.

3. How do you implement token refresh rotation to detect stolen refresh tokens?
   - Issue new refresh token with each refresh request, invalidate the old one. If old token is reused → both tokens revoked (indicates theft). Store token lineage/family in DB.

4. How does JWT compare to opaque tokens for microservices?
   - JWT: Self-contained, no introspection needed, stateless validation. Larger size, can't be revoked easily. Opaque: Requires introspection call to auth server, easily revocable, smaller. JWT preferred for performance, opaque for security-critical.

5. How do you handle JWT with WebSocket connections?
   - Pass JWT as query parameter during WebSocket handshake (headers not supported by browser WebSocket API). Validate on connect, store auth in WebSocket session attributes.

---

## Common Mistakes

1. **Storing JWT in localStorage** - Vulnerable to XSS. Use httpOnly cookies for refresh tokens.
2. **Very long access token expiry** - Defeats the purpose. Keep at 15-30 minutes.
3. **Not validating token on every request** - Always validate signature + expiration.
4. **Storing sensitive data in payload** - JWT payload is readable by anyone (Base64, not encrypted).
5. **Using weak signing keys** - Use at least 256-bit keys for HMAC-SHA256.
6. **Not implementing refresh token rotation** - Stolen refresh tokens remain valid forever.
7. **Single secret key across environments** - Each environment should have its own key.

---

## Best Practices

1. **Short access token expiry** (15 min) with refresh token rotation
2. **Store refresh tokens in httpOnly cookies** (not localStorage)
3. **Use strong secrets** (minimum 256-bit for HS256)
4. **Include minimal claims** in payload (just user ID and roles)
5. **Implement token blacklisting** for logout/revocation (Redis)
6. **Use asymmetric keys (RS256)** for microservices (public key for validation, private for signing)
7. **Add jti (JWT ID) claim** for tracking and revocation
8. **Validate issuer and audience** claims

---

## Production Considerations

- **Key rotation**: Plan for secret key rotation without invalidating existing tokens
- **Clock skew**: Allow small tolerance for expiration checks across servers
- **Token size in headers**: Large JWTs can hit header size limits in proxies/load balancers
- **Monitoring**: Track token refresh rates, failed validations, suspicious patterns
- **Rate limiting**: Protect /refresh endpoint from abuse
- **Graceful degradation**: Handle key rotation failures, Redis unavailability for blacklist
- **HTTPS only**: JWT in Authorization header must be over TLS

---

## Related Topics

- Spring Security
- OAuth2 / OpenID Connect
- Redis (token blacklisting)
- Microservices (token propagation)
- Spring Security Filter Chain
- CORS (credentials with cookies)
