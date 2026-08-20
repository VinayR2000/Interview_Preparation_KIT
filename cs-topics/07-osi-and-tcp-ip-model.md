# OSI Model & TCP/IP Model

## OSI Model (7 Layers)

| # | Layer | Protocol/Unit | Function | Devices |
|---|-------|---------------|----------|---------|
| 7 | Application | HTTP, FTP, DNS, SMTP | User interface, network services | - |
| 6 | Presentation | SSL/TLS, JPEG, MPEG | Encryption, compression, translation | - |
| 5 | Session | NetBIOS, RPC | Session management, dialog control | - |
| 4 | Transport | TCP, UDP / Segments | End-to-end delivery, flow control | - |
| 3 | Network | IP / Packets | Routing, logical addressing | Router |
| 2 | Data Link | Ethernet / Frames | Framing, MAC addressing, error detection | Switch |
| 1 | Physical | Bits | Physical transmission (cables, signals) | Hub, Cable |

### Memory Aid: "All People Seem To Need Data Processing" (top-down)
### or "Please Do Not Throw Sausage Pizza Away" (bottom-up)

---

## TCP/IP Model (4 Layers)

| # | Layer | OSI Equivalent | Protocols |
|---|-------|----------------|-----------|
| 4 | Application | Application + Presentation + Session | HTTP, FTP, DNS, SMTP, SSH |
| 3 | Transport | Transport | TCP, UDP |
| 2 | Internet | Network | IP, ICMP, ARP |
| 1 | Network Access | Data Link + Physical | Ethernet, Wi-Fi |

---

## OSI vs TCP/IP

| Aspect | OSI | TCP/IP |
|--------|-----|--------|
| Layers | 7 | 4 |
| Approach | Theoretical model | Practical implementation |
| Development | ISO standard | DARPA/DoD |
| Session/Presentation | Separate layers | Combined into Application |
| Usage | Reference/teaching | Actual internet |

---

## Data Encapsulation

```
Application Layer:  [DATA]
Transport Layer:    [TCP Header][DATA]              → Segment
Network Layer:      [IP Header][TCP Header][DATA]   → Packet
Data Link Layer:    [Frame Header][IP][TCP][DATA][FCS] → Frame
Physical Layer:     101010101010...                  → Bits
```

### At Each Layer (Sending)
1. Application creates data
2. Transport adds port numbers (segment)
3. Network adds IP addresses (packet)
4. Data Link adds MAC addresses (frame)
5. Physical converts to electrical signals

### At Each Layer (Receiving) - De-encapsulation
- Reverse process, stripping headers at each layer

---

## Layer Details

### Layer 1 - Physical
- Deals with raw bits over a medium
- Defines: Voltage levels, cable specs, pin layouts, data rates
- Devices: Hub, Repeater, Cable (Cat5, Cat6, Fiber)
- No addressing, no error correction

### Layer 2 - Data Link
- Reliable transfer between adjacent nodes
- MAC addressing (48-bit hardware address)
- Error detection (CRC/FCS)
- Flow control
- Sub-layers:
  - **LLC** (Logical Link Control): Flow control, error checking
  - **MAC** (Media Access Control): Addressing, channel access
- Devices: Switch, Bridge

### Layer 3 - Network
- Routing packets across networks
- Logical addressing (IP addresses)
- Path determination
- Packet fragmentation/reassembly
- Devices: Router, Layer 3 Switch
- Protocols: IP, ICMP, OSPF, BGP

### Layer 4 - Transport
- End-to-end communication
- Port numbers (process identification)
- Segmentation and reassembly
- Flow control, error recovery
- Protocols: TCP (reliable), UDP (fast)

### Layer 5 - Session
- Establish, manage, terminate sessions
- Dialog control (half-duplex, full-duplex)
- Synchronization points
- Examples: NetBIOS, RPC

### Layer 6 - Presentation
- Data format translation
- Encryption/Decryption
- Compression
- Examples: SSL/TLS, JPEG, ASCII, EBCDIC

### Layer 7 - Application
- Interface between network and user applications
- Network services: file transfer, email, web
- Examples: HTTP, FTP, SMTP, DNS

---

## How Data Flows (Full Example)

```
You type "google.com" in browser:

1. Application (HTTP): GET request created
2. Presentation: SSL/TLS encryption
3. Session: TCP session established
4. Transport: Data segmented, port 443 assigned
5. Network: Source/Destination IP added, routing decided
6. Data Link: MAC addresses added, frame created
7. Physical: Electrical signals sent over wire/wireless

→ Passes through routers (Layer 3), switches (Layer 2)
→ Arrives at Google's server, de-encapsulated layer by layer
```

---

## Key Interview Questions

**Q: Why do we need a layered model?**
> Modularity - each layer has specific responsibility. Changes in one layer don't affect others. Easier to design, debug, and standardize.

**Q: What layer does a router operate at?**
> Layer 3 (Network). It reads IP addresses to make routing decisions. A Layer 3 switch can also do routing.

**Q: What's the difference between a switch and a hub?**
> Hub (Layer 1): Broadcasts to all ports, no intelligence. Switch (Layer 2): Reads MAC addresses, forwards only to destination port. Much more efficient.

**Q: Where does encryption happen in OSI?**
> Presentation layer (Layer 6). In practice, TLS operates between Application and Transport layers.

**Q: If two computers are on the same LAN, which layers are involved?**
> Layers 1-2 (Physical and Data Link). No routing needed, just MAC addressing.

**Q: What happens when a packet crosses a router?**
> The Data Link layer headers (MAC addresses) change at each hop. The Network layer headers (IP addresses) remain the same end-to-end (unless NAT is involved).
