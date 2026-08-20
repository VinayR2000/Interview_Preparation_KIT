# Linux Process Management

## Process Concepts

### What is a Process in Linux?
- Running instance of a program
- Has unique PID (Process ID)
- Has parent process (PPID)
- Process 1 = init/systemd (parent of all)

### Process Types
| Type | Description |
|------|-------------|
| Foreground | Runs in terminal, receives input |
| Background | Runs without terminal interaction (append `&`) |
| Daemon | Background service (no terminal, runs continuously) |
| Zombie | Completed but parent hasn't read exit status |
| Orphan | Parent terminated, adopted by init/systemd |

---

## Process Commands

### ps - Process Status
```bash
ps                    # Processes in current shell
ps aux                # All processes (BSD syntax)
ps -ef                # All processes (System V syntax)
ps aux --sort=-%mem   # Sort by memory usage (descending)
ps aux --sort=-%cpu   # Sort by CPU usage (descending)
ps -p 1234            # Info about specific PID
ps -u username        # Processes by user
ps -eo pid,ppid,cmd,%mem,%cpu  # Custom format
```

#### Understanding `ps aux` Output
```
USER  PID %CPU %MEM   VSZ   RSS TTY  STAT  START  TIME COMMAND
root    1  0.0  0.1  2456  1234 ?    Ss    Jan01  0:03 /sbin/init
```
| Column | Meaning |
|--------|---------|
| USER | Process owner |
| PID | Process ID |
| %CPU | CPU usage |
| %MEM | Memory usage |
| VSZ | Virtual memory size |
| RSS | Resident (physical) memory |
| TTY | Terminal (? = no terminal) |
| STAT | Process state |
| TIME | Cumulative CPU time |

#### Process States (STAT)
| State | Meaning |
|-------|---------|
| R | Running |
| S | Sleeping (interruptible) |
| D | Uninterruptible sleep (I/O wait) |
| T | Stopped |
| Z | Zombie |
| s | Session leader |
| + | Foreground process group |
| < | High priority |
| N | Low priority |
| l | Multi-threaded |

---

### top - Real-time Process Monitor
```bash
top                   # Interactive process monitor
top -p 1234           # Monitor specific PID
top -u username       # Filter by user
```

#### top Shortcuts
| Key | Action |
|-----|--------|
| P | Sort by CPU |
| M | Sort by memory |
| k | Kill a process |
| q | Quit |
| 1 | Show individual CPUs |
| f | Add/remove fields |

### htop (Enhanced top)
```bash
htop                  # Interactive, colorful, easier to use
```

---

### kill - Send Signals to Process
```bash
kill PID              # Send SIGTERM (15) - graceful termination
kill -9 PID           # Send SIGKILL (9) - force kill
kill -15 PID          # SIGTERM explicitly
kill -STOP PID        # Pause process (SIGSTOP)
kill -CONT PID        # Resume process (SIGCONT)
kill -l               # List all signals
```

### Common Signals
| Signal | Number | Action | Catchable? |
|--------|--------|--------|-----------|
| SIGHUP | 1 | Hangup (terminal closed) | Yes |
| SIGINT | 2 | Interrupt (Ctrl+C) | Yes |
| SIGQUIT | 3 | Quit (Ctrl+\\) + core dump | Yes |
| SIGKILL | 9 | Force kill | **No** |
| SIGTERM | 15 | Graceful termination | Yes |
| SIGSTOP | 19 | Pause process | **No** |
| SIGCONT | 18 | Resume paused process | Yes |
| SIGCHLD | 17 | Child process terminated | Yes |

### kill vs kill -9
| `kill` (SIGTERM) | `kill -9` (SIGKILL) |
|-----------------|---------------------|
| Graceful shutdown | Immediate termination |
| Process can catch and cleanup | Cannot be caught or ignored |
| Allows saving state, closing files | No cleanup, no save |
| Try this first | Last resort |
| Process may refuse to die | Always works (except zombie/D state) |

### Other Kill Commands
```bash
killall process_name   # Kill all processes by name
pkill -f "pattern"     # Kill by pattern matching
pkill -u username      # Kill all processes of a user
```

---

## Background & Foreground

```bash
command &              # Run in background
jobs                   # List background jobs
fg %1                  # Bring job 1 to foreground
bg %1                  # Resume stopped job in background
Ctrl+Z                 # Stop (pause) foreground process
nohup command &        # Run process that survives terminal close
disown %1             # Detach job from terminal
```

---

## Process Priority (nice)

```bash
nice -n 10 command     # Start process with lower priority (higher nice = lower priority)
nice -n -20 command    # Start with highest priority (root only)
renice 5 -p PID       # Change priority of running process
```

### Nice Values
- Range: -20 (highest priority) to 19 (lowest priority)
- Default: 0
- Only root can set negative nice values

---

## Zombie and Orphan Processes

### Zombie Process
- Process has terminated but parent hasn't called `wait()` to read its exit status
- Shows as `Z` in ps output
- Takes no resources except a PID entry
- Can't be killed (already dead)
- Fix: Kill the parent (forces init to reap) or fix parent code

### Orphan Process
- Parent process terminated before child
- Adopted by init/systemd (PID 1)
- Init eventually calls `wait()` to reap it
- Not harmful

---

## Key Interview Questions

**Q: How to find which process is using port 8080?**
```bash
lsof -i :8080
# or
netstat -tlnp | grep 8080
# or
ss -tlnp | grep 8080
```

**Q: What's the difference between SIGTERM and SIGKILL?**
> SIGTERM (15) is a polite request to terminate - the process can catch it, cleanup, and exit gracefully. SIGKILL (9) immediately terminates the process with no chance to cleanup. Always try SIGTERM first; use SIGKILL only when process doesn't respond.

**Q: How to run a process that continues after you close the terminal?**
> `nohup command &` or use `screen`/`tmux`. `nohup` redirects output to nohup.out and ignores SIGHUP.

**Q: How to find the most memory-consuming processes?**
```bash
ps aux --sort=-%mem | head -10
# or
top (then press M to sort by memory)
```

**Q: What happens when you kill -9 a zombie process?**
> Nothing. Zombie processes are already dead - they just have an entry in the process table. You need to kill the parent process (which will cause init to reap the zombie) or fix the parent to properly call wait().
