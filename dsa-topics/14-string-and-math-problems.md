# 14. Strings & Math — Must-Solve Problems ⭐⭐⭐

---

## String Problems

---

### Problem 1: Longest Palindromic Substring (NeetCode #5) ⭐⭐⭐

### Problem
Find the longest palindromic substring in `s`.

### Approach: Expand from center (every char and every pair of chars)

### Solution

```java
public String longestPalindrome(String s) {
    int start = 0, maxLen = 0;
    
    for (int i = 0; i < s.length(); i++) {
        // Odd length palindrome (center = single char)
        int len1 = expand(s, i, i);
        // Even length palindrome (center = between two chars)
        int len2 = expand(s, i, i + 1);
        
        int len = Math.max(len1, len2);
        if (len > maxLen) {
            maxLen = len;
            start = i - (len - 1) / 2;
        }
    }
    return s.substring(start, start + maxLen);
}

private int expand(String s, int left, int right) {
    while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
        left--;
        right++;
    }
    return right - left - 1; // length of palindrome
}
```

### Dry Run

```
Input: s = "babad"

i=0('b'): expand(0,0)→"b"(1), expand(0,1)→""(0). maxLen=1
i=1('a'): expand(1,1)→"bab"(3), expand(1,2)→""(0). maxLen=3, start=0
i=2('b'): expand(2,2)→"aba"(3), expand(2,3)→""(0). maxLen=3
i=3('a'): expand(3,3)→"a"(1), expand(3,4)→""(0). maxLen=3
i=4('d'): expand(4,4)→"d"(1). maxLen=3

Output: "bab" (start=0, len=3)
```

### Complexity
- Time: O(n²) — n centers, each expansion up to O(n)
- Space: O(1)

---

### Problem 2: Palindromic Substrings — Count All (NeetCode #647) ⭐⭐

### Problem
Count the number of palindromic substrings in string `s`.

### Solution

```java
public int countSubstrings(String s) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
        count += countFromCenter(s, i, i);     // odd
        count += countFromCenter(s, i, i + 1); // even
    }
    return count;
}

private int countFromCenter(String s, int left, int right) {
    int count = 0;
    while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
        count++;
        left--;
        right++;
    }
    return count;
}
```

### Dry Run

```
Input: s = "aaa"

i=0: odd expand(0,0)→"a","aaa"? No just "a"=1. even expand(0,1)→"aa"=1. count=2
     Actually: expand(0,0): a(1 palindrome). expand(0,1): "aa"(1). count=2
i=1: expand(1,1): "a","aaa" → 2 palindromes. expand(1,2): "aa" → 1. count=2+3=5
i=2: expand(2,2): "a" → 1. expand(2,3): out of bounds → 0. count=5+1=6

Output: 6 (palindromes: "a","a","a","aa","aa","aaa")
```

### Complexity
- Time: O(n²), Space: O(1)

---

### Problem 3: Valid Parentheses String (NeetCode #678) ⭐⭐

### Problem
Given a string with `(`, `)`, and `*` (which can be `(`, `)`, or empty), determine if it's valid.

### Approach: Track range of possible open count [low, high]

### Solution

```java
public boolean checkValidString(String s) {
    int low = 0, high = 0; // range of possible open parentheses count
    
    for (char c : s.toCharArray()) {
        if (c == '(') {
            low++;
            high++;
        } else if (c == ')') {
            low--;
            high--;
        } else { // '*'
            low--;   // treat as ')'
            high++;  // treat as '('
        }
        
        if (high < 0) return false; // too many ')'
        low = Math.max(low, 0);    // low can't be negative (choose * as empty)
    }
    return low == 0;
}
```

### Dry Run

```
Input: s = "(*))"

'(': low=1, high=1
'*': low=0, high=2
')': low=-1→0, high=1
')': low=-1→0, high=0

high≥0 throughout, low==0 at end → true

Output: true (interpret * as '(' → "(())")
```

### Complexity
- Time: O(n), Space: O(1)

---

### Problem 4: Longest Substring with At Most K Distinct Characters (LeetCode #340) ⭐⭐⭐

### Problem
Find the length of the longest substring with at most k distinct characters.

### Solution

```java
public int lengthOfLongestSubstringKDistinct(String s, int k) {
    Map<Character, Integer> freq = new HashMap<>();
    int left = 0, maxLen = 0;
    
    for (int right = 0; right < s.length(); right++) {
        freq.merge(s.charAt(right), 1, Integer::sum);
        
        while (freq.size() > k) {
            char leftChar = s.charAt(left);
            freq.merge(leftChar, -1, Integer::sum);
            if (freq.get(leftChar) == 0) freq.remove(leftChar);
            left++;
        }
        maxLen = Math.max(maxLen, right - left + 1);
    }
    return maxLen;
}
```

### Complexity
- Time: O(n), Space: O(k)

---

### Problem 5: String to Integer (atoi) (LeetCode #8) ⭐⭐

### Problem
Implement `atoi` — parse an integer from a string, handling whitespace, signs, overflow.

### Solution

```java
public int myAtoi(String s) {
    int i = 0, n = s.length();
    
    // Skip whitespace
    while (i < n && s.charAt(i) == ' ') i++;
    if (i == n) return 0;
    
    // Handle sign
    int sign = 1;
    if (s.charAt(i) == '+' || s.charAt(i) == '-') {
        sign = (s.charAt(i) == '-') ? -1 : 1;
        i++;
    }
    
    // Parse digits
    long result = 0;
    while (i < n && Character.isDigit(s.charAt(i))) {
        result = result * 10 + (s.charAt(i) - '0');
        if (result * sign > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (result * sign < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        i++;
    }
    return (int)(result * sign);
}
```

### Complexity
- Time: O(n), Space: O(1)

---

### Problem 6: Decode String (LeetCode #394) ⭐⭐⭐

### Problem
Given encoded string like `"3[a2[c]]"`, decode it to `"accaccacc"`.

### Solution (Stack)

```java
public String decodeString(String s) {
    Deque<StringBuilder> strStack = new ArrayDeque<>();
    Deque<Integer> numStack = new ArrayDeque<>();
    StringBuilder current = new StringBuilder();
    int num = 0;
    
    for (char c : s.toCharArray()) {
        if (Character.isDigit(c)) {
            num = num * 10 + (c - '0');
        } else if (c == '[') {
            strStack.push(current);
            numStack.push(num);
            current = new StringBuilder();
            num = 0;
        } else if (c == ']') {
            int repeat = numStack.pop();
            StringBuilder prev = strStack.pop();
            for (int i = 0; i < repeat; i++) {
                prev.append(current);
            }
            current = prev;
        } else {
            current.append(c);
        }
    }
    return current.toString();
}
```

### Dry Run

```
Input: "3[a2[c]]"

'3': num=3
'[': push("",3). current="", num=0
'a': current="a"
'2': num=2
'[': push("a",2). current="", num=0
'c': current="c"
']': repeat=2, prev="a". prev="a"+"c"+"c"="acc". current="acc"
']': repeat=3, prev="". prev=""+"acc"+"acc"+"acc"="accaccacc". current="accaccacc"

Output: "accaccacc"
```

### Complexity
- Time: O(output length), Space: O(n)

---

## Math Problems (Frequently Asked)

---

### Problem 7: Pow(x, n) — Fast Exponentiation (NeetCode #50) ⭐⭐

### Problem
Implement `pow(x, n)` — x raised to power n.

### Approach: Binary exponentiation (square repeatedly)

### Solution

```java
public double myPow(double x, int n) {
    long N = n; // handle Integer.MIN_VALUE
    if (N < 0) {
        x = 1 / x;
        N = -N;
    }
    
    double result = 1.0;
    while (N > 0) {
        if ((N & 1) == 1) result *= x; // odd power
        x *= x;      // square the base
        N >>= 1;     // divide power by 2
    }
    return result;
}
```

### Dry Run

```
Input: x=2.0, n=10

N=10 (binary: 1010)
N=10: even, x=2*2=4, N=5
N=5: odd, result=1*4=4, x=4*4=16, N=2
N=2: even, x=16*16=256, N=1
N=1: odd, result=4*256=1024, N=0

Output: 1024.0 (2^10 = 1024)
```

### Complexity
- Time: O(log n), Space: O(1)

---

### Problem 8: Happy Number (NeetCode #202) ⭐

### Problem
A number is "happy" if repeatedly summing squares of digits eventually reaches 1.

### Solution (Floyd's Cycle Detection)

```java
public boolean isHappy(int n) {
    int slow = n, fast = n;
    do {
        slow = digitSquareSum(slow);
        fast = digitSquareSum(digitSquareSum(fast));
    } while (slow != fast);
    
    return slow == 1;
}

private int digitSquareSum(int n) {
    int sum = 0;
    while (n > 0) {
        int digit = n % 10;
        sum += digit * digit;
        n /= 10;
    }
    return sum;
}
```

### Complexity
- Time: O(log n), Space: O(1)

---

### Problem 9: Spiral Matrix (NeetCode #54) ⭐⭐

### Problem
Return all elements of a matrix in spiral order.

### Solution

```java
public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> result = new ArrayList<>();
    int top = 0, bottom = matrix.length - 1;
    int left = 0, right = matrix[0].length - 1;
    
    while (top <= bottom && left <= right) {
        // Traverse right
        for (int j = left; j <= right; j++) result.add(matrix[top][j]);
        top++;
        
        // Traverse down
        for (int i = top; i <= bottom; i++) result.add(matrix[i][right]);
        right--;
        
        // Traverse left
        if (top <= bottom) {
            for (int j = right; j >= left; j--) result.add(matrix[bottom][j]);
            bottom--;
        }
        
        // Traverse up
        if (left <= right) {
            for (int i = bottom; i >= top; i--) result.add(matrix[i][left]);
            left++;
        }
    }
    return result;
}
```

### Dry Run

```
Input: [[1,2,3],
        [4,5,6],
        [7,8,9]]

Round 1: right→[1,2,3], down→[6,9], left→[8,7], up→[4]
  top=1, right=1, bottom=1, left=1
Round 2: right→[5]
  top=2 > bottom=1 → stop

Output: [1,2,3,6,9,8,7,4,5]
```

### Complexity
- Time: O(m × n), Space: O(1) excluding output

---

### Problem 10: Multiply Strings (NeetCode #43) ⭐⭐

### Problem
Multiply two numbers represented as strings (can't use BigInteger).

### Solution

```java
public String multiply(String num1, String num2) {
    int m = num1.length(), n = num2.length();
    int[] result = new int[m + n];
    
    for (int i = m - 1; i >= 0; i--) {
        for (int j = n - 1; j >= 0; j--) {
            int mul = (num1.charAt(i) - '0') * (num2.charAt(j) - '0');
            int p1 = i + j, p2 = i + j + 1; // positions in result
            int sum = mul + result[p2];
            
            result[p2] = sum % 10;
            result[p1] += sum / 10;
        }
    }
    
    StringBuilder sb = new StringBuilder();
    for (int digit : result) {
        if (sb.length() > 0 || digit != 0) sb.append(digit);
    }
    return sb.length() == 0 ? "0" : sb.toString();
}
```

### Dry Run

```
Input: num1="123", num2="45"

result = [0,0,0,0,0]

i=2(3), j=1(5): mul=15, p1=3,p2=4. sum=15+0=15. result[4]=5, result[3]+=1 → [0,0,0,1,5]
i=2(3), j=0(4): mul=12, p1=2,p2=3. sum=12+1=13. result[3]=3, result[2]+=1 → [0,0,1,3,5]
i=1(2), j=1(5): mul=10, p1=2,p2=3. sum=10+3=13. result[3]=3, result[2]+=1 → [0,0,2,3,5]
i=1(2), j=0(4): mul=8, p1=1,p2=2. sum=8+2=10. result[2]=0, result[1]+=1 → [0,1,0,3,5]
i=0(1), j=1(5): mul=5, p1=1,p2=2. sum=5+0=5. result[2]=5 → [0,1,5,3,5]
i=0(1), j=0(4): mul=4, p1=0,p2=1. sum=4+1=5. result[1]=5, result[0]+=0 → [0,5,5,3,5]

Output: "5535" (123 × 45 = 5535) ✓
```

### Complexity
- Time: O(m × n), Space: O(m + n)

---

## Summary

| Problem | Pattern | Company Frequency |
|---------|---------|-------------------|
| Longest Palindromic Substring | Expand from center | Amazon, Microsoft, Google |
| Palindromic Substrings Count | Expand from center | Meta, Amazon |
| Valid Parentheses String | Low/High range | Amazon, Google |
| Decode String | Stack-based parsing | Google, Amazon, Meta |
| String to Integer | Edge case handling | Microsoft, Amazon |
| Pow(x,n) | Binary exponentiation | Meta, Google |
| Spiral Matrix | Boundary pointers | Amazon, Microsoft, Apple |
| Multiply Strings | Grade-school multiplication | Google, Meta |
