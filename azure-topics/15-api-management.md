# Azure API Management (APIM)

## Theory

### What is Azure API Management?
A full-featured API gateway and management platform. Sits in front of your backend APIs (Spring Boot microservices) and provides security, rate limiting, transformation, analytics, and a developer portal. Equivalent to AWS API Gateway (but significantly more feature-rich).

### Why APIM for Microservices? ⭐⭐⭐
- Single entry point for all APIs
- Centralized authentication/authorization
- Rate limiting and throttling
- API versioning
- Request/response transformation
- Caching
- Monitoring and analytics
- Developer portal (documentation)
- **Centralized TLS/certificate management**

---

## Internal Working

### APIM Architecture ⭐⭐⭐

```
External Clients (Mobile, Web, Partners)
    │
    ▼
Azure API Management
├── Gateway (request processing)
│   ├── Authentication (OAuth 2.0, API keys, JWT)
│   ├── Rate limiting (100 requests/min per user)
│   ├── Caching (reduce backend calls)
│   ├── Request transformation
│   ├── Routing to backends
│   └── Response transformation
│
├── Management Plane (configuration)
│   ├── APIs definition
│   ├── Products (bundled APIs)
│   ├── Subscriptions (API keys)
│   └── Policies
│
├── Developer Portal (self-service)
│   ├── API documentation
│   ├── Try-it console
│   ├── API key management
│   └── Usage analytics
│
└── Backend Services:
    ├── AKS → order-service
    ├── AKS → user-service
    ├── App Service → payment-service
    ├── Azure Functions → notification-function
    └── External API → partner-service
```

### APIM Components

| Component | Description |
|-----------|-------------|
| API | One or more operations (endpoints) mapped to a backend |
| Operation | HTTP method + path (GET /orders/{id}) |
| Product | Bundle of APIs with access policies (e.g., "Free", "Premium") |
| Subscription | API key for accessing a product |
| Policy | XML-based rules applied to requests/responses |
| Backend | The actual service URL |
| Named Value | Reusable configuration (like connection strings) |
| Certificate | Client certificates for backend authentication |

---

## Policies ⭐⭐⭐

Policies are the core power of APIM. Applied at different scopes:

```
Policy Execution Pipeline:

Client Request
    │
    ▼
┌─────────────────────────────────────────────────┐
│ INBOUND Policies                                 │
│ ├── Validate JWT token                           │
│ ├── Rate limit (100/min per subscription)        │
│ ├── Set header (X-Request-ID)                    │
│ ├── Rewrite URL                                  │
│ └── Cache lookup                                 │
└─────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────┐
│ BACKEND Policy                                   │
│ └── Forward request to backend service           │
└─────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────┐
│ OUTBOUND Policies                                │
│ ├── Cache store                                  │
│ ├── Remove internal headers                      │
│ ├── Transform response (XML → JSON)              │
│ └── Set CORS headers                             │
└─────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────┐
│ ON-ERROR Policies                                │
│ └── Return custom error response                 │
└─────────────────────────────────────────────────┘
    │
    ▼
Client Response
```

### Common Policies

```xml
<policies>
    <inbound>
        <!-- JWT Validation -->
        <validate-jwt header-name="Authorization" 
                      failed-validation-httpcode="401">
            <openid-config url="https://login.microsoftonline.com/{tenant}/.well-known/openid-configuration"/>
            <required-claims>
                <claim name="aud" match="all">
                    <value>{client-id}</value>
                </claim>
            </required-claims>
        </validate-jwt>
        
        <!-- Rate Limiting -->
        <rate-limit-by-key calls="100" 
                          renewal-period="60" 
                          counter-key="@(context.Subscription.Id)" />
        
        <!-- IP Filtering -->
        <ip-filter action="allow">
            <address-range from="10.0.0.0" to="10.0.255.255"/>
        </ip-filter>
        
        <!-- Set Backend -->
        <set-backend-service base-url="https://order-service.internal:8080" />
    </inbound>
    
    <backend>
        <forward-request />
    </backend>
    
    <outbound>
        <!-- Remove internal headers -->
        <set-header name="X-Internal-Id" exists-action="delete" />
        
        <!-- Response caching -->
        <cache-store duration="300" />
        
        <!-- CORS -->
        <cors>
            <allowed-origins>
                <origin>https://app.contoso.com</origin>
            </allowed-origins>
        </cors>
    </outbound>
    
    <on-error>
        <set-body>{"error": "An error occurred"}</set-body>
    </on-error>
</policies>
```

---

## TLS/Certificate Centralization ⭐⭐⭐

This is what you discussed earlier — APIM centralizes TLS:

```
Without APIM:
Client ──HTTPS──> Service A (cert A)
Client ──HTTPS──> Service B (cert B)
Client ──HTTPS──> Service C (cert C)
Problem: Certificate renewal across all services

With APIM:
Client ──HTTPS──> APIM (ONE certificate, from Key Vault)
                    │
                    ├──HTTP──> Service A (no cert needed)
                    ├──HTTP──> Service B (no cert needed)
                    └──HTTP──> Service C (no cert needed)

Certificate renewal:
Key Vault auto-renews → APIM picks up new cert → Done
No service redeployment needed!
```

---

## API Versioning ⭐⭐⭐

```
APIM Versioning Schemes:
├── URL Path: /api/v1/orders, /api/v2/orders
├── Query String: /api/orders?api-version=2024-01-01
└── Header: Api-Version: v2

Multiple versions coexist:
API: Orders API
├── v1 (deprecated, still serving old clients)
│   └── Backend: order-service-v1
├── v2 (current)
│   └── Backend: order-service-v2
└── v3 (preview)
    └── Backend: order-service-v3
```

---

## APIM + Microservices Architecture ⭐⭐⭐

```
Internet
    │
    ▼
Azure Front Door (Global CDN + WAF)
    │
    ▼
Azure API Management (Regional)
    │
    ├── Authentication (Entra ID JWT validation)
    ├── Rate limiting (per subscription/user)
    ├── API versioning
    ├── Response caching
    ├── Logging → Application Insights
    │
    ├── /api/orders/* → AKS: order-service
    ├── /api/users/* → AKS: user-service
    ├── /api/payments/* → AKS: payment-service
    ├── /api/notifications/* → Azure Functions
    └── /api/reports/* → App Service: report-service
```

### APIM Tiers

| Tier | Use Case | Features |
|------|----------|----------|
| Consumption | Serverless, pay-per-call | No dedicated infrastructure, cold starts |
| Developer | Dev/test | Full features, no SLA |
| Basic | Small workloads | Limited scale |
| Standard | Production | VNet, custom domains, multi-region |
| Premium | Enterprise | VNet injection, multi-region, highest scale |

---

## Interview Questions

### Q: What is Azure API Management and why use it for microservices?
**A:** APIM is an API gateway that sits in front of your microservices. Benefits:
1. **Single entry point**: Clients talk to one endpoint, not individual services
2. **Security**: Centralized JWT validation, OAuth 2.0, API keys
3. **Rate limiting**: Protect backends from overload
4. **TLS centralization**: One certificate at the gateway, no cert management per service
5. **Analytics**: Track usage, errors, latency per API
6. **Versioning**: Multiple API versions without client disruption
7. **Developer portal**: Self-service documentation and API key management
8. **Transformation**: Modify requests/responses without changing backends

### Q: How does APIM help with certificate management?
**A:** APIM terminates TLS at the gateway:
- One public certificate managed at APIM (integrated with Key Vault for auto-renewal)
- Backend services don't need individual public certificates
- Certificate renewal at APIM doesn't require service redeployment
- Internal traffic can be HTTP (simpler) or mTLS (more secure)
- Key Vault auto-renews certificates, APIM picks them up automatically

### Q: Explain APIM policies with a real scenario.
**A:** For a Spring Boot microservices API:
1. **Inbound**: Validate JWT from Entra ID, rate limit to 1000 req/min per subscription, check IP allowlist
2. **Backend**: Route /api/orders to order-service in AKS
3. **Outbound**: Cache GET responses for 5 minutes, remove internal headers (X-Internal-Trace-Id), add CORS headers
4. **On-error**: Return standardized error JSON, log to Application Insights

### Q: APIM vs Application Gateway — do you need both?
**A:** They serve different purposes and often work together:
- **Application Gateway**: Layer 7 load balancer + WAF. Distributes traffic, protects against OWASP attacks.
- **APIM**: API management platform. Authentication, rate limiting, transformation, developer portal, analytics.

Common pattern:
```
Internet → Application Gateway (WAF) → APIM → Backend Services
```
App Gateway handles WAF/DDoS protection. APIM handles API logic/policies. Both can terminate TLS.

### Q: How do you secure APIs in APIM?
**A:** Multiple layers:
1. **Subscription keys**: Required API key per client (Product-level)
2. **JWT validation**: Validate Entra ID tokens (OAuth 2.0)
3. **IP filtering**: Allow only known client IPs
4. **Rate limiting**: Per subscription, per IP, or custom key
5. **mTLS**: Client certificate validation
6. **CORS**: Control which origins can call APIs
7. **VNet**: APIM in VNet, backends only accessible internally
