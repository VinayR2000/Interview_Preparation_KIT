# 10. Stack, Heap & Intervals — Must-Solve Problems ⭐⭐⭐

---

## Stack Problems

---

### Problem 1: Valid Parentheses (NeetCode #20) ⭐

### Problem
Given a string containing just `(){}[]`, determine if it's valid.

### Solution

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    
    for (char c : s.toCharArray()) {
        if (c == '(' || c == '{' || c == '[') {
            stack.push(c);
        } else {
            if (stack.isEmpty()) return false;
            char top = stack.pop();
            if (c == ')' && top != '(') return false;
            if (c == '}' && top != '{') return false;
            if (c == ']' && top != '[') return false;
        }
    }
    return stack.isEmpty();
}
```

### Dry Run

```
Input: s = "({[]})"

'(' → push. stack=['(']
'{' → push. stack=['(', '{']
'[' → push. stack=['(', '{', '[']
']' → pop '[', matches ✓. stack=['(', '{']
'}' → pop '{', matches ✓. stack=['(']
')' → pop '(', matches ✓. stack=[]

stack empty → return true
```

### Complexity
- Time: O(n), Space: O(n)

---

### Problem 2: Min Stack (NeetCode #155) ⭐⭐

### Problem
Design a stack that supports push, pop, top, and retrieving the minimum element in O(1).

### Solution

```java
class MinStack {
    private Deque<int[]> stack; // [value, currentMin]
    
    public MinStack() { stack = new ArrayDeque<>(); }
    
    public void push(int val) {
        int min = stack.isEmpty() ? val : Math.min(val, stack.peek()[1]);
        stack.push(new int[]{val, min});
    }
    
    public void pop() { stack.pop(); }
    
    public int top() { return stack.peek()[0]; }
    
    public int getMin() { return stack.peek()[1]; }
}
```

### Dry Run

```
push(-2): stack=[(-2, -2)]
push(0):  stack=[(-2,-2), (0,-2)]
push(-3): stack=[(-2,-2), (0,-2), (-3,-3)]
getMin(): peek()[1] = -3
pop():    stack=[(-2,-2), (0,-2)]
top():    peek()[0] = 0
getMin(): peek()[1] = -2
```

### Complexity
- All operations: O(1)
- Space: O(n)

---

### Problem 3: Daily Temperatures (NeetCode #739) ⭐⭐

### Problem
Given daily temperatures, for each day find how many days until a warmer temperature.

### Approach: Monotonic Stack (decreasing)

### Solution

```java
public int[] dailyTemperatures(int[] temperatures) {
    int n = temperatures.length;
    int[] result = new int[n];
    Deque<Integer> stack = new ArrayDeque<>(); // indices
    
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
            int prevDay = stack.pop();
            result[prevDay] = i - prevDay;
        }
        stack.push(i);
    }
    return result;
}
```

### Dry Run

```
Input: temperatures = [73, 74, 75, 71, 69, 72, 76, 73]

i=0(73): stack=[0]
i=1(74): 74>73 → pop 0, result[0]=1-0=1. stack=[1]
i=2(75): 75>74 → pop 1, result[1]=2-1=1. stack=[2]
i=3(71): stack=[2,3]
i=4(69): stack=[2,3,4]
i=5(72): 72>69 → pop 4, result[4]=5-4=1. 72>71 → pop 3, result[3]=5-3=2. stack=[2,5]
i=6(76): 76>72 → pop 5, result[5]=6-5=1. 76>75 → pop 2, result[2]=6-2=4. stack=[6]
i=7(73): stack=[6,7]

Output: [1, 1, 4, 2, 1, 1, 0, 0]
```

### Complexity
- Time: O(n), Space: O(n)

---

### Problem 4: Largest Rectangle in Histogram (NeetCode #84) ⭐⭐⭐

### Problem
Find the largest rectangular area in a histogram.

### Approach: Monotonic Stack
- For each bar, find how far it can extend left and right

### Solution

```java
public int largestRectangleArea(int[] heights) {
    int n = heights.length;
    Deque<Integer> stack = new ArrayDeque<>();
    int maxArea = 0;
    
    for (int i = 0; i <= n; i++) {
        int currHeight = (i == n) ? 0 : heights[i]; // sentinel
        
        while (!stack.isEmpty() && currHeight < heights[stack.peek()]) {
            int height = heights[stack.pop()];
            int width = stack.isEmpty() ? i : i - stack.peek() - 1;
            maxArea = Math.max(maxArea, height * width);
        }
        stack.push(i);
    }
    return maxArea;
}
```

### Dry Run

```
Input: heights = [2, 1, 5, 6, 2, 3]

i=0(2): stack=[0]
i=1(1): 1<2 → pop 0, height=2, width=1 (stack empty → i=1), area=2. stack=[1]
i=2(5): stack=[1,2]
i=3(6): stack=[1,2,3]
i=4(2): 2<6 → pop 3, h=6, w=4-2-1=1, area=6.
        2<5 → pop 2, h=5, w=4-1-1=2, area=10. maxArea=10. stack=[1,4]
i=5(3): stack=[1,4,5]
i=6(0): 0<3 → pop 5, h=3, w=6-4-1=1, area=3.
        0<2 → pop 4, h=2, w=6-1-1=4, area=8.
        0<1 → pop 1, h=1, w=6 (empty), area=6.

maxArea = 10

Output: 10 (bars at index 2,3 with height 5, width 2)
```

### Complexity
- Time: O(n), Space: O(n)

---

## Heap (Priority Queue) Problems

---

### Problem 5: Kth Largest Element (NeetCode #215) ⭐⭐

### Problem
Find the kth largest element in an unsorted array.

### Solution (Min-Heap of size k)

```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    
    for (int num : nums) {
        minHeap.offer(num);
        if (minHeap.size() > k) {
            minHeap.poll(); // remove smallest
        }
    }
    return minHeap.peek(); // kth largest
}
```

### Dry Run

```
Input: nums = [3, 2, 1, 5, 6, 4], k = 2

num=3: heap=[3]
num=2: heap=[2,3]
num=1: heap=[1,2,3], size>2 → poll 1. heap=[2,3]
num=5: heap=[2,3,5], size>2 → poll 2. heap=[3,5]
num=6: heap=[3,5,6], size>2 → poll 3. heap=[5,6]
num=4: heap=[4,5,6], size>2 → poll 4. heap=[5,6]

peek() = 5

Output: 5 (2nd largest)
```

### Complexity
- Time: O(n log k)
- Space: O(k)

---

### Problem 6: Find Median from Data Stream (NeetCode #295) ⭐⭐⭐

### Problem
Design a data structure that supports adding numbers and finding the median.

### Approach: Two heaps
- Max-heap (left half) + Min-heap (right half)
- Keep balanced: sizes differ by at most 1

### Solution

```java
class MedianFinder {
    PriorityQueue<Integer> maxHeap; // left half (smaller numbers)
    PriorityQueue<Integer> minHeap; // right half (larger numbers)
    
    public MedianFinder() {
        maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        minHeap = new PriorityQueue<>();
    }
    
    public void addNum(int num) {
        maxHeap.offer(num);
        minHeap.offer(maxHeap.poll()); // ensure max of left ≤ min of right
        
        if (minHeap.size() > maxHeap.size()) {
            maxHeap.offer(minHeap.poll()); // balance sizes
        }
    }
    
    public double findMedian() {
        if (maxHeap.size() > minHeap.size()) {
            return maxHeap.peek();
        }
        return (maxHeap.peek() + minHeap.peek()) / 2.0;
    }
}
```

### Dry Run

```
addNum(1): maxHeap=[1], minHeap=[]. Median=1
addNum(2): maxHeap offer 2→[2,1]. Move top to min: maxHeap=[1], minHeap=[2].
           min.size>max.size → move back: maxHeap=[2,1]... 
           
Let me redo:
addNum(1): maxHeap.offer(1)→[1]. minHeap.offer(maxHeap.poll()=1)→[1]. 
           min.size(1) > max.size(0) → maxHeap.offer(minHeap.poll()=1). maxHeap=[1], minHeap=[]
           Median = 1.0

addNum(2): maxHeap.offer(2)→[2,1]. minHeap.offer(maxHeap.poll()=2)→[2].
           min.size(1) == max.size(1). Done. maxHeap=[1], minHeap=[2]
           Median = (1+2)/2 = 1.5

addNum(3): maxHeap.offer(3)→[3,1]. minHeap.offer(maxHeap.poll()=3)→[2,3].
           min.size(2) > max.size(1) → maxHeap.offer(minHeap.poll()=2). 
           maxHeap=[2,1], minHeap=[3]
           Median = 2.0
```

### Complexity
- addNum: O(log n)
- findMedian: O(1)
- Space: O(n)

---

## Interval Problems

---

### Problem 7: Merge Intervals (NeetCode #56) ⭐⭐⭐

### Problem
Given a collection of intervals, merge all overlapping intervals.

### Solution

```java
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> result = new ArrayList<>();
    result.add(intervals[0]);
    
    for (int i = 1; i < intervals.length; i++) {
        int[] last = result.get(result.size() - 1);
        
        if (intervals[i][0] <= last[1]) {
            // Overlapping → merge
            last[1] = Math.max(last[1], intervals[i][1]);
        } else {
            // Non-overlapping → add new
            result.add(intervals[i]);
        }
    }
    return result.toArray(new int[result.size()][]);
}
```

### Dry Run

```
Input: [[1,3],[2,6],[8,10],[15,18]]
After sort: [[1,3],[2,6],[8,10],[15,18]] (already sorted)

result = [[1,3]]
[2,6]: 2 ≤ 3 → merge! last=[1, max(3,6)]=[1,6]. result=[[1,6]]
[8,10]: 8 > 6 → add. result=[[1,6],[8,10]]
[15,18]: 15 > 10 → add. result=[[1,6],[8,10],[15,18]]

Output: [[1,6],[8,10],[15,18]]
```

### Complexity
- Time: O(n log n) — sorting
- Space: O(n)

---

### Problem 8: Non-Overlapping Intervals (NeetCode #435) ⭐⭐

### Problem
Find the minimum number of intervals to remove to make the rest non-overlapping.

### Approach: Greedy — sort by end time, always keep interval with earliest end

### Solution

```java
public int eraseOverlapIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]); // sort by end
    
    int count = 0;
    int prevEnd = Integer.MIN_VALUE;
    
    for (int[] interval : intervals) {
        if (interval[0] >= prevEnd) {
            prevEnd = interval[1]; // keep this interval
        } else {
            count++; // remove this interval (overlaps)
        }
    }
    return count;
}
```

### Dry Run

```
Input: [[1,2],[2,3],[3,4],[1,3]]
After sort by end: [[1,2],[2,3],[1,3],[3,4]]

prevEnd = -∞
[1,2]: 1 >= -∞ → keep. prevEnd=2
[2,3]: 2 >= 2 → keep. prevEnd=3
[1,3]: 1 < 3 → REMOVE! count=1
[3,4]: 3 >= 3 → keep. prevEnd=4

Output: 1 (remove [1,3])
```

### Complexity
- Time: O(n log n)
- Space: O(1)

---

### Problem 9: Meeting Rooms II (NeetCode #253) ⭐⭐

### Problem
Given meeting time intervals, find the minimum number of conference rooms required.

### Solution (Min-Heap)

```java
public int minMeetingRooms(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]); // sort by start
    PriorityQueue<Integer> minHeap = new PriorityQueue<>(); // end times
    
    for (int[] interval : intervals) {
        if (!minHeap.isEmpty() && interval[0] >= minHeap.peek()) {
            minHeap.poll(); // reuse room (earliest ending meeting is done)
        }
        minHeap.offer(interval[1]); // allocate room with this end time
    }
    return minHeap.size();
}
```

### Dry Run

```
Input: [[0,30],[5,10],[15,20]]
Sorted: [[0,30],[5,10],[15,20]]

[0,30]: heap empty → offer 30. heap=[30]. rooms=1
[5,10]: 5 < 30 (can't reuse) → offer 10. heap=[10,30]. rooms=2
[15,20]: 15 >= 10 (reuse!) → poll 10, offer 20. heap=[20,30]. rooms=2

Output: 2
```

### Complexity
- Time: O(n log n)
- Space: O(n)

---

## Summary

| Pattern | Problems | Key Data Structure |
|---------|----------|-------------------|
| Matching/nesting | Valid Parentheses | Stack |
| Track min/max | Min Stack, Daily Temperatures | Stack (monotonic) |
| Histogram area | Largest Rectangle | Monotonic Stack |
| Top-K / streaming | Kth Largest, Median | Heap (Priority Queue) |
| Merge overlapping | Merge Intervals | Sort + linear scan |
| Scheduling | Meeting Rooms, Non-overlapping | Sort + Heap/Greedy |
