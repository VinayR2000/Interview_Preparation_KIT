# JavaScript Security

## XSS (Cross-Site Scripting)

### What is XSS?
Attacker injects malicious scripts into web pages viewed by other users.

### Types
| Type | Description | Example |
|------|-------------|---------|
| Stored | Script saved in database, served to users | Comment with `<script>` tag |
| Reflected | Script in URL, reflected in response | Search query echoed in page |
| DOM-based | Script manipulates DOM directly | `innerHTML = userInput` |

### Prevention
```javascript
// ❌ Dangerous
element.innerHTML = userInput;
document.write(userInput);

// ✅ Safe
element.textContent = userInput;  // Escapes HTML
// Use DOMPurify for rich content
import DOMPurify from 'dompurify';
element.innerHTML = DOMPurify.sanitize(userInput);
```

---

## CSRF (Cross-Site Request Forgery)

### What is CSRF?
Attacker tricks user's browser into making unwanted requests to a site where they're authenticated.

### Prevention
- **CSRF tokens** in forms (server validates)
- **SameSite cookies** (`SameSite=Strict` or `Lax`)
- Check `Origin`/`Referer` headers

---

## CORS (Cross-Origin Resource Sharing)

```
Same Origin = Same Protocol + Domain + Port
http://example.com:80 vs https://example.com:443 → Different origin
```

### How it Works
1. Browser sends preflight OPTIONS request (for non-simple requests)
2. Server responds with allowed origins
3. If allowed, browser sends actual request

### Server Headers
```
Access-Control-Allow-Origin: https://your-app.com
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Content-Type, Authorization
Access-Control-Allow-Credentials: true
```

---

## Cookie Security

| Attribute | Purpose |
|-----------|---------|
| `HttpOnly` | Can't be accessed by JavaScript (prevents XSS stealing cookies) |
| `Secure` | Only sent over HTTPS |
| `SameSite=Strict` | Never sent cross-site (CSRF protection) |
| `SameSite=Lax` | Sent on top-level navigations only |
| `Domain` | Which domains receive the cookie |
| `Path` | Which paths receive the cookie |
| `Max-Age` / `Expires` | When cookie expires |

```javascript
// Secure cookie settings
document.cookie = 'token=abc; Secure; HttpOnly; SameSite=Strict; Path=/';
```

---

## Content Security Policy (CSP)

```html
<!-- Only allow scripts from own domain -->
<meta http-equiv="Content-Security-Policy" 
      content="default-src 'self'; script-src 'self'; style-src 'self'">
```
- Blocks inline scripts (prevents most XSS)
- Restricts which domains can serve resources

---

## Key Interview Questions

**Q: How do you prevent XSS?**
> 1) Never use `innerHTML` with user data. 2) Use `textContent` for text. 3) Sanitize with DOMPurify for rich content. 4) Set CSP headers. 5) HttpOnly cookies (script can't steal them).

**Q: What is the difference between CORS and same-origin policy?**
> Same-origin policy is the browser restriction that blocks cross-origin requests. CORS is the mechanism that allows servers to explicitly permit specific cross-origin access via headers.

**Q: Why use HttpOnly cookies over localStorage for tokens?**
> HttpOnly cookies are invisible to JavaScript — an XSS attack can't read them. localStorage is fully accessible to any JavaScript running on the page, making tokens vulnerable to XSS.
