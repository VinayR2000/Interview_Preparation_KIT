# HLD Interview Problems — Beginner

## Approach Framework

### The 13-Step Process
```
1. Requirements (Functional + Non-functional)
     ↓
2. Scale / Capacity Estimation
     ↓
3. API Design
     ↓
4. High-Level Architecture
     ↓
5. Database Design
     ↓
6. Caching Strategy
     ↓
7. Messaging / Async Processing
     ↓
8. Scaling Strategy
     ↓
9. Reliability / Fault Tolerance
     ↓
10. Security
     ↓
11. Observability (Monitoring, Logging, Tracing)
     ↓
12. Bottlenecks (identify and address)
     ↓
13. Trade-offs (explain decisions)
```

---

## 1. URL Shortener (like bit.ly)

### Requirements
**Functional:**
- Given a long URL, generate a short unique URL
- Redirect short URL to original long URL
- Optional: Custom short URLs, expiration, analytics

**Non-functional:**
- Very low latency for redirects (<100ms)
- High availability (redirects should always work)
- Short URLs should be as short as possible
- Non-predictable URLs

### Scale Estimation
```
Assumptions:
- 100M new URLs per month
- Read:Write ratio = 100:1 (1 create → 100 reads)
- URL stored for 5 years

Writes: 100M / (30 × 24 × 3600) ≈ 40 writes/sec
Reads: 40 × 100 = 4,000 reads/sec

Storage:
- 100M × 12 months × 5 years = 6 billion URLs
- Each record ~500 bytes = 6B × 500B = 3 TB
```

### API Design
```
POST /api/v1/shorten
  Body: { "longUrl": "https://...", "customAlias": "my-link", "expireAt": "..." }
  Response: { "shortUrl": "https://short.ly/abc123" }

GET /{shortCode}
  Response: 301/302 Redirect to long URL
```

### High-Level Architecture
```
┌──────┐    ┌───────────┐    ┌──────────────┐    ┌────────┐
│Client│───→│  API GW / │───→│  URL Service │───→│Database│
│      │    │    LB     │    │              │    │(NoSQL) │
└──────┘    └───────────┘    └──────┬───────┘    └────────┘
                                    │
                              ┌─────▼─────┐
                              │   Cache   │
                              │  (Redis)  │
                              └───────────┘
```

### Key Design Decisions

**Short Code Generation:**
| Approach | Pros | Cons |
|----------|------|------|
| MD5/SHA256 hash + first 7 chars | Simple | Collision possible |
| Base62 encoding of auto-increment ID | No collision | Predictable, needs counter |
| Pre-generated key service | Fast, no collision | Requires key management |
| Snowflake ID + Base62 | Unique, distributed | Longer code |

**Base62 Encoding:**
```
Characters: [0-9, a-z, A-Z] = 62 characters
6 chars → 62^6 = 56.8 billion combinations
7 chars → 62^7 = 3.5 trillion combinations
```

**Database Choice: NoSQL (DynamoDB/Cassandra)**
- Simple key-value access pattern
- No complex joins needed
- Needs horizontal scaling
- Schema: { shortCode (PK), longUrl, createdAt, expiresAt, userId }

**Caching:**
- Cache hot URLs in Redis (most URLs follow power law — few URLs get most traffic)
- Cache hit → redirect immediately (skip DB)
- TTL: 24 hours for cache entries
- LRU eviction

**301 vs 302 Redirect:**
| 301 Permanent | 302 Temporary |
|---------------|---------------|
| Browser caches | Browser doesn't cache |
| Less server load | Every visit hits server |
| Can't track clicks | Can track analytics |

---

## 2. Rate Limiter

### Requirements
**Functional:**
- Limit requests per user/IP/API-key within time window
- Return 429 Too Many Requests when exceeded
- Different limits for different endpoints/plans

**Non-functional:**
- Very low latency (should not slow down requests)
- Distributed (works across multiple servers)
- Accurate (not over-counting or under-counting)

### High-Level Architecture
```
┌──────┐    ┌───────────┐    ┌──────────────┐    ┌──────────┐
│Client│───→│    LB     │───→│ Rate Limiter │───→│  Backend │
└──────┘    └───────────┘    │ (Middleware) │    │  Service │
                             └──────┬───────┘    └──────────┘
                                    │
                              ┌─────▼─────┐
                              │   Redis   │
                              │ (Counters)│
                              └───────────┘
```

### Algorithms

**Sliding Window Counter (Recommended):**
```
Window: 1 minute, Limit: 100 requests

Current minute counter: 40 (at 75% through minute)
Previous minute counter: 80

Weighted count = prev × (1 - elapsed%) + current
               = 80 × 0.25 + 40
               = 20 + 40 = 60

60 < 100 → ALLOW
```

**Redis Implementation:**
```
-- Sliding window log with Redis Sorted Set
ZADD rate_limit:{userId} {timestamp} {requestId}
ZREMRANGEBYSCORE rate_limit:{userId} 0 {timestamp - window}
count = ZCARD rate_limit:{userId}
IF count > limit THEN reject
EXPIRE rate_limit:{userId} {window}
```

### Key Design Decisions
- **Where to rate limit**: API Gateway (centralized) vs application middleware (per-service)
- **Distributed counting**: Redis (shared counter across instances)
- **Race conditions**: Use Redis Lua scripts for atomic check-and-increment
- **Graceful handling**: Return Retry-After header, queue requests for VIPs

---

## 3. File Upload System

### Requirements
**Functional:**
- Upload files (images, documents, videos) up to 5GB
- Download files by URL
- Resume interrupted uploads
- File metadata (name, size, type, owner)

**Non-functional:**
- High durability (never lose a file)
- Low latency for downloads (CDN)
- Support large files without timeout

### High-Level Architecture
```
┌──────┐    ┌────────────┐    ┌──────────────┐
│Client│───→│  Upload    │───→│ Object Store │
│      │    │  Service   │    │  (S3)        │
└──────┘    └─────┬──────┘    └──────────────┘
                  │                    ▲
                  ▼                    │
            ┌──────────┐         ┌────┴─────┐
            │ Metadata │         │   CDN    │←── Download requests
            │   DB     │         └──────────┘
            └──────────┘
```

### Large File Upload — Multipart/Chunked
```
1. Client requests upload initiation
2. Server returns upload_id + pre-signed URLs for each chunk
3. Client uploads chunks in parallel (5MB each)
4. On completion: Client notifies server → server assembles
5. If interrupted: Client resumes from last successful chunk

Client                          Server                S3
  |-- InitiateUpload ---------->|                      |
  |<-- {uploadId, presignedUrls}|                      |
  |                             |                      |
  |-- PUT chunk 1 ────────────────────────────────────>|
  |-- PUT chunk 2 ────────────────────────────────────>|
  |-- PUT chunk 3 (fails, retry)──────────────────────>|
  |-- PUT chunk 3 (success)───────────────────────────>|
  |                             |                      |
  |-- CompleteUpload ---------->|-- Assemble --------->|
  |<-- {fileUrl} ---------------|                      |
```

---

## 4. Notification System

### Requirements
**Functional:**
- Send notifications via multiple channels: push, email, SMS
- Support different event triggers (order updates, promotions)
- User notification preferences
- Template-based messages

**Non-functional:**
- Delivery guarantee (at least once)
- Scale to millions of notifications/day
- Low latency for real-time notifications
- Retry on failure

### High-Level Architecture
```
┌──────────────┐    ┌─────────────┐    ┌─────────────────────────┐
│ Event Source │───→│   Message   │───→│  Notification Service   │
│(Order, Auth) │    │   Queue     │    │  (Priority + Routing)   │
└──────────────┘    │  (Kafka)    │    └─────┬────┬────┬─────────┘
                    └─────────────┘          │    │    │
                                            ▼    ▼    ▼
                                    ┌─────┐ ┌───┐ ┌──────┐
                                    │Push │ │SMS│ │Email │
                                    │Svc  │ │Svc│ │ Svc  │
                                    └──┬──┘ └─┬─┘ └──┬───┘
                                       │      │      │
                                       ▼      ▼      ▼
                                    [APNs] [Twilio] [SES]
```

### Key Design Decisions
- **Message Queue (Kafka)**: Decouple event producers from notification sending
- **Priority queues**: Critical (OTP, payment) > Important (order) > Low (promotional)
- **Fan-out**: One event → multiple channels based on user preferences
- **Rate limiting**: Don't spam users (aggregate notifications)
- **DLQ**: Failed notifications for retry/investigation
- **Template engine**: Reusable templates with variable substitution

---

## 5. Pastebin

### Requirements
**Functional:**
- Create text pastes with unique URL
- Read paste by URL
- Optional: expiration, syntax highlighting, private pastes, edit

**Non-functional:**
- Low latency reads
- High availability
- Paste content should be durable

### Key Design
- Very similar to URL shortener
- Additional: Store actual content (not just redirect)
- Object storage (S3) for paste content, metadata in DB
- CDN for frequently-accessed pastes
- Size limit per paste (10MB)

---

## 6. Simple Authentication System

### Requirements
**Functional:**
- User registration (email + password)
- Login (return token)
- Token validation on protected routes
- Password reset
- Logout (token invalidation)

**Non-functional:**
- Security (bcrypt hashing, TLS)
- Scalability (stateless token validation)
- Low latency for token validation

### High-Level Architecture
```
┌──────┐    ┌───────────┐    ┌──────────────┐    ┌──────┐
│Client│───→│  API GW   │───→│  Auth Service│───→│UserDB│
│      │    │(validate  │    │ (login/signup)│    │(hash)│
└──────┘    │  token)   │    └──────────────┘    └──────┘
            └───────────┘           │
                                    ▼
                              ┌───────────┐
                              │   Redis   │ (token blacklist
                              │           │  / refresh tokens)
                              └───────────┘
```

### JWT Flow
```
1. Login: Verify credentials → Generate access token (short-lived, 15 min)
                             → Generate refresh token (long-lived, 7 days)
2. API request: Validate access token signature + expiry (no DB lookup!)
3. Token expired: Use refresh token → Get new access token
4. Logout: Blacklist refresh token in Redis
```

### Key Design Decisions
- **Password storage**: bcrypt/argon2 with salt (never plain text)
- **Access token**: Short-lived JWT (15 min), stateless validation
- **Refresh token**: Long-lived, stored in Redis, can be revoked
- **Token blacklist**: Redis SET for revoked tokens (checked on refresh)

---

## Interview Tips for Beginner Problems

### Common Follow-ups
| Question | Key Point |
|----------|-----------|
| "How do you handle billions of URLs?" | Sharding by short code hash |
| "What if your rate limiter Redis goes down?" | Fallback: local rate limiting, circuit breaker |
| "How do you prevent duplicate uploads?" | Content hash (MD5/SHA256) deduplication |
| "What about notification delivery confirmation?" | Delivery receipts, retry with backoff |
| "How do you scale authentication?" | Stateless JWT, no DB lookup per request |

---

## Common Mistakes
- Over-engineering beginner problems (keep it simple first, then discuss scaling)
- Not doing capacity estimation (shows you understand scale)
- Forgetting about edge cases (what if URL shortener gets duplicate requests?)
- Not discussing trade-offs (301 vs 302, eventual vs strong consistency)
- Jumping to coding instead of architecture discussion

---

## Best Practices
- Start every problem with requirements clarification
- Do quick math for capacity (back-of-envelope)
- Draw the high-level diagram first, then deep dive
- Address non-functional requirements explicitly
- Discuss what happens when components fail
- End with trade-offs and potential improvements

---

## Related Topics
- HLD Interview Problems — Intermediate
- HLD Interview Problems — Advanced
- Capacity Estimation
- System Design Framework
