# 1. DSA Pattern Selection & Templates — Complete Guide

---

## Pattern Selection Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     DSA PATTERN SELECTION FLOWCHART                      │
└─────────────────────────────────────────────────────────────────────────┘

                            START
                              │
                    ┌─────────▼─────────┐
                    │  Input Type?       │
                    └─────────┬─────────┘
                              │
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    Array/String           Graph/Tree           Optimization
         │                    │                    │
    ┌────▼────┐          ┌───▼───┐          ┌────▼────┐
    │ Sorted? │          │ DFS / │          │   DP    │
    │ Pairs?  │          │ BFS   │          │         │
    │ Subarray│          └───────┘          └─────────┘
    └────┬────┘
         │
    ┌────┼──────────────────┬──────────────────┐
    │    │                  │                  │
    ▼    ▼                  ▼                  ▼
Sorted+Pair        Subarray/Substring     Frequency/Lookup
    │                    │                     │
┌───▼────┐         ┌────▼──────┐         ┌───▼──────┐
│  Two   │         │  Sliding  │         │ Hash Map │
│Pointers│         │  Window   │         │ Hash Set │
└────────┘         └───────────┘         └──────────┘
```

### Quick Decision Table

| Signal in Problem | Pattern | Examples |
|-------------------|---------|----------|
| Sorted array, find pair, palindrome | Two Pointers | Two Sum II, Container With Most Water, Valid Palindrome |
| Contiguous subarray/substring, max/min window | Sliding Window | Max Sum Subarray, Longest Substring Without Repeating |
| Count frequency, check existence, lookup | Hash Map / Set | Two Sum, Group Anagrams, Contains Duplicate |
| Sorted array, search target, min/max feasible | Binary Search | Search in Rotated Array, Koko Eating Bananas |
| Tree traversal, connected components, paths | DFS / BFS | Max Depth, Number of Islands, Level Order Traversal |
| Overlapping subproblems, optimal value | DP | Climbing Stairs, Longest Subsequence, Knapsack |

---

## Pattern 1: Two Pointers

### When to Use
- Array is **sorted** (or can be sorted)
- Finding **pairs** that satisfy a condition
- **Palindrome** checking
- Removing duplicates in-place
- Partitioning (Dutch National Flag)

### Template

```java
// Template 1: Opposite-direction pointers (sorted array, pairs)
public int[] twoPointers(int[] arr, int target) {
    int left = 0, right = arr.length - 1;
    
    while (left < right) {
        int sum = arr[left] + arr[right];
        if (sum == target) {
            return new int[]{left, right};
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    return new int[]{-1, -1};
}
```

```java
// Template 2: Same-direction pointers (remove duplicates / partition)
public int removeDuplicates(int[] nums) {
    if (nums.length == 0) return 0;
    int slow = 0;
    
    for (int fast = 1; fast < nums.length; fast++) {
        if (nums[fast] != nums[slow]) {
            slow++;
            nums[slow] = nums[fast];
        }
    }
    return slow + 1;
}
```

```java
// Template 3: Palindrome check
public boolean isPalindrome(String s) {
    int left = 0, right = s.length() - 1;
    
    while (left < right) {
        while (left < right && !Character.isLetterOrDigit(s.charAt(left))) left++;
        while (left < right && !Character.isLetterOrDigit(s.charAt(right))) right--;
        
        if (Character.toLowerCase(s.charAt(left)) != Character.toLowerCase(s.charAt(right))) {
            return false;
        }
        left++;
        right--;
    }
    return true;
}
```

### Complexity
- Time: O(n) — single pass with two pointers
- Space: O(1) — no extra space

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Two Sum II (sorted) | Opposite | Sum too small → move left; too big → move right |
| 3Sum | Opposite + loop | Fix one, two-pointer on rest |
| Container With Most Water | Opposite | Move the shorter line |
| Remove Duplicates | Same-direction | Slow = write position, fast = read |
| Trapping Rain Water | Opposite | Min of left_max, right_max minus height |
| Sort Colors | Three pointers | Dutch National Flag (0, 1, 2 partition) |

---

## Pattern 2: Sliding Window

### When to Use
- Finding **subarray** or **substring** with specific property
- Max/min sum/length of contiguous elements
- Window with at most K distinct characters
- Fixed-size or variable-size window

### Template

```java
// Template 1: Fixed-size window
public int maxSumFixedWindow(int[] arr, int k) {
    int windowSum = 0, maxSum = Integer.MIN_VALUE;
    
    for (int i = 0; i < arr.length; i++) {
        windowSum += arr[i];             // expand: add right element
        
        if (i >= k - 1) {               // window is full
            maxSum = Math.max(maxSum, windowSum);
            windowSum -= arr[i - k + 1]; // shrink: remove left element
        }
    }
    return maxSum;
}
```

```java
// Template 2: Variable-size window (shrink when condition violated)
public int longestSubstringKDistinct(String s, int k) {
    Map<Character, Integer> freq = new HashMap<>();
    int left = 0, maxLen = 0;
    
    for (int right = 0; right < s.length(); right++) {
        // Expand: add s[right] to window
        freq.merge(s.charAt(right), 1, Integer::sum);
        
        // Shrink: while window is invalid
        while (freq.size() > k) {
            char leftChar = s.charAt(left);
            freq.merge(leftChar, -1, Integer::sum);
            if (freq.get(leftChar) == 0) freq.remove(leftChar);
            left++;
        }
        
        // Update answer
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

```java
// Template 3: Minimum window (find smallest valid window)
public String minWindow(String s, String t) {
    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    
    int left = 0, matched = 0;
    int minLen = Integer.MAX_VALUE, minStart = 0;
    
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (need.containsKey(c)) {
            need.merge(c, -1, Integer::sum);
            if (need.get(c) >= 0) matched++;
        }
        
        // Shrink: while window is valid, try to minimize
        while (matched == t.length()) {
            if (right - left + 1 < minLen) {
                minLen = right - left + 1;
                minStart = left;
            }
            char leftChar = s.charAt(left);
            if (need.containsKey(leftChar)) {
                need.merge(leftChar, 1, Integer::sum);
                if (need.get(leftChar) > 0) matched--;
            }
            left++;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(minStart, minStart + minLen);
}
```

### Complexity
- Time: O(n) — each element is added/removed at most once
- Space: O(k) — map size bounded by distinct chars or window content

### Classic Problems

| Problem | Window Type | Key Insight |
|---------|-------------|-------------|
| Max Sum Subarray of Size K | Fixed | Subtract left, add right |
| Longest Substring Without Repeating | Variable (max) | Shrink when duplicate found |
| Minimum Window Substring | Variable (min) | Expand until valid, shrink to minimize |
| Max Consecutive Ones III | Variable (max) | At most K zeros allowed |
| Permutation in String | Fixed (size of p) | Frequency map comparison |
| Fruit Into Baskets | Variable (max) | At most 2 distinct types |

---

## Pattern 3: Hash Map / Set

### When to Use
- **Frequency** counting
- **Lookup** in O(1) — check if element exists
- **Grouping** elements by property
- **Two Sum** style — complement lookup
- Detecting **duplicates**

### Template

```java
// Template 1: Two Sum (complement lookup)
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>(); // value → index
    
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (map.containsKey(complement)) {
            return new int[]{map.get(complement), i};
        }
        map.put(nums[i], i);
    }
    return new int[]{-1, -1};
}
```

```java
// Template 2: Frequency count
public int majorityElement(int[] nums) {
    Map<Integer, Integer> freq = new HashMap<>();
    
    for (int num : nums) {
        freq.merge(num, 1, Integer::sum);
        if (freq.get(num) > nums.length / 2) {
            return num;
        }
    }
    return -1;
}
```

```java
// Template 3: Grouping (anagrams)
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();
    
    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(groups.values());
}
```

```java
// Template 4: HashSet for duplicate detection / existence
public boolean containsDuplicate(int[] nums) {
    Set<Integer> seen = new HashSet<>();
    for (int num : nums) {
        if (!seen.add(num)) return true; // add returns false if exists
    }
    return false;
}
```

```java
// Template 5: Subarray sum equals K (prefix sum + hash map)
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1); // empty prefix
    int sum = 0, count = 0;
    
    for (int num : nums) {
        sum += num;
        count += prefixCount.getOrDefault(sum - k, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
}
```

### Complexity
- Time: O(n) — single pass with O(1) lookups
- Space: O(n) — storing elements in map/set

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Two Sum | Complement lookup | Store value→index, check target-num |
| Group Anagrams | Grouping | Sorted string as key |
| Contains Duplicate | Set existence | add() returns false if exists |
| Longest Consecutive Sequence | Set + expand | Check if num-1 not in set (start of sequence) |
| Subarray Sum Equals K | Prefix sum + map | prefixSum[j] - prefixSum[i] = k |
| Top K Frequent Elements | Frequency + sort | Count first, then sort/heap |

---

## Pattern 4: Binary Search

### When to Use
- Array is **sorted**
- Search for **target** or **boundary** (first/last occurrence)
- **Minimize maximum** or **maximize minimum** (search on answer)
- Find **peak**, rotation point, or insertion point

### Template

```java
// Template 1: Standard binary search
public int binarySearch(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;
        else if (nums[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return -1;
}
```

```java
// Template 2: Find left boundary (first occurrence / lower bound)
public int leftBound(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] >= target) right = mid - 1;
        else left = mid + 1;
    }
    // left = first index where nums[left] >= target
    return (left < nums.length && nums[left] == target) ? left : -1;
}
```

```java
// Template 3: Find right boundary (last occurrence / upper bound)
public int rightBound(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] <= target) left = mid + 1;
        else right = mid - 1;
    }
    // right = last index where nums[right] <= target
    return (right >= 0 && nums[right] == target) ? right : -1;
}
```

```java
// Template 4: Binary search on answer (minimize maximum / feasibility check)
public int minMaxBinarySearch(int[] nums, int condition) {
    int left = minPossible, right = maxPossible;
    
    while (left < right) {
        int mid = left + (right - left) / 2;
        if (isFeasible(mid, nums, condition)) {
            right = mid;       // mid might be answer, try smaller
        } else {
            left = mid + 1;    // mid too small, need bigger
        }
    }
    return left; // minimum feasible value
}

private boolean isFeasible(int candidate, int[] nums, int condition) {
    // Check if 'candidate' satisfies the problem constraint
    // Return true if valid, false if not
}
```

### Complexity
- Time: O(log n) — halving search space each step
- Space: O(1) — iterative approach

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Binary Search | Standard | Classic find target |
| First/Last Position | Left/Right boundary | Adjust condition for >= or <= |
| Search in Rotated Sorted Array | Modified | Find which half is sorted |
| Find Peak Element | Modified | Move toward larger neighbor |
| Koko Eating Bananas | Search on answer | Minimize speed, check feasibility |
| Split Array Largest Sum | Search on answer | Minimize max sum, check splits |
| Median of Two Sorted Arrays | Partition-based | Binary search on partition point |

---

## Pattern 5: DFS (Depth-First Search)

### When to Use
- **Tree** traversal (preorder, inorder, postorder)
- **Graph** exploration (connected components, cycle detection)
- **Backtracking** (permutations, combinations, subsets)
- Path finding where you need **all paths** or **any path**
- Detecting **cycles** in directed/undirected graphs

### Template

```java
// Template 1: Tree DFS (recursive)
public int maxDepth(TreeNode root) {
    if (root == null) return 0;
    int left = maxDepth(root.left);
    int right = maxDepth(root.right);
    return 1 + Math.max(left, right);
}
```

```java
// Template 2: Graph DFS (visited tracking)
public int countComponents(int n, int[][] edges) {
    List<List<Integer>> graph = new ArrayList<>();
    for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
    for (int[] edge : edges) {
        graph.get(edge[0]).add(edge[1]);
        graph.get(edge[1]).add(edge[0]);
    }
    
    boolean[] visited = new boolean[n];
    int components = 0;
    
    for (int i = 0; i < n; i++) {
        if (!visited[i]) {
            dfs(graph, i, visited);
            components++;
        }
    }
    return components;
}

private void dfs(List<List<Integer>> graph, int node, boolean[] visited) {
    visited[node] = true;
    for (int neighbor : graph.get(node)) {
        if (!visited[neighbor]) {
            dfs(graph, neighbor, visited);
        }
    }
}
```

```java
// Template 3: Grid DFS (2D matrix — Number of Islands)
public int numIslands(char[][] grid) {
    int count = 0;
    for (int i = 0; i < grid.length; i++) {
        for (int j = 0; j < grid[0].length; j++) {
            if (grid[i][j] == '1') {
                dfsGrid(grid, i, j);
                count++;
            }
        }
    }
    return count;
}

private void dfsGrid(char[][] grid, int r, int c) {
    if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] == '0') {
        return;
    }
    grid[r][c] = '0'; // mark visited
    dfsGrid(grid, r + 1, c);
    dfsGrid(grid, r - 1, c);
    dfsGrid(grid, r, c + 1);
    dfsGrid(grid, r, c - 1);
}
```

```java
// Template 4: Backtracking (subsets / permutations / combinations)
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, int start, List<Integer> current, List<List<Integer>> result) {
    result.add(new ArrayList<>(current)); // add copy of current state
    
    for (int i = start; i < nums.length; i++) {
        current.add(nums[i]);           // choose
        backtrack(nums, i + 1, current, result); // explore
        current.remove(current.size() - 1);      // unchoose (backtrack)
    }
}
```

### Complexity
- Tree DFS: Time O(n), Space O(h) where h = height (O(log n) balanced, O(n) skewed)
- Graph DFS: Time O(V + E), Space O(V)
- Grid DFS: Time O(rows × cols), Space O(rows × cols) worst case
- Backtracking: Time O(2^n) subsets, O(n!) permutations

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Max Depth of Binary Tree | Tree DFS | Recursive: 1 + max(left, right) |
| Number of Islands | Grid DFS | Flood fill, mark visited |
| Clone Graph | Graph DFS | Map old → new, DFS neighbors |
| Course Schedule (cycle) | Graph DFS | Track visiting state (3 colors) |
| Subsets / Combinations | Backtracking | Include/exclude each element |
| Permutations | Backtracking | Swap or used[] array |
| Word Search | Grid + Backtracking | DFS + restore cell after visit |

---

## Pattern 6: BFS (Breadth-First Search)

### When to Use
- **Shortest path** in unweighted graph
- **Level-order** traversal of tree
- Processing nodes **layer by layer**
- Finding **minimum steps** to reach target
- Multi-source BFS (rotting oranges, walls and gates)

### Template

```java
// Template 1: Tree Level Order Traversal
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;
    
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    
    while (!queue.isEmpty()) {
        int levelSize = queue.size();
        List<Integer> level = new ArrayList<>();
        
        for (int i = 0; i < levelSize; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(level);
    }
    return result;
}
```

```java
// Template 2: Graph BFS — Shortest Path (unweighted)
public int shortestPath(int[][] graph, int src, int dst) {
    Queue<Integer> queue = new LinkedList<>();
    boolean[] visited = new boolean[graph.length];
    
    queue.offer(src);
    visited[src] = true;
    int distance = 0;
    
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int node = queue.poll();
            if (node == dst) return distance;
            
            for (int neighbor : graph[node]) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.offer(neighbor);
                }
            }
        }
        distance++;
    }
    return -1; // unreachable
}
```

```java
// Template 3: Grid BFS — Shortest Path in Matrix
public int shortestPathGrid(int[][] grid) {
    int rows = grid.length, cols = grid[0].length;
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    
    Queue<int[]> queue = new LinkedList<>();
    boolean[][] visited = new boolean[rows][cols];
    
    queue.offer(new int[]{0, 0});
    visited[0][0] = true;
    int steps = 0;
    
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cell = queue.poll();
            if (cell[0] == rows - 1 && cell[1] == cols - 1) return steps;
            
            for (int[] dir : dirs) {
                int nr = cell[0] + dir[0], nc = cell[1] + dir[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols 
                    && !visited[nr][nc] && grid[nr][nc] == 0) {
                    visited[nr][nc] = true;
                    queue.offer(new int[]{nr, nc});
                }
            }
        }
        steps++;
    }
    return -1;
}
```

```java
// Template 4: Multi-source BFS (Rotting Oranges / Walls and Gates)
public int orangesRotting(int[][] grid) {
    Queue<int[]> queue = new LinkedList<>();
    int fresh = 0;
    
    // Add ALL sources to queue initially
    for (int i = 0; i < grid.length; i++) {
        for (int j = 0; j < grid[0].length; j++) {
            if (grid[i][j] == 2) queue.offer(new int[]{i, j});
            else if (grid[i][j] == 1) fresh++;
        }
    }
    
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    int minutes = 0;
    
    while (!queue.isEmpty() && fresh > 0) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            int[] cell = queue.poll();
            for (int[] dir : dirs) {
                int nr = cell[0] + dir[0], nc = cell[1] + dir[1];
                if (nr >= 0 && nr < grid.length && nc >= 0 && nc < grid[0].length
                    && grid[nr][nc] == 1) {
                    grid[nr][nc] = 2;
                    fresh--;
                    queue.offer(new int[]{nr, nc});
                }
            }
        }
        minutes++;
    }
    return fresh == 0 ? minutes : -1;
}
```

### Complexity
- Time: O(V + E) for graphs, O(rows × cols) for grids
- Space: O(V) or O(rows × cols) — queue + visited

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Binary Tree Level Order | Tree BFS | Process level by level with size |
| Shortest Path (unweighted) | Graph BFS | BFS guarantees shortest |
| Word Ladder | Graph BFS | Each word is a node, one-char diff = edge |
| Rotting Oranges | Multi-source | All rotten start in queue |
| 01 Matrix (nearest 0) | Multi-source | All 0s start in queue |
| Open the Lock | State BFS | Each combo is a node |

---

## Pattern 7: Dynamic Programming (DP)

### When to Use
- Problem has **overlapping subproblems** (same computation repeated)
- Problem has **optimal substructure** (optimal solution built from optimal sub-solutions)
- Asking for **count**, **min/max**, or **is it possible**
- Sequences (LIS, LCS), knapsack, grid paths, string matching

### Template

```java
// Template 1: 1D DP — Climbing Stairs / Fibonacci style
public int climbStairs(int n) {
    if (n <= 2) return n;
    int prev2 = 1, prev1 = 2;
    
    for (int i = 3; i <= n; i++) {
        int curr = prev1 + prev2;
        prev2 = prev1;
        prev1 = curr;
    }
    return prev1;
}
```

```java
// Template 2: 1D DP — House Robber (take or skip)
public int rob(int[] nums) {
    int n = nums.length;
    if (n == 1) return nums[0];
    
    int[] dp = new int[n];
    dp[0] = nums[0];
    dp[1] = Math.max(nums[0], nums[1]);
    
    for (int i = 2; i < n; i++) {
        dp[i] = Math.max(dp[i - 1],       // skip current
                         dp[i - 2] + nums[i]); // take current
    }
    return dp[n - 1];
}
```

```java
// Template 3: 2D DP — Grid Paths (Unique Paths)
public int uniquePaths(int m, int n) {
    int[][] dp = new int[m][n];
    
    // Base cases: first row and first column are all 1
    for (int i = 0; i < m; i++) dp[i][0] = 1;
    for (int j = 0; j < n; j++) dp[0][j] = 1;
    
    for (int i = 1; i < m; i++) {
        for (int j = 1; j < n; j++) {
            dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
        }
    }
    return dp[m - 1][n - 1];
}
```

```java
// Template 4: String DP — Longest Common Subsequence (LCS)
public int longestCommonSubsequence(String text1, String text2) {
    int m = text1.length(), n = text2.length();
    int[][] dp = new int[m + 1][n + 1];
    
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                dp[i][j] = dp[i - 1][j - 1] + 1;
            } else {
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
    }
    return dp[m][n];
}
```

```java
// Template 5: Knapsack — 0/1 Knapsack
public int knapsack(int[] weights, int[] values, int capacity) {
    int n = weights.length;
    int[][] dp = new int[n + 1][capacity + 1];
    
    for (int i = 1; i <= n; i++) {
        for (int w = 0; w <= capacity; w++) {
            dp[i][w] = dp[i - 1][w]; // don't take item i
            if (weights[i - 1] <= w) {
                dp[i][w] = Math.max(dp[i][w], 
                    dp[i - 1][w - weights[i - 1]] + values[i - 1]); // take item i
            }
        }
    }
    return dp[n][capacity];
}
```

```java
// Template 6: LIS — Longest Increasing Subsequence (O(n log n))
public int lengthOfLIS(int[] nums) {
    List<Integer> tails = new ArrayList<>(); // tails[i] = smallest tail of IS of length i+1
    
    for (int num : nums) {
        int pos = Collections.binarySearch(tails, num);
        if (pos < 0) pos = -(pos + 1); // insertion point
        
        if (pos == tails.size()) {
            tails.add(num);
        } else {
            tails.set(pos, num);
        }
    }
    return tails.size();
}
```

### DP Problem Classification

```
┌────────────────────────────────────────────────────────┐
│               DP PROBLEM CATEGORIES                     │
├────────────────────────────────────────────────────────┤
│                                                        │
│  1D Linear          2D Grid/String       Knapsack      │
│  ─────────          ──────────────       ────────      │
│  Climbing Stairs    Unique Paths         0/1 Knapsack  │
│  House Robber       Min Path Sum         Subset Sum    │
│  Max Subarray       LCS                  Coin Change   │
│  Decode Ways        Edit Distance        Partition     │
│  Word Break         Regex Matching                     │
│                                                        │
│  Interval DP        State Machine        Tree DP       │
│  ───────────        ─────────────        ───────       │
│  Burst Balloons     Stock Buy/Sell       House Robber  │
│  Matrix Chain       State transitions    Binary Tree   │
│  Palindrome Part.   with k states        Maximum Path  │
│                                                        │
└────────────────────────────────────────────────────────┘
```

### Complexity
- 1D DP: Time O(n), Space O(n) or O(1) with space optimization
- 2D DP: Time O(m×n), Space O(m×n) or O(n) with rolling array
- Knapsack: Time O(n×W), Space O(n×W) or O(W)

### Classic Problems

| Problem | Category | Recurrence |
|---------|----------|------------|
| Climbing Stairs | 1D | dp[i] = dp[i-1] + dp[i-2] |
| House Robber | 1D | dp[i] = max(dp[i-1], dp[i-2] + nums[i]) |
| Coin Change | Unbounded Knapsack | dp[i] = min(dp[i-coin] + 1) for each coin |
| LCS | 2D String | match → dp[i-1][j-1]+1; else max(dp[i-1][j], dp[i][j-1]) |
| Edit Distance | 2D String | match → dp[i-1][j-1]; else 1+min(insert, delete, replace) |
| Longest Increasing Subseq | 1D/Binary Search | O(n²) or O(n log n) with patience sort |
| Partition Equal Subset Sum | 0/1 Knapsack | dp[sum] = can we form this sum? |

---

## Bonus Patterns

### Prefix Sum

```java
// Template: Range sum queries in O(1) after O(n) preprocessing
public class PrefixSum {
    private int[] prefix;
    
    public PrefixSum(int[] nums) {
        prefix = new int[nums.length + 1];
        for (int i = 0; i < nums.length; i++) {
            prefix[i + 1] = prefix[i] + nums[i];
        }
    }
    
    // Sum of nums[left..right] inclusive
    public int rangeSum(int left, int right) {
        return prefix[right + 1] - prefix[left];
    }
}
```

### Monotonic Stack

```java
// Template: Next Greater Element
public int[] nextGreaterElement(int[] nums) {
    int n = nums.length;
    int[] result = new int[n];
    Arrays.fill(result, -1);
    Deque<Integer> stack = new ArrayDeque<>(); // stores indices
    
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && nums[i] > nums[stack.peek()]) {
            result[stack.pop()] = nums[i];
        }
        stack.push(i);
    }
    return result;
}
```

### Union-Find (Disjoint Set)

```java
// Template: Union-Find with path compression + union by rank
public class UnionFind {
    private int[] parent, rank;
    private int components;
    
    public UnionFind(int n) {
        parent = new int[n];
        rank = new int[n];
        components = n;
        for (int i = 0; i < n; i++) parent[i] = i;
    }
    
    public int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]); // path compression
        return parent[x];
    }
    
    public boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;
        if (rank[px] < rank[py]) { int t = px; px = py; py = t; }
        parent[py] = px;
        if (rank[px] == rank[py]) rank[px]++;
        components--;
        return true;
    }
    
    public int getComponents() { return components; }
}
```

### Topological Sort (Kahn's BFS)

```java
// Template: Topological Sort using BFS (Kahn's algorithm)
public int[] topologicalSort(int n, int[][] edges) {
    List<List<Integer>> graph = new ArrayList<>();
    int[] indegree = new int[n];
    
    for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
    for (int[] edge : edges) {
        graph.get(edge[0]).add(edge[1]);
        indegree[edge[1]]++;
    }
    
    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < n; i++) {
        if (indegree[i] == 0) queue.offer(i);
    }
    
    int[] order = new int[n];
    int idx = 0;
    
    while (!queue.isEmpty()) {
        int node = queue.poll();
        order[idx++] = node;
        for (int neighbor : graph.get(node)) {
            if (--indegree[neighbor] == 0) {
                queue.offer(neighbor);
            }
        }
    }
    return idx == n ? order : new int[0]; // empty if cycle exists
}
```

---

## Pattern 8: Heap / Priority Queue

### When to Use
- **Top K** elements (largest/smallest)
- **Merge K** sorted lists/arrays
- **Running median** or streaming statistics
- **Scheduling** problems (meeting rooms, task scheduler)
- Anything that repeatedly needs min/max of dynamic data

### Template

```java
// Template 1: Top K Frequent Elements
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int num : nums) freq.merge(num, 1, Integer::sum);
    
    // Min-heap of size k (keeps the k largest)
    PriorityQueue<Integer> minHeap = new PriorityQueue<>(
        (a, b) -> freq.get(a) - freq.get(b)
    );
    
    for (int num : freq.keySet()) {
        minHeap.offer(num);
        if (minHeap.size() > k) minHeap.poll();
    }
    
    int[] result = new int[k];
    for (int i = 0; i < k; i++) result[i] = minHeap.poll();
    return result;
}
```

```java
// Template 2: Merge K Sorted Lists
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> minHeap = new PriorityQueue<>(
        (a, b) -> a.val - b.val
    );
    
    for (ListNode head : lists) {
        if (head != null) minHeap.offer(head);
    }
    
    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;
    
    while (!minHeap.isEmpty()) {
        ListNode node = minHeap.poll();
        curr.next = node;
        curr = curr.next;
        if (node.next != null) minHeap.offer(node.next);
    }
    return dummy.next;
}
```

```java
// Template 3: Find Median from Data Stream (Two Heaps)
class MedianFinder {
    private PriorityQueue<Integer> maxHeap; // left half (smaller numbers)
    private PriorityQueue<Integer> minHeap; // right half (larger numbers)
    
    public MedianFinder() {
        maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        minHeap = new PriorityQueue<>();
    }
    
    public void addNum(int num) {
        maxHeap.offer(num);
        minHeap.offer(maxHeap.poll()); // balance: move max of left to right
        
        if (minHeap.size() > maxHeap.size()) {
            maxHeap.offer(minHeap.poll()); // keep left >= right in size
        }
    }
    
    public double findMedian() {
        if (maxHeap.size() > minHeap.size()) return maxHeap.peek();
        return (maxHeap.peek() + minHeap.peek()) / 2.0;
    }
}
```

```java
// Template 4: K Closest Points to Origin
public int[][] kClosest(int[][] points, int k) {
    // Max-heap of size k (keeps k smallest distances)
    PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
        (a, b) -> (b[0]*b[0] + b[1]*b[1]) - (a[0]*a[0] + a[1]*a[1])
    );
    
    for (int[] point : points) {
        maxHeap.offer(point);
        if (maxHeap.size() > k) maxHeap.poll();
    }
    return maxHeap.toArray(new int[k][]);
}
```

### Complexity
- Top K: Time O(n log k), Space O(k)
- Merge K lists: Time O(N log k) where N = total nodes, Space O(k)
- Median stream: Time O(log n) per add, O(1) median, Space O(n)

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Top K Frequent Elements | Min-heap size k | Frequency map + heap |
| Kth Largest Element | Min-heap size k | Poll when size > k |
| Merge K Sorted Lists | Min-heap | Always poll smallest head |
| Find Median from Stream | Two heaps | Max-heap (left) + min-heap (right) |
| Meeting Rooms II | Min-heap | Track end times, poll if overlap |
| Task Scheduler | Max-heap + cooldown | Greedy with heap |
| Reorganize String | Max-heap | Place most frequent first |

---

## Pattern 9: Trie (Prefix Tree)

### When to Use
- **Prefix** search or autocomplete
- **Word dictionary** with insert/search/startsWith
- **Word search** in grid (Word Search II)
- Longest common prefix
- Counting words with given prefix

### Template

```java
// Template 1: Trie with Insert, Search, StartsWith
class TrieNode {
    TrieNode[] children = new TrieNode[26];
    boolean isEnd = false;
}

class Trie {
    private TrieNode root = new TrieNode();
    
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        node.isEnd = true;
    }
    
    public boolean search(String word) {
        TrieNode node = searchPrefix(word);
        return node != null && node.isEnd;
    }
    
    public boolean startsWith(String prefix) {
        return searchPrefix(prefix) != null;
    }
    
    private TrieNode searchPrefix(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) return null;
            node = node.children[idx];
        }
        return node;
    }
}
```

```java
// Template 2: Word Search II (Trie + Grid DFS)
public List<String> findWords(char[][] board, String[] words) {
    TrieNode root = buildTrie(words);
    List<String> result = new ArrayList<>();
    
    for (int i = 0; i < board.length; i++) {
        for (int j = 0; j < board[0].length; j++) {
            dfs(board, i, j, root, result);
        }
    }
    return result;
}

private void dfs(char[][] board, int r, int c, TrieNode node, List<String> result) {
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return;
    char ch = board[r][c];
    if (ch == '#' || node.children[ch - 'a'] == null) return;
    
    node = node.children[ch - 'a'];
    if (node.word != null) {
        result.add(node.word);
        node.word = null; // avoid duplicates
    }
    
    board[r][c] = '#'; // mark visited
    dfs(board, r+1, c, node, result);
    dfs(board, r-1, c, node, result);
    dfs(board, r, c+1, node, result);
    dfs(board, r, c-1, node, result);
    board[r][c] = ch; // restore
}
```

### Complexity
- Insert: O(m) where m = word length
- Search/StartsWith: O(m)
- Space: O(total characters across all words × 26)

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Implement Trie | Basic | Array of 26 children + isEnd flag |
| Word Search II | Trie + Grid DFS | Build trie from words, DFS on grid |
| Design Autocomplete | Trie + frequency | Store count at each node |
| Longest Common Prefix | Trie traversal | Walk until branching |
| Replace Words | Trie lookup | Find shortest prefix in trie |

---

## Pattern 10: Greedy

### When to Use
- **Locally optimal** choice leads to **globally optimal** solution
- Interval scheduling (activity selection, merge intervals)
- **Jump game** style problems
- When DP is overkill and greedy proof exists
- Sorting + choosing best at each step

### Template

```java
// Template 1: Interval Scheduling — Maximum non-overlapping intervals
public int eraseOverlapIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]); // sort by END time
    int count = 0, prevEnd = Integer.MIN_VALUE;
    
    for (int[] interval : intervals) {
        if (interval[0] >= prevEnd) {
            prevEnd = interval[1]; // no overlap, take this interval
        } else {
            count++; // overlap, remove this one (greedy: keep earlier end)
        }
    }
    return count;
}
```

```java
// Template 2: Jump Game (can reach end?)
public boolean canJump(int[] nums) {
    int maxReach = 0;
    
    for (int i = 0; i < nums.length; i++) {
        if (i > maxReach) return false; // can't reach this index
        maxReach = Math.max(maxReach, i + nums[i]);
    }
    return true;
}
```

```java
// Template 3: Jump Game II (minimum jumps to reach end)
public int jump(int[] nums) {
    int jumps = 0, currEnd = 0, farthest = 0;
    
    for (int i = 0; i < nums.length - 1; i++) {
        farthest = Math.max(farthest, i + nums[i]);
        if (i == currEnd) {
            jumps++;
            currEnd = farthest;
        }
    }
    return jumps;
}
```

```java
// Template 4: Task Scheduler (minimum intervals with cooldown)
public int leastInterval(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char t : tasks) freq[t - 'A']++;
    
    int maxFreq = Arrays.stream(freq).max().getAsInt();
    int maxCount = (int) Arrays.stream(freq).filter(f -> f == maxFreq).count();
    
    // Formula: (maxFreq - 1) * (n + 1) + maxCount
    // Minimum is at least tasks.length (when no idle needed)
    return Math.max(tasks.length, (maxFreq - 1) * (n + 1) + maxCount);
}
```

```java
// Template 5: Gas Station (circular route)
public int canCompleteCircuit(int[] gas, int[] cost) {
    int totalSurplus = 0, currentSurplus = 0, startIdx = 0;
    
    for (int i = 0; i < gas.length; i++) {
        totalSurplus += gas[i] - cost[i];
        currentSurplus += gas[i] - cost[i];
        
        if (currentSurplus < 0) {
            startIdx = i + 1;   // can't start from 0..i, try i+1
            currentSurplus = 0;
        }
    }
    return totalSurplus >= 0 ? startIdx : -1;
}
```

### Complexity
- Usually O(n log n) if sorting needed, O(n) if already sorted
- Space: O(1) to O(n) depending on problem

### Classic Problems

| Problem | Key Insight |
|---------|-------------|
| Activity Selection / Non-overlapping Intervals | Sort by end time, greedily pick |
| Jump Game | Track max reachable index |
| Gas Station | If total surplus >= 0, solution exists; reset on deficit |
| Task Scheduler | Fill slots around most frequent task |
| Partition Labels | Last occurrence defines partition boundary |
| Assign Cookies | Sort both, match greedily |
| Minimum Arrows to Burst Balloons | Sort by end, count non-overlapping groups |

---

## Pattern 11: Divide and Conquer

### When to Use
- **Split** problem into independent subproblems
- Merge sort, quick sort, quick select
- Finding **Kth element** efficiently
- Problems on ranges where halving helps
- Count inversions, closest pair of points

### Template

```java
// Template 1: Merge Sort (and count inversions)
public int[] mergeSort(int[] arr, int left, int right) {
    if (left >= right) return new int[]{arr[left]};
    
    int mid = left + (right - left) / 2;
    int[] leftArr = mergeSort(arr, left, mid);
    int[] rightArr = mergeSort(arr, mid + 1, right);
    return merge(leftArr, rightArr);
}

private int[] merge(int[] left, int[] right) {
    int[] result = new int[left.length + right.length];
    int i = 0, j = 0, k = 0;
    
    while (i < left.length && j < right.length) {
        if (left[i] <= right[j]) result[k++] = left[i++];
        else result[k++] = right[j++];
    }
    while (i < left.length) result[k++] = left[i++];
    while (j < right.length) result[k++] = right[j++];
    return result;
}
```

```java
// Template 2: Quick Select — Kth Largest Element (O(n) average)
public int findKthLargest(int[] nums, int k) {
    int target = nums.length - k; // kth largest = (n-k)th smallest
    return quickSelect(nums, 0, nums.length - 1, target);
}

private int quickSelect(int[] nums, int left, int right, int target) {
    int pivot = partition(nums, left, right);
    
    if (pivot == target) return nums[pivot];
    else if (pivot < target) return quickSelect(nums, pivot + 1, right, target);
    else return quickSelect(nums, left, pivot - 1, target);
}

private int partition(int[] nums, int left, int right) {
    int pivot = nums[right];
    int i = left;
    
    for (int j = left; j < right; j++) {
        if (nums[j] <= pivot) {
            swap(nums, i, j);
            i++;
        }
    }
    swap(nums, i, right);
    return i;
}
```

```java
// Template 3: Maximum Subarray (Divide and Conquer)
public int maxSubArray(int[] nums) {
    return maxSub(nums, 0, nums.length - 1);
}

private int maxSub(int[] nums, int left, int right) {
    if (left == right) return nums[left];
    
    int mid = left + (right - left) / 2;
    int leftMax = maxSub(nums, left, mid);
    int rightMax = maxSub(nums, mid + 1, right);
    int crossMax = maxCrossing(nums, left, mid, right);
    
    return Math.max(Math.max(leftMax, rightMax), crossMax);
}

private int maxCrossing(int[] nums, int left, int mid, int right) {
    int leftSum = Integer.MIN_VALUE, sum = 0;
    for (int i = mid; i >= left; i--) {
        sum += nums[i];
        leftSum = Math.max(leftSum, sum);
    }
    int rightSum = Integer.MIN_VALUE;
    sum = 0;
    for (int i = mid + 1; i <= right; i++) {
        sum += nums[i];
        rightSum = Math.max(rightSum, sum);
    }
    return leftSum + rightSum;
}
```

### Complexity
- Merge Sort: Time O(n log n), Space O(n)
- Quick Select: Time O(n) average, O(n²) worst, Space O(1)
- General: T(n) = 2T(n/2) + O(n) → O(n log n) by Master Theorem

### Classic Problems

| Problem | Key Insight |
|---------|-------------|
| Merge Sort | Split, sort halves, merge |
| Kth Largest (Quick Select) | Partition around pivot, recurse one side |
| Count Inversions | Count during merge step |
| Closest Pair of Points | Split by x, solve halves, check strip |
| Majority Element | Majority in at least one half |
| Pow(x, n) | x^n = (x^(n/2))² — O(log n) |

---

## Pattern 12: Linked List Patterns (Fast/Slow Pointers)

### When to Use
- **Cycle detection** (Floyd's algorithm)
- Finding **middle** of linked list
- Finding **Nth node from end**
- **Reversing** linked list (iterative)
- **Merge** two sorted lists
- Detecting **intersection** point

### Template

```java
// Template 1: Detect Cycle (Floyd's Tortoise and Hare)
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

```java
// Template 2: Find Cycle Start
public ListNode detectCycle(ListNode head) {
    ListNode slow = head, fast = head;
    
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) {
            // Move one pointer to head, advance both by 1
            ListNode ptr = head;
            while (ptr != slow) {
                ptr = ptr.next;
                slow = slow.next;
            }
            return ptr; // cycle start
        }
    }
    return null;
}
```

```java
// Template 3: Find Middle Node
public ListNode middleNode(ListNode head) {
    ListNode slow = head, fast = head;
    
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow; // middle (second middle if even length)
}
```

```java
// Template 4: Reverse Linked List (iterative)
public ListNode reverseList(ListNode head) {
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

```java
// Template 5: Remove Nth Node from End
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0, head);
    ListNode fast = dummy, slow = dummy;
    
    // Move fast n+1 steps ahead
    for (int i = 0; i <= n; i++) fast = fast.next;
    
    // Move both until fast reaches end
    while (fast != null) {
        fast = fast.next;
        slow = slow.next;
    }
    slow.next = slow.next.next; // remove nth from end
    return dummy.next;
}
```

```java
// Template 6: Merge Two Sorted Lists
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0);
    ListNode curr = dummy;
    
    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) {
            curr.next = l1;
            l1 = l1.next;
        } else {
            curr.next = l2;
            l2 = l2.next;
        }
        curr = curr.next;
    }
    curr.next = (l1 != null) ? l1 : l2;
    return dummy.next;
}
```

### Complexity
- Cycle detection: Time O(n), Space O(1)
- Middle/Nth from end: Time O(n), Space O(1)
- Reverse: Time O(n), Space O(1)
- Merge: Time O(n + m), Space O(1)

### Classic Problems

| Problem | Variant | Key Insight |
|---------|---------|-------------|
| Linked List Cycle | Fast/Slow | Fast catches slow if cycle exists |
| Linked List Cycle II | Floyd's | Reset one pointer to head after meet |
| Middle of Linked List | Fast/Slow | Fast moves 2x speed |
| Palindrome Linked List | Middle + Reverse | Find middle, reverse second half, compare |
| Reorder List | Middle + Reverse + Merge | Split, reverse, interleave |
| Intersection of Two Lists | Two pointers | Redirect to other head on null |
| Reverse Nodes in k-Group | Reverse + Count | Reverse k nodes, recurse/iterate |

---

## Pattern 13: Bit Manipulation

### When to Use
- Finding **single/unique** number (XOR)
- **Power of two** checks
- **Counting bits** or toggling specific bits
- Subset generation using bitmask
- Problems where O(1) space is critical and values are integers

### Template

```java
// Template 1: Single Number (XOR all — duplicates cancel)
public int singleNumber(int[] nums) {
    int result = 0;
    for (int num : nums) {
        result ^= num; // a ^ a = 0, a ^ 0 = a
    }
    return result;
}
```

```java
// Template 2: Power of Two
public boolean isPowerOfTwo(int n) {
    return n > 0 && (n & (n - 1)) == 0;
    // Power of 2 has exactly one set bit
    // n & (n-1) removes lowest set bit
}
```

```java
// Template 3: Count Set Bits (Brian Kernighan's)
public int countBits(int n) {
    int count = 0;
    while (n != 0) {
        n &= (n - 1); // remove lowest set bit
        count++;
    }
    return count;
}
```

```java
// Template 4: Subsets using Bitmask
public List<List<Integer>> subsets(int[] nums) {
    int n = nums.length;
    List<List<Integer>> result = new ArrayList<>();
    
    for (int mask = 0; mask < (1 << n); mask++) {
        List<Integer> subset = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ((mask & (1 << i)) != 0) {
                subset.add(nums[i]);
            }
        }
        result.add(subset);
    }
    return result;
}
```

```java
// Template 5: Two numbers appearing once (all others twice)
public int[] singleNumberIII(int[] nums) {
    int xor = 0;
    for (int num : nums) xor ^= num; // xor = a ^ b
    
    int diffBit = xor & (-xor); // lowest set bit (where a and b differ)
    
    int a = 0, b = 0;
    for (int num : nums) {
        if ((num & diffBit) == 0) a ^= num;
        else b ^= num;
    }
    return new int[]{a, b};
}
```

### Key Bit Operations

```
a & b       — AND (both bits 1)
a | b       — OR (either bit 1)
a ^ b       — XOR (bits differ)
~a          — NOT (flip all bits)
a << n      — Left shift (multiply by 2^n)
a >> n      — Right shift (divide by 2^n)
a & (a-1)   — Remove lowest set bit
a & (-a)    — Isolate lowest set bit
```

### Complexity
- Time: O(n) for array traversal, O(1) for individual operations
- Space: O(1)

### Classic Problems

| Problem | Key Insight |
|---------|-------------|
| Single Number | XOR all elements — pairs cancel to 0 |
| Single Number II (appears once, others 3x) | Count bits mod 3 at each position |
| Power of Two | n & (n-1) == 0 |
| Counting Bits (0 to n) | dp[i] = dp[i >> 1] + (i & 1) |
| Reverse Bits | Bit-by-bit shift |
| Missing Number | XOR with indices (0..n XOR array) |
| Subsets (bitmask) | Each bit = include/exclude |

---

## Pattern 14: Interval Merging

### When to Use
- **Merge overlapping** intervals
- **Insert** interval into sorted list
- **Meeting rooms** (overlap detection, min rooms)
- Find **gaps** between intervals
- Interval intersection

### Template

```java
// Template 1: Merge Overlapping Intervals
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]); // sort by start
    List<int[]> merged = new ArrayList<>();
    
    for (int[] interval : intervals) {
        if (merged.isEmpty() || merged.get(merged.size() - 1)[1] < interval[0]) {
            merged.add(interval); // no overlap
        } else {
            merged.get(merged.size() - 1)[1] = 
                Math.max(merged.get(merged.size() - 1)[1], interval[1]); // merge
        }
    }
    return merged.toArray(new int[merged.size()][]);
}
```

```java
// Template 2: Insert Interval
public int[][] insert(int[][] intervals, int[] newInterval) {
    List<int[]> result = new ArrayList<>();
    int i = 0;
    
    // Add all intervals ending before newInterval starts
    while (i < intervals.length && intervals[i][1] < newInterval[0]) {
        result.add(intervals[i++]);
    }
    
    // Merge all overlapping intervals with newInterval
    while (i < intervals.length && intervals[i][0] <= newInterval[1]) {
        newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
        newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
        i++;
    }
    result.add(newInterval);
    
    // Add remaining intervals
    while (i < intervals.length) {
        result.add(intervals[i++]);
    }
    return result.toArray(new int[result.size()][]);
}
```

```java
// Template 3: Meeting Rooms II (minimum rooms needed)
public int minMeetingRooms(int[][] intervals) {
    int[] starts = new int[intervals.length];
    int[] ends = new int[intervals.length];
    
    for (int i = 0; i < intervals.length; i++) {
        starts[i] = intervals[i][0];
        ends[i] = intervals[i][1];
    }
    Arrays.sort(starts);
    Arrays.sort(ends);
    
    int rooms = 0, endPtr = 0;
    for (int start : starts) {
        if (start < ends[endPtr]) {
            rooms++;
        } else {
            endPtr++;
        }
    }
    return rooms;
}
```

```java
// Template 4: Interval Intersection
public int[][] intervalIntersection(int[][] A, int[][] B) {
    List<int[]> result = new ArrayList<>();
    int i = 0, j = 0;
    
    while (i < A.length && j < B.length) {
        int lo = Math.max(A[i][0], B[j][0]);
        int hi = Math.min(A[i][1], B[j][1]);
        
        if (lo <= hi) result.add(new int[]{lo, hi});
        
        // Advance the one that ends first
        if (A[i][1] < B[j][1]) i++;
        else j++;
    }
    return result.toArray(new int[result.size()][]);
}
```

### Complexity
- Merge: Time O(n log n) for sort, Space O(n)
- Insert: Time O(n), Space O(n)
- Meeting Rooms II: Time O(n log n), Space O(n)

### Classic Problems

| Problem | Key Insight |
|---------|-------------|
| Merge Intervals | Sort by start, extend end if overlap |
| Insert Interval | Three phases: before, overlap, after |
| Meeting Rooms (can attend all?) | Sort, check any overlap |
| Meeting Rooms II (min rooms) | Sort starts/ends separately, two pointer |
| Non-overlapping Intervals | Sort by end, greedily remove overlaps |
| Interval List Intersections | Two pointer on sorted interval lists |
| Employee Free Time | Merge all busy, find gaps |

---

## Pattern 15: Matrix Patterns

### When to Use
- **Spiral** traversal
- **Rotate** matrix 90°
- **Search** in sorted 2D matrix
- **Set matrix zeroes** (mark and update)
- Diagonal traversal, transpose

### Template

```java
// Template 1: Spiral Order Traversal
public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> result = new ArrayList<>();
    int top = 0, bottom = matrix.length - 1;
    int left = 0, right = matrix[0].length - 1;
    
    while (top <= bottom && left <= right) {
        // Traverse right
        for (int j = left; j <= right; j++) result.add(matrix[top][j]);
        top++;
        
        // Traverse down
        for (int i = top; i <= bottom; i++) result.add(matrix[i][right]);
        right--;
        
        // Traverse left
        if (top <= bottom) {
            for (int j = right; j >= left; j--) result.add(matrix[bottom][j]);
            bottom--;
        }
        
        // Traverse up
        if (left <= right) {
            for (int i = bottom; i >= top; i--) result.add(matrix[i][left]);
            left++;
        }
    }
    return result;
}
```

```java
// Template 2: Rotate Matrix 90° Clockwise (in-place)
public void rotate(int[][] matrix) {
    int n = matrix.length;
    
    // Step 1: Transpose (swap matrix[i][j] with matrix[j][i])
    for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
            int temp = matrix[i][j];
            matrix[i][j] = matrix[j][i];
            matrix[j][i] = temp;
        }
    }
    
    // Step 2: Reverse each row
    for (int i = 0; i < n; i++) {
        int left = 0, right = n - 1;
        while (left < right) {
            int temp = matrix[i][left];
            matrix[i][left] = matrix[i][right];
            matrix[i][right] = temp;
            left++;
            right--;
        }
    }
}
```

```java
// Template 3: Search in Sorted 2D Matrix (staircase search)
// Each row sorted, first element of each row > last of previous row
public boolean searchMatrix(int[][] matrix, int target) {
    int rows = matrix.length, cols = matrix[0].length;
    int row = 0, col = cols - 1; // start top-right
    
    while (row < rows && col >= 0) {
        if (matrix[row][col] == target) return true;
        else if (matrix[row][col] > target) col--;
        else row++;
    }
    return false;
}
```

```java
// Template 4: Set Matrix Zeroes (O(1) space using first row/col as markers)
public void setZeroes(int[][] matrix) {
    int rows = matrix.length, cols = matrix[0].length;
    boolean firstRowZero = false, firstColZero = false;
    
    // Check if first row/col should be zeroed
    for (int j = 0; j < cols; j++) if (matrix[0][j] == 0) firstRowZero = true;
    for (int i = 0; i < rows; i++) if (matrix[i][0] == 0) firstColZero = true;
    
    // Mark zeros in first row/col
    for (int i = 1; i < rows; i++) {
        for (int j = 1; j < cols; j++) {
            if (matrix[i][j] == 0) {
                matrix[i][0] = 0;
                matrix[0][j] = 0;
            }
        }
    }
    
    // Zero out cells based on markers
    for (int i = 1; i < rows; i++) {
        for (int j = 1; j < cols; j++) {
            if (matrix[i][0] == 0 || matrix[0][j] == 0) {
                matrix[i][j] = 0;
            }
        }
    }
    
    // Handle first row and column
    if (firstRowZero) Arrays.fill(matrix[0], 0);
    if (firstColZero) for (int i = 0; i < rows; i++) matrix[i][0] = 0;
}
```

### Complexity
- Spiral/Rotate/Set Zeroes: Time O(m × n), Space O(1) in-place
- Search sorted matrix: Time O(m + n), Space O(1)

### Classic Problems

| Problem | Key Insight |
|---------|-------------|
| Spiral Matrix | Four boundaries: top, bottom, left, right |
| Rotate Image | Transpose + reverse rows (90° CW) |
| Search 2D Matrix | Staircase from top-right or binary search |
| Set Matrix Zeroes | Use first row/col as markers |
| Game of Life | Encode states as multi-bit values |
| Word Search | Grid DFS + backtracking |
| Valid Sudoku | HashSet per row, col, and 3×3 box |

---

## Pattern 16: Binary Tree Patterns

### When to Use
- **BST** operations (search, insert, validate, Kth smallest)
- **Lowest Common Ancestor** (LCA)
- Tree **construction** from traversals
- **Path sum** problems
- **Serialization** / deserialization
- Tree **diameter**, **width**, or **symmetry**

### Template

```java
// Template 1: Validate BST (inorder bounds)
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    if (node == null) return true;
    if (node.val <= min || node.val >= max) return false;
    return validate(node.left, min, node.val) 
        && validate(node.right, node.val, max);
}
```

```java
// Template 2: Lowest Common Ancestor (LCA) — Binary Tree
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    
    if (left != null && right != null) return root; // found in both subtrees
    return left != null ? left : right;
}
```

```java
// Template 3: LCA in BST (use BST property)
public TreeNode lcaBST(TreeNode root, TreeNode p, TreeNode q) {
    while (root != null) {
        if (p.val < root.val && q.val < root.val) root = root.left;
        else if (p.val > root.val && q.val > root.val) root = root.right;
        else return root; // split point = LCA
    }
    return null;
}
```

```java
// Template 4: Path Sum (root-to-leaf = target)
public boolean hasPathSum(TreeNode root, int targetSum) {
    if (root == null) return false;
    if (root.left == null && root.right == null) return targetSum == root.val;
    
    return hasPathSum(root.left, targetSum - root.val) 
        || hasPathSum(root.right, targetSum - root.val);
}
```

```java
// Template 5: Path Sum III (any downward path = target, using prefix sum)
public int pathSum(TreeNode root, int targetSum) {
    Map<Long, Integer> prefixMap = new HashMap<>();
    prefixMap.put(0L, 1);
    return dfs(root, 0, targetSum, prefixMap);
}

private int dfs(TreeNode node, long currSum, int target, Map<Long, Integer> prefixMap) {
    if (node == null) return 0;
    
    currSum += node.val;
    int count = prefixMap.getOrDefault(currSum - target, 0);
    
    prefixMap.merge(currSum, 1, Integer::sum);
    count += dfs(node.left, currSum, target, prefixMap);
    count += dfs(node.right, currSum, target, prefixMap);
    prefixMap.merge(currSum, -1, Integer::sum); // backtrack
    
    return count;
}
```

```java
// Template 6: Diameter of Binary Tree (longest path between any two nodes)
private int diameter = 0;

public int diameterOfBinaryTree(TreeNode root) {
    height(root);
    return diameter;
}

private int height(TreeNode node) {
    if (node == null) return 0;
    int left = height(node.left);
    int right = height(node.right);
    diameter = Math.max(diameter, left + right); // update diameter
    return 1 + Math.max(left, right);
}
```

```java
// Template 7: Construct Binary Tree from Preorder and Inorder
public TreeNode buildTree(int[] preorder, int[] inorder) {
    Map<Integer, Integer> inMap = new HashMap<>();
    for (int i = 0; i < inorder.length; i++) inMap.put(inorder[i], i);
    return build(preorder, 0, preorder.length - 1, 0, inorder.length - 1, inMap);
}

private int preIdx = 0;

private TreeNode build(int[] preorder, int preStart, int preEnd, 
                       int inStart, int inEnd, Map<Integer, Integer> inMap) {
    if (preStart > preEnd || inStart > inEnd) return null;
    
    TreeNode root = new TreeNode(preorder[preStart]);
    int inRoot = inMap.get(root.val);
    int leftSize = inRoot - inStart;
    
    root.left = build(preorder, preStart + 1, preStart + leftSize, 
                      inStart, inRoot - 1, inMap);
    root.right = build(preorder, preStart + leftSize + 1, preEnd, 
                       inRoot + 1, inEnd, inMap);
    return root;
}
```

```java
// Template 8: Kth Smallest in BST (inorder traversal)
public int kthSmallest(TreeNode root, int k) {
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    
    while (curr != null || !stack.isEmpty()) {
        while (curr != null) {
            stack.push(curr);
            curr = curr.left;
        }
        curr = stack.pop();
        k--;
        if (k == 0) return curr.val;
        curr = curr.right;
    }
    return -1;
}
```

```java
// Template 9: Serialize / Deserialize Binary Tree (preorder)
public String serialize(TreeNode root) {
    if (root == null) return "null";
    return root.val + "," + serialize(root.left) + "," + serialize(root.right);
}

public TreeNode deserialize(String data) {
    Queue<String> nodes = new LinkedList<>(Arrays.asList(data.split(",")));
    return buildFromQueue(nodes);
}

private TreeNode buildFromQueue(Queue<String> nodes) {
    String val = nodes.poll();
    if (val.equals("null")) return null;
    TreeNode node = new TreeNode(Integer.parseInt(val));
    node.left = buildFromQueue(nodes);
    node.right = buildFromQueue(nodes);
    return node;
}
```

```java
// Template 10: Symmetric Tree / Mirror Check
public boolean isSymmetric(TreeNode root) {
    return isMirror(root.left, root.right);
}

private boolean isMirror(TreeNode t1, TreeNode t2) {
    if (t1 == null && t2 == null) return true;
    if (t1 == null || t2 == null) return false;
    return t1.val == t2.val 
        && isMirror(t1.left, t2.right) 
        && isMirror(t1.right, t2.left);
}
```

### Tree Pattern Categories

```
┌─────────────────────────────────────────────────────────┐
│              BINARY TREE PROBLEM CATEGORIES               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Traversal            BST Operations     Construction    │
│  ─────────            ──────────────     ────────────    │
│  Inorder (LNR)        Validate BST       From Preorder  │
│  Preorder (NLR)       Search/Insert      + Inorder      │
│  Postorder (LRN)      Kth Smallest       From Preorder  │
│  Level Order (BFS)    LCA in BST         + Postorder    │
│  Zigzag Level Order   Floor/Ceiling      Serialize/     │
│  Vertical Order       Delete Node        Deserialize    │
│                                                          │
│  Path Problems        Structure           Properties    │
│  ─────────────        ─────────           ──────────    │
│  Path Sum (root→leaf) Invert Tree         Max Depth     │
│  Path Sum III (any)   Flatten to LL       Balanced?     │
│  Max Path Sum         Symmetric?          Same Tree?    │
│  Diameter             Right Side View     Subtree?      │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Complexity
- Most tree operations: Time O(n), Space O(h) where h = height
- BST search/insert/delete: O(h) → O(log n) balanced, O(n) skewed
- Level order: Time O(n), Space O(w) where w = max width

### Classic Problems

| Problem | Category | Key Insight |
|---------|----------|-------------|
| Validate BST | BST | Bounds [min, max] passed down |
| LCA of Binary Tree | Path/Ancestor | Post-order: found in both subtrees = ancestor |
| LCA of BST | BST | Split point where p and q diverge |
| Diameter of Tree | Path | Height function, track left+right |
| Max Path Sum | Path | At each node: max(left,0) + max(right,0) + node.val |
| Kth Smallest in BST | BST/Traversal | Inorder gives sorted order |
| Construct from Pre+In | Construction | Root from preorder, split using inorder |
| Serialize/Deserialize | Construction | Preorder with null markers |
| Symmetric Tree | Structure | Mirror comparison (left.left↔right.right) |
| Invert Binary Tree | Structure | Swap left and right recursively |
| Flatten to Linked List | Structure | Preorder: right = flattened left, then right |
| Binary Tree Right Side View | BFS | Last node at each level |
| Path Sum III | Prefix Sum + DFS | Prefix sum map in tree DFS |

---

## Master Pattern Selection Guide

```
READ THE PROBLEM STATEMENT AND ASK:

1. Is the input SORTED or can it be sorted?
   ├── YES + finding pairs/triplets → TWO POINTERS
   ├── YES + searching for target → BINARY SEARCH
   └── YES + minimize max / maximize min → BINARY SEARCH ON ANSWER

2. Is it about SUBARRAY or SUBSTRING (contiguous)?
   ├── Fixed size window → SLIDING WINDOW (fixed)
   ├── Longest/shortest valid window → SLIDING WINDOW (variable)
   └── Sum of subarray = K → PREFIX SUM + HASH MAP

3. Do I need O(1) LOOKUP or COUNTING?
   └── YES → HASH MAP / HASH SET

4. Is it a TREE or GRAPH problem?
   ├── Traversal / path finding → DFS
   ├── Shortest path (unweighted) → BFS
   ├── Connected components → DFS / BFS / UNION-FIND
   ├── Topological order → TOPOLOGICAL SORT (BFS/DFS)
   └── Cycle detection → DFS (coloring) / UNION-FIND

5. Is it asking for OPTIMAL VALUE (min/max/count)?
   ├── Overlapping subproblems → DP
   ├── Take/skip decisions → DP (house robber style)
   ├── Sequence comparison → DP (LCS/Edit Distance)
   └── Can I form target from items? → KNAPSACK DP

6. Need NEXT GREATER/SMALLER element?
   └── MONOTONIC STACK

7. Generate ALL combinations/permutations/subsets?
   └── BACKTRACKING (DFS)

8. Top K / streaming min-max / merge K sorted?
   └── HEAP / PRIORITY QUEUE

9. Prefix matching, autocomplete, dictionary search?
   └── TRIE

10. Locally optimal → globally optimal? Intervals? Scheduling?
    └── GREEDY

11. Split problem into halves? Kth element? Merge-sort style?
    └── DIVIDE AND CONQUER

12. Linked list with cycle / middle / nth from end?
    └── FAST-SLOW POINTERS

13. XOR tricks? Single number? Power of 2? Bit counting?
    └── BIT MANIPULATION

14. Overlapping intervals? Merge/insert/meeting rooms?
    └── INTERVAL MERGING

15. 2D matrix traversal? Spiral? Rotate? Search?
    └── MATRIX PATTERNS

16. BST validation? LCA? Path sums? Tree construction?
    └── BINARY TREE PATTERNS
```

---

## Time Complexity Quick Reference

| Pattern | Typical Time | Typical Space |
|---------|-------------|---------------|
| Two Pointers | O(n) | O(1) |
| Sliding Window | O(n) | O(k) |
| Hash Map/Set | O(n) | O(n) |
| Binary Search | O(log n) | O(1) |
| DFS/BFS (graph) | O(V + E) | O(V) |
| DFS/BFS (grid) | O(m × n) | O(m × n) |
| DP (1D) | O(n) | O(n) or O(1) |
| DP (2D) | O(m × n) | O(m × n) |
| Backtracking | O(2^n) or O(n!) | O(n) |
| Monotonic Stack | O(n) | O(n) |
| Union-Find | O(α(n)) ≈ O(1) per op | O(n) |
| Topological Sort | O(V + E) | O(V + E) |
| Prefix Sum | O(n) build, O(1) query | O(n) |
| Heap / Priority Queue | O(n log k) | O(k) |
| Trie | O(m) per word, m=length | O(total chars × 26) |
| Greedy | O(n log n) with sort | O(1) to O(n) |
| Divide and Conquer | O(n log n) | O(n) or O(log n) |
| Fast/Slow Pointers (LL) | O(n) | O(1) |
| Bit Manipulation | O(n) | O(1) |
| Interval Merging | O(n log n) | O(n) |
| Matrix Patterns | O(m × n) | O(1) in-place |
| Binary Tree Patterns | O(n) | O(h), h=height |

---

## Related Topics

- [02. Arrays & Strings](./02-arrays-and-strings.md)
- [03. Linked Lists](./03-linked-lists.md)
- [04. Trees & Graphs](./04-trees-and-graphs.md)
- [05. Dynamic Programming Deep Dive](./05-dynamic-programming.md)

