# 27. Kafka Security ⭐⭐

---

## Theory

Kafka security covers three pillars: **Authentication** (who are you?), **Authorization** (what can you do?), and **Encryption** (is data protected in transit?).

### Authentication

Verifying the identity of clients (producers, consumers) and brokers.

```
Mechanisms:
- SSL/TLS: Certificate-based mutual authentication
- SASL/PLAIN: Username/password (simple, not encrypted by itself)
- SASL/SCRAM: Salted challenge-response (more secure than PLAIN)
- SASL/GSSAPI (Kerberos): Enterprise SSO integration
- SASL/OAUTHBEARER: OAuth 2.0 token-based

Broker-to-broker: Also authenticated (inter-broker communication)
```

### Authorization (ACL)

```
Access Control Lists define: WHO can do WHAT on WHICH resource

Components:
- Principal: user or service identity (e.g., User:order-service)
- Operation: Read, Write, Create, Delete, Describe, Alter
- Resource: Topic, Group, Cluster, TransactionalId
- Permission: Allow or Deny

Examples:
  Allow User:order-service to Write to Topic:orders
  Allow User:analytics to Read from Topic:* (all topics)
  Allow User:admin to Alter Cluster
  Deny User:intern to Write to Topic:production-*
```

### SSL/TLS Encryption

```
In-transit encryption:
- Client ↔ Broker: PLAINTEXT (unencrypted) or SSL (encrypted)
- Broker ↔ Broker: PLAINTEXT or SSL (inter-broker)
- Client ↔ ZooKeeper: Separate TLS config

Configuration:
  listeners=PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093
  ssl.keystore.location=/etc/kafka/kafka.server.keystore.jks
  ssl.keystore.password=***
  ssl.key.password=***
  ssl.truststore.location=/etc/kafka/kafka.server.truststore.jks
```

### Security Protocols

| Protocol | Authentication | Encryption |
|----------|---------------|------------|
| PLAINTEXT | None | None |
| SSL | TLS certificates | TLS |
| SASL_PLAINTEXT | SASL | None |
| SASL_SSL | SASL | TLS |

**Production recommendation:** SASL_SSL (both authentication and encryption)

### SASL/SCRAM Configuration

```properties
# Broker config
listeners=SASL_SSL://0.0.0.0:9093
sasl.mechanism.inter.broker.protocol=SCRAM-SHA-256
sasl.enabled.mechanisms=SCRAM-SHA-256
security.inter.broker.protocol=SASL_SSL

# Client config
security.protocol=SASL_SSL
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
  username="order-service" \
  password="secret";
```

### ACL Commands

```bash
# Grant produce permission
kafka-acls.sh --bootstrap-server localhost:9093 \
  --add --allow-principal User:order-service \
  --operation Write --topic orders

# Grant consume permission
kafka-acls.sh --bootstrap-server localhost:9093 \
  --add --allow-principal User:analytics \
  --operation Read --topic orders \
  --group analytics-group

# List ACLs
kafka-acls.sh --bootstrap-server localhost:9093 --list --topic orders

# Remove ACL
kafka-acls.sh --bootstrap-server localhost:9093 \
  --remove --allow-principal User:old-service \
  --operation Write --topic orders
```

---

## Interview Questions

### Q1: How would you secure a Kafka cluster for production?

**A:** Three layers:
1. **Encryption:** Enable SSL/TLS for all client-broker and broker-broker communication (SASL_SSL protocol)
2. **Authentication:** Use SASL/SCRAM or Kerberos to verify client identity. Each service gets unique credentials.
3. **Authorization:** Enable ACLs. Grant minimum required permissions per service (least privilege). Deny by default.
4. **Network:** Place brokers in private subnet, restrict access via security groups/firewalls.
5. **Credentials:** Rotate passwords/certificates periodically, use secrets management (Vault, AWS Secrets Manager).

### Q2: What is the performance impact of enabling SSL?

**A:** SSL adds 20-30% CPU overhead for encryption/decryption. Impact on throughput:
- With modern CPUs supporting AES-NI: minimal impact (~5-10% throughput reduction)
- Without hardware acceleration: noticeable (~20-30%)
- Latency: adds 1-3ms per connection (TLS handshake)
- Mitigation: use hardware-accelerated encryption, connection pooling (fewer handshakes)
- Almost always worth it for production (security > minor performance cost).

---

## Related Topics

- [36. Production-Level Kafka](./36-production-level-kafka.md)
- [28. Kafka Monitoring](./28-kafka-monitoring.md)
