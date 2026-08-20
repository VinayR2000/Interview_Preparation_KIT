# Embeddings ⭐⭐⭐⭐⭐

## Overview
Embeddings are the foundation of semantic search, RAG, and recommendation systems. They convert text (or images, audio) into dense numerical vectors that capture meaning. Understanding embeddings is essential for building any AI application that needs to "understand" content.

---

## What is an Embedding?

An embedding is a numerical representation (vector) of text that captures its semantic meaning.

```
Text: "Java developer"
         ↓
   Embedding Model
         ↓
Vector: [0.21, -0.45, 0.73, 0.12, -0.89, 0.33, ...]
        (768 or 1536 dimensions)
```

**Key insight:** Similar meanings → similar vectors, regardless of exact words used.

```
"Java developer"      → [0.21, -0.45, 0.73, ...]
"Java programmer"     → [0.20, -0.44, 0.72, ...]  ← Very similar!
"Python developer"    → [0.18, -0.40, 0.65, ...]  ← Somewhat similar
"Chocolate cake"      → [-0.67, 0.23, -0.45, ...] ← Very different
```

---

## Text → Vector

```python
from openai import OpenAI

client = OpenAI()

# Single text embedding
response = client.embeddings.create(
    model="text-embedding-3-small",
    input="Java developer with Spring Boot experience"
)

embedding = response.data[0].embedding
print(f"Dimensions: {len(embedding)}")  # 1536
print(f"First 5 values: {embedding[:5]}")
# [0.0123, -0.0456, 0.0789, -0.0234, 0.0567]

# Batch embeddings
texts = [
    "Machine learning engineer",
    "Data scientist",
    "Frontend developer"
]
response = client.embeddings.create(
    model="text-embedding-3-small",
    input=texts
)
embeddings = [d.embedding for d in response.data]
```

---

## Vector Dimensions

The number of values in the embedding vector.

```
Model                        Dimensions    Quality
text-embedding-3-small       1536          Good (cost-effective)
text-embedding-3-large       3072          Better (more expensive)
all-MiniLM-L6-v2            384           Open source, fast
BAAI/bge-large-en           1024          Open source, high quality
Amazon Titan Embeddings      1536          AWS native

More dimensions = more information captured = better similarity
But also = more storage = slower search
```

### What Each Dimension Represents
```
No single dimension has a clear meaning (unlike manual features).
Together, they encode:
- Topic/domain (technology, food, sports)
- Sentiment (positive/negative)
- Specificity (general vs detailed)
- Relationships between concepts
- Contextual meaning

Think of it as: 1536 different "aspects" of meaning
```

---

## Semantic Similarity

```python
import numpy as np
from numpy.linalg import norm

def cosine_similarity(a, b):
    return np.dot(a, b) / (norm(a) * norm(b))

# Get embeddings
texts = [
    "How to learn Java programming",        # Query
    "Tutorial for Java development",         # Semantically similar
    "Best Java learning resources",          # Semantically similar
    "Python web development guide",          # Somewhat related (programming)
    "Best Italian restaurants nearby"        # Unrelated
]

embeddings = get_embeddings(texts)

query = embeddings[0]
for i, emb in enumerate(embeddings[1:], 1):
    sim = cosine_similarity(query, emb)
    print(f"'{texts[0]}' vs '{texts[i]}': {sim:.3f}")

# Output:
# vs "Tutorial for Java development":     0.92  (very similar)
# vs "Best Java learning resources":      0.89  (very similar)
# vs "Python web development guide":      0.65  (somewhat related)
# vs "Best Italian restaurants nearby":   0.12  (unrelated)
```

---

## Cosine Similarity vs Euclidean Distance

### Cosine Similarity
Measures angle between vectors (direction = meaning).

```python
# Range: -1 to 1 (normalized embeddings: 0 to 1)
# 1.0 = identical meaning
# 0.0 = unrelated
# -1.0 = opposite meaning

cosine_sim = np.dot(a, b) / (norm(a) * norm(b))
```

### Euclidean Distance
Measures straight-line distance between vector endpoints.

```python
# Range: 0 to ∞
# 0 = identical
# Large = different

euclidean_dist = np.linalg.norm(a - b)
```

### When to Use Which
```
Cosine Similarity (preferred for text):
- Invariant to vector magnitude
- "Java developer" (short) ≈ "experienced Java developer" (long)
- Focus on meaning direction, not document length

Euclidean Distance:
- Affected by magnitude
- Better for spatial/geometric data
- Some vector DBs use it for performance reasons

In practice: Most vector databases default to cosine similarity for text
```

---

## Embedding Models

### Closed Source
```
OpenAI:
  - text-embedding-3-small (1536d, cheapest, good quality)
  - text-embedding-3-large (3072d, best quality)
  
AWS Bedrock:
  - Amazon Titan Embeddings V2 (1024d)
  - Cohere Embed (1024d)

Google:
  - text-embedding-004 (768d)
```

### Open Source (run locally or self-host)
```
Sentence Transformers:
  - all-MiniLM-L6-v2 (384d, fast, good quality)
  - all-mpnet-base-v2 (768d, better quality)
  
BAAI:
  - bge-large-en-v1.5 (1024d, top quality)
  - bge-small-en-v1.5 (384d, fast)
  
Nomic:
  - nomic-embed-text-v1.5 (768d)
```

### Choosing an Embedding Model
```
Priority 1: Quality of similarity (benchmark scores)
Priority 2: Dimension size (storage/speed tradeoff)
Priority 3: Cost per token
Priority 4: Latency
Priority 5: Context window (how much text per embedding)

IMPORTANT: You CANNOT mix embedding models!
Documents embedded with Model A must be searched with Model A.
Switching models requires re-embedding everything.
```

---

## Document Embeddings vs Query Embeddings

```python
# Document embedding: embed once, store forever
doc_text = """
Spring Boot is an open-source framework that simplifies 
building production-ready Spring applications. It provides 
auto-configuration, embedded servers, and opinionated defaults.
"""
doc_embedding = embed(doc_text)  # Store in vector DB

# Query embedding: embed at search time
query = "How to create a REST API with Spring?"
query_embedding = embed(query)  # Compare against stored docs

# Similarity search
similarity = cosine_similarity(query_embedding, doc_embedding)
# High similarity → this document is relevant to the query
```

### Asymmetric Search
Some models are trained for asymmetric search (short query → long document).

```python
# Query: short, like a question
# Document: longer, like a paragraph/page

# Some models use prefixes:
query_text = "query: How to use Spring Boot?"
doc_text = "passage: Spring Boot simplifies application development..."

# The model understands the asymmetry
```

---

## Practical: Building a Semantic Search

```python
import numpy as np
from openai import OpenAI

client = OpenAI()

# 1. Embed your documents (done once)
documents = [
    "Spring Boot auto-configuration simplifies setup",
    "Docker containers provide isolated environments",
    "Kafka enables real-time event streaming",
    "PostgreSQL supports JSON and full-text search",
    "Redis provides in-memory caching for performance"
]

def get_embeddings(texts):
    response = client.embeddings.create(
        model="text-embedding-3-small",
        input=texts
    )
    return [d.embedding for d in response.data]

doc_embeddings = get_embeddings(documents)

# 2. Search function
def semantic_search(query: str, top_k: int = 3):
    query_embedding = get_embeddings([query])[0]
    
    similarities = []
    for i, doc_emb in enumerate(doc_embeddings):
        sim = cosine_similarity(
            np.array(query_embedding), 
            np.array(doc_emb)
        )
        similarities.append((sim, documents[i]))
    
    # Sort by similarity (highest first)
    similarities.sort(reverse=True)
    return similarities[:top_k]

# 3. Use it
results = semantic_search("How to improve application speed?")
# Returns: Redis caching doc, Spring Boot doc (most relevant)

results = semantic_search("messaging between microservices")
# Returns: Kafka doc (most relevant)
```

---

## Spring AI Embeddings

```java
@Service
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(List.of(text), EmbeddingOptions.EMPTY)
        );
        return response.getResult().getOutput();
    }
    
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(texts, EmbeddingOptions.EMPTY)
        );
        return response.getResults().stream()
            .map(r -> r.getOutput())
            .toList();
    }
    
    public double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

---

## Interview Questions

**Q: What are embeddings and why are they important for RAG?**
Embeddings are dense vector representations of text that capture semantic meaning. In RAG, documents are pre-embedded and stored in a vector database. When a user asks a question, the query is embedded and compared against document embeddings using cosine similarity to find the most relevant context to provide to the LLM.

**Q: Why can't you mix different embedding models?**
Each embedding model maps text to a different vector space with different dimensions and learned representations. A vector from Model A exists in a completely different mathematical space than Model B. Comparing them would be meaningless — like comparing coordinates on two different maps.

**Q: How do you choose between embedding models?**
Consider: quality (benchmark scores on your domain), dimensions (storage vs accuracy tradeoff), cost per token, latency, max input length, and whether you need open source (privacy/self-hosting) vs closed (managed service). Always benchmark on your actual data.

**Q: What's the difference between cosine similarity and dot product for embeddings?**
Cosine similarity normalizes for vector magnitude (length-independent), ranging from -1 to 1. Dot product doesn't normalize, so longer vectors get higher scores. For normalized embeddings (unit vectors), they're equivalent. Cosine similarity is preferred for text because document length shouldn't affect semantic similarity.

**Q: How would you handle embedding a document that exceeds the model's token limit?**
Split the document into chunks (typically 500-1000 tokens each) with overlap (50-100 tokens). Embed each chunk separately. Store chunk embeddings with metadata linking back to the source document. During search, retrieve relevant chunks and optionally fetch surrounding context.

---

## Key Takeaways

1. **Embeddings = meaning as numbers** — similar text → similar vectors
2. **Cosine similarity** is the standard metric for text comparison
3. **Never mix embedding models** — each creates its own vector space
4. **1536 dimensions** is the sweet spot for most applications
5. **Embed once, search many times** — amortize the embedding cost
6. **Embeddings are the bridge** between text and vector databases
7. **This enables RAG** — find relevant docs without keyword matching
