# LangChain / LlamaIndex

## Overview
LangChain and LlamaIndex are Python frameworks for building LLM applications. Learn the concepts they implement rather than becoming framework-dependent — the patterns transfer to any language/framework including Spring AI.

---

## LangChain

### Core Concepts

#### Models
```python
from langchain_openai import ChatOpenAI

# LLM wrapper
llm = ChatOpenAI(model="gpt-4", temperature=0.7)
response = llm.invoke("What is Spring Boot?")
```

#### Prompts
```python
from langchain.prompts import ChatPromptTemplate

prompt = ChatPromptTemplate.from_messages([
    ("system", "You are a {role} expert."),
    ("user", "{question}")
])

formatted = prompt.format_messages(role="Java", question="Explain generics")
```

#### Chains
Composing multiple steps into a pipeline.

```python
from langchain_core.output_parsers import StrOutputParser

# Simple chain: prompt → LLM → parse output
chain = prompt | llm | StrOutputParser()
result = chain.invoke({"role": "Java", "question": "Explain generics"})

# Multi-step chain
from langchain_core.runnables import RunnablePassthrough

rag_chain = (
    {"context": retriever, "question": RunnablePassthrough()}
    | prompt
    | llm
    | StrOutputParser()
)
```

#### Retrievers
```python
from langchain_community.vectorstores import PGVector

vectorstore = PGVector.from_documents(
    documents=chunks,
    embedding=OpenAIEmbeddings(),
    connection_string="postgresql://localhost/aidb"
)

retriever = vectorstore.as_retriever(
    search_type="similarity",
    search_kwargs={"k": 5}
)

docs = retriever.invoke("How does dependency injection work?")
```

#### Tools
```python
from langchain.tools import tool

@tool
def search_database(query: str) -> str:
    """Search the customer database for information."""
    # Your database logic here
    return f"Results for: {query}"

@tool
def send_email(to: str, subject: str, body: str) -> str:
    """Send an email to a customer."""
    # Your email logic here
    return f"Email sent to {to}"
```

#### Agents
LLM decides which tools to use and in what order.

```python
from langchain.agents import create_openai_functions_agent, AgentExecutor

tools = [search_database, send_email]

agent = create_openai_functions_agent(llm, tools, prompt)
executor = AgentExecutor(agent=agent, tools=tools, verbose=True)

result = executor.invoke({
    "input": "Find customer John's email and send him his order status"
})
# Agent: 1. Calls search_database("customer John email")
#         2. Calls send_email(to="john@...", subject="Order Status", ...)
```

#### Memory
```python
from langchain.memory import ConversationBufferMemory

memory = ConversationBufferMemory(return_messages=True)

# Automatically tracks conversation history
chain_with_memory = ConversationChain(llm=llm, memory=memory)
chain_with_memory.invoke({"input": "My name is John"})
chain_with_memory.invoke({"input": "What is my name?"})  # "Your name is John"
```

---

## LlamaIndex

### Core Concepts

#### Documents & Nodes
```python
from llama_index.core import Document, VectorStoreIndex

# Documents: raw input
documents = [Document(text="Spring Boot simplifies...")]

# Nodes: processed chunks
from llama_index.core.node_parser import SentenceSplitter
parser = SentenceSplitter(chunk_size=1024, chunk_overlap=200)
nodes = parser.get_nodes_from_documents(documents)
```

#### Indexes
```python
# VectorStoreIndex: default, semantic search
index = VectorStoreIndex.from_documents(documents)

# With custom vector store
from llama_index.vector_stores.postgres import PGVectorStore
vector_store = PGVectorStore.from_params(
    database="aidb", host="localhost",
    table_name="documents", embed_dim=1536
)
index = VectorStoreIndex.from_vector_store(vector_store)
```

#### Retrievers
```python
retriever = index.as_retriever(similarity_top_k=5)
nodes = retriever.retrieve("How to configure Spring Security?")
```

#### Query Engines
```python
# Simple query engine (RAG)
query_engine = index.as_query_engine(
    similarity_top_k=5,
    response_mode="compact"
)
response = query_engine.query("What is dependency injection?")
print(response.response)
print(response.source_nodes)  # Retrieved chunks
```

#### Agents
```python
from llama_index.core.tools import FunctionTool

def get_order_status(order_id: str) -> str:
    """Get the status of an order by ID."""
    return f"Order {order_id}: Shipped"

tool = FunctionTool.from_defaults(fn=get_order_status)

from llama_index.core.agent import ReActAgent
agent = ReActAgent.from_tools([tool], llm=llm, verbose=True)
response = agent.chat("What's the status of order 12345?")
```

---

## LangChain vs LlamaIndex vs Spring AI

| Aspect | LangChain | LlamaIndex | Spring AI |
|--------|-----------|------------|-----------|
| Language | Python | Python | Java |
| Focus | General LLM orchestration | Data indexing & retrieval | Enterprise AI |
| Strength | Flexibility, agent chains | RAG, document processing | Spring ecosystem |
| Production | Growing | Growing | Spring-grade |
| Learning curve | Moderate | Lower | Low (for Spring devs) |
| Use when | Python prototyping, agents | Complex document RAG | Java production apps |

---

## Key Patterns to Learn (Framework-Agnostic)

These patterns appear in all frameworks:

```
1. Chain/Pipeline:    prompt → model → parser → output
2. RAG:              load → chunk → embed → store → retrieve → generate
3. Agent:            observe → think → act → observe (loop)
4. Memory:           track conversation, summarize, retrieve
5. Tools:            define schema → LLM selects → execute → return result
6. Structured Output: define schema → parse LLM output → validate
```

---

## Interview Questions

**Q: When would you use LangChain vs Spring AI?**
LangChain for Python projects, rapid prototyping, and when leveraging Python's ML ecosystem. Spring AI for Java production applications that integrate with Spring Boot, need enterprise patterns (security, transactions, observability), and when the team is Java-focused.

**Q: What are the main concepts in LangChain?**
Models (LLM wrappers), Prompts (templates), Chains (composed pipelines), Retrievers (document search), Tools (external functions), Agents (autonomous decision-makers), and Memory (conversation state). These concepts map to patterns in any AI framework.

---

## Key Takeaways

1. **Learn the patterns, not the framework** — they transfer everywhere
2. **LangChain** = general-purpose LLM orchestration in Python
3. **LlamaIndex** = specialized for data/document pipelines
4. **Spring AI** = your production choice for Java
5. **All implement the same patterns** — RAG, agents, tools, memory
6. **Use Python frameworks for prototyping**, Java/Spring AI for production
