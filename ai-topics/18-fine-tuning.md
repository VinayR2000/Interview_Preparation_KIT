# Fine-Tuning

## Overview
Fine-tuning adapts a pre-trained model to behave differently — adopting a specific style, following certain patterns, or specializing in a domain. Understand when to fine-tune vs when simpler approaches suffice.

---

## When NOT to Fine-Tune (Decision Framework)

```
┌─────────────────────────────────────────────────────────┐
│  Do you need the model to know external/current data?    │
│       ↓ YES                    ↓ NO                      │
│    Use RAG                                               │
│                                                          │
│  Do you need a different behavior/tone/style?            │
│       ↓ YES                    ↓ NO                      │
│    Consider Fine-tuning                                  │
│                                                          │
│  Can you achieve it with better prompts + examples?      │
│       ↓ YES                    ↓ NO                      │
│    Use Prompting              Fine-tune                   │
└─────────────────────────────────────────────────────────┘
```

### Prompting vs RAG vs Fine-Tuning

| Need | Solution | Example |
|------|----------|---------|
| Access to company docs | RAG | "What's our refund policy?" |
| Specific output format | Prompting | "Always respond in JSON" |
| Domain expertise | RAG + Prompting | Medical Q&A with guidelines |
| Consistent personality | Fine-tuning | Brand voice across all responses |
| Different language style | Fine-tuning | Formal legal writing style |
| Reduced token usage | Fine-tuning | Eliminate lengthy system prompts |
| New task the model can't do | Fine-tuning | Classifying proprietary labels |

---

## Supervised Fine-Tuning

Training the model on input-output pairs.

```json
// Training data format (JSONL)
{"messages": [
    {"role": "system", "content": "You are a customer support agent for TechCorp."},
    {"role": "user", "content": "I can't login to my account"},
    {"role": "assistant", "content": "I'd be happy to help you with that login issue. Let me walk you through our troubleshooting steps:\n\n1. Clear your browser cache\n2. Try resetting your password at techcorp.com/reset\n3. Ensure you're using the email associated with your account\n\nIf none of these work, I can escalate this to our technical team. Would you like me to do that?"}
]}
{"messages": [
    {"role": "system", "content": "You are a customer support agent for TechCorp."},
    {"role": "user", "content": "How do I upgrade my plan?"},
    {"role": "assistant", "content": "Great question! Here's how to upgrade your TechCorp plan:\n\n1. Log in to your dashboard\n2. Navigate to Settings → Billing\n3. Click 'Change Plan'\n4. Select your desired tier\n\nThe upgrade takes effect immediately, and you'll be prorated for the current billing cycle. Any questions about specific plan features?"}
]}
```

---

## Instruction Tuning

Teaching the model to follow specific types of instructions.

```json
// Instruction-tuned examples
{"messages": [
    {"role": "user", "content": "Convert this Java class to Kotlin:\n```java\npublic class User {\n    private String name;\n    private int age;\n    // getters, setters\n}\n```"},
    {"role": "assistant", "content": "```kotlin\ndata class User(\n    val name: String,\n    val age: Int\n)\n```"}
]}
{"messages": [
    {"role": "user", "content": "Write a unit test for this service method:\n```java\npublic Order placeOrder(OrderRequest request) {\n    validate(request);\n    Order order = mapper.toEntity(request);\n    return orderRepository.save(order);\n}\n```"},
    {"role": "assistant", "content": "```java\n@Test\nvoid placeOrder_validRequest_savesAndReturnsOrder() {\n    OrderRequest request = new OrderRequest(\"item1\", 2);\n    Order expected = new Order(\"item1\", 2);\n    when(mapper.toEntity(request)).thenReturn(expected);\n    when(orderRepository.save(expected)).thenReturn(expected);\n    \n    Order result = orderService.placeOrder(request);\n    \n    assertEquals(expected, result);\n    verify(orderRepository).save(expected);\n}\n```"}
]}
```

---

## Dataset Preparation

The most critical step — quality data determines quality results.

```python
# Data preparation pipeline
import json

def prepare_training_data(raw_data: list) -> list:
    training_examples = []
    
    for item in raw_data:
        # Clean and format
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": clean_text(item["input"])},
            {"role": "assistant", "content": clean_text(item["output"])}
        ]
        
        # Validate
        if validate_example(messages):
            training_examples.append({"messages": messages})
    
    return training_examples

def validate_example(messages: list) -> bool:
    """Quality checks for training examples."""
    assistant_msg = messages[-1]["content"]
    
    # Not too short
    if len(assistant_msg) < 50:
        return False
    # Not too long
    if len(assistant_msg) > 5000:
        return False
    # Follows expected format
    if not meets_quality_standard(assistant_msg):
        return False
    return True

# Save as JSONL
with open("training_data.jsonl", "w") as f:
    for example in training_examples:
        f.write(json.dumps(example) + "\n")
```

### Dataset Guidelines
```
Size recommendations:
- Minimum: 50-100 examples (noticeable change)
- Good: 500-1000 examples (reliable behavior)
- Better: 1000-5000 examples (consistent quality)
- Diminishing returns after 10,000+ examples

Quality > Quantity:
- Every example should be high quality
- Remove contradictory examples
- Ensure consistent formatting
- Cover edge cases
- Include diverse inputs
```

---

## Training & Validation

```python
# OpenAI fine-tuning
from openai import OpenAI

client = OpenAI()

# Upload training file
training_file = client.files.create(
    file=open("training_data.jsonl", "rb"),
    purpose="fine-tune"
)

# Upload validation file
validation_file = client.files.create(
    file=open("validation_data.jsonl", "rb"),
    purpose="fine-tune"
)

# Start fine-tuning
job = client.fine_tuning.jobs.create(
    training_file=training_file.id,
    validation_file=validation_file.id,
    model="gpt-4o-mini-2024-07-18",
    hyperparameters={
        "n_epochs": 3,
        "batch_size": 4,
        "learning_rate_multiplier": 1.0
    }
)

# Monitor progress
while True:
    status = client.fine_tuning.jobs.retrieve(job.id)
    print(f"Status: {status.status}")
    if status.status in ["succeeded", "failed"]:
        break
    time.sleep(60)

# Use fine-tuned model
fine_tuned_model = status.fine_tuned_model
response = client.chat.completions.create(
    model=fine_tuned_model,
    messages=[{"role": "user", "content": "How do I upgrade my plan?"}]
)
```

---

## Parameter-Efficient Fine-Tuning (PEFT)

Fine-tune only a small subset of parameters — faster, cheaper, less data needed.

### LoRA (Low-Rank Adaptation)
```
Full fine-tuning: Update all 7B parameters → expensive, needs GPU cluster
LoRA: Update only ~0.1% of parameters → cheap, single GPU

How it works:
Original weight matrix W (large: 4096 × 4096)
LoRA adds: W' = W + A × B
Where A (4096 × 16) and B (16 × 4096) are tiny matrices

Only A and B are trained (much smaller!)
Original model stays frozen

Benefits:
- 10-100x less compute
- Can be trained on consumer GPUs
- Multiple LoRAs for different tasks (swap at inference)
- Original model integrity preserved
```

### QLoRA (Quantized LoRA)
```
Combines quantization (4-bit) with LoRA:
- Load model in 4-bit (reduces memory 4x)
- Apply LoRA adapters (trainable)
- Train on single 24GB GPU (even 70B models!)

Makes fine-tuning accessible on consumer hardware
```

---

## Model Evaluation

```python
# Evaluate fine-tuned model
def evaluate_model(model_name: str, test_data: list) -> dict:
    correct = 0
    total = len(test_data)
    
    for example in test_data:
        expected = example["messages"][-1]["content"]
        
        response = client.chat.completions.create(
            model=model_name,
            messages=example["messages"][:-1]  # Remove expected answer
        )
        actual = response.choices[0].message.content
        
        # Compare (may use LLM-as-judge for open-ended)
        if evaluate_response(expected, actual):
            correct += 1
    
    return {
        "accuracy": correct / total,
        "total_examples": total,
        "correct": correct
    }

# Compare base vs fine-tuned
base_results = evaluate_model("gpt-4o-mini", test_data)
ft_results = evaluate_model("ft:gpt-4o-mini:my-org:custom:abc123", test_data)
```

---

## Decision Matrix

```
┌──────────────────────────────────────────────────────────┐
│                                                           │
│  Need external/current knowledge?                         │
│  → RAG (retrieve docs at query time)                     │
│                                                           │
│  Need different behavior/style?                          │
│  → Fine-tuning (change model behavior)                   │
│                                                           │
│  Need simple instructions?                               │
│  → Prompting (system prompt + few-shot examples)         │
│                                                           │
│  Need all three?                                          │
│  → Fine-tuned model + RAG + good prompts                 │
│                                                           │
│  Most production apps need: Prompting + RAG               │
│  Few production apps need: Fine-tuning                    │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

## Interview Questions

**Q: When should you fine-tune vs use RAG?**
Fine-tune when you need to change model behavior (tone, style, format, or task-specific patterns). Use RAG when you need the model to access external/current knowledge. Fine-tuning changes HOW the model responds; RAG changes WHAT information it has access to. Most use cases are better served by RAG + good prompts.

**Q: What is LoRA and why is it important?**
LoRA (Low-Rank Adaptation) trains only small adapter matrices instead of all model parameters, reducing compute by 10-100x while achieving similar quality to full fine-tuning. It makes fine-tuning accessible (single GPU), preserves the original model, and allows multiple task-specific adapters that can be swapped at inference time.

**Q: How much data do you need for fine-tuning?**
Minimum 50-100 examples for noticeable change, 500-1000 for reliable behavior. Quality matters more than quantity — 200 perfect examples outperform 2000 mediocre ones. Always validate with a held-out test set. Start small, evaluate, then add more data if needed.

**Q: What are the risks of fine-tuning?**
Catastrophic forgetting (loses general capabilities), overfitting to training data, amplifying biases in training data, high cost for large models, ongoing maintenance (re-train with new data), and quality degradation if training data is inconsistent.

---

## Key Takeaways

1. **Most production apps don't need fine-tuning** — prompting + RAG usually suffices
2. **Fine-tune for behavior changes** — style, format, personality, not knowledge
3. **LoRA/QLoRA** make fine-tuning accessible on limited hardware
4. **Data quality > quantity** — curate your training data carefully
5. **Always compare** fine-tuned vs base model with good prompts
6. **RAG + prompting** is your default approach; fine-tuning is a last resort
