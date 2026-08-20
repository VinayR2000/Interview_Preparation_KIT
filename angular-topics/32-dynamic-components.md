# 32. Dynamic Components

---

## Theory

Dynamic components are created programmatically at runtime rather than declared in templates. Useful for modals, tabs, plugin systems, and configurable UIs.

### Creating Dynamic Components (Modern)

```typescript
@Component({
  selector: 'app-dynamic-host',
  standalone: true,
  template: `<ng-container #container></ng-container>`
})
export class DynamicHostComponent {
  @ViewChild('container', { read: ViewContainerRef }) container!: ViewContainerRef;

  loadComponent<T>(component: Type<T>, inputs?: Record<string, any>): ComponentRef<T> {
    this.container.clear();
    const ref = this.container.createComponent(component);
    
    // Set inputs dynamically
    if (inputs) {
      Object.entries(inputs).forEach(([key, value]) => {
        ref.setInput(key, value);
      });
    }
    
    return ref;
  }

  clearComponent(): void {
    this.container.clear();
  }
}
```

### Real-World: Dynamic Form Fields

```typescript
interface FormFieldConfig {
  type: 'text' | 'select' | 'checkbox' | 'date';
  label: string;
  name: string;
  options?: string[];
}

@Component({
  selector: 'app-dynamic-form',
  template: `
    @for (field of fields; track field.name) {
      <ng-container #fieldHost></ng-container>
    }
  `
})
export class DynamicFormComponent implements AfterViewInit {
  @Input() fields: FormFieldConfig[] = [];
  @ViewChildren('fieldHost', { read: ViewContainerRef }) hosts!: QueryList<ViewContainerRef>;

  private componentMap: Record<string, Type<any>> = {
    text: TextFieldComponent,
    select: SelectFieldComponent,
    checkbox: CheckboxFieldComponent,
    date: DateFieldComponent
  };

  ngAfterViewInit(): void {
    this.hosts.forEach((host, index) => {
      const field = this.fields[index];
      const component = this.componentMap[field.type];
      const ref = host.createComponent(component);
      ref.setInput('config', field);
    });
  }
}
```

### Dynamic Modal/Dialog

```typescript
@Injectable({ providedIn: 'root' })
export class ModalService {
  private viewContainerRef!: ViewContainerRef;

  setRootViewContainer(vcr: ViewContainerRef): void {
    this.viewContainerRef = vcr;
  }

  open<T>(component: Type<T>, inputs?: Record<string, any>): ComponentRef<T> {
    const ref = this.viewContainerRef.createComponent(component);
    if (inputs) {
      Object.entries(inputs).forEach(([key, value]) => ref.setInput(key, value));
    }
    return ref;
  }

  close(ref: ComponentRef<any>): void {
    ref.destroy();
  }
}
```

---

## Interview Questions and Answers

**Q1: How do you create components dynamically in Angular?**
> Inject `ViewContainerRef` (via @ViewChild with `{ read: ViewContainerRef }`), then call `viewContainerRef.createComponent(ComponentClass)`. Set inputs via `ref.setInput('name', value)`. Call `ref.destroy()` to remove. This is used for modals, dynamic forms, plugin systems, and tab content.

**Q2: What is ViewContainerRef?**
> ViewContainerRef represents a container in the DOM where you can dynamically insert views (components or templates). It's obtained via @ViewChild with `{ read: ViewContainerRef }`. Methods: `createComponent()`, `createEmbeddedView()`, `clear()`, `remove()`.

---

## Related Topics

- → [10. View Queries](./10-view-queries.md)
- → [31. Templates Advanced](./31-templates-advanced.md)
