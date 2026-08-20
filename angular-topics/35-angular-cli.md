# 35. Angular CLI

---

## Theory

Angular CLI is the command-line tool for creating, developing, building, and maintaining Angular applications.

### Essential Commands

```bash
# Project creation
ng new my-app                           # Create new project
ng new my-app --standalone --routing --style=scss  # With options

# Generate (scaffolding)
ng generate component features/user-list    # ng g c
ng generate service core/services/auth      # ng g s
ng generate pipe shared/pipes/truncate      # ng g p
ng generate directive shared/directives/highlight  # ng g d
ng generate guard core/guards/auth          # ng g guard
ng generate interceptor core/interceptors/auth
ng generate interface models/user           # ng g i
ng generate enum models/status
ng generate module features/admin           # ng g m (legacy)

# Development
ng serve                    # Dev server (localhost:4200)
ng serve --port 3000        # Custom port
ng serve --open             # Auto-open browser
ng serve --proxy-config proxy.conf.json  # With API proxy

# Build
ng build                    # Development build
ng build --configuration production  # Production build (optimized)

# Testing
ng test                     # Unit tests (Karma/Jest)
ng test --no-watch          # Run once (CI)
ng test --code-coverage     # With coverage report
ng e2e                      # End-to-end tests

# Lint
ng lint                     # Run linter (ESLint)

# Update
ng update                   # Check for updates
ng update @angular/cli @angular/core  # Update Angular

# Other
ng analytics                # Configure analytics
ng cache                    # Manage build cache
ng version                  # Show Angular CLI version
```

### Generate Options

```bash
# Component with specific options
ng g c user-card --standalone --change-detection=OnPush --skip-tests
ng g c user-list --inline-template --inline-style  # Inline template/style
ng g c user-form --flat  # No subdirectory

# Service
ng g s auth --skip-tests

# Module with routing (legacy)
ng g m admin --routing --module=app
```

### angular.json Key Configuration

```json
{
  "projects": {
    "my-app": {
      "architect": {
        "build": {
          "options": {
            "outputPath": "dist/my-app",
            "styles": ["src/styles.scss"],
            "scripts": [],
            "budgets": [
              { "type": "initial", "maximumWarning": "500kb", "maximumError": "1mb" }
            ]
          },
          "configurations": {
            "production": {
              "optimization": true,
              "sourceMap": false,
              "namedChunks": false
            }
          }
        },
        "serve": {
          "options": {
            "proxyConfig": "proxy.conf.json"
          }
        }
      }
    }
  }
}
```

---

## Interview Questions and Answers

**Q1: What is Angular CLI and what are its main commands?**
> Angular CLI is the official tool for Angular development. Key commands: `ng new` (create project), `ng generate` (scaffold components/services), `ng serve` (dev server with HMR), `ng build` (compile for production), `ng test` (run unit tests), `ng lint` (code quality). It handles webpack config, TypeScript compilation, and optimization automatically.

**Q2: How do you configure the Angular CLI build?**
> Via `angular.json` — configure build options (output path, styles, scripts), budgets (size limits), environments (production/dev configs), and architect targets (build, serve, test). Build budgets enforce bundle size limits in CI. Proxy config routes API calls during development.

---

## Best Practices

1. **Use `--standalone`** flag for all new components (default in Angular 17+).
2. **Set `--change-detection=OnPush`** as default in angular.json schematics.
3. **Configure build budgets** to prevent bundle size regression.
4. **Use proxy.conf.json** for development API calls.
5. **Run `ng update`** regularly to stay on latest Angular version.
6. **Use `ng generate`** consistently for proper file structure.

---

## Related Topics

- → [1. Angular Fundamentals](./01-angular-fundamentals.md)
- → [36. Build and Deployment](./36-build-deployment.md)
