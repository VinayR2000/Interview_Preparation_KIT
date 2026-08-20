# 6. Pipes

---

## Theory

Pipes transform data in templates without modifying the underlying data. They take an input value, apply a transformation, and return the formatted output for display.

### Syntax

```html
{{ value | pipeName }}
{{ value | pipeName:arg1:arg2 }}
{{ value | pipe1 | pipe2 }}  <!-- Chaining -->
```

### Built-in Pipes

| Pipe | Purpose | Example |
|------|---------|---------|
| `DatePipe` | Format dates | `{{ date \| date:'short' }}` |
| `CurrencyPipe` | Format currency | `{{ price \| currency:'USD' }}` |
| `DecimalPipe` | Format numbers | `{{ num \| number:'1.2-2' }}` |
| `PercentPipe` | Format percentage | `{{ 0.75 \| percent }}` |
| `UpperCasePipe` | Uppercase text | `{{ name \| uppercase }}` |
| `LowerCasePipe` | Lowercase text | `{{ name \| lowercase }}` |
| `TitleCasePipe` | Title case | `{{ name \| titlecase }}` |
| `JsonPipe` | Debug JSON output | `{{ obj \| json }}` |
| `AsyncPipe` | Subscribe to Observable/Promise | `{{ data$ \| async }}` |
| `SlicePipe` | Array/string slicing | `{{ arr \| slice:0:5 }}` |
| `KeyValuePipe` | Iterate object entries | `@for (kv of obj \| keyvalue)` |

### DatePipe Examples

```html
{{ today | date }}                    <!-- Sep 15, 2024 -->
{{ today | date:'short' }}           <!-- 9/15/24, 10:30 AM -->
{{ today | date:'medium' }}          <!-- Sep 15, 2024, 10:30:00 AM -->
{{ today | date:'long' }}            <!-- September 15, 2024 at 10:30:00 AM GMT+5 -->
{{ today | date:'full' }}            <!-- Sunday, September 15, 2024 at 10:30:00 AM -->
{{ today | date:'yyyy-MM-dd' }}      <!-- 2024-09-15 -->
{{ today | date:'dd/MM/yyyy HH:mm' }} <!-- 15/09/2024 10:30 -->
{{ today | date:'EEEE' }}            <!-- Sunday -->
```

### CurrencyPipe Examples

```html
{{ price | currency }}              <!-- $1,234.56 -->
{{ price | currency:'EUR' }}        <!-- €1,234.56 -->
{{ price | currency:'INR':'symbol':'1.0-0' }}  <!-- ₹1,235 -->
{{ price | currency:'USD':'code' }} <!-- USD1,234.56 -->
```

### DecimalPipe (number)

```html
<!-- Format: {minIntegerDigits}.{minFractionDigits}-{maxFractionDigits} -->
{{ 3.14159 | number:'1.2-2' }}     <!-- 3.14 -->
{{ 3.14159 | number:'3.1-5' }}     <!-- 003.14159 -->
{{ 1234567 | number }}              <!-- 1,234,567 -->
```

### AsyncPipe — Most Important

```html
<!-- Automatically subscribes and unsubscribes -->
@if (user$ | async; as user) {
  <h1>{{ user.name }}</h1>
  <p>{{ user.email }}</p>
}

<!-- With loading state -->
@if (users$ | async; as users) {
  @for (user of users; track user.id) {
    <app-user-card [user]="user" />
  }
} @else {
  <app-spinner />
}
```

### Custom Pipe

```typescript
import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'truncate',
  standalone: true
})
export class TruncatePipe implements PipeTransform {
  transform(value: string, limit: number = 50, trail: string = '...'): string {
    if (!value) return '';
    if (value.length <= limit) return value;
    return value.substring(0, limit) + trail;
  }
}

// Usage: {{ longText | truncate:100:'...' }}
```

### Pure vs Impure Pipes

| Feature | Pure Pipe (Default) | Impure Pipe |
|---------|-------------------|-------------|
| Execution | Only when input reference changes | Every change detection cycle |
| Performance | ✅ Cached, fast | ❌ Expensive |
| Use case | Stateless transforms | Array mutations, async |
| Declaration | `pure: true` (default) | `pure: false` |

```typescript
// Pure pipe — only re-evaluates when input reference changes
@Pipe({ name: 'filter', standalone: true, pure: true })
export class FilterPipe implements PipeTransform {
  transform(items: any[], field: string, value: string): any[] {
    if (!items || !value) return items;
    return items.filter(item => item[field].includes(value));
  }
}

// Impure pipe — re-evaluates every CD cycle (use sparingly!)
@Pipe({ name: 'filterImpure', standalone: true, pure: false })
export class FilterImpurePipe implements PipeTransform {
  transform(items: any[], field: string, value: string): any[] {
    if (!items || !value) return items;
    return items.filter(item => item[field].includes(value));
  }
}
```

---

## Internal Working

### Pure Pipe Execution

```
Change Detection Cycle:
1. Angular checks if pipe input reference changed (===)
2. If SAME reference → return cached result (skip transform)
3. If DIFFERENT reference → call transform() → cache result → return

Example:
  items = [1, 2, 3]
  items.push(4)        → Same reference → pipe NOT re-evaluated ❌
  items = [...items, 4] → New reference → pipe re-evaluated ✅
```

### Impure Pipe Execution

```
Change Detection Cycle:
1. Always calls transform() regardless of input change
2. No caching — computes every single time
3. Can detect mutations inside objects/arrays

Warning: In a component with 100 bindings checked 10 times/second,
an impure pipe runs 1000 times/second!
```

### AsyncPipe Internals

```
1. Subscribes to Observable/Promise on first evaluation
2. Returns null initially (until first emission)
3. Marks component for check (markForCheck) on each emission
4. Returns latest emitted value
5. Unsubscribes automatically on component destroy

This is why AsyncPipe works perfectly with OnPush change detection!
```

---

## Diagram

```
Pipe Data Flow:
┌──────────────┐          ┌──────────────┐          ┌──────────────┐
│   Raw Data    │──────→  │    Pipe       │──────→  │  Formatted   │
│ (component)   │  input  │ (transform)   │  output │  (template)  │
└──────────────┘          └──────────────┘          └──────────────┘

Example:
  1234.5  ──→  CurrencyPipe('USD')  ──→  "$1,234.50"
  Date()  ──→  DatePipe('short')    ──→  "9/15/24, 10:30 AM"

Pipe Chaining:
  value ──→ pipe1 ──→ pipe2 ──→ pipe3 ──→ display
  "HELLO" ──→ lowercase ──→ titlecase ──→ "Hello"
```

```
Pure vs Impure — Performance Impact:

Pure Pipe:
  CD Cycle 1: input changed? YES → transform() → cache result
  CD Cycle 2: input changed? NO  → return cached (skip transform)
  CD Cycle 3: input changed? NO  → return cached (skip transform)
  CD Cycle 4: input changed? YES → transform() → cache result
  Total calls: 2

Impure Pipe:
  CD Cycle 1: transform()
  CD Cycle 2: transform()
  CD Cycle 3: transform()
  CD Cycle 4: transform()
  Total calls: 4
```

---

## Code

```typescript
// Time-ago pipe (relative time)
@Pipe({ name: 'timeAgo', standalone: true })
export class TimeAgoPipe implements PipeTransform {
  transform(value: Date | string): string {
    if (!value) return '';
    const date = new Date(value);
    const now = new Date();
    const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (seconds < 60) return 'just now';
    if (seconds < 3600) return `${Math.floor(seconds / 60)} minutes ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)} hours ago`;
    if (seconds < 2592000) return `${Math.floor(seconds / 86400)} days ago`;
    if (seconds < 31536000) return `${Math.floor(seconds / 2592000)} months ago`;
    return `${Math.floor(seconds / 31536000)} years ago`;
  }
}
// Usage: {{ comment.createdAt | timeAgo }}

// File size pipe
@Pipe({ name: 'fileSize', standalone: true })
export class FileSizePipe implements PipeTransform {
  transform(bytes: number, decimals: number = 2): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(decimals)) + ' ' + sizes[i];
  }
}
// Usage: {{ document.size | fileSize }}

// Safe HTML pipe (for trusted HTML content)
@Pipe({ name: 'safeHtml', standalone: true })
export class SafeHtmlPipe implements PipeTransform {
  private sanitizer = inject(DomSanitizer);
  
  transform(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(value);
  }
}
// Usage: <div [innerHTML]="htmlContent | safeHtml"></div>
// WARNING: Only use with TRUSTED content — bypasses XSS protection!

// Highlight search term pipe
@Pipe({ name: 'highlight', standalone: true })
export class HighlightPipe implements PipeTransform {
  transform(text: string, search: string): string {
    if (!search || !text) return text;
    const regex = new RegExp(`(${search})`, 'gi');
    return text.replace(regex, '<mark>$1</mark>');
  }
}
// Usage: <span [innerHTML]="item.name | highlight:searchTerm"></span>

// Sort pipe
@Pipe({ name: 'sort', standalone: true })
export class SortPipe implements PipeTransform {
  transform<T>(array: T[], field: keyof T, direction: 'asc' | 'desc' = 'asc'): T[] {
    if (!array || !field) return array;
    const sorted = [...array].sort((a, b) => {
      if (a[field] < b[field]) return -1;
      if (a[field] > b[field]) return 1;
      return 0;
    });
    return direction === 'desc' ? sorted.reverse() : sorted;
  }
}
// Usage: @for (user of users | sort:'name':'asc'; track user.id) { }
```

---

## Dry Run

### Pure Pipe Caching

```
Component: searchTerm = 'ang'
Template: {{ items | filter:'name':searchTerm }}

CD Cycle 1:
  Input: items reference = 0x001, searchTerm = 'ang'
  First call → transform([...], 'name', 'ang') → [Angular, AngularJS]
  Cache: { input: 0x001, args: ['name', 'ang'], result: [...] }

CD Cycle 2 (user clicks something unrelated):
  Input: items reference = 0x001 (same!), searchTerm = 'ang' (same!)
  Pure pipe check: same reference → SKIP → return cached result

CD Cycle 3 (user types 'angular'):
  Input: items reference = 0x001 (same!), searchTerm = 'angular' (different!)
  Pure pipe check: args changed → call transform()
  transform([...], 'name', 'angular') → [Angular]
  Update cache

CD Cycle 4 (items reassigned):
  items = [...items, newItem]  // New reference 0x002
  Pure pipe check: reference changed → call transform()
```

### AsyncPipe Flow

```
Template: {{ users$ | async }}
Component: users$ = this.http.get<User[]>('/api/users')

Step 1: First CD cycle — AsyncPipe subscribes to users$
Step 2: HTTP request fires
Step 3: AsyncPipe returns null (nothing emitted yet)
Step 4: Template shows nothing (or @else block)
Step 5: HTTP response arrives → Observable emits [User, User, User]
Step 6: AsyncPipe receives value → calls markForCheck()
Step 7: Change detection runs → AsyncPipe returns [User, User, User]
Step 8: Template renders user list
Step 9: Component destroyed → AsyncPipe unsubscribes automatically
```

---

## Complexity

| Pipe | Time Complexity | Notes |
|------|----------------|-------|
| UpperCase/LowerCase | O(n) | n = string length |
| DatePipe | O(1) | Fixed format operations |
| CurrencyPipe | O(1) | Number formatting |
| SlicePipe | O(k) | k = slice size |
| Custom filter pipe | O(n) | n = array length |
| Sort pipe | O(n log n) | Sorting algorithm |
| AsyncPipe | O(1) | Just returns latest value |
| Pure pipe (cached) | O(0) | No computation when cached |

---

## Real Project Usage

```typescript
// In a real e-commerce application
@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, CurrencyPipe, TruncatePipe, TimeAgoPipe, AsyncPipe],
  template: `
    @if (products$ | async; as products) {
      @for (product of products; track product.id) {
        <div class="product-card">
          <h3>{{ product.name }}</h3>
          <p>{{ product.description | truncate:120 }}</p>
          <span class="price">{{ product.price | currency:'USD':'symbol':'1.2-2' }}</span>
          <span class="discount" *ngIf="product.discount > 0">
            -{{ product.discount | percent:'1.0-0' }}
          </span>
          <small>Added {{ product.createdAt | timeAgo }}</small>
          <small>Size: {{ product.fileSize | fileSize }}</small>
        </div>
      }
    } @else {
      <app-skeleton-loader [count]="6" />
    }
  `
})
export class ProductListComponent {
  products$ = inject(ProductService).getProducts();
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between pure and impure pipes?**
> Pure pipes (default) only execute when input reference changes — Angular caches the result and skips computation when inputs are the same. Impure pipes (`pure: false`) execute on every change detection cycle regardless of input changes. Always use pure pipes unless you specifically need to detect mutations inside objects/arrays.

**Q2: How does AsyncPipe work and why is it recommended?**
> AsyncPipe subscribes to an Observable or Promise, returns the latest emitted value, marks the component for change detection (`markForCheck()`), and automatically unsubscribes when the component is destroyed. It prevents memory leaks, works with OnPush strategy, and eliminates manual subscription management.

**Q3: Why won't a pure pipe detect array mutations?**
> Pure pipes use reference comparison (`===`). `array.push(item)` doesn't change the array reference — it's still the same object in memory. To trigger a pure pipe, you must create a new array: `array = [...array, item]`. This is by design — it's the same immutability concept that OnPush change detection relies on.

**Q4: When would you use an impure pipe?**
> Rarely. Impure pipes are appropriate when you need to detect mutations in objects/arrays without replacing references, or when the pipe depends on external state (like locale settings that change). In practice, prefer pure pipes with immutable data patterns. If you need impure behavior, consider using a computed property or signal instead.

**Q5: How do you chain pipes?**
> Pipes chain left to right: `{{ value | pipe1 | pipe2:arg }}`. The output of pipe1 becomes the input of pipe2. Example: `{{ text | lowercase | titlecase }}` first lowercases then title-cases. Order matters — `{{ date | date:'short' | uppercase }}` formats the date then uppercases it.

---

## Follow-up Questions and Answers

**Q: Can pipes accept multiple arguments?**
> Yes. Additional arguments are separated by colons: `{{ value | pipe:arg1:arg2:arg3 }}`. In the `transform` method, these become additional parameters: `transform(value: string, arg1: number, arg2: string, arg3: boolean)`.

**Q: What is the performance impact of using pipes vs methods in templates?**
> Pure pipes are cached — they only recompute when inputs change. Methods execute on every change detection cycle (could be many times per second). For any data transformation in templates, always prefer pipes over methods. A pipe called 100 times with the same input computes once; a method computes 100 times.

**Q: How does AsyncPipe work with OnPush change detection?**
> Perfectly. AsyncPipe calls `ChangeDetectorRef.markForCheck()` when a new value arrives. This tells Angular to check the component in the next CD cycle even though no `@Input()` reference changed. This is why AsyncPipe + OnPush is the recommended pattern for reactive data.

---

## Common Mistakes

1. **Using impure pipes for filtering/sorting large arrays**
   ```typescript
   // ❌ Executes every CD cycle — O(n) * many times/second
   @Pipe({ name: 'filter', pure: false })
   
   // ✅ Use pure pipe with immutable updates
   @Pipe({ name: 'filter', pure: true })
   // Then ensure new array reference: items = [...filtered]
   ```

2. **Forgetting to import pipes in standalone components**
   ```typescript
   // ❌ Error: The pipe 'date' could not be found
   @Component({ standalone: true, imports: [] })
   
   // ✅ Import pipes or modules containing them
   @Component({ standalone: true, imports: [DatePipe, CommonModule] })
   ```

3. **Using SafeHtml pipe with untrusted content**
   ```typescript
   // ❌ XSS vulnerability — user input rendered as HTML
   <div [innerHTML]="userComment | safeHtml"></div>
   
   // ✅ Only use with server-rendered trusted HTML
   <div [innerHTML]="trustedCmsContent | safeHtml"></div>
   ```

4. **Not handling null/undefined in custom pipes**
   ```typescript
   // ❌ Crashes if value is null
   transform(value: string): string {
     return value.toUpperCase();
   }
   
   // ✅ Guard against null
   transform(value: string | null | undefined): string {
     if (!value) return '';
     return value.toUpperCase();
   }
   ```

---

## Best Practices

1. **Always use pure pipes** (default) — impure pipes are a last resort.
2. **Use AsyncPipe** for Observable data instead of manual subscriptions.
3. **Handle null/undefined** gracefully in all custom pipes.
4. **Keep pipe transforms simple** — complex logic belongs in services.
5. **Use pipes instead of template methods** for data transformation.
6. **Chain pipes** for multiple transformations rather than creating one mega-pipe.
7. **Use built-in pipes** before creating custom ones — they handle localization.
8. **Test custom pipes** — they're pure functions and trivially testable.

---

## Production Considerations

- **Locale-sensitive pipes** (date, currency, number) depend on Angular's locale data. Import locale: `registerLocaleData(localeFr, 'fr')`.
- **AsyncPipe prevents memory leaks** — it's the safest way to consume Observables in templates.
- **Pure pipes are memoized** — excellent for expensive computations (formatting, filtering).
- **Avoid impure pipes on large lists** — they can cause serious performance degradation.
- **Bundle size**: Only import the pipes you need. `CommonModule` includes all common pipes.

---

## Related Topics

- → [4. Data Binding](./04-data-binding.md)
- → [5. Directives](./05-directives.md)
- → [17. RxJS](./17-rxjs.md)
- → [24. Change Detection](./24-change-detection.md)
- → [25. Angular Performance](./25-angular-performance.md)
