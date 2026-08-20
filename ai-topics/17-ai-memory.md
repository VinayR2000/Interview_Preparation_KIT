# AI Memory

## Overview
AI memory enables agents and chatbots to maintain context across interactions. Without memory, every conversation starts from scratch. Effective memory management is critical for building useful, personalized AI systems.

---

## Types of Memory

### Short-Term Memory
Current conversation context (within the session).

```
Session: User is discussing their order issue
Turn 1: "My order hasn't arrived" → Agent looks up order
Turn 2: "Can I get a refund?" → Agent knows WHICH order (from turn 1)
Turn 3: "Actually, just reship it" → Agent knows the context

Implementation: Message history in the current request
Lifetime: Single session/conversation
```

### Long-Term Memory
Persistent information across sessions.

```
Session 1 (January): User prefers dark mode, is a Java developer
Session 2 (March): Agent remembers preferences and expertise level
Session 3 (June): Agent adjusts explanations for Java background

Implementation: Database storage (relational or vector)
Lifetime: Permanent or time-bounded
```

---

## Conversation History

The simplest form of memory — keeping previous messages.

```java
@Service
public class ConversationService {
    
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    
    public ConversationService(ChatClient.Builder builder) {
        this.chatMemory = new InMemoryChatMemory();
        this.chatClient = builder
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
```

### Window-Based Memory
Keep only the last N messages to stay within token limits.

```java
// Keep last 20 messages
MessageChatMemoryAdvisor advisor = new MessageChatMemoryAdvisor(
    chatMemory, 
    20  // window size
);
```

---

## Session Memory

Per-session state that tracks context within a single interaction.

```java
public class SessionMemory {
    private final String sessionId;
    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Object> context = new HashMap<>();
    private final Instant createdAt;
    private Instant lastActivityAt;
    
    public void addContext(String key, Object value) {
        context.put(key, value);
        lastActivityAt = Instant.now();
    }
    
    // Track what the agent has learned this session
    public void recordFinding(String key, String value) {
        context.put("finding:" + key, value);
    }
    
    public String getContextSummary() {
        return context.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));
    }
}
```

---

## User Memory (Long-Term)

Persistent preferences and information about specific users.

```java
@Entity
@Table(name = "user_memory")
public class UserMemory {
    @Id
    private String id;
    private String userId;
    private String key;
    private String value;
    private String category;  // preference, fact, context
    private Instant createdAt;
    private Instant lastAccessedAt;
    private int accessCount;
}

@Service
public class UserMemoryService {
    
    private final UserMemoryRepository repository;
    
    // Store a learned fact about the user
    public void remember(String userId, String key, String value, String category) {
        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setKey(key);
        memory.setValue(value);
        memory.setCategory(category);
        memory.setCreatedAt(Instant.now());
        repository.save(memory);
    }
    
    // Retrieve relevant memories for context
    public List<UserMemory> recall(String userId, String category) {
        return repository.findByUserIdAndCategory(userId, category);
    }
    
    // Build context string for the LLM
    public String buildUserContext(String userId) {
        List<UserMemory> memories = repository.findByUserId(userId);
        if (memories.isEmpty()) return "No prior information about this user.";
        
        return "Known information about this user:\n" +
            memories.stream()
                .map(m -> "- " + m.getKey() + ": " + m.getValue())
                .collect(Collectors.joining("\n"));
    }
}
```

---

## Persistent Memory

Memory that survives application restarts.

```java
// Database-backed chat memory
@Component
public class JdbcChatMemory implements ChatMemory {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            jdbcTemplate.update(
                "INSERT INTO chat_messages (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                conversationId,
                message.getMessageType().name(),
                message.getContent(),
                Instant.now()
            );
        }
    }
    
    @Override
    public List<Message> get(String conversationId, int lastN) {
        return jdbcTemplate.query(
            "SELECT role, content FROM chat_messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT ?",
            (rs, rowNum) -> createMessage(rs.getString("role"), rs.getString("content")),
            conversationId, lastN
        );
    }
    
    @Override
    public void clear(String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_messages WHERE conversation_id = ?", conversationId);
    }
}
```

---

## Vector-Based Memory

Use embeddings to store and retrieve relevant memories semantically.

```java
@Service
public class SemanticMemory {
    
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    
    // Store a memory
    public void store(String userId, String content, Map<String, Object> metadata) {
        metadata.put("userId", userId);
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("type", "memory");
        
        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
    }
    
    // Retrieve relevant memories based on current context
    public List<String> recall(String userId, String currentContext, int topK) {
        List<Document> memories = vectorStore.similaritySearch(
            SearchRequest.query(currentContext)
                .withTopK(topK)
                .withSimilarityThreshold(0.7)
                .withFilterExpression("userId == '" + userId + "' && type == 'memory'")
        );
        
        return memories.stream()
            .map(Document::getContent)
            .toList();
    }
    
    // Auto-extract and store important information from conversation
    public void extractAndStore(String userId, String conversation) {
        String extraction = chatClient.prompt()
            .user("""
                Extract any important facts, preferences, or information about the user 
                from this conversation that would be useful to remember for future interactions.
                Return as a list of facts, one per line. If nothing notable, return "NONE".
                
                Conversation:
                %s
                """.formatted(conversation))
            .call()
            .content();
        
        if (!extraction.contains("NONE")) {
            for (String fact : extraction.split("\n")) {
                store(userId, fact.trim(), Map.of("source", "conversation_extraction"));
            }
        }
    }
}
```

---

## Memory Retrieval Strategies

```java
public class MemoryRetriever {
    
    // Strategy 1: Recency-based (most recent memories first)
    public List<Memory> recencyBased(String userId, int limit) {
        return memoryRepository.findByUserIdOrderByTimestampDesc(userId, limit);
    }
    
    // Strategy 2: Relevance-based (semantic similarity to current query)
    public List<Memory> relevanceBased(String userId, String query, int limit) {
        return vectorStore.similaritySearch(query, userId, limit);
    }
    
    // Strategy 3: Importance-based (frequently accessed memories first)
    public List<Memory> importanceBased(String userId, int limit) {
        return memoryRepository.findByUserIdOrderByAccessCountDesc(userId, limit);
    }
    
    // Strategy 4: Hybrid (combine all signals)
    public List<Memory> hybridRetrieval(String userId, String query, int limit) {
        // Get candidates from each strategy
        List<Memory> recent = recencyBased(userId, limit * 2);
        List<Memory> relevant = relevanceBased(userId, query, limit * 2);
        List<Memory> important = importanceBased(userId, limit * 2);
        
        // Score and merge
        Map<String, Double> scores = new HashMap<>();
        recent.forEach(m -> scores.merge(m.getId(), 0.3 * recencyScore(m), Double::sum));
        relevant.forEach(m -> scores.merge(m.getId(), 0.5 * m.getSimilarity(), Double::sum));
        important.forEach(m -> scores.merge(m.getId(), 0.2 * importanceScore(m), Double::sum));
        
        // Return top-K by combined score
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(e -> getMemoryById(e.getKey()))
            .toList();
    }
}
```

---

## Complete Memory Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    AI Application                         │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │                 Memory Manager                     │   │
│  │                                                    │   │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────┐  │   │
│  │  │ Session  │  │   User   │  │    Semantic     │  │   │
│  │  │ Memory   │  │  Memory  │  │    Memory       │  │   │
│  │  │(in-memory)│  │ (RDBMS) │  │ (Vector Store) │  │   │
│  │  └──────────┘  └──────────┘  └────────────────┘  │   │
│  └──────────────────────────────────────────────────┘   │
│                          ↓                               │
│  Context Builder: combines relevant memories             │
│  into system prompt for the LLM                          │
└─────────────────────────────────────────────────────────┘
```

---

## Interview Questions

**Q: How do you handle memory when context windows are limited?**
Use a layered approach: keep recent messages in full, summarize older messages, and use vector-based retrieval for long-term memories. Implement a memory manager that composes the optimal context from session history, relevant long-term memories, and user preferences within the token budget.

**Q: What's the difference between session memory and user memory?**
Session memory exists for a single conversation (tracks current context, what's been discussed, intermediate results). User memory persists across sessions (preferences, learned facts, interaction history). Session memory is typically in-memory; user memory is database-backed.

**Q: How would you implement memory for a customer support agent?**
Short-term: current conversation history. Session: customer ID, order being discussed, actions taken. Long-term: customer preferences, past issues, sentiment trends. Use vector-based memory to recall relevant past interactions when similar issues arise. Auto-extract and store important facts from each conversation.

---

## Key Takeaways

1. **Memory makes AI personal** — without it, every interaction starts cold
2. **Layer your memory** — session (fast) + user (persistent) + semantic (relevant)
3. **Vector-based memory** enables semantic recall (find memories by meaning)
4. **Auto-extraction** reduces manual memory management
5. **Token budgets** require smart memory selection (not everything fits in context)
6. **Privacy matters** — users should be able to see/delete their stored memories
