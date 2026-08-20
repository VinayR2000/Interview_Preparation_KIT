# 19. Service-to-Service Security

## Theory

Internal service communication needs its own security layer. Just because traffic is internal doesn't mean it's trusted — zero-trust networking assumes no implicit trust.

### Approaches:

| Approach | How It Works | When to Use |
|----------|-------------|-------------|
| JWT Propagation | Forward user's JWT to downstream services | User context needed downstream |
| Client Credentials | Service obtains its own token | Service-to-service without user context |
| mTLS | Mutual certificate authentication | Service identity verification |
| Service Identity (IAM) | Cloud IAM roles for services | Cloud-native environments |

### Key Concepts:
- **JWT Propagation**: Pass the user's token along the call chain
- **Client Credentials OAuth2**: Service authenticates as itself (machine-to-machine)
- **mTLS**: Both services present certificates — proves identity
- **Service Mesh**: Automatically handles mTLS, authorization between services
- **Secret Rotation**: Regular credential changes without downtime

---

## Internal Working

### JWT Propagation:

```
┌────────────────────────────────────────────────────────────┐
│ JWT PROPAGATION                                             │
│                                                             │
│ User JWT: {sub: "user-123", roles: ["USER"]}              │
│                                                             │
│ Client                                                     │
│   │ Bearer eyJ...                                         │
│   ↓                                                        │
│ API Gateway (validates JWT)                                │
│   │ X-User-Id: user-123                                   │
│   │ Authorization: Bearer eyJ... (forwarded)              │
│   ↓                                                        │
│ Order Service                                             │
│   │ "I need to check user's payment methods"              │
│   │ Forward JWT to Payment Service                        │
│   │ Authorization: Bearer eyJ... (same token)             │
│   ↓                                                        │
│ Payment Service                                           │
│   │ Validates JWT → knows it's user-123                  │
│   │ Returns user-123's payment methods                   │
│                                                             │
│ Benefit: Downstream knows WHO initiated the request       │
│ Risk: Token might expire mid-chain in long flows          │
└────────────────────────────────────────────────────────────┘
```

### Client Credentials (Machine-to-Machine):

```
┌────────────────────────────────────────────────────────────┐
│ CLIENT CREDENTIALS FLOW                                     │
│                                                             │
│ Order Service needs to call Payment Service                │
│ (background job, no user context)                          │
│                                                             │
│ 1. Order Service → Auth Server                            │
│    POST /oauth2/token                                     │
│    grant_type=client_credentials                          │
│    client_id=order-service                                │
│    client_secret=***                                      │
│    scope=payment:read payment:write                       │
│                                                             │
│ 2. Auth Server → Order Service                            │
│    {                                                        │
│      access_token: "eyJ...",                              │
│      token_type: "Bearer",                                │
│      expires_in: 3600,                                    │
│      scope: "payment:read payment:write"                  │
│    }                                                        │
│                                                             │
│ 3. Order Service → Payment Service                        │
│    Authorization: Bearer eyJ... (service token)           │
│                                                             │
│ 4. Payment Service validates:                             │
│    - Token is valid                                       │
│    - Issuer is trusted auth server                        │
│    - Scope includes "payment:write"                       │
│    - Client = "order-service" (authorized caller)         │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

### mTLS (Mutual TLS):

```
┌────────────────────────────────────────────────────────────┐
│ mTLS (Mutual TLS)                                           │
│                                                             │
│ Regular TLS:                                               │
│   Client verifies server certificate (one-way)            │
│   Server doesn't verify client                            │
│                                                             │
│ Mutual TLS:                                               │
│   Client verifies server certificate ✓                    │
│   Server verifies client certificate ✓                    │
│   Both sides prove identity                               │
│                                                             │
│ ┌──────────────┐                    ┌──────────────┐     │
│ │ Order Service│                    │Payment Service│     │
│ │              │                    │              │     │
│ │ Client Cert: │ ←── TLS Handshake ──→│ Server Cert: │    │
│ │ order-svc.crt│                    │ payment.crt  │     │
│ │              │                    │              │     │
│ │ "I am Order" │ verify each other  │"I am Payment"│     │
│ └──────────────┘                    └──────────────┘     │
│                                                             │
│ Service Mesh (Istio) automates this:                      │
│ - Generates certificates per service                      │
│ - Rotates certificates automatically                      │
│ - Sidecar proxies handle TLS                             │
│ - Application code doesn't change                        │
│                                                             │
│ ┌─────────┐    mTLS    ┌─────────┐                      │
│ │App │Proxy│ ←────────→ │Proxy│App│                      │
│ │    │(Envoy)           │(Envoy)  │                      │
│ └─────────┘             └─────────┘                      │
│ Application sends plain HTTP                              │
│ Sidecar handles mTLS transparently                       │
└────────────────────────────────────────────────────────────┘
```

---

## Code

### JWT Propagation with WebClient:

```java
@Service
public class OrderService {

    private final WebClient.Builder webClientBuilder;

    public PaymentMethodsResponse getUserPaymentMethods(String userJwt) {
        // Propagate user's JWT to downstream service
        return webClientBuilder.build()
            .get()
            .uri("http://payment-service/api/payment-methods")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userJwt)
            .retrieve()
            .bodyToMono(PaymentMethodsResponse.class)
            .block();
    }
}

// Automatic JWT propagation via filter
@Component
public class JwtPropagationFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> (JwtAuthenticationToken) ctx.getAuthentication())
            .map(auth -> ClientRequest.from(request)
                .header(HttpHeaders.AUTHORIZATION, 
                    "Bearer " + auth.getToken().getTokenValue())
                .build())
            .defaultIfEmpty(request)
            .flatMap(next::exchange);
    }
}
```

### Client Credentials Configuration:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          payment-service-client:
            client-id: order-service
            client-secret: ${ORDER_SERVICE_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: payment:read,payment:write
            provider: auth-server
        provider:
          auth-server:
            token-uri: http://auth-server:8080/oauth2/token
```

```java
@Configuration
public class ServiceClientConfig {

    @Bean
    public WebClient paymentServiceClient(
            OAuth2AuthorizedClientManager clientManager) {
        
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
        oauth2Filter.setDefaultClientRegistrationId("payment-service-client");

        return WebClient.builder()
            .baseUrl("http://payment-service")
            .apply(oauth2Filter.oauth2Configuration())
            .build();
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clients,
            OAuth2AuthorizedClientRepository authorizedClients) {
        
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build();

        DefaultOAuth2AuthorizedClientManager manager =
            new DefaultOAuth2AuthorizedClientManager(clients, authorizedClients);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }
}
```

### Service Authorization (Validating Caller Identity):

```java
@Configuration
public class ServiceAuthorizationConfig {

    // Only allow specific services to call specific endpoints
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .authorizeHttpRequests(auth -> auth
                // Only order-service can initiate payments
                .requestMatchers("/api/payments/process")
                    .hasAuthority("SCOPE_payment:write")
                // Only notification-service can query payment status
                .requestMatchers("/api/payments/*/status")
                    .hasAnyAuthority("SCOPE_payment:read")
                .anyRequest().authenticated()
            )
            .build();
    }
}
```

### Secret Rotation:

```java
@Service
@Slf4j
public class SecretRotationService {

    private final VaultTemplate vaultTemplate;
    private volatile String currentSecret;

    @Scheduled(fixedRate = 3600000)  // Check every hour
    public void refreshSecret() {
        try {
            VaultResponse response = vaultTemplate.read("secret/data/payment-service");
            String newSecret = (String) response.getData().get("api-key");
            
            if (!newSecret.equals(currentSecret)) {
                currentSecret = newSecret;
                log.info("Secret rotated successfully");
            }
        } catch (Exception e) {
            log.error("Failed to refresh secret", e);
            // Keep using current secret
        }
    }

    public String getCurrentSecret() {
        return currentSecret;
    }
}
```

---

## Interview Questions

1. **JWT Propagation vs Client Credentials — when to use each?**
   - JWT Propagation: When downstream needs to know the end-user identity (user-specific operations). Client Credentials: Service-to-service calls without user context (background jobs, system operations).

2. **What is mTLS and why use it?**
   - Mutual TLS: Both client and server authenticate via certificates. Proves service identity (not just server identity). Prevents unauthorized services from communicating. Service mesh automates certificate management.

3. **How does a service mesh handle security?**
   - Sidecar proxy (Envoy) automatically: negotiates mTLS, injects certificates, encrypts traffic. Application code is unaware — sends plain HTTP. Centralized policy defines who can call whom.

4. **How to implement zero-trust networking?**
   - Never trust based on network location. Every call authenticated (mTLS). Every call authorized (policies). Encrypt all traffic. Minimal permissions (least privilege). Verify continuously.

5. **How to handle secret rotation?**
   - Use secrets manager (Vault, AWS Secrets Manager). Services periodically refresh secrets. Dual-active period during rotation (both old and new valid). No secrets in code or config files.

---

## Common Mistakes

1. **Trusting internal network** — Lateral movement attacks bypass perimeter
2. **Long-lived service credentials** — Compromised credentials valid forever
3. **No service-level authorization** — Any service can call any endpoint
4. **Secrets in environment variables** — Visible in process listing, logs
5. **Not propagating user context** — Lose audit trail in downstream calls
6. **Certificate expiry** — Services fail when certs expire without rotation

---

## Best Practices

1. **Zero trust** — Authenticate and authorize every call, even internal
2. **Short-lived tokens** — Minimize damage window if compromised
3. **Automatic rotation** — Secrets, certificates, tokens
4. **Service mesh for mTLS** — Don't implement TLS in application code
5. **Least privilege** — Each service can only call what it needs
6. **Audit all access** — Log who called what for compliance
7. **Network policies** — Kubernetes NetworkPolicy as additional layer
