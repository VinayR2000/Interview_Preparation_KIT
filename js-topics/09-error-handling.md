# Error Handling

## try/catch/finally

```javascript
try {
  const data = JSON.parse(invalidJson);
} catch (error) {
  console.error(error.message);  // Error message
  console.error(error.name);     // "SyntaxError"
  console.error(error.stack);    // Stack trace
} finally {
  // Always runs (even if return in try/catch)
  cleanup();
}
```

---

## Error Types

| Type | When |
|------|------|
| `Error` | Generic error |
| `TypeError` | Wrong type (calling non-function, accessing null property) |
| `ReferenceError` | Accessing undeclared variable |
| `SyntaxError` | Invalid syntax |
| `RangeError` | Value out of range (e.g., invalid array length) |
| `URIError` | Invalid URI encoding |

---

## Custom Errors

```javascript
class ValidationError extends Error {
  constructor(field, message) {
    super(message);
    this.name = 'ValidationError';
    this.field = field;
  }
}

class NotFoundError extends Error {
  constructor(resource) {
    super(`${resource} not found`);
    this.name = 'NotFoundError';
    this.statusCode = 404;
  }
}

// Throw and catch
try {
  throw new ValidationError('email', 'Invalid email format');
} catch (error) {
  if (error instanceof ValidationError) {
    console.log(`Field: ${error.field}, Message: ${error.message}`);
  } else {
    throw error;  // Re-throw unknown errors
  }
}
```

---

## Async Error Handling

```javascript
// Promise - .catch()
fetch('/api/data')
  .then(res => res.json())
  .catch(err => console.error(err));

// async/await - try/catch
async function fetchData() {
  try {
    const res = await fetch('/api/data');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (error) {
    console.error('Fetch failed:', error.message);
    throw error;  // Re-throw to let caller handle
  }
}

// Unhandled Promise rejections
window.addEventListener('unhandledrejection', (event) => {
  console.error('Unhandled rejection:', event.reason);
});
```

---

## Key Interview Questions

**Q: What's the difference between throw and return?**
> `throw` stops execution and propagates up the call stack until caught by a `try/catch`. `return` just exits the current function with a value. Throw for exceptional situations; return for normal flow.

**Q: Can finally override a return?**
> Yes. If `finally` has a return statement, it overrides the return value from `try` or `catch`. Avoid returning in `finally`.

**Q: How do you handle errors in async code?**
> For Promises: `.catch()` at the end of chain. For async/await: wrap in `try/catch`. For unhandled rejections: listen for `unhandledrejection` event.
