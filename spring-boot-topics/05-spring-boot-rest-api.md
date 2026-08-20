# 5. Spring Boot REST API

## Theory

**HTTP Methods:**

| Method | Purpose | Idempotent | Safe | Body |
|--------|---------|-----------|------|------|
| GET | Retrieve resource | ✅ | ✅ | No |
| POST | Create resource | ❌ | ❌ | Yes |
| PUT | Replace resource | ✅ | ❌ | Yes |
| PATCH | Partial update | ❌ | ❌ | Yes |
| DELETE | Remove resource | ✅ | ❌ | No |
| HEAD | Headers only (no body) | ✅ | ✅ | No |
| OPTIONS | Supported methods | ✅ | ✅ | No |

**Controller Annotations:**
- `@RestController` = `@Controller` + `@ResponseBody` (auto-serializes to JSON)
- `@RequestMapping("/api/v1")` — Base path for all methods
- `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`

**Parameter Annotations:**

| Annotation | Source | Example |
|-----------|--------|---------|
| @PathVariable | URL path segment | `/users/{id}` |
| @RequestParam | Query string | `/users?status=active` |
| @RequestBody | Request body (JSON) | POST/PUT payload |
| @RequestHeader | HTTP header | `Authorization` |
| @CookieValue | Cookie | `sessionId` |
| @RequestPart | Multipart file | File uploads |

---

## Internal Working

```
HTTP Request arrives at Tomcat
       ↓
DispatcherServlet receives request
       ↓
HandlerMapping finds matching controller method
  (URL pattern + HTTP method → @GetMapping("/users/{id}"))
       ↓
HandlerAdapter invokes controller method
       ↓
Argument Resolvers:
  - PathVariableMethodArgumentResolver → @PathVariable
  - RequestParamMethodArgumentResolver → @RequestParam
  - RequestResponseBodyMethodProcessor → @RequestBody (Jackson)
       ↓
Controller method executes
       ↓
Return value handled:
  - HttpMessageConverter (Jackson) → JSON serialization
  - ResponseEntity → status + headers + body
       ↓
Response sent to client
```

**Jackson Serialization Flow:**
```
Java Object → ObjectMapper.writeValueAsString()
  → Field access (or getter) 
  → JSON string
  → Written to HTTP response body
  → Content-Type: application/json
```

---

## Diagram

```
Client (Browser/Postman)
       │
       │  GET /api/v1/users/42
       ▼
┌─────────────────────────────┐
│     Embedded Tomcat          │
│  ┌────────────────────────┐ │
│  │   DispatcherServlet     │ │
│  │         │               │ │
│  │         ▼               │ │
│  │   HandlerMapping        │ │
│  │         │               │ │
│  │         ▼               │ │
│  │  UserController         │ │
│  │  @GetMapping("/{id}")   │ │
│  │         │               │ │
│  │         ▼               │ │
│  │  @PathVariable id = 42  │ │
│  │         │               │ │
│  │         ▼               │ │
│  │  UserService.findById() │ │
│  │         │               │ │
│  │         ▼               │ │
│  │  Jackson → JSON         │ │
│  └────────────────────────┘ │
└─────────────────────────────┘
       │
       │  200 OK
       │  {"id":42,"name":"John"}
       ▼
     Client

URL Patterns:
/users/{id}       → @PathVariable Long id
/users?status=active&page=0 → @RequestParam String status, @RequestParam int page
```

---

## Code

```java
// === Complete REST Controller ===
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/v1/users
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        Page<UserResponse> users = userService.findAll(page, size, status);
        return ResponseEntity.ok(users.getContent());
    }

    // GET /api/v1/users/42
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.findById(id);
        return ResponseEntity.ok(user);
    }

    // POST /api/v1/users
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse created = userService.create(request);
        URI location = URI.create("/api/v1/users/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/v1/users/42
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        UserResponse updated = userService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    // PATCH /api/v1/users/42
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> partialUpdate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {

        UserResponse updated = userService.partialUpdate(id, updates);
        return ResponseEntity.ok(updated);
    }

    // DELETE /api/v1/users/42
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/users/search?name=John&city=NYC
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam String name,
            @RequestParam(required = false) String city) {

        List<UserResponse> results = userService.search(name, city);
        return ResponseEntity.ok(results);
    }
}

// === DTOs ===
public record CreateUserRequest(
        @NotBlank String name,
        @Email String email,
        @NotBlank String password,
        @Size(min = 10, max = 15) String phone
) {}

public record UpdateUserRequest(
        @NotBlank String name,
        @Email String email,
        String phone
) {}

public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {}

// === Headers and Cookies ===
@RestController
@RequestMapping("/api/v1")
public class HeaderDemoController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getInfo(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @CookieValue(value = "sessionId", required = false) String sessionId) {

        return ResponseEntity.ok(Map.of(
                "auth", authHeader,
                "requestId", requestId != null ? requestId : "none",
                "session", sessionId != null ? sessionId : "none"
        ));
    }
}

// === File Upload ===
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") FileMetadata metadata) {

        String savedPath = fileService.store(file, metadata);
        return ResponseEntity.ok(savedPath);
    }
}
```

---

## Dry Run

**Scenario**: POST /api/v1/users with JSON body

```
Request:
POST /api/v1/users
Content-Type: application/json
{
  "name": "Alice",
  "email": "alice@example.com",
  "password": "secret123",
  "phone": "1234567890"
}

Processing:
1. Tomcat receives request on port 8080
2. DispatcherServlet matches: POST /api/v1/users → UserController.createUser()
3. @RequestBody triggers Jackson deserialization:
   JSON → CreateUserRequest("Alice", "alice@example.com", "secret123", "1234567890")
4. @Valid triggers Bean Validation:
   - @NotBlank name: "Alice" ✓
   - @Email email: "alice@example.com" ✓
   - @NotBlank password: "secret123" ✓
   - @Size(10,15) phone: "1234567890" (10 chars) ✓
5. userService.create(request) called
   - Maps to entity, saves to DB, returns UserResponse
6. ResponseEntity.created(location).body(created)
7. Response:
   HTTP 201 Created
   Location: /api/v1/users/1
   {
     "id": 1,
     "name": "Alice",
     "email": "alice@example.com",
     "phone": "1234567890",
     "createdAt": "2024-01-15T10:30:00"
   }
```

---

## Complexity

| Operation | Time |
|-----------|------|
| URL pattern matching | O(n) — n = registered mappings (cached after first request) |
| JSON deserialization | O(n) — n = fields in object |
| JSON serialization | O(n) — n = fields in object |
| Validation | O(n) — n = constraints on object |
| Path variable extraction | O(1) — regex group capture |
| Request param binding | O(1) — query string parsing |

**Throughput**: A simple REST endpoint handles 5,000-50,000 req/sec depending on business logic.

---

## Real Project Usage

```java
// Versioned API with pagination
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping
    public ResponseEntity<PagedResponse<OrderSummary>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {

        PagedResponse<OrderSummary> response = orderService.findOrders(
                page, size, sort, status, from, to);

        return ResponseEntity.ok(response);
    }
}

// Custom response wrapper
public record PagedResponse<T>(
        List<T> data,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
```

---

## Interview Questions

1. **What is the difference between @Controller and @RestController?**
   - @Controller returns view names (for MVC/Thymeleaf). @RestController = @Controller + @ResponseBody; all methods automatically serialize return values to JSON/XML. Use @RestController for REST APIs.

2. **What is the difference between @PathVariable and @RequestParam?**
   - @PathVariable: Extracts from URL path `/users/{id}` — mandatory, part of resource identity. @RequestParam: Extracts from query string `/users?status=active` — optional filtering/pagination. Use @PathVariable for resource IDs, @RequestParam for filters.

3. **How does Spring Boot handle JSON serialization/deserialization?**
   - Jackson ObjectMapper auto-configured. @RequestBody: Jackson deserializes JSON → Java object. @ResponseBody/@RestController: Jackson serializes Java object → JSON. Customizable via ObjectMapper @Bean or application properties.

4. **What is the role of DispatcherServlet?**
   - Front controller pattern. Receives ALL HTTP requests, maps to handler (controller method) via HandlerMapping, invokes it via HandlerAdapter, resolves view/response. Single entry point for the web layer.

5. **How do you handle file uploads in Spring Boot?**
   - Use @RequestPart or @RequestParam MultipartFile. Configure limits: `spring.servlet.multipart.max-file-size`. Store to filesystem/S3. Return file metadata in response.

6. **What is the difference between PUT and PATCH?**
   - PUT: Full replacement of resource (client sends complete object). PATCH: Partial update (client sends only changed fields). PUT is idempotent; PATCH may not be. Different DTOs recommended.

7. **How do you implement API versioning?**
   - URI: `/api/v1/users` (most common, easy), Header: `Accept: application/vnd.api.v1+json`, Query param: `?version=1`. URI versioning is simplest and most widely adopted.

8. **What happens if @RequestParam is missing and not required=false?**
   - Spring throws MissingServletRequestParameterException → 400 Bad Request. Fix: `@RequestParam(required = false)` or `@RequestParam(defaultValue = "10")`.

9. **How do you return different HTTP status codes?**
   - ResponseEntity.status(HttpStatus.CREATED).body(obj), @ResponseStatus(HttpStatus.NO_CONTENT) on method, throw exception with @ResponseStatus. ResponseEntity gives most flexibility.

10. **What is content negotiation in Spring MVC?**
    - Client specifies desired response format via Accept header (application/json, application/xml). Spring selects appropriate HttpMessageConverter. Configurable via `spring.mvc.contentnegotiation`.

---

## Follow-up Questions

1. **After Q1**: "What if you want to return a view AND JSON from the same controller?"
   → Use @Controller with @ResponseBody on specific methods, or separate controllers.

2. **After Q4**: "How does DispatcherServlet handle multiple matching patterns?"
   → Most specific pattern wins. /users/active beats /users/{id}.

3. **After Q6**: "Should PATCH accept the same DTO as PUT?"
   → No. PATCH should accept partial data (Map or dedicated PatchDTO with nullable fields).

4. **After Q7**: "URI versioning vs Header versioning — which is better?"
   → URI (/api/v1/) is most common and easiest to understand. Header versioning is cleaner but harder to test/document.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Using @RequestBody for GET requests | GET shouldn't have body (some clients don't send it) | Use @RequestParam |
| Not using @Valid with @RequestBody | Validation doesn't trigger | Always add @Valid |
| Returning entity directly | Exposes DB structure, circular refs | Use DTOs |
| Not setting proper HTTP status | 200 for everything is not RESTful | Use ResponseEntity |
| @PathVariable name mismatch | Binds by name; mismatch = null | `@PathVariable("id") Long userId` |
| PUT for partial updates | PUT means full replacement | Use PATCH |
| Not handling optional params | NPE in service layer | Use `required = false` + null checks |

---

## Best Practices

1. **Use DTOs** — never expose entities directly
2. **Return proper HTTP status codes** — 201 for create, 204 for delete
3. **Use ResponseEntity** — full control over response
4. **Validate all input** — @Valid on @RequestBody, constraints on @RequestParam
5. **Version your APIs** — `/api/v1/`, `/api/v2/`
6. **Use plural nouns** — `/users`, `/orders` (not `/user`, `/getOrder`)
7. **Pagination for collections** — never return unbounded lists
8. **HATEOAS for discoverability** (optional but good practice)
9. **Consistent response structure** — wrap in standard envelope
10. **Document with OpenAPI/Swagger** — auto-generate docs

---

## Production Considerations

- **Rate limiting**: Prevent abuse with request throttling
- **Request size limits**: `spring.servlet.multipart.max-file-size=10MB`
- **Timeout**: Configure request timeouts to prevent thread exhaustion
- **CORS**: Configure allowed origins for browser clients
- **Compression**: Enable GZIP for responses > 1KB
- **Idempotency**: POST with idempotency keys for critical operations
- **API documentation**: OpenAPI + Swagger UI for consumers
- **Versioning strategy**: Plan for backward compatibility

---

## Related Topics

- → [6. Response Handling](#) (ResponseEntity, status codes)
- → [7. Exception Handling](#) (error responses)
- → [8. Validation](#) (input validation)
- → [15. Spring Security](#) (securing endpoints)
- → [27. REST API Testing](#) (testing controllers)
