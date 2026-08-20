# Mathematics for AI

## Overview
You don't need advanced mathematics initially. Focus on the concepts that directly apply to understanding how AI models work — particularly embeddings, similarity, and optimization.

---

## Linear Algebra Basics

### Vectors
A vector is an ordered list of numbers representing a point or direction in space.

```python
import numpy as np

# A vector (1D array)
v = np.array([1, 2, 3])

# In AI context: word/document embeddings are vectors
embedding = np.array([0.21, -0.45, 0.73, 0.12, -0.89])
# This 5-dimensional vector represents meaning
```

**Why it matters for AI:**
- Text embeddings are vectors (768 or 1536 dimensions typically)
- Images are represented as vectors
- All AI model inputs/outputs are vectors

### Matrices
A matrix is a 2D array of numbers (rows × columns).

```python
# Matrix (2D array)
M = np.array([
    [1, 2, 3],
    [4, 5, 6],
    [7, 8, 9]
])

# In AI: batch of embeddings = matrix
batch_embeddings = np.array([
    [0.21, -0.45, 0.73],   # Document 1
    [0.15, -0.32, 0.81],   # Document 2
    [0.89, 0.12, -0.43]    # Document 3
])
# Shape: (3, 3) → 3 documents, 3 dimensions each
```

**Why it matters:**
- Model weights are stored as matrices
- Transformers use matrix multiplication extensively
- Attention scores are computed via matrix operations

### Dot Product
The dot product measures how "aligned" two vectors are.

```python
a = np.array([1, 2, 3])
b = np.array([4, 5, 6])

# Dot product: sum of element-wise multiplication
dot = np.dot(a, b)  # 1*4 + 2*5 + 3*6 = 32

# In AI: used in attention mechanism
query = np.array([0.5, 0.3, 0.8])
key = np.array([0.4, 0.6, 0.7])
attention_score = np.dot(query, key)  # How relevant is this key to the query?
```

---

## Probability Basics

### Core Concepts
```
P(A)          = Probability of event A (0 to 1)
P(A|B)        = Probability of A given B (conditional)
P(A ∩ B)      = Probability of both A and B
P(A ∪ B)      = Probability of A or B
```

**In AI context:**
- LLMs output probability distributions over tokens
- "Temperature" adjusts these probability distributions
- Classification models output class probabilities

```python
# LLM next token probabilities (simplified)
token_probs = {
    "Hello": 0.35,
    "Hi": 0.25,
    "Hey": 0.15,
    "Good": 0.10,
    "Greetings": 0.05,
    # ... remaining tokens share 0.10
}
# Model picks based on these probabilities
```

---

## Statistics

### Mean (Average)
```python
data = [85, 90, 78, 92, 88]
mean = np.mean(data)  # 86.6

# In AI: average loss over a batch
batch_losses = [0.45, 0.32, 0.67, 0.21]
avg_loss = np.mean(batch_losses)  # 0.4125
```

### Median (Middle Value)
```python
data = [1, 2, 3, 100, 200]
median = np.median(data)  # 3 (not affected by outliers)
mean = np.mean(data)      # 61.2 (skewed by outliers)
```

### Variance & Standard Deviation
Measure how spread out data is.

```python
data = [85, 90, 78, 92, 88]

variance = np.var(data)    # Average squared deviation from mean
std_dev = np.std(data)     # Square root of variance

# Low std = data points close together (consistent)
# High std = data points spread out (varied)
```

**In AI:** Used in normalization, batch normalization, and understanding model confidence.

### Probability Distributions

```python
# Normal (Gaussian) distribution
# Most natural phenomena follow this bell curve
samples = np.random.normal(mean=0, std=1, size=1000)

# In AI: model weights are often initialized from normal distribution
weights = np.random.normal(0, 0.02, size=(768, 768))

# Softmax: converts raw scores to probability distribution
def softmax(x):
    exp_x = np.exp(x - np.max(x))  # Numerical stability
    return exp_x / exp_x.sum()

logits = np.array([2.0, 1.0, 0.1])
probs = softmax(logits)  # [0.659, 0.243, 0.098] → sums to 1.0
```

---

## Derivatives & Gradients

### Derivatives (Single Variable)
A derivative tells you the rate of change — how fast a function is changing at a point.

```
f(x) = x²
f'(x) = 2x    ← derivative

At x=3: slope = 6 (function is increasing steeply)
At x=0: slope = 0 (function is at minimum)
```

### Gradients (Multiple Variables)
A gradient is a vector of partial derivatives — it points in the direction of steepest increase.

```python
# Loss function depends on multiple weights
# L(w1, w2, w3) = some error measure

# Gradient tells us:
# - Which direction to adjust each weight
# - How much to adjust each weight
gradient = np.array([0.5, -0.3, 0.1])
# w1 should decrease, w2 should increase, w3 should slightly decrease
```

**Why it matters:** This is how neural networks learn — they compute gradients to know how to adjust weights to reduce error.

---

## Optimization

### Gradient Descent
The core algorithm for training neural networks.

```
Repeat:
    1. Forward pass: compute prediction
    2. Compute loss (how wrong the prediction is)
    3. Backward pass: compute gradients
    4. Update weights: w = w - learning_rate * gradient
```

```python
# Simplified gradient descent
learning_rate = 0.01
weights = np.random.randn(10)

for epoch in range(1000):
    prediction = model(input, weights)
    loss = compute_loss(prediction, target)
    gradient = compute_gradient(loss, weights)
    weights = weights - learning_rate * gradient  # Update step
```

### Key Intuition
- **Learning rate too high** → overshoots the minimum, loss oscillates
- **Learning rate too low** → converges very slowly
- **Just right** → smooth convergence to minimum loss

---

## Cosine Similarity ⭐⭐⭐⭐⭐

The most important metric for AI/embeddings work. Measures the angle between two vectors (ignoring magnitude).

```python
from numpy.linalg import norm

def cosine_similarity(a, b):
    return np.dot(a, b) / (norm(a) * norm(b))

# Range: -1 to 1
# 1.0  = identical direction (very similar)
# 0.0  = perpendicular (unrelated)
# -1.0 = opposite direction (very dissimilar)

# Example: document similarity
doc1_embedding = np.array([0.21, -0.45, 0.73, 0.12])
doc2_embedding = np.array([0.19, -0.42, 0.71, 0.15])
doc3_embedding = np.array([-0.85, 0.33, -0.12, 0.67])

sim_1_2 = cosine_similarity(doc1_embedding, doc2_embedding)  # ~0.99 (very similar)
sim_1_3 = cosine_similarity(doc1_embedding, doc3_embedding)  # ~-0.5 (dissimilar)
```

### Cosine Similarity vs Euclidean Distance

| Metric | Measures | Range | Use Case |
|--------|----------|-------|----------|
| Cosine Similarity | Angle between vectors | -1 to 1 | Semantic similarity |
| Euclidean Distance | Straight-line distance | 0 to ∞ | Spatial proximity |

```python
# Euclidean distance
euclidean = np.linalg.norm(doc1_embedding - doc2_embedding)

# Cosine similarity preferred for text because:
# - "Java developer" (short) vs "Java developer with experience" (long)
# - Same meaning, different magnitudes
# - Cosine ignores magnitude, focuses on direction
```

---

## Practical Application: How These Connect in AI

```
User asks: "What is machine learning?"
                    ↓
        Convert to embedding vector
        [0.21, -0.45, 0.73, ...]        ← Linear Algebra (vectors)
                    ↓
        Compare with stored vectors
        using cosine similarity          ← Cosine Similarity
                    ↓
        Rank results by score
        (probability of relevance)       ← Probability/Statistics
                    ↓
        Feed to LLM which was trained
        using gradient descent           ← Optimization/Gradients
                    ↓
        LLM outputs token probabilities
        using softmax                    ← Probability Distributions
                    ↓
        Select tokens based on
        temperature (scaling logits)     ← Statistics
```

---

## Interview Questions

**Q: What is cosine similarity and why is it used in RAG?**
Cosine similarity measures the angle between two vectors, ranging from -1 to 1. It's used in RAG to find semantically similar documents because it focuses on direction (meaning) rather than magnitude (document length). Two documents about "machine learning" will have similar vector directions regardless of their length.

**Q: Why does gradient descent work for training neural networks?**
Gradient descent iteratively adjusts model weights in the direction that reduces error. The gradient tells us which direction to move each weight and by how much. Over many iterations, the model converges to weights that minimize the loss function.

**Q: What is softmax and where is it used?**
Softmax converts a vector of raw numbers (logits) into a probability distribution that sums to 1. Used in: final layer of classification models, attention mechanism in transformers, and LLM token prediction.

**Q: What's the difference between variance and standard deviation?**
Variance is the average squared deviation from the mean. Standard deviation is its square root, bringing it back to the original unit scale. In AI, used for normalization and understanding model output confidence.

---

## Key Takeaways

1. **Vectors** = how AI represents everything (text, images, audio)
2. **Cosine similarity** = how AI measures "sameness" between things
3. **Probability distributions** = how LLMs choose next tokens
4. **Gradients** = how models learn (direction to improve)
5. **You don't need to implement these** — libraries handle the math
6. **Understanding the concepts** helps you debug and optimize AI systems
