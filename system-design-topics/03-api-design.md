# API Design

## REST API Design

### Theory
- REST (Representational State Transfer) is an architectural style for designing networked applications
- Based on resources identified by URIs, manipulated via HTTP methods
- Stateless: Each request contains all information needed to process it

### REST Constraints
1. **Client-Server**: Separation of concerns
2. **Stateless**: No session state on server between requests
3. **Cacheable**: Responses must define themselves as cacheable or not
4. **Uniform Interface**: Consistent resource identification and manipulation
5. **Layered System**: Client doesn't know if it's talking directly to server
6. **Code on Demand** (optional): Server can send executable code

---

## URI Design

### Principles
- Use nouns, not verbs: `/users` not `/getUsers`
- Use plural nouns: `/users` not `/user`
- Use lowercase: `/users/orders` not `/Users/Orders`
- Use hyphens for readability: `/order-items` not `/orderItems`
- Represent hierarchy: `/users/{id}/orders/{orderId}`
- No trailing slashes: `/users` not `/users/`

### Good vs Bad URIs

| ✗ Bad | ✓ Good |
|-------|--------|
| `GET /getUser?id=1` | `GET /users/1` |
| `POST /createUser` | `POST /users` |
| `POST /deleteUser/1` | `DELETE /users/1` |
| `GET /getUserOrders/1` | `GET /users/1/orders` |
| `POST /updateUser` | `PUT /users/1` |

### Resource Mapping
```
Collection: /users          → GET (list), POST (create)
Instance:   /users/{id}     → GET (read), PUT (replace), PATCH (update), DELETE (remove)
Sub-resource: /users/{id}/orders → GET (list user's orders)
```

---

## Request/Response Structure

### Standard Request
```json
POST /api/v1/users HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

{
  "name": "John Doe",
  "email": "john@example.com",
  "role": "USER"
}
```

### Standard Success Response
```json
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/v1/users/123

{
  "id": 123,
  "name": "John Doe",
  "email": "john@example.com",
  "role": "USER",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Standard Error Response
```json
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request data",
    "details": [
      {
        "field": "email",
        "message": "Invalid email format"
      }
    ]
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/users"
}
```

---

## Pagination

### Offset-Based Pagination
```
GET /api/users?page=2&size=20

Response:
{
  "data": [...],
  "pagination": {
    "page": 2,
    "size": 20,
    "totalElements": 156,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": true
  }
}
```

### Cursor-Based Pagination
```
GET /api/users?cursor=eyJpZCI6MTIzfQ&limit=20

Response:
{
  "data": [...],
  "pagination": {
    "nextCursor": "eyJpZCI6MTQzfQ",
    "hasNext": true,
    "limit": 20
  }
}
```

### Offset vs Cursor

| Aspect | Offset-Based | Cursor-Based |
|--------|-------------|--------------|
| Jump to page | ✓ Yes | ✗ No (sequential only) |
| Performance | Degrades with large offsets | Consistent |
| Data consistency | Can miss/duplicate on inserts | Stable |
| Use case | Admin panels, small datasets | Feeds, large datasets |
| Implementation | Simple (LIMIT/OFFSET) | More complex |

---

## Filtering, Sorting, Searching

### Filtering
```
GET /api/users?role=ADMIN&status=ACTIVE
GET /api/products?price_min=10&price_max=100
GET /api/orders?created_after=2024-01-01
```

### Sorting
```
GET /api/users?sort=name,asc
GET /api/users?sort=createdAt,desc&sort=name,asc
GET /api/products?sort_by=price&order=desc
```

### Searching
```
GET /api/users?search=john
GET /api/products?q=laptop&category=electronics
```

---

## API Versioning

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| URI path | `/api/v1/users` | Clear, easy to cache | URL pollution |
| Query param | `/api/users?version=1` | Easy to add | Can break caching |
| Header | `Accept: application/vnd.api.v1+json` | Clean URLs | Hidden, hard to test |
| Content negotiation | `Accept: application/json; version=1` | Standard HTTP | Complex |

**Recommendation**: URI path versioning (`/api/v1/`) — most common and explicit.

---

## Idempotency

### Theory
- An operation is idempotent if calling it multiple times produces the same result as calling it once
- Critical for retry safety and distributed systems

| Method | Idempotent? | Why |
|--------|-------------|-----|
| GET | ✓ | Reading doesn't change state |
| PUT | ✓ | Replaces entire resource (same result) |
| DELETE | ✓ | Deleting already-deleted = still deleted |
| POST | ✗ | Creates new resource each time |
| PATCH | ✗ | Depends on operation (increment is not idempotent) |

### Implementing Idempotency for POST
```
Client generates idempotency key:
POST /api/payments
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

Server:
1. Check if key exists in idempotency store
2. If yes → return stored response (don't process again)
3. If no → process request, store response with key
```

---

## API Gateway

### Theory
- Single entry point for all client requests
- Routes requests to appropriate microservices
- Cross-cutting concerns handled centrally

### Diagram
```
┌─────────┐     ┌─────────────┐     ┌──────────────┐
│ Web App │────→│             │────→│ User Service │
└─────────┘     │             │     └──────────────┘
                │             │
┌─────────┐     │   API       │     ┌──────────────┐
│Mobile App│───→│  Gateway    │────→│Order Service │
└─────────┘     │             │     └──────────────┘
                │             │
┌─────────┐     │             │     ┌──────────────┐
│ Partner │────→│             │────→│Payment Service│
└─────────┘     └─────────────┘     └──────────────┘
```

### API Gateway Responsibilities
- **Routing**: Direct requests to correct service
- **Authentication/Authorization**: Verify tokens centrally
- **Rate Limiting**: Prevent abuse
- **Load Balancing**: Distribute across instances
- **Circuit Breaking**: Prevent cascade failures
- **Request/Response Transformation**: Protocol translation
- **Caching**: Cache common responses
- **Logging/Monitoring**: Centralized observability
- **SSL Termination**: Handle HTTPS centrally

### Popular API Gateways
| Gateway | Type | Use Case |
|---------|------|----------|
| Kong | Open source | General purpose |
| AWS API Gateway | Managed | AWS ecosystem |
| Nginx | Open source | High performance |
| Spring Cloud Gateway | Java | Spring ecosystem |
| Envoy | Open source | Service mesh |

---

## Rate Limiting

### Theory
- Controls the rate of requests a client can make
- Protects against abuse, DDoS, ensures fair usage

### Algorithms

| Algorithm | Description | Pros | Cons |
|-----------|-------------|------|------|
| Token Bucket | Tokens added at fixed rate, consumed per request | Allows bursts, smooth | Memory per user |
| Leaky Bucket | Requests queued, processed at fixed rate | Smooth output | No burst handling |
| Fixed Window | Counter per time window | Simple | Burst at window edges |
| Sliding Window Log | Log timestamps, count in window | Accurate | Memory intensive |
| Sliding Window Counter | Weighted count across windows | Balanced | Slight approximation |

### Token Bucket Algorithm
```
Configuration: capacity=10, refill_rate=1/second

Bucket: [●●●●●●●●●●] (10 tokens)

Request arrives:
  If tokens > 0 → allow, tokens--
  If tokens = 0 → reject (429 Too Many Requests)

Every second: tokens = min(tokens + 1, capacity)
```

### Rate Limit Headers
```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 67
X-RateLimit-Reset: 1640995200

HTTP/1.1 429 Too Many Requests
Retry-After: 30
```

---

## Authentication vs Authorization

| Aspect | Authentication (AuthN) | Authorization (AuthZ) |
|--------|----------------------|---------------------|
| Question | "Who are you?" | "What can you do?" |
| Verifies | Identity | Permissions |
| Methods | Password, OAuth, SSO, biometric | RBAC, ABAC, ACL |
| HTTP code | 401 Unauthorized | 403 Forbidden |
| When | Before authorization | After authentication |

### Common Auth Patterns
| Pattern | Stateful? | Use Case |
|---------|-----------|----------|
| Session-based | Yes (server stores session) | Traditional web apps |
| JWT (Token-based) | No (token carries claims) | APIs, microservices, SPAs |
| API Key | No | Server-to-server, public APIs |
| OAuth 2.0 | Depends | Third-party access delegation |
| mTLS | No | Service-to-service (zero trust) |

### JWT Structure
```
Header.Payload.Signature

Header:  {"alg": "HS256", "typ": "JWT"}
Payload: {"sub": "123", "role": "ADMIN", "exp": 1640995200}
Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
```

---

## Interview Questions

**Q: How would you design a REST API for a blog platform?**
> Resources: posts, users, comments, tags
> ```
> GET    /api/v1/posts                    → List posts (paginated)
> POST   /api/v1/posts                    → Create post
> GET    /api/v1/posts/{id}               → Get post
> PUT    /api/v1/posts/{id}               → Update post
> DELETE /api/v1/posts/{id}               → Delete post
> GET    /api/v1/posts/{id}/comments      → List comments on post
> POST   /api/v1/posts/{id}/comments      → Add comment
> GET    /api/v1/users/{id}/posts         → List posts by user
> ```

**Q: How do you handle API versioning when breaking changes are needed?**
> 1. Use URI versioning (/v1/, /v2/)
> 2. Maintain old version for deprecation period
> 3. Communicate changes via changelog and deprecation headers
> 4. Use feature flags for gradual migration
> 5. Version the contract (OpenAPI spec), not the implementation

**Q: When would you use cursor-based pagination over offset?**
> Use cursor-based when: dataset is large, data changes frequently (inserts/deletes), infinite scroll UI, performance matters for deep pages. Use offset when: need random page access, dataset is small and stable, traditional page navigation UI.

**Q: How would you implement rate limiting in a distributed system?**
> Use centralized counter (Redis) with sliding window algorithm. Each request: INCR key with TTL. If count > limit, reject. For distributed: use Redis cluster. Consider: per-user, per-IP, per-API-key limits. Handle race conditions with Lua scripts in Redis for atomic operations.

**Q: What's the difference between PUT and PATCH?**
> PUT replaces the entire resource (must send all fields). PATCH applies partial modifications (send only changed fields). PUT is idempotent (same body → same result). PATCH may not be idempotent (e.g., {"op": "increment", "path": "/views"}).

---

## Common Mistakes
- Using verbs in URIs (`/getUsers` instead of `GET /users`)
- Not versioning APIs from the start
- Inconsistent error response format
- Not implementing pagination for list endpoints
- Returning 200 for errors (wrapping errors in success response)
- Not making POST endpoints idempotent when needed (payments!)
- Exposing internal IDs or implementation details in APIs

---

## Best Practices
- Use consistent naming conventions across all endpoints
- Always return appropriate HTTP status codes
- Implement HATEOAS for discoverability (optional, adds complexity)
- Document APIs with OpenAPI/Swagger
- Version from day one
- Use ISO 8601 for dates (`2024-01-15T10:30:00Z`)
- Implement request validation and return detailed error messages
- Use correlation IDs for request tracing
- Design for backward compatibility

---

## Production Considerations
- API documentation (Swagger/OpenAPI) auto-generated and always up-to-date
- Rate limiting per client, per endpoint
- Request/response logging (sanitize sensitive data)
- API analytics (most used endpoints, error rates)
- Contract testing between services
- Deprecation policy with sunset headers
- API health check endpoints
- CORS configuration for browser clients

---

## Related Topics
- Load Balancing
- Authentication (JWT, OAuth)
- gRPC (alternative to REST)
- GraphQL (alternative to REST)
- WebSockets (bidirectional communication)
