# Communication Patterns

## REST

### Theory
- Synchronous request-response over HTTP
- Stateless, resource-oriented
- Most common API style for client-facing services

### When to Use
- CRUD operations
- Client-facing APIs
- Simple request-response interactions
- Public APIs (well-understood, tooling-rich)

---

## gRPC

### Theory
- High-performance RPC framework by Google
- Uses Protocol Buffers (protobuf) for serialization
- HTTP/2 based: multiplexing, streaming, header compression
- Strongly typed contract (`.proto` files)

### gRPC vs REST

| Aspect | REST | gRPC |
|--------|------|------|
| Protocol | HTTP/1.1 or 2 | HTTP/2 only |
| Format | JSON (text) | Protobuf (binary) |
| Contract | OpenAPI (optional) | .proto (required) |
| Streaming | No (workarounds exist) | Bidirectional streaming |
| Performance | Slower (text parsing) | 7-10x faster |
| Browser support | Native | Needs gRPC-Web proxy |
| Tooling | Extensive | Growing |
| Use case | Public APIs, web clients | Service-to-service, high throughput |

### gRPC Communication Patterns
```
1. Unary (simple request-response):
   Client ──request──→ Server ──response──→ Client

2. Server Streaming:
   Client ──request──→ Server ──stream of responses──→ Client

3. Client Streaming:
   Client ──stream of requests──→ Server ──response──→ Client

4. Bidirectional Streaming:
   Client ←──stream──→ Server (both directions simultaneously)
```

### Proto File Example
```protobuf
syntax = "proto3";

service UserService {
  rpc GetUser(GetUserRequest) returns (UserResponse);
  rpc ListUsers(ListUsersRequest) returns (stream UserResponse);  // server streaming
}

message GetUserRequest {
  string user_id = 1;
}

message UserResponse {
  string id = 1;
  string name = 2;
  string email = 3;
}
```

---

## WebSockets

### Theory
- Full-duplex, persistent connection between client and server
- Starts as HTTP, then upgrades to WebSocket protocol
- Both sides can send messages at any time
- Low overhead (no HTTP headers per message)

### WebSocket Handshake
```
Client → Server:
  GET /chat HTTP/1.1
  Upgrade: websocket
  Connection: Upgrade
  Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==

Server → Client:
  HTTP/1.1 101 Switching Protocols
  Upgrade: websocket
  Connection: Upgrade
  Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

[WebSocket connection established - bidirectional]
```

### When to Use WebSockets
| Use WebSocket | Use HTTP/REST |
|---------------|---------------|
| Real-time chat | CRUD operations |
| Live notifications | Form submissions |
| Stock tickers | File uploads |
| Online gaming | One-time requests |
| Collaborative editing | Cacheable responses |

### WebSocket Scaling Challenges
- Stateful (connection pinned to specific server)
- Load balancer needs sticky sessions or connection-aware routing
- Connection limit per server (file descriptors)
- Need pub/sub (Redis) for broadcasting across servers

```
Scaling WebSockets:
┌─────────┐    ┌─────────┐    ┌─────────┐
│Server 1 │    │Server 2 │    │Server 3 │
│ Users   │    │ Users   │    │ Users   │
│ A, B, C │    │ D, E, F │    │ G, H, I │
└────┬────┘    └────┬────┘    └────┬────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
            ┌───────▼───────┐
            │  Redis Pub/Sub │  ← Broadcasts messages
            └───────────────┘       to all servers
```

---

## Message Queues

### Theory
- Asynchronous communication between services
- Producer sends message → Queue stores → Consumer processes
- Decouples producers from consumers
- Handles traffic spikes (buffer)

### Diagram
```
┌──────────┐     ┌───────────┐     ┌──────────┐
│ Producer │────→│   Queue   │────→│ Consumer │
│ (Order   │     │  (Buffer) │     │ (Payment │
│  Service)│     │           │     │  Service)│
└──────────┘     └───────────┘     └──────────┘
```

### Point-to-Point vs Pub/Sub

| Aspect | Point-to-Point (Queue) | Pub/Sub (Topic) |
|--------|----------------------|-----------------|
| Consumers | One consumer per message | Multiple subscribers |
| Delivery | Exactly one consumer processes | All subscribers receive |
| Use case | Task distribution | Event broadcasting |
| Example | Order processing | Notifications, analytics |
| Competing | Consumers compete | Consumers are independent |

### Message Queue Guarantees

| Guarantee | Description | Trade-off |
|-----------|-------------|-----------|
| At-most-once | Message delivered 0 or 1 time | Fast, may lose messages |
| At-least-once | Message delivered 1+ times | Safe, may duplicate |
| Exactly-once | Message delivered exactly 1 time | Complex, expensive |

### Popular Message Systems

| System | Type | Best For |
|--------|------|----------|
| RabbitMQ | Message broker | Complex routing, AMQP |
| Apache Kafka | Event streaming | High throughput, log |
| AWS SQS | Managed queue | Simple async, AWS |
| AWS SNS | Managed pub/sub | Fan-out notifications |
| Redis Streams | Stream/queue | Low latency, simple |

---

## Event-Driven Architecture

### Theory
- Services communicate by producing and consuming events
- Events represent facts that happened (past tense): "OrderCreated", "PaymentProcessed"
- Loose coupling: Producer doesn't know or care about consumers
- Eventual consistency is inherent

### Diagram
```
┌─────────────┐   OrderCreated    ┌─────────────────┐
│Order Service│──────────────────→│   Event Bus     │
└─────────────┘                   │  (Kafka/SNS)    │
                                  └──┬────┬────┬───┘
                                     │    │    │
                    ┌────────────────┘    │    └────────────────┐
                    ▼                     ▼                     ▼
            ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
            │Payment Service│    │Email Service │    │Analytics     │
            │"Process payment"│    │"Send confirm"│    │"Track order" │
            └──────────────┘    └──────────────┘    └──────────────┘
```

### Event Types
| Type | Description | Example |
|------|-------------|---------|
| Domain Event | Something that happened in the domain | OrderPlaced, UserRegistered |
| Integration Event | Event for cross-service communication | OrderCreated (with relevant data) |
| Command Event | Request for action (imperative) | ProcessPayment, SendEmail |

### Event-Driven vs Request-Driven

| Aspect | Request-Driven (REST) | Event-Driven |
|--------|----------------------|--------------|
| Coupling | Tight (caller knows callee) | Loose (producer doesn't know consumers) |
| Synchronous | Yes (waits for response) | No (fire and forget) |
| Error handling | Direct (immediate error) | Complex (dead letter queues) |
| Scalability | Limited by slowest service | Each service scales independently |
| Tracing | Simple (call chain) | Complex (correlation IDs needed) |
| Consistency | Strong (immediate) | Eventual |

---

## Pub/Sub Pattern

### Theory
- Publishers send messages to topics (not to specific subscribers)
- Subscribers express interest in topics
- Broker manages routing

### Fan-Out Pattern
```
                           ┌──→ Email Service (sends email)
                           │
Producer ──→ Topic ────────┼──→ SMS Service (sends SMS)
                           │
                           └──→ Push Service (sends push notification)
```

### Use Cases
- Notifications (one event, multiple handlers)
- Event distribution to analytics, audit, monitoring
- Cache invalidation (publish change event, all caches subscribe)
- Microservice communication

---

## Choosing Communication Pattern

### Decision Matrix

| Criteria | REST | gRPC | WebSocket | Message Queue | Event Bus |
|----------|------|------|-----------|---------------|-----------|
| Latency | Medium | Low | Very Low | High (async) | High (async) |
| Coupling | Tight | Tight | Tight | Loose | Very Loose |
| Real-time | No | No | Yes | No | No |
| Reliability | Medium | Medium | Low | High | High |
| Scalability | Medium | High | Hard | Very High | Very High |
| Complexity | Low | Medium | High | Medium | High |

### When to Use What

```
Synchronous (need immediate response):
  → REST: CRUD, client-facing, simple
  → gRPC: Service-to-service, high throughput, streaming

Asynchronous (can tolerate delay):
  → Message Queue: Task processing, work distribution
  → Event Bus: Broadcasting, multiple consumers, decoupling

Real-time (push updates):
  → WebSocket: Chat, live data, gaming
  → SSE (Server-Sent Events): One-way push (simpler than WebSocket)
```

---

## Interview Questions

**Q: When would you choose gRPC over REST for microservice communication?**
> Use gRPC when: services communicate frequently (low latency needed), need streaming, internal services (not browser-facing), strong contracts are important, high throughput needed. Use REST when: external/public APIs, browser clients, need simplicity, caching important (HTTP caching works with REST).

**Q: How do you handle message ordering in a distributed message queue?**
> 1. Partition by key (Kafka): Messages with same key go to same partition → ordered
> 2. Single consumer per partition: Guarantees ordered processing
> 3. Sequence numbers: Consumer detects gaps, reorders
> 4. Accept eventual consistency where possible
> 5. Use FIFO queues (SQS FIFO) for strict ordering

**Q: How do you handle failures in event-driven systems?**
> 1. Dead Letter Queue (DLQ): Failed messages moved for inspection
> 2. Retry with backoff: Exponential backoff + max retries
> 3. Idempotent consumers: Handle duplicate deliveries safely
> 4. Saga pattern: Compensating events for distributed transactions
> 5. Monitoring: Track processing lag, DLQ depth

**Q: How would you scale WebSocket connections to millions of users?**
> 1. Horizontal scaling: Multiple WebSocket servers behind LB (sticky sessions)
> 2. Pub/Sub backbone (Redis): Broadcast across servers
> 3. Connection offloading: Dedicated WebSocket servers (separate from HTTP)
> 4. Room-based routing: Hash room/channel to specific server cluster
> 5. Consider managed services (AWS API Gateway WebSocket)

**Q: What's the difference between choreography and orchestration in event-driven systems?**
> Choreography: Services react to events independently (no central coordinator). Simpler, more decoupled, harder to track overall flow.
> Orchestration: Central coordinator (saga orchestrator) tells each service what to do. Easier to understand flow, single point to add/modify logic, but tighter coupling to orchestrator.

---

## Common Mistakes
- Using synchronous communication for everything (creates coupling, cascade failures)
- Not implementing idempotency in message consumers
- Ignoring message ordering requirements
- Not setting up Dead Letter Queues for failed messages
- Using WebSockets when Server-Sent Events would suffice
- Not considering back-pressure in event-driven systems
- Mixing commands and events (commands should be handled differently)

---

## Best Practices
- Use sync (REST/gRPC) for queries, async (events/queues) for commands
- Implement correlation IDs for distributed tracing
- Design consumers to be idempotent (at-least-once is most practical)
- Set up DLQ and alerting for failed messages
- Use schema registry for event contracts (Avro, protobuf)
- Implement back-pressure mechanisms
- Choose partition keys carefully for message ordering

---

## Production Considerations
- Message retention policies (Kafka: days/weeks, SQS: 14 days max)
- Consumer lag monitoring and alerting
- Dead Letter Queue monitoring
- Schema evolution strategy (backward compatible changes)
- Message size limits (Kafka: 1MB default, SQS: 256KB)
- Network partition handling between services and message broker
- Exactly-once semantics: Kafka transactions or idempotent consumers

---

## Related Topics
- Kafka (deep dive)
- Event Sourcing
- Saga Pattern
- CQRS
- Service Mesh
