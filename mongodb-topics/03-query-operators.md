# Query Operators

## Overview

MongoDB query operators allow you to filter documents based on various conditions. Mastering these is essential for efficient data retrieval.

---

## Comparison Operators

### $eq — Equal
```javascript
// Explicit
db.users.find({ age: { $eq: 25 } })

// Implicit (shorthand)
db.users.find({ age: 25 })

// Works with all types
db.users.find({ status: { $eq: "active" } })
db.users.find({ isVerified: { $eq: true } })
```

### $ne — Not Equal
```javascript
db.users.find({ status: { $ne: "inactive" } })
// Returns documents where status is NOT "inactive"
// Also returns documents where status field doesn't exist!
```

### $gt — Greater Than
```javascript
db.users.find({ age: { $gt: 25 } })
db.orders.find({ createdAt: { $gt: new Date("2024-01-01") } })
```

### $gte — Greater Than or Equal
```javascript
db.users.find({ age: { $gte: 18 } })
```

### $lt — Less Than
```javascript
db.users.find({ age: { $lt: 65 } })
```

### $lte — Less Than or Equal
```javascript
db.products.find({ price: { $lte: 100 } })
```

### $in — Match any value in array
```javascript
db.users.find({ status: { $in: ["active", "pending"] } })
db.products.find({ category: { $in: ["electronics", "books", "clothing"] } })

// Efficient alternative to multiple $or conditions
// This is equivalent to:
db.users.find({ $or: [{ status: "active" }, { status: "pending" }] })
```

### $nin — Not in array
```javascript
db.users.find({ role: { $nin: ["admin", "superadmin"] } })
// Also matches documents where 'role' field doesn't exist
```

### Range Queries (Combining Operators)
```javascript
// Age between 18 and 65 (inclusive)
db.users.find({ age: { $gte: 18, $lte: 65 } })

// Date range
db.orders.find({
  createdAt: {
    $gte: new Date("2024-01-01"),
    $lt: new Date("2024-02-01")
  }
})
```

---

## Logical Operators

### $and — All conditions must match
```javascript
// Explicit $and
db.users.find({
  $and: [
    { age: { $gte: 18 } },
    { status: "active" },
    { role: "developer" }
  ]
})

// Implicit $and (comma-separated — preferred for simple cases)
db.users.find({
  age: { $gte: 18 },
  status: "active",
  role: "developer"
})

// Explicit $and is REQUIRED when querying same field multiple times
db.products.find({
  $and: [
    { price: { $gt: 10 } },
    { price: { $lt: 100 } }
  ]
})
// Though this is simpler:
db.products.find({ price: { $gt: 10, $lt: 100 } })
```

### $or — At least one condition must match
```javascript
db.users.find({
  $or: [
    { age: { $lt: 18 } },
    { age: { $gt: 65 } }
  ]
})

// Combined with other conditions
db.users.find({
  status: "active",
  $or: [
    { role: "admin" },
    { department: "engineering" }
  ]
})
// Means: status is active AND (role is admin OR department is engineering)
```

### $nor — None of the conditions match
```javascript
db.users.find({
  $nor: [
    { status: "banned" },
    { age: { $lt: 13 } }
  ]
})
// Returns documents where status is NOT "banned" AND age is NOT less than 13
// Also returns documents where these fields don't exist!
```

### $not — Negates a condition
```javascript
// Applied to a single field's expression
db.users.find({ age: { $not: { $gt: 65 } } })
// Returns: age <= 65 OR age field doesn't exist

// With regex
db.users.find({ name: { $not: /^A/ } })
// Returns documents where name doesn't start with 'A' (or name doesn't exist)
```

**Key Difference**: `$not` applies to a single operator expression. `$nor` applies to multiple conditions.

---

## Element Operators

### $exists — Field existence check
```javascript
// Documents that HAVE the field
db.users.find({ phone: { $exists: true } })

// Documents that DON'T have the field
db.users.find({ deletedAt: { $exists: false } })

// Common pattern: find documents with field AND value
db.users.find({ phone: { $exists: true, $ne: null } })
```

### $type — Check field's BSON type
```javascript
// Find documents where age is stored as string (data quality check)
db.users.find({ age: { $type: "string" } })

// Find documents where age is a number
db.users.find({ age: { $type: "number" } })

// Multiple types
db.users.find({ age: { $type: ["int", "double"] } })

// Common BSON type names:
// "double", "string", "object", "array", "objectId",
// "bool", "date", "null", "int", "long", "decimal"
```

---

## Evaluation Operators

### $regex — Regular expression matching
```javascript
// Case-insensitive search
db.users.find({ name: { $regex: /vinay/i } })

// Starts with
db.users.find({ email: { $regex: /^admin/ } })

// Ends with
db.users.find({ email: { $regex: /@gmail\.com$/ } })

// With options
db.users.find({ name: { $regex: "kumar", $options: "i" } })
```

**⚠️ Performance Warning**: Regex queries that don't start with `^` (anchor) cannot use indexes efficiently. Always prefer anchored patterns.

### $expr — Use aggregation expressions in queries
```javascript
// Compare two fields in the same document
db.orders.find({
  $expr: { $gt: ["$totalAmount", "$budget"] }
})

// Use aggregation operators
db.users.find({
  $expr: {
    $gt: [{ $size: "$orders" }, 5]
  }
})
```

### $text — Full-text search (requires text index)
```javascript
// Requires: db.articles.createIndex({ content: "text", title: "text" })

db.articles.find({ $text: { $search: "mongodb spring boot" } })

// Exact phrase
db.articles.find({ $text: { $search: "\"spring boot\"" } })

// Exclude term
db.articles.find({ $text: { $search: "mongodb -sql" } })
```

---

## Array Query Operators

### $elemMatch — Match array element with multiple conditions
```javascript
// Document structure
{
  name: "Vinay",
  scores: [
    { subject: "math", score: 85 },
    { subject: "english", score: 72 },
    { subject: "science", score: 91 }
  ]
}

// Find users with a score > 80 in math
db.users.find({
  scores: { $elemMatch: { subject: "math", score: { $gt: 80 } } }
})

// WITHOUT $elemMatch (incorrect behavior):
db.users.find({ "scores.subject": "math", "scores.score": { $gt: 80 } })
// This would match if ANY element has subject "math" AND ANY element has score > 80
// They don't have to be the SAME element!
```

### $all — Array contains ALL specified elements
```javascript
db.users.find({ skills: { $all: ["Java", "Spring Boot", "MongoDB"] } })
// User must have ALL three skills (order doesn't matter)
```

### $size — Array has exact length
```javascript
db.users.find({ skills: { $size: 5 } })
// Users with exactly 5 skills

// ⚠️ $size doesn't accept ranges!
// For range queries on array size, use $expr:
db.users.find({ $expr: { $gte: [{ $size: "$skills" }, 3] } })
```

### Querying Specific Array Positions
```javascript
// First element equals "Java"
db.users.find({ "skills.0": "Java" })

// Any element in array equals "MongoDB"
db.users.find({ skills: "MongoDB" })
```

---

## Combining Operators — Real-World Examples

### E-commerce Product Search
```javascript
db.products.find({
  category: { $in: ["electronics", "computers"] },
  price: { $gte: 100, $lte: 1000 },
  stock: { $gt: 0 },
  "ratings.average": { $gte: 4.0 },
  $or: [
    { brand: "Apple" },
    { brand: "Samsung" }
  ]
})
```

### User Management Dashboard
```javascript
db.users.find({
  status: "active",
  createdAt: { $gte: new Date("2024-01-01") },
  role: { $nin: ["admin", "superadmin"] },
  "profile.verified": true,
  skills: { $all: ["Java", "Spring"] },
  $expr: { $gte: [{ $size: "$projects" }, 3] }
})
```

### Audit Log Query
```javascript
db.auditLogs.find({
  $and: [
    { timestamp: { $gte: new Date("2024-06-01"), $lt: new Date("2024-07-01") } },
    { action: { $in: ["DELETE", "UPDATE"] } },
    { $or: [
      { "resource.type": "order" },
      { "resource.type": "payment" }
    ]}
  ]
}).sort({ timestamp: -1 }).limit(100)
```

---

## Interview Questions

**Q: What's the difference between $in and $or?**
A: `$in` checks a single field against multiple values. `$or` can check multiple fields with different conditions. `$in` is more efficient when checking one field against multiple values.

**Q: When do you NEED explicit $and vs implicit comma separation?**
A: When you need multiple conditions on the SAME field. `{ age: { $gt: 18 }, age: { $lt: 65 } }` — the second `age` would overwrite the first. Use `$and` or combine operators: `{ age: { $gt: 18, $lt: 65 } }`.

**Q: What does $ne return for documents where the field doesn't exist?**
A: `$ne` returns documents where the field doesn't exist as well. If you want only documents that HAVE the field but with a different value, combine with `$exists`: `{ field: { $exists: true, $ne: "value" } }`.

**Q: Why is $elemMatch important for array queries?**
A: Without `$elemMatch`, conditions on array sub-fields can match across different elements. `$elemMatch` ensures ALL conditions match the SAME array element.

**Q: How does regex performance work with indexes?**
A: Only prefix patterns (starting with `^`) can use indexes efficiently. Non-anchored regex like `/vinay/` requires a full collection scan or index scan of all values. Always anchor your regex when possible.

**Q: What's the difference between $not and $ne?**
A: `$ne` is a comparison operator for simple inequality. `$not` is a logical operator that negates any operator expression. `$not` can negate complex expressions like `{ $not: { $gt: 5, $lt: 10 } }`.
