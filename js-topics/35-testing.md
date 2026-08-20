# JavaScript Testing

## Unit Testing with Jest

```javascript
// sum.js
function sum(a, b) { return a + b; }
module.exports = sum;

// sum.test.js
const sum = require('./sum');

describe('sum', () => {
  test('adds two numbers', () => {
    expect(sum(1, 2)).toBe(3);
  });

  test('handles negative numbers', () => {
    expect(sum(-1, -1)).toBe(-2);
  });

  test('handles zero', () => {
    expect(sum(0, 5)).toBe(5);
  });
});
```

---

## Jest Matchers

```javascript
// Equality
expect(value).toBe(3);                // Strict equality (===)
expect(obj).toEqual({ a: 1 });        // Deep equality
expect(value).toBeNull();
expect(value).toBeUndefined();
expect(value).toBeTruthy();
expect(value).toBeFalsy();

// Numbers
expect(value).toBeGreaterThan(3);
expect(value).toBeLessThanOrEqual(5);
expect(0.1 + 0.2).toBeCloseTo(0.3);

// Strings
expect(str).toMatch(/regex/);
expect(str).toContain('substring');

// Arrays/Objects
expect(arr).toContain(item);
expect(obj).toHaveProperty('key', 'value');
expect(arr).toHaveLength(3);

// Errors
expect(() => fn()).toThrow();
expect(() => fn()).toThrow('error message');
expect(() => fn()).toThrow(TypeError);
```

---

## Mocking

```javascript
// Mock function
const mockFn = jest.fn();
mockFn.mockReturnValue(42);
mockFn.mockResolvedValue({ data: 'test' });

mockFn(1, 2);
expect(mockFn).toHaveBeenCalled();
expect(mockFn).toHaveBeenCalledWith(1, 2);
expect(mockFn).toHaveBeenCalledTimes(1);

// Mock module
jest.mock('./api', () => ({
  fetchUsers: jest.fn().mockResolvedValue([{ id: 1, name: 'John' }]),
}));

// Spy on existing method
const spy = jest.spyOn(Math, 'random').mockReturnValue(0.5);
expect(Math.random()).toBe(0.5);
spy.mockRestore();
```

---

## Async Testing

```javascript
// async/await
test('fetches user', async () => {
  const user = await fetchUser(1);
  expect(user.name).toBe('John');
});

// Resolves/Rejects
test('resolves with data', () => {
  return expect(fetchData()).resolves.toEqual({ id: 1 });
});

test('rejects with error', () => {
  return expect(fetchBad()).rejects.toThrow('Not found');
});

// Timers
jest.useFakeTimers();
test('debounce calls after delay', () => {
  const fn = jest.fn();
  const debounced = debounce(fn, 300);
  debounced();
  expect(fn).not.toHaveBeenCalled();
  jest.advanceTimersByTime(300);
  expect(fn).toHaveBeenCalledTimes(1);
});
```

---

## Test Structure

```javascript
describe('UserService', () => {
  let service;

  beforeAll(() => {
    // Run once before all tests
  });

  beforeEach(() => {
    // Run before each test
    service = new UserService();
  });

  afterEach(() => {
    // Cleanup after each test
    jest.clearAllMocks();
  });

  it('should create a user', async () => {
    const user = await service.create({ name: 'John' });
    expect(user.id).toBeDefined();
    expect(user.name).toBe('John');
  });

  it('should throw on invalid data', () => {
    expect(() => service.create({})).toThrow('Name required');
  });
});
```

---

## Code Coverage

```bash
jest --coverage
```

| Metric | Measures |
|--------|----------|
| Statements | % of statements executed |
| Branches | % of if/else paths taken |
| Functions | % of functions called |
| Lines | % of lines executed |

---

## Key Interview Questions

**Q: What's the difference between `toBe` and `toEqual`?**
> `toBe` uses `===` (reference equality for objects). `toEqual` does deep equality (compares properties recursively). Use `toBe` for primitives, `toEqual` for objects and arrays.

**Q: What is mocking and why use it?**
> Replacing real implementations with controlled fakes. Reasons: Isolate unit under test, avoid network calls, control return values, verify function was called with correct args.

**Q: What's the difference between a mock, stub, and spy?**
> Stub: Provides canned responses. Mock: Verifies correct calls were made. Spy: Wraps real implementation, records calls but still executes original code. Jest's `jest.fn()` combines all three.
