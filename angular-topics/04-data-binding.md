# 4. Data Binding

---

## Theory

Data binding is the mechanism that connects the **component class** (TypeScript) with the **template** (HTML). Angular supports four types of data binding.

### The Four Types

| Type | Syntax | Direction | Example |
|------|--------|-----------|---------|
| Interpolation | `{{ expression }}` | Component → Template | `{{ user.name }}` |
| Property Binding | `[property]="expression"` | Component → Template | `[src]="imageUrl"` |
| Event Binding | `(event)="handler()"` | Template → Component | `(click)="save()"` |
| Two-way Binding | `[(ngModel)]="property"` | Both directions | `[(ngModel)]="name"` |

### Interpolation

```html
<!-- String interpolation — converts expression to string -->
<h1>{{ title }}</h1>
<p>Welcome, {{ user.firstName + ' ' + user.lastName }}</p>
<p>Total: {{ price * quantity }}</p>
<p>Status: {{ isActive ? 'Active' : 'Inactive' }}</p>
<p>{{ getFormattedDate() }}</p>

<!-- Cannot use: assignments, new, chaining (;), ++ / -- -->
<!-- ❌ {{ x = 5 }} -->
<!-- ❌ {{ new Date() }} -->
<!-- ❌ {{ i++ }} -->
```

### Property Binding

```html
<!-- Binds component property to DOM element property -->
<img [src]="user.avatarUrl" [alt]="user.name">
<button [disabled]="isLoading">Submit</button>
<input [value]="searchTerm">
<div [hidden]="!showDetails">Details here</div>

<!-- Class binding -->
<div [class.active]="isActive"></div>
<div [class]="dynamicClass"></div>

<!-- Style binding -->
<div [style.color]="textColor"></div>
<div [style.font-size.px]="fontSize"></div>
<div [style.width.%]="progress"></div>

<!-- Attribute binding (for HTML attributes without DOM property) -->
<td [attr.colspan]="columnSpan"></td>
<button [attr.aria-label]="label"></button>
```

### Property Binding vs Attribute Binding

```html
<!-- Property binding — binds to DOM property (dynamic) -->
<input [value]="name">

<!-- Attribute binding — binds to HTML attribute (initial value only in native HTML) -->
<td [attr.colspan]="2"></td>

<!-- Key difference:
     - DOM properties are live (change reflects immediately)
     - HTML attributes are initial values only
     - Most HTML attributes have corresponding DOM properties
     - Some don't: colspan, aria-*, custom attributes → use [attr.xxx]
-->
```

### Event Binding

```html
<!-- Basic event binding -->
<button (click)="onSave()">Save</button>
<input (input)="onSearch($event)">
<input (keyup.enter)="onSubmit()">
<form (submit)="onFormSubmit($event)">
<div (mouseover)="onHover()" (mouseleave)="onLeave()">

<!-- $event — the DOM event object -->
<input (input)="onInput($event)">
<!-- In component: onInput(event: Event) {
  const value = (event.target as HTMLInputElement).value;
} -->

<!-- Key event filters -->
<input (keyup.enter)="search()">
<input (keydown.escape)="cancel()">
<input (keyup.shift.enter)="submitWithShift()">
<input (keydown.control.s)="save()">
```

### Two-Way Binding

```html
<!-- Requires FormsModule -->
<input [(ngModel)]="userName">

<!-- This is syntactic sugar for: -->
<input [ngModel]="userName" (ngModelChange)="userName = $event">

<!-- Works with custom components too -->
<app-slider [(value)]="volume"></app-slider>

<!-- Custom two-way binding in component:
  @Input() value: number;
  @Output() valueChange = new EventEmitter<number>();
  
  Convention: output name = input name + 'Change'
-->
```

### One-Way vs Two-Way Binding

```
One-Way (Component → View):
┌───────────┐      {{ }} / [prop]      ┌──────────┐
│ Component │ ──────────────────────→  │ Template │
│   Class   │                          │  (View)  │
└───────────┘                          └──────────┘

One-Way (View → Component):
┌───────────┐       (event)            ┌──────────┐
│ Component │ ←──────────────────────  │ Template │
│   Class   │                          │  (View)  │
└───────────┘                          └──────────┘

Two-Way:
┌───────────┐      [(ngModel)]         ┌──────────┐
│ Component │ ←──────────────────────→ │ Template │
│   Class   │                          │  (View)  │
└───────────┘                          └──────────┘
```

---

## Internal Working

### How Interpolation Works

```
Template: <h1>{{ user.name }}</h1>

AOT Compilation:
1. Parses {{ user.name }} as a text binding
2. Generates: element.textContent = component.user.name

Change Detection:
1. Previous value stored internally
2. On CD cycle: newValue = component.user.name
3. If newValue !== previousValue → update DOM
4. Store newValue as previousValue
```

### How Property Binding Works

```
Template: <img [src]="imageUrl">

AOT Compilation:
1. Parses [src] as a property binding
2. Generates: element.src = component.imageUrl

Change Detection:
1. Evaluates component.imageUrl
2. Compares with previous value (===)
3. If different → sets element.src = newValue
4. DOM updates via property assignment (not setAttribute)
```

### How Event Binding Works

```
Template: <button (click)="save()">

Compiled:
1. Registers event listener on the button element
2. Listener calls: component.save()
3. After handler executes → triggers change detection
4. Zone.js detects the async operation completed

Flow:
User Click → DOM Event → Zone.js intercepts → 
Handler executes → Change Detection triggered → DOM updated
```

### Two-Way Binding Internal Mechanism

```
Template: <input [(ngModel)]="name">

Desugarred to:
<input [ngModel]="name" (ngModelChange)="name = $event">

1. [ngModel]="name" → sets input value from component
2. User types → input event fires → ngModel directive captures
3. ngModelChange emits new value
4. name = $event → component property updated
5. Change detection → any other bindings using 'name' update
```

---

## Diagram

```
Data Binding Types:
┌──────────────────────────────────────────────────────────┐
│                    Component Class                         │
│  ┌────────────────────────────────────────────────────┐  │
│  │  title = 'Dashboard'                               │  │
│  │  imageUrl = 'avatar.png'                           │  │
│  │  isActive = true                                    │  │
│  │  userName = 'John'                                  │  │
│  │  save() { ... }                                     │  │
│  └────────────────────────────────────────────────────┘  │
└──────────┬───────────────┬────────────────┬──────────────┘
           │               │                │
    {{ title }}      [src]="imageUrl"   (click)="save()"
    ─────────→       ─────────→         ←─────────
    Interpolation    Property Binding   Event Binding
           │               │                │
           ▼               ▼                ▼
┌──────────────────────────────────────────────────────────┐
│                       Template                            │
│  <h1>Dashboard</h1>                                      │
│  <img src="avatar.png">                                  │
│  <button>Save</button>                                   │
│  <input [(ngModel)]="userName">  ← Two-way →             │
└──────────────────────────────────────────────────────────┘
```

---

## Code

```typescript
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

interface Product {
  id: number;
  name: string;
  price: number;
  imageUrl: string;
  inStock: boolean;
  quantity: number;
}

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Interpolation -->
    <h1>{{ product.name }}</h1>
    <p>Price: {{ product.price | currency:'USD' }}</p>
    <p>Total: {{ product.price * product.quantity | currency:'USD' }}</p>
    
    <!-- Property Binding -->
    <img [src]="product.imageUrl" [alt]="product.name">
    <button [disabled]="!product.inStock || isProcessing">
      {{ isProcessing ? 'Processing...' : 'Add to Cart' }}
    </button>
    
    <!-- Class and Style Binding -->
    <div [class.out-of-stock]="!product.inStock"
         [class.highlight]="isHighlighted"
         [style.opacity]="product.inStock ? 1 : 0.5">
      <span [style.color]="product.inStock ? 'green' : 'red'">
        {{ product.inStock ? 'In Stock' : 'Out of Stock' }}
      </span>
    </div>
    
    <!-- Event Binding -->
    <button (click)="addToCart()">Add to Cart</button>
    <button (click)="updateQuantity(1)">+</button>
    <button (click)="updateQuantity(-1)">-</button>
    
    <!-- Event with $event -->
    <input 
      type="text" 
      [value]="searchTerm"
      (input)="onSearchInput($event)"
      (keyup.enter)="performSearch()"
      (keyup.escape)="clearSearch()">
    
    <!-- Two-Way Binding -->
    <input [(ngModel)]="product.quantity" type="number" min="1" max="99">
    <p>Quantity: {{ product.quantity }}</p>
    
    <!-- Custom two-way binding -->
    <app-rating [(value)]="product.rating"></app-rating>
  `,
  styles: [`
    .out-of-stock { text-decoration: line-through; }
    .highlight { background: #fff3e0; }
  `]
})
export class ProductDetailComponent {
  product: Product = {
    id: 1,
    name: 'Angular Book',
    price: 49.99,
    imageUrl: 'assets/angular-book.jpg',
    inStock: true,
    quantity: 1
  };

  isProcessing = false;
  isHighlighted = false;
  searchTerm = '';

  addToCart(): void {
    this.isProcessing = true;
    setTimeout(() => this.isProcessing = false, 2000);
  }

  updateQuantity(delta: number): void {
    const newQty = this.product.quantity + delta;
    if (newQty >= 1 && newQty <= 99) {
      this.product.quantity = newQty;
    }
  }

  onSearchInput(event: Event): void {
    this.searchTerm = (event.target as HTMLInputElement).value;
  }

  performSearch(): void {
    console.log('Searching:', this.searchTerm);
  }

  clearSearch(): void {
    this.searchTerm = '';
  }
}
```

```typescript
// Custom two-way binding component
@Component({
  selector: 'app-rating',
  standalone: true,
  template: `
    @for (star of stars; track star) {
      <span 
        (click)="rate(star)"
        [class.filled]="star <= value">
        ★
      </span>
    }
  `,
  styles: [`
    span { cursor: pointer; font-size: 24px; color: #ccc; }
    .filled { color: #ffc107; }
  `]
})
export class RatingComponent {
  @Input() value = 0;
  @Output() valueChange = new EventEmitter<number>();

  stars = [1, 2, 3, 4, 5];

  rate(star: number): void {
    this.value = star;
    this.valueChange.emit(this.value);
  }
}
// Usage: <app-rating [(value)]="product.rating"></app-rating>
```

---

## Dry Run

### Interpolation Update

```
Initial: product.name = 'Angular Book'
Template: <h1>{{ product.name }}</h1>
DOM: <h1>Angular Book</h1>

User action: product.name = 'TypeScript Handbook'
Change Detection runs:
  previousValue = 'Angular Book'
  currentValue = 'TypeScript Handbook'
  'Angular Book' !== 'TypeScript Handbook' → UPDATE
DOM: <h1>TypeScript Handbook</h1>
```

### Two-Way Binding Flow

```
Initial: product.quantity = 1
Template: <input [(ngModel)]="product.quantity">
DOM: <input value="1">

User types "5":
Step 1: DOM input event fires
Step 2: ngModel directive captures event.target.value = "5"
Step 3: ngModelChange emits: product.quantity = 5
Step 4: Component property updated: this.product.quantity = 5
Step 5: Change detection runs
Step 6: <p>Quantity: {{ product.quantity }}</p> → updates to "Quantity: 5"
Step 7: <p>Total: {{ product.price * product.quantity }}</p> → updates to "$249.95"
```

---

## Complexity

| Binding Type | Change Detection Cost | Notes |
|-------------|----------------------|-------|
| Interpolation | O(1) per binding | String comparison |
| Property Binding | O(1) per binding | Reference comparison (===) |
| Event Binding | O(0) until triggered | Listener overhead minimal |
| Two-Way Binding | O(1) per binding | Property + Event combined |
| [class.x] | O(1) | Boolean check |
| [style.x] | O(1) | Value comparison |
| [ngClass] object | O(keys) | Iterates all keys |
| [ngStyle] object | O(keys) | Iterates all keys |

---

## Real Project Usage

```typescript
// Real-world: Search with debounce using event binding
@Component({
  selector: 'app-search',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="search-container">
      <input 
        type="text"
        [(ngModel)]="searchTerm"
        (ngModelChange)="onSearchChange($event)"
        (keyup.escape)="clear()"
        [placeholder]="placeholder"
        [attr.aria-label]="placeholder">
      <span class="count">{{ resultCount }} results</span>
    </div>
  `
})
export class SearchComponent implements OnInit, OnDestroy {
  @Input() placeholder = 'Search...';
  @Output() search = new EventEmitter<string>();
  @Output() resultCountChange = new EventEmitter<number>();
  
  searchTerm = '';
  resultCount = 0;
  private searchSubject = new Subject<string>();
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(term => this.search.emit(term));
  }

  onSearchChange(term: string): void {
    this.searchSubject.next(term);
  }

  clear(): void {
    this.searchTerm = '';
    this.search.emit('');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

---

## Interview Questions and Answers

**Q1: What are the four types of data binding in Angular?**
> Interpolation (`{{ }}`): one-way from component to view, displays expressions as text. Property binding (`[prop]`): one-way from component to DOM property. Event binding (`(event)`): one-way from DOM to component. Two-way binding (`[(ngModel)]`): bidirectional sync between component property and form input.

**Q2: What is the difference between property binding and attribute binding?**
> Property binding (`[src]`) sets the DOM property — it's live and reflects current state. Attribute binding (`[attr.colspan]`) sets the HTML attribute. Most HTML attributes have corresponding DOM properties, but some don't (colspan, aria-*, data-*, custom). Use `[attr.xxx]` when there's no DOM property equivalent.

**Q3: Why can't you use assignment operators in interpolation?**
> Interpolation is a one-way binding from component to view. It evaluates expressions and converts to string. Assignments (`=`), mutations (`++`, `--`), and `new` are side effects — they modify state, which would cause unpredictable behavior during change detection. Angular enforces pure expressions only.

**Q4: How does two-way binding work internally?**
> `[(ngModel)]="name"` is syntactic sugar for `[ngModel]="name" (ngModelChange)="name = $event"`. It combines property binding (component → view) with event binding (view → component). The convention is: `@Input() x` + `@Output() xChange` enables `[(x)]` syntax on parent.

**Q5: When should you use property binding vs interpolation?**
> Use interpolation for displaying text content. Use property binding for non-string values (booleans, objects, arrays) and HTML properties. Example: `[disabled]="isLoading"` (boolean) vs `{{ user.name }}` (text). Property binding is also preferred when the value isn't meant to be displayed as text.

---

## Follow-up Questions and Answers

**Q: What is the performance impact of calling methods in templates?**
> Methods called in interpolation (`{{ getTotal() }}`) execute on every change detection cycle — potentially hundreds of times per second. This is expensive if the method does computation. Solutions: use pure pipes (cached), precompute in component, or use signals/computed values.

**Q: How does Angular compare previous and current values in bindings?**
> Angular uses strict reference comparison (`===`) for property bindings. For objects and arrays, it only detects if the reference changed, not deep property changes. This is why OnPush works well with immutable data — new reference means change detected.

---

## Common Mistakes

1. **Calling expensive methods in templates**
   ```html
   <!-- ❌ Called every change detection cycle -->
   <p>{{ calculateTotal() }}</p>
   
   <!-- ✅ Precompute or use pipe -->
   <p>{{ total }}</p>
   <!-- or -->
   <p>{{ items | sumPipe }}</p>
   ```

2. **Using interpolation for boolean properties**
   ```html
   <!-- ❌ Sets attribute to string "true" -->
   <button disabled="{{ isDisabled }}">

   <!-- ✅ Property binding — sets DOM property to boolean -->
   <button [disabled]="isDisabled">
   ```

3. **Forgetting FormsModule for ngModel**
   ```typescript
   // ❌ Error: Can't bind to 'ngModel'
   @Component({ imports: [] }) // Missing FormsModule
   
   // ✅ Import FormsModule
   @Component({ imports: [FormsModule] })
   ```

4. **Not using $event type assertion**
   ```typescript
   // ❌ event is typed as Event — no .value property
   onInput(event: Event) {
     this.value = event.target.value; // Error
   }
   
   // ✅ Type assertion
   onInput(event: Event) {
     this.value = (event.target as HTMLInputElement).value;
   }
   ```

---

## Best Practices

1. **Use interpolation for text content**, property binding for DOM properties.
2. **Never call methods in templates** unless they're trivial (getter-like).
3. **Use strict typing** for event handlers with proper type assertions.
4. **Prefer one-way binding** — use two-way only for form inputs.
5. **Use `[class.x]` for single class toggles**, `[ngClass]` for multiple.
6. **Use `[style.x]` for single style**, `[ngStyle]` for multiple dynamic styles.
7. **Remember the banana-in-a-box** mnemonic: `[( )]` — square brackets outside, parentheses inside.

---

## Production Considerations

- **Template expressions should be side-effect free** — Angular may evaluate them multiple times.
- **Avoid complex expressions in templates** — extract to component properties for debuggability.
- **Two-way binding in forms** — use Reactive Forms in production for better control and testability.
- **Binding to `innerHTML`** is sanitized by Angular's DomSanitizer — but still avoid untrusted HTML.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [5. Directives](./05-directives.md)
- → [6. Pipes](./06-pipes.md)
- → [16. Forms](./16-forms.md)
- → [24. Change Detection](./24-change-detection.md)
