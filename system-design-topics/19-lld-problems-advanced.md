# LLD Interview Problems — Advanced

## 1. Chess

### Requirements
- Two players, 8x8 board
- All piece types with valid movement rules
- Check, checkmate, stalemate detection
- Move validation (can't move into check)
- Special moves: castling, en passant, pawn promotion
- Move history and undo

### Key Design
```java
// Piece hierarchy with Strategy for movement
public abstract class Piece {
    protected Color color;
    protected Position position;
    protected boolean hasMoved;
    
    public abstract List<Position> getValidMoves(Board board);
    public abstract PieceType getType();
    
    protected boolean isValidPosition(Position pos) {
        return pos.getRow() >= 0 && pos.getRow() < 8 
            && pos.getCol() >= 0 && pos.getCol() < 8;
    }
    
    protected boolean isEnemyOrEmpty(Board board, Position pos) {
        Piece piece = board.getPieceAt(pos);
        return piece == null || piece.getColor() != this.color;
    }
}

public class Knight extends Piece {
    private static final int[][] MOVES = {
        {-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}
    };
    
    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        for (int[] move : MOVES) {
            Position newPos = position.offset(move[0], move[1]);
            if (isValidPosition(newPos) && isEnemyOrEmpty(board, newPos)) {
                moves.add(newPos);
            }
        }
        return moves;
    }
}

public class King extends Piece {
    @Override
    public List<Position> getValidMoves(Board board) {
        List<Position> moves = new ArrayList<>();
        // 8 directions, 1 step
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                Position newPos = position.offset(dr, dc);
                if (isValidPosition(newPos) && isEnemyOrEmpty(board, newPos)) {
                    // Additional: can't move into check
                    if (!board.isUnderAttack(newPos, color.opposite())) {
                        moves.add(newPos);
                    }
                }
            }
        }
        // Castling logic
        addCastlingMoves(board, moves);
        return moves;
    }
}

// Board
public class Board {
    private final Piece[][] grid = new Piece[8][8];
    
    public boolean isInCheck(Color color) {
        Position kingPos = findKing(color);
        return isUnderAttack(kingPos, color.opposite());
    }
    
    public boolean isCheckmate(Color color) {
        if (!isInCheck(color)) return false;
        return getAllPieces(color).stream()
            .noneMatch(piece -> piece.getValidMoves(this).stream()
                .anyMatch(move -> !wouldBeInCheckAfterMove(piece, move)));
    }
    
    public boolean isStalemate(Color color) {
        if (isInCheck(color)) return false;
        return getAllPieces(color).stream()
            .allMatch(piece -> piece.getValidMoves(this).isEmpty());
    }
}

// Game with Command pattern for move history
public class Game {
    private final Board board;
    private final Player whitePlayer;
    private final Player blackPlayer;
    private final Deque<MoveCommand> moveHistory = new ArrayDeque<>();
    private Color currentTurn;
    private GameStatus status;
    
    public MoveResult makeMove(Position from, Position to) {
        Piece piece = board.getPieceAt(from);
        if (piece == null || piece.getColor() != currentTurn) {
            return MoveResult.INVALID;
        }
        
        if (!piece.getValidMoves(board).contains(to)) {
            return MoveResult.INVALID;
        }
        
        MoveCommand command = new MoveCommand(board, piece, from, to);
        command.execute();
        moveHistory.push(command);
        
        switchTurn();
        
        if (board.isCheckmate(currentTurn)) return MoveResult.CHECKMATE;
        if (board.isStalemate(currentTurn)) return MoveResult.STALEMATE;
        if (board.isInCheck(currentTurn)) return MoveResult.CHECK;
        
        return MoveResult.SUCCESS;
    }
    
    public void undoMove() {
        if (!moveHistory.isEmpty()) {
            moveHistory.pop().undo();
            switchTurn();
        }
    }
}
```

---

## 2. Snake & Ladders

### Requirements
- Multiple players (2-4)
- Configurable board size
- Configurable snakes and ladders
- Dice rolling
- Win detection
- Turn management

### Key Design
```java
public class GameBoard {
    private final int size;
    private final Map<Integer, Integer> snakes;  // head → tail (higher → lower)
    private final Map<Integer, Integer> ladders; // bottom → top (lower → higher)
    
    public int getNextPosition(int currentPos, int diceValue) {
        int newPos = currentPos + diceValue;
        if (newPos > size) return currentPos; // Can't overshoot
        
        // Check for snake or ladder at landing position
        if (snakes.containsKey(newPos)) return snakes.get(newPos);
        if (ladders.containsKey(newPos)) return ladders.get(newPos);
        
        return newPos;
    }
}

public class Game {
    private final GameBoard board;
    private final List<Player> players;
    private final Dice dice;
    private int currentPlayerIndex;
    
    public TurnResult playTurn() {
        Player current = players.get(currentPlayerIndex);
        int diceValue = dice.roll();
        int newPosition = board.getNextPosition(current.getPosition(), diceValue);
        current.setPosition(newPosition);
        
        if (newPosition == board.getSize()) {
            return TurnResult.win(current);
        }
        
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        return TurnResult.ongoing(current, diceValue, newPosition);
    }
}

// Builder for board configuration
public class GameBoardBuilder {
    private int size = 100;
    private final Map<Integer, Integer> snakes = new HashMap<>();
    private final Map<Integer, Integer> ladders = new HashMap<>();
    
    public GameBoardBuilder addSnake(int head, int tail) {
        if (head <= tail) throw new IllegalArgumentException("Snake must go down");
        snakes.put(head, tail);
        return this;
    }
    
    public GameBoardBuilder addLadder(int bottom, int top) {
        if (bottom >= top) throw new IllegalArgumentException("Ladder must go up");
        ladders.put(bottom, top);
        return this;
    }
    
    public GameBoard build() {
        // Validate: no snake at ladder position and vice versa
        return new GameBoard(size, snakes, ladders);
    }
}
```

---

## 3. Cab Booking System (Uber LLD)

### Requirements
- Rider requests ride (pickup, destination)
- Find nearby drivers
- Driver accepts/rejects
- Trip state management
- Fare calculation
- Rating system

### Key Design
```java
// Location tracking
public class Location {
    private double latitude;
    private double longitude;
    
    public double distanceTo(Location other) {
        // Haversine formula
        return calculateHaversineDistance(this, other);
    }
}

// Driver matching strategy
public interface MatchingStrategy {
    Optional<Driver> findBestDriver(List<Driver> available, Location pickup);
}

public class NearestDriverStrategy implements MatchingStrategy {
    private final double maxRadius;
    
    public Optional<Driver> findBestDriver(List<Driver> available, Location pickup) {
        return available.stream()
            .filter(d -> d.getLocation().distanceTo(pickup) <= maxRadius)
            .min(Comparator.comparingDouble(d -> d.getLocation().distanceTo(pickup)));
    }
}

// Trip with State pattern
public interface TripState {
    void driverAccept(Trip trip);
    void driverArrive(Trip trip);
    void startRide(Trip trip);
    void endRide(Trip trip);
    void cancel(Trip trip);
}

public class RequestedState implements TripState {
    public void driverAccept(Trip trip) { trip.setState(new AcceptedState()); }
    public void cancel(Trip trip) { trip.setState(new CancelledState()); }
    public void startRide(Trip trip) { throw new InvalidStateException(); }
    // ...
}

public class InProgressState implements TripState {
    public void endRide(Trip trip) {
        trip.setEndTime(Instant.now());
        trip.setFare(trip.getFareCalculator().calculate(trip));
        trip.setState(new CompletedState());
    }
    public void cancel(Trip trip) { throw new InvalidStateException("Can't cancel in-progress"); }
}

// Fare calculation
public interface FareCalculator {
    double calculate(Trip trip);
}

public class StandardFareCalculator implements FareCalculator {
    private static final double BASE_FARE = 50;
    private static final double PER_KM = 12;
    private static final double PER_MINUTE = 2;
    
    public double calculate(Trip trip) {
        double distance = trip.getPickup().distanceTo(trip.getDropoff());
        long minutes = Duration.between(trip.getStartTime(), trip.getEndTime()).toMinutes();
        double fare = BASE_FARE + (distance * PER_KM) + (minutes * PER_MINUTE);
        return fare * trip.getSurgeMultiplier();
    }
}
```

---

## 4. Amazon Order System

### Requirements
- Shopping cart management
- Order creation from cart
- Order state machine (placed → confirmed → shipped → delivered)
- Inventory reservation
- Payment integration
- Return/refund handling

### Key Design
```java
// Order with state machine
public class Order {
    private String orderId;
    private Customer customer;
    private List<OrderItem> items;
    private OrderState state;
    private Address shippingAddress;
    private Payment payment;
    private LocalDateTime createdAt;
    
    public void confirm() { state.confirm(this); }
    public void ship(String trackingId) { state.ship(this, trackingId); }
    public void deliver() { state.deliver(this); }
    public void cancel() { state.cancel(this); }
    public void returnOrder(String reason) { state.returnOrder(this, reason); }
}

// State transitions with business rules
public class PlacedState implements OrderState {
    public void confirm(Order order) {
        // Reserve inventory
        inventoryService.reserve(order.getItems());
        // Charge payment
        paymentService.charge(order.getPayment());
        order.setState(new ConfirmedState());
        notificationService.notify(order, "Order confirmed");
    }
    
    public void cancel(Order order) {
        order.setState(new CancelledState());
        notificationService.notify(order, "Order cancelled");
    }
    
    public void ship(Order order, String trackingId) {
        throw new InvalidStateTransition("Cannot ship unconfirmed order");
    }
}

// Return handling
public class ReturnRequest {
    private String returnId;
    private Order order;
    private List<OrderItem> returnItems;
    private String reason;
    private ReturnStatus status;
    
    public void approve() {
        this.status = ReturnStatus.APPROVED;
        // Initiate refund
        RefundRequest refund = new RefundRequest(order.getPayment(), calculateRefundAmount());
        paymentService.refund(refund);
        // Update inventory
        inventoryService.returnItems(returnItems);
    }
}
```

---

## 5. Notification Framework

### Requirements
- Support multiple channels (email, SMS, push, in-app)
- Template-based messages with variable substitution
- Priority levels (urgent, high, normal, low)
- Retry on failure
- Rate limiting per user
- User preferences (opt-in/opt-out per channel)
- Batch sending

### Key Design
```java
// Template engine
public class NotificationTemplate {
    private String templateId;
    private String channel;
    private String subjectTemplate;
    private String bodyTemplate;
    
    public String render(Map<String, Object> variables) {
        String rendered = bodyTemplate;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", 
                                       String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}

// Notification sender with Chain of Responsibility (pre-processing)
public abstract class NotificationHandler {
    private NotificationHandler next;
    
    public NotificationHandler setNext(NotificationHandler next) {
        this.next = next;
        return next;
    }
    
    public void handle(NotificationRequest request) {
        if (process(request) && next != null) {
            next.handle(request);
        }
    }
    
    protected abstract boolean process(NotificationRequest request);
}

public class PreferenceCheckHandler extends NotificationHandler {
    protected boolean process(NotificationRequest request) {
        UserPreferences prefs = prefsService.get(request.getUserId());
        return prefs.isChannelEnabled(request.getChannel());
    }
}

public class RateLimitHandler extends NotificationHandler {
    protected boolean process(NotificationRequest request) {
        return rateLimiter.allowRequest(request.getUserId(), request.getChannel());
    }
}

public class TemplateRenderHandler extends NotificationHandler {
    protected boolean process(NotificationRequest request) {
        String rendered = templateEngine.render(request.getTemplateId(), request.getVariables());
        request.setRenderedContent(rendered);
        return true;
    }
}

public class DeliveryHandler extends NotificationHandler {
    private final Map<String, NotificationSender> senders;
    
    protected boolean process(NotificationRequest request) {
        NotificationSender sender = senders.get(request.getChannel());
        try {
            sender.send(request);
            return true;
        } catch (Exception e) {
            // Queue for retry
            retryQueue.add(request);
            return false;
        }
    }
}

// Build chain
NotificationHandler chain = new PreferenceCheckHandler();
chain.setNext(new RateLimitHandler())
     .setNext(new TemplateRenderHandler())
     .setNext(new DeliveryHandler(senders));
```

---

## 6. Logging Framework

### Requirements
- Multiple log levels (TRACE, DEBUG, INFO, WARN, ERROR, FATAL)
- Multiple output destinations (console, file, database, remote)
- Configurable format (JSON, plain text)
- Thread-safe
- Async logging (don't block caller)
- Log rotation (size/time-based)

### Key Design
```java
public enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR, FATAL }

// Logger with Builder
public class Logger {
    private final String name;
    private final LogLevel minLevel;
    private final List<LogAppender> appenders;
    private final LogFormatter formatter;
    private final ExecutorService asyncExecutor;
    
    public void log(LogLevel level, String message, Object... args) {
        if (level.ordinal() < minLevel.ordinal()) return;
        
        LogEvent event = new LogEvent(level, name, format(message, args), 
                                       Thread.currentThread().getName(), Instant.now());
        
        String formatted = formatter.format(event);
        
        if (asyncExecutor != null) {
            asyncExecutor.submit(() -> writeToAppenders(formatted, event));
        } else {
            writeToAppenders(formatted, event);
        }
    }
    
    private void writeToAppenders(String formatted, LogEvent event) {
        appenders.forEach(appender -> {
            try {
                appender.append(formatted, event);
            } catch (Exception e) {
                // Fallback: write to stderr, never throw from logger
                System.err.println("Logging failed: " + e.getMessage());
            }
        });
    }
    
    public void info(String msg, Object... args) { log(LogLevel.INFO, msg, args); }
    public void error(String msg, Object... args) { log(LogLevel.ERROR, msg, args); }
}

// Appenders (Strategy pattern)
public interface LogAppender {
    void append(String formatted, LogEvent event);
}

public class FileAppender implements LogAppender {
    private final String filePath;
    private final RotationPolicy rotationPolicy;
    private BufferedWriter writer;
    
    public synchronized void append(String formatted, LogEvent event) {
        if (rotationPolicy.shouldRotate()) {
            rotate();
        }
        writer.write(formatted);
        writer.newLine();
        writer.flush();
    }
}

public class ConsoleAppender implements LogAppender {
    public void append(String formatted, LogEvent event) {
        PrintStream stream = event.getLevel().ordinal() >= LogLevel.ERROR.ordinal() 
            ? System.err : System.out;
        stream.println(formatted);
    }
}

// Formatters
public interface LogFormatter {
    String format(LogEvent event);
}

public class JsonFormatter implements LogFormatter {
    public String format(LogEvent event) {
        return String.format(
            "{\"timestamp\":\"%s\",\"level\":\"%s\",\"logger\":\"%s\",\"thread\":\"%s\",\"message\":\"%s\"}",
            event.getTimestamp(), event.getLevel(), event.getLoggerName(), 
            event.getThreadName(), event.getMessage()
        );
    }
}

// Rotation
public interface RotationPolicy {
    boolean shouldRotate();
}

public class SizeBasedRotation implements RotationPolicy {
    private final long maxBytes;
    public boolean shouldRotate() { return currentFileSize() > maxBytes; }
}

public class TimeBasedRotation implements RotationPolicy {
    private final Duration interval;
    private Instant lastRotation;
    public boolean shouldRotate() { 
        return Instant.now().isAfter(lastRotation.plus(interval)); 
    }
}
```

---

## 7. Payment Framework

### Requirements
- Support multiple payment providers (Stripe, PayPal, Razorpay)
- Idempotent payment processing
- Refund handling
- Payment state machine
- Webhook processing from providers
- Retry with exponential backoff

### Key Design
```java
// Payment gateway abstraction (Adapter + Strategy)
public interface PaymentGateway {
    PaymentResponse charge(PaymentRequest request);
    RefundResponse refund(RefundRequest request);
    PaymentStatus checkStatus(String transactionId);
}

public class StripeGateway implements PaymentGateway {
    public PaymentResponse charge(PaymentRequest request) {
        // Adapt our interface to Stripe's API
        StripeCharge charge = Stripe.charges.create(mapToStripeParams(request));
        return mapFromStripeResponse(charge);
    }
}

// Idempotent payment processing
public class PaymentService {
    private final PaymentGateway gateway;
    private final PaymentRepository repository;
    private final IdempotencyStore idempotencyStore;
    
    public PaymentResponse processPayment(PaymentRequest request) {
        // Check idempotency
        String idempotencyKey = request.getIdempotencyKey();
        Optional<PaymentResponse> cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent()) return cached.get();
        
        // Process
        Payment payment = new Payment(request);
        payment.setStatus(PaymentStatus.PROCESSING);
        repository.save(payment);
        
        try {
            PaymentResponse response = gateway.charge(request);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setTransactionId(response.getTransactionId());
            repository.save(payment);
            
            // Store for idempotency
            idempotencyStore.put(idempotencyKey, response);
            return response;
        } catch (PaymentDeclinedException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            repository.save(payment);
            throw e;
        }
    }
}

// Retry with exponential backoff (Template Method)
public abstract class RetryableOperation<T> {
    private final int maxRetries;
    private final Duration baseDelay;
    
    public T executeWithRetry() {
        int attempts = 0;
        while (true) {
            try {
                return execute();
            } catch (RetryableException e) {
                attempts++;
                if (attempts >= maxRetries) throw new MaxRetriesExceededException(e);
                Duration delay = baseDelay.multipliedBy((long) Math.pow(2, attempts - 1));
                Thread.sleep(delay.toMillis());
            }
        }
    }
    
    protected abstract T execute();
}
```

---

## Interview Tips for Advanced LLD

### What Interviewers Look For

| Signal | How to Demonstrate |
|--------|-------------------|
| SOLID adherence | Clearly separated responsibilities, programming to interfaces |
| Pattern awareness | Use patterns where they naturally fit (don't force) |
| Concurrency handling | Thread safety for shared state, async processing |
| Extensibility | Easy to add new pieces (strategies, handlers) without modification |
| Edge cases | Discuss failure scenarios, boundary conditions |
| Trade-offs | Explain why you chose this design over alternatives |

### Common Follow-Up Questions
- "How would you make this thread-safe?"
- "What if we need to add a new [type/channel/state]?"
- "How would you handle failures in this component?"
- "What design patterns are you using and why?"
- "How would you unit test this?"

---

## Common Mistakes
- Not handling concurrency (multiple threads accessing shared state)
- Missing error handling (what if payment gateway is down?)
- Not making the design extensible (hardcoding types)
- Violating SOLID (God classes, unused interface methods)
- Over-engineering (applying every pattern you know)
- Not discussing state machines where applicable

---

## Best Practices
- Start with clear state diagrams for stateful entities
- Identify extension points early (what will change?)
- Use Command pattern for operations that need undo/audit
- Chain of Responsibility for pipelines/middleware
- State pattern for complex state machines
- Strategy + Factory for pluggable behavior
- Always discuss thread safety for shared resources

---

## Related Topics
- Concurrent Design (thread safety)
- Advanced Java Design (generics, functional)
- Production-Level Design
- System Design (HLD counterparts)
