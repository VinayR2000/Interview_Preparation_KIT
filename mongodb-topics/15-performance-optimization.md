# Performance Optimization

## Performance Pillars

```
MongoDB Performance
├── Indexing (most impactful)
├── Query Optimization
├── Schema Design
├── Hardware & Configuration
└── Application Patterns
```

---

## Proper Indexing

### Identify Missing Indexes
```javascript
// Find slow queries
db.setProfilingLevel(1, { slowms: 100 })
db.system.profile.find({ millis: { $gt: 100 } }).sort({ ts: -1 })

// Check for COLLSCAN in explain
db.orders.find({ customerId: "C1" }).explain("executionStats")
// If stage is COLLSCAN → missing index

// Check index usage statistics
db.orders.aggregate([{ $indexStats: {} }])
// Indexes with 0 accesses → candidates for removal
```

### Index Strategy
```
1. Index fields used in $match / find filters
2. Index fields used in $sort
3. Follow ESR rule for compound indexes
4. Remove unused indexes (reduce write overhead)
5. Prefer compound indexes over multiple single-field indexes
6. Keep total indexes per collection < 10 (ideally 5-7)
```

---

## Query Optimization

### Use Projection — Return Only Needed Fields
```javascript
// ❌ BAD: Returns entire document (including large arrays, nested objects)
db.users.find({ status: "active" })

// ✅ GOOD: Only return needed fields
db.users.find({ status: "active" }, { name: 1, email: 1, _id: 0 })

// Reduces:
// - Network transfer
// - Memory usage on client
// - Deserialization time
```

### Avoid Returning Unnecessary Fields
```java
// Spring Data — projection
@Query(value = "{ 'customerId': ?0 }", fields = "{ 'orderNumber': 1, 'total': 1, 'status': 1 }")
List<OrderSummary> findOrderSummaries(String customerId);

// MongoTemplate — projection
Query query = new Query(Criteria.where("customerId").is(customerId));
query.fields().include("orderNumber", "total", "status");
```

### Use Covered Queries When Possible
```javascript
// Index: { customerId: 1, status: 1, total: 1 }
// Query returns ONLY indexed fields → no document fetch needed
db.orders.find(
  { customerId: "C1", status: "active" },
  { customerId: 1, status: 1, total: 1, _id: 0 }
)
// Explain shows: no FETCH stage → fastest possible
```

---

## Pagination

### Offset Pagination (Simple but Slow at Scale)
```javascript
// Page 1
db.products.find({}).sort({ _id: 1 }).skip(0).limit(20)

// Page 100
db.products.find({}).sort({ _id: 1 }).skip(1980).limit(20)
// ⚠️ MongoDB must scan and discard 1980 documents!
// Gets slower as page number increases
```

### Cursor-Based Pagination (Efficient at Scale) ✅
```javascript
// First page
db.products.find({ category: "electronics" })
  .sort({ _id: 1 })
  .limit(20)

// Next page (use last _id from previous page)
db.products.find({ 
  category: "electronics",
  _id: { $gt: ObjectId("lastIdFromPreviousPage") }
})
  .sort({ _id: 1 })
  .limit(20)
// Always fast — uses index, no skipping
```

### Cursor Pagination in Spring Boot
```java
public List<Product> getNextPage(String category, String lastId, int pageSize) {
    Query query = new Query();
    query.addCriteria(Criteria.where("category").is(category));
    
    if (lastId != null) {
        query.addCriteria(Criteria.where("_id").gt(new ObjectId(lastId)));
    }
    
    query.with(Sort.by(Sort.Direction.ASC, "_id"));
    query.limit(pageSize);
    
    return mongoTemplate.find(query, Product.class);
}
```

---

## Connection Pooling

### Why Connection Pooling Matters
```
Without pooling:
  Request → Create connection → Execute → Close connection
  Request → Create connection → Execute → Close connection
  (Connection creation: ~5-30ms overhead each time)

With pooling:
  Request → Get connection from pool → Execute → Return to pool
  Request → Get connection from pool → Execute → Return to pool
  (Near-zero overhead for reuse)
```

### Spring Boot Configuration
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb?maxPoolSize=100&minPoolSize=10&maxIdleTimeMS=60000&waitQueueTimeoutMS=5000
```

### Pool Settings
| Setting | Default | Recommendation |
|---------|---------|----------------|
| `maxPoolSize` | 100 | Match your expected concurrent connections |
| `minPoolSize` | 0 | 10-20 for always-ready connections |
| `maxIdleTimeMS` | 0 (infinite) | 60000 (1 min) to release idle connections |
| `waitQueueTimeoutMS` | 120000 | 5000 (fail fast if pool exhausted) |
| `connectTimeoutMS` | 10000 | 5000 for fast failure detection |
| `socketTimeoutMS` | 0 (infinite) | 30000 for stuck query protection |

---

## Read/Write Concerns for Performance

```
Performance vs Durability:

FASTEST ←─────────────────────────────→ SAFEST
w:0        w:1        w:majority      w:majority + j:true

Read concern:
FASTEST ←─────────────────────────────→ MOST CONSISTENT
local     available    majority        linearizable
```

### Guidelines
- **High-frequency writes** (logs, metrics): `w: 1` or even `w: 0`
- **Critical business data** (payments, orders): `w: "majority"`
- **Analytics reads**: `readPreference: secondaryPreferred`, `readConcern: local`
- **User-facing reads requiring latest data**: `readPreference: primary`

---

## Working Set and Memory

### What is the Working Set?
The working set is the portion of data and indexes that is actively accessed. Ideally, the working set fits in RAM.

```
RAM
┌─────────────────────────────────┐
│ WiredTiger Cache (50% of RAM)    │
│ ┌─────────────────────────────┐ │
│ │ Indexes (should fit here)   │ │
│ │ Frequently accessed data    │ │
│ │ = Working Set               │ │
│ └─────────────────────────────┘ │
│                                  │
│ OS File System Cache             │
└─────────────────────────────────┘

If working set > RAM → disk reads → SLOW
```

### Monitoring
```javascript
// Cache usage
db.serverStatus().wiredTiger.cache
// Key metrics:
// "bytes currently in the cache"
// "maximum bytes configured"
// "pages read into cache"
// "pages written from cache"

// If cache is full and evicting pages → working set exceeds RAM
```

### Solutions for Working Set Issues
1. Add more RAM
2. Add shards (distribute data)
3. Archive old data
4. Reduce document size (remove unnecessary fields)
5. Optimize indexes (smaller indexes = more data in cache)

---

## Slow Queries and Profiling

### MongoDB Profiler
```javascript
// Enable profiling for slow queries (> 100ms)
db.setProfilingLevel(1, { slowms: 100 })

// Profile ALL operations (development only!)
db.setProfilingLevel(2)

// Disable profiling
db.setProfilingLevel(0)

// Query the profiler
db.system.profile.find({
  millis: { $gt: 200 },
  ns: "mydb.orders"
}).sort({ ts: -1 }).limit(10)
```

### Profiler Output Analysis
```javascript
{
  "op": "query",
  "ns": "mydb.orders",
  "millis": 450,
  "planSummary": "COLLSCAN",        // ← Problem!
  "keysExamined": 0,                // ← No index used
  "docsExamined": 1000000,          // ← Full scan
  "nreturned": 3,                   // ← Only needed 3 docs
  "command": {
    "find": "orders",
    "filter": { "customerId": "C1" }
  }
}
// Fix: createIndex({ customerId: 1 })
```

---

## Anti-Patterns to Avoid

### 1. Unbounded Arrays
```javascript
// ❌ BAD: Array grows forever
{ userId: "U1", messages: [/* millions of messages */] }

// ✅ GOOD: Separate collection
// messages: { userId: "U1", text: "Hello", sentAt: ... }
```

### 2. Large Documents with Partial Reads
```javascript
// ❌ BAD: 10MB document, usually only need name and email
{
  name: "Vinay",
  email: "vinay@example.com",
  profileImage: "<base64 10MB image>",
  activityLog: [/* 50,000 entries */]
}

// ✅ GOOD: Separate concerns
// users: { name, email, imageUrl }
// user_activity: { userId, action, timestamp }
// images stored in GridFS or S3
```

### 3. No Indexes on Frequently Queried Fields
```javascript
// ❌ BAD: 10M documents, no index on customerId
db.orders.find({ customerId: "C1" }) // COLLSCAN every time

// ✅ GOOD: Index on query fields
db.orders.createIndex({ customerId: 1, orderDate: -1 })
```

### 4. Using $where or JavaScript in Queries
```javascript
// ❌ BAD: JavaScript execution, cannot use indexes
db.orders.find({ $where: "this.total > this.budget" })

// ✅ GOOD: Use $expr
db.orders.find({ $expr: { $gt: ["$total", "$budget"] } })
```

### 5. Excessive Indexing
```javascript
// ❌ BAD: 20 indexes on a write-heavy collection
// Every insert updates ALL 20 indexes

// ✅ GOOD: 5-7 well-designed compound indexes
// Use explain() to verify each is actually used
```

---

## Performance Monitoring Commands

```javascript
// Server status
db.serverStatus()

// Current operations
db.currentOp()
db.currentOp({ "secs_running": { $gt: 5 } })  // Operations running > 5 seconds

// Kill long-running operation
db.killOp(opId)

// Collection stats
db.orders.stats()

// Index sizes
db.orders.totalIndexSize()

// Database stats
db.stats()
```

---

## Performance Checklist

```
□ All frequent queries have supporting indexes (verify with explain())
□ No COLLSCAN in production queries
□ Compound indexes follow ESR rule
□ No in-memory SORT stages for large result sets
□ Using projection to limit returned fields
□ Cursor-based pagination for large collections
□ Connection pool properly sized
□ Working set fits in RAM
□ No unbounded arrays in documents
□ Unused indexes removed
□ Profiler enabled for slow query detection
□ Write concern appropriate for each operation type
□ Bulk operations used for batch processing
```

---

## Interview Questions

**Q: You have a collection with 100M documents and queries are slow. Walk me through your approach.**
A: 
1. Enable profiler to identify slow queries
2. Run explain() on slow queries to check execution plan
3. Check for COLLSCAN → add missing indexes
4. Check for SORT stage → redesign index with ESR rule
5. Check docsExamined vs nReturned ratio → improve index selectivity
6. Verify working set fits in RAM (check cache eviction rates)
7. Review schema — are documents too large? Unbounded arrays?
8. Consider sharding if single server can't handle the load

**Q: How do you handle pagination for millions of records?**
A: Use cursor-based pagination instead of skip/limit. Track the last document's sort field value (usually _id) and use it as a filter for the next page: `find({ _id: { $gt: lastId } }).limit(pageSize)`. This is O(log n) regardless of page number.

**Q: What's wrong with having too many indexes?**
A: Each index slows writes (every insert/update/delete must update all indexes), consumes storage, and uses RAM. In extreme cases, indexes might not fit in memory, causing performance degradation. Aim for 5-7 well-designed compound indexes instead of 15-20 single-field indexes.

**Q: How do you know if your working set exceeds available RAM?**
A: Monitor WiredTiger cache metrics. High "pages read into cache" rate, high cache eviction rate, or consistent disk I/O indicate the working set exceeds RAM. Also, if query latency increases during peak hours despite proper indexes, memory pressure is likely the cause.

**Q: When would you use w:0 write concern?**
A: For non-critical, high-volume data where occasional loss is acceptable: application logs, metrics, click tracking, session activity. The write is fire-and-forget — fastest possible, but no guarantee the write persisted.
