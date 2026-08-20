# 48. Frontend Performance

---

## Theory

Frontend performance focuses on delivering fast, responsive experiences measured by Core Web Vitals and perceived performance.

### Core Web Vitals

| Metric | What it Measures | Target |
|--------|-----------------|--------|
| **LCP** (Largest Contentful Paint) | When main content is visible | < 2.5s |
| **INP** (Interaction to Next Paint) | Input responsiveness | < 200ms |
| **CLS** (Cumulative Layout Shift) | Visual stability | < 0.1 |

### Performance Optimization Categories

```
1. LOADING (Network + Bundle)
   ├── Code splitting / lazy loading
   ├── Tree shaking
   ├── Compression (Brotli/Gzip)
   ├── CDN for static assets
   ├── Image optimization (WebP, lazy loading)
   └── Preloading / prefetching

2. RENDERING (DOM + Change Detection)
   ├── OnPush change detection
   ├── Virtual scrolling
   ├── @defer (Angular 17+)
   ├── Avoid layout thrashing
   └── requestAnimationFrame for animations

3. RUNTIME (JavaScript Execution)
   ├── Debouncing / throttling
   ├── Web Workers for heavy computation
   ├── Efficient algorithms
   ├── Avoid memory leaks
   └── runOutsideAngular for non-UI work

4. CACHING
   ├── HTTP caching (Cache-Control)
   ├── Service Worker (offline + cache)
   ├── shareReplay for API responses
   ├── Browser storage for state
   └── CDN caching
```

### Angular-Specific Optimizations

```typescript
// 1. @defer — lazy load heavy components (Angular 17+)
@defer (on viewport) {
  <app-analytics-chart [data]="chartData" />
} @placeholder {
  <div style="height: 300px" class="skeleton"></div>
}

// 2. Image optimization with NgOptimizedImage
import { NgOptimizedImage } from '@angular/common';

@Component({
  imports: [NgOptimizedImage],
  template: `<img ngSrc="hero.jpg" width="1200" height="600" priority>`
})
// Automatically: lazy loading, srcset, preconnect hints, LCP optimization

// 3. Preconnect to API domain
// <link rel="preconnect" href="https://api.myapp.com">

// 4. Service Worker for caching
// ng add @angular/pwa
// ngsw-config.json configures asset caching strategies
```

### Bundle Optimization

```bash
# Analyze bundle
ng build --configuration production --stats-json
npx webpack-bundle-analyzer dist/my-app/stats.json

# Common findings:
# - Moment.js (500KB!) → replace with date-fns or native Intl
# - Lodash (full import) → import { debounce } from 'lodash-es'
# - Unused icons from icon libraries
# - Barrel file side effects importing entire modules
```

### Image Optimization

```html
<!-- Lazy load below-fold images -->
<img src="photo.jpg" loading="lazy" alt="Description">

<!-- Responsive images -->
<img srcset="photo-400.jpg 400w, photo-800.jpg 800w, photo-1200.jpg 1200w"
     sizes="(max-width: 600px) 400px, (max-width: 900px) 800px, 1200px"
     src="photo-800.jpg" alt="Description">

<!-- Modern format with fallback -->
<picture>
  <source srcset="photo.avif" type="image/avif">
  <source srcset="photo.webp" type="image/webp">
  <img src="photo.jpg" alt="Description">
</picture>
```

### Performance Monitoring

```typescript
// Measure component render time
export class PerformanceService {
  mark(name: string): void {
    performance.mark(name);
  }

  measure(name: string, startMark: string): number {
    performance.mark(`${name}-end`);
    const measure = performance.measure(name, startMark, `${name}-end`);
    return measure.duration;
  }
}

// Web Vitals reporting
import { onLCP, onINP, onCLS } from 'web-vitals';
onLCP(metric => sendToAnalytics('LCP', metric.value));
onINP(metric => sendToAnalytics('INP', metric.value));
onCLS(metric => sendToAnalytics('CLS', metric.value));
```

---

## Interview Questions and Answers

**Q1: How do you measure and improve frontend performance?**
> Measure: Lighthouse audit, Core Web Vitals (LCP, INP, CLS), webpack-bundle-analyzer, Chrome DevTools Performance tab. Improve: lazy loading (reduce initial bundle), OnPush (fewer CD cycles), virtual scrolling (fewer DOM nodes), image optimization (WebP, lazy loading), compression (Brotli), CDN, caching (Service Worker).

**Q2: What are Core Web Vitals?**
> Google's metrics for user experience: LCP (Largest Contentful Paint < 2.5s) measures loading speed. INP (Interaction to Next Paint < 200ms) measures responsiveness. CLS (Cumulative Layout Shift < 0.1) measures visual stability. They affect SEO rankings and directly correlate with user satisfaction.

**Q3: How do you reduce Angular bundle size?**
> Lazy load all feature routes. Use standalone components (smaller than modules). Tree shake unused code (production build). Analyze bundle for bloated dependencies. Replace large libraries with smaller alternatives (moment→date-fns). Import only what you need (not entire libraries). Set build budgets to catch regressions.

**Q4: What is the role of a CDN in frontend performance?**
> CDN (Content Delivery Network) serves static assets from edge servers geographically close to users — reducing latency by 50-80%. Angular's hashed filenames enable aggressive CDN caching (1 year). Combined with Brotli compression: 60-80% smaller file sizes. Critical for global applications.

---

## Best Practices

1. **Measure first** — don't optimize without data (Lighthouse, DevTools).
2. **Bundle budget** — 500KB warning, 1MB error for initial load.
3. **Lazy load everything** that isn't on the first screen.
4. **Optimize images** — WebP/AVIF, lazy loading, proper sizing.
5. **Use CDN** for all static assets.
6. **Enable compression** — Brotli preferred over Gzip.
7. **Cache aggressively** — hashed assets = 1 year cache.
8. **Service Worker** for offline support and instant repeat visits.
9. **Monitor in production** — track Core Web Vitals continuously.
10. **@defer** heavy components until they're needed.

---

## Related Topics

- → [15. Lazy Loading](./15-lazy-loading.md)
- → [25. Angular Performance](./25-angular-performance.md)
- → [36. Build and Deployment](./36-build-deployment.md)
- → [44. SSR](./44-ssr.md)
