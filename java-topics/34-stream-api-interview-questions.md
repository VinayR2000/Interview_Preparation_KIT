# 34. Stream API — Most Asked Interview Questions & Answers

---

## Category 1: Conceptual Questions

---

### Q1: What is a Stream in Java? How is it different from a Collection?

**A:**

| Aspect | Collection | Stream |
|--------|-----------|--------|
| Storage | Stores elements in memory | Does NOT store elements — computes on demand |
| Consumption | Can be iterated multiple times | Single-use — consumed after terminal operation |
| Modification | Can add/remove elements | Cannot modify source |
| Evaluation | Eager — elements exist immediately | Lazy — computed only when terminal op invoked |
| Size | Finite | Can be infinite (`Stream.iterate`, `Stream.generate`) |
| Purpose | Data storage and access | Data processing pipeline |

```java
List<String> list = List.of("a", "b", "c"); // data exists in memory
Stream<String> stream = list.stream();       // no processing until terminal op

stream.filter(s -> s.length() > 0)  // lazy — nothing happens yet
      .map(String::toUpperCase)      // lazy — still nothing
      .collect(Collectors.toList()); // terminal — NOW all operations execute
```

---

### Q2: What is lazy evaluation in Streams? Why is it important?

**A:** Lazy evaluation means intermediate operations are NOT executed until a terminal operation is invoked. Operations are chained and executed element-by-element (not operation-by-operation).

**Why it matters:**
- **Short-circuiting** — `findFirst()`, `limit()` can stop early without processing all elements
- **Performance** — avoids unnecessary computation
- **Fusion** — multiple operations can be fused into a single pass

```java
List<String> names = List.of("Alice", "Bob", "Charlie", "David", "Eve");

Optional<String> result = names.stream()
    .filter(s -> {
        System.out.println("filter: " + s);
        return s.length() > 3;
    })
    .map(s -> {
        System.out.println("map: " + s);
        return s.toUpperCase();
    })
    .findFirst();

// Output (processes element by element, stops at first match):
// filter: Alice
// map: Alice
// Result: Optional[ALICE]
// "Bob", "Charlie", "David", "Eve" are NEVER processed
```

---

### Q3: What is the difference between intermediate and terminal operations?

**A:**

| Intermediate (Lazy) | Terminal (Triggers Execution) |
|---------------------|------------------------------|
| Return a new `Stream` | Return non-stream result (void, Optional, Collection, etc.) |
| Not executed until terminal op | Triggers the entire pipeline |
| Can be chained | Only one per pipeline (final step) |
| `filter`, `map`, `sorted`, `distinct`, `limit`, `skip`, `flatMap`, `peek` | `collect`, `forEach`, `reduce`, `count`, `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`, `toArray`, `min`, `max` |

```java
// Intermediate — returns Stream<String>
Stream<String> filtered = names.stream().filter(s -> s.length() > 3);
// Nothing happened yet! No filtering occurred.

// Terminal — triggers execution, returns List<String>
List<String> result = filtered.collect(Collectors.toList());
// NOW filtering and collection happen
```

---

### Q4: What is the difference between `map()` and `flatMap()`?

**A:**
- `map()` — one-to-one transformation. Each input element produces exactly one output element.
- `flatMap()` — one-to-many transformation. Each input element produces a stream, all streams are flattened into one.

```java
// map: Stream<T> → Stream<R> (same number of elements)
List<String> words = List.of("hello", "world");
List<Integer> lengths = words.stream()
    .map(String::length)
    .collect(Collectors.toList()); // [5, 5]

// flatMap: Stream<T> → Stream<R> (flattened, may have more elements)
List<List<Integer>> nested = List.of(List.of(1,2), List.of(3,4), List.of(5));
List<Integer> flat = nested.stream()
    .flatMap(Collection::stream)
    .collect(Collectors.toList()); // [1, 2, 3, 4, 5]

// Real use case: split words into characters
List<String> chars = words.stream()
    .flatMap(w -> Arrays.stream(w.split("")))
    .collect(Collectors.toList());
// [h, e, l, l, o, w, o, r, l, d]
```

**Key rule:** Use `flatMap` when each element maps to a collection/stream and you want a flat result.

---

### Q5: What is the difference between `findFirst()` and `findAny()`?

**A:**

| | `findFirst()` | `findAny()` |
|---|---|---|
| Guarantee | Always returns FIRST element in encounter order | Returns ANY matching element |
| Sequential stream | Same as findAny | Same as findFirst |
| Parallel stream | Deterministic (always first) but slower | Non-deterministic but faster |
| Use when | Order matters | Order doesn't matter (just need any match) |

```java
// Sequential: both return "Alice"
Optional<String> first = names.stream()
    .filter(s -> s.length() > 3)
    .findFirst(); // Always "Alice"

// Parallel: findAny may return "Alice", "Charlie", or "David"
Optional<String> any = names.parallelStream()
    .filter(s -> s.length() > 3)
    .findAny(); // Could be any matching element — faster
```

**Interview tip:** Always mention that `findAny()` is preferred in parallel streams for performance.

---

### Q6: Explain `reduce()` — how does it work with identity, accumulator, and combiner?

**A:** `reduce()` combines all elements into a single result by repeatedly applying a function.

**Three forms:**

```java
// Form 1: No identity — returns Optional (stream might be empty)
Optional<Integer> max = Stream.of(1, 2, 3).reduce(Integer::max); // Optional[3]

// Form 2: With identity — always returns a value
int sum = Stream.of(1, 2, 3, 4, 5).reduce(0, Integer::sum); // 15
// Execution: 0+1=1, 1+2=3, 3+3=6, 6+4=10, 10+5=15

// Form 3: Identity + accumulator + combiner (for parallel or type change)
int totalLength = List.of("hello", "world", "java").parallelStream()
    .reduce(
        0,                                    // identity
        (len, word) -> len + word.length(),   // accumulator: int + String → int
        Integer::sum                          // combiner: int + int → int (merges parallel results)
    ); // 14
```

**When is combiner needed?**
- When accumulator input type ≠ output type (like `String → int`)
- In parallel streams to merge partial results from different threads
- Combiner is NEVER called in sequential streams

---

### Q7: What is the difference between `Collection.stream()` and `Stream.of()`?

**A:**

```java
List<String> list = List.of("a", "b", "c");

// Collection.stream() — creates stream OF the collection's elements
Stream<String> s1 = list.stream();       // Stream<String>: "a", "b", "c"

// Stream.of(values) — creates stream from explicit values
Stream<String> s2 = Stream.of("a", "b"); // Stream<String>: "a", "b"

// COMMON MISTAKE:
Stream<List<String>> s3 = Stream.of(list); // Stream of ONE element (the list itself!)
// NOT Stream<String>!
```

**Fix for the mistake:** Use `list.stream()` or `Stream.of("a", "b", "c")`.

---

### Q8: What are stateful vs stateless intermediate operations?

**A:**

| Stateless | Stateful |
|-----------|----------|
| Process each element independently | Need to see other elements or buffer data |
| `filter`, `map`, `flatMap`, `peek` | `sorted`, `distinct`, `limit`, `skip` |
| O(1) space per element | May need O(n) space (buffer all elements) |
| Safe in parallel | May cause bottlenecks in parallel |

```java
// Stateless: each element processed independently
stream.filter(x -> x > 5)    // checks one element at a time
      .map(x -> x * 2);      // transforms one element at a time

// Stateful: needs to see all elements
stream.sorted()              // must buffer ALL elements before outputting
      .distinct();           // must track ALL seen elements (HashSet internally)
```

**Interview tip:** Mention that `sorted()` on an infinite stream will cause `OutOfMemoryError`.

---

## Category 2: Coding Questions

---

### Q9: Find the second highest salary from a list of employees

```java
Optional<Double> secondHighest = employees.stream()
    .map(Employee::getSalary)
    .distinct()                          // remove duplicate salaries
    .sorted(Comparator.reverseOrder())   // highest first
    .skip(1)                             // skip the highest
    .findFirst();                        // second highest

// Alternative using reduce (without sorting):
// Not straightforward — sorting approach is cleaner
```

**Follow-up:** What if multiple employees share the highest salary?
- `distinct()` handles this — it removes duplicate salary values before sorting.

---

### Q10: Group employees by department and find the highest-paid employee in each department

```java
Map<String, Optional<Employee>> highestByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))
    ));

// To get unwrapped values (no Optional):
Map<String, Employee> highestByDept2 = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.collectingAndThen(
            Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary)),
            Optional::get
        )
    ));

// To get just the salary value:
Map<String, Double> maxSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.collectingAndThen(
            Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary)),
            opt -> opt.map(Employee::getSalary).orElse(0.0)
        )
    ));
```

---

### Q11: Find duplicate elements in a list

```java
List<Integer> numbers = List.of(1, 2, 3, 2, 4, 3, 5, 1);

// Method 1: groupingBy + filter (functional, no side effects)
List<Integer> duplicates = numbers.stream()
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
    .entrySet().stream()
    .filter(e -> e.getValue() > 1)
    .map(Map.Entry::getKey)
    .collect(Collectors.toList()); // [1, 2, 3]

// Method 2: Set-based (more efficient but uses side effect)
Set<Integer> seen = new HashSet<>();
Set<Integer> duplicates2 = numbers.stream()
    .filter(n -> !seen.add(n))   // add() returns false if already present
    .collect(Collectors.toSet()); // [1, 2, 3]

// Method 3: Using Collections.frequency (less efficient but readable)
List<Integer> duplicates3 = numbers.stream()
    .filter(n -> Collections.frequency(numbers, n) > 1)
    .distinct()
    .collect(Collectors.toList());
```

**Interview tip:** Method 1 is the "pure" stream approach (no external state). Method 2 is more efficient but violates the no-side-effects principle.

---

### Q12: Find the first non-repeated character in a string

```java
String input = "aabbcdeeff";

Character firstNonRepeated = input.chars()
    .mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
    .entrySet().stream()
    .filter(e -> e.getValue() == 1)
    .map(Map.Entry::getKey)
    .findFirst()
    .orElse(null); // 'c'
```

**Key:** Use `LinkedHashMap` to maintain insertion order — otherwise you lose the "first" guarantee.

---

### Q13: Sort a map by values using streams

```java
Map<String, Integer> unsorted = Map.of("Alice", 85, "Bob", 92, "Charlie", 78, "David", 95);

// Sort by value ascending
Map<String, Integer> sortedByValue = unsorted.entrySet().stream()
    .sorted(Map.Entry.comparingByValue())
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        Map.Entry::getValue,
        (e1, e2) -> e1,           // merge function (not needed here but required)
        LinkedHashMap::new         // maintain sorted order
    ));
// {Charlie=78, Alice=85, Bob=92, David=95}

// Sort by value descending
Map<String, Integer> sortedDesc = unsorted.entrySet().stream()
    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    .collect(Collectors.toMap(
        Map.Entry::getKey, Map.Entry::getValue,
        (e1, e2) -> e1, LinkedHashMap::new
    ));
```

---

### Q14: Flatten a list of lists and remove duplicates

```java
List<List<Integer>> listOfLists = List.of(
    List.of(1, 2, 3),
    List.of(3, 4, 5),
    List.of(5, 6, 7)
);

List<Integer> flatDistinct = listOfLists.stream()
    .flatMap(Collection::stream)
    .distinct()
    .sorted()
    .collect(Collectors.toList()); // [1, 2, 3, 4, 5, 6, 7]
```

---

### Q15: Convert a list of strings to a map with string as key and length as value

```java
List<String> words = List.of("hello", "world", "java", "stream");

Map<String, Integer> wordLengths = words.stream()
    .collect(Collectors.toMap(
        Function.identity(),  // key = word itself
        String::length        // value = length
    ));
// {hello=5, world=5, java=4, stream=6}

// Handle duplicate keys (e.g., same-length words grouped):
// If duplicate keys are possible, provide merge function:
Map<String, Integer> safe = words.stream()
    .collect(Collectors.toMap(
        Function.identity(),
        String::length,
        (existing, replacement) -> existing  // keep first on conflict
    ));
```

---

### Q16: Find the most frequent element in a list

```java
List<Integer> numbers = List.of(1, 2, 3, 2, 4, 2, 5, 3, 2);

Integer mostFrequent = numbers.stream()
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
    .entrySet().stream()
    .max(Map.Entry.comparingByValue())
    .map(Map.Entry::getKey)
    .orElse(null); // 2 (appears 4 times)
```

---

### Q17: Partition employees into those above and below a salary threshold

```java
Map<Boolean, List<Employee>> partition = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.getSalary() > 50000));

List<Employee> highEarners = partition.get(true);
List<Employee> others = partition.get(false);

// partitioningBy vs groupingBy:
// partitioningBy → always 2 groups (true/false), even if one is empty
// groupingBy → any number of groups based on classifier
```

---

### Q18: Join strings with a delimiter, prefix, and suffix

```java
List<String> names = List.of("Alice", "Bob", "Charlie");

String joined = names.stream()
    .collect(Collectors.joining(", ", "[", "]"));
// [Alice, Bob, Charlie]

// Without prefix/suffix:
String csv = names.stream().collect(Collectors.joining(", "));
// Alice, Bob, Charlie

// With filtering:
String seniorNames = employees.stream()
    .filter(e -> e.getAge() > 30)
    .map(Employee::getName)
    .collect(Collectors.joining(" | ", "Seniors: ", ""));
// Seniors: Alice | Charlie | David
```

---

### Q19: Find the average salary by department

```java
Map<String, Double> avgByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.averagingDouble(Employee::getSalary)
    ));

// With full statistics:
Map<String, DoubleSummaryStatistics> statsByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.summarizingDouble(Employee::getSalary)
    ));
// statsByDept.get("IT").getAverage()
// statsByDept.get("IT").getMax()
// statsByDept.get("IT").getCount()
```

---

### Q20: Count the occurrences of each character in a string

```java
String input = "programming";

Map<Character, Long> charCount = input.chars()
    .mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
// {p=1, r=2, o=1, g=2, a=1, m=2, i=1, n=1}

// Sorted by frequency descending:
Map<Character, Long> sortedByFreq = charCount.entrySet().stream()
    .sorted(Map.Entry.<Character, Long>comparingByValue().reversed())
    .collect(Collectors.toMap(
        Map.Entry::getKey, Map.Entry::getValue,
        (e1, e2) -> e1, LinkedHashMap::new
    ));
```

---

## Category 3: Advanced / Tricky Questions

---

### Q21: What happens if you reuse a stream?

**A:** You get `IllegalStateException: stream has already been operated upon or closed`.

```java
Stream<String> stream = names.stream().filter(s -> s.length() > 3);

// First terminal operation — works fine
List<String> list1 = stream.collect(Collectors.toList());

// Second terminal operation — EXCEPTION!
List<String> list2 = stream.collect(Collectors.toList()); // IllegalStateException

// Fix: Create a new stream each time
Supplier<Stream<String>> streamSupplier = () -> names.stream().filter(s -> s.length() > 3);
List<String> result1 = streamSupplier.get().collect(Collectors.toList());
List<String> result2 = streamSupplier.get().collect(Collectors.toList()); // works!
```

---

### Q22: How does parallel stream work? When should you use it?

**A:** Parallel streams use the **ForkJoinPool.commonPool()** to split work across threads.

**Internal process:**
1. `Spliterator.trySplit()` divides the source into chunks
2. Each chunk is processed by a different thread from the common pool
3. Results are combined using the combiner function

```java
// Simple parallel usage
long count = numbers.parallelStream()
    .filter(n -> n > 5)
    .count();

// Custom thread pool (to avoid blocking common pool)
ForkJoinPool customPool = new ForkJoinPool(4);
List<String> result = customPool.submit(() ->
    list.parallelStream()
        .filter(s -> expensiveCheck(s))
        .collect(Collectors.toList())
).get();
```

**When to use parallel streams:**

| Use Parallel | Avoid Parallel |
|---|---|
| Large dataset (>10,000 elements) | Small dataset (<1000 elements) |
| CPU-intensive operations | I/O-bound operations |
| No shared mutable state | Shared mutable state |
| Order doesn't matter | Strict ordering needed |
| ArrayList/array source (good spliterator) | LinkedList source (poor spliterator) |

**Pitfalls:**
- Common pool shared across entire JVM — long tasks block other parallel streams
- Ordering overhead with `findFirst()` (use `findAny()` instead)
- Thread-safety issues if lambdas access shared state

---

### Q23: What is the difference between `peek()` and `forEach()`?

**A:**

| | `peek()` | `forEach()` |
|---|---|---|
| Type | Intermediate (lazy) | Terminal |
| Returns | Stream (chainable) | void |
| Execution | Only when terminal op triggers | Immediately executes |
| Purpose | Debugging / logging | Side effects (final action) |

```java
// peek — for debugging (NOT guaranteed to execute if short-circuited)
List<String> result = names.stream()
    .filter(s -> s.length() > 3)
    .peek(s -> System.out.println("After filter: " + s)) // may not execute for all
    .map(String::toUpperCase)
    .peek(s -> System.out.println("After map: " + s))
    .collect(Collectors.toList());

// forEach — terminal action
names.stream()
    .filter(s -> s.length() > 3)
    .forEach(System.out::println); // always executes
```

**Warning:** Never use `peek()` for business logic — it may not execute if the pipeline short-circuits.

---

### Q24: How do you handle checked exceptions in streams?

**A:** Streams don't support checked exceptions in lambdas. Solutions:

```java
// Problem: This won't compile
list.stream()
    .map(s -> throwsCheckedException(s)) // compile error!
    .collect(Collectors.toList());

// Solution 1: Wrap in unchecked exception
list.stream()
    .map(s -> {
        try {
            return throwsCheckedException(s);
        } catch (CheckedException e) {
            throw new RuntimeException(e);
        }
    })
    .collect(Collectors.toList());

// Solution 2: Utility wrapper method
@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}

static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R> f) {
    return t -> {
        try { return f.apply(t); }
        catch (Exception e) { throw new RuntimeException(e); }
    };
}

// Usage:
list.stream()
    .map(unchecked(s -> throwsCheckedException(s)))
    .collect(Collectors.toList());

// Solution 3: Filter out failures using Optional
list.stream()
    .map(s -> {
        try { return Optional.of(process(s)); }
        catch (Exception e) { return Optional.<String>empty(); }
    })
    .filter(Optional::isPresent)
    .map(Optional::get)
    .collect(Collectors.toList());
```

---

### Q25: What is `Collectors.collectingAndThen()`? Give a use case.

**A:** It applies a finishing transformation AFTER collecting.

```java
// Use case 1: Create unmodifiable list
List<String> unmodifiable = names.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList
    ));

// Use case 2: Get single result after collection
String oldest = employees.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.maxBy(Comparator.comparingInt(Employee::getAge)),
        opt -> opt.map(Employee::getName).orElse("N/A")
    ));

// Use case 3: Convert to array after collecting
String[] nameArray = names.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        list -> list.toArray(new String[0])
    ));
```

---

### Q26: What is the difference between `toMap()` with and without merge function?

**A:**

```java
// WITHOUT merge function — throws IllegalStateException on duplicate keys!
List<Employee> emps = List.of(
    new Employee("Alice", "IT"), 
    new Employee("Bob", "IT")   // same department!
);

// This CRASHES if two employees have the same key:
Map<String, Employee> map = emps.stream()
    .collect(Collectors.toMap(Employee::getDepartment, Function.identity()));
// IllegalStateException: Duplicate key IT

// WITH merge function — handles duplicates:
Map<String, Employee> safe = emps.stream()
    .collect(Collectors.toMap(
        Employee::getDepartment,
        Function.identity(),
        (existing, replacement) -> existing  // keep first occurrence
    ));

// WITH merge function + specific map type:
Map<String, Employee> sorted = emps.stream()
    .collect(Collectors.toMap(
        Employee::getDepartment,
        Function.identity(),
        (e1, e2) -> e1,
        TreeMap::new              // sorted map
    ));
```

**Rule:** Always provide a merge function if duplicate keys are possible.

---

### Q27: How do you create an infinite stream? How do you make it finite?

**A:**

```java
// Infinite streams
Stream<Integer> iterate = Stream.iterate(0, n -> n + 2); // 0, 2, 4, 6, ...
Stream<Double> generate = Stream.generate(Math::random);  // random, random, ...

// Make them finite with limit()
List<Integer> first10Evens = Stream.iterate(0, n -> n + 2)
    .limit(10)
    .collect(Collectors.toList()); // [0, 2, 4, 6, 8, 10, 12, 14, 16, 18]

// Java 9: iterate with predicate (built-in termination)
List<Integer> evensBelow100 = Stream.iterate(0, n -> n < 100, n -> n + 2)
    .collect(Collectors.toList()); // [0, 2, 4, ..., 98]

// takeWhile (Java 9) — stops when predicate becomes false
List<Integer> taken = Stream.iterate(1, n -> n * 2)
    .takeWhile(n -> n < 1000)
    .collect(Collectors.toList()); // [1, 2, 4, 8, 16, 32, 64, 128, 256, 512]
```

**Warning:** `sorted()` or `collect()` on an infinite stream without `limit()` → `OutOfMemoryError`.

---

### Q28: What is the difference between `Stream.of()` and `IntStream` / `LongStream` / `DoubleStream`?

**A:**

```java
// Stream.of with primitives — AUTOBOXING (performance overhead)
Stream<Integer> boxed = Stream.of(1, 2, 3, 4, 5); // Stream<Integer> — boxed objects

// Primitive streams — NO boxing (efficient)
IntStream intStream = IntStream.of(1, 2, 3, 4, 5);      // primitive int
LongStream longStream = LongStream.rangeClosed(1, 100);  // 1 to 100
DoubleStream doubleStream = DoubleStream.generate(Math::random);

// Benefits of primitive streams:
int sum = IntStream.rangeClosed(1, 100).sum();            // direct sum method
OptionalInt max = IntStream.of(5, 3, 8, 1).max();        // no Comparator needed
double avg = IntStream.of(5, 3, 8, 1).average().orElse(0);

// Converting between boxed and primitive:
IntStream primitive = Stream.of(1, 2, 3).mapToInt(Integer::intValue);
Stream<Integer> boxedAgain = IntStream.of(1, 2, 3).boxed();

// Range methods (only on primitive streams):
IntStream.range(0, 5);        // 0, 1, 2, 3, 4 (exclusive end)
IntStream.rangeClosed(0, 5);  // 0, 1, 2, 3, 4, 5 (inclusive end)
```

---

### Q29: Explain `Collectors.groupingBy()` with downstream collectors

**A:**

```java
List<Employee> employees = getEmployees();

// Simple grouping — List per group
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDepartment));

// Count per group
Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.counting()
    ));

// Average salary per group
Map<String, Double> avgSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.averagingDouble(Employee::getSalary)
    ));

// Max salary employee per group
Map<String, Optional<Employee>> richestByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))
    ));

// Collect names as Set per group (mapping downstream)
Map<String, Set<String>> namesByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.mapping(Employee::getName, Collectors.toSet())
    ));

// Multi-level grouping (group by dept, then by city)
Map<String, Map<String, List<Employee>>> byDeptAndCity = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.groupingBy(Employee::getCity)
    ));

// Summarizing statistics per group
Map<String, DoubleSummaryStatistics> statsByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.summarizingDouble(Employee::getSalary)
    ));
```

---

### Q30: Write a custom collector

**A:**

```java
// Custom collector to join strings with comma (simplified Collectors.joining)
Collector<String, StringJoiner, String> commaJoiner = Collector.of(
    () -> new StringJoiner(", "),           // supplier
    StringJoiner::add,                       // accumulator
    StringJoiner::merge,                     // combiner (for parallel)
    StringJoiner::toString                   // finisher
);

String result = Stream.of("a", "b", "c").collect(commaJoiner); // "a, b, c"

// Custom collector: collect to ImmutableList
Collector<Object, List<Object>, List<Object>> toImmutableList = Collector.of(
    ArrayList::new,                          // supplier
    List::add,                               // accumulator
    (left, right) -> { left.addAll(right); return left; }, // combiner
    Collections::unmodifiableList            // finisher
);
```

**Collector components:**
1. **Supplier** — creates the mutable container
2. **Accumulator** — adds element to container
3. **Combiner** — merges two containers (parallel streams)
4. **Finisher** — transforms container to final result

---

## Category 4: Scenario-Based Questions

---

### Q31: Given a list of transactions, find the top 3 most expensive transactions in 2024

```java
List<Transaction> top3 = transactions.stream()
    .filter(t -> t.getYear() == 2024)
    .sorted(Comparator.comparingDouble(Transaction::getAmount).reversed())
    .limit(3)
    .collect(Collectors.toList());
```

---

### Q32: Convert a list of employees to a map where key is department and value is comma-separated names

```java
Map<String, String> deptNames = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.mapping(
            Employee::getName,
            Collectors.joining(", ")
        )
    ));
// {IT=Alice, Charlie, HR=Bob, Diana}
```

---

### Q33: Find all pairs of numbers in a list that sum to a target

```java
List<int[]> pairs = IntStream.range(0, nums.size())
    .boxed()
    .flatMap(i -> IntStream.range(i + 1, nums.size())
        .filter(j -> nums.get(i) + nums.get(j) == target)
        .mapToObj(j -> new int[]{nums.get(i), nums.get(j)})
    )
    .collect(Collectors.toList());
```

**Note:** This is O(n²). For O(n), use a HashMap (complement lookup) — not everything should be solved with streams.

---

### Q34: Process a CSV file — read lines, parse, filter, and aggregate

```java
// Read file, skip header, parse employees, get average salary by department
Map<String, Double> avgSalaryByDept;
try (Stream<String> lines = Files.lines(Path.of("employees.csv"))) {
    avgSalaryByDept = lines
        .skip(1) // skip header
        .map(line -> line.split(","))
        .map(parts -> new Employee(parts[0], parts[1], Double.parseDouble(parts[2])))
        .collect(Collectors.groupingBy(
            Employee::getDepartment,
            Collectors.averagingDouble(Employee::getSalary)
        ));
}
// Important: use try-with-resources for Files.lines() to close the file handle
```

---

### Q35: Implement pagination using streams

```java
public <T> List<T> getPage(List<T> items, int pageNumber, int pageSize) {
    return items.stream()
        .skip((long) pageNumber * pageSize)
        .limit(pageSize)
        .collect(Collectors.toList());
}

// Usage: getPage(employees, 2, 10) → items 20-29
```

---

## Category 5: Java 9+ Stream Enhancements

---

### Q36: What stream methods were added in Java 9 and later?

**A:**

```java
// Java 9: takeWhile — takes elements while predicate is true
Stream.of(1, 2, 3, 4, 5, 1, 2).takeWhile(n -> n < 4)
    .collect(Collectors.toList()); // [1, 2, 3] — stops at 4

// Java 9: dropWhile — drops elements while predicate is true
Stream.of(1, 2, 3, 4, 5, 1, 2).dropWhile(n -> n < 4)
    .collect(Collectors.toList()); // [4, 5, 1, 2] — includes elements after first false

// Java 9: iterate with predicate
Stream.iterate(1, n -> n < 100, n -> n * 2)
    .collect(Collectors.toList()); // [1, 2, 4, 8, 16, 32, 64]

// Java 9: ofNullable
Stream.ofNullable(null).count();    // 0 (empty stream)
Stream.ofNullable("hello").count(); // 1

// Java 10: Collectors.toUnmodifiableList/Set/Map
List<String> immutable = names.stream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toUnmodifiableList());

// Java 16: Stream.toList() — shorthand for collect(Collectors.toList())
List<String> result = names.stream()
    .filter(s -> s.length() > 3)
    .toList(); // returns unmodifiable list

// Java 16: mapMulti (alternative to flatMap for small number of elements)
Stream.of(1, 2, 3, 4)
    .<Integer>mapMulti((num, consumer) -> {
        consumer.accept(num);
        consumer.accept(num * 10);
    })
    .toList(); // [1, 10, 2, 20, 3, 30, 4, 40]
```

---

## Category 6: Common Mistakes & Pitfalls

---

### Q37: What are the most common mistakes with streams?

| # | Mistake | Problem | Fix |
|---|---------|---------|-----|
| 1 | Reusing a stream | `IllegalStateException` | Create new stream or use `Supplier<Stream>` |
| 2 | Side effects in `map`/`filter` | Unpredictable in parallel | Use `forEach` for side effects |
| 3 | Using `peek()` for logic | Not guaranteed to execute | Use only for debugging |
| 4 | `toMap` without merge function | `IllegalStateException` on duplicate keys | Always provide merge function |
| 5 | Parallel with small data | Slower than sequential (overhead) | Use parallel only for large datasets |
| 6 | Modifying source during stream | `ConcurrentModificationException` | Don't modify source collection |
| 7 | `stream().count()` on List | Traverses entire pipeline | Use `list.size()` directly |
| 8 | `sorted()` on infinite stream | `OutOfMemoryError` | Use `limit()` before `sorted()` |
| 9 | Ignoring return of `map` | Common in `void` method calls | Use `forEach` instead |
| 10 | `forEach` + external accumulation | Not thread-safe in parallel | Use `collect` or `reduce` |

```java
// Mistake 10 example:
List<String> result = new ArrayList<>();
names.parallelStream()
    .filter(s -> s.length() > 3)
    .forEach(result::add);  // NOT thread-safe! Race condition!

// Fix: use collect
List<String> safeResult = names.parallelStream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toList()); // thread-safe
```

---

## Quick Reference — Most Used Stream Patterns

```java
// Filter + Collect
list.stream().filter(predicate).collect(Collectors.toList());

// Transform
list.stream().map(transformFunc).collect(Collectors.toList());

// Flatten nested
listOfLists.stream().flatMap(Collection::stream).collect(Collectors.toList());

// Group by
list.stream().collect(Collectors.groupingBy(classifier));

// Count by group
list.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));

// Frequency map
list.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

// Join strings
list.stream().map(Object::toString).collect(Collectors.joining(", "));

// Find max/min
list.stream().max(Comparator.comparingInt(func));

// Sum / Average
list.stream().mapToInt(func).sum();
list.stream().mapToDouble(func).average().orElse(0);

// To Map
list.stream().collect(Collectors.toMap(keyFunc, valueFunc, mergeFunc));

// Partition
list.stream().collect(Collectors.partitioningBy(predicate));

// Distinct sorted
list.stream().distinct().sorted().collect(Collectors.toList());

// First match
list.stream().filter(predicate).findFirst().orElse(default);

// Any/All/None match
list.stream().anyMatch(predicate);
list.stream().allMatch(predicate);

// Reduce
list.stream().reduce(identity, accumulator);
```

---

## Category 7: Deep Internals & Advanced Topics

---

### Q38: What is a Spliterator? How does it relate to streams?

**A:** A `Spliterator` (Splittable Iterator) is the backbone of parallel streams. It defines how a data source can be split and traversed.

**Key methods:**
- `tryAdvance(Consumer)` — processes one element and advances
- `trySplit()` — splits the source into two halves for parallel processing
- `estimateSize()` — estimated remaining elements
- `characteristics()` — flags like SIZED, ORDERED, SORTED, DISTINCT

```java
// Every collection has a spliterator:
Spliterator<String> spliterator = list.spliterator();

// How parallel stream uses spliterator:
// 1. ForkJoinPool calls trySplit() to divide work
// 2. Each half is processed by different thread
// 3. Results are combined

// Spliterator characteristics affect stream behavior:
// ORDERED → findFirst() must respect order (slower in parallel)
// SIZED → count() can be O(1) instead of traversing
// SORTED → sorted() becomes a no-op
// DISTINCT → distinct() becomes a no-op

// Why ArrayList parallel is faster than LinkedList:
// ArrayList spliterator → splits evenly at midpoint (O(1) split)
// LinkedList spliterator → must traverse to find midpoint (O(n) split)
```

**Custom Spliterator example:**

```java
// Custom spliterator for a sentence → words
class WordSpliterator implements Spliterator<String> {
    private final String text;
    private int currentPos = 0;

    public WordSpliterator(String text) { this.text = text; }

    @Override
    public boolean tryAdvance(Consumer<? super String> action) {
        if (currentPos >= text.length()) return false;
        int start = currentPos;
        while (currentPos < text.length() && text.charAt(currentPos) != ' ') currentPos++;
        action.accept(text.substring(start, currentPos));
        currentPos++; // skip space
        return true;
    }

    @Override
    public Spliterator<String> trySplit() {
        int mid = (currentPos + text.length()) / 2;
        // Find word boundary near midpoint
        while (mid < text.length() && text.charAt(mid) != ' ') mid++;
        if (mid >= text.length()) return null;
        WordSpliterator other = new WordSpliterator(text.substring(currentPos, mid));
        currentPos = mid + 1;
        return other;
    }

    @Override
    public long estimateSize() { return text.length() - currentPos; }

    @Override
    public int characteristics() { return ORDERED | NONNULL | IMMUTABLE; }
}

// Usage:
Stream<String> wordStream = StreamSupport.stream(new WordSpliterator(sentence), true);
```

---

### Q39: What is the difference between `forEach()` and `forEachOrdered()` in parallel streams?

**A:**

| | `forEach()` | `forEachOrdered()` |
|---|---|---|
| Order guarantee | NO — elements processed in arbitrary order | YES — respects encounter order |
| Parallel performance | Faster (no ordering constraint) | Slower (threads must coordinate) |
| Sequential stream | Same as forEachOrdered | Same as forEach |

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8);

// forEach — no order guarantee in parallel
numbers.parallelStream().forEach(System.out::print);
// Output might be: 65873214 (non-deterministic)

// forEachOrdered — maintains encounter order even in parallel
numbers.parallelStream().forEachOrdered(System.out::print);
// Output always: 12345678

// When to use forEachOrdered:
// - Writing to a file where order matters
// - Printing results that must be in sequence
// - Building an ordered output from parallel processing
```

**Interview tip:** If order doesn't matter, always prefer `forEach()` in parallel for better performance.

---

### Q40: How does `Stream.concat()` differ from `flatMap()` for merging streams?

**A:**

```java
Stream<String> s1 = Stream.of("a", "b");
Stream<String> s2 = Stream.of("c", "d");
Stream<String> s3 = Stream.of("e", "f");

// Stream.concat — merges exactly TWO streams
Stream<String> merged = Stream.concat(s1, s2); // [a, b, c, d]

// Concat multiple — nested (NOT recommended for many streams)
Stream<String> nested = Stream.concat(Stream.concat(s1, s2), s3);
// Creates deep call stack — O(n) depth for n streams!

// flatMap — merges ANY number of streams (preferred for 3+)
Stream<String> allMerged = Stream.of(s1, s2, s3).flatMap(Function.identity());
// [a, b, c, d, e, f] — flat structure, no deep nesting

// Performance difference:
// concat of n streams → creates nested pipeline of depth n (memory overhead)
// flatMap → flat structure regardless of count
```

| | `Stream.concat()` | `flatMap()` |
|---|---|---|
| Streams merged | Exactly 2 | Any number |
| Nesting depth | Grows with each concat | Always flat |
| Laziness | Fully lazy | Fully lazy |
| Use case | Merging 2 streams | Merging collection of streams |

---

### Q41: Explain `Collectors.teeing()` (Java 12)

**A:** `teeing()` applies TWO collectors simultaneously and merges their results.

```java
// Problem: Find both min and max in one pass
// Without teeing — need 2 separate stream operations:
Optional<Integer> min = numbers.stream().min(Integer::compareTo);
Optional<Integer> max = numbers.stream().max(Integer::compareTo);

// With teeing — single pass, two results:
var result = numbers.stream().collect(Collectors.teeing(
    Collectors.minBy(Integer::compareTo),   // first collector
    Collectors.maxBy(Integer::compareTo),   // second collector
    (minOpt, maxOpt) -> Map.of(            // merger function
        "min", minOpt.orElse(0),
        "max", maxOpt.orElse(0)
    )
));
// {min=1, max=10}

// Use case: Average and count in one pass
var stats = employees.stream().collect(Collectors.teeing(
    Collectors.averagingDouble(Employee::getSalary),
    Collectors.counting(),
    (avg, count) -> "Average: " + avg + ", Count: " + count
));

// Use case: Partition into two lists with custom merge
var partitioned = numbers.stream().collect(Collectors.teeing(
    Collectors.filtering(n -> n % 2 == 0, Collectors.toList()),
    Collectors.filtering(n -> n % 2 != 0, Collectors.toList()),
    (evens, odds) -> Map.of("evens", evens, "odds", odds)
));
```

---

### Q42: What are `Collectors.filtering()` and `Collectors.flatMapping()` (Java 9)?

**A:** These are downstream collectors — used INSIDE `groupingBy()` or `teeing()`.

```java
// Collectors.filtering — filter WITHIN a group (not before grouping)
Map<String, List<Employee>> highEarnersByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.filtering(
            e -> e.getSalary() > 80000,
            Collectors.toList()
        )
    ));
// Key difference vs filter().collect(groupingBy):
// filtering() keeps ALL department keys (even if empty list after filter)
// filter() before groupingBy drops departments with no high earners

// Without filtering — "Marketing" department disappears if no one earns > 80k
Map<String, List<Employee>> missing = employees.stream()
    .filter(e -> e.getSalary() > 80000)  // Marketing people filtered out
    .collect(Collectors.groupingBy(Employee::getDepartment));
// {IT=[Alice], HR=[Bob]}  ← Marketing is MISSING

// With filtering — Marketing still shows up with empty list
// {IT=[Alice], HR=[Bob], Marketing=[]}  ← Marketing is PRESENT

// Collectors.flatMapping — flatMap within a group
Map<String, Set<String>> skillsByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.flatMapping(
            e -> e.getSkills().stream(),  // each employee → stream of skills
            Collectors.toSet()
        )
    ));
// {IT=[Java, Python, Docker], HR=[Excel, SAP]}
```

---

### Q43: How does `Optional.stream()` (Java 9) work with streams?

**A:** `Optional.stream()` returns a stream of 0 or 1 element — perfect for flattening optionals in a stream.

```java
// Problem: List of IDs, some map to an employee (Optional), some don't
List<Long> ids = List.of(1L, 2L, 3L, 99L, 100L);

// Old way (Java 8) — filter + map
List<Employee> employees = ids.stream()
    .map(id -> findById(id))       // Stream<Optional<Employee>>
    .filter(Optional::isPresent)
    .map(Optional::get)
    .collect(Collectors.toList());

// New way (Java 9) — flatMap + Optional.stream()
List<Employee> employees2 = ids.stream()
    .map(id -> findById(id))       // Stream<Optional<Employee>>
    .flatMap(Optional::stream)     // empty optionals produce 0 elements, present produce 1
    .collect(Collectors.toList());

// Optional.stream() returns:
// Optional.empty() → Stream.empty() (0 elements)
// Optional.of(x)  → Stream.of(x)   (1 element)
```

---

### Q44: Stream vs for-loop — when is a for-loop better?

**A:**

| Scenario | Winner | Why |
|----------|--------|-----|
| Simple iteration, index needed | for-loop | `stream` has no index access |
| Early termination with complex logic | for-loop | Multiple conditions, hard to express in stream |
| Checked exceptions | for-loop | Streams don't support checked exceptions natively |
| Mutation-heavy accumulation | for-loop | Streams discourage side effects |
| Very small collections (<10) | for-loop | Stream overhead not worth it |
| Large data + transformations | Stream | Cleaner, parallelizable |
| Pipeline of filter/map/reduce | Stream | Expressive, readable |
| Parallel processing | Stream | Built-in parallelism |

```java
// for-loop wins: index-based, early break with state
String result = null;
for (int i = 0; i < list.size(); i++) {
    if (list.get(i).startsWith("X")) {
        result = list.get(i) + " at index " + i;
        break;
    }
}

// Stream wins: chain of transformations
Map<String, Long> frequency = words.stream()
    .filter(w -> w.length() > 3)
    .map(String::toLowerCase)
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
```

**Performance comparison (approximate):**

```
Operation on 1,000,000 integers:
  for-loop sum:         ~2ms
  stream sum:           ~5ms (2.5x slower due to pipeline overhead)
  parallelStream sum:   ~1.5ms (faster on multi-core)
  
Conclusion: Stream overhead is negligible for most real applications.
The bottleneck is almost always I/O, database, or network — not stream vs loop.
```

---

### Q45: Explain the internal architecture of a stream pipeline

**A:**

```
┌──────────────────────────────────────────────────────────────────────┐
│                    STREAM PIPELINE INTERNALS                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Source           Intermediate Stages         Terminal                │
│  ──────           ────────────────────        ────────               │
│  (Spliterator)    (AbstractPipeline)          (TerminalOp)           │
│                                                                      │
│  ArrayList ──→ Head ──→ filter ──→ map ──→ collect                   │
│  (source)    (ReferencePipeline.Head)      (ReduceOps)               │
│                                                                      │
│  When terminal op is invoked:                                        │
│  1. Pipeline builds a Sink chain (backward from terminal)            │
│  2. Each stage wraps the downstream Sink                             │
│  3. Source pushes elements through the chain                         │
│                                                                      │
│  Sink chain for: stream().filter(p).map(f).collect(toList())         │
│                                                                      │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐            │
│  │ filter Sink │ ──→ │  map Sink   │ ──→ │collect Sink │            │
│  │ if p(x):   │     │ pass f(x)   │     │ list.add(x) │            │
│  │  pass to   │     │ to next     │     │             │            │
│  │  next sink │     │             │     │             │            │
│  └─────────────┘     └─────────────┘     └─────────────┘            │
│        ↑                                                             │
│  source.spliterator().forEachRemaining(filterSink::accept)           │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Key insight:** Elements are processed vertically (one element through all stages) NOT horizontally (all elements through one stage). This enables short-circuiting.

```java
// What actually happens with: stream.filter(x > 5).map(x * 2).findFirst()
// Element 1: filter(1 > 5) → false → skip
// Element 2: filter(2 > 5) → false → skip
// ...
// Element 6: filter(6 > 5) → true → map(6 * 2) → 12 → findFirst captures → STOP
// Elements 7, 8, 9... are NEVER touched
```

---

### Q46: What is `Stream.concat()` vs `Stream.Builder`?

**A:**

```java
// Stream.Builder — build a stream element by element
Stream<String> built = Stream.<String>builder()
    .add("first")
    .add("second")
    .add("third")
    .build(); // Can only call build() once!

// Use case: conditional stream construction
Stream.Builder<String> builder = Stream.builder();
builder.add("always");
if (condition) builder.add("sometimes");
if (otherCondition) builder.add("rarely");
Stream<String> dynamic = builder.build();

// Stream.concat — merge two existing streams
Stream<String> merged = Stream.concat(
    getActiveUsers().stream(),
    getInactiveUsers().stream()
);
```

---

### Q47: How do you debug a stream pipeline?

**A:** Multiple techniques:

```java
// Technique 1: peek() for logging (temporary, remove after debugging)
List<String> result = names.stream()
    .filter(s -> s.length() > 3)
    .peek(s -> System.out.println("After filter: " + s))
    .map(String::toUpperCase)
    .peek(s -> System.out.println("After map: " + s))
    .collect(Collectors.toList());

// Technique 2: Break into variables for breakpoints
Stream<String> filtered = names.stream().filter(s -> s.length() > 3);
Stream<String> mapped = filtered.map(String::toUpperCase); // set breakpoint here
List<String> result2 = mapped.collect(Collectors.toList());

// Technique 3: IDE Stream Debugger (IntelliJ)
// - Set breakpoint on terminal operation
// - Click "Trace Current Stream Chain" in debugger
// - Shows element flow through each stage visually

// Technique 4: Collect intermediate results
List<String> afterFilter = names.stream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toList());
System.out.println("After filter: " + afterFilter); // inspect
List<String> afterMap = afterFilter.stream()
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

---

### Q48: What is `Collectors.toUnmodifiableList()` vs `stream().toList()`?

**A:**

```java
// Collectors.toUnmodifiableList() — Java 10
List<String> unmod1 = names.stream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toUnmodifiableList());
// Throws NullPointerException if any element is null!

// stream().toList() — Java 16 (shorthand)
List<String> unmod2 = names.stream()
    .filter(s -> s.length() > 3)
    .toList();
// Also unmodifiable, but ALLOWS null elements

// Collectors.toList() — Java 8 (mutable)
List<String> mutable = names.stream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toList());
// Returns ArrayList — mutable, allows null
```

| Method | Java | Mutable? | Allows null? | Type |
|--------|------|----------|--------------|------|
| `Collectors.toList()` | 8 | Yes | Yes | ArrayList |
| `Collectors.toUnmodifiableList()` | 10 | No | No (throws NPE) | Unmodifiable |
| `stream().toList()` | 16 | No | Yes | Unmodifiable |

---

## Related Topics

- [11. Stream API — Complete Guide](./11-stream-api.md) — theory, internals, code examples
- [10. Java 8 Features](./10-java8-features.md) — lambda expressions, functional interfaces
- [12. Optional](./12-optional.md) — handling stream terminal operations
- [9. Comparable vs Comparator](./09-comparable-vs-comparator.md) — sorting in streams
