# Topic 29: Production-Level Topics (Migrations, Backup, DR)

## Theory

### Database Lifecycle in Production

```
┌─────────────────────────────────────────────────────────────────┐
│            DATABASE LIFECYCLE IN PRODUCTION                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Development → Staging → Production                              │
│                                                                  │
│  KEY CONCERNS:                                                   │
│  1. Schema Evolution (migrations)                                │
│  2. Data Integrity (backup/restore)                              │
│  3. Availability (zero-downtime changes)                         │
│  4. Disaster Recovery (RPO/RTO)                                  │
│  5. Monitoring & Alerting                                        │
│  6. Audit Trail                                                  │
│                                                                  │
│  GOLDEN RULES:                                                   │
│  • Every schema change must be versioned and reversible          │
│  • Backups must be tested regularly (untested = unreliable)      │
│  • Zero-downtime migrations are expected for 99.9%+ SLA         │
│  • Monitoring must detect issues before users do                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Database Migrations

### Why Migrations?

```
┌─────────────────────────────────────────────────────────────────┐
│             WHY DATABASE MIGRATIONS?                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  WITHOUT MIGRATIONS:                                             │
│  • Manual SQL scripts run by DBA                                 │
│  • No version history                                            │
│  • "It works on my machine" problem                              │
│  • Environments drift (dev ≠ staging ≠ prod)                    │
│  • No rollback capability                                        │
│  • Deployment fear                                               │
│                                                                  │
│  WITH MIGRATIONS:                                                │
│  • Schema changes are code (version controlled)                  │
│  • Applied in order (deterministic)                              │
│  • Every environment matches                                     │
│  • Rollback support                                              │
│  • CI/CD integration                                             │
│  • Audit trail (who changed what, when)                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Flyway

```
┌─────────────────────────────────────────────────────────────────┐
│                      FLYWAY                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Naming Convention:                                              │
│  V{version}__{description}.sql                                   │
│                                                                  │
│  Example:                                                        │
│  src/main/resources/db/migration/                                │
│  ├── V1__create_users_table.sql                                  │
│  ├── V2__create_orders_table.sql                                 │
│  ├── V3__add_email_index.sql                                     │
│  ├── V4__add_status_column_to_orders.sql                         │
│  └── V5__create_audit_log_table.sql                              │
│                                                                  │
│  How it works:                                                   │
│  1. Flyway creates flyway_schema_history table                   │
│  2. On startup, checks which migrations have been applied        │
│  3. Applies pending migrations in version order                  │
│  4. Records each migration with checksum                         │
│  5. If checksum mismatch → FAILS (someone modified applied file)│
│                                                                  │
│  Migration Types:                                                │
│  V = Versioned (applied once, tracked)                           │
│  R = Repeatable (re-applied when checksum changes)               │
│  U = Undo (rollback for paid edition)                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Flyway Code Examples

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
```

```sql
-- V2__create_orders_table.sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
```

```sql
-- V3__add_phone_to_users.sql (backward compatible — adds nullable column)
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
```

### Spring Boot + Flyway Configuration

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
    out-of-order: false
    
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: app_user
    password: ${DB_PASSWORD}
```

### Liquibase (Alternative)

```xml
<!-- changelog-master.xml -->
<databaseChangeLog>
    <include file="db/changelog/001-create-users.xml"/>
    <include file="db/changelog/002-create-orders.xml"/>
    <include file="db/changelog/003-add-phone-to-users.xml"/>
</databaseChangeLog>

<!-- 001-create-users.xml -->
<changeSet id="1" author="developer">
    <createTable tableName="users">
        <column name="id" type="BIGSERIAL" autoIncrement="true">
            <constraints primaryKey="true"/>
        </column>
        <column name="email" type="VARCHAR(255)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="name" type="VARCHAR(100)">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <rollback>
        <dropTable tableName="users"/>
    </rollback>
</changeSet>
```

---

## Zero-Downtime Migrations

### Backward-Compatible Changes

```
┌─────────────────────────────────────────────────────────────────┐
│         ZERO-DOWNTIME MIGRATION PATTERNS                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  SAFE CHANGES (backward compatible):                             │
│  ✓ Add new table                                                │
│  ✓ Add nullable column                                          │
│  ✓ Add column with DEFAULT (PG 11+ instant, no rewrite)         │
│  ✓ Add new index CONCURRENTLY                                   │
│  ✓ Add new constraint as NOT VALID then VALIDATE separately     │
│                                                                  │
│  UNSAFE CHANGES (break old code):                                │
│  ✗ Drop column (old code still references it)                   │
│  ✗ Rename column (old code uses old name)                       │
│  ✗ Change column type (type mismatch)                           │
│  ✗ Add NOT NULL to existing column (existing nulls fail)        │
│  ✗ DROP TABLE (old code queries it)                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Expand-Contract Pattern (for unsafe changes)

```
RENAMING A COLUMN: "username" → "login_name"
──────────────────────────────────────────────

Phase 1: EXPAND (add new column, keep old)
──────────────────────────────────────────
Migration V5: 
  ALTER TABLE users ADD COLUMN login_name VARCHAR(100);
  UPDATE users SET login_name = username;
  CREATE TRIGGER sync_columns BEFORE INSERT OR UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION sync_username_login();

Phase 2: MIGRATE CODE (deploy reads both, writes both)
──────────────────────────────────────────────────────
  Code reads from login_name, writes to both columns

Phase 3: CONTRACT (remove old column after all code deployed)
─────────────────────────────────────────────────────────────
Migration V6:
  DROP TRIGGER sync_columns ON users;
  ALTER TABLE users DROP COLUMN username;

TOTAL DEPLOYS: 3 (migration 1, code deploy, migration 2)
DOWNTIME: 0
```

### Large Table Migration

```sql
-- PROBLEM: ALTER TABLE on 100M row table takes hours

-- SAFE: Create index without locking
CREATE INDEX CONCURRENTLY idx_events_type ON events(event_type);
-- CONCURRENTLY avoids table lock (doesn't block reads/writes)

-- SAFE: Add constraint without blocking
-- Step 1: NOT VALID (instant, no scan)
ALTER TABLE orders ADD CONSTRAINT chk_positive 
    CHECK (amount > 0) NOT VALID;
-- Step 2: VALIDATE separately (scans but doesn't block writes)
ALTER TABLE orders VALIDATE CONSTRAINT chk_positive;

-- SAFE: Backfill new column in batches
DO $$
DECLARE
    batch_size INT := 10000;
    max_id BIGINT;
    current_id BIGINT := 0;
BEGIN
    SELECT MAX(id) INTO max_id FROM events;
    WHILE current_id < max_id LOOP
        UPDATE events 
        SET processed_at = created_at
        WHERE id > current_id AND id <= current_id + batch_size
          AND processed_at IS NULL;
        current_id := current_id + batch_size;
        COMMIT;
        PERFORM pg_sleep(0.1); -- Reduce load
    END LOOP;
END $$;

-- TOOL: pg_repack (rewrite table without exclusive lock)
-- Alternative to VACUUM FULL (which locks the table)
-- pg_repack --table=events --no-superuser-check
```

---

## Backup & Restore

### Backup Strategies

```
┌─────────────────────────────────────────────────────────────────┐
│              BACKUP STRATEGIES                                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. LOGICAL BACKUP (pg_dump)                                     │
│     • Exports SQL statements                                     │
│     • Portable across PostgreSQL versions                        │
│     • Slow for large databases                                   │
│     • Can restore individual tables                              │
│     • Good for: small-medium DBs, selective restore              │
│                                                                  │
│  2. PHYSICAL BACKUP (pg_basebackup)                              │
│     • Copies actual data files                                   │
│     • Fast for large databases                                   │
│     • Combined with WAL for PITR                                 │
│     • Must restore entire cluster                                │
│     • Good for: large DBs, disaster recovery                     │
│                                                                  │
│  3. CONTINUOUS ARCHIVING (WAL archiving)                         │
│     • Archives WAL segments to object storage (S3)               │
│     • Enables Point-In-Time Recovery                             │
│     • Minimal data loss (RPO = seconds)                          │
│     • Good for: production critical systems                      │
│                                                                  │
│  RECOMMENDED: pg_basebackup (daily) + WAL archiving (continuous)│
│  = Point-in-time recovery to any second                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Backup Commands

```bash
# Logical backup — full database
pg_dump -h localhost -U postgres -d mydb -F custom -f backup.dump

# Logical backup — specific tables only
pg_dump -h localhost -U postgres -d mydb -t orders -t order_items -F custom -f orders.dump

# Restore logical backup
pg_restore -h localhost -U postgres -d mydb --clean backup.dump

# Physical backup (for PITR)
pg_basebackup -h localhost -U replication -D /backups/base -Ft -z -P

# WAL archiving config (postgresql.conf)
# archive_mode = on
# archive_command = 'aws s3 cp %p s3://my-bucket/wal/%f'
# archive_timeout = 60
```

---

## RPO and RTO

```
┌─────────────────────────────────────────────────────────────────┐
│              RPO & RTO                                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  RPO (Recovery Point Objective):                                 │
│  "How much DATA can we afford to LOSE?"                          │
│                                                                  │
│  • RPO = 0: No data loss (synchronous replication)               │
│  • RPO = 1 min: WAL archiving every 60 seconds                   │
│  • RPO = 1 hour: Hourly backups                                  │
│  • RPO = 24 hours: Daily backups                                 │
│                                                                  │
│  RTO (Recovery Time Objective):                                  │
│  "How long can we be DOWN?"                                      │
│                                                                  │
│  • RTO < 30s: Automated failover (Patroni)                       │
│  • RTO < 5 min: Manual promotion of replica                      │
│  • RTO < 1 hour: Restore from physical backup                    │
│  • RTO < 4 hours: Restore from logical backup                    │
│                                                                  │
│  COST: Lower RPO/RTO = more infrastructure = higher cost         │
│                                                                  │
│  TYPICAL TARGETS:                                                │
│  • Tier 1 (payments): RPO=0, RTO<30s                             │
│  • Tier 2 (orders): RPO<1min, RTO<5min                           │
│  • Tier 3 (analytics): RPO<1hour, RTO<1hour                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Point-in-Time Recovery (PITR)

```
Timeline:
─────────────────────────────────────────────────────────
│ Base Backup │ WAL 1 │ WAL 2 │ WAL 3 │ WAL 4 │       │
└─────────────┴───────┴───────┴───┬───┴───────┘       │
Jan 1                              │                    
                         Jan 5 14:30:00                  
                         (accidental DELETE!)             

RECOVERY:
1. Restore base backup from Jan 1
2. Set recovery target:
   recovery_target_time = '2024-01-05 14:29:59'
   restore_command = 'aws s3 cp s3://bucket/wal/%f %p'
3. Start PostgreSQL — replays WAL up to target time
4. Database state = Jan 5 14:29:59 (before the DELETE)
```

---

## Disaster Recovery

### DR Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│           DISASTER RECOVERY ARCHITECTURE                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PRIMARY REGION (us-east-1)        DR REGION (us-west-2)        │
│  ┌───────────────────────┐         ┌───────────────────────┐   │
│  │ Primary PostgreSQL    │ ──WAL──▶│ Standby PostgreSQL    │   │
│  │ (read/write)          │ stream  │ (recovery mode)       │   │
│  └───────────────────────┘         └───────────────────────┘   │
│         │                                    │                   │
│         │                                    │                   │
│  ┌──────▼──────────────┐           ┌────────▼──────────────┐   │
│  │ WAL Archive (S3)    │           │ S3 (cross-region      │   │
│  │ us-east-1           │ ──sync──▶│ replication)           │   │
│  └─────────────────────┘           └───────────────────────┘   │
│                                                                  │
│  FAILOVER PROCEDURE:                                             │
│  1. Detect primary failure                                       │
│  2. Verify standby is up-to-date                                 │
│  3. Promote standby to primary                                   │
│  4. Update DNS/endpoints                                         │
│  5. Redirect application traffic                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Monitoring & Alerting

### Key Metrics

```sql
-- 1. Connection count
SELECT count(*) FROM pg_stat_activity;
-- Alert if > 80% of max_connections

-- 2. Long-running queries
SELECT pid, now() - query_start as duration, query
FROM pg_stat_activity
WHERE state = 'active' AND now() - query_start > interval '30 seconds';

-- 3. Cache hit ratio (should be > 99%)
SELECT 
    sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)) as ratio
FROM pg_statio_user_tables;

-- 4. Dead tuples (bloat indicator)
SELECT relname, n_dead_tup, n_live_tup,
       n_dead_tup::float / NULLIF(n_live_tup, 0) as dead_ratio
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC;

-- 5. Replication lag
SELECT client_addr, state,
       pg_wal_lsn_diff(sent_lsn, replay_lsn) as lag_bytes
FROM pg_stat_replication;

-- 6. Table sizes
SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
FROM pg_stat_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 10;

-- 7. Slow queries (requires pg_stat_statements)
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC LIMIT 10;
```

### Alerting Rules

```yaml
# Prometheus alerting rules (example)
groups:
  - name: postgresql
    rules:
      - alert: PostgresqlHighConnections
        expr: pg_stat_activity_count > (pg_settings_max_connections * 0.8)
        for: 5m
        labels:
          severity: warning
          
      - alert: PostgresqlReplicationLag
        expr: pg_replication_lag_seconds > 10
        for: 2m
        labels:
          severity: critical
          
      - alert: PostgresqlDeadlocks
        expr: rate(pg_stat_database_deadlocks[5m]) > 0.5
        for: 5m
        labels:
          severity: warning
          
      - alert: PostgresqlLowCacheHitRatio
        expr: pg_stat_database_blks_hit / (pg_stat_database_blks_hit + pg_stat_database_blks_read) < 0.95
        for: 10m
        labels:
          severity: warning
```

---

## Audit Logging

```sql
-- Audit table
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    record_id BIGINT NOT NULL,
    operation VARCHAR(10) NOT NULL, -- INSERT, UPDATE, DELETE
    old_values JSONB,
    new_values JSONB,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_table_record ON audit_log(table_name, record_id);
CREATE INDEX idx_audit_changed_at ON audit_log(changed_at);

-- Generic audit trigger
CREATE OR REPLACE FUNCTION audit_trigger_func()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO audit_log (table_name, record_id, operation, new_values, changed_by)
        VALUES (TG_TABLE_NAME, NEW.id, 'INSERT', row_to_json(NEW)::jsonb, current_user);
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit_log (table_name, record_id, operation, old_values, new_values, changed_by)
        VALUES (TG_TABLE_NAME, NEW.id, 'UPDATE', row_to_json(OLD)::jsonb, row_to_json(NEW)::jsonb, current_user);
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO audit_log (table_name, record_id, operation, old_values, changed_by)
        VALUES (TG_TABLE_NAME, OLD.id, 'DELETE', row_to_json(OLD)::jsonb, current_user);
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Attach to tables
CREATE TRIGGER audit_orders
AFTER INSERT OR UPDATE OR DELETE ON orders
FOR EACH ROW EXECUTE FUNCTION audit_trigger_func();
```

---

## Interview Questions and Answers

### Q1: How do you perform zero-downtime schema migrations?

**Answer:**

Use the expand-contract pattern:
1. **Expand**: Add new column/table (backward compatible). Deploy migration.
2. **Migrate code**: Deploy application that uses both old and new schema.
3. **Contract**: Remove old column/table. Deploy cleanup migration.

Key rules:
- Never drop a column that running code still references
- Add columns as nullable or with DEFAULT
- Create indexes with `CONCURRENTLY` to avoid table locks
- Add constraints as `NOT VALID` then `VALIDATE` separately
- Backfill data in batches (not one massive UPDATE)

### Q2: What is your backup and recovery strategy?

**Answer:**

**Strategy**: pg_basebackup (daily) + continuous WAL archiving

- **Daily base backup** to S3 (physical, compressed)
- **WAL archiving** every 60 seconds to S3
- **Retention**: 30 days of PITR capability
- **Testing**: Monthly restore drill to verify backups work

**Recovery scenarios**:
- Accidental DELETE → PITR to moment before
- Database corruption → Restore base backup + replay WAL
- Region failure → Promote cross-region standby

**Targets**: RPO < 1 minute, RTO < 5 minutes (with automated failover)

### Q3: Explain the difference between Flyway and Liquibase.

**Answer:**

| Aspect | Flyway | Liquibase |
|---|---|---|
| Format | SQL files | XML/YAML/JSON/SQL |
| Approach | SQL-first | Abstraction-first |
| Rollback | Manual (or paid) | Built-in rollback support |
| DB independence | Less (raw SQL) | More (abstracted) |
| Learning curve | Lower | Higher |
| Checksums | On applied scripts | On changesets |
| Best for | Teams comfortable with SQL | Multi-DB environments |

**Recommendation**: Flyway for PostgreSQL-only projects (simpler, SQL-native). Liquibase if you need multi-database support or built-in rollback.

### Q4: How do you handle data migrations for millions of records?

**Answer:**

Never do large data migrations in a single transaction:

1. **Batch processing**: Update 10K-50K rows at a time with small pauses
2. **Background job**: Run migration as async process, not in deployment
3. **Dual-write period**: New code writes to both old and new format
4. **Backfill separately**: Fill historical data in batches after code deploy
5. **Monitor**: Track progress, watch for lock contention
6. **Resumable**: Design so migration can be restarted from where it stopped

```sql
-- Example: batch update with progress
DO $$
DECLARE
    rows_updated INT;
    total_updated INT := 0;
BEGIN
    LOOP
        UPDATE users SET normalized_email = LOWER(email)
        WHERE id IN (
            SELECT id FROM users WHERE normalized_email IS NULL LIMIT 10000
        );
        GET DIAGNOSTICS rows_updated = ROW_COUNT;
        total_updated := total_updated + rows_updated;
        RAISE NOTICE 'Updated % rows (total: %)', rows_updated, total_updated;
        EXIT WHEN rows_updated = 0;
        COMMIT;
        PERFORM pg_sleep(0.5); -- throttle
    END LOOP;
END $$;
```

---

## Follow-up Questions and Answers

### Q: What happens if a Flyway migration fails halfway?

**Answer:**

- PostgreSQL: Migration runs in a transaction. If it fails, the entire migration is rolled back. The migration is marked as FAILED in `flyway_schema_history`.
- To fix: Fix the SQL, then run `flyway repair` (removes failed entry) and re-run.
- Exception: DDL that can't be transactional (e.g., `CREATE INDEX CONCURRENTLY`) — these must be in their own migration file.

### Q: How do you test database migrations before production?

**Answer:**

1. **CI pipeline**: Run all migrations against fresh database + run tests
2. **Staging environment**: Mirror of production with anonymized data
3. **Schema comparison**: Compare post-migration schema against expected
4. **Performance test**: Run migration against production-sized dataset
5. **Rollback test**: Verify undo/rollback works correctly
6. **Shadow database**: Apply migration to copy of production data

---

## Common Mistakes

| Mistake | Impact | Fix |
|---|---|---|
| Not testing backups | Discover backup is corrupt during DR | Monthly restore drills |
| Modifying applied migration files | Flyway checksum mismatch | Never edit applied migrations |
| Running ALTER TABLE without CONCURRENTLY | Table locked for minutes | Use CONCURRENTLY for indexes |
| Single massive UPDATE for backfill | Locks table, exhausts WAL | Batch in small chunks |
| No monitoring on replication lag | Silent data loss risk | Alert on lag > 10s |
| Dropping columns in same deploy | Old instances crash | Expand-contract pattern |
| No timeout on migrations | Stuck migration holds lock | SET statement_timeout |
| Ignoring dead tuples growth | Bloated tables, slow queries | Monitor and tune autovacuum |

---

## Best Practices

1. **Version ALL schema changes** — never make manual DB changes
2. **Make migrations backward compatible** — old code must work with new schema
3. **Test backup restores monthly** — untested backups are not backups
4. **Use CONCURRENTLY for indexes** — avoid production lock issues
5. **Batch large data changes** — never single massive UPDATE
6. **Set up PITR** — you will eventually need point-in-time recovery
7. **Monitor migration execution time** — catch slow migrations in staging
8. **Separate schema migration from data migration** — different lifecycles
9. **Automate failover** — manual failover extends outages
10. **Document runbooks** — disaster recovery should not require invention

---

## Production Considerations

### Migration Checklist

```
PRE-DEPLOYMENT:
□ Migration tested on staging with production-size data
□ Execution time measured (< 1 minute for online migration)
□ Backward compatible (old code still works)
□ Rollback plan documented
□ No table-level locks for long duration
□ Indexes created with CONCURRENTLY

DEPLOYMENT:
□ Run migration
□ Monitor for lock contention
□ Verify application still healthy
□ Check error rates

POST-DEPLOYMENT:
□ Verify migration completed successfully
□ Check query performance (new indexes being used?)
□ Monitor for any regression
□ Schedule cleanup migration (if expand-contract)
```

### Disaster Recovery Runbook

```
SCENARIO: Primary database unresponsive

1. ASSESS (< 1 min)
   □ Is it a network issue or DB crash?
   □ Can we connect directly?
   □ Check cloud provider status

2. FAILOVER (< 5 min)
   □ Verify standby is caught up
   □ Promote standby: pg_ctl promote
   □ Update connection endpoint/DNS
   □ Verify application reconnects

3. VERIFY (< 10 min)
   □ Application functional?
   □ Data integrity check (spot check recent data)
   □ Monitor error rates
   □ Notify stakeholders

4. STABILIZE
   □ Investigate root cause of primary failure
   □ Set up new standby from promoted primary
   □ Resume WAL archiving
   □ Update monitoring for new topology
```

---

## Related Topics

- Topic 15: Transactions, ACID, Isolation Levels
- Topic 18: Partitioning, Replication, Sharding
- Topic 22: PostgreSQL Specifics (VACUUM, WAL)
- Topic 25: Advanced Database Performance
- Topic 26: Database Architecture & System Design
