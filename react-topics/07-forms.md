# Forms

## Controlled Components

React state is the "single source of truth" for form values.

```jsx
function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log({ email, password });
  };

  return (
    <form onSubmit={handleSubmit}>
      <input 
        type="email" 
        value={email}                          // Value from state
        onChange={(e) => setEmail(e.target.value)}  // Update state on change
      />
      <input 
        type="password" 
        value={password} 
        onChange={(e) => setPassword(e.target.value)} 
      />
      <button type="submit">Login</button>
    </form>
  );
}
```

---

## Uncontrolled Components

DOM manages the form data. Use `ref` to access values.

```jsx
function LoginForm() {
  const emailRef = useRef();
  const passwordRef = useRef();

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log({
      email: emailRef.current.value,
      password: passwordRef.current.value,
    });
  };

  return (
    <form onSubmit={handleSubmit}>
      <input type="email" ref={emailRef} defaultValue="" />
      <input type="password" ref={passwordRef} />
      <button type="submit">Login</button>
    </form>
  );
}
```

---

## Controlled vs Uncontrolled

| Aspect | Controlled | Uncontrolled |
|--------|-----------|--------------|
| Source of truth | React state | DOM |
| Value access | state variable | ref.current.value |
| Initial value | `value` prop | `defaultValue` prop |
| Re-renders | On every keystroke | No re-render on input |
| Validation | Real-time | On submit |
| When to use | Most cases | Simple forms, file inputs |

---

## Input Types

### Text Input
```jsx
<input type="text" value={name} onChange={(e) => setName(e.target.value)} />
```

### Textarea
```jsx
// In React, textarea uses value prop (unlike HTML which uses children)
<textarea value={bio} onChange={(e) => setBio(e.target.value)} rows={5} />
```

### Select Dropdown
```jsx
<select value={country} onChange={(e) => setCountry(e.target.value)}>
  <option value="">Select country</option>
  <option value="us">United States</option>
  <option value="uk">United Kingdom</option>
  <option value="in">India</option>
</select>
```

### Checkbox
```jsx
<input 
  type="checkbox" 
  checked={isAgreed}           // checked, not value!
  onChange={(e) => setIsAgreed(e.target.checked)}
/>
```

### Radio Buttons
```jsx
function GenderSelect() {
  const [gender, setGender] = useState('');

  return (
    <div>
      <label>
        <input type="radio" value="male" 
               checked={gender === 'male'}
               onChange={(e) => setGender(e.target.value)} />
        Male
      </label>
      <label>
        <input type="radio" value="female" 
               checked={gender === 'female'}
               onChange={(e) => setGender(e.target.value)} />
        Female
      </label>
    </div>
  );
}
```

---

## Multiple Inputs with Single Handler

```jsx
function RegistrationForm() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    age: '',
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  return (
    <form>
      <input name="name" value={form.name} onChange={handleChange} />
      <input name="email" value={form.email} onChange={handleChange} />
      <input name="age" value={form.age} onChange={handleChange} type="number" />
    </form>
  );
}
```

---

## Form Validation

```jsx
function SignupForm() {
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});

  const validate = () => {
    const newErrors = {};
    if (!form.email) newErrors.email = 'Email is required';
    else if (!/\S+@\S+\.\S+/.test(form.email)) newErrors.email = 'Invalid email';
    if (!form.password) newErrors.password = 'Password is required';
    else if (form.password.length < 8) newErrors.password = 'Min 8 characters';
    return newErrors;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const validationErrors = validate();
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }
    // Submit form
  };

  return (
    <form onSubmit={handleSubmit}>
      <input value={form.email} onChange={e => setForm({...form, email: e.target.value})} />
      {errors.email && <span className="error">{errors.email}</span>}
      
      <input type="password" value={form.password} 
             onChange={e => setForm({...form, password: e.target.value})} />
      {errors.password && <span className="error">{errors.password}</span>}
      
      <button type="submit">Sign Up</button>
    </form>
  );
}
```

---

## File Upload

```jsx
function FileUpload() {
  const [file, setFile] = useState(null);

  const handleFileChange = (e) => {
    setFile(e.target.files[0]);  // File input is always uncontrolled
  };

  const handleUpload = async () => {
    const formData = new FormData();
    formData.append('file', file);
    await fetch('/api/upload', { method: 'POST', body: formData });
  };

  return (
    <div>
      <input type="file" onChange={handleFileChange} accept="image/*" />
      {file && <p>Selected: {file.name} ({(file.size / 1024).toFixed(1)} KB)</p>}
      <button onClick={handleUpload} disabled={!file}>Upload</button>
    </div>
  );
}
```

---

## Form Libraries

### React Hook Form
```jsx
import { useForm } from 'react-hook-form';

function LoginForm() {
  const { register, handleSubmit, formState: { errors } } = useForm();

  const onSubmit = (data) => console.log(data);

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register('email', { required: 'Email required' })} />
      {errors.email && <span>{errors.email.message}</span>}

      <input type="password" {...register('password', { 
        required: 'Password required',
        minLength: { value: 8, message: 'Min 8 chars' }
      })} />
      {errors.password && <span>{errors.password.message}</span>}

      <button type="submit">Login</button>
    </form>
  );
}
```

### Why React Hook Form?
- Minimal re-renders (uncontrolled internally)
- Built-in validation
- Small bundle size
- Easy integration with UI libraries

---

## Key Interview Questions

**Q: Why prefer controlled components?**
> You have full control over form data in React state. Enables real-time validation, conditional disabling, formatting input, and computed values. The React state is always the source of truth.

**Q: When would you use an uncontrolled component?**
> File inputs (must be uncontrolled), integrating with non-React code, simple forms where you only need values on submit, performance-critical forms with many fields.

**Q: How do you handle form state for complex forms?**
> Options: useReducer for complex state logic, form libraries (React Hook Form, Formik), or a single state object with computed field name handler.

**Q: What's the difference between `value` and `defaultValue`?**
> `value` makes it controlled (React controls the value, need onChange). `defaultValue` makes it uncontrolled (DOM manages value, initial value only).
