# 16. Retention & Log Compaction ⭐⭐

---

## Theory

Retention policies control how long Kafka keeps data. Two cleanup strategies exist: **delete** (remove old segments) and **compact** (keep latest value per key).

### Time-Based Retention

```
retention.ms = 604800000 (7 days, default)

How it works:
- Kafka checks each CLOSED segment's latest timestamp
- If segment's max timestamp > retention.ms ago → eligible for deletion
- Active segment is NEVER deleted (even if old)
- Check interval: log.retention.check.interval.ms (default 5 min)

Important: Deletion is per-segment, not per-record
- Segment with mix of old and recent messages: kept until ALL are expired
- Smaller segments = finer-grained retention
```

### Size-Based Retention

```
retention.bytes = -1 (unlimited, default)

How it works:
- Per-partition size limit
- When partition exceeds limit → delete oldest segments
- Deletion happens after size check during log cleanup

Example:
  retention.bytes = 10737418240 (10GB per partition)
  Topic with 6 partitions → max 60GB total for the topic
  
  If partition grows to 11GB:
    Delete oldest segments until ≤ 10GB
```

### Log Cleanup

```
log.cleanup.policy = delete (default)

Cleanup runs periodically:
  log.retention.check.interval.ms = 300000 (5 min)

Process:
1. For each partition, identify closed segments
2. Check each segment against retention policy:
   - Time: segment.maxTimestamp + retention.ms < now?
   - Size: total partition size > retention.bytes?
3. Mark eligible segments for deletion
4. Delete marked segments (.deleted suffix briefly, then removed)
```

### Delete Policy

```
cleanup.policy = delete

Behavior:
- Old data permanently removed
- Oldest segments deleted first
- Cannot recover deleted data
- Consumers reading deleted offsets get OffsetOutOfRangeException

Best for:
- Event streams (ordered events, don't need old ones)
- Logs (recent logs matter, old logs archived elsewhere)
- Metrics (time-series data with limited relevance)
```

### Compact Policy

```
cleanup.policy = compact

Behavior:
- Keep ONLY the latest value for each key
- Older values for same key removed
- Null value = tombstone (key will be removed after delete.retention.ms)
- Offsets are preserved (gaps in offset sequence after compaction)
- Active segment NEVER compacted

Best for:
- Current state (user profile, configuration)
- Changelogs (KTable backing in Kafka Streams)
- Snapshot data (latest inventory per product)
```

### Tombstone Records

```
In compacted topics, a record with key + null value = "tombstone"

Purpose: Signal that a key should be DELETED

Process:
1. Producer sends: key="user-123", value=null (tombstone)
2. During compaction: all previous values for "user-123" removed
3. Tombstone itself retained for delete.retention.ms (default 24h)
4. After delete.retention.ms: tombstone also removed
5. Key "user-123" no longer exists in topic

Why delay tombstone removal?
- Consumers that are behind need to see the delete marker
- Otherwise they'd still have old value and never know it was deleted
```

### Compacted Topics — Deep Dive

```
Compaction process (Log Cleaner):

1. Cleaner thread builds "offset map" of dirty portion:
   {key → latest offset} for records in dirty (uncompacted) section

2. Copies clean segments, skipping records where key has newer offset

3. Original segments replaced with compacted versions

Dirty ratio: min.cleanable.dirty.ratio = 0.5 (default)
  - Compaction triggered when 50% of log is "dirty" (uncompacted)
  - Lower ratio = more frequent compaction = less lag

Guarantees:
- At least ONE value per key always retained (the latest)
- Ordering within a key preserved
- Offsets never reassigned
- Consumer reading from offset 0: sees at least latest value for every key
```

### Compaction Diagram

```
Before compaction:
Offset: 0    1    2    3    4    5    6    7    8    9
Key:    A    B    A    C    B    A    C    B    A    D
Value:  a1   b1   a2   c1   b2   a3   c2   b3   a4   d1
         ↓    ↓    ↓    ↓    ↓    ↓    ↓    ↓    ↓    ↓

After compaction (only latest per key):
Offset: 7    8    6    9
Key:    B    A    C    D
Value:  b3   a4   c2   d1

Note:
- Offsets preserved (7, 8, 6, 9 — gaps are normal)
- Order within key maintained (a4 is latest A)
- Each key appears exactly once with its latest value
```

---

## Diagram

### Retention vs Compaction

```
DELETE POLICY (cleanup.policy=delete, retention.ms=3 days):
═══════════════════════════════════════════════════════════

Day 1    Day 2    Day 3    Day 4    Day 5    Day 6    Day 7
│        │        │        │        │        │        │
[Seg1]───[Seg2]───[Seg3]───[Seg4]───[Seg5]───[Seg6]───[Active]
  ↑ deleted         ↑ deleted
  (>3 days old)     (>3 days old)


COMPACT POLICY (cleanup.policy=compact):
═══════════════════════════════════════════════════════════

Before: [A:1][B:1][A:2][C:1][B:2][A:3][C:2][B:3] [Active segment]
         ├─────────── dirty ───────────┤            ├── never touched ──┤

After:  [A:3][B:3][C:2] [Active segment]
         └── compacted ─┘ └── untouched ─┘

All data retained (no time limit), just deduplicated by key
```

### Use Cases Matrix

```
┌────────────────────────────┬────────────────────────────────────┐
│      DELETE                 │          COMPACT                    │
├────────────────────────────┼────────────────────────────────────┤
│ Event streams              │ Current state per entity            │
│ Logs / metrics             │ Configuration / feature flags       │
│ Notifications              │ User profiles (latest)              │
│ Click streams              │ Kafka Streams KTable state          │
│ Audit events (with backup) │ Database changelogs                 │
│                            │ Inventory levels per product        │
│ retention.ms = X days      │ No time-based deletion              │
│ Old data gone forever      │ Only redundant old values removed   │
└────────────────────────────┴────────────────────────────────────┘
```

---

## Code

### Topic Configuration Examples

```java
// Delete policy topic
@Bean
public NewTopic eventStreamTopic() {
    return TopicBuilder.name("user-events")
        .partitions(6)
        .replicas(3)
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
        .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")       // 7 days
        .config(TopicConfig.RETENTION_BYTES_CONFIG, "10737418240")  // 10GB per partition
        .config(TopicConfig.SEGMENT_BYTES_CONFIG, "536870912")      // 512MB segments
        .build();
}

// Compact policy topic
@Bean
public NewTopic userProfileTopic() {
    return TopicBuilder.name("user-profiles")
        .partitions(6)
        .replicas(3)
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact")
        .config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.3")
        .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "86400000") // 24h tombstone retention
        .config(TopicConfig.SEGMENT_MS_CONFIG, "3600000")          // 1h segment roll
        .build();
}

// Both policies (compact + delete)
@Bean
public NewTopic hybridTopic() {
    return TopicBuilder.name("session-state")
        .partitions(3)
        .replicas(3)
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact,delete")
        .config(TopicConfig.RETENTION_MS_CONFIG, "259200000")      // 3 days
        .config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.5")
        .build();
    // Compact within retention window, delete segments beyond retention
}
```

### Sending Tombstone Records

```java
// Delete a user profile (compacted topic)
public void deleteUserProfile(String userId) {
    // Null value = tombstone → key will be removed after compaction
    kafkaTemplate.send("user-profiles", userId, null);
}
```

---

## Interview Questions

### Q1: What is the difference between delete and compact cleanup policies?

**A:**
- **Delete:** Removes entire segments when retention time/size exceeded. All data in segment is permanently gone. Used for event streams where old data isn't needed.
- **Compact:** Removes only older duplicate values for the same key. Always retains the latest value per key. Used for "current state" topics (profiles, configs). Never removes the last value for any key (unless tombstone + delete.retention.ms).

### Q2: Why does compaction leave gaps in offsets?

**A:** Compaction removes records but never reassigns offsets. If offset 3 is removed (older value for key "A"), a consumer seeking to offset 3 will get the next available offset (e.g., 5). Gaps are expected and normal. This design ensures:
- Offsets are immutable identifiers
- No confusion with external systems referencing offsets
- Consumer `seek()` still works correctly (finds next valid offset)

### Q3: Can you have both delete and compact on the same topic?

**A:** Yes. Setting `cleanup.policy=compact,delete` applies both:
- **Within retention window:** Compaction deduplicates (keeps latest per key)
- **Beyond retention window:** Entire segments deleted (even compacted ones)
- Use case: Keep latest state per key for last 7 days, delete everything older
- Example: session data (need current state for active sessions, delete expired sessions)

### Q4: What is delete.retention.ms and why does it matter?

**A:** For compacted topics, tombstones (null value records) are retained for `delete.retention.ms` (default 24h) after compaction. This ensures consumers that are behind (not yet at the tombstone) can still see the delete marker. Without this grace period, a slow consumer would never learn that a key was deleted and would retain stale data. After the retention period, tombstones are removed during compaction.

### Q5: How does Kafka handle retention when a segment has mixed old and new messages?

**A:** Kafka only deletes **entire closed segments**. If a segment has messages from multiple time periods, it's kept until the **newest** message in it exceeds retention. This means actual retention can exceed configured retention by up to one segment duration. To minimize this effect:
- Use smaller segments (segment.bytes or segment.ms)
- Trade-off: more files, more file handles, more overhead

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Large segments with short retention | Data retained much longer than intended | Reduce segment.bytes or segment.ms |
| Compacted topic without keys | Records with null keys can't be compacted | Always use meaningful keys for compacted topics |
| Not setting delete.retention.ms | Tombstones removed too quickly for slow consumers | Set appropriately for your slowest consumer lag |
| retention.bytes without per-partition understanding | Limit is per-partition, not per-topic | Calculate: desired_total / partition_count |
| Not monitoring disk usage | Unexpected disk full despite retention | Alert on disk usage trends |

---

## Best Practices

1. **Choose policy based on use case:** events → delete, state → compact
2. **Use smaller segments** for finer retention granularity (256MB-512MB)
3. **Monitor log.cleaner metrics** — compaction lag, rate, ratio
4. **Set segment.ms** for compacted topics — ensures timely compaction eligibility
5. **Plan disk capacity** — peak write rate × retention period × RF
6. **Use tombstones** for key deletion in compacted topics (not just stopping writes)

---

## Related Topics

- [04. Topics](./04-topics.md)
- [15. Kafka Storage Internals](./15-kafka-storage-internals.md)
- [28. Kafka Monitoring](./28-kafka-monitoring.md)
