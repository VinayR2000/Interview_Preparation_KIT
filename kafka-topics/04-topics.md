# 4. Topics

---

## Theory

A **topic** is a named stream of records in Kafka. It's the primary abstraction for organizing and categorizing data. Topics are multi-subscriber — multiple consumer groups can read from the same topic independently.

### Creating Topics

Topics can be created automatically (when a producer first writes to them) or explicitly via Admin API/CLI.

```bash
# CLI creation
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic orders \
  --partitions 6 \
  --replication-factor 3

# With configuration overrides
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic user-events \
  --partitions 12 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config cleanup.policy=delete \
  --config max.message.bytes=1048576
```

### Topic Naming Conventions

```
Good naming patterns:
  <domain>.<entity>.<action>     → orders.payment.completed
  <team>.<service>.<event>       → checkout.order-service.order-created
  <environment>.<topic>          → prod.user-events

Rules:
  - Max 249 characters
  - Allowed: a-z, A-Z, 0-9, '.', '_', '-'
  - Avoid '.' and '_' together (internal conflict)
  - Use consistent convention across team
```

### Partitions

Number of partitions determines the parallelism of the topic.

```
Deciding partition count:
- Target throughput / throughput per partition
- Example: Need 100 MB/s, each partition handles ~10 MB/s → 10 partitions
- Can increase later (but NOT decrease without recreating topic)
- More partitions = more file handles, memory, leader elections
- Rule of thumb: start with max(expected consumers, throughput/10MB)
```

### Replication Factor

```
Replication Factor (RF) = number of copies across brokers

RF=1: No fault tolerance (data lost if broker fails)
RF=2: Survives 1 broker failure
RF=3: Survives 2 broker failures (PRODUCTION STANDARD)

Constraint: RF cannot exceed number of brokers
- 3 brokers → max RF=3
- If broker goes down and RF=3: still 2 copies available
```

### Retention

How long Kafka keeps messages before deletion.

```
retention.ms = 604800000 (7 days, default)
  - Messages older than 7 days are eligible for deletion
  - Set to -1 for infinite retention

retention.bytes = -1 (unlimited, default)
  - Maximum size per partition before oldest messages deleted
  - Set to limit disk usage per partition

Retention check frequency: log.retention.check.interval.ms (default 5 min)
```

### Cleanup Policy

Determines how old data is handled.

```
cleanup.policy=delete (default):
  - Old segments deleted based on retention time/size
  - Messages permanently removed

cleanup.policy=compact:
  - Keep only latest value for each key
  - Tombstone records (key + null value) mark deletions
  - Use case: changelog, current state snapshots

cleanup.policy=delete,compact:
  - Both policies applied
  - Compact within retention window, delete beyond it
```

### Log Compaction

```
Before compaction:
  Key:    A   B   A   C   B   A   C   B
  Offset: 0   1   2   3   4   5   6   7
  Value:  v1  v1  v2  v1  v2  v3  v2  v3

After compaction:
  Key:    A   C   B
  Offset: 5   6   7
  Value:  v3  v2  v3

- Only latest value per key retained
- Offsets NOT reassigned (gaps are normal)
- Active segment never compacted
- Guarantees: at least the last value for every key
```

### Segment Size and Rolling

```
log.segment.bytes = 1073741824 (1GB default)
  - Maximum size of a single segment file
  - When reached, segment is "rolled" (closed, new one started)

log.roll.ms / log.roll.hours = 168 hours (7 days default)
  - Time-based segment rolling
  - Even if size not reached, roll after this time
  - Smaller segments = more frequent compaction/deletion eligibility
```

### Topic Configuration

| Config | Default | Description |
|--------|---------|-------------|
| `retention.ms` | 604800000 (7d) | Time to retain messages |
| `retention.bytes` | -1 (unlimited) | Max size per partition |
| `cleanup.policy` | delete | delete, compact, or both |
| `min.insync.replicas` | 1 | Min ISR for acks=all to succeed |
| `max.message.bytes` | 1048588 (~1MB) | Max record batch size |
| `segment.bytes` | 1073741824 (1GB) | Segment file size |
| `segment.ms` | 604800000 (7d) | Time before rolling segment |
| `compression.type` | producer | none, gzip, snappy, lz4, zstd |
| `message.timestamp.type` | CreateTime | CreateTime or LogAppendTime |

---

## Diagram

### Topic Lifecycle

```
┌──────────────────────────────────────────────────────────────────┐
│ Topic "orders" (partitions=3, RF=3, retention=7d)                 │
│                                                                    │
│ Partition 0:                                                       │
│ [Seg0: 0-1000] [Seg1: 1001-2000] [Seg2: 2001-2500] [Active]     │
│   (expired)       (due soon)        (retained)       (writing)    │
│      ↓                                                             │
│   DELETED by                                                       │
│   retention                                                        │
│                                                                    │
│ Partition 1:                                                       │
│ [Seg0: 0-800] [Seg1: 801-1600] [Active: 1601-current]           │
│                                                                    │
│ Partition 2:                                                       │
│ [Seg0: 0-900] [Seg1: 901-1800] [Seg2: 1801-2700] [Active]      │
└──────────────────────────────────────────────────────────────────┘
```

### Compaction Visualization

```
Topic: "user-preferences" (cleanup.policy=compact)

Log before compaction:
┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
│K:A │K:B │K:C │K:A │K:B │K:C │K:A │K:D │K:B │K:A │
│V:1 │V:1 │V:1 │V:2 │V:2 │V:2 │V:3 │V:1 │V:3 │V:4 │
│O:0 │O:1 │O:2 │O:3 │O:4 │O:5 │O:6 │O:7 │O:8 │O:9 │
└────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘

Log after compaction:
┌────┬────┬────┬────┐
│K:C │K:D │K:B │K:A │  ← Latest value for each key
│V:2 │V:1 │V:3 │V:4 │
│O:5 │O:7 │O:8 │O:9 │  ← Offsets preserved
└────┴────┴────┴────┘
```

---

## Code (Admin API)

### Creating Topics Programmatically

```java
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name("orders")
            .partitions(6)
            .replicas(3)
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")  // 7 days
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
            .build();
    }

    @Bean
    public NewTopic userPreferencesTopic() {
        return TopicBuilder.name("user-preferences")
            .partitions(3)
            .replicas(3)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact")
            .config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.5")
            .build();
    }
}
```

### Admin Client Operations

```java
public class KafkaAdminOperations {

    private final AdminClient adminClient;

    public void createTopic(String name, int partitions, short replicationFactor) {
        NewTopic topic = new NewTopic(name, partitions, replicationFactor);
        topic.configs(Map.of(
            "retention.ms", "86400000",
            "cleanup.policy", "delete"
        ));
        adminClient.createTopics(List.of(topic)).all().get();
    }

    public TopicDescription describeTopic(String name) throws Exception {
        return adminClient.describeTopics(List.of(name))
            .topicNameValues().get(name).get();
    }

    public void increasePartitions(String topic, int newCount) throws Exception {
        adminClient.createPartitions(
            Map.of(topic, NewPartitions.increaseTo(newCount))
        ).all().get();
    }

    public void updateConfig(String topic, String key, String value) throws Exception {
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        AlterConfigOp op = new AlterConfigOp(
            new ConfigEntry(key, value), AlterConfigOp.OpType.SET);
        adminClient.incrementalAlterConfigs(
            Map.of(resource, List.of(op))
        ).all().get();
    }
}
```

---

## Interview Questions

### Q1: Can you decrease the number of partitions for a topic?

**A:** No. You can only increase partitions, never decrease. Decreasing would require reassigning messages to fewer partitions, which would break offset semantics and ordering guarantees. If you need fewer partitions, you must create a new topic with fewer partitions and migrate data.

### Q2: What is log compaction and when would you use it?

**A:** Log compaction retains only the latest value for each message key, deleting older values. Use it for:
- **Current state topics** (user profile, configuration, feature flags)
- **Changelog topics** (KTable state in Kafka Streams)
- **Snapshot data** (latest inventory count per product)

It guarantees at least the last update for every key is retained, making it suitable for rebuilding state from the topic.

### Q3: What happens when retention expires?

**A:** When time-based or size-based retention is exceeded:
1. Kafka identifies eligible closed segments (not the active segment)
2. Entire segment files are deleted (not individual messages)
3. This means actual deletion granularity = segment size/time
4. Consumers reading from deleted offsets get `OffsetOutOfRangeException`
5. With `auto.offset.reset=earliest`, consumer restarts from earliest available

### Q4: How to choose the right number of partitions?

**A:** Consider:
1. **Throughput:** target throughput / per-partition throughput (typically 10-30 MB/s per partition)
2. **Parallelism:** max consumers in a group = max partitions
3. **Ordering:** more partitions = more ordering domains
4. **Overhead:** each partition uses file handles, memory, adds to leader election time
5. **Rule of thumb:** For most services, 6-12 partitions is a good starting point. High-throughput topics may need 50-100+.

### Q5: What is the difference between delete and compact cleanup policies?

**A:**
- **Delete:** Removes entire segments when retention time/size exceeded. Old messages permanently gone.
- **Compact:** Removes older values for the same key, keeps latest. Never removes the last value for any key. Active segment never compacted.
- **Use delete** for: event streams, logs, metrics (don't need old data)
- **Use compact** for: state, configuration, changelogs (need latest value per key)

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Starting with too many partitions | Wastes resources, hard to reduce | Start conservative, increase as needed |
| Setting RF > broker count | Topic creation fails | RF ≤ number of brokers |
| Not setting min.insync.replicas | acks=all still allows single replica | Set min.insync.replicas=2 with acks=all |
| Infinite retention without monitoring | Disk fills up | Set retention or monitor disk usage |
| Using compaction without understanding keys | Null keys → messages not compacted properly | Ensure all messages have meaningful keys for compacted topics |

---

## Best Practices

1. **Use explicit topic creation** — don't rely on auto-creation in production
2. **Set min.insync.replicas=2** with RF=3 for durability
3. **Plan partition count upfront** — increasing later can break key ordering
4. **Use compacted topics** for "latest state" use cases
5. **Monitor disk usage** — especially with long retention or high throughput
6. **Document topic contracts** — schema, retention, ownership, consumer groups

---

## Related Topics

- [05. Partitions](./05-partitions.md)
- [13. Replication](./13-replication.md)
- [16. Retention & Log Compaction](./16-retention-log-compaction.md)
