# Kafka + AI

## Overview
Since you already know Kafka, leverage that knowledge for AI event-driven architectures. Kafka excels at handling the async, high-throughput, and pipeline-oriented nature of AI workloads.

---

## Event-Driven AI Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     Kafka-Powered AI Pipeline                      │
│                                                                    │
│  ┌─────────┐    ┌──────────────┐    ┌──────────────┐            │
│  │Document │    │  ai-ingest   │    │ ai-embedding │            │
│  │Upload   │───→│  (topic)     │───→│  (topic)     │            │
│  │Service  │    └──────────────┘    └──────┬───────┘            │
│  └─────────┘                               │                     │
│                                            ↓                     │
│                                    ┌──────────────┐              │
│                                    │ Vector Store │              │
│                                    │  (pgvector)  │              │
│                                    └──────────────┘              │
│                                                                    │
│  ┌─────────┐    ┌──────────────┐    ┌──────────────┐            │
│  │User     │    │ ai-requests  │    │ ai-responses │            │
│  │Request  │───→│  (topic)     │───→│  (topic)     │───→ User   │
│  └─────────┘    └──────────────┘    └──────────────┘            │
└──────────────────────────────────────────────────────────────────┘
```

---

## Async LLM Processing

```java
// Producer: Send AI requests to Kafka
@Service
public class AIRequestProducer {
    
    private final KafkaTemplate<String, AIRequest> kafkaTemplate;
    
    public String submitRequest(String userId, String question) {
        String correlationId = UUID.randomUUID().toString();
        
        AIRequest request = AIRequest.builder()
            .correlationId(correlationId)
            .userId(userId)
            .question(question)
            .priority(Priority.NORMAL)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("ai-requests", correlationId, request);
        return correlationId; // Client polls for response
    }
}

// Consumer: Process AI requests
@Service
public class AIRequestConsumer {
    
    private final ChatClient chatClient;
    private final KafkaTemplate<String, AIResponse> responseTemplate;
    
    @KafkaListener(topics = "ai-requests", groupId = "ai-processor",
                   concurrency = "5")
    public void processRequest(AIRequest request) {
        try {
            String response = chatClient.prompt()
                .user(request.getQuestion())
                .call()
                .content();
            
            responseTemplate.send("ai-responses", request.getCorrelationId(),
                AIResponse.success(request.getCorrelationId(), response));
                
        } catch (Exception e) {
            log.error("AI processing failed for {}", request.getCorrelationId(), e);
            responseTemplate.send("ai-responses", request.getCorrelationId(),
                AIResponse.error(request.getCorrelationId(), e.getMessage()));
        }
    }
}
```

---

## Document Processing Pipeline

```java
// Stage 1: Document ingestion
@KafkaListener(topics = "document-uploaded")
public void handleUpload(DocumentEvent event) {
    // Extract text from PDF/Word/HTML
    String text = documentExtractor.extract(event.getFileUrl());
    
    // Send to chunking stage
    kafkaTemplate.send("document-chunking", new ChunkingRequest(
        event.getDocumentId(), text, event.getMetadata()
    ));
}

// Stage 2: Chunking
@KafkaListener(topics = "document-chunking")
public void handleChunking(ChunkingRequest request) {
    List<String> chunks = textSplitter.split(request.getText(), 1000, 200);
    
    // Send each chunk for embedding
    for (int i = 0; i < chunks.size(); i++) {
        kafkaTemplate.send("document-embedding", new EmbeddingRequest(
            request.getDocumentId(),
            i,
            chunks.get(i),
            request.getMetadata()
        ));
    }
}

// Stage 3: Embedding generation
@KafkaListener(topics = "document-embedding", concurrency = "10")
public void handleEmbedding(EmbeddingRequest request) {
    float[] embedding = embeddingModel.embed(request.getChunkText());
    
    // Store in vector DB
    Document doc = new Document(request.getChunkText(), request.getMetadata());
    doc.setEmbedding(embedding);
    vectorStore.add(List.of(doc));
    
    log.info("Embedded chunk {}/{} for document {}", 
        request.getChunkIndex(), request.getDocumentId());
}
```

### Pipeline with Error Handling
```
document-uploaded → document-chunking → document-embedding → vector-store
        ↓                    ↓                    ↓
  upload-dlq          chunking-dlq         embedding-dlq
  
DLQ Strategy:
- Retry 3x with exponential backoff
- On final failure → DLQ
- Alert on DLQ messages
- Manual review/reprocess
```

---

## Embedding Generation at Scale

```java
@Service
public class BatchEmbeddingService {
    
    // Process embeddings in batches for efficiency
    @KafkaListener(topics = "embedding-batch", 
                   containerFactory = "batchKafkaListenerContainerFactory")
    public void processBatch(List<ConsumerRecord<String, EmbeddingRequest>> records) {
        // Batch embed (more efficient than one-by-one)
        List<String> texts = records.stream()
            .map(r -> r.value().getText())
            .toList();
        
        List<float[]> embeddings = embeddingModel.embedBatch(texts);
        
        // Store all at once
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            Document doc = new Document(texts.get(i));
            doc.setEmbedding(embeddings.get(i));
            doc.getMetadata().putAll(records.get(i).value().getMetadata());
            documents.add(doc);
        }
        
        vectorStore.add(documents);
        log.info("Batch embedded {} documents", documents.size());
    }
}

@Configuration
public class BatchKafkaConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmbeddingRequest> 
            batchKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EmbeddingRequest> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setBatchListener(true);
        factory.getContainerProperties().setIdleBetweenPolls(1000);
        // Process up to 50 embeddings per batch
        factory.getContainerProperties().setMaxPollRecords(50);
        return factory;
    }
}
```

---

## Retry & DLQ for AI

```java
@Configuration
public class KafkaRetryConfig {
    
    @Bean
    public DefaultErrorHandler aiErrorHandler(KafkaTemplate<String, Object> template) {
        // DLQ producer
        DeadLetterPublishingRecoverer recoverer = 
            new DeadLetterPublishingRecoverer(template);
        
        // Retry with backoff (AI calls may have transient failures)
        BackOff backOff = new ExponentialBackOff(1000L, 2.0); // 1s, 2s, 4s
        ((ExponentialBackOff) backOff).setMaxElapsedTime(30000L); // Max 30s total
        
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        
        // Don't retry on certain errors
        handler.addNotRetryableExceptions(
            InvalidRequestException.class,   // Bad input, won't fix on retry
            AuthenticationException.class     // API key issue
        );
        
        return handler;
    }
}

// DLQ processor for manual review
@KafkaListener(topics = "ai-requests-dlq")
public void handleDLQ(ConsumerRecord<String, AIRequest> record) {
    log.error("AI request permanently failed: correlationId={}, attempts={}",
        record.value().getCorrelationId(),
        record.headers().lastHeader("retry-count"));
    
    // Notify operations team
    alertService.sendAlert("AI request failed permanently: " + record.value());
    
    // Store for manual reprocessing
    failedRequestRepository.save(record.value());
}
```

---

## Event-Based Agents

```java
@Service
public class EventDrivenAIAgent {
    
    private final ChatClient chatClient;
    
    // Agent reacts to business events
    @KafkaListener(topics = "order-events")
    public void handleOrderEvent(OrderEvent event) {
        switch (event.getType()) {
            case ORDER_PLACED -> handleNewOrder(event);
            case DELIVERY_DELAYED -> handleDelay(event);
            case RETURN_REQUESTED -> handleReturn(event);
        }
    }
    
    private void handleDelay(OrderEvent event) {
        // Agent decides what to do
        String decision = chatClient.prompt()
            .system("""
                You are an order management agent. Decide what action to take 
                for a delayed delivery. Consider customer tier, delay duration, 
                and order value.
                
                Available actions:
                - SEND_UPDATE: Just notify the customer
                - OFFER_DISCOUNT: Offer a small discount on next order
                - EXPEDITE: Upgrade to express shipping
                - REFUND: Process a partial refund
                
                Respond with ONLY the action name.
                """)
            .user("Customer tier: %s, Delay: %d days, Order value: $%.2f"
                .formatted(event.getCustomerTier(), event.getDelayDays(), event.getOrderValue()))
            .call()
            .content();
        
        // Execute the decision
        switch (decision.trim()) {
            case "SEND_UPDATE" -> kafkaTemplate.send("notifications", 
                new NotificationEvent(event.getCustomerId(), "delivery-update"));
            case "OFFER_DISCOUNT" -> kafkaTemplate.send("promotions",
                new DiscountEvent(event.getCustomerId(), 10));
            case "EXPEDITE" -> kafkaTemplate.send("shipping-commands",
                new ExpediteEvent(event.getOrderId()));
            case "REFUND" -> kafkaTemplate.send("payment-commands",
                new RefundEvent(event.getOrderId(), event.getOrderValue() * 0.1));
        }
    }
}
```

---

## AI Job Queues with Priority

```java
// Priority-based AI processing
@Configuration
public class PriorityAIConfig {
    
    // Separate topics by priority
    // ai-requests-high (max latency: 5s)
    // ai-requests-normal (max latency: 30s)  
    // ai-requests-low (max latency: 5min)
    
    @KafkaListener(topics = "ai-requests-high", concurrency = "10")
    public void processHigh(AIRequest request) {
        // Use fastest model, highest priority
        processWithModel(request, "gpt-4-turbo", Duration.ofSeconds(5));
    }
    
    @KafkaListener(topics = "ai-requests-normal", concurrency = "5")
    public void processNormal(AIRequest request) {
        processWithModel(request, "gpt-4", Duration.ofSeconds(30));
    }
    
    @KafkaListener(topics = "ai-requests-low", concurrency = "2")
    public void processLow(AIRequest request) {
        processWithModel(request, "gpt-3.5-turbo", Duration.ofMinutes(5));
    }
}
```

---

## Interview Questions

**Q: Why use Kafka for AI workloads?**
AI calls are slow (seconds to minutes), making synchronous processing impractical at scale. Kafka provides: async processing (decouple request from response), backpressure handling (don't overwhelm LLM APIs), retry/DLQ (handle failures gracefully), pipeline composition (chunk → embed → store), and scalable concurrency (multiple consumers).

**Q: How would you design a document processing pipeline with Kafka?**
Multi-stage pipeline: upload → extract text → chunk → embed → store. Each stage is a separate topic and consumer group. Benefits: independent scaling per stage (embedding is CPU-heavy), fault isolation (one stage failing doesn't block others), batch processing for embeddings (efficient API usage), and DLQ per stage for debugging.

**Q: How do you handle rate limiting for LLM APIs in a Kafka consumer?**
Control concurrency in consumer settings, use consumer pause/resume based on rate limit headers, implement token bucket rate limiter before LLM calls, use batch consumers to amortize calls, and configure consumer poll intervals to match API rate limits.

---

## Key Takeaways

1. **Kafka + AI is natural** — async processing fits LLM call patterns
2. **Pipeline architecture** — chunk → embed → store as separate stages
3. **Batch processing** — embed in batches for efficiency
4. **Priority queues** — different SLAs for different AI workloads
5. **DLQ strategy** — AI calls fail, handle gracefully
6. **Your Kafka expertise directly applies** — same patterns, new use case
