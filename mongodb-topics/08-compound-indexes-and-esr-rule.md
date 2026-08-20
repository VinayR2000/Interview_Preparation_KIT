# Compound Indexes and ESR Rule ⭐⭐⭐

## Compound Index Fundamentals

A compound index is an index on multiple fields. The order of fields in the index definition is critical.

```javascript
db.orders.createIndex({ status: 1, createdAt: -1 })
```

This creates a B-tree sorted first by `status` (ascending), then by `createdAt` (descending) within each status value.

```
Index Structure (conceptual):
├── status: "active"
│   ├── createdAt: 2024-06-15
│   ├── createdAt: 2024-06-14
│   ├── createdAt: 2024-06-13
│   └── ...
├── status: "completed"
│   ├── createdAt: 2024-06-15
│   ├── createdAt: 2024-06-12
│   └── ...
├── status: "pending"
│   ├── createdAt: 2024-06-15
│   └── ...
└── status: "shipped"
    ├── createdAt: 2024-06-14
    └── ...
```

---

## Index Prefix Rule

A compound index can support queries on any **left prefix** of its fields.

```javascript
// Index: { a: 1, b: 1, c: 1, d: 1 }

// Supported queries (uses index):
{ a: value }                    // ✅ prefix [a]
{ a: value, b: value }          // ✅ prefix [a, b]
{ a: value, b: value, c: value } // ✅ prefix [a, b, c]
{ a: v, b: v, c: v, d: v }     // ✅ full index

// NOT supported (cannot use index):
{ b: value }                    // ❌ skips 'a'
{ c: value }                    // ❌ skips 'a' and 'b'
{ b: value, c: value }          // ❌ skips 'a'
{ a: value, c: value }          // ⚠️ only uses prefix [a], ignores 'c'
```

---

## The ESR Rule ⭐⭐⭐

The **ESR Rule** (Equality → Sort → Range) is the most important principle for designing compound indexes.

```
E - Equality fields FIRST
S - Sort fields SECOND
R - Range fields LAST

┌─────────────────────────────────────────────────┐
│  Index Field Order:                              │
│                                                  │
│  { equality1, equality2, sort1, range1, range2 } │
│    ════════════════  ════════  ════════════════   │
│         E              S            R            │
└─────────────────────────────────────────────────┘
```

### Why This Order?

**Equality (E)**: Narrows the search space immediately. After equality matches, MongoDB works with a much smaller set of index entries.

**Sort (S)**: When placed after equality fields, the remaining entries are already sorted, avoiding an in-memory sort.

**Range (R)**: Range conditions ($gt, $lt, $gte, $lte, $ne, $in with ranges) must scan a portion of the index. Placing them last means this scan happens on the smallest possible subset.

---

## ESR Examples

### Example 1: Order Query
```javascript
// Query: Find active orders for customer C1, sorted by date, after Jan 2024
db.orders.find({
  customerId: "C1",          // Equality
  status: "active",          // Equality
  orderDate: { $gt: date }   // Range
}).sort({ orderDate: -1 })   // Sort

// ✅ OPTIMAL index (ESR):
db.orders.createIndex({ customerId: 1, status: 1, orderDate: -1 })
//                       ═══════E═══════════════  ═══S + R══════

// Here orderDate serves BOTH sort and range since sort direction matches
```

### Example 2: Product Search
```javascript
// Query: Electronics products between $100-$500, sorted by rating
db.products.find({
  category: "electronics",       // Equality
  price: { $gte: 100, $lte: 500 } // Range
}).sort({ rating: -1 })          // Sort

// ✅ OPTIMAL index (ESR):
db.products.createIndex({ category: 1, rating: -1, price: 1 })
//                         ═══════E═══  ════S════  ═══R═══
```

### Example 3: User Activity
```javascript
// Query: Active users in Bangalore, sorted by lastLogin, age > 18
db.users.find({
  status: "active",              // Equality
  city: "Bangalore",             // Equality
  age: { $gte: 18 }             // Range
}).sort({ lastLogin: -1 })       // Sort

// ✅ OPTIMAL index (ESR):
db.users.createIndex({ status: 1, city: 1, lastLogin: -1, age: 1 })
//                      ═══════════E═══════  ════S════════  ══R══
```

---

## When ESR Doesn't Apply Perfectly

### Sort and Range on Same Field
```javascript
// Query: Orders after date X, sorted by date
db.orders.find({
  customerId: "C1",
  orderDate: { $gt: new Date("2024-01-01") }
}).sort({ orderDate: -1 })

// Index: { customerId: 1, orderDate: -1 }
// orderDate handles BOTH sort and range
// This is ideal — no conflict between S and R
```

### Multiple Range Conditions
```javascript
// Query: Price between 100-500 AND rating > 4
db.products.find({
  category: "electronics",
  price: { $gte: 100, $lte: 500 },
  rating: { $gte: 4.0 }
})

// Only ONE range field can efficiently use the index
// Choose the more selective one to come first
// If most products are $100-500 but few have rating ≥ 4:
db.products.createIndex({ category: 1, rating: 1, price: 1 })
```

### No Equality Fields
```javascript
// Query: Sort by date, filter by date range
db.events.find({
  timestamp: { $gte: start, $lte: end }
}).sort({ timestamp: -1 })

// Index: { timestamp: -1 }
// Sort and range on same field — works perfectly
```

---

## Sort Direction in Compound Indexes

For compound indexes, sort direction matters when sorting on multiple fields.

```javascript
// Index: { a: 1, b: -1 }

// ✅ Supported sorts:
.sort({ a: 1, b: -1 })   // Matches index exactly
.sort({ a: -1, b: 1 })   // Exact inverse (traverse backward)

// ❌ NOT supported (needs in-memory sort):
.sort({ a: 1, b: 1 })    // Mixed — doesn't match either direction
.sort({ a: -1, b: -1 })  // Mixed — doesn't match either direction
```

### Rule
- A compound index can support a sort if the sort pattern matches the index OR its exact inverse.
- "Exact inverse" means ALL fields have flipped directions.

---

## Practical Index Design Process

### Step 1: Identify your query
```javascript
db.orders.find({
  customerId: "C1",
  status: { $in: ["active", "pending"] },
  total: { $gt: 100 }
}).sort({ createdAt: -1 }).limit(20)
```

### Step 2: Classify each field
```
customerId: "C1"           → Equality
status: { $in: [...] }    → Equality (small $in acts like equality)
total: { $gt: 100 }       → Range
sort: { createdAt: -1 }   → Sort
```

### Step 3: Apply ESR
```javascript
db.orders.createIndex({ customerId: 1, status: 1, createdAt: -1, total: 1 })
//                       ═══════════E═══════════  ═════S═════════  ══R══
```

### Step 4: Verify with explain()
```javascript
db.orders.find({...}).sort({...}).explain("executionStats")
// Check: stage is IXSCAN, not COLLSCAN
// Check: nReturned close to totalDocsExamined
// Check: no in-memory sort stage
```

---

## Common Compound Index Mistakes

### Mistake 1: Range Before Sort
```javascript
// ❌ BAD: Range before sort
db.orders.createIndex({ customerId: 1, total: 1, createdAt: -1 })

// Query uses range on total → remaining entries NOT sorted by createdAt
// MongoDB must do in-memory sort
```

### Mistake 2: Too Many Indexes Covering Similar Queries
```javascript
// ❌ Redundant indexes:
db.orders.createIndex({ customerId: 1 })
db.orders.createIndex({ customerId: 1, status: 1 })
db.orders.createIndex({ customerId: 1, status: 1, createdAt: -1 })

// The third index covers all queries the first two support!
// Drop the first two:
db.orders.createIndex({ customerId: 1, status: 1, createdAt: -1 })  // ✅ Covers all
```

### Mistake 3: Ignoring the Prefix Rule
```javascript
// Index: { customerId: 1, status: 1, createdAt: -1 }

// This query CANNOT use the index:
db.orders.find({ status: "active" }).sort({ createdAt: -1 })
// It skips the leftmost field (customerId)

// You'd need a separate index for this query pattern
```

---

## Index Intersection

MongoDB can sometimes combine multiple single-field indexes, but this is generally LESS efficient than a proper compound index.

```javascript
// Two single-field indexes:
db.orders.createIndex({ customerId: 1 })
db.orders.createIndex({ status: 1 })

// Query:
db.orders.find({ customerId: "C1", status: "active" })
// MongoDB MAY use index intersection, but a compound index is better:
db.orders.createIndex({ customerId: 1, status: 1 })
```

**Rule of thumb**: Don't rely on index intersection. Design proper compound indexes for your query patterns.

---

## Interview Questions

**Q: Explain the ESR rule and why it matters.**
A: ESR stands for Equality-Sort-Range. In compound indexes, equality fields should come first (they narrow the search space immediately), sort fields second (so remaining entries are already in order, avoiding in-memory sort), and range fields last (they scan a subset of the already-narrowed entries). This order produces the most efficient query execution.

**Q: Given this query, design the optimal index:**
```javascript
db.products.find({ brand: "Apple", price: { $lt: 1000 } }).sort({ rating: -1 })
```
A: Apply ESR:
- Equality: `brand`
- Sort: `rating`  
- Range: `price`

Optimal index: `{ brand: 1, rating: -1, price: 1 }`

**Q: Why can't `{ a: 1, b: 1, c: 1 }` support a query on `{ b: value, c: value }`?**
A: Because of the index prefix rule. The index is sorted by `a` first. Without a condition on `a`, MongoDB can't efficiently locate entries for specific `b` and `c` values — it would need to scan the entire index.

**Q: How many compound indexes should a collection have?**
A: As few as possible while supporting your query patterns. Each index adds write overhead and memory usage. Often 3-5 well-designed compound indexes can cover all query patterns. Use explain() to verify each index is actually used.

**Q: What happens if you put range before sort in a compound index?**
A: After the range scan, the remaining entries are NOT guaranteed to be in sort order. MongoDB must collect all matching entries and perform an in-memory sort (SORT stage in explain), which is slower and has a 100 MB memory limit for sorts without allowDiskUse.

**Q: Does `$in` count as equality or range for ESR?**
A: Small `$in` (2-3 values) acts like equality. Large `$in` (many values) behaves more like range because MongoDB must scan multiple branches of the B-tree. For ESR purposes, treat `$in` as equality for small sets.
