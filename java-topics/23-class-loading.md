# 23. Class Loading

---

## Theory

Class loading is the process by which the JVM finds, loads, and prepares classes for execution. It follows a **delegation model** where child classloaders delegate to parents first.

### Class Loading Phases

```
Loading → Linking → Initialization
              │
              ├── Verification
              ├── Preparation
              └── Resolution
```

1. **Loading** — find and read `.class` file bytecode
2. **Verification** — ensure bytecode is valid and safe
3. **Preparation** — allocate memory for static fields (default values)
4. **Resolution** — resolve symbolic references to direct references
5. **Initialization** — execute static initializers and static blocks

---

## Internal Working

### ClassLoader Hierarchy

```
Bootstrap ClassLoader (native C/C++)
│   Loads: java.lang.*, java.util.*, etc. (core Java)
│   Path: $JAVA_HOME/lib (rt.jar in Java 8, jrt:/ in Java 9+)
│
├── Platform ClassLoader (Java 9+, was Extension ClassLoader)
│   │   Loads: java.sql.*, javax.*, etc.
│   │   Path: $JAVA_HOME/lib/ext (Java 8) or platform modules
│   │
│   └── Application ClassLoader (System ClassLoader)
│       │   Loads: your application classes
│       │   Path: classpath (-cp, CLASSPATH, -jar)
│       │
│       └── Custom ClassLoaders
│               E.g., Spring's classloader, Tomcat's WebAppClassLoader
```

### Parent Delegation Model

```
Class requested: "com.myapp.Service"

Application ClassLoader: "Can I load this?"
    ↓ delegates to parent FIRST
Platform ClassLoader: "Can I load this?"
    ↓ delegates to parent FIRST
Bootstrap ClassLoader: "Can I load this?"
    ↓ Not found in core Java
Platform ClassLoader: tries own paths → Not found
Application ClassLoader: tries classpath → FOUND! Loads class.

Why delegation?
1. Security: prevents replacing core classes (java.lang.String)
2. Uniqueness: same class loaded only once per classloader
3. Visibility: child can see parent's classes, not vice versa
```

---

## Diagram

```
Class Loading Flow:

loadClass("com.example.MyClass")
         │
         ▼
┌─────────────────────────────────┐
│ 1. Check if already loaded      │ → Yes → return cached Class
│    (findLoadedClass)             │
└─────────────────────────────────┘
         │ No
         ▼
┌─────────────────────────────────┐
│ 2. Delegate to parent           │ → parent.loadClass()
│    (Parent Delegation)           │
└─────────────────────────────────┘
         │ Parent can't load (ClassNotFoundException)
         ▼
┌─────────────────────────────────┐
│ 3. Try self (findClass)         │ → Read .class bytes
│    Load bytecode                 │ → defineClass()
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ 4. Linking                       │
│    - Verify bytecode             │
│    - Prepare (static defaults)   │
│    - Resolve references          │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ 5. Initialization                │
│    - Run <clinit> (static init)  │
│    - Execute static blocks       │
└─────────────────────────────────┘
```

---

## Code Examples

### Custom ClassLoader

```java
public class HotReloadClassLoader extends ClassLoader {
    private final String classPath;
    
    public HotReloadClassLoader(String classPath, ClassLoader parent) {
        super(parent);
        this.classPath = classPath;
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            String fileName = classPath + "/" + name.replace('.', '/') + ".class";
            byte[] bytes = Files.readAllBytes(Path.of(fileName));
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}

// Usage: hot-reload a class
HotReloadClassLoader loader = new HotReloadClassLoader("/path/to/classes", 
    ClassLoader.getSystemClassLoader());
Class<?> clazz = loader.loadClass("com.example.Plugin");
Object instance = clazz.getDeclaredConstructor().newInstance();
```

### Observing Class Loading

```java
// Get classloader of a class
ClassLoader cl = String.class.getClassLoader();
// null → means Bootstrap ClassLoader (native)

ClassLoader appCl = MyClass.class.getClassLoader();
// sun.misc.Launcher$AppClassLoader (Java 8) or jdk.internal.loader.ClassLoaders$AppClassLoader

// Verbose class loading
// Run with: -verbose:class or -Xlog:class+load=info
```

### Class Identity

```java
// Two classes loaded by DIFFERENT classloaders are DIFFERENT types!
ClassLoader cl1 = new URLClassLoader(new URL[]{url});
ClassLoader cl2 = new URLClassLoader(new URL[]{url});

Class<?> class1 = cl1.loadClass("com.example.MyClass");
Class<?> class2 = cl2.loadClass("com.example.MyClass");

class1 == class2;  // FALSE! Different classloaders = different classes
class1.equals(class2);  // FALSE!

// This is why instanceof fails across classloaders
// And why ClassCastException can happen with "same" class name
```

### Static Initialization Order

```java
public class InitOrder {
    static int x = 10;                          // 1st: field initializer
    static int y;                               // 2nd: default value (0)
    
    static {                                    // 3rd: static block
        y = x * 2;                              // y = 20
        System.out.println("Static init: x=" + x + " y=" + y);
    }
    
    static int z = method();                    // 4th: method call
    
    static int method() {
        return x + y;                           // z = 30
    }
}

// Class initialization happens ONCE, on first:
// - new Instance()
// - Static field access
// - Static method call
// - Reflection (Class.forName)
// - Subclass initialization
```

---

## Dry Run

### Initialization Trigger

```java
class Parent {
    static { System.out.println("Parent init"); }
}

class Child extends Parent {
    static { System.out.println("Child init"); }
    static int value = 42;
}

// Scenario 1: new Child()
// Output: "Parent init" → "Child init"
// Both initialized (child triggers parent first)

// Scenario 2: Child.value
// Output: "Parent init" → "Child init"
// Accessing child's static field initializes both

// Scenario 3: Child[] arr = new Child[10];
// Output: (nothing!)
// Array creation does NOT trigger initialization

// Scenario 4: Class.forName("Child")
// Output: "Parent init" → "Child init"
// forName triggers initialization by default

// Scenario 5: Class.forName("Child", false, classLoader)
// Output: (nothing!)
// initialize=false → loading without initialization
```

---

## Interview Questions and Answers

### Q1: What is the parent delegation model? Why does it exist?

**A:** Each classloader delegates to its parent before trying to load itself. Exists for:
1. **Security** — can't replace `java.lang.String` with a malicious version
2. **Uniqueness** — class loaded only once (by the highest capable loader)
3. **Consistency** — core classes are always the same instance

### Q2: Can two classes with the same name exist in JVM?

**A:** Yes! If loaded by different classloaders. A class's identity is `(fully-qualified-name, classloader)`. This is how:
- Servlet containers isolate web apps
- OSGi supports multiple versions of the same library
- Hot-reload frameworks work

### Q3: What is a ClassLoader leak?

**A:** When a custom classloader (and all classes it loaded) cannot be GC'd because something still references it. Common in web containers (hot-deploy):
- ThreadLocal holding reference to loaded class
- Static field referencing loaded class
- Registered but never-unregistered listeners

Results in Metaspace OOM over multiple redeploys.

### Q4: When is a class initialized?

**A:** On first active use:
1. `new` instance creation
2. Static field access (non-constant)
3. Static method invocation
4. Reflection (`Class.forName` with initialize=true)
5. Subclass initialization triggers parent

**NOT triggered by:** Array creation, constant field access (`static final` compile-time constant), `Class.forName(name, false, cl)`.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Not understanding class identity | ClassCastException with "same" class | Ensure single classloader |
| Static initialization dependencies | Circular dependency or NPE | Keep static init simple |
| ClassLoader leak in containers | Metaspace OOM on redeploy | Clean up references on undeploy |
| Breaking parent delegation | SecurityException or conflicts | Delegate to parent first |

---

## Best Practices

1. **Respect parent delegation** — only break it with good reason (e.g., plugin isolation)
2. **Avoid complex static initializers** — they run once and can't be retried on failure
3. **Clean up on undeploy** — remove ThreadLocals, deregister drivers, clear static refs
4. **Use module system (Java 9+)** — stronger encapsulation than classloader tricks
5. **Monitor Metaspace** — classloader leaks show as growing Metaspace

---

## Related Topics

- [20. JVM Internals](./20-jvm-internals.md) — JVM architecture
- [25. Reflection](./25-reflection.md) — loads and inspects classes at runtime
- [26. Annotations](./26-annotations.md) — metadata processed via reflection
