# Spring AI ⭐⭐⭐⭐⭐

## Overview
Spring AI is the official Spring framework for building AI-powered applications in Java. It brings the familiar Spring programming model to AI — dependency injection, auto-configuration, and clean abstractions. This is your primary tool for production AI work.

---

## Spring AI Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Angular / React Frontend                   │
└──────────────────────────────┬──────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                    │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │  Controllers│  │   Services   │  │   Configuration    │ │
│  └──────┬──────┘  └──────┬───────┘  └────────────────────┘ │
│         ↓                ↓                                   │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    Spring AI Layer                        ││
│  │  ┌──────────┐ ┌────────────┐ ┌───────────┐ ┌────────┐ ││
│  │  │ChatClient│ │EmbeddingModel│ │VectorStore│ │Advisors│ ││
│  │  └────┬─────┘ └─────┬──────┘ └─────┬─────┘ └───┬────┘ ││
│  └───────┼──────────────┼──────────────┼───────────┼────────┘│
└──────────┼──────────────┼──────────────┼───────────┼─────────┘
           ↓              ↓              ↓           ↓
┌──────────────┐  ┌─────────────┐  ┌──────────┐
│  LLM Provider│  │Embedding API│  │ Vector DB│
│  (OpenAI,    │  │             │  │(pgvector)│
│   Bedrock,   │  │             │  │          │
│   Ollama)    │  │             │  │          │
└──────────────┘  └─────────────┘  └──────────┘
```

---

## Getting Started

### Dependencies (pom.xml)
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- OpenAI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- OR AWS Bedrock -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- pgvector for RAG -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### Configuration (application.yml)
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-3-small
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
```

---

## ChatClient

The primary interface for interacting with LLMs.

```java
@Service
public class ChatService {
    
    private final ChatClient chatClient;
    
    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a helpful Java expert assistant.")
            .build();
    }
    
    // Simple chat
    public String chat(String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
    
    // With system prompt override
    public String chatWithRole(String role, String message) {
        return chatClient.prompt()
            .system(s -> s.text("You are a {role} expert.").param("role", role))
            .user(message)
            .call()
            .content();
    }
    
    // With options override
    public String creativeChat(String message) {
        return chatClient.prompt()
            .user(message)
            .options(ChatOptions.builder()
                .withTemperature(0.9)
                .withModel("gpt-4")
                .build())
            .call()
            .content();
    }
}
```

---

## ChatModel (Lower Level)

```java
@Service
public class LowLevelChatService {
    
    private final ChatModel chatModel;
    
    public String chat(String message) {
        Prompt prompt = new Prompt(List.of(
            new SystemMessage("You are a helpful assistant."),
            new UserMessage(message)
        ));
        
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getContent();
    }
}
```

---

## PromptTemplate

```java
@Service
public class PromptService {
    
    private final ChatClient chatClient;
    
    public String generateCode(String language, String task) {
        String template = """
            Generate {language} code for the following task:
            {task}
            
            Requirements:
            - Include error handling
            - Add comments
            - Follow best practices
            """;
        
        PromptTemplate promptTemplate = new PromptTemplate(template);
        Prompt prompt = promptTemplate.create(Map.of(
            "language", language,
            "task", task
        ));
        
        return chatClient.prompt(prompt).call().content();
    }
}
```

---

## Advisors

Advisors intercept and modify prompts/responses — like filters for AI calls.

```java
@Service
public class AdvisedChatService {
    
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    
    public AdvisedChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
            .defaultSystem("You are a knowledge assistant.")
            .defaultAdvisors(
                // RAG advisor - automatically retrieves relevant docs
                new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()),
                // Logging advisor
                new SimpleLoggerAdvisor()
            )
            .build();
        this.vectorStore = vectorStore;
    }
    
    public String queryWithRAG(String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
        // QuestionAnswerAdvisor automatically:
        // 1. Embeds the question
        // 2. Searches vector store
        // 3. Injects relevant docs into prompt
    }
}

// Custom Advisor
public class SafetyAdvisor implements CallAroundAdvisor {
    
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        // Pre-processing: check input
        String userMessage = request.userText();
        if (containsHarmfulContent(userMessage)) {
            // Return safe response without calling LLM
            return new AdvisedResponse(/* safe response */);
        }
        
        // Call the chain
        AdvisedResponse response = chain.nextAroundCall(request);
        
        // Post-processing: validate output
        String output = response.response().getResult().getOutput().getContent();
        if (containsSensitiveData(output)) {
            return redactResponse(response);
        }
        
        return response;
    }
}
```

---

## Structured Output

Map LLM responses directly to Java objects.

```java
// Define your record/class
public record MovieRecommendation(
    String title,
    int year,
    String genre,
    String reason
) {}

public record MovieList(List<MovieRecommendation> movies) {}

@Service
public class MovieService {
    
    private final ChatClient chatClient;
    
    public MovieList getRecommendations(String preferences) {
        return chatClient.prompt()
            .user("Recommend 3 movies for someone who likes: " + preferences)
            .call()
            .entity(MovieList.class);
        // Automatically parses JSON response into MovieList object
    }
    
    // With BeanOutputConverter for more control
    public List<MovieRecommendation> getMovies(String genre) {
        BeanOutputConverter<MovieList> converter = new BeanOutputConverter<>(MovieList.class);
        
        String format = converter.getFormat();  // JSON schema instructions
        
        String response = chatClient.prompt()
            .user(u -> u.text("List 5 {genre} movies. {format}")
                .param("genre", genre)
                .param("format", format))
            .call()
            .content();
        
        return converter.convert(response).movies();
    }
}
```

---

## Embeddings

```java
@Service
public class EmbeddingService {
    
    private final EmbeddingModel embeddingModel;
    
    public float[] embedText(String text) {
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(List.of(text), EmbeddingOptionsBuilder.builder().build())
        );
        return response.getResult().getOutput();
    }
    
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(texts, EmbeddingOptionsBuilder.builder().build())
        );
        return response.getResults().stream()
            .map(r -> r.getOutput())
            .toList();
    }
}
```

---

## Vector Stores & RAG

```java
@Service
public class RAGService {
    
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    
    // Ingest documents
    public void ingestDocuments(List<Resource> resources) {
        for (Resource resource : resources) {
            // Load PDF
            PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
            List<Document> documents = reader.get();
            
            // Chunk
            TokenTextSplitter splitter = new TokenTextSplitter(
                500,   // chunk size
                100,   // overlap
                5,     // min chunk size
                10000, // max chunk size
                true   // keep separator
            );
            List<Document> chunks = splitter.apply(documents);
            
            // Add metadata
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("source", resource.getFilename());
                chunk.getMetadata().put("ingested_at", Instant.now().toString());
            });
            
            // Store (embedding happens automatically)
            vectorStore.add(chunks);
        }
    }
    
    // Search with filters
    public List<Document> search(String query, String source) {
        return vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(5)
                .withSimilarityThreshold(0.7)
                .withFilterExpression("source == '" + source + "'")
        );
    }
    
    // Full RAG query
    public String query(String question) {
        return chatClient.prompt()
            .user(question)
            .advisors(new QuestionAnswerAdvisor(
                vectorStore,
                SearchRequest.query(question).withTopK(5).withSimilarityThreshold(0.7)
            ))
            .call()
            .content();
    }
}
```

---

## Tool Calling / Function Calling

```java
// Define tools as Spring beans
@Component
public class OrderTools {
    
    @Tool(description = "Get the status of an order by order ID")
    public String getOrderStatus(@ToolParam(description = "The order ID") String orderId) {
        // Call your actual service
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return "Order %s: %s, shipped on %s".formatted(
            orderId, order.getStatus(), order.getShippedDate()
        );
    }
    
    @Tool(description = "Cancel an order if it hasn't shipped yet")
    public String cancelOrder(@ToolParam(description = "The order ID") String orderId) {
        // Business logic
        orderService.cancel(orderId);
        return "Order " + orderId + " has been cancelled.";
    }
}

// Use tools in ChatClient
@Service
public class AgentService {
    
    private final ChatClient chatClient;
    private final OrderTools orderTools;
    
    public String handleCustomerQuery(String query) {
        return chatClient.prompt()
            .user(query)
            .tools(orderTools)
            .call()
            .content();
    }
    // When user asks "What's the status of order 12345?"
    // LLM automatically calls getOrderStatus("12345")
    // Then formulates a natural language response
}
```

---

## Memory

```java
@Service
public class ConversationService {
    
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    
    public ConversationService(ChatClient.Builder builder) {
        this.chatMemory = new InMemoryChatMemory();
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant.")
            .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
            .build();
    }
    
    public String chat(String sessionId, String message) {
        return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .call()
            .content();
    }
}

// Persistent memory with database
@Bean
public ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
    return new JdbcChatMemory(jdbcTemplate);
}
```

---

## Streaming

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatClient chatClient;
    
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
    }
    
    // With structured streaming events
    @GetMapping(value = "/stream/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamEvents(@RequestParam String message) {
        return chatClient.prompt()
            .user(message)
            .stream()
            .content()
            .map(token -> ServerSentEvent.builder(token).build());
    }
}
```

---

## Observability

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: ai-service

spring:
  ai:
    chat:
      observations:
        include-input: true
        include-output: true
```

```java
// Custom metrics
@Service
public class ObservableChatService {
    
    private final ChatClient chatClient;
    private final MeterRegistry meterRegistry;
    
    public String chat(String message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String response = chatClient.prompt()
                .user(message)
                .call()
                .content();
            
            meterRegistry.counter("ai.chat.success").increment();
            return response;
        } catch (Exception e) {
            meterRegistry.counter("ai.chat.error", "type", e.getClass().getSimpleName()).increment();
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("ai.chat.duration"));
        }
    }
}
```

---

## Complete Production Architecture

```java
@Configuration
public class AIConfiguration {
    
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, 
                                  VectorStore vectorStore,
                                  ChatMemory chatMemory) {
        return builder
            .defaultSystem("""
                You are a customer support assistant for TechCorp.
                - Only answer questions about our products and services
                - Be polite and concise
                - If you don't know, say so
                - Never make up information
                """)
            .defaultAdvisors(
                new QuestionAnswerAdvisor(vectorStore),
                new MessageChatMemoryAdvisor(chatMemory),
                new SimpleLoggerAdvisor()
            )
            .build();
    }
    
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel, DataSource dataSource) {
        return new PgVectorStore(dataSource, embeddingModel, PgVectorStore.PgVectorStoreConfig.builder()
            .withIndexType(PgVectorStore.PgIndexType.HNSW)
            .withDistanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .withDimensions(1536)
            .build());
    }
}

@RestController
@RequestMapping("/api/ai")
public class AIController {
    
    private final ChatClient chatClient;
    private final RAGService ragService;
    
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String response = chatClient.prompt()
            .user(request.message())
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.sessionId()))
            .call()
            .content();
        return ResponseEntity.ok(new ChatResponse(response));
    }
    
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message, 
                                    @RequestParam String sessionId) {
        return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .content();
    }
    
    @PostMapping("/documents/ingest")
    public ResponseEntity<Void> ingest(@RequestParam MultipartFile file) {
        ragService.ingest(file.getResource());
        return ResponseEntity.accepted().build();
    }
}
```

---

## Interview Questions

**Q: What is Spring AI and how does it fit into the Spring ecosystem?**
Spring AI provides a consistent abstraction layer for AI services (chat models, embeddings, vector stores) following Spring conventions. It supports multiple providers (OpenAI, AWS Bedrock, Ollama) through auto-configuration, uses familiar patterns (dependency injection, properties-based config), and integrates with Spring Security, Spring Data, and Spring Boot Actuator.

**Q: How does the Advisor pattern work in Spring AI?**
Advisors are like servlet filters for AI calls. They intercept requests before they reach the LLM and responses before they return to the caller. QuestionAnswerAdvisor adds RAG capability, MessageChatMemoryAdvisor adds conversation history, and custom advisors can add safety checks, logging, or response transformation.

**Q: How would you implement RAG with Spring AI?**
1. Load documents (PDFReader, TextReader). 2. Chunk with TokenTextSplitter. 3. Store in VectorStore (auto-embeds). 4. Use QuestionAnswerAdvisor with ChatClient — it automatically embeds queries, searches the vector store, and injects relevant context into prompts.

**Q: How does tool calling work in Spring AI?**
Define methods annotated with @Tool and @ToolParam on a Spring bean. Pass the bean to ChatClient via `.tools()`. The LLM receives tool descriptions, decides when to call them, Spring AI executes the method, and returns results to the LLM for the final response.

**Q: How do you handle multiple LLM providers in Spring AI?**
Define multiple ChatModel beans with qualifiers. Use different configurations per profile (dev uses Ollama locally, prod uses Bedrock). Implement model routing logic that selects the appropriate model based on task complexity, cost requirements, or availability.

---

## Key Takeaways

1. **Spring AI = Spring patterns for AI** — familiar DI, auto-config, starters
2. **ChatClient** is your primary interface — fluent API for all interactions
3. **Advisors** enable RAG, memory, and custom processing as composable filters
4. **Structured Output** maps LLM responses directly to Java records/classes
5. **Tool Calling** lets LLMs interact with your Spring services
6. **VectorStore + QuestionAnswerAdvisor** = RAG with minimal code
7. **Production-ready** with observability, streaming, and security integration
