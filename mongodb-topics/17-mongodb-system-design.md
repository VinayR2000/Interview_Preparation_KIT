# MongoDB System Design

## Design Process

For every MongoDB system design, ask these questions in order:

```
1. What are the access patterns?
         ↓
2. Embed or reference?
         ↓
3. What indexes are needed?
         ↓
4. SQL or MongoDB (is MongoDB the right choice)?
         ↓
5. Read/write ratio?
         ↓
6. Replication strategy?
         ↓
7. Sharding needed?
         ↓
8. Consistency requirements?
         ↓
9. Failure handling?
```

---

## Beginner Designs

### 1. User Profile System

**Access Patterns:**
- Get user by ID (most frequent)
- Get user by email (login)
- Update profile fields
- Search users by name

**Schema:**
```javascript
// users collection
{
  _id: ObjectId("..."),
  email: "vinay@example.com",
  passwordHash: "$2b$10$...",
  profile: {
    firstName: "Vinay",
    lastName: "Kumar",
    bio: "Backend developer",
    avatar: "https://cdn.example.com/avatars/vinay.jpg",
    dateOfBirth: ISODate("1996-03-15"),
    phone: "+91-9876543210"
  },
  address: {
    street: "123 Main St",
    city: "Bangalore",
    state: "Karnataka",
    country: "India",
    pincode: "560001"
  },
  preferences: {
    language: "en",
    timezone: "Asia/Kolkata",
    notifications: { email: true, push: true, sms: false }
  },
  social: {
    github: "vinay-dev",
    linkedin: "vinay-kumar"
  },
  status: "active",
  roles: ["user", "developer"],
  lastLogin: ISODate("2024-06-15T10:30:00Z"),
  createdAt: ISODate("2024-01-15T08:00:00Z"),
  updatedAt: ISODate("2024-06-15T10:30:00Z")
}
```

**Indexes:**
```javascript
db.users.createIndex({ email: 1 }, { unique: true })
db.users.createIndex({ "profile.firstName": 1, "profile.lastName": 1 })
db.users.createIndex({ status: 1, lastLogin: -1 })
```

**Design Decisions:**
- Embed profile, address, preferences (always accessed together, 1:1)
- Email unique index for fast login lookups
- TTL on sessions (separate collection)

---

### 2. Product Catalog

**Access Patterns:**
- Browse by category
- Search by name/keywords
- Filter by price range, brand, ratings
- Get product details

**Schema:**
```javascript
// products collection
{
  _id: ObjectId("..."),
  sku: "LAPTOP-PRO-001",
  name: "MacBook Pro 14-inch",
  slug: "macbook-pro-14-inch",
  description: "Professional laptop with M3 chip...",
  category: { id: "electronics", path: "electronics/laptops/apple" },
  brand: "Apple",
  price: {
    amount: 1999.00,
    currency: "USD",
    discountPercent: 10,
    effectivePrice: 1799.10
  },
  inventory: {
    available: 150,
    reserved: 12,
    warehouse: "WH-BLR-01"
  },
  attributes: {
    color: "Space Gray",
    storage: "512GB",
    ram: "16GB",
    processor: "M3 Pro"
  },
  images: [
    { url: "https://cdn.../main.jpg", alt: "Front view", isPrimary: true },
    { url: "https://cdn.../side.jpg", alt: "Side view", isPrimary: false }
  ],
  ratings: {
    average: 4.7,
    count: 2847,
    distribution: { "5": 1823, "4": 712, "3": 198, "2": 67, "1": 47 }
  },
  tags: ["laptop", "apple", "professional", "m3"],
  status: "active",
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

**Indexes:**
```javascript
db.products.createIndex({ "category.path": 1, "price.effectivePrice": 1 })
db.products.createIndex({ brand: 1, "ratings.average": -1 })
db.products.createIndex({ status: 1, "category.id": 1, "price.effectivePrice": 1 })
db.products.createIndex({ tags: 1 })  // Multikey
db.products.createIndex({ name: "text", description: "text", tags: "text" },
                        { weights: { name: 10, tags: 5, description: 1 } })
db.products.createIndex({ sku: 1 }, { unique: true })
```

---

### 3. Blog System

**Schema:**
```javascript
// posts collection
{
  _id: ObjectId("..."),
  title: "MongoDB Data Modeling Best Practices",
  slug: "mongodb-data-modeling-best-practices",
  content: "Full markdown content...",
  excerpt: "Learn how to model data effectively...",
  authorId: ObjectId("..."),
  authorSnapshot: { name: "Vinay Kumar", avatar: "url" },
  category: "databases",
  tags: ["mongodb", "data-modeling", "nosql"],
  status: "published",  // draft, published, archived
  comments: [  // Embedded — bounded (last 20)
    {
      userId: ObjectId("..."),
      userName: "Alice",
      text: "Great article!",
      createdAt: ISODate("...")
    }
  ],
  commentCount: 47,  // Total (including overflow)
  stats: {
    views: 12450,
    likes: 234,
    shares: 56
  },
  publishedAt: ISODate("2024-06-15T08:00:00Z"),
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}

// comments collection (for overflow / full comment history)
{
  _id: ObjectId("..."),
  postId: ObjectId("..."),
  userId: ObjectId("..."),
  userName: "Bob",
  text: "Very helpful explanation of the bucket pattern.",
  parentCommentId: ObjectId("..."),  // For nested replies
  likes: 5,
  createdAt: ISODate("...")
}
```

**Design Pattern**: Subset pattern — recent comments embedded in post, full comments in separate collection.

---

## Intermediate Designs

### 4. E-Commerce Order System

**Schema:**
```javascript
// orders collection
{
  _id: ObjectId("..."),
  orderNumber: "ORD-20240615-001",
  customerId: ObjectId("..."),
  customerSnapshot: {
    name: "Vinay Kumar",
    email: "vinay@example.com",
    phone: "+91-9876543210"
  },
  items: [
    {
      productId: ObjectId("..."),
      sku: "LAPTOP-PRO-001",
      name: "MacBook Pro",
      price: 1999.00,
      quantity: 1,
      subtotal: 1999.00
    }
  ],
  pricing: {
    subtotal: 1999.00,
    tax: 359.82,
    shipping: 0,
    discount: 199.90,
    total: 2158.92
  },
  shippingAddress: {
    name: "Vinay Kumar",
    street: "123 Main St",
    city: "Bangalore",
    state: "Karnataka",
    pincode: "560001",
    country: "India"
  },
  payment: {
    method: "card",
    provider: "stripe",
    transactionId: "txn_abc123",
    last4: "4242",
    status: "captured"
  },
  status: "shipped",
  timeline: [
    { status: "placed", at: ISODate("..."), note: "Order placed" },
    { status: "confirmed", at: ISODate("...") },
    { status: "processing", at: ISODate("...") },
    { status: "shipped", at: ISODate("..."), trackingId: "TRK123" }
  ],
  createdAt: ISODate("2024-06-15T10:30:00Z"),
  updatedAt: ISODate("2024-06-16T14:00:00Z")
}
```

**Indexes:**
```javascript
db.orders.createIndex({ customerId: 1, createdAt: -1 })
db.orders.createIndex({ orderNumber: 1 }, { unique: true })
db.orders.createIndex({ status: 1, createdAt: -1 })
db.orders.createIndex({ "items.productId": 1 })
db.orders.createIndex({ "payment.transactionId": 1 })
```

**Design Decisions:**
- Embed items (always displayed with order, bounded)
- Embed timeline (bounded, < 10 status changes)
- Customer snapshot (historical — not affected by profile updates)
- Price snapshot (historical — not affected by price changes)
- Reference customerId for customer lookup

---

### 5. Notification System

**Schema:**
```javascript
// notifications collection
{
  _id: ObjectId("..."),
  userId: ObjectId("..."),
  type: "order_shipped",
  channel: "push",  // push, email, sms, in_app
  title: "Your order has shipped!",
  body: "Order ORD-001 is on its way.",
  data: {
    orderId: "ORD-001",
    trackingUrl: "https://..."
  },
  status: "delivered",  // pending, sent, delivered, failed, read
  priority: "high",     // low, medium, high, critical
  sentAt: ISODate("..."),
  deliveredAt: ISODate("..."),
  readAt: null,
  expiresAt: ISODate("2024-07-15T00:00:00Z"),  // TTL
  createdAt: ISODate("...")
}
```

**Indexes:**
```javascript
db.notifications.createIndex({ userId: 1, createdAt: -1 })
db.notifications.createIndex({ userId: 1, status: 1, createdAt: -1 })
db.notifications.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 })  // TTL
db.notifications.createIndex({ status: 1, channel: 1 },
  { partialFilterExpression: { status: "pending" } })  // Only index pending
```

---

### 6. Audit Logging System

**Schema (Bucket Pattern):**
```javascript
// audit_logs collection — bucketed by hour
{
  _id: ObjectId("..."),
  entityType: "order",
  entityId: "ORD-001",
  date: ISODate("2024-06-15"),
  hour: 14,
  events: [
    {
      action: "status_update",
      userId: ObjectId("..."),
      userName: "Vinay",
      timestamp: ISODate("2024-06-15T14:05:23Z"),
      before: { status: "processing" },
      after: { status: "shipped" },
      metadata: { ip: "10.0.1.5", userAgent: "..." }
    },
    {
      action: "note_added",
      userId: ObjectId("..."),
      userName: "System",
      timestamp: ISODate("2024-06-15T14:10:45Z"),
      details: { note: "Tracking ID assigned: TRK123" }
    }
  ],
  eventCount: 2
}
```

**Why Bucket Pattern?**
- Millions of events → document per event = very large collection
- Bucketing by hour reduces document count by ~60x
- Still queryable by time range
- Efficient for "show me all changes to order X today"

---

## Advanced Designs

### 7. Real-Time Analytics Dashboard

**Access Patterns:**
- Real-time metrics (last 5 min, last 1 hour)
- Historical aggregations (daily, weekly, monthly)
- Per-service breakdown
- Alerting on thresholds

**Schema:**
```javascript
// metrics_realtime (time-series collection)
db.createCollection("metrics_realtime", {
  timeseries: {
    timeField: "timestamp",
    metaField: "service",
    granularity: "seconds"
  },
  expireAfterSeconds: 86400  // Keep 24 hours only
})

// Document
{
  timestamp: ISODate("2024-06-15T14:05:23Z"),
  service: { name: "order-service", instance: "order-service-pod-1" },
  requestCount: 45,
  errorCount: 2,
  avgLatency: 125.5,
  p99Latency: 450.0,
  cpuUsage: 67.8,
  memoryUsage: 72.3
}

// metrics_hourly (pre-aggregated)
{
  service: "order-service",
  date: ISODate("2024-06-15"),
  hour: 14,
  requests: { total: 54000, errors: 230, rate: 15.0 },
  latency: { avg: 120, p50: 85, p95: 300, p99: 500, max: 1200 },
  resources: { avgCpu: 65.2, avgMemory: 70.1, peakCpu: 89.5 }
}

// metrics_daily (pre-aggregated)
{
  service: "order-service",
  date: ISODate("2024-06-15"),
  requests: { total: 1200000, errors: 5400, errorRate: 0.45 },
  latency: { avg: 115, p95: 280, p99: 470 },
  availability: 99.95
}
```

**Architecture:**
```
Services emit metrics → Kafka → Metrics Consumer → MongoDB (realtime)
                                        ↓
                              Aggregation Job (every hour) → metrics_hourly
                                        ↓
                              Aggregation Job (daily) → metrics_daily
                                        ↓
                              Dashboard reads from pre-aggregated collections
```

---

### 8. Social Media Feed

**Design Challenge**: Millions of users, each following thousands of others. Feed must be fast.

**Schema:**
```javascript
// posts collection
{
  _id: ObjectId("..."),
  authorId: ObjectId("..."),
  content: "Just deployed my MongoDB-powered microservice!",
  media: [{ type: "image", url: "https://..." }],
  hashtags: ["mongodb", "microservices"],
  mentions: [ObjectId("...")],
  stats: {
    likes: 234,
    comments: 45,
    shares: 12
  },
  visibility: "public",
  createdAt: ISODate("...")
}

// user_feeds collection (fan-out on write)
{
  userId: ObjectId("user-who-should-see-this"),
  postId: ObjectId("the-post"),
  authorId: ObjectId("who-wrote-it"),
  createdAt: ISODate("..."),
  score: 0.85  // Relevance score for ranking
}

// follows collection
{
  followerId: ObjectId("..."),
  followingId: ObjectId("..."),
  createdAt: ISODate("...")
}
```

**Feed Strategy:**
- **Fan-out on write**: When user posts, write to all followers' feeds
- **Fan-out on read**: When user opens feed, pull posts from all followed users
- **Hybrid**: Fan-out on write for normal users, fan-out on read for celebrities

**Indexes:**
```javascript
db.posts.createIndex({ authorId: 1, createdAt: -1 })
db.user_feeds.createIndex({ userId: 1, createdAt: -1 })
db.user_feeds.createIndex({ userId: 1, score: -1 })
db.follows.createIndex({ followerId: 1, followingId: 1 }, { unique: true })
db.follows.createIndex({ followingId: 1 })
```

**Sharding**: Shard `user_feeds` by `userId` (each user's feed is targeted)

---

### 9. Distributed Event Processing

**Schema:**
```javascript
// events collection (event store)
{
  _id: ObjectId("..."),
  aggregateId: "order-ORD-001",
  aggregateType: "Order",
  eventType: "OrderPlaced",
  version: 1,
  payload: {
    customerId: "C1",
    items: [...],
    total: 999
  },
  metadata: {
    correlationId: "req-abc123",
    causationId: "cmd-xyz789",
    userId: "vinay",
    timestamp: ISODate("...")
  },
  processed: false
}

// outbox collection (transactional outbox pattern)
{
  _id: ObjectId("..."),
  aggregateId: "order-ORD-001",
  eventType: "OrderPlaced",
  payload: { ... },
  status: "pending",  // pending, published, failed
  retryCount: 0,
  createdAt: ISODate("..."),
  publishedAt: null
}
```

**Pattern: Transactional Outbox with Change Streams**
```
Application writes (in transaction):
  1. Update aggregate in domain collection
  2. Insert event into outbox collection
       ↓
Change Stream on outbox collection
       ↓
Outbox processor reads new events
       ↓
Publish to Kafka
       ↓
Mark as published (update outbox status)
```

---

## Design Decision Matrix

| Scenario | SQL or MongoDB? | Why? |
|----------|----------------|------|
| User profiles with flexible attributes | MongoDB | Schema flexibility, embedded data |
| Financial ledger with strict consistency | SQL | ACID across entities, strict schema |
| Product catalog with varying attributes | MongoDB | Polymorphic documents, no JOINs |
| IoT sensor data (millions/hour) | MongoDB | Time-series, horizontal scaling |
| Social media posts + feed | MongoDB | High write throughput, flexible schema |
| Banking transactions | SQL | Multi-table ACID, referential integrity |
| Content management | MongoDB | Nested content, schema flexibility |
| Audit logging | MongoDB | High write volume, TTL, time-series |
| E-commerce orders | Either | MongoDB for read-heavy, SQL for complex reporting |
| Real-time analytics | MongoDB | Time-series, aggregation pipeline |

---

## Interview Questions

**Q: Design a high-scale product catalog with MongoDB.**
A: Schema: Products collection with embedded attributes (polymorphic pattern), categories, pricing. Subset pattern for reviews (embed recent 10, full reviews in separate collection). Indexes: category + price for browse, text index for search, brand + rating for filters. Sharding: by category (targeted queries) or hashed productId (even distribution). Caching: Redis for hot products. Change streams to sync to Elasticsearch for full-text search.

**Q: How would you design a notification system?**
A: Notifications collection with TTL index for auto-cleanup. Compound index on userId + status + createdAt for "unread notifications for user X" queries. Partial index on status: "pending" for the delivery processor. Change streams or polling for delivery. Fan-out: one document per user per notification. Sharding by userId.

**Q: Your MongoDB-based order system needs to guarantee consistency between order creation and inventory update. How?**
A: Option 1: Embed inventory count in the product document and use findOneAndUpdate with condition `{ qty: { $gte: requestedQty } }` for atomic decrement. Option 2: Multi-document transaction across orders and inventory collections. Option 3: Saga pattern with compensating transactions. Prefer Option 1 if feasible (single-document atomicity, no transaction overhead).

**Q: When would you NOT choose MongoDB?**
A: When you need complex multi-table JOINs frequently, strict referential integrity across many entities, complex reporting across heavily normalized data, or when your team/infrastructure is already optimized for SQL. Also avoid MongoDB when you can't clearly define access patterns upfront (ad-hoc analytical queries favor columnar databases).
