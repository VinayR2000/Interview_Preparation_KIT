# 2. Arrays & Hashing — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Two Sum (NeetCode/LeetCode #1) ⭐⭐⭐

### Problem
Given an array of integers `nums` and an integer `target`, return indices of the two numbers that add up to `target`. Each input has exactly one solution, and you can't use the same element twice.

### Approach: HashMap (Complement Lookup)
- For each number, check if `target - num` exists in the map
- If yes → found the pair. If no → store current num and index.

### Solution

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>(); // value → index
    
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (map.containsKey(complement)) {
            return new int[]{map.get(complement), i};
        }
        map.put(nums[i], i);
    }
    return new int[]{};
}
```

### Dry Run

```
Input: nums = [2, 7, 11, 15], target = 9

i=0: complement = 9-2 = 7, map={} → not found, map={2:0}
i=1: complement = 9-7 = 2, map={2:0} → FOUND! return [0, 1]

Output: [0, 1]
```

### Complexity
- Time: O(n) — single pass
- Space: O(n) — hash map storage

---

## Problem 2: Contains Duplicate (NeetCode #217) ⭐

### Problem
Given an integer array `nums`, return `true` if any value appears at least twice.

### Solution

```java
public boolean containsDuplicate(int[] nums) {
    Set<Integer> seen = new HashSet<>();
    for (int num : nums) {
        if (!seen.add(num)) return true; // add returns false if already exists
    }
    return false;
}
```

### Dry Run

```
Input: nums = [1, 2, 3, 1]

i=0: seen.add(1) → true (added), seen={1}
i=1: seen.add(2) → true (added), seen={1,2}
i=2: seen.add(3) → true (added), seen={1,2,3}
i=3: seen.add(1) → false (already exists!) → return true

Output: true
```

### Complexity
- Time: O(n)
- Space: O(n)

---

## Problem 3: Valid Anagram (NeetCode #242) ⭐

### Problem
Given two strings `s` and `t`, return `true` if `t` is an anagram of `s`.

### Solution

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;
    
    int[] count = new int[26];
    for (int i = 0; i < s.length(); i++) {
        count[s.charAt(i) - 'a']++;
        count[t.charAt(i) - 'a']--;
    }
    
    for (int c : count) {
        if (c != 0) return false;
    }
    return true;
}
```

### Dry Run

```
Input: s = "anagram", t = "nagaram"

After counting s: count = [3,0,0,...,1,...,1,...] (a=3, g=1, m=1, n=1, r=1)
After subtracting t: all zeros → return true

Output: true
```

### Complexity
- Time: O(n)
- Space: O(1) — fixed size array of 26

---

## Problem 4: Group Anagrams (NeetCode #49) ⭐⭐

### Problem
Given an array of strings, group the anagrams together.

### Solution

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> map = new HashMap<>();
    
    for (String s : strs) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(map.values());
}
```

### Dry Run

```
Input: ["eat","tea","tan","ate","nat","bat"]

"eat" → sort → "aet" → map={"aet":["eat"]}
"tea" → sort → "aet" → map={"aet":["eat","tea"]}
"tan" → sort → "ant" → map={"aet":["eat","tea"], "ant":["tan"]}
"ate" → sort → "aet" → map={"aet":["eat","tea","ate"], "ant":["tan"]}
"nat" → sort → "ant" → map={"aet":["eat","tea","ate"], "ant":["tan","nat"]}
"bat" → sort → "abt" → map={"aet":["eat","tea","ate"], "ant":["tan","nat"], "abt":["bat"]}

Output: [["eat","tea","ate"],["tan","nat"],["bat"]]
```

### Complexity
- Time: O(n × k log k) where k = max string length (sorting each string)
- Space: O(n × k)

---

## Problem 5: Top K Frequent Elements (NeetCode #347) ⭐⭐

### Problem
Given an integer array `nums` and an integer `k`, return the `k` most frequent elements.

### Approach: Bucket Sort
- Count frequencies, then use bucket sort where index = frequency

### Solution

```java
public int[] topKFrequent(int[] nums, int k) {
    // Step 1: Count frequency
    Map<Integer, Integer> freq = new HashMap<>();
    for (int num : nums) freq.merge(num, 1, Integer::sum);
    
    // Step 2: Bucket sort — index = frequency, value = list of nums with that freq
    List<Integer>[] buckets = new List[nums.length + 1];
    for (int i = 0; i < buckets.length; i++) buckets[i] = new ArrayList<>();
    
    for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
        buckets[e.getValue()].add(e.getKey());
    }
    
    // Step 3: Collect from highest frequency buckets
    int[] result = new int[k];
    int idx = 0;
    for (int i = buckets.length - 1; i >= 0 && idx < k; i--) {
        for (int num : buckets[i]) {
            if (idx < k) result[idx++] = num;
        }
    }
    return result;
}
```

### Dry Run

```
Input: nums = [1,1,1,2,2,3], k = 2

Step 1: freq = {1:3, 2:2, 3:1}
Step 2: buckets[1]=[3], buckets[2]=[2], buckets[3]=[1]
Step 3: i=6→empty, i=5→empty, ..., i=3→[1]→result=[1], i=2→[2]→result=[1,2]

Output: [1, 2]
```

### Complexity
- Time: O(n) — bucket sort avoids O(n log n) sorting
- Space: O(n)

---

## Problem 6: Product of Array Except Self (NeetCode #238) ⭐⭐

### Problem
Given an integer array `nums`, return an array where `answer[i]` is the product of all elements except `nums[i]`. No division allowed.

### Approach: Prefix and Suffix Products

```
For each index i:
  answer[i] = (product of all elements to the left) × (product of all elements to the right)
```

### Solution

```java
public int[] productExceptSelf(int[] nums) {
    int n = nums.length;
    int[] answer = new int[n];
    
    // Left pass: answer[i] = product of all elements to the left of i
    answer[0] = 1;
    for (int i = 1; i < n; i++) {
        answer[i] = answer[i - 1] * nums[i - 1];
    }
    
    // Right pass: multiply by product of all elements to the right of i
    int rightProduct = 1;
    for (int i = n - 2; i >= 0; i--) {
        rightProduct *= nums[i + 1];
        answer[i] *= rightProduct;
    }
    return answer;
}
```

### Dry Run

```
Input: nums = [1, 2, 3, 4]

Left pass:  answer = [1, 1, 2, 6]
  answer[0]=1, answer[1]=1*1=1, answer[2]=1*2=2, answer[3]=2*3=6

Right pass (rightProduct starts at 1):
  i=2: rightProduct = 1*4 = 4,  answer[2] = 2*4 = 8
  i=1: rightProduct = 4*3 = 12, answer[1] = 1*12 = 12
  i=0: rightProduct = 12*2 = 24, answer[0] = 1*24 = 24

Output: [24, 12, 8, 6]

Verify: 2×3×4=24 ✓, 1×3×4=12 ✓, 1×2×4=8 ✓, 1×2×3=6 ✓
```

### Complexity
- Time: O(n) — two passes
- Space: O(1) — output array doesn't count as extra space

---

## Problem 7: Longest Consecutive Sequence (NeetCode #128) ⭐⭐

### Problem
Given an unsorted array of integers `nums`, return the length of the longest consecutive elements sequence. Must run in O(n) time.

### Approach: HashSet — Find Sequence Starts
- A number is the START of a sequence only if `num - 1` is NOT in the set
- From each start, count consecutive numbers

### Solution

```java
public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int num : nums) set.add(num);
    
    int longest = 0;
    
    for (int num : set) {
        // Only start counting from the beginning of a sequence
        if (!set.contains(num - 1)) {
            int length = 1;
            int current = num;
            
            while (set.contains(current + 1)) {
                current++;
                length++;
            }
            longest = Math.max(longest, length);
        }
    }
    return longest;
}
```

### Dry Run

```
Input: nums = [100, 4, 200, 1, 3, 2]

Set = {100, 4, 200, 1, 3, 2}

num=100: 99 not in set → START! count: 100→101? No. length=1
num=4:   3 in set → SKIP (not a start)
num=200: 199 not in set → START! count: 200→201? No. length=1
num=1:   0 not in set → START! count: 1→2✓→3✓→4✓→5? No. length=4
num=3:   2 in set → SKIP
num=2:   1 in set → SKIP

longest = 4

Output: 4 (sequence: [1,2,3,4])
```

### Complexity
- Time: O(n) — each number is visited at most twice (once in outer loop, once in while)
- Space: O(n) — hash set

---

## Problem 8: Valid Sudoku (NeetCode #36) ⭐⭐

### Problem
Determine if a 9×9 Sudoku board is valid. Only filled cells need to be checked.

### Solution

```java
public boolean isValidSudoku(char[][] board) {
    Set<String> seen = new HashSet<>();
    
    for (int i = 0; i < 9; i++) {
        for (int j = 0; j < 9; j++) {
            char c = board[i][j];
            if (c == '.') continue;
            
            String row = c + " in row " + i;
            String col = c + " in col " + j;
            String box = c + " in box " + (i / 3) + "-" + (j / 3);
            
            if (!seen.add(row) || !seen.add(col) || !seen.add(box)) {
                return false;
            }
        }
    }
    return true;
}
```

### Complexity
- Time: O(81) = O(1) — fixed 9×9 board
- Space: O(81) = O(1)

---

## Problem 9: Encode and Decode Strings (NeetCode #271) ⭐⭐

### Problem
Design an algorithm to encode a list of strings to a single string, and decode it back.

### Approach: Length-prefix encoding
- Encode each string as `length + "#" + string`

### Solution

```java
public String encode(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    for (String s : strs) {
        sb.append(s.length()).append('#').append(s);
    }
    return sb.toString();
}

public List<String> decode(String s) {
    List<String> result = new ArrayList<>();
    int i = 0;
    
    while (i < s.length()) {
        int j = i;
        while (s.charAt(j) != '#') j++;
        int len = Integer.parseInt(s.substring(i, j));
        String str = s.substring(j + 1, j + 1 + len);
        result.add(str);
        i = j + 1 + len;
    }
    return result;
}
```

### Dry Run

```
Encode: ["hello", "world"]
  → "5#hello5#world"

Decode: "5#hello5#world"
  i=0: j scans to '#' at j=1, len=5, str="hello", i=7
  i=7: j scans to '#' at j=8, len=5, str="world", i=14

Output: ["hello", "world"]
```

### Complexity
- Time: O(n) where n = total characters
- Space: O(n)

---

## Problem 10: Subarray Sum Equals K (LeetCode #560) ⭐⭐⭐

### Problem
Given an integer array `nums` and an integer `k`, return the total number of subarrays whose sum equals `k`.

### Approach: Prefix Sum + HashMap
- If `prefix[j] - prefix[i] = k`, then subarray `[i+1...j]` sums to k
- Store frequency of each prefix sum

### Solution

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> prefixCount = new HashMap<>();
    prefixCount.put(0, 1); // empty prefix (sum 0 occurs once)
    
    int sum = 0, count = 0;
    
    for (int num : nums) {
        sum += num;
        count += prefixCount.getOrDefault(sum - k, 0);
        prefixCount.merge(sum, 1, Integer::sum);
    }
    return count;
}
```

### Dry Run

```
Input: nums = [1, 2, 3], k = 3

prefixCount = {0:1}, sum=0, count=0

i=0: sum=1, look for sum-k=1-3=-2 → 0, prefixCount={0:1, 1:1}
i=1: sum=3, look for sum-k=3-3=0 → 1! count=1, prefixCount={0:1, 1:1, 3:1}
i=2: sum=6, look for sum-k=6-3=3 → 1! count=2, prefixCount={0:1, 1:1, 3:1, 6:1}

Output: 2 (subarrays: [1,2] and [3])
```

### Complexity
- Time: O(n)
- Space: O(n)

---

## Summary — Key Patterns for Arrays & Hashing

| Pattern | When to Use | Problems |
|---------|-------------|----------|
| HashMap complement | Find pair summing to target | Two Sum |
| HashSet existence | Detect duplicates, membership | Contains Duplicate, Longest Consecutive |
| Frequency count | Group by property, top-k | Group Anagrams, Top K Frequent |
| Prefix Sum + Map | Count subarrays with sum = k | Subarray Sum Equals K |
| Bucket Sort | Top-K without sorting | Top K Frequent Elements |
| Left/Right product | Product without division | Product Except Self |
| Length-prefix encoding | Serialize/deserialize strings | Encode/Decode Strings |
