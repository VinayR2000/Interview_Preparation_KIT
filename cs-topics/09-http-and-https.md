# HTTP and HTTPS

## HTTP (HyperText Transfer Protocol)

### Overview
- Application layer protocol for the web
- Client-server model (request-response)
- Stateless: Each request is independent
- Text-based protocol (HTTP/1.1), binary (HTTP/2)
- Default port: 80

---

## HTTP Methods

| Method | Purpose | Idempotent | Safe | Body |
|--------|---------|-----------|------|------|
| GET | Retrieve resource | Yes | Yes | No |
| POST | Create resource / submit data | No | No | Yes |
| PUT | Replace entire resource | Yes | No | Yes |
| PATCH | Partial update | No | No | Yes |
| DELETE | Remove resource | Yes | No | Optional |
| HEAD | GET without body (headers only) | Yes | Yes | No |
| OPTIONS | Get supported methods | Yes | Yes | No |

### Idempotent vs Safe
- **Safe**: No side effects on server (GET, HEAD, OPTIONS)
- **Idempotent**: Multiple identical requests = same result as single request
  - GET, PUT, DELETE are idempotent
  - POST is NOT idempotent (creates new resource each time)
  - PATCH is NOT necessarily idempotent

### POST vs PUT
| POST | PUT |
|------|-----|
| Create new resource | Replace entire resource |
| Server decides URI | Client specifies URI |
| Not idempotent | Idempotent |
| `POST /users` (create new) | `PUT /users/123` (replace user 123) |

---

## HTTP Status Codes

| Range | Category | Examples |
|-------|----------|----------|
| 1xx | Informational | 100 Continue, 101 Switching Protocols |
| 2xx | Success | 200 OK, 201 Created, 204 No Content |
| 3xx | Redirection | 301 Moved Permanently, 302 Found, 304 Not Modified |
| 4xx | Client Error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Too Many Requests |
| 5xx | Server Error | 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable |

### Important Status Codes in Detail

| Code | Meaning | When to Use |
|------|---------|-------------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST (resource created) |
| 204 | No Content | Successful DELETE (no body returned) |
| 301 | Moved Permanently | URL changed forever (SEO, bookmarks update) |
| 302 | Found (Temporary Redirect) | Temporary redirect |
| 304 | Not Modified | Cached version is still valid |
| 400 | Bad Request | Invalid request syntax/data |
| 401 | Unauthorized | Not authenticated (need to log in) |
| 403 | Forbidden | Authenticated but not authorized |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Request conflicts with current state |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server crashed/unhandled error |
| 502 | Bad Gateway | Upstream server returned invalid response |
| 503 | Service Unavailable | Server overloaded/maintenance |
| 504 | Gateway Timeout | Upstream server didn't respond in time |

### 401 vs 403
- **401**: "Who are you?" (not authenticated)
- **403**: "I know who you are, but you can't do this" (not authorized)

---

## HTTP Headers

### Request Headers
| Header | Purpose | Example |
|--------|---------|---------|
| Host | Target server | `Host: www.example.com` |
| Authorization | Credentials | `Authorization: Bearer <token>` |
| Content-Type | Body format | `Content-Type: application/json` |
| Accept | Desired response format | `Accept: application/json` |
| User-Agent | Client info | `User-Agent: Mozilla/5.0...` |
| Cookie | Send stored cookies | `Cookie: session=abc123` |
| Cache-Control | Caching directives | `Cache-Control: no-cache` |

### Response Headers
| Header | Purpose | Example |
|--------|---------|---------|
| Content-Type | Body format | `Content-Type: application/json` |
| Set-Cookie | Store cookie | `Set-Cookie: session=abc123; HttpOnly` |
| Cache-Control | Caching rules | `Cache-Control: max-age=3600` |
| Location | Redirect URL | `Location: /new-url` |
| Access-Control-Allow-Origin | CORS | `Access-Control-Allow-Origin: *` |
| ETag | Resource version | `ETag: "33a64df5"` |

---

## HTTP Versions

| Version | Year | Key Features |
|---------|------|--------------|
| HTTP/1.0 | 1996 | New connection per request |
| HTTP/1.1 | 1997 | Keep-alive, pipelining, chunked transfer |
| HTTP/2 | 2015 | Binary, multiplexing, header compression, server push |
| HTTP/3 | 2022 | QUIC (UDP-based), no head-of-line blocking |

### HTTP/1.1 vs HTTP/2
| HTTP/1.1 | HTTP/2 |
|-----------|--------|
| Text-based | Binary framing |
| One request per connection (or pipelining) | Multiplexed streams |
| Header repetition | Header compression (HPACK) |
| No server push | Server push |
| Head-of-line blocking | Stream-level prioritization |

---

## HTTPS

### What is HTTPS?
- HTTP + TLS (Transport Layer Security)
- Encrypts data between client and server
- Default port: 443
- Provides: Confidentiality, Integrity, Authentication

### Why HTTPS?
1. **Confidentiality**: Data encrypted, can't be read by middlemen
2. **Integrity**: Data can't be modified in transit
3. **Authentication**: Server proves its identity via certificate

---

## SSL/TLS

### SSL vs TLS
- SSL (Secure Sockets Layer): Deprecated (SSL 3.0 last version)
- TLS (Transport Layer Security): Current standard (TLS 1.2, 1.3)
- "SSL" is often used colloquially to mean TLS

### TLS Handshake (TLS 1.2)
```
Client                              Server
  |                                   |
  |--- ClientHello ------------------>|  (supported ciphers, TLS version, random)
  |                                   |
  |<-- ServerHello -------------------|  (chosen cipher, random)
  |<-- Certificate -------------------|  (server's public key certificate)
  |<-- ServerHelloDone ---------------|
  |                                   |
  |--- ClientKeyExchange ------------>|  (pre-master secret encrypted with server's public key)
  |--- ChangeCipherSpec ------------->|  (switching to encrypted)
  |--- Finished --------------------->|  (encrypted verification)
  |                                   |
  |<-- ChangeCipherSpec --------------|
  |<-- Finished ----------------------|
  |                                   |
  |=== ENCRYPTED COMMUNICATION ======|
```

### TLS 1.3 Improvements
- 1-RTT handshake (vs 2-RTT in TLS 1.2)
- 0-RTT resumption (for returning clients)
- Removed insecure algorithms
- Forward secrecy mandatory

### Key Concepts
| Term | Description |
|------|-------------|
| Symmetric encryption | Same key for encrypt/decrypt (AES) - fast |
| Asymmetric encryption | Public/private key pair (RSA, ECDSA) - slow |
| Certificate | Contains server's public key, signed by CA |
| CA (Certificate Authority) | Trusted entity that issues certificates |
| Forward secrecy | Compromise of long-term key doesn't compromise past sessions |

### How HTTPS Uses Both Encryption Types
1. **Asymmetric** (handshake): Exchange session key securely
2. **Symmetric** (data transfer): Encrypt actual data with session key
- Reason: Asymmetric is too slow for bulk data

---

## Key Interview Questions

**Q: What happens when you type a URL in the browser?**
> 1. DNS resolution (domain → IP)
> 2. TCP 3-way handshake
> 3. TLS handshake (if HTTPS)
> 4. HTTP request sent
> 5. Server processes, sends response
> 6. Browser renders HTML/CSS/JS
> 7. Additional requests for resources (images, scripts)

**Q: What's the difference between HTTP/1.1 Keep-Alive and HTTP/2 multiplexing?**
> Keep-alive reuses the TCP connection but requests are sequential (one at a time, or pipelined but responses must be ordered). HTTP/2 multiplexing sends multiple requests/responses simultaneously over a single connection without blocking.

**Q: How does the browser verify a server's certificate?**
> Browser checks: 1) Certificate signed by trusted CA, 2) Certificate not expired, 3) Domain matches certificate CN/SAN, 4) Certificate not revoked (CRL/OCSP).

**Q: What is HSTS?**
> HTTP Strict Transport Security. Server tells browser to ONLY use HTTPS for this domain. Prevents downgrade attacks and SSL stripping.
