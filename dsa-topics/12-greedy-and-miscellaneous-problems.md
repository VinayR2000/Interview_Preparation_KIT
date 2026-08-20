# 12. Greedy, Bit Manipulation & Miscellaneous — Must-Solve Problems ⭐⭐⭐

---

## Greedy Problems

---

### Problem 1: Jump Game (NeetCode #55) ⭐⭐

### Problem
Given an array where `nums[i]` represents max jump length from position `i`, determine if you can reach the last index.

### Approach: Track farthest reachable index

### Solution

```java
public boolean canJump(int[] nums) {
    int farthest = 0;
    
    for (int i = 0; i < nums.length; i++) {
        if (i > farthest) return false; // can't reach this position
        farthest = Math.max(farthest, i + nums[i]);
        if (farthest >= nums.length - 1) return true;
    }
    return true;
}
```

### Dry Run

```
Input: nums = [2, 3, 1, 1, 4]

i=0: 0 <= farthest(0) ✓, farthest = max(0, 0+2) = 2
i=1: 1 <= 2 ✓, farthest = max(2, 1+3) = 4 >= 4 → return true!

Input: nums = [3, 2, 1, 0, 4]
i=0: farthest = 3
i=1: farthest = max(3, 3) = 3
i=2: farthest = max(3, 3) = 3
i=3: farthest = max(3, 3) = 3
i=4: 4 > 3 → return false!

Output: true / false
```

### Complexity
- Time: O(n), Space: O(1)

---

### Problem 2: Jump Game II (NeetCode #45) ⭐⭐

### Problem
Minimum number of jumps to reach the last index.

### Solution

```java
public int jump(int[] nums) {
    int jumps = 0, currentEnd = 0, farthest = 0;
    
    for (int i = 0; i < nums.length - 1; i++) {
        farthest = Math.max(farthest, i + nums[i]);
        
        if (i == currentEnd) { // must jump here
            jumps++;
            currentEnd = farthest;
        }
    }
    return jumps;
}
```

### Dry Run

```
Input: nums = [2, 3, 1, 1, 4]

i=0: farthest=max(0,2)=2. i==currentEnd(0) → jump! jumps=1, currentEnd=2
i=1: farthest=max(2,4)=4. i!=currentEnd
i=2: farthest=max(4,3)=4. i==currentEnd(2) → jump! jumps=2, currentEnd=4
i=3: (loop ends at nums.length-1=3... wait i<4)
i=3: farthest=max(4,4)=4. i!=currentEnd

Output: 2 (jump from 0→1→4)
```

### Complexity
- Time: O(n), Space: O(1)

---

### Problem 3: Gas Station (NeetCode #134) ⭐⭐

### Problem
There are `n` gas stations in a circle. Find the starting station index to complete the circuit, or -1 if impossible.

### Key Insight
- If total gas ≥ total cost, a solution exists
- Start from where running surplus is lowest (or reset when tank < 0)

### Solution

```java
public int canCompleteCircuit(int[] gas, int[] cost) {
    int totalSurplus = 0, currentSurplus = 0, start = 0;
    
    for (int i = 0; i < gas.length; i++) {
        int diff = gas[i] - cost[i];
        totalSurplus += diff;
        currentSurplus += diff;
        
        if (currentSurplus < 0) {
            start = i + 1;       // can't start from any index before i+1
            currentSurplus = 0;  // reset
        }
    }
    return totalSurplus >= 0 ? start : -1;
}
```

### Dry Run

```
Input: gas = [1,2,3,4,5], cost = [3,4,5,1,2]

i=0: diff=-2, total=-2, current=-2 < 0 → start=1, current=0
i=1: diff=-2, total=-4, current=-2 < 0 → start=2, current=0
i=2: diff=-2, total=-6, current=-2 < 0 → start=3, current=0
i=3: diff=3, total=-3, current=3
i=4: diff=3, total=0, current=6

totalSurplus=0 >= 0 → return start=3

Output: 3 (start at station 3)
```

### Complexity
- Time: O(n), Space: O(1)

---

### Problem 4: Hand of Straights (NeetCode #846) ⭐⭐

### Problem
Determine if hand of cards can be rearranged into groups of size `groupSize` where each group has consecutive cards.

### Solution

```java
public boolean isNStraightHand(int[] hand, int groupSize) {
    if (hand.length % groupSize != 0) return false;
    
    TreeMap<Integer, Integer> count = new TreeMap<>();
    for (int card : hand) count.merge(card, 1, Integer::sum);
    
    while (!count.isEmpty()) {
        int first = count.firstKey();
        for (int i = first; i < first + groupSize; i++) {
            if (!count.containsKey(i)) return false;
            count.merge(i, -1, Integer::sum);
            if (count.get(i) == 0) count.remove(i);
        }
    }
    return true;
}
```

### Complexity
- Time: O(n log n)
- Space: O(n)

---

## Bit Manipulation Problems

---

### Problem 5: Single Number (NeetCode #136) ⭐

### Problem
Every element appears twice except one. Find the single one. O(1) space.

### Key Insight: XOR — `a ^ a = 0`, `a ^ 0 = a`

### Solution

```java
public int singleNumber(int[] nums) {
    int result = 0;
    for (int num : nums) {
        result ^= num;
    }
    return result;
}
```

### Dry Run

```
Input: nums = [4, 1, 2, 1, 2]

result = 0
0 ^ 4 = 4
4 ^ 1 = 5
5 ^ 2 = 7
7 ^ 1 = 6
6 ^ 2 = 4

Output: 4 (pairs cancel out: 1^1=0, 2^2=0, only 4 remains)
```

### Complexity
- Time: O(n), Space: O(1)

---

### Problem 6: Number of 1 Bits (NeetCode #191) ⭐

### Solution

```java
public int hammingWeight(int n) {
    int count = 0;
    while (n != 0) {
        count += (n & 1); // check last bit
        n >>>= 1;        // unsigned right shift
    }
    return count;
}

// Optimized: Brian Kernighan's algorithm
public int hammingWeight2(int n) {
    int count = 0;
    while (n != 0) {
        n &= (n - 1); // removes lowest set bit
        count++;
    }
    return count;
}
```

### Dry Run

```
n = 11 (binary: 1011)

n & (n-1): 1011 & 1010 = 1010, count=1
n & (n-1): 1010 & 1001 = 1000, count=2
n & (n-1): 1000 & 0111 = 0000, count=3

Output: 3
```

### Complexity
- Time: O(number of 1 bits), Space: O(1)

---

### Problem 7: Counting Bits (NeetCode #338) ⭐

### Problem
For every number i in [0, n], count the number of 1 bits.

### Key Insight: `dp[i] = dp[i >> 1] + (i & 1)`

### Solution

```java
public int[] countBits(int n) {
    int[] dp = new int[n + 1];
    for (int i = 1; i <= n; i++) {
        dp[i] = dp[i >> 1] + (i & 1);
    }
    return dp;
}
```

### Dry Run

```
dp[0] = 0
dp[1] = dp[0] + 1 = 1  (binary: 1)
dp[2] = dp[1] + 0 = 1  (binary: 10)
dp[3] = dp[1] + 1 = 2  (binary: 11)
dp[4] = dp[2] + 0 = 1  (binary: 100)
dp[5] = dp[2] + 1 = 2  (binary: 101)

Output: [0, 1, 1, 2, 1, 2]
```

### Complexity
- Time: O(n), Space: O(n)

---

### Problem 8: Reverse Bits (NeetCode #190) ⭐

### Solution

```java
public int reverseBits(int n) {
    int result = 0;
    for (int i = 0; i < 32; i++) {
        result = (result << 1) | (n & 1);
        n >>= 1;
    }
    return result;
}
```

### Complexity
- Time: O(32) = O(1), Space: O(1)

---

## Math / Miscellaneous

---

### Problem 9: Rotate Image (NeetCode #48) ⭐⭐

### Problem
Rotate an n×n matrix 90 degrees clockwise in-place.

### Approach: Transpose + Reverse each row

### Solution

```java
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

### Dry Run

```
Input:  [[1,2,3],     Transpose: [[1,4,7],     Reverse: [[7,4,1],
         [4,5,6],                  [2,5,8],               [8,5,2],
         [7,8,9]]                  [3,6,9]]               [9,6,3]]

Output: [[7,4,1],[8,5,2],[9,6,3]]
```

### Complexity
- Time: O(n²), Space: O(1)

---

### Problem 10: Set Matrix Zeroes (NeetCode #73) ⭐⭐

### Problem
If an element is 0, set its entire row and column to 0. Do it in-place.

### Approach: Use first row/column as markers

### Solution

```java
public void setZeroes(int[][] matrix) {
    int m = matrix.length, n = matrix[0].length;
    boolean firstRowZero = false, firstColZero = false;
    
    // Check if first row/col should be zeroed
    for (int j = 0; j < n; j++) if (matrix[0][j] == 0) firstRowZero = true;
    for (int i = 0; i < m; i++) if (matrix[i][0] == 0) firstColZero = true;
    
    // Mark zeros in first row/col
    for (int i = 1; i < m; i++) {
        for (int j = 1; j < n; j++) {
            if (matrix[i][j] == 0) {
                matrix[i][0] = 0;
                matrix[0][j] = 0;
            }
        }
    }
    
    // Zero out cells based on markers
    for (int i = 1; i < m; i++) {
        for (int j = 1; j < n; j++) {
            if (matrix[i][0] == 0 || matrix[0][j] == 0) {
                matrix[i][j] = 0;
            }
        }
    }
    
    // Handle first row and column
    if (firstRowZero) for (int j = 0; j < n; j++) matrix[0][j] = 0;
    if (firstColZero) for (int i = 0; i < m; i++) matrix[i][0] = 0;
}
```

### Complexity
- Time: O(m × n), Space: O(1)

---

## Summary — Pattern Recognition

| Signal | Pattern | Example |
|--------|---------|---------|
| "Can you reach / complete circuit?" | Greedy (track running state) | Jump Game, Gas Station |
| "Minimum steps/jumps" | Greedy (BFS-like levels) | Jump Game II |
| "Appears once, others twice" | XOR all elements | Single Number |
| "Count bits / reverse bits" | Bit manipulation | Counting Bits, Reverse Bits |
| "Rotate/transform matrix" | Transpose + Reverse | Rotate Image |
| "Set row/col to zero" | Use first row/col as markers | Set Matrix Zeroes |
| "Rearrange into groups" | TreeMap + greedy consume | Hand of Straights |

### Bit Manipulation Cheat Sheet
```
a ^ a = 0          (XOR same = cancel)
a ^ 0 = a          (XOR zero = identity)
n & (n-1)          (remove lowest set bit)
n & 1              (check if odd / last bit)
n >> 1             (divide by 2)
n << 1             (multiply by 2)
~n                 (flip all bits)
n & (-n)           (isolate lowest set bit)
```
