# 11. Stream API — Complete Guide

---

## Theory

The Stream API (Java 8+) provides a **declarative, functional** approach to processing collections. A Stream is a sequence of elements that supports sequential and parallel aggregate operations.

**Key characteristics:**
- **Not a data structure** — doesn't store elements; computes on demand
- **Lazy evaluation** — intermediate operations are not executed until a terminal operation is invoked
- **Single-use** — a stream cannot be reused after a terminal operation
- **Optionally parallel** — can leverage multi-core processors with `.parallelStream()`

### Stream Pipeline

```
Source → Intermediate Operations → Terminal Operation → Result

Collection.stream()
    .filter(...)       ← intermediate (lazy)
    .map(...)          ← intermediate (lazy)
    .sorted(...)       ← intermediate (lazy)
    .collect(...)      ← terminal (triggers execution)
```

---

## Internal Working

### Lazy Evaluation

```java
List<String> names = List.of("Alice", "Bob", "Charlie", "David");

names.stream()
    .filter(s -> {
        System.out.println("filter: " + s);
        return s.length() > 3;
    })
    .map(s -> {
        System.out.println("map: " + s);
        return s.toUpperCase();
    })
    .findFirst();

// Output (processes one element at a time until match):
// filter: Alice
// map: Alice        ← found! stops here
// Result: Optional[ALICE]
```

### Stream Implementation Architecture

```
AbstractPipeline
├── Head (source stage) → spliterator from collection
├── StatelessOp (filter, map, flatMap, peek) → per-element processing
└── StatefulOp (sorted, distinct, limit, skip) → may buffer all elements

Terminal Operation triggers:
1. Creates Sink chain (linked callbacks)
2. Pushes elements through the chain
3. Collects result
```

---

## Diagram

```
Stream Operations Classification:

┌───────────────────────────────────────────────────────────┐
│              INTERMEDIATE (return Stream — lazy)           │
├─────────────────────┬─────────────────────────────────────┤
│   STATELESS         │      STATEFUL                       │
├─────────────────────┼─────────────────────────────────────┤
│   filter()          │      sorted()                       │
│   map()             │      distinct()                     │
│   flatMap()         │      limit()                        │
│   peek()            │      skip()                         │
│   mapToInt/Long/Dbl │                                     │
└─────────────────────┴─────────────────────────────────────┘

┌───────────────────────────────────────────────────────────┐
│              TERMINAL (trigger execution)                  │
├─────────────────────┬─────────────────────────────────────┤
│   NON-SHORT-CIRCUIT │   SHORT-CIRCUIT                     │
├─────────────────────┼─────────────────────────────────────┤
│   forEach()         │   findFirst()                       │
│   collect()         │   findAny()                         │
│   reduce()          │   anyMatch()                        │
│   count()           │   allMatch()                        │
│   min() / max()     │   noneMatch()                       │
│   toArray()         │                                     │
└─────────────────────┴─────────────────────────────────────┘
```

---

## Code Examples

### Creating Streams

```java
// From Collection
Stream<String> s1 = list.stream();
Stream<String> s2 = list.parallelStream();

// From values
Stream<String> s3 = Stream.of("x", "y", "z");

// Infinite streams
Stream<Integer> evens = Stream.iterate(0, n -> n + 2);      // 0,2,4,6...
Stream<Double> randoms = Stream.generate(Math::random);
Stream<Integer> bounded = Stream.iterate(0, n -> n < 100, n -> n + 2); // Java 9

// From array
IntStream intStream = Arrays.stream(new int[]{1, 2, 3});

// From file
Stream<String> lines = Files.lines(Path.of("file.txt"));
```

### Filter, Map, FlatMap

```java
// filter — keeps elements matching predicate
List<Employee> seniors = employees.stream()
    .filter(e -> e.getAge() > 30)
    .filter(e -> e.getSalary() > 50000)
    .collect(Collectors.toList());

// map — transforms each element
List<String> names = employees.stream()
    .map(Employee::getName)
    .map(String::toUpperCase)
    .collect(Collectors.toList());

// flatMap — flattens nested structures
List<List<String>> nested = List.of(List.of("a","b"), List.of("c","d"));
List<String> flat = nested.stream()
    .flatMap(Collection::stream)
    .collect(Collectors.toList());  // [a, b, c, d]
```

### Sorted, Distinct, Limit, Skip

```java
// sorted with comparator
List<Employee> bySalary = employees.stream()
    .sorted(Comparator.comparingDouble(Employee::getSalary).reversed())
    .collect(Collectors.toList());

// distinct (uses equals/hashCode)
List<Integer> unique = Stream.of(1, 2, 2, 3, 3)
    .distinct().collect(Collectors.toList());  // [1, 2, 3]

// pagination: skip + limit
List<Employee> page = employees.stream()
    .sorted(Comparator.comparing(Employee::getName))
    .skip(10).limit(5)
    .collect(Collectors.toList());
```

### Reduce

```java
// Sum
int sum = Stream.of(1, 2, 3, 4, 5)
    .reduce(0, Integer::sum);  // 15

// Max without comparator
Optional<Integer> max = Stream.of(1, 2, 3, 4, 5)
    .reduce(Integer::max);  // Optional[5]

// String concatenation
String joined = Stream.of("a", "b", "c")
    .reduce("", (a, b) -> a + b);  // "abc"

// Complex reduce: total salary
double totalSalary = employees.stream()
    .map(Employee::getSalary)
    .reduce(0.0, Double::sum);

// Three-argument reduce (for parallel streams)
int totalLength = words.parallelStream()
    .reduce(0,                            // identity
            (sum2, word) -> sum2 + word.length(),  // accumulator
            Integer::sum);                 // combiner (merges partial results)
```

### Collectors — The Power Tool

```java
// toList, toSet, toMap
List<String> list = stream.collect(Collectors.toList());
Set<String> set = stream.collect(Collectors.toSet());
Map<Long, Employee> map = employees.stream()
    .collect(Collectors.toMap(Employee::getId, Function.identity()));

// toMap with merge function (handle duplicates)
Map<String, Employee> byName = employees.stream()
    .collect(Collectors.toMap(
        Employee::getName,
        Function.identity(),
        (existing, replacement) -> existing  // keep first on conflict
    ));

// groupingBy
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDepartment));

// groupingBy with downstream collector
Map<String, Double> avgSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.averagingDouble(Employee::getSalary)
    ));

Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.counting()
    ));

Map<String, Optional<Employee>> highestPaidByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))
    ));

// partitioningBy (true/false split)
Map<Boolean, List<Employee>> partition = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.getSalary() > 50000));
List<Employee> highEarners = partition.get(true);
List<Employee> others = partition.get(false);

// joining
String csv = employees.stream()
    .map(Employee::getName)
    .collect(Collectors.joining(", ", "[", "]"));
// [Alice, Bob, Charlie]

// summarizing
DoubleSummaryStatistics stats = employees.stream()
    .collect(Collectors.summarizingDouble(Employee::getSalary));
// stats.getCount(), stats.getSum(), stats.getMin(), stats.getMax(), stats.getAverage()

// collectingAndThen
List<Employee> unmodifiable = employees.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList
    ));

// mapping inside groupingBy
Map<String, Set<String>> namesByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.mapping(Employee::getName, Collectors.toSet())
    ));
```

### Match Operations

```java
boolean anyAdult = employees.stream().anyMatch(e -> e.getAge() >= 18);
boolean allAdult = employees.stream().allMatch(e -> e.getAge() >= 18);
boolean noneMinor = employees.stream().noneMatch(e -> e.getAge() < 18);
```

### Find Operations

```java
Optional<Employee> first = employees.stream()
    .filter(e -> e.getSalary() > 100000)
    .findFirst();  // deterministic

Optional<Employee> any = employees.parallelStream()
    .filter(e -> e.getSalary() > 100000)
    .findAny();  // non-deterministic (faster in parallel)
```

---

## Dry Run

### Lazy Pipeline Execution

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8);

List<Integer> result = numbers.stream()
    .filter(n -> n % 2 == 0)    // keeps even
    .map(n -> n * 10)           // multiply by 10
    .limit(2)                   // take first 2
    .collect(Collectors.toList());

// Execution (element by element, NOT operation by operation):
// 1: filter(1%2==0) → false → skip
// 2: filter(2%2==0) → true → map(2*10) → 20 → limit(count=1) → add
// 3: filter(3%2==0) → false → skip
// 4: filter(4%2==0) → true → map(4*10) → 40 → limit(count=2) → add → STOP
// 5,6,7,8: never processed!
// Result: [20, 40]
```

### GroupingBy Execution

```java
List<Employee> emps = List.of(
    new Employee("Alice", "IT", 70000),
    new Employee("Bob", "HR", 50000),
    new Employee("Charlie", "IT", 80000),
    new Employee("Diana", "HR", 60000)
);

Map<String, Double> avgByDept = emps.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.averagingDouble(Employee::getSalary)
    ));

// Step 1: Alice → dept="IT" → bucket "IT"=[70000]
// Step 2: Bob → dept="HR" → bucket "HR"=[50000]
// Step 3: Charlie → dept="IT" → bucket "IT"=[70000, 80000]
// Step 4: Diana → dept="HR" → bucket "HR"=[50000, 60000]
// Step 5: Average each bucket:
//   "IT" → (70000+80000)/2 = 75000.0
//   "HR" → (50000+60000)/2 = 55000.0
// Result: {IT=75000.0, HR=55000.0}
```

---

## Complexity

| Operation | Time | Space | Notes |
|-----------|------|-------|-------|
| filter | O(n) | O(1) | Stateless |
| map | O(n) | O(1) | Stateless |
| flatMap | O(n*m) | O(1) | m = avg inner size |
| sorted | O(n log n) | O(n) | Buffers all elements |
| distinct | O(n) | O(n) | HashSet internally |
| limit/skip | O(n) | O(1) | Short-circuits |
| reduce | O(n) | O(1) | Single pass |
| collect(toList) | O(n) | O(n) | Builds result |
| collect(groupingBy) | O(n) | O(n) | HashMap + lists |
| count | O(n) | O(1) | But may be O(1) with sized streams |

---

## Real Project Usage

### Data Processing Pipeline

```java
public class ReportService {
    
    public SalesReport generateMonthlyReport(List<Transaction> transactions, YearMonth month) {
        Map<String, DoubleSummaryStatistics> byCategory = transactions.stream()
            .filter(t -> YearMonth.from(t.getDate()).equals(month))
            .filter(t -> t.getStatus() == Status.COMPLETED)
            .collect(Collectors.groupingBy(
                Transaction::getCategory,
                Collectors.summarizingDouble(Transaction::getAmount)
            ));
        
        double total = byCategory.values().stream()
            .mapToDouble(DoubleSummaryStatistics::getSum)
            .sum();
        
        String topCategory = byCategory.entrySet().stream()
            .max(Comparator.comparingDouble(e -> e.getValue().getSum()))
            .map(Map.Entry::getKey)
            .orElse("N/A");
        
        return new SalesReport(month, total, topCategory, byCategory);
    }
}
```

### Batch Processing

```java
public <T> void processBatches(List<T> items, int batchSize, Consumer<List<T>> processor) {
    IntStream.range(0, (items.size() + batchSize - 1) / batchSize)
        .mapToObj(i -> items.subList(
            i * batchSize, 
            Math.min((i + 1) * batchSize, items.size())
        ))
        .forEach(processor);
}
```

### Complex Query Replacement

```java
// Instead of complex SQL, sometimes easier in code:
public Map<String, EmployeeSummary> getDepartmentSummaries(List<Employee> employees) {
    return employees.stream()
        .collect(Collectors.groupingBy(
            Employee::getDepartment,
            Collectors.collectingAndThen(
                Collectors.toList(),
                emps -> new EmployeeSummary(
                    emps.size(),
                    emps.stream().mapToDouble(Employee::getSalary).average().orElse(0),
                    emps.stream().max(Comparator.comparingDouble(Employee::getSalary)).orElse(null),
                    emps.stream().mapToInt(Employee::getAge).average().orElse(0)
                )
            )
        ));
}
```

---

## Interview Questions and Answers

### Q1: What is the difference between `map()` and `flatMap()`?

**A:**
- `map()` — one-to-one transformation. Each element produces exactly one output element.
- `flatMap()` — one-to-many transformation. Each element produces a stream, and all streams are flattened into one.

```java
// map: Stream<T> → Stream<R>
List<String> words = List.of("hello", "world");
words.stream().map(String::length);  // Stream<Integer>: [5, 5]

// flatMap: Stream<T> → Stream<R> (flattened)
words.stream().flatMap(w -> Arrays.stream(w.split("")));
// Stream<String>: [h, e, l, l, o, w, o, r, l, d]
```

### Q2: Find the second highest salary

```java
Optional<Double> secondHighest = employees.stream()
    .map(Employee::getSalary)
    .distinct()
    .sorted(Comparator.reverseOrder())
    .skip(1)
    .findFirst();
```

### Q3: Group employees by department and find highest salary in each

```java
Map<String, Optional<Employee>> highestByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))
    ));

// Or get the salary value directly:
Map<String, Double> maxSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.collectingAndThen(
            Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary)),
            opt -> opt.map(Employee::getSalary).orElse(0.0)
        )
    ));
```

### Q4: Find duplicate elements in a list

```java
// Method 1: groupingBy + filter
List<Integer> duplicates = numbers.stream()
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
    .entrySet().stream()
    .filter(e -> e.getValue() > 1)
    .map(Map.Entry::getKey)
    .collect(Collectors.toList());

// Method 2: Set-based (more efficient)
Set<Integer> seen = new HashSet<>();
List<Integer> duplicates2 = numbers.stream()
    .filter(n -> !seen.add(n))  // add returns false if already present
    .collect(Collectors.toList());
```

### Q5: What is the difference between `findFirst()` and `findAny()`?

**A:**
- `findFirst()` — always returns the first element in encounter order. Deterministic.
- `findAny()` — returns any matching element. Non-deterministic in parallel streams but faster.

```java
// Sequential: both return same result
// Parallel: findAny() may return different element each run (but faster)
Optional<Employee> any = employees.parallelStream()
    .filter(e -> e.getSalary() > 50000)
    .findAny();  // faster in parallel — no ordering constraint
```

### Q6: Explain `reduce()` with identity, accumulator, and combiner

**A:**
```java
// Two-arg: identity + accumulator
int sum = numbers.stream().reduce(0, Integer::sum);
// 0 + 1 + 2 + 3 + 4 + 5 = 15

// Three-arg: identity + accumulator + combiner (for parallel)
int totalLength = words.parallelStream()
    .reduce(0,                              // identity
            (len, word) -> len + word.length(),  // accumulator (int + String → int)
            Integer::sum);                   // combiner (int + int → int, merges partial results)

// Combiner is ONLY used in parallel streams to merge partial results from threads
```

---

## Follow-up Questions and Answers

### Q: When should you NOT use streams?

**A:**
1. **Simple loops** — `for` loop is clearer for index-based or early-break logic
2. **Mutation-heavy** — streams discourage side effects; loops are better for accumulating into existing structures
3. **Performance-critical tight loops** — stream overhead matters for very small collections or hot paths
4. **Checked exceptions** — streams don't play well with checked exceptions
5. **Debugging** — complex stream pipelines are harder to debug than loops

### Q: What is the difference between `Collection.stream()` and `Stream.of()`?

**A:**
- `Collection.stream()` — creates stream backed by the collection's spliterator
- `Stream.of(values)` — creates stream from explicit values (varargs or array)
- `Stream.of(collection)` — WRONG! Creates a stream of ONE element (the collection itself)

```java
List<String> list = List.of("a", "b");
Stream.of(list);       // Stream<List<String>> — one element!
list.stream();         // Stream<String> — two elements
Stream.of("a", "b");  // Stream<String> — two elements
```

### Q: How does parallel stream work internally?

**A:** Uses the **ForkJoinPool.commonPool()** by default:
1. Splits the source using `Spliterator.trySplit()`
2. Each split is processed by a different thread
3. Results are combined using the combiner function

```java
// Custom thread pool for parallel streams:
ForkJoinPool customPool = new ForkJoinPool(4);
List<String> result = customPool.submit(() ->
    list.parallelStream()
        .filter(...)
        .collect(Collectors.toList())
).get();
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Reusing a stream | `IllegalStateException` | Create a new stream |
| Side effects in `map/filter` | Unpredictable in parallel | Use `forEach` for side effects |
| `stream.peek()` for logic | Not guaranteed to execute | Use only for debugging |
| `collect(toMap)` with duplicate keys | `IllegalStateException` | Provide merge function |
| Parallel stream with small data | Slower than sequential | Use parallel only for large datasets |
| Modifying source during stream | `ConcurrentModificationException` | Don't modify during processing |
| `stream().count()` on `List` | Traverses entire stream | Use `list.size()` directly |
| Nested streams instead of flatMap | Unreadable `Stream<Stream<T>>` | Use `flatMap` |

---

## Best Practices

1. **Prefer method references** — `Employee::getName` over `e -> e.getName()`
2. **Use primitive streams** — `IntStream`, `LongStream`, `DoubleStream` to avoid boxing
3. **Short-circuit when possible** — `findFirst`, `anyMatch`, `limit`
4. **Avoid stateful lambdas** — no shared mutable state in stream operations
5. **Close resource-backed streams** — `Files.lines()` should be in try-with-resources
6. **Use `toList()` (Java 16+)** — `stream().toList()` instead of `.collect(Collectors.toList())`
7. **Profile before parallelizing** — parallel isn't always faster
8. **Keep pipelines readable** — break complex pipelines into variables

---

## Production Considerations

- **Memory:** `sorted()` and `distinct()` buffer ALL elements — dangerous with large streams
- **Parallel caution:** Shared mutable state, ordering requirements, and small datasets make parallel slower
- **Exception handling:** Wrap operations in try-catch or use a utility method
- **Debugging:** Use `peek()` temporarily, or break into variables for breakpoints
- **Performance:** For simple operations on small collections, a for-loop may outperform streams
- **Thread pool:** Parallel streams share `ForkJoinPool.commonPool()` — long operations block other parallel streams

---

## Related Topics

- [10. Java 8 Features](./10-java8-features.md) — lambda expressions, functional interfaces
- [12. Optional](./12-optional.md) — stream terminal operations return Optional
- [9. Comparable vs Comparator](./09-comparable-vs-comparator.md) — sorting in streams
