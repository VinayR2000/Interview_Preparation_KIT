# 5. Binary Search — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Binary Search (NeetCode #704) ⭐

### Problem
Given a sorted array `nums` and a `target`, return the index of target or -1 if not found.

### Solution

```java
public int search(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2; // avoid overflow
        if (nums[mid] == target) return mid;
        else if (nums[mid] < target) left = mid + 1;
        else right = mid - 1;
    }
    return -1;
}
```

### Complexity
- Time: O(log n)
- Space: O(1)

---

## Problem 2: Search in Rotated Sorted Array (NeetCode #33) ⭐⭐⭐

### Problem
Array was sorted then rotated. Find the target index. All values are unique.

### Key Insight
- At any mid, ONE half is always sorted
- Determine which half is sorted, then check if target is in that half

### Solution

```java
public int search(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) return mid;
        
        // Left half is sorted
        if (nums[left] <= nums[mid]) {
            if (target >= nums[left] && target < nums[mid]) {
                right = mid - 1; // target in left sorted half
            } else {
                left = mid + 1;  // target in right half
            }
        }
        // Right half is sorted
        else {
            if (target > nums[mid] && target <= nums[right]) {
                left = mid + 1;  // target in right sorted half
            } else {
                right = mid - 1; // target in left half
            }
        }
    }
    return -1;
}
```

### Dry Run

```
Input: nums = [4, 5, 6, 7, 0, 1, 2], target = 0

left=0, right=6, mid=3: nums[3]=7 ≠ 0
  nums[0]=4 <= nums[3]=7 → left half sorted [4,5,6,7]
  target=0 >= 4? No → target NOT in left half → left = 4

left=4, right=6, mid=5: nums[5]=1 ≠ 0
  nums[4]=0 <= nums[5]=1 → left half sorted [0,1]
  target=0 >= 0 AND target=0 < 1? YES → right = 4

left=4, right=4, mid=4: nums[4]=0 == target → return 4

Output: 4
```

### Complexity
- Time: O(log n)
- Space: O(1)

---

## Problem 3: Find Minimum in Rotated Sorted Array (NeetCode #153) ⭐⭐

### Problem
Find the minimum element in a rotated sorted array (no duplicates).

### Key Insight
- Minimum is at the rotation point
- If `nums[mid] > nums[right]`, minimum is in right half
- Otherwise, minimum is in left half (including mid)

### Solution

```java
public int findMin(int[] nums) {
    int left = 0, right = nums.length - 1;
    
    while (left < right) {
        int mid = left + (right - left) / 2;
        
        if (nums[mid] > nums[right]) {
            left = mid + 1;   // min is in right half
        } else {
            right = mid;      // min could be mid itself
        }
    }
    return nums[left];
}
```

### Dry Run

```
Input: nums = [3, 4, 5, 1, 2]

left=0, right=4, mid=2: nums[2]=5 > nums[4]=2 → left=3
left=3, right=4, mid=3: nums[3]=1 ≤ nums[4]=2 → right=3
left=3 == right=3 → return nums[3]=1

Output: 1
```

### Complexity
- Time: O(log n)
- Space: O(1)

---

## Problem 4: Koko Eating Bananas (NeetCode #875) ⭐⭐⭐

### Problem
Koko has `piles` of bananas and `h` hours. She eats at speed `k` bananas/hour (one pile per hour minimum). Find the minimum `k` to finish all bananas in `h` hours.

### Approach: Binary Search on Answer
- Search space: k ∈ [1, max(piles)]
- Feasibility check: can she finish at speed k within h hours?

### Solution

```java
public int minEatingSpeed(int[] piles, int h) {
    int left = 1, right = 0;
    for (int pile : piles) right = Math.max(right, pile);
    
    while (left < right) {
        int mid = left + (right - left) / 2;
        
        if (canFinish(piles, mid, h)) {
            right = mid;      // try slower speed
        } else {
            left = mid + 1;   // need faster speed
        }
    }
    return left;
}

private boolean canFinish(int[] piles, int speed, int h) {
    int hours = 0;
    for (int pile : piles) {
        hours += (pile + speed - 1) / speed; // ceil division
    }
    return hours <= h;
}
```

### Dry Run

```
Input: piles = [3, 6, 7, 11], h = 8

Search space: left=1, right=11

mid=6: hours = ceil(3/6)+ceil(6/6)+ceil(7/6)+ceil(11/6) = 1+1+2+2 = 6 ≤ 8 → right=6
mid=3: hours = ceil(3/3)+ceil(6/3)+ceil(7/3)+ceil(11/3) = 1+2+3+4 = 10 > 8 → left=4
mid=5: hours = ceil(3/5)+ceil(6/5)+ceil(7/5)+ceil(11/5) = 1+2+2+3 = 8 ≤ 8 → right=5
mid=4: hours = ceil(3/4)+ceil(6/4)+ceil(7/4)+ceil(11/4) = 1+2+2+3 = 8 ≤ 8 → right=4
left=4 == right=4 → return 4

Output: 4
```

### Complexity
- Time: O(n × log(max)) where n = piles length, max = largest pile
- Space: O(1)

---

## Problem 5: Search a 2D Matrix (NeetCode #74) ⭐⭐

### Problem
Each row is sorted, and the first integer of each row is greater than the last integer of the previous row. Search for a target.

### Approach
- Treat as a flattened sorted array of size m×n
- Binary search with index conversion: `row = mid / cols`, `col = mid % cols`

### Solution

```java
public boolean searchMatrix(int[][] matrix, int target) {
    int rows = matrix.length, cols = matrix[0].length;
    int left = 0, right = rows * cols - 1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2;
        int midVal = matrix[mid / cols][mid % cols];
        
        if (midVal == target) return true;
        else if (midVal < target) left = mid + 1;
        else right = mid - 1;
    }
    return false;
}
```

### Dry Run

```
Input: matrix = [[1,3,5,7],[10,11,16,20],[23,30,34,60]], target = 3
rows=3, cols=4, left=0, right=11

mid=5: matrix[5/4][5%4] = matrix[1][1] = 11 > 3 → right=4
mid=2: matrix[2/4][2%4] = matrix[0][2] = 5 > 3 → right=1
mid=0: matrix[0][0] = 1 < 3 → left=1
mid=1: matrix[0][1] = 3 == target → return true

Output: true
```

### Complexity
- Time: O(log(m × n))
- Space: O(1)

---

## Problem 6: Find Peak Element (NeetCode #162) ⭐⭐

### Problem
Find a peak element (strictly greater than its neighbors). Array may have multiple peaks.

### Key Insight
- If `nums[mid] < nums[mid + 1]`, a peak exists to the right
- Otherwise, a peak exists to the left (including mid)

### Solution

```java
public int findPeakElement(int[] nums) {
    int left = 0, right = nums.length - 1;
    
    while (left < right) {
        int mid = left + (right - left) / 2;
        
        if (nums[mid] < nums[mid + 1]) {
            left = mid + 1;   // peak is to the right
        } else {
            right = mid;      // mid could be peak
        }
    }
    return left;
}
```

### Dry Run

```
Input: nums = [1, 2, 3, 1]

left=0, right=3, mid=1: nums[1]=2 < nums[2]=3 → left=2
left=2, right=3, mid=2: nums[2]=3 > nums[3]=1 → right=2
left=2 == right=2 → return 2

Output: 2 (nums[2]=3 is a peak)
```

### Complexity
- Time: O(log n)
- Space: O(1)

---

## Problem 7: Median of Two Sorted Arrays (NeetCode #4) ⭐⭐⭐

### Problem
Given two sorted arrays `nums1` and `nums2`, find the median of the combined sorted array in O(log(m+n)) time.

### Approach
- Binary search on the partition point of the smaller array
- Ensure left partition has correct count and max(left) ≤ min(right)

### Solution

```java
public double findMedianSortedArrays(int[] nums1, int[] nums2) {
    // Ensure binary search on smaller array
    if (nums1.length > nums2.length) return findMedianSortedArrays(nums2, nums1);
    
    int m = nums1.length, n = nums2.length;
    int left = 0, right = m;
    int halfLen = (m + n + 1) / 2;
    
    while (left <= right) {
        int i = left + (right - left) / 2;  // partition of nums1
        int j = halfLen - i;                  // partition of nums2
        
        int maxLeft1 = (i == 0) ? Integer.MIN_VALUE : nums1[i - 1];
        int minRight1 = (i == m) ? Integer.MAX_VALUE : nums1[i];
        int maxLeft2 = (j == 0) ? Integer.MIN_VALUE : nums2[j - 1];
        int minRight2 = (j == n) ? Integer.MAX_VALUE : nums2[j];
        
        if (maxLeft1 <= minRight2 && maxLeft2 <= minRight1) {
            // Found correct partition
            if ((m + n) % 2 == 0) {
                return (Math.max(maxLeft1, maxLeft2) + Math.min(minRight1, minRight2)) / 2.0;
            } else {
                return Math.max(maxLeft1, maxLeft2);
            }
        } else if (maxLeft1 > minRight2) {
            right = i - 1; // too many from nums1 in left partition
        } else {
            left = i + 1;  // too few from nums1 in left partition
        }
    }
    return 0.0;
}
```

### Dry Run

```
Input: nums1 = [1, 3], nums2 = [2]
m=2, n=1, halfLen=(2+1+1)/2=2

left=0, right=2, i=1, j=2-1=1:
  maxLeft1=nums1[0]=1, minRight1=nums1[1]=3
  maxLeft2=nums2[0]=2, minRight2=MAX_VALUE
  
  1 ≤ MAX_VALUE ✓ AND 2 ≤ 3 ✓ → Found!
  (m+n)=3 is odd → return max(1,2) = 2.0

Output: 2.0
```

### Complexity
- Time: O(log(min(m, n)))
- Space: O(1)

---

## Problem 8: Time Based Key-Value Store (NeetCode #981) ⭐⭐

### Problem
Design a key-value store that stores multiple values per key with timestamps. `get(key, timestamp)` returns the value with the largest timestamp ≤ given timestamp.

### Solution

```java
class TimeMap {
    Map<String, List<int[]>> map; // key → [(timestamp, valueIndex)]
    Map<String, List<String>> values;
    
    // Simpler approach:
    Map<String, TreeMap<Integer, String>> store;
    
    public TimeMap() {
        store = new HashMap<>();
    }
    
    public void set(String key, String value, int timestamp) {
        store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
    }
    
    public String get(String key, int timestamp) {
        if (!store.containsKey(key)) return "";
        TreeMap<Integer, String> treeMap = store.get(key);
        Integer floorKey = treeMap.floorKey(timestamp);
        return floorKey == null ? "" : treeMap.get(floorKey);
    }
}
```

### Binary Search Approach (Interview Preferred):

```java
class TimeMap {
    Map<String, List<Pair>> map;
    
    record Pair(int timestamp, String value) {}
    
    public TimeMap() { map = new HashMap<>(); }
    
    public void set(String key, String value, int timestamp) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(new Pair(timestamp, value));
    }
    
    public String get(String key, int timestamp) {
        if (!map.containsKey(key)) return "";
        List<Pair> list = map.get(key);
        
        // Binary search for largest timestamp <= given
        int left = 0, right = list.size() - 1;
        String result = "";
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (list.get(mid).timestamp() <= timestamp) {
                result = list.get(mid).value();
                left = mid + 1; // try to find larger valid timestamp
            } else {
                right = mid - 1;
            }
        }
        return result;
    }
}
```

### Complexity
- set: O(1)
- get: O(log n) where n = number of timestamps for that key

---

## Summary — Binary Search Patterns

| Pattern | Template | Problems |
|---------|----------|----------|
| Standard search | `left <= right`, return mid | Binary Search, Search 2D Matrix |
| Find boundary | `left < right`, move right=mid | Find Min Rotated, Peak Element |
| Rotated array | Check which half sorted | Search Rotated Array |
| Search on answer | Binary search on result space | Koko Bananas, Split Array Largest Sum |
| Partition-based | Binary search on partition | Median of Two Arrays |

### Binary Search Recognition Signals
- "Sorted array" (or can be sorted)
- "Find target / boundary / first / last"
- "Minimize maximum" or "Maximize minimum"
- "O(log n) required"
- "Monotonic condition" (once true, stays true)
