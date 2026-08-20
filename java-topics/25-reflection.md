# 25. Reflection

## Theory

Reflection is a feature in Java that allows a program to examine and modify its own structure and behavior at runtime. Using reflection, you can:
- Inspect classes, interfaces, fields, and methods at runtime without knowing their names at compile time
- Create new instances of classes dynamically
- Get and set field values on objects
- Invoke methods on objects
- Modify access modifiers (access private members)

Reflection is the backbone of many frameworks (Spring, Hibernate, JUnit, Jackson) that need to work with user-defined classes without compile-time knowledge of them.

### When to Use Reflection
- Frameworks and libraries that work with arbitrary user classes
- Dependency injection containers (Spring)
- ORM mapping (Hibernate)
- Serialization/deserialization (Jackson, Gson)
- Testing frameworks (JUnit, Mockito)
- Plugin architectures
- IDE features (code completion, refactoring)

### When NOT to Use Reflection
- Normal application code (use polymorphism instead)
- Performance-critical paths
- When compile-time type safety is available

---

## Internal Working

### How Reflection Works

```
Java Source Code
      ↓ (compile)
.class file (bytecode + metadata)
      ↓ (class loading)
Class object in JVM memory
      ↓ (reflection API)
Inspect/Modify at runtime

Class object contains:
├── Class name, modifiers, package
├── Superclass reference
├── Implemented interfaces
├── Constructors (Constructor[])
├── Methods (Method[])
├── Fields (Field[])
├── Annotations (Annotation[])
└── Inner classes
```

### Class Loading and Reflection

```
1. ClassLoader loads .class file
      ↓
2. JVM creates java.lang.Class object
      ↓
3. Class object stores complete metadata:
   - All declared fields (including private)
   - All declared methods (including private)
   - All declared constructors
   - Annotations
   - Generic type information
      ↓
4. Reflection API provides access to this metadata
```

### Performance Impact

```
Direct method call:
  Thread → Stack Frame → Method Bytecode → Execute
  (few nanoseconds)

Reflective method call:
  Thread → Method.invoke() 
    → Security check (access control)
    → Parameter boxing/unboxing
    → Method resolution
    → Native invocation
    → Execute
  (10-100x slower initially, JIT may optimize to ~2-3x)
```

---

## Diagram

### Reflection API Class Hierarchy

```
java.lang.Class<T>
├── getConstructors() → Constructor<T>[]
├── getDeclaredConstructors() → Constructor<T>[]
├── getMethods() → Method[]
├── getDeclaredMethods() → Method[]
├── getFields() → Field[]
├── getDeclaredFields() → Field[]
├── getAnnotations() → Annotation[]
├── getSuperclass() → Class<?>
├── getInterfaces() → Class<?>[]
└── newInstance() (deprecated) → T

java.lang.reflect.Constructor<T>
├── newInstance(Object... args) → T
├── getParameterTypes() → Class<?>[]
└── setAccessible(boolean)

java.lang.reflect.Method
├── invoke(Object obj, Object... args) → Object
├── getReturnType() → Class<?>
├── getParameterTypes() → Class<?>[]
└── setAccessible(boolean)

java.lang.reflect.Field
├── get(Object obj) → Object
├── set(Object obj, Object value)
├── getType() → Class<?>
└── setAccessible(boolean)
```

### getXxx() vs getDeclaredXxx()

```
getMethods()         → public methods (including inherited)
getDeclaredMethods() → all methods declared in THIS class (including private)
                       (does NOT include inherited methods)

getFields()          → public fields (including inherited)
getDeclaredFields()  → all fields declared in THIS class (including private)

getConstructors()       → public constructors
getDeclaredConstructors() → all constructors (including private)
```

---

## Code

### Getting Class Object

```java
public class ReflectionBasics {
    public static void main(String[] args) throws Exception {
        // Three ways to get Class object
        
        // 1. Using .class literal (compile-time)
        Class<String> clazz1 = String.class;
        
        // 2. Using getClass() on an instance (runtime)
        String str = "hello";
        Class<?> clazz2 = str.getClass();
        
        // 3. Using Class.forName() with fully qualified name (runtime)
        Class<?> clazz3 = Class.forName("java.lang.String");
        
        // All three refer to the same Class object
        System.out.println(clazz1 == clazz2); // true
        System.out.println(clazz2 == clazz3); // true
    }
}
```

### Inspecting a Class

```java
import java.lang.reflect.*;

public class ClassInspector {
    public static void inspect(Class<?> clazz) {
        // Basic info
        System.out.println("Name: " + clazz.getName());
        System.out.println("Simple Name: " + clazz.getSimpleName());
        System.out.println("Package: " + clazz.getPackageName());
        System.out.println("Is Interface: " + clazz.isInterface());
        System.out.println("Is Abstract: " + Modifier.isAbstract(clazz.getModifiers()));
        System.out.println("Superclass: " + clazz.getSuperclass());
        
        // Interfaces
        System.out.println("\nInterfaces:");
        for (Class<?> iface : clazz.getInterfaces()) {
            System.out.println("  " + iface.getName());
        }
        
        // Fields
        System.out.println("\nDeclared Fields:");
        for (Field field : clazz.getDeclaredFields()) {
            System.out.printf("  %s %s %s%n",
                Modifier.toString(field.getModifiers()),
                field.getType().getSimpleName(),
                field.getName());
        }
        
        // Methods
        System.out.println("\nDeclared Methods:");
        for (Method method : clazz.getDeclaredMethods()) {
            System.out.printf("  %s %s %s(%s)%n",
                Modifier.toString(method.getModifiers()),
                method.getReturnType().getSimpleName(),
                method.getName(),
                paramTypesToString(method.getParameterTypes()));
        }
        
        // Constructors
        System.out.println("\nConstructors:");
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            System.out.printf("  %s(%s)%n",
                clazz.getSimpleName(),
                paramTypesToString(ctor.getParameterTypes()));
        }
    }
    
    private static String paramTypesToString(Class<?>[] params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(params[i].getSimpleName());
        }
        return sb.toString();
    }
}
```

### Creating Instances Dynamically

```java
import java.lang.reflect.Constructor;

public class DynamicInstantiation {
    
    static class Employee {
        private String name;
        private int age;
        
        public Employee() {
            this.name = "Unknown";
            this.age = 0;
        }
        
        public Employee(String name, int age) {
            this.name = name;
            this.age = age;
        }
        
        @Override
        public String toString() {
            return "Employee{name='" + name + "', age=" + age + "}";
        }
    }
    
    public static void main(String[] args) throws Exception {
        Class<Employee> clazz = Employee.class;
        
        // Using no-arg constructor
        Constructor<Employee> noArgCtor = clazz.getDeclaredConstructor();
        Employee emp1 = noArgCtor.newInstance();
        System.out.println(emp1); // Employee{name='Unknown', age=0}
        
        // Using parameterized constructor
        Constructor<Employee> paramCtor = 
            clazz.getDeclaredConstructor(String.class, int.class);
        Employee emp2 = paramCtor.newInstance("John", 30);
        System.out.println(emp2); // Employee{name='John', age=30}
    }
}
```

### Accessing Private Fields

```java
import java.lang.reflect.Field;

public class PrivateFieldAccess {
    
    static class SecretHolder {
        private String secret = "classified";
        private final int id = 42;
    }
    
    public static void main(String[] args) throws Exception {
        SecretHolder holder = new SecretHolder();
        Class<?> clazz = holder.getClass();
        
        // Access private field
        Field secretField = clazz.getDeclaredField("secret");
        secretField.setAccessible(true); // Bypass access control
        
        // Read private field
        String value = (String) secretField.get(holder);
        System.out.println("Secret: " + value); // "classified"
        
        // Modify private field
        secretField.set(holder, "exposed");
        System.out.println("Modified: " + secretField.get(holder)); // "exposed"
        
        // Even final fields can be modified (with caveats)
        Field idField = clazz.getDeclaredField("id");
        idField.setAccessible(true);
        // Note: modifying final fields is unreliable in modern JVMs
        // JIT compiler may inline final field values
    }
}
```

### Invoking Methods Dynamically

```java
import java.lang.reflect.Method;

public class DynamicMethodInvocation {
    
    static class Calculator {
        public int add(int a, int b) { return a + b; }
        public int multiply(int a, int b) { return a * b; }
        private int secret(int x) { return x * 42; }
    }
    
    public static void main(String[] args) throws Exception {
        Calculator calc = new Calculator();
        Class<?> clazz = calc.getClass();
        
        // Invoke public method
        Method addMethod = clazz.getMethod("add", int.class, int.class);
        Object result = addMethod.invoke(calc, 5, 3);
        System.out.println("5 + 3 = " + result); // 8
        
        // Invoke another method by name
        String operation = "multiply"; // Could come from config/user input
        Method opMethod = clazz.getMethod(operation, int.class, int.class);
        result = opMethod.invoke(calc, 4, 6);
        System.out.println("4 * 6 = " + result); // 24
        
        // Invoke private method
        Method secretMethod = clazz.getDeclaredMethod("secret", int.class);
        secretMethod.setAccessible(true);
        result = secretMethod.invoke(calc, 10);
        System.out.println("secret(10) = " + result); // 420
    }
}
```

### Simulating Spring Dependency Injection

```java
import java.lang.reflect.*;
import java.lang.annotation.*;

// Custom annotation (like Spring's @Autowired)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface Inject {}

// Service classes
class UserRepository {
    public String findUser(int id) { return "User_" + id; }
}

class EmailService {
    public void send(String to, String msg) {
        System.out.println("Sending '" + msg + "' to " + to);
    }
}

class UserService {
    @Inject
    private UserRepository userRepository;
    
    @Inject
    private EmailService emailService;
    
    public void notifyUser(int userId) {
        String user = userRepository.findUser(userId);
        emailService.send(user, "Welcome!");
    }
}

// Simple DI container using reflection
public class SimpleContainer {
    
    public static <T> T createInstance(Class<T> clazz) throws Exception {
        // Create instance
        T instance = clazz.getDeclaredConstructor().newInstance();
        
        // Inject dependencies
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                field.setAccessible(true);
                // Create dependency instance (recursive)
                Object dependency = field.getType().getDeclaredConstructor().newInstance();
                field.set(instance, dependency);
                System.out.println("Injected " + field.getType().getSimpleName() 
                    + " into " + clazz.getSimpleName() + "." + field.getName());
            }
        }
        
        return instance;
    }
    
    public static void main(String[] args) throws Exception {
        UserService userService = createInstance(UserService.class);
        userService.notifyUser(1);
        // Output:
        // Injected UserRepository into UserService.userRepository
        // Injected EmailService into UserService.emailService
        // Sending 'Welcome!' to User_1
    }
}
```

### Working with Generics via Reflection

```java
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;

public class GenericReflection {
    
    private List<String> names;
    private Map<Integer, List<String>> mapping;
    
    public static void main(String[] args) throws Exception {
        // Get generic type information for fields
        Field namesField = GenericReflection.class.getDeclaredField("names");
        Type genericType = namesField.getGenericType();
        
        if (genericType instanceof ParameterizedType pt) {
            System.out.println("Raw type: " + pt.getRawType()); // List
            Type[] typeArgs = pt.getActualTypeArguments();
            System.out.println("Type arg: " + typeArgs[0]); // String
        }
        
        // Nested generics
        Field mapField = GenericReflection.class.getDeclaredField("mapping");
        Type mapType = mapField.getGenericType();
        
        if (mapType instanceof ParameterizedType pt) {
            System.out.println("\nMap type args:");
            for (Type arg : pt.getActualTypeArguments()) {
                System.out.println("  " + arg);
            }
            // Integer
            // java.util.List<java.lang.String>
        }
    }
}
```

---

## Dry Run

### Reflective Method Invocation

```
Given: Calculator calc = new Calculator(); with method add(int, int)

Step 1: clazz.getMethod("add", int.class, int.class)
  → JVM searches method table for method named "add" with params (int, int)
  → Creates Method object wrapping the method metadata
  → Returns Method reference

Step 2: addMethod.invoke(calc, 5, 3)
  → Security check: is caller allowed to invoke? (yes, it's public)
  → Box primitive arguments: 5 → Integer(5), 3 → Integer(3)
  → Determine actual method to call (virtual dispatch)
  → Invoke underlying method: calc.add(5, 3)
  → Box return value: 8 → Integer(8)
  → Return Object (Integer with value 8)
```

### Dependency Injection Simulation

```
createInstance(UserService.class)

Step 1: clazz.getDeclaredConstructor().newInstance()
  → Find no-arg constructor of UserService
  → Create new UserService instance (fields are null)

Step 2: Iterate declared fields
  Field: userRepository (has @Inject)
    → setAccessible(true)  (bypass private)
    → Create UserRepository instance
    → field.set(userService, userRepositoryInstance)
  
  Field: emailService (has @Inject)
    → setAccessible(true)
    → Create EmailService instance
    → field.set(userService, emailServiceInstance)

Step 3: Return fully-wired UserService
```

---

## Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| Class.forName() | O(1) amortized | Cached after first load |
| getMethod() | O(n) | n = number of methods (linear search) |
| getDeclaredField() | O(n) | n = number of declared fields |
| Method.invoke() | ~2-3x direct call | After JIT optimization (first calls much slower) |
| Field.get/set() | ~2-3x direct access | After JIT optimization |
| newInstance() | ~3-5x new keyword | Constructor + security checks |
| setAccessible(true) | O(1) | One-time cost per AccessibleObject |

---

## Real Project Usage

### 1. Spring Framework — Bean Creation

```java
// Simplified Spring bean factory logic
public class BeanFactory {
    private Map<String, Object> beans = new HashMap<>();
    
    public Object getBean(String className) throws Exception {
        if (beans.containsKey(className)) {
            return beans.get(className);
        }
        
        Class<?> clazz = Class.forName(className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        
        // Inject @Autowired fields
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                field.setAccessible(true);
                Object dependency = getBean(field.getType().getName());
                field.set(instance, dependency);
            }
        }
        
        beans.put(className, instance);
        return instance;
    }
}
```

### 2. Jackson — JSON Deserialization

```java
// Simplified Jackson-like deserialization
public class SimpleJsonMapper {
    
    public <T> T fromJson(String json, Class<T> clazz) throws Exception {
        Map<String, String> jsonMap = parseJson(json);
        T instance = clazz.getDeclaredConstructor().newInstance();
        
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String jsonValue = jsonMap.get(field.getName());
            if (jsonValue != null) {
                Object converted = convert(jsonValue, field.getType());
                field.set(instance, converted);
            }
        }
        
        return instance;
    }
    
    private Object convert(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        return value;
    }
}
```

### 3. JUnit — Test Discovery and Execution

```java
// Simplified JUnit-like test runner
public class TestRunner {
    
    public void runTests(Class<?> testClass) throws Exception {
        Object testInstance = testClass.getDeclaredConstructor().newInstance();
        
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                System.out.print("Running " + method.getName() + "... ");
                try {
                    method.invoke(testInstance);
                    System.out.println("PASSED");
                } catch (InvocationTargetException e) {
                    System.out.println("FAILED: " + e.getCause().getMessage());
                }
            }
        }
    }
}
```

---

## Interview Questions and Answers

### Q1: What is Reflection in Java?
**A**: Reflection is a runtime API that allows examining and manipulating classes, methods, fields, and constructors at runtime without knowing them at compile time. It's provided by the `java.lang.reflect` package and is fundamental to frameworks like Spring (dependency injection), Hibernate (ORM mapping), and JUnit (test discovery).

### Q2: How does Spring use Reflection?
**A**: Spring uses reflection extensively for:
- **Bean instantiation**: Creating objects from class names in configuration
- **Dependency injection**: Scanning fields for `@Autowired` and setting them
- **AOP proxies**: Examining methods for annotations like `@Transactional`
- **Component scanning**: Finding classes annotated with `@Component`, `@Service`, etc.
- **Request mapping**: Matching HTTP requests to `@RequestMapping` methods

### Q3: What is the difference between getMethod() and getDeclaredMethod()?
**A**: 
- `getMethod()` returns public methods including those inherited from superclasses and interfaces
- `getDeclaredMethod()` returns all methods declared in the class itself (public, protected, default, private) but NOT inherited methods

Same pattern applies to `getField/getDeclaredField` and `getConstructor/getDeclaredConstructor`.

### Q4: What is setAccessible(true)?
**A**: It disables Java access control checks for the reflected object (field, method, or constructor). This allows accessing private members. It doesn't change the actual access modifier — it just tells the reflection API to skip the access check. Security managers can restrict this ability.

### Q5: What are the disadvantages of Reflection?
**A**:
- **Performance**: 2-100x slower than direct calls (security checks, boxing, no JIT inlining)
- **Type safety lost**: No compile-time checking, errors appear at runtime
- **Breaks encapsulation**: Can access private members, violating design intent
- **Fragile**: Renaming a field/method breaks reflective code silently (no compile error)
- **Security restrictions**: May fail under security managers or Java module system

### Q6: How does the Java Module System (Java 9+) affect Reflection?
**A**: The module system restricts reflection by default. You cannot use `setAccessible(true)` on members of unexported packages in other modules. You must either:
- `exports` the package in module-info.java
- `opens` the package for deep reflection
- Use `--add-opens` JVM argument (workaround)

This is why Spring Boot applications often need `--add-opens` flags on Java 17+.

---

## Follow-up Questions and Answers

### Q: Can you modify final fields with reflection?
**A**: Technically yes with `setAccessible(true)`, but it's unreliable in modern JVMs. The JIT compiler may inline final field values at compile time, so modifications via reflection may not be seen by other code. Java 12+ adds further restrictions. This should never be done in production code.

### Q: What are MethodHandles and how do they compare to reflection?
**A**: `java.lang.invoke.MethodHandle` is a more performant alternative to reflection introduced in Java 7. Unlike reflection, method handles can be fully optimized by the JIT compiler once resolved. They're used internally by lambda expressions and `invokedynamic`. For repeated invocations, MethodHandles significantly outperform Method.invoke().

### Q: How do Proxy and InvocationHandler work?
**A**: `java.lang.reflect.Proxy` creates dynamic proxy instances implementing specified interfaces at runtime. All method calls on the proxy are routed to an `InvocationHandler.invoke()` method. This is how Spring creates AOP proxies for interfaces, and how mocking frameworks like Mockito intercept method calls.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using `getMethod()` for private methods | `NoSuchMethodException` | Use `getDeclaredMethod()` + `setAccessible(true)` |
| Forgetting `setAccessible(true)` | `IllegalAccessException` on private members | Always call setAccessible for non-public members |
| Wrong parameter types in `getMethod()` | `NoSuchMethodException` | Match exact parameter types (int.class not Integer.class for primitives) |
| Invoking non-static method without instance | `NullPointerException` | Pass object instance as first arg to `invoke()` |
| Catching `Exception` from `invoke()` | Hides actual exception | Catch `InvocationTargetException`, call `getCause()` |
| Using reflection in tight loops | Severe performance issues | Cache Method/Field objects, or use MethodHandles |

---

## Best Practices

1. **Cache reflected objects** — `Method`, `Field`, `Constructor` lookups are expensive; do them once
2. **Use MethodHandles for repeated calls** — Better performance than Method.invoke()
3. **Handle InvocationTargetException properly** — Unwrap with `getCause()` to get the actual exception
4. **Prefer compile-time type safety** — Use reflection only when polymorphism is insufficient
5. **Respect module boundaries** — Use `opens` in module-info.java rather than `--add-opens` hacks
6. **Validate reflective targets** — Check method/field exists before calling to provide better error messages
7. **Use generics with Class<T>** — Maintains type safety: `Class<Employee>` → `Constructor<Employee>` → `Employee`

---

## Production Considerations

- **Startup vs Runtime**: Heavy reflection at startup (Spring context initialization) is acceptable; reflection in request paths hurts throughput
- **GraalVM Native Image**: Reflection must be explicitly configured for ahead-of-time compilation. Spring Native and Quarkus handle this via build-time processing
- **Security**: In containerized environments, security managers are rare, but module system restrictions still apply
- **Debugging difficulty**: Reflective stack traces are harder to read; framework stack traces often include many reflective frames
- **Alternative approaches**: Consider compile-time code generation (annotation processors) for performance-critical scenarios

---

## Related Topics

- [26. Annotations](./26-annotations.md)
- [23. Class Loading](./23-class-loading.md)
- [20. JVM Internals](./20-jvm-internals.md)
- [02. OOP Concepts](./02-oop-concepts.md)
