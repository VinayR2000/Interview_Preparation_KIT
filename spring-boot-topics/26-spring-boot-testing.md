# 26. Spring Boot Testing

## Theory

Spring Boot provides comprehensive testing support from unit tests to full integration tests. The testing pyramid recommends more unit tests (fast, isolated) and fewer integration tests (slow, comprehensive).

### Testing Layers:
- **Unit Tests**: Test single class in isolation (Mockito)
- **Slice Tests**: Test one layer with Spring context (@WebMvcTest, @DataJpaTest)
- **Integration Tests**: Test multiple layers together (@SpringBootTest)
- **End-to-End Tests**: Full application with real dependencies (Testcontainers)

### Key Frameworks:
- **JUnit 5**: Test framework (assertions, lifecycle)
- **Mockito**: Mocking framework (mock, spy, verify)
- **MockMvc**: Test controllers without HTTP server
- **Testcontainers**: Real databases/services in Docker for tests

### Important Annotations:
- `@SpringBootTest`: Full application context
- `@WebMvcTest`: Controller layer only
- `@DataJpaTest`: Repository layer only (in-memory DB)
- `@MockBean`: Replace bean with mock in Spring context
- `@SpyBean`: Wrap real bean with spy

---

## Internal Working

```
@SpringBootTest:
  → Starts FULL Spring ApplicationContext
  → All beans created (slow)
  → Use for integration testing

@WebMvcTest(UserController.class):
  → Starts PARTIAL context (MVC layer only)
  → Only controller + filters + converters loaded
  → Services/Repos NOT loaded (use @MockBean)
  → Fast: ~2-5 seconds

@DataJpaTest:
  → Starts PARTIAL context (JPA layer only)
  → Configures in-memory DB (H2)
  → Only repositories + EntityManager loaded
  → Transactional (rolls back after each test)

Unit Test (no Spring):
  → No context loaded
  → Create objects manually
  → Mock dependencies with Mockito
  → Fastest: ~milliseconds
```

---

## Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    TESTING PYRAMID                            │
│                                                              │
│                         /\                                   │
│                        /  \         E2E Tests                │
│                       / E2E\        (Testcontainers)         │
│                      /──────\                                │
│                     /        \      Integration Tests         │
│                    /Integration\    (@SpringBootTest)         │
│                   /────────────\                              │
│                  /              \    Slice Tests              │
│                 /  Slice Tests   \  (@WebMvcTest,            │
│                /                  \  @DataJpaTest)           │
│               /────────────────────\                         │
│              /                      \   Unit Tests           │
│             /      Unit Tests        \  (Mockito)           │
│            /──────────────────────────\                      │
│                                                              │
│   More tests ←──────────────────────────→ Fewer tests       │
│   Faster    ←──────────────────────────→ Slower             │
│   Isolated  ←──────────────────────────→ Integrated         │
└─────────────────────────────────────────────────────────────┘
```

---

## Code

### Unit Test (Service Layer):

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_shouldSaveAndPublishEvent() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, List.of(
            new OrderItem("PROD-1", 2, BigDecimal.valueOf(25.00))
        ));
        Order savedOrder = Order.builder()
            .id(42L).customerId(1L).status(OrderStatus.CREATED).build();
        
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentService.authorize(any())).thenReturn(true);

        // When
        Order result = orderService.createOrder(request);

        // Then
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        
        verify(orderRepository).save(any(Order.class));
        verify(paymentService).authorize(any());
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_whenPaymentFails_shouldThrowException() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, List.of());
        when(paymentService.authorize(any())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(PaymentDeclinedException.class)
            .hasMessageContaining("Payment declined");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_shouldReturnOrder() {
        // Given
        Order order = Order.builder().id(1L).status(OrderStatus.CREATED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // When
        Order result = orderService.getOrder(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ArgumentCaptor usage
    @Test
    void createOrder_shouldPublishCorrectEvent() {
        // Given
        when(orderRepository.save(any())).thenReturn(
            Order.builder().id(1L).customerId(5L).build());
        when(paymentService.authorize(any())).thenReturn(true);
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = 
            ArgumentCaptor.forClass(OrderCreatedEvent.class);

        // When
        orderService.createOrder(new CreateOrderRequest(5L, List.of()));

        // Then
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        OrderCreatedEvent event = eventCaptor.getValue();
        assertThat(event.customerId()).isEqualTo(5L);
    }
}
```

### Controller Slice Test (@WebMvcTest):

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
    void getOrder_shouldReturn200WithOrder() throws Exception {
        // Given
        OrderDTO orderDTO = new OrderDTO(1L, "CREATED", BigDecimal.valueOf(100));
        when(orderService.getOrder(1L)).thenReturn(orderDTO);

        // When/Then
        mockMvc.perform(get("/api/orders/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.totalAmount").value(100));
    }

    @Test
    void createOrder_shouldReturn201() throws Exception {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(1L, List.of());
        OrderDTO response = new OrderDTO(42L, "CREATED", BigDecimal.ZERO);
        when(orderService.createOrder(any())).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void createOrder_withInvalidRequest_shouldReturn400() throws Exception {
        // Given - empty customer ID (validation fails)
        String invalidRequest = "{}";

        // When/Then
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void getOrder_notFound_shouldReturn404() throws Exception {
        when(orderService.getOrder(999L))
            .thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(get("/api/orders/{id}", 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Order not found"));
    }
}
```

### Repository Slice Test (@DataJpaTest):

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByCustomerId_shouldReturnOrders() {
        // Given
        Order order1 = Order.builder().customerId(1L).status(OrderStatus.CREATED).build();
        Order order2 = Order.builder().customerId(1L).status(OrderStatus.SHIPPED).build();
        entityManager.persist(order1);
        entityManager.persist(order2);
        entityManager.flush();

        // When
        List<Order> orders = orderRepository.findByCustomerId(1L);

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(Order::getStatus)
            .containsExactlyInAnyOrder(OrderStatus.CREATED, OrderStatus.SHIPPED);
    }

    @Test
    void findByStatusAndCreatedAtBefore_shouldReturnExpiredOrders() {
        // Given
        Order expired = Order.builder()
            .customerId(1L).status(OrderStatus.PENDING)
            .createdAt(Instant.now().minus(2, ChronoUnit.HOURS)).build();
        Order recent = Order.builder()
            .customerId(2L).status(OrderStatus.PENDING)
            .createdAt(Instant.now()).build();
        entityManager.persist(expired);
        entityManager.persist(recent);
        entityManager.flush();

        // When
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Order> result = orderRepository
            .findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(1L);
    }
}
```

### Integration Test (@SpringBootTest):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAndGetOrder_fullFlow() {
        // Create order
        CreateOrderRequest request = new CreateOrderRequest(1L, 
            List.of(new OrderItem("PROD-1", 2, BigDecimal.valueOf(25))));
        
        ResponseEntity<OrderDTO> createResponse = restTemplate
            .postForEntity("/api/orders", request, OrderDTO.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        Long orderId = createResponse.getBody().getId();

        // Get order
        ResponseEntity<OrderDTO> getResponse = restTemplate
            .getForEntity("/api/orders/{id}", OrderDTO.class, orderId);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getStatus()).isEqualTo("CREATED");
    }
}
```

---

## Dry Run

### Unit Test Execution:

```
@Test createOrder_shouldSaveAndPublishEvent()

1. Mockito creates mock objects (no Spring context)
2. InjectMocks creates OrderService with mocks injected

3. Setup: when(orderRepository.save(any())).thenReturn(savedOrder)
4. Setup: when(paymentService.authorize(any())).thenReturn(true)

5. Execute: orderService.createOrder(request)
   → paymentService.authorize(amount) → returns true (mock)
   → orderRepository.save(order) → returns savedOrder (mock)
   → eventPublisher.publishEvent(event) → does nothing (mock)

6. Verify: assertThat(result.getId()).isEqualTo(42L) ✓
7. Verify: verify(orderRepository).save(any()) → called 1 time ✓
8. Verify: verify(eventPublisher).publishEvent(any()) → called 1 time ✓

TEST PASSES (~5ms)
```

---

## Complexity

| Test Type | Startup Time | Execution Time | Isolation |
|-----------|-------------|----------------|-----------|
| Unit Test | ~0ms | ~1-50ms | Complete |
| @WebMvcTest | ~2-5s | ~50-200ms | Controller layer |
| @DataJpaTest | ~3-8s | ~100-500ms | Repository layer |
| @SpringBootTest | ~10-30s | ~200ms-2s | None (full context) |
| Testcontainers | ~15-60s | ~500ms-5s | Real services |

---

## Real Project Usage

### Test Configuration for Profiles:

```java
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneOffset.UTC);
    }
}

// Shared Testcontainer base class
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = 
        new PostgreSQLContainer<>("postgres:15")
            .withReuse(true);  // Reuse container across tests

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

---

## Interview Questions

1. **Difference between @Mock and @MockBean?**
   - @Mock: Mockito creates mock (no Spring context needed). @MockBean: Replaces bean in Spring ApplicationContext with mock (requires Spring test).

2. **When to use @WebMvcTest vs @SpringBootTest?**
   - @WebMvcTest: Testing controller logic, request mapping, validation, response format. Fast (partial context). @SpringBootTest: Testing full flow across layers.

3. **What is Testcontainers and why use it?**
   - Runs real Docker containers (PostgreSQL, Redis, Kafka) during tests. Tests against real databases instead of H2 — catches compatibility issues.

4. **How does @DataJpaTest work?**
   - Configures in-memory DB, loads JPA-related beans only, each test runs in transaction that rolls back automatically.

5. **How to test @Async methods?**
   - Use Awaitility library to wait for async completion. Or use CompletableFuture return type and .get() in test.

---

## Follow-up Questions

1. How to test Spring Security in controller tests?
   - @WithMockUser(roles="ADMIN") for role-based tests. @WithUserDetails for custom UserDetails. Or manually set SecurityContext in test. For JWT: add Authorization header with test token.

2. How to write tests for Kafka consumers/producers?
   - Use @EmbeddedKafka or Testcontainers with real Kafka. Produce test messages, verify consumer side-effects. For producer: use MockConsumer or verify template.send() was called with ArgumentCaptor.

3. How to achieve good test coverage without over-testing?
   - Focus on behavior, not implementation. Test public API, not internal methods. Cover happy path + error paths + edge cases. 80% coverage on critical services. Don't test framework code (Spring, Jackson).

4. How to mock external REST APIs in integration tests (WireMock)?
   - @WireMockTest starts WireMock server. stubFor() defines response for specific URL patterns. verify() asserts requests were made. Configure your service to point to WireMock URL in test properties.

5. How to test scheduled tasks?
   - Don't test scheduling mechanism itself. Call the method directly in unit test. For integration: use Awaitility to wait for execution. Or disable scheduling in test profile and invoke manually.

---

## Common Mistakes

1. **Testing implementation instead of behavior** - Testing internal method calls rather than outcomes
2. **Over-mocking** - Mocking everything makes tests pass but miss real bugs
3. **No integration tests** - Unit tests pass but real flow fails
4. **Sharing mutable state** between tests - Tests become order-dependent
5. **Using @SpringBootTest everywhere** - Slow test suite (use slice tests)
6. **Not using Testcontainers** - H2 behaves differently from PostgreSQL

---

## Best Practices

1. **Follow testing pyramid** - Many unit, some slice, few integration
2. **Use @WebMvcTest for controllers** (fast, focused)
3. **Use Testcontainers for repository tests** (real database behavior)
4. **Test behavior, not implementation** - Assert outcomes, not method calls
5. **Use builder/factory patterns** for test data
6. **One assertion concept per test** (can have multiple asserts for one concept)
7. **Name tests descriptively**: `methodName_givenCondition_shouldExpectedBehavior`
8. **Reuse containers** with `.withReuse(true)` for faster test runs

---

## Production Considerations

- **CI/CD pipeline**: Tests must run reliably in CI (Testcontainers needs Docker)
- **Test speed**: Parallel execution, container reuse, proper test slicing
- **Test data management**: Use TestEntityManager, factory patterns, not SQL scripts
- **Coverage targets**: 80%+ line coverage for critical services, but quality > quantity
- **Flaky tests**: Async tests need proper waiting (Awaitility), not Thread.sleep()
- **Database migrations**: Test Flyway/Liquibase migrations in integration tests

---

## Related Topics

- REST API Testing (MockMvc, RestAssured)
- Testcontainers (real service testing)
- Spring Security Testing
- JUnit 5 (lifecycle, parameterized tests)
- Mockito (advanced mocking patterns)
- CI/CD (test automation)
