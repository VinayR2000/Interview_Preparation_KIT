# 18. Security Patterns ⭐⭐⭐⭐⭐

## Theory

Security in microservices is more complex than monoliths because every service is a potential attack surface. Authentication and authorization must work across distributed services.

### Key Concepts:
- **Authentication**: Who are you? (Identity verification)
- **Authorization**: What can you do? (Permission check)
- **OAuth 2.0**: Authorization framework for delegated access
- **OpenID Connect (OIDC)**: Identity layer on top of OAuth 2.0
- **JWT**: Self-contained token carrying identity + claims
- **API Gateway Security**: Centralized auth at the edge
- **Service-to-Service Security**: Internal service authentication

### Token Flow:
```
Client → Login → Auth Server → JWT (Access + Refresh tokens)
Client → API Request + JWT → API Gateway → Validate → Forward to Service
```

---

## Internal Working

### JWT-Based Security Flow:

```
┌──────────────────────────────────────────────────────────────┐
│                    SECURITY FLOW                               │
│                                                               │
│  1. Authentication (Login)                                   │
│  ┌──────┐     credentials      ┌───────────┐               │
│  │Client│ ───────────────────→ │ Auth Server│               │
│  └──────┘                      │ (Keycloak/ │               │
│     ↑                          │  Auth0)    │               │
│     │    JWT tokens            └─────┬──────┘               │
│     │    (access + refresh)          │                       │
│     └────────────────────────────────┘                       │
│                                                               │
│  2. API Call with JWT                                        │
│  ┌──────┐  Authorization: Bearer <jwt>   ┌──────────┐      │
│  │Client│ ─────────────────────────────→ │API Gateway│      │
│  └──────┘                                └─────┬─────┘      │
│                                                 │            │
│  3. Gateway validates JWT                      │            │
│     - Verify signature (public key)            │            │
│     - Check expiration                         │            │
│     - Extract claims (userId, roles)           │            │
│                                                 │            │
│  4. Forward with user context                  │            │
│  ┌──────────────────────────────────────────────┘           │
│  │                                                           │
│  │  Headers:                                                │
│  │    X-User-Id: "user-123"                                │
│  │    X-User-Roles: "ADMIN,USER"                           │
│  │                                                           │
│  ↓                                                           │
│  ┌──────────────┐                                           │
│  │ Microservice │ → Check authorization (role-based)       │
│  │              │ → Process request                        │
│  └──────────────┘                                           │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### OAuth 2.0 + OpenID Connect:

```
┌────────────────────────────────────────────────────────────┐
│ OAuth 2.0 Authorization Code Flow + OIDC                    │
│                                                             │
│ 1. Client → Auth Server: /authorize?response_type=code     │
│    → User logs in, consents                                │
│    → Auth Server redirects with ?code=abc123               │
│                                                             │
│ 2. Client → Auth Server: /token                            │
│    Body: code=abc123, client_id, client_secret             │
│    ← Response:                                             │
│      {                                                      │
│        access_token: "eyJ...",   // Short-lived (15min)    │
│        refresh_token: "eyJ...",  // Long-lived (7 days)    │
│        id_token: "eyJ...",       // OIDC: user identity    │
│        expires_in: 900                                     │
│      }                                                      │
│                                                             │
│ 3. Client → API: Authorization: Bearer <access_token>      │
│                                                             │
│ 4. Token expires → Client → Auth Server: /token           │
│    Body: grant_type=refresh_token, refresh_token=...       │
│    ← New access_token                                      │
└────────────────────────────────────────────────────────────┘
```

### RBAC (Role-Based Access Control):

```
┌────────────────────────────────────────────────────┐
│ ROLE-BASED ACCESS CONTROL                           │
│                                                     │
│ JWT Claims:                                        │
│ {                                                   │
│   "sub": "user-123",                              │
│   "roles": ["ADMIN", "ORDER_MANAGER"],            │
│   "permissions": ["order:read", "order:write",    │
│                   "payment:read"]                  │
│ }                                                   │
│                                                     │
│ Authorization rules:                               │
│                                                     │
│ Endpoint            │ Required Role/Permission    │
│ ─────────────────────┼──────────────────────────── │
│ GET /orders         │ order:read                  │
│ POST /orders        │ order:write                 │
│ DELETE /orders/{id} │ ADMIN                       │
│ GET /payments       │ payment:read                │
│ POST /refunds       │ ADMIN + payment:write       │
│                                                     │
└────────────────────────────────────────────────────┘
```

---

## Diagram

```
Security Architecture:

┌──────────────────────────────────────────────────────────┐
│                                                           │
│  External Traffic                                        │
│  ┌──────────┐                                           │
│  │  Client  │                                           │
│  └────┬─────┘                                           │
│       │ HTTPS + JWT                                     │
│       ↓                                                  │
│  ┌───────────────────────────────────────────────┐      │
│  │              API GATEWAY                       │      │
│  │  - SSL Termination                            │      │
│  │  - JWT Validation                             │      │
│  │  - Rate Limiting                              │      │
│  │  - RBAC (coarse-grained)                      │      │
│  └───────────────┬───────────────────────────────┘      │
│                  │ Internal (HTTP + headers)             │
│                  │ X-User-Id, X-User-Roles              │
│    ┌─────────────┼─────────────────┐                    │
│    ↓             ↓                 ↓                    │
│  ┌─────┐    ┌───────┐    ┌──────────┐                 │
│  │Order│    │Payment│    │  User    │                  │
│  │ Svc │    │  Svc  │    │  Svc    │                  │
│  │     │    │       │    │         │                  │
│  │Fine-│    │Fine-  │    │Fine-    │                  │
│  │grained│  │grained│    │grained  │                  │
│  │authz │   │authz  │    │authz    │                  │
│  └──┬───┘   └───┬───┘    └────┬────┘                  │
│     │            │             │                        │
│     └────── mTLS between services ─────┘               │
│                                                           │
│  Internal Traffic (Service-to-Service):                  │
│  - mTLS (mutual TLS) for authentication                │
│  - JWT propagation for user context                    │
│  - Service mesh handles mTLS automatically             │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

## Code

### API Gateway JWT Validation:

```java
@Component
public class JwtAuthFilter implements GatewayFilter {

    private final JwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders()
            .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Jwt jwt = jwtDecoder.decode(token);

            // Extract claims and forward as headers
            ServerHttpRequest modified = exchange.getRequest().mutate()
                .header("X-User-Id", jwt.getSubject())
                .header("X-User-Roles", String.join(",", jwt.getClaimAsStringList("roles")))
                .header("X-User-Email", jwt.getClaimAsString("email"))
                .build();

            return chain.filter(exchange.mutate().request(modified).build());

        } catch (JwtException e) {
            return unauthorized(exchange, "Invalid token: " + e.getMessage());
        }
    }
}
```

### Microservice Authorization:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public List<OrderDto> getOrders(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Roles") String roles) {
        
        if (roles.contains("ADMIN")) {
            return orderService.getAllOrders();  // Admin sees all
        }
        return orderService.getOrdersByUser(userId);  // User sees own
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID orderId) {
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:write')")
    public ResponseEntity<OrderDto> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderDto order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
```

### Spring Security Configuration:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())  // APIs are stateless
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter()))
            )
            .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = 
            new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
```

### Service-to-Service Authentication (Client Credentials):

```java
@Configuration
public class ServiceClientConfig {

    @Bean
    public WebClient paymentServiceClient(
            OAuth2AuthorizedClientManager clientManager) {
        
        // Automatically obtains + refreshes service token
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
        oauth2.setDefaultClientRegistrationId("payment-service");

        return WebClient.builder()
            .baseUrl("http://payment-service")
            .apply(oauth2.oauth2Configuration())
            .build();
    }
}
```

```yaml
# Service-to-service OAuth2 client credentials
spring:
  security:
    oauth2:
      client:
        registration:
          payment-service:
            client-id: order-service
            client-secret: ${SERVICE_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: payment:read,payment:write
        provider:
          payment-service:
            token-uri: http://auth-server/oauth2/token
```

---

## Interview Questions

1. **How does security work in microservices?**
   - API Gateway handles authentication (validates JWT). Services handle fine-grained authorization (check roles/permissions). Service-to-service uses mTLS or client credentials. Tokens propagated via headers.

2. **OAuth 2.0 vs OpenID Connect?**
   - OAuth 2.0: Authorization framework (what can you access). OIDC: Identity layer on top (who are you). OAuth gives access_token. OIDC adds id_token with user identity claims.

3. **Why JWT for microservices?**
   - Stateless (no session store needed). Self-contained (carries claims). Decentralized validation (any service can verify with public key). Reduces inter-service calls for auth. Short-lived for security.

4. **How to handle token expiration?**
   - Access token: Short-lived (15 min). Refresh token: Long-lived (7 days). Client uses refresh token to get new access token without re-login. Refresh token rotation for security.

5. **What is mTLS?**
   - Mutual TLS: Both client and server present certificates. Ensures both sides are who they claim. Used for service-to-service in production. Service mesh (Istio) automates mTLS between all services.

6. **API Gateway security vs service-level security?**
   - Gateway: Coarse-grained (is token valid? is user authenticated?). Service: Fine-grained (does THIS user have permission for THIS resource?). Both needed — defense in depth.

---

## Common Mistakes

1. **No token validation at gateway** — Services individually validate (wasteful, inconsistent)
2. **Long-lived access tokens** — Larger window of exposure if token is stolen
3. **Storing secrets in code** — Use vault/secrets manager
4. **No service-to-service auth** — Any pod can call any service
5. **JWT in URL parameters** — Logged in access logs, bookmarked, cached
6. **Not rotating secrets** — Compromised secrets remain valid forever

---

## Best Practices

1. **Defense in depth** — Gateway auth + service auth + network policies
2. **Short-lived tokens** — Access token ≤ 15 minutes
3. **Secret management** — Vault, Kubernetes Secrets, AWS Secrets Manager
4. **mTLS between services** — Authenticate service identity
5. **Principle of least privilege** — Services have only permissions they need
6. **Audit logging** — Log all auth decisions for compliance
7. **Token not stored client-side insecurely** — HttpOnly cookies or secure storage
