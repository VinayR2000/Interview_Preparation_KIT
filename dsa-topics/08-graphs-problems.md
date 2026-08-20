# 8. Graphs — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Number of Islands (NeetCode #200) ⭐⭐⭐

### Problem
Given a 2D grid of '1's (land) and '0's (water), count the number of islands.

### Approach: DFS flood fill
- For each '1', DFS to mark all connected land as visited
- Each DFS call = one island

### Solution

```java
public int numIslands(char[][] grid) {
    int count = 0;
    
    for (int i = 0; i < grid.length; i++) {
        for (int j = 0; j < grid[0].length; j++) {
            if (grid[i][j] == '1') {
                dfs(grid, i, j);
                count++;
            }
        }
    }
    return count;
}

private void dfs(char[][] grid, int r, int c) {
    if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] == '0') {
        return;
    }
    grid[r][c] = '0'; // mark visited
    dfs(grid, r + 1, c);
    dfs(grid, r - 1, c);
    dfs(grid, r, c + 1);
    dfs(grid, r, c - 1);
}
```

### Dry Run

```
Input:
  1 1 0 0 0
  1 1 0 0 0
  0 0 1 0 0
  0 0 0 1 1

(0,0)='1' → DFS marks (0,0),(0,1),(1,0),(1,1) as '0'. count=1
(0,2)='0' → skip
...
(2,2)='1' → DFS marks (2,2). count=2
...
(3,3)='1' → DFS marks (3,3),(3,4). count=3

Output: 3
```

### Complexity
- Time: O(m × n)
- Space: O(m × n) — recursion stack worst case

---

## Problem 2: Clone Graph (NeetCode #133) ⭐⭐

### Problem
Given a reference to a node in a connected undirected graph, return a deep copy.

### Solution

```java
public Node cloneGraph(Node node) {
    if (node == null) return null;
    
    Map<Node, Node> visited = new HashMap<>();
    return dfs(node, visited);
}

private Node dfs(Node node, Map<Node, Node> visited) {
    if (visited.containsKey(node)) return visited.get(node);
    
    Node clone = new Node(node.val);
    visited.put(node, clone);
    
    for (Node neighbor : node.neighbors) {
        clone.neighbors.add(dfs(neighbor, visited));
    }
    return clone;
}
```

### Complexity
- Time: O(V + E)
- Space: O(V)

---

## Problem 3: Course Schedule (NeetCode #207) ⭐⭐⭐

### Problem
There are `numCourses` courses with prerequisites. Determine if you can finish all courses (detect cycle in directed graph).

### Approach: DFS with 3 states (topological sort)
- 0 = unvisited, 1 = visiting (in current DFS path), 2 = visited (completed)
- If we reach a node in state 1 → CYCLE!

### Solution

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    List<List<Integer>> graph = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) graph.add(new ArrayList<>());
    
    for (int[] pre : prerequisites) {
        graph.get(pre[1]).add(pre[0]); // pre[1] → pre[0] (must take pre[1] first)
    }
    
    int[] state = new int[numCourses]; // 0=unvisited, 1=visiting, 2=done
    
    for (int i = 0; i < numCourses; i++) {
        if (hasCycle(graph, i, state)) return false;
    }
    return true;
}

private boolean hasCycle(List<List<Integer>> graph, int node, int[] state) {
    if (state[node] == 1) return true;  // cycle detected!
    if (state[node] == 2) return false; // already processed
    
    state[node] = 1; // mark visiting
    
    for (int neighbor : graph.get(node)) {
        if (hasCycle(graph, neighbor, state)) return true;
    }
    
    state[node] = 2; // mark done
    return false;
}
```

### Dry Run

```
Input: numCourses=4, prerequisites=[[1,0],[2,0],[3,1],[3,2]]
Graph: 0→[1,2], 1→[3], 2→[3]

DFS from 0: state[0]=1
  → 1: state[1]=1
    → 3: state[3]=1, no neighbors, state[3]=2
    state[1]=2
  → 2: state[2]=1
    → 3: state[3]=2 → skip (done)
    state[2]=2
  state[0]=2

All nodes processed, no cycle → return true

Output: true (can finish all courses)
```

### Complexity
- Time: O(V + E)
- Space: O(V + E)

---

## Problem 4: Course Schedule II — Topological Sort (NeetCode #210) ⭐⭐⭐

### Problem
Return the ordering of courses (topological order). If impossible, return empty.

### Solution (Kahn's BFS)

```java
public int[] findOrder(int numCourses, int[][] prerequisites) {
    List<List<Integer>> graph = new ArrayList<>();
    int[] indegree = new int[numCourses];
    
    for (int i = 0; i < numCourses; i++) graph.add(new ArrayList<>());
    
    for (int[] pre : prerequisites) {
        graph.get(pre[1]).add(pre[0]);
        indegree[pre[0]]++;
    }
    
    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < numCourses; i++) {
        if (indegree[i] == 0) queue.offer(i);
    }
    
    int[] order = new int[numCourses];
    int idx = 0;
    
    while (!queue.isEmpty()) {
        int course = queue.poll();
        order[idx++] = course;
        
        for (int next : graph.get(course)) {
            indegree[next]--;
            if (indegree[next] == 0) queue.offer(next);
        }
    }
    
    return idx == numCourses ? order : new int[0]; // empty if cycle
}
```

### Dry Run

```
Input: numCourses=4, prerequisites=[[1,0],[2,0],[3,1],[3,2]]
Graph: 0→[1,2], 1→[3], 2→[3]
Indegree: [0, 1, 1, 2]

Queue starts: [0] (indegree 0)

Poll 0: order=[0], reduce indegree of 1,2 → indegree=[0,0,0,2], queue=[1,2]
Poll 1: order=[0,1], reduce indegree of 3 → indegree=[0,0,0,1], queue=[2]
Poll 2: order=[0,1,2], reduce indegree of 3 → indegree=[0,0,0,0], queue=[3]
Poll 3: order=[0,1,2,3], queue=[]

idx=4 == numCourses → return [0,1,2,3]

Output: [0, 1, 2, 3]
```

### Complexity
- Time: O(V + E)
- Space: O(V + E)

---

## Problem 5: Pacific Atlantic Water Flow (NeetCode #417) ⭐⭐

### Problem
Given an island height matrix, find cells where water can flow to BOTH Pacific (top/left edges) and Atlantic (bottom/right edges).

### Approach: Reverse DFS from ocean edges
- DFS from Pacific border → mark all reachable cells
- DFS from Atlantic border → mark all reachable cells
- Answer = cells in BOTH sets

### Solution

```java
public List<List<Integer>> pacificAtlantic(int[][] heights) {
    int rows = heights.length, cols = heights[0].length;
    boolean[][] pacific = new boolean[rows][cols];
    boolean[][] atlantic = new boolean[rows][cols];
    
    // DFS from Pacific edges (top row + left col)
    for (int c = 0; c < cols; c++) dfs(heights, 0, c, pacific, Integer.MIN_VALUE);
    for (int r = 0; r < rows; r++) dfs(heights, r, 0, pacific, Integer.MIN_VALUE);
    
    // DFS from Atlantic edges (bottom row + right col)
    for (int c = 0; c < cols; c++) dfs(heights, rows - 1, c, atlantic, Integer.MIN_VALUE);
    for (int r = 0; r < rows; r++) dfs(heights, r, cols - 1, atlantic, Integer.MIN_VALUE);
    
    // Intersection
    List<List<Integer>> result = new ArrayList<>();
    for (int r = 0; r < rows; r++) {
        for (int c = 0; c < cols; c++) {
            if (pacific[r][c] && atlantic[r][c]) {
                result.add(Arrays.asList(r, c));
            }
        }
    }
    return result;
}

private void dfs(int[][] heights, int r, int c, boolean[][] visited, int prevHeight) {
    if (r < 0 || r >= heights.length || c < 0 || c >= heights[0].length) return;
    if (visited[r][c] || heights[r][c] < prevHeight) return;
    
    visited[r][c] = true;
    dfs(heights, r + 1, c, visited, heights[r][c]);
    dfs(heights, r - 1, c, visited, heights[r][c]);
    dfs(heights, r, c + 1, visited, heights[r][c]);
    dfs(heights, r, c - 1, visited, heights[r][c]);
}
```

### Complexity
- Time: O(m × n)
- Space: O(m × n)

---

## Problem 6: Rotting Oranges (NeetCode #994) ⭐⭐

### Problem
Every minute, fresh oranges adjacent to rotten ones become rotten. Return minutes until no fresh oranges remain, or -1 if impossible.

### Approach: Multi-source BFS
- Start BFS from ALL rotten oranges simultaneously

### Solution

```java
public int orangesRotting(int[][] grid) {
    Queue<int[]> queue = new LinkedList<>();
    int fresh = 0;
    
    for (int i = 0; i < grid.length; i++) {
        for (int j = 0; j < grid[0].length; j++) {
            if (grid[i][j] == 2) queue.offer(new int[]{i, j});
            else if (grid[i][j] == 1) fresh++;
        }
    }
    
    if (fresh == 0) return 0;
    
    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
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

### Dry Run

```
Input:
  2 1 1
  1 1 0
  0 1 1

Initial: queue=[(0,0)], fresh=6

Minute 1: process (0,0)
  (1,0) rotten, (0,1) rotten. fresh=4
  queue=[(1,0),(0,1)]

Minute 2: process (1,0),(0,1)
  (1,0)→(1,1) rotten. (0,1)→(0,2) rotten. fresh=2
  queue=[(1,1),(0,2)]

Minute 3: process (1,1),(0,2)
  (1,1)→(2,1) rotten. fresh=1
  queue=[(2,1)]

Minute 4: process (2,1)
  (2,1)→(2,2) rotten. fresh=0
  queue=[]

fresh==0 → return 4

Output: 4
```

### Complexity
- Time: O(m × n)
- Space: O(m × n)

---

## Problem 7: Word Ladder (NeetCode #127) ⭐⭐⭐

### Problem
Transform `beginWord` to `endWord`, changing one letter at a time. Each intermediate word must exist in `wordList`. Return minimum transformations.

### Approach: BFS (shortest path in unweighted graph)
- Each word is a node. Edge = one letter difference.

### Solution

```java
public int ladderLength(String beginWord, String endWord, List<String> wordList) {
    Set<String> wordSet = new HashSet<>(wordList);
    if (!wordSet.contains(endWord)) return 0;
    
    Queue<String> queue = new LinkedList<>();
    queue.offer(beginWord);
    Set<String> visited = new HashSet<>();
    visited.add(beginWord);
    int steps = 1;
    
    while (!queue.isEmpty()) {
        int size = queue.size();
        for (int i = 0; i < size; i++) {
            String word = queue.poll();
            
            char[] chars = word.toCharArray();
            for (int j = 0; j < chars.length; j++) {
                char original = chars[j];
                for (char c = 'a'; c <= 'z'; c++) {
                    if (c == original) continue;
                    chars[j] = c;
                    String newWord = new String(chars);
                    
                    if (newWord.equals(endWord)) return steps + 1;
                    if (wordSet.contains(newWord) && !visited.contains(newWord)) {
                        visited.add(newWord);
                        queue.offer(newWord);
                    }
                }
                chars[j] = original;
            }
        }
        steps++;
    }
    return 0;
}
```

### Dry Run

```
Input: beginWord="hit", endWord="cog", wordList=["hot","dot","dog","lot","log","cog"]

Step 1: queue=[hit]
  "hit" → try all 1-char changes: "hot" in wordSet! queue=[hot]

Step 2: queue=[hot]
  "hot" → "dot"✓, "lot"✓. queue=[dot, lot]

Step 3: queue=[dot, lot]
  "dot" → "dog"✓. "lot" → "log"✓. queue=[dog, log]

Step 4: queue=[dog, log]
  "dog" → "cog" == endWord! return 4+1=5

Output: 5 (hit → hot → dot → dog → cog)
```

### Complexity
- Time: O(n × m × 26) where n = words, m = word length
- Space: O(n × m)

---

## Problem 8: Graph Valid Tree (NeetCode #261) ⭐⭐

### Problem
Given n nodes and edges, determine if these edges make a valid tree (connected + no cycles).

### Solution (Union-Find)

```java
public boolean validTree(int n, int[][] edges) {
    // Tree: exactly n-1 edges + connected
    if (edges.length != n - 1) return false;
    
    int[] parent = new int[n];
    for (int i = 0; i < n; i++) parent[i] = i;
    
    for (int[] edge : edges) {
        int p1 = find(parent, edge[0]);
        int p2 = find(parent, edge[1]);
        if (p1 == p2) return false; // cycle!
        parent[p1] = p2;
    }
    return true; // n-1 edges + no cycle → connected tree
}

private int find(int[] parent, int x) {
    while (parent[x] != x) {
        parent[x] = parent[parent[x]]; // path compression
        x = parent[x];
    }
    return x;
}
```

### Complexity
- Time: O(n × α(n)) ≈ O(n) with path compression
- Space: O(n)

---

## Summary — Graph Problem Patterns

| Pattern | When to Use | Problems |
|---------|-------------|----------|
| Grid DFS (flood fill) | Connected components in matrix | Number of Islands, Pacific Atlantic |
| Multi-source BFS | Simultaneous spreading | Rotting Oranges, 01 Matrix |
| Cycle Detection (DFS 3-state) | Can you complete all tasks? | Course Schedule |
| Topological Sort (Kahn's) | Ordering with dependencies | Course Schedule II |
| BFS shortest path | Minimum transformations | Word Ladder |
| Union-Find | Dynamic connectivity, cycle detection | Graph Valid Tree, Redundant Connection |
| Clone/Copy | Deep copy graph structures | Clone Graph |
