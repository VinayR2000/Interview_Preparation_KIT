# DynamoDB

## Theory

DynamoDB is a fully managed NoSQL key-value and document database. Serverless, single-digit millisecond latency at any scale. No servers to manage, auto-scales, built-in replication across 3 AZs.

---

## Internal Working

### Data Model
```
Table: Orders
├── Partition Key: userId (required — determines data distribution)
├── Sort Key: orderId (optional — enables range queries within partition)
├── Attributes: orderDate, total, status, items (flexible schema)
│
├── Item: { userId: "U001", orderId: "O100", total: 59.99, status: "SHIPPED" }
├── Item: { userId: "U001", orderId: "O101", total: 120.00, status: "PENDING" }
└── Item: { userId: "U002", orderId: "O102", total: 35.50, status: "DELIVERED" }
```

### Access Patterns

| Operation | Description | Requirement |
|-----------|-------------|-------------|
| GetItem | Single item by key | Partition key (+ sort key) |
| Query | Multiple items, same partition | Partition key + sort key condition |
| Scan | All items in table | Avoid! (expensive, reads everything) |
| PutItem | Create/replace item | Full primary key |
| UpdateItem | Modify attributes | Full primary key |
| DeleteItem | Remove item | Full primary key |

### Capacity Modes

| Mode | Billing | Use Case |
|------|---------|----------|
| On-Demand | Per request | Unpredictable traffic, new apps |
| Provisioned | Per RCU/WCU | Predictable, steady traffic |

### Secondary Indexes

| Index Type | Description | Use Case |
|-----------|-------------|----------|
| GSI (Global Secondary Index) | Different partition key | Query by non-key attributes |
| LSI (Local Secondary Index) | Same partition key, different sort key | Alternative sort within partition |

---

## Interview Questions and Answers

**Q: When would you choose DynamoDB instead of PostgreSQL (RDS)?**
> DynamoDB when: (1) Simple access patterns (key-value lookups, single-table queries), (2) Need extreme scale (millions of requests/sec), (3) Serverless architecture, (4) Session storage, caching, user profiles. PostgreSQL when: (1) Complex queries with JOINs, (2) Transactions across tables, (3) Reporting/analytics, (4) Relational data with integrity constraints. Most Spring Boot apps: PostgreSQL for primary data + DynamoDB for specific use cases (sessions, carts, activity feeds).

**Q: What's the difference between Query and Scan?**
> Query: Efficient — uses partition key to go directly to the data. O(items in partition). Scan: Reads EVERY item in the table, then filters. O(all items). Always use Query with proper key design. Scan is acceptable only for small tables or one-time operations.

**Q: How do you design a DynamoDB table for a Spring Boot e-commerce app?**
> Single-table design: PK=USER#userId, SK varies: SK=PROFILE for user data, SK=ORDER#orderId for orders, SK=ITEM#itemId for cart items. GSI1: PK=ORDER#orderId for looking up orders by ID. This avoids JOINs — all user data in one query. Access patterns drive the schema, not entity relationships.

---

## Best Practices

1. **Design for access patterns** — know your queries before designing schema
2. **Use on-demand** for unpredictable workloads (simpler, auto-scales)
3. **Avoid Scans** — always Query with proper keys
4. **Single-table design** for related entities (reduces round trips)
5. **TTL** for auto-expiring data (sessions, carts, temporary data)
6. **DAX** (DynamoDB Accelerator) for microsecond read cache
7. **Global Tables** for multi-region replication

---

## Related Topics
- → [08. RDS](./08-rds.md)
- → [10. ElastiCache](./10-elasticache.md)
- → [11. SQS SNS EventBridge](./11-sqs-sns-eventbridge.md)
