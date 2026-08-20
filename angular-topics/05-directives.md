# 5. Directives

---

## Theory

Directives are classes that add behavior to DOM elements. Angular has three types of directives:

| Type | Purpose | Example |
|------|---------|---------|
| **Component** | Directive with a template | `@Component` |
| **Structural** | Change DOM layout (add/remove elements) | `@if`, `@for`, `*ngIf`, `*ngFor` |
| **Attribute** | Change appearance/behavior of element | `ngClass`, `ngStyle`, custom |

### Modern Control Flow (Angular 17+)

```html
<!-- @if — conditional rendering -->
@if (user) {
  <h1>Welcome, {{ user.name }}</h1>
} @else if (isLoading) {
  <app-spinner />
} @else {
  <p>Please log in</p>
}

<!-- @for — list rendering (replaces *ngFor) -->
@for (item of items; track item.id) {
  <app-item-card [item]="item" />
} @empty {
  <p>No items found.</p>
}

<!-- @for implicit variables -->
@for (item of items; track item.id; let idx = $index, first = $first, last = $last, even = $even, odd = $odd, count = $count) {
  <div [class.first]="first" [class.last]="last" [class.even]="even">
    {{ idx + 1 }}. {{ item.name }}
  </div>
}

<!-- @switch — multiple conditions -->
@switch (status) {
  @case ('active') { <span class="badge-active">Active</span> }
  @case ('inactive') { <span class="badge-inactive">Inactive</span> }
  @case ('pending') { <span class="badge-pending">Pending</span> }
  @default { <span>Unknown</span> }
}
```

### Legacy Structural Directives

```html
<!-- *ngIf -->
<div *ngIf="isLoggedIn">Welcome back!</div>
<div *ngIf="isLoggedIn; else loginTemplate">Welcome!</div>
<ng-template #loginTemplate>
  <p>Please log in</p>
</ng-template>

<!-- *ngIf with as (alias for async data) -->
<div *ngIf="user$ | async as user">
  {{ user.name }}
</div>

<!-- *ngFor -->
<li *ngFor="let item of items; trackBy: trackById; let i = index; let first = first; let last = last; let even = even; let odd = odd">
  {{ i + 1 }}. {{ item.name }}
</li>

<!-- *ngSwitch -->
<div [ngSwitch]="role">
  <p *ngSwitchCase="'admin'">Admin Panel</p>
  <p *ngSwitchCase="'user'">User Dashboard</p>
  <p *ngSwitchDefault>Guest View</p>
</div>
```

### Attribute Directives

```html
<!-- ngClass — multiple CSS classes -->
<div [ngClass]="{'active': isActive, 'disabled': isDisabled, 'highlight': isNew}">
</div>
<div [ngClass]="currentClasses">  <!-- object or array -->
</div>

<!-- ngStyle — multiple inline styles -->
<div [ngStyle]="{'color': textColor, 'font-size.px': fontSize, 'font-weight': isBold ? 'bold' : 'normal'}">
</div>

<!-- Single class binding (preferred for single class) -->
<div [class.active]="isActive"></div>

<!-- Single style binding -->
<div [style.color]="textColor"></div>
<div [style.width.px]="width"></div>
```

### Custom Attribute Directive

```typescript
import { Directive, ElementRef, HostListener, Input } from '@angular/core';

@Directive({
  selector: '[appHighlight]',
  standalone: true
})
export class HighlightDirective {
  @Input() appHighlight = 'yellow';  // Default color
  @Input() highlightTextColor = '#000';

  constructor(private el: ElementRef) {}

  @HostListener('mouseenter')
  onMouseEnter(): void {
    this.highlight(this.appHighlight || 'yellow');
  }

  @HostListener('mouseleave')
  onMouseLeave(): void {
    this.highlight('');
  }

  private highlight(color: string): void {
    this.el.nativeElement.style.backgroundColor = color;
    this.el.nativeElement.style.color = color ? this.highlightTextColor : '';
  }
}

// Usage:
// <p appHighlight>Default yellow</p>
// <p [appHighlight]="'lightblue'" highlightTextColor="white">Blue highlight</p>
```

### Custom Directive with Renderer2 (Best Practice)

```typescript
import { Directive, ElementRef, Renderer2, HostListener, Input } from '@angular/core';

@Directive({
  selector: '[appTooltip]',
  standalone: true
})
export class TooltipDirective {
  @Input('appTooltip') tooltipText = '';
  @Input() tooltipPosition: 'top' | 'bottom' | 'left' | 'right' = 'top';

  private tooltipElement: HTMLElement | null = null;

  constructor(
    private el: ElementRef,
    private renderer: Renderer2
  ) {}

  @HostListener('mouseenter')
  show(): void {
    this.tooltipElement = this.renderer.createElement('span');
    this.renderer.appendChild(
      this.tooltipElement,
      this.renderer.createText(this.tooltipText)
    );
    this.renderer.appendChild(this.el.nativeElement, this.tooltipElement);
    this.renderer.addClass(this.tooltipElement, 'tooltip');
    this.renderer.addClass(this.tooltipElement, `tooltip-${this.tooltipPosition}`);
  }

  @HostListener('mouseleave')
  hide(): void {
    if (this.tooltipElement) {
      this.renderer.removeChild(this.el.nativeElement, this.tooltipElement);
      this.tooltipElement = null;
    }
  }
}

// Usage:
// <button appTooltip="Click to save" tooltipPosition="bottom">Save</button>
```

### Host Binding and Host Listener

```typescript
@Directive({
  selector: '[appDropdown]',
  standalone: true
})
export class DropdownDirective {
  @HostBinding('class.open') isOpen = false;
  @HostBinding('style.border') border = '';

  @HostListener('click')
  toggle(): void {
    this.isOpen = !this.isOpen;
    this.border = this.isOpen ? '2px solid blue' : '';
  }

  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: HTMLElement): void {
    // Close when clicking outside
    if (!this.el.nativeElement.contains(target)) {
      this.isOpen = false;
      this.border = '';
    }
  }

  constructor(private el: ElementRef) {}
}
```

### ElementRef vs Renderer2

| Feature | ElementRef | Renderer2 |
|---------|-----------|-----------|
| Direct DOM access | ✅ `nativeElement` | ❌ Abstracted |
| SSR compatible | ❌ No DOM on server | ✅ Works everywhere |
| Security | ⚠️ XSS risk if used carelessly | ✅ Sanitized |
| Performance | Slightly faster | Negligible overhead |
| Best for | Reading DOM | Modifying DOM |

---

## Internal Working

### How Structural Directives Work

```
Template: @if (condition) { <div>Content</div> }

Internally:
1. Angular creates an embedded view (template)
2. Condition evaluated on each change detection
3. If true: view is created and inserted into ViewContainerRef
4. If false: view is detached and destroyed
5. DOM nodes are added/removed accordingly
```

### How @for with track Works

```
Data: items = [{id:1, name:'A'}, {id:2, name:'B'}, {id:3, name:'C'}]

Step 1: Initial render — creates 3 DOM nodes (one per item)
Step 2: items = [{id:2, name:'B'}, {id:3, name:'C'}, {id:4, name:'D'}]

Without track (by reference):
  - All 3 items have new references → destroys all 3 nodes, creates 3 new

With track item.id:
  - id:1 gone → destroy its node
  - id:2 same → REUSE node (just update bindings if needed)
  - id:3 same → REUSE node
  - id:4 new → create new node

Result: 1 destroy + 1 create instead of 3 destroys + 3 creates
```

### Directive Resolution Order

```
1. Component directives (highest priority)
2. Structural directives (one per element)
3. Attribute directives (multiple allowed per element)

Multiple structural directives on same element:
<!-- ❌ Cannot have two structural directives on same element -->
<div *ngIf="show" *ngFor="let item of items">

<!-- ✅ Use ng-container -->
<ng-container *ngIf="show">
  <div *ngFor="let item of items">
</ng-container>

<!-- ✅ Modern syntax — no restriction -->
@if (show) {
  @for (item of items; track item.id) {
    <div>{{ item.name }}</div>
  }
}
```

---

## Diagram

```
Directive Types:
┌─────────────────────────────────────────────────────────┐
│                      Directives                          │
├───────────────┬───────────────────┬─────────────────────┤
│  Component    │    Structural     │     Attribute       │
│  (has view)   │ (change DOM tree) │ (change behavior)   │
├───────────────┼───────────────────┼─────────────────────┤
│ @Component    │ @if / *ngIf       │ ngClass             │
│ (AppComponent)│ @for / *ngFor     │ ngStyle             │
│               │ @switch / *ngSwitch│ [appHighlight]     │
│               │                   │ [appTooltip]        │
└───────────────┴───────────────────┴─────────────────────┘
```

```
Structural Directive — DOM Manipulation:
Before @if (true):               After @if (false):
┌─────────────────┐             ┌─────────────────┐
│  Parent Element  │             │  Parent Element  │
│  ┌─────────────┐│             │                  │
│  │ Child (shown)││             │  <!-- empty -->  │
│  └─────────────┘│             │                  │
└─────────────────┘             └─────────────────┘

@for rendering:
items = [A, B, C]
┌────┐ ┌────┐ ┌────┐
│ A  │ │ B  │ │ C  │
└────┘ └────┘ └────┘
```

---

## Code

```typescript
// Complete custom directive: Auto-focus on element
@Directive({
  selector: '[appAutoFocus]',
  standalone: true
})
export class AutoFocusDirective implements AfterViewInit {
  @Input() appAutoFocus = true; // Can disable

  constructor(private el: ElementRef) {}

  ngAfterViewInit(): void {
    if (this.appAutoFocus) {
      setTimeout(() => this.el.nativeElement.focus(), 0);
    }
  }
}

// Permission-based visibility directive
@Directive({
  selector: '[appHasPermission]',
  standalone: true
})
export class HasPermissionDirective implements OnInit {
  @Input('appHasPermission') permission = '';

  private authService = inject(AuthService);

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef
  ) {}

  ngOnInit(): void {
    if (this.authService.hasPermission(this.permission)) {
      this.viewContainer.createEmbeddedView(this.templateRef);
    } else {
      this.viewContainer.clear();
    }
  }
}
// Usage: <button *appHasPermission="'admin:delete'">Delete</button>

// Click-outside directive
@Directive({
  selector: '[appClickOutside]',
  standalone: true
})
export class ClickOutsideDirective {
  @Output() appClickOutside = new EventEmitter<void>();

  constructor(private el: ElementRef) {}

  @HostListener('document:click', ['$event.target'])
  onClick(target: HTMLElement): void {
    if (!this.el.nativeElement.contains(target)) {
      this.appClickOutside.emit();
    }
  }
}
// Usage: <div appClickOutside (appClickOutside)="closeDropdown()">

// Debounce click directive
@Directive({
  selector: '[appDebounceClick]',
  standalone: true
})
export class DebounceClickDirective implements OnInit, OnDestroy {
  @Input() debounceTime = 500;
  @Output() appDebounceClick = new EventEmitter<MouseEvent>();

  private clicks = new Subject<MouseEvent>();
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.clicks.pipe(
      debounceTime(this.debounceTime),
      takeUntil(this.destroy$)
    ).subscribe(event => this.appDebounceClick.emit(event));
  }

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.clicks.next(event);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
// Usage: <button (appDebounceClick)="save()" [debounceTime]="300">Save</button>
```

---

## Dry Run

### Custom Highlight Directive

```
Template: <p [appHighlight]="'lightblue'" highlightTextColor="white">Hello</p>

Step 1: Angular instantiates HighlightDirective
Step 2: @Input appHighlight = 'lightblue'
Step 3: @Input highlightTextColor = 'white'
Step 4: User hovers over <p>
Step 5: @HostListener('mouseenter') fires
Step 6: highlight('lightblue') called
Step 7: el.nativeElement.style.backgroundColor = 'lightblue'
Step 8: el.nativeElement.style.color = 'white'
Step 9: User moves mouse away
Step 10: @HostListener('mouseleave') fires
Step 11: highlight('') → removes background and text color
```

### @for with track

```
Initial: items = [{id:1, name:'Apple'}, {id:2, name:'Banana'}]
DOM: [<div>Apple</div>, <div>Banana</div>]

Action: items = [{id:2, name:'Banana'}, {id:1, name:'Apple'}, {id:3, name:'Cherry'}]

With track item.id:
Step 1: id:2 existed → reuse DOM node, move to position 0
Step 2: id:1 existed → reuse DOM node, move to position 1
Step 3: id:3 new → create new DOM node at position 2
Result: 0 destroys, 1 create, 2 moves (efficient)

Without track (by index):
Step 1: Index 0 → different content → update textContent
Step 2: Index 1 → different content → update textContent
Step 3: Index 2 → new → create node
Result: 2 updates, 1 create (slightly less efficient for complex templates)
```

---

## Complexity

| Directive | Time Complexity | Notes |
|-----------|----------------|-------|
| @if | O(1) | Boolean check per CD cycle |
| @for with track | O(n) initial, O(k) updates | k = changed items |
| @for without track | O(n) on every update | Recreates all nodes |
| ngClass (object) | O(keys) | Checks each class |
| ngStyle (object) | O(keys) | Checks each style |
| Custom attribute | O(1) per event | Event-driven |

---

## Real Project Usage

```typescript
// Real-world: Permission-based UI with structural directive
@Directive({
  selector: '[appRole]',
  standalone: true
})
export class RoleDirective implements OnInit, OnDestroy {
  @Input('appRole') requiredRoles: string[] = [];

  private authService = inject(AuthService);
  private destroy$ = new Subject<void>();

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.pipe(
      takeUntil(this.destroy$)
    ).subscribe(user => {
      const hasRole = user?.roles.some(r => this.requiredRoles.includes(r));
      if (hasRole) {
        this.viewContainer.createEmbeddedView(this.templateRef);
      } else {
        this.viewContainer.clear();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}

// Usage in template:
// <button *appRole="['admin', 'manager']">Delete User</button>
// <app-admin-panel *appRole="['admin']" />
```

---

## Interview Questions and Answers

**Q1: What is the difference between structural and attribute directives?**
> Structural directives change the DOM structure by adding/removing elements (`@if`, `@for`, `*ngIf`, `*ngFor`). They're prefixed with `*` (legacy) or use `@` syntax (modern). Attribute directives change the appearance or behavior of existing elements (`ngClass`, `ngStyle`, custom). You can have multiple attribute directives but only one structural directive per element (legacy syntax).

**Q2: Why should you use `track` in @for (or trackBy in *ngFor)?**
> Without track, Angular identifies items by reference. If the array is replaced (common with HTTP responses), all DOM nodes are destroyed and recreated even if the data is the same. With track, Angular uses the specified key (like `id`) to match old and new items, reusing DOM nodes for existing items. This dramatically improves performance for large lists.

**Q3: What is the difference between ElementRef and Renderer2?**
> `ElementRef` gives direct access to the native DOM element. It's not safe for server-side rendering (no DOM on server) and can expose XSS vectors. `Renderer2` provides an abstraction layer — it works across platforms (browser, server, web workers) and sanitizes operations. Use Renderer2 for DOM manipulation in directives; use ElementRef only for reading.

**Q4: How do you create a custom structural directive?**
> Inject `TemplateRef` (the template to render) and `ViewContainerRef` (where to insert). Call `viewContainer.createEmbeddedView(templateRef)` to show the element, `viewContainer.clear()` to remove it. The `*` prefix is syntactic sugar that wraps the element in `<ng-template>` and passes it as `TemplateRef`.

**Q5: What is the modern control flow syntax and why was it introduced?**
> Angular 17 introduced `@if`, `@for`, `@switch` as built-in control flow. Benefits: no need to import `CommonModule`, better performance (optimized at compile time), cleaner syntax, `@empty` block for empty lists, no restriction on multiple structural directives. It replaces `*ngIf`, `*ngFor`, `*ngSwitch` which required module imports and had syntactic limitations.

---

## Follow-up Questions and Answers

**Q: Can you use both @if and *ngIf in the same project?**
> Yes, they coexist. You can migrate incrementally. However, mixing in the same template is confusing — stick to one style per component. Use `ng generate @angular/core:control-flow` schematic to auto-migrate.

**Q: How does ng-container differ from ng-template?**
> `<ng-container>` is a grouping element that doesn't render any DOM node — perfect for applying structural directives without extra wrapper elements. `<ng-template>` defines a template that isn't rendered until explicitly instantiated (via structural directive, ViewContainerRef, or ngTemplateOutlet).

---

## Common Mistakes

1. **Multiple structural directives on one element (legacy syntax)**
   ```html
   <!-- ❌ Error -->
   <div *ngIf="show" *ngFor="let item of items">
   
   <!-- ✅ Use ng-container -->
   <ng-container *ngIf="show">
     <div *ngFor="let item of items">{{ item.name }}</div>
   </ng-container>
   ```

2. **Tracking by index instead of unique ID**
   ```html
   <!-- ❌ Defeats purpose of tracking -->
   @for (item of items; track $index) { }
   
   <!-- ✅ Track by stable unique identifier -->
   @for (item of items; track item.id) { }
   ```

3. **Direct DOM manipulation without Renderer2**
   ```typescript
   // ❌ Breaks SSR, not safe
   this.el.nativeElement.innerHTML = userInput;
   
   // ✅ Use Renderer2
   const text = this.renderer.createText(sanitizedValue);
   this.renderer.appendChild(this.el.nativeElement, text);
   ```

4. **Not cleaning up in custom directives**
   ```typescript
   // ❌ Memory leak — event listeners stay forever
   ngOnInit() { document.addEventListener('scroll', this.onScroll); }
   
   // ✅ Clean up
   ngOnDestroy() { document.removeEventListener('scroll', this.onScroll); }
   ```

---

## Best Practices

1. **Use modern control flow** (`@if`, `@for`, `@switch`) for new projects.
2. **Always use `track`** with a unique, stable identifier in `@for`.
3. **Use Renderer2** for DOM manipulation in directives.
4. **Keep directives focused** — one behavior per directive.
5. **Use `ng-container`** to avoid unnecessary wrapper elements.
6. **Clean up** event listeners and subscriptions in `ngOnDestroy`.
7. **Export directives** from shared modules/make standalone for reuse.

---

## Production Considerations

- **Large lists**: Use virtual scrolling (`@angular/cdk/scrolling`) instead of rendering all items.
- **Frequent list updates**: Proper `track` function prevents expensive DOM recreation.
- **SSR compatibility**: Always use Renderer2 in directives that manipulate DOM.
- **Bundle size**: Modern control flow is built-in (no CommonModule import needed).
- **Performance**: Structural directives create/destroy components — expensive for complex templates.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [4. Data Binding](./04-data-binding.md)
- → [6. Pipes](./06-pipes.md)
- → [10. View Queries](./10-view-queries.md)
- → [31. Templates Advanced](./31-templates-advanced.md)
