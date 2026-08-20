# Deadlock

## What is Deadlock?
- A situation where two or more processes are blocked forever, each waiting for a resource held by another
- No process can proceed because each is waiting for the other to release a resource

### Classic Example
```
Thread A: holds Lock1, waiting for Lock2
Thread B: holds Lock2, waiting for Lock1

→ Both wait forever = DEADLOCK
```

---

## Four Necessary Conditions (Coffman Conditions)

ALL four must hold simultaneously for deadlock to occur:

| # | Condition | Description |
|---|-----------|-------------|
| 1 | **Mutual Exclusion** | At least one resource is non-shareable (only one process can use it) |
| 2 | **Hold and Wait** | A process holds at least one resource and is waiting for others |
| 3 | **No Preemption** | Resources cannot be forcibly taken from a process |
| 4 | **Circular Wait** | A circular chain of processes, each waiting for resource held by next |

### Memory Aid: "MHNC"
- **M**utual exclusion
- **H**old and wait
- **N**o preemption
- **C**ircular wait

---

## Deadlock Prevention

**Strategy**: Break at least ONE of the four necessary conditions

### 1. Break Mutual Exclusion
- Make resources shareable (e.g., read-only files)
- Not always possible (printers, write locks)

### 2. Break Hold and Wait
- **Option A**: Request ALL resources at once before starting
  - Problem: Low resource utilization, starvation possible
- **Option B**: Release all resources before requesting new ones
  - Problem: Not practical for many applications

### 3. Break No Preemption
- If a process can't get a resource, forcibly release its held resources
- Works for resources whose state can be saved (CPU registers, memory)
- Doesn't work for printers, locks

### 4. Break Circular Wait
- **Impose ordering**: Number all resources, processes must request in increasing order
- Most practical prevention method

```
Resources: R1=1, R2=2, R3=3
Rule: Always request lower-numbered resource first

Thread A: lock(R1), then lock(R2)  ✓
Thread B: lock(R1), then lock(R2)  ✓ (cannot lock R2 then R1)
```

---

## Deadlock Avoidance

**Strategy**: Make dynamic decisions to avoid unsafe states

### Safe State
- A state where there exists at least one sequence in which all processes can finish
- If system is in safe state → no deadlock
- Unsafe state ≠ deadlock, but deadlock is possible

### Banker's Algorithm
- Named after a banker who never allocates cash such that they can't satisfy all customers
- Used for multiple instances of resources

#### Data Structures
```
n = number of processes
m = number of resource types

Available[m]     - Available instances of each resource
Max[n][m]        - Maximum demand of each process
Allocation[n][m] - Currently allocated to each process
Need[n][m]       - Remaining need (Max - Allocation)
```

#### Safety Algorithm
```
1. Let Work = Available, Finish[i] = false for all i
2. Find process Pi where:
   - Finish[i] == false
   - Need[i] <= Work
3. If found:
   - Work = Work + Allocation[i]
   - Finish[i] = true
   - Go to step 2
4. If all Finish[i] == true → SAFE STATE
```

#### Resource Request Algorithm
```
1. If Request[i] <= Need[i], go to step 2 (else error)
2. If Request[i] <= Available, go to step 3 (else wait)
3. Pretend to allocate:
   Available -= Request[i]
   Allocation[i] += Request[i]
   Need[i] -= Request[i]
4. Run Safety Algorithm
   - If safe: grant request
   - If unsafe: rollback, process must wait
```

---

## Deadlock Detection

**Strategy**: Allow deadlock to occur, detect it, then recover

### Single Instance Resources - Wait-For Graph
- Remove resource nodes from Resource Allocation Graph
- If cycle exists in Wait-For Graph → deadlock exists

```
Wait-For Graph:
P1 → P2 (P1 is waiting for P2)
P2 → P3
P3 → P1  ← CYCLE = DEADLOCK
```

### Multiple Instance Resources - Detection Algorithm
- Similar to Banker's algorithm but uses current allocation
- Periodically run to check for deadlock

### When to Run Detection?
- Every time a resource request is denied
- Periodically (e.g., every 5 minutes)
- When CPU utilization drops below threshold

---

## Deadlock Recovery

Once detected, options:

### 1. Process Termination
- **Abort all deadlocked processes**: Simple but expensive
- **Abort one at a time**: Until deadlock cycle is broken
  - Choose victim based on: priority, resources held, time executed, resources needed

### 2. Resource Preemption
- Forcibly take resources from some processes
- **Selecting victim**: Minimize cost
- **Rollback**: Roll back process to safe state
- **Starvation**: Ensure same process isn't always victim (include rollback count in cost)

---

## Deadlock in Practice

### Database Deadlocks
```sql
-- Transaction 1:
UPDATE accounts SET balance = balance - 100 WHERE id = 1;  -- locks row 1
UPDATE accounts SET balance = balance + 100 WHERE id = 2;  -- waits for row 2

-- Transaction 2:
UPDATE accounts SET balance = balance - 50 WHERE id = 2;   -- locks row 2
UPDATE accounts SET balance = balance + 50 WHERE id = 1;   -- waits for row 1
```
**Solution**: Always access tables/rows in the same order

### Java Deadlock Example
```java
Object lock1 = new Object();
Object lock2 = new Object();

// Thread 1
synchronized(lock1) {
    synchronized(lock2) { /* work */ }
}

// Thread 2
synchronized(lock2) {    // ← DEADLOCK RISK
    synchronized(lock1) { /* work */ }
}

// FIX: Always acquire locks in same order
// Thread 2 (fixed)
synchronized(lock1) {
    synchronized(lock2) { /* work */ }
}
```

---

## Starvation vs Livelock vs Deadlock

| Condition | Description | Progress? |
|-----------|-------------|-----------|
| **Deadlock** | Processes blocked forever, each waiting for other | No |
| **Starvation** | Process never gets resources due to scheduling | Others progress, victim doesn't |
| **Livelock** | Processes keep changing state in response to each other but make no progress | Appears active, no real progress |

### Starvation
- A process is indefinitely denied resources
- Cause: Unfair scheduling (e.g., priority scheduling without aging)
- Solution: Aging, fair scheduling

### Livelock
- Like two people in a hallway trying to pass each other
- Both keep moving aside, but neither passes
- Solution: Random backoff (like Ethernet collision handling)

---

## Key Interview Questions

**Q: Can deadlock occur with a single process?**
> Yes, if a non-reentrant lock is acquired twice by the same process (self-deadlock).

**Q: What's the difference between deadlock prevention and avoidance?**
> Prevention: Static rules that break one of the four conditions. Avoidance: Dynamic decisions using algorithms like Banker's to stay in safe states.

**Q: How do databases handle deadlocks?**
> Detection + recovery. They maintain a wait-for graph, detect cycles, and abort one transaction (the victim) to break the cycle.

**Q: Which deadlock condition is easiest to break?**
> Circular wait - by imposing a total ordering on resource acquisition. This is the most practical prevention technique in real systems.

**Q: In real systems, which approach is most common?**
> Most systems use the "ostrich approach" (ignore deadlocks) combined with detection and recovery when they occur. Full prevention is too restrictive for most applications.
