# AI Evaluation ⭐⭐⭐⭐⭐

## Overview
Evaluation is how you know your AI system works correctly. Without proper evaluation, you're deploying a black box into production. This is often overlooked but critical — you can't improve what you can't measure.

---

## Why Evaluation Matters

```
Without evaluation:
  "The chatbot seems to work... mostly... I think?"
  → Can't detect regressions
  → Can't compare approaches
  → Can't justify to stakeholders

With evaluation:
  "RAG accuracy is 87%, faithfulness 94%, latency p95 = 1.2s"
  → Clear improvement targets
  → Regression detection in CI/CD
  → Data-driven decisions
```

---

## Key Metrics

### Accuracy
Does the system give the correct answer?

```python
def accuracy(predictions, ground_truth):
    correct = sum(1 for p, g in zip(predictions, ground_truth) if p == g)
    return correct / len(predictions)
```

### Relevance
Does the response address what was actually asked?

```python
RELEVANCE_PROMPT = """
Rate how relevant this response is to the question on a scale of 1-5:
1 = Completely irrelevant
3 = Partially relevant
5 = Directly answers the question

Question: {question}
Response: {response}
Score:"""
```

### Faithfulness
Does the response stick to the provided context (no hallucination)?

```python
FAITHFULNESS_PROMPT = """
Given the context and the response, determine if every claim in the 
response is supported by the context.

Context: {context}
Response: {response}

For each claim in the response:
1. Extract the claim
2. Check if it's supported by the context
3. Mark as SUPPORTED or UNSUPPORTED

Faithfulness score (0-1): number of supported claims / total claims"""
```

### Groundedness
Is every statement in the response traceable to a source?

```python
def evaluate_groundedness(response, sources):
    """Check each sentence against source documents."""
    sentences = split_into_sentences(response)
    grounded_count = 0
    
    for sentence in sentences:
        # Check if this sentence is supported by any source
        for source in sources:
            if is_supported(sentence, source):
                grounded_count += 1
                break
    
    return grounded_count / len(sentences)
```

### Hallucination Detection
Identify when the model makes up information.

```python
HALLUCINATION_PROMPT = """
Given ONLY the provided context, identify any statements in the response 
that are NOT supported by the context.

Context:
{context}

Response:
{response}

List hallucinated statements (claims not in context):
1. ...
2. ...

Hallucination rate: [number of hallucinated statements] / [total statements]"""
```

---

## Retrieval Evaluation (for RAG)

### Precision@K
Of the K documents retrieved, how many are relevant?

```python
def precision_at_k(retrieved_docs, relevant_docs, k):
    retrieved_k = retrieved_docs[:k]
    relevant_count = sum(1 for doc in retrieved_k if doc in relevant_docs)
    return relevant_count / k

# Example:
# Retrieved: [doc1✓, doc3✗, doc5✓, doc2✓, doc8✗]
# Precision@3 = 2/3 = 0.67
# Precision@5 = 3/5 = 0.60
```

### Recall@K
Of all relevant documents, how many were retrieved?

```python
def recall_at_k(retrieved_docs, relevant_docs, k):
    retrieved_k = retrieved_docs[:k]
    relevant_retrieved = sum(1 for doc in retrieved_k if doc in relevant_docs)
    return relevant_retrieved / len(relevant_docs)

# Example: 4 relevant docs exist
# Retrieved top 5: [doc1✓, doc3✗, doc5✓, doc2✓, doc8✗]
# Recall@5 = 3/4 = 0.75
```

### MRR (Mean Reciprocal Rank)
How high is the first relevant result?

```python
def mrr(queries_results):
    reciprocal_ranks = []
    for results, relevant_docs in queries_results:
        for rank, doc in enumerate(results, 1):
            if doc in relevant_docs:
                reciprocal_ranks.append(1 / rank)
                break
        else:
            reciprocal_ranks.append(0)
    return sum(reciprocal_ranks) / len(reciprocal_ranks)

# Query 1: first relevant at position 1 → 1/1 = 1.0
# Query 2: first relevant at position 3 → 1/3 = 0.33
# MRR = (1.0 + 0.33) / 2 = 0.67
```

---

## Answer Evaluation

### LLM-as-Judge
Use another LLM to evaluate response quality.

```python
def llm_judge(question, expected, actual):
    prompt = f"""
    You are an expert evaluator. Compare the actual response against the expected answer.
    
    Question: {question}
    Expected Answer: {expected}
    Actual Response: {actual}
    
    Rate on these dimensions (1-5 each):
    1. Correctness: Does it convey the same information?
    2. Completeness: Does it cover all key points?
    3. Conciseness: Is it appropriately brief without missing info?
    4. Clarity: Is it easy to understand?
    
    Return JSON:
    {{"correctness": X, "completeness": X, "conciseness": X, "clarity": X, "explanation": "..."}}
    """
    return llm.generate(prompt)
```

### Pairwise Comparison
Compare two responses side by side.

```python
def pairwise_judge(question, response_a, response_b):
    prompt = f"""
    Which response better answers the question?
    
    Question: {question}
    Response A: {response_a}
    Response B: {response_b}
    
    Winner: A, B, or TIE
    Reason:"""
    return llm.generate(prompt)
```

---

## Golden Datasets

Curated test sets with known correct answers.

```python
# Golden dataset format
golden_dataset = [
    {
        "id": "q001",
        "question": "What is our refund policy for digital products?",
        "expected_answer": "Digital products can be refunded within 14 days of purchase if unused.",
        "relevant_docs": ["policies/refund-policy.md#section-3"],
        "category": "policy",
        "difficulty": "easy"
    },
    {
        "id": "q002",
        "question": "How do I configure SSO for enterprise accounts?",
        "expected_answer": "Enterprise SSO requires: 1) SAML 2.0 configuration in admin panel, 2) IdP metadata upload, 3) Domain verification.",
        "relevant_docs": ["docs/enterprise/sso-setup.md"],
        "category": "technical",
        "difficulty": "medium"
    }
]
```

### Building Golden Datasets
```
1. Collect real user questions (from support tickets, chat logs)
2. Have domain experts write ideal answers
3. Tag relevant source documents
4. Categorize by topic and difficulty
5. Include edge cases and ambiguous questions
6. Review and update regularly

Size recommendations:
- Start with 50-100 examples
- Cover all major use cases
- Include failure cases (questions that SHOULD say "I don't know")
- Grow incrementally as you discover new patterns
```

---

## Automated Evaluation Pipeline

```java
@Service
public class RAGEvaluationService {
    
    private final RAGService ragService;
    private final ChatClient judgeModel;
    
    public EvaluationReport evaluate(List<TestCase> testCases) {
        List<EvaluationResult> results = testCases.stream()
            .map(this::evaluateCase)
            .toList();
        
        return EvaluationReport.builder()
            .totalCases(results.size())
            .averageCorrectness(results.stream().mapToDouble(r -> r.correctness).average().orElse(0))
            .averageFaithfulness(results.stream().mapToDouble(r -> r.faithfulness).average().orElse(0))
            .averageRelevance(results.stream().mapToDouble(r -> r.relevance).average().orElse(0))
            .hallucianationRate(results.stream().mapToDouble(r -> r.hallucinationScore).average().orElse(0))
            .results(results)
            .build();
    }
    
    private EvaluationResult evaluateCase(TestCase testCase) {
        // Run the RAG system
        String actualResponse = ragService.query(testCase.getQuestion());
        List<Document> retrievedDocs = ragService.retrieve(testCase.getQuestion());
        
        // Evaluate retrieval
        double retrievalPrecision = calculatePrecision(retrievedDocs, testCase.getRelevantDocs());
        
        // Evaluate generation (LLM-as-judge)
        String judgeResponse = judgeModel.prompt()
            .user(buildJudgePrompt(testCase, actualResponse, retrievedDocs))
            .call()
            .content();
        
        return parseJudgeResponse(judgeResponse, retrievalPrecision);
    }
}
```

---

## Human Evaluation

Automated evaluation has limits. Human evaluation catches what metrics miss.

```
When to use human evaluation:
- Initial system validation
- Periodic spot checks (weekly/monthly)
- Edge case assessment
- Tone and style evaluation
- Safety and bias detection

Human evaluation rubric:
┌─────────────────────────────────────────────┐
│ Dimension          │ 1 (Poor) │ 5 (Excellent)│
├────────────────────┼──────────┼──────────────┤
│ Factual accuracy   │ Wrong    │ Correct      │
│ Completeness       │ Missing  │ Thorough     │
│ Helpfulness        │ Useless  │ Actionable   │
│ Tone               │ Rude     │ Professional │
│ Safety             │ Harmful  │ Safe         │
└─────────────────────────────────────────────┘
```

---

## Regression Testing

Ensure changes don't break existing behavior.

```python
# CI/CD integration
def run_regression_tests():
    """Run on every PR that modifies AI components."""
    current_results = evaluate_rag(golden_dataset)
    baseline_results = load_baseline_metrics()
    
    # Check for regressions
    regressions = []
    for metric, current in current_results.items():
        baseline = baseline_results[metric]
        if current < baseline - TOLERANCE:
            regressions.append(f"{metric}: {baseline:.3f} → {current:.3f}")
    
    if regressions:
        raise RegressionError(f"Metrics regressed:\n" + "\n".join(regressions))
    
    # Update baseline if improved
    if all(current_results[m] >= baseline_results[m] for m in current_results):
        save_new_baseline(current_results)

# Run in CI/CD pipeline
# If metrics drop below threshold → block deployment
```

---

## Evaluation Frameworks

```python
# Using ragas for RAG evaluation
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision

results = evaluate(
    dataset=test_dataset,
    metrics=[faithfulness, answer_relevancy, context_precision]
)
print(results)
# {'faithfulness': 0.92, 'answer_relevancy': 0.87, 'context_precision': 0.85}
```

---

## Interview Questions

**Q: How do you evaluate a RAG system in production?**
Multi-dimensional: Retrieval quality (precision@K, recall@K, MRR), Generation quality (faithfulness, relevance, hallucination rate), and End-to-end (correctness vs golden dataset). Use automated LLM-as-judge for continuous monitoring, golden datasets for regression testing, and periodic human evaluation for nuance. Track metrics over time for trend detection.

**Q: What is faithfulness and why is it important?**
Faithfulness measures whether the generated response only uses information from the provided context (retrieved documents). A faithfulness score of 0.9 means 90% of claims are supported by the context. Low faithfulness = hallucination, which destroys user trust and can be dangerous in domains like healthcare or legal.

**Q: How do you build a golden dataset?**
1. Collect real user questions from production logs. 2. Have domain experts write ideal answers. 3. Tag which documents should be retrieved. 4. Include categories and difficulty levels. 5. Add negative cases (questions that should get "I don't know"). 6. Start with 50-100 cases, grow incrementally. Review and update regularly as the system evolves.

**Q: How do you detect and prevent hallucination?**
Detection: Compare generated claims against source documents using LLM-as-judge or NLI models. Prevention: Lower temperature, strict system prompts ("only use provided context"), faithfulness-focused evaluation in CI/CD, retrieval quality improvements (better chunks, reranking), and output guardrails that flag unsupported claims.

---

## Key Takeaways

1. **You can't improve what you can't measure** — evaluation is non-negotiable
2. **Golden datasets** are your most valuable testing asset — invest in building them
3. **LLM-as-judge** scales automated evaluation but needs human validation
4. **Faithfulness** is the most critical metric — hallucinations destroy trust
5. **Regression testing** in CI/CD prevents silent quality degradation
6. **Combine automated + human evaluation** for comprehensive coverage
7. **Track metrics over time** — trends matter more than individual scores
