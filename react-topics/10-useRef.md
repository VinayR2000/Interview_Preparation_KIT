# useRef

## What is useRef?

- Returns a mutable ref object: `{ current: initialValue }`
- Persists across renders (like state)
- Changing `.current` does NOT cause re-render (unlike state)
- Two main uses: DOM references and mutable values

```jsx
const myRef = useRef(initialValue);
// myRef.current = initialValue
```

---

## DOM References

```jsx
function TextInput() {
  const inputRef = useRef(null);

  const handleFocus = () => {
    inputRef.current.focus();  // Direct DOM access
  };

  const handleScroll = () => {
    inputRef.current.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <div>
      <input ref={inputRef} type="text" />
      <button onClick={handleFocus}>Focus Input</button>
    </div>
  );
}
```

### Common DOM Operations
```jsx
const elementRef = useRef(null);

// Get dimensions
const { width, height } = elementRef.current.getBoundingClientRect();

// Scroll
elementRef.current.scrollIntoView();

// Focus
elementRef.current.focus();

// Get/set value (uncontrolled)
const value = elementRef.current.value;
```

---

## Mutable Values (No Re-render)

### Store Previous Value
```jsx
function Counter() {
  const [count, setCount] = useState(0);
  const prevCountRef = useRef(0);

  useEffect(() => {
    prevCountRef.current = count;  // Update after render
  });

  return (
    <p>Current: {count}, Previous: {prevCountRef.current}</p>
  );
}

// Custom hook
function usePrevious(value) {
  const ref = useRef();
  useEffect(() => {
    ref.current = value;
  });
  return ref.current;
}
```

### Timer References
```jsx
function StopWatch() {
  const [time, setTime] = useState(0);
  const intervalRef = useRef(null);

  const start = () => {
    intervalRef.current = setInterval(() => {
      setTime(prev => prev + 1);
    }, 1000);
  };

  const stop = () => {
    clearInterval(intervalRef.current);  // Access the interval ID
  };

  useEffect(() => {
    return () => clearInterval(intervalRef.current);  // Cleanup
  }, []);

  return (
    <div>
      <p>Time: {time}s</p>
      <button onClick={start}>Start</button>
      <button onClick={stop}>Stop</button>
    </div>
  );
}
```

### Track Render Count
```jsx
function App() {
  const renderCount = useRef(0);
  renderCount.current++;  // Doesn't trigger re-render!
  
  console.log(`Rendered ${renderCount.current} times`);
  return <div>...</div>;
}
```

### Store Latest Value Without Re-render
```jsx
function ChatRoom({ roomId }) {
  const [message, setMessage] = useState('');
  const latestMessage = useRef(message);
  
  // Keep ref in sync without causing re-render
  useEffect(() => {
    latestMessage.current = message;
  });
  
  // Use in async callback without stale closure
  const sendMessage = useCallback(() => {
    socket.send(latestMessage.current);  // Always latest!
  }, []);
}
```

---

## useRef vs useState

| useRef | useState |
|--------|----------|
| `{ current: value }` | `[value, setter]` |
| Mutable (direct assignment) | Immutable (use setter) |
| Does NOT trigger re-render | Triggers re-render |
| Persists across renders | Persists across renders |
| For DOM access, mutable values | For UI data that affects render |
| Synchronous update | Asynchronous (batched) |

### When to Use Which
- **useState**: Value affects what's displayed (UI state)
- **useRef**: Value needed between renders but doesn't affect display

---

## Forwarding Refs

```jsx
// forwardRef lets parent access child's DOM element
const FancyButton = forwardRef(function FancyButton(props, ref) {
  return (
    <button ref={ref} className="fancy-button">
      {props.children}
    </button>
  );
});

// Parent can now ref the button
function App() {
  const buttonRef = useRef(null);
  
  const handleClick = () => {
    buttonRef.current.focus();  // Access child's button DOM
  };

  return <FancyButton ref={buttonRef}>Click me</FancyButton>;
}
```

---

## Imperative Access with useImperativeHandle

```jsx
const VideoPlayer = forwardRef(function VideoPlayer(props, ref) {
  const videoRef = useRef(null);

  // Expose only specific methods to parent
  useImperativeHandle(ref, () => ({
    play: () => videoRef.current.play(),
    pause: () => videoRef.current.pause(),
    getCurrentTime: () => videoRef.current.currentTime,
  }));

  return <video ref={videoRef} src={props.src} />;
});

// Parent
function App() {
  const playerRef = useRef(null);
  
  return (
    <div>
      <VideoPlayer ref={playerRef} src="/video.mp4" />
      <button onClick={() => playerRef.current.play()}>Play</button>
      <button onClick={() => playerRef.current.pause()}>Pause</button>
    </div>
  );
}
```

---

## Key Interview Questions

**Q: Why use useRef instead of a regular variable?**
> Regular variables reset on every render. useRef persists across renders. A `let` variable inside a component gets re-created each render; a ref maintains its value.

**Q: When should you use useRef over useState?**
> When you need to store a value that doesn't affect the render output. Examples: timer IDs, previous values, DOM elements, tracking whether component is mounted.

**Q: Can useRef cause memory leaks?**
> If you store references to DOM elements that get removed without clearing the ref, technically yes. But React automatically sets ref to null on unmount for elements rendered by React.

**Q: What's the difference between `createRef` and `useRef`?**
> `createRef` creates a new ref object every render (used in class components). `useRef` creates it once and returns the same object on subsequent renders (used in function components).
