# Decision Making

## Theory

### What Interviewers Are Looking For
- Can you make decisions with incomplete information?
- Do you use data and evidence, not just gut feeling?
- Can you evaluate trade-offs and articulate them clearly?
- Do you consider long-term consequences, not just short-term wins?
- Can you commit to a decision and drive it forward?
- Do you revisit decisions when new information emerges?

### Decision-Making for Engineers

```
Types of decisions you'll be asked about:
├── Technical trade-offs (SQL vs NoSQL, sync vs async, monolith vs microservices)
├── Build vs buy (custom solution vs third-party library/service)
├── Priority decisions (tech debt vs feature, speed vs quality)
├── Architecture decisions (with incomplete requirements)
├── Risk decisions (deploy on Friday? Migrate now or later?)
└── People decisions (who to assign, when to escalate)
```

---

## Decision-Making Framework ⭐⭐⭐

```
1. DEFINE the problem clearly
   └── What exactly are we deciding? What are the constraints?

2. GATHER information
   └── Data, metrics, team input, past experience, industry patterns

3. IDENTIFY options
   └── At least 2-3 alternatives (never just "do it" or "don't")

4. EVALUATE trade-offs
   └── Pros/cons for each option against your constraints

5. DECIDE and commit
   └── Make the call, communicate clearly, document reasoning

6. MONITOR and adapt
   └── Set success criteria, revisit if assumptions change
```

---

## Example Stories

### Story 1: Technical Trade-off Decision

**Situation**: "We needed to implement inter-service communication between our order service and inventory service. Two options: synchronous REST calls or asynchronous messaging via Kafka."

**Task**: "As the tech lead for the order service, I needed to recommend the approach. Both had vocal supporters on the team."

**Action**:
- "I listed our actual requirements: orders can't be lost, inventory updates can tolerate 2-3 second delay, peak load is 500 orders/minute, and we needed to add more consumers later (shipping, analytics)."
- "I created a comparison matrix:"

```
| Factor              | REST (Sync)      | Kafka (Async)      |
|---------------------|------------------|--------------------|
| Latency             | Immediate        | 1-3 seconds        |
| Coupling            | Tight            | Loose              |
| Failure handling    | Complex (retries)| Built-in (replay)  |
| Adding consumers    | N+1 API calls    | Just add consumer  |
| Operational cost    | Low              | Medium (Kafka ops) |
| Data loss risk      | Higher (no queue)| Lower (persisted)  |
```

- "Presented trade-offs to the team without pushing my preference."
- "The requirements clearly favored async: loose coupling, multiple consumers, no data loss. The 2-3 second delay was acceptable for inventory."
- "Acknowledged the operational cost of Kafka and proposed mitigation: use our existing shared Kafka cluster, not a new one."

**Result**: "Team aligned on Kafka. Within 3 months, we added 3 more consumers (shipping, analytics, notification) without touching the order service. When the inventory service had an outage, orders continued processing and inventory caught up automatically from the Kafka topic."

---

### Story 2: Build vs Buy Decision

**Situation**: "Our team needed a rate-limiting solution for our APIs. Options: build custom rate limiter in Spring Boot, or use a managed API gateway (Kong/Azure APIM)."

**Task**: "I was asked to evaluate and recommend. Budget was a concern, but so was time-to-market."

**Action**:
- "I timeboxed the evaluation to 3 days. Defined evaluation criteria with stakeholders: cost, time to implement, maintenance burden, feature set needed, and team expertise."
- "Built a quick prototype of custom rate limiting (Redis + sliding window) — estimated 2 weeks to production-ready with tests."
- "Evaluated managed options: Kong (self-hosted) and Azure APIM (managed)."
- "Key insight from data: we had 12 microservices. Custom rate limiting meant implementing in each one. A gateway provides it centrally."
- "Calculated TCO:"

```
Custom: 2 weeks dev + 1 day/month maintenance × 12 services = ~$30K/year (eng time)
APIM:   $600/month + 3 days setup = ~$10K/year (service cost + minimal eng time)
```

- "Recommended APIM despite the monthly cost because: centralized management, less engineering time, additional features (JWT validation, caching, analytics) we'd otherwise build ourselves."

**Result**: "Leadership approved APIM. Deployed in 4 days instead of 2 weeks. The additional features (JWT validation at gateway, request logging) saved us another 3 weeks of work we had in the backlog. Total cost savings in eng time first year: ~$20K."

---

### Story 3: Risk Decision Under Pressure

**Situation**: "On Thursday, we discovered a data inconsistency bug in production. The fix involved a database migration that would rename a column and backfill data. We had a major product launch on Monday."

**Task**: "I needed to decide: deploy the fix now (risky, Thursday before launch) or wait until after launch (accept the inconsistency for 5 more days)."

**Action**:
- "First, I assessed impact: the inconsistency affected 0.3% of records and wasn't user-facing — it only showed in internal reports."
- "Evaluated risk of deploying now: migration could fail, rollback needed, could destabilize before Monday launch."
- "Evaluated risk of waiting: data inconsistency grows slightly but is contained, no user impact, internal team can use a workaround."
- "Consulted with DBA on migration safety and my manager on business priority."
- "My recommendation: wait. The data bug is low-impact and contained. The launch is high-impact and risky to destabilize. Deploy Tuesday after launch stabilizes."
- "Documented the decision and reasoning in our ADR (Architecture Decision Record)."
- "Created a workaround for the internal team (filtered SQL query) to tide them over."

**Result**: "Monday launch went smoothly. Tuesday we deployed the migration without issues. In retrospect, if we'd deployed Thursday and something went wrong, we could have jeopardized the launch for a low-impact fix. The team appreciated the calm, risk-assessed decision over reactive 'fix everything immediately.'"

---

## Trade-off Thinking ⭐⭐⭐

### Common Engineering Trade-offs

| Decision | Option A | Option B | Key Factor |
|----------|----------|----------|-----------|
| Consistency vs Availability | Strong consistency | Eventual consistency | Can the user tolerate stale data? |
| Speed vs Quality | Ship fast with tech debt | Ship slower, cleaner code | Is this throwaway or long-lived? |
| Build vs Buy | Full control, custom | Less control, faster delivery | Is this core business differentiator? |
| Monolith vs Microservices | Simpler, faster to start | Scalable, independent deploys | Team size and system complexity? |
| SQL vs NoSQL | ACID, relationships | Scale, flexibility | Do you need joins and transactions? |
| Sync vs Async | Simple, immediate | Resilient, decoupled | Can the caller wait? |
| Cache vs DB | Fast, eventually consistent | Slower, always accurate | What's the staleness tolerance? |

### How to Articulate Trade-offs in Interviews

```
"I chose [Option A] because:
1. Our primary constraint was [X] (performance/cost/time/reliability)
2. The trade-off was [Y] (what we sacrificed)
3. I mitigated [Y] by [specific action]
4. The result was [measurable outcome]

If I had to do it again with [different constraint], I'd choose [Option B] because [reasoning]."
```

---

## Decision Documentation (ADR)

```
Architecture Decision Record:
├── Title: "Use Kafka for inter-service communication"
├── Date: 2024-03-15
├── Status: Accepted
├── Context: Need async communication between order and inventory services
├── Options Considered:
│   ├── Option 1: Synchronous REST
│   ├── Option 2: Kafka
│   └── Option 3: RabbitMQ
├── Decision: Kafka
├── Reasoning: Durability, multiple consumers, team familiarity, existing cluster
├── Trade-offs Accepted: Operational complexity, 1-3s latency
├── Consequences: Need Kafka expertise, monitoring, schema management
└── Review Date: 2024-06-15 (3 months)
```

---

## Reversible vs Irreversible Decisions ⭐⭐⭐

```
Reversible ("two-way door"):
├── Feature flag that can be turned off
├── Library choice that can be swapped
├── Caching strategy
├── API response format (if versioned)
└── → Decide quickly, iterate

Irreversible ("one-way door"):
├── Database schema for core entities
├── Choice of primary language/framework
├── Public API contract (once clients depend on it)
├── Data deletion
├── Architecture paradigm (monolith → microservices is expensive to reverse)
└── → Take more time, gather more input, prototype
```

**Key insight**: Most decisions are reversible. Treat them that way — decide fast, learn, adjust. Reserve heavy analysis for truly irreversible choices.

---

## Variations of This Question

| Question | Focus Area |
|----------|-----------|
| "Tell me about a difficult decision you made" | Any decision story |
| "How do you make technical decisions?" | Your framework/process |
| "Tell me about a time you had to make a decision with incomplete info" | Ambiguity tolerance |
| "Describe a trade-off you made in a project" | Trade-off reasoning |
| "Tell me about a decision you'd make differently now" | Self-awareness + learning |
| "How do you handle disagreement on technical decisions?" | Overlap with conflict |

---

## Tips

- **Always present alternatives**: Show you considered multiple options, not just jumped to one
- **Data over opinion**: "Based on our metrics..." beats "I felt like..."
- **Acknowledge trade-offs**: The best answer shows what you sacrificed and why it was acceptable
- **Show decisiveness**: Analysis paralysis is a red flag. At some point, you commit.
- **Revisit**: Mention that you monitored the outcome and would adjust if needed
- **Document**: Mention ADRs, RFCs, or written proposals — shows maturity
