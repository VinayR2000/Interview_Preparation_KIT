# Production-Level React Topics

## Performance Monitoring

### Core Web Vitals
| Metric | Target | What it Measures |
|--------|--------|-----------------|
| LCP (Largest Contentful Paint) | < 2.5s | Loading performance |
| FID (First Input Delay) | < 100ms | Interactivity |
| CLS (Cumulative Layout Shift) | < 0.1 | Visual stability |
| INP (Interaction to Next Paint) | < 200ms | Overall responsiveness |

---

## Error Monitoring

```jsx
// Integration with Sentry/DataDog
import * as Sentry from '@sentry/react';

Sentry.init({ dsn: 'https://...', environment: 'production' });

// Error Boundary with reporting
class MonitoredErrorBoundary extends React.Component {
  componentDidCatch(error, errorInfo) {
    Sentry.captureException(error, { extra: errorInfo });
  }
  render() {
    if (this.state.hasError) return <ErrorFallback />;
    return this.props.children;
  }
}
```

---

## Feature Flags

```jsx
function useFeatureFlag(flagName) {
  const flags = useContext(FeatureFlagContext);
  return flags[flagName] ?? false;
}

function App() {
  const showNewDashboard = useFeatureFlag('new-dashboard');
  return showNewDashboard ? <NewDashboard /> : <OldDashboard />;
}
```

---

## Memory Leaks

### Common Causes
1. Uncleared timers/intervals
2. Unremoved event listeners
3. Uncancelled API requests
4. Stale closures holding references

### Prevention
```jsx
useEffect(() => {
  const timer = setInterval(tick, 1000);
  const controller = new AbortController();
  
  fetch(url, { signal: controller.signal }).then(setData);
  window.addEventListener('resize', handler);
  
  return () => {
    clearInterval(timer);         // Clear timer
    controller.abort();           // Cancel request
    window.removeEventListener('resize', handler);  // Remove listener
  };
}, []);
```

---

## Race Conditions & Network Failures

```jsx
// Race condition prevention
useEffect(() => {
  let cancelled = false;
  fetchData().then(data => { if (!cancelled) setData(data); });
  return () => { cancelled = true; };
}, [dependency]);

// Retry with exponential backoff
async function fetchWithRetry(url, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      return await fetch(url);
    } catch (err) {
      if (i === retries - 1) throw err;
      await new Promise(r => setTimeout(r, Math.pow(2, i) * 1000));
    }
  }
}
```

---

## Caching Strategy

```jsx
// Browser caching headers (configured on server/CDN)
Cache-Control: public, max-age=31536000    // Static assets (hashed filenames)
Cache-Control: no-cache                     // HTML (always revalidate)
Cache-Control: private, max-age=0          // User-specific data

// App-level caching
// React Query handles: staleTime, gcTime, background refetch
// Service Worker for offline support
```

---

## Security

### Security Headers
```
Content-Security-Policy: default-src 'self'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000
```

### React-Specific Security
- React auto-escapes JSX content (XSS protection)
- Never use `dangerouslySetInnerHTML` with user content
- Validate/sanitize all user input
- Use HttpOnly cookies for auth tokens
- Implement CSRF protection

---

## Environment Management

```bash
# .env.development
VITE_API_URL=http://localhost:3001
VITE_ENV=development

# .env.production
VITE_API_URL=https://api.production.com
VITE_ENV=production

# .env.staging
VITE_API_URL=https://api.staging.com
VITE_ENV=staging
```

---

## CI/CD Pipeline

```yaml
# Typical pipeline stages
1. Lint (ESLint)
2. Type check (TypeScript)
3. Unit tests (Jest/Vitest)
4. Build (vite build)
5. Integration tests
6. Deploy to staging
7. E2E tests (Cypress/Playwright)
8. Deploy to production
```

---

## Observability Checklist

| Area | Tool |
|------|------|
| Error tracking | Sentry, DataDog |
| Performance | Lighthouse, Web Vitals |
| Analytics | Google Analytics, Mixpanel |
| Logging | Console + remote logging service |
| Uptime | Pingdom, UptimeRobot |
| Bundle analysis | vite-plugin-bundle-analyzer |

---

## Key Interview Questions

**Q: How do you handle errors in production React apps?**
> Error boundaries for render errors with fallback UI. Global error handler for unhandled promises. Error monitoring (Sentry) for tracking. Graceful degradation with retry mechanisms.

**Q: How do you prevent memory leaks?**
> Cleanup in useEffect return: clear timers, cancel requests (AbortController), remove event listeners. Use the cleanup function consistently.

**Q: How do you handle offline scenarios?**
> Service workers for caching static assets and API responses. Show offline indicator. Queue mutations and sync when online. Use React Query's offline support.

**Q: What's your CI/CD pipeline for React?**
> Lint → Type check → Test → Build → Deploy. Use staging environment for QA. Feature flags for gradual rollout. Automated E2E tests before production deployment.

**Q: How do you manage environment-specific configuration?**
> Environment variables (.env files per environment). Build-time injection via bundler. Never hardcode secrets in frontend code. Use VITE_* prefix for Vite projects.
