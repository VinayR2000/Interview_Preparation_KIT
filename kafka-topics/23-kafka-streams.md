# 23. Kafka Streams ⭐⭐

---

## Theory

**Kafka Streams** is a client library for building real-time stream processing applications. It runs inside your application (no separate cluster needed), provides exactly-once processing, and handles state management.

### Core Abstractions

#### KStream (Event Stream)

```
Unbounded stream of key-value records. Each record is an independent event.
Analogous to an INSERT — every record is a new fact.

KStream<String, OrderEvent> orders = builder.stream("orders");
// Each record: one order event (even for same key)
```

#### KTable (Changelog Stream)

```
Table-like view where each key has ONE current value. Updates replace previous value.
Analogous to an UPDATE — latest value per key.

KTable<String, UserProfile> users = builder.table("user-profiles");
// For key "user-1": only latest profile value matters
```

#### GlobalKTable

```
Like KTable but fully replicated to ALL instances (not partitioned).
Used for small reference data (country codes, configs) that all instances need.

GlobalKTable<String, Country> countries = builder.globalTable("countries");
// Every instance has complete copy — enables foreign-key joins
```

### Stateless Operations

```java
stream
    .filter((key, value) -> value.getAmount() > 100)       // filter
    .mapValues(order -> order.getTotal())                    // transform value
    .map((key, value) -> KeyValue.pair(value.getRegion(), value))  // transform key+value
    .flatMapValues(order -> order.getLineItems())           // one-to-many
    .selectKey((key, value) -> value.getCustomerId())       // re-key
    .to("output-topic");                                     // write to topic
```

### Stateful Operations

```java
// groupBy + count
KTable<String, Long> orderCounts = orders
    .groupBy((key, value) -> value.getCustomerId())
    .count(Materialized.as("order-counts-store"));

// groupBy + reduce
KTable<String, Double> totalByCustomer = orders
    .groupBy((key, value) -> value.getCustomerId())
    .reduce((aggValue, newValue) -> aggValue + newValue.getAmount(),
            Materialized.as("totals-store"));

// groupBy + aggregate
KTable<String, OrderStats> stats = orders
    .groupByKey()
    .aggregate(
        OrderStats::new,                        // initializer
        (key, order, agg) -> agg.add(order),   // aggregator
        Materialized.as("stats-store")
    );
```

### Joins

```java
// KStream-KStream Join (windowed)
KStream<String, EnrichedOrder> enriched = orders.join(
    payments,
    (order, payment) -> new EnrichedOrder(order, payment),
    JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(5)),
    StreamJoined.with(Serdes.String(), orderSerde, paymentSerde)
);

// KStream-KTable Join (lookup)
KStream<String, OrderWithCustomer> result = orders.join(
    customers,  // KTable
    (order, customer) -> new OrderWithCustomer(order, customer)
);

// KStream-GlobalKTable Join
KStream<String, OrderWithCountry> result = orders.join(
    countriesTable,  // GlobalKTable
    (key, value) -> value.getCountryCode(),  // key extractor
    (order, country) -> new OrderWithCountry(order, country)
);
```

### Windowing

```java
// Tumbling window (fixed, non-overlapping)
TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5))
// [0-5min] [5-10min] [10-15min] ...

// Hopping window (fixed, overlapping)
TimeWindows.ofSizeAndGrace(Duration.ofMinutes(5), Duration.ofMinutes(1))
    .advanceBy(Duration.ofMinutes(1))
// [0-5] [1-6] [2-7] ... (advances every 1 min)

// Session window (activity-based)
SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(5))
// Gap of 5min inactivity closes the session

// Usage
orders.groupByKey()
    .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofHours(1)))
    .count()  // count per key per hour
```

### State Stores

```
Kafka Streams maintains LOCAL state stores (RocksDB by default):
- Used for aggregations, joins, windowing
- Backed by changelog topics (for fault tolerance)
- If instance fails → new instance rebuilds state from changelog

State store types:
  - KeyValueStore: simple key-value (for count, reduce, aggregate)
  - WindowStore: time-windowed key-value (for windowed operations)
  - SessionStore: session-windowed key-value

Queryable state stores (Interactive Queries):
  - Can query local state store via API
  - Useful for serving real-time dashboards
```

### Exactly-Once Processing

```java
Properties props = new Properties();
props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, "exactly_once_v2");
// Ensures: input consumption + state update + output production = atomic
```

---

## Diagram

### Kafka Streams Topology

```
                    ┌─────────────────────────────────────────┐
                    │         Kafka Streams Application         │
                    │                                           │
Input Topic         │  ┌──────────────────────────────────┐   │  Output Topic
"orders"  ─────────►│  │         Stream Topology            │   │─────────► "enriched-orders"
                    │  │                                    │   │
                    │  │  Source ──► Filter ──► Map         │   │
                    │  │                         │          │   │
                    │  │                    ┌────┴────┐     │   │
                    │  │                    │ Join    │     │   │
"customers" ───────►│  │  Table ──────────►│(lookup) │─────┼───┤
                    │  │                    └─────────┘     │   │
                    │  │                                    │   │
                    │  │  State Store (RocksDB)             │   │
                    │  │  ┌────────────────────────┐       │   │
                    │  │  │ customer-id → profile   │       │   │
                    │  │  └────────────────────────┘       │   │
                    │  └──────────────────────────────────┘   │
                    └─────────────────────────────────────────┘
                    
                    Changelog topic (automatic backup of state store)
                    "__app-customer-store-changelog"
```

---

## Interview Questions

### Q1: What is the difference between KStream and KTable?

**A:**
- **KStream:** Each record is an event (insert semantics). Key "user-1" can appear many times — each is a separate event. Used for event streams (orders, clicks, logs).
- **KTable:** Each record is an update (upsert semantics). Key "user-1" has only one current value — newer records replace older ones. Used for current state (user profiles, account balances, configs).
- Internally: KTable is backed by a compacted changelog topic.

### Q2: Why doesn't Kafka Streams need a separate cluster?

**A:** Kafka Streams is a library, not a framework. It runs inside your application JVM (just add dependency). Scaling = deploy more instances of your app. State is stored locally (RocksDB) with changelog backup in Kafka. Consumer group protocol handles partition assignment. No separate infrastructure to manage (unlike Flink, Spark Streaming). Trade-off: simpler operations, but limited to Kafka-in/Kafka-out patterns.

### Q3: How does Kafka Streams handle state store failures?

**A:** State stores are backed by **changelog topics** (compacted Kafka topics). If an instance fails:
1. Partitions reassigned to another instance via consumer group rebalance
2. New instance rebuilds state store by replaying changelog topic
3. Once caught up, processing resumes
4. Standby replicas (optional) pre-build state for faster failover.

---

## Related Topics

- [22. Kafka APIs](./22-kafka-apis.md)
- [20. Kafka Transactions](./20-kafka-transactions.md)
- [33. Kafka Design Patterns](./33-kafka-design-patterns.md)
