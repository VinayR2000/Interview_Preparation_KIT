# 10. View Queries — @ViewChild, @ViewChildren

---

## Theory

View queries allow a component to access elements, directives, or child components from its own template. They provide imperative access to the DOM and child component APIs.

### @ViewChild — Single Element

```typescript
@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <input #searchInput type="text" placeholder="Search...">
    <app-data-table #table [data]="data"></app-data-table>
    <div #chartContainer class="chart"></div>
  `
})
export class DashboardComponent implements AfterViewInit {
  // Query by template reference variable
  @ViewChild('searchInput') searchInput!: ElementRef<HTMLInputElement>;
  
  // Query by component type
  @ViewChild(DataTableComponent) dataTable!: DataTableComponent;
  
  // Query by template reference — getting ElementRef
  @ViewChild('chartContainer') chartContainer!: ElementRef;
  
  // Query with options
  @ViewChild('searchInput', { static: true }) staticInput!: ElementRef;
  // static: true → available in ngOnInit (if not inside *ngIf/@if)
  // static: false (default) → available in ngAfterViewInit

  ngAfterViewInit(): void {
    // All @ViewChild references are available here
    this.searchInput.nativeElement.focus();
    this.dataTable.refresh();
    this.initChart(this.chartContainer.nativeElement);
  }
}
```

### @ViewChildren — Multiple Elements

```typescript
@Component({
  selector: 'app-form',
  standalone: true,
  template: `
    <input #field type="text" placeholder="Name">
    <input #field type="email" placeholder="Email">
    <input #field type="tel" placeholder="Phone">
    
    @for (item of items; track item.id) {
      <app-item-card #card [item]="item"></app-item-card>
    }
  `
})
export class FormComponent implements AfterViewInit {
  // QueryList of all elements with #field reference
  @ViewChildren('field') fields!: QueryList<ElementRef<HTMLInputElement>>;
  
  // QueryList of all ItemCardComponent instances
  @ViewChildren(ItemCardComponent) cards!: QueryList<ItemCardComponent>;

  ngAfterViewInit(): void {
    // Access all fields
    this.fields.forEach(field => {
      console.log(field.nativeElement.placeholder);
    });

    // QueryList is live — updates when @for adds/removes items
    this.cards.changes.subscribe((cards: QueryList<ItemCardComponent>) => {
      console.log('Cards updated:', cards.length);
    });

    // Convert to array
    const fieldArray = this.fields.toArray();
    const firstField = this.fields.first;
    const lastField = this.fields.last;
  }
}
```

### Template References

```html
<!-- Template reference variable (#name) -->
<input #nameInput type="text">
<button (click)="greet(nameInput.value)">Greet</button>
<!-- nameInput refers to the HTMLInputElement directly in template -->

<!-- Reference to a directive/component -->
<form #myForm="ngForm">
  <!-- myForm refers to the NgForm directive instance -->
</form>
<button [disabled]="myForm.invalid">Submit</button>

<!-- Reference to a component -->
<app-timer #timer></app-timer>
<button (click)="timer.start()">Start Timer</button>
<button (click)="timer.stop()">Stop Timer</button>
```

### ElementRef, TemplateRef, ViewContainerRef

```typescript
// ElementRef — wrapper around native DOM element
@ViewChild('container') container!: ElementRef<HTMLDivElement>;
// Access: this.container.nativeElement (the actual DOM element)

// TemplateRef — reference to an <ng-template>
@ViewChild('itemTemplate') template!: TemplateRef<any>;
// Used with ngTemplateOutlet or ViewContainerRef

// ViewContainerRef — container that can hold views
@ViewChild('outlet', { read: ViewContainerRef }) outlet!: ViewContainerRef;
// Used for dynamic component/template insertion
```

### Reading Different Types from Same Reference

```typescript
@Component({
  template: `<app-tooltip #tip>Content</app-tooltip>`
})
export class ExampleComponent {
  // Read as component instance (default for component selectors)
  @ViewChild('tip') tipComponent!: TooltipComponent;
  
  // Read as ElementRef (the DOM element)
  @ViewChild('tip', { read: ElementRef }) tipElement!: ElementRef;
  
  // Read as ViewContainerRef (for dynamic insertion)
  @ViewChild('tip', { read: ViewContainerRef }) tipContainer!: ViewContainerRef;
}
```

### Static vs Dynamic Queries

```typescript
// static: true — resolved before change detection (ngOnInit)
// Use when: element is NOT inside *ngIf, @if, *ngFor, @for
@ViewChild('alwaysPresent', { static: true }) el!: ElementRef;

// static: false (default) — resolved after change detection (ngAfterViewInit)
// Use when: element is inside conditional/loop or you don't need it early
@ViewChild('conditional') el!: ElementRef;

// Example:
@Component({
  template: `
    <div #always>Always here</div>
    @if (showDetails) {
      <div #conditional>Sometimes here</div>
    }
  `
})
export class ExampleComponent implements OnInit, AfterViewInit {
  @ViewChild('always', { static: true }) always!: ElementRef;
  @ViewChild('conditional') conditional!: ElementRef; // May be undefined

  ngOnInit(): void {
    console.log(this.always); // ✅ Available (static: true)
    // console.log(this.conditional); // ❌ Not available yet
  }

  ngAfterViewInit(): void {
    console.log(this.always);       // ✅ Available
    console.log(this.conditional);  // ✅ Available (if showDetails is true)
  }
}
```

---

## Internal Working

### How ViewChild Resolution Works

```
Template Compilation (AOT):
1. Angular identifies #references and component/directive selectors
2. Creates query metadata for each @ViewChild/@ViewChildren

Component Initialization:
1. Component instance created
2. Template rendered (DOM created)
3. Angular resolves queries:
   - Walks component's view (own template only)
   - Matches references/types
   - Assigns results to decorated properties
4. ngAfterViewInit() called — queries guaranteed available

Query Update (for @ViewChildren):
1. Change detection runs
2. If DOM changes (items added/removed)
3. QueryList is updated
4. QueryList.changes emits
5. ngAfterViewChecked() called
```

### QueryList Internals

```
QueryList<T> is a live collection:
  - Iterable (supports for...of)
  - Has .changes Observable (emits on add/remove)
  - Properties: first, last, length
  - Methods: toArray(), forEach(), map(), filter(), find(), some(), reduce()
  - Updates automatically when DOM changes
  - Does NOT emit on property changes of existing items
```

---

## Diagram

```
@ViewChild vs @ContentChild:

Component Template (own view):
┌─────────────────────────────────────────────────┐
│  <div #myDiv>                    ← @ViewChild   │
│    <app-child #childRef />       ← @ViewChild   │
│    <ng-content></ng-content>     ← ContentChild  │
│  </div>                          (of app-child)  │
└─────────────────────────────────────────────────┘

Parent's Template:
┌─────────────────────────────────────────────────┐
│  <app-this-component>                            │
│    <h2 #projected>Title</h2>    ← @ContentChild │
│  </app-this-component>          (of this comp)   │
└─────────────────────────────────────────────────┘

Summary:
  @ViewChild   → queries OWN template
  @ContentChild → queries PROJECTED content (from parent)
```

---

## Code

```typescript
// Complete example: Image gallery with ViewChildren
@Component({
  selector: 'app-image-gallery',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="gallery" #galleryContainer>
      @for (image of images; track image.id) {
        <div class="image-wrapper" #imageWrapper
             [class.active]="activeIndex === $index"
             (click)="selectImage($index)">
          <img [src]="image.url" [alt]="image.alt" #img>
        </div>
      }
    </div>
    <div class="controls">
      <button (click)="prev()" [disabled]="activeIndex === 0">← Prev</button>
      <span>{{ activeIndex + 1 }} / {{ images.length }}</span>
      <button (click)="next()" [disabled]="activeIndex === images.length - 1">Next →</button>
    </div>
  `
})
export class ImageGalleryComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) images!: ImageItem[];
  
  @ViewChild('galleryContainer') gallery!: ElementRef<HTMLDivElement>;
  @ViewChildren('imageWrapper') imageWrappers!: QueryList<ElementRef>;
  @ViewChildren('img') imageElements!: QueryList<ElementRef<HTMLImageElement>>;

  activeIndex = 0;
  private resizeObserver: ResizeObserver | null = null;

  ngAfterViewInit(): void {
    // Set up intersection observer for lazy loading
    this.setupLazyLoading();
    
    // Watch for gallery resize
    this.resizeObserver = new ResizeObserver(() => this.scrollToActive());
    this.resizeObserver.observe(this.gallery.nativeElement);

    // React to dynamic image additions
    this.imageWrappers.changes.subscribe(() => {
      this.setupLazyLoading();
    });
  }

  selectImage(index: number): void {
    this.activeIndex = index;
    this.scrollToActive();
  }

  prev(): void {
    if (this.activeIndex > 0) {
      this.activeIndex--;
      this.scrollToActive();
    }
  }

  next(): void {
    if (this.activeIndex < this.images.length - 1) {
      this.activeIndex++;
      this.scrollToActive();
    }
  }

  private scrollToActive(): void {
    const wrappers = this.imageWrappers.toArray();
    if (wrappers[this.activeIndex]) {
      wrappers[this.activeIndex].nativeElement.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'center'
      });
    }
  }

  private setupLazyLoading(): void {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const img = entry.target as HTMLImageElement;
          if (img.dataset['src']) {
            img.src = img.dataset['src'];
            observer.unobserve(img);
          }
        }
      });
    });

    this.imageElements.forEach(img => observer.observe(img.nativeElement));
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
  }
}
```

```typescript
// Dynamic form with ViewChildren
@Component({
  selector: 'app-dynamic-form',
  standalone: true,
  imports: [FormsModule],
  template: `
    @for (field of fields; track field.name; let i = $index) {
      <div class="form-group">
        <label>{{ field.label }}</label>
        <input #formField
               [type]="field.type"
               [name]="field.name"
               [(ngModel)]="field.value"
               [required]="field.required"
               (keydown.tab)="focusNext(i)">
      </div>
    }
    <button (click)="validateAll()">Validate</button>
  `
})
export class DynamicFormComponent {
  @ViewChildren('formField') formFields!: QueryList<ElementRef<HTMLInputElement>>;
  
  @Input() fields: FormField[] = [];

  focusNext(currentIndex: number): void {
    const fieldsArray = this.formFields.toArray();
    const nextIndex = currentIndex + 1;
    if (nextIndex < fieldsArray.length) {
      fieldsArray[nextIndex].nativeElement.focus();
    }
  }

  validateAll(): void {
    const firstInvalid = this.formFields.find(
      field => !field.nativeElement.checkValidity()
    );
    if (firstInvalid) {
      firstInvalid.nativeElement.focus();
      firstInvalid.nativeElement.reportValidity();
    }
  }
}
```

---

## Dry Run

### @ViewChildren with Dynamic List

```
Initial: images = [{id:1}, {id:2}, {id:3}]
Template renders 3 image-wrapper divs

ngAfterViewInit():
  imageWrappers.length = 3
  imageWrappers.toArray() = [ElementRef1, ElementRef2, ElementRef3]
  Setup lazy loading for all 3

User action: images = [...images, {id:4}]
Change detection:
  @for renders 4th image-wrapper
  QueryList updates: imageWrappers.length = 4
  imageWrappers.changes emits QueryList(4 items)
  setupLazyLoading() called again for new image

User calls selectImage(2):
  activeIndex = 2
  scrollToActive():
    wrappers = imageWrappers.toArray()
    wrappers[2].nativeElement.scrollIntoView({...})
```

---

## Complexity

| Operation | Performance |
|-----------|-------------|
| @ViewChild resolution | O(1) — direct reference |
| @ViewChildren resolution | O(n) — n = matched elements |
| QueryList.changes | Event-driven (not polling) |
| QueryList.find() | O(n) — linear search |
| QueryList.toArray() | O(n) — creates new array |

---

## Interview Questions and Answers

**Q1: What is the difference between @ViewChild and @ViewChildren?**
> `@ViewChild` returns the first matching element/component. `@ViewChildren` returns a `QueryList` of all matching elements. Use `@ViewChild` for single elements (a specific input, a chart container). Use `@ViewChildren` for collections (all form inputs, all list items).

**Q2: When is @ViewChild available?**
> After `ngAfterViewInit()`. If `static: true` is set and the element is not inside a conditional (`@if`, `*ngIf`), it's available in `ngOnInit()`. Default is `static: false` (resolved after view init). Always use `ngAfterViewInit` for safety.

**Q3: What is the `read` option in @ViewChild?**
> `read` specifies what type to read from the query. By default, Angular returns the component instance (for components) or ElementRef (for plain elements). You can override: `@ViewChild('ref', { read: ViewContainerRef })` gives you a ViewContainerRef instead. Useful for dynamic component insertion.

**Q4: How does QueryList.changes work?**
> `QueryList.changes` is an Observable that emits whenever items are added or removed from the query (e.g., when `@for` renders new items or `@if` shows/hides elements). It does NOT emit when properties of existing items change. Subscribe in `ngAfterViewInit` and unsubscribe in `ngOnDestroy`.

**Q5: What is the difference between @ViewChild and template reference variables?**
> Template reference variables (`#ref`) are accessible within the template itself (for passing to event handlers, other bindings). `@ViewChild` makes them accessible in the component class (TypeScript). Use `#ref` for template-only access; use `@ViewChild` when you need programmatic control from the class.

---

## Common Mistakes

1. **Accessing ViewChild before ngAfterViewInit**
   ```typescript
   // ❌ undefined — view not rendered yet
   ngOnInit() { this.input.nativeElement.focus(); }
   
   // ✅ Available after view init
   ngAfterViewInit() { this.input.nativeElement.focus(); }
   ```

2. **Not handling undefined for conditional elements**
   ```typescript
   // ❌ Crashes if element is inside @if that's false
   ngAfterViewInit() { this.conditional.nativeElement.focus(); }
   
   // ✅ Guard against undefined
   ngAfterViewInit() { this.conditional?.nativeElement.focus(); }
   ```

3. **Not unsubscribing from QueryList.changes**
   ```typescript
   // ❌ Memory leak
   ngAfterViewInit() { this.items.changes.subscribe(...); }
   
   // ✅ Clean up
   ngAfterViewInit() {
     this.items.changes.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(...);
   }
   ```

---

## Best Practices

1. **Use `static: true`** only when the element is guaranteed to exist and you need it in ngOnInit.
2. **Prefer template reference variables** over ViewChild when possible (declarative > imperative).
3. **Always null-check** ViewChild references that might be inside conditionals.
4. **Use the `read` option** explicitly when you need ViewContainerRef or TemplateRef.
5. **Subscribe to `QueryList.changes`** to react to dynamic list changes.
6. **Use ViewChild sparingly** — it creates coupling between parent and child internals.
7. **Prefer Renderer2** over direct nativeElement manipulation for SSR compatibility.

---

## Production Considerations

- **SSR**: Direct `nativeElement` access doesn't work on the server. Guard with `isPlatformBrowser` or use Renderer2.
- **Performance**: Avoid excessive ViewChild queries — each adds to query resolution time.
- **Testing**: ViewChild makes unit testing harder — prefer @Input/@Output patterns where possible.
- **Dynamic content**: Use `QueryList.changes` to handle dynamically added elements properly.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [7. Component Lifecycle](./07-component-lifecycle.md)
- → [9. Content Projection](./09-content-projection.md)
- → [31. Templates Advanced](./31-templates-advanced.md)
- → [32. Dynamic Components](./32-dynamic-components.md)
