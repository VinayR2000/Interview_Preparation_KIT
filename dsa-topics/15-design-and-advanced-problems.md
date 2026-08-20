# 15. Design & Advanced Problems — Must-Solve ⭐⭐⭐

---

## Problem 1: Design Twitter (NeetCode #355) ⭐⭐

### Problem
Design a simplified Twitter: postTweet, getNewsFeed (10 most recent from user + followed), follow, unfollow.

### Solution

```java
class Twitter {
    private int timestamp = 0;
    private Map<Integer, Set<Integer>> following;      // userId → set of followees
    private Map<Integer, List<int[]>> tweets;          // userId → [(time, tweetId)]
    
    public Twitter() {
        following = new HashMap<>();
        tweets = new HashMap<>();
    }
    
    public void postTweet(int userId, int tweetId) {
        tweets.computeIfAbsent(userId, k -> new ArrayList<>()).add(new int[]{timestamp++, tweetId});
    }
    
    public List<Integer> getNewsFeed(int userId) {
        // Merge K sorted lists (user's tweets + all followees' tweets)
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> b[0] - a[0]); // max heap by time
        
        // Add user's own tweets
        Set<Integer> users = new HashSet<>(following.getOrDefault(userId, Set.of()));
        users.add(userId);
        
        for (int user : users) {
            List<int[]> userTweets = tweets.getOrDefault(user, List.of());
            for (int[] tweet : userTweets) {
                pq.offer(tweet);
            }
        }
        
        List<Integer> feed = new ArrayList<>();
        while (!pq.isEmpty() && feed.size() < 10) {
            feed.add(pq.poll()[1]);
        }
        return feed;
    }
    
    public void follow(int followerId, int followeeId) {
        following.computeIfAbsent(followerId, k -> new HashSet<>()).add(followeeId);
    }
    
    public void unfollow(int followerId, int followeeId) {
        following.getOrDefault(followerId, Set.of()).remove(followeeId);
    }
}
```

### Complexity
- postTweet: O(1)
- getNewsFeed: O(n log n) where n = total tweets from user + followees
- follow/unfollow: O(1)

---

## Problem 2: Design Min/Max Stack (NeetCode #716) ⭐⭐

Already covered in topic 10. See Min Stack.

---

## Problem 3: Task Scheduler (NeetCode #621) ⭐⭐⭐

### Problem
Given tasks with cooldown `n` between same tasks, find minimum intervals to complete all tasks.

### Key Insight
- Most frequent task dictates structure
- Idle slots = gaps that can be filled with other tasks

### Solution

```java
public int leastInterval(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char task : tasks) freq[task - 'A']++;
    
    int maxFreq = 0, maxCount = 0;
    for (int f : freq) {
        if (f > maxFreq) { maxFreq = f; maxCount = 1; }
        else if (f == maxFreq) maxCount++;
    }
    
    // Formula: (maxFreq - 1) * (n + 1) + maxCount
    int intervals = (maxFreq - 1) * (n + 1) + maxCount;
    return Math.max(intervals, tasks.length); // can't be less than total tasks
}
```

### Dry Run

```
Input: tasks = ["A","A","A","B","B","B"], n = 2

freq: A=3, B=3. maxFreq=3, maxCount=2

intervals = (3-1) * (2+1) + 2 = 6 + 2 = 8
max(8, 6) = 8

Schedule: A B _ A B _ A B
          ─────── ─────── ──
          chunk1  chunk2  last

Output: 8
```

### Complexity
- Time: O(n), Space: O(1)

---

## Problem 4: Maximum Frequency Stack (NeetCode #895) ⭐⭐⭐

### Problem
Design a stack where `pop` removes the most frequent element (ties broken by most recent).

### Solution

```java
class FreqStack {
    private Map<Integer, Integer> freq;           // val → frequency
    private Map<Integer, Deque<Integer>> groups;  // frequency → stack of values
    private int maxFreq;
    
    public FreqStack() {
        freq = new HashMap<>();
        groups = new HashMap<>();
        maxFreq = 0;
    }
    
    public void push(int val) {
        int f = freq.merge(val, 1, Integer::sum);
        maxFreq = Math.max(maxFreq, f);
        groups.computeIfAbsent(f, k -> new ArrayDeque<>()).push(val);
    }
    
    public int pop() {
        int val = groups.get(maxFreq).pop();
        if (groups.get(maxFreq).isEmpty()) {
            groups.remove(maxFreq);
            maxFreq--;
        }
        freq.merge(val, -1, Integer::sum);
        return val;
    }
}
```

### Dry Run

```
push(5): freq={5:1}, groups={1:[5]}, maxFreq=1
push(7): freq={5:1,7:1}, groups={1:[7,5]}, maxFreq=1
push(5): freq={5:2,7:1}, groups={1:[7,5], 2:[5]}, maxFreq=2
push(7): freq={5:2,7:2}, groups={1:[7,5], 2:[7,5]}, maxFreq=2
push(4): freq={5:2,7:2,4:1}, groups={1:[4,7,5], 2:[7,5]}, maxFreq=2
push(5): freq={5:3,7:2,4:1}, groups={1:[4,7,5], 2:[7,5], 3:[5]}, maxFreq=3

pop(): groups[3]=[5] → pop 5. freq={5:2}. groups[3] empty → maxFreq=2. return 5
pop(): groups[2]=[7,5] → pop 7. freq={7:1}. return 7
pop(): groups[2]=[5] → pop 5. freq={5:1}. groups[2] empty → maxFreq=1. return 5
```

### Complexity
- push: O(1), pop: O(1)
- Space: O(n)

---

## Problem 5: Longest Valid Parentheses (LeetCode #32) ⭐⭐⭐

### Problem
Find the length of the longest valid (well-formed) parentheses substring.

### Solution (Stack)

```java
public int longestValidParentheses(String s) {
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(-1); // base index
    int maxLen = 0;
    
    for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) == '(') {
            stack.push(i);
        } else {
            stack.pop();
            if (stack.isEmpty()) {
                stack.push(i); // new base
            } else {
                maxLen = Math.max(maxLen, i - stack.peek());
            }
        }
    }
    return maxLen;
}
```

### Dry Run

```
Input: s = ")()())"

i=0(')'): pop -1, stack empty → push 0. stack=[0]
i=1('('): push 1. stack=[0,1]
i=2(')'): pop 1. maxLen=max(0, 2-0)=2. stack=[0]
i=3('('): push 3. stack=[0,3]
i=4(')'): pop 3. maxLen=max(2, 4-0)=4. stack=[0]
i=5(')'): pop 0, stack empty → push 5. stack=[5]

Output: 4 (substring "()()")
```

### Complexity
- Time: O(n), Space: O(n)

---

## Problem 6: Trapping Rain Water (already in Two Pointers #42) ⭐⭐⭐

See topic 03.

---

## Problem 7: Maximum Product Subarray (NeetCode #152) ⭐⭐⭐

### Problem
Find the contiguous subarray with the largest product.

### Key Insight
- Negative × Negative = Positive (keep track of min too!)

### Solution

```java
public int maxProduct(int[] nums) {
    int maxProd = nums[0], minProd = nums[0], result = nums[0];
    
    for (int i = 1; i < nums.length; i++) {
        if (nums[i] < 0) {
            // Swap: negative number flips min/max
            int temp = maxProd;
            maxProd = minProd;
            minProd = temp;
        }
        
        maxProd = Math.max(nums[i], maxProd * nums[i]);
        minProd = Math.min(nums[i], minProd * nums[i]);
        result = Math.max(result, maxProd);
    }
    return result;
}
```

### Dry Run

```
Input: nums = [2, 3, -2, 4]

i=0: maxProd=2, minProd=2, result=2
i=1: maxProd=max(3, 2*3)=6, minProd=min(3, 2*3)=3, result=6
i=2: nums[2]=-2<0 → swap: maxProd=3, minProd=6
     maxProd=max(-2, 3*-2)=-2, minProd=min(-2, 6*-2)=-12, result=6
i=3: maxProd=max(4, -2*4)=4, minProd=min(4, -12*4)=-48, result=6

Output: 6 (subarray [2,3])
```

### Complexity
- Time: O(n), Space: O(1)

---

## Problem 8: Implement HashMap (LeetCode #706) ⭐⭐

### Problem
Design a HashMap without using built-in hash table libraries.

### Solution (Chaining with array of linked lists)

```java
class MyHashMap {
    private static final int SIZE = 1000;
    private List<int[]>[] buckets;
    
    public MyHashMap() {
        buckets = new List[SIZE];
    }
    
    public void put(int key, int value) {
        int idx = key % SIZE;
        if (buckets[idx] == null) buckets[idx] = new ArrayList<>();
        
        for (int[] pair : buckets[idx]) {
            if (pair[0] == key) { pair[1] = value; return; }
        }
        buckets[idx].add(new int[]{key, value});
    }
    
    public int get(int key) {
        int idx = key % SIZE;
        if (buckets[idx] == null) return -1;
        
        for (int[] pair : buckets[idx]) {
            if (pair[0] == key) return pair[1];
        }
        return -1;
    }
    
    public void remove(int key) {
        int idx = key % SIZE;
        if (buckets[idx] == null) return;
        
        buckets[idx].removeIf(pair -> pair[0] == key);
    }
}
```

### Complexity
- Average: O(1) for all operations (with good hash function and load factor)
- Worst: O(n/SIZE) per operation

---

## Problem 9: Clone Graph (NeetCode #133) ⭐⭐

Already covered in Graph topic 08.

---

## Problem 10: Number of Connected Components (NeetCode #323) ⭐⭐

### Problem
Given n nodes and edges, find the number of connected components.

### Solution (Union-Find)

```java
public int countComponents(int n, int[][] edges) {
    int[] parent = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;
    int components = n;
    
    for (int[] edge : edges) {
        int p1 = find(parent, edge[0]);
        int p2 = find(parent, edge[1]);
        if (p1 != p2) {
            parent[p1] = p2;
            components--;
        }
    }
    return components;
}

private int find(int[] parent, int x) {
    while (parent[x] != x) {
        parent[x] = parent[parent[x]];
        x = parent[x];
    }
    return x;
}
```

### Complexity
- Time: O(E × α(n)) ≈ O(E)
- Space: O(n)

---

## Problem 11: Design Hit Counter (LeetCode #362) ⭐⭐

### Problem
Design a hit counter that counts hits in the past 5 minutes (300 seconds).

### Solution

```java
class HitCounter {
    private int[] times;
    private int[] hits;
    
    public HitCounter() {
        times = new int[300];
        hits = new int[300];
    }
    
    public void hit(int timestamp) {
        int idx = timestamp % 300;
        if (times[idx] != timestamp) {
            times[idx] = timestamp;
            hits[idx] = 1;
        } else {
            hits[idx]++;
        }
    }
    
    public int getHits(int timestamp) {
        int count = 0;
        for (int i = 0; i < 300; i++) {
            if (timestamp - times[i] < 300) {
                count += hits[i];
            }
        }
        return count;
    }
}
```

### Complexity
- hit: O(1)
- getHits: O(300) = O(1)
- Space: O(300) = O(1)

---

## Most Asked Problems by Company (Summary)

### Google
| Problem | Frequency |
|---------|-----------|
| Median of Two Sorted Arrays | Very High |
| Word Ladder / Word Search II | Very High |
| Alien Dictionary | High |
| Task Scheduler | High |
| Decode String | High |
| Longest Increasing Subsequence | High |

### Amazon
| Problem | Frequency |
|---------|-----------|
| Two Sum | Very High |
| LRU Cache | Very High |
| Merge Intervals | Very High |
| Number of Islands | Very High |
| Trapping Rain Water | High |
| Course Schedule | High |
| Word Break | High |
| Min Stack | High |

### Meta (Facebook)
| Problem | Frequency |
|---------|-----------|
| Valid Palindrome | Very High |
| Merge Intervals | Very High |
| Binary Tree Paths | High |
| Subarray Sum Equals K | High |
| Random Pick with Weight | High |
| Pow(x,n) | High |
| Longest Palindromic Substring | High |

### Microsoft
| Problem | Frequency |
|---------|-----------|
| Two Sum | Very High |
| Reverse Linked List | Very High |
| Valid Parentheses | Very High |
| Spiral Matrix | High |
| Merge K Sorted Lists | High |
| LRU Cache | High |
| String to Integer | High |

### Apple
| Problem | Frequency |
|---------|-----------|
| 3Sum | High |
| Container With Most Water | High |
| Product Except Self | High |
| Longest Consecutive Sequence | High |
| Sliding Window Maximum | High |
