# Index Fundamentals ⭐⭐⭐

## What is an Index?

An index is a data structure (B-tree in MongoDB) that stores a small portion of the collection's data in an easy-to-traverse form. Without indexes, MongoDB must scan every document in a collection (COLLSCAN) to find matching documents.

```
Without Index (COLLSCAN):
┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐
│ 1 │→│ 2 │→│ 3 │→│ 4 │→│ 5 │→│ 6 │→│ 7 │→│ 8 │  Scan ALL documents
└───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘

With Index (IXSCAN):
         ┌───┐
         │ 4 │
       ┌─┴───┴─┐
    ┌──┤       ├──┐
    │  └───────┘  │
  ┌─┴─┐        ┌─┴─┐
  │ 2 │        │ 6 │        B-tree → O(log n) lookup
  ├───┤        ├───┤
┌─┤   ├─┐  ┌─┤   ├─┐
│ └───┘ │  │ └───┘ │
│1│   │3│  │5│   │7│
```

---

## Why Indexes Improve Reads

| Without Index | With Index |
|---------------|------------|
| Scan all documents | Navigate B-tree |
| O(n) complexity | O(log n) complexity |
| Reads entire collection from disk | Reads only relevant index pages |
| 1M documents = 1M comparisons | 1M documents = ~20 comparisons |

### Example Impact
```
Collection: 10 million orders
Query: db.orders.find({ customerId: "C123" })

Without index: Scans 10,000,000 documents → ~5 seconds
With index on customerId: Examines ~3 documents → ~2 ms
```

---

## Index Cost (Trade-offs)

Indexes are NOT free. Every index has costs:

| Benefit | Cost |
|---------|------|
| Faster reads | Slower writes (index must be updated) |
| Faster sorts | Additional storage space |
| Covered queries | Memory usage (indexes in RAM) |

### Write Overhead
```
Insert 1 document into collection with 5 indexes:
  1. Write document to collection
  2. Update index 1 (B-tree insert)
  3. Update index 2 (B-tree insert)
  4. Update index 3 (B-tree insert)
  5. Update index 4 (B-tree insert)
  6. Update index 5 (B-tree insert)
  
→ 6 write operations instead of 1
```

### When NOT to Index
- Fields rarely queried
- Collections that are very small (< 1000 documents)
- Write-heavy collections where read performance isn't critical
- Fields with very low cardinality (e.g., boolean field with only true/false)

---

## Collection Scan vs Index Scan

### COLLSCAN (Collection Scan)
- MongoDB reads EVERY document in the collection
- Checks each document against the query filter
- Only option when no suitable index exists
- Acceptable ONLY for very small collections

### IXSCAN (Index Scan)
- MongoDB traverses the index B-tree
- Finds matching index entries
- Fetches only the matching documents from collection
- Dramatically faster for selective queries

### COVERED QUERY (Best Case)
- All queried/projected fields exist in the index
- MongoDB never touches the actual documents
- Returns results directly from the index
- Fastest possible query execution

```javascript
// Create index
db.users.createIndex({ email: 1, name: 1 })

// Covered query (all fields in index, _id excluded from projection)
db.users.find({ email: "vinay@example.com" }, { email: 1, name: 1, _id: 0 })
// → Returns from index only, never reads documents
```

---

## Creating and Managing Indexes

### createIndex()
```javascript
// Single field index (ascending)
db.users.createIndex({ email: 1 })

// Single field index (descending)
db.users.createIndex({ createdAt: -1 })

// With options
db.users.createIndex(
  { email: 1 },
  { 
    unique: true,
    name: "idx_users_email_unique",
    background: true  // Deprecated in 4.2+, all builds are now optimized
  }
)

// Compound index
db.orders.createIndex({ customerId: 1, orderDate: -1 })
```

### getIndexes()
```javascript
db.users.getIndexes()
// Returns:
[
  { v: 2, key: { _id: 1 }, name: '_id_' },
  { v: 2, key: { email: 1 }, name: 'idx_users_email_unique', unique: true }
]
```

### dropIndex()
```javascript
// Drop by name
db.users.dropIndex("idx_users_email_unique")

// Drop by specification
db.users.dropIndex({ email: 1 })

// Drop all indexes (except _id)
db.users.dropIndexes()
```

### Index Build Behavior (MongoDB 4.2+)
- Index builds are **optimized** by default
- They hold an exclusive lock only at the beginning and end
- During build, the collection remains available for reads/writes
- For production systems, schedule index builds during low-traffic periods

---

## The _id Index

- Automatically created on every collection
- Cannot be dropped
- Unique index on the `_id` field
- Supports all queries on `_id`
- Used for the primary key lookup

---

## Index Selectivity

The effectiveness of an index depends on **selectivity** — how well it narrows down results.

```
High Selectivity (Good):
  email index → typically 1 document per value
  userId index → typically 1 document per value
  
Low Selectivity (Poor):
  gender index → only 2-3 possible values
  status index → only "active"/"inactive"
  boolean field → only true/false
```

**Rule**: Index fields with HIGH cardinality (many distinct values).

---

## Index Size and Memory

```javascript
// Check index sizes
db.users.stats().indexSizes
// { "_id_": 245760, "email_1": 188416, "createdAt_-1": 204800 }

// Total index size
db.users.totalIndexSize()
// 639 KB
```

### Working Set
- Indexes should ideally fit in RAM
- If indexes exceed available RAM → disk reads → slow performance
- Monitor with `db.serverStatus().wiredTiger.cache`

---

## Index Hints

Force MongoDB to use a specific index:

```javascript
// Use specific index
db.orders.find({ customerId: "C1", status: "active" })
  .hint({ customerId: 1, status: 1 })

// Force collection scan (testing)
db.orders.find({ customerId: "C1" }).hint({ $natural: 1 })
```

**Use Cases**: When the query planner picks a suboptimal index (rare), or for testing/benchmarking.

---

## Common Patterns

### Pattern: Ensuring Uniqueness
```javascript
db.users.createIndex({ email: 1 }, { unique: true })
// Insert with duplicate email → throws DuplicateKeyError (E11000)
```

### Pattern: TTL for Expiration
```javascript
// Auto-delete documents after 30 days
db.sessions.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 })
```

### Pattern: Partial Index (Index Subset of Documents)
```javascript
// Only index active users (saves space)
db.users.createIndex(
  { email: 1 },
  { partialFilterExpression: { status: "active" } }
)
```

---

## Interview Questions

**Q: What happens if you query a field without an index?**
A: MongoDB performs a COLLSCAN — it reads every document in the collection and checks each against the query filter. This is O(n) and becomes unacceptable as the collection grows.

**Q: Can you have too many indexes?**
A: Yes. Each index slows down writes (insert/update/delete must update all indexes), consumes storage, and uses RAM. A good rule of thumb is to have indexes only for your actual query patterns. Remove unused indexes.

**Q: What's a covered query?**
A: A query where all queried and projected fields are in the index. MongoDB returns results directly from the index without accessing the actual documents. This is the fastest possible query type.

**Q: Does index order matter (ascending vs descending)?**
A: For single-field indexes, no (MongoDB can traverse in either direction). For compound indexes, direction matters for sort operations — the index must match or be the exact inverse of the requested sort.

**Q: How do you find unused indexes?**
A: Use `db.collection.aggregate([{$indexStats: {}}])` to see index usage statistics. Indexes with zero `accesses.ops` are candidates for removal.

**Q: What is the default index?**
A: Every collection has a `_id` index created automatically. It's unique and cannot be dropped.

**Q: How many indexes can a collection have?**
A: MongoDB allows up to 64 indexes per collection. But in practice, 5-10 well-designed indexes should cover most access patterns.
