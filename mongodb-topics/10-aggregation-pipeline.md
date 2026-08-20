# Aggregation Pipeline ⭐⭐⭐

## What is the Aggregation Pipeline?

The aggregation pipeline is MongoDB's framework for data processing and transformation. Documents enter a multi-stage pipeline where each stage transforms the documents as they pass through.

```
Documents (Input)
       ↓
┌─────────────┐
│   $match    │  ← Filter documents
└──────┬──────┘
       ↓
┌─────────────┐
│  $project   │  ← Shape/transform fields
└──────┬──────┘
       ↓
┌─────────────┐
│   $group    │  ← Group and aggregate
└──────┬──────┘
       ↓
┌─────────────┐
│   $sort     │  ← Sort results
└──────┬──────┘
       ↓
┌─────────────┐
│   $limit    │  ← Limit output
└──────┬──────┘
       ↓
  Results (Output)
```

---

## Core Stages

### $match — Filter Documents

Works like `find()`. Should be placed as early as possible to reduce documents processed by later stages.

```javascript
db.orders.aggregate([
  { $match: { 
    status: "completed",
    orderDate: { $gte: new Date("2024-01-01") },
    total: { $gt: 100 }
  }}
])

// $match can use indexes (ONLY when it's the first stage or follows $sort)
```

### $project — Reshape Documents

Include, exclude, or compute new fields.

```javascript
db.users.aggregate([
  { $project: {
    fullName: { $concat: ["$firstName", " ", "$lastName"] },
    email: 1,
    yearJoined: { $year: "$createdAt" },
    _id: 0
  }}
])

// Computed fields:
{ $project: {
  totalPrice: { $multiply: ["$price", "$quantity"] },
  discountedPrice: { $subtract: ["$price", { $multiply: ["$price", 0.1] }] },
  uppercaseName: { $toUpper: "$name" },
  nameLength: { $strLenCP: "$name" }
}}
```

### $group — Group and Aggregate

The most powerful stage. Groups documents by a key and applies accumulator expressions.

```javascript
// Total sales by category
db.orders.aggregate([
  { $group: {
    _id: "$category",           // Group key (null for all docs)
    totalSales: { $sum: "$total" },
    avgOrder: { $avg: "$total" },
    maxOrder: { $max: "$total" },
    minOrder: { $min: "$total" },
    orderCount: { $sum: 1 },    // Count documents
    customers: { $addToSet: "$customerId" },  // Unique customers
    firstOrder: { $first: "$orderDate" },
    lastOrder: { $last: "$orderDate" }
  }}
])

// Group by multiple fields
{ $group: {
  _id: { category: "$category", year: { $year: "$orderDate" } },
  revenue: { $sum: "$total" }
}}

// Group ALL documents (no grouping key)
{ $group: {
  _id: null,
  totalRevenue: { $sum: "$total" },
  orderCount: { $sum: 1 }
}}
```

### Accumulator Operators
| Operator | Description |
|----------|-------------|
| `$sum` | Sum of values (or count with `$sum: 1`) |
| `$avg` | Average |
| `$min` | Minimum value |
| `$max` | Maximum value |
| `$first` | First value in group (order-dependent) |
| `$last` | Last value in group (order-dependent) |
| `$push` | Push values into array |
| `$addToSet` | Push unique values into array |
| `$count` | Count documents in group |
| `$stdDevPop` | Population standard deviation |
| `$stdDevSamp` | Sample standard deviation |

### $sort — Sort Documents

```javascript
db.orders.aggregate([
  { $match: { status: "completed" } },
  { $group: { _id: "$category", total: { $sum: "$amount" } } },
  { $sort: { total: -1 } }  // Sort by total descending
])

// Multiple sort fields
{ $sort: { category: 1, total: -1 } }

// $sort before $group can use indexes
// $sort after $group is always in-memory
```

### $limit and $skip

```javascript
// Pagination pattern
db.products.aggregate([
  { $match: { category: "electronics" } },
  { $sort: { price: -1 } },
  { $skip: 20 },   // Skip first 20
  { $limit: 10 }   // Take next 10
])
```

### $unwind — Deconstruct Arrays

Splits each document with an array into multiple documents (one per array element).

```javascript
// Input document:
{ name: "Vinay", skills: ["Java", "Spring", "MongoDB"] }

// After $unwind:
{ name: "Vinay", skills: "Java" }
{ name: "Vinay", skills: "Spring" }
{ name: "Vinay", skills: "MongoDB" }

// Usage:
db.users.aggregate([
  { $unwind: "$skills" },
  { $group: { _id: "$skills", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
])
// Result: Most popular skills across all users

// Preserve null/empty arrays
{ $unwind: { path: "$skills", preserveNullAndEmptyArrays: true } }
```

### $addFields / $set — Add New Fields

Adds new fields without removing existing ones (unlike $project).

```javascript
db.orders.aggregate([
  { $addFields: {
    totalWithTax: { $multiply: ["$total", 1.18] },
    isHighValue: { $gt: ["$total", 1000] },
    year: { $year: "$orderDate" }
  }}
])

// $set is an alias for $addFields (MongoDB 4.2+)
{ $set: { fullName: { $concat: ["$firstName", " ", "$lastName"] } } }
```

### $unset — Remove Fields

```javascript
db.users.aggregate([
  { $unset: ["password", "internalNotes", "__v"] }
])
```

### $count — Count Documents

```javascript
db.orders.aggregate([
  { $match: { status: "active" } },
  { $count: "activeOrders" }
])
// Result: { activeOrders: 1523 }
```

### $facet — Multiple Pipelines in Parallel

Run multiple aggregation pipelines on the same input documents.

```javascript
db.products.aggregate([
  { $match: { status: "active" } },
  { $facet: {
    // Pipeline 1: Paginated results
    "results": [
      { $sort: { price: -1 } },
      { $skip: 0 },
      { $limit: 10 }
    ],
    // Pipeline 2: Total count
    "totalCount": [
      { $count: "count" }
    ],
    // Pipeline 3: Price stats
    "priceStats": [
      { $group: {
        _id: null,
        avgPrice: { $avg: "$price" },
        maxPrice: { $max: "$price" },
        minPrice: { $min: "$price" }
      }}
    ],
    // Pipeline 4: Category breakdown
    "byCategory": [
      { $group: { _id: "$category", count: { $sum: 1 } } },
      { $sort: { count: -1 } }
    ]
  }}
])
```

---

## $lookup — Joining Collections

MongoDB's version of a JOIN operation.

```javascript
// Basic $lookup
db.orders.aggregate([
  { $lookup: {
    from: "customers",           // Foreign collection
    localField: "customerId",    // Field in orders
    foreignField: "_id",         // Field in customers
    as: "customerDetails"        // Output array field
  }},
  { $unwind: "$customerDetails" }  // Convert array to object (since 1:1)
])

// Result:
{
  orderId: "ORD-001",
  customerId: ObjectId("..."),
  total: 999,
  customerDetails: {           // Joined data
    name: "Vinay Kumar",
    email: "vinay@example.com"
  }
}
```

### Pipeline $lookup (More Powerful)
```javascript
db.orders.aggregate([
  { $lookup: {
    from: "products",
    let: { orderItems: "$items" },  // Variables from the parent doc
    pipeline: [                      // Sub-pipeline on foreign collection
      { $match: {
        $expr: { $in: ["$_id", "$$orderItems.productId"] }
      }},
      { $project: { name: 1, price: 1, category: 1 } }
    ],
    as: "productDetails"
  }}
])
```

### $lookup Considerations
- Creates an ARRAY in the output (even for 1:1 relationships)
- Use `$unwind` after `$lookup` for 1:1 joins
- Performance: `$lookup` is expensive. Consider if embedding would be better
- Index the `foreignField` for performance
- Not available in sharded collections as the "from" collection (pre-5.1)

---

## Practical Aggregation Examples

### Monthly Revenue Report
```javascript
db.orders.aggregate([
  { $match: { 
    status: "completed",
    orderDate: { $gte: new Date("2024-01-01") }
  }},
  { $group: {
    _id: { 
      year: { $year: "$orderDate" },
      month: { $month: "$orderDate" }
    },
    revenue: { $sum: "$total" },
    orderCount: { $sum: 1 },
    avgOrderValue: { $avg: "$total" },
    uniqueCustomers: { $addToSet: "$customerId" }
  }},
  { $addFields: {
    customerCount: { $size: "$uniqueCustomers" }
  }},
  { $project: { uniqueCustomers: 0 } },
  { $sort: { "_id.year": 1, "_id.month": 1 } }
])
```

### Top Customers by Spending
```javascript
db.orders.aggregate([
  { $match: { status: "completed" } },
  { $group: {
    _id: "$customerId",
    totalSpent: { $sum: "$total" },
    orderCount: { $sum: 1 },
    avgOrder: { $avg: "$total" },
    lastOrder: { $max: "$orderDate" }
  }},
  { $sort: { totalSpent: -1 } },
  { $limit: 10 },
  { $lookup: {
    from: "customers",
    localField: "_id",
    foreignField: "_id",
    as: "customer"
  }},
  { $unwind: "$customer" },
  { $project: {
    customerName: "$customer.name",
    email: "$customer.email",
    totalSpent: 1,
    orderCount: 1,
    avgOrder: { $round: ["$avgOrder", 2] },
    lastOrder: 1
  }}
])
```

### Product Sales with Category Breakdown
```javascript
db.orders.aggregate([
  { $unwind: "$items" },
  { $group: {
    _id: "$items.productId",
    totalQuantity: { $sum: "$items.quantity" },
    totalRevenue: { $sum: { $multiply: ["$items.price", "$items.quantity"] } },
    orderCount: { $sum: 1 }
  }},
  { $lookup: {
    from: "products",
    localField: "_id",
    foreignField: "_id",
    as: "product"
  }},
  { $unwind: "$product" },
  { $group: {
    _id: "$product.category",
    products: { $push: {
      name: "$product.name",
      revenue: "$totalRevenue",
      quantity: "$totalQuantity"
    }},
    categoryRevenue: { $sum: "$totalRevenue" },
    categoryQuantity: { $sum: "$totalQuantity" }
  }},
  { $sort: { categoryRevenue: -1 } }
])
```

---

## Pipeline Optimization Tips

### 1. $match Early
```javascript
// ✅ GOOD: Filter early, process less data
[
  { $match: { status: "active" } },      // Reduces dataset first
  { $group: { _id: "$category", ... } }
]

// ❌ BAD: Process everything, then filter
[
  { $group: { _id: "$category", ... } },
  { $match: { count: { $gt: 10 } } }     // Could move some filtering earlier
]
```

### 2. $project Early to Reduce Document Size
```javascript
// ✅ Only carry needed fields through pipeline
[
  { $match: { status: "active" } },
  { $project: { customerId: 1, total: 1, orderDate: 1 } },  // Drop unnecessary fields
  { $group: { ... } }
]
```

### 3. Use Indexes
```javascript
// $match and $sort at the beginning of pipeline can use indexes
// After $group or $unwind, indexes can't be used
```

### 4. Avoid $unwind When Possible
```javascript
// If you just need array size, use $size instead of $unwind + $group
{ $addFields: { skillCount: { $size: "$skills" } } }
```

---

## Interview Questions

**Q: How is the aggregation pipeline different from MapReduce?**
A: The aggregation pipeline is the recommended approach. It's faster (native C++ implementation), more readable (declarative stages), and supports more operations. MapReduce is deprecated since MongoDB 5.0.

**Q: Can aggregation pipelines use indexes?**
A: Yes, but only for `$match` and `$sort` stages at the beginning of the pipeline (before any stage that modifies the document stream like $group, $unwind, $project). Place $match first to leverage indexes.

**Q: When would you use $facet?**
A: When you need multiple aggregations on the same dataset in one query — e.g., paginated results + total count + category breakdown. Without $facet, you'd need multiple queries.

**Q: What's the memory limit for aggregation?**
A: Each pipeline stage has a 100 MB memory limit. Use `allowDiskUse: true` to spill to disk for large datasets. But this is a sign you should optimize your pipeline or data model.

**Q: How do you paginate with aggregation?**
A: Use `$sort` → `$skip` → `$limit`. For better performance on large datasets, use cursor-based pagination with `$match: { _id: { $gt: lastId } }` instead of `$skip`.

**Q: What's the difference between $project and $addFields?**
A: `$project` explicitly includes/excludes fields (non-specified fields are dropped). `$addFields` adds new fields while preserving ALL existing fields. Use `$addFields` when you want to add computed fields without losing other data.
