# 37. Angular + Docker

---

## Theory

Dockerizing Angular applications uses multi-stage builds: Node.js builds the app, then Nginx serves the static files. This produces small, production-ready container images.

### Multi-Stage Dockerfile

```dockerfile
# Stage 1: Build Angular
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/dist/my-app/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Nginx Configuration for SPA

```nginx
# nginx.conf
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    # SPA routing — all routes return index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets aggressively
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Don't cache index.html (app shell)
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # Proxy API requests to Spring Boot
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;
    gzip_min_length 256;
}
```

### Docker Compose (Full Stack)

```yaml
# docker-compose.yml
version: '3.8'
services:
  frontend:
    build: ./angular-app
    ports:
      - "80:80"
    depends_on:
      - backend

  backend:
    build: ./spring-boot-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/appdb
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=secret
    depends_on:
      - db

  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=appdb
      - POSTGRES_PASSWORD=secret
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

volumes:
  pgdata:
```

### .dockerignore

```
node_modules
dist
.git
.angular
*.md
.env
```

---

## Diagram

```
Docker Build Process:
┌──────────────────────────────────┐
│ Stage 1: Node (build)            │
│                                   │
│  npm ci                           │
│  ng build --production            │
│  Output: dist/my-app/browser/     │
│  (HTML, JS, CSS, assets)          │
│                                   │
│  Size: ~1GB (node_modules + src)  │
└──────────────┬───────────────────┘
               │ COPY dist/
               ↓
┌──────────────────────────────────┐
│ Stage 2: Nginx (serve)            │
│                                   │
│  Static files in /usr/share/nginx │
│  nginx.conf for SPA routing       │
│  Proxy /api to Spring Boot        │
│                                   │
│  Final image size: ~25MB          │
└──────────────────────────────────┘
```

---

## Interview Questions and Answers

**Q1: How do you Dockerize an Angular application?**
> Multi-stage build: Stage 1 uses Node image to `npm ci` + `ng build --production`. Stage 2 copies the built dist/ folder to Nginx image. Nginx serves static files and handles SPA routing (`try_files $uri /index.html`). Final image is ~25MB. Use .dockerignore to exclude node_modules from build context.

**Q2: Why use multi-stage Docker builds?**
> Stage 1 (Node) is ~1GB with node_modules — only needed for building. Stage 2 (Nginx) is ~25MB and only contains compiled static files. Multi-stage keeps the final image small, secure (no source code), and fast to deploy. It also improves CI/CD pipeline performance.

**Q3: How do you handle SPA routing in Docker/Nginx?**
> Angular uses client-side routing — URLs like /users/42 don't exist as server files. Without config, Nginx returns 404. Solution: `try_files $uri $uri/ /index.html` — if no file matches the URL, serve index.html and let Angular Router handle it.

**Q4: How do you handle environment-specific configuration in Docker?**
> Option 1: Build-time — build separate images per environment (not ideal). Option 2: Runtime — load config from a JSON file that's generated at container startup via entrypoint script. Option 3: Nginx can substitute environment variables in static files using `envsubst`. Option 2 is most flexible for Kubernetes deployments.

---

## Best Practices

1. **Multi-stage builds** — separate build from serve.
2. **Pin versions** — `node:20-alpine`, `nginx:1.25-alpine`.
3. **Use `npm ci`** (not `npm install`) for reproducible builds.
4. **SPA routing** — `try_files` in Nginx config.
5. **Cache busting** — Angular adds hashes to filenames; cache aggressively.
6. **Don't cache index.html** — it references the current JS bundles.
7. **Use .dockerignore** — exclude node_modules, .git, dist.
8. **Health checks** — add Nginx health endpoint for Kubernetes.

---

## Related Topics

- → [36. Build and Deployment](./36-build-deployment.md)
- → [38. Angular + Spring Boot Integration](./38-angular-spring-boot.md)
