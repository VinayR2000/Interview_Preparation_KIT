# 7. Trees — Must-Solve Problems ⭐⭐⭐

---

## Problem 1: Invert Binary Tree (NeetCode #226) ⭐

### Problem
Invert a binary tree (mirror it).

### Solution

```java
public TreeNode invertTree(TreeNode root) {
    if (root == null) return null;
    
    TreeNode left = invertTree(root.left);
    TreeNode right = invertTree(root.right);
    
    root.left = right;
    root.right = left;
    return root;
}
```

### Dry Run

```
Input:      4              Output:     4
          /   \                      /   \
         2     7          →         7     2
        / \   / \                  / \   / \
       1   3 6   9                9   6 3   1

invertTree(4):
  invertTree(2) → swaps children → returns 2 (with 3,1)
  invertTree(7) → swaps children → returns 7 (with 9,6)
  swap: root.left=7, root.right=2
  return 4
```

### Complexity
- Time: O(n) — visit every node
- Space: O(h) — recursion stack (h = height)

---

## Problem 2: Maximum Depth of Binary Tree (NeetCode #104) ⭐

### Solution

```java
public int maxDepth(TreeNode root) {
    if (root == null) return 0;
    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
}
```

### Complexity
- Time: O(n), Space: O(h)

---

## Problem 3: Same Tree (NeetCode #100) ⭐

### Solution

```java
public boolean isSameTree(TreeNode p, TreeNode q) {
    if (p == null && q == null) return true;
    if (p == null || q == null) return false;
    if (p.val != q.val) return false;
    return isSameTree(p.left, q.left) && isSameTree(p.right, q.right);
}
```

---

## Problem 4: Validate Binary Search Tree (NeetCode #98) ⭐⭐⭐

### Problem
Determine if a binary tree is a valid BST.

### Key Insight
- Each node has a valid range: `(min, max)`
- Left child must be in range `(min, node.val)`
- Right child must be in range `(node.val, max)`

### Solution

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}

private boolean validate(TreeNode node, long min, long max) {
    if (node == null) return true;
    if (node.val <= min || node.val >= max) return false;
    return validate(node.left, min, node.val) && 
           validate(node.right, node.val, max);
}
```

### Dry Run

```
Input:      5
          /   \
         1     4
              / \
             3   6

validate(5, -∞, +∞): 5 > -∞ and 5 < +∞ ✓
  validate(1, -∞, 5): 1 > -∞ and 1 < 5 ✓
    validate(null) → true
  validate(4, 5, +∞): 4 > 5? NO! → return false

Output: false (4 is in right subtree of 5 but 4 < 5)
```

### Complexity
- Time: O(n), Space: O(h)

---

## Problem 5: Lowest Common Ancestor of BST (NeetCode #235) ⭐⭐

### Problem
Find LCA of two nodes in a BST.

### Key Insight (BST Property)
- If both p and q are smaller than root → LCA is in left subtree
- If both are larger → LCA is in right subtree
- Otherwise, root IS the LCA (split point)

### Solution

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    while (root != null) {
        if (p.val < root.val && q.val < root.val) {
            root = root.left;
        } else if (p.val > root.val && q.val > root.val) {
            root = root.right;
        } else {
            return root; // split point = LCA
        }
    }
    return null;
}
```

### Dry Run

```
Input: root=6, p=2, q=8
         6
       /   \
      2     8
     / \   / \
    0   4 7   9

root=6: p=2 < 6 but q=8 > 6 → SPLIT → return 6

Input: root=6, p=2, q=4
root=6: both 2,4 < 6 → go left
root=2: p=2 ≤ root but q=4 > 2 → SPLIT → return 2

Output: 6 (first case), 2 (second case)
```

### Complexity
- Time: O(h) — follows one path
- Space: O(1) iterative

---

## Problem 6: Binary Tree Level Order Traversal (NeetCode #102) ⭐⭐

### Solution

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> result = new ArrayList<>();
    if (root == null) return result;
    
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    
    while (!queue.isEmpty()) {
        int size = queue.size();
        List<Integer> level = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        result.add(level);
    }
    return result;
}
```

### Dry Run

```
Input:     3
         /   \
        9    20
            /  \
           15   7

Queue: [3], result=[]
  Level 0: poll 3, add children [9,20]. level=[3]
Queue: [9, 20], result=[[3]]
  Level 1: poll 9 (no children), poll 20 (add 15,7). level=[9,20]
Queue: [15, 7], result=[[3],[9,20]]
  Level 2: poll 15, poll 7. level=[15,7]

Output: [[3], [9,20], [15,7]]
```

### Complexity
- Time: O(n), Space: O(n) — queue holds at most one level

---

## Problem 7: Diameter of Binary Tree (NeetCode #543) ⭐⭐

### Problem
Find the length of the longest path between any two nodes (may not pass through root).

### Key Insight
- At each node, diameter through that node = leftHeight + rightHeight
- Track global maximum

### Solution

```java
private int diameter = 0;

public int diameterOfBinaryTree(TreeNode root) {
    height(root);
    return diameter;
}

private int height(TreeNode node) {
    if (node == null) return 0;
    int left = height(node.left);
    int right = height(node.right);
    diameter = Math.max(diameter, left + right); // update diameter
    return 1 + Math.max(left, right);            // return height
}
```

### Dry Run

```
Input:     1
         /   \
        2     3
       / \
      4   5

height(4) = 0+0, diameter=max(0,0)=0, return 1
height(5) = 0+0, diameter=max(0,0)=0, return 1
height(2) = left=1, right=1, diameter=max(0,1+1)=2, return 1+max(1,1)=2
height(3) = 0+0, diameter=2, return 1
height(1) = left=2, right=1, diameter=max(2,2+1)=3, return 1+max(2,1)=3

Output: 3 (path: 4→2→1→3 or 5→2→1→3)
```

### Complexity
- Time: O(n), Space: O(h)

---

## Problem 8: Subtree of Another Tree (NeetCode #572) ⭐⭐

### Solution

```java
public boolean isSubtree(TreeNode root, TreeNode subRoot) {
    if (root == null) return false;
    if (isSameTree(root, subRoot)) return true;
    return isSubtree(root.left, subRoot) || isSubtree(root.right, subRoot);
}

private boolean isSameTree(TreeNode p, TreeNode q) {
    if (p == null && q == null) return true;
    if (p == null || q == null) return false;
    return p.val == q.val && isSameTree(p.left, q.left) && isSameTree(p.right, q.right);
}
```

### Complexity
- Time: O(m × n) worst case
- Space: O(h)

---

## Problem 9: Binary Tree Maximum Path Sum (NeetCode #124) ⭐⭐⭐

### Problem
Find the maximum path sum. A path can start and end at any node.

### Key Insight
- At each node, max path through it = node.val + leftGain + rightGain
- But when returning to parent, can only use ONE side (path can't fork)
- Gain from a subtree = max(0, subtreeMax) — ignore negative gains

### Solution

```java
private int maxSum = Integer.MIN_VALUE;

public int maxPathSum(TreeNode root) {
    maxGain(root);
    return maxSum;
}

private int maxGain(TreeNode node) {
    if (node == null) return 0;
    
    int leftGain = Math.max(0, maxGain(node.left));   // ignore negative
    int rightGain = Math.max(0, maxGain(node.right));
    
    // Path through this node (both sides)
    int pathSum = node.val + leftGain + rightGain;
    maxSum = Math.max(maxSum, pathSum);
    
    // Return max gain to parent (can only choose one side)
    return node.val + Math.max(leftGain, rightGain);
}
```

### Dry Run

```
Input:     -10
          /    \
         9     20
              /  \
             15   7

maxGain(9):  leftGain=0, rightGain=0, pathSum=9, maxSum=9. return 9
maxGain(15): pathSum=15, maxSum=15. return 15
maxGain(7):  pathSum=7, maxSum=15. return 7
maxGain(20): leftGain=15, rightGain=7, pathSum=20+15+7=42, maxSum=42. return 20+max(15,7)=35
maxGain(-10): leftGain=max(0,9)=9, rightGain=max(0,35)=35
  pathSum=-10+9+35=34, maxSum=max(42,34)=42
  return -10+max(9,35)=25

Output: 42 (path: 15→20→7)
```

### Complexity
- Time: O(n), Space: O(h)

---

## Problem 10: Serialize and Deserialize Binary Tree (NeetCode #297) ⭐⭐⭐

### Problem
Design an algorithm to serialize a binary tree to a string and deserialize it back.

### Solution (Preorder with null markers)

```java
public class Codec {
    public String serialize(TreeNode root) {
        StringBuilder sb = new StringBuilder();
        serializeHelper(root, sb);
        return sb.toString();
    }
    
    private void serializeHelper(TreeNode node, StringBuilder sb) {
        if (node == null) {
            sb.append("N,");
            return;
        }
        sb.append(node.val).append(",");
        serializeHelper(node.left, sb);
        serializeHelper(node.right, sb);
    }
    
    public TreeNode deserialize(String data) {
        Queue<String> queue = new LinkedList<>(Arrays.asList(data.split(",")));
        return deserializeHelper(queue);
    }
    
    private TreeNode deserializeHelper(Queue<String> queue) {
        String val = queue.poll();
        if (val.equals("N")) return null;
        TreeNode node = new TreeNode(Integer.parseInt(val));
        node.left = deserializeHelper(queue);
        node.right = deserializeHelper(queue);
        return node;
    }
}
```

### Dry Run

```
Serialize:    1         → "1,2,N,N,3,4,N,N,5,N,N,"
            /   \
           2     3
                / \
               4   5

Deserialize: "1,2,N,N,3,4,N,N,5,N,N,"
  poll "1" → node(1)
    left: poll "2" → node(2)
      left: poll "N" → null
      right: poll "N" → null
    right: poll "3" → node(3)
      left: poll "4" → node(4)
        left: poll "N" → null
        right: poll "N" → null
      right: poll "5" → node(5)
        left: poll "N" → null
        right: poll "N" → null
```

### Complexity
- Time: O(n) for both operations
- Space: O(n)

---

## Summary — Tree Problem Patterns

| Pattern | When to Use | Problems |
|---------|-------------|----------|
| Recursive DFS (postorder) | Need info from children first | Max Depth, Diameter, Max Path Sum |
| Recursive DFS (preorder) | Process node before children | Serialize, Validate BST |
| BFS (level order) | Level-by-level processing | Level Order Traversal, Right Side View |
| BST property | Ordered search, valid range | Validate BST, LCA of BST, Search BST |
| Global variable + DFS | Track max/min across all nodes | Diameter, Max Path Sum |
| Two-tree comparison | Compare structures | Same Tree, Subtree, Symmetric |
