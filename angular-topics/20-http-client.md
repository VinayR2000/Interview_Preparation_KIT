# 20. HTTP Client

---

## Theory

Angular's `HttpClient` is a service for making HTTP requests to backend APIs. It returns Observables, integrates with interceptors, and provides typed responses.

### Setup

```typescript
// app.config.ts
import { provideHttpClient, withInterceptors } from '@angular/common/http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))
  ]
};
```

### HTTP Methods

```typescript
@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);
  private apiUrl = '/api/users';

  // GET — fetch data
  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  // POST — create
  createUser(user: CreateUserDTO): Observable<User> {
    return this.http.post<User>(this.apiUrl, user);
  }

  // PUT — full update
  updateUser(id: number, user: UpdateUserDTO): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, user);
  }

  // PATCH — partial update
  patchUser(id: number, changes: Partial<User>): Observable<User> {
    return this.http.patch<User>(`${this.apiUrl}/${id}`, changes);
  }

  // DELETE
  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
```

### Query Parameters

```typescript
import { HttpParams } from '@angular/common/http';

getUsers(page: number, size: number, sort: string): Observable<PaginatedResponse<User>> {
  const params = new HttpParams()
    .set('page', page.toString())
    .set('size', size.toString())
    .set('sort', sort);

  return this.http.get<PaginatedResponse<User>>(this.apiUrl, { params });
  // URL: /api/users?page=0&size=20&sort=name
}

// Alternative: pass params object directly
getUsers(filters: Record<string, string>): Observable<User[]> {
  return this.http.get<User[]>(this.apiUrl, { params: filters });
}
```

### Headers

```typescript
import { HttpHeaders } from '@angular/common/http';

uploadFile(file: File): Observable<UploadResponse> {
  const formData = new FormData();
  formData.append('file', file);

  const headers = new HttpHeaders({
    'Accept': 'application/json'
    // Don't set Content-Type for multipart — browser sets it with boundary
  });

  return this.http.post<UploadResponse>('/api/upload', formData, { headers });
}
```

### Full Response (Headers, Status)

```typescript
// observe: 'response' gives full HttpResponse
createUser(user: CreateUserDTO): Observable<HttpResponse<User>> {
  return this.http.post<User>(this.apiUrl, user, { observe: 'response' });
}

// Usage
this.userService.createUser(dto).subscribe(response => {
  console.log('Status:', response.status);         // 201
  console.log('Location:', response.headers.get('Location')); // /api/users/42
  console.log('Body:', response.body);             // User object
});
```

### Error Handling

```typescript
import { HttpErrorResponse } from '@angular/common/http';

getUser(id: number): Observable<User> {
  return this.http.get<User>(`${this.apiUrl}/${id}`).pipe(
    retry(2), // Retry failed requests up to 2 times
    catchError((error: HttpErrorResponse) => {
      if (error.status === 404) {
        return throwError(() => new Error('User not found'));
      }
      if (error.status === 0) {
        // Network error
        return throwError(() => new Error('Network unavailable'));
      }
      return throwError(() => new Error(`Server error: ${error.status}`));
    })
  );
}
```

### Request Cancellation

```typescript
// switchMap automatically cancels previous HTTP request
this.route.params.pipe(
  switchMap(params => this.userService.getUser(+params['id']))
).subscribe(user => this.user = user);

// Manual cancellation with takeUntil
private cancel$ = new Subject<void>();

loadData(): void {
  this.cancel$.next(); // Cancel previous
  this.http.get<Data>('/api/data').pipe(
    takeUntil(this.cancel$)
  ).subscribe(data => this.data = data);
}
```

---

## Internal Working

### HttpClient Request Flow

```
this.http.get<User[]>('/api/users').subscribe()
    ↓
1. HttpClient creates HttpRequest object
    ↓
2. Request passes through interceptor chain (in order)
    ↓
3. HttpXhrBackend (or HttpFetch) makes actual XMLHttpRequest/fetch
    ↓
4. Response received from server
    ↓
5. Response passes through interceptors (reverse order)
    ↓
6. HttpResponse delivered to subscriber
    ↓
7. Observable COMPLETES (HTTP observables complete after one emission)
```

### HTTP Observable Characteristics

```
- COLD Observable: each subscribe triggers a new HTTP request
- Emits exactly ONE value (the response)
- Automatically COMPLETES after emitting
- No need to unsubscribe for single requests (they complete)
  BUT: unsubscribe CANCELS the request (useful for switchMap)
- Errors are delivered via the error channel
```

---

## Code

```typescript
// Complete service with pagination, caching, error handling
interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class EmployeeApiService {
  private http = inject(HttpClient);
  private baseUrl = '/api/employees';

  // Paginated list with filters
  getEmployees(params: {
    page?: number;
    size?: number;
    sort?: string;
    department?: string;
    search?: string;
  }): Observable<PaginatedResponse<Employee>> {
    let httpParams = new HttpParams()
      .set('page', (params.page ?? 0).toString())
      .set('size', (params.size ?? 20).toString());

    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    if (params.department) httpParams = httpParams.set('department', params.department);
    if (params.search) httpParams = httpParams.set('search', params.search);

    return this.http.get<PaginatedResponse<Employee>>(this.baseUrl, { params: httpParams });
  }

  // CRUD operations
  getEmployee(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  createEmployee(dto: CreateEmployeeDTO): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, dto);
  }

  updateEmployee(id: number, dto: UpdateEmployeeDTO): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, dto);
  }

  deleteEmployee(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  // File upload with progress
  uploadAvatar(employeeId: number, file: File): Observable<HttpEvent<any>> {
    const formData = new FormData();
    formData.append('avatar', file);

    return this.http.post(`${this.baseUrl}/${employeeId}/avatar`, formData, {
      reportProgress: true,
      observe: 'events'
    });
  }

  // Bulk operations
  bulkDelete(ids: number[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/bulk-delete`, { ids });
  }
}

// Component using the service
@Component({
  selector: 'app-employee-list',
  template: `
    @if (loading) { <app-spinner /> }
    @if (error) { <app-error [message]="error" (retry)="load()" /> }
    @for (emp of employees; track emp.id) {
      <app-employee-row [employee]="emp" />
    }
    <app-pagination [total]="totalPages" [current]="currentPage" (pageChange)="onPage($event)" />
  `
})
export class EmployeeListComponent implements OnInit {
  private api = inject(EmployeeApiService);
  private route = inject(ActivatedRoute);
  
  employees: Employee[] = [];
  loading = false;
  error: string | null = null;
  currentPage = 0;
  totalPages = 0;

  ngOnInit(): void {
    this.route.queryParams.pipe(
      tap(() => { this.loading = true; this.error = null; }),
      switchMap(params => this.api.getEmployees({
        page: +params['page'] || 0,
        sort: params['sort'],
        search: params['q']
      }).pipe(
        catchError(err => {
          this.error = 'Failed to load employees';
          return of({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
        }),
        finalize(() => this.loading = false)
      ))
    ).subscribe(response => {
      this.employees = response.content;
      this.totalPages = response.totalPages;
      this.currentPage = response.page;
    });
  }
}
```

---

## Interview Questions and Answers

**Q1: How does Angular HttpClient work?**
> HttpClient is a service that wraps XMLHttpRequest/fetch into Observables. It provides typed responses via generics (`get<User[]>`), integrates with interceptors for cross-cutting concerns (auth, logging), and supports all HTTP methods. Observables enable cancellation, retry, and composition with other async operations.

**Q2: Why does HttpClient return Observables instead of Promises?**
> Observables support cancellation (unsubscribe aborts the request), retry, rich operator composition (switchMap for search), and consistency with Angular's reactive model (router, forms). They also allow interceptor pipelines. For simple cases, you can use `firstValueFrom()` to convert to Promise.

**Q3: Do you need to unsubscribe from HTTP observables?**
> HTTP Observables complete automatically after emitting one response — technically no unsubscribe needed. HOWEVER: if you navigate away before the response arrives, the subscription still holds a reference to the component. Best practice: use `takeUntilDestroyed()` or AsyncPipe, which also cancels the HTTP request (saving bandwidth).

**Q4: How do you handle errors in HTTP calls?**
> Use `catchError` operator in the pipe to intercept errors. Check `HttpErrorResponse.status` for specific handling (404, 401, 500). Use `retry(n)` for transient failures. For global error handling, use an HTTP interceptor that catches all errors centrally.

**Q5: How do you pass query parameters to GET requests?**
> Use `HttpParams`: `new HttpParams().set('page', '1').set('size', '20')`. Pass as options: `http.get(url, { params })`. HttpParams is immutable — each `.set()` returns a new instance. Alternatively, pass a plain object: `{ params: { page: '1', size: '20' } }`.

---

## Common Mistakes

1. **Multiple subscriptions = multiple requests**
   ```typescript
   // ❌ 3 subscriptions = 3 HTTP requests
   const users$ = this.http.get<User[]>('/api/users');
   users$.subscribe(u => this.list = u);
   users$.subscribe(u => this.count = u.length);
   users$.subscribe(u => console.log(u));
   
   // ✅ Share the response
   const users$ = this.http.get<User[]>('/api/users').pipe(shareReplay(1));
   ```

2. **Not typing responses**
   ```typescript
   // ❌ Returns Observable<Object> — no type safety
   this.http.get('/api/users');
   
   // ✅ Generic type parameter
   this.http.get<User[]>('/api/users');
   ```

3. **Setting Content-Type for FormData**
   ```typescript
   // ❌ Browser needs to set boundary
   headers: { 'Content-Type': 'multipart/form-data' }
   
   // ✅ Don't set Content-Type — browser handles it
   this.http.post('/api/upload', formData);
   ```

---

## Best Practices

1. **Always type your responses** with generics.
2. **Use services** to encapsulate API calls (not in components).
3. **Use interceptors** for auth tokens, error handling, logging.
4. **Use `switchMap`** when newer requests should cancel older ones.
5. **Use `shareReplay(1)`** when multiple components need same data.
6. **Handle errors** with catchError in services or interceptors.
7. **Use `environment.apiUrl`** for base URL configuration.

---

## Production Considerations

- **Timeout**: Use `timeout(10000)` operator to prevent hanging requests.
- **Retry with backoff**: `retry({ count: 3, delay: (_, i) => timer(1000 * i) })`.
- **Caching**: Use interceptor-based caching or `shareReplay` for frequently-accessed data.
- **Request deduplication**: Prevent identical concurrent requests with shareReplay.
- **CORS**: Configure backend to allow frontend origin.

---

## Related Topics

- → [17. RxJS](./17-rxjs.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
- → [23. Error Handling](./23-error-handling.md)
- → [38. Angular + Spring Boot Integration](./38-angular-spring-boot.md)
