# 1. Angular Fundamentals

---

## Theory

Angular is a **TypeScript-based, component-driven, full-featured frontend framework** built and maintained by Google. It follows a **component architecture** with built-in support for routing, forms, HTTP, animations, and dependency injection.

### Angular vs AngularJS

| Feature | AngularJS (1.x) | Angular (2+) |
|---------|-----------------|--------------|
| Language | JavaScript | TypeScript |
| Architecture | MVC | Component-based |
| Data Binding | Two-way (scope) | One-way default + two-way optional |
| Rendering | DOM manipulation | Virtual DOM-like change detection |
| Mobile Support | Limited | Full (Ionic, NativeScript) |
| Performance | Digest cycle (slow) | Zone.js + OnPush (fast) |
| Dependency Injection | Basic | Hierarchical, powerful |
| CLI | None | Angular CLI |

### Angular Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Angular Application                    │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  Component   │  │  Component   │  │  Component   │    │
│  │  ┌───────┐  │  │  ┌───────┐  │  │  ┌───────┐  │    │
│  │  │Template│  │  │  │Template│  │  │  │Template│  │    │
│  │  └───────┘  │  │  └───────┘  │  │  └───────┘  │    │
│  │  ┌───────┐  │  │  ┌───────┐  │  │  ┌───────┐  │    │
│  │  │ Class  │  │  │  │ Class  │  │  │  │ Class  │  │    │
│  │  └───────┘  │  │  └───────┘  │  │  └───────┘  │    │
│  │  ┌───────┐  │  │  ┌───────┐  │  │  ┌───────┐  │    │
│  │  │ Style  │  │  │  │ Style  │  │  │  │ Style  │  │    │
│  │  └───────┘  │  │  └───────┘  │  │  └───────┘  │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
├─────────────────────────────────────────────────────────┤
│  Services │ Directives │ Pipes │ Guards │ Interceptors  │
├─────────────────────────────────────────────────────────┤
│              Dependency Injection System                  │
├─────────────────────────────────────────────────────────┤
│  Router │ Forms │ HttpClient │ Animations │ i18n        │
└─────────────────────────────────────────────────────────┘
```

### Core Building Blocks

| Building Block | Purpose |
|---------------|---------|
| **Components** | UI building blocks with template + logic + style |
| **Templates** | HTML views with Angular syntax |
| **Directives** | Custom behavior attached to DOM elements |
| **Pipes** | Transform displayed data in templates |
| **Services** | Reusable business logic, shared across components |
| **Dependency Injection** | Provides instances of services to components |
| **Modules** | Organize related code (being replaced by standalone) |
| **Routing** | Navigation between views |

### Angular CLI

```bash
# Install Angular CLI globally
npm install -g @angular/cli

# Create a new project
ng new my-app

# Generate components, services, etc.
ng generate component user-list
ng generate service auth
ng generate pipe currency-format
ng generate directive highlight
ng generate guard auth
ng generate interceptor jwt

# Development server
ng serve                    # http://localhost:4200
ng serve --port 3000        # custom port
ng serve --open             # auto-open browser

# Build
ng build                    # development build
ng build --configuration production  # production build

# Test
ng test                     # unit tests (Karma)
ng e2e                      # end-to-end tests

# Lint
ng lint

# Update Angular
ng update @angular/cli @angular/core
```

### Project Structure

```
my-app/
├── src/
│   ├── app/
│   │   ├── app.component.ts        # Root component
│   │   ├── app.component.html       # Root template
│   │   ├── app.component.css        # Root styles
│   │   ├── app.component.spec.ts    # Root tests
│   │   ├── app.config.ts           # Application config (standalone)
│   │   ├── app.routes.ts           # Route definitions
│   │   ├── features/               # Feature modules/components
│   │   ├── shared/                 # Shared components, pipes, directives
│   │   ├── core/                   # Singleton services, guards, interceptors
│   │   └── models/                 # Interfaces, types, enums
│   ├── assets/                     # Static assets (images, fonts)
│   ├── environments/               # Environment configs
│   │   ├── environment.ts          # Development
│   │   └── environment.prod.ts     # Production
│   ├── index.html                  # Main HTML page
│   ├── main.ts                     # Application entry point
│   └── styles.css                  # Global styles
├── angular.json                    # Angular workspace config
├── package.json                    # Dependencies
├── tsconfig.json                   # TypeScript config
├── tsconfig.app.json               # App-specific TS config
└── tsconfig.spec.json              # Test-specific TS config
```

### angular.json (Key Sections)

```json
{
  "projects": {
    "my-app": {
      "architect": {
        "build": {
          "options": {
            "outputPath": "dist/my-app",
            "index": "src/index.html",
            "main": "src/main.ts",
            "styles": ["src/styles.css"],
            "scripts": [],
            "budgets": [
              { "type": "initial", "maximumWarning": "500kb", "maximumError": "1mb" }
            ]
          },
          "configurations": {
            "production": {
              "optimization": true,
              "sourceMap": false,
              "extractCss": true
            }
          }
        }
      }
    }
  }
}
```

### Bootstrapping (Standalone — Modern Angular)

```typescript
// main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig)
  .catch(err => console.error(err));
```

```typescript
// app.config.ts
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([]))
  ]
};
```

### Bootstrapping (NgModule-based — Legacy)

```typescript
// main.ts
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { AppModule } from './app/app.module';

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.error(err));
```

```typescript
// app.module.ts
@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, AppRoutingModule],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

### Angular Application Lifecycle

```
1. main.ts executes
       ↓
2. bootstrapApplication() / platformBrowserDynamic().bootstrapModule()
       ↓
3. Angular creates the platform (browser environment)
       ↓
4. Root component is instantiated
       ↓
5. Dependency injection tree is built
       ↓
6. Template is compiled (AOT = pre-compiled, JIT = runtime)
       ↓
7. Change detection starts
       ↓
8. Application is rendered in the browser
       ↓
9. Zone.js monitors async operations
       ↓
10. Change detection runs on events → UI updates
```

---

## Internal Working

### How Angular Compiles Templates

```
Template HTML
     ↓ AOT Compiler (ngc)
TypeScript Factory Code
     ↓ TypeScript Compiler (tsc)
JavaScript
     ↓ Bundler (webpack/esbuild)
Optimized Bundle
     ↓ Browser
DOM Rendering
```

### AOT vs JIT Compilation

| Feature | AOT (Ahead-of-Time) | JIT (Just-in-Time) |
|---------|---------------------|-------------------|
| When | Build time | Runtime (browser) |
| Bundle Size | Smaller (no compiler) | Larger (includes compiler) |
| Startup | Faster | Slower |
| Error Detection | At build time | At runtime |
| Production | ✅ Default | ❌ Not recommended |
| Development | ✅ Default (Angular 9+) | Legacy |

### Zone.js and Change Detection

```
User Event (click, input, timer, HTTP response)
       ↓
Zone.js intercepts the async operation
       ↓
Zone notifies Angular: "something happened"
       ↓
Angular triggers Change Detection
       ↓
Component tree is checked top-down
       ↓
DOM is updated where bindings changed
```

### Component Instantiation

```
1. Angular encounters <app-user> in template
       ↓
2. Looks up component registry for selector 'app-user'
       ↓
3. Creates component instance via DI
       ↓
4. Resolves all constructor dependencies
       ↓
5. Calls lifecycle hooks in order
       ↓
6. Renders template and attaches to DOM
```

---

## Diagram

```
Angular Application Architecture:
┌────────────────────────────────────────────────────────────┐
│                        Browser                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   Angular App                         │  │
│  │                                                       │  │
│  │  ┌─────────┐    ┌─────────┐    ┌─────────┐          │  │
│  │  │  Root   │───→│ Feature │───→│  Shared  │          │  │
│  │  │Component│    │Component│    │Component │          │  │
│  │  └────┬────┘    └────┬────┘    └──────────┘          │  │
│  │       │              │                                │  │
│  │       ▼              ▼                                │  │
│  │  ┌──────────────────────────┐                        │  │
│  │  │    Services Layer         │                        │  │
│  │  │  (Business Logic, State)  │                        │  │
│  │  └────────────┬─────────────┘                        │  │
│  │               │                                       │  │
│  │               ▼                                       │  │
│  │  ┌──────────────────────────┐                        │  │
│  │  │    HttpClient             │                        │  │
│  │  │  (REST API calls)         │                        │  │
│  │  └────────────┬─────────────┘                        │  │
│  └───────────────┼──────────────────────────────────────┘  │
│                  │                                          │
└──────────────────┼──────────────────────────────────────────┘
                   │ HTTP/HTTPS
                   ▼
┌────────────────────────────────────────────────────────────┐
│              Spring Boot Backend (REST API)                  │
└────────────────────────────────────────────────────────────┘
```

---

## Code

```typescript
// Minimal standalone component
import { Component } from '@angular/core';

@Component({
  selector: 'app-hello',
  standalone: true,
  template: `<h1>Hello, {{ name }}!</h1>`,
  styles: [`h1 { color: #333; }`]
})
export class HelloComponent {
  name = 'Angular';
}
```

```typescript
// Component with external template and styles
import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent {
  title = 'Dashboard';
  isLoggedIn = true;
  
  logout(): void {
    this.isLoggedIn = false;
  }
}
```

```typescript
// Full application bootstrap with providers
// app.config.ts
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations()
  ]
};
```

---

## Dry Run

### Bootstrapping Process

```
Step 1: Browser loads index.html
Step 2: index.html has <app-root></app-root>
Step 3: Browser loads main.js bundle
Step 4: main.ts calls bootstrapApplication(AppComponent, appConfig)
Step 5: Angular creates root injector with providers from appConfig
Step 6: Angular instantiates AppComponent
Step 7: AppComponent template is rendered
Step 8: <app-root> in index.html is replaced with rendered template
Step 9: Zone.js starts monitoring async operations
Step 10: Application is interactive
```

### Component Rendering

```
Template: <h1>Hello, {{ name }}!</h1>
Component: name = 'Angular'

Step 1: Angular compiles template (AOT — at build time)
Step 2: Creates view factory with binding instructions
Step 3: On render, evaluates {{ name }} → 'Angular'
Step 4: DOM output: <h1>Hello, Angular!</h1>
Step 5: If name changes → change detection rerenders binding
```

---

## Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Component creation | O(1) | DI resolution is cached |
| Change detection (Default) | O(n) | n = number of bindings in tree |
| Change detection (OnPush) | O(k) | k = bindings in changed subtree |
| Template compilation (AOT) | Build time | No runtime cost |
| Template compilation (JIT) | O(template size) | Runtime cost |

---

## Real Project Usage

### Enterprise Application Structure

```
src/app/
├── core/                          # Singleton services
│   ├── services/
│   │   ├── auth.service.ts
│   │   ├── api.service.ts
│   │   └── notification.service.ts
│   ├── interceptors/
│   │   ├── auth.interceptor.ts
│   │   └── error.interceptor.ts
│   ├── guards/
│   │   ├── auth.guard.ts
│   │   └── role.guard.ts
│   └── models/
│       ├── user.model.ts
│       └── api-response.model.ts
├── features/
│   ├── dashboard/
│   │   ├── dashboard.component.ts
│   │   ├── dashboard.component.html
│   │   └── dashboard.routes.ts
│   ├── users/
│   │   ├── user-list/
│   │   ├── user-detail/
│   │   └── users.routes.ts
│   └── orders/
│       ├── order-list/
│       ├── order-form/
│       └── orders.routes.ts
├── shared/
│   ├── components/
│   │   ├── loading-spinner/
│   │   ├── confirm-dialog/
│   │   └── pagination/
│   ├── pipes/
│   │   └── truncate.pipe.ts
│   └── directives/
│       └── highlight.directive.ts
├── app.component.ts
├── app.config.ts
└── app.routes.ts
```

---

## Interview Questions and Answers

**Q1: What is Angular and how does it differ from React?**
> Angular is a full-featured framework with built-in routing, forms, HTTP client, DI, and animations. React is a library focused only on the view layer — you need to add routing (React Router), state management (Redux), and HTTP (Axios) separately. Angular uses TypeScript by default and has opinionated structure; React is more flexible but requires more decisions.

**Q2: What is AOT compilation and why is it the default?**
> AOT (Ahead-of-Time) compiles templates during the build process, not in the browser. Benefits: faster startup (no compilation at runtime), smaller bundle (Angular compiler not included), earlier error detection, better security (templates are pre-compiled, less injectable). It's been default since Angular 9.

**Q3: Explain Angular's bootstrapping process.**
> In standalone Angular: `main.ts` calls `bootstrapApplication(AppComponent, appConfig)`. Angular creates the platform (browser), sets up the root injector with configured providers, instantiates the root component, compiles and renders its template, replacing the `<app-root>` element in `index.html`. Zone.js then monitors async operations to trigger change detection.

**Q4: What is the difference between standalone components and NgModule-based components?**
> Standalone components (Angular 14+) declare their own dependencies via the `imports` array in `@Component`. They don't need to be declared in any NgModule. This simplifies the mental model — each component is self-contained. NgModule-based components must be declared in a module's `declarations` array and depend on the module's `imports` for their dependencies.

**Q5: What is angular.json and what are its key configurations?**
> `angular.json` is the workspace configuration file. Key sections: `projects` (app configs), `architect.build` (build options like output path, budgets, optimization), `architect.serve` (dev server config), `architect.test` (test runner config). Build budgets enforce bundle size limits; configurations enable environment-specific settings.

---

## Follow-up Questions and Answers

**Q: How does Angular handle environments (dev vs prod)?**
> Angular uses `fileReplacements` in `angular.json` to swap environment files at build time. In production builds, `environment.ts` is replaced with `environment.prod.ts`. Modern Angular (v15+) recommends using `--define` flag or environment-specific configuration files instead of the legacy `environments/` folder pattern.

**Q: Can you mix standalone and NgModule-based components?**
> Yes. Standalone components can import NgModules (to use their declarations), and NgModules can import standalone components in their `imports` array. This enables incremental migration from NgModule to standalone architecture.

**Q: What replaced the deprecated `environment.ts` pattern?**
> Angular 15.1+ supports `APP_INITIALIZER` with dynamic config loading, or build-time `--define` flags. For runtime config, a common pattern is loading a JSON config file during app initialization. This is more flexible for containerized deployments where you don't want to rebuild for each environment.

---

## Common Mistakes

1. **Not using standalone components in new projects**
   ```typescript
   // ❌ Old way — creates unnecessary module complexity
   @NgModule({ declarations: [MyComponent] })
   export class MyModule { }
   
   // ✅ Modern way — standalone
   @Component({ standalone: true, imports: [...] })
   export class MyComponent { }
   ```

2. **Importing FormsModule globally instead of per-component**
   ```typescript
   // ❌ Pollutes global scope
   @NgModule({ imports: [FormsModule] }) // in AppModule
   
   // ✅ Import only where needed (standalone)
   @Component({ imports: [FormsModule] })
   ```

3. **Using JIT compilation in production**
   ```bash
   # ❌ Never do this
   ng build  # without production config
   
   # ✅ Always use production build
   ng build --configuration production
   ```

4. **Ignoring build budgets**
   ```json
   // ❌ No budget warnings = uncontrolled bundle growth
   // ✅ Set budgets in angular.json
   { "type": "initial", "maximumWarning": "500kb", "maximumError": "1mb" }
   ```

---

## Best Practices

1. **Use standalone components** for all new development (Angular 14+).
2. **Follow the Angular CLI** project structure conventions.
3. **Separate concerns**: core (singletons), shared (reusable), features (domain-specific).
4. **Use strict TypeScript** — enable `strict: true` in `tsconfig.json`.
5. **Set build budgets** to catch bundle size regressions early.
6. **Use AOT compilation** (default) — never disable it for production.
7. **Lazy load feature routes** to reduce initial bundle size.
8. **Keep components small** — extract logic into services.

---

## Production Considerations

- **Bundle Size**: Monitor with `ng build --stats-json` and `webpack-bundle-analyzer`. Target < 200KB initial load (gzipped).
- **Build Optimization**: Enable `optimization`, `sourceMap: false`, tree-shaking in production config.
- **Differential Loading**: Angular builds for both ES2015+ (modern browsers) and ES5 (legacy) automatically.
- **Service Workers**: Use `@angular/service-worker` for offline support and caching.
- **Error Tracking**: Integrate Sentry or similar for production error monitoring.
- **Performance Budget**: Set budgets in `angular.json` and fail CI on violations.

---

## Related Topics

- → [2. TypeScript Must-Know](./02-typescript-essentials.md)
- → [3. Components](./03-components.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
- → [13. Routing](./13-routing.md)
- → [29. Angular Modules](./29-angular-modules.md)
