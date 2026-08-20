# 6. Collections Framework — 🔴 Highest Priority

---

## Theory

The Java Collections Framework provides a unified architecture for storing, manipulating, and retrieving groups of objects. It's the most frequently tested topic in interviews.

### Core Hierarchy

```
Iterable
└── Collection
    ├── List (ordered, allows duplicates)
    │   ├── ArrayList
    │   ├── LinkedList
    │   ├── Vector (legacy, synchronized)
    │   └── CopyOnWriteArrayList (concurrent)
    │
    ├── Set (no duplicates)
    │   ├── HashSet
    │   ├── LinkedHashSet (insertion order)
    │   └── TreeSet (sorted)
    │
    └── Queue
        ├── PriorityQueue
        ├── ArrayDeque
        └── BlockingQueue (interface)
            ├── LinkedBlockingQueue
            ├── ArrayBlockingQueue
            └── PriorityBlockingQueue

Map (separate hierarchy — NOT Collection)
├── HashMap
├── LinkedHashMap (insertion order)
├── TreeMap (sorted by key)
├── Hashtable (legacy, synchronized)
├── ConcurrentHashMap (concurrent)
└── WeakHashMap (weak key references)
```

### List — Ordered, Indexed, Duplicates Allowed

| Implementation | Underlying Structure | Random Access | Insert/Delete | Thread-Safe |
|---------------|---------------------|---------------|---------------|-------------|
| **ArrayList** | Dynamic array | O(1) | O(n) | No |
| **LinkedList** | Doubly-linked list | O(n) | O(1)* | No |
| **Vector** | Dynamic array | O(1) | O(n) | Yes (synchronized) |
| **CopyOnWriteArrayList** | Copy-on-write array | O(1) | O(n) | Yes |

*LinkedList O(1) insert/delete only if you already have the node reference.

```java
List<String> arrayList = new ArrayList<>();    // default choice
List<String> linkedList = new LinkedList<>();   // rare in practice
List<String> cowList = new CopyOnWriteArrayList<>(); // read-heavy concurrent
```

### Set — No Duplicates

| Implementation | Order | Null | Performance |
|---------------|-------|------|-------------|
| **HashSet** | No order | 1 null | O(1) add/remove/contains |
| **LinkedHashSet** | Insertion order | 1 null | O(1) + ordering overhead |
| **TreeSet** | Sorted (natural/comparator) | No null | O(log n) |

```java
Set<String> hashSet = new HashSet<>();        // fastest, no order
Set<String> linkedSet = new LinkedHashSet<>(); // maintains insertion order
Set<String> treeSet = new TreeSet<>();        // sorted
```

### Map — Key-Value Pairs

| Implementation | Order | Null Key | Null Values | Thread-Safe |
|---------------|-------|----------|-------------|-------------|
| **HashMap** | No order | 1 null key | Multiple null | No |
| **LinkedHashMap** | Insertion/Access order | 1 null key | Multiple null | No |
| **TreeMap** | Sorted by key | No null key | Multiple null | No |
| **Hashtable** | No order | No null | No null | Yes (legacy) |
| **ConcurrentHashMap** | No order | No null | No null | Yes |
| **WeakHashMap** | No order | 1 null key | Multiple null | No |

```java
Map<String, Integer> hashMap = new HashMap<>();           // default choice
Map<String, Integer> linkedMap = new LinkedHashMap<>();    // order preserved
Map<String, Integer> treeMap = new TreeMap<>();           // sorted keys
Map<String, Integer> concMap = new ConcurrentHashMap<>();  // thread-safe
```

### Queue — FIFO (First In, First Out)

| Implementation | Order | Bounded | Use Case |
|---------------|-------|---------|----------|
| **PriorityQueue** | Priority (min-heap) | No | Task scheduling |
| **ArrayDeque** | FIFO/LIFO (stack + queue) | No | General purpose |
| **LinkedBlockingQueue** | FIFO | Optional | Producer-consumer |
| **ArrayBlockingQueue** | FIFO | Yes | Bounded buffer |

```java
Queue<Integer> pq = new PriorityQueue<>();    // min-heap
Deque<Integer> deque = new ArrayDeque<>();     // both stack and queue
Queue<Integer> bq = new LinkedBlockingQueue<>(); // blocking (concurrent)
```

### When to Use What

```
Need random access by index? → ArrayList
Need frequent insert/delete in middle? → LinkedList (rare in practice — ArrayList usually still wins)
Need unique elements? → HashSet
Need unique + ordered? → LinkedHashSet
Need unique + sorted? → TreeSet
Need key-value? → HashMap
Need key-value + ordered? → LinkedHashMap
Need key-value + sorted? → TreeMap
Need thread-safe map? → ConcurrentHashMap
Need FIFO queue? → ArrayDeque
Need priority ordering? → PriorityQueue
Need thread-safe queue? → BlockingQueue implementations
Need stack (LIFO)? → ArrayDeque (not Stack class)
```

---

## Internal Working

### ArrayList Internal Structure
```
Object[] elementData;  // internal array
int size;              // actual number of elements

Initial capacity: 10 (when first element added)
Growth: newCapacity = oldCapacity + (oldCapacity >> 1)  // 1.5x
  10 → 15 → 22 → 33 → 49 → ...

Add element:
  if (size == elementData.length) grow();
  elementData[size++] = element;
```

### HashSet = HashMap under the hood
```java
// HashSet internally uses HashMap!
public class HashSet<E> {
    private transient HashMap<E, Object> map;
    private static final Object PRESENT = new Object(); // dummy value
    
    public boolean add(E e) {
        return map.put(e, PRESENT) == null;
    }
}
// Elements are stored as KEYS in the internal HashMap
// Values are all the same dummy object (PRESENT)
```

### TreeSet = TreeMap under the hood
```
TreeSet uses Red-Black Tree (self-balancing BST)
All operations: O(log n)
Elements must be Comparable or Comparator provided
```

### PriorityQueue = Binary Min-Heap
```
Array representation of heap:
  Parent: (i-1)/2
  Left child: 2*i + 1
  Right child: 2*i + 2

[1, 3, 5, 7, 9, 8, 6]

        1
       / \
      3   5
     / \ / \
    7  9 8  6

offer(): O(log n) — sift up
poll(): O(log n) — sift down
peek(): O(1) — look at root
```

---

## Diagram

```
Collections Framework Overview:
┌───────────────────────────────────────────────────────┐
│                    Iterable<E>                         │
│                       │                               │
│                  Collection<E>                         │
│           ┌──────────┼──────────┐                    │
│        List<E>     Set<E>    Queue<E>                │
│         │            │          │                     │
│    ArrayList    HashSet    PriorityQueue              │
│    LinkedList   TreeSet    ArrayDeque                 │
│                LinkedHashSet                          │
└───────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────┐
│                     Map<K,V>                           │
│         ┌──────────┼──────────────┐                  │
│      HashMap   TreeMap    ConcurrentHashMap           │
│         │                                             │
│    LinkedHashMap                                      │
└───────────────────────────────────────────────────────┘
```

```
Choosing the Right Collection:
                   ┌─── Need index? ──→ ArrayList
                   │
Need to store ─────┼─── Need unique? ──→ HashSet / TreeSet
elements?          │
                   ├─── Need FIFO? ────→ ArrayDeque
                   │
                   └─── Need priority?─→ PriorityQueue

Need key-value? ───────→ HashMap / TreeMap / ConcurrentHashMap
```

---

## Code

```java
import java.util.*;

public class CollectionsDemo {

    public static void main(String[] args) {
        
        // === LIST ===
        List<String> names = new ArrayList<>(List.of("Alice", "Bob", "Charlie", "Alice"));
        names.add("David");
        names.remove("Alice");           // removes first occurrence
        names.set(0, "Bobby");           // replace at index
        names.sort(Comparator.naturalOrder());
        System.out.println(names.contains("Bobby")); // true
        System.out.println(names.indexOf("Charlie")); // index or -1
        
        // Immutable list (Java 9+)
        List<String> immutable = List.of("A", "B", "C"); // unmodifiable
        // immutable.add("D"); // UnsupportedOperationException

        // === SET ===
        Set<Integer> numbers = new HashSet<>(Arrays.asList(5, 3, 1, 4, 2, 3, 1));
        System.out.println(numbers); // [1, 2, 3, 4, 5] — no duplicates, no order guaranteed
        
        Set<Integer> sorted = new TreeSet<>(numbers);
        System.out.println(sorted); // [1, 2, 3, 4, 5] — sorted
        
        Set<Integer> ordered = new LinkedHashSet<>(Arrays.asList(5, 3, 1, 4, 2));
        System.out.println(ordered); // [5, 3, 1, 4, 2] — insertion order

        // Set operations
        Set<Integer> setA = new HashSet<>(Set.of(1, 2, 3, 4));
        Set<Integer> setB = new HashSet<>(Set.of(3, 4, 5, 6));
        
        Set<Integer> union = new HashSet<>(setA);
        union.addAll(setB);         // [1, 2, 3, 4, 5, 6]
        
        Set<Integer> intersection = new HashSet<>(setA);
        intersection.retainAll(setB); // [3, 4]
        
        Set<Integer> difference = new HashSet<>(setA);
        difference.removeAll(setB);   // [1, 2]

        // === MAP ===
        Map<String, Integer> scores = new HashMap<>();
        scores.put("Alice", 95);
        scores.put("Bob", 87);
        scores.put("Charlie", 92);
        
        scores.getOrDefault("David", 0);        // 0 (not found)
        scores.putIfAbsent("Alice", 100);       // no-op, Alice exists
        scores.computeIfAbsent("David", k -> 0); // adds David=0
        scores.merge("Alice", 5, Integer::sum);  // Alice: 95+5=100
        
        // Iteration patterns
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
        scores.forEach((name, score) -> System.out.println(name + ": " + score));

        // === QUEUE ===
        Queue<String> queue = new ArrayDeque<>();
        queue.offer("First");   // add to tail
        queue.offer("Second");
        queue.offer("Third");
        queue.peek();           // "First" (doesn't remove)
        queue.poll();           // "First" (removes)
        
        // Priority Queue
        PriorityQueue<Integer> pq = new PriorityQueue<>(); // min-heap
        pq.offer(30);
        pq.offer(10);
        pq.offer(20);
        pq.poll(); // 10 (smallest)
        pq.poll(); // 20
        
        // Max-heap
        PriorityQueue<Integer> maxPq = new PriorityQueue<>(Comparator.reverseOrder());
        
        // === DEQUE (Stack + Queue) ===
        Deque<String> stack = new ArrayDeque<>();
        stack.push("First");   // add to head (stack behavior)
        stack.push("Second");
        stack.pop();           // "Second" (LIFO)
    }
}
```

---

## Dry Run

### ArrayList add and grow
```
new ArrayList<>():
  elementData = {} (empty array, DEFAULTCAPACITY_EMPTY_ELEMENTDATA)
  size = 0

add("A"):
  First add → grow to capacity 10
  elementData = [A, null, null, null, null, null, null, null, null, null]
  size = 1

add("B") through add("J"): (10 elements total)
  elementData = [A, B, C, D, E, F, G, H, I, J]
  size = 10

add("K"): capacity exceeded!
  newCapacity = 10 + (10 >> 1) = 15
  elementData = Arrays.copyOf(old, 15)
  elementData = [A, B, C, D, E, F, G, H, I, J, K, null, null, null, null]
  size = 11
```

### HashSet add
```
HashSet<String> set = new HashSet<>();
set.add("hello");

Internally:
  map.put("hello", PRESENT)
  → hash("hello") = 99162322
  → bucket index = 99162322 & (15) = 2
  → bucket[2] empty → insert Node("hello", PRESENT)
  → returns null (first time) → add() returns true

set.add("hello"); // duplicate
  → hash("hello") = 99162322 → same bucket
  → finds existing node where equals() is true
  → returns PRESENT (not null) → add() returns false
```

---

## Complexity

| Collection | Add | Remove | Get/Contains | Search | Notes |
|-----------|-----|--------|--------------|--------|-------|
| **ArrayList** | O(1)* | O(n) | O(1) by index | O(n) | *amortized |
| **LinkedList** | O(1) | O(1)** | O(n) | O(n) | **if node known |
| **HashSet** | O(1) | O(1) | O(1) | O(1) | hash-based |
| **TreeSet** | O(log n) | O(log n) | O(log n) | O(log n) | balanced BST |
| **HashMap** | O(1) | O(1) | O(1) | O(1) | hash-based |
| **TreeMap** | O(log n) | O(log n) | O(log n) | O(log n) | Red-Black tree |
| **PriorityQueue** | O(log n) | O(n) | O(1) peek | O(n) | binary heap |
| **ArrayDeque** | O(1) | O(1)** | O(n) | O(n) | **from ends |

---

## Real Project Usage

```java
// Service layer using appropriate collections

@Service
public class OrderService {
    
    // HashMap for quick lookups
    private final Map<String, Order> orderCache = new ConcurrentHashMap<>();
    
    // TreeMap for range queries (orders by date)
    public NavigableMap<LocalDate, List<Order>> getOrdersByDateRange(
            LocalDate from, LocalDate to) {
        TreeMap<LocalDate, List<Order>> allOrders = loadOrdersByDate();
        return allOrders.subMap(from, true, to, true);
    }
    
    // LinkedHashMap for LRU cache
    private final Map<String, Product> productCache = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Product> eldest) {
            return size() > 100; // cap at 100 entries
        }
    };
    
    // Set for deduplication
    public List<String> getUniqueCategories(List<Product> products) {
        return products.stream()
                .map(Product::getCategory)
                .collect(Collectors.toCollection(LinkedHashSet::new)) // preserve order
                .stream().toList();
    }
    
    // PriorityQueue for task scheduling
    private final PriorityQueue<Task> taskQueue = new PriorityQueue<>(
            Comparator.comparingInt(Task::getPriority)
                      .thenComparing(Task::getCreatedAt)
    );
    
    // ArrayDeque as stack for undo operations
    private final Deque<Command> undoStack = new ArrayDeque<>();
    
    public void executeCommand(Command cmd) {
        cmd.execute();
        undoStack.push(cmd);
    }
    
    public void undo() {
        if (!undoStack.isEmpty()) {
            undoStack.pop().undo();
        }
    }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between ArrayList and LinkedList?**
> ArrayList uses a dynamic array — fast random access O(1) but slow inserts/deletes in the middle O(n) due to shifting. LinkedList uses doubly-linked nodes — O(1) insert/delete if you have the node, but O(n) access by index. In practice, ArrayList wins in almost all cases due to CPU cache locality (contiguous memory). Use LinkedList only when you need constant-time insertions at both ends AND never access by index.

**Q2: Difference between HashMap and Hashtable?**
> HashMap is non-synchronized, allows one null key and multiple null values, uses fail-fast iterators. Hashtable is synchronized (thread-safe but slow), doesn't allow any nulls, uses legacy Enumerator. Never use Hashtable — use ConcurrentHashMap for thread safety or Collections.synchronizedMap() for a synchronized wrapper.

**Q3: How does HashSet ensure uniqueness?**
> HashSet is backed by a HashMap. When you add an element, it's stored as a key in the internal HashMap (with a dummy value). HashMap uses hashCode() to find the bucket and equals() to check for duplicates within the bucket. If equals() returns true for an existing key, the add is rejected (returns false).

**Q4: What is the difference between fail-fast and fail-safe iterators?**
> Fail-fast: Throws ConcurrentModificationException if collection is modified during iteration (ArrayList, HashMap). Uses a modCount field. Fail-safe: Works on a copy or uses concurrent mechanisms — no exception thrown (CopyOnWriteArrayList, ConcurrentHashMap). Fail-safe may not reflect latest modifications.

**Q5: When would you use TreeMap over HashMap?**
> When you need keys in sorted order, or need range operations (subMap, headMap, tailMap, floorKey, ceilingKey). TreeMap uses a Red-Black tree — O(log n) operations. Use HashMap when you just need O(1) lookup and don't care about order. TreeMap also doesn't allow null keys.

**Q6: What is CopyOnWriteArrayList and when to use it?**
> A thread-safe List variant where every mutating operation (add, set, remove) creates a new copy of the underlying array. Reads are lock-free and fast. Use when reads vastly outnumber writes (event listener lists, configuration lists). Writes are O(n) due to copying.

---

## Follow-up Questions and Answers

**Q: Why is ArrayDeque preferred over Stack?**
> `Stack` extends `Vector` (synchronized + legacy). ArrayDeque is faster (no synchronization), more consistent (pure LIFO/FIFO operations), and doesn't expose random-access methods that break the stack abstraction. Java documentation itself recommends ArrayDeque.

**Q: What is WeakHashMap used for?**
> Keys are held via WeakReferences — if no other strong reference to the key exists, the entry is eligible for garbage collection. Use for caches where you want entries to disappear when the key is no longer used elsewhere (e.g., storing metadata about objects that might be GC'd).

**Q: How does LinkedHashMap maintain insertion order?**
> It extends HashMap and additionally maintains a doubly-linked list connecting all entries in insertion order (or access order if configured). Each entry has `before` and `after` pointers in addition to the hash bucket's `next` pointer.

**Q: Can you use a mutable object as a HashMap key?**
> Technically yes, but it's extremely dangerous. If you mutate the key after insertion, its hashCode changes, and the entry becomes "lost" — you can't find it because the new hash points to a different bucket. Always use immutable keys (String, Integer, records).

---

## Common Mistakes

1. **Using wrong collection type**
   ```java
   // Using LinkedList for random access — O(n) per access!
   LinkedList<Integer> list = new LinkedList<>();
   list.get(500); // traverses 500 nodes
   // Fix: ArrayList for index-based access
   ```

2. **ConcurrentModificationException**
   ```java
   for (String item : list) {
       if (item.equals("remove")) {
           list.remove(item); // throws CME!
       }
   }
   // Fix: use Iterator.remove() or removeIf()
   list.removeIf(item -> item.equals("remove"));
   ```

3. **Not specifying initial capacity**
   ```java
   // If you know size will be 10000, avoid multiple resizes
   new ArrayList<>(); // starts at 10, resizes many times
   new ArrayList<>(10000); // single allocation
   new HashMap<>(capacity, 0.75f); // capacity / 0.75 to avoid rehash
   ```

4. **Using null keys in TreeMap**
   ```java
   TreeMap<String, Integer> map = new TreeMap<>();
   map.put(null, 1); // NullPointerException — can't compare null
   ```

5. **Modifying set element after insertion**
   ```java
   Set<List<Integer>> set = new HashSet<>();
   List<Integer> list = new ArrayList<>(List.of(1, 2));
   set.add(list);
   list.add(3); // hashCode changed! Element is "lost" in set
   set.contains(list); // may return false!
   ```

---

## Best Practices

1. **Program to interfaces**: `List<String> list = new ArrayList<>()` — not `ArrayList<String> list`.
2. **Pre-size collections** when you know the approximate size.
3. **Use `List.of()`, `Set.of()`, `Map.of()`** for immutable collections (Java 9+).
4. **Use `Collections.unmodifiableList()`** to protect internal collections.
5. **Prefer ArrayDeque** over Stack and LinkedList for stack/queue operations.
6. **Use EnumSet and EnumMap** for enum-keyed collections — extremely fast (bit vectors).
7. **Use `computeIfAbsent`** instead of check-then-put pattern for maps.
8. **Choose ConcurrentHashMap** over `Collections.synchronizedMap()` — better concurrency.

---

## Production Considerations

- **Memory overhead**: HashMap Entry = 32 bytes overhead per entry. For millions of entries, consider specialized libraries (Eclipse Collections, fastutil) or primitive maps.

- **Initial capacity for HashMap**: `expectedSize / loadFactor + 1` to avoid rehashing. For 100 entries: `new HashMap<>(134)` or use Guava's `Maps.newHashMapWithExpectedSize(100)`.

- **ArrayList vs LinkedList in reality**: ArrayList almost always wins due to cache locality. Even insertions in the middle are faster for ArrayList until the list is extremely large (>10M elements). LinkedList's node allocation causes cache misses.

- **Concurrent collections**: Use `ConcurrentHashMap` for concurrent access. Never iterate over a synchronized collection without external synchronization.

- **Immutability**: Use `List.copyOf()`, `Set.copyOf()`, `Map.copyOf()` (Java 10+) for true immutable copies. `Collections.unmodifiableList()` is just a view — changes to original still reflect.

---

## Related Topics

- → [3. Object Class](./03-object-class.md) (equals/hashCode for Sets and Maps)
- → [7. Collection Internals](./07-collection-internals.md) (deep dive into HashMap)
- → [8. Generics](./08-generics.md)
- → [9. Comparable vs Comparator](./09-comparable-vs-comparator.md) (TreeSet, TreeMap, sorting)
- → [10. Java 8 Features](./10-java8-features.md) (Stream operations on collections)
- → [19. Concurrent Collections](./19-concurrent-collections.md)
