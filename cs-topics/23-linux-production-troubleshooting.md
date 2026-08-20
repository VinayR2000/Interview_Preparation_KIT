# Linux Production Troubleshooting ⭐⭐⭐

## The Troubleshooting Mindset

### Systematic Approach

```
Application slow or down
        │
        ▼
┌─────────────────────────┐
│  1. Define the problem  │ → What exactly is wrong?
├─────────────────────────┤
│  2. Check symptoms      │ → CPU? Memory? Disk? Network?
├─────────────────────────┤
│  3. Form hypothesis     │ → What could cause this?
├─────────────────────────┤
│  4. Test hypothesis     │ → Verify with commands
├─────────────────────────┤
│  5. Fix and verify      │ → Apply fix, confirm resolution
└─────────────────────────┘
```

---

## The USE Method (Utilization, Saturation, Errors)

For each resource, check:
- **U**tilization: How busy is it? (% used)
- **S**aturation: Is work queuing? (waiting)
- **E**rrors: Any errors occurring?

| Resource | Utilization | Saturation | Errors |
|----------|-------------|------------|--------|
| CPU | `top` (us%, sy%) | Load avg > CPU count | `dmesg` |
| Memory | `free -h` (available) | swap used, `vmstat` si/so | `dmesg \| grep oom` |
| Disk I/O | `iostat` (%util) | `iostat` (avgqu-sz) | `dmesg`, `smartctl` |
| Disk Space | `df -h` (Use%) | 100% full | `df -i` (inode) |
| Network | `sar -n DEV` | `ss -s`, drops | `ifconfig` (errors) |

---

## Complete First-Response Playbook ⭐⭐⭐

When paged about a production issue, run these in order:

### Step 1: Quick Overview (30 seconds)
```bash
# System load and uptime
uptime
# 14:30:00 up 45 days, load average: 12.50, 8.10, 4.80
# ↑ Load increasing rapidly — problem is recent and getting worse

# Who else is on the system
w

# Quick memory check
free -h

# Quick disk check
df -h | awk '$5+0 > 80 {print}'    # Only show > 80% full
```

### Step 2: Top Processes (1 minute)
```bash
# CPU and memory leaders
top -bn1 | head -20

# Specifically for Java apps
ps aux --sort=-%cpu | head -10
ps aux --sort=-%mem | head -10
```

### Step 3: Identify the Bottleneck (2-3 minutes)
```bash
# CPU bottleneck?
mpstat -P ALL 1 3                   # Per-CPU usage
# If us% high → application problem
# If sy% high → kernel/system problem
# If wa% high → disk I/O problem

# Memory bottleneck?
vmstat 1 5
# If si/so > 0 → swapping (memory pressure)
# If free very low + no cache → memory exhaustion

# Disk I/O bottleneck?
iostat -x 1 3
# If %util > 80% → disk saturated
# If await > 20ms → slow disk responses

# Network issues?
ss -s                               # Connection summary
ss -tn state close-wait | wc -l     # Connection leaks
```

---

## Scenario-Based Troubleshooting

### Scenario 1: Application Not Responding ⭐⭐⭐

**Symptoms**: Health check failing, users getting timeouts

```bash
# 1. Is the process running?
ps -ef | grep java
# If not running → check logs for why it crashed

# 2. Is it listening on the port?
ss -tlnp | grep :8080
# If not listening → process crashed or failed to start

# 3. Can you connect locally?
curl -m 5 http://localhost:8080/actuator/health
# If timeout → application is hung (deadlock, GC storm)

# 4. Check thread state
jstack <PID> | grep -c "BLOCKED"
jstack <PID> | grep -c "RUNNABLE"
# Many BLOCKED → possible deadlock

# 5. Check GC activity
jstat -gcutil <PID> 1000 5
# If GC is running constantly (>50% time) → memory pressure

# 6. Check system resources
top -p <PID>                        # CPU usage of process
free -h                             # Available memory
df -h                               # Disk space
```

---

### Scenario 2: High Response Times ⭐⭐⭐

**Symptoms**: API responses > 5 seconds, timeouts

```bash
# 1. Where is time being spent?
# Check application logs for slow queries/calls
grep -i "slow\|timeout\|took [0-9]\{4,\}" /var/log/myapp/app.log | tail -20

# 2. CPU contention?
top -p <PID>
# If CPU > 90% → profiling needed
# High thread count + moderate CPU → too many context switches

# 3. Memory/GC issues?
jstat -gcutil <PID> 1000 5
# Full GC happening frequently → increase heap or find leak

# 4. Database connection issues?
ss -tn | grep :5432 | awk '{print $4}' | sort | uniq -c
# Many connections in CLOSE_WAIT → connection pool leak

# 5. External service latency?
curl -w "DNS: %{time_namelookup}s\nConnect: %{time_connect}s\nTotal: %{time_total}s\n" \
     -o /dev/null -s http://external-service:8080/api

# 6. Disk I/O (if app does file operations)?
iostat -x 1 3
# High await → slow disk affecting writes
```

---

### Scenario 3: Memory Issues ⭐⭐⭐

**Symptoms**: OOMKilled, application restart, slow GC

```bash
# 1. System memory status
free -h
# If available < 500MB → system memory pressure

# 2. What's consuming memory?
ps aux --sort=-%mem | head -10

# 3. Was OOM killer triggered?
dmesg | grep -i "out of memory" | tail -5
dmesg | grep -i "killed process" | tail -5

# 4. Java heap analysis
jmap -heap <PID>
# Check: used vs max heap

# 5. GC behavior
jstat -gcutil <PID> 1000 10
# Old generation at 100% + frequent Full GC = memory leak

# 6. Generate heap dump (if needed)
jmap -dump:live,format=b,file=/tmp/heap.hprof <PID>
# Download and analyze with Eclipse MAT or VisualVM

# 7. Check for memory leaks
jmap -histo <PID> | head -20
# Look for unusually large instance counts
```

---

### Scenario 4: Disk Full ⭐⭐⭐

**Symptoms**: Application errors writing files, database errors

```bash
# 1. Identify full filesystem
df -h
# /dev/xvda1    20G    20G    0   100%  /

# 2. Find what's consuming space
du -sh /* 2>/dev/null | sort -rh | head -5
du -sh /var/* | sort -rh | head -5
du -sh /var/log/* | sort -rh | head -5

# 3. Find large files
find / -type f -size +100M -exec ls -lh {} \; 2>/dev/null | sort -k5 -rh | head -10

# 4. Check for deleted files held by processes
lsof | grep '(deleted)' | awk '{print $7, $9}' | sort -rn | head -10
# Fix: restart the process holding the file

# 5. Quick relief
# Compress old logs
find /var/log -name "*.log" -mtime +3 -exec gzip {} \;
# Remove old rotated logs
find /var/log -name "*.gz" -mtime +30 -delete
# Truncate (NOT delete) active log
> /var/log/myapp/application.log

# 6. Check inodes (can be full even with disk space!)
df -i
```

---

### Scenario 5: Network/Connection Issues ⭐⭐⭐

**Symptoms**: Connection refused, timeouts, intermittent failures

```bash
# 1. Is the target reachable?
ping -c 3 target-host
nc -zv target-host 5432            # Test specific port
curl -v http://target-host:8080/health

# 2. DNS working?
nslookup target-host
dig target-host

# 3. Check local connections
ss -s                               # Connection summary
ss -tn | awk '{print $1}' | sort | uniq -c | sort -rn
# Many TIME_WAIT → connection churn
# Many CLOSE_WAIT → application not closing connections

# 4. Check connection limits
cat /proc/sys/net/core/somaxconn    # Max backlog
cat /proc/sys/net/ipv4/ip_local_port_range  # Available ports
ss -s | grep "TCP:"                 # Current total connections

# 5. Firewall blocking?
iptables -L -n | grep <port>
# Or on target: ss -tlnp | grep <port>

# 6. High latency to dependencies?
traceroute target-host
mtr target-host                     # Combined ping + traceroute
```

---

### Scenario 6: Process Crash / Restart Loop

**Symptoms**: Application keeps restarting, CrashLoopBackOff in K8s

```bash
# 1. Check recent crash
dmesg | tail -20                    # Kernel messages
journalctl -u myapp --since "10 min ago"

# 2. Was it killed?
grep "killed" /var/log/syslog | tail -5
dmesg | grep -i "oom\|segfault\|killed"

# 3. Check exit code
systemctl status myapp              # Shows exit code
# Exit 137 = killed by signal 9 (SIGKILL/OOM)
# Exit 143 = killed by signal 15 (SIGTERM)
# Exit 1   = application error

# 4. Check logs before crash
journalctl -u myapp --since "10 min ago" -p err

# 5. Check resources at crash time
sar -r                              # Historical memory
sar -u                              # Historical CPU

# 6. Set up core dumps for analysis
ulimit -c unlimited
echo '/tmp/core.%p' > /proc/sys/kernel/core_pattern
```

---

## Advanced Troubleshooting Tools

### strace — System Call Tracing
```bash
# Trace a running process
strace -p <PID>                     # All syscalls
strace -p <PID> -e trace=network    # Network calls only
strace -p <PID> -e trace=file       # File operations only
strace -c -p <PID>                  # Statistics summary

# Trace a new command
strace -o output.txt command
```

### lsof — List Open Files
```bash
lsof -p <PID>                       # All open files for process
lsof -i :8080                       # What's on port 8080
lsof -i -P -n                       # All network connections
lsof -u username                    # Files opened by user
lsof +D /var/log                    # Files open in directory
```

### /proc Filesystem for Deep Inspection
```bash
# Process information
cat /proc/<PID>/cmdline | tr '\0' ' '    # Full command
cat /proc/<PID>/status                    # Detailed status
cat /proc/<PID>/limits                    # Resource limits
cat /proc/<PID>/io                        # I/O statistics
ls /proc/<PID>/fd | wc -l                # Open file count
cat /proc/<PID>/net/tcp                   # TCP connections

# System information
cat /proc/loadavg                         # Load average
cat /proc/meminfo                         # Detailed memory
cat /proc/stat                            # CPU statistics
cat /proc/diskstats                       # Disk I/O stats
```

### sar — System Activity Reporter
```bash
# Historical data (if sysstat installed)
sar -u                              # CPU history
sar -r                              # Memory history
sar -d                              # Disk history
sar -n DEV                          # Network history
sar -q                              # Load average history
```

---

## Quick Reference: Command → Problem Mapping

| Problem | First Commands |
|---------|---------------|
| "Is it running?" | `ps -ef \| grep java` |
| "High CPU" | `top`, `pidstat -p <PID> 1` |
| "Out of memory" | `free -h`, `dmesg \| grep oom` |
| "Disk full" | `df -h`, `du -sh /*` |
| "Can't connect" | `ss -tlnp`, `curl`, `nc -zv` |
| "Port in use" | `ss -tlnp \| grep :PORT` |
| "Slow response" | `curl -w`, `jstat`, logs |
| "Connection leak" | `ss -tn state close-wait` |
| "Too many open files" | `lsof -p <PID> \| wc -l` |
| "Process killed" | `dmesg \| grep killed` |
| "Boot failure" | `journalctl -b`, `systemctl --failed` |
| "Permission denied" | `ls -la`, `namei -l /path` |
| "DNS failure" | `nslookup`, `dig`, `/etc/resolv.conf` |

---

## Production Monitoring One-Liners ⭐⭐⭐

```bash
# Quick health check script
echo "=== LOAD ===" && uptime && \
echo "=== MEMORY ===" && free -h && \
echo "=== DISK ===" && df -h | awk '$5+0>70' && \
echo "=== TOP PROCS ===" && ps aux --sort=-%cpu | head -5

# Watch for errors in real-time
tail -f /var/log/myapp/app.log | grep --line-buffered -iE "error|exception|fatal"

# Connection count monitor
watch -n 5 'ss -s'

# Memory trend
watch -n 10 'free -h | grep Mem'

# Check all services status
systemctl list-units --type=service --state=failed

# Quick network port scan on localhost
ss -tlnp | awk 'NR>1 {print $4}' | sort -t: -k2 -n
```

---

## Key Interview Questions

**Q: Production application is down. Walk through your first 5 minutes.**
> 1. `ps -ef | grep java` — Is the process running?
> 2. `tail -50 /var/log/myapp/app.log` — What do logs say?
> 3. `free -h && df -h` — Resources available?
> 4. `dmesg | tail` — Kernel errors? OOM kill?
> 5. `ss -tlnp | grep :8080` — Port available?
> 6. If process is running: `curl localhost:8080/health` — Responsive?
> 7. Based on findings: restart service, increase resources, or investigate deeper.

**Q: How do you check if a Java application has a memory leak?**
> 1. Monitor RSS over time: `while true; do ps -p <PID> -o rss=; sleep 60; done`
> 2. Check GC activity: `jstat -gcutil <PID> 5000` — Old gen growing?
> 3. If Old gen keeps growing and Full GC doesn't reclaim: likely a leak
> 4. Get heap dump: `jmap -dump:live,format=b,file=heap.hprof <PID>`
> 5. Analyze with Eclipse MAT: find objects retaining most memory

**Q: How to handle a "Too many open files" error in production?**
> 1. Check current count: `lsof -p <PID> | wc -l`
> 2. Check limit: `cat /proc/<PID>/limits | grep "open files"`
> 3. Find what's open: `lsof -p <PID> | awk '{print $9}' | sort | uniq -c | sort -rn | head`
> 4. Short-term fix: Increase limit (`ulimit -n 65535` or systemd `LimitNOFILE`)
> 5. Long-term fix: Find and fix the resource leak (unclosed connections/files)

**Q: What's the difference between `tail -f` and watching logs with journalctl?**
> `tail -f` follows a specific file — simple, works everywhere. `journalctl -u service -f` accesses systemd's structured journal — supports filtering by time, priority, service, and survives log rotation. Use `journalctl` for systemd services, `tail -f` for custom log files.

**Q: Server has high load average but low CPU usage. What could cause this?**
> High load average counts processes in both running AND uninterruptible sleep (I/O wait) states. Low CPU + high load = processes waiting for disk I/O. Check with `iostat -x 1` — if %util is high or await is large, disk is the bottleneck. Could be: slow disk, too many writes, failing drive.
