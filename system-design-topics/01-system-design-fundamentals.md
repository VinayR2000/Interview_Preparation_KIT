# System Design Fundamentals

## What is System Design?

### Theory
- System design is the process of defining the architecture, components, modules, interfaces, and data flow of a system to satisfy specified requirements
- It involves making trade-offs between competing concerns (cost, performance, scalability, complexity)
- Two main categories:
  - **HLD (High-Level Design)**: Architecture, components, data flow between services
  - **LLD (Low-Level Design)**: Class design, interfaces, data structures, algorithms within a component

### HLD vs LLD

| Aspect | HLD | LLD |
|--------|-----|-----|
| Focus | Architecture, services, data flow | Classes, interfaces, methods |
| Scope | Entire system | Single component/module |
| Audience | Architects, senior engineers | Developers implementing the code |
| Output | Architecture diagrams, API contracts | Class diagrams, sequence diagrams |
| Examples | "How does YouTube handle video uploads?" | "Design the Parking Lot class structure" |
| Concerns | Scalability, availability, consistency | SOLID principles, design patterns |

---

## Requirements

### Functional Requirements (FR)
- What the system DOES
- Features, behaviors, use cases
- Example: "Users can upload videos", "System sends notifications"

### Non-Functional Requirements (NFR)
- How WELL the system performs
- Quality attributes
- Example: "System handles 10K requests/sec", "99.99% uptime"

| Category | Examples |
|----------|----------|
| Performance | Response time < 200ms, throughput > 10K rps |
| Scalability | Handle 100M users, 1B messages/day |
| Availability | 99.99% uptime (52 min downtime/year) |
| Reliability | No data loss, exactly-once processing |
| Security | Encryption at rest/transit, RBAC |
| Maintainability | Modular, well-documented, testable |

---

## Key System Properties

### Scalability
- Ability to handle increased load by adding resources
- **Vertical Scaling (Scale Up)**: Add more CPU/RAM to existing machine
- **Horizontal Scaling (Scale Out)**: Add more machines

| Vertical Scaling | Horizontal Scaling |
|-----------------|-------------------|
| Simpler | More complex |
| Hardware limits | Virtually unlimited |
| Single point of failure | Distributed |
| No code changes | May need code changes (stateless) |
| Expensive at scale | Cost-effective |

### Availability
- Percentage of time a system is operational
- Measured in "nines"

| Availability | Downtime/Year | Downtime/Month |
|-------------|---------------|----------------|
| 99% (two nines) | 3.65 days | 7.2 hours |
| 99.9% (three nines) | 8.76 hours | 43.2 minutes |
| 99.99% (four nines) | 52.6 minutes | 4.3 minutes |
| 99.999% (five nines) | 5.26 minutes | 25.9 seconds |

### Reliability
- Probability that a system performs correctly for a specified period
- Achieved through: Redundancy, replication, fault tolerance
- Reliable system ≠ Available system (a system can be available but return wrong data)

### Maintainability
- Ease of fixing bugs, adding features, understanding code
- Includes: Operability, Simplicity, Evolvability

### Performance
- **Latency**: Time to complete a single request (ms)
- **Throughput**: Number of requests processed per unit time (rps, tps)

| Metric | Definition | Measured As |
|--------|-----------|-------------|
| Latency | Time from request sent to response received | p50, p95, p99 (percentiles) |
| Throughput | Volume of work done in given time | Requests/second, transactions/second |
| Bandwidth | Maximum data transfer rate | Mbps, Gbps |

### Latency vs Throughput
- Not inversely proportional (common misconception)
- A system can have LOW latency AND HIGH throughput (well-optimized)
- A system can have HIGH latency AND LOW throughput (bottlenecked)
- Improving one doesn't necessarily improve the other

---

## CAP Theorem

### Theory
- In a distributed system, you can only guarantee TWO of THREE:
  - **Consistency (C)**: Every read receives the most recent write
  - **Availability (A)**: Every request receives a response (even if stale)
  - **Partition Tolerance (P)**: System continues despite network failures

### Diagram
```
        Consistency (C)
           /\
          /  \
    CP   /    \   CA
        /      \
       /________\
  Availability(A) — AP — Partition Tolerance(P)
```

### Why You Must Choose P
- Network partitions WILL happen in distributed systems
- So the real choice is: CP or AP

| Type | Behavior During Partition | Examples |
|------|--------------------------|----------|
| CP | Returns error/timeout rather than stale data | MongoDB, HBase, Redis (cluster) |
| AP | Returns stale data but stays available | Cassandra, DynamoDB, CouchDB |
| CA | Only possible in single-node (no distribution) | Traditional RDBMS |

### PACELC Theorem
- Extension of CAP: What happens when there's NO partition?
- **PAC**: During Partition → choose Availability or Consistency
- **ELC**: Else (no partition) → choose Latency or Consistency

```
If Partition (P):
  Choose Availability (A) or Consistency (C)
Else (E):
  Choose Latency (L) or Consistency (C)
```

| System | P+A/C | E+L/C |
|--------|-------|-------|
| DynamoDB | PA | EL (eventually consistent reads are faster) |
| MongoDB | PC | EC (strongly consistent reads) |
| Cassandra | PA | EL |
| MySQL (single) | N/A | EC |

---

## Stateful vs Stateless Services

### Stateless Services
- Don't store client state between requests
- Any instance can handle any request
- Easy to scale horizontally
- Examples: REST APIs, microservices

### Stateful Services
- Maintain client state between requests
- Specific instances handle specific clients
- Harder to scale
- Examples: WebSocket servers, game servers, database servers

| Aspect | Stateless | Stateful |
|--------|-----------|----------|
| Scaling | Easy (add more instances) | Hard (need sticky sessions or state sync) |
| Failover | Easy (redirect to any instance) | Hard (state must be recovered) |
| Load Balancing | Any algorithm works | Need sticky sessions |
| Caching | External cache needed | Can use local cache |
| Examples | REST API, Lambda | WebSocket, Database |

### Making Stateful Services Stateless
- Move state to external store (Redis, DB)
- Use tokens (JWT) to carry state in requests
- Use distributed caches

---

## Horizontal vs Vertical Scaling

### Diagram
```
Vertical Scaling:           Horizontal Scaling:
┌─────────────┐            ┌───┐ ┌───┐ ┌───┐ ┌───┐
│             │            │ S │ │ S │ │ S │ │ S │
│   Bigger    │            │ e │ │ e │ │ e │ │ e │
│   Machine   │            │ r │ │ r │ │ r │ │ r │
│  (More CPU, │            │ v │ │ v │ │ v │ │ v │
│   RAM, SSD) │            │ 1 │ │ 2 │ │ 3 │ │ 4 │
│             │            └───┘ └───┘ └───┘ └───┘
└─────────────┘                    ↑
                            Load Balancer
```

---

## Interview Questions

**Q: How do you approach a system design interview?**
> Follow a structured approach:
> 1. Clarify requirements (functional + non-functional)
> 2. Estimate scale (users, requests/sec, storage)
> 3. Define APIs
> 4. High-level architecture
> 5. Deep dive into components (database, cache, messaging)
> 6. Address scalability, reliability, monitoring
> 7. Discuss trade-offs

**Q: When would you choose CP over AP?**
> When data correctness is critical and stale data is unacceptable. Examples: financial systems (bank transfers), inventory management (don't oversell), leader election, distributed locks.

**Q: When would you choose AP over CP?**
> When availability is more important than immediate consistency. Examples: social media feeds (showing slightly stale post count is fine), DNS, shopping cart (merge conflicts later).

**Q: How do you calculate the number of nines needed?**
> Based on business impact of downtime. A payment system needs 99.99% (4 nines = 52 min/year downtime). A blog might be fine with 99.9% (8.7 hours/year). Consider: revenue loss per minute of downtime, user impact, SLA commitments.

**Q: What's the difference between reliability and availability?**
> Availability = system is reachable and responds. Reliability = system responds correctly. A system returning wrong data is available but not reliable. A reliable system that's down for maintenance is reliable but not available at that moment.

**Q: How do you make a stateful service horizontally scalable?**
> 1. Externalize state (Redis, database)
> 2. Use consistent hashing for routing
> 3. Implement state replication between nodes
> 4. Use sticky sessions with failover
> 5. Design for state partitioning

---

## Common Mistakes
- Jumping into solution without clarifying requirements
- Ignoring non-functional requirements
- Not estimating scale/capacity
- Choosing CP/AP without justifying WHY for your use case
- Confusing availability with reliability
- Assuming vertical scaling is always inferior (sometimes it's the right answer for simplicity)

---

## Best Practices
- Always start with requirements (FR + NFR)
- Do back-of-envelope calculations for scale
- Design for failure (everything will fail eventually)
- Start simple, then optimize (premature optimization is the root of all evil)
- Make trade-offs explicit and justified
- Use standard building blocks (load balancers, caches, queues)
- Design for observability from day one

---

## Production Considerations
- Define SLAs (Service Level Agreements) and SLOs (Service Level Objectives)
- Plan for capacity: current load + projected growth
- Implement monitoring, alerting, and dashboards
- Design for graceful degradation
- Plan disaster recovery and backup strategies
- Document architecture decisions (ADRs)

---

## Related Topics
- Client-Server Architecture (next topic)
- Load Balancing
- Caching
- Database Scaling
- CAP Theorem applications in specific databases
