# 30. Dependency Injection — Advanced

---

## Theory

Advanced DI covers hierarchical injectors, injection tokens, multi-providers, factory providers, and the inject() function patterns used in modern Angular.

### Hierarchical DI — Provider Scoping

```typescript
// Root level — singleton for entire app
@Injectable({ providedIn: 'root' })
export class GlobalStateService { }

// Component level — new instance per component
@Component({
  providers: [FormStateService]  // Each component gets its own instance
})
export class UserFormComponent { }

// viewProviders — available to view children but NOT projected content
@Component({
  viewProviders: [PanelService]  // Not visible to <ng-content> children
})
export class PanelComponent { }
```

### InjectionToken with Factory

```typescript
// Token with default factory (tree-shakable)
export const LOGGER = new InjectionToken<Logger>('Logger', {
  providedIn: 'root',
  factory: () => {
    const isProd = inject(IS_PRODUCTION, { optional: true }) ?? false;
    return isProd ? new RemoteLogger() : new ConsoleLogger();
  }
});

// Platform-aware tokens
export const IS_MOBILE = new InjectionToken<boolean>('IS_MOBILE', {
  providedIn: 'root',
  factory: () => /Android|iPhone|iPad/i.test(navigator.userAgent)
});

export const LOCAL_STORAGE = new InjectionToken<Storage>('LocalStorage', {
  providedIn: 'root',
  factory: () => {
    if (inject(PLATFORM_ID) === 'browser') return localStorage;
    return new MemoryStorage(); // SSR fallback
  }
});
```

### Multi Providers

```typescript
// Collect multiple implementations under one token
export const APP_INITIALIZER = new InjectionToken<(() => void)[]>('APP_INIT');

providers: [
  { provide: APP_INITIALIZER, useFactory: () => () => loadConfig(), multi: true },
  { provide: APP_INITIALIZER, useFactory: () => () => loadUser(), multi: true },
  { provide: APP_INITIALIZER, useFactory: () => () => initAnalytics(), multi: true },
]
// Injecting APP_INITIALIZER returns array of all 3 functions
```

### inject() Function — Modern Patterns

```typescript
// In field initializers (injection context)
export class UserComponent {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);
  
  // With options
  private analytics = inject(AnalyticsService, { optional: true });
  private parentForm = inject(FormGroupDirective, { skipSelf: true, optional: true });
}

// In factory functions
export function createUserService(): UserService {
  const http = inject(HttpClient);
  const config = inject(APP_CONFIG);
  return new UserService(http, config.apiUrl);
}

// In functional guards/interceptors/resolvers
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService); // Works in injection context
  return auth.isAuthenticated();
};
```

### @Self, @SkipSelf, @Host — Injection Scope

```typescript
// @Self — only check THIS component's injector
@Component({
  providers: [LoggerService]
})
export class ChildComponent {
  // Will ONLY find LoggerService in this component's providers
  // Throws if not found here (won't look up the tree)
  private logger = inject(LoggerService, { self: true });
}

// @SkipSelf — skip this injector, start from parent
@Component({
  providers: [CounterService] // Provides own instance
})
export class ChildComponent {
  // Gets PARENT's CounterService, not this component's
  private parentCounter = inject(CounterService, { skipSelf: true });
  // Gets THIS component's CounterService
  private ownCounter = inject(CounterService, { self: true });
}
```

---

## Internal Working

### Injector Tree Resolution Algorithm

```
inject(ServiceX) called in ChildComponent:

Step 1: Check ChildComponent's element injector
  → providers/viewProviders array → found? Return instance
  → Not found → go up

Step 2: Check parent component's element injector
  → Walk up component tree checking each providers array
  → Found at any level? Return that instance (may be shared with siblings)
  
Step 3: Check environment injector (module-level)
  → Lazy module injector (if inside lazy route)
  → Root environment injector (providedIn: 'root')
  
Step 4: Check platform injector
  → PLATFORM_ID, DOCUMENT, etc.
  
Step 5: Not found anywhere → throw NullInjectorError
  → Unless { optional: true } → return null

Key insight: inject({ self: true }) → ONLY step 1, skip all others
             inject({ skipSelf: true }) → skip step 1, start at step 2
```

### viewProviders vs providers Internals

```
<app-panel>
  <app-projected-child></app-projected-child>  ← projected via ng-content
</app-panel>

PanelComponent template:
  <div>
    <ng-content></ng-content>
    <app-view-child></app-view-child>  ← view child (in own template)
  </div>

providers: [PanelService]
  → app-projected-child CAN access PanelService ✅
  → app-view-child CAN access PanelService ✅

viewProviders: [PanelService]
  → app-projected-child CANNOT access PanelService ❌ (NullInjectorError)
  → app-view-child CAN access PanelService ✅

Why? Projected content is created in PARENT's injector context,
     not PanelComponent's. viewProviders are only visible within
     the component's own view boundary.
```

---

## Diagram

```
Injector Hierarchy with viewProviders:

┌─────────────────────────────────────────────────────────┐
│ Root Injector: AuthService, HttpClient (singletons)      │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│ PanelComponent                                           │
│   providers: [PanelConfigService]  ← visible to ALL     │
│   viewProviders: [PanelStateService] ← visible to view  │
│                                                          │
│   View boundary:                                         │
│   ┌──────────────────────────────────────────────────┐  │
│   │ <app-view-child>  ← sees PanelConfigService ✅    │  │
│   │                    ← sees PanelStateService ✅    │  │
│   │                                                   │  │
│   │ <ng-content>                                      │  │
│   │   <app-projected> ← sees PanelConfigService ✅   │  │
│   │                    ← sees PanelStateService ❌   │  │
│   │   (created in parent's context)                   │  │
│   └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Code

```typescript
// Real-world: Form group with per-instance form state
@Injectable()
export class FormStateService {
  private dirty = signal(false);
  private submitting = signal(false);
  
  readonly isDirty = this.dirty.asReadonly();
  readonly isSubmitting = this.submitting.asReadonly();
  
  markDirty(): void { this.dirty.set(true); }
  markClean(): void { this.dirty.set(false); }
  setSubmitting(value: boolean): void { this.submitting.set(value); }
}

// Each form component gets its OWN FormStateService
@Component({
  selector: 'app-user-form',
  providers: [FormStateService], // Per-component instance!
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      ...
      <button [disabled]="formState.isSubmitting()">
        {{ formState.isSubmitting() ? 'Saving...' : 'Save' }}
      </button>
    </form>
    <p *ngIf="formState.isDirty()">Unsaved changes</p>
  `
})
export class UserFormComponent {
  formState = inject(FormStateService); // Gets THIS component's instance
  form = inject(FormBuilder).group({ ... });
}

// Two <app-user-form> on the same page → each has OWN FormStateService
// One can be dirty while the other is clean — independent state!

// Advanced: Composable inject() patterns
function injectLogger(context: string) {
  const logger = inject(LoggerService);
  return {
    info: (msg: string) => logger.log(`[${context}] ${msg}`, 'info'),
    error: (msg: string, err?: Error) => logger.error(`[${context}] ${msg}`, err)
  };
}

@Component({ ... })
export class OrderComponent {
  private log = injectLogger('OrderComponent'); // Reusable pattern
  
  ngOnInit() {
    this.log.info('Component initialized');
  }
}
```

---

## Dry Run

### viewProviders Scenario

```
Component tree:
  AppComponent
    └── PanelComponent (viewProviders: [ThemeService])
          ├── (view) TabsComponent  ← in Panel's template
          └── (content) CardComponent ← projected via <ng-content>

CardComponent injects ThemeService:
  Step 1: CardComponent element injector → not found
  Step 2: Walk up → AppComponent injector → not found
  Step 3: Root injector → not found
  Step 4: NullInjectorError! ❌

  Why? CardComponent is PROJECTED content. 
  PanelComponent's viewProviders are NOT visible to projected content.
  The injection walks up from CardComponent's CREATION context (AppComponent),
  NOT from PanelComponent where it's rendered.

TabsComponent injects ThemeService:
  Step 1: TabsComponent element injector → not found
  Step 2: Walk up → PanelComponent element injector → 
          viewProviders has ThemeService → FOUND ✅
  
  TabsComponent is a VIEW CHILD (in Panel's template) → can see viewProviders.
```

---

## Interview Questions and Answers

**Q1: What is hierarchical DI and why does it matter?**
> Angular has a tree of injectors (root → module → component → child). When you request a service, Angular walks UP the tree until it finds a provider. This allows: global singletons (root), feature-scoped instances (module), and per-component instances (component providers). Each level can override parent providers, enabling powerful patterns like per-form-instance state.

**Q2: What is the difference between providers and viewProviders?**
> `providers` makes a service available to the component, its view children, AND projected content. `viewProviders` makes it available only to the component and view children — projected content (ng-content) cannot access it. This is because projected content is created in the PARENT's injector context, not the host component's. Use viewProviders when you want to hide internal services from projected content.

**Q3: How does inject() differ from constructor injection?**
> `inject()` works in "injection context" — field initializers, factory functions, functional guards/interceptors. It's more flexible (works outside classes), enables composition patterns (helper functions that call inject()), and is required for functional patterns. Constructor injection only works in class constructors. Modern Angular prefers inject().

**Q4: What happens when a lazy module provides a service that's also in root?**
> Two separate instances are created! The root injector has one instance (used by eager components), and the lazy module's child injector creates another (used within that module). This is a common source of bugs — state appears "not shared". Fix: always use `providedIn: 'root'` for services that should be singletons.

**Q5: What is an injection context and why does it matter?**
> Injection context is the execution scope where `inject()` works: constructors, field initializers, factory functions (useFactory, InjectionToken factory), and functional APIs (guards, interceptors, resolvers). Calling `inject()` outside these contexts (e.g., in onClick handler, setTimeout callback) throws `NG0203: inject() must be called from an injection context`.

---

## Follow-up Questions and Answers

**Q: How do you call inject() from a regular function?**
> The function must be called DURING construction — in a field initializer or constructor. You can create composable "inject helpers": `function injectLogger() { return inject(LoggerService); }`. Call it in a field: `private log = injectLogger();`. This works because the field initializer runs in injection context.

**Q: What is runInInjectionContext?**
> Angular 16+ provides `runInInjectionContext(injector, fn)` to explicitly run code in an injection context. Useful for testing (running guards) or dynamically creating services. The EnvironmentInjector or Injector provides the context.

---

## Common Mistakes

1. **Calling inject() outside injection context**
   ```typescript
   // ❌ NG0203 Error
   onClick() { const svc = inject(MyService); }
   
   // ✅ Inject in field or constructor
   private svc = inject(MyService);
   onClick() { this.svc.doSomething(); }
   ```

2. **Expecting lazy module services to be global singletons**
   ```typescript
   // ❌ Creates separate instance in lazy module
   @NgModule({ providers: [SharedStateService] }) // In lazy module
   
   // ✅ Always use providedIn: 'root' for global singletons
   @Injectable({ providedIn: 'root' }) export class SharedStateService {}
   ```

3. **Using providers when viewProviders is needed (leaking internal services)**

---

## Best Practices

1. **Use `inject()` function** over constructor injection.
2. **Use `providedIn: 'root'`** for most services (tree-shakable singleton).
3. **Component `providers`** for per-instance services (form state, UI state).
4. **`viewProviders`** to hide internal services from projected content.
5. **InjectionToken with factory** for configurable/platform-aware dependencies.
6. **Composable inject helpers** for reusable injection patterns.
7. **Avoid deep injector hierarchies** — they're hard to debug.

---

## Production Considerations

- **Memory**: Component-level providers are destroyed with the component — good for cleanup.
- **Testing**: Override providers with mocks: `{ provide: Service, useValue: mockService }`.
- **Debugging**: Use Angular DevTools → Injector Tree to visualize the hierarchy.
- **Tree-shaking**: Only `providedIn: 'root'` services are tree-shakable. Module-level providers are always bundled.

---

## Related Topics

- → [11. Dependency Injection](./11-dependency-injection.md)
- → [12. Services](./12-services.md)
- → [9. Content Projection](./09-content-projection.md)
