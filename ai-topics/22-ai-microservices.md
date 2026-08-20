# AI + Microservices

## Overview
Learn how AI fits into your existing microservices architecture. AI capabilities should be integrated as services that follow the same patterns you already use — separation of concerns, API contracts, resilience, and observability.

---

## Architecture Pattern

```
┌─────────────────┐
│    Frontend     │
│ (Angular/React) │
└────────┬────────┘
         ↓
┌─────────────────┐
│   API Gateway   │
│  (Rate limiting,│
│   Auth, Routing)│
└────────┬────────┘
         ↓
┌────────┴──────────────────────────────────────┐
│                                                │
↓                    ↓                    ↓      │
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│Order Service │  │ User Service │  │  AI Service  │
│              │  │              │  │              │
│ - CRUD       │  │ - Auth       │  │ - Chat       │
│ - Validation │  │ - Profile    │  │ - RAG        │
│ - Payment    │  │ - Preferences│  │ - Agents     │
│              │  │              │  │ - Embeddings │
└──────┬───────┘  └──────────────┘  └──────┬───────┘
       │                                     │
       ↓                                     ↓
┌──────────────┐                    ┌──────────────────┐
│  PostgreSQL  │                    │  Spring AI        │
│  (Orders)    │                    │       ↓           │
└──────────────┘                    │  ┌──────────┐    │
                                    │  │   LLM    │    │
                                    │  │(Bedrock/ │    │
                                    │  │ OpenAI)  │    │
                                    │  └──────────┘    │
                                    │       ↓           │
                                    │  ┌──────────┐    │
                                    │  │ pgvector │    │
                                    │  │(Vector DB)│    │
                                    │  └──────────┘    │
                                    └──────────────────┘
```

---

## AI Service Design

```java
// AI Service — dedicated microservice for AI capabilities
@SpringBootApplication
public class AIServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AIServiceApplication.class, args);
    }
}

// REST API for other services to consume
@RestController
@RequestMapping("/api/v1/ai")
public class AIController {
    
    private final ChatService chatService;
    private final RAGService ragService;
    private final EmbeddingService embeddingService;
    
    // Chat endpoint (used by frontend)
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(request));
    }
    
    // RAG query (used by other services)
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        return ResponseEntity.ok(ragService.query(request));
    }
    
    // Embedding generation (used by other services for search)
    @PostMapping("/embed")
    public ResponseEntity<EmbeddingResponse> embed(@RequestBody EmbeddingRequest request) {
        return ResponseEntity.ok(embeddingService.embed(request));
    }
    
    // Document ingestion
    @PostMapping("/documents")
    public ResponseEntity<Void> ingestDocument(@RequestParam MultipartFile file,
                                                @RequestParam String category) {
        ragService.ingest(file.getResource(), category);
        return ResponseEntity.accepted().build();
    }
}
```

---

## Service-to-Service Communication

```java
// Other services calling AI Service via Feign Client
@FeignClient(name = "ai-service", fallback = AIServiceFallback.class)
public interface AIServiceClient {
    
    @PostMapping("/api/v1/ai/query")
    QueryResponse query(@RequestBody QueryRequest request);
    
    @PostMapping("/api/v1/ai/embed")
    EmbeddingResponse embed(@RequestBody EmbeddingRequest request);
}

// Order Service using AI for intelligent processing
@Service
public class IntelligentOrderService {
    
    private final AIServiceClient aiClient;
    private final OrderRepository orderRepository;
    
    // AI-powered order categorization
    public Order processOrder(OrderRequest request) {
        // Use AI to categorize and route the order
        QueryResponse aiResponse = aiClient.query(new QueryRequest(
            "Categorize this order and suggest priority: " + request.description()
        ));
        
        Order order = new Order();
        order.setCategory(aiResponse.getExtractedCategory());
        order.setPriority(aiResponse.getSuggestedPriority());
        order.setDescription(request.description());
        
        return orderRepository.save(order);
    }
}

// Fallback when AI service is unavailable
@Component
public class AIServiceFallback implements AIServiceClient {
    
    @Override
    public QueryResponse query(QueryRequest request) {
        return QueryResponse.fallback("AI service temporarily unavailable");
    }
    
    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        return EmbeddingResponse.empty();
    }
}
```

---

## Event-Driven AI Integration

```java
// AI Service listens to domain events
@Service
public class AIEventListener {
    
    private final RAGService ragService;
    private final ChatClient chatClient;
    
    // When a new document is uploaded anywhere in the system
    @KafkaListener(topics = "document-events")
    public void handleDocumentEvent(DocumentEvent event) {
        if (event.getType() == EventType.CREATED) {
            ragService.ingestFromUrl(event.getDocumentUrl(), event.getMetadata());
        } else if (event.getType() == EventType.DELETED) {
            ragService.removeDocument(event.getDocumentId());
        }
    }
    
    // When customer support ticket is created
    @KafkaListener(topics = "support-tickets")
    public void handleSupportTicket(SupportTicketEvent event) {
        // AI suggests response
        String suggestedResponse = chatClient.prompt()
            .user("Suggest a response for this support ticket: " + event.getDescription())
            .advisors(new QuestionAnswerAdvisor(vectorStore))
            .call()
            .content();
        
        // Publish suggestion back
        kafkaTemplate.send("ai-suggestions", new AISuggestion(
            event.getTicketId(), suggestedResponse
        ));
    }
    
    // Async embedding generation for search
    @KafkaListener(topics = "product-updates")
    public void handleProductUpdate(ProductEvent event) {
        // Re-embed product description for semantic search
        embeddingService.updateProductEmbedding(event.getProductId(), event.getDescription());
    }
}
```

---

## Shared AI Patterns Across Services

```java
// Shared library for AI integration
// ai-common module used by all services

public interface AICapability {
    
    // Any service can request text generation
    String generate(String prompt);
    
    // Any service can request embeddings
    float[] embed(String text);
    
    // Any service can do similarity search
    List<SearchResult> search(String query, String namespace);
}

// Implementation uses the AI Service via REST/gRPC
@Component
public class AICapabilityClient implements AICapability {
    
    private final WebClient aiServiceClient;
    private final CircuitBreaker circuitBreaker;
    
    @Override
    public String generate(String prompt) {
        return circuitBreaker.run(() ->
            aiServiceClient.post()
                .uri("/api/v1/ai/generate")
                .bodyValue(new GenerateRequest(prompt))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30))
        , throwable -> "Service temporarily unavailable");
    }
}
```

---

## Deployment Considerations

```yaml
# AI Service Kubernetes deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-service
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: ai-service
          image: ai-service:latest
          resources:
            requests:
              memory: "1Gi"    # AI services need more memory
              cpu: "500m"
            limits:
              memory: "2Gi"
              cpu: "2000m"
          env:
            - name: OPENAI_API_KEY
              valueFrom:
                secretKeyRef:
                  name: ai-secrets
                  key: openai-api-key
            - name: SPRING_AI_VECTORSTORE_PGVECTOR_DIMENSIONS
              value: "1536"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60   # AI models take longer to load
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
---
# Horizontal Pod Autoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ai-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ai-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Pods
      pods:
        metric:
          name: ai_request_queue_size
        target:
          type: AverageValue
          averageValue: "10"
```

---

## Interview Questions

**Q: How would you integrate AI into an existing microservices architecture?**
Create a dedicated AI service that encapsulates all AI capabilities (chat, RAG, embeddings, agents). Other services communicate via REST/gRPC or events (Kafka). Apply the same patterns: API gateway, circuit breakers, fallbacks, service discovery. Keep AI concerns isolated — other services shouldn't need AI libraries.

**Q: Should AI be a separate microservice or embedded in each service?**
Separate service for: shared AI capabilities, centralized cost/rate management, independent scaling, and team ownership. Embedded when: tight latency requirements, simple use case (just embeddings), or offline processing. Most architectures benefit from a dedicated AI service that other services call.

**Q: How do you handle the latency of AI calls in a microservice architecture?**
Async processing (Kafka/queues for non-real-time), streaming for user-facing interactions, caching (Redis for repeated queries), timeout + fallback, pre-computation (batch embed documents ahead of time), and appropriate SLAs per endpoint (chat = 3s, document processing = async).

---

## Key Takeaways

1. **AI as a service** — dedicated microservice, clean API, standard patterns
2. **Event-driven integration** — Kafka for async AI processing
3. **Same resilience patterns** — circuit breaker, retry, fallback, timeout
4. **Independent scaling** — AI service scales differently from CRUD services
5. **Shared client library** — other services use a thin client to call AI
6. **Your microservices expertise directly applies** to AI architecture
