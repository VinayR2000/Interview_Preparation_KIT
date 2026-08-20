# Linux Shell and Scripting

## Shell Basics

### What is a Shell?
- Command-line interpreter between user and kernel
- Reads commands, executes them, displays results
- Types: Bash (most common), Zsh, Fish, sh

### Bash (Bourne Again Shell)
- Default shell on most Linux distributions
- Superset of Bourne shell (sh)
- Features: History, tab completion, scripting, aliases

---

## Variables

### Shell Variables
```bash
# Assignment (no spaces around =!)
name="John"
age=25
path="/home/user"

# Access
echo $name
echo ${name}            # Preferred (clear boundaries)
echo "${name}_suffix"   # Variable inside string

# Read-only
readonly PI=3.14

# Unset
unset name
```

### Environment Variables
```bash
# Set environment variable (available to child processes)
export JAVA_HOME="/usr/lib/jvm/java-17"
export PATH="$PATH:/usr/local/bin"

# Common environment variables
echo $HOME          # User's home directory
echo $USER          # Current username
echo $PATH          # Executable search path
echo $SHELL         # Current shell
echo $PWD           # Current directory
echo $HOSTNAME      # Machine hostname
```

### Variable Scope
| Type | Scope | Set With |
|------|-------|----------|
| Local variable | Current shell only | `VAR=value` |
| Environment variable | Current shell + child processes | `export VAR=value` |

### Special Variables
| Variable | Meaning |
|----------|---------|
| `$0` | Script name |
| `$1, $2...` | Positional parameters |
| `$#` | Number of arguments |
| `$@` | All arguments (as separate words) |
| `$*` | All arguments (as single string) |
| `$?` | Exit status of last command |
| `$$` | Current shell PID |
| `$!` | PID of last background command |

---

## Pipes and Redirection

### Pipes (`|`)
- Send output of one command as input to another
- Creates a pipeline

```bash
# Examples
ls -l | grep ".txt"                    # Find .txt files
cat file | sort | uniq                 # Sort and remove duplicates
ps aux | grep java | awk '{print $2}' # Get PIDs of java processes
cat access.log | cut -d' ' -f1 | sort | uniq -c | sort -rn | head  # Top IPs
```

### Output Redirection
```bash
command > file           # Redirect stdout to file (overwrite)
command >> file          # Redirect stdout to file (append)
command 2> file          # Redirect stderr to file
command 2>&1             # Redirect stderr to stdout
command > file 2>&1      # Redirect both stdout and stderr to file
command &> file          # Same as above (bash shorthand)
command > /dev/null 2>&1 # Discard all output
```

### Input Redirection
```bash
command < file           # Use file as stdin
command << EOF           # Here document
Hello World
EOF
```

### File Descriptors
| FD | Name | Default |
|----|------|---------|
| 0 | stdin | Keyboard |
| 1 | stdout | Terminal |
| 2 | stderr | Terminal |

---

## Shell Scripts

### Basic Structure
```bash
#!/bin/bash
# This is a comment

echo "Hello, World!"
```

### Making Script Executable
```bash
chmod +x script.sh
./script.sh
# or
bash script.sh
```

### Conditional Statements
```bash
# if-else
if [ "$age" -gt 18 ]; then
    echo "Adult"
elif [ "$age" -eq 18 ]; then
    echo "Just turned adult"
else
    echo "Minor"
fi

# File tests
if [ -f "$file" ]; then echo "File exists"; fi
if [ -d "$dir" ]; then echo "Directory exists"; fi
if [ -r "$file" ]; then echo "File is readable"; fi
if [ -z "$var" ]; then echo "Variable is empty"; fi
if [ -n "$var" ]; then echo "Variable is not empty"; fi

# String comparison
if [ "$str1" = "$str2" ]; then echo "Equal"; fi
if [ "$str1" != "$str2" ]; then echo "Not equal"; fi

# Numeric comparison
-eq  # Equal
-ne  # Not equal
-gt  # Greater than
-lt  # Less than
-ge  # Greater or equal
-le  # Less or equal
```

### Loops
```bash
# For loop
for i in 1 2 3 4 5; do
    echo $i
done

for file in *.txt; do
    echo "Processing $file"
done

for i in $(seq 1 10); do
    echo $i
done

# While loop
count=0
while [ $count -lt 10 ]; do
    echo $count
    count=$((count + 1))
done

# Read file line by line
while IFS= read -r line; do
    echo "$line"
done < file.txt
```

### Functions
```bash
greet() {
    local name=$1       # Local variable
    echo "Hello, $name!"
    return 0            # Exit status
}

greet "World"
result=$?               # Capture return value
```

### Useful Script Patterns
```bash
# Check if command exists
if command -v docker &> /dev/null; then
    echo "Docker is installed"
fi

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "Please run as root"
    exit 1
fi

# Error handling
set -e          # Exit on first error
set -u          # Error on undefined variable
set -o pipefail # Pipeline returns error if any command fails

# Default values
name=${1:-"default"}    # Use "default" if $1 is empty
```

---

## Logs

### tail - View End of File
```bash
tail file               # Last 10 lines
tail -n 50 file         # Last 50 lines
tail -f file            # Follow (real-time, great for logs!)
tail -f /var/log/syslog # Watch system log live
tail -F file            # Follow + retry if file rotates
```

### Common Log Locations
| Log | Location | Content |
|-----|----------|---------|
| System | /var/log/syslog | General system messages |
| Auth | /var/log/auth.log | Authentication events |
| Kernel | /var/log/kern.log | Kernel messages |
| Application | /var/log/app/ | Application-specific |
| Journal | journalctl | Systemd journal |

### journalctl - Systemd Journal
```bash
journalctl                      # All logs
journalctl -u nginx             # Logs for nginx service
journalctl -f                   # Follow (like tail -f)
journalctl --since "1 hour ago" # Time-based filtering
journalctl --since today        # Today's logs
journalctl -p err               # Only errors
journalctl -b                   # Since last boot
journalctl -k                   # Kernel messages only
```

---

## Environment Configuration

### Configuration Files (in order of execution)
| File | When Loaded | Scope |
|------|-------------|-------|
| /etc/environment | Login | System-wide |
| /etc/profile | Login shell | System-wide |
| ~/.bash_profile | Login shell | User |
| ~/.bashrc | Interactive non-login | User |
| ~/.bash_logout | On logout | User |

### Typical .bashrc Content
```bash
# Aliases
alias ll='ls -la'
alias gs='git status'
alias dc='docker-compose'

# Environment variables
export JAVA_HOME="/usr/lib/jvm/java-17"
export PATH="$PATH:$JAVA_HOME/bin"
export EDITOR="vim"

# Custom prompt
PS1='\u@\h:\w\$ '
```

### Reload Configuration
```bash
source ~/.bashrc    # or
. ~/.bashrc         # Same thing
```

---

## Key Interview Questions

**Q: Difference between `.bashrc` and `.bash_profile`?**
> `.bash_profile` is executed for login shells (SSH, first terminal). `.bashrc` is executed for interactive non-login shells (new terminal tab). Best practice: Source .bashrc from .bash_profile.

**Q: How to find errors in a log file in real time?**
```bash
tail -f /var/log/app.log | grep -i "error"
```

**Q: What does `set -e` do in a script?**
> Exit immediately if any command returns non-zero exit status. Prevents the script from continuing after an error.

**Q: How to pass environment variables to a child process?**
> Use `export`. Without export, variables are only available in the current shell. `export VAR=value` makes it available to any child processes spawned from that shell.

**Q: What's the difference between `$@` and `$*`?**
> `"$@"` expands to each argument as a separate quoted word: `"arg1" "arg2" "arg3"`. `"$*"` expands to all arguments as a single word: `"arg1 arg2 arg3"`. Use `"$@"` in almost all cases.
