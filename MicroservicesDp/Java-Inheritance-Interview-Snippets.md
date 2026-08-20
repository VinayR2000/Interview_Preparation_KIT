# Java Inheritance & Polymorphism — Interview Code Snippets

A comprehensive guide covering method overriding, overloading, access modifiers, exception handling in inheritance, constructors, casting, and polymorphism rules. Each snippet includes the code, output, and a clear explanation of the underlying concept.

---

## Table of Contents

1. [Access Modifier Rules in Overriding](#1-access-modifier-rules-in-overriding)
2. [Exception Handling Rules in Overriding](#2-exception-handling-rules-in-overriding)
3. [Method Overloading with null](#3-method-overloading-with-null)
4. [Ambiguous Overloading](#4-ambiguous-overloading)
5. [Static Method Hiding vs Overriding](#5-static-method-hiding-vs-overriding)
6. [Basic Runtime Polymorphism](#6-basic-runtime-polymorphism)
7. [Variable Hiding](#7-variable-hiding)
8. [Constructor Chaining](#8-constructor-chaining)
9. [super() and Parameterized Constructors](#9-super-and-parameterized-constructors)
10. [Calling Parent Method with super](#10-calling-parent-method-with-super)
11. [Upcasting and instanceof](#11-upcasting-and-instanceof)
12. [Downcasting](#12-downcasting)
13. [Invalid Downcasting (ClassCastException)](#13-invalid-downcasting-classcastexception)
14. [Overriding vs Overloading Confusion](#14-overriding-vs-overloading-confusion)
15. [Final Methods](#15-final-methods)
16. [Dynamic Method Dispatch (Multi-Level)](#16-dynamic-method-dispatch-multi-level)
17. [Parent Reference Limitations](#17-parent-reference-limitations)
18. [Upcasting In-Depth](#18-upcasting-in-depth)
19. [Tricky Interview Question: Overloading + Overriding + Upcasting](#19-tricky-interview-question-overloading--overriding--upcasting)
20. [Constructor vs Method with Same Name — Neither Overloading](#20-constructor-vs-method-with-same-name--neither-overloading)
21. [String Pool and == vs equals()](#21-string-pool-and--vs-equals)
22. [Autoboxing and Integer Cache](#22-autoboxing-and-integer-cache)
23. [finally Block vs return](#23-finally-block-vs-return)
24. [Exception in Initializer Block](#24-exception-in-initializer-block)
25. [Covariant Return Types](#25-covariant-return-types)
26. [Method Hiding with Private Methods](#26-method-hiding-with-private-methods)
27. [Interface Default Method Diamond Problem](#27-interface-default-method-diamond-problem)
28. [Tricky equals() and hashCode()](#28-tricky-equals-and-hashcode)
29. [Immutable String Confusion](#29-immutable-string-confusion)
30. [try-with-resources Execution Order](#30-try-with-resources-execution-order)
31. [Static Binding with Overloaded + Overridden Methods](#31-static-binding-with-overloaded--overridden-methods)
32. [Abstract Class with Constructor](#32-abstract-class-with-constructor)
33. [Varargs vs Specific Parameter](#33-varargs-vs-specific-parameter)
34. [Post-Increment in Return Statement](#34-post-increment-in-return-statement)
35. [Unreachable Code and finally](#35-unreachable-code-and-finally)
36. [Encapsulation — Defensive Copying Trap](#36-encapsulation--defensive-copying-trap)
37. [this() Constructor Chaining](#37-this-constructor-chaining)
38. [Multiple Inheritance via Interfaces](#38-multiple-inheritance-via-interfaces)
39. [Shallow Copy vs Deep Copy (Cloneable)](#39-shallow-copy-vs-deep-copy-cloneable)
40. [Object Class Methods — toString() and equals()](#40-object-class-methods--tostring-and-equals)
41. [Inner Classes — Access and Scope Tricks](#41-inner-classes--access-and-scope-tricks)
42. [Enum with Constructor and Methods](#42-enum-with-constructor-and-methods)
43. [Generics Type Erasure](#43-generics-type-erasure)
44. [Composition vs Inheritance](#44-composition-vs-inheritance)
45. [SOLID Principles in Code — Liskov Substitution](#45-solid-principles-in-code--liskov-substitution)
46. [Comparable — Natural Ordering](#46-comparable--natural-ordering)
47. [Comparator — Custom Ordering](#47-comparator--custom-ordering)
48. [Multiple Field Sorting with Comparator Chaining](#48-multiple-field-sorting-with-comparator-chaining)
49. [Comparable vs Comparator — When to Use Which](#49-comparable-vs-comparator--when-to-use-which)
50. [Sorting Traps and Tricky Snippets](#50-sorting-traps-and-tricky-snippets)
51. [Collections.sort() vs List.sort() vs Arrays.sort()](#51-collectionssort-vs-listsort-vs-arrayssort)
52. [Reverse Order, Null Handling, and Natural Order](#52-reverse-order-null-handling-and-natural-order)

---

## 1. Access Modifier Rules in Overriding

### Rule
> When overriding a method, the child class **cannot** assign weaker access privileges than the parent method.

Access hierarchy (strongest → weakest): `public` → `protected` → `default` → `private`

### Snippet

```java
public class Test {
    public static void main(String[] args) {
        A a = new B();
        a.disp();
    }
}

class A {
    public void disp() {
        System.out.println("class A");
    }
}

class B extends A {
    protected void disp() {  // Weaker than public!
        System.out.println("class B");
    }
}
```

### Output
```
Compilation Error: 'disp()' in 'B' clashes with 'disp()' in 'A';
attempting to assign weaker access privileges ('protected'); was 'public'
```

### Explanation
- `A.disp()` is `public`.
- `B.disp()` attempts to reduce visibility to `protected`.
- Java disallows this because it would break the Liskov Substitution Principle — code that expects to call a `public` method via a parent reference would suddenly fail.

---

## 2. Exception Handling Rules in Overriding

### Rule
> An overriding method can throw:
> - The **same** checked exception as the parent
> - A **subclass** (narrower) checked exception
> - **No** checked exception at all
> - Any **unchecked** (Runtime) exception freely
>
> It **cannot** throw a **new or broader** checked exception if the parent doesn't declare one.

### Snippet A — Narrower Checked Exception (Valid)

```java
class A {
    public void disp() throws Exception {
        System.out.println("class A");
    }
}

class B extends A {
    public void disp() throws IOException {  // IOException is subclass of Exception
        System.out.println("class B");
    }
}
```

**Output:** `class B`

**Why it works:** `IOException` is a subclass of `Exception`, so it's a narrower exception — this is allowed.

---

### Snippet B — Adding Checked Exception When Parent Has None (Invalid)

```java
class A {
    public void disp() {
        System.out.println("class A");
    }
}

class B extends A {
    public void disp() throws IOException {  // Parent declares NONE!
        System.out.println("class B");
    }
}
```

**Output:**
```
Compilation Error: 'disp()' in 'B' clashes with 'disp()' in 'A';
overridden method does not throw 'java.io.IOException'
```

**Why it fails:** Parent `disp()` has no `throws` clause. The child cannot introduce a new checked exception.

---

### Snippet C — Unchecked (Runtime) Exception (Valid)

```java
class A {
    public void disp() {
        System.out.println("class A");
    }
}

class B extends A {
    public void disp() throws RuntimeException {  // Unchecked — always allowed
        System.out.println("class B");
    }
}
```

**Output:** `class B`

**Why it works:** `RuntimeException` (and its subclasses) are unchecked — they don't need to be declared and can be added freely.

---

### Summary Table

| Parent Declares | Child Declares | Result |
|----------------|---------------|--------|
| `throws Exception` | `throws IOException` | ✅ Valid (narrower) |
| No throws | `throws IOException` | ❌ Compile error |
| No throws | `throws RuntimeException` | ✅ Valid (unchecked) |
| `throws IOException` | `throws Exception` | ❌ Compile error (broader) |

---

## 3. Method Overloading with null

### Rule
> When multiple overloaded methods match `null`, Java picks the **most specific type**.

### Snippet

```java
public class JavaClass {
    public void disp(String a) {
        System.out.println("test A");
    }

    public void disp(Object b) {
        System.out.println("test B");
    }

    public static void main(String[] args) {
        new JavaClass().disp(null);
    }
}
```

### Output
```
test A
```

### Explanation
- `null` can be assigned to both `String` and `Object`.
- `String` is a subclass of `Object`, making it more specific.
- Java selects the **most specific** matching overload at compile time.
- If two equally specific types existed (e.g., `String` and `Integer`), it would be ambiguous.

---

## 4. Ambiguous Overloading

### Rule
> If the compiler cannot determine a single most-specific overload, it throws an ambiguity error.

### Snippet

```java
public class JavaClass {
    public void disp(long a, int b) {
        System.out.println("test A");
    }

    public void disp(int b, long a) {
        System.out.println("test B");
    }

    public static void main(String[] args) {
        new JavaClass().disp(2, 2);
    }
}
```

### Output
```
Compilation Error: reference to disp is ambiguous
```

### Explanation
- Both `int` arguments can be widened to `long`.
- `disp(long, int)` matches by widening the first argument.
- `disp(int, long)` matches by widening the second argument.
- Neither is more specific than the other → ambiguous.

---

## 5. Static Method Hiding vs Overriding

### Rule
> Static methods belong to the **class**, not the instance. They are **hidden**, not overridden. The reference type determines which version executes.

### Snippet

```java
public class JavaClass {
    public static void main(String[] args) {
        A a = new B();
        a.disp();
    }
}

class A {
    public static void disp() {
        System.out.println("in class A");
    }
}

class B extends A {
    public static void disp() {
        System.out.println("in class B");
    }
}
```

### Output
```
in class A
```

### Explanation
- `disp()` is `static` — resolved at compile time based on reference type.
- Reference type is `A`, so `A.disp()` is called regardless of the actual object type.
- This is **method hiding**, not polymorphism.

---

## 6. Basic Runtime Polymorphism

### Rule
> For instance methods, Java resolves the call at **runtime** based on the actual object type (dynamic method dispatch).

### Snippet

```java
public class JavaClass {
    public static void main(String[] args) {
        A a = new B();
        a.disp();
    }
}

class A {
    public void disp() {
        System.out.println("in class A");
    }
}

class B extends A {
    public void disp() {
        System.out.println("in class B");
    }
}
```

### Output
```
in class B
```

### Explanation
- `disp()` is an instance method → resolved at runtime.
- Object type is `B`, so `B.disp()` executes.
- This is the classic example of runtime polymorphism.

---

## 7. Variable Hiding

### Rule
> Instance variables are **never overridden** — they are hidden. Access depends on the **reference type**, not the object type.

### Snippet

```java
class Animal {
    String name = "Animal";
}

class Dog extends Animal {
    String name = "Dog";
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();
        System.out.println(a.name);
    }
}
```

### Output
```
Animal
```

### Explanation
- Variables don't participate in polymorphism.
- Reference type is `Animal`, so `Animal.name` is accessed.
- To get the child's variable, you'd need a `Dog` reference or a getter method.

---

## 8. Constructor Chaining

### Rule
> When creating a child object, the parent constructor always executes **first** (implicitly via `super()`).

### Snippet

```java
class Animal {
    Animal() {
        System.out.println("Animal Constructor");
    }
}

class Dog extends Animal {
    Dog() {
        System.out.println("Dog Constructor");
    }
}

public class Test {
    public static void main(String[] args) {
        new Dog();
    }
}
```

### Output
```
Animal Constructor
Dog Constructor
```

### Explanation
- `Dog()` implicitly calls `super()` as its first statement.
- This ensures the parent is fully initialized before the child.
- The chain goes all the way up to `Object`.

---

## 9. super() and Parameterized Constructors

### Rule
> If the parent has no default constructor, the child **must** explicitly call `super(args)`.

### Snippet

```java
class Animal {
    Animal(String name) {
        System.out.println(name);
    }
}

class Dog extends Animal {
    Dog() {
        super("Tommy");
    }
}

public class Test {
    public static void main(String[] args) {
        new Dog();
    }
}
```

### Output
```
Tommy
```

### Explanation
- `Animal` only has a parameterized constructor — no default exists.
- `Dog` must call `super("Tommy")` explicitly.
- Without it, the code won't compile.

---

## 10. Calling Parent Method with super

### Rule
> Use `super.method()` to explicitly invoke the parent's version of an overridden method.

### Snippet

```java
class Animal {
    void display() {
        System.out.println("Animal");
    }
}

class Dog extends Animal {
    void display() {
        super.display();
        System.out.println("Dog");
    }
}

public class Test {
    public static void main(String[] args) {
        Dog d = new Dog();
        d.display();
    }
}
```

### Output
```
Animal
Dog
```

### Explanation
- `super.display()` calls the parent's implementation first.
- Then the child adds its own behavior.
- Useful for extending rather than replacing parent logic.

---

## 11. Upcasting and instanceof

### Rule
> Upcasting (child → parent reference) is always safe and implicit. The `instanceof` operator checks the **actual object type** at runtime.

### Snippet

```java
class Animal {}

class Dog extends Animal {
    void bark() {
        System.out.println("Bark");
    }
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();   // Upcasting
        System.out.println(a instanceof Dog);
    }
}
```

### Output
```
true
```

### Explanation
- `a` references a `Dog` object, even though the variable type is `Animal`.
- `instanceof` checks the actual runtime object → `true`.
- After upcasting, child-specific methods (like `bark()`) are not accessible without downcasting.

---

## 12. Downcasting

### Rule
> Downcasting (parent reference → child type) requires an explicit cast and is only safe if the object is actually an instance of the target type.

### Snippet

```java
class Animal {}

class Dog extends Animal {
    void bark() {
        System.out.println("Bark");
    }
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();
        Dog d = (Dog) a;    // Downcasting
        d.bark();
    }
}
```

### Output
```
Bark
```

### Explanation
- The object is actually a `Dog`, so the cast succeeds.
- After downcasting, child-specific methods become accessible.
- Best practice: always check with `instanceof` before downcasting.

---

## 13. Invalid Downcasting (ClassCastException)

### Rule
> If the object is not actually an instance of the target class, a `ClassCastException` is thrown at runtime.

### Snippet

```java
class Animal {}
class Dog extends Animal {}
class Cat extends Animal {}

public class Test {
    public static void main(String[] args) {
        Animal a = new Cat();
        Dog d = (Dog) a;    // Runtime error!
    }
}
```

### Output
```
Exception in thread "main" java.lang.ClassCastException:
Cat cannot be cast to Dog
```

### Explanation
- The actual object is a `Cat`, not a `Dog`.
- The compiler allows the cast (both extend `Animal`), but runtime fails.
- Prevention: `if (a instanceof Dog) { Dog d = (Dog) a; }`

---

## 14. Overriding vs Overloading Confusion

### Rule
> Overriding replaces the parent method (same signature). Overloading adds a new method (different signature). They are independent.

### Snippet

```java
class Animal {
    void show() {
        System.out.println("Animal");
    }
}

class Dog extends Animal {
    void show(int x) {   // Overloaded, NOT overridden
        System.out.println("Dog");
    }
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();
        a.show();
    }
}
```

### Output
```
Animal
```

### Explanation
- `show(int)` in `Dog` is a different method (different parameter list) — it's an overload.
- `show()` (no params) is NOT overridden in `Dog`.
- So `Animal.show()` executes. The reference type `Animal` has no access to `show(int)`.

---

## 15. Final Methods

### Rule
> A `final` method cannot be overridden. Attempting to do so causes a compile-time error.

### Snippet

```java
class Animal {
    final void display() {
        System.out.println("Animal");
    }
}

class Dog extends Animal {
    // void display() {} // ❌ Compile-time error: cannot override final method
}
```

### Key Points
- Use `final` when a method's behavior must be guaranteed across all subclasses.
- Common in template method patterns and security-critical code.
- `final` classes cannot be extended at all.

---

## 16. Dynamic Method Dispatch (Multi-Level)

### Rule
> Dynamic dispatch works through the entire inheritance chain — the actual object's deepest override executes.

### Snippet

```java
class A {
    void display() {
        System.out.println("A");
    }
}

class B extends A {
    void display() {
        System.out.println("B");
    }
}

class C extends B {
    void display() {
        System.out.println("C");
    }
}

public class Test {
    public static void main(String[] args) {
        A obj = new C();
        obj.display();
    }
}
```

### Output
```
C
```

### Explanation
- Object type is `C`, which has the deepest override.
- Even though reference is `A`, runtime resolves to `C.display()`.
- The dispatch always finds the most specific (deepest) implementation.

---

## 17. Parent Reference Limitations

### Rule
> A parent reference can only access methods declared in the parent class. Child-specific methods require a child reference (or downcasting).

### Snippet

```java
class Parent {
    void m1() {
        System.out.println("Parent");
    }
}

class Child extends Parent {
    void m1() {
        System.out.println("Child");
    }

    void m2() {
        System.out.println("Extra");
    }
}

public class Test {
    public static void main(String[] args) {
        Parent p = new Child();
        p.m1();       // ✅ Works — overridden method
        // p.m2();    // ❌ Compile-time error
    }
}
```

### Output
```
Child
```

### Explanation
- `p.m1()` works: `m1()` exists in `Parent`, and at runtime the overridden `Child.m1()` executes.
- `p.m2()` fails: `m2()` doesn't exist in `Parent`, so the compiler rejects it.
- This is the trade-off of upcasting: you gain polymorphism but lose access to child-specific members.

---

## 18. Upcasting In-Depth

### Concept
Upcasting = storing a child object in a parent reference.

```java
Animal a = new Dog();   // Dog → Animal (child → parent)
```

### Key Behaviors

| Aspect | Behavior |
|--------|----------|
| Assignment | Always implicit and safe |
| Parent methods | Accessible |
| Child-only methods | NOT accessible |
| Overridden methods | Child's version executes (runtime polymorphism) |
| Variables | Parent's version accessed (no polymorphism) |

### Example — Runtime Polymorphism via Upcasting

```java
class Animal {
    void sound() {
        System.out.println("Animal sound");
    }
}

class Dog extends Animal {
    void sound() {
        System.out.println("Dog barks");
    }
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();   // Upcasting
        a.sound();              // Dog's version at runtime
    }
}
```

**Output:** `Dog barks`

### Visual Representation

```
Dog object (actual)
    ↑
    |  stored in
    ↓
Animal reference (visible interface)

Accessible: methods declared in Animal
Executed:   overridden version from Dog (at runtime)
```

---

## 19. Tricky Interview Question: Overloading + Overriding + Upcasting

This is a classic question that confuses even experienced developers. It combines three concepts in one.

### Snippet

```java
class Animal {
    void eat(Animal a) {
        System.out.println("Animal eats animal food");
    }
}

class Dog extends Animal {
    void eat(Dog d) {                    // Overloaded (different param type)
        System.out.println("Dog eats dog food");
    }

    @Override
    void eat(Animal a) {                 // Overridden (same param type as parent)
        System.out.println("Dog eats animal food");
    }
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();
        Dog d = new Dog();
        a.eat(d);
    }
}
```

### Output
```
Dog eats animal food
```

### Most Developers Incorrectly Answer
> "Dog eats dog food"

### Step-by-Step Breakdown

**Phase 1 — Compile Time (method selection):**
1. Compiler looks at the reference type: `Animal`
2. Available methods in `Animal`: only `eat(Animal)`
3. `eat(Dog)` doesn't exist in `Animal` — compiler doesn't consider it
4. Compiler selects: `eat(Animal)` (the `Dog d` argument is widened to `Animal`)

**Phase 2 — Runtime (method execution):**
1. JVM looks at the actual object type: `Dog`
2. `Dog` overrides `eat(Animal)` → executes `Dog`'s version
3. Result: `"Dog eats animal food"`

### The Golden Rule

```
┌─────────────────────────────────────────────────┐
│  Overloading  →  resolved at COMPILE time       │
│  Overriding   →  resolved at RUNTIME            │
│                                                 │
│  Compile time = reference type determines       │
│                 WHICH method signature           │
│  Runtime      = object type determines          │
│                 WHICH implementation             │
└─────────────────────────────────────────────────┘
```

### Visual Flow

```
a.eat(d)
   │
   ├── Compile time: reference = Animal → eat(Animal) selected
   │
   └── Runtime: object = Dog → Dog's overridden eat(Animal) executed
```

---

## Quick Reference — Key Rules

| Concept | Resolution Time | Based On |
|---------|----------------|----------|
| Method Overloading | Compile time | Reference type + argument types |
| Method Overriding | Runtime | Actual object type |
| Variable Access | Compile time | Reference type |
| Static Methods | Compile time | Reference type (hiding, not overriding) |
| `instanceof` | Runtime | Actual object type |
| Access Modifiers | Compile time | Cannot be weaker in child |
| Exceptions (checked) | Compile time | Cannot be broader in child |

---

## Common Interview Traps

1. **"Static methods are overridden"** — No, they are hidden. Reference type wins.
2. **"Variables are polymorphic"** — No. Reference type determines variable access.
3. **"Overloaded method chosen at runtime"** — No. Overloading is compile-time.
4. **"Child can throw any exception"** — Only unchecked. Checked must be same or narrower.
5. **"Weaker access is fine in child"** — Never. Must be same or stronger.
6. **"final methods can be overloaded"** — Yes! Only overriding is blocked.


---

## 20. Constructor vs Method with Same Name — Neither Overloading

### Snippet

```java
public class A {
    public A() {
        // Constructor
    }

    void A() {
        // Method
    }
}
```

### Answer
**Neither constructor overloading nor method overloading.**

### Why This Code Is Valid

| Declaration | Type | Reasoning |
|-------------|------|-----------|
| `public A()` | ✅ Constructor | Same name as the class, no return type |
| `void A()` | ✅ Method | Has a return type (`void`). A method is allowed to have the same name as the class |

The key distinction: **a return type (even `void`) makes it a method, not a constructor.**

### Why It's NOT Constructor Overloading

Constructor overloading means multiple constructors with different parameter lists:

```java
class A {
    A() { }
    A(int x) { }
    A(String s) { }
}
```

Here there are 3 constructors → this IS constructor overloading.

In the original snippet, there is only **1 constructor** (`public A()`).

### Why It's NOT Method Overloading

Method overloading means multiple methods with the same name but different parameters:

```java
class A {
    void show() { }
    void show(int x) { }
    void show(String s) { }
}
```

Here there are 3 methods named `show()` → this IS method overloading.

In the original snippet, there is only **1 method** (`void A()`).

### The Core Concept

```
Your code has:
  1 constructor  →  A()
  1 method       →  void A()

Constructor and method are DIFFERENT language constructs.
They are NOT considered overloads of each other.
```

### Interview Answer

> The code is valid. `A()` is a constructor, `void A()` is a method. This is **neither constructor overloading nor method overloading** because overloading applies only among constructors or among methods — not between a constructor and a method.

### How to Identify

```
┌──────────────────────────────────────────────┐
│  Same name as class + NO return type          │
│  → Constructor                                │
│                                               │
│  Same name as class + HAS return type (void)  │
│  → Method (just happens to share the name)    │
└──────────────────────────────────────────────┘
```


---

## 21. String Pool and == vs equals()

### Rule
> `==` compares **references** (memory addresses). `equals()` compares **content**. String literals are stored in the String Pool and reused.

### Snippet

```java
public class Test {
    public static void main(String[] args) {
        String s1 = "Hello";
        String s2 = "Hello";
        String s3 = new String("Hello");

        System.out.println(s1 == s2);        // ?
        System.out.println(s1 == s3);        // ?
        System.out.println(s1.equals(s3));   // ?
        System.out.println(s1 == s3.intern()); // ?
    }
}
```

### Output
```
true
false
true
true
```

### Explanation
- `s1 == s2` → `true`: Both are literals pointing to the same pool object.
- `s1 == s3` → `false`: `new String()` creates a separate heap object.
- `s1.equals(s3)` → `true`: Content is identical.
- `s1 == s3.intern()` → `true`: `intern()` returns the pool reference.

### Key Takeaway
```
┌───────────────────────────────────────────┐
│  Literal  →  String Pool (shared)         │
│  new String()  →  Heap (separate object)  │
│  intern()  →  Returns pool reference      │
└───────────────────────────────────────────┘
```

---

## 22. Autoboxing and Integer Cache

### Rule
> Java caches `Integer` objects for values **-128 to 127**. Within this range, `==` returns `true` for autoboxed values. Outside this range, different objects are created.

### Snippet

```java
public class Test {
    public static void main(String[] args) {
        Integer a = 127;
        Integer b = 127;
        System.out.println(a == b);    // ?

        Integer c = 128;
        Integer d = 128;
        System.out.println(c == d);    // ?

        Integer e = new Integer(127);
        Integer f = new Integer(127);
        System.out.println(e == f);    // ?
    }
}
```

### Output
```
true
false
false
```

### Explanation
- `a == b` → `true`: 127 is within cache range, same object returned.
- `c == d` → `false`: 128 is outside cache, two different objects.
- `e == f` → `false`: `new` always creates a fresh object, bypasses cache.

### Interview Tip
> Always use `.equals()` for comparing wrapper objects. Never rely on `==` for `Integer`, `Long`, etc.

---

## 23. finally Block vs return

### Rule
> `finally` **always executes** (except `System.exit()`), even after a `return` statement. If `finally` also has a return, it **overrides** the try/catch return.

### Snippet

```java
public class Test {
    public static int getValue() {
        try {
            return 1;
        } catch (Exception e) {
            return 2;
        } finally {
            return 3;
        }
    }

    public static void main(String[] args) {
        System.out.println(getValue());
    }
}
```

### Output
```
3
```

### Explanation
- `try` prepares to return `1`.
- `finally` executes before the method actually returns.
- `finally` has its own `return 3`, which **overrides** the try's return value.
- This is a compiler warning in most IDEs — returning from `finally` is bad practice.

### Variant — finally Modifies Variable (Trap!)

```java
public static int getValue() {
    int x = 1;
    try {
        return x;
    } finally {
        x = 99;
    }
}
```

**Output:** `1` (NOT 99!)

**Why:** The return value (`1`) is saved before `finally` runs. Modifying `x` in `finally` doesn't change the already-saved return value. Only a `return` in `finally` can override it.

---

## 24. Exception in Initializer Block

### Rule
> Static and instance initializer blocks run before constructors. Exceptions in static blocks wrap in `ExceptionInInitializerError`.

### Snippet

```java
public class Test {
    static {
        System.out.println("Static Block");
    }

    {
        System.out.println("Instance Block");
    }

    Test() {
        System.out.println("Constructor");
    }

    public static void main(String[] args) {
        new Test();
        System.out.println("---");
        new Test();
    }
}
```

### Output
```
Static Block
Instance Block
Constructor
---
Instance Block
Constructor
```

### Explanation
- Static block runs **once** when the class is loaded.
- Instance block runs **every time** an object is created, before the constructor.
- Order: Static block → Instance block → Constructor.

### Tricky Variant — Static Block Exception

```java
class Broken {
    static {
        int x = 1 / 0;  // ArithmeticException
    }
}

public class Test {
    public static void main(String[] args) {
        new Broken();
    }
}
```

**Output:** `ExceptionInInitializerError` (wraps the ArithmeticException)

---

## 25. Covariant Return Types

### Rule
> An overriding method can return a **subtype** of the parent method's return type. This is called a covariant return type.

### Snippet

```java
class Animal {
    Animal create() {
        System.out.println("Animal created");
        return new Animal();
    }
}

class Dog extends Animal {
    @Override
    Dog create() {   // Returns Dog instead of Animal — valid!
        System.out.println("Dog created");
        return new Dog();
    }
}

public class Test {
    public static void main(String[] args) {
        Animal a = new Dog();
        a.create();
    }
}
```

### Output
```
Dog created
```

### Explanation
- `Dog.create()` returns `Dog` instead of `Animal`.
- This is valid because `Dog` IS-A `Animal` (covariant return).
- Works with any subtype relationship, not just direct child.

### Invalid Example

```java
class Animal {
    Dog create() { return new Dog(); }
}

class Dog extends Animal {
    Animal create() { return new Animal(); }  // ❌ Broader return type — compile error
}
```

---

## 26. Method Hiding with Private Methods

### Rule
> `private` methods are **not inherited**. A child class can define a method with the same signature — this is a **new method**, not an override.

### Snippet

```java
class Parent {
    private void display() {
        System.out.println("Parent");
    }

    public void call() {
        display();  // Calls Parent's own private display()
    }
}

class Child extends Parent {
    private void display() {  // Completely independent method
        System.out.println("Child");
    }
}

public class Test {
    public static void main(String[] args) {
        Child c = new Child();
        c.call();
    }
}
```

### Output
```
Parent
```

### Explanation
- `call()` is inherited by `Child`.
- Inside `call()`, `display()` refers to `Parent`'s private method (bound at compile time).
- `Child.display()` is a completely separate method — not an override.
- Private methods use **static binding** (resolved at compile time).

### Interview Trap
> Many developers expect "Child" because the object is `Child`. But `private` methods don't participate in polymorphism at all.

---

## 27. Interface Default Method Diamond Problem

### Rule
> If a class implements two interfaces with the same default method, it **must** override the method to resolve ambiguity.

### Snippet

```java
interface A {
    default void show() {
        System.out.println("A");
    }
}

interface B {
    default void show() {
        System.out.println("B");
    }
}

class C implements A, B {
    @Override
    public void show() {
        A.super.show();  // Explicitly choose A's version
    }
}

public class Test {
    public static void main(String[] args) {
        new C().show();
    }
}
```

### Output
```
A
```

### Explanation
- Both `A` and `B` have `default show()`.
- `C` must override to resolve the conflict.
- `A.super.show()` syntax explicitly delegates to interface A's default.
- Without the override → compile error: "class C inherits unrelated defaults for show()"

### What If Only One Interface Has Default?

```java
interface A {
    default void show() { System.out.println("A"); }
}

interface B {
    void show();  // Abstract
}

class C implements A, B { }  // ❌ Compile error — must override
```

Even though `A` provides a default, the abstract declaration in `B` forces an explicit override.

---

## 28. Tricky equals() and hashCode()

### Rule
> Objects used as HashMap keys must correctly implement both `equals()` and `hashCode()`. If `hashCode()` is inconsistent, objects "disappear" from maps.

### Snippet

```java
import java.util.HashMap;

class Key {
    int id;

    Key(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Key) return this.id == ((Key) o).id;
        return false;
    }

    // hashCode() NOT overridden!
}

public class Test {
    public static void main(String[] args) {
        HashMap<Key, String> map = new HashMap<>();
        map.put(new Key(1), "One");

        System.out.println(map.get(new Key(1)));
    }
}
```

### Output
```
null
```

### Explanation
- `equals()` is overridden — two `Key(1)` objects are "equal".
- But `hashCode()` is NOT overridden — each object gets a different hash.
- `HashMap` first checks `hashCode()` to find the bucket, then `equals()`.
- Different hash → different bucket → object not found → returns `null`.

### Fix
```java
@Override
public int hashCode() {
    return Integer.hashCode(id);
}
```

### The Contract
> If `a.equals(b)` is `true`, then `a.hashCode() == b.hashCode()` **must** be true.

---

## 29. Immutable String Confusion

### Rule
> Strings are **immutable**. Every modification creates a new object. The original reference remains unchanged unless reassigned.

### Snippet

```java
public class Test {
    public static void main(String[] args) {
        String s = "Hello";
        s.concat(" World");
        System.out.println(s);

        s = s.concat(" World");
        System.out.println(s);
    }
}
```

### Output
```
Hello
Hello World
```

### Explanation
- First `s.concat(" World")`: creates a new String, but result is **not assigned** — `s` still points to "Hello".
- Second `s = s.concat(...)`: result is assigned back to `s`.
- Common trap in interviews: forgetting that String methods return new objects.

### Variant — StringBuilder

```java
StringBuilder sb = new StringBuilder("Hello");
sb.append(" World");
System.out.println(sb);  // "Hello World" — mutable!
```

`StringBuilder` modifies in-place. `String` never does.

---

## 30. try-with-resources Execution Order

### Rule
> Resources are closed in **reverse order** of declaration, after the try block but **before** catch/finally.

### Snippet

```java
class ResourceA implements AutoCloseable {
    ResourceA() { System.out.println("A opened"); }
    public void close() { System.out.println("A closed"); }
}

class ResourceB implements AutoCloseable {
    ResourceB() { System.out.println("B opened"); }
    public void close() { System.out.println("B closed"); }
}

public class Test {
    public static void main(String[] args) {
        try (ResourceA a = new ResourceA();
             ResourceB b = new ResourceB()) {
            System.out.println("In try");
        } catch (Exception e) {
            System.out.println("In catch");
        } finally {
            System.out.println("In finally");
        }
    }
}
```

### Output
```
A opened
B opened
In try
B closed
A closed
In finally
```

### Explanation
- Resources open in declaration order: A → B.
- Resources close in **reverse** order: B → A.
- Close happens **before** `finally` block.
- Order: try body → close resources (reverse) → catch (if needed) → finally.

---

## 31. Static Binding with Overloaded + Overridden Methods

### Rule
> When a method is both overloaded and overridden, the **overload** is selected at compile time (static binding), and the **override** is resolved at runtime (dynamic binding).

### Snippet

```java
class Base {
    void process(int x) {
        System.out.println("Base int: " + x);
    }

    void process(long x) {
        System.out.println("Base long: " + x);
    }
}

class Derived extends Base {
    @Override
    void process(int x) {
        System.out.println("Derived int: " + x);
    }
}

public class Test {
    public static void main(String[] args) {
        Base b = new Derived();
        b.process(5);
        b.process(5L);
    }
}
```

### Output
```
Derived int: 5
Base long: 5
```

### Explanation
- `b.process(5)`: compiler selects `process(int)` → runtime finds `Derived.process(int)`.
- `b.process(5L)`: compiler selects `process(long)` → `Derived` doesn't override it → `Base.process(long)` runs.

---

## 32. Abstract Class with Constructor

### Rule
> Abstract classes **can** have constructors. They run when a concrete subclass is instantiated.

### Snippet

```java
abstract class Shape {
    String color;

    Shape(String color) {
        this.color = color;
        System.out.println("Shape created: " + color);
    }

    abstract void draw();
}

class Circle extends Shape {
    Circle(String color) {
        super(color);
        System.out.println("Circle created");
    }

    void draw() {
        System.out.println("Drawing " + color + " circle");
    }
}

public class Test {
    public static void main(String[] args) {
        // Shape s = new Shape("Red");  // ❌ Cannot instantiate abstract class
        Circle c = new Circle("Red");
        c.draw();
    }
}
```

### Output
```
Shape created: Red
Circle created
Drawing Red circle
```

### Explanation
- You can't do `new Shape()` directly — it's abstract.
- But the constructor runs when a subclass calls `super()`.
- Useful for initializing common fields in the abstract parent.

### Interview Trap
> "Can abstract classes have constructors?" — **Yes!** They just can't be called directly with `new`.

---

## 33. Varargs vs Specific Parameter

### Rule
> When both a varargs method and a specific-parameter method match, Java prefers the **more specific** (non-varargs) version.

### Snippet

```java
public class Test {
    static void show(int a, int b) {
        System.out.println("Two ints");
    }

    static void show(int... a) {
        System.out.println("Varargs");
    }

    public static void main(String[] args) {
        show(1, 2);
        show(1, 2, 3);
        show(1);
    }
}
```

### Output
```
Two ints
Varargs
Varargs
```

### Explanation
- `show(1, 2)`: Both match, but `show(int, int)` is more specific → chosen.
- `show(1, 2, 3)`: Only varargs matches.
- `show(1)`: Only varargs matches.
- Varargs is treated as the **least specific** option in overload resolution.

---

## 34. Post-Increment in Return Statement

### Rule
> Post-increment (`x++`) returns the **original value** before incrementing. The increment happens after the expression is evaluated.

### Snippet

```java
public class Test {
    static int x = 10;

    static int getX() {
        return x++;
    }

    public static void main(String[] args) {
        System.out.println(getX());
        System.out.println(x);

        int a = 5;
        a = a++;
        System.out.println(a);
    }
}
```

### Output
```
10
11
5
```

### Explanation
- `getX()` returns `10` (original), then increments `x` to `11`.
- `a = a++`: This is the famous trap!
  1. `a++` evaluates to `5` (current value of `a`)
  2. `a` is incremented to `6`
  3. The expression result (`5`) is assigned back to `a`
  4. Final value: `5`

### Visual for `a = a++`
```
Step 1: temp = a       →  temp = 5
Step 2: a = a + 1      →  a = 6
Step 3: a = temp       →  a = 5  (assignment overwrites the increment!)
```

---

## 35. Unreachable Code and finally

### Rule
> `System.exit()` is the only thing that prevents `finally` from executing. But even with infinite loops or exceptions in catch, `finally` still runs.

### Snippet

```java
public class Test {
    public static void main(String[] args) {
        System.out.println(test1());
        System.out.println("---");
        test2();
    }

    static String test1() {
        try {
            System.out.println("try");
            throw new RuntimeException();
        } catch (Exception e) {
            System.out.println("catch");
            return "from catch";
        } finally {
            System.out.println("finally");
        }
    }

    static void test2() {
        try {
            System.out.println("try");
            System.exit(0);
        } finally {
            System.out.println("finally");  // Never prints!
        }
    }
}
```

### Output
```
try
catch
finally
from catch
---
try
```

### Explanation
- `test1()`: even though `catch` has a `return`, `finally` still runs before the method returns.
- `test2()`: `System.exit(0)` terminates the JVM — `finally` does NOT run.
- This is the **only** way to skip `finally`.

### When finally Does NOT Execute
| Scenario | finally Runs? |
|----------|:------------:|
| return in try | ✅ Yes |
| exception in try | ✅ Yes |
| exception in catch | ✅ Yes |
| `System.exit()` | ❌ No |
| JVM crash | ❌ No |
| Infinite loop before finally | ❌ Never reached |

---

## Quick Reference — All Tricky Concepts

| # | Concept | Common Wrong Answer | Correct Answer |
|---|---------|-------------------|----------------|
| 21 | `==` on String literals | `false` | `true` (same pool object) |
| 22 | `Integer == Integer` (128) | `true` | `false` (outside cache) |
| 23 | return in finally | try's return | finally's return wins |
| 24 | Static block runs | Every object creation | Only once (class load) |
| 25 | Covariant return | Compile error | Valid (subtype allowed) |
| 26 | Private method + inheritance | "Child" | "Parent" (no polymorphism) |
| 27 | Two default methods | Inherits one | Must override (ambiguity) |
| 28 | Missing hashCode() | Found in map | `null` (wrong bucket) |
| 29 | `s.concat()` without assign | "Hello World" | "Hello" (immutable) |
| 30 | Resource close order | Same as open | Reverse order |
| 31 | Overload + Override | Derived long | Base long (overload is static) |
| 32 | Abstract constructor | Not allowed | Allowed (runs via super) |
| 33 | Varargs vs specific | Varargs chosen | Specific wins |
| 34 | `a = a++` | 6 | 5 (post-increment trap) |
| 35 | finally after System.exit | Runs | Does NOT run |


---

## 36. Encapsulation — Defensive Copying Trap

### Rule
> If a getter returns a reference to a mutable field (like `Date` or `List`), external code can modify the object's internal state — breaking encapsulation.

### Snippet

```java
import java.util.ArrayList;
import java.util.List;

class Student {
    private List<String> courses;

    Student(List<String> courses) {
        this.courses = courses;  // ❌ Direct reference
    }

    public List<String> getCourses() {
        return courses;  // ❌ Exposes internal list
    }
}

public class Test {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("Math");

        Student s = new Student(list);

        // External modification — breaks encapsulation!
        list.add("Hacked via constructor ref");
        s.getCourses().add("Hacked via getter");

        System.out.println(s.getCourses());
    }
}
```

### Output
```
[Math, Hacked via constructor ref, Hacked via getter]
```

### Explanation
- The constructor stores the original list reference → external `list` variable can modify internals.
- The getter returns the internal reference → caller can mutate the list directly.
- The "private" keyword gives a false sense of security here.

### Fix — Defensive Copying

```java
class Student {
    private final List<String> courses;

    Student(List<String> courses) {
        this.courses = new ArrayList<>(courses);  // ✅ Copy on input
    }

    public List<String> getCourses() {
        return new ArrayList<>(courses);  // ✅ Copy on output
    }
}
```

### Immutable Class Checklist
```
✅ Class is final (prevent subclass tampering)
✅ All fields are private final
✅ No setters
✅ Defensive copy in constructor (for mutable params)
✅ Defensive copy in getters (for mutable fields)
```

---

## 37. this() Constructor Chaining

### Rule
> `this()` calls another constructor in the **same class**. `super()` calls the parent constructor. Both must be the **first statement** — you cannot use both in the same constructor.

### Snippet

```java
class Employee {
    String name;
    int age;
    String dept;

    Employee() {
        this("Unknown");
        System.out.println("No-arg constructor");
    }

    Employee(String name) {
        this(name, 0);
        System.out.println("One-arg constructor");
    }

    Employee(String name, int age) {
        this(name, age, "General");
        System.out.println("Two-arg constructor");
    }

    Employee(String name, int age, String dept) {
        this.name = name;
        this.age = age;
        this.dept = dept;
        System.out.println("Three-arg constructor");
    }
}

public class Test {
    public static void main(String[] args) {
        new Employee();
    }
}
```

### Output
```
Three-arg constructor
Two-arg constructor
One-arg constructor
No-arg constructor
```

### Explanation
- `this()` delegates to the next constructor in the chain.
- Execution starts from the most specific constructor (last in chain) and unwinds back.
- Useful for reducing code duplication across constructors (telescoping pattern).

### Common Compile Errors

```java
Employee() {
    super();   // ❌ Cannot have both this() and super()
    this("X");
}

Employee() {
    System.out.println("Hi");
    this("X");  // ❌ this() must be first statement
}
```

### this() vs super()

| Feature | `this()` | `super()` |
|---------|----------|-----------|
| Calls | Same class constructor | Parent class constructor |
| Must be | First statement | First statement |
| Can coexist | ❌ Not with super() | ❌ Not with this() |
| Default | Not added automatically | Added automatically if no this() |

---

## 38. Multiple Inheritance via Interfaces

### Rule
> Java doesn't support multiple class inheritance, but a class can implement multiple interfaces. Abstract classes + interfaces together simulate multiple inheritance.

### Snippet

```java
interface Flyable {
    void fly();
    default String getType() { return "Flying"; }
}

interface Swimmable {
    void swim();
    default String getType() { return "Swimming"; }
}

abstract class Animal {
    String name;
    Animal(String name) { this.name = name; }
    abstract void sound();
}

class Duck extends Animal implements Flyable, Swimmable {

    Duck(String name) { super(name); }

    @Override
    public void sound() { System.out.println(name + " quacks"); }

    @Override
    public void fly() { System.out.println(name + " flies"); }

    @Override
    public void swim() { System.out.println(name + " swims"); }

    @Override
    public String getType() {
        return Flyable.super.getType() + " + " + Swimmable.super.getType();
    }
}

public class Test {
    public static void main(String[] args) {
        Duck d = new Duck("Donald");
        d.sound();
        d.fly();
        d.swim();
        System.out.println(d.getType());

        // Polymorphism with interfaces
        Flyable f = d;
        f.fly();

        Swimmable s = d;
        s.swim();
    }
}
```

### Output
```
Donald quacks
Donald flies
Donald swims
Flying + Swimming
Donald flies
Donald swims
```

### Explanation
- `Duck` extends one abstract class and implements two interfaces → simulates multiple inheritance.
- Conflicting `getType()` defaults require an explicit override.
- `InterfaceName.super.method()` selects a specific default implementation.
- Interface references provide polymorphic access to specific capabilities.

### Why Java Chose This Design
```
┌────────────────────────────────────────────────┐
│  Multiple class inheritance → Diamond Problem  │
│  (ambiguity in fields and constructors)        │
│                                                │
│  Multiple interface inheritance → Safe         │
│  (no state, explicit conflict resolution)      │
└────────────────────────────────────────────────┘
```

---

## 39. Shallow Copy vs Deep Copy (Cloneable)

### Rule
> `Object.clone()` performs a **shallow copy** — primitive fields are copied, but object references still point to the same objects. Deep copy requires manual implementation.

### Snippet

```java
class Address implements Cloneable {
    String city;

    Address(String city) { this.city = city; }

    @Override
    protected Address clone() throws CloneNotSupportedException {
        return (Address) super.clone();
    }
}

class Person implements Cloneable {
    String name;
    Address address;

    Person(String name, Address address) {
        this.name = name;
        this.address = address;
    }

    // Shallow copy
    @Override
    protected Person clone() throws CloneNotSupportedException {
        return (Person) super.clone();
    }

    // Deep copy
    protected Person deepClone() throws CloneNotSupportedException {
        Person copy = (Person) super.clone();
        copy.address = this.address.clone();  // Clone nested object
        return copy;
    }
}

public class Test {
    public static void main(String[] args) throws Exception {
        Person p1 = new Person("Alice", new Address("Mumbai"));

        Person p2 = p1.clone();         // Shallow
        Person p3 = p1.deepClone();     // Deep

        p1.address.city = "Delhi";

        System.out.println("p1: " + p1.address.city);
        System.out.println("p2: " + p2.address.city);  // Affected!
        System.out.println("p3: " + p3.address.city);  // Independent
    }
}
```

### Output
```
p1: Delhi
p2: Delhi
p3: Mumbai
```

### Explanation
- `p2` (shallow copy): shares the same `Address` object as `p1` → mutation reflects.
- `p3` (deep copy): has its own `Address` object → independent of `p1`.
- Shallow copy copies field values; for references, that means copying the pointer, not the object.

### Shallow vs Deep

| Aspect | Shallow Copy | Deep Copy |
|--------|-------------|-----------|
| Primitives | Copied ✅ | Copied ✅ |
| Object refs | Shared (same object) | New copy (independent) |
| Performance | Fast | Slower |
| Use case | Immutable nested fields | Mutable nested fields |

### Interview Note
> `Cloneable` is a **marker interface** — it has no methods. It just signals that `Object.clone()` is permitted. Without it, `clone()` throws `CloneNotSupportedException`.

---

## 40. Object Class Methods — toString() and equals()

### Rule
> Every class inherits from `Object`. Default `toString()` prints `ClassName@hashCode`. Default `equals()` uses `==` (reference comparison).

### Snippet

```java
class Book {
    String title;
    int pages;

    Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }
}

class BetterBook {
    String title;
    int pages;

    BetterBook(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    @Override
    public String toString() {
        return "BetterBook{title='" + title + "', pages=" + pages + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BetterBook that = (BetterBook) o;
        return pages == that.pages && title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return title.hashCode() * 31 + pages;
    }
}

public class Test {
    public static void main(String[] args) {
        Book b1 = new Book("Java", 500);
        Book b2 = new Book("Java", 500);
        System.out.println(b1);               // ?
        System.out.println(b1.equals(b2));    // ?

        BetterBook bb1 = new BetterBook("Java", 500);
        BetterBook bb2 = new BetterBook("Java", 500);
        System.out.println(bb1);              // ?
        System.out.println(bb1.equals(bb2));  // ?
    }
}
```

### Output
```
Book@15db9742          (or similar hex)
false
BetterBook{title='Java', pages=500}
true
```

### Explanation
- `Book` uses Object defaults: `toString()` shows class@hash, `equals()` checks reference only.
- `BetterBook` overrides both: meaningful string output and content-based equality.
- `hashCode()` is also overridden to maintain the contract with `equals()`.

### Object Class Important Methods

| Method | Default Behavior | Override When |
|--------|-----------------|--------------|
| `toString()` | `ClassName@hex` | Want readable output |
| `equals()` | Reference comparison (`==`) | Content equality needed |
| `hashCode()` | Memory-based hash | Always with equals() |
| `clone()` | Shallow copy | Need object duplication |
| `finalize()` | Nothing (deprecated) | Avoid — use try-with-resources |
| `getClass()` | Returns runtime class | Never override (final) |

---

## 41. Inner Classes — Access and Scope Tricks

### Rule
> Inner classes can access **all** members of the enclosing class (including private). Static nested classes cannot access instance members without an object reference.

### Snippet

```java
class Outer {
    private int x = 10;
    private static int y = 20;

    // Non-static inner class
    class Inner {
        void display() {
            System.out.println("Inner accesses private x: " + x);      // ✅
            System.out.println("Inner accesses static y: " + y);       // ✅
        }
    }

    // Static nested class
    static class StaticNested {
        void display() {
            // System.out.println(x);  // ❌ Cannot access instance member
            System.out.println("StaticNested accesses static y: " + y); // ✅
        }
    }

    // Method with local class
    void process() {
        final int localVar = 30;  // Must be effectively final

        class LocalInner {
            void show() {
                System.out.println("Local accesses x: " + x);
                System.out.println("Local accesses localVar: " + localVar);
            }
        }
        new LocalInner().show();
    }
}

public class Test {
    public static void main(String[] args) {
        // Inner class instantiation
        Outer outer = new Outer();
        Outer.Inner inner = outer.new Inner();
        inner.display();

        System.out.println("---");

        // Static nested — no outer instance needed
        Outer.StaticNested nested = new Outer.StaticNested();
        nested.display();

        System.out.println("---");

        // Local inner via method
        outer.process();
    }
}
```

### Output
```
Inner accesses private x: 10
Inner accesses static y: 20
---
StaticNested accesses static y: 20
---
Local accesses x: 10
Local accesses localVar: 30
```

### Explanation

| Type | Access to Outer | Instantiation |
|------|----------------|---------------|
| Inner class | All members (including private) | `outer.new Inner()` |
| Static nested | Only static members | `new Outer.StaticNested()` |
| Local class | Outer members + effectively final locals | Inside the method only |
| Anonymous class | Same as local | Inline (no name) |

### Anonymous Class Example

```java
interface Greeting {
    void greet();
}

public class Test {
    public static void main(String[] args) {
        Greeting g = new Greeting() {  // Anonymous class
            @Override
            public void greet() {
                System.out.println("Hello!");
            }
        };
        g.greet();  // Output: Hello!
    }
}
```

### Interview Trap
> "Can an inner class be private?" — **Yes!** Only inner classes can be `private` or `protected`. Top-level classes can only be `public` or default.

---

## 42. Enum with Constructor and Methods

### Rule
> Enums can have fields, constructors, and methods. The constructor is called for each constant at class loading. Enum constructors are implicitly **private**.

### Snippet

```java
enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    EARTH(5.976e+24, 6.37814e6),
    MARS(6.421e+23, 3.3972e6);

    private final double mass;
    private final double radius;
    private static final double G = 6.67300E-11;

    // Constructor — implicitly private
    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
        System.out.println(this.name() + " created");
    }

    double surfaceGravity() {
        return G * mass / (radius * radius);
    }

    double surfaceWeight(double otherMass) {
        return otherMass * surfaceGravity();
    }
}

public class Test {
    public static void main(String[] args) {
        System.out.println("---");
        double earthWeight = 75.0;
        double mass = earthWeight / Planet.EARTH.surfaceGravity();

        for (Planet p : Planet.values()) {
            System.out.printf("Weight on %s: %.2f%n", p, p.surfaceWeight(mass));
        }
    }
}
```

### Output
```
MERCURY created
EARTH created
MARS created
---
Weight on MERCURY: 28.33
Weight on EARTH: 75.00
Weight on MARS: 28.38
```

### Explanation
- Constructors run for **every** enum constant when the class loads (before `main`).
- Enum constants are effectively `public static final` instances.
- `values()` returns all constants in declaration order.
- You cannot do `new Planet(...)` — constructors are always private.

### Enum with Abstract Methods (Strategy Pattern)

```java
enum Operation {
    ADD {
        public int apply(int a, int b) { return a + b; }
    },
    SUBTRACT {
        public int apply(int a, int b) { return a - b; }
    };

    public abstract int apply(int a, int b);
}

// Usage:
// Operation.ADD.apply(5, 3) → 8
```

### Enum as Singleton

```java
enum Singleton {
    INSTANCE;

    private int counter = 0;

    public int increment() { return ++counter; }
}
// Thread-safe, serialization-safe, reflection-proof singleton
```

---

## 43. Generics Type Erasure

### Rule
> Generics are a **compile-time** feature. At runtime, all generic type information is erased (replaced with `Object` or bounds). This means you cannot use generics for runtime type checks.

### Snippet

```java
import java.util.ArrayList;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();

        // At runtime, both are just "ArrayList"
        System.out.println(strings.getClass() == integers.getClass());  // ?

        // This is why you can "cheat" with raw types
        List raw = strings;
        raw.add(42);  // No compile error on raw type!

        // Blows up when you try to USE the element as String
        // String s = strings.get(0);  // ClassCastException at runtime!

        System.out.println(strings.size());
        System.out.println(strings);  // Prints with the integer inside!
    }
}
```

### Output
```
true
1
[42]
```

### Explanation
- `strings.getClass() == integers.getClass()` → `true`: After erasure, both are just `ArrayList`.
- Raw type bypasses compile-time checks → integer gets into a "String" list.
- The cast to `String` only happens when you read from the list — that's where it fails.

### What You CANNOT Do with Generics

```java
class Box<T> {
    // T obj = new T();             // ❌ Cannot instantiate type parameter
    // T[] arr = new T[10];         // ❌ Cannot create generic array
    // if (obj instanceof T) {}     // ❌ Cannot use instanceof with type param
    // static T value;              // ❌ Cannot use in static context
}
```

### Bounded Type Erasure

```java
class Container<T extends Number> {
    T value;
    // After erasure, T becomes Number (not Object)
    // So value is treated as Number at runtime
}
```

### Interview Questions

| Question | Answer |
|----------|--------|
| Can you get generic type at runtime? | No (erased). Use `Class<T>` token pattern |
| Why no `new T()`? | Compiler doesn't know T's constructor |
| Why no generic arrays? | Arrays are covariant + reified; generics are invariant + erased |
| `List<String>` vs `List<Object>`? | No inheritance relationship (invariant) |

---

## 44. Composition vs Inheritance

### Rule
> "Favor composition over inheritance" — compose objects with HAS-A relationships rather than extending with IS-A. Inheritance creates tight coupling; composition is flexible and testable.

### Snippet — Problem with Inheritance

```java
import java.util.HashSet;

// Fragile base class problem
class CountingHashSet<E> extends HashSet<E> {
    private int addCount = 0;

    @Override
    public boolean add(E e) {
        addCount++;
        return super.add(e);
    }

    @Override
    public boolean addAll(java.util.Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c);  // Internally calls add() for each element!
    }

    public int getAddCount() { return addCount; }
}

public class Test {
    public static void main(String[] args) {
        CountingHashSet<String> set = new CountingHashSet<>();
        set.addAll(java.util.Arrays.asList("A", "B", "C"));
        System.out.println(set.getAddCount());  // Expected: 3
    }
}
```

### Output
```
6
```

### Explanation
- `addAll()` increments count by 3.
- `super.addAll()` internally calls `add()` for each element → increments count 3 more times.
- Total: 6 instead of expected 3.
- This is the **fragile base class problem** — parent's internal implementation details leak into child behavior.

### Fix — Composition (Wrapper/Decorator Pattern)

```java
import java.util.*;

class CountingSet<E> {
    private final Set<E> set = new HashSet<>();  // HAS-A (composition)
    private int addCount = 0;

    public boolean add(E e) {
        addCount++;
        return set.add(e);
    }

    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return set.addAll(c);  // Delegates — doesn't call OUR add()
    }

    public int getAddCount() { return addCount; }
    public int size() { return set.size(); }
}
```

Now `addAll` works correctly because we delegate to `HashSet.addAll()` without self-interference.

### When to Use Each

| Use Inheritance When | Use Composition When |
|---------------------|---------------------|
| True IS-A relationship | HAS-A relationship |
| Need polymorphism | Need flexibility |
| Extending an interface/abstract | Wrapping existing behavior |
| `Dog IS-A Animal` | `Car HAS-A Engine` |

---

## 45. SOLID Principles in Code — Liskov Substitution

### Rule
> **Liskov Substitution Principle (LSP):** Subtypes must be substitutable for their parent types without altering the correctness of the program.

### Snippet — LSP Violation

```java
class Rectangle {
    protected int width;
    protected int height;

    public void setWidth(int w) { this.width = w; }
    public void setHeight(int h) { this.height = h; }
    public int getArea() { return width * height; }
}

class Square extends Rectangle {
    @Override
    public void setWidth(int w) {
        this.width = w;
        this.height = w;  // Forces both to be equal
    }

    @Override
    public void setHeight(int h) {
        this.width = h;   // Forces both to be equal
        this.height = h;
    }
}

public class Test {
    static void resize(Rectangle r) {
        r.setWidth(5);
        r.setHeight(10);
        // Expectation: area = 50 for any Rectangle
        System.out.println("Area: " + r.getArea());
    }

    public static void main(String[] args) {
        resize(new Rectangle());  // Works correctly
        resize(new Square());     // Breaks expectation!
    }
}
```

### Output
```
Area: 50
Area: 100
```

### Explanation
- `Rectangle` expects independent width and height.
- `Square` forces both to be equal → `setHeight(10)` also changes width to 10.
- Result: 10 × 10 = 100 instead of 5 × 10 = 50.
- `Square` cannot be substituted for `Rectangle` without breaking behavior → **LSP violation**.

### Fix — Separate Abstractions

```java
interface Shape {
    int getArea();
}

class Rectangle implements Shape {
    private final int width, height;
    Rectangle(int w, int h) { width = w; height = h; }
    public int getArea() { return width * height; }
}

class Square implements Shape {
    private final int side;
    Square(int s) { side = s; }
    public int getArea() { return side * side; }
}
```

No inheritance relationship between Square and Rectangle — each implements the Shape contract correctly.

### All SOLID Principles — Quick Reference

| Principle | Meaning | Violation Sign |
|-----------|---------|---------------|
| **S** — Single Responsibility | One class, one reason to change | Class doing UI + DB + logic |
| **O** — Open/Closed | Open for extension, closed for modification | `if/else` chains for new types |
| **L** — Liskov Substitution | Subtypes replace parents safely | Override breaks parent's contract |
| **I** — Interface Segregation | Small, focused interfaces | Clients forced to implement unused methods |
| **D** — Dependency Inversion | Depend on abstractions, not concretions | `new ConcreteClass()` inside business logic |

---

## Final Summary — Complete OOP Coverage

| Section | Core Concept | Category |
|---------|-------------|----------|
| 1–6 | Method overriding rules (access, exceptions) | Inheritance |
| 7 | Variable hiding | Inheritance |
| 8–9 | Constructors, super(), this() | Constructors |
| 10 | super.method() | Inheritance |
| 11–13 | Upcasting, downcasting, ClassCastException | Polymorphism |
| 14–16 | Overriding vs overloading, dynamic dispatch | Polymorphism |
| 17–19 | Parent reference limits, tricky combo questions | Polymorphism |
| 20 | Constructor vs method naming | Constructors |
| 21–22 | String pool, Integer cache | Immutability |
| 23, 35 | finally block behavior | Exception Handling |
| 24 | Initializer blocks | Class Loading |
| 25 | Covariant return types | Inheritance |
| 26 | Private methods (no polymorphism) | Encapsulation |
| 27, 38 | Interface defaults, multiple inheritance | Interfaces |
| 28, 40 | equals/hashCode contract | Object Class |
| 29 | String immutability | Immutability |
| 30 | try-with-resources order | Exception Handling |
| 31, 33 | Static binding, varargs resolution | Overloading |
| 32 | Abstract class constructors | Abstraction |
| 34 | Post-increment trap | Language Quirks |
| 36 | Defensive copying | Encapsulation |
| 37 | this() chaining | Constructors |
| 39 | Shallow vs deep copy | Cloning |
| 41 | Inner/nested/anonymous classes | Encapsulation |
| 42 | Enum constructors, singleton pattern | Enums |
| 43 | Generics type erasure | Generics |
| 44 | Composition vs inheritance | Design |
| 45 | SOLID / Liskov Substitution | Design Principles |


---

## 46. Comparable — Natural Ordering

### Rule
> `Comparable<T>` defines the **natural ordering** of a class. Implement `compareTo()` in the class itself. Used by `Collections.sort()`, `TreeSet`, `TreeMap` by default.

### Snippet

```java
import java.util.*;

class Employee implements Comparable<Employee> {
    String name;
    int salary;

    Employee(String name, int salary) {
        this.name = name;
        this.salary = salary;
    }

    @Override
    public int compareTo(Employee other) {
        return this.salary - other.salary;  // Ascending by salary
    }

    @Override
    public String toString() {
        return name + "(" + salary + ")";
    }
}

public class Test {
    public static void main(String[] args) {
        List<Employee> list = new ArrayList<>();
        list.add(new Employee("Alice", 70000));
        list.add(new Employee("Bob", 50000));
        list.add(new Employee("Charlie", 60000));

        Collections.sort(list);  // Uses compareTo()
        System.out.println(list);
    }
}
```

### Output
```
[Bob(50000), Charlie(60000), Alice(70000)]
```

### Explanation
- `Employee` implements `Comparable<Employee>`.
- `compareTo()` returns:
  - Negative → `this` comes before `other`
  - Zero → equal
  - Positive → `this` comes after `other`
- `Collections.sort()` uses this natural ordering automatically.

### compareTo() Return Values

```
┌─────────────────────────────────────────────┐
│  this.compareTo(other)                       │
│                                             │
│  Negative  →  this < other  (this first)    │
│  Zero      →  this == other (equal)         │
│  Positive  →  this > other  (other first)   │
└─────────────────────────────────────────────┘
```

### ⚠️ Integer Overflow Trap

```java
// ❌ DANGEROUS for large values:
public int compareTo(Employee other) {
    return this.salary - other.salary;  // Can overflow!
}

// ✅ SAFE approach:
public int compareTo(Employee other) {
    return Integer.compare(this.salary, other.salary);
}
```

---

## 47. Comparator — Custom Ordering

### Rule
> `Comparator<T>` defines **external/custom ordering**. It's a separate class or lambda — doesn't modify the original class. Allows multiple sort strategies.

### Snippet

```java
import java.util.*;

class Student {
    String name;
    int age;
    double gpa;

    Student(String name, int age, double gpa) {
        this.name = name;
        this.age = age;
        this.gpa = gpa;
    }

    @Override
    public String toString() {
        return name + "(age=" + age + ", gpa=" + gpa + ")";
    }
}

public class Test {
    public static void main(String[] args) {
        List<Student> students = Arrays.asList(
            new Student("Alice", 22, 3.8),
            new Student("Bob", 20, 3.9),
            new Student("Charlie", 21, 3.8)
        );

        // Sort by age (anonymous class)
        Collections.sort(students, new Comparator<Student>() {
            @Override
            public int compare(Student a, Student b) {
                return Integer.compare(a.age, b.age);
            }
        });
        System.out.println("By age: " + students);

        // Sort by GPA descending (lambda)
        students.sort((a, b) -> Double.compare(b.gpa, a.gpa));
        System.out.println("By GPA desc: " + students);

        // Sort by name (method reference)
        students.sort(Comparator.comparing(s -> s.name));
        System.out.println("By name: " + students);
    }
}
```

### Output
```
By age: [Bob(age=20, gpa=3.9), Charlie(age=21, gpa=3.8), Alice(age=22, gpa=3.8)]
By GPA desc: [Bob(age=20, gpa=3.9), Alice(age=22, gpa=3.8), Charlie(age=21, gpa=3.8)]
By name: [Alice(age=22, gpa=3.8), Bob(age=20, gpa=3.9), Charlie(age=21, gpa=3.8)]
```

### Explanation
- `Comparator` is external — doesn't require modifying the `Student` class.
- You can have unlimited sorting strategies for the same class.
- Java 8 lambdas make comparators concise.

### Three Ways to Create a Comparator

```java
// 1. Anonymous class (pre-Java 8)
Comparator<Student> byAge = new Comparator<Student>() {
    public int compare(Student a, Student b) {
        return Integer.compare(a.age, b.age);
    }
};

// 2. Lambda expression (Java 8+)
Comparator<Student> byAge = (a, b) -> Integer.compare(a.age, b.age);

// 3. Comparator.comparing() (Java 8+ — cleanest)
Comparator<Student> byAge = Comparator.comparingInt(s -> s.age);
```

---

## 48. Multiple Field Sorting with Comparator Chaining

### Rule
> Use `thenComparing()` to chain multiple sort criteria. First comparator is primary, subsequent ones break ties.

### Snippet

```java
import java.util.*;

class Product {
    String category;
    String name;
    double price;

    Product(String category, String name, double price) {
        this.category = category;
        this.name = name;
        this.price = price;
    }

    @Override
    public String toString() {
        return category + "/" + name + "($" + price + ")";
    }
}

public class Test {
    public static void main(String[] args) {
        List<Product> products = Arrays.asList(
            new Product("Electronics", "Phone", 999),
            new Product("Books", "Java", 49),
            new Product("Electronics", "Laptop", 1299),
            new Product("Books", "Python", 39),
            new Product("Electronics", "Tablet", 499)
        );

        // Sort by category ASC, then price DESC
        products.sort(
            Comparator.comparing((Product p) -> p.category)
                .thenComparing(Comparator.comparingDouble((Product p) -> p.price).reversed())
        );

        products.forEach(System.out::println);
    }
}
```

### Output
```
Books/Java($49.0)
Books/Python($39.0)
Electronics/Laptop($1299.0)
Electronics/Phone($999.0)
Electronics/Tablet($499.0)
```

### Explanation
- Primary sort: `category` ascending (Books before Electronics).
- Secondary sort: `price` descending within each category.
- `thenComparing()` only kicks in when the primary comparison returns 0 (tie).

### Chaining Methods

```java
Comparator.comparing(Employee::getDepartment)       // 1st: department
    .thenComparing(Employee::getSalary)             // 2nd: salary
    .thenComparing(Employee::getName)               // 3rd: name (tie-breaker)
    .reversed();                                    // Reverse entire chain
```

---

## 49. Comparable vs Comparator — When to Use Which

### Rule
> Use `Comparable` for the **one natural ordering**. Use `Comparator` for **alternative/multiple orderings**.

### Side-by-Side Comparison

| Feature | Comparable | Comparator |
|---------|-----------|------------|
| Package | `java.lang` | `java.util` |
| Method | `compareTo(T o)` | `compare(T a, T b)` |
| Modifies class? | Yes (implements interface) | No (external) |
| # of orderings | One (natural) | Unlimited |
| Usage | `Collections.sort(list)` | `Collections.sort(list, comparator)` |
| Lambda support | No (it's in the class) | Yes |
| Used by default in | TreeSet, TreeMap, sort() | Only when explicitly passed |

### Interview Snippet — Both Together

```java
import java.util.*;

class Person implements Comparable<Person> {
    String name;
    int age;

    Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // Natural ordering = by name
    @Override
    public int compareTo(Person other) {
        return this.name.compareTo(other.name);
    }

    @Override
    public String toString() { return name + "(" + age + ")"; }
}

public class Test {
    public static void main(String[] args) {
        List<Person> people = Arrays.asList(
            new Person("Charlie", 25),
            new Person("Alice", 30),
            new Person("Bob", 20)
        );

        // Natural ordering (Comparable — by name)
        Collections.sort(people);
        System.out.println("Natural: " + people);

        // Custom ordering (Comparator — by age)
        people.sort(Comparator.comparingInt(p -> p.age));
        System.out.println("By age:  " + people);

        // TreeSet uses natural ordering
        TreeSet<Person> set = new TreeSet<>(people);
        System.out.println("TreeSet: " + set);

        // TreeSet with custom Comparator
        TreeSet<Person> byAge = new TreeSet<>(Comparator.comparingInt(p -> p.age));
        byAge.addAll(people);
        System.out.println("TreeSet by age: " + byAge);
    }
}
```

### Output
```
Natural: [Alice(30), Bob(20), Charlie(25)]
By age:  [Bob(20), Charlie(25), Alice(30)]
TreeSet: [Alice(30), Bob(20), Charlie(25)]
TreeSet by age: [Bob(20), Charlie(25), Alice(30)]
```

### Decision Flowchart

```
Do you OWN the class?
├── Yes → Does it have ONE obvious ordering?
│         ├── Yes → Implement Comparable
│         └── No  → Use Comparator externally
└── No (third-party class) → Use Comparator (only option)
```

---

## 50. Sorting Traps and Tricky Snippets

### Trap 1 — compareTo() Inconsistent with equals()

```java
import java.util.*;

class Item implements Comparable<Item> {
    String name;
    int priority;

    Item(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    @Override
    public int compareTo(Item other) {
        return Integer.compare(this.priority, other.priority);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Item) return this.name.equals(((Item) o).name);
        return false;
    }

    @Override
    public String toString() { return name + "(" + priority + ")"; }
}

public class Test {
    public static void main(String[] args) {
        Item a = new Item("Task-A", 1);
        Item b = new Item("Task-B", 1);  // Same priority, different name

        System.out.println(a.equals(b));      // ?
        System.out.println(a.compareTo(b));   // ?

        TreeSet<Item> set = new TreeSet<>();
        set.add(a);
        set.add(b);
        System.out.println("TreeSet size: " + set.size());  // ?
    }
}
```

### Output
```
false
0
TreeSet size: 1
```

### Explanation
- `equals()` says they're different (different names).
- `compareTo()` says they're equal (same priority).
- `TreeSet` uses `compareTo()` — thinks they're duplicates → keeps only one!
- **Rule:** `compareTo()` should be consistent with `equals()` for sorted collections.

---

### Trap 2 — Sorting a List of Mixed Types

```java
import java.util.*;

public class Test {
    public static void main(String[] args) {
        List list = new ArrayList();  // Raw type
        list.add("Banana");
        list.add(1);
        list.add("Apple");

        Collections.sort(list);  // ?
    }
}
```

### Output
```
Exception: ClassCastException — Integer cannot be cast to String
```

### Explanation
- `Collections.sort()` calls `compareTo()` between elements.
- `"Banana".compareTo(1)` fails — incompatible types.
- Raw types bypass generic safety → runtime blow-up.
- **Fix:** Always use generics: `List<String>` or `List<Integer>`.

---

### Trap 3 — Comparator Returning Only -1, 0, 1

```java
import java.util.*;

public class Test {
    public static void main(String[] args) {
        List<Integer> nums = Arrays.asList(5, 3, 8, 1, 9);

        // ❌ Wrong — always returns -1 or 1
        nums.sort((a, b) -> a > b ? 1 : -1);
        System.out.println(nums);

        // ✅ Correct — handles equality
        nums.sort((a, b) -> Integer.compare(a, b));
        System.out.println(nums);
    }
}
```

### Output
```
[5, 3, 1, 8, 9]    (may vary — unstable!)
[1, 3, 5, 8, 9]    (always correct)
```

### Explanation
- Never returning `0` breaks the sort contract (transitivity).
- The sort algorithm assumes equal elements can stay in place — skipping `0` causes unpredictable results.
- **Always use** `Integer.compare()`, `Double.compare()`, or `Comparator.comparing()`.

---

## 51. Collections.sort() vs List.sort() vs Arrays.sort()

### Rule
> All three sort, but differ in usage and implementation.

### Snippet

```java
import java.util.*;

public class Test {
    public static void main(String[] args) {
        // 1. Collections.sort() — for List
        List<String> list1 = new ArrayList<>(Arrays.asList("Banana", "Apple", "Cherry"));
        Collections.sort(list1);
        System.out.println("Collections.sort: " + list1);

        // 2. List.sort() — Java 8+ instance method
        List<String> list2 = new ArrayList<>(Arrays.asList("Banana", "Apple", "Cherry"));
        list2.sort(Comparator.reverseOrder());
        System.out.println("List.sort:        " + list2);

        // 3. Arrays.sort() — for arrays
        int[] arr = {5, 2, 8, 1, 9};
        Arrays.sort(arr);
        System.out.println("Arrays.sort:      " + Arrays.toString(arr));

        // 4. Arrays.sort with range
        int[] arr2 = {5, 2, 8, 1, 9};
        Arrays.sort(arr2, 1, 4);  // Sort index 1 to 3 only
        System.out.println("Partial sort:     " + Arrays.toString(arr2));
    }
}
```

### Output
```
Collections.sort: [Apple, Banana, Cherry]
List.sort:        [Cherry, Banana, Apple]
Arrays.sort:      [1, 2, 5, 8, 9]
Partial sort:     [5, 1, 2, 8, 9]
```

### Comparison Table

| Method | Works On | Algorithm | Stable? | Null-safe? |
|--------|----------|-----------|:-------:|:----------:|
| `Collections.sort(list)` | List | TimSort | ✅ Yes | ❌ No |
| `list.sort(comparator)` | List | TimSort | ✅ Yes | ❌ No |
| `Arrays.sort(arr)` (primitives) | Array | Dual-Pivot QuickSort | ❌ No | N/A |
| `Arrays.sort(arr)` (objects) | Array | TimSort | ✅ Yes | ❌ No |

### Interview Note
> `Collections.sort()` internally calls `list.sort()` since Java 8. They're equivalent now. Prefer `list.sort()` for readability.

---

## 52. Reverse Order, Null Handling, and Natural Order

### Rule
> Java provides utility comparators for common needs: reversing, null-first/null-last, and natural ordering.

### Snippet

```java
import java.util.*;

public class Test {
    public static void main(String[] args) {
        // 1. Reverse natural order
        List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5));
        nums.sort(Comparator.reverseOrder());
        System.out.println("Reversed: " + nums);

        // 2. Null handling
        List<String> withNulls = new ArrayList<>(Arrays.asList("Banana", null, "Apple", null, "Cherry"));

        // nullsFirst — nulls at the beginning
        withNulls.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
        System.out.println("Nulls first: " + withNulls);

        // nullsLast — nulls at the end
        withNulls.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        System.out.println("Nulls last:  " + withNulls);

        // 3. Complex: sort by length, nulls last, reverse
        List<String> words = new ArrayList<>(Arrays.asList("hi", null, "hello", "a", null, "world"));
        words.sort(
            Comparator.nullsLast(
                Comparator.comparingInt(String::length).reversed()
            )
        );
        System.out.println("By length desc, nulls last: " + words);
    }
}
```

### Output
```
Reversed: [5, 4, 3, 1, 1]
Nulls first: [null, null, Apple, Banana, Cherry]
Nulls last:  [Apple, Banana, Cherry, null, null]
By length desc, nulls last: [hello, world, hi, a, null, null]
```

### Explanation
- `Comparator.reverseOrder()` — reverses natural ordering.
- `Comparator.nullsFirst(comp)` — treats `null` as less than everything.
- `Comparator.nullsLast(comp)` — treats `null` as greater than everything.
- These can be chained with any other comparator.

### Utility Comparators Cheat Sheet

```java
// Natural order (ascending)
Comparator.naturalOrder()

// Reverse order (descending)
Comparator.reverseOrder()

// By specific field
Comparator.comparing(Person::getName)
Comparator.comparingInt(Person::getAge)
Comparator.comparingDouble(Person::getSalary)

// Null safety
Comparator.nullsFirst(Comparator.naturalOrder())
Comparator.nullsLast(Comparator.comparing(Person::getName))

// Chaining
Comparator.comparing(Person::getDept)
    .thenComparingInt(Person::getAge)
    .thenComparing(Person::getName)
    .reversed()
```

### Common Interview Question — Sort Map by Value

```java
import java.util.*;
import java.util.stream.*;

public class Test {
    public static void main(String[] args) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("Alice", 85);
        scores.put("Bob", 92);
        scores.put("Charlie", 78);

        // Sort by value descending
        Map<String, Integer> sorted = scores.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        System.out.println(sorted);
    }
}
```

**Output:** `{Bob=92, Alice=85, Charlie=78}`

---

## Comparable/Comparator Quick Reference

| Question | Answer |
|----------|--------|
| Where is Comparable? | `java.lang` (auto-imported) |
| Where is Comparator? | `java.util` (must import) |
| Can a class have both? | Yes — Comparable for default, Comparators for alternatives |
| What does TreeSet use? | Comparable by default, or Comparator if provided |
| What happens if neither? | `ClassCastException` at runtime |
| Is sort stable? | Yes (TimSort) — equal elements keep original order |
| `compareTo` contract with `equals`? | Should be consistent — TreeSet/TreeMap assume they agree |
| Fastest way to reverse? | `Comparator.reverseOrder()` or `.reversed()` |
| Handle nulls? | `Comparator.nullsFirst()` / `nullsLast()` |
