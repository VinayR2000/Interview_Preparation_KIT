# Advanced MongoDB Features

## Change Streams

Change Streams allow applications to react to real-time data changes in MongoDB without polling.

```
MongoDB Collection
       ↓ (write operation)
Change Stream (oplog tailing)
       ↓
Application Consumer
       ↓
React to changes (notify, sync, trigger)
```

### Basic Change Stream
```javascript
// Watch all changes on a collection
const changeStream = db.orders.watch();

changeStream.on("change", (event) => {
  console.log("Change detected:", event);
  // event.operationType: "insert", "update", "replace", "delete"
  // event.fullDocument: the changed document
  // event.updateDescription: what changed (for updates)
});
```

### Change Stream with Pipeline
```javascript
// Watch only specific changes
const pipeline = [
  { $match: { 
    operationType: { $in: ["insert", "update"] },
    "fullDocument.status": "shipped"
  }}
];

const changeStream = db.orders.watch(pipeline);
```

### Change Stream Events
```javascript
// Insert event
{
  operationType: "insert",
  fullDocument: { _id: ..., orderId: "ORD-001", status: "placed", total: 999 },
  ns: { db: "mydb", coll: "orders" },
  clusterTime: Timestamp(...)
}

// Update event
{
  operationType: "update",
  documentKey: { _id: ObjectId("...") },
  updateDescription: {
    updatedFields: { status: "shipped" },
    removedFields: []
  },
  fullDocument: { ... },  // Only if fullDocument: "updateLookup" specified
  ns: { db: "mydb", coll: "orders" }
}

// Delete event
{
  operationType: "delete",
  documentKey: { _id: ObjectId("...") },
  ns: { db: "mydb", coll: "orders" }
}
```

### Resume Tokens
```javascript
// Change streams can resume from where they left off
const changeStream = db.orders.watch([], { 
  resumeAfter: savedResumeToken,
  fullDocument: "updateLookup"  // Include full document for updates
});

// Save resume token for recovery
changeStream.on("change", (event) => {
  saveResumeToken(event._id);  // Persist this
  processChange(event);
});
```

### Change Streams + Spring Boot
```java
@Component
public class OrderChangeListener {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @PostConstruct
    public void watchOrders() {
        ChangeStreamOptions options = ChangeStreamOptions.builder()
            .filter(Aggregation.newAggregation(
                Aggregation.match(Criteria.where("operationType").in("insert", "update"))
            ))
            .build();
        
        Flux<ChangeStreamEvent<Order>> flux = mongoTemplate
            .changeStream("orders", options, Order.class);
        
        flux.subscribe(event -> {
            Order order = event.getBody();
            // Process change
            log.info("Order changed: {}", order.getOrderId());
        });
    }
}

// Using MessageListenerContainer
@Bean
MessageListenerContainer changeStreamContainer(MongoTemplate template) {
    MessageListenerContainer container = new DefaultMessageListenerContainer(template);
    container.start();
    
    ChangeStreamRequest<Order> request = ChangeStreamRequest.builder(message -> {
        Order order = message.getBody();
        // Handle change
    })
    .collection("orders")
    .filter(new Document("$match", new Document("operationType", "insert")))
    .build();
    
    container.register(request, Order.class);
    return container;
}
```

### Change Streams + Kafka Pattern
```
MongoDB
   ↓ (Change Stream)
Change Stream Consumer (application)
   ↓ (produce to Kafka)
Kafka Topic
   ↓
Multiple Downstream Consumers
   ├── Search Service (update Elasticsearch)
   ├── Notification Service (send alerts)
   ├── Analytics Service (update dashboards)
   └── Audit Service (log changes)
```

---

## GridFS

GridFS is MongoDB's specification for storing files larger than 16 MB.

### How It Works
```
Large File (e.g., 50 MB PDF)
       ↓
Split into chunks (default 255 KB each)
       ↓
┌─────────────────────────────────────┐
│ fs.files collection                  │
│ { filename, length, uploadDate,     │
│   metadata, md5, chunkSize }        │
└─────────────────────────────────────┘
┌─────────────────────────────────────┐
│ fs.chunks collection                 │
│ { files_id, n (sequence), data }    │
└─────────────────────────────────────┘
```

### When to Use
- Files > 16 MB (MongoDB document limit)
- When you want to access portions of a file without loading entirely
- When you want files replicated and backed up with your database
- Alternative to a separate file storage system

### When NOT to Use
- Small files (< 16 MB) → store directly in documents or use `BinData`
- When a dedicated object store (S3, GCS) is available → usually better choice
- High-performance streaming → S3/CDN is better

### Spring Boot GridFS
```java
@Autowired
private GridFsTemplate gridFsTemplate;

// Store file
ObjectId fileId = gridFsTemplate.store(
    inputStream,
    "report.pdf",
    "application/pdf",
    metadata
);

// Retrieve file
GridFSFile file = gridFsTemplate.findOne(
    Query.query(Criteria.where("_id").is(fileId))
);
GridFsResource resource = gridFsTemplate.getResource(file);
InputStream stream = resource.getInputStream();
```

---

## TTL Indexes (Revisited for Advanced Use)

### Dynamic Expiration
```javascript
// Each document specifies its own expiry time
db.tokens.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 })

// Insert with different TTLs
db.tokens.insertOne({ 
  token: "abc123", 
  type: "access",
  expiresAt: new Date(Date.now() + 3600000)  // 1 hour
})

db.tokens.insertOne({ 
  token: "def456", 
  type: "refresh",
  expiresAt: new Date(Date.now() + 2592000000)  // 30 days
})
```

### Modify TTL
```javascript
// Change TTL duration (requires collMod)
db.runCommand({
  collMod: "sessions",
  index: {
    keyPattern: { createdAt: 1 },
    expireAfterSeconds: 7200  // Change from 3600 to 7200
  }
})
```

---

## Time-Series Collections (MongoDB 5.0+)

Optimized for time-series data: IoT sensors, metrics, logs.

```javascript
db.createCollection("metrics", {
  timeseries: {
    timeField: "timestamp",         // Required: the time field
    metaField: "metadata",          // Optional: identifies the series
    granularity: "minutes"          // "seconds" | "minutes" | "hours"
  },
  expireAfterSeconds: 2592000       // Optional: auto-delete after 30 days
})

// Insert time-series data
db.metrics.insertMany([
  {
    timestamp: new Date(),
    metadata: { sensorId: "sensor-001", location: "datacenter-1" },
    temperature: 22.5,
    humidity: 45.2,
    cpuUsage: 67.8
  },
  {
    timestamp: new Date(),
    metadata: { sensorId: "sensor-002", location: "datacenter-1" },
    temperature: 23.1,
    humidity: 44.8,
    cpuUsage: 72.3
  }
])
```

### Advantages
- Optimized storage (columnar-like compression)
- Faster queries on time ranges
- Automatic bucketing
- Lower storage cost for high-volume time-series data

---

## Schema Validation

Enforce document structure at the database level.

```javascript
db.createCollection("users", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["name", "email", "age"],
      properties: {
        name: {
          bsonType: "string",
          description: "must be a string and is required"
        },
        email: {
          bsonType: "string",
          pattern: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
          description: "must be a valid email"
        },
        age: {
          bsonType: "int",
          minimum: 0,
          maximum: 150,
          description: "must be an integer between 0 and 150"
        },
        status: {
          enum: ["active", "inactive", "banned"],
          description: "must be one of the allowed values"
        },
        address: {
          bsonType: "object",
          required: ["city"],
          properties: {
            city: { bsonType: "string" },
            pincode: { bsonType: "string" }
          }
        }
      }
    }
  },
  validationLevel: "strict",       // "strict" | "moderate"
  validationAction: "error"        // "error" | "warn"
})
```

### Validation Levels
- **strict**: Validates ALL inserts and updates
- **moderate**: Validates inserts and updates to documents that already match the schema (allows existing non-conforming documents to be updated)

---

## MongoDB Atlas Search

Full-text search powered by Apache Lucene (available on Atlas).

```javascript
// Create search index (in Atlas UI or via API)
{
  "mappings": {
    "dynamic": true,
    "fields": {
      "title": { "type": "string", "analyzer": "lucene.english" },
      "description": { "type": "string", "analyzer": "lucene.english" },
      "tags": { "type": "string" }
    }
  }
}

// Search query using aggregation
db.products.aggregate([
  { $search: {
    text: {
      query: "wireless bluetooth headphones",
      path: ["title", "description"],
      fuzzy: { maxEdits: 1 }
    }
  }},
  { $limit: 10 },
  { $project: { title: 1, price: 1, score: { $meta: "searchScore" } } }
])
```

---

## Vector Search (MongoDB 7.0+ / Atlas)

For AI/ML applications — similarity search on embeddings.

```javascript
// Create vector search index
{
  "fields": [{
    "type": "vector",
    "path": "embedding",
    "numDimensions": 1536,
    "similarity": "cosine"
  }]
}

// Vector search query
db.products.aggregate([
  { $vectorSearch: {
    index: "vector_index",
    path: "embedding",
    queryVector: [0.1, 0.2, ...],  // 1536-dimension vector
    numCandidates: 100,
    limit: 10
  }},
  { $project: { title: 1, description: 1, score: { $meta: "vectorSearchScore" } } }
])
```

---

## Capped Collections

Fixed-size collections that automatically remove oldest documents.

```javascript
db.createCollection("logs", { 
  capped: true, 
  size: 1048576,    // 1 MB max size
  max: 5000         // Optional: max document count
})

// Characteristics:
// - Insertion order preserved (natural order)
// - Cannot delete individual documents
// - Cannot grow beyond specified size
// - Fast writes (no index overhead for inserts)
// - Useful for: logs, caches, circular buffers
```

---

## Interview Questions

**Q: What are change streams and when would you use them?**
A: Change streams allow applications to subscribe to real-time data changes (inserts, updates, deletes) without polling. Use cases: event-driven architectures, syncing data to search indexes (Elasticsearch), sending notifications on data changes, audit logging, and CDC (Change Data Capture) to Kafka.

**Q: How do change streams handle application restarts?**
A: Change streams provide resume tokens. Save the token from the last processed event. On restart, pass the token to `resumeAfter` to pick up exactly where you left off without missing events or reprocessing.

**Q: When would you use GridFS vs S3?**
A: Use GridFS when you want files co-located with your MongoDB data, benefiting from the same replication/backup strategy, and when files are accessed infrequently. Use S3/CDN when you need high-performance delivery, global distribution, cost-effective storage, or direct browser access to files. Most modern applications prefer S3 + MongoDB metadata.

**Q: What's schema validation and when should you use it?**
A: Schema validation enforces document structure at the database level (like constraints in SQL). Use it when multiple applications write to the same collection, when data integrity is critical, or as a safety net against application bugs. Use `validationAction: "warn"` during migration to find violations without breaking writes.

**Q: How would you implement CDC from MongoDB to Kafka?**
A: Use Change Streams to capture changes, produce them to Kafka topics. Or use MongoDB Kafka Connector (Debezium alternative) which handles this automatically with exactly-once semantics, resume tokens for recovery, and configurable transformations.
