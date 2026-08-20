# Query Optimization

## The explain() Method

The single most important tool for query performance analysis.

```javascript
// Three verbosity levels:
db.orders.find({ customerId: "C1" }).explain()                    // "queryPlanner" (default)
db.orders.find({ customerId: "C1" }).explain("executionStats")    // Most useful
db.orders.find({ customerId: "C1" }).explain("allPlansExecution") // All candidate plans
```

---

## Understanding explain() Output

### Key Fields in executionStats

```javascript
db.orders.find({ customerId: "C1", status: "active" })
  .sort({ createdAt: -1 })
  .explain("executionStats")
```

```json
{
  "executionStats": {
    "executionSuccess": true,
    "nReturned": 25,              // Documents returned to client
    "executionTimeMillis": 3,     // Total execution time
    "totalKeysExamined": 25,      // Index entries examined
    "totalDocsExamined": 25,      // Documents examined from collection
    "executionStages": {
      "stage": "FETCH",           // Final stage
      "nReturned": 25,
      "inputStage": {
        "stage": "IXSCAN",        // Index scan stage
        "nReturned": 25,
        "keyPattern": { "customerId": 1, "status": 1, "createdAt": -1 },
        "indexName": "customerId_1_status_1_createdAt_-1",
        "direction": "forward"
      }
    }
  }
}
```

### Critical Metrics

| Metric | Ideal | Problem |
|--------|-------|---------|
| `nReturned` vs `totalDocsExamined` | Equal (ratio = 1) | Examining many docs, returning few |
| `totalKeysExamined` vs `nReturned` | Equal or close | Scanning too many index entries |
| `executionTimeMillis` | < 100ms | Slow query |
| Stage | IXSCAN | COLLSCAN = no index used |

### Performance Ratios
```
Efficiency Ratio = nReturned / totalDocsExamined

1.0     → Perfect (every examined doc was returned)
0.5     → OK (examining 2x what's returned)
0.01    → Terrible (examining 100x what's returned)

Index Efficiency = nReturned / totalKeysExamined

1.0     → Perfect (every key led to a returned doc)
< 0.1   → Index might not be selective enough
```

---

## Query Execution Stages

### COLLSCAN — Collection Scan ❌
```
COLLSCAN
    ↓
Every document read from disk
    ↓
Filter applied to each document
    ↓
Matching documents returned
```
**Meaning**: No suitable index found. Full collection scan.
**Fix**: Create an appropriate index.

### IXSCAN — Index Scan ✅
```
IXSCAN
    ↓
Navigate B-tree to find matching keys
    ↓
Only matching key pointers followed
    ↓
FETCH (read full documents)
    ↓
Return documents
```
**Meaning**: Using an index efficiently.

### FETCH — Document Fetch
```
IXSCAN → FETCH
```
**Meaning**: After index scan, MongoDB reads the actual documents to get fields not in the index.

### SORT — In-Memory Sort ⚠️
```
IXSCAN → FETCH → SORT
```
**Meaning**: Index couldn't provide sorted results. MongoDB sorts in memory.
**Problem**: 
- Limited to 100 MB by default (fails with error if exceeded)
- Slower than index-based sort
**Fix**: Redesign index using ESR rule to cover the sort.

### SORT_KEY_GENERATOR
Generates sort keys for in-memory sort.

### PROJECTION
Applies field projection to returned documents.

### LIMIT / SKIP
Applies limit/skip after results are gathered.

---

## Winning Plan

MongoDB's query planner evaluates multiple candidate plans and picks the winner.

```javascript
db.orders.find({ customerId: "C1", status: "active" }).explain("allPlansExecution")

// Shows:
// - winningPlan: The plan MongoDB chose
// - rejectedPlans: Alternative plans that were not chosen
```

### How MongoDB Chooses
1. Identifies all candidate indexes
2. Runs each plan concurrently for a trial period
3. Plan that returns batch size (101 docs or 32 MB) first wins
4. Winner is cached in plan cache (until collection changes significantly)

---

## Real-World Optimization Examples

### Example 1: Missing Index
```javascript
// BEFORE: No index on customerId
db.orders.find({ customerId: "C1" }).explain("executionStats")
// Stage: COLLSCAN
// totalDocsExamined: 5,000,000
// executionTimeMillis: 4200

// AFTER: Add index
db.orders.createIndex({ customerId: 1 })
// Stage: IXSCAN → FETCH
// totalDocsExamined: 47
// executionTimeMillis: 2
```

### Example 2: In-Memory Sort
```javascript
// BEFORE: Index { customerId: 1 }
db.orders.find({ customerId: "C1" }).sort({ createdAt: -1 }).explain("executionStats")
// Stages: IXSCAN → FETCH → SORT (in-memory)
// Customer has 10,000 orders → sorting 10,000 docs in memory

// AFTER: Compound index
db.orders.createIndex({ customerId: 1, createdAt: -1 })
// Stages: IXSCAN → FETCH (no SORT stage!)
// Index provides sorted order directly
```

### Example 3: Examining Too Many Documents
```javascript
// Index: { status: 1, createdAt: -1 }
// 90% of orders are "completed"
db.orders.find({ status: "completed" }).sort({ createdAt: -1 }).limit(10)
  .explain("executionStats")
// totalKeysExamined: 10
// totalDocsExamined: 10
// nReturned: 10
// ✅ Good! Limit + sort direction lets MongoDB stop after 10 matches

// But this is bad for the same index:
db.orders.find({ status: "active", total: { $gt: 1000 } })
  .sort({ createdAt: -1 })
// totalKeysExamined: 50,000 (scans all "active" entries looking for total > 1000)
// totalDocsExamined: 50,000
// nReturned: 230
// ❌ Bad ratio! Need better index: { status: 1, total: 1, createdAt: -1 }
```

---

## Common Performance Issues and Fixes

### Issue: COLLSCAN on Large Collection
```javascript
// Symptom: executionTimeMillis > 1000, stage: COLLSCAN
// Fix: Create index matching the query pattern
```

### Issue: In-Memory Sort (SORT Stage)
```javascript
// Symptom: SORT stage in execution plan, slow for large result sets
// Fix: Redesign index to include sort field in correct position (ESR rule)
```

### Issue: High docsExamined vs nReturned
```javascript
// Symptom: totalDocsExamined: 100,000, nReturned: 50
// Fix: Index is not selective enough. Add more fields to compound index or use partial index
```

### Issue: $or with Only One Branch Indexed
```javascript
// Symptom: One branch uses IXSCAN, other uses COLLSCAN
// Fix: Ensure ALL branches of $or have supporting indexes
db.users.find({ $or: [{ email: "..." }, { phone: "..." }] })
// Needs BOTH: index on email AND index on phone
```

### Issue: Regex Without Anchor
```javascript
// BAD: Non-anchored regex → full index scan
db.users.find({ name: { $regex: /vinay/ } })

// GOOD: Anchored regex → efficient index range scan
db.users.find({ name: { $regex: /^vinay/ } })
```

### Issue: Negation Operators ($ne, $nin, $not)
```javascript
// These operators are generally NOT selective and scan large portions of the index
db.users.find({ status: { $ne: "deleted" } })
// If 99% of users are not deleted, this scans 99% of the index

// Better: Query for what you WANT, not what you don't want
db.users.find({ status: { $in: ["active", "pending", "inactive"] } })
```

---

## Query Optimization Checklist

```
□ Run explain("executionStats") on the query
□ Check stage is IXSCAN (not COLLSCAN)
□ Check no SORT stage (index covers sort)
□ Check nReturned ≈ totalDocsExamined (good selectivity)
□ Check nReturned ≈ totalKeysExamined (efficient index use)
□ Check executionTimeMillis is acceptable
□ Verify using correct compound index (ESR rule)
□ Check if covered query is possible (no FETCH stage)
□ Consider if projection reduces data transfer
□ Check if limit() reduces work (with supporting index)
```

---

## Profiler

For finding slow queries in production:

```javascript
// Enable profiler (level 2 = all queries, level 1 = slow queries only)
db.setProfilingLevel(1, { slowms: 100 })  // Log queries > 100ms

// Check profiler output
db.system.profile.find().sort({ ts: -1 }).limit(5)

// Fields in profile:
{
  op: "query",
  ns: "mydb.orders",
  millis: 450,
  planSummary: "COLLSCAN",
  keysExamined: 0,
  docsExamined: 1000000,
  nreturned: 3,
  query: { customerId: "C1" }
}

// Disable profiler
db.setProfilingLevel(0)
```

---

## allowDiskUse

For sorts that exceed 100 MB memory limit:

```javascript
// Aggregation
db.orders.aggregate([
  { $match: { status: "active" } },
  { $sort: { total: -1 } }
], { allowDiskUse: true })

// find() in MongoDB 4.4+
db.orders.find({ status: "active" })
  .sort({ total: -1 })
  .allowDiskUse()
```

**⚠️ Warning**: Using allowDiskUse is a band-aid. If you need it, your index strategy likely needs improvement.

---

## Interview Questions

**Q: You have a slow query. Walk me through how you'd diagnose and fix it.**
A: 
1. Run `explain("executionStats")` to see the execution plan
2. Check if COLLSCAN → need an index
3. Check if SORT stage → index doesn't cover sort order, redesign with ESR
4. Check docsExamined vs nReturned ratio → poor selectivity, need better compound index
5. Check keysExamined vs nReturned → index scanning too many entries
6. Design optimal index using ESR rule
7. Create index and verify improvement with explain() again

**Q: What's a covered query and why is it the fastest?**
A: A covered query returns results entirely from the index without accessing the actual documents (no FETCH stage). All queried and projected fields must be in the index. It's fastest because indexes are smaller and more likely cached in RAM than full documents.

**Q: What's the 100 MB sort limit?**
A: MongoDB limits in-memory sorts to 100 MB. If a sort operation exceeds this, the query fails with an error. Solutions: (1) design an index that covers the sort, (2) use `allowDiskUse()` as a temporary fix, (3) reduce the result set with more selective filters before sorting.

**Q: How does MongoDB choose between multiple candidate indexes?**
A: The query planner runs all candidate plans concurrently for a trial period. The plan that returns the first batch (101 docs or 32 MB) fastest wins. The winning plan is cached until the collection changes significantly (new indexes, 1000+ writes, etc.).

**Q: What's the impact of $or on index usage?**
A: Each branch of `$or` is evaluated independently. Each branch needs its own supporting index. MongoDB performs an index scan for each branch and merges results. If ANY branch lacks an index, it may fall back to COLLSCAN for the entire query.
