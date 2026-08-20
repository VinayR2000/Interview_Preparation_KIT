# TypeScript with React

## Props Typing

```tsx
// Interface for props
interface ButtonProps {
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary' | 'danger';  // Optional union
  disabled?: boolean;
  children?: React.ReactNode;
}

function Button({ label, onClick, variant = 'primary', disabled }: ButtonProps) {
  return (
    <button className={variant} onClick={onClick} disabled={disabled}>
      {label}
    </button>
  );
}
```

---

## State Typing

```tsx
// Inferred
const [count, setCount] = useState(0);          // number
const [name, setName] = useState('');           // string

// Explicit (when initial value doesn't reveal type)
const [user, setUser] = useState<User | null>(null);
const [items, setItems] = useState<Item[]>([]);
const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle');
```

---

## Event Typing

```tsx
// Common event types
const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => { };
const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => { };
const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => { };
const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => { };

// Inline
<input onChange={(e: React.ChangeEvent<HTMLInputElement>) => setValue(e.target.value)} />

// Often inferred when inline
<input onChange={(e) => setValue(e.target.value)} />  // TypeScript infers type
```

---

## Hook Typing

```tsx
// useRef
const inputRef = useRef<HTMLInputElement>(null);
const countRef = useRef<number>(0);

// useReducer
type Action = 
  | { type: 'INCREMENT' }
  | { type: 'SET'; payload: number };

interface State { count: number; }

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'INCREMENT': return { count: state.count + 1 };
    case 'SET': return { count: action.payload };
  }
}

const [state, dispatch] = useReducer(reducer, { count: 0 });
```

---

## API Response Types

```tsx
interface User {
  id: number;
  name: string;
  email: string;
  role: 'admin' | 'user';
}

interface ApiResponse<T> {
  data: T;
  message: string;
  success: boolean;
}

async function fetchUsers(): Promise<User[]> {
  const response = await api.get<ApiResponse<User[]>>('/users');
  return response.data.data;
}
```

---

## Generics

```tsx
// Generic component
interface ListProps<T> {
  items: T[];
  renderItem: (item: T) => React.ReactNode;
}

function List<T>({ items, renderItem }: ListProps<T>) {
  return <ul>{items.map(renderItem)}</ul>;
}

// Usage - T is inferred
<List items={users} renderItem={(user) => <li>{user.name}</li>} />
```

---

## Type vs Interface

| Interface | Type |
|-----------|------|
| Extendable (`extends`) | Unions, intersections, mapped types |
| Declaration merging | No merging |
| Better for objects/props | Better for complex types |
| Preferred for public API | Preferred for internal |

```tsx
// Interface - for component props, objects
interface UserProps {
  name: string;
  age: number;
}

// Type - for unions, intersections, primitives
type Status = 'idle' | 'loading' | 'success' | 'error';
type ID = string | number;
type UserWithPosts = User & { posts: Post[] };
```

---

## Common Patterns

```tsx
// Children prop
interface LayoutProps {
  children: React.ReactNode;  // Anything renderable
}

// Component as prop
interface Props {
  icon: React.ComponentType<{ size?: number }>;
}

// Style prop
interface Props {
  style?: React.CSSProperties;
  className?: string;
}

// Extending HTML element props
interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}
```

---

## Key Interview Questions

**Q: Type vs Interface - which to use for props?**
> Both work. Convention: Use `interface` for component props (extendable, better error messages). Use `type` for unions, complex types, and utility types.

**Q: How do you type the children prop?**
> `React.ReactNode` for anything renderable (elements, strings, numbers, null). `React.ReactElement` for only JSX elements. `() => JSX.Element` for render props.

**Q: How do you handle nullable state?**
> `useState<User | null>(null)` then narrow with conditional checks: `if (user) { user.name }` or optional chaining `user?.name`.
