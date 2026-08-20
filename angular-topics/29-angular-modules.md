# 29. Angular Modules

---

## Theory

NgModules organize related code into cohesive blocks. While standalone components are the modern approach, understanding modules is essential for existing codebases.

### NgModule Structure

```typescript
@NgModule({
  declarations: [UserListComponent, UserCardComponent],  // Components, directives, pipes owned by this module
  imports: [CommonModule, ReactiveFormsModule, RouterModule],  // Other modules needed
  exports: [UserListComponent, UserCardComponent],  // What other modules can use
  providers: [UserService],  // Services scoped to this module (prefer providedIn: 'root')
  bootstrap: [AppComponent]  // Root component (only in AppModule)
})
export class UserModule { }
```

### Module Types

| Module | Purpose | Example |
|--------|---------|---------|
| **AppModule** | Root module, bootstraps app | `AppModule` |
| **Feature Module** | Encapsulates a feature area | `OrderModule`, `AdminModule` |
| **Shared Module** | Reusable components/pipes/directives | `SharedModule` |
| **Core Module** | Singleton services, app-wide components | `CoreModule` |
| **Lazy Module** | Loaded on demand via routing | `AdminModule` (lazy) |

### Shared Module Pattern

```typescript
@NgModule({
  declarations: [LoadingSpinnerComponent, TruncatePipe, HighlightDirective],
  imports: [CommonModule],
  exports: [
    // Re-export commonly used modules
    CommonModule,
    ReactiveFormsModule,
    // Export own declarations
    LoadingSpinnerComponent,
    TruncatePipe,
    HighlightDirective
  ]
})
export class SharedModule { }
```

### NgModule vs Standalone

| Feature | NgModule | Standalone |
|---------|----------|-----------|
| Dependency declaration | Module-level imports | Component-level imports |
| Boilerplate | High (module per feature) | Low (self-contained) |
| Tree-shaking | Limited | Better |
| Lazy loading | loadChildren with module | loadComponent directly |
| Learning curve | Higher | Lower |
| Recommendation | Legacy/existing apps | All new development |

### Migration: NgModule → Standalone

```typescript
// Before (NgModule-based)
@NgModule({
  declarations: [UserListComponent],
  imports: [CommonModule, SharedModule]
})
export class UserModule { }

@Component({ selector: 'app-user-list', templateUrl: '...' })
export class UserListComponent { }

// After (Standalone)
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent, TruncatePipe],
  templateUrl: '...'
})
export class UserListComponent { }
// No module needed!
```

---

## Interview Questions and Answers

**Q1: What is the difference between declarations, imports, exports, and providers?**
> `declarations`: components/directives/pipes that belong to this module. `imports`: other modules whose exported declarations this module needs. `exports`: declarations that other modules can use when they import this module. `providers`: services available to this module's injector (prefer providedIn: 'root' instead).

**Q2: What is the difference between NgModule and standalone components?**
> NgModules group related code and manage dependencies at the module level — components must be declared in exactly one module. Standalone components (Angular 14+) manage their own dependencies via imports array directly in @Component — no module needed. Standalone is simpler, more tree-shakable, and is the recommended approach for all new code.

**Q3: What are SharedModule and CoreModule patterns?**
> SharedModule: contains reusable UI components, pipes, directives used across features — imported by each feature module. CoreModule: contains singleton services and app-wide components (header, footer) — imported ONCE in AppModule. With standalone + providedIn: 'root', these patterns are less necessary.

---

---

## Internal Working

### Module Resolution

```
Angular encounters <app-user-card> in a template:

NgModule approach:
1. Which module declares this component? → UserModule
2. Is UserModule imported by the module that uses <app-user-card>? → Yes
3. Resolve → render UserCardComponent

Standalone approach:
1. Does the using component's imports[] include UserCardComponent? → Yes
2. Resolve → render UserCardComponent

Key difference: NgModule = centralized registry, Standalone = per-component imports
```

### Lazy Module DI Scope

```
Eager Module:
  providers → merged into ROOT injector → singleton globally

Lazy Module:
  providers → gets OWN child injector → NOT shared with other modules
  
Problem scenario:
  SharedModule provides AuthService
  LazyModuleA imports SharedModule → gets NEW AuthService instance
  LazyModuleB imports SharedModule → gets ANOTHER new instance
  
  Two different AuthService instances! State not shared!

Fix: Use providedIn: 'root' (always singleton, always tree-shakable)
```

---

## Diagram

```
Module Import Graph:
┌─────────────────────────────────────────────────┐
│ AppModule                                        │
│   imports: [CoreModule, SharedModule, RoutingModule]│
│   bootstrap: [AppComponent]                      │
└───────┬─────────────┬───────────────────────────┘
        │             │
┌───────▼────┐  ┌─────▼──────────┐
│ CoreModule  │  │ Feature Routes  │
│ (imported   │  │ (lazy loaded)   │
│  once only) │  └─────┬──────────┘
│ - AuthService│       │
│ - HeaderComp │  ┌────▼──────────┐  ┌──────────────┐
└─────────────┘  │ AdminModule    │  │ OrderModule   │
                  │ (own injector) │  │ (own injector)│
                  │ imports:       │  │ imports:      │
                  │  [SharedModule]│  │  [SharedModule│
                  └───────────────┘  └──────────────┘

                  ┌───────────────┐
                  │ SharedModule   │
                  │ exports:       │
                  │  - CommonModule│
                  │  - SpinnerComp │
                  │  - TruncatePipe│
                  └───────────────┘
```

---

## Code

```typescript
// Core Module — import ONCE in AppModule
@NgModule({
  declarations: [HeaderComponent, FooterComponent, NotFoundComponent],
  imports: [CommonModule, RouterModule],
  exports: [HeaderComponent, FooterComponent]
})
export class CoreModule {
  // Prevent re-import
  constructor(@Optional() @SkipSelf() parentModule: CoreModule) {
    if (parentModule) {
      throw new Error('CoreModule is already loaded. Import it only in AppModule.');
    }
  }
}

// Feature Module with routing (legacy pattern)
@NgModule({
  declarations: [
    EmployeeListComponent,
    EmployeeDetailComponent,
    EmployeeFormComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    ReactiveFormsModule,
    EmployeeRoutingModule // RouterModule.forChild(routes)
  ]
})
export class EmployeeModule { }

// Equivalent standalone approach (modern — no module needed)
// Each component imports what IT needs
// Routes defined in employees.routes.ts
// Lazy loaded via loadChildren: () => import('./employees.routes')
```

---

## Dry Run

### Module Loading Sequence

```
App starts:
1. main.ts → platformBrowserDynamic().bootstrapModule(AppModule)
2. AppModule processes:
   - imports: [BrowserModule, CoreModule, SharedModule, AppRoutingModule]
   - All components in declarations become available
   - Services in providers registered at root injector
3. AppComponent renders → <router-outlet> ready

User navigates to /admin (lazy):
4. Router triggers loadChildren for AdminModule
5. Browser downloads admin-module-chunk.js
6. AdminModule processed:
   - Creates CHILD injector (not root)
   - declarations components available within AdminModule only
   - imports SharedModule → SharedModule's exports available
7. AdminDashboardComponent renders in <router-outlet>

Key: AdminModule's providers are SCOPED to admin — not available globally
```

---

## Interview Questions and Answers

**Q1: What is the difference between declarations, imports, exports, and providers?**
> `declarations`: components/directives/pipes that belong to this module — each can only be in ONE module's declarations. `imports`: other modules whose exported declarations this module needs. `exports`: declarations/modules that become available when someone imports this module. `providers`: services (prefer providedIn: 'root' instead — tree-shakable and avoids lazy module scoping issues).

**Q2: What is the difference between NgModule and standalone components?**
> NgModules group related code and manage dependencies centrally — components must be declared in exactly one module. Standalone components (Angular 14+) manage their own dependencies directly via imports[] in @Component — no module needed. Standalone is simpler, more tree-shakable, and the recommended approach. They can coexist during migration.

**Q3: What are SharedModule and CoreModule patterns?**
> SharedModule: reusable UI (components, pipes, directives) imported by multiple features. CoreModule: singletons and app-shell components imported ONCE in AppModule — has a guard to prevent re-import. With standalone + providedIn: 'root', CoreModule is unnecessary; SharedModule is replaced by importing individual standalone components.

**Q4: What happens with services in lazy-loaded modules?**
> Lazy-loaded modules get their own injector. If a service is provided in the lazy module's providers, it creates a SEPARATE instance (not the root singleton). This can cause bugs where state isn't shared. Fix: always use `providedIn: 'root'` for services that should be singletons — they go in the root injector regardless of which module imports them.

**Q5: How do you prevent a module from being imported twice?**
> In the module constructor, inject itself with @Optional() @SkipSelf(). If it gets an instance (meaning a parent already imported it), throw an error. This pattern is used for CoreModule to ensure it's only in AppModule. With standalone components, this issue doesn't exist.

---

## Follow-up Questions and Answers

**Q: Can a standalone component use NgModule-based components?**
> Yes. Import the NgModule in the standalone component's imports array: `@Component({ standalone: true, imports: [MaterialModule, FormsModule] })`. The standalone component can use everything the NgModule exports.

**Q: Can NgModule-based components use standalone components?**
> Yes. Import the standalone component in the NgModule's imports array (not declarations): `@NgModule({ imports: [StandaloneButtonComponent] })`. This enables incremental migration.

---

## Common Mistakes

1. **Declaring a component in multiple modules**
   ```typescript
   // ❌ Error: Component declared in 2 modules
   @NgModule({ declarations: [UserCardComponent] }) export class ModuleA { }
   @NgModule({ declarations: [UserCardComponent] }) export class ModuleB { }
   
   // ✅ Declare once, export, import the module
   @NgModule({ declarations: [UserCardComponent], exports: [UserCardComponent] })
   export class SharedModule { }
   ```

2. **Providing services in SharedModule (multiple instances in lazy modules)**
   ```typescript
   // ❌ Each lazy module that imports SharedModule gets new service instance
   @NgModule({ providers: [DataService] }) export class SharedModule { }
   
   // ✅ Use providedIn: 'root'
   @Injectable({ providedIn: 'root' }) export class DataService { }
   ```

3. **Creating a module for every single component (over-engineering)**
   ```typescript
   // ❌ Unnecessary boilerplate
   @NgModule({ declarations: [ButtonComponent], exports: [ButtonComponent] })
   export class ButtonModule { }
   
   // ✅ Just make it standalone
   @Component({ standalone: true, ... }) export class ButtonComponent { }
   ```

---

## Best Practices

1. **Use standalone components** for all new development.
2. **Migrate incrementally** — standalone can import NgModules and vice versa.
3. **Don't create modules for single components** — use standalone.
4. **Use `ng generate @angular/core:standalone`** schematic for automated migration.
5. **For existing NgModule apps**: SharedModule for reusables, CoreModule for singletons.
6. **Always use `providedIn: 'root'`** for services — avoids lazy module scoping issues.

---

## Production Considerations

- **Bundle impact**: NgModules can't be tree-shaken if imported. Standalone components are individually tree-shakable.
- **Migration strategy**: Start with leaf components (no children), work up to pages, migrate routes last.
- **Library authoring**: Libraries should export standalone components — consumers don't need to import modules.
- **Team coordination**: In large teams, agree on one approach per feature area to avoid confusion.

---

## Related Topics

- → [1. Angular Fundamentals](./01-angular-fundamentals.md)
- → [3. Components](./03-components.md)
- → [11. Dependency Injection](./11-dependency-injection.md)
- → [15. Lazy Loading](./15-lazy-loading.md)
