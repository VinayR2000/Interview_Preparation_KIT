# 46. Accessibility (a11y)

---

## Theory

Accessibility ensures your Angular application is usable by everyone, including people with disabilities who use screen readers, keyboard navigation, or other assistive technologies.

### Key Principles (WCAG)

| Principle | Meaning | Example |
|-----------|---------|---------|
| Perceivable | Content available to senses | Alt text, captions, contrast |
| Operable | Interface navigable | Keyboard access, focus management |
| Understandable | Content readable | Clear labels, consistent navigation |
| Robust | Works with assistive tech | Semantic HTML, ARIA roles |

### Semantic HTML

```html
<!-- ❌ Divs everywhere — screen readers can't understand structure -->
<div class="nav"><div class="link" (click)="go()">Home</div></div>
<div class="header">Page Title</div>

<!-- ✅ Semantic elements — screen readers understand layout -->
<nav><a routerLink="/home">Home</a></nav>
<h1>Page Title</h1>
<main>
  <article>...</article>
  <aside>...</aside>
</main>
<footer>...</footer>
```

### ARIA Attributes

```html
<!-- Role for custom widgets -->
<div role="tablist">
  <button role="tab" [attr.aria-selected]="activeTab === 'profile'" (click)="selectTab('profile')">
    Profile
  </button>
</div>
<div role="tabpanel" [attr.aria-hidden]="activeTab !== 'profile'">
  Tab content
</div>

<!-- Live regions — announce dynamic changes -->
<div aria-live="polite" aria-atomic="true">
  {{ notification }}  <!-- Screen reader announces when this changes -->
</div>

<!-- Labels -->
<input [attr.aria-label]="'Search users'" type="text">
<input [attr.aria-labelledby]="'nameLabel'" type="text">
<label id="nameLabel">Full Name</label>

<!-- Expanded state -->
<button [attr.aria-expanded]="isMenuOpen" (click)="toggleMenu()">Menu</button>
```

### Keyboard Navigation

```typescript
@Component({
  template: `
    <div class="dropdown" 
         (keydown.escape)="close()"
         (keydown.arrowDown)="focusNext()"
         (keydown.arrowUp)="focusPrev()"
         (keydown.enter)="select()">
      <button (click)="toggle()" [attr.aria-expanded]="isOpen">
        {{ selected || 'Select...' }}
      </button>
      @if (isOpen) {
        <ul role="listbox">
          @for (option of options; track option; let i = $index) {
            <li role="option" 
                [class.focused]="focusIndex === i"
                [attr.aria-selected]="selected === option"
                (click)="selectOption(option)">
              {{ option }}
            </li>
          }
        </ul>
      }
    </div>
  `
})
export class AccessibleDropdownComponent { ... }
```

### Focus Management

```typescript
@Component({ ... })
export class ModalComponent implements AfterViewInit {
  @ViewChild('closeButton') closeButton!: ElementRef;

  ngAfterViewInit(): void {
    // Trap focus in modal
    this.closeButton.nativeElement.focus();
  }

  // Return focus to trigger element on close
  close(): void {
    this.triggerElement?.focus();
    this.isOpen = false;
  }
}
```

### Angular CDK a11y Module

```typescript
import { A11yModule } from '@angular/cdk/a11y';

// FocusTrap — keeps focus within a container (modals)
<div cdkTrapFocus>
  <input placeholder="First field">
  <button>Close</button>
</div>

// LiveAnnouncer — announce messages to screen readers
private announcer = inject(LiveAnnouncer);
this.announcer.announce('Item deleted successfully', 'polite');
```

---

## Interview Questions and Answers

**Q1: How do you make Angular applications accessible?**
> Use semantic HTML (nav, main, h1-h6, button). Add ARIA attributes for custom widgets (role, aria-label, aria-expanded). Ensure keyboard navigation (all interactive elements focusable, logical tab order). Manage focus in modals/dialogs. Use Angular CDK a11y module (FocusTrap, LiveAnnouncer). Test with screen readers.

**Q2: What is ARIA and when do you use it?**
> ARIA (Accessible Rich Internet Applications) adds meaning to elements for assistive technologies. Use when native HTML semantics aren't sufficient — custom dropdowns, tabs, modals, live notifications. First rule: if a native HTML element works (button, select, nav), use it instead of ARIA on a div.

**Q3: How do you handle focus management in SPAs?**
> After route navigation, move focus to the main content or page heading (screen readers otherwise lose context). In modals: trap focus inside, return focus to trigger on close. Use Angular CDK's `FocusTrap` directive. Manage skip links for keyboard users.

---

## Best Practices

1. **Use native HTML elements** before adding ARIA.
2. **All interactive elements** must be keyboard accessible.
3. **Color contrast** minimum 4.5:1 for normal text.
4. **Form labels** — every input needs an associated label.
5. **Focus indicators** — never remove outline without replacement.
6. **Live regions** for dynamic content updates (notifications, loading).
7. **Test with screen readers** (NVDA, VoiceOver) and keyboard only.
8. **Use Angular CDK a11y** for focus trapping and announcements.

---

## Related Topics

- → [16. Forms](./16-forms.md)
- → [25. Angular Performance](./25-angular-performance.md)
