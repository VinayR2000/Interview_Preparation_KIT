# MCP — Model Context Protocol

## Overview
MCP (Model Context Protocol) is a standardized protocol for connecting AI models/agents to external tools, resources, and data sources. Think of it as a universal adapter between AI systems and the tools they need.

---

## What is MCP?

```
Problem:
  Every AI tool integration is custom:
  - Agent A uses custom API for GitHub
  - Agent B uses different custom API for GitHub
  - Each integration = custom code, maintenance, security

Solution (MCP):
  Standardized protocol — build a tool ONCE, use it with ANY AI system

  ┌───────────────┐     MCP Protocol      ┌───────────────┐
  │  AI Agent /   │ ←──────────────────→  │  MCP Server   │
  │  MCP Client   │   (standardized)       │  (tool/data   │
  │  (Claude,     │                        │   provider)   │
  │   ChatGPT,    │                        │               │
  │   Your Agent) │                        │  - GitHub     │
  └───────────────┘                        │  - Database   │
                                           │  - File System│
                                           └───────────────┘
```

---

## MCP Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MCP HOST                              │
│  (Application: IDE, Chat app, Agent framework)               │
│                                                              │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  MCP Client  │    │  MCP Client  │    │  MCP Client  │  │
│  │  (Session 1) │    │  (Session 2) │    │  (Session 3) │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘  │
└─────────┼───────────────────┼───────────────────┼───────────┘
          ↓                   ↓                   ↓
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│   MCP Server A   │ │   MCP Server B   │ │   MCP Server C   │
│   (GitHub)       │ │   (Database)     │ │   (File System)  │
│                  │ │                  │ │                  │
│  Tools:          │ │  Tools:          │ │  Tools:          │
│  - create_issue  │ │  - query_sql     │ │  - read_file     │
│  - list_repos    │ │  - list_tables   │ │  - write_file    │
│  - search_code   │ │  - describe_table│ │  - search_files  │
│                  │ │                  │ │                  │
│  Resources:      │ │  Resources:      │ │  Resources:      │
│  - repo contents │ │  - schema info   │ │  - file contents │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

---

## Core Components

### MCP Server
Exposes tools, resources, and prompts to AI clients.

```python
# Python MCP Server example
from mcp.server import Server
from mcp.types import Tool, TextContent

server = Server("my-tool-server")

@server.tool()
async def get_order_status(order_id: str) -> str:
    """Get the current status of an order by its ID."""
    order = await db.get_order(order_id)
    return f"Order {order_id}: {order.status}, shipped: {order.shipped_date}"

@server.tool()
async def search_products(query: str, category: str = None) -> str:
    """Search product catalog by name or keyword."""
    results = await product_service.search(query, category)
    return json.dumps([{"name": r.name, "price": r.price} for r in results])
```

### MCP Client
Connects to MCP servers and invokes tools on behalf of the AI model.

```python
from mcp.client import ClientSession
from mcp.client.stdio import stdio_client

async def main():
    async with stdio_client(server_params) as (read, write):
        async with ClientSession(read, write) as session:
            # Initialize
            await session.initialize()
            
            # List available tools
            tools = await session.list_tools()
            
            # Call a tool
            result = await session.call_tool(
                "get_order_status",
                arguments={"order_id": "ORD-12345"}
            )
```

### MCP Tools
Functions the AI can call through the MCP server.

```python
@server.tool()
async def create_ticket(
    customer_id: str,
    subject: str,
    description: str,
    priority: str = "medium"
) -> str:
    """Create a support ticket. Priority: low, medium, high, urgent."""
    ticket = await ticket_service.create(customer_id, subject, description, priority)
    return f"Ticket created: {ticket.id}"
```

### MCP Resources
Data/content the AI can read (like files, database schemas, documentation).

```python
@server.resource("schema://database/tables")
async def get_schema() -> str:
    """Database schema information."""
    tables = await db.get_all_tables()
    return format_schema(tables)

@server.resource("docs://api/endpoints")
async def get_api_docs() -> str:
    """API endpoint documentation."""
    return load_api_documentation()
```

### MCP Prompts
Pre-built prompt templates the server provides.

```python
@server.prompt()
async def code_review_prompt(code: str, language: str) -> str:
    """Generate a code review prompt for the given code."""
    return f"""Review this {language} code for:
    - Security vulnerabilities
    - Performance issues
    - Best practice violations
    
    Code:
    ```{language}
    {code}
    ```"""
```

---

## Transport

How MCP clients and servers communicate.

```
1. stdio (Standard I/O):
   Client spawns server as subprocess
   Communication via stdin/stdout
   Best for: local tools, IDE integrations

2. HTTP/SSE (Server-Sent Events):
   Client connects via HTTP
   Server pushes events via SSE
   Best for: remote servers, shared tools

3. WebSocket (coming):
   Bidirectional real-time communication
   Best for: interactive, stateful tools
```

---

## Authentication & Security

```python
# Server-side authentication
@server.tool()
async def access_customer_data(customer_id: str) -> str:
    """Access customer information (requires authentication)."""
    # Verify the caller's identity
    auth_context = get_auth_context()
    if not auth_context.has_permission("read:customers"):
        raise PermissionError("Insufficient permissions")
    
    # Verify the customer belongs to the caller's organization
    if not await verify_ownership(auth_context.org_id, customer_id):
        raise PermissionError("Customer not in your organization")
    
    return await customer_service.get(customer_id)
```

### Security Best Practices
```
1. Authentication: Verify who is calling
2. Authorization: Verify what they can access
3. Input validation: Sanitize all tool parameters
4. Rate limiting: Prevent abuse
5. Audit logging: Track all tool invocations
6. Transport security: Use TLS for remote connections
7. Least privilege: Expose only necessary tools
8. Secrets management: Never expose API keys through MCP
```

---

## Building an MCP Server

```python
# Complete MCP Server for a todo application
import asyncio
from mcp.server import Server
from mcp.server.stdio import stdio_server

server = Server("todo-server")

# In-memory store (replace with DB in production)
todos = {}

@server.tool()
async def add_todo(title: str, description: str = "", priority: str = "medium") -> str:
    """Add a new todo item. Priority: low, medium, high."""
    todo_id = str(len(todos) + 1)
    todos[todo_id] = {
        "id": todo_id,
        "title": title,
        "description": description,
        "priority": priority,
        "completed": False
    }
    return f"Todo created with ID: {todo_id}"

@server.tool()
async def list_todos(status: str = "all") -> str:
    """List todos. Status filter: all, pending, completed."""
    filtered = todos.values()
    if status == "pending":
        filtered = [t for t in filtered if not t["completed"]]
    elif status == "completed":
        filtered = [t for t in filtered if t["completed"]]
    return json.dumps(list(filtered), indent=2)

@server.tool()
async def complete_todo(todo_id: str) -> str:
    """Mark a todo as completed."""
    if todo_id not in todos:
        return f"Error: Todo {todo_id} not found"
    todos[todo_id]["completed"] = True
    return f"Todo {todo_id} marked as completed"

@server.resource("todos://all")
async def all_todos_resource() -> str:
    """All todo items as a resource."""
    return json.dumps(list(todos.values()))

# Run the server
async def main():
    async with stdio_server() as (read, write):
        await server.run(read, write)

asyncio.run(main())
```

---

## Connecting Agents to MCP

```java
// Spring AI with MCP (conceptual)
@Configuration
public class MCPConfiguration {
    
    @Bean
    public MCPClient mcpClient() {
        return MCPClient.builder()
            .server("order-service", MCPTransport.stdio("python", "order_mcp_server.py"))
            .server("knowledge-base", MCPTransport.http("https://kb.internal/mcp"))
            .build();
    }
    
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, MCPClient mcpClient) {
        // MCP tools are automatically available to the agent
        return builder
            .defaultTools(mcpClient.getTools())
            .build();
    }
}
```

### IDE Configuration (mcp.json)
```json
{
    "mcpServers": {
        "order-service": {
            "command": "python",
            "args": ["mcp_servers/order_server.py"],
            "env": {
                "DATABASE_URL": "${DB_URL}"
            }
        },
        "github": {
            "command": "uvx",
            "args": ["mcp-server-github"],
            "env": {
                "GITHUB_TOKEN": "${GITHUB_TOKEN}"
            }
        }
    }
}
```

---

## MCP vs Direct Tool Calling

| Aspect | Direct Tool Calling | MCP |
|--------|-------------------|-----|
| Integration | Custom per LLM provider | Standardized, universal |
| Reusability | Per application | Write once, use anywhere |
| Discovery | Hardcoded tool list | Dynamic tool discovery |
| Updates | Redeploy application | Update server independently |
| Ecosystem | Build everything | Community MCP servers |
| Complexity | Simpler for single app | Better for multi-agent/multi-tool |

---

## Interview Questions

**Q: What is MCP and why does it matter?**
MCP is a standardized protocol for connecting AI models to external tools and data. It matters because it creates interoperability — build a tool integration once and it works with any MCP-compatible AI system. This reduces duplicate integration work and enables an ecosystem of reusable tool servers.

**Q: How does MCP differ from direct function/tool calling?**
Direct tool calling ties tools to a specific application and LLM provider. MCP provides a standard interface so tools are decoupled from consumers. Any MCP client (Claude, custom agent, IDE) can connect to any MCP server. It's like HTTP for APIs — a universal protocol that enables interoperability.

**Q: What are the security considerations for MCP?**
Authentication (verify caller identity), authorization (check permissions per tool), transport security (TLS for remote), input validation, rate limiting, audit logging, and the principle of least privilege (only expose necessary tools). MCP servers should never trust client input blindly.

**Q: When would you build an MCP server vs use direct tool calling?**
Build MCP when: tools need to be shared across multiple agents/applications, you want a reusable ecosystem, tools are maintained by a separate team, or you need dynamic tool discovery. Use direct tools when: single application, simple integration, tight coupling is acceptable, or you need maximum performance.

---

## Key Takeaways

1. **MCP = standard protocol** for connecting AI to tools (like HTTP for APIs)
2. **Server exposes tools + resources + prompts** — client connects and uses them
3. **Build once, use everywhere** — any MCP client can use any MCP server
4. **Security is critical** — authenticate, authorize, validate, audit
5. **stdio for local**, HTTP/SSE for remote — choose transport based on deployment
6. **Growing ecosystem** — community MCP servers for common integrations
7. **Complements direct tool calling** — use MCP for shared/reusable tools
