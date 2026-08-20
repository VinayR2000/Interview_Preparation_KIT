# Deep Learning

## Overview
Deep Learning uses neural networks with multiple layers to learn complex patterns. It's the foundation of modern AI — LLMs, image recognition, and speech processing all rely on deep learning architectures.

---

## Neural Networks

### Neurons (Perceptron)
The basic unit of a neural network, inspired by biological neurons.

```
Inputs        Weights      Activation
x1 ──→ [w1] ──┐
x2 ──→ [w2] ──┼──→ Σ(wi*xi) + b ──→ f(z) ──→ output
x3 ──→ [w3] ──┘

Where:
  x = input values
  w = weights (learned)
  b = bias (learned)
  f = activation function
```

### Weights & Biases
```python
# Weights: determine importance of each input
# Bias: allows shifting the activation threshold

# Simple neuron calculation
import numpy as np

inputs = np.array([0.5, 0.3, 0.8])
weights = np.array([0.4, 0.6, 0.2])
bias = 0.1

# Weighted sum
z = np.dot(inputs, weights) + bias  # 0.5*0.4 + 0.3*0.6 + 0.8*0.2 + 0.1 = 0.64
```

### Activation Functions
Non-linear functions that enable the network to learn complex patterns.

```python
# ReLU (most common in hidden layers)
def relu(x):
    return max(0, x)
# Advantage: simple, fast, avoids vanishing gradient

# Sigmoid (0 to 1, used in binary classification)
def sigmoid(x):
    return 1 / (1 + np.exp(-x))

# Tanh (-1 to 1)
def tanh(x):
    return np.tanh(x)

# Softmax (output layer for multi-class classification)
def softmax(x):
    exp_x = np.exp(x - np.max(x))
    return exp_x / exp_x.sum()
```

---

## How Neural Networks Learn

### Forward Propagation
Data flows forward through the network to produce a prediction.

```
Input Layer → Hidden Layer 1 → Hidden Layer 2 → Output Layer
[0.5, 0.3]  → [0.7, 0.2, 0.9] → [0.4, 0.6]  → [0.85]

Each layer: output = activation(weights * input + bias)
```

### Loss Functions
Measure how wrong the prediction is.

```python
# Mean Squared Error (regression)
def mse(predicted, actual):
    return np.mean((predicted - actual) ** 2)

# Cross-Entropy Loss (classification)
# Used in LLMs for next-token prediction
def cross_entropy(predicted_probs, actual_class):
    return -np.log(predicted_probs[actual_class])
```

### Backpropagation
Calculate how much each weight contributed to the error, then adjust.

```
1. Forward pass: compute prediction
2. Compute loss
3. Backward pass: compute gradient of loss w.r.t. each weight
   (using chain rule of calculus)
4. Update weights in opposite direction of gradient

This is how the network "learns" — adjusting weights to reduce error
```

### Gradient Descent & Optimizers

```python
# Basic gradient descent
weights = weights - learning_rate * gradient

# Optimizers (smarter than basic gradient descent):
# - SGD: Stochastic Gradient Descent (uses random subsets)
# - Adam: Adaptive learning rates per parameter (most popular)
# - AdaGrad: Adapts learning rate based on history
# - RMSprop: Running average of gradients
```

---

## Training Hyperparameters

### Batch Size
Number of samples processed before updating weights.

```
Batch size = 1      → Stochastic (noisy, slow)
Batch size = 32     → Mini-batch (balanced, most common)
Batch size = full   → Batch (smooth, memory intensive)
```

### Epoch
One complete pass through the entire training dataset.

```
Epoch 1: Process all 10,000 samples → update weights
Epoch 2: Process all 10,000 samples → update weights
...
Epoch 100: Process all 10,000 samples → update weights

More epochs = more learning (but risk overfitting)
```

### Learning Rate
How big each weight update step is.

```
Learning rate = 0.001  → Small steps (slow but stable)
Learning rate = 0.1    → Large steps (fast but may overshoot)
Learning rate = 0.01   → Common starting point

Typical: start with 0.001, use learning rate scheduling
```

---

## Neural Network Architectures

### CNN (Convolutional Neural Network)
Specialized for spatial data (images, sometimes text).

```
Image → [Conv Layer → ReLU → Pool] × N → Flatten → Dense → Output

Key concepts:
- Filters/Kernels: detect patterns (edges, shapes, textures)
- Pooling: reduce spatial dimensions
- Feature maps: learned representations

Use cases: Image classification, object detection, OCR
```

### RNN (Recurrent Neural Network)
Processes sequential data with memory of previous inputs.

```
x1 → [RNN] → h1
              ↓
x2 → [RNN] → h2
              ↓
x3 → [RNN] → h3 → output

h = hidden state (memory from previous steps)

Problem: Vanishing gradient (forgets long-range dependencies)
```

### LSTM (Long Short-Term Memory)
Improved RNN that handles long sequences better.

```
Has gates:
- Forget gate: what to discard from memory
- Input gate: what new info to store
- Output gate: what to output

Use cases: Time series, older NLP models
Largely replaced by Transformers for text
```

### Transformers ⭐⭐⭐⭐⭐
The architecture behind all modern LLMs (GPT, BERT, Claude).

```
Key innovation: Attention mechanism
- Process all tokens in parallel (not sequentially like RNN)
- Learn which parts of input to focus on
- Scale to very long sequences

Architecture:
Input → Embedding → [Attention + Feed Forward] × N → Output

This is detailed in Topic 06 (LLM Fundamentals)
```

---

## Framework: PyTorch Basics

```python
import torch
import torch.nn as nn
import torch.optim as optim

# Define a simple neural network
class SimpleNet(nn.Module):
    def __init__(self, input_size, hidden_size, output_size):
        super().__init__()
        self.layer1 = nn.Linear(input_size, hidden_size)
        self.relu = nn.ReLU()
        self.layer2 = nn.Linear(hidden_size, output_size)
        self.sigmoid = nn.Sigmoid()
    
    def forward(self, x):
        x = self.layer1(x)
        x = self.relu(x)
        x = self.layer2(x)
        x = self.sigmoid(x)
        return x

# Create model
model = SimpleNet(input_size=10, hidden_size=64, output_size=1)

# Loss function and optimizer
criterion = nn.BCELoss()          # Binary Cross Entropy
optimizer = optim.Adam(model.parameters(), lr=0.001)

# Training loop
for epoch in range(100):
    # Forward pass
    outputs = model(X_train)
    loss = criterion(outputs, y_train)
    
    # Backward pass
    optimizer.zero_grad()          # Reset gradients
    loss.backward()                # Compute gradients
    optimizer.step()               # Update weights
    
    if epoch % 10 == 0:
        print(f"Epoch {epoch}, Loss: {loss.item():.4f}")
```

### Key PyTorch Concepts
```python
# Tensors (like NumPy arrays but with GPU support)
tensor = torch.tensor([1.0, 2.0, 3.0])
gpu_tensor = tensor.to("cuda")   # Move to GPU

# Automatic differentiation
x = torch.tensor(3.0, requires_grad=True)
y = x ** 2
y.backward()                      # Compute dy/dx
print(x.grad)                     # 6.0 (derivative of x² at x=3)

# DataLoader for batching
from torch.utils.data import DataLoader, TensorDataset

dataset = TensorDataset(X_tensor, y_tensor)
loader = DataLoader(dataset, batch_size=32, shuffle=True)

for batch_X, batch_y in loader:
    # Process each batch
    pass
```

---

## How This Connects to LLMs

```
LLMs are deep learning models that:
1. Use Transformer architecture (not CNN/RNN)
2. Have billions of parameters (weights)
3. Trained on massive text data
4. Use cross-entropy loss for next-token prediction
5. Optimized with variants of Adam optimizer
6. Require thousands of GPUs for training
7. But can run inference on single GPU/CPU

GPT-4: ~1.7 trillion parameters
LLaMA-7B: 7 billion parameters
BERT: 340 million parameters
```

---

## Interview Questions

**Q: What is backpropagation?**
Backpropagation computes the gradient of the loss function with respect to each weight using the chain rule. It flows backward from the output layer to the input layer, telling each weight how much it contributed to the error and in which direction to adjust.

**Q: Why are activation functions necessary?**
Without activation functions, a neural network is just a linear transformation (no matter how many layers). Activation functions introduce non-linearity, enabling the network to learn complex patterns like curves, edges, and abstract concepts.

**Q: What's the difference between CNN, RNN, and Transformer?**
CNN: excels at spatial patterns (images), uses convolutional filters. RNN: processes sequences one-by-one with memory, but struggles with long sequences. Transformer: processes all positions in parallel using attention, scales to long sequences — dominates NLP and is the basis of all modern LLMs.

**Q: Why did Transformers replace RNNs for NLP?**
Parallelization (faster training), better long-range dependency handling via attention, and more scalable to large datasets and model sizes. RNNs process sequentially (slow) and suffer from vanishing gradients.

**Q: What is the vanishing gradient problem?**
In deep networks, gradients can become extremely small as they propagate backward through many layers. This means early layers learn very slowly or stop learning entirely. Solutions: ReLU activation, residual connections, LSTM gates, and Transformer architecture.

---

## Key Takeaways

1. **Neural networks learn by adjusting weights** to minimize error via backpropagation
2. **Transformers are the key architecture** — understand attention mechanism
3. **You don't need to train models from scratch** — use pre-trained models
4. **PyTorch** is the dominant framework for research and production
5. **LLMs are just very large, very deep Transformer networks**
6. **Understanding the concepts** helps with fine-tuning, evaluation, and system design
