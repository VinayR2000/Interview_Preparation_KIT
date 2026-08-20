# 13. Tries & Advanced Graphs — Must-Solve Problems ⭐⭐⭐

---

## Trie (Prefix Tree)

---

### Problem 1: Implement Trie (NeetCode #208) ⭐⭐⭐

### Problem
Implement a trie with insert, search, and startsWith.

### Solution

```java
class Trie {
    private TrieNode root;
    
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd = false;
    }
    
    public Trie() { root = new TrieNode(); }
    
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) {
                node.children[idx] = new TrieNode();
            }
            node = node.children[idx];
        }
        node.isEnd = true;
    }
    
    public boolean search(String word) {
        TrieNode node = findNode(word);
        return node != null && node.isEnd;
    }
    
    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }
    
    private TrieNode findNode(String s) {
        TrieNode node = root;
        for (char c : s.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) return null;
            node = node.children[idx];
        }
        return node;
    }
}
```

### Dry Run

```
insert("apple"):
  root → a → p → p → l → e (isEnd=true)

insert("app"):
  root → a → p → p (isEnd=true)  (reuses existing path)

search("apple"): root→a→p→p→l→e, isEnd=true → true
search("app"):   root→a→p→p, isEnd=true → true
search("ap"):    root→a→p, isEnd=false → false
startsWith("ap"): root→a→p, node exists → true
```

### Complexity
- Insert: O(m) where m = word length
- Search: O(m)
- Space: O(total characters across all words × 26)

---

### Problem 2: Design Add and Search Words (NeetCode #211) ⭐⭐⭐

### Problem
Design a data structure that supports adding words and searching with `.` wildcard (matches any letter).

### Solution

```java
class WordDictionary {
    private TrieNode root;
    
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isEnd = false;
    }
    
    public WordDictionary() { root = new TrieNode(); }
    
    public void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) node.children[idx] = new TrieNode();
            node = node.children[idx];
        }
        node.isEnd = true;
    }
    
    public boolean search(String word) {
        return dfs(word, 0, root);
    }
    
    private boolean dfs(String word, int idx, TrieNode node) {
        if (idx == word.length()) return node.isEnd;
        
        char c = word.charAt(idx);
        if (c == '.') {
            // Try all 26 children
            for (TrieNode child : node.children) {
                if (child != null && dfs(word, idx + 1, child)) return true;
            }
            return false;
        } else {
            TrieNode child = node.children[c - 'a'];
            return child != null && dfs(word, idx + 1, child);
        }
    }
}
```

### Dry Run

```
addWord("bad"), addWord("dad"), addWord("mad")

search(".ad"):
  '.' → try all children at root level:
    'b' exists → dfs("ad", 1, node_b):
      'a' exists → dfs("d", 2, node_a):
        'd' exists, isEnd=true → return true!

search("b.."):
  'b' → node_b, '.' → try 'a' → node_a, '.' → try 'd' → isEnd=true → true

Output: true, true
```

### Complexity
- addWord: O(m)
- search: O(m) for exact, O(26^m) worst case with all wildcards (rare in practice)

---

### Problem 3: Word Search II (NeetCode #212) ⭐⭐⭐

### Problem
Given a board and a list of words, find all words that exist in the board (adjacent cells, no reuse per word).

### Approach: Trie + DFS (instead of DFS per word)
- Build trie from all words
- DFS from each cell, guided by trie (prune early)

### Solution

```java
public List<String> findWords(char[][] board, String[] words) {
    TrieNode root = buildTrie(words);
    List<String> result = new ArrayList<>();
    
    for (int i = 0; i < board.length; i++) {
        for (int j = 0; j < board[0].length; j++) {
            dfs(board, i, j, root, result);
        }
    }
    return result;
}

private void dfs(char[][] board, int r, int c, TrieNode node, List<String> result) {
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return;
    
    char ch = board[r][c];
    if (ch == '#' || node.children[ch - 'a'] == null) return;
    
    node = node.children[ch - 'a'];
    if (node.word != null) {
        result.add(node.word);
        node.word = null; // avoid duplicates
    }
    
    board[r][c] = '#'; // mark visited
    dfs(board, r + 1, c, node, result);
    dfs(board, r - 1, c, node, result);
    dfs(board, r, c + 1, node, result);
    dfs(board, r, c - 1, node, result);
    board[r][c] = ch; // restore
}

private TrieNode buildTrie(String[] words) {
    TrieNode root = new TrieNode();
    for (String word : words) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (node.children[idx] == null) node.children[idx] = new TrieNode();
            node = node.children[idx];
        }
        node.word = word; // store word at end node
    }
    return root;
}

class TrieNode {
    TrieNode[] children = new TrieNode[26];
    String word = null;
}
```

### Complexity
- Time: O(m × n × 4^L) where L = max word length (but trie pruning makes it much faster)
- Space: O(total characters in words)

---

## Advanced Graph Problems

---

### Problem 4: Alien Dictionary (NeetCode #269) ⭐⭐⭐

### Problem
Given a sorted dictionary of an alien language, derive the character ordering (topological sort).

### Approach: Build graph from adjacent word comparisons → Topological Sort

### Solution

```java
public String alienOrder(String[] words) {
    Map<Character, Set<Character>> graph = new HashMap<>();
    Map<Character, Integer> indegree = new HashMap<>();
    
    // Initialize all characters
    for (String word : words) {
        for (char c : word.toCharArray()) {
            graph.putIfAbsent(c, new HashSet<>());
            indegree.putIfAbsent(c, 0);
        }
    }
    
    // Build graph from adjacent words
    for (int i = 0; i < words.length - 1; i++) {
        String w1 = words[i], w2 = words[i + 1];
        
        // Invalid case: "abc" before "ab"
        if (w1.length() > w2.length() && w1.startsWith(w2)) return "";
        
        for (int j = 0; j < Math.min(w1.length(), w2.length()); j++) {
            if (w1.charAt(j) != w2.charAt(j)) {
                if (graph.get(w1.charAt(j)).add(w2.charAt(j))) {
                    indegree.merge(w2.charAt(j), 1, Integer::sum);
                }
                break; // only first difference matters
            }
        }
    }
    
    // Topological sort (Kahn's BFS)
    Queue<Character> queue = new LinkedList<>();
    for (char c : indegree.keySet()) {
        if (indegree.get(c) == 0) queue.offer(c);
    }
    
    StringBuilder sb = new StringBuilder();
    while (!queue.isEmpty()) {
        char c = queue.poll();
        sb.append(c);
        for (char next : graph.get(c)) {
            indegree.merge(next, -1, Integer::sum);
            if (indegree.get(next) == 0) queue.offer(next);
        }
    }
    
    return sb.length() == indegree.size() ? sb.toString() : "";
}
```

### Dry Run

```
Input: ["wrt", "wrf", "er", "ett", "rftt"]

Compare adjacent:
  "wrt" vs "wrf": t → f (first diff at index 2)
  "wrf" vs "er":  w → e (first diff at index 0)
  "er" vs "ett":  r → t (first diff at index 1)
  "ett" vs "rftt": e → r (first diff at index 0)

Graph: t→f, w→e, r→t, e→r
Indegree: w:0, t:1, f:1, e:1, r:1

BFS: queue=[w]
  w → e. indegree[e]=0. queue=[e]
  e → r. indegree[r]=0. queue=[r]
  r → t. indegree[t]=0. queue=[t]
  t → f. indegree[f]=0. queue=[f]

Output: "wertf"
```

### Complexity
- Time: O(C) where C = total characters in all words
- Space: O(unique characters)

---

### Problem 5: Network Delay Time / Dijkstra's (NeetCode #743) ⭐⭐⭐

### Problem
Given a network of `n` nodes with weighted edges, find the time for a signal to reach all nodes from source `k`.

### Solution (Dijkstra's Algorithm)

```java
public int networkDelayTime(int[][] times, int n, int k) {
    // Build adjacency list
    Map<Integer, List<int[]>> graph = new HashMap<>();
    for (int[] time : times) {
        graph.computeIfAbsent(time[0], x -> new ArrayList<>()).add(new int[]{time[1], time[2]});
    }
    
    // Dijkstra's: min-heap of [distance, node]
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{0, k});
    Map<Integer, Integer> dist = new HashMap<>();
    
    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int d = curr[0], node = curr[1];
        
        if (dist.containsKey(node)) continue; // already processed
        dist.put(node, d);
        
        if (!graph.containsKey(node)) continue;
        for (int[] neighbor : graph.get(node)) {
            int next = neighbor[0], weight = neighbor[1];
            if (!dist.containsKey(next)) {
                pq.offer(new int[]{d + weight, next});
            }
        }
    }
    
    if (dist.size() != n) return -1; // not all reachable
    return Collections.max(dist.values());
}
```

### Dry Run

```
Input: times=[[2,1,1],[2,3,1],[3,4,1]], n=4, k=2

Graph: 2→[(1,1),(3,1)], 3→[(4,1)]
pq=[(0,2)]

Poll (0,2): dist={2:0}. Add (1,1),(1,3) to pq.
Poll (1,1): dist={2:0, 1:1}. Node 1 has no outgoing.
Poll (1,3): dist={2:0, 1:1, 3:1}. Add (2,4).
Poll (2,4): dist={2:0, 1:1, 3:1, 4:2}.

All 4 nodes reached. max(0,1,1,2) = 2

Output: 2
```

### Complexity
- Time: O(E log V) — heap operations
- Space: O(V + E)

---

### Problem 6: Cheapest Flights Within K Stops (NeetCode #787) ⭐⭐⭐

### Problem
Find cheapest price from `src` to `dst` with at most `k` stops.

### Approach: Bellman-Ford (relaxation limited to k+1 iterations)

### Solution

```java
public int findCheapestPrice(int n, int[][] flights, int src, int dst, int k) {
    int[] prices = new int[n];
    Arrays.fill(prices, Integer.MAX_VALUE);
    prices[src] = 0;
    
    for (int i = 0; i <= k; i++) {
        int[] temp = Arrays.copyOf(prices, n); // copy to avoid using current round's updates
        
        for (int[] flight : flights) {
            int from = flight[0], to = flight[1], cost = flight[2];
            if (prices[from] == Integer.MAX_VALUE) continue;
            temp[to] = Math.min(temp[to], prices[from] + cost);
        }
        prices = temp;
    }
    return prices[dst] == Integer.MAX_VALUE ? -1 : prices[dst];
}
```

### Dry Run

```
Input: n=4, flights=[[0,1,100],[1,2,100],[2,0,100],[1,3,600],[2,3,200]], src=0, dst=3, k=1

Round 0 (0 stops, direct flights from src):
  prices=[0, MAX, MAX, MAX]
  0→1: temp[1]=min(MAX, 0+100)=100
  temp=[0, 100, MAX, MAX]

Round 1 (1 stop):
  prices=[0, 100, MAX, MAX]
  0→1: temp[1]=min(100, 100)=100
  1→2: temp[2]=min(MAX, 100+100)=200
  1→3: temp[3]=min(MAX, 100+600)=700
  temp=[0, 100, 200, 700]

prices[3]=700 (but wait — let's check 2→3 wasn't used because prices[2]=MAX in round 1)

Actually: round 1 uses prices=[0,100,MAX,MAX]. Since prices[2]=MAX, flight 2→3 skipped.
So cheapest with ≤1 stop: 0→1→3 = 700

Output: 700
```

### Complexity
- Time: O(k × E)
- Space: O(n)

---

### Problem 7: Redundant Connection (NeetCode #684) ⭐⭐

### Problem
Find the edge that makes an undirected graph NOT a tree (creates a cycle).

### Solution (Union-Find)

```java
public int[] findRedundantConnection(int[][] edges) {
    int n = edges.length;
    int[] parent = new int[n + 1];
    int[] rank = new int[n + 1];
    for (int i = 1; i <= n; i++) parent[i] = i;
    
    for (int[] edge : edges) {
        int p1 = find(parent, edge[0]);
        int p2 = find(parent, edge[1]);
        
        if (p1 == p2) return edge; // cycle detected!
        
        // Union by rank
        if (rank[p1] < rank[p2]) parent[p1] = p2;
        else if (rank[p1] > rank[p2]) parent[p2] = p1;
        else { parent[p2] = p1; rank[p1]++; }
    }
    return new int[]{};
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
- Time: O(n × α(n)) ≈ O(n)
- Space: O(n)

---

### Problem 8: Swim in Rising Water (NeetCode #778) ⭐⭐⭐

### Problem
Find minimum time to swim from (0,0) to (n-1,n-1) in a grid where you can only move to cells with elevation ≤ current time.

### Approach: Dijkstra's variant (min-heap by max elevation on path)

### Solution

```java
public int swimInWater(int[][] grid) {
    int n = grid.length;
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    pq.offer(new int[]{grid[0][0], 0, 0});
    boolean[][] visited = new boolean[n][n];
    visited[0][0] = true;
    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
    
    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int maxElev = curr[0], r = curr[1], c = curr[2];
        
        if (r == n - 1 && c == n - 1) return maxElev;
        
        for (int[] dir : dirs) {
            int nr = r + dir[0], nc = c + dir[1];
            if (nr >= 0 && nr < n && nc >= 0 && nc < n && !visited[nr][nc]) {
                visited[nr][nc] = true;
                pq.offer(new int[]{Math.max(maxElev, grid[nr][nc]), nr, nc});
            }
        }
    }
    return -1;
}
```

### Complexity
- Time: O(n² log n)
- Space: O(n²)

---

## Summary

| Problem | Pattern | Company Frequency |
|---------|---------|-------------------|
| Implement Trie | Trie basics | Amazon, Google, Microsoft |
| Word Search II | Trie + DFS | Google, Meta, Amazon |
| Add/Search Words | Trie + DFS wildcard | Meta, Amazon |
| Alien Dictionary | Topological Sort | Google, Meta, Airbnb |
| Network Delay (Dijkstra) | Shortest path weighted | Amazon, Google, Uber |
| Cheapest Flights K Stops | Bellman-Ford | Google, Amazon |
| Redundant Connection | Union-Find | Amazon, Google |
| Swim in Rising Water | Dijkstra on grid | Google |
