# HLD Interview Problems — Intermediate

## 1. Food Delivery System (Zomato/DoorDash)

### Requirements
**Functional:**
- Browse restaurants and menus
- Place orders from restaurants
- Real-time order tracking
- Delivery partner assignment and routing
- Payment processing
- Ratings and reviews

**Non-functional:**
- Low latency for restaurant search (<200ms)
- Real-time location tracking (delivery partner)
- High availability (especially during peak hours)
- Scale: 10M daily active users, 1M orders/day

### Scale Estimation
```
Orders: 1M/day ≈ 12 orders/sec (average), 50 orders/sec (peak)
Active delivery partners: 100K
Location updates: 100K × every 5 sec = 20K updates/sec
Restaurant search: 10M DAU × 5 searches/day = 50M searches/day ≈ 600 reads/sec
```

### High-Level Architecture
```
┌───────┐    ┌──────────┐    ┌─────────────────────────────────────────┐
│Mobile │───→│ API GW + │    │                Services                  │
│ App   │    │   LB     │───→├──────────────┬──────────────┬───────────┤
└───────┘    └──────────┘    │Restaurant Svc│  Order Svc   │Payment Svc│
                             │              │              │           │
                             │ Search Svc   │ Delivery Svc │Notification│
                             │(Elasticsearch)│(Location)    │   Svc     │
                             └──────────────┴──────┬───────┴───────────┘
                                                   │
                             ┌──────────────────────┼───────────────────┐
                             │  Kafka (Events)      │  Redis (Cache/Loc)│
                             └──────────────────────┴───────────────────┘
```

### Key Design Decisions

**Restaurant Search:**
- Geospatial index (PostGIS, Elasticsearch geo_point)
- Search by: location (radius), cuisine, rating, delivery time
- Cache popular searches per area

**Order Flow:**
```
Customer places order
  → Order Service creates order (PENDING)
  → Payment Service charges (PAID)
  → Restaurant receives order (CONFIRMED)
  → Restaurant prepares (PREPARING)
  → Delivery matching algorithm assigns partner (ASSIGNED)
  → Partner picks up (PICKED_UP)
  → Partner delivers (DELIVERED)
```

**Delivery Partner Matching:**
- Find available partners within radius of restaurant
- Consider: distance, current direction, rating, load
- Real-time location stored in Redis (GeoHash)
- `GEOSEARCH partners FROMMEMBER restaurant_123 BYRADIUS 3 km`

**Real-Time Tracking:**
- Delivery partner sends location every 5 seconds
- WebSocket connection from customer app to server
- Location stored in Redis, pushed to customer via WebSocket

**ETA Estimation:**
- Prep time (restaurant average) + Travel time (maps API)
- ML model for better predictions based on historical data

---

## 2. Ride Booking System (Uber/Lyft)

### Requirements
**Functional:**
- Request a ride (pickup, destination)
- Match with nearby driver
- Real-time tracking
- Fare estimation and surge pricing
- Payment processing
- Rating system

**Non-functional:**
- Matching latency < 30 seconds
- Location accuracy and real-time updates
- 99.99% availability
- Scale: 20M rides/day globally

### High-Level Architecture
```
┌───────┐                    ┌──────────────────────────────────┐
│Rider  │───→┐               │          Services                │
│ App   │    │  ┌──────────┐ │ Matching │ Location │ Pricing    │
└───────┘    ├─→│  API GW  │→│ Service  │ Service  │ Service    │
             │  └──────────┘ │          │          │            │
┌───────┐   │               │ Trip Svc │ Payment  │ Notification│
│Driver │───→┘               │          │ Service  │ Service    │
│ App   │                    └──────────┴──────────┴────────────┘
└───────┘                              │
                    ┌──────────────────────────────────────┐
                    │ Redis (Location) │ Kafka │ PostgreSQL │
                    └──────────────────────────────────────┘
```

### Key Design Decisions

**Driver Matching:**
```
1. Rider requests ride
2. Find all available drivers within 5km radius (Redis GEOSEARCH)
3. Score drivers: distance, ETA, rating, acceptance rate
4. Send ride request to top driver
5. If declined/timeout (10s) → next driver
6. If all decline → expand radius, notify rider

Quadtree/S2 Geometry for efficient geo-indexing at scale
```

**Surge Pricing:**
```
supply = available drivers in area
demand = ride requests in area in last 5 min

surge_multiplier = demand / supply

If surge > 1.0 → increase fare
Display estimated fare BEFORE rider confirms

Update every 2 minutes per geo-zone
```

**Real-Time Location:**
- Drivers send GPS every 4 seconds
- Store in Redis with TTL (GEO data structure)
- Push updates to rider via WebSocket

---

## 3. E-Commerce System (Amazon)

### Requirements
**Functional:**
- Product catalog with search
- Shopping cart
- Order placement and payment
- Inventory management
- Order tracking
- Reviews and ratings

**Non-functional:**
- Product search < 200ms
- Handle flash sales (100x normal traffic)
- Never oversell (inventory consistency)
- Scale: 100M products, 10M DAU

### High-Level Architecture
```
┌──────┐    ┌─────┐    ┌───────────────────────────────────────┐
│Client│───→│CDN  │    │             Services                   │
│      │    │+API │───→│ Product │ Cart │ Order │ Payment │ User│
└──────┘    │ GW  │    │ Service │ Svc  │ Svc   │ Svc     │ Svc│
            └─────┘    └─────────┴──────┴───────┴─────────┴────┘
                              │         │         │
                        ┌─────┴───┐ ┌───┴──┐ ┌───┴──────────┐
                        │Elastic  │ │Redis │ │PostgreSQL    │
                        │Search   │ │(Cart)│ │(Orders,Users)│
                        └─────────┘ └──────┘ └──────────────┘
```

### Key Design Decisions

**Inventory Management (Prevent Overselling):**
```
Approach: Optimistic locking + reservation

1. User adds to cart → No inventory lock
2. User proceeds to checkout → Reserve inventory (Redis decrement)
3. Payment succeeds → Confirm reservation (DB update)
4. Payment fails / timeout → Release reservation

Redis:
  DECR inventory:product:123  (atomic decrement)
  If result < 0 → INCR back, return "Out of stock"
  
Reservation TTL: 10 minutes (auto-release if abandoned)
```

**Flash Sales:**
```
Challenges: 100x normal traffic, inventory for limited items

Solution:
1. Pre-warm caches and auto-scale before sale
2. Queue-based: Put requests in queue, process sequentially
3. Token-based: Issue limited tokens, only token holders can purchase
4. Rate limiting: Limit requests per user
5. CDN for static pages, API throttling
```

**Shopping Cart:**
- Logged-in users: Redis (fast, survives sessions)
- Guest users: Local storage / cookies
- Cart merge on login

---

## 4. Payment System

### Requirements
**Functional:**
- Process payments (credit card, wallet, UPI)
- Refund processing
- Transaction history
- Retry failed payments
- Webhook notifications to merchants

**Non-functional:**
- Strong consistency (never double-charge)
- Exactly-once processing
- PCI DSS compliance
- Audit trail for every transaction
- 99.99% availability

### High-Level Architecture
```
┌──────────┐    ┌──────────────┐    ┌─────────────────────┐
│ Merchant │───→│Payment Gateway│───→│  Payment Service    │
│  Service │    │ (API)        │    │  (Orchestrator)     │
└──────────┘    └──────────────┘    └──────┬──────────────┘
                                           │
                    ┌──────────────────────────────────────┐
                    │              │           │            │
                    ▼              ▼           ▼            ▼
            ┌────────────┐ ┌──────────┐ ┌─────────┐ ┌──────────┐
            │ Risk/Fraud │ │  Ledger  │ │ Payment │ │  Wallet  │
            │  Engine    │ │ Service  │ │ Provider│ │  Service │
            └────────────┘ └──────────┘ │(Stripe) │ └──────────┘
                                        └─────────┘
```

### Key Design Decisions

**Idempotency (Prevent Double Charge):**
```
Every payment request has a unique idempotency_key

Client → POST /payments { idempotency_key: "uuid-123", amount: 99.99 }

Server:
1. Check Redis: EXISTS idempotency:uuid-123
2. If exists → return cached response (don't process again)
3. If not → process payment, store result with key

This handles: network retries, client retries, duplicate webhooks
```

**Double-Entry Ledger:**
```
Every transaction creates TWO entries:
  Debit:  Customer account → -$99.99
  Credit: Merchant account → +$99.99

Ensures: Total debits = Total credits (always balanced)
Used for: Reconciliation, auditing, dispute resolution
```

**Payment State Machine:**
```
INITIATED → PROCESSING → SUCCEEDED
                ↓              ↓
            FAILED        REFUND_INITIATED → REFUNDED
                ↓
            RETRY → PROCESSING
```

---

## 5. Ticket Booking System (BookMyShow)

### Requirements
**Functional:**
- Browse events/movies with available shows
- View seat map and select seats
- Book seats with payment
- Handle concurrent booking (same seat)

**Non-functional:**
- No double booking (strong consistency for seat selection)
- Handle burst traffic (popular event release)
- Booking flow < 5 minutes (timeout)

### Key Design — Seat Locking
```
Problem: Two users select same seat simultaneously

Solution: Temporary seat lock with TTL

1. User selects seat → SETNX seat:show123:A5 userId TTL=300s (5 min)
2. If lock acquired → proceed to payment
3. If lock exists → seat unavailable, pick another
4. Payment succeeds → mark seat as BOOKED in DB
5. Payment fails/timeout → lock auto-expires, seat available again

Alternative: Optimistic locking with DB version
  UPDATE seats SET status='BOOKED', version=version+1 
  WHERE seat_id='A5' AND status='AVAILABLE' AND version=5
  If rows_affected = 0 → someone else booked it
```

---

## 6. Chat Application (Slack/Teams)

### Requirements
**Functional:**
- One-to-one messaging
- Group channels
- Online/offline status
- Read receipts
- File sharing
- Message history

**Non-functional:**
- Real-time delivery (<100ms for online users)
- Message ordering within conversation
- Scale: 1M concurrent connections, 10B messages/day

### High-Level Architecture
```
┌──────┐                    ┌─────────────────────────────────┐
│Client│←──WebSocket──────→│    WebSocket Gateway            │
└──────┘                    │  (Connection Manager)           │
                            └────────────┬────────────────────┘
                                         │
                            ┌────────────▼────────────────────┐
                            │          Kafka                   │
                            │  (Message routing & ordering)    │
                            └────────────┬────────────────────┘
                                         │
              ┌──────────────────────────────────────────────┐
              │          │            │           │           │
              ▼          ▼            ▼           ▼           ▼
        ┌──────────┐┌──────────┐┌──────────┐┌─────────┐┌─────────┐
        │Message   ││Presence  ││Group     ││Push     ││File    │
        │Service   ││Service   ││Service   ││Service  ││Service │
        └──────────┘└──────────┘└──────────┘└─────────┘└─────────┘
```

### Key Design Decisions

**Message Delivery:**
```
User A sends message to User B:

1. A → WebSocket Gateway → Kafka (topic: user_B_inbox)
2. If B is online:
   - WebSocket Gateway for B's server consumes from Kafka
   - Push message via WebSocket to B
3. If B is offline:
   - Message stored in DB (unread)
   - Push notification sent
   - When B connects: fetch unread messages from DB
```

**Message Ordering:**
- Kafka partition by conversation_id → ordered within conversation
- Snowflake ID for message ordering (timestamp-based, sortable)
- Client-side ordering by message timestamp

**Presence (Online/Offline):**
- Heartbeat every 30 seconds via WebSocket
- If no heartbeat for 60s → mark offline
- Store in Redis: `SETEX presence:user123 60 "online"`
- Broadcast status changes to user's contacts

**Group Messaging:**
- Fan-out on write: Send to each member's inbox (small groups)
- Fan-out on read: Store once, each reader fetches (large groups/channels)

---

## 7. Order Management System

### Requirements
**Functional:**
- Create, update, cancel orders
- Order state management (placed → paid → shipped → delivered)
- Inventory reservation on order
- Refund on cancellation
- Order history

**Non-functional:**
- Strong consistency for inventory and payment
- Eventual consistency acceptable for notifications
- Audit trail for all state changes
- Scale: 100K orders/day

### Key Design — Saga Pattern for Order Flow
```
Orchestrator Saga:

1. Order Service: Create order (PENDING)
2. Inventory Service: Reserve items
   ↳ Failure → Cancel order
3. Payment Service: Charge customer
   ↳ Failure → Release inventory, cancel order
4. Notification Service: Send confirmation
5. Order Service: Update status (CONFIRMED)

Compensating actions on failure:
  Payment fails → release inventory → cancel order → notify customer
```

---

## Interview Tips for Intermediate Problems

### Key Discussion Points
| System | Focus Areas |
|--------|-------------|
| Food Delivery | Geo-indexing, real-time tracking, matching algorithm |
| Ride Booking | Driver matching, surge pricing, location updates |
| E-Commerce | Inventory consistency, flash sales, search |
| Payment | Idempotency, double-entry ledger, reconciliation |
| Ticket Booking | Seat locking, concurrency, timeout handling |
| Chat | WebSocket scaling, message ordering, presence |
| Order Management | Saga pattern, state machine, compensation |

---

## Common Mistakes
- Not addressing concurrency (double booking, overselling)
- Ignoring real-time requirements (polling vs push)
- Not discussing failure scenarios and compensation
- Using synchronous calls for everything (blocking, cascade failures)
- Forgetting about data consistency in distributed transactions

---

## Best Practices
- Use event-driven architecture for loose coupling
- Implement idempotency for all state-changing operations
- Design for failure: retries, DLQ, circuit breakers
- Use appropriate consistency model per use case
- Consider geo-distribution for global systems
- Plan for burst traffic with queuing and rate limiting

---

## Related Topics
- HLD Interview Problems — Advanced
- Saga Pattern
- Event-Driven Architecture
- Distributed Transactions
- Real-Time Systems (WebSocket)
