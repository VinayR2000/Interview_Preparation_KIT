# Regular Expressions

## Creating RegEx

```javascript
const regex1 = /pattern/flags;              // Literal
const regex2 = new RegExp('pattern', 'flags'); // Constructor
```

## Flags

| Flag | Meaning |
|------|---------|
| `g` | Global (find all matches) |
| `i` | Case-insensitive |
| `m` | Multiline (^ and $ match line boundaries) |
| `s` | dotAll (`.` matches newline) |
| `u` | Unicode support |

---

## Syntax

| Pattern | Matches |
|---------|---------|
| `.` | Any char (except newline) |
| `\d` | Digit [0-9] |
| `\w` | Word char [a-zA-Z0-9_] |
| `\s` | Whitespace |
| `\D`, `\W`, `\S` | Negation of above |
| `[abc]` | a, b, or c |
| `[^abc]` | NOT a, b, or c |
| `[a-z]` | Range |
| `^` | Start of string |
| `$` | End of string |
| `\b` | Word boundary |
| `(abc)` | Group/capture |
| `a|b` | a OR b |

## Quantifiers

| Quantifier | Meaning |
|-----------|---------|
| `*` | 0 or more |
| `+` | 1 or more |
| `?` | 0 or 1 |
| `{3}` | Exactly 3 |
| `{2,5}` | 2 to 5 |
| `{2,}` | 2 or more |

---

## Methods

```javascript
// test() - returns boolean
/\d+/.test('abc123');       // true

// match() - returns matches array
'hello world'.match(/\w+/g);  // ['hello', 'world']

// replace()
'hello'.replace(/l/g, 'L');  // 'heLLo'
'2024-01-15'.replace(/(\d{4})-(\d{2})-(\d{2})/, '$3/$2/$1');  // '15/01/2024'

// split()
'a,b,,c'.split(/,+/);  // ['a', 'b', 'c']

// exec() - detailed match info
const match = /(\d+)/.exec('abc123');
// match[0] = '123', match[1] = '123' (group), match.index = 3
```

---

## Common Patterns

```javascript
// Email (basic)
/^[\w.-]+@[\w.-]+\.\w{2,}$/

// Phone (US)
/^\(?(\d{3})\)?[-.\s]?(\d{3})[-.\s]?(\d{4})$/

// URL
/^https?:\/\/[\w.-]+(:\d+)?(\/[\w.-]*)*(\?[\w=&]*)?(#\w*)?$/

// Password (8+ chars, upper, lower, digit, special)
/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/

// Numbers only
/^\d+$/

// Remove extra whitespace
str.replace(/\s+/g, ' ').trim();
```

---

## Key Interview Questions

**Q: What does the `g` flag do?**
> Global search — finds all matches instead of stopping at the first. Without `g`, `.match()` returns detailed info about the first match. With `g`, it returns all matches as an array.

**Q: What's a lookahead?**
> `(?=...)` positive lookahead: matches if followed by pattern. `(?!...)` negative lookahead: matches if NOT followed. Used in password validation to assert multiple conditions without consuming characters.
