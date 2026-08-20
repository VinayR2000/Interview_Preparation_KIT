# Topic 4: Sorting & Pagination

## Theory

### ORDER BY

Controls the sequence in which rows are returned. Without ORDER BY, SQL provides **no guarantee** of row order.

```sql
ORDER BY column1 [ASC|DESC] [NULLS FIRST|NULLS LAST], column2 [ASC|DESC]
```

- **ASC** (default): smallest first (A→Z, 1→9, oldest→newest)
- **DESC**: largest first (Z→A, 9→1, newest→oldest)
- **NULLS FIRST**: NULL values appear first
- **NULLS LAST**: NULL values appear last (default for ASC)

### Pagination Approaches

| Method | Mechanism | Best For |
|--------|-----------|----------|
| **OFFSET/LIMIT** | Skip N rows, return M | Simple UIs, small datasets |
| **Keyset (Cursor)** | Use last seen value as boundary | Large datasets, infinite scroll |
| **FETCH FIRST** | SQL standard syntax | Standards compliance |
| **TOP** | SQL Server specific | SQL Server |

### OFFSET Pagination vs Keyset Pagination

```
OFFSET Pagination:
┌──────────────────────────────────────────────┐
│ Page 1: LIMIT 10 OFFSET 0    → Scan 10 rows │
│ Page 2: LIMIT 10 OFFSET 10   → Scan 20 rows │
│ Page 3: LIMIT 10 OFFSET 20   → Scan 30 rows │
│ Page 100: LIMIT 10 OFFSET 990→ Scan 1000 rows│  ← SLOW!
└──────────────────────────────────────────────┘

Keyset Pagination:
┌──────────────────────────────────────────────────────────┐
│ Page 1: WHERE id > 0 ORDER BY id LIMIT 10    → Fast     │
│ Page 2: WHERE id > 10 ORDER BY id LIMIT 10   → Fast     │
│ Page 3: WHERE id > 20 ORDER BY id LIMIT 10   → Fast     │
│ Page 100: WHERE id > 990 ORDER BY id LIMIT 10→ Fast!    │
└──────────────────────────────────────────────────────────┘
```

---

## Internal Working

### How ORDER BY Works

```
┌─────────────────────────────────────────────────┐
│           ORDER BY Execution                     │
├─────────────────────────────────────────────────┤
│                                                   │
│  Case 1: Index available on sort column          │
│  ┌─────────────────────────────────────────┐     │
│  │ Use index to read rows in sorted order   │    │
│  │ No extra sorting needed                  │    │
│  │ Cost: O(n) — sequential index traversal  │    │
│  └─────────────────────────────────────────┘     │
│                                                   │
│  Case 2: No index, small result set              │
│  ┌─────────────────────────────────────────┐     │
│  │ Quicksort in memory (work_mem)          │     │
│  │ Cost: O(n log n)                        │     │
│  └─────────────────────────────────────────┘     │
│                                                   │
│  Case 3: No index, large result set              │
│  ┌─────────────────────────────────────────┐     │
│  │ External merge sort (spills to disk)    │     │
│  │ Cost: O(n log n) + disk I/O             │     │
│  │ Very slow!                              │     │
│  └─────────────────────────────────────────┘     │
│                                                   │
└─────────────────────────────────────────────────┘
```

### How OFFSET Works (The Problem)

```
Query: SELECT * FROM orders ORDER BY id LIMIT 10 OFFSET 10000;

Execution Plan:
1. Scan table/index to find sorted rows
2. Read rows 1 through 10010
3. DISCARD rows 1 through 10000  ← Wasted work!
4. Return rows 10001 through 10010

The database MUST read and discard all offset rows.
This gets progressively slower as OFFSET grows.
```

### How Keyset Pagination Works

```
Query: SELECT * FROM orders WHERE id > 10000 ORDER BY id LIMIT 10;

Execution Plan:
1. Use index to seek directly to id > 10000  ← Jump!
2. Read next 10 rows
3. Return immediately

No rows are read and discarded. Constant time regardless of page number.
```

---

## Code Examples

### ORDER BY

```sql
-- Basic sorting
SELECT * FROM employees ORDER BY salary DESC;

-- Multiple sort columns
SELECT * FROM employees
ORDER BY department ASC, salary DESC;
-- First by department alphabetically, then highest salary first within each dept

-- Sort by expression
SELECT name, salary, salary * 12 AS annual
FROM employees
ORDER BY salary * 12 DESC;
-- Or use alias (works in ORDER BY)
ORDER BY annual DESC;

-- Sort by column position (not recommended)
SELECT first_name, last_name, salary
FROM employees
ORDER BY 3 DESC;  -- Sort by 3rd column (salary)

-- NULL handling
SELECT name, bonus FROM employees
ORDER BY bonus DESC NULLS LAST;  -- NULLs at the end

-- Custom sort order with CASE
SELECT * FROM tickets
ORDER BY
    CASE severity
        WHEN 'critical' THEN 1
        WHEN 'high' THEN 2
        WHEN 'medium' THEN 3
        WHEN 'low' THEN 4
    END,
    created_at ASC;

-- Conditional sort direction
SELECT * FROM products
ORDER BY
    CASE WHEN :sort_direction = 'asc' THEN price END ASC,
    CASE WHEN :sort_direction = 'desc' THEN price END DESC;
```

### LIMIT and OFFSET

```sql
-- Basic pagination
SELECT * FROM products
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;   -- Page 1

SELECT * FROM products
ORDER BY created_at DESC
LIMIT 20 OFFSET 20;  -- Page 2

SELECT * FROM products
ORDER BY created_at DESC
LIMIT 20 OFFSET 40;  -- Page 3

-- Formula: OFFSET = (page_number - 1) * page_size

-- Get total count for pagination metadata
SELECT COUNT(*) FROM products WHERE is_active = TRUE;
```

### FETCH (SQL Standard)

```sql
-- SQL:2008 standard syntax
SELECT * FROM products
ORDER BY price DESC
FETCH FIRST 10 ROWS ONLY;

-- With offset
SELECT * FROM products
ORDER BY price DESC
OFFSET 20 ROWS
FETCH NEXT 10 ROWS ONLY;

-- FETCH with ties (include rows with same value as last)
SELECT * FROM products
ORDER BY price DESC
FETCH FIRST 10 ROWS WITH TIES;
-- If 10th row has price=50, includes ALL rows with price=50
```

### Keyset (Cursor) Pagination

```sql
-- First page (no cursor)
SELECT id, name, price, created_at
FROM products
WHERE is_active = TRUE
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Next page (cursor = last row's values)
-- Last row had: created_at = '2024-03-15 10:30:00', id = 450
SELECT id, name, price, created_at
FROM products
WHERE is_active = TRUE
  AND (created_at, id) < ('2024-03-15 10:30:00', 450)
ORDER BY created_at DESC, id DESC
LIMIT 20;

-- Alternative syntax (works in all databases)
SELECT id, name, price, created_at
FROM products
WHERE is_active = TRUE
  AND (created_at < '2024-03-15 10:30:00'
       OR (created_at = '2024-03-15 10:30:00' AND id < 450))
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

### Keyset Pagination with Non-Unique Sort Columns

```sql
-- Problem: Sorting by a non-unique column (e.g., price)
-- Solution: Always add a unique tiebreaker (id)

-- First page
SELECT id, name, price
FROM products
ORDER BY price ASC, id ASC
LIMIT 10;
-- Last row: price = 29.99, id = 47

-- Next page
SELECT id, name, price
FROM products
WHERE (price, id) > (29.99, 47)
ORDER BY price ASC, id ASC
LIMIT 10;
```

---

## Dry Run

### OFFSET Pagination Issue

```sql
-- Table: products (1,000,000 rows)
-- Query: Get page 5000 (10 items per page)

SELECT * FROM products
ORDER BY id
LIMIT 10 OFFSET 49990;

-- Step 1: Sequential/Index scan → read 50,000 rows
-- Step 2: Sort if no index (O(n log n))
-- Step 3: Skip first 49,990 rows
-- Step 4: Return rows 49,991 to 50,000
-- Time: ~200ms (gets worse with higher offsets)

-- KEYSET alternative:
SELECT * FROM products
WHERE id > 49990
ORDER BY id
LIMIT 10;

-- Step 1: Index seek to id > 49990 → O(log n)
-- Step 2: Read next 10 rows → O(1)
-- Time: ~2ms (constant regardless of page)
```

### Race Condition with OFFSET

```sql
-- Page 1 at time T1:
SELECT * FROM news ORDER BY published_at DESC LIMIT 5 OFFSET 0;
-- Returns: [A, B, C, D, E]

-- New article X inserted between T1 and T2

-- Page 2 at time T2:
SELECT * FROM news ORDER BY published_at DESC LIMIT 5 OFFSET 5;
-- Returns: [E, F, G, H, I]
-- ↑ E appears on BOTH pages! (X pushed everything down)

-- Keyset pagination avoids this:
-- Page 2: WHERE published_at < E.published_at
-- Returns: [F, G, H, I, J]  -- No duplicates!
```

---

## Complexity

| Operation | Complexity |
|-----------|-----------|
| ORDER BY (no index) | O(n log n) |
| ORDER BY (with index) | O(n) — just traverse |
| LIMIT without ORDER BY | O(k) where k = limit |
| LIMIT with ORDER BY (no index) | O(n log n) + O(k) |
| LIMIT with ORDER BY (index) | O(k) |
| OFFSET k | O(k) — must skip k rows |
| Keyset pagination | O(log n) + O(k) |

---

## Real Project Usage

### Spring Boot Pagination Implementation

```java
// Using Spring Data JPA — Offset pagination
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    Page<Product> findByIsActiveTrue(Pageable pageable);
    
    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :min AND :max")
    Page<Product> findByPriceRange(
        @Param("min") BigDecimal min,
        @Param("max") BigDecimal max,
        Pageable pageable
    );
}

// Controller
@GetMapping("/products")
public ResponseEntity<Page<ProductDTO>> getProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String direction) {
    
    Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
    Pageable pageable = PageRequest.of(page, size, sort);
    
    Page<Product> products = productRepository.findByIsActiveTrue(pageable);
    return ResponseEntity.ok(products.map(ProductDTO::from));
}
```

### Keyset Pagination in Spring Boot (Custom)

```java
// Native query for keyset pagination
@Query(value = """
    SELECT * FROM products 
    WHERE is_active = true
    AND (created_at, id) < (:lastCreatedAt, :lastId)
    ORDER BY created_at DESC, id DESC
    LIMIT :limit
    """, nativeQuery = true)
List<Product> findNextPage(
    @Param("lastCreatedAt") Timestamp lastCreatedAt,
    @Param("lastId") Long lastId,
    @Param("limit") int limit
);

// Controller with cursor
@GetMapping("/products/stream")
public ResponseEntity<CursorPage<ProductDTO>> getProducts(
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int size) {
    
    CursorPage<ProductDTO> page;
    if (cursor == null) {
        page = productService.getFirstPage(size);
    } else {
        Cursor decoded = Cursor.decode(cursor);
        page = productService.getNextPage(decoded, size);
    }
    return ResponseEntity.ok(page);
}
```

### Cursor Response DTO

```java
public class CursorPage<T> {
    private List<T> items;
    private String nextCursor;  // Base64 encoded (lastCreatedAt + lastId)
    private boolean hasMore;
    
    public static String encodeCursor(Instant createdAt, Long id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }
    
    public static Cursor decodeCursor(String cursor) {
        String raw = new String(Base64.getDecoder().decode(cursor));
        String[] parts = raw.split("\\|");
        return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
    }
}
```

---

## Interview Questions & Answers

**Q1: Why is OFFSET pagination slow for large offsets?**

The database must read and discard all rows before the offset. For `OFFSET 1000000`, it reads 1 million rows then throws them away. The cost is O(offset + limit), not O(limit).

**Q2: When would you use OFFSET vs Keyset pagination?**

| Use OFFSET when | Use Keyset when |
|-----------------|-----------------|
| "Jump to page 50" required | Sequential page navigation |
| Small total dataset | Large dataset (millions) |
| Total page count needed | Infinite scroll / "Load More" |
| Admin tools | User-facing APIs |
| Simple implementation | Performance critical |

**Q3: What are the drawbacks of Keyset pagination?**

1. Cannot jump to arbitrary page (must traverse sequentially)
2. Cannot easily compute total page count
3. More complex implementation
4. Sort column must be indexed and unique (or include tiebreaker)
5. Changing sort direction mid-navigation is complex

**Q4: What's the difference between LIMIT and FETCH FIRST?**

| LIMIT | FETCH FIRST |
|-------|-------------|
| PostgreSQL/MySQL extension | SQL:2008 standard |
| Simpler syntax | More verbose |
| No "WITH TIES" support | Supports WITH TIES |
| Widely used | More portable |

**Q5: How do you handle pagination with changing data?**

Three approaches:
1. **Keyset pagination**: Naturally handles inserts/deletes (no shifted pages)
2. **Snapshot isolation**: Use a transaction snapshot for consistent pages
3. **Timestamp-based**: `WHERE created_at <= :query_start_time` to freeze the dataset

---

## Follow-up Questions & Answers

**Q: Can ORDER BY use an expression not in SELECT?**
Yes. `SELECT name FROM emp ORDER BY salary DESC;` is valid.

**Q: Does LIMIT without ORDER BY give random results?**
Not truly random, but unpredictable. Results depend on physical storage order and plan chosen.

**Q: How does WITH TIES work?**
```sql
SELECT * FROM products ORDER BY price FETCH FIRST 3 ROWS WITH TIES;
-- If 3rd row has price=50 and 4th row also has price=50,
-- both are included (returns 4+ rows)
```

---

## Common Mistakes

1. **Using OFFSET for deep pagination** — gets exponentially slower
2. **Pagination without ORDER BY** — results are non-deterministic, items may shift between pages
3. **Non-unique sort column without tiebreaker** — rows can appear on multiple pages or be skipped
4. **Counting total rows on every page request** — cache the count or compute async
5. **BETWEEN with timestamps** — loses last-second data of the day

---

## Best Practices

1. **Always use ORDER BY with pagination** — otherwise results are non-deterministic
2. **Always include a unique tiebreaker column** (usually `id`) in ORDER BY
3. **Use keyset pagination for large datasets** — O(log n) vs O(n)
4. **Index sort columns** — without index, ORDER BY requires full sort
5. **Limit maximum page size** — prevent clients from requesting millions of rows
6. **Use covering indexes** — include all selected columns to avoid table lookups
7. **Cache total counts** — don't compute COUNT(*) on every page request

```sql
-- Optimal index for pagination query
CREATE INDEX idx_products_pagination 
ON products (created_at DESC, id DESC) 
WHERE is_active = TRUE
INCLUDE (name, price);  -- Covering index
```

---

## Production Considerations

1. **Set maximum page size**: Never allow unbounded queries
   ```java
   int pageSize = Math.min(requestedSize, MAX_PAGE_SIZE); // MAX = 100
   ```

2. **Index design for sorting**: Create composite indexes matching your ORDER BY
   ```sql
   -- For: ORDER BY created_at DESC, id DESC
   CREATE INDEX idx_created_id ON products (created_at DESC, id DESC);
   ```

3. **Avoid COUNT(*) on large tables**: Use estimates for UI "total results"
   ```sql
   -- Fast estimate from statistics
   SELECT reltuples::BIGINT FROM pg_class WHERE relname = 'products';
   ```

4. **Connection pool impact**: Long pagination sessions hold connections
   - Use short transactions per page
   - Don't cursor-based pagination without pooler support

5. **API design**: Return pagination metadata
   ```json
   {
     "data": [...],
     "pagination": {
       "nextCursor": "abc123",
       "hasMore": true,
       "totalEstimate": 150000
     }
   }
   ```

---

## Related Topics
- [Topic 3: SELECT Fundamentals](#)
- [Topic 18: Indexes](#)
- [Topic 19: Query Optimization](#)
