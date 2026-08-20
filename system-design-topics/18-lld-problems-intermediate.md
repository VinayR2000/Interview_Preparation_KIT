# LLD Interview Problems — Intermediate

## 1. Elevator System

### Requirements
- Multiple elevators in a building
- Floors with up/down buttons
- Inside panel with floor buttons
- Efficient scheduling (minimize wait time)
- Handle multiple concurrent requests

### Key Design Decisions
- **Strategy Pattern**: Elevator scheduling algorithm
- **State Pattern**: Elevator states (IDLE, MOVING_UP, MOVING_DOWN, DOOR_OPEN)
- **Observer Pattern**: Notify elevator when new request arrives

### Code
```java
public enum Direction { UP, DOWN, IDLE }
public enum ElevatorState { IDLE, MOVING_UP, MOVING_DOWN, DOOR_OPEN }

public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private ElevatorState state;
    private final TreeSet<Integer> upRequests = new TreeSet<>();    // Sorted ascending
    private final TreeSet<Integer> downRequests = new TreeSet<>(Collections.reverseOrder()); // Sorted descending
    
    public void addRequest(int floor) {
        if (floor > currentFloor) upRequests.add(floor);
        else if (floor < currentFloor) downRequests.add(floor);
    }
    
    public void move() {
        if (direction == Direction.UP) {
            if (!upRequests.isEmpty()) {
                currentFloor = upRequests.pollFirst(); // Next floor going up
                openDoor();
            } else {
                direction = Direction.DOWN; // Switch direction
            }
        } else if (direction == Direction.DOWN) {
            if (!downRequests.isEmpty()) {
                currentFloor = downRequests.pollFirst();
                openDoor();
            } else {
                direction = Direction.IDLE;
            }
        }
    }
}

// Scheduling Strategy
public interface ElevatorScheduler {
    Elevator selectElevator(List<Elevator> elevators, int requestFloor, Direction direction);
}

// LOOK Algorithm (elevator scan)
public class LookScheduler implements ElevatorScheduler {
    public Elevator selectElevator(List<Elevator> elevators, int requestFloor, Direction dir) {
        return elevators.stream()
            .min(Comparator.comparingInt(e -> calculateCost(e, requestFloor, dir)))
            .orElseThrow();
    }
    
    private int calculateCost(Elevator elevator, int targetFloor, Direction dir) {
        // Prefer: same direction AND on the way
        if (elevator.getDirection() == dir) {
            if (dir == Direction.UP && elevator.getCurrentFloor() <= targetFloor) {
                return targetFloor - elevator.getCurrentFloor(); // Direct cost
            }
            if (dir == Direction.DOWN && elevator.getCurrentFloor() >= targetFloor) {
                return elevator.getCurrentFloor() - targetFloor;
            }
        }
        // Otherwise, add penalty for direction change
        return Math.abs(elevator.getCurrentFloor() - targetFloor) + 1000;
    }
}

// Controller
public class ElevatorController {
    private final List<Elevator> elevators;
    private final ElevatorScheduler scheduler;
    
    public void requestFromFloor(int floor, Direction direction) {
        Elevator best = scheduler.selectElevator(elevators, floor, direction);
        best.addRequest(floor);
    }
    
    public void requestFromInside(int elevatorId, int floor) {
        elevators.get(elevatorId).addRequest(floor);
    }
}
```

---

## 2. Car Rental System

### Requirements
- Browse available vehicles by type, location, dates
- Reserve a vehicle for specific dates
- Pick up and return vehicles
- Calculate rental cost (daily rate + insurance + extras)
- Handle damage/late fees

### Key Entities
- Vehicle (Car, SUV, Van), Location, Reservation, RentalAgreement, Customer, Payment

### Code
```java
public abstract class Vehicle {
    private String vehicleId;
    private String model;
    private VehicleType type;
    private VehicleStatus status;
    private Location currentLocation;
    private double dailyRate;
}

public class Reservation {
    private String reservationId;
    private Customer customer;
    private Vehicle vehicle;
    private Location pickupLocation;
    private Location dropoffLocation;
    private LocalDate startDate;
    private LocalDate endDate;
    private ReservationStatus status;
    
    public long getDays() {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
}

// Pricing with Decorator pattern (add-ons)
public interface RentalPricing {
    double calculate(Reservation reservation);
}

public class BasePricing implements RentalPricing {
    public double calculate(Reservation reservation) {
        return reservation.getVehicle().getDailyRate() * reservation.getDays();
    }
}

public class InsurancePricing implements RentalPricing {
    private final RentalPricing wrapped;
    private final double dailyInsurance;
    
    public double calculate(Reservation reservation) {
        return wrapped.calculate(reservation) + (dailyInsurance * reservation.getDays());
    }
}

public class GPSAddon implements RentalPricing {
    private final RentalPricing wrapped;
    
    public double calculate(Reservation reservation) {
        return wrapped.calculate(reservation) + 50.0; // Flat GPS fee
    }
}

// Usage: Stack add-ons
RentalPricing pricing = new GPSAddon(new InsurancePricing(new BasePricing(), 15.0));
double total = pricing.calculate(reservation);
```

---

## 3. Movie Ticket Booking (BookMyShow)

### Requirements
- Browse movies by city, theater
- View available shows and seat map
- Select seats and book
- Handle concurrent seat selection
- Payment processing
- Booking confirmation/cancellation

### Key Design — Concurrency for Seat Booking
```java
public class Show {
    private final String showId;
    private final Movie movie;
    private final Theater theater;
    private final LocalDateTime showTime;
    private final Map<String, Seat> seats; // seatId → Seat
    private final ReentrantLock lock = new ReentrantLock();
    
    public BookingResult bookSeats(Customer customer, List<String> seatIds) {
        lock.lock();
        try {
            // Validate all seats are available
            for (String seatId : seatIds) {
                Seat seat = seats.get(seatId);
                if (seat == null || seat.getStatus() != SeatStatus.AVAILABLE) {
                    return BookingResult.failure("Seat " + seatId + " unavailable");
                }
            }
            
            // Temporarily lock seats
            seatIds.forEach(id -> seats.get(id).setStatus(SeatStatus.LOCKED));
            
            // Create booking with expiry
            Booking booking = new Booking(customer, seatIds, Duration.ofMinutes(10));
            return BookingResult.success(booking);
        } finally {
            lock.unlock();
        }
    }
    
    public void confirmBooking(Booking booking) {
        booking.getSeatIds().forEach(id -> seats.get(id).setStatus(SeatStatus.BOOKED));
        booking.setStatus(BookingStatus.CONFIRMED);
    }
    
    public void releaseExpiredBookings() {
        // Scheduled task: release seats for expired/unpaid bookings
    }
}
```

---

## 4. Hotel Booking System

### Requirements
- Search rooms by date range, type, amenities
- Reserve rooms
- Check-in / check-out
- Dynamic pricing (weekday/weekend, season, demand)
- Handle overbooking strategy

### Key Design — Availability and Pricing
```java
public class Room {
    private String roomId;
    private RoomType type; // STANDARD, DELUXE, SUITE
    private int floor;
    private List<Amenity> amenities;
    private double basePrice;
}

public class RoomInventory {
    // Date → Available rooms of each type
    private final Map<LocalDate, Map<RoomType, List<Room>>> availability;
    
    public List<Room> getAvailable(LocalDate checkIn, LocalDate checkOut, RoomType type) {
        // Room must be available for ALL nights
        return availability.entrySet().stream()
            .filter(e -> !e.getKey().isBefore(checkIn) && e.getKey().isBefore(checkOut))
            .map(e -> e.getValue().getOrDefault(type, List.of()))
            .reduce((a, b) -> {
                Set<Room> intersection = new HashSet<>(a);
                intersection.retainAll(b);
                return new ArrayList<>(intersection);
            })
            .orElse(List.of());
    }
}

// Dynamic Pricing (Strategy)
public interface PricingStrategy {
    double calculateRate(Room room, LocalDate date);
}

public class DynamicPricingStrategy implements PricingStrategy {
    public double calculateRate(Room room, LocalDate date) {
        double rate = room.getBasePrice();
        
        // Weekend surcharge
        if (date.getDayOfWeek() == SATURDAY || date.getDayOfWeek() == SUNDAY) {
            rate *= 1.3;
        }
        
        // Demand-based (occupancy rate)
        double occupancy = getOccupancyRate(date);
        if (occupancy > 0.8) rate *= 1.5;      // High demand
        else if (occupancy < 0.3) rate *= 0.8;  // Low demand
        
        return rate;
    }
}
```

---

## 5. Restaurant Management System

### Requirements
- Table management (assign, release)
- Menu with categories
- Order management (place, update, status)
- Bill generation (with taxes, tips, splits)
- Kitchen display (order queue)

### Key Design
```java
public class Table {
    private int tableNumber;
    private int capacity;
    private TableStatus status; // AVAILABLE, OCCUPIED, RESERVED
    private List<Order> activeOrders;
}

public class Order {
    private String orderId;
    private Table table;
    private List<OrderItem> items;
    private OrderStatus status;
    private LocalDateTime createdAt;
    
    public double getSubtotal() {
        return items.stream().mapToDouble(OrderItem::getTotal).sum();
    }
}

// Bill calculation with Decorator
public interface BillCalculator {
    double calculate(Order order);
}

public class BaseBill implements BillCalculator {
    public double calculate(Order order) { return order.getSubtotal(); }
}

public class TaxBill implements BillCalculator {
    private final BillCalculator wrapped;
    private final double taxRate;
    
    public double calculate(Order order) {
        return wrapped.calculate(order) * (1 + taxRate);
    }
}

public class TipBill implements BillCalculator {
    private final BillCalculator wrapped;
    private final double tipPercent;
    
    public double calculate(Order order) {
        double base = wrapped.calculate(order);
        return base + (order.getSubtotal() * tipPercent); // Tip on subtotal only
    }
}

// Kitchen uses Observer pattern
public class KitchenDisplay implements OrderObserver {
    private final Queue<Order> pendingOrders = new PriorityQueue<>(
        Comparator.comparing(Order::getCreatedAt)
    );
    
    @Override
    public void onNewOrder(Order order) {
        pendingOrders.add(order);
        displayUpdate();
    }
}
```

---

## 6. Splitwise

### Requirements
- Create groups
- Add expenses (split equally, by percentage, exact amounts)
- Track balances between users
- Simplify debts (minimize transactions)
- Send notifications/reminders

### Key Design
```java
// Split strategies
public interface SplitStrategy {
    Map<User, Double> calculateSplits(double amount, List<User> participants, 
                                       Map<User, Double> customSplits);
}

public class EqualSplit implements SplitStrategy {
    public Map<User, Double> calculateSplits(double amount, List<User> participants,
                                              Map<User, Double> customSplits) {
        double perPerson = amount / participants.size();
        return participants.stream().collect(toMap(u -> u, u -> perPerson));
    }
}

public class PercentageSplit implements SplitStrategy {
    public Map<User, Double> calculateSplits(double amount, List<User> participants,
                                              Map<User, Double> percentages) {
        // Validate percentages sum to 100
        return percentages.entrySet().stream()
            .collect(toMap(Map.Entry::getKey, e -> amount * e.getValue() / 100));
    }
}

// Balance tracking
public class BalanceSheet {
    // balances[i][j] = amount user i owes to user j
    private final Map<String, Map<String, Double>> balances = new HashMap<>();
    
    public void addExpense(User payer, Map<User, Double> splits) {
        for (Map.Entry<User, Double> entry : splits.entrySet()) {
            User debtor = entry.getKey();
            double amount = entry.getValue();
            
            if (!debtor.equals(payer)) {
                // Debtor owes payer
                addBalance(debtor.getId(), payer.getId(), amount);
            }
        }
    }
    
    private void addBalance(String fromId, String toId, double amount) {
        balances.computeIfAbsent(fromId, k -> new HashMap<>())
                .merge(toId, amount, Double::sum);
    }
    
    // Simplify debts: minimize number of transactions
    public List<Transaction> simplifyDebts() {
        // Calculate net balance for each user
        Map<String, Double> netBalance = new HashMap<>();
        // ... Calculate net amounts
        
        // Greedy: Match largest creditor with largest debtor
        List<Transaction> simplified = new ArrayList<>();
        PriorityQueue<Map.Entry<String, Double>> creditors = new PriorityQueue<>(/*...*/);
        PriorityQueue<Map.Entry<String, Double>> debtors = new PriorityQueue<>(/*...*/);
        
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            // Match and create minimal transactions
        }
        return simplified;
    }
}
```

---

## Common Patterns in Intermediate LLD

| Problem | Primary Patterns |
|---------|-----------------|
| Elevator | Strategy (scheduling), State (elevator state), Observer |
| Car Rental | Decorator (pricing add-ons), Factory (vehicles) |
| Movie Booking | Lock/concurrency handling, Observer (notifications) |
| Hotel Booking | Strategy (pricing), Builder (reservation) |
| Restaurant | Observer (kitchen), Decorator (billing), Command (orders) |
| Splitwise | Strategy (split types), Observer (notifications) |

---

## Interview Questions

**Q: How do you handle the situation where two users try to book the same seat?**
> Optimistic locking: Use version column. `UPDATE seats SET status='BOOKED', version=v+1 WHERE id=? AND version=v`. If rows_affected=0, someone else booked it. Alternatively: temporary lock with TTL (10 min for payment), after which seats auto-release. Display "locked by another user" in real-time via WebSocket.

**Q: How would you scale the Elevator system for a 100-floor building with 50 elevators?**
> Zone-based: Divide into zones (1-25, 26-50, 51-75, 76-100). Each zone served by dedicated elevators. Express elevators skip floors. Separate freight elevators. Destination dispatch system (select floor before entering elevator → system groups efficiently).

**Q: How do you simplify debts in Splitwise efficiently?**
> 1. Calculate net balance for each user (total owed - total owing)
> 2. Users with positive net = creditors, negative = debtors
> 3. Greedy matching: Pair largest debtor with largest creditor
> 4. This is NP-hard for truly minimal transactions, but greedy gives good results
> 5. Alternative: Settle through a central "bank" user (everyone pays/receives from one entity)

---

## Common Mistakes
- Not handling concurrent access (two users booking same resource)
- Ignoring time-based constraints (reservation expiry, seat lock timeout)
- Not considering the simplification of complex algorithms (elevator scheduling)
- Missing observer notifications (kitchen display, user alerts)
- Not separating pricing logic from core domain (hardcoding prices)

---

## Best Practices
- Use locks/synchronization for shared mutable state
- Implement timeout/expiry for temporary locks
- Strategy pattern for any logic that might have multiple implementations
- Observer for any event that multiple parties care about
- Decorator for incremental feature additions (pricing, bill calculation)
- Command for operations that need undo/history

---

## Related Topics
- LLD Problems — Advanced
- Concurrency in Java
- Design Patterns
- Database design for these systems
