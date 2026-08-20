# DNS and Networking Concepts

## IP Address

### IPv4
- 32-bit address, written as four octets: `192.168.1.1`
- Total addresses: ~4.3 billion (2^32)
- Running out → need IPv6

#### Classes
| Class | Range | Default Mask | Use |
|-------|-------|-------------|-----|
| A | 1.0.0.0 - 126.255.255.255 | /8 (255.0.0.0) | Large networks |
| B | 128.0.0.0 - 191.255.255.255 | /16 (255.255.0.0) | Medium networks |
| C | 192.0.0.0 - 223.255.255.255 | /24 (255.255.255.0) | Small networks |
| D | 224.0.0.0 - 239.255.255.255 | - | Multicast |
| E | 240.0.0.0 - 255.255.255.255 | - | Reserved |

#### Private IP Ranges
| Class | Range |
|-------|-------|
| A | 10.0.0.0 - 10.255.255.255 |
| B | 172.16.0.0 - 172.31.255.255 |
| C | 192.168.0.0 - 192.168.255.255 |

### IPv6
- 128-bit address: `2001:0db8:85a3:0000:0000:8a2e:0370:7334`
- Total addresses: 2^128 (practically unlimited)
- No NAT needed (every device gets a public IP)
- Built-in IPsec
- No broadcast (uses multicast)

---

## MAC Address
- 48-bit hardware address (burned into NIC)
- Format: `AA:BB:CC:DD:EE:FF`
- First 3 bytes: OUI (manufacturer)
- Last 3 bytes: Device-specific
- Used at Layer 2 (Data Link)
- Does NOT change when moving between networks

### IP vs MAC
| IP Address | MAC Address |
|-----------|-------------|
| Layer 3 (Network) | Layer 2 (Data Link) |
| Logical (assigned) | Physical (hardware) |
| Changes with network | Doesn't change |
| Used for routing across networks | Used for local delivery |

---

## ARP (Address Resolution Protocol)

### Purpose
- Resolves IP address → MAC address
- Used when a device knows destination IP but needs MAC to send frame

### How ARP Works
```
1. Device A wants to send to IP 192.168.1.5
2. A checks ARP cache (local table)
3. If not found: A broadcasts ARP Request
   "Who has 192.168.1.5? Tell 192.168.1.1"
4. All devices on LAN receive broadcast
5. Device with 192.168.1.5 responds (unicast ARP Reply)
   "192.168.1.5 is at MAC AA:BB:CC:DD:EE:FF"
6. A caches the mapping and sends the frame
```

### ARP Cache
- Stores IP→MAC mappings
- Entries expire (timeout, typically 20 min)
- `arp -a` to view on most systems

---

## Routing

### What is Routing?
- Process of selecting a path for network traffic
- Routers forward packets based on destination IP
- Uses routing tables

### Routing Table Entry
| Destination | Subnet Mask | Next Hop | Interface | Metric |
|-------------|-------------|----------|-----------|--------|
| 192.168.1.0 | /24 | Direct | eth0 | 0 |
| 10.0.0.0 | /8 | 192.168.1.1 | eth0 | 1 |
| 0.0.0.0 | /0 | 192.168.1.1 | eth0 | 1 |

### Static vs Dynamic Routing
| Static | Dynamic |
|--------|---------|
| Manually configured | Protocols discover routes |
| Doesn't adapt | Adapts to changes |
| Good for small networks | Good for large networks |
| Less overhead | More overhead |

### Routing Protocols
| Protocol | Type | Algorithm | Use |
|----------|------|-----------|-----|
| RIP | Distance Vector | Bellman-Ford | Small networks |
| OSPF | Link State | Dijkstra | Enterprise |
| BGP | Path Vector | Policy-based | Internet backbone |

---

## DNS (Domain Name System)

### What is DNS?
- Translates domain names to IP addresses
- Distributed hierarchical database
- Uses UDP port 53 (TCP for large responses/zone transfers)

### DNS Resolution Process
```
Browser → Local DNS Cache → OS DNS Cache → Recursive Resolver
→ Root Server (.com) → TLD Server (.example.com) → Authoritative Server
→ Returns IP Address
```

### DNS Record Types
| Type | Purpose | Example |
|------|---------|---------|
| A | Domain → IPv4 | `example.com → 93.184.216.34` |
| AAAA | Domain → IPv6 | `example.com → 2606:2800:...` |
| CNAME | Alias to another domain | `www.example.com → example.com` |
| MX | Mail server | `example.com → mail.example.com` |
| NS | Name server | `example.com → ns1.example.com` |
| TXT | Text record | SPF, DKIM verification |
| PTR | IP → Domain (reverse DNS) | `93.184.216.34 → example.com` |
| SOA | Zone authority info | Primary NS, admin email, serial |

### DNS Caching
- Browser cache (seconds to minutes)
- OS cache
- Recursive resolver cache
- TTL (Time To Live) controls cache duration

---

## CDN (Content Delivery Network)

### What is a CDN?
- Geographically distributed network of proxy servers
- Serves content from the nearest location to the user
- Reduces latency, load on origin server

### How CDN Works
```
User (India) → CDN Edge Server (India) → Cached Content
                    ↓ (if not cached)
               Origin Server (USA)
```

### Benefits
- Lower latency (geographic proximity)
- Reduced bandwidth on origin
- DDoS protection
- High availability (redundancy)
- SSL/TLS termination at edge

---

## Cookies, Sessions, JWT

### Cookies
- Small data stored in browser, sent with every request
- Set by server: `Set-Cookie: name=value; HttpOnly; Secure; SameSite`
- Attributes:
  - **HttpOnly**: Can't be accessed by JavaScript (XSS protection)
  - **Secure**: Only sent over HTTPS
  - **SameSite**: CSRF protection (Strict, Lax, None)
  - **Expires/Max-Age**: When cookie expires

### Sessions
- Server-side storage of user state
- Client holds only session ID (in cookie)
- Server looks up session data using ID

```
Client                    Server
  |--- Request + session_id -->|
  |                            | → Look up session_id in session store
  |<-- Response + data --------|
```

### JWT (JSON Web Token)
- Self-contained token with claims
- Stateless: Server doesn't need to store session
- Structure: `header.payload.signature`

```
Header: {"alg": "HS256", "typ": "JWT"}
Payload: {"sub": "user123", "exp": 1234567890, "roles": ["admin"]}
Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
```

### Cookie-Session vs JWT
| Cookie + Session | JWT |
|-----------------|-----|
| Stateful (server stores session) | Stateless (token is self-contained) |
| Easy to invalidate | Hard to invalidate (until expiry) |
| Server memory/storage needed | No server storage |
| Works well for monolith | Works well for microservices |
| Session ID is opaque | Token contains user data |

---

## OAuth2

### What is OAuth2?
- Authorization framework (NOT authentication)
- Allows third-party apps to access resources without sharing credentials
- Roles: Resource Owner, Client, Authorization Server, Resource Server

### Common Flows
| Flow | Use Case |
|------|----------|
| Authorization Code | Server-side web apps (most secure) |
| PKCE | Mobile/SPA (public clients) |
| Client Credentials | Machine-to-machine |
| Implicit | Deprecated (was for SPAs) |

---

## CORS (Cross-Origin Resource Sharing)

### What is CORS?
- Browser security mechanism
- Restricts web pages from making requests to different origins
- Origin = protocol + domain + port

### How CORS Works
```
Frontend (http://localhost:3000) → Backend (http://api.example.com)

1. Browser sends preflight OPTIONS request:
   Origin: http://localhost:3000
   Access-Control-Request-Method: POST

2. Server responds with allowed origins:
   Access-Control-Allow-Origin: http://localhost:3000
   Access-Control-Allow-Methods: GET, POST

3. If allowed, browser sends actual request
```

### Simple vs Preflight Requests
- **Simple**: GET/HEAD/POST with standard headers → no preflight
- **Preflight**: Non-simple methods/headers → OPTIONS request first

---

## WebSocket

### What is WebSocket?
- Full-duplex communication over single TCP connection
- Persistent connection (unlike HTTP request-response)
- Low latency, real-time communication
- Starts as HTTP upgrade request

### WebSocket vs HTTP
| HTTP | WebSocket |
|------|-----------|
| Request-response | Full-duplex |
| New connection per request (or keep-alive) | Persistent connection |
| Client initiates | Either side can send |
| Stateless | Stateful |
| Higher overhead | Lower overhead after handshake |

### Use Cases
- Chat applications
- Live notifications
- Real-time dashboards
- Online gaming
- Collaborative editing

---

## Proxy, Reverse Proxy, Load Balancer

### Proxy (Forward Proxy)
- Sits between client and internet
- Client knows about proxy, uses it intentionally
- Use: Anonymity, caching, filtering, bypassing restrictions

### Reverse Proxy
- Sits between internet and servers
- Client doesn't know about it
- Use: Load balancing, SSL termination, caching, security

### Load Balancer
- Distributes traffic across multiple servers
- Algorithms:
  - **Round Robin**: Rotate through servers
  - **Least Connections**: Send to least busy server
  - **IP Hash**: Same client → same server (sticky)
  - **Weighted**: Higher capacity servers get more traffic

### Comparison
```
Client → [Forward Proxy] → Internet → [Reverse Proxy/LB] → Servers
         (client-side)                   (server-side)
```

---

## Connection Pooling & Keep-Alive

### Connection Pooling
- Reuse existing connections instead of creating new ones
- Avoids overhead of TCP handshake + TLS handshake for each request
- Common in database connections, HTTP clients

### HTTP Keep-Alive
- Reuse TCP connection for multiple HTTP requests
- `Connection: keep-alive` header
- Reduces latency (no repeated handshakes)
- Connection closes after timeout or max requests

---

## gRPC

### What is gRPC?
- High-performance RPC framework by Google
- Uses HTTP/2 + Protocol Buffers (binary serialization)
- Supports streaming (unary, server, client, bidirectional)

### gRPC vs REST
| REST | gRPC |
|------|------|
| JSON (text) | Protocol Buffers (binary) |
| HTTP/1.1 or 2 | HTTP/2 only |
| Request-response | Streaming supported |
| Browser-friendly | Needs proxy for browser |
| Loosely coupled | Strongly typed (proto files) |
| Slower | Faster (10x for serialization) |

---

## Key Interview Questions

**Q: What's the difference between proxy and reverse proxy?**
> Forward proxy acts on behalf of clients (hides client identity). Reverse proxy acts on behalf of servers (hides server identity). Client configures forward proxy; server deploys reverse proxy.

**Q: How does DNS work when you visit google.com?**
> Browser cache → OS cache → Recursive resolver → Root DNS (.) → TLD DNS (.com) → Authoritative DNS (google.com) → Returns IP. Cached at each level per TTL.

**Q: Why use gRPC over REST for microservices?**
> Binary serialization (smaller/faster), built-in streaming, strong typing with proto files, HTTP/2 multiplexing. Better for high-throughput internal communication. REST better for public APIs.
