# Python Fundamentals for AI

## Overview
Python is the primary language for AI/ML development. As a Java developer, you need working Python knowledge to interact with ML libraries, build prototypes, and understand AI tooling.

---

## Python Syntax Basics

### Variables & Data Types
```python
# No type declarations needed (dynamically typed)
name = "John"          # str
age = 30               # int
salary = 75000.50      # float
is_active = True       # bool
nothing = None         # NoneType

# Type hints (optional, like annotations in Java)
name: str = "John"
age: int = 30
```

### Key Differences from Java
| Java | Python |
|------|--------|
| `int x = 5;` | `x = 5` |
| `String s = "hi";` | `s = "hi"` |
| `// comment` | `# comment` |
| `{ }` blocks | Indentation blocks |
| `null` | `None` |
| `true/false` | `True/False` |

---

## Data Structures

### Lists (like ArrayList)
```python
fruits = ["apple", "banana", "cherry"]
fruits.append("date")          # Add to end
fruits.insert(1, "avocado")    # Insert at index
fruits.remove("banana")        # Remove by value
fruits.pop(0)                  # Remove by index
fruits.sort()                  # Sort in place

# Slicing
first_two = fruits[0:2]        # ["apple", "avocado"]
last_two = fruits[-2:]         # Last 2 elements
```

### Tuples (immutable lists)
```python
point = (10, 20)
x, y = point                   # Unpacking
# point[0] = 5  → ERROR (immutable)
```

### Sets (like HashSet)
```python
unique = {1, 2, 3, 3, 4}      # {1, 2, 3, 4}
unique.add(5)
unique.discard(2)

# Set operations
a = {1, 2, 3}
b = {2, 3, 4}
a & b    # Intersection: {2, 3}
a | b    # Union: {1, 2, 3, 4}
a - b    # Difference: {1}
```

### Dictionaries (like HashMap)
```python
person = {
    "name": "John",
    "age": 30,
    "skills": ["Java", "Python"]
}

# Access
person["name"]                  # "John"
person.get("email", "N/A")     # "N/A" (default if missing)

# Iterate
for key, value in person.items():
    print(f"{key}: {value}")
```

---

## Loops & Conditions

```python
# If/elif/else
if age > 18:
    print("Adult")
elif age > 12:
    print("Teen")
else:
    print("Child")

# For loop
for i in range(5):             # 0, 1, 2, 3, 4
    print(i)

for fruit in fruits:
    print(fruit)

for i, fruit in enumerate(fruits):
    print(f"{i}: {fruit}")

# While loop
count = 0
while count < 5:
    count += 1

# Ternary
status = "adult" if age >= 18 else "minor"
```

---

## Functions

```python
def greet(name: str, greeting: str = "Hello") -> str:
    """Greets a person (this is a docstring)."""
    return f"{greeting}, {name}!"

# Call
greet("John")                   # "Hello, John!"
greet("John", "Hi")            # "Hi, John!"
greet(greeting="Hey", name="John")  # Keyword args

# *args and **kwargs
def flexible(*args, **kwargs):
    print(args)                 # Tuple of positional args
    print(kwargs)               # Dict of keyword args

flexible(1, 2, name="John")    # (1, 2) {'name': 'John'}
```

---

## Lambda Functions

```python
# Lambda = anonymous function (like Java's lambda)
square = lambda x: x ** 2
add = lambda x, y: x + y

# Common with map, filter, sorted
numbers = [3, 1, 4, 1, 5]
sorted_nums = sorted(numbers, key=lambda x: -x)  # Descending
evens = list(filter(lambda x: x % 2 == 0, numbers))
squares = list(map(lambda x: x ** 2, numbers))
```

---

## List/Dict Comprehensions

```python
# List comprehension (very Pythonic)
squares = [x**2 for x in range(10)]
evens = [x for x in range(20) if x % 2 == 0]

# Dict comprehension
word_lengths = {word: len(word) for word in ["hello", "world"]}
# {'hello': 5, 'world': 5}

# Nested comprehension
matrix = [[i*j for j in range(3)] for i in range(3)]

# Set comprehension
unique_lengths = {len(word) for word in ["hi", "hello", "hey"]}
```

---

## Exception Handling

```python
try:
    result = 10 / 0
except ZeroDivisionError as e:
    print(f"Error: {e}")
except (TypeError, ValueError) as e:
    print(f"Type/Value error: {e}")
except Exception as e:
    print(f"Unexpected: {e}")
else:
    print("No error occurred")
finally:
    print("Always runs")

# Custom exceptions
class AIModelError(Exception):
    def __init__(self, model_name: str, message: str):
        self.model_name = model_name
        super().__init__(f"{model_name}: {message}")

raise AIModelError("GPT-4", "Token limit exceeded")
```

---

## Object-Oriented Programming

```python
from abc import ABC, abstractmethod

# Abstract class (like Java interface)
class Model(ABC):
    @abstractmethod
    def predict(self, input_data):
        pass

# Concrete class
class TextClassifier(Model):
    def __init__(self, model_name: str, num_classes: int):
        self.model_name = model_name
        self.num_classes = num_classes
        self._threshold = 0.5     # Convention: "private"
    
    def predict(self, input_data):
        return {"class": "positive", "confidence": 0.92}
    
    @property
    def threshold(self):
        return self._threshold
    
    @threshold.setter
    def threshold(self, value):
        if 0 <= value <= 1:
            self._threshold = value
    
    def __str__(self):
        return f"TextClassifier({self.model_name})"

# Inheritance
class BERTClassifier(TextClassifier):
    def __init__(self, num_classes: int):
        super().__init__("BERT", num_classes)
```

---

## Modules & Packages

```python
# Importing
import os
import json
from pathlib import Path
from typing import List, Dict, Optional

# Relative imports (within a package)
from .utils import helper_function
from ..models import BaseModel

# Common AI imports
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
```

---

## pip & Virtual Environments

```bash
# Create virtual environment
python -m venv venv

# Activate (Windows)
venv\Scripts\activate

# Activate (Linux/Mac)
source venv/bin/activate

# Install packages
pip install numpy pandas scikit-learn
pip install -r requirements.txt

# Save dependencies
pip freeze > requirements.txt

# Deactivate
deactivate
```

### requirements.txt example:
```
numpy==1.24.0
pandas==2.0.0
scikit-learn==1.3.0
openai==1.0.0
langchain==0.1.0
```

---

## File Handling

```python
# Read file
with open("data.txt", "r") as f:
    content = f.read()
    # or line by line
    lines = f.readlines()

# Write file
with open("output.txt", "w") as f:
    f.write("Hello World\n")

# Read CSV (using pandas)
import pandas as pd
df = pd.read_csv("data.csv")
```

---

## JSON Handling

```python
import json

# Python dict → JSON string
data = {"name": "John", "scores": [95, 87, 92]}
json_str = json.dumps(data, indent=2)

# JSON string → Python dict
parsed = json.loads(json_str)

# File I/O
with open("config.json", "w") as f:
    json.dump(data, f, indent=2)

with open("config.json", "r") as f:
    config = json.load(f)
```

---

## REST API Calls (requests library)

```python
import requests

# GET request
response = requests.get("https://api.example.com/users")
if response.status_code == 200:
    users = response.json()

# POST request (like calling an LLM API)
headers = {
    "Authorization": "Bearer YOUR_API_KEY",
    "Content-Type": "application/json"
}
payload = {
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}]
}
response = requests.post(
    "https://api.openai.com/v1/chat/completions",
    headers=headers,
    json=payload
)
result = response.json()

# Error handling
try:
    response.raise_for_status()  # Raises for 4xx/5xx
except requests.HTTPError as e:
    print(f"API error: {e}")
```

---

## Async Basics

```python
import asyncio
import aiohttp

# Async function
async def fetch_data(url: str) -> dict:
    async with aiohttp.ClientSession() as session:
        async with session.get(url) as response:
            return await response.json()

# Run multiple requests concurrently
async def main():
    urls = ["https://api.example.com/1", "https://api.example.com/2"]
    tasks = [fetch_data(url) for url in urls]
    results = await asyncio.gather(*tasks)
    return results

# Execute
asyncio.run(main())
```

---

## Interview Questions

**Q: Why is Python preferred for AI over Java?**
Rich ML ecosystem (NumPy, PyTorch, TensorFlow, scikit-learn), faster prototyping, simpler syntax for mathematical operations, and extensive community support for AI research.

**Q: What's the difference between a list and a tuple?**
Lists are mutable (can be modified), tuples are immutable. Tuples are faster, hashable (can be dict keys), and used for fixed collections.

**Q: What is a virtual environment and why use it?**
An isolated Python environment with its own packages. Prevents dependency conflicts between projects (Project A needs numpy 1.x, Project B needs numpy 2.x).

**Q: How do list comprehensions compare to Java Streams?**
```python
# Python
evens = [x for x in numbers if x % 2 == 0]

# Java equivalent
List<Integer> evens = numbers.stream()
    .filter(x -> x % 2 == 0)
    .collect(Collectors.toList());
```

**Q: What is `*args` and `**kwargs`?**
`*args` collects positional arguments into a tuple, `**kwargs` collects keyword arguments into a dictionary. Used for flexible function signatures.

---

## Key Takeaways for Java Developers

1. **No semicolons, no braces** — indentation matters
2. **Dynamic typing** — but use type hints for clarity
3. **Everything is an object** — even functions
4. **List comprehensions** replace Streams for simple transforms
5. **`with` statement** replaces try-with-resources
6. **Dictionaries everywhere** — JSON maps directly to dicts
7. **pip + venv** is your Maven/Gradle equivalent
8. **Focus on libraries** — NumPy, pandas, requests, openai
