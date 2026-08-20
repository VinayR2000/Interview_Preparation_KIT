# RAG — Retrieval-Augmented Generation ⭐⭐⭐⭐⭐

## Overview
RAG is the most important pattern for building production AI applications. It lets you give LLMs access to your private data, current information, and domain knowledge without fine-tuning. This is likely what you'll build most often as an AI engineer.

---

## What is RAG?

RAG = Retrieve relevant documents + Augment the prompt + Generate an answer.

```
Problem: LLMs don't know your company's data, policies, or current information
Solution: Find relevant documents and include them as context in the prompt

Without RAG:
  User: "What is our refund policy?"
  LLM: "I don't have information about your specific refund policy."

With RAG:
  User: "What is our refund policy?"
  → Retrieve: Find refund policy document from vector DB
  → Augment: Add document to prompt as context  
  → Generate: LLM answers using the retrieved context
  LLM: "Your refund policy allows returns within 30 days..."
```

---

## Basic RAG Flow

```
┌─── INDEXING (done once/periodically) ───────────────────────┐
│                                                              │
│  Documents → Chunking → Embeddings → Vector DB              │
│                                                              │
│  PDF, Word,    Split into    Convert to    Store for         │
│  HTML, etc.    smaller       vectors       search            │
│                pieces                                        │
└──────────────────────────────────────────────────────────────┘

┌─── RETRIEVAL & GENERATION (per query) ──────────────────────┐
│                                                              │
│  User Question                                               │
│       ↓                                                      │
│  Embed Question → Similarity Search → Relevant Chunks        │
│       ↓                                                      │
│  Construct Prompt:                                           │
│    "Given this context: [retrieved chunks]                    │
│     Answer this question: [user question]"                   │
│       ↓                                                      │
│  LLM generates answer based on context                       │
│       ↓                                                      │
│  Return Answer to User                                       │
└──────────────────────────────────────────────────────────────┘
```

---

## Document Ingestion

### Document Loaders
```python
# Load different file types
from langchain.document_loaders import (
    PyPDFLoader,
    TextLoader,
    CSVLoader,
    UnstructuredHTMLLoader,
    WebBaseLoader
)

# PDF
loader = PyPDFLoader("manual.pdf")
documents = loader.load()

# Web page
loader = WebBaseLoader("https://docs.example.com/api")
documents = loader.load()

# Multiple files
from pathlib import Path

all_docs = []
for pdf_file in Path("docs/").glob("*.pdf"):
    loader = PyPDFLoader(str(pdf_file))
    all_docs.extend(loader.load())
```

### Spring AI Document Loading
```java
// PDF loading
Resource pdfResource = new ClassPathResource("manual.pdf");
PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource);
List<Document> documents = reader.get();
```

---

## Chunking ⭐

Breaking documents into smaller pieces for embedding and retrieval.

### Why Chunk?
```
1. Embedding models have token limits (typically 512-8192 tokens)
2. Smaller chunks = more precise retrieval
3. LLM context window is limited
4. Better relevance scoring on focused content
```

### Chunk Size
```
Too small (50 tokens):
  "Spring Boot is a framework."
  → Lost context, not enough information to be useful

Too large (5000 tokens):
  [Entire chapter about Spring Boot]
  → Too broad, dilutes relevance, wastes context window

Sweet spot (200-1000 tokens):
  "Spring Boot auto-configuration automatically configures your
   application based on the dependencies you've added. For example,
   if spring-data-jpa is on the classpath, it automatically configures
   a DataSource and EntityManager."
  → Focused, self-contained, informative
```

### Chunk Overlap
```
Without overlap:
  Chunk 1: "...Spring Boot uses application.properties"
  Chunk 2: "for configuration. You can define database..."
  → Context split! Neither chunk has the full thought.

With overlap (50-100 tokens):
  Chunk 1: "...Spring Boot uses application.properties for configuration."
  Chunk 2: "application.properties for configuration. You can define database..."
  → Overlap preserves context at boundaries
```

### Chunking Strategies
```python
from langchain.text_splitter import (
    RecursiveCharacterTextSplitter,
    TokenTextSplitter
)

# Recursive (best general-purpose)
splitter = RecursiveCharacterTextSplitter(
    chunk_size=1000,
    chunk_overlap=200,
    separators=["\n\n", "\n", ". ", " ", ""]
)
chunks = splitter.split_documents(documents)

# Token-based (aligned with model limits)
splitter = TokenTextSplitter(
    chunk_size=500,
    chunk_overlap=50
)
```

---

## Embeddings & Storage

```python
from openai import OpenAI

client = OpenAI()

# Embed all chunks
def embed_chunks(chunks):
    texts = [chunk.page_content for chunk in chunks]
    response = client.embeddings.create(
        model="text-embedding-3-small",
        input=texts
    )
    return [d.embedding for d in response.data]

embeddings = embed_chunks(chunks)

# Store in vector DB (pgvector example)
import psycopg2

conn = psycopg2.connect("postgresql://localhost/aidb")
cur = conn.cursor()

for chunk, embedding in zip(chunks, embeddings):
    cur.execute(
        "INSERT INTO documents (content, embedding, metadata) VALUES (%s, %s, %s)",
        (chunk.page_content, embedding, json.dumps(chunk.metadata))
    )
conn.commit()
```

---

## Vector Search & Retrieval

```python
def retrieve(query: str, top_k: int = 5) -> list:
    # Embed the query
    query_embedding = client.embeddings.create(
        model="text-embedding-3-small",
        input=[query]
    ).data[0].embedding
    
    # Search vector DB
    cur.execute("""
        SELECT content, metadata,
               1 - (embedding <=> %s::vector) AS similarity
        FROM documents
        WHERE 1 - (embedding <=> %s::vector) > 0.7  -- similarity threshold
        ORDER BY embedding <=> %s::vector
        LIMIT %s
    """, (query_embedding, query_embedding, query_embedding, top_k))
    
    return cur.fetchall()
```

---

## Context Injection & Prompt Construction

```python
def generate_answer(query: str, retrieved_docs: list) -> str:
    # Build context from retrieved documents
    context = "\n\n---\n\n".join([doc[0] for doc in retrieved_docs])
    
    # Construct RAG prompt
    prompt = f"""Answer the user's question based ONLY on the provided context.
If the answer is not in the context, say "I don't have enough information to answer that."

Context:
{context}

Question: {query}

Answer:"""
    
    response = client.chat.completions.create(
        model="gpt-4",
        messages=[
            {"role": "system", "content": "You are a helpful assistant that answers questions based on provided context."},
            {"role": "user", "content": prompt}
        ],
        temperature=0.3  # Lower for factual accuracy
    )
    
    return response.choices[0].message.content
```

---

## Complete Basic RAG Pipeline

```python
class BasicRAG:
    def __init__(self):
        self.client = OpenAI()
        self.db = connect_to_pgvector()
    
    # === INDEXING ===
    def ingest(self, file_path: str):
        # 1. Load document
        documents = load_document(file_path)
        
        # 2. Chunk
        chunks = RecursiveCharacterTextSplitter(
            chunk_size=1000, chunk_overlap=200
        ).split_documents(documents)
        
        # 3. Embed
        embeddings = self.embed([c.page_content for c in chunks])
        
        # 4. Store
        for chunk, embedding in zip(chunks, embeddings):
            self.db.insert(chunk.page_content, embedding, chunk.metadata)
    
    # === RETRIEVAL & GENERATION ===
    def query(self, question: str) -> str:
        # 1. Embed question
        query_embedding = self.embed([question])[0]
        
        # 2. Retrieve relevant chunks
        relevant_docs = self.db.similarity_search(query_embedding, top_k=5)
        
        # 3. Build prompt with context
        context = "\n\n".join([doc.content for doc in relevant_docs])
        
        # 4. Generate answer
        return self.generate(question, context)
    
    def embed(self, texts):
        response = self.client.embeddings.create(
            model="text-embedding-3-small", input=texts
        )
        return [d.embedding for d in response.data]
    
    def generate(self, question, context):
        response = self.client.chat.completions.create(
            model="gpt-4",
            messages=[
                {"role": "system", "content": "Answer based on the context provided."},
                {"role": "user", "content": f"Context:\n{context}\n\nQuestion: {question}"}
            ]
        )
        return response.choices[0].message.content
```

---

## Advanced RAG Techniques

### Metadata Filtering
```python
# Only search within specific document categories
results = vector_db.search(
    query_embedding,
    filter={"category": "hr-policies", "year": 2024}
)
```

### Hybrid Search
```python
# Combine semantic + keyword search
results = vector_db.hybrid_search(
    query_text=question,          # For keyword/BM25
    query_vector=query_embedding,  # For semantic
    alpha=0.7                      # 70% semantic, 30% keyword
)
```

### Reranking
```python
# Initial retrieval: fast, broad (top 20)
candidates = vector_db.search(query_embedding, top_k=20)

# Reranking: slower, more accurate (pick top 5)
from sentence_transformers import CrossEncoder
reranker = CrossEncoder('cross-encoder/ms-marco-MiniLM-L-6-v2')

pairs = [(question, doc.content) for doc in candidates]
scores = reranker.predict(pairs)

# Sort by reranker score
reranked = sorted(zip(candidates, scores), key=lambda x: x[1], reverse=True)
final_docs = [doc for doc, score in reranked[:5]]
```

### Query Expansion / Rewriting
```python
# Original query might be vague
original_query = "How to fix the login issue?"

# Rewrite for better retrieval
rewrite_prompt = f"""Rewrite this query to be more specific for document search.
Original: {original_query}
Rewritten:"""

better_query = llm.generate(rewrite_prompt)
# "authentication failure troubleshooting login error Spring Security"
```

### Multi-Query Retrieval
```python
# Generate multiple search queries from one question
queries = llm.generate(f"""
Generate 3 different search queries for: "{question}"
Each should approach the topic from a different angle.
""")
# ["Spring Boot authentication configuration",
#  "login endpoint security filter chain", 
#  "user credential validation process"]

# Search with all queries, merge results
all_results = set()
for query in queries:
    results = vector_db.search(embed(query), top_k=3)
    all_results.update(results)
```

### Parent-Child Retrieval
```python
# Store small chunks for precise retrieval
# But return the parent (larger context) to the LLM

# Index: small chunks (200 tokens) → high precision retrieval
# Return: parent section (2000 tokens) → full context for LLM

# When chunk "Spring Boot uses @Autowired" matches:
# Return the entire section about Dependency Injection
```

### Context Compression
```python
# Retrieved docs may have irrelevant parts
# Compress to only the relevant sentences

compression_prompt = f"""
Given this question: {question}
Extract ONLY the sentences relevant to answering it from this document:
{retrieved_doc}
"""
compressed = llm.generate(compression_prompt)
# Feed compressed context to final generation
```

---

## Graph RAG

Combine knowledge graphs with vector search.

```
Documents → Extract entities & relationships → Knowledge Graph
                                                    ↓
Query → Find relevant entities → Traverse graph → Structured context
                                                    ↓
                                              LLM generates answer

Benefits:
- Better for multi-hop reasoning ("Who manages the team that built X?")
- Handles relationships traditional RAG misses
- More explainable (can show the graph path)
```

---

## RAG Evaluation

### Key Metrics
```
Retrieval quality:
- Precision@K: Of retrieved docs, how many are relevant?
- Recall@K: Of all relevant docs, how many were retrieved?
- MRR (Mean Reciprocal Rank): How high is the first relevant result?

Generation quality:
- Faithfulness: Does the answer stick to retrieved context?
- Relevance: Does the answer address the question?
- Groundedness: Is every claim supported by context?
- Hallucination: Does it make up information not in context?
```

```python
# Simple evaluation framework
def evaluate_rag(questions, ground_truth_answers, rag_system):
    results = []
    for question, expected in zip(questions, ground_truth_answers):
        # Get RAG response
        response = rag_system.query(question)
        retrieved_docs = rag_system.retrieve(question)
        
        # Evaluate with LLM-as-judge
        evaluation = llm.generate(f"""
        Question: {question}
        Expected Answer: {expected}
        RAG Answer: {response}
        Retrieved Context: {retrieved_docs}
        
        Score (1-5) on:
        - Correctness: Does it match the expected answer?
        - Faithfulness: Does it only use retrieved context?
        - Completeness: Does it fully answer the question?
        """)
        results.append(evaluation)
    return results
```

---

## Spring AI RAG Implementation

```java
@Service
public class RAGService {
    
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    
    public RAGService(VectorStore vectorStore, ChatClient.Builder builder) {
        this.vectorStore = vectorStore;
        this.chatClient = builder.build();
    }
    
    // Ingestion
    public void ingestDocument(Resource resource) {
        // Load and chunk
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> documents = reader.get();
        
        TokenTextSplitter splitter = new TokenTextSplitter(500, 100);
        List<Document> chunks = splitter.apply(documents);
        
        // Store (embedding happens automatically)
        vectorStore.add(chunks);
    }
    
    // Query with RAG
    public String query(String question) {
        return chatClient.prompt()
            .user(question)
            .advisors(new QuestionAnswerAdvisor(vectorStore))
            .call()
            .content();
    }
    
    // Custom RAG with filtering
    public String queryWithFilter(String question, String category) {
        List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.query(question)
                .withTopK(5)
                .withSimilarityThreshold(0.7)
                .withFilterExpression("category == '" + category + "'")
        );
        
        String context = relevantDocs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));
        
        return chatClient.prompt()
            .system("Answer based only on the provided context. If unsure, say so.")
            .user("Context:\n" + context + "\n\nQuestion: " + question)
            .call()
            .content();
    }
}
```

---

## Interview Questions

**Q: What is RAG and why is it preferred over fine-tuning for most use cases?**
RAG retrieves relevant documents at query time and provides them as context to the LLM. Preferred because: no training required, data stays current (just update the vector DB), cheaper than fine-tuning, works with any LLM, explainable (you can show source documents), and data stays in your control.

**Q: How do you choose chunk size and overlap?**
Chunk size: Balance between specificity (small chunks, precise retrieval) and context (large chunks, self-contained). Start with 500-1000 tokens. Overlap: 10-20% of chunk size prevents context loss at boundaries. Optimize based on retrieval evaluation metrics.

**Q: What's the difference between basic RAG and advanced RAG?**
Basic: chunk → embed → store → retrieve → generate. Advanced adds: query rewriting (better retrieval), hybrid search (semantic + keyword), reranking (better relevance), multi-query (broader coverage), parent-child retrieval (precision + context), metadata filtering (scope control), and evaluation.

**Q: How do you evaluate a RAG system?**
Retrieval: precision@K, recall@K, MRR. Generation: faithfulness (sticks to context), relevance (answers the question), groundedness (claims supported), hallucination rate. Use golden datasets with known answers, automated LLM-as-judge evaluation, and human evaluation for production.

**Q: How do you handle documents that are too large or too complex for simple RAG?**
Hierarchical chunking (summaries + details), parent-child retrieval, table/image extraction with specialized models, graph RAG for relational content, metadata-rich chunks for filtering, and multi-modal approaches for mixed content.

---

## Key Takeaways

1. **RAG = your most important AI pattern** — it's how you make LLMs useful with your data
2. **Chunking quality determines RAG quality** — invest time here
3. **Start simple** (basic RAG), then add advanced techniques as needed
4. **Hybrid search** (vector + keyword) almost always beats pure vector search
5. **Evaluation is essential** — build golden datasets early
6. **Spring AI makes RAG straightforward** in Java applications
7. **pgvector + Spring AI + Spring Boot** = production RAG with minimal infrastructure
