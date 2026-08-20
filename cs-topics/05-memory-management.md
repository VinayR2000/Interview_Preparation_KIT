# Memory Management

## Memory Hierarchy
```
Registers (fastest, smallest)
    ↓
L1 Cache
    ↓
L2 Cache
    ↓
L3 Cache
    ↓
Main Memory (RAM)
    ↓
Disk/SSD (slowest, largest)
```

---

## Stack vs Heap

| Aspect | Stack | Heap |
|--------|-------|------|
| Allocation | Automatic (compiler) | Manual (programmer) / GC |
| Deallocation | Automatic (scope exit) | Manual `free()` / GC |
| Speed | Very fast (pointer move) | Slower (search for free block) |
| Size | Limited (typically 1-8 MB) | Large (limited by RAM) |
| Growth direction | High → Low address | Low → High address |
| Order | LIFO | No order |
| What's stored | Local variables, function params, return addresses | Objects, dynamic arrays, global data |
| Thread safety | Each thread has own stack | Shared across threads |
| Fragmentation | No | Yes |

### Stack Overflow
- When stack grows beyond its limit
- Common cause: Deep/infinite recursion
- Fixed size per thread (typically 1-8 MB)

### Heap Overflow
- When heap exhausts available memory
- `malloc()` returns NULL / `new` throws OutOfMemoryError

---

## Memory Allocation Strategies

### Contiguous Allocation
Each process gets a single contiguous block of memory

#### Strategies for Choosing a Free Block
| Strategy | Description | Pros | Cons |
|----------|-------------|------|------|
| **First Fit** | First hole big enough | Fast | External fragmentation |
| **Best Fit** | Smallest hole big enough | Least waste | Slow, small leftover fragments |
| **Worst Fit** | Largest hole | Large leftover (usable) | Slow, wasteful |

---

## Fragmentation

### External Fragmentation
- Total free memory is enough, but not contiguous
- Occurs with variable-size allocation
- Solution: Compaction (move processes to create contiguous free space)

```
Before: [P1][  ][P2][  ][P3][  ]  ← 3 small holes, can't fit P4
After:  [P1][P2][P3][           ]  ← compacted, P4 fits
```

### Internal Fragmentation
- Allocated block is larger than needed
- Wasted space is inside the allocated block
- Occurs with fixed-size allocation (paging)

```
Process needs 3001 bytes, page size = 4096 bytes
Allocated: 4096 bytes
Wasted (internal fragmentation): 1095 bytes
```

---

## Virtual Memory

### What is Virtual Memory?
- Technique that provides illusion of large contiguous memory
- Allows execution of processes not completely in memory
- Maps virtual addresses to physical addresses

### Benefits
1. Process isolation (each has own address space)
2. Programs larger than physical memory can run
3. Efficient memory sharing between processes
4. Simplified memory allocation for programs

### Address Translation
```
CPU → Virtual Address → MMU → Physical Address → Main Memory
                        ↓
                    Page Table
```

---

## Paging

### Concept
- Divide physical memory into fixed-size frames
- Divide logical memory into same-size pages
- Map pages to frames using a page table
- Eliminates external fragmentation

### Page Table
```
Virtual Address = [Page Number | Offset]
Physical Address = [Frame Number | Offset]

Page Table Entry:
| Frame Number | Valid Bit | Protection Bits | Dirty Bit | Reference Bit |
```

### Page Table Structures
| Structure | Description |
|-----------|-------------|
| Single-level | Simple array, large for 64-bit |
| Multi-level | Hierarchical (like a tree) |
| Inverted | One entry per physical frame |
| Hashed | Hash table for lookup |

### TLB (Translation Lookaside Buffer)
- Hardware cache for page table entries
- Speeds up address translation
- TLB hit: No memory access for translation
- TLB miss: Must access page table in memory

```
TLB Hit Ratio = 99% (typical)
Effective Access Time = hit_ratio × (TLB_time + memory_time) + 
                       miss_ratio × (TLB_time + 2 × memory_time)
```

---

## Page Fault

### What is a Page Fault?
- Occurs when a process accesses a page not currently in physical memory
- The page is on disk and must be loaded

### Page Fault Handling Steps
1. Process accesses a page
2. MMU checks page table → valid bit = 0 (not in memory)
3. **Page fault trap** to OS
4. OS finds page on disk
5. OS finds a free frame (or evicts using page replacement)
6. OS loads page from disk to frame
7. Update page table entry (frame number, valid bit = 1)
8. Restart the instruction that caused the fault

### Page Fault Rate
- If p = page fault rate (0 ≤ p ≤ 1)
- Effective Access Time = (1-p) × memory_access + p × page_fault_time
- Page fault time ≈ 10ms (disk I/O), memory access ≈ 100ns
- Even 0.1% page fault rate dramatically increases EAT

---

## Page Replacement Algorithms

When a page fault occurs and no free frames exist, choose a victim page to evict.

### FIFO (First In First Out)
- Replace the oldest page in memory
- Simple but suffers from **Belady's Anomaly** (more frames can cause more faults)

### Optimal (OPT)
- Replace page that won't be used for the longest time
- Not implementable (requires future knowledge)
- Used as benchmark

### LRU (Least Recently Used)
- Replace the page not used for the longest time
- Approximates OPT by looking at past
- Implementation: Counter or stack

### LRU Approximations
- **Clock Algorithm (Second Chance)**:
  - Each page has a reference bit
  - On replacement: check bit
    - If 1: set to 0, move to next (give second chance)
    - If 0: replace this page

### Comparison
| Algorithm | Belady's Anomaly | Optimal | Practical |
|-----------|-----------------|---------|-----------|
| FIFO | Yes | No | Yes |
| OPT | No | Yes | No (future knowledge) |
| LRU | No | Near-optimal | Expensive to implement exactly |
| Clock | No | Good approximation | Yes (used in practice) |

---

## Segmentation

### What is Segmentation?
- Divide memory into variable-size segments based on logical divisions
- Each segment: code, data, stack, heap, etc.
- User's view of memory

### Segment Table
```
Logical Address = [Segment Number | Offset]

Segment Table Entry:
| Base (start address) | Limit (length) |

If offset > limit → Segmentation Fault
Physical Address = Base + Offset
```

### Paging vs Segmentation
| Aspect | Paging | Segmentation |
|--------|--------|--------------|
| Size | Fixed (pages) | Variable (segments) |
| Fragmentation | Internal | External |
| View | Physical division | Logical division |
| User visible | No | Yes |
| Sharing | Harder | Natural (share a segment) |

### Segmentation with Paging
- Modern systems combine both
- Segment → Pages → Frames
- Used in x86 architecture (historically)

---

## Thrashing

### What is Thrashing?
- When a system spends more time swapping pages than executing processes
- CPU utilization drops dramatically
- Occurs when processes don't have enough frames

### Cause
```
More processes loaded → Less frames per process → More page faults
→ More I/O → CPU idle → OS loads more processes → Even less frames
→ THRASHING
```

### Detection
- High page fault rate
- Low CPU utilization despite many processes in memory
- Excessive disk I/O

### Solutions
1. **Working Set Model**: Give each process enough frames for its working set
2. **Page Fault Frequency**: If fault rate too high → give more frames; too low → take frames away
3. **Reduce degree of multiprogramming**: Swap out some processes
4. **Increase RAM**: Hardware solution

### Working Set
- Set of pages referenced in the last Δ time units
- If we allocate enough frames for the working set, page faults are minimal
- WSS(i) = working set size of process i
- If Σ WSS(i) > total frames → thrashing will occur

---

## Key Interview Questions

**Q: What's the difference between paging and swapping?**
> Paging moves individual pages between disk and memory. Swapping moves an entire process between disk and memory. Paging is finer-grained.

**Q: Why is the TLB important?**
> Without TLB, every memory access requires two memory accesses (one for page table, one for data). TLB caches translations, making most accesses single-memory-access.

**Q: What causes a segmentation fault?**
> Accessing memory outside your allocated segment (offset > limit) or accessing a page marked as invalid/no-permission.

**Q: How does copy-on-write work?**
> After fork(), parent and child share the same physical pages (marked read-only). Only when one writes does the OS copy that specific page. Saves memory and time for processes that mostly read.

**Q: What's the difference between logical and physical address?**
> Logical (virtual) address is what the process sees. Physical address is the actual location in RAM. MMU translates between them using page tables.
