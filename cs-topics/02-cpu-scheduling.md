# CPU Scheduling

## Overview
- CPU scheduling decides which process in the ready queue gets the CPU next
- Goal: Maximize CPU utilization, throughput, and fairness

## Scheduling Criteria
| Criteria | Goal |
|----------|------|
| CPU Utilization | Keep CPU as busy as possible |
| Throughput | Max processes completed per unit time |
| Turnaround Time | Minimize total time from submission to completion |
| Waiting Time | Minimize time spent in ready queue |
| Response Time | Minimize time from request to first response |

## Preemptive vs Non-Preemptive

| Type | Description | Example |
|------|-------------|---------|
| Non-Preemptive | Process runs until it voluntarily releases CPU | FCFS, SJF (non-preemptive) |
| Preemptive | OS can forcibly take CPU from a running process | Round Robin, SRTF, Priority (preemptive) |

---

## FCFS (First Come First Served)

### How it Works
- Processes are executed in the order they arrive
- Non-preemptive
- Uses a simple FIFO queue

### Example
| Process | Arrival | Burst |
|---------|---------|-------|
| P1 | 0 | 24 |
| P2 | 0 | 3 |
| P3 | 0 | 3 |

**Gantt Chart**: P1(0-24) → P2(24-27) → P3(27-30)
**Average Waiting Time**: (0 + 24 + 27) / 3 = 17

### Pros & Cons
- ✅ Simple to implement
- ❌ Convoy effect: Short processes wait behind long ones
- ❌ High average waiting time

---

## SJF (Shortest Job First)

### How it Works
- Select process with shortest burst time next
- Can be preemptive (SRTF) or non-preemptive

### Non-Preemptive SJF
| Process | Arrival | Burst |
|---------|---------|-------|
| P1 | 0 | 7 |
| P2 | 2 | 4 |
| P3 | 4 | 1 |
| P4 | 5 | 4 |

**Gantt**: P1(0-7) → P3(7-8) → P2(8-12) → P4(12-16)

### SRTF (Shortest Remaining Time First) - Preemptive SJF
- At each arrival, compare remaining time of current process with new process
- Switch if new process has shorter remaining time

### Pros & Cons
- ✅ Optimal average waiting time (provably)
- ❌ Starvation of long processes
- ❌ Requires knowing burst time in advance (prediction needed)

---

## Round Robin

### How it Works
- Each process gets a fixed time quantum (q)
- After quantum expires, process is preempted and moved to end of ready queue
- Circular rotation through all ready processes

### Example (Time Quantum = 4)
| Process | Burst |
|---------|-------|
| P1 | 24 |
| P2 | 3 |
| P3 | 3 |

**Gantt**: P1(0-4) → P2(4-7) → P3(7-10) → P1(10-14) → P1(14-18) → P1(18-22) → P1(22-26) → P1(26-30)

### Time Quantum Selection
- **Too small**: Too many context switches (overhead)
- **Too large**: Degenerates to FCFS
- **Rule of thumb**: 80% of bursts should be shorter than quantum
- Typical values: 10-100 milliseconds

### Pros & Cons
- ✅ Fair - every process gets CPU time
- ✅ Good response time
- ❌ Higher average turnaround time than SJF
- ❌ Performance depends on quantum size

---

## Priority Scheduling

### How it Works
- Each process is assigned a priority number
- CPU allocated to highest priority process
- Can be preemptive or non-preemptive
- Lower number = higher priority (typically)

### Types
- **Static Priority**: Assigned at creation, doesn't change
- **Dynamic Priority**: Changes based on aging, CPU usage, etc.

### Problem: Starvation
- Low-priority processes may never execute
- **Solution: Aging** - gradually increase priority of waiting processes

### Example
| Process | Burst | Priority |
|---------|-------|----------|
| P1 | 10 | 3 |
| P2 | 1 | 1 |
| P3 | 2 | 4 |
| P4 | 1 | 5 |
| P5 | 5 | 2 |

**Execution Order**: P2 → P5 → P1 → P3 → P4

---

## Multilevel Queue Scheduling

### Concept
- Ready queue is divided into multiple queues
- Each queue has its own scheduling algorithm
- Scheduling between queues (fixed priority or time slice)

### Example Setup
```
[System Processes]     → Priority scheduling
[Interactive Processes] → Round Robin
[Batch Processes]      → FCFS
```

### Multilevel Feedback Queue
- Processes can move between queues
- New processes start in highest priority queue
- If they use full quantum, they move to lower queue
- Aging moves processes back up

---

## Comparison Table

| Algorithm | Preemptive | Starvation | Convoy Effect | Optimal |
|-----------|-----------|------------|---------------|---------|
| FCFS | No | No | Yes | No |
| SJF | No | Yes | No | Yes (non-preemptive) |
| SRTF | Yes | Yes | No | Yes (preemptive) |
| Round Robin | Yes | No | No | No |
| Priority | Both | Yes | No | No |

---

## Key Interview Questions

**Q: Which scheduling algorithm is used in real operating systems?**
> Most modern OS use a combination - typically multilevel feedback queue. Linux uses CFS (Completely Fair Scheduler). Windows uses priority-based preemptive scheduling.

**Q: What's the convoy effect?**
> When short processes are stuck waiting behind a long process in FCFS, like small cars stuck behind a slow truck in single-lane traffic.

**Q: How does the OS predict burst time for SJF?**
> Using exponential averaging: τ(n+1) = α * t(n) + (1-α) * τ(n), where t(n) is actual burst and τ(n) is predicted.

**Q: What happens if time quantum in Round Robin is very large?**
> It becomes FCFS. If very small, too much context switching overhead.
