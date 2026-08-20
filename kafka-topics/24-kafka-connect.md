# 24. Kafka Connect

---

## Theory

**Kafka Connect** is a framework for streaming data between Kafka and external systems without writing custom code. It provides scalable, reliable data integration.

### Source vs Sink Connectors

```
Source Connector: External System → Kafka
  - Database → Kafka (CDC, polling)
  - File System → Kafka
  - API → Kafka

Sink Connector: Kafka → External System
  - Kafka → Database
  - Kafka → Elasticsearch
  - Kafka → S3
  - Kafka → HDFS
```

### Worker Modes

```
Standalone Mode:
  - Single process
  - Good for development/testing
  - No fault tolerance

Distributed Mode:
  - Multiple workers forming a cluster
  - Connectors/tasks distributed across workers
  - Automatic failover (task reassigned if worker dies)
  - Production standard
```

### Common Connectors

| Connector | Type | Use Case |
|-----------|------|----------|
| JDBC Source | Source | Poll database tables → Kafka |
| Debezium | Source | CDC from MySQL/PostgreSQL/MongoDB |
| S3 Sink | Sink | Kafka → S3 (Parquet/JSON/Avro) |
| Elasticsearch Sink | Sink | Kafka → Elasticsearch (search/analytics) |
| JDBC Sink | Sink | Kafka → Database tables |
| File Source/Sink | Both | File-based integration (testing) |

### Connector Configuration

```json
{
  "name": "orders-jdbc-source",
  "config": {
    "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
    "connection.url": "jdbc:postgresql://db:5432/mydb",
    "connection.user": "kafka_connect",
    "connection.password": "${file:/secrets/db-password}",
    "table.whitelist": "orders,order_items",
    "mode": "timestamp+incrementing",
    "timestamp.column.name": "updated_at",
    "incrementing.column.name": "id",
    "topic.prefix": "db.",
    "poll.interval.ms": "1000",
    "tasks.max": "3"
  }
}
```

---

## Interview Questions

### Q1: When would you use Kafka Connect vs a custom consumer/producer?

**A:** Use Connect when: moving data between Kafka and standard systems (DB, S3, Elasticsearch) with standard patterns (CDC, ETL). Use custom code when: complex business logic, non-standard transformations, or no connector available. Connect handles offset management, fault tolerance, and scaling automatically — significant development effort saved for standard integrations.

### Q2: How does Kafka Connect handle failures in distributed mode?

**A:** Workers form a group (like consumer groups). If a worker fails:
1. Other workers detect failure (heartbeat timeout)
2. Tasks from failed worker redistributed to remaining workers
3. Tasks resume from last committed offset (exactly-once possible with some connectors)
4. If worker recovers, tasks may rebalance back to it

---

## Related Topics

- [22. Kafka APIs](./22-kafka-apis.md)
- [35. Kafka + Database](./35-kafka-database.md)
