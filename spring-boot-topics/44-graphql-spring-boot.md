# 44. GraphQL with Spring Boot

## Theory

GraphQL is a query language for APIs that allows clients to request exactly the data they need. Spring for GraphQL (official Spring project since Spring Boot 3.x) provides first-class integration.

### GraphQL vs REST:

| Aspect | REST | GraphQL |
|--------|------|---------|
| Endpoints | Multiple (per resource) | Single (/graphql) |
| Data fetching | Fixed response shape | Client specifies fields |
| Over-fetching | Common (get all fields) | Never (only requested fields) |
| Under-fetching | Multiple requests needed | Single query for related data |
| Versioning | URL/header versioning | Schema evolution (no versioning needed) |
| Caching | HTTP caching (easy) | More complex (query-based) |
| File upload | Native (multipart) | Requires extension |
| Real-time | WebSocket/SSE separately | Subscriptions built-in |

### Core Concepts:
- **Schema**: Defines types, queries, mutations, subscriptions (contract)
- **Query**: Read operations (like GET)
- **Mutation**: Write operations (like POST/PUT/DELETE)
- **Subscription**: Real-time updates via WebSocket
- **Resolver**: Function that fetches data for a field
- **DataLoader**: Batches and caches field-level fetches (solves N+1)
- **Schema-first vs Code-first**: Define schema in .graphqls files vs annotations

### GraphQL Schema Language:
```graphql
type Query {
    user(id: ID!): User
    users(page: Int, size: Int): UserPage!
}

type Mutation {
    createUser(input: CreateUserInput!): User!
    updateUser(id: ID!, input: UpdateUserInput!): User!
    deleteUser(id: ID!): Boolean!
}

type Subscription {
    orderStatusChanged(orderId: ID!): Order!
}

type User {
    id: ID!
    name: String!
    email: String!
    orders: [Order!]!
}
```

---

## Internal Working

```
Client sends POST /graphql
  Body: { "query": "{ user(id: 1) { name email orders { id total } } }" }
       ↓
GraphQL Engine parses query
       ↓
Validates against schema (type checking)
       ↓
Execution engine traverses query tree:
  ┌─────────────────────────────────────────┐
  │ user(id: 1)                             │
  │   → UserController.user(id) resolver    │
  │   → Returns User object                 │
  │                                          │
  │   user.name → resolved from User field  │
  │   user.email → resolved from User field │
  │   user.orders                            │
  │     → OrderController.orders(user)      │
  │     → DataLoader batches if multiple    │
  │     → Returns List<Order>               │
  │       order.id → from Order field       │
  │       order.total → from Order field    │
  └─────────────────────────────────────────┘
       ↓
Response assembled (only requested fields):
{
  "data": {
    "user": {
      "name": "John",
      "email": "john@example.com",
      "orders": [
        { "id": "1", "total": 99.99 }
      ]
    }
  }
}
```

### DataLoader (N+1 Solution):
```
Without DataLoader:
  Query: { users { orders { ... } } }
  → Fetch 10 users (1 query)
  → For EACH user, fetch orders individually (10 queries)
  → Total: 11 queries (N+1!)

With DataLoader:
  → Fetch 10 users (1 query)
  → Collect all user IDs: [1, 2, 3, ..., 10]
  → Batch fetch: SELECT * FROM orders WHERE user_id IN (1,2,...,10) (1 query)
  → Total: 2 queries!
```

---

## Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    GRAPHQL REQUEST FLOW                        │
│                                                               │
│  Client                                                       │
│  query { user(id:1) { name orders { id } } }                 │
│       │                                                       │
│       ↓ POST /graphql                                         │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Spring GraphQL Engine                        │ │
│  │                                                          │ │
│  │  1. Parse query string → AST                             │ │
│  │  2. Validate against schema                              │ │
│  │  3. Execute resolvers for each field:                    │ │
│  │                                                          │ │
│  │  ┌──────────────────────────────────────────────────┐   │ │
│  │  │ Query.user(id:1)                                  │   │ │
│  │  │   → @QueryMapping user()                          │   │ │
│  │  │   → userService.findById(1)                       │   │ │
│  │  │   → Returns User{id:1, name:"John", email:"..."}  │   │ │
│  │  │                                                    │   │ │
│  │  │ User.orders                                        │   │ │
│  │  │   → @SchemaMapping orders(User user)               │   │ │
│  │  │   → DataLoader.load(user.id)                       │   │ │
│  │  │   → Batched: orderRepo.findByUserIds([1])          │   │ │
│  │  │   → Returns [Order{id:1, total:99.99}]             │   │ │
│  │  └──────────────────────────────────────────────────┘   │ │
│  │                                                          │ │
│  │  4. Assemble response (only requested fields)            │ │
│  └─────────────────────────────────────────────────────────┘ │
│       │                                                       │
│       ↓                                                       │
│  { "data": { "user": { "name": "John", "orders": [...] } }} │
└──────────────────────────────────────────────────────────────┘

┌──────────── DATALOADER BATCHING ─────────────────────────────┐
│                                                               │
│  Query: { users { name orders { id total } } }               │
│                                                               │
│  Without DataLoader:            With DataLoader:              │
│  SELECT * FROM users           SELECT * FROM users            │
│  SELECT * FROM orders          SELECT * FROM orders           │
│    WHERE user_id = 1             WHERE user_id IN (1,2,3,4,5)│
│  SELECT * FROM orders                                         │
│    WHERE user_id = 2           Total: 2 queries ✓             │
│  SELECT * FROM orders                                         │
│    WHERE user_id = 3                                          │
│  SELECT * FROM orders                                         │
│    WHERE user_id = 4                                          │
│  SELECT * FROM orders                                         │
│    WHERE user_id = 5                                          │
│  Total: 6 queries ✗                                           │
└───────────────────────────────────────────────────────────────┘
```

---

## Code

### Dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### Schema (src/main/resources/graphql/schema.graphqls):

```graphql
type Query {
    user(id: ID!): User
    users(page: Int = 0, size: Int = 20): UserConnection!
    searchUsers(name: String!): [User!]!
}

type Mutation {
    createUser(input: CreateUserInput!): User!
    updateUser(id: ID!, input: UpdateUserInput!): User!
    deleteUser(id: ID!): Boolean!
    placeOrder(input: PlaceOrderInput!): Order!
}

type Subscription {
    orderStatusChanged(orderId: ID!): Order!
}

type User {
    id: ID!
    name: String!
    email: String!
    createdAt: String!
    orders: [Order!]!
    orderCount: Int!
}

type Order {
    id: ID!
    status: OrderStatus!
    totalAmount: Float!
    items: [OrderItem!]!
    createdAt: String!
}

type OrderItem {
    id: ID!
    productName: String!
    quantity: Int!
    price: Float!
}

type UserConnection {
    content: [User!]!
    totalElements: Int!
    totalPages: Int!
    hasNext: Boolean!
}

enum OrderStatus {
    PENDING
    CONFIRMED
    SHIPPED
    DELIVERED
    CANCELLED
}

input CreateUserInput {
    name: String!
    email: String!
    password: String!
}

input UpdateUserInput {
    name: String
    email: String
}

input PlaceOrderInput {
    userId: ID!
    items: [OrderItemInput!]!
}

input OrderItemInput {
    productId: ID!
    quantity: Int!
}
```

### Configuration:

```yaml
spring:
  graphql:
    graphiql:
      enabled: true    # GraphiQL UI at /graphiql
      path: /graphiql
    schema:
      printer:
        enabled: true  # Schema introspection
    path: /graphql
```

### Query Controller:

```java
@Controller
public class UserGraphQLController {

    private final UserService userService;

    // Query resolver: maps to Query.user(id) in schema
    @QueryMapping
    public User user(@Argument Long id) {
        return userService.findById(id);
    }

    // Query resolver: maps to Query.users(page, size)
    @QueryMapping
    public UserConnection users(@Argument int page, @Argument int size) {
        Page<User> result = userService.findAll(PageRequest.of(page, size));
        return new UserConnection(
            result.getContent(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.hasNext()
        );
    }

    // Query resolver: maps to Query.searchUsers(name)
    @QueryMapping
    public List<User> searchUsers(@Argument String name) {
        return userService.searchByName(name);
    }
}
```

### Mutation Controller:

```java
@Controller
public class UserMutationController {

    private final UserService userService;

    @MutationMapping
    public User createUser(@Argument CreateUserInput input) {
        return userService.create(input);
    }

    @MutationMapping
    public User updateUser(@Argument Long id, @Argument UpdateUserInput input) {
        return userService.update(id, input);
    }

    @MutationMapping
    public boolean deleteUser(@Argument Long id) {
        userService.delete(id);
        return true;
    }
}
```

### Schema Mapping (Field Resolvers):

```java
@Controller
public class UserFieldResolvers {

    private final OrderService orderService;

    // Resolves User.orders field — only called if client requests it!
    @SchemaMapping(typeName = "User", field = "orders")
    public List<Order> orders(User user) {
        return orderService.findByUserId(user.getId());
    }

    // Resolves User.orderCount — computed field
    @SchemaMapping(typeName = "User", field = "orderCount")
    public int orderCount(User user) {
        return orderService.countByUserId(user.getId());
    }
}
```

### DataLoader (Batch Loading — Solves N+1):

```java
@Controller
public class UserFieldResolvers {

    // DataLoader: batches all User.orders calls into ONE query
    @BatchMapping
    public Map<User, List<Order>> orders(List<User> users) {
        // Called ONCE with ALL users that need orders loaded
        List<Long> userIds = users.stream().map(User::getId).toList();
        List<Order> allOrders = orderService.findByUserIds(userIds);

        // Group orders by user
        Map<Long, List<Order>> ordersByUserId = allOrders.stream()
            .collect(Collectors.groupingBy(Order::getUserId));

        // Return map: User → their orders
        return users.stream()
            .collect(Collectors.toMap(
                user -> user,
                user -> ordersByUserId.getOrDefault(user.getId(), List.of())
            ));
    }
}
```

### Subscription (Real-time):

```java
@Controller
public class OrderSubscriptionController {

    private final Sinks.Many<Order> orderSink = Sinks.many().multicast().onBackpressureBuffer();

    @SubscriptionMapping
    public Flux<Order> orderStatusChanged(@Argument Long orderId) {
        return orderSink.asFlux()
            .filter(order -> order.getId().equals(orderId));
    }

    // Called from service when order status changes
    public void publishOrderUpdate(Order order) {
        orderSink.tryEmitNext(order);
    }
}
```

### Exception Handling:

```java
@ControllerAdvice
public class GraphQLExceptionHandler {

    @GraphQlExceptionHandler
    public GraphQLError handleNotFound(ResourceNotFoundException ex) {
        return GraphQLError.newError()
            .errorType(ErrorType.NOT_FOUND)
            .message(ex.getMessage())
            .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleValidation(ConstraintViolationException ex) {
        return GraphQLError.newError()
            .errorType(ErrorType.BAD_REQUEST)
            .message("Validation failed: " + ex.getMessage())
            .build();
    }
}
```

### Security Integration:

```java
@Controller
public class SecuredGraphQLController {

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> allUsers() {
        return userService.findAll();
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public User updateUser(@Argument Long id, @Argument UpdateUserInput input) {
        return userService.update(id, input);
    }

    // Access current user in resolver
    @QueryMapping
    public User me(@AuthenticationPrincipal CustomUserDetails principal) {
        return userService.findById(principal.getId());
    }
}
```

### Testing:

```java
@SpringBootTest
@AutoConfigureGraphQlTester
class UserGraphQLTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Test
    void shouldReturnUser() {
        graphQlTester.document("""
                query {
                    user(id: 1) {
                        name
                        email
                    }
                }
            """)
            .execute()
            .path("user.name").entity(String.class).isEqualTo("John")
            .path("user.email").entity(String.class).isEqualTo("john@example.com");
    }

    @Test
    void shouldCreateUser() {
        graphQlTester.document("""
                mutation {
                    createUser(input: {name: "Alice", email: "alice@test.com", password: "secret"}) {
                        id
                        name
                    }
                }
            """)
            .execute()
            .path("createUser.name").entity(String.class).isEqualTo("Alice")
            .path("createUser.id").entity(String.class).isNotEmpty();
    }

    @Test
    void shouldHandleNotFound() {
        graphQlTester.document("""
                query {
                    user(id: 999) {
                        name
                    }
                }
            """)
            .execute()
            .errors()
            .expect(error -> error.getErrorType() == ErrorType.NOT_FOUND);
    }
}
```

---

## Dry Run

### Client Query Execution:

```
Client sends:
POST /graphql
{
  "query": "{ users(page: 0, size: 2) { content { name email orders { id total } } totalElements } }"
}

Execution:
1. Parse query → validated against schema ✓
2. Resolve Query.users(page=0, size=2):
   → UserGraphQLController.users(0, 2)
   → SQL: SELECT * FROM users LIMIT 2 OFFSET 0
   → Returns [User{id=1, name="John"}, User{id=2, name="Jane"}]
   → totalElements: COUNT(*) = 50

3. Resolve User.orders for BOTH users (@BatchMapping):
   → Called ONCE with [User#1, User#2]
   → SQL: SELECT * FROM orders WHERE user_id IN (1, 2)
   → Returns {1: [Order{id=10, total=99}], 2: [Order{id=11, total=149}]}

4. Assemble response (only requested fields):

Response:
{
  "data": {
    "users": {
      "content": [
        {
          "name": "John",
          "email": "john@example.com",
          "orders": [{"id": "10", "total": 99.0}]
        },
        {
          "name": "Jane",
          "email": "jane@example.com",
          "orders": [{"id": "11", "total": 149.0}]
        }
      ],
      "totalElements": 50
    }
  }
}

Total SQL: 3 queries (users + count + batched orders)
Without DataLoader: 4 queries (users + count + orders×2)
```

---

## Complexity

| Operation | Time |
|-----------|------|
| Query parsing + validation | O(n) — n = query depth/fields |
| Resolver execution | Depends on data source (DB query time) |
| DataLoader batching | O(1) batch call vs O(n) individual calls |
| Response serialization | O(m) — m = response fields |
| Schema introspection | O(s) — s = schema size (cached) |

### Performance Comparison:

| Scenario | REST | GraphQL |
|----------|------|---------|
| Get user + orders + products | 3 API calls | 1 query |
| Get only user name | Full user object returned | Only name field returned |
| Mobile (low bandwidth) | Large payloads | Minimal data transfer |
| Complex relationships | Multiple round-trips | Single request |

---

## Real Project Usage

### E-commerce GraphQL API:

```graphql
# Client query for order detail page
query OrderDetail($orderId: ID!) {
    order(id: $orderId) {
        id
        status
        totalAmount
        createdAt
        items {
            productName
            quantity
            price
        }
        user {
            name
            email
        }
        shippingAddress {
            street
            city
            zipCode
        }
    }
}

# REST equivalent would require:
# GET /api/orders/42
# GET /api/users/5
# GET /api/orders/42/items
# GET /api/addresses/order/42
# = 4 HTTP calls vs 1 GraphQL query
```

---

## Interview Questions

1. **What is GraphQL and how does it differ from REST?**
   - GraphQL: Single endpoint, client specifies exact fields needed, strongly typed schema. REST: Multiple endpoints, server decides response shape, HTTP method semantics. GraphQL eliminates over-fetching/under-fetching. REST is simpler for CRUD, GraphQL for complex data requirements.

2. **How does Spring for GraphQL handle the N+1 problem?**
   - @BatchMapping annotation. Instead of resolving related data per-item (N queries), collects all parent items and resolves in a single batch call (1 query). Spring's DataLoader integration handles batching and caching per-request automatically.

3. **What is the difference between @QueryMapping, @MutationMapping, and @SchemaMapping?**
   - @QueryMapping: Resolves root Query fields (entry points). @MutationMapping: Resolves root Mutation fields (write operations). @SchemaMapping: Resolves fields on any type (e.g., User.orders). @BatchMapping: Like @SchemaMapping but batches calls.

4. **When would you choose GraphQL over REST?**
   - GraphQL: Complex relationships, mobile apps (minimize data), multiple clients needing different data shapes, reducing round-trips. REST: Simple CRUD, file uploads, HTTP caching needed, server-to-server APIs, team unfamiliar with GraphQL.

5. **How do you handle authentication/authorization in GraphQL?**
   - Authentication: Same as REST (JWT in Authorization header, Spring Security filter chain). Authorization: @PreAuthorize on resolver methods, or custom directive-based authorization. Access current user via @AuthenticationPrincipal in resolvers.

---

## Follow-up Questions

1. How do you prevent deeply nested queries from causing performance issues?
   - Query depth limiting (max depth = 10). Query complexity analysis (assign cost per field, reject if total > threshold). Timeout on execution. Disable introspection in production. Persisted queries (only allow pre-approved queries).

2. How does GraphQL subscription work with Spring Boot?
   - WebSocket transport. Client subscribes → server pushes updates. Uses Reactor Flux in Spring. Publisher emits events, subscription filters relevant ones. Auto-closes on client disconnect. Requires spring-boot-starter-websocket.

3. How do you handle file uploads in GraphQL?
   - GraphQL spec doesn't support files natively. Options: Multipart request spec (graphql-java-kickstart), separate REST endpoint for uploads (return URL, use in mutation), or Base64 encoding (small files only, not recommended for large).

4. How do you version a GraphQL API?
   - You don't (typically). Use schema evolution: add new fields (non-breaking), deprecate old fields with @deprecated directive, eventually remove after migration period. Clients request only what they need, so additive changes are safe.

5. GraphQL vs REST for microservices communication?
   - REST preferred for service-to-service (simpler, HTTP caching, well-understood). GraphQL better as BFF (Backend for Frontend) aggregating multiple services. Don't use GraphQL between microservices — adds unnecessary complexity.

---

## Common Mistakes

1. **Exposing entity classes directly** - Use DTOs/dedicated GraphQL types to avoid exposing DB structure
2. **Not using DataLoader/BatchMapping** - N+1 problem is even worse in GraphQL (nested resolvers)
3. **No query complexity limits** - Malicious queries can DoS your server: `{ users { orders { items { product { reviews { ... } } } } } }`
4. **Using GraphQL for simple CRUD** - Overkill; REST is simpler and more appropriate
5. **Mixing mutation side effects** - Mutations should be explicit; avoid hidden side effects in queries
6. **Not handling null vs absent** - In GraphQL, a field not requested is different from a field returning null
7. **Enabling introspection in production** - Reveals entire API schema to attackers

---

## Best Practices

1. **Use @BatchMapping** for all relationship fields (prevent N+1)
2. **Define clear input types** for mutations (not reusing query types)
3. **Implement query depth/complexity limits** for security
4. **Schema-first design** - Define .graphqls files, then implement resolvers
5. **Use connections pattern** for pagination (Relay-style cursor or offset)
6. **Deprecate don't remove** - `@deprecated(reason: "Use newField instead")`
7. **Error handling** - Return partial data + errors (GraphQL supports this natively)
8. **Separate concerns** - @QueryMapping in one controller, @SchemaMapping in another
9. **Test with GraphQlTester** - Spring's dedicated testing support
10. **Disable GraphiQL in production** - Development tool only

---

## Production Considerations

- **Query complexity limits**: Prevent expensive deeply-nested queries
- **Persisted queries**: Only allow pre-registered queries in production (APQ - Automatic Persisted Queries)
- **Caching**: Response caching is harder than REST (POST requests, variable queries). Use DataLoader per-request caching and Redis for resolver-level caching.
- **Monitoring**: Track resolver execution time, query complexity, error rates per operation
- **Schema registry**: Version and validate schema changes (Federation for multi-service GraphQL)
- **Security**: Disable introspection in production, implement field-level authorization
- **Federation**: Apollo Federation or schema stitching for multi-service GraphQL gateway

---

## Related Topics

- Spring Boot REST API (comparison)
- Spring Data JPA (data fetching for resolvers)
- Caching (resolver-level caching)
- Spring Security (authentication in GraphQL)
- WebSocket (subscriptions)
- Microservices (BFF pattern with GraphQL)
- Performance (N+1, DataLoader)
