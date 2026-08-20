# AWS for AI

## Overview
AWS provides managed AI services through Bedrock and supporting infrastructure. Focus on the services that matter for building production AI applications — model access, storage, compute, and security.

---

## AWS Bedrock ⭐

Managed service for accessing foundation models (LLMs) via API.

```java
// Spring AI with AWS Bedrock
// application.yml
spring:
  ai:
    bedrock:
      aws:
        region: us-east-1
      anthropic:
        chat:
          enabled: true
          model: anthropic.claude-3-5-sonnet-20241022-v2:0
          options:
            temperature: 0.7
            max-tokens: 4096
      titan:
        embedding:
          enabled: true
          model: amazon.titan-embed-text-v2:0

// Usage - same Spring AI abstractions
@Service
public class BedrockChatService {
    
    private final ChatClient chatClient;
    
    public String chat(String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
```

### Available Models on Bedrock
```
Anthropic Claude 3.5 Sonnet/Haiku  — Best for code & reasoning
Amazon Titan                        — Cost-effective, embeddings
Meta Llama 3                        — Open source, good general
Cohere                              — Embeddings & reranking
Mistral                             — Fast, efficient

Benefits of Bedrock:
- No API key management (uses IAM)
- Data stays in your AWS account
- Pay per token (no provisioning)
- Private model endpoints
- VPC connectivity
- CloudWatch integration
```

---

## S3 (Document Storage)

```java
@Service
public class DocumentStorageService {
    
    private final S3Client s3Client;
    private final String bucketName = "ai-documents";
    
    // Store documents for RAG ingestion
    public String uploadDocument(MultipartFile file, Map<String, String> metadata) {
        String key = "documents/" + UUID.randomUUID() + "/" + file.getOriginalFilename();
        
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .metadata(metadata)
            .serverSideEncryption(ServerSideEncryption.AWS_KMS)
            .build();
        
        s3Client.putObject(request, RequestBody.fromInputStream(
            file.getInputStream(), file.getSize()));
        
        return key;
    }
    
    // Read document for processing
    public InputStream getDocument(String key) {
        return s3Client.getObject(GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build());
    }
}
```

---

## Lambda (Serverless AI Processing)

```java
// Lambda for async document processing
public class DocumentProcessorLambda implements RequestHandler<S3Event, String> {
    
    private final EmbeddingService embeddingService;
    private final VectorStoreClient vectorStoreClient;
    
    @Override
    public String handleRequest(S3Event event, Context context) {
        for (S3EventNotification.S3Entity s3Entity : event.getRecords()) {
            String bucket = s3Entity.getS3().getBucket().getName();
            String key = s3Entity.getS3().getObject().getKey();
            
            // 1. Download and extract text
            String text = extractText(bucket, key);
            
            // 2. Chunk
            List<String> chunks = chunk(text);
            
            // 3. Embed and store
            for (String chunk : chunks) {
                float[] embedding = embeddingService.embed(chunk);
                vectorStoreClient.store(chunk, embedding, Map.of("source", key));
            }
        }
        return "Processed " + event.getRecords().size() + " documents";
    }
}
```

---

## RDS + pgvector (Vector Database)

```yaml
# CloudFormation for RDS with pgvector
Resources:
  AIDatabase:
    Type: AWS::RDS::DBInstance
    Properties:
      DBInstanceIdentifier: ai-vector-db
      Engine: postgres
      EngineVersion: "16.1"
      DBInstanceClass: db.r6g.xlarge  # Memory optimized for vector ops
      AllocatedStorage: 100
      StorageEncrypted: true
      MasterUsername: !Ref DBUsername
      MasterUserPassword: !Ref DBPassword
      VPCSecurityGroups:
        - !Ref AISecurityGroup
      EnablePerformanceInsights: true
```

```sql
-- Initialize pgvector
CREATE EXTENSION vector;

CREATE TABLE ai_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    source VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX ON ai_documents 
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

---

## Architecture: Complete AWS AI Stack

```
┌─────────────────────────────────────────────────────────────────┐
│                         AWS Architecture                          │
│                                                                   │
│  ┌────────────┐                                                  │
│  │ CloudFront │  ← Static assets (Angular/React)                │
│  └─────┬──────┘                                                  │
│        ↓                                                         │
│  ┌────────────┐                                                  │
│  │API Gateway │  ← REST API, WebSocket for streaming            │
│  └─────┬──────┘                                                  │
│        ↓                                                         │
│  ┌────────────────────────────────────────────┐                  │
│  │         ECS / EKS (Spring Boot)            │                  │
│  │                                            │                  │
│  │  ┌─────────────────────────────────────┐   │                  │
│  │  │          Spring AI                   │   │                  │
│  │  │  ChatClient → AWS Bedrock (Claude)  │   │                  │
│  │  │  EmbeddingModel → Titan Embeddings  │   │                  │
│  │  │  VectorStore → RDS pgvector         │   │                  │
│  │  └─────────────────────────────────────┘   │                  │
│  └────────────────────────────────────────────┘                  │
│        ↓              ↓              ↓                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐                  │
│  │   RDS    │  │   S3     │  │ AWS Bedrock  │                  │
│  │pgvector  │  │  (docs)  │  │   (LLMs)    │                  │
│  └──────────┘  └──────────┘  └──────────────┘                  │
│        ↓                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐                  │
│  │  SQS     │  │ Secrets  │  │ CloudWatch   │                  │
│  │(queues)  │  │ Manager  │  │(monitoring)  │                  │
│  └──────────┘  └──────────┘  └──────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## IAM & Security

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "bedrock:InvokeModel",
                "bedrock:InvokeModelWithResponseStream"
            ],
            "Resource": [
                "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-3-5-sonnet*",
                "arn:aws:bedrock:us-east-1::foundation-model/amazon.titan-embed*"
            ]
        },
        {
            "Effect": "Allow",
            "Action": ["s3:GetObject", "s3:PutObject"],
            "Resource": "arn:aws:s3:::ai-documents/*"
        },
        {
            "Effect": "Allow",
            "Action": ["secretsmanager:GetSecretValue"],
            "Resource": "arn:aws:secretsmanager:us-east-1:*:secret:ai-service/*"
        }
    ]
}
```

---

## Secrets Manager

```java
@Configuration
public class SecretsConfig {
    
    // Spring Cloud AWS auto-loads secrets
    // application.yml:
    // spring.config.import: aws-secretsmanager:/ai-service/config
    
    @Value("${database.password}")
    private String dbPassword;  // Loaded from Secrets Manager
}
```

---

## SQS (Alternative to Kafka for AI Queues)

```java
@Service
public class SQSAIProcessor {
    
    @SqsListener("ai-processing-queue")
    public void processMessage(AIRequest request) {
        String response = chatClient.prompt()
            .user(request.getQuestion())
            .call()
            .content();
        
        // Store response
        responseStore.save(request.getCorrelationId(), response);
    }
}
```

---

## Cost Optimization

```
AWS Bedrock Pricing (approximate):
  Claude 3.5 Sonnet: $3/M input tokens, $15/M output tokens
  Titan Embeddings: $0.02/M tokens
  
Cost optimization strategies:
1. Use cheaper models for simple tasks (Haiku for classification)
2. Cache embeddings (don't re-embed unchanged documents)
3. Use S3 Intelligent-Tiering for document storage
4. Right-size ECS/EKS instances
5. Use Spot instances for batch processing
6. Set budget alerts in AWS Cost Explorer
7. Monitor per-model spend with tags
```

---

## Interview Questions

**Q: Why choose AWS Bedrock over direct OpenAI API?**
Security (data stays in your AWS account, no external API calls), IAM-based authentication (no API keys to manage), VPC integration (private endpoints), compliance (SOC2, HIPAA), multi-model access (switch models without code changes), and integration with AWS services (CloudWatch, S3, Lambda).

**Q: How would you architect a production RAG system on AWS?**
S3 for document storage → Lambda/ECS for ingestion pipeline → Bedrock Titan for embeddings → RDS pgvector for vector storage → ECS/EKS running Spring Boot + Spring AI → Bedrock Claude for generation → API Gateway for frontend access → CloudWatch for monitoring → Secrets Manager for credentials.

**Q: How do you manage costs for AI workloads on AWS?**
Monitor per-model token usage via CloudWatch, set budget alerts, use model routing (cheap models for simple tasks), cache with ElastiCache/Redis, batch embeddings, use reserved capacity for predictable workloads, Spot instances for batch processing, and implement per-user quotas.

---

## Key Takeaways

1. **AWS Bedrock** = managed LLM access with enterprise security
2. **IAM authentication** eliminates API key management
3. **Same Spring AI code** — just swap provider configuration
4. **RDS pgvector** is your production vector database on AWS
5. **Architecture mirrors standard AWS patterns** — just add Bedrock
6. **Cost control** is critical — monitor and optimize per-model usage
