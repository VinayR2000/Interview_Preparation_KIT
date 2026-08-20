# High-Level Architecture Patterns

## Layered Architecture (N-Tier)

### Theory
- Organizes code into horizontal layers, each with a specific responsibility
- Each layer only communicates with adjacent layers
- Most traditional and well-understood architecture

### Diagram
```
┌──────────────────────────────────────┐
│         Presentation Layer           │  (UI, Controllers, API endpoints)
├──────────────────────────────────────┤
│          Business Layer              │  (Services, Business Logic, Validation)
├──────────────────────────────────────┤
│         Persistence Layer            │  (Repositories, DAO, ORM)
├──────────────────────────────────────┤
│          Database Layer              │  (DB, File system, External services)
└──────────────────────────────────────┘
```

### Pros and Cons
| Pros | Cons |
|------|------|
| Simple, well-understood | Monolithic tendency |
| Separation of concerns | Changes often span all layers |
| Easy to test each layer | Can become a "layered monolith" |
| Team familiarity | Performance (pass-through layers) |

---

## Microservices Architecture

### Theory
- System decomposed into small, independent services
- Each service owns its data and business logic
- Services communicate via APIs or events
- Independently deployable and scalable

### Diagram
```
┌──────┐    ┌──────────┐    ┌──────────────┐    ┌──────┐
│Client│───→│API Gateway│───→│ User Service │←──→│UserDB│
└──────┘    └────┬─────┘    └──────────────┘    └──────┘
                 │
                 ├──────────→┌──────────────┐    ┌──────┐
                 │           │Order Service │←──→│OrdDB │
                 │           └──────┬───────┘    └──────┘
                 │                  │ (event)
                 │                  ▼
                 │           ┌──────────────┐    ┌──────┐
                 └──────────→│Payment Svc   │←──→│PayDB │
                             └──────────────┘    └──────┘
```

### Microservices Characteristics
- Single responsibility (one business capability per service)
- Own database (database per service)
- Independently deployable
- Technology agnostic (polyglot)
- Organized around business capabilities
- Decentralized governance

### Monolith vs Microservices

| Aspect | Monolith | Microservices |
|--------|----------|---------------|
| Deployment | All or nothing | Independent per service |
| Scaling | Scale entire app | Scale individual services |
| Technology | Single stack | Polyglot possible |
| Data | Shared database | Database per service |
| Complexity | In code | In infrastructure |
| Team | One large team | Small, autonomous teams |
| Latency | In-process calls | Network calls |
| Transactions | ACID (simple) | Saga/eventual consistency |

---

## Event-Driven Architecture

### Theory
- Components communicate through events
- Producers emit events, consumers react to them
- Asynchronous, loosely coupled
- Events are immutable facts (things that happened)

### Patterns

#### Event Notification
```
Service A ──"OrderCreated"──→ Event Bus ──→ Service B (reacts)
                                       ──→ Service C (reacts)
```
- Just a signal that something happened
- Consumer may need to query source for details

#### Event-Carried State Transfer
```
Service A ──"OrderCreated{id, items, total, address}"──→ Consumers
```
- Event carries all data consumers need
- No need to query back to source
- Consumers maintain local copies

#### Event Sourcing
```
Instead of storing current state:
  Account: {balance: 500}

Store sequence of events:
  AccountCreated{initial: 1000}
  MoneyWithdrawn{amount: 300}
  MoneyDeposited{amount: 200}
  MoneyWithdrawn{amount: 400}
  
Current state = replay all events
```

---

## Serverless Architecture

### Theory
- Functions as a service (FaaS)
- No server management
- Auto-scales to zero (pay per execution)
- Event-triggered (HTTP, queue, schedule, file upload)

### Diagram
```
┌─────────┐    ┌───────────┐    ┌──────────┐    ┌─────────┐
│  Client │───→│API Gateway│───→│ Lambda   │───→│DynamoDB │
└─────────┘    └───────────┘    │ Function │    └─────────┘
                                └──────────┘
                                     │
                                     ▼
┌──────────┐    ┌──────────┐    ┌──────────┐
│  S3      │───→│ Lambda   │───→│  SQS     │
│ (upload) │    │ (process)│    │ (queue)  │
└──────────┘    └──────────┘    └──────────┘
```

### Serverless Pros/Cons
| Pros | Cons |
|------|------|
| No infra management | Cold start latency |
| Auto-scaling | Vendor lock-in |
| Pay per use | Limited execution time (15 min) |
| Fast to deploy | Debugging is harder |
| Built-in HA | Stateless (no local state) |

### When to Use
- Event processing (file uploads, webhooks)
- Scheduled tasks (cron jobs)
- Low/unpredictable traffic APIs
- Prototyping and MVPs
- Background processing

---

## CQRS (Command Query Responsibility Segregation)

### Theory
- Separate models for reading and writing data
- Commands: Change state (write) — validated, processed
- Queries: Return data (read) — optimized views

### Diagram
```
┌─────────┐                      ┌─────────────────┐
│Commands │─→ Command Handler ──→│  Write Model    │
│(Create, │   (Validate, Apply)  │  (Normalized)   │
│ Update) │                      │  PostgreSQL     │
└─────────┘                      └────────┬────────┘
                                          │ Sync/Events
                                          ▼
┌─────────┐                      ┌─────────────────┐
│ Queries │←─── Query Handler ←──│  Read Model     │
│(Search, │    (Optimized)       │ (Denormalized)  │
│ List)   │                      │ Elasticsearch   │
└─────────┘                      └─────────────────┘
```

### When to Use CQRS
- Read and write workloads differ significantly
- Complex queries that don't map well to write schema
- Need different scaling for reads vs writes
- Combined with Event Sourcing

---

## Event Sourcing

### Theory
- Store state as a sequence of events (not current state)
- Current state derived by replaying events
- Complete audit trail — know exactly what happened and when
- Can rebuild state at any point in time

### Event Store
```
Stream: Order-12345
┌─────────────────────────────────────────────────────┐
│ Seq │ Event              │ Data                      │ Timestamp │
├─────┼────────────────────┼───────────────────────────┼───────────┤
│  1  │ OrderCreated       │ {items: [...], userId: 1} │ 10:00:01  │
│  2  │ PaymentReceived    │ {amount: 99.99}           │ 10:00:05  │
│  3  │ OrderShipped       │ {trackingId: "ABC123"}    │ 10:02:30  │
│  4  │ OrderDelivered     │ {signature: "..."}        │ 11:45:00  │
└─────┴────────────────────┴───────────────────────────┴───────────┘

Current state = replay(Event1 + Event2 + Event3 + Event4)
```

### Event Sourcing + CQRS
```
Commands ──→ Event Store (append only) ──→ Projections ──→ Read Models
                                                              │
                                    ┌─────────────────────────┤
                                    ▼                         ▼
                              Order Summary View       Analytics View
                              (for customers)          (for admins)
```

---

## Hexagonal Architecture (Ports & Adapters)

### Theory
- Application core is isolated from external concerns
- Ports: Interfaces defined by the application
- Adapters: Implementations that connect to external systems
- Dependency flows inward (outer layers depend on inner)

### Diagram
```
        ┌──────────────────────────────────────────┐
        │              Adapters (Outside)           │
        │  ┌──────┐  ┌──────┐  ┌──────────────┐   │
        │  │REST  │  │Kafka │  │  PostgreSQL   │   │
        │  │Ctrlr │  │Listner│  │  Repository  │   │
        │  └──┬───┘  └──┬───┘  └──────┬───────┘   │
        │     │          │             │           │
        │  ───┼──────────┼─────────────┼────       │
        │     │   Ports (Interfaces)   │           │
        │     ▼          ▼             ▼           │
        │  ┌──────────────────────────────────┐    │
        │  │         Application Core          │    │
        │  │    (Domain Logic, Use Cases)      │    │
        │  │    No dependency on adapters      │    │
        │  └──────────────────────────────────┘    │
        └──────────────────────────────────────────┘
```

### Benefits
- Testable: Mock adapters for testing business logic
- Flexible: Swap database, UI, or messaging without changing core
- Clear boundaries: Domain logic never depends on infrastructure

---

## Clean Architecture

### Theory
- Similar to Hexagonal but with explicit layer rings
- Dependency Rule: Dependencies point inward only
- Inner layers know nothing about outer layers

### Layers (Inside → Outside)
```
┌─────────────────────────────────────────────────┐
│                  Frameworks & Drivers            │ (Web, DB, UI)
│  ┌─────────────────────────────────────────┐    │
│  │            Interface Adapters            │    │ (Controllers, Gateways)
│  │  ┌─────────────────────────────────┐    │    │
│  │  │        Application Layer        │    │    │ (Use Cases)
│  │  │  ┌─────────────────────────┐    │    │    │
│  │  │  │     Domain Layer        │    │    │    │ (Entities, Value Objects)
│  │  │  └─────────────────────────┘    │    │    │
│  │  └─────────────────────────────────┘    │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

---

## Architecture Comparison

| Pattern | Scale | Complexity | Best For |
|---------|-------|------------|----------|
| Layered/N-tier | Small-Medium | Low | Traditional apps, small teams |
| Microservices | Large | High | Large teams, independent scaling |
| Event-Driven | Medium-Large | High | Async workflows, decoupling |
| Serverless | Variable | Medium | Event processing, variable load |
| CQRS | Medium-Large | High | Read/write asymmetry |
| Hexagonal/Clean | Any | Medium | Testability, flexibility |

---

## Interview Questions

**Q: When would you choose microservices over a monolith?**
> Choose microservices when: multiple teams need independent deployment, services have different scaling needs, technology diversity is needed, system is large enough to justify overhead. Keep monolith when: small team (<10 engineers), starting a new project (start monolith, extract later), services are tightly coupled.

**Q: How do you handle distributed transactions in microservices?**
> 1. Saga Pattern: Series of local transactions with compensating actions on failure
>    - Choreography: Services react to events
>    - Orchestration: Central coordinator manages flow
> 2. Outbox Pattern: Write event to outbox table in same DB transaction
> 3. Two-Phase Commit (2PC): Avoid if possible (blocks, doesn't scale)
> 4. Accept eventual consistency where possible

**Q: What's the difference between event sourcing and event-driven architecture?**
> Event-driven: Services communicate via events (integration pattern). Events can be ephemeral.
> Event sourcing: State stored as events (persistence pattern). Events are the source of truth, permanent. You can have event-driven without event sourcing, and event sourcing without event-driven (though they often combine well).

**Q: How do you decide service boundaries in microservices?**
> 1. Domain-Driven Design (DDD): Bounded contexts define service boundaries
> 2. Business capabilities: Each service = one business capability
> 3. Data ownership: Services that share data tightly should probably be one service
> 4. Team structure (Conway's Law): Service boundaries mirror team boundaries
> 5. Change frequency: Things that change together stay together

**Q: What are the downsides of event sourcing?**
> 1. Event schema evolution is complex (versioning)
> 2. Querying current state requires replaying or projections
> 3. Storage grows unbounded (need snapshots for optimization)
> 4. Eventual consistency in read models
> 5. Debugging is harder (state derived from events, not visible directly)
> 6. Team unfamiliarity — steep learning curve

---

## Common Mistakes
- Choosing microservices for a small project (premature complexity)
- Shared database between microservices (defeats the purpose)
- Not defining clear service boundaries (distributed monolith)
- Using event sourcing everywhere (not all domains need it)
- Ignoring the operational overhead of microservices
- Building a "clean architecture" with unnecessary abstractions for simple CRUD

---

## Best Practices
- Start with a well-structured monolith, extract services as needed
- Use DDD to find natural service boundaries
- One team per service, one service per team
- Prefer choreography for simple flows, orchestration for complex
- Use event sourcing only where audit trail and temporal queries add value
- Apply hexagonal/clean architecture within each microservice
- Define clear API contracts between services

---

## Production Considerations
- Service mesh for cross-cutting concerns (Istio, Linkerd)
- Distributed tracing (Jaeger, Zipkin) for request flow visibility
- Centralized logging (ELK stack, CloudWatch)
- Circuit breakers for inter-service communication
- Contract testing between services
- Automated deployment pipelines per service
- Feature flags for gradual rollouts
- Chaos engineering to test resilience

---

## Related Topics
- Domain-Driven Design (DDD)
- Saga Pattern
- Service Mesh
- API Gateway
- Circuit Breaker Pattern
- Event Sourcing (deep dive)
