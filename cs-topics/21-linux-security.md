# Linux Security

## SSH — Secure Shell ⭐⭐⭐

### SSH Basics

```bash
# Connect to remote server
ssh user@hostname
ssh user@192.168.1.100
ssh -p 2222 user@host              # Custom port

# Execute command remotely
ssh user@host "ps aux | grep java"

# Copy files
scp file.txt user@host:/path/      # Local → Remote
scp user@host:/path/file.txt .     # Remote → Local
scp -r dir/ user@host:/path/       # Directory (recursive)
```

---

### SSH Key Authentication ⭐⭐⭐

**Why keys over passwords?**
- More secure (4096-bit key vs guessable password)
- No brute-force vulnerability
- Enables passwordless automation (CI/CD)

```bash
# Generate SSH key pair
ssh-keygen -t rsa -b 4096 -C "user@email.com"
ssh-keygen -t ed25519 -C "user@email.com"    # Modern, preferred

# Generated files
~/.ssh/id_rsa           # Private key (NEVER share!)
~/.ssh/id_rsa.pub       # Public key (share freely)

# Copy public key to server
ssh-copy-id user@host
# Or manually:
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys   # On remote server

# Permissions MUST be correct
chmod 700 ~/.ssh
chmod 600 ~/.ssh/id_rsa              # Private key: owner read only
chmod 644 ~/.ssh/id_rsa.pub          # Public key
chmod 600 ~/.ssh/authorized_keys
```

### SSH Config File
```bash
# ~/.ssh/config — simplify SSH connections
Host prod-server
    HostName 10.0.1.50
    User deploy
    Port 22
    IdentityFile ~/.ssh/prod_key

Host staging
    HostName 10.0.2.50
    User deploy
    IdentityFile ~/.ssh/staging_key

# Now connect with just:
ssh prod-server
```

---

### SSH Security Hardening

```bash
# /etc/ssh/sshd_config — SSH server configuration
PermitRootLogin no                   # Disable root login
PasswordAuthentication no            # Keys only
MaxAuthTries 3                       # Limit attempts
Port 2222                            # Change default port
AllowUsers deploy admin              # Whitelist users
Protocol 2                           # Only SSH protocol 2
ClientAliveInterval 300              # Timeout idle sessions
ClientAliveCountMax 2

# Restart SSH after changes
systemctl restart sshd
```

---

## sudo — Superuser Access ⭐⭐⭐

### Understanding sudo

```bash
# Run as root
sudo command
sudo systemctl restart nginx

# Run as specific user
sudo -u postgres psql

# Edit sudoers file (safely!)
sudo visudo

# Check sudo permissions
sudo -l                              # List your sudo privileges
```

### Sudoers Configuration
```bash
# /etc/sudoers (edit with visudo ONLY)

# User privilege specification
root    ALL=(ALL:ALL) ALL
deploy  ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart myapp
john    ALL=(ALL) ALL

# Group sudo access
%developers ALL=(ALL) NOPASSWD: /usr/bin/docker
%admin      ALL=(ALL) ALL
```

### sudo vs su
| Command | Purpose | Authentication |
|---------|---------|---------------|
| `sudo command` | Run single command as root | Your password |
| `sudo -i` | Start root shell | Your password |
| `su -` | Switch to root user | Root's password |
| `su - user` | Switch to another user | That user's password |

---

## Firewall ⭐⭐

### iptables (Traditional)

```bash
# List rules
iptables -L -n                       # List all rules (numeric)
iptables -L -n --line-numbers        # With line numbers

# Allow incoming SSH
iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# Allow HTTP/HTTPS
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# Allow Spring Boot port
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT

# Block specific IP
iptables -A INPUT -s 192.168.1.100 -j DROP

# Allow loopback
iptables -A INPUT -i lo -j ACCEPT

# Allow established connections
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# Default deny
iptables -P INPUT DROP

# Delete a rule
iptables -D INPUT 3                  # Delete rule #3

# Save rules (persist across reboot)
iptables-save > /etc/iptables/rules.v4
```

---

### ufw (Uncomplicated Firewall — Ubuntu)

```bash
# Enable/disable
ufw enable
ufw disable
ufw status verbose

# Allow ports
ufw allow 22/tcp                     # SSH
ufw allow 80/tcp                     # HTTP
ufw allow 443/tcp                    # HTTPS
ufw allow 8080/tcp                   # Spring Boot

# Allow from specific IP
ufw allow from 10.0.0.0/24 to any port 5432   # Allow DB from internal network

# Deny
ufw deny 3306/tcp                    # Block MySQL from outside

# Delete rule
ufw delete allow 8080/tcp

# Default policies
ufw default deny incoming
ufw default allow outgoing
```

---

### firewalld (RHEL/CentOS)

```bash
# Status
firewall-cmd --state
firewall-cmd --list-all

# Add ports
firewall-cmd --add-port=8080/tcp --permanent
firewall-cmd --add-service=http --permanent
firewall-cmd --add-service=https --permanent

# Remove
firewall-cmd --remove-port=8080/tcp --permanent

# Apply changes
firewall-cmd --reload

# Zones
firewall-cmd --list-all-zones
firewall-cmd --get-active-zones
```

---

## File Security

### Important Permission Patterns

```bash
# Sensitive files should have restricted permissions
chmod 600 ~/.ssh/id_rsa             # SSH private key
chmod 600 /etc/shadow               # Password hashes
chmod 600 /opt/myapp/.env           # Application secrets
chmod 644 /etc/passwd               # User info (readable)
chmod 755 /opt/myapp/               # Application directory
chmod 400 /etc/ssl/private/key.pem  # SSL private key
```

### Find Security Issues
```bash
# World-writable files (security risk)
find / -type f -perm -o+w 2>/dev/null

# SUID files (runs as owner, potential escalation)
find / -type f -perm -4000 2>/dev/null

# Files with no owner
find / -nouser -o -nogroup 2>/dev/null

# World-readable sensitive files
find /etc -name "*.conf" -perm -o+r -exec grep -l "password" {} \;
```

---

## SELinux Basics

### What is SELinux?
- Security-Enhanced Linux (Mandatory Access Control)
- Default on RHEL/CentOS/Amazon Linux
- Adds labels to files, processes, ports
- Even root is constrained by SELinux policies

```bash
# Check status
getenforce                          # Enforcing/Permissive/Disabled
sestatus                            # Detailed status

# Modes
setenforce 0                        # Set Permissive (temporary)
setenforce 1                        # Set Enforcing (temporary)

# Permanent change: edit /etc/selinux/config
# SELINUX=enforcing|permissive|disabled

# Check file context
ls -Z /var/www/html/
# -rw-r--r--. root root unconfined_u:object_r:httpd_sys_content_t:s0 index.html

# Fix context after moving files
restorecon -Rv /var/www/html/

# Allow a port for a service
semanage port -a -t http_port_t -p tcp 8080
```

### Common SELinux Issues with Java Apps
```bash
# Application can't bind to non-standard port
# Solution:
semanage port -a -t http_port_t -p tcp 9090

# Application can't write to a directory
# Check audit log
ausearch -m AVC -ts recent
# Fix with:
chcon -R -t httpd_sys_rw_content_t /opt/myapp/logs/
```

---

## User and Group Management

```bash
# Add user
useradd -m -s /bin/bash john        # Create with home dir and shell
useradd -r -s /sbin/nologin appuser # System user (no login)

# Set password
passwd john

# Modify user
usermod -aG docker john             # Add to docker group
usermod -L john                     # Lock account
usermod -U john                     # Unlock account

# Delete user
userdel -r john                     # Delete user + home directory

# Groups
groupadd developers
usermod -aG developers john         # Add user to group
groups john                         # Show user's groups
id john                             # UID, GID, groups

# View users/groups
cat /etc/passwd                     # All users
cat /etc/group                      # All groups
getent passwd john                  # Specific user info
```

---

## Security Best Practices for Java Deployments

### Principle of Least Privilege
```bash
# Create dedicated application user (no shell, no login)
useradd -r -s /sbin/nologin -d /opt/myapp appuser

# Application files owned by appuser
chown -R appuser:appuser /opt/myapp
chmod 750 /opt/myapp
chmod 640 /opt/myapp/application.properties

# Run application as non-root user (in systemd service)
# [Service]
# User=appuser
# Group=appuser
```

### Secrets Management
```bash
# NEVER store secrets in:
# - Git repository
# - application.properties committed to VCS
# - Environment variables visible in /proc

# Better approaches:
# 1. Environment files with restricted permissions
chmod 600 /opt/myapp/.env
# 2. Secrets management (AWS Secrets Manager, Vault)
# 3. Encrypted environment files
```

---

## Key Interview Questions

**Q: How to secure SSH access to a production server?**
> 1. Disable root login (`PermitRootLogin no`)
> 2. Use key-based authentication, disable passwords
> 3. Change default port (obscurity, not security)
> 4. Use `AllowUsers` to whitelist
> 5. Set up fail2ban to block brute force
> 6. Keep SSH updated

**Q: How to check if someone unauthorized logged in?**
```bash
# Recent logins
last
lastlog

# Failed login attempts
grep "Failed password" /var/log/auth.log
lastb

# Currently logged in users
who
w
```

**Q: What's the difference between iptables and security groups (AWS)?**
> iptables operates at the OS level on the instance. Security groups operate at the network level (VPC) before traffic reaches the instance. Best practice: use both. Security groups for broad rules, iptables for fine-grained control.

**Q: How to ensure your Java application runs with minimal privileges?**
> 1. Create a dedicated non-root user with no shell
> 2. Restrict file permissions (750 for dirs, 640 for files)
> 3. Use capabilities instead of root when possible
> 4. Bind to ports >1024 (no root needed) or use authbind
> 5. Restrict network access with firewall rules
> 6. Use SELinux/AppArmor profiles
