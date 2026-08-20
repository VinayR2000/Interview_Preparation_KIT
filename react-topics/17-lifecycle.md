# Component Lifecycle

## Lifecycle Phases

```
┌─────────────────────────────────────────────┐
│                  MOUNTING                     │
│  constructor → render → DOM update →         │
│  componentDidMount / useEffect([], ...)      │
├─────────────────────────────────────────────┤
│                  UPDATING                     │
│  New props/state → render → DOM update →     │
│  componentDidUpdate / useEffect([deps], ...) │
├─────────────────────────────────────────────┤
│                 UNMOUNTING                    │
│  componentWillUnmount / useEffect cleanup    │
└─────────────────────────────────────────────┘
```

---

## Class Component Lifecycle Methods

### Mounting
| Method | When | Use For |
|--------|------|---------|
| `constructor(props)` | Component created | Initialize state, bind methods |
| `render()` | Every render | Return JSX |
| `componentDidMount()` | After first DOM insert | API calls, subscriptions, DOM access |

### Updating
| Method | When | Use For |
|--------|------|---------|
| `shouldComponentUpdate(nextProps, nextState)` | Before re-render | Performance optimization |
| `render()` | On re-render | Return updated JSX |
| `componentDidUpdate(prevProps, prevState)` | After DOM update | React to prop/state changes |

### Unmounting
| Method | When | Use For |
|--------|------|---------|
| `componentWillUnmount()` | Before removal | Cleanup (timers, subscriptions) |

---

## useEffect Equivalents (Functional Components)

### componentDidMount → useEffect(fn, [])
```jsx
useEffect(() => {
  // Runs once after mount
  fetchData();
  const subscription = subscribe();
  
  return () => {
    // componentWillUnmount equivalent
    subscription.unsubscribe();
  };
}, []);  // Empty deps = mount only
```

### componentDidUpdate → useEffect(fn, [deps])
```jsx
useEffect(() => {
  // Runs when userId changes (like componentDidUpdate for userId)
  fetchUser(userId);
}, [userId]);
```

### componentWillUnmount → cleanup function
```jsx
useEffect(() => {
  const timer = setInterval(tick, 1000);
  return () => clearInterval(timer);  // Cleanup on unmount
}, []);
```

---

## Class vs Functional Lifecycle Mapping

| Class Lifecycle | Functional Equivalent |
|----------------|----------------------|
| `constructor` | `useState(initialValue)` |
| `componentDidMount` | `useEffect(() => {}, [])` |
| `componentDidUpdate` | `useEffect(() => {}, [deps])` |
| `componentWillUnmount` | `useEffect(() => { return cleanup }, [])` |
| `shouldComponentUpdate` | `React.memo` |
| `getDerivedStateFromProps` | Update state during render |
| `getSnapshotBeforeUpdate` | `useLayoutEffect` (partial) |

---

## Key Interview Questions

**Q: In what order do lifecycle methods/hooks run?**
> Mount: Parent render → Child render → Child useEffect → Parent useEffect. Unmount: Parent cleanup → Child cleanup (reverse order).

**Q: Can you have multiple useEffects?**
> Yes. Each useEffect handles one concern (separation of concerns). They run in order of declaration after each render.

**Q: When does cleanup run?**
> Before the effect re-runs (on dependency change) AND on unmount. This ensures old subscriptions/timers are cleaned before new ones start.
