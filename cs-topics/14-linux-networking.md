# Linux Networking Commands

## Network Diagnostics

### ping - Test Connectivity
```bash
ping google.com             # Continuous ping (Ctrl+C to stop)
ping -c 5 google.com        # Send 5 packets only
ping -i 0.5 host            # Interval of 0.5 seconds
ping -W 3 host              # Timeout after 3 seconds
```

**What it tests**: Network reachability (ICMP echo request/reply)
**Common issues**: 
- `Destination Host Unreachable` = routing problem
- `Request timed out` = host down or firewall blocking ICMP
- High latency = network congestion

---

### netstat - Network Statistics (Legacy)
```bash
netstat -tlnp    # TCP listening ports with PID
netstat -ulnp    # UDP listening ports with PID
netstat -an      # All connections, numeric addresses
netstat -rn      # Routing table
netstat -i       # Network interfaces
```

#### Flags
| Flag | Meaning |
|------|---------|
| -t | TCP connections |
| -u | UDP connections |
| -l | Listening only |
| -n | Numeric (don't resolve hostnames) |
| -p | Show PID/program name |
| -a | All connections |
| -r | Routing table |

---

### ss - Socket Statistics (Modern netstat)
```bash
ss -tlnp         # TCP listening ports with process
ss -ulnp         # UDP listening ports
ss -s            # Summary statistics
ss -t state established  # Only established connections
ss -t dst :443   # Connections to port 443
```

### ss vs netstat
| ss | netstat |
|----|---------|
| Faster (reads from kernel directly) | Slower (reads /proc) |
| More features | Being deprecated |
| Modern Linux | Legacy |

---

### curl - HTTP Client
```bash
# Basic requests
curl https://api.example.com                     # GET request
curl -X POST -d '{"name":"test"}' URL            # POST with data
curl -X PUT -d '{"name":"updated"}' URL          # PUT request
curl -X DELETE URL                                # DELETE request

# Headers
curl -H "Authorization: Bearer token" URL        # Custom header
curl -H "Content-Type: application/json" URL     # Set content type
curl -I URL                                      # Response headers only

# Options
curl -v URL                     # Verbose (show request/response headers)
curl -o output.html URL         # Save to file
curl -O URL                     # Save with original filename
curl -L URL                     # Follow redirects
curl -k URL                     # Skip SSL verification
curl -u user:pass URL           # Basic authentication
curl -w "%{http_code}" URL      # Show only status code
curl --max-time 10 URL          # Timeout after 10 seconds
```

---

### telnet - Test Port Connectivity
```bash
telnet host 80          # Test if port 80 is open
telnet host 3306        # Test MySQL port
```
- If connection succeeds: port is open
- If "Connection refused": port is closed
- If timeout: firewall blocking or host unreachable

---

## Common Networking Scenarios

### Find What's Running on a Port
```bash
ss -tlnp | grep :8080
# or
lsof -i :8080
# or
fuser 8080/tcp
```

### Check if a Remote Port is Open
```bash
nc -zv host 443         # Using netcat
telnet host 443         # Using telnet
curl -v host:443        # Using curl
```

### Check DNS Resolution
```bash
nslookup domain.com
dig domain.com
host domain.com
```

### Check Network Interfaces
```bash
ip addr                 # Show all interfaces and IPs
ip link                 # Show interface status
ifconfig                # Legacy (still widely used)
```

### Trace Route to Host
```bash
traceroute google.com   # Show path to destination
traceroute -n host      # Numeric only (faster)
mtr host                # Combines ping + traceroute (live)
```

---

## Firewall Basics (iptables/firewalld)

### iptables
```bash
iptables -L                         # List all rules
iptables -A INPUT -p tcp --dport 80 -j ACCEPT   # Allow port 80
iptables -A INPUT -p tcp --dport 22 -j DROP      # Block SSH
iptables -D INPUT 1                 # Delete rule 1
```

### firewalld (Modern)
```bash
firewall-cmd --list-all                          # Show all rules
firewall-cmd --add-port=8080/tcp --permanent     # Open port
firewall-cmd --reload                            # Apply changes
```

---

## Key Interview Questions

**Q: How to check if a service is running on port 8080?**
```bash
ss -tlnp | grep :8080
# Shows PID and process name listening on that port
```

**Q: How to troubleshoot "Connection refused" vs "Connection timed out"?**
> - Connection refused: Port is reachable but no service listening (or service crashed)
> - Connection timed out: Firewall blocking, host unreachable, or wrong IP/port

**Q: How to check which process is using the most bandwidth?**
```bash
iftop        # Real-time bandwidth per connection
nethogs      # Bandwidth per process
```

**Q: Difference between `curl` and `wget`?**
> curl: More versatile, supports many protocols, outputs to stdout by default, can upload. wget: Simpler, built for downloading, can mirror sites, supports recursive download, better for batch downloads.
