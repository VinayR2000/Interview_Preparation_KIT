# 39. API Documentation

## Theory

API documentation describes your REST endpoints, request/response schemas, authentication requirements, and error responses. Spring Boot integrates with OpenAPI (Swagger) for automatic documentation generation.

### Key Tools:
- **OpenAPI 3.0**: Specification standard for describing REST APIs
- **springdoc-openapi**: Library that auto-generates OpenAPI spec from Spring controllers
- **Swagger UI**: Interactive browser-based API explorer
- **Spring REST Docs**: Test-driven documentation (docs generated from tests)

### What to Document:
- Endpoint URLs, HTTP methods
- Request parameters, headers, body schemas
- Response schemas and status codes
- Authentication requirements
- Error response formats
- API versioning

---

## Internal Working

```
Application starts with springdoc-openapi dependency
       ↓
OpenAPI auto-configuration activates
       ↓
Scans all @RestController classes
       ↓
For each @RequestMapping method:
  - Extracts URL pattern, HTTP method
  - Analyzes @RequestBody → generates schema from DTO class
  - Analyzes @PathVariable, @RequestParam → generates parameters
  - Analyzes ResponseEntity<T> → generates response schema
  - Reads @Operation, @ApiResponse annotations for metadata
       ↓
Generates OpenAPI 3.0 JSON/YAML specification
       ↓
Exposes at: /v3/api-docs
Swagger UI at: /swagger-ui.html
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                  API DOCUMENTATION FLOW                        │
│                                                               │
│  ┌──────────────────┐      ┌──────────────────────────────┐ │
│  │ @RestController   │      │ springdoc-openapi            │ │
│  │ @GetMapping       │ ───→ │ Scans controllers            │ │
│  │ @PostMapping      │      │ Generates OpenAPI spec        │ │
│  │ @RequestBody      │      │ Serves Swagger UI             │ │
│  └──────────────────┘      └──────────────┬───────────────┘ │
│                                             │                  │
│                             ┌───────────────┴──────────────┐  │
│                             │ /v3/api-docs (JSON spec)       │  │
│                             │ /swagger-ui.html (interactive) │  │
│                             └───────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## Code

### Dependencies:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### Configuration:

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operationsSorter: method
    tagsSorter: alpha
  info:
    title: Order Service API
    version: 2.1.0
    description: API for managing orders
    contact:
      name: Backend Team
      email: backend@example.com
```

### OpenAPI Configuration:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Order Service API")
                .version("2.1.0")
                .description("REST API for order management")
                .contact(new Contact()
                    .name("Backend Team")
                    .email("backend@example.com")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new Components()
                .addSecuritySchemes("Bearer Authentication",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Enter JWT token")));
    }
}
```

### Annotated Controller:

```java
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order management endpoints")
public class OrderController {

    @Operation(
        summary = "Create a new order",
        description = "Creates an order and initiates payment processing",
        responses = {
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.create(request));
    }

    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Order ID", example = "42")
            @PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id));
    }

    @Operation(summary = "Search orders with filters")
    @GetMapping
    public ResponseEntity<Page<OrderSummary>> searchOrders(
            @Parameter(description = "Order status filter")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.search(status, page, size));
    }
}
```

### DTO with Schema Annotations:

```java
@Schema(description = "Request to create a new order")
public record CreateOrderRequest(
    @Schema(description = "Customer ID", example = "12345", required = true)
    @NotNull Long customerId,

    @Schema(description = "Order items", minLength = 1)
    @NotEmpty List<@Valid OrderItemRequest> items,

    @Schema(description = "Shipping address")
    @Valid @NotNull AddressRequest shippingAddress,

    @Schema(description = "Optional order notes", example = "Please deliver after 5 PM")
    @Size(max = 500) String notes
) {}

@Schema(description = "Order item in the request")
public record OrderItemRequest(
    @Schema(description = "Product ID", example = "PROD-001")
    @NotBlank String productId,

    @Schema(description = "Quantity", example = "2", minimum = "1")
    @Positive int quantity
) {}
```

### Grouping APIs:

```java
@Configuration
public class OpenApiGroupConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/v1/**")
            .pathsToExclude("/api/v1/admin/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/v1/admin/**")
            .build();
    }
}
```

---

## Dry Run

### Swagger UI Interaction:

```
1. Developer opens: http://localhost:8080/swagger-ui.html

2. Sees grouped endpoints:
   Orders:
     POST /api/v1/orders        - Create a new order
     GET  /api/v1/orders/{id}   - Get order by ID
     GET  /api/v1/orders        - Search orders

3. Clicks "Try it out" on POST /api/v1/orders

4. Swagger shows request body schema with examples:
   {
     "customerId": 12345,
     "items": [{"productId": "PROD-001", "quantity": 2}],
     "shippingAddress": {...},
     "notes": "Please deliver after 5 PM"
   }

5. User fills in data, clicks "Execute"

6. Shows response: 201 Created with OrderResponse body
   Plus curl command for reproduction
```

---

## Complexity

| Operation | Time |
|-----------|------|
| OpenAPI spec generation (startup) | ~100-500ms |
| Swagger UI load | ~1-2s (fetches spec + renders) |
| Per-endpoint schema generation | O(1) (cached) |
| Impact on production performance | Zero (spec cached) |

---

## Real Project Usage

### Securing Swagger in Production:

```java
@Bean
public SecurityFilterChain swaggerSecurity(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                .hasRole("DEVELOPER")  // Only devs can access docs
            .anyRequest().authenticated()
        )
        .build();
}
```

```yaml
# Disable Swagger in production
springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
```

---

## Interview Questions

1. **How do you document REST APIs in Spring Boot?**
   - Use springdoc-openapi library. Auto-generates OpenAPI 3.0 spec from controllers. Provides Swagger UI for interactive testing. Enhance with @Operation, @Schema, @Parameter annotations.

2. **What is the difference between OpenAPI and Swagger?**
   - OpenAPI: The specification standard (version 3.0+). Swagger: The toolset (Swagger UI, Swagger Editor, Swagger Codegen). OpenAPI defines the format; Swagger tools implement it.

3. **How to add authentication documentation?**
   - Configure SecurityScheme in OpenAPI bean (Bearer JWT, API Key, OAuth2). Add @SecurityRequirement on controllers. Swagger UI shows "Authorize" button for entering credentials.

4. **Spring REST Docs vs springdoc-openapi?**
   - springdoc-openapi: Annotation-driven, auto-generated, easier setup. REST Docs: Test-driven (docs from tests), always accurate, more effort. REST Docs guarantees docs match reality (test must pass).

5. **How to handle API versioning in documentation?**
   - Group APIs by version using GroupedOpenApi. Each version has its own spec. Or use single spec with all versions clearly tagged and deprecated endpoints marked.

---

## Follow-up Questions

1. How to generate client SDKs from OpenAPI spec?
   - Use OpenAPI Generator: `openapi-generator generate -i api-docs.json -g java -o ./client`. Generates typed client code. Supports Java, TypeScript, Python, etc. Automate in CI/CD pipeline.

2. How to keep documentation in sync with code?
   - springdoc-openapi auto-generates from code (always in sync). Spring REST Docs generates from tests (tests fail if API changes). Both approaches prevent documentation drift.

3. How to document error responses consistently?
   - Define ErrorResponse schema. Add @ApiResponse(responseCode="4xx") with content pointing to ErrorResponse. Document error codes in API overview. Use @ControllerAdvice for consistent error format.

4. Should you expose Swagger UI in production?
   - Generally no (security risk — exposes API surface). Options: disable in prod, protect behind auth, only expose on internal network/VPN. Or use separate documentation portal (Redoc, Stoplight).

5. How to add examples to API documentation?
   - Use `example` attribute in @Schema annotations. Add @ExampleObject in @Content for complex examples. Create sample request/response JSON in description fields.

---

## Common Mistakes

1. **Not documenting error responses** - Only happy path documented
2. **Exposing Swagger in production without auth** - Security risk
3. **Not adding examples** - Consumers guess at correct values
4. **Ignoring @Schema annotations on DTOs** - Auto-generated descriptions are cryptic
5. **Not versioning the API documentation** - Breaking changes surprise consumers
6. **Documentation drift** - Manual docs not updated when code changes

---

## Best Practices

1. **Use springdoc-openapi** for auto-generated, always-up-to-date docs
2. **Add @Operation descriptions** on every endpoint
3. **Document all response codes** (200, 201, 400, 401, 403, 404, 500)
4. **Provide examples** via @Schema(example = "...")
5. **Group APIs logically** (by domain, by version, by access level)
6. **Secure Swagger UI** or disable in production
7. **Include authentication docs** so consumers know how to authenticate
8. **Version your API docs** alongside API versions

---

## Production Considerations

- **Security**: Disable or auth-protect Swagger UI in production
- **Performance**: Spec generation happens once at startup (no runtime impact)
- **CI/CD**: Export OpenAPI spec as artifact for client generation
- **Consumer portal**: Consider Redoc or Stoplight for polished documentation
- **Contract testing**: Use exported spec as contract for consumer testing

---

## Related Topics

- Spring Boot REST API (what to document)
- Validation (request constraints)
- Exception Handling (error response schemas)
- Spring Security (auth documentation)
- REST API Testing (Spring REST Docs alternative)
