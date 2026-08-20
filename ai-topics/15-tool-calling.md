# Tool Calling

## Overview
Tool calling allows LLMs to interact with external systems — APIs, databases, services — extending their capabilities beyond text generation. The LLM decides WHEN and HOW to call tools, but your application EXECUTES them.

---

## How Tool Calling Works

```
┌──────────────────────────────────────────────────────────────┐
│  1. Define tools (schemas) and send to LLM                    │
│  2. LLM decides which tool to call and with what arguments    │
│  3. Your app executes the tool                                │
│  4. Result sent back to LLM                                   │
│  5. LLM formulates final response                            │
└──────────────────────────────────────────────────────────────┘

User: "What's the weather in London and book me a table for tonight"
         ↓
LLM sees available tools:
  - get_weather(location)
  - book_restaurant(date, time, party_size, location)
  - get_order_status(order_id)
         ↓
LLM decides: I need to call 2 tools
  1. get_weather(location="London")
  2. book_restaurant(date="today", time="19:00", party_size=2, location="London")
         ↓
App executes both tools, returns results
         ↓
LLM: "The weather in London is 12°C and rainy. I've booked a table 
      for 2 tonight at 7PM at Bella Italia. Bring an umbrella!"
```

---

## Function Schemas / Tool Definitions

The LLM needs to know what tools are available and how to call them.

```json
{
    "type": "function",
    "function": {
        "name": "get_order_status",
        "description": "Retrieves the current status of a customer order including shipping and delivery information",
        "parameters": {
            "type": "object",
            "properties": {
                "order_id": {
                    "type": "string",
                    "description": "The unique order identifier (e.g., ORD-12345)"
                }
            },
            "required": ["order_id"]
        }
    }
}
```

### Best Practices for Tool Definitions
```
1. Clear, specific descriptions (LLM uses these to decide when to call)
2. Document parameter types and formats
3. Include example values in descriptions
4. Specify required vs optional parameters
5. Keep tool count manageable (10-20 max per call)
6. Group related tools logically
```

---

## Tool Selection

The LLM decides which tools to use based on:
1. User's intent
2. Tool descriptions
3. Available information

```python
tools = [
    {
        "name": "search_products",
        "description": "Search product catalog by name, category, or features"
    },
    {
        "name": "get_product_details",
        "description": "Get detailed info about a specific product by ID"
    },
    {
        "name": "check_inventory",
        "description": "Check if a product is in stock at a specific location"
    },
    {
        "name": "place_order",
        "description": "Place an order for a product"
    }
]

# User: "Do you have the iPhone 15 in stock at the downtown store?"
# LLM selects: search_products → get_product_details → check_inventory
# (chains multiple tools to answer the question)
```

---

## Tool Execution

```java
// Spring AI tool execution
@Component
public class EcommerceTools {
    
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    
    @Tool(description = "Search products by name, category, or keyword. Returns list of matching products with IDs.")
    public List<ProductSummary> searchProducts(
        @ToolParam(description = "Search query (product name or keyword)") String query,
        @ToolParam(description = "Product category (optional): electronics, clothing, food") String category
    ) {
        return productService.search(query, category);
    }
    
    @Tool(description = "Check real-time inventory for a product at a specific store location")
    public InventoryStatus checkInventory(
        @ToolParam(description = "Product ID from search results") String productId,
        @ToolParam(description = "Store location name or ID") String storeLocation
    ) {
        return inventoryService.check(productId, storeLocation);
    }
    
    @Tool(description = "Place an order for a customer. Requires customer authentication.")
    public OrderConfirmation placeOrder(
        @ToolParam(description = "Product ID to order") String productId,
        @ToolParam(description = "Quantity to order") int quantity,
        @ToolParam(description = "Delivery address") String address
    ) {
        // Validate, process payment, create order
        return orderService.place(productId, quantity, address);
    }
}
```

---

## Tool Results

```java
// Tool returns structured data → LLM interprets it for the user
@Tool(description = "Get detailed order information")
public OrderInfo getOrderInfo(@ToolParam(description = "Order ID") String orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ToolExecutionException("Order not found: " + orderId));
    
    return new OrderInfo(
        order.getId(),
        order.getStatus().name(),
        order.getItems().stream()
            .map(i -> i.getName() + " x" + i.getQuantity())
            .toList(),
        order.getTotal(),
        order.getEstimatedDelivery()
    );
}

// LLM receives: {"orderId": "ORD-123", "status": "SHIPPED", 
//                "items": ["Widget x2", "Gadget x1"], 
//                "total": 59.99, "estimatedDelivery": "2024-03-20"}
// LLM responds: "Your order ORD-123 has shipped! It contains 2 Widgets and 
//                1 Gadget ($59.99 total). Expected delivery: March 20th."
```

---

## Error Handling

```java
@Tool(description = "Transfer money between accounts")
public TransferResult transferMoney(
    @ToolParam(description = "Source account number") String fromAccount,
    @ToolParam(description = "Destination account number") String toAccount,
    @ToolParam(description = "Amount to transfer") double amount
) {
    try {
        // Validation
        if (amount <= 0) {
            return TransferResult.error("Amount must be positive");
        }
        if (amount > 10000) {
            return TransferResult.error("Transfers over $10,000 require manual approval");
        }
        
        // Execute
        Transaction tx = bankService.transfer(fromAccount, toAccount, amount);
        return TransferResult.success(tx.getId(), tx.getTimestamp());
        
    } catch (InsufficientFundsException e) {
        return TransferResult.error("Insufficient funds. Available balance: " + e.getBalance());
    } catch (AccountNotFoundException e) {
        return TransferResult.error("Account not found: " + e.getAccountNumber());
    } catch (Exception e) {
        log.error("Transfer failed", e);
        return TransferResult.error("Transfer failed. Please try again later.");
    }
}

// Return errors as data (not exceptions) so LLM can communicate them naturally
// LLM: "I wasn't able to complete that transfer — there aren't enough funds 
//        in the source account. Your current balance is $450.00."
```

---

## Tool Security

```java
@Component
public class SecureTools {
    
    private final AuthenticationContext authContext;
    
    @Tool(description = "Access customer's personal information")
    public CustomerInfo getMyInfo() {
        // Tool only accesses authenticated user's data
        String userId = authContext.getCurrentUserId();
        return customerService.getInfo(userId);
    }
    
    @Tool(description = "Delete a document (irreversible)")
    public DeletionResult deleteDocument(
        @ToolParam(description = "Document ID") String docId
    ) {
        // Authorization check
        String userId = authContext.getCurrentUserId();
        if (!documentService.isOwner(docId, userId)) {
            return DeletionResult.denied("You don't have permission to delete this document.");
        }
        
        // Confirmation check (require explicit confirmation)
        // In production: implement human-in-the-loop for destructive actions
        return DeletionResult.requiresConfirmation(docId);
    }
}

// Security principles for tools:
// 1. Least privilege: only expose necessary operations
// 2. Authentication: verify who is calling
// 3. Authorization: verify what they can access
// 4. Input validation: sanitize all parameters
// 5. Rate limiting: prevent abuse
// 6. Audit logging: track all tool executions
// 7. Confirmation: require explicit approval for destructive actions
```

---

## Parallel Tool Calling

```python
# LLM can request multiple tools simultaneously
response = client.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": "Compare weather in London, Paris, and Tokyo"}],
    tools=tools,
    parallel_tool_calls=True
)

# LLM returns 3 tool calls at once:
# tool_calls = [
#   get_weather(location="London"),
#   get_weather(location="Paris"),
#   get_weather(location="Tokyo")
# ]

# Execute all in parallel
import asyncio

async def execute_parallel(tool_calls):
    tasks = [execute_tool(tc) for tc in tool_calls]
    return await asyncio.gather(*tasks)
```

---

## Real-World Example: Customer Support Agent

```java
@Component
public class SupportAgentTools {
    
    @Tool(description = "Search the knowledge base for help articles related to a topic")
    public List<Article> searchKnowledgeBase(@ToolParam(description = "Search query") String query) {
        return knowledgeBaseService.search(query, 5);
    }
    
    @Tool(description = "Look up customer account details by email or customer ID")
    public CustomerProfile getCustomer(@ToolParam(description = "Email or customer ID") String identifier) {
        return customerService.lookup(identifier);
    }
    
    @Tool(description = "Get all orders for a customer, sorted by most recent")
    public List<OrderSummary> getCustomerOrders(@ToolParam(description = "Customer ID") String customerId) {
        return orderService.getByCustomer(customerId);
    }
    
    @Tool(description = "Initiate a return/refund process for an order")
    public ReturnResult initiateReturn(
        @ToolParam(description = "Order ID") String orderId,
        @ToolParam(description = "Reason for return") String reason,
        @ToolParam(description = "Items to return (product IDs)") List<String> itemIds
    ) {
        return returnService.initiate(orderId, reason, itemIds);
    }
    
    @Tool(description = "Create a support ticket for issues requiring human review")
    public TicketResult escalateToHuman(
        @ToolParam(description = "Customer ID") String customerId,
        @ToolParam(description = "Issue summary") String summary,
        @ToolParam(description = "Priority: LOW, MEDIUM, HIGH, URGENT") String priority
    ) {
        return ticketService.create(customerId, summary, Priority.valueOf(priority));
    }
    
    @Tool(description = "Send an email to the customer")
    public EmailResult sendEmail(
        @ToolParam(description = "Customer email address") String to,
        @ToolParam(description = "Email subject") String subject,
        @ToolParam(description = "Email body (plain text)") String body
    ) {
        return emailService.send(to, subject, body);
    }
}
```

---

## Interview Questions

**Q: How does tool calling differ from RAG?**
Tool calling lets the LLM take actions (call APIs, modify data, trigger workflows). RAG retrieves static documents as context. Tool calling is for real-time data and actions; RAG is for knowledge retrieval. They're complementary — an agent might use RAG to understand policies and tools to execute actions.

**Q: How do you ensure tool security in production?**
Authenticate users before tool execution, authorize each tool call against user permissions, validate all inputs, implement rate limiting, log every execution for audit, require confirmation for destructive operations, use the principle of least privilege (minimal tools per context), and never expose admin tools in user-facing agents.

**Q: What happens when a tool call fails?**
Return error information as structured data (not exceptions) so the LLM can reason about it. The LLM might: try an alternative approach, ask the user for different information, or explain what went wrong. Always include graceful fallbacks and don't expose internal system details in error messages.

**Q: How do you decide what should be a tool vs context?**
Tools: real-time data, actions that change state, calculations, external API calls. Context (RAG): static knowledge, policies, documentation, historical data. Rule of thumb: if the information changes frequently or requires computation, it's a tool. If it's stable knowledge, it's context.

---

## Key Takeaways

1. **LLM decides WHEN to call tools** — your app EXECUTES them
2. **Tool descriptions are critical** — the LLM reads them to decide which to use
3. **Return errors as data** — let the LLM communicate failures naturally
4. **Security is essential** — authenticate, authorize, validate, audit
5. **Parallel tool calls** improve performance for independent operations
6. **Keep tool count manageable** — 10-20 tools max per interaction
7. **Confirmation for destructive actions** — human-in-the-loop when needed
