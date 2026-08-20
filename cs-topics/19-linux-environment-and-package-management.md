# Linux Environment Variables and Package Management

## Environment Variables ⭐⭐

### Understanding Environment Variables

```
┌─────────────────────────────────┐
│         Shell Session           │
│                                 │
│  LOCAL_VAR=value (shell only)   │
│  export ENV_VAR=value           │
│         │                       │
│         ├──→ Child Process 1    │
│         │    (inherits ENV_VAR) │
│         ├──→ Child Process 2    │
│         │    (inherits ENV_VAR) │
│         └──→ Java Application   │
│              (reads ENV_VAR)    │
└─────────────────────────────────┘
```

---

### Viewing Environment Variables

```bash
# All environment variables
env
printenv

# Specific variable
echo $PATH
echo $HOME
echo $USER
printenv JAVA_HOME

# All variables (including shell-local)
set
```

### Common System Variables
| Variable | Purpose | Example |
|----------|---------|---------|
| `PATH` | Executable search directories | `/usr/bin:/usr/local/bin` |
| `HOME` | User's home directory | `/home/john` |
| `USER` | Current username | `john` |
| `SHELL` | Default shell | `/bin/bash` |
| `PWD` | Current directory | `/opt/myapp` |
| `HOSTNAME` | Machine hostname | `prod-server-01` |
| `LANG` | System locale | `en_US.UTF-8` |
| `TERM` | Terminal type | `xterm-256color` |
| `EDITOR` | Default text editor | `vim` |

---

### Setting Environment Variables

```bash
# Local variable (current shell only)
DB_HOST=localhost
echo $DB_HOST                    # Works in this shell

# Environment variable (available to child processes)
export DB_HOST=localhost
export DB_PORT=5432
export JAVA_HOME=/usr/lib/jvm/java-17
export PATH="$PATH:$JAVA_HOME/bin"

# Set and export in one line
export SPRING_PROFILES_ACTIVE=production

# Remove variable
unset DB_HOST
```

---

### Persistence — Where to Set Variables

| File | Scope | When Loaded |
|------|-------|-------------|
| `/etc/environment` | All users, all processes | Login |
| `/etc/profile` | All users, login shells | Login |
| `/etc/profile.d/*.sh` | All users, login shells | Login |
| `~/.bash_profile` | Current user, login shell | Login (SSH) |
| `~/.bashrc` | Current user, interactive shell | New terminal |
| `~/.profile` | Current user, login shell | Login |

```bash
# System-wide (all users)
echo 'JAVA_HOME=/usr/lib/jvm/java-17' >> /etc/environment

# Current user (persists across sessions)
echo 'export JAVA_HOME=/usr/lib/jvm/java-17' >> ~/.bashrc
source ~/.bashrc         # Apply immediately
```

---

### Environment Variables → Spring Boot Connection ⭐⭐⭐

Spring Boot reads environment variables for configuration:

```bash
# These override application.properties
export SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mydb
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD=secret
export SERVER_PORT=9090
export SPRING_PROFILES_ACTIVE=production

# Run application
java -jar myapp.jar
```

**Priority order** (highest to lowest):
1. Command line args (`--server.port=9090`)
2. Environment variables (`SERVER_PORT=9090`)
3. `application-{profile}.properties`
4. `application.properties`

---

### PATH Variable ⭐⭐⭐

```bash
# View current PATH
echo $PATH
# /usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Add to PATH
export PATH="$PATH:/opt/kafka/bin"
export PATH="$PATH:$JAVA_HOME/bin"

# Add to beginning (takes priority)
export PATH="/opt/custom/bin:$PATH"

# Check where a command is found
which java          # /usr/bin/java
type java           # java is /usr/bin/java
whereis java        # shows all locations
```

---

## Package Management

### Debian/Ubuntu (apt) ⭐⭐⭐

```bash
# Update package lists
apt update

# Install package
apt install nginx
apt install -y openjdk-17-jdk     # -y = auto-confirm

# Remove package
apt remove nginx                   # Remove package
apt purge nginx                    # Remove + config files
apt autoremove                     # Remove unused dependencies

# Upgrade
apt upgrade                        # Upgrade all packages
apt full-upgrade                   # Upgrade with dependency changes

# Search
apt search keyword
apt list --installed               # List installed packages
apt show nginx                     # Package details

# Check package info
dpkg -l | grep java                # List installed matching "java"
dpkg -L package_name               # List files from a package
dpkg -S /usr/bin/java              # Which package owns this file
```

---

### RHEL/Amazon Linux (yum/dnf)

```bash
# Install
yum install nginx
dnf install java-17-openjdk        # dnf is the modern replacement

# Remove
yum remove nginx

# Update
yum update                         # Update all
yum update nginx                   # Update specific

# Search
yum search keyword
yum list installed | grep java

# Package info
yum info nginx
rpm -qa | grep java                # List all installed RPMs
rpm -ql package_name               # List files in package
```

---

### Key Package Management Concepts

| Concept | Description |
|---------|-------------|
| Repository | Remote source of packages (like Maven Central) |
| Package | Software bundle with metadata and dependencies |
| Dependency | Other packages required by a package |
| Cache | Local copy of repository metadata |

```bash
# Add a repository (Ubuntu)
add-apt-repository ppa:some/repo
apt update

# Add a repository (RHEL)
yum-config-manager --add-repo https://repo.example.com/repo.rpm
```

---

## systemd and Services ⭐⭐⭐

### Understanding systemd

```
systemd (PID 1)
├── Service management
├── Socket activation
├── Timer (cron replacement)
├── Mount management
└── Logging (journald)
```

### systemctl — Service Management

```bash
# Status
systemctl status nginx              # Detailed service status
systemctl is-active nginx           # Just active/inactive
systemctl is-enabled nginx          # Will it start on boot?

# Control
systemctl start nginx               # Start service
systemctl stop nginx                # Stop service
systemctl restart nginx             # Stop then start
systemctl reload nginx              # Reload config (no downtime)

# Boot behavior
systemctl enable nginx              # Start on boot
systemctl disable nginx             # Don't start on boot
systemctl enable --now nginx        # Enable + start immediately

# List services
systemctl list-units --type=service              # Running services
systemctl list-units --type=service --state=failed   # Failed services
systemctl list-unit-files --type=service         # All service files
```

---

### Creating a Spring Boot Service ⭐⭐⭐

```ini
# /etc/systemd/system/myapp.service
[Unit]
Description=My Spring Boot Application
After=network.target

[Service]
Type=simple
User=appuser
Group=appuser
WorkingDirectory=/opt/myapp
ExecStart=/usr/bin/java -Xmx2g -jar /opt/myapp/app.jar --spring.profiles.active=production
Restart=on-failure
RestartSec=10
StandardOutput=append:/var/log/myapp/stdout.log
StandardError=append:/var/log/myapp/stderr.log

# Environment
Environment=JAVA_HOME=/usr/lib/jvm/java-17
Environment=SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/mydb
EnvironmentFile=/opt/myapp/.env

# Resource limits
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

```bash
# Deploy the service
systemctl daemon-reload             # Reload after creating/modifying service file
systemctl enable myapp              # Enable on boot
systemctl start myapp               # Start now
systemctl status myapp              # Verify
```

---

### journalctl — Service Logs ⭐⭐⭐

```bash
# View logs for a service
journalctl -u myapp                 # All logs for service
journalctl -u myapp -f              # Follow (like tail -f)
journalctl -u myapp --since "1 hour ago"
journalctl -u myapp --since today
journalctl -u myapp -n 100         # Last 100 lines
journalctl -u myapp -p err         # Only errors
journalctl -u myapp --no-pager     # Don't paginate

# System logs
journalctl -b                       # Since last boot
journalctl -k                       # Kernel messages
journalctl --disk-usage             # Journal disk usage
```

---

## Key Interview Questions

**Q: How do you set up a Java application to start automatically on server reboot?**
> Create a systemd service file in `/etc/systemd/system/`, configure ExecStart with the java command, set `Restart=on-failure`, then `systemctl enable myapp`.

**Q: What's the difference between `export VAR=value` and `VAR=value`?**
> Without `export`, the variable exists only in the current shell. With `export`, it's available to all child processes (including Java applications you launch). Use `export` when the variable needs to be read by applications.

**Q: How does Spring Boot read environment variables?**
> Spring Boot automatically maps environment variables to properties. `SERVER_PORT` maps to `server.port`, `SPRING_DATASOURCE_URL` maps to `spring.datasource.url`. The convention is: uppercase, dots become underscores.

**Q: How to check why a service failed to start?**
```bash
systemctl status myapp              # Quick status + last few log lines
journalctl -u myapp -n 50          # Last 50 log lines
journalctl -u myapp --since "5 min ago" -p err  # Recent errors
```

**Q: What's the difference between `apt` and `apt-get`?**
> `apt` is the newer, user-friendly command combining `apt-get` and `apt-cache`. It has a progress bar and cleaner output. In scripts, `apt-get` is preferred for stability. For interactive use, `apt` is better.

**Q: How to install Java 17 on Ubuntu?**
```bash
apt update
apt install -y openjdk-17-jdk
java -version
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
```
