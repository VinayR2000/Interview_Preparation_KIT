# 43. OAuth2 & OpenID Connect

## Theory

OAuth2 is an authorization framework that allows third-party applications to access resources on behalf of a user without sharing credentials. OpenID Connect (OIDC) is an identity layer built on top of OAuth2 that adds authentication.

### Key Distinction:
- **OAuth2** = Authorization ("What can you do?")
- **OpenID Connect** = Authentication ("Who are you?") + OAuth2

### OAuth2 Roles:

| Role | Description | Example |
|------|-------------|---------|
| Resource Owner | User who owns the data | End user |
| Client | App requesting access | Your Spring Boot app |
| Authorization Server | Issues tokens after auth | Keycloak, Auth0, Okta, Google |
| Resource Server | API protecting resources | Your REST API |

### OAuth2 Grant Types:

| Grant Type | Use Case |
|-----------|----------|
| Authorization Code | Server-side web apps (most secure) |
| Authorization Code + PKCE | Single-page apps, mobile apps |
| Client Credentials | Service-to-service (no user involved) |
| Refresh Token | Get new access token without re-auth |
| ~~Implicit~~ | Deprecated (insecure) |
| ~~Password~~ | Deprecated (credentials shared with client) |

### Tokens:
- **Access Token**: Short-lived (5-30 min), sent with API requests
- **Refresh Token**: Long-lived (days/weeks), used to get new access tokens
- **ID Token** (OIDC): Contains user identity claims (who the user is)

### OpenID Connect Scopes:
- `openid` — Required, signals OIDC request
- `profile` — Name, picture, locale
- `email` — Email address, email_verified
- `phone` — Phone number
- `offline_access` — Request refresh token

---

## Internal Working

### Authorization Code Flow:
```
1. User clicks "Login with Google"
       ↓
2. Spring Boot redirects to Authorization Server:
   GET https://accounts.google.com/o/oauth2/auth?
     response_type=code
     &client_id=YOUR_CLIENT_ID
     &redirect_uri=http://localhost:8080/login/oauth2/code/google
     &scope=openid profile email
     &state=random_csrf_token
       ↓
3. User authenticates at Google (enters credentials)
       ↓
4. Google redirects back with authorization code:
   GET http://localhost:8080/login/oauth2/code/google?
     code=AUTH_CODE_HERE
     &state=random_csrf_token
       ↓
5. Spring Boot exchanges code for tokens (server-to-server):
   POST https://oauth2.googleapis.com/token
   Body: code=AUTH_CODE&client_id=...&client_secret=...&grant_type=authorization_code
       ↓
6. Google returns:
   {
     "access_token": "ya29.a0...",
     "refresh_token": "1//0e...",
     "id_token": "eyJhbGci...",  (OIDC)
     "expires_in": 3600
   }
       ↓
7. Spring Boot:
   - Validates ID token (signature, expiry, issuer)
   - Extracts user info (email, name, picture)
   - Creates SecurityContext with user principal
   - User is authenticated ✓
```

### Client Credentials Flow (Service-to-Service):
```
Service A needs to call Service B's API:
       ↓
Service A → Authorization Server:
  POST /oauth2/token
  Body: grant_type=client_credentials
        &client_id=service-a
        &client_secret=SECRET
        &scope=orders:read
       ↓
Authorization Server validates client credentials
  → Returns access token with granted scopes
       ↓
Service A → Service B:
  GET /api/orders
  Header: Authorization: Bearer ACCESS_TOKEN
       ↓
Service B (Resource Server):
  → Validates token (signature, expiry, scope)
  → Checks scope includes "orders:read"
  → Returns data
```

### Resource Server Token Validation:
```
API receives request with: Authorization: Bearer eyJhbGci...
       ↓
BearerTokenAuthenticationFilter extracts token
       ↓
Token validation (two approaches):
  ┌── JWT (self-contained):
  │   → Verify signature with public key (from JWKS endpoint)
  │   → Check expiration (exp claim)
  │   → Check issuer (iss claim)
  │   → Check audience (aud claim)
  │   → Extract authorities from claims
  │   → No network call needed! (fast)
  │
  └── Opaque Token:
      → Call Authorization Server /introspect endpoint
      → Server confirms if token is active
      → Returns token metadata (scopes, user info)
      → Network call required (slower, but revocable)
       ↓
SecurityContext populated with authenticated principal
       ↓
Controller handles request
```

---

## Diagram

```
┌────────── AUTHORIZATION CODE FLOW ────────────────────────────┐
│                                                                │
│  ┌──────┐       ┌──────────────┐       ┌──────────────────┐  │
│  │ User │       │ Spring Boot  │       │  Auth Server     │  │
│  │      │       │ (Client)     │       │  (Keycloak/Auth0)│  │
│  └──┬───┘       └──────┬───────┘       └────────┬─────────┘  │
│     │                   │                         │            │
│     │ 1. /login         │                         │            │
│     │──────────────────→│                         │            │
│     │                   │ 2. Redirect to auth     │            │
│     │←──────────────────│    server               │            │
│     │                   │                         │            │
│     │ 3. Login page     │                         │            │
│     │─────────────────────────────────────────────→           │
│     │                   │                         │            │
│     │ 4. Consent + auth │                         │            │
│     │─────────────────────────────────────────────→           │
│     │                   │                         │            │
│     │ 5. Redirect + code│                         │            │
│     │←─────────────────────────────────────────────           │
│     │──────────────────→│                         │            │
│     │                   │ 6. Exchange code→tokens │            │
│     │                   │─────────────────────────→           │
│     │                   │                         │            │
│     │                   │ 7. Access + ID tokens   │            │
│     │                   │←─────────────────────────           │
│     │                   │                         │            │
│     │ 8. Authenticated  │                         │            │
│     │←──────────────────│                         │            │
│                                                                │
└────────────────────────────────────────────────────────────────┘

┌────────── RESOURCE SERVER ────────────────────────────────────┐
│                                                                │
│  Client                     Resource Server                    │
│    │                              │                            │
│    │ GET /api/orders              │                            │
│    │ Authorization: Bearer TOKEN  │                            │
│    │─────────────────────────────→│                            │
│    │                              │                            │
│    │                    ┌─────────┴────────────┐              │
│    │                    │ Validate JWT:         │              │
│    │                    │  - Signature (JWKS)   │              │
│    │                    │  - Expiration          │              │
│    │                    │  - Issuer              │              │
│    │                    │  - Audience            │              │
│    │                    │  - Extract roles       │              │
│    │                    └─────────┬────────────┘              │
│    │                              │                            │
│    │ 200 OK {orders...}           │                            │
│    │←─────────────────────────────│                            │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## Code

### OAuth2 Client (Login with Google/GitHub):

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, profile, email
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: user:email, read:user
        provider:
          # Google and GitHub are auto-configured
          # Custom provider:
          keycloak:
            issuer-uri: http://localhost:8180/realms/myrealm
```

```java
@Configuration
@EnableWebSecurity
public class OAuth2LoginConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService()))
            )
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .build();
    }

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> customOAuth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User oAuth2User = delegate.loadUser(request);
            // Custom logic: create/update user in your DB
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");
            userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(name, email)));
            return oAuth2User;
        };
    }
}
```

### Resource Server (JWT Validation):

```yaml
# application.yml — Resource Server
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8180/realms/myrealm
          # Spring auto-fetches JWKS from: {issuer-uri}/.well-known/openid-configuration
```

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/orders/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .build();
    }

    // Convert JWT claims to Spring Security authorities
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Keycloak stores roles in: realm_access.roles
        authoritiesConverter.setAuthoritiesClaimName("realm_access.roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    // Custom converter for complex claim structures (Keycloak)
    @Bean
    public JwtAuthenticationConverter keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return Collections.emptyList();

            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
        });
        return converter;
    }
}
```

### Client Credentials (Service-to-Service):

```yaml
# application.yml — Service A calling Service B
spring:
  security:
    oauth2:
      client:
        registration:
          service-b:
            client-id: service-a-client
            client-secret: ${SERVICE_A_SECRET}
            authorization-grant-type: client_credentials
            scope: orders:read, orders:write
        provider:
          service-b:
            token-uri: http://keycloak:8180/realms/myrealm/protocol/openid-connect/token
```

```java
@Configuration
public class OAuth2ClientConfig {

    // WebClient with automatic token management
    @Bean
    public WebClient serviceBWebClient(
            OAuth2AuthorizedClientManager authorizedClientManager) {
        
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2.setDefaultClientRegistrationId("service-b");

        return WebClient.builder()
            .baseUrl("http://service-b:8080")
            .apply(oauth2.oauth2Configuration())
            .build();
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider clientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .refreshToken()
            .build();

        DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(clientProvider);
        return manager;
    }
}

// Usage — token automatically obtained and refreshed
@Service
public class OrderServiceClient {

    private final WebClient webClient;

    public List<Order> getOrders() {
        return webClient.get()
            .uri("/api/orders")
            .retrieve()
            .bodyToFlux(Order.class)
            .collectList()
            .block();
        // WebClient automatically:
        // 1. Gets token from Keycloak (client_credentials)
        // 2. Caches token until expiry
        // 3. Refreshes when expired
        // 4. Adds Authorization: Bearer TOKEN header
    }
}
```

### Custom User Mapping from OAuth2:

```java
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        // Map external OAuth2 user to internal user
        String email = oidcUser.getEmail();
        String provider = userRequest.getClientRegistration().getRegistrationId();

        User user = userRepository.findByEmailAndProvider(email, provider)
            .orElseGet(() -> {
                User newUser = User.builder()
                    .email(email)
                    .name(oidcUser.getFullName())
                    .picture(oidcUser.getPicture())
                    .provider(provider)
                    .providerId(oidcUser.getSubject())
                    .role(Role.USER)
                    .build();
                return userRepository.save(newUser);
            });

        // Return custom principal with internal user ID + OAuth2 attributes
        return new CustomOidcUser(user, oidcUser);
    }
}
```

### Multi-Tenant Resource Server:

```java
@Bean
public JwtDecoder jwtDecoder() {
    // Support multiple issuers (multi-tenant)
    Map<String, JwtDecoder> decoders = Map.of(
        "https://accounts.google.com", JwtDecoders.fromIssuerLocation("https://accounts.google.com"),
        "http://keycloak:8180/realms/tenant-a", JwtDecoders.fromIssuerLocation("http://keycloak:8180/realms/tenant-a"),
        "http://keycloak:8180/realms/tenant-b", JwtDecoders.fromIssuerLocation("http://keycloak:8180/realms/tenant-b")
    );

    return token -> {
        // Peek at issuer claim without full validation first
        String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        JwtDecoder decoder = decoders.get(issuer);
        if (decoder == null) throw new JwtException("Unknown issuer: " + issuer);
        return decoder.decode(token);
    };
}
```

---

## Dry Run

### Login with Google Flow:

```
1. User visits: http://myapp.com/dashboard (protected)
   → Not authenticated → Redirect to /oauth2/authorization/google

2. Spring redirects to Google:
   https://accounts.google.com/o/oauth2/auth?
     client_id=12345.apps.googleusercontent.com
     &redirect_uri=http://myapp.com/login/oauth2/code/google
     &scope=openid%20profile%20email
     &response_type=code
     &state=abc123

3. User logs into Google, consents to sharing profile+email

4. Google redirects back:
   http://myapp.com/login/oauth2/code/google?code=4/0AX4XfWh...&state=abc123

5. Spring (server-side) exchanges code for tokens:
   POST https://oauth2.googleapis.com/token
   {client_id, client_secret, code, redirect_uri, grant_type=authorization_code}

6. Google returns:
   {
     "access_token": "ya29.a0AVvZ...",
     "id_token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMDk4...",
     "expires_in": 3600,
     "refresh_token": "1//0eXy..."
   }

7. Spring validates id_token:
   - Signature verified via Google's JWKS endpoint
   - Claims: sub=10987, email=john@gmail.com, name="John Doe"

8. CustomOAuth2UserService:
   - Finds/creates user in DB
   - SecurityContext populated

9. User redirected to /dashboard (authenticated ✓)
```

### Client Credentials Flow:

```
1. Order Service needs to call Inventory Service

2. WebClient (with OAuth2 filter) makes request:
   - Checks: Do we have a valid token for "service-b" registration?
   - No token cached → request one

3. Token request to Keycloak:
   POST http://keycloak:8180/realms/myrealm/protocol/openid-connect/token
   Body: grant_type=client_credentials&client_id=service-a&client_secret=SECRET&scope=inventory:read

4. Keycloak returns:
   {"access_token": "eyJhbG...", "expires_in": 300}

5. WebClient adds header and calls Inventory Service:
   GET http://inventory-service/api/stock
   Authorization: Bearer eyJhbG...

6. Inventory Service (Resource Server):
   - BearerTokenAuthenticationFilter extracts token
   - JwtDecoder validates signature (JWKS from Keycloak)
   - Checks scope includes "inventory:read" ✓
   - Returns stock data

7. Token cached by WebClient until expiry (300s)
   - Next call reuses cached token (no Keycloak round-trip)
```

---

## Complexity

| Operation | Time |
|-----------|------|
| JWT validation (local) | ~1-5ms (signature verification, no network) |
| Opaque token introspection | ~10-50ms (network call to auth server) |
| JWKS fetch (cached) | 0ms (cached), ~50-100ms (first fetch/refresh) |
| Authorization code exchange | ~100-300ms (server-to-server HTTP) |
| Token refresh | ~50-150ms (server-to-server HTTP) |

---

## Real Project Usage

### Keycloak Setup for Microservices:

```yaml
# docker-compose.yml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    command: start-dev
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    ports:
      - "8180:8080"

# Keycloak configuration:
# Realm: my-platform
# Clients:
#   - frontend-app (public, authorization_code + PKCE)
#   - order-service (confidential, client_credentials)
#   - user-service (confidential, client_credentials)
# Roles: ADMIN, USER, SERVICE
# Users: mapped to roles
```

### Token Claims Structure (Keycloak JWT):

```json
{
  "iss": "http://keycloak:8180/realms/my-platform",
  "sub": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "aud": "order-service",
  "exp": 1699999999,
  "iat": 1699999000,
  "email": "john@example.com",
  "preferred_username": "john",
  "realm_access": {
    "roles": ["USER", "PREMIUM"]
  },
  "resource_access": {
    "order-service": {
      "roles": ["order:read", "order:write"]
    }
  }
}
```

---

## Interview Questions

1. **What is the difference between OAuth2 and OpenID Connect?**
   - OAuth2: Authorization framework (delegates access to resources, issues access tokens). OIDC: Authentication layer on top of OAuth2 (verifies identity, issues ID tokens with user info). OAuth2 = "what can you do", OIDC = "who are you".

2. **Explain the Authorization Code flow step by step.**
   - User redirected to auth server → User authenticates → Auth server redirects back with code → App exchanges code for tokens (server-to-server, secret included) → App gets access token + ID token. Code exchange is server-side (secure, secret not exposed to browser).

3. **What is the difference between Access Token and ID Token?**
   - Access Token: Sent to Resource Server to access APIs (short-lived, contains scopes/permissions). ID Token: Contains user identity claims (name, email, sub), used by Client to identify user. Access token = for APIs, ID token = for the app itself.

4. **How does Spring Boot validate JWT tokens as a Resource Server?**
   - Fetches public keys from JWKS endpoint (cached). Verifies JWT signature. Checks expiration (exp), issuer (iss), audience (aud). Extracts authorities from claims. All done locally (no auth server call needed per request).

5. **When would you use Client Credentials vs Authorization Code?**
   - Client Credentials: Service-to-service (no user involved), machine-to-machine, background jobs. Authorization Code: User-facing applications where the user must authenticate and consent. Client Credentials has no user context.

---

## Follow-up Questions

1. What is PKCE and why is it needed for SPAs?
   - PKCE (Proof Key for Code Exchange): Client generates code_verifier (random), sends hash (code_challenge) with auth request. On token exchange, sends original verifier. Prevents authorization code interception attacks. Required for public clients (no client_secret).

2. How do you handle token revocation with JWT?
   - JWT is self-contained (can't be "revoked" at auth server). Options: Short expiry (5 min) + refresh token rotation. Token blacklist (Redis check on each request — adds latency). Revoke refresh token (user must re-authenticate when access token expires).

3. How do you implement role-based access from OAuth2 tokens?
   - Map token claims to Spring Security authorities. Use JwtAuthenticationConverter to extract roles from custom claims (e.g., realm_access.roles in Keycloak). Then @PreAuthorize("hasRole('ADMIN')") works normally.

4. What is the difference between opaque tokens and JWT?
   - JWT: Self-contained, validated locally (fast, but can't revoke immediately). Opaque: Random string, requires introspection call to auth server (slower, but instantly revocable). JWT for performance, opaque for strict security.

5. How do you handle multi-tenant OAuth2 in Spring Boot?
   - Multiple issuer support: Custom JwtDecoder that routes to correct JWKS based on issuer claim. Tenant-aware security: Extract tenant from token claims, apply tenant-specific authorization rules. Separate Keycloak realms per tenant.

---

## Common Mistakes

1. **Storing tokens in localStorage** - XSS vulnerable. Use httpOnly cookies for refresh tokens.
2. **Long-lived access tokens** - Hard to revoke. Keep short (5-15 min) with refresh tokens.
3. **Not validating audience (aud) claim** - Token for service A accepted by service B.
4. **Exposing client_secret in frontend** - Use PKCE for public clients. Secret only for confidential (server-side) clients.
5. **Not implementing token refresh** - Users forced to re-login when access token expires.
6. **Trusting all token claims without verification** - Always verify signature and issuer.
7. **Same client for all services** - Use separate client registrations with minimal scopes per service.

---

## Best Practices

1. **Use Authorization Code + PKCE** for all user-facing applications
2. **Client Credentials** for service-to-service (no user context)
3. **Short access token expiry** (5-15 min) with refresh token rotation
4. **Validate issuer, audience, and signature** on every request
5. **Minimal scopes** - Request only what you need
6. **JWKS caching** - Don't fetch keys on every request (Spring does this automatically)
7. **Use established providers** (Keycloak, Auth0, Okta) - Don't build your own auth server
8. **Separate client registrations** per service with different scopes
9. **Store refresh tokens securely** - httpOnly cookie or encrypted storage
10. **Implement logout properly** - Revoke refresh token, clear session, redirect to auth server logout

---

## Production Considerations

- **Auth server HA**: Keycloak clustered (multiple instances), database-backed sessions
- **JWKS caching**: Spring caches public keys; configure refresh interval for key rotation
- **Token size**: Large JWTs (many claims) can hit header limits in proxies. Keep claims minimal.
- **Rate limiting on token endpoint**: Prevent brute force on client credentials
- **Key rotation**: Auth server rotates signing keys periodically; resource servers auto-fetch new JWKS
- **Monitoring**: Track token issuance rate, failed validations, refresh rates
- **Compliance**: GDPR consent in OAuth2 scopes, token lifetime policies, audit trail

---

## Related Topics

- Spring Security (authentication/authorization foundation)
- JWT Security (token structure and validation)
- Microservices (service-to-service auth)
- API Gateway (token validation at edge)
- Redis (token blacklisting, session storage)
- Spring Cloud (security propagation)
