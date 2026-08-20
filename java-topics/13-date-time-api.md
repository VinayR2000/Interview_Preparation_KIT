# 13. Date and Time API (Java 8+)

---

## Theory

Java 8 introduced `java.time` package (JSR-310) to replace the broken `java.util.Date` and `Calendar` APIs. The new API is:

- **Immutable** — all classes are immutable and thread-safe
- **Clear** — separates date, time, datetime, and timezone concepts
- **Fluent** — method chaining with descriptive names
- **ISO-8601** — follows international standards by default

### Old vs New

| Old (Broken) | New (Java 8+) | Why Old Was Bad |
|--------------|---------------|-----------------|
| `java.util.Date` | `LocalDateTime`, `Instant` | Mutable, confusing API |
| `java.util.Calendar` | `LocalDate`, `LocalTime` | Month starts at 0, not thread-safe |
| `SimpleDateFormat` | `DateTimeFormatter` | Not thread-safe |
| `java.sql.Date/Time` | `LocalDate`, `LocalTime` | Confusing inheritance |

---

## Internal Working

### Class Hierarchy

```
java.time Package:

Temporal (interface)
├── LocalDate        → date only (2024-03-15)
├── LocalTime        → time only (14:30:00)
├── LocalDateTime    → date + time (2024-03-15T14:30:00)
├── ZonedDateTime    → date + time + timezone
├── OffsetDateTime   → date + time + offset (+05:30)
├── Instant          → machine timestamp (epoch seconds + nanos)
├── Year             → just year
├── YearMonth        → year + month
└── MonthDay         → month + day

Duration  → time-based amount (hours, minutes, seconds)
Period    → date-based amount (years, months, days)

ZoneId       → timezone identifier (Asia/Kolkata)
ZoneOffset   → fixed offset from UTC (+05:30)

DateTimeFormatter → formatting/parsing
```

### Immutability

```java
// Every "modification" returns a NEW object
LocalDate today = LocalDate.now();         // 2024-03-15
LocalDate tomorrow = today.plusDays(1);    // 2024-03-16
// today is STILL 2024-03-15 — unchanged!
```

---

## Diagram

```
Choosing the Right Class:

Need just a date?          → LocalDate (birthday, holiday)
Need just a time?          → LocalTime (alarm, opening hours)
Need date + time?          → LocalDateTime (meeting, event)
Need timezone awareness?   → ZonedDateTime (global events, flights)
Need machine timestamp?    → Instant (logs, audit, DB timestamps)
Need duration in time?     → Duration (timeout, elapsed time)
Need duration in dates?    → Period (age, subscription length)

Timeline Visualization:

Instant (UTC epoch):
|────────────────|────────────────|────────────────|
0            1970-01-01       2024-03-15        future
             epoch             now

ZonedDateTime:
┌──────────────────────────────────────────────────┐
│  2024-03-15T14:30:00+05:30[Asia/Kolkata]         │
│  ├── LocalDate: 2024-03-15                       │
│  ├── LocalTime: 14:30:00                         │
│  ├── ZoneOffset: +05:30                          │
│  └── ZoneId: Asia/Kolkata                        │
└──────────────────────────────────────────────────┘
```

---

## Code Examples

### LocalDate

```java
// Creating
LocalDate today = LocalDate.now();
LocalDate specific = LocalDate.of(2024, 3, 15);
LocalDate parsed = LocalDate.parse("2024-03-15");

// Operations
LocalDate tomorrow = today.plusDays(1);
LocalDate lastMonth = today.minusMonths(1);
LocalDate nextYear = today.plusYears(1);
LocalDate withDay = today.withDayOfMonth(1);  // first of current month

// Querying
int year = today.getYear();
Month month = today.getMonth();
int day = today.getDayOfMonth();
DayOfWeek dow = today.getDayOfWeek();
int daysInMonth = today.lengthOfMonth();
boolean isLeap = today.isLeapYear();

// Comparing
boolean isBefore = today.isBefore(tomorrow);
boolean isAfter = today.isAfter(yesterday);
long daysBetween = ChronoUnit.DAYS.between(start, end);
```

### LocalTime

```java
LocalTime now = LocalTime.now();
LocalTime specific = LocalTime.of(14, 30, 0);
LocalTime parsed = LocalTime.parse("14:30:00");

LocalTime later = now.plusHours(2).plusMinutes(30);
int hour = now.getHour();
int minute = now.getMinute();

// Truncation
LocalTime truncated = now.truncatedTo(ChronoUnit.MINUTES);  // removes seconds
```

### LocalDateTime

```java
LocalDateTime now = LocalDateTime.now();
LocalDateTime specific = LocalDateTime.of(2024, 3, 15, 14, 30, 0);
LocalDateTime combined = LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0));

// Parsing
LocalDateTime parsed = LocalDateTime.parse("2024-03-15T14:30:00");

// Convert to/from LocalDate and LocalTime
LocalDate date = now.toLocalDate();
LocalTime time = now.toLocalTime();
LocalDateTime fromDate = date.atTime(14, 30);
LocalDateTime fromTime = time.atDate(LocalDate.now());
```

### ZonedDateTime and Timezones

```java
// Creating
ZonedDateTime nowIndia = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
ZonedDateTime nowUTC = ZonedDateTime.now(ZoneOffset.UTC);
ZonedDateTime specific = ZonedDateTime.of(
    LocalDateTime.of(2024, 3, 15, 14, 30),
    ZoneId.of("America/New_York")
);

// Converting between zones
ZonedDateTime indiaTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
ZonedDateTime nyTime = indiaTime.withZoneSameInstant(ZoneId.of("America/New_York"));
// Same moment, different local time

// All available zones
Set<String> zones = ZoneId.getAvailableZoneIds();

// Important: DST handling is automatic
ZonedDateTime beforeDST = ZonedDateTime.of(
    LocalDateTime.of(2024, 3, 10, 1, 30), ZoneId.of("America/New_York")
);
ZonedDateTime afterDST = beforeDST.plusHours(1);  // skips to 3:30 AM
```

### Instant — Machine Timestamp

```java
Instant now = Instant.now();  // current UTC timestamp
Instant epoch = Instant.EPOCH;  // 1970-01-01T00:00:00Z
Instant fromEpoch = Instant.ofEpochSecond(1710000000L);
Instant fromMillis = Instant.ofEpochMilli(System.currentTimeMillis());

// Convert to/from ZonedDateTime
ZonedDateTime zdt = now.atZone(ZoneId.of("Asia/Kolkata"));
Instant back = zdt.toInstant();

// Duration between instants
long seconds = Duration.between(start, end).getSeconds();
```

### Duration and Period

```java
// Duration — time-based (hours, minutes, seconds, nanos)
Duration twoHours = Duration.ofHours(2);
Duration between = Duration.between(startTime, endTime);
long minutes = between.toMinutes();
long seconds = between.getSeconds();

// Period — date-based (years, months, days)
Period sixMonths = Period.ofMonths(6);
Period age = Period.between(birthDate, LocalDate.now());
int years = age.getYears();
int months = age.getMonths();

// Using with dates
LocalDate expiry = LocalDate.now().plus(Period.ofMonths(6));
LocalTime timeout = LocalTime.now().plus(Duration.ofMinutes(30));
```

### DateTimeFormatter

```java
// Predefined formatters
String iso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);  // 2024-03-15

// Custom patterns
DateTimeFormatter custom = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
String formatted = LocalDateTime.now().format(custom);  // 15/03/2024 14:30:00

// Parsing with custom formatter
LocalDate parsed = LocalDate.parse("15/03/2024", 
    DateTimeFormatter.ofPattern("dd/MM/yyyy"));

// Locale-aware
DateTimeFormatter localized = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    .withLocale(Locale.US);
String usFormat = LocalDate.now().format(localized);  // March 15, 2024

// Thread-safe! (unlike SimpleDateFormat)
// Can be stored as static final constant
public static final DateTimeFormatter FORMATTER = 
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
```

---

## Dry Run

### Timezone Conversion

```java
// Meeting at 2:30 PM India time — what time in New York?
ZonedDateTime indiaTime = ZonedDateTime.of(
    LocalDateTime.of(2024, 3, 15, 14, 30),
    ZoneId.of("Asia/Kolkata")  // UTC+5:30
);

ZonedDateTime nyTime = indiaTime.withZoneSameInstant(ZoneId.of("America/New_York"));
// India: 2024-03-15T14:30+05:30[Asia/Kolkata]
// NY offset in March (EDT): UTC-4
// Calculation: 14:30 - 5:30 = 09:00 UTC → 09:00 - 4:00 = 05:00 NY
// NY: 2024-03-15T05:00-04:00[America/New_York]
```

### Age Calculation

```java
LocalDate birthDate = LocalDate.of(1995, 6, 15);
LocalDate today = LocalDate.of(2024, 3, 15);

Period age = Period.between(birthDate, today);
// years: 2024-1995 = 29, but month 3 < 6, so: 28
// months: 3-6 = -3, adjust: 12-3 = 9
// days: 15-15 = 0
// Result: 28 years, 9 months, 0 days
```

---

## Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| `now()` | O(1) | System clock call |
| `plus/minus` | O(1) | Creates new immutable object |
| `between()` | O(1) | Arithmetic on fields |
| `parse()` | O(n) | n = string length |
| `format()` | O(n) | n = output length |
| Zone conversion | O(1) | Timezone rule lookup (cached) |

---

## Real Project Usage

### Audit Timestamp

```java
public class AuditLog {
    private Instant createdAt;     // machine timestamp — timezone independent
    private Instant updatedAt;
    
    public void markCreated() {
        this.createdAt = Instant.now();
    }
    
    // Display in user's timezone
    public String getCreatedAtFormatted(ZoneId userZone) {
        return createdAt.atZone(userZone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }
}
```

### Scheduling

```java
public class SchedulerService {
    
    public ZonedDateTime getNextExecutionTime(String cron, ZoneId timezone) {
        // Business hours check
        ZonedDateTime next = ZonedDateTime.now(timezone);
        while (!isBusinessHour(next)) {
            next = next.plusMinutes(15);
        }
        return next;
    }
    
    private boolean isBusinessHour(ZonedDateTime time) {
        return time.getDayOfWeek().getValue() <= 5  // Mon-Fri
            && time.getHour() >= 9
            && time.getHour() < 17;
    }
}
```

### REST API with Date Parameters

```java
@GetMapping("/transactions")
public List<Transaction> getTransactions(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    
    return transactionService.findBetween(
        from.atStartOfDay(),
        to.atTime(LocalTime.MAX)
    );
}
```

---

## Interview Questions and Answers

### Q1: Why was the new Date/Time API introduced?

**A:** The old `java.util.Date` and `Calendar` had serious problems:
1. **Mutable** — not thread-safe, leads to bugs
2. **Poor design** — months 0-indexed, year offset from 1900
3. **No separation** — no way to represent just a date or just a time
4. **No timezone support** — `Date` is always UTC internally but `toString()` uses system timezone
5. **`SimpleDateFormat` not thread-safe** — common source of production bugs

### Q2: What is the difference between `LocalDateTime` and `ZonedDateTime`?

**A:**
- `LocalDateTime` — date + time WITHOUT timezone. Represents a wall-clock reading. Same value regardless of where you are.
- `ZonedDateTime` — date + time WITH timezone. Represents a specific moment in time.

```java
// LocalDateTime: "March 15, 2:30 PM" — could be anywhere
LocalDateTime meeting = LocalDateTime.of(2024, 3, 15, 14, 30);

// ZonedDateTime: "March 15, 2:30 PM in India" — specific moment
ZonedDateTime indianMeeting = meeting.atZone(ZoneId.of("Asia/Kolkata"));
```

**Rule:** Use `Instant` or `ZonedDateTime` for timestamps. Use `LocalDateTime` only for events without timezone context (birthdays, business hours).

### Q3: What is the difference between `Duration` and `Period`?

**A:**
- `Duration` — **time-based**: hours, minutes, seconds, nanoseconds. For measuring elapsed time.
- `Period` — **date-based**: years, months, days. For measuring calendar-based differences.

```java
Duration d = Duration.ofHours(2);      // 2 hours = 7200 seconds (exact)
Period p = Period.ofMonths(1);          // 1 month (28-31 days — context-dependent!)
```

### Q4: How to store date/time in a database?

**A:**
- Store as `Instant` (UTC timestamp) or `TIMESTAMP WITH TIME ZONE`
- Convert to user's timezone only for display
- Never store as formatted string

```java
// JPA/Hibernate mapping
@Column(name = "created_at")
private Instant createdAt;  // stored as UTC in DB

// Display
public String getDisplayTime(ZoneId userZone) {
    return createdAt.atZone(userZone).format(formatter);
}
```

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Using `LocalDateTime` for timestamps | No timezone — ambiguous | Use `Instant` or `ZonedDateTime` |
| `SimpleDateFormat` in multithreaded code | Not thread-safe | Use `DateTimeFormatter` (thread-safe) |
| Storing local time in DB for global app | Timezone confusion | Store as UTC `Instant` |
| Ignoring DST transitions | Time gaps/overlaps | Use `ZonedDateTime` which handles DST |
| `Period.between` for exact time | Period doesn't count hours | Use `Duration` or `ChronoUnit` |
| Parsing without formatter | Fails for non-ISO formats | Specify `DateTimeFormatter` |

---

## Best Practices

1. **Store timestamps as `Instant` (UTC)** — convert to local zone only for display
2. **Use `DateTimeFormatter` constants** — thread-safe, reusable
3. **Use `LocalDate` for dates without time** — birthdays, holidays
4. **Use `ZonedDateTime` for scheduled events** — respects DST
5. **Use `Duration`/`Period` for amounts** — not manual arithmetic
6. **Specify timezone explicitly** — never rely on system default: `ZoneId.of("UTC")`
7. **Use `Instant` for logs and audit** — unambiguous point in time

---

## Production Considerations

- **Serialization:** Jackson requires `jackson-datatype-jsr310` module for proper serialization
- **JPA:** Use `@Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")` for Instant
- **Timezone database:** JVM uses IANA timezone database — keep JVM updated for DST rule changes
- **Clock injection:** For testing, inject `Clock` instead of calling `now()` directly
- **Leap seconds:** Java's `Instant` doesn't account for leap seconds (follows UTC-SLS)

---

## Related Topics

- [10. Java 8 Features](./10-java8-features.md) — introduced alongside streams and lambdas
- [29. Serialization](./29-serialization.md) — serializing date/time objects
- [30. JDBC](./30-jdbc.md) — mapping to SQL date/time types
