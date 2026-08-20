# 16. Monotonic Stack, Segment Trees & Advanced DP ⭐⭐⭐

---

## Monotonic Stack Problems

---

### Problem 1: Next Greater Element I (NeetCode #496) ⭐⭐

### Problem
Given two arrays `nums1` (subset of `nums2`), for each element in `nums1`, find the next greater element in `nums2`. If none exists, return -1.

### Solution

```java
public int[] nextGreaterElement(int[] nums1, int[] nums2) {
    Map<Integer, Integer> nextGreater = new HashMap<>();
    Deque<Integer> stack = new ArrayDeque<>();
    
    // Process nums2 — find next greater for each element
    for (int num : nums2) {
        while (!stack.isEmpty() && stack.peek() < num) {
            nextGreater.put(stack.pop(), num);
        }
        stack.push(num);
    }
    
    // Build result for nums1
    int[] result = new int[nums1.length];
    for (int i = 0; i < nums1.length; i++) {
        result[i] = nextGreater.getOrDefault(nums1[i], -1);
    }
    return result;
}
```

### Dry Run

```
Input: nums1 = [4, 1, 2], nums2 = [1, 3, 4, 2]

Processing nums2 with stack:
  num=1: stack=[1]
  num=3: 3>1 → pop 1, map={1:3}. stack=[3]
  num=4: 4>3 → pop 3, map={1:3, 3:4}. stack=[4]
  num=2: 2<4 → stack=[4,2]

Remaining in stack: no next greater for 4 and 2.
map = {1:3, 3:4}

Result for nums1:
  4 → not in map → -1
  1 → map[1]=3
  2 → not in map → -1

Output: [-1, 3, -1]
```

### Complexity
- Time: O(n + m), Space: O(n)

---

### Problem 2: Next Greater Element II — Circular (NeetCode #503) ⭐⭐

### Problem
Given a circular array, find the next greater element for each element.

### Key Insight: Traverse array twice (simulate circular with `i % n`)

### Solution

```java
public int[] nextGreaterElements(int[] nums) {
    int n = nums.length;
    int[] result = new int[n];
    Arrays.fill(result, -1);
    Deque<Integer> stack = new ArrayDeque<>(); // stores indices
    
    // Traverse twice to handle circular
    for (int i = 0; i < 2 * n; i++) {
        int num = nums[i % n];
        while (!stack.isEmpty() && nums[stack.peek()] < num) {
            result[stack.pop()] = num;
        }
        if (i < n) stack.push(i); // only push indices from first pass
    }
    return result;
}
```

### Dry Run

```
Input: nums = [1, 2, 1]

First pass (i=0,1,2):
  i=0(1): stack=[0]
  i=1(2): 2>nums[0]=1 → pop 0, result[0]=2. stack=[1]
  i=2(1): stack=[1,2]

Second pass (i=3,4,5 → indices 0,1,2):
  i=3, nums[0]=1: 1<nums[2]=1 → no pop. (don't push)
  i=4, nums[1]=2: 2>nums[2]=1 → pop 2, result[2]=2. (don't push)
  i=5, nums[2]=1: no pop.

result[1] remains -1 (2 is the largest, no next greater)

Output: [2, -1, 2]
```

### Complexity
- Time: O(n), Space: O(n)

---

### Problem 3: Online Stock Span (NeetCode #901) ⭐⭐

### Problem
For each day's stock price, find the span (number of consecutive days with price ≤ today's price, including today).

### Solution

```java
class StockSpanner {
    Deque<int[]> stack; // [price, span]
    
    public StockSpanner() { stack = new ArrayDeque<>(); }
    
    public int next(int price) {
        int span = 1;
        while (!stack.isEmpty() && stack.peek()[0] <= price) {
            span += stack.pop()[1]; // absorb previous spans
        }
        stack.push(new int[]{price, span});
        return span;
    }
}
```

### Dry Run

```
next(100): stack=[], span=1. stack=[(100,1)]. return 1
next(80):  stack=[(100,1)], 100>80. span=1. stack=[(100,1),(80,1)]. return 1
next(60):  80>60. span=1. stack=[(100,1),(80,1),(60,1)]. return 1
next(70):  60≤70 → pop, span=1+1=2. 80>70. stack=[(100,1),(80,1),(70,2)]. return 2
next(60):  70>60. span=1. stack=[...,(60,1)]. return 1
next(75):  60≤75 → span=1+1=2. 70≤75 → span=2+2=4. 80>75. stack=[(100,1),(80,1),(75,4)]. return 4
next(85):  75≤85 → span=1+4=5. 80≤85 → span=5+1=6. 100>85. stack=[(100,1),(85,6)]. return 6
```

### Complexity
- Time: O(1) amortized per call
- Space: O(n)

---

### Problem 4: Sum of Subarray Minimums (LeetCode #907) ⭐⭐⭐

### Problem
Find the sum of min(subarray) for all subarrays of `arr`.

### Key Insight
- For each element, find how many subarrays it's the minimum of
- Use monotonic stack to find previous less element (PLE) and next less element (NLE)
- Contribution of arr[i] = arr[i] × left × right

### Solution

```java
public int sumSubarrayMins(int[] arr) {
    int MOD = 1_000_000_007;
    int n = arr.length;
    int[] left = new int[n];  // distance to previous less element
    int[] right = new int[n]; // distance to next less element
    
    Deque<Integer> stack = new ArrayDeque<>();
    
    // Find previous less or equal
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && arr[stack.peek()] >= arr[i]) stack.pop();
        left[i] = stack.isEmpty() ? i + 1 : i - stack.peek();
        stack.push(i);
    }
    
    stack.clear();
    
    // Find next less (strict)
    for (int i = n - 1; i >= 0; i--) {
        while (!stack.isEmpty() && arr[stack.peek()] > arr[i]) stack.pop();
        right[i] = stack.isEmpty() ? n - i : stack.peek() - i;
        stack.push(i);
    }
    
    // Calculate sum
    long sum = 0;
    for (int i = 0; i < n; i++) {
        sum = (sum + (long) arr[i] * left[i] * right[i]) % MOD;
    }
    return (int) sum;
}
```

### Dry Run

```
Input: arr = [3, 1, 2, 4]

left:  [1, 2, 1, 1]  (3 has 1 element to left boundary, 1 has 2, etc.)
right: [1, 3, 2, 1]  (3 has 1 to right boundary, 1 has 3 elements rightward, etc.)

Contributions:
  arr[0]=3: 3 × 1 × 1 = 3
  arr[1]=1: 1 × 2 × 3 = 6
  arr[2]=2: 2 × 1 × 2 = 4
  arr[3]=4: 4 × 1 × 1 = 4

Sum = 3 + 6 + 4 + 4 = 17

Verify: subarrays and their mins:
  [3]=3, [1]=1, [2]=2, [4]=4, [3,1]=1, [1,2]=1, [2,4]=2, [3,1,2]=1, [1,2,4]=1, [3,1,2,4]=1
  Sum = 3+1+2+4+1+1+2+1+1+1 = 17 ✓

Output: 17
```

### Complexity
- Time: O(n), Space: O(n)

---

## Segment Tree / Fenwick Tree (Binary Indexed Tree)

---

### Problem 5: Range Sum Query — Mutable (LeetCode #307) ⭐⭐⭐

### Problem
Given an array, support two operations:
1. `update(index, val)` — update element at index
2. `sumRange(left, right)` — sum of elements in range [left, right]

### Solution (Fenwick Tree / BIT)

```java
class NumArray {
    private int[] tree;
    private int[] nums;
    private int n;
    
    public NumArray(int[] nums) {
        this.n = nums.length;
        this.nums = new int[n];
        this.tree = new int[n + 1];
        for (int i = 0; i < n; i++) update(i, nums[i]);
    }
    
    public void update(int index, int val) {
        int diff = val - nums[index];
        nums[index] = val;
        index++; // BIT is 1-indexed
        while (index <= n) {
            tree[index] += diff;
            index += index & (-index); // add lowest set bit
        }
    }
    
    public int sumRange(int left, int right) {
        return prefixSum(right + 1) - prefixSum(left);
    }
    
    private int prefixSum(int index) {
        int sum = 0;
        while (index > 0) {
            sum += tree[index];
            index -= index & (-index); // remove lowest set bit
        }
        return sum;
    }
}
```

### How BIT Works

```
Index:    1    2    3    4    5    6    7    8
Binary:  001  010  011  100  101  110  111  1000

tree[1] = nums[0]                    (covers 1 element)
tree[2] = nums[0] + nums[1]          (covers 2 elements)
tree[3] = nums[2]                    (covers 1 element)
tree[4] = nums[0]+nums[1]+nums[2]+nums[3]  (covers 4 elements)
tree[5] = nums[4]                    (covers 1 element)
tree[6] = nums[4] + nums[5]          (covers 2 elements)
tree[7] = nums[6]                    (covers 1 element)
tree[8] = nums[0..7]                 (covers 8 elements)

Prefix sum query (e.g., sum of first 6):
  prefixSum(6): tree[6] + tree[4] = (nums[4]+nums[5]) + (nums[0..3])
  6 = 110 → remove LSB → 100 → remove LSB → 000 (stop)
  
Update (e.g., update index 3):
  3+1=4: 100 → add LSB → 1000 (stop at n)
  Update tree[4] and tree[8]
```

### Complexity
- Update: O(log n)
- Query: O(log n)
- Space: O(n)

---

### Problem 6: Count of Smaller Numbers After Self (LeetCode #315) ⭐⭐⭐

### Problem
For each element, count how many elements to its right are smaller.

### Solution (Merge Sort approach)

```java
public List<Integer> countSmaller(int[] nums) {
    int n = nums.length;
    int[] result = new int[n];
    int[][] indexed = new int[n][2]; // [value, original_index]
    
    for (int i = 0; i < n; i++) indexed[i] = new int[]{nums[i], i};
    
    mergeSort(indexed, result, 0, n - 1);
    
    List<Integer> list = new ArrayList<>();
    for (int r : result) list.add(r);
    return list;
}

private void mergeSort(int[][] arr, int[] result, int left, int right) {
    if (left >= right) return;
    int mid = left + (right - left) / 2;
    mergeSort(arr, result, left, mid);
    mergeSort(arr, result, mid + 1, right);
    merge(arr, result, left, mid, right);
}

private void merge(int[][] arr, int[] result, int left, int mid, int right) {
    int[][] temp = new int[right - left + 1][2];
    int i = left, j = mid + 1, k = 0;
    int rightCount = 0; // elements from right half that are smaller
    
    while (i <= mid && j <= right) {
        if (arr[j][0] < arr[i][0]) {
            rightCount++;
            temp[k++] = arr[j++];
        } else {
            result[arr[i][1]] += rightCount;
            temp[k++] = arr[i++];
        }
    }
    while (i <= mid) {
        result[arr[i][1]] += rightCount;
        temp[k++] = arr[i++];
    }
    while (j <= right) temp[k++] = arr[j++];
    
    System.arraycopy(temp, 0, arr, left, temp.length);
}
```

### Dry Run

```
Input: nums = [5, 2, 6, 1]

indexed = [(5,0), (2,1), (6,2), (1,3)]

Merge sort:
  Left half: [(5,0), (2,1)] → sorted: [(2,1), (5,0)], result[0]+=1 (2<5)
  Right half: [(6,2), (1,3)] → sorted: [(1,3), (6,2)], result[2]+=1 (1<6)
  
  Merge [(2,1),(5,0)] with [(1,3),(6,2)]:
    1<2: rightCount=1, temp=[(1,3)]
    2<5: result[1]+=1, temp=[..(2,1)]
    5<6: result[0]+=1, temp=[..(5,0)]
    6: result[2]+=1, temp=[..(6,2)]

result = [2, 1, 1, 0]

Output: [2, 1, 1, 0]
```

### Complexity
- Time: O(n log n)
- Space: O(n)

---

## Advanced DP Problems

---

### Problem 7: Burst Balloons (LeetCode #312) ⭐⭐⭐

### Problem
Given `n` balloons with values, burst all to maximize coins. Bursting balloon `i` earns `nums[i-1] * nums[i] * nums[i+1]` coins.

### Key Insight (Interval DP)
- Think in REVERSE: which balloon is burst LAST in range [left, right]?
- If balloon `k` is last in range, its neighbors are `left-1` and `right+1` (boundaries)

### Solution

```java
public int maxCoins(int[] nums) {
    int n = nums.length;
    // Add boundary balloons with value 1
    int[] vals = new int[n + 2];
    vals[0] = vals[n + 1] = 1;
    for (int i = 0; i < n; i++) vals[i + 1] = nums[i];
    
    // dp[i][j] = max coins from bursting all balloons in range (i, j) exclusive
    int[][] dp = new int[n + 2][n + 2];
    
    // Fill by increasing length
    for (int len = 1; len <= n; len++) {
        for (int left = 0; left + len + 1 <= n + 1; left++) {
            int right = left + len + 1;
            
            for (int k = left + 1; k < right; k++) {
                // k is the LAST balloon burst in range (left, right)
                int coins = vals[left] * vals[k] * vals[right]
                          + dp[left][k] + dp[k][right];
                dp[left][right] = Math.max(dp[left][right], coins);
            }
        }
    }
    return dp[0][n + 1];
}
```

### Dry Run

```
Input: nums = [3, 1, 5, 8]
vals = [1, 3, 1, 5, 8, 1]

dp[i][j] = max coins in open interval (i, j)

len=1 (single balloons):
  dp[0][2]: k=1 → 1*3*1 = 3. dp[0][2]=3
  dp[1][3]: k=2 → 3*1*5 = 15. dp[1][3]=15
  dp[2][4]: k=3 → 1*5*8 = 40. dp[2][4]=40
  dp[3][5]: k=4 → 5*8*1 = 40. dp[3][5]=40

len=2:
  dp[0][3]: k=1 → 1*3*5 + dp[1][3] = 15+15=30
            k=2 → 1*1*5 + dp[0][2] + 0 = 5+3=8. dp[0][3]=30
  dp[1][4]: k=2 → 3*1*8 + 0 + dp[2][4] = 24+40=64
            k=3 → 3*5*8 + dp[1][3] + 0 = 120+15=135. dp[1][4]=135
  dp[2][5]: k=3 → 1*5*1 + 0 + dp[3][5] = 5+40=45
            k=4 → 1*8*1 + dp[2][4] + 0 = 8+40=48. dp[2][5]=48

len=3:
  dp[0][4]: k=1 → 1*3*8 + dp[1][4] = 24+135=159
            k=2 → 1*1*8 + dp[0][2] + dp[2][4] = 8+3+40=51
            k=3 → 1*5*8 + dp[0][3] + dp[3][4]... dp[0][4]=159
  dp[1][5]: k=2 → 3*1*1 + dp[2][5] = 3+48=51
            k=3 → 3*5*1 + dp[1][3] + dp[3][5] = 15+15+40=70
            k=4 → 3*8*1 + dp[1][4] = 24+135=159. dp[1][5]=159

len=4:
  dp[0][5]: k=1 → 1*3*1 + dp[1][5] = 3+159=162
            k=2 → 1*1*1 + dp[0][2] + dp[2][5] = 1+3+48=52
            k=3 → 1*5*1 + dp[0][3] + dp[3][5] = 5+30+40=75
            k=4 → 1*8*1 + dp[0][4] + dp[4][5] = 8+159+0=167. dp[0][5]=167

Output: 167
```

### Complexity
- Time: O(n³)
- Space: O(n²)

---

### Problem 8: Longest Increasing Path in a Matrix (LeetCode #329) ⭐⭐⭐

### Problem
Find the longest strictly increasing path in a matrix (move in 4 directions).

### Approach: DFS + Memoization

### Solution

```java
public int longestIncreasingPath(int[][] matrix) {
    int rows = matrix.length, cols = matrix[0].length;
    int[][] memo = new int[rows][cols];
    int maxPath = 0;
    
    for (int i = 0; i < rows; i++) {
        for (int j = 0; j < cols; j++) {
            maxPath = Math.max(maxPath, dfs(matrix, memo, i, j));
        }
    }
    return maxPath;
}

private int dfs(int[][] matrix, int[][] memo, int r, int c) {
    if (memo[r][c] != 0) return memo[r][c];
    
    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
    int maxLen = 1;
    
    for (int[] dir : dirs) {
        int nr = r + dir[0], nc = c + dir[1];
        if (nr >= 0 && nr < matrix.length && nc >= 0 && nc < matrix[0].length
            && matrix[nr][nc] > matrix[r][c]) {
            maxLen = Math.max(maxLen, 1 + dfs(matrix, memo, nr, nc));
        }
    }
    memo[r][c] = maxLen;
    return maxLen;
}
```

### Dry Run

```
Input: [[9, 9, 4],
        [6, 6, 8],
        [2, 1, 1]]

Starting from (2,1) value=1:
  → (2,0) value=2: → (1,0) value=6: → (0,0) value=9. Length=4
  
Path: 1 → 2 → 6 → 9. Length = 4

Output: 4
```

### Complexity
- Time: O(m × n) — each cell computed once
- Space: O(m × n)

---

### Problem 9: Matrix Chain Multiplication / Minimum Cost Tree From Leaf Values (LeetCode #1130) ⭐⭐

### Problem
Build a tree where leaves are elements of `arr`, each non-leaf = product of the max of its left and right subtree leaves. Minimize the sum of all non-leaf values.

### Solution (Stack-based greedy — O(n))

```java
public int mctFromLeafValues(int[] arr) {
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(Integer.MAX_VALUE); // sentinel
    int result = 0;
    
    for (int num : arr) {
        while (stack.peek() <= num) {
            int mid = stack.pop();
            result += mid * Math.min(stack.peek(), num);
        }
        stack.push(num);
    }
    
    // Multiply remaining elements in stack
    while (stack.size() > 2) {
        result += stack.pop() * stack.peek();
    }
    return result;
}
```

### Complexity
- Time: O(n), Space: O(n)

---

### Problem 10: Regular Expression Matching (LeetCode #10) ⭐⭐⭐

### Problem
Implement regex with `.` (any single char) and `*` (zero or more of preceding char).

### Solution (2D DP)

```java
public boolean isMatch(String s, String p) {
    int m = s.length(), n = p.length();
    boolean[][] dp = new boolean[m + 1][n + 1];
    dp[0][0] = true;
    
    // Handle patterns like a*, a*b*, a*b*c* matching empty string
    for (int j = 2; j <= n; j++) {
        if (p.charAt(j - 1) == '*') {
            dp[0][j] = dp[0][j - 2]; // * means zero occurrences
        }
    }
    
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            char sc = s.charAt(i - 1), pc = p.charAt(j - 1);
            
            if (pc == '.' || pc == sc) {
                dp[i][j] = dp[i - 1][j - 1]; // chars match
            } else if (pc == '*') {
                char prev = p.charAt(j - 2);
                // Zero occurrences of prev
                dp[i][j] = dp[i][j - 2];
                // One or more occurrences (if prev matches current char)
                if (prev == '.' || prev == sc) {
                    dp[i][j] = dp[i][j] || dp[i - 1][j];
                }
            }
        }
    }
    return dp[m][n];
}
```

### Dry Run

```
Input: s = "aab", p = "c*a*b"

     ""   c   *   a   *   b
""   T    F   T   F   T   F
a    F    F   F   T   T   F
a    F    F   F   F   T   F
b    F    F   F   F   F   T

dp[0][2]: p[1]='*' → dp[0][0]=T (c* matches empty)
dp[0][4]: p[3]='*' → dp[0][2]=T (c*a* matches empty)
dp[1][3]: p='a', s='a' match → dp[0][2]=T
dp[1][4]: p='*', prev='a', 'a'=='a' → dp[0][4]=T || dp[1][4-2]... dp[1][4]=T
dp[2][4]: p='*', prev='a', 'a'=='a' → dp[2][2]=F || dp[1][4]=T → dp[2][4]=T
dp[3][5]: p='b', s='b' match → dp[2][4]=T → dp[3][5]=T

Output: true (c*=empty, a*=aa, b=b → "aab")
```

### Complexity
- Time: O(m × n)
- Space: O(m × n)

---

## Summary — When These Come Up

| Problem | When Asked | Companies |
|---------|-----------|-----------|
| Next Greater Element I/II | Stack warmup, monotonic pattern | Amazon, Bloomberg |
| Stock Span | Real-time data processing | Goldman Sachs, Amazon |
| Sum of Subarray Minimums | Contribution technique | Google, Amazon |
| Range Sum (BIT/Segment Tree) | Range queries with updates | Google, Meta |
| Count Smaller After Self | Merge sort / BIT | Google, Amazon |
| Burst Balloons | Interval DP | Google |
| Longest Increasing Path (Matrix) | DFS + Memo | Google, Meta |
| Regular Expression Matching | 2D DP with wildcards | Google, Meta, Amazon |

### Monotonic Stack Cheat Sheet
```
Monotonic DECREASING stack → finds NEXT GREATER element
Monotonic INCREASING stack → finds NEXT SMALLER element

Use when: "for each element, find nearest larger/smaller to left/right"

Pattern:
  for (int i = 0; i < n; i++) {
      while (!stack.isEmpty() && condition(stack.peek(), nums[i])) {
          int popped = stack.pop();
          result[popped] = nums[i]; // nums[i] is the answer for popped
      }
      stack.push(i);
  }
```

### Interval DP Template
```
// dp[i][j] = optimal value for range [i, j]
for (int len = 2; len <= n; len++) {
    for (int i = 0; i + len - 1 < n; i++) {
        int j = i + len - 1;
        for (int k = i; k < j; k++) {  // split point
            dp[i][j] = optimal(dp[i][j], dp[i][k] + dp[k+1][j] + cost(i,k,j));
        }
    }
}
```
