# 9. Dynamic Programming — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Climbing Stairs (NeetCode #70) ⭐

### Problem
You can climb 1 or 2 steps at a time. How many distinct ways to climb n steps?

### Recurrence: `dp[i] = dp[i-1] + dp[i-2]` (Fibonacci)

### Solution

```java
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

### Dry Run

```
n=5:
  i=3: curr=2+1=3, prev2=2, prev1=3
  i=4: curr=3+2=5, prev2=3, prev1=5
  i=5: curr=5+3=8, prev2=5, prev1=8

Output: 8
Ways: [1+1+1+1+1, 1+1+1+2, 1+1+2+1, 1+2+1+1, 2+1+1+1, 1+2+2, 2+1+2, 2+2+1]
```

### Complexity
- Time: O(n), Space: O(1)

---

## Problem 2: House Robber (NeetCode #198) ⭐⭐

### Problem
Rob houses along a street. Can't rob two adjacent houses. Maximize total money.

### Recurrence: `dp[i] = max(dp[i-1], dp[i-2] + nums[i])`
- Skip current: take previous best
- Rob current: take best from 2 houses back + current

### Solution

```java
public int rob(int[] nums) {
    if (nums.length == 1) return nums[0];
    
    int prev2 = 0, prev1 = 0;
    
    for (int num : nums) {
        int curr = Math.max(prev1, prev2 + num);
        prev2 = prev1;
        prev1 = curr;
    }
    return prev1;
}
```

### Dry Run

```
Input: nums = [2, 7, 9, 3, 1]

num=2: curr=max(0, 0+2)=2, prev2=0, prev1=2
num=7: curr=max(2, 0+7)=7, prev2=2, prev1=7
num=9: curr=max(7, 2+9)=11, prev2=7, prev1=11
num=3: curr=max(11, 7+3)=11, prev2=11, prev1=11  (skip 3, not worth it)
       Actually: max(11, 7+3)=11 vs 10 → 11
num=1: curr=max(11, 11+1)=12, prev2=11, prev1=12

Output: 12 (rob houses 0,2,4: 2+9+1=12)
```

### Complexity
- Time: O(n), Space: O(1)

---

## Problem 3: Coin Change (NeetCode #322) ⭐⭐⭐

### Problem
Given coin denominations and a total amount, find the fewest coins needed. Return -1 if impossible.

### Recurrence: `dp[amount] = min(dp[amount - coin] + 1)` for each coin

### Solution

```java
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1); // impossible value
    dp[0] = 0;
    
    for (int i = 1; i <= amount; i++) {
        for (int coin : coins) {
            if (coin <= i) {
                dp[i] = Math.min(dp[i], dp[i - coin] + 1);
            }
        }
    }
    return dp[amount] > amount ? -1 : dp[amount];
}
```

### Dry Run

```
Input: coins = [1, 3, 4], amount = 6

dp[0]=0
dp[1]: min(dp[1-1]+1)=min(dp[0]+1)=1. Using coin 1.
dp[2]: min(dp[2-1]+1)=min(dp[1]+1)=2. Using coin 1.
dp[3]: min(dp[3-1]+1, dp[3-3]+1)=min(3, 1)=1. Using coin 3.
dp[4]: min(dp[4-1]+1, dp[4-3]+1, dp[4-4]+1)=min(2, 2, 1)=1. Using coin 4.
dp[5]: min(dp[5-1]+1, dp[5-3]+1, dp[5-4]+1)=min(2, 3, 2)=2. Using coins 1+4.
dp[6]: min(dp[6-1]+1, dp[6-3]+1, dp[6-4]+1)=min(3, 2, 3)=2. Using coins 3+3.

Output: 2 (coins: 3+3)
```

### Complexity
- Time: O(amount × coins)
- Space: O(amount)

---

## Problem 4: Longest Increasing Subsequence (NeetCode #300) ⭐⭐⭐

### Problem
Find the length of the longest strictly increasing subsequence.

### Solution (O(n log n) — Binary Search + Patience Sort)

```java
public int lengthOfLIS(int[] nums) {
    List<Integer> tails = new ArrayList<>();
    
    for (int num : nums) {
        int pos = Collections.binarySearch(tails, num);
        if (pos < 0) pos = -(pos + 1); // insertion point
        
        if (pos == tails.size()) {
            tails.add(num); // extend LIS
        } else {
            tails.set(pos, num); // replace with smaller value
        }
    }
    return tails.size();
}
```

### Dry Run

```
Input: nums = [10, 9, 2, 5, 3, 7, 101, 18]

num=10: tails=[], pos=0, add → tails=[10]
num=9:  pos=0 (9<10), replace → tails=[9]
num=2:  pos=0 (2<9), replace → tails=[2]
num=5:  pos=1 (5>2), add → tails=[2,5]
num=3:  pos=1 (3 replaces 5), → tails=[2,3]
num=7:  pos=2, add → tails=[2,3,7]
num=101: pos=3, add → tails=[2,3,7,101]
num=18: pos=3 (18 replaces 101) → tails=[2,3,7,18]

Output: 4 (LIS length, e.g., [2,3,7,101] or [2,3,7,18])
```

### Note: `tails` is NOT the actual LIS, just tracks the smallest possible tails.

### O(n²) solution (clearer for interviews):

```java
public int lengthOfLIS(int[] nums) {
    int[] dp = new int[nums.length];
    Arrays.fill(dp, 1);
    int maxLen = 1;
    
    for (int i = 1; i < nums.length; i++) {
        for (int j = 0; j < i; j++) {
            if (nums[j] < nums[i]) {
                dp[i] = Math.max(dp[i], dp[j] + 1);
            }
        }
        maxLen = Math.max(maxLen, dp[i]);
    }
    return maxLen;
}
```

### Complexity
- O(n log n): Binary search approach
- O(n²): DP approach

---

## Problem 5: Longest Common Subsequence (NeetCode #1143) ⭐⭐⭐

### Problem
Find the length of the longest subsequence common to both strings.

### Recurrence
```
if s1[i] == s2[j]: dp[i][j] = dp[i-1][j-1] + 1 (both match, extend)
else:              dp[i][j] = max(dp[i-1][j], dp[i][j-1]) (skip one char)
```

### Solution

```java
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

### Dry Run

```
Input: text1 = "abcde", text2 = "ace"

     ""  a  c  e
  ""  0  0  0  0
  a   0  1  1  1
  b   0  1  1  1
  c   0  1  2  2
  d   0  1  2  2
  e   0  1  2  3

dp[5][3] = 3 → LCS = "ace"
```

### Complexity
- Time: O(m × n)
- Space: O(m × n), can optimize to O(n)

---

## Problem 6: Word Break (NeetCode #139) ⭐⭐⭐

### Problem
Given a string `s` and a dictionary `wordDict`, determine if `s` can be segmented into dictionary words.

### Recurrence: `dp[i] = true if dp[j] && s[j..i] in dict` for some j < i

### Solution

```java
public boolean wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);
    int n = s.length();
    boolean[] dp = new boolean[n + 1];
    dp[0] = true; // empty string
    
    for (int i = 1; i <= n; i++) {
        for (int j = 0; j < i; j++) {
            if (dp[j] && dict.contains(s.substring(j, i))) {
                dp[i] = true;
                break;
            }
        }
    }
    return dp[n];
}
```

### Dry Run

```
Input: s = "leetcode", wordDict = ["leet", "code"]

dp[0]=true
dp[1]: "l"∉dict → false
dp[2]: "le"∉dict → false
dp[3]: "lee"∉dict → false
dp[4]: dp[0]=true && "leet"∈dict → dp[4]=true!
dp[5]: dp[4]=true && "c"∉dict; dp[0..3] && substrings → false
dp[6]: false
dp[7]: false
dp[8]: dp[4]=true && "code"∈dict → dp[8]=true!

Output: true ("leet" + "code")
```

### Complexity
- Time: O(n² × k) where k = average word length for substring comparison
- Space: O(n)

---

## Problem 7: 0/1 Knapsack / Partition Equal Subset Sum (NeetCode #416) ⭐⭐⭐

### Problem
Given an array `nums`, determine if it can be partitioned into two subsets with equal sum.

### Approach: 0/1 Knapsack where target = totalSum / 2

### Solution

```java
public boolean canPartition(int[] nums) {
    int sum = 0;
    for (int num : nums) sum += num;
    if (sum % 2 != 0) return false;
    
    int target = sum / 2;
    boolean[] dp = new boolean[target + 1];
    dp[0] = true;
    
    for (int num : nums) {
        // Traverse backward to avoid using same element twice
        for (int j = target; j >= num; j--) {
            dp[j] = dp[j] || dp[j - num];
        }
    }
    return dp[target];
}
```

### Dry Run

```
Input: nums = [1, 5, 11, 5], sum=22, target=11

dp = [T, F, F, F, F, F, F, F, F, F, F, F]

num=1:  dp[1]=dp[1]||dp[0]=T → dp=[T,T,F,F,F,F,F,F,F,F,F,F]
num=5:  dp[6]=dp[6]||dp[1]=T, dp[5]=dp[5]||dp[0]=T
        → dp=[T,T,F,F,F,T,T,F,F,F,F,F]
num=11: dp[11]=dp[11]||dp[0]=T!
        → dp=[T,T,F,F,F,T,T,F,F,F,F,T]

dp[11]=true → return true

Output: true (subset {1,5,5} sums to 11)
```

### Complexity
- Time: O(n × target)
- Space: O(target)

---

## Problem 8: Edit Distance (NeetCode #72) ⭐⭐⭐

### Problem
Find minimum operations (insert, delete, replace) to convert word1 to word2.

### Recurrence
```
if word1[i] == word2[j]: dp[i][j] = dp[i-1][j-1] (no operation)
else: dp[i][j] = 1 + min(dp[i-1][j-1],  // replace
                         dp[i-1][j],      // delete from word1
                         dp[i][j-1])      // insert into word1
```

### Solution

```java
public int minDistance(String word1, String word2) {
    int m = word1.length(), n = word2.length();
    int[][] dp = new int[m + 1][n + 1];
    
    // Base cases: converting to/from empty string
    for (int i = 0; i <= m; i++) dp[i][0] = i;
    for (int j = 0; j <= n; j++) dp[0][j] = j;
    
    for (int i = 1; i <= m; i++) {
        for (int j = 1; j <= n; j++) {
            if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                dp[i][j] = dp[i - 1][j - 1];
            } else {
                dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
    }
    return dp[m][n];
}
```

### Dry Run

```
Input: word1 = "horse", word2 = "ros"

     ""  r  o  s
  ""  0  1  2  3
  h   1  1  2  3
  o   2  2  1  2
  r   3  2  2  2
  s   4  3  3  2
  e   5  4  4  3

dp[5][3] = 3
Operations: horse → rorse (replace h→r) → rose (delete r) → ros (delete e)
```

### Complexity
- Time: O(m × n)
- Space: O(m × n), can optimize to O(n)

---

## Problem 9: Maximum Subarray / Kadane's Algorithm (NeetCode #53) ⭐⭐

### Problem
Find the contiguous subarray with the largest sum.

### Solution

```java
public int maxSubArray(int[] nums) {
    int maxSum = nums[0];
    int currentSum = nums[0];
    
    for (int i = 1; i < nums.length; i++) {
        currentSum = Math.max(nums[i], currentSum + nums[i]);
        maxSum = Math.max(maxSum, currentSum);
    }
    return maxSum;
}
```

### Dry Run

```
Input: nums = [-2, 1, -3, 4, -1, 2, 1, -5, 4]

i=0: currentSum=-2, maxSum=-2
i=1: currentSum=max(1, -2+1)=1, maxSum=1
i=2: currentSum=max(-3, 1-3)=-2, maxSum=1
i=3: currentSum=max(4, -2+4)=4, maxSum=4
i=4: currentSum=max(-1, 4-1)=3, maxSum=4
i=5: currentSum=max(2, 3+2)=5, maxSum=5
i=6: currentSum=max(1, 5+1)=6, maxSum=6
i=7: currentSum=max(-5, 6-5)=1, maxSum=6
i=8: currentSum=max(4, 1+4)=5, maxSum=6

Output: 6 (subarray [4,-1,2,1])
```

### Complexity
- Time: O(n), Space: O(1)

---

## Problem 10: Unique Paths (NeetCode #62) ⭐⭐

### Problem
Robot at top-left of m×n grid, can only move right or down. Count unique paths to bottom-right.

### Solution

```java
public int uniquePaths(int m, int n) {
    int[] dp = new int[n];
    Arrays.fill(dp, 1); // first row: all 1s
    
    for (int i = 1; i < m; i++) {
        for (int j = 1; j < n; j++) {
            dp[j] += dp[j - 1]; // dp[j] = from above + from left
        }
    }
    return dp[n - 1];
}
```

### Dry Run

```
Input: m=3, n=3

Row 0: dp = [1, 1, 1]
Row 1: dp = [1, 2, 3]   (dp[1]=1+1=2, dp[2]=1+2=3)
Row 2: dp = [1, 3, 6]   (dp[1]=2+1=3, dp[2]=3+3=6)

Output: 6
```

### Complexity
- Time: O(m × n), Space: O(n)

---

## Summary — DP Problem Categories

| Category | Recurrence Pattern | Problems |
|----------|-------------------|----------|
| Fibonacci | `dp[i] = dp[i-1] + dp[i-2]` | Climbing Stairs, House Robber |
| Take/Skip | `dp[i] = max(take, skip)` | House Robber, 0/1 Knapsack |
| Unbounded Knapsack | `dp[i] = min/max(dp[i-coin] + 1)` | Coin Change |
| Subsequence | Compare chars, 2D table | LCS, Edit Distance, LIS |
| Boolean reachability | `dp[i] = dp[j] && condition` | Word Break, Partition Sum |
| Grid | `dp[i][j] = dp[i-1][j] + dp[i][j-1]` | Unique Paths, Min Path Sum |
| Kadane's | `dp[i] = max(nums[i], dp[i-1]+nums[i])` | Maximum Subarray |

### DP Problem-Solving Framework
1. **Define state**: What does dp[i] represent?
2. **Base case**: What's dp[0] (or dp[0][0])?
3. **Transition**: How does dp[i] relate to smaller subproblems?
4. **Answer**: Where is the final answer? dp[n], dp[n][m], max(dp)?
5. **Optimize space**: Can you use 1D instead of 2D?
