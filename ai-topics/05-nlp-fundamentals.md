# NLP Fundamentals

## Overview
Natural Language Processing (NLP) is the field of AI focused on understanding and generating human language. Modern LLMs have transformed NLP, but understanding traditional techniques helps you appreciate what LLMs do and when simpler approaches suffice.

---

## Text Preprocessing

Raw text must be cleaned and structured before processing.

```python
import re

text = "Hello World! This is an NLP example... #AI @2024"

# Lowercase
text_lower = text.lower()

# Remove special characters
text_clean = re.sub(r'[^a-zA-Z\s]', '', text)

# Remove extra whitespace
text_clean = ' '.join(text_clean.split())

# Common pipeline
def preprocess(text: str) -> str:
    text = text.lower()
    text = re.sub(r'[^a-z\s]', '', text)
    text = ' '.join(text.split())
    return text
```

---

## Tokenization

Breaking text into meaningful units (tokens).

```python
# Simple whitespace tokenization
tokens = "Hello world".split()  # ["Hello", "world"]

# Word tokenization (handles punctuation)
from nltk.tokenize import word_tokenize
tokens = word_tokenize("Don't stop! It's working.")
# ["Do", "n't", "stop", "!", "It", "'s", "working", "."]

# Subword tokenization (used by LLMs like GPT)
# "unhappiness" → ["un", "happiness"] or ["un", "happ", "iness"]
# Handles unknown words by breaking into known subwords
```

### Tokenization in LLMs
```
Input: "Machine learning is powerful"

GPT tokenizer:
["Machine", " learning", " is", " powerful"]  → [22137, 4673, 318, 6505]

Key points:
- Each token maps to an ID (integer)
- Models work with token IDs, not text
- Average: 1 token ≈ 4 characters in English
- "ChatGPT" might be 1-2 tokens
- Code/non-English often uses more tokens per word
```

---

## Stop Words

Common words that carry little meaning (removed in traditional NLP).

```python
from nltk.corpus import stopwords

stop_words = set(stopwords.words('english'))
# {'the', 'is', 'at', 'which', 'on', 'a', 'an', 'and', ...}

text = "This is a very important document about AI"
filtered = [w for w in text.split() if w.lower() not in stop_words]
# ['important', 'document', 'AI']
```

**Note:** LLMs do NOT remove stop words — they process full text. Stop word removal is for traditional ML/search approaches.

---

## Stemming & Lemmatization

Reducing words to their base form.

### Stemming (crude, rule-based)
```python
from nltk.stem import PorterStemmer

stemmer = PorterStemmer()
stemmer.stem("running")     # "run"
stemmer.stem("studies")     # "studi" (not always a real word)
stemmer.stem("happiness")   # "happi"
```

### Lemmatization (linguistic, dictionary-based)
```python
from nltk.stem import WordNetLemmatizer

lemmatizer = WordNetLemmatizer()
lemmatizer.lemmatize("running", pos='v')   # "run"
lemmatizer.lemmatize("studies")            # "study"
lemmatizer.lemmatize("better", pos='a')    # "good"
```

**In practice:** LLMs handle this internally. These techniques are for search indexing, keyword extraction, and traditional ML approaches.

---

## Bag of Words (BoW)

Represents text as a count of words (ignoring order).

```python
from sklearn.feature_extraction.text import CountVectorizer

documents = [
    "I love machine learning",
    "I love deep learning",
    "Deep learning is powerful"
]

vectorizer = CountVectorizer()
bow_matrix = vectorizer.fit_transform(documents)

# Vocabulary: ['deep', 'is', 'learning', 'love', 'machine', 'powerful']
# Doc 1: [0, 0, 1, 1, 1, 0]
# Doc 2: [1, 0, 1, 1, 0, 0]
# Doc 3: [1, 1, 1, 0, 0, 1]
```

**Limitation:** Loses word order and context. "Dog bites man" = "Man bites dog" in BoW.

---

## TF-IDF (Term Frequency - Inverse Document Frequency)

Weighs words by importance: frequent in this document but rare overall = important.

```python
from sklearn.feature_extraction.text import TfidfVectorizer

documents = [
    "Machine learning is a subset of AI",
    "Deep learning is a subset of machine learning",
    "Natural language processing uses machine learning"
]

tfidf = TfidfVectorizer()
tfidf_matrix = tfidf.fit_transform(documents)

# "machine" appears in all docs → low IDF (common, less important)
# "natural" appears in 1 doc → high IDF (distinctive)
```

**Formula:**
```
TF(t,d) = count of term t in document d / total terms in d
IDF(t) = log(total documents / documents containing t)
TF-IDF = TF × IDF
```

**Use cases:** Search ranking, document similarity (before embeddings), keyword extraction.

---

## Word Embeddings

Dense vector representations of words where similar words have similar vectors.

```python
# Word2Vec: trained on word co-occurrence
# king - man + woman ≈ queen
# Paris - France + Italy ≈ Rome

# Using pre-trained embeddings
from gensim.models import KeyedVectors

model = KeyedVectors.load_word2vec_format('GoogleNews-vectors.bin', binary=True)

# Find similar words
model.most_similar("python")
# [('java', 0.85), ('programming', 0.82), ...]

# Vector arithmetic
result = model["king"] - model["man"] + model["woman"]
# Closest to "queen"
```

### Evolution of Representations
```
BoW:         "bank" → [0, 0, 1, 0, 0]     (sparse, no meaning)
Word2Vec:    "bank" → [0.2, -0.4, 0.7...]  (dense, one meaning)
BERT/GPT:    "bank" → context-dependent     (different for "river bank" vs "bank account")
```

---

## Semantic Similarity

Measuring how similar two pieces of text are in meaning.

```python
import numpy as np
from numpy.linalg import norm

def cosine_similarity(a, b):
    return np.dot(a, b) / (norm(a) * norm(b))

# With sentence embeddings
from sentence_transformers import SentenceTransformer

model = SentenceTransformer('all-MiniLM-L6-v2')

sentences = [
    "How to learn programming",
    "Best way to start coding",
    "Recipe for chocolate cake"
]

embeddings = model.encode(sentences)

# "How to learn programming" vs "Best way to start coding" → ~0.85 (similar)
# "How to learn programming" vs "Recipe for chocolate cake" → ~0.15 (different)
```

---

## Named Entity Recognition (NER)

Identifying and classifying named entities in text.

```python
import spacy

nlp = spacy.load("en_core_web_sm")
doc = nlp("Apple Inc. was founded by Steve Jobs in Cupertino, California in 1976.")

for ent in doc.ents:
    print(f"{ent.text:20} → {ent.label_}")

# Apple Inc.           → ORG
# Steve Jobs           → PERSON
# Cupertino            → GPE (geo-political entity)
# California           → GPE
# 1976                 → DATE
```

**Use cases in AI systems:**
- Extracting structured data from documents
- Building knowledge graphs
- Improving search and RAG systems
- Data anonymization (finding PII)

---

## Text Classification

Assigning categories to text.

```python
from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB

# Traditional ML approach
pipeline = Pipeline([
    ('tfidf', TfidfVectorizer()),
    ('classifier', MultinomialNB())
])

# Training data
texts = ["Great product!", "Terrible service", "Love it", "Waste of money"]
labels = ["positive", "negative", "positive", "negative"]

pipeline.fit(texts, labels)
prediction = pipeline.predict(["This is amazing!"])  # "positive"
```

### Modern Approach (with LLMs)
```python
# Zero-shot classification with an LLM
prompt = """Classify this review as positive or negative:
Review: "The food was okay but the service was slow"
Classification:"""

# LLM response: "negative" (or "mixed")
# No training data needed!
```

---

## Sentiment Analysis

Determining the emotional tone of text.

```python
# Using transformers library
from transformers import pipeline

sentiment = pipeline("sentiment-analysis")

results = sentiment([
    "I love this product!",
    "This is terrible.",
    "It's okay, nothing special."
])
# [{'label': 'POSITIVE', 'score': 0.9998},
#  {'label': 'NEGATIVE', 'score': 0.9994},
#  {'label': 'NEGATIVE', 'score': 0.6127}]  # Less confident
```

---

## Traditional NLP vs LLMs

| Aspect | Traditional NLP | LLMs |
|--------|----------------|------|
| Preprocessing | Extensive (tokenize, stem, remove stop words) | Minimal (raw text) |
| Features | Manual (BoW, TF-IDF) | Learned automatically |
| Training data | Needs labeled data | Few-shot or zero-shot |
| Context | Limited (bag of words) | Full context understanding |
| Accuracy | Good for specific tasks | Superior across tasks |
| Cost | Cheap to run | Expensive (API calls) |
| Setup | Significant engineering | API call + prompt |

### When to use Traditional NLP:
- Simple keyword matching / search
- High volume, low complexity tasks
- Cost-sensitive applications
- When you don't need deep understanding

### When to use LLMs:
- Complex understanding needed
- Multiple task types
- Few or no labeled examples
- Nuanced text generation

---

## Interview Questions

**Q: What is tokenization and why does it matter for LLMs?**
Tokenization converts text into discrete units (tokens) that models can process. LLMs use subword tokenization (BPE, WordPiece) which balances vocabulary size with coverage. Token count affects cost, context window usage, and processing speed.

**Q: What's the difference between TF-IDF and embeddings?**
TF-IDF creates sparse vectors based on word frequency statistics — captures keyword importance but not meaning. Embeddings create dense vectors that capture semantic meaning — "car" and "automobile" have similar embeddings despite being different words. TF-IDF is simpler and faster; embeddings understand language.

**Q: When would you use traditional NLP over LLMs?**
For high-volume, simple tasks where cost matters (keyword search, basic filtering), when latency is critical (sub-millisecond response), when interpretability is needed, or when the task is well-defined with good labeled data (spam detection with millions of examples).

**Q: What is the relationship between NLP and RAG?**
RAG uses NLP at multiple stages: document preprocessing (chunking, cleaning), embedding generation (semantic representation), query understanding, and retrieval ranking. Traditional NLP techniques like keyword matching can complement semantic search in hybrid approaches.

---

## Key Takeaways

1. **Traditional NLP** = manual feature engineering + ML algorithms
2. **Modern NLP** = embeddings + Transformers/LLMs
3. **LLMs replace most traditional NLP pipelines** for understanding tasks
4. **Tokenization** is the bridge between text and model
5. **Embeddings** are the foundation of semantic search and RAG
6. **Understanding traditional NLP** helps debug and optimize LLM-based systems
7. **Hybrid approaches** (TF-IDF + embeddings) often outperform either alone
