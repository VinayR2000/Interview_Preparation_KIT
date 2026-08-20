# 33. Angular Animations

---

## Theory

Angular's animation system builds on Web Animations API, providing declarative animations tied to component state changes.

### Setup

```typescript
// app.config.ts
import { provideAnimations } from '@angular/platform-browser/animations';

export const appConfig: ApplicationConfig = {
  providers: [provideAnimations()]
};
```

### Basic Animation

```typescript
import { trigger, state, style, transition, animate } from '@angular/animations';

@Component({
  selector: 'app-toggle',
  standalone: true,
  animations: [
    trigger('openClose', [
      state('open', style({ height: '200px', opacity: 1 })),
      state('closed', style({ height: '0px', opacity: 0 })),
      transition('open => closed', animate('300ms ease-in')),
      transition('closed => open', animate('300ms ease-out'))
    ])
  ],
  template: `
    <button (click)="toggle()">Toggle</button>
    <div [@openClose]="isOpen ? 'open' : 'closed'" class="box">
      Content here
    </div>
  `
})
export class ToggleComponent {
  isOpen = true;
  toggle(): void { this.isOpen = !this.isOpen; }
}
```

### Enter/Leave Animations

```typescript
animations: [
  trigger('fadeInOut', [
    transition(':enter', [  // void => *
      style({ opacity: 0, transform: 'translateY(-20px)' }),
      animate('300ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
    ]),
    transition(':leave', [  // * => void
      animate('200ms ease-in', style({ opacity: 0, transform: 'translateY(20px)' }))
    ])
  ])
]

// Usage
// @for (item of items; track item.id) {
//   <div @fadeInOut>{{ item.name }}</div>
// }
```

### List Animation (Stagger)

```typescript
import { trigger, transition, style, animate, query, stagger } from '@angular/animations';

animations: [
  trigger('listAnimation', [
    transition('* => *', [
      query(':enter', [
        style({ opacity: 0, transform: 'translateX(-20px)' }),
        stagger(50, [
          animate('300ms ease-out', style({ opacity: 1, transform: 'translateX(0)' }))
        ])
      ], { optional: true })
    ])
  ])
]

// Template: <div [@listAnimation]="items.length">
```

---

## Interview Questions and Answers

**Q1: How do Angular animations work?**
> Angular animations use the `@angular/animations` module built on Web Animations API. You define triggers (named animations), states (style snapshots), and transitions (between states with timing). Bind triggers in templates with `[@triggerName]`. `:enter` and `:leave` handle element insertion/removal.

**Q2: What are :enter and :leave in animations?**
> `:enter` (alias for `void => *`) triggers when an element is added to the DOM (e.g., *ngIf becomes true, @for adds item). `:leave` (alias for `* => void`) triggers when removed. Use for fade-in, slide-in effects on dynamic content.

---

## Best Practices

1. **Use CSS transitions** for simple hover/focus effects (no Angular overhead).
2. **Use Angular animations** for state-driven, enter/leave, or complex sequences.
3. **Use `{ optional: true }`** in query() to prevent errors when no elements match.
4. **Minimize animation complexity** — complex animations impact performance.
5. **Use `will-change` CSS property** for frequently animated elements.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [25. Angular Performance](./25-angular-performance.md)
