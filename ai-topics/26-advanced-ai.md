# Advanced AI Topics

## Overview
After mastering the fundamentals, these advanced topics expand your capabilities. Focus on these after you're comfortable building production RAG systems with agents and tool calling.

---

## Multimodal AI

Models that process multiple types of input (text, images, audio, video).

```java
// Spring AI with vision model
@Service
public class VisionService {
    
    private final ChatClient chatClient;
    
    public String analyzeImage(Resource imageResource, String question) {
        return chatClient.prompt()
            .user(u -> u
                .text(question)
                .media(MimeTypeUtils.IMAGE_PNG, imageResource))
            .call()
            .content();
    }
    
    // Use cases:
    // - Document OCR and understanding
    // - Image-based product search
    // - Chart/diagram analysis
    // - UI screenshot analysis for testing
}
```

### Speech-to-Text
```python
# Whisper API
from openai import OpenAI

client = OpenAI()

# Transcribe audio
with open("meeting.mp3", "rb") as audio_file:
    transcript = client.audio.transcriptions.create(
        model="whisper-1",
        file=audio_file
    )

# Use case: Meeting summaries, voice-based RAG queries
```

### Text-to-Speech
```python
# Generate audio from LLM response
response = client.audio.speech.create(
    model="tts-1",
    voice="alloy",
    input="Your order has been shipped and will arrive tomorrow."
)
response.stream_to_file("response.mp3")
```

---

## AI Coding Agents

Agents that write, review, and debug code.

```java
@Component
public class CodingAgent {
    
    @Tool(description = "Read a file from the project")
    public String readFile(@ToolParam(description = "File path") String path) {
        return fileService.readFile(path);
    }
    
    @Tool(description = "Write content to a file")
    public String writeFile(
        @ToolParam(description = "File path") String path,
        @ToolParam(description = "File content") String content
    ) {
        fileService.writeFile(path, content);
        return "File written: " + path;
    }
    
    @Tool(description = "Run a shell command and return output")
    public String runCommand(@ToolParam(description = "Command to execute") String command) {
        return shellService.execute(command);
    }
    
    @Tool(description = "Search codebase for pattern")
    public String searchCode(@ToolParam(description = "Search pattern") String pattern) {
        return codeSearchService.search(pattern);
    }
    
    // Agent can: read code → understand it → write new code → run tests → fix issues
}
```

---

## Graph RAG

Combine knowledge graphs with vector search for better multi-hop reasoning.

```
Traditional RAG:
  "Who manages the team that built the payment system?"
  → Might retrieve: payment system docs, team docs (separately)
  → May fail to connect: payment system → team → manager

Graph RAG:
  Knowledge Graph: payment_system --built_by--> team_alpha --managed_by--> John
  → Direct traversal finds the answer
```

```python
# Neo4j + Vector Search (conceptual)
from neo4j import GraphDatabase

# Extract entities and relationships from documents
# "John manages Team Alpha which built the payment system"
# → (John)-[:MANAGES]->(Team Alpha)-[:BUILT]->(Payment System)

# Query combines graph traversal + semantic search
query = """
CALL db.index.vector.queryNodes('document_embeddings', 5, $embedding)
YIELD node, score
MATCH (node)-[*1..3]-(related)
RETURN node, related, score
ORDER BY score DESC
"""
```

---

## Multimodal RAG

RAG that handles images, tables, and mixed content.

```python
# Process documents with images and tables
class MultimodalRAG:
    
    def ingest_document(self, pdf_path):
        # Extract text chunks
        text_chunks = extract_text(pdf_path)
        
        # Extract and describe images with vision model
        images = extract_images(pdf_path)
        image_descriptions = []
        for img in images:
            description = vision_model.describe(img)
            image_descriptions.append(description)
        
        # Extract tables and convert to text
        tables = extract_tables(pdf_path)
        table_texts = [table_to_markdown(t) for t in tables]
        
        # Embed everything
        all_content = text_chunks + image_descriptions + table_texts
        embed_and_store(all_content)
```

---

## Agentic RAG

RAG where an agent decides HOW to retrieve, not just WHAT to retrieve.

```java
@Service
public class AgenticRAG {
    
    private final ChatClient chatClient;
    
    @Tool(description = "Search general knowledge base")
    public List<String> searchKnowledgeBase(String query) { ... }
    
    @Tool(description = "Search technical documentation")
    public List<String> searchTechDocs(String query) { ... }
    
    @Tool(description = "Search customer-specific data")
    public List<String> searchCustomerData(String customerId, String query) { ... }
    
    @Tool(description = "Query SQL database for structured data")
    public String queryDatabase(String sql) { ... }
    
    // Agent DECIDES which retrieval method to use and how to combine results
    public String query(String question) {
        return chatClient.prompt()
            .system("""
                You are a research agent. Use the available tools to find 
                the best information to answer the user's question.
                You can use multiple tools and combine results.
                Think about which sources are most relevant.
                """)
            .user(question)
            .tools(this)
            .call()
            .content();
    }
}
```

---

## Multi-Agent Systems

Multiple specialized agents collaborating.

```
┌────────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR                              │
│  Receives task, decomposes, routes to specialists           │
└───────┬──────────────────┬───────────────────┬─────────────┘
        ↓                  ↓                   ↓
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│  Researcher   │  │   Coder       │  │   Reviewer    │
│  - Web search │  │  - Write code │  │  - Review code│
│  - Read docs  │  │  - Run tests  │  │  - Security   │
│  - Summarize  │  │  - Debug      │  │  - Quality    │
└───────────────┘  └───────────────┘  └───────────────┘

Communication patterns:
1. Sequential: Research → Code → Review
2. Parallel: Multiple researchers work simultaneously
3. Debate: Two agents argue, judge decides
4. Hierarchical: Manager delegates, workers execute
```

---

## Model Routing

Dynamically select the best model for each request.

```java
@Service
public class ModelRouter {
    
    private final ChatClient gpt4Client;      // Best quality
    private final ChatClient gpt35Client;     // Fast, cheap
    private final ChatClient localClient;     // Privacy, offline
    
    public String route(String message, RoutingContext context) {
        ModelChoice choice = selectModel(message, context);
        
        return switch (choice) {
            case GPT4 -> gpt4Client.prompt().user(message).call().content();
            case GPT35 -> gpt35Client.prompt().user(message).call().content();
            case LOCAL -> localClient.prompt().user(message).call().content();
        };
    }
    
    private ModelChoice selectModel(String message, RoutingContext context) {
        // Simple routing logic
        if (context.requiresPrivacy()) return ModelChoice.LOCAL;
        if (isSimpleQuery(message)) return ModelChoice.GPT35;
        if (requiresReasoning(message)) return ModelChoice.GPT4;
        return ModelChoice.GPT35; // Default to fast/cheap
    }
    
    // Advanced: Use a classifier model to route
    private ModelChoice classifierRoute(String message) {
        String classification = classifierClient.prompt()
            .user("Classify complexity (simple/medium/complex): " + message)
            .call()
            .content();
        
        return switch (classification.trim().toLowerCase()) {
            case "simple" -> ModelChoice.GPT35;
            case "complex" -> ModelChoice.GPT4;
            default -> ModelChoice.GPT35;
        };
    }
}
```

---

## Small Language Models & Local LLMs

Running models locally for privacy, cost, or offline use.

```yaml
# Ollama for local models
# Install: https://ollama.ai
# Run: ollama run llama3

# Spring AI with Ollama
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3:8b
      embedding:
        model: nomic-embed-text
```

### When to Use Local Models
```
Use local/small models when:
- Data cannot leave your infrastructure (privacy)
- Latency requirements < 100ms (no network call)
- High volume, simple tasks (cost optimization)
- Offline/air-gapped environments
- Development and testing (no API costs)

Small models (1-7B parameters):
- Classification tasks
- Simple extraction
- Code completion
- Embeddings generation
- Summarization of short texts
```

---

## Quantization

Reducing model precision to decrease size and increase speed.

```
Full precision (FP32):  7B model = ~28GB RAM
Half precision (FP16):  7B model = ~14GB RAM
8-bit quantization:     7B model = ~7GB RAM
4-bit quantization:     7B model = ~4GB RAM

Trade-off: Lower precision = faster + less RAM, but slightly lower quality
4-bit is usually acceptable for most tasks (minimal quality loss)
```

---

## GPU Fundamentals

```
Why GPUs for AI?
- Neural networks = massive matrix multiplications
- GPUs have thousands of cores optimized for parallel math
- Training: absolutely requires GPUs (weeks → hours)
- Inference: GPUs help but CPUs work for smaller models

Key specs:
- VRAM (GPU memory): determines max model size
  - 24GB (RTX 4090): runs 7B-13B models
  - 40GB (A100): runs 30B-70B models
  - 80GB (A100/H100): runs 70B+ models

For application developers:
- You rarely need to manage GPUs directly
- Cloud providers (AWS, Azure) handle GPU provisioning
- Bedrock/OpenAI abstract GPU management entirely
- Local development: Ollama manages GPU automatically
```

---

## Projects to Build 🔥

### Project 1: AI Chat Application
```
Stack: Spring Boot + Spring AI + Angular + LLM
Features:
- Chat interface with streaming
- Conversation memory
- System prompt customization
- Model selection
```

### Project 2: PDF RAG Application
```
Stack: Spring Boot + Spring AI + pgvector + PDF processing
Features:
- Upload PDFs
- Automatic chunking and embedding
- Question answering over documents
- Source citation
```

### Project 3: Enterprise Knowledge Assistant
```
Stack: Full production stack
Features:
- Authentication (Spring Security + JWT)
- RBAC for document access
- PostgreSQL + pgvector
- Redis caching
- RAG with multiple document types
- Kafka for async ingestion
- Monitoring (Prometheus + Grafana)
```

### Project 4: AI Customer Support Agent
```
Stack: Spring Boot + Spring AI + Agents + Tools
Features:
- Agent with tool calling
- Order API integration
- Customer lookup
- Ticket creation
- Email sending
- Escalation logic
```

### Project 5: Production-grade Agentic RAG ⭐⭐⭐⭐⭐
```
Stack: Everything combined
- Spring Boot + Spring AI
- AWS Bedrock
- PostgreSQL + pgvector
- Kafka (event-driven processing)
- Redis (caching, rate limiting)
- Kubernetes (deployment)
- MCP (tool integration)
- Tool calling (external APIs)
- RAG (knowledge retrieval)
- Agent (autonomous reasoning)
- Security (auth, injection prevention)
- Observability (metrics, tracing, logging)
- Evaluation (automated quality checks)
```

---

## Priority Roadmap

```
🔴 MUST MASTER (do these first):
├── LLM Fundamentals (Topic 6)
├── Prompt Engineering (Topic 7)
├── Embeddings (Topic 9)
├── Vector Databases (Topic 10)
├── RAG (Topic 11)
├── Spring AI (Topic 13)
├── Tool Calling (Topic 15)
├── AI Agents (Topic 14)
├── MCP (Topic 16)
├── AI Security (Topic 20)
├── AI Evaluation (Topic 19)
└── Production AI Engineering (Topic 21)

🟡 GOOD TO KNOW (study after mastering above):
├── ML Algorithms (Topic 3)
├── Deep Learning (Topic 4)
├── NLP (Topic 5)
├── Fine-Tuning (Topic 18)
└── LangChain/LlamaIndex (Topic 12)

🟢 ADVANCED / LATER:
├── Training LLMs from scratch
├── GPU optimization
├── Distributed model training
├── Advanced mathematics
└── Building foundation models
```

---

## Key Takeaways

1. **Master the fundamentals first** — RAG, agents, tool calling, Spring AI
2. **Advanced topics build on fundamentals** — don't skip ahead
3. **Build projects progressively** — each builds on the last
4. **Your backend skills are your advantage** — production AI needs engineering
5. **Stay current** — AI moves fast, but patterns are stable
6. **Focus on Java/Spring AI** — it's your professional differentiator
