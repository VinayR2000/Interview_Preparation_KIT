# 6. Linked List — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Reverse Linked List (NeetCode #206) ⭐⭐

### Problem
Reverse a singly linked list.

### Solution (Iterative)

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null, curr = head;
    
    while (curr != null) {
        ListNode next = curr.next; // save next
        curr.next = prev;          // reverse pointer
        prev = curr;               // advance prev
        curr = next;               // advance curr
    }
    return prev;
}
```

### Dry Run

```
Input: 1 → 2 → 3 → 4 → 5

prev=null, curr=1
  next=2, 1.next=null, prev=1, curr=2     | null ← 1   2 → 3 → 4 → 5
  next=3, 2.next=1, prev=2, curr=3        | null ← 1 ← 2   3 → 4 → 5
  next=4, 3.next=2, prev=3, curr=4        | null ← 1 ← 2 ← 3   4 → 5
  next=5, 4.next=3, prev=4, curr=5        | null ← 1 ← 2 ← 3 ← 4   5
  next=null, 5.next=4, prev=5, curr=null   | null ← 1 ← 2 ← 3 ← 4 ← 5

return prev = 5

Output: 5 → 4 → 3 → 2 → 1
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 2: Merge Two Sorted Lists (NeetCode #21) ⭐

### Problem
Merge two sorted linked lists into one sorted list.

### Solution

```java
public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;
    
    while (list1 != null && list2 != null) {
        if (list1.val <= list2.val) {
            curr.next = list1;
            list1 = list1.next;
        } else {
            curr.next = list2;
            list2 = list2.next;
        }
        curr = curr.next;
    }
    
    curr.next = (list1 != null) ? list1 : list2;
    return dummy.next;
}
```

### Dry Run

```
Input: list1 = 1→2→4, list2 = 1→3→4

dummy→?, curr=dummy
  1<=1 → curr.next=list1(1), list1=2, curr=1     dummy→1
  2>1  → curr.next=list2(1), list2=3, curr=1     dummy→1→1
  2<=3 → curr.next=list1(2), list1=4, curr=2     dummy→1→1→2
  4>3  → curr.next=list2(3), list2=4, curr=3     dummy→1→1→2→3
  4<=4 → curr.next=list1(4), list1=null, curr=4  dummy→1→1→2→3→4
  list1=null → curr.next=list2(4)                 dummy→1→1→2→3→4→4

Output: 1→1→2→3→4→4
```

### Complexity
- Time: O(m + n)
- Space: O(1)

---

## Problem 3: Linked List Cycle (NeetCode #141) ⭐⭐

### Problem
Determine if a linked list has a cycle.

### Approach: Floyd's Tortoise and Hare
- Slow moves 1 step, fast moves 2 steps
- If cycle exists, they'll meet

### Solution

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}
```

### Dry Run

```
Input: 1→2→3→4→2 (4 points back to 2, cycle)

slow=1, fast=1
  slow=2, fast=3
  slow=3, fast=2 (went 3→4→2)
  slow=4, fast=4 (went 2→3→4) → slow==fast! return true

Output: true
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 4: Reorder List (NeetCode #143) ⭐⭐⭐

### Problem
Reorder list from `L0→L1→...→Ln` to `L0→Ln→L1→Ln-1→L2→Ln-2→...`

### Approach
1. Find middle (slow/fast pointer)
2. Reverse second half
3. Merge alternately

### Solution

```java
public void reorderList(ListNode head) {
    if (head == null || head.next == null) return;
    
    // Step 1: Find middle
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    
    // Step 2: Reverse second half
    ListNode second = reverse(slow.next);
    slow.next = null; // cut first half
    
    // Step 3: Merge alternately
    ListNode first = head;
    while (second != null) {
        ListNode tmp1 = first.next, tmp2 = second.next;
        first.next = second;
        second.next = tmp1;
        first = tmp1;
        second = tmp2;
    }
}

private ListNode reverse(ListNode head) {
    ListNode prev = null, curr = head;
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }
    return prev;
}
```

### Dry Run

```
Input: 1→2→3→4→5

Step 1: Find middle
  slow=1,fast=1 → slow=2,fast=3 → slow=3,fast=5
  middle = 3, second half starts at 4

Step 2: Reverse second half (4→5 → 5→4)
  first: 1→2→3
  second: 5→4

Step 3: Merge alternately
  first=1, second=5: 1→5→2→3, first=2, second=4
  first=2, second=4: 1→5→2→4→3, first=3, second=null
  second=null → done

Output: 1→5→2→4→3
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 5: Remove Nth Node From End (NeetCode #19) ⭐⭐

### Problem
Remove the nth node from the end of the list in one pass.

### Approach: Two pointers, n apart
- Advance fast n steps ahead, then move both. When fast reaches end, slow is at target.

### Solution

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0, head);
    ListNode slow = dummy, fast = dummy;
    
    // Move fast n+1 steps ahead
    for (int i = 0; i <= n; i++) {
        fast = fast.next;
    }
    
    // Move both until fast reaches end
    while (fast != null) {
        slow = slow.next;
        fast = fast.next;
    }
    
    // Skip the target node
    slow.next = slow.next.next;
    return dummy.next;
}
```

### Dry Run

```
Input: 1→2→3→4→5, n=2 (remove 4)

dummy→1→2→3→4→5
fast moves n+1=3 steps: fast=3
slow=dummy, fast=3
  slow=1, fast=4
  slow=2, fast=5
  slow=3, fast=null → stop!

slow.next = slow.next.next → 3.next = 5 (skips 4)

Output: 1→2→3→5
```

### Complexity
- Time: O(n) — single pass
- Space: O(1)

---

## Problem 6: LRU Cache (NeetCode #146) ⭐⭐⭐

### Problem
Design a data structure that follows LRU (Least Recently Used) eviction. `get(key)` and `put(key, value)` in O(1).

### Approach: HashMap + Doubly Linked List
- HashMap: key → Node (O(1) access)
- DLL: maintains order (head = most recent, tail = least recent)

### Solution

```java
class LRUCache {
    private int capacity;
    private Map<Integer, Node> map;
    private Node head, tail; // dummy nodes
    
    class Node {
        int key, value;
        Node prev, next;
        Node(int k, int v) { key = k; value = v; }
    }
    
    public LRUCache(int capacity) {
        this.capacity = capacity;
        map = new HashMap<>();
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }
    
    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        remove(node);
        addToHead(node);
        return node.value;
    }
    
    public void put(int key, int value) {
        if (map.containsKey(key)) {
            Node node = map.get(key);
            node.value = value;
            remove(node);
            addToHead(node);
        } else {
            if (map.size() == capacity) {
                Node lru = tail.prev; // least recently used
                remove(lru);
                map.remove(lru.key);
            }
            Node newNode = new Node(key, value);
            addToHead(newNode);
            map.put(key, newNode);
        }
    }
    
    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    private void addToHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
```

### Dry Run

```
LRUCache(2)
  head ↔ tail, map={}

put(1, 1): map={1:Node(1,1)}, head ↔ [1,1] ↔ tail
put(2, 2): map={1,2}, head ↔ [2,2] ↔ [1,1] ↔ tail
get(1):    move [1,1] to head. head ↔ [1,1] ↔ [2,2] ↔ tail. return 1
put(3, 3): capacity full! Evict tail.prev = [2,2]. Remove from map.
           Add [3,3]. head ↔ [3,3] ↔ [1,1] ↔ tail, map={1,3}
get(2):    key 2 not in map → return -1
```

### Complexity
- Time: O(1) for both get and put
- Space: O(capacity)

---

## Problem 7: Merge K Sorted Lists (NeetCode #23) ⭐⭐⭐

### Problem
Merge k sorted linked lists into one sorted list.

### Approach: Min-Heap (Priority Queue)

```java
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> pq = new PriorityQueue<>((a, b) -> a.val - b.val);
    
    for (ListNode list : lists) {
        if (list != null) pq.offer(list);
    }
    
    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;
    
    while (!pq.isEmpty()) {
        ListNode smallest = pq.poll();
        curr.next = smallest;
        curr = curr.next;
        if (smallest.next != null) {
            pq.offer(smallest.next);
        }
    }
    return dummy.next;
}
```

### Approach 2: Divide and Conquer (Merge Sort style)

```java
public ListNode mergeKLists(ListNode[] lists) {
    if (lists == null || lists.length == 0) return null;
    return mergeRange(lists, 0, lists.length - 1);
}

private ListNode mergeRange(ListNode[] lists, int start, int end) {
    if (start == end) return lists[start];
    int mid = start + (end - start) / 2;
    ListNode left = mergeRange(lists, start, mid);
    ListNode right = mergeRange(lists, mid + 1, end);
    return mergeTwoLists(left, right);
}
```

### Complexity
- Min-Heap: Time O(n log k), Space O(k)
- Divide & Conquer: Time O(n log k), Space O(log k)
- where n = total nodes, k = number of lists

---

## Problem 8: Find the Duplicate Number (NeetCode #287) ⭐⭐⭐

### Problem
Array of n+1 integers in range [1, n]. One number repeats. Find it without modifying array. O(1) space.

### Approach: Floyd's Cycle Detection (treat array as linked list)
- `nums[i]` is the "next" pointer. Since one value repeats, there's a cycle.

### Solution

```java
public int findDuplicate(int[] nums) {
    // Phase 1: Find intersection point
    int slow = nums[0], fast = nums[0];
    do {
        slow = nums[slow];
        fast = nums[nums[fast]];
    } while (slow != fast);
    
    // Phase 2: Find cycle entrance (= duplicate)
    slow = nums[0];
    while (slow != fast) {
        slow = nums[slow];
        fast = nums[fast];
    }
    return slow;
}
```

### Dry Run

```
Input: nums = [1, 3, 4, 2, 2]
Indices:       0  1  2  3  4

Linked list interpretation:
  0→1→3→2→4→2→4→2... (cycle at node 2)

Phase 1: Find meeting point
  slow=nums[0]=1, fast=nums[0]=1
  slow=nums[1]=3, fast=nums[nums[1]]=nums[3]=2
  slow=nums[3]=2, fast=nums[nums[2]]=nums[4]=2
  slow==fast at 2 → meeting point

Phase 2: Find entrance
  slow=nums[0]=1, fast=2
  slow=nums[1]=3, fast=nums[2]=4
  slow=nums[3]=2, fast=nums[4]=2
  slow==fast at 2 → duplicate!

Output: 2
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Summary — Linked List Key Techniques

| Technique | When to Use | Problems |
|-----------|-------------|----------|
| Reverse iteratively | Reverse portion or full list | Reverse List, Reverse K-Group |
| Dummy head | Simplify edge cases (head removal) | Remove Nth, Merge Lists |
| Slow/Fast pointers | Find middle, detect cycle | Cycle Detection, Middle Node |
| Floyd's Algorithm | Find cycle start / duplicate | Linked List Cycle II, Find Duplicate |
| Merge technique | Combine sorted structures | Merge Two Lists, Merge K Lists |
| HashMap + DLL | O(1) ordered access | LRU Cache |
