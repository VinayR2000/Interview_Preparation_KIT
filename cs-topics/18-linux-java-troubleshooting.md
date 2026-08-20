# Linux Java Application Troubleshooting ⭐⭐⭐

## Finding Java Processes

### Locate Running Java Applications
```bash
# Find all Java processes
ps -ef | grep java
ps aux | grep java

# Find with full command line (shows JVM flags, classpath, main class)
ps -ef | grep java | grep -v grep

# Using pgrep
pgrep -a java                       # PID and full command
pgrep -f "spring-boot"              # Match against full command

# Using jps (JDK tool)
jps                                 # List Java processes
jps -l                              # With full class name
jps -v                              # With JVM arguments
jps -m                              # With main method arguments
```

### Example Output
```
UID   PID  PPID  C STIME TTY      TIME     CMD
app  12345  1    2 09:00 ?        00:05:30 java -Xmx2g -jar /opt/myapp/app.jar --spring.profiles.active=prod
```

---

## Resource Monitoring for Java Apps

### CPU Usage
```bash
# Real-time CPU per process
top -p <PID>

# CPU usage snapshot
ps -p <PID> -o %cpu,%mem,etime,cmd

# Find high-CPU Java threads
top -H -p <PID>                     # Thread-level view

# CPU over time
pidstat -p <PID> 1 10               # Every 1 sec, 10 times
```

### Memory Usage ⭐⭐⭐
```bash
# Process memory
ps -p <PID> -o pid,rss,vsz,%mem,cmd
# RSS = Resident Set Size (actual physical memory)
# VSZ = Virtual Size (total allocated, including swap)

# System memory
free -h                             # Human-readable
free -m                             # In MB

# Detailed memory
cat /proc/<PID>/status | grep -i mem
# VmPeak: Peak virtual memory
# VmRSS:  Resident Set Size
# VmSize: Current virtual memory
```

#### free -h Output Explained
```
              total     used     free   shared  buff/cache  available
Mem:           16G      12G      500M    256M       3.5G        3.2G
Swap:           4G      1.2G     2.8G
```
- **used**: Memory in use by processes
- **buff/cache**: Used for disk caching (can be reclaimed)
- **available**: Memory available for new processes (free + reclaimable cache)
- **Swap used > 0**: System is under memory pressure

---

### Disk Usage
```bash
# Filesystem space
df -h

# Application directory size
du -sh /opt/myapp/

# Find large files
find /opt/myapp -size +100M -type f -exec ls -lh {} \;

# Log file sizes
du -sh /var/log/myapp/*

# Watch for disk filling
watch -n 5 'df -h | grep "/dev/sda1"'
```

---

### Open Files and File Descriptors
```bash
# List all open files for a process
lsof -p <PID>

# Count open files
lsof -p <PID> | wc -l

# Check file descriptor limits
cat /proc/<PID>/limits | grep "open files"

# Current FD count vs limit
ls /proc/<PID>/fd | wc -l

# System-wide open file count
cat /proc/sys/fs/file-nr
# Output: allocated  free  max_allowed

# Find processes with most open files
lsof | awk '{print $1}' | sort | uniq -c | sort -rn | head
```

**Common issue**: `Too many open files` error
```bash
# Check current limit
ulimit -n

# Increase for current session
ulimit -n 65535

# Permanent: edit /etc/security/limits.conf
# app_user  soft  nofile  65535
# app_user  hard  nofile  65535
```

---

## Network Troubleshooting for Java Apps

### Port Conflicts ⭐⭐⭐
```bash
# "Port 8080 already in use" — Find who's using it
ss -tlnp | grep :8080
lsof -i :8080
fuser 8080/tcp

# Kill process on port
fuser -k 8080/tcp
# or
lsof -i :8080 | awk 'NR>1 {print $2}' | xargs kill
```

### Connection Issues
```bash
# Check if app is listening
ss -tlnp | grep java

# Test connectivity to database
curl -v telnet://db-host:5432
nc -zv db-host 5432

# Test Spring Boot health endpoint
curl http://localhost:8080/actuator/health

# Check established connections
ss -tn | grep <PID>
ss -tn state established | grep :8080

# Count connections per state
ss -tn | awk '{print $1}' | sort | uniq -c
```

### DNS Resolution
```bash
# Check if hostname resolves
nslookup db-server.internal
dig db-server.internal

# Check /etc/hosts
cat /etc/hosts

# Check DNS config
cat /etc/resolv.conf
```

---

## JVM-Specific Linux Tools

### Thread Dump
```bash
# Send SIGQUIT to get thread dump (prints to stdout/log)
kill -3 <PID>

# Using jstack (JDK tool)
jstack <PID> > thread_dump.txt

# Find thread with high CPU
top -H -p <PID>                     # Note the thread ID (TID)
printf '%x\n' <TID>                 # Convert to hex
jstack <PID> | grep -A 30 "nid=0x<hex_tid>"
```

### Heap Dump
```bash
# Using jmap
jmap -dump:live,format=b,file=heap.hprof <PID>

# Heap summary
jmap -heap <PID>

# Histogram (class memory usage)
jmap -histo <PID> | head -20
```

### GC Monitoring
```bash
# GC stats
jstat -gc <PID> 1000               # Every 1 second
jstat -gcutil <PID> 1000           # Percentage format

# GC log analysis (if -Xlog:gc enabled)
grep "GC pause" gc.log | tail -20
```

---

## Common Troubleshooting Scenarios

### Scenario 1: Application Not Starting ⭐⭐⭐
```bash
# 1. Check if process exists
ps -ef | grep java

# 2. Check recent logs
tail -100 /var/log/myapp/application.log

# 3. Check port availability
ss -tlnp | grep :8080

# 4. Check available memory
free -h

# 5. Check disk space
df -h

# 6. Check application logs for errors
grep -i "error\|exception\|failed" /var/log/myapp/application.log | tail -20
```

### Scenario 2: Application Running Slow ⭐⭐⭐
```bash
# 1. Check CPU usage
top -p <PID>

# 2. Check memory / GC pressure
free -h
jstat -gcutil <PID> 1000 5

# 3. Check disk I/O
iostat -x 1 5

# 4. Check network latency to dependencies
curl -w "Total: %{time_total}s\n" -o /dev/null http://db-host:5432
ping db-host

# 5. Check thread count
ps -p <PID> -T | wc -l
cat /proc/<PID>/status | grep Threads

# 6. Check for blocked threads
jstack <PID> | grep -c "BLOCKED"
jstack <PID> | grep -c "WAITING"
```

### Scenario 3: Out of Memory ⭐⭐⭐
```bash
# 1. Check system memory
free -h

# 2. Check if OOM killer was triggered
dmesg | grep -i "out of memory"
dmesg | grep -i "killed process"
grep -i "oom" /var/log/syslog

# 3. Check Java heap
jmap -heap <PID>

# 4. Top memory consumers
ps aux --sort=-%mem | head -10

# 5. Check swap usage
swapon --show
vmstat 1 5

# 6. Generate heap dump for analysis
jmap -dump:live,format=b,file=heap.hprof <PID>
```

### Scenario 4: Too Many Open Files
```bash
# 1. Check current count
ls /proc/<PID>/fd | wc -l

# 2. Check limit
cat /proc/<PID>/limits | grep "open files"

# 3. Find what files are open
lsof -p <PID> | awk '{print $9}' | sort | uniq -c | sort -rn | head

# 4. Check for connection leaks (many CLOSE_WAIT)
ss -tn state close-wait | grep <PID>
lsof -p <PID> | grep -c "TCP"
```

### Scenario 5: Disk Full
```bash
# 1. Find the full filesystem
df -h

# 2. Find largest directories
du -sh /var/log/* | sort -rh | head

# 3. Find largest files
find /var/log -size +100M -type f -exec ls -lh {} \;

# 4. Check for log rotation
ls -la /etc/logrotate.d/

# 5. Quick cleanup (if safe)
find /var/log -name "*.log.gz" -mtime +30 -delete
> /var/log/myapp/application.log    # Truncate (keep fd open)
```

---

## Monitoring Cheat Sheet

| What to Check | Command |
|---------------|---------|
| Is process running? | `ps -ef \| grep java` |
| CPU usage | `top -p <PID>` |
| Memory usage | `free -h`, `ps -p <PID> -o rss,%mem` |
| Disk space | `df -h` |
| Disk usage | `du -sh *` |
| Open files | `lsof -p <PID> \| wc -l` |
| Network ports | `ss -tlnp` |
| Port in use | `ss -tlnp \| grep :8080` |
| Connections | `ss -tn \| grep :8080` |
| Logs | `tail -f /var/log/myapp/app.log` |
| Thread count | `ps -p <PID> -T \| wc -l` |
| OOM events | `dmesg \| grep -i oom` |
| System load | `uptime`, `top` |

---

## Key Interview Questions

**Q: Spring Boot application says "Port 8080 already in use." How do you fix it?**
```bash
ss -tlnp | grep :8080          # Find the process
kill <PID>                      # Terminate it
# Or change app port: --server.port=8081
```

**Q: Java application is consuming too much memory. How do you diagnose?**
> 1. `free -h` — Check system memory
> 2. `ps -p <PID> -o rss,%mem` — Check process memory
> 3. `jmap -heap <PID>` — Check JVM heap usage
> 4. `jstat -gcutil <PID> 1000` — Check GC activity
> 5. If GC is constant → heap too small or memory leak
> 6. `jmap -dump:live,format=b,file=heap.hprof <PID>` → Analyze with Eclipse MAT

**Q: How to check if a Java application is properly connected to the database?**
```bash
# Check if DB port is reachable
nc -zv db-host 5432

# Check established connections from app
ss -tn | grep <db-port>

# Check from application logs
grep -i "connection\|datasource" /var/log/myapp/app.log

# Test directly
curl http://localhost:8080/actuator/health
```

**Q: Application logs are filling up disk. What do you do?**
> 1. `df -h` — Confirm disk usage
> 2. `du -sh /var/log/myapp/*` — Find the culprit
> 3. Immediate: truncate the log `> /var/log/myapp/app.log`
> 4. Long-term: Configure logback/log4j rotation, set up logrotate
> 5. Prevent: Add alerting on disk usage > 80%
