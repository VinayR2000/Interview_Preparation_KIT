# 7. Collection Internals — Deep Dive

---

## Theory

Understanding the internal workings of Java collections is what separates senior developers from juniors. This section focuses on **how** collections work, not just how to use them.

### ArrayList Internals

```java
public class ArrayList<E> {
    transient Object[] elementData;  // internal array
    private int size;                // actual element count
    
    private static final int DEFAULT_CAPACITY = 10;
    
    // Growth formula:
    // newCapacity = oldCapacity + (oldCapacity >> 1)  → 1.5x growth
    // 10 → 15 → 22 → 33 → 49 → 73 → 109 → ...
}
```

**Key internals:**
- Initial capacity: 0 (empty array until first add) → then 10
- Growth factor: 50% (1.5x)
- Uses `System.arraycopy()` for shifts (native method, very fast)
- `trimToSize()` releases unused capacity
- Implements `RandomAccess` marker interface → signals O(1) index access

### LinkedList Internals

```java
public class LinkedList<E> {
    transient Node<E> first;
    transient Node<E> last;
    transient int size;
    
    private static class Node<E> {
        E item;
        Node<E> next;
        Node<E> prev;
        
        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }
}
```

**Key internals:**
- Doubly-linked list (each node has prev and next)
- No initial capacity or growth — allocates per node
- Each node = 3 references (item, prev, next) + object header ≈ 40+ bytes overhead
- Implements both `List` and `Deque`

### HashMap Internals ⭐⭐⭐

This is THE most important collection internal to understand.

```java
public class HashMap<K,V> {
    transient Node<K,V>[] table;        // bucket array
    transient int size;                  // number of entries
    int threshold;                       // capacity * loadFactor
    final float loadFactor;             // default 0.75
    
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int TREEIFY_THRESHOLD = 8;     // linked list → tree
    static final int UNTREEIFY_THRESHOLD = 6;   // tree → linked list
    static final int MIN_TREEIFY_CAPACITY = 64; // min capacity for treeification
    
    static class Node<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next;
    }
    
    static final class TreeNode<K,V> extends Node<K,V> {
        TreeNode<K,V> parent;
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        boolean red;
    }
}
```

### HashMap put() Process

```
1. Calculate hash:
   hash = key.hashCode() ^ (key.hashCode() >>> 16)  // spread high bits
   
2. Find bucket:
   index = hash & (capacity - 1)  // bitwise AND (faster than modulo)
   
3. Insert:
   if bucket is empty → create new Node
   if bucket has entries:
     a. Compare hash AND key using equals()
     b. If match → update value (returns old value)
     c. If no match → add to end of linked list
     d. If linked list length > 8 AND table capacity ≥ 64 → convert to Red-Black Tree
     
4. Check threshold:
   if (size > threshold) resize()  // double capacity
```

### HashMap Resize (Rehashing)

```
Old capacity: 16, New capacity: 32

For each entry:
  newIndex = hash & (newCapacity - 1)
  
Key insight: entries either stay at same index OR move to (oldIndex + oldCapacity)
  Because new bit in mask is either 0 (stay) or 1 (move)
  
Example: hash = 17
  Old: 17 & 15 (0x0F) = 1   → bucket[1]
  New: 17 & 31 (0x1F) = 17  → bucket[17] (moved to index + 16)
```

### Why Capacity Must Be Power of 2

```
Capacity = 16 (binary: 10000)
Mask = capacity - 1 = 15 (binary: 01111)

index = hash & mask
  hash = 0b10110101
  mask = 0b00001111
  index= 0b00000101 = 5

This ensures uniform distribution across buckets.
If capacity were not power of 2, some bucket indices would never be reached.
```

### Treeification (Java 8+)

```
When a bucket has > 8 entries (TREEIFY_THRESHOLD):
  Linked List → Red-Black Tree (if table capacity ≥ 64)
  
Performance change:
  Linked List traversal: O(n) worst case
  Red-Black Tree search: O(log n) worst case

When bucket shrinks below 6 (UNTREEIFY_THRESHOLD):
  Red-Black Tree → Linked List (saves memory overhead)
```

### Load Factor

```
loadFactor = 0.75 (default)
threshold = capacity * loadFactor = 16 * 0.75 = 12

When size exceeds threshold (12 entries in 16 buckets):
  → resize to 32 buckets
  → new threshold = 32 * 0.75 = 24

Higher load factor (e.g., 0.9):
  + Less memory (fewer empty buckets)
  - More collisions (slower)

Lower load factor (e.g., 0.5):
  + Fewer collisions (faster)
  - More memory (more empty buckets)
```

---

## Internal Working

### HashMap hash() Function Deep Dive

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

Why XOR with upper 16 bits?
```
Problem: If table size is 16, only bottom 4 bits of hash determine bucket.
  hashCode = 0x12345678
  Without spread: index = 0x12345678 & 0xF = 0x8 (only uses last nibble!)
  
Solution: Mix high bits into low bits
  h = 0x12345678
  h >>> 16 = 0x00001234
  h ^ (h>>>16) = 0x12344444  // high bits influence low bits
  
This reduces collisions when keys have patterns in high bits.
```

### HashMap get() Internal Flow

```java
public V get(Object key) {
    Node<K,V> e;
    return (e = getNode(hash(key), key)) == null ? null : e.value;
}

final Node<K,V> getNode(int hash, Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
    
    // 1. Table exists and bucket not empty?
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & hash]) != null) {
        
        // 2. Check first node (fast path)
        if (first.hash == hash &&
            ((k = first.key) == key || (key != null && key.equals(k))))
            return first;
        
        // 3. Traverse bucket (linked list or tree)
        if ((e = first.next) != null) {
            if (first instanceof TreeNode)
                return ((TreeNode<K,V>)first).getTreeNode(hash, key);
            do {
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    return e;
            } while ((e = e.next) != null);
        }
    }
    return null;
}
```

### ConcurrentHashMap Internals (Java 8+)

```
Java 8 ConcurrentHashMap:
- Uses CAS (Compare-And-Swap) + synchronized on individual bucket heads
- No Segment locking (unlike Java 7)
- Fine-grained locking: only the affected bucket is locked during put/remove
- size() uses a baseCount + CounterCell array (similar to LongAdder)

Put operation:
1. If bucket empty → CAS to insert (no lock!)
2. If bucket occupied → synchronized(first node of bucket)
3. Then normal linked list/tree operations within the lock
```

---

## Diagram

```
HashMap Structure:
┌──────────────────────────────────────────────────────────────┐
│ Node<K,V>[] table (bucket array)                             │
├──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬─────┤
│  [0] │  [1] │  [2] │  [3] │  [4] │  [5] │  [6] │  [7] │... │
│ null │  ↓   │ null │  ↓   │ null │  ↓   │ null │ null │    │
└──────┴──┼───┴──────┴──┼───┴──────┴──┼───┴──────┴──────┴─────┘
          ↓              ↓              ↓
       [K1,V1]       [K3,V3]        [K5,V5]
          ↓              ↓              ↓
       [K2,V2]       [K4,V4]        [K6,V6]   ← Linked list
          ↓                             ↓
         null                       [K7,V7]
                                       ↓
                                    ...8+ nodes → Red-Black Tree
```

```
HashMap Resize:
Before (capacity=4):
[0]: A→B    [1]: C    [2]: D→E    [3]: null

After resize (capacity=8):
[0]: A      [1]: C    [2]: D    [3]: null
[4]: B      [5]: null [6]: E    [7]: null
         ↑                   ↑
    moved (oldIdx+4)    moved (oldIdx+4)
```

```
ArrayList Growth:
Capacity: 10  │████████░░│ size=8
  add()...
Capacity: 10  │██████████│ size=10 (FULL)
  add() → GROW!
Capacity: 15  │███████████░░░░│ size=11
  System.arraycopy() to new array
```

---

## Code

```java
// Demonstrating HashMap internals behavior

public class HashMapInternalsDemo {
    
    // Custom key to demonstrate hashing behavior
    static class BadKey {
        int id;
        
        BadKey(int id) { this.id = id; }
        
        // BAD: All objects have same hashCode → all go to one bucket → O(n)!
        @Override
        public int hashCode() { return 1; }
        
        @Override
        public boolean equals(Object o) {
            return o instanceof BadKey && ((BadKey) o).id == this.id;
        }
    }
    
    static class GoodKey {
        int id;
        String name;
        
        GoodKey(int id, String name) { this.id = id; this.name = name; }
        
        @Override
        public int hashCode() {
            return 31 * id + (name != null ? name.hashCode() : 0);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GoodKey gk)) return false;
            return id == gk.id && Objects.equals(name, gk.name);
        }
    }
    
    public static void main(String[] args) {
        // --- HashMap capacity and threshold ---
        HashMap<String, Integer> map = new HashMap<>();
        // table = null (lazy initialization)
        
        map.put("first", 1);
        // Now: table = Node[16], threshold = 12, size = 1
        
        // Add 12 entries → triggers resize at 13th
        for (int i = 0; i < 12; i++) {
            map.put("key" + i, i);
        }
        // size = 13 > threshold 12 → resize to 32, threshold = 24
        
        // --- Demonstrating collision resolution ---
        HashMap<BadKey, String> badMap = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            badMap.put(new BadKey(i), "value" + i);
        }
        // All 10 entries in ONE bucket (same hashCode=1)
        // Bucket becomes: linked list of 8 → then treeified (if capacity≥64)
        // get() is O(n) or O(log n) instead of O(1)!
        
        // --- Pre-sizing to avoid rehashing ---
        int expectedSize = 1000;
        // capacity needed = expectedSize / loadFactor + 1
        int capacity = (int) (expectedSize / 0.75f) + 1;
        HashMap<String, Object> preSized = new HashMap<>(capacity);
        
        // --- LinkedHashMap access-order for LRU cache ---
        LinkedHashMap<String, Integer> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return size() > 5; // max 5 entries
            }
        };
        lruCache.put("a", 1);
        lruCache.put("b", 2);
        lruCache.put("c", 3);
        lruCache.put("d", 4);
        lruCache.put("e", 5);
        lruCache.get("a");     // moves "a" to end (most recently accessed)
        lruCache.put("f", 6);  // triggers removeEldestEntry → removes "b" (least recent)
        System.out.println(lruCache); // {c=3, d=4, e=5, a=1, f=6}
        
        // --- ArrayList vs LinkedList benchmark ---
        benchmarkInsertionAt(0);       // LinkedList wins (no shift)
        benchmarkInsertionAt(50000);   // ArrayList wins (cache locality)
        benchmarkRandomAccess();       // ArrayList dominates
    }
    
    static void benchmarkRandomAccess() {
        int size = 100_000;
        List<Integer> arrayList = new ArrayList<>(size);
        List<Integer> linkedList = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }
        
        Random rand = new Random();
        
        // ArrayList: O(1) access → ~1ms for 10000 random gets
        long start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            arrayList.get(rand.nextInt(size));
        }
        long arrayTime = System.nanoTime() - start;
        
        // LinkedList: O(n) access → ~5000ms for 10000 random gets!
        start = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            linkedList.get(rand.nextInt(size));
        }
        long linkedTime = System.nanoTime() - start;
        
        System.out.printf("ArrayList: %dms, LinkedList: %dms%n", 
                arrayTime/1_000_000, linkedTime/1_000_000);
    }
}
```

---

## Dry Run

### HashMap put("Java", 100) — Complete Flow

```
HashMap<String, Integer> map = new HashMap<>();

Step 1: put("Java", 100)
  - table is null → initialize
  - table = new Node[16], threshold = 12

Step 2: hash("Java")
  - "Java".hashCode() = 2301506
  - h = 2301506
  - h ^ (h >>> 16) = 2301506 ^ 35 = 2301473
  
Step 3: bucket index
  - index = 2301473 & (16 - 1) = 2301473 & 15 = 1
  
Step 4: table[1] is null → create new Node
  - table[1] = Node(hash=2301473, key="Java", value=100, next=null)
  - size = 1

Now: put("Javb", 200) — let's say hash collision (same bucket)
  
Step 5: hash("Javb") → (assume same bucket index = 1)
Step 6: table[1] is NOT null (has "Java" node)
Step 7: Compare:
  - hash match? Check.
  - key.equals()? "Javb".equals("Java") → false
Step 8: Move to next: null → end of list
Step 9: Append: Node("Java").next = Node("Javb", 200)
  - size = 2

Later: get("Java")
Step 10: hash("Java") → index = 1
Step 11: table[1] = Node(key="Java")
Step 12: hash matches AND "Java".equals("Java") → true!
Step 13: Return value = 100
```

### HashMap Resize Visualization

```
Before resize (capacity=16, size=13 > threshold=12):
table[0]: null
table[1]: [K1,V1] → [K2,V2]
table[2]: null
...
table[5]: [K3,V3]
...

Resize triggered:
1. newTable = new Node[32]
2. For each entry:
   - Recompute: index = hash & (32-1) = hash & 31
   - Entry may stay at same index OR move to (index + 16)
   
3. Example: entry with hash = 17
   Old: 17 & 15 = 1  → was at index 1
   New: 17 & 31 = 17 → moves to index 17

4. threshold = 32 * 0.75 = 24
```

---

## Complexity

### HashMap Operations

| Operation | Average | Worst Case (all collisions) | Worst Case (treeified) |
|-----------|---------|---------------------------|----------------------|
| put() | O(1) | O(n) | O(log n) |
| get() | O(1) | O(n) | O(log n) |
| remove() | O(1) | O(n) | O(log n) |
| containsKey() | O(1) | O(n) | O(log n) |
| containsValue() | O(n) | O(n) | O(n) |
| resize() | O(n) | O(n) | O(n) |

### ArrayList Operations

| Operation | Complexity | Why |
|-----------|-----------|-----|
| get(i) | O(1) | Direct array index |
| add(e) (end) | O(1) amortized | May resize (rare) |
| add(i, e) (middle) | O(n) | Shift elements right |
| remove(i) | O(n) | Shift elements left |
| contains(e) | O(n) | Linear scan |
| set(i, e) | O(1) | Direct array index |

---

## Real Project Usage

```java
// High-performance cache with proper HashMap usage
public class InMemoryCache<K, V> {
    
    // Pre-sized HashMap to avoid rehashing
    private final Map<K, CacheEntry<V>> store;
    private final int maxSize;
    private final Duration ttl;
    
    public InMemoryCache(int maxSize, Duration ttl) {
        this.maxSize = maxSize;
        this.ttl = ttl;
        // Capacity = maxSize / 0.75 + 1 to avoid ANY rehashing
        int capacity = (int) (maxSize / 0.75f) + 1;
        this.store = new ConcurrentHashMap<>(capacity);
    }
    
    public V get(K key) {
        CacheEntry<V> entry = store.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }
    
    public void put(K key, V value) {
        if (store.size() >= maxSize) {
            evictExpired();
        }
        store.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
    }
    
    record CacheEntry<V>(V value, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }
}

// Using TreeMap for efficient range queries
public class PriceRangeService {
    
    // TreeMap allows range operations
    private final TreeMap<BigDecimal, List<Product>> productsByPrice = new TreeMap<>();
    
    public List<Product> getProductsInRange(BigDecimal min, BigDecimal max) {
        // O(log n) to find range, O(k) to collect results
        return productsByPrice.subMap(min, true, max, true)
                .values()
                .stream()
                .flatMap(List::stream)
                .toList();
    }
    
    public BigDecimal findCheapestAbove(BigDecimal threshold) {
        // O(log n) — find next key ≥ threshold
        Map.Entry<BigDecimal, List<Product>> entry = productsByPrice.ceilingEntry(threshold);
        return entry != null ? entry.getKey() : null;
    }
}
```

---

## Interview Questions and Answers

**Q1: How does HashMap work internally?**
> HashMap uses an array of buckets (Node[]). When putting a key-value pair: (1) computes hash of key using hashCode() XORed with upper bits, (2) finds bucket index via `hash & (capacity-1)`, (3) if bucket empty, creates new Node, (4) if occupied, traverses linked list using equals() to find duplicate or appends. If a bucket has >8 nodes and capacity ≥64, converts to Red-Black tree for O(log n) lookup. Resizes to double capacity when size > capacity * loadFactor.

**Q2: Why is HashMap capacity always a power of 2?**
> So that bucket index calculation can use bitwise AND (`hash & (capacity-1)`) instead of modulo (%). Bitwise AND is much faster. It also ensures even distribution — with power-of-2 capacity, all bucket indices are reachable. Non-power-of-2 would create unreachable indices.

**Q3: What happens during a HashMap collision?**
> When two different keys hash to the same bucket: (1) Java checks each existing node using hash comparison (fast int compare) then equals() (slower). (2) If no match, appends new node to the linked list. (3) If list length exceeds 8 and table size ≥ 64, the list converts to a Red-Black tree for O(log n) search.

**Q4: What is the load factor? What happens when HashMap resizes?**
> Load factor (default 0.75) is the ratio of entries to capacity that triggers resize. When size > capacity*0.75, HashMap doubles its capacity. During resize: (1) new array of double size is created, (2) every entry is rehashed to its new position (some stay, some move to old_index + old_capacity), (3) this is O(n) and expensive.

**Q5: Why should HashMap keys be immutable?**
> Because HashMap uses the key's hashCode to determine the bucket. If you mutate the key after insertion, its hashCode changes, and the entry is now in the "wrong" bucket. get() with the same mutated key will look in a different bucket and return null — the entry is effectively lost. String, Integer, and records make excellent keys.

**Q6: HashMap vs ConcurrentHashMap internal difference?**
> HashMap: no synchronization, not thread-safe. ConcurrentHashMap (Java 8+): uses CAS for empty bucket insertions and synchronized blocks on individual bucket heads — only the affected bucket is locked. Size tracking uses a distributed counter (like LongAdder). No null keys or values allowed (ambiguity between "not found" and "null value" in concurrent context).

---

## Follow-up Questions and Answers

**Q: What is treeification and when does it happen?**
> When a single bucket accumulates more than 8 nodes (TREEIFY_THRESHOLD), the linked list converts to a Red-Black tree — IF the table capacity is ≥ 64. If capacity < 64, it resizes instead (more buckets = fewer collisions). This prevents worst-case O(n) degradation from hash attacks. Untreeification happens when nodes drop below 6.

**Q: Why does ArrayList grow by 1.5x and not 2x?**
> The 1.5x growth factor balances memory waste vs resize frequency. With 2x growth, you waste up to 50% of capacity. With 1.5x, waste is up to 33%. The old array can also potentially be reused for the new allocation with 1.5x growth (an optimization some allocators support).

**Q: Why can't you use ConcurrentHashMap with null keys/values?**
> Because you can't distinguish between "key not found" (returns null) and "key exists with null value" in a concurrent context. With regular HashMap, you can use `containsKey()` after a null get, but in ConcurrentHashMap another thread might have changed the map between those two calls.

**Q: How does LinkedHashMap maintain order with HashMap inheritance?**
> LinkedHashMap extends HashMap and adds `before`/`after` pointers to each entry, forming a doubly-linked list across all entries. The `head` and `tail` maintain list boundaries. During iteration, it follows this linked list instead of traversing the bucket array.

---

## Common Mistakes

1. **Not pre-sizing HashMap when size is known**
   ```java
   // BAD: causes multiple expensive resize operations
   Map<String, Object> map = new HashMap<>(); // starts at 16, resizes at 12, 24, 48...
   for (int i = 0; i < 10000; i++) map.put(key, value);
   
   // GOOD: pre-allocate
   Map<String, Object> map = new HashMap<>((int)(10000 / 0.75) + 1);
   ```

2. **Mutable key in HashMap**
   ```java
   List<Integer> key = new ArrayList<>(List.of(1, 2, 3));
   map.put(key, "value");
   key.add(4); // hashCode changed!
   map.get(key); // null! Entry is lost
   ```

3. **Using LinkedList when ArrayList is appropriate**
   ```java
   // LinkedList: 40+ bytes per element (node overhead) + cache misses
   // ArrayList: 4-8 bytes per reference + contiguous memory
   // ArrayList wins in almost ALL real scenarios
   ```

4. **Ignoring initial capacity for known-size collections**
   ```java
   // 10 resizes for 10000 elements: 10→15→22→33→49→73→109→163→244→366→549→...
   new ArrayList<>(); // BAD
   new ArrayList<>(10000); // GOOD
   ```

---

## Best Practices

1. **Always specify initial capacity** when collection size is predictable.
2. **Use immutable keys** for HashMap (String, Integer, records).
3. **Understand your data access patterns** before choosing a collection.
4. **Profile with JMH** for performance-critical collection choices.
5. **Use specialized collections** for primitives (Eclipse Collections, fastutil) in performance-critical paths.
6. **Implement good hashCode()** — use all significant fields, distribute uniformly.
7. **Consider capacity + load factor** together when sizing HashMaps.
8. **Use `computeIfAbsent()`** instead of get-then-put patterns.

---

## Production Considerations

- **HashMap memory overhead**: Each entry ≈ 32 bytes (Node object: 16 header + 4 hash + 4 key ref + 4 value ref + 4 next ref). For 1M entries ≈ 32MB just for the HashMap structure.

- **HashMap vs open-addressing**: Java's HashMap uses chaining (linked list/tree). Open-addressing maps (Eclipse Collections HashMap, Koloboke) have better cache locality and less memory overhead.

- **Monitoring**: Track collection sizes in production. Unexpected growth → memory leak. Use heap dumps to identify oversized collections.

- **GC Impact**: Large ArrayLists/HashMaps in old generation survive Full GC → long pauses. Consider off-heap storage or segmented caches.

- **Resize storms**: Multiple large HashMaps resizing simultaneously → sudden memory spike → OOM. Pre-size or stagger growth.

---

## Related Topics

- → [3. Object Class](./03-object-class.md) (equals/hashCode contract)
- → [6. Collections Framework](./06-collections-framework.md)
- → [8. Generics](./08-generics.md)
- → [19. Concurrent Collections](./19-concurrent-collections.md)
- → [20. JVM Internals](./20-jvm-internals.md) (memory layout)
- → [33. Java Performance](./33-java-performance.md)
