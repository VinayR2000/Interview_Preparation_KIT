# Design Patterns — Behavioral

## Strategy

### Theory
- Defines a family of algorithms, encapsulates each one, and makes them interchangeable
- Client selects algorithm at runtime
- Eliminates conditional logic (if/else, switch)

### Code
```java
public interface SortingStrategy {
    <T extends Comparable<T>> void sort(List<T> data);
}

public class QuickSort implements SortingStrategy {
    public <T extends Comparable<T>> void sort(List<T> data) { /* quicksort */ }
}

public class MergeSort implements SortingStrategy {
    public <T extends Comparable<T>> void sort(List<T> data) { /* mergesort */ }
}

public class DataProcessor {
    private SortingStrategy strategy;
    
    public void setStrategy(SortingStrategy strategy) {
        this.strategy = strategy;
    }
    
    public void process(List<Integer> data) {
        strategy.sort(data); // Delegates to chosen strategy
    }
}

// Usage: Switch at runtime
DataProcessor processor = new DataProcessor();
processor.setStrategy(data.size() < 1000 ? new QuickSort() : new MergeSort());
processor.process(data);
```

### When to Use
- Multiple algorithms for same task (sorting, pricing, validation)
- Want to switch behavior at runtime
- Eliminate growing if/else or switch statements
- Real: Comparator in Java, Spring's Resource loading, payment methods

---

## Observer

### Theory
- Defines one-to-many dependency: when one object changes state, all dependents are notified
- Publisher doesn't know who subscribers are
- Loose coupling between event producer and consumers

### Code
```java
public interface EventListener {
    void onEvent(Event event);
}

public class EventBus {
    private final Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();
    
    public void subscribe(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }
    
    public void unsubscribe(String eventType, EventListener listener) {
        listeners.getOrDefault(eventType, List.of()).remove(listener);
    }
    
    public void publish(String eventType, Event event) {
        listeners.getOrDefault(eventType, List.of())
                 .forEach(listener -> listener.onEvent(event));
    }
}

// Subscribers
public class EmailNotifier implements EventListener {
    public void onEvent(Event event) {
        if (event instanceof OrderPlacedEvent e) {
            sendEmail(e.getUserEmail(), "Order confirmed!");
        }
    }
}

public class InventoryUpdater implements EventListener {
    public void onEvent(Event event) {
        if (event instanceof OrderPlacedEvent e) {
            decrementStock(e.getItems());
        }
    }
}

// Usage
eventBus.subscribe("ORDER_PLACED", new EmailNotifier());
eventBus.subscribe("ORDER_PLACED", new InventoryUpdater());
eventBus.publish("ORDER_PLACED", new OrderPlacedEvent(order));
```

### When to Use
- One change triggers multiple actions
- Publishers shouldn't know about subscribers
- Event-driven systems
- Real: Spring ApplicationEvent, Java Swing listeners, Kafka consumers

---

## Chain of Responsibility

### Theory
- Chain of handlers that each decide whether to process a request or pass it to the next handler
- Decouples sender from receivers
- Request passes along chain until handled

### Code
```java
public abstract class RequestHandler {
    private RequestHandler next;
    
    public RequestHandler setNext(RequestHandler next) {
        this.next = next;
        return next; // For chaining
    }
    
    public void handle(HttpRequest request) {
        if (canHandle(request)) {
            doHandle(request);
        } else if (next != null) {
            next.handle(request);
        } else {
            throw new UnhandledRequestException();
        }
    }
    
    protected abstract boolean canHandle(HttpRequest request);
    protected abstract void doHandle(HttpRequest request);
}

// Middleware chain
public class AuthenticationHandler extends RequestHandler {
    protected boolean canHandle(HttpRequest req) { return true; } // Always runs
    protected void doHandle(HttpRequest req) {
        if (!isAuthenticated(req)) throw new UnauthorizedException();
        next.handle(req); // Pass to next
    }
}

public class RateLimitHandler extends RequestHandler {
    protected boolean canHandle(HttpRequest req) { return true; }
    protected void doHandle(HttpRequest req) {
        if (isRateLimited(req)) throw new TooManyRequestsException();
        next.handle(req);
    }
}

public class ValidationHandler extends RequestHandler {
    protected boolean canHandle(HttpRequest req) { return req.hasBody(); }
    protected void doHandle(HttpRequest req) {
        validate(req.getBody());
        next.handle(req);
    }
}

// Build chain
RequestHandler chain = new AuthenticationHandler();
chain.setNext(new RateLimitHandler())
     .setNext(new ValidationHandler())
     .setNext(new BusinessLogicHandler());

chain.handle(request);
```

### When to Use
- Multiple handlers for same request (middleware)
- Order of processing matters
- Handler should be able to stop the chain
- Real: Servlet filters, Spring Security filter chain, logging levels

---

## Template Method

### Theory
- Defines skeleton of an algorithm in base class, letting subclasses override specific steps
- "Don't call us, we'll call you" (Hollywood Principle)
- Fixed algorithm structure with customizable steps

### Code
```java
public abstract class DataParser {
    
    // Template method — defines the algorithm skeleton
    public final List<Record> parse(File file) {
        openFile(file);
        String rawData = readData();
        List<Record> records = parseRecords(rawData);  // Abstract step
        records = filterRecords(records);               // Hook (optional override)
        closeFile();
        return records;
    }
    
    private void openFile(File file) { /* common logic */ }
    private String readData() { /* common logic */ }
    private void closeFile() { /* common logic */ }
    
    // Abstract step — subclass MUST implement
    protected abstract List<Record> parseRecords(String rawData);
    
    // Hook — subclass CAN override (has default behavior)
    protected List<Record> filterRecords(List<Record> records) {
        return records; // Default: no filtering
    }
}

public class CSVParser extends DataParser {
    @Override
    protected List<Record> parseRecords(String rawData) {
        return rawData.lines()
            .map(line -> line.split(","))
            .map(Record::fromArray)
            .collect(toList());
    }
}

public class JSONParser extends DataParser {
    @Override
    protected List<Record> parseRecords(String rawData) {
        return objectMapper.readValue(rawData, new TypeReference<>() {});
    }
    
    @Override
    protected List<Record> filterRecords(List<Record> records) {
        return records.stream().filter(Record::isValid).collect(toList());
    }
}
```

### Template Method vs Strategy
| Template Method | Strategy |
|----------------|----------|
| Inheritance-based | Composition-based |
| Fixed skeleton, customize steps | Entire algorithm swappable |
| Compile-time binding | Runtime binding |
| "Override steps" | "Inject algorithm" |

---

## Command

### Theory
- Encapsulates a request as an object
- Allows: parameterize, queue, log, undo operations
- Separates "what to do" from "when to do it"

### Code
```java
public interface Command {
    void execute();
    void undo();
}

public class AddItemCommand implements Command {
    private final ShoppingCart cart;
    private final Item item;
    
    public AddItemCommand(ShoppingCart cart, Item item) {
        this.cart = cart;
        this.item = item;
    }
    
    public void execute() { cart.add(item); }
    public void undo() { cart.remove(item); }
}

// Command invoker with history (undo support)
public class CommandHistory {
    private final Deque<Command> history = new ArrayDeque<>();
    
    public void execute(Command command) {
        command.execute();
        history.push(command);
    }
    
    public void undo() {
        if (!history.isEmpty()) {
            history.pop().undo();
        }
    }
}
```

### When to Use
- Undo/redo functionality
- Queue operations for later execution
- Logging/audit trail of operations
- Real: Runnable in Java, Spring @Async, text editor undo

---

## State

### Theory
- Object alters its behavior when internal state changes (appears to change its class)
- Replaces complex conditional logic based on state
- Each state is a separate class

### Code
```java
public interface OrderState {
    void next(Order order);
    void cancel(Order order);
    String getStatus();
}

public class PendingState implements OrderState {
    public void next(Order order) { order.setState(new PaidState()); }
    public void cancel(Order order) { order.setState(new CancelledState()); }
    public String getStatus() { return "PENDING"; }
}

public class PaidState implements OrderState {
    public void next(Order order) { order.setState(new ShippedState()); }
    public void cancel(Order order) {
        // Refund logic
        order.setState(new CancelledState());
    }
    public String getStatus() { return "PAID"; }
}

public class ShippedState implements OrderState {
    public void next(Order order) { order.setState(new DeliveredState()); }
    public void cancel(Order order) {
        throw new IllegalStateException("Cannot cancel shipped order");
    }
    public String getStatus() { return "SHIPPED"; }
}

public class Order {
    private OrderState state = new PendingState();
    
    public void setState(OrderState state) { this.state = state; }
    public void next() { state.next(this); }
    public void cancel() { state.cancel(this); }
    public String getStatus() { return state.getStatus(); }
}
```

### When to Use
- Object behavior depends on state (and has many states)
- Complex conditional logic based on state
- State transitions have rules/constraints
- Real: TCP connection states, order lifecycle, vending machine, traffic light

---

## Iterator

### Theory
- Provides a way to access elements of a collection sequentially without exposing internal structure
- Standardizes traversal across different collection types

### When to Use
- Traverse collections without exposing internals
- Multiple traversal algorithms needed for same collection
- Real: Java Iterator/Iterable, Stream API, database cursor

---

## Mediator

### Theory
- Defines an object that encapsulates how a set of objects interact
- Reduces direct dependencies between objects (communicate through mediator)
- Turns many-to-many into many-to-one

### Code
```java
// Chat room as mediator
public class ChatRoom {
    private final Map<String, User> users = new HashMap<>();
    
    public void register(User user) {
        users.put(user.getName(), user);
    }
    
    public void sendMessage(String from, String to, String message) {
        User recipient = users.get(to);
        if (recipient != null) {
            recipient.receive(from, message);
        }
    }
    
    public void broadcast(String from, String message) {
        users.values().stream()
            .filter(u -> !u.getName().equals(from))
            .forEach(u -> u.receive(from, message));
    }
}
```

### When to Use
- Many objects communicating in complex ways
- Reduce coupling between components
- Centralize communication logic
- Real: Air traffic control, chat rooms, event buses, Spring's ApplicationContext

---

## Most Important Patterns for Interviews

### Priority Focus

| Pattern | Why Important | Typical LLD Use |
|---------|--------------|-----------------|
| Strategy | Eliminates if/else, runtime flexibility | Payment methods, pricing rules |
| Factory | Object creation decoupling | Creating vehicles, notifications |
| Builder | Complex object construction | Orders, configurations |
| Observer | Event handling, notifications | Pub/sub, state change notifications |
| Decorator | Dynamic behavior addition | I/O streams, middleware |
| Adapter | Integration with external systems | Third-party API integration |
| Facade | Simplify complex subsystems | API layers, service orchestration |
| Proxy | Caching, access control, lazy loading | Spring AOP, JPA lazy loading |
| Chain of Responsibility | Request pipeline | Middleware, validation chains |
| Template Method | Algorithm with variable steps | Parsers, report generators |
| State | State machine behavior | Order status, game states |

---

## Interview Questions

**Q: Strategy vs State — what's the difference?**
> Both encapsulate behavior in separate classes, but:
> - Strategy: Client chooses which algorithm to use (external decision)
> - State: Object transitions between states internally (state decides next state)
> - Strategy: Algorithms are interchangeable but independent
> - State: States know about each other (transition logic)

**Q: How would you implement undo/redo in a text editor?**
> Command pattern. Each action (type, delete, paste) is a Command object with execute() and undo(). Maintain two stacks: undo stack (executed commands) and redo stack (undone commands). Undo: pop from undo stack, call undo(), push to redo stack. Redo: pop from redo stack, call execute(), push to undo stack.

**Q: When would you use Chain of Responsibility over Strategy?**
> Chain: Multiple handlers potentially process the same request in sequence (middleware pipeline). Any handler can stop the chain.
> Strategy: ONE algorithm processes the entire request (selected beforehand).
> Chain: "Who handles this?" Strategy: "How do we handle this?"

**Q: How is Observer pattern different from Pub/Sub?**
> Observer: Direct coupling between subject and observers (subject holds observer references).
> Pub/Sub: Indirect through message broker (publisher and subscriber don't know each other).
> Observer is in-process, Pub/Sub can be distributed. Pub/Sub is more scalable but more complex.

**Q: How does Template Method relate to frameworks?**
> Frameworks use Template Method extensively — they define the skeleton (lifecycle), you fill in the steps. Spring: `AbstractController.handleRequest()` calls your implementation. JUnit: `setUp()` → `test()` → `tearDown()`. This is "Inversion of Control" — framework calls your code.

---

## Common Mistakes
- Using Observer for synchronous operations that should be direct calls
- State pattern without clear state transition rules
- Chain of Responsibility without a terminal handler (request disappears)
- Over-using Command when simple method call suffices
- Confusing Mediator with Facade (Mediator for peer communication, Facade for simplification)

---

## Best Practices
- Strategy: Use with DI frameworks for cleaner injection
- Observer: Consider async notification for expensive subscribers
- Chain: Always have a default/fallback handler
- State: Document state transitions (state diagram)
- Command: Keep commands small and focused
- Template Method: Prefer hooks (optional override) over abstract methods when possible

---

## Production Considerations
- Observer: Handle subscriber failures gracefully (don't let one subscriber break others)
- Chain: Monitor chain execution time (long chains add latency)
- Command: Serializable commands enable distributed processing and event sourcing
- State: Persist current state for recovery after crashes
- Strategy: Use feature flags to A/B test different strategies

---

## Related Topics
- Event-Driven Architecture (uses Observer at system level)
- Saga Pattern (uses State + Command at distributed level)
- Middleware Pattern (Chain of Responsibility)
- Plugin Architecture (Strategy + Factory)
