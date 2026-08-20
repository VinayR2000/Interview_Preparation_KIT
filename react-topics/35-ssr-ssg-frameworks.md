# SSR, SSG, and Frameworks

## Rendering Strategies

| Strategy | Acronym | When HTML Generated | Use Case |
|----------|---------|-------------------|----------|
| Client-Side Rendering | CSR | In browser (JavaScript) | SPAs, dashboards |
| Server-Side Rendering | SSR | On each request (server) | Dynamic content, SEO |
| Static Site Generation | SSG | At build time | Blogs, docs, marketing |
| Incremental Static Regen | ISR | Build time + revalidate | E-commerce, news |

---

## CSR (Client-Side Rendering)

```
Browser requests → Server sends empty HTML + JS bundle
→ Browser downloads JS → JS renders the page
```

**Pros**: Rich interactivity, fast subsequent navigation
**Cons**: Slow initial load, poor SEO (empty HTML), requires JavaScript

---

## SSR (Server-Side Rendering)

```
Browser requests → Server fetches data → Server renders full HTML
→ Browser receives complete HTML (visible immediately)
→ JS bundle loads → Hydration (adds interactivity)
```

**Pros**: Fast initial load, good SEO, works without JS initially
**Cons**: Server load on each request, TTFB can be slow

---

## SSG (Static Site Generation)

```
Build time: Fetch data → Generate HTML files → Deploy to CDN
Runtime: Browser requests → CDN serves pre-built HTML (instant!)
```

**Pros**: Fastest (served from CDN), no server needed, best SEO
**Cons**: Stale data (until rebuild), long build for many pages

---

## ISR (Incremental Static Regeneration)

```
First request: Serve stale static page → Revalidate in background
Next request: Serve fresh regenerated page
```

**Pros**: Static speed + fresh data, no full rebuild needed
**Cons**: First visitor after revalidation sees stale content

---

## Hydration

### What is Hydration?
- Process of attaching JavaScript event handlers to server-rendered HTML
- Makes static HTML interactive
- React "takes over" the DOM that was rendered on the server

```
1. Server sends fully rendered HTML (visible, not interactive)
2. Browser downloads JS bundle
3. React hydrates: Attaches event handlers, state, effects
4. Page becomes fully interactive
```

### Hydration Mismatch
- Server HTML must match what client would render
- Mismatches cause warnings and potential bugs
- Common cause: Using `Date.now()`, `Math.random()`, browser-only APIs

---

## Next.js

### App Router (Modern)
```
app/
  layout.tsx          # Root layout (Server Component)
  page.tsx            # Home page
  about/
    page.tsx          # /about
  users/
    page.tsx          # /users
    [id]/
      page.tsx        # /users/:id (dynamic)
  api/
    users/
      route.ts        # API endpoint
```

### Server Components (Default)
```tsx
// app/users/page.tsx - Server Component (default)
async function UsersPage() {
  const users = await db.user.findMany();  // Direct DB access!
  return <UserList users={users} />;
}
```

### Client Components
```tsx
'use client';  // Opt-in

import { useState } from 'react';

function SearchBar() {
  const [query, setQuery] = useState('');
  return <input value={query} onChange={e => setQuery(e.target.value)} />;
}
```

### Server Actions
```tsx
// Server-side function callable from client
async function createUser(formData: FormData) {
  'use server';
  const name = formData.get('name');
  await db.user.create({ data: { name } });
  revalidatePath('/users');
}

function CreateUserForm() {
  return (
    <form action={createUser}>
      <input name="name" />
      <button type="submit">Create</button>
    </form>
  );
}
```

---

## When to Use What

| Scenario | Strategy |
|----------|----------|
| Blog, documentation | SSG |
| E-commerce product pages | ISR |
| Social media feed | SSR |
| Admin dashboard | CSR |
| Marketing landing page | SSG |
| Real-time chat | CSR + WebSocket |
| News articles | ISR or SSR |

---

## Key Interview Questions

**Q: What's the difference between SSR and SSG?**
> SSR generates HTML on every request (dynamic, always fresh). SSG generates HTML at build time (static, fastest, may be stale). SSR = on-demand, SSG = pre-built.

**Q: What is hydration and why can it fail?**
> Hydration attaches JS interactivity to server-rendered HTML. It fails when server HTML doesn't match client render (mismatch). Common causes: Date/time, browser-only APIs (window), random values.

**Q: Why use Next.js over plain React?**
> Next.js provides: SSR/SSG/ISR out of the box, file-based routing, Server Components, API routes, built-in optimization (images, fonts), zero-config TypeScript. Plain React only gives you CSR.

**Q: What are Server Components good for?**
> Data fetching (direct DB/API access), reducing client bundle (heavy dependencies stay on server), sensitive logic (API keys, queries). They never send JavaScript to the client.
