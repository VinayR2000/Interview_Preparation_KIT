# Arrays and Embedded Documents

## Why Arrays Matter in MongoDB

Arrays are one of MongoDB's most powerful features. They allow you to store related data together in a single document, reducing the need for JOINs.

```json
{
  "name": "Vinay",
  "skills": ["Java", "Spring Boot", "MongoDB", "Kafka"],
  "experience": [
    { "company": "TechCorp", "role": "Senior Dev", "years": 3 },
    { "company": "StartupXYZ", "role": "Backend Dev", "years": 2 }
  ]
}
```

---

## Querying Arrays

### Match Exact Array
```javascript
// Exact match (order matters, must be exact)
db.users.find({ skills: ["Java", "Spring Boot"] })
// Only matches if skills is EXACTLY ["Java", "Spring Boot"] — nothing more, nothing less
```

### Match Any Element
```javascript
// Contains "MongoDB" anywhere in the array
db.users.find({ skills: "MongoDB" })

// Contains any of these
db.users.find({ skills: { $in: ["MongoDB", "PostgreSQL"] } })
```

### Match All Elements
```javascript
// Must contain ALL specified values (order doesn't matter)
db.users.find({ skills: { $all: ["Java", "Spring Boot", "MongoDB"] } })
```

### Match by Array Position
```javascript
// First skill is "Java"
db.users.find({ "skills.0": "Java" })

// Third element in experience array
db.users.find({ "experience.2.company": "Google" })
```

### Match by Array Size
```javascript
// Exactly 5 skills
db.users.find({ skills: { $size: 5 } })

// More than 3 skills (no direct $size range, use $expr)
db.users.find({ $expr: { $gt: [{ $size: "$skills" }, 3] } })

// Has at least one skill (not empty)
db.users.find({ skills: { $exists: true, $ne: [] } })
// OR
db.users.find({ "skills.0": { $exists: true } })
```

---

## $elemMatch — The Most Important Array Operator

### The Problem Without $elemMatch
```javascript
// Document
{
  name: "Vinay",
  scores: [
    { subject: "math", score: 65 },
    { subject: "science", score: 92 }
  ]
}

// INCORRECT: "Find users with science score > 80"
db.users.find({ "scores.subject": "science", "scores.score": { $gt: 80 } })
// This matches if ANY element has subject "science" AND ANY element has score > 80
// These can be DIFFERENT elements!
// Would match even if science score was 65 (because math has nothing to do with it
// and some OTHER element might have score > 80)
```

### The Solution With $elemMatch
```javascript
// CORRECT: Both conditions must be on the SAME array element
db.users.find({
  scores: {
    $elemMatch: { subject: "science", score: { $gt: 80 } }
  }
})
```

### Complex $elemMatch
```javascript
// Find orders with an item that costs > $50 AND quantity > 2
db.orders.find({
  items: {
    $elemMatch: {
      price: { $gt: 50 },
      quantity: { $gt: 2 },
      status: { $ne: "cancelled" }
    }
  }
})
```

---

## Querying Embedded Documents

### Dot Notation
```javascript
// Document
{
  name: "Vinay",
  address: {
    street: "123 Main St",
    city: "Bangalore",
    state: "Karnataka",
    pincode: "560001"
  }
}

// Query nested field
db.users.find({ "address.city": "Bangalore" })
db.users.find({ "address.pincode": { $regex: /^560/ } })

// Multiple conditions on nested document
db.users.find({
  "address.city": "Bangalore",
  "address.state": "Karnataka"
})
```

### Exact Embedded Document Match
```javascript
// Matches ONLY if address is EXACTLY this object (all fields, same order)
db.users.find({
  address: {
    street: "123 Main St",
    city: "Bangalore",
    state: "Karnataka",
    pincode: "560001"
  }
})
// If the document has additional fields in address, this won't match!
// Use dot notation for partial matching
```

### Deep Nesting
```javascript
// Document
{
  name: "Vinay",
  profile: {
    social: {
      github: "vinay-dev",
      linkedin: "vinay-kumar"
    }
  }
}

// Query deeply nested
db.users.find({ "profile.social.github": "vinay-dev" })
```

---

## Querying Arrays of Embedded Documents

### Common Pattern: Array of Objects
```javascript
// Document
{
  orderId: "ORD-001",
  items: [
    { productId: "P1", name: "Laptop", price: 999, qty: 1 },
    { productId: "P2", name: "Mouse", price: 29, qty: 2 },
    { productId: "P3", name: "Keyboard", price: 59, qty: 1 }
  ]
}

// Find orders containing product "P1"
db.orders.find({ "items.productId": "P1" })

// Find orders with any item priced over $500
db.orders.find({ "items.price": { $gt: 500 } })

// Find orders with a specific product AND quantity > 1
// MUST use $elemMatch for same-element matching
db.orders.find({
  items: { $elemMatch: { productId: "P2", qty: { $gt: 1 } } }
})
```

---

## Updating Arrays

### $push — Add element
```javascript
db.users.updateOne(
  { name: "Vinay" },
  { $push: { skills: "Docker" } }
)

// Push to nested array
db.orders.updateOne(
  { orderId: "ORD-001" },
  { $push: { items: { productId: "P4", name: "Cable", price: 15, qty: 3 } } }
)
```

### $push with Modifiers
```javascript
// Add multiple + sort + keep only top 10
db.users.updateOne(
  { name: "Vinay" },
  {
    $push: {
      recentScores: {
        $each: [95, 87, 92],
        $sort: -1,       // Sort descending
        $slice: 10       // Keep only 10 elements
      }
    }
  }
)

// $position — insert at specific index
db.users.updateOne(
  { name: "Vinay" },
  {
    $push: {
      skills: {
        $each: ["Kubernetes"],
        $position: 0    // Insert at beginning
      }
    }
  }
)
```

### $pull — Remove elements
```javascript
// Remove specific value
db.users.updateOne(
  { name: "Vinay" },
  { $pull: { skills: "Java" } }
)

// Remove by condition
db.orders.updateOne(
  { orderId: "ORD-001" },
  { $pull: { items: { price: { $lt: 20 } } } }
)

// Remove from array of embedded documents
db.users.updateOne(
  { name: "Vinay" },
  { $pull: { experience: { company: "StartupXYZ" } } }
)
```

### $addToSet — Add only if unique
```javascript
db.users.updateOne(
  { name: "Vinay" },
  { $addToSet: { skills: "MongoDB" } }
)
// No duplicate added if "MongoDB" already exists
```

### $pop — Remove first/last
```javascript
// Remove last element
db.users.updateOne({ name: "Vinay" }, { $pop: { skills: 1 } })

// Remove first element
db.users.updateOne({ name: "Vinay" }, { $pop: { skills: -1 } })
```

### Positional Operator ($) — Update specific array element
```javascript
// Update the first matching element
db.orders.updateOne(
  { orderId: "ORD-001", "items.productId": "P2" },
  { $set: { "items.$.price": 35 } }
)
// The $ refers to the first element that matched the query condition

// Update nested field in matched element
db.orders.updateOne(
  { orderId: "ORD-001", "items.productId": "P2" },
  { $set: { "items.$.status": "shipped" } }
)
```

### Positional All Operator ($[]) — Update ALL array elements
```javascript
db.orders.updateOne(
  { orderId: "ORD-001" },
  { $set: { "items.$[].status": "processing" } }
)
// Sets status to "processing" for ALL items in the array
```

### Filtered Positional Operator ($[<identifier>]) — Update filtered elements
```javascript
db.orders.updateOne(
  { orderId: "ORD-001" },
  { $set: { "items.$[item].status": "shipped" } },
  { arrayFilters: [{ "item.price": { $gt: 50 } }] }
)
// Only updates items where price > 50
```

---

## Array Projection

### $slice — Limit array elements returned
```javascript
// First 3 elements
db.users.find({ name: "Vinay" }, { skills: { $slice: 3 } })

// Last 2 elements
db.users.find({ name: "Vinay" }, { skills: { $slice: -2 } })

// Skip 2, take 3 (pagination within array)
db.users.find({ name: "Vinay" }, { skills: { $slice: [2, 3] } })
```

### $elemMatch in Projection — Return only first matching element
```javascript
db.orders.find(
  { orderId: "ORD-001" },
  { items: { $elemMatch: { price: { $gt: 500 } } } }
)
// Returns only the FIRST item matching price > 500 in the projection
```

---

## Common Patterns and Best Practices

### Pattern: Tags/Labels
```javascript
// Document
{ title: "MongoDB Guide", tags: ["database", "nosql", "backend"] }

// Find by tag
db.articles.find({ tags: "nosql" })

// Find by multiple tags (AND)
db.articles.find({ tags: { $all: ["database", "backend"] } })

// Add tag (avoid duplicates)
db.articles.updateOne({ _id: id }, { $addToSet: { tags: "tutorial" } })
```

### Pattern: Bounded Arrays (Recent Activity)
```javascript
// Keep only last 50 activities
db.users.updateOne(
  { _id: userId },
  {
    $push: {
      recentActivity: {
        $each: [{ action: "login", time: new Date() }],
        $slice: -50  // Keep last 50 only
      }
    }
  }
)
```

### Anti-Pattern: Unbounded Arrays ⚠️
```javascript
// BAD: Array grows without limit
{
  userId: "user1",
  messages: [/* could grow to millions */]
}

// GOOD: Separate collection with reference
// messages collection:
{ userId: "user1", text: "Hello", sentAt: new Date() }
```

---

## Interview Questions

**Q: What's the 16 MB document limit implication for arrays?**
A: Arrays that grow without bounds (unbounded arrays) can push a document past 16 MB. Design patterns like bucket pattern or separate collections should be used for potentially large arrays.

**Q: How do you query the Nth element of an array?**
A: Use dot notation with the index: `db.collection.find({ "arrayField.2": "value" })` (0-indexed).

**Q: When is $elemMatch required vs dot notation?**
A: When you need multiple conditions to apply to the SAME array element. With dot notation, MongoDB can match different conditions against different array elements.

**Q: How do you update a specific element in an array without knowing its index?**
A: Use the positional operator ($) in combination with a query that matches the element: `{ $set: { "items.$.field": value } }`. The query must include a condition on the array field.

**Q: What's the difference between $push and $addToSet?**
A: `$push` always appends (allows duplicates). `$addToSet` only appends if the value doesn't already exist. For arrays of objects, `$addToSet` uses deep equality comparison.

**Q: How would you implement pagination within an array?**
A: Use `$slice` in projection: `{ arrayField: { $slice: [skip, limit] } }`. However, if the array is very large, consider moving it to a separate collection and using standard pagination.
