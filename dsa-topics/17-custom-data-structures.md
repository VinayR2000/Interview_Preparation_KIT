# 17. Custom Data Structures — Interview Implementations ⭐⭐⭐

---

## Problem 1: Custom ArrayList (Dynamic Array) ⭐⭐⭐

### How Java's ArrayList Works Internally

```
┌─────────────────────────────────────────────────────────────┐
│                    ARRAYLIST INTERNALS                        │
│                                                              │
│  Backed by: Object[] elementData (internal array)           │
│  Default capacity: 10                                        │
│  Growth factor: 1.5x (newCapacity = oldCapacity + old/2)    │
│                                                              │
│  add(element):                                               │
│    If size == capacity → grow array (1.5x), copy elements   │
│    elementData[size++] = element                             │
│                                                              │
│  get(index):                                                 │
│    Bounds check → return elementData[index]                  │
│                                                              │
│  remove(index):                                              │
│    Shift elements left → size--                              │
│                                                              │
│  Time Complexities:                                          │
│    add(end):    O(1) amortized (O(n) when resizing)         │
│    add(index):  O(n) — shift elements right                 │
│    get(index):  O(1) — direct array access                  │
│    remove(idx): O(n) — shift elements left                  │
│    contains:    O(n) — linear scan                          │
│    size:        O(1)                                         │
└─────────────────────────────────────────────────────────────┘
```

### Full Implementation

```java
public class CustomArrayList<E> {
    private static final int DEFAULT_CAPACITY = 10;
    private Object[] elementData;
    private int size;

    // ─── Constructors ───
    public CustomArrayList() {
        this.elementData = new Object[DEFAULT_CAPACITY];
        this.size = 0;
    }

    public CustomArrayList(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + initialCapacity);
        }
        this.elementData = new Object[initialCapacity];
        this.size = 0;
    }

    // ─── Core Operations ───

    /**
     * Add element at the end. O(1) amortized.
     */
    public void add(E element) {
        ensureCapacity();
        elementData[size++] = element;
    }

    /**
     * Add element at specific index. O(n) — shifts elements right.
     */
    public void add(int index, E element) {
        rangeCheckForAdd(index);
        ensureCapacity();
        // Shift elements to the right
        System.arraycopy(elementData, index, elementData, index + 1, size - index);
        elementData[index] = element;
        size++;
    }

    /**
     * Get element at index. O(1).
     */
    @SuppressWarnings("unchecked")
    public E get(int index) {
        rangeCheck(index);
        return (E) elementData[index];
    }

    /**
     * Set element at index, return old value. O(1).
     */
    @SuppressWarnings("unchecked")
    public E set(int index, E element) {
        rangeCheck(index);
        E oldValue = (E) elementData[index];
        elementData[index] = element;
        return oldValue;
    }

    /**
     * Remove element at index, return removed element. O(n) — shifts left.
     */
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        rangeCheck(index);
        E removedElement = (E) elementData[index];

        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elementData, index + 1, elementData, index, numMoved);
        }
        elementData[--size] = null; // help GC
        return removedElement;
    }

    /**
     * Remove first occurrence of element. O(n).
     */
    public boolean remove(Object obj) {
        int index = indexOf(obj);
        if (index == -1) return false;
        remove(index);
        return true;
    }

    /**
     * Check if element exists. O(n).
     */
    public boolean contains(Object obj) {
        return indexOf(obj) >= 0;
    }

    /**
     * Find index of element. O(n).
     */
    public int indexOf(Object obj) {
        if (obj == null) {
            for (int i = 0; i < size; i++) {
                if (elementData[i] == null) return i;
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (obj.equals(elementData[i])) return i;
            }
        }
        return -1;
    }

    /**
     * Current number of elements.
     */
    public int size() {
        return size;
    }

    /**
     * Is the list empty?
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Remove all elements.
     */
    public void clear() {
        for (int i = 0; i < size; i++) {
            elementData[i] = null; // help GC
        }
        size = 0;
    }

    // ─── Internal Helpers ───

    /**
     * Grow array when full. New capacity = old * 1.5 (matching Java's ArrayList).
     */
    private void ensureCapacity() {
        if (size == elementData.length) {
            int newCapacity = elementData.length + (elementData.length >> 1); // 1.5x
            if (newCapacity == elementData.length) newCapacity++; // handle capacity 0 or 1
            Object[] newArray = new Object[newCapacity];
            System.arraycopy(elementData, 0, newArray, 0, size);
            elementData = newArray;
        }
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    private void rangeCheckForAdd(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(elementData[i]);
            if (i < size - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }
}
```

### Dry Run

```
CustomArrayList<Integer> list = new CustomArrayList<>();
// elementData = [null × 10], size=0

list.add(10);  // elementData = [10, null×9], size=1
list.add(20);  // elementData = [10, 20, null×8], size=2
list.add(30);  // elementData = [10, 20, 30, null×7], size=3

list.get(1);   // → 20 (direct array access)

list.add(1, 15);
// Shift: [10, 20, 30] → [10, _, 20, 30], insert 15 at index 1
// elementData = [10, 15, 20, 30, null×6], size=4

list.remove(2);
// Remove index 2 (value 20). Shift left: [10, 15, 30, null×7], size=3
// Returns 20

list.size();   // → 3
list.toString(); // → [10, 15, 30]

// RESIZE SCENARIO:
// If we keep adding until size==10:
// ensureCapacity() → newCapacity = 10 + 5 = 15
// Creates new array of size 15, copies elements, old array GC'd
```

### Growth Pattern

```
Initial capacity: 10
After 10 elements:  grows to 15  (10 + 10/2)
After 15 elements:  grows to 22  (15 + 15/2)
After 22 elements:  grows to 33  (22 + 22/2)
After 33 elements:  grows to 49  (33 + 33/2)

Amortized cost of add: O(1)
  Total copies across n insertions: n + n/1.5 + n/1.5² + ... ≈ 3n = O(n)
  Average per insertion: O(n)/n = O(1)
```

### Complexity Summary

| Operation | Time | Why |
|-----------|------|-----|
| `add(E)` (end) | O(1) amortized | Direct placement, occasional resize |
| `add(index, E)` | O(n) | Shift elements right |
| `get(index)` | O(1) | Direct array indexing |
| `set(index, E)` | O(1) | Direct array indexing |
| `remove(index)` | O(n) | Shift elements left |
| `remove(Object)` | O(n) | Linear search + shift |
| `contains(Object)` | O(n) | Linear scan |
| `indexOf(Object)` | O(n) | Linear scan |
| `size()` | O(1) | Return field |
| Space | O(n) | Array of capacity ≥ size |

---

## Interview Follow-Up Questions

### Q1: Why grow by 1.5x and not 2x?

**A:** Memory efficiency. 2x growth wastes up to 50% of allocated memory. 1.5x wastes up to 33%. Java uses 1.5x (`oldCapacity + oldCapacity >> 1`). C++ vectors typically use 2x. The tradeoff: 2x = fewer resizes but more wasted memory.

### Q2: Why does ArrayList use Object[] instead of E[]?

**A:** Java generics are erased at runtime (type erasure). You can't create `new E[10]` because the JVM doesn't know what E is. So ArrayList uses `Object[]` internally and casts on retrieval with `@SuppressWarnings("unchecked")`.

### Q3: What happens if you don't null out removed elements?

**A:** Memory leak. The array still holds a reference to the object, preventing garbage collection even after logical removal. That's why `elementData[--size] = null` is critical.

### Q4: How is ArrayList different from LinkedList?

**A:**
| | ArrayList | LinkedList |
|---|-----------|------------|
| Get by index | O(1) | O(n) |
| Add at end | O(1) amortized | O(1) |
| Add at middle | O(n) shift | O(1) if pointer known |
| Remove by index | O(n) shift | O(n) traverse + O(1) unlink |
| Memory | Compact (cache-friendly) | Scattered (node overhead) |
| Use when | Random access, iteration | Frequent insert/delete at known position |

In practice, ArrayList wins almost always due to CPU cache locality.

### Q5: Is ArrayList thread-safe?

**A:** No. Use `Collections.synchronizedList(new ArrayList<>())` or `CopyOnWriteArrayList` for thread safety. `CopyOnWriteArrayList` copies the entire array on write — good for read-heavy, rarely-modified lists.

---

## Bonus: Custom ArrayList with Iterator

```java
public class CustomArrayList<E> implements Iterable<E> {
    // ... (all code above) ...

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            @SuppressWarnings("unchecked")
            public E next() {
                if (!hasNext()) throw new NoSuchElementException();
                return (E) elementData[cursor++];
            }
        };
    }
}

// Usage:
CustomArrayList<String> list = new CustomArrayList<>();
list.add("A"); list.add("B"); list.add("C");

for (String s : list) {
    System.out.println(s); // A, B, C
}
```

---

## Related Topics

- [Java Collections Framework](../java-topics/06-collections-framework.md)
- [Java Collection Internals](../java-topics/07-collection-internals.md)
- [Generics](../java-topics/08-generics.md)
