# HTTP & API Communication

## HTTP Methods

| Method | Purpose | Idempotent | Body |
|--------|---------|-----------|------|
| GET | Retrieve | Yes | No |
| POST | Create | No | Yes |
| PUT | Replace | Yes | Yes |
| PATCH | Partial update | No | Yes |
| DELETE | Remove | Yes | Optional |

---

## fetch() in Detail

```javascript
// Complete pattern with error handling
async function apiCall(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        ...options.headers,
      },
    });

    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw { status: response.status, message: error.message || response.statusText };
    }

    return await response.json();
  } catch (err) {
    if (err.name === 'AbortError') throw new Error('Request timeout');
    throw err;
  } finally {
    clearTimeout(timeout);
  }
}
```

---

## Axios

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'https://api.example.com',
  timeout: 10000,
});

// Interceptors
api.interceptors.request.use(config => {
  config.headers.Authorization = `Bearer ${getToken()}`;
  return config;
});

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) refreshToken();
    return Promise.reject(error);
  }
);

// CRUD
const { data } = await api.get('/users');
await api.post('/users', { name: 'John' });
await api.put('/users/1', { name: 'Jane' });
await api.delete('/users/1');
```

---

## JSON

```javascript
// Serialize (object → string)
const json = JSON.stringify(obj);
JSON.stringify(obj, null, 2);           // Pretty print
JSON.stringify(obj, ['name', 'age']);    // Only specific keys
JSON.stringify(obj, (key, val) => val); // Custom replacer

// Parse (string → object)
const obj = JSON.parse(jsonString);
JSON.parse(str, (key, val) => {
  if (key === 'date') return new Date(val);  // Reviver function
  return val;
});

// Limitations of JSON
// Cannot serialize: functions, undefined, Symbol, circular references
// Date → string (not Date object)
// Map/Set → {} (empty object)
```

---

## HTTP Status Codes

| Range | Category | Common Codes |
|-------|----------|-------------|
| 2xx | Success | 200 OK, 201 Created, 204 No Content |
| 3xx | Redirect | 301 Moved, 304 Not Modified |
| 4xx | Client Error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found |
| 5xx | Server Error | 500 Internal Error, 502 Bad Gateway, 503 Unavailable |

---

## AbortController

```javascript
const controller = new AbortController();

// Pass signal to fetch
fetch('/api/data', { signal: controller.signal })
  .then(res => res.json())
  .catch(err => {
    if (err.name === 'AbortError') console.log('Cancelled');
  });

// Cancel after 5 seconds
setTimeout(() => controller.abort(), 5000);

// Cancel on user action
document.getElementById('cancel').onclick = () => controller.abort();
```

---

## Key Interview Questions

**Q: How do you handle API errors properly?**
> Check `response.ok` (fetch doesn't reject on 4xx/5xx). Use try/catch for network errors. Differentiate: network error (no response), client error (4xx), server error (5xx). Implement retry for transient failures.

**Q: What is CORS?**
> Cross-Origin Resource Sharing. Browser blocks requests to different origins by default. Server must include `Access-Control-Allow-Origin` header to permit cross-origin requests. Preflight OPTIONS request sent for non-simple requests.

**Q: fetch vs XMLHttpRequest vs axios?**
> fetch: Native, Promise-based, no progress events. XHR: Legacy, callback-based, has progress events. Axios: Library, auto JSON parsing, interceptors, request cancellation, better error handling (rejects on 4xx/5xx).
