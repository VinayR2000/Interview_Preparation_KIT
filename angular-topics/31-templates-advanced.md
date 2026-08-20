# 31. Templates — Advanced

---

## Theory

Advanced template features include template reference variables, ng-template, ng-container, dynamic templates, and template outlets.

### ng-template — Reusable Template Block

```html
<!-- ng-template is NOT rendered until explicitly used -->
<ng-template #greeting let-name="name">
  <h2>Hello, {{ name }}!</h2>
</ng-template>

<!-- Render with ngTemplateOutlet -->
<ng-container *ngTemplateOutlet="greeting; context: { name: 'John' }"></ng-container>

<!-- Conditional templates -->
<div *ngIf="isLoggedIn; else loginTemplate">
  Welcome back!
</div>
<ng-template #loginTemplate>
  <p>Please log in</p>
</ng-template>
```

### ng-container — Invisible Grouping

```html
<!-- ng-container adds NO DOM element -->
<!-- Perfect for structural directives without extra wrapper -->
<ng-container *ngIf="user">
  <h1>{{ user.name }}</h1>
  <p>{{ user.email }}</p>
</ng-container>

<!-- Without ng-container you'd need a wrapper div -->
<!-- <div *ngIf="user"> — adds unwanted div to DOM -->

<!-- Multiple structural directives -->
<ng-container *ngIf="showList">
  <li *ngFor="let item of items">{{ item }}</li>
</ng-container>
```

### ngTemplateOutlet — Dynamic Templates

```typescript
@Component({
  template: `
    <!-- Configurable list component with custom item template -->
    <app-list [items]="users" [itemTemplate]="userTemplate"></app-list>

    <ng-template #userTemplate let-user>
      <div class="user-item">
        <img [src]="user.avatar">
        <span>{{ user.name }}</span>
      </div>
    </ng-template>
  `
})
export class ParentComponent { }

@Component({
  selector: 'app-list',
  template: `
    @for (item of items; track item.id) {
      <ng-container *ngTemplateOutlet="itemTemplate; context: { $implicit: item }">
      </ng-container>
    }
  `
})
export class ListComponent {
  @Input() items: any[] = [];
  @Input() itemTemplate!: TemplateRef<any>;
}
```

### Template Reference Variables

```html
<!-- Reference to DOM element -->
<input #nameInput type="text">
<button (click)="greet(nameInput.value)">Greet</button>

<!-- Reference to directive instance -->
<form #myForm="ngForm" (ngSubmit)="onSubmit(myForm)">
  <input name="email" ngModel required #email="ngModel">
  @if (email.invalid && email.touched) {
    <span>Email required</span>
  }
</form>

<!-- Reference to component -->
<app-timer #timer></app-timer>
<button (click)="timer.start()">Start</button>
<button (click)="timer.stop()">Stop</button>
```

### Dynamic Component Loading

```typescript
@Component({
  template: `<ng-container #outlet></ng-container>`
})
export class DynamicHostComponent {
  @ViewChild('outlet', { read: ViewContainerRef }) outlet!: ViewContainerRef;

  loadComponent(component: Type<any>, inputs?: Record<string, any>): void {
    this.outlet.clear();
    const ref = this.outlet.createComponent(component);
    if (inputs) {
      Object.entries(inputs).forEach(([key, value]) => {
        ref.setInput(key, value);
      });
    }
  }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between ng-template and ng-container?**
> `ng-template` defines a template that isn't rendered until explicitly instantiated (via *ngIf else, ngTemplateOutlet, or ViewContainerRef). `ng-container` is rendered immediately but produces no DOM element — it's a logical grouping. Use ng-container to apply structural directives without adding wrapper elements.

**Q2: What is ngTemplateOutlet used for?**
> ngTemplateOutlet renders a template reference dynamically with a context. It enables configurable components — a parent can pass custom templates to a child (e.g., custom list item rendering). Context provides data to the template via `let-variable` syntax.

**Q3: How do you pass data to an ng-template?**
> Use context object: `*ngTemplateOutlet="template; context: { $implicit: data, name: 'John' }"`. In the template: `<ng-template let-item let-name="name">`. `$implicit` maps to the default `let-item` (no assignment needed). Named properties map to `let-x="propertyName"`.

---

## Related Topics

- → [5. Directives](./05-directives.md)
- → [9. Content Projection](./09-content-projection.md)
- → [10. View Queries](./10-view-queries.md)
- → [32. Dynamic Components](./32-dynamic-components.md)
