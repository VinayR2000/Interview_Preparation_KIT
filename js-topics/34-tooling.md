# JavaScript Tooling

## npm (Node Package Manager)

```bash
npm init                    # Create package.json
npm install react           # Install to dependencies
npm install -D jest         # Install to devDependencies
npm install                 # Install all from package.json
npm uninstall package       # Remove package
npm update                  # Update packages
npm run build               # Run script
npm list --depth=0          # List installed packages
npx create-vite my-app     # Run without installing globally
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
    "test": "jest",
    "lint": "eslint src/",
    "format": "prettier --write src/"
  },
  "dependencies": {
    "react": "^18.2.0"
  },
  "devDependencies": {
    "vite": "^5.0.0",
    "jest": "^29.0.0",
    "eslint": "^8.0.0"
  }
}
```

### Version Ranges
| Symbol | Meaning | Example |
|--------|---------|---------|
| `^` | Compatible (minor + patch) | `^1.2.3` → `1.x.x` |
| `~` | Approximate (patch only) | `~1.2.3` → `1.2.x` |
| Exact | Exact version | `1.2.3` → only `1.2.3` |

### package-lock.json
- Locks exact versions of entire dependency tree
- Ensures reproducible installs across machines
- Committed to version control

---

## Babel (Transpilation)

Converts modern JS to older syntax for browser compatibility.

```javascript
// Input (ES2022)
const greet = (name) => `Hello, ${name}!`;
const value = obj?.nested?.value ?? 'default';

// Output (ES5)
var greet = function(name) { return "Hello, " + name + "!"; };
var _obj$nested;
var value = (_obj$nested = obj === null ? void 0 : obj.nested) !== null 
  ? _obj$nested.value : 'default';
```

---

## Vite (Build Tool)

| Feature | Description |
|---------|-------------|
| Dev server | Native ES modules, instant HMR |
| Build | Rollup under the hood |
| Speed | Much faster than Webpack |
| Config | Minimal (works out of box) |

---

## Webpack (Bundler)

| Concept | Description |
|---------|-------------|
| Entry | Starting file (`src/index.js`) |
| Output | Bundle file (`dist/bundle.js`) |
| Loaders | Transform files (babel-loader, css-loader) |
| Plugins | Additional functionality (HtmlWebpackPlugin, MiniCssExtract) |
| Code Splitting | Dynamic imports → separate chunks |

---

## ESLint & Prettier

```bash
# ESLint - Find problems in code
npx eslint src/             # Check
npx eslint src/ --fix       # Auto-fix

# Prettier - Format code
npx prettier --write src/   # Format all files
```

### ESLint catches: Unused variables, missing returns, bad practices
### Prettier handles: Formatting (indentation, quotes, semicolons)

---

## Source Maps

- Map bundled/minified code back to original source
- Enable debugging in browser DevTools
- Types: `source-map` (full), `cheap-module-source-map` (faster)
- Disable or use hidden source maps in production

---

## Key Interview Questions

**Q: What's the difference between dependencies and devDependencies?**
> `dependencies`: Required at runtime (React, Axios). Included in production bundle. `devDependencies`: Only for development (testing, linting, building). Not included in production.

**Q: What does tree shaking do?**
> Removes unused code (dead code elimination). Only works with ES modules (static `import`/`export`). Bundlers analyze which exports are actually imported and exclude the rest.

**Q: Why use a bundler?**
> Combines many files into few (fewer HTTP requests), minifies code, handles dependencies, enables code splitting, tree shaking, polyfills, and asset optimization.
