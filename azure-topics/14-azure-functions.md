# Azure Functions (Serverless)

## Theory

### What is Azure Functions?
Event-driven serverless compute. Write a function, attach a trigger, and Azure handles scaling, infrastructure, and execution. Equivalent to AWS Lambda.

### Core Concepts

| Concept | Description |
|---------|-------------|
| Function App | Container for one or more functions (deployment unit) |
| Function | Individual piece of code with trigger + bindings |
| Trigger | Event that starts execution (HTTP, timer, queue, etc.) |
| Input Binding | Data source read automatically before execution |
| Output Binding | Destination written to automatically after execution |
| Plan | Hosting model (Consumption, Premium, Dedicated) |

---

## Internal Working

### Function Architecture

```
Function App: fa-order-processing
├── Runtime: Java 17
├── Plan: Premium (EP1)
│
├── Function: processOrder
│   ├── Trigger: Service Bus Queue (order-queue)
│   ├── Input Binding: Cosmos DB (order details)
│   └── Output Binding: Service Bus Topic (order-events)
│
├── Function: generateReport
│   ├── Trigger: Timer (0 0 6 * * *) — Daily 6 AM
│   └── Output Binding: Blob Storage (reports container)
│
└── Function: handleWebhook
    ├── Trigger: HTTP (POST /api/webhook)
    └── Output Binding: Queue Storage (webhook-processing)
```

### Hosting Plans

| Plan | Scaling | Cold Start | Max Timeout | Use Case |
|------|---------|-----------|-------------|----------|
| Consumption | Auto (0 → 200 instances) | Yes (seconds) | 5 min (default), 10 min max | Event-driven, low traffic |
| Premium | Auto (pre-warmed) | No (always warm) | 60 min | Production, no cold start |
| Dedicated | Manual/Auto | No | Unlimited | Existing App Service Plan |

### Common Triggers

| Trigger | Description | Use Case |
|---------|-------------|----------|
| HTTP | REST API endpoint | Webhooks, simple APIs |
| Timer | CRON schedule | Scheduled jobs, reports |
| Service Bus | Queue/Topic message | Message processing |
| Blob Storage | File upload/change | File processing |
| Event Grid | Azure/custom events | Event reactions |
| Event Hubs | Stream events | Stream processing |
| Cosmos DB | Document changes | Change feed processing |
| Queue Storage | Queue message | Simple async tasks |

### Java Example — Service Bus Triggered Function

```java
public class OrderProcessor {

    @FunctionName("processOrder")
    public void run(
        @ServiceBusQueueTrigger(
            name = "message",
            queueName = "order-queue",
            connection = "ServiceBusConnection"
        ) String message,
        
        @CosmosDBInput(
            name = "orderDoc",
            databaseName = "ecommerce",
            containerName = "orders",
            id = "{orderId}",
            partitionKey = "{customerId}",
            connection = "CosmosDBConnection"
        ) String orderDocument,
        
        @ServiceBusTopicOutput(
            name = "outputMessage",
            topicName = "order-events",
            connection = "ServiceBusConnection"
        ) OutputBinding<String> outputMessage,
        
        final ExecutionContext context
    ) {
        context.getLogger().info("Processing order: " + message);
        
        // Process order logic...
        OrderEvent event = new OrderEvent("ORDER_COMPLETED", message);
        outputMessage.setValue(objectMapper.writeValueAsString(event));
    }
}
```

---

## Functions vs Container Apps vs AKS

| Factor | Functions | Container Apps | AKS |
|--------|-----------|---------------|-----|
| Unit of deployment | Single function | Container | Container + K8s manifests |
| Scaling | Per-function, instant | Per-app, fast | Per-deployment, configurable |
| Cold start | Yes (Consumption) | Yes (scale-to-zero) | No (always running) |
| Max execution time | 5-60 min | Unlimited | Unlimited |
| Use case | Event handlers, glue code | Microservices | Complex orchestration |
| Cost model | Per execution + GB-seconds | Per vCPU-second | Per node |
| Complexity | Lowest | Low | Highest |

---

## Interview Questions

### Q: When would you use Azure Functions vs Container Apps vs App Service?
**A:**
- **Azure Functions**: Short-lived, event-triggered tasks. Queue processing, file processing, webhooks, scheduled jobs, glue between services. Not for long-running or complex applications.
- **Container Apps**: Containerized microservices with auto-scaling. HTTP APIs and event-driven services that need scale-to-zero but run longer than Functions allow.
- **App Service**: Traditional web applications and APIs. Persistent, always-running, deployment slots, simple scaling.

### Q: What is cold start and how do you avoid it?
**A:** Cold start = delay when a function executes for the first time after being idle (instance needs to spin up). Avoid with:
1. Premium plan (pre-warmed instances)
2. Keep a minimum instance count > 0
3. Use timer trigger to "ping" and keep warm
4. For Java: longer cold starts due to JVM startup — Premium plan recommended

### Q: How would you use Azure Functions in a microservices architecture?
**A:** Functions complement microservices as lightweight glue:
- Process messages from Service Bus dead-letter queues
- Handle Event Grid notifications (blob uploaded → resize image)
- Scheduled data aggregation/cleanup
- Webhook receivers
- Secret rotation automation
- NOT for core business logic that requires complex state management
