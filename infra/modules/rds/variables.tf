variable "name" {
  description = "Name prefix for database resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC hosting the database."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnets for the DB subnet group."
  type        = list(string)
}

variable "allowed_security_group_id" {
  description = "Security group permitted to connect on 5432 (the EKS cluster SG)."
  type        = string
}

variable "engine_version" {
  description = "PostgreSQL major version."
  type        = string
  default     = "16.4"
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  description = "Initial storage in GiB."
  type        = number
  default     = 20
}

variable "max_allocated_storage" {
  description = "Upper bound for storage autoscaling in GiB."
  type        = number
  default     = 50
}

variable "database_name" {
  description = "Initial database name."
  type        = string
  default     = "shopkart"
}

variable "master_username" {
  description = "Master username. The password is generated, never hardcoded."
  type        = string
  default     = "shopkart"
}

variable "multi_az" {
  description = "Standby in a second AZ. Doubles cost; enable for production."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Automated backup retention."
  type        = number
  default     = 7
}

variable "skip_final_snapshot" {
  description = "Skip the final snapshot on destroy. True only for throwaway environments."
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Block accidental deletion. Enable for production."
  type        = bool
  default     = false
}

variable "performance_insights_enabled" {
  description = "Enable Performance Insights. Not supported on some micro classes."
  type        = bool
  default     = false
}

variable "secret_recovery_days" {
  description = "Secrets Manager recovery window. Zero allows immediate reuse of the name."
  type        = number
  default     = 0
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
