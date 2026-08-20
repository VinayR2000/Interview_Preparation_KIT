# Java 8 to 26 - Complete Features Guide for Interview Preparation

---

## Table of Contents

- [Java 8 (2014) - The Game Changer](#java-8-2014---the-game-changer)
  - [1. Lambda Expressions](#1-lambda-expressions)
  - [2. Functional Interfaces](#2-functional-interfaces)
  - [3. Stream API](#3-stream-api)
  - [4. Optional](#4-optional)
  - [5. Method References](#5-method-references)
  - [6. Default and Static Methods in Interfaces](#6-default-and-static-methods-in-interfaces)
  - [7. Date and Time API (java.time)](#7-date-and-time-api-javatime)
  - [8. CompletableFuture](#8-completablefuture)
- [Java 9 (2017) - Modularity & Improvements](#java-9-2017---modularity--improvements)
  - [1. Module System (Project Jigsaw)](#1-module-system-project-jigsaw)
  - [2. JShell (REPL)](#2-jshell-repl)
  - [3. Collection Factory Methods](#3-collection-factory-methods)
  - [4. Private Methods in Interfaces](#4-private-methods-in-interfaces)
  - [5. Stream API Enhancements](#5-stream-api-enhancements)
  - [6. Optional Enhancements](#6-optional-enhancements)
  - [7. Try-with-Resources Enhancement](#7-try-with-resources-enhancement)
  - [8. Process API Improvements](#8-process-api-improvements)
- [Java 10 (2018) - Local Variable Type Inference](#java-10-2018---local-variable-type-inference)
  - [1. var - Local Variable Type Inference](#1-var---local-variable-type-inference)
  - [2. Unmodifiable Collections](#2-unmodifiable-collections)
- [Java 11 (2018, LTS) - String & HTTP Client](#java-11-2018-lts---string--http-client)
  - [1. New String Methods](#1-new-string-methods)
  - [2. HTTP Client (Standard)](#2-http-client-standard)
  - [3. Running Single-File Programs](#3-running-single-file-programs)
  - [4. Optional.isEmpty()](#4-optionalisempty)
  - [5. Files Utility Methods](#5-files-utility-methods)
  - [6. var in Lambda Parameters](#6-var-in-lambda-parameters)
- [Java 12 (2019) - Switch Expressions Preview](#java-12-2019---switch-expressions-preview)
  - [1. Switch Expressions (Preview)](#1-switch-expressions-preview)
  - [2. Compact Number Formatting](#2-compact-number-formatting)
  - [3. String Indentation and Transform](#3-string-indentation-and-transform)
- [Java 13 (2019) - Text Blocks Preview](#java-13-2019---text-blocks-preview)
  - [1. Text Blocks (Preview)](#1-text-blocks-preview)
  - [2. Switch Expressions Enhancement (yield)](#2-switch-expressions-enhancement-yield)
- [Java 14 (2020) - Records & Pattern Matching](#java-14-2020---records--pattern-matching)
  - [1. Records](#1-records)
  - [2. Pattern Matching for instanceof](#2-pattern-matching-for-instanceof)
  - [3. Helpful NullPointerExceptions](#3-helpful-nullpointerexceptions)
  - [4. Switch Expressions (Standard)](#4-switch-expressions-standard)
- [Java 15 (2020) - Sealed Classes Preview](#java-15-2020---sealed-classes-preview)
  - [1. Sealed Classes (Preview)](#1-sealed-classes-preview)
  - [2. Text Blocks (Standard)](#2-text-blocks-standard)
  - [3. Hidden Classes](#3-hidden-classes)
- [Java 16 (2021) - Records & Pattern Matching Standard](#java-16-2021---records--pattern-matching-standard)
  - [1. Records (Standard)](#1-records-standard)
  - [2. Pattern Matching for instanceof (Standard)](#2-pattern-matching-for-instanceof-standard)
  - [3. Stream.toList()](#3-streamtolist)
  - [4. Day Period Support](#4-day-period-support)
- [Java 17 (2021, LTS) - Sealed Classes Standard](#java-17-2021-lts---sealed-classes-standard)
  - [1. Sealed Classes (Standard)](#1-sealed-classes-standard)
  - [2. Pattern Matching for switch (Preview)](#2-pattern-matching-for-switch-preview)
  - [3. Restore Always-Strict Floating-Point Semantics](#3-restore-always-strict-floating-point-semantics)
  - [4. Deprecation of Security Manager](#4-deprecation-of-security-manager)
- [Java 18 (2022) - Simple Web Server](#java-18-2022---simple-web-server)
  - [1. Simple Web Server](#1-simple-web-server)
  - [2. Code Snippets in JavaDoc](#2-code-snippets-in-javadoc)
  - [3. UTF-8 by Default](#3-utf-8-by-default)
- [Java 19 (2022) - Virtual Threads Preview](#java-19-2022---virtual-threads-preview)
  - [1. Virtual Threads (Preview)](#1-virtual-threads-preview)
  - [2. Structured Concurrency (Incubator)](#2-structured-concurrency-incubator)
  - [3. Record Patterns (Preview)](#3-record-patterns-preview)
- [Java 20 (2023) - Scoped Values Preview](#java-20-2023---scoped-values-preview)
  - [1. Scoped Values (Incubator)](#1-scoped-values-incubator)
  - [2. Record Patterns (Second Preview)](#2-record-patterns-second-preview)
- [Java 21 (2023, LTS) - Virtual Threads & Pattern Matching Standard](#java-21-2023-lts---virtual-threads--pattern-matching-standard)
  - [1. Virtual Threads (Standard)](#1-virtual-threads-standard)
  - [2. Pattern Matching for switch (Standard)](#2-pattern-matching-for-switch-standard)
  - [3. Record Patterns (Standard)](#3-record-patterns-standard)
  - [4. Sequenced Collections](#4-sequenced-collections)
  - [5. String Templates (Preview)](#5-string-templates-preview)
  - [6. Unnamed Patterns and Variables (Preview)](#6-unnamed-patterns-and-variables-preview)
- [Java 22 (2024) - Unnamed Variables Standard](#java-22-2024---unnamed-variables-standard)
  - [1. Unnamed Variables (Standard)](#1-unnamed-variables-standard)
  - [2. Statements Before super() (Preview)](#2-statements-before-super-preview)
  - [3. Stream Gatherers (Preview)](#3-stream-gatherers-preview)
  - [4. Foreign Function & Memory API](#4-foreign-function--memory-api-second-preview---will-be-standard)
- [Java 23 (2024) - Primitive Patterns & Markdown JavaDoc](#java-23-2024---primitive-patterns--markdown-javadoc)
  - [1. Primitive Types in Patterns (Preview)](#1-primitive-types-in-patterns-preview)
  - [2. Markdown Documentation Comments](#2-markdown-documentation-comments)
  - [3. Flexible Constructor Bodies (Second Preview)](#3-flexible-constructor-bodies-second-preview)
  - [4. Stream Gatherers (Second Preview)](#4-stream-gatherers-second-preview)
  - [5. Structured Concurrency (Third Preview)](#5-structured-concurrency-third-preview)
  - [6. String Templates Removed](#6-string-templates-removed)
- [Java 24 (2025) - Stream Gatherers Standard](#java-24-2025---stream-gatherers-standard)
  - [1. Stream Gatherers (Standard)](#1-stream-gatherers-standard)
  - [2. Flexible Constructor Bodies (Third Preview)](#2-flexible-constructor-bodies-third-preview)
  - [3. Scoped Values (Third Preview)](#3-scoped-values-third-preview)
  - [4. Class-File API (Standard)](#4-class-file-api-standard)
  - [5. Ahead-of-Time Class Loading & Linking](#5-ahead-of-time-class-loading--linking)
- [Java 25 (2025) - Compact Source Files & Patterns](#java-25-2025---compact-source-files--patterns)
  - [1. Compact Source Files and Instance Main Methods (Standard)](#1-compact-source-files-and-instance-main-methods-standard)
  - [2. Structured Concurrency (Fourth Preview)](#2-structured-concurrency-fourth-preview)
  - [3. Scoped Values (Fourth Preview)](#3-scoped-values-fourth-preview)
  - [4. Flexible Constructor Bodies (Standard)](#4-flexible-constructor-bodies-standard)
  - [5. Module Import Declarations (Preview)](#5-module-import-declarations-preview)
- [Java 26 (2025/2026) - Latest Features](#java-26-20252026---latest-features)
  - [1. Primitive Types in Patterns (Standard anticipated)](#1-primitive-types-in-patterns-standard-anticipated)
  - [2. Stable Values (Preview)](#2-stable-values-preview)
  - [3. Compact Object Headers (Experimental)](#3-compact-object-headers-experimental)
  - [4. Generational ZGC as Default](#4-generational-zgc-as-default)
  - [5. Key Encapsulation Mechanism (KEM) API](#5-key-encapsulation-mechanism-kem-api)
- [Interview Quick Reference - Version by Feature Matrix](#interview-quick-reference---version-by-feature-matrix)
- [Common Interview Questions & Answers](#common-interview-questions--answers)
- [Summary of LTS Versions (Focus for Interviews)](#summary-of-lts-versions-focus-for-interviews)

---

## Java 8 (2014) - The Game Changer

### 1. Lambda Expressions

**Explanation:** Lambda expressions provide a concise way to represent anonymous functions (functional interfaces). They enable functional programming in Java.

**Syntax:** `(parameters) -> expression` or `(parameters) -> { statements; }`

```java
// Before Java 8 - Anonymous inner class
Runnable r1 = new Runnable() {
    @Override
    public void run() {
        System.out.println("Hello from thread");
    }
};

// Java 8 - Lambda expression
Runnable r2 = () -> System.out.println("Hello from thread");

// With parameters
Comparator<String> comp = (s1, s2) -> s1.compareTo(s2);

// Multiple statements
Comparator<String> comp2 = (s1, s2) -> {
    System.out.println("Comparing: " + s1 + " and " + s2);
    return s1.length() - s2.length();
};
```

**Interview Tip:** Lambdas can only be used with functional interfaces (interfaces with exactly one abstract method).

---

### 2. Functional Interfaces

**Explanation:** An interface with exactly one abstract method. Annotated with `@FunctionalInterface`. Java 8 provides built-in functional interfaces in `java.util.function` package.

```java
@FunctionalInterface
interface Calculator {
    int calculate(int a, int b);
}

// Usage
Calculator add = (a, b) -> a + b;
Calculator multiply = (a, b) -> a * b;

System.out.println(add.calculate(5, 3));       // 8
System.out.println(multiply.calculate(5, 3));  // 15

// Built-in Functional Interfaces
// Predicate<T> - takes T, returns boolean
Predicate<Integer> isEven = n -> n % 2 == 0;
System.out.println(isEven.test(4));  // true

// Function<T, R> - takes T, returns R
Function<String, Integer> strLength = String::length;
System.out.println(strLength.apply("Hello"));  // 5

// Consumer<T> - takes T, returns void
Consumer<String> printer = System.out::println;
printer.accept("Hello World");

// Supplier<T> - takes nothing, returns T
Supplier<Double> randomVal = Math::random;
System.out.println(randomVal.get());

// UnaryOperator<T> - takes T, returns T
UnaryOperator<String> toUpper = String::toUpperCase;
System.out.println(toUpper.apply("hello"));  // HELLO

// BinaryOperator<T> - takes (T, T), returns T
BinaryOperator<Integer> sum = Integer::sum;
System.out.println(sum.apply(3, 7));  // 10
```

---

### 3. Stream API

**Explanation:** Streams provide a functional approach to processing collections of objects. They support sequential and parallel operations.

```java
List<String> names = Arrays.asList("Alice", "Bob", "Charlie", "David", "Eve");

// filter - filters elements based on a predicate
List<String> longNames = names.stream()
    .filter(name -> name.length() > 3)
    .collect(Collectors.toList());
// [Alice, Charlie, David]

// map - transforms each element
List<Integer> nameLengths = names.stream()
    .map(String::length)
    .collect(Collectors.toList());
// [5, 3, 7, 5, 3]

// sorted
List<String> sorted = names.stream()
    .sorted()
    .collect(Collectors.toList());

// reduce - combines elements into a single result
int totalLength = names.stream()
    .map(String::length)
    .reduce(0, Integer::sum);
// 23

// flatMap - flattens nested collections
List<List<Integer>> nestedList = Arrays.asList(
    Arrays.asList(1, 2, 3),
    Arrays.asList(4, 5, 6),
    Arrays.asList(7, 8, 9)
);
List<Integer> flatList = nestedList.stream()
    .flatMap(Collection::stream)
    .collect(Collectors.toList());
// [1, 2, 3, 4, 5, 6, 7, 8, 9]

// Collectors - groupingBy, partitioningBy
Map<Integer, List<String>> byLength = names.stream()
    .collect(Collectors.groupingBy(String::length));
// {3=[Bob, Eve], 5=[Alice, David], 7=[Charlie]}

Map<Boolean, List<String>> partitioned = names.stream()
    .collect(Collectors.partitioningBy(n -> n.length() > 3));

// distinct, count, findFirst, anyMatch
long count = names.stream().filter(n -> n.startsWith("A")).count();
Optional<String> first = names.stream().filter(n -> n.startsWith("C")).findFirst();
boolean anyMatch = names.stream().anyMatch(n -> n.length() > 5);

// Parallel Streams
long sum = LongStream.rangeClosed(1, 1_000_000)
    .parallel()
    .sum();
```

---

### 4. Optional

**Explanation:** A container object that may or may not contain a non-null value. Designed to avoid NullPointerException.

```java
// Creating Optional
Optional<String> empty = Optional.empty();
Optional<String> name = Optional.of("Alice");         // throws NPE if null
Optional<String> nullable = Optional.ofNullable(null); // allows null

// Checking value
name.isPresent();  // true
empty.isPresent(); // false

// Getting value
name.get();                          // "Alice" (throws NoSuchElementException if empty)
empty.orElse("Default");             // "Default"
empty.orElseGet(() -> "Computed");   // "Computed" (lazy evaluation)
empty.orElseThrow(() -> new RuntimeException("Not found"));

// Transforming
Optional<Integer> length = name.map(String::length);  // Optional[5]
Optional<String> upper = name.map(String::toUpperCase); // Optional["ALICE"]

// flatMap - when transformation returns Optional
Optional<String> result = name.flatMap(n -> 
    n.length() > 3 ? Optional.of(n.toUpperCase()) : Optional.empty()
);

// filter
Optional<String> filtered = name.filter(n -> n.startsWith("A")); // Optional["Alice"]

// Chaining
String displayName = Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("Unknown City");
```

---

### 5. Method References

**Explanation:** Shorthand notation of a lambda expression to call a method. Four types exist.

```java
// 1. Static method reference - ClassName::staticMethod
Function<String, Integer> parser = Integer::parseInt;
// equivalent to: s -> Integer.parseInt(s)

// 2. Instance method of a particular object - object::instanceMethod
String str = "Hello";
Supplier<Integer> lengthSupplier = str::length;
// equivalent to: () -> str.length()

// 3. Instance method of an arbitrary object - ClassName::instanceMethod
Function<String, String> toUpper = String::toUpperCase;
// equivalent to: s -> s.toUpperCase()

// 4. Constructor reference - ClassName::new
Supplier<ArrayList<String>> listFactory = ArrayList::new;
Function<Integer, int[]> arrayFactory = int[]::new;
```

---

### 6. Default and Static Methods in Interfaces

**Explanation:** Interfaces can now have method implementations using `default` and `static` keywords.

```java
interface Vehicle {
    // Abstract method
    void start();
    
    // Default method - provides default implementation
    default void honk() {
        System.out.println("Beep beep!");
    }
    
    // Static method - utility method in interface
    static Vehicle create(String type) {
        if ("car".equals(type)) return new Car();
        return new Bike();
    }
}

class Car implements Vehicle {
    @Override
    public void start() {
        System.out.println("Car starting...");
    }
    
    // Can override default method
    @Override
    public void honk() {
        System.out.println("Car horn!");
    }
}

// Diamond problem resolution
interface A { default void hello() { System.out.println("A"); } }
interface B { default void hello() { System.out.println("B"); } }

class C implements A, B {
    @Override
    public void hello() {
        A.super.hello(); // explicitly choose
    }
}
```

---

### 7. Date and Time API (java.time)

**Explanation:** New immutable, thread-safe date-time API replacing the old `java.util.Date` and `Calendar`.

```java
// LocalDate - date without time
LocalDate today = LocalDate.now();
LocalDate birthday = LocalDate.of(1990, Month.JANUARY, 15);
LocalDate parsed = LocalDate.parse("2024-01-15");

// LocalTime - time without date
LocalTime now = LocalTime.now();
LocalTime meeting = LocalTime.of(14, 30, 0);

// LocalDateTime - date and time without timezone
LocalDateTime dateTime = LocalDateTime.now();
LocalDateTime specific = LocalDateTime.of(2024, 6, 15, 10, 30);

// ZonedDateTime - with timezone
ZonedDateTime zoned = ZonedDateTime.now(ZoneId.of("America/New_York"));

// Period and Duration
Period period = Period.between(birthday, today);
System.out.println("Age: " + period.getYears() + " years");

Duration duration = Duration.between(LocalTime.of(9, 0), LocalTime.of(17, 0));
System.out.println("Work hours: " + duration.toHours());

// Formatting
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
String formatted = dateTime.format(formatter);
LocalDateTime parsedDT = LocalDateTime.parse("15-06-2024 10:30", formatter);

// Manipulation (immutable - returns new instance)
LocalDate nextWeek = today.plusWeeks(1);
LocalDate lastMonth = today.minusMonths(1);
LocalDate firstDayOfMonth = today.withDayOfMonth(1);
```

---

### 8. CompletableFuture

**Explanation:** Enhanced Future with support for asynchronous programming, chaining, and combining multiple futures.

```java
// Basic async execution
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // Simulating long-running task
    try { Thread.sleep(1000); } catch (InterruptedException e) {}
    return "Hello from async";
});

// Chaining with thenApply (map), thenAccept (consume), thenRun (side-effect)
CompletableFuture<Integer> lengthFuture = future
    .thenApply(String::toUpperCase)
    .thenApply(String::length);

// Combining futures
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "Hello");
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> "World");

CompletableFuture<String> combined = future1.thenCombine(future2, (s1, s2) -> s1 + " " + s2);
System.out.println(combined.get()); // "Hello World"

// allOf - wait for all to complete
CompletableFuture<Void> all = CompletableFuture.allOf(future1, future2);

// anyOf - first to complete
CompletableFuture<Object> any = CompletableFuture.anyOf(future1, future2);

// Exception handling
CompletableFuture<String> safe = CompletableFuture.supplyAsync(() -> {
    if (true) throw new RuntimeException("Oops");
    return "OK";
}).exceptionally(ex -> "Fallback: " + ex.getMessage())
  .handle((result, ex) -> ex != null ? "Error" : result);
```

---

## Java 9 (2017) - Modularity & Improvements

### 1. Module System (Project Jigsaw)

**Explanation:** Introduces a module system to encapsulate packages and control access between modules. Improves security, maintainability, and performance.

```java
// module-info.java
module com.myapp {
    requires java.sql;           // depends on java.sql module
    requires transitive java.logging; // transitive dependency
    exports com.myapp.api;       // exports package for others to use
    opens com.myapp.internal to com.framework; // allows reflection
    provides com.myapp.spi.Service with com.myapp.impl.ServiceImpl;
    uses com.myapp.spi.Service;
}
```

---

### 2. JShell (REPL)

**Explanation:** Interactive Read-Eval-Print Loop for quick prototyping and testing Java code without creating a full class.

```shell
$ jshell
jshell> int x = 10;
x ==> 10

jshell> System.out.println("Hello " + x);
Hello 10

jshell> List.of(1, 2, 3).stream().map(i -> i * 2).toList()
$3 ==> [2, 4, 6]
```

---

### 3. Collection Factory Methods

**Explanation:** Convenient factory methods to create immutable collections.

```java
// Immutable List
List<String> list = List.of("A", "B", "C");
// list.add("D"); // throws UnsupportedOperationException

// Immutable Set
Set<Integer> set = Set.of(1, 2, 3, 4);

// Immutable Map
Map<String, Integer> map = Map.of("one", 1, "two", 2, "three", 3);

// For more than 10 entries
Map<String, Integer> largeMap = Map.ofEntries(
    Map.entry("one", 1),
    Map.entry("two", 2),
    Map.entry("three", 3)
);
```

---

### 4. Private Methods in Interfaces

**Explanation:** Interfaces can have private methods to share code between default methods.

```java
interface Logger {
    default void logInfo(String message) {
        log("INFO", message);
    }
    
    default void logError(String message) {
        log("ERROR", message);
    }
    
    // Private helper method - avoids code duplication
    private void log(String level, String message) {
        System.out.println("[" + level + "] " + LocalDateTime.now() + ": " + message);
    }
    
    // Private static method
    private static String formatMessage(String msg) {
        return msg.trim().toUpperCase();
    }
}
```

---

### 5. Stream API Enhancements

```java
// takeWhile - takes elements while predicate is true (ordered streams)
List<Integer> result = Stream.of(1, 2, 3, 4, 5, 6, 1, 2)
    .takeWhile(n -> n < 4)
    .collect(Collectors.toList());
// [1, 2, 3]

// dropWhile - drops elements while predicate is true
List<Integer> result2 = Stream.of(1, 2, 3, 4, 5, 6, 1, 2)
    .dropWhile(n -> n < 4)
    .collect(Collectors.toList());
// [4, 5, 6, 1, 2]

// ofNullable - creates a stream of 0 or 1 element
Stream<String> stream = Stream.ofNullable(null);  // empty stream
Stream<String> stream2 = Stream.ofNullable("Hi"); // stream of one element

// iterate with predicate (like a for-loop)
Stream.iterate(1, n -> n <= 10, n -> n + 1)
    .forEach(System.out::println); // prints 1 to 10
```

---

### 6. Optional Enhancements

```java
Optional<String> opt = Optional.of("Hello");

// ifPresentOrElse
opt.ifPresentOrElse(
    val -> System.out.println("Found: " + val),
    () -> System.out.println("Not found")
);

// or - lazy alternative Optional
Optional<String> result = Optional.<String>empty()
    .or(() -> Optional.of("Fallback"));

// stream - converts Optional to Stream
List<String> list = opt.stream().collect(Collectors.toList()); // ["Hello"]
```

---

### 7. Try-with-Resources Enhancement

```java
// Java 7 - had to declare in try
try (BufferedReader br = new BufferedReader(new FileReader("file.txt"))) {
    // use br
}

// Java 9 - can use effectively final variables
BufferedReader br = new BufferedReader(new FileReader("file.txt"));
try (br) {  // br is effectively final
    System.out.println(br.readLine());
}
```

---

### 8. Process API Improvements

```java
// Get current process info
ProcessHandle current = ProcessHandle.current();
System.out.println("PID: " + current.pid());
System.out.println("User: " + current.info().user().orElse("unknown"));
System.out.println("Command: " + current.info().command().orElse("unknown"));

// List all processes
ProcessHandle.allProcesses()
    .filter(p -> p.info().command().isPresent())
    .limit(5)
    .forEach(p -> System.out.println(p.pid() + " - " + p.info().command().get()));
```

---

## Java 10 (2018) - Local Variable Type Inference

### 1. var - Local Variable Type Inference

**Explanation:** The `var` keyword lets the compiler infer the type of local variables. Reduces boilerplate while maintaining type safety.

```java
// Type is inferred by the compiler
var list = new ArrayList<String>();        // ArrayList<String>
var stream = list.stream();                // Stream<String>
var map = Map.of("key", "value");          // Map<String, String>

// Works in for-loops
var numbers = List.of(1, 2, 3, 4, 5);
for (var number : numbers) {
    System.out.println(number);
}

// Works in try-with-resources
try (var reader = new BufferedReader(new FileReader("file.txt"))) {
    var line = reader.readLine();
}

// CANNOT be used:
// var x;                    // no initializer
// var x = null;             // null type
// var x = {1, 2, 3};       // array initializer
// var x = () -> "hello";   // lambda (no target type)
// Fields, method parameters, or return types
```

**Interview Tip:** `var` is NOT a keyword (it's a reserved type name). You can still name a variable `var`.

---

### 2. Unmodifiable Collections

```java
// copyOf - creates unmodifiable copy
List<String> original = new ArrayList<>(Arrays.asList("A", "B", "C"));
List<String> unmodifiable = List.copyOf(original);
// unmodifiable.add("D"); // throws UnsupportedOperationException

// Collectors.toUnmodifiableList/Set/Map
List<String> immutable = original.stream()
    .filter(s -> s.length() > 0)
    .collect(Collectors.toUnmodifiableList());
```

---

## Java 11 (2018, LTS) - String & HTTP Client

### 1. New String Methods

```java
String str = "  Hello World  ";

str.isBlank();        // false (checks whitespace-only too)
"   ".isBlank();      // true

str.strip();          // "Hello World" (Unicode-aware trim)
str.stripLeading();   // "Hello World  "
str.stripTrailing();  // "  Hello World"

"Hi\nWorld\n".lines().count();  // 2 (returns Stream<String>)

"Ha".repeat(3);       // "HaHaHa"
```

---

### 2. HTTP Client (Standard)

**Explanation:** New standard HTTP Client replacing the legacy `HttpURLConnection`. Supports HTTP/2, async operations, and WebSocket.

```java
// Synchronous GET
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Accept", "application/json")
    .GET()
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode());  // 200
System.out.println(response.body());

// Asynchronous GET
CompletableFuture<HttpResponse<String>> asyncResponse = 
    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
asyncResponse.thenAccept(r -> System.out.println(r.body()));

// POST with body
HttpRequest postRequest = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Alice\"}"))
    .build();
```

---

### 3. Running Single-File Programs

```shell
# No need to compile first
$ java HelloWorld.java
```

---

### 4. Optional.isEmpty()

```java
Optional<String> opt = Optional.empty();
opt.isEmpty();   // true (opposite of isPresent)
```

---

### 5. Files Utility Methods

```java
// Read and write strings directly
Path path = Path.of("test.txt");
Files.writeString(path, "Hello, Java 11!");
String content = Files.readString(path);
```

---

### 6. var in Lambda Parameters

```java
// Allows annotations on lambda parameters
list.stream()
    .map((@NotNull var s) -> s.toUpperCase())
    .collect(Collectors.toList());
```

---

## Java 12 (2019) - Switch Expressions Preview

### 1. Switch Expressions (Preview)

**Explanation:** Switch can now be used as an expression that returns a value. Arrow syntax eliminates fall-through bugs.

```java
// Traditional switch (statement)
String day = "MONDAY";
int numLetters;
switch (day) {
    case "MONDAY": case "FRIDAY": case "SUNDAY":
        numLetters = 6;
        break;
    case "TUESDAY":
        numLetters = 7;
        break;
    default:
        numLetters = -1;
}

// Java 12+ Switch expression with arrow syntax
int numLetters2 = switch (day) {
    case "MONDAY", "FRIDAY", "SUNDAY" -> 6;
    case "TUESDAY" -> 7;
    case "WEDNESDAY" -> 9;
    case "THURSDAY", "SATURDAY" -> 8;
    default -> -1;
};
```

---

### 2. Compact Number Formatting

```java
NumberFormat shortFormat = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
System.out.println(shortFormat.format(1000));      // "1K"
System.out.println(shortFormat.format(1_000_000)); // "1M"

NumberFormat longFormat = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.LONG);
System.out.println(longFormat.format(1000));       // "1 thousand"
```

---

### 3. String Indentation and Transform

```java
String text = "Hello\nWorld";
String indented = text.indent(4);  // adds 4 spaces to each line
// "    Hello\n    World\n"

// transform - applies a function to a string
String result = "hello".transform(s -> s.toUpperCase())
                       .transform(s -> s + "!");
// "HELLO!"
```

---

## Java 13 (2019) - Text Blocks Preview

### 1. Text Blocks (Preview)

**Explanation:** Multi-line string literals that preserve formatting. Eliminates escape sequences for multi-line strings.

```java
// Before - messy string concatenation
String json = "{\n" +
              "    \"name\": \"Alice\",\n" +
              "    \"age\": 30\n" +
              "}";

// Java 13+ Text Block
String jsonBlock = """
        {
            "name": "Alice",
            "age": 30
        }
        """;

// SQL query
String sql = """
        SELECT id, name, email
        FROM users
        WHERE active = true
        ORDER BY name
        """;

// HTML
String html = """
        <html>
            <body>
                <h1>Hello, World!</h1>
            </body>
        </html>
        """;
```

---

### 2. Switch Expressions Enhancement (yield)

```java
// When you need a block in switch expression, use 'yield'
int result = switch (day) {
    case "MONDAY" -> 1;
    case "TUESDAY" -> 2;
    case "WEDNESDAY" -> {
        System.out.println("Mid-week");
        yield 3;  // 'yield' returns value from block
    }
    default -> 0;
};
```

---

## Java 14 (2020) - Records & Pattern Matching

### 1. Records

**Explanation:** Compact syntax for immutable data carrier classes. Automatically generates constructor, getters, `equals()`, `hashCode()`, and `toString()`.

```java
// Before - verbose POJO
public class PointOld {
    private final int x;
    private final int y;
    
    public PointOld(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public int getX() { return x; }
    public int getY() { return y; }
    // equals, hashCode, toString...
}

// Java 14+ Record
public record Point(int x, int y) {}

// Usage
Point p = new Point(5, 10);
System.out.println(p.x());       // 5 (accessor method, not getX())
System.out.println(p.y());       // 10
System.out.println(p);           // Point[x=5, y=10]

// Custom constructor (compact canonical)
public record Person(String name, int age) {
    // Compact canonical constructor - validation
    public Person {
        if (age < 0) throw new IllegalArgumentException("Age cannot be negative");
        name = name.trim();  // can reassign parameters
    }
}

// Records can implement interfaces
public record Employee(String name, double salary) implements Comparable<Employee> {
    @Override
    public int compareTo(Employee other) {
        return Double.compare(this.salary, other.salary);
    }
}

// Records can have static fields, static methods, and instance methods
public record Range(int start, int end) {
    // Custom instance method
    public int length() {
        return end - start;
    }
    
    // Static factory method
    public static Range of(int start, int end) {
        return new Range(start, end);
    }
}
```

**Interview Tip:** Records cannot extend other classes (they implicitly extend `Record`), cannot be abstract, and their fields are always `final`.

---

### 2. Pattern Matching for instanceof

**Explanation:** Eliminates the need for explicit casting after an `instanceof` check.

```java
// Before Java 14
Object obj = "Hello";
if (obj instanceof String) {
    String s = (String) obj;  // explicit cast needed
    System.out.println(s.toUpperCase());
}

// Java 14+ Pattern matching
if (obj instanceof String s) {
    System.out.println(s.toUpperCase());  // s is already cast
}

// Works with logical operators
if (obj instanceof String s && s.length() > 3) {
    System.out.println(s);
}

// Negation pattern
if (!(obj instanceof String s)) {
    // s is NOT in scope here
    return;
}
// s IS in scope here (flow scoping)
System.out.println(s.toUpperCase());
```

---

### 3. Helpful NullPointerExceptions

**Explanation:** NPEs now tell you exactly which variable was null.

```java
String city = user.getAddress().getCity().toUpperCase();
// Before: NullPointerException (which one is null?)
// Java 14+: Cannot invoke "Address.getCity()" because the return value of 
//           "User.getAddress()" is null
```

---

### 4. Switch Expressions (Standard)

Switch expressions become a standard feature (no longer preview) in Java 14.

---

## Java 15 (2020) - Sealed Classes Preview

### 1. Sealed Classes (Preview)

**Explanation:** Restricts which classes can extend/implement a class/interface. Enables exhaustive pattern matching.

```java
// Only Circle, Rectangle, and Triangle can extend Shape
public sealed class Shape permits Circle, Rectangle, Triangle {
    // common shape logic
}

public final class Circle extends Shape {
    private final double radius;
    public Circle(double radius) { this.radius = radius; }
}

public sealed class Rectangle extends Shape permits Square {
    private final double width, height;
    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }
}

public final class Square extends Rectangle {
    public Square(double side) { super(side, side); }
}

// non-sealed allows unrestricted subclassing
public non-sealed class Triangle extends Shape {
    // any class can extend Triangle
}
```

**Interview Tip:** Subclasses must be `final`, `sealed`, or `non-sealed`.

---

### 2. Text Blocks (Standard)

Text Blocks become standard in Java 15 with escape sequences:

```java
String text = """
        This is a line \
        that continues here""";  
// "This is a line that continues here" (\ prevents newline)

String withSpace = """
        trailing spaces   \s
        are preserved     \s""";
// \s prevents trailing whitespace stripping
```

---

### 3. Hidden Classes

**Explanation:** Classes that cannot be discovered or used directly by other classes. Used by frameworks for dynamic proxy generation.

```java
// Used internally by frameworks - not directly created by developers
// Useful for dynamic class generation in frameworks like Spring, Hibernate
```

---

## Java 16 (2021) - Records & Pattern Matching Standard

### 1. Records (Standard)

Records become a standard feature. Can also be local (defined inside methods):

```java
public List<String> processData(List<String> items) {
    // Local record inside a method
    record Pair(String original, String processed) {}
    
    return items.stream()
        .map(item -> new Pair(item, item.toUpperCase()))
        .filter(pair -> pair.processed().length() > 3)
        .map(Pair::processed)
        .collect(Collectors.toList());
}
```

---

### 2. Pattern Matching for instanceof (Standard)

Now a permanent feature.

---

### 3. Stream.toList()

```java
// Before
List<String> list = stream.collect(Collectors.toList());

// Java 16+ - returns unmodifiable list
List<String> list = stream.toList();
```

---

### 4. Day Period Support

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm B");
System.out.println(LocalTime.now().format(formatter));
// e.g., "2:30 in the afternoon"
```

---

## Java 17 (2021, LTS) - Sealed Classes Standard

### 1. Sealed Classes (Standard)

Sealed Classes become standard (covered in Java 15 section above).

---

### 2. Pattern Matching for switch (Preview)

**Explanation:** Extends pattern matching to switch statements/expressions for type patterns.

```java
// Type pattern in switch
static String formatValue(Object obj) {
    return switch (obj) {
        case Integer i -> "Integer: " + i;
        case Long l    -> "Long: " + l;
        case Double d  -> "Double: " + d;
        case String s  -> "String: " + s;
        case null      -> "null";
        default        -> "Unknown: " + obj;
    };
}

// Guarded patterns
static String categorize(Object obj) {
    return switch (obj) {
        case Integer i when i > 0 -> "Positive integer";
        case Integer i when i < 0 -> "Negative integer";
        case Integer i            -> "Zero";
        case String s when s.isEmpty() -> "Empty string";
        case String s             -> "String: " + s;
        default                   -> "Other";
    };
}
```

---

### 3. Restore Always-Strict Floating-Point Semantics

All floating-point operations are now always strict (equivalent to `strictfp`).

---

### 4. Deprecation of Security Manager

The Security Manager is deprecated for removal.

---

## Java 18 (2022) - Simple Web Server

### 1. Simple Web Server

**Explanation:** Built-in lightweight HTTP server for prototyping and testing.

```shell
# Start a simple file server from command line
$ jwebserver --port 8080 --directory /path/to/files
```

```java
// Programmatic usage
var server = SimpleFileServer.createFileServer(
    new InetSocketAddress(8080),
    Path.of("/tmp/www"),
    SimpleFileServer.OutputLevel.VERBOSE
);
server.start();
```

---

### 2. Code Snippets in JavaDoc

```java
/**
 * Example usage:
 * {@snippet :
 *     List<String> list = List.of("a", "b", "c");
 *     list.forEach(System.out::println); // @highlight substring="forEach"
 * }
 */
public void exampleMethod() { }
```

---

### 3. UTF-8 by Default

Java 18 uses UTF-8 as the default charset for all APIs.

```java
// No need to specify UTF-8 explicitly anymore
Charset defaultCharset = Charset.defaultCharset(); // UTF-8
```

---

## Java 19 (2022) - Virtual Threads Preview

### 1. Virtual Threads (Preview)

**Explanation:** Lightweight threads managed by the JVM, not the OS. Enables millions of concurrent threads for high-throughput I/O-bound applications.

```java
// Creating a virtual thread
Thread vThread = Thread.ofVirtual().start(() -> {
    System.out.println("Running in virtual thread: " + Thread.currentThread());
});

// Named virtual thread
Thread named = Thread.ofVirtual().name("my-vthread").start(() -> {
    System.out.println("Named virtual thread");
});

// Virtual thread executor - scales to millions of tasks
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // Each task gets its own virtual thread
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> {
            Thread.sleep(Duration.ofSeconds(1));
            return "Done";
        });
    }
} // executor auto-closes, waits for all tasks
```

---

### 2. Structured Concurrency (Incubator)

**Explanation:** Treats related concurrent tasks as a single unit of work. If one task fails, others are cancelled.

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> user = scope.fork(() -> fetchUser(userId));
    Subtask<List<Order>> orders = scope.fork(() -> fetchOrders(userId));
    
    scope.join();           // wait for all
    scope.throwIfFailed();  // propagate errors
    
    // Both completed successfully
    return new UserProfile(user.get(), orders.get());
}
```

---

### 3. Record Patterns (Preview)

**Explanation:** Deconstruct record values directly in pattern matching.

```java
record Point(int x, int y) {}

// Deconstruct in instanceof
Object obj = new Point(3, 4);
if (obj instanceof Point(int x, int y)) {
    System.out.println("x=" + x + ", y=" + y);
}

// Nested record patterns
record Line(Point start, Point end) {}

Object line = new Line(new Point(0, 0), new Point(5, 5));
if (line instanceof Line(Point(int x1, int y1), Point(int x2, int y2))) {
    System.out.println("Line from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
}
```

---

## Java 20 (2023) - Scoped Values Preview

### 1. Scoped Values (Incubator)

**Explanation:** A safer, more performant alternative to ThreadLocal for sharing immutable data within a thread/task scope.

```java
// Define a scoped value
private static final ScopedValue<String> USER = ScopedValue.newInstance();

// Bind and use
ScopedValue.runWhere(USER, "Alice", () -> {
    System.out.println("User: " + USER.get());  // "Alice"
    processRequest();  // inner methods can also access USER.get()
});

// More efficient than ThreadLocal:
// - Immutable within scope (no accidental mutation)
// - Automatically cleaned up
// - Works naturally with virtual threads
```

---

### 2. Record Patterns (Second Preview)

Enhanced with support for generic record patterns and improved inference.

---

## Java 21 (2023, LTS) - Virtual Threads & Pattern Matching Standard

### 1. Virtual Threads (Standard)

**Now a permanent feature.** Revolutionizes Java concurrency.

```java
// Simple creation
Thread.ofVirtual().start(() -> System.out.println("Virtual!"));

// With executor (most common pattern)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < 1_000_000; i++) {
        final int taskId = i;
        futures.add(executor.submit(() -> {
            Thread.sleep(100);  // I/O simulation
            return "Task " + taskId + " done";
        }));
    }
    // Process results
    for (Future<String> future : futures) {
        String result = future.get();
    }
}

// Spring Boot integration (application.properties)
// spring.threads.virtual.enabled=true
```

**Interview Tip:** Virtual threads are ideal for I/O-bound tasks (HTTP calls, DB queries). NOT for CPU-bound work. Don't pool virtual threads - create new ones per task.

---

### 2. Pattern Matching for switch (Standard)

```java
// Complete exhaustive pattern matching with sealed types
sealed interface Shape permits Circle, Rectangle, Triangle {}
record Circle(double radius) implements Shape {}
record Rectangle(double width, double height) implements Shape {}
record Triangle(double base, double height) implements Shape {}

static double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
        case Triangle t -> 0.5 * t.base() * t.height();
        // No default needed - compiler knows all cases are covered (sealed)
    };
}

// Null handling in switch
static String process(String s) {
    return switch (s) {
        case null -> "null input";
        case String str when str.isBlank() -> "blank";
        case String str -> str.toUpperCase();
    };
}
```

---

### 3. Record Patterns (Standard)

```java
record Address(String city, String country) {}
record Person(String name, Address address) {}

// Nested deconstruction
static String getCity(Object obj) {
    return switch (obj) {
        case Person(String name, Address(String city, String country)) -> city;
        default -> "Unknown";
    };
}

// In enhanced for loop
record Point(int x, int y) {}
List<Point> points = List.of(new Point(1, 2), new Point(3, 4));
for (Point(int x, int y) : points) {
    System.out.println("x=" + x + ", y=" + y);
}
```

---

### 4. Sequenced Collections

**Explanation:** New interfaces that provide uniform access to first/last elements and reverse ordering for ordered collections.

```java
// New interfaces: SequencedCollection, SequencedSet, SequencedMap
// Adds: getFirst(), getLast(), reversed(), addFirst(), addLast()

List<String> list = new ArrayList<>(List.of("A", "B", "C"));
list.getFirst();   // "A"
list.getLast();    // "C"
list.reversed();   // ["C", "B", "A"] (reversed view)

// Works with LinkedHashSet, TreeSet
SequencedSet<String> set = new LinkedHashSet<>(List.of("X", "Y", "Z"));
set.getFirst();    // "X"
set.getLast();     // "Z"

// SequencedMap
SequencedMap<String, Integer> map = new LinkedHashMap<>();
map.put("one", 1);
map.put("two", 2);
map.put("three", 3);
map.firstEntry();  // one=1
map.lastEntry();   // three=3
map.reversed();    // reversed view of the map
```

---

### 5. String Templates (Preview)

**Explanation:** Safe and composable string interpolation.

```java
// STR template processor (Preview in 21)
String name = "Alice";
int age = 30;
String greeting = STR."Hello, \{name}! You are \{age} years old.";
// "Hello, Alice! You are 30 years old."

// Expressions in templates
String calc = STR."2 + 3 = \{2 + 3}";  // "2 + 3 = 5"

// Multi-line
String json = STR."""
        {
            "name": "\{name}",
            "age": \{age}
        }
        """;

// FMT processor for formatting
double price = 19.99;
String formatted = FMT."Price: $%.2f\{price}";  // "Price: $19.99"
```

**Note:** String Templates were later removed in Java 23 after feedback.

---

### 6. Unnamed Patterns and Variables (Preview)

```java
// _ for unused variables
try {
    // ...
} catch (Exception _) {  // don't need the exception variable
    System.out.println("An error occurred");
}

// In pattern matching
if (obj instanceof Point(int x, _)) {
    System.out.println("x = " + x);  // don't care about y
}

// In switch
switch (shape) {
    case Circle _ -> System.out.println("It's a circle");
    case Rectangle _ -> System.out.println("It's a rectangle");
    default -> System.out.println("Unknown shape");
}

// In enhanced for
for (var _ : collection) {
    count++;  // only care about iteration count
}
```

---

## Java 22 (2024) - Unnamed Variables Standard

### 1. Unnamed Variables (Standard)

```java
// Standard use of _ for unused variables
Map<String, List<Integer>> map = Map.of("a", List.of(1, 2), "b", List.of(3, 4));

// Only care about values, not keys
for (var entry : map.entrySet()) {
    var _ = entry.getKey(); // unused
    process(entry.getValue());
}

// In try-catch
try {
    Integer.parseInt("abc");
} catch (NumberFormatException _) {
    System.out.println("Invalid number");
}

// Multiple unused in same scope
var _ = sideEffect1();
var _ = sideEffect2();  // both can be _ in same scope!
```

---

### 2. Statements Before super() (Preview)

**Explanation:** Allows validation logic before calling `super()` in constructors.

```java
// Before Java 22 - could NOT have statements before super()
// Had to use workarounds like static factory methods

// Java 22+ - statements before super()
public class PositiveInteger extends Number {
    private final int value;
    
    public PositiveInteger(int value) {
        // Validation BEFORE super()
        if (value <= 0) {
            throw new IllegalArgumentException("Must be positive: " + value);
        }
        super();  // now allowed after statements
        this.value = value;
    }
}
```

---

### 3. Stream Gatherers (Preview)

**Explanation:** Custom intermediate stream operations. Fills the gap where built-in stream operations are insufficient.

```java
// Built-in gatherers
import java.util.stream.Gatherers;

// windowFixed - groups into fixed-size windows
List<List<Integer>> windows = Stream.of(1, 2, 3, 4, 5, 6, 7)
    .gather(Gatherers.windowFixed(3))
    .toList();
// [[1, 2, 3], [4, 5, 6], [7]]

// windowSliding - sliding window
List<List<Integer>> sliding = Stream.of(1, 2, 3, 4, 5)
    .gather(Gatherers.windowSliding(3))
    .toList();
// [[1, 2, 3], [2, 3, 4], [3, 4, 5]]

// fold - stateful accumulation (more powerful than reduce)
Optional<String> result = Stream.of("a", "b", "c")
    .gather(Gatherers.fold(() -> "", (acc, elem) -> acc + elem))
    .findFirst();
// Optional["abc"]

// scan - running accumulation, emits intermediate results
List<Integer> runningSum = Stream.of(1, 2, 3, 4, 5)
    .gather(Gatherers.scan(() -> 0, Integer::sum))
    .toList();
// [1, 3, 6, 10, 15]

// Custom gatherer
Gatherer<Integer, ?, Integer> doubler = Gatherer.of(
    (_, element, downstream) -> downstream.push(element * 2)
);
List<Integer> doubled = Stream.of(1, 2, 3)
    .gather(doubler)
    .toList();
// [2, 4, 6]
```

---

### 4. Foreign Function & Memory API (Second Preview -> will be standard)

**Explanation:** Safely interact with native code and memory outside the JVM. Replaces JNI with a safer, more performant API.

```java
// Call a native C function
try (Arena arena = Arena.ofConfined()) {
    // Allocate off-heap memory
    MemorySegment segment = arena.allocateFrom("Hello, Native!");
    
    // Look up a native function
    Linker linker = Linker.nativeLinker();
    SymbolLookup stdlib = linker.defaultLookup();
    MethodHandle strlen = linker.downcallHandle(
        stdlib.find("strlen").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
    );
    
    long len = (long) strlen.invoke(segment);
    System.out.println("Length: " + len);  // 14
}
```

---

## Java 23 (2024) - Primitive Patterns & Markdown JavaDoc

### 1. Primitive Types in Patterns (Preview)

**Explanation:** Allows primitive types in pattern matching, including widening, narrowing, and unboxing conversions.

```java
// Primitive type patterns in switch
static String classify(Number number) {
    return switch (number) {
        case Integer i when i > 0 -> "Positive int: " + i;
        case Integer i -> "Non-positive int: " + i;
        case Double d -> "Double: " + d;
        case Long l -> "Long: " + l;
        default -> "Other number";
    };
}

// Primitive narrowing in switch
static String sizeCategory(int size) {
    return switch (size) {
        case 0 -> "empty";
        case 1 -> "single";
        case int n when n > 0 && n <= 10 -> "small";
        case int n when n > 10 -> "large";
        default -> "negative";
    };
}
```

---

### 2. Markdown Documentation Comments

**Explanation:** Write JavaDoc using Markdown syntax instead of HTML.

```java
/// # Method Summary
/// 
/// This method calculates the **area** of a circle.
/// 
/// ## Parameters
/// - `radius` - the radius of the circle (must be positive)
/// 
/// ## Returns
/// The area as a `double` value
/// 
/// ## Example
/// ```java
/// double area = calculateArea(5.0); // 78.54
/// ```
/// 
/// @param radius the radius
/// @return the area
public double calculateArea(double radius) {
    return Math.PI * radius * radius;
}
```

---

### 3. Flexible Constructor Bodies (Second Preview)

```java
public class Range {
    private final int start;
    private final int end;
    
    public Range(int start, int end) {
        // Statements before super() - validation
        if (start > end) {
            throw new IllegalArgumentException(
                "start (%d) must be <= end (%d)".formatted(start, end)
            );
        }
        this.start = start;  // Can even assign fields before super
        super();
        this.end = end;
    }
}
```

---

### 4. Stream Gatherers (Second Preview)

Same as Java 22 with refinements. See Java 22 section.

---

### 5. Structured Concurrency (Third Preview)

```java
// Structured concurrency - tasks as a unit
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> userData = scope.fork(() -> callUserService());
    Subtask<List<Order>> orderData = scope.fork(() -> callOrderService());
    
    scope.join();
    scope.throwIfFailed();
    
    // Both succeeded - use results
    return new Dashboard(userData.get(), orderData.get());
}
// If callUserService() fails, callOrderService() is automatically cancelled
```

---

### 6. String Templates Removed

String Templates (STR."...") were removed after receiving negative feedback. The feature is being redesigned.

---

## Java 24 (2025) - Stream Gatherers Standard

### 1. Stream Gatherers (Standard)

Stream Gatherers become a permanent feature in Java 24.

```java
// All Gatherers are now standard
import java.util.stream.Gatherers;

// Practical example: batch processing
List<List<Record>> batches = records.stream()
    .gather(Gatherers.windowFixed(100))
    .toList();
batches.forEach(batch -> processBatch(batch));

// Running average
List<Double> movingAvg = prices.stream()
    .gather(Gatherers.windowSliding(5))
    .map(window -> window.stream().mapToDouble(d -> d).average().orElse(0))
    .toList();
```

---

### 2. Flexible Constructor Bodies (Third Preview)

Now allows assigning fields before `this()` or `super()` calls.

---

### 3. Scoped Values (Third Preview)

```java
private static final ScopedValue<UserContext> CONTEXT = ScopedValue.newInstance();

public void handleRequest(UserContext ctx) {
    ScopedValue.runWhere(CONTEXT, ctx, () -> {
        // All methods in this scope can access the context
        processBusinessLogic();
    });
}

private void processBusinessLogic() {
    UserContext ctx = CONTEXT.get(); // Access without passing as parameter
    System.out.println("Processing for: " + ctx.username());
}
```

---

### 4. Class-File API (Standard)

**Explanation:** Standard API for reading, writing, and transforming Java class files. Replaces ASM for bytecode manipulation.

```java
// Read a class file
ClassModel classModel = ClassFile.of().parse(bytes);

// Transform a class
byte[] newBytes = ClassFile.of().transform(classModel, (builder, element) -> {
    if (element instanceof MethodModel method) {
        // Transform methods
        builder.accept(element);
    } else {
        builder.accept(element);
    }
});

// Generate a class from scratch
byte[] generated = ClassFile.of().build(ClassDesc.of("com.example.Generated"), cb -> {
    cb.withFlags(ClassFile.ACC_PUBLIC);
    cb.withMethod("hello", MethodTypeDesc.of(CD_String), ClassFile.ACC_PUBLIC, mb -> {
        mb.withCode(code -> {
            code.ldc("Hello from generated class!");
            code.areturn();
        });
    });
});
```

---

### 5. Ahead-of-Time Class Loading & Linking

Improves startup time by pre-loading and linking classes.

---

## Java 25 (2025) - Compact Source Files & Patterns

### 1. Compact Source Files and Instance Main Methods (Standard)

**Explanation:** Simplifies writing small programs. No need for class declaration or `public static void main(String[] args)`.

```java
// Before - full ceremony
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}

// Java 25 - Compact source file (implicitly declared class)
void main() {
    System.out.println("Hello, World!");
}

// With args
void main(String[] args) {
    System.out.println("Hello, " + args[0]);
}

// Can have other methods and fields in the implicit class
String greeting = "Hello";

void main() {
    greet("World");
}

void greet(String name) {
    System.out.println(greeting + ", " + name + "!");
}
```

---

### 2. Structured Concurrency (Fourth Preview)

```java
// ShutdownOnSuccess - returns first successful result
try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
    scope.fork(() -> queryMirror1());
    scope.fork(() -> queryMirror2());
    scope.fork(() -> queryMirror3());
    
    scope.join();
    String fastest = scope.result();  // first successful result
}
```

---

### 3. Scoped Values (Fourth Preview)

Continued refinements to the API.

---

### 4. Flexible Constructor Bodies (Standard)

```java
// Now standard - validation before super()
public class ValidatedList<E> extends ArrayList<E> {
    public ValidatedList(Collection<E> items) {
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        super(items); // super after validation
    }
}
```

---

### 5. Module Import Declarations (Preview)

**Explanation:** Import all exported packages of a module with a single statement.

```java
// Import all packages from java.base module
import module java.base;

// Instead of individual imports:
// import java.util.*;
// import java.io.*;
// import java.time.*;
// etc.

void main() {
    var list = List.of(1, 2, 3);
    var now = LocalDateTime.now();
    var path = Path.of("test.txt");
}
```

---

## Java 26 (2025/2026) - Latest Features

### 1. Primitive Types in Patterns (Standard anticipated)

```java
// Pattern matching with primitives - fully standard
static String describe(Object obj) {
    return switch (obj) {
        case int i when i > 0    -> "positive int";
        case int i               -> "non-positive int";
        case double d when d > 0 -> "positive double";
        case double d            -> "non-positive double";
        case String s            -> "string: " + s;
        default                  -> "other: " + obj;
    };
}
```

---

### 2. Stable Values (Preview)

**Explanation:** Lazily-initialized, immutable values. Thread-safe alternative to double-checked locking.

```java
// StableValue - lazy singleton
private static final StableValue<ExpensiveService> SERVICE = StableValue.of();

public static ExpensiveService getService() {
    return SERVICE.orElseSet(() -> new ExpensiveService());
}

// Only computed once, thread-safe, no volatile/synchronized needed
```

---

### 3. Compact Object Headers (Experimental)

Reduces object header size from 12-16 bytes to 8 bytes, improving memory usage.

---

### 4. Generational ZGC as Default

Z Garbage Collector (generational mode) becomes the default GC for improved throughput and lower latency.

---

### 5. Key Encapsulation Mechanism (KEM) API

```java
// Post-quantum cryptography support
KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
KeyPair keyPair = kpg.generateKeyPair();

KEM kem = KEM.getInstance("DHKEM");
KEM.Encapsulator enc = kem.newEncapsulator(keyPair.getPublic());
KEM.Encapsulated encapsulated = enc.encapsulate();

SecretKey sharedSecret = encapsulated.key();
byte[] encapsulation = encapsulated.encapsulation();
```

---

## Interview Quick Reference - Version by Feature Matrix

| Feature | Introduced | Standard |
|---------|-----------|----------|
| Lambda Expressions | Java 8 | Java 8 |
| Stream API | Java 8 | Java 8 |
| Optional | Java 8 | Java 8 |
| Modules | Java 9 | Java 9 |
| var (local) | Java 10 | Java 10 |
| HTTP Client | Java 11 | Java 11 |
| Switch Expressions | Java 12 (P) | Java 14 |
| Text Blocks | Java 13 (P) | Java 15 |
| Records | Java 14 (P) | Java 16 |
| instanceof Pattern | Java 14 (P) | Java 16 |
| Sealed Classes | Java 15 (P) | Java 17 |
| Virtual Threads | Java 19 (P) | Java 21 |
| Record Patterns | Java 19 (P) | Java 21 |
| Pattern switch | Java 17 (P) | Java 21 |
| Sequenced Collections | Java 21 | Java 21 |
| Unnamed Variables (_) | Java 21 (P) | Java 22 |
| Stream Gatherers | Java 22 (P) | Java 24 |
| Class-File API | Java 22 (P) | Java 24 |
| Compact Source Files | Java 21 (P) | Java 25 |
| Flexible Constructors | Java 22 (P) | Java 25 |
| Scoped Values | Java 20 (I) | TBD |
| Structured Concurrency | Java 19 (I) | TBD |

*(P) = Preview, (I) = Incubator*

---

## Common Interview Questions & Answers

### Q1: What is the difference between `map()` and `flatMap()` in Streams?

```java
// map: one-to-one transformation
List<String> words = List.of("Hello", "World");
List<Integer> lengths = words.stream().map(String::length).toList(); // [5, 5]

// flatMap: one-to-many, flattens nested streams
List<String> sentences = List.of("Hello World", "Java Stream");
List<String> allWords = sentences.stream()
    .flatMap(s -> Arrays.stream(s.split(" ")))
    .toList(); // ["Hello", "World", "Java", "Stream"]
```

---

### Q2: What is the difference between `findFirst()` and `findAny()`?

```java
// findFirst() - always returns first element (deterministic)
Optional<Integer> first = Stream.of(1, 2, 3, 4).findFirst(); // Optional[1]

// findAny() - returns any element (non-deterministic in parallel)
Optional<Integer> any = Stream.of(1, 2, 3, 4).parallel().findAny(); // Could be any element
```

**Use findAny()** in parallel streams for better performance when order doesn't matter.

---

### Q3: Difference between `var`, records, and sealed classes?

- **var**: Type inference for local variables (less typing, same type safety)
- **Records**: Immutable data carriers (replace POJOs/DTOs)
- **Sealed classes**: Restrict class hierarchy (enable exhaustive pattern matching)

---

### Q4: When should you use Virtual Threads vs Platform Threads?

```java
// Virtual Threads - I/O bound tasks (HTTP calls, DB, file I/O)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> httpClient.send(request, bodyHandler));
    executor.submit(() -> database.query(sql));
}

// Platform Threads - CPU bound tasks (computation, algorithms)
try (var executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
    executor.submit(() -> computeHash(data));
    executor.submit(() -> processImage(pixels));
}
```

---

### Q5: How does Sealed Classes enable exhaustive pattern matching?

```java
sealed interface Payment permits CreditCard, DebitCard, UPI {}
record CreditCard(String number, String expiry) implements Payment {}
record DebitCard(String number, String pin) implements Payment {}
record UPI(String vpa) implements Payment {}

// Compiler knows all cases - no default needed
String process(Payment payment) {
    return switch (payment) {
        case CreditCard cc -> "CC ending " + cc.number().substring(12);
        case DebitCard dc  -> "Debit: " + dc.number();
        case UPI upi       -> "UPI: " + upi.vpa();
    }; // Compile error if any case is missing
}
```

---

### Q6: What are the key differences between Optional methods?

```java
Optional<String> opt = getOptionalValue();

// orElse - always evaluates the alternative
opt.orElse(computeExpensiveDefault());  // computed even if opt has value

// orElseGet - lazy evaluation (preferred for expensive operations)
opt.orElseGet(() -> computeExpensiveDefault());  // only computed if empty

// orElseThrow - throws if empty
opt.orElseThrow(() -> new NotFoundException("Not found"));

// or() (Java 9+) - returns another Optional
opt.or(() -> getBackupOptional());
```

---

### Q7: Explain the Stream Pipeline Architecture

```java
List<String> result = employees.stream()     // Source
    .filter(e -> e.age() > 30)               // Intermediate (lazy)
    .map(Employee::name)                      // Intermediate (lazy)
    .sorted()                                 // Intermediate (stateful)
    .distinct()                               // Intermediate (stateful)
    .limit(5)                                 // Intermediate (short-circuit)
    .collect(Collectors.toList());            // Terminal (triggers execution)

// Key concepts:
// 1. Lazy evaluation - nothing happens until terminal operation
// 2. Short-circuit - can stop early (findFirst, limit, anyMatch)
// 3. Stateless vs Stateful - sorted/distinct need all elements
// 4. One terminal operation per stream
```

---

### Q8: CompletableFuture vs Future?

| Feature | Future | CompletableFuture |
|---------|--------|-------------------|
| Blocking get | ✅ | ✅ |
| Chaining | ❌ | ✅ (thenApply, thenCompose) |
| Combining | ❌ | ✅ (thenCombine, allOf) |
| Exception handling | ❌ | ✅ (exceptionally, handle) |
| Manual complete | ❌ | ✅ (complete, completeExceptionally) |
| Async callbacks | ❌ | ✅ (thenAcceptAsync) |

---

## Summary of LTS Versions (Focus for Interviews)

| LTS Version | Key Headlines |
|-------------|---------------|
| **Java 8** | Lambdas, Streams, Optional, Date/Time API, CompletableFuture |
| **Java 11** | var in lambdas, HTTP Client, String methods, single-file execution |
| **Java 17** | Sealed classes, Pattern matching switch (preview), Records stable |
| **Java 21** | Virtual Threads, Pattern matching switch (standard), Record Patterns, Sequenced Collections |
| **Java 25** | Compact source files, Flexible constructors, Stream Gatherers stable |

---

*Pro Tip: For interviews, focus on Java 8 features (most asked), Java 17 features (enterprise standard), and Java 21 features (modern Java). Be ready to explain Virtual Threads, Sealed Classes + Pattern Matching, and Records with practical examples.*
