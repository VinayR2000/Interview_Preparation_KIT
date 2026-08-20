# Linux Text Processing and Pipes

## Pipes — The Power of Linux ⭐⭐⭐

### Concept
```
Command 1 → stdout → | → stdin → Command 2 → stdout → | → stdin → Command 3
```

Pipes (`|`) connect the output of one command to the input of another, enabling powerful data processing chains.

---

### Essential Pipe Patterns

```bash
# Find Java processes
ps aux | grep java

# Count error lines in log
grep "ERROR" application.log | wc -l

# Top 10 largest files
du -ah /var | sort -rh | head -10

# Most frequent error messages
grep "ERROR" app.log | awk '{print $5}' | sort | uniq -c | sort -rn | head

# Find which IPs hit your server most
cat access.log | awk '{print $1}' | sort | uniq -c | sort -rn | head -10

# Find and kill a process on port 8080
lsof -i :8080 | awk 'NR>1 {print $2}' | xargs kill

# Extract unique HTTP status codes
cat access.log | awk '{print $9}' | sort | uniq -c | sort -rn

# Monitor disk space and alert
df -h | awk '$5+0 > 80 {print "WARNING:", $6, "is", $5, "full"}'
```

---

## grep — Pattern Search ⭐⭐⭐

### Basic Usage
```bash
grep "pattern" file                 # Search for pattern
grep "ERROR" application.log        # Find errors in log
grep "Exception" *.log              # Search multiple files
```

### Essential Flags
```bash
grep -i "error" file                # Case-insensitive
grep -n "error" file                # Show line numbers
grep -r "TODO" /src                 # Recursive search in directory
grep -v "DEBUG" file                # Invert match (exclude DEBUG lines)
grep -c "ERROR" file                # Count matches
grep -l "password" *.conf           # List files containing match
grep -w "error" file                # Whole word match only
grep -A 3 "Exception" file         # Show 3 lines AFTER match
grep -B 2 "Exception" file         # Show 2 lines BEFORE match
grep -C 2 "Exception" file         # Show 2 lines BEFORE and AFTER
grep -E "error|warning" file       # Extended regex (OR pattern)
grep -P "\d{3}" file               # Perl regex
```

### Real-World grep Examples
```bash
# Find all errors in the last hour of logs
grep "$(date '+%H:')" application.log | grep -i "error"

# Find Java exceptions with stack traces
grep -A 10 "Exception" application.log

# Find config values
grep -r "spring.datasource" /etc/myapp/

# Find files NOT containing a pattern
grep -rL "copyright" src/

# Count errors per type
grep "ERROR" app.log | awk -F'ERROR' '{print $2}' | sort | uniq -c | sort -rn

# Find slow queries (response > 1000ms)
grep -E "took [0-9]{4,}ms" application.log
```

---

## find — File Search ⭐⭐⭐

### Basic Usage
```bash
find /path -name "filename"         # Find by exact name
find /path -name "*.log"            # Find by pattern
find . -name "*.java"               # Find all Java files
```

### Essential Options
```bash
# By type
find /var -type f                   # Files only
find /var -type d                   # Directories only
find /var -type l                   # Symlinks only

# By size
find / -size +100M                  # Files larger than 100MB
find / -size +1G                    # Files larger than 1GB
find /tmp -size 0                   # Empty files

# By time
find /var/log -mtime -1             # Modified in last 24 hours
find /var/log -mtime +30            # Modified more than 30 days ago
find /tmp -atime +7                 # Accessed more than 7 days ago
find . -newer reference_file        # Newer than reference file

# By permissions
find / -perm 777                    # World-writable files
find / -perm -u+s                   # SUID files (security audit)

# By owner
find /home -user john               # Files owned by john
find / -nouser                      # Files with no valid owner
```

### find with Actions
```bash
# Execute command on each result
find /var/log -name "*.log" -exec ls -lh {} \;

# Delete old files
find /tmp -mtime +7 -delete

# Change permissions
find /var/www -type f -exec chmod 644 {} \;
find /var/www -type d -exec chmod 755 {} \;

# Find and compress old logs
find /var/log -name "*.log" -mtime +30 -exec gzip {} \;

# Find large files and show sizes
find / -size +100M -type f -exec ls -lh {} \; 2>/dev/null
```

### Common find Interview Patterns
```bash
# Find all files modified in last 24 hours
find /var/log -type f -mtime -1

# Find and delete files older than 30 days
find /tmp -type f -mtime +30 -delete

# Find all SUID/SGID files (security audit)
find / -type f \( -perm -4000 -o -perm -2000 \) 2>/dev/null

# Find world-writable directories
find / -type d -perm -o+w 2>/dev/null

# Find empty files and directories
find /path -empty
```

---

## awk — Column-Based Text Processing ⭐⭐

### Basic Syntax
```bash
awk '{action}' file
awk '/pattern/ {action}' file
awk -F'delimiter' '{action}' file
```

### Print Columns
```bash
awk '{print $1}' file               # First column
awk '{print $1, $3}' file           # First and third columns
awk '{print $NF}' file              # Last column
awk '{print NR, $0}' file           # Line number + full line
```

### Custom Delimiters
```bash
awk -F: '{print $1}' /etc/passwd    # Colon-separated
awk -F, '{print $2}' data.csv       # Comma-separated
awk -F'\t' '{print $1}' file        # Tab-separated
```

### Conditions and Filtering
```bash
awk '$3 > 100 {print $1}' file      # Filter by column value
awk '/ERROR/ {print}' file          # Filter by pattern
awk 'NR >= 10 && NR <= 20' file     # Print lines 10-20
awk 'NF > 3' file                   # Lines with more than 3 fields
awk '$1 != "comment"' file          # Exclude specific values
```

### Aggregation
```bash
# Sum a column
awk '{sum += $3} END {print sum}' file

# Average
awk '{sum += $3; count++} END {print sum/count}' file

# Count occurrences
awk '{count[$1]++} END {for (k in count) print k, count[k]}' file

# Max value
awk 'BEGIN{max=0} $3>max {max=$3} END {print max}' file
```

### Real-World awk Examples
```bash
# Extract response times from access log
awk '{print $NF}' access.log | sort -n | tail -10

# Sum request sizes
awk '{sum += $10} END {print sum/1024/1024, "MB"}' access.log

# Count HTTP methods
awk '{print $6}' access.log | sort | uniq -c | sort -rn

# Find processes using most memory
ps aux | awk '$4 > 5.0 {print $11, $4"%"}'

# Parse CSV and filter
awk -F, '$3 > 1000 {printf "%-20s %s\n", $1, $3}' data.csv
```

---

## sed — Stream Editor ⭐⭐

### Basic Substitution
```bash
sed 's/old/new/' file               # Replace first occurrence per line
sed 's/old/new/g' file              # Replace ALL occurrences
sed 's/old/new/gi' file             # Case-insensitive replace all
sed -i 's/old/new/g' file           # In-place edit (modifies file!)
sed -i.bak 's/old/new/g' file       # In-place with backup
```

### Line Operations
```bash
sed '5d' file                       # Delete line 5
sed '1,10d' file                    # Delete lines 1-10
sed '/pattern/d' file               # Delete lines matching pattern
sed -n '10,20p' file                # Print only lines 10-20
sed -n '/START/,/END/p' file        # Print between patterns
```

### Insert and Append
```bash
sed '3i\New line' file              # Insert before line 3
sed '3a\New line' file              # Append after line 3
sed 's/^/prefix: /' file            # Add prefix to each line
sed 's/$/ :suffix/' file            # Add suffix to each line
```

### Real-World sed Examples
```bash
# Update application port
sed -i 's/server.port=8080/server.port=9090/' application.properties

# Remove comment lines
sed '/^#/d' config.file

# Remove empty lines
sed '/^$/d' file

# Replace environment-specific values
sed -i "s/DB_HOST=localhost/DB_HOST=$PROD_DB/" .env

# Extract text between markers
sed -n '/BEGIN/,/END/p' file
```

---

## sort, uniq, cut, tr — Supporting Tools

### sort
```bash
sort file                           # Alphabetical sort
sort -n file                        # Numeric sort
sort -r file                        # Reverse sort
sort -k 2 file                      # Sort by 2nd column
sort -t: -k3 -n /etc/passwd         # Sort passwd by UID
sort -u file                        # Sort and remove duplicates
sort -h file                        # Human-numeric (1K, 2M, 3G)
```

### uniq
```bash
uniq file                           # Remove adjacent duplicates
uniq -c file                        # Count occurrences
uniq -d file                        # Show only duplicates
sort file | uniq -c | sort -rn      # Frequency count (common pattern!)
```

### cut
```bash
cut -d: -f1 /etc/passwd             # Extract field 1 (delimiter :)
cut -d',' -f1,3 data.csv            # Fields 1 and 3
cut -c1-10 file                     # Characters 1-10
```

### tr — Translate/Delete Characters
```bash
echo "hello" | tr 'a-z' 'A-Z'      # To uppercase
echo "hello" | tr -d 'l'           # Delete character
echo "a  b  c" | tr -s ' '        # Squeeze repeated spaces
cat file | tr '\t' ','             # Tab to comma
```

---

## xargs — Build Commands from Input

```bash
# Delete all .tmp files
find . -name "*.tmp" | xargs rm

# Kill all Java processes
pgrep java | xargs kill

# Parallel execution
find . -name "*.gz" | xargs -P 4 gunzip

# With placeholder
find . -name "*.log" | xargs -I {} mv {} /archive/

# Limit arguments per command
echo "1 2 3 4 5" | xargs -n 2 echo
```

---

## Redirection Deep Dive

### Standard Streams
```
stdin  (0) → Standard Input  → Keyboard by default
stdout (1) → Standard Output → Terminal by default
stderr (2) → Standard Error  → Terminal by default
```

### Redirection Operators
```bash
# Output redirection
command > file              # stdout to file (overwrite)
command >> file             # stdout to file (append)
command 2> file             # stderr to file
command 2>> file            # stderr append
command > file 2>&1         # stdout + stderr to same file
command &> file             # Same (bash shorthand)
command > /dev/null 2>&1    # Discard all output

# Input redirection
command < file              # Read from file
command << EOF              # Here document
line 1
line 2
EOF

# Tee — write to file AND stdout
command | tee file          # Output to screen and file
command | tee -a file       # Append to file
```

---

## Complete Pipeline Examples ⭐⭐⭐

### Log Analysis
```bash
# Find top 10 most common errors
grep "ERROR" app.log | awk '{$1=$2=$3=""; print}' | sort | uniq -c | sort -rn | head -10

# Requests per minute
awk '{print $4}' access.log | cut -d: -f1-3 | sort | uniq -c | sort -rn | head

# Average response time
awk '{sum += $NF; count++} END {print sum/count, "ms"}' access.log

# Find slow endpoints
awk '$NF > 1000 {print $7, $NF"ms"}' access.log | sort -t' ' -k2 -rn | head
```

### System Monitoring
```bash
# Top 5 memory-consuming processes
ps aux --sort=-%mem | head -6

# Disk usage by top-level directories
du -sh /* 2>/dev/null | sort -rh | head -10

# Count open connections per IP
ss -tn | awk '{print $5}' | cut -d: -f1 | sort | uniq -c | sort -rn | head

# Find largest log files
find /var/log -name "*.log" -type f -exec ls -lh {} \; | awk '{print $5, $9}' | sort -rh | head
```

---

## Key Interview Questions

**Q: How do you find the top 10 most frequent IP addresses in an access log?**
```bash
awk '{print $1}' access.log | sort | uniq -c | sort -rn | head -10
```

**Q: How would you replace all occurrences of "localhost" with a production URL in all config files?**
```bash
find /etc/myapp -name "*.conf" -exec sed -i 's/localhost/prod-server.example.com/g' {} \;
```

**Q: Find all files larger than 100MB modified in the last 7 days**
```bash
find / -type f -size +100M -mtime -7 -exec ls -lh {} \;
```

**Q: How to count the number of 500 errors in an access log?**
```bash
awk '$9 == 500' access.log | wc -l
# or
grep -c '" 500 ' access.log
```

**Q: Difference between `grep`, `awk`, and `sed`?**
> - `grep`: Searches for patterns, filters lines. Best for: "Find lines containing X"
> - `awk`: Column-oriented text processing. Best for: "Extract and transform structured data"
> - `sed`: Stream editing, substitution. Best for: "Replace text, delete lines"
> They complement each other and are often combined in pipelines.
