# Terraform on Azure

## Theory

### Why Terraform for Azure?
Infrastructure as Code (IaC) for provisioning and managing Azure resources. Declarative, version-controlled, repeatable. You already know Terraform from AWS — the concepts are identical, only the provider and resource types change.

### Azure Provider (AzureRM)

```hcl
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80"
    }
  }
  
  backend "azurerm" {
    resource_group_name  = "rg-terraform-state"
    storage_account_name = "tfstatecontoso"
    container_name       = "tfstate"
    key                  = "production.tfstate"
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
}
```

---

## Internal Working

### Terraform State on Azure ⭐⭐⭐

```
Remote Backend: Azure Storage Account
├── Storage Account: tfstatecontoso
│   └── Container: tfstate
│       ├── production.tfstate
│       ├── staging.tfstate
│       └── dev.tfstate

Benefits:
├── Shared state (team collaboration)
├── State locking (prevents concurrent modifications)
├── Encryption at rest
└── Versioning (recover from state corruption)
```

### Authentication for Terraform

| Method | Use Case |
|--------|----------|
| Azure CLI (`az login`) | Local development |
| Service Principal (client_id + secret) | CI/CD pipelines |
| Managed Identity | Terraform running on Azure VM/Container |
| Workload Identity Federation | GitHub Actions (no secrets) |

---

## Complete Production Infrastructure ⭐⭐⭐

### Project Structure

```
terraform/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   ├── staging/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── terraform.tfvars
│   └── production/
│       ├── main.tf
│       ├── variables.tf
│       └── terraform.tfvars
├── modules/
│   ├── networking/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── aks/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   ├── database/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   └── outputs.tf
│   └── monitoring/
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
└── README.md
```

### Core Resources Example

```hcl
# Resource Group
resource "azurerm_resource_group" "main" {
  name     = "rg-${var.project}-${var.environment}"
  location = var.location
  tags     = var.tags
}

# Virtual Network
resource "azurerm_virtual_network" "main" {
  name                = "vnet-${var.project}-${var.environment}"
  address_space       = ["10.0.0.0/16"]
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
}

# Subnets
resource "azurerm_subnet" "aks" {
  name                 = "snet-aks"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.0.0/22"]
}

resource "azurerm_subnet" "db" {
  name                 = "snet-db"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.4.0/24"]
  
  delegation {
    name = "postgresql"
    service_delegation {
      name = "Microsoft.DBforPostgreSQL/flexibleServers"
    }
  }
}

resource "azurerm_subnet" "pe" {
  name                 = "snet-private-endpoints"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.8.0/24"]
}

# NSG
resource "azurerm_network_security_group" "aks" {
  name                = "nsg-aks"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  security_rule {
    name                       = "AllowHTTPS"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

# AKS Cluster
resource "azurerm_kubernetes_cluster" "main" {
  name                = "aks-${var.project}-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  dns_prefix          = "aks-${var.project}"
  kubernetes_version  = var.kubernetes_version

  default_node_pool {
    name                = "system"
    node_count          = 3
    vm_size             = "Standard_D4s_v5"
    vnet_subnet_id      = azurerm_subnet.aks.id
    zones               = [1, 2, 3]
    enable_auto_scaling = true
    min_count           = 3
    max_count           = 6
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin    = "azure"
    service_cidr      = "10.1.0.0/16"
    dns_service_ip    = "10.1.0.10"
  }

  oms_agent {
    log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  }
}

# User Node Pool
resource "azurerm_kubernetes_cluster_node_pool" "app" {
  name                  = "apppool"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.main.id
  vm_size               = "Standard_D8s_v5"
  zones                 = [1, 2, 3]
  enable_auto_scaling   = true
  min_count             = 3
  max_count             = 20
  vnet_subnet_id        = azurerm_subnet.aks.id
}

# ACR
resource "azurerm_container_registry" "main" {
  name                = "${var.project}acr${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "Premium"
  admin_enabled       = false
}

# ACR → AKS role assignment
resource "azurerm_role_assignment" "aks_acr" {
  scope                = azurerm_container_registry.main.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
}

# PostgreSQL Flexible Server
resource "azurerm_postgresql_flexible_server" "main" {
  name                   = "psql-${var.project}-${var.environment}"
  resource_group_name    = azurerm_resource_group.main.name
  location               = azurerm_resource_group.main.location
  version                = "16"
  delegated_subnet_id    = azurerm_subnet.db.id
  private_dns_zone_id    = azurerm_private_dns_zone.postgres.id
  
  administrator_login    = var.db_admin_username
  administrator_password = var.db_admin_password

  storage_mb = 65536
  sku_name   = "GP_Standard_D4s_v3"

  high_availability {
    mode                      = "ZoneRedundant"
    standby_availability_zone = "2"
  }

  backup_retention_days = 35
}

# Key Vault
resource "azurerm_key_vault" "main" {
  name                       = "kv-${var.project}-${var.environment}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  soft_delete_retention_days = 90
  purge_protection_enabled   = true
  
  enable_rbac_authorization  = true
}

# Redis
resource "azurerm_redis_cache" "main" {
  name                = "redis-${var.project}-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  capacity            = 2
  family              = "P"
  sku_name            = "Premium"
  
  redis_configuration {
    maxmemory_policy = "volatile-lru"
  }
  
  zones = [1, 2, 3]
}

# Log Analytics
resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-${var.project}-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 90
}
```

---

## Terraform Architecture Diagram

```
Terraform provisions:
│
├── Resource Group
│
├── Networking
│   ├── VNet (10.0.0.0/16)
│   ├── Subnet: AKS (/22)
│   ├── Subnet: DB (/24, delegated)
│   ├── Subnet: Private Endpoints (/24)
│   ├── Subnet: App Gateway (/24)
│   └── NSGs
│
├── Compute
│   ├── AKS Cluster (system + app node pools)
│   └── Application Gateway
│
├── Data
│   ├── PostgreSQL Flexible Server (HA, private)
│   ├── Redis Cache (Premium, zone-redundant)
│   └── Storage Account (Blob)
│
├── Security
│   ├── Key Vault (RBAC-enabled)
│   ├── Managed Identities
│   └── Role Assignments
│
├── Containers
│   └── ACR (Premium, attached to AKS)
│
├── Messaging
│   └── Service Bus Namespace (queues, topics)
│
└── Monitoring
    ├── Log Analytics Workspace
    ├── Application Insights
    └── Alert Rules
```

---

## Interview Questions

### Q: How do you structure Terraform for multiple environments?
**A:** Two common approaches:
1. **Workspaces + tfvars**: Single codebase, different variable files per environment. Simple but limited.
2. **Directory per environment + modules** (recommended): 
   - Shared modules in `modules/` (networking, AKS, database)
   - Environment directories (`environments/dev/`, `environments/prod/`) call modules with different variables
   - DRY code, environment isolation, independent state files

### Q: How do you manage Terraform state for a team?
**A:** Remote backend on Azure Storage:
- Storage Account with versioning enabled (state recovery)
- State locking via Azure Blob leasing (prevents concurrent modifications)
- Separate state files per environment (isolated blast radius)
- Restrict access via RBAC (only pipeline service principal can modify)
- Enable soft delete on container (accidental deletion recovery)

### Q: How do you handle secrets in Terraform?
**A:**
- Never commit secrets to version control
- Use `sensitive = true` on variables
- Store secrets in Key Vault, reference via data source
- Pipeline injects secrets from Key Vault at apply time
- For initial setup (like DB password): generate in Terraform, store in Key Vault immediately
- Use Managed Identity for Terraform authentication (no stored credentials)

### Q: What does your Terraform CI/CD pipeline look like?
**A:**
1. PR created → `terraform plan` (show changes, post to PR as comment)
2. PR reviewed and approved → merge to main
3. Main branch → `terraform plan` (confirm expected changes)
4. Manual approval → `terraform apply`
5. Post-apply: verify health checks, tag state with release version

Safeguards: `prevent_destroy` on critical resources, plan output review, separate state per environment.
