# CRUD Operations

## Overview

MongoDB CRUD operations: Create, Read, Update, Delete — the foundation of all data manipulation.

```
Application
    ↓
┌─────────────────────────────────────┐
│         CRUD Operations              │
│  ┌─────────┐  ┌─────────┐          │
│  │ Create  │  │  Read   │          │
│  │insertOne│  │  find   │          │
│  │insertMany│ │ findOne │          │
│  └─────────┘  └─────────┘          │
│  ┌─────────┐  ┌─────────┐          │
│  │ Update  │  │ Delete  │          │
│  │updateOne│  │deleteOne│          │
│  │updateMany│ │deleteMany│         │
│  └─────────┘  └─────────┘          │
└─────────────────────────────────────┘
```

---

## CREATE Operations

### insertOne()

Inserts a single document into a collection.

```javascript
db.users.insertOne({
  name: "Vinay",
  email: "vinay@example.com",
  age: 28,
  skills: ["Java", "Spring Boot"],
  createdAt: new Date()
})

// Response
{
  acknowledged: true,
  insertedId: ObjectId("64a7f2e8b1c2d3e4f5a6b7c8")
}
```

### insertMany()

Inserts multiple documents in a single operation.

```javascript
db.users.insertMany([
  { name: "Alice", email: "alice@example.com", age: 25 },
  { name: "Bob", email: "bob@example.com", age: 30 },
  { name: "Charlie", email: "charlie@example.com", age: 35 }
])

// Response
{
  acknowledged: true,
  insertedIds: {
    '0': ObjectId("..."),
    '1': ObjectId("..."),
    '2': ObjectId("...")
  }
}
```

### Ordered vs Unordered Inserts
```javascript
// Default: ordered = true (stops on first error)
db.users.insertMany([doc1, doc2, doc3])

// Unordered: continues past errors
db.users.insertMany([doc1, doc2, doc3], { ordered: false })
```

**Key Point**: With `ordered: false`, MongoDB attempts to insert ALL documents even if some fail (e.g., duplicate `_id`). This is faster for bulk inserts where some failures are acceptable.

---

## READ Operations

### find()

Returns a cursor to all matching documents.

```javascript
// Find all documents
db.users.find()

// Find with filter
db.users.find({ age: { $gte: 25 } })

// Find with projection (include only specific fields)
db.users.find({ age: { $gte: 25 } }, { name: 1, email: 1, _id: 0 })

// Chaining
db.users.find({ status: "active" })
  .sort({ createdAt: -1 })
  .limit(10)
  .skip(20)
```

### findOne()

Returns the first matching document (not a cursor).

```javascript
db.users.findOne({ email: "vinay@example.com" })

// Returns the document directly (or null if not found)
{
  _id: ObjectId("..."),
  name: "Vinay",
  email: "vinay@example.com",
  age: 28
}
```

### Projection
```javascript
// Include fields (1 = include)
db.users.find({}, { name: 1, email: 1 })
// Result: { _id: ..., name: "...", email: "..." }

// Exclude fields (0 = exclude)
db.users.find({}, { password: 0, __v: 0 })

// Cannot mix include and exclude (except _id)
// This is VALID:
db.users.find({}, { name: 1, email: 1, _id: 0 })

// This is INVALID:
db.users.find({}, { name: 1, password: 0 })  // ERROR!
```

### Cursor Methods
```javascript
const cursor = db.users.find({ status: "active" })

cursor.sort({ age: 1 })        // Sort ascending
cursor.sort({ age: -1 })       // Sort descending
cursor.limit(10)               // Limit results
cursor.skip(20)                // Skip first 20
cursor.count()                 // Count documents (deprecated, use countDocuments)
cursor.toArray()               // Convert to array

// Modern count
db.users.countDocuments({ status: "active" })
db.users.estimatedDocumentCount()  // Faster, uses metadata
```

---

## UPDATE Operations

### updateOne()

Updates the first document matching the filter.

```javascript
db.users.updateOne(
  { email: "vinay@example.com" },          // filter
  { $set: { age: 29, updatedAt: new Date() } }  // update
)

// Response
{
  acknowledged: true,
  matchedCount: 1,
  modifiedCount: 1
}
```

### updateMany()

Updates ALL documents matching the filter.

```javascript
db.users.updateMany(
  { status: "inactive" },
  { $set: { archived: true, archivedAt: new Date() } }
)

// Response
{
  acknowledged: true,
  matchedCount: 150,
  modifiedCount: 150
}
```

### replaceOne()

Replaces the ENTIRE document (except _id).

```javascript
db.users.replaceOne(
  { email: "vinay@example.com" },
  {
    name: "Vinay Kumar",
    email: "vinay@example.com",
    age: 29,
    role: "Senior Engineer"
    // Note: all other fields from the original document are GONE
  }
)
```

**Critical Difference**: `updateOne` with `$set` modifies specific fields. `replaceOne` replaces the entire document body.

### Upsert
```javascript
// Insert if not found, update if found
db.users.updateOne(
  { email: "new@example.com" },
  { $set: { name: "New User", age: 22 } },
  { upsert: true }
)
```

---

## Update Operators

### $set — Set field values
```javascript
db.users.updateOne(
  { _id: userId },
  { $set: { name: "Updated Name", "address.city": "Mumbai" } }
)
```

### $unset — Remove fields
```javascript
db.users.updateOne(
  { _id: userId },
  { $unset: { temporaryField: "", oldField: "" } }
)
// Value provided to $unset doesn't matter, field is removed
```

### $inc — Increment numeric values
```javascript
db.products.updateOne(
  { _id: productId },
  { $inc: { quantity: -1, viewCount: 1 } }
)
// quantity decreases by 1, viewCount increases by 1
```

### $push — Add element to array
```javascript
db.users.updateOne(
  { _id: userId },
  { $push: { skills: "MongoDB" } }
)

// Push multiple
db.users.updateOne(
  { _id: userId },
  { $push: { skills: { $each: ["Docker", "Kubernetes"] } } }
)

// Push with sort and limit (maintain top-N)
db.users.updateOne(
  { _id: userId },
  {
    $push: {
      scores: {
        $each: [85, 92],
        $sort: -1,
        $slice: 5  // Keep only top 5
      }
    }
  }
)
```

### $pull — Remove elements from array
```javascript
// Remove specific value
db.users.updateOne(
  { _id: userId },
  { $pull: { skills: "Java" } }
)

// Remove by condition
db.users.updateOne(
  { _id: userId },
  { $pull: { scores: { $lt: 50 } } }
)
```

### $addToSet — Add to array only if not already present
```javascript
db.users.updateOne(
  { _id: userId },
  { $addToSet: { skills: "MongoDB" } }
)
// If "MongoDB" already in skills array, no change
// If not present, it's added

// Multiple values
db.users.updateOne(
  { _id: userId },
  { $addToSet: { skills: { $each: ["Docker", "K8s"] } } }
)
```

### $pop — Remove first or last element from array
```javascript
// Remove last element
db.users.updateOne({ _id: userId }, { $pop: { skills: 1 } })

// Remove first element
db.users.updateOne({ _id: userId }, { $pop: { skills: -1 } })
```

### $rename — Rename a field
```javascript
db.users.updateMany(
  {},
  { $rename: { "fname": "firstName", "lname": "lastName" } }
)
```

---

## DELETE Operations

### deleteOne()

Deletes the first document matching the filter.

```javascript
db.users.deleteOne({ email: "old@example.com" })

// Response
{ acknowledged: true, deletedCount: 1 }
```

### deleteMany()

Deletes ALL documents matching the filter.

```javascript
// Delete all inactive users
db.users.deleteMany({ status: "inactive", lastLogin: { $lt: new Date("2023-01-01") } })

// Delete ALL documents (empty filter)
db.users.deleteMany({})  // ⚠️ Dangerous! Removes all documents.
```

---

## Bulk Write Operations

For high-performance batch operations:

```javascript
db.users.bulkWrite([
  { insertOne: { document: { name: "User1", age: 25 } } },
  { updateOne: { filter: { name: "User2" }, update: { $set: { age: 30 } } } },
  { deleteOne: { filter: { name: "User3" } } },
  { replaceOne: { filter: { name: "User4" }, replacement: { name: "User4", age: 35 } } }
], { ordered: false })
```

---

## findOneAndUpdate / findOneAndDelete

Atomic operations that return the document.

```javascript
// Find, update, and return the UPDATED document
db.users.findOneAndUpdate(
  { email: "vinay@example.com" },
  { $inc: { loginCount: 1 }, $set: { lastLogin: new Date() } },
  { returnDocument: "after" }  // "before" returns original
)

// Find and delete, returning the deleted document
db.users.findOneAndDelete({ email: "old@example.com" })
```

**Use Case**: When you need to atomically read and modify a document (e.g., claiming a task from a queue).

---

## Interview Questions

**Q: What's the difference between updateOne and replaceOne?**
A: `updateOne` uses update operators ($set, $inc, etc.) to modify specific fields. `replaceOne` replaces the entire document body (except _id). Use `updateOne` when modifying fields, `replaceOne` when you want to overwrite the whole document.

**Q: What happens if updateOne filter matches no documents?**
A: `matchedCount: 0, modifiedCount: 0`. No error. Use `upsert: true` if you want to insert when not found.

**Q: Difference between $push and $addToSet?**
A: `$push` always adds the element (allows duplicates). `$addToSet` only adds if the element doesn't already exist in the array.

**Q: How do you handle bulk inserts with potential duplicate keys?**
A: Use `insertMany` with `{ ordered: false }`. This continues inserting remaining documents even if some fail due to duplicate key errors.

**Q: What is the difference between find() and findOne()?**
A: `find()` returns a cursor (lazy iteration over results). `findOne()` returns the first matching document directly (or null). Use `findOne()` when you only need one document.

**Q: How would you implement a simple queue with MongoDB?**
A: Use `findOneAndUpdate` with a filter for unclaimed items and an update to mark as claimed. The atomicity ensures no two consumers claim the same item.
