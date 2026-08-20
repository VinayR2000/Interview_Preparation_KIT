# Testing React Applications

## Testing Pyramid

```
        /  E2E Tests  \        (Cypress, Playwright) - Few, slow, expensive
       / Integration   \       (RTL + Jest) - Some
      /   Unit Tests    \      (Jest) - Many, fast, cheap
```

---

## Jest Basics

```jsx
// test or it
test('adds 1 + 2 to equal 3', () => {
  expect(1 + 2).toBe(3);
});

// describe for grouping
describe('Calculator', () => {
  it('should add numbers', () => { expect(add(1, 2)).toBe(3); });
  it('should subtract numbers', () => { expect(sub(5, 3)).toBe(2); });
});

// Common matchers
expect(value).toBe(3);               // Strict equality
expect(value).toEqual({ a: 1 });     // Deep equality
expect(value).toBeTruthy();
expect(value).toBeNull();
expect(array).toContain('item');
expect(fn).toThrow();
expect(fn).toHaveBeenCalledWith(arg);
```

---

## React Testing Library (RTL)

### Philosophy
- Test behavior, not implementation
- Query elements like a user would (by role, text, label)
- Avoid testing internal state or lifecycle methods

### Rendering
```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

test('renders greeting', () => {
  render(<Greeting name="John" />);
  expect(screen.getByText('Hello, John!')).toBeInTheDocument();
});
```

### Queries (Priority Order)
```jsx
// 1. Accessible (preferred)
screen.getByRole('button', { name: 'Submit' });
screen.getByLabelText('Email');
screen.getByPlaceholderText('Search...');
screen.getByText('Welcome');

// 2. Semantic
screen.getByAltText('User avatar');
screen.getByTitle('Close');

// 3. Test ID (last resort)
screen.getByTestId('custom-element');
```

### Query Variants
| Variant | 0 matches | 1 match | 1+ matches | Async? |
|---------|-----------|---------|------------|--------|
| getBy | throws | returns | throws | No |
| queryBy | null | returns | throws | No |
| findBy | throws | returns | throws | Yes |
| getAllBy | throws | array | array | No |
| queryAllBy | [] | array | array | No |
| findAllBy | throws | array | array | Yes |

---

## User Events

```jsx
import userEvent from '@testing-library/user-event';

test('form submission', async () => {
  const handleSubmit = jest.fn();
  render(<LoginForm onSubmit={handleSubmit} />);

  const user = userEvent.setup();
  
  await user.type(screen.getByLabelText('Email'), 'john@test.com');
  await user.type(screen.getByLabelText('Password'), 'password123');
  await user.click(screen.getByRole('button', { name: 'Login' }));

  expect(handleSubmit).toHaveBeenCalledWith({
    email: 'john@test.com',
    password: 'password123',
  });
});
```

---

## Mocking

### Mock Functions
```jsx
const mockFn = jest.fn();
mockFn.mockReturnValue(42);
mockFn.mockResolvedValue({ data: 'test' });  // Async

expect(mockFn).toHaveBeenCalled();
expect(mockFn).toHaveBeenCalledTimes(2);
expect(mockFn).toHaveBeenCalledWith('arg1', 'arg2');
```

### Mock API Calls
```jsx
// Mock module
jest.mock('./api', () => ({
  fetchUsers: jest.fn(),
}));

import { fetchUsers } from './api';

test('displays users after fetch', async () => {
  fetchUsers.mockResolvedValue([{ id: 1, name: 'John' }]);
  
  render(<UserList />);
  
  expect(screen.getByText('Loading...')).toBeInTheDocument();
  await waitFor(() => {
    expect(screen.getByText('John')).toBeInTheDocument();
  });
});
```

---

## Async Testing

```jsx
test('loads data on mount', async () => {
  render(<DataComponent />);
  
  // Wait for element to appear
  const element = await screen.findByText('Data loaded');
  expect(element).toBeInTheDocument();
  
  // Or use waitFor for assertions
  await waitFor(() => {
    expect(screen.getByText('Data loaded')).toBeInTheDocument();
  });
  
  // waitForElementToBeRemoved
  await waitForElementToBeRemoved(() => screen.queryByText('Loading...'));
});
```

---

## Component Testing Example

```jsx
describe('Counter', () => {
  it('renders initial count', () => {
    render(<Counter initialCount={5} />);
    expect(screen.getByText('Count: 5')).toBeInTheDocument();
  });

  it('increments on click', async () => {
    render(<Counter initialCount={0} />);
    const user = userEvent.setup();
    
    await user.click(screen.getByRole('button', { name: '+' }));
    expect(screen.getByText('Count: 1')).toBeInTheDocument();
  });

  it('disables decrement at zero', () => {
    render(<Counter initialCount={0} />);
    expect(screen.getByRole('button', { name: '-' })).toBeDisabled();
  });
});
```

---

## Key Interview Questions

**Q: What's the difference between getBy, queryBy, and findBy?**
> `getBy` throws if not found (use when element must exist). `queryBy` returns null if not found (use for asserting absence). `findBy` is async and waits for element (use after async operations).

**Q: Why does RTL discourage testing implementation details?**
> Implementation details (state values, method names) can change without affecting behavior. Testing behavior (what user sees and does) makes tests resilient to refactoring.

**Q: How do you test components with Context/Redux?**
> Wrap in provider during render: `render(<Provider store={store}><Component /></Provider>)`. Create a custom render utility that includes providers.
