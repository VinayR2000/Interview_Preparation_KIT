# Topic 30: Database Security

## Theory

### Security Layers

```
┌─────────────────────────────────────────────────────────────────┐
│              DATABASE SECURITY LAYERS                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Layer 1: NETWORK                                                │
│  • Firewall rules (only allow app servers)                       │
│  • VPC/private subnet (DB not internet-accessible)               │
│  • TLS/SSL encryption in transit                                 │
│                                                                  │
│  Layer 2: AUTHENTICATION                                         │
│  • Strong passwords / certificate-based auth                     │
│  • Separate users per service (not shared credentials)           │
│  • Rotate credentials regularly                                  │
│                                                                  │
│  Layer 3: AUTHORIZATION                                          │
│  • Least privilege principle                                     │
│  • GRANT only needed permissions per role                        │
│  • Row-level security for multi-tenant                           │
│                                                                  │
│  Layer 4: APPLICATION                                            │
│  • Parameterized queries (prevent SQL injection)                 │
│  • Input validation                                              │
│  • ORM/prepared statements                                       │
│                                                                  │
│  Layer 5: DATA                                                   │
│  • Encryption at rest                                            │
│  • Column-level encryption for PII                               │
│  • Data masking for non-production environments                  │
│                                                                  │
│  Layer 6: AUDIT                                                  │
│  • Query logging (who accessed what)                             │
│  • Change tracking (who modified what)                           │
│  • Compliance reporting                                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## SQL Injection — The Most Critical Vulnerability

### How SQL Injection Works

```
┌─────────────────────────────────────────────────────────────────┐
│                SQL INJECTION EXPLAINED                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  VULNERABLE CODE:                                                │
│  String sql = "SELECT * FROM users WHERE email = '"              │
│               + userInput + "' AND password = '" + password + "'";│
│                                                                  │
│  NORMAL INPUT:                                                   │
│  email = "alice@test.com"                                        │
│  SQL → SELECT * FROM users WHERE email = 'alice@test.com'        │
│         AND password = '...'                                     │
│                                                                  │
│  MALICIOUS INPUT:                                                │
│  email = "' OR '1'='1' --"                                       │
│  SQL → SELECT * FROM users WHERE email = '' OR '1'='1' --'      │
│         AND password = '...'                                     │
│  Result: Returns ALL users! (bypasses authentication)            │
│                                                                  │
│  DESTRUCTIVE INPUT:                                              │
│  email = "'; DROP TABLE users; --"                               │
│  SQL → SELECT * FROM users WHERE email = '';                     │
│         DROP TABLE users; --' AND password = '...'               │
│  Result: TABLE DELETED!                                          │
│                                                                  │
│  DATA EXFILTRATION:                                              │
│  email = "' UNION SELECT credit_card, cvv, '','','' FROM payments--"│
│  Result: Returns credit card data from payments table!           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Prevention — Parameterized Queries

```java
// ❌ VULNERABLE — String concatenation
public User findUser(String email) {
    String sql = "SELECT * FROM users WHERE email = '" + email + "'";
    return jdbcTemplate.queryForObject(sql, new UserRowMapper());
}

// ✅ SAFE — Parameterized query (JdbcTemplate)
public User findUser(String email) {
    String sql = "SELECT * FROM users WHERE email = ?";
    return jdbcTemplate.queryForObject(sql, new UserRowMapper(), email);
}

// ✅ SAFE — Named parameters
public User findUser(String email) {
    String sql = "SELECT * FROM users WHERE email = :email";
    MapSqlParameterSource params = new MapSqlParameterSource("email", email);
    return namedTemplate.queryForObject(sql, params, new UserRowMapper());
}

// ✅ SAFE — JPA/Hibernate (always parameterized)
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// ✅ SAFE — Spring Data derived methods (auto-parameterized)
Optional<User> findByEmail(String email);

// ❌ VULNERABLE — JPQL with concatenation
@Query("SELECT u FROM User u WHERE u.email = '" + email + "'") // NEVER DO THIS
```

### Dynamic Queries — Safe Approaches

```java
// ❌ VULNERABLE — building WHERE clause with string concat
public List<User> search(String name, String status) {
    String sql = "SELECT * FROM users WHERE 1=1";
    if (name != null) {
        sql += " AND name = '" + name + "'"; // SQL INJECTION!
    }
    return jdbcTemplate.query(sql, new UserRowMapper());
}

// ✅ SAFE — Criteria API (JPA)
public List<User> search(String name, String status) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<User> query = cb.createQuery(User.class);
    Root<User> root = query.from(User.class);
    
    List<Predicate> predicates = new ArrayList<>();
    if (name != null) {
        predicates.add(cb.equal(root.get("name"), name)); // Parameterized
    }
    if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
    }
    query.where(predicates.toArray(new Predicate[0]));
    return em.createQuery(query).getResultList();
}

// ✅ SAFE — Spring Data Specifications
public List<User> search(String name, String status) {
    Specification<User> spec = Specification.where(null);
    if (name != null) {
        spec = spec.and((root, q, cb) -> cb.equal(root.get("name"), name));
    }
    if (status != null) {
        spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    }
    return userRepository.findAll(spec);
}
```

---

## Database Users & Roles

### PostgreSQL Role Management

```sql
-- Create roles (roles = users in PostgreSQL)
CREATE ROLE app_readonly LOGIN PASSWORD 'strong_pass_1';
CREATE ROLE app_readwrite LOGIN PASSWORD 'strong_pass_2';
CREATE ROLE app_admin LOGIN PASSWORD 'strong_pass_3';

-- Grant permissions — LEAST PRIVILEGE
-- Read-only role (for reporting, analytics)
GRANT CONNECT ON DATABASE mydb TO app_readonly;
GRANT USAGE ON SCHEMA public TO app_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO app_readonly;

-- Read-write role (for application)
GRANT CONNECT ON DATABASE mydb TO app_readwrite;
GRANT USAGE ON SCHEMA public TO app_readwrite;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_readwrite;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_readwrite;
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_readwrite;

-- Admin role (for migrations — used only during deployment)
GRANT ALL PRIVILEGES ON DATABASE mydb TO app_admin;
GRANT ALL PRIVILEGES ON SCHEMA public TO app_admin;

-- REVOKE dangerous permissions from application user
REVOKE CREATE ON SCHEMA public FROM app_readwrite;
REVOKE ALL ON DATABASE mydb FROM PUBLIC;

-- View current grants
SELECT grantee, privilege_type, table_name
FROM information_schema.role_table_grants
WHERE table_schema = 'public';
```

### Multi-Service Setup

```sql
-- Each microservice gets its own user
CREATE ROLE order_service LOGIN PASSWORD '${ORDER_DB_PASS}';
CREATE ROLE payment_service LOGIN PASSWORD '${PAYMENT_DB_PASS}';
CREATE ROLE inventory_service LOGIN PASSWORD '${INVENTORY_DB_PASS}';

-- Order service: full access to orders, read-only to products
GRANT SELECT, INSERT, UPDATE ON orders, order_items TO order_service;
GRANT SELECT ON products TO order_service;
-- Cannot access payment tables!

-- Payment service: full access to payments only
GRANT SELECT, INSERT, UPDATE ON payments, refunds TO payment_service;
-- Cannot access order tables!
```

---

## Row-Level Security (RLS)

```sql
-- Multi-tenant isolation using RLS
-- Each tenant can only see their own data

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

-- Policy: users can only see their own tenant's orders
CREATE POLICY tenant_isolation ON orders
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant')::bigint);

-- Set tenant context at connection time
SET app.current_tenant = '42';

-- Now: SELECT * FROM orders only returns tenant 42's orders
-- Even if someone bypasses application logic, DB enforces isolation!

-- Force RLS even for table owner
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
```

```java
// Spring Boot — set tenant context per request
@Component
public class TenantInterceptor extends HandlerInterceptorAdapter {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader("X-Tenant-ID");
        // Validate tenantId...
        
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                "SET app.current_tenant = '" + sanitize(tenantId) + "'");
        }
        return true;
    }
}
```

---

## Encryption

### Encryption at Rest

```
┌─────────────────────────────────────────────────────────────────┐
│            ENCRYPTION AT REST                                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  TRANSPARENT DATA ENCRYPTION (TDE):                              │
│  • Encrypts data files on disk                                   │
│  • Transparent to application (no code changes)                  │
│  • Available in: AWS RDS, Azure, managed PostgreSQL              │
│  • Protects against: physical theft, disk access                 │
│                                                                  │
│  COLUMN-LEVEL ENCRYPTION:                                        │
│  • Encrypt specific columns (SSN, credit card)                   │
│  • Application encrypts before INSERT, decrypts after SELECT     │
│  • Protects against: unauthorized DB access                      │
│  • Trade-off: can't query encrypted columns                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Column-Level Encryption

```java
// Using pgcrypto extension
// CREATE EXTENSION pgcrypto;

@Entity
public class Customer {
    @Id
    private Long id;
    
    private String name;
    
    // Encrypted at application level
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ssn_encrypted")
    private String ssn;
}

// JPA AttributeConverter for transparent encryption
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    
    private final String encryptionKey = System.getenv("ENCRYPTION_KEY");
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // ... encryption logic
            return Base64.encode(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // ... decryption logic
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

### Encryption in Transit (TLS/SSL)

```yaml
# Spring Boot — connect to PostgreSQL with SSL
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/mydb?sslmode=verify-full&sslrootcert=/certs/ca.pem
    # sslmode options:
    # disable    — no SSL
    # allow      — try SSL, fall back to non-SSL
    # prefer     — try SSL, fall back to non-SSL (default)
    # require    — must use SSL (no cert verification)
    # verify-ca  — must use SSL, verify CA
    # verify-full — must use SSL, verify CA + hostname (RECOMMENDED)
```

---

## Secrets Management

```
┌─────────────────────────────────────────────────────────────────┐
│         DATABASE CREDENTIALS MANAGEMENT                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ❌ NEVER:                                                       │
│  • Hardcode passwords in application.yml                         │
│  • Commit .env files with passwords to git                       │
│  • Use same password across environments                         │
│  • Share credentials between services                            │
│                                                                  │
│  ✅ USE:                                                         │
│  • Environment variables (basic)                                 │
│  • Vault (HashiCorp Vault) — dynamic secrets                    │
│  • AWS Secrets Manager / Parameter Store                         │
│  • Kubernetes Secrets (encrypted at rest)                        │
│                                                                  │
│  BEST: Dynamic secrets (Vault generates time-limited credentials)│
│  → Credentials auto-rotate, leaks have limited blast radius      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

```yaml
# application.yml — reference secrets from environment
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

# Kubernetes secret
# kubectl create secret generic db-credentials \
#   --from-literal=DB_URL=jdbc:postgresql://... \
#   --from-literal=DB_USERNAME=app_user \
#   --from-literal=DB_PASSWORD=...
```

---

## Dry Run — SQL Injection Attack & Defense

```
ATTACK SCENARIO:
────────────────
Login form: email + password

1. Attacker enters:
   email: admin@company.com
   password: ' OR '1'='1

2. Vulnerable code builds:
   SELECT * FROM users 
   WHERE email = 'admin@company.com' 
   AND password = '' OR '1'='1'

3. SQL parses as:
   WHERE (email = 'admin@company.com' AND password = '') OR ('1'='1')
   → '1'='1' is always true
   → Returns ALL users
   → Attacker logged in as first user (usually admin)!

DEFENSE (parameterized query):
──────────────────────────────
1. Application sends:
   SQL template: "SELECT * FROM users WHERE email = ? AND password = ?"
   Parameters: ["admin@company.com", "' OR '1'='1"]

2. Database receives:
   SELECT * FROM users 
   WHERE email = 'admin@company.com' 
   AND password = ''' OR ''1''=''1'
   (Quotes are ESCAPED — treated as literal string data)

3. Query returns 0 rows (no user with that literal password)
   → Login fails. Attack neutralized.
```

---

## Interview Questions and Answers

### Q1: How do you prevent SQL injection?

**Answer:**

**Primary defense: Parameterized queries (prepared statements)**
- Never concatenate user input into SQL strings
- Use `?` placeholders (JDBC) or `:param` (named parameters)
- JPA/Hibernate automatically parameterizes
- Spring Data repository methods are safe by default

**Secondary defenses (defense-in-depth):**
- Input validation (whitelist allowed characters)
- Least privilege (app user can't DROP tables)
- WAF (Web Application Firewall) as additional layer
- Escape output (prevent stored XSS via DB)

**In Spring Boot**, you're safe if you use:
- `JpaRepository` methods
- `@Query` with parameters
- `JdbcTemplate` with `?` placeholders
- `Specification/Criteria API`

You're vulnerable if you concatenate strings in `@Query` annotations or native SQL.

### Q2: Explain the principle of least privilege for databases.

**Answer:**

Each database user/role should have only the minimum permissions needed:

- **Application user**: SELECT, INSERT, UPDATE, DELETE on specific tables. No CREATE/DROP.
- **Migration user**: DDL privileges (used only during deployment, not at runtime)
- **Reporting user**: SELECT only on specific tables/views
- **Backup user**: pg_read_all_data role (read-only for backup)

Implementation:
```sql
-- App can read/write data but can't change schema
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES TO app_user;
REVOKE CREATE, DROP ON SCHEMA public FROM app_user;
```

If the application is compromised, the attacker can't DROP tables or access other services' data.

### Q3: How do you handle database credentials in production?

**Answer:**

1. **Never in code/config files** committed to git
2. **Environment variables** as minimum (set at deployment time)
3. **Secrets manager** (Vault, AWS Secrets Manager) for:
   - Centralized management
   - Automatic rotation
   - Audit trail of access
   - Time-limited credentials
4. **Dynamic secrets** (Vault generates per-pod credentials):
   - Each pod gets unique credentials
   - Auto-expire after TTL
   - Revoke individual pod access if compromised

### Q4: What is Row-Level Security and when would you use it?

**Answer:**

RLS allows the database to automatically filter rows based on the current user/context. The WHERE clause is implicitly added to every query.

**Use cases:**
- Multi-tenant SaaS (tenant sees only their data)
- Department-level access (users see only their department)
- PII protection (role determines which columns are visible)

**Benefit:** Even if application code has a bug that doesn't filter by tenant, the database enforces isolation. It's defense-in-depth.

---

## Follow-up Questions and Answers

### Q: Can SQL injection happen with ORM (Hibernate)?

**Answer:**

ORMs like Hibernate are generally safe because they parameterize by default. However, injection is still possible if you:
- Use `createNativeQuery()` with string concatenation
- Build JPQL with concatenation
- Use `@Query` with inline string variables (compile-time substitution)
- Call stored procedures with concatenated parameters

```java
// VULNERABLE even with Hibernate:
em.createNativeQuery("SELECT * FROM users WHERE name = '" + input + "'");

// SAFE:
em.createNativeQuery("SELECT * FROM users WHERE name = ?1")
  .setParameter(1, input);
```

### Q: How do you handle data masking for non-production environments?

**Answer:**

Production data copied to staging/dev must be anonymized:
- Replace real emails: `email → user_[id]@example.com`
- Mask names: `"John Smith" → "User 12345"`
- Randomize financial data: keep distribution, change values
- Preserve referential integrity (IDs stay consistent)

Tools: pg_anonymize, PostgreSQL Anonymizer, custom scripts

---

## Common Mistakes

| Mistake | Impact | Fix |
|---|---|---|
| String concatenation in SQL | SQL injection | Always use parameterized queries |
| Using root/admin for application | Full access if compromised | Dedicated least-privilege user |
| Same DB password everywhere | One breach exposes all | Unique per environment/service |
| Passwords in git | Exposed in history forever | Secrets manager + env vars |
| No encryption in transit | Credentials sniffable | Require SSL (sslmode=verify-full) |
| Overly permissive GRANT | App can DROP tables | Grant minimum needed permissions |
| No audit logging | Can't trace who did what | Enable pg_audit or trigger-based logging |
| Shared service accounts | Can't isolate breach | One user per service |

---

## Best Practices

1. **Always use parameterized queries** — no exceptions
2. **Separate DB users per service** with minimum privileges
3. **Encrypt in transit** (TLS) and at rest
4. **Use secrets management** (Vault, AWS Secrets Manager)
5. **Enable Row-Level Security** for multi-tenant applications
6. **Rotate credentials** regularly (automate with Vault)
7. **Audit all access** — know who queried/changed data
8. **Never expose DB to internet** — private subnet only
9. **Mask production data** in non-production environments
10. **Run regular security audits** — SQL injection testing, permission review

---

## Production Considerations

### Security Checklist

```
□ Database in private subnet (no public IP)
□ Firewall rules: only app servers can connect
□ TLS required for all connections (sslmode=verify-full)
□ Application user has minimum permissions (no DDL)
□ Separate migration user for schema changes
□ Credentials in secrets manager (not env files)
□ Credential rotation enabled
□ Row-level security for multi-tenant data
□ pg_audit enabled for compliance
□ Query logging for security review
□ PII encrypted at column level
□ Backup encryption enabled
□ Non-production environments use masked data
```

### PostgreSQL Security Settings

```sql
-- postgresql.conf
ssl = on
ssl_cert_file = '/certs/server.crt'
ssl_key_file = '/certs/server.key'
ssl_ca_file = '/certs/ca.crt'

-- pg_hba.conf (strict authentication)
# TYPE  DATABASE  USER        ADDRESS         METHOD
hostssl all       all         10.0.0.0/8      scram-sha-256
host    all       all         0.0.0.0/0       reject

-- Force password complexity
CREATE EXTENSION passwordcheck;

-- Enable audit logging
CREATE EXTENSION pgaudit;
SET pgaudit.log = 'write, ddl';
SET pgaudit.log_catalog = off;
```

---

## Related Topics

- Topic 24: JDBC & Connection Pooling (prepared statements)
- Topic 27: SQL Interview Scenarios
- Topic 29: Production-Level Topics
- Topic 22: PostgreSQL Specifics (roles, pg_hba.conf)
