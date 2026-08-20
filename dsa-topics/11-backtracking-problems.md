# 11. Backtracking — Must-Solve Problems ⭐⭐⭐

---

## Core Pattern

```java
// Backtracking Template
void backtrack(state, choices, result) {
    if (isGoal(state)) {
        result.add(copy(state));
        return;
    }
    
    for (choice : choices) {
        if (isValid(choice)) {
            make(choice);           // choose
            backtrack(nextState);   // explore
            undo(choice);           // un-choose (backtrack)
        }
    }
}
```

---

## Problem 1: Subsets (NeetCode #78) ⭐⭐

### Problem
Given an integer array of unique elements, return all possible subsets.

### Solution

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, int start, List<Integer> current, List<List<Integer>> result) {
    result.add(new ArrayList<>(current)); // every state is valid subset
    
    for (int i = start; i < nums.length; i++) {
        current.add(nums[i]);                     // choose
        backtrack(nums, i + 1, current, result);  // explore
        current.remove(current.size() - 1);       // un-choose
    }
}
```

### Dry Run (Decision Tree)

```
Input: nums = [1, 2, 3]

                        []
               /        |        \
           [1]         [2]       [3]
          /   \         |
      [1,2]  [1,3]   [2,3]
        |
    [1,2,3]

Result: [[], [1], [1,2], [1,2,3], [1,3], [2], [2,3], [3]]
```

### Complexity
- Time: O(n × 2^n) — 2^n subsets, each takes O(n) to copy
- Space: O(n) — recursion depth

---

## Problem 2: Combination Sum (NeetCode #39) ⭐⭐

### Problem
Given candidates (no duplicates) and a target, find all unique combinations that sum to target. Same number may be used unlimited times.

### Solution

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(candidates, target, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] candidates, int remaining, int start, 
                       List<Integer> current, List<List<Integer>> result) {
    if (remaining == 0) {
        result.add(new ArrayList<>(current));
        return;
    }
    if (remaining < 0) return;
    
    for (int i = start; i < candidates.length; i++) {
        current.add(candidates[i]);
        backtrack(candidates, remaining - candidates[i], i, current, result); // i not i+1 (reuse!)
        current.remove(current.size() - 1);
    }
}
```

### Dry Run

```
Input: candidates = [2, 3, 6, 7], target = 7

backtrack(7, start=0):
  add 2, backtrack(5, start=0):
    add 2, backtrack(3, start=0):
      add 2, backtrack(1, start=0):
        add 2, backtrack(-1) → return (negative)
        add 3, backtrack(-2) → return
      remove 2
      add 3, backtrack(0) → FOUND! [2,2,3]
    remove 2
    add 3, backtrack(2, start=1):
      add 3, backtrack(-1) → return
    remove 3
  remove 2
  add 3, backtrack(4, start=1):
    add 3, backtrack(1) → no valid
  remove 3
  add 6, backtrack(1) → no valid
  add 7, backtrack(0) → FOUND! [7]

Output: [[2,2,3], [7]]
```

### Complexity
- Time: O(n^(T/M)) where T=target, M=min candidate
- Space: O(T/M) — max recursion depth

---

## Problem 3: Permutations (NeetCode #46) ⭐⭐

### Problem
Given an array of distinct integers, return all possible permutations.

### Solution

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> result = new ArrayList<>();
    backtrack(nums, new boolean[nums.length], new ArrayList<>(), result);
    return result;
}

private void backtrack(int[] nums, boolean[] used, List<Integer> current, 
                       List<List<Integer>> result) {
    if (current.size() == nums.length) {
        result.add(new ArrayList<>(current));
        return;
    }
    
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;
        
        used[i] = true;
        current.add(nums[i]);
        backtrack(nums, used, current, result);
        current.remove(current.size() - 1);
        used[i] = false;
    }
}
```

### Dry Run

```
Input: nums = [1, 2, 3]

Decision tree (showing choices at each level):
Level 0: choose 1 or 2 or 3
Level 1: choose remaining 2 options
Level 2: choose last remaining

[1] → [1,2] → [1,2,3] ✓
         → [1,3] → [1,3,2] ✓
[2] → [2,1] → [2,1,3] ✓
         → [2,3] → [2,3,1] ✓
[3] → [3,1] → [3,1,2] ✓
         → [3,2] → [3,2,1] ✓

Output: [[1,2,3],[1,3,2],[2,1,3],[2,3,1],[3,1,2],[3,2,1]]
```

### Complexity
- Time: O(n × n!) — n! permutations, each takes O(n) to copy
- Space: O(n) — recursion + used array

---

## Problem 4: Word Search (NeetCode #79) ⭐⭐⭐

### Problem
Given a board and a word, determine if the word exists in the grid via adjacent cells (no reuse).

### Solution

```java
public boolean exist(char[][] board, String word) {
    for (int i = 0; i < board.length; i++) {
        for (int j = 0; j < board[0].length; j++) {
            if (dfs(board, word, i, j, 0)) return true;
        }
    }
    return false;
}

private boolean dfs(char[][] board, String word, int r, int c, int idx) {
    if (idx == word.length()) return true; // found complete word
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return false;
    if (board[r][c] != word.charAt(idx)) return false;
    
    char temp = board[r][c];
    board[r][c] = '#'; // mark visited
    
    boolean found = dfs(board, word, r + 1, c, idx + 1)
                 || dfs(board, word, r - 1, c, idx + 1)
                 || dfs(board, word, r, c + 1, idx + 1)
                 || dfs(board, word, r, c - 1, idx + 1);
    
    board[r][c] = temp; // restore (backtrack)
    return found;
}
```

### Dry Run

```
Input: board = [["A","B","C","E"],    word = "ABCCED"
                ["S","F","C","S"],
                ["A","D","E","E"]]

Start (0,0)='A' matches word[0]='A' ✓
  → (0,1)='B' matches word[1]='B' ✓
    → (0,2)='C' matches word[2]='C' ✓
      → (1,2)='C' matches word[3]='C' ✓
        → (2,2)='E' matches word[4]='E' ✓
          → (2,1)='D' matches word[5]='D' ✓
            idx==6==word.length → return true!

Output: true
```

### Complexity
- Time: O(m × n × 4^L) where L = word length
- Space: O(L) — recursion depth

---

## Problem 5: N-Queens (NeetCode #51) ⭐⭐⭐

### Problem
Place n queens on an n×n chessboard so no two queens threaten each other.

### Solution

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> result = new ArrayList<>();
    char[][] board = new char[n][n];
    for (char[] row : board) Arrays.fill(row, '.');
    
    backtrack(board, 0, result);
    return result;
}

private void backtrack(char[][] board, int row, List<List<String>> result) {
    if (row == board.length) {
        result.add(construct(board));
        return;
    }
    
    for (int col = 0; col < board.length; col++) {
        if (isValid(board, row, col)) {
            board[row][col] = 'Q';
            backtrack(board, row + 1, result);
            board[row][col] = '.';
        }
    }
}

private boolean isValid(char[][] board, int row, int col) {
    // Check column
    for (int r = 0; r < row; r++) {
        if (board[r][col] == 'Q') return false;
    }
    // Check upper-left diagonal
    for (int r = row - 1, c = col - 1; r >= 0 && c >= 0; r--, c--) {
        if (board[r][c] == 'Q') return false;
    }
    // Check upper-right diagonal
    for (int r = row - 1, c = col + 1; r >= 0 && c < board.length; r--, c++) {
        if (board[r][c] == 'Q') return false;
    }
    return true;
}

private List<String> construct(char[][] board) {
    List<String> result = new ArrayList<>();
    for (char[] row : board) result.add(new String(row));
    return result;
}
```

### Complexity
- Time: O(n!) — at most n choices first row, n-1 second, etc.
- Space: O(n²) — board

---

## Problem 6: Palindrome Partitioning (NeetCode #131) ⭐⭐

### Problem
Partition string `s` such that every substring in the partition is a palindrome. Return all possible partitions.

### Solution

```java
public List<List<String>> partition(String s) {
    List<List<String>> result = new ArrayList<>();
    backtrack(s, 0, new ArrayList<>(), result);
    return result;
}

private void backtrack(String s, int start, List<String> current, List<List<String>> result) {
    if (start == s.length()) {
        result.add(new ArrayList<>(current));
        return;
    }
    
    for (int end = start; end < s.length(); end++) {
        if (isPalindrome(s, start, end)) {
            current.add(s.substring(start, end + 1));
            backtrack(s, end + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}

private boolean isPalindrome(String s, int left, int right) {
    while (left < right) {
        if (s.charAt(left++) != s.charAt(right--)) return false;
    }
    return true;
}
```

### Dry Run

```
Input: s = "aab"

start=0:
  end=0: "a" palindrome ✓ → [a]
    start=1:
      end=1: "a" ✓ → [a,a]
        start=2: "b" ✓ → [a,a,b] → FOUND!
      end=2: "ab" not palindrome ✗
  end=1: "aa" palindrome ✓ → [aa]
    start=2: "b" ✓ → [aa,b] → FOUND!
  end=2: "aab" not palindrome ✗

Output: [["a","a","b"], ["aa","b"]]
```

### Complexity
- Time: O(n × 2^n)
- Space: O(n)

---

## Problem 7: Letter Combinations of Phone Number (NeetCode #17) ⭐⭐

### Solution

```java
private static final String[] KEYS = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};

public List<String> letterCombinations(String digits) {
    List<String> result = new ArrayList<>();
    if (digits.isEmpty()) return result;
    backtrack(digits, 0, new StringBuilder(), result);
    return result;
}

private void backtrack(String digits, int idx, StringBuilder current, List<String> result) {
    if (idx == digits.length()) {
        result.add(current.toString());
        return;
    }
    
    String letters = KEYS[digits.charAt(idx) - '0'];
    for (char c : letters.toCharArray()) {
        current.append(c);
        backtrack(digits, idx + 1, current, result);
        current.deleteCharAt(current.length() - 1);
    }
}
```

### Complexity
- Time: O(4^n) where n = digits length (worst case: 4 letters per digit)
- Space: O(n)

---

## Summary — Backtracking Patterns

| Problem Type | Key Differences | Start Index |
|-------------|-----------------|-------------|
| Subsets | Add ALL states to result | `i+1` (no reuse) |
| Combinations | Add when target met | `i+1` or `i` (if reuse allowed) |
| Permutations | Add when length == n | `0` (use `used[]` array) |
| Grid search | Match characters sequentially | Adjacent cells |
| Partitioning | Every partition must be valid | `end+1` |

### Decision Checklist
1. **Can elements be reused?** → `start = i` (Combination Sum) vs `start = i+1` (Subsets)
2. **Order matters?** → Permutation (all positions) vs Combination (only forward)
3. **Need all states or just leaves?** → Subsets (all) vs Permutations (leaves only)
4. **Duplicates in input?** → Sort + skip `if (i > start && nums[i] == nums[i-1])`
