# Index Types

## Overview of All Index Types

```
MongoDB Index Types
├── Single-Field
├── Compound
├── Multikey (arrays)
├── Unique
├── Text
├── TTL (Time-To-Live)
├── Partial
├── Sparse
├── Hashed
├── Geospatial (2d, 2dsphere)
└── Wildcard
```

---

## Single-Field Index

The simplest index type — on one field.

```javascript
// Ascending
db.users.createIndex({ email: 1 })

// Descending
db.users.createIndex({ createdAt: -1 })

// Supports queries on that field
db.users.find({ email: "vinay@example.com" })     // ✅ Uses index
db.users.find({ email: { $regex: /^vinay/ } })    // ✅ Uses index (anchored regex)
db.users.find().sort({ createdAt: -1 })            // ✅ Uses index for sort
db.users.find().sort({ createdAt: 1 })             // ✅ Also works (reverse traversal)
```

**Note**: For single-field indexes, direction (1 or -1) doesn't matter — MongoDB can traverse in both directions.

---

## Compound Index

Index on multiple fields. Order matters significantly.

```javascript
db.orders.createIndex({ customerId: 1, orderDate: -1, status: 1 })
```

### Index Prefix Rule
A compound index supports queries on any **prefix** of the index fields:

```javascript
// Index: { customerId: 1, orderDate: -1, status: 1 }

db.orders.find({ customerId: "C1" })                          // ✅ Uses prefix {customerId}
db.orders.find({ customerId: "C1", orderDate: { $gt: date } }) // ✅ Uses prefix {customerId, orderDate}
db.orders.find({ customerId: "C1", orderDate: d, status: "shipped" }) // ✅ Uses full index

db.orders.find({ orderDate: { $gt: date } })                   // ❌ Cannot use index (skipped prefix)
db.orders.find({ status: "shipped" })                          // ❌ Cannot use index (skipped prefix)
db.orders.find({ customerId: "C1", status: "shipped" })        // ⚠️ Uses prefix {customerId} only
```

### Sort Support
```javascript
// Index: { customerId: 1, orderDate: -1 }

db.orders.find({ customerId: "C1" }).sort({ orderDate: -1 })  // ✅ Sort matches index
db.orders.find({ customerId: "C1" }).sort({ orderDate: 1 })   // ✅ Reverse traversal OK
db.orders.find({}).sort({ customerId: 1, orderDate: -1 })      // ✅ Matches index
db.orders.find({}).sort({ customerId: -1, orderDate: 1 })      // ✅ Exact inverse OK
db.orders.find({}).sort({ customerId: 1, orderDate: 1 })       // ❌ Mixed directions don't match
```

---

## Multikey Index

Automatically created when you index a field that contains an array.

```javascript
// Document
{ name: "Vinay", skills: ["Java", "Spring", "MongoDB"] }

// Creating index on array field
db.users.createIndex({ skills: 1 })
// MongoDB creates separate index entries for EACH array element

// Queries that use this index:
db.users.find({ skills: "MongoDB" })                    // ✅
db.users.find({ skills: { $in: ["Java", "Python"] } })  // ✅
db.users.find({ skills: { $all: ["Java", "Spring"] } }) // ✅
```

### Limitations
```javascript
// ❌ Cannot create compound multikey index on TWO array fields
{ tags: ["a", "b"], scores: [1, 2, 3] }
db.collection.createIndex({ tags: 1, scores: 1 })  // ERROR if both are arrays

// ✅ OK if only ONE field is an array
{ name: "Vinay", skills: ["Java", "Spring"] }
db.users.createIndex({ name: 1, skills: 1 })  // OK — name is scalar, skills is array
```

---

## Unique Index

Ensures no two documents have the same value for the indexed field.

```javascript
db.users.createIndex({ email: 1 }, { unique: true })

// Inserting duplicate → DuplicateKeyError (E11000)
db.users.insertOne({ email: "vinay@example.com" })  // OK
db.users.insertOne({ email: "vinay@example.com" })  // ERROR: E11000

// Compound unique index
db.subscriptions.createIndex(
  { userId: 1, planType: 1 },
  { unique: true }
)
// Ensures each user can only have one subscription per plan type
```

### Unique + Null
```javascript
// ⚠️ null is treated as a value — only ONE document can have the field missing/null
db.users.createIndex({ phone: 1 }, { unique: true })
db.users.insertOne({ name: "Alice" })  // phone = null → OK
db.users.insertOne({ name: "Bob" })    // phone = null → ERROR (duplicate null)

// Solution: Partial + Unique
db.users.createIndex(
  { phone: 1 },
  { unique: true, partialFilterExpression: { phone: { $exists: true } } }
)
// Now multiple documents can lack the phone field
```

---

## Text Index

Supports full-text search on string fields.

```javascript
// Create text index
db.articles.createIndex({ title: "text", content: "text" })

// Only ONE text index per collection allowed!

// Query
db.articles.find({ $text: { $search: "mongodb spring boot" } })

// Exact phrase
db.articles.find({ $text: { $search: "\"spring boot\"" } })

// Exclude word
db.articles.find({ $text: { $search: "mongodb -postgresql" } })

// With text score (relevance)
db.articles.find(
  { $text: { $search: "mongodb tutorial" } },
  { score: { $meta: "textScore" } }
).sort({ score: { $meta: "textScore" } })
```

### Text Index Options
```javascript
// Weighted fields (title more important than content)
db.articles.createIndex(
  { title: "text", content: "text", tags: "text" },
  { weights: { title: 10, content: 5, tags: 2 } }
)

// Specific language
db.articles.createIndex(
  { content: "text" },
  { default_language: "english" }
)
```

---

## TTL Index (Time-To-Live)

Automatically deletes documents after a specified time period.

```javascript
// Delete documents 30 days after createdAt
db.sessions.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 })

// Delete at specific time (set expireAt field in document)
db.events.createIndex({ expireAt: 1 }, { expireAfterSeconds: 0 })
// Document: { data: "...", expireAt: ISODate("2024-12-31T23:59:59Z") }
```

### TTL Rules
- Field MUST be a Date type (or array of Dates)
- Cannot be a compound index
- Background thread runs every 60 seconds to check for expired documents
- Documents may persist slightly past expiration (up to 60 seconds)
- Cannot be used on `_id` field or capped collections

### Use Cases
- Session data
- Temporary tokens
- Cache entries
- Audit logs with retention policy
- OTP/verification codes

---

## Partial Index

Only indexes documents matching a filter expression. Saves space and improves performance.

```javascript
// Only index orders with status "active"
db.orders.createIndex(
  { customerId: 1, orderDate: -1 },
  { partialFilterExpression: { status: "active" } }
)

// ✅ Uses partial index
db.orders.find({ customerId: "C1", status: "active" })

// ❌ Cannot use partial index (query doesn't guarantee matching the filter)
db.orders.find({ customerId: "C1" })
db.orders.find({ customerId: "C1", status: "shipped" })
```

### When to Use
- Large collections where only a subset is frequently queried
- Fields where most values are the same (e.g., 95% active, 5% inactive)
- Reducing index size for better memory utilization

---

## Sparse Index

Only indexes documents where the indexed field EXISTS (not null).

```javascript
db.users.createIndex({ phone: 1 }, { sparse: true })

// Documents WITHOUT phone field → NOT in the index
// Documents WITH phone field (including null) → IN the index
```

### Sparse vs Partial
```javascript
// Sparse: indexes all documents where field exists
db.users.createIndex({ phone: 1 }, { sparse: true })

// Partial: more flexible filtering
db.users.createIndex(
  { phone: 1 },
  { partialFilterExpression: { phone: { $exists: true, $ne: null } } }
)

// Partial indexes are generally preferred over sparse (more control)
```

### ⚠️ Sparse Index Gotcha
```javascript
db.users.createIndex({ phone: 1 }, { sparse: true })

// This sort might give incomplete results!
db.users.find().sort({ phone: 1 })
// Documents without 'phone' field are excluded from results
// because MongoDB uses the sparse index for the sort
```

---

## Hashed Index

Indexes the hash of the field value. Used primarily for **hash-based sharding**.

```javascript
db.users.createIndex({ userId: "hashed" })
```

### Characteristics
- Supports only equality queries (`$eq`, `$in`)
- Does NOT support range queries ($gt, $lt, $sort)
- Ensures even distribution for sharding
- Cannot be unique
- Cannot be compound (pre-MongoDB 4.4)

### Use Case
```javascript
// Sharding with hashed key for even distribution
sh.shardCollection("mydb.users", { userId: "hashed" })
```

---

## Geospatial Indexes

### 2dsphere (for Earth-like surfaces)
```javascript
// Document with GeoJSON point
{
  name: "Coffee Shop",
  location: {
    type: "Point",
    coordinates: [77.5946, 12.9716]  // [longitude, latitude]
  }
}

db.places.createIndex({ location: "2dsphere" })

// Find places near a point
db.places.find({
  location: {
    $near: {
      $geometry: { type: "Point", coordinates: [77.5946, 12.9716] },
      $maxDistance: 5000  // meters
    }
  }
})
```

---

## Wildcard Index

Indexes all fields or fields matching a pattern. Useful for dynamic schemas.

```javascript
// Index ALL fields in the document
db.collection.createIndex({ "$**": 1 })

// Index all fields under a specific path
db.collection.createIndex({ "metadata.$**": 1 })

// Exclude specific fields
db.collection.createIndex(
  { "$**": 1 },
  { wildcardProjection: { sensitiveField: 0 } }
)
```

### When to Use
- Dynamic/unpredictable schema
- Flexible metadata fields
- When you can't predict which fields will be queried

### Limitations
- Cannot support compound queries across multiple fields
- Higher storage overhead
- Not a replacement for well-designed compound indexes

---

## Index Type Comparison

| Type | Use Case | Supports Range | Supports Sort | Unique |
|------|----------|---------------|---------------|--------|
| Single-Field | Simple queries on one field | ✅ | ✅ | ✅ |
| Compound | Multi-field queries | ✅ | ✅ | ✅ |
| Multikey | Array fields | ✅ | ✅ | ✅* |
| Text | Full-text search | ❌ | By score | ❌ |
| TTL | Auto-expire documents | ❌ | ❌ | ❌ |
| Hashed | Even distribution/sharding | ❌ | ❌ | ❌ |
| Partial | Subset of collection | ✅ | ✅ | ✅ |
| Sparse | Only existing fields | ✅ | ✅ | ✅ |
| Wildcard | Dynamic schemas | ✅ | ✅ | ❌ |
| 2dsphere | Geospatial queries | N/A | By distance | ❌ |

---

## Interview Questions

**Q: When would you use a partial index over a regular index?**
A: When only a subset of documents is frequently queried. Example: a 100M document collection where 95% are "completed" orders but you only query "active" ones. A partial index on `{ status: "active" }` would be much smaller and fit better in memory.

**Q: Can you have multiple text indexes on a collection?**
A: No. MongoDB allows only ONE text index per collection. You can include multiple fields in that single text index with different weights.

**Q: What's the difference between sparse and partial indexes?**
A: Sparse indexes include all documents where the field exists. Partial indexes use a custom filter expression, giving you more control. Partial indexes are the modern, preferred approach.

**Q: Why can't you create a compound multikey index on two array fields?**
A: The cross-product of two arrays would create too many index entries. For two arrays of size N and M, you'd need N×M entries per document, which is expensive and generally not useful.

**Q: How does TTL deletion work? Is it immediate?**
A: No. A background thread checks for expired documents every 60 seconds. Documents may persist up to ~60 seconds past their expiration time. This is by design — don't rely on TTL for precise timing.

**Q: When would you use a hashed index?**
A: Primarily for shard key distribution. Hashed indexes ensure even data distribution across shards, preventing hot spots. They sacrifice range query support for uniform distribution.
