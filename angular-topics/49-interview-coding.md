# 49. Angular Interview Coding — Practice Problems

---

## Theory

Interview coding challenges test your ability to implement Angular patterns from scratch. These cover TypeScript fundamentals, custom Angular constructs, and RxJS patterns.

---

## TypeScript/JavaScript Problems

### Reverse String
```typescript
function reverseString(str: string): string {
  return str.split('').reverse().join('');
}
// Or: [...str].reverse().join('')
```

### Palindrome
```typescript
function isPalindrome(str: string): boolean {
  const cleaned = str.toLowerCase().replace(/[^a-z0-9]/g, '');
  return cleaned === cleaned.split('').reverse().join('');
}
```

### Fibonacci
```typescript
function fibonacci(n: number): number[] {
  if (n <= 0) return [];
  if (n === 1) return [0];
  const fib = [0, 1];
  for (let i = 2; i < n; i++) {
    fib.push(fib[i - 1] + fib[i - 2]);
  }
  return fib;
}
```

### Remove Duplicates
```typescript
function removeDuplicates<T>(arr: T[]): T[] {
  return [...new Set(arr)];
}

// For objects by key
function uniqueBy<T>(arr: T[], key: keyof T): T[] {
  const seen = new Set();
  return arr.filter(item => {
    if (seen.has(item[key])) return false;
    seen.add(item[key]);
    return true;
  });
}
```

### Flatten Array
```typescript
function flatten(arr: any[]): any[] {
  return arr.reduce((acc, item) =>
    acc.concat(Array.isArray(item) ? flatten(item) : item), []);
}
// Or: arr.flat(Infinity)
```

### Debounce Function
```typescript
function debounce<T extends (...args: any[]) => any>(fn: T, delay: number): T {
  let timer: ReturnType<typeof setTimeout>;
  return ((...args: any[]) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  }) as T;
}
```

### Throttle Function
```typescript
function throttle<T extends (...args: any[]) => any>(fn: T, limit: number): T {
  let inThrottle = false;
  return ((...args: any[]) => {
    if (!inThrottle) {
      fn(...args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  }) as T;
}
```

### Frequency of Characters
```typescript
function charFrequency(str: string): Record<string, number> {
  return [...str].reduce((freq, char) => {
    freq[char] = (freq[char] || 0) + 1;
    return freq;
  }, {} as Record<string, number>);
}
```

### Group By
```typescript
function groupBy<T>(arr: T[], key: keyof T): Record<string, T[]> {
  return arr.reduce((groups, item) => {
    const group = String(item[key]);
    groups[group] = groups[group] || [];
    groups[group].push(item);
    return groups;
  }, {} as Record<string, T[]>);
}
// groupBy(users, 'department') → { Engineering: [...], Marketing: [...] }
```

---

## Angular-Specific Problems

### Custom Pipe — Truncate

```typescript
@Pipe({ name: 'truncate', standalone: true })
export class TruncatePipe implements PipeTransform {
  transform(value: string, limit = 50, trail = '...'): string {
    if (!value) return '';
    return value.length > limit ? value.substring(0, limit) + trail : value;
  }
}
```

### Custom Directive — Click Outside

```typescript
@Directive({ selector: '[appClickOutside]', standalone: true })
export class ClickOutsideDirective {
  @Output() appClickOutside = new EventEmitter<void>();
  
  constructor(private el: ElementRef) {}

  @HostListener('document:click', ['$event.target'])
  onClick(target: HTMLElement): void {
    if (!this.el.nativeElement.contains(target)) {
      this.appClickOutside.emit();
    }
  }
}
```

### Custom Validator — Password Strength

```typescript
export function passwordStrength(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (!value) return null;
    
    const hasUpper = /[A-Z]/.test(value);
    const hasLower = /[a-z]/.test(value);
    const hasNumber = /\d/.test(value);
    const hasSpecial = /[!@#$%^&*]/.test(value);
    const minLength = value.length >= 8;

    const valid = hasUpper && hasLower && hasNumber && hasSpecial && minLength;
    return valid ? null : {
      passwordStrength: {
        hasUpper, hasLower, hasNumber, hasSpecial, minLength
      }
    };
  };
}
```

### HTTP Interceptor — Auth

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  if (token && !req.url.includes('/auth/')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
```

### Auth Guard

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
```

### Shared Service (Sibling Communication)

```typescript
@Injectable({ providedIn: 'root' })
export class MessageService {
  private messageSubject = new Subject<string>();
  message$ = this.messageSubject.asObservable();

  send(message: string): void {
    this.messageSubject.next(message);
  }
}
```

### RxJS Search with Autocomplete

```typescript
@Component({
  template: `
    <input [formControl]="search">
    @for (result of results$ | async; track result.id) {
      <div>{{ result.name }}</div>
    }
  `
})
export class SearchComponent {
  search = new FormControl('');
  
  results$ = this.search.valueChanges.pipe(
    debounceTime(300),
    distinctUntilChanged(),
    filter(term => !!term && term.length >= 2),
    switchMap(term => inject(ApiService).search(term).pipe(
      catchError(() => of([]))
    ))
  );
}
```

### Pagination Component

```typescript
@Component({
  selector: 'app-pagination',
  standalone: true,
  template: `
    <nav>
      <button (click)="onPage(currentPage - 1)" [disabled]="currentPage === 0">Prev</button>
      @for (page of pages; track page) {
        <button (click)="onPage(page)" [class.active]="page === currentPage">
          {{ page + 1 }}
        </button>
      }
      <button (click)="onPage(currentPage + 1)" [disabled]="currentPage === totalPages - 1">Next</button>
    </nav>
  `
})
export class PaginationComponent {
  @Input() currentPage = 0;
  @Input() totalPages = 0;
  @Output() pageChange = new EventEmitter<number>();

  get pages(): number[] {
    const start = Math.max(0, this.currentPage - 2);
    const end = Math.min(this.totalPages, start + 5);
    return Array.from({ length: end - start }, (_, i) => start + i);
  }

  onPage(page: number): void {
    if (page >= 0 && page < this.totalPages && page !== this.currentPage) {
      this.pageChange.emit(page);
    }
  }
}
```

### Counter with Signals

```typescript
@Component({
  selector: 'app-counter',
  standalone: true,
  template: `
    <button (click)="decrement()">-</button>
    <span>{{ count() }}</span>
    <button (click)="increment()">+</button>
    <p>Doubled: {{ doubled() }}</p>
  `
})
export class CounterComponent {
  count = signal(0);
  doubled = computed(() => this.count() * 2);

  increment(): void { this.count.update(c => c + 1); }
  decrement(): void { this.count.update(c => c - 1); }
}
```

---

## Common Interview Patterns to Memorize

```typescript
// 1. takeUntilDestroyed cleanup
private destroyRef = inject(DestroyRef);
this.obs$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe();

// 2. OnPush + immutable update
@Component({ changeDetection: ChangeDetectionStrategy.OnPush })
this.items = [...this.items, newItem]; // New reference

// 3. Typed HTTP call
this.http.get<User[]>('/api/users').pipe(catchError(() => of([])));

// 4. Route param loading
this.route.params.pipe(
  switchMap(p => this.service.get(+p['id']))
).subscribe(data => this.data = data);

// 5. Form with validation
this.fb.group({
  email: ['', [Validators.required, Validators.email]],
  password: ['', [Validators.required, Validators.minLength(8)]]
});
```

---

## Related Topics

- → [2. TypeScript](./02-typescript-essentials.md)
- → [5. Directives](./05-directives.md)
- → [6. Pipes](./06-pipes.md)
- → [14. Route Guards](./14-route-guards.md)
- → [16. Forms](./16-forms.md)
- → [17. RxJS](./17-rxjs.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
