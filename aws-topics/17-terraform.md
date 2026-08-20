# Terraform + AWS ⭐⭐⭐

## Theory

Terraform is Infrastructure as Code (IaC) — define AWS infrastructure in declarative configuration files. Version-controlled, reproducible, reviewable, automated.

---

## Diagram

### Terraform Workflow

```
Developer writes .tf files
    ↓
terraform init (download providers, initialize backend)
    ↓
terraform plan (preview changes — what will be created/modified/destroyed)
    ↓
terraform apply (execute changes against AWS)
    ↓
State file updated (terraform.tfstate — tracks actual infrastructure)
```

### State Management

```
┌── terraform.tfstate ──────────────────┐
│  Records: what resources exist        │
│  Maps: config → real AWS resource IDs │
│  Used for: plan/apply/destroy         │
│                                        │
│  Storage:                              │
│  ├── Local (default, DON'T use in team)│
│  └── Remote (S3 + DynamoDB lock) ✅   │
└────────────────────────────────────────┘
```

---

## Code

### Project Structure
```
infrastructure/
├── main.tf           # Provider, backend
├── vpc.tf            # VPC, subnets, IGW, NAT
├── security.tf       # Security groups, NACLs
├── ecs.tf            # ECS cluster, services, tasks
├── rds.tf            # RDS instance
├── alb.tf            # Load balancer, target groups
├── iam.tf            # Roles, policies
├── monitoring.tf     # CloudWatch alarms
├── variables.tf      # Input variables
├── outputs.tf        # Output values
└── terraform.tfvars  # Variable values (NOT in git if secrets)
```

### Backend Configuration (Remote State)
```hcl
terraform {
  required_version = ">= 1.5"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket         = "my-terraform-state"
    key            = "production/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-locks"  # State locking
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Environment = var.environment
      ManagedBy   = "terraform"
      Project     = var.project_name
    }
  }
}
```

### Complete Spring Boot Infrastructure
```hcl
# Variables
variable "environment" { default = "production" }
variable "app_name" { default = "user-service" }
variable "aws_region" { default = "us-east-1" }

# VPC
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.0"

  name = "${var.app_name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["us-east-1a", "us-east-1b"]
  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnets = ["10.0.3.0/24", "10.0.4.0/24"]

  enable_nat_gateway = true
  single_nat_gateway = false  # One per AZ for HA
}

# ECS Cluster
resource "aws_ecs_cluster" "main" {
  name = "${var.app_name}-cluster"
  
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# ECS Service
resource "aws_ecs_service" "app" {
  name            = var.app_name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = module.vpc.private_subnets
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.app_name
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
}

# RDS
resource "aws_db_instance" "main" {
  identifier     = "${var.app_name}-db"
  engine         = "postgres"
  engine_version = "15"
  instance_class = "db.t3.medium"
  
  allocated_storage     = 50
  max_allocated_storage = 200
  storage_encrypted     = true

  db_name  = "myapp"
  username = "admin"
  password = random_password.db.result  # Generate, store in Secrets Manager
  
  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  
  backup_retention_period = 7
  skip_final_snapshot     = false
  final_snapshot_identifier = "${var.app_name}-final-snapshot"
}
```

### Modules (Reusable Components)
```hcl
# modules/ecs-service/main.tf
variable "name" {}
variable "image" {}
variable "port" { default = 8080 }
variable "cpu" { default = 512 }
variable "memory" { default = 1024 }
variable "desired_count" { default = 2 }

resource "aws_ecs_service" "this" {
  name            = var.name
  # ... configuration using variables
}

# Usage in main project:
module "user_service" {
  source = "./modules/ecs-service"
  name   = "user-service"
  image  = "123456.dkr.ecr.us-east-1.amazonaws.com/user-service:v1.2.3"
  cpu    = 512
  memory = 1024
}

module "order_service" {
  source = "./modules/ecs-service"
  name   = "order-service"
  image  = "123456.dkr.ecr.us-east-1.amazonaws.com/order-service:v2.0.1"
  cpu    = 1024
  memory = 2048
}
```

---

## Interview Questions and Answers

**Q: What is Terraform state and why is remote state important?**
> State file maps your Terraform configuration to real AWS resources (IDs, attributes). Without state, Terraform can't know what exists vs what needs changing. Remote state (S3 + DynamoDB): enables team collaboration (shared state), prevents concurrent modifications (state locking), provides versioning (S3 versioning for rollback), and ensures state isn't lost (not on a developer's laptop).

**Q: How do you manage multiple environments (dev/staging/prod) with Terraform?**
> Options: (1) Workspaces — same config, different state per workspace. (2) Separate directories — `environments/dev/`, `environments/prod/` with different variable files. (3) Terragrunt — DRY configuration across environments. Recommended: separate directories with shared modules. Each environment has its own state, variables, and can be deployed independently.

**Q: What's the difference between `terraform plan` and `terraform apply`?**
> `plan` is a dry run — shows what Terraform WOULD do without making changes. Shows creates (+), modifies (~), and destroys (-). `apply` executes the changes. Always run plan first and review. In CI/CD: plan on PR (review changes), apply on merge to main (execute).

**Q: How do you handle secrets in Terraform?**
> Never put secrets in .tf files or tfvars committed to git. Options: (1) Generate with `random_password` resource, store in Secrets Manager. (2) Use data source to read from Secrets Manager. (3) Pass via environment variables (TF_VAR_db_password). (4) Use Vault provider. The Terraform state file itself contains secrets — encrypt it (S3 backend with encryption).

---

## Best Practices

1. **Remote state** — S3 + DynamoDB locking (never local state in teams)
2. **Modules** — reusable components for services, VPCs, databases
3. **Pin versions** — provider and module versions locked
4. **Plan before apply** — always review changes
5. **Small, focused changes** — avoid massive terraform applies
6. **State file is sensitive** — encrypt, restrict access
7. **Tag everything** — use default_tags in provider
8. **CI/CD integration** — plan on PR, apply on merge
9. **`prevent_destroy`** — on critical resources (RDS, S3 with data)
10. **Import existing resources** — `terraform import` for brownfield adoption

---

## Related Topics
- → [03. VPC and Networking](./03-vpc-networking.md)
- → [12. ECS and EKS](./12-ecs-eks.md)
- → [18. CI/CD](./18-cicd.md)
