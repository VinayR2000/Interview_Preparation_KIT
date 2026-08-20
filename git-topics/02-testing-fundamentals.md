# Software Testing Fundamentals

## Theory

### What is Software Testing?
The process of verifying that software works correctly, meets requirements, and is free of defects. Testing is not just "writing tests" — it's a mindset and engineering discipline.

### Testing Pyramid ⭐⭐⭐

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲         Few, slow, expensive
                 ╱──────╲        (Selenium, Cypress, Playwright)
                ╱        ╲
               ╱Integration╲    Medium count, moderate speed
              ╱─────────────╲   (Spring Boot @SpringBootTest, Testcontainers)
             ╱               ╲
            ╱   Unit Tests    ╲  Many, fast, cheap
           ╱───────────────────╲ (JUnit, Mockito)
          ╱─────────────────────╲

Rule: More tests at the bottom, fewer at the top.
Each layer catches different types of bugs.
```

### Test Types

| Type | What it Tests | Speed | Scope | Tools |
|------|--------------|-------|-------|-------|
| Unit | Single class/method in isolation | Very fast (ms) | Narrow | JUnit, Mockito |
| Integration | Multiple components together | Moderate (seconds) | Medium | @SpringBootTest, Testcontainers |
| E2E / Functional | Full system from user perspective | Slow (seconds-minutes) | Wide | Selenium, REST Assured |
| Contract | API contracts between services | Fast | Interface | Pact, Spring Cloud Contract |
| Performance | Load, stress, scalability | Slow | System | JMeter, Gatling, k6 |
| Security | Vulnerabilities, auth bypass | Varies | System | OWASP ZAP, dependency scanners |

---

## Unit Testing ⭐⭐⭐

### Principles

```
Unit test characteristics:
├── Fast (milliseconds per test)
├── Isolated (no external dependencies — DB, network, filesystem)
├── Repeatable (same result every time, any order)
├── Self-validating (pass or fail, no manual inspection)
└── Timely (written with or before the code)
```

### What to Unit Test

| Test | Don't Test |
|------|-----------|
| Business logic | Getters/setters |
| Calculations | Framework code (Spring, Hibernate) |
| Conditional paths | Private methods directly |
| Edge cases | Configuration |
| Validation rules | Trivial code |
| Error handling | Third-party libraries |

### JUnit 5 + Mockito Example (Spring Boot)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("Should create order when payment succeeds")
    void createOrder_paymentSucceeds_orderSaved() {
        // Given (Arrange)
        OrderRequest request = new OrderRequest("PROD-1", 2, "CUST-123");
        when(paymentService.charge(any())).thenReturn(PaymentResult.success("PAY-001"));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId("ORD-001");
            return order;
        });

        // When (Act)
        Order result = orderService.createOrder(request);

        // Then (Assert)
        assertThat(result.getId()).isEqualTo("ORD-001");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getPaymentId()).isEqualTo("PAY-001");

        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publish(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when payment fails")
    void createOrder_paymentFails_throwsException() {
        // Given
        OrderRequest request = new OrderRequest("PROD-1", 2, "CUST-123");
        when(paymentService.charge(any())).thenReturn(PaymentResult.failed("Insufficient funds"));

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(PaymentFailedException.class)
            .hasMessageContaining("Insufficient funds");

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should reject order with zero quantity")
    void createOrder_zeroQuantity_throwsValidationException() {
        // Given
        OrderRequest request = new OrderRequest("PROD-1", 0, "CUST-123");

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Quantity must be positive");

        verifyNoInteractions(paymentService, orderRepository, eventPublisher);
    }
}
```

### Test Naming Convention ⭐⭐⭐

```
Pattern: methodName_condition_expectedResult

Examples:
├── createOrder_validRequest_returnsConfirmedOrder
├── createOrder_paymentFails_throwsPaymentException
├── createOrder_duplicateOrderId_throwsConflictException
├── getOrder_existingId_returnsOrder
├── getOrder_nonExistentId_throwsNotFoundException
├── calculateDiscount_premiumCustomer_returns20Percent
└── calculateDiscount_newCustomer_returnsZero
```

---

## Mocking ⭐⭐⭐

### When to Mock

| Mock | Don't Mock |
|------|-----------|
| External services (payment, email) | The class under test |
| Repositories/database access | Value objects / DTOs |
| Network calls (HTTP clients) | Pure logic classes |
| Message publishers (Kafka, RabbitMQ) | Simple utilities |
| Clock/time (for deterministic tests) | Everything (over-mocking) |

### Mockito Patterns

```java
// Stubbing (define behavior)
when(repository.findById("123")).thenReturn(Optional.of(order));
when(repository.findById("999")).thenReturn(Optional.empty());
when(service.process(any())).thenThrow(new ServiceException("down"));

// Verification (check interactions)
verify(repository).save(orderCaptor.capture());
verify(publisher, times(1)).publish(any());
verify(service, never()).rollback(any());
verifyNoInteractions(notificationService);

// Argument captor (inspect what was passed)
ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
verify(repository).save(captor.capture());
Order savedOrder = captor.getValue();
assertThat(savedOrder.getStatus()).isEqualTo(CONFIRMED);
```

### Over-Mocking Anti-Pattern

```java
// BAD: Testing the mock, not the logic
@Test
void badTest() {
    when(calculator.add(2, 3)).thenReturn(5);
    assertEquals(5, calculator.add(2, 3)); // This tests MOCKITO, not your code!
}

// GOOD: Mock dependencies, test YOUR logic
@Test
void goodTest() {
    when(priceRepository.getPrice("PROD-1")).thenReturn(new BigDecimal("99.99"));
    when(discountService.getDiscount("CUST-1")).thenReturn(0.10);

    BigDecimal total = orderService.calculateTotal("PROD-1", 2, "CUST-1");

    assertThat(total).isEqualByComparingTo("179.98"); // 99.99 * 2 * 0.9
}
```

---

## Integration Testing ⭐⭐⭐

### Spring Boot Integration Test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void createOrder_fullFlow_savesToDatabase() {
        // Given
        OrderRequest request = new OrderRequest("PROD-1", 2, "CUST-123");

        // When
        ResponseEntity<Order> response = restTemplate.postForEntity(
            "/api/orders", request, Order.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();

        // Verify in database
        Optional<Order> saved = orderRepository.findById(response.getBody().getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
```

### Controller Test (@WebMvcTest)

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createOrder_validRequest_returns201() throws Exception {
        // Given
        OrderRequest request = new OrderRequest("PROD-1", 2, "CUST-123");
        Order expectedOrder = Order.builder().id("ORD-001").status(CONFIRMED).build();
        when(orderService.createOrder(any())).thenReturn(expectedOrder);

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("ORD-001"))
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createOrder_invalidRequest_returns400() throws Exception {
        // Given
        OrderRequest request = new OrderRequest("", 0, null); // all invalid

        // When & Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        when(orderService.getOrder("UNKNOWN")).thenThrow(new OrderNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/orders/UNKNOWN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Order not found: UNKNOWN"));
    }
}
```

---

## TDD (Test-Driven Development) ⭐⭐

```
TDD Cycle:
1. RED    → Write a failing test
2. GREEN  → Write minimum code to pass
3. REFACTOR → Clean up code (tests still pass)
4. Repeat

Benefits:
├── Forces you to think about design before implementation
├── Guarantees test coverage (code exists BECAUSE of a test)
├── Tests document behavior (living documentation)
├── Catches regressions immediately
└── Encourages small, focused methods

When to TDD:
├── Complex business logic
├── Algorithm implementations
├── Parsing / transformation code
├── When you want to think through edge cases first

When NOT to TDD:
├── Simple CRUD with no logic
├── Prototyping / exploring
├── UI layouts
└── Configuration code
```

---

## Test Coverage ⭐⭐

### What to Aim For

```
Coverage targets (practical):
├── Overall project: 70-80%
├── Business logic / services: 90%+
├── Controllers: 80%+ (happy path + error cases)
├── Repositories: integration tests (not unit mocking)
├── DTOs / entities: Don't waste time testing getters/setters
└── Configuration: Minimal or none

Coverage is a METRIC, not a GOAL.
100% coverage with bad tests = false confidence.
80% coverage with meaningful tests = real confidence.
```

### What Coverage Doesn't Tell You

```
Coverage says: "This line was executed during tests"
Coverage does NOT say:
├── The assertion was meaningful
├── Edge cases were tested
├── The test would catch a real bug
├── Error paths were verified
└── Concurrent behavior was tested
```

---

## Contract Testing ⭐⭐

For microservices — verify API contracts between producer and consumer.

```
Without contract tests:
Service A (consumer) ←── REST ──── Service B (producer)
                                    │
                                    └── Changes response format
                                        → A breaks in production!

With contract tests:
Service B publishes contract: "GET /orders/{id} returns {id, status, total}"
Service A verifies: "I expect {id, status, total}"
If B changes contract → Test fails BEFORE deployment
```

### Spring Cloud Contract Example

```java
// Producer side — contract definition (Groovy DSL)
Contract.make {
    request {
        method 'GET'
        url '/api/orders/ORD-001'
    }
    response {
        status 200
        body([
            id: 'ORD-001',
            status: 'CONFIRMED',
            total: 199.99
        ])
        headers {
            contentType(applicationJson())
        }
    }
}

// Consumer side — verifies against the contract stub
@SpringBootTest
@AutoConfigureStubRunner(ids = "com.example:order-service:+:stubs:8080")
class PaymentServiceContractTest {

    @Autowired
    private OrderClient orderClient; // Feign/RestTemplate client

    @Test
    void shouldGetOrderFromStub() {
        Order order = orderClient.getOrder("ORD-001");
        assertThat(order.getId()).isEqualTo("ORD-001");
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
    }
}
```

---

## Testing Best Practices ⭐⭐⭐

| Practice | Description |
|----------|-------------|
| Test behavior, not implementation | Don't test private methods or internal state |
| One assertion concept per test | Test one logical thing (can have multiple asserts) |
| Arrange-Act-Assert (AAA) | Given/When/Then structure |
| Use meaningful test names | Read the name = understand what's tested |
| Keep tests independent | No shared mutable state, any order execution |
| Fast tests enable CI | Slow tests get skipped — keep them fast |
| Test edge cases | Null, empty, boundary, overflow, concurrent |
| Don't test the framework | Spring, Hibernate, JUnit themselves are tested |
| Use test fixtures/builders | Avoid repeating setup code |
| Clean test data | Each test creates its own data, cleans up after |

---

## Testing in CI/CD Pipeline

```
Pipeline stages:
├── Stage 1: Unit tests (JUnit + Mockito)
│   └── Runs in seconds, fail fast
│
├── Stage 2: Integration tests (Testcontainers)
│   └── Real DB/Redis/Kafka in Docker containers
│
├── Stage 3: Contract tests (Spring Cloud Contract)
│   └── Verify API compatibility between services
│
├── Stage 4: E2E / Smoke tests (against staging)
│   └── Critical paths only (login, create order, pay)
│
└── Stage 5: Performance tests (periodic, not every build)
    └── Load testing against staging environment
```

---

## Interview Questions

### Q: What's the testing pyramid and why does it matter?
**A:** The testing pyramid recommends many fast unit tests at the base, fewer integration tests in the middle, and very few E2E tests at the top. This matters because:
- Unit tests give fast feedback (seconds) and pinpoint exact failures
- Integration tests verify components work together (database, HTTP, messaging)
- E2E tests verify user-facing flows but are slow and brittle
- Inverted pyramid (many E2E, few unit tests) leads to slow CI, flaky tests, and hard-to-debug failures

### Q: Unit test vs Integration test — when to use which?
**A:**
- **Unit tests**: Pure business logic, calculations, validation rules, conditional paths. Mock all dependencies. Fast, isolated.
- **Integration tests**: Database queries work correctly, HTTP endpoints return expected responses, message consumers process correctly, multiple Spring beans wire together properly.

Rule: If testing logic → unit test. If testing wiring/connectivity → integration test.

### Q: What is TDD and when do you use it?
**A:** TDD = Red → Green → Refactor. Write the test first, then the implementation. I use it for complex business logic where thinking through edge cases upfront improves the design. I don't dogmatically apply it to everything — simple CRUD or exploratory code doesn't benefit much.

### Q: How do you test microservices that depend on each other?
**A:** Multiple strategies:
1. **Contract testing** (Spring Cloud Contract / Pact): Producer publishes contract, consumer verifies against stubs
2. **Testcontainers**: Spin up real dependencies (Kafka, PostgreSQL) in Docker for integration tests
3. **WireMock**: Mock external HTTP services with predefined responses
4. **Test doubles for messaging**: Embedded Kafka for producer/consumer tests
5. **E2E in staging**: Deploy all services, run critical path tests

### Q: What makes a test "good"?
**A:** A good test:
1. Fails when the behavior it tests breaks (not brittle, not too broad)
2. Explains what went wrong via its name and assertion messages
3. Runs fast (seconds for unit, seconds-to-minutes for integration)
4. Is independent (no order dependency, no shared state)
5. Tests one concept (focused, readable)
6. Doesn't test implementation details (survives refactoring)
