# LLM Fundamentals ⭐⭐⭐⭐⭐

## Overview
This is the most important topic for AI engineering. Large Language Models (LLMs) are the core technology behind modern AI applications. Understanding how they work is essential for building effective AI systems.

---

## What is an LLM?

A Large Language Model is a deep learning model trained on massive text data to understand and generate human language.

```
Key characteristics:
- Billions of parameters (weights)
- Trained on internet-scale text data
- Based on Transformer architecture
- Primary task: predict the next token
- Emergent abilities at scale (reasoning, coding, analysis)
```

| Model | Parameters | Creator | Type |
|-------|-----------|---------|------|
| GPT-4 | ~1.7T (estimated) | OpenAI | Closed |
| Claude 3.5 | Unknown | Anthropic | Closed |
| LLaMA 3 70B | 70B | Meta | Open |
| Mistral 7B | 7B | Mistral | Open |
| Gemini Ultra | Unknown | Google | Closed |

---

## How LLMs Work — The Complete Flow

```
User Prompt: "What is Java?"
         ↓
┌─────────────────────┐
│   TOKENIZATION      │
│   "What" "is" "Java"│ → [2061, 318, 7349]
│   "?"               │ → [30]
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│   TOKEN EMBEDDINGS  │
│   Each token → dense│
│   vector (768-12288 │
│   dimensions)       │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│   POSITIONAL        │
│   ENCODING          │
│   Add position info │
│   (token order)     │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│   TRANSFORMER       │
│   LAYERS (×N)       │
│   ┌───────────────┐ │
│   │ Self-Attention│ │ ← Which tokens attend to which?
│   └───────┬───────┘ │
│   ┌───────────────┐ │
│   │ Feed Forward  │ │ ← Process attention output
│   └───────┬───────┘ │
│   ┌───────────────┐ │
│   │ Layer Norm    │ │ ← Stabilize training
│   └───────────────┘ │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│   OUTPUT LAYER      │
│   Logits → Softmax  │
│   → Probability     │
│   distribution over │
│   all tokens        │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│   TOKEN SELECTION   │
│   Based on          │
│   temperature,      │
│   top-k, top-p      │
│   Select: "Java"    │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│   AUTOREGRESSIVE    │
│   GENERATION        │
│   Append "Java" to  │
│   input, repeat     │
│   until done        │
└─────────────────────┘
          ↓
Response: "Java is a programming language..."
```

---

## Tokens & Tokenization

### What is a Token?
The fundamental unit LLMs process. Not exactly words — subword pieces.

```
"Hello, world!" → ["Hello", ",", " world", "!"]     → 4 tokens
"unhappiness"   → ["un", "happiness"]               → 2 tokens
"ChatGPT"       → ["Chat", "G", "PT"]               → 3 tokens
"こんにちは"      → ["こん", "にち", "は"]              → 3 tokens

Rules of thumb:
- 1 token ≈ 4 characters (English)
- 1 token ≈ ¾ of a word
- 100 tokens ≈ 75 words
- Code uses more tokens per line than prose
```

### Why Subword Tokenization?
```
Problem: Can't have infinite vocabulary
Solution: Break rare words into known subwords

"tokenization" → ["token", "ization"]
"unforgettable" → ["un", "forget", "table"]

BPE (Byte-Pair Encoding): Used by GPT models
  - Start with characters
  - Merge most frequent pairs iteratively
  - Builds vocabulary of common subwords
```

### Token Limits
```
Model context windows:
- GPT-4 Turbo: 128K tokens
- Claude 3.5: 200K tokens
- GPT-3.5: 16K tokens

Context window = input tokens + output tokens
If input = 3000 tokens, output limit = context_window - 3000
```

---

## Context Window

The maximum number of tokens a model can process in a single request.

```
┌──────────────────────────────────────────────────────┐
│                  Context Window (128K)                 │
│                                                        │
│  [System Prompt] [User Messages] [Assistant Messages]  │
│       2K              50K              10K              │
│                                                        │
│  Available for generation: 128K - 62K = 66K tokens    │
└──────────────────────────────────────────────────────┘
```

**Important implications:**
- Longer contexts = more expensive (billed per token)
- Information in the middle of context may be "lost" (lost in the middle problem)
- RAG helps by putting only relevant info in context

---

## Parameters & Weights

### Parameters
The learnable values in the model (weights and biases).

```
GPT-4:     ~1,700,000,000,000 parameters (1.7T)
LLaMA 70B:     70,000,000,000 parameters (70B)
LLaMA 7B:       7,000,000,000 parameters (7B)

More parameters → more knowledge capacity → better performance
But also → more compute → more cost → more latency
```

### What Parameters Store
```
Parameters encode:
- Language patterns and grammar
- World knowledge (facts, relationships)
- Reasoning patterns
- Code patterns
- Mathematical relationships
- Style and tone patterns

NOT stored:
- Current events (after training cutoff)
- Your specific data
- Real-time information
```

---

## Inference

The process of generating output from a trained model (no learning happens).

```
Training: Adjust parameters to learn (expensive, done once)
Inference: Use fixed parameters to generate (cheaper, done per request)

Inference flow:
Input text → Tokenize → Forward pass through model → Generate tokens one by one

Inference cost depends on:
- Model size (more parameters = slower)
- Input length (more tokens = more computation)
- Output length (generated autoregressively)
- Hardware (GPU vs CPU)
```

---

## Generation Parameters

### Temperature
Controls randomness/creativity of output.

```
Temperature = 0.0: Deterministic, always picks most likely token
Temperature = 0.7: Balanced (good default for most tasks)
Temperature = 1.0: More creative/varied
Temperature = 2.0: Very random, potentially incoherent

Low temp (0-0.3):  Code generation, factual Q&A, structured output
Med temp (0.5-0.7): General conversation, writing
High temp (0.8-1.2): Creative writing, brainstorming
```

```python
# How temperature works:
logits = [2.0, 1.0, 0.5]  # Raw model output

# Temperature 0.5 (more focused)
adjusted = [4.0, 2.0, 1.0]  # logits / 0.5 → sharper distribution
# softmax → [0.84, 0.11, 0.04]

# Temperature 2.0 (more random)
adjusted = [1.0, 0.5, 0.25]  # logits / 2.0 → flatter distribution
# softmax → [0.42, 0.31, 0.27]
```

### Top-K Sampling
Only consider the K most likely next tokens.

```
All tokens sorted by probability:
"the"(0.3), "a"(0.2), "Java"(0.15), "Python"(0.1), "is"(0.05), ...

Top-K = 3: Only consider {"the", "a", "Java"}
Then sample from these 3 based on their probabilities

Lower K = more focused
Higher K = more diverse
```

### Top-P (Nucleus Sampling)
Consider tokens until cumulative probability reaches P.

```
Sorted probabilities: 0.3, 0.2, 0.15, 0.1, 0.05, ...

Top-P = 0.7:
  0.3 + 0.2 + 0.15 + 0.1 = 0.75 > 0.7 ← stop here
  Consider top 4 tokens

Top-P = 0.9:
  Include more tokens (more diverse)

Top-P dynamically adjusts based on confidence:
- High confidence → fewer tokens considered
- Low confidence → more tokens considered
```

---

## Transformer Architecture ⭐⭐⭐⭐⭐

### Attention Mechanism
The key innovation that makes Transformers work.

```
"The cat sat on the mat because it was tired"

Attention answers: What does "it" refer to?
The model learns to "attend" (focus) on "cat" when processing "it"

Attention score computation:
1. Each token has Query (Q), Key (K), Value (V) vectors
2. Score = Q · K^T / √d_k  (dot product, scaled)
3. Weights = softmax(scores)
4. Output = Weights × V
```

### Self-Attention
Each token attends to all other tokens in the sequence.

```
Input: "Java is popular"

"Java" attends to: "Java"(0.5), "is"(0.1), "popular"(0.4)
"is" attends to:   "Java"(0.4), "is"(0.2), "popular"(0.4)
"popular" attends to: "Java"(0.6), "is"(0.1), "popular"(0.3)

This lets "popular" understand it refers to "Java" (high attention)
```

### Multi-Head Attention
Multiple attention "heads" capture different relationships.

```
Head 1: Captures syntactic relationships (subject-verb)
Head 2: Captures semantic relationships (synonyms)
Head 3: Captures positional relationships (nearby words)
...
Head 12: Captures long-range dependencies

Each head independently computes attention
Results are concatenated and projected

More heads = richer understanding of relationships
GPT-3: 96 heads, GPT-4: likely 128+ heads
```

### Positional Encoding
Since attention processes all tokens in parallel, we need position information.

```
Without position: "Dog bites man" = "Man bites dog" (same tokens, same meaning?!)
With position: Each token gets a position signal

Methods:
- Sinusoidal (original Transformer)
- Learned positional embeddings (GPT)
- Rotary Position Embedding/RoPE (modern, handles longer contexts)
```

---

## Encoder vs Decoder

### Encoder (Bidirectional)
Sees all tokens at once. Used for understanding.

```
Input: "The [MASK] sat on the mat"
Encoder sees all tokens, predicts: [MASK] = "cat"

Models: BERT, RoBERTa
Use cases: Classification, NER, similarity
```

### Decoder (Autoregressive)
Sees only previous tokens. Used for generation.

```
Input: "The cat"
Decoder predicts next: "sat"
Input: "The cat sat"
Decoder predicts next: "on"
...

Models: GPT-4, Claude, LLaMA
Use cases: Text generation, chat, code generation
```

### Encoder-Decoder
Encoder processes input, decoder generates output.

```
Models: T5, BART, original Transformer
Use cases: Translation, summarization

Input (encoder): "Translate to French: Hello world"
Output (decoder): "Bonjour le monde"
```

---

## GPT Architecture (Decoder-Only)

```
┌─────────────────────────────────────────────┐
│          GPT Architecture                    │
│                                              │
│  Input: "What is" → Tokens [2061, 318]      │
│          ↓                                   │
│  Token Embedding + Positional Encoding       │
│          ↓                                   │
│  ┌─── Transformer Block (×96 for GPT-3) ──┐ │
│  │  Masked Multi-Head Self-Attention       │ │
│  │  (can only attend to previous tokens)   │ │
│  │          ↓                              │ │
│  │  Add & Layer Norm                       │ │
│  │          ↓                              │ │
│  │  Feed-Forward Network                   │ │
│  │          ↓                              │ │
│  │  Add & Layer Norm                       │ │
│  └─────────────────────────────────────────┘ │
│          ↓                                   │
│  Linear Layer → Logits (vocab size)          │
│          ↓                                   │
│  Softmax → Probability Distribution          │
│          ↓                                   │
│  Sample next token: "Java"                   │
└─────────────────────────────────────────────┘

"Masked" attention: token can only see tokens before it
This is what makes it autoregressive (generates left-to-right)
```

---

## Open-Source vs Closed Models

| Aspect | Open Source | Closed |
|--------|-----------|--------|
| Examples | LLaMA, Mistral, Phi | GPT-4, Claude, Gemini |
| Weights | Downloadable | Not available |
| Hosting | Self-hosted or cloud | API only |
| Cost | Compute cost only | Per-token pricing |
| Customization | Fine-tunable | Prompt engineering only |
| Privacy | Data stays on your infra | Sent to provider |
| Performance | Good (improving fast) | Generally best |
| Maintenance | Your responsibility | Provider handles |

### When to use Open Source:
- Data privacy requirements
- Cost optimization at scale
- Need fine-tuning
- Compliance/regulatory requirements
- Offline/air-gapped environments

### When to use Closed:
- Best performance needed
- Don't want to manage infrastructure
- Getting started quickly
- Need latest capabilities

---

## Interview Questions

**Q: How does an LLM generate text?**
LLMs generate text autoregressively — one token at a time. For each step: the model processes all previous tokens through transformer layers, computes attention to understand context, produces a probability distribution over the vocabulary, and samples the next token based on temperature/top-p settings. This token is appended to the input and the process repeats until a stop condition is met.

**Q: What is the attention mechanism and why is it important?**
Attention allows each token to "look at" all other tokens and determine which are most relevant for its representation. Without attention, the model would process each token in isolation. Self-attention captures relationships (what "it" refers to, how "not" negates meaning) regardless of distance in the text. This is why Transformers handle long-range dependencies better than RNNs.

**Q: What's the difference between parameters and hyperparameters?**
Parameters are learned during training (weights, biases) — the model has billions of these. Hyperparameters are set by humans (learning rate, batch size, number of layers, temperature for inference). Parameters define what the model knows; hyperparameters define how it behaves.

**Q: Why can't LLMs access real-time information?**
LLMs have a knowledge cutoff — they only know what was in their training data. After training, parameters are frozen. They can't browse the internet or access databases unless given tools (tool calling, RAG). This is why RAG is essential for current/proprietary information.

**Q: What is the "lost in the middle" problem?**
LLMs tend to attend more strongly to information at the beginning and end of the context window. Information placed in the middle may be partially ignored. This affects RAG systems — retrieved documents should be placed strategically, not just concatenated.

**Q: How does temperature affect generation?**
Temperature scales the logits before softmax. Low temperature (→0) makes the distribution peaked (deterministic, always picks most likely token). High temperature makes it uniform (random, all tokens equally likely). Temperature 0.7 is a common default balancing coherence and creativity.

---

## Key Takeaways

1. **LLMs predict the next token** — everything else (reasoning, coding, analysis) emerges from this
2. **Attention mechanism** is the key innovation — "what should I focus on?"
3. **Context window** is your working memory limit — RAG helps overcome it
4. **Temperature/Top-P** control the creativity vs determinism tradeoff
5. **Autoregressive generation** means output is sequential (slower for long responses)
6. **Parameters encode knowledge** but are frozen after training
7. **Open vs Closed** is a tradeoff of control/privacy vs capability/convenience
