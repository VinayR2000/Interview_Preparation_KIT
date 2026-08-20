# Linux Storage, Memory, and CPU

## Disk Management ⭐⭐

### df — Filesystem Disk Space

```bash
df -h                           # Human-readable sizes
df -h /                         # Specific filesystem
df -i                           # Inode usage (can run out even with disk space!)
df -T                           # Show filesystem type
```

#### Example Output
```
Filesystem      Size  Used  Avail  Use%  Mounted on
/dev/xvda1      20G   15G   4.2G   78%  /
/dev/xvdf       100G  45G   55G    45%  /data
tmpfs           8.0G  0     8.0G   0%   /dev/shm
```

---

### du — Disk Usage

```bash
du -sh *                        # Size of each item in current directory
du -sh /var/log                 # Total size of directory
du -h --max-depth=1 /           # Top-level directory sizes
du -ah | sort -rh | head -20    # Largest files/dirs
du -sh /var/log/* | sort -rh    # Log directory breakdown
```

### df vs du
| Command | Measures | Use Case |
|---------|----------|----------|
| `df` | Filesystem available space | "Is the disk full?" |
| `du` | Directory/file actual usage | "What's taking up space?" |

---

### lsblk — List Block Devices

```bash
lsblk                           # List all block devices
lsblk -f                        # Show filesystem type
```

#### Example Output
```
NAME    MAJ:MIN  SIZE  TYPE  MOUNTPOINT
xvda      202:0   20G  disk
└─xvda1   202:1   20G  part  /
xvdf      202:80  100G disk  /data
```

---

### Mount / Unmount

```bash
# Mount a device
mount /dev/xvdf /data

# Mount with specific filesystem
mount -t ext4 /dev/xvdf /data

# Mount read-only
mount -ro /dev/xvdf /data

# Unmount
umount /data

# Check mounted filesystems
mount | grep "/data"
cat /proc/mounts
```

### Persistent Mounting (fstab)
```bash
# /etc/fstab — mounts applied on boot
# <device>       <mount point>  <type>  <options>       <dump>  <pass>
/dev/xvdf        /data          ext4    defaults        0       2
```

---

### Disk Full Troubleshooting ⭐⭐⭐

```bash
# 1. Which filesystem is full?
df -h

# 2. What's using the space?
du -sh /* 2>/dev/null | sort -rh | head
du -sh /var/* | sort -rh | head
du -sh /var/log/* | sort -rh | head

# 3. Find largest files
find / -type f -size +100M -exec ls -lh {} \; 2>/dev/null | sort -k5 -rh

# 4. Check for deleted files still held open
lsof | grep '(deleted)' | sort -k7 -rn | head

# 5. Common solutions
# Rotate/compress old logs
find /var/log -name "*.log" -mtime +7 -exec gzip {} \;
# Remove old temp files
find /tmp -mtime +7 -delete
# Truncate large log (keeps file handle open)
> /var/log/myapp/application.log
```

**Important**: Deleted files held open by processes still consume space. Restart the process or truncate instead of deleting.

---

## Memory Management ⭐⭐

### free — Memory Usage

```bash
free -h                         # Human-readable
free -m                         # In megabytes
free -s 5                       # Repeat every 5 seconds
```

#### Output Explained
```
              total    used    free    shared  buff/cache  available
Mem:           16G     10G     1G      256M       5G         5.2G
Swap:           4G     500M    3.5G
```

| Field | Meaning |
|-------|---------|
| total | Total physical RAM |
| used | Memory used by processes |
| free | Completely unused memory |
| shared | Memory used by tmpfs |
| buff/cache | Kernel buffers + page cache (reclaimable) |
| available | Memory available for new processes = free + reclaimable cache |

**Key insight**: Low `free` is normal! Linux uses free memory for caching. Look at `available` instead.

---

### Memory Concepts

```
┌──────────────────────────────────────┐
│              Physical RAM            │
├───────────┬──────────┬──────────────┤
│  Process  │  Buffers │  Page Cache  │
│  Memory   │          │  (disk cache)│
├───────────┴──────────┴──────────────┤
│            Swap Space (disk)         │
└──────────────────────────────────────┘
```

| Concept | Description |
|---------|-------------|
| RAM | Physical memory hardware |
| Virtual Memory | Abstraction: each process thinks it has its own memory space |
| Page Cache | Disk data cached in RAM for speed |
| Buffers | Kernel I/O buffers |
| Swap | Disk space used when RAM is full |
| OOM Killer | Kernel kills processes when memory is exhausted |

---

### vmstat — Virtual Memory Statistics

```bash
vmstat 1 5                      # Every 1 second, 5 times
```

#### Output
```
procs  memory          swap     io       system      cpu
r  b   swpd free   buff  cache  si  so  bi  bo  in  cs  us sy id wa
2  0   512  1024   128   4096   0   0   5   10  100 200 15 5  78 2
```

| Field | Meaning | Warning Sign |
|-------|---------|--------------|
| r | Processes waiting for CPU | > CPU count = overloaded |
| b | Processes in uninterruptible sleep | > 0 = I/O wait |
| swpd | Swap used | Growing = memory pressure |
| si/so | Swap in/out | > 0 = actively swapping (slow!) |
| us | User CPU% | High = application busy |
| sy | System CPU% | High = kernel busy (I/O, syscalls) |
| id | Idle CPU% | 0 = fully loaded |
| wa | I/O wait% | High = disk bottleneck |

---

### OOM Killer (Out of Memory) ⭐⭐⭐

When the system runs out of memory, the kernel's OOM Killer selects and terminates processes.

```bash
# Check if OOM killer was triggered
dmesg | grep -i "out of memory"
dmesg | grep -i "killed process"
grep -i "oom" /var/log/syslog
journalctl -k | grep -i "oom"

# Check OOM score of a process (higher = more likely to be killed)
cat /proc/<PID>/oom_score
cat /proc/<PID>/oom_score_adj

# Protect a process from OOM killer
echo -1000 > /proc/<PID>/oom_score_adj
```

---

### Swap

```bash
# Check swap usage
swapon --show
free -h | grep Swap

# Create swap file
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile

# Persist in fstab
echo '/swapfile swap swap defaults 0 0' >> /etc/fstab

# Check swappiness (how aggressively kernel uses swap)
cat /proc/sys/vm/swappiness    # Default: 60 (range 0-100)
# Lower = prefer killing processes over swapping
# Higher = prefer swapping to disk
```

---

## CPU Monitoring ⭐⭐

### top — Real-Time Process Monitor

```bash
top                             # Interactive
top -p <PID>                    # Specific process
top -H -p <PID>                 # Thread-level view
```

#### Header Explained
```
top - 14:30:00 up 45 days, load average: 2.50, 2.10, 1.80
Tasks: 234 total, 2 running, 232 sleeping
%Cpu(s): 25.0 us, 5.0 sy, 0.0 ni, 68.0 id, 2.0 wa, 0.0 hi, 0.0 si
```

| Field | Meaning |
|-------|---------|
| load average | 1min, 5min, 15min (should be < CPU count) |
| us | User space CPU (application code) |
| sy | System/kernel CPU (system calls, I/O) |
| id | Idle CPU |
| wa | Waiting for I/O (disk bottleneck indicator) |
| hi/si | Hardware/software interrupts |

---

### uptime and Load Average

```bash
uptime
# 14:30:00 up 45 days, 3 users, load average: 2.50, 2.10, 1.80
```

**Load average interpretation** (for a 4-CPU system):
- Load < 4.0: System is fine
- Load = 4.0: CPUs fully utilized
- Load > 4.0: Processes are queuing (overloaded)
- Load increasing: System getting busier

```bash
# Check number of CPUs
nproc                           # Quick: number of processors
lscpu                           # Detailed CPU info
cat /proc/cpuinfo | grep processor | wc -l
```

---

### mpstat — Per-CPU Statistics

```bash
mpstat -P ALL 1 5               # All CPUs, every 1 sec, 5 times
```

### pidstat — Per-Process Statistics

```bash
pidstat -p <PID> 1              # CPU stats per second
pidstat -r -p <PID> 1           # Memory stats per second
pidstat -d -p <PID> 1           # Disk I/O stats per second
```

---

### iostat — Disk I/O Statistics

```bash
iostat -x 1 5                   # Extended stats, every 1 sec
```

#### Key Fields
| Field | Meaning | Warning Sign |
|-------|---------|--------------|
| %util | Device utilization | >80% = bottleneck |
| await | Average I/O wait (ms) | >20ms = slow disk |
| r/s, w/s | Reads/writes per second | Context dependent |

---

## Production Troubleshooting Flowchart ⭐⭐⭐

```
Application is slow
        │
        ├── CPU problem?
        │   └── top → check us%, load average
        │       └── High us% → application issue (profiling needed)
        │       └── High sy% → too many syscalls/context switches
        │       └── High wa% → disk I/O problem
        │
        ├── Memory problem?
        │   └── free -h → check available
        │       └── Low available + swap used → memory pressure
        │       └── dmesg | grep oom → OOM kills?
        │       └── vmstat → si/so > 0 → actively swapping
        │
        ├── Disk problem?
        │   └── df -h → disk full?
        │   └── iostat → %util high?
        │   └── du -sh → what's consuming space?
        │
        ├── Network problem?
        │   └── ss -s → connection counts
        │   └── ping/curl → latency
        │   └── ss -tn state close-wait → connection leaks
        │
        └── Process problem?
            └── ps aux → resource usage
            └── lsof → open files/connections
            └── strace → system call tracing
```

---

## Key Interview Questions

**Q: Server is running slow. Walk me through your troubleshooting process.**
> 1. `uptime` — Check load average (CPU overloaded?)
> 2. `top` — Identify high-CPU/memory processes
> 3. `free -h` — Memory pressure? Swap active?
> 4. `df -h` — Disk full?
> 5. `iostat -x 1` — Disk I/O bottleneck?
> 6. `ss -s` — Too many connections?
> 7. `dmesg | tail` — Any kernel errors?
> Based on findings, drill deeper into the specific resource.

**Q: What does it mean when load average is 8 on a 4-CPU system?**
> It means 8 processes are either running or waiting for CPU time, but only 4 can run simultaneously. 4 processes are queued. The system is overloaded — processes are competing for CPU time.

**Q: What's the difference between RSS and VSZ in `ps` output?**
> - RSS (Resident Set Size): Actual physical memory being used right now
> - VSZ (Virtual Size): Total memory allocated (including not-yet-used pages, shared libraries, swap)
> RSS is the real memory footprint. VSZ is always >= RSS.

**Q: How to find what's consuming disk space quickly?**
```bash
df -h                               # Which disk is full
du -sh /* 2>/dev/null | sort -rh    # Drill down from root
# Repeat: du -sh /var/* | sort -rh → du -sh /var/log/* | sort -rh
```

**Q: System has 16GB RAM but `free` shows only 1GB free. Is this a problem?**
> Not necessarily. Linux uses free memory for disk caching (buff/cache). Check the `available` column — if it's reasonable (e.g., 5-6GB), the system is fine. The cache will be released when applications need memory. Only worry if `available` is very low AND swap is being used.
