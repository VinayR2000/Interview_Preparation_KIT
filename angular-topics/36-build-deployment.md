# 36. Build and Deployment

---

## Theory

Angular's build system compiles TypeScript, bundles modules, optimizes assets, and produces deployable static files.

### Build Types

| Build | Command | Use | Features |
|-------|---------|-----|----------|
| Development | `ng serve` | Local dev | Source maps, no optimization, HMR |
| Production | `ng build --configuration production` | Deployment | AOT, minification, tree shaking |

### Production Build Output

```bash
ng build --configuration production

# Output: dist/my-app/browser/
# ├── index.html           (~1KB)
# ├── main-[hash].js       (application code)
# ├── polyfills-[hash].js  (browser compatibility)
# ├── styles-[hash].css    (global styles)
# ├── chunk-[hash].js      (lazy-loaded routes)
# └── assets/              (images, fonts, etc.)
```

### Build Optimizations (Production)

| Optimization | Effect |
|-------------|--------|
| AOT Compilation | No template compiler in bundle |
| Tree Shaking | Remove unused code |
| Minification | Shorter variable names, remove whitespace |
| Dead Code Elimination | Remove unreachable code |
| Code Splitting | Lazy routes = separate chunks |
| Differential Loading | Modern + legacy bundles |
| Source Map Removal | No source maps (security) |

### Environment Configuration

```typescript
// environments/environment.ts (development)
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};

// environments/environment.prod.ts (production)
export const environment = {
  production: true,
  apiUrl: 'https://api.myapp.com'
};
```

### Build Budgets

```json
// angular.json
"budgets": [
  {
    "type": "initial",
    "maximumWarning": "500kb",
    "maximumError": "1mb"
  },
  {
    "type": "anyComponentStyle",
    "maximumWarning": "4kb",
    "maximumError": "8kb"
  }
]
```

### Deployment Options

```
Option 1: Static File Server (Nginx, Apache, S3+CloudFront)
  ng build → upload dist/ → configure SPA routing

Option 2: Docker Container
  Multi-stage build → Nginx serves static files

Option 3: Cloud Platforms
  Vercel, Netlify, Firebase Hosting, AWS Amplify

Option 4: Embedded in Spring Boot
  Copy dist/ to src/main/resources/static/ → single JAR

Option 5: Kubernetes
  Docker image → Kubernetes Deployment → Service → Ingress
```

### CI/CD Pipeline

```yaml
# GitHub Actions example
name: Build and Deploy
on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
      - run: npm ci
      - run: npm run lint
      - run: npm run test -- --no-watch --code-coverage
      - run: npm run build -- --configuration production
      - name: Deploy to Nginx/S3/etc
        run: # deployment command
```

### Nginx Deployment

```nginx
server {
    listen 80;
    server_name myapp.com;
    root /var/www/myapp;
    index index.html;

    # SPA routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets (hashed filenames)
    location ~* \.(js|css|png|jpg|gif|ico|svg|woff2?)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Never cache index.html
    location = /index.html {
        add_header Cache-Control "no-cache";
    }

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN";
    add_header X-Content-Type-Options "nosniff";
    add_header X-XSS-Protection "1; mode=block";
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains";

    # Gzip
    gzip on;
    gzip_types text/plain text/css application/json application/javascript;
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between AOT and JIT compilation?**
> AOT (Ahead-of-Time) compiles templates during the build — no compiler in the bundle, faster startup, catches template errors at build time. JIT (Just-in-Time) compiles in the browser at runtime — larger bundle (includes compiler), slower startup. AOT is the default for production since Angular 9.

**Q2: How do you optimize Angular bundle size?**
> 1. Lazy load feature routes (code splitting). 2. Tree shaking (automatic in production). 3. Set build budgets to catch regressions. 4. Analyze bundle with `webpack-bundle-analyzer`. 5. Remove unused imports/dependencies. 6. Use standalone components (smaller than NgModules). 7. Avoid importing entire libraries (import specific functions).

**Q3: How do you deploy Angular for production?**
> `ng build --configuration production` outputs optimized static files. Deploy to: Nginx (most common), S3+CloudFront, Docker container, or cloud platforms. Configure server for SPA routing (`try_files` in Nginx). Enable gzip/brotli compression. Set cache headers (1 year for hashed assets, no-cache for index.html).

**Q4: What is tree shaking?**
> Dead code elimination — removes unused exports from the final bundle. If you import `{ map }` from rxjs/operators but not `filter`, tree shaking removes `filter` from the bundle. Works with ES modules (static imports). Angular's `providedIn: 'root'` enables tree-shaking of unused services.

---

## Best Practices

1. **Always use production build** for deployment.
2. **Set build budgets** — catch size regressions early.
3. **Lazy load** all non-critical routes.
4. **Analyze bundles** periodically with webpack-bundle-analyzer.
5. **Cache static assets** aggressively (hashed filenames).
6. **Never cache index.html** — it references current bundles.
7. **Enable compression** (gzip/brotli) on the server.
8. **Use CI/CD** — automate build, test, deploy.

---

## Related Topics

- → [15. Lazy Loading](./15-lazy-loading.md)
- → [25. Angular Performance](./25-angular-performance.md)
- → [37. Angular + Docker](./37-angular-docker.md)
