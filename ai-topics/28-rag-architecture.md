# RAG Architecture — Complete Deep Dive

## Theory

### RAG Architecture Patterns

There are multiple RAG architecture patterns, from naive to production-grade. This file covers all of them.

---

## 1. Naive RAG ⭐⭐⭐

The simplest implementation. Most tutorials show this.

```
┌─────────────────────────────────────────────────────┐
│ INDEXING PIPELINE                                    │
│                                                      │
│ Documents → Chunk → Embed → Store in Vector DB       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ QUERY PIPELINE                                       │
│                                                      │
│ User Query → Embed → Vector Search → Top-K docs     │
│     → Stuff into prompt → LLM → Answer              │
└─────────────────────────────────────────────────────┘
```

**Problems with Naive RAG:**
- Bad chunking → retrieves irrelevant content
- No query understanding → vague queries get poor results
- No ranking → top-K by cosine similarity alone is noisy
- Single retrieval → misses multi-faceted questions
- No source validation → hallucinations from wrong context
- No feedback loop → can't improve over time

---

## 2. Advanced RAG ⭐⭐⭐

Addresses Naive RAG limitations with pre-retrieval, retrieval, and post-retrieval optimizations.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ADVANCED RAG PIPELINE                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  User Query                                                          │
│      │                                                               │
│      ▼                                                               │
│  ┌──────────────────────┐                                           │
│  │ PRE-RETRIEVAL        │                                           │
│  │ ├── Query rewriting  │ (clarify vague queries)                   │
│  │ ├── Query expansion  │ (generate sub-questions)                  │
│  │ ├── Query routing    │ (select which index to search)            │
│  │ └── Query classification │ (determine intent)                    │
│  └──────────┬───────────┘                                           │
│             │                                                        │
│             ▼                                                        │
│  ┌──────────────────────┐                                           │
│  │ RETRIEVAL            │                                           │
│  │ ├── Hybrid search    │ (vector + BM25/keyword)                   │
│  │ ├── Multi-index      │ (search multiple vector stores)           │
│  │ ├── Metadata filter  │ (narrow by date, category, source)       │
│  │ └── Recursive retrieval │ (follow references)                    │
│  └──────────┬───────────┘                                           │
│             │                                                        │
│             ▼                                                        │
│  ┌──────────────────────┐                                           │
│  │ POST-RETRIEVAL       │                                           │
│  │ ├── Reranking        │ (cross-encoder reorder by relevance)      │
│  │ ├── Compression      │ (extract only relevant sentences)         │
│  │ ├── Deduplication    │ (remove redundant chunks)                 │
│  │ └── Context ordering │ (most relevant first/last)               │
│  └──────────┬───────────┘                                           │
│             │                                                        │
│             ▼                                                        │
│  ┌──────────────────────┐                                           │
│  │ GENERATION           │                                           │
│  │ ├── Prompt template  │ (structured with citations)               │
│  │ ├── Chain-of-thought │ (step-by-step reasoning)                  │
│  │ ├── Self-reflection  │ (verify answer against context)           │
│  │ └── Citation linking │ (map claims to source docs)              │
│  └──────────────────────┘                                           │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Modular RAG ⭐⭐⭐

Composable modules that can be mixed and matched per use case.

```
┌────────────────────────────────────────────────────────────────┐
│                    MODULAR RAG ARCHITECTURE                      │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐│
│  │ Indexing     │  │ Retrieval   │  │ Generation              ││
│  │ Modules      │  │ Modules     │  │ Modules                 ││
│  ├─────────────┤  ├─────────────┤  ├─────────────────────────┤│
│  │• Chunking    │  │• Dense      │  │• Prompt construction    ││
│  │  - Fixed     │  │  (vector)   │  │• Answer generation      ││
│  │  - Semantic  │  │• Sparse     │  │• Citation extraction    ││
│  │  - Agentic   │  │  (BM25)     │  │• Fact verification      ││
│  │• Embedding   │  │• Hybrid     │  │• Answer refinement      ││
│  │  - Dense     │  │• Knowledge  │  │• Multi-step reasoning   ││
│  │  - Sparse    │  │  Graph      │  │                         ││
│  │  - Multi-vec │  │• SQL        │  │                         ││
│  │• Storage     │  │• Multi-modal│  │                         ││
│  │  - Vector DB │  │             │  │                         ││
│  │  - Graph DB  │  │             │  │                         ││
│  │  - Hybrid    │  │             │  │                         ││
│  └─────────────┘  └─────────────┘  └─────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Orchestration: Route queries to appropriate module combo     ││
│  └─────────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────┘
```

---

## 4. Agentic RAG ⭐⭐⭐

The LLM decides WHEN and HOW to retrieve, using tools.

```
┌────────────────────────────────────────────────────────────────┐
│                      AGENTIC RAG                                │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Query: "Compare our Q3 revenue with Q2 and explain why   │
│               it dropped, citing specific product lines"        │
│                                                                  │
│  Agent (LLM with tools):                                        │
│  │                                                               │
│  ├── Think: "I need Q3 revenue data, Q2 revenue data,          │
│  │           and product-line breakdown"                         │
│  │                                                               │
│  ├── Action: search_vector_db("Q3 2024 revenue report")         │
│  │   └── Result: [Q3 financial summary document]                │
│  │                                                               │
│  ├── Action: search_vector_db("Q2 2024 revenue report")         │
│  │   └── Result: [Q2 financial summary document]                │
│  │                                                               │
│  ├── Action: query_sql_db("SELECT product_line, revenue         │
│  │           FROM quarterly_revenue WHERE quarter IN ('Q2','Q3')│
│  │           ORDER BY product_line")                             │
│  │   └── Result: [structured data table]                        │
│  │                                                               │
│  ├── Think: "I have all the data. Q3 dropped because            │
│  │           Product X declined 30%. Let me find why."          │
│  │                                                               │
│  ├── Action: search_vector_db("Product X sales decline Q3")     │
│  │   └── Result: [market analysis document]                     │
│  │                                                               │
│  └── Generate: Final comprehensive answer with citations        │
│                                                                  │
└────────────────────────────────────────────────────────────────┘

Key difference from Advanced RAG:
- Advanced RAG: Fixed pipeline (always retrieves, always generates)
- Agentic RAG: LLM decides dynamically what tools to use and when
```

---

## 5. Graph RAG ⭐⭐

Combines knowledge graphs with vector search for relationship-heavy data.

```
┌────────────────────────────────────────────────────────────────┐
│                        GRAPH RAG                                 │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  INDEXING:                                                        │
│  Documents → Entity Extraction → Relationship Extraction         │
│      │              │                      │                     │
│      ▼              ▼                      ▼                     │
│  Vector DB     Knowledge Graph        Community Detection        │
│  (chunks)      (entities +            (group related             │
│                 relationships)          entities)                 │
│                                                                  │
│  QUERY:                                                          │
│  User Question                                                   │
│      │                                                           │
│      ├──► Vector Search (find relevant chunks)                  │
│      │                                                           │
│      ├──► Entity Linking (find mentioned entities)              │
│      │        │                                                  │
│      │        ▼                                                  │
│      │    Graph Traversal (follow relationships)                │
│      │        │                                                  │
│      │        ▼                                                  │
│      │    Subgraph Extraction (relevant neighborhood)           │
│      │                                                           │
│      └──► Merge: Vector results + Graph context                 │
│               │                                                  │
│               ▼                                                  │
│           LLM Generation (with structured + unstructured context)│
│                                                                  │
└────────────────────────────────────────────────────────────────┘

Best for:
- "Who reports to the manager of the team that built feature X?"
- Legal documents with cross-references
- Medical knowledge (symptoms → conditions → treatments)
- Code repositories (files → functions → dependencies)
```

---

## 6. Multi-Modal RAG ⭐⭐

Handle images, tables, and mixed content alongside text.

```
┌────────────────────────────────────────────────────────────────┐
│                     MULTI-MODAL RAG                              │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Document with mixed content:                                    │
│  ┌─────────────────────────────────────┐                        │
│  │ Text: "Revenue increased by 15%..." │──► Text chunking       │
│  │ Table: [Q1: $5M, Q2: $5.75M...]    │──► Table extraction    │
│  │ Chart: [bar graph of revenue]        │──► Image description  │
│  │ Diagram: [architecture diagram]      │──► Vision model       │
│  └─────────────────────────────────────┘                        │
│                                                                  │
│  Indexing strategy:                                              │
│  ├── Text → standard chunking + embedding                       │
│  ├── Tables → convert to text summary + store structured data   │
│  ├── Images → generate description via vision model + embed     │
│  └── All → store with metadata (type, page, position)           │
│                                                                  │
│  Retrieval:                                                      │
│  ├── Query embeds as text                                        │
│  ├── Retrieve text chunks, table summaries, image descriptions  │
│  └── Reconstruct full context (text + table data + image refs)  │
│                                                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## 7. Corrective RAG (CRAG) ⭐⭐

Self-correcting RAG that evaluates retrieval quality and falls back to alternatives.

```
┌────────────────────────────────────────────────────────────────┐
│                    CORRECTIVE RAG (CRAG)                         │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Query → Retrieve Documents                                 │
│       │                                                          │
│       ▼                                                          │
│  Relevance Evaluator (LLM judges each document):                │
│       │                                                          │
│       ├── ALL documents RELEVANT                                │
│       │   └── Proceed: Use retrieved context → Generate         │
│       │                                                          │
│       ├── SOME documents RELEVANT                               │
│       │   └── Filter: Keep relevant, discard irrelevant         │
│       │       → Supplement with web search                      │
│       │       → Generate with filtered + web context            │
│       │                                                          │
│       └── NO documents RELEVANT                                 │
│           └── Fallback: Web search / different index            │
│               → Generate with fallback context                  │
│               → OR say "I don't have that information"          │
│                                                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## 8. Self-RAG ⭐⭐

LLM decides whether to retrieve, evaluates relevance, and checks its own output.

```
┌────────────────────────────────────────────────────────────────┐
│                        SELF-RAG                                   │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Query                                                      │
│      │                                                           │
│      ▼                                                           │
│  [RETRIEVE?] → LLM decides: "Do I need external knowledge?"    │
│      │                                                           │
│      ├── No → Generate directly from parametric knowledge       │
│      │                                                           │
│      └── Yes → Retrieve documents                               │
│              │                                                    │
│              ▼                                                    │
│         [RELEVANT?] → LLM evaluates each document               │
│              │                                                    │
│              ├── Irrelevant → Discard, try another query         │
│              │                                                    │
│              └── Relevant → Generate with context                │
│                       │                                          │
│                       ▼                                          │
│                  [SUPPORTED?] → Check if output is grounded     │
│                       │                                          │
│                       ├── Not supported → Regenerate            │
│                       │                                          │
│                       └── Supported → [USEFUL?]                 │
│                                │                                 │
│                                ├── Not useful → Regenerate      │
│                                │                                 │
│                                └── Useful → Return answer       │
│                                                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## 9. Production RAG Architecture ⭐⭐⭐

Full system design for enterprise RAG.

```
┌─────────────────────────────────────────────────────────────────────┐
│                 PRODUCTION RAG SYSTEM ARCHITECTURE                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ DATA INGESTION LAYER                                          │   │
│  │                                                                │   │
│  │  Sources:                  Processing:          Storage:       │   │
│  │  ├── S3/Blob (PDFs)       ├── Document Parser   ├── PgVector │   │
│  │  ├── Confluence/Wiki      ├── Table Extractor   ├── Redis    │   │
│  │  ├── Databases (CDC)      ├── Chunker           │   (cache)  │   │
│  │  ├── APIs                 ├── Embedder          ├── S3       │   │
│  │  ├── Email                ├── Metadata Tagger   │   (source) │   │
│  │  └── Slack/Teams          └── Quality Filter    └── Graph DB │   │
│  │                                                                │   │
│  │  Orchestration: Kafka / Airflow / Event-driven pipeline       │   │
│  │  Schedule: Real-time (CDC) + Batch (nightly full re-index)    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ QUERY LAYER                                                    │   │
│  │                                                                │   │
│  │  API Gateway                                                   │   │
│  │      │                                                         │   │
│  │      ▼                                                         │   │
│  │  Query Router (classifies intent)                             │   │
│  │      │                                                         │   │
│  │      ├── FAQ → Cached answers (Redis)                         │   │
│  │      ├── Factual → RAG pipeline                               │   │
│  │      ├── Analytical → SQL + RAG                               │   │
│  │      ├── Conversational → Chat memory + RAG                   │   │
│  │      └── Out-of-scope → Polite decline                       │   │
│  │                                                                │   │
│  │  RAG Pipeline:                                                 │   │
│  │  Query Rewrite → Hybrid Search → Rerank → Compress → Generate│   │
│  │                                                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ EVALUATION & MONITORING                                        │   │
│  │                                                                │   │
│  │  ├── Retrieval metrics (precision, recall, MRR)               │   │
│  │  ├── Generation metrics (faithfulness, relevance)             │   │
│  │  ├── Latency tracking (P50, P95, P99)                        │   │
│  │  ├── Cost monitoring (tokens used, API calls)                 │   │
│  │  ├── User feedback (thumbs up/down, corrections)              │   │
│  │  ├── Hallucination detection (automated checks)               │   │
│  │  └── A/B testing (compare pipeline variants)                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ INFRASTRUCTURE                                                 │   │
│  │                                                                │   │
│  │  ├── LLM: OpenAI / Azure OpenAI / Bedrock (with fallback)    │   │
│  │  ├── Embeddings: text-embedding-3-small (cached)              │   │
│  │  ├── Vector DB: PgVector / Pinecone / Weaviate                │   │
│  │  ├── Cache: Redis (query cache, embedding cache)              │   │
│  │  ├── Compute: AKS / EKS (auto-scaling)                       │   │
│  │  ├── Queue: Kafka (ingestion events)                          │   │
│  │  └── Monitoring: App Insights / Prometheus + Grafana          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 10. Chunking Strategies Deep Dive ⭐⭐⭐

| Strategy | How It Works | Best For |
|----------|-------------|----------|
| Fixed-size | Split every N tokens with overlap | Simple documents, general use |
| Recursive | Split by paragraph → sentence → word | Markdown, documentation |
| Semantic | Group by meaning (embedding similarity) | Mixed-topic documents |
| Document-structure | Use headers, sections, lists | Technical docs, manuals |
| Sentence-window | Each chunk = one sentence + surrounding context | Precise retrieval |
| Parent-child | Small chunks for retrieval, return parent section | Balance precision + context |
| Agentic | LLM decides chunk boundaries | Complex documents |

```
SEMANTIC CHUNKING:

Traditional (fixed):
"...Spring Boot auto-configuration... | ...cut here... | ...handles JPA setup..."
→ Cuts in the middle of a concept!

Semantic (by meaning):
"[Chunk 1: Everything about auto-configuration]"
"[Chunk 2: Everything about JPA setup]"
→ Each chunk is a coherent unit

Implementation:
1. Split into sentences
2. Embed each sentence
3. Compare adjacent sentence embeddings
4. If similarity drops below threshold → chunk boundary
```

---

## 11. Retrieval Strategies ⭐⭐⭐

### Hybrid Search (Vector + Keyword)

```
User Query: "How to configure HikariCP connection pool timeout in Spring Boot"

Vector search alone:
- Finds "Spring Boot database configuration" (semantically similar)
- Might miss the specific "HikariCP timeout" setting

Keyword (BM25) search alone:
- Finds exact matches for "HikariCP" and "timeout"
- Might miss relevant content with different wording

Hybrid (combine both):
- Vector finds semantically relevant Spring Boot DB content
- BM25 finds exact HikariCP mentions
- Reciprocal Rank Fusion (RRF) combines rankings
- Result: Best of both worlds

RRF Formula:
score(doc) = Σ 1 / (k + rank_i(doc))
where k = 60 (constant), rank_i = rank in each search method
```

### Multi-Index Search

```
Query: "What's the SLA for our payment service?"

Route to multiple indexes:
├── Index 1: Technical documentation → SLA configuration details
├── Index 2: Runbooks → SLA monitoring procedures
├── Index 3: Architecture docs → Service dependencies
└── Merge results, deduplicate, rerank
```

### Hypothetical Document Embeddings (HyDE)

```
Problem: Short queries have weak embeddings
Query: "timeout error" → sparse embedding

HyDE approach:
1. Ask LLM: "Write a passage that would answer: timeout error"
2. LLM generates: "When a service call exceeds the configured timeout
   duration, a TimeoutException is thrown. In Spring Boot, you can
   configure timeouts using spring.http.client.connect-timeout and
   spring.http.client.read-timeout properties..."
3. Embed this hypothetical document (rich embedding!)
4. Search with the hypothetical doc's embedding
5. Retrieve real documents that are similar to the hypothetical

Result: Much better retrieval for vague queries
```

---

## 12. RAG Optimization Patterns ⭐⭐⭐

### Caching Layer
```
┌─────────────────────────────────────────────┐
│ Cache Strategy for RAG                       │
│                                              │
│ Layer 1: Exact query cache (Redis)           │
│ "What is our refund policy?" → cached answer │
│                                              │
│ Layer 2: Semantic query cache                │
│ Embed query → find similar cached queries    │
│ If similarity > 0.95 → return cached answer  │
│                                              │
│ Layer 3: Embedding cache                     │
│ Same document chunk → don't re-embed         │
│                                              │
│ Layer 4: LLM response cache                  │
│ Same context + same query → cached response  │
└─────────────────────────────────────────────┘
```

### Feedback Loop
```
User asks question → RAG answers → User provides feedback
                                         │
                                         ├── 👍 Good answer
                                         │   └── Log as positive example
                                         │
                                         └── 👎 Bad answer
                                             ├── Was retrieval bad? (wrong docs)
                                             │   └── Improve chunking/embeddings
                                             └── Was generation bad? (wrong answer from right docs)
                                                 └── Improve prompt/model
```

---

## 13. RAG for Java/Spring Boot Architecture ⭐⭐⭐

```
┌─────────────────────────────────────────────────────────────┐
│              SPRING BOOT RAG MICROSERVICE                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  REST API Layer (Spring MVC)                                 │
│  ├── POST /api/documents/ingest (upload & index)            │
│  ├── POST /api/chat (RAG query)                             │
│  ├── GET  /api/chat/stream (SSE streaming)                  │
│  └── POST /api/feedback (user ratings)                      │
│                                                              │
│  Service Layer                                               │
│  ├── DocumentIngestionService                                │
│  │   ├── DocumentReader (PDF, HTML, DOCX)                   │
│  │   ├── ChunkingService (recursive, semantic)              │
│  │   ├── MetadataEnricher (tags, categories, dates)         │
│  │   └── VectorStore.add(chunks)                            │
│  │                                                           │
│  ├── RAGQueryService                                         │
│  │   ├── QueryRewriter (expand/clarify)                     │
│  │   ├── HybridRetriever (vector + keyword)                 │
│  │   ├── Reranker (cross-encoder)                           │
│  │   ├── ContextBuilder (assemble prompt)                   │
│  │   └── ChatClient.call() (generate answer)                │
│  │                                                           │
│  └── EvaluationService                                       │
│      ├── RetrievalMetrics                                    │
│      ├── FaithfulnessChecker                                 │
│      └── FeedbackCollector                                   │
│                                                              │
│  Infrastructure                                              │
│  ├── PgVector (vector storage)                              │
│  ├── Redis (query cache, session memory)                    │
│  ├── Kafka (async document ingestion events)                │
│  ├── Azure OpenAI / OpenAI (LLM + embeddings)              │
│  └── Application Insights (observability)                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Spring AI RAG with All Patterns

```java
@Service
public class AdvancedRAGService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SearchClient searchClient; // For BM25/keyword
    private final RedisTemplate<String, String> cache;

    // HYBRID SEARCH
    public List<Document> hybridRetrieve(String query, int topK) {
        // Vector search
        List<Document> vectorResults = vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(topK * 2)
        );

        // Keyword search (Elasticsearch / Azure Cognitive Search)
        List<Document> keywordResults = searchClient.search(query, topK * 2);

        // Reciprocal Rank Fusion
        return reciprocalRankFusion(vectorResults, keywordResults, topK);
    }

    // QUERY REWRITING
    public String rewriteQuery(String originalQuery) {
        return chatClient.prompt()
            .system("Rewrite this query to be more specific for document retrieval. " +
                   "Return only the rewritten query, nothing else.")
            .user(originalQuery)
            .call()
            .content();
    }

    // FULL ADVANCED RAG PIPELINE
    public RAGResponse query(String userQuery, String sessionId) {
        // 1. Check cache
        String cached = cache.opsForValue().get("rag:" + userQuery.hashCode());
        if (cached != null) return RAGResponse.fromCache(cached);

        // 2. Rewrite query
        String rewrittenQuery = rewriteQuery(userQuery);

        // 3. Hybrid retrieval
        List<Document> retrieved = hybridRetrieve(rewrittenQuery, 10);

        // 4. Rerank (cross-encoder or LLM-based)
        List<Document> reranked = rerank(userQuery, retrieved, 5);

        // 5. Generate with citations
        String answer = generateWithCitations(userQuery, reranked);

        // 6. Cache result
        cache.opsForValue().set("rag:" + userQuery.hashCode(), answer,
            Duration.ofMinutes(30));

        return new RAGResponse(answer, reranked, rewrittenQuery);
    }

    private String generateWithCitations(String query, List<Document> docs) {
        String context = docs.stream()
            .map(d -> "[Source: " + d.getMetadata().get("source") + "]\n" + d.getContent())
            .collect(Collectors.joining("\n\n---\n\n"));

        return chatClient.prompt()
            .system("""
                Answer the question based ONLY on the provided context.
                Include [Source: filename] citations for each claim.
                If the context doesn't contain the answer, say "I don't have that information."
                """)
            .user("Context:\n" + context + "\n\nQuestion: " + query)
            .call()
            .content();
    }
}
```

---

## 14. RAG vs Fine-Tuning vs Long Context ⭐⭐⭐

| Factor | RAG | Fine-Tuning | Long Context Window |
|--------|-----|-------------|---------------------|
| Data freshness | Real-time (just update vector DB) | Stale (needs retraining) | Real-time (stuff all in context) |
| Cost | Low (just storage + retrieval) | High (training GPU hours) | High (pay per token, lots of tokens) |
| Accuracy | High (exact quotes from source) | Medium (learned patterns) | High but expensive |
| Scalability | Millions of documents | Limited by training data size | Limited by context window |
| Explainability | High (show source documents) | Low (black box) | Medium |
| Use case | Knowledge bases, docs, Q&A | Style/format, domain expertise | Small document sets, summarization |
| When to use | Most production apps | Need specific tone/behavior | < 100 pages total |

**Decision framework:**
```
Need to answer from specific documents? → RAG
Need the model to write in a specific style? → Fine-tuning
Have < 100 pages and budget for tokens? → Long context
Need real-time data accuracy? → RAG
Need to change the model's behavior? → Fine-tuning
```

---

## Interview Questions

### Q: Explain the different RAG architectures and when to use each.
**A:**
- **Naive RAG**: Simple retrieve-and-generate. Fine for POCs and simple Q&A.
- **Advanced RAG**: Adds query rewriting, hybrid search, reranking, compression. Use for production with quality requirements.
- **Modular RAG**: Mix-and-match components. Use when different query types need different pipelines.
- **Agentic RAG**: LLM dynamically decides what to retrieve. Use for complex, multi-step questions.
- **Graph RAG**: Knowledge graph + vectors. Use for relationship-heavy data (org charts, legal, medical).
- **CRAG/Self-RAG**: Self-correcting. Use when hallucination prevention is critical (healthcare, finance).

### Q: How do you optimize RAG retrieval quality?
**A:**
1. **Chunking**: Semantic chunking over fixed-size, with overlap
2. **Hybrid search**: Vector + BM25 with Reciprocal Rank Fusion
3. **Query rewriting**: LLM rewrites vague queries before retrieval
4. **Reranking**: Cross-encoder scores top-20 → select top-5
5. **Metadata filtering**: Narrow search scope (date, category, source)
6. **HyDE**: Generate hypothetical answer, embed that for search
7. **Multi-query**: Generate 3 search queries per user question

### Q: How do you prevent hallucinations in RAG?
**A:**
1. Strong system prompt: "Answer ONLY from context, say 'I don't know' otherwise"
2. Citation requirement: Force the model to cite [Source: X] for each claim
3. Post-generation verification: LLM checks if answer is grounded in context
4. CRAG pattern: Evaluate retrieval quality, fallback if irrelevant
5. Temperature = 0 or low: Reduce creative responses
6. Faithfulness evaluation: Automated checks on answer vs source
7. User feedback: Flag hallucinations for pipeline improvement

### Q: Design a production RAG system for a company knowledge base.
**A:**
1. **Ingestion**: Kafka-driven pipeline — new docs trigger chunking + embedding + indexing
2. **Storage**: PgVector for vectors, Elasticsearch for keyword/BM25, Redis for cache
3. **Query**: API gateway → query rewrite → hybrid search → rerank → generate with citations
4. **Caching**: Exact match cache + semantic similarity cache (avoid redundant LLM calls)
5. **Monitoring**: Retrieval precision, faithfulness scores, latency P95, cost per query
6. **Feedback**: Thumbs up/down → identify bad retrievals vs bad generation → targeted improvements
7. **Security**: RBAC on documents (user can only search docs they have access to)
8. **Scaling**: Async ingestion, cached embeddings, horizontal scaling of query layer
