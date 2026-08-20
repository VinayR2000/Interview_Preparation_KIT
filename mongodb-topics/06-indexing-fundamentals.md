# Indexing Fundamentals ⭐⭐⭐

## Why Indexes Matter

Without an index, MongoDB must scan EVERY document in a collection to find matches (COLLSCAN). With an index, MongoDB can jump directly to matching documents (IXSCAN).

```
WITHOUT INDEX (COLLSCAN):
┌─────────────────────────────────────────────┐
│  Scan Doc 1 → Scan Doc 2 → ... → Scan Doc N │
│  O(N) - checks every document                │
└─────────────────────────────────────────────┘

WITH INDEX (IXSCAN):
┌─────────────────────────────────────────────┐
│  B-Tree lookup → Pointer → Document          │
│  O(log N) - binary search in index           │
└─────────────────────────────────────────────┘
```

---

## How Indexes Work

MongoDB uses **B-Tree** data structures for indexes (WiredTiger storage engine).

```
                    [50]
                   /    \
            [20, 35]    [65, 80]
           /   |   \   /   |   \
       [10] [25] [40] [55] [70] [90]
        ↓     ↓    ↓    ↓    ↓    ↓
      Doc   Doc  Doc  Doc  Doc  Doc
      Ptrs  Ptrs Ptrs Ptrs Ptrs Ptrs
```

- Each index entry contains the indexed field value + pointer to the document
- Index is ordered → supports range queries and sorting
- Index is separate from the data → maintained on every write operation

---

## Index Costs

### Benefits
- Dramatically faster reads (IXSCAN vs COLLSCAN)
- Supports efficient sorting without in-memory sorts
- Enables covered queries (data served from index alone)

### Costs
- **Write overhead**: Every insert/update/delete must also update all relevant indexes
- **Storage**: Indexes consume disk space and RAM
- **Memory**: Working set includes index data (must fit in RAM for best performance)

### Rule of Thumb
- More indexes = faster reads, slower writes
- For read-heavy workloads: more indexes are beneficial
- For write-heavy workloads: minimize indexes to essential queries

---

## Collection Scan vs Index Scan

### COLLSCAN (Collection Scan)
```
Query: db.users.find({ email: "vinay@example.com" })
No index on "email" field

Plan: COLLSCAN
Documents Examined: 1,000,000  (entire collection)
Documents Returned: 1
Execution Time: 850ms
```

### IXSCAN (Index Scan)
```
Query: db.users.find({ email: "vinay@example.com" })
Index exists: { email: 1 }

Plan: IXSCAN → FETCH
Keys Examined: 1
Documents Examined: 1
Documents Returned: 1
Execution Time: 1ms
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
    name: "idx_users_email",
    background: true  // Deprecated in 4.2+, builds are non-blocking by default
  }
)
```

### getIndexes()
```javascript
db.users.getIndexes()
// Returns:
[
  { v: 2, key: { _id: 1 }, name: "_id_" },
  { v: 2, key: { email: 1 }, name: "idx_users_email", unique: true }
]
```

### dropIndex()
```javascript
// By name
db.users.dropIndex("idx_users_email")

// By specification
db.users.dropIndex({ email: 1 })

// Drop all indexes (except _id)
db.users.dropIndexes()
```

### hideIndex() (MongoDB 4.4+)
```javascript
// Hide index without dropping (test impact before removing)
db.users.hideIndex("idx_users_email")

// Unhide
db.users.unhideIndex("idx_users_email")
```

**Best Practice**: Hide an index first, monitor performance, then drop if no regression.

---

## The _id Index

- Automatically created on every collection
- Cannot be dropped
- Unique index on `_id` field
- Supports equality queries and `findById` operations
- Uses ObjectId by default (sortable by creation time)

---

## Index Properties

### Unique Index
```javascript
db.users.createIndex({ email: 1 }, { unique: true })

// Rejects duplicate values
db.users.insertOne({ email: "vinay@example.com" })  // OK
db.users.insertOne({ email: "vinay@example.com" })  // ERROR: duplicate key

// Null handling: only ONE document can have a missing field (null is a value)
// Use sparse or partial to avoid this
```

### Sparse Index
```javascript
db.users.createIndex({ phone: 1 }, { sparse: true })
// Only indexes documents that HAVE the phone field
// Documents without phone are not in the index

// ⚠️ Sparse indexes won't be used for queries that need to return
// documents without the indexed field
```

### TTL Index (Time-To-Live)
```javascript
// Automatically delete documents after 30 days
db.sessions.createIndex({ createdAt: 1 }, { expireAfterSeconds: 2592000 })

// Documents are removed by a background thread (runs every 60 seconds)
// Only works on single-field indexes containing Date values
```

### Partial Index
```javascript
// Only index active users (saves space)
db.users.createIndex(
  { email: 1 },
  { partialFilterExpression: { status: "active" } }
)

// Smaller index, faster to maintain
// Only used when query matches the partial filter expression

// Common pattern: index only non-null values
db.users.createIndex(
  { phone: 1 },
  { partialFilterExpression: { phone: { $exists: true } } }
)
```

---

## Index Selection by MongoDB

When a query is executed:
1. MongoDB identifies candidate indexes
2. The query planner evaluates plans
3. The **winning plan** is cached
4. Subsequent identical queries use the cached plan

```
Query Arrives
     ↓
Query Planner
     ↓
┌─────────────────────────┐
│ Candidate Plan 1: IXSCAN (idx_email)
│ Candidate Plan 2: IXSCAN (idx_email_status)
│ Candidate Plan 3: COLLSCAN
└─────────────────────────┘
     ↓
Race Plans (first to return batch wins)
     ↓
Winning Plan → Cache
```

---

## Index Usage Guidelines

### When to Create Indexes
- Fields frequently used in query filters ($match, find conditions)
- Fields used in sort operations
- Fields used in $lookup localField/foreignField
- Fields with high cardinality (many distinct values)

### When NOT to Create Indexes
- Small collections (< 1000 documents — COLLSCAN is fine)
- Fields with low cardinality (e.g., boolean, status with 3 values)
- Collections with extremely high write throughput where reads are infrequent
- Fields rarely used in queries

### Index Limitations
- Maximum 64 indexes per collection
- Index key size limit: 1024 bytes
- Compound index: maximum 32 fields
- Index name maximum: 127 bytes

---

## Covered Queries

A query is "covered" when ALL data is served from the index without fetching documents.

```javascript
// Index: { email: 1, name: 1 }

// COVERED query (only needs fields in the index)
db.users.find(
  { email: "vinay@example.com" },
  { email: 1, name: 1, _id: 0 }  // _id must be excluded!
)
// Stage: IXSCAN (no FETCH stage!)
// Documents Examined: 0

// NOT covered (needs to fetch document for 'age')
db.users.find(
  { email: "vinay@example.com" },
  { email: 1, name: 1, age: 1, _id: 0 }
)
// Stage: IXSCAN → FETCH
```

**Key Point**: Exclude `_id` from projection (unless it's in the index) for covered queries.

---

## Interview Questions

**Q: What happens if you don't create any indexes?**
A: MongoDB only has the default `_id` index. All queries on other fields result in COLLSCAN — scanning every document. This is O(N) and becomes increasingly slow as the collection grows.

**Q: How do indexes affect write performance?**
A: Every insert must update all indexes on that collection. Every update that modifies indexed fields must update those indexes. More indexes = slower writes. This is why you should only create indexes that your queries actually use.

**Q: What is a covered query?**
A: A query where all requested fields exist in the index itself. MongoDB doesn't need to fetch the actual document from disk — all data comes from the index. This is the fastest possible query.

**Q: When would you use a partial index?**
A: When you only need to index a subset of documents. Examples: only active users, only non-null values, only recent orders. This saves storage and makes the index faster to maintain.

**Q: What is the difference between sparse and partial indexes?**
A: Sparse indexes exclude documents where the indexed field doesn't exist. Partial indexes exclude documents based on any filter expression — they're more flexible. Partial indexes (MongoDB 3.2+) are generally preferred over sparse.

**Q: Can you have too many indexes?**
A: Yes. Each index consumes RAM and slows down writes. Unused indexes waste resources. Regularly audit indexes with `$indexStats` and drop unused ones.

**Q: How does MongoDB choose which index to use?**
A: The query planner identifies candidate indexes, runs them in a race (trial execution), and the fastest plan wins. The winning plan is cached for identical query shapes.
