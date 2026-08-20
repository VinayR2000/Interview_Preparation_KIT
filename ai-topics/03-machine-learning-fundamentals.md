# Machine Learning Fundamentals

## Overview
Machine Learning is a subset of AI where systems learn patterns from data rather than being explicitly programmed. Understanding ML foundations helps you work with AI systems even if you won't build ML models from scratch.

---

## Core Concepts

### AI vs ML vs Deep Learning vs GenAI

```
┌─────────────────────────────────────────────────────┐
│  Artificial Intelligence (AI)                        │
│  Any system that mimics human intelligence           │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │  Machine Learning (ML)                       │    │
│  │  Systems that learn from data                │    │
│  │                                              │    │
│  │  ┌─────────────────────────────────────┐    │    │
│  │  │  Deep Learning (DL)                  │    │    │
│  │  │  Neural networks with many layers    │    │    │
│  │  │                                      │    │    │
│  │  │  ┌─────────────────────────────┐    │    │    │
│  │  │  │  Generative AI (GenAI)      │    │    │    │
│  │  │  │  Creates new content        │    │    │    │
│  │  │  │  (LLMs, image generators)   │    │    │    │
│  │  │  └─────────────────────────────┘    │    │    │
│  │  └─────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

| Category | Example |
|----------|---------|
| AI | Chess engine, Siri, self-driving car |
| ML | Spam filter, recommendation system |
| Deep Learning | Image recognition, speech-to-text |
| GenAI | ChatGPT, DALL-E, Copilot |

---

## Learning Types

### Supervised Learning
Learn from labeled data (input → known output).

```
Training Data:
  Email text → "spam" or "not spam"
  House features → price
  Patient symptoms → diagnosis

Model learns: input → output mapping
```

**Use cases:** Classification, regression, prediction

### Unsupervised Learning
Find patterns in unlabeled data (no known output).

```
Training Data:
  Customer purchase history (no labels)
  
Model discovers: groups/clusters of similar customers
```

**Use cases:** Clustering, anomaly detection, dimensionality reduction

### Reinforcement Learning
Learn by trial and error with rewards/penalties.

```
Agent takes action → Environment gives reward/penalty
Agent adjusts strategy to maximize reward

Example: Game AI, robotics, recommendation optimization
```

---

## Key Terminology

### Training / Validation / Testing

```
Full Dataset
├── Training Set (70-80%)    → Model learns from this
├── Validation Set (10-15%)  → Tune hyperparameters
└── Test Set (10-15%)        → Final evaluation (never seen during training)
```

```python
from sklearn.model_selection import train_test_split

X_train, X_test, y_train, y_test = train_test_split(
    features, labels, test_size=0.2, random_state=42
)
```

### Features & Labels
```python
# Features = input variables (what the model sees)
# Labels = target variable (what the model predicts)

# Example: Predicting house price
features = ["bedrooms", "sqft", "location", "age"]  # X
label = "price"                                       # y
```

### Model
A mathematical function that maps inputs to outputs, trained on data.

```
Untrained model: random predictions
Training: adjust parameters to minimize error
Trained model: accurate predictions on new data
```

---

## Algorithms

### Linear Regression
Predicts a continuous value. Finds the best-fit line.

```
y = mx + b (simple)
y = w1*x1 + w2*x2 + ... + b (multiple features)

Use case: Predict salary from years of experience
```

```python
from sklearn.linear_model import LinearRegression

model = LinearRegression()
model.fit(X_train, y_train)
predictions = model.predict(X_test)
```

### Logistic Regression
Despite the name, it's for classification (binary: yes/no).

```
Output: probability between 0 and 1
If probability > 0.5 → Class 1
If probability < 0.5 → Class 0

Use case: Email spam detection, disease diagnosis
```

### Decision Trees
Makes decisions by splitting data on feature values.

```
                Is salary > 50k?
                /            \
             Yes              No
            /                   \
    Age > 30?              Credit score > 700?
    /      \                /           \
  Yes      No            Yes            No
Approve   Review        Approve        Reject
```

### Random Forest
Ensemble of many decision trees (reduces overfitting).

```
Tree 1 → Approve
Tree 2 → Approve
Tree 3 → Reject     → Majority vote → Approve
Tree 4 → Approve
Tree 5 → Approve
```

### KNN (K-Nearest Neighbors)
Classifies based on the K closest data points.

```
New point arrives → Find K nearest neighbors → Majority class wins

K=3: 2 neighbors are "spam", 1 is "not spam" → Classify as "spam"
```

### SVM (Support Vector Machine)
Finds the hyperplane that best separates classes with maximum margin.

```
Use case: Text classification, image classification
Strength: Works well in high-dimensional spaces
```

### K-Means (Unsupervised)
Groups data into K clusters.

```
1. Place K random centroids
2. Assign each point to nearest centroid
3. Move centroids to center of their cluster
4. Repeat until stable

Use case: Customer segmentation, document grouping
```

### Naive Bayes
Probabilistic classifier using Bayes' theorem.

```
P(spam | words) = P(words | spam) * P(spam) / P(words)

Use case: Text classification, spam filtering
Fast and works well with text data
```

---

## Model Evaluation

### Classification Metrics

```
                    Predicted
                  Pos     Neg
Actual  Pos  [  TP   |   FN  ]
        Neg  [  FP   |   TN  ]
        
        Confusion Matrix
```

| Metric | Formula | Meaning |
|--------|---------|---------|
| Accuracy | (TP+TN) / Total | Overall correctness |
| Precision | TP / (TP+FP) | Of predicted positive, how many correct? |
| Recall | TP / (TP+FN) | Of actual positive, how many found? |
| F1 Score | 2 * (P*R)/(P+R) | Harmonic mean of precision & recall |

```python
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, 
    f1_score, confusion_matrix, classification_report
)

print(classification_report(y_test, predictions))
```

### When to Use Which Metric

| Scenario | Priority Metric | Why |
|----------|----------------|-----|
| Spam filter | Precision | Don't want real emails in spam |
| Disease detection | Recall | Don't want to miss sick patients |
| Balanced problem | F1 Score | Balance precision and recall |
| General | Accuracy | When classes are balanced |

### ROC-AUC
ROC curve plots True Positive Rate vs False Positive Rate at various thresholds.
- AUC = 1.0: perfect classifier
- AUC = 0.5: random guessing

---

## Overfitting vs Underfitting

### Overfitting
Model memorizes training data, fails on new data.

```
Training accuracy: 99%
Test accuracy: 60%  ← Big gap = overfitting

Causes:
- Model too complex
- Too many features
- Not enough training data
- Training too long

Solutions:
- More training data
- Simpler model
- Regularization
- Dropout (in neural networks)
- Cross-validation
```

### Underfitting
Model is too simple to capture patterns.

```
Training accuracy: 55%
Test accuracy: 52%  ← Both low = underfitting

Causes:
- Model too simple
- Not enough features
- Training too short

Solutions:
- More complex model
- More features
- Train longer
```

### Bias vs Variance Tradeoff

```
High Bias = Underfitting
  Model makes strong assumptions, misses patterns
  
High Variance = Overfitting
  Model is too sensitive to training data
  
Goal: Find the sweet spot (low bias + low variance)

       Error
        │    \  Total Error
        │     \___/
        │    /     \
        │   / Variance
        │  /
        │ Bias
        │─────────────────→ Model Complexity
        Simple            Complex
```

---

## Practical ML Pipeline

```python
# Complete ML workflow
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report

# 1. Load data
df = pd.read_csv("data.csv")

# 2. Prepare features and labels
X = df.drop("target", axis=1)
y = df["target"]

# 3. Split data
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42
)

# 4. Scale features
scaler = StandardScaler()
X_train_scaled = scaler.fit_transform(X_train)
X_test_scaled = scaler.transform(X_test)

# 5. Train model
model = RandomForestClassifier(n_estimators=100, random_state=42)
model.fit(X_train_scaled, y_train)

# 6. Evaluate
predictions = model.predict(X_test_scaled)
print(classification_report(y_test, predictions))
```

---

## Interview Questions

**Q: What's the difference between supervised and unsupervised learning?**
Supervised learning uses labeled data (known inputs→outputs) to learn mappings. Unsupervised learning finds hidden patterns in unlabeled data without predefined outputs.

**Q: How do you handle overfitting?**
More training data, regularization (L1/L2), simpler model, cross-validation, early stopping, dropout (for neural networks), and ensemble methods.

**Q: When would you use Random Forest over Logistic Regression?**
Random Forest when: non-linear relationships, complex feature interactions, missing data tolerance needed. Logistic Regression when: you need interpretability, linear decision boundary suffices, fast training needed.

**Q: What's the bias-variance tradeoff?**
High bias (underfitting) means the model is too simple. High variance (overfitting) means it's too sensitive to training data. The goal is finding optimal complexity that generalizes well to unseen data.

**Q: Explain precision vs recall with an example.**
Cancer screening: High recall means catching most cancer cases (few false negatives). High precision means when we say "cancer," we're usually right (few false positives). Medical tests prioritize recall — better to investigate a false alarm than miss a real case.

---

## Key Takeaways

1. **ML = learning patterns from data** instead of explicit programming
2. **Supervised learning** dominates production use cases
3. **Model evaluation** is as important as model building
4. **Overfitting** is the most common problem in practice
5. **You don't need to build models** — but understanding them helps you design AI systems
6. **For GenAI work**, deep knowledge of ML algorithms is "good to know" not "must master"
