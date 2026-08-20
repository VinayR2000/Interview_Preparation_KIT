# Azure Storage

## Theory

### Azure Storage Account
A top-level resource that provides a namespace for Azure Storage services. All storage services (Blob, Files, Queues, Tables) live under a storage account.

### Storage Services

| Service | Description | AWS Equivalent | Use Case |
|---------|-------------|----------------|----------|
| Blob Storage | Object storage | S3 | Images, videos, backups, logs |
| Azure Files | Managed file shares (SMB/NFS) | EFS | Shared file storage, lift-and-shift |
| Queue Storage | Simple message queue | SQS (basic) | Lightweight async messaging |
| Table Storage | NoSQL key-value | DynamoDB (basic) | Simple structured data |
| Managed Disks | Block storage for VMs | EBS | VM operating system and data disks |

---

## Internal Working

### Storage Account Structure

```
Storage Account: contosoappstorage
├── Blob Storage
│   ├── Container: images
│   │   ├── products/phone.jpg
│   │   ├── products/laptop.png
│   │   └── avatars/user123.jpg
│   ├── Container: backups
│   │   ├── db-backup-2024-01-15.sql
│   │   └── db-backup-2024-01-16.sql
│   └── Container: logs
│       ├── 2024/01/15/app.log
│       └── 2024/01/16/app.log
│
├── Azure Files
│   └── File Share: shared-config
│       ├── application.yml
│       └── certificates/
│
├── Queue Storage
│   └── Queue: order-processing
│       ├── Message 1: {"orderId": "123"}
│       └── Message 2: {"orderId": "456"}
│
└── Table Storage
    └── Table: AuditLog
        ├── Row: {PartitionKey: "2024-01", RowKey: "001", ...}
        └── Row: {PartitionKey: "2024-01", RowKey: "002", ...}
```

### Storage Account Types

| Type | Services | Performance |
|------|----------|-------------|
| General Purpose v2 (GPv2) | All (Blob, Files, Queue, Table) | Standard or Premium |
| BlockBlobStorage | Blob only | Premium (SSD) |
| FileStorage | Files only | Premium (SSD) |

**GPv2 is the default and recommended for most scenarios.**

---

## Blob Storage ⭐⭐⭐

### Blob Types

| Type | Description | Use Case |
|------|-------------|----------|
| Block Blob | Optimized for upload/download | Files, images, videos, logs |
| Append Blob | Optimized for append operations | Log files, audit trails |
| Page Blob | Optimized for random read/write | VM disks (managed disks use this) |

### Access Tiers ⭐⭐⭐

| Tier | Access | Storage Cost | Access Cost | Use Case |
|------|--------|-------------|-------------|----------|
| Hot | Frequent | High | Low | Active data, frequently accessed |
| Cool | Infrequent (30+ days) | Medium | Medium | Backups, older data |
| Cold | Rare (90+ days) | Low | High | Compliance archives |
| Archive | Rarely (180+ days) | Very Low | Very High + rehydration time | Long-term retention |

```
Lifecycle Management Policy:
├── Rule 1: Move blobs to Cool tier after 30 days
├── Rule 2: Move blobs to Cold tier after 90 days
├── Rule 3: Move blobs to Archive tier after 365 days
└── Rule 4: Delete blobs after 7 years

Result: Automatic cost optimization without manual intervention
```

### Blob Versioning & Soft Delete
```
Blob: reports/monthly.pdf
├── Current version: v3 (latest upload)
├── Previous versions:
│   ├── v2 (2 days ago)
│   └── v1 (1 week ago)
└── Soft-deleted: recoverable for 30 days after deletion
```

---

## Replication (Redundancy) ⭐⭐⭐

| Option | Copies | Scope | Durability | Use Case |
|--------|--------|-------|------------|----------|
| LRS | 3 | Single data center | 99.999999999% (11 nines) | Dev/test, non-critical |
| ZRS | 3 | 3 Availability Zones | 99.9999999999% (12 nines) | Production, high availability |
| GRS | 6 | 2 regions (primary + secondary) | 99.99999999999999% (16 nines) | DR, compliance |
| GZRS | 6 | 3 AZs + secondary region | Highest | Mission-critical DR |

```
ZRS (Zone-Redundant Storage):
Region: East US
├── Zone 1: Copy 1 ✓
├── Zone 2: Copy 2 ✓
└── Zone 3: Copy 3 ✓

GRS (Geo-Redundant Storage):
Primary Region: East US          Secondary Region: West US
├── Copy 1                       ├── Copy 4
├── Copy 2                       ├── Copy 5
└── Copy 3                       └── Copy 6
```

---

## Security ⭐⭐⭐

### Access Methods

| Method | Description | Use Case |
|--------|-------------|----------|
| Entra ID + RBAC | Identity-based access | Applications with Managed Identity |
| Access Keys | Full account access (2 keys) | Legacy, avoid in production |
| SAS Tokens | Scoped, time-limited access | Temporary client access |
| Stored Access Policies | SAS with server-side revocation | Manageable temporary access |

### SAS (Shared Access Signature) ⭐⭐
```
SAS Token grants:
├── Permissions: Read, Write, Delete, List
├── Resource: Specific container or blob
├── Time: Start and expiry time
├── IP restriction: Only from specific IPs
└── Protocol: HTTPS only

Example URL:
https://contoso.blob.core.windows.net/images/photo.jpg?
  sv=2023-01-03&
  sr=b&
  sp=r&
  se=2024-12-31T23:59:59Z&
  sig=xxxxx
```

### Encryption
- **At rest**: All data encrypted with Microsoft-managed keys (or customer-managed via Key Vault)
- **In transit**: HTTPS enforced
- **Client-side**: Application encrypts before upload

---

## Spring Boot + Blob Storage ⭐⭐⭐

### Architecture
```
Client
    │
    ▼ (Upload file)
Spring Boot (App Service / AKS)
    │
    ├── Managed Identity
    ▼
Azure Blob Storage
    │
    ├── Container: user-uploads
    │   ├── user123/profile.jpg
    │   └── user456/resume.pdf
    │
    └── Container: generated-reports
        └── 2024/01/sales-report.pdf
```

### Java Code Example
```java
@Service
public class BlobStorageService {

    private final BlobServiceClient blobServiceClient;

    public BlobStorageService(BlobServiceClient blobServiceClient) {
        this.blobServiceClient = blobServiceClient;
    }

    public String uploadFile(String containerName, String fileName, 
                            InputStream data, long length) {
        BlobContainerClient container = 
            blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blob = container.getBlobClient(fileName);
        blob.upload(data, length, true);
        return blob.getBlobUrl();
    }

    public InputStream downloadFile(String containerName, String fileName) {
        BlobClient blob = blobServiceClient
            .getBlobContainerClient(containerName)
            .getBlobClient(fileName);
        return blob.openInputStream();
    }
}
```

### Configuration
```yaml
spring:
  cloud:
    azure:
      storage:
        blob:
          account-name: contosoappstorage
          # No credentials needed — Managed Identity handles auth
```

---

## Networking for Storage ⭐⭐

### Private Endpoint (Recommended)
```
VNet
├── App Subnet
│   └── Spring Boot App
│       │
│       ▼ (Private IP, no internet)
├── PE Subnet
│   └── Private Endpoint → Storage Account
│       └── 10.0.4.10
│
└── Private DNS Zone:
    contosoappstorage.blob.core.windows.net → 10.0.4.10

Storage Account: Public access = DISABLED
```

### Firewall Rules
- Allow specific VNets/subnets
- Allow specific IP addresses
- Deny all others

---

## Blob Storage vs Azure Files vs Managed Disks

| Feature | Blob Storage | Azure Files | Managed Disks |
|---------|-------------|-------------|---------------|
| Access | REST API / SDK | SMB / NFS mount | Attached to VM |
| Use case | App data, media, backups | Shared file systems | VM OS/data disk |
| Concurrent access | Many readers/writers via API | Multiple VMs mount same share | One VM (or read-only shared) |
| Max size | 190 TB per blob | 100 TB per share | 64 TB per disk |
| Spring Boot use | SDK for upload/download | Mount as volume | VM disk |
| AWS equivalent | S3 | EFS | EBS |

---

## Interview Questions

### Q: What is Azure Blob Storage and when would you use it?
**A:** Blob Storage is Azure's object storage for unstructured data (files, images, videos, logs, backups). Use it when:
- Storing files uploaded by users (profile pics, documents)
- Storing application-generated files (reports, exports)
- Backup and archival storage
- Static website hosting
- Log file storage

Access via REST API or Azure SDKs. Not a file system — no mounting (use Azure Files for that).

### Q: Explain Blob Storage access tiers and lifecycle management.
**A:** Azure provides Hot, Cool, Cold, and Archive tiers with decreasing storage cost but increasing access cost. Lifecycle management policies automatically transition blobs between tiers:
- Hot: Frequently accessed data (< 30 days old)
- Cool: Infrequently accessed (30-90 days)
- Cold: Rarely accessed (90-365 days)
- Archive: Long-term retention (365+ days, hours to rehydrate)

This optimizes cost automatically — recent data stays cheap to access, old data is cheap to store.

### Q: How do you secure a Storage Account in production?
**A:**
1. Disable public access, use Private Endpoints
2. Use Managed Identity + RBAC (not access keys)
3. Enforce HTTPS only
4. Enable soft delete and versioning
5. Use lifecycle policies for data retention
6. Enable Azure Defender for Storage (threat detection)
7. Disable shared key access (force Entra ID auth)
8. Enable immutability policies for compliance data
9. Use customer-managed keys in Key Vault for encryption

### Q: Blob Storage vs Azure Files — when to use which?
**A:**
- **Blob Storage**: REST API access, application-level integration (Spring Boot SDK), object storage pattern, unstructured data, lifecycle tiers, cheapest for large-scale storage
- **Azure Files**: SMB/NFS mount, shared across multiple VMs simultaneously, lift-and-shift scenarios where apps expect a file system, configuration file sharing

Rule: If your app accesses files via API → Blob. If your app expects a mounted file system → Azure Files.
