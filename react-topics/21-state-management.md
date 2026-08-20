# State Management

## State Types

| Type | Scope | Examples | Solution |
|------|-------|----------|----------|
| Local | Single component | Form inputs, toggles | useState |
| Shared | Few components | Selected item, filters | Lift state up |
| Global | Entire app | Auth, theme, cart | Context / Redux |
| Server | Remote data | API responses | React Query / SWR |
| URL | Browser URL | Pagination, filters | React Router |

---

## Context API + useReducer

```jsx
const CartContext = createContext();

function cartReducer(state, action) {
  switch (action.type) {
    case 'ADD_ITEM':
      return { ...state, items: [...state.items, action.payload] };
    case 'REMOVE_ITEM':
      return { ...state, items: state.items.filter(i => i.id !== action.payload) };
    case 'CLEAR':
      return { ...state, items: [] };
    default:
      return state;
  }
}

function CartProvider({ children }) {
  const [state, dispatch] = useReducer(cartReducer, { items: [] });
  return (
    <CartContext.Provider value={{ state, dispatch }}>
      {children}
    </CartContext.Provider>
  );
}
```

---

## Redux Toolkit (RTK)

### Store Setup
```jsx
import { configureStore } from '@reduxjs/toolkit';

const store = configureStore({
  reducer: {
    todos: todosReducer,
    user: userReducer,
  },
});
```

### Slice (Reducer + Actions)
```jsx
import { createSlice } from '@reduxjs/toolkit';

const todosSlice = createSlice({
  name: 'todos',
  initialState: { items: [], loading: false },
  reducers: {
    addTodo: (state, action) => {
      state.items.push(action.payload);  // Immer allows "mutations"
    },
    toggleTodo: (state, action) => {
      const todo = state.items.find(t => t.id === action.payload);
      if (todo) todo.completed = !todo.completed;
    },
    removeTodo: (state, action) => {
      state.items = state.items.filter(t => t.id !== action.payload);
    },
  },
});

export const { addTodo, toggleTodo, removeTodo } = todosSlice.actions;
export default todosSlice.reducer;
```

### Async Actions (createAsyncThunk)
```jsx
import { createAsyncThunk } from '@reduxjs/toolkit';

export const fetchTodos = createAsyncThunk('todos/fetch', async () => {
  const response = await api.get('/todos');
  return response.data;
});

// Handle in slice
extraReducers: (builder) => {
  builder
    .addCase(fetchTodos.pending, (state) => { state.loading = true; })
    .addCase(fetchTodos.fulfilled, (state, action) => {
      state.loading = false;
      state.items = action.payload;
    })
    .addCase(fetchTodos.rejected, (state, action) => {
      state.loading = false;
      state.error = action.error.message;
    });
}
```

### Using in Components
```jsx
import { useSelector, useDispatch } from 'react-redux';

function TodoList() {
  const { items, loading } = useSelector(state => state.todos);
  const dispatch = useDispatch();

  useEffect(() => { dispatch(fetchTodos()); }, [dispatch]);

  return (
    <ul>
      {items.map(todo => (
        <li key={todo.id} onClick={() => dispatch(toggleTodo(todo.id))}>
          {todo.text}
        </li>
      ))}
    </ul>
  );
}
```

---

## RTK Query (Data Fetching)

```jsx
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

const api = createApi({
  baseQuery: fetchBaseQuery({ baseUrl: '/api' }),
  tagTypes: ['User'],
  endpoints: (builder) => ({
    getUsers: builder.query({ query: () => '/users', providesTags: ['User'] }),
    addUser: builder.mutation({
      query: (user) => ({ url: '/users', method: 'POST', body: user }),
      invalidatesTags: ['User'],  // Auto-refetch users list
    }),
  }),
});

export const { useGetUsersQuery, useAddUserMutation } = api;

// Usage
function Users() {
  const { data: users, isLoading, error } = useGetUsersQuery();
  const [addUser] = useAddUserMutation();

  if (isLoading) return <Spinner />;
  return <UserList users={users} onAdd={addUser} />;
}
```

---

## Redux vs Context

| Redux | Context |
|-------|---------|
| Complex global state | Simple global state |
| Middleware (async, logging) | No middleware |
| DevTools, time-travel | No devtools |
| Selective re-rendering (selectors) | All consumers re-render |
| Boilerplate (reduced with RTK) | Minimal setup |
| Best for: Large apps, complex state | Best for: Theme, auth, locale |

---

## Key Interview Questions

**Q: When do you need Redux vs Context?**
> Context: Low-frequency updates (theme, auth, i18n). Redux: Complex state logic, frequent updates, middleware needs, multiple data flows, need for devtools/debugging.

**Q: What's the difference between Redux and Redux Toolkit?**
> RTK is the official recommended way to write Redux. It eliminates boilerplate: createSlice (combines actions + reducer), configureStore (auto middleware), Immer (mutable syntax), createAsyncThunk (async).

**Q: What is the flux pattern?**
> Unidirectional data flow: Action → Dispatcher → Store → View → Action. Redux follows this: dispatch(action) → reducer → new state → component re-renders.

**Q: How does useSelector prevent unnecessary re-renders?**
> useSelector subscribes to the store and re-renders only when the selected slice changes (using strict equality by default). This is more efficient than Context, which re-renders all consumers.
