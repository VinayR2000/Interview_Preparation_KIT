# AI Production Engineering

## Overview
This is where your existing backend engineering experience becomes directly valuable. Building production AI systems requires the same patterns you already know — API design, caching, resilience, observability — applied to AI-specific challenges.

---

## API Design

```java
@RestController
@RequestMapping("/api/v1/ai")
public class AIController {
    
    // Synchronous chat (short responses)
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody @Valid ChatRequest request) {
        String response = chatService.chat(request.sessionId(), request.message());
        return ResponseEntity.ok(new ChatResponse(response, tokenUsage));
    }
    
    // Streaming (long responses, better UX)
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @RequestParam String sessionId,
            @RequestParam String message) {
        return chatService.stream(sessionId, message)
            .map(token -> ServerSentEvent.builder(token).build());
    }
    
    // Async processing (heavy tasks: document ingestion, batch)
    @PostMapping("/documents/ingest")
    public ResponseEntity<JobResponse> ingestDocument(@RequestParam MultipartFile file) {
        String jobId = UUID.randomUUID().toString();
        ingestionService.ingestAsync(jobId, file.getResource());
        return ResponseEntity.accepted()
            .body(new JobResponse(jobId, "processing", "/api/v1/ai/jobs/" + jobId));
    }
    
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobStatus> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(jobService.getStatus(jobId));
    }
}
```

---

## Async Processing

LLM calls are slow (1-30 seconds). Design for async from the start.

```java
@Service
public class AsyncAIService {
    
    private final ThreadPoolTaskExecutor aiExecutor;
    
    @Async("aiExecutor")
    public CompletableFuture<String> processAsync(String input) {
        String result = chatClient.prompt().user(input).call().content();
        return CompletableFuture.completedFuture(result);
    }
    
    // Kafka-based async processing for heavy workloads
    @KafkaListener(topics = "ai-requests")
    public void handleAIRequest(AIRequest request) {
        try {
            String result = processRequest(request);
            kafkaTemplate.send("ai-responses", request.getCorrelationId(), result);
        } catch (Exception e) {
            // Send to DLQ
            kafkaTemplate.send("ai-requests-dlq", request.getCorrelationId(), request);
        }
    }
}

@Configuration
public class AsyncConfig {
    
    @Bean("aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        return executor;
    }
}
```

---

## Caching (Redis)

```java
@Service
public class CachedAIService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ChatClient chatClient;
    
    // Exact match cache
    @Cacheable(value = "ai-responses", key = "#question", unless = "#result == null")
    public String getCachedResponse(String question) {
        return chatClient.prompt().user(question).call().content();
    }
    
    // Semantic cache (cache similar questions)
    public String semanticCachedQuery(String question) {
        // 1. Embed the question
        float[] queryEmbedding = embeddingModel.embed(question);
        
        // 2. Check if similar question was already answered
        List<CachedEntry> similar = searchCache(queryEmbedding, 0.95); // High threshold
        if (!similar.isEmpty()) {
            log.info("Cache hit (semantic): similarity={}", similar.get(0).getSimilarity());
            return similar.get(0).getResponse();
        }
        
        // 3. Cache miss — call LLM
        String response = chatClient.prompt().user(question).call().content();
        
        // 4. Store in semantic cache
        cacheEntry(question, queryEmbedding, response);
        
        return response;
    }
}
```

### Cache Strategy
```
What to cache:
- Frequently asked questions (FAQ-type)
- Embedding results (expensive to recompute)
- RAG retrieval results (if documents don't change often)
- Structured extractions (same document → same result)

What NOT to cache:
- Personalized responses (user-specific context)
- Time-sensitive queries ("what time is it?")
- Creative outputs (should vary each time)
- Tool-calling results (real-time data)

TTL recommendations:
- Embeddings: 24h-7d (until documents change)
- FAQ responses: 1h-24h
- Search results: 5min-1h
- Token counts: 7d (deterministic)
```

---

## Rate Limiting

```java
@Component
public class AIRateLimiter {
    
    private final RedisTemplate<String, String> redis;
    
    // Token bucket per user
    public boolean allowRequest(String userId, int estimatedTokens) {
        String key = "rate:" + userId;
        
        // Check daily token budget
        Long usedTokens = redis.opsForValue().increment(key, estimatedTokens);
        if (usedTokens == null) usedTokens = (long) estimatedTokens;
        
        // Set expiry on first use
        if (usedTokens == estimatedTokens) {
            redis.expire(key, Duration.ofDays(1));
        }
        
        long dailyLimit = getUserDailyLimit(userId); // e.g., 100K tokens/day
        
        if (usedTokens > dailyLimit) {
            log.warn("User {} exceeded daily token limit: {}/{}", userId, usedTokens, dailyLimit);
            return false;
        }
        
        return true;
    }
    
    // Concurrent request limiting
    public boolean allowConcurrent(String userId) {
        String key = "concurrent:" + userId;
        Long current = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofMinutes(5)); // Auto-cleanup
        
        if (current > 5) { // Max 5 concurrent AI requests
            redis.opsForValue().decrement(key);
            return false;
        }
        return true;
    }
}
```

---

## Retry & Circuit Breaker

```java
@Service
public class ResilientAIService {
    
    private final ChatClient chatClient;
    private final RetryTemplate retryTemplate;
    private final CircuitBreaker circuitBreaker;
    
    public String chat(String message) {
        return circuitBreaker.run(
            () -> retryTemplate.execute(ctx -> {
                log.info("AI call attempt {}", ctx.getRetryCount() + 1);
                return chatClient.prompt().user(message).call().content();
            }),
            throwable -> fallback(message, throwable)
        );
    }
    
    private String fallback(String message, Throwable throwable) {
        log.error("AI service unavailable, using fallback", throwable);
        
        // Option 1: Try a different model
        try {
            return fallbackChatClient.prompt().user(message).call().content();
        } catch (Exception e) {
            // Option 2: Return cached/static response
            return "I'm experiencing high demand right now. Please try again in a moment.";
        }
    }
}

@Configuration
public class ResilienceConfig {
    
    @Bean
    public RetryTemplate aiRetryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(1000, 2.0, 10000)
            .retryOn(AIServiceException.class)
            .retryOn(TimeoutException.class)
            .build();
    }
    
    @Bean
    public CircuitBreaker aiCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("ai-service", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .build());
    }
}
```

---

## Timeout & Fallback Models

```java
@Service
public class ModelRouterService {
    
    private final ChatClient primaryModel;   // GPT-4 (best quality)
    private final ChatClient fallbackModel;  // GPT-3.5 (faster, cheaper)
    private final ChatClient localModel;     // Ollama (always available)
    
    public String chat(String message, QualityRequirement quality) {
        // Route based on requirement
        return switch (quality) {
            case HIGH -> callWithFallback(primaryModel, fallbackModel, message, Duration.ofSeconds(30));
            case MEDIUM -> callWithFallback(fallbackModel, localModel, message, Duration.ofSeconds(15));
            case LOW -> callWithTimeout(localModel, message, Duration.ofSeconds(10));
        };
    }
    
    private String callWithFallback(ChatClient primary, ChatClient fallback, 
                                     String message, Duration timeout) {
        try {
            return CompletableFuture.supplyAsync(() -> 
                primary.prompt().user(message).call().content()
            ).get(timeout.getSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException e) {
            log.warn("Primary model failed, using fallback");
            return fallback.prompt().user(message).call().content();
        }
    }
}
```

---

## Token Optimization & Cost

```java
@Service
public class TokenOptimizer {
    
    // Estimate tokens before calling
    public int estimateTokens(String text) {
        // Rough estimate: 1 token ≈ 4 characters
        return text.length() / 4;
    }
    
    // Trim context to fit budget
    public String trimToTokenBudget(String context, int maxTokens) {
        int estimated = estimateTokens(context);
        if (estimated <= maxTokens) return context;
        
        // Trim from the middle (keep beginning and end — lost-in-middle problem)
        int targetChars = maxTokens * 4;
        int halfTarget = targetChars / 2;
        
        return context.substring(0, halfTarget) + 
               "\n...[content trimmed]...\n" + 
               context.substring(context.length() - halfTarget);
    }
    
    // Track and report costs
    public void trackUsage(String userId, String model, int inputTokens, int outputTokens) {
        double cost = calculateCost(model, inputTokens, outputTokens);
        
        meterRegistry.counter("ai.tokens.input", "model", model).increment(inputTokens);
        meterRegistry.counter("ai.tokens.output", "model", model).increment(outputTokens);
        meterRegistry.counter("ai.cost.usd", "model", model, "user", userId).increment(cost);
    }
}
```

---

## Logging, Monitoring & Observability

```java
@Service
public class AIObservability {
    
    private final MeterRegistry meterRegistry;
    
    public String observedChat(String sessionId, String message) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        try {
            String response = chatClient.prompt().user(message).call().content();
            
            // Record metrics
            timer.stop(meterRegistry.timer("ai.chat.duration", "status", "success"));
            meterRegistry.counter("ai.chat.requests", "status", "success").increment();
            
            // Structured logging
            log.info("AI chat completed", 
                kv("sessionId", sessionId),
                kv("inputLength", message.length()),
                kv("outputLength", response.length()),
                kv("durationMs", timer.duration())
            );
            
            return response;
            
        } catch (Exception e) {
            timer.stop(meterRegistry.timer("ai.chat.duration", "status", "error"));
            meterRegistry.counter("ai.chat.requests", "status", "error", 
                "errorType", e.getClass().getSimpleName()).increment();
            throw e;
        }
    }
}
```

### Key Metrics to Track
```yaml
# Prometheus metrics
ai_chat_requests_total{status="success|error", model="gpt-4"}
ai_chat_duration_seconds{quantile="0.5|0.95|0.99"}
ai_tokens_total{type="input|output", model="gpt-4"}
ai_cost_usd_total{model="gpt-4"}
ai_cache_hits_total{type="exact|semantic"}
ai_retrieval_results{quality="high|medium|low"}
ai_errors_total{type="timeout|rate_limit|server_error"}
```

### Tracing
```java
// Distributed tracing for AI requests
@Service
public class TracedAIService {
    
    private final Tracer tracer;
    
    public String chat(String message) {
        Span span = tracer.spanBuilder("ai.chat").startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("ai.model", "gpt-4");
            span.setAttribute("ai.input.tokens", estimateTokens(message));
            
            // Retrieval span
            Span retrievalSpan = tracer.spanBuilder("ai.retrieval").startSpan();
            List<Document> docs = vectorStore.similaritySearch(message);
            retrievalSpan.setAttribute("ai.retrieval.count", docs.size());
            retrievalSpan.end();
            
            // Generation span
            Span generationSpan = tracer.spanBuilder("ai.generation").startSpan();
            String response = chatClient.prompt().user(message).call().content();
            generationSpan.end();
            
            span.setAttribute("ai.output.tokens", estimateTokens(response));
            return response;
        } finally {
            span.end();
        }
    }
}
```

---

## Interview Questions

**Q: How do you handle LLM API failures in production?**
Retry with exponential backoff for transient errors, circuit breaker for sustained failures, fallback to cheaper/faster models, graceful degradation (cached responses, static messages), timeout configuration per endpoint, and monitoring with alerts on error rate spikes.

**Q: How do you optimize AI costs in production?**
Model routing (use cheap models for simple tasks), semantic caching, input trimming (minimize context), max_tokens limits, batch processing, token budget per user/endpoint, monitoring spend with alerts, and evaluating if embeddings-only solutions can replace some LLM calls.

**Q: How would you design the observability for an AI system?**
Metrics: latency (p50/p95/p99), token usage, cost, error rates, cache hit rates, retrieval quality. Logging: structured logs with session IDs, input/output lengths, model used. Tracing: distributed traces showing retrieval → generation → tool calls. Alerts: cost anomalies, latency spikes, error rate thresholds.

---

## Key Takeaways

1. **Design for async** — LLM calls are slow, don't block threads
2. **Cache aggressively** — semantic caching saves money and reduces latency
3. **Implement resilience** — retry, circuit breaker, fallback models
4. **Monitor everything** — tokens, cost, latency, errors, quality
5. **Rate limit per user** — protect your budget and the LLM provider
6. **Your backend skills transfer directly** — same patterns, new domain
