# 4. Sliding Window — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Best Time to Buy and Sell Stock (NeetCode #121) ⭐

### Problem
Given an array `prices` where `prices[i]` is the price on day `i`, find the maximum profit from one buy and one sell (buy before sell).

### Approach
- Track minimum price seen so far (buy day)
- At each day, calculate profit if selling today

### Solution

```java
public int maxProfit(int[] prices) {
    int minPrice = Integer.MAX_VALUE;
    int maxProfit = 0;
    
    for (int price : prices) {
        minPrice = Math.min(minPrice, price);
        maxProfit = Math.max(maxProfit, price - minPrice);
    }
    return maxProfit;
}
```

### Dry Run

```
Input: prices = [7, 1, 5, 3, 6, 4]

price=7: minPrice=7, profit=0, maxProfit=0
price=1: minPrice=1, profit=0, maxProfit=0
price=5: minPrice=1, profit=4, maxProfit=4
price=3: minPrice=1, profit=2, maxProfit=4
price=6: minPrice=1, profit=5, maxProfit=5
price=4: minPrice=1, profit=3, maxProfit=5

Output: 5 (buy at 1, sell at 6)
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 2: Longest Substring Without Repeating Characters (NeetCode #3) ⭐⭐⭐

### Problem
Given a string `s`, find the length of the longest substring without repeating characters.

### Approach: Variable-size sliding window
- Expand right pointer, tracking characters in a set
- When duplicate found, shrink from left until no duplicate

### Solution

```java
public int lengthOfLongestSubstring(String s) {
    Set<Character> window = new HashSet<>();
    int left = 0, maxLen = 0;
    
    for (int right = 0; right < s.length(); right++) {
        while (window.contains(s.charAt(right))) {
            window.remove(s.charAt(left));
            left++;
        }
        window.add(s.charAt(right));
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### Optimized (HashMap to jump left pointer directly):

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> lastIndex = new HashMap<>();
    int left = 0, maxLen = 0;
    
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastIndex.containsKey(c) && lastIndex.get(c) >= left) {
            left = lastIndex.get(c) + 1; // jump past the duplicate
        }
        lastIndex.put(c, right);
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### Dry Run

```
Input: s = "abcabcbb"

right=0('a'): window={a}, left=0, len=1, maxLen=1
right=1('b'): window={a,b}, left=0, len=2, maxLen=2
right=2('c'): window={a,b,c}, left=0, len=3, maxLen=3
right=3('a'): 'a' in window! remove 'a', left=1. window={b,c,a}, len=3, maxLen=3
right=4('b'): 'b' in window! remove 'b', left=2. window={c,a,b}, len=3, maxLen=3
right=5('c'): 'c' in window! remove 'c', left=3. window={a,b,c}, len=3, maxLen=3
right=6('b'): 'b' in window! remove 'a', left=4. Still has 'b'! remove 'b', left=5.
              window={c,b}, len=2, maxLen=3
right=7('b'): 'b' in window! remove 'c', left=6. remove 'b', left=7.
              window={b}, len=1, maxLen=3

Output: 3 ("abc")
```

### Complexity
- Time: O(n) — each character added/removed from set at most once
- Space: O(min(n, 26)) — set bounded by alphabet size

---

## Problem 3: Longest Repeating Character Replacement (NeetCode #424) ⭐⭐⭐

### Problem
Given string `s` and integer `k`, you can replace at most `k` characters. Find the length of the longest substring with all same characters.

### Key Insight
- Window is valid if: `windowLength - maxFreqInWindow <= k`
- (Characters that need replacement ≤ k)

### Solution

```java
public int characterReplacement(String s, int k) {
    int[] count = new int[26];
    int left = 0, maxFreq = 0, maxLen = 0;
    
    for (int right = 0; right < s.length(); right++) {
        count[s.charAt(right) - 'A']++;
        maxFreq = Math.max(maxFreq, count[s.charAt(right) - 'A']);
        
        // Window invalid: chars to replace > k
        int windowLen = right - left + 1;
        if (windowLen - maxFreq > k) {
            count[s.charAt(left) - 'A']--;
            left++;
        }
        
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### Dry Run

```
Input: s = "AABABBA", k = 1

right=0('A'): count[A]=1, maxFreq=1, winLen=1, 1-1=0≤1 ✓, maxLen=1
right=1('A'): count[A]=2, maxFreq=2, winLen=2, 2-2=0≤1 ✓, maxLen=2
right=2('B'): count[B]=1, maxFreq=2, winLen=3, 3-2=1≤1 ✓, maxLen=3
right=3('A'): count[A]=3, maxFreq=3, winLen=4, 4-3=1≤1 ✓, maxLen=4
right=4('B'): count[B]=2, maxFreq=3, winLen=5, 5-3=2>1 ✗ → shrink
              count[A]=2, left=1, maxLen=4
right=5('B'): count[B]=3, maxFreq=3, winLen=5, 5-3=2>1 ✗ → shrink
              count[A]=1, left=2, maxLen=4
right=6('A'): count[A]=2, maxFreq=3, winLen=5, 5-3=2>1 ✗ → shrink
              count[B]=2, left=3, maxLen=4

Output: 4 (e.g., "AABA" → replace B → "AAAA")
```

### Why maxFreq Never Decreases
- We only care about the MAXIMUM window. Even if maxFreq becomes stale, the window only grows when a larger maxFreq is found. The window never grows incorrectly.

### Complexity
- Time: O(n)
- Space: O(1) — fixed 26-char array

---

## Problem 4: Minimum Window Substring (NeetCode #76) ⭐⭐⭐

### Problem
Given strings `s` and `t`, find the minimum window in `s` that contains all characters of `t`.

### Approach
- Expand right until window contains all chars of `t`
- Shrink left to minimize while maintaining validity

### Solution

```java
public String minWindow(String s, String t) {
    if (s.length() < t.length()) return "";
    
    Map<Character, Integer> need = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    
    int left = 0, matched = 0;
    int minLen = Integer.MAX_VALUE, minStart = 0;
    int required = need.size(); // distinct chars to satisfy
    Map<Character, Integer> window = new HashMap<>();
    
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        window.merge(c, 1, Integer::sum);
        
        if (need.containsKey(c) && window.get(c).intValue() == need.get(c).intValue()) {
            matched++;
        }
        
        // Shrink while valid
        while (matched == required) {
            if (right - left + 1 < minLen) {
                minLen = right - left + 1;
                minStart = left;
            }
            
            char leftChar = s.charAt(left);
            window.merge(leftChar, -1, Integer::sum);
            if (need.containsKey(leftChar) && window.get(leftChar) < need.get(leftChar)) {
                matched--;
            }
            left++;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(minStart, minStart + minLen);
}
```

### Dry Run

```
Input: s = "ADOBECODEBANC", t = "ABC"
need = {A:1, B:1, C:1}, required = 3

right=0('A'): window={A:1}, matched=1 (A satisfied)
right=1('D'): window={A:1,D:1}, matched=1
right=2('O'): matched=1
right=3('B'): window={..B:1}, matched=2 (B satisfied)
right=4('E'): matched=2
right=5('C'): window={..C:1}, matched=3 ✓ ALL MATCHED!
  → Shrink: len=6 "ADOBEC", minLen=6, minStart=0
  Remove 'A': window={A:0}, matched=2 (A broken), left=1

right=6('O'): matched=2
right=7('D'): matched=2
right=8('E'): matched=2
right=9('B'): window={B:2}, matched=2 (B was already satisfied)
right=10('A'): window={A:1}, matched=3 ✓
  → Shrink: len=10 → too long. Remove 'D','O','B','E','C'...
  Actually: left=1→2→3→4→5→...
  At left=5: removes 'C', matched=2, len was "CODEBA"=6 → no improvement
  
Wait, let me redo shrink at right=10:
  window valid, len=10 (index 1-10), no improvement
  Remove s[1]='D': still valid (D not needed), left=2, len=9
  Remove s[2]='O': still valid, left=3, len=8
  Remove s[3]='B': window[B]=1, still ≥ need[B]=1, valid! left=4, len=7
  Remove s[4]='E': still valid, left=5, len=6, same as minLen
  Remove s[5]='C': window[C]=0 < need[C]=1, matched=2 → stop, left=6

right=11('N'): matched=2
right=12('C'): window={C:1}, matched=3 ✓
  → Shrink: left=6, len=7 "ODEBANC" → worse
  Remove 'O': valid, left=7, len=6
  Remove 'D': valid, left=8, len=5
  Remove 'E': valid, left=9, len=4 "BANC" → minLen=4, minStart=9!
  Remove 'B': window[B]=0 < need[B]=1, matched=2 → stop

Output: "BANC"
```

### Complexity
- Time: O(n + m) where n = |s|, m = |t|
- Space: O(m) — character frequency maps

---

## Problem 5: Permutation in String (NeetCode #567) ⭐⭐

### Problem
Given strings `s1` and `s2`, return `true` if `s2` contains a permutation of `s1`.

### Approach: Fixed-size window (size of s1)
- Compare frequency arrays of window vs s1

### Solution

```java
public boolean checkInclusion(String s1, String s2) {
    if (s1.length() > s2.length()) return false;
    
    int[] s1Count = new int[26];
    int[] windowCount = new int[26];
    
    for (char c : s1.toCharArray()) s1Count[c - 'a']++;
    
    int windowSize = s1.length();
    
    for (int i = 0; i < s2.length(); i++) {
        windowCount[s2.charAt(i) - 'a']++; // add right
        
        if (i >= windowSize) {
            windowCount[s2.charAt(i - windowSize) - 'a']--; // remove left
        }
        
        if (Arrays.equals(s1Count, windowCount)) return true;
    }
    return false;
}
```

### Optimized (track matches instead of comparing arrays):

```java
public boolean checkInclusion(String s1, String s2) {
    if (s1.length() > s2.length()) return false;
    
    int[] count = new int[26];
    for (char c : s1.toCharArray()) count[c - 'a']++;
    
    int left = 0, toMatch = s1.length();
    
    for (int right = 0; right < s2.length(); right++) {
        if (count[s2.charAt(right) - 'a']-- > 0) toMatch--;
        
        if (toMatch == 0) return true;
        
        // Shrink window if it exceeds s1's length
        if (right - left + 1 == s1.length()) {
            if (++count[s2.charAt(left) - 'a'] > 0) toMatch++;
            left++;
        }
    }
    return false;
}
```

### Dry Run

```
Input: s1 = "ab", s2 = "eidbaooo"

count = [1,1,0,...] (a=1, b=1), toMatch=2, left=0

right=0('e'): count[e]-- → count[e]=-1, not >0 so toMatch stays 2
right=1('i'): count[i]=-1, toMatch=2. Window size=2: restore 'e', count[e]=0, left=1
right=2('d'): count[d]=-1, toMatch=2. Window=2: restore 'i', left=2
right=3('b'): count[b]-- → count[b]=0, was >0 → toMatch=1. Window=2: restore 'd', left=3
right=4('a'): count[a]-- → count[a]=0, was >0 → toMatch=0 → return true!

Output: true ("ba" is permutation of "ab" at index 3-4)
```

### Complexity
- Time: O(n) where n = |s2|
- Space: O(1) — fixed 26-char array

---

## Problem 6: Sliding Window Maximum (NeetCode #239) ⭐⭐⭐

### Problem
Given an array `nums` and window size `k`, return the maximum value in each sliding window.

### Approach: Monotonic Deque
- Maintain a deque storing indices in decreasing order of values
- Front of deque = maximum in current window

### Solution

```java
public int[] maxSlidingWindow(int[] nums, int k) {
    int n = nums.length;
    int[] result = new int[n - k + 1];
    Deque<Integer> deque = new ArrayDeque<>(); // stores indices
    
    for (int i = 0; i < n; i++) {
        // Remove indices outside window
        while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
            deque.pollFirst();
        }
        
        // Remove smaller elements (they'll never be max)
        while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i]) {
            deque.pollLast();
        }
        
        deque.offerLast(i);
        
        // Window is full, record result
        if (i >= k - 1) {
            result[i - k + 1] = nums[deque.peekFirst()];
        }
    }
    return result;
}
```

### Dry Run

```
Input: nums = [1, 3, -1, -3, 5, 3, 6, 7], k = 3

i=0: deque=[], add 0. deque=[0]
i=1: nums[0]=1 < nums[1]=3 → remove 0. add 1. deque=[1]
i=2: nums[1]=3 > nums[2]=-1 → keep. add 2. deque=[1,2]. 
     Window full! result[0]=nums[1]=3

i=3: deque front=1, 1 >= 3-3+1=1 → keep. nums[2]=-1 > nums[3]=-3 → keep.
     add 3. deque=[1,2,3]. result[1]=nums[1]=3

i=4: deque front=1, 1 < 4-3+1=2 → remove! deque=[2,3]
     nums[3]=-3 < nums[4]=5 → remove 3. nums[2]=-1 < 5 → remove 2.
     add 4. deque=[4]. result[2]=nums[4]=5

i=5: nums[4]=5 > nums[5]=3 → keep. add 5. deque=[4,5]. result[3]=nums[4]=5

i=6: deque front=4, 4 >= 6-3+1=4 → keep. nums[5]=3 < nums[6]=6 → remove.
     nums[4]=5 < nums[6]=6 → remove. add 6. deque=[6]. result[4]=nums[6]=6

i=7: nums[6]=6 < nums[7]=7 → remove. add 7. deque=[7]. result[5]=nums[7]=7

Output: [3, 3, 5, 5, 6, 7]
```

### Complexity
- Time: O(n) — each element added/removed from deque at most once
- Space: O(k) — deque stores at most k elements

---

## Problem 7: Minimum Size Subarray Sum (LeetCode #209) ⭐⭐

### Problem
Given an array of positive integers `nums` and a positive integer `target`, find the minimal length subarray whose sum ≥ target.

### Solution

```java
public int minSubArrayLen(int target, int[] nums) {
    int left = 0, sum = 0;
    int minLen = Integer.MAX_VALUE;
    
    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];
        
        while (sum >= target) {
            minLen = Math.min(minLen, right - left + 1);
            sum -= nums[left];
            left++;
        }
    }
    return minLen == Integer.MAX_VALUE ? 0 : minLen;
}
```

### Dry Run

```
Input: target = 7, nums = [2, 3, 1, 2, 4, 3]

right=0: sum=2
right=1: sum=5
right=2: sum=6
right=3: sum=8 ≥ 7 → minLen=4, remove 2 → sum=6, left=1
right=4: sum=10 ≥ 7 → minLen=3, remove 3 → sum=7 ≥ 7 → minLen=2, remove 1 → sum=6, left=3
right=5: sum=9 ≥ 7 → minLen=2, remove 2 → sum=7 ≥ 7 → minLen=2, remove 4 → sum=3, left=5

Output: 2 (subarray [4,3])
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Summary — Sliding Window Patterns

| Pattern | When to Use | Template |
|---------|-------------|----------|
| Fixed window | Window size given (k) | Add right, remove left when i ≥ k |
| Variable (maximize) | Longest valid substring | Expand right, shrink left when invalid |
| Variable (minimize) | Shortest valid substring | Expand until valid, shrink to minimize |
| Monotonic Deque | Max/min in sliding window | Maintain sorted indices in deque |

### Sliding Window Recognition Signals
- "Contiguous subarray/substring"
- "Longest/shortest with condition"
- "Maximum/minimum sum of size k"
- "At most k distinct characters"
- "Contains all characters of..."
