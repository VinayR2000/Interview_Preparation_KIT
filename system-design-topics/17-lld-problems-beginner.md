# LLD Interview Problems — Beginner

## Approach for Every LLD Problem

```
1. Requirements (clarify scope)
     ↓
2. Entities (identify nouns)
     ↓
3. Relationships (how entities connect)
     ↓
4. Interfaces (define contracts)
     ↓
5. Classes (implement with SOLID)
     ↓
6. Design Patterns (apply where appropriate)
     ↓
7. Class Diagram
     ↓
8. Java Implementation
     ↓
9. Edge Cases
```

---

## 1. Parking Lot

### Requirements
- Multiple floors, multiple types of spots (compact, regular, large)
- Park vehicles (motorcycle, car, truck)
- Vehicles matched to appropriate spot size
- Track occupancy per floor
- Calculate parking fee (hourly rate)
- Entry/exit gates with ticketing

### Entities
- ParkingLot, Floor, ParkingSpot, Vehicle, Ticket, Gate, Payment

### Key Design Decisions
- **Strategy Pattern**: Fee calculation (hourly, daily, flat rate)
- **Factory Pattern**: Create appropriate spot type
- **Singleton**: ParkingLot instance

### Code
```java
// Enums
public enum VehicleType { MOTORCYCLE, CAR, TRUCK }
public enum SpotType { COMPACT, REGULAR, LARGE }
public enum SpotStatus { AVAILABLE, OCCUPIED, RESERVED }

// Core entities
public abstract class Vehicle {
    private String licensePlate;
    private VehicleType type;
    
    public abstract SpotType getRequiredSpotType();
}

public class Car extends Vehicle {
    public SpotType getRequiredSpotType() { return SpotType.REGULAR; }
}

public class Truck extends Vehicle {
    public SpotType getRequiredSpotType() { return SpotType.LARGE; }
}

// Parking Spot
public class ParkingSpot {
    private final String spotId;
    private final SpotType type;
    private final int floor;
    private SpotStatus status;
    private Vehicle currentVehicle;
    
    public boolean canFit(Vehicle vehicle) {
        return this.type.ordinal() >= vehicle.getRequiredSpotType().ordinal()
               && status == SpotStatus.AVAILABLE;
    }
    
    public void park(Vehicle vehicle) {
        this.currentVehicle = vehicle;
        this.status = SpotStatus.OCCUPIED;
    }
    
    public void release() {
        this.currentVehicle = null;
        this.status = SpotStatus.AVAILABLE;
    }
}

// Ticket
public class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    
    public Duration getDuration() {
        return Duration.between(entryTime, exitTime != null ? exitTime : LocalDateTime.now());
    }
}

// Fee Strategy
public interface FeeStrategy {
    double calculate(Ticket ticket);
}

public class HourlyFeeStrategy implements FeeStrategy {
    private final Map<VehicleType, Double> ratePerHour;
    
    public double calculate(Ticket ticket) {
        long hours = ticket.getDuration().toHours() + 1; // Round up
        double rate = ratePerHour.get(ticket.getVehicle().getType());
        return hours * rate;
    }
}

// Parking Lot (Singleton)
public class ParkingLot {
    private static ParkingLot instance;
    private final List<Floor> floors;
    private final FeeStrategy feeStrategy;
    
    public synchronized Ticket parkVehicle(Vehicle vehicle) {
        ParkingSpot spot = findAvailableSpot(vehicle);
        if (spot == null) throw new ParkingFullException();
        spot.park(vehicle);
        return new Ticket(generateId(), vehicle, spot, LocalDateTime.now());
    }
    
    public Payment exitVehicle(Ticket ticket) {
        ticket.setExitTime(LocalDateTime.now());
        double fee = feeStrategy.calculate(ticket);
        ticket.getSpot().release();
        return new Payment(fee);
    }
    
    private ParkingSpot findAvailableSpot(Vehicle vehicle) {
        return floors.stream()
            .flatMap(f -> f.getSpots().stream())
            .filter(s -> s.canFit(vehicle))
            .findFirst()
            .orElse(null);
    }
}
```

---

## 2. Library Management System

### Requirements
- Add/remove books, manage members
- Search books (by title, author, ISBN)
- Issue and return books
- Fine calculation for late returns
- Book reservation
- Member limits (max 5 books)

### Key Entities
- Book, BookCopy, Member, Librarian, Loan, Reservation, Fine

### Key Design
```java
public class Book {
    private String isbn;
    private String title;
    private String author;
    private List<BookCopy> copies;
    
    public boolean hasAvailableCopy() {
        return copies.stream().anyMatch(BookCopy::isAvailable);
    }
}

public class BookCopy {
    private String copyId;
    private Book book;
    private CopyStatus status; // AVAILABLE, ISSUED, RESERVED, LOST
}

public class Member {
    private String memberId;
    private List<Loan> activeLoans;
    private static final int MAX_BOOKS = 5;
    
    public boolean canBorrow() {
        return activeLoans.size() < MAX_BOOKS;
    }
}

public class Loan {
    private BookCopy copy;
    private Member member;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    
    public boolean isOverdue() {
        return LocalDate.now().isAfter(dueDate) && returnDate == null;
    }
    
    public double calculateFine() {
        if (!isOverdue()) return 0;
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        return daysLate * FINE_PER_DAY;
    }
}

// Service layer
public class LibraryService {
    public Loan issueBook(Member member, BookCopy copy) {
        if (!member.canBorrow()) throw new MaxBooksExceededException();
        if (!copy.isAvailable()) throw new BookNotAvailableException();
        
        copy.setStatus(CopyStatus.ISSUED);
        Loan loan = new Loan(copy, member, LocalDate.now(), LocalDate.now().plusDays(14));
        member.getActiveLoans().add(loan);
        return loan;
    }
    
    public double returnBook(Loan loan) {
        loan.setReturnDate(LocalDate.now());
        loan.getCopy().setStatus(CopyStatus.AVAILABLE);
        loan.getMember().getActiveLoans().remove(loan);
        return loan.calculateFine();
    }
}
```

---

## 3. Tic-Tac-Toe

### Requirements
- 2 players take turns
- 3x3 board (extensible to NxN)
- Detect win (row, column, diagonal)
- Detect draw
- Input validation (occupied cell)

### Key Design
```java
public class Board {
    private final int size;
    private final char[][] grid;
    private int movesCount;
    
    public Board(int size) {
        this.size = size;
        this.grid = new char[size][size];
    }
    
    public boolean makeMove(int row, int col, char symbol) {
        if (row < 0 || row >= size || col < 0 || col >= size) return false;
        if (grid[row][col] != '\0') return false;
        
        grid[row][col] = symbol;
        movesCount++;
        return true;
    }
    
    public boolean isFull() { return movesCount == size * size; }
}

// Win strategy (Open/Closed — can add new win conditions)
public interface WinStrategy {
    boolean checkWin(Board board, int row, int col, char symbol);
}

public class StandardWinStrategy implements WinStrategy {
    public boolean checkWin(Board board, int row, int col, char symbol) {
        return checkRow(board, row, symbol) 
            || checkCol(board, col, symbol)
            || checkDiagonal(board, symbol)
            || checkAntiDiagonal(board, symbol);
    }
}

public class Game {
    private final Board board;
    private final Player[] players;
    private final WinStrategy winStrategy;
    private int currentPlayerIndex;
    private GameStatus status;
    
    public GameResult play(int row, int col) {
        Player current = players[currentPlayerIndex];
        
        if (!board.makeMove(row, col, current.getSymbol())) {
            return GameResult.INVALID_MOVE;
        }
        
        if (winStrategy.checkWin(board, row, col, current.getSymbol())) {
            status = GameStatus.WON;
            return GameResult.win(current);
        }
        
        if (board.isFull()) {
            status = GameStatus.DRAW;
            return GameResult.DRAW;
        }
        
        currentPlayerIndex = (currentPlayerIndex + 1) % players.length;
        return GameResult.CONTINUE;
    }
}
```

---

## 4. ATM Machine

### Requirements
- Authenticate user (card + PIN)
- Check balance
- Withdraw cash (multiple denominations)
- Deposit cash
- Transfer funds
- Daily withdrawal limit

### Key Design — Chain of Responsibility for Dispensing
```java
// Cash dispenser using Chain of Responsibility
public abstract class CashDispenser {
    private CashDispenser next;
    protected int denomination;
    protected int count;
    
    public void setNext(CashDispenser next) { this.next = next; }
    
    public void dispense(int amount) {
        if (amount >= denomination && count > 0) {
            int notesNeeded = Math.min(amount / denomination, count);
            count -= notesNeeded;
            int remaining = amount - (notesNeeded * denomination);
            System.out.println(notesNeeded + " x $" + denomination);
            
            if (remaining > 0 && next != null) {
                next.dispense(remaining);
            }
        } else if (next != null) {
            next.dispense(amount);
        }
    }
}

// Chain: $100 → $50 → $20 → $10
CashDispenser chain = new HundredDispenser(100); // 100 notes of $100
chain.setNext(new FiftyDispenser(200));
chain.setNext(new TwentyDispenser(500));
chain.setNext(new TenDispenser(1000));

// State pattern for ATM states
public interface ATMState {
    void insertCard(ATM atm);
    void enterPin(ATM atm, String pin);
    void selectTransaction(ATM atm, TransactionType type);
    void dispense(ATM atm, int amount);
    void ejectCard(ATM atm);
}

public class IdleState implements ATMState { /* waiting for card */ }
public class AuthenticatingState implements ATMState { /* validating PIN */ }
public class TransactionState implements ATMState { /* processing */ }
public class DispensingState implements ATMState { /* dispensing cash */ }
```

---

## 5. Vending Machine

### Requirements
- Display products with prices
- Accept coins/notes
- Select product
- Dispense product and change
- Handle: insufficient funds, out of stock, exact change only

### Key Design — State Pattern
```java
public interface VendingState {
    void insertMoney(VendingMachine vm, double amount);
    void selectProduct(VendingMachine vm, String productCode);
    void dispense(VendingMachine vm);
    void cancel(VendingMachine vm);
}

public class IdleState implements VendingState {
    public void insertMoney(VendingMachine vm, double amount) {
        vm.addBalance(amount);
        vm.setState(new HasMoneyState());
    }
    public void selectProduct(VendingMachine vm, String code) {
        throw new IllegalStateException("Insert money first");
    }
    // ...
}

public class HasMoneyState implements VendingState {
    public void selectProduct(VendingMachine vm, String code) {
        Product product = vm.getProduct(code);
        if (product == null || product.getQuantity() == 0) {
            throw new OutOfStockException();
        }
        if (vm.getBalance() < product.getPrice()) {
            throw new InsufficientFundsException();
        }
        vm.setSelectedProduct(product);
        vm.setState(new DispensingState());
    }
    
    public void cancel(VendingMachine vm) {
        vm.refund();
        vm.setState(new IdleState());
    }
}

public class DispensingState implements VendingState {
    public void dispense(VendingMachine vm) {
        Product product = vm.getSelectedProduct();
        product.decrementQuantity();
        double change = vm.getBalance() - product.getPrice();
        vm.dispenseProduct(product);
        vm.dispenseChange(change);
        vm.resetBalance();
        vm.setState(new IdleState());
    }
}
```

---

## Common Patterns in Beginner LLD

| Problem | Primary Patterns |
|---------|-----------------|
| Parking Lot | Strategy (pricing), Factory (vehicles), Singleton |
| Library | Observer (notifications), State (book status) |
| Tic-Tac-Toe | Strategy (win check), Factory (player creation) |
| ATM | State (machine states), Chain of Responsibility (dispensing) |
| Vending Machine | State (machine states) |

---

## Interview Questions

**Q: How do you handle concurrency in the Parking Lot?**
> 1. Synchronize the `parkVehicle()` method (simple but low throughput)
> 2. Use `ReentrantLock` per floor (finer granularity)
> 3. Use `AtomicReference` for spot status (lock-free for status check)
> 4. Use database-level locking for distributed parking lots
> Key: Multiple threads may try to park in same spot simultaneously

**Q: How would you extend Tic-Tac-Toe to support NxN board and K-in-a-row?**
> Parameterize board size and win length. WinStrategy becomes configurable. Check k consecutive in row/col/diagonal from last move position (O(k) per check instead of O(n) for entire board). Can also support >2 players by cycling through player array.

**Q: How do you handle "exact change only" in Vending Machine?**
> Track available change in the machine. Before accepting selection: check if machine can make change for (inserted - price). If not, display "exact change only" and restrict selections to matching amounts. Use greedy algorithm for change-making (largest denomination first).

---

## Common Mistakes
- Not identifying the right state transitions (State pattern)
- Putting all logic in one class (violating SRP)
- Not handling concurrent access
- Missing edge cases (what if parking lot is full? what if book already issued?)
- Over-engineering simple problems

---

## Best Practices
- Start with entities and relationships before jumping to code
- Apply patterns only where they add value
- Handle edge cases explicitly
- Make the design extensible (new vehicle types, new products)
- Use enums for fixed sets of values
- Keep classes small and focused

---

## Related Topics
- LLD Problems — Intermediate
- LLD Problems — Advanced
- Design Patterns (Creational, Structural, Behavioral)
- SOLID Principles
