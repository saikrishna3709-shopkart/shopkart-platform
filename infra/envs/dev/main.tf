locals {
  name = "${var.project}-${var.environment}"

  tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
    Owner       = var.owner
  }

  services = [
    "catalog-service",
    "inventory-service",
    "notification-service",
    "order-service",
    "payment-service",
    "storefront-service",
  ]
}

module "vpc" {
  source = "../../modules/vpc"

  name     = local.name
  region   = var.region
  vpc_cidr = var.vpc_cidr
  az_count = 2
  # Deliberate cost/HA trade-off: one NAT saves ~$32/month but is an AZ-level SPOF.
  single_nat_gateway = true

  tags = local.tags
}

module "ecr" {
  source = "../../modules/ecr"

  name             = var.project
  repository_names = local.services

  tags = local.tags
}

module "jenkins" {
  source = "../../modules/jenkins"

  name                = local.name
  region              = var.region
  vpc_id              = module.vpc.vpc_id
  subnet_id           = module.vpc.public_subnet_ids[0]
  admin_cidrs         = var.api_allowed_cidrs
  ecr_repository_arns = values(module.ecr.repository_arns)
  instance_type       = var.jenkins_instance_type
  root_volume_size    = var.jenkins_root_volume_size

  tags = local.tags
}

module "eks" {
  source = "../../modules/eks"

  name               = local.name
  kubernetes_version = var.kubernetes_version
  private_subnet_ids = module.vpc.private_subnet_ids

  node_instance_types = var.node_instance_types
  # Spot is fine for dev and roughly 70% cheaper.
  node_capacity_type = "SPOT"
  node_desired_size  = var.node_desired_size
  node_min_size      = var.node_min_size
  node_max_size      = var.node_max_size
  admin_principal_arns = {
    jenkins = module.jenkins.role_arn
  }

  public_access_cidrs = distinct(concat(
    var.api_allowed_cidrs,
    ["${module.jenkins.public_ip}/32"],
  ))

  tags = local.tags
}

module "rds" {
  source = "../../modules/rds"

  name               = local.name
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  # Only workloads in the cluster may open a connection.
  allowed_security_group_id = module.eks.cluster_security_group_id

  instance_class        = var.db_instance_class
  multi_az              = false
  backup_retention_days = 1
  skip_final_snapshot   = true
  deletion_protection   = false

  tags = local.tags
}

module "addons" {
  source = "../../modules/addons"

  name                      = local.name
  region                    = var.region
  vpc_id                    = module.vpc.vpc_id
  cluster_name              = module.eks.cluster_name
  oidc_provider_arn         = module.eks.oidc_provider_arn
  oidc_provider_url         = module.eks.oidc_provider_url
  secretsmanager_secret_arn = module.rds.secret_arn

  tags = local.tags

  depends_on = [module.eks]
}
