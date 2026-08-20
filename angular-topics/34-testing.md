# 34. Testing

---

## Theory

Angular provides a comprehensive testing ecosystem with TestBed for component testing, and supports Jasmine/Karma (default) or Jest as test runners.

### Testing Types

| Type | What | Tools | Speed |
|------|------|-------|-------|
| Unit | Individual classes/functions | Jasmine/Jest | Fast |
| Component | Component + template | TestBed | Medium |
| Integration | Multiple components together | TestBed | Slower |
| E2E | Full application flow | Cypress/Playwright | Slowest |

### Service Testing

```typescript
describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify()); // No outstanding requests

  it('should fetch users', () => {
    const mockUsers: User[] = [
      { id: 1, name: 'John', email: 'j@t.com' }
    ];

    service.getUsers().subscribe(users => {
      expect(users.length).toBe(1);
      expect(users[0].name).toBe('John');
    });

    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    req.flush(mockUsers); // Provide mock response
  });

  it('should handle error', () => {
    service.getUser(999).subscribe({
      error: err => expect(err.message).toContain('not found')
    });

    const req = httpMock.expectOne('/api/users/999');
    req.flush('Not found', { status: 404, statusText: 'Not Found' });
  });
});
```

### Component Testing

```typescript
describe('UserCardComponent', () => {
  let component: UserCardComponent;
  let fixture: ComponentFixture<UserCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserCardComponent] // Standalone component
    }).compileComponents();

    fixture = TestBed.createComponent(UserCardComponent);
    component = fixture.componentInstance;
    component.user = { id: 1, name: 'John', email: 'j@t.com' };
    fixture.detectChanges(); // Trigger initial change detection
  });

  it('should display user name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h3')?.textContent).toContain('John');
  });

  it('should emit delete event', () => {
    spyOn(component.delete, 'emit');
    
    const deleteBtn = fixture.nativeElement.querySelector('.delete-btn');
    deleteBtn.click();
    
    expect(component.delete.emit).toHaveBeenCalledWith(1);
  });

  it('should update when input changes', () => {
    component.user = { id: 2, name: 'Jane', email: 'jane@t.com' };
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('h3').textContent).toContain('Jane');
  });
});
```

### Testing with Mocks

```typescript
describe('UserListComponent', () => {
  let mockUserService: jasmine.SpyObj<UserService>;

  beforeEach(async () => {
    mockUserService = jasmine.createSpyObj('UserService', ['getUsers', 'deleteUser']);
    mockUserService.getUsers.and.returnValue(of([
      { id: 1, name: 'John' }, { id: 2, name: 'Jane' }
    ]));

    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        { provide: UserService, useValue: mockUserService }
      ]
    }).compileComponents();
  });

  it('should load users on init', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    
    expect(mockUserService.getUsers).toHaveBeenCalled();
    const cards = fixture.nativeElement.querySelectorAll('app-user-card');
    expect(cards.length).toBe(2);
  });
});
```

### Pipe Testing

```typescript
describe('TruncatePipe', () => {
  const pipe = new TruncatePipe();

  it('should truncate long text', () => {
    expect(pipe.transform('Hello World', 5)).toBe('Hello...');
  });

  it('should not truncate short text', () => {
    expect(pipe.transform('Hi', 5)).toBe('Hi');
  });

  it('should handle empty string', () => {
    expect(pipe.transform('', 5)).toBe('');
  });
});
```

### Guard/Interceptor Testing

```typescript
describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        provideRouter([])
      ]
    });
    router = TestBed.inject(Router);
  });

  it('should allow access when authenticated', () => {
    authService.isAuthenticated.and.returnValue(true);
    const result = TestBed.runInInjectionContext(() => 
      authGuard({} as any, { url: '/dashboard' } as any)
    );
    expect(result).toBe(true);
  });

  it('should redirect to login when not authenticated', () => {
    authService.isAuthenticated.and.returnValue(false);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/dashboard' } as any)
    );
    expect(result).toEqual(router.createUrlTree(['/login'], {
      queryParams: { returnUrl: '/dashboard' }
    }));
  });
});
```

---

## Interview Questions and Answers

**Q1: How do you test Angular components?**
> Use TestBed to configure a testing module with the component and its dependencies. Create a ComponentFixture for DOM access. Provide mock services via `{ provide: Service, useValue: mockService }`. Call `fixture.detectChanges()` to trigger rendering. Query the DOM with `nativeElement.querySelector()`. Test inputs, outputs, and rendered HTML.

**Q2: How do you mock services in tests?**
> `jasmine.createSpyObj('ServiceName', ['method1', 'method2'])` creates a mock with spy methods. Configure return values: `mock.method1.and.returnValue(of(data))`. Provide in TestBed: `{ provide: RealService, useValue: mockService }`. This isolates the component from real HTTP calls and external dependencies.

**Q3: How do you test HTTP calls?**
> Use `HttpTestingController` from `@angular/common/http/testing`. After calling the service method, use `httpMock.expectOne(url)` to capture the request. Assert method, headers, body. Call `req.flush(mockData)` to provide response. Call `httpMock.verify()` in afterEach to ensure no outstanding requests.

**Q4: How do you test async operations in Angular?**
> For Observables: subscribe in the test and assert in the subscription callback. For fakeAsync: wrap test in `fakeAsync(() => { ... })`, use `tick()` to advance time, `flush()` to complete all pending timers. For async: use `async/await` with `fixture.whenStable()`.

---

---

## Internal Working

### TestBed Architecture

```
TestBed creates an isolated Angular testing environment:
1. Creates a testing module (like a mini NgModule)
2. Compiles components within that module
3. Creates component fixtures with change detection
4. Provides a test injector for mocking dependencies

fixture.detectChanges():
  → Triggers change detection for the component
  → Evaluates bindings
  → Updates DOM
  → Must be called after setting inputs or changing state
```

### Async Testing Patterns

```typescript
// fakeAsync — control time synchronously
it('should debounce search', fakeAsync(() => {
  component.searchControl.setValue('angular');
  tick(300); // Advance virtual time by 300ms (debounceTime)
  fixture.detectChanges();
  expect(mockService.search).toHaveBeenCalledWith('angular');
}));

// waitForAsync — wait for all promises/observables
it('should load data', waitForAsync(() => {
  fixture.detectChanges();
  fixture.whenStable().then(() => {
    expect(component.data.length).toBe(3);
  });
}));

// done callback — manual async control
it('should emit event', (done) => {
  component.output.subscribe(value => {
    expect(value).toBe('test');
    done();
  });
  component.triggerOutput();
});
```

---

## Diagram

```
Testing Pyramid for Angular:
┌────────────────────────┐
│ E2E Tests (Cypress)     │  Few — slow, brittle
│ Full app flow           │
├────────────────────────┤
│ Integration Tests       │  Some — component + children
│ (TestBed + real deps)   │
├────────────────────────┤
│ Component Tests         │  Many — component + template
│ (TestBed + mocks)       │
├────────────────────────┤
│ Unit Tests              │  Most — services, pipes, functions
│ (No TestBed needed)     │  Fast, isolated
└────────────────────────┘
```

---

## Code

```typescript
// Testing reactive forms
describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['login']);
    router = jasmine.createSpyObj('Router', ['navigateByUrl']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should disable submit when form is invalid', () => {
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button.disabled).toBeTrue();
  });

  it('should enable submit when form is valid', () => {
    component.loginForm.patchValue({ email: 'test@test.com', password: '123456' });
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button.disabled).toBeFalse();
  });

  it('should call auth service on submit', () => {
    authService.login.and.returnValue(of({ id: 1, name: 'Test' } as User));
    component.loginForm.patchValue({ email: 'test@test.com', password: '123456' });
    component.onSubmit();
    expect(authService.login).toHaveBeenCalledWith('test@test.com', '123456');
  });

  it('should navigate on successful login', () => {
    authService.login.and.returnValue(of({ id: 1, name: 'Test' } as User));
    component.loginForm.patchValue({ email: 'test@test.com', password: '123456' });
    component.onSubmit();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('should show error message on login failure', () => {
    authService.login.and.returnValue(throwError(() => ({ status: 401 })));
    component.loginForm.patchValue({ email: 'test@test.com', password: 'wrong' });
    component.onSubmit();
    fixture.detectChanges();
    const error = fixture.nativeElement.querySelector('.alert.error');
    expect(error.textContent).toContain('Invalid email or password');
  });
});

// Testing Observable-based component
describe('UserListComponent', () => {
  it('should show loading spinner initially', () => {
    const mockService = jasmine.createSpyObj('UserService', ['getUsers']);
    mockService.getUsers.and.returnValue(new Subject()); // Never emits

    TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [{ provide: UserService, useValue: mockService }]
    });

    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    
    expect(fixture.nativeElement.querySelector('app-spinner')).toBeTruthy();
  });

  it('should render users when loaded', () => {
    const mockService = jasmine.createSpyObj('UserService', ['getUsers']);
    mockService.getUsers.and.returnValue(of([
      { id: 1, name: 'Alice' },
      { id: 2, name: 'Bob' }
    ]));

    TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [{ provide: UserService, useValue: mockService }]
    });

    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();

    const cards = fixture.nativeElement.querySelectorAll('app-user-card');
    expect(cards.length).toBe(2);
  });
});
```

---

## Dry Run

### Component Test Execution

```
beforeEach:
  Step 1: TestBed creates testing module with LoginComponent + mocks
  Step 2: compileComponents() compiles template
  Step 3: createComponent(LoginComponent) instantiates
  Step 4: fixture.detectChanges() → ngOnInit, template renders

Test: 'should disable submit when form is invalid'
  Step 1: Form initial state: email='', password='' → INVALID
  Step 2: Query DOM: button[type="submit"]
  Step 3: Assert button.disabled === true ✅

Test: 'should show error on failure'
  Step 1: Mock returns error Observable
  Step 2: Set form values (valid)
  Step 3: Call onSubmit() → login() called → error thrown
  Step 4: component.errorMessage = 'Invalid email or password'
  Step 5: fixture.detectChanges() → DOM updates
  Step 6: Query .alert.error → verify text content ✅
```

---

## Common Mistakes

1. **Forgetting fixture.detectChanges() after state changes**
   ```typescript
   // ❌ DOM won't reflect the change
   component.title = 'New Title';
   expect(fixture.nativeElement.querySelector('h1').textContent).toBe('New Title'); // FAILS
   
   // ✅ Trigger CD first
   component.title = 'New Title';
   fixture.detectChanges();
   expect(fixture.nativeElement.querySelector('h1').textContent).toBe('New Title');
   ```

2. **Not using fakeAsync for debounced operations**
3. **Testing implementation instead of behavior**
4. **Sharing state between tests (not resetting mocks)**
5. **Not testing error paths and edge cases**

---

## Best Practices

1. **Test behavior, not implementation** — test what the user sees.
2. **Use mocks for external dependencies** (HTTP, services).
3. **One assert per test** when possible (clear failure messages).
4. **Use `fixture.detectChanges()`** after setting inputs.
5. **Test error states** — not just happy paths.
6. **Use `fakeAsync` + `tick`** for timer-based tests.
7. **Keep tests independent** — no shared mutable state between tests.
8. **Use `jasmine.createSpyObj`** for clean service mocking.
9. **Test component @Output emissions** by spying on emit.
10. **Test form validation** — invalid state, error messages, submit behavior.

---

## Production Considerations

- **Code coverage**: Aim for 80%+ on services and critical components.
- **CI/CD**: Run `ng test --no-watch --code-coverage` in pipeline.
- **E2E**: Use Playwright or Cypress for critical user flows (login, checkout).
- **Visual regression**: Tools like Chromatic catch unintended UI changes.
- **Performance tests**: Lighthouse CI to catch performance regressions.

---

## Related Topics

- → [3. Components](./03-components.md)
- → [12. Services](./12-services.md)
- → [16. Forms](./16-forms.md)
- → [20. HTTP Client](./20-http-client.md)
