# 39. CORS

---

## Theory

CORS (Cross-Origin Resource Sharing) is a browser security mechanism that blocks requests from one origin to another unless the server explicitly allows it.

### What is Same-Origin Policy?

```
Same origin = same protocol + domain + port

http://localhost:4200  →  http://localhost:8080  ❌ Different port = CORS
https://myapp.com     →  https://api.myapp.com  ❌ Different subdomain = CORS
https://myapp.com     →  https://myapp.com/api  ✅ Same origin = no CORS
```

### How CORS Works

```
Simple Request (GET, POST with simple headers):
  Browser sends request with Origin header
  Server responds with Access-Control-Allow-Origin
  Browser allows/blocks based on response headers

Preflight Request (PUT, DELETE, custom headers, JSON content-type):
  1. Browser sends OPTIONS request (preflight)
     → Origin: http://localhost:4200
     → Access-Control-Request-Method: POST
     → Access-Control-Request-Headers: Content-Type, Authorization
  
  2. Server responds with allowed methods/headers
     → Access-Control-Allow-Origin: http://localhost:4200
     → Access-Control-Allow-Methods: GET, POST, PUT, DELETE
     → Access-Control-Allow-Headers: Content-Type, Authorization
     → Access-Control-Max-Age: 3600
  
  3. If allowed → Browser sends actual request
     If denied → Browser blocks, Angular gets error
```

### Angular + Spring Boot CORS Solutions

**Solution 1: Angular Proxy (Development Only)**

```json
// proxy.conf.json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```
```bash
# angular.json → serve → options → proxyConfig: "proxy.conf.json"
ng serve  # /api requests proxied to :8080 — no CORS!
```

**Solution 2: Spring Boot CORS Configuration (Production)**

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("https://myapp.com", "http://localhost:4200")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600); // Cache preflight for 1 hour
            }
        };
    }
}
```

**Solution 3: Spring Security CORS (When Security is enabled)**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigSource()))
            .csrf(csrf -> csrf.disable())
            // ... other config
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://myapp.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

**Solution 4: Same Origin via Nginx (Production — Recommended)**

```nginx
server {
    listen 80;
    
    # Serve Angular static files
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
    
    # Proxy API to Spring Boot — same origin, NO CORS needed
    location /api/ {
        proxy_pass http://spring-boot-service:8080;
    }
}
```

---

## Diagram

```
CORS Preflight Flow:

Angular (localhost:4200)              Spring Boot (localhost:8080)
        │                                      │
        │─── OPTIONS /api/users ──────────────→│
        │    Origin: http://localhost:4200      │
        │    Access-Control-Request-Method: POST│
        │    Access-Control-Request-Headers:    │
        │      Authorization, Content-Type     │
        │                                      │
        │←── 200 OK ──────────────────────────│
        │    Access-Control-Allow-Origin: *     │
        │    Access-Control-Allow-Methods:      │
        │      GET, POST, PUT, DELETE          │
        │    Access-Control-Allow-Headers:      │
        │      Authorization, Content-Type     │
        │    Access-Control-Max-Age: 3600      │
        │                                      │
        │─── POST /api/users ─────────────────→│
        │    Origin: http://localhost:4200      │
        │    Authorization: Bearer eyJ...      │
        │    Content-Type: application/json    │
        │    { "name": "John" }                │
        │                                      │
        │←── 201 Created ─────────────────────│
        │    { "id": 1, "name": "John" }      │
```

---

## Interview Questions and Answers

**Q1: What is CORS and why does it exist?**
> CORS is a browser security mechanism that restricts HTTP requests across different origins. It exists to prevent malicious websites from making requests to other sites using the user's cookies/session (CSRF attacks). Without CORS, any website could call your bank's API using your authenticated session.

**Q2: How do you handle CORS between Angular and Spring Boot?**
> Development: Angular proxy (proxy.conf.json proxies /api to backend — same origin, no CORS). Production: either configure CORS headers on Spring Boot (allowedOrigins, allowedMethods, allowedHeaders), or serve both from same origin via Nginx reverse proxy (recommended — eliminates CORS entirely).

**Q3: What is a preflight request?**
> An OPTIONS request the browser automatically sends before non-simple requests (PUT, DELETE, requests with custom headers like Authorization, or Content-Type: application/json). It asks the server "are you okay with this method and these headers from this origin?" If server says yes, browser sends the actual request.

**Q4: Why is the Nginx same-origin approach preferred in production?**
> It eliminates CORS entirely — both Angular and API are served from the same domain/port. No preflight requests (faster), no CORS misconfiguration risks, simpler security model. Also enables secure httpOnly cookies for authentication (same-origin required for cookies).

---

## Common Mistakes

1. **Setting `allowedOrigins("*")` with `allowCredentials(true)`** — browsers reject this combination.
2. **Forgetting to enable CORS in Spring Security** — security filters run before MVC, blocking CORS.
3. **Not handling OPTIONS method** in Spring Security (must permit all OPTIONS requests).
4. **Using proxy in production** — proxy.conf.json is dev-only (ng serve).

---

## Best Practices

1. **Development**: Use Angular proxy — zero CORS configuration needed.
2. **Production**: Same-origin via Nginx (preferred) or explicit CORS config.
3. **Never use `*`** with credentials — specify exact origins.
4. **Set `maxAge`** to cache preflight responses (reduces OPTIONS requests).
5. **Configure CORS in Spring Security** when security is enabled (not just MVC).
6. **Restrict origins** to your specific frontend domains only.

---

## Related Topics

- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
- → [38. Angular + Spring Boot Integration](./38-angular-spring-boot.md)
- → [40. Security](./40-security.md)
