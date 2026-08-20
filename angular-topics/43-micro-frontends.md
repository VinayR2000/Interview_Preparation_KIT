# 43. Micro Frontends

---

## Theory

Micro frontends extend microservices principles to the frontend — independently deployable frontend applications that compose into a unified user experience.

### Key Concepts

| Concept | Description |
|---------|-------------|
| Module Federation | Webpack 5 feature for sharing code between builds at runtime |
| Independent Deployment | Each micro frontend deploys separately |
| Technology Agnostic | Different teams can use different frameworks |
| Shared Dependencies | Common libraries shared to avoid duplication |
| Shell/Host | Container app that loads micro frontends |

### Module Federation (Angular)

```typescript
// webpack.config.js (Host/Shell)
module.exports = {
  plugins: [
    new ModuleFederationPlugin({
      remotes: {
        userApp: 'userApp@http://localhost:4201/remoteEntry.js',
        orderApp: 'orderApp@http://localhost:4202/remoteEntry.js'
      },
      shared: ['@angular/core', '@angular/common', '@angular/router']
    })
  ]
};

// Shell routes
const routes: Routes = [
  { path: 'users', loadChildren: () => import('userApp/Module').then(m => m.UserModule) },
  { path: 'orders', loadChildren: () => import('orderApp/Module').then(m => m.OrderModule) }
];
```

```typescript
// webpack.config.js (Remote/Micro Frontend)
module.exports = {
  plugins: [
    new ModuleFederationPlugin({
      name: 'userApp',
      filename: 'remoteEntry.js',
      exposes: {
        './Module': './src/app/user/user.module.ts'
      },
      shared: ['@angular/core', '@angular/common', '@angular/router']
    })
  ]
};
```

### Architecture

```
┌─────────────────────────────────────────────────────┐
│ Shell Application (Host)                             │
│  ├── Header / Navigation                            │
│  ├── Authentication (shared)                        │
│  └── <router-outlet> → loads micro frontends        │
├─────────────────────────────────────────────────────┤
│ Micro Frontend A       │ Micro Frontend B           │
│ (User Management)      │ (Order Processing)         │
│ Team Alpha             │ Team Beta                  │
│ Angular 17             │ Angular 18                 │
│ Deployed independently │ Deployed independently     │
└────────────────────────┴────────────────────────────┘
```

### Advantages & Disadvantages

| Advantages | Disadvantages |
|-----------|---------------|
| Independent deployments | Increased complexity |
| Team autonomy | Shared dependency management |
| Incremental upgrades | Performance overhead |
| Scalable development | UX consistency challenges |
| Isolated failures | Complex debugging |

### Communication Between Micro Frontends

```typescript
// Custom events (framework agnostic)
window.dispatchEvent(new CustomEvent('user-selected', { detail: { userId: 42 } }));
window.addEventListener('user-selected', (e: CustomEvent) => { /* handle */ });

// Shared service via dependency injection (same Angular version)
// Shared state via URL (query params)
// Shared state via localStorage/sessionStorage
```

---

## Interview Questions and Answers

**Q1: What are micro frontends?**
> Micro frontends apply microservices principles to the UI — independently developed, tested, and deployed frontend applications that compose into one user-facing app. Each team owns a feature end-to-end (frontend + backend). They enable scaling development across teams without tight coupling.

**Q2: What is Module Federation?**
> Webpack 5's Module Federation allows separate builds to share code at runtime. A host app can dynamically load remote modules (components, routes) from other independently-built applications. Shared dependencies (Angular, RxJS) are loaded once. This is the most common micro frontend implementation for Angular.

**Q3: When should you use micro frontends?**
> When you have: multiple teams working on the same product, need for independent deployments, different release cadences per feature, or need to incrementally migrate from legacy to modern framework. Don't use for small teams or simple apps — the complexity overhead isn't worth it.

---

## Best Practices

1. **Share as little as possible** between micro frontends.
2. **Use Module Federation** for Angular micro frontends.
3. **Pin shared dependency versions** carefully.
4. **Design system** ensures visual consistency across teams.
5. **Communication via events** or URL state (not shared services).
6. **Nx monorepo** helps manage shared libraries and build orchestration.

---

## Related Topics

- → [15. Lazy Loading](./15-lazy-loading.md)
- → [42. Advanced Architecture](./42-advanced-architecture.md)
