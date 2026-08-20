# Styling in React

## CSS Approaches Comparison

| Approach | Scoped | Runtime | Bundle | DX |
|----------|--------|---------|--------|-----|
| Plain CSS | No | No | Small | Simple |
| CSS Modules | Yes | No | Small | Good |
| Styled Components | Yes | Yes | Medium | Great |
| Tailwind CSS | Yes (utility) | No | Small | Fast |
| Inline Styles | Yes | No | In JS | Limited |

---

## CSS Modules

```jsx
// Button.module.css
.button { padding: 8px 16px; border-radius: 4px; }
.primary { background: blue; color: white; }
.secondary { background: gray; color: black; }

// Button.jsx
import styles from './Button.module.css';

function Button({ variant = 'primary', children }) {
  return (
    <button className={`${styles.button} ${styles[variant]}`}>
      {children}
    </button>
  );
}
```

---

## Styled Components (CSS-in-JS)

```jsx
import styled from 'styled-components';

const Button = styled.button`
  padding: 8px 16px;
  border-radius: 4px;
  background: ${props => props.variant === 'primary' ? 'blue' : 'gray'};
  color: white;
  
  &:hover { opacity: 0.8; }
  &:disabled { opacity: 0.5; }
`;

<Button variant="primary">Click me</Button>
```

---

## Tailwind CSS

```jsx
function Button({ variant = 'primary', children }) {
  const base = 'px-4 py-2 rounded font-medium transition-colors';
  const variants = {
    primary: 'bg-blue-600 text-white hover:bg-blue-700',
    secondary: 'bg-gray-200 text-gray-800 hover:bg-gray-300',
    danger: 'bg-red-600 text-white hover:bg-red-700',
  };

  return (
    <button className={`${base} ${variants[variant]}`}>
      {children}
    </button>
  );
}
```

---

## Responsive Design

```jsx
// Tailwind responsive prefixes
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
  {items.map(item => <Card key={item.id} {...item} />)}
</div>

// CSS Modules with media queries
.container { display: grid; grid-template-columns: 1fr; }
@media (min-width: 768px) { .container { grid-template-columns: 1fr 1fr; } }
```

---

## Key Interview Questions

**Q: CSS Modules vs Styled Components?**
> CSS Modules: No runtime cost, standard CSS, smaller bundle. Styled Components: Dynamic styles based on props, colocated with component, theming support, but adds runtime overhead.

**Q: Why is Tailwind popular?**
> Rapid development (no context switching to CSS files), consistent design system, tiny production bundle (unused classes purged), no naming problems, responsive utilities built-in.

**Q: How do you handle theming?**
> CSS variables (`:root { --primary: blue }`) + Context for theme switching. Tailwind: dark mode class strategy. Styled Components: ThemeProvider.
