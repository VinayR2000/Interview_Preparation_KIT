# Client-Server Fundamentals

## Client-Server Architecture

### Theory
- A distributed architecture where clients request services and servers provide them
- Separation of concerns: client handles UI/presentation, server handles logic/data
- Communication over a network via well-defined protocols

### Diagram
```
┌──────────┐         Request (HTTP)        ┌──────────┐
│          │ ──────────────────────────────>│          │
│  Client  │                                │  Server  │
│ (Browser)│ <──────────────────────────────│  (API)   │
│          │         Response (JSON)        │          │
└──────────┘                                └──────────┘
```

### Request/Response Model
```
Client sends:
┌─────────────────────────────────┐
│ Method: GET /api/users/123      │
│ Headers:                        │
│   Authorization: Bearer <token> │
│   Accept: application/json      │
│ Body: (empty for GET)           │
└─────────────────────────────────┘

Server responds:
┌─────────────────────────────────┐
│ Status: 200 OK                  │
│ Headers:                        │
│   Content-Type: application/json│
│ Body:                           │
│   { "id": 123, "name": "John" }│
└─────────────────────────────────┘
```

---

## TCP/IP Basics

### TCP/IP Model (4 Layers)

| Layer | Protocol | Role |
|-------|----------|------|
| Application | HTTP, DNS, FTP, SMTP | Application-level communication |
| Transport | TCP, UDP | End-to-end delivery, reliability |
| Internet | IP, ICMP | Routing, addressing |
| Network Access | Ethernet, WiFi | Physical transmission |

### TCP vs UDP

| Aspect | TCP | UDP |
|--------|-----|-----|
| Connection | Connection-oriented (3-way handshake) | Connectionless |
| Reliability | Guaranteed delivery, ordering | No guarantees |
| Speed | Slower (overhead) | Faster (minimal overhead) |
| Use Cases | HTTP, APIs, file transfer | Video streaming, DNS, gaming |
| Flow Control | Yes (sliding window) | No |

### TCP 3-Way Handshake
```
Client              Server
  |--- SYN --------→|    (Client initiates)
  |←-- SYN-ACK -----|    (Server acknowledges)
  |--- ACK --------→|    (Connection established)
  |                  |
  |=== DATA FLOW ===|
```

### TCP Connection Teardown (4-Way)
```
Client              Server
  |--- FIN --------→|    (Client wants to close)
  |←-- ACK ---------|    (Server acknowledges)
  |←-- FIN ---------|    (Server also wants to close)
  |--- ACK --------→|    (Connection closed)
```

---

## DNS (Domain Name System)

### Theory
- Translates human-readable domain names to IP addresses
- Hierarchical, distributed database
- Uses UDP port 53 (TCP for zone transfers)

### DNS Resolution Process
```
Browser                DNS Resolver        Root NS       TLD NS (.com)    Auth NS
  |                       |                  |               |              |
  |-- google.com? ------->|                  |               |              |
  |                       |-- Who has .com? ->|               |              |
  |                       |<- TLD NS IP -----|               |              |
  |                       |                                  |              |
  |                       |-- Who has google.com? ---------->|              |
  |                       |<- Auth NS IP -------------------|              |
  |                       |                                                |
  |                       |-- IP for google.com? ------------------------->|
  |                       |<- 142.250.80.46 -------------------------------|
  |<-- 142.250.80.46 ----|
```

### DNS Record Types
| Record | Purpose | Example |
|--------|---------|---------|
| A | Domain → IPv4 | `google.com → 142.250.80.46` |
| AAAA | Domain → IPv6 | `google.com → 2607:f8b0:...` |
| CNAME | Alias to another domain | `www.google.com → google.com` |
| MX | Mail server | `google.com → smtp.google.com` |
| NS | Name server for domain | `google.com → ns1.google.com` |
| TXT | Text records (verification) | SPF, DKIM records |

### DNS Caching Levels
1. Browser cache
2. OS cache (hosts file)
3. Router cache
4. ISP DNS resolver cache
5. Recursive resolver cache

---

## IP Addresses and Ports

### IP Addresses
- **IPv4**: 32-bit, dotted decimal (192.168.1.1), ~4.3 billion addresses
- **IPv6**: 128-bit, hex (2001:0db8::1), virtually unlimited
- **Private IPs**: 10.x.x.x, 172.16-31.x.x, 192.168.x.x (not routable on internet)
- **Public IPs**: Routable on internet, assigned by ISP

### Ports
- 16-bit number (0-65535) identifying a specific service on a host
- Well-known ports: 0-1023

| Port | Service |
|------|---------|
| 80 | HTTP |
| 443 | HTTPS |
| 22 | SSH |
| 3306 | MySQL |
| 5432 | PostgreSQL |
| 6379 | Redis |
| 27017 | MongoDB |
| 9092 | Kafka |
| 8080 | Application server (common) |

---

## TLS/SSL

### Internal Working
1. Client sends ClientHello (supported ciphers, TLS version)
2. Server responds with ServerHello + certificate (public key)
3. Client verifies certificate with CA
4. Client generates pre-master secret, encrypts with server's public key
5. Both derive session key from pre-master secret
6. Symmetric encryption begins with session key

### Why Both Symmetric and Asymmetric?
- **Asymmetric** (RSA/ECDH): Secure key exchange (slow, used once)
- **Symmetric** (AES): Actual data encryption (fast, used for all data)

---

## HTTP Methods

| Method | Purpose | Idempotent | Safe | Has Body |
|--------|---------|-----------|------|----------|
| GET | Retrieve data | ✓ | ✓ | No |
| POST | Create / submit | ✗ | ✗ | Yes |
| PUT | Full replacement | ✓ | ✗ | Yes |
| PATCH | Partial update | ✗ | ✗ | Yes |
| DELETE | Remove resource | ✓ | ✗ | Optional |
| HEAD | Headers only | ✓ | ✓ | No |
| OPTIONS | Supported methods | ✓ | ✓ | No |

---

## HTTP Status Codes

| Range | Category | Key Codes |
|-------|----------|-----------|
| 1xx | Informational | 101 Switching Protocols (WebSocket upgrade) |
| 2xx | Success | 200 OK, 201 Created, 204 No Content |
| 3xx | Redirection | 301 Permanent, 302 Temporary, 304 Not Modified |
| 4xx | Client Error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Rate Limited |
| 5xx | Server Error | 500 Internal, 502 Bad Gateway, 503 Unavailable, 504 Timeout |

---

## Headers, Cookies, Sessions

### Important Headers
| Header | Direction | Purpose |
|--------|-----------|---------|
| Content-Type | Both | MIME type of body |
| Authorization | Request | Authentication credentials |
| Cache-Control | Both | Caching directives |
| Set-Cookie | Response | Store cookie on client |
| Cookie | Request | Send stored cookies |
| X-Request-ID | Both | Request tracing |

### Cookies vs Sessions

| Aspect | Cookies | Sessions |
|--------|---------|----------|
| Storage | Client-side (browser) | Server-side (memory/DB) |
| Size | Limited (~4KB) | Unlimited (server memory) |
| Security | Exposed to client | Server-controlled |
| Scalability | Stateless (client carries data) | Stateful (server stores state) |
| Expiration | Set by server (Expires/Max-Age) | Server-managed |

### Session Flow
```
1. Client → Login request → Server
2. Server creates session (sessionId = abc123, stores user data)
3. Server → Set-Cookie: sessionId=abc123 → Client
4. Client → Cookie: sessionId=abc123 → Server (subsequent requests)
5. Server looks up session by ID, retrieves user data
```

### Cookie Attributes
| Attribute | Purpose |
|-----------|---------|
| HttpOnly | Not accessible via JavaScript (prevents XSS) |
| Secure | Only sent over HTTPS |
| SameSite | Controls cross-site sending (CSRF protection) |
| Domain | Which domain can access |
| Path | Which path can access |
| Max-Age | Expiration time |

---

## Interview Questions

**Q: What happens when you type google.com in the browser and press Enter?**
> 1. Browser checks DNS cache → OS cache → Router → ISP
> 2. DNS resolution: Root NS → TLD NS → Authoritative NS → IP
> 3. TCP 3-way handshake with server
> 4. TLS handshake (certificate exchange, key derivation)
> 5. HTTP GET request sent
> 6. Server processes request, sends response
> 7. Browser parses HTML, builds DOM
> 8. Fetches CSS, JS, images (parallel requests)
> 9. Renders page

**Q: Why is HTTP stateless? How do we maintain state?**
> HTTP is stateless for simplicity and scalability — each request is independent. State is maintained via:
> - Cookies (client-side token)
> - Sessions (server-side state, cookie holds session ID)
> - JWT tokens (stateless auth, client carries claims)
> - URL parameters (rarely used for sensitive data)

**Q: What's the difference between HTTP/1.1 and HTTP/2?**
> HTTP/1.1: Text-based, one request per connection (or pipelined), header repetition.
> HTTP/2: Binary framing, multiplexed streams (multiple requests on one connection), header compression (HPACK), server push. Solves head-of-line blocking at HTTP level (not TCP level).

**Q: How does TLS prevent man-in-the-middle attacks?**
> 1. Server presents certificate signed by trusted CA
> 2. Client verifies certificate chain up to root CA
> 3. Certificate contains server's public key
> 4. Key exchange ensures only client and server share session key
> 5. If attacker intercepts, they can't decrypt without private key

**Q: What's the difference between forward proxy and reverse proxy?**
> Forward proxy: Client-side, hides clients from servers (VPN, corporate proxy).
> Reverse proxy: Server-side, hides servers from clients (Nginx, load balancer). Clients think they're talking to one server.

---

## Common Mistakes
- Confusing 401 (not authenticated) with 403 (not authorized)
- Not understanding TCP vs UDP trade-offs
- Thinking DNS uses TCP (it uses UDP, except for zone transfers)
- Forgetting that HTTP/2 still suffers from TCP head-of-line blocking
- Not considering DNS TTL in failover strategies
- Using cookies without HttpOnly/Secure flags

---

## Best Practices
- Always use HTTPS in production
- Set appropriate Cache-Control headers
- Use connection pooling for backend services
- Implement proper DNS TTL for failover
- Use HTTP/2 or HTTP/3 where possible
- Set cookie security attributes (HttpOnly, Secure, SameSite)
- Use Content-Type correctly for all responses

---

## Production Considerations
- CDN for static content (reduces latency via edge nodes)
- DNS failover with health checks
- TLS certificate rotation and monitoring
- Connection pool sizing for downstream services
- Keep-alive settings for connection reuse
- HTTP timeout configurations (connect, read, write)
- Rate limiting at the network edge

---

## Related Topics
- API Design (next topic)
- Load Balancing
- CORS
- WebSockets
- gRPC
