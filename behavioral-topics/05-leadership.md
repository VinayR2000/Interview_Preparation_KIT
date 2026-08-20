# Leadership

## Theory

### What Interviewers Are Looking For
- Can you lead without formal authority?
- Do you influence outcomes through expertise and communication?
- Can you make decisions with incomplete information?
- Do you unblock others and elevate the team?
- Can you drive projects to completion?

### Leadership for Engineers ≠ Management

```
Engineering Leadership:
├── Technical leadership (architecture decisions, code standards)
├── Project leadership (driving delivery, unblocking people)
├── Team leadership (mentoring, culture, knowledge sharing)
├── Influence without authority (convincing stakeholders)
└── Leading by example (quality, ownership, accountability)

You DON'T need a "Lead" title to demonstrate leadership.
```

---

## Example Stories

### Story 1: Leading a Technical Migration

**Situation**: "Our team had a legacy Spring Boot monolith handling 3 services (orders, inventory, notifications). It was becoming a deployment bottleneck — a change in notifications required redeploying the entire system, including orders and inventory."

**Task**: "No one was formally assigned to address this. I proposed the migration to microservices to my manager and volunteered to lead it."

**Action**:
- "I wrote an RFC (Request for Comments) document outlining: current pain points (3 incidents in 2 months from coupled deployments), proposed architecture, migration plan (strangler fig pattern), estimated timeline (3 months), and risks."
- "Got buy-in from my manager and architect by presenting data on incidents and deployment frequency."
- "Created a phased plan: Phase 1 — extract notification service (lowest risk). Phase 2 — extract inventory. Phase 3 — orders."
- "Held weekly alignment meetings with the team. Assigned work based on team members' strengths and growth areas."
- "Handled the tricky parts myself (shared database decomposition, event publishing for consistency) and delegated well-scoped work to others."
- "When we hit a blocker (shared session state), I facilitated a design session with the team instead of dictating a solution."

**Result**: "Completed in 14 weeks. Deployment frequency went from 1/week (risky, coordinated) to 3-4/week per service (independent). Incidents from coupled deployments dropped to zero. Two junior engineers grew significantly from the scoped work they owned."

---

### Story 2: Leading Through a Production Crisis

**Situation**: "Our payment service went down during peak hours (Black Friday). Error rate spiked to 40%. The on-call engineer was overwhelmed — it was their first major incident."

**Task**: "I wasn't on call or formally responsible, but I had deep knowledge of the payment service and recognized the on-call engineer needed support."

**Action**:
- "Joined the incident channel and offered to help coordinate."
- "Established structure: 'Let's divide — I'll investigate the database, you check the Kafka consumer lag, and [third person] check the load balancer metrics.'"
- "Identified the root cause within 15 minutes: connection pool exhaustion due to a long-running transaction from a new query."
- "Guided the on-call engineer through the fix (killed the blocking query, increased pool timeout, deployed connection pool metrics)."
- "Communicated status updates to stakeholders every 10 minutes (product manager, support team)."
- "After resolution, facilitated the post-mortem and action items."

**Result**: "Incident resolved in 35 minutes (team average for similar severity: 90 minutes). On-call engineer felt supported rather than overwhelmed. Post-mortem led to: connection pool alerts, query timeout limits, and a runbook for similar issues."

---

### Story 3: Driving a Cross-Team Initiative

**Situation**: "Three teams were independently building similar authentication logic in their microservices. Each team had their own JWT validation, role-checking code, and slightly different implementations. This caused inconsistencies and security gaps."

**Task**: "I identified this pattern during a code review of another team's PR and decided to address it."

**Action**:
- "Documented the problem: 3 implementations, 2 had subtle security issues (missing token expiry check, missing audience validation)."
- "Proposed a shared Spring Boot starter library that all teams could use."
- "Got buy-in from all 3 tech leads by showing the security gaps (not just 'code duplication is bad')."
- "Led the design: created an RFC, gathered requirements from each team, designed a flexible API."
- "Built the core library myself (2 weeks), then worked with one engineer from each team to integrate it into their services."
- "Wrote documentation, migration guide, and held a lunch-and-learn demo."

**Result**: "All 3 teams adopted the shared library within a month. Security gaps eliminated. New teams onboarded in minutes instead of days. Library is now the standard for authentication across 8 services. I was recognized in the company tech newsletter."

---

## Leadership Qualities to Demonstrate ⭐⭐⭐

```
1. INITIATIVE: Seeing what needs to be done and doing it
2. INFLUENCE: Convincing others through data and reasoning
3. DECISION-MAKING: Making calls with incomplete information
4. DELEGATION: Empowering others with appropriate work
5. COMMUNICATION: Keeping stakeholders informed
6. UNBLOCKING: Removing obstacles for the team
7. ACCOUNTABILITY: Owning outcomes (good and bad)
8. ELEVATING OTHERS: Making the team stronger, not just yourself
```

---

## Leadership vs. Individual Contribution

| Factor | IC Focus | Leadership Focus |
|--------|----------|-----------------|
| Success metric | "I built X" | "The team delivered X" |
| Problem-solving | Solve it yourself | Help others solve it |
| Knowledge | Deep expertise | Share expertise broadly |
| Decisions | Make for your code | Make for the team/system |
| Ownership | Your features | Team outcomes |
| Growth | Your skills | Others' growth |

---

## Variations of This Question

| Question | What They Want |
|----------|---------------|
| "Tell me about a time you led a project" | End-to-end project leadership |
| "Describe a time you influenced without authority" | Persuasion + collaboration |
| "Tell me about a time you stepped up" | Initiative in ambiguity |
| "How do you lead technical decisions?" | Decision-making process |
| "Tell me about a time you drove alignment" | Cross-team influence |
| "Describe your leadership style" | Self-awareness + examples |

---

## Tips

- You don't need a "team lead" title — any project where you drove outcomes counts
- Focus on "we" for outcomes but "I" for your specific actions
- Show that you elevated others, not just yourself
- Include how you handled disagreement or pushback during your leadership
- Quantify: team size, timeline, impact metrics
- Show the DECISION-MAKING, not just the execution
