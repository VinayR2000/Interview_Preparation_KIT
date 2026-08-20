# 3. Two Pointers — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Valid Palindrome (NeetCode #125) ⭐

### Problem
Given a string `s`, return `true` if it is a palindrome considering only alphanumeric characters and ignoring cases.

### Solution

```java
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

### Dry Run

```
Input: s = "A man, a plan, a canal: Panama"

left=0('A'), right=29('a') → 'a'=='a' ✓
left=1(' '), skip → left=2('m'), right=28('m') → 'm'=='m' ✓
... continues matching ...
All match → return true

Output: true
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 2: Two Sum II - Sorted Array (NeetCode #167) ⭐⭐

### Problem
Given a 1-indexed sorted array, find two numbers that add up to `target`. Return their indices.

### Solution

```java
public int[] twoSum(int[] numbers, int target) {
    int left = 0, right = numbers.length - 1;
    
    while (left < right) {
        int sum = numbers[left] + numbers[right];
        if (sum == target) {
            return new int[]{left + 1, right + 1}; // 1-indexed
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    return new int[]{};
}
```

### Dry Run

```
Input: numbers = [2, 7, 11, 15], target = 9

left=0(2), right=3(15): sum=17 > 9 → right--
left=0(2), right=2(11): sum=13 > 9 → right--
left=0(2), right=1(7):  sum=9 == 9 → return [1, 2]

Output: [1, 2]
```

### Why This Works
- Array is sorted. If sum too big → decrease right (make sum smaller). If sum too small → increase left (make sum bigger). Guaranteed to find the pair.

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 3: 3Sum (NeetCode #15) ⭐⭐⭐

### Problem
Given an integer array `nums`, find all unique triplets `[nums[i], nums[j], nums[k]]` such that `i != j != k` and `nums[i] + nums[j] + nums[k] == 0`.

### Approach
- Sort the array
- Fix one element, use two-pointer for the remaining pair
- Skip duplicates to avoid duplicate triplets

### Solution

```java
public List<List<Integer>> threeSum(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    Arrays.sort(nums);
    
    for (int i = 0; i < nums.length - 2; i++) {
        // Skip duplicate for first element
        if (i > 0 && nums[i] == nums[i - 1]) continue;
        
        // Early termination: if smallest > 0, no solution possible
        if (nums[i] > 0) break;
        
        int left = i + 1, right = nums.length - 1;
        int target = -nums[i];
        
        while (left < right) {
            int sum = nums[left] + nums[right];
            if (sum == target) {
                result.add(Arrays.asList(nums[i], nums[left], nums[right]));
                // Skip duplicates
                while (left < right && nums[left] == nums[left + 1]) left++;
                while (left < right && nums[right] == nums[right - 1]) right--;
                left++;
                right--;
            } else if (sum < target) {
                left++;
            } else {
                right--;
            }
        }
    }
    return result;
}
```

### Dry Run

```
Input: nums = [-1, 0, 1, 2, -1, -4]
After sort: [-4, -1, -1, 0, 1, 2]

i=0, nums[i]=-4, target=4:
  left=1(-1), right=5(2): sum=1 < 4 → left++
  left=2(-1), right=5(2): sum=1 < 4 → left++
  left=3(0), right=5(2):  sum=2 < 4 → left++
  left=4(1), right=5(2):  sum=3 < 4 → left++
  left=5 >= right → done

i=1, nums[i]=-1, target=1:
  left=2(-1), right=5(2): sum=1 == 1 → result=[[-1,-1,2]]
    skip dups, left=3, right=4
  left=3(0), right=4(1): sum=1 == 1 → result=[[-1,-1,2],[-1,0,1]]
    left=4 >= right → done

i=2, nums[i]=-1, same as nums[1] → SKIP

i=3, nums[i]=0, target=0:
  left=4(1), right=5(2): sum=3 > 0 → right--
  left=4 >= right → done

Output: [[-1,-1,2], [-1,0,1]]
```

### Complexity
- Time: O(n²) — outer loop O(n), inner two-pointer O(n)
- Space: O(1) excluding output (O(n) for sorting in some implementations)

---

## Problem 4: Container With Most Water (NeetCode #11) ⭐⭐

### Problem
Given `n` vertical lines at positions 0 to n-1 with heights `height[i]`, find two lines that form a container holding the most water.

### Key Insight
- Area = min(height[left], height[right]) × (right - left)
- Move the pointer with the shorter line (moving the taller one can never increase area)

### Solution

```java
public int maxArea(int[] height) {
    int left = 0, right = height.length - 1;
    int maxWater = 0;
    
    while (left < right) {
        int area = Math.min(height[left], height[right]) * (right - left);
        maxWater = Math.max(maxWater, area);
        
        if (height[left] < height[right]) {
            left++;
        } else {
            right--;
        }
    }
    return maxWater;
}
```

### Dry Run

```
Input: height = [1, 8, 6, 2, 5, 4, 8, 3, 7]

left=0(1), right=8(7): area=min(1,7)*8=8, maxWater=8. Move left (shorter)
left=1(8), right=8(7): area=min(8,7)*7=49, maxWater=49. Move right
left=1(8), right=7(3): area=min(8,3)*6=18, maxWater=49. Move right
left=1(8), right=6(8): area=min(8,8)*5=40, maxWater=49. Move right
left=1(8), right=5(4): area=min(8,4)*4=16, maxWater=49. Move right
left=1(8), right=4(5): area=min(8,5)*3=15, maxWater=49. Move right
left=1(8), right=3(2): area=min(8,2)*2=4, maxWater=49. Move right
left=1(8), right=2(6): area=min(8,6)*1=6, maxWater=49. Move right
left=1 >= right=1 → done

Output: 49
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 5: Trapping Rain Water (NeetCode #42) ⭐⭐⭐

### Problem
Given `n` non-negative integers representing an elevation map where the width of each bar is 1, compute how much water it can trap after raining.

### Approach: Two Pointers with Left/Right Max
- Water at position i = min(leftMax, rightMax) - height[i]
- Process from the side with the smaller max

### Solution

```java
public int trap(int[] height) {
    int left = 0, right = height.length - 1;
    int leftMax = 0, rightMax = 0;
    int water = 0;
    
    while (left < right) {
        if (height[left] < height[right]) {
            leftMax = Math.max(leftMax, height[left]);
            water += leftMax - height[left]; // water trapped at left
            left++;
        } else {
            rightMax = Math.max(rightMax, height[right]);
            water += rightMax - height[right]; // water trapped at right
            right--;
        }
    }
    return water;
}
```

### Dry Run

```
Input: height = [0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1]

left=0, right=11, leftMax=0, rightMax=0
  h[0]=0 < h[11]=1 → leftMax=max(0,0)=0, water+=0-0=0, left=1
  h[1]=1 < h[11]=1? No → rightMax=max(0,1)=1, water+=1-1=0, right=10
  h[1]=1 < h[10]=2 → leftMax=max(0,1)=1, water+=1-1=0, left=2
  h[2]=0 < h[10]=2 → leftMax=max(1,0)=1, water+=1-0=1, left=3
  h[3]=2 < h[10]=2? No → rightMax=max(1,2)=2, water+=2-2=0, right=9
  h[3]=2 < h[9]=1? No → rightMax=max(2,1)=2, water+=2-1=1, right=8
  h[3]=2 < h[8]=2? No → rightMax=max(2,2)=2, water+=2-2=0, right=7
  h[3]=2 < h[7]=3 → leftMax=max(1,2)=2, water+=2-2=0, left=4
  h[4]=1 < h[7]=3 → leftMax=max(2,1)=2, water+=2-1=1, left=5
  h[5]=0 < h[7]=3 → leftMax=max(2,0)=2, water+=2-0=2, left=6
  h[6]=1 < h[7]=3 → leftMax=max(2,1)=2, water+=2-1=1, left=7
  left=7 >= right=7 → done

water = 0+0+0+1+0+1+0+0+1+2+1 = 6

Output: 6
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Problem 6: 3Sum Closest (LeetCode #16) ⭐⭐

### Problem
Given an integer array `nums` and a `target`, find three integers in `nums` such that the sum is closest to `target`. Return the sum.

### Solution

```java
public int threeSumClosest(int[] nums, int target) {
    Arrays.sort(nums);
    int closest = nums[0] + nums[1] + nums[2];
    
    for (int i = 0; i < nums.length - 2; i++) {
        int left = i + 1, right = nums.length - 1;
        
        while (left < right) {
            int sum = nums[i] + nums[left] + nums[right];
            
            if (Math.abs(sum - target) < Math.abs(closest - target)) {
                closest = sum;
            }
            
            if (sum < target) left++;
            else if (sum > target) right--;
            else return sum; // exact match
        }
    }
    return closest;
}
```

### Complexity
- Time: O(n²)
- Space: O(1)

---

## Problem 7: Sort Colors / Dutch National Flag (NeetCode #75) ⭐⭐

### Problem
Given an array `nums` with values 0, 1, or 2 (red, white, blue), sort them in-place. One-pass, constant space.

### Approach: Three Pointers
- `low`: boundary for 0s (next position to place 0)
- `mid`: current element being examined
- `high`: boundary for 2s (next position to place 2)

### Solution

```java
public void sortColors(int[] nums) {
    int low = 0, mid = 0, high = nums.length - 1;
    
    while (mid <= high) {
        if (nums[mid] == 0) {
            swap(nums, low, mid);
            low++;
            mid++;
        } else if (nums[mid] == 1) {
            mid++;
        } else { // nums[mid] == 2
            swap(nums, mid, high);
            high--;
            // Don't increment mid! Swapped element needs checking
        }
    }
}

private void swap(int[] nums, int i, int j) {
    int temp = nums[i]; nums[i] = nums[j]; nums[j] = temp;
}
```

### Dry Run

```
Input: nums = [2, 0, 2, 1, 1, 0]
low=0, mid=0, high=5

mid=0: nums[0]=2 → swap(0,5) → [0,0,2,1,1,2], high=4
mid=0: nums[0]=0 → swap(0,0) → [0,0,2,1,1,2], low=1, mid=1
mid=1: nums[1]=0 → swap(1,1) → [0,0,2,1,1,2], low=2, mid=2
mid=2: nums[2]=2 → swap(2,4) → [0,0,1,1,2,2], high=3
mid=2: nums[2]=1 → mid=3
mid=3: nums[3]=1 → mid=4
mid=4 > high=3 → done

Output: [0, 0, 1, 1, 2, 2]
```

### Complexity
- Time: O(n) — single pass
- Space: O(1)

---

## Problem 8: Move Zeroes (LeetCode #283) ⭐

### Problem
Move all 0s to the end of the array while maintaining the relative order of non-zero elements. In-place.

### Solution

```java
public void moveZeroes(int[] nums) {
    int slow = 0; // position to place next non-zero
    
    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) {
            int temp = nums[slow];
            nums[slow] = nums[fast];
            nums[fast] = temp;
            slow++;
        }
    }
}
```

### Dry Run

```
Input: nums = [0, 1, 0, 3, 12]
slow=0

fast=0: nums[0]=0 → skip
fast=1: nums[1]=1≠0 → swap(0,1) → [1,0,0,3,12], slow=1
fast=2: nums[2]=0 → skip
fast=3: nums[3]=3≠0 → swap(1,3) → [1,3,0,0,12], slow=2
fast=4: nums[4]=12≠0 → swap(2,4) → [1,3,12,0,0], slow=3

Output: [1, 3, 12, 0, 0]
```

### Complexity
- Time: O(n)
- Space: O(1)

---

## Summary — Two Pointers Key Insights

| Problem | Pointer Type | Key Insight |
|---------|-------------|-------------|
| Valid Palindrome | Opposite (inward) | Skip non-alphanumeric |
| Two Sum II | Opposite | Sum guides direction |
| 3Sum | Fixed + Opposite | Sort + fix one + two-pointer rest |
| Container With Most Water | Opposite | Move shorter line |
| Trapping Rain Water | Opposite + max tracking | Process smaller-max side |
| Sort Colors | Three pointers | Dutch National Flag |
| Move Zeroes | Same direction (slow/fast) | Slow = write position |

### When to Use Two Pointers
1. **Sorted array + find pair** → opposite direction pointers
2. **Palindrome check** → converge from both ends
3. **Partition/rearrange in-place** → slow/fast pointers
4. **Optimize brute-force O(n²) to O(n)** → sorted + two pointers
