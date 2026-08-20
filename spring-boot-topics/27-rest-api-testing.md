# 27. REST API Testing

## Theory

REST API testing validates that your endpoints handle requests correctly, return proper responses, handle errors gracefully, and integrate properly with downstream services.

### Testing Tools:
- **MockMvc**: Spring's test framework for controllers (no HTTP server)
- **REST Assured**: Fluent API for HTTP testing (with real server)
- **WebTestClient**: Reactive-compatible test client
- **WireMock**: Mock external HTTP services
- **Postman/Newman**: Manual + automated API testing

### What to Test in API:
- Request mapping (correct URL, method)
- Request validation (body, params, headers)
- Response status codes
- Response body structure
- Error responses
- Authentication/Authorization
- Content negotiation
- Pagination

---

## Internal Working

```
MockMvc Test:
  → Creates MockHttpServletRequest
  → Passes through DispatcherServlet (filters, handlers)
  → Controller processes request
  → Returns MockHttpServletResponse
  → Assertions on response
  (No actual HTTP, no embedded server)

@SpringBootTest + TestRestTemplate:
  → Starts embedded server on random port
  → Makes real HTTP requests
  → Tests full stack including serialization
  (Real HTTP, real server)

REST Assured:
  → Makes real HTTP requests to running server
  → BDD-style assertions (given/when/then)
  → Tests from external client perspective
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                   API TESTING LEVELS                           │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Level 1: MockMvc (Controller Unit Test)                  │ │
│  │                                                          │ │
│  │ Test → MockMvc → DispatcherServlet → Controller          │ │
│  │        (Services mocked with @MockBean)                  │ │
│  │        Fast, isolated, no HTTP                           │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Level 2: Integration (TestRestTemplate)                   │ │
│  │                                                          │ │
│  │ Test → HTTP → Embedded Server → Full Stack → DB          │ │
│  │        (Real services, Testcontainers for DB)            │ │
│  │        Slower, catches integration bugs                  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Level 3: Contract (WireMock + Pact)                       │ │
│  │                                                          │ │
│  │ Service A → WireMock (mocks Service B)                   │ │
│  │ Verifies API contracts between services                  │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

---

## Code

### MockMvc Comprehensive Test:

```java
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)  // If security is involved
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    // GET with path variable
    @Test
    void getUser_shouldReturn200() throws Exception {
        UserDTO user = new UserDTO(1L, "John", "john@example.com");
        when(userService.findById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/{id}", 1L)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("John"))
            .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    // GET with query parameters + pagination
    @Test
    void searchUsers_withPagination() throws Exception {
        Page<UserDTO> page = new PageImpl<>(
            List.of(new UserDTO(1L, "John", "john@example.com")),
            PageRequest.of(0, 10), 1);
        when(userService.search(eq("John"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/users")
                .param("name", "John")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].name").value("John"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    // POST with validation
    @Test
    void createUser_withValidRequest_shouldReturn201() throws Exception {
        CreateUserRequest request = new CreateUserRequest("John", "john@example.com");
        UserDTO response = new UserDTO(1L, "John", "john@example.com");
        when(userService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/users/1"))
            .andExpect(jsonPath("$.id").value(1));
    }

    // Validation error test
    @Test
    void createUser_withInvalidEmail_shouldReturn400() throws Exception {
        CreateUserRequest request = new CreateUserRequest("", "invalid-email");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists())
            .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists());
    }

    // PUT
    @Test
    void updateUser_shouldReturn200() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest("John Updated");
        UserDTO updated = new UserDTO(1L, "John Updated", "john@example.com");
        when(userService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/users/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("John Updated"));
    }

    // DELETE
    @Test
    void deleteUser_shouldReturn204() throws Exception {
        doNothing().when(userService).delete(1L);

        mockMvc.perform(delete("/api/users/{id}", 1L))
            .andExpect(status().isNoContent());

        verify(userService).delete(1L);
    }

    // Error handling
    @Test
    void getUser_notFound_shouldReturn404() throws Exception {
        when(userService.findById(999L))
            .thenThrow(new ResourceNotFoundException("User not found with id: 999"));

        mockMvc.perform(get("/api/users/{id}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("User not found with id: 999"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    // With authentication
    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_asAdmin_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteUser_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L))
            .andExpect(status().isForbidden());
    }
}
```

### REST Assured Tests:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiRestAssuredTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setup() {
        RestAssured.port = port;
        RestAssured.basePath = "/api";
    }

    @Test
    void createAndGetUser() {
        // Create user
        UserDTO created = given()
            .contentType(ContentType.JSON)
            .body(new CreateUserRequest("John", "john@example.com"))
        .when()
            .post("/users")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .body("name", equalTo("John"))
            .body("email", equalTo("john@example.com"))
            .extract().as(UserDTO.class);

        // Get user
        given()
        .when()
            .get("/users/{id}", created.getId())
        .then()
            .statusCode(200)
            .body("id", equalTo(created.getId().intValue()))
            .body("name", equalTo("John"));
    }
}
```

### WireMock for External Service:

```java
@SpringBootTest
@WireMockTest(httpPort = 8089)
class OrderServiceWithExternalApiTest {

    @Test
    void createOrder_shouldCallPaymentGateway(WireMockRuntimeInfo wmInfo) {
        // Stub external payment API
        stubFor(post(urlPathEqualTo("/api/payments"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"transactionId": "txn-123", "status": "APPROVED"}
                    """)));

        // Test your service that calls the payment API
        Order result = orderService.createOrder(request);

        assertThat(result.getPaymentStatus()).isEqualTo("APPROVED");
        
        verify(postRequestedFor(urlPathEqualTo("/api/payments"))
            .withRequestBody(matchingJsonPath("$.amount", equalTo("100.00"))));
    }
}
```

---

## Dry Run

### MockMvc Test Execution:

```
@Test createUser_withValidRequest_shouldReturn201()

1. MockMvc builds request:
   POST /api/users
   Content-Type: application/json
   Body: {"name": "John", "email": "john@example.com"}

2. DispatcherServlet processes:
   → Finds mapping: POST /api/users → UserController.createUser()
   → Validates @RequestBody with @Valid
   → Validation passes ✓

3. Controller calls userService.create()
   → @MockBean returns: UserDTO{id=1, name="John", email="john@example.com"}

4. Controller returns: ResponseEntity.created(URI).body(userDTO)

5. MockMvc assertions:
   → status().isCreated() → 201 ✓
   → header("Location", "/api/users/1") ✓
   → jsonPath("$.id").value(1) ✓

TEST PASSES
```

---

## Complexity

| Tool | Startup | Per Test | Best For |
|------|---------|----------|----------|
| MockMvc | ~3s | ~10-50ms | Controller logic |
| TestRestTemplate | ~10-20s | ~50-200ms | Full integration |
| REST Assured | ~10-20s | ~50-200ms | BDD-style, readability |
| WireMock | ~1-2s | ~20-100ms | External service mocking |

---

## Real Project Usage

### Contract Testing Between Services:

```java
// Consumer test (Service A expects this from Service B)
@Test
void getUserFromUserService_shouldReturnExpectedFormat() {
    stubFor(get(urlEqualTo("/api/users/1"))
        .willReturn(okJson("""
            {"id": 1, "name": "John", "email": "john@test.com", "active": true}
            """)));

    UserDTO user = userServiceClient.getUser(1L);
    
    assertThat(user.getName()).isEqualTo("John");
    assertThat(user.isActive()).isTrue();
}
```

---

## Interview Questions

1. **MockMvc vs TestRestTemplate?**
   - MockMvc: No server started, tests through DispatcherServlet (fast, controller-focused). TestRestTemplate: Real HTTP to embedded server (tests serialization, full stack).

2. **How to test exception handlers?**
   - Trigger exceptions via mocked service, assert response status and error body structure using MockMvc.

3. **How to test secured endpoints?**
   - @WithMockUser for simple role testing, or inject JWT token in headers for JWT-secured endpoints.

4. **What is contract testing?**
   - Verifying that producer/consumer API expectations match. Tools: Pact, Spring Cloud Contract. Prevents breaking changes between services.

5. **How to test file upload endpoints?**
   - MockMvc: `mockMvc.perform(multipart("/upload").file(mockFile))`. Create MockMultipartFile with test content.

---

## Follow-up Questions

1. How to test WebSocket endpoints?
   - Use WebSocketStompClient in tests. Connect to embedded server, subscribe to topic, send message, verify received response. Or use MockMvc for STOMP message testing.

2. How to implement API versioning tests?
   - Test both versions simultaneously. Verify v1 returns old format, v2 returns new format. Ensure v1 backward compatibility when v2 is deployed. Contract tests prevent breaking changes.

3. How to test rate-limited endpoints?
   - Send requests in rapid succession, assert that responses change from 200 to 429 after limit. Reset rate limiter between tests. Use a fast clock or mock rate limiter for unit tests.

4. How to automate API tests in CI/CD pipeline?
   - Run MockMvc tests (no infra needed) in unit test phase. Run Testcontainers integration tests with Docker in CI. Use Newman (Postman CLI) for E2E against deployed environments. Fail pipeline on test failure.

5. How to generate API documentation from tests (Spring REST Docs)?
   - Use spring-restdocs-mockmvc: Write tests with documented().andDo(document("endpoint-name")). Generates AsciiDoc snippets from actual request/response. Ensures docs are always accurate (test must pass).

---

## Common Mistakes

1. **Not testing error paths** - Only testing happy path misses 404, 400, 500 scenarios
2. **Testing framework instead of code** - Asserting Spring/Jackson behavior instead of your logic
3. **No validation testing** - Skipping tests for invalid request bodies
4. **Hardcoded test data** - Use builders/factories for maintainable test data
5. **Ignoring response headers** - Location header for POST, Content-Type, custom headers
6. **Not testing pagination** - Missing page metadata assertions

---

## Best Practices

1. **Test all HTTP methods** your endpoint supports
2. **Test all response codes** (200, 201, 204, 400, 401, 403, 404, 409, 500)
3. **Test validation** for every constraint on request body
4. **Use @WebMvcTest** for controller-level testing (fast, isolated)
5. **Test error response structure** - Consistent error format across API
6. **Use WireMock** for external service dependencies
7. **Test content negotiation** if supporting multiple formats
8. **Automate with CI/CD** - Run on every PR

---

## Production Considerations

- **Performance testing**: Use JMeter, Gatling, or k6 for load tests
- **API documentation**: Generate from tests (Spring REST Docs) or annotations (SpringDoc/Swagger)
- **Monitoring test coverage**: Track which endpoints have tests
- **Contract tests in CI**: Run consumer tests before deploying provider changes
- **Test environments**: Mirror production setup with Testcontainers

---

## Related Topics

- Spring Boot Testing (unit + integration)
- Spring Security Testing
- MockMvc (detailed API)
- WireMock (external mocking)
- Testcontainers (real services)
- API Documentation (OpenAPI/Swagger)
