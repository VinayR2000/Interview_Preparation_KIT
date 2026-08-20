# 28. Kafka Monitoring ⭐⭐

---

## Theory

Monitoring Kafka is essential for detecting performance degradation, consumer lag, broker health issues, and capacity problems before they impact production.

### Key Metrics

#### Consumer Lag

```
Consumer Lag = Log End Offset (LEO) - Committed Offset

What it tells you:
- Lag = 0: Consumer fully caught up (ideal)
- Lag growing: Consumer slower than producer (problem!)
- Lag stable (non-zero): Consumer keeping pace but behind

Alert thresholds (example):
- Warning: lag > 1000 records for 5 minutes
- Critical: lag > 10000 records for 5 minutes
```

#### Throughput

```
Producer metrics:
  record-send-rate       — records/sec sent
  byte-rate             — bytes/sec sent
  record-send-total     — total records sent

Broker metrics:
  BytesInPerSec         — bytes/sec received from producers
  BytesOutPerSec        — bytes/sec sent to consumers
  MessagesInPerSec      — messages/sec received

Consumer metrics:
  records-consumed-rate  — records/sec consumed
  bytes-consumed-rate    — bytes/sec consumed
```

#### Latency

```
Producer:
  request-latency-avg    — average time for produce request
  record-queue-time-avg  — time record spends in accumulator

Broker:
  RequestHandlerAvgIdlePercent  — how busy request handlers are
  TotalTimeMs                   — total request processing time

Consumer:
  fetch-latency-avg     — average time for fetch request
  poll-idle-ratio       — fraction of time spent idle (higher = caught up)
```

#### Broker Health

```
ActiveControllerCount     — should be 1 (0 = no controller, problem!)
UnderReplicatedPartitions — partitions with ISR < RF (0 is ideal)
UnderMinIsrPartitionCount — partitions below min.insync.replicas
OfflinePartitionsCount    — partitions with no leader (CRITICAL)
IsrShrinksPerSec         — rate of ISR shrinkage
IsrExpandsPerSec         — rate of ISR recovery
LeaderElectionRateAndTimeMs — leader election frequency
```

#### Partition Health

```
Under-replicated partitions: ISR size < replication factor
  Cause: Follower falling behind (slow disk, network, GC)
  Impact: Reduced fault tolerance
  Action: Investigate lagging broker

Offline partitions: No leader available
  Cause: All ISR members down (or unclean election disabled)
  Impact: Topic partition completely unavailable
  Action: Immediate investigation! Bring ISR members back.
```

#### Resource Metrics

```
Disk:
  - Usage percentage per broker
  - I/O wait time (high = disk saturated)
  - Log flush rate and time

CPU:
  - System CPU (OS operations)
  - User CPU (Kafka application)
  - SSL adds 20-30% CPU overhead

Memory:
  - JVM heap usage and GC frequency
  - Page cache hit rate (should be high for recent data)
  - Free memory (available for page cache)

Network:
  - Bytes in/out per broker
  - Request queue size (growing = overloaded)
  - Network handler idle percentage
```

### JMX Metrics

Kafka exposes all metrics via JMX (Java Management Extensions).

```bash
# Enable JMX on broker
KAFKA_JMX_OPTS="-Dcom.sun.management.jmxremote
  -Dcom.sun.management.jmxremote.port=9999
  -Dcom.sun.management.jmxremote.authenticate=false"

# Key MBeans:
kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions
kafka.controller:type=KafkaController,name=OfflinePartitionsCount
kafka.network:type=RequestMetrics,name=TotalTimeMs,request=Produce
kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*
```

### Prometheus + Grafana

```
Standard monitoring stack:

Kafka Broker ──JMX──► JMX Exporter ──► Prometheus ──► Grafana
                      (agent/sidecar)     (scrape)     (dashboard)

Alternative: Kafka exports metrics → Prometheus via:
  - JMX Exporter (java agent)
  - Kafka Exporter (standalone binary, limited metrics)
  - Confluent Metrics Reporter
```

### Essential Dashboards

```
Dashboard 1: Cluster Overview
  - Active controller count
  - Broker count
  - Total topics/partitions
  - Under-replicated partitions
  - Offline partitions

Dashboard 2: Throughput
  - Messages in/out per broker
  - Bytes in/out per broker
  - Request rate by type

Dashboard 3: Consumer Groups
  - Consumer lag per group/topic/partition
  - Consumption rate
  - Rebalance frequency

Dashboard 4: Producer Performance
  - Record send rate
  - Error rate
  - Request latency (p50, p95, p99)
  - Batch size average

Dashboard 5: Broker Resources
  - CPU, Memory, Disk, Network per broker
  - GC frequency and duration
  - Request handler utilization
```

---

## Code

### Consumer Lag Monitoring (Spring Boot Actuator)

```java
@Component
@Slf4j
public class ConsumerLagMonitor {

    private final AdminClient adminClient;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedRate = 30000)  // every 30s
    public void checkConsumerLag() {
        try {
            adminClient.listConsumerGroups().all().get().forEach(group -> {
                Map<TopicPartition, OffsetAndMetadata> committed = 
                    adminClient.listConsumerGroupOffsets(group.groupId())
                        .partitionsToOffsetAndMetadata().get();
                
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(committed.keySet().stream()
                        .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest())))
                    .all().get();
                
                committed.forEach((tp, offsetMeta) -> {
                    long lag = endOffsets.get(tp).offset() - offsetMeta.offset();
                    meterRegistry.gauge("kafka.consumer.lag",
                        Tags.of("group", group.groupId(), 
                                "topic", tp.topic(),
                                "partition", String.valueOf(tp.partition())),
                        lag);
                    
                    if (lag > 10000) {
                        log.warn("High lag: group={}, topic={}, partition={}, lag={}",
                            group.groupId(), tp.topic(), tp.partition(), lag);
                    }
                });
            });
        } catch (Exception e) {
            log.error("Failed to check consumer lag", e);
        }
    }
}
```

### CLI Monitoring Commands

```bash
# Check consumer group lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group order-processing-group

# Output:
# GROUP          TOPIC    PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# order-group    orders   0          4521            4525            4
# order-group    orders   1          3892            3900            8
# order-group    orders   2          5100            5100            0

# Check under-replicated partitions
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --under-replicated-partitions

# Check broker topic metrics
kafka-run-class.sh kafka.tools.JmxTool \
  --object-name kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec \
  --jmx-url service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi
```

---

## Interview Questions

### Q1: What are the most critical Kafka metrics to monitor?

**A:** Top priority metrics:
1. **Consumer lag** — growing lag = consumer can't keep up → delayed processing
2. **Under-replicated partitions** — indicates broker health issues → reduced fault tolerance
3. **Offline partitions** — CRITICAL — no leader → partition unavailable
4. **Active controller count** — must be exactly 1 (0 = cluster has no coordinator)
5. **ISR shrink rate** — frequent shrinks indicate unstable brokers
6. **Request handler idle %** — low idle = broker overloaded
7. **Disk usage** — approaching full = writes will fail

### Q2: How would you investigate growing consumer lag?

**A:** Systematic approach:
1. **Check if isolated:** Is lag growing for all partitions or specific ones? (hotspot vs systemic)
2. **Check consumer health:** Are consumers alive? Rebalancing frequently? max.poll.interval violations?
3. **Check throughput:** Compare production rate vs consumption rate (is inbound suddenly higher?)
4. **Check processing time:** Is downstream slow (DB timeouts, API latency)?
5. **Check resources:** CPU, memory, network of consumer instances
6. **Fix:** Scale consumers (add instances up to partition count), optimize processing, increase max.poll.records for batch efficiency, fix downstream bottlenecks

### Q3: What does "under-replicated partitions" indicate?

**A:** A partition where ISR < replication factor — at least one follower is lagging. Causes: broker disk I/O saturation, network issues, GC pauses, or broker failure. Impact: reduced fault tolerance (fewer replicas to promote on leader failure). If combined with `min.insync.replicas`, may block writes. Action: identify the lagging broker (check disk I/O, network, GC logs), resolve the bottleneck. Persistent under-replication may require adding capacity.

---

## Best Practices

1. **Alert on consumer lag growth** — not just absolute value, but rate of change
2. **Monitor under-replicated partitions** — never let this stay non-zero
3. **Track request latency percentiles** — p99 catches tail latencies
4. **Set up dashboards** — cluster overview, per-consumer-group, per-broker resources
5. **Use Prometheus + Grafana** — industry standard for Kafka monitoring
6. **Monitor disk headroom** — alert at 70% usage (before it's too late)
7. **Track rebalance frequency** — excessive rebalancing indicates configuration issues

---

## Related Topics

- [36. Production-Level Kafka](./36-production-level-kafka.md)
- [29. Kafka Operations](./29-kafka-operations.md)
- [17. Kafka Performance](./17-kafka-performance.md)
