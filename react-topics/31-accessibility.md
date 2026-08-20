# Accessibility (a11y)

## Semantic HTML

```jsx
// ❌ Non-semantic (div soup)
<div onClick={handleClick}>Click me</div>
<div class="header">Title</div>

// ✅ Semantic
<button onClick={handleClick}>Click me</button>
<header><h1>Title</h1></header>
<nav>...</nav>
<main>...</main>
<article>...</article>
<footer>...</footer>
```

---

## ARIA Attributes

```jsx
// When HTML semantics aren't enough
<div role="alert" aria-live="polite">{errorMessage}</div>
<button aria-label="Close dialog" onClick={onClose}>×</button>
<input aria-describedby="email-help" aria-invalid={!!errors.email} />
<span id="email-help">We'll never share your email.</span>
<div role="tabpanel" aria-labelledby="tab-1">Content</div>

// Hide decorative elements from screen readers
<img src="decorative.svg" alt="" aria-hidden="true" />
```

---

## Keyboard Navigation

```jsx
// All interactive elements must be keyboard accessible
function Dialog({ isOpen, onClose, children }) {
  const dialogRef = useRef(null);

  // Trap focus inside modal
  useEffect(() => {
    if (isOpen) dialogRef.current?.focus();
  }, [isOpen]);

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') onClose();
  };

  return (
    <div 
      role="dialog" 
      aria-modal="true"
      ref={dialogRef}
      tabIndex={-1}
      onKeyDown={handleKeyDown}
    >
      {children}
      <button onClick={onClose}>Close</button>
    </div>
  );
}
```

---

## Accessible Forms

```jsx
function SignupForm() {
  return (
    <form aria-labelledby="form-title">
      <h2 id="form-title">Create Account</h2>
      
      <div>
        <label htmlFor="email">Email (required)</label>
        <input 
          id="email" 
          type="email" 
          required
          aria-required="true"
          aria-describedby="email-error"
          aria-invalid={!!errors.email}
        />
        {errors.email && (
          <span id="email-error" role="alert">{errors.email}</span>
        )}
      </div>
      
      <button type="submit">Sign Up</button>
    </form>
  );
}
```

---

## Focus Management

```jsx
// Return focus after modal closes
function useReturnFocus() {
  const triggerRef = useRef(null);
  
  const saveFocus = () => { triggerRef.current = document.activeElement; };
  const returnFocus = () => { triggerRef.current?.focus(); };
  
  return { saveFocus, returnFocus };
}

// Skip navigation link
<a href="#main-content" className="skip-link">Skip to main content</a>
<main id="main-content">...</main>
```

---

## Key Interview Questions

**Q: Why is semantic HTML important for accessibility?**
> Screen readers use HTML semantics to convey meaning. A `<button>` announces as "button" and is keyboard-focusable. A `<div onClick>` has no semantics - screen readers don't know it's interactive. Semantic HTML provides free accessibility.

**Q: What is ARIA and when should you use it?**
> ARIA (Accessible Rich Internet Applications) adds meaning to elements when HTML semantics aren't sufficient. Use it as a last resort - proper HTML elements are always preferred. Common use: custom widgets, live regions, relationships.

**Q: How do you test accessibility?**
> Automated: axe-core, eslint-plugin-jsx-a11y, Lighthouse. Manual: Keyboard navigation testing, screen reader testing (NVDA, VoiceOver), color contrast checks. Note: Automated tools catch ~30% of issues; manual testing is essential.
