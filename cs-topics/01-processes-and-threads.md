# Processes and Threads

## Process

### What is a Process?
- A process is an instance of a program in execution
- It has its own memory space (code, data, heap, stack)
- Each process has a unique Process ID (PID)
- Processes are isolated from each other

### Process States
```
New → Ready → Running → Terminated
              ↑    ↓
              ← Waiting
```

| State | Description |
|-------|-------------|
| New | Process is being created |
| Ready | Process is waiting to be assigned to CPU |
| Running | Instructions are being executed |
| Waiting | Process is waiting for some event (I/O) |
| Terminated | Process has finished execution |

### Process Control Block (PCB)
- Process ID
- Process state
- Program counter
- CPU registers
- Memory management info
- I/O status
- Scheduling information

---

## Thread

### What is a Thread?
- A thread is the smallest unit of execution within a process
- Also called a "lightweight process"
- Threads within the same process share:
  - Code section
  - Data section
  - Heap memory
  - Open files and signals
- Each thread has its own:
  - Thread ID
  - Program counter
  - Register set
  - Stack

### Types of Threads
| Type | Description |
|------|-------------|
| User-level threads | Managed by user-level library, kernel unaware |
| Kernel-level threads | Managed directly by OS kernel |

### Thread Models
- **Many-to-One**: Many user threads → one kernel thread
- **One-to-One**: Each user thread → one kernel thread (Linux, Windows)
- **Many-to-Many**: Many user threads → many kernel threads

---

## Process vs Thread

| Aspect | Process | Thread |
|--------|---------|--------|
| Memory | Separate address space | Shared address space |
| Creation | Heavy (fork) | Lightweight |
| Communication | IPC needed (pipes, sockets) | Shared memory directly |
| Context switch | Expensive | Cheaper |
| Isolation | High (crash doesn't affect others) | Low (crash can kill all threads) |
| Overhead | More resources | Less resources |

### When to Use What?
- **Process**: When isolation is critical (e.g., browser tabs in Chrome)
- **Thread**: When you need shared state and fast communication (e.g., web server handling requests)

---

## User Mode vs Kernel Mode

### User Mode
- Restricted access to hardware
- Cannot execute privileged instructions
- Application code runs here
- If a crash occurs, only the process is affected

### Kernel Mode
- Full access to hardware and all instructions
- OS kernel runs in this mode
- Can access any memory address
- A crash here can bring down the entire system

### Mode Switching
```
User Mode Application
        ↓ (system call)
    Trap/Interrupt
        ↓
Kernel Mode (OS handles request)
        ↓
    Return
        ↓
User Mode Application
```

### System Calls
- Interface between user mode and kernel mode
- Examples: `open()`, `read()`, `write()`, `fork()`, `exec()`

---

## Context Switching

### What is Context Switching?
- Saving the state of one process/thread and loading the state of another
- Enables multitasking on a single CPU

### Steps in Context Switch
1. Save state of current process (registers, PC, stack pointer) into PCB
2. Update PCB status (Running → Ready/Waiting)
3. Select next process (CPU scheduler)
4. Load state of next process from its PCB
5. Update PCB status (Ready → Running)
6. Resume execution

### Cost of Context Switching
- Direct cost: Save/restore registers, flush caches
- Indirect cost: Cache misses (cold cache), TLB flush
- Thread context switch is cheaper than process context switch (no need to switch address space)

### Interview Question: Why is context switching expensive?
> Because it involves saving/restoring CPU state, flushing TLB and caches, and the indirect cost of cache misses after switching. For processes, the virtual memory mappings also need to change.

---

## Key Interview Questions

**Q: Can a process exist without a thread?**
> No. Every process has at least one thread (the main thread).

**Q: What happens when a thread crashes?**
> All threads in that process are terminated because they share the same address space.

**Q: Why does Chrome use multiple processes instead of threads for tabs?**
> For isolation. If one tab crashes, it doesn't bring down other tabs. Also provides security isolation between sites.

**Q: What's the difference between concurrency and parallelism?**
> Concurrency: Multiple tasks making progress (may be interleaved on one core). Parallelism: Multiple tasks executing simultaneously (requires multiple cores).
