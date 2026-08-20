# Build Tools

## npm / npx

### npm (Node Package Manager)
```bash
npm init                   # Create package.json
npm install react          # Install dependency
npm install -D jest        # Install dev dependency
npm install                # Install all from package.json
npm run build              # Run script from package.json
npm run dev                # Start dev server
```

### npx (Node Package Execute)
```bash
npx create-react-app my-app    # Run without global install
npx vite my-app                # Create Vite project
```

---

## package.json

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest",
    "lint": "eslint src/"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-router-dom": "^6.0.0"
  },
  "devDependencies": {
    "vite": "^5.0.0",
    "vitest": "^1.0.0",
    "eslint": "^8.0.0",
    "@types/react": "^18.2.0"
  }
}
```

### dependencies vs devDependencies
| dependencies | devDependencies |
|-------------|-----------------|
| Needed at runtime | Needed only for development |
| Included in production bundle | NOT in production |
| react, axios, react-router | vite, jest, eslint, TypeScript |

---

## Vite (Modern Build Tool)

```bash
npm create vite@latest my-app -- --template react-ts
```

### Why Vite over Webpack?
| Vite | Webpack (CRA) |
|------|---------------|
| Native ES modules (fast HMR) | Bundle everything (slow HMR) |
| Instant dev server start | Slow cold start |
| Esbuild for transpilation | Babel (slower) |
| Rollup for production | Webpack |
| Minimal config | Complex config |

---

## Webpack Concepts

| Concept | Description |
|---------|-------------|
| Entry | Starting point (`src/index.js`) |
| Output | Bundled file (`dist/bundle.js`) |
| Loaders | Transform files (babel-loader, css-loader) |
| Plugins | Additional functionality (HtmlWebpackPlugin) |
| Mode | development / production |

---

## Babel (Transpilation)

- Converts modern JS/JSX to browser-compatible code
- JSX → JavaScript
- ES2024 → ES5 (for older browsers)
- TypeScript → JavaScript

---

## Key Concepts

### Tree Shaking
- Remove unused code from bundle
- Relies on ES modules (static imports)
- `import { Button } from 'lib'` → only Button is bundled

### Source Maps
- Map bundled code back to original source
- Enable debugging in browser DevTools
- Disabled in production (or use hidden source maps)

### Environment Variables
```bash
# .env
VITE_API_URL=https://api.example.com
VITE_APP_TITLE=My App

# Usage in code (Vite)
const apiUrl = import.meta.env.VITE_API_URL;
```

---

## Key Interview Questions

**Q: What's the difference between Vite and Webpack?**
> Vite uses native ES modules for instant dev server startup and fast HMR. Webpack bundles everything before serving. Vite is significantly faster for development. Both produce optimized bundles for production.

**Q: What is tree shaking?**
> Dead code elimination. Bundlers analyze import/export statements and remove code that's never imported. Only works with ES modules (not CommonJS require).

**Q: Why not include devDependencies in production?**
> They're development tools (testing, linting, bundling). The production bundle only needs runtime code. Including dev deps would bloat the bundle unnecessarily.
