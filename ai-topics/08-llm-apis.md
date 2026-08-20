# LLM APIs

## Overview
LLM APIs are how applications communicate with language models. Understanding API patterns is essential for building AI-powered applications. This is where your backend engineering skills directly apply.

---

## Chat Completion

The primary API pattern for conversational AI.

```python
import openai

client = openai.OpenAI(api_key="your-key")

response = client.chat.completions.create(
    model="gpt-4",
    messages=[
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "What is Spring Boot?"},
        {"role": "assistant", "content": "Spring Boot is a framework..."},
        {"role": "user", "content": "How does it differ from Spring?"}
    ],
    temperature=0.7,
    max_tokens=500
)

answer = response.choices[0].message.content
```

### Message Roles
```
system:    Sets behavior/rules for the model
user:      Human input
assistant: Model's previous responses (conversation history)
tool:      Results from tool/function calls
```

### Java (Spring AI)
```java
@Service
public class ChatService {
    
    private final ChatClient chatClient;
    
    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant.")
            .build();
    }
    
    public String chat(String userMessage) {
        return chatClient.prompt()
            .user(userMessage)
            .call()
            .content();
    }
}
```

---

## Streaming

Receive tokens as they're generated (better UX for long responses).

```python
# Python streaming
stream = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Explain microservices"}],
    stream=True
)

for chunk in stream:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="", flush=True)
```

### Java (Spring AI Streaming)
```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String message) {
    return chatClient.prompt()
        .user(message)
        .stream()
        .content();
}
```

### Server-Sent Events (SSE) for Frontend
```javascript
// Angular/React frontend
const eventSource = new EventSource('/api/chat/stream?message=Hello');

eventSource.onmessage = (event) => {
    const token = event.data;
    appendToDisplay(token);
};

eventSource.onerror = () => {
    eventSource.close();
};
```

---

## Structured Output

Force the model to return data in a specific schema.

```python
# OpenAI structured output
response = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Extract person info: John Smith, 30, engineer at Google"}],
    response_format={
        "type": "json_schema",
        "json_schema": {
            "name": "person_info",
            "schema": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "age": {"type": "integer"},
                    "company": {"type": "string"},
                    "role": {"type": "string"}
                },
                "required": ["name", "age", "company", "role"]
            }
        }
    }
)
# Guaranteed valid JSON matching schema
```

### Spring AI Structured Output
```java
public record PersonInfo(String name, int age, String company, String role) {}

PersonInfo info = chatClient.prompt()
    .user("Extract person info: John Smith, 30, engineer at Google")
    .call()
    .entity(PersonInfo.class);

// Returns: PersonInfo("John Smith", 30, "Google", "engineer")
```

---

## Function Calling / Tool Calling

Allow the LLM to invoke external functions/APIs.

```python
# Define tools
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Get the current weather in a location",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {"type": "string", "description": "City name"},
                    "unit": {"type": "string", "enum": ["celsius", "fahrenheit"]}
                },
                "required": ["location"]
            }
        }
    }
]

# Send message with tools
response = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "What's the weather in London?"}],
    tools=tools,
    tool_choice="auto"
)

# Model decides to call a function
tool_call = response.choices[0].message.tool_calls[0]
# tool_call.function.name = "get_weather"
# tool_call.function.arguments = '{"location": "London", "unit": "celsius"}'

# Execute the function
weather_result = get_weather("London", "celsius")

# Send result back
messages.append(response.choices[0].message)
messages.append({
    "role": "tool",
    "tool_call_id": tool_call.id,
    "content": json.dumps(weather_result)
})

# Get final response
final = client.chat.completions.create(
    model="gpt-4",
    messages=messages,
    tools=tools
)
# "The weather in London is 15°C and cloudy."
```

### Flow Diagram
```
User: "What's the weather in London?"
          ↓
LLM: "I need to call get_weather(location='London')"
          ↓
App: Executes get_weather("London") → {temp: 15, condition: "cloudy"}
          ↓
LLM: "The current weather in London is 15°C and cloudy."
          ↓
User: Gets natural language response
```

---

## Token Usage & Cost

```python
response = client.chat.completions.create(...)

# Token usage
usage = response.usage
print(f"Prompt tokens: {usage.prompt_tokens}")      # Input cost
print(f"Completion tokens: {usage.completion_tokens}")  # Output cost
print(f"Total tokens: {usage.total_tokens}")

# Cost calculation (example GPT-4 pricing)
# Input: $0.03 per 1K tokens
# Output: $0.06 per 1K tokens
input_cost = (usage.prompt_tokens / 1000) * 0.03
output_cost = (usage.completion_tokens / 1000) * 0.06
total_cost = input_cost + output_cost
```

### Cost Optimization Strategies
```
1. Use smaller models for simple tasks (GPT-3.5 vs GPT-4)
2. Minimize context (only send relevant info)
3. Set max_tokens to limit output
4. Cache common responses (Redis)
5. Use structured output (shorter than prose)
6. Batch similar requests
7. Use embeddings for filtering before LLM calls
```

---

## Context Management

Managing conversation history within token limits.

```python
class ConversationManager:
    def __init__(self, max_tokens=4000):
        self.messages = []
        self.system_prompt = {"role": "system", "content": "You are helpful."}
        self.max_tokens = max_tokens
    
    def add_message(self, role: str, content: str):
        self.messages.append({"role": role, "content": content})
        self._trim_history()
    
    def _trim_history(self):
        """Remove oldest messages if exceeding token limit"""
        while self._count_tokens() > self.max_tokens:
            # Keep system prompt, remove oldest user/assistant pair
            if len(self.messages) > 2:
                self.messages.pop(0)
                self.messages.pop(0)
    
    def get_messages(self):
        return [self.system_prompt] + self.messages
```

### Strategies
```
1. Sliding window: Keep last N messages
2. Summarization: Summarize old messages, keep recent ones
3. Selective: Keep system prompt + recent + most relevant
4. Token-based: Trim until under limit
```

---

## Model Selection

Choosing the right model for the task.

| Use Case | Recommended | Why |
|----------|-------------|-----|
| Complex reasoning | GPT-4 / Claude 3.5 Opus | Best quality |
| Code generation | GPT-4 / Claude 3.5 Sonnet | Good balance |
| Simple classification | GPT-3.5 / Claude Haiku | Fast, cheap |
| Embeddings | text-embedding-3-small | Optimized for vectors |
| Summarization | GPT-4 / Claude Sonnet | Good comprehension |
| Data extraction | GPT-4 with structured output | Reliable JSON |

---

## API Authentication

```python
# Environment variable (recommended)
import os
client = openai.OpenAI(api_key=os.environ["OPENAI_API_KEY"])

# AWS Bedrock (uses AWS credentials)
import boto3
bedrock = boto3.client("bedrock-runtime", region_name="us-east-1")
```

```java
// Spring AI with properties
// application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
    # Or AWS Bedrock
    bedrock:
      aws:
        region: us-east-1
        access-key: ${AWS_ACCESS_KEY}
        secret-key: ${AWS_SECRET_KEY}
```

---

## Error Handling & Retry

```python
import time
from openai import RateLimitError, APIError, APITimeoutError

def call_llm_with_retry(messages, max_retries=3):
    for attempt in range(max_retries):
        try:
            response = client.chat.completions.create(
                model="gpt-4",
                messages=messages,
                timeout=30
            )
            return response.choices[0].message.content
        
        except RateLimitError:
            wait_time = 2 ** attempt  # Exponential backoff
            print(f"Rate limited. Waiting {wait_time}s...")
            time.sleep(wait_time)
        
        except APITimeoutError:
            print(f"Timeout. Attempt {attempt + 1}/{max_retries}")
            continue
        
        except APIError as e:
            if e.status_code >= 500:  # Server error, retry
                time.sleep(1)
                continue
            raise  # Client error, don't retry
    
    raise Exception("Max retries exceeded")
```

### Spring AI Retry
```java
@Configuration
public class AIConfig {
    
    @Bean
    public RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(1000, 2, 10000)
            .retryOn(AIException.class)
            .build();
    }
}
```

---

## Rate Limiting

```python
import time
from collections import deque

class RateLimiter:
    def __init__(self, max_requests: int, window_seconds: int):
        self.max_requests = max_requests
        self.window = window_seconds
        self.requests = deque()
    
    def wait_if_needed(self):
        now = time.time()
        # Remove old requests outside window
        while self.requests and self.requests[0] < now - self.window:
            self.requests.popleft()
        
        if len(self.requests) >= self.max_requests:
            sleep_time = self.requests[0] + self.window - now
            time.sleep(sleep_time)
        
        self.requests.append(time.time())

# Usage
limiter = RateLimiter(max_requests=60, window_seconds=60)

for query in queries:
    limiter.wait_if_needed()
    response = call_llm(query)
```

---

## Interview Questions

**Q: How would you implement streaming in a Spring Boot AI application?**
Use Spring AI's `stream()` method which returns a Flux<String>. Expose it via a controller endpoint with MediaType.TEXT_EVENT_STREAM_VALUE. On the frontend, use EventSource or fetch with ReadableStream to consume the SSE stream and render tokens progressively.

**Q: How do you handle rate limiting when calling LLM APIs?**
Implement exponential backoff with retry on 429 status codes, use a rate limiter (token bucket or sliding window) to proactively throttle requests, batch similar requests, cache repeated queries in Redis, and implement circuit breaker pattern for cascading failures.

**Q: What's the difference between function calling and RAG?**
Function calling lets the LLM interact with external systems (APIs, databases) to take actions or get real-time data. RAG provides the LLM with relevant documents as context to answer questions. Use function calling for actions and real-time data; use RAG for knowledge retrieval from static documents.

**Q: How do you manage context in a long conversation?**
Sliding window (keep last N messages), summarize older messages, token counting with trimming, selective retention of important messages, and hybrid approaches combining recent history with a summary of older context.

**Q: How do you optimize LLM API costs in production?**
Use cheaper models for simple tasks (model routing), cache common responses, minimize prompt size (only relevant context), set max_tokens, use structured output (shorter), batch requests, implement semantic caching (similar queries → same cached response), and monitor token usage per endpoint.

---

## Key Takeaways

1. **Chat completion** is the standard API pattern — messages array with roles
2. **Streaming** is essential for good UX — use SSE
3. **Tool/function calling** connects LLMs to external systems
4. **Token management** directly impacts cost and performance
5. **Always implement retry + rate limiting** — APIs are unreliable
6. **Model selection matters** — don't use GPT-4 for simple classification
7. **Structured output** ensures reliable integration with your application
