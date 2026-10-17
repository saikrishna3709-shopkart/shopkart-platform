variable "project" {
  description = "Project name, used as a prefix everywhere."
  type        = string
  default     = "shopkart"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "owner" {
  description = "Owner tag value, for cost attribution."
  type        = string
  default     = "platform"
}

variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "kubernetes_version" {
  description = "EKS control plane version."
  type        = string
  default     = "1.36"
}

variable "node_instance_types" {
  description = "Instance types for the managed node group."
  type        = list(string)
  default     = ["m7i-flex.large"]
}

variable "node_desired_size" {
  description = "Initial node count."
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum node count."
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum node count. Each node consumes EC2 Spot Standard vCPU quota."
  type        = number
  default     = 2
}

variable "api_allowed_cidrs" {
  description = "Administrator CIDRs allowed to reach the tooling and Kubernetes API."
  type        = list(string)

  validation {
    condition = (
      length(var.api_allowed_cidrs) > 0 &&
      alltrue([
        for cidr in var.api_allowed_cidrs :
        can(cidrnetmask(cidr)) && cidr != "0.0.0.0/0"
      ])
    )
    error_message = "api_allowed_cidrs must contain valid restricted CIDRs; 0.0.0.0/0 is forbidden."
  }
}

variable "jenkins_instance_type" {
  description = "Instance type for Jenkins, SonarQube, Nexus, and administration tooling."
  type        = string
  default     = "m7i-flex.large"
}

variable "jenkins_root_volume_size" {
  description = "Encrypted Jenkins host root volume size in GiB."
  type        = number
  default     = 50
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}
