# 16. Forms — Must Know

---

## Theory

Angular provides two approaches to forms: Template-driven (simple, two-way binding) and Reactive (programmatic, scalable). **Reactive forms are preferred for production applications.**

### Template-Driven vs Reactive Forms

| Feature | Template-Driven | Reactive |
|---------|----------------|----------|
| Setup | FormsModule | ReactiveFormsModule |
| Logic location | Template (HTML) | Component (TypeScript) |
| Data model | Implicit (ngModel) | Explicit (FormGroup) |
| Validation | Template directives | Functions in code |
| Testability | Hard (need DOM) | Easy (pure functions) |
| Dynamic forms | Difficult | Easy (FormArray) |
| Scalability | Small forms | Complex forms |
| Immutability | Mutable | Immutable (value snapshots) |

### Template-Driven Forms

```typescript
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form #loginForm="ngForm" (ngSubmit)="onSubmit(loginForm)">
      <input type="email" name="email" [(ngModel)]="user.email" required email #emailField="ngModel">
      @if (emailField.invalid && emailField.touched) {
        <span class="error">Valid email required</span>
      }
      
      <input type="password" name="password" [(ngModel)]="user.password" required minlength="6">
      
      <button type="submit" [disabled]="loginForm.invalid">Login</button>
    </form>
  `
})
export class LoginFormComponent {
  user = { email: '', password: '' };

  onSubmit(form: NgForm): void {
    if (form.valid) {
      console.log('Form data:', this.user);
    }
  }
}
```

### Reactive Forms — Complete

```typescript
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule],
  template: `
    <form [formGroup]="employeeForm" (ngSubmit)="onSubmit()">
      <div class="form-group">
        <label>Name</label>
        <input formControlName="name">
        @if (f['name'].invalid && f['name'].touched) {
          @if (f['name'].errors?.['required']) { <span>Name is required</span> }
          @if (f['name'].errors?.['minlength']) { <span>Min 2 characters</span> }
        }
      </div>

      <div class="form-group">
        <label>Email</label>
        <input formControlName="email" type="email">
        @if (f['email'].errors?.['email'] && f['email'].touched) {
          <span>Invalid email</span>
        }
      </div>

      <div class="form-group">
        <label>Department</label>
        <select formControlName="department">
          <option value="">Select...</option>
          @for (dept of departments; track dept) {
            <option [value]="dept">{{ dept }}</option>
          }
        </select>
      </div>

      <!-- Nested FormGroup -->
      <div formGroupName="address">
        <h4>Address</h4>
        <input formControlName="street" placeholder="Street">
        <input formControlName="city" placeholder="City">
        <input formControlName="zipCode" placeholder="Zip Code">
      </div>

      <!-- FormArray -->
      <div>
        <h4>Skills</h4>
        @for (skill of skills.controls; track $index; let i = $index) {
          <div>
            <input [formControl]="skill">
            <button type="button" (click)="removeSkill(i)">Remove</button>
          </div>
        }
        <button type="button" (click)="addSkill()">Add Skill</button>
      </div>

      <button type="submit" [disabled]="employeeForm.invalid">Save</button>
      <button type="button" (click)="employeeForm.reset()">Reset</button>
    </form>

    <pre>Form Value: {{ employeeForm.value | json }}</pre>
    <pre>Valid: {{ employeeForm.valid }}</pre>
  `
})
export class EmployeeFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  
  employeeForm!: FormGroup;
  departments = ['Engineering', 'Marketing', 'Sales', 'HR'];

  ngOnInit(): void {
    this.employeeForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      department: ['', Validators.required],
      salary: [0, [Validators.required, Validators.min(30000)]],
      address: this.fb.group({
        street: [''],
        city: ['', Validators.required],
        zipCode: ['', [Validators.required, Validators.pattern(/^\d{5}$/)]]
      }),
      skills: this.fb.array([])
    });
  }

  get f() { return this.employeeForm.controls; }
  get skills() { return this.employeeForm.get('skills') as FormArray; }

  addSkill(): void {
    this.skills.push(this.fb.control('', Validators.required));
  }

  removeSkill(index: number): void {
    this.skills.removeAt(index);
  }

  onSubmit(): void {
    if (this.employeeForm.valid) {
      const formValue = this.employeeForm.value;
      console.log('Submit:', formValue);
    } else {
      // Mark all fields as touched to show errors
      this.employeeForm.markAllAsTouched();
    }
  }
}
```

### Custom Validators

```typescript
import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

// Sync validator
export function noWhitespace(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (control.value && control.value.trim().length === 0) {
      return { whitespace: true };
    }
    return null;
  };
}

// Password match validator (cross-field)
export function passwordMatchValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return password === confirm ? null : { passwordMismatch: true };
  };
}

// Async validator (check server)
export function uniqueEmailValidator(userService: UserService): AsyncValidatorFn {
  return (control: AbstractControl): Observable<ValidationErrors | null> => {
    return userService.checkEmailAvailable(control.value).pipe(
      map(available => available ? null : { emailTaken: true }),
      catchError(() => of(null))
    );
  };
}

// Usage
this.fb.group({
  name: ['', [Validators.required, noWhitespace()]],
  email: ['', [Validators.required, Validators.email], [uniqueEmailValidator(this.userService)]],
  password: ['', [Validators.required, Validators.minLength(8)]],
  confirmPassword: ['', Validators.required]
}, { validators: passwordMatchValidator() });
```

### Form State

| Property | Meaning |
|----------|---------|
| `valid` | All validators pass |
| `invalid` | At least one validator fails |
| `pristine` | Value never changed by user |
| `dirty` | Value has been changed |
| `touched` | Field has been focused and blurred |
| `untouched` | Field never focused |
| `pending` | Async validator running |

---

## Internal Working

### FormControl Value Changes

```
User types in input:
1. DOM input event fires
2. ControlValueAccessor reads new value
3. FormControl.setValue() called internally
4. Validators run (sync first, then async)
5. FormControl status updated (VALID/INVALID/PENDING)
6. valueChanges Observable emits
7. statusChanges Observable emits
8. Parent FormGroup validity recalculated
```

### FormGroup Structure

```
FormGroup: employeeForm
├── FormControl: name (value: 'John', valid: true)
├── FormControl: email (value: 'j@t.com', valid: true)
├── FormGroup: address
│   ├── FormControl: street
│   ├── FormControl: city
│   └── FormControl: zipCode
└── FormArray: skills
    ├── FormControl [0] (value: 'Angular')
    ├── FormControl [1] (value: 'TypeScript')
    └── FormControl [2] (value: 'Java')
```

---

## Code

```typescript
// Real-world: Dynamic form with conditional validation
@Component({
  selector: 'app-registration',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()">
      <select formControlName="accountType" (change)="onAccountTypeChange()">
        <option value="personal">Personal</option>
        <option value="business">Business</option>
      </select>

      <input formControlName="email" placeholder="Email">
      <input formControlName="name" placeholder="Full Name">

      @if (form.get('accountType')?.value === 'business') {
        <input formControlName="companyName" placeholder="Company Name">
        <input formControlName="taxId" placeholder="Tax ID">
      }
      
      <button [disabled]="form.invalid">Register</button>
    </form>
  `
})
export class RegistrationComponent implements OnInit {
  private fb = inject(FormBuilder);
  form!: FormGroup;

  ngOnInit(): void {
    this.form = this.fb.group({
      accountType: ['personal'],
      email: ['', [Validators.required, Validators.email]],
      name: ['', Validators.required],
      companyName: [''],
      taxId: ['']
    });
  }

  onAccountTypeChange(): void {
    const type = this.form.get('accountType')?.value;
    const companyName = this.form.get('companyName')!;
    const taxId = this.form.get('taxId')!;

    if (type === 'business') {
      companyName.setValidators(Validators.required);
      taxId.setValidators([Validators.required, Validators.pattern(/^\d{9}$/)]);
    } else {
      companyName.clearValidators();
      taxId.clearValidators();
    }
    companyName.updateValueAndValidity();
    taxId.updateValueAndValidity();
  }

  submit(): void {
    if (this.form.valid) {
      console.log(this.form.value);
    }
  }
}
```

---

## Interview Questions and Answers

**Q1: What is the difference between template-driven and reactive forms?**
> Template-driven forms use ngModel and directives in HTML — logic lives in the template. Reactive forms use FormGroup/FormControl in TypeScript — logic lives in the component class. Reactive forms are preferred for complex forms: better testability, dynamic fields, cross-field validation, and programmatic control.

**Q2: How do you implement custom validation?**
> Create a function that takes `AbstractControl` and returns `ValidationErrors | null`. For sync validators: return errors object if invalid, null if valid. For async validators: return `Observable<ValidationErrors | null>`. Cross-field validators are applied at the FormGroup level.

**Q3: What is FormArray and when do you use it?**
> FormArray manages a dynamic list of form controls (like adding/removing skills, phone numbers, addresses). Use `push()` to add controls, `removeAt()` to remove. Each element can be a FormControl, FormGroup, or nested FormArray.

**Q4: How do you handle form state (dirty, touched, valid)?**
> `dirty` = user changed value. `touched` = user focused and left field. `valid` = all validators pass. Show errors only when field is touched AND invalid (avoids showing errors before user interacts). Use `markAllAsTouched()` on submit to force error display.

**Q5: How do you implement conditional validation?**
> Use `setValidators()` / `clearValidators()` when conditions change. Always call `updateValueAndValidity()` after changing validators. Alternative: use a custom validator that checks the condition internally.

---

## Common Mistakes

1. **Showing errors before user interaction**
   ```html
   <!-- ❌ Shows error immediately on page load -->
   @if (f['email'].invalid) { <span>Error</span> }
   
   <!-- ✅ Only after user touches the field -->
   @if (f['email'].invalid && f['email'].touched) { <span>Error</span> }
   ```

2. **Forgetting updateValueAndValidity after changing validators**
3. **Using FormArray without trackBy/track**
4. **Not calling markAllAsTouched on submit**

---

## Best Practices

1. **Use Reactive Forms** for all non-trivial forms.
2. **Show errors** only when touched or on submit.
3. **Use FormBuilder** for cleaner form creation syntax.
4. **Extract validators** into separate files for reuse.
5. **Use `markAllAsTouched()`** on invalid submit attempts.
6. **Type your forms** with TypeScript (Angular 14+ typed forms).
7. **Use `valueChanges`** for reactive filtering/search.

---

## Production Considerations

- **Typed forms** (Angular 14+): `FormGroup<{ name: FormControl<string> }>` provides compile-time safety.
- **Large forms**: Split into sub-components with nested FormGroups.
- **Async validators**: Add debounce to avoid excessive server calls.
- **Accessibility**: Use proper labels, aria attributes, and error announcements.

---

## Related Topics

- → [4. Data Binding](./04-data-binding.md)
- → [8. Component Communication](./08-component-communication.md)
- → [17. RxJS](./17-rxjs.md)
