# Vector Databases ⭐⭐⭐⭐⭐

## Overview
Vector databases store, index, and search high-dimensional vectors efficiently. They are the backbone of RAG systems, enabling fast similarity search across millions of embeddings.

---

## Why Vector Databases?

Traditional databases search by exact match or keyword. Vector databases search by meaning.

```
Traditional DB:
  SELECT * FROM docs WHERE content LIKE '%Spring Boot%'
  → Only finds docs with exact phrase "Spring Boot"
  → Misses: "Spring framework applications", "Boot starter projects"

Vector DB:
  Find documents similar to embed("How to build Spring Boot apps")
  → Finds: "Spring Boot tutorial", "Getting started with Spring", 
            "Building REST APIs with Spring framework"
  → Understands meaning, not just keywords
```

---

## Core Concepts

### Vector Storage
```
┌─────────────────────────────────────────────────┐
│  Vector Database                                 │
│                                                  │
│  ID    Vector [1536 dims]           Metadata     │
│  ─────────────────────────────────────────────── │
│  1     [0.21, -0.45, 0.73, ...]    {source: "doc1.pdf", page: 3}  │
│  2     [0.15, -0.32, 0.81, ...]    {source: "doc1.pdf", page: 4}  │
│  3     [-0.67, 0.23, -0.12, ...]   {source: "doc2.pdf", page: 1}  │
│  ...                                             │
│  1M    [0.33, 0.12, -0.89, ...]    {source: "doc500.pdf", page: 7}│
└─────────────────────────────────────────────────┘
```

### Similarity Search
```python
# Query: "How does dependency injection work?"
query_vector = embed("How does dependency injection work?")

# Vector DB finds top-K most similar vectors
results = vector_db.similarity_search(
    query_vector=query_vector,
    top_k=5
)
# Returns 5 most semantically similar document chunks
```

### ANN (Approximate Nearest Neighbor) Search
Exact similarity search on millions of vectors is slow. ANN trades tiny accuracy for massive speed.

```
Exact search: Check ALL 1 million vectors → 100% accurate, very slow
ANN search:   Check ~1% of vectors       → 95-99% accurate, very fast

Algorithms:
- HNSW (Hierarchical Navigable Small World) — most popular
- IVF (Inverted File Index) — good for large datasets
- PQ (Product Quantization) — memory efficient

Trade-off: recall (accuracy) vs latency (speed)
```

---

## Metadata & Filtering

```python
# Store vectors with metadata
vector_db.upsert(
    vectors=[
        {
            "id": "doc_1_chunk_3",
            "values": embedding,
            "metadata": {
                "source": "spring-boot-guide.pdf",
                "page": 3,
                "category": "backend",
                "date": "2024-01-15",
                "author": "John Doe"
            }
        }
    ]
)

# Search with metadata filter
results = vector_db.query(
    vector=query_embedding,
    top_k=5,
    filter={
        "category": "backend",
        "date": {"$gte": "2024-01-01"}
    }
)
# Only searches within backend docs from 2024+
```

---

## Indexing

How vector databases organize vectors for fast search.

### HNSW (Most Common)
```
Multi-layer graph structure:

Layer 2:  A ─── B (few nodes, fast navigation)
          |     |
Layer 1:  A ─ C ─ B ─ D (more nodes)
          |   |   |   |
Layer 0:  A-C-E-B-F-D-G (all nodes, fine-grained)

Search: Start at top layer, navigate down to find nearest neighbors
Speed: O(log N) — very fast even with millions of vectors
```

### IVF (Inverted File Index)
```
1. Cluster vectors into groups (centroids)
2. At search time, find nearest centroids first
3. Only search within those clusters

Faster for very large datasets (100M+ vectors)
Less accurate than HNSW but uses less memory
```

---

## Hybrid Search

Combine vector similarity with keyword search for better results.

```python
# Hybrid search combines:
# 1. Semantic search (embeddings) — understands meaning
# 2. Keyword search (BM25/TF-IDF) — exact term matching

# Example: Query "Java NullPointerException fix"
# Semantic finds: docs about handling null in Java
# Keyword finds: docs with exact "NullPointerException" term
# Hybrid: merges both result sets with weighted scoring

results = vector_db.hybrid_search(
    query_text="Java NullPointerException fix",
    query_vector=embed("Java NullPointerException fix"),
    alpha=0.7,  # 70% semantic, 30% keyword
    top_k=5
)
```

---

## Vector Database Options

### PostgreSQL + pgvector ⭐ (Recommended Starting Point)

```sql
-- Enable extension
CREATE EXTENSION vector;

-- Create table with vector column
CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    content TEXT,
    embedding vector(1536),  -- 1536 dimensions
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create index for fast search
CREATE INDEX ON documents 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);

-- Or HNSW index (better accuracy)
CREATE INDEX ON documents 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Insert document with embedding
INSERT INTO documents (content, embedding, metadata)
VALUES (
    'Spring Boot simplifies application development',
    '[0.21, -0.45, 0.73, ...]',
    '{"source": "docs", "category": "backend"}'
);

-- Similarity search
SELECT content, metadata,
       1 - (embedding <=> query_embedding) AS similarity
FROM documents
WHERE metadata->>'category' = 'backend'
ORDER BY embedding <=> '[0.15, -0.32, 0.81, ...]'  -- cosine distance
LIMIT 5;
```

**Why pgvector?**
- You already know PostgreSQL
- Single database for vectors + relational data
- ACID transactions
- Joins with other tables
- No additional infrastructure
- Good for <10M vectors

### Pinecone (Managed)
```python
import pinecone

pinecone.init(api_key="your-key", environment="us-east1-gcp")
index = pinecone.Index("my-index")

# Upsert vectors
index.upsert(
    vectors=[
        ("id1", embedding1, {"source": "doc1.pdf"}),
        ("id2", embedding2, {"source": "doc2.pdf"})
    ]
)

# Query
results = index.query(
    vector=query_embedding,
    top_k=5,
    filter={"source": "doc1.pdf"},
    include_metadata=True
)
```

**Pros:** Fully managed, scales easily, fast
**Cons:** Vendor lock-in, cost at scale

### Weaviate (Open Source)
```python
import weaviate

client = weaviate.Client("http://localhost:8080")

# Create schema
client.schema.create_class({
    "class": "Document",
    "vectorizer": "text2vec-openai",
    "properties": [
        {"name": "content", "dataType": ["text"]},
        {"name": "source", "dataType": ["string"]}
    ]
})

# Add data (auto-embeds if vectorizer configured)
client.data_object.create(
    data_object={"content": "Spring Boot guide", "source": "docs"},
    class_name="Document"
)

# Hybrid search
results = client.query.get("Document", ["content", "source"]) \
    .with_hybrid(query="Spring Boot", alpha=0.7) \
    .with_limit(5) \
    .do()
```

### Milvus (Open Source, High Performance)
```python
from pymilvus import connections, Collection, FieldSchema, CollectionSchema, DataType

connections.connect("default", host="localhost", port="19530")

# Define schema
fields = [
    FieldSchema(name="id", dtype=DataType.INT64, is_primary=True),
    FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=1536),
    FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=5000)
]
schema = CollectionSchema(fields)
collection = Collection("documents", schema)

# Create HNSW index
collection.create_index("embedding", {
    "index_type": "HNSW",
    "metric_type": "COSINE",
    "params": {"M": 16, "efConstruction": 200}
})
```

---

## Spring AI + pgvector

```java
// application.yml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
  datasource:
    url: jdbc:postgresql://localhost:5432/aidb
    username: postgres
    password: password

// VectorStore usage
@Service
public class DocumentService {
    
    private final VectorStore vectorStore;
    
    public void storeDocument(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
    }
    
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(topK)
                .withSimilarityThreshold(0.7)
                .withFilterExpression("category == 'backend'")
        );
    }
}
```

---

## Comparison Table

| Feature | pgvector | Pinecone | Weaviate | Milvus |
|---------|----------|----------|----------|--------|
| Type | Extension | Managed | Self-host/Cloud | Self-host/Cloud |
| Max vectors | ~10M | Billions | Billions | Billions |
| Hybrid search | With FTS | Yes | Yes | Yes |
| Metadata filter | SQL WHERE | Yes | Yes | Yes |
| ACID | Yes | No | No | No |
| Cost | Free (your DB) | Pay per use | Free/paid | Free/paid |
| Complexity | Low | Low | Medium | Medium |
| Best for | Small-medium, existing PG | Managed, scale | Multimodal | High performance |

---

## Interview Questions

**Q: Why use a vector database instead of a regular database?**
Vector databases are optimized for high-dimensional similarity search using ANN algorithms (HNSW, IVF). A regular database would need brute-force comparison against every vector — O(n) per query. Vector DBs achieve O(log n) through specialized indexing, making them practical for millions of vectors.

**Q: What is HNSW and why is it popular?**
Hierarchical Navigable Small World is a graph-based ANN algorithm. It builds a multi-layer graph where top layers have few long-range connections for fast navigation and bottom layers have fine-grained connections for accuracy. It offers the best recall-latency tradeoff for most workloads.

**Q: When would you choose pgvector over Pinecone?**
pgvector when: you already use PostgreSQL, need ACID transactions, want to join vector search with relational data, have <10M vectors, want to minimize infrastructure, or need to keep data in your own DB for compliance. Pinecone when: you need to scale beyond 10M vectors, want zero-ops management, or need the fastest possible search at scale.

**Q: How does metadata filtering work with vector search?**
Pre-filtering: Filter by metadata first, then do vector search on the subset. Post-filtering: Do vector search first, then filter results. Pre-filtering is more accurate but may miss relevant results. Most vector DBs use a hybrid approach for optimal performance.

**Q: How do you handle updating documents in a vector database?**
Delete the old vector(s) by ID, re-chunk the updated document, re-embed the new chunks, and insert new vectors. For pgvector, you can use SQL UPDATE. Track document versions with metadata to know when re-embedding is needed.

---

## Key Takeaways

1. **Vector DBs enable semantic search** — find by meaning, not keywords
2. **pgvector is your best starting point** — leverage existing PostgreSQL skills
3. **HNSW indexing** provides fast, accurate approximate nearest neighbor search
4. **Metadata filtering** lets you scope searches to relevant subsets
5. **Hybrid search** (vector + keyword) often outperforms either alone
6. **Never mix embedding models** — all vectors in a collection must use the same model
7. **Vector DBs are essential for RAG** — they store and retrieve the context
