# Synchronization

## The Critical Section Problem

### What is a Critical Section?
- A section of code that accesses shared resources
- Only one process/thread should execute in its critical section at a time

### Requirements for a Solution
1. **Mutual Exclusion**: Only one process in critical section at a time
2. **Progress**: If no process is in CS, selection of next process cannot be postponed indefinitely
3. **Bounded Waiting**: There must be a limit on how many times other processes can enter CS after a process has requested

---

## Race Condition

### What is a Race Condition?
- When multiple processes/threads access shared data concurrently
- The outcome depends on the order of execution (non-deterministic)
- Leads to incorrect results

### Classic Example
```
// Shared variable: counter = 5

Thread A: counter++       Thread B: counter--
  LOAD counter (5)          LOAD counter (5)
  ADD 1 (6)                 SUB 1 (4)
  STORE counter (6)         STORE counter (4)

// Expected: 5, Actual: 4 or 6 (race condition!)
```

### How to Prevent
- Use synchronization mechanisms (mutex, semaphore, monitor)
- Atomic operations
- Lock-free data structures

---

## Mutex (Mutual Exclusion)

### What is a Mutex?
- A locking mechanism that ensures only ONE thread can access a resource at a time
- Binary: locked or unlocked
- Has ownership - only the thread that locked it can unlock it

### Operations
```
mutex.lock()       // Acquire lock (blocks if already locked)
// Critical Section
mutex.unlock()     // Release lock
```

### Properties
- Ownership: Only owner can unlock
- Non-recursive (typically): Same thread locking twice = deadlock
- Binary: Only two states

### Spinlock vs Mutex
| Spinlock | Mutex |
|----------|-------|
| Busy-waits (loops checking) | Blocks (goes to sleep) |
| Good for short critical sections | Good for long critical sections |
| Wastes CPU cycles | No CPU waste while waiting |
| Used in kernel code | Used in user-space |

---

## Semaphore

### What is a Semaphore?
- A signaling mechanism with an integer counter
- No ownership concept - any thread can signal
- Can allow multiple threads to access resource

### Types
| Type | Value | Use Case |
|------|-------|----------|
| Binary Semaphore | 0 or 1 | Similar to mutex (but no ownership) |
| Counting Semaphore | 0 to N | Limit concurrent access to N |

### Operations
```
wait(S) / P(S) / down(S):    // Decrement
    while (S <= 0) block;
    S--;

signal(S) / V(S) / up(S):   // Increment
    S++;
```

### Semaphore vs Mutex
| Mutex | Semaphore |
|-------|-----------|
| Ownership (only owner unlocks) | No ownership (anyone can signal) |
| Binary only | Can be counting |
| For mutual exclusion | For signaling/resource counting |
| Locking mechanism | Signaling mechanism |

### Classic Problems Using Semaphores

#### Producer-Consumer
```
semaphore empty = N;   // empty slots
semaphore full = 0;    // filled slots
semaphore mutex = 1;   // mutual exclusion

Producer:
    wait(empty);
    wait(mutex);
    // produce item, add to buffer
    signal(mutex);
    signal(full);

Consumer:
    wait(full);
    wait(mutex);
    // remove item from buffer
    signal(mutex);
    signal(empty);
```

#### Readers-Writers
```
semaphore rw_mutex = 1;   // exclusive access for writers
semaphore mutex = 1;      // protect read_count
int read_count = 0;

Writer:
    wait(rw_mutex);
    // write
    signal(rw_mutex);

Reader:
    wait(mutex);
    read_count++;
    if (read_count == 1) wait(rw_mutex);  // first reader blocks writers
    signal(mutex);
    // read
    wait(mutex);
    read_count--;
    if (read_count == 0) signal(rw_mutex); // last reader unblocks writers
    signal(mutex);
```

---

## Monitor

### What is a Monitor?
- A high-level synchronization construct
- Encapsulates shared data + operations + synchronization
- Only one thread can be active inside a monitor at a time
- Built into languages (Java `synchronized`, C# `lock`)

### Components
1. **Shared data**: Variables accessible only through monitor procedures
2. **Procedures**: Methods that operate on shared data
3. **Condition variables**: For waiting/signaling within monitor

### Condition Variables
```
condition x;

x.wait()   // Release monitor lock and suspend thread
x.signal() // Wake up one waiting thread
```

### Java Example
```java
class BoundedBuffer {
    private Queue<Integer> buffer = new LinkedList<>();
    private int capacity;

    public synchronized void produce(int item) throws InterruptedException {
        while (buffer.size() == capacity) {
            wait();  // release lock, wait
        }
        buffer.add(item);
        notifyAll();  // wake up consumers
    }

    public synchronized int consume() throws InterruptedException {
        while (buffer.isEmpty()) {
            wait();
        }
        int item = buffer.poll();
        notifyAll();  // wake up producers
        return item;
    }
}
```

---

## Comparison Summary

| Mechanism | Level | Ownership | Count | Use Case |
|-----------|-------|-----------|-------|----------|
| Mutex | Low | Yes | Binary | Exclusive access |
| Semaphore | Low | No | 0-N | Resource counting, signaling |
| Monitor | High | N/A (built-in) | N/A | Structured synchronization |
| Spinlock | Low | Yes | Binary | Short critical sections (kernel) |

---

## Key Interview Questions

**Q: Can a deadlock occur with a single mutex?**
> Yes, if a non-recursive mutex is locked twice by the same thread (self-deadlock).

**Q: What's the difference between mutex and binary semaphore?**
> Mutex has ownership (only locker can unlock), binary semaphore doesn't. Mutex is for mutual exclusion, semaphore is for signaling.

**Q: What is priority inversion?**
> When a high-priority thread is blocked waiting for a lock held by a low-priority thread, which is preempted by medium-priority threads. Solution: Priority inheritance.

**Q: Why is `notify()` preferred over `notifyAll()` in Java?**
> `notify()` is more efficient (wakes one thread), but `notifyAll()` is safer when multiple conditions exist. Use `notifyAll()` to avoid missed signals.
