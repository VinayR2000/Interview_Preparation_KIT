# 44. Angular SSR / Modern Rendering

---

## Theory

Server-Side Rendering (SSR) generates HTML on the server, improving initial load performance and SEO.

### Rendering Strategies

| Strategy | Where HTML is generated | When | Use Case |
|----------|------------------------|------|----------|
| CSR (Client-Side) | Browser | On every request | SPAs, dashboards |
| SSR (Server-Side) | Server | On every request | SEO, dynamic content |
| SSG (Pre-rendering) | Build time | At build | Static pages, blogs |
| ISR (Incremental) | Server + cache | On demand | E-commerce, news |

### Angular SSR Setup

```bash
ng add @angular/ssr
# Creates: server.ts, main.server.ts, app.config.server.ts
```

```typescript
// app.config.server.ts
import { mergeApplicationConfig } from '@angular/core';
import { provideServerRendering } from '@angular/platform-server';
import { appConfig } from './app.config';

const serverConfig: ApplicationConfig = {
  providers: [provideServerRendering()]
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
```

### Hydration

```
SSR Flow:
1. Browser requests page
2. Server renders Angular app to HTML string
3. Server sends complete HTML (user sees content immediately)
4. Browser downloads JavaScript bundles
5. Angular "hydrates" — attaches event listeners to existing DOM
6. App becomes interactive

Without hydration: Angular would destroy server-rendered DOM and re-create it
With hydration: Angular reuses existing DOM nodes (faster, no flicker)
```

### Platform Checks

```typescript
import { isPlatformBrowser, isPlatformServer } from '@angular/common';
import { PLATFORM_ID, inject, afterNextRender, afterRender } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class StorageService {
  private platformId = inject(PLATFORM_ID);

  getItem(key: string): string | null {
    if (isPlatformBrowser(this.platformId)) {
      return localStorage.getItem(key);
    }
    return null; // No localStorage on server
  }
}

// afterNextRender — runs once after first render (browser only)
@Component({ ... })
export class ChartComponent {
  constructor() {
    afterNextRender(() => {
      // This only runs in the browser, after DOM is ready
      this.initChart();
    });
  }
}

// afterRender — runs after every render (browser only)
@Component({ ... })
export class ScrollTrackerComponent {
  constructor() {
    afterRender(() => {
      // Runs after every render — useful for scroll sync
      this.syncScrollPosition();
    });
  }
}
```

### TransferState (Avoid Duplicate HTTP Calls)

```typescript
// Without TransferState:
// 1. Server fetches /api/users → renders HTML
// 2. Client hydrates → fetches /api/users AGAIN (duplicate!)

// With TransferState:
// 1. Server fetches /api/users → stores in TransferState → renders HTML
// 2. Client hydrates → reads from TransferState (no duplicate HTTP!)

import { TransferState, makeStateKey } from '@angular/core';

const USERS_KEY = makeStateKey<User[]>('users');

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private transferState = inject(TransferState);
  private platformId = inject(PLATFORM_ID);

  getUsers(): Observable<User[]> {
    // Check if data was already fetched on server
    const cachedData = this.transferState.get(USERS_KEY, null);
    if (cachedData) {
      this.transferState.remove(USERS_KEY); // Use once
      return of(cachedData);
    }

    return this.http.get<User[]>('/api/users').pipe(
      tap(users => {
        if (isPlatformServer(this.platformId)) {
          this.transferState.set(USERS_KEY, users); // Cache for client
        }
      })
    );
  }
}

// Angular 16+ with HttpClient: TransferState is AUTOMATIC with provideClientHydration()
// Just add: provideClientHydration() in app.config.ts — no manual TransferState needed!
```

### Incremental Hydration (Angular 18+)

```html
<!-- Only hydrate this section when user interacts with it -->
@defer (hydrate on interaction) {
  <app-comments [postId]="post.id" />
}

<!-- Hydrate when visible in viewport -->
@defer (hydrate on viewport) {
  <app-product-recommendations />
}

<!-- Never hydrate (stays static HTML from server) -->
@defer (hydrate never) {
  <app-static-footer />
}
```

---

## Internal Working

### SSR Request Flow

```
Browser requests https://myapp.com/products/42:

1. Request hits Node.js server (Express/Fastify)
2. Angular SSR engine:
   a. Creates Angular app instance (server platform)
   b. Navigates to /products/42
   c. Route resolver fetches product data from API
   d. Component renders with data
   e. Serializes component tree to HTML string
   f. Embeds TransferState as <script> in HTML
3. Server sends complete HTML to browser
4. Browser renders HTML immediately (user sees content)
5. Browser downloads JS bundles
6. Angular hydrates:
   a. Finds existing DOM nodes
   b. Attaches event listeners
   c. Reads TransferState (skips duplicate fetches)
   d. App becomes fully interactive

Total flow: ~200ms server render + ~500ms hydration = content visible in 200ms
vs CSR: ~1500ms (download + parse + fetch + render)
```

### Hydration DOM Reuse

```
Server HTML:
<app-root>
  <h1>Product: Angular Book</h1>
  <p>Price: $49.99</p>
  <button>Add to Cart</button>
</app-root>

Without hydration (Angular <16):
  1. Destroy all server DOM ❌
  2. Recreate from scratch (flicker!)
  3. Bind events

With hydration (Angular 16+):
  1. Walk existing DOM nodes ✅
  2. Match to component tree
  3. Attach event listeners to EXISTING nodes
  4. No DOM destruction, no flicker
```

---

## Diagram

```
SSR vs CSR Timeline:

CSR (Client-Side Rendering):
  ─────────────────────────────────────────────→ time
  │ blank │ download JS │ fetch data │ render │
  0ms     200ms         800ms         1200ms    1500ms ← First Meaningful Paint

SSR (Server-Side Rendering):  
  ─────────────────────────────────────────────→ time
  │ server render │ HTML visible │ hydrate │ interactive │
  0ms            200ms          200ms      700ms         1000ms
                  ↑ First Meaningful Paint (much faster!)

SSG (Pre-rendered):
  ─────────────────────────────────────────────→ time
  │ CDN serves cached HTML │ hydrate │ interactive │
  0ms                     50ms      500ms         800ms
                           ↑ Fastest possible (no server computation)
```

---

## Code

```typescript
// Complete SSR-aware service
@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);

  getProduct(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
    // With provideClientHydration(), Angular handles TransferState automatically
  }

  // Platform-aware analytics (only track in browser)
  trackView(productId: number): void {
    if (isPlatformBrowser(this.platformId)) {
      // Don't track on server-side renders
      this.analyticsService.track('product_view', { productId });
    }
  }
}

// SSR-safe component
@Component({
  selector: 'app-product-detail',
  template: `
    @if (product) {
      <h1>{{ product.name }}</h1>
      <p>{{ product.description }}</p>
      <div class="gallery" #gallery></div>
    }
  `
})
export class ProductDetailComponent {
  product: Product | null = null;
  @ViewChild('gallery') gallery!: ElementRef;
  
  private route = inject(ActivatedRoute);
  private productService = inject(ProductService);

  constructor() {
    // Safe: runs only in browser after DOM is ready
    afterNextRender(() => {
      this.initImageGallery(this.gallery.nativeElement);
    });
  }

  ngOnInit(): void {
    // This runs on BOTH server and client
    this.route.params.pipe(
      switchMap(params => this.productService.getProduct(+params['id']))
    ).subscribe(product => {
      this.product = product;
      this.productService.trackView(product.id); // Only tracks in browser
    });
  }
}

// app.config.ts for SSR
import { provideClientHydration } from '@angular/platform-browser';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withFetch()), // Use fetch API (works on server)
    provideClientHydration() // Enable hydration + automatic TransferState
  ]
};
```

---

## Dry Run

### SSR + Hydration Flow

```
User visits https://myapp.com/products/42:

SERVER SIDE:
Step 1: Express receives GET /products/42
Step 2: Angular SSR creates app instance
Step 3: Router resolves to ProductDetailComponent
Step 4: ngOnInit runs → HTTP GET /api/products/42 (server-to-server)
Step 5: Product data received: { id: 42, name: 'Angular Book', price: 49.99 }
Step 6: Template renders with data
Step 7: HTML serialized: <h1>Angular Book</h1><p>$49.99</p>
Step 8: TransferState embedded as <script type="application/json">
Step 9: Complete HTML sent to browser (~200ms total)

BROWSER SIDE:
Step 10: Browser receives HTML → renders immediately (user sees product!)
Step 11: JS bundles download in parallel
Step 12: Angular boots → provideClientHydration() active
Step 13: Hydration: matches DOM nodes to component tree (no re-render!)
Step 14: Event listeners attached (button clicks now work)
Step 15: ngOnInit runs → checks TransferState → product data found!
Step 16: No duplicate HTTP call → uses cached data ✅
Step 17: afterNextRender fires → image gallery initialized
Step 18: App fully interactive (~700ms after initial HTML)

Key savings:
- User sees content at 200ms (vs 1500ms with CSR)
- No duplicate API call (TransferState)
- No DOM flicker (hydration reuses server HTML)
```

---

## Interview Questions and Answers

**Q1: What is SSR and why use it in Angular?**
> SSR renders Angular on the server, sending complete HTML to the browser. Benefits: faster First Contentful Paint (user sees content before JS loads), better SEO (search engines can crawl content), improved performance on slow devices. Trade-off: more complex deployment (needs Node.js server), slightly slower Time to Interactive.

**Q2: What is hydration?**
> Hydration is the process where Angular takes over server-rendered HTML without re-creating it. Instead of destroying and rebuilding the DOM, Angular attaches event listeners and state to existing nodes. This prevents the "flicker" of content disappearing and reappearing, and improves perceived performance. Angular 16+ enables this with `provideClientHydration()`.

**Q3: What challenges does SSR introduce?**
> No browser APIs on server (window, document, localStorage) — use `isPlatformBrowser` checks or `afterNextRender`. Third-party libraries may access DOM directly — wrap in `afterNextRender`. State transfer between server and client needs handling (automatic with `provideClientHydration` in Angular 16+). More complex deployment (Node.js server vs static hosting).

**Q4: When should you NOT use SSR?**
> Authenticated dashboards (SEO irrelevant, content is private), heavy real-time apps (WebSocket-driven state changes constantly), internal tools. CSR is simpler to deploy (static files) and sufficient when SEO isn't a concern. SSR adds deployment complexity for marginal benefit in these cases.

**Q5: What is the difference between SSR, SSG, and ISR?**
> SSR: renders on every request (dynamic content, personalized pages). SSG (Static Site Generation): renders at build time (blogs, docs, marketing pages — fastest but stale until rebuild). ISR (Incremental Static Regeneration): serves cached static page but revalidates periodically (e-commerce product pages — fast + fresh).

---

## Follow-up Questions and Answers

**Q: How does Angular handle HttpClient on the server?**
> Angular's HttpClient with `withFetch()` uses the Fetch API which works in Node.js. Server-side HTTP calls go directly to the backend (no CORS — server-to-server). Use absolute URLs or configure a base URL for server-side requests. With `provideClientHydration()`, responses are automatically transferred to the client.

**Q: What is incremental hydration?**
> Angular 18+ allows selective hydration — parts of the page hydrate on different triggers (viewport visibility, user interaction, or never). This reduces JavaScript execution on initial load. Static content (footers, headers) can skip hydration entirely, while interactive sections hydrate on demand.

---

## Common Mistakes

1. **Accessing window/document without platform check**
   ```typescript
   // ❌ Crashes on server
   const width = window.innerWidth;
   
   // ✅ Platform check
   if (isPlatformBrowser(this.platformId)) {
     const width = window.innerWidth;
   }
   // Or use afterNextRender() for DOM access
   ```

2. **Using third-party libraries that access DOM directly**
   ```typescript
   // ❌ Chart library tries to access canvas element on server
   ngOnInit() { Chart.bindings(this.canvas); }
   
   // ✅ Defer to browser
   constructor() { afterNextRender(() => Chart.bindings(this.canvas)); }
   ```

3. **Not using provideClientHydration() (duplicate HTTP calls)**

4. **Using setTimeout for SSR workarounds instead of proper platform APIs**

---

## Best Practices

1. **Use `isPlatformBrowser`** before accessing browser APIs.
2. **Use `afterNextRender`** for browser-only initialization.
3. **Use TransferState** to avoid duplicate HTTP calls (server + client).
4. **Set meta tags dynamically** for SEO on each route.
5. **Pre-render static pages** (SSG) for content that rarely changes.
6. **Use Angular's built-in hydration** (Angular 16+, enabled by default with SSR).

---

## Related Topics

- → [25. Angular Performance](./25-angular-performance.md)
- → [36. Build and Deployment](./36-build-deployment.md)
- → [48. Frontend Performance](./48-frontend-performance.md)
