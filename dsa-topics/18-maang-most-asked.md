# MAANG Most Asked DSA Problems

## Top 75 — Prioritized by Frequency

These are the most frequently asked problems at Meta, Amazon, Apple, Netflix, Google, Microsoft, and top-tier companies. Organized by pattern with difficulty and frequency ratings.

---

## Arrays & Hashing 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 1 | **Two Sum** | Easy | HashMap | Store complement in map, O(n) |
| 2 | **Best Time to Buy and Sell Stock** | Easy | Kadane's variant | Track min price, max profit |
| 3 | **Contains Duplicate** | Easy | HashSet | Set size vs array size |
| 4 | **Product of Array Except Self** | Medium | Prefix/Suffix | Left pass × Right pass, no division |
| 5 | **Maximum Subarray** | Medium | Kadane's | Reset running sum when negative |
| 6 | **3Sum** | Medium | Sort + Two Pointers | Sort, fix one, two-pointer rest |
| 7 | **Group Anagrams** | Medium | HashMap + Sorting | sorted(word) as key |
| 8 | **Top K Frequent Elements** | Medium | HashMap + Heap/Bucket | Count freq, then top-K |
| 9 | **Merge Intervals** | Medium | Sort + Sweep | Sort by start, merge overlapping |
| 10 | **Insert Interval** | Medium | Interval logic | Find position, merge overlapping |
| 11 | **Longest Consecutive Sequence** | Medium | HashSet | Check if start of sequence |
| 12 | **Subarray Sum Equals K** | Medium | Prefix Sum + HashMap | prefixSum - k exists in map? |

### Must-Know Template: Subarray Sum = K
```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1);
    int sum = 0, count = 0;
    for (int num : nums) {
        sum += num;
        count += prefixCount.getOrDefault(sum - k, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
}
```

---

## Two Pointers 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 13 | **Valid Palindrome** | Easy | Two Pointers | Left/right skip non-alphanumeric |
| 14 | **Container With Most Water** | Medium | Two Pointers | Move the shorter side inward |
| 15 | **Trapping Rain Water** | Hard | Two Pointers / Stack | Min(leftMax, rightMax) - height |
| 16 | **3Sum** | Medium | Sort + Two Pointers | Fix i, two-pointer j,k |

### Must-Know Template: Trapping Rain Water
```java
public int trap(int[] height) {
    int left = 0, right = height.length - 1;
    int leftMax = 0, rightMax = 0, water = 0;
    while (left < right) {
        if (height[left] < height[right]) {
            leftMax = Math.max(leftMax, height[left]);
            water += leftMax - height[left];
            left++;
        } else {
            rightMax = Math.max(rightMax, height[right]);
            water += rightMax - height[right];
            right--;
        }
    }
    return water;
}
```

---

## Sliding Window 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 17 | **Longest Substring Without Repeating** | Medium | Expand/Shrink | HashMap for last seen index |
| 18 | **Minimum Window Substring** | Hard | Expand/Shrink + Counter | Expand until valid, shrink while valid |
| 19 | **Longest Repeating Character Replacement** | Medium | Fixed condition | window - maxFreq <= k |
| 20 | **Sliding Window Maximum** | Hard | Monotonic Deque | Deque stores indices in decreasing order |

### Must-Know Template: Minimum Window Substring
```java
public String minWindow(String s, String t) {
    Map<Character, Integer> need = new HashMap<>(), window = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    
    int left = 0, valid = 0, start = 0, minLen = Integer.MAX_VALUE;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        window.merge(c, 1, Integer::sum);
        if (window.get(c).equals(need.get(c))) valid++;
        
        while (valid == need.size()) {
            if (right - left + 1 < minLen) {
                start = left;
                minLen = right - left + 1;
            }
            char d = s.charAt(left++);
            if (need.containsKey(d)) {
                if (window.get(d).equals(need.get(d))) valid--;
                window.merge(d, -1, Integer::sum);
            }
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(start, start + minLen);
}
```

---

## Binary Search 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 21 | **Search in Rotated Sorted Array** | Medium | Modified BS | Determine which half is sorted |
| 22 | **Find Minimum in Rotated Sorted Array** | Medium | Modified BS | Compare mid with right |
| 23 | **Median of Two Sorted Arrays** | Hard | Binary Search | Partition both arrays |
| 24 | **Koko Eating Bananas** | Medium | BS on Answer | Binary search on speed |
| 25 | **Time Based Key-Value Store** | Medium | BS on Timestamps | Floor search |

### Must-Know Template: Search in Rotated Array
```java
public int search(int[] nums, int target) {
    int lo = 0, hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) return mid;
        if (nums[lo] <= nums[mid]) { // left half sorted
            if (nums[lo] <= target && target < nums[mid]) hi = mid - 1;
            else lo = mid + 1;
        } else { // right half sorted
            if (nums[mid] < target && target <= nums[hi]) lo = mid + 1;
            else hi = mid - 1;
        }
    }
    return -1;
}
```

---

## Linked List 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 26 | **Reverse Linked List** | Easy | Iterative/Recursive | prev/curr/next pointers |
| 27 | **Merge Two Sorted Lists** | Easy | Merge | Dummy head, compare heads |
| 28 | **Linked List Cycle** | Easy | Fast/Slow | Floyd's cycle detection |
| 29 | **Reorder List** | Medium | Find mid + Reverse + Merge | Three-step process |
| 30 | **Remove Nth Node From End** | Medium | Two Pointers | Advance fast N steps |
| 31 | **Merge K Sorted Lists** | Hard | Min-Heap / Divide & Conquer | PriorityQueue of list heads |
| 32 | **LRU Cache** | Medium | HashMap + Doubly Linked List | O(1) get and put |

### Must-Know: LRU Cache
```java
class LRUCache {
    private Map<Integer, Node> map = new HashMap<>();
    private Node head = new Node(0, 0), tail = new Node(0, 0);
    private int capacity;

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        Node node = map.get(key);
        remove(node);
        insertAfterHead(node);
        return node.val;
    }

    public void put(int key, int value) {
        if (map.containsKey(key)) remove(map.get(key));
        if (map.size() == capacity) {
            remove(tail.prev);
        }
        Node node = new Node(key, value);
        insertAfterHead(node);
    }

    private void remove(Node node) {
        map.remove(node.key);
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void insertAfterHead(Node node) {
        map.put(node.key, node);
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
```

---

## Trees 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 33 | **Invert Binary Tree** | Easy | DFS | Swap left/right recursively |
| 34 | **Maximum Depth of Binary Tree** | Easy | DFS | 1 + max(left, right) |
| 35 | **Same Tree** | Easy | DFS | Compare values + structure |
| 36 | **Binary Tree Level Order Traversal** | Medium | BFS | Queue, process level by level |
| 37 | **Validate BST** | Medium | DFS + Range | Pass min/max bounds down |
| 38 | **Lowest Common Ancestor (BST)** | Medium | BST property | Split point |
| 39 | **Lowest Common Ancestor (BT)** | Medium | DFS | Return node if found in subtree |
| 40 | **Binary Tree Right Side View** | Medium | BFS | Last node in each level |
| 41 | **Serialize and Deserialize Binary Tree** | Hard | BFS/DFS + String | Preorder with null markers |
| 42 | **Kth Smallest Element in BST** | Medium | Inorder | Inorder traversal = sorted |
| 43 | **Construct BT from Preorder & Inorder** | Medium | Recursion | Root from preorder, split by inorder |
| 44 | **Diameter of Binary Tree** | Easy | DFS | max(leftHeight + rightHeight) at each node |

### Must-Know: Lowest Common Ancestor (Binary Tree)
```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) return root;
    return left != null ? left : right;
}
```

---

## Graphs 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 45 | **Number of Islands** | Medium | BFS/DFS + Visited | Flood fill on each unvisited '1' |
| 46 | **Clone Graph** | Medium | BFS/DFS + HashMap | Map old→new nodes |
| 47 | **Course Schedule** | Medium | Topological Sort | Detect cycle in directed graph |
| 48 | **Course Schedule II** | Medium | Topological Sort (Kahn's) | BFS with indegree |
| 49 | **Pacific Atlantic Water Flow** | Medium | Multi-source BFS/DFS | DFS from both oceans, intersect |
| 50 | **Word Ladder** | Hard | BFS | Each word is a node, BFS for shortest |
| 51 | **Graph Valid Tree** | Medium | Union-Find / DFS | n-1 edges + connected |
| 52 | **Number of Connected Components** | Medium | Union-Find / DFS | Count distinct roots |
| 53 | **Rotting Oranges** | Medium | Multi-source BFS | BFS from all rotten simultaneously |
| 54 | **Alien Dictionary** | Hard | Topological Sort | Build graph from word order |

### Must-Know: Topological Sort (Kahn's BFS)
```java
public int[] topologicalSort(int numCourses, int[][] prerequisites) {
    List<List<Integer>> graph = new ArrayList<>();
    int[] indegree = new int[numCourses];
    for (int i = 0; i < numCourses; i++) graph.add(new ArrayList<>());
    for (int[] pre : prerequisites) {
        graph.get(pre[1]).add(pre[0]);
        indegree[pre[0]]++;
    }
    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++)
        if (indegree[i] == 0) queue.offer(i);
    
    int[] result = new int[numCourses];
    int idx = 0;
    while (!queue.isEmpty()) {
        int node = queue.poll();
        result[idx++] = node;
        for (int next : graph.get(node))
            if (--indegree[next] == 0) queue.offer(next);
    }
    return idx == numCourses ? result : new int[0]; // empty = cycle
}
```

---

## Dynamic Programming 🔥🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 55 | **Climbing Stairs** | Easy | Fibonacci | dp[i] = dp[i-1] + dp[i-2] |
| 56 | **House Robber** | Medium | Take/Skip | dp[i] = max(dp[i-1], dp[i-2]+nums[i]) |
| 57 | **Coin Change** | Medium | Unbounded Knapsack | dp[amount] = min coins to make amount |
| 58 | **Longest Increasing Subsequence** | Medium | LIS | dp[i] = length of LIS ending at i |
| 59 | **Word Break** | Medium | DP + Set | dp[i] = can form s[0..i]? |
| 60 | **Unique Paths** | Medium | Grid DP | dp[i][j] = dp[i-1][j] + dp[i][j-1] |
| 61 | **Longest Common Subsequence** | Medium | 2D DP | If match: 1+dp[i-1][j-1], else max |
| 62 | **Decode Ways** | Medium | 1D DP | 1 or 2 digit decode options |
| 63 | **Edit Distance** | Medium | 2D DP | Insert/delete/replace operations |
| 64 | **Partition Equal Subset Sum** | Medium | 0/1 Knapsack | Can subset sum = total/2? |

### Must-Know: Coin Change
```java
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);
    dp[0] = 0;
    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            if (coin <= i) dp[i] = Math.min(dp[i], dp[i - coin] + 1);
        }
    }
    return dp[amount] > amount ? -1 : dp[amount];
}
```

---

## Stack 🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 65 | **Valid Parentheses** | Easy | Stack | Push open, pop on close, check match |
| 66 | **Min Stack** | Medium | Two Stacks | Auxiliary stack tracks current min |
| 67 | **Daily Temperatures** | Medium | Monotonic Stack | Decreasing stack, pop when warmer |
| 68 | **Largest Rectangle in Histogram** | Hard | Monotonic Stack | Stack of increasing heights |
| 69 | **Evaluate Reverse Polish Notation** | Medium | Stack | Push numbers, pop on operator |

---

## Heap / Priority Queue 🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 70 | **Kth Largest Element** | Medium | QuickSelect / MinHeap | MinHeap of size K |
| 71 | **Find Median from Data Stream** | Hard | Two Heaps | MaxHeap(left) + MinHeap(right) |
| 72 | **Task Scheduler** | Medium | Greedy + Heap | Most frequent task first |
| 73 | **Merge K Sorted Lists** | Hard | Min Heap | Push all heads, pop min |

### Must-Know: Find Median from Data Stream
```java
class MedianFinder {
    PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder()); // left half
    PriorityQueue<Integer> minHeap = new PriorityQueue<>(); // right half

    public void addNum(int num) {
        maxHeap.offer(num);
        minHeap.offer(maxHeap.poll());
        if (minHeap.size() > maxHeap.size()) maxHeap.offer(minHeap.poll());
    }

    public double findMedian() {
        return maxHeap.size() > minHeap.size() 
            ? maxHeap.peek() 
            : (maxHeap.peek() + minHeap.peek()) / 2.0;
    }
}
```

---

## Backtracking 🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 74 | **Combination Sum** | Medium | Backtrack | Reuse elements, sort for pruning |
| 75 | **Permutations** | Medium | Backtrack | Swap or visited array |
| 76 | **Subsets** | Medium | Backtrack | Include/exclude each element |
| 77 | **Word Search** | Medium | DFS + Backtrack | Grid DFS with visited marking |
| 78 | **N-Queens** | Hard | Backtrack + Pruning | Column, diagonal, anti-diagonal sets |
| 79 | **Letter Combinations of Phone** | Medium | Backtrack | Digit → letters mapping |

---

## Intervals 🔥🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 80 | **Merge Intervals** | Medium | Sort + Merge | Sort by start, extend end |
| 81 | **Non-overlapping Intervals** | Medium | Greedy | Sort by end, count removals |
| 82 | **Meeting Rooms II** | Medium | Sort + Min Heap | Track end times in heap |

---

## Trie 🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 83 | **Implement Trie** | Medium | Trie | Node with children[26] + isEnd |
| 84 | **Word Search II** | Hard | Trie + DFS | Build trie from words, DFS on grid |

---

## Bit Manipulation 🔥

| # | Problem | Difficulty | Pattern | Key Insight |
|---|---------|-----------|---------|-------------|
| 85 | **Number of 1 Bits** | Easy | Bit trick | n & (n-1) removes lowest set bit |
| 86 | **Counting Bits** | Easy | DP + Bit | dp[i] = dp[i >> 1] + (i & 1) |
| 87 | **Single Number** | Easy | XOR | XOR all elements, duplicate cancels |
| 88 | **Reverse Bits** | Easy | Bit manipulation | Shift and OR |

---

## MAANG-Specific Favorites ⭐⭐⭐

### Google Favorites
- Median of Two Sorted Arrays
- Word Ladder / Word Ladder II
- Serialize/Deserialize Binary Tree
- Alien Dictionary
- Sliding Window Maximum

### Amazon Favorites
- LRU Cache
- Number of Islands
- Merge Intervals
- Two Sum
- Word Break
- Meeting Rooms II
- Top K Frequent Elements

### Meta (Facebook) Favorites
- Valid Palindrome II
- Subarray Sum Equals K
- Binary Tree Right Side View
- Lowest Common Ancestor
- Random Pick with Weight
- Add Strings / Multiply Strings
- Merge K Sorted Lists

### Microsoft Favorites
- LRU Cache
- Serialize/Deserialize Binary Tree
- Reverse Linked List
- Spiral Matrix
- Group Anagrams
- Min Stack

### Apple Favorites
- 3Sum
- Trapping Rain Water
- Longest Substring Without Repeating
- House Robber
- Valid Parentheses

---

## Study Priority (4-Week Sprint) ⭐⭐⭐

```
Week 1: Arrays + Two Pointers + Sliding Window (Problems 1-20)
├── Day 1-2: Two Sum, Best Time Buy/Sell, Contains Duplicate, Product Except Self
├── Day 3-4: 3Sum, Container Most Water, Longest Substring Without Repeating
├── Day 5-6: Merge Intervals, Min Window Substring, Trapping Rain Water
└── Day 7: Review + patterns practice

Week 2: Binary Search + Linked List + Trees (Problems 21-44)
├── Day 1-2: Rotated Array, Koko Bananas, Median Two Sorted
├── Day 3-4: Reverse LL, Merge K Lists, LRU Cache
├── Day 5-6: Validate BST, LCA, Serialize/Deserialize Tree
└── Day 7: Review + patterns practice

Week 3: Graphs + DP (Problems 45-64)
├── Day 1-2: Islands, Course Schedule, Word Ladder
├── Day 3-4: Climbing Stairs, House Robber, Coin Change
├── Day 5-6: LIS, Word Break, Edit Distance
└── Day 7: Review + patterns practice

Week 4: Stack + Heap + Backtracking + Review (Problems 65-88)
├── Day 1-2: Daily Temperatures, Largest Rectangle, Median Stream
├── Day 3-4: Combination Sum, Word Search, N-Queens
├── Day 5-6: Full mock interviews (pick 2 random, 45 min each)
└── Day 7: Weak areas review
```

---

## Pattern Recognition Cheat Sheet ⭐⭐⭐

```
"Find two elements that..." → HashMap (Two Sum pattern)
"Subarray with sum..." → Prefix Sum + HashMap
"Longest/shortest substring..." → Sliding Window
"Sorted array search..." → Binary Search
"kth largest/smallest..." → Heap (min-heap of size k)
"All combinations/permutations..." → Backtracking
"Number of ways to..." → Dynamic Programming
"Shortest path..." → BFS
"Detect cycle..." → DFS / Union-Find / Floyd's
"Top K..." → Heap or Bucket Sort
"Intervals overlap..." → Sort + Sweep
"Tree traversal..." → DFS (recursive) or BFS (queue)
"Graph dependency order..." → Topological Sort
"Consecutive elements..." → HashSet
"Parentheses valid..." → Stack
"Next greater element..." → Monotonic Stack
"Stream of data, median..." → Two Heaps
```
