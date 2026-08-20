# MongoDB Fundamentals

## What is MongoDB?

MongoDB is an open-source, document-oriented NoSQL database designed for high performance, high availability, and automatic scaling. Instead of storing data in rows and columns (like relational databases), MongoDB stores data as flexible JSON-like documents.

---

## NoSQL vs SQL

| Aspect | SQL (Relational) | NoSQL (MongoDB) |
|--------|------------------|-----------------|
| Data Model | Tables, rows, columns | Collections, documents |
| Schema | Fixed schema (DDL required) | Flexible schema (schema-less) |
| Relationships | JOINs across tables | Embedding or referencing |
| Scaling | Vertical (scale up) | Horizontal (scale out / sharding) |
| Transactions | Strong ACID by default | ACID per document; multi-doc transactions available |
| Query Language | SQL | MongoDB Query Language (MQL) |
| Best For | Complex relationships, strict consistency | Flexible data, high throughput, rapid iteration |

### When to Choose MongoDB Over SQL
- Data structure varies or evolves frequently
- High write throughput needed
- Hierarchical/nested data (e.g., product catalogs, user profiles)
- Horizontal scaling is a priority
- Access patterns are well-defined and document-centric

### When SQL is Better
- Complex multi-table JOINs are frequent
- Strict referential integrity is critical
- ACID transactions across many entities
- Reporting/analytics with complex aggregations across normalized data

---

## Core Terminology

### Database
- A container for collections
- Equivalent to a database in SQL
- Created implicitly when you first store data

### Collection
- A group of documents
- Equivalent to a table in SQL
- No enforced schema (documents in the same collection can have different fields)
- Created implicitly on first insert

### Document
- A single record in a collection
- Equivalent to a row in SQL
- Stored as BSON (Binary JSON)
- Maximum size: **16 MB**

### Field
- A key-value pair within a document
- Equivalent to a column in SQL
- Can hold any BSON data type including nested documents and arrays

### _id Field
- Every document MUST have an `_id` field
- Acts as the primary key
- If not provided, MongoDB auto-generates an `ObjectId`
- `ObjectId` is a 12-byte value: 4-byte timestamp + 5-byte random + 3-byte counter
- Guarantees uniqueness across the cluster

---

## BSON (Binary JSON)

MongoDB stores documents in BSON format internally.

### JSON vs BSON

| Aspect | JSON | BSON |
|--------|------|------|
| Format | Text-based | Binary-encoded |
| Data Types | String, Number, Boolean, Array, Object, null | All JSON types + Date, ObjectId, Int32, Int64, Decimal128, Binary, etc. |
| Size | Larger (text) | Compact binary |
| Parsing | Slower (text parsing) | Faster (binary traversal) |
| Usage | Data interchange | Internal storage format |

### Important BSON Types
```
String        → UTF-8 string
Int32         → 32-bit integer
Int64         → 64-bit integer
Double        → 64-bit floating point
Decimal128    → High-precision decimal
Boolean       → true/false
Date          → Milliseconds since epoch
ObjectId      → 12-byte unique identifier
Array         → Ordered list of values
Object        → Embedded document
Binary        → Binary data
Null          → Null value
```

---

## MongoDB Architecture

```
Client Application
       ↓
   MongoDB Driver (Java, Node.js, Python, etc.)
       ↓
   mongos (if sharded) or direct to mongod
       ↓
┌─────────────────────────────────┐
│          mongod Process          │
│  ┌───────────────────────────┐  │
│  │     Storage Engine        │  │
│  │     (WiredTiger)          │  │
│  │  ┌─────────────────────┐ │  │
│  │  │  In-Memory Cache     │ │  │
│  │  │  (Working Set)       │ │  │
│  │  └─────────────────────┘ │  │
│  │  ┌─────────────────────┐ │  │
│  │  │  Journal (WAL)       │ │  │
│  │  └─────────────────────┘ │  │
│  │  ┌─────────────────────┐ │  │
│  │  │  Data Files          │ │  │
│  │  └─────────────────────┘ │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

### Key Components
- **mongod**: The primary daemon process that handles data requests, manages data access, and performs background management operations
- **mongos**: The routing service for sharded clusters
- **WiredTiger**: Default storage engine since MongoDB 3.2
  - Document-level concurrency control
  - Compression (snappy, zlib, zstd)
  - Journal for crash recovery
  - In-memory cache (default: 50% of RAM - 1GB)

---

## MongoDB Compass

MongoDB Compass is the official GUI for MongoDB.

### Key Features
- Visual exploration of data
- Query builder (no syntax knowledge needed)
- Schema analysis and visualization
- Index management
- Aggregation pipeline builder (drag and drop stages)
- Performance monitoring
- Real-time server stats

### When to Use
- Exploring data structure in development
- Building complex aggregation pipelines visually
- Analyzing schema patterns
- Quick ad-hoc queries without writing MQL

---

## MongoDB Shell (mongosh)

The modern MongoDB shell (replaced the legacy `mongo` shell).

### Basic Commands
```javascript
// Show databases
show dbs

// Switch to / create database
use myDatabase

// Show collections
show collections

// Get current database
db

// Get server status
db.serverStatus()

// Get collection stats
db.myCollection.stats()

// Drop database
db.dropDatabase()

// Drop collection
db.myCollection.drop()
```

### Connection String
```
mongodb://username:password@host:port/database?options

// Replica set
mongodb://host1:27017,host2:27017,host3:27017/mydb?replicaSet=myRS

// MongoDB Atlas
mongodb+srv://username:password@cluster.mongodb.net/mydb
```

---

## Document Structure Example

```json
{
  "_id": ObjectId("64a7f2e8b1c2d3e4f5a6b7c8"),
  "name": "Vinay Kumar",
  "email": "vinay@example.com",
  "age": 28,
  "isActive": true,
  "skills": ["Java", "Spring Boot", "MongoDB", "Kafka"],
  "address": {
    "street": "123 Main St",
    "city": "Bangalore",
    "state": "Karnataka",
    "pincode": "560001"
  },
  "experience": [
    {
      "company": "TechCorp",
      "role": "Senior Developer",
      "years": 3
    },
    {
      "company": "StartupXYZ",
      "role": "Backend Engineer",
      "years": 2
    }
  ],
  "createdAt": ISODate("2024-01-15T10:30:00Z"),
  "updatedAt": ISODate("2024-06-20T14:45:00Z")
}
```

---

## Interview Questions

**Q: What is the maximum document size in MongoDB?**
A: 16 MB. If you need to store larger data, use GridFS.

**Q: What happens if you don't provide an _id field?**
A: MongoDB automatically generates an ObjectId for the _id field.

**Q: Why does MongoDB use BSON instead of JSON?**
A: BSON is more efficient for storage and traversal, supports additional data types (Date, ObjectId, Binary, etc.), and allows for fast field-level access without parsing the entire document.

**Q: Can two documents in the same collection have different fields?**
A: Yes. MongoDB is schema-flexible. However, in practice, documents in a collection usually share a similar structure.

**Q: What is the default storage engine?**
A: WiredTiger (since MongoDB 3.2). It provides document-level concurrency, compression, and journaling.

**Q: How is ObjectId generated?**
A: 12 bytes: 4-byte Unix timestamp + 5-byte random value (per process) + 3-byte incrementing counter. This makes ObjectIds roughly sortable by creation time.
