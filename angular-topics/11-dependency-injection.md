# 11. Dependency Injection — Very Important

---

## Theory

Dependency Injection (DI) is a design pattern where a class receives its dependencies from external sources rather than creating them itself. Angular has a powerful hierarchical DI system built into its core.

### Core Concepts

| Concept | Purpose |
|---------|---------|
| **Injector** | Container that creates and manages service instances |
| **Provider** | Recipe for creating a service |
| **Token** | Key used to look up a dependency |
| **@Injectable** | Marks a class as available for DI |

### @Injectable and providedIn

```typescript
// providedIn: 'root' — singleton, available everywhere (recommended)
@Injectable({ providedIn: 'root' })
export class AuthService {
  private token: string | null = null;
  login(credentials: LoginDTO): Observable<string> { return of(''); }
  isAuthenticated(): boolean { return !!this.token; }
}

// No providedIn — must be registered explicitly
@Injectable()
export class UserFormService {
  // Per-component instance when provided at component level
}
```

### Injecting Dependencies

```typescript
// Constructor injection (traditional)
@Component({ ... })
export class UserComponent {
  constructor(
    private userService: UserService,
    private router: Router
  ) {}
}

// inject() function (modern — Angular 14+)
@Component({ ... })
export class UserComponent {
  private userService = inject(UserService);
  private router = inject(Router);
}
```

### Provider Types

```typescript
providers: [
  // useClass — provide a class (default)
  { provide: LoggerService, useClass: ConsoleLoggerService },
  
  // useValue — provide a static value
  { provide: API_URL, useValue: 'https://api.example.com' },
  
  // useFactory — create with logic
  {
    provide: StorageService,
    useFactory: (platformId: Object) => {
      return isPlatformBrowser(platformId)
        ? new LocalStorageService()
        : new MemoryStorageService();
    },
    deps: [PLATFORM_ID]
  },
  
  // useExisting — alias to another provider
  { provide: AbstractLogger, useExisting: ConsoleLoggerService }
]
```

### InjectionToken

```typescript
import { InjectionToken } from '@angular/core';

// For non-class values
export const API_URL = new InjectionToken<string>('API_URL');
export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG');

// With factory default
export const WINDOW = new InjectionToken<Window>('Window', {
  providedIn: 'root',
  factory: () => window
});

// Usage
export class ApiService {
  private apiUrl = inject(API_URL);
}
```

### Hierarchical Injectors

```
Platform Injector
    ↓
Root Injector (providedIn: 'root')
    ↓
Module Injector (lazy-loaded modules)
    ↓
Component Injector (providers in @Component)
    ↓
Child Component Injector

Resolution: bottom-up (first match wins)
```

### Component-Level Providers

```typescript
@Component({
  selector: 'app-user-form',
  standalone: true,
  providers: [UserFormService], // NEW instance per component
  template: `...`
})
export class UserFormComponent {
  private formService = inject(UserFormService);
}
```

### Optional Dependencies

```typescript
// Inject with optional flag — returns null if not found
private analytics = inject(AnalyticsService, { optional: true });
```

### @Self, @SkipSelf, @Host

```typescript
// @Self — only look in THIS injector
inject(MyService, { self: true });

// @SkipSelf — skip THIS injector, start from parent
inject(MyService, { skipSelf: true });

// @Host — look in this and host component injector only
inject(MyService, { host: true });
```

### Multi Providers

```typescript
export const HTTP_INTERCEPTORS = new InjectionToken<HttpInterceptor[]>('interceptors');

providers: [
  { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: LoggingInterceptor, multi: true },
]
// Injecting returns array: [AuthInterceptor, LoggingInterceptor]
```

---

## Internal Working

### DI Resolution Process

```
Request: inject(UserService)

Step 1: Check current component's injector → NOT found
Step 2: Check parent component's injector → NOT found
Step 3: Check module injector → NOT found
Step 4: Check root injector → FOUND → return singleton
Step 5: If not found anywhere → NullInjectorError
        (Unless { optional: true } → returns null)
```

### Singleton vs Per-Component

```
providedIn: 'root':
  - 1 instance for entire app
  - Lives for app lifetime
  - All components share it

Component providers:
  - 1 instance per component instance
  - Destroyed with component
  - Children inherit parent's instance
```

### Tree-Shaking

```
providedIn: 'root':
  - If no component injects it → removed from bundle
  - Zero cost if unused

NgModule providers:
  - Always in bundle (cannot be tree-shaken)
  - Legacy pattern
```

---

## Diagram

```
Hierarchical Injector Tree:
┌─────────────────────────────────────────────────┐
│          Root Injector                            │
│  AuthService, HttpClient, Router (singletons)    │
└───────────────────┬─────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼──────────┐    ┌──────▼──────────┐
│  AppComponent     │    │ Lazy Module      │
│  (no providers)   │    │ (own injector)   │
└───────┬──────────┘    └─────────────────┘
        │
   ┌────┴────┐
   │         │
┌──▼────┐ ┌──▼────────────────┐
│Header │ │UserForm             │
│       │ │providers:[FormSvc]  │ ← New instance per component
└───────┘ │  ├── FieldComp      │ ← Inherits FormService
          │  └── FieldComp      │ ← Same FormService instance
          └────────────────────┘

Resolution (bottom → up): Component → Parent → Module → Root → Platform
```

---

## Code

```typescript
// Complete DI example: Configurable logging system
export interface LogConfig {
  level: 'debug' | 'info' | 'warn' | 'error';
  remote: boolean;
}

export const LOG_CONFIG = new InjectionToken<LogConfig>('LOG_CONFIG', {
  providedIn: 'root',
  factory: () => ({ level: 'info', remote: false })
});

export abstract class Logger {
  abstract log(message: string): void;
  abstract error(message: string, error?: Error): void;
}

@Injectable()
export class ConsoleLogger extends Logger {
  private config = inject(LOG_CONFIG);

  log(message: string): void {
    console.log(`[LOG] ${message}`);
  }

  error(message: string, error?: Error): void {
    console.error(`[ERROR] ${message}`, error);
    if (this.config.remote) {
      // send to remote
    }
  }
}

// App configuration
export const appConfig: ApplicationConfig = {
  providers: [
    { provide: Logger, useClass: ConsoleLogger },
    { provide: LOG_CONFIG, useValue: { level: 'warn', remote: true } }
  ]
};

// Usage
@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private logger = inject(Logger);

  getUser(id: number): Observable<User> {
    this.logger.log(`Fetching user ${id}`);
    return this.http.get<User>(`/api/users/${id}`).pipe(
      catchError(err => {
        this.logger.error(`Failed to fetch user ${id}`, err);
        throw err;
      })
    );
  }
}
```

```typescript
// Factory provider — platform-aware storage
export const BROWSER_STORAGE = new InjectionToken<Storage>('Storage', {
  providedIn: 'root',
  factory: () => {
    try {
      localStorage.setItem('__test__', '1');
      localStorage.removeItem('__test__');
      return localStorage;
    } catch {
      return sessionStorage;
    }
  }
});

@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private storage = inject(BROWSER_STORAGE);

  getToken(): string | null { return this.storage.getItem('auth_token'); }
  setToken(token: string): void { this.storage.setItem('auth_token', token); }
  removeToken(): void { this.storage.removeItem('auth_token'); }
}
```

---

## Dry Run

### Hierarchical Resolution

```
Setup:
- Root injector: AuthService (singleton)
- UserFormComponent providers: [FormService]

Component UserFormComponent injects both:

inject(AuthService):
  Step 1: Component injector → only has FormService → NOT found
  Step 2: Parent injector → NOT found
  Step 3: Root injector → FOUND → return singleton

inject(FormService):
  Step 1: Component injector → FOUND → return component-scoped instance

Two UserFormComponents on page:
  - Both share same AuthService (root singleton)
  - Each has OWN FormService (component provider)
```

---

## Complexity

| Operation | Performance |
|-----------|-------------|
| First injection (create) | O(tree depth) |
| Subsequent injection | O(1) cached |
| providedIn: 'root' | O(1) direct |
| Multi provider | O(n) collect all |

---

## Real Project Usage

```typescript
// Real-world: Environment-aware configuration
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: API_BASE_URL, useValue: environment.apiUrl },
    {
      provide: ErrorHandler,
      useFactory: () => environment.production
        ? new SentryErrorHandler()
        : new ErrorHandler()
    }
  ]
};

@Injectable({ providedIn: 'root' })
export class ApiService {
  private baseUrl = inject(API_BASE_URL);
  private http = inject(HttpClient);

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(`${this.baseUrl}${path}`);
  }
}
```

---

## Interview Questions and Answers

**Q1: What is Dependency Injection in Angular?**
> DI is a pattern where Angular's injector creates and provides service instances to components that declare them as dependencies. Instead of creating services manually, components receive them via constructor or inject(). This enables loose coupling, testability, and centralized instance management.

**Q2: What is the difference between providedIn: 'root' and component-level providers?**
> `providedIn: 'root'` creates a singleton — one instance shared across the entire app, tree-shakable if unused. Component-level `providers: [Service]` creates a new instance for each component instance — destroyed when the component is destroyed. Use root for shared state; component-level for per-instance state.

**Q3: What is an InjectionToken and when do you use it?**
> InjectionToken provides a unique token for non-class dependencies (strings, objects, interfaces). Since TypeScript interfaces don't exist at runtime, you can't use them as DI tokens. InjectionToken fills this gap: `const API_URL = new InjectionToken<string>('API_URL')` creates a unique runtime token.

**Q4: Explain useClass, useValue, useFactory, useExisting.**
> `useClass`: provides a class instance (supports DI in the class). `useValue`: provides a static value (configs, constants). `useFactory`: calls a function to create the value (conditional logic, needs deps). `useExisting`: aliases one token to another (same instance, different token).

**Q5: What is hierarchical injection and how does resolution work?**
> Angular has a tree of injectors matching the component tree. When a dependency is requested, Angular looks in the current injector first, then walks UP the tree (parent → grandparent → root). First match wins. This allows overriding services at any level — a child component can get a different instance than its parent.

---

## Follow-up Questions and Answers

**Q: What happens with DI in lazy-loaded modules?**
> Lazy-loaded modules get their own injector (child of root). Services provided in the lazy module are scoped to that module — not available globally. If the same service is also in root, the lazy module creates a SECOND instance. This can cause subtle bugs with state not being shared.

**Q: inject() vs constructor injection — which to prefer?**
> `inject()` (Angular 14+) is preferred for modern code. Benefits: works in functions (not just classes), enables composition (inject in field initializers), better tree-shaking, works with functional guards/interceptors. Constructor injection is still valid but more verbose.

**Q: How do you test components with DI?**
> In TestBed, override providers: `TestBed.configureTestingModule({ providers: [{ provide: UserService, useClass: MockUserService }] })`. This injects mock services. For inject() function, the same approach works — TestBed manages the injector.

---

## Common Mistakes

1. **Forgetting @Injectable() on services without providedIn**
   ```typescript
   // Error: No provider for MyService
   export class MyService { } // Missing @Injectable()
   ```

2. **Providing a root service in a component (creates duplicate)**
   ```typescript
   // ❌ Creates SECOND instance — not the singleton!
   @Component({ providers: [AuthService] }) // Already providedIn: 'root'
   
   // ✅ Don't re-provide root services
   @Component({ }) // Just inject it
   ```

3. **Circular dependency**
   ```typescript
   // ServiceA injects ServiceB, ServiceB injects ServiceA → ERROR
   // Fix: Use forwardRef() or restructure services
   ```

4. **Injecting outside injection context**
   ```typescript
   // ❌ inject() only works during construction
   onClick() { const svc = inject(MyService); } // ERROR
   
   // ✅ Inject in field or constructor
   private svc = inject(MyService);
   ```

---

## Best Practices

1. **Use `providedIn: 'root'`** for most services (singleton, tree-shakable).
2. **Use `inject()` function** over constructor injection (modern Angular).
3. **Use InjectionToken** for non-class dependencies.
4. **Component-level providers** only when you need per-instance state.
5. **Avoid circular dependencies** — extract shared logic to a third service.
6. **Use abstract classes as tokens** for polymorphic injection.
7. **Use `{ optional: true }`** for optional features (analytics, logging).
8. **Factory providers** for platform-aware or conditional creation.

---

## Production Considerations

- **Tree-shaking**: `providedIn: 'root'` services are removed if unused — smaller bundles.
- **Memory**: Component-level services are destroyed with the component — no leaks.
- **Lazy modules**: Services provided in lazy modules create separate instances — be intentional about scope.
- **Testing**: DI makes mocking trivial — use `{ provide: X, useClass: MockX }` in tests.

---

## Related Topics

- → [1. Angular Fundamentals](./01-angular-fundamentals.md)
- → [3. Components](./03-components.md)
- → [12. Services](./12-services.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
- → [30. DI Advanced](./30-di-advanced.md)
