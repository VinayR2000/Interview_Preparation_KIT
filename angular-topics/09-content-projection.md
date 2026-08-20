# 9. Content Projection

---

## Theory

Content projection allows a parent component to insert content into a child component's template. It's Angular's equivalent of "slots" in other frameworks. The child defines where projected content appears using `<ng-content>`.

### Types of Content Projection

| Type | Syntax | Use Case |
|------|--------|----------|
| Single-slot | `<ng-content>` | One projection area |
| Multi-slot | `<ng-content select="...">` | Multiple named areas |
| Conditional | `ngProjectAs` | Project with different selector |

### Single-Slot Projection

```typescript
// Card component (child)
@Component({
  selector: 'app-card',
  standalone: true,
  template: `
    <div class="card">
      <ng-content></ng-content>  <!-- Everything goes here -->
    </div>
  `,
  styles: [`.card { border: 1px solid #ddd; padding: 16px; border-radius: 8px; }`]
})
export class CardComponent {}

// Usage (parent)
// <app-card>
//   <h2>Title</h2>
//   <p>Any content here gets projected into the card</p>
//   <button>Action</button>
// </app-card>
```

### Multi-Slot Projection

```typescript
@Component({
  selector: 'app-layout-card',
  standalone: true,
  template: `
    <div class="card">
      <div class="card-header">
        <ng-content select="[card-header]"></ng-content>
      </div>
      <div class="card-body">
        <ng-content></ng-content>  <!-- Default slot (unmatched content) -->
      </div>
      <div class="card-footer">
        <ng-content select="[card-footer]"></ng-content>
      </div>
    </div>
  `
})
export class LayoutCardComponent {}

// Usage
// <app-layout-card>
//   <h3 card-header>User Profile</h3>     ← goes to select="[card-header]"
//   <p>This is the body content</p>        ← goes to default <ng-content>
//   <img src="avatar.png">                 ← also goes to default
//   <button card-footer>Save</button>      ← goes to select="[card-footer]"
// </app-layout-card>
```

### Select Attribute — Matching Rules

```html
<!-- By attribute -->
<ng-content select="[card-header]"></ng-content>
<!-- Matches: <div card-header>...</div> -->

<!-- By CSS class -->
<ng-content select=".header-content"></ng-content>
<!-- Matches: <div class="header-content">...</div> -->

<!-- By element -->
<ng-content select="h2"></ng-content>
<!-- Matches: <h2>...</h2> -->

<!-- By component selector -->
<ng-content select="app-icon"></ng-content>
<!-- Matches: <app-icon name="user"></app-icon> -->

<!-- By ngProjectAs (override) -->
<ng-content select="[card-header]"></ng-content>
<!-- Matches: <ng-container ngProjectAs="[card-header]">...</ng-container> -->
```

### ngProjectAs

```html
<!-- Problem: you want to project a ng-container but it has no element to add attribute to -->
<!-- Solution: ngProjectAs tells Angular to treat it as if it matches the selector -->

<app-layout-card>
  <ng-container ngProjectAs="[card-header]">
    <h3>Dynamic Title</h3>
    <span class="badge">New</span>
  </ng-container>
  
  <p>Body content</p>
</app-layout-card>
```

### ContentChild and ContentChildren

```typescript
@Component({
  selector: 'app-tab-group',
  standalone: true,
  template: `
    <div class="tabs">
      @for (tab of tabs; track tab.label) {
        <button [class.active]="tab === activeTab" (click)="selectTab(tab)">
          {{ tab.label }}
        </button>
      }
    </div>
    <div class="tab-content">
      <ng-content></ng-content>
    </div>
  `
})
export class TabGroupComponent implements AfterContentInit {
  @ContentChildren(TabComponent) tabList!: QueryList<TabComponent>;
  
  tabs: TabComponent[] = [];
  activeTab: TabComponent | null = null;

  ngAfterContentInit(): void {
    this.tabs = this.tabList.toArray();
    this.activeTab = this.tabs[0] || null;
    this.selectTab(this.activeTab);
    
    // React to dynamic tab additions/removals
    this.tabList.changes.subscribe(() => {
      this.tabs = this.tabList.toArray();
    });
  }

  selectTab(tab: TabComponent | null): void {
    this.tabs.forEach(t => t.isActive = false);
    if (tab) {
      tab.isActive = true;
      this.activeTab = tab;
    }
  }
}

@Component({
  selector: 'app-tab',
  standalone: true,
  template: `
    <div [hidden]="!isActive">
      <ng-content></ng-content>
    </div>
  `
})
export class TabComponent {
  @Input({ required: true }) label!: string;
  isActive = false;
}

// Usage:
// <app-tab-group>
//   <app-tab label="Profile">Profile content</app-tab>
//   <app-tab label="Settings">Settings content</app-tab>
//   <app-tab label="Security">Security content</app-tab>
// </app-tab-group>
```

### @ContentChild — Single Projected Element

```typescript
@Component({
  selector: 'app-dropdown',
  standalone: true,
  template: `
    <div class="dropdown" (click)="toggle()">
      <ng-content select="[dropdown-trigger]"></ng-content>
      @if (isOpen) {
        <div class="dropdown-menu">
          <ng-content select="[dropdown-menu]"></ng-content>
        </div>
      }
    </div>
  `
})
export class DropdownComponent implements AfterContentInit {
  @ContentChild('triggerElement') trigger!: ElementRef;
  
  isOpen = false;

  ngAfterContentInit(): void {
    // Access projected content element
    console.log('Trigger element:', this.trigger);
  }

  toggle(): void {
    this.isOpen = !this.isOpen;
  }
}
```

---

## Internal Working

### How Content Projection Works

```
Parent Template:
<app-card>
  <h2>Title</h2>
  <p>Content</p>
</app-card>

Compilation:
1. Angular compiles parent template
2. Content between <app-card>...</app-card> tags is noted
3. When CardComponent renders, Angular looks for <ng-content>
4. Projected content is inserted at <ng-content> position
5. The projected content belongs to the PARENT's view (not child's)
   → Parent's change detection handles the bindings
   → Parent's context is available (not child's)

Key: Content is PROJECTED, not MOVED
  - It's created in parent's view
  - Inserted into child's DOM position
  - Change detection for projected content runs with PARENT
```

### Multi-Slot Matching

```
Content: <h2 card-header>Title</h2> <p>Body</p> <button card-footer>Save</button>

Angular processes:
1. <h2 card-header> → matches select="[card-header]" → slot 1
2. <p>Body</p> → matches NO selector → default slot
3. <button card-footer> → matches select="[card-footer]" → slot 3

DOM Result:
<app-layout-card>
  <div class="card">
    <div class="card-header">
      <h2 card-header>Title</h2>           ← slot 1
    </div>
    <div class="card-body">
      <p>Body</p>                           ← default slot
    </div>
    <div class="card-footer">
      <button card-footer>Save</button>     ← slot 3
    </div>
  </div>
</app-layout-card>
```

### ContentChild vs ViewChild

```
┌─────────────────────────────────────────────────┐
│ Parent Component Template                        │
│                                                  │
│  <app-card>                                      │
│    <h2 #projectedTitle>Title</h2>  ← ContentChild of app-card │
│  </app-card>                                     │
│                                                  │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ Card Component Template                          │
│                                                  │
│  <div class="card">                              │
│    <ng-content></ng-content>                     │
│    <span #internalRef>Footer</span> ← ViewChild of app-card │
│  </div>                                          │
│                                                  │
└─────────────────────────────────────────────────┘

ContentChild = references to projected content (from parent)
ViewChild = references to own template elements
```

---

## Diagram

```
Content Projection Flow:
┌──────────────────────────────────────────────────────────┐
│ Parent Component                                          │
│                                                           │
│  <app-card>                                               │
│    ┌──────────────────────────────────────┐               │
│    │ <h2>Title</h2>                       │ ← Content     │
│    │ <p>Body</p>                          │    to project │
│    │ <button>Action</button>              │               │
│    └──────────────────────────────────────┘               │
│  </app-card>                                              │
│                                                           │
└──────────────────────────────────────────────────────────┘
                          │ Projection
                          ▼
┌──────────────────────────────────────────────────────────┐
│ Card Component Template                                   │
│                                                           │
│  <div class="card">                                       │
│    ┌──────────────────────────────────────┐               │
│    │ <ng-content>                         │               │
│    │   ← <h2>Title</h2>                  │ ← Projected   │
│    │   ← <p>Body</p>                     │    here       │
│    │   ← <button>Action</button>         │               │
│    │ </ng-content>                        │               │
│    └──────────────────────────────────────┘               │
│  </div>                                                   │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

## Code

```typescript
// Modal/Dialog component with content projection
@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (isOpen) {
      <div class="overlay" (click)="close()">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <ng-content select="[modal-title]"></ng-content>
            <button class="close-btn" (click)="close()">×</button>
          </div>
          <div class="modal-body">
            <ng-content></ng-content>
          </div>
          <div class="modal-footer">
            <ng-content select="[modal-actions]"></ng-content>
          </div>
        </div>
      </div>
    }
  `
})
export class ModalComponent {
  @Input() isOpen = false;
  @Output() isOpenChange = new EventEmitter<boolean>();

  close(): void {
    this.isOpen = false;
    this.isOpenChange.emit(false);
  }
}

// Usage:
// <app-modal [(isOpen)]="showModal">
//   <h2 modal-title>Confirm Delete</h2>
//   <p>Are you sure you want to delete this user?</p>
//   <div modal-actions>
//     <button (click)="showModal = false">Cancel</button>
//     <button (click)="confirmDelete()">Delete</button>
//   </div>
// </app-modal>

// Alert component with conditional projection
@Component({
  selector: 'app-alert',
  standalone: true,
  template: `
    <div class="alert" [class]="'alert-' + type">
      <div class="alert-icon">
        <ng-content select="[alert-icon]"></ng-content>
      </div>
      <div class="alert-content">
        <ng-content></ng-content>
      </div>
      <div class="alert-action">
        <ng-content select="[alert-action]"></ng-content>
      </div>
    </div>
  `
})
export class AlertComponent {
  @Input() type: 'success' | 'error' | 'warning' | 'info' = 'info';
}

// Accordion component using ContentChildren
@Component({
  selector: 'app-accordion',
  standalone: true,
  template: `<div class="accordion"><ng-content></ng-content></div>`
})
export class AccordionComponent implements AfterContentInit {
  @ContentChildren(AccordionItemComponent) items!: QueryList<AccordionItemComponent>;

  ngAfterContentInit(): void {
    this.items.forEach(item => {
      item.toggle.subscribe(() => this.closeOthers(item));
    });
  }

  private closeOthers(openItem: AccordionItemComponent): void {
    this.items.forEach(item => {
      if (item !== openItem) item.isOpen = false;
    });
  }
}

@Component({
  selector: 'app-accordion-item',
  standalone: true,
  template: `
    <div class="accordion-item">
      <div class="header" (click)="onToggle()">
        <ng-content select="[accordion-header]"></ng-content>
        <span>{{ isOpen ? '▲' : '▼' }}</span>
      </div>
      @if (isOpen) {
        <div class="body">
          <ng-content></ng-content>
        </div>
      }
    </div>
  `
})
export class AccordionItemComponent {
  @Output() toggle = new EventEmitter<void>();
  isOpen = false;

  onToggle(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) this.toggle.emit();
  }
}
```

---

## Dry Run

### Multi-Slot Projection

```
Parent template:
<app-layout-card>
  <h3 card-header>Order #1234</h3>
  <span card-header class="badge">Pending</span>
  <p>Order details go here</p>
  <p>Total: $99.99</p>
  <button card-footer (click)="approve()">Approve</button>
  <button card-footer (click)="reject()">Reject</button>
</app-layout-card>

Matching:
Step 1: <h3 card-header> → matches select="[card-header]" → header slot
Step 2: <span card-header> → matches select="[card-header]" → header slot
Step 3: <p>Order details</p> → no match → default slot
Step 4: <p>Total: $99.99</p> → no match → default slot
Step 5: <button card-footer> → matches select="[card-footer]" → footer slot
Step 6: <button card-footer> → matches select="[card-footer]" → footer slot

Rendered DOM:
<div class="card">
  <div class="card-header">
    <h3>Order #1234</h3>
    <span class="badge">Pending</span>
  </div>
  <div class="card-body">
    <p>Order details go here</p>
    <p>Total: $99.99</p>
  </div>
  <div class="card-footer">
    <button>Approve</button>
    <button>Reject</button>
  </div>
</div>
```

---

## Complexity

| Operation | Performance | Notes |
|-----------|-------------|-------|
| Single-slot projection | O(1) | Direct insertion |
| Multi-slot matching | O(n) | n = number of projected elements |
| ContentChildren query | O(k) | k = matched children |
| QueryList.changes | Event-driven | Only on additions/removals |

---

## Real Project Usage

```typescript
// Reusable data table with projection
@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <table>
      <thead>
        <ng-content select="[table-header]"></ng-content>
      </thead>
      <tbody>
        <ng-content></ng-content>
      </tbody>
      <tfoot>
        <ng-content select="[table-footer]"></ng-content>
      </tfoot>
    </table>
    <div class="pagination">
      <ng-content select="[table-pagination]"></ng-content>
    </div>
  `
})
export class DataTableComponent {}

// Usage:
// <app-data-table>
//   <tr table-header>
//     <th>Name</th><th>Email</th><th>Actions</th>
//   </tr>
//   @for (user of users; track user.id) {
//     <tr><td>{{user.name}}</td><td>{{user.email}}</td><td>...</td></tr>
//   }
//   <tr table-footer><td colspan="3">Total: {{users.length}}</td></tr>
//   <app-pagination table-pagination [total]="total" (pageChange)="load($event)" />
// </app-data-table>
```

---

## Interview Questions and Answers

**Q1: What is content projection in Angular?**
> Content projection lets a parent component pass content (HTML, components, elements) to a child component, which renders it in a designated area using `<ng-content>`. It's used to create reusable, flexible wrapper components like cards, modals, tabs, and layouts.

**Q2: What is the difference between single-slot and multi-slot projection?**
> Single-slot uses one `<ng-content>` — all projected content goes there. Multi-slot uses multiple `<ng-content select="...">` tags with CSS-like selectors to route different content to different slots. Unmatched content goes to the default (no select) `<ng-content>`.

**Q3: What is the difference between ContentChild and ViewChild?**
> `@ContentChild` queries projected content (passed from parent via `<ng-content>`). Available in `ngAfterContentInit`. `@ViewChild` queries elements in the component's own template. Available in `ngAfterViewInit`. They correspond to different parts of the component's view.

**Q4: When is ngAfterContentInit called?**
> After Angular projects external content into the component's `<ng-content>` and initializes it. This is when `@ContentChild` and `@ContentChildren` become available. It's called once after the first `ngDoCheck`.

**Q5: What is ngProjectAs used for?**
> `ngProjectAs` overrides how Angular matches content to `<ng-content select="...">` slots. It's useful when you need to project `<ng-container>` (which has no DOM element to put attributes on) into a specific named slot, or when you want to match content that doesn't naturally have the required selector.

---

## Follow-up Questions and Answers

**Q: Can you conditionally project content?**
> `<ng-content>` itself doesn't support conditions. However, you can wrap it in `@if` to show/hide the slot, or use `ngTemplateOutlet` for more dynamic projection. Note that projected content is always created (even if hidden) — it's in the parent's view.

**Q: What happens if there's no content to project?**
> The `<ng-content>` area simply renders nothing. You can provide fallback content by placing it inside `<ng-content>` in newer Angular versions, or by using `@ContentChild` to check if content exists and show a default.

---

## Common Mistakes

1. **Expecting child's context in projected content**
   ```html
   <!-- ❌ 'childProperty' belongs to child — not accessible in projected content -->
   <app-card>
     <p>{{ childProperty }}</p>  <!-- This uses PARENT's context -->
   </app-card>
   
   <!-- ✅ Projected content uses parent's context -->
   <app-card>
     <p>{{ parentProperty }}</p>
   </app-card>
   ```

2. **Multiple default ng-content slots**
   ```html
   <!-- ❌ Only FIRST default ng-content receives content -->
   <ng-content></ng-content>
   <ng-content></ng-content>  <!-- Always empty! -->
   
   <!-- ✅ Use named slots for multiple areas -->
   <ng-content select="[header]"></ng-content>
   <ng-content></ng-content>
   ```

3. **Forgetting that projected content is created even when hidden**
   ```html
   <!-- ⚠️ Content is ALWAYS created, even if wrapper is hidden -->
   @if (showDetails) {
     <ng-content select="[details]"></ng-content>
   }
   <!-- The projected <app-expensive> component is created regardless -->
   ```

---

## Best Practices

1. **Use multi-slot projection** for layout components (cards, modals, pages).
2. **Always include a default slot** for unmatched content.
3. **Use meaningful selector names** (`[card-header]`, `[modal-actions]`).
4. **Document available slots** for component consumers.
5. **Use `ngProjectAs`** when projecting `<ng-container>` groups.
6. **Prefer projection over @Input** for complex template content.
7. **Use `@ContentChildren`** for dynamic lists of projected components.

---

## Production Considerations

- **Projected content is created in parent's view** — change detection for bindings in projected content runs with the parent, not the child.
- **Memory**: Content is always instantiated even if conditionally hidden. For expensive content, consider `ngTemplateOutlet` instead.
- **Encapsulation**: Child's styles don't apply to projected content by default (it belongs to parent's view encapsulation scope).

---

## Related Topics

- → [3. Components](./03-components.md)
- → [8. Component Communication](./08-component-communication.md)
- → [10. View Queries](./10-view-queries.md)
- → [31. Templates Advanced](./31-templates-advanced.md)
- → [32. Dynamic Components](./32-dynamic-components.md)
