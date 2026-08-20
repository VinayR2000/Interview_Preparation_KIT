# Deployment

## Production Build

```bash
# Vite
npm run build        # Creates dist/ folder
npm run preview      # Preview production build locally

# Output:
dist/
├── index.html       # Entry HTML
├── assets/
│   ├── index-[hash].js    # Main JS bundle
│   ├── vendor-[hash].js   # Third-party libs
│   └── index-[hash].css   # Styles
```

### Build Optimizations (Automatic)
- Minification (JS + CSS)
- Tree shaking (dead code removal)
- Code splitting (lazy imports)
- Asset hashing (cache busting)
- Gzip/Brotli compression

---

## Static Hosting

### SPA Routing Problem
- React Router handles routes client-side
- Server must return `index.html` for ALL routes
- Otherwise, direct URL access gets 404

### Nginx Configuration
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;  # SPA fallback
    }

    # Cache static assets aggressively
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Don't cache index.html
    location = /index.html {
        add_header Cache-Control "no-cache";
    }
}
```

---

## Docker

```dockerfile
# Multi-stage build
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## Environment Configuration

```jsx
// Access at runtime (Vite)
const apiUrl = import.meta.env.VITE_API_URL;

// Build-time replacement - values baked into bundle
// Never put secrets in frontend env vars!
```

---

## Cache Strategy

| Asset | Cache | Why |
|-------|-------|-----|
| `index.html` | no-cache | Must always get latest |
| `*.js` (hashed) | 1 year | Hash changes on update |
| `*.css` (hashed) | 1 year | Hash changes on update |
| Images (hashed) | 1 year | Hash changes on update |
| API responses | Varies | React Query handles |

---

## Key Interview Questions

**Q: Why do SPAs need special server configuration?**
> SPAs use client-side routing. When user navigates to `/dashboard` directly, the server looks for a `/dashboard` file (doesn't exist → 404). The server must be configured to serve `index.html` for all routes, letting React Router handle the path.

**Q: What's a multi-stage Docker build?**
> First stage: Install dependencies and build (large image with Node.js). Second stage: Copy only the built output into a lightweight nginx image. Results in much smaller production image.

**Q: How do you handle environment variables in React?**
> Use `.env` files with `VITE_` prefix. Values are replaced at build time (baked into the bundle). Never put secrets (API keys, DB credentials) in frontend env vars - they're visible to anyone.
