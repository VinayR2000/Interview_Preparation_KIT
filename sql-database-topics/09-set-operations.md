# Topic 9: Set Operations

## Theory

Set operations combine results from two or more SELECT statements into a single result set.

### Operations

| Operation | Description | Duplicates |
|-----------|-------------|-----------|
| UNION | Combine + remove duplicates | Removed |
| UNION ALL | Combine + keep duplicates | Kept |
| INTERSECT | Only rows in BOTH queries | Removed |
| EXCEPT | Rows in first but NOT second | Removed |
| MINUS | Same as EXCEPT (Oracle syntax) | Removed |

### Rules
1. Both queries must have the SAME number of columns
2. Corresponding columns must have compatible data types
3. Column names come from the FIRST query
4. ORDER BY applies to the FINAL combined result

```
UNION:          A ∪ B (distinct)
UNION ALL:      A + B (all rows)
INTERSECT:      A ∩ B
EXCEPT:         A - B (in A but not in B)
```

---

## Internal Working

```
UNION (removes duplicates):
┌──────────────────────────────────────────────┐
│ 1. Execute Query A → Result Set A            │
│ 2. Execute Query B → Result Set B            │
│ 3. Concatenate A + B                          │
│ 4. Sort or Hash to find/remove duplicates    │
│ Time: O(n + m) + O((n+m) log(n+m)) for sort │
└──────────────────────────────────────────────┘

UNION ALL (keeps duplicates):
┌──────────────────────────────────────────────┐
│ 1. Execute Query A → Result Set A            │
│ 2. Execute Query B → Result Set B            │
│ 3. Concatenate A + B (no dedup)             │
│ Time: O(n + m)  ← Much faster!              │
└──────────────────────────────────────────────┘

INTERSECT:
┌──────────────────────────────────────────────┐
│ 1. Execute both queries                      │
│ 2. Hash smaller set                          │
│ 3. Probe with larger set                     │
│ 4. Return matches                            │
└──────────────────────────────────────────────┘

EXCEPT:
┌──────────────────────────────────────────────┐
│ 1. Execute both queries                      │
│ 2. Hash set B                                │
│ 3. For each row in A, check if in B         │
│ 4. Return rows from A NOT in B              │
└──────────────────────────────────────────────┘
```

---

## Code Examples

```sql
-- UNION: Active customers + VIP customers (no duplicates)
SELECT id, name, email FROM customers WHERE is_active = TRUE
UNION
SELECT id, name, email FROM customers WHERE is_vip = TRUE;

-- UNION ALL: All audit events (faster, duplicates ok)
SELECT user_id, action, created_at FROM login_logs
UNION ALL
SELECT user_id, action, created_at FROM activity_logs;

-- INTERSECT: Customers who are BOTH active AND VIP
SELECT customer_id FROM active_customers
INTERSECT
SELECT customer_id FROM vip_customers;

-- EXCEPT: Active customers who are NOT VIP
SELECT customer_id FROM active_customers
EXCEPT
SELECT customer_id FROM vip_customers;

-- Practical: Find products sold in January but NOT in February
SELECT product_id FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE EXTRACT(MONTH FROM o.created_at) = 1
EXCEPT
SELECT product_id FROM order_items oi
JOIN orders o ON oi.order_id = o.id
WHERE EXTRACT(MONTH FROM o.created_at) = 2;

-- Combined search across multiple tables
SELECT id, name, 'customer' AS type FROM customers WHERE name ILIKE '%john%'
UNION ALL
SELECT id, name, 'supplier' AS type FROM suppliers WHERE name ILIKE '%john%'
UNION ALL
SELECT id, name, 'employee' AS type FROM employees WHERE name ILIKE '%john%'
ORDER BY name
LIMIT 20;
```

---

## Dry Run

```sql
-- Query A: SELECT id FROM table_a = {1, 2, 3, 3, 4}
-- Query B: SELECT id FROM table_b = {3, 4, 5, 5, 6}

-- UNION: {1, 2, 3, 4, 5, 6}  ← Unique values from both
-- UNION ALL: {1, 2, 3, 3, 4, 3, 4, 5, 5, 6}  ← All rows concatenated
-- INTERSECT: {3, 4}  ← In both
-- EXCEPT (A - B): {1, 2}  ← In A but not B
```

---

## Complexity

| Operation | Time | Space |
|-----------|------|-------|
| UNION ALL | O(n + m) | O(1) streaming |
| UNION | O((n+m) log(n+m)) | O(n + m) for dedup |
| INTERSECT | O(n + m) with hash | O(min(n,m)) |
| EXCEPT | O(n + m) with hash | O(m) for hash of B |

---

## Interview Questions & Answers

**Q1: What's the difference between UNION and UNION ALL?**
UNION removes duplicates (requires sort/hash — slower). UNION ALL keeps all rows (just concatenates — faster). Always use UNION ALL unless you specifically need deduplication.

**Q2: Can you use ORDER BY with UNION?**
Only one ORDER BY at the end, which applies to the entire combined result. You can't ORDER BY within individual SELECT statements.

**Q3: What's the difference between EXCEPT and NOT IN?**
- EXCEPT: Set operation, compares entire rows, handles NULLs correctly
- NOT IN: Row filter, compares single values, fails with NULLs

---

## Common Mistakes

1. **Using UNION when UNION ALL is sufficient** — unnecessary performance cost
2. **Different column counts** between the queries
3. **Trying to ORDER BY in individual queries** — only works on final result
4. **Forgetting EXCEPT is directional** — A EXCEPT B ≠ B EXCEPT A

---

## Best Practices

1. **Prefer UNION ALL** unless you specifically need deduplication
2. **Use EXCEPT over NOT IN** — handles NULLs correctly
3. **Add type discriminator column** when combining different entity types
4. **Use CTEs** for complex set operations for readability

---

## Related Topics
- [Topic 7: Joins](#)
- [Topic 8: Subqueries](#)
- [Topic 11: CTE](#)
