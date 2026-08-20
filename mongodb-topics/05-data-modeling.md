# Data Modeling ⭐

## The Golden Rule

> "How will this data be accessed?"

MongoDB data modeling should be driven by **access patterns**, not by entity relationships like in SQL normalization. Start with your queries, then design your schema.

---

## Embedding vs Referencing

### Embedded (Denormalized)
```json
{
  "orderId": "ORD-001",
  "orderDate": "2024-06-15",
  "customer": {
    "name": "Vinay Kumar",
    "email": "vinay@example.com",
    "phone": "+91-9876543210"
  },
  "items": [
    { "productId": "P1", "name": "Laptop", "price": 999, "qty": 1 },
    { "productId": "P2", "name": "Mouse", "price": 29, "qty": 2 }
  ],
  "shippingAddress": {
    "street": "123 Main St",
    "city": "Bangalore",
    "pincode": "560001"
  },
  "total": 1057
}
```

**Single read fetches everything needed to display the order.**

### Referenced (Normalized)
```json
// orders collection
{
  "orderId": "ORD-001",
  "customerId": ObjectId("..."),
  "items": [
    { "productId": ObjectId("..."), "qty": 1 },
    { "productId": ObjectId("..."), "qty": 2 }
  ],
  "shippingAddressId": ObjectId("...")
}

// customers collection
{ "_id": ObjectId("..."), "name": "Vinay Kumar", "email": "vinay@example.com" }

// products collection
{ "_id": ObjectId("..."), "name": "Laptop", "price": 999 }
```

**Requires multiple reads or $lookup to assemble the full order.**

---

## When to Embed

| Condition | Embed |
|-----------|-------|
| Data is always accessed together | ✅ |
| One-to-one relationship | ✅ |
| One-to-few relationship (bounded) | ✅ |
| Data doesn't change frequently | ✅ |
| Embedded data is "owned" by parent | ✅ |
| Need atomic operations on related data | ✅ |
| Read-heavy access pattern | ✅ |

### Examples Where Embedding Works
- Order → Shipping Address (address at time of order, rarely changes)
- User → Preferences (always accessed with user)
- Blog Post → Comments (if comments are few and bounded)
- Product → Reviews (if bounded, e.g., last 10 reviews)

---

## When to Reference

| Condition | Reference |
|-----------|-----------|
| Data is accessed independently | ✅ |
| One-to-many (unbounded) | ✅ |
| Many-to-many relationship | ✅ |
| Data changes frequently | ✅ |
| Data is shared across many documents | ✅ |
| Document size would exceed 16 MB | ✅ |
| Need to query the related data independently | ✅ |

### Examples Where Referencing Works
- User → Orders (user has unlimited orders)
- Product → Reviews (millions of reviews)
- Author → Books (many-to-many with publishers)
- User → Messages (unbounded, queried independently)

---

## Relationship Patterns

### One-to-One
```javascript
// EMBED (preferred for 1:1)
{
  name: "Vinay",
  email: "vinay@example.com",
  profile: {
    bio: "Backend developer",
    avatar: "url",
    social: { github: "vinay-dev" }
  }
}
```

### One-to-Few (1:N where N is small and bounded)
```javascript
// EMBED
{
  name: "Vinay",
  addresses: [
    { label: "Home", street: "123 Main St", city: "Bangalore" },
    { label: "Office", street: "456 Tech Park", city: "Bangalore" }
  ]
}
// Users rarely have more than 3-5 addresses
```

### One-to-Many (1:N where N is large)
```javascript
// REFERENCE — store reference on the "many" side
// products collection
{
  _id: ObjectId("product1"),
  name: "Laptop",
  categoryId: ObjectId("cat1")  // Reference to category
}

// categories collection
{
  _id: ObjectId("cat1"),
  name: "Electronics"
}
```

### One-to-Squillions (1:N where N is massive)
```javascript
// REFERENCE — always on the "many" side
// logs collection (millions per host)
{
  _id: ObjectId("..."),
  hostId: ObjectId("host1"),
  message: "CPU spike detected",
  timestamp: ISODate("2024-06-15T10:30:00Z"),
  level: "WARNING"
}

// hosts collection
{
  _id: ObjectId("host1"),
  hostname: "server-prod-01",
  ip: "10.0.1.5"
}
// NEVER store array of millions of logIds in the host document!
```

### Many-to-Many
```javascript
// Option 1: Array of references (if bounded)
// students collection
{
  _id: ObjectId("student1"),
  name: "Vinay",
  courseIds: [ObjectId("course1"), ObjectId("course2"), ObjectId("course3")]
}

// courses collection
{
  _id: ObjectId("course1"),
  title: "MongoDB",
  studentIds: [ObjectId("student1"), ObjectId("student2")]
}

// Option 2: Junction collection (if unbounded or need extra data)
// enrollments collection
{
  studentId: ObjectId("student1"),
  courseId: ObjectId("course1"),
  enrolledAt: ISODate("2024-01-15"),
  grade: "A",
  completedAt: ISODate("2024-06-01")
}
```

---

## Design Patterns

### Subset Pattern
Store frequently accessed data embedded, full data in a separate collection.

```javascript
// products collection (hot data)
{
  _id: ObjectId("p1"),
  name: "Laptop Pro",
  price: 1299,
  recentReviews: [
    { user: "Alice", rating: 5, text: "Great!", date: "2024-06-10" },
    { user: "Bob", rating: 4, text: "Good value", date: "2024-06-08" }
  ],
  averageRating: 4.5,
  totalReviews: 2847
}

// reviews collection (full data)
{
  productId: ObjectId("p1"),
  userId: ObjectId("..."),
  rating: 5,
  text: "Great laptop! Battery life is amazing...",
  date: ISODate("2024-06-10"),
  helpful: 42
}
```

### Bucket Pattern
Group related data into time-based or count-based buckets.

```javascript
// Instead of one document per sensor reading (millions):
// Group into hourly buckets
{
  sensorId: "sensor-001",
  date: ISODate("2024-06-15"),
  hour: 14,
  readings: [
    { time: ISODate("2024-06-15T14:00:05Z"), temp: 22.5 },
    { time: ISODate("2024-06-15T14:00:10Z"), temp: 22.6 },
    { time: ISODate("2024-06-15T14:00:15Z"), temp: 22.4 }
    // ... up to 720 readings per bucket (every 5 seconds)
  ],
  count: 720,
  sum: 16200,
  avg: 22.5,
  min: 21.8,
  max: 23.2
}
```

### Extended Reference Pattern
Embed frequently needed fields from referenced document.

```javascript
// orders collection
{
  orderId: "ORD-001",
  customerId: ObjectId("customer1"),
  // Embedded subset of customer data (avoids $lookup for common reads)
  customerName: "Vinay Kumar",
  customerEmail: "vinay@example.com",
  items: [...]
}
// Full customer data still lives in customers collection
// Trade-off: slight duplication vs. read performance
```

### Computed Pattern
Pre-compute values to avoid expensive runtime calculations.

```javascript
// Instead of calculating total views on every read:
{
  productId: "P1",
  name: "Laptop",
  dailyViews: 1523,
  weeklyViews: 8942,
  monthlyViews: 34521,
  totalViews: 892341,
  lastComputed: ISODate("2024-06-15T00:00:00Z")
}
// Update counters atomically with $inc
```

### Polymorphic Pattern
Single collection stores different types of documents sharing a base structure.

```javascript
// notifications collection
{ type: "email", to: "vinay@example.com", subject: "Welcome", body: "..." }
{ type: "sms", to: "+919876543210", message: "OTP: 123456" }
{ type: "push", deviceToken: "abc123", title: "New Order", payload: {...} }

// All share: _id, type, createdAt, status
// Each has type-specific fields
```

### Outlier Pattern
Handle exceptional documents differently.

```javascript
// Most users have < 100 followers (embed)
{
  username: "regular_user",
  followers: ["user1", "user2", "user3"]
}

// Celebrity users have millions (reference + flag)
{
  username: "celebrity",
  hasOverflow: true,
  followers: ["user1", "user2", ... /* first 1000 */]
}
// Overflow stored in separate collection
// followers_overflow: { userId: "celebrity", followerId: "user5001" }
```

---

## Document Size Considerations

| Factor | Guideline |
|--------|-----------|
| Max document size | 16 MB |
| Ideal document size | < 1 MB for most use cases |
| Array elements | Keep bounded (< 1000 for embedded) |
| Nesting depth | Avoid > 3-4 levels deep |
| Field names | Keep short (they're stored in every document) |

---

## Duplication vs Normalization

### Acceptable Duplication
- Data that rarely changes (country names, category names)
- Historical snapshots (price at time of order)
- Read-optimized data (denormalized for fast reads)
- Computed/cached values

### Avoid Duplicating
- Frequently changing data (user email that changes → update nightmare)
- Large data (images, long text stored in many places)
- Data requiring strong consistency across references

---

## Decision Framework

```
Start with Access Patterns
         ↓
┌─────────────────────────────────────┐
│ Question 1: Read together?          │
│   YES → Consider embedding          │
│   NO  → Consider referencing        │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Question 2: Bounded relationship?   │
│   YES (< ~100) → Embedding safe     │
│   NO (unbounded) → Reference        │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Question 3: Update frequency?       │
│   Rarely changes → Embedding OK     │
│   Frequently changes → Reference    │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Question 4: Data accessed alone?    │
│   Never alone → Embed               │
│   Often independent → Reference     │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Question 5: Document size risk?     │
│   Will stay < 16MB → OK to embed    │
│   Could grow large → Reference      │
└─────────────────────────────────────┘
```

---

## Real-World Modeling Examples

### E-Commerce Order
```javascript
// HYBRID: Embed what you read together, reference what you don't
{
  _id: ObjectId("..."),
  orderNumber: "ORD-20240615-001",
  status: "shipped",
  customerId: ObjectId("..."),           // Reference (full profile rarely needed)
  customerSnapshot: {                     // Embedded snapshot
    name: "Vinay Kumar",
    email: "vinay@example.com"
  },
  items: [                               // Embedded (always shown with order)
    {
      productId: ObjectId("..."),        // Reference (for navigation)
      name: "MacBook Pro",              // Snapshot (for display)
      price: 1299,                      // Snapshot (price at time of order)
      qty: 1
    }
  ],
  shippingAddress: {                     // Embedded (historical, doesn't change)
    street: "123 Main St",
    city: "Bangalore",
    pincode: "560001"
  },
  payment: {                             // Embedded (1:1, always read together)
    method: "card",
    last4: "4242",
    amount: 1299,
    currency: "USD"
  },
  timeline: [                            // Embedded (bounded, < 10 events per order)
    { status: "placed", at: ISODate("...") },
    { status: "confirmed", at: ISODate("...") },
    { status: "shipped", at: ISODate("...") }
  ],
  createdAt: ISODate("..."),
  updatedAt: ISODate("...")
}
```

---

## Interview Questions

**Q: How do you decide between embedding and referencing?**
A: Start with access patterns. If data is always read together, bounded in size, and owned by the parent → embed. If data is accessed independently, unbounded, shared, or frequently updated → reference. The key question is "How will this data be accessed?"

**Q: What's the problem with unbounded arrays?**
A: They can push documents past the 16 MB limit, cause increased memory usage (entire document loaded even if you need just one element), and make updates progressively slower as the array grows.

**Q: When would you duplicate data intentionally?**
A: When you need historical snapshots (price at time of order), when the duplicated data rarely changes (category names), or when read performance is critical and the data is expensive to look up ($lookup elimination).

**Q: How do you handle many-to-many relationships in MongoDB?**
A: Three options: (1) Arrays of references in both documents (if bounded), (2) Array of references on one side only (common), (3) Junction collection (if unbounded or relationship has its own attributes like enrollment date/grade).

**Q: Your order document is approaching 16 MB. What do you do?**
A: Identify what's causing growth (usually an unbounded array like order history, messages, or logs). Move the growing array to a separate collection with a reference. Consider the bucket pattern for time-series data. Use the subset pattern to keep recent items embedded and archive old ones.

**Q: A SQL developer asks you to normalize their MongoDB schema. What do you say?**
A: MongoDB normalization != SQL normalization. In SQL, you normalize to avoid data anomalies. In MongoDB, you model for access patterns. Some duplication is acceptable and even desirable when it eliminates expensive joins. The goal is to satisfy your read/write patterns efficiently, not to achieve a normal form.
