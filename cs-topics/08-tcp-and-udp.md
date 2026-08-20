# TCP and UDP

## TCP (Transmission Control Protocol)

### Characteristics
- **Connection-oriented**: Must establish connection before data transfer
- **Reliable**: Guarantees delivery (acknowledgments, retransmission)
- **Ordered**: Data arrives in the order it was sent (sequence numbers)
- **Flow control**: Prevents sender from overwhelming receiver (sliding window)
- **Congestion control**: Adapts to network capacity
- **Full-duplex**: Both sides can send simultaneously
- **Byte-stream oriented**: No message boundaries

### TCP Header (20-60 bytes)
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        Sequence Number                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Acknowledgment Number                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Offset| Reserved |U|A|P|R|S|F|            Window             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|           Checksum            |         Urgent Pointer        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### TCP Flags
| Flag | Name | Purpose |
|------|------|---------|
| SYN | Synchronize | Initiate connection |
| ACK | Acknowledge | Confirm receipt |
| FIN | Finish | Terminate connection |
| RST | Reset | Abort connection |
| PSH | Push | Send data immediately |
| URG | Urgent | Priority data |

---

## 3-Way Handshake (Connection Establishment)

```
Client                          Server
  |                               |
  |---- SYN (seq=x) ------------>|  Step 1: Client sends SYN
  |                               |
  |<--- SYN+ACK (seq=y, ack=x+1)-|  Step 2: Server responds SYN+ACK
  |                               |
  |---- ACK (ack=y+1) ---------->|  Step 3: Client confirms
  |                               |
  |===== CONNECTION ESTABLISHED ==|
```

### Why 3-Way? (Not 2-Way)
- Prevents old duplicate connection requests from being accepted
- Both sides confirm they can send AND receive
- Synchronizes sequence numbers

### SYN Flood Attack
- Attacker sends many SYNs without completing handshake
- Server runs out of resources maintaining half-open connections
- Defense: SYN cookies, rate limiting, timeouts

---

## 4-Way Connection Termination

```
Client                          Server
  |                               |
  |---- FIN (seq=u) ------------>|  Step 1: Client wants to close
  |                               |
  |<--- ACK (ack=u+1) ----------|  Step 2: Server acknowledges
  |                               |  (Server may still send data)
  |<--- FIN (seq=v) ------------|  Step 3: Server ready to close
  |                               |
  |---- ACK (ack=v+1) --------->|  Step 4: Client confirms
  |                               |
  |     TIME_WAIT (2*MSL)        |  Client waits before fully closing
```

### Why 4-Way? (Not 3-Way)
- Connection is half-duplex close: each direction closed independently
- Server may still have data to send after receiving FIN
- Server's ACK and FIN are separate (unlike SYN+ACK in handshake)

### TIME_WAIT State
- Client waits 2×MSL (Maximum Segment Lifetime, typically 60s)
- Reasons:
  - Ensure last ACK reaches server (retransmit if lost)
  - Allow old duplicate segments to expire

---

## TCP Flow Control

### Sliding Window
- Receiver advertises **window size** (how much data it can accept)
- Sender limits unacknowledged data to window size
- Window slides forward as ACKs arrive

```
Sender:
[sent+acked] [sent, not acked] [can send] [cannot send yet]
              |<-- window -->|
```

### Zero Window
- Receiver sets window = 0 (buffer full)
- Sender stops sending, periodically sends **window probes**
- Receiver sends window update when ready

---

## TCP Congestion Control

### Algorithms
| Phase | Description |
|-------|-------------|
| **Slow Start** | Exponential growth (cwnd doubles each RTT) |
| **Congestion Avoidance** | Linear growth (cwnd += 1 each RTT) after threshold |
| **Fast Retransmit** | Retransmit on 3 duplicate ACKs (don't wait for timeout) |
| **Fast Recovery** | After fast retransmit, halve cwnd instead of reset to 1 |

### Slow Start → Congestion Avoidance
```
cwnd (congestion window):
  Start: cwnd = 1 MSS
  Slow Start: cwnd doubles each RTT (1, 2, 4, 8, 16...)
  When cwnd >= ssthresh: Switch to Congestion Avoidance
  Congestion Avoidance: cwnd += 1 each RTT (linear)
  
On timeout: ssthresh = cwnd/2, cwnd = 1 (restart slow start)
On 3 dup ACKs: ssthresh = cwnd/2, cwnd = ssthresh (fast recovery)
```

---

## UDP (User Datagram Protocol)

### Characteristics
- **Connectionless**: No connection setup
- **Unreliable**: No guarantee of delivery
- **Unordered**: Packets may arrive out of order
- **No flow control**: Sender sends at any rate
- **No congestion control**: Doesn't adapt to network
- **Message-oriented**: Preserves message boundaries
- **Low overhead**: Simple header (8 bytes)

### UDP Header (8 bytes)
```
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Source Port          |       Destination Port        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|            Length             |           Checksum            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## TCP vs UDP

| Aspect | TCP | UDP |
|--------|-----|-----|
| Connection | Connection-oriented | Connectionless |
| Reliability | Guaranteed delivery | Best effort |
| Ordering | Ordered (sequence numbers) | No ordering |
| Speed | Slower (overhead) | Faster (minimal overhead) |
| Header size | 20-60 bytes | 8 bytes |
| Flow control | Yes (sliding window) | No |
| Congestion control | Yes | No |
| Broadcasting | No | Yes |
| Message boundary | Byte stream (no boundary) | Preserved |
| Use cases | Web, email, file transfer | Streaming, gaming, DNS |

### When to Use TCP
- File transfer (FTP)
- Web browsing (HTTP/HTTPS)
- Email (SMTP, IMAP)
- Remote login (SSH)
- Database queries
- Any case where data integrity > speed

### When to Use UDP
- Video/audio streaming (live)
- Online gaming (real-time)
- DNS queries (small, single request-response)
- DHCP
- VoIP
- IoT sensor data
- Any case where speed > reliability

---

## TCP Retransmission

### Timeout-based
- Sender sets timer for each segment
- If ACK not received within RTO (Retransmission Timeout): retransmit
- RTO calculated from RTT samples: RTO = SRTT + 4×RTTVAR

### Fast Retransmit
- On receiving 3 duplicate ACKs → retransmit immediately
- Don't wait for timeout
- Indicates a single packet loss (not network failure)

---

## Key Interview Questions

**Q: Can TCP guarantee delivery?**
> TCP guarantees delivery to the receiving socket buffer (through retransmission). It cannot guarantee the application reads it or that data survives if the receiver crashes after ACK.

**Q: What happens if the last ACK in 4-way handshake is lost?**
> Server will retransmit FIN. Client's TIME_WAIT ensures it can re-send ACK. After 2×MSL timeout, connection is cleaned up.

**Q: Why does DNS use UDP?**
> DNS queries are small (fit in one packet), fast response needed, no connection setup overhead. If response is too large (>512 bytes), DNS falls back to TCP.

**Q: Can you build reliable communication over UDP?**
> Yes. Examples: QUIC (HTTP/3), game engines with custom reliability layers. You implement your own ACK/retransmit/ordering on top of UDP.

**Q: What is head-of-line blocking in TCP?**
> If a packet is lost, all subsequent packets (even if received) must wait for retransmission. This blocks the entire stream. UDP/QUIC can deliver independent streams without this problem.

**Q: What is Nagle's Algorithm?**
> Combines small TCP segments into larger ones to reduce overhead. Collects data until: previous segment is ACKed OR enough data to fill a segment. Can be disabled with TCP_NODELAY for latency-sensitive apps.
