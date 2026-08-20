# Spring AI — Hands-On Code Examples

## Theory

### What is Spring AI?
A Spring framework project that provides a consistent, portable API for integrating AI models (OpenAI, Azure OpenAI, Ollama, etc.) into Spring Boot applications. Same philosophy as Spring Data — unified abstraction over different providers.

---

## Complete Working Examples

### 1. Basic Chat Completion — Spring Boot REST API

```java
// pom.xml dependency
// <dependency>
//   <groupId>org.springframework.ai</groupId>
//   <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
// </dependency>

// application.yml
// spring:
//   ai:
//     openai:
//       api-key: ${OPENAI_API_KEY}
//       chat:
//         options:
//           model: gpt-4o
//           temperature: 0.7

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String userMessage) {
        return chatClient.prompt()
            .user(userMessage)
            .call()
            .content();
    }

    @PostMapping("/chat/structured")
    public MovieRecommendation getRecommendation(@RequestParam String genre) {
        return chatClient.prompt()
            .user("Recommend a " + genre + " movie with title, year, and reason")
            .call()
            .entity(MovieRecommendation.class); // Automatic JSON parsing!
    }
}

record MovieRecommendation(String title, int year, String reason) {}
```

### 2. System Prompt + Conversation Memory

```java
@Service
public class CustomerSupportService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public CustomerSupportService(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
            .defaultSystem("""
                You are a helpful customer support agent for an e-commerce platform.
                Be concise, friendly, and always offer to escalate to a human if you can't help.
                You can help with: order status, returns, product questions.
                You cannot help with: payment disputes, account deletion.
                """)
            .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
            .build();
        this.chatMemory = chatMemory;
    }

    public String chat(String sessionId, String userMessage) {
        return chatClient.prompt()
            .user(userMessage)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .call()
            .content();
    }
}
```

### 3. RAG (Retrieval-Augmented Generation) — Full Implementation

```java
// Step 1: Ingest documents into Vector Store
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.textSplitter = new TokenTextSplitter(800, 200, 5, 10000, true);
    }

    public void ingestPdf(Resource pdfResource) {
        // Read PDF
        PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource);
        List<Document> documents = reader.get();

        // Split into chunks
        List<Document> chunks = textSplitter.apply(documents);

        // Store in vector database (embedding happens automatically)
        vectorStore.add(chunks);
    }
}

// Step 2: Query with RAG
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final ChatClient chatClient;

    public KnowledgeController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
            .defaultSystem("Answer questions based on the provided context. " +
                          "If the context doesn't contain the answer, say 'I don't know'.")
            .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore, 
                SearchRequest.defaults().withTopK(5)))
            .build();
    }

    @PostMapping("/ask")
    public String askQuestion(@RequestBody String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }
}

// application.yml for PgVector
// spring:
//   ai:
//     vectorstore:
//       pgvector:
//         index-type: HNSW
//         distance-type: COSINE_DISTANCE
//         dimensions: 1536
```

### 4. Function Calling (Tool Use)

```java
// Define functions the LLM can call
@Configuration
public class AiFunctions {

    @Bean
    @Description("Get the current weather for a given city")
    public Function<WeatherRequest, WeatherResponse> currentWeather() {
        return request -> {
            // Call real weather API here
            return new WeatherResponse(request.city(), 22.5, "Partly cloudy");
        };
    }

    @Bean
    @Description("Get order status by order ID")
    public Function<OrderStatusRequest, OrderStatusResponse> orderStatus(
            OrderRepository orderRepository) {
        return request -> {
            Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new OrderNotFoundException(request.orderId()));
            return new OrderStatusResponse(order.getId(), order.getStatus().name(), 
                                          order.getEstimatedDelivery());
        };
    }
}

record WeatherRequest(String city) {}
record WeatherResponse(String city, double temperature, String condition) {}
record OrderStatusRequest(String orderId) {}
record OrderStatusResponse(String orderId, String status, LocalDate estimatedDelivery) {}

// Use in ChatClient
@RestController
public class AssistantController {

    private final ChatClient chatClient;

    public AssistantController(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant. Use available tools when needed.")
            .defaultFunctions("currentWeather", "orderStatus") // Register tools
            .build();
    }

    @PostMapping("/api/assistant")
    public String assist(@RequestBody String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
        // LLM will automatically call functions when needed!
        // User: "What's the status of order ORD-123?"
        // LLM: calls orderStatus("ORD-123") → returns status to user
    }
}
```

### 5. Streaming Responses (SSE)

```java
@RestController
public class StreamingController {

    private final ChatClient chatClient;

    public StreamingController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
    }
}
```

### 6. Image Generation

```java
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageClient imageClient;

    public ImageController(ImageClient imageClient) {
        this.imageClient = imageClient;
    }

    @PostMapping("/generate")
    public String generateImage(@RequestBody String prompt) {
        ImageResponse response = imageClient.call(
            new ImagePrompt(prompt, 
                ImageOptionsBuilder.builder()
                    .withModel("dall-e-3")
                    .withHeight(1024)
                    .withWidth(1024)
                    .build())
        );
        return response.getResult().getOutput().getUrl();
    }
}
```

### 7. Embedding + Similarity Search

```java
@Service
public class ProductSearchService {

    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;

    public ProductSearchService(VectorStore vectorStore, EmbeddingClient embeddingClient) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
    }

    // Index products
    public void indexProduct(Product product) {
        Document doc = new Document(
            product.getDescription(),
            Map.of(
                "productId", product.getId(),
                "name", product.getName(),
                "price", String.valueOf(product.getPrice()),
                "category", product.getCategory()
            )
        );
        vectorStore.add(List.of(doc));
    }

    // Semantic search
    public List<Product> semanticSearch(String query, int topK) {
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(topK)
                .withSimilarityThreshold(0.7)
        );

        return results.stream()
            .map(doc -> new Product(
                doc.getMetadata().get("productId").toString(),
                doc.getMetadata().get("name").toString(),
                doc.getContent()
            ))
            .toList();
    }
}
```

### 8. Multi-Provider Configuration (Azure OpenAI)

```yaml
# application-azure.yml
spring:
  ai:
    azure:
      openai:
        api-key: ${AZURE_OPENAI_API_KEY}
        endpoint: ${AZURE_OPENAI_ENDPOINT}
        chat:
          options:
            deployment-name: gpt-4o
            temperature: 0.7
        embedding:
          options:
            deployment-name: text-embedding-3-small
```

```java
// Same ChatClient code works regardless of provider!
// Switch between OpenAI, Azure, Ollama by changing starter dependency + config
// Code doesn't change — that's the Spring AI abstraction value.
```

---

## Production Architecture with Spring AI

```
Client
    │
    ▼
Spring Boot API (ChatController)
    │
    ├── ChatClient (abstracts LLM provider)
    │   ├── System prompt (role, constraints)
    │   ├── Chat memory (Redis/PostgreSQL-backed)
    │   ├── RAG advisor (retrieves context from vector store)
    │   └── Function calling (order status, weather, etc.)
    │
    ├── Vector Store (PgVector / Redis / Pinecone)
    │   └── Document embeddings for RAG
    │
    ├── Document Ingestion Pipeline
    │   ├── PDF/HTML/Markdown readers
    │   ├── Text splitters (chunking)
    │   └── Embedding generation → Vector Store
    │
    └── External LLM Provider
        ├── OpenAI (gpt-4o)
        ├── Azure OpenAI
        ├── Ollama (local, llama3)
        └── Anthropic (Claude)
```

---

## Interview Questions

### Q: How does Spring AI differ from directly calling the OpenAI API?
**A:** Spring AI provides:
1. **Abstraction**: Same code works with OpenAI, Azure, Ollama — swap provider via config
2. **Spring integration**: Dependency injection, auto-configuration, property management
3. **Built-in features**: Chat memory, RAG advisors, function calling, output parsers
4. **Type safety**: Structured output with `entity()` — automatic JSON → Java object
5. **Testability**: Mock the ChatClient in unit tests like any Spring bean

### Q: Explain RAG in Spring AI.
**A:** RAG (Retrieval-Augmented Generation):
1. **Ingest**: Read documents → split into chunks → generate embeddings → store in vector DB
2. **Query**: User question → embed the question → similarity search in vector DB → retrieve top-K relevant chunks
3. **Generate**: Send user question + retrieved context to LLM → LLM answers using the context

Spring AI's `QuestionAnswerAdvisor` handles steps 2-3 automatically. You just configure the VectorStore and attach the advisor to your ChatClient.

### Q: How do you handle function calling in Spring AI?
**A:** Define Java functions as Spring beans with `@Description`. Register them with the ChatClient via `.defaultFunctions()`. When the LLM determines it needs external data (e.g., user asks "what's my order status?"), it generates a function call. Spring AI automatically invokes your Java function, passes the result back to the LLM, and the LLM formulates the final response. The LLM never directly accesses your database — it goes through your controlled function.
