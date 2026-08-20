# 38. Angular + Spring Boot Integration — Critical for Full-Stack Profile

---

## Theory

This topic covers the complete integration between Angular frontend and Spring Boot backend — the core architecture for Java full-stack developers.

### Typical Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                      Client (Browser)                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                  Angular Application                       │  │
│  │  Components → Services → HttpClient → Interceptors        │  │
│  └────────────────────────┬─────────────────────────────────┘  │
└───────────────────────────┼────────────────────────────────────┘
                            │ HTTP/HTTPS (JSON)
                            ↓
┌────────────────────────────────────────────────────────────────┐
│                    API Gateway / Nginx                           │
└───────────────────────────┬────────────────────────────────────┘
                            ↓
┌────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                       │
│  ┌────────────┐  ┌──────────┐  ┌────────────┐  ┌───────────┐ │
│  │ Controller  │→ │ Service  │→ │ Repository │→ │ Database  │ │
│  │ (REST API)  │  │ (Logic)  │  │ (JPA)      │  │(PostgreSQL)│ │
│  └────────────┘  └──────────┘  └────────────┘  └───────────┘ │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Spring Security (JWT Filter → Authentication → Authorization)│
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### Communication Pattern

| Aspect | Angular Side | Spring Boot Side |
|--------|-------------|-----------------|
| HTTP calls | HttpClient service | @RestController |
| Data format | TypeScript interfaces | DTOs / Entities |
| Auth token | HTTP Interceptor adds JWT | JWT Filter validates |
| Error codes | Error interceptor handles | @ExceptionHandler |
| Validation | Reactive Forms validators | @Valid + Bean Validation |
| Pagination | Query params (page, size) | Pageable + Page<T> |

### DTOs — Shared Data Contract

```typescript
// Angular — TypeScript interface
export interface EmployeeDTO {
  id: number;
  name: string;
  email: string;
  department: string;
  salary: number;
  joinDate: string; // ISO date string from backend
}

export interface CreateEmployeeDTO {
  name: string;
  email: string;
  department: string;
  salary: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;      // current page (0-based)
  size: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
```

```java
// Spring Boot — Java DTO
public record EmployeeDTO(
    Long id,
    String name,
    String email,
    String department,
    BigDecimal salary,
    LocalDate joinDate
) {}

public record CreateEmployeeDTO(
    @NotBlank String name,
    @Email String email,
    @NotBlank String department,
    @Min(30000) BigDecimal salary
) {}
```

### Angular Service Consuming Spring Boot API

```typescript
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  private apiUrl = '/api/employees'; // Proxied to Spring Boot

  getEmployees(page = 0, size = 20, sort = 'name,asc'): Observable<PaginatedResponse<EmployeeDTO>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);
    return this.http.get<PaginatedResponse<EmployeeDTO>>(this.apiUrl, { params });
  }

  getEmployee(id: number): Observable<EmployeeDTO> {
    return this.http.get<EmployeeDTO>(`${this.apiUrl}/${id}`);
  }

  createEmployee(dto: CreateEmployeeDTO): Observable<EmployeeDTO> {
    return this.http.post<EmployeeDTO>(this.apiUrl, dto);
  }

  updateEmployee(id: number, dto: CreateEmployeeDTO): Observable<EmployeeDTO> {
    return this.http.put<EmployeeDTO>(`${this.apiUrl}/${id}`, dto);
  }

  deleteEmployee(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  searchEmployees(query: string): Observable<EmployeeDTO[]> {
    return this.http.get<EmployeeDTO[]>(`${this.apiUrl}/search`, {
      params: { q: query }
    });
  }

  uploadAvatar(id: number, file: File): Observable<{ url: string }> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<{ url: string }>(`${this.apiUrl}/${id}/avatar`, formData);
  }
}
```

### Spring Boot Controller (Backend)

```java
@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "http://localhost:4200") // Dev only
public class EmployeeController {

    @GetMapping
    public Page<EmployeeDTO> getEmployees(Pageable pageable) {
        return employeeService.getAll(pageable);
    }

    @GetMapping("/{id}")
    public EmployeeDTO getEmployee(@PathVariable Long id) {
        return employeeService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDTO createEmployee(@Valid @RequestBody CreateEmployeeDTO dto) {
        return employeeService.create(dto);
    }

    @PutMapping("/{id}")
    public EmployeeDTO updateEmployee(@PathVariable Long id, @Valid @RequestBody CreateEmployeeDTO dto) {
        return employeeService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.delete(id);
    }
}
```

### Development Proxy Configuration

```json
// proxy.conf.json (Angular dev server proxies to Spring Boot)
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

```json
// angular.json — add proxy to serve
"serve": {
  "options": {
    "proxyConfig": "proxy.conf.json"
  }
}
```

### CORS Configuration (Spring Boot)

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:4200", "https://myapp.com")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
```

### JWT Authentication Flow (End-to-End)

```
1. Angular Login Form → POST /api/auth/login { email, password }
2. Spring Security authenticates → generates JWT
3. Response: { accessToken: "eyJ...", refreshToken: "...", user: {...} }
4. Angular stores token in localStorage
5. Angular HTTP Interceptor: Authorization: Bearer eyJ...
6. Spring Boot JWT Filter: validates token on every request
7. SecurityContext populated → request proceeds to controller
8. On 401: Angular interceptor tries refresh token
9. On refresh fail: Angular redirects to login
```

### Error Handling Integration

```typescript
// Angular interceptor handles Spring Boot error responses
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Spring Boot returns structured error:
      // { timestamp, status, error, message, path }
      const apiError = error.error as ApiError;
      
      if (apiError?.message) {
        inject(NotificationService).show(apiError.message, 'error');
      }
      
      return throwError(() => error);
    })
  );
};
```

```java
// Spring Boot global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EmployeeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(EmployeeNotFoundException ex) {
        return new ApiError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ApiError(HttpStatus.BAD_REQUEST, message);
    }
}
```

### Pagination Integration

```typescript
// Angular component with pagination
@Component({
  template: `
    @for (emp of employees; track emp.id) {
      <app-employee-row [employee]="emp" />
    }
    <app-pagination 
      [currentPage]="currentPage"
      [totalPages]="totalPages"
      (pageChange)="onPageChange($event)" />
  `
})
export class EmployeeListComponent implements OnInit {
  employees: EmployeeDTO[] = [];
  currentPage = 0;
  totalPages = 0;

  private employeeService = inject(EmployeeService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  ngOnInit(): void {
    this.route.queryParams.pipe(
      switchMap(params => {
        this.currentPage = +(params['page'] ?? 0);
        return this.employeeService.getEmployees(this.currentPage, 20, 'name,asc');
      })
    ).subscribe(response => {
      this.employees = response.content;
      this.totalPages = response.totalPages;
    });
  }

  onPageChange(page: number): void {
    this.router.navigate([], { queryParams: { page }, queryParamsHandling: 'merge' });
  }
}
```

---

## Diagram

```
Development Setup:
┌──────────────────┐              ┌──────────────────┐
│ Angular Dev      │   proxy      │ Spring Boot      │
│ localhost:4200   │ ──────────→  │ localhost:8080   │
│                  │  /api/*      │                  │
│ ng serve         │              │ mvn spring-boot: │
│                  │              │   run            │
└──────────────────┘              └──────────────────┘

Production Setup:
┌──────────────────┐
│ Nginx            │
│ (serves Angular  │  → /api/*  → Spring Boot (Docker)
│  static files)   │              → PostgreSQL (Docker)
│ :80 / :443       │
└──────────────────┘
```

---

## Interview Questions and Answers

**Q1: How does Angular communicate with Spring Boot?**
> Angular uses HttpClient to make REST API calls (GET, POST, PUT, DELETE) to Spring Boot @RestController endpoints. Data is exchanged as JSON. Angular TypeScript interfaces mirror Spring Boot DTOs. Authentication uses JWT — Angular interceptor adds token, Spring Security filter validates it.

**Q2: How do you handle CORS between Angular and Spring Boot?**
> In development: use Angular's proxy.conf.json to proxy /api/* to localhost:8080 (no CORS needed). In production: configure CORS on Spring Boot using `@CrossOrigin` or `WebMvcConfigurer` — specify allowed origins, methods, headers. Or serve Angular from the same origin via Nginx.

**Q3: How do you handle pagination between Angular and Spring Boot?**
> Spring Boot uses Spring Data's `Pageable` parameter and returns `Page<T>` (contains content, totalElements, totalPages, etc.). Angular sends query params `?page=0&size=20&sort=name,asc`. Angular service maps the response to display content and pagination controls.

**Q4: How do you deploy Angular + Spring Boot together?**
> Option 1: Build Angular (`ng build`), copy dist/ to Spring Boot's `src/main/resources/static/` — single JAR serves both. Option 2: Separate containers — Angular served by Nginx, Spring Boot as separate service, Nginx proxies /api to Spring Boot. Option 2 is preferred for production (independent scaling/deployment).

**Q5: How do you handle validation between frontend and backend?**
> Validate on BOTH sides. Angular: Reactive Forms validators provide instant UX feedback. Spring Boot: @Valid + Bean Validation (@NotBlank, @Email, @Min) enforces data integrity. On validation failure, Spring Boot returns 400 with field-level errors. Angular interceptor/component displays these server-side errors.

---

## Best Practices

1. **Use proxy in development** — avoid CORS complexity during dev.
2. **Mirror DTOs** — Angular interfaces should match Spring Boot DTOs exactly.
3. **Validate on both sides** — frontend for UX, backend for security.
4. **Use interceptors** for cross-cutting concerns (auth, errors, loading).
5. **Pagination** — use Spring Data's Pageable conventions.
6. **Error responses** — standardize format between frontend and backend.
7. **Environment config** — use Angular environments for API URLs.
8. **API versioning** — prefix with /api/v1/ for future flexibility.

---

## Production Considerations

- **HTTPS**: Always use HTTPS in production. Configure SSL in Nginx.
- **CORS**: In production, restrict to your specific domain only.
- **API Gateway**: Use Spring Cloud Gateway or Nginx for routing, rate limiting.
- **Monitoring**: Backend → Spring Boot Actuator + Prometheus. Frontend → Sentry.
- **CI/CD**: Build Angular and Spring Boot in same pipeline, deploy as containers.
- **Docker Compose**: Local development with Angular, Spring Boot, PostgreSQL, Redis.

---

## Related Topics

- → [20. HTTP Client](./20-http-client.md)
- → [21. HTTP Interceptors](./21-http-interceptors.md)
- → [22. Angular + JWT Authentication](./22-angular-jwt-auth.md)
- → [37. Angular + Docker](./37-angular-docker.md)
- → [39. CORS](./39-cors.md)
