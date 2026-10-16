variable "name" {
  description = "Name prefix for all VPC resources."
  type        = string
}

variable "region" {
  description = "AWS region, used for VPC endpoint service names."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "How many availability zones to spread subnets across."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 4
    error_message = "az_count must be between 2 and 4; EKS requires at least two AZs."
  }
}

variable "single_nat_gateway" {
  description = "One NAT for all AZs (cheaper, single point of failure) or one per AZ."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
