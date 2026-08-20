# HLD Interview Problems — Advanced

## 1. YouTube / Video Streaming

### Requirements
**Functional:**
- Upload videos (all formats, up to hours long)
- Stream/watch videos (adaptive quality)
- Search videos
- Recommendations
- Like, comment, subscribe
- View count, analytics

**Non-functional:**
- Upload: Reliable, resumable for large files
- Streaming: Low latency start (<2s), no buffering
- Scale: 2B MAU, 500 hours video uploaded per minute
- Global CDN for low-latency delivery
- Storage: Petabytes of video data

### Scale Estimation
```
Daily active users: 800M
Average videos watched/day: 5
Video views/day: 4 billion
Average video size (after encoding): 500MB → multiple resolutions
Storage for new videos/day: 500 hours/min × 60 × 24 × 500MB ≈ 360 PB/year
```

### High-Level Architecture
```
Upload Path:
┌──────┐    ┌──────────┐    ┌───────────────┐    ┌─────────────┐
│Creator│───→│Upload Svc │───→│ Object Store  │───→│Transcoding  │
│       │    │(chunked)  │    │  (raw video)  │    │  Pipeline   │
└──────┘    └──────────┘    └───────────────┘    └──────┬──────┘
                                                         │
                                                         ▼
                                                   ┌───────────┐
                                                   │ Encoded   │
                                                   │ Videos    │
                                                   │(multiple  │
                                                   │ qualities)│
                                                   └─────┬─────┘
                                                         │
Streaming Path:                                          ▼
┌──────┐    ┌─────┐    ┌───────────────────────────────────────┐
│Viewer│←──→│ CDN │←───│  Origin Servers (encoded videos)      │
└──────┘    └─────┘    └───────────────────────────────────────┘
```

### Key Design Decisions

**Video Transcoding Pipeline:**
```
Raw Video → Split into segments → Transcode each segment
                                    ├── 240p (low bandwidth)
                                    ├── 480p
                                    ├── 720p
                                    ├── 1080p
                                    └── 4K (high bandwidth)

DAG (Directed Acyclic Graph) workflow:
  Video → Audio extraction → Audio encoding
       → Video segmentation → Parallel transcoding
       → Thumbnail generation
       → Metadata extraction
       → All complete → Merge manifest → Publish
```

**Adaptive Bitrate Streaming (ABR):**
```
Client monitors bandwidth:
  High bandwidth → request 1080p segments
  Bandwidth drops → switch to 480p segments
  
Protocols: HLS (Apple), DASH (universal)
Manifest file (.m3u8): Lists all quality levels and segment URLs
```

**CDN Strategy:**
- Popular videos: Pushed to edge (CDN cache)
- Long-tail videos: Fetched on demand from origin
- Geographic distribution: Deploy CDN nodes in major cities
- Cache hit ratio target: >95% for popular content

---

## 2. Netflix

### Additional Complexity Over YouTube
- Licensed content (DRM protection)
- Personalized recommendations (ML-heavy)
- Offline downloads
- Multiple profiles per account
- Global content licensing (different catalog per country)

### Key Differences
| Aspect | YouTube | Netflix |
|--------|---------|---------|
| Content | User-generated | Licensed/Original |
| Revenue | Ads + Premium | Subscription |
| DRM | Optional | Required (Widevine, FairPlay) |
| Catalog size | Billions | ~15,000 titles |
| Recommendation | Watch history + similar | Sophisticated ML (80% of watches) |
| Pre-caching | Popular videos at edge | Predicted content pre-loaded to CDN |

### Netflix Open Connect (Custom CDN)
```
Netflix places appliances (OCA) in ISP data centers:
- Predict what users will watch (ML)
- Pre-load content to local OCA during off-peak
- Stream from local appliance (minimal internet transit)
- Reduces bandwidth costs and latency
```

---

## 3. WhatsApp / Messaging at Scale

### Requirements
**Functional:**
- 1:1 and group messaging (up to 1024 members)
- End-to-end encryption
- Read receipts, online status
- Media sharing (images, video, voice)
- Voice/video calls

**Non-functional:**
- End-to-end encryption (server can't read messages)
- Message delivery guarantee
- Real-time (<100ms for online users)
- Scale: 2B users, 100B messages/day

### Key Design Decisions

**End-to-End Encryption:**
```
Signal Protocol:
1. Each device has identity key pair + signed pre-key + one-time pre-keys
2. Alice wants to message Bob:
   - Fetch Bob's public keys from server
   - Derive shared secret (X3DH key agreement)
   - Encrypt message with shared secret
3. Server ONLY routes encrypted bytes (can't decrypt)
4. Group: Sender encrypts once with shared group key
   - Group key rotated when member leaves
```

**Message Delivery:**
```
Online recipient:
  Sender → Server → Push to recipient's connection → ACK back

Offline recipient:
  Sender → Server → Store in queue (per-user)
  When recipient connects → deliver queued messages → delete from server

Delivery states: Sent (1 check) → Delivered (2 checks) → Read (blue checks)
```

**Group Messaging at Scale:**
```
Small groups (< 256): Fan-out on send (server sends to each member)
Large groups: Fan-out on read (store once, each member fetches)

Optimization:
- Store message once in group's message queue
- Each member has a pointer to last read message
- Fetch unread = messages after pointer
```

---

## 4. Instagram / Social Media

### Requirements
**Functional:**
- Post photos/reels with captions
- Follow/unfollow users
- News feed (timeline)
- Like, comment
- Stories (24-hour content)
- Explore/discover
- Direct messages

**Non-functional:**
- Feed generation < 500ms
- Handle celebrity accounts (millions of followers)
- Scale: 2B MAU, 95M posts/day

### Key Design — News Feed

**Feed Generation Approaches:**

| Approach | How | Pros | Cons |
|----------|-----|------|------|
| Fan-out on write | Pre-compute feed for each user on new post | Fast reads | Slow writes for celebrities |
| Fan-out on read | Compute feed on request (pull from follows) | Simple writes | Slow reads |
| Hybrid | Fan-out on write for normal users, on read for celebrities | Balanced | Complex |

**Hybrid Approach:**
```
Normal user posts (< 10K followers):
  → Fan-out: Write to each follower's feed cache

Celebrity posts (> 10K followers):
  → Don't fan-out
  → At read time: Merge follower's feed cache + fetch celebrity posts

Feed read:
  1. Get pre-computed feed from cache
  2. Merge with celebrity posts (fetch and sort)
  3. Apply ranking (ML model: relevance, recency, engagement)
  4. Return paginated feed
```

**Celebrity Problem:**
```
Celebrity with 100M followers posts:
  Fan-out write: 100M cache writes (minutes to complete, wasteful if most don't see it)
  Solution: Don't fan-out for celebrities, pull on demand
```

---

## 5. Uber (Full System)

### Beyond Basic Ride Matching

**Supply-Demand Prediction:**
- ML models predict demand per area per time
- Pre-position drivers to high-demand areas
- Dynamic pricing based on real-time supply/demand

**Dispatch Algorithm:**
```
Optimization problem:
  - Multiple riders requesting simultaneously
  - Multiple drivers available
  - Minimize: overall wait time, empty miles
  - Consider: driver direction, ETA, type match (UberX, Black, XL)
  
Solution: Batched matching every few seconds
  - Collect rider requests in batch window (2-3 sec)
  - Available drivers in area
  - Hungarian algorithm / optimization to find best global matching
```

**ETA Calculation:**
```
Naive: Google Maps distance/time
Better: ML model trained on:
  - Historical trip times for this route
  - Current traffic (real-time from active drivers)
  - Time of day, weather, events
  - Road closures
  
Update ETA in real-time as driver moves
```

---

## 6. Amazon (Full E-Commerce at Scale)

### Beyond Basic E-Commerce

**Recommendation Engine:**
```
Collaborative filtering: "Users who bought X also bought Y"
Content-based: "Similar products by features"
Hybrid: Combine both

Real-time signals:
  - Browsing history (session)
  - Purchase history
  - Search queries
  - Cart contents

Batch processing: Spark/ML pipeline (daily updates)
Real-time: Stream processing for session-based recs
```

**Search System:**
```
Product Search Pipeline:
  Query → Spell correction → Tokenize → Expand (synonyms)
       → Retrieve (inverted index / Elasticsearch)
       → Rank (relevance + popularity + personalization)
       → Filter (price, brand, availability)
       → Return paginated results

Index: Elasticsearch with custom analyzers
Ranking: ML model (click-through rate, conversion rate, recency)
```

**Warehouse and Fulfillment:**
```
Order placed → Select warehouse (closest with stock)
            → Pick, Pack, Ship
            → Multiple items may come from different warehouses
            → Optimize: minimize shipping cost + delivery time

Inventory distributed across warehouses:
  - Demand forecasting per region
  - Automatic restock triggers
  - Split shipments when necessary
```

---

## 7. Distributed Notification System

### At Scale Challenges
```
Scale: 10B notifications/day, 500M users
Channels: Push, Email, SMS, In-app
Challenge: Different latency requirements per type
```

### Architecture
```
                    ┌─────────────────────────────────┐
Event Sources ────→ │       Priority Queue System      │
                    │  P0: OTP, Auth (< 1 sec)         │
                    │  P1: Order updates (< 30 sec)    │
                    │  P2: Promotional (best effort)   │
                    └──────────────┬──────────────────┘
                                   │
                    ┌──────────────▼──────────────────┐
                    │     Notification Router          │
                    │  (User preferences, DND, caps)  │
                    └──┬──────────┬──────────┬───────┘
                       │          │          │
                       ▼          ▼          ▼
                    [Push]     [Email]     [SMS]
                    Worker     Worker      Worker
                    Pool       Pool        Pool
```

### Key Design: Deduplication and Throttling
```
Dedup: Hash(userId + templateId + content hash) → check last sent time
Throttle: Max 5 push/hour per user, Max 2 email/day for promotions
Batching: Aggregate multiple events into single notification
DND: Respect quiet hours per timezone
```

---

## 8. Distributed Job Scheduler

### Requirements
- Schedule jobs: one-time, recurring (cron), delayed
- Exactly-once execution guarantee
- Handle node failures (reschedule)
- Priority support
- Scale: 1M scheduled jobs, 100K executions/minute

### Architecture
```
┌──────────────┐    ┌─────────────────────────────────────┐
│ Job Submitter│───→│        Scheduler Service             │
└──────────────┘    │  ┌────────────┐  ┌───────────────┐  │
                    │  │ Job Store  │  │ Timer Service  │  │
                    │  │ (DB)       │  │ (trigger jobs) │  │
                    │  └────────────┘  └───────┬───────┘  │
                    └──────────────────────────┼───────────┘
                                               │
                              ┌─────────────────────────────┐
                              │      Execution Queue         │
                              │      (Kafka / Redis)         │
                              └──────────────┬──────────────┘
                                             │
                    ┌────────────────────────────────────────┐
                    │           Worker Pool                   │
                    │  [W1]  [W2]  [W3]  [W4]  [W5]         │
                    └────────────────────────────────────────┘
```

### Key Design: Exactly-Once Execution
```
Challenge: Node crashes after picking job but before completing

Solution: Claim-based execution
1. Worker claims job: UPDATE jobs SET status='RUNNING', worker='W1', 
   heartbeat=NOW() WHERE id=123 AND status='READY'
2. Worker sends heartbeat every 30s
3. Monitor: If heartbeat stale > 60s → mark job READY (reschedule)
4. On completion: Worker marks COMPLETED
5. Idempotent execution: Job logic handles re-runs safely
```

---

## 9. Search System (Google-lite)

### Requirements
- Web crawling (discover pages)
- Indexing (build searchable index)
- Ranking (relevance)
- Query processing (< 500ms)

### Architecture
```
┌────────────┐    ┌───────────────┐    ┌──────────────┐
│   Crawler  │───→│   Indexer     │───→│ Inverted     │
│  (fetches  │    │  (tokenize,  │    │  Index       │
│   pages)   │    │   normalize)  │    │ (distributed)│
└────────────┘    └───────────────┘    └──────┬───────┘
                                              │
┌────────────┐    ┌───────────────┐           │
│   User     │───→│Query Processor│───────────┘
│  (search)  │    │(parse, expand,│
└────────────┘    │ retrieve,rank)│
                  └───────────────┘
```

### Inverted Index
```
Word → List of documents containing it

"distributed" → [doc1, doc5, doc89, doc234, ...]
"system"      → [doc1, doc3, doc5, doc89, ...]
"design"      → [doc1, doc12, doc89, ...]

Query "distributed system design":
  Intersect posting lists → [doc1, doc89, ...]
  Rank by relevance (TF-IDF, PageRank, freshness)
```

### PageRank (Simplified)
```
Page importance = Sum of (importance of linking pages / their outlink count)

Pages with many high-quality inlinks rank higher
Recursive: Computed iteratively until convergence
```

---

## Interview Tips for Advanced Problems

### What Interviewers Look For
| Signal | How to Demonstrate |
|--------|-------------------|
| Scale awareness | Capacity estimation, identify bottlenecks |
| Trade-off thinking | "We chose X over Y because..." |
| Depth | Deep dive into one component (CDN, feed ranking, matching) |
| Breadth | Cover all major components at high level |
| Failure handling | "What if this component fails?" |
| Evolution | "Start simple, then scale to..." |

### Common Deep-Dive Topics
- CDN and video delivery (YouTube/Netflix)
- Feed ranking algorithms (Instagram/Twitter)
- Real-time location matching (Uber)
- Distributed transactions in payments
- Message delivery guarantees (WhatsApp)
- Search indexing and ranking

---

## Common Mistakes
- Trying to solve everything (focus on 2-3 key components deeply)
- Not quantifying scale (always do capacity estimation)
- Ignoring the "celebrity problem" in social media
- Designing only the happy path (discuss failures)
- Not considering global distribution for large-scale systems

---

## Best Practices
- Spend first 5 minutes on requirements and scale
- Draw high-level architecture, then pick 2-3 areas to deep-dive
- Discuss trade-offs explicitly (consistency vs availability, cost vs performance)
- Mention real technologies (Kafka, Redis, Elasticsearch) to show experience
- Address security and compliance where relevant (DRM, GDPR, PCI)
- End with: "Given more time, I would also address..."

---

## Related Topics
- CDN and Content Delivery
- ML/Recommendation Systems
- Real-Time Systems
- Distributed Consensus
- Global System Design
