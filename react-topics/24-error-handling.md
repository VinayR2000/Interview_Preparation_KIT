# Error Handling

## Types of Errors in React

| Type | Where | Handling |
|------|-------|----------|
| Render errors | During component render | Error Boundary |
| Event handler errors | onClick, onChange, etc. | try/catch |
| Async/API errors | fetch, axios calls | try/catch + state |
| Network errors | No connectivity | Error state + retry |

---

## Error Boundaries

```jsx
class ErrorBoundary extends React.Component {
  state = { hasError: false, error: null, errorInfo: null };

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    // Log to monitoring service (Sentry, DataDog)
    errorService.log(error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="error-fallback">
          <h2>Something went wrong</h2>
          <p>{this.state.error?.message}</p>
          <button onClick={() => this.setState({ hasError: false })}>
            Try Again
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
```

---

## API Error Handling

```jsx
async function fetchWithErrorHandling(url, options = {}) {
  try {
    const response = await fetch(url, options);
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => null);
      throw {
        status: response.status,
        message: errorData?.message || response.statusText,
        data: errorData,
      };
    }
    
    return await response.json();
  } catch (err) {
    if (err.status) throw err;  // HTTP error (already structured)
    if (err.name === 'AbortError') throw err;  // Cancelled
    throw { status: 0, message: 'Network error. Check your connection.' };
  }
}
```

### Axios Error Handling
```jsx
try {
  const { data } = await api.get('/users');
} catch (error) {
  if (error.response) {
    // Server responded with error status (4xx, 5xx)
    console.log(error.response.status);   // 404, 500, etc.
    console.log(error.response.data);     // Server error message
  } else if (error.request) {
    // Request made but no response (network error)
    console.log('Network error');
  } else {
    // Error setting up request
    console.log(error.message);
  }
}
```

---

## Global Error Handling

```jsx
// Axios interceptor for global error handling
api.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status;
    
    if (status === 401) redirectToLogin();
    if (status === 403) showForbiddenToast();
    if (status === 500) showGenericErrorToast();
    if (!error.response) showNetworkErrorToast();
    
    return Promise.reject(error);
  }
);
```

---

## Key Interview Questions

**Q: What errors do Error Boundaries NOT catch?**
> Event handlers (use try/catch), async code (Promises), server-side rendering, and errors in the boundary itself. They only catch errors during rendering, lifecycle, and constructors.

**Q: How do you handle errors in event handlers?**
> Use try/catch within the handler. Set error state and display to user. Error boundaries don't catch these.

**Q: How do you implement retry logic?**
> Track attempt count in state. On error, show retry button. onClick retriggers the fetch. Optionally implement exponential backoff for automatic retries.
