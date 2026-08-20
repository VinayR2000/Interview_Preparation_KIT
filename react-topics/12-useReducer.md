# useReducer

## Concept

- Alternative to useState for complex state logic
- Inspired by Redux pattern: `(state, action) => newState`
- Centralizes state update logic in a reducer function

```jsx
const [state, dispatch] = useReducer(reducer, initialState);
```

---

## Components

### Reducer Function
```jsx
function reducer(state, action) {
  switch (action.type) {
    case 'INCREMENT':
      return { ...state, count: state.count + 1 };
    case 'DECREMENT':
      return { ...state, count: state.count - 1 };
    case 'SET_COUNT':
      return { ...state, count: action.payload };
    default:
      throw new Error(`Unknown action: ${action.type}`);
  }
}
```

### Action
```jsx
// Action = object with type (and optional payload)
dispatch({ type: 'INCREMENT' });
dispatch({ type: 'SET_COUNT', payload: 10 });
dispatch({ type: 'ADD_TODO', payload: { id: 1, text: 'Learn React' } });
```

### Dispatch
```jsx
// dispatch sends actions to the reducer
<button onClick={() => dispatch({ type: 'INCREMENT' })}>+</button>
```

---

## Complete Example: Todo App

```jsx
const initialState = {
  todos: [],
  filter: 'all',
};

function todoReducer(state, action) {
  switch (action.type) {
    case 'ADD_TODO':
      return {
        ...state,
        todos: [...state.todos, { id: Date.now(), text: action.payload, done: false }],
      };
    case 'TOGGLE_TODO':
      return {
        ...state,
        todos: state.todos.map(todo =>
          todo.id === action.payload ? { ...todo, done: !todo.done } : todo
        ),
      };
    case 'DELETE_TODO':
      return {
        ...state,
        todos: state.todos.filter(todo => todo.id !== action.payload),
      };
    case 'SET_FILTER':
      return { ...state, filter: action.payload };
    default:
      throw new Error(`Unknown action: ${action.type}`);
  }
}

function TodoApp() {
  const [state, dispatch] = useReducer(todoReducer, initialState);
  const [input, setInput] = useState('');

  const handleAdd = () => {
    if (input.trim()) {
      dispatch({ type: 'ADD_TODO', payload: input });
      setInput('');
    }
  };

  const filteredTodos = state.todos.filter(todo => {
    if (state.filter === 'active') return !todo.done;
    if (state.filter === 'completed') return todo.done;
    return true;
  });

  return (
    <div>
      <input value={input} onChange={e => setInput(e.target.value)} />
      <button onClick={handleAdd}>Add</button>
      <ul>
        {filteredTodos.map(todo => (
          <li key={todo.id}>
            <span 
              style={{ textDecoration: todo.done ? 'line-through' : 'none' }}
              onClick={() => dispatch({ type: 'TOGGLE_TODO', payload: todo.id })}
            >
              {todo.text}
            </span>
            <button onClick={() => dispatch({ type: 'DELETE_TODO', payload: todo.id })}>
              ×
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

---

## useReducer vs useState

| useState | useReducer |
|----------|-----------|
| Simple state (primitives, simple objects) | Complex state (nested objects, multiple sub-values) |
| Independent state updates | Related state transitions |
| Few update patterns | Many update patterns (CRUD operations) |
| Logic scattered in handlers | Logic centralized in reducer |
| Easy to start | Better for scaling |

### When to Choose useReducer
- State has multiple sub-values that change together
- Next state depends on previous state in complex ways
- Multiple components dispatch the same actions
- You want to test state logic separately
- State transitions have clear action names

---

## Combining useReducer + Context

```jsx
// Global state without Redux
const TodoContext = createContext();

function TodoProvider({ children }) {
  const [state, dispatch] = useReducer(todoReducer, initialState);
  
  return (
    <TodoContext.Provider value={{ state, dispatch }}>
      {children}
    </TodoContext.Provider>
  );
}

// Custom hook for consuming
function useTodos() {
  const context = useContext(TodoContext);
  if (!context) throw new Error('useTodos must be inside TodoProvider');
  return context;
}

// Any nested component
function TodoItem({ todo }) {
  const { dispatch } = useTodos();
  return (
    <li onClick={() => dispatch({ type: 'TOGGLE_TODO', payload: todo.id })}>
      {todo.text}
    </li>
  );
}
```

---

## Key Interview Questions

**Q: When would you use useReducer over useState?**
> When state logic is complex, involves multiple sub-values, or when the next state depends on the previous state in non-trivial ways. Also when you want to centralize and test state logic independently.

**Q: Is useReducer the same as Redux?**
> Similar pattern (reducer + dispatch + actions) but simpler. useReducer is local to a component (unless combined with Context). Redux has middleware, devtools, selectors, and is designed for global app state.

**Q: Can you use async logic in a reducer?**
> No. Reducers must be pure functions (no side effects). Handle async in the component (useEffect), then dispatch with the result. For complex async, consider middleware patterns or libraries.
