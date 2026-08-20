# Conflict Resolution

## Theory

### What Interviewers Are Looking For
- Can you disagree professionally without damaging relationships?
- Do you focus on the problem, not the person?
- Can you find common ground and compromise?
- Do you escalate appropriately (not too early, not too late)?
- Are you self-aware about your own role in the conflict?

### Common Conflict Scenarios for Engineers

| Scenario | Context |
|----------|---------|
| Technical disagreement | Microservices vs monolith, REST vs gRPC, architecture decisions |
| Code review conflict | Reviewer pushes back on your approach |
| Priority conflict | PM wants feature X, you believe tech debt needs fixing |
| Cross-team dependency | Another team is blocking your deliverable |
| Interpersonal tension | Teammate not pulling their weight, communication issues |
| Manager disagreement | You disagree with a decision from leadership |

---

## STAR Framework ⭐⭐⭐

```
S — Situation: Set the context (1-2 sentences)
T — Task: What was your responsibility?
A — Action: What did YOU specifically do? (most important)
R — Result: What was the outcome? (quantify if possible)
```

---

## Example Stories

### Story 1: Technical Disagreement (Architecture)

**Situation**: "Our team was designing a new notification service. A senior colleague strongly advocated for building it as a synchronous REST service calling each notification channel directly. I believed we needed an event-driven approach with Kafka because we had reliability and scaling requirements."

**Task**: "I needed to present my case without making it personal or dismissing his experience, since he had 8 years on the team and significant influence."

**Action**: 
- "I scheduled a 1:1 first — not a public meeting — to understand his reasoning. He was concerned about Kafka's operational complexity."
- "I acknowledged his concern was valid — it was more complex operationally."
- "I prepared a simple comparison document: current volume (5K notifications/day), projected growth (50K in 6 months), failure scenarios for each approach."
- "I proposed a compromise: use Kafka for the async pipeline but keep the architecture simple — no Kafka Streams, just a producer and consumer with a dead-letter topic."
- "I offered to own the Kafka operational setup personally."

**Result**: "He agreed to the event-driven approach with the simplified scope. We shipped it in 3 weeks. When volume hit 40K/day six months later, the system scaled without changes. He later told me he appreciated that I came to him privately first rather than debating in a meeting."

---

### Story 2: Cross-Team Dependency Conflict

**Situation**: "Our team needed an API from the platform team to proceed with a feature. We had a hard deadline from the business, but the platform team kept deprioritizing our request."

**Task**: "I needed to unblock my team without creating an adversarial relationship with platform."

**Action**:
- "I set up a meeting with the platform team's tech lead to understand their priorities and constraints."
- "I discovered they had a major production incident consuming all bandwidth."
- "Instead of escalating to managers, I proposed: could we design the API contract together, and our team implements a temporary mock while they build the real service?"
- "I drafted the OpenAPI spec, got their review, and we built against the contract."
- "I also offered one of our team members to help with their incident for a day."

**Result**: "We met our deadline using the mock. Platform delivered the real API two weeks later with zero integration issues because we designed the contract together. The relationship between our teams actually improved because of the collaboration."

---

### Story 3: Code Review / Standards Disagreement

**Situation**: "I submitted a PR that introduced a new caching pattern using Redis with TTL-based invalidation. A teammate rejected it, insisting we should use cache-aside with manual invalidation everywhere, which I felt was error-prone at scale."

**Task**: "I needed to resolve this without it becoming a PR war of comments back and forth."

**Action**:
- "I pulled him into a 15-minute call instead of continuing the PR comment thread."
- "I asked him to explain his concern — he had experienced a production bug from stale TTL caches at a previous company."
- "I acknowledged his experience was valid and asked: 'What if we combine both? TTL as a safety net (30 min) plus explicit invalidation on writes?'"
- "We sketched the hybrid approach together and I updated the PR."

**Result**: "We got a better design than either of us proposed individually. The combined approach gave us both correctness (explicit invalidation) and resilience (TTL prevents indefinite staleness). The PR was merged within the hour."

---

## Key Principles ⭐⭐⭐

```
DO:
├── Focus on the problem, not the person
├── Seek to understand before being understood
├── Have 1:1 conversations before public forums
├── Acknowledge the other person's perspective
├── Use data and evidence, not opinions
├── Propose compromises
├── Assume good intent
└── Know when to escalate (last resort)

DON'T:
├── Make it personal ("you always do this")
├── Debate in public forums/Slack channels
├── Escalate to manager as first step
├── Refuse to compromise on non-critical points
├── Hold grudges after resolution
├── Avoid conflict entirely (that's also a red flag)
└── Say "I was right" even if you were
```

---

## Escalation Framework

```
Level 1: Direct conversation (1:1)
    │ (If not resolved)
    ▼
Level 2: Involve a neutral third party (another senior engineer)
    │ (If not resolved)
    ▼
Level 3: Involve your manager
    │ (If not resolved)
    ▼
Level 4: Involve skip-level / leadership
    │
    └── Rarely needed. If you reach here, document everything.
```

---

## Interview Tips

### What NOT to Say
- "I've never had a conflict" → Shows lack of self-awareness
- "I escalated to my manager immediately" → Shows can't handle things yourself
- "The other person was wrong and I proved it" → Shows inability to collaborate
- "We never resolved it" → Bad ending

### What TO Show
- Self-awareness (you understood your part in the conflict)
- Professional maturity (you kept it respectful)
- Problem-solving (you found a path forward)
- Positive outcome (relationship preserved or improved)
- Growth (what you learned for next time)

---

## Variations of This Question

| Question | Same Answer Category |
|----------|---------------------|
| "Tell me about a time you disagreed with a teammate" | Technical disagreement story |
| "Describe a situation where you had a conflict at work" | Any conflict story |
| "How do you handle disagreements?" | Framework + example |
| "Tell me about a difficult coworker" | Interpersonal conflict |
| "How do you handle pushback on your ideas?" | Technical disagreement |
| "Tell me about a time you convinced someone" | Persuasion through data |
