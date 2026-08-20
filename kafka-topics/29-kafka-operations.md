# 29. Kafka Operations

---

## Theory

Kafka CLI tools provide operational capabilities for managing topics, producing/consuming messages, managing consumer groups, and administering the cluster.

### Essential CLI Tools

| Tool | Purpose |
|------|---------|
| `kafka-topics.sh` | Create, describe, delete, list topics |
| `kafka-console-producer.sh` | Produce messages from command line |
| `kafka-console-consumer.sh` | Consume messages from command line |
| `kafka-consumer-groups.sh` | Manage consumer groups and offsets |
| `kafka-configs.sh` | Alter topic/broker configurations |
| `kafka-reassign-partitions.sh` | Move partitions between brokers |

---

## Code (CLI Commands)

### Topic Operations

```bash
# Create topic
kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic orders \
  --partitions 6 --replication-factor 3 \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# List all topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe topic (shows partitions, leaders, ISR)
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic orders

# Output:
# Topic: orders  Partitions: 6  RF: 3
# Partition: 0  Leader: 1  Replicas: 1,2,3  ISR: 1,2,3
# Partition: 1  Leader: 2  Replicas: 2,3,1  ISR: 2,3,1

# Increase partitions (can't decrease!)
kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic orders --partitions 12

# Delete topic
kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic old-topic

# Show under-replicated partitions
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --under-replicated-partitions

# Show topics with no leader
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --unavailable-partitions
```

### Produce and Consume Messages

```bash
# Produce messages (interactive)
kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic orders \
  --property key.separator=: \
  --property parse.key=true
# Input: order-1:{"orderId":"1","amount":100}

# Consume from beginning
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders --from-beginning \
  --property print.key=true \
  --property print.timestamp=true

# Consume with specific group
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders --group test-group

# Consume specific partition and offset
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic orders --partition 0 --offset 100
```

### Consumer Group Operations

```bash
# List all consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Describe group (shows lag per partition)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group order-processing-group

# Reset offsets to earliest (must stop consumers first!)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-processing-group --topic orders \
  --reset-offsets --to-earliest --execute

# Reset offsets to specific timestamp
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-processing-group --topic orders \
  --reset-offsets --to-datetime 2024-01-15T00:00:00.000 --execute

# Reset by shifting offset
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-processing-group --topic orders \
  --reset-offsets --shift-by -100 --execute

# Delete consumer group
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --delete --group old-group
```

### Configuration Changes

```bash
# Alter topic config
kafka-configs.sh --bootstrap-server localhost:9092 \
  --alter --entity-type topics --entity-name orders \
  --add-config retention.ms=259200000

# Describe topic config
kafka-configs.sh --bootstrap-server localhost:9092 \
  --describe --entity-type topics --entity-name orders

# Alter broker config (dynamic, no restart needed)
kafka-configs.sh --bootstrap-server localhost:9092 \
  --alter --entity-type brokers --entity-name 1 \
  --add-config log.retention.ms=172800000
```

### Partition Reassignment

```bash
# Generate reassignment plan
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
  --topics-to-move-json-file topics.json \
  --broker-list "1,2,3,4" \
  --generate

# Execute reassignment
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --execute

# Verify reassignment progress
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
  --reassignment-json-file reassignment.json \
  --verify
```

---

## Interview Questions

### Q1: How would you reset consumer offsets to replay events from a specific date?

**A:** Steps:
1. **Stop all consumers** in the group (required for reset)
2. Run: `kafka-consumer-groups.sh --reset-offsets --to-datetime 2024-01-15T00:00:00.000 --execute`
3. Restart consumers — they'll re-read from that timestamp
4. Ensure consumers handle duplicates (idempotent processing)
5. Alternative without stopping: use `consumer.seek()` programmatically in the application

### Q2: How do you investigate why a topic has under-replicated partitions?

**A:**
1. `kafka-topics.sh --describe --under-replicated-partitions` — identify which partitions
2. Check which broker(s) are the lagging followers
3. Check those brokers: disk I/O (`iostat`), network (`ifconfig`), CPU, GC logs
4. Check `replica.lag.time.max.ms` — is the follower exceeding this?
5. Common causes: disk saturation, network issues, broker overloaded, long GC pauses
6. Fix: add disk capacity, rebalance partitions, fix network, tune GC

---

## Best Practices

1. **Always use `--execute` flag carefully** — dry-run first without it for offset resets
2. **Stop consumers before offset reset** — active consumers will override your reset
3. **Monitor reassignment progress** — large moves take time and add I/O load
4. **Throttle reassignment** — use `--throttle` flag to limit replication bandwidth
5. **Script common operations** — prevent human error in production

---

## Related Topics

- [28. Kafka Monitoring](./28-kafka-monitoring.md)
- [30. Kafka Cluster Management](./30-kafka-cluster-management.md)
