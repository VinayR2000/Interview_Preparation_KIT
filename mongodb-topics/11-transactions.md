# MongoDB Transactions

## Single-Document Atomicity

MongoDB guarantees atomicity at the **single-document level** by default. All operations on a single document (including embedded documents and arrays) are atomic.

```javascript
// This is ATOMIC — no transaction needed
db.accounts.updateOne(
  { _id: "account1" },
  {
    $inc: { balance: -100 },
    $push: { transactions: { type: "debit", amount: 100, date: new Date() } },
    $set: { updatedAt: new Date() }
  }
)
// Either ALL fields are updated, or NONE are. No partial state possible.
```

### Why This Matters
- Good data modeling (embedding) can often eliminate the need for multi-document transactions
- If you design your documents to contain all related data, single-document atomicity is sufficient
- This is a core advantage of MongoDB's document model

---

## When You Need Multi-Document Transactions

Multi-document transactions are needed when:
1. You must update multiple documents atomically
2. You must update documents in different collections atomically
3. You can't redesign the schema to embed the related data

### Classic Example: Money Transfer
```javascript
// Account A: Debit $100
// Account B: Credit $100
// BOTH must succeed or BOTH must fail
```

---

## Multi-Document Transactions (MongoDB 4.0+)

### Basic Transaction Pattern
```javascript
const session = client.startSession();

try {
  session.startTransaction({
    readConcern: { level: "snapshot" },
    writeConcern: { w: "majority" },
    readPreference: "primary"
  });

  // Operation 1: Debit account A
  db.accounts.updateOne(
    { _id: "accountA", balance: { $gte: 100 } },
    { $inc: { balance: -100 } },
    { session }
  );

  // Operation 2: Credit account B
  db.accounts.updateOne(
    { _id: "accountB" },
    { $inc: { balance: 100 } },
    { session }
  );

  // Operation 3: Record transfer
  db.transfers.insertOne(
    { from: "accountA", to: "accountB", amount: 100, date: new Date() },
    { session }
  );

  // Commit
  await session.commitTransaction();
  
} catch (error) {
  // Abort on any error
  await session.abortTransaction();
  throw error;
  
} finally {
  session.endSession();
}
```

### Transaction Flow
```
startSession()
       ↓
startTransaction()
       ↓
┌─────────────────────────┐
│  Operation 1 (with session) │
│  Operation 2 (with session) │
│  Operation 3 (with session) │
└─────────────────────────┘
       ↓
   ┌───┴───┐
   │Success│ → commitTransaction()
   │       │
   │Failure│ → abortTransaction()
   └───────┘
       ↓
endSession()
```

---

## Read Concern

Controls the consistency and isolation properties of data read from replica sets.

| Level | Description | Use Case |
|-------|-------------|----------|
| `local` | Returns most recent data on the node (default) | Fast reads, eventual consistency OK |
| `available` | Like local, but for sharded clusters | Fastest reads in sharded environment |
| `majority` | Returns data acknowledged by majority of nodes | Strong consistency reads |
| `snapshot` | Returns data from a snapshot in time | Transactions (point-in-time consistency) |
| `linearizable` | Reflects all successful majority writes | Strongest consistency (slowest) |

```javascript
// Read concern in a query
db.orders.find({ status: "active" }).readConcern("majority")

// In a transaction (set at transaction level)
session.startTransaction({ readConcern: { level: "snapshot" } })
```

---

## Write Concern

Controls the acknowledgment level for write operations.

| Level | Description | Durability |
|-------|-------------|------------|
| `w: 0` | No acknowledgment (fire and forget) | None |
| `w: 1` | Acknowledged by primary only (default) | Minimal |
| `w: "majority"` | Acknowledged by majority of replica set | Strong |
| `w: <number>` | Acknowledged by N members | Custom |
| `j: true` | Written to journal on disk | Durable after crash |
| `wtimeout` | Max time to wait for write concern | Prevents indefinite blocking |

```javascript
// Write concern in operations
db.orders.insertOne(
  { orderId: "ORD-001", total: 999 },
  { writeConcern: { w: "majority", j: true, wtimeout: 5000 } }
)

// In transactions
session.startTransaction({
  writeConcern: { w: "majority" }
})
```

### Write Concern Trade-offs
```
w: 0       → Fastest, no durability guarantee
w: 1       → Fast, primary acknowledges (default)
w: majority → Slower, survives primary failure
w: majority + j: true → Slowest, survives any single node failure + crash
```

---

## Read Preference

Controls which replica set members receive read operations.

| Mode | Description | Use Case |
|------|-------------|----------|
| `primary` | Always read from primary (default) | Strong consistency |
| `primaryPreferred` | Primary if available, else secondary | Failover tolerance |
| `secondary` | Read from secondary only | Offload reads from primary |
| `secondaryPreferred` | Secondary if available, else primary | Read scaling |
| `nearest` | Read from lowest latency member | Geo-distributed apps |

```javascript
// Set read preference
db.analytics.find({}).readPref("secondaryPreferred")

// In connection string
mongodb://host1,host2,host3/mydb?readPreference=secondaryPreferred
```

### ⚠️ Stale Reads
Reading from secondaries may return slightly stale data (replication lag). Use `primary` or `majority` read concern when you need the latest data.

---

## SQL Transactions vs MongoDB Transactions

| Aspect | SQL | MongoDB |
|--------|-----|---------|
| Default scope | Multi-table | Single-document |
| Isolation | Row-level locks | Document-level (WiredTiger) |
| Transactions needed for | Almost everything | Only cross-document operations |
| Performance impact | Moderate (well-optimized) | Higher overhead (distributed protocol) |
| Duration limit | Varies | 60 seconds default |
| Best practice | Use freely | Use sparingly, model to avoid |

---

## Transaction Limitations and Best Practices

### Limitations
- **60-second time limit** (default) — transactions that exceed this are automatically aborted
- **16 MB oplog entry limit** — transactions generating large oplog entries may fail
- **Cannot create collections** inside a transaction
- **Cannot create indexes** inside a transaction
- **Performance overhead** — multi-doc transactions are slower than single-doc operations
- Requires **replica set** (standalone mongod doesn't support multi-doc transactions)

### Best Practices

#### 1. Model to Avoid Transactions
```javascript
// ❌ BAD: Separate collections requiring transaction
// orders collection + orderItems collection + inventory collection
// → Need transaction for every order placement

// ✅ GOOD: Embed to get single-document atomicity
{
  orderId: "ORD-001",
  items: [
    { productId: "P1", name: "Laptop", qty: 1, price: 999 }
  ],
  total: 999,
  status: "placed"
}
// Order creation is atomic without a transaction
```

#### 2. Keep Transactions Short
```javascript
// ❌ BAD: Long transaction with external API call
session.startTransaction();
const result = await externalPaymentAPI.charge(amount); // Could take seconds!
db.orders.updateOne({ ... }, { ... }, { session });
session.commitTransaction();

// ✅ GOOD: External call outside transaction
const result = await externalPaymentAPI.charge(amount);
if (result.success) {
  session.startTransaction();
  db.orders.updateOne({ ... }, { ... }, { session });
  db.accounts.updateOne({ ... }, { ... }, { session });
  session.commitTransaction();
}
```

#### 3. Retry on Transient Errors
```javascript
async function runTransactionWithRetry(session, txnFunc) {
  while (true) {
    try {
      await txnFunc(session);
      break;
    } catch (error) {
      if (error.hasErrorLabel("TransientTransactionError")) {
        console.log("Transient error, retrying...");
        continue;
      }
      throw error;
    }
  }
}

async function commitWithRetry(session) {
  while (true) {
    try {
      await session.commitTransaction();
      break;
    } catch (error) {
      if (error.hasErrorLabel("UnknownTransactionCommitResult")) {
        console.log("Commit result unknown, retrying...");
        continue;
      }
      throw error;
    }
  }
}
```

#### 4. Don't Use Transactions for Everything
```javascript
// ❌ UNNECESSARY: Single document update wrapped in transaction
session.startTransaction();
db.users.updateOne({ _id: userId }, { $set: { name: "New Name" } }, { session });
session.commitTransaction();
// This is already atomic without a transaction!

// ✅ NECESSARY: Cross-collection atomicity
session.startTransaction();
db.accounts.updateOne({ _id: "A" }, { $inc: { balance: -100 } }, { session });
db.accounts.updateOne({ _id: "B" }, { $inc: { balance: 100 } }, { session });
session.commitTransaction();
```

---

## Transaction in Spring Boot (Preview)

```java
@Transactional
public void transferMoney(String fromId, String toId, double amount) {
    Account from = accountRepository.findById(fromId)
        .orElseThrow(() -> new RuntimeException("Account not found"));
    
    if (from.getBalance() < amount) {
        throw new InsufficientFundsException("Insufficient balance");
    }
    
    accountRepository.debit(fromId, amount);
    accountRepository.credit(toId, amount);
    
    Transfer transfer = new Transfer(fromId, toId, amount, LocalDateTime.now());
    transferRepository.save(transfer);
}
// Spring's @Transactional with MongoTransactionManager handles session management
```

---

## Interview Questions

**Q: Does MongoDB support ACID transactions?**
A: Yes. Single-document operations are always ACID. Multi-document ACID transactions are supported since MongoDB 4.0 (replica sets) and 4.2 (sharded clusters). However, the recommendation is to design your schema to minimize the need for multi-document transactions.

**Q: When should you use multi-document transactions?**
A: Only when you MUST atomically modify multiple documents across collections and cannot redesign your schema to use embedding. Common cases: financial transfers, inventory + order management, any cross-collection consistency requirement.

**Q: What happens if a transaction exceeds 60 seconds?**
A: MongoDB automatically aborts it. This is intentional — long transactions hold locks and affect cluster performance. Keep transactions short. If you need longer operations, reconsider your design.

**Q: How do read concern and write concern relate to transactions?**
A: They control consistency guarantees. Inside transactions, `readConcern: "snapshot"` provides point-in-time consistency, and `writeConcern: "majority"` ensures committed data survives primary failure. For most transactional use cases, use snapshot + majority.

**Q: Can you use transactions on a standalone MongoDB instance?**
A: Multi-document transactions require a replica set (or sharded cluster). They don't work on standalone mongod. Even for development, you should use a single-node replica set.

**Q: What's the difference between "TransientTransactionError" and "UnknownTransactionCommitResult"?**
A: TransientTransactionError means the transaction didn't complete (safe to retry the whole transaction). UnknownTransactionCommitResult means the commit might have succeeded but the client didn't get confirmation (safe to retry the commit only — the operations won't duplicate).
