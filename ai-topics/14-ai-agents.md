# AI Agents ⭐⭐⭐⭐⭐

## Overview
AI Agents are autonomous systems that use LLMs to reason, plan, and take actions. Unlike simple chatbots that just generate text, agents can interact with external systems, make decisions, and complete multi-step tasks.

---

## What is an AI Agent?

```
Chatbot:
  User → LLM → Response (text only, no actions)

Agent:
  User → LLM → Think → Choose Tool → Execute → Observe → Think → ... → Response
  (can take actions, use tools, access systems)
```

### Agent vs Chatbot

| Feature | Chatbot | Agent |
|---------|---------|-------|
| Output | Text only | Text + Actions |
| Tools | None | APIs, DBs, code execution |
| Planning | Single response | Multi-step reasoning |
| Memory | Limited conversation | Short + long-term memory |
| Autonomy | Reactive only | Proactive decision-making |
| State | Stateless | Maintains state |

---

## Agent Loop

The fundamental pattern of all AI agents.

```
┌─────────────────────────────────────────────────┐
│                  AGENT LOOP                       │
│                                                   │
│  ┌─────────┐                                     │
│  │  INPUT  │ ← User request / task               │
│  └────┬────┘                                     │
│       ↓                                          │
│  ┌─────────┐                                     │
│  │  THINK  │ ← LLM reasons about what to do     │
│  └────┬────┘                                     │
│       ↓                                          │
│  ┌──────────────┐   No action needed?            │
│  │ DECIDE/PLAN  │ ──────────→ Final Response     │
│  └──────┬───────┘                                │
│         ↓ Action needed                          │
│  ┌─────────────┐                                 │
│  │ SELECT TOOL │ ← Choose which tool to use      │
│  └──────┬──────┘                                 │
│         ↓                                        │
│  ┌──────────────┐                                │
│  │ EXECUTE TOOL │ ← Call API, query DB, etc.     │
│  └──────┬───────┘                                │
│         ↓                                        │
│  ┌───────────┐                                   │
│  │  OBSERVE  │ ← Process tool result             │
│  └─────┬─────┘                                   │
│        ↓                                         │
│   Loop back to THINK                             │
│   (until task is complete)                       │
└─────────────────────────────────────────────────┘
```

---

## Planning & Reasoning

### ReAct Pattern (Reasoning + Acting)
```
User: "Book a flight from NYC to London for next Friday under $500"

Agent:
  Thought: I need to search for flights from NYC to London on [date]. 
           Let me check available options under $500.
  Action: search_flights(from="NYC", to="London", date="2024-03-15", max_price=500)
  Observation: Found 3 flights: UA101 ($450), BA178 ($480), VS4 ($520)
  
  Thought: VS4 is over budget. UA101 is cheapest. Let me get details.
  Action: get_flight_details(flight="UA101")
  Observation: UA101: Departs 7PM, arrives 7AM next day, Economy
  
  Thought: I have a good option within budget. Let me present it to the user.
  Final Answer: I found a flight for you: United UA101, NYC→London, 
                departing Friday at 7PM, arriving Saturday 7AM. 
                Price: $450 (Economy). Would you like me to book it?
```

### Planning Strategies
```
1. Sequential: Step-by-step execution
   Task → Step 1 → Step 2 → Step 3 → Done

2. Parallel: Independent subtasks run simultaneously
   Task → [Step 1a, Step 1b, Step 1c] → Merge → Done

3. Iterative: Refine approach based on results
   Task → Attempt → Evaluate → Refine → Attempt → ... → Done

4. Hierarchical: Break into sub-agents
   Task → [Sub-agent A handles part 1]
        → [Sub-agent B handles part 2]
        → Combine results → Done
```

---

## Tool Selection & Execution

```java
// Spring AI Agent with tools
@Component
public class CustomerSupportAgent {
    
    @Tool(description = "Look up customer information by customer ID or email")
    public CustomerInfo lookupCustomer(
        @ToolParam(description = "Customer ID or email address") String identifier
    ) {
        return customerService.find(identifier);
    }
    
    @Tool(description = "Get order details including status and tracking")
    public OrderDetails getOrder(
        @ToolParam(description = "Order ID") String orderId
    ) {
        return orderService.getDetails(orderId);
    }
    
    @Tool(description = "Create a support ticket for issues that need escalation")
    public TicketResult createTicket(
        @ToolParam(description = "Customer ID") String customerId,
        @ToolParam(description = "Issue description") String issue,
        @ToolParam(description = "Priority: low, medium, high") String priority
    ) {
        return ticketService.create(customerId, issue, Priority.valueOf(priority.toUpperCase()));
    }
    
    @Tool(description = "Process a refund for an order")
    public RefundResult processRefund(
        @ToolParam(description = "Order ID to refund") String orderId,
        @ToolParam(description = "Reason for refund") String reason
    ) {
        return paymentService.refund(orderId, reason);
    }
}
```

### Agent Execution Flow
```
User: "My order #12345 hasn't arrived. Can I get a refund?"

Agent thinks: I need to look up this order first.
→ Calls: getOrder("12345")
← Result: {status: "shipped", shipped_date: "2024-01-10", tracking: "...", 
            delivery_estimate: "2024-01-15"}

Agent thinks: Order was shipped 10 days ago and should have arrived.
              Let me check the customer info.
→ Calls: lookupCustomer(session.customerId)
← Result: {name: "John", email: "john@...", tier: "premium"}

Agent thinks: Premium customer, order is late. I should process the refund.
→ Calls: processRefund("12345", "Order not received after estimated delivery")
← Result: {refund_id: "R789", amount: 49.99, status: "processed"}

Agent responds: "I've processed a refund of $49.99 for order #12345. 
                 The refund (ID: R789) should appear in your account 
                 within 3-5 business days."
```

---

## Memory & State

```java
@Service
public class StatefulAgent {
    
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final Map<String, AgentState> sessionState = new ConcurrentHashMap<>();
    
    public String process(String sessionId, String input) {
        // Get or create session state
        AgentState state = sessionState.computeIfAbsent(sessionId, AgentState::new);
        
        // Update state with new input
        state.addUserInput(input);
        
        // Agent call with memory and state context
        String response = chatClient.prompt()
            .system(buildSystemPrompt(state))
            .user(input)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .tools(agentTools)
            .call()
            .content();
        
        // Update state with response
        state.addAgentResponse(response);
        
        return response;
    }
    
    private String buildSystemPrompt(AgentState state) {
        return """
            You are a customer support agent.
            
            Current context:
            - Customer: %s
            - Previous actions taken: %s
            - Unresolved issues: %s
            
            Guidelines:
            - Always verify customer identity first
            - Check order status before processing refunds
            - Escalate security-related issues
            """.formatted(
                state.getCustomerInfo(),
                state.getActionHistory(),
                state.getOpenIssues()
            );
    }
}
```

---

## Agent Workflows

### Sequential Workflow
```java
public class DocumentProcessingAgent {
    
    public ProcessingResult process(Document document) {
        // Step 1: Extract text
        String text = extractText(document);
        
        // Step 2: Classify document type
        String docType = classifyDocument(text);
        
        // Step 3: Extract relevant entities
        Map<String, String> entities = extractEntities(text, docType);
        
        // Step 4: Validate extracted data
        ValidationResult validation = validate(entities, docType);
        
        // Step 5: Store or escalate
        if (validation.isValid()) {
            return store(entities);
        } else {
            return escalateToHuman(document, validation.getIssues());
        }
    }
}
```

### Event-Driven Agent
```java
@Service
public class EventDrivenAgent {
    
    @KafkaListener(topics = "customer-events")
    public void handleEvent(CustomerEvent event) {
        switch (event.getType()) {
            case ORDER_DELAYED -> handleDelayedOrder(event);
            case COMPLAINT_RECEIVED -> handleComplaint(event);
            case HIGH_VALUE_RETURN -> handleHighValueReturn(event);
        }
    }
    
    private void handleDelayedOrder(CustomerEvent event) {
        // Agent autonomously:
        // 1. Checks order status
        // 2. Determines appropriate action
        // 3. Sends proactive communication
        // 4. Offers compensation if needed
        
        String action = chatClient.prompt()
            .system("You decide how to handle delayed orders...")
            .user("Order %s is delayed. Customer tier: %s. Delay: %d days"
                .formatted(event.getOrderId(), event.getCustomerTier(), event.getDelayDays()))
            .tools(orderTools, communicationTools)
            .call()
            .content();
    }
}
```

---

## Multi-Agent Systems

Multiple specialized agents collaborating on complex tasks.

```
┌──────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR AGENT                      │
│  (Routes tasks, coordinates, merges results)              │
└────────────┬──────────────────┬────────────────┬─────────┘
             ↓                  ↓                ↓
    ┌────────────────┐  ┌──────────────┐  ┌─────────────┐
    │ Research Agent  │  │ Analysis     │  │ Writing     │
    │ - Web search   │  │ Agent        │  │ Agent       │
    │ - Document read│  │ - Data       │  │ - Drafting  │
    │ - Summarize    │  │   processing │  │ - Editing   │
    └────────────────┘  │ - Comparison │  │ - Formatting│
                        └──────────────┘  └─────────────┘
```

```java
@Service
public class MultiAgentOrchestrator {
    
    private final ChatClient researchAgent;
    private final ChatClient analysisAgent;
    private final ChatClient writingAgent;
    
    public Report generateReport(String topic) {
        // Agent 1: Research
        String research = researchAgent.prompt()
            .user("Research the following topic thoroughly: " + topic)
            .tools(webSearchTool, documentReaderTool)
            .call()
            .content();
        
        // Agent 2: Analysis (uses research output)
        String analysis = analysisAgent.prompt()
            .user("Analyze this research and identify key insights:\n" + research)
            .call()
            .content();
        
        // Agent 3: Writing (uses analysis)
        String report = writingAgent.prompt()
            .user("Write a professional report based on this analysis:\n" + analysis)
            .call()
            .content();
        
        return new Report(topic, research, analysis, report);
    }
}
```

---

## Basic Agent Implementation

```java
@Service
public class SimpleAgent {
    
    private final ChatClient chatClient;
    private static final int MAX_ITERATIONS = 10;
    
    public String execute(String task) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
            You are an autonomous agent. To complete tasks:
            1. Think about what needs to be done
            2. Use available tools to gather info or take action
            3. When the task is complete, provide the final answer
            
            Always explain your reasoning before taking action.
            """));
        messages.add(new UserMessage(task));
        
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            ChatResponse response = chatClient.prompt()
                .messages(messages)
                .tools(allTools)
                .call()
                .chatResponse();
            
            // Check if agent is done (no tool calls)
            if (!response.hasToolCalls()) {
                return response.getResult().getOutput().getContent();
            }
            
            // Execute tool calls and continue loop
            messages.add(response.getResult().getOutput());
            for (ToolCall toolCall : response.getToolCalls()) {
                String result = executeTool(toolCall);
                messages.add(new ToolMessage(toolCall.getId(), result));
            }
        }
        
        return "Agent reached maximum iterations without completing the task.";
    }
}
```

---

## Interview Questions

**Q: What is the difference between an AI agent and a chatbot?**
A chatbot generates text responses. An agent can reason about tasks, plan multi-step actions, select and execute tools (APIs, databases), observe results, and iterate until the task is complete. Agents have autonomy — they decide what actions to take rather than just responding to prompts.

**Q: How does the ReAct pattern work?**
ReAct (Reasoning + Acting) alternates between thinking and acting. The agent: Thinks (reasons about what to do next) → Acts (calls a tool) → Observes (processes the result) → Thinks again (decides if task is complete or needs more steps). This loop continues until the agent has enough information to provide a final answer.

**Q: How do you prevent an agent from going into infinite loops?**
Set maximum iteration limits, implement timeout mechanisms, track visited states to detect cycles, use a supervisor that monitors agent progress, and implement fallback behavior (escalate to human) when the agent appears stuck.

**Q: How would you implement a multi-agent system?**
Use an orchestrator agent that decomposes tasks and routes sub-tasks to specialized agents. Each agent has its own tools and expertise. Results flow back to the orchestrator for synthesis. Communication can be sequential (pipeline), parallel (fan-out/fan-in), or event-driven (message passing).

**Q: What are the security concerns with AI agents?**
Agents can be manipulated via prompt injection to misuse tools, access unauthorized data, or take harmful actions. Mitigations: least-privilege tool access, action confirmation for destructive operations, input/output validation, rate limiting tool calls, logging all actions for audit, and human-in-the-loop for high-risk decisions.

---

## Key Takeaways

1. **Agents = LLMs + tools + reasoning loop** — they take actions, not just generate text
2. **The agent loop** (think → act → observe → repeat) is the fundamental pattern
3. **Tool design matters** — clear descriptions help the LLM choose correctly
4. **Memory is essential** — agents need context across steps and sessions
5. **Set safety limits** — max iterations, action confirmation, audit logging
6. **Multi-agent** systems decompose complex tasks across specialists
7. **Spring AI makes agents natural** — tools are just Spring beans with @Tool
