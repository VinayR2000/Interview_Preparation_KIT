# Linux Architecture and Commands

## Linux Architecture

```
┌─────────────────────────────────────┐
│           User Applications          │
├─────────────────────────────────────┤
│         System Libraries (glibc)     │
├─────────────────────────────────────┤
│       System Calls Interface         │
├─────────────────────────────────────┤
│              Kernel                   │
│  ┌──────┬──────┬──────┬──────────┐  │
│  │ Process│Memory│ File │ Device   │  │
│  │ Mgmt  │ Mgmt │System│ Drivers  │  │
│  └──────┴──────┴──────┴──────────┘  │
├─────────────────────────────────────┤
│            Hardware                   │
│    CPU, RAM, Disk, Network, I/O      │
└─────────────────────────────────────┘
```

### Components
| Component | Role |
|-----------|------|
| Hardware | Physical devices |
| Kernel | Core OS: process/memory/device management |
| System Libraries | Interface between apps and kernel (glibc) |
| System Calls | API for user programs to request kernel services |
| Shell | Command interpreter (Bash, Zsh) |
| User Programs | Applications (ls, vim, browsers) |

### Kernel Responsibilities
- Process scheduling and management
- Memory management (virtual memory, paging)
- File system management
- Device drivers
- Network stack
- Security and access control

---

## Essential Commands

### File System Navigation & Manipulation

#### ls - List Directory Contents
```bash
ls              # List files in current directory
ls -l           # Long format (permissions, owner, size, date)
ls -la          # Include hidden files (starting with .)
ls -lh          # Human-readable sizes (KB, MB, GB)
ls -lt          # Sort by modification time
ls -R           # Recursive listing
```

#### cd - Change Directory
```bash
cd /home/user   # Absolute path
cd ..           # Parent directory
cd ~            # Home directory
cd -            # Previous directory
```

#### cp - Copy
```bash
cp file1 file2           # Copy file
cp -r dir1 dir2          # Copy directory recursively
cp -p file1 file2        # Preserve permissions/timestamps
cp -i file1 file2        # Interactive (confirm overwrite)
```

#### mv - Move/Rename
```bash
mv file1 file2           # Rename file
mv file1 /path/to/dir/   # Move file
mv -i file1 file2        # Interactive (confirm overwrite)
```

#### rm - Remove
```bash
rm file                  # Delete file
rm -r directory          # Delete directory recursively
rm -f file               # Force delete (no confirmation)
rm -rf directory         # Force recursive delete (DANGEROUS!)
```

#### cat - Concatenate/Display
```bash
cat file                 # Display file contents
cat file1 file2          # Concatenate files
cat > newfile            # Create file (type content, Ctrl+D to save)
cat >> file              # Append to file
```

---

### Searching and Text Processing

#### grep - Search Text Patterns
```bash
grep "pattern" file              # Search for pattern in file
grep -i "pattern" file           # Case insensitive
grep -r "pattern" directory      # Recursive search
grep -n "pattern" file           # Show line numbers
grep -v "pattern" file           # Invert match (lines NOT matching)
grep -c "pattern" file           # Count matches
grep -l "pattern" *.txt          # List files with matches
grep -E "regex" file             # Extended regex (egrep)
grep -w "word" file              # Match whole word only
```

#### find - Search for Files
```bash
find /path -name "*.txt"         # Find by name
find /path -type f               # Files only
find /path -type d               # Directories only
find /path -size +100M           # Files larger than 100MB
find /path -mtime -7             # Modified in last 7 days
find /path -name "*.log" -delete # Find and delete
find /path -exec command {} \;   # Execute command on results
find /path -perm 777             # Find by permissions
```

#### awk - Text Processing
```bash
awk '{print $1}' file            # Print first column
awk -F: '{print $1}' /etc/passwd # Custom delimiter (:)
awk '$3 > 100' file              # Filter rows (3rd column > 100)
awk '{sum += $1} END {print sum}' # Sum first column
awk 'NR==5' file                 # Print 5th line
awk '/pattern/ {print}' file     # Print lines matching pattern
```

#### sed - Stream Editor
```bash
sed 's/old/new/' file            # Replace first occurrence per line
sed 's/old/new/g' file           # Replace all occurrences
sed -i 's/old/new/g' file       # In-place edit
sed '5d' file                    # Delete line 5
sed '/pattern/d' file            # Delete lines matching pattern
sed -n '10,20p' file             # Print lines 10-20
sed 's/^/prefix/' file           # Add prefix to each line
```

---

### Network Commands

#### curl - Transfer Data
```bash
curl https://api.example.com                    # GET request
curl -X POST -d '{"key":"val"}' URL             # POST with data
curl -H "Content-Type: application/json" URL    # Custom header
curl -o output.html URL                         # Save to file
curl -I URL                                     # Headers only (HEAD)
curl -u user:pass URL                           # Basic auth
curl -k URL                                     # Skip SSL verification
curl -L URL                                     # Follow redirects
```

#### wget - Download Files
```bash
wget URL                         # Download file
wget -O filename URL             # Save with specific name
wget -c URL                      # Resume interrupted download
wget -r URL                      # Recursive download
wget --mirror URL                # Mirror a website
```

#### ps - Process Status
```bash
ps                              # Current shell processes
ps aux                          # All processes, detailed
ps -ef                          # Full format listing
ps aux | grep java              # Find Java processes
ps -eo pid,ppid,cmd,%mem,%cpu   # Custom columns
```

---

## Key Interview Questions

**Q: What's the difference between hard link and soft link?**
> Hard link: Points directly to inode (same file data). Cannot cross filesystems, cannot link directories. Soft link (symlink): Points to filename. Can cross filesystems, can link directories. Like a shortcut. If target is deleted, soft link breaks but hard link still works.

**Q: What does `/dev/null` do?**
> It's a special file that discards all data written to it. Used to suppress output: `command > /dev/null 2>&1` (discard both stdout and stderr).

**Q: What's the difference between `>` and `>>`?**
> `>` overwrites the file. `>>` appends to the file.

**Q: How to find all Java processes and kill them?**
> `ps aux | grep java | awk '{print $2}' | xargs kill` or `pkill -f java`
