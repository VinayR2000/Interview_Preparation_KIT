# 6. Response Handling

## Theory

**ResponseEntity<T>:**
A wrapper representing the entire HTTP response: status code, headers, and body. Gives full control over what the client receives.

**HTTP Status Codes:**

| Code | Meaning | When to Use |
|------|---------|-------------|
| 200 OK | Success | GET, PUT, PATCH success |
| 201 Created | Resource created | POST success |
| 204 No Content | Success, no body | DELETE success |
| 400 Bad Request | Invalid input | Validation failures |
| 401 Unauthorized | Not authenticated | Missing/invalid credentials |
| 403 Forbidden | Not authorized | Insufficient permissions |
| 404 Not Found | Resource doesn't exist | Invalid ID |
| 409 Conflict | State conflict | Duplicate resource |
| 422 Unprocessable Entity | Semantic error | Business rule violation |
| 500 Internal Server Error | Server failure | Unexpected exceptions |

**@ResponseStatus:**
Annotation to set default status code for a controller method or exception class.

---

## Internal Working

```
Controller method returns ResponseEntity<UserResponse>
       ↓
ReturnValueHandler processes return type
       ↓
If ResponseEntity:
  - Extract status code → set on HttpServletResponse
  - Extract headers → add to response
  - Extract body → pass to HttpMessageConverter
       ↓
Jackson ObjectMapper serializes body to JSON
       ↓
Content-Type header set (application/json)
       ↓
Response written to output stream
       ↓
Client receives: Status + Headers + Body
```

---

## Diagram

```
Controller Method Return
         │
         ├── ResponseEntity<T>
         │      ├── HttpStatus (200, 201, 404...)
         │      ├── HttpHeaders (Location, Cache-Control...)
         │      └── Body <T> (DTO object)
         │
         ├── T (plain object)
         │      └── Always 200 OK + body
         │
         └── void
                └── 200 OK, no body

Standard API Response Structure:
┌─────────────────────────────┐
│ HTTP/1.1 201 Created        │ ← Status
│ Location: /api/v1/users/42  │ ← Headers
│ Content-Type: application/json
│                             │
│ {                           │ ← Body
│   "id": 42,                │
│   "name": "Alice",         │
│   "createdAt": "..."       │
│ }                           │
└─────────────────────────────┘
```

---

## Code

```java
// === ResponseEntity usage patterns ===
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    // 200 OK with body
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        ProductResponse product = productService.findById(id);
        return ResponseEntity.ok(product);
    }

    // 201 Created with Location header
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(created);
    }

    // 204 No Content
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // 200 OK with custom headers
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ProductResponse> result = productService.findAll(page, size);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total-Count", String.valueOf(result.getTotalElements()));
        headers.add("X-Total-Pages", String.valueOf(result.getTotalPages()));

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(result.getContent());
    }

    // Conditional response
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        ProductResponse updated = productService.update(id, request);
        return ResponseEntity.ok(updated);
    }
}

// === Standard API Response Wrapper ===
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, LocalDateTime.now());
    }
}

// Usage:
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
    ProductResponse product = productService.findById(id);
    return ResponseEntity.ok(ApiResponse.success(product));
}

// === Error Response Structure ===
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message, Object rejectedValue) {}
}

// === @ResponseStatus on exception ===
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}

// === @ResponseStatus on method ===
@PostMapping
@ResponseStatus(HttpStatus.CREATED)  // Alternative to ResponseEntity
public ProductResponse createProduct(@Valid @RequestBody CreateProductRequest request) {
    return productService.create(request);
}
```

---

## Dry Run

**Scenario**: POST creates a product successfully

```
Request:
POST /api/v1/products
Content-Type: application/json
{"name": "Laptop", "price": 999.99, "category": "ELECTRONICS"}

Processing:
1. Jackson deserializes → CreateProductRequest
2. @Valid passes all constraints
3. productService.create() saves to DB, returns ProductResponse(id=5, ...)
4. ServletUriComponentsBuilder builds URI: /api/v1/products/5
5. ResponseEntity.created(URI).body(product)
6. Status: 201
7. Headers: Location=/api/v1/products/5, Content-Type=application/json
8. Body: {"id":5,"name":"Laptop","price":999.99,...}

Response:
HTTP/1.1 201 Created
Location: /api/v1/products/5
Content-Type: application/json
{"id":5,"name":"Laptop","price":999.99,"category":"ELECTRONICS","createdAt":"2024-01-15T10:30:00"}
```

---

## Complexity

| Operation | Time |
|-----------|------|
| ResponseEntity construction | O(1) |
| Header addition | O(1) per header |
| JSON serialization | O(n) — n = fields |
| URI building | O(1) |

---

## Real Project Usage

```java
// Paginated response with metadata
@GetMapping("/orders")
public ResponseEntity<PagedApiResponse<OrderSummary>> getOrders(Pageable pageable) {
    Page<OrderSummary> page = orderService.findAll(pageable);
    
    PagedApiResponse<OrderSummary> response = new PagedApiResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext()
    );
    
    return ResponseEntity.ok(response);
}

// Cache-Control headers
@GetMapping("/catalog")
public ResponseEntity<List<Product>> getCatalog() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
        .body(catalogService.getAll());
}
```

---

## Interview Questions

1. **What is ResponseEntity and why use it over plain return types?**
   - ResponseEntity wraps status code + headers + body. Use it for full control over HTTP response. Plain return type always gives 200 OK; ResponseEntity lets you specify 201, 204, set Location header, etc.

2. **What is the difference between @ResponseStatus and ResponseEntity?**
   - @ResponseStatus: Fixed status on method/exception class. ResponseEntity: Dynamic status per request. Use @ResponseStatus for simple cases (always 201), ResponseEntity when status varies (200 vs 404).

3. **What HTTP status code should you return for a POST that creates a resource?**
   - 201 Created with a Location header pointing to the new resource URI (`/api/users/42`).

4. **How do you add custom headers to a response?**
   - `ResponseEntity.ok().header("X-Custom-Header", "value").body(data)` or use HttpServletResponse.

5. **How do you build a Location header for a newly created resource?**
   - `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()` — builds full URL relative to current request.

6. **What is the difference between 401 and 403?**
   - 401 Unauthorized: Identity not established (not authenticated — missing/invalid credentials). 403 Forbidden: Identity known but lacks permission (authenticated but not authorized).

7. **When would you use 409 Conflict?**
   - When the request conflicts with current state: duplicate email registration, optimistic lock version mismatch, resource already exists with same unique constraint.

8. **How do you standardize API responses across your application?**
   - Create a generic wrapper class (ApiResponse<T>) with status, message, data, timestamp. Use @RestControllerAdvice to wrap all responses or handle errors consistently.

9. **What is content negotiation?**
   - Client specifies desired format via Accept header (application/json, application/xml). Server selects appropriate HttpMessageConverter. Spring auto-negotiates based on configured converters.

10. **How do you handle 204 No Content properly?**
    - `ResponseEntity.noContent().build()` — no body, no Content-Type header. Used for successful DELETE, PUT that doesn't return updated resource.

---

## Follow-up Questions

1. **After Q1**: "Can you return ResponseEntity<Void>? When?"
   → Yes. For DELETE (204 No Content) or when status/headers matter but no body needed.

2. **After Q6**: "If a JWT is expired, is that 401 or 403?"
   → 401 — the user is no longer authenticated (token invalid).

3. **After Q7**: "Give a real example of 409 Conflict."
   → User tries to create an account with an email that already exists.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|---------------|-----|
| Always returning 200 | Not RESTful, clients can't differentiate | Use proper status codes |
| Returning error details in 200 | Anti-pattern | Use 4xx/5xx codes |
| Missing Location header on 201 | Clients can't find new resource | Use .created(uri) |
| Body on 204 response | Spec says no body | Use .noContent().build() |
| Leaking stack traces in error response | Security risk | Custom error handler |

---

## Best Practices

1. **Always use ResponseEntity** for full control
2. **201 + Location header** for POST creating resources
3. **204 No Content** for DELETE (no body needed)
4. **Consistent response structure** across all endpoints
5. **Proper error response format** with status, message, timestamp
6. **Use HTTP caching headers** where appropriate
7. **Don't expose internal error details** to clients

---

## Production Considerations

- **Response compression**: Enable GZIP for large payloads
- **Cache headers**: Set appropriate caching for static/semi-static data
- **Response size limits**: Paginate to avoid massive payloads
- **Security headers**: X-Content-Type-Options, X-Frame-Options
- **CORS headers**: Properly configured for frontend clients
- **Rate limit headers**: X-RateLimit-Remaining, X-RateLimit-Reset

---

## Related Topics

- → [5. Spring Boot REST API](#) (controller layer)
- → [7. Exception Handling](#) (error responses)
- → [8. Validation](#) (400 Bad Request responses)
- → [15. Spring Security](#) (401/403 responses)
