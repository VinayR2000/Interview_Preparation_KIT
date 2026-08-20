# DOM and Browser Events

## DOM (Document Object Model)

### Selecting Elements
```javascript
document.getElementById('id');
document.querySelector('.class');           // First match
document.querySelectorAll('div.card');     // All matches (NodeList)
document.getElementsByClassName('class');   // Live HTMLCollection
document.getElementsByTagName('div');       // Live HTMLCollection
```

### Modifying Elements
```javascript
const el = document.querySelector('#app');

// Content
el.textContent = 'Hello';        // Text only (safe from XSS)
el.innerHTML = '<b>Hello</b>';   // Parses HTML (XSS risk!)

// Attributes
el.setAttribute('data-id', '5');
el.getAttribute('data-id');
el.removeAttribute('data-id');
el.dataset.id = '5';            // data-* attributes

// Classes
el.classList.add('active');
el.classList.remove('active');
el.classList.toggle('active');
el.classList.contains('active');

// Styles
el.style.backgroundColor = 'red';
el.style.display = 'none';
```

### Creating and Removing
```javascript
const div = document.createElement('div');
div.textContent = 'New element';
div.className = 'card';

document.body.appendChild(div);
document.body.removeChild(div);
parent.insertBefore(newEl, referenceEl);
el.remove();  // Remove self
```

---

## Events

### Event Listeners
```javascript
// Add
element.addEventListener('click', handler);
element.addEventListener('click', handler, { once: true });  // Fire once
element.addEventListener('click', handler, { passive: true }); // Performance

// Remove (must be same function reference)
element.removeEventListener('click', handler);
```

### Event Object
```javascript
function handler(event) {
  event.type;          // 'click'
  event.target;        // Element that triggered (could be child)
  event.currentTarget; // Element with the listener
  event.preventDefault();   // Stop default behavior
  event.stopPropagation();  // Stop bubbling
}
```

### Event Propagation
```
Capturing Phase:  Window → Document → html → body → div → button (top-down)
Target Phase:     Event reaches target element
Bubbling Phase:   button → div → body → html → Document → Window (bottom-up)
```

### Event Delegation
```javascript
// Instead of adding listener to each <li>, add one to <ul>
document.querySelector('ul').addEventListener('click', (e) => {
  if (e.target.tagName === 'LI') {
    console.log('Clicked item:', e.target.textContent);
  }
});
// Benefits: Works for dynamically added items, fewer listeners, better performance
```

---

## Storage APIs

```javascript
// localStorage - Persists until cleared
localStorage.setItem('key', JSON.stringify(data));
const data = JSON.parse(localStorage.getItem('key'));
localStorage.removeItem('key');
localStorage.clear();

// sessionStorage - Cleared when tab closes
sessionStorage.setItem('key', 'value');

// Cookies
document.cookie = 'name=value; max-age=3600; path=/; Secure; SameSite=Strict';
```

---

## Key Interview Questions

**Q: What is event delegation?**
> Attaching a single event listener to a parent element instead of individual children. Works because events bubble up. Benefits: Handles dynamic elements, fewer listeners, better memory.

**Q: What's the difference between `target` and `currentTarget`?**
> `target` is the actual element clicked (could be a nested child). `currentTarget` is the element with the event listener attached. In delegation, `target` helps identify which child was clicked.

**Q: How to prevent default and stop propagation?**
> `event.preventDefault()` stops default behavior (form submit, link navigation). `event.stopPropagation()` stops the event from bubbling to parent elements.

**Q: localStorage vs sessionStorage vs cookies?**
> localStorage: ~5MB, persists forever, same-origin only. sessionStorage: ~5MB, cleared on tab close. Cookies: ~4KB, sent with every HTTP request, can have expiry, server-accessible.
