# Topic 26: Database Architecture & System Design

## Theory

### Database Architecture Patterns

```
┌─────────────────────────────────────────────────────────────────┐
│             DATABASE ARCHITECTURE SPECTRUM                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  SIMPLE                                           COMPLEX        │
│  ──────────────────────────────────────────────────────────      │
│                                                                  │
│  Single DB    Read         Sharded      CQRS       Event        │
│  (monolith)  Replicas     Database     Pattern    Sourcing      │
│     │           │             │           │           │          │
│     ▼           ▼             ▼           ▼           ▼          │
│  1 server    1 write +     Multiple    Separate   Events as     │
│              N readers     databases   read/write source of     │
│                            by shard    models     truth         │
│                            key                                   │
│                                                                  │
│  WHEN TO USE:                                                    │
│  • Start simple (single DB)                                      │
│  • Add read replicas when reads >> writes                        │
│  • Shard when single DB can't handle the data volume             │
│  • CQRS when read/write patterns are fundamentally different     │
│  • Event sourcing when audit trail is business-critical          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Read-Heavy Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│             READ-HEAVY ARCHITECTURE                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│                    ┌──────────┐                                   │
│           ┌───────│  Client  │───────┐                           │
│           │       └──────────┘       │                           │
│           │                          │                           │
│      ┌────▼────┐              ┌──────▼──────┐                   │
│      │ CDN/    │              │  API Gateway │                   │
│      │ Static  │              │  (routing)   │                   │
│      └─────────┘              └──────┬───────┘                   │
│                                      │                           │
│                          ┌───────────┼───────────┐               │
│                          │           │           │               │
│                     ┌────▼───┐  ┌────▼───┐  ┌───▼────┐          │
│                     │Service │  │Service │  │Service │          │
│                     │   A    │  │   B    │  │   C    │          │
│                     └────┬───┘  └────┬───┘  └───┬────┘          │
│                          │           │           │               │
│                     ┌────▼───────────▼───────────▼────┐          │
│                     │         Redis Cache              │          │
│                     │   (cache-aside pattern)         │          │
│                     └────────────┬────────────────────┘          │
│                                  │ cache miss                    │
│                     ┌────────────▼────────────────────┐          │
│                     │     Read Replicas (3x)          │          │
│                     │  ┌────────┐┌────────┐┌────────┐│          │
│                     │  │Replica1││Replica2││Replica3││          │
│                     │  └────────┘└────────┘└────────┘│          │
│                     └────────────────────────────────┘          │
│                                  ▲                               │
│                                  │ replication                   │
│                     ┌────────────┴────────────────────┐          │
│                     │     Primary (writes only)       │          │
│                     └─────────────────────────────────┘          │
│                                                                  │
│  STRATEGY:                                                       │
│  1. CDN for static content                                       │
│  2. Redis for hot data (TTL-based cache)                         │
│  3. Read replicas for cache misses                               │
│  4. Primary only for writes                                      │
│  5. 90%+ requests never hit primary DB                           │
└─────────────────────────────────────────────────────────────────┘
```

### Write-Heavy Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│             WRITE-HEAVY ARCHITECTURE                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│     ┌──────────┐                                                 │
│     │  Client  │                                                 │
│     └────┬─────┘                                                 │
│          │                                                       │
│     ┌────▼─────────────────────────────────────┐                │
│     │           Message Queue (Kafka)           │                │
│     │  (buffer writes, handle back-pressure)   │                │
│     └────┬──────────────┬──────────────┬───────┘                │
│          │              │              │                          │
│     ┌────▼────┐    ┌────▼────┐    ┌────▼────┐                   │
│     │Consumer │    │Consumer │    │Consumer │                   │
│     │  Group  │    │  Group  │    │  Group  │                   │
│     └────┬────┘    └────┬────┘    └────┬────┘                   │
│          │              │              │                          │
│     ┌────▼────┐    ┌────▼────┐    ┌────▼────┐                   │
│     │ Shard 0 │    │ Shard 1 │    │ Shard 2 │                   │
│     │(users   │    │(users   │    │(users   │                   │
│     │ 0-33%)  │    │ 33-66%) │    │ 66-100%)│                   │
│     └─────────┘    └─────────┘    └─────────┘                   │
│                                                                  │
│  STRATEGY:                                                       │
│  1. Queue writes (buffer with Kafka/SQS)                         │
│  2. Batch writes from consumers                                  │
│  3. Shard database by write-heavy key                            │
│  4. Use append-only storage (time-series)                        │
│  5. Consider eventual consistency for reads                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## CQRS (Command Query Responsibility Segregation)

### Internal Working

```
┌─────────────────────────────────────────────────────────────────┐
│                    CQRS PATTERN                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│                    ┌──────────┐                                   │
│                    │  Client  │                                   │
│                    └────┬─────┘                                   │
│                ┌────────┴────────┐                                │
│                │                 │                                │
│         ┌──────▼──────┐   ┌─────▼──────┐                        │
│         │  COMMAND     │   │   QUERY    │                        │
│         │  (Write)     │   │   (Read)   │                        │
│         └──────┬───────┘   └─────┬──────┘                        │
│                │                  │                               │
│         ┌──────▼───────┐   ┌─────▼──────┐                        │
│         │ Write Model  │   │ Read Model │                        │
│         │ (normalized, │   │(denormalized│                       │
│         │  consistent) │   │ optimized   │                       │
│         └──────┬───────┘   │ for queries)│                       │
│                │           └─────▲──────┘                        │
│                │                  │                               │
│         ┌──────▼───────┐         │ async sync                    │
│         │  Write DB    │─────────┘                               │
│         │ (PostgreSQL) │  (events/CDC/polling)                   │
│         └──────────────┘                                         │
│                                                                  │
│  EXAMPLE:                                                        │
│  Write Side: Orders table (normalized, ACID)                     │
│  Read Side: Order dashboard view (denormalized, fast)            │
│                                                                  │
│  Write: INSERT INTO orders (...) → event published               │
│  Event consumer: Updates denormalized read model                 │
│  Read: SELECT from pre-joined, pre-aggregated view              │
│                                                                  │
│  TRADE-OFFS:                                                     │
│  ✓ Independent scaling of reads vs writes                       │
│  ✓ Optimized models for each use case                           │
│  ✗ Eventual consistency between models                          │
│  ✗ Increased complexity                                          │
│  ✗ Data synchronization challenges                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Event Sourcing

### Theory

```
┌─────────────────────────────────────────────────────────────────┐
│                  EVENT SOURCING                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  TRADITIONAL:                                                    │
│  ┌──────────┐  UPDATE  ┌──────────────────────────┐             │
│  │  App     │─────────▶│ accounts: balance = 500   │             │
│  └──────────┘          └──────────────────────────┘             │
│  (Only current state stored. History lost.)                      │
│                                                                  │
│  EVENT SOURCING:                                                 │
│  ┌──────────┐  APPEND  ┌──────────────────────────────────┐     │
│  │  App     │─────────▶│ Event Store (append-only):        │     │
│  └──────────┘          │  1. AccountCreated(id=1, $1000)   │     │
│                         │  2. MoneyDeposited(id=1, $500)    │     │
│                         │  3. MoneyWithdrawn(id=1, $200)    │     │
│                         │  4. MoneyWithdrawn(id=1, $800)    │     │
│                         └──────────────────────────────────┘     │
│                                                                  │
│  Current state = replay all events:                              │
│    $1000 + $500 - $200 - $800 = $500 ✓                          │
│                                                                  │
│  BENEFITS:                                                       │
│  • Complete audit trail                                          │
│  • Temporal queries ("what was balance on March 15?")            │
│  • Event replay (rebuild state, fix bugs retroactively)          │
│  • Natural fit for event-driven architectures                    │
│                                                                  │
│  CHALLENGES:                                                     │
│  • Eventual consistency                                          │
│  • Event schema evolution (versioning)                           │
│  • Performance of replay (use snapshots)                         │
│  • Complexity (unfamiliar paradigm)                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Polyglot Persistence

```
┌─────────────────────────────────────────────────────────────────┐
│              POLYGLOT PERSISTENCE                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Use the RIGHT database for each use case:                       │
│                                                                  │
│  ┌───────────────┬────────────────┬──────────────────────┐      │
│  │ Use Case      │ Database       │ Why                  │      │
│  ├───────────────┼────────────────┼──────────────────────┤      │
│  │ User accounts │ PostgreSQL     │ ACID, relationships  │      │
│  │ Sessions      │ Redis          │ Fast, TTL, key-value │      │
│  │ Product search│ Elasticsearch  │ Full-text, faceted   │      │
│  │ Activity feed │ Cassandra      │ High write, timeseries│     │
│  │ Social graph  │ Neo4j          │ Relationship queries │      │
│  │ File metadata │ MongoDB        │ Flexible schema      │      │
│  │ Analytics     │ ClickHouse     │ Column-oriented, fast│      │
│  │ Cache         │ Redis/Memcached│ Sub-ms latency       │      │
│  └───────────────┴────────────────┴──────────────────────┘      │
│                                                                  │
│  EXAMPLE — E-commerce:                                           │
│  • PostgreSQL: Orders, payments, inventory (ACID required)       │
│  • Redis: Shopping cart, session data (speed)                    │
│  • Elasticsearch: Product catalog search (full-text)             │
│  • Kafka: Event streaming between services                       │
│  • S3: Product images, files                                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Scaling Strategies

### Vertical vs Horizontal Scaling

```
VERTICAL SCALING (Scale Up):
─────────────────────────────
  ┌─────────┐      ┌─────────────────┐
  │ 4 CPU   │  →   │ 64 CPU          │
  │ 16GB RAM│      │ 512GB RAM       │
  │ 500GB   │      │ 10TB NVMe       │
  └─────────┘      └─────────────────┘
  
  ✓ Simple — no code changes
  ✓ No distributed complexity
  ✗ Hardware limits (can't scale forever)
  ✗ Single point of failure
  ✗ Expensive at high end

HORIZONTAL SCALING (Scale Out):
───────────────────────────────
  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
  │ Node 1  │   │ Node 2  │   │ Node 3  │   │ Node N  │
  │ (shard) │   │ (shard) │   │ (shard) │   │ (shard) │
  └─────────┘   └─────────┘   └─────────┘   └─────────┘
  
  ✓ Near-linear scalability
  ✓ Fault tolerance (no single point of failure)
  ✓ Cost-effective with commodity hardware
  ✗ Distributed transactions complex
  ✗ Cross-shard queries expensive
  ✗ Operational complexity
```

### Database Scaling Decision Tree

```
Problem: DB can't handle the load
│
├── CPU-bound? (complex queries)
│   ├── Optimize queries/indexes
│   ├── Add read replicas
│   └── Vertical scale (more cores)
│
├── Memory-bound? (working set > RAM)
│   ├── Add application caching (Redis)
│   ├── Vertical scale (more RAM)
│   └── Partition/archive old data
│
├── I/O-bound? (slow disk)
│   ├── Move to SSD/NVMe
│   ├── Increase shared_buffers
│   └── Denormalize to reduce JOINs
│
├── Connection-bound? (too many connections)
│   ├── Add connection pooler (PgBouncer)
│   ├── Reduce pool size per service
│   └── Queue/batch requests
│
└── Data-volume-bound? (too much data)
    ├── Partition tables (time-based)
    ├── Archive old data
    └── Shard database (last resort)
```

---

## Code — CQRS Implementation

```java
// WRITE SIDE — Command Handler
@Service
@Transactional
public class OrderCommandService {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public Long createOrder(CreateOrderCommand command) {
        Order order = Order.builder()
            .customerId(command.getCustomerId())
            .items(command.getItems())
            .status(OrderStatus.CREATED)
            .build();
        
        order = orderRepository.save(order);
        
        // Publish event for read model sync
        eventPublisher.publishEvent(new OrderCreatedEvent(
            order.getId(), order.getCustomerId(), 
            order.getTotalAmount(), order.getCreatedAt()
        ));
        
        return order.getId();
    }
}

// READ SIDE — Query Handler
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    @Autowired
    private OrderReadRepository readRepo; // Different repository/model

    public Page<OrderSummaryDTO> getOrderDashboard(Long customerId, Pageable pageable) {
        // Reads from denormalized, pre-joined read model
        return readRepo.findByCustomerId(customerId, pageable);
    }
    
    public OrderStatsDTO getOrderStats(Long customerId) {
        // Pre-aggregated stats (no expensive runtime aggregation)
        return readRepo.getStats(customerId);
    }
}

// EVENT HANDLER — Syncs read model
@Component
public class OrderEventHandler {

    @Autowired
    private OrderReadRepository readRepo;

    @EventListener
    @Async
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        OrderReadModel readModel = OrderReadModel.builder()
            .orderId(event.getOrderId())
            .customerId(event.getCustomerId())
            .customerName(customerService.getName(event.getCustomerId()))
            .totalAmount(event.getTotalAmount())
            .createdAt(event.getCreatedAt())
            .build();
        
        readRepo.save(readModel);
    }
}
```

### Outbox Pattern Implementation

```java
// Transactional Outbox — ensures event is published atomically with data change
@Service
public class OrderService {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // Business operation
        Order order = orderRepository.save(new Order(request));
        
        // Write event to outbox table (SAME transaction)
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(order.getId().toString())
            .eventType("ORDER_CREATED")
            .payload(objectMapper.writeValueAsString(new OrderCreatedPayload(order)))
            .createdAt(Instant.now())
            .processed(false)
            .build();
        outboxRepository.save(event);
        
        return order;
        // On commit: both order AND outbox event are persisted atomically
    }
}

// Outbox Publisher — polls outbox table and publishes to Kafka
@Component
@Scheduled(fixedDelay = 1000) // Every 1 second
public class OutboxPublisher {

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxRepository
            .findByProcessedFalseOrderByCreatedAt();
        
        for (OutboxEvent event : events) {
            kafkaTemplate.send(event.getAggregateType(), 
                               event.getAggregateId(), 
                               event.getPayload());
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
        }
    }
}
```

---

## Dry Run — Read Replica Routing

```
Scenario: @Transactional(readOnly = true) method called

1. Controller receives GET /api/orders?customerId=5
2. Calls: orderService.getOrders(5, pageable)
3. Spring proxy intercepts:
   → @Transactional(readOnly = true) detected
   → TransactionSynchronizationManager.setCurrentTransactionReadOnly(true)
   → DataSource.getConnection() called

4. AbstractRoutingDataSource.determineCurrentLookupKey():
   → Checks: isCurrentTransactionReadOnly() → true
   → Returns: DataSourceType.READ_REPLICA

5. Connection obtained from read-replica pool
   → Routes to: replica-1.db.company.com

6. Query executes on REPLICA (not primary)
   → SELECT * FROM orders WHERE customer_id = 5 ORDER BY created_at DESC LIMIT 20

7. Results returned to service

8. Transaction completes:
   → Connection returned to read-replica pool
   → No commit needed (read-only)

BENEFIT: Primary DB freed up for write operations
NOTE: Read replica may have slight lag (async replication)
```

---

## Interview Questions and Answers

### Q1: How would you design a database architecture for a system with 10M users, 100K concurrent users?

**Answer:**

Layer 1 — **Caching**: Redis cluster for session data, hot content (90% of reads served here)

Layer 2 — **Read Replicas**: 3-5 read replicas for cache misses

Layer 3 — **Primary DB**: PostgreSQL primary for writes only

Layer 4 — **Partitioning**: Time-partition large tables (orders by month)

Layer 5 — **Sharding** (if single primary can't handle writes): Shard by user_id

Supporting:
- PgBouncer for connection pooling
- Kafka for async event processing
- CDN for static content
- Background jobs for heavy aggregations (pre-computed dashboards)

### Q2: Explain CQRS and when you would use it.

**Answer:**

CQRS separates read and write operations into different models:
- **Write model**: Normalized, optimized for consistency (ACID)
- **Read model**: Denormalized, optimized for query performance

When to use:
- Read and write patterns differ significantly
- Read load >> write load
- Complex reporting/aggregation requirements
- Different scaling needs for reads vs writes

When NOT to use:
- Simple CRUD applications
- When eventual consistency is unacceptable
- Small teams (added complexity)

### Q3: What is the Outbox Pattern and why is it needed?

**Answer:**

**Problem**: You need to save to database AND publish to message broker atomically. Dual writes (save + publish) can fail partially — the DB write succeeds but the message publish fails, or vice versa.

**Solution**: Write the event to an "outbox" table in the SAME database transaction. A separate process polls the outbox table and publishes to the message broker.

```
Without Outbox (dual write — UNSAFE):
  1. Save order to DB    ✓
  2. Publish to Kafka    ✗ (network failure)
  Result: Order exists but no event published!

With Outbox (SAFE):
  1. Save order + outbox event in SAME transaction  ✓ (atomic)
  2. Separate process: read outbox → publish to Kafka
  3. Mark outbox event as processed
  Result: At-least-once delivery guaranteed
```

### Q4: How do you handle data consistency across microservices?

**Answer:**

1. **Saga Pattern**: Orchestrate distributed transactions as a sequence of local transactions. Each service has compensating action for rollback.

2. **Eventual Consistency**: Accept temporary inconsistency. Use events to propagate changes.

3. **Outbox + CDC**: Capture database changes and propagate via events.

4. **Idempotency**: Design all operations to be safely retried.

```
Order Saga Example:
  1. Order Service: Create order (PENDING)
  2. Payment Service: Charge payment
     → Success: proceed
     → Failure: Order Service → Cancel order (compensate)
  3. Inventory Service: Reserve stock
     → Success: Order → CONFIRMED
     → Failure: Payment Service → Refund (compensate)
                 Order Service → Cancel order (compensate)
```

### Q5: When would you choose PostgreSQL vs a NoSQL database?

**Answer:**

Choose **PostgreSQL** when:
- Data has relationships (JOINs needed)
- ACID transactions required
- Complex queries, aggregations
- Schema is known and stable
- Strong consistency required

Choose **NoSQL** when:
- Massive write throughput (Cassandra for time-series)
- Flexible/evolving schema (MongoDB)
- Simple key-value access pattern (Redis, DynamoDB)
- Graph relationships (Neo4j)
- Full-text search with facets (Elasticsearch)

Most production systems use **both** (polyglot persistence).

---

## Follow-up Questions and Answers

### Q: What is CDC (Change Data Capture) and how does it work?

**Answer:**

CDC captures row-level changes from the database transaction log (WAL in PostgreSQL) and streams them to other systems.

Tools: Debezium (most popular), AWS DMS, Maxwell

```
PostgreSQL WAL → Debezium → Kafka → Consumers
                    │
                    └── Captures: INSERT, UPDATE, DELETE events
                        with before/after row state
```

Use cases:
- Sync read model in CQRS
- Real-time data warehouse updates
- Cross-service data synchronization
- Cache invalidation

### Q: How do you handle database failover?

**Answer:**

1. **Health checks**: Monitor primary with heartbeats
2. **Automatic failover**: Promote replica to primary (Patroni, pg_auto_failover)
3. **Connection routing**: Update DNS or use proxy (PgBouncer/HAProxy)
4. **Application retry**: Connection errors trigger retry with backoff
5. **Split-brain prevention**: Use consensus (etcd/ZooKeeper) for leader election

RPO/RTO targets:
- RPO (Recovery Point Objective): Max data loss acceptable → synchronous replication for RPO=0
- RTO (Recovery Time Objective): Max downtime acceptable → automated failover for RTO < 30s

---

## Common Mistakes

| Mistake | Impact | Fix |
|---|---|---|
| Premature sharding | Operational nightmare | Scale vertically first, shard as last resort |
| No caching layer | Every request hits DB | Add Redis for hot data |
| Dual writes | Data inconsistency | Use outbox pattern |
| Ignoring replication lag | Stale reads after writes | Read-your-writes pattern |
| Over-engineering | Complexity without benefit | Start simple, evolve |
| Single point of failure | Complete outage | Add redundancy (replicas, failover) |
| No read/write split | Primary overloaded | Route reads to replicas |
| Choosing NoSQL for everything | Lost ACID guarantees | Use relational for transactional data |

---

## Best Practices

1. **Start simple** — single PostgreSQL handles more than you think (1M+ rows easily)
2. **Add caching early** — Redis eliminates 90%+ of DB reads
3. **Use read replicas** before sharding
4. **Implement connection pooling** from day one
5. **Design for failure** — every component will fail eventually
6. **Use the outbox pattern** for reliable event publishing
7. **Partition large tables** before they become unmanageable
8. **Monitor everything** — you can't fix what you can't see
9. **Automate failover** — manual failover means longer outages
10. **Document data flow** — know exactly how data moves through your system

---

## Production Considerations

### High Availability Setup

```
TYPICAL PRODUCTION SETUP:
─────────────────────────

  App Layer:        3+ app instances (Kubernetes)
  Connection Pool:  PgBouncer (per-pod or centralized)
  Write DB:         Primary PostgreSQL (synchronous replication)
  Read DB:          2-3 async replicas
  Cache:            Redis Cluster (3 masters + 3 replicas)
  Queue:            Kafka Cluster (3+ brokers)
  Monitoring:       Prometheus + Grafana
  Failover:         Patroni (automatic PostgreSQL failover)
  Backup:           pg_basebackup + WAL archiving to S3
  
  SLA Target:       99.99% availability (52 min downtime/year)
```

### Cost-Performance Matrix

| Solution | Monthly Cost | Reads/sec | Writes/sec | Complexity |
|---|---|---|---|---|
| Single PostgreSQL (16GB) | $200 | 10K | 5K | Low |
| + Redis cache | $350 | 100K | 5K | Low |
| + 2 read replicas | $750 | 50K direct | 5K | Medium |
| + PgBouncer | $800 | 50K | 10K | Medium |
| Sharded (3 shards) | $2000 | 150K | 15K | High |
| Full CQRS + ES | $3000+ | 500K | 50K | Very High |

---

## Related Topics

- Topic 18: Partitioning, Replication, Sharding
- Topic 21: Locking, Concurrency & MVCC
- Topic 22: PostgreSQL Specifics
- Topic 25: Advanced Database Performance
- Topic 28: SQL + Microservices
